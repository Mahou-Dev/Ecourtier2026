-- Initial Database Schema Migration for E-Courtier Senegal
-- Version: V1
-- Description: Create core tables for Utilisateurs, Annonces, Photos, Equipements, RendezVous, Transactions

CREATE TABLE utilisateurs (
    id UUID PRIMARY KEY,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    mot_de_passe VARCHAR(255) NOT NULL,
    telephone VARCHAR(30) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'AGENT', 'PROPRIETAIRE', 'LOCATAIRE')),
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    photo_profil_url VARCHAR(512)
);

CREATE INDEX idx_utilisateurs_email ON utilisateurs(email);

CREATE TABLE annonces (
    id UUID PRIMARY KEY,
    titre VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    type VARCHAR(30) NOT NULL CHECK (type IN ('STUDIO', 'APPARTEMENT', 'VILLA')),
    mode_transaction VARCHAR(20) NOT NULL CHECK (mode_transaction IN ('LOCATION', 'VENTE')),
    prix NUMERIC(15, 2) NOT NULL,
    superficie DOUBLE PRECISION NOT NULL,
    nombre_pieces INT NOT NULL,
    quartier VARCHAR(100) NOT NULL,
    ville VARCHAR(100) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    meuble BOOLEAN NOT NULL DEFAULT FALSE,
    animaux_acceptes BOOLEAN NOT NULL DEFAULT FALSE,
    lien_visite_360 VARCHAR(512),
    statut_verification VARCHAR(30) NOT NULL CHECK (statut_verification IN ('EN_ATTENTE', 'VERIFIEE', 'REJETEE')),
    statut_publication VARCHAR(30) NOT NULL CHECK (statut_publication IN ('DISPONIBLE', 'RESERVEE', 'LOUEE', 'VENDUE')),
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    utilisateur_id UUID NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE
);

CREATE INDEX idx_annonces_ville_quartier ON annonces(ville, quartier);
CREATE INDEX idx_annonces_utilisateur ON annonces(utilisateur_id);

CREATE TABLE photos_annonces (
    id UUID PRIMARY KEY,
    url VARCHAR(512) NOT NULL,
    ordre INT NOT NULL DEFAULT 0,
    annonce_id UUID NOT NULL REFERENCES annonces(id) ON DELETE CASCADE
);

CREATE INDEX idx_photos_annonce ON photos_annonces(annonce_id);

CREATE TABLE equipements_annonces (
    annonce_id UUID NOT NULL REFERENCES annonces(id) ON DELETE CASCADE,
    equipement VARCHAR(50) NOT NULL,
    PRIMARY KEY (annonce_id, equipement)
);

CREATE TABLE rendez_vous (
    id UUID PRIMARY KEY,
    annonce_id UUID NOT NULL REFERENCES annonces(id) ON DELETE CASCADE,
    demandeur_id UUID NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    date_heure TIMESTAMP NOT NULL,
    statut VARCHAR(30) NOT NULL CHECK (statut IN ('DEMANDE', 'CONFIRME', 'ANNULE', 'EFFECTUE')),
    rappel_envoye BOOLEAN NOT NULL DEFAULT FALSE,
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_rendezvous_annonce ON rendez_vous(annonce_id);
CREATE INDEX idx_rendezvous_demandeur ON rendez_vous(demandeur_id);

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    annonce_id UUID NOT NULL REFERENCES annonces(id) ON DELETE CASCADE,
    locataire_acheteur_id UUID NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    montant_caution NUMERIC(15, 2) NOT NULL,
    statut_sequestre VARCHAR(30) NOT NULL CHECK (statut_sequestre IN ('EN_ATTENTE', 'BLOQUE', 'LIBERE', 'REMBOURSE')),
    statut_bail VARCHAR(30) NOT NULL CHECK (statut_bail IN ('NON_SIGNE', 'SIGNE_LOCATAIRE', 'SIGNE_PROPRIETAIRE', 'SIGNE_COMPLET')),
    frais_dossier NUMERIC(15, 2) NOT NULL DEFAULT 5000.00,
    reference_wave VARCHAR(100),
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_liberation TIMESTAMP
);

CREATE INDEX idx_transactions_annonce ON transactions(annonce_id);
CREATE INDEX idx_transactions_locataire ON transactions(locataire_acheteur_id);
