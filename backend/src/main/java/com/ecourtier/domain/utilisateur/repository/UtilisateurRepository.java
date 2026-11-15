package com.ecourtier.domain.utilisateur.repository;

import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UtilisateurRepository extends JpaRepository<UtilisateurEntity, UUID>, JpaSpecificationExecutor<UtilisateurEntity> {
    Optional<UtilisateurEntity> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByRole(Role role);
    long countByRoleAndActifTrue(Role role);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE UtilisateurEntity u SET u.tokenVersion = u.tokenVersion + 1 WHERE u.id = :id")
    int incrementTokenVersion(@Param("id") UUID id);
}
