-- Migration V16: Ajout du type de bien 'CHAMBRE' dans les contraintes d'annonces
ALTER TABLE annonces DROP CONSTRAINT IF EXISTS annonces_type_check;
ALTER TABLE annonces ADD CONSTRAINT annonces_type_check CHECK (type IN ('STUDIO', 'APPARTEMENT', 'VILLA', 'CHAMBRE'));
