-- Migration V15: Index composite pour l'optimisation des requêtes de photos par annonce et ordre (ANO-11)
-- Description: Évite un tri mémoire systématique (Sort) lors de la récupération des photos ordonnées d'une annonce.

CREATE INDEX IF NOT EXISTS idx_photos_annonces_annonce_ordre ON photos_annonces (annonce_id, ordre ASC);
