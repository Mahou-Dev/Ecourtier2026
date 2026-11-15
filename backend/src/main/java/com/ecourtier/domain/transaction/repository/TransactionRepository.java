package com.ecourtier.domain.transaction.repository;

import com.ecourtier.domain.transaction.entity.StatutSequestre;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    @Query("SELECT t.annonce.id FROM TransactionEntity t WHERE t.id = :id")
    Optional<UUID> findAnnonceIdById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TransactionEntity t WHERE t.id = :id")
    Optional<TransactionEntity> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TransactionEntity t WHERE t.annonce.id = :annonceId " +
            "AND t.statutSequestre = com.ecourtier.domain.transaction.entity.StatutSequestre.INITIALISE " +
            "AND t.dossierActif = true")
    List<TransactionEntity> findActiveInitialiseByAnnonceIdForUpdate(@Param("annonceId") UUID annonceId);

    @Query("SELECT t.id FROM TransactionEntity t WHERE t.statutSequestre = com.ecourtier.domain.transaction.entity.StatutSequestre.INITIALISE " +
            "AND t.dossierActif = true AND t.dateExpiration IS NOT NULL AND t.dateExpiration <= :now " +
            "ORDER BY t.dateExpiration ASC")
    List<UUID> findExpiredActiveInitialiseIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TransactionEntity t WHERE t.annonce.id = :annonceId " +
            "AND ((t.dossierActif = true AND t.statutSequestre IN :statutsActifs) " +
            "OR t.statutSequestre = com.ecourtier.domain.transaction.entity.StatutSequestre.BLOQUE)")
    List<TransactionEntity> findBlockingTransactionsByAnnonceIdForUpdate(
            @Param("annonceId") UUID annonceId,
            @Param("statutsActifs") Collection<StatutSequestre> statutsActifs
    );

    boolean existsByAnnonceIdAndStatutSequestreIn(UUID annonceId, Collection<StatutSequestre> statuts);

    boolean existsByAnnonceIdAndDossierActifTrueAndStatutSequestreIn(UUID annonceId, Collection<StatutSequestre> statuts);

    List<TransactionEntity> findByAnnonceIdOrderByDateCreationDesc(UUID annonceId);

    List<TransactionEntity> findByLocataireIdOrProprietaireIdOrderByDateCreationDesc(UUID locataireId, UUID proprietaireId);

    Optional<TransactionEntity> findByAnnonceIdAndLocataireId(UUID annonceId, UUID locataireId);

    Page<TransactionEntity> findByStatutSequestre(StatutSequestre statutSequestre, Pageable pageable);

    @Query("SELECT t.id FROM TransactionEntity t " +
            "WHERE t.statutSequestre = com.ecourtier.domain.transaction.entity.StatutSequestre.LIBERE " +
            "AND t.statutBail = com.ecourtier.domain.transaction.entity.StatutBail.SIGNE_COMPLET " +
            "AND t.statutDocumentBail IN (com.ecourtier.domain.transaction.entity.StatutDocumentBail.A_GENERER, " +
            "com.ecourtier.domain.transaction.entity.StatutDocumentBail.ECHEC) " +
            "AND t.pdfBailTentatives < :maxTentatives " +
            "ORDER BY t.pdfBailDerniereTentative ASC NULLS FIRST, t.dateLiberation ASC")
    List<UUID> findIdsEligiblePdfRecovery(@Param("maxTentatives") int maxTentatives, Pageable pageable);
}
