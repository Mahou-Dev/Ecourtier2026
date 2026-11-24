package com.ecourtier.domain.transaction.controller;

import com.ecourtier.domain.transaction.dto.PayTechWebhookPayloadDTO;
import com.ecourtier.domain.transaction.dto.PayTechWebhookResponseDTO;
import com.ecourtier.domain.transaction.service.PayTechWebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoint IPN PayTech (Sandbox uniquement).
 * Accepte les notifications JSON et application/x-www-form-urlencoded envoyées par PayTech.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Transactions & Séquestre")
public class PayTechWebhookController {

    private static final int MAX_WEBHOOK_BODY_BYTES = 64 * 1024;

    private final PayTechWebhookService payTechWebhookService;
    private final ObjectMapper objectMapper;

    @PostMapping(
            value = "/webhooks/paytech",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.ALL_VALUE}
    )
    @SecurityRequirements
    @Operation(
            summary = "Recevoir une notification IPN PayTech Sandbox (HMAC-SHA256 / SHA-256)",
            description = "Endpoint IPN PayTech authentifié par hmac_compute (HMAC-SHA256) ou api_key_sha256/api_secret_sha256. "
                    + "Traite de manière idempotente sale_complete, sale_canceled, refund_complete, transfer_success et transfer_failed en environnement Sandbox uniquement (env=test)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification IPN PayTech authentifiée et traitée de manière idempotente"),
            @ApiResponse(responseCode = "400", description = "Payload IPN invalide ou type_event non supporté"),
            @ApiResponse(responseCode = "401", description = "Clés PayTech non configurées ou jeton d'authentification manquant"),
            @ApiResponse(responseCode = "403", description = "Signature HMAC/SHA-256 invalide ou environnement prod refusé"),
            @ApiResponse(responseCode = "404", description = "Référence PayTech (ref_command / id_transfer) introuvable"),
            @ApiResponse(responseCode = "409", description = "Conflit de référence ou d'état")
    })
    public ResponseEntity<PayTechWebhookResponseDTO> recevoirIpnPayTech(HttpServletRequest request) {
        if (request.getContentLengthLong() > MAX_WEBHOOK_BODY_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Corps de notification IPN PayTech trop volumineux.");
        }
        String contentType = request.getContentType() != null ? request.getContentType().toLowerCase() : "";
        PayTechWebhookPayloadDTO payload;
        String rawBody = null;

        // AUDIT-PT-009 : N'utiliser getParameterMap() que pour application/x-www-form-urlencoded (jamais si JSON avec query params)
        if (contentType.contains(MediaType.APPLICATION_FORM_URLENCODED_VALUE)) {
            Map<String, String> params = new LinkedHashMap<>();
            request.getParameterMap().forEach((key, values) -> {
                if (values != null && values.length > 0) {
                    params.put(key, values[0]);
                }
            });
            payload = payTechWebhookService.parseFromMap(params);
        } else {
            try {
                byte[] bytes = request.getInputStream().readNBytes(MAX_WEBHOOK_BODY_BYTES + 1);
                if (bytes.length > MAX_WEBHOOK_BODY_BYTES) {
                    throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Corps de notification IPN PayTech trop volumineux.");
                }
                rawBody = new String(bytes, StandardCharsets.UTF_8);
            } catch (ResponseStatusException e) {
                throw e;
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible de lire le corps de la notification PayTech.", e);
            }
            if (rawBody.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Corps de notification IPN PayTech vide.");
            }
            try {
                payload = objectMapper.readValue(rawBody, PayTechWebhookPayloadDTO.class);
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload JSON IPN PayTech invalide.", e);
            }
        }

        PayTechWebhookResponseDTO response = payTechWebhookService.traiterNotification(payload, rawBody);
        return ResponseEntity.ok(response);
    }
}
