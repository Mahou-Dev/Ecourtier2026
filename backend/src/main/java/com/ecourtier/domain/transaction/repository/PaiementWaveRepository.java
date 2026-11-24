package com.ecourtier.domain.transaction.repository;

import com.ecourtier.domain.transaction.entity.PaiementWaveEntity;
import com.ecourtier.domain.transaction.entity.StatutPaiementWave;
import com.ecourtier.domain.transaction.entity.TypeOperationWave;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaiementWaveRepository extends JpaRepository<PaiementWaveEntity, UUID> {

    Optional<PaiementWaveEntity> findByCleIdempotence(String cleIdempotence);

    Optional<PaiementWaveEntity> findByReferenceExterne(String referenceExterne);

    Optional<PaiementWaveEntity> findByRefCommand(String refCommand);

    Optional<PaiementWaveEntity> findByTokenExterne(String tokenExterne);

    @Query("SELECT p.transaction.id FROM PaiementWaveEntity p WHERE p.refCommand = :refCommand")
    Optional<UUID> findTransactionIdByRefCommand(@Param("refCommand") String refCommand);

    @Query("SELECT p.transaction.id FROM PaiementWaveEntity p WHERE p.tokenExterne = :token OR p.referenceExterne = :token")
    Optional<UUID> findTransactionIdByTokenOrReference(@Param("token") String token);

    Optional<PaiementWaveEntity> findTopByTransactionIdAndTypeOperationOrderByDateCreationDesc(
            UUID transactionId,
            TypeOperationWave typeOperation
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaiementWaveEntity p WHERE p.id = :id")
    Optional<PaiementWaveEntity> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p FROM PaiementWaveEntity p
            WHERE p.transaction.id = :transactionId
              AND p.typeOperation = :typeOperation
              AND p.statut IN :statuts
            ORDER BY p.dateCreation DESC
            """)
    List<PaiementWaveEntity> findByTransactionAndTypeAndStatutInForUpdate(
            @Param("transactionId") UUID transactionId,
            @Param("typeOperation") TypeOperationWave typeOperation,
            @Param("statuts") Collection<StatutPaiementWave> statuts
    );

    List<PaiementWaveEntity> findByStatutInOrderByDateCreationAsc(Collection<StatutPaiementWave> statuts);

    List<PaiementWaveEntity> findByTransactionIdOrderByDateCreationDesc(UUID transactionId);
}
