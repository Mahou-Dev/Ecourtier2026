import { test, expect } from '@playwright/test';

test.describe('Filtres de recherche (F7 à F13, F14, F28)', () => {

  test('1. Sans filtre : la liste contient LOCATION et VENTE, et la villa à 45 000 000 apparaît', async ({ page }) => {
    // Intercepter la requête /api/v1/annonces
    let apiRequestUrl = '';
    await page.route('**/api/v1/annonces?*', async (route) => {
      apiRequestUrl = route.request().url();
      await route.continue();
    });

    await page.goto('/recherche');
    await page.waitForSelector('h1:has-text("Logements Disponibles")');

    // Vérifier l'URL dans le navigateur : aucun paramètre de filtre restrictif
    expect(page.url()).not.toContain('budgetMax=');
    expect(page.url()).not.toContain('modeTransaction=');

    // Vérifier les paramètres envoyés à l'API : pas de plafond par défaut, pas de mode imposé
    expect(apiRequestUrl).toContain('/api/v1/annonces?');
    const urlObj = new URL(apiRequestUrl);
    expect(urlObj.searchParams.get('budgetMax')).toBeNull();
    expect(urlObj.searchParams.get('modeTransaction')).toBeNull();

    // Vérifier que la liste contient des annonces de Vente ET de Location
    await expect(page.locator('text=45 000 000 FCFA').first()).toBeVisible({ timeout: 10000 });
    await expect(page.locator('span:text-is("Vente")').first()).toBeVisible();
    await expect(page.locator('span:text-is("Location")').first()).toBeVisible();
  });

  test('2. Filtre Mode Transaction : VENTE via bouton d\'action rapide', async ({ page }) => {
    let interceptedUrl = '';
    await page.route('**/api/v1/annonces?*', async (route) => {
      interceptedUrl = route.request().url();
      await route.continue();
    });

    await page.goto('/recherche');
    await page.waitForSelector('h1:has-text("Logements Disponibles")');

    // Clic sur le bouton de mode "Vente"
    const venteBtn = page.locator('button:text-is("Vente")').first();
    await venteBtn.click();

    // Vérifier la mise à jour de l'URL
    await expect(page).toHaveURL(/modeTransaction=VENTE/);

    // Vérifier le paramètre envoyé à l'API
    await page.waitForTimeout(500);
    const urlObj = new URL(interceptedUrl);
    expect(urlObj.searchParams.get('modeTransaction')).toBe('VENTE');

    // Résultat affiché : pastille Vente présente, la villa à 45M est visible
    await expect(page.locator('span:has-text("Filtres actifs :")')).toBeVisible();
    await expect(page.locator('span:text-is("Vente")').first()).toBeVisible();
    await expect(page.locator('text=45 000 000 FCFA').first()).toBeVisible();
  });

  test('3. Filtre Type de bien : VILLA', async ({ page }) => {
    let interceptedUrl = '';
    await page.route('**/api/v1/annonces?*', async (route) => {
      interceptedUrl = route.request().url();
      await route.continue();
    });

    await page.goto('/recherche?type=VILLA');
    await page.waitForSelector('h1:has-text("Logements Disponibles")');

    // Vérifier URL
    expect(page.url()).toContain('type=VILLA');

    // Vérifier requête API
    const urlObj = new URL(interceptedUrl);
    expect(urlObj.searchParams.getAll('type')).toContain('VILLA');

    // Pastille présente et annonce Villa présente
    await expect(page.locator('span:text-is("Villa")').first()).toBeVisible();
    await expect(page.locator('span:text-is("VILLA")').first()).toBeVisible();
  });

  test('4. Filtre Type de bien : Choix multiple (VILLA & APPARTEMENT)', async ({ page }) => {
    let interceptedUrl = '';
    await page.route('**/api/v1/annonces?*', async (route) => {
      interceptedUrl = route.request().url();
      await route.continue();
    });

    await page.goto('/recherche?type=VILLA&type=APPARTEMENT');
    await page.waitForSelector('h1:has-text("Logements Disponibles")');

    // Vérifier URL
    expect(page.url()).toContain('type=VILLA');
    expect(page.url()).toContain('type=APPARTEMENT');

    // Vérifier requête API
    const urlObj = new URL(interceptedUrl);
    const types = urlObj.searchParams.getAll('type');
    expect(types).toContain('VILLA');
    expect(types).toContain('APPARTEMENT');

    // Pastilles affichées pour les deux types
    await expect(page.locator('span:text-is("Villa")').first()).toBeVisible();
    await expect(page.locator('span:text-is("Appartement")').first()).toBeVisible();
  });

  test('5. Filtre Budget Min et Max', async ({ page }) => {
    let interceptedUrl = '';
    await page.route('**/api/v1/annonces?*', async (route) => {
      interceptedUrl = route.request().url();
      await route.continue();
    });

    await page.goto('/recherche?budgetMin=150000&budgetMax=500000');
    await page.waitForSelector('h1:has-text("Logements Disponibles")');

    // Vérifier URL
    expect(page.url()).toContain('budgetMin=150000');
    expect(page.url()).toContain('budgetMax=500000');

    // Vérifier requête API
    const urlObj = new URL(interceptedUrl);
    expect(urlObj.searchParams.get('budgetMin')).toBe('150000');
    expect(urlObj.searchParams.get('budgetMax')).toBe('500000');

    // Pastilles de budget
    await expect(page.locator('text=Min : 150\u202f000 FCFA').or(page.locator('text=Min : 150 000 FCFA'))).toBeVisible();
    await expect(page.locator('text=Max : 500\u202f000 FCFA').or(page.locator('text=Max : 500 000 FCFA'))).toBeVisible();
  });

  test('6. Filtre Superficie minimale', async ({ page }) => {
    let interceptedUrl = '';
    await page.route('**/api/v1/annonces?*', async (route) => {
      interceptedUrl = route.request().url();
      await route.continue();
    });

    await page.goto('/recherche?superficieMin=250');
    await page.waitForSelector('h1:has-text("Logements Disponibles")');

    // Vérifier URL et API
    expect(page.url()).toContain('superficieMin=250');
    const urlObj = new URL(interceptedUrl);
    expect(urlObj.searchParams.get('superficieMin')).toBe('250');

    // Pastille
    await expect(page.locator('text=≥ 250 m²')).toBeVisible();
  });

  test('7. Filtre Nombre de pièces', async ({ page }) => {
    let interceptedUrl = '';
    await page.route('**/api/v1/annonces?*', async (route) => {
      interceptedUrl = route.request().url();
      await route.continue();
    });

    await page.goto('/recherche?nombrePieces=4');
    await page.waitForSelector('h1:has-text("Logements Disponibles")');

    // Vérifier URL et API
    expect(page.url()).toContain('nombrePieces=4');
    const urlObj = new URL(interceptedUrl);
    expect(urlObj.searchParams.get('nombrePieces')).toBe('4');

    // Pastille
    await expect(page.locator('text=4+ pièces')).toBeVisible();
  });

  test('8. Filtre Quartier (autocomplétion / URL)', async ({ page }) => {
    let interceptedUrl = '';
    await page.route('**/api/v1/annonces?*', async (route) => {
      interceptedUrl = route.request().url();
      await route.continue();
    });

    await page.goto('/recherche?quartier=Nema');
    await page.waitForSelector('h1:has-text("Logements Disponibles")');

    // Vérifier URL et API
    expect(page.url()).toContain('quartier=Nema');
    const urlObj = new URL(interceptedUrl);
    expect(urlObj.searchParams.get('quartier')).toBe('Nema');

    // Pastille et annonce correspondante
    await expect(page.locator('text=Quartier : Nema')).toBeVisible();
    await expect(page.locator('text=Belle Villa Familiale Ziguinchor Nema')).toBeVisible();
  });

  test('9. Filtre Proximités (cases à cocher / URL)', async ({ page }) => {
    let interceptedUrl = '';
    await page.route('**/api/v1/annonces?*', async (route) => {
      interceptedUrl = route.request().url();
      await route.continue();
    });

    await page.goto('/recherche?proximite=PLAGE');
    await page.waitForSelector('h1:has-text("Logements Disponibles")');

    // Vérifier URL et API
    expect(page.url()).toContain('proximite=PLAGE');
    const urlObj = new URL(interceptedUrl);
    expect(urlObj.searchParams.getAll('proximite')).toContain('PLAGE');

    // Pastille affichée
    await expect(page.locator('text=Plage / Mer').or(page.locator('text=PLAGE'))).toBeVisible();
  });

  test('10. Filtre Meublé', async ({ page }) => {
    let interceptedUrl = '';
    await page.route('**/api/v1/annonces?*', async (route) => {
      interceptedUrl = route.request().url();
      await route.continue();
    });

    await page.goto('/recherche?meuble=true');
    await page.waitForSelector('h1:has-text("Logements Disponibles")');

    // Vérifier URL et API
    expect(page.url()).toContain('meuble=true');
    const urlObj = new URL(interceptedUrl);
    expect(urlObj.searchParams.get('meuble')).toBe('true');

    // Pastille affichée
    await expect(page.locator('span:text-is("Meublé")').first()).toBeVisible();
  });

  test('11. Filtre Animaux acceptés', async ({ page }) => {
    let interceptedUrl = '';
    await page.route('**/api/v1/annonces?*', async (route) => {
      interceptedUrl = route.request().url();
      await route.continue();
    });

    await page.goto('/recherche?animauxAcceptes=true');
    await page.waitForSelector('h1:has-text("Logements Disponibles")');

    // Vérifier URL et API
    expect(page.url()).toContain('animauxAcceptes=true');
    const urlObj = new URL(interceptedUrl);
    expect(urlObj.searchParams.get('animauxAcceptes')).toBe('true');

    // Pastille affichée
    await expect(page.locator('text=Animaux acceptés').first()).toBeVisible();
  });

  test('12. Chaque pastille se supprime au clic', async ({ page }) => {
    await page.goto('/recherche?modeTransaction=VENTE');
    await page.waitForSelector('h1:has-text("Logements Disponibles")');

    // La pastille "Vente" est présente
    const removeChipBtn = page.locator('[data-testid="remove-chip-mode"]');
    await expect(removeChipBtn).toBeVisible();

    // Clic pour supprimer la pastille
    await removeChipBtn.click();

    // La pastille doit disparaître et l'URL ne doit plus contenir modeTransaction=VENTE
    await expect(removeChipBtn).not.toBeVisible();
    expect(page.url()).not.toContain('modeTransaction=VENTE');
  });

  test('13. Le bouton Retour du navigateur restaure les filtres', async ({ page }) => {
    // 1. Démarrer sur /recherche sans filtre
    await page.goto('/recherche');
    await page.waitForSelector('h1:has-text("Logements Disponibles")');
    expect(page.url()).not.toContain('modeTransaction=VENTE');

    // 2. Cliquer sur "Vente"
    const venteBtn = page.locator('button:text-is("Vente")').first();
    await venteBtn.click();
    await expect(page).toHaveURL(/modeTransaction=VENTE/);
    await expect(page.locator('[data-testid="remove-chip-mode"]')).toBeVisible();

    // 3. Bouton Retour du navigateur
    await page.goBack();

    // 4. L'URL et l'interface reviennent à l'état sans filtre
    await expect(page).not.toHaveURL(/modeTransaction=VENTE/);
    await expect(page.locator('[data-testid="remove-chip-mode"]')).not.toBeVisible();
    await expect(page.locator('span:text-is("Location")').first()).toBeVisible();
  });

});
