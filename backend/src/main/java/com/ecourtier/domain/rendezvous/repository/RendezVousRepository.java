package com.ecourtier.domain.rendezvous.repository;

import com.ecourtier.domain.rendezvous.entity.RendezVousEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RendezVousRepository extends JpaRepository<RendezVousEntity, UUID> {
}
