package com.ecourtier.domain.transaction.service;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.rendezvous.repository.RendezVousRepository;
import com.ecourtier.domain.transaction.client.WaveCheckoutResponse;
import com.ecourtier.domain.transaction.client.WaveClient;
import com.ecourtier.domain.transaction.client.WaveRefundResponse;
import com.ecourtier.domain.transaction.dto.RemboursementRequestDTO;
import com.ecourtier.domain.transaction.entity.StatutBail;
import com.ecourtier.domain.transaction.entity.StatutSequestre;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
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

    @Mock
    private WaveClient waveClient;

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
    @DisplayName("ANO-01 : Un échec du prestataire Wave lors du paiement ne marque jamais la transaction comme BLOQUE")
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
        when(waveClient.initiateEscrowCheckout(eq(txId), any(), any()))
                .thenThrow(new RuntimeException("Wave API Timeout"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> transactionService.effectuerPaiementCaution(txId, "loc@ecourtier.sn"));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
        assertEquals(StatutSequestre.INITIALISE, tx.getStatutSequestre());
        assertNull(tx.getReferenceWave());
        assertNull(tx.getDatePaiement());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("ANO-01 : Une réponse Wave non confirmée (FAILED) refuse le paiement et conserve INITIALISE")
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
        when(waveClient.initiateEscrowCheckout(eq(txId), any(), any()))
                .thenReturn(WaveCheckoutResponse.builder()
                        .waveReference("WAVE-FAILED-001")
                        .status("FAILED")
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
    @DisplayName("ANO-01 : Deuxième tentative de paiement sur une transaction déjà BLOQUE refusée sans appeler WaveClient ni régénérer le PDF")
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
                .referenceWave("WAVE-SANDBOX-INITIAL1")
                .statutBail(StatutBail.NON_SIGNE)
                .build();

        when(transactionRepository.findByIdForUpdate(txId)).thenReturn(Optional.of(tx));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> transactionService.effectuerPaiementCaution(txId, "loc@ecourtier.sn"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("WAVE-SANDBOX-INITIAL1", tx.getReferenceWave());
        verify(waveClient, never()).initiateEscrowCheckout(any(), any(), any());
        verify(pdfBailGeneratorService, never()).generatePdfBail(any());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("ANO-09 : Un échec du prestataire Wave lors du remboursement ne passe jamais la transaction à REMBOURSE")
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
                .referenceWave("WAVE-SANDBOX-PAID123")
                .statutBail(StatutBail.NON_SIGNE)
                .build();

        when(transactionRepository.findByIdForUpdate(txId)).thenReturn(Optional.of(tx));
        when(waveClient.initiateEscrowRefund(eq(txId), eq("WAVE-SANDBOX-PAID123"), any()))
                .thenReturn(WaveRefundResponse.builder()
                        .refundReference("WAVE-REFUND-ERR")
                        .status("FAILED")
                        .build());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> transactionService.rembourserTransaction(
                        txId,
                        RemboursementRequestDTO.builder().motif("Annulation").build(),
                        "admin@ecourtier.sn"
                ));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
        assertEquals(StatutSequestre.BLOQUE, tx.getStatutSequestre());
        assertEquals("WAVE-SANDBOX-PAID123", tx.getReferenceWave());
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
        when(waveClient.initiateEscrowCheckout(eq(txId), any(), any()))
                .thenReturn(WaveCheckoutResponse.builder()
                        .transactionId(txId)
                        .amount(BigDecimal.valueOf(505000))
                        .waveReference("WAVE-SANDBOX-OK001")
                        .status("SUCCESS")
                        .build());
        when(waveClient.verifyPaymentStatus("WAVE-SANDBOX-OK001", txId, BigDecimal.valueOf(505000))).thenReturn(true);
        when(pdfBailGeneratorService.generatePdfBail(tx)).thenReturn("uploads/baux/bail_" + txId + ".pdf");
        when(transactionRepository.saveAndFlush(any(TransactionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var res = transactionService.effectuerPaiementCaution(txId, "loc@ecourtier.sn");

        assertEquals(StatutSequestre.LIBERE, res.getStatutSequestre());
        assertEquals("WAVE-SANDBOX-OK001", res.getReferenceWave());
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
    @DisplayName("ANO-09 : Remboursement refusé (502) si originalWaveReference ou refundedAmount ne correspond pas à la transaction")
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
                .referenceWave("WAVE-SANDBOX-PAID123")
                .statutBail(StatutBail.NON_SIGNE)
                .dossierActif(Boolean.TRUE)
                .build();

        when(transactionRepository.findByIdForUpdate(txId)).thenReturn(Optional.of(tx));

        // Cas 1 : originalWaveReference incohérente
        when(waveClient.initiateEscrowRefund(eq(txId), eq("WAVE-SANDBOX-PAID123"), any()))
                .thenReturn(WaveRefundResponse.builder()
                        .refundReference("WAVE-REFUND-SANDBOX-001")
                        .originalWaveReference("WAVE-SANDBOX-AUTRE999")
                        .refundedAmount(BigDecimal.valueOf(505000))
                        .status("SUCCESS")
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
        when(waveClient.initiateEscrowRefund(eq(txId), eq("WAVE-SANDBOX-PAID123"), any()))
                .thenReturn(WaveRefundResponse.builder()
                        .refundReference("WAVE-REFUND-SANDBOX-002")
                        .originalWaveReference("WAVE-SANDBOX-PAID123")
                        .refundedAmount(BigDecimal.valueOf(250000))
                        .status("SUCCESS")
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
        when(waveClient.initiateEscrowRefund(eq(txId), eq("WAVE-SANDBOX-PAID123"), any()))
                .thenReturn(WaveRefundResponse.builder()
                        .refundReference("WAVE-REFUND-SANDBOX-003")
                        .originalWaveReference("WAVE-SANDBOX-PAID123")
                        .refundedAmount(new BigDecimal("505000.00"))
                        .status("SUCCESS")
                        .build());
        when(waveClient.verifyRefundStatus("WAVE-REFUND-SANDBOX-003", "WAVE-SANDBOX-PAID123", BigDecimal.valueOf(505000)))
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
    @DisplayName("LOT 2 : Un dossier inactif (dossier_actif = false) refuse tout paiement, signature ou remboursement (409) sans aucun appel Wave")
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

        // 4. Aucun appel au client Wave ni écriture en base
        verify(waveClient, never()).initiateEscrowCheckout(any(), any(), any());
        verify(waveClient, never()).initiateEscrowRefund(any(), any(), any());
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

        // Cas 1 : Réponse Wave associée à une autre transaction
        when(waveClient.initiateEscrowCheckout(eq(txId), any(), any()))
                .thenReturn(WaveCheckoutResponse.builder()
                        .transactionId(UUID.randomUUID())
                        .amount(BigDecimal.valueOf(505000))
                        .waveReference("WAVE-SANDBOX-OTHER-TX")
                        .status("SUCCESS")
                        .build());

        ResponseStatusException exOtherTx = assertThrows(ResponseStatusException.class,
                () -> transactionService.effectuerPaiementCaution(txId, "loc@ecourtier.sn"));
        assertEquals(HttpStatus.BAD_GATEWAY, exOtherTx.getStatusCode());
        assertEquals(StatutSequestre.INITIALISE, tx.getStatutSequestre());
        assertNull(tx.getReferenceWave());

        // Cas 2 : Réponse Wave avec un montant différent du montant attendu
        when(waveClient.initiateEscrowCheckout(eq(txId), any(), any()))
                .thenReturn(WaveCheckoutResponse.builder()
                        .transactionId(txId)
                        .amount(BigDecimal.valueOf(100000))
                        .waveReference("WAVE-SANDBOX-WRONG-AMT")
                        .status("SUCCESS")
                        .build());

        ResponseStatusException exWrongAmt = assertThrows(ResponseStatusException.class,
                () -> transactionService.effectuerPaiementCaution(txId, "loc@ecourtier.sn"));
        assertEquals(HttpStatus.BAD_GATEWAY, exWrongAmt.getStatusCode());
        assertEquals(StatutSequestre.INITIALISE, tx.getStatutSequestre());
        assertNull(tx.getReferenceWave());

        // Cas 3 : verifyPaymentStatus(ref, txId, expectedAmount) rejette la correspondance
        when(waveClient.initiateEscrowCheckout(eq(txId), any(), any()))
                .thenReturn(WaveCheckoutResponse.builder()
                        .transactionId(txId)
                        .amount(BigDecimal.valueOf(505000))
                        .waveReference("WAVE-SANDBOX-UNVERIFIED")
                        .status("SUCCESS")
                        .build());
        when(waveClient.verifyPaymentStatus("WAVE-SANDBOX-UNVERIFIED", txId, BigDecimal.valueOf(505000)))
                .thenReturn(false);

        ResponseStatusException exUnverified = assertThrows(ResponseStatusException.class,
                () -> transactionService.effectuerPaiementCaution(txId, "loc@ecourtier.sn"));
        assertEquals(HttpStatus.BAD_GATEWAY, exUnverified.getStatusCode());
        assertEquals(StatutSequestre.INITIALISE, tx.getStatutSequestre());
        assertNull(tx.getReferenceWave());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("WaveClientMock : Registre interne de paiements et remboursements simulés")
    void testWaveClientMockRegistreInterneEtRejets() {
        com.ecourtier.domain.transaction.client.WaveClientMock mock = new com.ecourtier.domain.transaction.client.WaveClientMock();
        UUID idTx = UUID.randomUUID();
        BigDecimal montant = new BigDecimal("505000.00");

        // 1. Un paiement inconnu mais préfixé WAVE- est rejeté par verifyPaymentStatus
        org.junit.jupiter.api.Assertions.assertFalse(mock.verifyPaymentStatus("WAVE-SANDBOX-INCONNU"));

        // 2. Initiation d'un paiement valide -> enregistré et confirmé
        WaveCheckoutResponse checkout = mock.initiateEscrowCheckout(idTx, montant, "+221771112233");
        org.junit.jupiter.api.Assertions.assertTrue(mock.verifyPaymentStatus(checkout.getWaveReference()));
        org.junit.jupiter.api.Assertions.assertTrue(mock.verifyPaymentStatus(checkout.getWaveReference(), idTx, new BigDecimal("505000")));
        org.junit.jupiter.api.Assertions.assertFalse(mock.verifyPaymentStatus(checkout.getWaveReference(), UUID.randomUUID(), montant));
        org.junit.jupiter.api.Assertions.assertFalse(mock.verifyPaymentStatus(checkout.getWaveReference(), idTx, new BigDecimal("100000")));

        // 3. Remboursement sur référence de paiement inconnue -> rejeté
        assertThrows(IllegalStateException.class,
                () -> mock.initiateEscrowRefund(idTx, "WAVE-SANDBOX-INCONNU", montant));

        // 4. Remboursement d'un montant supérieur au montant payé -> rejeté
        assertThrows(IllegalArgumentException.class,
                () -> mock.initiateEscrowRefund(idTx, checkout.getWaveReference(), new BigDecimal("600000")));

        // 5. Un remboursement non enregistré mais préfixé WAVE-REFUND- est rejeté par verifyRefundStatus
        org.junit.jupiter.api.Assertions.assertFalse(mock.verifyRefundStatus("WAVE-REFUND-SANDBOX-FAUX"));

        // 6. Premier remboursement valide -> enregistré et confirmé
        WaveRefundResponse refund = mock.initiateEscrowRefund(idTx, checkout.getWaveReference(), montant);
        org.junit.jupiter.api.Assertions.assertTrue(mock.verifyRefundStatus(refund.getRefundReference()));
        org.junit.jupiter.api.Assertions.assertTrue(mock.verifyRefundStatus(refund.getRefundReference(), checkout.getWaveReference(), new BigDecimal("505000")));
        org.junit.jupiter.api.Assertions.assertFalse(mock.verifyRefundStatus(refund.getRefundReference(), "WAVE-SANDBOX-AUTRE", new BigDecimal("505000")));
        org.junit.jupiter.api.Assertions.assertFalse(mock.verifyRefundStatus(refund.getRefundReference(), checkout.getWaveReference(), new BigDecimal("250000")));

        // 7. Deuxième remboursement pour le même paiement initial -> rejeté
        assertThrows(IllegalStateException.class,
                () -> mock.initiateEscrowRefund(idTx, checkout.getWaveReference(), montant));
    }
}
