package com.ecourtier.domain.transaction.gateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

/**
 * Implémentation PayTech (Sandbox uniquement, env=test, target_payment=Wave) de {@link PaymentGateway}.
 *
 * Garanties de sécurité :
 * - Initialisation : retourne systématiquement PENDING (jamais CONFIRMED avant IPN authentifié ou get-status vérifié).
 * - Sandbox strict : rejette env=prod avant tout appel réseau.
 * - Remboursement & transfert propriétaire : simulation locale explicite (aucun mouvement financier réel),
 *   car l'API PayTech ne garantit pas le zéro débit réel en mode test/transfert.
 */
@Component("payTechPaymentGateway")
@ConditionalOnProperty(name = "ecourtier.payment.provider", havingValue = "paytech")
@Slf4j
@RequiredArgsConstructor
public class PayTechPaymentGateway implements PaymentGateway {

    public static final String PROVIDER_CODE = "PAYTECH";

    private final PayTechClient payTechClient;
    private final PayTechPaymentProperties properties;

    @Override
    public String getProviderCode() {
        return PROVIDER_CODE;
    }

    @Override
    public String getDefaultPaymentMethod() {
        return properties.getEffectiveTargetPayment();
    }

    @Override
    public boolean isSimulated() {
        return false;
    }

    @Override
    public boolean isZeroDebitGuaranteed() {
        return properties.isZeroDebitGuaranteed();
    }

    @Override
    public PaymentInitResponse initiatePayment(PaymentInitRequest request) {
        // AUDIT-PT-001 : Verrou backend obligatoire (bloque tout appel réseau si zeroDebitGuaranteed=false)
        properties.validateOutboundPaymentAllowed();

        String refCommand = (request.getRefCommand() != null && !request.getRefCommand().isBlank())
                ? request.getRefCommand().trim()
                : PayTechClient.generateUniqueRefCommand(request.getTransactionId());

        PaymentInitRequest enriched = PaymentInitRequest.builder()
                .transactionId(request.getTransactionId())
                .annonceId(request.getAnnonceId())
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "XOF")
                .itemName(request.getItemName())
                .commandName(request.getCommandName())
                .refCommand(refCommand)
                .customerPhone(request.getCustomerPhone())
                .customerEmail(request.getCustomerEmail())
                .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : getDefaultPaymentMethod())
                .build();

        PayTechClient.PayTechRequestPaymentResult result = payTechClient.requestPayment(enriched);
        boolean zeroDebit = properties.isZeroDebitGuaranteed();

        return PaymentInitResponse.builder()
                .transactionId(enriched.getTransactionId())
                .amount(enriched.getAmount())
                .providerCode(PROVIDER_CODE)
                .paymentMethod(enriched.getPaymentMethod())
                .refCommand(result.getRefCommand())
                .externalReference(result.getToken())
                .externalToken(result.getToken())
                .redirectUrl(zeroDebit ? result.getRedirectUrl() : null)
                .state(ExternalPaymentState.PENDING)
                .simulated(false)
                .zeroDebitGuaranteed(zeroDebit)
                .message("Session PayTech Sandbox initialisée (env=test). En attente de confirmation IPN authentifiée.")
                .build();
    }

    @Override
    public PaymentStatusResult checkPaymentStatus(String externalReferenceOrToken, UUID transactionId, BigDecimal expectedAmount) {
        properties.validateSandboxReady();

        PayTechClient.PayTechStatusQueryResult queryResult = payTechClient.getPaymentStatus(externalReferenceOrToken);
        ExternalPaymentState state = queryResult.getState();

        // AUDIT-PT-007 : Ne jamais confirmer un paiement sans preuve explicite du montant retourné par PayTech
        if (state == ExternalPaymentState.CONFIRMED) {
            if (queryResult.getAmount() == null) {
                log.warn("[PAYTECH SANDBOX] Statut CONFIRMED retourné sans montant lors de get-status token={} tx={} : confirmation refusée (UNKNOWN)",
                        externalReferenceOrToken, transactionId);
                return PaymentStatusResult.builder()
                        .state(ExternalPaymentState.UNKNOWN)
                        .externalReference(externalReferenceOrToken)
                        .confirmedAmount(null)
                        .simulated(false)
                        .message("Statut PayTech sans montant confirmé : en attente d'IPN authentifié")
                        .build();
            }
            if (expectedAmount != null && expectedAmount.compareTo(queryResult.getAmount()) != 0) {
                log.warn("[PAYTECH SANDBOX] Incohérence de montant lors de get-status token={} tx={} (attendu={}, reçu={})",
                        externalReferenceOrToken, transactionId, expectedAmount, queryResult.getAmount());
                return PaymentStatusResult.builder()
                        .state(ExternalPaymentState.FAILED)
                        .externalReference(externalReferenceOrToken)
                        .confirmedAmount(queryResult.getAmount())
                        .simulated(false)
                        .message("Montant PayTech incohérent avec la transaction attendue")
                        .build();
            }
        }

        return PaymentStatusResult.builder()
                .state(state)
                .externalReference(queryResult.getToken() != null ? queryResult.getToken() : externalReferenceOrToken)
                .confirmedAmount(queryResult.getAmount())
                .simulated(false)
                .message(queryResult.getMessage())
                .build();
    }

    @Override
    public PaymentRefundResult initiateRefund(UUID transactionId, String originalReference, BigDecimal amount) {
        properties.assertSandboxOnly();
        if (originalReference == null || originalReference.isBlank()) {
            throw new IllegalArgumentException("Remboursement simulé refusé : référence initiale manquante.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Remboursement simulé refusé : montant invalide.");
        }

        String simRefundRef = "SIM-REFUND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        log.info("[PAYTECH SANDBOX — REMBOURSEMENT SIMULÉ LOCALEMENT] Aucun appel réseau de remboursement réel effectué. tx={} origRef={} montant={} XOF refSimulee={}",
                transactionId, originalReference, amount, simRefundRef);

        return PaymentRefundResult.builder()
                .refundReference(simRefundRef)
                .originalReference(originalReference.trim())
                .refundedAmount(amount)
                .status("SUCCESS")
                .simulated(true)
                .message("Remboursement simulé localement (aucun remboursement réel PayTech déclenché en Sandbox).")
                .build();
    }

    @Override
    public boolean verifyRefundStatus(String refundReference, String originalReference, BigDecimal expectedAmount) {
        return refundReference != null && refundReference.trim().startsWith("SIM-REFUND-")
                && originalReference != null && !originalReference.isBlank()
                && expectedAmount != null && expectedAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    @Override
    public OwnerTransferResult initiateSimulatedOwnerTransfer(UUID transactionId, BigDecimal amount, String destinationPhone) {
        properties.assertSandboxOnly();
        String simTransferRef = "SIM-TRANSFER-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        log.info("[PAYTECH SANDBOX — TRANSFERT PROPRIÉTAIRE SIMULÉ] Aucun virement réel déclenché (API Transfer non simulable sans débit). tx={} montant={} XOF refSimulee={}",
                transactionId, amount, simTransferRef);

        return OwnerTransferResult.builder()
                .transferReference(simTransferRef)
                .amount(amount)
                .status("TRANSFERT_SIMULE")
                .simulated(true)
                .providerCode(PROVIDER_CODE)
                .message("Transfert propriétaire simulé localement (libération logique uniquement — aucun versement réel effectué).")
                .build();
    }
}
