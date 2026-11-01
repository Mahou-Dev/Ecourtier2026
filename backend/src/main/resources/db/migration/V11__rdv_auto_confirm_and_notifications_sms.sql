-- Migration V11 : Confirmation automatique des RDV (F16) et notifications SMS simulées (F17, S3)

ALTER TABLE annonces
    ADD COLUMN IF NOT EXISTS confirmation_automatique BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS notifications_sms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    utilisateur_id UUID REFERENCES utilisateurs(id) ON DELETE SET NULL,
    telephone VARCHAR(30),
    type VARCHAR(30) NOT NULL CHECK (type IN ('RDV_DEMANDE', 'RDV_CONFIRME', 'RDV_ANNULE', 'RDV_RAPPEL')),
    message TEXT NOT NULL,
    statut VARCHAR(20) NOT NULL DEFAULT 'SIMULE',
    rdv_id UUID REFERENCES rendez_vous(id) ON DELETE SET NULL,
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notifications_sms_utilisateur ON notifications_sms(utilisateur_id);
CREATE INDEX IF NOT EXISTS idx_notifications_sms_rdv ON notifications_sms(rdv_id);
CREATE INDEX IF NOT EXISTS idx_notifications_sms_date_creation ON notifications_sms(date_creation DESC);
