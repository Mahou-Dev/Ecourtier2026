package com.ecourtier.domain.transaction.gateway;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Propriétés de configuration PayTech (Sandbox uniquement).
 * Les clés API ne doivent jamais être exposées dans les logs, réponses API ou le frontend.
 */
@Component
@ConfigurationProperties(prefix = "ecourtier.payment.paytech")
@Getter
@Setter
public class PayTechPaymentProperties {

    public static final String SANDBOX_ENV = "test";
    public static final String DEFAULT_TARGET_PAYMENT = "Wave";
    public static final String DEFAULT_BASE_URL = "https://paytech.sn/api";

    private String apiKey = "";
    private String apiSecret = "";
    private String env = SANDBOX_ENV;
    private String baseUrl = DEFAULT_BASE_URL;
    private String ipnUrl = "";
    private String refundNotifUrl = "";
    private String successUrl = "";
    private String cancelUrl = "";
    private String targetPayment = DEFAULT_TARGET_PAYMENT;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;
    /**
     * Par défaut false : la documentation officielle PayTech indique que env=test
     * peut débiter 100 à 150 FCFA réels lors du parcours checkout.
     */
    private boolean zeroDebitGuaranteed = false;

    /**
     * Bloque strictement toute tentative de configuration hors Sandbox (ex: env=prod ou valeur vide).
     */
    public void assertSandboxOnly() {
        String normalizedEnv = env != null ? env.trim().toLowerCase(Locale.ROOT) : "";
        if (!SANDBOX_ENV.equals(normalizedEnv)) {
            throw new IllegalStateException(
                    "REFUS SÉCURITÉ SANDBOX : PayTech est strictement restreint à env=test (valeur détectée : '"
                            + normalizedEnv + "'). Aucun appel de production n'est autorisé."
            );
        }
    }

    /**
     * AUDIT-PT-001 : Verrou backend indépendant garantissant l'absence de tout paiement réseau
     * susceptible d'entraîner un débit réel (100 à 150 FCFA en mode PayTech env=test).
     */
    public void assertZeroDebitGuaranteed() {
        if (!zeroDebitGuaranteed) {
            throw new IllegalStateException(
                    "REFUS SÉCURITÉ ZÉRO DÉBIT : aucun appel d'initialisation PayTech n'est autorisé tant que le zéro débit réel n'est pas garanti "
                            + "(ecourtier.payment.paytech.zero-debit-guaranteed=false ; PayTech env=test peut prélever 100 à 150 FCFA réels)."
            );
        }
    }

    /**
     * Vérifie que tous les paramètres requis pour PayTech Sandbox sont renseignés et valides.
     */
    public void validateSandboxReady() {
        assertSandboxOnly();
        List<String> missing = getMissingRequiredParameters();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Configuration PayTech Sandbox incomplète. Variables manquantes ou invalides : "
                            + String.join(", ", missing)
            );
        }
        validateHttpUrl("PAYTECH_BASE_URL", baseUrl, false);
        validateHttpUrl("PAYTECH_IPN_URL", ipnUrl, true);
        if (!isBlank(refundNotifUrl)) {
            validateHttpUrl("PAYTECH_REFUND_NOTIF_URL", refundNotifUrl, true);
        }
        validateHttpUrl("PAYTECH_SUCCESS_URL", successUrl, false);
        validateHttpUrl("PAYTECH_CANCEL_URL", cancelUrl, false);
    }

    /**
     * Vérifie à la fois la configuration Sandbox complète ET le verrou backend zéro débit
     * avant tout appel sortant d'initialisation de paiement (/payment/request-payment).
     */
    public void validateOutboundPaymentAllowed() {
        validateSandboxReady();
        assertZeroDebitGuaranteed();
    }

    public List<String> getMissingRequiredParameters() {
        List<String> missing = new ArrayList<>();
        if (isBlank(apiKey)) {
            missing.add("PAYTECH_API_KEY");
        }
        if (isBlank(apiSecret)) {
            missing.add("PAYTECH_API_SECRET");
        }
        if (isBlank(baseUrl)) {
            missing.add("PAYTECH_BASE_URL");
        }
        if (isBlank(ipnUrl)) {
            missing.add("PAYTECH_IPN_URL");
        }
        if (isBlank(successUrl)) {
            missing.add("PAYTECH_SUCCESS_URL");
        }
        if (isBlank(cancelUrl)) {
            missing.add("PAYTECH_CANCEL_URL");
        }
        return missing;
    }

    public boolean hasApiCredentials() {
        return !isBlank(apiKey) && !isBlank(apiSecret);
    }

    public String getEffectiveTargetPayment() {
        return isBlank(targetPayment) ? DEFAULT_TARGET_PAYMENT : targetPayment.trim();
    }

    public String getEffectiveBaseUrl() {
        String raw = isBlank(baseUrl) ? DEFAULT_BASE_URL : baseUrl.trim();
        return raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
    }

    private void validateHttpUrl(String name, String value, boolean requireHttpsUnlessLocal) {
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
            String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
            boolean isLocal = "localhost".equals(host) || "127.0.0.1".equals(host);
            if (!"https".equals(scheme) && !("http".equals(scheme) && (!requireHttpsUnlessLocal || isLocal))) {
                throw new IllegalStateException(
                        "Configuration PayTech Sandbox invalide : " + name + " doit être une URL HTTPS valide."
                );
            }
            if (host.isBlank()) {
                throw new IllegalStateException(
                        "Configuration PayTech Sandbox invalide : " + name + " ne contient pas d'hôte valide."
                );
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Configuration PayTech Sandbox invalide : " + name + " n'est pas une URI valide.", ex
            );
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "PayTechPaymentProperties{"
                + "apiKey=" + (isBlank(apiKey) ? "[UNSET]" : "[REDACTED]")
                + ", apiSecret=" + (isBlank(apiSecret) ? "[UNSET]" : "[REDACTED]")
                + ", env='" + env + '\''
                + ", baseUrl='" + baseUrl + '\''
                + ", targetPayment='" + targetPayment + '\''
                + ", zeroDebitGuaranteed=" + zeroDebitGuaranteed
                + '}';
    }
}
