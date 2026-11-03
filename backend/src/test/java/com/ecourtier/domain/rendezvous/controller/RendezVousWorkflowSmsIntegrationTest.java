package com.ecourtier.domain.rendezvous.controller;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.rendezvous.entity.NotificationSmsEntity;
import com.ecourtier.domain.rendezvous.entity.RendezVousEntity;
import com.ecourtier.domain.rendezvous.entity.StatutRendezVous;
import com.ecourtier.domain.rendezvous.entity.TypeNotificationSms;
import com.ecourtier.domain.rendezvous.repository.NotificationSmsRepository;
import com.ecourtier.domain.rendezvous.repository.RendezVousRepository;
import com.ecourtier.domain.rendezvous.service.RendezVousService;
import com.ecourtier.domain.rendezvous.sms.SmsGateway;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RendezVousWorkflowSmsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private RendezVousRepository rendezVousRepository;

    @Autowired
    private NotificationSmsRepository notificationSmsRepository;

    @Autowired
    private RendezVousService rendezVousService;

    @MockitoSpyBean
    private SmsGateway smsGateway;

    private UtilisateurEntity proprietaire;
    private UtilisateurEntity locataire1;
    private UtilisateurEntity locataire2;
    private AnnonceEntity annonceAuto;
    private AnnonceEntity annonceManuelle;

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        Mockito.reset(smsGateway);
        notificationSmsRepository.deleteAll();
        rendezVousRepository.deleteAll();
        annonceRepository.deleteAll();
    }

    @BeforeEach
    void setUp() {
        Mockito.reset(smsGateway);
        notificationSmsRepository.deleteAll();
        rendezVousRepository.deleteAll();
        annonceRepository.deleteAll();

        proprietaire = utilisateurRepository.findByEmail("prop.sms.it@ecourtier.sn")
                .orElseGet(() -> utilisateurRepository.save(UtilisateurEntity.builder()
                        .nom("Ba")
                        .prenom("Mamadou")
                        .email("prop.sms.it@ecourtier.sn")
                        .motDePasse("$2a$10$KhUqg3EWfmNgLhTIGhjP0uzeSXRI1y0ulUhiJyv1Qz0H34Ybju3IS")
                        .telephone("+221771001122")
                        .role(Role.PROPRIETAIRE)
                        .actif(true)
                        .build()));

        locataire1 = utilisateurRepository.findByEmail("loc1.sms.it@ecourtier.sn")
                .orElseGet(() -> utilisateurRepository.save(UtilisateurEntity.builder()
                        .nom("Ndiaye")
                        .prenom("Fatou")
                        .email("loc1.sms.it@ecourtier.sn")
                        .motDePasse("$2a$10$KhUqg3EWfmNgLhTIGhjP0uzeSXRI1y0ulUhiJyv1Qz0H34Ybju3IS")
                        .telephone("+221772003344")
                        .role(Role.LOCATAIRE)
                        .actif(true)
                        .build()));

        locataire2 = utilisateurRepository.findByEmail("loc2.sms.it@ecourtier.sn")
                .orElseGet(() -> utilisateurRepository.save(UtilisateurEntity.builder()
                        .nom("Fall")
                        .prenom("Cheikh")
                        .email("loc2.sms.it@ecourtier.sn")
                        .motDePasse("$2a$10$KhUqg3EWfmNgLhTIGhjP0uzeSXRI1y0ulUhiJyv1Qz0H34Ybju3IS")
                        .telephone("+221773005566")
                        .role(Role.LOCATAIRE)
                        .actif(true)
                        .build()));

        annonceAuto = annonceRepository.save(AnnonceEntity.builder()
                .titre("Appartement Mermoz Auto-Confirm")
                .description("Appartement F3 avec confirmation automatique activée")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(300000))
                .superficie(95.0)
                .nombrePieces(3)
                .quartier("Mermoz")
                .ville("Dakar")
                .latitude(14.7085)
                .longitude(-17.4725)
                .meuble(true)
                .animauxAcceptes(false)
                .confirmationAutomatique(true)
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(StatutPublication.DISPONIBLE)
                .supprime(false)
                .utilisateur(proprietaire)
                .build());

        annonceManuelle = annonceRepository.save(AnnonceEntity.builder()
                .titre("Villa Almadies Confirmation Manuelle")
                .description("Villa avec validation manuelle des créneaux de visite")
                .type(TypeBien.VILLA)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(850000))
                .superficie(240.0)
                .nombrePieces(5)
                .quartier("Almadies")
                .ville("Dakar")
                .latitude(14.7445)
                .longitude(-17.5162)
                .meuble(false)
                .animauxAcceptes(true)
                .confirmationAutomatique(false)
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(StatutPublication.DISPONIBLE)
                .supprime(false)
                .utilisateur(proprietaire)
                .build());
    }

    @Test
    @DisplayName("F16 : Confirmation automatique si confirmationAutomatique=true et aucun conflit à ±60 min ; conflit à +30 min reste DEMANDE")
    void testConfirmationAutomatiqueEtConflitResteDemande() throws Exception {
        LocalDateTime creneau1 = LocalDateTime.now().plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime creneauConflit30Min = creneau1.plusMinutes(30);
        LocalDateTime creneauLibrePlus2h = creneau1.plusHours(2);

        // 1. Premier RDV sur annonceAuto -> passe directement à CONFIRME
        mockMvc.perform(post("/api/v1/annonces/" + annonceAuto.getId() + "/rendez-vous")
                        .with(user("loc1.sms.it@ecourtier.sn").roles("LOCATAIRE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateHeure\":\"" + creneau1 + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut", is("CONFIRME")));

        // 2. Deuxième RDV à +30 min (dans la fenêtre ±60 min) -> reste DEMANDE (pas d'erreur, confirmation manuelle requise)
        mockMvc.perform(post("/api/v1/annonces/" + annonceAuto.getId() + "/rendez-vous")
                        .with(user("loc2.sms.it@ecourtier.sn").roles("LOCATAIRE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateHeure\":\"" + creneauConflit30Min + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut", is("DEMANDE")));

        // 3. Troisième RDV à +2h (hors fenêtre ±60 min) -> passe directement à CONFIRME
        mockMvc.perform(post("/api/v1/annonces/" + annonceAuto.getId() + "/rendez-vous")
                        .with(user("loc2.sms.it@ecourtier.sn").roles("LOCATAIRE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateHeure\":\"" + creneauLibrePlus2h + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut", is("CONFIRME")));
    }

    @Test
    @DisplayName("F17 & S3 : SMS simulés émis à chaque étape (demande, confirmation, rappel, annulation à l'autre partie) et exposés via GET /mes-sms")
    void testSmsEmisAChaqueEtapeEtEndpointMesSms() throws Exception {
        LocalDate tomorrowDakar = ZonedDateTime.now(ZoneId.of("Africa/Dakar")).toLocalDate().plusDays(1);
        LocalDateTime demain15h = tomorrowDakar.atTime(15, 0);

        // 1. Demande sur annonceManuelle -> statut DEMANDE + 2 SMS RDV_DEMANDE (locataire1 + proprietaire)
        MvcResult createRes = mockMvc.perform(post("/api/v1/annonces/" + annonceManuelle.getId() + "/rendez-vous")
                        .with(user("loc1.sms.it@ecourtier.sn").roles("LOCATAIRE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateHeure\":\"" + demain15h + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut", is("DEMANDE")))
                .andReturn();

        UUID rdvId = UUID.fromString(objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText());

        List<NotificationSmsEntity> afterDemande = notificationSmsRepository.findByRendezVousIdOrderByDateCreationAsc(rdvId);
        assertEquals(2, afterDemande.size());
        assertTrue(afterDemande.stream().anyMatch(s ->
                s.getType() == TypeNotificationSms.RDV_DEMANDE
                        && s.getUtilisateur().getId().equals(locataire1.getId())
                        && s.getMessage().toLowerCase().contains("demande enregistrée")
                        && "SIMULE".equals(s.getStatut())));
        assertTrue(afterDemande.stream().anyMatch(s ->
                s.getType() == TypeNotificationSms.RDV_DEMANDE
                        && s.getUtilisateur().getId().equals(proprietaire.getId())
                        && s.getMessage().toLowerCase().contains("nouvelle demande")));

        // 2. Confirmation manuelle par le propriétaire -> SMS RDV_CONFIRME au locataire
        mockMvc.perform(put("/api/v1/rendez-vous/" + rdvId + "/confirmer")
                        .with(user("prop.sms.it@ecourtier.sn").roles("PROPRIETAIRE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut", is("CONFIRME")));

        List<NotificationSmsEntity> afterConf = notificationSmsRepository.findByRendezVousIdOrderByDateCreationAsc(rdvId);
        assertEquals(3, afterConf.size());
        assertEquals(TypeNotificationSms.RDV_CONFIRME, afterConf.get(2).getType());
        assertEquals(locataire1.getId(), afterConf.get(2).getUtilisateur().getId());

        // 3. Rappel la veille (idempotent : 1ère exécution = 1 SMS, 2nde exécution = 0 SMS)
        int premierPassage = rendezVousService.simulerRappelsDemain();
        int secondPassage = rendezVousService.simulerRappelsDemain();
        assertEquals(1, premierPassage);
        assertEquals(0, secondPassage);

        List<NotificationSmsEntity> afterRappel = notificationSmsRepository.findByRendezVousIdOrderByDateCreationAsc(rdvId);
        assertEquals(4, afterRappel.size());
        assertEquals(TypeNotificationSms.RDV_RAPPEL, afterRappel.get(3).getType());
        assertEquals(locataire1.getId(), afterRappel.get(3).getUtilisateur().getId());

        // 4. Annulation par le locataire -> SMS RDV_ANNULE envoyé à l'autre partie (propriétaire)
        mockMvc.perform(put("/api/v1/rendez-vous/" + rdvId + "/annuler")
                        .with(user("loc1.sms.it@ecourtier.sn").roles("LOCATAIRE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motif\":\"Contretemps professionnel\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut", is("ANNULE")));

        List<NotificationSmsEntity> afterAnnul = notificationSmsRepository.findByRendezVousIdOrderByDateCreationAsc(rdvId);
        assertEquals(5, afterAnnul.size());
        assertEquals(TypeNotificationSms.RDV_ANNULE, afterAnnul.get(4).getType());
        assertEquals(proprietaire.getId(), afterAnnul.get(4).getUtilisateur().getId());

        // 5. Consultation paginée via GET /api/v1/notifications-sms/mes-sms
        // Locataire1 a reçu : RDV_DEMANDE + RDV_CONFIRME + RDV_RAPPEL = 3 SMS
        mockMvc.perform(get("/api/v1/notifications-sms/mes-sms?page=0&size=10")
                        .with(user("loc1.sms.it@ecourtier.sn").roles("LOCATAIRE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.content", hasSize(3)));

        // Propriétaire a reçu : RDV_DEMANDE + RDV_ANNULE = 2 SMS
        mockMvc.perform(get("/api/v1/notifications-sms/mes-sms?page=0&size=10")
                        .with(user("prop.sms.it@ecourtier.sn").roles("PROPRIETAIRE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    @DisplayName("S3 Résilience : Une panne du SmsGateway ne fait jamais échouer le RDV en base")
    void testPanneSmsGatewayNeFaitJamaisEchouerLeRdv() throws Exception {
        doThrow(new RuntimeException("Gateway SMS hors ligne"))
                .when(smsGateway).envoyerSms(any(), any(), any(), any(), any());

        LocalDateTime creneau = LocalDateTime.now().plusDays(4).withHour(11).withMinute(0).withSecond(0).withNano(0);

        MvcResult res = mockMvc.perform(post("/api/v1/annonces/" + annonceAuto.getId() + "/rendez-vous")
                        .with(user("loc1.sms.it@ecourtier.sn").roles("LOCATAIRE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateHeure\":\"" + creneau + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut", is("CONFIRME")))
                .andReturn();

        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        UUID rdvId = UUID.fromString(json.get("id").asText());

        RendezVousEntity persisted = rendezVousRepository.findById(rdvId).orElseThrow();
        assertEquals(StatutRendezVous.CONFIRME, persisted.getStatut());
        assertEquals(0, notificationSmsRepository.count());
    }

    @Test
    @DisplayName("RendezVous : Confirmation manuelle, marquer effectué, mes RDV, agenda annonce et simulation rappels admin")
    void testEndpointsRendezVousComplementaires() throws Exception {
        LocalDateTime creneau = LocalDateTime.now().plusDays(2).withHour(15).withMinute(0).withSecond(0).withNano(0);

        // 1. Demande sur annonce manuelle -> statut DEMANDE
        MvcResult res = mockMvc.perform(post("/api/v1/annonces/" + annonceManuelle.getId() + "/rendez-vous")
                        .with(user("loc1.sms.it@ecourtier.sn").roles("LOCATAIRE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateHeure\":\"" + creneau + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut", is("DEMANDE")))
                .andReturn();

        UUID rdvId = UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());

        // 2. Propriétaire confirme manuellement -> CONFIRME
        mockMvc.perform(put("/api/v1/rendez-vous/" + rdvId + "/confirmer")
                        .with(user("prop.sms.it@ecourtier.sn").roles("PROPRIETAIRE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut", is("CONFIRME")));

        // 3. Propriétaire marque le RDV comme effectué -> EFFECTUE
        mockMvc.perform(put("/api/v1/rendez-vous/" + rdvId + "/marquer-effectue")
                        .with(user("prop.sms.it@ecourtier.sn").roles("PROPRIETAIRE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut", is("EFFECTUE")));

        // 4. Locataire consulte ses rendez-vous
        mockMvc.perform(get("/api/v1/rendez-vous/mes-rendez-vous")
                        .with(user("loc1.sms.it@ecourtier.sn").roles("LOCATAIRE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(rdvId.toString())));

        // 5. Propriétaire consulte l'agenda de l'annonce
        mockMvc.perform(get("/api/v1/annonces/" + annonceManuelle.getId() + "/rendez-vous")
                        .with(user("prop.sms.it@ecourtier.sn").roles("PROPRIETAIRE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // 6. Admin simule les rappels SMS de demain
        mockMvc.perform(post("/api/v1/rendez-vous/simuler-rappels")
                        .with(user("admin.test@ecourtier.sn").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut", is("SUCCESS")));
    }
}
