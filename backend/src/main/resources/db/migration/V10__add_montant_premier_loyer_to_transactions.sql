-- Migration V10 : Ajout du montant du premier loyer pour le séquestre locatif
-- Conforme au cahier des charges (§4) : caution ET premier loyer en séquestre pour LOCATION, 0 pour VENTE
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS montant_premier_loyer NUMERIC(15,2) NOT NULL DEFAULT 0;
