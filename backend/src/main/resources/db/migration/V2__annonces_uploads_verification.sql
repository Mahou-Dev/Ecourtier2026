-- Migration V2: Soft delete on Annonces, Proximites Tags, and Documents Verification Schema

-- Add soft delete column to annonces
ALTER TABLE annonces ADD COLUMN supprime BOOLEAN NOT NULL DEFAULT FALSE;

-- Table for proximity tags on annonces
CREATE TABLE proximites_annonces (
    annonce_id UUID NOT NULL REFERENCES annonces(id) ON DELETE CASCADE,
    proximite VARCHAR(50) NOT NULL,
    PRIMARY KEY (annonce_id, proximite)
);

-- Table for verification proof documents
CREATE TABLE documents_verification (
    id UUID PRIMARY KEY,
    annonce_id UUID NOT NULL REFERENCES annonces(id) ON DELETE CASCADE,
    type_document VARCHAR(50) NOT NULL CHECK (type_document IN ('TITRE_FONCIER', 'BAIL_PRECEDENT', 'PIECE_IDENTITE')),
    url VARCHAR(512) NOT NULL,
    statut VARCHAR(30) NOT NULL CHECK (statut IN ('EN_ATTENTE', 'VALIDE', 'REJETE')),
    motif_rejet TEXT,
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_documents_annonce ON documents_verification(annonce_id);
CREATE INDEX idx_annonces_supprime ON annonces(supprime);
