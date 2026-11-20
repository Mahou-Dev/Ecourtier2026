import { test, expect } from '@playwright/test';

const UI_URL = process.env.E2E_UI_URL || 'http://127.0.0.1:3000';
const API_URL = process.env.E2E_API_URL || 'http://localhost:8080';

async function getToken(email: string, motDePasse: string) {
  const res = await fetch(`${API_URL}/api/v1/auth/connexion`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, motDePasse }),
  });
  if (!res.ok) throw new Error(`Auth failed for ${email}: ${res.status}`);
  return res.json();
}

async function applySession(page: import('@playwright/test').Page, authPayload: Record<string, unknown>) {
  await page.evaluate((payload) => {
    window.sessionStorage.setItem(
      'ecourtier-auth',
      JSON.stringify({
        state: {
          utilisateur: payload.utilisateur,
          accessToken: payload.accessToken,
          refreshToken: payload.refreshToken,
        },
        version: 0,
      })
    );
  }, authPayload);
}

test.describe('Mission 2.1 — Socle Transversal, Médias, Deep-Link & Samba IA Global', () => {
  test('1. Route 404 catch-all, redirection /admin, guards auth et non-routage de ComingSoon', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.route('**/*fonts.g*', (r) => r.abort());

    // 404 catch-all page
    await page.goto(`${UI_URL}/cette-route-n-existe-pas-audit`, { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: /Page introuvable/i })).toBeVisible();
    await expect(page.getByText(/Erreur 404/i)).toBeVisible();
    await expect(page.getByRole('link', { name: /Retour à l’accueil/i })).toBeVisible();
    await expect(page.getByRole('link', { name: /Explorer les annonces/i })).toBeVisible();

    // Protected route redirects to /connexion when unauthenticated
    await page.goto(`${UI_URL}/favoris`, { waitUntil: 'domcontentloaded' });
    await expect(page).toHaveURL(/\/connexion/);

    // /admin redirects cleanly (when unauthenticated or admin; when unauthenticated -> /admin/dashboard -> /connexion)
    await page.goto(`${UI_URL}/admin`, { waitUntil: 'domcontentloaded' });
    await expect(page).toHaveURL(/\/connexion|\/admin\/dashboard/);
  });

  test('2. Deep-link /annonce/:id en environnement dev, SPA navigation, préfetch HTML invalide & absence de doublons', async ({
    page,
  }) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));

    await page.setViewportSize({ width: 1440, height: 900 });
    await page.route('**/*fonts.g*', (r) => r.abort());

    // Find a genuine listing ID from live API
    const listRes = await fetch(`${API_URL}/api/v1/annonces?page=0&size=5&statutVerification=VERIFIEE`);
    const listJson = (await listRes.json()) as { content: Array<{ id: string; titre: string }> };
    const realListing = listJson.content[0];
    expect(realListing?.id).toBeTruthy();

    // 2a. Direct URL access on Vite dev (:3000/annonce/:id) where window.location.origin /api returns 404
    await page.goto(`${UI_URL}/annonce/${realListing.id}`, { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { level: 1 })).toContainText(realListing.titre, { timeout: 10000 });

    // 2b. SPA navigation from /recherche -> /annonce/:id
    await page.goto(`${UI_URL}/recherche`, { waitUntil: 'domcontentloaded' });
    const firstCardLink = page.getByRole('link', { name: /Voir la fiche/i }).first();
    await expect(firstCardLink).toBeVisible();
    await firstCardLink.click();
    await expect(page).toHaveURL(/\/annonce\//);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

    // 2c. Vite invalid HTML prefetch (both 404 text/html and 200 text/html) -> falls back to apiFetch once
    const MOCK_ID = '11111111-1111-1111-1111-111111111111';
    const htmlPrefetchCtx = await page.context().browser()!.newContext({ viewport: { width: 1440, height: 900 } });
    const pHtml = await htmlPrefetchCtx.newPage();
    await pHtml.route('**/*fonts.g*', (r) => r.abort());
    let htmlOriginCalls = 0;
    let apiCalls = 0;
    await pHtml.route(`**/api/v1/annonces/${MOCK_ID}`, async (route) => {
      const url = route.request().url();
      if (url.startsWith(UI_URL)) {
        htmlOriginCalls++;
        return route.fulfill({ status: 404, contentType: 'text/html', body: '<!doctype html><html>Not Found</html>' });
      }
      apiCalls++;
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: MOCK_ID,
          titre: 'Villa Almadies Secours Prefetch',
          description: 'Annonce chargée via fallback apiFetch.',
          type: 'VILLA',
          modeTransaction: 'VENTE',
          prix: 120000000,
          superficie: 350,
          nombrePieces: 6,
          ville: 'Dakar',
          quartier: 'Almadies',
          latitude: 14.74,
          longitude: -17.51,
          meuble: true,
          animauxAcceptes: false,
          statutVerification: 'VERIFIEE',
          photos: [],
          equipements: [],
          proximites: [],
          auteur: { id: 'u1', prenom: 'Awa', nom: 'Diop', email: 'awa@ecourtier.sn', role: 'PROPRIETAIRE', telephone: '+221770000000' },
        }),
      });
    });
    await pHtml.route(`**/api/v1/annonces/${MOCK_ID}/avis`, (r) =>
      r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ avis: [], total: 0 }) })
    );
    await pHtml.goto(`${UI_URL}/annonce/${MOCK_ID}`, { waitUntil: 'domcontentloaded' });
    await expect(pHtml.getByRole('heading', { level: 1 })).toContainText('Villa Almadies Secours Prefetch');
    expect(htmlOriginCalls).toBe(1);
    expect(apiCalls).toBe(1);
    await htmlPrefetchCtx.close();

    // 2d. Valid same-origin JSON 200 prefetch -> exactly 1 request total (no duplicate apiFetch)
    const jsonOkCtx = await page.context().browser()!.newContext({ viewport: { width: 1440, height: 900 } });
    const pJsonOk = await jsonOkCtx.newPage();
    await pJsonOk.route('**/*fonts.g*', (r) => r.abort());
    let totalCount200 = 0;
    await pJsonOk.route(`**/api/v1/annonces/${MOCK_ID}`, async (route) => {
      totalCount200++;
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: MOCK_ID,
          titre: 'Appartement Prefetch JSON Valide',
          description: 'Réponse JSON prefetch directe.',
          type: 'APPARTEMENT',
          modeTransaction: 'LOCATION',
          prix: 450000,
          superficie: 110,
          nombrePieces: 4,
          ville: 'Dakar',
          quartier: 'Mermoz',
          latitude: 14.71,
          longitude: -17.47,
          meuble: true,
          animauxAcceptes: false,
          statutVerification: 'VERIFIEE',
          photos: [],
          equipements: [],
          proximites: [],
          auteur: { id: 'u1', prenom: 'Awa', nom: 'Diop', email: 'awa@ecourtier.sn', role: 'PROPRIETAIRE', telephone: '+221770000000' },
        }),
      });
    });
    await pJsonOk.route(`**/api/v1/annonces/${MOCK_ID}/avis`, (r) =>
      r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ avis: [], total: 0 }) })
    );
    await pJsonOk.goto(`${UI_URL}/annonce/${MOCK_ID}`, { waitUntil: 'domcontentloaded' });
    await expect(pJsonOk.getByRole('heading', { level: 1 })).toContainText('Appartement Prefetch JSON Valide');
    await pJsonOk.waitForTimeout(400);
    expect(totalCount200).toBe(1);
    await jsonOkCtx.close();

    // 2e. Invalid 200 text/html SPA fallback prefetch -> ignored cleanly, falls back to apiFetch once
    const html200Ctx = await page.context().browser()!.newContext({ viewport: { width: 1440, height: 900 } });
    const pHtml200 = await html200Ctx.newPage();
    await pHtml200.route('**/*fonts.g*', (r) => r.abort());
    await pHtml200.route(`**/api/v1/annonces/${MOCK_ID}`, async (route) => {
      if (route.request().url().startsWith(UI_URL)) {
        return route.fulfill({ status: 200, contentType: 'text/html', body: '<!doctype html><html>SPA</html>' });
      }
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: MOCK_ID,
          titre: 'Villa Secours HTML 200',
          description: 'Chargée après rejet JSON invalide HTML.',
          type: 'VILLA',
          modeTransaction: 'VENTE',
          prix: 150000000,
          superficie: 400,
          nombrePieces: 6,
          ville: 'Dakar',
          quartier: 'Ngor',
          latitude: 14.75,
          longitude: -17.51,
          meuble: true,
          animauxAcceptes: false,
          statutVerification: 'VERIFIEE',
          photos: [],
          equipements: [],
          proximites: [],
          auteur: { id: 'u1', prenom: 'Awa', nom: 'Diop', email: 'awa@ecourtier.sn', role: 'PROPRIETAIRE', telephone: '+221770000000' },
        }),
      });
    });
    await pHtml200.route(`**/api/v1/annonces/${MOCK_ID}/avis`, (r) =>
      r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ avis: [], total: 0 }) })
    );
    await pHtml200.goto(`${UI_URL}/annonce/${MOCK_ID}`, { waitUntil: 'domcontentloaded' });
    await expect(pHtml200.getByRole('heading', { level: 1 })).toContainText('Villa Secours HTML 200');
    await html200Ctx.close();

    // 2f. Real API 404 JSON on prefetch -> single request only + "Annonce introuvable"
    const MISSING_ID = '22222222-2222-2222-2222-222222222222';
    const json404Ctx = await page.context().browser()!.newContext({ viewport: { width: 1440, height: 900 } });
    const pJson404 = await json404Ctx.newPage();
    await pJson404.route('**/*fonts.g*', (r) => r.abort());
    let count404 = 0;
    await pJson404.route(`**/api/v1/annonces/${MISSING_ID}`, (route) => {
      count404++;
      return route.fulfill({
        status: 404,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Annonce introuvable' }),
      });
    });
    await pJson404.goto(`${UI_URL}/annonce/${MISSING_ID}`, { waitUntil: 'domcontentloaded' });
    await expect(pJson404.getByRole('heading', { name: /Annonce introuvable/i })).toBeVisible();
    await pJson404.waitForTimeout(400);
    expect(count404).toBe(1);
    await json404Ctx.close();

    // 2g. Genuine server 500 JSON error -> clean degraded error UI without uncaught crash
    const ERR_ID = '33333333-3333-3333-3333-333333333333';
    const json500Ctx = await page.context().browser()!.newContext({ viewport: { width: 1440, height: 900 } });
    const pJson500 = await json500Ctx.newPage();
    await pJson500.route('**/*fonts.g*', (r) => r.abort());
    await pJson500.route(`**/api/v1/annonces/${ERR_ID}`, (route) =>
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Erreur interne serveur' }),
      })
    );
    await pJson500.goto(`${UI_URL}/annonce/${ERR_ID}`, { waitUntil: 'domcontentloaded' });
    await expect(pJson500.getByRole('heading', { name: /Annonce introuvable/i })).toBeVisible({ timeout: 8000 });
    await json500Ctx.close();

    expect(errors).toEqual([]);
  });

  test('3. Cascade 3 niveaux PropertyVisual sans aplats 8x8 sur /recherche et /annonce/:id', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.route('**/*fonts.g*', (r) => r.abort());

    const tiny8x8Svg =
      '<svg xmlns="http://www.w3.org/2000/svg" width="8" height="8"><rect width="8" height="8" fill="#E96D3F"/></svg>';
    const validSvg =
      '<svg xmlns="http://www.w3.org/2000/svg" width="320" height="200"><rect width="320" height="200" fill="#1C352E"/></svg>';
    await page.route('**/mock-8x8.svg', (r) => r.fulfill({ status: 200, contentType: 'image/svg+xml', body: tiny8x8Svg }));
    await page.route('**/mock-real.svg', (r) => r.fulfill({ status: 200, contentType: 'image/svg+xml', body: validSvg }));

    await page.route('**/api/v1/annonces?*', (r) =>
      r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            {
              id: 'r-real',
              titre: 'Villa Réelle Valide',
              type: 'VILLA',
              modeTransaction: 'VENTE',
              prix: 90000000,
              superficie: 280,
              nombrePieces: 5,
              ville: 'Dakar',
              quartier: 'Almadies',
              latitude: 14.74,
              longitude: -17.51,
              meuble: false,
              animauxAcceptes: false,
              statutVerification: 'VERIFIEE',
              photos: [{ id: 'p1', url: '/mock-real.svg', ordre: 0 }],
            },
            {
              id: 'r-8x8',
              titre: 'Appartement vignette 8x8 rejetée',
              type: 'APPARTEMENT',
              modeTransaction: 'LOCATION',
              prix: 350000,
              superficie: 95,
              nombrePieces: 3,
              ville: 'Dakar',
              quartier: 'Plateau',
              latitude: 14.67,
              longitude: -17.43,
              meuble: true,
              animauxAcceptes: false,
              statutVerification: 'VERIFIEE',
              photos: [{ id: 'p2', url: '/mock-8x8.svg', ordre: 0 }],
            },
            {
              id: 'r-empty',
              titre: 'Terrain sans photo',
              type: 'TERRAIN',
              modeTransaction: 'VENTE',
              prix: 25000000,
              superficie: 300,
              nombrePieces: 0,
              ville: 'Diamniadio',
              quartier: 'Pôle Urbain',
              latitude: 14.72,
              longitude: -17.2,
              meuble: false,
              animauxAcceptes: false,
              statutVerification: 'VERIFIEE',
              photos: [],
            },
          ],
          number: 0,
          size: 12,
          totalElements: 3,
          totalPages: 1,
          first: true,
          last: true,
        }),
      })
    );

    await page.goto(`${UI_URL}/recherche`, { waitUntil: 'domcontentloaded' });
    await expect(page.getByText('Villa Réelle Valide')).toBeVisible();
    await page.waitForTimeout(500);

    // No /placeholder.jpg requested, real photo keeps src, 8x8 and empty show "Image illustrative"
    const imgs = await page.evaluate(() =>
      Array.from(document.querySelectorAll('img')).map((i) => ({
        src: i.getAttribute('src') || '',
        w: i.naturalWidth,
      }))
    );
    expect(imgs.some((i) => i.src.includes('placeholder.jpg'))).toBe(false);
    expect(imgs.some((i) => i.src.includes('mock-real.svg'))).toBe(true);
    expect(imgs.some((i) => i.src.includes('mock-8x8.svg'))).toBe(false);
    expect(await page.getByText('Image illustrative').count()).toBeGreaterThanOrEqual(2);

    // Fallback image failure transitions cleanly to Level-3 graphic placeholder ("Visuel non disponible")
    await page.route(/\/(apartment|villa|house|land|commercial|default)-\d+.*\.webp/i, (r) => {
      if (r.request().resourceType() === 'image') return r.abort();
      return r.continue();
    });
    await page.reload({ waitUntil: 'domcontentloaded' });
    await expect(page.getByText('Visuel non disponible').first()).toBeVisible({ timeout: 6000 });
  });

  test('4. Politique Footer par famille de routes & non-collision globale Samba IA', async ({ page }) => {
    await page.route('**/*fonts.g*', (r) => r.abort());

    // 4a. /recherche has NO footer & no collision between Samba IA and Leaflet zoom or filters
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto(`${UI_URL}/recherche`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(500);
    await expect(page.locator('footer')).toHaveCount(0);
    const searchControlsOverlap = await page.evaluate(() => {
      const samba = document.querySelector('button[aria-label="Ouvrir l\'assistant IA E-Courtier"]')?.getBoundingClientRect();
      const zoom = document.querySelector('.leaflet-control-zoom')?.getBoundingClientRect();
      if (!samba || !zoom) return 0;
      const x = Math.max(0, Math.min(samba.right, zoom.right) - Math.max(samba.left, zoom.left));
      const y = Math.max(0, Math.min(samba.bottom, zoom.bottom) - Math.max(samba.top, zoom.top));
      return x * y;
    });
    expect(searchControlsOverlap).toBe(0);

    // Samba IA compact button preserves aria-label + sr-only & opens/closes by keyboard
    const sambaBtn = page.getByRole('button', { name: /Ouvrir l'assistant IA E-Courtier/i });
    await expect(sambaBtn).toBeVisible();
    await expect(page.locator('button:has-text("Assistant IA E-Courtier")')).toHaveCount(1);
    await sambaBtn.focus();
    await page.keyboard.press('Enter');
    await expect(page.getByText(/Samba — IA Immobilier/i)).toBeVisible();
    await page.getByRole('button', { name: /Fermer l'assistant/i }).click();

    // 4b. /annonce/:id keeps full editorial footer & 0 Samba overlap on booking CTA/price
    const listRes = await fetch(`${API_URL}/api/v1/annonces?page=0&size=1&statutVerification=VERIFIEE`);
    const listJson = (await listRes.json()) as { content: Array<{ id: string }> };
    const firstId = listJson.content[0].id;
    await page.goto(`${UI_URL}/annonce/${firstId}`, { waitUntil: 'domcontentloaded' });
    await expect(page.locator('footer')).toBeVisible();
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

    const detailOverlap = await page.evaluate(() => {
      const samba = document.querySelector('button[aria-label="Ouvrir l\'assistant IA E-Courtier"]')?.getBoundingClientRect();
      const card = document.querySelector('#booking-card')?.getBoundingClientRect();
      if (!samba || !card) return 999;
      const x = Math.max(0, Math.min(samba.right, card.right) - Math.max(samba.left, card.left));
      const y = Math.max(0, Math.min(samba.bottom, card.bottom) - Math.max(samba.top, card.top));
      return x * y;
    });
    expect(detailOverlap).toBe(0);

    // Check 1366x768 laptop viewport as well for zero collision with #booking-card
    await page.setViewportSize({ width: 1366, height: 768 });
    await page.waitForTimeout(200);
    const detailOverlap1366 = await page.evaluate(() => {
      const samba = document.querySelector('button[aria-label="Ouvrir l\'assistant IA E-Courtier"]')?.getBoundingClientRect();
      const card = document.querySelector('#booking-card')?.getBoundingClientRect();
      if (!samba || !card) return 999;
      const x = Math.max(0, Math.min(samba.right, card.right) - Math.max(samba.left, card.left));
      const y = Math.max(0, Math.min(samba.bottom, card.bottom) - Math.max(samba.top, card.top));
      return x * y;
    });
    expect(detailOverlap1366).toBe(0);
    await page.setViewportSize({ width: 1440, height: 900 });

    // 4c. Authenticated Tenant (/favoris full footer + clearance), Landlord (/dashboard & Messages) & Admin checks
    const locAuth = await getToken('locataire.demo@ecourtier.sn', 'LocatairePass123!');
    await applySession(page, locAuth);
    await page.goto(`${UI_URL}/favoris`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(400);
    await expect(page.locator('footer')).toBeVisible();
    const favorisFooterOverlap = await page.evaluate(() => {
      window.scrollTo(0, document.body.scrollHeight);
      const samba = document.querySelector('button[aria-label="Ouvrir l\'assistant IA E-Courtier"]')?.getBoundingClientRect();
      const copyP = Array.from(document.querySelectorAll('footer p')).pop()?.getBoundingClientRect();
      if (!samba || !copyP) return 0;
      const x = Math.max(0, Math.min(samba.right, copyP.right) - Math.max(samba.left, copyP.left));
      const y = Math.max(0, Math.min(samba.bottom, copyP.bottom) - Math.max(samba.top, copyP.top));
      return x * y;
    });
    expect(favorisFooterOverlap).toBe(0);

    const proprioAuth = await getToken('proprio.demo@ecourtier.sn', 'ProprioPass123!');
    await applySession(page, proprioAuth);
    await page.goto(`${UI_URL}/dashboard`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(500);
    const dashFooterBox = await page.locator('footer').boundingBox();
    expect(dashFooterBox?.height || 999).toBeLessThan(90);

    await page.goto(`${UI_URL}/dashboard?tab=messages`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(700);
    const footerBox = await page.locator('footer').boundingBox();
    expect(footerBox?.height || 999).toBeLessThan(90);

    // Mobile 390x844 Messages Send button vs Samba IA
    await page.setViewportSize({ width: 390, height: 844 });
    const convBtn = page.locator('button').filter({ hasText: /Audit|Sacré|Centre|Almadies|Mermoz|Diabir|Villa/i }).first();
    await expect(convBtn).toBeVisible();
    await convBtn.click();
    await page.waitForTimeout(300);
    const sendOverlap = await page.evaluate(() => {
      const samba = document.querySelector('button[aria-label="Ouvrir l\'assistant IA E-Courtier"]')?.getBoundingClientRect();
      const send = document.querySelector('button[aria-label="Envoyer"]')?.getBoundingClientRect();
      if (!samba || !send) return 999;
      const x = Math.max(0, Math.min(samba.right, send.right) - Math.max(samba.left, send.left));
      const y = Math.max(0, Math.min(samba.bottom, send.bottom) - Math.max(samba.top, send.top));
      return x * y;
    });
    expect(sendOverlap).toBe(0);

    // 4d. Admin compact footer & non-overlap on /admin/utilisateurs and /admin/transactions
    await page.setViewportSize({ width: 1440, height: 900 });
    const adminAuth = await getToken('admin@ecourtier.sn', 'AdminPass123!');
    await applySession(page, adminAuth);
    await page.goto(`${UI_URL}/admin/utilisateurs`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(600);
    const adminFooterBox = await page.locator('footer').boundingBox();
    expect(adminFooterBox?.height || 999).toBeLessThan(90);
    const adminActionsOverlap = await page.evaluate(() => {
      const samba = document.querySelector('button[aria-label="Ouvrir l\'assistant IA E-Courtier"]')?.getBoundingClientRect();
      if (!samba) return 999;
      let maxOverlap = 0;
      document.querySelectorAll('table button, table a').forEach((el) => {
        const r = el.getBoundingClientRect();
        const x = Math.max(0, Math.min(samba.right, r.right) - Math.max(samba.left, r.left));
        const y = Math.max(0, Math.min(samba.bottom, r.bottom) - Math.max(samba.top, r.top));
        maxOverlap = Math.max(maxOverlap, x * y);
      });
      return maxOverlap;
    });
    expect(adminActionsOverlap).toBe(0);

    await page.goto(`${UI_URL}/admin/transactions`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(600);
    const txFooterBox = await page.locator('footer').boundingBox();
    expect(txFooterBox?.height || 999).toBeLessThan(90);
    const txActionsOverlap = await page.evaluate(() => {
      const samba = document.querySelector('button[aria-label="Ouvrir l\'assistant IA E-Courtier"]')?.getBoundingClientRect();
      if (!samba) return 999;
      let maxOverlap = 0;
      document.querySelectorAll('table button, table a').forEach((el) => {
        const r = el.getBoundingClientRect();
        const x = Math.max(0, Math.min(samba.right, r.right) - Math.max(samba.left, r.left));
        const y = Math.max(0, Math.min(samba.bottom, r.bottom) - Math.max(samba.top, r.top));
        maxOverlap = Math.max(maxOverlap, x * y);
      });
      return maxOverlap;
    });
    expect(txActionsOverlap).toBe(0);
  });
});
