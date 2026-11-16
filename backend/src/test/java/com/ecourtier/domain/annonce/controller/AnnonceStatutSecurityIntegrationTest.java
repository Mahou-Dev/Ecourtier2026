package com.ecourtier.domain.annonce.controller;

import com.ecourtier.common.security.JwtService;
import com.ecourtier.domain.annonce.dto.AnnonceStatutUpdateDTO;
import com.ecourtier.domain.annonce.dto.AnnonceUpdateDTO;
import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.avis.dto.TraiterSignalementDTO;
import com.ecourtier.domain.avis.entity.MotifSignalement;
import com.ecourtier.domain.avis.entity.SignalementEntity;
import com.ecourtier.domain.avis.entity.StatutSignalement;
import com.ecourtier.domain.avis.repository.SignalementRepository;
import com.ecourtier.domain.avis.service.SignalementService;
import com.ecourtier.domain.rendezvous.repository.RendezVousRepository;
import com.ecourtier.domain.transaction.client.WaveClientMock;
import com.ecourtier.domain.transaction.entity.StatutBail;
import com.ecourtier.domain.transaction.entity.StatutSequestre;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import com.ecourtier.domain.transaction.repository.TransactionRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnnonceStatutSecurityIntegrationTest {

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
    private TransactionRepository transactionRepository;

    @Autowired
    private RendezVousRepository rendezVousRepository;

    @Autowired
    private SignalementRepository signalementRepository;

    @Autowired
    private SignalementService signalementService;

    @Autowired
    private WaveClientMock waveClientMock;

    @Autowired
    private com.ecourtier.testsupport.TestDatabaseCleaner testDatabaseCleaner;

    private UtilisateurEntity proprietaire;
    private UtilisateurEntity locataire;
    private UtilisateurEntity admin;

    private String tokenProprietaire;
    private String tokenLocataire;
    private String tokenAdmin;

    @BeforeEach
    void setUp() {
        cleanTables();
        waveClientMock.resetState();

        proprietaire = createUser("p02.prop@ecourtier.sn", Role.PROPRIETAIRE);
        locataire = createUser("p02.loc@ecourtier.sn", Role.LOCATAIRE);
        admin = createUser("p02.admin@ecourtier.sn", Role.ADMIN);

        tokenProprietaire = jwtService.generateAccessToken(proprietaire);
        tokenLocataire = jwtService.generateAccessToken(locataire);
        tokenAdmin = jwtService.generateAccessToken(admin);
    }

    @AfterEach
    void tearDown() {
        cleanTables();
    }

    private void cleanTables() {
        testDatabaseCleaner.cleanAll();
    }

    private UtilisateurEntity createUser(String email, Role role) {
        return utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Test")
                .prenom(role.name())
                .email(email)
                .motDePasse(passwordEncoder.encode("Pass123!"))
                .telephone("+221770001122")
                .role(role)
                .actif(true)
                .build());
    }

    private AnnonceEntity createAnnonce(StatutPublication statutPublication) {
        return annonceRepository.save(AnnonceEntity.builder()
                .titre("Appartement F3 Plateau P02")
                .description("Description initiale")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(300000))
                .superficie(100.0)
                .nombrePieces(3)
                .quartier("Plateau")
                .ville("Dakar")
                .latitude(14.67)
                .longitude(-17.43)
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(statutPublication)
                .supprime(false)
                .utilisateur(proprietaire)
                .build());
    }

    @Test
    @DisplayName("P0-2 : Propriétaire refusé (403) s'il tente de changer statutPublication via PUT /annonces/{id}, même pour lever SUSPENDUE")
    void testProprietaireRefuseSurChangementStatutPublication() throws Exception {
        for (StatutPublication initial : List.of(
                StatutPublication.SUSPENDUE,
                StatutPublication.LOUEE,
                StatutPublication.VENDUE,
                StatutPublication.RESERVEE
        )) {
            AnnonceEntity annonce = createAnnonce(initial);

            AnnonceUpdateDTO attempt = AnnonceUpdateDTO.builder()
                    .titre("Tentative réactivation")
                    .statutPublication(StatutPublication.DISPONIBLE)
                    .version(annonce.getVersion())
                    .build();

            mockMvc.perform(put("/api/v1/annonces/" + annonce.getId())
                            .header("Authorization", "Bearer " + tokenProprietaire)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(attempt)))
                    .andExpect(status().isForbidden());

            AnnonceEntity persisted = annonceRepository.findById(annonce.getId()).orElseThrow();
            assertEquals(initial, persisted.getStatutPublication(),
                    "Le statutPublication " + initial + " doit rester intact après rejet 403");
        }

        // Même DISPONIBLE -> SUSPENDUE est interdit au propriétaire
        AnnonceEntity dispo = createAnnonce(StatutPublication.DISPONIBLE);
        AnnonceUpdateDTO suspendAttempt = AnnonceUpdateDTO.builder()
                .statutPublication(StatutPublication.SUSPENDUE)
                .version(dispo.getVersion())
                .build();
        mockMvc.perform(put("/api/v1/annonces/" + dispo.getId())
                        .header("Authorization", "Bearer " + tokenProprietaire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(suspendAttempt)))
                .andExpect(status().isForbidden());
        assertEquals(StatutPublication.DISPONIBLE, annonceRepository.findById(dispo.getId()).orElseThrow().getStatutPublication());
    }

    @Test
    @DisplayName("P0-2 : Édition éditoriale d'une annonce SUSPENDUE sans changement de statut préserve SUSPENDUE")
    void testProprietaireEditionSurAnnonceSuspenduePreserveStatut() throws Exception {
        AnnonceEntity suspendue = createAnnonce(StatutPublication.SUSPENDUE);

        AnnonceUpdateDTO editorialOnly = AnnonceUpdateDTO.builder()
                .titre("Titre mis à jour uniquement")
                .description("Nouvelle description détaillée")
                .version(suspendue.getVersion())
                .build();

        mockMvc.perform(put("/api/v1/annonces/" + suspendue.getId())
                        .header("Authorization", "Bearer " + tokenProprietaire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editorialOnly)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titre", is("Titre mis à jour uniquement")))
                .andExpect(jsonPath("$.statutPublication", is("SUSPENDUE")));

        AnnonceEntity reloaded = annonceRepository.findById(suspendue.getId()).orElseThrow();
        assertEquals(StatutPublication.SUSPENDUE, reloaded.getStatutPublication());
    }

    @Test
    @DisplayName("P0-2 : Modération admin DISPONIBLE <-> SUSPENDUE et clôture ANNULE des transactions INITIALISE impayées")
    void testAdminSuspensionClotureInitialiseEtLeveeSuspension() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE);

        TransactionEntity txInitialise = transactionRepository.save(TransactionEntity.builder()
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(300000))
                .montantPremierLoyer(BigDecimal.valueOf(300000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .dateExpiration(LocalDateTime.now().plusHours(24))
                .statutSequestre(StatutSequestre.INITIALISE)
                .statutBail(StatutBail.NON_SIGNE)
                .dossierActif(Boolean.TRUE)
                .build());

        // Non-admin refusé sur /api/v1/admin/annonces/{id}/statut
        AnnonceStatutUpdateDTO suspendDto = AnnonceStatutUpdateDTO.builder()
                .statutPublication(StatutPublication.SUSPENDUE)
                .motif("Annonce non conforme à vérifier")
                .version(annonce.getVersion())
                .build();

        mockMvc.perform(put("/api/v1/admin/annonces/" + annonce.getId() + "/statut")
                        .header("Authorization", "Bearer " + tokenProprietaire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(suspendDto)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/admin/annonces/" + annonce.getId() + "/statut")
                        .header("Authorization", "Bearer " + tokenLocataire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(suspendDto)))
                .andExpect(status().isForbidden());

        // Admin suspend -> 200 OK et la transaction INITIALISE est clôturée en ANNULE
        mockMvc.perform(put("/api/v1/admin/annonces/" + annonce.getId() + "/statut")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(suspendDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPublication", is("SUSPENDUE")));

        TransactionEntity closedTx = transactionRepository.findById(txInitialise.getId()).orElseThrow();
        assertEquals(StatutSequestre.ANNULE, closedTx.getStatutSequestre());
        assertFalse(closedTx.getDossierActif());
        assertNotNull(closedTx.getDateCloture());
        assertNotNull(closedTx.getMotifCloture());

        // Payer la transaction clôturée doit échouer en 409 sans appeler Wave
        mockMvc.perform(post("/api/v1/transactions/" + txInitialise.getId() + "/paiement-caution")
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isConflict());
        assertEquals(0, waveClientMock.getCheckoutCallCount());

        AnnonceEntity annonceApresSuspension = annonceRepository.findById(annonce.getId()).orElseThrow();

        // Admin tente de fixer manuellement LOUEE / RESERVEE -> 409 Conflict
        AnnonceStatutUpdateDTO illegalTarget = AnnonceStatutUpdateDTO.builder()
                .statutPublication(StatutPublication.LOUEE)
                .motif("Transition illégale vers louée")
                .version(annonceApresSuspension.getVersion())
                .build();
        mockMvc.perform(put("/api/v1/admin/annonces/" + annonce.getId() + "/statut")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(illegalTarget)))
                .andExpect(status().isConflict());

        // Admin lève la suspension -> 200 OK, retour à DISPONIBLE
        AnnonceStatutUpdateDTO reactivateDto = AnnonceStatutUpdateDTO.builder()
                .statutPublication(StatutPublication.DISPONIBLE)
                .motif("Pièces justificatives validées")
                .version(annonceApresSuspension.getVersion())
                .build();
        mockMvc.perform(put("/api/v1/admin/annonces/" + annonce.getId() + "/statut")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reactivateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPublication", is("DISPONIBLE")));
    }

    @Test
    @DisplayName("P0-2 : Suspension administrative refusée (409) lorsqu'un séquestre BLOQUE est actif, y compris via signalement")
    void testSuspensionRefuseeSiSequestreBloqueActif() {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE);

        transactionRepository.save(TransactionEntity.builder()
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(300000))
                .montantPremierLoyer(BigDecimal.valueOf(300000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.BLOQUE)
                .referenceWave("WAVE-SANDBOX-LOCKED")
                .datePaiement(LocalDateTime.now())
                .statutBail(StatutBail.NON_SIGNE)
                .dossierActif(Boolean.TRUE)
                .build());

        SignalementEntity sig = signalementRepository.save(SignalementEntity.builder()
                .annonce(annonce)
                .auteur(locataire)
                .motif(MotifSignalement.FAUSSE_ANNONCE)
                .description("Test blocage séquestre")
                .statut(StatutSignalement.EN_ATTENTE)
                .build());

        TraiterSignalementDTO traiter = TraiterSignalementDTO.builder()
                .statut(StatutSignalement.TRAITE)
                .suspendreAnnonce(true)
                .build();

        assertThrows(ResponseStatusException.class,
                () -> signalementService.traiterSignalement(sig.getId(), traiter));

        assertEquals(StatutPublication.DISPONIBLE,
                annonceRepository.findById(annonce.getId()).orElseThrow().getStatutPublication());
    }

    @Test
    @DisplayName("P0-2 Concurrence : édition propriétaire et suspension admin simultanées sans écrasement de SUSPENDUE")
    void testConcurrencyEditionProprioVsSuspensionAdmin() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Integer> adminStatuses = Collections.synchronizedList(new ArrayList<>());
        List<Integer> ownerStatuses = Collections.synchronizedList(new ArrayList<>());

        executor.submit(() -> {
            try {
                ready.countDown();
                start.await();
                AnnonceUpdateDTO edit = AnnonceUpdateDTO.builder()
                        .description("Description concurrente")
                        .version(annonce.getVersion())
                        .build();
                int st = mockMvc.perform(put("/api/v1/annonces/" + annonce.getId())
                                .header("Authorization", "Bearer " + tokenProprietaire)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(edit)))
                        .andReturn().getResponse().getStatus();
                if (st == 409) {
                    AnnonceEntity refreshed = annonceRepository.findById(annonce.getId()).orElseThrow();
                    AnnonceUpdateDTO retryEdit = AnnonceUpdateDTO.builder()
                            .description("Description concurrente")
                            .version(refreshed.getVersion())
                            .build();
                    st = mockMvc.perform(put("/api/v1/annonces/" + annonce.getId())
                                    .header("Authorization", "Bearer " + tokenProprietaire)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(retryEdit)))
                            .andReturn().getResponse().getStatus();
                }
                ownerStatuses.add(st);
            } catch (Exception e) {
                ownerStatuses.add(500);
            }
        });

        executor.submit(() -> {
            try {
                ready.countDown();
                start.await();
                AnnonceStatutUpdateDTO suspend = AnnonceStatutUpdateDTO.builder()
                        .statutPublication(StatutPublication.SUSPENDUE)
                        .motif("Suspension concurrente")
                        .version(annonce.getVersion())
                        .build();
                int st = mockMvc.perform(put("/api/v1/admin/annonces/" + annonce.getId() + "/statut")
                                .header("Authorization", "Bearer " + tokenAdmin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(suspend)))
                        .andReturn().getResponse().getStatus();
                adminStatuses.add(st);
            } catch (Exception e) {
                adminStatuses.add(500);
            }
        });

        ready.await();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(List.of(200), adminStatuses, "La suspension admin doit réussir sans perte de mise à jour");
        assertEquals(List.of(200), ownerStatuses, "L'édition éditoriale doit s'exécuter proprement après ou avant le verrou");

        AnnonceEntity finalAnnonce = annonceRepository.findById(annonce.getId()).orElseThrow();
        assertEquals(StatutPublication.SUSPENDUE, finalAnnonce.getStatutPublication(),
                "La mise à jour propriétaire ne doit jamais écraser la suspension administrative");
        assertEquals("Description concurrente", finalAnnonce.getDescription());
    }
}
