package com.ecourtier.domain.rendezvous.repository;

import com.ecourtier.domain.rendezvous.entity.NotificationSmsEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationSmsRepository extends JpaRepository<NotificationSmsEntity, UUID> {

    Page<NotificationSmsEntity> findByUtilisateurIdOrderByDateCreationDesc(UUID utilisateurId, Pageable pageable);

    List<NotificationSmsEntity> findByRendezVousIdOrderByDateCreationAsc(UUID rdvId);
}
