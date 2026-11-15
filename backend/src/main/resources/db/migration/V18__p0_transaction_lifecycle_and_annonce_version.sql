-- Migration V18 : Verrouillage optimiste des annonces (P0-2) et cycle de vie complet des transactions INITIALISE (P0-3)

-- 1. P0-2 : Colonne de version optimiste sur la table annonces
ALTER TABLE annonces ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- 2. P0-3 : Colonnes d'échéance et de clôture sur la table transactions
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS date_expiration TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS date_cloture TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS motif_cloture VARCHAR(500);

-- 3. P0-3 : Rétro-alimentation déterministe de date_expiration pour les transactions INITIALISE sans échéance
UPDATE transactions
SET date_expiration = date_creation + INTERVAL '24 hours'
WHERE statut_sequestre = 'INITIALISE'
  AND date_expiration IS NULL;

-- 4. P0-3 : Extension de la contrainte CHECK sur transactions.statut_sequestre pour inclure ANNULE et EXPIRE
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_statut_sequestre_check;
ALTER TABLE transactions ADD CONSTRAINT transactions_statut_sequestre_check
    CHECK (statut_sequestre IN ('INITIALISE', 'BLOQUE', 'LIBERE', 'REMBOURSE', 'ANNULE', 'EXPIRE'));

-- 5. P0-3 : Index partiel pour la sélection efficace des transactions INITIALISE expirées par le scheduler
CREATE INDEX IF NOT EXISTS idx_transactions_initialise_expiration
    ON transactions (date_expiration)
    WHERE statut_sequestre = 'INITIALISE' AND dossier_actif = TRUE;
