package com.ecourtier.domain.messagerie.controller;

import com.ecourtier.common.security.JwtService;
import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.messagerie.dto.ConversationResponseDTO;
import com.ecourtier.domain.messagerie.repository.ConversationChatRepository;
import com.ecourtier.domain.messagerie.repository.MessageChatRepository;
import com.ecourtier.domain.messagerie.service.MessagerieService;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessagerieRestControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessagerieService messagerieService;

    @Autowired
    private MessageChatRepository messageChatRepository;

    @Autowired
    private ConversationChatRepository conversationChatRepository;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private JwtService jwtService;

    private UtilisateurEntity proprietaire;
    private UtilisateurEntity locataire;
    private AnnonceEntity annonce;
    private String locataireToken;
    private String proprietaireToken;

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        messageChatRepository.deleteAll();
        conversationChatRepository.deleteAll();
        annonceRepository.deleteAll();
        utilisateurRepository.deleteAll();
    }

    @BeforeEach
    void setUp() {
        messageChatRepository.deleteAll();
        conversationChatRepository.deleteAll();
        annonceRepository.deleteAll();
        utilisateurRepository.deleteAll();

        proprietaire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Diop")
                .prenom("Moussa")
                .email("moussa.prop@ecourtier.sn")
                .motDePasse("Pass1234!")
                .telephone("+221771110001")
                .role(Role.PROPRIETAIRE)
                .actif(true)
                .build());

        locataire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Fall")
                .prenom("Aida")
                .email("aida.loc@ecourtier.sn")
                .motDePasse("Pass1234!")
                .telephone("+221771110002")
                .role(Role.LOCATAIRE)
                .actif(true)
                .build());

        annonce = annonceRepository.save(AnnonceEntity.builder()
                .titre("Appartement Ngor Standing")
                .description("Vue imprenable sur l'île")
                .prix(BigDecimal.valueOf(350000))
                .superficie(80.0)
                .nombrePieces(3)
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .statutPublication(StatutPublication.DISPONIBLE)
                .ville("Dakar")
                .quartier("Ngor")
                .latitude(14.7523)
                .longitude(-17.5186)
                .utilisateur(proprietaire)
                .build());

        locataireToken = jwtService.generateAccessToken(locataire);
        proprietaireToken = jwtService.generateAccessToken(proprietaire);
    }

    @Test
    @DisplayName("Workflow complet messagerie : Initier -> Lister conversations -> Envoyer message -> Compter non lus -> Marquer comme lu")
    void testWorkflowMessagerieComplet() throws Exception {
        // 1. Locataire initie une conversation sur l'annonce
        MvcResult initResult = mockMvc.perform(post("/api/v1/annonces/{id}/conversations", annonce.getId())
                        .header("Authorization", "Bearer " + locataireToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.annonceTitre").value("Appartement Ngor Standing"))
                .andReturn();

        ConversationResponseDTO convDto = objectMapper.readValue(
                initResult.getResponse().getContentAsString(), ConversationResponseDTO.class);
        UUID conversationId = convDto.getId();

        // 2. Locataire liste ses conversations
        mockMvc.perform(get("/api/v1/conversations/mes-conversations")
                        .header("Authorization", "Bearer " + locataireToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(conversationId.toString()));

        // 3. Propriétaire liste aussi ses conversations
        mockMvc.perform(get("/api/v1/conversations/mes-conversations")
                        .header("Authorization", "Bearer " + proprietaireToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(conversationId.toString()));

        // 4. Obtenir les métadonnées d'une conversation par ID
        mockMvc.perform(get("/api/v1/conversations/{id}", conversationId)
                        .header("Authorization", "Bearer " + locataireToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conversationId.toString()));

        // 5. Envoyer un message du locataire au propriétaire
        messagerieService.envoyerMessage(conversationId, "Bonjour, le bien est-il disponible ?", locataire.getEmail());

        // 6. Propriétaire vérifie son compteur de messages non lus
        mockMvc.perform(get("/api/v1/conversations/non-lus/count")
                        .header("Authorization", "Bearer " + proprietaireToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNonLus").value(1));

        // 7. Propriétaire consulte les messages paginés
        mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId)
                        .header("Authorization", "Bearer " + proprietaireToken)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].contenu").value("Bonjour, le bien est-il disponible ?"))
                .andExpect(jsonPath("$.content[0].lu").value(false));

        // 8. Propriétaire consulte tous les messages
        mockMvc.perform(get("/api/v1/conversations/{id}/messages/tous", conversationId)
                        .header("Authorization", "Bearer " + proprietaireToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].contenu").value("Bonjour, le bien est-il disponible ?"));

        // 9. Propriétaire marque les messages comme lus
        mockMvc.perform(put("/api/v1/conversations/{id}/marquer-lu", conversationId)
                        .header("Authorization", "Bearer " + proprietaireToken))
                .andExpect(status().isNoContent());

        // 10. Propriétaire revérifie son compteur -> 0 non lus
        mockMvc.perform(get("/api/v1/conversations/non-lus/count")
                        .header("Authorization", "Bearer " + proprietaireToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNonLus").value(0));
    }
}
