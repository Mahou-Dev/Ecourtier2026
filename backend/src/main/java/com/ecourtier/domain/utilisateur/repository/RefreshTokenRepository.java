package com.ecourtier.domain.utilisateur.repository;

import com.ecourtier.domain.utilisateur.entity.RefreshTokenEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshTokenEntity r JOIN FETCH r.utilisateur WHERE r.jti = :jti")
    Optional<RefreshTokenEntity> findByJtiForUpdate(@Param("jti") UUID jti);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshTokenEntity r JOIN FETCH r.utilisateur WHERE r.tokenHash = :tokenHash")
    Optional<RefreshTokenEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<RefreshTokenEntity> findByFamilyId(UUID familyId);

    List<RefreshTokenEntity> findByUtilisateurIdAndRevokedAtIsNull(UUID utilisateurId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshTokenEntity r
            SET r.revokedAt = :now, r.revocationReason = :reason
            WHERE r.familyId = :familyId AND r.revokedAt IS NULL
            """)
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") LocalDateTime now, @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshTokenEntity r
            SET r.revokedAt = :now, r.revocationReason = :reason
            WHERE r.utilisateur.id = :utilisateurId AND r.revokedAt IS NULL
            """)
    int revokeAllActiveByUtilisateurId(
            @Param("utilisateurId") UUID utilisateurId,
            @Param("now") LocalDateTime now,
            @Param("reason") String reason
    );

    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
            FROM RefreshTokenEntity r
            WHERE r.familyId = :familyId
              AND r.utilisateur.id = :utilisateurId
              AND r.revokedAt IS NULL
              AND r.usedAt IS NULL
              AND r.expiresAt > :now
            """)
    boolean isFamilyActive(
            @Param("familyId") UUID familyId,
            @Param("utilisateurId") UUID utilisateurId,
            @Param("now") LocalDateTime now
    );

    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
            FROM RefreshTokenEntity r
            WHERE r.utilisateur.id = :utilisateurId
              AND r.revokedAt IS NULL
              AND r.usedAt IS NULL
              AND r.expiresAt > :now
            """)
    boolean existsActiveByUtilisateurId(
            @Param("utilisateurId") UUID utilisateurId,
            @Param("now") LocalDateTime now
    );
}
