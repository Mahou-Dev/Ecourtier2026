package com.ecourtier.domain.transaction.gateway;

import com.ecourtier.domain.transaction.client.WaveCheckoutResponse;
import com.ecourtier.domain.transaction.client.WaveClient;
import com.ecourtier.domain.transaction.client.WaveRefundResponse;
import com.ecourtier.domain.transaction.entity.PaiementWaveEntity;
import com.ecourtier.domain.transaction.entity.StatutPaiementWave;
import com.ecourtier.domain.transaction.entity.TypeOperationWave;
import com.ecourtier.domain.transaction.repository.PaiementWaveRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulation locale contrôlée de {@link PaymentGateway} (zéro débit réel garanti).
 *
 * Remédie à ESCROW-OPS-001 (registre purement volatile en mémoire) en s'appuyant sur la table
 * {@code paiements_wave} lorsque le cache mémoire est vidé (ex: redémarrage du backend),
 * et utilise exclusivement des préfixes explicites {@code SIM-LOCAL-}, {@code SIM-REFUND-}
 * et {@code SIM-TRANSFER-} (jamais de fausses références Wave présentées comme réelles).
 */
@Slf4j
public class MockPaymentGateway implements PaymentGateway, WaveClient {

    public static final String PROVIDER_CODE = "MOCK_LOCAL";

    public record SimulatedPaymentOperation(
            String reference,
            UUID transactionId,
            BigDecimal amount,
            String customerPhone,
            String status
    ) {
    }

    public record SimulatedRefundOperation(
            String refundReference,
            UUID transactionId,
            String originalReference,
            BigDecimal refundedAmount,
            String status
    ) {
    }

    private final ConcurrentMap<String, SimulatedPaymentOperation> paymentsRegistry = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SimulatedRefundOperation> refundsRegistry = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> refundByOriginalPaymentReference = new ConcurrentHashMap<>();

    private final AtomicInteger checkoutCallCount = new AtomicInteger(0);
    private final AtomicInteger refundCallCount = new AtomicInteger(0);
    private final AtomicInteger transferCallCount = new AtomicInteger(0);

    private PaiementWaveRepository paiementWaveRepository;

    @Autowired(required = false)
    public void setPaiementWaveRepository(PaiementWaveRepository paiementWaveRepository) {
        this.paiementWaveRepository = paiementWaveRepository;
    }

    @Override
    public String getProviderCode() {
        return PROVIDER_CODE;
    }

    @Override
    public String getDefaultPaymentMethod() {
        return "Wave";
    }

    @Override
    public boolean isSimulated() {
        return true;
    }

    @Override
    public boolean isZeroDebitGuaranteed() {
        return true;
    }

    @Override
    public PaymentInitResponse initiatePayment(PaymentInitRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Requête de paiement simulé manquante.");
        }
        WaveCheckoutResponse resp = initiateEscrowCheckout(
                request.getTransactionId(),
                request.getAmount(),
                request.getCustomerPhone()
        );
        if (resp == null) {
            return PaymentInitResponse.builder()
                    .transactionId(request.getTransactionId())
                    .amount(request.getAmount())
                    .providerCode(PROVIDER_CODE)
                    .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : "Wave")
                    .refCommand(request.getRefCommand())
                    .state(ExternalPaymentState.FAILED)
                    .simulated(true)
                    .zeroDebitGuaranteed(true)
                    .message("Réponse de simulation vide")
                    .build();
        }
        ExternalPaymentState mappedState = "SUCCESS".equalsIgnoreCase(resp.getStatus())
                ? ExternalPaymentState.CONFIRMED_SIMULATED
                : ("PENDING".equalsIgnoreCase(resp.getStatus()) ? ExternalPaymentState.PENDING : ExternalPaymentState.FAILED);

        return PaymentInitResponse.builder()
                .transactionId(resp.getTransactionId() != null ? resp.getTransactionId() : request.getTransactionId())
                .amount(resp.getAmount() != null ? resp.getAmount() : request.getAmount())
                .providerCode(PROVIDER_CODE)
                .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : "Wave")
                .refCommand(request.getRefCommand())
                .externalReference(resp.getWaveReference())
                .externalToken(resp.getWaveReference())
                .redirectUrl(resp.getCheckoutUrl())
                .state(mappedState)
                .simulated(true)
                .zeroDebitGuaranteed(true)
                .message("Paiement simulé localement (aucun débit financier réel).")
                .build();
    }

    @Override
    public PaymentStatusResult checkPaymentStatus(String externalReferenceOrToken, UUID transactionId, BigDecimal expectedAmount) {
        boolean verified = (transactionId != null && expectedAmount != null)
                ? verifyPaymentStatus(externalReferenceOrToken, transactionId, expectedAmount)
                : verifyPaymentStatus(externalReferenceOrToken);
        return PaymentStatusResult.builder()
                .state(verified ? ExternalPaymentState.CONFIRMED_SIMULATED : ExternalPaymentState.FAILED)
                .externalReference(externalReferenceOrToken)
                .confirmedAmount(verified ? expectedAmount : null)
                .simulated(true)
                .message(verified ? "Paiement simulé vérifié" : "Paiement simulé introuvable ou incohérent")
                .build();
    }

    @Override
    public PaymentRefundResult initiateRefund(UUID transactionId, String originalReference, BigDecimal amount) {
        WaveRefundResponse resp = initiateEscrowRefund(transactionId, originalReference, amount);
        if (resp == null) {
            return PaymentRefundResult.builder()
                    .originalReference(originalReference)
                    .refundedAmount(amount)
                    .status("FAILED")
                    .simulated(true)
                    .message("Réponse de remboursement simulé vide")
                    .build();
        }
        return PaymentRefundResult.builder()
                .refundReference(resp.getRefundReference())
                .originalReference(resp.getOriginalWaveReference())
                .refundedAmount(resp.getRefundedAmount())
                .status(resp.getStatus())
                .simulated(true)
                .message("Remboursement simulé localement (aucun transfert financier réel).")
                .build();
    }

    @Override
    public OwnerTransferResult initiateSimulatedOwnerTransfer(UUID transactionId, BigDecimal amount, String destinationPhone) {
        transferCallCount.incrementAndGet();
        String ref = "SIM-TRANSFER-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        log.info("[SIMULATION LOCALE — TRANSFERT PROPRIÉTAIRE] Aucun virement réel effectué. tx={} montant={} XOF ref={}",
                transactionId, amount, ref);
        return OwnerTransferResult.builder()
                .transferReference(ref)
                .amount(amount)
                .status("TRANSFERT_SIMULE")
                .simulated(true)
                .providerCode(PROVIDER_CODE)
                .message("Transfert propriétaire simulé localement (libération logique uniquement).")
                .build();
    }

    @Override
    public WaveCheckoutResponse initiateEscrowCheckout(UUID transactionId, BigDecimal amount, String customerPhone) {
        checkoutCallCount.incrementAndGet();

        if (transactionId == null) {
            throw new IllegalArgumentException("Paiement simulé refusé : identifiant de transaction manquant.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Paiement simulé refusé : montant invalide (" + amount + ").");
        }

        String simRef = "SIM-LOCAL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        SimulatedPaymentOperation operation = new SimulatedPaymentOperation(
                simRef,
                transactionId,
                amount,
                customerPhone,
                "SUCCESS"
        );
        paymentsRegistry.put(simRef, operation);

        log.info("[SIMULATION LOCALE] Paiement simulé enregistré (zéro débit réel) pour tx={}: montant={} XOF, réf={}",
                transactionId, amount, simRef);

        return WaveCheckoutResponse.builder()
                .transactionId(transactionId)
                .amount(amount)
                .waveReference(simRef)
                .checkoutUrl(null)
                .status("SUCCESS")
                .build();
    }

    @Override
    public boolean verifyPaymentStatus(String waveReference) {
        if (waveReference == null || waveReference.isBlank()) {
            return false;
        }
        String normalized = waveReference.trim();
        SimulatedPaymentOperation operation = paymentsRegistry.get(normalized);
        if (operation != null) {
            return operation.transactionId() != null
                    && operation.amount() != null
                    && operation.amount().compareTo(BigDecimal.ZERO) > 0
                    && "SUCCESS".equalsIgnoreCase(operation.status());
        }
        // Repli persistant sur PostgreSQL / H2 (ESCROW-OPS-001 : survit aux redémarrages JVM)
        return findPersistedPayment(normalized).isPresent();
    }

    @Override
    public boolean verifyPaymentStatus(String waveReference, UUID transactionId, BigDecimal expectedAmount) {
        if (waveReference == null || waveReference.isBlank() || transactionId == null || expectedAmount == null) {
            return false;
        }
        String normalized = waveReference.trim();
        SimulatedPaymentOperation operation = paymentsRegistry.get(normalized);
        if (operation != null) {
            return "SUCCESS".equalsIgnoreCase(operation.status())
                    && transactionId.equals(operation.transactionId())
                    && expectedAmount.compareTo(operation.amount()) == 0;
        }
        Optional<PaiementWaveEntity> persisted = findPersistedPayment(normalized);
        return persisted.isPresent()
                && persisted.get().getTransaction() != null
                && transactionId.equals(persisted.get().getTransaction().getId())
                && persisted.get().getMontant() != null
                && expectedAmount.compareTo(persisted.get().getMontant()) == 0;
    }

    @Override
    public WaveRefundResponse initiateEscrowRefund(UUID transactionId, String originalWaveReference, BigDecimal amount) {
        refundCallCount.incrementAndGet();

        if (originalWaveReference == null || originalWaveReference.isBlank()) {
            throw new IllegalArgumentException("Remboursement simulé refusé : référence de paiement initiale manquante.");
        }

        String normalizedOriginalRef = originalWaveReference.trim();
        SimulatedPaymentOperation paidOperation = paymentsRegistry.get(normalizedOriginalRef);

        UUID paidTxId;
        BigDecimal paidAmount;
        if (paidOperation != null && "SUCCESS".equalsIgnoreCase(paidOperation.status())) {
            paidTxId = paidOperation.transactionId();
            paidAmount = paidOperation.amount();
        } else {
            Optional<PaiementWaveEntity> persisted = findPersistedPayment(normalizedOriginalRef);
            if (persisted.isEmpty() || persisted.get().getTransaction() == null) {
                throw new IllegalStateException("Remboursement simulé refusé : référence de paiement initiale inconnue ou non confirmée (" + normalizedOriginalRef + ").");
            }
            paidTxId = persisted.get().getTransaction().getId();
            paidAmount = persisted.get().getMontant();
        }

        if (transactionId == null || !paidTxId.equals(transactionId)) {
            throw new IllegalStateException("Remboursement simulé refusé : la référence " + normalizedOriginalRef + " ne correspond pas à la transaction " + transactionId + ".");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(paidAmount) > 0) {
            throw new IllegalArgumentException("Remboursement simulé refusé : le montant demandé (" + amount + ") est invalide ou dépasse le montant payé (" + paidAmount + ").");
        }

        // AUDIT-PT-008 : Vérification persistante en base d'un remboursement déjà enregistré (survit au redémarrage)
        if (paiementWaveRepository != null) {
            boolean alreadyRefundedInDb = paiementWaveRepository.findByTransactionIdOrderByDateCreationDesc(transactionId)
                    .stream()
                    .anyMatch(p -> p.getTypeOperation() == TypeOperationWave.REMBOURSEMENT
                            && (p.getStatut() == StatutPaiementWave.CONFIRME
                                || p.getStatut() == StatutPaiementWave.SIMULE
                                || p.getStatut() == StatutPaiementWave.RECONCILIE));
            if (alreadyRefundedInDb) {
                throw new IllegalStateException("Remboursement simulé refusé : un remboursement confirmé existe déjà en base pour la transaction " + transactionId + ".");
            }
        }

        String refundRef = "SIM-REFUND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String existingRefundRef = refundByOriginalPaymentReference.putIfAbsent(normalizedOriginalRef, refundRef);
        if (existingRefundRef != null) {
            throw new IllegalStateException("Remboursement simulé refusé : le paiement " + normalizedOriginalRef + " a déjà fait l'objet d'un remboursement (" + existingRefundRef + ").");
        }

        SimulatedRefundOperation refundOperation = new SimulatedRefundOperation(
                refundRef,
                transactionId,
                normalizedOriginalRef,
                amount,
                "SUCCESS"
        );
        refundsRegistry.put(refundRef, refundOperation);

        log.info("[SIMULATION LOCALE] Remboursement simulé enregistré (zéro débit réel) pour tx={} (origRef={}): montant={} XOF, réf={}",
                transactionId, normalizedOriginalRef, amount, refundRef);

        return WaveRefundResponse.builder()
                .refundReference(refundRef)
                .originalWaveReference(normalizedOriginalRef)
                .refundedAmount(amount)
                .status("SUCCESS")
                .build();
    }

    @Override
    public boolean verifyRefundStatus(String refundReference) {
        if (refundReference == null || refundReference.isBlank()) {
            return false;
        }
        String normalized = refundReference.trim();
        SimulatedRefundOperation refundOperation = refundsRegistry.get(normalized);
        if (refundOperation != null && "SUCCESS".equalsIgnoreCase(refundOperation.status())) {
            return verifyPaymentStatus(refundOperation.originalReference());
        }
        return findPersistedRefund(normalized).isPresent();
    }

    @Override
    public boolean verifyRefundStatus(String refundReference, String originalWaveReference, BigDecimal expectedAmount) {
        if (refundReference == null || refundReference.isBlank() || originalWaveReference == null || expectedAmount == null) {
            return false;
        }
        String normalized = refundReference.trim();
        SimulatedRefundOperation refundOperation = refundsRegistry.get(normalized);
        if (refundOperation != null) {
            return "SUCCESS".equalsIgnoreCase(refundOperation.status())
                    && originalWaveReference.trim().equals(refundOperation.originalReference())
                    && expectedAmount.compareTo(refundOperation.refundedAmount()) == 0
                    && verifyPaymentStatus(refundOperation.originalReference());
        }
        Optional<PaiementWaveEntity> persistedRefund = findPersistedRefund(normalized);
        return persistedRefund.isPresent()
                && persistedRefund.get().getMontant() != null
                && expectedAmount.compareTo(persistedRefund.get().getMontant()) == 0
                && verifyPaymentStatus(originalWaveReference);
    }

    private Optional<PaiementWaveEntity> findPersistedPayment(String ref) {
        if (paiementWaveRepository == null) {
            return Optional.empty();
        }
        // AUDIT-PT-008 : Exclure EN_COURS (opération potentiellement interrompue avant confirmation)
        return paiementWaveRepository.findByReferenceExterne(ref)
                .filter(p -> p.getTypeOperation() == TypeOperationWave.PAIEMENT_SEQUESTRE)
                .filter(p -> p.getStatut() == StatutPaiementWave.CONFIRME
                        || p.getStatut() == StatutPaiementWave.A_RECONCILIER
                        || p.getStatut() == StatutPaiementWave.TARDIF_A_REMBOURSER
                        || p.getStatut() == StatutPaiementWave.RECONCILIE);
    }

    private Optional<PaiementWaveEntity> findPersistedRefund(String ref) {
        if (paiementWaveRepository == null) {
            return Optional.empty();
        }
        return paiementWaveRepository.findByReferenceExterne(ref)
                .filter(p -> p.getTypeOperation() == TypeOperationWave.REMBOURSEMENT)
                .filter(p -> p.getStatut() == StatutPaiementWave.CONFIRME
                        || p.getStatut() == StatutPaiementWave.SIMULE
                        || p.getStatut() == StatutPaiementWave.A_RECONCILIER
                        || p.getStatut() == StatutPaiementWave.RECONCILIE);
    }

    public int getCheckoutCallCount() {
        return checkoutCallCount.get();
    }

    public int getRefundCallCount() {
        return refundCallCount.get();
    }

    public int getTransferCallCount() {
        return transferCallCount.get();
    }

    public void resetCounters() {
        checkoutCallCount.set(0);
        refundCallCount.set(0);
        transferCallCount.set(0);
    }

    /**
     * Vide uniquement le cache mémoire pour simuler un redémarrage backend tout en conservant la base SQL.
     */
    public void simulateBackendRestartClearMemoryOnly() {
        paymentsRegistry.clear();
        refundsRegistry.clear();
        refundByOriginalPaymentReference.clear();
    }

    public void resetState() {
        simulateBackendRestartClearMemoryOnly();
        resetCounters();
    }
}
