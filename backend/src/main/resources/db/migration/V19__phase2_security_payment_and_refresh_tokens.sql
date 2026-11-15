-- Migration V19 : Robustesse des paiements Wave (RES-02) et persistance/rotation des refresh tokens (RES-05)

-- 1. RES-02 : Table de suivi idempotent, outbox et réconciliation des opérations Wave
CREATE TABLE IF NOT EXISTS paiements_wave (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    type_operation VARCHAR(30) NOT NULL CHECK (type_operation IN ('PAIEMENT_SEQUESTRE', 'REMBOURSEMENT')),
    statut VARCHAR(40) NOT NULL CHECK (statut IN ('INITIE', 'EN_COURS', 'A_RECONCILIER', 'CONFIRME', 'ECHOUE', 'TARDIF_A_REMBOURSER', 'RECONCILIE')),
    cle_idempotence VARCHAR(128) NOT NULL UNIQUE,
    reference_externe VARCHAR(150),
    montant NUMERIC(15, 2) NOT NULL CHECK (montant > 0),
    devise VARCHAR(10) NOT NULL DEFAULT 'XOF',
    tentatives INTEGER NOT NULL DEFAULT 1,
    message_erreur VARCHAR(1000),
    payload_webhook TEXT,
    date_creation TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_modification TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_confirmation TIMESTAMP WITHOUT TIME ZONE
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_paiements_wave_tx_op_actif
    ON paiements_wave (transaction_id, type_operation)
    WHERE statut IN ('INITIE', 'EN_COURS', 'A_RECONCILIER');

CREATE UNIQUE INDEX IF NOT EXISTS uq_paiements_wave_reference_externe
    ON paiements_wave (reference_externe)
    WHERE reference_externe IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_paiements_wave_statut_date
    ON paiements_wave (statut, date_modification);

-- 2. RES-05 : Table de persistance, rotation et révocation de famille des refresh tokens
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    utilisateur_id UUID NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    jti UUID NOT NULL UNIQUE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    family_id UUID NOT NULL,
    parent_jti UUID,
    replaced_by_jti UUID,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    used_at TIMESTAMP WITHOUT TIME ZONE,
    revoked_at TIMESTAMP WITHOUT TIME ZONE,
    revocation_reason VARCHAR(100),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_utilisateur_actif
    ON refresh_tokens (utilisateur_id)
    WHERE revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family_id
    ON refresh_tokens (family_id);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at
    ON refresh_tokens (expires_at);
