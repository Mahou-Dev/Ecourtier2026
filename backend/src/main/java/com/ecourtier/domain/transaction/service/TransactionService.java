package com.ecourtier.domain.transaction.service;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.annonce.service.AnnonceStatutStateMachine;
import com.ecourtier.domain.rendezvous.entity.RendezVousEntity;
import com.ecourtier.domain.rendezvous.entity.StatutRendezVous;
import com.ecourtier.domain.rendezvous.repository.RendezVousRepository;
import com.ecourtier.domain.transaction.client.WaveCheckoutResponse;
import com.ecourtier.domain.transaction.client.WaveClient;
import com.ecourtier.domain.transaction.client.WaveRefundResponse;
import com.ecourtier.domain.transaction.dto.AnnulationTransactionRequestDTO;
import com.ecourtier.domain.transaction.dto.RemboursementRequestDTO;
import com.ecourtier.domain.transaction.dto.TransactionRequestDTO;
import com.ecourtier.domain.transaction.dto.TransactionResponseDTO;
import com.ecourtier.domain.transaction.entity.PaiementWaveEntity;
import com.ecourtier.domain.transaction.entity.StatutBail;
import com.ecourtier.domain.transaction.entity.StatutDocumentBail;
import com.ecourtier.domain.transaction.entity.StatutPaiementWave;
import com.ecourtier.domain.transaction.entity.StatutSequestre;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import com.ecourtier.domain.transaction.entity.TypeOperationWave;
import com.ecourtier.domain.transaction.mapper.TransactionMapper;
import com.ecourtier.domain.transaction.repository.PaiementWaveRepository;
import com.ecourtier.domain.transaction.repository.TransactionRepository;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

    private static final List<StatutSequestre> STATUTS_ACTIFS_LOCATION = List.of(
            StatutSequestre.INITIALISE,
            StatutSequestre.BLOQUE
    );

    private static final List<StatutSequestre> STATUTS_ACTIFS_VENTE = List.of(
            StatutSequestre.INITIALISE,
            StatutSequestre.BLOQUE,
            StatutSequestre.LIBERE
    );

    private static final String MOTIF_EXPIRATION_AUTO =
            "Transaction expirée automatiquement (délai de paiement dépassé)";

    private static final AnnonceStatutStateMachine FALLBACK_STATE_MACHINE = new AnnonceStatutStateMachine();

    private final TransactionRepository transactionRepository;
    private final RendezVousRepository rendezVousRepository;
    private final AnnonceRepository annonceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final TransactionMapper transactionMapper;
    private final WaveClient waveClient;
    private final PdfBailGeneratorService pdfBailGeneratorService;
    private final AnnonceStatutStateMachine annonceStatutStateMachine;

    @Value("${ecourtier.vente.acompte-pourcentage:10}")
    private int acomptePourcentage = 10;

    @Value("${ecourtier.transactions.initialise-expiration:PT24H}")
    private Duration initialiseExpiration = Duration.ofHours(24);

    private static final ZoneId DAKAR_ZONE = ZoneId.of("Africa/Dakar");

    @Value("${ecourtier.transactions.expiration-batch-size:100}")
    private int expirationBatchSize = 100;

    private Clock clock = Clock.system(DAKAR_ZONE);
    private TransactionTemplate transactionTemplate;
    private TransactionTemplate requiresNewTransactionTemplate;
    private PaiementWaveRepository paiementWaveRepository;

    @Autowired(required = false)
    void setClock(Clock clock) {
        if (clock != null) {
            this.clock = clock;
        }
    }

    @Autowired(required = false)
    void setPaiementWaveRepository(PaiementWaveRepository paiementWaveRepository) {
        this.paiementWaveRepository = paiementWaveRepository;
    }

    @Autowired(required = false)
    void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = transactionManager != null ? new TransactionTemplate(transactionManager) : null;
        if (transactionManager != null) {
            TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
            requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            this.requiresNewTransactionTemplate = requiresNew;
        } else {
            this.requiresNewTransactionTemplate = null;
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock != null ? clock : Clock.system(DAKAR_ZONE));
    }

    private Duration getEffectiveInitialiseExpiration() {
        if (initialiseExpiration == null || initialiseExpiration.isNegative() || initialiseExpiration.isZero()) {
            return Duration.ofHours(24);
        }
        return initialiseExpiration;
    }

    private AnnonceStatutStateMachine stateMachine() {
        return annonceStatutStateMachine != null ? annonceStatutStateMachine : FALLBACK_STATE_MACHINE;
    }

    private <T> T executeInTransaction(Supplier<T> action) {
        if (transactionTemplate != null) {
            return transactionTemplate.execute(status -> action.get());
        }
        return action.get();
    }

    private <T> T executeInNewTransaction(Supplier<T> action) {
        if (requiresNewTransactionTemplate != null) {
            return requiresNewTransactionTemplate.execute(status -> action.get());
        }
        return action.get();
    }

    /**
     * Ordre canonique de verrouillage (Annonce -> Transaction) partagé par tout le cycle de vie
     * afin de prévenir tout interblocage entre initiation, paiement, annulation, remboursement,
     * signature et expiration automatique.
     */
    private Optional<TransactionEntity> lockAnnonceThenTransaction(UUID transactionId) {
        transactionRepository.findAnnonceIdById(transactionId)
                .ifPresent(annonceRepository::findByIdAndSupprimeFalseForUpdate);
        return transactionRepository.findByIdForUpdate(transactionId);
    }

    private boolean hasBailSignature(TransactionEntity tx) {
        return tx != null
                && ((tx.getStatutBail() != null && tx.getStatutBail() != StatutBail.NON_SIGNE)
                || tx.getDateSignatureLocataire() != null
                || tx.getDateSignatureProprietaire() != null);
    }

    private boolean estExpiree(TransactionEntity tx, LocalDateTime referenceNow) {
        return tx != null
                && tx.getStatutSequestre() == StatutSequestre.INITIALISE
                && Boolean.TRUE.equals(tx.getDossierActif())
                && !hasBailSignature(tx)
                && tx.getDateExpiration() != null
                && !tx.getDateExpiration().isAfter(referenceNow);
    }

    private void cloturerTransactionExpiree(TransactionEntity tx, LocalDateTime referenceNow) {
        if (hasBailSignature(tx)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Impossible d'expirer cette transaction : une signature du contrat est déjà enregistrée (" + tx.getStatutBail() + ")."
            );
        }
        tx.setStatutSequestre(StatutSequestre.EXPIRE);
        tx.setDossierActif(Boolean.FALSE);
        if (tx.getDateCloture() == null) {
            tx.setDateCloture(referenceNow);
        }
        if (tx.getMotifCloture() == null || tx.getMotifCloture().isBlank()) {
            tx.setMotifCloture(MOTIF_EXPIRATION_AUTO);
        }
        AnnonceEntity annonce = tx.getAnnonce();
        if (annonce != null && annonce.getStatutPublication() == StatutPublication.RESERVEE) {
            stateMachine().appliquerTransitionTransaction(annonce, StatutPublication.DISPONIBLE);
            annonceRepository.save(annonce);
        }
        transactionRepository.saveAndFlush(tx);
        log.info("[EXPIRATION TRANSACTION] Transaction {} clôturée (INITIALISE -> EXPIRE) sur l'annonce {}.",
                tx.getId(), annonce != null ? annonce.getId() : null);
    }

    private void expirerTransactionsExpireesSurAnnonceVerrouillee(AnnonceEntity annonce, LocalDateTime referenceNow) {
        List<TransactionEntity> activeInitialise = transactionRepository.findActiveInitialiseByAnnonceIdForUpdate(annonce.getId());
        if (activeInitialise == null || activeInitialise.isEmpty()) {
            return;
        }
        boolean anyExpired = false;
        for (TransactionEntity existing : activeInitialise) {
            if (estExpiree(existing, referenceNow)
                    && (existing.getReferenceWave() == null || existing.getReferenceWave().isBlank())) {
                existing.setStatutSequestre(StatutSequestre.EXPIRE);
                existing.setDossierActif(Boolean.FALSE);
                if (existing.getDateCloture() == null) {
                    existing.setDateCloture(referenceNow);
                }
                if (existing.getMotifCloture() == null || existing.getMotifCloture().isBlank()) {
                    existing.setMotifCloture(MOTIF_EXPIRATION_AUTO);
                }
                if (annonce.getStatutPublication() == StatutPublication.RESERVEE) {
                    stateMachine().appliquerTransitionTransaction(annonce, StatutPublication.DISPONIBLE);
                    annonceRepository.save(annonce);
                }
                transactionRepository.save(existing);
                anyExpired = true;
                log.info("[EXPIRATION PARESSEUSE] Transaction {} expirée lors de l'initiation d'une nouvelle transaction sur l'annonce {}.",
                        existing.getId(), annonce.getId());
            }
        }
        if (anyExpired) {
            transactionRepository.flush();
        }
    }

    private record TxLifecycleOutcome(TransactionResponseDTO dto, boolean expired) {
    }

    @Transactional
    public TransactionResponseDTO initierTransaction(UUID annonceId, TransactionRequestDTO request, String userEmail) {
        UtilisateurEntity locataire = utilisateurRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));

        // Verrouillage pessimiste de l'annonce pour sérialiser deux demandes concurrentes sur le même bien
        AnnonceEntity annonce = annonceRepository.findByIdAndSupprimeFalseForUpdate(annonceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Annonce introuvable"));

        if (annonce.getUtilisateur() != null && annonce.getUtilisateur().getId().equals(locataire.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Impossible d'initier une transaction sur votre propre annonce."
            );
        }

        // ANO-02 (1) : L'annonce doit avoir été vérifiée et approuvée par l'administrateur
        if (annonce.getStatutVerification() != StatutVerification.VERIFIEE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Impossible d'initier une transaction : l'annonce doit être vérifiée par l'administrateur (statut actuel : "
                            + annonce.getStatutVerification() + ")."
            );
        }

        LocalDateTime currentNow = now();
        // P0-3 : Clôturer paresseusement d'éventuelles transactions INITIALISE expirées avant de réévaluer la disponibilité
        expirerTransactionsExpireesSurAnnonceVerrouillee(annonce, currentNow);

        // ANO-02 (2) : L'annonce doit être disponible
        if (annonce.getStatutPublication() != StatutPublication.DISPONIBLE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Impossible d'initier une transaction : ce bien n'est pas disponible (statut actuel : "
                            + annonce.getStatutPublication() + ")."
            );
        }

        // ANO-02 (3) : Aucune transaction active incompatible ne doit déjà exister pour cette annonce
        // Les transactions REMBOURSE, ANNULE, EXPIRE ou les dossiers historiques inactifs ne bloquent pas une nouvelle opération
        List<StatutSequestre> statutsActifsIncompatibles = annonce.getModeTransaction() == ModeTransaction.VENTE
                ? STATUTS_ACTIFS_VENTE
                : STATUTS_ACTIFS_LOCATION;

        if (transactionRepository.existsByAnnonceIdAndDossierActifTrueAndStatutSequestreIn(annonceId, statutsActifsIncompatibles)
                || transactionRepository.existsByAnnonceIdAndStatutSequestreIn(annonceId, List.of(StatutSequestre.BLOQUE))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Impossible d'initier une transaction : une transaction active existe déjà pour cette annonce."
            );
        }

        // Vérifier qu'un RDV à l'état EFFECTUE existe pour cette annonce et ce locataire
        Optional<RendezVousEntity> rdvEffectue = rendezVousRepository.findFirstByAnnonceIdAndDemandeurIdAndStatut(
                annonceId,
                locataire.getId(),
                StatutRendezVous.EFFECTUE
        );

        if (rdvEffectue.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Impossible d'initier une transaction : aucun rendez-vous de visite EFFECTUÉ préalable n'a été trouvé pour cette annonce."
            );
        }

        // Calcul des montants strictement côté serveur à partir de l'annonce (§4 / F29)
        // LOCATION : caution = 1 mois de loyer (prix), 1er loyer = prix, frais = 5 000 FCFA
        // VENTE : montantCaution = acompte = prix × acomptePourcentage / 100 (arrondi au franc), 1er loyer = 0, frais = 5 000 FCFA
        // Tout montant envoyé par le client dans la requête est délibérément ignoré
        BigDecimal prixAnnonce = annonce.getPrix() != null ? annonce.getPrix() : BigDecimal.ZERO;
        BigDecimal montantCaution;
        BigDecimal montantPremierLoyer;
        if (annonce.getModeTransaction() == ModeTransaction.VENTE) {
            montantCaution = prixAnnonce
                    .multiply(BigDecimal.valueOf(acomptePourcentage))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            montantPremierLoyer = BigDecimal.ZERO;
        } else {
            montantCaution = prixAnnonce;
            montantPremierLoyer = prixAnnonce;
        }
        BigDecimal fraisDossier = BigDecimal.valueOf(5000);

        LocalDateTime dateExpiration = currentNow.plus(getEffectiveInitialiseExpiration());
        TransactionEntity tx = TransactionEntity.builder()
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(annonce.getUtilisateur())
                .montantCaution(montantCaution)
                .montantPremierLoyer(montantPremierLoyer)
                .fraisDossier(fraisDossier)
                .dateCreation(currentNow)
                .dateExpiration(dateExpiration)
                .statutSequestre(StatutSequestre.INITIALISE)
                .statutBail(StatutBail.NON_SIGNE)
                .dossierActif(Boolean.TRUE)
                .build();

        try {
            TransactionEntity saved = transactionRepository.saveAndFlush(tx);
            return transactionMapper.toResponseDTO(saved);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Impossible d'initier une transaction : une transaction active existe déjà pour cette annonce."
            );
        }
    }

    public TransactionResponseDTO effectuerPaiementCaution(UUID transactionId, String userEmail) {
        PaymentClaim claim = executeInTransaction(() -> {
            LocalDateTime currentNow = now();
            // Phase 1 (verrou court Annonce -> Transaction) : validation + prise de verrou applicatif Wave
            TransactionEntity tx = lockAnnonceThenTransaction(transactionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction introuvable"));

            if (!Boolean.TRUE.equals(tx.getDossierActif())) {
                log.warn("[PAIEMENT SÉQUESTRE REFUSÉ] Tentative de paiement sur un dossier historique inactif {}", transactionId);
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Impossible d'effectuer le paiement : ce dossier de transaction est inactif."
                );
            }

            if (tx.getStatutSequestre() == StatutSequestre.ANNULE) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Impossible d'effectuer le paiement : cette transaction a été annulée."
                );
            }

            if (tx.getStatutSequestre() == StatutSequestre.EXPIRE) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Impossible d'effectuer le paiement : cette transaction a expiré."
                );
            }

            if (estExpiree(tx, currentNow) && (tx.getReferenceWave() == null || tx.getReferenceWave().isBlank())) {
                cloturerTransactionExpiree(tx, currentNow);
                return new PaymentClaim(null, tx.getId(), tx.getMontantTotalSequestre(), null, true);
            }

            if (tx.getStatutSequestre() != StatutSequestre.INITIALISE
                    || (tx.getReferenceWave() != null && !tx.getReferenceWave().isBlank())) {
                log.warn("[PAIEMENT SÉQUESTRE REFUSÉ] Tentative de paiement multiple ou sur état incompatible pour la transaction {} (statut={}, ref={})",
                        transactionId, tx.getStatutSequestre(), tx.getReferenceWave());
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Impossible d'effectuer le paiement : la caution a déjà été payée ou la transaction est dans un état incompatible ("
                                + tx.getStatutSequestre() + ")."
                );
            }

            AnnonceEntity annonce = tx.getAnnonce();
            if (annonce != null && (annonce.isSupprime()
                    || annonce.getStatutPublication() == StatutPublication.SUSPENDUE
                    || annonce.getStatutPublication() == StatutPublication.LOUEE
                    || annonce.getStatutPublication() == StatutPublication.VENDUE
                    || annonce.getStatutPublication() == StatutPublication.RESERVEE)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Impossible d'effectuer le paiement : l'annonce associée n'est plus disponible ("
                                + annonce.getStatutPublication() + ")."
                );
            }

            BigDecimal montantTotalSequestre = tx.getMontantTotalSequestre();
            String telephone = tx.getLocataire() != null ? tx.getLocataire().getTelephone() : null;
            UUID paiementWaveId = claimPaiementWaveOperation(tx, TypeOperationWave.PAIEMENT_SEQUESTRE, montantTotalSequestre, currentNow);
            return new PaymentClaim(paiementWaveId, tx.getId(), montantTotalSequestre, telephone, false);
        });

        if (claim == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne lors de l'initialisation du paiement.");
        }
        if (claim.expired()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Impossible d'effectuer le paiement : cette transaction a expiré."
            );
        }

        // Phase 2 (HORS transaction SQL, SANS verrou base tenu) : appel réseau au prestataire Wave
        WaveCheckoutResponse response;
        try {
            response = waveClient.initiateEscrowCheckout(claim.transactionId(), claim.montant(), claim.telephone());
        } catch (ResponseStatusException e) {
            updatePaiementWaveStateRequiresNew(claim.paiementWaveId(), null, StatutPaiementWave.ECHOUE, e.getReason(), null);
            throw e;
        } catch (Exception e) {
            log.error("[PAIEMENT SÉQUESTRE ÉCHEC] Erreur lors de l'appel au prestataire Wave pour la transaction {}", transactionId, e);
            updatePaiementWaveStateRequiresNew(claim.paiementWaveId(), null, StatutPaiementWave.ECHOUE, truncateError(e.getMessage()), null);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Échec de communication avec le prestataire de paiement Wave. La transaction n'a pas été débitée."
            );
        }

        String candidateRef = response != null && response.getWaveReference() != null
                ? response.getWaveReference().trim()
                : null;
        if (candidateRef != null && !candidateRef.isBlank()) {
            attachWaveReferenceRequiresNew(claim.paiementWaveId(), candidateRef);
        }

        boolean structureValid = response != null
                && "SUCCESS".equalsIgnoreCase(response.getStatus())
                && candidateRef != null
                && !candidateRef.isBlank()
                && (response.getTransactionId() == null || claim.transactionId().equals(response.getTransactionId()))
                && (response.getAmount() == null || claim.montant().compareTo(response.getAmount()) == 0);

        boolean verified = false;
        if (structureValid) {
            try {
                verified = waveClient.verifyPaymentStatus(candidateRef, claim.transactionId(), claim.montant());
            } catch (ResponseStatusException e) {
                updatePaiementWaveStateRequiresNew(claim.paiementWaveId(), candidateRef, StatutPaiementWave.A_RECONCILIER, e.getReason(), null);
                throw e;
            } catch (Exception verifyEx) {
                log.error("[PAIEMENT SÉQUESTRE INCERTAIN] Vérification Wave échouée après initiation (ref={}) pour tx={}",
                        candidateRef, transactionId, verifyEx);
                updatePaiementWaveStateRequiresNew(
                        claim.paiementWaveId(),
                        candidateRef,
                        StatutPaiementWave.A_RECONCILIER,
                        "Erreur vérification post-checkout: " + truncateError(verifyEx.getMessage()),
                        null
                );
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Le statut du paiement Wave est en attente de réconciliation."
                );
            }
        }

        if (!structureValid || !verified) {
            log.error("[PAIEMENT SÉQUESTRE NON CONFIRMÉ] Réponse invalide ou non confirmée du prestataire Wave pour la transaction {}", transactionId);
            updatePaiementWaveStateRequiresNew(
                    claim.paiementWaveId(),
                    candidateRef,
                    StatutPaiementWave.ECHOUE,
                    "Réponse prestataire Wave invalide ou non confirmée",
                    null
            );
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Le paiement de la caution n'a pas été confirmé par le prestataire Wave."
            );
        }

        // Phase 3 (nouveau verrou court Annonce -> Transaction) : re-vérification d'état et confirmation ou marquage tardif
        PaymentConfirmationResult confirmation;
        try {
            confirmation = executeInTransaction(() -> confirmWavePaymentInTransaction(
                    transactionId,
                    claim.paiementWaveId(),
                    candidateRef,
                    claim.montant(),
                    null
            ));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException persistenceEx) {
            log.error("[PAIEMENT SÉQUESTRE À RÉCONCILIER] Échec SQL lors de la confirmation locale après débit Wave (tx={}, ref={})",
                    transactionId, candidateRef, persistenceEx);
            updatePaiementWaveStateRequiresNew(
                    claim.paiementWaveId(),
                    candidateRef,
                    StatutPaiementWave.A_RECONCILIER,
                    "Échec persistance SQL post-confirmation Wave: " + truncateError(persistenceEx.getMessage()),
                    null
            );
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Le paiement Wave a été reçu mais la confirmation locale nécessite une réconciliation.",
                    persistenceEx
            );
        }

        if (confirmation != null && confirmation.latePayment()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, confirmation.lateReason());
        }
        return confirmation != null ? confirmation.dto() : null;
    }

    @Transactional
    public TransactionResponseDTO annulerTransaction(UUID transactionId, AnnulationTransactionRequestDTO request, String userEmail) {
        LocalDateTime currentNow = now();
        // Ordre canonique de verrouillage Annonce -> Transaction
        TransactionEntity tx = lockAnnonceThenTransaction(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction introuvable"));

        if (!Boolean.TRUE.equals(tx.getDossierActif())
                || tx.getStatutSequestre() == StatutSequestre.ANNULE
                || tx.getStatutSequestre() == StatutSequestre.EXPIRE
                || tx.getStatutSequestre() == StatutSequestre.REMBOURSE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Impossible d'annuler cette transaction : elle est déjà clôturée (" + tx.getStatutSequestre() + ")."
            );
        }

        if (tx.getStatutSequestre() != StatutSequestre.INITIALISE
                || (tx.getReferenceWave() != null && !tx.getReferenceWave().isBlank())) {
            log.warn("[ANNULATION TRANSACTION REFUSÉE] Tentative d'annulation sur transaction {} déjà payée ou avancée (statut={})",
                    transactionId, tx.getStatutSequestre());
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Impossible d'annuler cette transaction : la caution a déjà été payée ("
                            + tx.getStatutSequestre() + "). Utilisez la procédure de remboursement."
            );
        }

        if (hasBailSignature(tx)) {
            log.warn("[ANNULATION TRANSACTION REFUSÉE] Transaction {} comporte déjà une signature de bail ({})",
                    transactionId, tx.getStatutBail());
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Impossible d'annuler cette transaction : une signature du contrat de bail est déjà enregistrée ("
                            + tx.getStatutBail() + ")."
            );
        }

        String motif = (request != null && request.getMotif() != null && !request.getMotif().isBlank())
                ? request.getMotif().trim()
                : "Annulation demandée par " + (userEmail != null && !userEmail.isBlank() ? userEmail : "utilisateur");

        tx.setStatutSequestre(StatutSequestre.ANNULE);
        tx.setDossierActif(Boolean.FALSE);
        tx.setDateCloture(currentNow);
        tx.setMotifCloture(motif);

        AnnonceEntity annonce = tx.getAnnonce();
        if (annonce != null && annonce.getStatutPublication() == StatutPublication.RESERVEE) {
            stateMachine().appliquerTransitionTransaction(annonce, StatutPublication.DISPONIBLE);
            annonceRepository.save(annonce);
        }

        TransactionEntity saved = transactionRepository.saveAndFlush(tx);
        log.info("[ANNULATION TRANSACTION] Transaction {} annulée (INITIALISE -> ANNULE) par {}. Motif: {}",
                saved.getId(), userEmail, motif);
        return transactionMapper.toResponseDTO(saved);
    }

    public int expirerTransactionsExpirees() {
        LocalDateTime referenceNow = now();
        int batchSize = Math.max(1, expirationBatchSize);
        int totalExpired = 0;
        Set<UUID> attemptedIds = new HashSet<>();

        for (int page = 0; page < 100; page++) {
            List<UUID> candidateIds = transactionRepository.findExpiredActiveInitialiseIds(
                    referenceNow,
                    PageRequest.of(0, batchSize)
            );
            if (candidateIds == null || candidateIds.isEmpty()) {
                break;
            }

            boolean processedNewCandidate = false;
            for (UUID candidateId : candidateIds) {
                if (!attemptedIds.add(candidateId)) {
                    continue;
                }
                processedNewCandidate = true;
                try {
                    if (expirerTransactionIndividuelle(candidateId, referenceNow)) {
                        totalExpired++;
                    }
                } catch (Exception e) {
                    log.warn("[EXPIRATION SCHEDULER] Échec lors du traitement de la transaction {}: {}",
                            candidateId, e.getMessage());
                }
            }

            if (!processedNewCandidate || candidateIds.size() < batchSize) {
                break;
            }
        }

        if (totalExpired > 0) {
            log.info("[EXPIRATION SCHEDULER] {} transaction(s) INITIALISE expirée(s) clôturée(s).", totalExpired);
        }
        return totalExpired;
    }

    public boolean expirerTransactionIndividuelle(UUID transactionId, LocalDateTime referenceNow) {
        LocalDateTime effectiveNow = referenceNow != null ? referenceNow : now();
        Boolean expired = executeInTransaction(() -> {
            Optional<TransactionEntity> optTx = lockAnnonceThenTransaction(transactionId);
            if (optTx.isEmpty()) {
                return Boolean.FALSE;
            }
            TransactionEntity tx = optTx.get();
            if (hasBailSignature(tx)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Impossible d'expirer cette transaction : une signature du contrat de bail est déjà enregistrée ("
                                + tx.getStatutBail() + ")."
                );
            }
            if (!estExpiree(tx, effectiveNow)
                    || (tx.getReferenceWave() != null && !tx.getReferenceWave().isBlank())) {
                return Boolean.FALSE;
            }
            cloturerTransactionExpiree(tx, effectiveNow);
            return Boolean.TRUE;
        });
        return Boolean.TRUE.equals(expired);
    }

    public TransactionResponseDTO rembourserTransaction(UUID transactionId, RemboursementRequestDTO request, String userEmail) {
        String motif = (request != null && request.getMotif() != null && !request.getMotif().isBlank())
                ? request.getMotif().trim()
                : "Remboursement du séquestre";

        RefundClaim claim = executeInTransaction(() -> {
            LocalDateTime currentNow = now();
            // Phase 1 (verrou court Annonce -> Transaction) : validation pré-remboursement + prise de verrou applicatif Wave
            TransactionEntity tx = lockAnnonceThenTransaction(transactionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction introuvable"));

            if (!Boolean.TRUE.equals(tx.getDossierActif())) {
                log.warn("[REMBOURSEMENT SÉQUESTRE REFUSÉ] Tentative de remboursement sur un dossier historique inactif {}", transactionId);
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Impossible de rembourser cette transaction : ce dossier est inactif."
                );
            }

            if (tx.getStatutSequestre() == StatutSequestre.REMBOURSE
                    || (tx.getReferenceRemboursementWave() != null && !tx.getReferenceRemboursementWave().isBlank())) {
                log.warn("[REMBOURSEMENT SÉQUESTRE REFUSÉ] Transaction {} déjà remboursée (réf remboursement={})",
                        transactionId, tx.getReferenceRemboursementWave());
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Cette transaction a déjà été remboursée."
                );
            }

            if (tx.getStatutSequestre() == StatutSequestre.LIBERE || tx.getDateLiberation() != null) {
                log.warn("[REMBOURSEMENT SÉQUESTRE REFUSÉ] Fonds déjà libérés pour la transaction {}", transactionId);
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Impossible de rembourser cette transaction : les fonds du séquestre ont déjà été libérés au bénéficiaire."
                );
            }

            if (tx.getStatutBail() == StatutBail.SIGNE_COMPLET) {
                log.warn("[REMBOURSEMENT SÉQUESTRE REFUSÉ] Bail déjà SIGNE_COMPLET pour la transaction {}", transactionId);
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Impossible de rembourser cette transaction : le contrat de bail est déjà entièrement signé (SIGNE_COMPLET)."
                );
            }

            if (tx.getStatutSequestre() != StatutSequestre.BLOQUE
                    || tx.getReferenceWave() == null
                    || tx.getReferenceWave().isBlank()) {
                log.warn("[REMBOURSEMENT SÉQUESTRE REFUSÉ] État incompatible ({}) ou référence Wave absente pour la transaction {}",
                        tx.getStatutSequestre(), transactionId);
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Impossible de rembourser une transaction dont la caution n'est pas payée et bloquée en séquestre (état actuel : "
                                + tx.getStatutSequestre() + ")."
                );
            }

            BigDecimal montantARembourser = tx.getMontantTotalSequestre();
            String referencePaiementInitial = tx.getReferenceWave().trim();
            UUID paiementWaveId = claimPaiementWaveOperation(tx, TypeOperationWave.REMBOURSEMENT, montantARembourser, currentNow);
            return new RefundClaim(paiementWaveId, tx.getId(), referencePaiementInitial, montantARembourser);
        });

        // Phase 2 (HORS transaction SQL, SANS verrou base tenu) : appel au prestataire Wave
        WaveRefundResponse refundResponse;
        try {
            refundResponse = waveClient.initiateEscrowRefund(claim.transactionId(), claim.referencePaiementInitial(), claim.montant());
        } catch (ResponseStatusException e) {
            updatePaiementWaveStateRequiresNew(claim.paiementWaveId(), null, StatutPaiementWave.ECHOUE, e.getReason(), null);
            throw e;
        } catch (Exception e) {
            log.error("[REMBOURSEMENT SÉQUESTRE ÉCHEC] Erreur lors de l'appel de remboursement Wave pour la transaction {}", transactionId, e);
            updatePaiementWaveStateRequiresNew(claim.paiementWaveId(), null, StatutPaiementWave.ECHOUE, truncateError(e.getMessage()), null);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Échec de communication avec le prestataire Wave lors du remboursement."
            );
        }

        String candidateRefundRef = refundResponse != null && refundResponse.getRefundReference() != null
                ? refundResponse.getRefundReference().trim()
                : null;
        if (candidateRefundRef != null && !candidateRefundRef.isBlank()) {
            attachWaveReferenceRequiresNew(claim.paiementWaveId(), candidateRefundRef);
        }

        boolean structureValid = refundResponse != null
                && "SUCCESS".equalsIgnoreCase(refundResponse.getStatus())
                && candidateRefundRef != null
                && !candidateRefundRef.isBlank()
                && refundResponse.getOriginalWaveReference() != null
                && claim.referencePaiementInitial().equals(refundResponse.getOriginalWaveReference().trim())
                && refundResponse.getRefundedAmount() != null
                && claim.montant().compareTo(refundResponse.getRefundedAmount()) == 0;

        boolean verified = false;
        if (structureValid) {
            try {
                verified = waveClient.verifyRefundStatus(candidateRefundRef, claim.referencePaiementInitial(), claim.montant());
            } catch (ResponseStatusException e) {
                updatePaiementWaveStateRequiresNew(claim.paiementWaveId(), candidateRefundRef, StatutPaiementWave.A_RECONCILIER, e.getReason(), null);
                throw e;
            } catch (Exception verifyEx) {
                log.error("[REMBOURSEMENT SÉQUESTRE INCERTAIN] Vérification remboursement Wave échouée (ref={}) pour tx={}",
                        candidateRefundRef, transactionId, verifyEx);
                updatePaiementWaveStateRequiresNew(
                        claim.paiementWaveId(),
                        candidateRefundRef,
                        StatutPaiementWave.A_RECONCILIER,
                        "Erreur vérification remboursement: " + truncateError(verifyEx.getMessage()),
                        null
                );
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Le statut du remboursement Wave est en attente de réconciliation."
                );
            }
        }

        if (!structureValid || !verified) {
            log.error("[REMBOURSEMENT SÉQUESTRE NON CONFIRMÉ] Réponse invalide ou incohérente du prestataire Wave pour la transaction {} (réf attendue={}, réf reçue={}, montant attendu={}, montant reçu={})",
                    transactionId,
                    claim.referencePaiementInitial(),
                    refundResponse != null ? refundResponse.getOriginalWaveReference() : null,
                    claim.montant(),
                    refundResponse != null ? refundResponse.getRefundedAmount() : null);
            updatePaiementWaveStateRequiresNew(
                    claim.paiementWaveId(),
                    candidateRefundRef,
                    StatutPaiementWave.ECHOUE,
                    "Réponse prestataire Wave invalide ou incohérente",
                    null
            );
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Le remboursement n'a pas été confirmé par le prestataire Wave."
            );
        }

        // Phase 3 (nouveau verrou court Annonce -> Transaction) : confirmation finale
        BigDecimal montantEffectif = refundResponse.getRefundedAmount();
        try {
            return executeInTransaction(() -> {
                LocalDateTime confirmNow = now();
                TransactionEntity tx = lockAnnonceThenTransaction(transactionId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction introuvable"));

                if (tx.getStatutSequestre() != StatutSequestre.BLOQUE
                        || tx.getStatutBail() == StatutBail.SIGNE_COMPLET
                        || !Boolean.TRUE.equals(tx.getDossierActif())
                        || (tx.getReferenceRemboursementWave() != null && !tx.getReferenceRemboursementWave().isBlank())) {
                    updatePaiementWaveStateRequiresNew(
                            claim.paiementWaveId(),
                            candidateRefundRef,
                            StatutPaiementWave.A_RECONCILIER,
                            "Conflit concurrent durant le remboursement (statut=" + tx.getStatutSequestre() + ", bail=" + tx.getStatutBail() + ")",
                            null
                    );
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Impossible de finaliser le remboursement : l'état de la transaction a changé pendant l'appel Wave ("
                                    + tx.getStatutSequestre() + ")."
                    );
                }

                tx.setReferenceRemboursementWave(candidateRefundRef);
                tx.setMontantRembourse(montantEffectif);
                tx.setDateRemboursement(confirmNow);
                tx.setMotifRemboursement(motif);
                tx.setDateCloture(confirmNow);
                tx.setMotifCloture(motif);
                tx.setStatutSequestre(StatutSequestre.REMBOURSE);
                tx.setDossierActif(Boolean.FALSE);

                AnnonceEntity annonce = tx.getAnnonce();
                if (annonce != null && annonce.getStatutPublication() == StatutPublication.RESERVEE) {
                    stateMachine().appliquerTransitionTransaction(annonce, StatutPublication.DISPONIBLE);
                    annonceRepository.save(annonce);
                }

                markPaiementWaveConfirmedLocked(claim.paiementWaveId(), candidateRefundRef, confirmNow, null);
                log.info("[REMBOURSEMENT SÉQUESTRE CONFIRMÉ] Transaction {} remboursée par {}. Réf paiement initial: {}, Réf remboursement: {}, Montant: {} FCFA, Motif: {}",
                        tx.getId(), userEmail, tx.getReferenceWave(), tx.getReferenceRemboursementWave(), tx.getMontantRembourse(), motif);

                TransactionEntity saved = transactionRepository.saveAndFlush(tx);
                return transactionMapper.toResponseDTO(saved);
            });
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException persistenceEx) {
            log.error("[REMBOURSEMENT SÉQUESTRE À RÉCONCILIER] Échec SQL lors de la confirmation du remboursement (tx={}, ref={})",
                    transactionId, candidateRefundRef, persistenceEx);
            updatePaiementWaveStateRequiresNew(
                    claim.paiementWaveId(),
                    candidateRefundRef,
                    StatutPaiementWave.A_RECONCILIER,
                    "Échec persistance SQL post-remboursement Wave: " + truncateError(persistenceEx.getMessage()),
                    null
            );
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Le remboursement Wave a été exécuté mais nécessite une réconciliation locale.",
                    persistenceEx
            );
        }
    }

    public TransactionResponseDTO signerBail(UUID transactionId, String userEmail) {
        TxLifecycleOutcome outcome = executeInTransaction(() -> {
            LocalDateTime currentNow = now();
            // Ordre canonique de verrouillage Annonce -> Transaction
            TransactionEntity tx = lockAnnonceThenTransaction(transactionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction introuvable"));

            if (!Boolean.TRUE.equals(tx.getDossierActif())) {
                log.warn("[SIGNATURE BAIL REFUSÉE] Tentative de signature sur un dossier historique inactif {}", transactionId);
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Impossible de signer le contrat : ce dossier de transaction est inactif."
                );
            }

            if (tx.getStatutSequestre() == StatutSequestre.REMBOURSE) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Impossible de signer le contrat : cette transaction a été remboursée."
                );
            }

            if (tx.getStatutSequestre() == StatutSequestre.ANNULE) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Impossible de signer le contrat : cette transaction a été annulée."
                );
            }

            if (tx.getStatutSequestre() == StatutSequestre.EXPIRE) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Impossible de signer le contrat : cette transaction a expiré."
                );
            }

            if (estExpiree(tx, currentNow) && !hasBailSignature(tx) && (tx.getReferenceWave() == null || tx.getReferenceWave().isBlank())) {
                cloturerTransactionExpiree(tx, currentNow);
                return new TxLifecycleOutcome(null, true);
            }

            if (tx.getStatutSequestre() == StatutSequestre.LIBERE) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Ce contrat a déjà été entièrement signé et les fonds ont été libérés."
                );
            }

            if (tx.getStatutSequestre() == StatutSequestre.INITIALISE) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Le contrat ne peut pas être signé avant le paiement et le blocage du séquestre."
                );
            }

            if (tx.getStatutSequestre() != StatutSequestre.BLOQUE) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Impossible de signer le contrat : la transaction est au statut " + tx.getStatutSequestre() + "."
                );
            }

            AnnonceEntity annonce = tx.getAnnonce();
            if (annonce != null && (annonce.isSupprime()
                    || annonce.getStatutPublication() == StatutPublication.SUSPENDUE)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Impossible de signer le contrat : l'annonce associée est suspendue ou supprimée."
                );
            }

            boolean isLocataire = tx.getLocataire() != null && tx.getLocataire().getEmail().equalsIgnoreCase(userEmail);
            boolean isProprietaire = tx.getProprietaire() != null && tx.getProprietaire().getEmail().equalsIgnoreCase(userEmail);

            if (isLocataire) {
                appliquerSignatureLocataire(tx, currentNow);
            } else if (isProprietaire) {
                appliquerSignatureProprietaire(tx, currentNow);
            } else {
                if (!isAdminCaller(userEmail)) {
                    throw new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "Vous n'êtes pas autorisé à signer ce contrat."
                    );
                }
                if (tx.getDateSignatureLocataire() == null) {
                    appliquerSignatureLocataire(tx, currentNow);
                } else if (tx.getDateSignatureProprietaire() == null) {
                    appliquerSignatureProprietaire(tx, currentNow);
                } else {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce contrat de bail a déjà été entièrement signé.");
                }
            }

            // Vérifier si la libération automatique doit se déclencher
            verifierEtDeclencherLiberation(tx);

            TransactionEntity saved = transactionRepository.saveAndFlush(tx);
            return new TxLifecycleOutcome(transactionMapper.toResponseDTO(saved), false);
        });

        if (outcome != null && outcome.expired()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Impossible de signer le contrat : cette transaction a expiré."
            );
        }
        return outcome != null ? outcome.dto() : null;
    }

    private void verifierEtDeclencherLiberation(TransactionEntity tx) {
        if (tx.getStatutBail() == StatutBail.SIGNE_COMPLET && tx.getStatutSequestre() == StatutSequestre.BLOQUE) {
            BigDecimal montantVerseProprietaire = tx.getMontantVerseProprietaire();
            BigDecimal fraisConserves = tx.getFraisDossier() != null ? tx.getFraisDossier() : BigDecimal.valueOf(5000);

            log.info("[LIBÉRATION AUTOMATIQUE SÉQUESTRE] Bail signé complet ET séquestre bloqué pour la transaction {}. " +
                            "Total séquestre: {} FCFA. Reversé au propriétaire: {} FCFA (caution {} + premier loyer {}). " +
                            "Frais plateforme conservés: {} FCFA. Statut -> LIBERE",
                    tx.getId(), tx.getMontantTotalSequestre(), montantVerseProprietaire,
                    tx.getMontantCaution(), tx.getMontantPremierLoyer(), fraisConserves);

            tx.setStatutSequestre(StatutSequestre.LIBERE);
            if (tx.getDateLiberation() == null) {
                tx.setDateLiberation(now());
            }

            // Mise à jour du statut de l'annonce en LOUEE ou VENDUE via la machine à états centralisée
            AnnonceEntity annonce = tx.getAnnonce();
            if (annonce != null) {
                StatutPublication cible = annonce.getModeTransaction() == ModeTransaction.VENTE
                        ? StatutPublication.VENDUE
                        : StatutPublication.LOUEE;
                stateMachine().appliquerTransitionTransaction(annonce, cible);
                annonceRepository.save(annonce);
            }

            // Génération du PDF du bail uniquement s'il n'a pas déjà été généré
            if (tx.getPdfBailPath() == null || tx.getPdfBailPath().isBlank()
                    || (tx.getStatutDocumentBail() != StatutDocumentBail.GENERE && !fichierPdfExiste(tx))) {
                tenterGenerationPdf(tx);
            }
        }
    }

    @Transactional(readOnly = true)
    public TransactionResponseDTO obtenirDetail(UUID transactionId, String userEmail) {
        TransactionEntity tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction introuvable"));
        return transactionMapper.toResponseDTO(tx);
    }

    @Transactional(readOnly = true)
    public java.util.List<TransactionResponseDTO> obtenirMesTransactions(String userEmail) {
        UtilisateurEntity user = utilisateurRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));
        java.util.List<TransactionEntity> transactions = transactionRepository.findByLocataireIdOrProprietaireIdOrderByDateCreationDesc(user.getId(), user.getId());
        return transactions.stream().map(transactionMapper::toResponseDTO).toList();
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponseDTO> listerToutesLesTransactions(StatutSequestre statut, Pageable pageable) {
        Page<TransactionEntity> page;
        if (statut != null) {
            page = transactionRepository.findByStatutSequestre(statut, pageable);
        } else {
            page = transactionRepository.findAll(pageable);
        }
        return page.map(transactionMapper::toResponseDTO);
    }

    private void appliquerSignatureLocataire(TransactionEntity tx, LocalDateTime signatureTimestamp) {
        if (tx.getDateSignatureLocataire() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vous avez déjà signé ce contrat de bail.");
        }
        tx.setDateSignatureLocataire(signatureTimestamp);
        recalculerStatutBail(tx);
    }

    private void appliquerSignatureProprietaire(TransactionEntity tx, LocalDateTime signatureTimestamp) {
        if (tx.getDateSignatureProprietaire() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vous avez déjà signé ce contrat de bail.");
        }
        tx.setDateSignatureProprietaire(signatureTimestamp);
        recalculerStatutBail(tx);
    }

    private void recalculerStatutBail(TransactionEntity tx) {
        boolean locataireSigne = tx.getDateSignatureLocataire() != null;
        boolean proprietaireSigne = tx.getDateSignatureProprietaire() != null;
        if (locataireSigne && proprietaireSigne) {
            tx.setStatutBail(StatutBail.SIGNE_COMPLET);
        } else if (locataireSigne) {
            tx.setStatutBail(StatutBail.SIGNE_LOCATAIRE);
        } else if (proprietaireSigne) {
            tx.setStatutBail(StatutBail.SIGNE_PROPRIETAIRE);
        } else {
            tx.setStatutBail(StatutBail.NON_SIGNE);
        }
    }

    private boolean isAdminCaller(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return false;
        }
        Optional<UtilisateurEntity> userOpt = utilisateurRepository.findByEmail(userEmail);
        if (userOpt.isPresent()) {
            return userOpt.get().getRole() == Role.ADMIN;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && userEmail.equalsIgnoreCase(auth.getName())
                && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /**
     * Point d'entrée transactionnel partagé pour confirmer un paiement Wave externe
     * (appelé par Phase 3, webhook signé ou service de réconciliation) sous verrou canonique Annonce -> Transaction.
     */
    public PaymentConfirmationResult confirmWavePaymentFromExternal(
            UUID transactionId,
            UUID paiementWaveId,
            String waveReference,
            BigDecimal expectedAmount,
            String payloadWebhook
    ) {
        return executeInTransaction(() ->
                confirmWavePaymentInTransaction(transactionId, paiementWaveId, waveReference, expectedAmount, payloadWebhook));
    }

    private UUID claimPaiementWaveOperation(
            TransactionEntity tx,
            TypeOperationWave typeOperation,
            BigDecimal montant,
            LocalDateTime currentNow
    ) {
        if (paiementWaveRepository == null) {
            return null;
        }
        List<StatutPaiementWave> blockingStatuses = List.of(
                StatutPaiementWave.INITIE,
                StatutPaiementWave.EN_COURS,
                StatutPaiementWave.A_RECONCILIER,
                StatutPaiementWave.CONFIRME,
                StatutPaiementWave.TARDIF_A_REMBOURSER
        );
        List<PaiementWaveEntity> existing = paiementWaveRepository.findByTransactionAndTypeAndStatutInForUpdate(
                tx.getId(), typeOperation, blockingStatuses
        );
        if (!existing.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Une opération Wave (" + typeOperation + ") est déjà active ou finalisée pour cette transaction."
            );
        }

        PaiementWaveEntity claim = PaiementWaveEntity.builder()
                .transaction(tx)
                .typeOperation(typeOperation)
                .statut(StatutPaiementWave.EN_COURS)
                .cleIdempotence(typeOperation.name() + "-" + tx.getId() + "-" + UUID.randomUUID())
                .montant(montant)
                .devise("XOF")
                .tentatives(1)
                .dateCreation(currentNow)
                .dateModification(currentNow)
                .build();
        try {
            return paiementWaveRepository.saveAndFlush(claim).getId();
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Opération Wave concurrente déjà engagée pour cette transaction.",
                    ex
            );
        }
    }

    private void attachWaveReferenceRequiresNew(UUID paiementWaveId, String waveReference) {
        if (paiementWaveRepository == null || paiementWaveId == null || waveReference == null || waveReference.isBlank()) {
            return;
        }
        try {
            executeInNewTransaction(() -> {
                paiementWaveRepository.findByIdForUpdate(paiementWaveId).ifPresent(pw -> {
                    pw.setReferenceExterne(waveReference.trim());
                    pw.setDateModification(now());
                    paiementWaveRepository.saveAndFlush(pw);
                });
                return null;
            });
        } catch (Exception ex) {
            log.warn("Impossible d'attacher immédiatement la référence Wave {} sur paiementWave={}: {}",
                    waveReference, paiementWaveId, ex.getMessage());
        }
    }

    private void updatePaiementWaveStateRequiresNew(
            UUID paiementWaveId,
            String waveReference,
            StatutPaiementWave newStatut,
            String errorMessage,
            String payloadWebhook
    ) {
        if (paiementWaveRepository == null || paiementWaveId == null) {
            return;
        }
        try {
            executeInNewTransaction(() -> {
                paiementWaveRepository.findByIdForUpdate(paiementWaveId).ifPresent(pw -> {
                    if (waveReference != null && !waveReference.isBlank()) {
                        pw.setReferenceExterne(waveReference.trim());
                    }
                    pw.setStatut(newStatut);
                    pw.setMessageErreur(truncateError(errorMessage));
                    if (payloadWebhook != null) {
                        pw.setPayloadWebhook(payloadWebhook);
                    }
                    pw.setDateModification(now());
                    paiementWaveRepository.saveAndFlush(pw);
                });
                return null;
            });
        } catch (Exception ex) {
            log.error("Échec de persistance REQUIRES_NEW pour paiementWave={} -> statut={}: {}",
                    paiementWaveId, newStatut, ex.getMessage());
        }
    }

    private void markPaiementWaveConfirmedLocked(
            UUID paiementWaveId,
            String waveReference,
            LocalDateTime confirmNow,
            String payloadWebhook
    ) {
        if (paiementWaveRepository == null || paiementWaveId == null) {
            return;
        }
        paiementWaveRepository.findByIdForUpdate(paiementWaveId).ifPresent(pw -> {
            pw.setReferenceExterne(waveReference);
            pw.setStatut(StatutPaiementWave.CONFIRME);
            pw.setDateConfirmation(confirmNow);
            pw.setDateModification(confirmNow);
            pw.setMessageErreur(null);
            if (payloadWebhook != null) {
                pw.setPayloadWebhook(payloadWebhook);
            }
            paiementWaveRepository.saveAndFlush(pw);
        });
    }

    private PaymentConfirmationResult confirmWavePaymentInTransaction(
            UUID transactionId,
            UUID paiementWaveId,
            String candidateRef,
            BigDecimal expectedAmount,
            String payloadWebhook
    ) {
        LocalDateTime confirmNow = now();
        String normalizedRef = candidateRef != null ? candidateRef.trim() : null;
        if (normalizedRef == null || normalizedRef.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Référence Wave manquante.");
        }

        // Idempotence : si ce webhook / cette référence est déjà enregistrée comme tardive/réconciliée ou confirmée
        if (paiementWaveRepository != null) {
            Optional<PaiementWaveEntity> byRefOpt = paiementWaveRepository.findByReferenceExterne(normalizedRef);
            if (byRefOpt.isPresent()) {
                PaiementWaveEntity existingRef = byRefOpt.get();
                if (existingRef.getTransaction() != null && !existingRef.getTransaction().getId().equals(transactionId)) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Référence Wave déjà associée à une autre transaction."
                    );
                }
                if (existingRef.getStatut() == StatutPaiementWave.TARDIF_A_REMBOURSER
                        || existingRef.getStatut() == StatutPaiementWave.RECONCILIE) {
                    TransactionEntity currentTx = lockAnnonceThenTransaction(transactionId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction introuvable"));
                    return new PaymentConfirmationResult(
                            transactionMapper.toResponseDTO(currentTx),
                            true,
                            existingRef.getMessageErreur(),
                            false
                    );
                }
            }
        }

        TransactionEntity tx = lockAnnonceThenTransaction(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction introuvable"));

        if (expectedAmount != null && tx.getMontantTotalSequestre().compareTo(expectedAmount) != 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Montant Wave incohérent pour la transaction " + transactionId
            );
        }

        if (tx.getReferenceWave() != null && normalizedRef.equals(tx.getReferenceWave().trim())
                && (tx.getStatutSequestre() == StatutSequestre.BLOQUE || tx.getStatutSequestre() == StatutSequestre.LIBERE)) {
            markOrCreateWebhookPaiementLocked(tx, paiementWaveId, normalizedRef, StatutPaiementWave.CONFIRME, confirmNow, null, payloadWebhook);
            return new PaymentConfirmationResult(transactionMapper.toResponseDTO(tx), false, null, false);
        }

        if (estExpiree(tx, confirmNow)) {
            cloturerTransactionExpiree(tx, confirmNow);
            String lateMsg = "Paiement Wave confirmé tardivement après dépassement du délai d'expiration de 24h ; remboursement automatique requis.";
            markOrCreateWebhookPaiementLocked(tx, paiementWaveId, normalizedRef, StatutPaiementWave.TARDIF_A_REMBOURSER, confirmNow, lateMsg, payloadWebhook);
            TransactionEntity savedExpired = transactionRepository.saveAndFlush(tx);
            return new PaymentConfirmationResult(transactionMapper.toResponseDTO(savedExpired), true, lateMsg, false);
        }

        if (!Boolean.TRUE.equals(tx.getDossierActif())
                || tx.getStatutSequestre() != StatutSequestre.INITIALISE
                || (tx.getReferenceWave() != null && !tx.getReferenceWave().isBlank())) {
            String lateMsg = "Paiement Wave reçu sur transaction non éligible (" + tx.getStatutSequestre() + ") ; remboursement automatique requis.";
            markOrCreateWebhookPaiementLocked(tx, paiementWaveId, normalizedRef, StatutPaiementWave.TARDIF_A_REMBOURSER, confirmNow, lateMsg, payloadWebhook);
            return new PaymentConfirmationResult(transactionMapper.toResponseDTO(tx), true, lateMsg, false);
        }

        AnnonceEntity annonce = tx.getAnnonce();
        if (annonce != null && (annonce.isSupprime()
                || annonce.getStatutPublication() == StatutPublication.SUSPENDUE
                || annonce.getStatutPublication() == StatutPublication.LOUEE
                || annonce.getStatutPublication() == StatutPublication.VENDUE
                || annonce.getStatutPublication() == StatutPublication.RESERVEE)) {
            String lateMsg = "Paiement Wave tardif reçu alors que l'annonce est au statut "
                    + (annonce.isSupprime() ? "SUPPRIMEE" : annonce.getStatutPublication())
                    + " ; remboursement automatique requis.";
            markOrCreateWebhookPaiementLocked(tx, paiementWaveId, normalizedRef, StatutPaiementWave.TARDIF_A_REMBOURSER, confirmNow, lateMsg, payloadWebhook);
            return new PaymentConfirmationResult(transactionMapper.toResponseDTO(tx), true, lateMsg, false);
        }

        tx.setReferenceWave(normalizedRef);
        tx.setDatePaiement(confirmNow);
        tx.setStatutSequestre(StatutSequestre.BLOQUE);

        verifierEtDeclencherLiberation(tx);

        if (tx.getStatutSequestre() == StatutSequestre.BLOQUE
                && annonce != null
                && annonce.getStatutPublication() == StatutPublication.DISPONIBLE) {
            stateMachine().appliquerTransitionTransaction(annonce, StatutPublication.RESERVEE);
            annonceRepository.save(annonce);
        }

        markOrCreateWebhookPaiementLocked(tx, paiementWaveId, normalizedRef, StatutPaiementWave.CONFIRME, confirmNow, null, payloadWebhook);
        TransactionEntity saved = transactionRepository.saveAndFlush(tx);
        return new PaymentConfirmationResult(transactionMapper.toResponseDTO(saved), false, null, true);
    }

    private void markOrCreateWebhookPaiementLocked(
            TransactionEntity tx,
            UUID paiementWaveId,
            String normalizedRef,
            StatutPaiementWave targetStatut,
            LocalDateTime eventNow,
            String messageErreur,
            String payloadWebhook
    ) {
        if (paiementWaveRepository == null) {
            return;
        }
        PaiementWaveEntity target = null;
        if (paiementWaveId != null) {
            target = paiementWaveRepository.findByIdForUpdate(paiementWaveId).orElse(null);
        }
        if (target == null && normalizedRef != null) {
            target = paiementWaveRepository.findByReferenceExterne(normalizedRef).orElse(null);
        }
        if (target == null) {
            List<PaiementWaveEntity> actives = paiementWaveRepository.findByTransactionAndTypeAndStatutInForUpdate(
                    tx.getId(),
                    TypeOperationWave.PAIEMENT_SEQUESTRE,
                    List.of(StatutPaiementWave.INITIE, StatutPaiementWave.EN_COURS, StatutPaiementWave.A_RECONCILIER)
            );
            if (!actives.isEmpty()) {
                target = actives.get(0);
            }
        }

        if (target == null) {
            target = PaiementWaveEntity.builder()
                    .transaction(tx)
                    .typeOperation(TypeOperationWave.PAIEMENT_SEQUESTRE)
                    .cleIdempotence("WEBHOOK-" + tx.getId() + "-" + normalizedRef)
                    .montant(tx.getMontantTotalSequestre())
                    .devise("XOF")
                    .tentatives(1)
                    .dateCreation(eventNow)
                    .build();
        }
        target.setReferenceExterne(normalizedRef);
        target.setStatut(targetStatut);
        target.setDateModification(eventNow);
        target.setMessageErreur(truncateError(messageErreur));
        if (targetStatut == StatutPaiementWave.CONFIRME && target.getDateConfirmation() == null) {
            target.setDateConfirmation(eventNow);
        }
        if (payloadWebhook != null) {
            target.setPayloadWebhook(payloadWebhook);
        }
        paiementWaveRepository.saveAndFlush(target);
    }

    private String truncateError(String msg) {
        if (msg == null) {
            return null;
        }
        return msg.length() <= 950 ? msg : msg.substring(0, 950);
    }

    public void markWebhookPaymentFailed(UUID transactionId, String waveReference, String errorReason, String payloadWebhook) {
        if (paiementWaveRepository == null) {
            return;
        }
        executeInTransaction(() -> {
            TransactionEntity tx = lockAnnonceThenTransaction(transactionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction introuvable"));
            if (tx.getStatutSequestre() == StatutSequestre.INITIALISE) {
                markOrCreateWebhookPaiementLocked(tx, null, waveReference, StatutPaiementWave.ECHOUE, now(), errorReason, payloadWebhook);
            }
            return null;
        });
    }

    public boolean finalizeReconciledRefund(UUID transactionId, UUID paiementWaveId, String refundRef, BigDecimal refundedAmount, String motif) {
        return Boolean.TRUE.equals(executeInTransaction(() -> {
            LocalDateTime confirmNow = now();
            TransactionEntity tx = lockAnnonceThenTransaction(transactionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction introuvable"));
            if (tx.getStatutSequestre() != StatutSequestre.BLOQUE || tx.getStatutBail() == StatutBail.SIGNE_COMPLET) {
                updatePaiementWaveStateRequiresNew(
                        paiementWaveId,
                        refundRef,
                        StatutPaiementWave.ECHOUE,
                        "Réconciliation remboursement impossible (statut=" + tx.getStatutSequestre() + ", bail=" + tx.getStatutBail() + ")",
                        null
                );
                return false;
            }
            tx.setReferenceRemboursementWave(refundRef);
            tx.setMontantRembourse(refundedAmount);
            tx.setDateRemboursement(confirmNow);
            tx.setMotifRemboursement(motif != null ? motif : "Remboursement réconcilié");
            tx.setDateCloture(confirmNow);
            tx.setMotifCloture(tx.getMotifRemboursement());
            tx.setStatutSequestre(StatutSequestre.REMBOURSE);
            tx.setDossierActif(Boolean.FALSE);
            AnnonceEntity annonce = tx.getAnnonce();
            if (annonce != null && annonce.getStatutPublication() == StatutPublication.RESERVEE) {
                stateMachine().appliquerTransitionTransaction(annonce, StatutPublication.DISPONIBLE);
                annonceRepository.save(annonce);
            }
            markPaiementWaveConfirmedLocked(paiementWaveId, refundRef, confirmNow, null);
            transactionRepository.saveAndFlush(tx);
            return true;
        }));
    }

    public TransactionResponseDTO genererOuRegenererPdfBail(UUID transactionId, String actorEmail, boolean isAdmin) {
        PdfGenerationOutcome outcome = executeInTransaction(() -> {
            TransactionEntity tx = lockAnnonceThenTransaction(transactionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction introuvable"));

            boolean estLocataire = tx.getLocataire() != null && actorEmail != null
                    && tx.getLocataire().getEmail().equalsIgnoreCase(actorEmail);
            boolean estProprietaire = tx.getProprietaire() != null && actorEmail != null
                    && tx.getProprietaire().getEmail().equalsIgnoreCase(actorEmail);
            if (!isAdmin && !estLocataire && !estProprietaire) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Accès refusé : vous n'êtes pas partie prenante de cette transaction.");
            }

            if (tx.getStatutSequestre() != StatutSequestre.LIBERE
                    || tx.getStatutBail() != StatutBail.SIGNE_COMPLET) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Le PDF du contrat de bail n'est générable qu'après double signature complète et libération du séquestre.");
            }

            if (tx.getStatutDocumentBail() == StatutDocumentBail.GENERE && fichierPdfExiste(tx)) {
                return new PdfGenerationOutcome(transactionMapper.toResponseDTO(tx), null);
            }

            boolean ok = tenterGenerationPdf(tx);
            TransactionEntity persisted = transactionRepository.save(tx);
            TransactionEntity effective = persisted != null ? persisted : tx;
            return new PdfGenerationOutcome(
                    transactionMapper.toResponseDTO(effective),
                    ok ? null : effective.getPdfBailErreur()
            );
        });

        if (outcome.errorMessage() != null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Échec temporaire de la génération du PDF du bail : " + outcome.errorMessage()
            );
        }
        return outcome.dto();
    }

    public int reprendreGenerationsPdfEnEchec(int maxTentatives, int batchSize) {
        int safeMaxTentatives = Math.max(1, maxTentatives);
        int safeBatchSize = Math.max(1, batchSize);
        List<UUID> candidateIds = transactionRepository.findIdsEligiblePdfRecovery(
                safeMaxTentatives,
                PageRequest.of(0, safeBatchSize)
        );
        int recovered = 0;
        for (UUID txId : candidateIds) {
            try {
                Boolean ok = executeInTransaction(() -> {
                    TransactionEntity tx = lockAnnonceThenTransaction(txId).orElse(null);
                    if (tx == null
                            || tx.getStatutSequestre() != StatutSequestre.LIBERE
                            || tx.getStatutBail() != StatutBail.SIGNE_COMPLET) {
                        return Boolean.FALSE;
                    }
                    if (tx.getStatutDocumentBail() == StatutDocumentBail.GENERE && fichierPdfExiste(tx)) {
                        return Boolean.FALSE;
                    }
                    int attempts = tx.getPdfBailTentatives() != null ? tx.getPdfBailTentatives() : 0;
                    if (attempts >= safeMaxTentatives) {
                        return Boolean.FALSE;
                    }
                    boolean generated = tenterGenerationPdf(tx);
                    transactionRepository.save(tx);
                    return generated;
                });
                if (Boolean.TRUE.equals(ok)) {
                    recovered++;
                }
            } catch (Exception e) {
                log.error("[PDF BAIL REPRISE] Erreur inattendue lors de la reprise pour transaction {}", txId, e);
            }
        }
        return recovered;
    }

    private boolean tenterGenerationPdf(TransactionEntity tx) {
        tx.setStatutDocumentBail(StatutDocumentBail.A_GENERER);
        int nextAttempt = (tx.getPdfBailTentatives() != null ? tx.getPdfBailTentatives() : 0) + 1;
        tx.setPdfBailTentatives(nextAttempt);
        tx.setPdfBailDerniereTentative(now());
        try {
            String pdfPath = pdfBailGeneratorService.generatePdfBail(tx);
            if (pdfPath == null || pdfPath.isBlank()) {
                throw new IllegalStateException("Le chemin PDF généré est vide ou nul");
            }
            tx.setPdfBailPath(pdfPath);
            tx.setStatutDocumentBail(StatutDocumentBail.GENERE);
            tx.setPdfBailErreur(null);
            return true;
        } catch (Exception e) {
            String msg = e.getMessage() != null && !e.getMessage().isBlank()
                    ? e.getMessage()
                    : e.getClass().getSimpleName();
            tx.setPdfBailPath(null);
            tx.setStatutDocumentBail(StatutDocumentBail.ECHEC);
            tx.setPdfBailErreur(truncateError(msg));
            log.error("Échec de la génération du PDF du bail pour la transaction {} (tentative {}): {}",
                    tx.getId(), nextAttempt, msg, e);
            return false;
        }
    }

    private boolean fichierPdfExiste(TransactionEntity tx) {
        if (tx.getPdfBailPath() == null || tx.getPdfBailPath().isBlank()) {
            return false;
        }
        try {
            Path path = Path.of(tx.getPdfBailPath());
            return Files.isRegularFile(path) && Files.size(path) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private record PdfGenerationOutcome(TransactionResponseDTO dto, String errorMessage) {}

    private record PaymentClaim(UUID paiementWaveId, UUID transactionId, BigDecimal montant, String telephone, boolean expired) {}

    private record RefundClaim(UUID paiementWaveId, UUID transactionId, String referencePaiementInitial, BigDecimal montant) {}

    public record PaymentConfirmationResult(TransactionResponseDTO dto, boolean latePayment, String lateReason, boolean stateUpdated) {}
}
