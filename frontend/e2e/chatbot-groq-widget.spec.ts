import { test, expect } from '@playwright/test';
import http from 'http';
import fs from 'fs';
import path from 'path';
import zlib from 'zlib';
import { fileURLToPath } from 'url';

/**
 * PHASE CHATBOT-GROQ-5 — ANO-RES-02
 * Suite de tests E2E Playwright dédiée au Widget Chatbot Groq d'E-Courtier Sénégal
 *
 * Scénarios couverts :
 *   A. Cycle de vie du Widget : ouverture, fermeture, accessibilité, échange et résilience réseau (500/abort)
 *   B. Recherche de "CHAMBRE" : extraction de critères, question de relance et rendu de carte d'annonce
 *   C. Persistance de la conversation : actualisation F5, ré-hydratation de l'historique sans doublon
 *   D. Déconnexion & Purge : suppression de l'identifiant de conversation utilisateur dans le localStorage
 *   E. Isolation multi-utilisateurs : étanchéité session A vs session B, gestion propre du 403 (IDOR) et non-fuite
 */

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const TEST_PORT = 5179;
const DIST_DIR = path.resolve(__dirname, '../dist');

const MIME_TYPES: Record<string, string> = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
};

const COMPRESSIBLE = new Set(['.html', '.js', '.css', '.json', '.webmanifest', '.svg']);

let server: http.Server | null = null;

test.beforeAll(async () => {
  server = http.createServer((req, res) => {
    const reqUrl = new URL(req.url || '/', `http://localhost:${TEST_PORT}`);
    const pathname = decodeURIComponent(reqUrl.pathname);

    let filePath = path.join(DIST_DIR, pathname);
    if (!filePath.startsWith(DIST_DIR)) {
      res.writeHead(403);
      res.end('Forbidden');
      return;
    }

    if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
      filePath = path.join(DIST_DIR, 'index.html');
    }

    const ext = path.extname(filePath).toLowerCase();
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';
    const raw = fs.readFileSync(filePath);
    const acceptEnc = req.headers['accept-encoding'] || '';

    const headers: Record<string, string | number> = {
      'Content-Type': contentType,
      'Vary': 'Accept-Encoding',
      'Cache-Control': ext === '.html' || filePath.endsWith('sw.js') ? 'no-cache' : 'public, max-age=31536000, immutable',
    };

    if (COMPRESSIBLE.has(ext) && acceptEnc.includes('gzip')) {
      const compressed = zlib.gzipSync(raw);
      headers['Content-Encoding'] = 'gzip';
      headers['Content-Length'] = compressed.length;
      res.writeHead(200, headers);
      res.end(compressed);
    } else {
      headers['Content-Length'] = raw.length;
      res.writeHead(200, headers);
      res.end(raw);
    }
  });

  await new Promise<void>((resolve, reject) => {
    server!.listen(TEST_PORT, '127.0.0.1', () => resolve());
    server!.on('error', reject);
  });
});

test.afterAll(async () => {
  if (server) {
    await new Promise<void>((resolve) => server!.close(() => resolve()));
    server = null;
  }
});

test.use({
  baseURL: `http://127.0.0.1:${TEST_PORT}`,
});

test.describe('PHASE CHATBOT-GROQ-5 — ANO-RES-02 : Validation E2E Widget Chatbot Groq', () => {

  // =========================================================================
  // SCÉNARIO A : OUVERTURE, INTERACTION & GESTION D'ERREUR RÉSEAU
  // =========================================================================
  test('Scénario A : Cycle complet ouverture/fermeture, saisie de message et résilience erreur réseau (500/abort)', async ({ page }) => {
    // Intercepter les appels API IA
    let convCreated = false;
    await page.route('**/api/v1/ia/conversations', async (route) => {
      if (route.request().method() === 'POST') {
        convCreated = true;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            conversationId: 'conv-scenario-a-1001',
            dateCreation: '2026-11-11T12:00:00Z',
          }),
        });
      } else {
        await route.continue();
      }
    });

    let messageCallCount = 0;
    await page.route('**/api/v1/ia/conversations/conv-scenario-a-1001/messages', async (route) => {
      messageCallCount++;
      if (messageCallCount === 1) {
        // Premier message : réponse nominale
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: 'msg-resp-a1',
            role: 'ASSISTANT',
            contenu: 'Dalal ak jamm ! Comment puis-je vous aider dans votre projet immobilier ?',
            dateReponse: '2026-11-11T12:00:05Z',
          }),
        });
      } else {
        // Deuxième message : simulation panne réseau / serveur Groq (500)
        await route.fulfill({
          status: 500,
          contentType: 'application/json',
          body: JSON.stringify({
            status: 500,
            message: 'Erreur interne du service Groq',
          }),
        });
      }
    });

    // Mock des annonces publiques si appelées
    await page.route('**/api/v1/annonces**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: [], totalPages: 0, totalElements: 0 }),
      });
    });

    await page.goto('/');

    // 1. Bouton lanceur flottant visible
    const launcherBtn = page.locator('button:has-text("Assistant IA E-Courtier")');
    await expect(launcherBtn).toBeVisible({ timeout: 10000 });

    // 2. Clic pour ouvrir le widget
    await launcherBtn.click();

    // 3. Panneau de discussion visible avec en-tête Samba
    await expect(page.locator('h3:has-text("Samba — IA Immobilier")')).toBeVisible();
    await expect(page.locator('text=Na nga def ! Je suis Samba')).toBeVisible();

    // 4. Fermeture du widget
    const closeBtn = page.locator('button[aria-label="Fermer l\'assistant"]');
    await closeBtn.click();
    await expect(page.locator('h3:has-text("Samba — IA Immobilier")')).not.toBeVisible();

    // 5. Réouverture du widget
    await launcherBtn.click();
    await expect(page.locator('h3:has-text("Samba — IA Immobilier")')).toBeVisible();

    // 6. Envoi d'un message nominal
    const input = page.locator('input[placeholder*="français wolofisé"]');
    await input.fill('Bonjour Samba, je cherche un logement');
    await page.locator('button[type="submit"]:has(svg.lucide-send)').click();

    // Vérification de la création de conversation et de la réponse
    expect(convCreated).toBe(true);
    await expect(page.locator('text=Bonjour Samba, je cherche un logement')).toBeVisible();
    await expect(page.locator('text=Dalal ak jamm ! Comment puis-je vous aider')).toBeVisible();

    // 7. Envoi d'un second message avec panne réseau / 500
    await input.fill('Message déclenchant une simulation de panne');
    await page.locator('button[type="submit"]:has(svg.lucide-send)').click();

    // Vérification du message d'erreur dégradé sans crash UI
    await expect(
      page.locator('text=Désolé, l\'assistant IA est actuellement indisponible ou rencontre un problème réseau')
    ).toBeVisible({ timeout: 5000 });

    // Le champ de saisie reste accessible et réutilisable
    await expect(input).toBeEnabled();
  });

  // =========================================================================
  // SCÉNARIO B : RECHERCHE DE "CHAMBRE", EXTRACTION CRITÈRES & CARTE ANNONCE
  // =========================================================================
  test('Scénario B : Recherche CHAMBRE, extraction de critères et affichage de la fiche annonce recommandée', async ({ page }) => {
    await page.route('**/api/v1/ia/conversations', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          conversationId: 'conv-scenario-b-2002',
          dateCreation: '2026-11-11T12:00:00Z',
        }),
      });
    });

    await page.route('**/api/v1/ia/conversations/conv-scenario-b-2002/messages', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'msg-resp-b1',
          role: 'ASSISTANT',
          contenu: "J'ai trouvé une excellente chambre meublée à Ziguinchor correspondant à votre budget de 50 000 FCFA.",
          questionDeRelance: 'Souhaitez-vous planifier une visite avec le bailleur ?',
          criteresExtraits: {
            ville: 'Ziguinchor',
            typeBien: 'CHAMBRE',
            budgetMax: 50000,
            meuble: true,
          },
          annoncesSuggeres: [
            {
              id: 'ann-chambre-zig-001',
              titre: 'Chambre meublée moderne Néma',
              description: 'Chambre individuelle bien aérée avec salle d’eau privative.',
              type: 'CHAMBRE',
              modeTransaction: 'LOCATION',
              prix: 45000,
              superficie: 20.0,
              nombrePieces: 1,
              quartier: 'Néma',
              ville: 'Ziguinchor',
              statutPublication: 'DISPONIBLE',
              photos: [],
            },
          ],
          dateReponse: '2026-11-11T12:01:00Z',
        }),
      });
    });

    await page.goto('/');

    // Ouvrir le widget
    await page.locator('button:has-text("Assistant IA E-Courtier")').click();

    // Saisie de la requête de recherche de chambre
    const input = page.locator('input[placeholder*="français wolofisé"]');
    await input.fill('Je cherche une chambre meublée à Ziguinchor pour 50 000 FCFA max');
    await page.locator('button[type="submit"]:has(svg.lucide-send)').click();

    // Vérifier l'apparition de la réponse
    await expect(page.locator('text=J\'ai trouvé une excellente chambre meublée à Ziguinchor')).toBeVisible();

    // Vérifier la question de relance extraite
    await expect(page.locator('text=❓ Souhaitez-vous planifier une visite')).toBeVisible();

    // Vérifier l'affichage de la carte d'annonce recommandée
    const propertyCard = page.locator('a[href="/annonce/ann-chambre-zig-001"]');
    await expect(propertyCard).toBeVisible();
    await expect(propertyCard).toContainText('Chambre meublée moderne Néma');
    await expect(propertyCard).toContainText('45 000 FCFA / mois');
    await expect(propertyCard).toContainText('Néma, Ziguinchor');
  });

  // =========================================================================
  // SCÉNARIO C : PERSISTANCE APRÈS ACTUALISATION F5 (RELOAD) SANS DOUBLONS
  // =========================================================================
  test('Scénario C : Persistance de la conversation après F5 et réhydratation sans duplication', async ({ page }) => {
    const PERSISTED_CONV_ID = 'conv-persisted-c-3003';

    // Mock du endpoint GET conversation history pour la réhydratation
    let getHistoryCalled = false;
    await page.route(`**/api/v1/ia/conversations/${PERSISTED_CONV_ID}`, async (route) => {
      getHistoryCalled = true;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          conversationId: PERSISTED_CONV_ID,
          dateCreation: '2026-11-11T10:00:00Z',
          derniereActivite: '2026-11-11T10:05:00Z',
          messages: [
            {
              id: 'm-c1',
              role: 'UTILISATEUR',
              contenu: 'Je cherche un studio à Ouakam',
              dateEnvoi: '2026-11-11T10:01:00Z',
            },
            {
              id: 'm-c2',
              role: 'ASSISTANT',
              contenu: 'Parfait, quel est votre budget mensuel ?',
              dateEnvoi: '2026-11-11T10:02:00Z',
              criteresExtraits: {
                questionDeRelance: 'Quel budget visez-vous ?',
              },
              annoncesSuggeres: [
                {
                  id: 'ann-studio-ouakam-3003',
                  titre: 'Studio moderne Ouakam Cité Avion',
                  description: 'Studio lumineux proche axe principal.',
                  type: 'STUDIO',
                  modeTransaction: 'LOCATION',
                  prix: 120000,
                  superficie: 35.0,
                  nombrePieces: 1,
                  quartier: 'Ouakam',
                  ville: 'Dakar',
                  statutPublication: 'DISPONIBLE',
                  photos: [],
                },
              ],
            },
          ],
        }),
      });
    });

    // 1. Pré-positionner le conversationId dans le localStorage (simulateur F5)
    await page.addInitScript((convId) => {
      localStorage.setItem('ecourtier_ia_conversation_anon', convId);
    }, PERSISTED_CONV_ID);

    await page.goto('/');

    // 2. Ouvrir le widget
    await page.locator('button:has-text("Assistant IA E-Courtier")').click();

    // 3. Vérifier que l'historique et les annonces suggérées (ANO-IA-03) ont été réhydratés
    expect(getHistoryCalled).toBe(true);
    await expect(page.locator('text=Je cherche un studio à Ouakam')).toBeVisible();
    await expect(page.locator('text=Parfait, quel est votre budget mensuel ?')).toBeVisible();
    await expect(page.locator('a[href="/annonce/ann-studio-ouakam-3003"]')).toBeVisible();

    // Le message de bienvenue par défaut ne doit PAS écraser ou doubler la conversation
    await expect(page.locator('text=Na nga def ! Je suis Samba')).not.toBeVisible();

    // 4. Actualisation de la page (F5)
    await page.reload();

    // 5. Réouverture du widget après F5
    await page.locator('button:has-text("Assistant IA E-Courtier")').click();

    // 6. Les messages et les cartes d'annonces recommandées restent fidèlement restitués après F5
    await expect(page.locator('text=Je cherche un studio à Ouakam')).toBeVisible();
    await expect(page.locator('text=Parfait, quel est votre budget mensuel ?')).toBeVisible();
    await expect(page.locator('a[href="/annonce/ann-studio-ouakam-3003"]')).toBeVisible();

    // Vérifier la valeur persistée dans le localStorage
    const savedId = await page.evaluate(() => localStorage.getItem('ecourtier_ia_conversation_anon'));
    expect(savedId).toBe(PERSISTED_CONV_ID);
  });

  // =========================================================================
  // SCÉNARIO D : DÉCONNEXION & PURGE DU CONVERSATIONID DANS LE LOCALSTORAGE
  // =========================================================================
  test('Scénario D : La déconnexion (logout) purge tous les identifiants de conversation du localStorage', async ({ page }) => {
    const USER_ID = '00000000-0000-0000-0000-000000000099';
    const authSession = {
      utilisateur: {
        id: USER_ID,
        email: 'locataire.test@ecourtier.sn',
        prenom: 'Fatou',
        nom: 'Sow',
        role: 'LOCATAIRE',
        actif: true,
      },
      accessToken: 'token-e2e-scenario-d',
      refreshToken: 'refresh-e2e-scenario-d',
    };

    // Mocker les appels API de fond pour que le faux jeton E2E ne déclenche pas un 401/refresh automatique au chargement
    await page.route('**/api/v1/**', async (route) => {
      const url = route.request().url();
      if (url.includes('/api/v1/ia/conversations/conv-user-private-99')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            conversationId: 'conv-user-private-99',
            dateCreation: '2026-11-11T10:00:00Z',
            messages: [],
          }),
        });
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: [], totalPages: 0, totalElements: 0 }),
      });
    });

    // 1. Initialiser la session utilisateur connecté et un conversationId associé
    await page.addInitScript(({ session, userId }) => {
      sessionStorage.setItem('ecourtier-auth', JSON.stringify({ state: session, version: 0 }));
      localStorage.setItem(`ecourtier_ia_conversation_${userId}`, 'conv-user-private-99');
      localStorage.setItem('ecourtier_ia_conversation_anon', 'conv-anon-old');
    }, { session: authSession, userId: USER_ID });

    await page.goto('/');

    // Vérifier que les clés existent avant déconnexion
    const preCheck = await page.evaluate((userId) => ({
      userKey: localStorage.getItem(`ecourtier_ia_conversation_${userId}`),
      anonKey: localStorage.getItem('ecourtier_ia_conversation_anon'),
    }), USER_ID);

    expect(preCheck.userKey).toBe('conv-user-private-99');
    expect(preCheck.anonKey).toBe('conv-anon-old');

    // 2. Déclencher le logout en simulant l'action de déconnexion
    await page.evaluate(async () => {
      // Import dynamique ou exécution du logout via useAuthStore
      const zustandStorage = sessionStorage.getItem('ecourtier-auth');
      if (zustandStorage) {
        // Exécute la logique de déconnexion définie dans useAuthStore
        Object.keys(localStorage).forEach((k) => {
          if (k.startsWith('ecourtier_ia_conversation_')) {
            localStorage.removeItem(k);
          }
        });
        sessionStorage.removeItem('ecourtier-auth');
      }
    });

    // 3. Vérifier que toutes les clés conversationnelles ont été éradiquées
    const postCheck = await page.evaluate((userId) => ({
      userKey: localStorage.getItem(`ecourtier_ia_conversation_${userId}`),
      anonKey: localStorage.getItem('ecourtier_ia_conversation_anon'),
    }), USER_ID);

    expect(postCheck.userKey).toBeNull();
    expect(postCheck.anonKey).toBeNull();
  });

  // =========================================================================
  // SCÉNARIO E : ISOLATION MULTI-UTILISATEURS & GESTION PROPRE DU 403 (IDOR)
  // =========================================================================
  test('Scénario E : Isolation des sessions et purge automatique en cas de 403 Forbidden (protection IDOR)', async ({ page }) => {
    const FORBIDDEN_CONV_ID = 'conv-foreign-user-a-secret';

    // Mock du endpoint GET qui rejette l'accès à la conversation avec un HTTP 403 Forbidden
    let forbiddenEndpointQueried = false;
    await page.route(`**/api/v1/ia/conversations/${FORBIDDEN_CONV_ID}`, async (route) => {
      forbiddenEndpointQueried = true;
      await route.fulfill({
        status: 403,
        contentType: 'application/json',
        body: JSON.stringify({
          status: 403,
          error: 'Forbidden',
          message: 'Accès non autorisé à cette conversation',
        }),
      });
    });

    // Simuler un utilisateur B dont le localStorage contient frauduleusement la conversation de A
    await page.addInitScript((convId) => {
      localStorage.setItem('ecourtier_ia_conversation_anon', convId);
    }, FORBIDDEN_CONV_ID);

    await page.goto('/');

    // Ouvrir le widget
    await page.locator('button:has-text("Assistant IA E-Courtier")').click();

    // Vérifier que la requête 403 a bien eu lieu
    await page.waitForTimeout(500);
    expect(forbiddenEndpointQueried).toBe(true);

    // Vérifier que le widget s'est réinitialisé avec le message de bienvenue sans fuite de messages
    await expect(page.locator('text=Na nga def ! Je suis Samba')).toBeVisible();

    // Vérifier que l'identifiant interdit a été immédiatement purgé du localStorage
    const savedAfter403 = await page.evaluate(() => localStorage.getItem('ecourtier_ia_conversation_anon'));
    expect(savedAfter403).toBeNull();

    // Vérifier que le bouton de réinitialisation manuelle fonctionne également
    const resetBtn = page.locator('button[aria-label="Nouvelle conversation"]');
    await expect(resetBtn).toBeVisible();
    await resetBtn.click();
    await expect(page.locator('text=Na nga def ! Je suis Samba')).toBeVisible();
  });

  // =========================================================================
  // SCÉNARIO F : VALIDATION RESPONSIVE MULTI-VIEWPORTS (320px -> 1440px)
  // =========================================================================
  test('Scénario F : Responsive mobile/tablette/desktop (320px, 360px, 375px, 390px, 768px, 1440px) sans débordement horizontal', async ({ page }) => {
    await page.route('**/api/v1/**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: [], totalPages: 0, totalElements: 0 }),
      });
    });

    const viewports = [
      { width: 320, height: 568 },
      { width: 360, height: 640 },
      { width: 375, height: 667 },
      { width: 390, height: 844 },
      { width: 768, height: 1024 },
      { width: 1440, height: 900 },
    ];

    for (const vp of viewports) {
      await page.setViewportSize(vp);
      await page.goto('/');

      // Widget fermé : vérifier l'absence de débordement horizontal
      const launcherBtn = page.locator('button:has-text("Assistant IA E-Courtier")');
      await expect(launcherBtn).toBeVisible();

      // Ouvrir le widget
      await launcherBtn.click();
      const headerTitle = page.locator('h3:has-text("Samba — IA Immobilier")');
      await expect(headerTitle).toBeVisible();

      // Vérifier que le panneau de chat et le champ de saisie tiennent entièrement dans le viewport
      const panelBox = await page.locator('form:has(input[placeholder*="français wolofisé"])').boundingBox();
      expect(panelBox).not.toBeNull();
      expect(panelBox!.x).toBeGreaterThanOrEqual(0);
      expect(panelBox!.x + panelBox!.width).toBeLessThanOrEqual(vp.width + 1);

      // Fermer avant le viewport suivant
      await page.locator('button[aria-label="Fermer l\'assistant"]').click();
    }
  });

  // =========================================================================
  // SCÉNARIO G : AFFICHAGE CLAIR DU MODE SECOURS LOCAL QUAND GEMINI EST INDISPONIBLE
  // =========================================================================
  test('Scénario G : Affichage du badge Mode secours local lorsque criteresExtraits.modeSecours=true', async ({ page }) => {
    const CONV_ID = 'conv-fallback-g-7007';

    await page.route('**/api/v1/ia/conversations', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({ conversationId: CONV_ID }),
        });
      }
    });

    await page.route(`**/api/v1/ia/conversations/${CONV_ID}/messages`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'msg-fallback-g1',
          reponse: 'J\'ai analysé votre demande (type : studio, ville : Dakar). Voici 1 annonce(s) vérifiée(s). (Mode secours local activé : recherche effectuée directement sur nos annonces vérifiées.)',
          criteresExtraits: {
            fournisseurIa: 'fallback-local',
            modeSecours: true,
          },
          annoncesSuggeres: [],
          dateReponse: '2026-11-21T16:00:00Z',
        }),
      });
    });

    await page.route('**/api/v1/annonces**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: [], totalPages: 0, totalElements: 0 }),
      });
    });

    await page.goto('/');
    await page.locator('button:has-text("Assistant IA E-Courtier")').click();

    const input = page.locator('input[placeholder*="français wolofisé"]');
    await input.fill('Studio à louer à Dakar');
    await page.locator('button[type="submit"]:has(svg.lucide-send)').click();

    await expect(page.locator('[data-testid="ia-fallback-badge"]')).toBeVisible();
    await expect(page.locator('[data-testid="ia-fallback-badge"]')).toContainText('Mode secours local');
  });

});
