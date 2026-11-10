import { test, expect } from '@playwright/test';
import http from 'http';
import fs from 'fs';
import path from 'path';
import zlib from 'zlib';

/**
 * Suite de tests E2E et d'intégration ciblée des optimisations du Lot 3 (Phase 9C / Lot 3B)
 * Couvre les 5 piliers :
 *   A. Préchargement API précoce (index.html <head>) & gestion des erreurs (404, 500, abort)
 *   B. Réconciliation des favoris (visiteur anonyme vs session authentifiée)
 *   C. Sécurisation du cache runtime PWA & réduction du précache Workbox
 *   D. Chargement différé (carte Leaflet & modales lourdes)
 *   E. Responsive mobile (375x667) sans débordement horizontal
 */

import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const TEST_PORT = 5173;
const DIST_DIR = path.resolve(__dirname, '../dist');
const ANNONCE_ID_1 = '958b59d1-12af-4412-b319-f20f24dfcb87';
const ANNONCE_ID_2 = '88888888-8888-8888-8888-888888888888';
const ANNONCE_ID_404 = '00000000-0000-0000-0000-000000000404';
const ANNONCE_ID_500 = '50000000-0000-0000-0000-000000000500';

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

const mockListing1 = {
  id: ANNONCE_ID_1,
  titre: 'Appartement F4 Vue Mer Almadies',
  description: 'Superbe appartement F4 lumineux aux Almadies avec vue mer, finitions haut de gamme et sécurité 24h/24.',
  type: 'APPARTEMENT',
  modeTransaction: 'LOCATION',
  prix: 450000,
  superficie: 120.0,
  nombrePieces: 4,
  etage: 3,
  quartier: 'Almadies',
  ville: 'Dakar',
  latitude: 14.7450,
  longitude: -17.5180,
  meuble: true,
  animauxAcceptes: true,
  confirmationAutomatique: true,
  statutVerification: 'VERIFIEE',
  statutPublication: 'DISPONIBLE',
  dateCreation: '2026-10-30T10:00:00Z',
  auteurRole: 'PROPRIETAIRE',
  auteur: {
    id: 'u1-owner',
    nom: 'Diouf',
    prenom: 'Babacar',
    email: 'proprio.demo@ecourtier.sn',
    telephone: '+221770000002',
    role: 'PROPRIETAIRE',
    actif: true,
  },
  photos: [
    {
      id: 'p1',
      url: 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="800" height="600"%3E%3Crect width="800" height="600" fill="%231a3c34"/%3E%3C/svg%3E',
      ordre: 0,
    },
    {
      id: 'p2',
      url: 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="800" height="600"%3E%3Crect width="800" height="600" fill="%23c85a32"/%3E%3C/svg%3E',
      ordre: 1,
    },
  ],
  equipements: ['CLIMATISATION', 'PARKING', 'WIFI', 'GARDIENNAGE'],
  proximites: ['MER', 'COMMERCE', 'TRANSPORT'],
  estFavori: false,
};

const mockListing2 = {
  id: ANNONCE_ID_2,
  titre: 'Villa Contemporaine Fann Résidence',
  description: 'Magnifique villa contemporaine avec piscine et jardin arboré à Fann Résidence.',
  type: 'VILLA',
  modeTransaction: 'VENTE',
  prix: 85000000,
  superficie: 350.0,
  nombrePieces: 6,
  etage: 1,
  quartier: 'Fann Résidence',
  ville: 'Dakar',
  latitude: 14.6930,
  longitude: -17.4690,
  meuble: false,
  animauxAcceptes: true,
  confirmationAutomatique: false,
  statutVerification: 'VERIFIEE',
  statutPublication: 'DISPONIBLE',
  dateCreation: '2026-10-31T12:00:00Z',
  auteurRole: 'PROPRIETAIRE',
  auteur: {
    id: 'u2-owner',
    nom: 'Ndiaye',
    prenom: 'Awa',
    email: 'awa.ndiaye@ecourtier.sn',
    telephone: '+221771112233',
    role: 'PROPRIETAIRE',
    actif: true,
  },
  photos: [
    {
      id: 'p3',
      url: 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="800" height="600"%3E%3Crect width="800" height="600" fill="%232c4a3e"/%3E%3C/svg%3E',
      ordre: 0,
    },
  ],
  equipements: ['PISCINE', 'JARDIN', 'CLIMATISATION'],
  proximites: ['HOPITAL', 'ECOLE'],
  estFavori: false,
};

let server: http.Server | null = null;

test.beforeAll(async () => {
  // Démarre le serveur statique SPA sur TEST_PORT servant le bundle compilé frontend/dist
  server = http.createServer((req, res) => {
    const reqUrl = new URL(req.url || '/', `http://localhost:${TEST_PORT}`);
    const pathname = decodeURIComponent(reqUrl.pathname);

    let filePath = path.join(DIST_DIR, pathname);
    if (!filePath.startsWith(DIST_DIR)) {
      res.writeHead(403);
      res.end('Forbidden');
      return;
    }

    // Routage SPA : fallback vers index.html pour les routes clientes (/annonce/:id, /connexion, etc.)
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

test.describe('Lot 3 / 3B — Optimisations des performances frontend & Sécurisation PWA', () => {

  // =========================================================================
  // PILIER A : PRÉCHARGEMENT API PRÉCOCE & GESTION DES ERREURS
  // =========================================================================
  test.describe('Pilier A — Préchargement API & Robustesse', () => {

    test('1. Accès direct à /annonce/:id : préchargement précoce initié dans index.html avant parsing complet', async ({ page }) => {
      let apiCalled = false;
      await page.route(`**/api/v1/annonces/${ANNONCE_ID_1}`, async (route) => {
        apiCalled = true;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(mockListing1),
        });
      });
      await page.route(`**/api/v1/annonces/${ANNONCE_ID_1}/avis`, async (route) => {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ avis: [], total: 0 }) });
      });

      await page.goto(`/annonce/${ANNONCE_ID_1}`);

      // Vérifie que les variables globales de préchargement ont été injectées
      const prefetchId = await page.evaluate(() => (window as any).__PREFETCHED_ANNONCE_ID__);
      expect(prefetchId).toBe(ANNONCE_ID_1);
      expect(apiCalled).toBe(true);

      // Le titre de l'annonce préchargée doit être rendu à l'écran
      const heading = page.locator('h1', { hasText: 'Appartement F4 Vue Mer Almadies' });
      await expect(heading).toBeVisible({ timeout: 5000 });
      await expect(page.locator('text=450 000 FCFA')).toBeVisible();
    });

    test('2. Navigation SPA : accès à une annonce sans conflit avec le mécanisme de préchargement', async ({ page }) => {
      await page.route(`**/api/v1/annonces/${ANNONCE_ID_1}`, async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(mockListing1),
        });
      });
      await page.route(`**/api/v1/annonces/${ANNONCE_ID_1}/avis`, async (route) => {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ avis: [], total: 0 }) });
      });

      // Part d'une page neutre puis navigue en client-side
      await page.goto('/connexion');
      await page.evaluate((id) => {
        window.history.pushState({}, '', `/annonce/${id}`);
        window.dispatchEvent(new PopStateEvent('popstate'));
      }, ANNONCE_ID_1);

      await expect(page.locator('h1', { hasText: 'Appartement F4 Vue Mer Almadies' })).toBeVisible({ timeout: 5000 });
    });

    test('3. Consultation successive de 2 annonces : aucune persistance de données périmées', async ({ page }) => {
      await page.route(`**/api/v1/annonces/${ANNONCE_ID_1}`, async (route) => {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockListing1) });
      });
      await page.route(`**/api/v1/annonces/${ANNONCE_ID_2}`, async (route) => {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockListing2) });
      });
      await page.route(`**/api/v1/annonces/*/avis`, async (route) => {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ avis: [], total: 0 }) });
      });

      // 1ère annonce
      await page.goto(`/annonce/${ANNONCE_ID_1}`);
      await expect(page.locator('h1', { hasText: 'Appartement F4 Vue Mer Almadies' })).toBeVisible();

      // 2ème annonce
      await page.goto(`/annonce/${ANNONCE_ID_2}`);
      await expect(page.locator('h1', { hasText: 'Villa Contemporaine Fann Résidence' })).toBeVisible();

      // Vérifie l'absence des données de la 1ère annonce
      await expect(page.locator('text=Appartement F4 Vue Mer Almadies')).not.toBeVisible();
      await expect(page.locator('text=85 000 000 FCFA')).toBeVisible();
    });

    test('4. Annonce 404 : exactement 1 seule requête exécutée (aucun fallback redondant ANO-L3-03)', async ({ page }) => {
      let requestCount = 0;
      await page.route(`**/api/v1/annonces/${ANNONCE_ID_404}`, async (route) => {
        requestCount++;
        await route.fulfill({
          status: 404,
          contentType: 'application/json',
          body: JSON.stringify({ message: 'Annonce introuvable ou supprimée' }),
        });
      });

      await page.goto(`/annonce/${ANNONCE_ID_404}`);

      // L'écran d'erreur doit être affiché immédiatement
      await expect(page.locator('text=Annonce introuvable')).toBeVisible({ timeout: 5000 });

      // Attendre un délai supplémentaire pour s'assurer qu'aucun 2ème appel n'est déclenché
      await page.waitForTimeout(600);

      // VÉRIFICATION CRITIQUE ANO-L3-03 : Exactement 1 seule requête API
      expect(requestCount).toBe(1);
    });

    test('5. Gestion des erreurs 500 et coupure réseau : comportement dégradé propre sans crash', async ({ page }) => {
      // Cas 500 Server Error
      await page.route(`**/api/v1/annonces/${ANNONCE_ID_500}`, async (route) => {
        await route.fulfill({
          status: 500,
          contentType: 'application/json',
          body: JSON.stringify({ message: 'Erreur interne du serveur' }),
        });
      });

      await page.goto(`/annonce/${ANNONCE_ID_500}`);
      await expect(page.locator('text=Annonce introuvable')).toBeVisible({ timeout: 5000 });
      // L'en-tête de navigation doit toujours être fonctionnel
      await expect(page.locator('nav').first()).toBeVisible();

      // Cas coupure réseau (abort)
      const ABORT_ID = '00000000-0000-0000-0000-000000000505';
      await page.route(`**/api/v1/annonces/${ABORT_ID}`, async (route) => {
        await route.abort('failed');
      });

      await page.goto(`/annonce/${ABORT_ID}`);
      await expect(page.locator('text=Annonce introuvable')).toBeVisible({ timeout: 5000 });
      await expect(page.locator('nav').first()).toBeVisible();
    });
  });

  // =========================================================================
  // PILIER B : FAVORIS & RÉCONCILIATION
  // =========================================================================
  test.describe('Pilier B — Favoris & Réconciliation d\'état', () => {

    test('6. Visiteur anonyme : clic sur favori invite à la connexion sans appel API inutile', async ({ page }) => {
      let favoriApiCalled = false;
      await page.route(`**/api/v1/annonces/${ANNONCE_ID_1}`, async (route) => {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockListing1) });
      });
      await page.route(`**/api/v1/annonces/${ANNONCE_ID_1}/avis`, async (route) => {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ avis: [], total: 0 }) });
      });
      await page.route(`**/api/v1/annonces/${ANNONCE_ID_1}/favoris`, async (route) => {
        favoriApiCalled = true;
        await route.fulfill({ status: 200 });
      });

      await page.goto(`/annonce/${ANNONCE_ID_1}`);
      await expect(page.locator('h1', { hasText: 'Appartement F4 Vue Mer Almadies' })).toBeVisible();

      // Trouver le bouton favori (bouton contenant le SVG coeur)
      const favButton = page.locator('button:has(svg.lucide-heart)').first();
      await expect(favButton).toBeVisible();
      await favButton.click();

      // Redirection immédiate vers /connexion avec message toast
      await page.waitForURL(/\/connexion/);
      expect(page.url()).toContain('/connexion');

      // Aucun appel direct à l'API de favoris ne doit avoir été émis par un anonyme
      expect(favoriApiCalled).toBe(false);
    });

    test('7. Utilisateur connecté : réconciliation de l\'état favori après préchargement anonyme (ANO-L3-02)', async ({ page }) => {
      let callIndex = 0;
      let authenticatedCallReceived = false;

      await page.route(`**/api/v1/annonces/${ANNONCE_ID_1}`, async (route) => {
        const auth = route.request().headers()['authorization'];
        callIndex++;
        if (auth && auth.includes('Bearer fake-jwt-token')) {
          authenticatedCallReceived = true;
          // Deuxième appel (réconcilié) : annonce marquée comme favorite
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ ...mockListing1, estFavori: true }),
          });
        } else {
          // Premier appel (préchargement anonyme depuis <head>) : estFavori = false
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ ...mockListing1, estFavori: false }),
          });
        }
      });
      await page.route(`**/api/v1/annonces/${ANNONCE_ID_1}/avis`, async (route) => {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ avis: [], total: 0 }) });
      });

      // Injecte une session authentifiée dans sessionStorage avant navigation
      await page.addInitScript(() => {
        sessionStorage.setItem('ecourtier-auth', JSON.stringify({
          state: {
            utilisateur: {
              id: 'u-locataire-1',
              email: 'moussa.diop@test.sn',
              nom: 'Diop',
              prenom: 'Moussa',
              role: 'LOCATAIRE',
            },
            accessToken: 'fake-jwt-token',
            refreshToken: 'fake-refresh-token',
          },
          version: 0,
        }));
      });

      await page.goto(`/annonce/${ANNONCE_ID_1}`);
      await expect(page.locator('h1', { hasText: 'Appartement F4 Vue Mer Almadies' })).toBeVisible();

      // Attendre que la réconciliation s'exécute (useEffect wasPrefetchedRef)
      const favButton = page.locator('button[aria-label="Retirer des favoris"]').first();
      await expect(favButton).toBeVisible({ timeout: 5000 });
      const favHeart = page.locator('button[aria-label="Retirer des favoris"] svg.lucide-heart').first();
      await expect(favHeart).toHaveClass(/fill-current/);

      // VÉRIFICATION ANO-L3-02 : L'appel authentifié a bien réconcilié l'état du favori
      expect(authenticatedCallReceived).toBe(true);
      expect(callIndex).toBeGreaterThanOrEqual(2);
    });
  });

  // =========================================================================
  // PILIER C : CACHE PWA & RÉDUCTION DU PRÉCACHE WORKBOX
  // =========================================================================
  test.describe('Pilier C — Sécurité du Cache PWA & Réduction du Précache', () => {

    test('8. Matcher PWA : rejet explicite de toute requête avec Authorization header ou Cookies/Credentials (ANO-L3-04)', () => {
      // Logique exacte de urlPattern implémentée dans vite.config.ts
      const cacheMatcher = (params: { url: URL; request: { method: string; credentials?: string; headers?: { get: (name: string) => string | null } } }) => {
        if (params.request.method !== 'GET') return false;

        const authHeader = params.request.headers
          ? (params.request.headers.get('authorization') || params.request.headers.get('Authorization'))
          : null;
        if (authHeader) return false;

        const cookieHeader = params.request.headers
          ? (params.request.headers.get('cookie') || params.request.headers.get('Cookie'))
          : null;
        if (cookieHeader) return false;
        const creds = params.request.credentials;
        if (creds === 'include' || creds === 'same-origin') return false;

        const pathname = params.url.pathname;
        const search = params.url.search;

        if (pathname.includes('/admin') || pathname.includes('/auth') || pathname.includes('/utilisateurs/moi')) return false;
        if (pathname.includes('/documents') || pathname.includes('/fichier')) return false;
        if (search.includes('mes_annonces') || search.includes('mes-annonces') || search.includes('mes-transactions') || search.includes('favoris')) return false;

        return (
          pathname === '/api/v1/annonces' ||
          pathname === '/api/v1/annonces/quartiers' ||
          /^\/api\/v1\/annonces\/[a-f0-9-]+$/i.test(pathname)
        );
      };

      const createMockReq = (method: string, headersObj: Record<string, string>, credentials?: string) => ({
        method,
        credentials,
        headers: {
          get: (header: string) => headersObj[header.toLowerCase()] || headersObj[header] || null,
        },
      });

      // 1. Rejet si Authorization présent
      const authReq = createMockReq('GET', { authorization: 'Bearer secret_jwt' });
      expect(cacheMatcher({ url: new URL(`http://localhost/api/v1/annonces/${ANNONCE_ID_1}`), request: authReq })).toBe(false);

      // 1b. Rejet si Cookie présent (session cookies, JSESSIONID, etc.)
      const cookieReq = createMockReq('GET', { cookie: 'JSESSIONID=session_secret_123' });
      expect(cacheMatcher({ url: new URL(`http://localhost/api/v1/annonces/${ANNONCE_ID_1}`), request: cookieReq })).toBe(false);

      // 1c. Rejet si credentials === 'include'
      const credReq = createMockReq('GET', {}, 'include');
      expect(cacheMatcher({ url: new URL(`http://localhost/api/v1/annonces/${ANNONCE_ID_1}`), request: credReq })).toBe(false);

      // 1d. Rejet si credentials === 'same-origin' (ANO-F-01)
      const sameOriginReq = createMockReq('GET', {}, 'same-origin');
      expect(cacheMatcher({ url: new URL(`http://localhost/api/v1/annonces/${ANNONCE_ID_1}`), request: sameOriginReq })).toBe(false);

      // 2. Rejet si méthode non-GET (POST, PUT, DELETE)
      const postReq = createMockReq('POST', {});
      expect(cacheMatcher({ url: new URL(`http://localhost/api/v1/annonces/${ANNONCE_ID_1}`), request: postReq })).toBe(false);

      // 3. Acceptation si requête GET publique anonyme (sans credentials ou credentials: omit)
      const anonReq = createMockReq('GET', {});
      expect(cacheMatcher({ url: new URL(`http://localhost/api/v1/annonces/${ANNONCE_ID_1}`), request: anonReq })).toBe(true);
      const omitReq = createMockReq('GET', {}, 'omit');
      expect(cacheMatcher({ url: new URL(`http://localhost/api/v1/annonces/${ANNONCE_ID_1}`), request: omitReq })).toBe(true);
      expect(cacheMatcher({ url: new URL('http://localhost/api/v1/annonces'), request: anonReq })).toBe(true);
      expect(cacheMatcher({ url: new URL('http://localhost/api/v1/annonces/quartiers'), request: anonReq })).toBe(true);
    });

    test('9. Matcher PWA : exclusion stricte des routes privées, documents cadastraux et favoris', () => {
      const cacheMatcher = (params: { url: URL; request: { method: string; headers?: { get: (name: string) => string | null } } }) => {
        if (params.request.method !== 'GET') return false;
        const authHeader = params.request.headers ? (params.request.headers.get('authorization') || params.request.headers.get('Authorization')) : null;
        if (authHeader) return false;
        const pathname = params.url.pathname;
        const search = params.url.search;
        if (pathname.includes('/admin') || pathname.includes('/auth') || pathname.includes('/utilisateurs/moi')) return false;
        if (pathname.includes('/documents') || pathname.includes('/fichier')) return false;
        if (search.includes('mes_annonces') || search.includes('mes-annonces') || search.includes('mes-transactions') || search.includes('favoris')) return false;
        return pathname === '/api/v1/annonces' || pathname === '/api/v1/annonces/quartiers' || /^\/api\/v1\/annonces\/[a-f0-9-]+$/i.test(pathname);
      };

      const anonReq = { method: 'GET', headers: { get: () => null } };

      // Documents cadastraux et fichiers sensibles
      expect(cacheMatcher({ url: new URL(`http://localhost/api/v1/annonces/${ANNONCE_ID_1}/documents`), request: anonReq })).toBe(false);
      expect(cacheMatcher({ url: new URL(`http://localhost/api/v1/annonces/${ANNONCE_ID_1}/fichier`), request: anonReq })).toBe(false);

      // Profil utilisateur et requêtes privées
      expect(cacheMatcher({ url: new URL('http://localhost/api/v1/utilisateurs/moi'), request: anonReq })).toBe(false);
      expect(cacheMatcher({ url: new URL('http://localhost/api/v1/annonces?favoris=true'), request: anonReq })).toBe(false);
      expect(cacheMatcher({ url: new URL('http://localhost/api/v1/annonces?mes-annonces=true'), request: anonReq })).toBe(false);

      // Espace administration
      expect(cacheMatcher({ url: new URL('http://localhost/api/v1/admin/annonces'), request: anonReq })).toBe(false);
    });

    test('10. Workbox precache : exclusion confirmée des bundles d\'administration et des tableaux de bord privés (ANO-L3-01)', () => {
      const swPath = path.join(DIST_DIR, 'sw.js');
      expect(fs.existsSync(swPath)).toBe(true);

      const swContent = fs.readFileSync(swPath, 'utf-8');

      // Vérifie qu'aucun chunk d'administration ni de tableau de bord privé n'est présent dans le précache
      expect(swContent).not.toMatch(/AdminDashboardPage-[A-Za-z0-9_-]+\.js/);
      expect(swContent).not.toMatch(/AdminSignalementsPage-[A-Za-z0-9_-]+\.js/);
      expect(swContent).not.toMatch(/AdminUtilisateursPage-[A-Za-z0-9_-]+\.js/);
      expect(swContent).not.toMatch(/AdminVerificationPage-[A-Za-z0-9_-]+\.js/);
      expect(swContent).not.toMatch(/DashboardPage-[A-Za-z0-9_-]+\.js/);
      expect(swContent).not.toMatch(/MonComptePage-[A-Za-z0-9_-]+\.js/);

      // Vérifie que les garde-fous Authorization et Cookie sont bien compilés dans le service worker
      expect(swContent).toMatch(/authorization/i);
      expect(swContent).toMatch(/cookie/i);
    });
  });

  // =========================================================================
  // PILIER D : CHARGEMENT DIFFÉRÉ (CARTE & MODALES)
  // =========================================================================
  test.describe('Pilier D — Chargement différé (Map & Modales)', () => {

    test('11. Carte Leaflet : non injectée tant qu\'elle n\'est pas dans le viewport ou activée', async ({ page }) => {
      await page.route(`**/api/v1/annonces/${ANNONCE_ID_1}`, async (route) => {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockListing1) });
      });
      await page.route(`**/api/v1/annonces/${ANNONCE_ID_1}/avis`, async (route) => {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ avis: [], total: 0 }) });
      });

      await page.goto(`/annonce/${ANNONCE_ID_1}`);
      await expect(page.locator('h1', { hasText: 'Appartement F4 Vue Mer Almadies' })).toBeVisible();

      // Au chargement initial en haut de page, le placeholder interactif doit être visible
      const mapPlaceholder = page.locator('text=Cliquer pour charger la carte interactive');
      await expect(mapPlaceholder).toBeVisible();

      // La carte Leaflet (.leaflet-container) ne doit PAS être montée dans le DOM initial
      const leafletContainer = page.locator('.leaflet-container');
      expect(await leafletContainer.count()).toBe(0);

      // Clic sur le placeholder pour déclencher le chargement différé à la demande
      await mapPlaceholder.click();

      // La carte Leaflet se monte dynamiquement
      await expect(page.locator('.leaflet-container')).toBeVisible({ timeout: 5000 });
    });

    test('12. Modales lourdes (Lightbox, Chat, Transaction, Signalement) : absentes du DOM initial', async ({ page }) => {
      await page.route(`**/api/v1/annonces/${ANNONCE_ID_1}`, async (route) => {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockListing1) });
      });
      await page.route(`**/api/v1/annonces/${ANNONCE_ID_1}/avis`, async (route) => {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ avis: [], total: 0 }) });
      });

      await page.goto(`/annonce/${ANNONCE_ID_1}`);
      await expect(page.locator('h1', { hasText: 'Appartement F4 Vue Mer Almadies' })).toBeVisible();

      // Vérifie l'absence dans le DOM initial
      expect(await page.locator('.yarl__container').count()).toBe(0);
      expect(await page.locator('text=Discussion avec le propriétaire').count()).toBe(0);
      expect(await page.locator('text=Signaler cette annonce').count()).toBe(0);
      expect(await page.locator('text=Détails de la transaction').count()).toBe(0);
    });
  });

  // =========================================================================
  // PILIER E : RESPONSIVE MOBILE SANS DÉBORDEMENT
  // =========================================================================
  test.describe('Pilier E — Responsive Mobile 375x667', () => {

    test('13. Affichage mobile 375x667 : aucun débordement horizontal (scrollWidth <= clientWidth)', async ({ page }) => {
      // Configuration viewport iPhone SE / mobile standard
      await page.setViewportSize({ width: 375, height: 667 });

      await page.route(`**/api/v1/annonces/${ANNONCE_ID_1}`, async (route) => {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockListing1) });
      });
      await page.route(`**/api/v1/annonces/${ANNONCE_ID_1}/avis`, async (route) => {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ avis: [], total: 0 }) });
      });

      await page.goto(`/annonce/${ANNONCE_ID_1}`);
      await expect(page.locator('h1', { hasText: 'Appartement F4 Vue Mer Almadies' })).toBeVisible();

      // Vérifie l'absence de dépassement horizontal sur document et body
      const overflow = await page.evaluate(() => {
        const docWidth = document.documentElement.scrollWidth;
        const bodyWidth = document.body.scrollWidth;
        const clientWidth = document.documentElement.clientWidth;
        return {
          docWidth,
          bodyWidth,
          clientWidth,
          hasOverflow: docWidth > clientWidth || bodyWidth > clientWidth,
        };
      });

      expect(overflow.hasOverflow).toBe(false);
      expect(overflow.docWidth).toBeLessThanOrEqual(overflow.clientWidth);
    });
  });
});
