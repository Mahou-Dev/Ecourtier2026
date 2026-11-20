import { test, expect, Page } from '@playwright/test';

const UI_URL = process.env.E2E_UI_URL || 'http://127.0.0.1:3000';

const VIEWPORTS = [
  { name: '320x568', width: 320, height: 568 },
  { name: '390x844', width: 390, height: 844 },
  { name: '768x1024', width: 768, height: 1024 },
  { name: '1024x768', width: 1024, height: 768 },
  { name: '1280x720', width: 1280, height: 720 },
  { name: '1440x900', width: 1440, height: 900 },
] as const;

type Role = 'LOCATAIRE' | 'PROPRIETAIRE' | 'AGENT' | 'ADMIN';

function makeUser(role: Role) {
  return {
    id: `user-${role.toLowerCase()}-p2`,
    prenom: role === 'ADMIN' ? 'Cheikh' : role === 'AGENT' ? 'Fatou' : role === 'PROPRIETAIRE' ? 'Moussa' : 'Awa',
    nom: role === 'ADMIN' ? 'Ba' : role === 'AGENT' ? 'Sarr' : role === 'PROPRIETAIRE' ? 'Ndiaye' : 'Diop',
    email: `${role.toLowerCase()}@ecourtier.sn`,
    telephone: '+221771234567',
    role,
    actif: true,
    dateCreation: '2026-10-01T10:00:00Z',
  };
}

const SAMPLE_FAVORITES_PAGE0 = [
  {
    id: 'fav-1',
    titre: 'Studio lumineux Plateau disponible',
    description: 'Studio moderne équipé.',
    type: 'STUDIO',
    modeTransaction: 'LOCATION',
    prix: 225000,
    superficie: 0,
    nombrePieces: 1,
    quartier: 'Plateau',
    ville: 'Dakar',
    latitude: 14.667,
    longitude: -17.436,
    meuble: true,
    animauxAcceptes: false,
    statutVerification: 'VERIFIEE',
    statutPublication: 'DISPONIBLE',
    dateCreation: '2026-10-10T10:00:00Z',
    auteurRole: 'AGENT',
    auteur: makeUser('AGENT'),
    photos: [],
    equipements: ['WIFI'],
    proximites: ['TRANSPORT'],
    estFavori: true,
  },
  {
    id: 'fav-2',
    titre: 'Villa jardin Almadies déjà louée',
    description: 'Grande villa avec piscine.',
    type: 'VILLA',
    modeTransaction: 'LOCATION',
    prix: 1200000,
    superficie: 280,
    nombrePieces: 5,
    quartier: 'Almadies',
    ville: 'Dakar',
    latitude: 14.745,
    longitude: -17.518,
    meuble: true,
    animauxAcceptes: true,
    statutVerification: 'VERIFIEE',
    statutPublication: 'LOUEE',
    dateCreation: '2026-10-12T10:00:00Z',
    auteurRole: 'PROPRIETAIRE',
    auteur: makeUser('PROPRIETAIRE'),
    photos: [],
    equipements: ['PISCINE'],
    proximites: ['PLAGE'],
    estFavori: true,
  },
  {
    id: 'fav-3',
    titre: 'Appartement F4 VDN en vente',
    description: 'Bel appartement familial.',
    type: 'APPARTEMENT',
    modeTransaction: 'VENTE',
    prix: 85000000,
    superficie: 145,
    nombrePieces: 4,
    quartier: 'Sacré-Cœur',
    ville: 'Dakar',
    latitude: 14.716,
    longitude: -17.467,
    meuble: false,
    animauxAcceptes: false,
    statutVerification: 'EN_ATTENTE',
    statutPublication: 'VENDUE',
    dateCreation: '2026-10-14T10:00:00Z',
    auteurRole: 'PROPRIETAIRE',
    auteur: makeUser('PROPRIETAIRE'),
    photos: [],
    equipements: ['PARKING'],
    proximites: ['COMMERCE'],
    estFavori: true,
  },
];

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
}

async function setupBaseMocks(page: Page, role?: Role) {
  const externalBlocked: string[] = [];
  const apiCalls: string[] = [];
  const user = role ? makeUser(role) : null;

  await page.route('**/*', async (route) => {
    const reqUrl = route.request().url();
    const u = new URL(reqUrl);

    if (u.hostname !== '127.0.0.1' && u.hostname !== 'localhost') {
      externalBlocked.push(`${u.hostname}${u.pathname}`);
      return route.abort();
    }

    if (u.pathname.startsWith('/ws')) {
      return route.abort();
    }

    if (u.pathname.startsWith('/api/v1/')) {
      apiCalls.push(`${route.request().method()} ${u.pathname}${u.search}`);
      const json = (body: unknown, status = 200) =>
        route.fulfill({
          status,
          contentType: 'application/json',
          body: JSON.stringify(body),
        });

      if (u.pathname === '/api/v1/utilisateurs/moi') {
        return user ? json(user) : json({ error: 'Non authentifié' }, 401);
      }
      if (u.pathname === '/api/v1/utilisateurs/moi/favoris/count') {
        return json({ count: SAMPLE_FAVORITES_PAGE0.length });
      }
      if (u.pathname === '/api/v1/utilisateurs/moi/favoris') {
        return json({
          content: SAMPLE_FAVORITES_PAGE0,
          totalElements: SAMPLE_FAVORITES_PAGE0.length,
          totalPages: 1,
          size: 12,
          number: 0,
          first: true,
          last: true,
          empty: false,
        });
      }
      if (u.pathname.match(/^\/api\/v1\/utilisateurs\/[^/]+\/reputation$/)) {
        return json({ utilisateurId: user?.id || 'u', noteMoyenne: 4.8, nombreAvis: 12 });
      }
      if (u.pathname === '/api/v1/conversations/non-lus/count') {
        return json({ totalNonLus: 1 });
      }
      if (u.pathname === '/api/v1/conversations/mes-conversations') {
        return json([
          {
            id: 'conv-p2-1',
            annonceId: 'fav-1',
            annonceTitre: 'Studio lumineux Plateau disponible',
            locataire: makeUser('LOCATAIRE'),
            proprietaire: makeUser('PROPRIETAIRE'),
            nbMessagesNonLus: 1,
            dateCreation: '2026-11-18T10:00:00Z',
          },
        ]);
      }
      if (u.pathname === '/api/v1/conversations/conv-p2-1/messages/tous') {
        return json([]);
      }
      if (u.pathname === '/api/v1/annonces') {
        return json({ content: [], totalElements: 0, totalPages: 0, size: 12, number: 0, first: true, last: true, empty: true });
      }
      if (u.pathname === '/api/v1/rendez-vous/mes-rendez-vous') {
        return json([]);
      }
      if (u.pathname === '/api/v1/transactions/mes-transactions') {
        return json([]);
      }
      if (u.pathname === '/api/v1/references') {
        return json({ typesBien: [], equipements: [], proximites: [], modesTransaction: [], typesDocument: [] });
      }
      return json({});
    }

    return route.continue();
  });

  return { externalBlocked, apiCalls };
}

test.describe('Prompt 2/5 — Socle transverse, Auth & Espaces personnels', () => {
  test('1. Garde centralisée state.from, rôle ADMIN et redirections post-connexion', async ({ page }) => {
    await setupBaseMocks(page);

    // Visiteur anonyme sur /dashboard?tab=messages -> /connexion avec state.from préservé
    await page.goto(`${UI_URL}/dashboard?tab=messages`, { waitUntil: 'domcontentloaded' });
    await expect(page).toHaveURL(/\/connexion$/);

    // Soumission connexion PROPRIETAIRE -> restaure /dashboard?tab=messages
    await page.route('**/api/v1/auth/connexion', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          accessToken: 'jwt-prop',
          refreshToken: 'ref-prop',
          tokenType: 'Bearer',
          expiresIn: 900,
          utilisateur: makeUser('PROPRIETAIRE'),
        }),
      })
    );

    await page.getByLabel('Adresse Email').fill('proprietaire@ecourtier.sn');
    await page.getByLabel('Mot de passe').fill('Secret123!');
    await page.getByRole('button', { name: /Se connecter/i }).click();

    await expect(page).toHaveURL(/\/dashboard\?tab=messages/);

    // Sans state.from, ADMIN -> /admin/dashboard
    await page.route('**/api/v1/auth/connexion', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          accessToken: 'jwt-admin',
          refreshToken: 'ref-admin',
          tokenType: 'Bearer',
          expiresIn: 900,
          utilisateur: makeUser('ADMIN'),
        }),
      })
    );
    await page.goto(`${UI_URL}/connexion`, { waitUntil: 'domcontentloaded' });
    await page.getByLabel('Adresse Email').fill('admin@ecourtier.sn');
    await page.getByLabel('Mot de passe').fill('Secret123!');
    await page.getByRole('button', { name: /Se connecter/i }).click();
    await expect(page).toHaveURL(/\/admin\/dashboard/);

    // LOCATAIRE tentant /admin/utilisateurs -> redirigé proprement vers /recherche
    const ctxLoc = await page.context().browser()!.newContext();
    const pageLoc = await ctxLoc.newPage();
    await seedAuth(pageLoc, 'LOCATAIRE');
    await setupBaseMocks(pageLoc, 'LOCATAIRE');
    await pageLoc.goto(`${UI_URL}/admin/utilisateurs`, { waitUntil: 'domcontentloaded' });
    await expect(pageLoc).toHaveURL(/\/recherche/);
    await ctxLoc.close();
  });

  test('2. Connexion & Inscription: visuels locaux, toggle mot de passe, clavier & checklist DTO', async ({ page }) => {
    const { externalBlocked } = await setupBaseMocks(page);

    // /connexion
    await page.goto(`${UI_URL}/connexion`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(300);

    // Toggle password visibility
    const loginPwd = page.getByLabel('Mot de passe');
    await expect(loginPwd).toHaveAttribute('type', 'password');
    await page.getByRole('button', { name: 'Afficher la saisie' }).click();
    await expect(loginPwd).toHaveAttribute('type', 'text');

    // Mot de passe oublié: contact unifié et aucune mention universitaire
    await page.getByRole('button', { name: /Mot de passe oublié/i }).click();
    const bodyLogin = await page.locator('body').innerText();
    expect(bodyLogin).toContain('contact@e-courtier.sn');
    expect(bodyLogin.toLowerCase()).not.toContain('universitaire');

    // /inscription
    await page.goto(`${UI_URL}/inscription`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(300);

    // Téléphone non pré-rempli avec faux numéro
    const telInput = page.getByLabel('Téléphone (WhatsApp)');
    await expect(telInput).toHaveValue('');

    // Sélecteur de rôle clavier + aria-pressed
    const ownerRoleBtn = page.getByRole('button', { name: /Propriétaire/i });
    await expect(ownerRoleBtn).toHaveAttribute('aria-pressed', 'false');
    await ownerRoleBtn.focus();
    await page.keyboard.press('Enter');
    await expect(ownerRoleBtn).toHaveAttribute('aria-pressed', 'true');

    // Note agent conservée et aucun rôle public ADMIN/AGENT
    await expect(page.getByText(/Les comptes agents sont créés par l/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /Administrateur/i })).toHaveCount(0);

    // Checklist mot de passe + blocage soumission invalide
    const regPwd = page.getByLabel('Mot de passe');
    await regPwd.fill('abc');
    await page.getByLabel('Prénom').fill('Amina');
    await page.getByLabel('Nom', { exact: true }).fill('Diallo');
    await page.getByLabel('Adresse Email').fill('amina@ecourtier.sn');
    await telInput.fill('+221771112233');
    await page.getByRole('button', { name: /S'inscrire et commencer/i }).click();
    await expect(page.getByRole('alert')).toContainText(/8 caractères, une lettre majuscule et un chiffre/i);

    // Aucune requête Unsplash ou ui-avatars émise
    expect(externalBlocked.some((u) => u.includes('unsplash') || u.includes('ui-avatars'))).toBe(false);

    // Images locales chargées proprement (desktop split visible >= md)
    await page.setViewportSize({ width: 1280, height: 720 });
    await page.waitForTimeout(200);
    const brokenImgs = await page.evaluate(() =>
      Array.from(document.querySelectorAll('img')).filter((img) => img.naturalWidth === 0).length
    );
    expect(brokenImgs).toBe(0);
  });

  test('3. /mon-compte: sync /utilisateurs/moi, résilience erreur transitoire, avatars offline, libellés & sécurité', async ({ page }) => {
    await seedAuth(page, 'PROPRIETAIRE');
    const { externalBlocked } = await setupBaseMocks(page, 'PROPRIETAIRE');
    let moiSyncCalls = 0;
    let passwordChangedPayload: { ancienMotDePasse?: string; nouveauMotDePasse?: string } | null = null;

    // Surcharger /utilisateurs/moi pour retourner un prénom actualisé
    await page.route('**/api/v1/utilisateurs/moi', (route) => {
      moiSyncCalls++;
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          ...makeUser('PROPRIETAIRE'),
          prenom: 'Moussa Synchronisé',
        }),
      });
    });

    await page.route('**/api/v1/utilisateurs/moi/mot-de-passe', async (route) => {
      passwordChangedPayload = JSON.parse(route.request().postData() || '{}') as {
        ancienMotDePasse?: string;
        nouveauMotDePasse?: string;
      };
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Mot de passe modifié' }),
      });
    });

    await page.goto(`${UI_URL}/mon-compte`, { waitUntil: 'domcontentloaded' });

    // Sync /utilisateurs/moi appelée
    await expect.poll(() => moiSyncCalls).toBeGreaterThan(0);
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Moussa Synchronisé Ndiaye');

    // Libellé humain du rôle (jamais PROPRIETAIRE brut)
    await expect(page.getByText('Propriétaire', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('PROPRIETAIRE', { exact: true })).toHaveCount(0);

    // Statut & Réputation visibles pour PROPRIETAIRE
    await expect(page.getByText('Compte actif')).toBeVisible();
    await expect(page.getByText('4.8 / 5')).toBeVisible();
    await expect(page.getByText('(12 avis)')).toBeVisible();

    // Pas de badge vérifié falsifié sur l'avatar
    await expect(page.locator('[aria-label="Profil vérifié"]')).toHaveCount(0);

    // Raccourcis personnalisés PROPRIETAIRE (et jamais Administration)
    const main = page.locator('main');
    await expect(main.getByRole('link', { name: /Espace Partenaire/i })).toBeVisible();
    await expect(main.getByRole('link', { name: /Console Administration/i })).toHaveCount(0);

    // Consultation honnête & contact support unifié
    await expect(main.getByRole('link', { name: 'contact@e-courtier.sn' })).toBeVisible();
    await expect(main.getByText(/révocation de session/i)).toBeVisible();

    // Aucune requête ui-avatars ni image cassée
    expect(externalBlocked.some((u) => u.includes('ui-avatars'))).toBe(false);
    const brokenImgs = await page.evaluate(() =>
      Array.from(document.querySelectorAll('img')).filter((img) => img.naturalWidth === 0).length
    );
    expect(brokenImgs).toBe(0);

    // Résilience erreur 500 transitoire sur /utilisateurs/moi : ne déconnecte pas
    await page.route('**/api/v1/utilisateurs/moi', (route) =>
      route.fulfill({ status: 500, contentType: 'application/json', body: '{"error":"Erreur temporaire"}' })
    );
    await page.reload({ waitUntil: 'domcontentloaded' });
    await expect(page).toHaveURL(/\/mon-compte/);
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Moussa');

    // Validation & changement de mot de passe (confirmation + soumission PUT + révocation session -> /connexion)
    await page.getByLabel('Mot de passe actuel').fill('OldPass123');
    await page.getByLabel('Nouveau mot de passe', { exact: true }).fill('NewPass456');
    await page.getByLabel('Confirmer le nouveau mot de passe').fill('Mismatch1');
    await page.getByRole('button', { name: /Enregistrer le nouveau mot de passe/i }).click();
    await expect(page.getByRole('alert')).toContainText(/ne correspond pas/i);
    expect(passwordChangedPayload).toBeNull();

    await page.getByLabel('Confirmer le nouveau mot de passe').fill('NewPass456');
    await page.getByRole('button', { name: /Enregistrer le nouveau mot de passe/i }).click();
    await expect.poll(() => passwordChangedPayload?.nouveauMotDePasse).toBe('NewPass456');
    expect(passwordChangedPayload?.ancienMotDePasse).toBe('OldPass123');
    await expect(page).toHaveURL(/\/connexion$/);

    // LOCATAIRE ne voit ni réputation propriétaire ni liens admin
    const ctxLoc = await page.context().browser()!.newContext();
    const pageLoc = await ctxLoc.newPage();
    await seedAuth(pageLoc, 'LOCATAIRE');
    await setupBaseMocks(pageLoc, 'LOCATAIRE');
    await pageLoc.goto(`${UI_URL}/mon-compte`, { waitUntil: 'domcontentloaded' });
    await expect(pageLoc.getByText('Locataire / Acheteur', { exact: true }).first()).toBeVisible();
    await expect(pageLoc.getByText('LOCATAIRE', { exact: true })).toHaveCount(0);
    await expect(pageLoc.getByText(/Réputation sur la plateforme/i)).toHaveCount(0);
    await expect(pageLoc.locator('main').getByRole('link', { name: /Mes visites & dossiers/i })).toBeVisible();
    await expect(pageLoc.locator('main').getByRole('link', { name: /Console Administration/i })).toHaveCount(0);
    await ctxLoc.close();
  });

  test('4. /favoris: cartes harmonisées (0 m², 1 pièce, statuts), filtres rapides, rollback et recul de page', async ({ page }) => {
    await seedAuth(page, 'LOCATAIRE');
    const { apiCalls } = await setupBaseMocks(page, 'LOCATAIRE');

    // Simuler 2 pages (page 0: 3 items, page 1: 1 dernier item pour tester le retour page 0)
    let page1ItemPresent = true;
    let failNextDelete = true;

    await page.route('**/api/v1/utilisateurs/moi/favoris?*', async (route) => {
      const u = new URL(route.request().url());
      const p = Number(u.searchParams.get('page') || '0');
      if (p === 0) {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            content: SAMPLE_FAVORITES_PAGE0,
            totalElements: page1ItemPresent ? 4 : 3,
            totalPages: page1ItemPresent ? 2 : 1,
            size: 12,
            number: 0,
            first: true,
            last: !page1ItemPresent,
            empty: false,
          }),
        });
      }
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: page1ItemPresent
            ? [{ ...SAMPLE_FAVORITES_PAGE0[0], id: 'fav-page1-last', titre: 'Bien isolé page 2' }]
            : [],
          totalElements: page1ItemPresent ? 4 : 3,
          totalPages: page1ItemPresent ? 2 : 1,
          size: 12,
          number: 1,
          first: false,
          last: true,
          empty: !page1ItemPresent,
        }),
      });
    });

    await page.route('**/api/v1/annonces/*/favoris', async (route) => {
      if (route.request().method() === 'DELETE') {
        if (failNextDelete) {
          failNextDelete = false;
          return route.fulfill({ status: 500, contentType: 'application/json', body: '{"error":"Erreur serveur"}' });
        }
        page1ItemPresent = false;
        return route.fulfill({ status: 204, body: '' });
      }
      return route.fulfill({ status: 201, body: '' });
    });

    await page.goto(`${UI_URL}/favoris`, { waitUntil: 'domcontentloaded' });

    // Attendre chargement effectif des favoris (chunk lazy + React Query)
    await expect(page.getByText('Studio lumineux Plateau disponible')).toBeVisible();

    // Header utilise bien /favoris/count
    expect(apiCalls.some((c) => c.includes('/api/v1/utilisateurs/moi/favoris/count'))).toBe(true);

    // Zéro "0 m²", singulier "1 pièce", statuts Louée / Vendue présents
    const bodyText = await page.locator('body').innerText();
    expect(bodyText).not.toMatch(/\b0 m²/);
    expect(bodyText).toContain('1 pièce');
    expect(bodyText).not.toContain('1 pièces');
    await expect(page.getByText('Louée', { exact: true })).toBeVisible();
    await expect(page.getByText('Vendue', { exact: true })).toBeVisible();

    // Filtres rapides
    await page.getByRole('button', { name: /Disponibles uniquement/i }).click();
    await expect(page.getByText('Studio lumineux Plateau disponible')).toBeVisible();
    await expect(page.getByText('Villa jardin Almadies déjà louée')).toHaveCount(0);

    await page.getByRole('button', { name: /Vente/i }).click();
    await expect(page.getByText('Appartement F4 VDN en vente')).toBeVisible();

    await page.getByRole('button', { name: /Tous/i }).click();

    // Aller en page 2 -> rollback sur échec DELETE -> puis suppression réussie recule en page 1
    await page.getByRole('button', { name: /Suivant/i }).click();
    await expect(page.getByText('Bien isolé page 2')).toBeVisible();
    await expect(page.getByText('Page 2 sur 2')).toBeVisible();

    // 1er clic échoue (500) -> rollback conserve le bien en page 2
    await page.getByRole('button', { name: 'Retirer des favoris' }).first().click();
    await expect(page.getByText('Bien isolé page 2')).toBeVisible();

    // 2e clic réussit -> revient automatiquement à Page 1 sur 1
    await page.getByRole('button', { name: 'Retirer des favoris' }).first().click();
    await expect(page.getByText('Studio lumineux Plateau disponible')).toBeVisible();
  });

  test('5. Résilience ErrorBoundary & garde Array.isArray dans useConversationSocket (zéro page blanche)', async ({ page }) => {
    await seedAuth(page, 'PROPRIETAIRE');
    await setupBaseMocks(page, 'PROPRIETAIRE');

    // Simuler une réponse REST corrompue (objet au lieu d'un tableau)
    await page.route('**/api/v1/conversations/conv-p2-1/messages/tous', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: [], unexpectedObject: true }),
      })
    );

    const pageErrors: string[] = [];
    page.on('pageerror', (err) => pageErrors.push(err.message));

    await page.goto(`${UI_URL}/dashboard?tab=messages`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(450);

    await page.getByRole('button', { name: /Awa Diop/i }).first().click();
    await page.waitForTimeout(350);

    await expect(page.getByRole('banner')).toBeVisible();
    expect(pageErrors).toEqual([]);
  });

  test('6. Matrice responsive 6 viewports: 0 overflow, 0 collision Samba IA/Footer & cibles tactiles >= 44px', async ({ page }) => {
    test.setTimeout(90000);
    const scenarios = [
      { path: '/connexion', auth: null as Role | null, readyText: 'Entrez vos identifiants pour accéder à votre compte E-Courtier.' },
      { path: '/inscription', auth: null as Role | null, readyText: 'Créer votre compte' },
      { path: '/mon-compte', auth: 'PROPRIETAIRE' as Role, readyText: 'Identité' },
      { path: '/favoris', auth: 'LOCATAIRE' as Role, readyText: 'Studio lumineux Plateau disponible' },
    ];

    for (const sc of scenarios) {
      for (const vp of VIEWPORTS) {
        const ctx = await page.context().browser()!.newContext({ viewport: { width: vp.width, height: vp.height } });
        const p = await ctx.newPage();
        if (sc.auth) {
          await seedAuth(p, sc.auth);
        }
        await setupBaseMocks(p, sc.auth ?? undefined);

        const pageErrors: string[] = [];
        p.on('pageerror', (err) => pageErrors.push(err.message));

        await p.goto(`${UI_URL}${sc.path}`, { waitUntil: 'domcontentloaded' });
        await expect(p.getByText(sc.readyText).first()).toBeVisible();

        await p.waitForLoadState('load');
        const check = await p.evaluate(async (isMobile) => {
          await document.fonts.ready;
          // Attendre la stabilisation de la hauteur (images/polices) avant toute mesure.
          let lastHeight = -1;
          for (let i = 0; i < 40; i++) {
            const h = document.documentElement.scrollHeight;
            if (h === lastHeight) break;
            lastHeight = h;
            await new Promise((resolve) => setTimeout(resolve, 150));
          }

          const vw = window.innerWidth;
          const overflowTop = document.documentElement.scrollWidth - vw;

          window.scrollTo({ top: document.documentElement.scrollHeight, behavior: 'instant' as ScrollBehavior });
          await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
          const overflowBottom = document.documentElement.scrollWidth - vw;
          const samba = document.querySelector('button[aria-label*="assistant IA" i]')?.getBoundingClientRect();

          let sambaOverlapCount = 0;
          if (samba) {
            const bottomClearanceTargets = document.querySelectorAll('footer a, footer p, form button[type="submit"], form a');
            for (const el of bottomClearanceTargets) {
              const st = getComputedStyle(el);
              if (st.display === 'none' || st.visibility === 'hidden') continue;
              const r = el.getBoundingClientRect();
              if (r.width === 0 || r.height === 0) continue;
              const intersects = !(r.right <= samba.left || r.left >= samba.right || r.bottom <= samba.top || r.top >= samba.bottom);
              if (intersects) {
                sambaOverlapCount++;
              }
            }
          }

          const smallMobileTargets: string[] = [];
          if (isMobile) {
            const controls = document.querySelectorAll('main button, main a[class*="inline-flex"], main a[class*="flex"], form button, form a');
            for (const el of controls) {
              const st = getComputedStyle(el);
              if (st.display === 'none' || st.visibility === 'hidden') continue;
              const r = el.getBoundingClientRect();
              if (r.width === 0 || r.height === 0) continue;
              if (r.height < 43.5) {
                smallMobileTargets.push(`${el.tagName}:${(el.textContent || el.getAttribute('aria-label') || '').trim().slice(0, 30)}=${Math.round(r.height)}px`);
              }
            }
          }

          return {
            overflow: Math.max(overflowTop, overflowBottom),
            sambaOverlapCount,
            smallMobileTargets,
          };
        }, vp.width <= 390);

        expect(pageErrors, `pageErrors on ${sc.path} @ ${vp.name}`).toEqual([]);
        expect(check.overflow, `horizontal overflow on ${sc.path} @ ${vp.name}`).toBeLessThanOrEqual(1);
        expect(check.sambaOverlapCount, `Samba IA bottom/footer overlap on ${sc.path} @ ${vp.name}`).toBe(0);
        expect(check.smallMobileTargets, `mobile touch targets < 44px on ${sc.path} @ ${vp.name}`).toEqual([]);

        await ctx.close();
      }
    }
  });
});
