package com.ecourtier.domain.transaction.service;

import com.ecourtier.common.scheduler.SchedulerLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Tâche planifiée chargée de reprendre automatiquement la génération des PDF de bail
 * restés en statut A_GENERER ou ECHEC après double signature complète et libération du séquestre.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BailPdfRecoveryScheduler {

    public static final String LOCK_NAME = "transaction-bail-pdf-recovery";
    private static final Duration LOCK_AT_MOST_FOR = Duration.ofMinutes(3);
    private static final Duration LOCK_AT_LEAST_FOR = Duration.ofSeconds(10);

    private final TransactionService transactionService;
    private final SchedulerLockService schedulerLockService;

    @Value("${ecourtier.transactions.pdf-recovery-max-tentatives:5}")
    private int maxTentatives;

    @Value("${ecourtier.transactions.pdf-recovery-batch-size:20}")
    private int batchSize;

    @Scheduled(
            initialDelayString = "${ecourtier.transactions.pdf-recovery-initial-delay-ms:15000}",
            fixedDelayString = "${ecourtier.transactions.pdf-recovery-fixed-delay-ms:60000}"
    )
    public void reprendrePdfEnEchec() {
        boolean executed = schedulerLockService.tryExecuteWithLock(LOCK_NAME, LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR, () -> {
            int recovered = transactionService.reprendreGenerationsPdfEnEchec(maxTentatives, batchSize);
            if (recovered > 0) {
                log.info("[PDF BAIL REPRISE] {} document(s) PDF de bail régénéré(s) automatiquement", recovered);
            }
        });
        if (!executed) {
            log.debug("[PDF BAIL REPRISE] Verrou '{}' déjà détenu par une autre instance, exécution ignorée.", LOCK_NAME);
        }
    }
}
