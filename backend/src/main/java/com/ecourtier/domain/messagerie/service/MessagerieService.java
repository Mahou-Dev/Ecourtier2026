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
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MessagerieService {

    private final ConversationChatRepository conversationChatRepository;
    private final MessageChatRepository messageChatRepository;
    private final AnnonceRepository annonceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final MessagerieMapper messagerieMapper;
    private final ConversationSecurityService conversationSecurityService;

    private static final Pattern SCRIPT_PATTERN = Pattern.compile("(?i)<script.*?>.*?</script.*?>|<script.*?>|javascript:|onload=");

    @Transactional
    public ConversationResponseDTO initierConversation(UUID annonceId, String locataireEmail) {
        UtilisateurEntity locataire = utilisateurRepository.findByEmail(locataireEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur locataire non trouvé"));

        AnnonceEntity annonce = annonceRepository.findById(annonceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Annonce non trouvée"));

        UtilisateurEntity proprietaire = annonce.getUtilisateur();
        if (proprietaire == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'annonce n'a pas de propriétaire associé");
        }

        if (locataire.getId().equals(proprietaire.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vous êtes le propriétaire de cette annonce");
        }

        // Check if conversation already exists (idempotency)
        Optional<ConversationChatEntity> existing = conversationChatRepository.findByAnnonceIdAndLocataireId(annonceId, locataire.getId());
        if (existing.isPresent()) {
            return mapToConversationDTO(existing.get(), locataire.getId());
        }

        try {
            ConversationChatEntity conversation = ConversationChatEntity.builder()
                    .annonce(annonce)
                    .locataire(locataire)
                    .proprietaire(proprietaire)
                    .dateCreation(LocalDateTime.now())
                    .build();

            ConversationChatEntity saved = conversationChatRepository.save(conversation);
            return mapToConversationDTO(saved, locataire.getId());
        } catch (DataIntegrityViolationException e) {
            // Handle race condition cleanly
            ConversationChatEntity duplicate = conversationChatRepository.findByAnnonceIdAndLocataireId(annonceId, locataire.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur lors de la création de la conversation"));
            return mapToConversationDTO(duplicate, locataire.getId());
        }
    }

    @Transactional
    public MessageResponseDTO envoyerMessage(UUID conversationId, String contenuRaw, String expediteurEmail) {
        if (contenuRaw == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le contenu du message est requis");
        }

        String contenu = contenuRaw.trim();
        if (contenu.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le contenu du message ne peut pas être vide");
        }
        if (contenu.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le contenu du message ne peut pas dépasser 1000 caractères");
        }
        if (SCRIPT_PATTERN.matcher(contenu).find()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le contenu du message contient du code balisé non autorisé");
        }

        if (!conversationSecurityService.isParticipant(conversationId, expediteurEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé : vous n'êtes pas participant de cette conversation");
        }

        UtilisateurEntity expediteur = utilisateurRepository.findByEmail(expediteurEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expéditeur non trouvé"));

        ConversationChatEntity conversation = conversationChatRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation non trouvée"));

        MessageChatEntity message = MessageChatEntity.builder()
                .conversation(conversation)
                .expediteur(expediteur)
                .contenu(contenu)
                .dateEnvoi(LocalDateTime.now())
                .lu(false)
                .build();

        MessageChatEntity saved = messageChatRepository.save(message);
        return messagerieMapper.toMessageResponseDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponseDTO> listerMesConversations(String userEmail) {
        UtilisateurEntity user = utilisateurRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur non trouvé"));

        List<ConversationChatEntity> conversations = conversationChatRepository.findAllByUserId(user.getId());
        return conversations.stream()
                .map(c -> mapToConversationDTO(c, user.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<MessageResponseDTO> listerMessages(UUID conversationId, Pageable pageable, String userEmail) {
        if (!conversationSecurityService.isParticipant(conversationId, userEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé à cette conversation");
        }

        return messageChatRepository.findByConversationIdOrderByDateEnvoiAsc(conversationId, pageable)
                .map(messagerieMapper::toMessageResponseDTO);
    }

    @Transactional(readOnly = true)
    public List<MessageResponseDTO> listerTousLesMessages(UUID conversationId, String userEmail) {
        if (!conversationSecurityService.isParticipant(conversationId, userEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé à cette conversation");
        }

        return messageChatRepository.findByConversationIdOrderByDateEnvoiAsc(conversationId)
                .stream()
                .map(messagerieMapper::toMessageResponseDTO)
                .toList();
    }

    @Transactional
    public void marquerCommeLu(UUID conversationId, String userEmail) {
        if (!conversationSecurityService.isParticipant(conversationId, userEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé à cette conversation");
        }

        UtilisateurEntity user = utilisateurRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur non trouvé"));

        messageChatRepository.markMessagesAsRead(conversationId, user.getId());
    }

    @Transactional(readOnly = true)
    public long compterMessagesNonLus(String userEmail) {
        UtilisateurEntity user = utilisateurRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur non trouvé"));

        return messageChatRepository.countTotalUnreadMessagesForUser(user.getId());
    }

    @Transactional(readOnly = true)
    public ConversationResponseDTO obtenirConversation(UUID conversationId, String userEmail) {
        if (!conversationSecurityService.isParticipant(conversationId, userEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé à cette conversation");
        }

        ConversationChatEntity conversation = conversationChatRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation non trouvée"));

        UtilisateurEntity user = utilisateurRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur non trouvé"));

        return mapToConversationDTO(conversation, user.getId());
    }

    private ConversationResponseDTO mapToConversationDTO(ConversationChatEntity c, UUID currentUserId) {
        MessageChatEntity dernierMessage = messageChatRepository.findFirstByConversationIdOrderByDateEnvoiDesc(c.getId()).orElse(null);
        long unreadCount = messageChatRepository.countUnreadMessagesForUserInConversation(c.getId(), currentUserId);
        return messagerieMapper.toConversationResponseDTO(c, dernierMessage, unreadCount);
    }
}
