package com.ecourtier.domain.favori.entity;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "favoris", uniqueConstraints = {
    @UniqueConstraint(name = "uq_favori_utilisateur_annonce", columnNames = {"utilisateur_id", "annonce_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FavoriEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private UtilisateurEntity utilisateur;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "annonce_id", nullable = false)
    private AnnonceEntity annonce;

    @Column(name = "date_ajout", nullable = false, updatable = false)
    private LocalDateTime dateAjout;

    @PrePersist
    public void prePersist() {
        if (dateAjout == null) {
            dateAjout = LocalDateTime.now();
        }
    }
}
