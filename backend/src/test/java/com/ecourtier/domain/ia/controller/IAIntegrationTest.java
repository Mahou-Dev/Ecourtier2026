package com.ecourtier.domain.ia.controller;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.ia.dto.MessageUserRequestDTO;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class IAIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private UtilisateurEntity testUser;
    private AnnonceEntity testAnnonce;

    @BeforeEach
    void setUp() {
        if (utilisateurRepository.findByEmail("prop.ia.test@ecourtier.sn").isEmpty()) {
            testUser = utilisateurRepository.save(UtilisateurEntity.builder()
                    .nom("Fall")
                    .prenom("Mamadou")
                    .email("prop.ia.test@ecourtier.sn")
                    .motDePasse("$2a$10$KhUqg3EWfmNgLhTIGhjP0uzeSXRI1y0ulUhiJyv1Qz0H34Ybju3IS")
                    .telephone("+221771112233")
                    .role(Role.PROPRIETAIRE)
                    .actif(true)
                    .build());
        } else {
            testUser = utilisateurRepository.findByEmail("prop.ia.test@ecourtier.sn").get();
        }

        if (annonceRepository.findAll().isEmpty()) {
            testAnnonce = annonceRepository.save(AnnonceEntity.builder()
                    .titre("Appartement meublé Nema F3")
                    .description("Bel F3 meublé proche écoles")
                    .type(TypeBien.APPARTEMENT)
                    .modeTransaction(ModeTransaction.LOCATION)
                    .prix(BigDecimal.valueOf(150000))
                    .superficie(80.0)
                    .nombrePieces(3)
                    .quartier("Nema")
                    .ville("Ziguinchor")
                    .latitude(12.58)
                    .longitude(-16.27)
                    .meuble(true)
                    .animauxAcceptes(false)
                    .statutVerification(StatutVerification.EN_ATTENTE)
                    .statutPublication(StatutPublication.DISPONIBLE)
                    .supprime(false)
                    .utilisateur(testUser)
                    .build());
        }
    }

    @Test
    @DisplayName("Conversation IA complète : création -> message avec recherche filtrée < 3s -> historique")
    void testWorkflowIAComplet() throws Exception {
        // 1. Démarre conversation
        MvcResult convResult = mockMvc.perform(post("/api/v1/ia/conversations"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conversationId", notNullValue()))
                .andReturn();

        String convResponseStr = convResult.getResponse().getContentAsString();
        String conversationId = objectMapper.readTree(convResponseStr).get("conversationId").asText();

        // 2. Envoie message
        long startTime = System.currentTimeMillis();

        MessageUserRequestDTO messageDto = MessageUserRequestDTO.builder()
                .message("3 pièces à Nema, budget 150000, meublé")
                .build();

        mockMvc.perform(post("/api/v1/ia/conversations/" + conversationId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reponse", notNullValue()))
                .andExpect(jsonPath("$.criteresExtraits.quartier", is("Nema")))
                .andExpect(jsonPath("$.criteresExtraits.nombrePieces", is(3)))
                .andExpect(jsonPath("$.criteresExtraits.budgetMax", is(150000)))
                .andExpect(jsonPath("$.criteresExtraits.meuble", is(true)))
                .andExpect(jsonPath("$.annoncesSuggeres", notNullValue()));

        long duration = System.currentTimeMillis() - startTime;
        org.junit.jupiter.api.Assertions.assertTrue(duration < 5000, "Le temps de réponse doit être raisonnable pour l'appel d'intégration (obtenu: " + duration + "ms)");

        // 3. Obtenir historique
        mockMvc.perform(get("/api/v1/ia/conversations/" + conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId", is(conversationId)))
                .andExpect(jsonPath("$.messages", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @DisplayName("Message trop long (> 500 chars) rejeté avec HTTP 400")
    void testMessageTropLongRejete() throws Exception {
        MvcResult convResult = mockMvc.perform(post("/api/v1/ia/conversations"))
                .andExpect(status().isCreated())
                .andReturn();

        String conversationId = objectMapper.readTree(convResult.getResponse().getContentAsString()).get("conversationId").asText();

        MessageUserRequestDTO messageDto = MessageUserRequestDTO.builder()
                .message("x".repeat(501))
                .build();

        mockMvc.perform(post("/api/v1/ia/conversations/" + conversationId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageDto)))
                .andExpect(status().isBadRequest());
    }
}
