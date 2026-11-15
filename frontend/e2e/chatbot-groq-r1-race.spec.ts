import { test, expect, type Page, type Route } from '@playwright/test';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const DIST_DIR = path.resolve(__dirname, '../dist');

const MIME_TYPES: Record<string, string> = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.woff2': 'font/woff2',
};

let server: http.Server;
let baseUrl: string;

const USER_A = {
  id: 'user-r1-alpha',
  email: 'alpha@ecourtier.sn',
  nom: 'Diallo',
  prenom: 'Alpha',
  telephone: '771112233',
  role: 'LOCATAIRE',
  verifie: true,
};

const USER_B = {
  id: 'user-r1-beta',
  email: 'beta@ecourtier.sn',
  nom: 'Ndiaye',
  prenom: 'Beta',
  telephone: '774445566',
  role: 'LOCATAIRE',
  verifie: true,
};

function seedAuthSession(
  page: Page,
  user: typeof USER_A,
  convId?: string
) {
  return page.addInitScript(
    ({ authUser, savedConvId }) => {
      sessionStorage.setItem(
        'ecourtier-auth',
        JSON.stringify({
          state: {
            utilisateur: authUser,
            accessToken: `jwt-${authUser.id}`,
            refreshToken: `refresh-${authUser.id}`,
          },
          version: 0,
        })
      );
      if (savedConvId) {
        localStorage.setItem(
          `ecourtier_ia_conversation_${authUser.id}`,
          savedConvId
        );
      }
    },
    { authUser: user, savedConvId: convId }
  );
}

function createDeferred() {
  let resolve!: () => void;
  const promise = new Promise<void>((res) => {
    resolve = res;
  });
  return { promise, resolve };
}

test.describe('Chatbot Groq R1 — Courses asynchrones & isolation frontend (mocked network timing)', () => {
  test.beforeAll(async () => {
    server = http.createServer((req, res) => {
      const reqPath = (req.url || '/').split('?')[0];
      let filePath = path.join(DIST_DIR, reqPath === '/' ? 'index.html' : reqPath);
      if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
        filePath = path.join(DIST_DIR, 'index.html');
      }
      const ext = path.extname(filePath);
      res.writeHead(200, { 'Content-Type': MIME_TYPES[ext] || 'application/octet-stream' });
      fs.createReadStream(filePath).pipe(res);
    });

    await new Promise<void>((resolve) => {
      server.listen(0, '127.0.0.1', () => {
        const addr = server.address();
        if (addr && typeof addr === 'object') {
          baseUrl = `http://127.0.0.1:${addr.port}`;
        }
        resolve();
      });
    });
  });

  test.afterAll(async () => {
    await new Promise<void>((resolve) => server.close(() => resolve()));
  });

  test('R1-1: Restauration A en vol + déconnexion réelle -> réponse tardive A ignorée', async ({ page }) => {
    const convA = 'conv-r1-a-logout';
    const gateA = createDeferred();
    let getACalled = false;

    await page.route('**/api/v1/**', async (route: Route) => {
      const url = route.request().url();
      if (url.includes(`/api/v1/ia/conversations/${convA}`)) {
        getACalled = true;
        await gateA.promise;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            conversationId: convA,
            dateCreation: '2026-11-12T10:00:00Z',
            messages: [
              { role: 'USER', contenu: 'Secret de Alpha avant logout', dateEnvoi: '2026-11-12T10:00:01Z' },
              { role: 'ASSISTANT', contenu: 'Réponse privée de Alpha', dateEnvoi: '2026-11-12T10:00:02Z' },
            ],
          }),
        });
        return;
      }
      if (url.includes('/api/v1/auth/deconnexion')) {
        await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
        return;
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{"content":[]}' });
    });

    await seedAuthSession(page, USER_A, convA);
    await page.goto(`${baseUrl}/recherche`, { waitUntil: 'domcontentloaded' });

    await expect.poll(() => getACalled).toBe(true);

    // Déconnexion réelle via le bouton du Header pendant que la restauration de A est en vol
    await page.locator('button[title="Se déconnecter"]').click();

    // Relâcher la réponse tardive de A
    gateA.resolve();
    await page.waitForTimeout(300);

    // Ouvrir le widget et vérifier qu'aucun message de A n'est présent
    await page.getByRole('button', { name: /Assistant IA E-Courtier/i }).click();
    await expect(page.getByText('Na nga def ! Je suis Samba')).toBeVisible();
    await expect(page.getByText('Secret de Alpha avant logout')).not.toBeVisible();
    await expect(page.getByText('Réponse privée de Alpha')).not.toBeVisible();
  });

  test('R1-2: Restauration A en vol + connexion B via /connexion -> réponse tardive A ignorée', async ({ page }) => {
    const convA = 'conv-r1-a-switch';
    const convB = 'conv-r1-b-switch';
    const gateA = createDeferred();
    let getACalled = false;

    await page.route('**/api/v1/**', async (route: Route) => {
      const url = route.request().url();
      const method = route.request().method();

      if (url.includes(`/api/v1/ia/conversations/${convA}`)) {
        getACalled = true;
        await gateA.promise;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            conversationId: convA,
            dateCreation: '2026-11-12T10:00:00Z',
            messages: [
              { role: 'USER', contenu: 'Historique confidentiel Alpha', dateEnvoi: '2026-11-12T10:00:01Z' },
            ],
          }),
        });
        return;
      }
      if (url.includes(`/api/v1/ia/conversations/${convB}`)) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            conversationId: convB,
            dateCreation: '2026-11-12T10:05:00Z',
            messages: [
              { role: 'USER', contenu: 'Message légitime Beta', dateEnvoi: '2026-11-12T10:05:01Z' },
            ],
          }),
        });
        return;
      }
      if (url.includes('/api/v1/auth/connexion') && method === 'POST') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            accessToken: `jwt-${USER_B.id}`,
            refreshToken: `refresh-${USER_B.id}`,
            utilisateur: USER_B,
          }),
        });
        return;
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{"content":[]}' });
    });

    await seedAuthSession(page, USER_A, convA);
    await page.goto(`${baseUrl}/connexion`, { waitUntil: 'domcontentloaded' });
    await expect.poll(() => getACalled).toBe(true);

    // Connecter B pendant que la restauration de A est en vol
    await page.locator('input[type="email"]').fill(USER_B.email);
    await page.locator('input[type="password"]').fill('Secret123!');
    await page.getByRole('button', { name: /Se connecter/i }).click();

    await expect(page).toHaveURL(/\/recherche/);

    // Relâcher la réponse tardive de A
    gateA.resolve();
    await page.waitForTimeout(300);

    await page.getByRole('button', { name: /Assistant IA E-Courtier/i }).click();
    await expect(page.getByText('Historique confidentiel Alpha')).not.toBeVisible();
    await expect(page.getByText('Na nga def ! Je suis Samba')).toBeVisible();
  });

  test('R1-3: Transition authentifié -> anonyme pendant restauration en vol -> zéro fuite', async ({ page }) => {
    const convA = 'conv-r1-auth-to-anon';
    const gateA = createDeferred();
    let getACalled = false;

    await page.route('**/api/v1/**', async (route: Route) => {
      const url = route.request().url();
      if (url.includes(`/api/v1/ia/conversations/${convA}`)) {
        getACalled = true;
        await gateA.promise;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            conversationId: convA,
            dateCreation: '2026-11-12T10:00:00Z',
            messages: [
              { role: 'USER', contenu: 'Donnée authentifiée à ne pas fuiter', dateEnvoi: '2026-11-12T10:00:01Z' },
            ],
          }),
        });
        return;
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{"content":[]}' });
    });

    await seedAuthSession(page, USER_A, convA);
    await page.goto(`${baseUrl}/recherche`, { waitUntil: 'domcontentloaded' });
    await expect.poll(() => getACalled).toBe(true);

    // Déconnexion vers session anonyme
    await page.locator('button[title="Se déconnecter"]').click();

    // Relâcher l'ancienne requête authentifiée
    gateA.resolve();
    await page.waitForTimeout(300);

    await page.getByRole('button', { name: /Assistant IA E-Courtier/i }).click();
    await expect(page.getByText('Donnée authentifiée à ne pas fuiter')).not.toBeVisible();
    const anonKey = await page.evaluate(() => localStorage.getItem('ecourtier_ia_conversation_anon'));
    expect(anonKey).toBeNull();
  });

  test('R1-4: Deux restaurations rapides successives (réponse lente 1 après réponse 2) -> seule la session active gagne', async ({ page }) => {
    const conv1 = 'conv-r1-slow-1';
    const conv2 = 'conv-r1-fast-2';
    const gate1 = createDeferred();
    let get1Called = false;

    await page.route('**/api/v1/**', async (route: Route) => {
      const url = route.request().url();
      const method = route.request().method();

      if (url.includes(`/api/v1/ia/conversations/${conv1}`)) {
        get1Called = true;
        await gate1.promise;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            conversationId: conv1,
            dateCreation: '2026-11-12T10:00:00Z',
            messages: [
              { role: 'USER', contenu: 'Ancien message lent session 1', dateEnvoi: '2026-11-12T10:00:01Z' },
            ],
          }),
        });
        return;
      }
      if (url.includes(`/api/v1/ia/conversations/${conv2}`)) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            conversationId: conv2,
            dateCreation: '2026-11-12T10:10:00Z',
            messages: [
              { role: 'USER', contenu: 'Message actif rapide session 2', dateEnvoi: '2026-11-12T10:10:01Z' },
            ],
          }),
        });
        return;
      }
      if (url.includes('/api/v1/auth/connexion') && method === 'POST') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            accessToken: `jwt-${USER_B.id}`,
            refreshToken: `refresh-${USER_B.id}`,
            utilisateur: USER_B,
          }),
        });
        return;
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{"content":[]}' });
    });

    // Pré-enregistrer aussi la conversation de B dans localStorage avant la navigation
    await page.addInitScript(
      ({ userA, c1, userB, c2 }) => {
        sessionStorage.setItem(
          'ecourtier-auth',
          JSON.stringify({
            state: {
              utilisateur: userA,
              accessToken: `jwt-${userA.id}`,
              refreshToken: `refresh-${userA.id}`,
            },
            version: 0,
          })
        );
        localStorage.setItem(`ecourtier_ia_conversation_${userA.id}`, c1);
        localStorage.setItem(`ecourtier_ia_conversation_${userB.id}`, c2);
      },
      { userA: USER_A, c1: conv1, userB: USER_B, c2: conv2 }
    );

    await page.goto(`${baseUrl}/connexion`, { waitUntil: 'domcontentloaded' });
    await expect.poll(() => get1Called).toBe(true);

    // Basculer sur B (la restauration 2 répond immédiatement)
    await page.locator('input[type="email"]').fill(USER_B.email);
    await page.locator('input[type="password"]').fill('Secret123!');
    await page.getByRole('button', { name: /Se connecter/i }).click();

    await page.getByRole('button', { name: /Assistant IA E-Courtier/i }).click();
    await expect(page.getByText('Message actif rapide session 2')).toBeVisible();

    // Relâcher maintenant la réponse lente de la session 1
    gate1.resolve();
    await page.waitForTimeout(300);

    await expect(page.getByText('Message actif rapide session 2')).toBeVisible();
    await expect(page.getByText('Ancien message lent session 1')).not.toBeVisible();
  });

  test('R1-5: Clic sur "Nouvelle conversation" pendant restauration en vol -> ancienne réponse n’écrase pas la nouvelle conversation', async ({ page }) => {
    const convOld = 'conv-r1-old-before-reset';
    const gateOld = createDeferred();
    let getOldCalled = false;

    await page.route('**/api/v1/**', async (route: Route) => {
      const url = route.request().url();
      if (url.includes(`/api/v1/ia/conversations/${convOld}`)) {
        getOldCalled = true;
        await gateOld.promise;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            conversationId: convOld,
            dateCreation: '2026-11-12T10:00:00Z',
            messages: [
              { role: 'USER', contenu: 'Ancienne discussion à ne pas restaurer après reset', dateEnvoi: '2026-11-12T10:00:01Z' },
            ],
          }),
        });
        return;
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{"content":[]}' });
    });

    await seedAuthSession(page, USER_A, convOld);
    await page.goto(`${baseUrl}/recherche`, { waitUntil: 'domcontentloaded' });
    await expect.poll(() => getOldCalled).toBe(true);

    // Ouvrir le widget et cliquer sur "Nouvelle conversation" pendant que la restauration est en cours
    await page.getByRole('button', { name: /Assistant IA E-Courtier/i }).click();
    await page.locator('button[aria-label="Nouvelle conversation"]').click();

    // Relâcher la vieille réponse
    gateOld.resolve();
    await page.waitForTimeout(300);

    await expect(page.getByText('Na nga def ! Je suis Samba')).toBeVisible();
    await expect(page.getByText('Ancienne discussion à ne pas restaurer après reset')).not.toBeVisible();
  });

  test('R1-6: Restauration normale après F5 sans erreur ni duplication', async ({ page }) => {
    const convNormal = 'conv-r1-normal-f5';

    await page.route('**/api/v1/**', async (route: Route) => {
      const url = route.request().url();
      if (url.includes(`/api/v1/ia/conversations/${convNormal}`)) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            conversationId: convNormal,
            dateCreation: '2026-11-12T10:00:00Z',
            messages: [
              { role: 'USER', contenu: 'Je cherche une chambre à Dakar', dateEnvoi: '2026-11-12T10:00:01Z' },
              { role: 'ASSISTANT', contenu: 'Voici des chambres disponibles à Dakar.', dateEnvoi: '2026-11-12T10:00:02Z' },
            ],
          }),
        });
        return;
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{"content":[]}' });
    });

    await seedAuthSession(page, USER_A, convNormal);
    await page.goto(`${baseUrl}/recherche`, { waitUntil: 'domcontentloaded' });
    await page.reload({ waitUntil: 'domcontentloaded' });

    await page.getByRole('button', { name: /Assistant IA E-Courtier/i }).click();
    await expect(page.getByText('Je cherche une chambre à Dakar')).toHaveCount(1);
    await expect(page.getByText('Voici des chambres disponibles à Dakar.')).toHaveCount(1);
  });
});
