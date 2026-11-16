package com.ecourtier.domain.annonce.controller;

import com.ecourtier.common.exception.GlobalExceptionHandler;
import com.ecourtier.common.security.JwtService;
import com.ecourtier.domain.annonce.dto.AnnonceCreateDTO;
import com.ecourtier.domain.annonce.dto.AnnonceUpdateDTO;
import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.avis.repository.AvisRepository;
import com.ecourtier.domain.avis.repository.SignalementRepository;
import com.ecourtier.domain.favori.repository.FavoriRepository;
import com.ecourtier.domain.rendezvous.repository.RendezVousRepository;
import com.ecourtier.domain.transaction.repository.PaiementWaveRepository;
import com.ecourtier.domain.transaction.repository.TransactionRepository;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.RefreshTokenRepository;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnnonceOptimisticLockIntegrationTest {

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
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private FavoriRepository favoriRepository;

    @Autowired
    private AvisRepository avisRepository;

    @Autowired
    private SignalementRepository signalementRepository;

    @Autowired
    private RendezVousRepository rendezVousRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PaiementWaveRepository paiementWaveRepository;

    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

    @Autowired
    private com.ecourtier.testsupport.TestDatabaseCleaner testDatabaseCleaner;

    private UtilisateurEntity proprietaire;
    private String tokenProprietaire;
    private UUID annonceId;

    @BeforeEach
    void setUp() throws Exception {
        cleanupDatabase();

        proprietaire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Ndiaye")
                .prenom("Fatou")
                .email("fatou.optlock@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass123!"))
                .telephone("+221770003344")
                .role(Role.PROPRIETAIRE)
                .actif(true)
                .build());

        tokenProprietaire = jwtService.generateAccessToken(proprietaire);

        AnnonceCreateDTO createDto = AnnonceCreateDTO.builder()
                .titre("Appartement F4 Mermoz Initial")
                .description("Grand appartement avec terrasse")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(new BigDecimal("350000"))
                .superficie(120.0)
                .nombrePieces(4)
                .quartier("Mermoz")
                .ville("Dakar")
                .latitude(14.7061)
                .longitude(-17.4694)
                .meuble(false)
                .animauxAcceptes(false)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/annonces")
                        .header("Authorization", "Bearer " + tokenProprietaire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();

        JsonNode createdJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        annonceId = UUID.fromString(createdJson.get("id").asText());
    }

    @AfterEach
    void tearDown() {
        cleanupDatabase();
    }

    private void cleanupDatabase() {
        testDatabaseCleaner.cleanAll();
    }

    @Test
    @DisplayName("Version valide -> 200 OK et incrémentation atomique de version ; version obsolète -> 409 CONFLICT")
    void testVersionValideEtStaleConflict409() throws Exception {
        AnnonceUpdateDTO validUpdate = AnnonceUpdateDTO.builder()
                .titre("Appartement F4 Mermoz V1")
                .prix(new BigDecimal("360000"))
                .version(0L)
                .build();

        mockMvc.perform(put("/api/v1/annonces/" + annonceId)
                        .header("Authorization", "Bearer " + tokenProprietaire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titre").value("Appartement F4 Mermoz V1"))
                .andExpect(jsonPath("$.version").value(1));

        // Tentative concurrente / tardive avec l'ancienne version 0 -> 409 Conflict
        AnnonceUpdateDTO staleUpdate = AnnonceUpdateDTO.builder()
                .titre("Ecrasement silencieux")
                .prix(new BigDecimal("290000"))
                .version(0L)
                .build();

        mockMvc.perform(put("/api/v1/annonces/" + annonceId)
                        .header("Authorization", "Bearer " + tokenProprietaire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(staleUpdate)))
                .andExpect(status().isConflict());

        // Vérifier que les données persistées restent intactes à V1
        mockMvc.perform(get("/api/v1/annonces/" + annonceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titre").value("Appartement F4 Mermoz V1"))
                .andExpect(jsonPath("$.prix").value(360000))
                .andExpect(jsonPath("$.version").value(1));

        // Phase 3 (B1) : l'omission du champ version doit être rejetée en 400 Bad Request (pas de contournement)
        AnnonceUpdateDTO withoutVersionUpdate = AnnonceUpdateDTO.builder()
                .titre("Appartement F4 Mermoz Sans Version")
                .build();

        mockMvc.perform(put("/api/v1/annonces/" + annonceId)
                        .header("Authorization", "Bearer " + tokenProprietaire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withoutVersionUpdate)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/annonces/" + annonceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titre").value("Appartement F4 Mermoz V1"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("Concurrence réelle 2 threads avec même version 0 -> un 200 OK et un 409 CONFLICT")
    void testConcurrenceDeuxThreadsMemeVersion() throws Exception {
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(2);

        for (int i = 0; i < 2; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    AnnonceUpdateDTO concurrentUpdate = AnnonceUpdateDTO.builder()
                            .titre("Modification Thread " + index)
                            .prix(new BigDecimal(370000 + index * 10000))
                            .version(0L)
                            .build();
                    String body = objectMapper.writeValueAsString(concurrentUpdate);

                    readyLatch.countDown();
                    startLatch.await(5, TimeUnit.SECONDS);

                    MvcResult res = mockMvc.perform(put("/api/v1/annonces/" + annonceId)
                                    .header("Authorization", "Bearer " + tokenProprietaire)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                            .andReturn();
                    statuses.add(res.getResponse().getStatus());
                } catch (Exception e) {
                    statuses.add(500);
                }
            });
        }

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
        startLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

        assertEquals(2, statuses.size());
        long okCount = statuses.stream().filter(s -> s == 200).count();
        long conflictCount = statuses.stream().filter(s -> s == 409).count();

        assertEquals(1, okCount, "Exactement une mise à jour doit réussir (statuses=" + statuses + ")");
        assertEquals(1, conflictCount, "Exactement une mise à jour concurrente doit recevoir 409 (statuses=" + statuses + ")");

        AnnonceEntity persisted = annonceRepository.findById(annonceId).orElseThrow();
        assertEquals(1L, persisted.getVersion());
        assertNotNull(persisted.getTitre());
    }

    @Test
    @DisplayName("GlobalExceptionHandler mappe ObjectOptimisticLockingFailureException vers 409 CONFLICT")
    void testGlobalExceptionHandlerOptimisticLock409() {
        ResponseEntity<Map<String, Object>> response = globalExceptionHandler.handleOptimisticLockingFailure(
                new ObjectOptimisticLockingFailureException(AnnonceEntity.class, annonceId)
        );
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().get("status"));
    }

    @Test
    @DisplayName("CA3-004: L'ADMIN peut suspendre (DISPONIBLE -> SUSPENDUE) et réactiver (SUSPENDUE -> DISPONIBLE) une annonce avec contrôle de version et refus 409/403")
    void testAdminStatutPublicationSuspendReactivateAndOptimisticConflict() throws Exception {
        UtilisateurEntity adminUser = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Admin")
                .prenom("Moderation")
                .email("admin.statut.ca3004@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass123!"))
                .telephone("+221770009988")
                .role(Role.ADMIN)
                .actif(true)
                .build());
        String tokenAdmin = jwtService.generateAccessToken(adminUser);

        // 1. Un propriétaire (non-ADMIN) ne peut pas appeler PUT /api/v1/admin/annonces/{id}/statut -> 403
        mockMvc.perform(put("/api/v1/admin/annonces/" + annonceId + "/statut")
                        .header("Authorization", "Bearer " + tokenProprietaire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statutPublication\":\"SUSPENDUE\",\"motif\":\"Tentative non-admin\",\"version\":0}"))
                .andExpect(status().isForbidden());

        // 2. Suspension par l'ADMIN (DISPONIBLE -> SUSPENDUE) avec version 0 -> 200 OK et version passe à 1
        mockMvc.perform(put("/api/v1/admin/annonces/" + annonceId + "/statut")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statutPublication\":\"SUSPENDUE\",\"motif\":\"Signalement vérifié\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPublication").value("SUSPENDUE"))
                .andExpect(jsonPath("$.version").value(1));

        // 3. Tentative de réactivation avec une version obsolète (version=0 au lieu de 1) -> 409 CONFLICT
        mockMvc.perform(put("/api/v1/admin/annonces/" + annonceId + "/statut")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statutPublication\":\"DISPONIBLE\",\"motif\":\"Levée avec version obsolète\",\"version\":0}"))
                .andExpect(status().isConflict());

        // 4. Réactivation par l'ADMIN (SUSPENDUE -> DISPONIBLE) avec version 1 -> 200 OK et version passe à 2
        mockMvc.perform(put("/api/v1/admin/annonces/" + annonceId + "/statut")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statutPublication\":\"DISPONIBLE\",\"motif\":\"Levée de suspension après contrôle\",\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPublication").value("DISPONIBLE"))
                .andExpect(jsonPath("$.version").value(2));

        // 5. Transition directe illégale (DISPONIBLE -> LOUEE sans séquestre/bail signé) -> 409 CONFLICT
        mockMvc.perform(put("/api/v1/admin/annonces/" + annonceId + "/statut")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statutPublication\":\"LOUEE\",\"motif\":\"Transition illégale\",\"version\":2}"))
                .andExpect(status().isConflict());

        // 6. CA3-004 : Version absente -> 400 Bad Request (aucun contournement du verrouillage optimiste)
        mockMvc.perform(put("/api/v1/admin/annonces/" + annonceId + "/statut")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statutPublication\":\"SUSPENDUE\",\"motif\":\"Tentative sans version\"}"))
                .andExpect(status().isBadRequest());

        // 7. CA3-004 : Version explicite null -> 400 Bad Request
        mockMvc.perform(put("/api/v1/admin/annonces/" + annonceId + "/statut")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statutPublication\":\"SUSPENDUE\",\"motif\":\"Tentative version nulle\",\"version\":null}"))
                .andExpect(status().isBadRequest());

        // 8. CA3-004 : Motif de suspension absent ou vide -> 400 Bad Request
        mockMvc.perform(put("/api/v1/admin/annonces/" + annonceId + "/statut")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statutPublication\":\"SUSPENDUE\",\"version\":2}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/admin/annonces/" + annonceId + "/statut")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statutPublication\":\"SUSPENDUE\",\"motif\":\"   \",\"version\":2}"))
                .andExpect(status().isBadRequest());
    }
}
