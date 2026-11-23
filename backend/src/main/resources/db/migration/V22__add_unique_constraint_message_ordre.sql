-- Migration V22 : Garantie d'unicité SQL de l'ordre chronologique des messages IA (REV-03)
-- Assainit d'éventuelles collisions héritées puis ajoute la contrainte UNIQUE (conversation_id, ordre).

WITH duplicate_conversations AS (
    SELECT DISTINCT conversation_id
    FROM messages_ia
    GROUP BY conversation_id, ordre
    HAVING COUNT(*) > 1
),
resequenced AS (
    SELECT m.id,
           ROW_NUMBER() OVER (
               PARTITION BY m.conversation_id
               ORDER BY m.ordre ASC,
                        m.date_envoi ASC,
                        CASE WHEN m.role = 'UTILISATEUR' THEN 0 ELSE 1 END ASC,
                        m.id ASC
           ) AS seq_ordre
    FROM messages_ia m
    INNER JOIN duplicate_conversations d ON d.conversation_id = m.conversation_id
)
UPDATE messages_ia m
SET ordre = r.seq_ordre
FROM resequenced r
WHERE m.id = r.id;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_messages_ia_conversation_ordre'
    ) THEN
        ALTER TABLE messages_ia
            ADD CONSTRAINT uq_messages_ia_conversation_ordre UNIQUE (conversation_id, ordre);
    END IF;
END $$;
