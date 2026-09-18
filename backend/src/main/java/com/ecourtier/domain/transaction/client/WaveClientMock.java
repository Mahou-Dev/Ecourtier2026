package com.ecourtier.domain.transaction.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@Slf4j
public class WaveClientMock implements WaveClient {

    @Override
    public WaveCheckoutResponse initiateEscrowCheckout(UUID transactionId, BigDecimal amount, String customerPhone) {
        String waveRef = "WAVE-SANDBOX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[WAVE SANDBOX MOCK] Paiement séquestre initié pour la transaction {}: Montant {} FCFA, Réf {}", transactionId, amount, waveRef);

        return WaveCheckoutResponse.builder()
                .waveReference(waveRef)
                .checkoutUrl("https://pay.wave.com/sandbox/checkout/" + waveRef)
                .status("SUCCESS")
                .build();
    }

    @Override
    public boolean verifyPaymentStatus(String waveReference) {
        log.info("[WAVE SANDBOX MOCK] Vérification du statut du paiement pour la référence {}", waveReference);
        return waveReference != null && waveReference.startsWith("WAVE-");
    }
}
