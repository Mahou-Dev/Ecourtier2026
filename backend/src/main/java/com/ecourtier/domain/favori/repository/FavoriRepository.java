package com.ecourtier.domain.favori.repository;

import com.ecourtier.domain.favori.entity.FavoriEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FavoriRepository extends JpaRepository<FavoriEntity, UUID> {

    boolean existsByUtilisateurIdAndAnnonceId(UUID utilisateurId, UUID annonceId);

    Optional<FavoriEntity> findByUtilisateurIdAndAnnonceId(UUID utilisateurId, UUID annonceId);

    @EntityGraph(attributePaths = {"annonce", "annonce.utilisateur"})
    @Query("SELECT f FROM FavoriEntity f WHERE f.utilisateur.id = :utilisateurId AND f.annonce.supprime = false ORDER BY f.dateAjout DESC")
    Page<FavoriEntity> findByUtilisateurIdOrderByDateAjoutDesc(@Param("utilisateurId") UUID utilisateurId, Pageable pageable);

    @Query("SELECT f.annonce.id FROM FavoriEntity f WHERE f.utilisateur.id = :utilisateurId AND f.annonce.supprime = false")
    List<UUID> findAnnonceIdsByUtilisateurId(@Param("utilisateurId") UUID utilisateurId);

    @Query("SELECT COUNT(f) FROM FavoriEntity f WHERE f.utilisateur.id = :utilisateurId AND f.annonce.supprime = false")
    long countByUtilisateurId(@Param("utilisateurId") UUID utilisateurId);
}
