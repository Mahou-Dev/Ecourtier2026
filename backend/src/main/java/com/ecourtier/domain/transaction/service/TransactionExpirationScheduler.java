package com.ecourtier.domain.transaction.service;

import com.ecourtier.common.scheduler.SchedulerLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Tâche planifiée chargée de clôturer automatiquement les transactions INITIALISE
 * dont le délai d'attente de paiement est expiré.
 * Protégée en environnement multi-instances par verrou SQL distribué ({@link SchedulerLockService})
 * combiné au verrouillage pessimiste par transaction (Annonce -> Transaction).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TransactionExpirationScheduler {

    public static final String LOCK_NAME = "transaction-expiration";
    private static final Duration LOCK_AT_MOST_FOR = Duration.ofMinutes(2);
    private static final Duration LOCK_AT_LEAST_FOR = Duration.ofSeconds(10);

    private final TransactionService transactionService;
    private final SchedulerLockService schedulerLockService;

    @Scheduled(
            initialDelayString = "${ecourtier.transactions.expiration-check-initial-delay-ms:15000}",
            fixedDelayString = "${ecourtier.transactions.expiration-check-fixed-delay-ms:60000}"
    )
    public void expirerTransactionsAbandonnees() {
        try {
            boolean executed = schedulerLockService.tryExecuteWithLock(
                    LOCK_NAME,
                    LOCK_AT_MOST_FOR,
                    LOCK_AT_LEAST_FOR,
                    transactionService::expirerTransactionsExpirees
            );
            if (!executed) {
                log.debug("[EXPIRATION SCHEDULER] Verrou '{}' déjà détenu par une autre instance.", LOCK_NAME);
            }
        } catch (Exception e) {
            log.error("[EXPIRATION SCHEDULER] Erreur inattendue lors de l'expiration automatique des transactions", e);
        }
    }
}
