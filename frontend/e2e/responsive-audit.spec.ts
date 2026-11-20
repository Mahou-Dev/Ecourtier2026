import { test, expect, Page } from '@playwright/test';

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:3000';

const VIEWPORTS = [
  { name: '320x568', width: 320, height: 568 },
  { name: '360x800', width: 360, height: 800 },
  { name: '375x667', width: 375, height: 667 },
  { name: '390x844', width: 390, height: 844 },
  { name: '430x932', width: 430, height: 932 },
  { name: '768x1024', width: 768, height: 1024 },
  { name: '820x1180', width: 820, height: 1180 },
  { name: '1024x768', width: 1024, height: 768 },
  { name: '1280x800', width: 1280, height: 800 },
  { name: '1366x768', width: 1366, height: 768 },
  { name: '1440x900', width: 1440, height: 900 },
  { name: '1920x1080', width: 1920, height: 1080 },
] as const;

const MOCK_ADMIN_AUTH = {
  state: {
    accessToken: 'audit-mock-token',
    refreshToken: 'audit-mock-refresh',
    utilisateur: {
      id: '11111111-1111-1111-1111-111111111111',
      nom: 'Audit',
      prenom: 'Admin',
      email: 'admin@ecourtier.sn',
      telephone: '+221770000000',
      role: 'ADMIN',
      actif: true,
    },
  },
  version: 0,
};

const SAMPLE_ANNONCE = {
  id: '05e3e71d-bab0-4baf-be2b-051c8bb7b869',
  titre: 'Appartement F3 Standing Almadies Vue Mer',
  description: 'Bel appartement spacieux avec climatisation centrale et gardiennage 24/7.',
  type: 'APPARTEMENT',
  modeTransaction: 'LOCATION',
  prix: 450000,
  superficie: 115,
  nombrePieces: 4,
  etage: 2,
  quartier: 'Almadies',
  ville: 'Dakar',
  latitude: 14.745,
  longitude: -17.518,
  meuble: true,
  animauxAcceptes: false,
  statutPublication: 'DISPONIBLE',
  statutVerification: 'EN_ATTENTE',
  version: 1,
  dateCreation: '2026-11-15T10:00:00Z',
  auteur: {
    id: '22222222-2222-2222-2222-222222222222',
    prenom: 'Moussa',
    nom: 'Diop',
    email: 'moussa.diop.proprietaire@ecourtier-senegal.sn',
    telephone: '+221771112233',
    role: 'PROPRIETAIRE',
  },
  photos: [{ id: 'p1', url: '/placeholder.jpg', ordre: 0 }],
  documentsVerification: [
    {
      id: 'd1',
      typeDocument: 'TITRE_FONCIER',
      url: '/api/v1/documents/d1',
      nomFichier: 'titre_foncier_almadies.pdf',
      statut: 'EN_ATTENTE',
      dateCreation: '2026-11-15T10:00:00Z',
    },
  ],
  equipements: ['WIFI', 'CLIMATISATION', 'PARKING'],
  proximites: ['ECOLE', 'TRANSPORT'],
};

const SAMPLE_TX = {
  id: '33333333-3333-3333-3333-333333333333',
  annonceId: SAMPLE_ANNONCE.id,
  annonceTitre: SAMPLE_ANNONCE.titre,
  modeTransaction: 'LOCATION',
  locataireId: '44444444-4444-4444-4444-444444444444',
  locataireNomComplet: 'Awa Ndiaye',
  proprietaireId: SAMPLE_ANNONCE.auteur.id,
  proprietaireNomComplet: 'Moussa Diop',
  montantCaution: 450000,
  montantPremierLoyer: 450000,
  fraisDossier: 5000,
  montantTotalSequestre: 905000,
  statutSequestre: 'BLOQUE',
  statutBail: 'SIGNE_LOCATAIRE',
  statutDocumentBail: 'A_GENERER',
  referenceWave: 'WAVE-AUDIT-2026-001',
  dateCreation: '2026-11-15T11:00:00Z',
};

const SAMPLE_RDV = {
  id: '55555555-5555-5555-5555-555555555555',
  annonceId: SAMPLE_ANNONCE.id,
  annonceTitre: SAMPLE_ANNONCE.titre,
  demandeurNomComplet: 'Awa Ndiaye',
  demandeurEmail: 'awa@ecourtier.sn',
  dateHeure: '2026-12-01T10:30:00Z',
  statut: 'CONFIRME',
};

const ROUTES = [
  { key: 'home', path: '/', auth: false },
  { key: 'recherche', path: '/recherche', auth: false },
  { key: 'annonce', path: `/annonce/${SAMPLE_ANNONCE.id}`, auth: false },
  { key: 'connexion', path: '/connexion', auth: false },
  { key: 'inscription', path: '/inscription', auth: false },
  { key: 'favoris', path: '/favoris', auth: true },
  { key: 'mon-compte', path: '/mon-compte', auth: true },
  { key: 'dashboard', path: '/dashboard', auth: true },
  { key: 'dashboard-messages', path: '/dashboard?tab=messages', auth: true },
  { key: 'admin-dashboard', path: '/admin/dashboard', auth: true },
  { key: 'admin-utilisateurs', path: '/admin/utilisateurs', auth: true },
  { key: 'admin-verification', path: '/admin/verification', auth: true },
  { key: 'admin-signalements', path: '/admin/signalements', auth: true },
  { key: 'admin-transactions', path: '/admin/transactions', auth: true },
] as const;

async function setupApiMocks(page: Page, auth: boolean) {
  await page.route('**/*fonts.g*', (r) => r.abort());

  if (auth) {
    await page.addInitScript((authJson) => {
      sessionStorage.setItem('ecourtier-auth', authJson);
    }, JSON.stringify(MOCK_ADMIN_AUTH));
  }

  await page.route('**/api/v1/**', async (r) => {
    const url = r.request().url();

    if (url.includes('/annonces/quartiers')) {
      return r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(['Almadies', 'Mermoz', 'Plateau', 'Nema', 'Boucotte']),
      });
    }
    if (url.includes('/references')) {
      return r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          typesBien: [
            { valeur: 'STUDIO', libelle: 'Studio' },
            { valeur: 'APPARTEMENT', libelle: 'Appartement' },
            { valeur: 'VILLA', libelle: 'Villa' },
            { valeur: 'CHAMBRE', libelle: 'Chambre' },
          ],
          modesTransaction: [
            { valeur: 'LOCATION', libelle: 'Location' },
            { valeur: 'VENTE', libelle: 'Vente' },
          ],
          equipements: [
            { valeur: 'WIFI', libelle: 'Wi-Fi' },
            { valeur: 'CLIMATISATION', libelle: 'Climatisation' },
            { valeur: 'PARKING', libelle: 'Parking' },
          ],
          proximites: [
            { valeur: 'ECOLE', libelle: 'École' },
            { valeur: 'MARCHE', libelle: 'Marché' },
            { valeur: 'HOPITAL', libelle: 'Hôpital / Santé' },
            { valeur: 'TRANSPORT', libelle: 'Transports (BRT/Bus)' },
          ],
          villesQuartiers: {
            Dakar: ['Almadies', 'Mermoz', 'Plateau'],
            Ziguinchor: ['Nema', 'Boucotte'],
          },
        }),
      });
    }
    if (url.includes('/utilisateurs/moi/favoris')) {
      return r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [SAMPLE_ANNONCE],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          first: true,
          last: true,
        }),
      });
    }
    if (url.includes('/conversations/non-lus/compteur')) {
      return r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(2) });
    }
    if (url.includes('/conversations') && !url.includes('/messages')) {
      return r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 'conv-1',
            annonceId: SAMPLE_ANNONCE.id,
            annonceTitre: SAMPLE_ANNONCE.titre,
            locataire: { id: '4444', prenom: 'Awa', nom: 'Ndiaye' },
            proprietaire: MOCK_ADMIN_AUTH.state.utilisateur,
            nbMessagesNonLus: 1,
            dernierMessage: {
              contenu: 'Bonjour, est-ce disponible ?',
              dateEnvoi: '2026-11-15T12:00:00Z',
            },
          },
        ]),
      });
    }
    if (url.includes('/conversations/') && url.includes('/messages')) {
      return r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 'm1',
            contenu: 'Bonjour, le bien est disponible ?',
            dateEnvoi: '2026-11-15T12:00:00Z',
            lu: true,
            expediteur: { id: '4444', prenom: 'Awa', nom: 'Ndiaye' },
          },
        ]),
      });
    }
    if (url.includes('/rendez-vous/mes-rendez-vous')) {
      return r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([SAMPLE_RDV]) });
    }
    if (url.includes('/transactions/mes-transactions')) {
      return r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([SAMPLE_TX]) });
    }
    if (url.includes('/notifications-sms/mes-sms')) {
      return r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0, number: 0, first: true, last: true }),
      });
    }
    if (url.includes('/admin/statistiques')) {
      return r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          utilisateurs: {
            nouvellesInscriptions: [
              { date: '2026-11-10', valeur: 4 },
              { date: '2026-11-12', valeur: 7 },
            ],
          },
          annonces: {
            totalParStatutPublication: { DISPONIBLE: 12, LOUEE: 3 },
            totalParStatutVerification: { VERIFIEE: 10, EN_ATTENTE: 2 },
            repartitionParVilleQuartier: [{ ville: 'Dakar', quartier: 'Almadies', total: 5 }],
            tempsMoyenDecisionMinutes: 18,
          },
          activiteCommerciale: {
            nombreRdvParStatut: { EFFECTUE: 8, CONFIRME: 4, DEMANDE: 2 },
            nombreTransactionsInitiees: 5,
            nombreBauxSignesComplets: 3,
            tauxConversionRdvToTransaction: 62.5,
            tauxConversionTransactionToBail: 60,
            montantTotalSequestreBloque: 2715000,
            montantVerseProprietaires: 1800000,
            fraisDossierConserves: 25000,
          },
          moderation: { signalementsParMotif: { FAUSSE_ANNONCE: 2 }, noteMoyenneGlobale: 4.7 },
          assistantIA: { nombreConversations: 14, tauxConversationsShortlistNonVide: 78 },
        }),
      });
    }
    if (url.includes('/admin/utilisateurs')) {
      return r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            MOCK_ADMIN_AUTH.state.utilisateur,
            { ...SAMPLE_ANNONCE.auteur, actif: true, dateCreation: '2026-11-15T10:00:00Z' },
          ],
          totalElements: 2,
          totalPages: 1,
          number: 0,
          first: true,
          last: true,
        }),
      });
    }
    if (url.includes('/admin/annonces/en-attente-verification') || url.includes('/admin/annonces?')) {
      return r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [SAMPLE_ANNONCE],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          first: true,
          last: true,
        }),
      });
    }
    if (url.includes('/admin/signalements')) {
      return r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            {
              id: 'sig-1',
              annonceId: SAMPLE_ANNONCE.id,
              annonceTitre: SAMPLE_ANNONCE.titre,
              motif: 'PRIX_TROMPEUR',
              description: 'Annonce suspecte test responsive',
              statut: 'EN_ATTENTE',
              auteurEmail: 'loc@ecourtier.sn',
              dateCreation: '2026-11-15T10:00:00Z',
            },
          ],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          first: true,
          last: true,
        }),
      });
    }
    if (url.includes('/admin/transactions')) {
      return r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [SAMPLE_TX],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          first: true,
          last: true,
        }),
      });
    }
    if (url.includes('/annonces/mes-annonces')) {
      return r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [SAMPLE_ANNONCE],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          first: true,
          last: true,
        }),
      });
    }
    if (url.includes('/annonces/') && url.includes('/avis')) {
      return r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          noteMoyenne: 4.8,
          nombreAvis: 1,
          avis: [
            {
              id: 'av-1',
              note: 5,
              commentaire: 'Très propre et sécurisé',
              auteurPrenom: 'Awa',
              auteurNom: 'Ndiaye',
              dateCreation: '2026-11-15T10:00:00Z',
            },
          ],
        }),
      });
    }
    if (url.includes('/utilisateurs/') && url.includes('/reputation')) {
      return r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ noteMoyenne: 4.8, nombreAvis: 3 }) });
    }
    if (url.includes(`/annonces/${SAMPLE_ANNONCE.id}`) && !url.includes('?')) {
      return r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(SAMPLE_ANNONCE) });
    }
    if (url.includes('/annonces?')) {
      return r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [SAMPLE_ANNONCE],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          first: true,
          last: true,
        }),
      });
    }
    return r.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
  });
}

async function assertNoLayoutOverflow(page: Page) {
  return page.evaluate(() => {
    const docClientW = document.documentElement.clientWidth;
    const docScrollW = document.documentElement.scrollWidth;
    const bodyScrollW = document.body.scrollWidth;
    const header = document.querySelector('header');
    const headerScrollW = header ? header.scrollWidth : 0;
    const headerClientW = header ? header.clientWidth : 0;
    const main = document.querySelector('main');
    const mainScrollW = main ? main.scrollWidth : 0;
    const mainClientW = main ? main.clientWidth : 0;

    const badElements: string[] = [];
    for (const el of Array.from(document.querySelectorAll('body *'))) {
      if (el.closest('.leaflet-pane, .leaflet-control-container')) continue;
      const style = window.getComputedStyle(el);
      if (style.display === 'none' || style.visibility === 'hidden') continue;

      let clippedByAncestor = false;
      let parent = el.parentElement;
      while (parent && parent !== document.body) {
        const pStyle = window.getComputedStyle(parent);
        if (
          pStyle.overflowX === 'hidden' ||
          pStyle.overflowX === 'auto' ||
          pStyle.overflowX === 'scroll' ||
          pStyle.overflow === 'hidden'
        ) {
          clippedByAncestor = true;
          break;
        }
        parent = parent.parentElement;
      }
      if (clippedByAncestor) continue;

      const rect = el.getBoundingClientRect();
      if (rect.width > 0 && rect.height > 0 && rect.right > docClientW + 2) {
        badElements.push(`${el.tagName}.${String(el.className || '').split(' ').slice(0, 3).join('.')}(right=${Math.round(rect.right)}/${docClientW})`);
        if (badElements.length >= 5) break;
      }
    }

    return {
      docClientW,
      docScrollW,
      bodyScrollW,
      headerOverflow: headerScrollW > headerClientW + 1 ? `${headerScrollW}>${headerClientW}` : null,
      mainOverflow: mainScrollW > mainClientW + 1 ? `${mainScrollW}>${mainClientW}` : null,
      badElements,
    };
  });
}

test.describe('Responsive Design Matrix — Toutes Routes × 12 Écrans', () => {
  test.setTimeout(120_000);

  for (const route of ROUTES) {
    test(`Route ${route.key} (${route.path}) sans débordement sur les 12 viewports`, async ({ page }) => {
      await setupApiMocks(page, route.auth);
      await page.goto(`${BASE_URL}${route.path}`, { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(500);

      for (const vp of VIEWPORTS) {
        await page.setViewportSize({ width: vp.width, height: vp.height });
        await page.waitForTimeout(150);

        const metrics = await assertNoLayoutOverflow(page);
        expect.soft(metrics.docScrollW, `${route.key} @ ${vp.name} document scrollWidth`).toBeLessThanOrEqual(metrics.docClientW + 1);
        expect.soft(metrics.bodyScrollW, `${route.key} @ ${vp.name} body scrollWidth`).toBeLessThanOrEqual(metrics.docClientW + 1);
        expect.soft(metrics.headerOverflow, `${route.key} @ ${vp.name} header overflow`).toBeNull();
        expect.soft(metrics.mainOverflow, `${route.key} @ ${vp.name} main overflow`).toBeNull();
        expect.soft(metrics.badElements, `${route.key} @ ${vp.name} overflowing elements`).toEqual([]);
      }
    });
  }
});

test.describe('Interactions Responsive Critiques (Mobile, Tablette & Desktop)', () => {
  test.setTimeout(60_000);

  test('Header: Drawer mobile/tablette (<1280px) et nav horizontale préservée (>=1280px)', async ({ page }) => {
    await setupApiMocks(page, true);
    await page.setViewportSize({ width: 320, height: 568 });
    await page.goto(`${BASE_URL}/`, { waitUntil: 'domcontentloaded' });

    const desktopNav = page.getByRole('navigation', { name: 'Navigation principale' });
    await expect(desktopNav).toBeHidden();

    const menuButton = page.getByRole('button', { name: 'Ouvrir le menu de navigation' });
    await expect(menuButton).toBeVisible();
    await menuButton.click();

    const mobileNav = page.getByRole('navigation', { name: 'Menu mobile' });
    await expect(mobileNav).toBeVisible();
    await expect(mobileNav.getByRole('link', { name: 'Mes favoris' })).toBeVisible();
    await expect(mobileNav.getByRole('link', { name: 'Administration' })).toBeVisible();

    await mobileNav.getByRole('link', { name: 'Mes favoris' }).click();
    await expect(page).toHaveURL(/\/favoris/);
    await expect(mobileNav).toBeHidden();

    await page.setViewportSize({ width: 1440, height: 900 });
    await expect(desktopNav).toBeVisible();
    await expect(menuButton).toBeHidden();
  });

  test('Samba IA Widget tient intégralement dans le viewport 320x568 sans débordement', async ({ page }) => {
    await setupApiMocks(page, false);
    await page.setViewportSize({ width: 320, height: 568 });
    await page.goto(`${BASE_URL}/`, { waitUntil: 'domcontentloaded' });

    const launcher = page.getByRole('button', { name: "Ouvrir l'assistant IA E-Courtier" });
    await expect(launcher).toBeVisible();
    const launcherBox = await launcher.boundingBox();
    expect(launcherBox).not.toBeNull();
    expect(launcherBox!.x + launcherBox!.width).toBeLessThanOrEqual(320);

    await launcher.click();
    const closeBtn = page.getByRole('button', { name: "Fermer l'assistant" });
    await expect(closeBtn).toBeVisible();

    const headerBox = await closeBtn.boundingBox();
    expect(headerBox).not.toBeNull();
    expect(headerBox!.y).toBeGreaterThanOrEqual(0);
    await closeBtn.click();
  });

  test('Recherche: switch Liste/Carte sur 375x667 et Drawer Filtres sans overflow', async ({ page }) => {
    await setupApiMocks(page, false);
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto(`${BASE_URL}/recherche`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(400);

    await expect(page.getByText('Appartement F3 Standing Almadies Vue Mer')).toBeVisible();

    await page.getByRole('button', { name: 'Carte' }).click();
    const leaflet = page.locator('.leaflet-container');
    await expect(leaflet).toBeVisible();
    const mapBox = await leaflet.boundingBox();
    expect(mapBox).not.toBeNull();
    expect(mapBox!.height).toBeGreaterThan(250);

    await page.waitForTimeout(200);
    await page.getByRole('button', { name: 'Filtres', exact: true }).click();
    await expect(page.getByText('Filtres de Recherche Approfondis')).toBeVisible();
    const drawerOverflow = await assertNoLayoutOverflow(page);
    expect(drawerOverflow.docScrollW).toBeLessThanOrEqual(376);
  });

  test('Dashboard Messagerie mobile: bascule Liste <-> Conversation avec bouton retour', async ({ page }) => {
    await setupApiMocks(page, true);
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto(`${BASE_URL}/dashboard?tab=messages`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(400);

    const convButton = page.getByRole('button', { name: /Awa Ndiaye/i });
    await expect(convButton).toBeVisible();
    await convButton.click();

    const backButton = page.getByRole('button', { name: 'Retour aux conversations' });
    await expect(backButton).toBeVisible();
    await expect(page.getByPlaceholder('Écrivez un message...')).toBeVisible();

    await backButton.click();
    await expect(convButton).toBeVisible();
  });

  test('Admin Transactions mobile: cartes lisibles et modale TransactionDetails sans overflow sur 375x667', async ({ page }) => {
    await setupApiMocks(page, true);
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto(`${BASE_URL}/admin/transactions`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(400);

    await expect(page.locator('table')).toBeHidden();
    const manageBtn = page.getByRole('button', { name: 'Rembourser / Gérer' }).first();
    await expect(manageBtn).toBeVisible();
    await manageBtn.click();

    await expect(page.getByText(/Dossier de Transaction/i)).toBeVisible();
    const modalMetrics = await assertNoLayoutOverflow(page);
    expect(modalMetrics.docScrollW).toBeLessThanOrEqual(376);
  });
});
