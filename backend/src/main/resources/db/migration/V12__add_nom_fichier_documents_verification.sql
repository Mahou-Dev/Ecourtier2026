-- Migration V12: Stockage du nom de fichier original pour les documents de vérification (C2)

ALTER TABLE documents_verification
    ADD COLUMN IF NOT EXISTS nom_fichier VARCHAR(255);
