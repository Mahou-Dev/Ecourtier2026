package com.ecourtier.domain.annonce.entity;

import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "annonces")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnonceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String titre;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TypeBien type;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_transaction", nullable = false, length = 20)
    private ModeTransaction modeTransaction;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal prix;

    @Column(nullable = false)
    private Double superficie;

    @Column(name = "nombre_pieces", nullable = false)
    private Integer nombrePieces;

    @Column(nullable = false, length = 100)
    private String quartier;

    @Column(nullable = false, length = 100)
    private String ville;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false)
    @Builder.Default
    private boolean meuble = false;

    @Column(name = "animaux_acceptes", nullable = false)
    @Builder.Default
    private boolean animauxAcceptes = false;

    @Column(name = "lien_visite_360", length = 512)
    private String lienVisite360;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_verification", nullable = false, length = 30)
    @Builder.Default
    private StatutVerification statutVerification = StatutVerification.EN_ATTENTE;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_publication", nullable = false, length = 30)
    @Builder.Default
    private StatutPublication statutPublication = StatutPublication.DISPONIBLE;

    @Column(nullable = false)
    @Builder.Default
    private boolean supprime = false;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private UtilisateurEntity utilisateur;

    @OneToMany(mappedBy = "annonce", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PhotoAnnonceEntity> photos = new ArrayList<>();

    @ElementCollection(targetClass = Equipement.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "equipements_annonces", joinColumns = @JoinColumn(name = "annonce_id"))
    @Column(name = "equipement", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<Equipement> equipements = new HashSet<>();

    @ElementCollection(targetClass = ProximiteTag.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "proximites_annonces", joinColumns = @JoinColumn(name = "annonce_id"))
    @Column(name = "proximite", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<ProximiteTag> proximites = new HashSet<>();

    @OneToMany(mappedBy = "annonce", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DocumentVerificationEntity> documentsVerification = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (dateCreation == null) {
            dateCreation = LocalDateTime.now();
        }
        if (statutVerification == null) {
            statutVerification = StatutVerification.EN_ATTENTE;
        }
        if (statutPublication == null) {
            statutPublication = StatutPublication.DISPONIBLE;
        }
    }
}
