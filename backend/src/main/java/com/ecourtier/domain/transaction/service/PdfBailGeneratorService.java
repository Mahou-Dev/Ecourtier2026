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
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class PdfBailGeneratorService {

    private static final String UPLOAD_DIR = "uploads/baux/";

    @Value("${ecourtier.vente.acompte-pourcentage:10}")
    private int acomptePourcentage = 10;

    public String generatePdfBail(TransactionEntity transaction) {
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

            Document document = new Document();
            PdfWriter.getInstance(document, new FileOutputStream(filePath.toFile()));
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 17, Color.BLACK);
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Color.DARK_GRAY);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.BLACK);
            Font noticeFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, Color.DARK_GRAY);

            if (isVente) {
                document.add(new Paragraph("RÉCAPITULATIF DE RÉSERVATION D'ACHAT", titleFont));
                document.add(new Paragraph("================================================", titleFont));
                document.add(new Paragraph("Document récapitulatif généré par la plateforme ; il ne constitue pas un acte de vente", noticeFont));
            } else {
                document.add(new Paragraph("CONTRAT DE BAIL NUMÉRIQUE E-COURTIER SÉNÉGAL", titleFont));
                document.add(new Paragraph("================================================", titleFont));
                document.add(new Paragraph("Document récapitulatif généré par la plateforme", noticeFont));
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

            document.add(new Paragraph("3. CONDITIONS FINANCIÈRES", sectionFont));
            if (isVente) {
                document.add(new Paragraph("Acompte de réservation (" + acomptePourcentage + " % du prix) : " + transaction.getMontantCaution() + " FCFA", bodyFont));
                document.add(new Paragraph("Frais de dossier plateforme : " + transaction.getFraisDossier() + " FCFA", bodyFont));
                document.add(new Paragraph("Montant total consigné en séquestre : " + transaction.getMontantTotalSequestre() + " FCFA", bodyFont));
                document.add(new Paragraph("Montant versé au vendeur à la libération : " + transaction.getMontantVerseProprietaire() + " FCFA", bodyFont));
            } else {
                document.add(new Paragraph("Montant de la caution : " + transaction.getMontantCaution() + " FCFA", bodyFont));
                document.add(new Paragraph("Montant du premier loyer : " + transaction.getMontantPremierLoyer() + " FCFA", bodyFont));
                document.add(new Paragraph("Frais de dossier plateforme : " + transaction.getFraisDossier() + " FCFA", bodyFont));
                document.add(new Paragraph("Montant total consigné en séquestre : " + transaction.getMontantTotalSequestre() + " FCFA", bodyFont));
                document.add(new Paragraph("Montant versé au bailleur à la libération : " + transaction.getMontantVerseProprietaire() + " FCFA", bodyFont));
            }
            document.add(new Paragraph("Référence paiement Wave : " + (transaction.getReferenceWave() != null ? transaction.getReferenceWave() : "En attente"), bodyFont));
            document.add(new Paragraph(" ", bodyFont));

            document.add(new Paragraph("4. SIGNATURES NUMÉRIQUES", sectionFont));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            String labelSigClient = isVente ? "Signature Acheteur : " : "Signature Locataire : ";
            String labelSigProprio = isVente ? "Signature Vendeur : " : "Signature Propriétaire : ";
            document.add(new Paragraph(labelSigClient +
                    (transaction.getDateSignatureLocataire() != null ? "Signé le " + transaction.getDateSignatureLocataire().format(formatter) : "Non signé"), bodyFont));
            document.add(new Paragraph(labelSigProprio +
                    (transaction.getDateSignatureProprietaire() != null ? "Signé le " + transaction.getDateSignatureProprietaire().format(formatter) : "Non signé"), bodyFont));
            document.add(new Paragraph("Statut du séquestre : " + transaction.getStatutSequestre(), bodyFont));

            document.close();
            log.info("PDF récapitulatif ({}) généré avec succès à {}", isVente ? "VENTE" : "LOCATION", filePath.toAbsolutePath());

            return filePath.toString();
        } catch (Exception e) {
            log.error("Erreur lors de la génération du PDF récapitulatif", e);
            throw new RuntimeException("Échec de génération du PDF récapitulatif", e);
        }
    }
}
