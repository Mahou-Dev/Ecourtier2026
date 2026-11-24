import { test, expect } from '@playwright/test';

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:4173';

test.describe('MISSION RENDER-PAYTECH-01 — Scénario 19 : Frontend honnête (ESCROW-FE-001 / ESCROW-SIGN-001 / ESCROW-ARCH-001)', () => {
  test('1. Initialisation PayTech Sandbox (EN_ATTENTE_CONFIRMATION) : aucune fausse confirmation, aucune redirection si zeroDebitGaranti=false, puis polling de statut', async ({ page }) => {
    const locataireAuth = {
      utilisateur: {
        id: 'loc-pt-01',
        email: 'locataire.paytech@test.sn',
        prenom: 'Awa',
        nom: 'Diouf',
        role: 'LOCATAIRE',
        actif: true,
      },
      accessToken: 'mock-loc-pt-jwt',
      refreshToken: 'mock-loc-pt-refresh',
    };

    const currentTx: Record<string, unknown> = {
      id: 'tx-paytech-pending-01',
      annonceId: 'ann-pt-01',
      annonceTitre: 'Appartement F3 Mermoz PayTech Sandbox',
      modeTransaction: 'LOCATION',
      annoncePrix: 250000,
      locataireId: 'loc-pt-01',
      locataireNomComplet: 'Awa Diouf',
      proprietaireId: 'prop-pt-01',
      proprietaireNomComplet: 'Cheikh Ndiaye',
      montantCaution: 250000,
      montantPremierLoyer: 250000,
      fraisDossier: 5000,
      montantTotalSequestre: 505000,
      montantVerseProprietaire: 500000,
      statutSequestre: 'INITIALISE',
      statutBail: 'NON_SIGNE',
      dossierActif: true,
      prestatairePaiement: 'PAYTECH',
      moyenPaiement: 'Wave',
      modeSimulationPaiement: false,
      zeroDebitGaranti: false,
      dateCreation: '2026-11-24T10:00:00',
      dateExpiration: '2026-11-25T10:00:00',
    };

    let statusChecks = 0;

    await page.route('**/api/v1/**', async (route) => {
      const url = route.request().url();
      const method = route.request().method();

      if (url.includes('/transactions/mes-transactions')) {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([currentTx]),
        });
      }
      if (url.includes('/transactions/tx-paytech-pending-01/paiement-caution') && method === 'POST') {
        currentTx.statutSequestre = 'INITIALISE';
        currentTx.statutPaiementExterne = 'EN_ATTENTE_CONFIRMATION';
        currentTx.refCommandPaytech = 'ECOURTIER-SBX-001';
        currentTx.tokenPaiementExterne = 'tok_sbx_pending_001';
        currentTx.redirectUrlPaiement = 'https://paytech.sn/payment/checkout/tok_sbx_pending_001';
        currentTx.zeroDebitGaranti = false;
        currentTx.modeSimulationPaiement = false;
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(currentTx),
        });
      }
      if (url.includes('/transactions/tx-paytech-pending-01/paiement/statut') && method === 'GET') {
        statusChecks += 1;
        if (statusChecks === 1) {
          // Toujours en attente lors du 1er polling
          return route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(currentTx),
          });
        }
        // Confirmé via backend authentifié lors du 2e polling
        currentTx.statutSequestre = 'BLOQUE';
        currentTx.statutPaiementExterne = 'CONFIRME';
        currentTx.referenceWave = 'tok_sbx_pending_001';
        currentTx.datePaiement = '2026-11-24T10:05:00';
        currentTx.bailDocumentSha256 = 'a'.repeat(64);
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(currentTx),
        });
      }
      if (url.includes('/annonces') || url.includes('/utilisateurs/moi/favoris')) {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0 }),
        });
      }
      return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });

    await page.addInitScript((auth) => {
      sessionStorage.setItem('ecourtier-auth', JSON.stringify({ state: auth, version: 0 }));
    }, locataireAuth);

    await page.goto(`${BASE_URL}/dashboard`);
    await page.getByRole('button', { name: /Suivi Transactions/i }).click();
    await page.getByRole('button', { name: /Ouvrir le dossier/i }).click();

    const dialog = page.locator('div[role="dialog"]');
    await expect(dialog).toBeVisible();

    // Mention réglementaire honnête (ESCROW-ARCH-001)
    await expect(dialog).toContainText("n'est pas un établissement de séquestre bancaire réglementé");

    // 1. Cliquer sur "Initier le paiement" -> PayTech retourne EN_ATTENTE_CONFIRMATION
    await dialog.getByRole('button', { name: /Initier le paiement .* via PayTech \/ Wave/i }).click();

    // Aucune redirection vers paytech.sn car zeroDebitGaranti=false
    expect(page.url()).toContain('/dashboard');

    // Le panneau d'attente PayTech s'affiche avec avertissement 100-150 FCFA
    const pendingPanel = dialog.getByTestId('paytech-pending-panel');
    await expect(pendingPanel).toBeVisible();
    await expect(pendingPanel).toContainText('ECOURTIER-SBX-001');
    await expect(pendingPanel).toContainText('100 à 150 FCFA');

    // AUCUNE fausse confirmation et signature toujours verrouillée
    await expect(dialog).not.toContainText('Paiement confirmé via PayTech');
    await expect(dialog.getByRole('button', { name: /Signer le bail numérique/i })).toHaveCount(0);

    // 2. Rechargement de page : l'état EN_ATTENTE_CONFIRMATION persiste (jamais converti en succès — AUDIT-PT-010)
    await page.reload();
    await page.getByRole('button', { name: /Suivi Transactions/i }).click();
    await page.getByRole('button', { name: /Ouvrir le dossier/i }).click();
    const reloadedDialog = page.locator('div[role="dialog"]');
    await expect(reloadedDialog).toBeVisible();
    await expect(reloadedDialog.getByTestId('paytech-pending-panel')).toBeVisible();
    await expect(reloadedDialog).not.toContainText('Paiement confirmé via PayTech');
    await expect(reloadedDialog.getByRole('button', { name: /Signer le bail numérique/i })).toHaveCount(0);

    // 3. Premier polling : toujours en attente
    const checkBtn = reloadedDialog.getByRole('button', { name: /Vérifier le statut du paiement/i });
    await checkBtn.click();
    await expect.poll(() => statusChecks).toBe(1);
    await expect(reloadedDialog.getByTestId('paytech-pending-panel')).toBeVisible();
    await expect(reloadedDialog.getByRole('button', { name: /Signer le bail numérique/i })).toHaveCount(0);

    // 4. Second polling : paiement authentifié confirmé par le backend
    await checkBtn.click();
    await expect.poll(() => statusChecks).toBe(2);
    await expect(reloadedDialog).toContainText('Paiement confirmé via PayTech');
    await expect(reloadedDialog).toContainText('a'.repeat(64));
    await expect(reloadedDialog.getByRole('button', { name: /Consulter l'aperçu PDF pré-signature/i })).toBeVisible();
    await expect(reloadedDialog.getByRole('button', { name: /Signer le bail numérique/i })).toBeVisible();
  });

  test('3. AUDIT-PT-006 / AUDIT-PT-010 : Rejet URL de redirection non PayTech et gestion erreur réseau 503', async ({ page }) => {
    const locataireAuth = {
      utilisateur: {
        id: 'loc-pt-03',
        email: 'locataire.err@test.sn',
        prenom: 'Ndeye',
        nom: 'Sene',
        role: 'LOCATAIRE',
        actif: true,
      },
      accessToken: 'mock-loc-err-jwt',
      refreshToken: 'mock-loc-err-refresh',
    };

    let attempt = 0;
    const baseTx: Record<string, unknown> = {
      id: 'tx-paytech-sec-03',
      annonceId: 'ann-pt-03',
      annonceTitre: 'Studio Liberté 6 Sécurité',
      modeTransaction: 'LOCATION',
      annoncePrix: 180000,
      locataireId: 'loc-pt-03',
      locataireNomComplet: 'Ndeye Sene',
      proprietaireId: 'prop-pt-03',
      proprietaireNomComplet: 'Pape Gueye',
      montantCaution: 180000,
      montantPremierLoyer: 180000,
      fraisDossier: 5000,
      montantTotalSequestre: 365000,
      montantVerseProprietaire: 360000,
      statutSequestre: 'INITIALISE',
      statutBail: 'NON_SIGNE',
      dossierActif: true,
      prestatairePaiement: 'PAYTECH',
      moyenPaiement: 'Wave',
      modeSimulationPaiement: false,
      zeroDebitGaranti: false,
      dateCreation: '2026-11-24T12:00:00',
    };

    await page.route('**/api/v1/**', async (route) => {
      const url = route.request().url();
      const method = route.request().method();
      if (url.includes('/transactions/mes-transactions')) {
        return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([baseTx]) });
      }
      if (url.includes('/transactions/tx-paytech-sec-03/paiement-caution') && method === 'POST') {
        attempt += 1;
        if (attempt === 1) {
          // Erreur 503 (verrou zéro débit backend)
          return route.fulfill({
            status: 503,
            contentType: 'application/json',
            body: JSON.stringify({ status: 503, message: 'REFUS SÉCURITÉ ZÉRO DÉBIT : appels PayTech bloqués.' }),
          });
        }
        // URL de redirection malveillante même avec zeroDebitGaranti=true -> bloquée côté frontend (AUDIT-PT-006)
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            ...baseTx,
            statutPaiementExterne: 'EN_ATTENTE_CONFIRMATION',
            zeroDebitGaranti: true,
            redirectUrlPaiement: 'https://evil.example/fake-checkout',
          }),
        });
      }
      if (url.includes('/annonces') || url.includes('/utilisateurs/moi/favoris')) {
        return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0 }) });
      }
      return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });

    await page.addInitScript((auth) => {
      sessionStorage.setItem('ecourtier-auth', JSON.stringify({ state: auth, version: 0 }));
    }, locataireAuth);

    await page.goto(`${BASE_URL}/dashboard`);
    await page.getByRole('button', { name: /Suivi Transactions/i }).click();
    await page.getByRole('button', { name: /Ouvrir le dossier/i }).click();

    const dialog = page.locator('div[role="dialog"]');
    const payBtn = dialog.getByRole('button', { name: /Initier le paiement .* via PayTech \/ Wave/i });

    // 1. Erreur 503 affichée proprement et transaction reste INITIALISE
    await payBtn.click();
    await expect(page.getByText(/REFUS SÉCURITÉ ZÉRO DÉBIT/i)).toBeVisible();
    await expect(dialog).not.toContainText('Paiement confirmé');

    // 2. Domaine redirect_url non fiable rejeté sans redirection navigateur
    await payBtn.click();
    await expect(page.getByText(/domaine PayTech HTTPS requis/i)).toBeVisible();
    expect(page.url()).toContain('/dashboard');
  });

  test('2. ADMIN ne voit aucun bouton de signature (ESCROW-SIGN-001) et un transfert simulé est clairement étiqueté (ESCROW-FIN-001)', async ({ page }) => {
    const adminAuth = {
      utilisateur: {
        id: 'admin-pt-01',
        email: 'admin.paytech@ecourtier.sn',
        prenom: 'Admin',
        nom: 'Audit',
        role: 'ADMIN',
        actif: true,
      },
      accessToken: 'mock-admin-pt-jwt',
      refreshToken: 'mock-admin-pt-refresh',
    };

    const txBloque = {
      id: 'tx-admin-nosign-01',
      annonceId: 'ann-pt-02',
      annonceTitre: 'Studio Point E',
      modeTransaction: 'LOCATION',
      annoncePrix: 200000,
      locataireId: 'loc-pt-02',
      locataireNomComplet: 'Ousmane Ba',
      proprietaireId: 'prop-pt-02',
      proprietaireNomComplet: 'Mariama Faye',
      montantCaution: 200000,
      montantPremierLoyer: 200000,
      fraisDossier: 5000,
      montantTotalSequestre: 405000,
      montantVerseProprietaire: 400000,
      statutSequestre: 'BLOQUE',
      statutBail: 'NON_SIGNE',
      dossierActif: true,
      referenceWave: 'SIM-LOCAL-ABCD1234',
      prestatairePaiement: 'MOCK_LOCAL',
      modeSimulationPaiement: true,
      dateCreation: '2026-11-24T11:00:00',
    };

    const txLibere = {
      ...txBloque,
      id: 'tx-admin-libere-02',
      annonceTitre: 'Villa Sacré-Coeur Libérée',
      statutSequestre: 'LIBERE',
      statutBail: 'SIGNE_COMPLET',
      dateSignatureLocataire: '2026-11-24T11:10:00',
      dateSignatureProprietaire: '2026-11-24T11:15:00',
      statutTransfertProprietaire: 'TRANSFERT_SIMULE',
      referenceTransfertProprietaire: 'SIM-TRANSFER-99887766',
    };

    await page.route('**/api/v1/**', async (route) => {
      const url = route.request().url();
      if (url.includes('/admin/transactions')) {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            content: [txBloque, txLibere],
            totalPages: 1,
            totalElements: 2,
            size: 15,
            number: 0,
          }),
        });
      }
      if (url.includes('/notifications/non-lues/compteur')) {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ count: 0 }),
        });
      }
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: '[]',
      });
    });

    await page.addInitScript((auth) => {
      sessionStorage.setItem('ecourtier-auth', JSON.stringify({ state: auth, version: 0 }));
    }, adminAuth);

    await page.goto(`${BASE_URL}/admin/transactions`);

    // 1. Dossier BLOQUE : ADMIN ne voit AUCUN bouton de signature, mais un avis de sécurité explicite
    await page.locator('tbody button:has-text("Rembourser / Gérer")').click();
    const dialog = page.locator('div[role="dialog"]');
    await expect(dialog).toBeVisible();
    await expect(dialog).toContainText('un administrateur ne peut jamais signer le contrat à la place des parties');
    await expect(dialog).toContainText('SIM-LOCAL-ABCD1234');
    await expect(dialog).toContainText('simulation locale');
    await expect(dialog.getByRole('button', { name: /Signer le bail numérique/i })).toHaveCount(0);

    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();

    // 2. Dossier LIBERE : affiche "Libération logique du dossier effectuée" et "TRANSFERT_SIMULE" (sans virement réel)
    await page.locator('tbody button:has-text("Détails")').click();
    await expect(dialog).toBeVisible();
    await expect(dialog).toContainText('Libération logique du dossier effectuée');
    await expect(dialog).toContainText('TRANSFERT_SIMULE');
    await expect(dialog).toContainText('SIM-TRANSFER-99887766');
    await expect(dialog).toContainText('simulation locale sans virement réel');
  });
});
