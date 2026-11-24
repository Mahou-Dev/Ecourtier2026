package com.ecourtier.domain.transaction.controller;

import com.ecourtier.common.security.JwtService;
import com.ecourtier.domain.annonce.dto.AnnonceStatutUpdateDTO;
import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.rendezvous.repository.RendezVousRepository;
import com.ecourtier.domain.transaction.entity.StatutBail;
import com.ecourtier.domain.transaction.entity.StatutSequestre;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import com.ecourtier.domain.transaction.repository.TransactionRepository;
import com.ecourtier.domain.transaction.service.TransactionService;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BailSignatureLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private RendezVousRepository rendezVousRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private com.ecourtier.domain.ia.repository.MessageIARepository messageIARepository;

    @Autowired
    private com.ecourtier.domain.ia.repository.ConversationIARepository conversationIARepository;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private com.ecourtier.testsupport.TestDatabaseCleaner testDatabaseCleaner;

    private UtilisateurEntity proprietaire;
    private UtilisateurEntity locataire;
    private UtilisateurEntity autreUtilisateur;
    private UtilisateurEntity admin;

    private String tokenProprietaire;
    private String tokenLocataire;
    private String tokenAutre;
    private String tokenAdmin;

    @BeforeEach
    void setUp() {
        testDatabaseCleaner.cleanAll();

        proprietaire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Fall")
                .prenom("Ibrahima")
                .email("prop.bail@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass1234!"))
                .telephone("+221772220001")
                .role(Role.PROPRIETAIRE)
                .actif(true)
                .build());

        locataire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Sarr")
                .prenom("Aminata")
                .email("loc.bail@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass1234!"))
                .telephone("+221772220002")
                .role(Role.LOCATAIRE)
                .actif(true)
                .build());

        autreUtilisateur = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Diallo")
                .prenom("Oumar")
                .email("autre.bail@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass1234!"))
                .telephone("+221772220003")
                .role(Role.LOCATAIRE)
                .actif(true)
                .build());

        admin = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Admin")
                .prenom("Signature")
                .email("admin.bail@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass1234!"))
                .telephone("+221772220004")
                .role(Role.ADMIN)
                .actif(true)
                .build());

        tokenProprietaire = jwtService.generateAccessToken(proprietaire);
        tokenLocataire = jwtService.generateAccessToken(locataire);
        tokenAutre = jwtService.generateAccessToken(autreUtilisateur);
        tokenAdmin = jwtService.generateAccessToken(admin);
    }

    @AfterEach
    void tearDown() {
        testDatabaseCleaner.cleanAll();
    }

    private AnnonceEntity createAnnonce(StatutPublication statut) {
        return annonceRepository.save(AnnonceEntity.builder()
                .titre("Appartement Bail RES-03")
                .description("Test signature cycle de vie")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(350000))
                .superficie(90.0)
                .nombrePieces(4)
                .quartier("Point E")
                .ville("Dakar")
                .latitude(14.70)
                .longitude(-17.46)
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(statut)
                .supprime(false)
                .utilisateur(proprietaire)
                .build());
    }

    private TransactionEntity createTransaction(AnnonceEntity annonce, StatutSequestre sequestre, StatutBail bail,
                                                LocalDateTime sigLoc, LocalDateTime sigProp, LocalDateTime expiration) {
        return transactionRepository.save(TransactionEntity.builder()
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(350000))
                .montantPremierLoyer(BigDecimal.valueOf(350000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(sequestre)
                .statutBail(bail)
                .dateSignatureLocataire(sigLoc)
                .dateSignatureProprietaire(sigProp)
                .dossierActif(sequestre == StatutSequestre.INITIALISE || sequestre == StatutSequestre.BLOQUE || sequestre == StatutSequestre.LIBERE)
                .dateExpiration(expiration)
                .build());
    }

    @Test
    @DisplayName("1. RES-03 / ESCROW-SIGN-001 : Refus 409 sur INITIALISE pour locataire/propriétaire et 403 pour ADMIN")
    void testRefusSignatureSurInitialisePourTousActeurs() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE);
        TransactionEntity tx = createTransaction(annonce, StatutSequestre.INITIALISE, StatutBail.NON_SIGNE,
                null, null, LocalDateTime.now().plusHours(24));

        for (String token : List.of(tokenLocataire, tokenProprietaire)) {
            mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", tx.getId())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isConflict());
        }

        // ESCROW-SIGN-001 : ADMIN est toujours interdit de signer (403 Forbidden)
        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", tx.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isForbidden());

        TransactionEntity unchanged = transactionRepository.findById(tx.getId()).orElseThrow();
        assertEquals(StatutBail.NON_SIGNE, unchanged.getStatutBail());
        assertNull(unchanged.getDateSignatureLocataire());
        assertNull(unchanged.getDateSignatureProprietaire());
    }

    @Test
    @DisplayName("2. RES-03 : Workflow nominal BLOQUE -> locataire -> propriétaire -> SIGNE_COMPLET + LIBERE + LOUEE")
    void testWorkflowNominalLocatairePuisProprietaire() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.RESERVEE);
        TransactionEntity tx = createTransaction(annonce, StatutSequestre.BLOQUE, StatutBail.NON_SIGNE,
                null, null, LocalDateTime.now().plusHours(24));

        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutBail", is("SIGNE_LOCATAIRE")))
                .andExpect(jsonPath("$.statutSequestre", is("BLOQUE")));

        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", tx.getId())
                        .header("Authorization", "Bearer " + tokenProprietaire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutBail", is("SIGNE_COMPLET")))
                .andExpect(jsonPath("$.statutSequestre", is("LIBERE")));

        TransactionEntity updatedTx = transactionRepository.findById(tx.getId()).orElseThrow();
        assertNotNull(updatedTx.getDateSignatureLocataire());
        assertNotNull(updatedTx.getDateSignatureProprietaire());
        assertNotNull(updatedTx.getPdfBailPath());
        assertEquals(StatutPublication.LOUEE, annonceRepository.findById(annonce.getId()).orElseThrow().getStatutPublication());
    }

    @Test
    @DisplayName("3. RES-03 : Workflow nominal BLOQUE -> propriétaire -> locataire -> SIGNE_COMPLET + LIBERE")
    void testWorkflowNominalProprietairePuisLocataire() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.RESERVEE);
        TransactionEntity tx = createTransaction(annonce, StatutSequestre.BLOQUE, StatutBail.NON_SIGNE,
                null, null, LocalDateTime.now().plusHours(24));

        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", tx.getId())
                        .header("Authorization", "Bearer " + tokenProprietaire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutBail", is("SIGNE_PROPRIETAIRE")))
                .andExpect(jsonPath("$.statutSequestre", is("BLOQUE")));

        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutBail", is("SIGNE_COMPLET")))
                .andExpect(jsonPath("$.statutSequestre", is("LIBERE")));
    }

    @Test
    @DisplayName("4. ESCROW-SIGN-001 : ADMIN est strictement interdit (403) de signer à la place du locataire après le propriétaire")
    void testAdminInterditDeSignerApresProprietaire() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.RESERVEE);
        TransactionEntity tx = createTransaction(annonce, StatutSequestre.BLOQUE, StatutBail.NON_SIGNE,
                null, null, LocalDateTime.now().plusHours(24));

        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", tx.getId())
                        .header("Authorization", "Bearer " + tokenProprietaire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutBail", is("SIGNE_PROPRIETAIRE")))
                .andExpect(jsonPath("$.statutSequestre", is("BLOQUE")));

        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", tx.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isForbidden());

        ResponseStatusException serviceEx = assertThrows(ResponseStatusException.class,
                () -> transactionService.signerBail(tx.getId(), admin.getEmail()));
        assertEquals(403, serviceEx.getStatusCode().value());

        TransactionEntity unchangedTx = transactionRepository.findById(tx.getId()).orElseThrow();
        assertEquals(StatutBail.SIGNE_PROPRIETAIRE, unchangedTx.getStatutBail());
        assertEquals(StatutSequestre.BLOQUE, unchangedTx.getStatutSequestre());
        assertNull(unchangedTx.getDateSignatureLocataire());
        assertNotNull(unchangedTx.getDateSignatureProprietaire());
    }

    @Test
    @DisplayName("5. ESCROW-SIGN-001 : ADMIN ne peut provoquer la double signature ni après locataire ni par deux appels successifs")
    void testAdminInterditDeSignerApresLocataireOuParDoubleAppel() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.RESERVEE);
        TransactionEntity tx = createTransaction(annonce, StatutSequestre.BLOQUE, StatutBail.NON_SIGNE,
                null, null, LocalDateTime.now().plusHours(24));

        // Deux appels successifs par ADMIN -> 403 à chaque appel, aucune signature, aucune libération
        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", tx.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", tx.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isForbidden());

        TransactionEntity afterAdminAttempts = transactionRepository.findById(tx.getId()).orElseThrow();
        assertEquals(StatutBail.NON_SIGNE, afterAdminAttempts.getStatutBail());
        assertEquals(StatutSequestre.BLOQUE, afterAdminAttempts.getStatutSequestre());
        assertNull(afterAdminAttempts.getDateSignatureLocataire());
        assertNull(afterAdminAttempts.getDateSignatureProprietaire());

        // Locataire signe -> ADMIN ne peut toujours pas compléter à la place du propriétaire
        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutBail", is("SIGNE_LOCATAIRE")));

        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", tx.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isForbidden());

        TransactionEntity stillWaitingOwner = transactionRepository.findById(tx.getId()).orElseThrow();
        assertEquals(StatutBail.SIGNE_LOCATAIRE, stillWaitingOwner.getStatutBail());
        assertEquals(StatutSequestre.BLOQUE, stillWaitingOwner.getStatutSequestre());
        assertNull(stillWaitingOwner.getDateSignatureProprietaire());
    }

    @Test
    @DisplayName("6. RES-03 : Double signature par la même partie refusée -> 400 Bad Request")
    void testDoubleSignatureMemePartieRefusee() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.RESERVEE);
        TransactionEntity tx = createTransaction(annonce, StatutSequestre.BLOQUE, StatutBail.NON_SIGNE,
                null, null, LocalDateTime.now().plusHours(24));

        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("7. RES-03 : Tiers non impliqué et non-ADMIN refusé -> 403 Forbidden (HTTP + service)")
    void testNonPartieNiAdminRefuse403() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.RESERVEE);
        TransactionEntity tx = createTransaction(annonce, StatutSequestre.BLOQUE, StatutBail.NON_SIGNE,
                null, null, LocalDateTime.now().plusHours(24));

        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", tx.getId())
                        .header("Authorization", "Bearer " + tokenAutre))
                .andExpect(status().isForbidden());

        ResponseStatusException serviceEx = assertThrows(ResponseStatusException.class,
                () -> transactionService.signerBail(tx.getId(), autreUtilisateur.getEmail()));
        assertEquals(403, serviceEx.getStatusCode().value());
    }

    @Test
    @DisplayName("8. RES-03 : Signature refusée (409) sur ANNULE, EXPIRE, REMBOURSE et LIBERE")
    void testSignatureRefuseeSurStatutsCloturesEtLibere() throws Exception {
        for (StatutSequestre statut : List.of(StatutSequestre.ANNULE, StatutSequestre.EXPIRE, StatutSequestre.REMBOURSE, StatutSequestre.LIBERE)) {
            AnnonceEntity annonce = createAnnonce(statut == StatutSequestre.LIBERE ? StatutPublication.LOUEE : StatutPublication.DISPONIBLE);
            TransactionEntity tx = createTransaction(annonce, statut,
                    statut == StatutSequestre.LIBERE ? StatutBail.SIGNE_COMPLET : StatutBail.NON_SIGNE,
                    null, null, LocalDateTime.now().plusHours(24));

            mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", tx.getId())
                            .header("Authorization", "Bearer " + tokenLocataire))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    @DisplayName("9. RES-03 : Annulation, expiration individuelle et scheduler refusés/ignorés si signature partielle présente")
    void testAnnulationEtExpirationRefuseesSiSignaturePartielle() {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE);
        TransactionEntity txPartial = createTransaction(
                annonce,
                StatutSequestre.INITIALISE,
                StatutBail.SIGNE_LOCATAIRE,
                LocalDateTime.now().minusMinutes(5),
                null,
                LocalDateTime.now().minusHours(1)
        );

        ResponseStatusException cancelEx = assertThrows(ResponseStatusException.class,
                () -> transactionService.annulerTransaction(txPartial.getId(), null, locataire.getEmail()));
        assertEquals(409, cancelEx.getStatusCode().value());

        ResponseStatusException expireEx = assertThrows(ResponseStatusException.class,
                () -> transactionService.expirerTransactionIndividuelle(txPartial.getId(), LocalDateTime.now()));
        assertEquals(409, expireEx.getStatusCode().value());

        int expiredCount = transactionService.expirerTransactionsExpirees();
        assertEquals(0, expiredCount);
        assertEquals(StatutSequestre.INITIALISE, transactionRepository.findById(txPartial.getId()).orElseThrow().getStatutSequestre());
    }

    @Test
    @DisplayName("10. RES-03 : Suspension admin refusée (409) si signature partielle existe sur transaction INITIALISE + signature bloquée si annonce SUSPENDUE")
    void testSuspensionEtAnnonceSuspendueSignature() throws Exception {
        AnnonceEntity annonceAvecSignature = createAnnonce(StatutPublication.DISPONIBLE);
        createTransaction(annonceAvecSignature, StatutSequestre.INITIALISE, StatutBail.SIGNE_LOCATAIRE,
                LocalDateTime.now().minusMinutes(2), null, LocalDateTime.now().plusHours(12));

        AnnonceStatutUpdateDTO suspendDto = AnnonceStatutUpdateDTO.builder()
                .statutPublication(StatutPublication.SUSPENDUE)
                .motif("Modération RES-03")
                .version(annonceAvecSignature.getVersion())
                .build();

        mockMvc.perform(put("/api/v1/admin/annonces/{id}/statut", annonceAvecSignature.getId())
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(suspendDto)))
                .andExpect(status().isConflict());

        AnnonceEntity annonceSuspendue = createAnnonce(StatutPublication.SUSPENDUE);
        TransactionEntity txBloqueSuspendu = createTransaction(annonceSuspendue, StatutSequestre.BLOQUE, StatutBail.NON_SIGNE,
                null, null, LocalDateTime.now().plusHours(24));

        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", txBloqueSuspendu.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("11. RES-03 : Remboursement refusé (409) si SIGNE_COMPLET, autorisé (200) si BLOQUE + signature partielle")
    void testRemboursementSelonEtatSignature() throws Exception {
        AnnonceEntity annonceComplet = createAnnonce(StatutPublication.DISPONIBLE);
        TransactionEntity txComplet = createTransaction(annonceComplet, StatutSequestre.INITIALISE, StatutBail.NON_SIGNE,
                null, null, LocalDateTime.now().plusHours(24));

        mockMvc.perform(post("/api/v1/transactions/{id}/paiement-caution", txComplet.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("BLOQUE")));

        TransactionEntity bloqueComplet = transactionRepository.findById(txComplet.getId()).orElseThrow();
        bloqueComplet.setStatutBail(StatutBail.SIGNE_COMPLET);
        bloqueComplet.setDateSignatureLocataire(LocalDateTime.now().minusMinutes(10));
        bloqueComplet.setDateSignatureProprietaire(LocalDateTime.now().minusMinutes(5));
        transactionRepository.save(bloqueComplet);

        mockMvc.perform(post("/api/v1/transactions/{id}/rembourser", txComplet.getId())
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motif\":\"Tentative remboursement bail complet\"}"))
                .andExpect(status().isConflict());

        AnnonceEntity annoncePartiel = createAnnonce(StatutPublication.DISPONIBLE);
        TransactionEntity txPartiel = createTransaction(annoncePartiel, StatutSequestre.INITIALISE, StatutBail.NON_SIGNE,
                null, null, LocalDateTime.now().plusHours(24));

        mockMvc.perform(post("/api/v1/transactions/{id}/paiement-caution", txPartiel.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("BLOQUE")));

        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", txPartiel.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutBail", is("SIGNE_LOCATAIRE")));

        mockMvc.perform(post("/api/v1/transactions/{id}/rembourser", txPartiel.getId())
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motif\":\"Annulation litigieuse avant double signature\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("REMBOURSE")));

        assertEquals(StatutPublication.DISPONIBLE,
                annonceRepository.findById(annoncePartiel.getId()).orElseThrow().getStatutPublication());
    }

    @Test
    @DisplayName("12. RES-03 Concurrence : double signature simultanée & course signature finale vs remboursement")
    void testConcurrenceDoubleSignatureEtCourseAvecRemboursement() throws Exception {
        AnnonceEntity annonceDouble = createAnnonce(StatutPublication.RESERVEE);
        TransactionEntity txDouble = createTransaction(annonceDouble, StatutSequestre.BLOQUE, StatutBail.NON_SIGNE,
                null, null, LocalDateTime.now().plusHours(24));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch readyDouble = new CountDownLatch(2);
        CountDownLatch startDouble = new CountDownLatch(1);
        List<Integer> doubleStatuses = Collections.synchronizedList(new ArrayList<>());

        pool.submit(() -> {
            readyDouble.countDown();
            try {
                startDouble.await();
                MvcResult res = mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", txDouble.getId())
                                .header("Authorization", "Bearer " + tokenLocataire))
                        .andReturn();
                doubleStatuses.add(res.getResponse().getStatus());
            } catch (Exception e) {
                doubleStatuses.add(500);
            }
        });
        pool.submit(() -> {
            readyDouble.countDown();
            try {
                startDouble.await();
                MvcResult res = mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", txDouble.getId())
                                .header("Authorization", "Bearer " + tokenProprietaire))
                        .andReturn();
                doubleStatuses.add(res.getResponse().getStatus());
            } catch (Exception e) {
                doubleStatuses.add(500);
            }
        });

        readyDouble.await(5, TimeUnit.SECONDS);
        startDouble.countDown();
        pool.shutdown();
        pool.awaitTermination(15, TimeUnit.SECONDS);

        assertEquals(List.of(200, 200), doubleStatuses);
        TransactionEntity finalDouble = transactionRepository.findById(txDouble.getId()).orElseThrow();
        assertEquals(StatutBail.SIGNE_COMPLET, finalDouble.getStatutBail());
        assertEquals(StatutSequestre.LIBERE, finalDouble.getStatutSequestre());
        assertNotNull(finalDouble.getPdfBailPath());

        // Course signature finale vs remboursement admin : un seul doit gagner
        AnnonceEntity annonceCourse = createAnnonce(StatutPublication.DISPONIBLE);
        TransactionEntity txCourse = createTransaction(annonceCourse, StatutSequestre.INITIALISE, StatutBail.NON_SIGNE,
                null, null, LocalDateTime.now().plusHours(24));

        mockMvc.perform(post("/api/v1/transactions/{id}/paiement-caution", txCourse.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("BLOQUE")));

        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", txCourse.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutBail", is("SIGNE_LOCATAIRE")));

        ExecutorService racePool = Executors.newFixedThreadPool(2);
        CountDownLatch readyRace = new CountDownLatch(2);
        CountDownLatch startRace = new CountDownLatch(1);
        List<Integer> raceStatuses = Collections.synchronizedList(new ArrayList<>());

        racePool.submit(() -> {
            readyRace.countDown();
            try {
                startRace.await();
                MvcResult res = mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", txCourse.getId())
                                .header("Authorization", "Bearer " + tokenProprietaire))
                        .andReturn();
                raceStatuses.add(res.getResponse().getStatus());
            } catch (Exception e) {
                raceStatuses.add(500);
            }
        });
        racePool.submit(() -> {
            readyRace.countDown();
            try {
                startRace.await();
                MvcResult res = mockMvc.perform(post("/api/v1/transactions/{id}/rembourser", txCourse.getId())
                                .header("Authorization", "Bearer " + tokenAdmin))
                        .andReturn();
                raceStatuses.add(res.getResponse().getStatus());
            } catch (Exception e) {
                raceStatuses.add(500);
            }
        });

        readyRace.await(5, TimeUnit.SECONDS);
        startRace.countDown();
        racePool.shutdown();
        racePool.awaitTermination(15, TimeUnit.SECONDS);

        long okCount = raceStatuses.stream().filter(s -> s == 200).count();
        long conflictCount = raceStatuses.stream().filter(s -> s == 409).count();
        assertEquals(1, okCount, "Un seul acteur doit remporter la course signature/remboursement: " + raceStatuses);
        assertEquals(1, conflictCount, "Le second acteur doit recevoir 409 Conflict: " + raceStatuses);
    }
}
