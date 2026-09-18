package com.ecourtier.domain.transaction.client;

import java.math.BigDecimal;
import java.util.UUID;

public interface WaveClient {

    WaveCheckoutResponse initiateEscrowCheckout(UUID transactionId, BigDecimal amount, String customerPhone);

    boolean verifyPaymentStatus(String waveReference);
}
