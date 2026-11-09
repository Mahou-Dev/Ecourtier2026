-- Migration V14 : Réconciliation non destructive et vérification stricte du schéma transactions (LOT 2)
-- Objectifs :
--   1. Garantir la présence de la colonne dossier_actif (BOOLEAN NOT NULL DEFAULT TRUE) sans modifier les données historiques.
--   2. Vérifier l'absence de toute violation de contrainte ou de doublon incompatible avant création/remplacement des contraintes et index
--      (aucun DELETE, TRUNCATE ou UPDATE de données métier).
--   3. Vérifier l'expression exacte de la contrainte chk_transactions_bloque_dossier_actif et des 4 index uniques partiels :
--      - uq_transactions_annonce_active
--      - uq_transactions_annonce_bloque
--      - uq_transactions_reference_wave
--      - uq_transactions_reference_remboursement_wave
--      Si un objet existe sous le même nom avec une définition, colonne ou prédicat incorrect, effectuer un remplacement contrôlé
--      après validation des données, puis vérifier la conformité finale des définitions dans le catalogue PostgreSQL.

-- 1. Ajout idempotent de la colonne dossier_actif si elle est absente (cas d'une base ayant appliqué V13 historique)
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS dossier_actif BOOLEAN NOT NULL DEFAULT TRUE;

-- 2. Contrôles préalables sur les données et remplacement contrôlé des objets dont la définition diffère de la cible
DO $$
DECLARE
    v_annonce_id UUID;
    v_nb BIGINT;
    v_ids TEXT;
    v_ref VARCHAR(100);
    v_constraint_def TEXT;
    v_indexdef TEXT;

    c_expected_check CONSTANT TEXT :=
        'CHECK ((((statut_sequestre)::text <> ''BLOQUE''::text) OR (dossier_actif = true)))';
    c_expected_idx_active CONSTANT TEXT :=
        'CREATE UNIQUE INDEX uq_transactions_annonce_active ON public.transactions USING btree (annonce_id) WHERE (((statut_sequestre)::text = ANY ((ARRAY[''INITIALISE''::character varying, ''BLOQUE''::character varying])::text[])) AND (dossier_actif = true))';
    c_expected_idx_bloque CONSTANT TEXT :=
        'CREATE UNIQUE INDEX uq_transactions_annonce_bloque ON public.transactions USING btree (annonce_id) WHERE ((statut_sequestre)::text = ''BLOQUE''::text)';
    c_expected_idx_ref_wave CONSTANT TEXT :=
        'CREATE UNIQUE INDEX uq_transactions_reference_wave ON public.transactions USING btree (reference_wave) WHERE (reference_wave IS NOT NULL)';
    c_expected_idx_ref_remb CONSTANT TEXT :=
        'CREATE UNIQUE INDEX uq_transactions_reference_remboursement_wave ON public.transactions USING btree (reference_remboursement_wave) WHERE (reference_remboursement_wave IS NOT NULL)';
BEGIN
    -- 2.1 Vérification des données pour chk_transactions_bloque_dossier_actif :
    --     aucune transaction BLOQUE ne peut avoir dossier_actif = FALSE (ou NULL)
    SELECT COUNT(*), STRING_AGG(id::TEXT, ', ' ORDER BY id)
    INTO v_nb, v_ids
    FROM transactions
    WHERE statut_sequestre = 'BLOQUE'
      AND COALESCE(dossier_actif, FALSE) = FALSE;

    IF v_nb > 0 THEN
        RAISE EXCEPTION 'Migration V14 interrompue : % transaction(s) en statut BLOQUE présentent dossier_actif = FALSE [ids=%]. Aucune correction silencieuse n''est effectuée.',
            v_nb, v_ids;
    END IF;

    -- 2.2 Vérification de l'unicité des transactions BLOQUE par annonce
    SELECT annonce_id, COUNT(*), STRING_AGG(id::TEXT, ', ' ORDER BY id)
    INTO v_annonce_id, v_nb, v_ids
    FROM transactions
    WHERE statut_sequestre = 'BLOQUE'
    GROUP BY annonce_id
    HAVING COUNT(*) > 1
    LIMIT 1;

    IF v_annonce_id IS NOT NULL THEN
        RAISE EXCEPTION 'Migration V14 interrompue : % transactions BLOQUE simultanées détectées pour l''annonce % [ids=%]. Une régularisation humaine est requise.',
            v_nb, v_annonce_id, v_ids;
    END IF;

    -- 2.3 Vérification de l'unicité des transactions actives (INITIALISE ou BLOQUE avec dossier_actif = TRUE) par annonce
    SELECT annonce_id, COUNT(*), STRING_AGG(id::TEXT, ', ' ORDER BY id)
    INTO v_annonce_id, v_nb, v_ids
    FROM transactions
    WHERE statut_sequestre IN ('INITIALISE', 'BLOQUE')
      AND dossier_actif = TRUE
    GROUP BY annonce_id
    HAVING COUNT(*) > 1
    LIMIT 1;

    IF v_annonce_id IS NOT NULL THEN
        RAISE EXCEPTION 'Migration V14 interrompue : % transactions actives (INITIALISE/BLOQUE avec dossier_actif = TRUE) détectées pour l''annonce % [ids=%]. Aucune désactivation ni modification automatique n''est effectuée par V14.',
            v_nb, v_annonce_id, v_ids;
    END IF;

    -- 2.4 Vérification de l'unicité des références Wave de paiement non nulles
    SELECT reference_wave, COUNT(*), STRING_AGG(id::TEXT, ', ' ORDER BY id)
    INTO v_ref, v_nb, v_ids
    FROM transactions
    WHERE reference_wave IS NOT NULL
    GROUP BY reference_wave
    HAVING COUNT(*) > 1
    LIMIT 1;

    IF v_ref IS NOT NULL THEN
        RAISE EXCEPTION 'Migration V14 interrompue : doublon de reference_wave (%) détecté sur % transactions [ids=%].',
            v_ref, v_nb, v_ids;
    END IF;

    -- 2.5 Vérification de l'unicité des références Wave de remboursement non nulles
    SELECT reference_remboursement_wave, COUNT(*), STRING_AGG(id::TEXT, ', ' ORDER BY id)
    INTO v_ref, v_nb, v_ids
    FROM transactions
    WHERE reference_remboursement_wave IS NOT NULL
    GROUP BY reference_remboursement_wave
    HAVING COUNT(*) > 1
    LIMIT 1;

    IF v_ref IS NOT NULL THEN
        RAISE EXCEPTION 'Migration V14 interrompue : doublon de reference_remboursement_wave (%) détecté sur % transactions [ids=%].',
            v_ref, v_nb, v_ids;
    END IF;

    -- 2.6 Contrôle et réconciliation de la contrainte CHECK chk_transactions_bloque_dossier_actif
    SELECT pg_get_constraintdef(oid)
    INTO v_constraint_def
    FROM pg_constraint
    WHERE conrelid = 'public.transactions'::regclass
      AND conname = 'chk_transactions_bloque_dossier_actif';

    IF v_constraint_def IS NOT NULL AND v_constraint_def <> c_expected_check THEN
        ALTER TABLE transactions DROP CONSTRAINT chk_transactions_bloque_dossier_actif;
        v_constraint_def := NULL;
    END IF;

    IF v_constraint_def IS NULL THEN
        ALTER TABLE transactions
            ADD CONSTRAINT chk_transactions_bloque_dossier_actif
            CHECK (statut_sequestre <> 'BLOQUE' OR dossier_actif = TRUE);
    END IF;

    -- 2.7 Contrôle de la définition exacte de uq_transactions_annonce_active
    SELECT indexdef
    INTO v_indexdef
    FROM pg_indexes
    WHERE schemaname = 'public'
      AND tablename = 'transactions'
      AND indexname = 'uq_transactions_annonce_active';

    IF v_indexdef IS NOT NULL AND v_indexdef <> c_expected_idx_active THEN
        DROP INDEX public.uq_transactions_annonce_active;
    END IF;

    -- 2.8 Contrôle de la définition exacte de uq_transactions_annonce_bloque
    SELECT indexdef
    INTO v_indexdef
    FROM pg_indexes
    WHERE schemaname = 'public'
      AND tablename = 'transactions'
      AND indexname = 'uq_transactions_annonce_bloque';

    IF v_indexdef IS NOT NULL AND v_indexdef <> c_expected_idx_bloque THEN
        DROP INDEX public.uq_transactions_annonce_bloque;
    END IF;

    -- 2.9 Contrôle de la définition exacte de uq_transactions_reference_wave
    SELECT indexdef
    INTO v_indexdef
    FROM pg_indexes
    WHERE schemaname = 'public'
      AND tablename = 'transactions'
      AND indexname = 'uq_transactions_reference_wave';

    IF v_indexdef IS NOT NULL AND v_indexdef <> c_expected_idx_ref_wave THEN
        DROP INDEX public.uq_transactions_reference_wave;
    END IF;

    -- 2.10 Contrôle de la définition exacte de uq_transactions_reference_remboursement_wave
    SELECT indexdef
    INTO v_indexdef
    FROM pg_indexes
    WHERE schemaname = 'public'
      AND tablename = 'transactions'
      AND indexname = 'uq_transactions_reference_remboursement_wave';

    IF v_indexdef IS NOT NULL AND v_indexdef <> c_expected_idx_ref_remb THEN
        DROP INDEX public.uq_transactions_reference_remboursement_wave;
    END IF;
END $$;

-- 3. Création idempotente des quatre index uniques partiels conformes au modèle métier
CREATE UNIQUE INDEX IF NOT EXISTS uq_transactions_annonce_active
    ON transactions (annonce_id)
    WHERE statut_sequestre IN ('INITIALISE', 'BLOQUE') AND dossier_actif = TRUE;

CREATE UNIQUE INDEX IF NOT EXISTS uq_transactions_annonce_bloque
    ON transactions (annonce_id)
    WHERE statut_sequestre = 'BLOQUE';

CREATE UNIQUE INDEX IF NOT EXISTS uq_transactions_reference_wave
    ON transactions (reference_wave)
    WHERE reference_wave IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_transactions_reference_remboursement_wave
    ON transactions (reference_remboursement_wave)
    WHERE reference_remboursement_wave IS NOT NULL;

-- 4. Vérification finale obligatoire des expressions et prédicats dans le catalogue PostgreSQL
DO $$
DECLARE
    v_constraint_def TEXT;
    v_idx_active TEXT;
    v_idx_bloque TEXT;
    v_idx_ref_wave TEXT;
    v_idx_ref_remb TEXT;

    c_expected_check CONSTANT TEXT :=
        'CHECK ((((statut_sequestre)::text <> ''BLOQUE''::text) OR (dossier_actif = true)))';
    c_expected_idx_active CONSTANT TEXT :=
        'CREATE UNIQUE INDEX uq_transactions_annonce_active ON public.transactions USING btree (annonce_id) WHERE (((statut_sequestre)::text = ANY ((ARRAY[''INITIALISE''::character varying, ''BLOQUE''::character varying])::text[])) AND (dossier_actif = true))';
    c_expected_idx_bloque CONSTANT TEXT :=
        'CREATE UNIQUE INDEX uq_transactions_annonce_bloque ON public.transactions USING btree (annonce_id) WHERE ((statut_sequestre)::text = ''BLOQUE''::text)';
    c_expected_idx_ref_wave CONSTANT TEXT :=
        'CREATE UNIQUE INDEX uq_transactions_reference_wave ON public.transactions USING btree (reference_wave) WHERE (reference_wave IS NOT NULL)';
    c_expected_idx_ref_remb CONSTANT TEXT :=
        'CREATE UNIQUE INDEX uq_transactions_reference_remboursement_wave ON public.transactions USING btree (reference_remboursement_wave) WHERE (reference_remboursement_wave IS NOT NULL)';
BEGIN
    SELECT pg_get_constraintdef(oid)
    INTO v_constraint_def
    FROM pg_constraint
    WHERE conrelid = 'public.transactions'::regclass
      AND conname = 'chk_transactions_bloque_dossier_actif'
      AND contype = 'c';

    IF v_constraint_def IS DISTINCT FROM c_expected_check THEN
        RAISE EXCEPTION 'Migration V14 invalide : définition de chk_transactions_bloque_dossier_actif non conforme (actuelle=% / attendue=%)',
            v_constraint_def, c_expected_check;
    END IF;

    SELECT indexdef INTO v_idx_active
    FROM pg_indexes
    WHERE schemaname = 'public' AND tablename = 'transactions' AND indexname = 'uq_transactions_annonce_active';

    IF v_idx_active IS DISTINCT FROM c_expected_idx_active THEN
        RAISE EXCEPTION 'Migration V14 invalide : définition de uq_transactions_annonce_active non conforme (actuelle=% / attendue=%)',
            v_idx_active, c_expected_idx_active;
    END IF;

    SELECT indexdef INTO v_idx_bloque
    FROM pg_indexes
    WHERE schemaname = 'public' AND tablename = 'transactions' AND indexname = 'uq_transactions_annonce_bloque';

    IF v_idx_bloque IS DISTINCT FROM c_expected_idx_bloque THEN
        RAISE EXCEPTION 'Migration V14 invalide : définition de uq_transactions_annonce_bloque non conforme (actuelle=% / attendue=%)',
            v_idx_bloque, c_expected_idx_bloque;
    END IF;

    SELECT indexdef INTO v_idx_ref_wave
    FROM pg_indexes
    WHERE schemaname = 'public' AND tablename = 'transactions' AND indexname = 'uq_transactions_reference_wave';

    IF v_idx_ref_wave IS DISTINCT FROM c_expected_idx_ref_wave THEN
        RAISE EXCEPTION 'Migration V14 invalide : définition de uq_transactions_reference_wave non conforme (actuelle=% / attendue=%)',
            v_idx_ref_wave, c_expected_idx_ref_wave;
    END IF;

    SELECT indexdef INTO v_idx_ref_remb
    FROM pg_indexes
    WHERE schemaname = 'public' AND tablename = 'transactions' AND indexname = 'uq_transactions_reference_remboursement_wave';

    IF v_idx_ref_remb IS DISTINCT FROM c_expected_idx_ref_remb THEN
        RAISE EXCEPTION 'Migration V14 invalide : définition de uq_transactions_reference_remboursement_wave non conforme (actuelle=% / attendue=%)',
            v_idx_ref_remb, c_expected_idx_ref_remb;
    END IF;
END $$;
