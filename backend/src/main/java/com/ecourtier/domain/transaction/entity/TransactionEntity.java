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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "annonce_id", nullable = false)
    private AnnonceEntity annonce;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "locataire_acheteur_id", nullable = false)
    private UtilisateurEntity locataireAcheteur;

    @Column(name = "montant_caution", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantCaution;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_sequestre", nullable = false, length = 30)
    private StatutSequestre statutSequestre = StatutSequestre.EN_ATTENTE;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_bail", nullable = false, length = 30)
    private StatutBail statutBail = StatutBail.NON_SIGNE;

    @Column(name = "frais_dossier", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal fraisDossier = new BigDecimal("5000.00");

    @Column(name = "reference_wave", length = 100)
    private String referenceWave;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_liberation")
    private LocalDateTime dateLiberation;

    @PrePersist
    public void prePersist() {
        if (dateCreation == null) {
            dateCreation = LocalDateTime.now();
        }
        if (fraisDossier == null) {
            fraisDossier = new BigDecimal("5000.00");
        }
    }
}
