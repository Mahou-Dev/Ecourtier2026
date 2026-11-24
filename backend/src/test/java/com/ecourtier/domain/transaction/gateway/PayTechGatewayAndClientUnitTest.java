package com.ecourtier.domain.transaction.gateway;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ecourtier.domain.transaction.gateway.PaymentGateway.ExternalPaymentState;
import com.ecourtier.domain.transaction.gateway.PaymentGateway.OwnerTransferResult;
import com.ecourtier.domain.transaction.gateway.PaymentGateway.PaymentInitRequest;
import com.ecourtier.domain.transaction.gateway.PaymentGateway.PaymentInitResponse;
import com.ecourtier.domain.transaction.gateway.PaymentGateway.PaymentRefundResult;
import com.ecourtier.domain.transaction.gateway.PaymentGateway.PaymentStatusResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("RENDER-PAYTECH-01 — Tests unitaires & MockRestServiceServer PayTech Sandbox (zéro appel réseau réel)")
class PayTechGatewayAndClientUnitTest {

    private static final String TEST_API_KEY = "pt_sandbox_key_sentinel_99887766";
    private static final String TEST_API_SECRET = "pt_sandbox_secret_sentinel_55443322";

    private ObjectMapper objectMapper;
    private PayTechPaymentProperties properties;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private PayTechClient payTechClient;
    private PayTechPaymentGateway gateway;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger transactionLogger;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new PayTechPaymentProperties();
        properties.setApiKey(TEST_API_KEY);
        properties.setApiSecret(TEST_API_SECRET);
        properties.setEnv("test");
        properties.setBaseUrl("https://paytech.sn/api");
        properties.setIpnUrl("https://ecourtier-backend.test/api/v1/webhooks/paytech");
        properties.setSuccessUrl("https://ecourtier-frontend.test/dashboard?tab=transactions&payment=success");
        properties.setCancelUrl("https://ecourtier-frontend.test/dashboard?tab=transactions&payment=cancel");
        properties.setTargetPayment("Wave");
        properties.setZeroDebitGuaranteed(false);

        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        payTechClient = new PayTechClient(properties, objectMapper, restClientBuilder.build());
        gateway = new PayTechPaymentGateway(payTechClient, properties);

        transactionLogger = (Logger) LoggerFactory.getLogger("com.ecourtier.domain.transaction");
        logAppender = new ListAppender<>();
        logAppender.start();
        transactionLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        if (transactionLogger != null && logAppender != null) {
            transactionLogger.detachAppender(logAppender);
        }
    }

    private void assertNoSecretsInLogs() {
        for (ILoggingEvent event : logAppender.list) {
            String msg = event.getFormattedMessage();
            assertFalse(msg.contains(TEST_API_KEY), "FUITE DE SECRET : PAYTECH_API_KEY trouvé dans les logs !");
            assertFalse(msg.contains(TEST_API_SECRET), "FUITE DE SECRET : PAYTECH_API_SECRET trouvé dans les logs !");
        }
        assertFalse(properties.toString().contains(TEST_API_KEY), "toString() ne doit jamais exposer apiKey");
        assertFalse(properties.toString().contains(TEST_API_SECRET), "toString() ne doit jamais exposer apiSecret");
    }

    @Test
    @DisplayName("1. Initialisation PayTech Sandbox (env=test, target_payment=Wave, mock HTTP isolé) retourne PENDING + redirect_url sans marquer payé")
    void testInitialisationPayTechSandboxRetournePending() {
        // Autorisation explicite uniquement sur serveur MockRestServiceServer local isolé (0 appel réseau réel)
        properties.setZeroDebitGuaranteed(true);
        UUID txId = UUID.randomUUID();
        UUID annonceId = UUID.randomUUID();

        mockServer.expect(requestTo("https://paytech.sn/api/payment/request-payment"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("API_KEY", TEST_API_KEY))
                .andExpect(header("API_SECRET", TEST_API_SECRET))
                .andExpect(jsonPath("$.env").value("test"))
                .andExpect(jsonPath("$.target_payment").value("Wave"))
                .andExpect(jsonPath("$.currency").value("XOF"))
                .andExpect(jsonPath("$.item_price").value(505000))
                .andExpect(jsonPath("$.ref_command").value("ECOURTIER-TEST-001"))
                .andExpect(jsonPath("$.ipn_url").value("https://ecourtier-backend.test/api/v1/webhooks/paytech"))
                .andRespond(withSuccess(
                        "{\"success\":1,\"token\":\"tok_sbx_abc123\",\"redirect_url\":\"https://paytech.sn/payment/checkout/tok_sbx_abc123\"}",
                        MediaType.APPLICATION_JSON
                ));

        PaymentInitResponse response = gateway.initiatePayment(PaymentInitRequest.builder()
                .transactionId(txId)
                .annonceId(annonceId)
                .amount(new BigDecimal("505000"))
                .currency("XOF")
                .itemName("Caution + 1er loyer Almadies")
                .commandName("Dossier E-Courtier")
                .refCommand("ECOURTIER-TEST-001")
                .paymentMethod("Wave")
                .build());

        mockServer.verify();
        assertEquals(ExternalPaymentState.PENDING, response.getState(), "L'initialisation ne doit JAMAIS retourner CONFIRMED");
        assertEquals("PAYTECH", response.getProviderCode());
        assertEquals("Wave", response.getPaymentMethod());
        assertEquals("tok_sbx_abc123", response.getExternalToken());
        assertEquals("https://paytech.sn/payment/checkout/tok_sbx_abc123", response.getRedirectUrl());
        assertFalse(response.isSimulated());
        assertTrue(response.isZeroDebitGuaranteed());
        assertNoSecretsInLogs();
    }

    @Test
    @DisplayName("1b. AUDIT-PT-001 : Verrou backend zéro-débit — zeroDebitGuaranteed=false bloque tout appel POST /payment/request-payment (0 appel HTTP)")
    void testRefusBackendSiZeroDebitNonGaranti() {
        properties.setZeroDebitGuaranteed(false);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> gateway.initiatePayment(
                PaymentInitRequest.builder()
                        .transactionId(UUID.randomUUID())
                        .amount(new BigDecimal("505000"))
                        .refCommand("ECOURTIER-ZERODEBIT-BLOCKED")
                        .build()
        ));

        assertTrue(ex.getMessage().contains("ZÉRO DÉBIT"));
        mockServer.verify(); // 0 appel HTTP effectué
        assertNoSecretsInLogs();
    }

    @Test
    @DisplayName("2. Refus strict de toute configuration env=prod (aucun appel HTTP lancé)")
    void testRefusConfigurationProd() {
        properties.setEnv("prod");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> gateway.initiatePayment(
                PaymentInitRequest.builder()
                        .transactionId(UUID.randomUUID())
                        .amount(new BigDecimal("505000"))
                        .refCommand("ECOURTIER-PROD-BLOCKED")
                        .build()
        ));

        assertTrue(ex.getMessage().contains("env=test"));
        mockServer.verify(); // 0 appel HTTP effectué
        assertNoSecretsInLogs();
    }

    @Test
    @DisplayName("3. Refus de paiement si les paramètres Sandbox obligatoires sont absents (API_KEY / API_SECRET / URLs)")
    void testRefusSiParametresSandboxAbsents() {
        properties.setApiKey("");
        properties.setIpnUrl("");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> gateway.initiatePayment(
                PaymentInitRequest.builder()
                        .transactionId(UUID.randomUUID())
                        .amount(new BigDecimal("505000"))
                        .refCommand("ECOURTIER-MISSING-CFG")
                        .build()
        ));

        assertTrue(ex.getMessage().contains("PAYTECH_API_KEY"));
        assertTrue(ex.getMessage().contains("PAYTECH_IPN_URL"));
        mockServer.verify();
        assertNoSecretsInLogs();
    }

    @Test
    @DisplayName("4. Réponse PayTech invalide (success=0, JSON malformé, token manquant ou redirect_url non fiable) -> 502 BAD_GATEWAY")
    void testReponsePayTechInvalide() {
        properties.setZeroDebitGuaranteed(true);

        // Cas A : success = -1
        mockServer.expect(requestTo("https://paytech.sn/api/payment/request-payment"))
                .andRespond(withSuccess("{\"success\":-1,\"message\":\"Clé invalide\"}", MediaType.APPLICATION_JSON));

        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class, () -> gateway.initiatePayment(
                PaymentInitRequest.builder()
                        .transactionId(UUID.randomUUID())
                        .amount(new BigDecimal("250000"))
                        .refCommand("ECOURTIER-BAD-1")
                        .build()
        ));
        assertEquals(HttpStatus.BAD_GATEWAY, ex1.getStatusCode());

        // Cas B : success = 1 mais redirect_url manquant
        mockServer.reset();
        mockServer.expect(requestTo("https://paytech.sn/api/payment/request-payment"))
                .andRespond(withSuccess("{\"success\":1,\"token\":\"tok_only\"}", MediaType.APPLICATION_JSON));

        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class, () -> gateway.initiatePayment(
                PaymentInitRequest.builder()
                        .transactionId(UUID.randomUUID())
                        .amount(new BigDecimal("250000"))
                        .refCommand("ECOURTIER-BAD-2")
                        .build()
        ));
        assertEquals(HttpStatus.BAD_GATEWAY, ex2.getStatusCode());

        // Cas C : JSON malformé
        mockServer.reset();
        mockServer.expect(requestTo("https://paytech.sn/api/payment/request-payment"))
                .andRespond(withSuccess("NOT_JSON{{{", MediaType.APPLICATION_JSON));

        ResponseStatusException ex3 = assertThrows(ResponseStatusException.class, () -> gateway.initiatePayment(
                PaymentInitRequest.builder()
                        .transactionId(UUID.randomUUID())
                        .amount(new BigDecimal("250000"))
                        .refCommand("ECOURTIER-BAD-3")
                        .build()
        ));
        assertEquals(HttpStatus.BAD_GATEWAY, ex3.getStatusCode());

        // Cas D (AUDIT-PT-006) : URL de redirection hors domaine paytech.sn ou schéma javascript:
        mockServer.reset();
        mockServer.expect(requestTo("https://paytech.sn/api/payment/request-payment"))
                .andRespond(withSuccess("{\"success\":1,\"token\":\"tok_evil\",\"redirect_url\":\"https://evil.example/checkout\"}", MediaType.APPLICATION_JSON));

        ResponseStatusException ex4 = assertThrows(ResponseStatusException.class, () -> gateway.initiatePayment(
                PaymentInitRequest.builder()
                        .transactionId(UUID.randomUUID())
                        .amount(new BigDecimal("250000"))
                        .refCommand("ECOURTIER-BAD-4")
                        .build()
        ));
        assertEquals(HttpStatus.BAD_GATEWAY, ex4.getStatusCode());

        mockServer.reset();
        mockServer.expect(requestTo("https://paytech.sn/api/payment/request-payment"))
                .andRespond(withSuccess("{\"success\":1,\"token\":\"tok_xss\",\"redirect_url\":\"javascript:alert(1)\"}", MediaType.APPLICATION_JSON));

        ResponseStatusException ex5 = assertThrows(ResponseStatusException.class, () -> gateway.initiatePayment(
                PaymentInitRequest.builder()
                        .transactionId(UUID.randomUUID())
                        .amount(new BigDecimal("250000"))
                        .refCommand("ECOURTIER-BAD-5")
                        .build()
        ));
        assertEquals(HttpStatus.BAD_GATEWAY, ex5.getStatusCode());
        assertNoSecretsInLogs();
    }

    @Test
    @DisplayName("5. Timeout réseau lors de l'appel PayTech -> 504 GATEWAY_TIMEOUT sans fuite de secrets")
    void testTimeoutReseauPayTech() {
        properties.setZeroDebitGuaranteed(true);
        mockServer.expect(requestTo("https://paytech.sn/api/payment/request-payment"))
                .andRespond(req -> {
                    throw new ResourceAccessException("Read timed out (" + TEST_API_SECRET + ")", new SocketTimeoutException("Read timed out"));
                });

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> gateway.initiatePayment(
                PaymentInitRequest.builder()
                        .transactionId(UUID.randomUUID())
                        .amount(new BigDecimal("505000"))
                        .refCommand("ECOURTIER-TIMEOUT-01")
                        .build()
        ));

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, ex.getStatusCode());
        assertNoSecretsInLogs();
    }

    @Test
    @DisplayName("6. Consultation get-status : PENDING, CONFIRMED avec montant, rejet sans montant (UNKNOWN) et rejet si env != test")
    void testGetPaymentStatusPendingConfirmedEtRejetProd() {
        UUID txId = UUID.randomUUID();

        // Pending
        mockServer.expect(requestTo(containsString("/payment/get-status?token_payment=tok_pending_1")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("API_KEY", TEST_API_KEY))
                .andExpect(header("API_SECRET", TEST_API_SECRET))
                .andRespond(withSuccess("{\"success\":1,\"status\":\"pending\",\"env\":\"test\"}", MediaType.APPLICATION_JSON));

        PaymentStatusResult pending = gateway.checkPaymentStatus("tok_pending_1", txId, new BigDecimal("505000"));
        assertEquals(ExternalPaymentState.PENDING, pending.getState());

        // Confirmed avec montant exact + env=test
        mockServer.reset();
        mockServer.expect(requestTo(containsString("/payment/get-status?token_payment=tok_paid_1")))
                .andRespond(withSuccess("{\"success\":1,\"status\":\"sale_complete\",\"item_price\":505000,\"env\":\"test\"}", MediaType.APPLICATION_JSON));

        PaymentStatusResult confirmed = gateway.checkPaymentStatus("tok_paid_1", txId, new BigDecimal("505000"));
        assertEquals(ExternalPaymentState.CONFIRMED, confirmed.getState());

        // AUDIT-PT-007 : CONFIRMED sans montant -> ne jamais confirmer (retourne UNKNOWN)
        mockServer.reset();
        mockServer.expect(requestTo(containsString("/payment/get-status?token_payment=tok_noamt_1")))
                .andRespond(withSuccess("{\"success\":1,\"status\":\"sale_complete\",\"env\":\"test\"}", MediaType.APPLICATION_JSON));

        PaymentStatusResult noAmount = gateway.checkPaymentStatus("tok_noamt_1", txId, new BigDecimal("505000"));
        assertEquals(ExternalPaymentState.UNKNOWN, noAmount.getState());

        // Rejet si env=prod ou env=live ou env absent sur confirmation
        mockServer.reset();
        mockServer.expect(requestTo(containsString("/payment/get-status?token_payment=tok_prod_1")))
                .andRespond(withSuccess("{\"success\":1,\"status\":\"sale_complete\",\"item_price\":505000,\"env\":\"prod\"}", MediaType.APPLICATION_JSON));
        assertThrows(IllegalStateException.class, () -> gateway.checkPaymentStatus("tok_prod_1", txId, new BigDecimal("505000")));

        mockServer.reset();
        mockServer.expect(requestTo(containsString("/payment/get-status?token_payment=tok_live_1")))
                .andRespond(withSuccess("{\"success\":1,\"status\":\"sale_complete\",\"item_price\":505000,\"env\":\"live\"}", MediaType.APPLICATION_JSON));
        assertThrows(IllegalStateException.class, () -> gateway.checkPaymentStatus("tok_live_1", txId, new BigDecimal("505000")));

        mockServer.reset();
        mockServer.expect(requestTo(containsString("/payment/get-status?token_payment=tok_noenv_1")))
                .andRespond(withSuccess("{\"success\":1,\"status\":\"sale_complete\",\"item_price\":505000}", MediaType.APPLICATION_JSON));
        assertThrows(IllegalStateException.class, () -> gateway.checkPaymentStatus("tok_noenv_1", txId, new BigDecimal("505000")));
        assertNoSecretsInLogs();
    }

    @Test
    @DisplayName("12 & 13. Remboursement simulé et Transfert propriétaire simulé ne déclenchent aucun appel financier réel PayTech")
    void testRemboursementEtTransfertSimulesSansAppelReseau() {
        UUID txId = UUID.randomUUID();

        PaymentRefundResult refund = gateway.initiateRefund(txId, "tok_sbx_paid", new BigDecimal("505000"));
        assertTrue(refund.isSimulated());
        assertTrue(refund.getRefundReference().startsWith("SIM-REFUND-"));
        assertEquals("SUCCESS", refund.getStatus());
        assertTrue(gateway.verifyRefundStatus(refund.getRefundReference(), "tok_sbx_paid", new BigDecimal("505000")));

        OwnerTransferResult transfer = gateway.initiateSimulatedOwnerTransfer(txId, new BigDecimal("500000"), "+221771234567");
        assertTrue(transfer.isSimulated());
        assertEquals("TRANSFERT_SIMULE", transfer.getStatus());
        assertTrue(transfer.getTransferReference().startsWith("SIM-TRANSFER-"));

        mockServer.verify(); // 0 appel HTTP financier sortant
        assertNoSecretsInLogs();
    }
}
