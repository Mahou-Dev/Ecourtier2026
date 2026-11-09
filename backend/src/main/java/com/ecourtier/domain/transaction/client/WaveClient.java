package com.ecourtier.domain.transaction.client;

import java.math.BigDecimal;
import java.util.UUID;

public interface WaveClient {

    WaveCheckoutResponse initiateEscrowCheckout(UUID transactionId, BigDecimal amount, String customerPhone);

    boolean verifyPaymentStatus(String waveReference);

    default boolean verifyPaymentStatus(String waveReference, UUID transactionId, BigDecimal expectedAmount) {
        return verifyPaymentStatus(waveReference);
    }

    WaveRefundResponse initiateEscrowRefund(UUID transactionId, String originalWaveReference, BigDecimal amount);

    boolean verifyRefundStatus(String refundReference);

    default boolean verifyRefundStatus(String refundReference, String originalWaveReference, BigDecimal expectedAmount) {
        return verifyRefundStatus(refundReference);
    }
}
