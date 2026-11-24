package com.ecourtier.domain.transaction.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ecourtier.common.security.JwtService;
import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.transaction.client.WaveClientMock;
import com.ecourtier.domain.transaction.entity.PaiementWaveEntity;
import com.ecourtier.domain.transaction.entity.StatutBail;
import com.ecourtier.domain.transaction.entity.StatutPaiementWave;
import com.ecourtier.domain.transaction.entity.StatutSequestre;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import com.ecourtier.domain.transaction.entity.TypeOperationWave;
import com.ecourtier.domain.transaction.gateway.PayTechPaymentProperties;
import com.ecourtier.domain.transaction.gateway.PaymentGateway;
import com.ecourtier.domain.transaction.gateway.PaymentGateway.ExternalPaymentState;
import com.ecourtier.domain.transaction.gateway.PaymentGateway.PaymentInitResponse;
import com.ecourtier.domain.transaction.gateway.PaymentGateway.PaymentStatusResult;
import com.ecourtier.domain.transaction.repository.PaiementWaveRepository;
import com.ecourtier.domain.transaction.repository.TransactionRepository;
import com.ecourtier.domain.transaction.service.PayTechWebhookService;
import com.ecourtier.domain.transaction.service.TransactionService;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("RENDER-PAYTECH-01 — Intégration complète PayTech IPN, persistance redémarrage, ESCROW-SIGN-001/002 et ESCROW-FIN-001")
class PayTechEndToEndSandboxIntegrationTest {

    private static final String TEST_API_KEY = "pt_e2e_api_key_sentinel_112233";
    private static final String TEST_API_SECRET = "pt_e2e_api_secret_sentinel_445566";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PaiementWaveRepository paiementWaveRepository;

    @Autowired
    private PayTechPaymentProperties payTechProperties;

    @Autowired
    private PayTechWebhookService payTechWebhookService;

    @Autowired
    private TransactionService transactionService;

    @MockitoSpyBean
    private WaveClientMock waveClientMock;

    @Autowired
    private com.ecourtier.testsupport.TestDatabaseCleaner testDatabaseCleaner;

    private UtilisateurEntity proprietaire;
    private UtilisateurEntity locataire;
    private UtilisateurEntity admin;
    private String tokenProprietaire;
    private String tokenLocataire;
    private String tokenAdmin;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger transactionLogger;

    @BeforeEach
    void setUp() {
        Mockito.reset(waveClientMock);
        waveClientMock.resetState();
        testDatabaseCleaner.cleanAll();

        payTechProperties.setApiKey(TEST_API_KEY);
        payTechProperties.setApiSecret(TEST_API_SECRET);
        payTechProperties.setEnv("test");
        payTechProperties.setZeroDebitGuaranteed(false);

        proprietaire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Ndiaye")
                .prenom("Mamadou")
                .email("prop.paytech@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass1234!"))
                .telephone("+221774440001")
                .role(Role.PROPRIETAIRE)
                .actif(true)
                .build());

        locataire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Fall")
                .prenom("Aminata")
                .email("loc.paytech@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass1234!"))
                .telephone("+221774440002")
                .role(Role.LOCATAIRE)
                .actif(true)
                .build());

        admin = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Admin")
                .prenom("Super")
                .email("admin.paytech@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass1234!"))
                .telephone("+221774440003")
                .role(Role.ADMIN)
                .actif(true)
                .build());

        tokenProprietaire = jwtService.generateAccessToken(proprietaire);
        tokenLocataire = jwtService.generateAccessToken(locataire);
        tokenAdmin = jwtService.generateAccessToken(admin);

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
        for (ILoggingEvent event : logAppender.list) {
            String msg = event.getFormattedMessage();
            assertFalse(msg.contains(TEST_API_KEY), "FUITE DE SECRET : PAYTECH_API_KEY dans les logs !");
            assertFalse(msg.contains(TEST_API_SECRET), "FUITE DE SECRET : PAYTECH_API_SECRET dans les logs !");
        }
        payTechProperties.setApiKey("");
        payTechProperties.setApiSecret("");
        payTechProperties.setEnv("test");
        Mockito.reset(waveClientMock);
        waveClientMock.resetState();
        testDatabaseCleaner.cleanAll();
    }

    private AnnonceEntity createAnnonce(StatutPublication statut) {
        return annonceRepository.save(AnnonceEntity.builder()
                .titre("Appartement Mermoz PayTech")
                .description("Test d'intégration complet PayTech Sandbox")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(250000))
                .superficie(80.0)
                .nombrePieces(3)
                .quartier("Mermoz")
                .ville("Dakar")
                .latitude(14.70)
                .longitude(-17.47)
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(statut)
                .supprime(false)
                .utilisateur(proprietaire)
                .build());
    }

    private TransactionEntity createTransaction(AnnonceEntity annonce, StatutSequestre statut, LocalDateTime expiration) {
        boolean actif = statut == StatutSequestre.INITIALISE || statut == StatutSequestre.BLOQUE || statut == StatutSequestre.LIBERE;
        return transactionRepository.save(TransactionEntity.builder()
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(250000))
                .montantPremierLoyer(BigDecimal.valueOf(250000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(statut)
                .statutBail(StatutBail.NON_SIGNE)
                .dossierActif(actif)
                .dateExpiration(expiration)
                .build());
    }

    @Test
    @DisplayName("Scénarios 1, 6, 7 & 10 : Flux PayTech PENDING -> polling PENDING -> IPN sale_complete HMAC -> BLOQUE + idempotence")
    void testFluxPayTechPendingPuisIpnSaleCompleteEtRejeuIdempotent() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE);
        TransactionEntity tx = createTransaction(annonce, StatutSequestre.INITIALISE, LocalDateTime.now().plusHours(24));

        String refCommand = "ECOURTIER-PT-" + tx.getId().toString().substring(0, 8).toUpperCase();
        String tokenPayTech = "tok_paytech_sbx_778899";
        String redirectUrl = "https://paytech.sn/payment/checkout/" + tokenPayTech;

        // Simuler le retour PENDING de PayTechPaymentGateway lors de l'initiation
        doReturn(PaymentInitResponse.builder()
                .transactionId(tx.getId())
                .amount(BigDecimal.valueOf(505000))
                .providerCode("PAYTECH")
                .paymentMethod("Wave")
                .refCommand(refCommand)
                .externalReference(tokenPayTech)
                .externalToken(tokenPayTech)
                .redirectUrl(redirectUrl)
                .state(ExternalPaymentState.PENDING)
                .simulated(false)
                .zeroDebitGuaranteed(false)
                .build())
                .when(waveClientMock).initiatePayment(any(PaymentGateway.PaymentInitRequest.class));

        // 1. Initiation -> retourne 200 avec statutSequestre=INITIALISE (JAMAIS BLOQUE !),
        //    zeroDebitGaranti=false et redirectUrlPaiement masquée côté backend (AUDIT-PT-001)
        mockMvc.perform(post("/api/v1/transactions/{id}/paiement-caution", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("INITIALISE")))
                .andExpect(jsonPath("$.statutPaiementExterne", is("EN_ATTENTE_CONFIRMATION")))
                .andExpect(jsonPath("$.prestatairePaiement", is("PAYTECH")))
                .andExpect(jsonPath("$.modeSimulationPaiement", is(false)))
                .andExpect(jsonPath("$.zeroDebitGaranti", is(false)))
                .andExpect(jsonPath("$.refCommandPaytech", is(refCommand)))
                .andExpect(jsonPath("$.redirectUrlPaiement").doesNotExist());

        // 2. Consultation de statut alors que PayTech répond toujours PENDING -> reste INITIALISE
        doReturn(PaymentStatusResult.builder()
                .state(ExternalPaymentState.PENDING)
                .externalReference(tokenPayTech)
                .simulated(false)
                .build())
                .when(waveClientMock).checkPaymentStatus(eq(tokenPayTech), eq(tx.getId()), any());

        mockMvc.perform(get("/api/v1/transactions/{id}/paiement/statut", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("INITIALISE")))
                .andExpect(jsonPath("$.statutPaiementExterne", is("EN_ATTENTE_CONFIRMATION")));

        // 3. IPN sale_complete authentifié par HMAC-SHA256 + devise XOF (format form-urlencoded officiel PayTech)
        String hmac = payTechWebhookService.computePaymentHmacHex("505000", refCommand, TEST_API_KEY, TEST_API_SECRET);

        mockMvc.perform(post("/api/v1/webhooks/paytech")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("type_event", "sale_complete")
                        .param("ref_command", refCommand)
                        .param("item_price", "505000")
                        .param("final_item_price", "505000")
                        .param("currency", "XOF")
                        .param("token", tokenPayTech)
                        .param("env", "test")
                        .param("payment_method", "Wave")
                        .param("hmac_compute", hmac))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeEvent", is("sale_complete")))
                .andExpect(jsonPath("$.statutPaiement", is("CONFIRME")))
                .andExpect(jsonPath("$.statutSequestre", is("BLOQUE")))
                .andExpect(jsonPath("$.latePayment", is(false)))
                .andExpect(jsonPath("$.idempotentReplay", is(false)));

        assertEquals(StatutPublication.RESERVEE, annonceRepository.findById(annonce.getId()).orElseThrow().getStatutPublication());

        // 4. Rejeu identique de la notification sale_complete -> 200 OK idempotent, aucun doublon
        mockMvc.perform(post("/api/v1/webhooks/paytech")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("type_event", "sale_complete")
                        .param("ref_command", refCommand)
                        .param("item_price", "505000")
                        .param("final_item_price", "505000")
                        .param("currency", "XOF")
                        .param("token", tokenPayTech)
                        .param("env", "test")
                        .param("hmac_compute", hmac))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPaiement", is("CONFIRME")))
                .andExpect(jsonPath("$.idempotentReplay", is(true)));

        assertEquals(1, paiementWaveRepository.findByTransactionIdOrderByDateCreationDesc(tx.getId()).size());
    }

    @Test
    @DisplayName("Scénarios 8, 9 & 11 : IPN sale_canceled, notifications frauduleuses (HMAC/SHA256/prod) et référence inconnue (404)")
    void testIpnSaleCanceledFrauduleuxEtReferenceInconnue() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE);
        TransactionEntity tx = createTransaction(annonce, StatutSequestre.INITIALISE, LocalDateTime.now().plusHours(24));
        String refCommand = "ECOURTIER-CANCEL-" + tx.getId().toString().substring(0, 8).toUpperCase();
        tx.setRefCommandPaytech(refCommand);
        tx.setStatutPaiementExterne("EN_ATTENTE_CONFIRMATION");
        transactionRepository.saveAndFlush(tx);

        // A. Notification sans authentification -> 401
        mockMvc.perform(post("/api/v1/webhooks/paytech")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type_event\":\"sale_complete\",\"ref_command\":\"" + refCommand + "\",\"item_price\":\"505000\",\"env\":\"test\"}"))
                .andExpect(status().isUnauthorized());

        // B. Notification avec HMAC falsifié -> 403
        mockMvc.perform(post("/api/v1/webhooks/paytech")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type_event\":\"sale_complete\",\"ref_command\":\"" + refCommand + "\",\"item_price\":\"505000\",\"env\":\"test\",\"hmac_compute\":\"0000000000000000000000000000000000000000000000000000000000000000\"}"))
                .andExpect(status().isForbidden());

        // C. Notification avec env=prod -> 403 (même si HMAC valide !)
        String validHmac = payTechWebhookService.computePaymentHmacHex("505000", refCommand, TEST_API_KEY, TEST_API_SECRET);
        mockMvc.perform(post("/api/v1/webhooks/paytech")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type_event\":\"sale_complete\",\"ref_command\":\"" + refCommand + "\",\"item_price\":\"505000\",\"env\":\"prod\",\"hmac_compute\":\"" + validHmac + "\"}"))
                .andExpect(status().isForbidden());

        // D. Notification avec montant falsifié -> 403
        mockMvc.perform(post("/api/v1/webhooks/paytech")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type_event\":\"sale_complete\",\"ref_command\":\"" + refCommand + "\",\"item_price\":\"100\",\"currency\":\"XOF\",\"env\":\"test\",\"hmac_compute\":\"" + validHmac + "\"}"))
                .andExpect(status().isForbidden());

        // D2 (AUDIT-PT-002) : Fallback SHA-256 sans hmac_compute interdit sur sale_complete (même avec hashes valides et sans montant) -> 401
        String keySha = payTechWebhookService.sha256Hex(TEST_API_KEY);
        String secretSha = payTechWebhookService.sha256Hex(TEST_API_SECRET);
        mockMvc.perform(post("/api/v1/webhooks/paytech")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type_event\":\"sale_complete\",\"ref_command\":\"" + refCommand + "\",\"currency\":\"XOF\",\"env\":\"test\",\"api_key_sha256\":\"" + keySha + "\",\"api_secret_sha256\":\"" + secretSha + "\"}"))
                .andExpect(status().isUnauthorized());

        // D3 (AUDIT-PT-002) : Devise non-XOF ou absente refusée -> 400
        mockMvc.perform(post("/api/v1/webhooks/paytech")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type_event\":\"sale_complete\",\"ref_command\":\"" + refCommand + "\",\"item_price\":\"505000\",\"currency\":\"USD\",\"env\":\"test\",\"hmac_compute\":\"" + validHmac + "\"}"))
                .andExpect(status().isBadRequest());

        // D4 (AUDIT-PT-002) : Champ env absent refusé -> 403
        mockMvc.perform(post("/api/v1/webhooks/paytech")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type_event\":\"sale_complete\",\"ref_command\":\"" + refCommand + "\",\"item_price\":\"505000\",\"currency\":\"XOF\",\"hmac_compute\":\"" + validHmac + "\"}"))
                .andExpect(status().isForbidden());

        // D5 (AUDIT-PT-004) : ref_command inconnu ne peut pas détourner custom_field -> 404
        String hijackHmac = payTechWebhookService.computePaymentHmacHex("505000", "ECOURTIER-HIJACK-999", TEST_API_KEY, TEST_API_SECRET);
        mockMvc.perform(post("/api/v1/webhooks/paytech")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type_event\":\"sale_complete\",\"ref_command\":\"ECOURTIER-HIJACK-999\",\"item_price\":\"505000\",\"currency\":\"XOF\",\"env\":\"test\",\"custom_field\":\"{\\\"transactionId\\\":\\\"" + tx.getId() + "\\\"}\",\"hmac_compute\":\"" + hijackHmac + "\"}"))
                .andExpect(status().isNotFound());

        assertEquals(StatutSequestre.INITIALISE, transactionRepository.findById(tx.getId()).orElseThrow().getStatutSequestre());

        // E. Référence inconnue authentifiée -> 404 Not Found
        String unknownHmac = payTechWebhookService.computePaymentHmacHex("505000", "ECOURTIER-INCONNU-999", TEST_API_KEY, TEST_API_SECRET);
        mockMvc.perform(post("/api/v1/webhooks/paytech")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type_event\":\"sale_complete\",\"ref_command\":\"ECOURTIER-INCONNU-999\",\"item_price\":\"505000\",\"currency\":\"XOF\",\"env\":\"test\",\"hmac_compute\":\"" + unknownHmac + "\"}"))
                .andExpect(status().isNotFound());

        // F. Notification sale_canceled authentifiée (JSON + query string -> AUDIT-PT-009) -> ANNULE mais transaction reste INITIALISE
        mockMvc.perform(post("/api/v1/webhooks/paytech?source=paytech_sandbox")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type_event\":\"sale_canceled\",\"ref_command\":\"" + refCommand + "\",\"item_price\":\"505000\",\"token\":\"tok_cancel_1\",\"env\":\"test\",\"hmac_compute\":\"" + validHmac + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPaiement", is("ANNULE")))
                .andExpect(jsonPath("$.statutSequestre", is("INITIALISE")));

        TransactionEntity afterCancel = transactionRepository.findById(tx.getId()).orElseThrow();
        assertEquals(StatutSequestre.INITIALISE, afterCancel.getStatutSequestre());
        assertEquals("ANNULE", afterCancel.getStatutPaiementExterne());
    }

    @Test
    @DisplayName("Scénarios 12, 13 & 14 : Paiement tardif PayTech, IPN refund_complete, transfer_success et transfer_failed")
    void testPaiementTardifRefundEtTransferIpn() throws Exception {
        // 1. Paiement tardif sur transaction expirée -> TARDIF_A_REMBOURSER sans réouvrir la transaction
        AnnonceEntity annonceExp = createAnnonce(StatutPublication.DISPONIBLE);
        TransactionEntity expiredTx = createTransaction(annonceExp, StatutSequestre.EXPIRE, LocalDateTime.now().minusHours(2));
        String lateRefCmd = "ECOURTIER-LATE-001";
        expiredTx.setRefCommandPaytech(lateRefCmd);
        transactionRepository.saveAndFlush(expiredTx);

        String lateHmac = payTechWebhookService.computePaymentHmacHex("505000", lateRefCmd, TEST_API_KEY, TEST_API_SECRET);
        mockMvc.perform(post("/api/v1/webhooks/paytech")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type_event\":\"sale_complete\",\"ref_command\":\"" + lateRefCmd + "\",\"item_price\":\"505000\",\"currency\":\"XOF\",\"token\":\"tok_late_1\",\"env\":\"test\",\"hmac_compute\":\"" + lateHmac + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPaiement", is("TARDIF_A_REMBOURSER")))
                .andExpect(jsonPath("$.statutSequestre", is("EXPIRE")))
                .andExpect(jsonPath("$.latePayment", is(true)));

        // 2. IPN refund_complete (fallback SHA-256 officiel PayTech) sur transaction BLOQUE -> REMBOURSE
        AnnonceEntity annonceRef = createAnnonce(StatutPublication.RESERVEE);
        TransactionEntity bloqueTx = createTransaction(annonceRef, StatutSequestre.BLOQUE, LocalDateTime.now().plusHours(20));
        String refundRefCmd = "ECOURTIER-REFUND-001";
        bloqueTx.setRefCommandPaytech(refundRefCmd);
        bloqueTx.setReferenceWave("tok_paid_ref_001");
        transactionRepository.saveAndFlush(bloqueTx);

        String keySha = payTechWebhookService.sha256Hex(TEST_API_KEY);
        String secretSha = payTechWebhookService.sha256Hex(TEST_API_SECRET);

        mockMvc.perform(post("/api/v1/webhooks/paytech")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type_event\":\"refund_complete\",\"ref_command\":\"" + refundRefCmd + "\",\"item_price\":\"505000\",\"currency\":\"xof\",\"token\":\"tok_refund_001\",\"env\":\"test\",\"api_key_sha256\":\"" + keySha + "\",\"api_secret_sha256\":\"" + secretSha + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeEvent", is("refund_complete")))
                .andExpect(jsonPath("$.statutSequestre", is("REMBOURSE")));

        assertEquals(StatutPublication.DISPONIBLE, annonceRepository.findById(annonceRef.getId()).orElseThrow().getStatutPublication());

        // 3. IPN transfer_success authentifié par HMAC transfert + protection contre transfer_failed hors ordre (AUDIT-PT-005)
        AnnonceEntity annonceTr = createAnnonce(StatutPublication.LOUEE);
        TransactionEntity libTx = createTransaction(annonceTr, StatutSequestre.LIBERE, LocalDateTime.now().plusHours(20));
        paiementWaveRepository.saveAndFlush(PaiementWaveEntity.builder()
                .transaction(libTx)
                .typeOperation(TypeOperationWave.TRANSFERT_PROPRIETAIRE)
                .statut(StatutPaiementWave.EN_ATTENTE_CONFIRMATION)
                .prestataire("PAYTECH")
                .moyenPaiement("Wave")
                .environnement("test")
                .modeSimulation(Boolean.FALSE)
                .cleIdempotence("TR-TEST-" + libTx.getId())
                .tokenExterne("PYT-DEPOT-TEST001")
                .referenceExterne("PYT-DEPOT-TEST001")
                .montant(BigDecimal.valueOf(500000))
                .devise("XOF")
                .build());

        String trHmac = payTechWebhookService.computeTransferHmacHex("500000", "PYT-DEPOT-TEST001", TEST_API_KEY, TEST_API_SECRET);
        mockMvc.perform(post("/api/v1/webhooks/paytech")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type_event\":\"transfer_success\",\"id_transfer\":\"PYT-DEPOT-TEST001\",\"amount\":\"500000\",\"state\":\"success\",\"env\":\"test\",\"hmac_compute\":\"" + trHmac + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeEvent", is("transfer_success")))
                .andExpect(jsonPath("$.statutPaiement", is("CONFIRME")));

        assertEquals("CONFIRME", transactionRepository.findById(libTx.getId()).orElseThrow().getStatutTransfertProprietaire());

        // AUDIT-PT-005 : un transfer_failed hors ordre sur un transfert déjà CONFIRME NE DOIT PAS écraser CONFIRME !
        mockMvc.perform(post("/api/v1/webhooks/paytech")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type_event\":\"transfer_failed\",\"id_transfer\":\"PYT-DEPOT-TEST001\",\"amount\":\"500000\",\"state\":\"failed\",\"env\":\"test\",\"api_key_sha256\":\"" + keySha + "\",\"api_secret_sha256\":\"" + secretSha + "\"}"))
                .andExpect(status().isOk());
        assertEquals("CONFIRME", transactionRepository.findById(libTx.getId()).orElseThrow().getStatutTransfertProprietaire());

        // 4. Un transfert distinct en attente recevant transfer_failed (fallback SHA-256) passe proprement à ECHOUE
        AnnonceEntity annonceTrFail = createAnnonce(StatutPublication.LOUEE);
        TransactionEntity libTxFail = createTransaction(annonceTrFail, StatutSequestre.LIBERE, LocalDateTime.now().plusHours(20));
        paiementWaveRepository.saveAndFlush(PaiementWaveEntity.builder()
                .transaction(libTxFail)
                .typeOperation(TypeOperationWave.TRANSFERT_PROPRIETAIRE)
                .statut(StatutPaiementWave.EN_ATTENTE_CONFIRMATION)
                .prestataire("PAYTECH")
                .moyenPaiement("Wave")
                .environnement("test")
                .modeSimulation(Boolean.FALSE)
                .cleIdempotence("TR-TEST-FAIL-" + libTxFail.getId())
                .tokenExterne("PYT-DEPOT-FAIL002")
                .referenceExterne("PYT-DEPOT-FAIL002")
                .montant(BigDecimal.valueOf(500000))
                .devise("XOF")
                .build());

        mockMvc.perform(post("/api/v1/webhooks/paytech")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type_event\":\"transfer_failed\",\"id_transfer\":\"PYT-DEPOT-FAIL002\",\"amount\":\"500000\",\"state\":\"failed\",\"env\":\"test\",\"api_key_sha256\":\"" + keySha + "\",\"api_secret_sha256\":\"" + secretSha + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeEvent", is("transfer_failed")))
                .andExpect(jsonPath("$.statutPaiement", is("ECHOUE")));

        assertEquals("ECHOUE", transactionRepository.findById(libTxFail.getId()).orElseThrow().getStatutTransfertProprietaire());
    }

    @Test
    @DisplayName("Scénario 15 (ESCROW-OPS-001) : Redémarrage du backend -> opérations persistées en base permettent vérification et remboursement")
    void testRedemarrageBackendPersistanceOperations() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE);
        TransactionEntity tx = createTransaction(annonce, StatutSequestre.INITIALISE, LocalDateTime.now().plusHours(24));

        // 1. Paiement simulé localement -> persisté dans transactions + paiements_wave
        mockMvc.perform(post("/api/v1/transactions/{id}/paiement-caution", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("BLOQUE")))
                .andExpect(jsonPath("$.referenceWave", startsWith("SIM-LOCAL-")))
                .andExpect(jsonPath("$.modeSimulationPaiement", is(true)));

        String paidRef = transactionRepository.findById(tx.getId()).orElseThrow().getReferenceWave();
        assertNotNull(paidRef);

        // 2. Simuler un redémarrage complet du backend (vidage total du cache mémoire de MockPaymentGateway)
        waveClientMock.simulateBackendRestartClearMemoryOnly();

        // 3. Malgré le vidage mémoire, verifyPaymentStatus et le remboursement ADMIN réussissent via PostgreSQL/H2 !
        assertTrue(waveClientMock.verifyPaymentStatus(paidRef, tx.getId(), BigDecimal.valueOf(505000)),
                "L'opération persistée doit survivre au redémarrage mémoire");

        mockMvc.perform(post("/api/v1/transactions/{id}/rembourser", tx.getId())
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motif\":\"Remboursement post-redémarrage\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("REMBOURSE")))
                .andExpect(jsonPath("$.referenceRemboursementWave", startsWith("SIM-REFUND-")));
    }

    @Test
    @DisplayName("Scénarios 13, 16, 17 & 18 : ESCROW-SIGN-001 (ADMIN interdit), ESCROW-SIGN-002 (aperçu PDF + SHA-256), ESCROW-FIN-001 (TRANSFERT_SIMULE)")
    void testDoubleSignatureAuthentifieeSha256AdminInterditEtTransfertSimule() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE);
        TransactionEntity tx = createTransaction(annonce, StatutSequestre.INITIALISE, LocalDateTime.now().plusHours(24));

        // 1. Paiement simulé
        mockMvc.perform(post("/api/v1/transactions/{id}/paiement-caution", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("BLOQUE")))
                .andExpect(jsonPath("$.bailDocumentSha256").isNotEmpty());

        // 2. Aperçu PDF pré-signature accessible au locataire
        mockMvc.perform(get("/api/v1/transactions/{id}/bail/apercu-pdf", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        // 3. ADMIN tente de signer 2 fois -> 403 Forbidden à chaque tentative, statut reste BLOQUE & NON_SIGNE
        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", tx.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isForbidden());
        assertThrows(ResponseStatusException.class, () -> transactionService.signerBail(tx.getId(), admin.getEmail()));

        // 4. Locataire signe (1/2) -> reste BLOQUE, AUCUNE libération logique, AUCUN transfert simulé !
        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutBail", is("SIGNE_LOCATAIRE")))
                .andExpect(jsonPath("$.statutSequestre", is("BLOQUE")))
                .andExpect(jsonPath("$.signatureLocataireHash").isNotEmpty())
                .andExpect(jsonPath("$.statutTransfertProprietaire").doesNotExist());

        // 5. Locataire tente de re-signer -> 400 Bad Request
        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isBadRequest());

        // 6. Propriétaire signe (2/2) -> SIGNE_COMPLET + LIBERE + TRANSFERT_SIMULE + empreintes SHA-256
        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", tx.getId())
                        .header("Authorization", "Bearer " + tokenProprietaire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutBail", is("SIGNE_COMPLET")))
                .andExpect(jsonPath("$.statutSequestre", is("LIBERE")))
                .andExpect(jsonPath("$.statutTransfertProprietaire", is("TRANSFERT_SIMULE")))
                .andExpect(jsonPath("$.referenceTransfertProprietaire", startsWith("SIM-TRANSFER-")))
                .andExpect(jsonPath("$.signatureProprietaireHash").isNotEmpty())
                .andExpect(jsonPath("$.bailDocumentSha256").isNotEmpty());

        List<PaiementWaveEntity> ops = paiementWaveRepository.findByTransactionIdOrderByDateCreationDesc(tx.getId());
        assertTrue(ops.stream().anyMatch(op ->
                        op.getTypeOperation() == TypeOperationWave.TRANSFERT_PROPRIETAIRE
                                && op.getStatut() == StatutPaiementWave.SIMULE
                                && Boolean.TRUE.equals(op.getModeSimulation())),
                "Une opération TRANSFERT_PROPRIETAIRE avec statut SIMULE et modeSimulation=true doit être persistée");
    }

    @Test
    @DisplayName("AUDIT-PT-010 / ESCROW-SIGN-002 : Altération des termes contractuels (SHA-256) entre signature 1 et signature 2 -> 409 Conflict")
    void testAlterationTermesEntreSignaturesRefusee409() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE);
        TransactionEntity tx = createTransaction(annonce, StatutSequestre.INITIALISE, LocalDateTime.now().plusHours(24));

        // 1. Paiement simulé + 1re signature locataire -> empreinte SHA-256 figée
        mockMvc.perform(post("/api/v1/transactions/{id}/paiement-caution", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk());

        // 2. Modification frauduleuse d'un terme contractuel (titre de l'annonce affiché dans le bail) entre les 2 signatures
        AnnonceEntity tamperedAnnonce = annonceRepository.findById(annonce.getId()).orElseThrow();
        tamperedAnnonce.setTitre("Appartement Modifié Après 1re Signature");
        annonceRepository.saveAndFlush(tamperedAnnonce);

        // 3. La 2e signature (propriétaire) est immédiatement bloquée par la détection SHA-256 -> 409 Conflict
        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", tx.getId())
                        .header("Authorization", "Bearer " + tokenProprietaire))
                .andExpect(status().isConflict());

        TransactionEntity unchanged = transactionRepository.findById(tx.getId()).orElseThrow();
        assertEquals(StatutSequestre.BLOQUE, unchanged.getStatutSequestre());
        assertEquals(StatutBail.SIGNE_LOCATAIRE, unchanged.getStatutBail());
    }

    @Test
    @DisplayName("RE-AUDIT-01/02 : IPN refund_complete avec même ref_command/token que sale_complete + montant erroné 400 + transfer sur token paiement 404")
    void testRefundCompleteMemeRefCommandTokenEtTransferSurTokenPaiementRefuse() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE);
        TransactionEntity tx = createTransaction(annonce, StatutSequestre.INITIALISE, LocalDateTime.now().plusHours(24));

        String refCommand = "ECOURTIER-REAUDIT-" + tx.getId().toString().substring(0, 8).toUpperCase();
        String tokenPayTech = "tok_reaudit_same_001";
        tx.setRefCommandPaytech(refCommand);
        tx.setTokenPaiementExterne(tokenPayTech);
        tx.setStatutPaiementExterne("EN_ATTENTE_CONFIRMATION");
        transactionRepository.saveAndFlush(tx);

        // 1. sale_complete crée la ligne PAIEMENT_SEQUESTRE avec ref_command et reference_externe=tokenPayTech
        String hmacSale = payTechWebhookService.computePaymentHmacHex("505000", refCommand, TEST_API_KEY, TEST_API_SECRET);
        mockMvc.perform(post("/api/v1/webhooks/paytech")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type_event\":\"sale_complete\",\"ref_command\":\"" + refCommand + "\",\"item_price\":\"505000\",\"currency\":\"XOF\",\"token\":\"" + tokenPayTech + "\",\"env\":\"test\",\"hmac_compute\":\"" + hmacSale + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("BLOQUE")));

        String keySha = payTechWebhookService.sha256Hex(TEST_API_KEY);
        String secretSha = payTechWebhookService.sha256Hex(TEST_API_SECRET);

        // 2. RE-AUDIT-02 : transfer_failed ciblant le token d'une opération PAIEMENT_SEQUESTRE (non TRANSFERT_PROPRIETAIRE) -> 404
        mockMvc.perform(post("/api/v1/webhooks/paytech")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type_event\":\"transfer_failed\",\"id_transfer\":\"" + tokenPayTech + "\",\"amount\":\"505000\",\"state\":\"failed\",\"env\":\"test\",\"api_key_sha256\":\"" + keySha + "\",\"api_secret_sha256\":\"" + secretSha + "\"}"))
                .andExpect(status().isNotFound());

        // 3. RE-AUDIT-01 : refund_complete avec montant erroné -> 400 Bad Request
        mockMvc.perform(post("/api/v1/webhooks/paytech")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type_event\":\"refund_complete\",\"ref_command\":\"" + refCommand + "\",\"item_price\":\"100\",\"currency\":\"XOF\",\"token\":\"" + tokenPayTech + "\",\"env\":\"test\",\"api_key_sha256\":\"" + keySha + "\",\"api_secret_sha256\":\"" + secretSha + "\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(StatutSequestre.BLOQUE, transactionRepository.findById(tx.getId()).orElseThrow().getStatutSequestre());

        // 4. RE-AUDIT-01 : refund_complete avec MÊME ref_command et MÊME token que sale_complete -> 200 OK (aucune collision d'index) + rejeu idempotent
        String refundJson = "{\"type_event\":\"refund_complete\",\"ref_command\":\"" + refCommand + "\",\"item_price\":\"505000\",\"currency\":\"XOF\",\"token\":\"" + tokenPayTech + "\",\"env\":\"test\",\"api_key_sha256\":\"" + keySha + "\",\"api_secret_sha256\":\"" + secretSha + "\"}";
        mockMvc.perform(post("/api/v1/webhooks/paytech")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("REMBOURSE")));

        mockMvc.perform(post("/api/v1/webhooks/paytech")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("REMBOURSE")));

        List<PaiementWaveEntity> ops = paiementWaveRepository.findByTransactionIdOrderByDateCreationDesc(tx.getId());
        assertEquals(2, ops.size(), "Doit contenir 1 ligne PAIEMENT_SEQUESTRE et 1 ligne REMBOURSEMENT sans doublon");
        assertTrue(ops.stream().anyMatch(op -> op.getTypeOperation() == TypeOperationWave.REMBOURSEMENT
                && op.getReferenceExterne() != null && op.getReferenceExterne().startsWith("REFUND-")));
    }
}
