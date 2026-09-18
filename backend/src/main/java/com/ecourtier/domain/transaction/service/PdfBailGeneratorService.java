package com.ecourtier.domain.transaction.service;

import com.ecourtier.domain.transaction.entity.TransactionEntity;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
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

    public String generatePdfBail(TransactionEntity transaction) {
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String filename = "bail_" + transaction.getId() + ".pdf";
            Path filePath = uploadPath.resolve(filename);

            Document document = new Document();
            PdfWriter.getInstance(document, new FileOutputStream(filePath.toFile()));
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLACK);
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.DARK_GRAY);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.BLACK);

            document.add(new Paragraph("CONTRAT DE BAIL NUMÉRIQUE E-COURTIER SÉNÉGAL", titleFont));
            document.add(new Paragraph("================================================", titleFont));
            document.add(new Paragraph(" ", bodyFont));

            document.add(new Paragraph("1. PARTIES PRENANTES", sectionFont));
            document.add(new Paragraph("Propriétaire / Bailleur : " +
                    (transaction.getProprietaire() != null ? transaction.getProprietaire().getPrenom() + " " + transaction.getProprietaire().getNom() + " (" + transaction.getProprietaire().getEmail() + ")" : "N/A"), bodyFont));
            document.add(new Paragraph("Locataire : " +
                    (transaction.getLocataire() != null ? transaction.getLocataire().getPrenom() + " " + transaction.getLocataire().getNom() + " (" + transaction.getLocataire().getEmail() + ")" : "N/A"), bodyFont));
            document.add(new Paragraph(" ", bodyFont));

            document.add(new Paragraph("2. BIEN IMMOBILIER", sectionFont));
            document.add(new Paragraph("Titre de l'annonce : " + transaction.getAnnonce().getTitre(), bodyFont));
            document.add(new Paragraph("Type de bien : " + transaction.getAnnonce().getType(), bodyFont));
            document.add(new Paragraph("Localisation : " + transaction.getAnnonce().getQuartier() + ", " + transaction.getAnnonce().getVille(), bodyFont));
            document.add(new Paragraph(" ", bodyFont));

            document.add(new Paragraph("3. CONDITIONS FINANCIÈRES", sectionFont));
            document.add(new Paragraph("Montant de la caution : " + transaction.getMontantCaution() + " FCFA", bodyFont));
            document.add(new Paragraph("Frais de dossier plateforme : " + transaction.getFraisDossier() + " FCFA", bodyFont));
            document.add(new Paragraph("Référence paiement Wave : " + (transaction.getReferenceWave() != null ? transaction.getReferenceWave() : "En attente"), bodyFont));
            document.add(new Paragraph(" ", bodyFont));

            document.add(new Paragraph("4. SIGNATURES NUMÉRIQUES", sectionFont));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            document.add(new Paragraph("Signature Locataire : " +
                    (transaction.getDateSignatureLocataire() != null ? "Signé le " + transaction.getDateSignatureLocataire().format(formatter) : "Non signé"), bodyFont));
            document.add(new Paragraph("Signature Propriétaire : " +
                    (transaction.getDateSignatureProprietaire() != null ? "Signé le " + transaction.getDateSignatureProprietaire().format(formatter) : "Non signé"), bodyFont));
            document.add(new Paragraph("Statut du séquestre : " + transaction.getStatutSequestre(), bodyFont));

            document.close();
            log.info("PDF du bail généré avec succès à {}", filePath.toAbsolutePath());

            return filePath.toString();
        } catch (Exception e) {
            log.error("Erreur lors de la génération du PDF du bail", e);
            throw new RuntimeException("Échec de génération du PDF de bail", e);
        }
    }
}
