package com.ecourtier.domain.transaction.service;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.rendezvous.entity.RendezVousEntity;
import com.ecourtier.domain.rendezvous.entity.StatutRendezVous;
import com.ecourtier.domain.rendezvous.repository.RendezVousRepository;
import com.ecourtier.domain.transaction.client.WaveCheckoutResponse;
import com.ecourtier.domain.transaction.client.WaveClient;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final RendezVousRepository rendezVousRepository;
    private final AnnonceRepository annonceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final TransactionMapper transactionMapper;
    private final WaveClient waveClient;
    private final PdfBailGeneratorService pdfBailGeneratorService;

    @Transactional
    public TransactionResponseDTO initierTransaction(UUID annonceId, TransactionRequestDTO request, String userEmail) {
        UtilisateurEntity locataire = utilisateurRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));

        AnnonceEntity annonce = annonceRepository.findByIdAndSupprimeFalse(annonceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Annonce introuvable"));

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

        TransactionEntity tx = TransactionEntity.builder()
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(annonce.getUtilisateur())
                .montantCaution(request.getMontantCaution())
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.INITIALISE)
                .statutBail(StatutBail.NON_SIGNE)
                .build();

        TransactionEntity saved = transactionRepository.save(tx);
        return transactionMapper.toResponseDTO(saved);
    }

    @Transactional
    public TransactionResponseDTO effectuerPaiementCaution(UUID transactionId, String userEmail) {
        TransactionEntity tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction introuvable"));

        WaveCheckoutResponse response = waveClient.initiateEscrowCheckout(
                tx.getId(),
                tx.getMontantCaution(),
                tx.getLocataire() != null ? tx.getLocataire().getTelephone() : null
        );

        tx.setReferenceWave(response.getWaveReference());
        tx.setStatutSequestre(StatutSequestre.BLOQUE);

        // Vérifier si la libération automatique doit se déclencher
        verifierEtDeclencherLiberation(tx);

        TransactionEntity saved = transactionRepository.save(tx);
        return transactionMapper.toResponseDTO(saved);
    }

    @Transactional
    public TransactionResponseDTO signerBail(UUID transactionId, String userEmail) {
        TransactionEntity tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction introuvable"));

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

        TransactionEntity saved = transactionRepository.save(tx);
        return transactionMapper.toResponseDTO(saved);
    }

    private void verifierEtDeclencherLiberation(TransactionEntity tx) {
        if (tx.getStatutBail() == StatutBail.SIGNE_COMPLET && tx.getStatutSequestre() == StatutSequestre.BLOQUE) {
            log.info("[LIBÉRATION AUTOMATIQUE SÉQUESTRE] Bail signé complet ET séquestre bloqué pour la transaction {}. Libération des fonds...", tx.getId());

            tx.setStatutSequestre(StatutSequestre.LIBERE);
            tx.setDateLiberation(LocalDateTime.now());

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

            // Génération du PDF du bail
            try {
                String pdfPath = pdfBailGeneratorService.generatePdfBail(tx);
                tx.setPdfBailPath(pdfPath);
            } catch (Exception e) {
                log.error("Échec de la génération du PDF lors de la libération automatique pour la transaction {}", tx.getId(), e);
            }
        }
    }

    @Transactional(readOnly = true)
    public TransactionResponseDTO obtenirDetail(UUID transactionId, String userEmail) {
        TransactionEntity tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction introuvable"));
        return transactionMapper.toResponseDTO(tx);
    }
}
