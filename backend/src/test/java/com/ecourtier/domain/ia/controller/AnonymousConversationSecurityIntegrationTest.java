package com.ecourtier.domain.ia.controller;

import com.ecourtier.domain.ia.dto.MessageUserRequestDTO;
import com.ecourtier.domain.ia.entity.ConversationIAEntity;
import com.ecourtier.domain.ia.repository.ConversationIARepository;
import com.ecourtier.domain.ia.repository.MessageIARepository;
import com.ecourtier.domain.ia.service.IAService;
import com.ecourtier.domain.ia.service.IaRateLimiterService;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "ecourtier.cors.allowed-origins=https://ecourtier-frontend.onrender.com,http://localhost:3000")
@Transactional
class AnonymousConversationSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConversationIARepository conversationIARepository;

    @Autowired
    private MessageIARepository messageIARepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private IaRateLimiterService iaRateLimiterService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.ecourtier.testsupport.TestDatabaseCleaner testDatabaseCleaner;

    private UtilisateurEntity utilisateurA;
    private UtilisateurEntity utilisateurB;

    @AfterEach
    void tearDown() {
        iaRateLimiterService.clear();
        testDatabaseCleaner.cleanAll();
    }

    @BeforeEach
    void setUp() {
        iaRateLimiterService.clear();
        testDatabaseCleaner.cleanAll();

        utilisateurA = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Diallo")
                .prenom("Mamadou")
                .email("mamadou.diallo@test.sn")
                .motDePasse("Password123!")
                .telephone("+221 77 111 22 33")
                .role(Role.LOCATAIRE)
                .actif(true)
                .build());

        utilisateurB = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Sow")
                .prenom("Fatou")
                .email("fatou.sow@test.sn")
                .motDePasse("Password123!")
                .telephone("+221 77 444 55 66")
                .role(Role.LOCATAIRE)
                .actif(true)
                .build());
    }

    @Test
    @DisplayName("1. Création d'une conversation anonyme : jeton X-IA-Session-Token émis en JSON et hash SHA-256 persisté sans cookie")
    void test01_CreationConversationAnonyme_DelivrePreuvePossession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/ia/conversations"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conversationId").exists())
                .andExpect(jsonPath("$.sessionToken").exists())
                .andReturn();

        assertNull(result.getResponse().getCookie("ecourtier_ia_session"),
                "Aucun cookie cross-origin ne doit être émis pour l'assistant IA");

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID convId = UUID.fromString(jsonNode.get("conversationId").asText());
        String rawToken = jsonNode.get("sessionToken").asText();
        assertNotNull(rawToken);
        assertFalse(rawToken.isBlank());

        ConversationIAEntity conv = conversationIARepository.findById(convId).orElseThrow();
        assertNull(conv.getUtilisateur(), "L'utilisateur doit être null pour une conversation anonyme");
        assertNotNull(conv.getSessionTokenHash(), "Le hash du jeton doit être enregistré en base");
        assertEquals(IAService.hashToken(rawToken), conv.getSessionTokenHash(),
                "Le hash en base doit correspondre au SHA-256 du jeton");
        assertNotNull(conv.getSessionExpiration(), "Une date d'expiration doit être définie");
        assertTrue(conv.getSessionExpiration().isAfter(LocalDateTime.now()), "L'expiration doit être dans le futur");
    }

    @Test
    @DisplayName("2. Parcours anonyme complet (création -> 2 messages -> rechargement GET après F5 -> nouvelle conversation) : 200 OK")
    void test02_ParcoursAnonymeComplet_Autorise() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/ia/conversations"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID convId = UUID.fromString(created.get("conversationId").asText());
        String token = created.get("sessionToken").asText();

        mockMvc.perform(post("/api/v1/ia/conversations/" + convId + "/messages")
                        .header(IAService.IA_SESSION_HEADER_NAME, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MessageUserRequestDTO("Je cherche une chambre à Dakar"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reponse").exists());

        mockMvc.perform(post("/api/v1/ia/conversations/" + convId + "/messages")
                        .header(IAService.IA_SESSION_HEADER_NAME, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MessageUserRequestDTO("Budget max 90 000 FCFA"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reponse").exists());

        // Simulation rechargement F5
        mockMvc.perform(get("/api/v1/ia/conversations/" + convId)
                        .header(IAService.IA_SESSION_HEADER_NAME, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(convId.toString()))
                .andExpect(jsonPath("$.messages.length()").value(4));
    }

    @Test
    @DisplayName("3. Lecture de l'historique sans en-tête X-IA-Session-Token : refusée (403 Forbidden)")
    void test03_LectureSansPreuve_Refusee() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/ia/conversations"))
                .andExpect(status().isCreated())
                .andReturn();

        UUID convId = UUID.fromString(objectMapper.readTree(createResult.getResponse().getContentAsString()).get("conversationId").asText());

        mockMvc.perform(get("/api/v1/ia/conversations/" + convId))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("4. Lecture de l'historique avec une preuve incorrecte : refusée (403 Forbidden)")
    void test04_LecturePreuveIncorrecte_Refusee() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/ia/conversations"))
                .andExpect(status().isCreated())
                .andReturn();

        UUID convId = UUID.fromString(objectMapper.readTree(createResult.getResponse().getContentAsString()).get("conversationId").asText());

        mockMvc.perform(get("/api/v1/ia/conversations/" + convId)
                        .header(IAService.IA_SESSION_HEADER_NAME, "jeton_frauduleux_inexistant_1234567890abcdef"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("5. Ajout d'un message sans preuve de possession : refusé (403 Forbidden)")
    void test05_AjoutMessageSansPreuve_Refuse() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/ia/conversations"))
                .andExpect(status().isCreated())
                .andReturn();

        UUID convId = UUID.fromString(objectMapper.readTree(createResult.getResponse().getContentAsString()).get("conversationId").asText());

        mockMvc.perform(post("/api/v1/ia/conversations/" + convId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MessageUserRequestDTO("Bonjour Samba"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("6. Ajout d'un message avec preuve X-IA-Session-Token valide : autorisé (200 OK)")
    void test06_AjoutMessageAvecPreuveValide_Autorise() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/ia/conversations"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID convId = UUID.fromString(created.get("conversationId").asText());
        String token = created.get("sessionToken").asText();

        mockMvc.perform(post("/api/v1/ia/conversations/" + convId + "/messages")
                        .header(IAService.IA_SESSION_HEADER_NAME, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MessageUserRequestDTO("Je cherche une chambre à Dakar"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reponse").exists());
    }

    @Test
    @DisplayName("7. UUID seul insuffisant : deux sessions anonymes distinctes ne peuvent pas accéder aux conversations de l'autre")
    void test07_UuidSeulInsuffisant_IsolationSessionsAnonymes() throws Exception {
        MvcResult res1 = mockMvc.perform(post("/api/v1/ia/conversations")).andExpect(status().isCreated()).andReturn();
        JsonNode json1 = objectMapper.readTree(res1.getResponse().getContentAsString());
        UUID convId1 = UUID.fromString(json1.get("conversationId").asText());
        String token1 = json1.get("sessionToken").asText();

        MvcResult res2 = mockMvc.perform(post("/api/v1/ia/conversations")).andExpect(status().isCreated()).andReturn();
        JsonNode json2 = objectMapper.readTree(res2.getResponse().getContentAsString());
        UUID convId2 = UUID.fromString(json2.get("conversationId").asText());
        String token2 = json2.get("sessionToken").asText();

        mockMvc.perform(get("/api/v1/ia/conversations/" + convId1)
                        .header(IAService.IA_SESSION_HEADER_NAME, token2))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ia/conversations/" + convId1 + "/messages")
                        .header(IAService.IA_SESSION_HEADER_NAME, token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MessageUserRequestDTO("Intrus"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ia/conversations/" + convId2)
                        .header(IAService.IA_SESSION_HEADER_NAME, token1))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ia/conversations/" + convId1)
                        .header(IAService.IA_SESSION_HEADER_NAME, token1))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/ia/conversations/" + convId2)
                        .header(IAService.IA_SESSION_HEADER_NAME, token2))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("8. Conversation authentifiée : seul son propriétaire peut y accéder et aucun sessionToken anonyme n'est émis")
    @WithMockUser(username = "mamadou.diallo@test.sn", roles = {"LOCATAIRE"})
    void test08_ConversationAuthentifiee_ProprietaireAutorise() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/ia/conversations"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionToken").doesNotExist())
                .andReturn();

        UUID convId = UUID.fromString(objectMapper.readTree(createResult.getResponse().getContentAsString()).get("conversationId").asText());

        mockMvc.perform(get("/api/v1/ia/conversations/" + convId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(convId.toString()));
    }

    @Test
    @DisplayName("9. Utilisateur B tentant d'accéder à la conversation authentifiée de A : refusé (403 Forbidden)")
    @WithMockUser(username = "fatou.sow@test.sn", roles = {"LOCATAIRE"})
    void test09_UtilisateurBTentantAccesConversationA_Refuse() throws Exception {
        ConversationIAEntity convA = conversationIARepository.save(ConversationIAEntity.builder()
                .utilisateur(utilisateurA)
                .dateCreation(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/v1/ia/conversations/" + convA.getId()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ia/conversations/" + convA.getId() + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MessageUserRequestDTO("Tentative pirate"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("10. Utilisateur anonyme tentant d'accéder à une conversation authentifiée : refusé (403 Forbidden)")
    void test10_UtilisateurAnonymeTentantAccesConversationAuthentifiee_Refuse() throws Exception {
        ConversationIAEntity convA = conversationIARepository.save(ConversationIAEntity.builder()
                .utilisateur(utilisateurA)
                .dateCreation(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/v1/ia/conversations/" + convA.getId()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ia/conversations/" + convA.getId())
                        .header(IAService.IA_SESSION_HEADER_NAME, "random_session_anon_token_12345"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("11. Expiration de la session anonyme : accès refusé après expiration (403 Forbidden)")
    void test11_ExpirationPreuve_Refuse() throws Exception {
        String token = IAService.generateSecureToken();
        String hash = IAService.hashToken(token);

        ConversationIAEntity convExpiree = conversationIARepository.save(ConversationIAEntity.builder()
                .sessionTokenHash(hash)
                .sessionExpiration(LocalDateTime.now().minusHours(1))
                .dateCreation(LocalDateTime.now().minusDays(8))
                .build());

        mockMvc.perform(get("/api/v1/ia/conversations/" + convExpiree.getId())
                        .header(IAService.IA_SESSION_HEADER_NAME, token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("12. Aucune fuite du secret ou du hash dans l'historique GET ou l'envoi de message POST")
    void test12_AucuneFuiteDuSecretDansHistoriqueOuMessages() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/ia/conversations"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String rawToken = created.get("sessionToken").asText();
        UUID convId = UUID.fromString(created.get("conversationId").asText());

        MvcResult msgResult = mockMvc.perform(post("/api/v1/ia/conversations/" + convId + "/messages")
                        .header(IAService.IA_SESSION_HEADER_NAME, rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MessageUserRequestDTO("Studio à Dakar"))))
                .andExpect(status().isOk())
                .andReturn();
        String jsonMsg = msgResult.getResponse().getContentAsString();
        assertFalse(jsonMsg.contains(rawToken));
        assertFalse(jsonMsg.contains("sessionToken"));
        assertFalse(jsonMsg.contains("sessionTokenHash"));

        MvcResult histResult = mockMvc.perform(get("/api/v1/ia/conversations/" + convId)
                        .header(IAService.IA_SESSION_HEADER_NAME, rawToken))
                .andExpect(status().isOk())
                .andReturn();
        String jsonHist = histResult.getResponse().getContentAsString();
        assertFalse(jsonHist.contains(rawToken));
        assertFalse(jsonHist.contains("sessionToken"));
        assertFalse(jsonHist.contains("sessionTokenHash"));
    }

    @Test
    @DisplayName("13. Anciennes conversations anonymes sans preuve de possession : accès refusé par défaut (403 Forbidden)")
    void test13_AnciennesConversationsAnonymesSansPreuve_Refusees() throws Exception {
        ConversationIAEntity convAncienne = conversationIARepository.save(ConversationIAEntity.builder()
                .sessionTokenHash(null)
                .sessionExpiration(null)
                .dateCreation(LocalDateTime.now().minusDays(30))
                .build());

        mockMvc.perform(get("/api/v1/ia/conversations/" + convAncienne.getId()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ia/conversations/" + convAncienne.getId())
                        .header(IAService.IA_SESSION_HEADER_NAME, "any_token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("14. Requête CORS preflight OPTIONS cross-origin autorisant explicitement X-IA-Session-Token")
    void test14_CorsPreflightAutoriseHeaderSessionIa() throws Exception {
        MvcResult preflight = mockMvc.perform(options("/api/v1/ia/conversations")
                        .header("Origin", "https://ecourtier-frontend.onrender.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type, X-IA-Session-Token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://ecourtier-frontend.onrender.com"))
                .andExpect(header().exists("Access-Control-Allow-Headers"))
                .andReturn();
        String allowHeaders = preflight.getResponse().getHeader("Access-Control-Allow-Headers");
        assertNotNull(allowHeaders);
        assertTrue(allowHeaders.toLowerCase().contains("x-ia-session-token"),
                "CORS doit autoriser X-IA-Session-Token (reçu: " + allowHeaders + ")");
    }
}
