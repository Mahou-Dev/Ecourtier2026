import { test, expect, type Page } from '@playwright/test';

const UI_URL = process.env.E2E_UI_URL || 'http://127.0.0.1:3000';

// Minimal valid 80x80 solid PNG (> 64x64 threshold in PropertyVisual)
const PNG_80X80_BASE64 =
  'iVBORw0KGgoAAAANSUhEUgAAAFAAAABQCAYAAACOEfKtAAAAaklEQVR42u3RAQ0AAAjDMO5fNCCDkC0gdd83cQghhBBCCCGEEEIIIYQQQgghhBBCCCGEEEIIIYQQQgghhBBCCCGEEEIIIYQQQgghhBBCCCGEEEIIIYQQQgghhBBCCCGEEEIIIfS3BwAAAP//AwB9yQBBiY0c0gAAAABJRU5ErkJggg==';

interface DetailMock {
  id: string;
  titre: string;
  description: string;
  type: 'STUDIO' | 'APPARTEMENT' | 'VILLA' | 'CHAMBRE';
  modeTransaction: 'LOCATION' | 'VENTE';
  prix: number;
  superficie?: number | null;
  nombrePieces?: number | null;
  etage?: number | null;
  quartier: string;
  ville: string;
  latitude: number;
  longitude: number;
  meuble: boolean;
  animauxAcceptes: boolean;
  statutVerification: 'VERIFIEE' | 'EN_ATTENTE' | 'REJETEE';
  statutPublication: 'DISPONIBLE' | 'RESERVEE' | 'LOUEE' | 'VENDUE' | 'SUSPENDUE';
  dateCreation: string;
  auteurRole: 'AGENT' | 'PROPRIETAIRE';
  auteur: { id: string; prenom: string; nom: string; telephone: string; role: 'AGENT' | 'PROPRIETAIRE'; actif: boolean };
  photos: Array<{ id: string; url: string; ordre: number }>;
  equipements: string[];
  proximites: string[];
  estFavori: boolean;
}

function makeListing(overrides: Partial<DetailMock> = {}): DetailMock {
  return {
    id: '22c10001-0000-0000-0000-000000000001',
    titre: 'Appartement F4 Vue Mer Almadies',
    description: 'Bel appartement spacieux avec terrasses vue mer et gardiennage 24/7.',
    type: 'APPARTEMENT',
    modeTransaction: 'LOCATION',
    prix: 450000,
    superficie: 135,
    nombrePieces: 4,
    etage: 2,
    quartier: 'Almadies',
    ville: 'Dakar',
    latitude: 14.745,
    longitude: -17.518,
    meuble: true,
    animauxAcceptes: true,
    statutVerification: 'VERIFIEE',
    statutPublication: 'DISPONIBLE',
    dateCreation: '2026-11-18T10:00:00Z',
    auteurRole: 'AGENT',
    auteur: {
      id: 'owner-22c-1',
      prenom: 'Babacar',
      nom: 'Diouf',
      telephone: '+221771112233',
      role: 'AGENT',
      actif: true,
    },
    photos: [
      { id: 'p0', url: 'https://mock-gallery.ecourtier.sn/photo-1.png', ordre: 0 },
      { id: 'p1', url: 'https://mock-gallery.ecourtier.sn/photo-2.png', ordre: 1 },
    ],
    equipements: ['CLIMATISATION', 'WIFI', 'PARKING'],
    proximites: ['MER', 'ECOLE'],
    estFavori: false,
    ...overrides,
  };
}

async function setupDetailRouteMocks(
  page: Page,
  listing: DetailMock,
  options?: { transactions?: unknown[]; authUser?: { id: string; role: 'LOCATAIRE' | 'PROPRIETAIRE'; prenom: string; nom: string } }
) {
  await page.route('**/*fonts.g*', (r) => r.abort());
  await page.route('**/mock-gallery.ecourtier.sn/**', (r) =>
    r.fulfill({
      status: 200,
      contentType: 'image/png',
      body: Buffer.from(PNG_80X80_BASE64, 'base64'),
    })
  );

  if (options?.authUser) {
    const authPayload = {
      state: {
        utilisateur: {
          ...options.authUser,
          email: 'test@ecourtier.sn',
          telephone: '+221770000099',
          actif: true,
        },
        accessToken: 'mock-jwt-22c',
        refreshToken: 'mock-refresh-22c',
      },
      version: 0,
    };
    await page.addInitScript((json) => {
      sessionStorage.setItem('ecourtier-auth', json);
    }, JSON.stringify(authPayload));
  }

  await page.route('**/api/v1/conversations/non-lus/count', (r) =>
    r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ totalNonLus: 0 }) })
  );
  await page.route('**/api/v1/utilisateurs/moi/favoris/count', (r) =>
    r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ count: 0 }) })
  );
  await page.route('**/api/v1/utilisateurs/moi/favoris*', (r) =>
    r.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 1, first: true, last: true }),
    })
  );
  await page.route('**/api/v1/utilisateurs/moi', (r) =>
    r.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        options?.authUser
          ? { ...options.authUser, email: 'test@ecourtier.sn', telephone: '+221770000099', actif: true }
          : {}
      ),
    })
  );
  await page.route('**/api/v1/auth/rafraichir', (r) =>
    r.fulfill({ status: 401, contentType: 'application/json', body: '{}' })
  );

  await page.route(`**/api/v1/annonces/${listing.id}`, (r) =>
    r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(listing) })
  );
  await page.route(`**/api/v1/annonces/${listing.id}/avis`, (r) =>
    r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ avis: [], nombreAvis: 0, noteMoyenne: null }) })
  );
  await page.route('**/api/v1/utilisateurs/*/reputation', (r) =>
    r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ nombreAvis: 0, noteMoyenne: null }) })
  );
  await page.route('**/api/v1/rendez-vous/mes-rendez-vous', (r) =>
    r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) })
  );
  await page.route('**/api/v1/transactions/mes-transactions', (r) =>
    r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(options?.transactions ?? []) })
  );
}

test.describe('Lot 2.2.C — Fiche annonce : Prix unique, Galerie 1..10, Statuts & Cohérence visuelle', () => {
  test('1. Unicité DOM du prix, zéro doublon strict-mode, 0 overflow et 0 collision Samba IA sur 5 viewports', async ({
    page,
  }) => {
    const errors: string[] = [];
    page.on('pageerror', (err) => errors.push(err.message));
    page.on('console', (msg) => {
      if (msg.type() === 'error') errors.push(msg.text());
    });

    const listing = makeListing();
    await setupDetailRouteMocks(page, listing);

    const viewports = [
      { width: 1280, height: 720 },
      { width: 1024, height: 768 },
      { width: 768, height: 1024 },
      { width: 390, height: 844 },
      { width: 320, height: 568 },
    ];

    for (const vp of viewports) {
      await page.setViewportSize(vp);
      await page.goto(`${UI_URL}/annonce/${listing.id}`, { waitUntil: 'domcontentloaded' });
      await expect(page.locator('h1', { hasText: listing.titre })).toBeVisible();

      // Strict mode check without .first(): MUST match exact 1 element in DOM across ALL viewports!
      const priceLocator = page.getByText('450 000 FCFA');
      await expect(priceLocator).toHaveCount(1);
      await expect(priceLocator).toBeVisible();

      const metrics = await page.evaluate(() => {
        const docW = document.documentElement.scrollWidth;
        const clientW = document.documentElement.clientWidth;
        const innerH = window.innerHeight;
        const booking = document.querySelector('#booking-card')?.getBoundingClientRect();
        const priceEl = Array.from(document.querySelectorAll('#booking-card span')).find((s) =>
          /450[\s\u202f\u00a0]*000\s*FCFA/.test(s.textContent || '')
        )?.getBoundingClientRect();
        const visitEl = Array.from(document.querySelectorAll('#booking-card button')).find((b) =>
          /Demander une visite/i.test(b.textContent || '')
        )?.getBoundingClientRect();
        const contactEl = Array.from(document.querySelectorAll('#booking-card button')).find((b) =>
          /Contacter le propriétaire/i.test(b.textContent || '')
        )?.getBoundingClientRect();
        const samba = document.querySelector('button[aria-label="Ouvrir l\'assistant IA E-Courtier"]')?.getBoundingClientRect();

        const hasOverlap = (a?: DOMRect, b?: DOMRect) =>
          Boolean(a && b && !(a.right < b.left || a.left > b.right || a.bottom < b.top || a.top > b.bottom));

        const sambaBookingOverlap = hasOverlap(booking, samba);
        const sambaInteractiveOverlap =
          hasOverlap(priceEl, samba) || hasOverlap(visitEl, samba) || hasOverlap(contactEl, samba);

        window.scrollTo(0, document.body.scrollHeight);
        const footerP = Array.from(document.querySelectorAll('footer p')).pop()?.getBoundingClientRect();
        const sambaAfter = document.querySelector('button[aria-label="Ouvrir l\'assistant IA E-Courtier"]')?.getBoundingClientRect();
        const sambaFooterOverlap = hasOverlap(footerP, sambaAfter);

        return {
          overflow: docW - clientW,
          sambaBookingOverlap,
          sambaInteractiveOverlap,
          sambaFooterOverlap,
          priceAboveFold: Boolean(priceEl && priceEl.top >= 0 && priceEl.bottom <= innerH),
          ctaAboveFold: Boolean(visitEl && visitEl.top >= 0 && visitEl.bottom <= innerH),
        };
      });

      expect(metrics.overflow).toBeLessThanOrEqual(0);
      expect(metrics.sambaInteractiveOverlap).toBe(false);
      if (vp.width >= 768) {
        expect(metrics.sambaBookingOverlap).toBe(false);
      }
      if (vp.width === 1280 || vp.width === 1024 || vp.width === 390) {
        expect(metrics.priceAboveFold).toBe(true);
        expect(metrics.ctaAboveFold).toBe(true);
      }
      expect(metrics.sambaFooterOverlap).toBe(false);
    }

    expect(errors).toEqual([]);
  });

  test('2. Galerie adaptative (1, 2, 3, 4, 5, 8 et 10 photos) + fallback sans invention + Lightbox synchronisée', async ({
    page,
  }) => {
    const errors: string[] = [];
    page.on('pageerror', (err) => errors.push(err.message));

    // Check desktop balanced compositions across photo counts
    await page.setViewportSize({ width: 1280, height: 720 });

    for (const count of [1, 2, 3, 4, 5, 8, 10]) {
      const photos = Array.from({ length: count }, (_, idx) => ({
        id: `p-${idx}`,
        url: `https://mock-gallery.ecourtier.sn/photo-${idx + 1}.png`,
        ordre: idx,
      }));
      const listing = makeListing({ id: `22c20001-0000-0000-0000-${String(count).padStart(12, '0')}`, photos });
      await setupDetailRouteMocks(page, listing);
      await page.goto(`${UI_URL}/annonce/${listing.id}`, { waitUntil: 'domcontentloaded' });
      await expect(page.locator('h1', { hasText: listing.titre })).toBeVisible();

      // Secondary grid tile count matches min(4, count - 1) -> zero empty placeholder slots
      const secondaryTiles = page.locator('[data-testid^="gallery-tile-"]');
      await expect(secondaryTiles).toHaveCount(Math.min(4, count - 1));

      if (count > 5) {
        const overflowBadge = page.getByTestId('gallery-more-overlay');
        await expect(overflowBadge).toBeVisible();
        const expectedExtra = count - 5;
        await expect(overflowBadge).toContainText(`+${expectedExtra} ${expectedExtra === 1 ? 'photo' : 'photos'}`);
        await expect(overflowBadge).toContainText(`Voir les ${count} photos`);
      } else {
        await expect(page.getByTestId('gallery-more-overlay')).toHaveCount(0);
      }

      // Must NOT falsely show "Image illustrative" for genuine >64x64 photos
      await expect(page.getByTestId('property-gallery-grid')).not.toContainText('Image illustrative');
    }

    // Open Lightbox on 8-photo listing from "+3 photos" overlay -> closes cleanly
    const photos8 = Array.from({ length: 8 }, (_, idx) => ({
      id: `p8-${idx}`,
      url: `https://mock-gallery.ecourtier.sn/photo-${idx + 1}.png`,
      ordre: idx,
    }));
    const listing8 = makeListing({ id: '22c20002-0000-0000-0000-000000000008', photos: photos8 });
    await setupDetailRouteMocks(page, listing8);
    await page.goto(`${UI_URL}/annonce/${listing8.id}`, { waitUntil: 'domcontentloaded' });
    await page.getByTestId('gallery-more-overlay').click();

    await expect(page.locator('.yarl__container')).toBeVisible({ timeout: 5000 });
    await page.locator('.yarl__button[aria-label="Close"], .yarl__container button[title="Close"]').first().click();
    await expect(page.locator('.yarl__container')).toHaveCount(0);

    // Fallback when photos: [] keeps 1 illustrative image cleanly (never invents multi-photo gallery)
    const listingEmpty = makeListing({ id: '22c20003-0000-0000-0000-000000000000', photos: [] });
    await setupDetailRouteMocks(page, listingEmpty);
    await page.goto(`${UI_URL}/annonce/${listingEmpty.id}`, { waitUntil: 'domcontentloaded' });
    await expect(page.locator('[data-testid^="gallery-tile-"]')).toHaveCount(0);
    await expect(page.getByTestId('property-gallery-grid')).toContainText('Image illustrative');

    // Mobile carousel navigation + synchronization with Lightbox
    await page.setViewportSize({ width: 390, height: 844 });
    const listing6 = makeListing({
      id: '22c20004-0000-0000-0000-000000000006',
      photos: photos8.slice(0, 6),
    });
    await setupDetailRouteMocks(page, listing6);
    await page.goto(`${UI_URL}/annonce/${listing6.id}`, { waitUntil: 'domcontentloaded' });

    const counter = page.getByTestId('mobile-gallery-counter');
    await expect(counter).toHaveText('1 / 6 photos');

    await page.getByRole('button', { name: 'Photo suivante' }).click();
    await expect(counter).toHaveText('2 / 6 photos');

    await page.getByRole('button', { name: 'Photo suivante' }).click();
    await expect(counter).toHaveText('3 / 6 photos');

    // Open Lightbox -> synchronized on slide photo-3.png
    await page.getByTestId('property-gallery-grid').locator('img').first().click();
    await expect(page.locator('.yarl__container')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('.yarl__slide_current img')).toHaveAttribute('src', /photo-3\.png/);

    // Advance slide inside Lightbox -> Close -> mobile hero keeps synced to 4 / 6
    await page.locator('.yarl__navigation_next').click();
    await expect(page.locator('.yarl__slide_current img')).toHaveAttribute('src', /photo-4\.png/);
    await page.locator('.yarl__button[aria-label="Close"], .yarl__container button[title="Close"]').first().click();
    await expect(counter).toHaveText('4 / 6 photos');

    expect(errors).toEqual([]);
  });

  test('3. Statuts de publication (LOUEE, VENDUE, RESERVEE, SUSPENDUE) et préservation dossier existant', async ({
    page,
  }) => {
    const cases: Array<{ status: DetailMock['statutPublication']; badge: string }> = [
      { status: 'LOUEE', badge: 'Déjà louée' },
      { status: 'VENDUE', badge: 'Bien vendu' },
      { status: 'RESERVEE', badge: 'Réservée' },
      { status: 'SUSPENDUE', badge: 'Annonce suspendue' },
    ];

    await page.setViewportSize({ width: 1280, height: 720 });
    for (const c of cases) {
      const listing = makeListing({
        id: `22c30001-0000-0000-0000-${c.status.toLowerCase().padEnd(12, '0').slice(0, 12)}`,
        statutPublication: c.status,
        modeTransaction: c.status === 'VENDUE' ? 'VENTE' : 'LOCATION',
      });
      await setupDetailRouteMocks(page, listing);
      await page.goto(`${UI_URL}/annonce/${listing.id}`, { waitUntil: 'domcontentloaded' });

      await expect(page.getByTestId('publication-status-badge')).toHaveText(c.badge);
      await expect(page.getByTestId('booking-status-notice')).toContainText(c.badge);
      await expect(page.getByTestId('booking-unavailable-cta')).toBeDisabled();
      await expect(page.getByRole('button', { name: 'Demander une visite du bien' })).toHaveCount(0);
      await expect(page.getByRole('button', { name: /Contacter le propriétaire/i })).toBeDisabled();
    }

    // RESERVEE listing STILL grants access to involved tenant's existing active transaction
    const reservedId = '22c30002-0000-0000-0000-000000000099';
    const reservedListing = makeListing({ id: reservedId, statutPublication: 'RESERVEE' });
    const authPage = await page.context().browser()!.newPage();
    try {
      await setupDetailRouteMocks(authPage, reservedListing, {
        authUser: { id: 'loc-22c', role: 'LOCATAIRE', prenom: 'Awa', nom: 'Ndiaye' },
        transactions: [
          {
            id: 'tx-active-22c',
            annonceId: reservedId,
            statutSequestre: 'INITIALISE',
            dossierActif: true,
            montantCaution: 450000,
          },
        ],
      });
      await authPage.goto(`${UI_URL}/annonce/${reservedId}`, { waitUntil: 'domcontentloaded' });
      await expect(authPage.getByRole('button', { name: /Gérer la caution & Signer le bail/i })).toBeEnabled();
    } finally {
      await authPage.close();
    }
  });

  test('4. Cohérence VENTE vs LOCATION, absence de N/A sur etage, grille mobile équilibrée & ChatDrawer Sahel', async ({
    page,
  }) => {
    const errors: string[] = [];
    page.on('pageerror', (err) => errors.push(err.message));

    // 4a. VENTE with missing etage + auth user able to open Sahel ChatDrawer
    const venteListing = makeListing({
      id: '22c40001-0000-0000-0000-000000000001',
      modeTransaction: 'VENTE',
      prix: 85000000,
      etage: null,
      statutPublication: 'DISPONIBLE',
    });

    await setupDetailRouteMocks(page, venteListing, {
      authUser: { id: 'loc-chat-1', role: 'LOCATAIRE', prenom: 'Moussa', nom: 'Sarr' },
    });
    await page.route(`**/api/v1/annonces/${venteListing.id}/conversations`, (r) =>
      r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'conv-22c-1',
          annonceId: venteListing.id,
          annonceTitre: venteListing.titre,
          locataire: { id: 'loc-chat-1', prenom: 'Moussa', nom: 'Sarr' },
          proprietaire: { id: 'owner-22c-1', prenom: 'Babacar', nom: 'Diouf' },
        }),
      })
    );
    await page.route('**/api/v1/conversations/conv-22c-1/messages*', (r) =>
      r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) })
    );
    await page.route('**/api/v1/conversations/conv-22c-1/marquer-lu', (r) =>
      r.fulfill({ status: 200, contentType: 'application/json', body: '{}' })
    );

    await page.setViewportSize({ width: 1280, height: 720 });
    await page.goto(`${UI_URL}/annonce/${venteListing.id}`, { waitUntil: 'domcontentloaded' });

    const bookingCard = page.locator('#booking-card');
    await expect(bookingCard).toContainText('Prix de Vente');
    await expect(bookingCard).toContainText('85 000 000 FCFA');
    await expect(bookingCard).toContainText('Acompte de réservation sécurisé sous séquestre');
    await expect(bookingCard).not.toContainText(/Loyer|Caution|bail|Wave|✔/i);

    // etage absent -> omitted completely (no N/A!)
    const specsGrid = page.getByTestId('specs-grid');
    await expect(specsGrid).not.toContainText('N/A');
    await expect(specsGrid).not.toContainText('Étage');
    await expect(specsGrid).toContainText('4 pièces');

    // Open Sahel ChatDrawer cleanly
    await page.getByRole('button', { name: /Contacter le propriétaire/i }).click();
    await expect(page.getByText('Démarrez la conversation')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Envoyer le message' })).toBeDisabled();
    await page.getByRole('button', { name: 'Fermer la discussion' }).click();

    // 4b. Mobile 390px with etage: 0 -> 5 tiles, 5th tile spans full width (col-span-2) cleanly -> no orphan hole
    await page.setViewportSize({ width: 390, height: 844 });
    const rdcListing = makeListing({
      id: '22c40002-0000-0000-0000-000000000002',
      etage: 0,
      nombrePieces: 1,
    });
    await setupDetailRouteMocks(page, rdcListing);
    await page.goto(`${UI_URL}/annonce/${rdcListing.id}`, { waitUntil: 'domcontentloaded' });

    const rdcSpecs = page.getByTestId('specs-grid');
    await expect(rdcSpecs).toContainText('RDC');
    await expect(rdcSpecs).toContainText('1 pièce');
    await expect(rdcSpecs).not.toContainText('1 pièces');
    const lastTileBox = await rdcSpecs.locator('> div').last().boundingBox();
    const gridBox = await rdcSpecs.boundingBox();
    expect((lastTileBox?.width ?? 0) > (gridBox?.width ?? 999) * 0.7).toBe(true);

    // LOCATION card keeps valid contextual assurances without raw Unicode ✔
    await expect(page.locator('#booking-card')).toContainText('Loyer Mensuel');
    await expect(page.locator('#booking-card')).not.toContainText('✔');

    expect(errors).toEqual([]);
  });
});
