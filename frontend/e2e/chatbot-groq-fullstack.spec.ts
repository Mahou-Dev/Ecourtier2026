import { test, expect, type Browser, type Route } from '@playwright/test';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const DIST_DIR = path.resolve(__dirname, '../dist');

const BACKEND_HOST = '127.0.0.1';
const BACKEND_PORT = 8089;
const FRONTEND_PORT = 5173;
const BASE_URL = `http://localhost:${FRONTEND_PORT}`;

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

let proxyServer: http.Server;

const RUN_SUFFIX = Date.now();
const USER_A = {
  nom: 'Diallo',
  prenom: 'Alpha',
  email: `fullstack-alpha-${RUN_SUFFIX}@ecourtier.sn`,
  motDePasse: 'Fullstack123!',
  telephone: '771000001',
  role: 'LOCATAIRE',
};
const USER_B = {
  nom: 'Ndiaye',
  prenom: 'Beta',
  email: `fullstack-beta-${RUN_SUFFIX}@ecourtier.sn`,
  motDePasse: 'Fullstack123!',
  telephone: '771000002',
  role: 'LOCATAIRE',
};

function createDeferred() {
  let resolve!: () => void;
  const promise = new Promise<void>((res) => {
    resolve = res;
  });
  return { promise, resolve };
}

async function waitForBackendReady(timeoutMs = 90_000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    try {
      const res = await fetch(`http://${BACKEND_HOST}:${BACKEND_PORT}/api/v1/annonces?page=0&size=1`);
      if (res.ok) return;
    } catch {
      // backend still booting
    }
    await new Promise((r) => setTimeout(r, 1000));
  }
  throw new Error(`Backend did not become ready on ${BACKEND_HOST}:${BACKEND_PORT} within ${timeoutMs}ms`);
}

async function registerUser(user: typeof USER_A) {
  const res = await fetch(`http://${BACKEND_HOST}:${BACKEND_PORT}/api/v1/auth/inscription`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(user),
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`Failed to register ${user.email}: ${res.status} ${body}`);
  }
  return res.json() as Promise<{ accessToken: string; refreshToken: string; utilisateur: { id: string; email: string } }>;
}

test.describe('Chatbot Groq — Validation Full-Stack Réelle (Frontend + Spring Boot + PostgreSQL 16)', () => {
  test.beforeAll(async () => {
    proxyServer = http.createServer((req, res) => {
      const reqUrl = req.url || '/';
      if (reqUrl.startsWith('/api/')) {
        const proxyReq = http.request(
          {
            hostname: BACKEND_HOST,
            port: BACKEND_PORT,
            path: reqUrl,
            method: req.method,
            headers: {
              ...req.headers,
              host: `${BACKEND_HOST}:${BACKEND_PORT}`,
            },
          },
          (proxyRes) => {
            res.writeHead(proxyRes.statusCode || 502, proxyRes.headers);
            proxyRes.pipe(res, { end: true });
          }
        );
        proxyReq.on('error', (err) => {
          res.writeHead(502, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ message: `Proxy error: ${err.message}` }));
        });
        req.pipe(proxyReq, { end: true });
        return;
      }

      const reqPath = reqUrl.split('?')[0];
      let filePath = path.join(DIST_DIR, reqPath === '/' ? 'index.html' : reqPath);
      if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
        filePath = path.join(DIST_DIR, 'index.html');
      }
      const ext = path.extname(filePath);
      res.writeHead(200, { 'Content-Type': MIME_TYPES[ext] || 'application/octet-stream' });
      fs.createReadStream(filePath).pipe(res);
    });

    await new Promise<void>((resolve) => {
      proxyServer.listen(FRONTEND_PORT, '127.0.0.1', () => resolve());
    });

    await waitForBackendReady();
    await registerUser(USER_A);
    await registerUser(USER_B);
  });

  test.afterAll(async () => {
    if (proxyServer) {
      await new Promise<void>((resolve) => proxyServer.close(() => resolve()));
    }
  });

  test('Scénarios A & B (100% réel, zéro mock): Session anonyme HttpOnly + F5 + protection possession 403 sans cookie', async ({ browser }: { browser: Browser }) => {
    const context1 = await browser.newContext();
    const page1 = await context1.newPage();

    await page1.goto(`${BASE_URL}/recherche`, { waitUntil: 'domcontentloaded' });
    await page1.getByRole('button', { name: /Assistant IA E-Courtier/i }).click();

    const input1 = page1.locator('input[placeholder*="français wolofisé"]');
    await input1.fill('Je cherche une chambre meublée à Ziguinchor budget 80000 FCFA');
    const msgResPromise1 = page1.waitForResponse(
      (res) => res.url().includes('/api/v1/ia/conversations/') && res.url().endsWith('/messages') && res.request().method() === 'POST'
    );
    await page1.locator('button[type="submit"]:has(svg.lucide-send)').click();
    const msgRes1 = await msgResPromise1;
    expect(msgRes1.status()).toBe(200);

    // Réponse réelle du backend + annonce réelle PostgreSQL
    await expect(page1.getByText('Chambre meublée Néma Ziguinchor').last()).toBeVisible({ timeout: 15_000 });

    const convId = await page1.evaluate(() => localStorage.getItem('ecourtier_ia_conversation_anon'));
    expect(convId).toBeTruthy();

    // Vérifier le cookie HttpOnly ecourtier_ia_session et son absence du localStorage
    const cookies = await context1.cookies(`${BASE_URL}/api/v1/ia`);
    const iaCookie = cookies.find((c) => c.name === 'ecourtier_ia_session');
    expect(iaCookie).toBeDefined();
    expect(iaCookie?.httpOnly).toBe(true);

    const hasCookieTokenInStorage = await page1.evaluate(() =>
      Object.keys(localStorage).some((k) => k.includes('ecourtier_ia_session'))
    );
    expect(hasCookieTokenInStorage).toBe(false);

    // Scénario A: F5 restauration autorisée grâce au cookie HttpOnly
    await page1.reload({ waitUntil: 'domcontentloaded' });
    await page1.getByRole('button', { name: /Assistant IA E-Courtier/i }).click();
    await expect(page1.getByText('Je cherche une chambre meublée à Ziguinchor budget 80000 FCFA')).toBeVisible();

    // Scénario B: Un autre navigateur sans le cookie HttpOnly reçoit 403 Forbidden
    const context2 = await browser.newContext();
    const apiForbiddenRes = await context2.request.get(`${BASE_URL}/api/v1/ia/conversations/${convId}`);
    expect(apiForbiddenRes.status()).toBe(403);

    // Même si l'UUID est injecté dans le localStorage d'un autre navigateur, le widget purge et n'affiche rien
    const page2 = await context2.newPage();
    await page2.addInitScript((id) => {
      localStorage.setItem('ecourtier_ia_conversation_anon', id);
    }, convId!);

    const getStatusPromise = page2.waitForResponse((res) =>
      res.url().includes(`/api/v1/ia/conversations/${convId}`)
    );
    await page2.goto(`${BASE_URL}/recherche`, { waitUntil: 'domcontentloaded' });
    const getRes = await getStatusPromise;
    expect(getRes.status()).toBe(403);

    await page2.getByRole('button', { name: /Assistant IA E-Courtier/i }).click();
    await expect(page2.getByText('Na nga def ! Je suis Samba')).toBeVisible();
    await expect(page2.getByText('Je cherche une chambre meublée à Ziguinchor budget 80000 FCFA')).not.toBeVisible();

    const purgedAnonId = await page2.evaluate(() => localStorage.getItem('ecourtier_ia_conversation_anon'));
    expect(purgedAnonId).toBeNull();

    // Le contexte 1 légitime peut toujours lire sa conversation
    const legitRes = await context1.request.get(`${BASE_URL}/api/v1/ia/conversations/${convId}`);
    expect(legitRes.status()).toBe(200);

    await context1.close();
    await context2.close();
  });

  test('Scénarios C & E (100% réel, zéro mock): Déconnexion réelle + isolation stricte entre User A et User B', async ({ browser }: { browser: Browser }) => {
    const contextA = await browser.newContext();
    const pageA = await contextA.newPage();

    // Connexion réelle de User A via l'UI
    await pageA.goto(`${BASE_URL}/connexion`, { waitUntil: 'domcontentloaded' });
    await pageA.locator('input[type="email"]').fill(USER_A.email);
    await pageA.locator('input[type="password"]').fill(USER_A.motDePasse);
    await pageA.getByRole('button', { name: /Se connecter/i }).click();
    await expect(pageA).toHaveURL(/\/recherche/);

    await pageA.getByRole('button', { name: /Assistant IA E-Courtier/i }).click();
    await pageA.locator('input[placeholder*="français wolofisé"]').fill('Message privé réel de Alpha');
    await pageA.locator('button[type="submit"]:has(svg.lucide-send)').click();
    await expect(pageA.getByText('Message privé réel de Alpha')).toBeVisible({ timeout: 15_000 });

    const convInfo = await pageA.evaluate(() => {
      const keys = Object.keys(localStorage).filter((k) => k.startsWith('ecourtier_ia_conversation_'));
      return keys.map((k) => ({ key: k, id: localStorage.getItem(k) }));
    });
    expect(convInfo.length).toBe(1);
    const convIdA = convInfo[0].id!;

    // Scénario E: User B connecté dans un autre contexte ne peut pas lire la conversation de A (403 réel)
    const contextB = await browser.newContext();
    const pageB = await contextB.newPage();
    await pageB.goto(`${BASE_URL}/connexion`, { waitUntil: 'domcontentloaded' });
    await pageB.locator('input[type="email"]').fill(USER_B.email);
    await pageB.locator('input[type="password"]').fill(USER_B.motDePasse);
    await pageB.getByRole('button', { name: /Se connecter/i }).click();
    await expect(pageB).toHaveURL(/\/recherche/);

    const tokenB = await pageB.evaluate(() => {
      const raw = sessionStorage.getItem('ecourtier-auth');
      return raw ? JSON.parse(raw).state.accessToken : null;
    });
    expect(tokenB).toBeTruthy();

    const forbiddenBRes = await contextB.request.get(`${BASE_URL}/api/v1/ia/conversations/${convIdA}`, {
      headers: { Authorization: `Bearer ${tokenB}` },
    });
    expect(forbiddenBRes.status()).toBe(403);

    // Scénario C: Déconnexion réelle de User A via le bouton du Header
    await pageA.locator('button[title="Se déconnecter"]').click();
    const remainingKeys = await pageA.evaluate(() =>
      Object.keys(localStorage).filter((k) => k.startsWith('ecourtier_ia_conversation_'))
    );
    expect(remainingKeys).toHaveLength(0);
    await expect(pageA.getByText('Message privé réel de Alpha')).not.toBeVisible();
    await expect(pageA.getByText('Na nga def ! Je suis Samba')).toBeVisible();

    await contextA.close();
    await contextB.close();
  });

  test('Scénario D (backend réel + retardement contrôlé via route.fetch): Changement rapide A -> B pendant restauration', async ({ page }) => {
    // 1. Connexion réelle A et création d'une conversation réelle en base
    await page.goto(`${BASE_URL}/connexion`, { waitUntil: 'domcontentloaded' });
    await page.locator('input[type="email"]').fill(USER_A.email);
    await page.locator('input[type="password"]').fill(USER_A.motDePasse);
    await page.getByRole('button', { name: /Se connecter/i }).click();
    await expect(page).toHaveURL(/\/recherche/);

    await page.getByRole('button', { name: /Assistant IA E-Courtier/i }).click();
    await page.locator('input[placeholder*="français wolofisé"]').fill('Message sensible A pour test course fullstack');
    const msgResPromiseD = page.waitForResponse(
      (res) => res.url().includes('/api/v1/ia/conversations/') && res.url().endsWith('/messages') && res.request().method() === 'POST'
    );
    await page.locator('button[type="submit"]:has(svg.lucide-send)').click();
    const msgResD = await msgResPromiseD;
    expect(msgResD.status()).toBe(200);
    await expect(page.getByText('Message sensible A pour test course fullstack')).toBeVisible({ timeout: 15_000 });

    const convIdA = await page.evaluate(() => {
      const key = Object.keys(localStorage).find((k) => k.startsWith('ecourtier_ia_conversation_') && !k.endsWith('_anon'));
      return key ? localStorage.getItem(key) : null;
    });
    expect(convIdA).toBeTruthy();

    // 2. Intercepter GET /api/v1/ia/conversations/{convIdA} en utilisant route.fetch() (réponse réelle du backend!)
    //    et bloquer la livraison au navigateur jusqu'au relâchement manuel
    const gate = createDeferred();
    let interceptedRealResponse = false;

    await page.route(`**/api/v1/ia/conversations/${convIdA}`, async (route: Route) => {
      const response = await route.fetch();
      interceptedRealResponse = true;
      await gate.promise;
      await route.fulfill({ response });
    });

    // Recharger /connexion avec A authentifié pour déclencher la restauration réelle de convIdA
    await page.goto(`${BASE_URL}/connexion`, { waitUntil: 'domcontentloaded' });
    await expect.poll(() => interceptedRealResponse).toBe(true);

    // Connecter B pendant que la réponse réelle de A est retenue
    await page.locator('input[type="email"]').fill(USER_B.email);
    await page.locator('input[type="password"]').fill(USER_B.motDePasse);
    await page.getByRole('button', { name: /Se connecter/i }).click();
    await expect(page).toHaveURL(/\/recherche/);

    // Relâcher maintenant la vraie réponse HTTP 200 de A
    gate.resolve();
    await page.waitForTimeout(300);

    await page.getByRole('button', { name: /Assistant IA E-Courtier/i }).click();
    await expect(page.getByText('Na nga def ! Je suis Samba')).toBeVisible();
    await expect(page.getByText('Message sensible A pour test course fullstack')).not.toBeVisible();
  });

  test('Scénario F (réel + sous-cas erreur backend contrôlée): Multi-tours, nouvelle conversation, F5 auth & résilience 500', async ({ page }) => {
    // 1. Flux réel connecté + F5 + Nouvelle conversation
    await page.goto(`${BASE_URL}/connexion`, { waitUntil: 'domcontentloaded' });
    await page.locator('input[type="email"]').fill(USER_B.email);
    await page.locator('input[type="password"]').fill(USER_B.motDePasse);
    await page.getByRole('button', { name: /Se connecter/i }).click();
    await expect(page).toHaveURL(/\/recherche/);

    await page.getByRole('button', { name: /Assistant IA E-Courtier/i }).click();
    const input = page.locator('input[placeholder*="français wolofisé"]');
    await input.fill('Bonjour Samba, je cherche une chambre à Ziguinchor');
    const msgResPromiseF = page.waitForResponse(
      (res) => res.url().includes('/api/v1/ia/conversations/') && res.url().endsWith('/messages') && res.request().method() === 'POST'
    );
    await page.locator('button[type="submit"]:has(svg.lucide-send)').click();
    const msgResF = await msgResPromiseF;
    expect(msgResF.status()).toBe(200);
    await expect(page.getByText('Chambre meublée Néma Ziguinchor').last()).toBeVisible({ timeout: 15_000 });

    // Rechargement F5 authentifié
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.getByRole('button', { name: /Assistant IA E-Courtier/i }).click();
    await expect(page.getByText('Bonjour Samba, je cherche une chambre à Ziguinchor')).toHaveCount(1);

    // Nouvelle conversation
    await page.locator('button[aria-label="Nouvelle conversation"]').click();
    await expect(page.getByText('Bonjour Samba, je cherche une chambre à Ziguinchor')).not.toBeVisible();
    await expect(page.getByText('Na nga def ! Je suis Samba')).toBeVisible();

    // 2. Sous-cas contrôlé: erreur backend 500 sur POST .../messages
    await page.route('**/api/v1/ia/conversations/*/messages', async (route: Route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Erreur interne simulée' }),
      });
    });

    await input.fill('Test gestion erreur serveur');
    await page.locator('button[type="submit"]:has(svg.lucide-send)').click();
    await expect(page.getByText(/désolé|erreur|réessayer/i).first()).toBeVisible({ timeout: 10_000 });
  });
});
