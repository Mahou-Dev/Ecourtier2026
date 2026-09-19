-- Migration V5: Addition of etage, SUSPENDUE status, avis, and signalements tables

-- 1. Add etage column to annonces
ALTER TABLE annonces ADD COLUMN IF NOT EXISTS etage INTEGER;

-- 2. Update CHECK constraint on annonces.statut_publication to include 'SUSPENDUE'
ALTER TABLE annonces DROP CONSTRAINT IF EXISTS annonces_statut_publication_check;
ALTER TABLE annonces ADD CONSTRAINT annonces_statut_publication_check
    CHECK (statut_publication IN ('DISPONIBLE', 'RESERVEE', 'LOUEE', 'VENDUE', 'SUSPENDUE'));

-- 3. Create avis table with UNIQUE constraint on (annonce_id, auteur_id)
CREATE TABLE IF NOT EXISTS avis (
    id UUID PRIMARY KEY,
    annonce_id UUID NOT NULL REFERENCES annonces(id) ON DELETE CASCADE,
    auteur_id UUID NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    note INTEGER NOT NULL CHECK (note >= 1 AND note <= 5),
    commentaire TEXT,
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_avis_annonce_auteur UNIQUE (annonce_id, auteur_id)
);

CREATE INDEX IF NOT EXISTS idx_avis_annonce ON avis(annonce_id);
CREATE INDEX IF NOT EXISTS idx_avis_auteur ON avis(auteur_id);

-- 4. Create signalements table
CREATE TABLE IF NOT EXISTS signalements (
    id UUID PRIMARY KEY,
    annonce_id UUID NOT NULL REFERENCES annonces(id) ON DELETE CASCADE,
    auteur_id UUID NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    motif VARCHAR(50) NOT NULL,
    description TEXT,
    statut VARCHAR(30) NOT NULL DEFAULT 'EN_ATTENTE',
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_signalements_annonce ON signalements(annonce_id);
CREATE INDEX IF NOT EXISTS idx_signalements_statut ON signalements(statut);
