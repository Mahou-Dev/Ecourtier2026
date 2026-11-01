import fs from 'fs';
import http from 'http';
import path from 'path';
import zlib from 'zlib';
import { execFile } from 'child_process';
import { promisify } from 'util';
import { createRequire } from 'module';

const execFileAsync = promisify(execFile);
const require = createRequire('/home/mahou-dev/.npm/_npx/0f94ee7615faf582/package.json');
const puppeteer = require('puppeteer-core');

const CHROME_PATH = '/home/mahou-dev/.cache/ms-playwright/chromium-1248/chrome-linux64/chrome';
const LH_BIN = '/home/mahou-dev/.npm/_npx/0f94ee7615faf582/node_modules/.bin/lighthouse';
const DIST_DIR = '/home/mahou-dev/.gemini/antigravity/brain/03ddae90-455d-4adf-9b78-08bcdaa7e9b4/scratch/frontend-prod/dist';
const PORT = 5173;
const ANNONCE_ID = '958b59d1-12af-4412-b319-f20f24dfcb87';
const TARGET_URL = `http://localhost:${PORT}/annonce/${ANNONCE_ID}`;

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
};

const COMPRESSIBLE = new Set(['.html', '.js', '.css', '.json', '.webmanifest', '.svg']);

function startCompressedSpaServer() {
  const server = http.createServer((req, res) => {
    const reqUrl = new URL(req.url, `http://localhost:${PORT}`);
    let filePath = path.join(DIST_DIR, decodeURIComponent(reqUrl.pathname));
    if (!filePath.startsWith(DIST_DIR)) {
      res.writeHead(403);
      res.end('Forbidden');
      return;
    }
    if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
      filePath = path.join(DIST_DIR, 'index.html');
    }
    const ext = path.extname(filePath).toLowerCase();
    const contentType = MIME[ext] || 'application/octet-stream';
    const raw = fs.readFileSync(filePath);
    const acceptEnc = req.headers['accept-encoding'] || '';

    const headers = {
      'Content-Type': contentType,
      'Vary': 'Accept-Encoding',
      'Cache-Control': ext === '.html' || filePath.endsWith('sw.js') ? 'no-cache' : 'public, max-age=31536000, immutable',
    };

    if (COMPRESSIBLE.has(ext) && acceptEnc.includes('br')) {
      const compressed = zlib.brotliCompressSync(raw);
      headers['Content-Encoding'] = 'br';
      headers['Content-Length'] = compressed.length;
      res.writeHead(200, headers);
      res.end(compressed);
    } else if (COMPRESSIBLE.has(ext) && acceptEnc.includes('gzip')) {
      const compressed = zlib.gzipSync(raw);
      headers['Content-Encoding'] = 'gzip';
      headers['Content-Length'] = compressed.length;
      res.writeHead(200, headers);
      res.end(compressed);
    } else {
      headers['Content-Length'] = raw.length;
      res.writeHead(200, headers);
      res.end(raw);
    }
  });

  return new Promise((resolve) => {
    server.listen(PORT, '127.0.0.1', () => resolve(server));
  });
}

function median(arr) {
  const s = [...arr].sort((a, b) => a - b);
  const mid = Math.floor(s.length / 2);
  return s.length % 2 !== 0 ? s[mid] : (s[mid - 1] + s[mid]) / 2;
}

async function runLighthousePass(url, profileName, passIndex, throttlingArgs) {
  const outPath = `/home/mahou-dev/.gemini/antigravity/brain/03ddae90-455d-4adf-9b78-08bcdaa7e9b4/scratch/lh_${profileName}_pass${passIndex}.json`;
  const args = [
    url,
    '--output=json',
    `--output-path=${outPath}`,
    '--quiet',
    '--only-categories=performance,accessibility,best-practices,seo',
    '--chrome-flags=--headless=new --no-sandbox --disable-setuid-sandbox --disable-dev-shm-usage --disk-cache-size=1',
    ...throttlingArgs,
  ];
  await execFileAsync(LH_BIN, args, {
    env: { ...process.env, CHROME_PATH },
    timeout: 120000,
    maxBuffer: 20 * 1024 * 1024,
  });
  const lhr = JSON.parse(fs.readFileSync(outPath, 'utf-8'));
  const netItems = lhr.audits['network-requests']?.details?.items || [];
  const validStarts = netItems.map((i) => i.networkRequestTime).filter((v) => typeof v === 'number' && v >= 0);
  const validEnds = netItems.map((i) => i.networkEndTime).filter((v) => typeof v === 'number' && v >= 0);
  const networkSpanMs = validStarts.length && validEnds.length ? Math.round(Math.max(...validEnds) - Math.min(...validStarts)) : null;

  const failingA11y = Object.values(lhr.audits)
    .filter((a) => lhr.categories.accessibility?.auditRefs?.some((r) => r.id === a.id && r.weight > 0) && a.score !== null && a.score < 1)
    .map((a) => ({
      id: a.id,
      title: a.title,
      score: a.score,
      itemsCount: a.details?.items?.length || 0,
      sampleSnippets: (a.details?.items || []).slice(0, 3).map((it) => it.node?.snippet || it.node?.selector || ''),
    }));

  const failingBestPractices = Object.values(lhr.audits)
    .filter((a) => lhr.categories['best-practices']?.auditRefs?.some((r) => r.id === a.id && r.weight > 0) && a.score !== null && a.score < 1)
    .map((a) => ({ id: a.id, title: a.title, score: a.score }));

  return {
    pass: passIndex,
    fcpMs: Math.round(lhr.audits['first-contentful-paint']?.numericValue || 0),
    lcpMs: Math.round(lhr.audits['largest-contentful-paint']?.numericValue || 0),
    speedIndexMs: Math.round(lhr.audits['speed-index']?.numericValue || 0),
    ttiMs: Math.round(lhr.audits['interactive']?.numericValue || 0),
    tbtMs: Math.round(lhr.audits['total-blocking-time']?.numericValue || 0),
    cls: Number((lhr.audits['cumulative-layout-shift']?.numericValue || 0).toFixed(4)),
    networkLoadDurationMs: networkSpanMs,
    lhTotalTimingMs: Math.round(lhr.timing?.total || 0),
    totalBytesTransferred: lhr.audits['total-byte-weight']?.numericValue || 0,
    requestsCount: netItems.length,
    scores: {
      performance: Math.round((lhr.categories.performance?.score || 0) * 100),
      accessibility: Math.round((lhr.categories.accessibility?.score || 0) * 100),
      bestPractices: Math.round((lhr.categories['best-practices']?.score || 0) * 100),
      seo: Math.round((lhr.categories.seo?.score || 0) * 100),
    },
    failingA11y,
    failingBestPractices,
  };
}

function summarizePasses(passes) {
  return {
    passes,
    median: {
      fcpMs: median(passes.map((p) => p.fcpMs)),
      lcpMs: median(passes.map((p) => p.lcpMs)),
      speedIndexMs: median(passes.map((p) => p.speedIndexMs)),
      ttiMs: median(passes.map((p) => p.ttiMs)),
      tbtMs: median(passes.map((p) => p.tbtMs)),
      networkLoadDurationMs: median(passes.map((p) => p.networkLoadDurationMs || 0)),
      totalBytesTransferred: median(passes.map((p) => p.totalBytesTransferred)),
      scores: {
        performance: median(passes.map((p) => p.scores.performance)),
        accessibility: median(passes.map((p) => p.scores.accessibility)),
        bestPractices: median(passes.map((p) => p.scores.bestPractices)),
        seo: median(passes.map((p) => p.scores.seo)),
      },
    },
  };
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function main() {
  const server = await startCompressedSpaServer();
  const report = {
    executedAt: new Date().toISOString(),
    lighthouseVersion: '13.5.0',
    pwaCategoryNote: 'Lighthouse v12+ a retiré la catégorie PWA ; le Service Worker, le manifeste et le mode hors-ligne ont été vérifiés directement via Puppeteer + CDP.',
    targetUrl: TARGET_URL,
  };

  try {
    // 1. Verify compression headers on localhost:5173
    const compCheck = await new Promise((resolve) => {
      http.get(`http://127.0.0.1:${PORT}/`, { headers: { 'Accept-Encoding': 'gzip, br' } }, (res) => {
        res.resume();
        resolve({ statusCode: res.statusCode, contentEncoding: res.headers['content-encoding'], contentLength: res.headers['content-length'] });
      });
    });
    report.compressionVerification = compCheck;
    console.log('Compression verified:', compCheck);

    // 2. Puppeteer checks: CreateAnnonceModal screenshot 12 + T4 Service Worker Offline test
    const browser = await puppeteer.launch({
      executablePath: CHROME_PATH,
      headless: 'new',
      args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
    });
    try {
      const page = await browser.newPage();
      await page.setViewport({ width: 1440, height: 900, deviceScaleFactor: 1 });

      // Fix screenshot 12: Open "Créer une annonce" modal on Dashboard
      await page.goto(`http://localhost:${PORT}/connexion`, { waitUntil: 'networkidle2', timeout: 30000 });
      await page.type('input[type="email"]', 'proprio.demo@ecourtier.sn');
      await page.type('input[type="password"]', process.env.AUDIT_PROPRIO_PASSWORD || '');
      await page.click('button[type="submit"]');
      await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 15000 }).catch(() => {});
      await sleep(1000);
      await page.goto(`http://localhost:${PORT}/dashboard`, { waitUntil: 'networkidle2', timeout: 30000 });
      await sleep(1000);
      await page.evaluate(() => {
        const btn = Array.from(document.querySelectorAll('button')).find((b) => /créer une annonce/i.test(b.innerText));
        if (btn) btn.click();
      });
      await sleep(700);
      report.createAnnonceModalCheck = await page.evaluate(() => {
        const dialog = document.querySelector('[role="dialog"]');
        if (!dialog) return { opened: false };
        const scrollables = dialog.querySelectorAll('div, form');
        scrollables.forEach((el) => {
          if (el.scrollHeight > el.clientHeight) el.scrollTop = el.scrollHeight;
        });
        dialog.scrollTop = dialog.scrollHeight;
        const fileInputs = Array.from(dialog.querySelectorAll('input[type="file"]')).map((i) => ({
          accept: i.getAttribute('accept'),
          multiple: i.hasAttribute('multiple'),
        }));
        const labels = Array.from(dialog.querySelectorAll('label')).map((l) => l.innerText.trim());
        return {
          opened: true,
          labels,
          fileInputs,
          textExcerpt: dialog.innerText.slice(-700),
          hasEquipementsField: /climatisation|piscine|groupe électrogène|équipements/i.test(dialog.innerText),
          hasOwnerPhotoOption: /photo propriétaire|selfie/i.test(dialog.innerText),
        };
      });
      await sleep(300);
      await page.screenshot({ path: path.resolve('docs/audit/screenshots/12_modal_creation_preuves_f14_f5.png') });
      console.log('Updated 12_modal_creation_preuves_f14_f5.png');

      // T4: Service Worker + Offline cache verification on production build
      const swPage = await browser.newPage();
      await swPage.setViewport({ width: 390, height: 844, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
      await swPage.goto(TARGET_URL, { waitUntil: 'networkidle2', timeout: 30000 });
      await sleep(2000);
      // Reload once so active SW controls the page and populates runtime cache `public-annonces-cache`
      await swPage.reload({ waitUntil: 'networkidle2', timeout: 30000 });
      await sleep(1500);

      const swOnlineInfo = await swPage.evaluate(async () => {
        const reg = await navigator.serviceWorker?.getRegistration();
        const cacheNames = await caches.keys();
        const cacheDetails = {};
        for (const name of cacheNames) {
          const c = await caches.open(name);
          const keys = await c.keys();
          cacheDetails[name] = keys.length;
        }
        const manifestResp = await fetch('/manifest.webmanifest').then((r) => r.json()).catch(() => null);
        return {
          swControlled: !!navigator.serviceWorker?.controller,
          swState: reg?.active?.state || null,
          swScriptURL: reg?.active?.scriptURL || null,
          cacheDetails,
          manifest: manifestResp,
        };
      });

      // Switch to Offline via CDP & reload the listing page!
      const client = await swPage.createCDPSession();
      await client.send('Network.enable');
      await client.send('Network.emulateNetworkConditions', {
        offline: true,
        latency: 0,
        downloadThroughput: 0,
        uploadThroughput: 0,
      });
      await swPage.setOfflineMode(true);

      let offlineReloadSucceeded = false;
      try {
        await swPage.reload({ waitUntil: 'domcontentloaded', timeout: 15000 });
        await sleep(1500);
        offlineReloadSucceeded = true;
      } catch (e) {
        offlineReloadSucceeded = false;
      }

      const swOfflineCheck = await swPage.evaluate(() => ({
        navigatorOnLine: navigator.onLine,
        rendersListingTitleOffline: document.body.innerText.includes('Appartement Témoin Audit Visite 360'),
        rendersOfflineBanner: document.body.innerText.includes('Mode hors ligne'),
        visibleTextSample: document.body.innerText.slice(0, 350),
      }));
      await swPage.screenshot({ path: path.resolve('docs/audit/screenshots/17_offline_pwa_sw_t4.png') });
      report.pwaOfflineT4 = { online: swOnlineInfo, offlineReloadSucceeded, offline: swOfflineCheck };
      console.log('T4 Offline check:', report.pwaOfflineT4.offline);
    } finally {
      await browser.close();
    }

    // 3. Lighthouse 3 passes Fast 3G (1.6 Mbps = 1600 kbps, 150 ms RTT)
    const fast3gArgs = [
      '--throttling-method=devtools',
      '--throttling.requestLatencyMs=150',
      '--throttling.downloadThroughputKbps=1600',
      '--throttling.uploadThroughputKbps=750',
      '--throttling.cpuSlowdownMultiplier=4',
    ];
    const fast3gPasses = [];
    for (let i = 1; i <= 3; i++) {
      console.log(`Running Lighthouse Fast 3G pass ${i}/3...`);
      fast3gPasses.push(await runLighthousePass(TARGET_URL, 'fast3g', i, fast3gArgs));
    }
    report.localFast3G = summarizePasses(fast3gPasses);

    // 4. Lighthouse 3 passes Slow 3G (400 kbps, 400 ms RTT)
    const slow3gArgs = [
      '--throttling-method=devtools',
      '--throttling.requestLatencyMs=400',
      '--throttling.downloadThroughputKbps=400',
      '--throttling.uploadThroughputKbps=400',
      '--throttling.cpuSlowdownMultiplier=4',
    ];
    const slow3gPasses = [];
    for (let i = 1; i <= 3; i++) {
      console.log(`Running Lighthouse Slow 3G pass ${i}/3...`);
      slow3gPasses.push(await runLighthousePass(TARGET_URL, 'slow3g', i, slow3gArgs));
    }
    report.localSlow3G = summarizePasses(slow3gPasses);

    // 5. Render URL check for S5: /annonce/:id returns 404, test root / once on Fast 3G for reference
    console.log('Running Lighthouse Fast 3G 1 pass on Render root https://ecourtier-frontend.onrender.com/ ...');
    try {
      report.renderRootFast3G = await runLighthousePass('https://ecourtier-frontend.onrender.com/', 'render_root', 1, fast3gArgs);
    } catch (e) {
      report.renderRootFast3G = { error: String(e) };
    }

    fs.writeFileSync('docs/audit/lighthouse_s5_t10_t4.json', JSON.stringify(report, null, 2));
    console.log('Saved -> docs/audit/lighthouse_s5_t10_t4.json');
  } finally {
    server.close();
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
