import fs from 'node:fs';
import path from 'node:path';
import { APIRequestContext, Browser, BrowserContext, expect, Page, test } from '@playwright/test';

const API_BASE_URL = process.env.PLAYWRIGHT_API_URL || 'http://127.0.0.1:18080';
const UI_BASE_URL = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:4173';
const ADMIN_EMAIL = process.env.PLAYWRIGHT_ADMIN_EMAIL || 'admin@ecourtier.sn';
const ADMIN_PASSWORD = process.env.PLAYWRIGHT_ADMIN_PASSWORD || 'AdminPass123!';

interface UtilisateurResponse {
  id: string;
  email: string;
  prenom: string;
  nom: string;
  role: 'ADMIN' | 'AGENT' | 'PROPRIETAIRE' | 'LOCATAIRE';
  actif?: boolean;
}

interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  utilisateur: UtilisateurResponse;
}

interface AnnonceResponse {
  id: string;
  titre: string;
  version: number;
  prix: number;
  statutVerification: string;
  statutPublication: string;
}

interface RendezVousResponse {
  id: string;
  statut: string;
}

interface TransactionResponse {
  id: string;
  statutSequestre: string;
  statutBail: string;
  statutDocumentBail?: string;
}

interface ConversationResponse {
  id: string;
  annonceTitre: string;
}

function uniqueSuffix(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

async function registerUser(
  request: APIRequestContext,
  role: 'PROPRIETAIRE' | 'LOCATAIRE',
  prefix: string
): Promise<{ auth: AuthResponse; password: string }> {
  const password = 'Password123!';
  const suffix = uniqueSuffix();
  const res = await request.post(`${API_BASE_URL}/api/v1/auth/inscription`, {
    data: {
      nom: role === 'PROPRIETAIRE' ? 'Diallo' : 'Ndiaye',
      prenom: `${prefix}-${suffix.slice(-4)}`,
      email: `${prefix}.${suffix}@ecourtier-e2e.sn`,
      motDePasse: password,
      telephone: `+22177${String(Math.floor(1000000 + Math.random() * 8999999))}`,
      role,
    },
  });
  expect(res.status(), await res.text()).toBe(201);
  const auth = (await res.json()) as AuthResponse;
  return { auth, password };
}

async function loginAdmin(request: APIRequestContext): Promise<AuthResponse> {
  const res = await request.post(`${API_BASE_URL}/api/v1/auth/connexion`, {
    data: {
      email: ADMIN_EMAIL,
      motDePasse: ADMIN_PASSWORD,
    },
  });
  expect(res.status(), await res.text()).toBe(200);
  return (await res.json()) as AuthResponse;
}

async function createSampleAnnonce(
  request: APIRequestContext,
  token: string,
  titre: string
): Promise<AnnonceResponse> {
  const res = await request.post(`${API_BASE_URL}/api/v1/annonces`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      titre,
      description: 'Appartement lumineux vérifié pour scénario E2E Phase 3 aux Almadies avec parking.',
      type: 'APPARTEMENT',
      modeTransaction: 'LOCATION',
      prix: 450000,
      superficie: 95,
      nombrePieces: 3,
      etage: 2,
      quartier: 'Almadies',
      ville: 'Dakar',
      latitude: 14.7456,
      longitude: -17.5123,
      meuble: true,
      animauxAcceptes: false,
      confirmationAutomatique: true,
    },
  });
  expect(res.status(), await res.text()).toBe(201);
  return (await res.json()) as AnnonceResponse;
}

async function uploadAndVerifyAnnonce(
  request: APIRequestContext,
  adminToken: string,
  ownerToken: string,
  annonceId: string
): Promise<void> {
  const tinyPngBase64 =
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=';
  for (const typeDocument of ['PIECE_IDENTITE', 'TITRE_FONCIER'] as const) {
    const docRes = await request.post(
      `${API_BASE_URL}/api/v1/annonces/${annonceId}/documents?typeDocument=${typeDocument}`,
      {
        headers: { Authorization: `Bearer ${ownerToken}` },
        multipart: {
          file: {
            name: `${typeDocument.toLowerCase()}.png`,
            mimeType: 'image/png',
            buffer: Buffer.from(tinyPngBase64, 'base64'),
          },
        },
      }
    );
    expect(docRes.status(), await docRes.text()).toBe(201);
  }

  const verifyRes = await request.put(`${API_BASE_URL}/api/v1/admin/annonces/${annonceId}/verifier`, {
    headers: { Authorization: `Bearer ${adminToken}` },
    data: { statutVerification: 'VERIFIEE' },
  });
  expect(verifyRes.status(), await verifyRes.text()).toBe(200);
}

async function loginViaUi(page: Page, email: string, password: string): Promise<void> {
  await page.goto(`${UI_BASE_URL}/connexion`);
  await page.getByLabel('Adresse Email').fill(email);
  await page.getByLabel('Mot de passe').fill(password);
  await page.getByRole('button', { name: 'Se connecter' }).click();
}

async function openAuthenticatedContext(
  browser: Browser,
  auth: AuthResponse,
  pathAfterSeed: string
): Promise<{ context: BrowserContext; page: Page }> {
  const context = await browser.newContext({ baseURL: UI_BASE_URL });
  await context.addInitScript((serializedAuth: string) => {
    window.sessionStorage.setItem('ecourtier-auth', serializedAuth);
  }, JSON.stringify({ state: { utilisateur: auth.utilisateur, accessToken: auth.accessToken, refreshToken: auth.refreshToken }, version: 0 }));
  const page = await context.newPage();
  const targetUrl = pathAfterSeed.startsWith('http') ? pathAfterSeed : `${UI_BASE_URL}${pathAfterSeed}`;
  await page.goto(targetUrl);
  return { context, page };
}

test.describe('Phase 3 — Real Backend Full-Stack E2E', () => {
  const createdTransactionIds: string[] = [];
  const createdAnnonceIds: string[] = [];

  test.afterAll(() => {
    const bailDirs = [
      path.resolve(process.cwd(), '../backend/uploads/baux'),
      path.resolve(process.cwd(), '../uploads/baux'),
      path.resolve(process.cwd(), 'uploads/baux'),
    ];
    for (const txId of createdTransactionIds) {
      for (const dir of bailDirs) {
        for (const prefix of ['bail_', 'reservation_achat_']) {
          const target = path.join(dir, `${prefix}${txId}.pdf`);
          if (fs.existsSync(target)) {
            fs.unlinkSync(target);
          }
        }
      }
    }

    const docRoots = [
      path.resolve(process.cwd(), '../backend/uploads/documents'),
      path.resolve(process.cwd(), '../uploads/documents'),
      path.resolve(process.cwd(), 'uploads/documents'),
    ];
    for (const annonceId of createdAnnonceIds) {
      for (const root of docRoots) {
        const dir = path.join(root, annonceId);
        if (fs.existsSync(dir)) {
          fs.rmSync(dir, { recursive: true, force: true });
        }
      }
    }
  });

  test('A1: Déconnexion et changement de mot de passe révoquent immédiatement le JWT existant', async ({
    page,
    request,
  }) => {
    const { auth, password } = await registerUser(request, 'PROPRIETAIRE', 'a1-owner');

    await loginViaUi(page, auth.utilisateur.email, password);
    await expect(page).toHaveURL(/\/dashboard/);
    await expect(page.getByText('Mes Annonces Immobilières')).toBeVisible();

    const storedAfterLogin = await page.evaluate(() => window.sessionStorage.getItem('ecourtier-auth'));
    expect(storedAfterLogin).toBeTruthy();
    const tokenBeforeLogout = (JSON.parse(storedAfterLogin as string) as { state: AuthResponse }).state.accessToken;

    const validRes = await request.get(`${API_BASE_URL}/api/v1/utilisateurs/moi`, {
      headers: { Authorization: `Bearer ${tokenBeforeLogout}` },
    });
    expect(validRes.status()).toBe(200);

    const logoutResPromise = page.waitForResponse(
      (res) => res.url().includes('/api/v1/auth/deconnexion') && res.request().method() === 'POST'
    );
    await page.getByRole('button', { name: 'Déconnexion' }).first().click();
    const logoutRes = await logoutResPromise;
    expect(logoutRes.status()).toBeLessThan(300);

    await page.goto(`${UI_BASE_URL}/dashboard`);
    await expect(page).toHaveURL(/\/connexion/);

    const replayAfterLogout = await request.get(`${API_BASE_URL}/api/v1/utilisateurs/moi`, {
      headers: { Authorization: `Bearer ${tokenBeforeLogout}` },
    });
    expect(replayAfterLogout.status()).toBe(401);

    await loginViaUi(page, auth.utilisateur.email, password);
    await expect(page).toHaveURL(/\/dashboard/);

    const storedAfterRelogin = await page.evaluate(() => window.sessionStorage.getItem('ecourtier-auth'));
    const tokenBeforePwdChange = (JSON.parse(storedAfterRelogin as string) as { state: AuthResponse }).state.accessToken;

    await page.goto(`${UI_BASE_URL}/mon-compte`);
    const newPassword = 'NewPassword456!';
    await page.getByLabel('Mot de passe actuel').fill(password);
    await page.getByLabel('Nouveau mot de passe', { exact: true }).fill(newPassword);
    await page.getByLabel('Confirmer le nouveau mot de passe').fill(newPassword);
    await page.getByRole('button', { name: 'Enregistrer le nouveau mot de passe' }).click();

    await expect(page).toHaveURL(/\/connexion/, { timeout: 10000 });

    const replayAfterPwdChange = await request.get(`${API_BASE_URL}/api/v1/utilisateurs/moi`, {
      headers: { Authorization: `Bearer ${tokenBeforePwdChange}` },
    });
    expect(replayAfterPwdChange.status()).toBe(401);

    await loginViaUi(page, auth.utilisateur.email, newPassword);
    await expect(page).toHaveURL(/\/dashboard/);
    await expect(page.getByText('Mes Annonces Immobilières')).toBeVisible();
  });

  test('A1/IA: Isolation stricte des conversations Samba AI et purge du cache client entre utilisateurs', async ({
    page,
    request,
  }) => {
    const userA = await registerUser(request, 'LOCATAIRE', 'ia-user-a');
    const userB = await registerUser(request, 'LOCATAIRE', 'ia-user-b');
    const uniquePrompt = `Studio meublé Plateau 220000 FCFA ${uniqueSuffix()}`;

    await loginViaUi(page, userA.auth.utilisateur.email, userA.password);
    await expect(page).toHaveURL(/\/recherche/);

    await page.getByRole('button', { name: /Assistant IA E-Courtier/i }).click();
    await page.getByPlaceholder('Posez votre question en français wolofisé...').fill(uniquePrompt);
    const sendPromise = page.waitForResponse(
      (res) => res.url().includes('/api/v1/ia/conversations/') && res.url().endsWith('/messages')
    );
    await page.getByPlaceholder('Posez votre question en français wolofisé...').press('Enter');
    const sendRes = await sendPromise;
    expect(sendRes.status()).toBe(200);
    await expect(page.getByText(uniquePrompt)).toBeVisible();

    const convKeyA = `ecourtier_ia_conversation_${userA.auth.utilisateur.id}`;
    const convIdA = await page.evaluate((key) => window.localStorage.getItem(key), convKeyA);
    expect(convIdA).toBeTruthy();

    await page.getByRole('button', { name: 'Déconnexion' }).first().click();
    await expect(page).toHaveURL(/\/(connexion|$)/);

    const remainingKeyA = await page.evaluate((key) => window.localStorage.getItem(key), convKeyA);
    expect(remainingKeyA).toBeNull();

    await loginViaUi(page, userB.auth.utilisateur.email, userB.password);
    await expect(page).toHaveURL(/\/recherche/);

    await page.getByRole('button', { name: /Assistant IA E-Courtier/i }).click();
    await expect(page.getByText(uniquePrompt)).toHaveCount(0);

    const crossAccessRes = await request.get(`${API_BASE_URL}/api/v1/ia/conversations/${convIdA as string}`, {
      headers: { Authorization: `Bearer ${userB.auth.accessToken}` },
    });
    expect([401, 403, 404]).toContain(crossAccessRes.status());
  });

  test('B1: Verrou optimiste obligatoire (rejet 400 sans version, conflit 409 en édition concurrente)', async ({
    browser,
    request,
  }) => {
    const { auth: owner } = await registerUser(request, 'PROPRIETAIRE', 'b1-owner');
    const titreInitial = `Appartement E2E B1 ${uniqueSuffix()}`;
    const annonce = await createSampleAnnonce(request, owner.accessToken, titreInitial);
    expect(annonce.version).toBe(0);

    // REST check: PUT without version MUST return 400
    const noVersionRes = await request.put(`${API_BASE_URL}/api/v1/annonces/${annonce.id}`, {
      headers: { Authorization: `Bearer ${owner.accessToken}` },
      data: { titre: 'Sans version rejetée' },
    });
    expect(noVersionRes.status()).toBe(400);

    const sessionA = await openAuthenticatedContext(browser, owner, '/dashboard');
    const sessionB = await openAuthenticatedContext(browser, owner, '/dashboard');
    try {
      const cardA = sessionA.page.locator('div', { hasText: titreInitial }).filter({ has: sessionA.page.getByRole('button', { name: 'Modifier' }) }).first();
      const cardB = sessionB.page.locator('div', { hasText: titreInitial }).filter({ has: sessionB.page.getByRole('button', { name: 'Modifier' }) }).first();

      await cardA.getByRole('button', { name: 'Modifier' }).click();
      await cardB.getByRole('button', { name: 'Modifier' }).click();

      await expect(sessionA.page.getByText("Modifier l'annonce")).toBeVisible();
      await expect(sessionB.page.getByText("Modifier l'annonce")).toBeVisible();

      const updatedTitle = `Appartement Concurrent V1 ${uniqueSuffix()}`;
      await sessionA.page.getByLabel("Titre de l'annonce").fill(updatedTitle);
      const saveAPromise = sessionA.page.waitForResponse(
        (res) => res.url().includes(`/api/v1/annonces/${annonce.id}`) && res.request().method() === 'PUT'
      );
      await sessionA.page.getByRole('button', { name: 'Enregistrer les modifications' }).click();
      const saveARes = await saveAPromise;
      expect(saveARes.status()).toBe(200);
      await expect(sessionA.page.getByText(updatedTitle)).toBeVisible();

      await sessionB.page.getByLabel("Titre de l'annonce").fill('Tentative périmée B');
      const saveBPromise = sessionB.page.waitForResponse(
        (res) => res.url().includes(`/api/v1/annonces/${annonce.id}`) && res.request().method() === 'PUT'
      );
      const toastPromise = expect(
        sessionB.page.getByText(/modifiée par un autre utilisateur|Veuillez recharger/i)
      ).toBeVisible({ timeout: 5000 });
      await sessionB.page.getByRole('button', { name: 'Enregistrer les modifications' }).click();
      const saveBRes = await saveBPromise;
      expect(saveBRes.status()).toBe(409);
      await toastPromise;
    } finally {
      await sessionA.context.close();
      await sessionB.context.close();
    }
  });

  test('D/A1: Messagerie STOMP temps réel puis coupure et révocation immédiates lors de désactivation admin', async ({
    browser,
    request,
  }) => {
    const admin = await loginAdmin(request);
    const { auth: owner } = await registerUser(request, 'PROPRIETAIRE', 'ws-owner');
    const { auth: locataire } = await registerUser(request, 'LOCATAIRE', 'ws-locataire');
    const titreAnnonce = `Bien WS E2E ${uniqueSuffix()}`;
    const annonce = await createSampleAnnonce(request, owner.accessToken, titreAnnonce);
    createdAnnonceIds.push(annonce.id);

    await uploadAndVerifyAnnonce(request, admin.accessToken, owner.accessToken, annonce.id);

    const convRes = await request.post(`${API_BASE_URL}/api/v1/annonces/${annonce.id}/conversations`, {
      headers: { Authorization: `Bearer ${locataire.accessToken}` },
    });
    expect([200, 201]).toContain(convRes.status());
    const conv = (await convRes.json()) as ConversationResponse;

    const ownerWin = await openAuthenticatedContext(browser, owner, '/dashboard');
    const locWin = await openAuthenticatedContext(browser, locataire, '/dashboard');

    try {
      await ownerWin.page.getByRole('button', { name: 'Messagerie' }).click();
      await locWin.page.getByRole('button', { name: 'Messagerie' }).click();

      await ownerWin.page.getByRole('button', { name: new RegExp(conv.annonceTitre) }).click();
      await locWin.page.getByRole('button', { name: new RegExp(conv.annonceTitre) }).click();

      await expect(ownerWin.page.getByText('Temps réel actif')).toBeVisible({ timeout: 10000 });
      await expect(locWin.page.getByText('Temps réel actif')).toBeVisible({ timeout: 10000 });

      const messageAvant = `Message temps réel E2E ${uniqueSuffix()}`;
      await ownerWin.page.getByPlaceholder('Écrivez un message...').fill(messageAvant);
      await ownerWin.page.getByRole('button', { name: 'Envoyer' }).click();

      await expect(ownerWin.page.getByText(messageAvant)).toBeVisible({ timeout: 10000 });
      await expect(locWin.page.getByText(messageAvant)).toBeVisible({ timeout: 10000 });

      // Admin deactivates Locataire account -> afterCommit closes WebSocket + rejects future JWT
      const deactivateRes = await request.put(
        `${API_BASE_URL}/api/v1/admin/utilisateurs/${locataire.utilisateur.id}/statut`,
        {
          headers: { Authorization: `Bearer ${admin.accessToken}` },
          data: { actif: false },
        }
      );
      expect(deactivateRes.status(), await deactivateRes.text()).toBe(200);

      await expect(locWin.page.getByText('Temps réel actif')).toHaveCount(0, { timeout: 10000 });

      const messageApres = `Message secret post-désactivation ${uniqueSuffix()}`;
      await ownerWin.page.getByPlaceholder('Écrivez un message...').fill(messageApres);
      await ownerWin.page.getByRole('button', { name: 'Envoyer' }).click();
      await expect(ownerWin.page.getByText(messageApres).last()).toBeVisible({ timeout: 10000 });

      await locWin.page.waitForTimeout(1000);
      await expect(locWin.page.getByText(messageApres)).toHaveCount(0);

      const restReplay = await request.get(`${API_BASE_URL}/api/v1/conversations/mes-conversations`, {
        headers: { Authorization: `Bearer ${locataire.accessToken}` },
      });
      expect([401, 403]).toContain(restReplay.status());
    } finally {
      await ownerWin.context.close();
      await locWin.context.close();
    }
  });

  test('C: Transaction complète, paiement séquestre, double signature bail UI et PDF horodaté protégé', async ({
    browser,
    request,
  }) => {
    const admin = await loginAdmin(request);
    const { auth: owner } = await registerUser(request, 'PROPRIETAIRE', 'bail-owner');
    const { auth: locataire } = await registerUser(request, 'LOCATAIRE', 'bail-locataire');
    const { auth: outsider } = await registerUser(request, 'LOCATAIRE', 'bail-intrus');

    const titreAnnonce = `Villa Bail PDF E2E ${uniqueSuffix()}`;
    const annonce = await createSampleAnnonce(request, owner.accessToken, titreAnnonce);
    createdAnnonceIds.push(annonce.id);

    await uploadAndVerifyAnnonce(request, admin.accessToken, owner.accessToken, annonce.id);

    const futureDate = new Date(Date.now() + 3 * 3600_000).toISOString().slice(0, 19);
    const rdvRes = await request.post(`${API_BASE_URL}/api/v1/annonces/${annonce.id}/rendez-vous`, {
      headers: { Authorization: `Bearer ${locataire.accessToken}` },
      data: { dateHeure: futureDate },
    });
    expect(rdvRes.status(), await rdvRes.text()).toBe(201);
    const rdv = (await rdvRes.json()) as RendezVousResponse;

    const rdvEffectueRes = await request.put(`${API_BASE_URL}/api/v1/rendez-vous/${rdv.id}/marquer-effectue`, {
      headers: { Authorization: `Bearer ${owner.accessToken}` },
    });
    expect(rdvEffectueRes.status(), await rdvEffectueRes.text()).toBe(200);

    const txInitRes = await request.post(`${API_BASE_URL}/api/v1/annonces/${annonce.id}/transactions`, {
      headers: { Authorization: `Bearer ${locataire.accessToken}` },
    });
    expect(txInitRes.status(), await txInitRes.text()).toBe(201);
    const txInit = (await txInitRes.json()) as TransactionResponse;
    createdTransactionIds.push(txInit.id);

    const locWin = await openAuthenticatedContext(browser, locataire, '/dashboard');
    let ownerWin: { context: BrowserContext; page: Page } | null = null;

    try {
      await locWin.page.getByRole('button', { name: 'Suivi Transactions' }).click();

      // CA3-003 : Ouvrir la modale en état INITIALISE, payer la caution dans la modale et signer sans fermer ni rouvrir la modale
      await locWin.page.getByRole('button', { name: 'Ouvrir le dossier' }).first().click();
      const locDialog = locWin.page.locator('div[role="dialog"]');
      await expect(locDialog).toBeVisible();
      await expect(locDialog).toContainText('Séquestre : En attente de dépôt');

      const payLocPromise = locWin.page.waitForResponse(
        (res) => res.url().includes(`/api/v1/transactions/${txInit.id}/paiement-caution`) && res.request().method() === 'POST'
      );
      await locDialog.getByRole('button', { name: /Payer .* via Wave/i }).click();
      const payLocRes = await payLocPromise;
      expect(payLocRes.status()).toBe(200);

      // Le bouton de signature apparaît immédiatement dans la modale toujours ouverte
      const locSignBtn = locDialog.getByRole('button', { name: 'Signer le bail numérique' });
      await expect(locSignBtn).toBeVisible();

      const signLocPromise = locWin.page.waitForResponse(
        (res) => res.url().includes(`/api/v1/transactions/${txInit.id}/bail/signer`) && res.request().method() === 'POST'
      );
      await locSignBtn.click();
      const signLocRes = await signLocPromise;
      expect(signLocRes.status()).toBe(200);
      await expect(locDialog).toContainText('Votre signature est déjà enregistrée. En attente de la deuxième partie.');

      ownerWin = await openAuthenticatedContext(browser, owner, '/dashboard');
      await ownerWin.page.getByRole('button', { name: 'Suivi Transactions' }).click();
      await ownerWin.page.getByRole('button', { name: 'Ouvrir le dossier' }).first().click();
      const signOwnerPromise = ownerWin.page.waitForResponse(
        (res) => res.url().includes(`/api/v1/transactions/${txInit.id}/bail/signer`) && res.request().method() === 'POST'
      );
      await ownerWin.page.getByRole('button', { name: 'Signer le bail numérique' }).click();
      const signOwnerRes = await signOwnerPromise;
      expect(signOwnerRes.status()).toBe(200);

      const finalTx = (await signOwnerRes.json()) as TransactionResponse;
      expect(finalTx.statutBail).toBe('SIGNE_COMPLET');
      expect(finalTx.statutSequestre).toBe('LIBERE');
      expect(finalTx.statutDocumentBail).toBe('GENERE');

      await expect(ownerWin.page.getByText('Document PDF généré et prêt au téléchargement.')).toBeVisible();

      const pdfResponsePromise = ownerWin.page.waitForResponse(
        (res) => res.url().includes(`/api/v1/transactions/${txInit.id}/bail/pdf`) && res.status() === 200
      );
      const downloadPromise = ownerWin.page.waitForEvent('download');
      await ownerWin.page.getByRole('button', { name: 'Télécharger le contrat de bail PDF (Horodaté & Numéroté)' }).click();
      const [pdfResponse, download] = await Promise.all([pdfResponsePromise, downloadPromise]);

      expect(pdfResponse.status()).toBe(200);
      expect(download.suggestedFilename()).toContain('.pdf');
      const downloadedPath = await download.path();
      expect(downloadedPath).toBeTruthy();
      const pdfBytes = fs.readFileSync(downloadedPath as string);
      expect(pdfBytes.subarray(0, 5).toString('utf8')).toBe('%PDF-');

      const ownerPdfRes = await request.get(`${API_BASE_URL}/api/v1/transactions/${txInit.id}/bail/pdf`, {
        headers: { Authorization: `Bearer ${owner.accessToken}` },
      });
      expect(ownerPdfRes.status()).toBe(200);
      expect((await ownerPdfRes.body()).subarray(0, 5).toString('utf8')).toBe('%PDF-');

      const intrusRes = await request.get(`${API_BASE_URL}/api/v1/transactions/${txInit.id}/bail/pdf`, {
        headers: { Authorization: `Bearer ${outsider.accessToken}` },
      });
      expect(intrusRes.status()).toBe(403);
    } finally {
      await locWin.context.close();
      if (ownerWin) {
        await ownerWin.context.close();
      }
    }
  });

  test('CA3-001, CA3-002 & CA3-004: Révocation session sur rejeu Refresh Token, protection anti-DoS /deconnexion et gestion administrative du statut d\'annonce (DISPONIBLE <-> SUSPENDUE)', async ({
    browser,
    request,
  }) => {
    const admin = await loginAdmin(request);
    const { auth: sessionA1, password } = await registerUser(request, 'PROPRIETAIRE', 'ca3-lot1');

    // Connexion sur un 2e appareil indépendant (sessionB)
    const loginBRes = await request.post(`${API_BASE_URL}/api/v1/auth/connexion`, {
      data: { email: sessionA1.utilisateur.email, motDePasse: password },
    });
    expect(loginBRes.status()).toBe(200);
    const sessionB = (await loginBRes.json()) as AuthResponse;

    // Rotation normale sur l'appareil A : R1_A -> R2_A
    const rotARes = await request.post(`${API_BASE_URL}/api/v1/auth/rafraichir`, {
      data: { refreshToken: sessionA1.refreshToken },
    });
    expect(rotARes.status()).toBe(200);
    const sessionA2 = (await rotARes.json()) as AuthResponse;

    // CA3-001 : Rejeu de R1_A consommé -> 401 et révocation immédiate des Access Tokens de la famille A
    const replayRes = await request.post(`${API_BASE_URL}/api/v1/auth/rafraichir`, {
      data: { refreshToken: sessionA1.refreshToken },
    });
    expect(replayRes.status()).toBe(401);

    const checkA1 = await request.get(`${API_BASE_URL}/api/v1/utilisateurs/moi`, {
      headers: { Authorization: `Bearer ${sessionA1.accessToken}` },
    });
    expect(checkA1.status()).toBe(401);

    const checkA2 = await request.get(`${API_BASE_URL}/api/v1/utilisateurs/moi`, {
      headers: { Authorization: `Bearer ${sessionA2.accessToken}` },
    });
    expect(checkA2.status()).toBe(401);

    // CA3-002 : Tentative de déconnexion forcée avec les anciens Refresh Tokens (R1_A consommé, R2_A révoqué) -> 401 sans couper sessionB
    const dos1 = await request.post(`${API_BASE_URL}/api/v1/auth/deconnexion`, {
      data: { refreshToken: sessionA1.refreshToken },
    });
    expect(dos1.status()).toBe(401);

    const dos2 = await request.post(`${API_BASE_URL}/api/v1/auth/deconnexion`, {
      data: { refreshToken: sessionA2.refreshToken },
    });
    expect(dos2.status()).toBe(401);

    // La session B reste pleinement active
    const checkB = await request.get(`${API_BASE_URL}/api/v1/utilisateurs/moi`, {
      headers: { Authorization: `Bearer ${sessionB.accessToken}` },
    });
    expect(checkB.status()).toBe(200);

    // CA3-004 : Créer une annonce avec sessionB et gérer son statut dans l'interface Admin (/admin/verification)
    const titreAnnonce = `Résidence Admin Statut ${uniqueSuffix()}`;
    const annonce = await createSampleAnnonce(request, sessionB.accessToken, titreAnnonce);

    const adminWin = await openAuthenticatedContext(browser, admin, '/admin/verification');
    try {
      const statusSelect = adminWin.page.getByLabel(`Statut de publication cible pour ${titreAnnonce}`);
      await expect(statusSelect).toBeVisible({ timeout: 10000 });
      const card = statusSelect.locator('xpath=ancestor::div[contains(@class, "p-6")][1]');
      await expect(card).toContainText('Publication : DISPONIBLE');

      // 1. Suspendre l'annonce (DISPONIBLE -> SUSPENDUE)
      const suspendPromise = adminWin.page.waitForResponse(
        (res) => res.url().includes(`/api/v1/admin/annonces/${annonce.id}/statut`) && res.request().method() === 'PUT'
      );
      await card.getByRole('button', { name: "Suspendre l'annonce" }).click();
      const suspendRes = await suspendPromise;
      expect(suspendRes.status()).toBe(200);
      await expect(card).toContainText('Publication : SUSPENDUE');

      // 2. Réactiver l'annonce (SUSPENDUE -> DISPONIBLE)
      const reactivatePromise = adminWin.page.waitForResponse(
        (res) => res.url().includes(`/api/v1/admin/annonces/${annonce.id}/statut`) && res.request().method() === 'PUT'
      );
      await card.getByRole('button', { name: "Réactiver l'annonce" }).click();
      const reactivateRes = await reactivatePromise;
      expect(reactivateRes.status()).toBe(200);
      await expect(card).toContainText('Publication : DISPONIBLE');

      // 3. Transition invalide (DISPONIBLE -> LOUEE) -> 409 et message d'erreur explicite dans l'UI
      await statusSelect.selectOption('LOUEE');
      const invalidPromise = adminWin.page.waitForResponse(
        (res) => res.url().includes(`/api/v1/admin/annonces/${annonce.id}/statut`) && res.request().method() === 'PUT'
      );
      await card.getByRole('button', { name: 'Appliquer le statut' }).click();
      const invalidRes = await invalidPromise;
      expect(invalidRes.status()).toBe(409);
      await expect(card.getByRole('alert')).toBeVisible();
    } finally {
      await adminWin.context.close();
    }
  });
});
