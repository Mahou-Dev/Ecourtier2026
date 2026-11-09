-- Migration V13 : Sécurisation des transactions, idempotence du séquestre et traçabilité des remboursements (LOT 2 : ANO-01, ANO-02, ANO-09)

-- 1. Colonnes de traçabilité financière et de verrouillage optimiste sur la table transactions
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS date_paiement TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS reference_remboursement_wave VARCHAR(100);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS montant_rembourse NUMERIC(15, 2);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS date_remboursement TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS motif_remboursement VARCHAR(500);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- 2. Préservation et régularisation sans perte de données d'éventuels doublons actifs historiques
-- Si une même annonce possède plusieurs transactions actives (INITIALISE ou BLOQUE),
-- on conserve la plus avancée (BLOQUE prioritaire sur INITIALISE) puis la plus récente,
-- et on bascule uniquement les doublons antérieurs non aboutis à l'état REMBOURSE avec un motif explicite.
WITH ranked_active AS (
    SELECT id,
           annonce_id,
           statut_sequestre,
           ROW_NUMBER() OVER (
               PARTITION BY annonce_id
               ORDER BY CASE WHEN statut_sequestre = 'BLOQUE' THEN 0 ELSE 1 END,
                        date_creation DESC,
                        id DESC
           ) AS rn
    FROM transactions
    WHERE statut_sequestre IN ('INITIALISE', 'BLOQUE')
)
UPDATE transactions t
SET statut_sequestre = 'REMBOURSE',
    date_remboursement = COALESCE(t.date_remboursement, CURRENT_TIMESTAMP),
    motif_remboursement = COALESCE(t.motif_remboursement, 'Régularisation automatique migration V13 : doublon historique de transaction active')
FROM ranked_active r
WHERE t.id = r.id
  AND r.rn > 1;

-- 3. Index unique partiel empêchant deux transactions actives simultanées (INITIALISE ou BLOQUE) pour une même annonce
-- Les transactions terminées (REMBOURSE ou LIBERE historique) ne bloquent pas la contrainte partielle.
CREATE UNIQUE INDEX IF NOT EXISTS uq_transactions_annonce_active
    ON transactions (annonce_id)
    WHERE statut_sequestre IN ('INITIALISE', 'BLOQUE');

-- 4. Index unique sur les références Wave (paiement et remboursement) lorsqu'elles sont renseignées
CREATE UNIQUE INDEX IF NOT EXISTS uq_transactions_reference_wave
    ON transactions (reference_wave)
    WHERE reference_wave IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_transactions_reference_remboursement_wave
    ON transactions (reference_remboursement_wave)
    WHERE reference_remboursement_wave IS NOT NULL;
