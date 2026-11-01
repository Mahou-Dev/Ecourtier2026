import fs from 'fs';
import path from 'path';
import { createRequire } from 'module';

const require = createRequire('/home/mahou-dev/.npm/_npx/0f94ee7615faf582/package.json');
const puppeteer = require('puppeteer-core');

const CHROME_PATH = '/home/mahou-dev/.cache/ms-playwright/chromium-1248/chrome-linux64/chrome';
const BASE_URL = 'http://localhost:3000';
const OUT_DIR = path.resolve('docs/audit/screenshots');

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

    const proofs = JSON.parse(fs.readFileSync('docs/audit/api_proofs.json', 'utf8'));
    const buyerEmail = proofs.steps?.auth_register_acheteur?.response?.utilisateur?.email;
    const password = process.env.AUDIT_TEST_PASSWORD || 'AuditSecure2026!';

    console.log('Logging in as buyer:', buyerEmail);
    await page.goto(`${BASE_URL}/connexion`, { waitUntil: 'networkidle2', timeout: 30000 });
    await sleep(600);
    await page.type('input[type="email"]', buyerEmail);
    await page.type('input[type="password"]', password);
    await page.click('button[type="submit"]');
    await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 10000 }).catch(() => {});
    await sleep(1500);

    await page.goto(`${BASE_URL}/dashboard`, { waitUntil: 'networkidle2', timeout: 30000 });
    await sleep(2000);

    // Click sidebar button "Suivi Transactions"
    await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('aside button, nav button, button'));
      const txBtn = btns.find((b) => b.textContent && b.textContent.includes('Suivi Transactions'));
      if (txBtn) txBtn.click();
    });
    await sleep(2000);

    // Look for button that opens transaction details
    const clicked = await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('button'));
      const btn = btns.find((b) => b.textContent && (b.textContent.includes('Consulter') || b.textContent.includes('Détails') || b.textContent.includes('dossier')));
      if (btn) {
        btn.click();
        return true;
      }
      return false;
    });

    console.log('Clicked transaction button:', clicked);
    await sleep(2000);

    const shotPath = path.join(OUT_DIR, '21_dashboard_vente_deposit_label_f29.png');
    await page.screenshot({ path: shotPath, fullPage: false });
    console.log('Saved screenshot 21:', shotPath);
  } finally {
    await browser.close();
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
