package com.ecourtier.domain.transaction.service;

import com.ecourtier.domain.transaction.dto.WaveWebhookPayloadDTO;
import com.ecourtier.domain.transaction.dto.WaveWebhookResponseDTO;
import com.ecourtier.domain.transaction.service.TransactionService.PaymentConfirmationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Service interne de traitement des notifications webhook Wave signées HMAC-SHA256.
 * Implémentation modélisée côté serveur pour les tests de robustesse, l'idempotence et
 * la gestion des paiements tardifs (hors intégration réseau vers un serveur Wave réel).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WaveWebhookService {

    private final TransactionService transactionService;
    private final ObjectMapper objectMapper;

    @Value("${ecourtier.wave.webhook-secret:}")
    private String webhookSecret = "";

    public void setWebhookSecretForTest(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public WaveWebhookResponseDTO traiterWebhook(String rawPayload, String signatureHeader) {
        verifierSignatureHmac(rawPayload, signatureHeader);

        WaveWebhookPayloadDTO payload;
        try {
            payload = objectMapper.readValue(rawPayload, WaveWebhookPayloadDTO.class);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload webhook Wave JSON invalide", e);
        }

        if (payload == null || payload.getTransactionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "transactionId obligatoire dans le webhook Wave");
        }

        boolean isFailureEvent = "FAILED".equalsIgnoreCase(payload.getStatus())
                || "checkout.session.failed".equalsIgnoreCase(payload.getType());

        if (isFailureEvent) {
            String reason = payload.getFailureReason() != null && !payload.getFailureReason().isBlank()
                    ? payload.getFailureReason()
                    : "Notification échec Wave";
            String ref = payload.getWaveReference() != null && !payload.getWaveReference().isBlank()
                    ? payload.getWaveReference().trim()
                    : "FAILED-" + payload.getTransactionId();
            transactionService.markWebhookPaymentFailed(payload.getTransactionId(), ref, reason, rawPayload);
            return WaveWebhookResponseDTO.builder()
                    .transactionId(payload.getTransactionId())
                    .statutPaiement("ECHOUE")
                    .latePayment(false)
                    .message(reason)
                    .build();
        }

        if (payload.getWaveReference() == null || payload.getWaveReference().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "waveReference obligatoire pour confirmation webhook");
        }

        PaymentConfirmationResult result = transactionService.confirmWavePaymentFromExternal(
                payload.getTransactionId(),
                null,
                payload.getWaveReference().trim(),
                payload.getAmount(),
                rawPayload
        );

        String statutPaiement = result.latePayment() ? "TARDIF_A_REMBOURSER" : "CONFIRME";
        String message = result.latePayment()
                ? (result.lateReason() != null ? result.lateReason() : "Paiement tardif marqué pour remboursement automatique")
                : "Paiement séquestre confirmé";

        return WaveWebhookResponseDTO.builder()
                .transactionId(payload.getTransactionId())
                .statutPaiement(statutPaiement)
                .statutSequestre(result.dto() != null ? result.dto().getStatutSequestre() : null)
                .latePayment(result.latePayment())
                .message(message)
                .build();
    }

    public String computeHmacSha256Hex(String rawPayload) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("Secret webhook Wave non configuré");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((rawPayload != null ? rawPayload : "").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de calculer la signature HMAC-SHA256", e);
        }
    }

    private void verifierSignatureHmac(String rawPayload, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("[WEBHOOK WAVE REFUSÉ] Secret webhook Wave non configuré");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook Wave non configuré.");
        }
        if (signatureHeader == null || signatureHeader.isBlank() || rawPayload == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Signature webhook Wave manquante.");
        }

        String provided = signatureHeader.trim();
        if (provided.toLowerCase(Locale.ROOT).startsWith("sha256=")) {
            provided = provided.substring("sha256=".length()).trim();
        }
        provided = provided.toLowerCase(Locale.ROOT);

        String expected = computeHmacSha256Hex(rawPayload).toLowerCase(Locale.ROOT);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8))) {
            log.warn("[WEBHOOK WAVE REFUSÉ] Signature HMAC-SHA256 invalide");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Signature webhook Wave invalide.");
        }
    }
}
