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

    // Login as buyer who has simulated SMS
    await page.goto(`${BASE_URL}/connexion`, { waitUntil: 'networkidle2', timeout: 30000 });
    await sleep(600);

    // Read generated buyer email from api_proofs.json
    const proofs = JSON.parse(fs.readFileSync('docs/audit/api_proofs.json', 'utf8'));
    const buyerEmail = proofs.steps?.auth_register_acheteur?.response?.utilisateur?.email || 'locataire.demo@ecourtier.sn';
    const password = process.env.AUDIT_TEST_PASSWORD || 'AuditSecure2026!';

    console.log('Logging in as:', buyerEmail);
    await page.type('input[type="email"]', buyerEmail);
    await page.type('input[type="password"]', password);
    await page.click('button[type="submit"]');
    await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 10000 }).catch(() => {});
    await sleep(1500);

    // Go to Dashboard
    await page.goto(`${BASE_URL}/dashboard`, { waitUntil: 'networkidle2', timeout: 30000 });
    await sleep(1500);

    // Click on "SMS simulés" tab
    const smsTabFound = await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('button'));
      const smsBtn = btns.find((b) => b.textContent && b.textContent.includes('SMS simulés'));
      if (smsBtn) {
        smsBtn.click();
        return true;
      }
      return false;
    });

    console.log('SMS tab clicked:', smsTabFound);
    await sleep(1500);

    // Screenshot 1: SMS simulés tab
    const smsShot = path.join(OUT_DIR, '19_dashboard_sms_simules_f17.png');
    await page.screenshot({ path: smsShot, fullPage: false });
    console.log('Saved screenshot 19:', smsShot);

    // Now login as PROPRIETAIRE to show auto-confirm checkbox in create listing modal
    await page.evaluate(() => {
      localStorage.clear();
    });
    await page.goto(`${BASE_URL}/connexion`, { waitUntil: 'networkidle2', timeout: 30000 });
    await sleep(600);
    const proprioPass = process.env.AUDIT_PROPRIO_PASSWORD || 'ProprioPass123!';
    await page.type('input[type="email"]', 'proprio.demo@ecourtier.sn');
    await page.type('input[type="password"]', proprioPass);
    await page.click('button[type="submit"]');
    await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 10000 }).catch(() => {});
    await sleep(1500);

    await page.goto(`${BASE_URL}/dashboard`, { waitUntil: 'networkidle2', timeout: 30000 });
    await sleep(1500);

    // Click "Créer une annonce" button
    const pubBtnFound = await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('button'));
      const pubBtn = btns.find((b) => b.textContent && (b.textContent.includes('Créer') || b.textContent.includes('Publier')));
      if (pubBtn) {
        pubBtn.click();
        return true;
      }
      return false;
    });
    console.log('Créer annonce button clicked:', pubBtnFound);
    await sleep(1000);

    // Scroll inside modal to auto-confirm checkbox
    await page.evaluate(() => {
      const checkbox = document.querySelector('input[type="checkbox"]');
      if (checkbox) {
        checkbox.scrollIntoView({ behavior: 'instant', block: 'center' });
      }
    });
    await sleep(500);

    // Screenshot 2: Auto-confirm checkbox in modal
    const autoConfirmShot = path.join(OUT_DIR, '20_dashboard_auto_confirm_checkbox_f16.png');
    await page.screenshot({ path: autoConfirmShot, fullPage: false });
    console.log('Saved screenshot 20:', autoConfirmShot);

    // Close modal
    await page.keyboard.press('Escape');
    await sleep(500);

    // Navigate to "Suivi Transactions" tab
    await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('button'));
      const txTab = btns.find((b) => b.textContent && b.textContent.includes('Suivi Transactions'));
      if (txTab) txTab.click();
    });
    await sleep(1500);

    // Click on the VENTE transaction "Consulter" button
    const txBtnClicked = await page.evaluate(() => {
      const cards = Array.from(document.querySelectorAll('button'));
      const detailBtn = cards.find((b) => b.textContent && (b.textContent.includes('Détails') || b.textContent.includes('Consulter') || b.textContent.includes('Voir')));
      if (detailBtn) {
        detailBtn.click();
        return true;
      }
      return false;
    });
    console.log('Transaction detail clicked:', txBtnClicked);
    await sleep(1000);

    // Screenshot 21: VENTE Transaction Details Modal showing 10% Acompte
    const venteModalShot = path.join(OUT_DIR, '21_dashboard_vente_deposit_label_f29.png');
    await page.screenshot({ path: venteModalShot, fullPage: false });
    console.log('Saved screenshot 21:', venteModalShot);

  } finally {
    await browser.close();
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
