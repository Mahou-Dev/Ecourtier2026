-- Migration V4 : Mise à jour et alignement des tables rendez_vous et transactions

-- 1. Table rendez_vous
ALTER TABLE rendez_vous ADD COLUMN IF NOT EXISTS motif_annulation TEXT;
ALTER TABLE rendez_vous ALTER COLUMN id SET DEFAULT gen_random_uuid();
CREATE INDEX IF NOT EXISTS idx_rendezvous_statut ON rendez_vous(statut);

-- 2. Table transactions
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'transactions' AND column_name = 'locataire_acheteur_id'
    ) THEN
        ALTER TABLE transactions RENAME COLUMN locataire_acheteur_id TO locataire_id;
    END IF;
END $$;

ALTER TABLE transactions ADD COLUMN IF NOT EXISTS proprietaire_id UUID REFERENCES utilisateurs(id) ON DELETE CASCADE;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS date_signature_locataire TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS date_signature_proprietaire TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS pdf_bail_path VARCHAR(500);

ALTER TABLE transactions ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE transactions ALTER COLUMN statut_sequestre SET DEFAULT 'INITIALISE';

-- Remplacement de la contrainte CHECK statut_sequestre
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_statut_sequestre_check;
ALTER TABLE transactions ADD CONSTRAINT transactions_statut_sequestre_check 
    CHECK (statut_sequestre IN ('INITIALISE', 'BLOQUE', 'LIBERE', 'REMBOURSE'));

-- Index complémentaires
CREATE INDEX IF NOT EXISTS idx_transactions_proprietaire ON transactions(proprietaire_id);
