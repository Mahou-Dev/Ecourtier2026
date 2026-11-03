package com.ecourtier.domain.utilisateur.repository;

import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class UtilisateurSpecification {

    private UtilisateurSpecification() {
    }

    public static Specification<UtilisateurEntity> filterUtilisateurs(
            Role role,
            Boolean actif,
            String recherche
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }

            if (actif != null) {
                predicates.add(cb.equal(root.get("actif"), actif));
            }

            if (recherche != null && !recherche.trim().isEmpty()) {
                String pattern = "%" + recherche.trim().toLowerCase() + "%";
                Predicate nomMatch = cb.like(cb.lower(root.get("nom")), pattern);
                Predicate prenomMatch = cb.like(cb.lower(root.get("prenom")), pattern);
                Predicate emailMatch = cb.like(cb.lower(root.get("email")), pattern);
                Predicate telMatch = cb.like(cb.lower(root.get("telephone")), pattern);
                predicates.add(cb.or(nomMatch, prenomMatch, emailMatch, telMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
