CREATE TABLE IF NOT EXISTS conversations_chat (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    annonce_id UUID NOT NULL REFERENCES annonces(id) ON DELETE CASCADE,
    locataire_id UUID NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    proprietaire_id UUID NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_conversation_annonce_locataire UNIQUE (annonce_id, locataire_id)
);

CREATE TABLE IF NOT EXISTS messages_chat (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations_chat(id) ON DELETE CASCADE,
    expediteur_id UUID NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    contenu TEXT NOT NULL,
    date_envoi TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lu BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_conversations_locataire ON conversations_chat(locataire_id);
CREATE INDEX IF NOT EXISTS idx_conversations_proprietaire ON conversations_chat(proprietaire_id);
CREATE INDEX IF NOT EXISTS idx_conversations_annonce ON conversations_chat(annonce_id);
CREATE INDEX IF NOT EXISTS idx_messages_conversation ON messages_chat(conversation_id);
CREATE INDEX IF NOT EXISTS idx_messages_expediteur ON messages_chat(expediteur_id);
CREATE INDEX IF NOT EXISTS idx_messages_lu ON messages_chat(conversation_id, lu);
