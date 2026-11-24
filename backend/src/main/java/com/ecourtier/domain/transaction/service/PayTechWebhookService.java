package com.ecourtier.domain.transaction.service;

import com.ecourtier.domain.transaction.dto.PayTechWebhookPayloadDTO;
import com.ecourtier.domain.transaction.dto.PayTechWebhookResponseDTO;
import com.ecourtier.domain.transaction.dto.TransactionResponseDTO;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import com.ecourtier.domain.transaction.gateway.PayTechPaymentProperties;
import com.ecourtier.domain.transaction.repository.PaiementWaveRepository;
import com.ecourtier.domain.transaction.repository.TransactionRepository;
import com.ecourtier.domain.transaction.service.TransactionService.PaymentConfirmationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service de traitement des notifications IPN officielles PayTech (https://doc.paytech.sn/doc_paytech.php).
 *
 * Sécurité implémentée :
 * 1. Vérification HMAC-SHA256 via {@code hmac_compute} (méthode recommandée PayTech) :
 *    - Paiements/remboursements : HMAC-SHA256(api_secret, "${final_item_price || item_price}|${ref_command}|${api_key}")
 *    - Transferts : HMAC-SHA256(api_secret, "${amount}|${id_transfer}|${api_key}")
 * 2. Fallback SHA-256 des clés ({@code api_key_sha256} + {@code api_secret_sha256}) uniquement lorsque {@code hmac_compute}
 *    n'est pas transmis par PayTech (ex: refund_complete / transfer_failed).
 * 3. Rejet strict de tout événement {@code env=prod} (mode Sandbox uniquement).
 * 4. Comparaison en temps constant ({@link MessageDigest#isEqual}) et aucune fuite de secret dans les logs.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PayTechWebhookService {

    private final PayTechPaymentProperties properties;
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final PaiementWaveRepository paiementWaveRepository;
    private final ObjectMapper objectMapper;

    public PayTechWebhookResponseDTO traiterNotification(PayTechWebhookPayloadDTO payload, String rawAuditPayload) {
        if (payload == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload IPN PayTech manquant.");
        }

        // 1. Authentification stricte + verrou Sandbox (env=test uniquement)
        verifierAuthenticitePayTech(payload);

        String typeEvent = payload.getTypeEvent() != null
                ? payload.getTypeEvent().trim().toLowerCase(Locale.ROOT)
                : "";
        if (typeEvent.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Champ type_event obligatoire dans l'IPN PayTech.");
        }

        String sanitizedAudit = sanitizeAuditPayload(payload);

        return switch (typeEvent) {
            case "sale_complete" -> handleSaleComplete(payload, sanitizedAudit);
            case "sale_canceled" -> handleSaleCanceled(payload, sanitizedAudit);
            case "refund_complete" -> handleRefundComplete(payload, sanitizedAudit);
            case "transfer_success" -> handleTransferEvent(payload, true, sanitizedAudit);
            case "transfer_failed" -> handleTransferEvent(payload, false, sanitizedAudit);
            default -> {
                log.warn("[IPN PAYTECH] Événement non supporté type_event={}", typeEvent);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type d'événement PayTech non supporté : " + typeEvent);
            }
        };
    }

    public PayTechWebhookPayloadDTO parseFromMap(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        return PayTechWebhookPayloadDTO.builder()
                .typeEvent(params.get("type_event"))
                .refCommand(params.get("ref_command"))
                .itemName(params.get("item_name"))
                .itemPrice(params.get("item_price"))
                .finalItemPrice(params.get("final_item_price"))
                .initialItemPrice(params.get("initial_item_price"))
                .currency(params.get("currency"))
                .commandName(params.get("command_name"))
                .token(params.get("token"))
                .env(params.get("env"))
                .paymentMethod(params.get("payment_method"))
                .clientPhone(params.get("client_phone"))
                .customField(params.get("custom_field"))
                .idTransfer(params.get("id_transfer"))
                .tokenTransfer(params.get("token_transfer"))
                .externalId(params.get("external_id"))
                .amount(params.get("amount"))
                .state(params.get("state"))
                .serviceName(params.get("service_name"))
                .destinationNumber(params.get("destination_number"))
                .hmacCompute(params.get("hmac_compute"))
                .apiKeySha256(params.get("api_key_sha256"))
                .apiSecretSha256(params.get("api_secret_sha256"))
                .build();
    }

    private PayTechWebhookResponseDTO handleSaleComplete(PayTechWebhookPayloadDTO payload, String auditPayload) {
        String refCommand = requireNonBlank(payload.getRefCommand(), "ref_command");

        // AUDIT-PT-002 : Montant obligatoire strictement positif + devise XOF obligatoire
        BigDecimal receivedAmount = parseAmount(firstNonBlank(payload.getFinalItemPrice(), payload.getItemPrice()));
        if (receivedAmount == null || receivedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Montant obligatoire et strictement positif dans l'IPN sale_complete.");
        }
        String currency = payload.getCurrency() != null ? payload.getCurrency().trim() : "";
        if (!"XOF".equalsIgnoreCase(currency)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Devise IPN PayTech invalide ou absente (XOF attendu).");
        }

        UUID txId = resolveTransactionIdStrict(refCommand, payload.getToken())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Aucune transaction connue pour la référence PayTech : " + refCommand
                ));

        String externalRef = firstNonBlank(payload.getToken(), refCommand);

        PaymentConfirmationResult result = transactionService.confirmExternalPayment(
                txId,
                null,
                externalRef,
                refCommand,
                "PAYTECH",
                false,
                receivedAmount,
                auditPayload
        );

        String statutPaiement = result.latePayment() ? "TARDIF_A_REMBOURSER" : "CONFIRME";
        boolean replay = !result.stateUpdated() && !result.latePayment();
        String msg = result.latePayment()
                ? (result.lateReason() != null ? result.lateReason() : "Paiement tardif marqué TARDIF_A_REMBOURSER")
                : (replay ? "Notification sale_complete déjà traitée (idempotent)" : "Paiement PayTech confirmé");

        log.info("[IPN PAYTECH sale_complete] tx={} ref_command={} statut={} late={} replay={}",
                txId, refCommand, statutPaiement, result.latePayment(), replay);

        return PayTechWebhookResponseDTO.builder()
                .transactionId(txId)
                .typeEvent("sale_complete")
                .refCommand(refCommand)
                .statutPaiement(statutPaiement)
                .statutSequestre(result.dto() != null ? result.dto().getStatutSequestre() : null)
                .latePayment(result.latePayment())
                .idempotentReplay(replay)
                .message(msg)
                .build();
    }

    private PayTechWebhookResponseDTO handleSaleCanceled(PayTechWebhookPayloadDTO payload, String auditPayload) {
        String refCommand = requireNonBlank(payload.getRefCommand(), "ref_command");
        UUID txId = resolveTransactionIdStrict(refCommand, payload.getToken())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Aucune transaction connue pour la référence PayTech : " + refCommand
                ));

        TransactionResponseDTO dto = transactionService.markExternalPaymentCanceled(
                txId,
                refCommand,
                payload.getToken(),
                "Paiement annulé par notification PayTech (sale_canceled)",
                auditPayload
        );

        log.info("[IPN PAYTECH sale_canceled] tx={} ref_command={} statutSequestre={}",
                txId, refCommand, dto != null ? dto.getStatutSequestre() : null);

        return PayTechWebhookResponseDTO.builder()
                .transactionId(txId)
                .typeEvent("sale_canceled")
                .refCommand(refCommand)
                .statutPaiement("ANNULE")
                .statutSequestre(dto != null ? dto.getStatutSequestre() : null)
                .latePayment(false)
                .idempotentReplay(false)
                .message("Paiement annulé enregistré")
                .build();
    }

    private PayTechWebhookResponseDTO handleRefundComplete(PayTechWebhookPayloadDTO payload, String auditPayload) {
        String refCommand = requireNonBlank(payload.getRefCommand(), "ref_command");
        UUID txId = resolveTransactionIdStrict(refCommand, payload.getToken())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Aucune transaction connue pour le remboursement PayTech : " + refCommand
                ));

        BigDecimal refundedAmount = parseAmount(firstNonBlank(payload.getFinalItemPrice(), payload.getItemPrice()));
        if (refundedAmount == null || refundedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Montant de remboursement obligatoire dans l'IPN refund_complete.");
        }
        String currency = payload.getCurrency() != null ? payload.getCurrency().trim() : "";
        if (!currency.isEmpty() && !"XOF".equalsIgnoreCase(currency)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Devise IPN refund_complete invalide (XOF attendu).");
        }
        TransactionResponseDTO dto = transactionService.handleExternalRefundNotification(
                txId,
                refCommand,
                payload.getToken(),
                refundedAmount,
                auditPayload
        );

        log.info("[IPN PAYTECH refund_complete] tx={} ref_command={}", txId, refCommand);

        return PayTechWebhookResponseDTO.builder()
                .transactionId(txId)
                .typeEvent("refund_complete")
                .refCommand(refCommand)
                .statutPaiement("REMBOURSE")
                .statutSequestre(dto != null ? dto.getStatutSequestre() : null)
                .latePayment(false)
                .idempotentReplay(false)
                .message("Notification refund_complete traitée")
                .build();
    }

    private PayTechWebhookResponseDTO handleTransferEvent(PayTechWebhookPayloadDTO payload, boolean success, String auditPayload) {
        String idTransfer = payload.getIdTransfer();
        String externalId = payload.getExternalId();
        if ((idTransfer == null || idTransfer.isBlank()) && (externalId == null || externalId.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id_transfer ou external_id obligatoire pour un événement transfer.");
        }

        TransactionResponseDTO dto = transactionService.handleExternalTransferNotification(
                idTransfer,
                externalId,
                success,
                payload.getState(),
                auditPayload
        );

        String eventName = success ? "transfer_success" : "transfer_failed";
        log.info("[IPN PAYTECH {}] id_transfer={} external_id={} tx={}",
                eventName, idTransfer, externalId, dto != null ? dto.getId() : null);

        return PayTechWebhookResponseDTO.builder()
                .transactionId(dto != null ? dto.getId() : null)
                .typeEvent(eventName)
                .refCommand(firstNonBlank(idTransfer, externalId))
                .statutPaiement(success ? "CONFIRME" : "ECHOUE")
                .statutSequestre(dto != null ? dto.getStatutSequestre() : null)
                .latePayment(false)
                .idempotentReplay(false)
                .message("Notification " + eventName + " traitée")
                .build();
    }

    /**
     * Vérifie l'authenticité cryptographique d'un IPN PayTech et interdit strictement env != test.
     * AUDIT-PT-002 :
     * - hmac_compute (HMAC-SHA256 liant montant + référence + clé) est OBLIGATOIRE pour sale_complete,
     *   sale_canceled et transfer_success (aucun downgrade vers les hashes statiques SHA-256).
     * - Le fallback api_key_sha256 + api_secret_sha256 n'est autorisé que pour refund_complete et
     *   transfer_failed (événements où la doc PayTech omet hmac_compute).
     * - env=test est obligatoire pour les événements paiement/remboursement ; tout env != test est rejeté (403).
     */
    public void verifierAuthenticitePayTech(PayTechWebhookPayloadDTO payload) {
        try {
            properties.assertSandboxOnly();
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        }

        if (!properties.hasApiCredentials()) {
            log.error("[IPN PAYTECH REFUSÉ] Clés API PayTech non configurées côté backend");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification IPN PayTech non configurée.");
        }

        String typeEvent = payload.getTypeEvent() != null
                ? payload.getTypeEvent().trim().toLowerCase(Locale.ROOT)
                : "";
        boolean isTransfer = typeEvent.startsWith("transfer_");

        // Rejet strict de toute notification hors Sandbox :
        // - env=test obligatoire sur sale_complete, sale_canceled, refund_complete
        // - sur transfer_*, si env est fourni il doit impérativement valoir test
        String rawEnv = payload.getEnv() != null ? payload.getEnv().trim() : "";
        if (!rawEnv.isEmpty() && !PayTechPaymentProperties.SANDBOX_ENV.equalsIgnoreCase(rawEnv)) {
            log.warn("[IPN PAYTECH REFUSÉ] Notification reçue avec env='{}' (seul env=test est autorisé)", rawEnv);
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Notification PayTech refusée : seul l'environnement Sandbox (env=test) est autorisé."
            );
        }
        if (!isTransfer && rawEnv.isEmpty()) {
            log.warn("[IPN PAYTECH REFUSÉ] Champ env absent pour type_event={} (env=test obligatoire)", typeEvent);
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Notification PayTech refusée : champ env=test obligatoire."
            );
        }

        String apiKey = properties.getApiKey().trim();
        String apiSecret = properties.getApiSecret().trim();
        String hmacProvided = payload.getHmacCompute() != null ? payload.getHmacCompute().trim().toLowerCase(Locale.ROOT) : "";
        String keyHashProvided = payload.getApiKeySha256() != null ? payload.getApiKeySha256().trim().toLowerCase(Locale.ROOT) : "";
        String secretHashProvided = payload.getApiSecretSha256() != null ? payload.getApiSecretSha256().trim().toLowerCase(Locale.ROOT) : "";

        boolean hasHmac = !hmacProvided.isEmpty();
        boolean hasSha256Pair = !keyHashProvided.isEmpty() && !secretHashProvided.isEmpty();
        boolean sha256FallbackAllowed = "refund_complete".equals(typeEvent) || "transfer_failed".equals(typeEvent);

        if (!hasHmac) {
            if (!sha256FallbackAllowed) {
                log.warn("[IPN PAYTECH REFUSÉ] Signature hmac_compute obligatoire pour type_event={}", typeEvent);
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Signature HMAC-SHA256 (hmac_compute) obligatoire pour l'événement " + typeEvent + "."
                );
            }
            if (!hasSha256Pair) {
                log.warn("[IPN PAYTECH REFUSÉ] Aucun jeton d'authentification (hmac_compute ou api_key_sha256/api_secret_sha256)");
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Signature d'authentification PayTech manquante.");
            }
        }

        if (hasHmac) {
            String expectedHmac;
            if (isTransfer) {
                String amountStr = firstNonBlank(payload.getAmount());
                String transferId = firstNonBlank(payload.getIdTransfer());
                if (amountStr == null || transferId == null) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Champs requis pour vérification HMAC transfert manquants.");
                }
                expectedHmac = computeTransferHmacHex(amountStr, transferId, apiKey, apiSecret);
            } else {
                String amountStr = firstNonBlank(payload.getFinalItemPrice(), payload.getItemPrice());
                String refCmd = firstNonBlank(payload.getRefCommand());
                if (amountStr == null || refCmd == null) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Champs requis pour vérification HMAC paiement manquants.");
                }
                expectedHmac = computePaymentHmacHex(amountStr, refCmd, apiKey, apiSecret);
            }

            if (!constantTimeEquals(expectedHmac, hmacProvided)) {
                log.warn("[IPN PAYTECH REFUSÉ] Signature HMAC-SHA256 invalide pour ref={}", firstNonBlank(payload.getRefCommand(), payload.getIdTransfer()));
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Signature HMAC PayTech invalide.");
            }
        }

        // Si api_key_sha256 ou api_secret_sha256 sont fournis (en complément ou en fallback autorisé), ils doivent correspondre
        if (hasSha256Pair || !keyHashProvided.isEmpty() || !secretHashProvided.isEmpty()) {
            String expectedKeyHash = sha256Hex(apiKey);
            String expectedSecretHash = sha256Hex(apiSecret);
            if (!constantTimeEquals(expectedKeyHash, keyHashProvided)
                    || !constantTimeEquals(expectedSecretHash, secretHashProvided)) {
                log.warn("[IPN PAYTECH REFUSÉ] Empreintes SHA-256 des clés API PayTech invalides");
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Clés SHA-256 PayTech invalides.");
            }
        }
    }

    public String computePaymentHmacHex(String amountStr, String refCommand, String apiKey, String apiSecret) {
        String message = amountStr + "|" + refCommand + "|" + apiKey;
        return hmacSha256Hex(message, apiSecret);
    }

    public String computeTransferHmacHex(String amountStr, String idTransfer, String apiKey, String apiSecret) {
        String message = amountStr + "|" + idTransfer + "|" + apiKey;
        return hmacSha256Hex(message, apiSecret);
    }

    public String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8))).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            throw new IllegalStateException("Algorithme SHA-256 indisponible", e);
        }
    }

    private String hmacSha256Hex(String message, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de calculer HMAC-SHA256", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * AUDIT-PT-004 : Résolution stricte par ref_command (et contrôle de cohérence token).
     * Aucun fallback permissif vers custom_field lorsque ref_command est inconnu, et projections
     * JPQL directes sur transaction.id afin d'éviter tout accès à un proxy LAZY hors session.
     */
    private Optional<UUID> resolveTransactionIdStrict(String refCommand, String token) {
        Optional<UUID> txIdByRef = Optional.empty();
        if (refCommand != null && !refCommand.isBlank()) {
            String normalizedRef = refCommand.trim();
            txIdByRef = paiementWaveRepository.findTransactionIdByRefCommand(normalizedRef)
                    .or(() -> transactionRepository.findByRefCommandPaytech(normalizedRef).map(TransactionEntity::getId));
        }

        Optional<UUID> txIdByToken = Optional.empty();
        if (token != null && !token.isBlank()) {
            String normalizedTok = token.trim();
            txIdByToken = paiementWaveRepository.findTransactionIdByTokenOrReference(normalizedTok)
                    .or(() -> transactionRepository.findByTokenPaiementExterne(normalizedTok).map(TransactionEntity::getId));
        }

        if (txIdByRef.isPresent() && txIdByToken.isPresent() && !txIdByRef.get().equals(txIdByToken.get())) {
            log.warn("[IPN PAYTECH CONFLIT] ref_command={} pointe vers tx={} mais token={} pointe vers tx={}",
                    refCommand, txIdByRef.get(), token, txIdByToken.get());
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Incohérence IPN PayTech : ref_command et token désignent deux transactions différentes."
            );
        }

        if (refCommand != null && !refCommand.isBlank()) {
            return txIdByRef;
        }
        return txIdByToken;
    }

    private BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Montant IPN PayTech invalide : " + raw);
        }
    }

    private String sanitizeAuditPayload(PayTechWebhookPayloadDTO payload) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "type_event", nullToEmpty(payload.getTypeEvent()),
                    "ref_command", nullToEmpty(payload.getRefCommand()),
                    "token", nullToEmpty(payload.getToken()),
                    "item_price", nullToEmpty(firstNonBlank(payload.getFinalItemPrice(), payload.getItemPrice(), payload.getAmount())),
                    "currency", nullToEmpty(payload.getCurrency()),
                    "payment_method", nullToEmpty(payload.getPaymentMethod()),
                    "env", nullToEmpty(payload.getEnv()),
                    "id_transfer", nullToEmpty(payload.getIdTransfer()),
                    "state", nullToEmpty(payload.getState())
            ));
        } catch (Exception e) {
            return "{\"type_event\":\"" + nullToEmpty(payload.getTypeEvent()) + "\"}";
        }
    }

    private String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Champ obligatoire manquant dans l'IPN PayTech : " + fieldName);
        }
        return value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }
}
