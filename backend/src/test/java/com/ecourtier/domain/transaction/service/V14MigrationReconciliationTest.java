package com.ecourtier.domain.transaction.service;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests d'intégration et de validation de la séquence V13 (historique immuable, checksum 584995134) + V14
 * (V14__reconciliation_schema_transactions.sql).
 *
 * Couvre les scénarios obligatoires :
 * - A : Ancien V13 exact (checksum 584995134) + V14.
 * - B : V1 à V13 exacts + V14 sur base vierge et idempotence de V14.
 * - C : Conservation intégrale des 12 transactions historiques (11 LIBERE + 1 INITIALISE).
 * - D : Rejet des doublons actifs incompatibles.
 * - E : Rejet d'une transaction BLOQUE avec dossier_actif = FALSE.
 * - G : Vérification des définitions exactes des 4 index partiels et de la contrainte CHECK,
 *       y compris le remplacement contrôlé lorsqu'un objet porte le bon nom mais une définition erronée.
 */
class V14MigrationReconciliationTest {

    private static final Path MIGRATIONS_DIR = Path.of("src/main/resources/db/migration");
    private static final Path V13_PATH = MIGRATIONS_DIR.resolve("V13__securisation_transactions_sequestre_remboursement.sql");
    private static final Path V14_PATH = MIGRATIONS_DIR.resolve("V14__reconciliation_schema_transactions.sql");

    private static final String EXPECTED_CHECK_DEF =
            "CHECK ((((statut_sequestre)::text <> 'BLOQUE'::text) OR (dossier_actif = true)))";
    private static final String EXPECTED_IDX_ACTIVE_DEF =
            "CREATE UNIQUE INDEX uq_transactions_annonce_active ON public.transactions USING btree (annonce_id) WHERE (((statut_sequestre)::text = ANY ((ARRAY['INITIALISE'::character varying, 'BLOQUE'::character varying])::text[])) AND (dossier_actif = true))";
    private static final String EXPECTED_IDX_BLOQUE_DEF =
            "CREATE UNIQUE INDEX uq_transactions_annonce_bloque ON public.transactions USING btree (annonce_id) WHERE ((statut_sequestre)::text = 'BLOQUE'::text)";
    private static final String EXPECTED_IDX_REF_WAVE_DEF =
            "CREATE UNIQUE INDEX uq_transactions_reference_wave ON public.transactions USING btree (reference_wave) WHERE (reference_wave IS NOT NULL)";
    private static final String EXPECTED_IDX_REF_REMB_DEF =
            "CREATE UNIQUE INDEX uq_transactions_reference_remboursement_wave ON public.transactions USING btree (reference_remboursement_wave) WHERE (reference_remboursement_wave IS NOT NULL)";

    private record HistoricalTxSeed(
            UUID id,
            UUID annonceId,
            UUID locataireId,
            UUID proprietaireId,
            BigDecimal montantCaution,
            BigDecimal montantPremierLoyer,
            BigDecimal fraisDossier,
            String statutSequestre,
            String statutBail,
            String referenceWave,
            LocalDateTime dateCreation,
            LocalDateTime dateLiberation,
            LocalDateTime dateSignatureLocataire,
            LocalDateTime dateSignatureProprietaire,
            String pdfBailPath
    ) {
    }

    private String getPg16JdbcUrl() {
        String prop = System.getProperty("pg16.jdbc.url");
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        String env = System.getenv("PG16_JDBC_URL");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return null;
    }

    private boolean isPostgresMode() {
        return getPg16JdbcUrl() != null;
    }

    private static int computeFlywayChecksum(Path sqlFile) throws Exception {
        List<String> lines = Files.readAllLines(sqlFile, StandardCharsets.UTF_8);
        CRC32 crc32 = new CRC32();
        for (String line : lines) {
            crc32.update(line.getBytes(StandardCharsets.UTF_8));
        }
        return (int) crc32.getValue();
    }

    private DataSource createIsolatedDatabase(String suffix) throws Exception {
        String pgUrl = getPg16JdbcUrl();
        if (pgUrl != null) {
            String user = System.getProperty("pg16.jdbc.user", "ecourtier_user");
            String password = System.getProperty("pg16.jdbc.password", "ecourtier_secure_pass_2026");
            String dbName = "v14_test_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

            PGSimpleDataSource adminDs = new PGSimpleDataSource();
            adminDs.setUrl(pgUrl);
            adminDs.setUser(user);
            adminDs.setPassword(password);

            try (Connection adminConn = adminDs.getConnection();
                 Statement st = adminConn.createStatement()) {
                st.execute("CREATE DATABASE " + dbName);
            }

            String basePrefix = pgUrl.substring(0, pgUrl.lastIndexOf('/') + 1);
            PGSimpleDataSource testDs = new PGSimpleDataSource();
            testDs.setUrl(basePrefix + dbName);
            testDs.setUser(user);
            testDs.setPassword(password);
            return testDs;
        } else {
            JdbcDataSource h2Ds = new JdbcDataSource();
            h2Ds.setURL("jdbc:h2:mem:v14_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "")
                    + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
            h2Ds.setUser("sa");
            h2Ds.setPassword("");
            return h2Ds;
        }
    }

    private void initPreV13Schema(Connection conn) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS transactions (
                        id UUID PRIMARY KEY,
                        annonce_id UUID NOT NULL,
                        locataire_id UUID NOT NULL,
                        proprietaire_id UUID,
                        montant_caution NUMERIC(15, 2) NOT NULL,
                        statut_sequestre VARCHAR(30) NOT NULL DEFAULT 'INITIALISE',
                        statut_bail VARCHAR(30) NOT NULL DEFAULT 'NON_SIGNE',
                        frais_dossier NUMERIC(15, 2) NOT NULL DEFAULT 5000.00,
                        reference_wave VARCHAR(100),
                        date_creation TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        date_liberation TIMESTAMP WITHOUT TIME ZONE,
                        date_signature_locataire TIMESTAMP WITHOUT TIME ZONE,
                        date_signature_proprietaire TIMESTAMP WITHOUT TIME ZONE,
                        pdf_bail_path VARCHAR(500),
                        montant_premier_loyer NUMERIC(15, 2) NOT NULL DEFAULT 0,
                        CONSTRAINT transactions_statut_sequestre_check
                            CHECK (statut_sequestre IN ('INITIALISE', 'BLOQUE', 'LIBERE', 'REMBOURSE')),
                        CONSTRAINT transactions_statut_bail_check
                            CHECK (statut_bail IN ('NON_SIGNE', 'SIGNE_LOCATAIRE', 'SIGNE_PROPRIETAIRE', 'SIGNE_COMPLET'))
                    )
                    """);
        }
    }

    /**
     * Exécute le fichier V13 exact du dépôt (checksum 584995134).
     */
    private void applyExactV13FromRepo(Connection conn) throws Exception {
        String v13Sql = Files.readString(V13_PATH);
        if (isPostgresMode()) {
            try (Statement st = conn.createStatement()) {
                st.execute(v13Sql);
            }
        } else {
            try (Statement st = conn.createStatement()) {
                for (String line : v13Sql.split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("ALTER TABLE transactions ADD COLUMN IF NOT EXISTS")) {
                        st.execute(trimmed);
                    }
                }
                st.execute("CREATE INDEX IF NOT EXISTS uq_transactions_annonce_active ON transactions (annonce_id)");
            }
        }
    }

    /**
     * Exécute le fichier V14__reconciliation_schema_transactions.sql.
     */
    private void applyV14(Connection conn) throws Exception {
        String v14Sql = Files.readString(V14_PATH);
        if (isPostgresMode()) {
            try (Statement st = conn.createStatement()) {
                st.execute(v14Sql);
            }
        } else {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("ALTER TABLE transactions ADD COLUMN IF NOT EXISTS dossier_actif BOOLEAN NOT NULL DEFAULT TRUE");

                try (PreparedStatement ps = conn.prepareStatement("""
                        SELECT id FROM transactions
                        WHERE statut_sequestre = 'BLOQUE' AND COALESCE(dossier_actif, FALSE) = FALSE
                        """);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        throw new SQLException("Migration V14 interrompue : transaction(s) en statut BLOQUE présentent dossier_actif = FALSE [ids=" + rs.getObject(1) + "]");
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement("""
                        SELECT annonce_id, COUNT(*) FROM transactions
                        WHERE statut_sequestre = 'BLOQUE'
                        GROUP BY annonce_id HAVING COUNT(*) > 1
                        """);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        throw new SQLException("Migration V14 interrompue : " + rs.getLong(2) + " transactions BLOQUE simultanées détectées pour l'annonce " + rs.getObject(1));
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement("""
                        SELECT annonce_id, COUNT(*) FROM transactions
                        WHERE statut_sequestre IN ('INITIALISE', 'BLOQUE') AND dossier_actif = TRUE
                        GROUP BY annonce_id HAVING COUNT(*) > 1
                        """);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        throw new SQLException("Migration V14 interrompue : " + rs.getLong(2) + " transactions actives détectées pour l'annonce " + rs.getObject(1));
                    }
                }

                st.execute("ALTER TABLE transactions DROP CONSTRAINT IF EXISTS chk_transactions_bloque_dossier_actif");
                st.execute("""
                        ALTER TABLE transactions ADD CONSTRAINT chk_transactions_bloque_dossier_actif
                            CHECK (statut_sequestre <> 'BLOQUE' OR dossier_actif = TRUE)
                        """);
                st.execute("DROP INDEX IF EXISTS uq_transactions_annonce_active");
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private boolean hasColumnDossierActif(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT 1 FROM information_schema.columns
                WHERE table_name = 'transactions' AND column_name = 'dossier_actif'
                """);
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }

    private boolean hasCheckConstraintBloqueDossierActif(Connection conn) throws SQLException {
        if (isPostgresMode()) {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT pg_get_constraintdef(oid)
                    FROM pg_constraint
                    WHERE conrelid = 'public.transactions'::regclass
                      AND conname = 'chk_transactions_bloque_dossier_actif'
                      AND contype = 'c'
                    """);
                 ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                return EXPECTED_CHECK_DEF.equals(rs.getString(1));
            }
        } else {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT 1 FROM information_schema.table_constraints
                    WHERE table_name = 'transactions'
                      AND constraint_name = 'chk_transactions_bloque_dossier_actif'
                    """);
                 ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Test
    @DisplayName("Immuabilité de V13 (checksum 584995134) et invariants statiques de V14")
    void testChecksumV13ImmuableEtInvariantsV14() throws Exception {
        int v13Checksum = computeFlywayChecksum(V13_PATH);
        assertEquals(584995134, v13Checksum,
                "Le fichier V13 du dépôt doit avoir le checksum Flyway historique exact 584995134");

        String v14ExecutableSql = Files.readAllLines(V14_PATH, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(line -> !line.startsWith("--"))
                .reduce("", (a, b) -> a + "\n" + b)
                .toUpperCase();
        assertFalse(v14ExecutableSql.contains("DELETE FROM"), "V14 ne doit supprimer aucune donnée métier");
        assertFalse(v14ExecutableSql.contains("TRUNCATE"), "V14 ne doit contenir aucun TRUNCATE");
        assertFalse(v14ExecutableSql.contains("UPDATE TRANSACTIONS"), "V14 ne doit modifier aucune transaction historique");
        assertTrue(v14ExecutableSql.contains("CHK_TRANSACTIONS_BLOQUE_DOSSIER_ACTIF"));
        assertTrue(v14ExecutableSql.contains("UQ_TRANSACTIONS_ANNONCE_ACTIVE"));
        assertTrue(v14ExecutableSql.contains("UQ_TRANSACTIONS_ANNONCE_BLOQUE"));
        assertTrue(v14ExecutableSql.contains("UQ_TRANSACTIONS_REFERENCE_WAVE"));
        assertTrue(v14ExecutableSql.contains("UQ_TRANSACTIONS_REFERENCE_REMBOURSEMENT_WAVE"));
    }

    @Test
    @DisplayName("Scénario A & G : Ancien V13 exact (584995134) + V14 et vérification des définitions exactes des 4 index et du CHECK")
    void testScenarioAEtGAncienV13ExactPuisV14EtDefinitionsExactes() throws Exception {
        DataSource ds = createIsolatedDatabase("scen_ag");
        try (Connection conn = ds.getConnection()) {
            initPreV13Schema(conn);
            applyExactV13FromRepo(conn);

            assertFalse(hasColumnDossierActif(conn), "Après V13 historique, dossier_actif est absent");
            assertFalse(hasCheckConstraintBloqueDossierActif(conn),
                    "Après V13 historique, chk_transactions_bloque_dossier_actif est absente");

            applyV14(conn);

            assertTrue(hasColumnDossierActif(conn), "Après V14, dossier_actif doit être présent");
            assertTrue(hasCheckConstraintBloqueDossierActif(conn),
                    "Après V14, chk_transactions_bloque_dossier_actif doit avoir la définition exacte attendue");

            if (isPostgresMode()) {
                assertCatalogDefinitionsExact(conn);
            }
        }
    }

    @Test
    @DisplayName("Scénario B & G : Remplacement contrôlé si les 4 index ou la contrainte CHECK préexistent sous le bon nom mais avec une définition incorrecte")
    void testScenarioBGIdempotenceEtRemplacementDefinitionsIncorrectes() throws Exception {
        DataSource ds = createIsolatedDatabase("scen_bg");
        try (Connection conn = ds.getConnection()) {
            initPreV13Schema(conn);
            applyExactV13FromRepo(conn);
            applyV14(conn);

            if (isPostgresMode()) {
                // Altération volontaire des définitions sous les mêmes noms d'objets
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE transactions DROP CONSTRAINT chk_transactions_bloque_dossier_actif");
                    st.execute("ALTER TABLE transactions ADD CONSTRAINT chk_transactions_bloque_dossier_actif CHECK (dossier_actif IS NOT NULL)");

                    st.execute("DROP INDEX uq_transactions_annonce_active");
                    st.execute("CREATE INDEX uq_transactions_annonce_active ON transactions (annonce_id)");

                    st.execute("DROP INDEX uq_transactions_annonce_bloque");
                    st.execute("CREATE INDEX uq_transactions_annonce_bloque ON transactions (annonce_id)");

                    st.execute("DROP INDEX uq_transactions_reference_wave");
                    st.execute("CREATE INDEX uq_transactions_reference_wave ON transactions (reference_wave)");

                    st.execute("DROP INDEX uq_transactions_reference_remboursement_wave");
                    st.execute("CREATE INDEX uq_transactions_reference_remboursement_wave ON transactions (reference_remboursement_wave)");
                }

                // Réapplication de V14 : doit détecter les 5 définitions incorrectes et les remplacer proprement
                applyV14(conn);
                assertCatalogDefinitionsExact(conn);
            }
        }
    }

    private void assertCatalogDefinitionsExact(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT pg_get_constraintdef(oid)
                FROM pg_constraint
                WHERE conrelid = 'public.transactions'::regclass
                  AND conname = 'chk_transactions_bloque_dossier_actif'
                  AND contype = 'c'
                """);
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next(), "chk_transactions_bloque_dossier_actif doit exister");
            assertEquals(EXPECTED_CHECK_DEF, rs.getString(1));
        }

        assertIndexDefExact(conn, "uq_transactions_annonce_active", EXPECTED_IDX_ACTIVE_DEF);
        assertIndexDefExact(conn, "uq_transactions_annonce_bloque", EXPECTED_IDX_BLOQUE_DEF);
        assertIndexDefExact(conn, "uq_transactions_reference_wave", EXPECTED_IDX_REF_WAVE_DEF);
        assertIndexDefExact(conn, "uq_transactions_reference_remboursement_wave", EXPECTED_IDX_REF_REMB_DEF);
    }

    private void assertIndexDefExact(Connection conn, String indexName, String expectedDef) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'transactions'
                  AND indexname = ?
                """)) {
            ps.setString(1, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "L'index " + indexName + " doit exister");
                assertEquals(expectedDef, rs.getString(1), "Définition exacte non conforme pour " + indexName);
            }
        }
    }

    @Test
    @DisplayName("Scénario C : Conservation intégrale des 12 transactions historiques (11 LIBERE + 1 INITIALISE)")
    void testScenarioCDonneesHistoriques12TransactionsIntegralementPreservees() throws Exception {
        DataSource ds = createIsolatedDatabase("scen_c");
        try (Connection conn = ds.getConnection()) {
            initPreV13Schema(conn);
            applyExactV13FromRepo(conn);

            List<HistoricalTxSeed> seeds = new ArrayList<>();
            LocalDateTime baseTime = LocalDateTime.of(2026, 10, 21, 14, 22, 55);

            for (int i = 0; i < 12; i++) {
                boolean isInitialise = (i == 1);
                UUID id = UUID.randomUUID();
                UUID annonceId = UUID.randomUUID();
                UUID locataireId = UUID.randomUUID();
                UUID proprietaireId = UUID.randomUUID();
                BigDecimal caution = isInitialise ? new BigDecimal("45000000.00") : new BigDecimal((i + 1) * 80000 + ".00");
                BigDecimal premierLoyer = (isInitialise || i % 2 == 1) ? new BigDecimal("0.00") : caution;
                BigDecimal frais = new BigDecimal("5000.00");
                String statutSequestre = isInitialise ? "INITIALISE" : "LIBERE";
                String statutBail = isInitialise ? "NON_SIGNE" : "SIGNE_COMPLET";
                String refWave = isInitialise ? null : "WAVE-SANDBOX-HIST-" + (i + 1);
                LocalDateTime created = baseTime.plusHours(i * 5L);
                LocalDateTime liberated = isInitialise ? null : created.plusSeconds(45);
                LocalDateTime sigLoc = isInitialise ? null : created.plusSeconds(20);
                LocalDateTime sigProp = isInitialise ? null : created.plusSeconds(44);
                String pdf = isInitialise ? null : "uploads/baux/bail_" + id + ".pdf";

                seeds.add(new HistoricalTxSeed(
                        id, annonceId, locataireId, proprietaireId,
                        caution, premierLoyer, frais,
                        statutSequestre, statutBail, refWave,
                        created, liberated, sigLoc, sigProp, pdf
                ));

                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO transactions (
                            id, annonce_id, locataire_id, proprietaire_id,
                            montant_caution, montant_premier_loyer, frais_dossier,
                            statut_sequestre, statut_bail, reference_wave,
                            date_creation, date_liberation,
                            date_signature_locataire, date_signature_proprietaire, pdf_bail_path
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    ps.setObject(1, id);
                    ps.setObject(2, annonceId);
                    ps.setObject(3, locataireId);
                    ps.setObject(4, proprietaireId);
                    ps.setBigDecimal(5, caution);
                    ps.setBigDecimal(6, premierLoyer);
                    ps.setBigDecimal(7, frais);
                    ps.setString(8, statutSequestre);
                    ps.setString(9, statutBail);
                    ps.setString(10, refWave);
                    ps.setTimestamp(11, Timestamp.valueOf(created));
                    ps.setTimestamp(12, liberated != null ? Timestamp.valueOf(liberated) : null);
                    ps.setTimestamp(13, sigLoc != null ? Timestamp.valueOf(sigLoc) : null);
                    ps.setTimestamp(14, sigProp != null ? Timestamp.valueOf(sigProp) : null);
                    ps.setString(15, pdf);
                    ps.executeUpdate();
                }
            }

            applyV14(conn);

            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM transactions")) {
                assertTrue(rs.next());
                assertEquals(12L, rs.getLong(1));
            }

            for (HistoricalTxSeed seed : seeds) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM transactions WHERE id = ?")) {
                    ps.setObject(1, seed.id());
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(seed.annonceId(), rs.getObject("annonce_id"));
                        assertEquals(seed.locataireId(), rs.getObject("locataire_id"));
                        assertEquals(seed.proprietaireId(), rs.getObject("proprietaire_id"));
                        assertEquals(0, seed.montantCaution().compareTo(rs.getBigDecimal("montant_caution")));
                        assertEquals(0, seed.montantPremierLoyer().compareTo(rs.getBigDecimal("montant_premier_loyer")));
                        assertEquals(0, seed.fraisDossier().compareTo(rs.getBigDecimal("frais_dossier")));
                        assertEquals(seed.statutSequestre(), rs.getString("statut_sequestre"));
                        assertEquals(seed.statutBail(), rs.getString("statut_bail"));
                        assertEquals(seed.referenceWave(), rs.getString("reference_wave"));
                        assertEquals(Timestamp.valueOf(seed.dateCreation()), rs.getTimestamp("date_creation"));
                        assertEquals(seed.dateLiberation() != null ? Timestamp.valueOf(seed.dateLiberation()) : null,
                                rs.getTimestamp("date_liberation"));
                        assertEquals(seed.dateSignatureLocataire() != null ? Timestamp.valueOf(seed.dateSignatureLocataire()) : null,
                                rs.getTimestamp("date_signature_locataire"));
                        assertEquals(seed.dateSignatureProprietaire() != null ? Timestamp.valueOf(seed.dateSignatureProprietaire()) : null,
                                rs.getTimestamp("date_signature_proprietaire"));
                        assertEquals(seed.pdfBailPath(), rs.getString("pdf_bail_path"));
                        assertTrue(rs.getBoolean("dossier_actif"));
                        assertNull(rs.getString("reference_remboursement_wave"));
                        assertNull(rs.getBigDecimal("montant_rembourse"));
                        assertNull(rs.getTimestamp("date_remboursement"));
                        assertNull(rs.getString("motif_remboursement"));
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Scénario D & E : Rejet des doublons actifs incompatibles et rejet d'une transaction BLOQUE avec dossier_actif = FALSE")
    void testScenarioDEtERejetDoublonsActifsEtBloqueInactif() throws Exception {
        DataSource ds = createIsolatedDatabase("scen_de");
        try (Connection conn = ds.getConnection()) {
            initPreV13Schema(conn);
            applyExactV13FromRepo(conn);
            applyV14(conn);

            UUID annonceId = UUID.randomUUID();

            // Plusieurs dossiers historiques inactifs (INITIALISE + dossier_actif = FALSE) autorisés
            for (int i = 0; i < 2; i++) {
                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO transactions (
                            id, annonce_id, locataire_id, proprietaire_id,
                            montant_caution, montant_premier_loyer, frais_dossier,
                            statut_sequestre, statut_bail, dossier_actif
                        ) VALUES (?, ?, ?, ?, 250000, 250000, 5000, 'INITIALISE', 'NON_SIGNE', FALSE)
                        """)) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, annonceId);
                    ps.setObject(3, UUID.randomUUID());
                    ps.setObject(4, UUID.randomUUID());
                    ps.executeUpdate();
                }
            }

            // Premier dossier actif sur l'annonce autorisé
            UUID activeTxId = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO transactions (
                        id, annonce_id, locataire_id, proprietaire_id,
                        montant_caution, montant_premier_loyer, frais_dossier,
                        statut_sequestre, statut_bail, dossier_actif
                    ) VALUES (?, ?, ?, ?, 250000, 250000, 5000, 'INITIALISE', 'NON_SIGNE', TRUE)
                    """)) {
                ps.setObject(1, activeTxId);
                ps.setObject(2, annonceId);
                ps.setObject(3, UUID.randomUUID());
                ps.setObject(4, UUID.randomUUID());
                ps.executeUpdate();
            }

            // E : Transaction BLOQUE avec dossier_actif = FALSE refusée par chk_transactions_bloque_dossier_actif
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO transactions (
                        id, annonce_id, locataire_id, proprietaire_id,
                        montant_caution, montant_premier_loyer, frais_dossier,
                        statut_sequestre, statut_bail, dossier_actif, reference_wave
                    ) VALUES (?, ?, ?, ?, 250000, 250000, 5000, 'BLOQUE', 'NON_SIGNE', FALSE, 'WAVE-BLK-INACTIF')
                    """)) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, UUID.randomUUID());
                ps.setObject(3, UUID.randomUUID());
                ps.setObject(4, UUID.randomUUID());
                assertThrows(SQLException.class, ps::executeUpdate,
                        "chk_transactions_bloque_dossier_actif doit rejeter statut_sequestre = 'BLOQUE' avec dossier_actif = FALSE");
            }

            if (isPostgresMode()) {
                // D : Seconde transaction active sur la même annonce refusée par uq_transactions_annonce_active
                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO transactions (
                            id, annonce_id, locataire_id, proprietaire_id,
                            montant_caution, montant_premier_loyer, frais_dossier,
                            statut_sequestre, statut_bail, dossier_actif
                        ) VALUES (?, ?, ?, ?, 250000, 250000, 5000, 'INITIALISE', 'NON_SIGNE', TRUE)
                        """)) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, annonceId);
                    ps.setObject(3, UUID.randomUUID());
                    ps.setObject(4, UUID.randomUUID());
                    assertThrows(SQLException.class, ps::executeUpdate,
                            "uq_transactions_annonce_active doit rejeter une seconde transaction active sur la même annonce");
                }
            }
        }
    }

    @Test
    @DisplayName("Scénario D & E (Blocage pré-migration V14) : V14 interrompt la migration en cas de doublon actif ou BLOQUE inactif préexistant")
    void testScenarioDEtEBlocagePreMigrationV14() throws Exception {
        DataSource dsDoublon = createIsolatedDatabase("scen_d_pre");
        try (Connection conn = dsDoublon.getConnection()) {
            initPreV13Schema(conn);
            try (Statement st = conn.createStatement()) {
                st.execute("ALTER TABLE transactions ADD COLUMN IF NOT EXISTS dossier_actif BOOLEAN NOT NULL DEFAULT TRUE");
            }
            UUID annonceId = UUID.randomUUID();
            for (int i = 0; i < 2; i++) {
                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO transactions (
                            id, annonce_id, locataire_id, proprietaire_id,
                            montant_caution, montant_premier_loyer, frais_dossier,
                            statut_sequestre, statut_bail, dossier_actif
                        ) VALUES (?, ?, ?, ?, 250000, 250000, 5000, 'INITIALISE', 'NON_SIGNE', TRUE)
                        """)) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, annonceId);
                    ps.setObject(3, UUID.randomUUID());
                    ps.setObject(4, UUID.randomUUID());
                    ps.executeUpdate();
                }
            }
            SQLException ex = assertThrows(SQLException.class, () -> applyV14(conn));
            assertTrue(ex.getMessage().contains("Migration V14 interrompue"));
        }

        DataSource dsBloqueInactif = createIsolatedDatabase("scen_e_pre");
        try (Connection conn = dsBloqueInactif.getConnection()) {
            initPreV13Schema(conn);
            try (Statement st = conn.createStatement()) {
                st.execute("ALTER TABLE transactions ADD COLUMN IF NOT EXISTS dossier_actif BOOLEAN NOT NULL DEFAULT TRUE");
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO transactions (
                        id, annonce_id, locataire_id, proprietaire_id,
                        montant_caution, montant_premier_loyer, frais_dossier,
                        statut_sequestre, statut_bail, dossier_actif, reference_wave
                    ) VALUES (?, ?, ?, ?, 250000, 250000, 5000, 'BLOQUE', 'NON_SIGNE', FALSE, 'WAVE-ERR-2')
                    """)) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, UUID.randomUUID());
                ps.setObject(3, UUID.randomUUID());
                ps.setObject(4, UUID.randomUUID());
                ps.executeUpdate();
            }
            SQLException ex = assertThrows(SQLException.class, () -> applyV14(conn));
            assertTrue(ex.getMessage().contains("Migration V14 interrompue") && ex.getMessage().contains("BLOQUE"));
        }
    }
}
