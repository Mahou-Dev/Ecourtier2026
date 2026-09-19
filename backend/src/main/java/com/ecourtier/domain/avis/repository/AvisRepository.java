package com.ecourtier.domain.avis.repository;

import com.ecourtier.domain.avis.entity.AvisEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AvisRepository extends JpaRepository<AvisEntity, UUID> {

    List<AvisEntity> findByAnnonceIdOrderByDateCreationDesc(UUID annonceId);

    boolean existsByAnnonceIdAndAuteurId(UUID annonceId, UUID auteurId);

    @Query("SELECT AVG(a.note) FROM AvisEntity a WHERE a.annonce.id = :annonceId")
    Double findAverageNoteByAnnonceId(@Param("annonceId") UUID annonceId);

    @Query("SELECT COUNT(a) FROM AvisEntity a WHERE a.annonce.id = :annonceId")
    Long countByAnnonceId(@Param("annonceId") UUID annonceId);

    @Query("SELECT AVG(a.note) FROM AvisEntity a WHERE a.annonce.utilisateur.id = :utilisateurId")
    Double findAverageNoteByUtilisateurId(@Param("utilisateurId") UUID utilisateurId);

    @Query("SELECT COUNT(a) FROM AvisEntity a WHERE a.annonce.utilisateur.id = :utilisateurId")
    Long countByUtilisateurId(@Param("utilisateurId") UUID utilisateurId);
}
