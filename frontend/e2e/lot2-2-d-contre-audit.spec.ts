import { test, expect, type Page } from '@playwright/test';

const UI_URL = process.env.E2E_UI_URL || 'http://127.0.0.1:3000';

const MANDATORY_7_VIEWPORTS = [
  { name: '320x568', width: 320, height: 568 },
  { name: '390x844', width: 390, height: 844 },
  { name: '768x1024', width: 768, height: 1024 },
  { name: '1024x768', width: 1024, height: 768 },
  { name: '1280x720', width: 1280, height: 720 },
  { name: '1366x768', width: 1366, height: 768 },
  { name: '1440x900', width: 1440, height: 900 },
] as const;

const PNG_80X80_BASE64 =
  'iVBORw0KGgoAAAANSUhEUgAAAFAAAABQCAYAAACOEfKtAAAAaklEQVR42u3RAQ0AAAjDMO5fNCCDkC0gdd83cQghhBBCCCGEEEIIIYQQQgghhBBCCCGEEEIIIYQQQgghhBBCCCGEEEIIIYQQQgghhBBCCCGEEEIIIYQQQgghhBBCCCGEEEIIIfS3BwAAAP//AwB9yQBBiY0c0gAAAABJRU5ErkJggg==';

const AUDIT_SEARCH_ITEMS = [
  {
    id: '22d10001-0000-0000-0000-000000000001',
    titre: 'Villa Piscine Almadies Standing 2.2.D',
    description: 'Villa haut standing avec piscine et jardin.',
    type: 'VILLA' as const,
    modeTransaction: 'VENTE' as const,
    prix: 95_000_000,
    superficie: 420,
    nombrePieces: 6,
    quartier: 'Almadies',
    ville: 'Dakar',
    latitude: 14.745,
    longitude: -17.515,
    meuble: true,
    animauxAcceptes: false,
    statutVerification: 'VERIFIEE' as const,
    statutPublication: 'DISPONIBLE' as const,
    dateCreation: '2026-11-19T10:00:00Z',
    auteurRole: 'AGENT' as const,
    auteur: { id: 'ag-22d', prenom: 'Amina', nom: 'Ndiaye', role: 'AGENT', email: 'a@ecourtier.sn', telephone: '+221770000001' },
    photos: [{ id: 'p-1', url: 'https://mock-22d-audit.ecourtier.sn/p1.png', ordre: 0 }],
    equipements: ['PISCINE'],
    proximites: ['PLAGE'],
    estFavori: false,
  },
  {
    id: '22d10002-0000-0000-0000-000000000002',
    titre: 'Appartement F4 Vue Mer Mermoz Corniche',
    description: 'Bel appartement spacieux vue dégagée et balcon.',
    type: 'APPARTEMENT' as const,
    modeTransaction: 'LOCATION' as const,
    prix: 450_000,
    superficie: 135,
    nombrePieces: 4,
    quartier: 'Mermoz',
    ville: 'Dakar',
    latitude: 14.708,
    longitude: -17.472,
    meuble: true,
    animauxAcceptes: true,
    statutVerification: 'VERIFIEE' as const,
    statutPublication: 'DISPONIBLE' as const,
    dateCreation: '2026-11-18T10:00:00Z',
    auteurRole: 'AGENT' as const,
    auteur: { id: 'ag-22d', prenom: 'Amina', nom: 'Ndiaye', role: 'AGENT', email: 'a@ecourtier.sn', telephone: '+221770000001' },
    photos: [{ id: 'p-2', url: 'https://mock-22d-audit.ecourtier.sn/p2.png', ordre: 0 }],
    equipements: ['WIFI', 'CLIMATISATION'],
    proximites: ['ECOLE'],
    estFavori: false,
  },
];

async function setupAuditMocks(
  page: Page,
  options?: {
    detailListing?: Record<string, unknown>;
    authUser?: { id: string; role: 'LOCATAIRE' | 'PROPRIETAIRE'; prenom: string; nom: string };
    visits?: unknown[];
    transactions?: unknown[];
  }
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
  await page.route('**/mock-22d-audit.ecourtier.sn/**', (r) =>
    r.fulfill({ status: 200, contentType: 'image/png', body: Buffer.from(PNG_80X80_BASE64, 'base64') })
  );

  // STOMP WebSocket mock so offline broker in tests does not trigger 1008 forced logout
  await page.routeWebSocket('**/ws*', (ws) => {
    let subId = 'sub-0';
    let dest = '/topic/conversations/conv-22d-1';
    ws.onMessage((msg) => {
      const raw = typeof msg === 'string' ? msg : msg.toString('utf8');
      if (raw.startsWith('CONNECT') || raw.startsWith('STOMP')) {
        ws.send('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
      } else if (raw.startsWith('SUBSCRIBE')) {
        const idMatch = raw.match(/id:([^\n]+)/);
        const destMatch = raw.match(/destination:([^\n]+)/);
        if (idMatch) subId = idMatch[1];
        if (destMatch) dest = destMatch[1];
      } else if (raw.startsWith('SEND')) {
        const bodyPart = raw.split('\n\n')[1]?.replace(/\0$/, '') || '{}';
        const parsed = JSON.parse(bodyPart) as { contenu?: string };
        const frameBody = JSON.stringify({
          id: `msg-ws-${Date.now()}`,
          contenu: parsed.contenu || '',
          dateEnvoi: '2026-11-20T12:00:00Z',
          lu: false,
          expediteur: options?.authUser || { id: 'loc-22d-1', prenom: 'Awa', nom: 'Ndiaye' },
        });
        ws.send(`MESSAGE\nsubscription:${subId}\nmessage-id:m-ws-1\ndestination:${dest}\ncontent-type:application/json\n\n${frameBody}\0`);
      }
    });
  });

  if (options?.authUser) {
    const authPayload = {
      state: {
        utilisateur: {
          ...options.authUser,
          email: 'locataire.audit@ecourtier.sn',
          telephone: '+221770009999',
          actif: true,
        },
        accessToken: 'mock-jwt-22d',
        refreshToken: 'mock-refresh-22d',
      },
      version: 0,
    };
    await page.addInitScript((json) => {
      sessionStorage.setItem('ecourtier-auth', json);
    }, JSON.stringify(authPayload));
  }

  await page.route('**/api/v1/auth/rafraichir', (r) =>
    r.fulfill({ status: 401, contentType: 'application/json', body: '{}' })
  );
  await page.route('**/api/v1/conversations/non-lus/count', (r) =>
    r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ totalNonLus: 0 }) })
  );
  await page.route('**/api/v1/utilisateurs/moi', (r) =>
    r.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        options?.authUser
          ? { ...options.authUser, email: 'locataire.audit@ecourtier.sn', telephone: '+221770009999', actif: true }
          : {}
      ),
    })
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
  await page.route('**/api/v1/references', (r) =>
    r.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        typesBien: [
          { valeur: 'APPARTEMENT', libelle: 'Appartement' },
          { valeur: 'VILLA', libelle: 'Villa' },
          { valeur: 'STUDIO', libelle: 'Studio' },
          { valeur: 'CHAMBRE', libelle: 'Chambre' },
        ],
        proximites: [
          { valeur: 'ECOLE', libelle: 'École' },
          { valeur: 'PLAGE', libelle: 'Plage / Mer' },
          { valeur: 'COMMERCE', libelle: 'Commerces' },
        ],
      }),
    })
  );
  await page.route('**/api/v1/annonces/quartiers', (r) =>
    r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(['Almadies', 'Mermoz']) })
  );
  await page.route('**/api/v1/annonces?*', (r) => {
    const params = new URL(r.request().url()).searchParams;
    const pageNum = Number(params.get('page') || '0');
    return r.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: AUDIT_SEARCH_ITEMS,
        totalElements: 16,
        totalPages: 2,
        number: pageNum,
        size: 12,
        first: pageNum === 0,
        last: pageNum >= 1,
      }),
    });
  });

  if (options?.detailListing) {
    const dId = String(options.detailListing.id);
    await page.route(`**/api/v1/annonces/${dId}`, (r) =>
      r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(options.detailListing) })
    );
    await page.route(`**/api/v1/annonces/${dId}/avis`, (r) =>
      r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ avis: [], nombreAvis: 0, noteMoyenne: null }) })
    );
  }
  await page.route('**/api/v1/utilisateurs/*/reputation', (r) =>
    r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ nombreAvis: 0, noteMoyenne: null }) })
  );
  await page.route('**/api/v1/rendez-vous/mes-rendez-vous', (r) =>
    r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(options?.visits ?? []) })
  );
  await page.route('**/api/v1/transactions/mes-transactions', (r) =>
    r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(options?.transactions ?? []) })
  );
}

test.describe('Lot 2.2.D — Contre-Audit Global & Matrice 7 Écrans (/recherche & /annonce/:id)', () => {
  test.afterEach(async ({ page }) => {
    await page.unrouteAll({ behavior: 'ignoreErrors' });
  });

  test('1. /recherche : Matrice 7 viewports, zéro collision Leaflet/Samba/Drawer, hauteur carte, popup CTA & reset page=0', async ({
    page,
  }) => {
    await setupAuditMocks(page);

    for (const vp of MANDATORY_7_VIEWPORTS) {
      await page.setViewportSize({ width: vp.width, height: vp.height });
      await page.goto(`${UI_URL}/recherche?modeTransaction=LOCATION&q=Dakar`, { waitUntil: 'domcontentloaded' });
      await expect(page.getByRole('heading', { level: 1 })).toContainText('Logements Disponibles (16)');

      const m = await page.evaluate(() => {
        const rect = (el: Element | null) => el?.getBoundingClientRect() ?? null;
        const overlap = (a: DOMRect | null, b: DOMRect | null) => {
          if (!a || !b || a.width === 0 || b.width === 0) return 0;
          const x = Math.max(0, Math.min(a.right, b.right) - Math.max(a.left, b.left));
          const y = Math.max(0, Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top));
          return Math.round(x * y);
        };
        const samba = rect(document.querySelector('button[aria-label="Ouvrir l\'assistant IA E-Courtier"]'));
        const badge = rect(document.querySelector('[data-testid="map-page-count-badge"]'));
        const zoom = rect(document.querySelector('.leaflet-control-zoom'));
        const attrib = rect(document.querySelector('.leaflet-control-attribution'));
        const map = rect(document.querySelector('.leaflet-container'));
        return {
          overflowX: document.documentElement.scrollWidth - document.documentElement.clientWidth,
          verticalDelta: document.documentElement.scrollHeight - window.innerHeight,
          footerCount: document.querySelectorAll('footer').length,
          mapH: map?.height ?? 0,
          sambaVsBadge: overlap(samba, badge),
          sambaVsZoom: overlap(samba, zoom),
          sambaVsAttrib: overlap(samba, attrib),
          badgeVsZoom: overlap(badge, zoom),
        };
      });

      expect(m.overflowX, `${vp.name} overflowX`).toBeLessThanOrEqual(0);
      expect(m.verticalDelta, `${vp.name} split-screen height delta`).toBe(0);
      expect(m.footerCount, `${vp.name} no footer`).toBe(0);
      expect(m.sambaVsBadge, `${vp.name} samba vs mapBadge`).toBe(0);
      expect(m.sambaVsZoom, `${vp.name} samba vs zoom`).toBe(0);
      expect(m.sambaVsAttrib, `${vp.name} samba vs attrib`).toBe(0);
      expect(m.badgeVsZoom, `${vp.name} mapBadge vs zoom`).toBe(0);
      if (vp.width >= 768) {
        expect(m.mapH, `${vp.name} split-screen map height`).toBeGreaterThan(vp.height * 0.8);
      }

      if (vp.width < 768) {
        await page.getByRole('button', { name: 'Carte' }).click();
        await expect(page.getByTestId('mobile-map-active-filters')).toBeVisible();
        const mobileMap = await page.evaluate(() => {
          const map = document.querySelector('.leaflet-container')?.getBoundingClientRect();
          const strip = document.querySelector('[data-testid="mobile-map-active-filters"]')?.getBoundingClientRect();
          const badge = document.querySelector('[data-testid="map-page-count-badge"]')?.getBoundingClientRect();
          const zoom = document.querySelector('.leaflet-control-zoom')?.getBoundingClientRect();
          const overlap = (a?: DOMRect, b?: DOMRect) => {
            if (!a || !b) return 0;
            const x = Math.max(0, Math.min(a.right, b.right) - Math.max(a.left, b.left));
            const y = Math.max(0, Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top));
            return Math.round(x * y);
          };
          return {
            overflowX: document.documentElement.scrollWidth - document.documentElement.clientWidth,
            verticalDelta: document.documentElement.scrollHeight - window.innerHeight,
            mapH: map?.height ?? 0,
            badgeVsZoom: overlap(badge, zoom),
            stripVsBadge: overlap(strip, badge),
          };
        });
        expect(mobileMap.overflowX).toBeLessThanOrEqual(0);
        expect(mobileMap.verticalDelta).toBe(0);
        expect(mobileMap.mapH).toBeGreaterThan(vp.height * 0.7);
        expect(mobileMap.badgeVsZoom).toBe(0);
        expect(mobileMap.stripVsBadge).toBe(0);
      }

      // Check Drawer z-index above Samba & zero overflow at 320px and 1440px
      if (vp.width === 320 || vp.width === 1440) {
        const filterBtn =
          vp.width < 768
            ? page.getByRole('button', { name: 'Filtres', exact: true })
            : page.getByRole('button', { name: /Filtres Avancés/i });
        await filterBtn.click();
        await expect(page.getByText('Filtres de Recherche Approfondis')).toBeVisible();
        const drawerStats = await page.evaluate(() => {
          const dialog = document.querySelector('[role="dialog"]');
          const sambaContainer = document.querySelector('button[aria-label="Ouvrir l\'assistant IA E-Courtier"]')?.parentElement;
          const dialogZ = Number(dialog ? window.getComputedStyle(dialog).zIndex : 0);
          const sambaZ = Number(sambaContainer ? window.getComputedStyle(sambaContainer).zIndex : 0);
          return { dialogZ, sambaZ, overflowX: document.documentElement.scrollWidth - document.documentElement.clientWidth };
        });
        expect(drawerStats.dialogZ).toBeGreaterThan(drawerStats.sambaZ);
        expect(drawerStats.overflowX).toBeLessThanOrEqual(0);
        await page.getByRole('button', { name: 'Fermer' }).click();
      }
    }

    // Reset page=1 -> page=0 on sort, keyword search submit & Drawer toggle + Leaflet Popup CTA navigation
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto(`${UI_URL}/recherche?q=Dakar&page=1`, { waitUntil: 'domcontentloaded' });
    await expect(page.getByTestId('results-transparency-summary')).toContainText('Page 2 / 2');

    await page.getByLabel('Trier les résultats').selectOption('prix,asc');
    await expect(page).toHaveURL(/sort=prix%2Casc|sort=prix,asc/);
    await expect(page).not.toHaveURL(/page=/);
    await expect(page.getByTestId('results-transparency-summary')).toContainText('Page 1 / 2');

    // Re-navigate to page=1 and reset via q submit
    await page.goto(`${UI_URL}/recherche?q=Dakar&page=1`, { waitUntil: 'domcontentloaded' });
    await page.getByLabel('Rechercher par mot-clé').fill('Almadies');
    await page.getByRole('button', { name: 'Lancer la recherche' }).click();
    await expect(page).toHaveURL(/q=Almadies/);
    await expect(page).not.toHaveURL(/page=/);

    // Re-navigate to page=1 and reset via Drawer option
    await page.goto(`${UI_URL}/recherche?page=1`, { waitUntil: 'domcontentloaded' });
    await page.getByRole('button', { name: /Filtres Avancés/i }).click();
    await page.getByRole('button', { name: 'Meublé', exact: true }).click();
    await page.getByRole('button', { name: /Voir les résultats/i }).click();
    await expect(page).toHaveURL(/meuble=true/);
    await expect(page).not.toHaveURL(/page=/);

    // Verify Leaflet marker -> contrast popup -> "Voir la fiche" navigation
    const pin = page.locator('.leaflet-marker-icon[title="Appartement F4 Vue Mer Mermoz Corniche"]');
    await expect(pin).toBeVisible();
    await pin.click();
    const popup = page.locator('.leaflet-popup-content').filter({ hasText: 'Appartement F4 Vue Mer Mermoz Corniche' });
    await expect(popup).toBeVisible();
    const popupLink = popup.getByRole('link', { name: /Voir la fiche/i });
    await expect(popupLink).toHaveAttribute('href', `/annonce/${AUDIT_SEARCH_ITEMS[1].id}`);
  });

  test('2. /annonce/:id : Matrice 7 viewports (unicité prix, visibilité CTA, 0 collision Samba, caractéristiques absentes)', async ({
    page,
  }) => {
    const detailListing = {
      ...AUDIT_SEARCH_ITEMS[1],
      etage: 2,
      photos: Array.from({ length: 6 }, (_, i) => ({
        id: `d-ph-${i}`,
        url: `https://mock-22d-audit.ecourtier.sn/dp-${i + 1}.png`,
        ordre: i,
      })),
    };
    await setupAuditMocks(page, { detailListing });

    for (const vp of MANDATORY_7_VIEWPORTS) {
      await page.setViewportSize({ width: vp.width, height: vp.height });
      await page.goto(`${UI_URL}/annonce/${detailListing.id}`, { waitUntil: 'domcontentloaded' });
      await expect(page.getByRole('heading', { level: 1 })).toContainText(detailListing.titre);

      // Strict price uniqueness across ALL 7 viewports
      await expect(page.getByText('450 000 FCFA')).toHaveCount(1);

      const m = await page.evaluate(() => {
        const rect = (el: Element | null) => el?.getBoundingClientRect() ?? null;
        const overlap = (a: DOMRect | null, b: DOMRect | null) => {
          if (!a || !b || a.width === 0 || b.width === 0) return 0;
          const x = Math.max(0, Math.min(a.right, b.right) - Math.max(a.left, b.left));
          const y = Math.max(0, Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top));
          return Math.round(x * y);
        };
        const innerH = window.innerHeight;
        const samba = rect(document.querySelector('button[aria-label="Ouvrir l\'assistant IA E-Courtier"]'));
        const priceEl = rect(
          Array.from(document.querySelectorAll('#booking-card span')).find((s) =>
            /450[\s\u202f\u00a0]*000\s*FCFA/.test(s.textContent || '')
          ) || null
        );
        const reassuranceEl = rect(
          Array.from(document.querySelectorAll('#booking-card p')).find((p) =>
            /Caution 100%|Acompte de réservation/i.test(p.textContent || '')
          ) || null
        );
        const visitBtn = rect(
          Array.from(document.querySelectorAll('#booking-card button')).find((b) =>
            /Demander une visite/i.test(b.textContent || '')
          ) || null
        );
        const contactBtn = rect(
          Array.from(document.querySelectorAll('#booking-card button')).find((b) =>
            /Contacter le propriétaire/i.test(b.textContent || '')
          ) || null
        );
        window.scrollTo(0, document.body.scrollHeight);
        const footerLastP = rect(Array.from(document.querySelectorAll('footer p')).pop() || null);
        const sambaEnd = rect(document.querySelector('button[aria-label="Ouvrir l\'assistant IA E-Courtier"]'));
        return {
          overflowX: document.documentElement.scrollWidth - document.documentElement.clientWidth,
          priceAboveFold: Boolean(priceEl && priceEl.top >= 0 && priceEl.bottom <= innerH),
          visitAboveFold: Boolean(visitBtn && visitBtn.top >= 0 && visitBtn.bottom <= innerH),
          sambaVsPrice: overlap(samba, priceEl),
          sambaVsReassurance: overlap(samba, reassuranceEl),
          sambaVsVisit: overlap(samba, visitBtn),
          sambaVsContact: overlap(samba, contactBtn),
          sambaVsFooter: overlap(sambaEnd, footerLastP),
        };
      });

      expect(m.overflowX, `${vp.name} overflowX`).toBeLessThanOrEqual(0);
      if (vp.width >= 390) {
        expect(m.priceAboveFold, `${vp.name} price above fold`).toBe(true);
        expect(m.visitAboveFold, `${vp.name} CTA above fold`).toBe(true);
      } else {
        await expect(page.locator('#booking-card')).toBeVisible();
      }
      expect(m.sambaVsPrice, `${vp.name} samba vs price`).toBe(0);
      expect(m.sambaVsReassurance, `${vp.name} samba vs reassurance`).toBe(0);
      expect(m.sambaVsVisit, `${vp.name} samba vs visit`).toBe(0);
      expect(m.sambaVsContact, `${vp.name} samba vs contact`).toBe(0);
      expect(m.sambaVsFooter, `${vp.name} samba vs footer`).toBe(0);
    }

    // Short 1-line unverified listing at 320x568 scroll=0 must ALSO keep 0 collision with Samba IA
    const shortListing = {
      ...AUDIT_SEARCH_ITEMS[1],
      id: '22d10008-0000-0000-0000-000000000008',
      titre: 'Appartement F4 Mermoz',
      statutVerification: 'EN_ATTENTE',
      auteurRole: 'PROPRIETAIRE',
      photos: [],
    };
    await page.unrouteAll({ behavior: 'ignoreErrors' });
    await setupAuditMocks(page, { detailListing: shortListing });
    await page.setViewportSize({ width: 320, height: 568 });
    await page.goto(`${UI_URL}/annonce/${shortListing.id}`, { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { level: 1 })).toContainText(shortListing.titre);
    const shortOverlap = await page.evaluate(() => {
      const rect = (el: Element | null) => el?.getBoundingClientRect() ?? null;
      const overlap = (a: DOMRect | null, b: DOMRect | null) => {
        if (!a || !b || a.width === 0 || b.width === 0) return 0;
        const x = Math.max(0, Math.min(a.right, b.right) - Math.max(a.left, b.left));
        const y = Math.max(0, Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top));
        return Math.round(x * y);
      };
      const samba = rect(document.querySelector('button[aria-label="Ouvrir l\'assistant IA E-Courtier"]'));
      const priceEl = rect(Array.from(document.querySelectorAll('#booking-card span')).find((s) => /450[\s\u202f\u00a0]*000\s*FCFA/.test(s.textContent || '')) || null);
      const reassuranceEl = rect(Array.from(document.querySelectorAll('#booking-card p')).find((p) => /Caution 100%/i.test(p.textContent || '')) || null);
      const visitBtn = rect(Array.from(document.querySelectorAll('#booking-card button')).find((b) => /Demander une visite/i.test(b.textContent || '')) || null);
      return {
        sambaVsPrice: overlap(samba, priceEl),
        sambaVsReassurance: overlap(samba, reassuranceEl),
        sambaVsVisit: overlap(samba, visitBtn),
      };
    });
    expect(shortOverlap).toEqual({ sambaVsPrice: 0, sambaVsReassurance: 0, sambaVsVisit: 0 });

    // Missing optional attributes (superficie: null, nombrePieces: null, etage: null) -> zero N/A / undefined / m² / pièces
    const sparseListing = {
      ...AUDIT_SEARCH_ITEMS[1],
      id: '22d10009-0000-0000-0000-000000000009',
      type: 'TERRAIN',
      superficie: null,
      nombrePieces: null,
      etage: null,
      photos: [],
    };
    await page.unrouteAll({ behavior: 'ignoreErrors' });
    await setupAuditMocks(page, { detailListing: sparseListing });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(`${UI_URL}/annonce/${sparseListing.id}`, { waitUntil: 'domcontentloaded' });
    const specsGrid = page.getByTestId('specs-grid');
    await expect(specsGrid).not.toContainText(/N\/A|undefined|null|m²|pièce|Étage/i);
    await expect(specsGrid.locator('> div')).toHaveCount(2);
  });

  test('3. /annonce/:id : Parcours visite (LOCATAIRE), transactions post-visite (LOCATION & VENTE) et ChatDrawer STOMP', async ({
    page,
  }) => {
    const locationDetail = {
      ...AUDIT_SEARCH_ITEMS[1],
      id: '22d20001-0000-0000-0000-000000000001',
    };

    let visitPayload: { dateHeure?: string } | null = null;
    let locationTxCount = 0;
    let venteTxCount = 0;

    await setupAuditMocks(page, {
      detailListing: locationDetail,
      authUser: { id: 'loc-22d-1', role: 'LOCATAIRE', prenom: 'Awa', nom: 'Ndiaye' },
      visits: [],
      transactions: [],
    });
    await page.route(`**/api/v1/annonces/${locationDetail.id}/rendez-vous`, async (r) => {
      visitPayload = JSON.parse(r.request().postData() || '{}') as { dateHeure?: string };
      return r.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({ id: 'rdv-22d-0001-0000', annonceId: locationDetail.id, statut: 'DEMANDE' }),
      });
    });

    // 3a. Visit booking flow at 320x568 mobile without overflow
    await page.setViewportSize({ width: 320, height: 568 });
    await page.goto(`${UI_URL}/annonce/${locationDetail.id}`, { waitUntil: 'domcontentloaded' });
    await page.getByRole('button', { name: 'Demander une visite du bien' }).click();
    await expect(page.getByText('Planifier une Visite du Logement')).toBeVisible();
    const modalOverflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth
    );
    expect(modalOverflow).toBeLessThanOrEqual(0);

    await page.locator('input[type="date"]').fill('2026-12-15');
    await page.locator('input[type="time"]').fill('14:30');
    await page.getByRole('button', { name: 'Confirmer la demande de visite' }).click();
    await expect(page.getByText('Planifier une Visite du Logement')).toBeHidden();
    expect(visitPayload?.dateHeure).toBe('2026-12-15T14:30:00');

    // 3b. ChatDrawer STOMP interactive flow at 320x568 (history + live send + close)
    await page.route(`**/api/v1/annonces/${locationDetail.id}/conversations`, (r) =>
      r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'conv-22d-1',
          annonceId: locationDetail.id,
          annonceTitre: locationDetail.titre,
          locataire: { id: 'loc-22d-1', prenom: 'Awa', nom: 'Ndiaye' },
          proprietaire: locationDetail.auteur,
        }),
      })
    );
    await page.route(/\/api\/v1\/conversations\/conv-22d-1\/messages/, (r) =>
      r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 'msg-22d-1',
            contenu: 'Bonjour, ce bien est disponible pour une visite.',
            dateEnvoi: '2026-11-20T10:00:00Z',
            lu: true,
            expediteur: locationDetail.auteur,
          },
        ]),
      })
    );
    await page.route(/\/api\/v1\/conversations\/conv-22d-1\/marquer-lu/, (r) =>
      r.fulfill({ status: 200, contentType: 'application/json', body: '{}' })
    );

    // Scroll to bottom before opening ChatDrawer to prove portal viewport fixation above Footer & Samba IA
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
    await page.getByRole('button', { name: /Contacter le propriétaire/i }).click();
    await expect(page.getByText('Bonjour, ce bien est disponible pour une visite.')).toBeVisible();

    const portalStats = await page.evaluate(() => {
      const overlay = document.querySelector('[data-testid="chat-drawer-overlay"]');
      const sambaContainer = document.querySelector('button[aria-label="Ouvrir l\'assistant IA E-Courtier"]')?.parentElement;
      const input = document.querySelector('textarea[placeholder="Écrivez votre message..."]');
      const oRect = overlay?.getBoundingClientRect();
      const iRect = input?.getBoundingClientRect();
      return {
        inBody: overlay?.parentElement === document.body,
        overlayTop: oRect ? Math.round(oRect.top) : -1,
        overlayBottom: oRect ? Math.round(oRect.bottom) : -1,
        innerH: window.innerHeight,
        chatZ: Number(overlay ? window.getComputedStyle(overlay).zIndex : 0),
        sambaZ: Number(sambaContainer ? window.getComputedStyle(sambaContainer).zIndex : 0),
        inputVisibleInViewport: Boolean(iRect && iRect.top >= 0 && iRect.bottom <= window.innerHeight),
      };
    });
    expect(portalStats.inBody).toBe(true);
    expect(portalStats.overlayTop).toBe(0);
    expect(portalStats.overlayBottom).toBe(portalStats.innerH);
    expect(portalStats.chatZ).toBeGreaterThan(portalStats.sambaZ);
    expect(portalStats.inputVisibleInViewport).toBe(true);

    const sendBtn = page.getByRole('button', { name: 'Envoyer le message' });
    await expect(sendBtn).toBeDisabled();
    await page.getByPlaceholder('Écrivez votre message...').fill('Je confirme mon intérêt pour vendredi.');
    await expect(sendBtn).toBeEnabled();
    await expect(page.getByText('38 / 1000')).toBeVisible();
    await sendBtn.click();
    await expect(page.getByText('Je confirme mon intérêt pour vendredi.')).toBeVisible();
    const chatOverflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth
    );
    expect(chatOverflow).toBeLessThanOrEqual(0);
    await page.getByRole('button', { name: 'Fermer la discussion' }).click();

    // 3c. Post-visit transaction initiation on LOCATION
    await page.unrouteAll({ behavior: 'ignoreErrors' });
    await setupAuditMocks(page, {
      detailListing: locationDetail,
      authUser: { id: 'loc-22d-1', role: 'LOCATAIRE', prenom: 'Awa', nom: 'Ndiaye' },
      visits: [{ id: 'rdv-done-loc', annonceId: locationDetail.id, statut: 'EFFECTUE' }],
      transactions: [],
    });
    await page.route(`**/api/v1/annonces/${locationDetail.id}/transactions`, (r) => {
      locationTxCount++;
      return r.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'tx-loc-22d-1',
          annonceId: locationDetail.id,
          annonceTitre: locationDetail.titre,
          modeTransaction: 'LOCATION',
          annoncePrix: 450_000,
          locataireId: 'loc-22d-1',
          locataireNomComplet: 'Awa Ndiaye',
          proprietaireId: locationDetail.auteur.id,
          proprietaireNomComplet: 'Amina Ndiaye',
          montantCaution: 450_000,
          montantPremierLoyer: 450_000,
          fraisDossier: 5000,
          montantTotalSequestre: 905_000,
          statutSequestre: 'INITIALISE',
          statutBail: 'NON_SIGNE',
          dossierActif: true,
          dateCreation: '2026-11-20T11:00:00Z',
        }),
      });
    });

    await page.setViewportSize({ width: 1280, height: 720 });
    await page.goto(`${UI_URL}/annonce/${locationDetail.id}`, { waitUntil: 'domcontentloaded' });
    await page.getByRole('button', { name: 'Initier la location' }).click();
    await expect.poll(() => locationTxCount).toBe(1);
    await expect(page.locator('div[role="dialog"]')).toBeVisible();
    await page.keyboard.press('Escape');

    // 3d. Post-visit booking initiation on VENTE (without Wave / bail / loyer on booking card)
    const venteDetail = {
      ...AUDIT_SEARCH_ITEMS[0],
      id: '22d20002-0000-0000-0000-000000000002',
    };
    await page.unrouteAll({ behavior: 'ignoreErrors' });
    await setupAuditMocks(page, {
      detailListing: venteDetail,
      authUser: { id: 'loc-22d-1', role: 'LOCATAIRE', prenom: 'Awa', nom: 'Ndiaye' },
      visits: [{ id: 'rdv-done-vente', annonceId: venteDetail.id, statut: 'EFFECTUE' }],
      transactions: [],
    });
    await page.route(`**/api/v1/annonces/${venteDetail.id}/transactions`, (r) => {
      venteTxCount++;
      return r.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'tx-vente-22d-1',
          annonceId: venteDetail.id,
          annonceTitre: venteDetail.titre,
          modeTransaction: 'VENTE',
          annoncePrix: 95_000_000,
          locataireId: 'loc-22d-1',
          locataireNomComplet: 'Awa Ndiaye',
          proprietaireId: venteDetail.auteur.id,
          proprietaireNomComplet: 'Amina Ndiaye',
          montantCaution: 9_500_000,
          montantPremierLoyer: 0,
          fraisDossier: 5000,
          montantTotalSequestre: 9_505_000,
          statutSequestre: 'INITIALISE',
          statutBail: 'NON_SIGNE',
          dossierActif: true,
          dateCreation: '2026-11-20T11:00:00Z',
        }),
      });
    });

    await page.goto(`${UI_URL}/annonce/${venteDetail.id}`, { waitUntil: 'domcontentloaded' });
    await expect(page.locator('#booking-card')).toContainText('Visite effectuée avec succès');
    await expect(page.locator('#booking-card')).not.toContainText(/Loyer|Caution|bail|Wave/i);
    await page.getByRole('button', { name: "Initier la réservation d'achat" }).click();
    await expect.poll(() => venteTxCount).toBe(1);
    await expect(page.locator('div[role="dialog"]')).toBeVisible();
  });
});
