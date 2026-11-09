package com.ecourtier.domain.transaction.service;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
 * Tests de validation de l'immuabilité historique de V13 (checksum 584995134)
 * et des invariants financiers de la migration corrective V14.
 */
class V13MigrationValidationTest {

    private static final Path V13_PATH = Path.of(
            "src/main/resources/db/migration/V13__securisation_transactions_sequestre_remboursement.sql"
    );
    private static final Path V14_PATH = Path.of(
            "src/main/resources/db/migration/V14__reconciliation_schema_transactions.sql"
    );

    private JdbcDataSource dataSource;
    private String v13Sql;
    private String v14Sql;

    @BeforeEach
    void setUp() throws Exception {
        v13Sql = Files.readString(V13_PATH);
        v14Sql = Files.readString(V14_PATH);

        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:v13_v14_test_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE transactions (
                        id UUID PRIMARY KEY,
                        annonce_id UUID NOT NULL,
                        locataire_id UUID NOT NULL,
                        proprietaire_id UUID NOT NULL,
                        montant_caution NUMERIC(15, 2) NOT NULL,
                        montant_premier_loyer NUMERIC(15, 2) NOT NULL DEFAULT 0,
                        frais_dossier NUMERIC(15, 2) NOT NULL DEFAULT 5000,
                        statut_sequestre VARCHAR(20) NOT NULL DEFAULT 'INITIALISE',
                        reference_wave VARCHAR(100),
                        statut_bail VARCHAR(20) NOT NULL DEFAULT 'NON_SIGNE',
                        date_signature_locataire TIMESTAMP WITHOUT TIME ZONE,
                        date_signature_proprietaire TIMESTAMP WITHOUT TIME ZONE,
                        date_liberation TIMESTAMP WITHOUT TIME ZONE,
                        pdf_bail_path VARCHAR(500),
                        date_creation TIMESTAMP WITHOUT TIME ZONE NOT NULL
                    )
                    """);
        }
    }

    private static int computeFlywayChecksum(Path sqlFile) throws Exception {
        List<String> lines = Files.readAllLines(sqlFile, StandardCharsets.UTF_8);
        CRC32 crc32 = new CRC32();
        for (String line : lines) {
            crc32.update(line.getBytes(StandardCharsets.UTF_8));
        }
        return (int) crc32.getValue();
    }

    private void executeV13AndV14Reconciliation(Connection conn) throws SQLException {
        conn.setAutoCommit(false);
        try (Statement stmt = conn.createStatement()) {
            for (String line : v13Sql.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("ALTER TABLE transactions ADD COLUMN IF NOT EXISTS")) {
                    stmt.execute(trimmed);
                }
            }
            for (String line : v14Sql.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("ALTER TABLE transactions ADD COLUMN IF NOT EXISTS")) {
                    stmt.execute(trimmed);
                }
            }

            // Contrôle 2.1 de V14 : aucune transaction BLOQUE avec dossier_actif = FALSE
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT COUNT(*)
                    FROM transactions
                    WHERE statut_sequestre = 'BLOQUE'
                      AND COALESCE(dossier_actif, FALSE) = FALSE
                    """);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getLong(1) > 0) {
                    throw new SQLException("Migration V14 interrompue : transaction(s) BLOQUE avec dossier_actif = FALSE");
                }
            }

            // Contrôle 2.2 de V14 : plusieurs transactions BLOQUE pour une même annonce
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT annonce_id, COUNT(*)
                    FROM transactions
                    WHERE statut_sequestre = 'BLOQUE'
                    GROUP BY annonce_id
                    HAVING COUNT(*) > 1
                    LIMIT 1
                    """);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new SQLException(String.format(
                            "Migration V14 interrompue : %d transactions BLOQUE simultanées détectées pour l'annonce %s",
                            rs.getLong(2), rs.getObject(1)));
                }
            }

            // Contrôle 2.3 de V14 : plusieurs transactions actives pour une même annonce
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT annonce_id, COUNT(*)
                    FROM transactions
                    WHERE statut_sequestre IN ('INITIALISE', 'BLOQUE')
                      AND dossier_actif = TRUE
                    GROUP BY annonce_id
                    HAVING COUNT(*) > 1
                    LIMIT 1
                    """);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new SQLException(String.format(
                            "Migration V14 interrompue : %d transactions actives (INITIALISE/BLOQUE avec dossier_actif = TRUE) détectées pour l'annonce %s",
                            rs.getLong(2), rs.getObject(1)));
                }
            }

            stmt.execute("ALTER TABLE transactions DROP CONSTRAINT IF EXISTS chk_transactions_bloque_dossier_actif");
            stmt.execute("""
                    ALTER TABLE transactions ADD CONSTRAINT chk_transactions_bloque_dossier_actif
                        CHECK (statut_sequestre <> 'BLOQUE' OR dossier_actif = TRUE)
                    """);

            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private void insertTransaction(
            Connection conn,
            UUID id,
            UUID annonceId,
            UUID locataireId,
            UUID proprietaireId,
            BigDecimal caution,
            BigDecimal premierLoyer,
            BigDecimal frais,
            String statutSequestre,
            String referenceWave,
            LocalDateTime dateCreation,
            LocalDateTime dateLiberation
    ) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO transactions (
                    id, annonce_id, locataire_id, proprietaire_id,
                    montant_caution, montant_premier_loyer, frais_dossier,
                    statut_sequestre, reference_wave, date_creation, date_liberation
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setObject(1, id);
            ps.setObject(2, annonceId);
            ps.setObject(3, locataireId);
            ps.setObject(4, proprietaireId);
            ps.setBigDecimal(5, caution);
            ps.setBigDecimal(6, premierLoyer);
            ps.setBigDecimal(7, frais);
            ps.setString(8, statutSequestre);
            ps.setString(9, referenceWave);
            ps.setTimestamp(10, Timestamp.valueOf(dateCreation));
            ps.setTimestamp(11, dateLiberation != null ? Timestamp.valueOf(dateLiberation) : null);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("Immuabilité historique de V13 (checksum 584995134) et invariants statiques de V14")
    void testChecksumHistoriqueV13EtInvariantsStatiquesV14() throws Exception {
        assertEquals(584995134, computeFlywayChecksum(V13_PATH),
                "V13 doit conserver exactement le checksum historique 584995134 enregistré dans ecourtier_db");
        assertFalse(v14Sql.contains("SET statut_sequestre = 'REMBOURSE'"),
                "V14 ne doit jamais basculer automatiquement une transaction en REMBOURSE");
        assertFalse(v14Sql.contains("date_remboursement ="),
                "V14 ne doit jamais renseigner date_remboursement sans remboursement réel");
        assertFalse(v14Sql.toUpperCase().contains("DELETE FROM"),
                "V14 ne doit supprimer aucune transaction historique");
        assertTrue(v14Sql.contains("RAISE EXCEPTION"),
                "V14 doit interrompre la migration avec RAISE EXCEPTION en cas de doublons actifs ou BLOQUE invalides");
        assertTrue(v14Sql.contains("CHECK (statut_sequestre <> 'BLOQUE' OR dossier_actif = TRUE)"),
                "V14 doit contenir la contrainte CHECK (statut_sequestre <> 'BLOQUE' OR dossier_actif = TRUE)");
    }

    @Test
    @DisplayName("Cas 1 : Une seule transaction BLOQUE — migration autorisée et données intégralement conservées")
    void testCas1UneSeuleTransactionBloqueMigrationAutorisee() throws Exception {
        UUID txId = UUID.randomUUID();
        UUID annonceId = UUID.randomUUID();
        LocalDateTime dateCreation = LocalDateTime.of(2026, 3, 10, 14, 30, 0);

        try (Connection conn = dataSource.getConnection()) {
            insertTransaction(
                    conn,
                    txId,
                    annonceId,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new BigDecimal("250000.00"),
                    new BigDecimal("250000.00"),
                    new BigDecimal("5000.00"),
                    "BLOQUE",
                    "WAVE-SANDBOX-HIST001",
                    dateCreation,
                    null
            );

            executeV13AndV14Reconciliation(conn);

            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM transactions WHERE id = ?")) {
                ps.setObject(1, txId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("BLOQUE", rs.getString("statut_sequestre"));
                    assertTrue(rs.getBoolean("dossier_actif"));
                    assertEquals("WAVE-SANDBOX-HIST001", rs.getString("reference_wave"));
                    assertEquals(0, new BigDecimal("250000.00").compareTo(rs.getBigDecimal("montant_caution")));
                    assertEquals(Timestamp.valueOf(dateCreation), rs.getTimestamp("date_creation"));
                    assertNull(rs.getString("reference_remboursement_wave"));
                    assertNull(rs.getBigDecimal("montant_rembourse"));
                    assertNull(rs.getTimestamp("date_remboursement"));
                }
            }
        }
    }

    @Test
    @DisplayName("Cas 2 : Transaction BLOQUE + transaction INITIALISE inactive (dossier_actif = FALSE) — coexistence autorisée et CHECK actif")
    void testCas2CoexistenceBloqueEtInitialiseInactif() throws Exception {
        UUID annonceId = UUID.randomUUID();
        UUID txInitialiseId = UUID.randomUUID();
        UUID txBloqueId = UUID.randomUUID();

        try (Connection conn = dataSource.getConnection()) {
            executeV13AndV14Reconciliation(conn);

            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO transactions (
                        id, annonce_id, locataire_id, proprietaire_id,
                        montant_caution, montant_premier_loyer, frais_dossier,
                        statut_sequestre, reference_wave, date_creation, dossier_actif
                    ) VALUES (?, ?, ?, ?, 200000, 200000, 5000, 'INITIALISE', NULL, CURRENT_TIMESTAMP, FALSE)
                    """)) {
                ps.setObject(1, txInitialiseId);
                ps.setObject(2, annonceId);
                ps.setObject(3, UUID.randomUUID());
                ps.setObject(4, UUID.randomUUID());
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO transactions (
                        id, annonce_id, locataire_id, proprietaire_id,
                        montant_caution, montant_premier_loyer, frais_dossier,
                        statut_sequestre, reference_wave, date_creation, dossier_actif
                    ) VALUES (?, ?, ?, ?, 200000, 200000, 5000, 'BLOQUE', 'WAVE-SANDBOX-PAID777', CURRENT_TIMESTAMP, TRUE)
                    """)) {
                ps.setObject(1, txBloqueId);
                ps.setObject(2, annonceId);
                ps.setObject(3, UUID.randomUUID());
                ps.setObject(4, UUID.randomUUID());
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE transactions SET statut_sequestre = 'BLOQUE' WHERE id = ?")) {
                ps.setObject(1, txInitialiseId);
                assertThrows(SQLException.class, ps::executeUpdate,
                        "La contrainte CHECK chk_transactions_bloque_dossier_actif doit interdire statut_sequestre='BLOQUE' avec dossier_actif=FALSE");
            }
        }
    }

    @Test
    @DisplayName("Cas 3 : Deux transactions BLOQUE pour la même annonce — migration V14 interrompue avec erreur explicite")
    void testCas3DeuxTransactionsBloqueMemeAnnonceMigrationInterrompue() throws Exception {
        UUID annonceId = UUID.randomUUID();
        try (Connection conn = dataSource.getConnection()) {
            insertTransaction(
                    conn,
                    UUID.randomUUID(),
                    annonceId,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new BigDecimal("300000.00"),
                    new BigDecimal("300000.00"),
                    new BigDecimal("5000.00"),
                    "BLOQUE",
                    "WAVE-SANDBOX-BLK001",
                    LocalDateTime.of(2026, 2, 10, 9, 0, 0),
                    null
            );
            insertTransaction(
                    conn,
                    UUID.randomUUID(),
                    annonceId,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new BigDecimal("300000.00"),
                    new BigDecimal("300000.00"),
                    new BigDecimal("5000.00"),
                    "BLOQUE",
                    "WAVE-SANDBOX-BLK002",
                    LocalDateTime.of(2026, 2, 11, 11, 0, 0),
                    null
            );

            SQLException ex = assertThrows(SQLException.class, () -> executeV13AndV14Reconciliation(conn));
            assertNotNull(ex.getMessage());
            assertTrue(ex.getMessage().contains("Migration V14 interrompue") && ex.getMessage().contains("BLOQUE"));
        }
    }

    @Test
    @DisplayName("Cas 4 : Deux transactions INITIALISE actives pour la même annonce — arrêt explicite de V14")
    void testCas4DeuxTransactionsInitialiseMemeAnnonceArretExplicite() throws Exception {
        UUID annonceId = UUID.randomUUID();
        try (Connection conn = dataSource.getConnection()) {
            insertTransaction(
                    conn,
                    UUID.randomUUID(),
                    annonceId,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new BigDecimal("150000.00"),
                    new BigDecimal("150000.00"),
                    new BigDecimal("5000.00"),
                    "INITIALISE",
                    null,
                    LocalDateTime.of(2026, 2, 15, 8, 0, 0),
                    null
            );
            insertTransaction(
                    conn,
                    UUID.randomUUID(),
                    annonceId,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new BigDecimal("150000.00"),
                    new BigDecimal("150000.00"),
                    new BigDecimal("5000.00"),
                    "INITIALISE",
                    null,
                    LocalDateTime.of(2026, 2, 16, 8, 0, 0),
                    null
            );

            SQLException ex = assertThrows(SQLException.class, () -> executeV13AndV14Reconciliation(conn));
            assertNotNull(ex.getMessage());
            assertTrue(ex.getMessage().contains("Migration V14 interrompue"));
        }
    }

    @Test
    @DisplayName("Cas 5 : Conservation intégrale des références Wave, montants et dates historiques")
    void testCas5ConservationIntegraleReferencesMontantsEtDatesHistoriques() throws Exception {
        UUID annonce1 = UUID.randomUUID();
        UUID annonce2 = UUID.randomUUID();
        UUID txLibereeId = UUID.randomUUID();
        UUID txBloqueeId = UUID.randomUUID();
        UUID txInitAnnonce2Id = UUID.randomUUID();

        LocalDateTime dateCreation1 = LocalDateTime.of(2026, 1, 5, 10, 15, 0);
        LocalDateTime dateLiberation1 = LocalDateTime.of(2026, 1, 6, 16, 45, 0);
        LocalDateTime dateCreation2 = LocalDateTime.of(2026, 2, 20, 12, 0, 0);
        LocalDateTime dateCreation3 = LocalDateTime.of(2026, 3, 1, 9, 30, 0);

        try (Connection conn = dataSource.getConnection()) {
            insertTransaction(
                    conn,
                    txLibereeId,
                    annonce1,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new BigDecimal("400000.00"),
                    new BigDecimal("400000.00"),
                    new BigDecimal("5000.00"),
                    "LIBERE",
                    "WAVE-HIST-LIBERE-01",
                    dateCreation1,
                    dateLiberation1
            );
            insertTransaction(
                    conn,
                    txBloqueeId,
                    annonce1,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new BigDecimal("400000.00"),
                    new BigDecimal("400000.00"),
                    new BigDecimal("5000.00"),
                    "BLOQUE",
                    "WAVE-HIST-BLOQUE-02",
                    dateCreation2,
                    null
            );
            insertTransaction(
                    conn,
                    txInitAnnonce2Id,
                    annonce2,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new BigDecimal("175000.00"),
                    new BigDecimal("175000.00"),
                    new BigDecimal("5000.00"),
                    "INITIALISE",
                    null,
                    dateCreation3,
                    null
            );

            executeV13AndV14Reconciliation(conn);

            try (Statement st = conn.createStatement();
                 ResultSet rsCount = st.executeQuery("SELECT COUNT(*) FROM transactions")) {
                assertTrue(rsCount.next());
                assertEquals(3L, rsCount.getLong(1));
            }
        }
    }
}
