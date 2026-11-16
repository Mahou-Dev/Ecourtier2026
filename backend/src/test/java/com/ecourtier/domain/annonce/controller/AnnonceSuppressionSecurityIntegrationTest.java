package com.ecourtier.domain.annonce.controller;

import com.ecourtier.common.security.JwtService;
import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.favori.repository.FavoriRepository;
import com.ecourtier.domain.rendezvous.entity.RendezVousEntity;
import com.ecourtier.domain.rendezvous.entity.StatutRendezVous;
import com.ecourtier.domain.rendezvous.repository.RendezVousRepository;
import com.ecourtier.domain.transaction.entity.StatutBail;
import com.ecourtier.domain.transaction.entity.StatutSequestre;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnnonceSuppressionSecurityIntegrationTest {

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
    private RendezVousRepository rendezVousRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private FavoriRepository favoriRepository;

    @Autowired
    private com.ecourtier.testsupport.TestDatabaseCleaner testDatabaseCleaner;

    private UtilisateurEntity proprietaire;
    private UtilisateurEntity autreProprietaire;
    private UtilisateurEntity locataire;
    private UtilisateurEntity admin;

    private String tokenProprietaire;
    private String tokenAutreProprietaire;
    private String tokenLocataire;
    private String tokenAdmin;

    @BeforeEach
    void setUp() {
        testDatabaseCleaner.cleanAll();

        proprietaire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Ndiaye")
                .prenom("Mamadou")
                .email("prop.supp@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass1234!"))
                .telephone("+221771110001")
                .role(Role.PROPRIETAIRE)
                .actif(true)
                .build());

        autreProprietaire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Fall")
                .prenom("Khadija")
                .email("autre.supp@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass1234!"))
                .telephone("+221771110002")
                .role(Role.PROPRIETAIRE)
                .actif(true)
                .build());

        locataire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Diop")
                .prenom("Cheikh")
                .email("loc.supp@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass1234!"))
                .telephone("+221771110003")
                .role(Role.LOCATAIRE)
                .actif(true)
                .build());

        admin = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Admin")
                .prenom("Moderation")
                .email("admin.supp@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass1234!"))
                .telephone("+221771110004")
                .role(Role.ADMIN)
                .actif(true)
                .build());

        tokenProprietaire = jwtService.generateAccessToken(proprietaire);
        tokenAutreProprietaire = jwtService.generateAccessToken(autreProprietaire);
        tokenLocataire = jwtService.generateAccessToken(locataire);
        tokenAdmin = jwtService.generateAccessToken(admin);
    }

    @AfterEach
    void tearDown() {
        testDatabaseCleaner.cleanAll();
    }

    private AnnonceEntity createAnnonce(StatutPublication statutPublication, ModeTransaction mode) {
        return annonceRepository.save(AnnonceEntity.builder()
                .titre("Appartement Suppression " + statutPublication)
                .description("Description test RES-01 pour " + statutPublication)
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(mode)
                .prix(BigDecimal.valueOf(300000))
                .superficie(80.0)
                .nombrePieces(3)
                .quartier("Mermoz")
                .ville("Dakar")
                .latitude(14.70)
                .longitude(-17.47)
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(statutPublication)
                .supprime(false)
                .utilisateur(proprietaire)
                .build());
    }

    private TransactionEntity createTransaction(AnnonceEntity annonce, StatutSequestre statut, boolean dossierActif) {
        return transactionRepository.save(TransactionEntity.builder()
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(300000))
                .montantPremierLoyer(BigDecimal.valueOf(300000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(statut)
                .statutBail(StatutBail.NON_SIGNE)
                .dossierActif(dossierActif)
                .dateExpiration(LocalDateTime.now().plusHours(24))
                .build());
    }

    @Test
    @DisplayName("RES-01 : Suppression nominale sur annonce DISPONIBLE sans transaction active -> 204")
    void testSuppressionNominaleDisponible() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE, ModeTransaction.LOCATION);
        TransactionEntity cancelledTx = createTransaction(annonce, StatutSequestre.ANNULE, false);

        mockMvc.perform(delete("/api/v1/annonces/{id}", annonce.getId())
                        .header("Authorization", "Bearer " + tokenProprietaire))
                .andExpect(status().isNoContent());

        AnnonceEntity reloaded = annonceRepository.findById(annonce.getId()).orElseThrow();
        assertTrue(reloaded.isSupprime());
        assertEquals(StatutSequestre.ANNULE, transactionRepository.findById(cancelledTx.getId()).orElseThrow().getStatutSequestre());
    }

    @Test
    @DisplayName("RES-01 : Refus 409 sur annonce avec transaction INITIALISE active")
    void testRefusSuppressionTransactionInitialise() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE, ModeTransaction.LOCATION);
        TransactionEntity tx = createTransaction(annonce, StatutSequestre.INITIALISE, true);

        mockMvc.perform(delete("/api/v1/annonces/{id}", annonce.getId())
                        .header("Authorization", "Bearer " + tokenProprietaire))
                .andExpect(status().isConflict());

        assertFalse(annonceRepository.findById(annonce.getId()).orElseThrow().isSupprime());
        assertEquals(StatutSequestre.INITIALISE, transactionRepository.findById(tx.getId()).orElseThrow().getStatutSequestre());
    }

    @Test
    @DisplayName("RES-01 : Refus 409 sur annonce avec transaction BLOQUE")
    void testRefusSuppressionTransactionBloque() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE, ModeTransaction.LOCATION);
        TransactionEntity tx = createTransaction(annonce, StatutSequestre.BLOQUE, true);

        mockMvc.perform(delete("/api/v1/annonces/{id}", annonce.getId())
                        .header("Authorization", "Bearer " + tokenProprietaire))
                .andExpect(status().isConflict());

        assertFalse(annonceRepository.findById(annonce.getId()).orElseThrow().isSupprime());
        assertEquals(StatutSequestre.BLOQUE, transactionRepository.findById(tx.getId()).orElseThrow().getStatutSequestre());
    }

    @Test
    @DisplayName("RES-01 : Refus 409 sur annonce RESERVEE")
    void testRefusSuppressionReservee() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.RESERVEE, ModeTransaction.LOCATION);

        mockMvc.perform(delete("/api/v1/annonces/{id}", annonce.getId())
                        .header("Authorization", "Bearer " + tokenProprietaire))
                .andExpect(status().isConflict());

        assertFalse(annonceRepository.findById(annonce.getId()).orElseThrow().isSupprime());
    }

    @Test
    @DisplayName("RES-01 : Refus 409 sur annonce LOUEE")
    void testRefusSuppressionLouee() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.LOUEE, ModeTransaction.LOCATION);

        mockMvc.perform(delete("/api/v1/annonces/{id}", annonce.getId())
                        .header("Authorization", "Bearer " + tokenProprietaire))
                .andExpect(status().isConflict());

        assertFalse(annonceRepository.findById(annonce.getId()).orElseThrow().isSupprime());
    }

    @Test
    @DisplayName("RES-01 : Refus 409 sur annonce VENDUE")
    void testRefusSuppressionVendue() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.VENDUE, ModeTransaction.VENTE);

        mockMvc.perform(delete("/api/v1/annonces/{id}", annonce.getId())
                        .header("Authorization", "Bearer " + tokenProprietaire))
                .andExpect(status().isConflict());

        assertFalse(annonceRepository.findById(annonce.getId()).orElseThrow().isSupprime());
    }

    @Test
    @DisplayName("RES-01 : Refus 409 sur annonce SUSPENDUE (préservation de la modération)")
    void testRefusSuppressionSuspendue() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.SUSPENDUE, ModeTransaction.LOCATION);

        mockMvc.perform(delete("/api/v1/annonces/{id}", annonce.getId())
                        .header("Authorization", "Bearer " + tokenProprietaire))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/annonces/{id}", annonce.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isConflict());

        assertFalse(annonceRepository.findById(annonce.getId()).orElseThrow().isSupprime());
    }

    @Test
    @DisplayName("RES-01 : Refus 403 pour non-propriétaire (autre propriétaire ou locataire)")
    void testRefusSuppressionNonProprietaire403() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE, ModeTransaction.LOCATION);

        mockMvc.perform(delete("/api/v1/annonces/{id}", annonce.getId())
                        .header("Authorization", "Bearer " + tokenAutreProprietaire))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/annonces/{id}", annonce.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isForbidden());

        assertFalse(annonceRepository.findById(annonce.getId()).orElseThrow().isSupprime());
    }

    @Test
    @DisplayName("RES-01 : ADMIN autorisé sur DISPONIBLE sans transaction active (204) mais bloqué sur transaction active (409)")
    void testComportementAdminSuppression() throws Exception {
        AnnonceEntity libre = createAnnonce(StatutPublication.DISPONIBLE, ModeTransaction.LOCATION);
        mockMvc.perform(delete("/api/v1/annonces/{id}", libre.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());
        assertTrue(annonceRepository.findById(libre.getId()).orElseThrow().isSupprime());

        AnnonceEntity bloquee = createAnnonce(StatutPublication.DISPONIBLE, ModeTransaction.LOCATION);
        createTransaction(bloquee, StatutSequestre.BLOQUE, true);
        mockMvc.perform(delete("/api/v1/annonces/{id}", bloquee.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isConflict());
        assertFalse(annonceRepository.findById(bloquee.getId()).orElseThrow().isSupprime());
    }

    @Test
    @DisplayName("RES-01 : Concurrence entre supprimerAnnonceLogique et initierTransaction préserve l'invariant d'intégrité")
    void testConcurrenceSuppressionEtInitiationTransaction() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            transactionRepository.deleteAll();
            rendezVousRepository.deleteAll();
            annonceRepository.deleteAll();

            AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE, ModeTransaction.LOCATION);
            rendezVousRepository.save(RendezVousEntity.builder()
                    .annonce(annonce)
                    .demandeur(locataire)
                    .dateHeure(LocalDateTime.now().minusHours(2))
                    .statut(StatutRendezVous.EFFECTUE)
                    .build());

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            List<Integer> deleteStatuses = Collections.synchronizedList(new ArrayList<>());
            List<Integer> initStatuses = Collections.synchronizedList(new ArrayList<>());

            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    MvcResult res = mockMvc.perform(delete("/api/v1/annonces/{id}", annonce.getId())
                                    .header("Authorization", "Bearer " + tokenProprietaire))
                            .andReturn();
                    deleteStatuses.add(res.getResponse().getStatus());
                } catch (Exception e) {
                    deleteStatuses.add(500);
                }
            });

            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    MvcResult res = mockMvc.perform(post("/api/v1/annonces/{id}/transactions", annonce.getId())
                                    .header("Authorization", "Bearer " + tokenLocataire)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}"))
                            .andReturn();
                    initStatuses.add(res.getResponse().getStatus());
                } catch (Exception e) {
                    initStatuses.add(500);
                }
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

            int delStatus = deleteStatuses.get(0);
            int initStatus = initStatuses.get(0);
            AnnonceEntity finalAnnonce = annonceRepository.findById(annonce.getId()).orElseThrow();
            List<TransactionEntity> txs = transactionRepository.findByAnnonceIdOrderByDateCreationDesc(annonce.getId());

            if (delStatus == 204) {
                assertTrue(finalAnnonce.isSupprime());
                assertTrue(txs.isEmpty(), "Aucune transaction ne doit exister si la suppression a gagné");
                assertTrue(initStatus == 404 || initStatus == 409, "Initiation doit être rejetée si suppression gagnante: " + initStatus);
            } else {
                assertEquals(201, initStatus, "Si la suppression n'a pas gagné, l'initiation doit réussir");
                assertEquals(409, delStatus, "La suppression perdante doit recevoir 409 Conflict");
                assertFalse(finalAnnonce.isSupprime(), "L'annonce ne doit jamais être supprimée avec une transaction active");
                assertEquals(1, txs.size());
                assertEquals(StatutSequestre.INITIALISE, txs.get(0).getStatutSequestre());
            }
        }
    }

    @Test
    @DisplayName("B2 : Déjà supprimée -> 404 Not Found ; AGENT non propriétaire -> 403 ; AGENT propriétaire -> 204")
    void testAgentPermissionsAndAlreadyDeletedReturns404() throws Exception {
        UtilisateurEntity agentAutre = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Agent").prenom("Tiers").email("agent.tiers@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass1234!")).telephone("+221771110098")
                .role(Role.AGENT).actif(true).build());
        UtilisateurEntity agentProprio = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Agent").prenom("Auteur").email("agent.auteur@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass1234!")).telephone("+221771110099")
                .role(Role.AGENT).actif(true).build());
        String tokenAgentTiers = jwtService.generateAccessToken(agentAutre);
        String tokenAgentAuteur = jwtService.generateAccessToken(agentProprio);

        AnnonceEntity annonceAgent = annonceRepository.save(AnnonceEntity.builder()
                .titre("Studio Agent").description("Bien géré par agent").type(TypeBien.STUDIO)
                .modeTransaction(ModeTransaction.LOCATION).prix(BigDecimal.valueOf(180000))
                .superficie(40.0).nombrePieces(1).quartier("Plateau").ville("Dakar")
                .latitude(14.67).longitude(-17.43)
                .statutVerification(StatutVerification.VERIFIEE).statutPublication(StatutPublication.DISPONIBLE)
                .supprime(false).utilisateur(agentProprio).build());

        // 1. Agent tiers refusé (403)
        mockMvc.perform(delete("/api/v1/annonces/{id}", annonceAgent.getId())
                        .header("Authorization", "Bearer " + tokenAgentTiers))
                .andExpect(status().isForbidden());

        // 2. Agent propriétaire autorisé (204)
        mockMvc.perform(delete("/api/v1/annonces/{id}", annonceAgent.getId())
                        .header("Authorization", "Bearer " + tokenAgentAuteur))
                .andExpect(status().isNoContent());

        // 3. Re-suppression d'une annonce déjà supprimée -> 404 Not Found
        mockMvc.perform(delete("/api/v1/annonces/{id}", annonceAgent.getId())
                        .header("Authorization", "Bearer " + tokenAgentAuteur))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("B2 : Double DELETE simultané -> un 204 No Content et un 404 Not Found")
    void testDoubleDeleteSimultaneUnSuccesUn404() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE, ModeTransaction.LOCATION);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    MvcResult res = mockMvc.perform(delete("/api/v1/annonces/{id}", annonce.getId())
                                    .header("Authorization", "Bearer " + tokenProprietaire))
                            .andReturn();
                    statuses.add(res.getResponse().getStatus());
                } catch (Exception e) {
                    statuses.add(500);
                }
            });
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

        assertEquals(2, statuses.size());
        assertEquals(1, statuses.stream().filter(s -> s == 204).count(), "Un seul DELETE doit réussir (204): " + statuses);
        assertEquals(1, statuses.stream().filter(s -> s == 404).count(), "Le second DELETE doit recevoir 404: " + statuses);
        assertTrue(annonceRepository.findById(annonce.getId()).orElseThrow().isSupprime());
    }

    @Test
    @DisplayName("CA3-008 : Suppression logique masque les favoris, annule RDV actifs, bloque messagerie et confirmation RDV")
    void testSuppressionLogiqueMasqueFavorisAnnuleRdvEtBloqueMessagerie() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE, ModeTransaction.LOCATION);

        mockMvc.perform(post("/api/v1/annonces/{id}/favoris", annonce.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/utilisateurs/moi/favoris/count")
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        RendezVousEntity rdvDemande = rendezVousRepository.save(RendezVousEntity.builder()
                .annonce(annonce)
                .demandeur(locataire)
                .dateHeure(LocalDateTime.now().plusDays(2))
                .statut(StatutRendezVous.DEMANDE)
                .build());
        RendezVousEntity rdvConfirme = rendezVousRepository.save(RendezVousEntity.builder()
                .annonce(annonce)
                .demandeur(locataire)
                .dateHeure(LocalDateTime.now().plusDays(3))
                .statut(StatutRendezVous.CONFIRME)
                .build());

        mockMvc.perform(delete("/api/v1/annonces/{id}", annonce.getId())
                        .header("Authorization", "Bearer " + tokenProprietaire))
                .andExpect(status().isNoContent());

        // Favoris : compteur et liste n'exposent plus l'annonce supprimée
        mockMvc.perform(get("/api/v1/utilisateurs/moi/favoris/count")
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
        mockMvc.perform(get("/api/v1/utilisateurs/moi/favoris")
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        // Messagerie : impossible de démarrer une conversation sur une annonce supprimée
        mockMvc.perform(post("/api/v1/annonces/{id}/conversations", annonce.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isNotFound());

        // RDV : les RDV DEMANDE et CONFIRME sont automatiquement annulés
        assertEquals(StatutRendezVous.ANNULE, rendezVousRepository.findById(rdvDemande.getId()).orElseThrow().getStatut());
        assertEquals(StatutRendezVous.ANNULE, rendezVousRepository.findById(rdvConfirme.getId()).orElseThrow().getStatut());

        // Confirmation manuelle d'un RDV sur annonce supprimée refusée (409)
        RendezVousEntity rdvOrphelin = rendezVousRepository.save(RendezVousEntity.builder()
                .annonce(annonce)
                .demandeur(locataire)
                .dateHeure(LocalDateTime.now().plusDays(4))
                .statut(StatutRendezVous.DEMANDE)
                .build());
        mockMvc.perform(put("/api/v1/rendez-vous/{id}/confirmer", rdvOrphelin.getId())
                        .header("Authorization", "Bearer " + tokenProprietaire))
                .andExpect(status().isConflict());
    }
}
