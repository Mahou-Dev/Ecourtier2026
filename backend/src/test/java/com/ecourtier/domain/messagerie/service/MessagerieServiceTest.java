package com.ecourtier.domain.messagerie.service;

import com.ecourtier.common.security.ConversationSecurityService;
import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.messagerie.dto.ConversationResponseDTO;
import com.ecourtier.domain.messagerie.dto.MessageResponseDTO;
import com.ecourtier.domain.messagerie.entity.ConversationChatEntity;
import com.ecourtier.domain.messagerie.entity.MessageChatEntity;
import com.ecourtier.domain.messagerie.mapper.MessagerieMapper;
import com.ecourtier.domain.messagerie.repository.ConversationChatRepository;
import com.ecourtier.domain.messagerie.repository.MessageChatRepository;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessagerieServiceTest {

    @Mock
    private ConversationChatRepository conversationChatRepository;
    @Mock
    private MessageChatRepository messageChatRepository;
    @Mock
    private AnnonceRepository annonceRepository;
    @Mock
    private UtilisateurRepository utilisateurRepository;
    @Mock
    private MessagerieMapper messagerieMapper;
    @Mock
    private ConversationSecurityService conversationSecurityService;

    @InjectMocks
    private MessagerieService messagerieService;

    private UtilisateurEntity locataire;
    private UtilisateurEntity proprietaire;
    private UtilisateurEntity tiers;
    private AnnonceEntity annonce;
    private ConversationChatEntity conversation;

    private UUID annonceId;
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        annonceId = UUID.randomUUID();
        conversationId = UUID.randomUUID();

        locataire = UtilisateurEntity.builder()
                .id(UUID.randomUUID())
                .email("locataire@test.sn")
                .nom("Diop")
                .prenom("Amina")
                .role(Role.LOCATAIRE)
                .build();

        proprietaire = UtilisateurEntity.builder()
                .id(UUID.randomUUID())
                .email("proprietaire@test.sn")
                .nom("Ndiaye")
                .prenom("Ousmane")
                .role(Role.PROPRIETAIRE)
                .build();

        tiers = UtilisateurEntity.builder()
                .id(UUID.randomUUID())
                .email("tiers@test.sn")
                .nom("Sall")
                .prenom("Mamadou")
                .role(Role.LOCATAIRE)
                .build();

        annonce = AnnonceEntity.builder()
                .id(annonceId)
                .titre("Appartement Fann Residence")
                .utilisateur(proprietaire)
                .build();

        conversation = ConversationChatEntity.builder()
                .id(conversationId)
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(proprietaire)
                .dateCreation(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Initier une conversation valide — création idempotent")
    void initierConversation_Success() {
        when(utilisateurRepository.findByEmail(locataire.getEmail())).thenReturn(Optional.of(locataire));
        when(annonceRepository.findById(annonceId)).thenReturn(Optional.of(annonce));
        when(conversationChatRepository.findByAnnonceIdAndLocataireId(annonceId, locataire.getId())).thenReturn(Optional.empty());
        when(conversationChatRepository.save(any(ConversationChatEntity.class))).thenReturn(conversation);

        ConversationResponseDTO expectedDto = ConversationResponseDTO.builder()
                .id(conversationId)
                .annonceId(annonceId)
                .build();
        when(messagerieMapper.toConversationResponseDTO(eq(conversation), any(), eq(0L))).thenReturn(expectedDto);

        ConversationResponseDTO result = messagerieService.initierConversation(annonceId, locataire.getEmail());

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(conversationId);
        verify(conversationChatRepository).save(any(ConversationChatEntity.class));
    }

    @Test
    @DisplayName("Initier une conversation — retourne l'existant si déjà initiée")
    void initierConversation_ReturnsExisting() {
        when(utilisateurRepository.findByEmail(locataire.getEmail())).thenReturn(Optional.of(locataire));
        when(annonceRepository.findById(annonceId)).thenReturn(Optional.of(annonce));
        when(conversationChatRepository.findByAnnonceIdAndLocataireId(annonceId, locataire.getId())).thenReturn(Optional.of(conversation));

        ConversationResponseDTO expectedDto = ConversationResponseDTO.builder().id(conversationId).build();
        when(messagerieMapper.toConversationResponseDTO(eq(conversation), any(), eq(0L))).thenReturn(expectedDto);

        ConversationResponseDTO result = messagerieService.initierConversation(annonceId, locataire.getEmail());

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(conversationId);
        verify(conversationChatRepository, never()).save(any());
    }

    @Test
    @DisplayName("Initier conversation par le propriétaire lui-même — rejet 400 Bad Request")
    void initierConversation_SelfContact_Rejected() {
        when(utilisateurRepository.findByEmail(proprietaire.getEmail())).thenReturn(Optional.of(proprietaire));
        when(annonceRepository.findById(annonceId)).thenReturn(Optional.of(annonce));

        assertThatThrownBy(() -> messagerieService.initierConversation(annonceId, proprietaire.getEmail()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Vous êtes le propriétaire de cette annonce");
    }

    @Test
    @DisplayName("Envoyer un message valide — succès")
    void envoyerMessage_Success() {
        String contenu = "Bonjour, le logement est-il toujours disponible ?";
        when(conversationSecurityService.isParticipant(conversationId, locataire.getEmail())).thenReturn(true);
        when(utilisateurRepository.findByEmail(locataire.getEmail())).thenReturn(Optional.of(locataire));
        when(conversationChatRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

        MessageChatEntity savedMessage = MessageChatEntity.builder()
                .id(UUID.randomUUID())
                .conversation(conversation)
                .expediteur(locataire)
                .contenu(contenu)
                .dateEnvoi(LocalDateTime.now())
                .lu(false)
                .build();
        when(messageChatRepository.save(any(MessageChatEntity.class))).thenReturn(savedMessage);

        MessageResponseDTO expectedResponse = MessageResponseDTO.builder()
                .id(savedMessage.getId())
                .contenu(contenu)
                .build();
        when(messagerieMapper.toMessageResponseDTO(savedMessage)).thenReturn(expectedResponse);

        MessageResponseDTO response = messagerieService.envoyerMessage(conversationId, contenu, locataire.getEmail());

        assertThat(response).isNotNull();
        assertThat(response.getContenu()).isEqualTo(contenu);
        verify(messageChatRepository).save(any(MessageChatEntity.class));
    }

    @Test
    @DisplayName("Envoyer un message vide ou espace — rejet 400 Bad Request")
    void envoyerMessage_Empty_Rejected() {
        assertThatThrownBy(() -> messagerieService.envoyerMessage(conversationId, "   ", locataire.getEmail()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("vide");
    }

    @Test
    @DisplayName("Envoyer un message > 1000 caractères — rejet 400 Bad Request")
    void envoyerMessage_TooLong_Rejected() {
        String longText = "A".repeat(1001);
        assertThatThrownBy(() -> messagerieService.envoyerMessage(conversationId, longText, locataire.getEmail()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("dépasser 1000 caractères");
    }

    @Test
    @DisplayName("Envoyer un message avec balises <script> — rejet sanitisation XSS")
    void envoyerMessage_ScriptTag_Rejected() {
        String scriptPayload = "Hello <script>alert('xss')</script>";
        assertThatThrownBy(() -> messagerieService.envoyerMessage(conversationId, scriptPayload, locataire.getEmail()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("code balisé non autorisé");
    }

    @Test
    @DisplayName("Envoyer un message par un tiers non participant — rejet 403 Forbidden")
    void envoyerMessage_NonParticipant_Forbidden() {
        when(conversationSecurityService.isParticipant(conversationId, tiers.getEmail())).thenReturn(false);

        assertThatThrownBy(() -> messagerieService.envoyerMessage(conversationId, "Message intrus", tiers.getEmail()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("n'êtes pas participant");
    }

    @Test
    @DisplayName("Marquer comme lu — appel repository")
    void marquerCommeLu_Success() {
        when(conversationSecurityService.isParticipant(conversationId, locataire.getEmail())).thenReturn(true);
        when(utilisateurRepository.findByEmail(locataire.getEmail())).thenReturn(Optional.of(locataire));

        messagerieService.marquerCommeLu(conversationId, locataire.getEmail());

        verify(messageChatRepository).markMessagesAsRead(conversationId, locataire.getId());
    }

    @Test
    @DisplayName("Compter les messages non lus d'un utilisateur — succès")
    void compterMessagesNonLus_Success() {
        when(utilisateurRepository.findByEmail(locataire.getEmail())).thenReturn(Optional.of(locataire));
        when(messageChatRepository.countTotalUnreadMessagesForUser(locataire.getId())).thenReturn(5L);

        long count = messagerieService.compterMessagesNonLus(locataire.getEmail());

        assertThat(count).isEqualTo(5L);
    }
}
