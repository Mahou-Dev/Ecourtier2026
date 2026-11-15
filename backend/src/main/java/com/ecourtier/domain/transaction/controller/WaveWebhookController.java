package com.ecourtier.domain.transaction.controller;

import com.ecourtier.domain.transaction.dto.WaveWebhookResponseDTO;
import com.ecourtier.domain.transaction.service.WavePaymentReconciliationService;
import com.ecourtier.domain.transaction.service.WaveWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Transactions & Séquestre")
public class WaveWebhookController {

    private final WaveWebhookService waveWebhookService;
    private final WavePaymentReconciliationService reconciliationService;

    @PostMapping("/webhooks/wave")
    @Operation(
            summary = "Webhook interne modélisé de confirmation Wave (HMAC-SHA256)",
            description = "Endpoint interne modélisé authentifié par en-tête X-Wave-Signature (HMAC-SHA256 du corps brut). Garantit l'idempotence et le marquage des paiements tardifs (TARDIF_A_REMBOURSER) sans rouvrir une transaction expirée/annulée."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification traitée de manière idempotente ou marquée TARDIF_A_REMBOURSER / ECHOUE"),
            @ApiResponse(responseCode = "400", description = "Payload webhook invalide ou champs obligatoires manquants"),
            @ApiResponse(responseCode = "401", description = "Signature HMAC-SHA256 absente, invalide ou secret non configuré"),
            @ApiResponse(responseCode = "404", description = "Transaction cible introuvable"),
            @ApiResponse(responseCode = "409", description = "Référence Wave déjà utilisée ou conflit de montant/état irréconciliable")
    })
    public ResponseEntity<WaveWebhookResponseDTO> recevoirWebhookWave(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Wave-Signature", required = false) String signatureHeader
    ) {
        WaveWebhookResponseDTO response = waveWebhookService.traiterWebhook(rawPayload, signatureHeader);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/admin/transactions/reconcilier-wave")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Déclencher manuellement la réconciliation Wave (ADMIN)",
            description = "Réconcilie les opérations A_RECONCILIER / EN_COURS expirées et exécute les remboursements en attente des paiements tardifs (TARDIF_A_REMBOURSER)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cycle de réconciliation exécuté"),
            @ApiResponse(responseCode = "401", description = "Authentification JWT requise"),
            @ApiResponse(responseCode = "403", description = "Accès strictement réservé à l'administrateur")
    })
    public ResponseEntity<Map<String, Integer>> reconcilierMaintenant() {
        int processed = reconciliationService.reconcilierPaiementsEnAttente();
        return ResponseEntity.ok(Map.of("operationsTraitees", processed));
    }
}
