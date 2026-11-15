package com.ecourtier.domain.ia.service;

import com.ecourtier.domain.ia.dto.ConversationCreateResponseDTO;
import com.ecourtier.domain.ia.dto.MessageUserRequestDTO;
import com.ecourtier.domain.ia.entity.ConversationIAEntity;
import com.ecourtier.domain.ia.entity.MessageIAEntity;
import com.ecourtier.domain.ia.repository.ConversationIARepository;
import com.ecourtier.domain.ia.repository.MessageIARepository;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IAServiceClockTest {

    private static final ZoneId DAKAR_ZONE = ZoneId.of("Africa/Dakar");

    @Autowired
    private IAService iaService;

    @Autowired
    private ConversationIARepository conversationIARepository;

    @Autowired
    private MessageIARepository messageIARepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private TimeZone previousTimeZone;
    private UtilisateurEntity user;

    @BeforeEach
    void setUp() {
        previousTimeZone = TimeZone.getDefault();
        messageIARepository.deleteAll();
        conversationIARepository.deleteAll();
        utilisateurRepository.deleteAll();

        user = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Ndiaye")
                .prenom("Aissatou")
                .email("aissatou.clock.ia@test.sn")
                .motDePasse("Password123!")
                .telephone("+221771234567")
                .role(Role.LOCATAIRE)
                .actif(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        iaService.setClock(Clock.system(DAKAR_ZONE));
        TimeZone.setDefault(previousTimeZone);
    }

    @Test
    @DisplayName("E1 : ConversationIAEntity et MessageIAEntity utilisent l'horloge Africa/Dakar même sous TZ Europe/Paris")
    void testConversationEtMessagesUtilisentHorlogeDakarSousTzParis() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"));

        // Juin : Africa/Dakar = 23:30 le 15 juin, Europe/Paris = 01:30 le 16 juin
        Instant instantFixe = Instant.parse("2026-06-15T23:30:00Z");
        iaService.setClock(Clock.fixed(instantFixe, DAKAR_ZONE));

        ConversationCreateResponseDTO created = iaService.creerConversation(user.getEmail());
        iaService.envoyerMessage(
                created.getConversationId(),
                new MessageUserRequestDTO("Je cherche une villa à louer à Dakar"),
                user.getEmail()
        );

        LocalDateTime expectedDakarTime = LocalDateTime.of(2026, 6, 15, 23, 30, 0);

        ConversationIAEntity conv = conversationIARepository.findById(created.getConversationId()).orElseThrow();
        assertEquals(expectedDakarTime, conv.getDateCreation(),
                "Hibernate ne doit pas remplacer dateCreation par @CreationTimestamp ou la TZ JVM");

        List<MessageIAEntity> messages = messageIARepository.findByConversationIdOrderByDateEnvoiAsc(conv.getId());
        assertEquals(2, messages.size());
        assertEquals(expectedDakarTime, messages.get(0).getDateEnvoi(),
                "dateEnvoi du message utilisateur doit suivre l'horloge Africa/Dakar");
        assertEquals(expectedDakarTime, messages.get(1).getDateEnvoi(),
                "dateEnvoi du message assistant doit suivre l'horloge Africa/Dakar");
    }

    @Test
    @DisplayName("E1 : L'expiration de session anonyme IA est calculée et évaluée en heure Africa/Dakar sous TZ Europe/Paris")
    void testExpirationSessionAnonymeAligneeDakarSousTzParis() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"));

        Instant creationInstant = Instant.parse("2026-06-15T23:30:00Z");
        iaService.setClock(Clock.fixed(creationInstant, DAKAR_ZONE));

        String rawToken = IAService.generateSecureToken();
        ConversationCreateResponseDTO created = iaService.creerConversation(null, rawToken);

        ConversationIAEntity conv = conversationIARepository.findById(created.getConversationId()).orElseThrow();
        LocalDateTime expectedCreation = LocalDateTime.of(2026, 6, 15, 23, 30, 0);
        LocalDateTime expectedExpiration = expectedCreation.plusDays(7);
        assertEquals(expectedCreation, conv.getDateCreation());
        assertEquals(expectedExpiration, conv.getSessionExpiration());

        // À 2026-06-22T22:00:00Z : Paris = 2026-06-23 00:00 (après 23:30 le 22 en apparence), mais Dakar = 22:00 (< 23:30)
        iaService.setClock(Clock.fixed(Instant.parse("2026-06-22T22:00:00Z"), DAKAR_ZONE));
        assertNotNull(iaService.envoyerMessage(
                conv.getId(),
                new MessageUserRequestDTO("Appartement à louer à Almadies"),
                null,
                rawToken
        ));

        // À 2026-06-22T23:31:00Z : Dakar = 23:31 > sessionExpiration -> 403 Forbidden
        iaService.setClock(Clock.fixed(Instant.parse("2026-06-22T23:31:00Z"), DAKAR_ZONE));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                iaService.envoyerMessage(
                        conv.getId(),
                        new MessageUserRequestDTO("Encore disponible ?"),
                        null,
                        rawToken
                )
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("E1 : En horloge système Africa/Dakar sous TZ Europe/Paris, les timestamps IA restent calés sur Dakar")
    void testHorlogeLiveDakarSousTzParis() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"));
        iaService.setClock(Clock.system(DAKAR_ZONE));

        ConversationCreateResponseDTO created = iaService.creerConversation(user.getEmail());
        iaService.envoyerMessage(
                created.getConversationId(),
                new MessageUserRequestDTO("Studio meublé à Plateau"),
                user.getEmail()
        );

        LocalDateTime dakarNow = LocalDateTime.now(DAKAR_ZONE);
        ConversationIAEntity conv = conversationIARepository.findById(created.getConversationId()).orElseThrow();
        List<MessageIAEntity> messages = messageIARepository.findByConversationIdOrderByDateEnvoiAsc(conv.getId());

        long convDrift = Math.abs(Duration.between(conv.getDateCreation(), dakarNow).toSeconds());
        long msgDrift = Math.abs(Duration.between(messages.get(0).getDateEnvoi(), dakarNow).toSeconds());
        assertTrue(convDrift < 15, "dateCreation doit rester en Africa/Dakar (drift=" + convDrift + "s)");
        assertTrue(msgDrift < 15, "dateEnvoi doit rester en Africa/Dakar (drift=" + msgDrift + "s)");
    }
}
