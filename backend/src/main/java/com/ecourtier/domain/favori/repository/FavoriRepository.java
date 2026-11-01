package com.ecourtier.domain.favori.repository;

import com.ecourtier.domain.favori.entity.FavoriEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Page<FavoriEntity> findByUtilisateurIdOrderByDateAjoutDesc(UUID utilisateurId, Pageable pageable);

    @Query("SELECT f.annonce.id FROM FavoriEntity f WHERE f.utilisateur.id = :utilisateurId")
    List<UUID> findAnnonceIdsByUtilisateurId(@Param("utilisateurId") UUID utilisateurId);

    long countByUtilisateurId(UUID utilisateurId);
}
