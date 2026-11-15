package com.ecourtier.domain.transaction.service;

import com.ecourtier.domain.transaction.client.WaveClient;
import com.ecourtier.domain.transaction.client.WaveRefundResponse;
import com.ecourtier.domain.transaction.entity.PaiementWaveEntity;
import com.ecourtier.domain.transaction.entity.StatutPaiementWave;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import com.ecourtier.domain.transaction.entity.TypeOperationWave;
import com.ecourtier.domain.transaction.repository.PaiementWaveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class WavePaymentReconciliationService {

    private static final ZoneId DAKAR_ZONE = ZoneId.of("Africa/Dakar");

    private final PaiementWaveRepository paiementWaveRepository;
    private final TransactionService transactionService;
    private final WaveClient waveClient;

    @Value("${ecourtier.wave.stale-timeout:PT2M}")
    private Duration staleTimeout = Duration.ofMinutes(2);

    private Clock clock = Clock.system(DAKAR_ZONE);
    private TransactionTemplate transactionTemplate;

    @Autowired(required = false)
    void setClock(Clock clock) {
        if (clock != null) {
            this.clock = clock;
        }
    }

    @Autowired(required = false)
    void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = transactionManager != null ? new TransactionTemplate(transactionManager) : null;
    }

    @Scheduled(fixedDelayString = "${ecourtier.wave.reconciliation-delay-ms:300000}")
    public void scheduledReconcile() {
        try {
            int processed = reconcilierPaiementsEnAttente();
            if (processed > 0) {
                log.info("[RÉCONCILIATION WAVE] {} opération(s) réconciliée(s)", processed);
            }
        } catch (Exception e) {
            log.error("[RÉCONCILIATION WAVE] Erreur lors du cycle planifié", e);
        }
    }

    public int reconcilierPaiementsEnAttente() {
        return reconcilierPaiementsEnAttente(staleTimeout != null ? staleTimeout : Duration.ofMinutes(2));
    }

    public int reconcilierPaiementsEnAttente(Duration effectiveStaleTimeout) {
        List<CandidateSnapshot> candidates = executeInTx(() -> paiementWaveRepository
                .findByStatutInOrderByDateCreationAsc(List.of(
                        StatutPaiementWave.A_RECONCILIER,
                        StatutPaiementWave.EN_COURS,
                        StatutPaiementWave.TARDIF_A_REMBOURSER
                ))
                .stream()
                .map(pw -> {
                    TransactionEntity tx = pw.getTransaction();
                    return new CandidateSnapshot(
                            pw.getId(),
                            tx != null ? tx.getId() : null,
                            pw.getTypeOperation(),
                            pw.getStatut(),
                            pw.getReferenceExterne(),
                            tx != null ? tx.getReferenceWave() : null,
                            pw.getMontant(),
                            pw.getDateModification()
                    );
                })
                .toList());

        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }

        int processed = 0;
        LocalDateTime referenceNow = LocalDateTime.now(clock != null ? clock : Clock.system(DAKAR_ZONE));
        Duration threshold = effectiveStaleTimeout != null ? effectiveStaleTimeout : Duration.ofMinutes(2);

        for (CandidateSnapshot c : candidates) {
            try {
                if (c.transactionId() == null) {
                    continue;
                }
                if (reconcilierCandidat(c, referenceNow, threshold)) {
                    processed++;
                }
            } catch (Exception e) {
                log.error("[RÉCONCILIATION WAVE] Échec traitement candidat paiementWave={} tx={}",
                        c.paiementWaveId(), c.transactionId(), e);
                incrementerTentative(c.paiementWaveId(), e.getMessage());
            }
        }
        return processed;
    }

    private boolean reconcilierCandidat(CandidateSnapshot c, LocalDateTime referenceNow, Duration threshold) {
        boolean isStale = c.dateModification() == null || !c.dateModification().isAfter(referenceNow.minus(threshold));

        if (c.typeOperation() == TypeOperationWave.PAIEMENT_SEQUESTRE) {
            if (c.statut() == StatutPaiementWave.TARDIF_A_REMBOURSER) {
                return tenterRemboursementTardif(c, referenceNow);
            }

            if (c.statut() == StatutPaiementWave.EN_COURS && !isStale) {
                return false;
            }

            if (c.referenceExterne() == null || c.referenceExterne().isBlank()) {
                if (isStale) {
                    marquerEchec(c.paiementWaveId(), "Abandon ou redémarrage avant obtention de référence Wave", referenceNow);
                    return true;
                }
                return false;
            }

            boolean paidAtWave = waveClient.verifyPaymentStatus(c.referenceExterne(), c.transactionId(), c.montant());
            if (paidAtWave) {
                transactionService.confirmWavePaymentFromExternal(
                        c.transactionId(),
                        c.paiementWaveId(),
                        c.referenceExterne(),
                        c.montant(),
                        null
                );
                return true;
            } else if (c.statut() == StatutPaiementWave.A_RECONCILIER || isStale) {
                marquerEchec(c.paiementWaveId(), "Paiement non confirmé par Wave lors de la réconciliation", referenceNow);
                return true;
            }
            return false;
        }

        // Cas REMBOURSEMENT
        if (c.statut() == StatutPaiementWave.EN_COURS && !isStale) {
            return false;
        }
        if (c.referenceExterne() == null || c.referenceExterne().isBlank()) {
            if (isStale) {
                marquerEchec(c.paiementWaveId(), "Abandon ou redémarrage avant obtention de référence remboursement Wave", referenceNow);
                return true;
            }
            return false;
        }

        String origRef = c.originalTxWaveRef();
        boolean refundedAtWave = origRef != null && !origRef.isBlank()
                && waveClient.verifyRefundStatus(c.referenceExterne(), origRef, c.montant());
        if (refundedAtWave) {
            return transactionService.finalizeReconciledRefund(
                    c.transactionId(),
                    c.paiementWaveId(),
                    c.referenceExterne(),
                    c.montant(),
                    "Remboursement finalisé par réconciliation Wave"
            );
        } else if (c.statut() == StatutPaiementWave.A_RECONCILIER || isStale) {
            marquerEchec(c.paiementWaveId(), "Remboursement non confirmé par Wave lors de la réconciliation", referenceNow);
            return true;
        }
        return false;
    }

    private boolean tenterRemboursementTardif(CandidateSnapshot c, LocalDateTime referenceNow) {
        if (c.referenceExterne() == null || c.referenceExterne().isBlank()) {
            return false;
        }
        WaveRefundResponse refundResp = waveClient.initiateEscrowRefund(c.transactionId(), c.referenceExterne(), c.montant());
        if (refundResp == null
                || !"SUCCESS".equalsIgnoreCase(refundResp.getStatus())
                || refundResp.getRefundReference() == null
                || refundResp.getRefundReference().isBlank()
                || refundResp.getRefundedAmount() == null
                || c.montant().compareTo(refundResp.getRefundedAmount()) != 0
                || !waveClient.verifyRefundStatus(refundResp.getRefundReference().trim(), c.referenceExterne(), c.montant())) {
            incrementerTentative(c.paiementWaveId(), "Échec remboursement automatique du paiement tardif");
            return false;
        }

        executeInTx(() -> {
            paiementWaveRepository.findByIdForUpdate(c.paiementWaveId()).ifPresent(pw -> {
                pw.setStatut(StatutPaiementWave.RECONCILIE);
                pw.setDateModification(referenceNow);
                pw.setTentatives((pw.getTentatives() != null ? pw.getTentatives() : 0) + 1);
                pw.setMessageErreur("Remboursement tardif exécuté et confirmé : " + refundResp.getRefundReference().trim());
                paiementWaveRepository.saveAndFlush(pw);
            });
            return null;
        });
        return true;
    }

    private void marquerEchec(UUID paiementWaveId, String motif, LocalDateTime now) {
        executeInTx(() -> {
            paiementWaveRepository.findByIdForUpdate(paiementWaveId).ifPresent(pw -> {
                pw.setStatut(StatutPaiementWave.ECHOUE);
                pw.setMessageErreur(truncate(motif));
                pw.setDateModification(now);
                paiementWaveRepository.saveAndFlush(pw);
            });
            return null;
        });
    }

    private void incrementerTentative(UUID paiementWaveId, String error) {
        LocalDateTime now = LocalDateTime.now(clock != null ? clock : Clock.system(DAKAR_ZONE));
        executeInTx(() -> {
            paiementWaveRepository.findByIdForUpdate(paiementWaveId).ifPresent(pw -> {
                pw.setTentatives((pw.getTentatives() != null ? pw.getTentatives() : 0) + 1);
                pw.setDateModification(now);
                if (error != null && !error.isBlank()) {
                    pw.setMessageErreur(truncate(error));
                }
                paiementWaveRepository.saveAndFlush(pw);
            });
            return null;
        });
    }

    private <T> T executeInTx(java.util.function.Supplier<T> action) {
        if (transactionTemplate != null) {
            return transactionTemplate.execute(status -> action.get());
        }
        return action.get();
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 950 ? s : s.substring(0, 950);
    }

    private record CandidateSnapshot(
            UUID paiementWaveId,
            UUID transactionId,
            TypeOperationWave typeOperation,
            StatutPaiementWave statut,
            String referenceExterne,
            String originalTxWaveRef,
            BigDecimal montant,
            LocalDateTime dateModification
    ) {}
}
