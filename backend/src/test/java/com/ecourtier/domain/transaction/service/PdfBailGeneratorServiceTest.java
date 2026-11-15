package com.ecourtier.domain.transaction.service;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.transaction.entity.StatutBail;
import com.ecourtier.domain.transaction.entity.StatutSequestre;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfBailGeneratorServiceTest {

    private final PdfBailGeneratorService pdfBailGeneratorService = new PdfBailGeneratorService();
    private final Path uploadDir = Paths.get("uploads/baux/");
    private UUID generatedTxId;

    @AfterEach
    void cleanup() throws Exception {
        if (generatedTxId != null && Files.exists(uploadDir)) {
            Files.deleteIfExists(uploadDir.resolve("bail_" + generatedTxId + ".pdf"));
            Files.deleteIfExists(uploadDir.resolve("reservation_achat_" + generatedTxId + ".pdf"));
            try (var stream = Files.list(uploadDir)) {
                stream.filter(p -> p.getFileName().toString().contains(generatedTxId.toString()))
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (Exception ignored) {
                            }
                        });
            }
        }
    }

    @Test
    @DisplayName("Phase 3 C: Écriture atomique d'un PDF valide sans fichier .tmp résiduel")
    void testGeneratePdfBailAtomicWriteSuccess() throws Exception {
        generatedTxId = UUID.randomUUID();
        UtilisateurEntity proprio = UtilisateurEntity.builder()
                .prenom("Moussa").nom("Diop").email("moussa@test.sn").role(Role.PROPRIETAIRE).build();
        UtilisateurEntity locataire = UtilisateurEntity.builder()
                .prenom("Awa").nom("Ndiaye").email("awa@test.sn").role(Role.LOCATAIRE).build();
        AnnonceEntity annonce = AnnonceEntity.builder()
                .id(UUID.randomUUID())
                .titre("Appartement Mermoz")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .quartier("Mermoz")
                .ville("Dakar")
                .prix(BigDecimal.valueOf(350000))
                .build();

        TransactionEntity tx = TransactionEntity.builder()
                .id(generatedTxId)
                .annonce(annonce)
                .proprietaire(proprio)
                .locataire(locataire)
                .montantCaution(BigDecimal.valueOf(350000))
                .montantPremierLoyer(BigDecimal.valueOf(350000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.LIBERE)
                .statutBail(StatutBail.SIGNE_COMPLET)
                .dateSignatureLocataire(LocalDateTime.of(2026, 11, 14, 10, 0))
                .dateSignatureProprietaire(LocalDateTime.of(2026, 11, 14, 10, 5))
                .build();

        String path = pdfBailGeneratorService.generatePdfBail(tx);
        Path generatedPath = Path.of(path);

        assertTrue(Files.isRegularFile(generatedPath));
        assertTrue(Files.size(generatedPath) > 0);
        byte[] header = Files.readAllBytes(generatedPath);
        assertEquals("%PDF", new String(header, 0, 4));

        List<Path> tmpResiduals;
        try (var stream = Files.list(uploadDir)) {
            tmpResiduals = stream
                    .filter(p -> p.getFileName().toString().contains(generatedTxId.toString())
                            && p.getFileName().toString().endsWith(".tmp"))
                    .toList();
        }
        assertTrue(tmpResiduals.isEmpty(), "Aucun fichier temporaire .tmp ne doit rester après écriture atomique");
    }

    @Test
    @DisplayName("Phase 3 C: En cas d'erreur de génération, aucun fichier PDF corrompu ni .tmp ne subsiste")
    void testGeneratePdfBailFailureLeavesNoCorruptFileOrTmp() throws Exception {
        generatedTxId = UUID.randomUUID();
        TransactionEntity brokenTx = TransactionEntity.builder()
                .id(generatedTxId)
                .annonce(null) // Provoque NPE pendant la rédaction du contenu PDF
                .statutSequestre(StatutSequestre.LIBERE)
                .statutBail(StatutBail.SIGNE_COMPLET)
                .build();

        assertThrows(RuntimeException.class, () -> pdfBailGeneratorService.generatePdfBail(brokenTx));

        Path expectedPdf = uploadDir.resolve("bail_" + generatedTxId + ".pdf");
        assertFalse(Files.exists(expectedPdf), "Aucun fichier PDF partiel/corrompu ne doit être promu après échec");

        if (Files.exists(uploadDir)) {
            List<Path> residuals;
            try (var stream = Files.list(uploadDir)) {
                residuals = stream
                        .filter(p -> p.getFileName().toString().contains(generatedTxId.toString()))
                        .toList();
            }
            assertTrue(residuals.isEmpty(), "Tous les fichiers temporaires .tmp doivent être nettoyés dans finally");
        }
    }
}
