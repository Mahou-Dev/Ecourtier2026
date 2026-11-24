package com.ecourtier.domain.transaction.controller;

import com.ecourtier.domain.transaction.dto.AnnulationTransactionRequestDTO;
import com.ecourtier.domain.transaction.dto.RemboursementRequestDTO;
import com.ecourtier.domain.transaction.dto.TransactionRequestDTO;
import com.ecourtier.domain.transaction.dto.TransactionResponseDTO;
import com.ecourtier.domain.transaction.entity.StatutBail;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import com.ecourtier.domain.transaction.repository.TransactionRepository;
import com.ecourtier.domain.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.ecourtier.domain.transaction.entity.StatutSequestre;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.RequestParam;
import java.io.File;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Transactions & Séquestre", description = "Paiement via PayTech Sandbox (Wave) ou simulation locale, double signature contractuelle avec empreinte SHA-256 et génération PDF")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;

    @PostMapping("/annonces/{id}/transactions")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Initier une transaction (location ou réservation d'achat)",
            description = "Requiert une visite EFFECTUE préalable. Les montants sont calculés côté serveur (LOCATION : caution + 1er loyer + 5 000 FCFA ; VENTE : acompte de 10 % du prix + 5 000 FCFA)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transaction initialisée avec échéance 24h"),
            @ApiResponse(responseCode = "400", description = "Prérequis métier manquant (visite EFFECTUE, annonce non vérifiée ou propriétaire identique)"),
            @ApiResponse(responseCode = "401", description = "Authentification JWT requise ou compte désactivé"),
            @ApiResponse(responseCode = "404", description = "Annonce introuvable"),
            @ApiResponse(responseCode = "409", description = "Annonce indisponible ou transaction active déjà existante")
    })
    public ResponseEntity<TransactionResponseDTO> initierTransaction(
            @PathVariable("id") UUID id,
            @Valid @RequestBody(required = false) TransactionRequestDTO request,
            Authentication authentication
    ) {
        TransactionResponseDTO response = transactionService.initierTransaction(id, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/transactions/{id}/paiement-caution")
    @PreAuthorize("@transactionSecurityService.isLocataireOrAdmin(#id, authentication)")
    @Operation(
            summary = "Initier le paiement via PayTech Sandbox (Wave) ou simulation locale",
            description = "En mode PayTech Sandbox (env=test, target_payment=Wave), initialise la session et retourne un statut EN_ATTENTE_CONFIRMATION sans jamais marquer payé avant IPN authentifié. En mode simulation locale (mock), enregistre un paiement simulé (SIM-LOCAL-)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session PayTech initialisée en attente (INITIALISE + EN_ATTENTE_CONFIRMATION) ou paiement simulé localement (BLOQUE)"),
            @ApiResponse(responseCode = "401", description = "Authentification JWT requise ou compte désactivé"),
            @ApiResponse(responseCode = "403", description = "Accès refusé (ni locataire/acheteur concerné ni ADMIN)"),
            @ApiResponse(responseCode = "404", description = "Transaction introuvable"),
            @ApiResponse(responseCode = "409", description = "Transaction expirée, annulée, non INITIALISE ou opération déjà en cours"),
            @ApiResponse(responseCode = "502", description = "Refus ou réponse incohérente du prestataire de paiement"),
            @ApiResponse(responseCode = "503", description = "Configuration Sandbox incomplète/interdite ou opération en attente de réconciliation"),
            @ApiResponse(responseCode = "504", description = "Timeout réseau lors de la communication avec le prestataire")
    })
    public ResponseEntity<TransactionResponseDTO> effectuerPaiementCaution(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        TransactionResponseDTO response = transactionService.effectuerPaiementCaution(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions/{id}/paiement/statut")
    @PreAuthorize("@transactionSecurityService.isStakeholderOrAdmin(#id, authentication)")
    @Operation(
            summary = "Consulter et synchroniser le statut de paiement auprès du prestataire",
            description = "Interroge le prestataire (PayTech get-status en Sandbox) lorsqu'un paiement est en attente de confirmation et retourne l'état actualisé du dossier."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statut de paiement vérifié et retourné"),
            @ApiResponse(responseCode = "401", description = "Authentification JWT requise"),
            @ApiResponse(responseCode = "403", description = "Accès réservé aux parties prenantes ou à l'administrateur"),
            @ApiResponse(responseCode = "404", description = "Transaction introuvable"),
            @ApiResponse(responseCode = "502", description = "Erreur lors de la consultation du prestataire"),
            @ApiResponse(responseCode = "504", description = "Timeout lors de la consultation du prestataire")
    })
    public ResponseEntity<TransactionResponseDTO> verifierStatutPaiement(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        TransactionResponseDTO response = transactionService.verifierStatutPaiementExterne(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transactions/{id}/annuler")
    @PreAuthorize("@transactionSecurityService.isLocataireOrAdmin(#id, authentication)")
    @Operation(
            summary = "Annuler une transaction INITIALISE avant paiement",
            description = "Clôture proprement une transaction INITIALISE non payée (statutSequestre -> ANNULE, dossierActif = false) afin de libérer l'annonce."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transaction annulée et annonce libérée"),
            @ApiResponse(responseCode = "401", description = "Authentification JWT requise ou compte désactivé"),
            @ApiResponse(responseCode = "403", description = "Accès refusé (ni locataire/acheteur concerné ni ADMIN)"),
            @ApiResponse(responseCode = "404", description = "Transaction introuvable"),
            @ApiResponse(responseCode = "409", description = "Annulation impossible (transaction déjà payée, clôturée ou expirée)")
    })
    public ResponseEntity<TransactionResponseDTO> annulerTransaction(
            @PathVariable("id") UUID id,
            @Valid @RequestBody(required = false) AnnulationTransactionRequestDTO request,
            Authentication authentication
    ) {
        TransactionResponseDTO response = transactionService.annulerTransaction(id, request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping({"/transactions/{id}/remboursement", "/transactions/{id}/rembourser"})
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Rembourser le séquestre d'une transaction (ADMIN uniquement)",
            description = "Exécute le remboursement simulé Wave Sandbox d'une transaction à l'état BLOQUE (dont les fonds n'ont pas été libérés), conserve la référence de paiement initial, enregistre la référence et le montant du remboursement et passe statutSequestre à REMBOURSE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Séquestre remboursé (statutSequestre=REMBOURSE, dossierActif=false)"),
            @ApiResponse(responseCode = "401", description = "Authentification JWT requise ou compte désactivé"),
            @ApiResponse(responseCode = "403", description = "Accès strictement réservé à l'administrateur"),
            @ApiResponse(responseCode = "404", description = "Transaction introuvable"),
            @ApiResponse(responseCode = "409", description = "Remboursement refusé (bail déjà SIGNE_COMPLET, fonds LIBERE, dossier déjà remboursé ou non BLOQUE)"),
            @ApiResponse(responseCode = "502", description = "Erreur lors du remboursement auprès du prestataire Wave"),
            @ApiResponse(responseCode = "503", description = "Remboursement Wave incertain en attente de réconciliation")
    })
    public ResponseEntity<TransactionResponseDTO> rembourserTransaction(
            @PathVariable("id") UUID id,
            @Valid @RequestBody(required = false) RemboursementRequestDTO request,
            Authentication authentication
    ) {
        TransactionResponseDTO response = transactionService.rembourserTransaction(id, request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/transactions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Lister toutes les transactions pour la gestion administrative et le remboursement (ADMIN)",
            description = "Retourne la liste paginée de toutes les transactions avec filtrage optionnel par statut de séquestre."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de transactions retournée"),
            @ApiResponse(responseCode = "401", description = "Authentification JWT requise ou compte désactivé"),
            @ApiResponse(responseCode = "403", description = "Accès réservé au rôle ADMIN")
    })
    public ResponseEntity<Page<TransactionResponseDTO>> listerTransactionsAdmin(
            @RequestParam(value = "statut", required = false) StatutSequestre statut,
            @ParameterObject @PageableDefault(size = 20, sort = "dateCreation", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<TransactionResponseDTO> response = transactionService.listerToutesLesTransactions(statut, pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transactions/{id}/bail/signer")
    @PreAuthorize("@transactionSecurityService.isContractParty(#id, authentication)")
    @Operation(
            summary = "Signer le bail ou le récapitulatif de réservation (parties contractantes uniquement)",
            description = "ESCROW-SIGN-001 : strictement réservé au locataire/acheteur et au propriétaire/vendeur authentifiés (ADMIN interdit). Enregistre l'empreinte SHA-256 des termes et la trace de signature. À la double signature (SIGNE_COMPLET), déclenche la libération logique du dossier et un transfert propriétaire simulé."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Signature enregistrée ; libération logique du dossier dès double signature"),
            @ApiResponse(responseCode = "400", description = "Double signature par la même partie refusée"),
            @ApiResponse(responseCode = "401", description = "Authentification JWT requise ou compte désactivé"),
            @ApiResponse(responseCode = "403", description = "Accès refusé aux tiers et aux administrateurs (seules les parties contractantes peuvent signer)"),
            @ApiResponse(responseCode = "404", description = "Transaction introuvable"),
            @ApiResponse(responseCode = "409", description = "Signature impossible : paiement non confirmé, intégrité modifiée ou dossier clôturé/inactif")
    })
    public ResponseEntity<TransactionResponseDTO> signerBail(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        TransactionResponseDTO response = transactionService.signerBail(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions/{id}/bail/apercu-pdf")
    @PreAuthorize("@transactionSecurityService.isStakeholderOrAdmin(#id, authentication)")
    @Operation(
            summary = "Télécharger l'aperçu PDF pré-signature du contrat avec empreinte SHA-256",
            description = "ESCROW-SIGN-002 : permet aux parties de consulter les conditions contractuelles et l'empreinte SHA-256 canonique avant signature."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aperçu PDF pré-signature retourné"),
            @ApiResponse(responseCode = "401", description = "Authentification requise"),
            @ApiResponse(responseCode = "403", description = "Accès réservé aux parties prenantes ou à un ADMIN"),
            @ApiResponse(responseCode = "404", description = "Transaction introuvable"),
            @ApiResponse(responseCode = "409", description = "Aperçu disponible uniquement après confirmation du paiement")
    })
    public ResponseEntity<byte[]> telechargerApercuPdfBail(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        byte[] pdfBytes = transactionService.genererApercuPdfBail(
                id,
                authentication != null ? authentication.getName() : null,
                isAdmin
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"apercu_contrat_" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/transactions/mes-transactions")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Lister mes dossiers de transaction",
            description = "Retourne l'ensemble des transactions impliquant l'utilisateur connecté en tant que locataire/acheteur ou propriétaire/agent."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des transactions de l'utilisateur retournée"),
            @ApiResponse(responseCode = "401", description = "Authentification JWT requise ou compte désactivé")
    })
    public ResponseEntity<java.util.List<TransactionResponseDTO>> listerMesTransactions(
            Authentication authentication
    ) {
        java.util.List<TransactionResponseDTO> response = transactionService.obtenirMesTransactions(authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions/{id}")
    @PreAuthorize("@transactionSecurityService.isStakeholderOrAdmin(#id, authentication)")
    @Operation(
            summary = "Consulter le détail d'une transaction",
            description = "Retourne l'état complet d'un dossier de séquestre et de signature (réservé aux parties prenantes ou à un ADMIN)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail complet de la transaction retourné"),
            @ApiResponse(responseCode = "401", description = "Authentification JWT requise ou compte désactivé"),
            @ApiResponse(responseCode = "403", description = "Accès réservé aux parties prenantes ou à un ADMIN"),
            @ApiResponse(responseCode = "404", description = "Transaction introuvable")
    })
    public ResponseEntity<TransactionResponseDTO> obtenirDetail(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        TransactionResponseDTO response = transactionService.obtenirDetail(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions/{id}/bail/pdf")
    @PreAuthorize("@transactionSecurityService.isStakeholderOrAdmin(#id, authentication)")
    @Operation(
            summary = "Télécharger le PDF récapitulatif généré après double signature",
            description = "Télécharge le contrat de bail numérique (LOCATION) ou le récapitulatif de réservation d'achat (VENTE) généré une fois le statut SIGNE_COMPLET atteint."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Flux binaire du contrat PDF retourné"),
            @ApiResponse(responseCode = "401", description = "Authentification JWT requise ou compte désactivé"),
            @ApiResponse(responseCode = "403", description = "Accès réservé aux parties prenantes ou à un ADMIN"),
            @ApiResponse(responseCode = "404", description = "Transaction ou PDF introuvable"),
            @ApiResponse(responseCode = "503", description = "Échec technique temporaire lors de la régénération à la volée du PDF")
    })
    public ResponseEntity<Resource> telechargerPdfBail(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        TransactionEntity tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction introuvable"));

        File file = (tx.getPdfBailPath() != null && !tx.getPdfBailPath().trim().isEmpty())
                ? new File(tx.getPdfBailPath())
                : null;

        if ((file == null || !file.exists())
                && tx.getStatutSequestre() == StatutSequestre.LIBERE
                && tx.getStatutBail() == StatutBail.SIGNE_COMPLET) {
            boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            TransactionResponseDTO recovered = transactionService.genererOuRegenererPdfBail(
                    id,
                    authentication != null ? authentication.getName() : null,
                    isAdmin
            );
            file = recovered.getPdfBailPath() != null ? new File(recovered.getPdfBailPath()) : null;
        }

        if (file == null || !file.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Le PDF récapitulatif n'a pas encore été généré pour cette transaction.");
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }

    @PostMapping("/transactions/{id}/bail/pdf/generer")
    @PreAuthorize("@transactionSecurityService.isStakeholderOrAdmin(#id, authentication)")
    @Operation(
            summary = "Générer ou régénérer le contrat de bail PDF",
            description = "Opération idempotente réservée aux parties prenantes et à l'administrateur pour générer ou relancer la génération du PDF lorsque la transaction est LIBERE et SIGNE_COMPLET."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF généré ou déjà disponible"),
            @ApiResponse(responseCode = "401", description = "Authentification requise"),
            @ApiResponse(responseCode = "403", description = "Accès réservé aux parties prenantes ou à l'administrateur"),
            @ApiResponse(responseCode = "404", description = "Transaction introuvable"),
            @ApiResponse(responseCode = "409", description = "Transaction non éligible à la génération du PDF"),
            @ApiResponse(responseCode = "503", description = "Échec technique temporaire lors de la génération PDF (tentative tracée)")
    })
    public ResponseEntity<TransactionResponseDTO> genererPdfBail(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        TransactionResponseDTO response = transactionService.genererOuRegenererPdfBail(
                id,
                authentication != null ? authentication.getName() : null,
                isAdmin
        );
        return ResponseEntity.ok(response);
    }
}
