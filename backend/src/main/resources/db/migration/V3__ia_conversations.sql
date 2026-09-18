-- Migration V3: Schema for AI Chatbot Assistant Conversations and Messages

CREATE TABLE conversations_ia (
    id UUID PRIMARY KEY,
    utilisateur_id UUID REFERENCES utilisateurs(id) ON DELETE SET NULL,
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_conversations_ia_utilisateur ON conversations_ia(utilisateur_id);

CREATE TABLE messages_ia (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations_ia(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('UTILISATEUR', 'ASSISTANT')),
    contenu TEXT NOT NULL,
    criteres_extraits TEXT,
    date_envoi TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_messages_ia_conversation ON messages_ia(conversation_id);
