package com.ecourtier.domain.transaction.controller;

import com.ecourtier.domain.transaction.dto.TransactionRequestDTO;
import com.ecourtier.domain.transaction.dto.TransactionResponseDTO;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import com.ecourtier.domain.transaction.repository.TransactionRepository;
import com.ecourtier.domain.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.io.File;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;

    @PostMapping("/annonces/{id}/transactions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TransactionResponseDTO> initierTransaction(
            @PathVariable("id") UUID id,
            @Valid @RequestBody TransactionRequestDTO request,
            Authentication authentication
    ) {
        TransactionResponseDTO response = transactionService.initierTransaction(id, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/transactions/{id}/paiement-caution")
    @PreAuthorize("@transactionSecurityService.isLocataireOrAdmin(#id, authentication)")
    public ResponseEntity<TransactionResponseDTO> effectuerPaiementCaution(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        TransactionResponseDTO response = transactionService.effectuerPaiementCaution(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transactions/{id}/bail/signer")
    @PreAuthorize("@transactionSecurityService.isStakeholderOrAdmin(#id, authentication)")
    public ResponseEntity<TransactionResponseDTO> signerBail(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        TransactionResponseDTO response = transactionService.signerBail(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions/{id}")
    @PreAuthorize("@transactionSecurityService.isStakeholderOrAdmin(#id, authentication)")
    public ResponseEntity<TransactionResponseDTO> obtenirDetail(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        TransactionResponseDTO response = transactionService.obtenirDetail(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions/{id}/bail/pdf")
    @PreAuthorize("@transactionSecurityService.isStakeholderOrAdmin(#id, authentication)")
    public ResponseEntity<Resource> telechargerPdfBail(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        TransactionEntity tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction introuvable"));

        if (tx.getPdfBailPath() == null || tx.getPdfBailPath().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Le PDF du bail n'a pas encore été généré pour cette transaction.");
        }

        File file = new File(tx.getPdfBailPath());
        if (!file.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier PDF introuvable sur le serveur.");
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }
}
