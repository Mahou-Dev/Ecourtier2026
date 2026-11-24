package com.ecourtier.domain.transaction.dto;

import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.transaction.entity.StatutBail;
import com.ecourtier.domain.transaction.entity.StatutDocumentBail;
import com.ecourtier.domain.transaction.entity.StatutSequestre;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponseDTO {

    private UUID id;
    private UUID annonceId;
    private String annonceTitre;

    @Schema(description = "Mode de transaction de l'annonce (LOCATION ou VENTE)")
    private ModeTransaction modeTransaction;

    @Schema(description = "Prix affiché sur l'annonce (loyer mensuel ou prix de vente)")
    private BigDecimal annoncePrix;

    @Schema(description = "Pourcentage d'acompte appliqué en mode VENTE (ex: 10)")
    private Integer acomptePourcentage;

    private UUID locataireId;
    private String locataireNomComplet;
    private UUID proprietaireId;
    private String proprietaireNomComplet;
    private BigDecimal montantCaution;
    private BigDecimal montantPremierLoyer;
    private BigDecimal fraisDossier;
    private BigDecimal montantTotalSequestre;
    private BigDecimal montantVerseProprietaire;
    private StatutSequestre statutSequestre;
    private String referenceWave;
    @Schema(description = "Prestataire de paiement utilisé (PAYTECH ou MOCK_LOCAL)", example = "PAYTECH")
    private String prestatairePaiement;
    @Schema(description = "Moyen de paiement ciblé", example = "Wave")
    private String moyenPaiement;
    @Schema(description = "Indique si le paiement repose sur une simulation locale (true) ou un prestataire externe (false)")
    private Boolean modeSimulationPaiement;
    @Schema(description = "Indique si le zéro débit réel est garanti avant redirection (false en Sandbox PayTech où 100-150 FCFA peuvent être débités)")
    private Boolean zeroDebitGaranti;
    @Schema(description = "Référence commande PayTech unique (ref_command)")
    private String refCommandPaytech;
    @Schema(description = "Token de paiement externe retourné par le prestataire (ex: token PayTech)")
    private String tokenPaiementExterne;
    @Schema(description = "URL de redirection retournée par le prestataire pour finaliser le paiement")
    private String redirectUrlPaiement;
    @Schema(description = "Statut détaillé du paiement prestataire (EN_ATTENTE_CONFIRMATION, CONFIRME, CONFIRME_SIMULE, ANNULE, ECHOUE)")
    private String statutPaiementExterne;
    @Schema(description = "Statut du transfert propriétaire lors de la libération logique (NON_INITIE, TRANSFERT_SIMULE, CONFIRME, ECHOUE)")
    private String statutTransfertProprietaire;
    @Schema(description = "Référence du transfert propriétaire (clairement préfixée SIM-TRANSFER- en simulation locale)")
    private String referenceTransfertProprietaire;
    @Schema(description = "Date d'enregistrement du transfert propriétaire (ou simulation)")
    private LocalDateTime dateTransfertProprietaire;
    @Schema(description = "Empreinte SHA-256 canonique des termes contractuels figés avant signature")
    private String bailDocumentSha256;
    @Schema(description = "Trace SHA-256 de la signature applicative du locataire/acheteur")
    private String signatureLocataireHash;
    @Schema(description = "Trace SHA-256 de la signature applicative du propriétaire/vendeur")
    private String signatureProprietaireHash;
    private LocalDateTime datePaiement;
    private String referenceRemboursementWave;
    private BigDecimal montantRembourse;
    private LocalDateTime dateRemboursement;
    private String motifRemboursement;
    private StatutBail statutBail;
    private LocalDateTime dateSignatureLocataire;
    private LocalDateTime dateSignatureProprietaire;
    private LocalDateTime dateLiberation;
    private String pdfBailPath;
    @Schema(description = "État de génération du PDF du contrat de bail", example = "GENERE")
    private StatutDocumentBail statutDocumentBail;
    @Schema(description = "Dernier message d'erreur technique lors de la génération PDF (en cas d'échec)")
    private String pdfBailErreur;
    @Schema(description = "Nombre de tentatives de génération du PDF du bail", example = "1")
    private Integer pdfBailTentatives;
    @Schema(description = "Date/heure Africa/Dakar de la dernière tentative de génération PDF")
    private LocalDateTime pdfBailDerniereTentative;
    private LocalDateTime dateCreation;
    private Boolean dossierActif;
    private LocalDateTime dateExpiration;
    private LocalDateTime dateCloture;
    private String motifCloture;
}
