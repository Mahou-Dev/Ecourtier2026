package com.ecourtier.domain.transaction.entity;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "annonce_id", nullable = false)
    private AnnonceEntity annonce;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "locataire_id", nullable = false)
    private UtilisateurEntity locataire;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proprietaire_id", nullable = false)
    private UtilisateurEntity proprietaire;

    @Column(name = "montant_caution", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantCaution;

    @Column(name = "montant_premier_loyer", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal montantPremierLoyer = BigDecimal.ZERO;

    @Column(name = "frais_dossier", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal fraisDossier = BigDecimal.valueOf(5000);

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_sequestre", nullable = false, length = 20)
    @Builder.Default
    private StatutSequestre statutSequestre = StatutSequestre.INITIALISE;

    @Column(name = "reference_wave", length = 100)
    private String referenceWave;

    @Column(name = "date_paiement")
    private LocalDateTime datePaiement;

    @Column(name = "reference_remboursement_wave", length = 100)
    private String referenceRemboursementWave;

    @Column(name = "montant_rembourse", precision = 15, scale = 2)
    private BigDecimal montantRembourse;

    @Column(name = "date_remboursement")
    private LocalDateTime dateRemboursement;

    @Column(name = "motif_remboursement", length = 500)
    private String motifRemboursement;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_bail", nullable = false, length = 20)
    @Builder.Default
    private StatutBail statutBail = StatutBail.NON_SIGNE;

    @Column(name = "date_signature_locataire")
    private LocalDateTime dateSignatureLocataire;

    @Column(name = "date_signature_proprietaire")
    private LocalDateTime dateSignatureProprietaire;

    @Column(name = "date_liberation")
    private LocalDateTime dateLiberation;

    @Column(name = "pdf_bail_path", length = 500)
    private String pdfBailPath;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_expiration")
    private LocalDateTime dateExpiration;

    @Column(name = "date_cloture")
    private LocalDateTime dateCloture;

    @Column(name = "motif_cloture", length = 500)
    private String motifCloture;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_document_bail", nullable = false, length = 30)
    @Builder.Default
    private StatutDocumentBail statutDocumentBail = StatutDocumentBail.NON_GENERE;

    @Column(name = "pdf_bail_erreur", length = 1000)
    private String pdfBailErreur;

    @Column(name = "pdf_bail_tentatives", nullable = false)
    @Builder.Default
    private Integer pdfBailTentatives = 0;

    @Column(name = "pdf_bail_derniere_tentative")
    private LocalDateTime pdfBailDerniereTentative;

    @Column(name = "dossier_actif", nullable = false)
    @Builder.Default
    private Boolean dossierActif = Boolean.TRUE;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @PrePersist
    protected void onCreate() {
        if (dateCreation == null) {
            dateCreation = LocalDateTime.now(ZoneId.of("Africa/Dakar"));
        }
        if (fraisDossier == null) {
            fraisDossier = BigDecimal.valueOf(5000);
        }
        if (montantPremierLoyer == null) {
            montantPremierLoyer = BigDecimal.ZERO;
        }
        if (statutSequestre == null) {
            statutSequestre = StatutSequestre.INITIALISE;
        }
        if (statutBail == null) {
            statutBail = StatutBail.NON_SIGNE;
        }
        if (statutDocumentBail == null) {
            statutDocumentBail = StatutDocumentBail.NON_GENERE;
        }
        if (pdfBailTentatives == null) {
            pdfBailTentatives = 0;
        }
        if (dossierActif == null) {
            dossierActif = Boolean.TRUE;
        }
        if (version == null) {
            version = 0L;
        }
    }

    public BigDecimal getMontantTotalSequestre() {
        BigDecimal caution = montantCaution != null ? montantCaution : BigDecimal.ZERO;
        BigDecimal premierLoyer = montantPremierLoyer != null ? montantPremierLoyer : BigDecimal.ZERO;
        BigDecimal frais = fraisDossier != null ? fraisDossier : BigDecimal.ZERO;
        return caution.add(premierLoyer).add(frais);
    }

    public BigDecimal getMontantVerseProprietaire() {
        BigDecimal caution = montantCaution != null ? montantCaution : BigDecimal.ZERO;
        BigDecimal premierLoyer = montantPremierLoyer != null ? montantPremierLoyer : BigDecimal.ZERO;
        return caution.add(premierLoyer);
    }
}
