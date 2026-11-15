-- Migration V17: Sécurisation des conversations IA anonymes par preuve de possession et expiration

ALTER TABLE conversations_ia
    ADD COLUMN IF NOT EXISTS session_token_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS session_expiration TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_conversations_ia_session_token_hash ON conversations_ia(session_token_hash);
