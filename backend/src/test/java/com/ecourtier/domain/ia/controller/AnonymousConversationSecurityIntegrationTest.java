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
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
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

    private UtilisateurEntity utilisateurA;
    private UtilisateurEntity utilisateurB;

    @BeforeEach
    void setUp() {
        iaRateLimiterService.clear();
        messageIARepository.deleteAll();
        conversationIARepository.deleteAll();
        utilisateurRepository.deleteAll();

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
    @DisplayName("1. Création d'une conversation anonyme : délivrance du cookie HttpOnly de possession et hash en base")
    void test01_CreationConversationAnonyme_DelivrePreuvePossession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/ia/conversations"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conversationId").exists())
                .andReturn();

        // 1. Vérifier la présence du cookie HttpOnly
        Cookie sessionCookie = result.getResponse().getCookie(IAService.IA_SESSION_COOKIE_NAME);
        assertNotNull(sessionCookie, "Le cookie 'ecourtier_ia_session' doit être émis");
        assertTrue(sessionCookie.isHttpOnly(), "Le cookie de session IA doit obligatoirement être HttpOnly");
        assertEquals("/api/v1/ia", sessionCookie.getPath(), "Le chemin du cookie doit être restreint à /api/v1/ia");
        assertNotNull(sessionCookie.getValue(), "La valeur du jeton de session ne doit pas être vide");
        assertFalse(sessionCookie.getValue().isBlank());

        // 2. Vérifier l'entité en base
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID convId = UUID.fromString(jsonNode.get("conversationId").asText());

        ConversationIAEntity conv = conversationIARepository.findById(convId).orElseThrow();
        assertNull(conv.getUtilisateur(), "L'utilisateur doit être null pour une conversation anonyme");
        assertNotNull(conv.getSessionTokenHash(), "Le hash du jeton doit être enregistré en base");
        assertEquals(IAService.hashToken(sessionCookie.getValue()), conv.getSessionTokenHash(), "Le hash en base doit correspondre au SHA-256 du jeton du cookie");
        assertNotNull(conv.getSessionExpiration(), "Une date d'expiration doit être définie");
        assertTrue(conv.getSessionExpiration().isAfter(LocalDateTime.now()), "L'expiration doit être dans le futur");
    }

    @Test
    @DisplayName("2. Lecture de l'historique d'une conversation anonyme avec cookie de session valide : autorisée (200 OK)")
    void test02_LectureAvecPreuveValide_Autorisee() throws Exception {
        // Création
        MvcResult createResult = mockMvc.perform(post("/api/v1/ia/conversations"))
                .andExpect(status().isCreated())
                .andReturn();

        Cookie cookie = createResult.getResponse().getCookie(IAService.IA_SESSION_COOKIE_NAME);
        UUID convId = UUID.fromString(objectMapper.readTree(createResult.getResponse().getContentAsString()).get("conversationId").asText());

        // Lecture avec preuve
        mockMvc.perform(get("/api/v1/ia/conversations/" + convId)
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(convId.toString()));
    }

    @Test
    @DisplayName("3. Lecture de l'historique sans preuve de possession : refusée (403 Forbidden)")
    void test03_LectureSansPreuve_Refusee() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/ia/conversations"))
                .andExpect(status().isCreated())
                .andReturn();

        UUID convId = UUID.fromString(objectMapper.readTree(createResult.getResponse().getContentAsString()).get("conversationId").asText());

        // Requête sans cookie ni header
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

        Cookie wrongCookie = new Cookie(IAService.IA_SESSION_COOKIE_NAME, "jeton_frauduleux_inexistant_1234567890abcdef");

        mockMvc.perform(get("/api/v1/ia/conversations/" + convId)
                        .cookie(wrongCookie))
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
    @DisplayName("6. Ajout d'un message avec preuve de possession valide : autorisé (200 OK)")
    void test06_AjoutMessageAvecPreuveValide_Autorise() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/ia/conversations"))
                .andExpect(status().isCreated())
                .andReturn();

        Cookie cookie = createResult.getResponse().getCookie(IAService.IA_SESSION_COOKIE_NAME);
        UUID convId = UUID.fromString(objectMapper.readTree(createResult.getResponse().getContentAsString()).get("conversationId").asText());

        mockMvc.perform(post("/api/v1/ia/conversations/" + convId + "/messages")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MessageUserRequestDTO("Je cherche une chambre à Dakar"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reponse").exists());
    }

    @Test
    @DisplayName("7. UUID seul insuffisant : deux sessions anonymes distinctes ne peuvent pas accéder aux conversations de l'autre")
    void test07_UuidSeulInsuffisant_IsolationSessionsAnonymes() throws Exception {
        // Session 1
        MvcResult res1 = mockMvc.perform(post("/api/v1/ia/conversations"))
                .andExpect(status().isCreated())
                .andReturn();
        Cookie cookie1 = res1.getResponse().getCookie(IAService.IA_SESSION_COOKIE_NAME);
        UUID convId1 = UUID.fromString(objectMapper.readTree(res1.getResponse().getContentAsString()).get("conversationId").asText());

        // Session 2
        MvcResult res2 = mockMvc.perform(post("/api/v1/ia/conversations"))
                .andExpect(status().isCreated())
                .andReturn();
        Cookie cookie2 = res2.getResponse().getCookie(IAService.IA_SESSION_COOKIE_NAME);
        UUID convId2 = UUID.fromString(objectMapper.readTree(res2.getResponse().getContentAsString()).get("conversationId").asText());

        // Session 2 tente d'accéder à la conversation 1 avec le cookie de session 2 -> 403
        mockMvc.perform(get("/api/v1/ia/conversations/" + convId1)
                        .cookie(cookie2))
                .andExpect(status().isForbidden());

        // Session 1 tente d'accéder à la conversation 2 avec le cookie de session 1 -> 403
        mockMvc.perform(get("/api/v1/ia/conversations/" + convId2)
                        .cookie(cookie1))
                .andExpect(status().isForbidden());

        // Chaque session accède normalement à sa propre conversation
        mockMvc.perform(get("/api/v1/ia/conversations/" + convId1).cookie(cookie1)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/ia/conversations/" + convId2).cookie(cookie2)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("8. Conversation authentifiée : seul son propriétaire peut y accéder")
    @WithMockUser(username = "mamadou.diallo@test.sn", roles = {"LOCATAIRE"})
    void test08_ConversationAuthentifiee_ProprietaireAutorise() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/ia/conversations"))
                .andExpect(status().isCreated())
                .andReturn();

        UUID convId = UUID.fromString(objectMapper.readTree(createResult.getResponse().getContentAsString()).get("conversationId").asText());

        // Mamadou accède à sa propre conversation
        mockMvc.perform(get("/api/v1/ia/conversations/" + convId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(convId.toString()));
    }

    @Test
    @DisplayName("9. Utilisateur B tentant d'accéder à la conversation authentifiée de A : refusé (403 Forbidden)")
    @WithMockUser(username = "fatou.sow@test.sn", roles = {"LOCATAIRE"})
    void test09_UtilisateurBTentantAccesConversationA_Refuse() throws Exception {
        // Conversation créée pour Mamadou
        ConversationIAEntity convA = conversationIARepository.save(ConversationIAEntity.builder()
                .utilisateur(utilisateurA)
                .dateCreation(LocalDateTime.now())
                .build());

        // Fatou tente de lire la conversation de Mamadou
        mockMvc.perform(get("/api/v1/ia/conversations/" + convA.getId()))
                .andExpect(status().isForbidden());

        // Fatou tente d'envoyer un message sur la conversation de Mamadou
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

        // Anonyme sans cookie
        mockMvc.perform(get("/api/v1/ia/conversations/" + convA.getId()))
                .andExpect(status().isForbidden());

        // Anonyme avec un cookie de session anonyme valide
        Cookie cookieAnon = new Cookie(IAService.IA_SESSION_COOKIE_NAME, "random_session_anon_token_12345");
        mockMvc.perform(get("/api/v1/ia/conversations/" + convA.getId())
                        .cookie(cookieAnon))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("11. Expiration de la session anonyme : accès refusé après expiration (403 Forbidden)")
    void test11_ExpirationPreuve_Refuse() throws Exception {
        String token = IAService.generateSecureToken();
        String hash = IAService.hashToken(token);

        // Conversation anonyme expirée depuis 1 heure
        ConversationIAEntity convExpiree = conversationIARepository.save(ConversationIAEntity.builder()
                .sessionTokenHash(hash)
                .sessionExpiration(LocalDateTime.now().minusHours(1))
                .dateCreation(LocalDateTime.now().minusDays(8))
                .build());

        Cookie cookie = new Cookie(IAService.IA_SESSION_COOKIE_NAME, token);

        mockMvc.perform(get("/api/v1/ia/conversations/" + convExpiree.getId())
                        .cookie(cookie))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("12. Aucune fuite du secret ou du hash dans les réponses API ou corps JSON")
    void test12_AucuneFuiteDuSecretDansReponsesApi() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/ia/conversations"))
                .andExpect(status().isCreated())
                .andReturn();

        String rawToken = createResult.getResponse().getCookie(IAService.IA_SESSION_COOKIE_NAME).getValue();
        String jsonCreate = createResult.getResponse().getContentAsString();

        // Le token brut ne doit jamais figurer dans le body JSON
        assertFalse(jsonCreate.contains(rawToken), "Le jeton secret brut ne doit jamais être présent dans le corps de réponse");
        assertFalse(jsonCreate.contains("sessionToken"), "Aucun champ de jeton ne doit fuiter");
        assertFalse(jsonCreate.contains("sessionTokenHash"), "Le hash ne doit pas figurer dans le body");

        UUID convId = UUID.fromString(objectMapper.readTree(jsonCreate).get("conversationId").asText());

        // Vérifier l'historique
        MvcResult histResult = mockMvc.perform(get("/api/v1/ia/conversations/" + convId)
                        .cookie(createResult.getResponse().getCookie(IAService.IA_SESSION_COOKIE_NAME)))
                .andExpect(status().isOk())
                .andReturn();

        String jsonHist = histResult.getResponse().getContentAsString();
        assertFalse(jsonHist.contains(rawToken), "L'historique ne doit pas contenir le jeton secret");
        assertFalse(jsonHist.contains("sessionTokenHash"), "L'historique ne doit pas exposer le hash");
    }

    @Test
    @DisplayName("13. Anciennes conversations anonymes sans preuve de possession : accès refusé par défaut (403 Forbidden)")
    void test13_AnciennesConversationsAnonymesSansPreuve_Refusees() throws Exception {
        // Conversation héritée sans hash ni date d'expiration
        ConversationIAEntity convAncienne = conversationIARepository.save(ConversationIAEntity.builder()
                .sessionTokenHash(null)
                .sessionExpiration(null)
                .dateCreation(LocalDateTime.now().minusDays(30))
                .build());

        // Tout accès est refusé pour empêcher l'exploitation des données antérieures non sécurisées
        mockMvc.perform(get("/api/v1/ia/conversations/" + convAncienne.getId()))
                .andExpect(status().isForbidden());

        Cookie anyCookie = new Cookie(IAService.IA_SESSION_COOKIE_NAME, "any_token");
        mockMvc.perform(get("/api/v1/ia/conversations/" + convAncienne.getId())
                        .cookie(anyCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("14. Support alternatif de l'en-tête X-IA-Session-Token pour les clients programmatiques")
    void test14_SupportHeaderAlternatif_Autorise() throws Exception {
        String token = IAService.generateSecureToken();
        String hash = IAService.hashToken(token);

        ConversationIAEntity conv = conversationIARepository.save(ConversationIAEntity.builder()
                .sessionTokenHash(hash)
                .sessionExpiration(LocalDateTime.now().plusDays(7))
                .dateCreation(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/v1/ia/conversations/" + conv.getId())
                        .header(IAService.IA_SESSION_HEADER_NAME, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(conv.getId().toString()));
    }
}
