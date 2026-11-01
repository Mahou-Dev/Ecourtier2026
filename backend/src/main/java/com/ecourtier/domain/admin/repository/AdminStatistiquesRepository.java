package com.ecourtier.domain.admin.repository;

import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AdminStatistiquesRepository extends JpaRepository<UtilisateurEntity, UUID> {

    @Query("SELECT u.role, COUNT(u) FROM UtilisateurEntity u GROUP BY u.role")
    List<Object[]> countUtilisateursByRole();

    @Query("SELECT CAST(u.dateCreation AS java.time.LocalDate), COUNT(u) FROM UtilisateurEntity u WHERE u.dateCreation >= :dateDebut GROUP BY CAST(u.dateCreation AS java.time.LocalDate) ORDER BY CAST(u.dateCreation AS java.time.LocalDate) ASC")
    List<Object[]> countDailyRegistrations(@Param("dateDebut") LocalDateTime dateDebut);

    @Query("SELECT a.statutPublication, COUNT(a) FROM AnnonceEntity a WHERE a.supprime = false GROUP BY a.statutPublication")
    List<Object[]> countAnnoncesByStatutPublication();

    @Query("SELECT a.statutVerification, COUNT(a) FROM AnnonceEntity a WHERE a.supprime = false GROUP BY a.statutVerification")
    List<Object[]> countAnnoncesByStatutVerification();

    @Query("SELECT a.ville, a.quartier, COUNT(a) FROM AnnonceEntity a WHERE a.supprime = false GROUP BY a.ville, a.quartier ORDER BY COUNT(a) DESC")
    List<Object[]> findTopVilleQuartier(Pageable pageable);

    @Query("SELECT a.type, AVG(a.prix) FROM AnnonceEntity a WHERE a.supprime = false GROUP BY a.type")
    List<Object[]> findAveragePriceByTypeBien();

    @Query("SELECT a.dateCreation, a.dateVerification FROM AnnonceEntity a WHERE a.dateVerification IS NOT NULL AND a.supprime = false")
    List<Object[]> findVerificationDecisionTimes();

    @Query("SELECT r.statut, COUNT(r) FROM RendezVousEntity r WHERE r.dateCreation >= :dateDebut GROUP BY r.statut")
    List<Object[]> countRendezVousByStatut(@Param("dateDebut") LocalDateTime dateDebut);

    @Query("SELECT COUNT(r) FROM RendezVousEntity r WHERE r.statut = com.ecourtier.domain.rendezvous.entity.StatutRendezVous.EFFECTUE AND r.dateCreation >= :dateDebut")
    long countRendezVousEffectues(@Param("dateDebut") LocalDateTime dateDebut);

    @Query("SELECT COUNT(t) FROM TransactionEntity t WHERE t.dateCreation >= :dateDebut")
    long countTransactionsInitiated(@Param("dateDebut") LocalDateTime dateDebut);

    @Query("SELECT COUNT(t) FROM TransactionEntity t WHERE t.statutBail = com.ecourtier.domain.transaction.entity.StatutBail.SIGNE_COMPLET AND t.dateCreation >= :dateDebut")
    long countBauxSignesComplet(@Param("dateDebut") LocalDateTime dateDebut);

    @Query("SELECT COALESCE(SUM(COALESCE(t.montantCaution, 0) + COALESCE(t.montantPremierLoyer, 0) + COALESCE(t.fraisDossier, 0)), 0) FROM TransactionEntity t WHERE t.statutSequestre = com.ecourtier.domain.transaction.entity.StatutSequestre.BLOQUE")
    BigDecimal sumMontantSequestreBloque();

    @Query("SELECT s.statut, COUNT(s) FROM SignalementEntity s GROUP BY s.statut")
    List<Object[]> countSignalementsByStatut();

    @Query("SELECT s.motif, COUNT(s) FROM SignalementEntity s GROUP BY s.motif")
    List<Object[]> countSignalementsByMotif();

    @Query("SELECT COALESCE(AVG(a.note), 0.0) FROM AvisEntity a")
    Double findNoteMoyenneGlobale();

    @Query("SELECT COUNT(c) FROM ConversationIAEntity c WHERE c.dateCreation >= :dateDebut")
    long countConversationsIA(@Param("dateDebut") LocalDateTime dateDebut);

    /**
     * Compte le nombre de conversations distinctes ayant eu au moins une shortlist d'annonces non vide.
     * 
     * RÈGLE D'AGRÉGATION & STRUCTURATION :
     * 1. Nouveaux messages (créés après migration V9) : s'appuie sur la donnée structurée (m.nombreAnnoncesSuggerees > 0).
     * 2. Fallback pour l'historique antérieur (m.nombreAnnoncesSuggerees IS NULL) : conserve temporairement la vérification
     *    par le contenu du message ("J'ai trouvé %") uniquement pour les messages historiques où la valeur structurée n'est pas disponible.
     */
    @Query("SELECT COUNT(DISTINCT m.conversation.id) FROM MessageIAEntity m " +
           "WHERE m.role = com.ecourtier.domain.ia.entity.RoleMessageIA.ASSISTANT " +
           "AND (" +
           "   (m.nombreAnnoncesSuggerees IS NOT NULL AND m.nombreAnnoncesSuggerees > 0) " +
           "   OR (m.nombreAnnoncesSuggerees IS NULL AND m.contenu LIKE 'J''ai trouvé %')" +
           ") " +
           "AND m.conversation.dateCreation >= :dateDebut")
    long countConversationsIAWithShortlist(@Param("dateDebut") LocalDateTime dateDebut);
}
