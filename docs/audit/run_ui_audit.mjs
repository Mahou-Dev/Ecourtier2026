import fs from 'fs';
import path from 'path';
import { createRequire } from 'module';

const require = createRequire('/home/mahou-dev/.npm/_npx/0f94ee7615faf582/package.json');
const puppeteer = require('puppeteer-core');

const CHROME_PATH = '/home/mahou-dev/.cache/ms-playwright/chromium-1248/chrome-linux64/chrome';
const BASE_URL = 'http://localhost:3000';
const OUT_DIR = path.resolve('docs/audit/screenshots');
const UI_ANNONCE_ID = '958b59d1-12af-4412-b319-f20f24dfcb87';
const AVIS_ANNONCE_ID = 'ae188335-e6be-4a0f-a635-4aeda107cb36';

fs.mkdirSync(OUT_DIR, { recursive: true });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function checkOverflow(page) {
  return page.evaluate(() => ({
    innerWidth: window.innerWidth,
    innerHeight: window.innerHeight,
    docScrollWidth: document.documentElement.scrollWidth,
    bodyScrollWidth: document.body.scrollWidth,
    overflowPx: Math.max(document.documentElement.scrollWidth, document.body.scrollWidth) - window.innerWidth,
    hasHorizontalOverflow: Math.max(document.documentElement.scrollWidth, document.body.scrollWidth) > window.innerWidth + 1,
  }));
}

async function main() {
  const results = { executedAt: new Date().toISOString(), mobile360: {}, uiChecks: {}, screenshots: [] };

  const browser = await puppeteer.launch({
    executablePath: CHROME_PATH,
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
  });

  try {
    const page = await browser.newPage();

    const snap = async (filename, fullPage = false) => {
      const p = path.join(OUT_DIR, filename);
      await page.screenshot({ path: p, fullPage });
      results.screenshots.push(`docs/audit/screenshots/${filename}`);
      console.log('Saved', filename);
    };

    // 1. Accueil Mobile 360px (T3)
    await page.setViewport({ width: 360, height: 800, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
    await page.goto(`${BASE_URL}/`, { waitUntil: 'networkidle2', timeout: 30000 });
    await sleep(800);
    results.mobile360.accueil = await checkOverflow(page);
    await snap('01_accueil_mobile_360_t3.png', false);

    // 2. Chatbot React sur Desktop (S6, F6)
    await page.setViewport({ width: 1440, height: 900, deviceScaleFactor: 1 });
    await page.goto(`${BASE_URL}/`, { waitUntil: 'networkidle2', timeout: 30000 });
    await sleep(600);
    await page.evaluate(() => {
      const btn = Array.from(document.querySelectorAll('button')).find((b) => b.textContent?.includes('Assistant IA'));
      if (btn) btn.click();
    });
    await sleep(500);
    const chipClicked = await page.evaluate(() => {
      const chip = Array.from(document.querySelectorAll('button')).find((b) => b.textContent?.includes('Appartement 3 pièces'));
      if (chip) {
        chip.click();
        return true;
      }
      return false;
    });
    if (chipClicked) {
      await sleep(3000);
    }
    results.uiChecks.chatbotReact = await page.evaluate(() => {
      const links = Array.from(document.querySelectorAll('a[href^="/annonce/"]')).map((a) => a.getAttribute('href'));
      return {
        widgetOpen: document.body.innerText.includes('Samba — IA Immobilier'),
        shortlistCardsRendered: links.length,
      };
    });
    await snap('02_chatbot_react_s6.png', false);

    // 3. Recherche + Carte Leaflet (F4) + Filtres UI (F7-F13)
    await page.goto(`${BASE_URL}/recherche`, { waitUntil: 'networkidle2', timeout: 30000 });
    await sleep(1200);
    results.uiChecks.recherche = await page.evaluate(() => {
      const imgs = Array.from(document.querySelectorAll('main img, section img, div img')).filter((i) => !i.closest('.leaflet-pane'));
      const lazyCount = imgs.filter((i) => i.getAttribute('loading') === 'lazy').length;
      return {
        leafletPresent: !!document.querySelector('.leaflet-container'),
        leafletMarkers: document.querySelectorAll('.leaflet-marker-icon, .custom-price-marker, .leaflet-interactive').length,
        cardsImagesTotal: imgs.length,
        cardsImagesLazyCount: lazyCount,
        visibleTextSample: document.body.innerText.slice(0, 400),
      };
    });
    await snap('03_recherche_carte_leaflet_f4.png', false);

    // Open Filter Drawer
    await page.evaluate(() => {
      const btn = Array.from(document.querySelectorAll('button')).find((b) => b.textContent?.includes('Filtres'));
      if (btn) btn.click();
    });
    await sleep(700);
    results.uiChecks.rechercheFiltresDrawer = await page.evaluate(() => {
      const dialog = document.querySelector('[role="dialog"]') || document.body;
      const text = dialog.innerText;
      const labels = Array.from(dialog.querySelectorAll('label')).map((l) => l.innerText.trim());
      const selects = Array.from(dialog.querySelectorAll('select')).map((s) => ({
        value: s.value,
        options: Array.from(s.options).map((o) => ({ value: o.value, label: o.text })),
      }));
      const sliders = Array.from(dialog.querySelectorAll('[role="slider"]')).map((el) => ({
        min: el.getAttribute('aria-valuemin'),
        max: el.getAttribute('aria-valuemax'),
        now: el.getAttribute('aria-valuenow'),
      }));
      const checkboxes = Array.from(dialog.querySelectorAll('input[type="checkbox"]')).map((c) => ({
        checked: c.checked,
        parentText: c.parentElement?.innerText?.trim(),
      }));
      return {
        labels,
        selects,
        sliders,
        checkboxes,
        hasSurfaceInput: /surface|superficie|m²/i.test(text),
        hasPiecesInput: /nombre de pièces|pièces|chambres/i.test(text),
        hasQuartierInput: /quartier/i.test(text),
        hasNonMeubleFilter: /non[- ]meublé/i.test(text),
      };
    });
    await snap('04_recherche_filtres_f7_f13.png', false);

    // Close drawer & check mobile 360 on /recherche
    await page.keyboard.press('Escape');
    await sleep(300);
    await page.setViewport({ width: 360, height: 800, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
    await sleep(600);
    results.mobile360.recherche = await checkOverflow(page);
    await snap('05_recherche_mobile_360_t3.png', false);

    // 4. Fiche annonce multi-photos + badge + visite 360 + équipements check (F2, F3, F4, F5, C2, S2, S5)
    await page.setViewport({ width: 1440, height: 900, deviceScaleFactor: 1 });
    await page.goto(`${BASE_URL}/annonce/${UI_ANNONCE_ID}`, { waitUntil: 'networkidle2', timeout: 30000 });
    await sleep(1200);
    results.uiChecks.propertyDetail = await page.evaluate(() => {
      const bodyText = document.body.innerText;
      const imgs = Array.from(document.querySelectorAll('img')).filter((i) => !i.closest('.leaflet-pane'));
      return {
        hasVerifiedBadge: /Annonce Vérifiée|Vérifiée/i.test(bodyText),
        has360Button: Array.from(document.querySelectorAll('button')).some((b) => /360/i.test(b.innerText)),
        hasLeafletMap: !!document.querySelector('.leaflet-container'),
        galleryThumbButtons: document.querySelectorAll('button img').length,
        lazyImagesCount: imgs.filter((i) => i.getAttribute('loading') === 'lazy').length,
        totalContentImages: imgs.length,
        rendersEquipementClimatisation: /climatisation/i.test(bodyText) && !/avec climatisation/i.test(bodyText.replace(/Appartement F4[^\n]+/g, '')),
        rendersEquipementReserveEau: /réserve d'eau|reserve_eau|reserve eau/i.test(bodyText),
        rendersEquipementSectionHeading: /équipements/i.test(bodyText),
        rendersProximites: /ECOLE|HOPITAL|TRANSPORT/i.test(bodyText),
      };
    });
    await snap('06_fiche_annonce_galerie_badge_c2_f2.png', true);

    // Open Visite 360 modal
    await page.evaluate(() => {
      const btn = Array.from(document.querySelectorAll('button')).find((b) => /360/i.test(b.innerText));
      if (btn) btn.click();
    });
    await sleep(800);
    results.uiChecks.visite360ModalOpen = await page.evaluate(() => !!document.querySelector('iframe'));
    await snap('07_fiche_annonce_visite360_f3.png', false);
    await page.keyboard.press('Escape');
    await sleep(300);

    // Mobile 360 on Fiche Annonce (T3)
    await page.setViewport({ width: 360, height: 800, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
    await sleep(600);
    results.mobile360.ficheAnnonce = await checkOverflow(page);
    await snap('09_fiche_annonce_mobile_360_t3.png', false);

    // Fiche annonce avec Avis & Réputation (F20, F21, F22)
    await page.setViewport({ width: 1440, height: 900, deviceScaleFactor: 1 });
    await page.goto(`${BASE_URL}/annonce/${AVIS_ANNONCE_ID}`, { waitUntil: 'networkidle2', timeout: 30000 });
    await sleep(1000);
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight * 0.55));
    await sleep(500);
    results.uiChecks.avisAndSignalement = await page.evaluate(() => ({
      hasReviewComment: document.body.innerText.includes('Excellent appartement conforme aux photos'),
      hasSignalementButton: Array.from(document.querySelectorAll('button')).some((b) => /signaler/i.test(b.innerText)),
      hasReputationStars: /5\.0|avis/i.test(document.body.innerText),
    }));
    await snap('08_fiche_annonce_avis_f20.png', false);

    // 5. Inscription Page - Agent Role Card (F27) + Connexion mobile check
    await page.goto(`${BASE_URL}/inscription`, { waitUntil: 'networkidle2', timeout: 30000 });
    await sleep(600);
    await page.evaluate(() => {
      const card = Array.from(document.querySelectorAll('h4')).find((h) => h.innerText.includes('Courtier / Agent'));
      if (card) card.click();
    });
    await sleep(400);
    await snap('10_inscription_role_agent_f27.png', false);

    await page.setViewport({ width: 360, height: 800, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
    await page.goto(`${BASE_URL}/connexion`, { waitUntil: 'networkidle2', timeout: 30000 });
    await sleep(500);
    results.mobile360.connexion = await checkOverflow(page);

    // 6. Login as PROPRIETAIRE -> Dashboard (F14, F15, F23, F24, F25)
    await page.setViewport({ width: 1440, height: 900, deviceScaleFactor: 1 });
    await page.goto(`${BASE_URL}/connexion`, { waitUntil: 'networkidle2', timeout: 30000 });
    await sleep(500);
    await page.type('input[type="email"]', 'proprio.demo@ecourtier.sn');
    await page.type('input[type="password"]', process.env.AUDIT_PROPRIO_PASSWORD || '');
    await page.click('button[type="submit"]');
    await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 15000 }).catch(() => {});
    await sleep(1200);
    await page.goto(`${BASE_URL}/dashboard`, { waitUntil: 'networkidle2', timeout: 30000 });
    await sleep(1200);

    results.uiChecks.dashboardProprio = await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('button')).map((b) => b.innerText.trim());
      return {
        buttons: btns,
        hasEditAnnonceButton: btns.some((t) => /modifier|éditer|edit/i.test(t)),
        hasDeleteAnnonceButton: btns.some((t) => /supprimer|retirer|delete/i.test(t)),
      };
    });
    await snap('11_dashboard_proprietaire_annonces_f23.png', false);

    // Mobile 360 check on /dashboard
    await page.setViewport({ width: 360, height: 800, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
    await sleep(500);
    results.mobile360.dashboard = await checkOverflow(page);
    await page.setViewport({ width: 1440, height: 900, deviceScaleFactor: 1 });
    await sleep(400);

    // Open CreateAnnonceModal -> inspect F14 & F5
    await page.evaluate(() => {
      const btn = Array.from(document.querySelectorAll('button')).find((b) => /publier un bien|nouvelle annonce/i.test(b.innerText));
      if (btn) btn.click();
    });
    await sleep(700);
    results.uiChecks.createAnnonceModal = await page.evaluate(() => {
      const dialog = document.querySelector('[role="dialog"]');
      if (!dialog) return { opened: false };
      dialog.scrollTop = dialog.scrollHeight;
      const scrollable = dialog.querySelector('.overflow-y-auto') || dialog;
      scrollable.scrollTop = scrollable.scrollHeight;
      const fileInputs = Array.from(dialog.querySelectorAll('input[type="file"]')).map((i) => ({
        accept: i.getAttribute('accept'),
        multiple: i.hasAttribute('multiple'),
      }));
      const selects = Array.from(dialog.querySelectorAll('select')).map((s) =>
        Array.from(s.options).map((o) => ({ value: o.value, label: o.text }))
      );
      return {
        opened: true,
        fileInputs,
        selects,
        hasEquipementsField: /climatisation|piscine|groupe électrogène|équipements/i.test(dialog.innerText),
        hasOwnerSelfieOption: /photo propriétaire|selfie/i.test(dialog.innerText),
      };
    });
    await sleep(400);
    await snap('12_modal_creation_preuves_f14_f5.png', false);
    await page.keyboard.press('Escape');
    await sleep(400);

    // Visites & Agenda tab (F15, F24)
    await page.evaluate(() => {
      const tab = Array.from(document.querySelectorAll('[role="tab"], button')).find((b) => /visites/i.test(b.innerText));
      if (tab) tab.click();
    });
    await sleep(800);
    await snap('13_dashboard_visites_agenda_f15_f24.png', false);

    // Transactions & Cautions tab + modal (C3, F18, F19, F25)
    await page.evaluate(() => {
      const tab = Array.from(document.querySelectorAll('[role="tab"], button')).find((b) => /transactions/i.test(b.innerText));
      if (tab) tab.click();
    });
    await sleep(800);
    await page.evaluate(() => {
      const detailBtn = Array.from(document.querySelectorAll('button')).find((b) => /détails|voir|gérer|contrat|dossier/i.test(b.innerText) && !/transactions/i.test(b.innerText));
      if (detailBtn) detailBtn.click();
    });
    await sleep(700);
    await snap('14_dashboard_transactions_modal_c3_f25.png', false);
    await page.keyboard.press('Escape');
    await sleep(300);

    // 7. Logout & Login as ADMIN -> Admin Dashboard, Verification, Signalements (F26, C2, S2, F22)
    await page.evaluate(() => localStorage.clear());
    await page.goto(`${BASE_URL}/connexion`, { waitUntil: 'networkidle2', timeout: 30000 });
    await sleep(500);
    await page.type('input[type="email"]', 'admin@ecourtier.sn');
    await page.type('input[type="password"]', process.env.AUDIT_ADMIN_PASSWORD || '');
    await page.click('button[type="submit"]');
    await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 15000 }).catch(() => {});
    await sleep(1200);

    await page.goto(`${BASE_URL}/admin/dashboard`, { waitUntil: 'networkidle2', timeout: 30000 });
    await sleep(1500);
    await snap('15_admin_dashboard_statistiques_f26.png', true);

    await page.goto(`${BASE_URL}/admin/verification`, { waitUntil: 'networkidle2', timeout: 30000 });
    await sleep(1200);
    results.uiChecks.adminVerification = await page.evaluate(() => ({
      pendingCardsCount: document.body.innerText.match(/Documents soumis:/g)?.length || 0,
      hasDocumentDownloadLink: Array.from(document.querySelectorAll('a, button')).some((el) => /télécharger|consulter le document|ouvrir la pièce/i.test(el.innerText)),
    }));
    await snap('16a_admin_verification_c2.png', false);

    await page.goto(`${BASE_URL}/admin/signalements`, { waitUntil: 'networkidle2', timeout: 30000 });
    await sleep(1000);
    await page.evaluate(() => {
      const tab = Array.from(document.querySelectorAll('button')).find((b) => b.innerText.trim() === 'Traités');
      if (tab) tab.click();
    });
    await sleep(800);
    await snap('16b_admin_signalements_f22.png', false);

    fs.writeFileSync('docs/audit/ui_audit_results.json', JSON.stringify(results, null, 2));
    console.log('UI audit completed -> docs/audit/ui_audit_results.json');
  } finally {
    await browser.close();
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
