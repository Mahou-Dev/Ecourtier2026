package com.ecourtier.domain.annonce.repository;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.ProximiteTag;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class AnnonceSpecification {

    public static Specification<AnnonceEntity> filterAnnonces(
            BigDecimal budgetMin,
            BigDecimal budgetMax,
            TypeBien type,
            ModeTransaction modeTransaction,
            Double superficieMin,
            Integer nombrePieces,
            String quartier,
            String ville,
            Boolean meuble,
            Boolean animauxAcceptes,
            StatutVerification statutVerification,
            ProximiteTag proximite
    ) {
        return filterAnnonces(budgetMin, budgetMax, type, modeTransaction, superficieMin,
                nombrePieces, quartier, ville, meuble, animauxAcceptes, statutVerification, proximite, false);
    }

    public static Specification<AnnonceEntity> filterAnnonces(
            BigDecimal budgetMin,
            BigDecimal budgetMax,
            TypeBien type,
            ModeTransaction modeTransaction,
            Double superficieMin,
            Integer nombrePieces,
            String quartier,
            String ville,
            Boolean meuble,
            Boolean animauxAcceptes,
            StatutVerification statutVerification,
            ProximiteTag proximite,
            Boolean includeIndisponibles
    ) {
        return filterAnnonces(budgetMin, budgetMax, type, modeTransaction, superficieMin,
                nombrePieces, quartier, ville, meuble, animauxAcceptes, statutVerification, proximite, includeIndisponibles, null);
    }

    public static Specification<AnnonceEntity> filterAnnonces(
            BigDecimal budgetMin,
            BigDecimal budgetMax,
            TypeBien type,
            ModeTransaction modeTransaction,
            Double superficieMin,
            Integer nombrePieces,
            String quartier,
            String ville,
            Boolean meuble,
            Boolean animauxAcceptes,
            StatutVerification statutVerification,
            ProximiteTag proximite,
            Boolean includeIndisponibles,
            String auteurEmail
    ) {
        return filterAnnonces(
                budgetMin, budgetMax,
                type != null ? List.of(type) : null,
                modeTransaction, superficieMin, nombrePieces,
                quartier, ville, meuble, animauxAcceptes,
                statutVerification,
                proximite != null ? List.of(proximite) : null,
                includeIndisponibles, auteurEmail
        );
    }

    public static Specification<AnnonceEntity> filterAnnonces(
            BigDecimal budgetMin,
            BigDecimal budgetMax,
            List<TypeBien> types,
            ModeTransaction modeTransaction,
            Double superficieMin,
            Integer nombrePieces,
            String quartier,
            String ville,
            Boolean meuble,
            Boolean animauxAcceptes,
            StatutVerification statutVerification,
            List<ProximiteTag> proximites,
            Boolean includeIndisponibles,
            String auteurEmail
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Exclude soft deleted items by default
            predicates.add(criteriaBuilder.equal(root.get("supprime"), false));

            // Filter by author email if requested (for landlord's own listings)
            if (auteurEmail != null && !auteurEmail.trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("utilisateur").get("email"), auteurEmail));
            } else if (!Boolean.TRUE.equals(includeIndisponibles)) {
                // Exclude unavailable (LOUEE, VENDUE, SUSPENDUE) unless includeIndisponibles is true
                predicates.add(root.get("statutPublication").in(StatutPublication.LOUEE, StatutPublication.VENDUE, StatutPublication.SUSPENDUE).not());
            }

            // 2. Budget Min & Max
            if (budgetMin != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("prix"), budgetMin));
            }
            if (budgetMax != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("prix"), budgetMax));
            }

            // 3. Types de bien (Support choix unique ou multiple)
            if (types != null && !types.isEmpty()) {
                if (types.size() == 1) {
                    predicates.add(criteriaBuilder.equal(root.get("type"), types.get(0)));
                } else {
                    predicates.add(root.get("type").in(types));
                }
            }

            // 4. Mode Transaction (LOCATION / VENTE)
            if (modeTransaction != null) {
                predicates.add(criteriaBuilder.equal(root.get("modeTransaction"), modeTransaction));
            }

            // 5. Superficie Min
            if (superficieMin != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("superficie"), superficieMin));
            }

            // 6. Nombre de Pièces
            if (nombrePieces != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("nombrePieces"), nombrePieces));
            }

            // 7. Quartier (Case-insensitive LIKE)
            if (quartier != null && !quartier.trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("quartier")),
                        "%" + quartier.trim().toLowerCase() + "%"
                ));
            }

            // 8. Ville (Case-insensitive LIKE)
            if (ville != null && !ville.trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("ville")),
                        "%" + ville.trim().toLowerCase() + "%"
                ));
            }

            // 9. Meublé
            if (meuble != null) {
                predicates.add(criteriaBuilder.equal(root.get("meuble"), meuble));
            }

            // 10. Animaux Acceptés
            if (animauxAcceptes != null) {
                predicates.add(criteriaBuilder.equal(root.get("animauxAcceptes"), animauxAcceptes));
            }

            // 11. Statut Vérification (e.g., VERIFIEE pour le badge)
            if (statutVerification != null) {
                predicates.add(criteriaBuilder.equal(root.get("statutVerification"), statutVerification));
            }

            // 12. Tag(s) de Proximité (Join ElementCollection)
            if (proximites != null && !proximites.isEmpty()) {
                Join<AnnonceEntity, ProximiteTag> proximiteJoin = root.join("proximites");
                if (proximites.size() == 1) {
                    predicates.add(criteriaBuilder.equal(proximiteJoin, proximites.get(0)));
                } else {
                    predicates.add(proximiteJoin.in(proximites));
                }
                if (query != null) {
                    query.distinct(true);
                }
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
