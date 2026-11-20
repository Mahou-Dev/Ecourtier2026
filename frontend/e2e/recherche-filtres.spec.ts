import { test, expect, type Page } from '@playwright/test';

/**
 * Suite F7–F13, F14, F28 — Filtres de recherche déterministes (Lot 2.2.D)
 *
 * Isolation & reproductibilité :
 * - Aucun appel au backend live ou à la base PostgreSQL locale (100 % isolé par mocks Playwright).
 * - Jeu de données contrôlé de 16 annonces (2 pages de size=12) reproduisant fidèlement
 *   le tri par défaut `dateCreation,desc` où « Belle Villa Familiale Ziguinchor Nema »
 *   (45 000 000 FCFA) figure en page 2 sans filtre et remonte en page 1 dès filtrage VENTE / VILLA / Nema.
 * - Émulation complète des filtres Spring Data (`modeTransaction`, multi `type`, `ville`, `quartier`, `q`,
 *   `budgetMin`, `budgetMax`, `superficieMin`, `nombrePieces`, `meuble`, `animauxAcceptes`,
 *   multi `proximite`, `statutVerification`, `sort`, `page`).
 */

const PNG_80X80_BASE64 =
  'iVBORw0KGgoAAAANSUhEUgAAAFAAAABQCAYAAACOEfKtAAAAaklEQVR42u3RAQ0AAAjDMO5fNCCDkC0gdd83cQghhBBCCCGEEEIIIYQQQgghhBBCCCGEEEIIIYQQQgghhBBCCCGEEEIIIYQQQgghhBBCCCGEEEIIIYQQQgghhBBCCCGEEEIIIfS3BwAAAP//AwB9yQBBiY0c0gAAAABJRU5ErkJggg==';

interface FixtureAnnonce {
  id: string;
  titre: string;
  description: string;
  type: 'STUDIO' | 'APPARTEMENT' | 'VILLA' | 'CHAMBRE';
  modeTransaction: 'LOCATION' | 'VENTE';
  prix: number;
  superficie: number;
  nombrePieces: number;
  etage?: number;
  quartier: string;
  ville: string;
  latitude: number;
  longitude: number;
  meuble: boolean;
  animauxAcceptes: boolean;
  statutVerification: 'EN_ATTENTE' | 'VERIFIEE' | 'REJETEE';
  statutPublication: 'DISPONIBLE';
  dateCreation: string;
  auteurRole: 'AGENT' | 'PROPRIETAIRE';
  auteur: { id: string; prenom: string; nom: string; role: 'AGENT' | 'PROPRIETAIRE'; email: string; telephone: string };
  photos: Array<{ id: string; url: string; ordre: number }>;
  equipements: string[];
  proximites: string[];
  estFavori: boolean;
}

const DEFAULT_AUTHORS = {
  agent: {
    id: 'rf-agent-1',
    prenom: 'Amina',
    nom: 'Ndiaye',
    role: 'AGENT' as const,
    email: 'amina@ecourtier.sn',
    telephone: '+221770001101',
  },
  owner: {
    id: 'rf-owner-1',
    prenom: 'Babacar',
    nom: 'Diouf',
    role: 'PROPRIETAIRE' as const,
    email: 'babacar@ecourtier.sn',
    telephone: '+221770001102',
  },
};

function makeFixture(
  idx: number,
  overrides: Partial<FixtureAnnonce> & Pick<FixtureAnnonce, 'titre' | 'type' | 'modeTransaction' | 'prix' | 'superficie' | 'nombrePieces' | 'quartier' | 'ville'>
): FixtureAnnonce {
  const id = `22d00000-0000-0000-0000-${String(idx).padStart(12, '0')}`;
  return {
    id,
    description: `${overrides.titre} — logement contrôlé pour validation E2E déterministe.`,
    etage: 1,
    latitude: 14.7 + idx * 0.002,
    longitude: -17.48 + idx * 0.002,
    meuble: false,
    animauxAcceptes: false,
    statutVerification: 'VERIFIEE',
    statutPublication: 'DISPONIBLE',
    dateCreation: `2026-11-${String(Math.max(1, 20 - idx)).padStart(2, '0')}T10:00:00Z`,
    auteurRole: idx % 3 === 1 ? 'AGENT' : 'PROPRIETAIRE',
    auteur: idx % 3 === 1 ? DEFAULT_AUTHORS.agent : DEFAULT_AUTHORS.owner,
    photos: [{ id: `ph-${idx}`, url: `https://mock-rf.ecourtier.sn/p-${idx}.png`, ordre: 0 }],
    equipements: ['WIFI', 'CLIMATISATION'],
    proximites: [],
    estFavori: false,
    ...overrides,
  };
}

// 16 annonces triées du plus récent (#1) au plus ancien (#16) :
// Page 1 (`page=0`, #1..#12) et Page 2 (`page=1`, #13..#16, incluant #15 à 45 000 000 FCFA).
const CONTROLLED_ANNONCES: FixtureAnnonce[] = [
  makeFixture(1, {
    titre: 'Villa Contemporaine Almadies Piscine',
    type: 'VILLA',
    modeTransaction: 'VENTE',
    prix: 65_000_000,
    superficie: 320,
    nombrePieces: 6,
    quartier: 'Almadies',
    ville: 'Dakar',
    meuble: true,
    animauxAcceptes: false,
    proximites: ['PLAGE', 'COMMERCE'],
  }),
  makeFixture(2, {
    titre: 'Appartement F4 Standing Mermoz Corniche',
    type: 'APPARTEMENT',
    modeTransaction: 'LOCATION',
    prix: 350_000,
    superficie: 115,
    nombrePieces: 4,
    quartier: 'Mermoz',
    ville: 'Dakar',
    meuble: true,
    animauxAcceptes: true,
    proximites: ['ECOLE', 'TRANSPORT'],
  }),
  makeFixture(3, {
    titre: 'Studio Cosy Plateau Centre',
    type: 'STUDIO',
    modeTransaction: 'LOCATION',
    prix: 110_000,
    superficie: 45,
    nombrePieces: 1,
    quartier: 'Plateau',
    ville: 'Dakar',
    statutVerification: 'EN_ATTENTE',
    proximites: ['MARCHE', 'TRANSPORT'],
  }),
  makeFixture(4, {
    titre: 'Appartement F3 Sacré-Cœur 3',
    type: 'APPARTEMENT',
    modeTransaction: 'LOCATION',
    prix: 250_000,
    superficie: 95,
    nombrePieces: 3,
    quartier: 'Sacré-Cœur',
    ville: 'Dakar',
    animauxAcceptes: true,
    proximites: ['ECOLE', 'COMMERCE'],
  }),
  makeFixture(5, {
    titre: 'Villa de Vacances Saly Niakh Niakhal',
    type: 'VILLA',
    modeTransaction: 'VENTE',
    prix: 85_000_000,
    superficie: 400,
    nombrePieces: 5,
    quartier: 'Niakh Niakhal',
    ville: 'Saly',
    meuble: true,
    animauxAcceptes: true,
    proximites: ['PLAGE'],
  }),
  makeFixture(6, {
    titre: 'Chambre Étudiante Liberté 6',
    type: 'CHAMBRE',
    modeTransaction: 'LOCATION',
    prix: 75_000,
    superficie: 22,
    nombrePieces: 1,
    quartier: 'Liberté 6',
    ville: 'Dakar',
    statutVerification: 'EN_ATTENTE',
    proximites: ['TRANSPORT'],
  }),
  makeFixture(7, {
    titre: 'Appartement Bord de Mer Saly',
    type: 'APPARTEMENT',
    modeTransaction: 'LOCATION',
    prix: 180_000,
    superficie: 80,
    nombrePieces: 2,
    quartier: 'Centre',
    ville: 'Saly',
    meuble: true,
    proximites: ['PLAGE', 'MARCHE'],
  }),
  makeFixture(8, {
    titre: 'Villa de Luxe Almadies Sea View',
    type: 'VILLA',
    modeTransaction: 'LOCATION',
    prix: 1_500_000,
    superficie: 380,
    nombrePieces: 6,
    quartier: 'Almadies',
    ville: 'Dakar',
    meuble: true,
    animauxAcceptes: true,
    proximites: ['PLAGE', 'HOPITAL'],
  }),
  makeFixture(9, {
    titre: 'Studio Étudiant Ziguinchor Nema',
    type: 'STUDIO',
    modeTransaction: 'LOCATION',
    prix: 90_000,
    superficie: 38,
    nombrePieces: 1,
    quartier: 'Nema',
    ville: 'Ziguinchor',
    proximites: ['UNIVERSITE'],
  }),
  makeFixture(10, {
    titre: 'Appartement F3 Thiès Dixième',
    type: 'APPARTEMENT',
    modeTransaction: 'LOCATION',
    prix: 160_000,
    superficie: 88,
    nombrePieces: 3,
    quartier: 'Dixième',
    ville: 'Thiès',
    statutVerification: 'EN_ATTENTE',
    proximites: ['HOPITAL'],
  }),
  makeFixture(11, {
    titre: 'Appartement Résidentiel Ngor Vente',
    type: 'APPARTEMENT',
    modeTransaction: 'VENTE',
    prix: 38_000_000,
    superficie: 140,
    nombrePieces: 4,
    quartier: 'Ngor',
    ville: 'Dakar',
    proximites: ['PLAGE'],
  }),
  makeFixture(12, {
    titre: 'Appartement F4 Fann Résidence',
    type: 'APPARTEMENT',
    modeTransaction: 'LOCATION',
    prix: 420_000,
    superficie: 130,
    nombrePieces: 4,
    quartier: 'Fann Résidence',
    ville: 'Dakar',
    meuble: true,
    animauxAcceptes: true,
    proximites: ['UNIVERSITE', 'TRANSPORT'],
  }),
  // --- PAGE 2 (`page=1`, éléments 13..16) ---
  makeFixture(13, {
    titre: 'Studio Meublé Ziguinchor Boucotte',
    type: 'STUDIO',
    modeTransaction: 'LOCATION',
    prix: 95_000,
    superficie: 40,
    nombrePieces: 2,
    quartier: 'Boucotte',
    ville: 'Ziguinchor',
    meuble: true,
    proximites: ['MARCHE'],
  }),
  makeFixture(14, {
    titre: 'Appartement Familial Parcelles Assainies',
    type: 'APPARTEMENT',
    modeTransaction: 'LOCATION',
    prix: 175_000,
    superficie: 78,
    nombrePieces: 3,
    quartier: 'Parcelles',
    ville: 'Dakar',
    animauxAcceptes: true,
    statutVerification: 'EN_ATTENTE',
    proximites: ['ECOLE'],
  }),
  makeFixture(15, {
    titre: 'Belle Villa Familiale Ziguinchor Nema',
    type: 'VILLA',
    modeTransaction: 'VENTE',
    prix: 45_000_000,
    superficie: 310,
    nombrePieces: 6,
    quartier: 'Nema',
    ville: 'Ziguinchor',
    animauxAcceptes: true,
    proximites: ['ECOLE', 'MARCHE'],
  }),
  makeFixture(16, {
    titre: 'Chambre Confort Saly Centre',
    type: 'CHAMBRE',
    modeTransaction: 'LOCATION',
    prix: 120_000,
    superficie: 28,
    nombrePieces: 1,
    quartier: 'Centre',
    ville: 'Saly',
    meuble: true,
    proximites: ['COMMERCE'],
  }),
];

async function installSearchFilterFixtures(page: Page): Promise<{ requests: string[] }> {
  const requests: string[] = [];

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
  await page.route('**/mock-rf.ecourtier.sn/**', (r) =>
    r.fulfill({ status: 200, contentType: 'image/png', body: Buffer.from(PNG_80X80_BASE64, 'base64') })
  );
  await page.route('**/api/v1/auth/rafraichir', (r) =>
    r.fulfill({ status: 401, contentType: 'application/json', body: '{}' })
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
        modesTransaction: [
          { valeur: 'LOCATION', libelle: 'Location' },
          { valeur: 'VENTE', libelle: 'Vente' },
        ],
        equipements: [
          { valeur: 'WIFI', libelle: 'Wi-Fi' },
          { valeur: 'CLIMATISATION', libelle: 'Climatisation' },
        ],
        proximites: [
          { valeur: 'ECOLE', libelle: 'École' },
          { valeur: 'MARCHE', libelle: 'Marché' },
          { valeur: 'HOPITAL', libelle: 'Hôpital / Santé' },
          { valeur: 'TRANSPORT', libelle: 'Transports (BRT/Bus)' },
          { valeur: 'PLAGE', libelle: 'Plage / Mer' },
          { valeur: 'COMMERCE', libelle: 'Commerces' },
          { valeur: 'UNIVERSITE', libelle: 'Université' },
        ],
      }),
    })
  );
  await page.route('**/api/v1/annonces/quartiers', (r) =>
    r.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(['Almadies', 'Mermoz', 'Plateau', 'Sacré-Cœur', 'Nema', 'Boucotte', 'Ngor']),
    })
  );

  await page.route('**/api/v1/annonces?*', async (route) => {
    const rawUrl = route.request().url();
    requests.push(rawUrl);
    const params = new URL(rawUrl).searchParams;

    const mode = params.get('modeTransaction');
    const types = params.getAll('type');
    const ville = params.get('ville');
    const quartier = (params.get('quartier') || '').trim().toLowerCase();
    const q = (params.get('q') || '').trim().toLowerCase();
    const budgetMin = params.get('budgetMin') ? Number(params.get('budgetMin')) : undefined;
    const budgetMax = params.get('budgetMax') ? Number(params.get('budgetMax')) : undefined;
    const superficieMin = params.get('superficieMin') ? Number(params.get('superficieMin')) : undefined;
    const nombrePieces = params.get('nombrePieces') ? Number(params.get('nombrePieces')) : undefined;
    const meuble = params.get('meuble');
    const animaux = params.get('animauxAcceptes');
    const proximites = params.getAll('proximite');
    const statutVerification = params.get('statutVerification');
    const sort = params.get('sort') || 'dateCreation,desc';
    const pageNum = Math.max(0, Number(params.get('page') || '0'));
    const pageSize = Math.max(1, Number(params.get('size') || '12'));

    let filtered = CONTROLLED_ANNONCES.filter((item) => {
      if (mode && item.modeTransaction !== mode) return false;
      if (types.length > 0 && !types.includes(item.type)) return false;
      if (ville && item.ville.toLowerCase() !== ville.toLowerCase()) return false;
      if (quartier && !item.quartier.toLowerCase().includes(quartier)) return false;
      if (
        q &&
        !`${item.titre} ${item.description} ${item.quartier} ${item.ville}`.toLowerCase().includes(q)
      ) {
        return false;
      }
      if (budgetMin !== undefined && item.prix < budgetMin) return false;
      if (budgetMax !== undefined && item.prix > budgetMax) return false;
      if (superficieMin !== undefined && item.superficie < superficieMin) return false;
      if (nombrePieces !== undefined && item.nombrePieces < nombrePieces) return false;
      if (meuble === 'true' && !item.meuble) return false;
      if (meuble === 'false' && item.meuble) return false;
      if (animaux === 'true' && !item.animauxAcceptes) return false;
      if (proximites.length > 0 && !proximites.every((p) => item.proximites.includes(p))) return false;
      if (statutVerification && item.statutVerification !== statutVerification) return false;
      return true;
    });

    if (sort === 'prix,asc') {
      filtered = [...filtered].sort((a, b) => a.prix - b.prix);
    } else if (sort === 'prix,desc') {
      filtered = [...filtered].sort((a, b) => b.prix - a.prix);
    } else if (sort === 'superficie,desc') {
      filtered = [...filtered].sort((a, b) => b.superficie - a.superficie);
    }

    const totalElements = filtered.length;
    const totalPages = Math.max(1, Math.ceil(totalElements / pageSize));
    const start = pageNum * pageSize;
    const content = filtered.slice(start, start + pageSize);

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content,
        totalElements,
        totalPages,
        number: pageNum,
        size: pageSize,
        first: pageNum === 0,
        last: pageNum >= totalPages - 1,
      }),
    });
  });

  return { requests };
}

test.describe('Filtres de recherche (F7 à F13, F14, F28) — Déterministe', () => {
  test.afterEach(async ({ page }) => {
    await page.unrouteAll({ behavior: 'ignoreErrors' });
  });

  test('1. Sans filtre : Page 1 mixte LOCATION + VENTE (12/16) et villa à 45 000 000 atteinte en Page 2', async ({ page }) => {
    const { requests } = await installSearchFilterFixtures(page);

    await page.goto('/recherche');
    await page.waitForSelector('h1:has-text("Logements Disponibles (16)")');

    // Aucun paramètre restrictif dans l'URL ni dans l'appel API
    expect(page.url()).not.toContain('budgetMax=');
    expect(page.url()).not.toContain('modeTransaction=');
    expect(requests.length).toBeGreaterThanOrEqual(1);
    const page1Url = new URL(requests[requests.length - 1]);
    expect(page1Url.pathname).toBe('/api/v1/annonces');
    expect(page1Url.searchParams.get('budgetMax')).toBeNull();
    expect(page1Url.searchParams.get('modeTransaction')).toBeNull();
    expect(page1Url.searchParams.get('page')).toBe('0');
    expect(page1Url.searchParams.get('size')).toBe('12');

    // Page 1 affiche 12 annonces sur 16 avec présence simultanée Vente et Location
    await expect(page.getByTestId('results-transparency-summary')).toContainText(
      '12 annonces affichées sur cette page — 16 au total'
    );
    await expect(page.locator('span:text-is("Vente")').first()).toBeVisible();
    await expect(page.locator('span:text-is("Location")').first()).toBeVisible();
    await expect(page.locator('text=65 000 000 FCFA').first()).toBeVisible();

    // Sans confusion page 1 / absence en base : l'annonce #15 (45M) est hors page 1 et visible dès page 2
    await expect(page.locator('text=45 000 000 FCFA')).toHaveCount(0);

    const page2Response = page.waitForResponse((res) => res.url().includes('/api/v1/annonces?') && res.url().includes('page=1'));
    await page.getByRole('button', { name: 'Suivant' }).click();
    await page2Response;

    await expect(page).toHaveURL(/page=1/);
    await expect(page.getByTestId('results-transparency-summary')).toContainText(
      '4 annonces affichées sur cette page — 16 au total'
    );
    await expect(page.locator('text=45 000 000 FCFA')).toBeVisible();
    await expect(page.getByText('Belle Villa Familiale Ziguinchor Nema')).toBeVisible();
  });

  test('2. Filtre Mode Transaction : VENTE via bouton d\'action rapide', async ({ page }) => {
    const { requests } = await installSearchFilterFixtures(page);

    await page.goto('/recherche');
    await page.waitForSelector('h1:has-text("Logements Disponibles (16)")');

    const venteApiPromise = page.waitForResponse(
      (res) => res.url().includes('/api/v1/annonces?') && res.url().includes('modeTransaction=VENTE')
    );
    await page.locator('button:text-is("Vente")').first().click();
    await venteApiPromise;

    await expect(page).toHaveURL(/modeTransaction=VENTE/);
    const urlObj = new URL(requests[requests.length - 1]);
    expect(urlObj.searchParams.get('modeTransaction')).toBe('VENTE');

    // Pastille Vente, aucune carte Location résiduelle, et villa 45M visible parmi les 4 ventes
    await expect(page.locator('span:has-text("Filtres actifs :")')).toBeVisible();
    await expect(page.locator('span:text-is("Vente")').first()).toBeVisible();
    await expect(page.getByTestId('results-transparency-summary')).toContainText(
      '4 annonces affichées sur cette page — 4 au total'
    );
    await expect(page.locator('.overflow-y-auto span:text-is("Location")')).toHaveCount(0);
    await expect(page.locator('text=45 000 000 FCFA')).toBeVisible();
  });

  test('3. Filtre Type de bien : VILLA', async ({ page }) => {
    const { requests } = await installSearchFilterFixtures(page);

    await page.goto('/recherche?type=VILLA');
    await page.waitForSelector('h1:has-text("Logements Disponibles (4)")');

    expect(page.url()).toContain('type=VILLA');
    const urlObj = new URL(requests[requests.length - 1]);
    expect(urlObj.searchParams.getAll('type')).toContain('VILLA');

    await expect(page.locator('span:text-is("Villa")').first()).toBeVisible();
    await expect(page.locator('span:text-is("VILLA")').first()).toBeVisible();
    await expect(page.locator('.overflow-y-auto span:text-is("APPARTEMENT")')).toHaveCount(0);
    await expect(page.locator('text=Belle Villa Familiale Ziguinchor Nema')).toBeVisible();
  });

  test('4. Filtre Type de bien : Choix multiple (VILLA & APPARTEMENT)', async ({ page }) => {
    const { requests } = await installSearchFilterFixtures(page);

    await page.goto('/recherche?type=VILLA&type=APPARTEMENT');
    await page.waitForSelector('h1:has-text("Logements Disponibles (11)")');

    expect(page.url()).toContain('type=VILLA');
    expect(page.url()).toContain('type=APPARTEMENT');
    const urlObj = new URL(requests[requests.length - 1]);
    const types = urlObj.searchParams.getAll('type');
    expect(types).toContain('VILLA');
    expect(types).toContain('APPARTEMENT');

    await expect(page.locator('span:text-is("Villa")').first()).toBeVisible();
    await expect(page.locator('span:text-is("Appartement")').first()).toBeVisible();
    await expect(page.locator('.overflow-y-auto span:text-is("STUDIO")')).toHaveCount(0);
    await expect(page.locator('.overflow-y-auto span:text-is("CHAMBRE")')).toHaveCount(0);
  });

  test('5. Filtre Budget Min et Max', async ({ page }) => {
    const { requests } = await installSearchFilterFixtures(page);

    await page.goto('/recherche?budgetMin=150000&budgetMax=500000');
    await page.waitForSelector('h1:has-text("Logements Disponibles (6)")');

    expect(page.url()).toContain('budgetMin=150000');
    expect(page.url()).toContain('budgetMax=500000');
    const urlObj = new URL(requests[requests.length - 1]);
    expect(urlObj.searchParams.get('budgetMin')).toBe('150000');
    expect(urlObj.searchParams.get('budgetMax')).toBe('500000');

    await expect(page.locator('text=Min : 150\u202f000 FCFA').or(page.locator('text=Min : 150 000 FCFA'))).toBeVisible();
    await expect(page.locator('text=Max : 500\u202f000 FCFA').or(page.locator('text=Max : 500 000 FCFA'))).toBeVisible();
    await expect(page.locator('text=350 000 FCFA').first()).toBeVisible();
    await expect(page.locator('text=65 000 000 FCFA')).toHaveCount(0);
    await expect(page.locator('text=110 000 FCFA')).toHaveCount(0);
  });

  test('6. Filtre Superficie minimale', async ({ page }) => {
    const { requests } = await installSearchFilterFixtures(page);

    await page.goto('/recherche?superficieMin=250');
    await page.waitForSelector('h1:has-text("Logements Disponibles (4)")');

    expect(page.url()).toContain('superficieMin=250');
    const urlObj = new URL(requests[requests.length - 1]);
    expect(urlObj.searchParams.get('superficieMin')).toBe('250');

    await expect(page.locator('text=≥ 250 m²')).toBeVisible();
    await expect(page.locator('text=320 m²')).toBeVisible();
    await expect(page.locator('text=310 m²')).toBeVisible();
    await expect(page.locator('text=45 m²')).toHaveCount(0);
  });

  test('7. Filtre Nombre de pièces', async ({ page }) => {
    const { requests } = await installSearchFilterFixtures(page);

    await page.goto('/recherche?nombrePieces=4');
    await page.waitForSelector('h1:has-text("Logements Disponibles (7)")');

    expect(page.url()).toContain('nombrePieces=4');
    const urlObj = new URL(requests[requests.length - 1]);
    expect(urlObj.searchParams.get('nombrePieces')).toBe('4');

    await expect(page.locator('text=4+ pièces')).toBeVisible();
    await expect(page.locator('text=Studio Cosy Plateau Centre')).toHaveCount(0);
    await expect(page.locator('text=Belle Villa Familiale Ziguinchor Nema')).toBeVisible();
  });

  test('8. Filtre Quartier (autocomplétion / URL)', async ({ page }) => {
    const { requests } = await installSearchFilterFixtures(page);

    await page.goto('/recherche?quartier=Nema');
    await page.waitForSelector('h1:has-text("Logements Disponibles (2)")');

    expect(page.url()).toContain('quartier=Nema');
    const urlObj = new URL(requests[requests.length - 1]);
    expect(urlObj.searchParams.get('quartier')).toBe('Nema');

    await expect(page.locator('text=Quartier : Nema')).toBeVisible();
    await expect(page.locator('text=Belle Villa Familiale Ziguinchor Nema')).toBeVisible();
    await expect(page.locator('text=Studio Étudiant Ziguinchor Nema')).toBeVisible();
  });

  test('9. Filtre Proximités (cases à cocher / URL)', async ({ page }) => {
    const { requests } = await installSearchFilterFixtures(page);

    await page.goto('/recherche?proximite=PLAGE');
    await page.waitForSelector('h1:has-text("Logements Disponibles (5)")');

    expect(page.url()).toContain('proximite=PLAGE');
    const urlObj = new URL(requests[requests.length - 1]);
    expect(urlObj.searchParams.getAll('proximite')).toContain('PLAGE');

    await expect(page.locator('text=Plage / Mer').or(page.locator('text=PLAGE'))).toBeVisible();
    await expect(page.locator('text=Villa Contemporaine Almadies Piscine')).toBeVisible();
  });

  test('10. Filtre Meublé', async ({ page }) => {
    const { requests } = await installSearchFilterFixtures(page);

    await page.goto('/recherche?meuble=true');
    await page.waitForSelector('h1:has-text("Logements Disponibles (8)")');

    expect(page.url()).toContain('meuble=true');
    const urlObj = new URL(requests[requests.length - 1]);
    expect(urlObj.searchParams.get('meuble')).toBe('true');

    await expect(page.locator('span:text-is("Meublé")').first()).toBeVisible();
    await expect(page.locator('text=Studio Cosy Plateau Centre')).toHaveCount(0);
  });

  test('11. Filtre Animaux acceptés', async ({ page }) => {
    const { requests } = await installSearchFilterFixtures(page);

    await page.goto('/recherche?animauxAcceptes=true');
    await page.waitForSelector('h1:has-text("Logements Disponibles (7)")');

    expect(page.url()).toContain('animauxAcceptes=true');
    const urlObj = new URL(requests[requests.length - 1]);
    expect(urlObj.searchParams.get('animauxAcceptes')).toBe('true');

    await expect(page.locator('text=Animaux acceptés').first()).toBeVisible();
    await expect(page.locator('.overflow-y-auto span:text-is("Animaux OK")')).toHaveCount(7);
  });

  test('12. Chaque pastille se supprime au clic', async ({ page }) => {
    await installSearchFilterFixtures(page);

    await page.goto('/recherche?modeTransaction=VENTE');
    await page.waitForSelector('h1:has-text("Logements Disponibles (4)")');

    const removeChipBtn = page.locator('[data-testid="remove-chip-mode"]');
    await expect(removeChipBtn).toBeVisible();

    const clearedApiPromise = page.waitForResponse(
      (res) => res.url().includes('/api/v1/annonces?') && !res.url().includes('modeTransaction=VENTE')
    );
    await removeChipBtn.click();
    await clearedApiPromise;

    await expect(removeChipBtn).not.toBeVisible();
    expect(page.url()).not.toContain('modeTransaction=VENTE');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Logements Disponibles (16)');
  });

  test('13. Le bouton Retour du navigateur restaure les filtres', async ({ page }) => {
    await installSearchFilterFixtures(page);

    await page.goto('/recherche');
    await page.waitForSelector('h1:has-text("Logements Disponibles (16)")');
    expect(page.url()).not.toContain('modeTransaction=VENTE');

    const venteApiPromise = page.waitForResponse(
      (res) => res.url().includes('/api/v1/annonces?') && res.url().includes('modeTransaction=VENTE')
    );
    await page.locator('button:text-is("Vente")').first().click();
    await venteApiPromise;

    await expect(page).toHaveURL(/modeTransaction=VENTE/);
    await expect(page.locator('[data-testid="remove-chip-mode"]')).toBeVisible();

    await page.goBack();

    await expect(page).not.toHaveURL(/modeTransaction=VENTE/);
    await expect(page.locator('[data-testid="remove-chip-mode"]')).not.toBeVisible();
    await expect(page.locator('span:text-is("Location")').first()).toBeVisible();
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Logements Disponibles (16)');
  });
});
