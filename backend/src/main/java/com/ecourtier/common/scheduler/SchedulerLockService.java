package com.ecourtier.common.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

/**
 * Service de verrouillage distribué basé sur base SQL (table {@code scheduler_locks}),
 * compatible PostgreSQL 16 et H2 sans dépendance externe supplémentaire.
 *
 * Algorithme atomique en {@code REQUIRES_NEW} (inspiré de ShedLock) :
 * 1. Tente de réclamer un verrou existant expiré ({@code UPDATE ... WHERE name = ? AND lock_until <= ?}).
 * 2. Sinon, tente une insertion initiale atomique ({@code INSERT INTO scheduler_locks ...}) ;
 *    en cas de course concurrente entre instances, la violation de clé primaire est isolée et capturée proprement.
 */
@Service
@Slf4j
public class SchedulerLockService {

    private static final ZoneId DAKAR_ZONE = ZoneId.of("Africa/Dakar");

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate requiresNewTxTemplate;
    private Clock clock;
    private final String instanceOwner;
    private volatile Boolean postgresDialect;

    @Autowired
    public SchedulerLockService(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            @Autowired(required = false) Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.requiresNewTxTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock != null ? clock : Clock.system(DAKAR_ZONE);
        this.instanceOwner = resolveInstanceId();
    }

    void setClock(Clock clock) {
        this.clock = clock != null ? clock : Clock.system(DAKAR_ZONE);
    }

    public boolean tryExecuteWithLock(String lockName, Duration lockAtMostFor, Runnable task) {
        return tryExecuteWithLock(lockName, lockAtMostFor, Duration.ZERO, instanceOwner, task);
    }

    public boolean tryExecuteWithLock(
            String lockName,
            Duration lockAtMostFor,
            Duration lockAtLeastFor,
            Runnable task
    ) {
        return tryExecuteWithLock(lockName, lockAtMostFor, lockAtLeastFor, instanceOwner, task);
    }

    public boolean tryExecuteWithLock(
            String lockName,
            Duration lockAtMostFor,
            Duration lockAtLeastFor,
            String owner,
            Runnable task
    ) {
        if (!tryAcquire(lockName, lockAtMostFor, owner)) {
            return false;
        }
        try {
            task.run();
        } finally {
            release(lockName, lockAtLeastFor, owner);
        }
        return true;
    }

    public boolean tryAcquire(String lockName, Duration lockAtMostFor, String owner) {
        if (lockName == null || lockName.isBlank()) {
            throw new IllegalArgumentException("Le nom du verrou de tâche planifiée est obligatoire");
        }
        Duration effectiveMax = (lockAtMostFor == null || lockAtMostFor.isNegative() || lockAtMostFor.isZero())
                ? Duration.ofMinutes(5)
                : lockAtMostFor;
        String effectiveOwner = (owner == null || owner.isBlank()) ? instanceOwner : owner.trim();

        LocalDateTime now = LocalDateTime.now(effectiveClock());
        LocalDateTime lockUntil = now.plus(effectiveMax);
        Timestamp nowTs = Timestamp.valueOf(now);
        Timestamp lockUntilTs = Timestamp.valueOf(lockUntil);

        Boolean acquired = requiresNewTxTemplate.execute(status -> {
            int updated = jdbcTemplate.update(
                    "UPDATE scheduler_locks SET lock_until = ?, locked_at = ?, locked_by = ? "
                            + "WHERE name = ? AND lock_until <= ?",
                    lockUntilTs, nowTs, effectiveOwner, lockName, nowTs
            );
            if (updated > 0) {
                return Boolean.TRUE;
            }
            try {
                String insertSql = isPostgresDialect()
                        ? "INSERT INTO scheduler_locks (name, lock_until, locked_at, locked_by) VALUES (?, ?, ?, ?) ON CONFLICT (name) DO NOTHING"
                        : "INSERT INTO scheduler_locks (name, lock_until, locked_at, locked_by) VALUES (?, ?, ?, ?)";
                int inserted = jdbcTemplate.update(
                        insertSql,
                        lockName, lockUntilTs, nowTs, effectiveOwner
                );
                return inserted > 0;
            } catch (DataIntegrityViolationException conflict) {
                status.setRollbackOnly();
                return Boolean.FALSE;
            }
        });

        boolean ok = Boolean.TRUE.equals(acquired);
        if (!ok) {
            log.debug("[SCHEDULER LOCK] Verrou '{}' déjà détenu par une autre exécution/instance.", lockName);
        }
        return ok;
    }

    private boolean isPostgresDialect() {
        Boolean cached = postgresDialect;
        if (cached != null) {
            return cached;
        }
        boolean detected = false;
        try {
            var ds = jdbcTemplate.getDataSource();
            if (ds != null) {
                try (Connection conn = ds.getConnection()) {
                    String product = conn.getMetaData().getDatabaseProductName();
                    detected = product != null && product.toLowerCase(Locale.ROOT).contains("postgresql");
                }
            }
        } catch (Exception e) {
            log.debug("[SCHEDULER LOCK] Détection du dialecte SQL impossible, repli sur INSERT standard.", e);
        }
        postgresDialect = detected;
        return detected;
    }

    public void release(String lockName, Duration lockAtLeastFor, String owner) {
        String effectiveOwner = (owner == null || owner.isBlank()) ? instanceOwner : owner.trim();
        Duration effectiveMin = (lockAtLeastFor == null || lockAtLeastFor.isNegative()) ? Duration.ZERO : lockAtLeastFor;
        LocalDateTime now = LocalDateTime.now(effectiveClock());

        try {
            requiresNewTxTemplate.executeWithoutResult(status -> {
                Timestamp minReleaseTs = jdbcTemplate.query(
                        "SELECT locked_at FROM scheduler_locks WHERE name = ? AND locked_by = ?",
                        rs -> {
                            if (!rs.next()) {
                                return null;
                            }
                            Timestamp lockedAtTs = rs.getTimestamp("locked_at");
                            LocalDateTime lockedAt = lockedAtTs != null ? lockedAtTs.toLocalDateTime() : now;
                            LocalDateTime minUntil = lockedAt.plus(effectiveMin);
                            LocalDateTime targetUntil = minUntil.isAfter(now) ? minUntil : now;
                            return Timestamp.valueOf(targetUntil);
                        },
                        lockName, effectiveOwner
                );
                if (minReleaseTs != null) {
                    jdbcTemplate.update(
                            "UPDATE scheduler_locks SET lock_until = ? WHERE name = ? AND locked_by = ?",
                            minReleaseTs, lockName, effectiveOwner
                    );
                }
            });
        } catch (Exception e) {
            log.warn("[SCHEDULER LOCK] Échec libération verrou '{}' : {}", lockName, e.getMessage());
        }
    }

    private Clock effectiveClock() {
        return clock != null ? clock : Clock.system(DAKAR_ZONE);
    }

    private static String resolveInstanceId() {
        String host = "node";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            // fallback
        }
        return host + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
