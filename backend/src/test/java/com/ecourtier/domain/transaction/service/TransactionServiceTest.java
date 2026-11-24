package com.ecourtier.domain.transaction.service;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.annonce.service.AnnonceStatutStateMachine;
import com.ecourtier.domain.rendezvous.repository.RendezVousRepository;
import com.ecourtier.domain.transaction.client.WaveCheckoutResponse;
import com.ecourtier.domain.transaction.client.WaveRefundResponse;
import com.ecourtier.domain.transaction.dto.RemboursementRequestDTO;
import com.ecourtier.domain.transaction.entity.StatutBail;
import com.ecourtier.domain.transaction.entity.StatutSequestre;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import com.ecourtier.domain.transaction.gateway.PaymentGateway;
import com.ecourtier.domain.transaction.gateway.PaymentGateway.ExternalPaymentState;
import com.ecourtier.domain.transaction.gateway.PaymentGateway.PaymentInitRequest;
import com.ecourtier.domain.transaction.gateway.PaymentGateway.PaymentInitResponse;
import com.ecourtier.domain.transaction.gateway.PaymentGateway.PaymentRefundResult;
import com.ecourtier.domain.transaction.gateway.PaymentGateway.PaymentStatusResult;
import com.ecourtier.domain.transaction.mapper.TransactionMapper;
import com.ecourtier.domain.transaction.repository.TransactionRepository;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private RendezVousRepository rendezVousRepository;

    @Mock
    private AnnonceRepository annonceRepository;

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Spy
    private TransactionMapper transactionMapper = new TransactionMapper();

    @Spy
    private AnnonceStatutStateMachine annonceStatutStateMachine = new AnnonceStatutStateMachine();

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PdfBailGeneratorService pdfBailGeneratorService;

    @InjectMocks
    private TransactionService transactionService;

    private UUID txId;
    private UtilisateurEntity locataire;
    private UtilisateurEntity proprietaire;
    private AnnonceEntity annonce;

    @BeforeEach
    void setUp() {
        txId = UUID.randomUUID();
        locataire = UtilisateurEntity.builder()
                .id(UUID.randomUUID())
                .nom("Diop")
                .prenom("Awa")
                .email("loc@ecourtier.sn")
                .telephone("+221771112233")
                .role(Role.LOCATAIRE)
                .build();

        proprietaire = UtilisateurEntity.builder()
                .id(UUID.randomUUID())
                .nom("Sow")
                .prenom("Ousmane")
                .email("prop@ecourtier.sn")
                .telephone("+221772223344")
                .role(Role.PROPRIETAIRE)
                .build();

        annonce = AnnonceEntity.builder()
                .id(UUID.randomUUID())
                .titre("Appartement Mermoz")
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(250000))
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(StatutPublication.DISPONIBLE)
                .utilisateur(proprietaire)
                .build();
    }

    @Test
    @DisplayName("ANO-01 : Un échec du prestataire lors du paiement ne marque jamais la transaction comme BLOQUE")
    void testEchecPrestatairePaiementNeMarquePasTransactionPayee() {
        TransactionEntity tx = TransactionEntity.builder()
                .id(txId)
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(250000))
                .montantPremierLoyer(BigDecimal.valueOf(250000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.INITIALISE)
                .statutBail(StatutBail.NON_SIGNE)
                .build();

        when(transactionRepository.findByIdForUpdate(txId)).thenReturn(Optional.of(tx));
        when(paymentGateway.initiatePayment(any(PaymentInitRequest.class)))
                .thenThrow(new RuntimeException("Gateway API Timeout"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> transactionService.effectuerPaiementCaution(txId, "loc@ecourtier.sn"));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
        assertEquals(StatutSequestre.INITIALISE, tx.getStatutSequestre());
        assertNull(tx.getReferenceWave());
        assertNull(tx.getDatePaiement());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("ANO-01 : Une réponse prestataire non confirmée (FAILED) refuse le paiement et conserve INITIALISE")
    void testReponsePrestataireFailedNeMarquePasTransactionPayee() {
        TransactionEntity tx = TransactionEntity.builder()
                .id(txId)
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(250000))
                .montantPremierLoyer(BigDecimal.valueOf(250000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.INITIALISE)
                .statutBail(StatutBail.NON_SIGNE)
                .build();

        when(transactionRepository.findByIdForUpdate(txId)).thenReturn(Optional.of(tx));
        when(paymentGateway.initiatePayment(any(PaymentInitRequest.class)))
                .thenReturn(PaymentInitResponse.builder()
                        .externalReference("SIM-FAILED-001")
                        .state(ExternalPaymentState.FAILED)
                        .simulated(true)
                        .build());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> transactionService.effectuerPaiementCaution(txId, "loc@ecourtier.sn"));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
        assertEquals(StatutSequestre.INITIALISE, tx.getStatutSequestre());
        assertNull(tx.getReferenceWave());
        assertNull(tx.getDatePaiement());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("ANO-01 : Deuxième tentative de paiement sur une transaction déjà BLOQUE refusée sans appeler PaymentGateway ni régénérer le PDF")
    void testDeuxiemePaiementRefuseSansAppelPrestataire() {
        TransactionEntity tx = TransactionEntity.builder()
                .id(txId)
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(250000))
                .montantPremierLoyer(BigDecimal.valueOf(250000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.BLOQUE)
                .referenceWave("SIM-LOCAL-INITIAL1")
                .statutBail(StatutBail.NON_SIGNE)
                .build();

        when(transactionRepository.findByIdForUpdate(txId)).thenReturn(Optional.of(tx));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> transactionService.effectuerPaiementCaution(txId, "loc@ecourtier.sn"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("SIM-LOCAL-INITIAL1", tx.getReferenceWave());
        verify(paymentGateway, never()).initiatePayment(any());
        verify(pdfBailGeneratorService, never()).generatePdfBail(any());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("ANO-09 : Un échec du prestataire lors du remboursement ne passe jamais la transaction à REMBOURSE")
    void testEchecPrestataireRemboursementConserveStatutBloque() {
        TransactionEntity tx = TransactionEntity.builder()
                .id(txId)
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(250000))
                .montantPremierLoyer(BigDecimal.valueOf(250000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.BLOQUE)
                .referenceWave("SIM-LOCAL-PAID123")
                .statutBail(StatutBail.NON_SIGNE)
                .build();

        when(transactionRepository.findByIdForUpdate(txId)).thenReturn(Optional.of(tx));
        when(paymentGateway.initiateRefund(eq(txId), eq("SIM-LOCAL-PAID123"), any()))
                .thenReturn(PaymentRefundResult.builder()
                        .refundReference("SIM-REFUND-ERR")
                        .status("FAILED")
                        .simulated(true)
                        .build());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> transactionService.rembourserTransaction(
                        txId,
                        RemboursementRequestDTO.builder().motif("Annulation").build(),
                        "admin@ecourtier.sn"
                ));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
        assertEquals(StatutSequestre.BLOQUE, tx.getStatutSequestre());
        assertEquals("SIM-LOCAL-PAID123", tx.getReferenceWave());
        assertNull(tx.getReferenceRemboursementWave());
        assertNull(tx.getMontantRembourse());
        assertNull(tx.getDateRemboursement());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("ANO-01 : Le PDF n'est généré qu'une seule fois lors de la libération et n'est pas écrasé s'il existe déjà")
    void testPdfGenereUneSeuleFoisLorsDeLaLiberation() {
        TransactionEntity tx = TransactionEntity.builder()
                .id(txId)
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(250000))
                .montantPremierLoyer(BigDecimal.valueOf(250000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.INITIALISE)
                .statutBail(StatutBail.SIGNE_COMPLET)
                .build();

        when(transactionRepository.findByIdForUpdate(txId)).thenReturn(Optional.of(tx));
        when(paymentGateway.initiatePayment(any(PaymentInitRequest.class)))
                .thenReturn(PaymentInitResponse.builder()
                        .transactionId(txId)
                        .amount(BigDecimal.valueOf(505000))
                        .providerCode("MOCK_LOCAL")
                        .externalReference("SIM-LOCAL-OK001")
                        .state(ExternalPaymentState.CONFIRMED_SIMULATED)
                        .simulated(true)
                        .zeroDebitGuaranteed(true)
                        .build());
        when(paymentGateway.checkPaymentStatus("SIM-LOCAL-OK001", txId, BigDecimal.valueOf(505000)))
                .thenReturn(PaymentStatusResult.builder()
                        .state(ExternalPaymentState.CONFIRMED_SIMULATED)
                        .externalReference("SIM-LOCAL-OK001")
                        .confirmedAmount(BigDecimal.valueOf(505000))
                        .simulated(true)
                        .build());
        when(pdfBailGeneratorService.generatePdfBail(tx)).thenReturn("uploads/baux/bail_" + txId + ".pdf");
        when(transactionRepository.saveAndFlush(any(TransactionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var res = transactionService.effectuerPaiementCaution(txId, "loc@ecourtier.sn");

        assertEquals(StatutSequestre.LIBERE, res.getStatutSequestre());
        assertEquals("SIM-LOCAL-OK001", res.getReferenceWave());
        assertEquals("TRANSFERT_SIMULE", res.getStatutTransfertProprietaire());
        assertNotNull(res.getDatePaiement());
        assertNotNull(res.getDateLiberation());
        assertEquals("uploads/baux/bail_" + txId + ".pdf", res.getPdfBailPath());
        verify(pdfBailGeneratorService, times(1)).generatePdfBail(tx);

        // Un second appel de paiement ou de signature est rejeté en 409 et ne régénère jamais le PDF
        assertThrows(ResponseStatusException.class,
                () -> transactionService.effectuerPaiementCaution(txId, "loc@ecourtier.sn"));
        assertThrows(ResponseStatusException.class,
                () -> transactionService.signerBail(txId, "loc@ecourtier.sn"));

        verify(pdfBailGeneratorService, times(1)).generatePdfBail(any());
    }

    @Test
    @DisplayName("ANO-09 : Remboursement refusé (502) si originalReference ou refundedAmount ne correspond pas à la transaction")
    void testRemboursementRefuseSiReferenceInitialeOuMontantIncoherent() {
        TransactionEntity tx = TransactionEntity.builder()
                .id(txId)
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(250000))
                .montantPremierLoyer(BigDecimal.valueOf(250000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.BLOQUE)
                .referenceWave("SIM-LOCAL-PAID123")
                .statutBail(StatutBail.NON_SIGNE)
                .dossierActif(Boolean.TRUE)
                .build();

        when(transactionRepository.findByIdForUpdate(txId)).thenReturn(Optional.of(tx));

        // Cas 1 : originalReference incohérente
        when(paymentGateway.initiateRefund(eq(txId), eq("SIM-LOCAL-PAID123"), any()))
                .thenReturn(PaymentRefundResult.builder()
                        .refundReference("SIM-REFUND-001")
                        .originalReference("SIM-LOCAL-AUTRE999")
                        .refundedAmount(BigDecimal.valueOf(505000))
                        .status("SUCCESS")
                        .simulated(true)
                        .build());

        ResponseStatusException exRef = assertThrows(ResponseStatusException.class,
                () -> transactionService.rembourserTransaction(
                        txId,
                        RemboursementRequestDTO.builder().motif("Test ref incohérente").build(),
                        "admin@ecourtier.sn"
                ));
        assertEquals(HttpStatus.BAD_GATEWAY, exRef.getStatusCode());
        assertEquals(StatutSequestre.BLOQUE, tx.getStatutSequestre());
        assertNull(tx.getReferenceRemboursementWave());
        assertNull(tx.getMontantRembourse());
        assertNull(tx.getDateRemboursement());

        // Cas 2 : refundedAmount différent du montant attendu (partiel non autorisé)
        when(paymentGateway.initiateRefund(eq(txId), eq("SIM-LOCAL-PAID123"), any()))
                .thenReturn(PaymentRefundResult.builder()
                        .refundReference("SIM-REFUND-002")
                        .originalReference("SIM-LOCAL-PAID123")
                        .refundedAmount(BigDecimal.valueOf(250000))
                        .status("SUCCESS")
                        .simulated(true)
                        .build());

        ResponseStatusException exAmount = assertThrows(ResponseStatusException.class,
                () -> transactionService.rembourserTransaction(
                        txId,
                        RemboursementRequestDTO.builder().motif("Test montant incohérent").build(),
                        "admin@ecourtier.sn"
                ));
        assertEquals(HttpStatus.BAD_GATEWAY, exAmount.getStatusCode());
        assertEquals(StatutSequestre.BLOQUE, tx.getStatutSequestre());
        assertNull(tx.getReferenceRemboursementWave());
        assertNull(tx.getMontantRembourse());
        assertNull(tx.getDateRemboursement());

        // Cas 3 : verifyRefundStatus (surcharge à 3 arguments) retourne false
        when(paymentGateway.initiateRefund(eq(txId), eq("SIM-LOCAL-PAID123"), any()))
                .thenReturn(PaymentRefundResult.builder()
                        .refundReference("SIM-REFUND-003")
                        .originalReference("SIM-LOCAL-PAID123")
                        .refundedAmount(new BigDecimal("505000.00"))
                        .status("SUCCESS")
                        .simulated(true)
                        .build());
        when(paymentGateway.verifyRefundStatus("SIM-REFUND-003", "SIM-LOCAL-PAID123", BigDecimal.valueOf(505000)))
                .thenReturn(false);

        ResponseStatusException exVerify = assertThrows(ResponseStatusException.class,
                () -> transactionService.rembourserTransaction(
                        txId,
                        RemboursementRequestDTO.builder().motif("Test non vérifié").build(),
                        "admin@ecourtier.sn"
                ));
        assertEquals(HttpStatus.BAD_GATEWAY, exVerify.getStatusCode());
        assertEquals(StatutSequestre.BLOQUE, tx.getStatutSequestre());
        assertNull(tx.getReferenceRemboursementWave());
        assertNull(tx.getMontantRembourse());
        assertNull(tx.getDateRemboursement());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("LOT 2 : Un dossier inactif (dossier_actif = false) refuse tout paiement, signature ou remboursement (409) sans aucun appel prestataire")
    void testDossierInactifRefusePaiementSignatureEtRemboursementSansAppelWave() {
        TransactionEntity txInactive = TransactionEntity.builder()
                .id(txId)
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(250000))
                .montantPremierLoyer(BigDecimal.valueOf(250000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.INITIALISE)
                .statutBail(StatutBail.NON_SIGNE)
                .dossierActif(Boolean.FALSE)
                .build();

        when(transactionRepository.findByIdForUpdate(txId)).thenReturn(Optional.of(txInactive));

        // 1. Paiement refusé avec 409 CONFLICT
        ResponseStatusException exPay = assertThrows(ResponseStatusException.class,
                () -> transactionService.effectuerPaiementCaution(txId, "loc@ecourtier.sn"));
        assertEquals(HttpStatus.CONFLICT, exPay.getStatusCode());
        assertEquals(StatutSequestre.INITIALISE, txInactive.getStatutSequestre());
        assertNull(txInactive.getReferenceWave());

        // 2. Signature refusée avec 409 CONFLICT
        ResponseStatusException exSign = assertThrows(ResponseStatusException.class,
                () -> transactionService.signerBail(txId, "loc@ecourtier.sn"));
        assertEquals(HttpStatus.CONFLICT, exSign.getStatusCode());
        assertEquals(StatutBail.NON_SIGNE, txInactive.getStatutBail());

        // 3. Remboursement refusé avec 409 CONFLICT
        ResponseStatusException exRefund = assertThrows(ResponseStatusException.class,
                () -> transactionService.rembourserTransaction(
                        txId,
                        RemboursementRequestDTO.builder().motif("Tentative sur dossier inactif").build(),
                        "admin@ecourtier.sn"
                ));
        assertEquals(HttpStatus.CONFLICT, exRefund.getStatusCode());
        assertEquals(StatutSequestre.INITIALISE, txInactive.getStatutSequestre());
        assertNull(txInactive.getReferenceRemboursementWave());

        // 4. Aucun appel au prestataire ni écriture en base
        verify(paymentGateway, never()).initiatePayment(any());
        verify(paymentGateway, never()).initiateRefund(any(), any(), any());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("LOT 2 : Le paiement rejette une référence associée à une autre transaction ou un montant différent (502)")
    void testPaiementRejetteReferenceAutreTransactionOuMontantDifferent() {
        TransactionEntity tx = TransactionEntity.builder()
                .id(txId)
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(250000))
                .montantPremierLoyer(BigDecimal.valueOf(250000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.INITIALISE)
                .statutBail(StatutBail.NON_SIGNE)
                .dossierActif(Boolean.TRUE)
                .build();

        when(transactionRepository.findByIdForUpdate(txId)).thenReturn(Optional.of(tx));

        // Cas 1 : Réponse associée à une autre transaction
        when(paymentGateway.initiatePayment(any(PaymentInitRequest.class)))
                .thenReturn(PaymentInitResponse.builder()
                        .transactionId(UUID.randomUUID())
                        .amount(BigDecimal.valueOf(505000))
                        .externalReference("SIM-LOCAL-OTHER-TX")
                        .state(ExternalPaymentState.CONFIRMED_SIMULATED)
                        .simulated(true)
                        .build());

        ResponseStatusException exOtherTx = assertThrows(ResponseStatusException.class,
                () -> transactionService.effectuerPaiementCaution(txId, "loc@ecourtier.sn"));
        assertEquals(HttpStatus.BAD_GATEWAY, exOtherTx.getStatusCode());
        assertEquals(StatutSequestre.INITIALISE, tx.getStatutSequestre());
        assertNull(tx.getReferenceWave());

        // Cas 2 : Réponse avec un montant différent du montant attendu
        when(paymentGateway.initiatePayment(any(PaymentInitRequest.class)))
                .thenReturn(PaymentInitResponse.builder()
                        .transactionId(txId)
                        .amount(BigDecimal.valueOf(100000))
                        .externalReference("SIM-LOCAL-WRONG-AMT")
                        .state(ExternalPaymentState.CONFIRMED_SIMULATED)
                        .simulated(true)
                        .build());

        ResponseStatusException exWrongAmt = assertThrows(ResponseStatusException.class,
                () -> transactionService.effectuerPaiementCaution(txId, "loc@ecourtier.sn"));
        assertEquals(HttpStatus.BAD_GATEWAY, exWrongAmt.getStatusCode());
        assertEquals(StatutSequestre.INITIALISE, tx.getStatutSequestre());
        assertNull(tx.getReferenceWave());

        // Cas 3 : checkPaymentStatus rejette la correspondance
        when(paymentGateway.initiatePayment(any(PaymentInitRequest.class)))
                .thenReturn(PaymentInitResponse.builder()
                        .transactionId(txId)
                        .amount(BigDecimal.valueOf(505000))
                        .externalReference("SIM-LOCAL-UNVERIFIED")
                        .state(ExternalPaymentState.CONFIRMED_SIMULATED)
                        .simulated(true)
                        .build());
        when(paymentGateway.checkPaymentStatus("SIM-LOCAL-UNVERIFIED", txId, BigDecimal.valueOf(505000)))
                .thenReturn(PaymentStatusResult.builder()
                        .state(ExternalPaymentState.FAILED)
                        .externalReference("SIM-LOCAL-UNVERIFIED")
                        .simulated(true)
                        .build());

        ResponseStatusException exUnverified = assertThrows(ResponseStatusException.class,
                () -> transactionService.effectuerPaiementCaution(txId, "loc@ecourtier.sn"));
        assertEquals(HttpStatus.BAD_GATEWAY, exUnverified.getStatusCode());
        assertEquals(StatutSequestre.INITIALISE, tx.getStatutSequestre());
        assertNull(tx.getReferenceWave());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("MockPaymentGateway / WaveClientMock : Registre interne de paiements et remboursements simulés")
    void testWaveClientMockRegistreInterneEtRejets() {
        com.ecourtier.domain.transaction.client.WaveClientMock mock = new com.ecourtier.domain.transaction.client.WaveClientMock();
        UUID idTx = UUID.randomUUID();
        BigDecimal montant = new BigDecimal("505000.00");

        // 1. Un paiement inconnu est rejeté par verifyPaymentStatus
        org.junit.jupiter.api.Assertions.assertFalse(mock.verifyPaymentStatus("SIM-LOCAL-INCONNU"));

        // 2. Initiation d'un paiement valide -> enregistré et confirmé avec préfixe SIM-LOCAL-
        WaveCheckoutResponse checkout = mock.initiateEscrowCheckout(idTx, montant, "+221771112233");
        org.junit.jupiter.api.Assertions.assertTrue(checkout.getWaveReference().startsWith("SIM-LOCAL-"));
        org.junit.jupiter.api.Assertions.assertTrue(mock.verifyPaymentStatus(checkout.getWaveReference()));
        org.junit.jupiter.api.Assertions.assertTrue(mock.verifyPaymentStatus(checkout.getWaveReference(), idTx, new BigDecimal("505000")));
        org.junit.jupiter.api.Assertions.assertFalse(mock.verifyPaymentStatus(checkout.getWaveReference(), UUID.randomUUID(), montant));
        org.junit.jupiter.api.Assertions.assertFalse(mock.verifyPaymentStatus(checkout.getWaveReference(), idTx, new BigDecimal("100000")));

        // 3. Remboursement sur référence de paiement inconnue -> rejeté
        assertThrows(IllegalStateException.class,
                () -> mock.initiateEscrowRefund(idTx, "SIM-LOCAL-INCONNU", montant));

        // 4. Remboursement d'un montant supérieur au montant payé -> rejeté
        assertThrows(IllegalArgumentException.class,
                () -> mock.initiateEscrowRefund(idTx, checkout.getWaveReference(), new BigDecimal("600000")));

        // 5. Un remboursement non enregistré est rejeté par verifyRefundStatus
        org.junit.jupiter.api.Assertions.assertFalse(mock.verifyRefundStatus("SIM-REFUND-FAUX"));

        // 6. Premier remboursement valide -> enregistré et confirmé avec préfixe SIM-REFUND-
        WaveRefundResponse refund = mock.initiateEscrowRefund(idTx, checkout.getWaveReference(), montant);
        org.junit.jupiter.api.Assertions.assertTrue(refund.getRefundReference().startsWith("SIM-REFUND-"));
        org.junit.jupiter.api.Assertions.assertTrue(mock.verifyRefundStatus(refund.getRefundReference()));
        org.junit.jupiter.api.Assertions.assertTrue(mock.verifyRefundStatus(refund.getRefundReference(), checkout.getWaveReference(), new BigDecimal("505000")));
        org.junit.jupiter.api.Assertions.assertFalse(mock.verifyRefundStatus(refund.getRefundReference(), "SIM-LOCAL-AUTRE", new BigDecimal("505000")));
        org.junit.jupiter.api.Assertions.assertFalse(mock.verifyRefundStatus(refund.getRefundReference(), checkout.getWaveReference(), new BigDecimal("250000")));

        // 7. Deuxième remboursement pour le même paiement initial -> rejeté
        assertThrows(IllegalStateException.class,
                () -> mock.initiateEscrowRefund(idTx, checkout.getWaveReference(), montant));
    }
}
