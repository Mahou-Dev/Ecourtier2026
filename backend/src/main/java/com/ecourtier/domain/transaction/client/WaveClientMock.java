package com.ecourtier.domain.transaction.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class WaveClientMock implements WaveClient {

    public record SimulatedPaymentOperation(
            String waveReference,
            UUID transactionId,
            BigDecimal amount,
            String customerPhone,
            String status
    ) {
    }

    public record SimulatedRefundOperation(
            String refundReference,
            UUID transactionId,
            String originalWaveReference,
            BigDecimal refundedAmount,
            String status
    ) {
    }

    private final ConcurrentMap<String, SimulatedPaymentOperation> paymentsRegistry = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SimulatedRefundOperation> refundsRegistry = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> refundByOriginalPaymentReference = new ConcurrentHashMap<>();

    private final AtomicInteger checkoutCallCount = new AtomicInteger(0);
    private final AtomicInteger refundCallCount = new AtomicInteger(0);

    @Override
    public WaveCheckoutResponse initiateEscrowCheckout(UUID transactionId, BigDecimal amount, String customerPhone) {
        checkoutCallCount.incrementAndGet();

        if (transactionId == null) {
            throw new IllegalArgumentException("Paiement Wave refusé : identifiant de transaction manquant.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Paiement Wave refusé : montant invalide (" + amount + ").");
        }

        String waveRef = "WAVE-SANDBOX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        SimulatedPaymentOperation operation = new SimulatedPaymentOperation(
                waveRef,
                transactionId,
                amount,
                customerPhone,
                "SUCCESS"
        );
        paymentsRegistry.put(waveRef, operation);

        log.info("[WAVE SANDBOX MOCK] Paiement séquestre initié et enregistré pour la transaction {}: Montant {} FCFA, Réf {}",
                transactionId, amount, waveRef);

        return WaveCheckoutResponse.builder()
                .transactionId(transactionId)
                .amount(amount)
                .waveReference(waveRef)
                .checkoutUrl("https://pay.wave.com/sandbox/checkout/" + waveRef)
                .status("SUCCESS")
                .build();
    }

    @Override
    public boolean verifyPaymentStatus(String waveReference) {
        log.info("[WAVE SANDBOX MOCK] Vérification du statut du paiement dans le registre interne pour la référence {}", waveReference);
        if (waveReference == null || waveReference.isBlank()) {
            return false;
        }
        SimulatedPaymentOperation operation = paymentsRegistry.get(waveReference.trim());
        return operation != null
                && operation.transactionId() != null
                && operation.amount() != null
                && operation.amount().compareTo(BigDecimal.ZERO) > 0
                && "SUCCESS".equalsIgnoreCase(operation.status());
    }

    @Override
    public boolean verifyPaymentStatus(String waveReference, UUID transactionId, BigDecimal expectedAmount) {
        if (!verifyPaymentStatus(waveReference) || transactionId == null || expectedAmount == null) {
            return false;
        }
        SimulatedPaymentOperation operation = paymentsRegistry.get(waveReference.trim());
        return operation != null
                && transactionId.equals(operation.transactionId())
                && expectedAmount.compareTo(operation.amount()) == 0;
    }

    @Override
    public WaveRefundResponse initiateEscrowRefund(UUID transactionId, String originalWaveReference, BigDecimal amount) {
        refundCallCount.incrementAndGet();

        if (originalWaveReference == null || originalWaveReference.isBlank()) {
            throw new IllegalArgumentException("Remboursement Wave refusé : référence de paiement initiale manquante.");
        }

        String normalizedOriginalRef = originalWaveReference.trim();
        SimulatedPaymentOperation paidOperation = paymentsRegistry.get(normalizedOriginalRef);
        if (paidOperation == null || !"SUCCESS".equalsIgnoreCase(paidOperation.status())) {
            throw new IllegalStateException("Remboursement Wave refusé : référence de paiement initiale inconnue ou non confirmée (" + normalizedOriginalRef + ").");
        }

        if (transactionId == null || !paidOperation.transactionId().equals(transactionId)) {
            throw new IllegalStateException("Remboursement Wave refusé : la référence de paiement " + normalizedOriginalRef + " ne correspond pas à la transaction " + transactionId + ".");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(paidOperation.amount()) > 0) {
            throw new IllegalArgumentException("Remboursement Wave refusé : le montant demandé (" + amount + ") est invalide ou dépasse le montant payé (" + paidOperation.amount() + ").");
        }

        String refundRef = "WAVE-REFUND-SANDBOX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String existingRefundRef = refundByOriginalPaymentReference.putIfAbsent(normalizedOriginalRef, refundRef);
        if (existingRefundRef != null) {
            throw new IllegalStateException("Remboursement Wave refusé : le paiement " + normalizedOriginalRef + " a déjà fait l'objet d'un remboursement (" + existingRefundRef + ").");
        }

        SimulatedRefundOperation refundOperation = new SimulatedRefundOperation(
                refundRef,
                transactionId,
                normalizedOriginalRef,
                amount,
                "SUCCESS"
        );
        refundsRegistry.put(refundRef, refundOperation);

        log.info("[WAVE SANDBOX MOCK] Remboursement séquestre simulé et enregistré pour la transaction {} (réf paiement initiale {}): Montant {} FCFA, Réf remboursement {}",
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
        log.info("[WAVE SANDBOX MOCK] Vérification du statut du remboursement dans le registre interne pour la référence {}", refundReference);
        if (refundReference == null || refundReference.isBlank()) {
            return false;
        }
        SimulatedRefundOperation refundOperation = refundsRegistry.get(refundReference.trim());
        if (refundOperation == null || !"SUCCESS".equalsIgnoreCase(refundOperation.status())) {
            return false;
        }
        SimulatedPaymentOperation paidOperation = paymentsRegistry.get(refundOperation.originalWaveReference());
        return paidOperation != null && "SUCCESS".equalsIgnoreCase(paidOperation.status());
    }

    @Override
    public boolean verifyRefundStatus(String refundReference, String originalWaveReference, BigDecimal expectedAmount) {
        if (!verifyRefundStatus(refundReference) || originalWaveReference == null || expectedAmount == null) {
            return false;
        }
        SimulatedRefundOperation refundOperation = refundsRegistry.get(refundReference.trim());
        return refundOperation != null
                && originalWaveReference.trim().equals(refundOperation.originalWaveReference())
                && expectedAmount.compareTo(refundOperation.refundedAmount()) == 0;
    }

    public int getCheckoutCallCount() {
        return checkoutCallCount.get();
    }

    public int getRefundCallCount() {
        return refundCallCount.get();
    }

    public void resetCounters() {
        checkoutCallCount.set(0);
        refundCallCount.set(0);
    }

    public void resetState() {
        paymentsRegistry.clear();
        refundsRegistry.clear();
        refundByOriginalPaymentReference.clear();
        resetCounters();
    }
}
