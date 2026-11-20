import { test, expect } from '@playwright/test';

const UI_URL = process.env.E2E_UI_URL || 'http://127.0.0.1:3000';

const ALL_VIEWPORTS = [
  { width: 320, height: 568, name: '320x568' },
  { width: 360, height: 800, name: '360x800' },
  { width: 375, height: 667, name: '375x667' },
  { width: 390, height: 844, name: '390x844' },
  { width: 430, height: 932, name: '430x932' },
  { width: 768, height: 1024, name: '768x1024' },
  { width: 820, height: 1180, name: '820x1180' },
  { width: 1024, height: 768, name: '1024x768' },
  { width: 1280, height: 800, name: '1280x800' },
  { width: 1366, height: 768, name: '1366x768' },
  { width: 1440, height: 900, name: '1440x900' },
  { width: 1920, height: 1080, name: '1920x1080' },
];

test.describe('Refonte Page Accueil / — Responsive, Accessibilité & Interactions', () => {
  test('Aucune erreur console/page, aucun &check; brut, et images locales chargées', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (err) => errors.push(err.message));
    page.on('console', (msg) => {
      if (msg.type() === 'error') errors.push(msg.text());
    });

    await page.setViewportSize({ width: 1440, height: 900 });
    await page.route('**/*fonts.g*', (r) => r.abort());
    await page.goto(`${UI_URL}/`, { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await page.waitForTimeout(800);

    const bodyText = await page.locator('body').innerText();
    expect(bodyText).not.toContain('&check;');

    await expect(page.getByRole('heading', { level: 1 })).toContainText(/immobilier au Sénégal/i);
    await expect(page.getByRole('region', { name: /Contrôles du diaporama/i })).toBeVisible();

    // Scroll cleanly down to ensure every lazy image enters viewport and finishes loading
    const imgCount = await page.locator('img').count();
    for (let i = 0; i < imgCount; i++) {
      await page.locator('img').nth(i).scrollIntoViewIfNeeded();
      await page.waitForTimeout(100);
    }
    await page.waitForLoadState('networkidle').catch(() => {});
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.waitForTimeout(300);

    const brokenLocalImages = await page.evaluate(() => {
      const imgs = Array.from(document.querySelectorAll('img'));
      return imgs
        .filter((img) => {
          const src = img.getAttribute('src') || '';
          if (src.startsWith('/assets/') || src.includes('.webp')) {
            return !img.complete || img.naturalWidth === 0;
          }
          return false;
        })
        .map((img) => img.getAttribute('src'));
    });
    expect(brokenLocalImages).toEqual([]);
    expect(errors).toEqual([]);
  });

  for (const vp of ALL_VIEWPORTS) {
    test(`Responsive sans débordement horizontal à ${vp.name}`, async ({ page }) => {
      await page.setViewportSize({ width: vp.width, height: vp.height });
      await page.route('**/*fonts.g*', (r) => r.abort());
      await page.goto(`${UI_URL}/`, { waitUntil: 'domcontentloaded' });
      await page.waitForLoadState('networkidle').catch(() => {});
      await page.waitForTimeout(400);

      const layout = await page.evaluate(() => ({
        scrollWidth: document.documentElement.scrollWidth,
        clientWidth: document.documentElement.clientWidth,
        hasH1: Boolean(document.querySelector('h1')),
        hasSearchSubmit: Boolean(document.querySelector('form[role="search"] button[type="submit"]')),
      }));

      expect(layout.hasH1).toBeTruthy();
      expect(layout.hasSearchSubmit).toBeTruthy();
      expect(layout.scrollWidth).toBeLessThanOrEqual(layout.clientWidth + 1);
    });
  }

  test('Carrousel hero, recherche réelle vers /recherche et favoris/navigation', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.route('**/*fonts.g*', (r) => r.abort());
    await page.goto(`${UI_URL}/`, { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await page.waitForTimeout(800);

    // Carousel controls
    await expect(page.getByText('Almadies, Dakar').first()).toBeVisible();
    await page.getByRole('button', { name: /Diapositive suivante/i }).click();
    await expect(page.getByText('Saly, Petite Côte').first()).toBeVisible();
    await page.getByRole('button', { name: /Diapositive précédente/i }).click();
    await expect(page.getByText('Almadies, Dakar').first()).toBeVisible();

    const pauseBtn = page.getByRole('button', { name: /Mettre en pause le défilement/i });
    await pauseBtn.click();
    await expect(page.getByRole('button', { name: /Reprendre le défilement/i })).toBeVisible();

    // Real verified listing card -> Consulter -> SPA /annonce/:id
    const firstConsultLink = page.locator('#annonces-verifiees a', { hasText: /Consulter/i }).first();
    await expect(firstConsultLink).toBeVisible();

    // Search form navigation
    await page.getByRole('button', { name: 'Acheter', exact: true }).click();
    await page.locator('#home-search-city').selectOption('Saly');
    await page.locator('#home-search-type').selectOption('VILLA');
    await page.locator('#home-search-query').fill('plage');
    await page.getByRole('switch', { name: /Biens vérifiés uniquement/i }).click();
    await page.locator('form[role="search"] button[type="submit"]').click();

    await page.waitForURL(/\/recherche\?/);
    const url = page.url();
    expect(url).toContain('modeTransaction=VENTE');
    expect(url).toContain('ville=Saly');
    expect(url).toContain('type=VILLA');
    expect(url).toContain('q=plage');
    expect(url).toContain('statutVerification=VERIFIEE');
  });

  test('Mobile menu drawer et prefers-reduced-motion', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.route('**/*fonts.g*', (r) => r.abort());
    await page.goto(`${UI_URL}/`, { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await page.waitForTimeout(400);

    await page.getByRole('button', { name: /Ouvrir le menu de navigation/i }).click();
    await expect(page.getByText(/Explorer la plateforme/i)).toBeVisible();
    await expect(page.getByText(/Parcours immobilier encadré/i)).toBeVisible();
    await expect(page.getByRole('link', { name: /Se connecter/i })).toBeVisible();
    await expect(page.getByRole('link', { name: /Créer un compte/i })).toBeVisible();

    // Verify zero massive blank void between engagement card and auth box
    const drawerLayout = await page.evaluate(() => {
      const dialog = document.querySelector('[role="dialog"]');
      const links = Array.from(dialog?.querySelectorAll('a') || []);
      const loginLink = links.find((a) => /Se connecter/i.test(a.textContent || ''));
      const nav = dialog?.querySelector('nav');
      if (!nav || !loginLink) return { gap: 9999 };
      return { gap: loginLink.getBoundingClientRect().top - nav.getBoundingClientRect().bottom };
    });
    expect(drawerLayout.gap).toBeLessThan(260);

    await page.keyboard.press('Escape');

    // Non-regression quick check on /connexion
    await page.goto(`${UI_URL}/connexion`, { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: /Connexion/i })).toBeVisible();
  });

  test('Non-collision Samba IA vs bouton Rechercher à 1366x768 et 1440x900', async ({ page }) => {
    for (const vp of [
      { width: 1366, height: 768 },
      { width: 1440, height: 900 },
    ]) {
      await page.setViewportSize(vp);
      await page.route('**/*fonts.g*', (r) => r.abort());
      await page.goto(`${UI_URL}/`, { waitUntil: 'domcontentloaded' });
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
      await page.waitForTimeout(400);

      const geometry = await page.evaluate(() => {
        const submitBtn = document.querySelector('form[role="search"] button[type="submit"]');
        const sambaBtn = document.querySelector('button[aria-label="Ouvrir l\'assistant IA E-Courtier"]');
        if (!submitBtn || !sambaBtn) return null;
        const s = submitBtn.getBoundingClientRect();
        const a = sambaBtn.getBoundingClientRect();
        const overlapX = Math.max(0, Math.min(s.right, a.right) - Math.max(s.left, a.left));
        const overlapY = Math.max(0, Math.min(s.bottom, a.bottom) - Math.max(s.top, a.top));
        return {
          submitBottom: s.bottom,
          sambaTop: a.top,
          overlapArea: overlapX * overlapY,
        };
      });

      expect(geometry).not.toBeNull();
      expect(geometry!.overlapArea).toBe(0);
    }
  });

  test('Chaîne visuelle annonces (photo réelle, 8x8 rejetée, fallbacks typés distincts, erreur -> fallback, placeholder)', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.route('**/*fonts.g*', (r) => r.abort());

    // Valid SVG photo >= 64px vs tiny 8x8 SVG swatch vs broken photo URL
    const validSvg =
      '<svg xmlns="http://www.w3.org/2000/svg" width="320" height="200"><rect width="320" height="200" fill="#2E5A4C"/></svg>';
    const tiny8x8Svg =
      '<svg xmlns="http://www.w3.org/2000/svg" width="8" height="8"><rect width="8" height="8" fill="#E96D3F"/></svg>';

    await page.route('**/mock-real-photo.svg', (route) =>
      route.fulfill({ status: 200, contentType: 'image/svg+xml', body: validSvg })
    );
    await page.route('**/mock-tiny-8x8.svg', (route) =>
      route.fulfill({ status: 200, contentType: 'image/svg+xml', body: tiny8x8Svg })
    );
    await page.route('**/mock-broken-photo.jpg', (route) => route.abort());

    const mockedListings = [
      {
        id: 'card-real',
        titre: 'Villa avec vraie photographie valide',
        type: 'VILLA',
        modeTransaction: 'VENTE',
        prix: 145000000,
        ville: 'Dakar',
        quartier: 'Almadies',
        surface: 380,
        nombrePieces: 6,
        meuble: true,
        statutVerification: 'VERIFIEE',
        photos: [{ id: 'p1', url: '/mock-real-photo.svg' }],
        estFavori: false,
      },
      {
        id: 'card-apt-a',
        titre: 'Appartement sans photo série A',
        type: 'APPARTEMENT',
        modeTransaction: 'LOCATION',
        prix: 450000,
        ville: 'Dakar',
        quartier: 'Mermoz',
        surface: 115,
        nombrePieces: 3,
        meuble: false,
        statutVerification: 'VERIFIEE',
        photos: [{ id: 'p2', url: '/mock-tiny-8x8.svg' }],
        estFavori: false,
      },
      {
        id: 'card-apt-b',
        titre: 'Appartement sans photo série B',
        type: 'APPARTEMENT',
        modeTransaction: 'LOCATION',
        prix: 520000,
        ville: 'Dakar',
        quartier: 'Plateau',
        surface: 130,
        nombrePieces: 4,
        meuble: true,
        statutVerification: 'VERIFIEE',
        photos: [],
        estFavori: false,
      },
      {
        id: 'card-villa-broken',
        titre: 'Villa image distante en erreur',
        type: 'VILLA',
        modeTransaction: 'LOCATION',
        prix: 900000,
        ville: 'Saly',
        quartier: 'Portudal',
        surface: 260,
        nombrePieces: 5,
        meuble: true,
        statutVerification: 'VERIFIEE',
        photos: [{ id: 'p3', url: '/mock-broken-photo.jpg' }],
        estFavori: false,
      },
      {
        id: 'card-land',
        titre: 'Parcelle viabilisée sans photo',
        type: 'TERRAIN',
        modeTransaction: 'VENTE',
        prix: 38000000,
        ville: 'Diamniadio',
        quartier: 'Pôle Urbain',
        surface: 400,
        nombrePieces: 0,
        meuble: false,
        statutVerification: 'VERIFIEE',
        photos: [],
        estFavori: false,
      },
      {
        id: 'card-unknown',
        titre: 'Actif patrimonial atypique sans photo',
        type: 'ENTREPOT_INCONNU',
        modeTransaction: 'VENTE',
        prix: 78000000,
        ville: 'Thiès',
        quartier: 'Dixième',
        surface: 500,
        nombrePieces: 2,
        meuble: false,
        statutVerification: 'VERIFIEE',
        photos: [],
        estFavori: false,
      },
    ];

    await page.route('**/api/v1/annonces*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: mockedListings,
          page: 0,
          size: 6,
          totalElements: mockedListings.length,
          totalPages: 1,
          last: true,
        }),
      })
    );

    await page.goto(`${UI_URL}/`, { waitUntil: 'domcontentloaded' });
    const cards = page.locator('#annonces-verifiees article');
    await expect(cards).toHaveCount(6);

    for (let i = 0; i < 6; i++) {
      await cards.nth(i).scrollIntoViewIfNeeded();
      await page.waitForTimeout(150);
    }
    await page.waitForLoadState('networkidle').catch(() => {});
    await page.waitForTimeout(300);

    // 1. Real valid photo keeps its genuine src and does NOT display "Image illustrative"
    const realCard = cards.nth(0);
    await expect(realCard.locator('img')).toHaveAttribute('src', /mock-real-photo\.svg/);
    await expect(realCard.getByText('Image illustrative')).toHaveCount(0);

    // 2. Tiny 8x8 square is rejected in favor of apartment fallback + badge
    const aptACard = cards.nth(1);
    await expect(aptACard.getByText('Appartement', { exact: true })).toBeVisible();
    await expect(aptACard.getByText('Image illustrative')).toBeVisible();
    const aptASrc = await aptACard.locator('img').getAttribute('src');
    expect(aptASrc).toMatch(/apartment-0\d/);
    expect(aptASrc).not.toContain('mock-tiny-8x8');

    // 3. Adjacent APPARTEMENT fallback differs from neighbor
    const aptBCard = cards.nth(2);
    await expect(aptBCard.getByText('Image illustrative')).toBeVisible();
    const aptBSrc = await aptBCard.locator('img').getAttribute('src');
    expect(aptBSrc).toMatch(/apartment-0\d/);
    expect(aptBSrc).not.toEqual(aptASrc);

    // 4. Broken remote villa image falls back to local villa illustrative image
    const villaCard = cards.nth(3);
    await expect(villaCard.getByText('Villa', { exact: true })).toBeVisible();
    await expect(villaCard.getByText('Image illustrative')).toBeVisible();
    const villaSrc = await villaCard.locator('img').getAttribute('src');
    expect(villaSrc).toMatch(/villa-0\d/);
    expect(villaSrc).not.toContain('mock-broken-photo');

    // 5. Land and Unknown type cards display category-mapped illustrative fallbacks cleanly
    const landCard = cards.nth(4);
    await expect(landCard.getByText('Terrain', { exact: true })).toBeVisible();
    await expect(landCard.getByText('Image illustrative')).toBeVisible();
    expect(await landCard.locator('img').getAttribute('src')).toMatch(/land-0\d/);

    const unknownCard = cards.nth(5);
    await expect(unknownCard.getByText('Résidence', { exact: true })).toBeVisible();
    await expect(unknownCard.getByText('Image illustrative')).toBeVisible();
    expect(await unknownCard.locator('img').getAttribute('src')).toMatch(/default-0\d/);

    // Check equal visual card media heights across row & naturalWidth >= 100 on all loaded fallbacks
    const mediaMetrics = await page.evaluate(() => {
      const boxes = Array.from(document.querySelectorAll('#annonces-verifiees article > div:first-child'));
      const imgs = Array.from(document.querySelectorAll('#annonces-verifiees article img')) as HTMLImageElement[];
      return {
        heights: boxes.map((el) => Math.round(el.getBoundingClientRect().height)),
        minNaturalWidth: Math.min(...imgs.map((img) => img.naturalWidth)),
      };
    });
    expect(Math.max(...mediaMetrics.heights) - Math.min(...mediaMetrics.heights)).toBeLessThanOrEqual(2);
    expect(mediaMetrics.minNaturalWidth).toBeGreaterThanOrEqual(100);

    // 6. Verify clean mobile 390x844 rendering of fallback cards without horizontal overflow
    await page.setViewportSize({ width: 390, height: 844 });
    await page.waitForTimeout(200);
    const mobileOverflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth
    );
    expect(mobileOverflow).toBeLessThanOrEqual(1);
    await expect(cards.nth(1).getByText('Image illustrative')).toBeVisible();

    // 7. Test Level 3 graphic placeholder when fallback image requests fail
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.route(/\/(apartment|villa|house|land|commercial|default)-\d+.*\.webp/i, (route) => {
      if (route.request().resourceType() === 'image') {
        return route.abort();
      }
      return route.continue();
    });
    await page.reload({ waitUntil: 'domcontentloaded' });
    const placeholderSection = page.locator('#annonces-verifiees');
    await placeholderSection.scrollIntoViewIfNeeded();
    await page.waitForTimeout(600);
    await expect(page.getByText('Visuel non disponible')).toHaveCount(5);
  });
});
