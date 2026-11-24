package com.ecourtier.domain.transaction.client;

import com.ecourtier.domain.transaction.gateway.PaymentGateway;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

/**
 * Interface historique conservée pour compatibilité de tests.
 * L'abstraction canonique de passerelle de paiement est {@link PaymentGateway}.
 */
@Deprecated(forRemoval = false)
public interface WaveClient extends PaymentGateway {

    WaveCheckoutResponse initiateEscrowCheckout(UUID transactionId, BigDecimal amount, String customerPhone);

    boolean verifyPaymentStatus(String waveReference);

    default boolean verifyPaymentStatus(String waveReference, UUID transactionId, BigDecimal expectedAmount) {
        return verifyPaymentStatus(waveReference);
    }

    WaveRefundResponse initiateEscrowRefund(UUID transactionId, String originalWaveReference, BigDecimal amount);

    boolean verifyRefundStatus(String refundReference);

    @Override
    default boolean verifyRefundStatus(String refundReference, String originalWaveReference, BigDecimal expectedAmount) {
        return verifyRefundStatus(refundReference);
    }

    @Override
    default String getProviderCode() {
        return "MOCK_LOCAL";
    }

    @Override
    default String getDefaultPaymentMethod() {
        return "Wave";
    }

    @Override
    default boolean isSimulated() {
        return true;
    }

    @Override
    default boolean isZeroDebitGuaranteed() {
        return true;
    }

    @Override
    default PaymentInitResponse initiatePayment(PaymentInitRequest request) {
        WaveCheckoutResponse resp = initiateEscrowCheckout(
                request.getTransactionId(),
                request.getAmount(),
                request.getCustomerPhone()
        );
        if (resp == null) {
            return null;
        }
        ExternalPaymentState mappedState = "SUCCESS".equalsIgnoreCase(resp.getStatus())
                ? ExternalPaymentState.CONFIRMED_SIMULATED
                : ("PENDING".equalsIgnoreCase(resp.getStatus()) ? ExternalPaymentState.PENDING : ExternalPaymentState.FAILED);
        return PaymentInitResponse.builder()
                .transactionId(resp.getTransactionId() != null ? resp.getTransactionId() : request.getTransactionId())
                .amount(resp.getAmount() != null ? resp.getAmount() : request.getAmount())
                .providerCode(getProviderCode())
                .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : getDefaultPaymentMethod())
                .refCommand(request.getRefCommand())
                .externalReference(resp.getWaveReference())
                .externalToken(resp.getWaveReference())
                .redirectUrl(resp.getCheckoutUrl())
                .state(mappedState)
                .simulated(isSimulated())
                .zeroDebitGuaranteed(isZeroDebitGuaranteed())
                .build();
    }

    @Override
    default PaymentStatusResult checkPaymentStatus(String externalReferenceOrToken, UUID transactionId, BigDecimal expectedAmount) {
        boolean verified = verifyPaymentStatus(externalReferenceOrToken, transactionId, expectedAmount);
        return PaymentStatusResult.builder()
                .state(verified ? ExternalPaymentState.CONFIRMED_SIMULATED : ExternalPaymentState.FAILED)
                .externalReference(externalReferenceOrToken)
                .confirmedAmount(verified ? expectedAmount : null)
                .simulated(isSimulated())
                .build();
    }

    @Override
    default PaymentRefundResult initiateRefund(UUID transactionId, String originalReference, BigDecimal amount) {
        WaveRefundResponse resp = initiateEscrowRefund(transactionId, originalReference, amount);
        if (resp == null) {
            return null;
        }
        return PaymentRefundResult.builder()
                .refundReference(resp.getRefundReference())
                .originalReference(resp.getOriginalWaveReference())
                .refundedAmount(resp.getRefundedAmount())
                .status(resp.getStatus())
                .simulated(isSimulated())
                .build();
    }

    @Override
    default OwnerTransferResult initiateSimulatedOwnerTransfer(UUID transactionId, BigDecimal amount, String destinationPhone) {
        String ref = "SIM-TRANSFER-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        return OwnerTransferResult.builder()
                .transferReference(ref)
                .amount(amount)
                .status("TRANSFERT_SIMULE")
                .simulated(true)
                .providerCode(getProviderCode())
                .message("Transfert propriétaire simulé localement (libération logique uniquement).")
                .build();
    }
}
