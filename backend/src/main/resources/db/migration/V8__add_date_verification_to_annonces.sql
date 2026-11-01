-- Migration V8: Ajout date_verification à la table annonces pour mesurer le temps de modération admin
ALTER TABLE annonces ADD COLUMN IF NOT EXISTS date_verification TIMESTAMP;
