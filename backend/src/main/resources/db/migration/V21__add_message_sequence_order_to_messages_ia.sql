-- Migration V21 : Ordre chronologique déterministe des messages IA (ANO-PROD-04)
-- Ajoute la colonne `ordre` sur messages_ia, rétro-remplit de façon déterministe
-- (date_envoi ASC, UTILISATEUR avant ASSISTANT, id ASC) et crée un index composite.

ALTER TABLE messages_ia
    ADD COLUMN IF NOT EXISTS ordre BIGINT;

WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY conversation_id
               ORDER BY date_envoi ASC,
                        CASE WHEN role = 'UTILISATEUR' THEN 0 ELSE 1 END ASC,
                        id ASC
           ) AS seq_ordre
    FROM messages_ia
)
UPDATE messages_ia m
SET ordre = r.seq_ordre
FROM ranked r
WHERE m.id = r.id
  AND m.ordre IS NULL;

UPDATE messages_ia
SET ordre = 0
WHERE ordre IS NULL;

ALTER TABLE messages_ia
    ALTER COLUMN ordre SET DEFAULT 0;

ALTER TABLE messages_ia
    ALTER COLUMN ordre SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_messages_ia_conv_ordre
    ON messages_ia (conversation_id, ordre ASC, date_envoi ASC, id ASC);
