import { test, expect } from '@playwright/test';

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:4173';

test.describe('Phase 2 Frontend Security & Integrity UI (Mocked API)', () => {
  test('Signature du bail verrouillée en INITIALISE et débloquée en BLOQUE', async ({ page }) => {
    const locataireAuth = {
      utilisateur: {
        id: 'loc-001',
        email: 'locataire@test.sn',
        prenom: 'Fatou',
        nom: 'Ndiaye',
        role: 'LOCATAIRE',
        actif: true,
      },
      accessToken: 'mock-loc-jwt',
      refreshToken: 'mock-loc-refresh',
    };

    let signCalled = false;

    const txInitialise = {
      id: 'tx-init-1',
      annonceId: 'ann-1',
      annonceTitre: 'Studio Mermoz Phase 2',
      modeTransaction: 'LOCATION',
      annoncePrix: 200000,
      locataireId: 'loc-001',
      locataireNomComplet: 'Fatou Ndiaye',
      proprietaireId: 'prop-001',
      proprietaireNomComplet: 'Amadou Diop',
      montantCaution: 200000,
      montantPremierLoyer: 200000,
      fraisDossier: 5000,
      montantTotalSequestre: 405000,
      montantVerseProprietaire: 400000,
      statutSequestre: 'INITIALISE',
      statutBail: 'NON_SIGNE',
      dossierActif: true,
      dateCreation: '2026-11-14T08:00:00',
    };

    const txBloque = {
      ...txInitialise,
      id: 'tx-bloq-2',
      annonceTitre: 'Appartement Sacre Coeur Phase 2',
      statutSequestre: 'BLOQUE',
      referenceWave: 'WAVE-SANDBOX-OK',
      datePaiement: '2026-11-14T08:10:00',
    };

    await page.route('**/api/v1/**', async (route) => {
      const url = route.request().url();
      const method = route.request().method();

      if (url.includes('/transactions/mes-transactions')) {
        return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([txInitialise, txBloque]) });
      }
      if (url.includes('/transactions/tx-bloq-2/bail/signer') && method === 'POST') {
        signCalled = true;
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            ...txBloque,
            statutBail: 'SIGNE_LOCATAIRE',
            dateSignatureLocataire: '2026-11-14T08:15:00',
          }),
        });
      }
      if (url.includes('/annonces')) {
        return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0 }) });
      }
      if (url.includes('/rendez-vous/mes-rendez-vous')) {
        return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
      }
      if (url.includes('/utilisateurs/moi/favoris')) {
        return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0 }) });
      }
      if (url.includes('/messagerie')) {
        return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
      }
      if (url.includes('/notifications')) {
        return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
      }
      return route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
    });

    await page.addInitScript((auth) => {
      sessionStorage.setItem('ecourtier-auth', JSON.stringify({ state: auth, version: 0 }));
    }, locataireAuth);

    await page.goto(`${BASE_URL}/dashboard`);
    await page.getByRole('button', { name: /Suivi Transactions/i }).click();

    const openButtons = page.getByRole('button', { name: /Ouvrir le dossier/i });
    await expect(openButtons).toHaveCount(2);

    // 1. Dossier INITIALISE : signature impossible dans l'UI
    await openButtons.nth(0).click();
    const dialog = page.locator('div[role="dialog"]');
    await expect(dialog).toBeVisible();
    await expect(dialog).toContainText('après confirmation authentifiée du paiement');
    await expect(dialog.getByRole('button', { name: /Signer le bail numérique/i })).toHaveCount(0);

    // Fermer via touche Escape
    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();

    // 2. Dossier BLOQUE : bouton de signature présent et fonctionnel
    await openButtons.nth(1).click();
    await expect(dialog).toBeVisible();
    const signButton = dialog.getByRole('button', { name: /Signer le bail numérique/i });
    await expect(signButton).toBeVisible();
    await signButton.click();

    await expect.poll(() => signCalled).toBe(true);
  });

  test('EditAnnonceModal transmet version (optimistic lock) et gère un conflit 409 + Déconnexion révoque côté serveur', async ({ page }) => {
    const ownerAuth = {
      utilisateur: {
        id: 'prop-001',
        email: 'proprio@test.sn',
        prenom: 'Amadou',
        nom: 'Diop',
        role: 'PROPRIETAIRE',
        actif: true,
      },
      accessToken: 'mock-prop-jwt',
      refreshToken: 'mock-prop-refresh-token',
    };

    const annonce = {
      id: 'ann-versioned-1',
      titre: 'Appartement Plateau Version 3',
      description: 'Bel appartement spacieux au Plateau avec climatisation complète.',
      type: 'APPARTEMENT',
      modeTransaction: 'LOCATION',
      prix: 350000,
      superficie: 95,
      nombrePieces: 4,
      etage: 2,
      quartier: 'Plateau',
      ville: 'Dakar',
      latitude: 14.6928,
      longitude: -17.4467,
      meuble: true,
      animauxAcceptes: false,
      confirmationAutomatique: true,
      statutVerification: 'VERIFIEE',
      statutPublication: 'DISPONIBLE',
      version: 3,
      equipements: [],
      proximites: [],
      photos: [],
      auteur: {
        id: 'prop-001',
        prenom: 'Amadou',
        nom: 'Diop',
        email: 'proprio@test.sn',
        role: 'PROPRIETAIRE',
        actif: true,
      },
      dateCreation: '2026-11-10T10:00:00',
    };

    let putBody: Record<string, unknown> | null = null;
    let logoutPayload: Record<string, unknown> | null = null;
    let logoutAuthHeader: string | null = null;

    await page.route('**/api/v1/**', async (route) => {
      const url = route.request().url();
      const method = route.request().method();

      if (url.includes('/annonces?mes_annonces=true')) {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ content: [annonce], totalElements: 1, totalPages: 1 }),
        });
      }
      if (url.endsWith('/annonces/ann-versioned-1') && method === 'PUT') {
        putBody = route.request().postDataJSON();
        return route.fulfill({
          status: 409,
          contentType: 'application/json',
          body: JSON.stringify({
            status: 409,
            message: "Conflit de modification : l'annonce a été modifiée simultanément",
          }),
        });
      }
      if (url.includes('/auth/deconnexion') && method === 'POST') {
        logoutPayload = route.request().postDataJSON();
        logoutAuthHeader = route.request().headers()['authorization'] || null;
        return route.fulfill({ status: 204, body: '' });
      }
      if (url.includes('/rendez-vous/mes-rendez-vous') || url.includes('/transactions/mes-transactions') || url.includes('/messagerie') || url.includes('/notifications')) {
        return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
      }
      if (url.includes('/utilisateurs/moi/favoris')) {
        return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0 }) });
      }
      return route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
    });

    await page.addInitScript((auth) => {
      sessionStorage.setItem('ecourtier-auth', JSON.stringify({ state: auth, version: 0 }));
    }, ownerAuth);

    await page.goto(`${BASE_URL}/dashboard`);
    await expect(page.getByText('Appartement Plateau Version 3')).toBeVisible();

    await page.getByRole('button', { name: /^Modifier$/i }).click();
    const dialog = page.locator('div[role="dialog"]');
    await expect(dialog).toBeVisible();

    await dialog.getByRole('button', { name: /Enregistrer les modifications/i }).click();

    await expect.poll(() => putBody).not.toBeNull();
    expect(putBody?.version).toBe(3);
    await expect(page.getByText(/modifiée simultanément|données ont été actualisées/i).first()).toBeVisible();

    // Déconnexion appelle POST /api/v1/auth/deconnexion avec refreshToken et vide sessionStorage
    await page.getByRole('button', { name: /Déconnexion/i }).first().dispatchEvent('click');
    await expect.poll(() => logoutPayload).not.toBeNull();
    expect(logoutPayload?.refreshToken).toBe('mock-prop-refresh-token');
    expect(logoutAuthHeader).toBe('Bearer mock-prop-jwt');
  });

  test('CA3-003: TransactionDetailsModal actualise immédiatement son état après paiement caution puis signature sans fermeture de la modale', async ({ page }) => {
    const locataireAuth = {
      utilisateur: {
        id: 'loc-ca3-003',
        email: 'locataire.ca3003@test.sn',
        prenom: 'Moussa',
        nom: 'Sarr',
        role: 'LOCATAIRE',
        actif: true,
      },
      accessToken: 'mock-loc-ca3003-jwt',
      refreshToken: 'mock-loc-ca3003-refresh',
    };

    const currentTx: Record<string, unknown> = {
      id: 'tx-ca3-003',
      annonceId: 'ann-ca3-003',
      annonceTitre: 'Villa Almadies CA3-003',
      modeTransaction: 'LOCATION',
      annoncePrix: 300000,
      locataireId: 'loc-ca3-003',
      locataireNomComplet: 'Moussa Sarr',
      proprietaireId: 'prop-ca3-003',
      proprietaireNomComplet: 'Ibrahima Fall',
      montantCaution: 300000,
      montantPremierLoyer: 300000,
      fraisDossier: 5000,
      montantTotalSequestre: 605000,
      montantVerseProprietaire: 600000,
      statutSequestre: 'INITIALISE',
      statutBail: 'NON_SIGNE',
      dossierActif: true,
      dateCreation: '2026-11-15T09:00:00',
    };

    await page.route('**/api/v1/**', async (route) => {
      const url = route.request().url();
      const method = route.request().method();

      if (url.includes('/transactions/mes-transactions')) {
        // Simuler même un retard ou une liste initiale : la modale doit déjà se mettre à jour dès la réponse POST
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([currentTx]),
        });
      }
      if (url.includes('/transactions/tx-ca3-003/paiement-caution') && method === 'POST') {
        currentTx.statutSequestre = 'BLOQUE';
        currentTx.referenceWave = 'WAVE-CA3003-OK';
        currentTx.datePaiement = '2026-11-15T09:05:00';
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(currentTx),
        });
      }
      if (url.includes('/transactions/tx-ca3-003/bail/signer') && method === 'POST') {
        currentTx.statutBail = 'SIGNE_LOCATAIRE';
        currentTx.dateSignatureLocataire = '2026-11-15T09:06:00';
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
    await expect(dialog).toContainText('Paiement : En attente');

    // 1. Payer la caution directement dans la modale ouverte
    await dialog.getByRole('button', { name: /Initier le paiement .* via PayTech \/ Wave/i }).click();

    // Sans fermer ni rouvrir la modale, le statut passe à BLOQUE et le bouton de signature apparaît immédiatement
    await expect(dialog).toContainText(/Paiement confirmé/i);
    await expect(dialog).toContainText('WAVE-CA3003-OK');
    const signButton = dialog.getByRole('button', { name: /Signer le bail numérique/i });
    await expect(signButton).toBeVisible();

    // 2. Signer le bail dans la même modale toujours ouverte
    await signButton.click();

    // Sans fermer la modale, l'état passe immédiatement à SIGNE_LOCATAIRE et le bouton disparaît
    await expect(dialog).toContainText(/Bail : Signé par l['e ]locataire/i);
    await expect(dialog).toContainText('Votre signature est déjà enregistrée. En attente de la deuxième partie.');
    await expect(dialog.getByRole('button', { name: /Signer le bail numérique/i })).toHaveCount(0);
  });

  test('CA3-004: AdminVerificationPage permet la suspension (DISPONIBLE -> SUSPENDUE), la réactivation (SUSPENDUE -> DISPONIBLE) et affiche une erreur claire en cas de transition refusée (409)', async ({ page }) => {
    const adminAuth = {
      utilisateur: {
        id: 'admin-001',
        email: 'admin@ecourtier.sn',
        prenom: 'Admin',
        nom: 'Plateforme',
        role: 'ADMIN',
        actif: true,
      },
      accessToken: 'mock-admin-jwt',
      refreshToken: 'mock-admin-refresh',
    };

    const annonceState: Record<string, unknown> = {
      id: 'ann-admin-statut-1',
      titre: 'Résidence Ngor Almadies CA3-004',
      description: 'Appartement haut standing vérifié.',
      type: 'APPARTEMENT',
      modeTransaction: 'LOCATION',
      prix: 450000,
      superficie: 110,
      nombrePieces: 3,
      quartier: 'Ngor',
      ville: 'Dakar',
      latitude: 14.745,
      longitude: -17.514,
      meuble: true,
      animauxAcceptes: false,
      confirmationAutomatique: true,
      statutVerification: 'EN_ATTENTE',
      statutPublication: 'DISPONIBLE',
      version: 2,
      documentsVerification: [],
      photos: [],
      auteur: {
        id: 'prop-009',
        prenom: 'Ousmane',
        nom: 'Sow',
        email: 'ousmane@test.sn',
        role: 'PROPRIETAIRE',
        actif: true,
      },
      dateCreation: '2026-11-15T08:00:00',
    };

    const statusCalls: Array<Record<string, unknown>> = [];

    await page.route('**/api/v1/**', async (route) => {
      const url = route.request().url();
      const method = route.request().method();

      if (url.includes('/admin/annonces/en-attente-verification')) {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ content: [annonceState], totalElements: 1, totalPages: 1 }),
        });
      }
      if (url.endsWith('/admin/annonces/ann-admin-statut-1/statut') && method === 'PUT') {
        const body = route.request().postDataJSON() as Record<string, unknown>;
        statusCalls.push(body);
        if (body.statutPublication === 'LOUEE') {
          return route.fulfill({
            status: 409,
            contentType: 'application/json',
            body: JSON.stringify({
              status: 409,
              message: "Transition refusée : l'administrateur ne peut appliquer que DISPONIBLE ou SUSPENDUE.",
            }),
          });
        }
        annonceState.statutPublication = body.statutPublication;
        annonceState.version = Number(annonceState.version) + 1;
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(annonceState),
        });
      }
      return route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
    });

    await page.addInitScript((auth) => {
      sessionStorage.setItem('ecourtier-auth', JSON.stringify({ state: auth, version: 0 }));
    }, adminAuth);

    await page.goto(`${BASE_URL}/admin/verification`);
    await expect(page.getByText('Résidence Ngor Almadies CA3-004')).toBeVisible();
    await expect(page.getByText('Publication : DISPONIBLE').first()).toBeVisible();

    // 1. Suspension administrative (DISPONIBLE -> SUSPENDUE)
    await page.getByRole('button', { name: /Suspendre l'annonce/i }).click();
    await expect(page.getByText('Publication : SUSPENDUE').first()).toBeVisible();
    expect(statusCalls[0]?.statutPublication).toBe('SUSPENDUE');
    expect(statusCalls[0]?.version).toBe(2);

    // 2. Réactivation administrative (SUSPENDUE -> DISPONIBLE)
    const reactivateBtn = page.getByRole('button', { name: /Réactiver l'annonce/i });
    await expect(reactivateBtn).toBeVisible();
    await reactivateBtn.click();
    await expect(page.getByText('Publication : DISPONIBLE').first()).toBeVisible();
    expect(statusCalls[1]?.statutPublication).toBe('DISPONIBLE');
    expect(statusCalls[1]?.version).toBe(3);

    // 3. Transition invalide (DISPONIBLE -> LOUEE) -> affichage clair de l'erreur 409
    await page.getByLabel(/Statut de publication cible pour Résidence Ngor Almadies CA3-004/i).selectOption('LOUEE');
    await page.getByRole('button', { name: /Appliquer le statut/i }).click();
    await expect(page.getByRole('alert')).toContainText('Transition refusée');
  });
});
