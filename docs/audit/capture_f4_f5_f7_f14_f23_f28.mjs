import fs from 'fs';
import path from 'path';
import { createRequire } from 'module';

const require = createRequire('/home/mahou-dev/.npm/_npx/0f94ee7615faf582/package.json');
const puppeteer = require('puppeteer-core');

const CHROME_PATH = '/home/mahou-dev/.cache/ms-playwright/chromium-1248/chrome-linux64/chrome';
const BASE_URL = 'http://localhost:3000';
const OUT_DIR = path.resolve('docs/audit/screenshots');

fs.mkdirSync(OUT_DIR, { recursive: true });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function main() {
  const browser = await puppeteer.launch({
    executablePath: CHROME_PATH,
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
  });

  try {
    const page = await browser.newPage();
    await page.setViewport({ width: 1440, height: 900, deviceScaleFactor: 1 });

    const snap = async (filename) => {
      const p = path.join(OUT_DIR, filename);
      await page.screenshot({ path: p, fullPage: false });
      console.log('Saved', filename);
    };

    // 1. Search page with filters (F7-F13)
    console.log('Navigating to search page...');
    await page.goto(`${BASE_URL}/recherche?mode=LOCATION&type=APPARTEMENT&proximite=COMMERCE&proximite=ECOLE`, { waitUntil: 'networkidle2', timeout: 30000 });
    await sleep(2000);
    await snap('04_recherche_filtres_f7_f13.png');

    // 2. Login as owner to test Dashboard, modals, and property detail
    console.log('Logging in as owner...');
    await page.goto(`${BASE_URL}/connexion`, { waitUntil: 'networkidle2', timeout: 30000 });
    await sleep(600);
    await page.type('input[type="email"]', 'proprio.demo@ecourtier.sn');
    await page.type('input[type="password"]', 'ProprioPass123!');
    await page.click('button[type="submit"]');
    await sleep(2000);

    // 3. Go to Dashboard (F23, F28)
    console.log('Navigating to dashboard...');
    await page.goto(`${BASE_URL}/dashboard`, { waitUntil: 'networkidle2', timeout: 30000 });
    await sleep(2000);
    await snap('11_dashboard_proprietaire_annonces_f23.png');

    // 4. Open delete confirmation modal (F28)
    console.log('Opening delete modal...');
    const deleteBtnClicked = await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('button'));
      const del = btns.find(b => b.textContent && b.textContent.includes('Supprimer'));
      if (del) {
        del.click();
        return true;
      }
      return false;
    });
    console.log('Delete button clicked:', deleteBtnClicked);
    if (deleteBtnClicked) {
      await sleep(1000);
      await snap('24_dashboard_suppression_confirmation_f28.png');
      // Cancel delete
      await page.evaluate(() => {
        const btns = Array.from(document.querySelectorAll('button'));
        const cancelBtn = btns.find(b => b.textContent && b.textContent.includes('Annuler'));
        if (cancelBtn) cancelBtn.click();
      });
      await sleep(1000);
    }

    // 5. Open Create Annonce modal (F4, F5, F14)
    console.log('Opening create annonce modal...');
    const createBtnClicked = await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('button'));
      const createBtn = btns.find(b => b.textContent && b.textContent.includes('Créer une annonce'));
      if (createBtn) {
        createBtn.click();
        return true;
      }
      return false;
    });
    console.log('Create button clicked:', createBtnClicked);
    if (createBtnClicked) {
      await sleep(2500);
      await snap('12_modal_creation_preuves_f14_f5.png');
      // Close modal
      await page.keyboard.press('Escape');
      await sleep(1000);
    }

    // 6. Open Edit Annonce modal (F23, F5)
    console.log('Opening edit annonce modal...');
    const editBtnClicked = await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('button'));
      const editBtn = btns.find(b => b.textContent && b.textContent.includes('Modifier'));
      if (editBtn) {
        editBtn.click();
        return true;
      }
      return false;
    });
    console.log('Edit button clicked:', editBtnClicked);
    if (editBtnClicked) {
      await sleep(2500);
      await snap('22_modal_modification_f23_f5.png');
      await page.keyboard.press('Escape');
      await sleep(1000);
    }

    // 7. Property Detail Page (F5 icons, F14 owner document status and upload)
    console.log('Navigating to property detail page...');
    const targetAnnonceId = '741dcbde-81e6-4e16-8fe7-72c28aa02e3e';
    await page.goto(`${BASE_URL}/annonce/${targetAnnonceId}`, { waitUntil: 'networkidle2', timeout: 30000 });
    await sleep(2500);
    await snap('23_fiche_annonce_equipements_docs_f5_f14.png');

    console.log('All screenshots completed successfully!');
  } catch (err) {
    console.error('Error during capture:', err);
  } finally {
    await browser.close();
  }
}

main();
