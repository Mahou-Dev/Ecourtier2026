-- Migration V20 : Sécurisation Phase 3 (Hors Wave)
-- 1. Révocation immédiate des access tokens JWT via token_version par utilisateur
-- 2. Traçabilité et reprise fiable de génération du PDF de bail sur transactions
-- 3. Verrous persistants inter-instances pour les tâches @Scheduled (compatible PostgreSQL 16 & H2)

ALTER TABLE utilisateurs
    ADD COLUMN IF NOT EXISTS token_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS statut_document_bail VARCHAR(30) NOT NULL DEFAULT 'NON_GENERE';

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS pdf_bail_erreur VARCHAR(1000);

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS pdf_bail_tentatives INTEGER NOT NULL DEFAULT 0;

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS pdf_bail_derniere_tentative TIMESTAMP WITHOUT TIME ZONE;

UPDATE transactions
SET statut_document_bail = 'GENERE'
WHERE pdf_bail_path IS NOT NULL
  AND TRIM(pdf_bail_path) <> '';

UPDATE transactions
SET statut_document_bail = 'A_GENERER'
WHERE statut_sequestre = 'LIBERE'
  AND (pdf_bail_path IS NULL OR TRIM(pdf_bail_path) = '');

ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_statut_document_bail
        CHECK (statut_document_bail IN ('NON_GENERE', 'A_GENERER', 'GENERE', 'ECHEC'));

CREATE TABLE IF NOT EXISTS scheduler_locks (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    locked_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
