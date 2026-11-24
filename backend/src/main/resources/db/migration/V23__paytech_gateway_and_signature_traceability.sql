-- Migration V23 : Intégration passerelle PayTech Sandbox (Wave), transfert propriétaire simulé et traçabilité SHA-256 des signatures
-- Migration strictement additive : préserve les tables transactions et paiements_wave ainsi que toutes les données existantes.

-- 1. Adaptation additive de la table paiements_wave pour le multi-prestataire (PayTech / simulation locale)
ALTER TABLE paiements_wave ADD COLUMN IF NOT EXISTS prestataire VARCHAR(30) NOT NULL DEFAULT 'MOCK_LOCAL';
ALTER TABLE paiements_wave ADD COLUMN IF NOT EXISTS moyen_paiement VARCHAR(40) NOT NULL DEFAULT 'Wave';
ALTER TABLE paiements_wave ADD COLUMN IF NOT EXISTS environnement VARCHAR(16) NOT NULL DEFAULT 'test';
ALTER TABLE paiements_wave ADD COLUMN IF NOT EXISTS ref_command VARCHAR(150);
ALTER TABLE paiements_wave ADD COLUMN IF NOT EXISTS token_externe VARCHAR(150);
ALTER TABLE paiements_wave ADD COLUMN IF NOT EXISTS redirect_url VARCHAR(1000);
ALTER TABLE paiements_wave ADD COLUMN IF NOT EXISTS mode_simulation BOOLEAN NOT NULL DEFAULT TRUE;

-- Remplacement sûr des contraintes CHECK sur paiements_wave (type_operation et statut)
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'paiements_wave'::regclass
          AND contype = 'c'
          AND (pg_get_constraintdef(oid) ILIKE '%type_operation%'
               OR pg_get_constraintdef(oid) ILIKE '%statut%')
    LOOP
        EXECUTE format('ALTER TABLE paiements_wave DROP CONSTRAINT IF EXISTS %I', r.conname);
    END LOOP;
END $$;

ALTER TABLE paiements_wave ADD CONSTRAINT chk_paiements_wave_type_operation
    CHECK (type_operation IN ('PAIEMENT_SEQUESTRE', 'REMBOURSEMENT', 'TRANSFERT_PROPRIETAIRE'));

ALTER TABLE paiements_wave ADD CONSTRAINT chk_paiements_wave_statut
    CHECK (statut IN (
        'INITIE',
        'EN_COURS',
        'EN_ATTENTE_CONFIRMATION',
        'A_RECONCILIER',
        'CONFIRME',
        'ECHOUE',
        'ANNULE',
        'TARDIF_A_REMBOURSER',
        'RECONCILIE',
        'SIMULE'
    ));

-- Extension de l'index unique partiel des opérations actives pour couvrir EN_ATTENTE_CONFIRMATION
DROP INDEX IF EXISTS uq_paiements_wave_tx_op_actif;
CREATE UNIQUE INDEX IF NOT EXISTS uq_paiements_wave_tx_op_actif
    ON paiements_wave (transaction_id, type_operation)
    WHERE statut IN ('INITIE', 'EN_COURS', 'EN_ATTENTE_CONFIRMATION', 'A_RECONCILIER');

CREATE UNIQUE INDEX IF NOT EXISTS uq_paiements_wave_ref_command
    ON paiements_wave (ref_command)
    WHERE ref_command IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_paiements_wave_token_externe
    ON paiements_wave (token_externe)
    WHERE token_externe IS NOT NULL;

-- 2. Colonnes additives sur transactions : suivi PayTech, transfert propriétaire simulé et empreintes SHA-256
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS prestataire_paiement VARCHAR(30) DEFAULT 'MOCK_LOCAL';
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS moyen_paiement VARCHAR(40) DEFAULT 'Wave';
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS mode_simulation_paiement BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS ref_command_paytech VARCHAR(150);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS token_paiement_externe VARCHAR(150);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS redirect_url_paiement VARCHAR(1000);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS statut_paiement_externe VARCHAR(40);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS statut_transfert_proprietaire VARCHAR(40);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS reference_transfert_proprietaire VARCHAR(150);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS date_transfert_proprietaire TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS bail_document_sha256 VARCHAR(64);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS signature_locataire_hash VARCHAR(64);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS signature_proprietaire_hash VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_transactions_ref_command_paytech
    ON transactions (ref_command_paytech)
    WHERE ref_command_paytech IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_transactions_token_paiement_externe
    ON transactions (token_paiement_externe)
    WHERE token_paiement_externe IS NOT NULL;
