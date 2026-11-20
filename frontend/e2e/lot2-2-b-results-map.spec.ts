import { test, expect, type Page } from '@playwright/test';

const UI_URL = process.env.E2E_UI_URL || 'http://127.0.0.1:3000';

interface MockAnnonce {
  id: string;
  titre: string;
  description: string;
  prix: number;
  type: string;
  modeTransaction: 'VENTE' | 'LOCATION';
  statutVerification: 'VERIFIEE' | 'EN_ATTENTE' | 'REJETEE';
  statutDisponibilite: 'DISPONIBLE';
  ville: string;
  quartier?: string;
  superficie?: number | null;
  nombrePieces?: number | null;
  meuble: boolean;
  animauxAcceptes: boolean;
  latitude: number;
  longitude: number;
  auteurRole: 'AGENT' | 'CLIENT';
  auteur: { id: string; prenom: string; nom: string; role: 'AGENT' | 'CLIENT' };
  photos: Array<{ id: string; url: string }>;
  estFavori: boolean;
  dateCreation: string;
}

const SAMPLE_LISTINGS: MockAnnonce[] = [
  {
    id: '22b00001-0000-0000-0000-000000000001',
    titre: 'Studio Lumineux Almadies Océan',
    description: 'Studio vérifié géré par agent immobilier.',
    prix: 95_000_000,
    type: 'STUDIO',
    modeTransaction: 'VENTE',
    statutVerification: 'VERIFIEE',
    statutDisponibilite: 'DISPONIBLE',
    ville: 'Dakar',
    quartier: 'Almadies',
    superficie: 48,
    nombrePieces: 1, // singular test: must render "1 pièce"
    meuble: true,
    animauxAcceptes: false,
    latitude: 14.745,
    longitude: -17.515,
    auteurRole: 'AGENT',
    auteur: { id: 'ag-1', prenom: 'Amina', nom: 'Ndiaye', role: 'AGENT' },
    photos: [],
    estFavori: false,
    dateCreation: '2026-11-18T10:00:00Z',
  },
  {
    id: '22b00002-0000-0000-0000-000000000002',
    titre: 'Appartement Plateau Résidence Horizon',
    description: 'Bel appartement 4 pièces proche corniche.',
    prix: 850_000,
    type: 'APPARTEMENT',
    modeTransaction: 'LOCATION',
    statutVerification: 'EN_ATTENTE',
    statutDisponibilite: 'DISPONIBLE',
    ville: 'Dakar',
    quartier: 'Plateau',
    superficie: 145,
    nombrePieces: 4, // plural test: must render "4 pièces"
    meuble: false,
    animauxAcceptes: true,
    latitude: 14.445,
    longitude: -16.995, // Saly / Mbour distance so pins do not cluster tightly
    auteurRole: 'CLIENT',
    auteur: { id: 'cl-2', prenom: 'Moussa', nom: 'Diop', role: 'CLIENT' },
    photos: [],
    estFavori: false,
    dateCreation: '2026-11-17T10:00:00Z',
  },
  {
    id: '22b00003-0000-0000-0000-000000000003',
    titre: 'Terrain Titre Foncier Diamniadio Pôle',
    description: 'Parcelle sans nombre de pièces ni superficie renseignée.',
    prix: 32_000_000,
    type: 'TERRAIN',
    modeTransaction: 'VENTE',
    statutVerification: 'VERIFIEE',
    statutDisponibilite: 'DISPONIBLE',
    ville: 'Thiès',
    quartier: '',
    superficie: null,
    nombrePieces: null,
    meuble: false,
    animauxAcceptes: false,
    latitude: 14.795,
    longitude: -16.925, // Thiès distinct pin
    auteurRole: 'CLIENT',
    auteur: { id: 'cl-3', prenom: 'Ibrahima', nom: 'Sarr', role: 'CLIENT' },
    photos: [],
    estFavori: false,
    dateCreation: '2026-11-16T10:00:00Z',
  },
];

async function setupSearchMocks(
  page: Page,
  options?: { listings?: MockAnnonce[]; totalElements?: number; totalPages?: number }
) {
  await page.route('**/*fonts.g*', (r) => r.abort());
  await page.route('**/tile.openstreetmap.org/**', (r) =>
    r.fulfill({
      status: 200,
      contentType: 'image/png',
      body: Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=',
        'base64'
      ),
    })
  );

  await page.route('**/api/v1/references', (r) =>
    r.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        typesAnnonce: [
          { valeur: 'STUDIO', libelle: 'Studio' },
          { valeur: 'APPARTEMENT', libelle: 'Appartement' },
          { valeur: 'VILLA', libelle: 'Villa' },
          { valeur: 'TERRAIN', libelle: 'Terrain' },
        ],
        villes: ['Dakar', 'Thiès', 'Mbour'],
        proximites: [{ valeur: 'MER', libelle: 'Proche mer' }],
        quartiersParVille: { Dakar: ['Almadies', 'Plateau'], Thiès: ['Centre'] },
      }),
    })
  );

  const items = options?.listings ?? SAMPLE_LISTINGS;

  await page.route('**/api/v1/annonces?**', async (route) => {
    const reqUrl = new URL(route.request().url());
    const onlyVerified = reqUrl.searchParams.get('statutVerification') === 'VERIFIEE';
    const q = (reqUrl.searchParams.get('q') || '').trim().toLowerCase();
    const filtered = items.filter((item) => {
      if (onlyVerified && item.statutVerification !== 'VERIFIEE') return false;
      if (q && !item.titre.toLowerCase().includes(q) && !(item.quartier || '').toLowerCase().includes(q)) {
        return false;
      }
      return true;
    });

    const pageNum = Number(reqUrl.searchParams.get('page') || '0');
    const totalElements = options?.totalElements ?? filtered.length;
    const totalPages = options?.totalPages ?? Math.max(1, Math.ceil(totalElements / 12));

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: filtered,
        totalElements,
        totalPages,
        number: pageNum,
        size: 12,
        first: pageNum === 0,
        last: pageNum >= totalPages - 1,
      }),
    });
  });

  await page.route('**/api/v1/annonces/*', async (route) => {
    const url = route.request().url();
    const match = items.find((it) => url.endsWith(`/api/v1/annonces/${it.id}`));
    if (!match) {
      return route.continue();
    }
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        ...match,
        telephoneContact: '+221770000000',
        vues: 12,
        proximites: [],
      }),
    });
  });
}

test.describe('Lot 2.2.B — Cartes résultats, Carte mobile & Interactions Leaflet', () => {
  test('1. Cartes résultats : formatage conditionnel (1 pièce, N pièces, valeurs absentes) et réduction surcharge badges', async ({
    page,
  }) => {
    await setupSearchMocks(page);
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto(`${UI_URL}/recherche`, { waitUntil: 'domcontentloaded' });

    const studioHeading = page.getByRole('heading', { name: 'Studio Lumineux Almadies Océan' });
    await expect(studioHeading).toBeVisible();

    const cards = page.locator('.overflow-y-auto > .space-y-4 > div').filter({ has: page.locator('h3') });
    await expect(cards).toHaveCount(3);

    // Card 1 (Studio): 1 pièce (singular), 48 m², Vente + Vérifié sur miniature (sans "Agent immobilier" sur la photo),
    // et "Publié par un agent immobilier" conservé en pied de carte + "Image illustrative".
    const card1 = cards.nth(0);
    await expect(card1).toContainText('1 pièce');
    await expect(card1).not.toContainText('1 pièces');
    await expect(card1).toContainText('48 m²');
    await expect(card1).toContainText('Image illustrative');
    await expect(card1).toContainText('Publié par un agent immobilier');

    const card1Thumb = card1.locator('.aspect-\\[4\\/3\\]');
    await expect(card1Thumb).toContainText('Vente');
    await expect(card1Thumb).toContainText('Vérifié');
    await expect(card1Thumb).not.toContainText('Agent immobilier');

    // Card 2 (Appartement): 4 pièces (plural)
    const card2 = cards.nth(1);
    await expect(card2).toContainText('4 pièces');
    await expect(card2).toContainText('145 m²');
    await expect(card2).toContainText('Location');

    // Card 3 (Terrain sans superficie ni pièces ni quartier): aucune valeur vide/undefined/()
    const card3 = cards.nth(2);
    await expect(card3).toContainText('TERRAIN');
    await expect(card3).toContainText('Thiès');
    await expect(card3).not.toContainText('undefined');
    await expect(card3).not.toContainText('null');
    await expect(card3).not.toContainText('()');
    await expect(card3).not.toContainText('pièce');
    await expect(card3).not.toContainText('m²');
  });

  test('2. Vue Carte mobile (390px & 320px) : filtres actifs visibles, suppression directe en mode Carte et conservation hauteur utile', async ({
    page,
  }) => {
    await setupSearchMocks(page);

    for (const vp of [
      { width: 390, height: 844 },
      { width: 320, height: 568 },
    ]) {
      await page.setViewportSize(vp);
      await page.goto(
        `${UI_URL}/recherche?modeTransaction=VENTE&statutVerification=VERIFIEE&q=Almadies`,
        { waitUntil: 'domcontentloaded' }
      );

      await expect(page.getByRole('heading', { name: 'Studio Lumineux Almadies Océan' })).toBeVisible();

      // Switch to mobile Map view
      await page.getByRole('button', { name: 'Carte' }).click();

      const mobileFiltersBar = page.getByTestId('mobile-map-active-filters');
      await expect(mobileFiltersBar).toBeVisible();
      await expect(mobileFiltersBar).toContainText('3 filtres :');
      await expect(mobileFiltersBar).toContainText('Mot-clé : Almadies');
      await expect(mobileFiltersBar).toContainText('Vente');
      await expect(mobileFiltersBar).toContainText('Vérifiées uniquement');

      // Map occupies comfortable > 55% height without document overflow
      const layoutMetrics = await page.evaluate(() => {
        const mapEl = document.querySelector('.leaflet-container') as HTMLElement | null;
        const barEl = document.querySelector('[data-testid="mobile-map-active-filters"]') as HTMLElement | null;
        return {
          docScrollW: document.documentElement.scrollWidth,
          docClientW: document.documentElement.clientWidth,
          docScrollH: document.documentElement.scrollHeight,
          innerH: window.innerHeight,
          mapH: mapEl ? Math.round(mapEl.getBoundingClientRect().height) : 0,
          barH: barEl ? Math.round(barEl.getBoundingClientRect().height) : 0,
        };
      });

      expect(layoutMetrics.docScrollW).toBeLessThanOrEqual(layoutMetrics.docClientW);
      expect(layoutMetrics.docScrollH).toBeLessThanOrEqual(layoutMetrics.innerH + 1);
      expect(layoutMetrics.barH).toBeLessThanOrEqual(48);
      expect(layoutMetrics.mapH).toBeGreaterThan(vp.height * 0.55);

      // Remove a chip directly while staying on Map mode
      await page.getByTestId('mobile-map-remove-chip-verified').click();
      await expect(page).not.toHaveURL(/statutVerification=VERIFIEE/);
      await expect(mobileFiltersBar).toContainText('2 filtres :');
      await expect(page.locator('.leaflet-container')).toBeVisible();

      // Switch back to Liste to verify sync
      await page.getByRole('button', { name: /Liste/i }).click();
      await expect(page.getByRole('heading', { name: 'Studio Lumineux Almadies Océan' })).toBeVisible();
    }
  });

  test('3. Interactions Leaflet : clic pin -> Popup contrasté avec CTA "Voir la fiche" -> synchro carte/liste & navigation clavier', async ({
    page,
  }) => {
    await setupSearchMocks(page);
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto(`${UI_URL}/recherche`, { waitUntil: 'domcontentloaded' });

    const markers = page.locator('.leaflet-marker-icon');
    await expect(markers).toHaveCount(3, { timeout: 10000 });

    // Clicking marker #0 opens popup + highlights card #0 in the left list
    const studioMarker = page.locator('.leaflet-marker-icon[title="Studio Lumineux Almadies Océan"]');
    await studioMarker.click();
    await expect(
      page.locator('.leaflet-popup-content').filter({ hasText: 'Studio Lumineux Almadies Océan' })
    ).toBeVisible();
    const cards = page.locator('.overflow-y-auto > .space-y-4 > div').filter({ has: page.locator('h3') });
    await expect(cards.nth(0)).toHaveClass(/border-terracotta-500/);

    // Select card #1 from the list -> map pans to card #1 -> open its pin via keyboard Enter
    await cards.nth(1).click();
    await expect(cards.nth(1)).toHaveClass(/border-terracotta-500/);

    const aptMarker = page.locator('.leaflet-marker-icon[title="Appartement Plateau Résidence Horizon"]');
    await expect(aptMarker).toBeVisible();
    // Wait until flyTo animation finishes so the popup renders stably within viewport
    await page.waitForTimeout(750);
    await aptMarker.focus();
    await page.keyboard.press('Enter');

    const popup = page.locator('.leaflet-popup-content').filter({ hasText: 'Appartement Plateau Résidence Horizon' });
    await expect(popup).toBeVisible();
    await expect(popup).toContainText('/mois');

    const cta = popup.getByRole('link', { name: /Voir la fiche/i });
    await expect(cta).toBeVisible();

    // Ensure Leaflet `.leaflet-container a` color rule does NOT turn text blue on terracotta button
    const textColor = await cta.evaluate((el) => window.getComputedStyle(el).color);
    expect(textColor).toBe('rgb(255, 255, 255)');

    const href = await cta.getAttribute('href');
    expect(href).toBe(`/annonce/${SAMPLE_LISTINGS[1].id}`);

    await cta.click();
    await expect(page).toHaveURL(`${UI_URL}/annonce/${SAMPLE_LISTINGS[1].id}`);
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Appartement Plateau Résidence Horizon');
  });

  test('4. Transparence pagination, hauteur split-screen exacte (5 viewports) & non-collision badge carte / Samba IA', async ({
    page,
  }) => {
    await setupSearchMocks(page, { totalElements: 27, totalPages: 3 });

    const viewports = [
      { width: 1440, height: 900 },
      { width: 1366, height: 768 },
      { width: 768, height: 1024 },
      { width: 390, height: 844 },
      { width: 320, height: 568 },
    ];

    for (const vp of viewports) {
      await page.setViewportSize(vp);
      await page.goto(`${UI_URL}/recherche`, { waitUntil: 'domcontentloaded' });

      await expect(page.getByTestId('results-transparency-summary')).toContainText(
        '3 annonces affichées sur cette page — 27 au total'
      );

      // Verify exact viewport height fit (no +1px overshoot) and zero horizontal overflow
      const m = await page.evaluate(() => ({
        docScrollW: document.documentElement.scrollWidth,
        docClientW: document.documentElement.clientWidth,
        docScrollH: document.documentElement.scrollHeight,
        innerH: window.innerHeight,
      }));
      expect(m.docScrollW).toBeLessThanOrEqual(m.docClientW);
      expect(m.docScrollH).toBe(m.innerH);

      if (vp.width >= 768) {
        const mapBadge = page.getByTestId('map-page-count-badge');
        await expect(mapBadge).toBeVisible();
        await expect(mapBadge).toContainText('3 sur cette page • 27 au total');

        // Ensure mapBadge does not intersect Samba FAB if visible
        const overlap = await page.evaluate(() => {
          const badge = document.querySelector('[data-testid="map-page-count-badge"]')?.getBoundingClientRect();
          const samba = document.querySelector('[data-testid="chatbot-fab"]')?.getBoundingClientRect();
          if (!badge || !samba) return false;
          return !(
            badge.right < samba.left ||
            badge.left > samba.right ||
            badge.bottom < samba.top ||
            badge.top > samba.bottom
          );
        });
        expect(overlap).toBe(false);
      }
    }
  });

  test('5. Non-régression 2.2.A : recherche mot-clé q synchronisée et suppression pastille', async ({ page }) => {
    await setupSearchMocks(page);
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto(`${UI_URL}/recherche?q=Plateau&page=2`, { waitUntil: 'domcontentloaded' });

    const qInput = page.getByRole('textbox', { name: 'Rechercher par mot-clé' });
    await expect(qInput).toHaveValue('Plateau');
    await expect(page.getByTestId('remove-chip-q')).toBeVisible();

    await page.getByTestId('remove-chip-q').click();
    await expect(qInput).toHaveValue('');
    await expect(page).not.toHaveURL(/q=Plateau/);
    await expect(page).not.toHaveURL(/page=/);
  });
});
