package com.ecourtier.domain.transaction.service;

import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;

@Service
@Slf4j
public class PdfBailGeneratorService {

    private static final String UPLOAD_DIR = "uploads/baux/";

    @Value("${ecourtier.vente.acompte-pourcentage:10}")
    private int acomptePourcentage = 10;

    /**
     * ESCROW-SIGN-002 : Empreinte SHA-256 déterministe des termes contractuels canoniques
     * figés avant signature afin de détecter toute modification ultérieure des montants ou parties.
     */
    public String computeCanonicalContractSha256(TransactionEntity tx) {
        if (tx == null) {
            return null;
        }
        String canonical = String.join("|",
                tx.getId() != null ? tx.getId().toString() : "",
                tx.getAnnonce() != null && tx.getAnnonce().getId() != null ? tx.getAnnonce().getId().toString() : "",
                tx.getAnnonce() != null && tx.getAnnonce().getModeTransaction() != null ? tx.getAnnonce().getModeTransaction().name() : "",
                tx.getAnnonce() != null && tx.getAnnonce().getTitre() != null ? tx.getAnnonce().getTitre().trim() : "",
                tx.getAnnonce() != null && tx.getAnnonce().getQuartier() != null ? tx.getAnnonce().getQuartier().trim() : "",
                tx.getAnnonce() != null && tx.getAnnonce().getVille() != null ? tx.getAnnonce().getVille().trim() : "",
                tx.getAnnonce() != null ? normalizeAmount(tx.getAnnonce().getPrix()) : "0",
                tx.getLocataire() != null && tx.getLocataire().getId() != null ? tx.getLocataire().getId().toString() : "",
                tx.getLocataire() != null && tx.getLocataire().getEmail() != null ? tx.getLocataire().getEmail().trim().toLowerCase() : "",
                tx.getProprietaire() != null && tx.getProprietaire().getId() != null ? tx.getProprietaire().getId().toString() : "",
                tx.getProprietaire() != null && tx.getProprietaire().getEmail() != null ? tx.getProprietaire().getEmail().trim().toLowerCase() : "",
                normalizeAmount(tx.getMontantCaution()),
                normalizeAmount(tx.getMontantPremierLoyer()),
                normalizeAmount(tx.getFraisDossier()),
                tx.getReferenceWave() != null ? tx.getReferenceWave().trim() : ""
        );
        return sha256Hex(canonical);
    }

    /**
     * ESCROW-SIGN-002 : Trace SHA-256 applicative liant l'identité du signataire, l'horodatage
     * et l'empreinte canonique du contrat (ne constitue pas une signature électronique qualifiée).
     */
    public String computeSignatureTraceHash(
            UUID transactionId,
            String partyRole,
            String signerEmail,
            LocalDateTime signedAt,
            String contractSha256
    ) {
        String payload = String.join("|",
                transactionId != null ? transactionId.toString() : "",
                partyRole != null ? partyRole : "",
                signerEmail != null ? signerEmail.trim().toLowerCase() : "",
                signedAt != null ? signedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "",
                contractSha256 != null ? contractSha256 : ""
        );
        return sha256Hex(payload);
    }

    /**
     * Génère en mémoire un aperçu PDF pré-signature permettant aux parties de consulter
     * les conditions et l'empreinte SHA-256 avant de signer.
     */
    public byte[] generatePreviewPdfBytes(TransactionEntity transaction) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            writeContractPdf(transaction, baos, true);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Erreur lors de la génération de l'aperçu PDF pré-signature", e);
            throw new RuntimeException("Échec de génération de l'aperçu PDF pré-signature", e);
        }
    }

    public String generatePdfBail(TransactionEntity transaction) {
        Path tmpPath = null;
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            boolean isVente = transaction.getAnnonce() != null
                    && transaction.getAnnonce().getModeTransaction() == ModeTransaction.VENTE;

            String prefix = isVente ? "reservation_achat_" : "bail_";
            String filename = prefix + transaction.getId() + ".pdf";
            Path filePath = uploadPath.resolve(filename);
            tmpPath = uploadPath.resolve("." + filename + "." + UUID.randomUUID() + ".tmp");

            try (OutputStream out = Files.newOutputStream(tmpPath)) {
                writeContractPdf(transaction, out, false);
            }

            if (!Files.exists(tmpPath) || Files.size(tmpPath) <= 0L) {
                throw new IllegalStateException("Fichier PDF généré vide ou absent : " + tmpPath);
            }

            try {
                Files.move(tmpPath, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmpPath, filePath, StandardCopyOption.REPLACE_EXISTING);
            }

            if (!Files.exists(filePath) || Files.size(filePath) <= 0L) {
                throw new IllegalStateException("Fichier PDF final introuvable ou vide : " + filePath);
            }

            log.info("PDF récapitulatif ({}) généré avec succès à {}", isVente ? "VENTE" : "LOCATION", filePath.toAbsolutePath());
            return filePath.toString();
        } catch (Exception e) {
            log.error("Erreur lors de la génération du PDF récapitulatif", e);
            throw new RuntimeException("Échec de génération du PDF récapitulatif", e);
        } finally {
            if (tmpPath != null) {
                try {
                    Files.deleteIfExists(tmpPath);
                } catch (Exception cleanupEx) {
                    log.warn("Impossible de supprimer le fichier temporaire PDF {}: {}", tmpPath, cleanupEx.getMessage());
                }
            }
        }
    }

    private void writeContractPdf(TransactionEntity transaction, OutputStream out, boolean previewMode) {
        boolean isVente = transaction.getAnnonce() != null
                && transaction.getAnnonce().getModeTransaction() == ModeTransaction.VENTE;
        boolean simulated = Boolean.TRUE.equals(transaction.getModeSimulationPaiement());

        Document document = new Document();
        PdfWriter.getInstance(document, out);
        document.open();

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLACK);
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.DARK_GRAY);
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
        Font noticeFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.DARK_GRAY);

        if (previewMode) {
            document.add(new Paragraph("[APERÇU PRÉ-SIGNATURE — DOCUMENT NON FINALISÉ]", sectionFont));
            document.add(new Paragraph(" ", bodyFont));
        }

        if (isVente) {
            document.add(new Paragraph("RÉCAPITULATIF DE RÉSERVATION D'ACHAT", titleFont));
            document.add(new Paragraph("================================================", titleFont));
            document.add(new Paragraph("Document récapitulatif généré par la plateforme ; il ne constitue pas un acte de vente.", noticeFont));
        } else {
            document.add(new Paragraph("CONTRAT DE BAIL NUMÉRIQUE E-COURTIER SÉNÉGAL", titleFont));
            document.add(new Paragraph("================================================", titleFont));
            document.add(new Paragraph("Document récapitulatif généré par la plateforme.", noticeFont));
        }
        document.add(new Paragraph(" ", bodyFont));

        document.add(new Paragraph("1. PARTIES PRENANTES", sectionFont));
        String roleProprietaire = isVente ? "Vendeur / Propriétaire : " : "Propriétaire / Bailleur : ";
        String roleClient = isVente ? "Acheteur : " : "Locataire : ";
        document.add(new Paragraph(roleProprietaire +
                (transaction.getProprietaire() != null ? transaction.getProprietaire().getPrenom() + " " + transaction.getProprietaire().getNom() + " (" + transaction.getProprietaire().getEmail() + ")" : "N/A"), bodyFont));
        document.add(new Paragraph(roleClient +
                (transaction.getLocataire() != null ? transaction.getLocataire().getPrenom() + " " + transaction.getLocataire().getNom() + " (" + transaction.getLocataire().getEmail() + ")" : "N/A"), bodyFont));
        document.add(new Paragraph(" ", bodyFont));

        document.add(new Paragraph("2. BIEN IMMOBILIER", sectionFont));
        document.add(new Paragraph("Titre de l'annonce : " + transaction.getAnnonce().getTitre(), bodyFont));
        document.add(new Paragraph("Type de bien : " + transaction.getAnnonce().getType(), bodyFont));
        document.add(new Paragraph("Mode de transaction : " + transaction.getAnnonce().getModeTransaction(), bodyFont));
        document.add(new Paragraph("Localisation : " + transaction.getAnnonce().getQuartier() + ", " + transaction.getAnnonce().getVille(), bodyFont));
        if (isVente && transaction.getAnnonce().getPrix() != null) {
            document.add(new Paragraph("Prix de vente affiché : " + transaction.getAnnonce().getPrix() + " FCFA", bodyFont));
        }
        document.add(new Paragraph(" ", bodyFont));

        document.add(new Paragraph("3. CONDITIONS FINANCIÈRES ET PAIEMENT", sectionFont));
        if (isVente) {
            document.add(new Paragraph("Acompte de réservation (" + acomptePourcentage + " % du prix) : " + transaction.getMontantCaution() + " FCFA", bodyFont));
            document.add(new Paragraph("Frais de dossier plateforme : " + transaction.getFraisDossier() + " FCFA", bodyFont));
            document.add(new Paragraph("Montant total du dossier : " + transaction.getMontantTotalSequestre() + " FCFA", bodyFont));
            document.add(new Paragraph("Montant destiné au vendeur à la libération logique : " + transaction.getMontantVerseProprietaire() + " FCFA", bodyFont));
        } else {
            document.add(new Paragraph("Montant de la caution : " + transaction.getMontantCaution() + " FCFA", bodyFont));
            document.add(new Paragraph("Montant du premier loyer : " + transaction.getMontantPremierLoyer() + " FCFA", bodyFont));
            document.add(new Paragraph("Frais de dossier plateforme : " + transaction.getFraisDossier() + " FCFA", bodyFont));
            document.add(new Paragraph("Montant total du dossier : " + transaction.getMontantTotalSequestre() + " FCFA", bodyFont));
            document.add(new Paragraph("Montant destiné au bailleur à la libération logique : " + transaction.getMontantVerseProprietaire() + " FCFA", bodyFont));
        }
        String providerLabel = transaction.getPrestatairePaiement() != null ? transaction.getPrestatairePaiement() : "MOCK_LOCAL";
        String methodLabel = transaction.getMoyenPaiement() != null ? transaction.getMoyenPaiement() : "Wave";
        document.add(new Paragraph("Prestataire / Moyen de paiement : " + providerLabel + " (" + methodLabel + ")"
                + (simulated ? " — MODE SIMULATION LOCALE (AUCUN DÉBIT RÉEL)" : " — SANDBOX TEST"), bodyFont));
        document.add(new Paragraph("Référence paiement : " + (transaction.getReferenceWave() != null ? transaction.getReferenceWave() : "En attente"), bodyFont));
        if (transaction.getReferenceTransfertProprietaire() != null) {
            document.add(new Paragraph("Transfert propriétaire : " + transaction.getStatutTransfertProprietaire()
                    + " (Réf : " + transaction.getReferenceTransfertProprietaire() + " — simulation locale sans virement bancaire réel)", bodyFont));
        }
        document.add(new Paragraph("Mention légale : E-Courtier n'est pas un établissement séquestre réglementé.", noticeFont));
        document.add(new Paragraph(" ", bodyFont));

        document.add(new Paragraph("4. TRAÇABILITÉ DES SIGNATURES APPLICATIVES", sectionFont));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        String labelSigClient = isVente ? "Signature Acheteur : " : "Signature Locataire : ";
        String labelSigProprio = isVente ? "Signature Vendeur : " : "Signature Propriétaire : ";
        document.add(new Paragraph(labelSigClient +
                (transaction.getDateSignatureLocataire() != null ? "Enregistrée le " + transaction.getDateSignatureLocataire().format(formatter) : "Non signé"), bodyFont));
        if (transaction.getSignatureLocataireHash() != null) {
            document.add(new Paragraph("Trace SHA-256 Locataire : " + transaction.getSignatureLocataireHash(), noticeFont));
        }
        document.add(new Paragraph(labelSigProprio +
                (transaction.getDateSignatureProprietaire() != null ? "Enregistrée le " + transaction.getDateSignatureProprietaire().format(formatter) : "Non signé"), bodyFont));
        if (transaction.getSignatureProprietaireHash() != null) {
            document.add(new Paragraph("Trace SHA-256 Propriétaire : " + transaction.getSignatureProprietaireHash(), noticeFont));
        }
        String contractDigest = transaction.getBailDocumentSha256() != null
                ? transaction.getBailDocumentSha256()
                : computeCanonicalContractSha256(transaction);
        document.add(new Paragraph("Empreinte SHA-256 des termes contractuels : " + contractDigest, noticeFont));
        document.add(new Paragraph("Statut logique du dossier : " + transaction.getStatutSequestre(), bodyFont));
        document.add(new Paragraph("Avertissement : les traces SHA-256 et horodatages ci-dessus sont des preuves applicatives internes et ne constituent pas une signature électronique qualifiée par un tiers de confiance certifié.", noticeFont));

        document.close();
    }

    private String normalizeAmount(BigDecimal value) {
        return value != null ? value.stripTrailingZeros().toPlainString() : "0";
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Algorithme SHA-256 indisponible", e);
        }
    }
}
