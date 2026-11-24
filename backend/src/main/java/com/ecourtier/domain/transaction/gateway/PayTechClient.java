package com.ecourtier.domain.transaction.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Client HTTP Spring RestClient dédié à l'API officielle PayTech (https://doc.paytech.sn/doc_paytech.php).
 * Restreint strictement à l'environnement Sandbox (env=test).
 * Ne journalise jamais API_KEY ni API_SECRET.
 */
@Component
@Slf4j
public class PayTechClient {

    private final PayTechPaymentProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
    public PayTechClient(PayTechPaymentProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, buildDefaultRestClient(properties));
    }

    public PayTechClient(PayTechPaymentProperties properties, ObjectMapper objectMapper, RestClient restClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    private static RestClient buildDefaultRestClient(PayTechPaymentProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(500, properties.getConnectTimeoutMs()));
        factory.setReadTimeout(Math.max(1000, properties.getReadTimeoutMs()));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    /**
     * Initialise une session de paiement PayTech Sandbox : POST {baseUrl}/payment/request-payment.
     * Exige env=test ET zeroDebitGuaranteed=true (verrou backend anti-débit 100-150 FCFA)
     * et ne retourne jamais un statut payé/confirmé.
     */
    public PayTechRequestPaymentResult requestPayment(PaymentGateway.PaymentInitRequest request) {
        properties.validateOutboundPaymentAllowed();

        if (request == null || request.getTransactionId() == null) {
            throw new IllegalArgumentException("Requête PayTech invalide : transactionId obligatoire.");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Requête PayTech invalide : montant strictement positif requis.");
        }
        if (request.getRefCommand() == null || request.getRefCommand().isBlank()) {
            throw new IllegalArgumentException("Requête PayTech invalide : ref_command obligatoire.");
        }

        long itemPriceXof = request.getAmount().setScale(0, RoundingMode.HALF_UP).longValueExact();
        String targetPayment = (request.getPaymentMethod() != null && !request.getPaymentMethod().isBlank())
                ? request.getPaymentMethod().trim()
                : properties.getEffectiveTargetPayment();

        Map<String, Object> customFieldMap = new LinkedHashMap<>();
        customFieldMap.put("transactionId", request.getTransactionId().toString());
        if (request.getAnnonceId() != null) {
            customFieldMap.put("annonceId", request.getAnnonceId().toString());
        }
        customFieldMap.put("env", PayTechPaymentProperties.SANDBOX_ENV);

        String customFieldJson;
        try {
            customFieldJson = objectMapper.writeValueAsString(customFieldMap);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Impossible de sérialiser custom_field PayTech.", e);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("item_name", request.getItemName() != null && !request.getItemName().isBlank()
                ? request.getItemName()
                : "Paiement dossier E-Courtier");
        payload.put("item_price", itemPriceXof);
        payload.put("currency", request.getCurrency() != null && !request.getCurrency().isBlank()
                ? request.getCurrency().toUpperCase(Locale.ROOT)
                : "XOF");
        payload.put("ref_command", request.getRefCommand().trim());
        payload.put("command_name", request.getCommandName() != null && !request.getCommandName().isBlank()
                ? request.getCommandName()
                : "Paiement dossier E-Courtier " + request.getRefCommand().trim());
        payload.put("env", PayTechPaymentProperties.SANDBOX_ENV);
        payload.put("target_payment", targetPayment);
        payload.put("ipn_url", properties.getIpnUrl().trim());
        if (properties.getRefundNotifUrl() != null && !properties.getRefundNotifUrl().isBlank()) {
            payload.put("refund_notif_url", properties.getRefundNotifUrl().trim());
        }
        payload.put("success_url", properties.getSuccessUrl().trim());
        payload.put("cancel_url", properties.getCancelUrl().trim());
        payload.put("custom_field", customFieldJson);

        String endpoint = properties.getEffectiveBaseUrl() + "/payment/request-payment";
        log.info("[PAYTECH SANDBOX] Initialisation paiement ref_command={} tx={} montant={} XOF target_payment={} env=test",
                request.getRefCommand(), request.getTransactionId(), itemPriceXof, targetPayment);

        String rawResponse;
        try {
            rawResponse = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("API_KEY", properties.getApiKey().trim())
                    .header("API_SECRET", properties.getApiSecret().trim())
                    .body(payload)
                    .retrieve()
                    .body(String.class);
        } catch (ResourceAccessException ex) {
            boolean isTimeout = isTimeoutException(ex);
            log.error("[PAYTECH SANDBOX] {} lors de l'initialisation ref_command={} tx={}: {}",
                    isTimeout ? "Timeout réseau" : "Erreur de connexion",
                    request.getRefCommand(), request.getTransactionId(), sanitizeErrorMessage(ex.getMessage()));
            throw new ResponseStatusException(
                    isTimeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY,
                    isTimeout
                            ? "Délai d'attente dépassé lors de la communication avec PayTech Sandbox."
                            : "Impossible de joindre le prestataire PayTech Sandbox."
            );
        } catch (RestClientResponseException ex) {
            log.error("[PAYTECH SANDBOX] Réponse HTTP {} lors de l'initialisation ref_command={} tx={}",
                    ex.getStatusCode().value(), request.getRefCommand(), request.getTransactionId());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "PayTech Sandbox a rejeté la demande de paiement (HTTP " + ex.getStatusCode().value() + ")."
            );
        } catch (RestClientException ex) {
            log.error("[PAYTECH SANDBOX] Erreur client lors de l'initialisation ref_command={} tx={}: {}",
                    request.getRefCommand(), request.getTransactionId(), sanitizeErrorMessage(ex.getMessage()));
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Erreur de communication avec PayTech Sandbox."
            );
        }

        return parseRequestPaymentResponse(rawResponse, request.getRefCommand());
    }

    /**
     * Consulte le statut d'un paiement PayTech : GET {baseUrl}/payment/get-status?token_payment={token}.
     */
    public PayTechStatusQueryResult getPaymentStatus(String tokenPayment) {
        properties.validateSandboxReady();

        if (tokenPayment == null || tokenPayment.isBlank()) {
            return PayTechStatusQueryResult.builder()
                    .state(PaymentGateway.ExternalPaymentState.UNKNOWN)
                    .message("Token de paiement PayTech manquant")
                    .build();
        }

        String endpoint = properties.getEffectiveBaseUrl() + "/payment/get-status?token_payment={token}";
        log.info("[PAYTECH SANDBOX] Consultation statut paiement token={}", tokenPayment.trim());

        String rawResponse;
        try {
            rawResponse = restClient.get()
                    .uri(endpoint, tokenPayment.trim())
                    .accept(MediaType.APPLICATION_JSON)
                    .header("API_KEY", properties.getApiKey().trim())
                    .header("API_SECRET", properties.getApiSecret().trim())
                    .retrieve()
                    .body(String.class);
        } catch (ResourceAccessException ex) {
            boolean isTimeout = isTimeoutException(ex);
            log.error("[PAYTECH SANDBOX] {} lors de get-status token={}: {}",
                    isTimeout ? "Timeout" : "Erreur connexion", tokenPayment.trim(), sanitizeErrorMessage(ex.getMessage()));
            throw new ResponseStatusException(
                    isTimeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE,
                    "Impossible de vérifier le statut du paiement auprès de PayTech Sandbox."
            );
        } catch (RestClientException ex) {
            log.error("[PAYTECH SANDBOX] Erreur HTTP lors de get-status token={}: {}",
                    tokenPayment.trim(), sanitizeErrorMessage(ex.getMessage()));
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Erreur lors de la consultation du statut PayTech Sandbox."
            );
        }

        return parsePaymentStatusResponse(rawResponse, tokenPayment.trim());
    }

    private PayTechRequestPaymentResult parseRequestPaymentResponse(String rawResponse, String refCommand) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Réponse PayTech Sandbox vide.");
        }
        try {
            PayTechRawPaymentResponse parsed = objectMapper.readValue(rawResponse, PayTechRawPaymentResponse.class);
            String redirectUrl = (parsed.getRedirectUrl() != null && !parsed.getRedirectUrl().isBlank())
                    ? parsed.getRedirectUrl().trim()
                    : (parsed.getRedirectUrlCamel() != null ? parsed.getRedirectUrlCamel().trim() : null);
            String token = parsed.getToken() != null ? parsed.getToken().trim() : null;

            if (parsed.getSuccess() == null || parsed.getSuccess() != 1
                    || token == null || token.isBlank()
                    || redirectUrl == null || redirectUrl.isBlank()) {
                String msg = parsed.getMessage() != null && !parsed.getMessage().isBlank()
                        ? parsed.getMessage()
                        : "Réponse d'initialisation PayTech invalide ou incomplète";
                log.warn("[PAYTECH SANDBOX] Initialisation refusée ref_command={} success={} message={}",
                        refCommand, parsed.getSuccess(), sanitizeErrorMessage(msg));
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Initialisation PayTech Sandbox refusée : " + sanitizeErrorMessage(msg)
                );
            }

            // AUDIT-PT-006 : Vérifier que redirect_url est bien une URL HTTPS officielle PayTech (anti-open-redirect / anti-XSS)
            validateTrustedPayTechRedirectUrl(redirectUrl, refCommand);

            return PayTechRequestPaymentResult.builder()
                    .success(true)
                    .token(token)
                    .redirectUrl(redirectUrl)
                    .refCommand(refCommand)
                    .build();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[PAYTECH SANDBOX] Réponse JSON invalide pour ref_command={}: {}",
                    refCommand, sanitizeErrorMessage(ex.getMessage()));
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Réponse PayTech Sandbox illisible ou invalide."
            );
        }
    }

    private PayTechStatusQueryResult parsePaymentStatusResponse(String rawResponse, String token) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return PayTechStatusQueryResult.builder()
                    .state(PaymentGateway.ExternalPaymentState.UNKNOWN)
                    .token(token)
                    .message("Réponse get-status PayTech vide")
                    .build();
        }
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            int success = root.path("success").asInt(0);
            if (success != 1 && !root.has("status") && !root.has("type_event")) {
                return PayTechStatusQueryResult.builder()
                        .state(PaymentGateway.ExternalPaymentState.UNKNOWN)
                        .token(token)
                        .message(root.path("message").asText("Statut PayTech non disponible"))
                        .build();
            }

            // AUDIT-PT-007 : Rejeter toute réponse indiquant un environnement autre que 'test' (prod, live, production, etc.)
            String responseEnv = root.path("env").asText("");
            if (responseEnv.isBlank()) {
                responseEnv = root.path("payment").path("env").asText("");
            }
            if (!responseEnv.isBlank() && !PayTechPaymentProperties.SANDBOX_ENV.equalsIgnoreCase(responseEnv.trim())) {
                throw new IllegalStateException("REFUS SÉCURITÉ SANDBOX : PayTech a retourné env=" + responseEnv.trim() + " (seul env=test est autorisé).");
            }

            String rawStatus = firstNonBlank(
                    root.path("type_event").asText(null),
                    root.path("status").asText(null),
                    root.path("payment_status").asText(null),
                    root.path("payment").path("status").asText(null),
                    root.path("payment").path("state").asText(null)
            );
            String refCommand = firstNonBlank(
                    root.path("ref_command").asText(null),
                    root.path("payment").path("ref_command").asText(null)
            );
            BigDecimal amount = parseDecimal(root.path("final_item_price"), root.path("item_price"), root.path("payment").path("item_price"));

            PaymentGateway.ExternalPaymentState mappedState = mapStatus(rawStatus);
            if (mappedState == PaymentGateway.ExternalPaymentState.CONFIRMED
                    && !PayTechPaymentProperties.SANDBOX_ENV.equalsIgnoreCase(responseEnv.trim())) {
                throw new IllegalStateException("REFUS SÉCURITÉ SANDBOX : confirmation PayTech sans preuve explicite env=test refusée.");
            }
            return PayTechStatusQueryResult.builder()
                    .state(mappedState)
                    .token(token)
                    .refCommand(refCommand)
                    .amount(amount)
                    .rawStatus(rawStatus)
                    .message("Statut PayTech consulté : " + (rawStatus != null ? rawStatus : "inconnu"))
                    .build();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("[PAYTECH SANDBOX] Impossible de parser get-status token={}: {}", token, sanitizeErrorMessage(ex.getMessage()));
            return PayTechStatusQueryResult.builder()
                    .state(PaymentGateway.ExternalPaymentState.UNKNOWN)
                    .token(token)
                    .message("Réponse get-status PayTech invalide")
                    .build();
        }
    }

    private PaymentGateway.ExternalPaymentState mapStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return PaymentGateway.ExternalPaymentState.UNKNOWN;
        }
        String normalized = rawStatus.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "sale_complete", "completed", "complete", "paid", "success", "succeeded" ->
                    PaymentGateway.ExternalPaymentState.CONFIRMED;
            case "sale_canceled", "canceled", "cancelled", "expired" ->
                    PaymentGateway.ExternalPaymentState.CANCELED;
            case "failed", "error", "rejected" ->
                    PaymentGateway.ExternalPaymentState.FAILED;
            case "pending", "initiated", "processing", "waiting", "en_attente" ->
                    PaymentGateway.ExternalPaymentState.PENDING;
            default -> PaymentGateway.ExternalPaymentState.UNKNOWN;
        };
    }

    private BigDecimal parseDecimal(JsonNode... nodes) {
        for (JsonNode n : nodes) {
            if (n != null && !n.isMissingNode() && !n.isNull()) {
                if (n.isNumber()) {
                    return n.decimalValue();
                }
                String txt = n.asText("").trim();
                if (!txt.isEmpty()) {
                    try {
                        return new BigDecimal(txt);
                    } catch (NumberFormatException ignored) {
                        // continue
                    }
                }
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private boolean isTimeoutException(Throwable ex) {
        Throwable curr = ex;
        while (curr != null) {
            if (curr instanceof SocketTimeoutException) {
                return true;
            }
            if (curr.getMessage() != null && curr.getMessage().toLowerCase(Locale.ROOT).contains("timed out")) {
                return true;
            }
            curr = curr.getCause();
        }
        return false;
    }

    private void validateTrustedPayTechRedirectUrl(String redirectUrl, String refCommand) {
        try {
            URI uri = URI.create(redirectUrl);
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
            String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
            boolean trustedHost = "paytech.sn".equals(host) || host.endsWith(".paytech.sn");
            if (!"https".equals(scheme) || !trustedHost || uri.getUserInfo() != null) {
                log.warn("[PAYTECH SANDBOX] URL de redirection non fiable rejetée pour ref_command={} (scheme={}, host={})",
                        refCommand, scheme, host);
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "URL de redirection PayTech invalide ou domaine non autorisé."
                );
            }
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "URL de redirection PayTech malformée."
            );
        }
    }

    private String sanitizeErrorMessage(String raw) {
        if (raw == null) {
            return "";
        }
        String sanitized = raw;
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            sanitized = sanitized.replace(properties.getApiKey(), "[REDACTED]");
        }
        if (properties.getApiSecret() != null && !properties.getApiSecret().isBlank()) {
            sanitized = sanitized.replace(properties.getApiSecret(), "[REDACTED]");
        }
        return sanitized;
    }

    public static String generateUniqueRefCommand(UUID transactionId) {
        String shortTx = transactionId != null ? transactionId.toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT) : "UNKNOWN";
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "ECOURTIER-" + shortTx + "-" + suffix;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PayTechRawPaymentResponse {
        private Integer success;
        private String token;
        @JsonProperty("redirect_url")
        private String redirectUrl;
        @JsonProperty("redirectUrl")
        private String redirectUrlCamel;
        private String message;
    }

    @Data
    @Builder
    public static class PayTechRequestPaymentResult {
        private boolean success;
        private String token;
        private String redirectUrl;
        private String refCommand;
    }

    @Data
    @Builder
    public static class PayTechStatusQueryResult {
        private PaymentGateway.ExternalPaymentState state;
        private String token;
        private String refCommand;
        private BigDecimal amount;
        private String rawStatus;
        private String message;
    }
}
