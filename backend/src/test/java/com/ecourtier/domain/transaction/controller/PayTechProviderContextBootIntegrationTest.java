package com.ecourtier.domain.transaction.controller;

import com.ecourtier.common.security.JwtService;
import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.transaction.entity.StatutBail;
import com.ecourtier.domain.transaction.entity.StatutSequestre;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import com.ecourtier.domain.transaction.gateway.PayTechPaymentGateway;
import com.ecourtier.domain.transaction.gateway.PaymentGateway;
import com.ecourtier.domain.transaction.repository.TransactionRepository;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "ecourtier.payment.provider=paytech",
        "ecourtier.payment.paytech.env=test",
        "ecourtier.payment.paytech.api-key=pt_boot_test_key_sentinel",
        "ecourtier.payment.paytech.api-secret=pt_boot_test_secret_sentinel",
        "ecourtier.payment.paytech.base-url=https://paytech.sn/api",
        "ecourtier.payment.paytech.ipn-url=https://test.ecourtier.local/api/v1/webhooks/paytech",
        "ecourtier.payment.paytech.success-url=https://test.ecourtier.local/dashboard?payment=success",
        "ecourtier.payment.paytech.cancel-url=https://test.ecourtier.local/dashboard?payment=cancel",
        "ecourtier.payment.paytech.zero-debit-guaranteed=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("AUDIT-PT-001 / AUDIT-PT-009 / AUDIT-PT-010 : Démarrage Spring avec provider=paytech et verrou backend zéro-débit")
class PayTechProviderContextBootIntegrationTest {

    @Autowired
    private PaymentGateway paymentGateway;

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
    private com.ecourtier.testsupport.TestDatabaseCleaner testDatabaseCleaner;

    private UtilisateurEntity proprietaire;
    private UtilisateurEntity locataire;
    private String tokenLocataire;

    @BeforeEach
    void setUp() {
        testDatabaseCleaner.cleanAll();
        proprietaire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Ba").prenom("Moussa").email("prop.ptboot@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass1234!"))
                .telephone("+221775550001").role(Role.PROPRIETAIRE).actif(true).build());
        locataire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Sy").prenom("Fatou").email("loc.ptboot@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass1234!"))
                .telephone("+221775550002").role(Role.LOCATAIRE).actif(true).build());
        tokenLocataire = jwtService.generateAccessToken(locataire);
    }

    @AfterEach
    void tearDown() {
        testDatabaseCleaner.cleanAll();
    }

    @Test
    @DisplayName("1. Le contexte Spring démarre avec PayTechPaymentGateway actif, bloque tout paiement réseau (503) si zeroDebitGuaranteed=false, et désactive /webhooks/wave (403)")
    void testSpringBootWithPayTechProviderBlocksOutboundPaymentAndLegacyWaveWebhook() throws Exception {
        assertInstanceOf(PayTechPaymentGateway.class, paymentGateway,
                "PayTechPaymentGateway doit être le bean actif lorsque ecourtier.payment.provider=paytech");
        assertEquals("PAYTECH", paymentGateway.getProviderCode());
        assertFalse(paymentGateway.isZeroDebitGuaranteed());

        AnnonceEntity annonce = annonceRepository.save(AnnonceEntity.builder()
                .titre("Studio Plateau PayTech Boot").description("Test démarrage PayTech")
                .type(TypeBien.STUDIO).modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(200000)).superficie(45.0).nombrePieces(2)
                .quartier("Plateau").ville("Dakar").latitude(14.69).longitude(-17.44)
                .statutVerification(StatutVerification.VERIFIEE).statutPublication(StatutPublication.DISPONIBLE)
                .supprime(false).utilisateur(proprietaire).build());

        TransactionEntity tx = transactionRepository.save(TransactionEntity.builder()
                .annonce(annonce).locataire(locataire).proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(200000)).montantPremierLoyer(BigDecimal.valueOf(200000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.INITIALISE).statutBail(StatutBail.NON_SIGNE)
                .dossierActif(true).dateExpiration(LocalDateTime.now().plusHours(24)).build());

        // AUDIT-PT-001 : Même avec toutes les clés Sandbox présentes, zeroDebitGuaranteed=false bloque côté backend (503, 0 appel réseau)
        mockMvc.perform(post("/api/v1/transactions/{id}/paiement-caution", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isServiceUnavailable());

        TransactionEntity afterAttempt = transactionRepository.findById(tx.getId()).orElseThrow();
        assertEquals(StatutSequestre.INITIALISE, afterAttempt.getStatutSequestre());
        assertNull(afterAttempt.getRedirectUrlPaiement());
        assertNull(afterAttempt.getReferenceWave());

        // AUDIT-PT-009 : Le webhook hérité /api/v1/webhooks/wave est strictement désactivé (403) lorsque provider=paytech
        mockMvc.perform(post("/api/v1/webhooks/wave")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Wave-Signature", "sha256=deadbeef")
                        .content("{\"transactionId\":\"" + tx.getId() + "\",\"waveReference\":\"WAVE-LEGACY-1\",\"amount\":405000}"))
                .andExpect(status().isForbidden());
    }
}
