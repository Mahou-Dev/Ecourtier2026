import { test, expect } from '@playwright/test';
import path from 'path';

const SCREENSHOTS_DIR = path.resolve(process.cwd(), '../docs/audit/screenshots');

test.describe('Captures d\'écran pour l\'audit C2, F26, F27, T2', () => {

  test('Captures 25 à 30', async ({ page }) => {
    test.setTimeout(90000);
    // 1. Capture 28 : /inscription sans option Agent
    await page.goto('/inscription');
    await page.waitForSelector('text=Les comptes agents sont créés par l\'administrateur');
    await page.screenshot({
      path: path.join(SCREENSHOTS_DIR, '28_inscription_sans_agent_f27.png'),
      fullPage: true,
    });

    // 2. Connexion ADMIN
    await page.goto('/connexion');
    await page.fill('input[type="email"]', 'admin@ecourtier.sn');
    await page.fill('input[type="password"]', 'AdminPass123!');
    await page.click('button[type="submit"]');
    await page.waitForURL(/\/dashboard|\/recherche/);

    // 3. Capture 25 : /admin/utilisateurs (table, badges, boutons)
    await page.goto('/admin/utilisateurs');
    await page.waitForSelector('h1:has-text("Gestion des Utilisateurs")');
    await page.waitForSelector('button:has-text("Créer un compte")');
    await page.screenshot({
      path: path.join(SCREENSHOTS_DIR, '25_admin_utilisateurs_f26.png'),
      fullPage: true,
    });

    // 4. Capture 26 : Modal Création d'agent et mot de passe temporaire
    await page.click('button:has-text("Créer un compte")');
    await page.waitForSelector('text=Créer un compte utilisateur');
    await page.fill('input[placeholder="Ex: Fatou"]', 'Cheikh');
    await page.fill('input[placeholder="Ex: Sarr"]', 'Toure');
    await page.fill('input[placeholder="agent.sarr@ecourtier.sn"]', `cheikh.agent.${Date.now()}@ecourtier.sn`);
    await page.locator('input[type="tel"]').fill('+221771122334');
    await page.selectOption('select', 'AGENT');
    await page.click('button:has-text("Créer le compte")');

    // Attendre le modal de mot de passe temporaire
    await page.waitForSelector('text=Compte créé — Mot de passe temporaire');
    await page.waitForSelector('text=Affichage unique — Copiez ce mot de passe maintenant');
    await page.screenshot({
      path: path.join(SCREENSHOTS_DIR, '26_admin_create_agent_modal_f27.png'),
    });

    // Fermer le modal
    await page.click('button:has-text("J\'ai noté le mot de passe")');

    // 5. Capture 27 : /mon-compte (profil et changement mot de passe)
    await page.goto('/mon-compte');
    await page.waitForSelector('text=Mon compte');
    await page.waitForSelector('h2:has-text("Changer mon mot de passe")');
    await page.screenshot({
      path: path.join(SCREENSHOTS_DIR, '27_mon_compte_mot_de_passe_f27.png'),
      fullPage: true,
    });

    // 6. Capture 29 : Annonce avec badge Agent immobilier
    await page.goto('/annonce/e7088f21-80e1-4301-b1e1-4128f96bf6ba');
    await page.waitForSelector('text=Publié par un agent immobilier');
    await page.screenshot({
      path: path.join(SCREENSHOTS_DIR, '29_annonce_agent_badge_f27.png'),
      fullPage: true,
    });

    // 7. Capture 30 : /admin/verification (aperçu document PDF & alertes)
    await page.goto('/admin/verification');
    await page.waitForSelector('h1:has-text("Espace Modération Administrateur")');
    await page.waitForSelector('text=titre_foncier.pdf');
    await page.screenshot({
      path: path.join(SCREENSHOTS_DIR, '30_admin_verification_pdf_c2.png'),
      fullPage: true,
    });
  });

});
