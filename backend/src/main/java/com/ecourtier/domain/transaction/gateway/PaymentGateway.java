package com.ecourtier.domain.transaction.gateway;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Abstraction unifiée de passerelle de paiement.
 * Distingue explicitement le prestataire (ex: PAYTECH, MOCK_LOCAL) du moyen de paiement (ex: Wave),
 * ainsi que le statut d'initialisation en attente (PENDING) de la confirmation authentifiée.
 */
public interface PaymentGateway {

    String getProviderCode();

    String getDefaultPaymentMethod();

    boolean isSimulated();

    boolean isZeroDebitGuaranteed();

    PaymentInitResponse initiatePayment(PaymentInitRequest request);

    PaymentStatusResult checkPaymentStatus(String externalReferenceOrToken, UUID transactionId, BigDecimal expectedAmount);

    PaymentRefundResult initiateRefund(UUID transactionId, String originalReference, BigDecimal amount);

    boolean verifyRefundStatus(String refundReference, String originalReference, BigDecimal expectedAmount);

    OwnerTransferResult initiateSimulatedOwnerTransfer(UUID transactionId, BigDecimal amount, String destinationPhone);

    enum ExternalPaymentState {
        PENDING,
        CONFIRMED,
        CONFIRMED_SIMULATED,
        CANCELED,
        FAILED,
        UNKNOWN
    }

    @Value
    @Builder
    class PaymentInitRequest {
        UUID transactionId;
        UUID annonceId;
        BigDecimal amount;
        String currency;
        String itemName;
        String commandName;
        String refCommand;
        String customerPhone;
        String customerEmail;
        String paymentMethod;
    }

    @Value
    @Builder
    class PaymentInitResponse {
        UUID transactionId;
        BigDecimal amount;
        String providerCode;
        String paymentMethod;
        String refCommand;
        String externalReference;
        String externalToken;
        String redirectUrl;
        ExternalPaymentState state;
        boolean simulated;
        boolean zeroDebitGuaranteed;
        String message;
    }

    @Value
    @Builder
    class PaymentStatusResult {
        ExternalPaymentState state;
        String externalReference;
        BigDecimal confirmedAmount;
        boolean simulated;
        String message;
    }

    @Value
    @Builder
    class PaymentRefundResult {
        String refundReference;
        String originalReference;
        BigDecimal refundedAmount;
        String status;
        boolean simulated;
        String message;
    }

    @Value
    @Builder
    class OwnerTransferResult {
        String transferReference;
        BigDecimal amount;
        String status;
        boolean simulated;
        String providerCode;
        String message;
    }
}
