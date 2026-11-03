package com.ecourtier.domain.messagerie.entity;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MessagerieEntityTest {

    @Test
    @DisplayName("ConversationChatEntity : cycle de vie onCreate et accesseurs")
    void testConversationChatEntity() {
        ConversationChatEntity conv = new ConversationChatEntity();
        assertNull(conv.getDateCreation());
        conv.onCreate();
        assertNotNull(conv.getDateCreation());

        LocalDateTime specificDate = LocalDateTime.of(2026, 1, 1, 10, 0);
        conv.setDateCreation(specificDate);
        conv.onCreate();
        assertEquals(specificDate, conv.getDateCreation());

        UUID id = UUID.randomUUID();
        AnnonceEntity annonce = new AnnonceEntity();
        UtilisateurEntity loc = new UtilisateurEntity();
        UtilisateurEntity prop = new UtilisateurEntity();

        ConversationChatEntity allArgs = new ConversationChatEntity(id, annonce, loc, prop, specificDate);
        assertEquals(id, allArgs.getId());
        assertEquals(annonce, allArgs.getAnnonce());
        assertEquals(loc, allArgs.getLocataire());
        assertEquals(prop, allArgs.getProprietaire());
        assertEquals(specificDate, allArgs.getDateCreation());

        ConversationChatEntity built = ConversationChatEntity.builder()
                .id(id)
                .annonce(annonce)
                .locataire(loc)
                .proprietaire(prop)
                .dateCreation(specificDate)
                .build();
        assertNotNull(built);
    }

    @Test
    @DisplayName("MessageChatEntity : cycle de vie onCreate et accesseurs")
    void testMessageChatEntity() {
        MessageChatEntity msg = new MessageChatEntity();
        assertNull(msg.getDateEnvoi());
        msg.onCreate();
        assertNotNull(msg.getDateEnvoi());

        LocalDateTime specificDate = LocalDateTime.of(2026, 1, 1, 12, 0);
        msg.setDateEnvoi(specificDate);
        msg.onCreate();
        assertEquals(specificDate, msg.getDateEnvoi());

        UUID id = UUID.randomUUID();
        ConversationChatEntity conv = new ConversationChatEntity();
        UtilisateurEntity exp = new UtilisateurEntity();

        MessageChatEntity allArgs = new MessageChatEntity(id, conv, exp, "Hello", specificDate, true);
        assertEquals(id, allArgs.getId());
        assertEquals(conv, allArgs.getConversation());
        assertEquals(exp, allArgs.getExpediteur());
        assertEquals("Hello", allArgs.getContenu());
        assertEquals(specificDate, allArgs.getDateEnvoi());
        assertTrue(allArgs.isLu());

        MessageChatEntity built = MessageChatEntity.builder()
                .id(id)
                .conversation(conv)
                .expediteur(exp)
                .contenu("Hello")
                .dateEnvoi(specificDate)
                .lu(false)
                .build();
        assertNotNull(built);
    }
}
