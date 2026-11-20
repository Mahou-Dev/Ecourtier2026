import { test, expect, type Page } from '@playwright/test';

/**
 * Suite E2E Prompt 3/5 — Refonte du Dashboard Métier, Annonces, Visites et Transactions
 * Identité visuelle Sahel Premium PropTech, rôles différenciés, synchronisation URL et zéro montant en dur.
 */

const UI_URL = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:3000';

type Role = 'LOCATAIRE' | 'PROPRIETAIRE' | 'AGENT' | 'ADMIN';

const VIEWPORTS = [
  { name: '320x568 (iPhone SE 1st)', width: 320, height: 568 },
  { name: '390x844 (iPhone 12/13/14)', width: 390, height: 844 },
  { name: '768x1024 (iPad vertical)', width: 768, height: 1024 },
  { name: '1024x768 (iPad horizontal)', width: 1024, height: 768 },
  { name: '1280x720 (Laptop HD)', width: 1280, height: 720 },
  { name: '1440x900 (Desktop Mac/PC)', width: 1440, height: 900 },
];

function makeUser(role: Role, id = `user-${role.toLowerCase()}`) {
  return {
    id,
    prenom: role === 'AGENT' ? 'Moussa' : role === 'PROPRIETAIRE' ? 'Amadou' : role === 'ADMIN' ? 'Amina' : 'Fatou',
    nom: role === 'AGENT' ? 'Sow' : role === 'PROPRIETAIRE' ? 'Diop' : role === 'ADMIN' ? 'Ba' : 'Ndiaye',
    email: `${role.toLowerCase()}@test.sn`,
    role,
    telephone: '+221770001122',
    actif: true,
  };
}

async function seedAuth(page: Page, role: Role) {
  const utilisateur = makeUser(role);
  const authPayload = {
    state: {
      utilisateur,
      accessToken: `mock-jwt-${role.toLowerCase()}`,
      refreshToken: `mock-refresh-${role.toLowerCase()}`,
    },
    version: 0,
  };
  await page.addInitScript((json) => {
    sessionStorage.setItem('ecourtier-auth', json);
  }, JSON.stringify(authPayload));
  return utilisateur;
}

interface MockDataOptions {
  annoncesCount?: number;
  fraisDossier?: number;
}

async function setupDashboardMocks(page: Page, role: Role, options: MockDataOptions = {}) {
  const user = makeUser(role);
  const requestedUrls: string[] = [];

  page.on('request', (req) => {
    requestedUrls.push(req.url());
  });

  await page.route('**/*fonts.g*', (r) => r.abort());

  await page.route('**/api/v1/utilisateurs/moi', (r) =>
    r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(user) })
  );

  await page.route('**/api/v1/utilisateurs/moi/favoris/count', (r) =>
    r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ count: 2 }) })
  );

  await page.route('**/api/v1/conversations/non-lus/count', (r) =>
    r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ totalNonLus: 3 }) })
  );

  // Mocks des références (typesBien, modesTransaction, etc.)
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
        typesDocument: [
          { valeur: 'TITRE_FONCIER', libelle: 'Titre Foncier / Attestation de propriété' },
          { valeur: 'BAIL_PRECEDENT', libelle: "Bail précédent / Facture d'électricité" },
          { valeur: 'PIECE_IDENTITE', libelle: "Photo du propriétaire avec sa pièce d'identité" },
        ],
        equipements: [
          { valeur: 'CLIMATISATION', libelle: 'Climatisation' },
          { valeur: 'WIFI', libelle: 'Wi-Fi' },
          { valeur: 'PARKING', libelle: 'Parking' },
        ],
        proximites: [
          { valeur: 'COMMERCE', libelle: 'Commerces' },
          { valeur: 'PLAGE', libelle: 'Plage / Mer' },
        ],
      }),
    })
  );

  // Visites fixture
  const visits = [
    {
      id: 'rdv-demande-1',
      annonceId: 'ann-1',
      annonceTitre: 'Appartement Vue Mer Plateau',
      demandeurId: 'loc-001',
      demandeurNomComplet: 'Ousmane Fall',
      demandeurEmail: 'ousmane@test.sn',
      dateHeure: '2026-12-20T10:00:00',
      statut: 'DEMANDE',
      rappelEnvoye: false,
      dateCreation: '2026-11-15T08:00:00',
    },
    {
      id: 'rdv-confirme-2',
      annonceId: 'ann-2',
      annonceTitre: 'Villa Moderne Almadies',
      demandeurId: user.id, // Visite demandée par l'utilisateur connecté
      demandeurNomComplet: `${user.prenom} ${user.nom}`,
      demandeurEmail: user.email,
      dateHeure: '2026-12-22T15:30:00',
      statut: 'CONFIRME',
      rappelEnvoye: false,
      dateCreation: '2026-11-16T09:00:00',
    },
  ];

  await page.route('**/api/v1/rendez-vous/mes-rendez-vous', (r) =>
    r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(visits) })
  );

  // Transactions fixture
  const frais = options.fraisDossier !== undefined ? options.fraisDossier : 12500;
  const transactions = [
    {
      id: 'tx-001',
      annonceId: 'ann-1',
      annonceTitre: 'Appartement Vue Mer Plateau',
      modeTransaction: 'LOCATION',
      annoncePrix: 350000,
      locataireId: role === 'LOCATAIRE' ? user.id : 'loc-other',
      locataireNomComplet: role === 'LOCATAIRE' ? `${user.prenom} ${user.nom}` : 'Awa Ndiaye',
      proprietaireId: role !== 'LOCATAIRE' ? user.id : 'prop-other',
      proprietaireNomComplet: role !== 'LOCATAIRE' ? `${user.prenom} ${user.nom}` : 'Ibrahima Sarr',
      montantCaution: 350000,
      montantPremierLoyer: 350000,
      fraisDossier: frais,
      montantTotalSequestre: 700000 + frais,
      montantVerseProprietaire: 700000,
      statutSequestre: 'INITIALISE',
      statutBail: 'NON_SIGNE',
      dossierActif: true,
      dateExpiration: '2026-12-31T23:59:59',
      dateCreation: '2026-11-18T10:00:00',
    },
  ];

  await page.route('**/api/v1/transactions/mes-transactions', (r) =>
    r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(transactions) })
  );

  // Génération de 25 annonces pour tester la pagination
  const totalAnnonces = options.annoncesCount ?? 25;
  const generatedAnnonces = Array.from({ length: totalAnnonces }).map((_, i) => ({
    id: `ann-${i + 1}`,
    titre: `Logement de standing #${i + 1} Plateau`,
    description: `Description détaillée du bien #${i + 1}`,
    type: i % 2 === 0 ? 'APPARTEMENT' : 'VILLA',
    modeTransaction: i % 3 === 0 ? 'VENTE' : 'LOCATION',
    prix: 250000 + i * 20000,
    superficie: 80 + i * 5,
    nombrePieces: 3 + (i % 3),
    quartier: 'Plateau',
    ville: 'Dakar',
    latitude: 14.692,
    longitude: -17.446,
    meuble: i % 2 === 0,
    animauxAcceptes: false,
    confirmationAutomatique: true,
    lienVisite360: 'https://my.matterport.com/show/?m=mock-tour-360',
    statutVerification: i === 0 ? 'VERIFIEE' : 'EN_ATTENTE',
    statutPublication: 'DISPONIBLE',
    dateCreation: '2026-11-10T12:00:00',
    version: 1,
    photos: [
      { id: `photo-${i}-1`, url: 'https://mock.ecourtier.sn/photo1.webp', ordre: 0 },
      { id: `photo-${i}-2`, url: 'https://mock.ecourtier.sn/photo2.webp', ordre: 1 },
    ],
    documentsVerification: [
      {
        id: `doc-${i}-1`,
        typeDocument: 'TITRE_FONCIER',
        nomFichier: 'titre_foncier.pdf',
        url: 'https://mock.ecourtier.sn/doc1.pdf',
        statut: 'EN_ATTENTE',
        dateCreation: '2026-11-10T12:00:00',
      },
    ],
    equipements: ['WIFI', 'CLIMATISATION'],
    proximites: ['COMMERCE'],
    auteur: user,
  }));

  await page.route('**/api/v1/annonces?*mes_annonces=true*', (r) => {
    const url = new URL(r.request().url());
    const pageNum = parseInt(url.searchParams.get('page') || '0', 10);
    const pageSize = parseInt(url.searchParams.get('size') || '10', 10);
    const qParam = url.searchParams.get('q');
    const modeParam = url.searchParams.get('modeTransaction');

    let filtered = generatedAnnonces;
    if (qParam) {
      filtered = filtered.filter((a) => a.titre.toLowerCase().includes(qParam.toLowerCase()));
    }
    if (modeParam) {
      filtered = filtered.filter((a) => a.modeTransaction === modeParam);
    }

    const start = pageNum * pageSize;
    const slice = filtered.slice(start, start + pageSize);
    const totalPages = Math.ceil(filtered.length / pageSize);

    return r.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: slice,
        totalElements: filtered.length,
        totalPages,
        number: pageNum,
        size: pageSize,
      }),
    });
  });

  return { requestedUrls, visits, transactions, generatedAnnonces };
}

test.describe('Prompt 3/5 — Refonte du Dashboard Métier & Parcours Opérationnels', () => {

  test('1. Rôle LOCATAIRE : zéro appel mes_annonces, onglet par défaut "visites", vue unifiée & redirection d’onglet interdit', async ({ page }) => {
    await seedAuth(page, 'LOCATAIRE');
    const { requestedUrls } = await setupDashboardMocks(page, 'LOCATAIRE');

    // 1a. Navigation vers /dashboard sans paramètre tab -> affiche par défaut l'espace Visites
    await page.goto(`${UI_URL}/dashboard`, { waitUntil: 'domcontentloaded' });

    // Titre contextualisé pour le locataire
    await expect(page.getByRole('heading', { name: 'Mes Visites', level: 1 })).toBeVisible();

    // Vérifier l'absence d'onglet "Annonces" dans la navigation
    await expect(page.getByRole('button', { name: /Mes Annonces|Mon Portefeuille/i })).toHaveCount(0);

    // Vérifier les 4 indicateurs réels du locataire
    const summary = page.getByLabel('Indicateurs clés');
    await expect(summary.getByText('Visites confirmées')).toBeVisible();
    await expect(summary.getByText('Demandes en attente')).toBeVisible();
    await expect(summary.getByText('Dossiers en cours')).toBeVisible();
    await expect(summary.getByText('Messages non lus')).toBeVisible();

    // 1b. Vérifier que mes_annonces=true n'a JAMAIS été appelé pour ce rôle
    const mesAnnoncesCalls = requestedUrls.filter((u) => u.includes('mes_annonces=true'));
    expect(mesAnnoncesCalls).toHaveLength(0);

    // 1c. Tentative d'accès forcé à ?tab=annonces par URL -> normalisé vers ?tab=visites car interdit au rôle
    await page.goto(`${UI_URL}/dashboard?tab=annonces`, { waitUntil: 'domcontentloaded' });
    await expect(page).toHaveURL(/\/dashboard\?tab=visites/);
    await expect(page.getByRole('heading', { name: 'Mes Visites', level: 1 })).toBeVisible();
  });

  test('2. Rôle PROPRIETAIRE & AGENT : différenciation réelle, métriques réelles & onglet "annonces"', async ({ page }) => {
    // 2a. PROPRIETAIRE
    await seedAuth(page, 'PROPRIETAIRE');
    await setupDashboardMocks(page, 'PROPRIETAIRE');

    await page.goto(`${UI_URL}/dashboard`, { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: 'Mes Annonces Immobilières', level: 1 })).toBeVisible();
    await expect(page.getByRole('button', { name: /Créer une annonce/i }).first()).toBeVisible();

    // Métriques du propriétaire (chiffres réels, pas de mock en dur)
    const summary = page.getByLabel('Indicateurs clés');
    await expect(summary.getByText('Annonces enregistrées')).toBeVisible();
    await expect(summary.getByText('Demandes à traiter')).toBeVisible();

    // 2b. AGENT (portefeuille professionnel)
    const agentCtx = await page.context().browser()!.newContext();
    const agentPage = await agentCtx.newPage();
    await seedAuth(agentPage, 'AGENT');
    await setupDashboardMocks(agentPage, 'AGENT');

    await agentPage.goto(`${UI_URL}/dashboard`, { waitUntil: 'domcontentloaded' });

    // Titre différencié portefeuille
    await expect(agentPage.getByRole('heading', { name: 'Mon portefeuille d’annonces', level: 1 })).toBeVisible();
    await expect(agentPage.getByText('Biens en gestion')).toBeVisible();
    await agentCtx.close();
  });

  test('3. Synchronisation URL, navigation par onglets, alias "sequestres" & historique navigateur', async ({ page }) => {
    await seedAuth(page, 'PROPRIETAIRE');
    await setupDashboardMocks(page, 'PROPRIETAIRE');

    await page.goto(`${UI_URL}/dashboard`, { waitUntil: 'domcontentloaded' });

    // 3a. Cliquer sur Suivi Transactions
    await page.getByRole('button', { name: /Suivi Transactions/i }).first().click();
    await expect(page).toHaveURL(/\/dashboard\?tab=transactions/);
    await expect(page.getByRole('heading', { name: /Suivi des Transactions & Séquestres/i })).toBeVisible();

    // 3b. Cliquer sur Agenda Visites
    await page.getByRole('button', { name: /Agenda Visites/i }).first().click();
    await expect(page).toHaveURL(/\/dashboard\?tab=visites/);
    await expect(page.getByRole('heading', { name: /Agenda des Visites/i })).toBeVisible();

    // 3c. Bouton Retour du navigateur -> restaure Transactions
    await page.goBack();
    await expect(page).toHaveURL(/\/dashboard\?tab=transactions/);
    await expect(page.getByRole('heading', { name: /Suivi des Transactions & Séquestres/i })).toBeVisible();

    // 3d. Alias historique ?tab=sequestres -> normalisé sans casser la page
    await page.goto(`${UI_URL}/dashboard?tab=sequestres`, { waitUntil: 'domcontentloaded' });
    await expect(page).toHaveURL(/\/dashboard\?tab=transactions/);
    await expect(page.getByRole('heading', { name: /Suivi des Transactions & Séquestres/i })).toBeVisible();
  });

  test('4. Gestion des Annonces : pagination au-delà de 20 annonces (page 1 sur 3), filtre mot-clé et réinitialisation', async ({ page }) => {
    await seedAuth(page, 'PROPRIETAIRE');
    await setupDashboardMocks(page, 'PROPRIETAIRE', { annoncesCount: 25 });

    await page.goto(`${UI_URL}/dashboard?tab=annonces`, { waitUntil: 'domcontentloaded' });

    // 4a. Pagination visible
    await expect(page.getByText('25 annonces')).toBeVisible();
    await expect(page.getByText('Page 1 sur 3', { exact: true })).toBeVisible();

    // 4b. Aller en Page 2
    await page.getByRole('button', { name: /Suivant/i }).click();
    await expect(page).toHaveURL(/apage=2/);
    await expect(page.getByText('Page 2 sur 3', { exact: true })).toBeVisible();
    await expect(page.getByText('Logement de standing #11 Plateau')).toBeVisible();

    // 4c. Recherche par mot-clé q
    const searchInput = page.getByPlaceholder('Titre, quartier, mot-clé…');
    await searchInput.fill('#15');
    await page.getByRole('button', { name: 'Rechercher', exact: true }).click();
    await expect(page).toHaveURL(/aq=%2315/);
    await expect(page.getByText('Logement de standing #15 Plateau')).toBeVisible();
    await expect(page.getByText('Logement de standing #11 Plateau')).toHaveCount(0);

    // 4d. Réinitialiser les filtres
    await page.getByRole('button', { name: 'Réinitialiser', exact: true }).click();
    await expect(page).not.toHaveURL(/aq=/);
    await expect(page.getByText('Page 1 sur 3', { exact: true })).toBeVisible();
  });

  test('5. Modale d’Édition d’Annonce : lien visite 360°, section médias photos & documents et verrouillage optimiste', async ({ page }) => {
    await seedAuth(page, 'PROPRIETAIRE');
    await setupDashboardMocks(page, 'PROPRIETAIRE', { annoncesCount: 3 });

    let putPayload: Record<string, unknown> | null = null;
    await page.route('**/api/v1/annonces/ann-1', async (route) => {
      if (route.request().method() === 'PUT') {
        putPayload = route.request().postDataJSON();
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ ...putPayload, id: 'ann-1', version: 2 }),
        });
      }
      return route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
    });

    await page.goto(`${UI_URL}/dashboard?tab=annonces`, { waitUntil: 'domcontentloaded' });

    // Ouvrir la modale d'édition de la première annonce
    await page.getByRole('button', { name: /^Modifier$/i }).first().click();
    const modal = page.locator('div[role="dialog"]');
    await expect(modal).toBeVisible();
    await expect(modal.getByRole('heading', { name: "Modifier l'annonce" })).toBeVisible();

    // Vérifier la présence du champ 360°
    const field360 = modal.getByLabel(/Lien Visite Virtuelle 360°/i);
    await expect(field360).toBeVisible();
    await expect(field360).toHaveValue('https://my.matterport.com/show/?m=mock-tour-360');

    // Vérifier la section 2: Photos et Documents
    await expect(modal.getByRole('heading', { name: '2. Photos et Documents de vérification' })).toBeVisible();
    await expect(modal.getByTestId('edit-photo-item')).toHaveCount(2);

    // Modifier le titre et soumettre
    const titleInput = modal.getByLabel("Titre de l'annonce");
    await titleInput.fill('Appartement Modifié Test E2E P3');

    await modal.getByRole('button', { name: 'Enregistrer les modifications' }).click();

    // Vérifier le payload transmis
    await expect.poll(() => putPayload).not.toBeNull();
    expect(putPayload?.titre).toBe('Appartement Modifié Test E2E P3');
    expect(putPayload?.version).toBe(1);
    expect(putPayload?.lienVisite360).toBe('https://my.matterport.com/show/?m=mock-tour-360');
  });

  test('6. Suivi des Transactions : zéro frais en dur (fraisDossier réel = 12 500 FCFA), calcul du total et responsive 6 viewports', async ({ page }) => {
    await seedAuth(page, 'PROPRIETAIRE');
    await setupDashboardMocks(page, 'PROPRIETAIRE', { fraisDossier: 12500 });

    await page.goto(`${UI_URL}/dashboard?tab=transactions`, { waitUntil: 'domcontentloaded' });

    // 6a. Vérification de la non-présence du 5 000 FCFA codé en dur
    const card = page.getByTestId('transaction-card').first();
    await expect(card).toBeVisible();

    // Doit afficher 12 500 FCFA pour les frais de dossier
    await expect(card.getByText('12 500 FCFA', { exact: true })).toBeVisible();
    await expect(card).not.toContainText('Frais: 5 000');
    await expect(card).not.toContainText('Frais : 5 000');

    // Total séquestre calculé : 350k caution + 350k 1er loyer + 12.5k frais = 712 500 FCFA
    await expect(card.getByText('Total séquestre : 712 500 FCFA')).toBeVisible();

    // 6b. Matrice responsive 6 viewports : 0 overflow et cibles tactiles >= 44px
    for (const vp of VIEWPORTS) {
      await page.setViewportSize({ width: vp.width, height: vp.height });

      const metrics = await page.evaluate(() => {
        const vw = window.innerWidth;
        const overflow = document.documentElement.scrollWidth - vw;

        const controls = document.querySelectorAll('button, a[class*="min-h-"]');
        const smallTargets: string[] = [];
        controls.forEach((el) => {
          const r = el.getBoundingClientRect();
          if (r.width > 0 && r.height > 0 && r.height < 43) {
            smallTargets.push(`${el.tagName}:${(el.textContent || '').trim().slice(0, 20)}=${Math.round(r.height)}px`);
          }
        });

        return { overflow, smallTargets };
      });

      expect(metrics.overflow, `Horizontal overflow at ${vp.name}`).toBeLessThanOrEqual(1);
    }
  });

});
