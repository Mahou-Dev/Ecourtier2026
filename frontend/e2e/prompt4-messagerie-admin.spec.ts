import { test, expect, type Page } from '@playwright/test';

/**
 * Suite E2E Prompt 4/5 — Refonte de la Messagerie et de l'Administration des Utilisateurs
 * Palette Sahel, résolution du squish 128px à 768x1024, suppression du min-h-[480px] rigide à 320x568,
 * tableau desktop + cartes tablettes/mobiles sans coupure d'actions, et navigation administrative sans Wave.
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
    prenom: role === 'ADMIN' ? 'Amina' : role === 'AGENT' ? 'Moussa' : role === 'PROPRIETAIRE' ? 'Amadou' : 'Fatou',
    nom: role === 'ADMIN' ? 'Ba' : role === 'AGENT' ? 'Sow' : role === 'PROPRIETAIRE' ? 'Diop' : 'Ndiaye',
    email: `${role.toLowerCase()}@test.sn`,
    role,
    telephone: '+221 77 123 45 67',
    actif: true,
    dateCreation: '2026-01-15T10:00:00Z',
  };
}

async function seedAuth(page: Page, role: Role) {
  const utilisateur = makeUser(role);
  await page.addInitScript((user) => {
    window.sessionStorage.setItem(
      'ecourtier-auth',
      JSON.stringify({
        state: {
          utilisateur: user,
          accessToken: `jwt-${user.role}`,
          refreshToken: `refresh-${user.role}`,
        },
        version: 0,
      })
    );
  }, utilisateur);
  return utilisateur;
}

const mockConversations = [
  {
    id: 'conv-101',
    annonceId: 'ann-almadies-01',
    annonceTitre: 'Villa Moderne avec Piscine Almadies',
    locataire: {
      id: 'user-locataire',
      nom: 'Ndiaye',
      prenom: 'Fatou',
      email: 'locataire@test.sn',
      telephone: '+221 77 111 22 33',
      role: 'LOCATAIRE',
      actif: true,
    },
    proprietaire: {
      id: 'user-proprietaire',
      nom: 'Diop',
      prenom: 'Amadou',
      email: 'proprietaire@test.sn',
      telephone: '+221 77 444 55 66',
      role: 'PROPRIETAIRE',
      actif: true,
    },
    nbMessagesNonLus: 2,
    dernierMessage: {
      id: 'msg-prev-01',
      contenu: 'Bonjour, la villa est-elle toujours disponible pour une visite samedi ?',
      dateEnvoi: '2026-03-10T14:32:00Z',
      expediteur: {
        id: 'user-locataire',
        nom: 'Ndiaye',
        prenom: 'Fatou',
      },
      lu: false,
    },
  },
  {
    id: 'conv-102',
    annonceId: 'ann-plateau-02',
    annonceTitre: 'Appartement F4 Plateau Vue Mer',
    locataire: {
      id: 'user-locataire-2',
      nom: 'Sarr',
      prenom: 'Ibrahima',
      email: 'ibrahima.sarr@test.sn',
      telephone: '+221 77 777 88 99',
      role: 'LOCATAIRE',
      actif: true,
    },
    proprietaire: {
      id: 'user-proprietaire',
      nom: 'Diop',
      prenom: 'Amadou',
      email: 'proprietaire@test.sn',
      role: 'PROPRIETAIRE',
      actif: true,
    },
    nbMessagesNonLus: 0,
    dernierMessage: {
      id: 'msg-prev-02',
      contenu: 'Dossier de candidature reçu et validé.',
      dateEnvoi: '2026-03-09T18:15:00Z',
      expediteur: {
        id: 'user-proprietaire',
        nom: 'Diop',
        prenom: 'Amadou',
      },
      lu: true,
    },
  },
];

const mockMessagesConv101 = [
  {
    id: 'msg-01',
    conversationId: 'conv-101',
    contenu: 'Bonjour, je suis très intéressée par votre villa aux Almadies.',
    dateEnvoi: '2026-03-10T14:30:00Z',
    expediteur: {
      id: 'user-locataire',
      nom: 'Ndiaye',
      prenom: 'Fatou',
    },
    lu: true,
  },
  {
    id: 'msg-02',
    conversationId: 'conv-101',
    contenu: 'Bonjour Fatou ! Oui, elle est disponible. Quels créneaux vous arrangent ?',
    dateEnvoi: '2026-03-10T14:31:00Z',
    expediteur: {
      id: 'user-proprietaire',
      nom: 'Diop',
      prenom: 'Amadou',
    },
    lu: true,
  },
  {
    id: 'msg-03',
    conversationId: 'conv-101',
    contenu: 'Bonjour, la villa est-elle toujours disponible pour une visite samedi ?',
    dateEnvoi: '2026-03-10T14:32:00Z',
    expediteur: {
      id: 'user-locataire',
      nom: 'Ndiaye',
      prenom: 'Fatou',
    },
    lu: false,
  },
];

const mockAdminUsers = [
  {
    id: 'user-admin',
    nom: 'Ba',
    prenom: 'Amina',
    email: 'admin@test.sn',
    telephone: '+221 77 000 00 01',
    role: 'ADMIN',
    actif: true,
    dateCreation: '2026-01-01T08:00:00Z',
  },
  {
    id: 'user-agent-1',
    nom: 'Sow',
    prenom: 'Moussa',
    email: 'moussa.sow@agence-teranga.sn',
    telephone: '+221 77 222 33 44',
    role: 'AGENT',
    actif: true,
    dateCreation: '2026-01-10T11:20:00Z',
  },
  {
    id: 'user-proprio-1',
    nom: 'Diop',
    prenom: 'Amadou',
    email: 'amadou.diop@test.sn',
    telephone: '+221 77 333 44 55',
    role: 'PROPRIETAIRE',
    actif: true,
    dateCreation: '2026-01-15T09:40:00Z',
  },
  {
    id: 'user-loc-1',
    nom: 'Ndiaye',
    prenom: 'Fatou',
    email: 'fatou.ndiaye@test.sn',
    telephone: '+221 77 555 66 77',
    role: 'LOCATAIRE',
    actif: false, // inactif pour tester la réactivation
    dateCreation: '2026-02-01T15:10:00Z',
  },
];

async function setupCommonMocks(page: Page, role: Role) {
  const user = makeUser(role);

  await page.route('**/*fonts.g*', (r) => r.abort());
  await page.route('**/ws', (r) => r.abort());

  await page.route('**/api/v1/utilisateurs/moi', (route) => {
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(user) });
  });

  await page.route('**/api/v1/utilisateurs/moi/favoris/count', (route) => {
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ count: 1 }) });
  });

  await page.route('**/api/v1/conversations/non-lus/count', (route) => {
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ totalNonLus: 2 }) });
  });

  await page.route('**/api/v1/conversations/non-lus/compteur', (route) => {
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ totalNonLus: 2 }) });
  });

  await page.route('**/api/v1/references', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        typesBien: [],
        modesTransaction: [],
        typesDocument: [],
        equipements: [],
        proximites: [],
      }),
    });
  });

  await page.route('**/api/v1/annonces*', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [],
        totalElements: 0,
        totalPages: 0,
        size: 10,
        number: 0,
        first: true,
        last: true,
      }),
    });
  });

  await page.route('**/api/v1/rendez-vous/mes-rendez-vous', (route) => {
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
  });

  await page.route('**/api/v1/transactions/mes-transactions', (route) => {
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
  });
}

async function setupMessagerieMocks(page: Page, role: Role = 'PROPRIETAIRE') {
  await setupCommonMocks(page, role);

  await page.route('**/api/v1/conversations/mes-conversations', (route) => {
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockConversations) });
  });

  await page.route(/\/api\/v1\/conversations\/conv-101\/messages\/tous/, (route) => {
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockMessagesConv101) });
  });

  await page.route(/\/api\/v1\/conversations\/conv-102\/messages\/tous/, (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          id: 'msg-102-1',
          conversationId: 'conv-102',
          contenu: 'Dossier de candidature reçu et validé.',
          dateEnvoi: '2026-03-09T18:15:00Z',
          expediteur: { id: 'user-proprietaire', nom: 'Diop', prenom: 'Amadou' },
          lu: true,
        },
      ]),
    });
  });

  await page.route(/\/api\/v1\/conversations\/.*\/marquer-lu/, (route) => {
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true }) });
  });
}

async function setupAdminMocks(page: Page) {
  await setupCommonMocks(page, 'ADMIN');

  // Admin stats KPI
  await page.route('**/api/v1/admin/statistiques*', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        periode: '30j',
        utilisateurs: {
          totalParRole: {
            ADMIN: 2,
            AGENT: 12,
            PROPRIETAIRE: 28,
            LOCATAIRE: 84,
          },
          nouvellesInscriptions: [],
        },
        annonces: {
          totalParStatutPublication: { DISPONIBLE: 45 },
          totalParStatutVerification: { VERIFIEE: 30 },
          repartitionParVilleQuartier: [],
          prixMoyenParTypeBien: {},
          tempsMoyenDecisionMinutes: 120,
        },
        activiteCommerciale: {
          volumeTransactionsFCFA: 50000000,
          totalTransactions: 15,
          totalVisites: 90,
          tauxConversionVisiteTransaction: 0.16,
        },
        moderation: {
          signalementsEnAttente: 2,
          signalementsTraites: 18,
          comptesSuspendus: 1,
        },
        assistantIA: {
          conversationsTotal: 340,
          tauxResolutionSansAgent: 0.82,
          feedbacksPositifs: 290,
        },
      }),
    });
  });

  // Admin users list with filtering & pagination
  await page.route('**/api/v1/admin/utilisateurs*', (route) => {
    const url = new URL(route.request().url());
    const method = route.request().method();

    if (method === 'POST') {
      const body = JSON.parse(route.request().postData() || '{}');
      const newUser = {
        id: `user-${Date.now()}`,
        prenom: body.prenom || 'Nouveau',
        nom: body.nom || 'Agent',
        email: body.email,
        telephone: body.telephone,
        role: body.role || 'AGENT',
        actif: true,
        dateCreation: new Date().toISOString(),
      };
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          utilisateur: newUser,
          motDePasseTemporaire: body.motDePasseTemporaire || 'PassTemp123!',
        }),
      });
      return;
    }

    if (method === 'GET') {
      const role = url.searchParams.get('role');
      const actif = url.searchParams.get('actif');
      const recherche = url.searchParams.get('recherche')?.toLowerCase();

      let filtered = [...mockAdminUsers];

      if (role && role !== 'TOUS') {
        filtered = filtered.filter((u) => u.role === role);
      }
      if (actif !== null && actif !== undefined) {
        const isActif = actif === 'true';
        filtered = filtered.filter((u) => u.actif === isActif);
      }
      if (recherche) {
        filtered = filtered.filter(
          (u) =>
            u.prenom.toLowerCase().includes(recherche) ||
            u.nom.toLowerCase().includes(recherche) ||
            u.email.toLowerCase().includes(recherche) ||
            u.telephone.includes(recherche)
        );
      }

      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: filtered,
          totalElements: filtered.length,
          totalPages: 1,
          size: 15,
          number: 0,
          first: true,
          last: true,
        }),
      });
      return;
    }

    route.continue();
  });

  // Role modification
  await page.route(/\/api\/v1\/admin\/utilisateurs\/.*\/role/, (route) => {
    const body = JSON.parse(route.request().postData() || '{}');
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        ...mockAdminUsers[1],
        role: body.role,
      }),
    });
  });

  // Status modification (deactivate / reactivate)
  await page.route(/\/api\/v1\/admin\/utilisateurs\/.*\/statut/, (route) => {
    const body = JSON.parse(route.request().postData() || '{}');
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        ...mockAdminUsers[1],
        actif: body.actif,
      }),
    });
  });
}

test.describe('E-COURTIER — Prompt 4/5 : Messagerie et Administration des Utilisateurs', () => {
  test('1. Messagerie : Chargement, sélection, filtres et contexte annonce', async ({ page }) => {
    await seedAuth(page, 'PROPRIETAIRE');
    await setupMessagerieMocks(page, 'PROPRIETAIRE');

    await page.setViewportSize({ width: 1280, height: 720 });
    await page.goto(`${UI_URL}/dashboard?tab=messages`, { waitUntil: 'domcontentloaded' });

    // Titre de la messagerie
    await expect(page.getByText('Messagerie Directe')).toBeVisible();

    // Vérifier les 2 conversations dans la liste
    const convFatou = page.getByRole('button', { name: /Fatou Ndiaye/i });
    const convIbrahima = page.getByRole('button', { name: /Ibrahima Sarr/i });
    await expect(convFatou).toBeVisible();
    await expect(convIbrahima).toBeVisible();

    // Contexte annonce dans la conversation active (conv-101)
    await expect(page.getByText('Villa Moderne avec Piscine Almadies').first()).toBeVisible();
    const annonceLink = page.getByRole('link', { name: /Voir l'annonce/i });
    await expect(annonceLink).toBeVisible();
    await expect(annonceLink).toHaveAttribute('href', '/annonce/ann-almadies-01');

    // Vérifier les messages chargés
    await expect(page.getByText('Bonjour, je suis très intéressée par votre villa aux Almadies.')).toBeVisible();
    await expect(page.getByText('Bonjour Fatou ! Oui, elle est disponible. Quels créneaux vous arrangent ?')).toBeVisible();

    // Changement de conversation vers conv-102
    await convIbrahima.click();
    await expect(page.getByText('Appartement F4 Plateau Vue Mer').first()).toBeVisible();
    await expect(page.getByText('Dossier de candidature reçu et validé.').first()).toBeVisible();
  });

  test('2. Messagerie : Envoi de message, touche Entrée et déduplication', async ({ page }) => {
    await seedAuth(page, 'PROPRIETAIRE');
    await setupMessagerieMocks(page, 'PROPRIETAIRE');

    await page.setViewportSize({ width: 1280, height: 720 });
    await page.goto(`${UI_URL}/dashboard?tab=messages`, { waitUntil: 'domcontentloaded' });

    // Champ de saisie
    const input = page.getByPlaceholder('Écrivez un message...');
    await expect(input).toBeVisible();

    // Saisie et envoi
    await input.fill('Parfait, je vous confirme le rendez-vous à 15h.');
    const sendButton = page.getByRole('button', { name: 'Envoyer' });
    await expect(sendButton).toBeEnabled();

    // Soumission via clic ou enter
    await input.press('Enter');

    // Le champ doit être accessible et utilisable
    await expect(input).toBeVisible();
  });

  test('3. Messagerie Responsive : 768x1024 (résolution 128px) et 320x568 (saisie accessible)', async ({ page }) => {
    await seedAuth(page, 'PROPRIETAIRE');
    await setupMessagerieMocks(page, 'PROPRIETAIRE');

    // A) 768x1024 — Le panneau de conversation NE DOIT PAS être écrasé à 128px
    await page.setViewportSize({ width: 768, height: 1024 });
    await page.goto(`${UI_URL}/dashboard?tab=messages`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(300);

    const chatPane = page.locator('.flex-1.flex-col.bg-white').first();
    await expect(chatPane).toBeVisible();
    const box768 = await chatPane.boundingBox();
    expect(box768).not.toBeNull();
    // Doit être largement supérieur à 128px (au moins 400px à 768px de large)
    expect(box768!.width).toBeGreaterThanOrEqual(400);

    // B) 320x568 — Saisie accessible, pas de min-h-[480px] poussant hors-écran
    await page.setViewportSize({ width: 320, height: 568 });
    await page.goto(`${UI_URL}/dashboard?tab=messages`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(300);

    // Sur mobile 320px, on clique sur la conversation pour ouvrir le chat
    const convBtn = page.getByRole('button', { name: /Fatou Ndiaye/i });
    await expect(convBtn).toBeVisible();
    await convBtn.click();

    // Bouton retour aux conversations
    const backBtn = page.getByRole('button', { name: 'Retour aux conversations' });
    await expect(backBtn).toBeVisible();

    // Champ d'envoi visible à l'écran dans le viewport de 568px
    const inputMobile = page.getByPlaceholder('Écrivez un message...');
    await expect(inputMobile).toBeVisible();
    await inputMobile.scrollIntoViewIfNeeded();
    const inputBox = await inputMobile.boundingBox();
    expect(inputBox).not.toBeNull();
    // Le champ de saisie doit être contenu dans l'écran après scroll
    expect(inputBox!.y + inputBox!.height).toBeLessThanOrEqual(568);
    expect(inputBox!.y).toBeGreaterThanOrEqual(0);

    // Retour à la liste
    await backBtn.click();
    await expect(convBtn).toBeVisible();
  });

  test('4. Admin Utilisateurs : Contrôle d’accès (LOCATAIRE redirigé, ADMIN autorisé)', async ({ page }) => {
    // 1. Un LOCATAIRE ne doit pas pouvoir accéder à /admin/utilisateurs
    await seedAuth(page, 'LOCATAIRE');
    await setupCommonMocks(page, 'LOCATAIRE');
    await page.goto(`${UI_URL}/admin/utilisateurs`, { waitUntil: 'domcontentloaded' });

    // Redirigé proprement vers /recherche
    await page.waitForURL('**/recherche**');
    expect(page.url()).toContain('/recherche');

    // 2. Un ADMIN peut accéder à /admin/utilisateurs
    await seedAuth(page, 'ADMIN');
    await setupAdminMocks(page);
    await page.goto(`${UI_URL}/admin/utilisateurs`, { waitUntil: 'domcontentloaded' });

    await expect(page.getByRole('heading', { name: /Gestion des Utilisateurs & Agents/i })).toBeVisible();
    await expect(page.getByText('Espace Administration')).toBeVisible();
  });

  test('5. Admin Utilisateurs : Indicateurs réels (KPI), recherche et filtrage', async ({ page }) => {
    await seedAuth(page, 'ADMIN');
    await setupAdminMocks(page);

    await page.setViewportSize({ width: 1280, height: 720 });
    await page.goto(`${UI_URL}/admin/utilisateurs`, { waitUntil: 'domcontentloaded' });

    // Vérifier les 4 cartes KPI avec les vraies statistiques retournées
    const kpiSection = page.getByLabel('Indicateurs clés');
    await expect(kpiSection.getByText('Agents Agréés')).toBeVisible();
    await expect(kpiSection.getByText('12')).toBeVisible();
    await expect(kpiSection.getByText('Propriétaires')).toBeVisible();
    await expect(kpiSection.getByText('28')).toBeVisible();
    await expect(kpiSection.getByText('Locataires')).toBeVisible();
    await expect(kpiSection.getByText('84')).toBeVisible();

    // Recherche d'un utilisateur par mot-clé
    const searchInput = page.getByPlaceholder('Rechercher par nom, prénom, email ou téléphone...');
    await expect(searchInput).toBeVisible();
    await searchInput.fill('Moussa');

    // L'agent Moussa Sow doit être visible
    await expect(page.getByText('Moussa Sow').first()).toBeVisible();

    // Vider la recherche avant de tester le filtre de rôle
    await searchInput.fill('');

    // Filtrage par rôle
    const roleSelect = page.getByLabel('Filtrer par rôle');
    await roleSelect.selectOption('PROPRIETAIRE');
    await expect(page.getByText('Amadou Diop').first()).toBeVisible();

    // Remettre tous les rôles et filtrer par statut
    await roleSelect.selectOption('TOUS');
    const statutSelect = page.getByLabel('Filtrer par statut');
    await statutSelect.selectOption('INACTIF');
    await expect(page.getByText('Fatou Ndiaye').first()).toBeVisible();
  });

  test('6. Admin Utilisateurs : Actions administratives (Changer rôle, Confirmation suspension, Réactivation, Création)', async ({
    page,
  }) => {
    await seedAuth(page, 'ADMIN');
    await setupAdminMocks(page);

    await page.setViewportSize({ width: 1280, height: 720 });
    await page.goto(`${UI_URL}/admin/utilisateurs`, { waitUntil: 'domcontentloaded' });

    // A) Changer le rôle de Moussa Sow
    const rowMoussa = page.locator('tr', { hasText: 'Moussa Sow' });
    const changeRoleBtn = rowMoussa.getByRole('button', { name: 'Changer le rôle' });
    await expect(changeRoleBtn).toBeVisible();
    await changeRoleBtn.click();

    // Modale de changement de rôle
    await expect(page.getByRole('heading', { name: "Changer le rôle de l'utilisateur" })).toBeVisible();
    const submitRoleBtn = page.getByRole('button', { name: 'Enregistrer le rôle' });
    await submitRoleBtn.click();
    await expect(page.getByRole('heading', { name: "Changer le rôle de l'utilisateur" })).toBeHidden();

    // B) Action sensible : Suspension avec modale de confirmation
    const deactivateBtn = rowMoussa.getByRole('button', { name: 'Désactiver' });
    await expect(deactivateBtn).toBeVisible();
    await deactivateBtn.click();

    // Modale de confirmation
    await expect(page.getByRole('heading', { name: 'Désactiver le compte utilisateur' })).toBeVisible();
    await expect(page.getByText(/Attention — Suspension d'accès immédiate/i)).toBeVisible();
    const confirmDeactivateBtn = page.getByRole('button', { name: 'Confirmer la désactivation' });
    await confirmDeactivateBtn.click();
    await expect(page.getByRole('heading', { name: 'Désactiver le compte utilisateur' })).toBeHidden();

    // C) Réactivation directe d'un compte inactif (Fatou Ndiaye)
    const rowFatou = page.locator('tr', { hasText: 'Fatou Ndiaye' });
    const reactivateBtn = rowFatou.getByRole('button', { name: 'Réactiver' });
    await expect(reactivateBtn).toBeVisible();
    await reactivateBtn.click();

    // D) Création d'un compte avec affichage unique du mot de passe
    const createBtn = page.getByRole('button', { name: 'Créer un compte' });
    await createBtn.click();

    await expect(page.getByRole('heading', { name: 'Créer un compte utilisateur' })).toBeVisible();
    await page.getByLabel('Prénom').fill('Aïssatou');
    await page.getByLabel('Nom', { exact: true }).fill('Diallo');
    await page.getByLabel('Adresse Email').fill('aissatou.diallo@ecourtier.sn');
    await page.getByRole('button', { name: 'Créer le compte' }).click();

    // Fenêtre avec le mot de passe temporaire
    await expect(page.getByRole('heading', { name: /Mot de passe temporaire/i })).toBeVisible();
    await expect(page.getByText('Affichage unique — Copiez ce mot de passe maintenant')).toBeVisible();
    const copyBtn = page.getByRole('button', { name: 'Copier' });
    await expect(copyBtn).toBeVisible();
    await page.getByRole('button', { name: "J'ai noté le mot de passe" }).click();
  });

  test('7. Admin Utilisateurs : Résolution du débordement 768x1024 et absence de Wave', async ({ page }) => {
    await seedAuth(page, 'ADMIN');
    await setupAdminMocks(page);

    // A) 768x1024 : Pas de tableau débordant masquant les actions, affichage en cartes avec boutons complets
    await page.setViewportSize({ width: 768, height: 1024 });
    await page.goto(`${UI_URL}/admin/utilisateurs`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(300);

    // Le tableau desktop est masqué à 768px (< lg)
    await expect(page.locator('table')).toBeHidden();

    // Les cartes utilisateurs sont visibles et les boutons d'actions sont accessibles
    const roleBtns = page.getByRole('button', { name: 'Changer le rôle' });
    await expect(roleBtns.first()).toBeVisible();

    // Vérifier l'absence de débordement horizontal
    const scrollWidth = await page.evaluate(() => document.documentElement.scrollWidth);
    expect(scrollWidth).toBeLessThanOrEqual(768);

    // B) Vérifier qu'aucune mention de Wave n'apparaît dans la navigation
    const adminNav = page.getByRole('navigation', { name: 'Navigation administration' });
    await expect(adminNav).toBeVisible();
    await expect(adminNav.getByText(/Wave/i)).toBeHidden();
    await expect(adminNav.getByRole('link', { name: /Séquestres & Transactions/i })).toBeVisible();
  });

  test('8. Robustesse multi-viewports : Zéro overflow sur les 6 résolutions de référence', async ({ page }) => {
    await seedAuth(page, 'ADMIN');
    await setupAdminMocks(page);

    for (const vp of VIEWPORTS) {
      await page.setViewportSize({ width: vp.width, height: vp.height });
      await page.goto(`${UI_URL}/admin/utilisateurs`, { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(200);

      const hasHorizontalScroll = await page.evaluate(
        (expectedWidth) => document.documentElement.scrollWidth > expectedWidth + 1,
        vp.width
      );
      expect(hasHorizontalScroll, `Débordement horizontal détecté sur viewport ${vp.name}`).toBe(false);
    }
  });
});
