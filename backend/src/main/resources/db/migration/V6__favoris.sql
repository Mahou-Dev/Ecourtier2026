-- Migration V6: Favoris (annonces sauvegardées par les utilisateurs)

CREATE TABLE IF NOT EXISTS favoris (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    utilisateur_id UUID NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    annonce_id UUID NOT NULL REFERENCES annonces(id) ON DELETE CASCADE,
    date_ajout TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_favori_utilisateur_annonce UNIQUE (utilisateur_id, annonce_id)
);

CREATE INDEX IF NOT EXISTS idx_favoris_utilisateur ON favoris(utilisateur_id);
CREATE INDEX IF NOT EXISTS idx_favoris_annonce ON favoris(annonce_id);
