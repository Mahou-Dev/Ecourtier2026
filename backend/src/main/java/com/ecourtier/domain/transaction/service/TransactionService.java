package com.ecourtier.domain.transaction.service;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.rendezvous.entity.RendezVousEntity;
import com.ecourtier.domain.rendezvous.entity.StatutRendezVous;
import com.ecourtier.domain.rendezvous.repository.RendezVousRepository;
import com.ecourtier.domain.transaction.client.WaveCheckoutResponse;
import com.ecourtier.domain.transaction.client.WaveClient;
import com.ecourtier.domain.transaction.client.WaveRefundResponse;
import com.ecourtier.domain.transaction.dto.RemboursementRequestDTO;
import com.ecourtier.domain.transaction.dto.TransactionRequestDTO;
import com.ecourtier.domain.transaction.dto.TransactionResponseDTO;
import com.ecourtier.domain.transaction.entity.StatutBail;
import com.ecourtier.domain.transaction.entity.StatutSequestre;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import com.ecourtier.domain.transaction.mapper.TransactionMapper;
import com.ecourtier.domain.transaction.repository.TransactionRepository;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    private final TransactionRepository transactionRepository;
    private final RendezVousRepository rendezVousRepository;
    private final AnnonceRepository annonceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final TransactionMapper transactionMapper;
    private final WaveClient waveClient;
    private final PdfBailGeneratorService pdfBailGeneratorService;

    @Value("${ecourtier.vente.acompte-pourcentage:10}")
    private int acomptePourcentage = 10;

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

        // ANO-02 (2) : L'annonce doit être disponible
        if (annonce.getStatutPublication() != StatutPublication.DISPONIBLE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Impossible d'initier une transaction : ce bien n'est pas disponible (statut actuel : "
                            + annonce.getStatutPublication() + ")."
            );
        }

        // ANO-02 (3) : Aucune transaction active incompatible ne doit déjà exister pour cette annonce
        // Les transactions REMBOURSE ou les dossiers INITIALISE historiques inactifs ne bloquent pas une nouvelle opération
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

        TransactionEntity tx = TransactionEntity.builder()
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(annonce.getUtilisateur())
                .montantCaution(montantCaution)
                .montantPremierLoyer(montantPremierLoyer)
                .fraisDossier(fraisDossier)
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

    @Transactional
    public TransactionResponseDTO effectuerPaiementCaution(UUID transactionId, String userEmail) {
        // Verrouillage pessimiste pour empêcher deux paiements concurrents sur la même transaction
        TransactionEntity tx = transactionRepository.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction introuvable"));

        // 0. Protection stricte contre toute opération sur un dossier historique inactif
        if (!Boolean.TRUE.equals(tx.getDossierActif())) {
            log.warn("[PAIEMENT SÉQUESTRE REFUSÉ] Tentative de paiement sur un dossier historique inactif {}", transactionId);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Impossible d'effectuer le paiement : ce dossier de transaction est inactif."
            );
        }

        // 1. Vérification de l'état métier et protection contre l'écrasement d'une référence déjà confirmée
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

        BigDecimal montantTotalSequestre = tx.getMontantTotalSequestre();
        String telephone = tx.getLocataire() != null ? tx.getLocataire().getTelephone() : null;

        // 2. Initialisation du paiement auprès du prestataire (WaveClient / Mock)
        WaveCheckoutResponse response;
        try {
            response = waveClient.initiateEscrowCheckout(tx.getId(), montantTotalSequestre, telephone);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("[PAIEMENT SÉQUESTRE ÉCHEC] Erreur lors de l'appel au prestataire Wave pour la transaction {}", transactionId, e);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Échec de communication avec le prestataire de paiement Wave. La transaction n'a pas été débitée."
            );
        }

        // 3. Vérification stricte de la réponse et confirmation du paiement (référence, transactionId, montant exact)
        if (response == null
                || !"SUCCESS".equalsIgnoreCase(response.getStatus())
                || response.getWaveReference() == null
                || response.getWaveReference().isBlank()
                || (response.getTransactionId() != null && !tx.getId().equals(response.getTransactionId()))
                || (response.getAmount() != null && montantTotalSequestre.compareTo(response.getAmount()) != 0)
                || !waveClient.verifyPaymentStatus(response.getWaveReference().trim(), tx.getId(), montantTotalSequestre)) {
            log.error("[PAIEMENT SÉQUESTRE NON CONFIRMÉ] Réponse invalide ou non confirmée du prestataire Wave pour la transaction {}", transactionId);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Le paiement de la caution n'a pas été confirmé par le prestataire Wave."
            );
        }

        // 4. Mise à jour de l'état local uniquement après confirmation
        tx.setReferenceWave(response.getWaveReference().trim());
        tx.setDatePaiement(LocalDateTime.now());
        tx.setStatutSequestre(StatutSequestre.BLOQUE);

        // Vérifier si la libération automatique doit se déclencher (si le bail était déjà SIGNE_COMPLET)
        verifierEtDeclencherLiberation(tx);

        TransactionEntity saved = transactionRepository.saveAndFlush(tx);
        return transactionMapper.toResponseDTO(saved);
    }

    @Transactional
    public TransactionResponseDTO rembourserTransaction(UUID transactionId, RemboursementRequestDTO request, String userEmail) {
        // Verrouillage pessimiste pour empêcher deux remboursements concurrents
        TransactionEntity tx = transactionRepository.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction introuvable"));

        // 0. Protection stricte contre toute opération sur un dossier historique inactif
        if (!Boolean.TRUE.equals(tx.getDossierActif())) {
            log.warn("[REMBOURSEMENT SÉQUESTRE REFUSÉ] Tentative de remboursement sur un dossier historique inactif {}", transactionId);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Impossible de rembourser cette transaction : ce dossier est inactif."
            );
        }

        // 1. Vérifier que la transaction n'est pas déjà remboursée
        if (tx.getStatutSequestre() == StatutSequestre.REMBOURSE
                || (tx.getReferenceRemboursementWave() != null && !tx.getReferenceRemboursementWave().isBlank())) {
            log.warn("[REMBOURSEMENT SÉQUESTRE REFUSÉ] Transaction {} déjà remboursée (réf remboursement={})",
                    transactionId, tx.getReferenceRemboursementWave());
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cette transaction a déjà été remboursée."
            );
        }

        // 2. Vérifier que les fonds n'ont pas déjà été libérés au bénéficiaire
        if (tx.getStatutSequestre() == StatutSequestre.LIBERE || tx.getDateLiberation() != null) {
            log.warn("[REMBOURSEMENT SÉQUESTRE REFUSÉ] Fonds déjà libérés pour la transaction {}", transactionId);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Impossible de rembourser cette transaction : les fonds du séquestre ont déjà été libérés au bénéficiaire."
            );
        }

        // 3. Vérifier que l'état autorise un remboursement (séquestre effectivement payé et BLOQUE)
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

        // 4. Appel au prestataire (WaveClient / Mock) pour exécuter le remboursement
        WaveRefundResponse refundResponse;
        try {
            refundResponse = waveClient.initiateEscrowRefund(tx.getId(), referencePaiementInitial, montantARembourser);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("[REMBOURSEMENT SÉQUESTRE ÉCHEC] Erreur lors de l'appel de remboursement Wave pour la transaction {}", transactionId, e);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Échec de communication avec le prestataire Wave lors du remboursement."
            );
        }

        // 5. Confirmation stricte du remboursement par le prestataire avant tout changement de statut :
        //    - statut SUCCESS
        //    - référence de remboursement non vide
        //    - référence de paiement initiale strictement identique à tx.getReferenceWave()
        //    - montant remboursé non nul et strictement égal au montant demandé (compareTo == 0)
        //    - confirmation par waveClient.verifyRefundStatus(refundRef, originalWaveRef, expectedAmount)
        if (refundResponse == null
                || !"SUCCESS".equalsIgnoreCase(refundResponse.getStatus())
                || refundResponse.getRefundReference() == null
                || refundResponse.getRefundReference().isBlank()
                || refundResponse.getOriginalWaveReference() == null
                || !referencePaiementInitial.equals(refundResponse.getOriginalWaveReference().trim())
                || refundResponse.getRefundedAmount() == null
                || montantARembourser.compareTo(refundResponse.getRefundedAmount()) != 0
                || !waveClient.verifyRefundStatus(refundResponse.getRefundReference().trim(), referencePaiementInitial, montantARembourser)) {
            log.error("[REMBOURSEMENT SÉQUESTRE NON CONFIRMÉ] Réponse invalide ou incohérente du prestataire Wave pour la transaction {} (réf attendue={}, réf reçue={}, montant attendu={}, montant reçu={})",
                    transactionId,
                    referencePaiementInitial,
                    refundResponse != null ? refundResponse.getOriginalWaveReference() : null,
                    montantARembourser,
                    refundResponse != null ? refundResponse.getRefundedAmount() : null);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Le remboursement n'a pas été confirmé par le prestataire Wave."
            );
        }

        // 6. Mise à jour de l'état local en préservant la référence du paiement initial
        BigDecimal montantEffectif = refundResponse.getRefundedAmount();
        String motif = (request != null && request.getMotif() != null && !request.getMotif().isBlank())
                ? request.getMotif().trim()
                : "Remboursement du séquestre";

        tx.setReferenceRemboursementWave(refundResponse.getRefundReference().trim());
        tx.setMontantRembourse(montantEffectif);
        tx.setDateRemboursement(LocalDateTime.now());
        tx.setMotifRemboursement(motif);
        tx.setStatutSequestre(StatutSequestre.REMBOURSE);

        AnnonceEntity annonce = tx.getAnnonce();
        if (annonce != null && annonce.getStatutPublication() == StatutPublication.RESERVEE) {
            annonce.setStatutPublication(StatutPublication.DISPONIBLE);
            annonceRepository.save(annonce);
        }

        log.info("[REMBOURSEMENT SÉQUESTRE CONFIRMÉ] Transaction {} remboursée par {}. Réf paiement initial: {}, Réf remboursement: {}, Montant: {} FCFA, Motif: {}",
                tx.getId(), userEmail, tx.getReferenceWave(), tx.getReferenceRemboursementWave(), tx.getMontantRembourse(), motif);

        TransactionEntity saved = transactionRepository.saveAndFlush(tx);
        return transactionMapper.toResponseDTO(saved);
    }

    @Transactional
    public TransactionResponseDTO signerBail(UUID transactionId, String userEmail) {
        TransactionEntity tx = transactionRepository.findByIdForUpdate(transactionId)
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

        if (tx.getStatutSequestre() == StatutSequestre.LIBERE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ce contrat a déjà été entièrement signé et les fonds ont été libérés."
            );
        }

        boolean isLocataire = tx.getLocataire() != null && tx.getLocataire().getEmail().equalsIgnoreCase(userEmail);
        boolean isProprietaire = tx.getProprietaire() != null && tx.getProprietaire().getEmail().equalsIgnoreCase(userEmail);

        LocalDateTime now = LocalDateTime.now();

        if (isLocataire) {
            if (tx.getDateSignatureLocataire() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vous avez déjà signé ce contrat de bail.");
            }
            tx.setDateSignatureLocataire(now);
            if (tx.getStatutBail() == StatutBail.NON_SIGNE) {
                tx.setStatutBail(StatutBail.SIGNE_LOCATAIRE);
            } else if (tx.getStatutBail() == StatutBail.SIGNE_PROPRIETAIRE) {
                tx.setStatutBail(StatutBail.SIGNE_COMPLET);
            }
        } else if (isProprietaire) {
            if (tx.getDateSignatureProprietaire() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vous avez déjà signé ce contrat de bail.");
            }
            tx.setDateSignatureProprietaire(now);
            if (tx.getStatutBail() == StatutBail.NON_SIGNE) {
                tx.setStatutBail(StatutBail.SIGNE_PROPRIETAIRE);
            } else if (tx.getStatutBail() == StatutBail.SIGNE_LOCATAIRE) {
                tx.setStatutBail(StatutBail.SIGNE_COMPLET);
            }
        } else {
            // Si admin force la signature
            if (tx.getDateSignatureLocataire() == null) {
                tx.setDateSignatureLocataire(now);
                tx.setStatutBail(StatutBail.SIGNE_LOCATAIRE);
            } else if (tx.getDateSignatureProprietaire() == null) {
                tx.setDateSignatureProprietaire(now);
                tx.setStatutBail(StatutBail.SIGNE_COMPLET);
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce contrat de bail a déjà été entièrement signé.");
            }
        }

        // Vérifier si la libération automatique doit se déclencher
        verifierEtDeclencherLiberation(tx);

        TransactionEntity saved = transactionRepository.saveAndFlush(tx);
        return transactionMapper.toResponseDTO(saved);
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
                tx.setDateLiberation(LocalDateTime.now());
            }

            // Mise à jour du statut de l'annonce en LOUEE ou VENDUE
            AnnonceEntity annonce = tx.getAnnonce();
            if (annonce != null) {
                if (annonce.getModeTransaction() == ModeTransaction.VENTE) {
                    annonce.setStatutPublication(StatutPublication.VENDUE);
                } else {
                    annonce.setStatutPublication(StatutPublication.LOUEE);
                }
                annonceRepository.save(annonce);
            }

            // Génération du PDF du bail uniquement s'il n'a pas déjà été généré
            if (tx.getPdfBailPath() == null || tx.getPdfBailPath().isBlank()) {
                try {
                    String pdfPath = pdfBailGeneratorService.generatePdfBail(tx);
                    tx.setPdfBailPath(pdfPath);
                } catch (Exception e) {
                    log.error("Échec de la génération du PDF lors de la libération automatique pour la transaction {}", tx.getId(), e);
                }
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
}
