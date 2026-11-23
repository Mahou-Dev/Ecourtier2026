import fs from 'node:fs';
import http from 'node:http';
import type { AddressInfo } from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { test, expect } from '@playwright/test';

const currentDir = path.dirname(fileURLToPath(import.meta.url));
const frontendDir = path.resolve(currentDir, '..');
const repoRoot = path.resolve(frontendDir, '..');
const nginxConfPath = path.join(frontendDir, 'nginx.conf');
const indexHtmlPath = path.join(frontendDir, 'index.html');
const distDir = path.join(frontendDir, 'dist');
const distIndexHtmlPath = path.join(distDir, 'index.html');
const prefetchScriptPath = path.join(frontendDir, 'public', 'prefetch-annonce.js');
const renderYamlPath = path.join(repoRoot, 'render.yaml');

const MIME_TYPES: Record<string, string> = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
};

function extractNginxCsp(): string {
  const nginxConf = fs.readFileSync(nginxConfPath, 'utf8');
  const match = nginxConf.match(/set\s+\$csp_policy\s+"([^"]+)"\s*;/);
  if (!match) {
    throw new Error('Directive set $csp_policy introuvable dans frontend/nginx.conf');
  }
  return match[1];
}

function extractRenderStaticCsp(): string {
  const renderYaml = fs.readFileSync(renderYamlPath, 'utf8');
  const match = renderYaml.match(/name:\s*Content-Security-Policy\s*\n\s*value:\s*"([^"]+)"/);
  if (!match) {
    throw new Error('En-tête Content-Security-Policy introuvable dans render.yaml');
  }
  return match[1];
}

function startDistStaticServerWithCsp(cspHeader: string): Promise<{ server: http.Server; baseUrl: string }> {
  return new Promise((resolve, reject) => {
    const server = http.createServer((req, res) => {
      const reqPath = (req.url || '/').split('?')[0];
      const safeRel = reqPath === '/' ? 'index.html' : reqPath.replace(/^\/+/, '');
      let targetPath = path.resolve(distDir, safeRel);

      if (!targetPath.startsWith(distDir) || !fs.existsSync(targetPath) || fs.statSync(targetPath).isDirectory()) {
        // Fallback SPA exact comme nginx.conf (`try_files $uri $uri/ /index.html`)
        targetPath = distIndexHtmlPath;
      }

      const ext = path.extname(targetPath).toLowerCase();
      res.writeHead(200, {
        'Content-Type': MIME_TYPES[ext] || 'application/octet-stream',
        'Content-Security-Policy': cspHeader,
        'X-Content-Type-Options': 'nosniff',
        'X-Frame-Options': 'SAMEORIGIN',
        'Referrer-Policy': 'strict-origin-when-cross-origin',
      });
      fs.createReadStream(targetPath).pipe(res);
    });

    server.on('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const addr = server.address() as AddressInfo;
      resolve({ server, baseUrl: `http://127.0.0.1:${addr.port}` });
    });
  });
}

test.describe('Conformité Content-Security-Policy & Durcissement XSS (REV-04)', () => {
  test('1. Validation statique nginx.conf, render.yaml, index.html et prefetch externe', () => {
    const nginxConf = fs.readFileSync(nginxConfPath, 'utf8');
    const nginxCsp = extractNginxCsp();
    const renderCsp = extractRenderStaticCsp();
    const indexHtml = fs.readFileSync(indexHtmlPath, 'utf8');
    const prefetchJs = fs.readFileSync(prefetchScriptPath, 'utf8');

    // Aucun script inline ni gestionnaire onload= dans index.html
    expect(indexHtml).not.toMatch(/\sonload\s*=/i);
    expect(indexHtml).not.toMatch(/<script(?![^>]*\ssrc=)[^>]*>/i);
    expect(indexHtml).toContain('<script src="/prefetch-annonce.js"></script>');
    expect(prefetchJs).toContain('__PREFETCHED_ANNONCE_PROMISE__');

    // Vérifier aussi le index.html généré dans dist/
    expect(fs.existsSync(distIndexHtmlPath)).toBe(true);
    const distHtml = fs.readFileSync(distIndexHtmlPath, 'utf8');
    expect(distHtml).not.toMatch(/\sonload\s*=/i);
    expect(distHtml).not.toMatch(/<script(?![^>]*\ssrc=)[^>]*>/i);
    expect(distHtml).toContain('/prefetch-annonce.js');

    // Directives CSP obligatoires sans unsafe-eval ni script-src unsafe-inline
    for (const [label, policy] of [
      ['nginx.conf', nginxCsp],
      ['render.yaml', renderCsp],
    ] as const) {
      expect(policy, `${label} ne doit pas contenir unsafe-eval`).not.toContain('unsafe-eval');
      expect(policy, `${label} doit restreindre script-src à 'self'`).toMatch(/(?:^|;\s*)script-src\s+'self'(?:;|$)/);
      expect(policy, `${label} doit définir default-src 'self'`).toContain("default-src 'self'");
      expect(policy, `${label} doit définir style-src`).toContain("style-src 'self' 'unsafe-inline' https://fonts.googleapis.com");
      expect(policy, `${label} doit définir font-src`).toContain("font-src 'self' https://fonts.gstatic.com data:");
      expect(policy, `${label} doit autoriser OpenStreetMap dans img-src`).toContain('https://*.tile.openstreetmap.org');
      expect(policy, `${label} doit autoriser le backend Render dans connect-src`).toContain('https://ecourtier-backend.onrender.com');
      expect(policy, `${label} doit bloquer object-src`).toContain("object-src 'none'");
      expect(policy, `${label} doit restreindre base-uri`).toContain("base-uri 'self'");
      expect(policy, `${label} doit restreindre frame-ancestors`).toContain("frame-ancestors 'self'");
      expect(policy, `${label} doit restreindre form-action`).toContain("form-action 'self'");
      expect(policy, `${label} doit encadrer worker-src et manifest-src`).toContain("worker-src 'self'");
      expect(policy, `${label} doit encadrer manifest-src`).toContain("manifest-src 'self'");
    }

    // render.yaml (HTTPS prod) interdit localhost et active upgrade-insecure-requests
    expect(renderCsp).not.toContain('localhost');
    expect(renderCsp).not.toContain('127.0.0.1');
    expect(renderCsp).toContain('upgrade-insecure-requests');

    // nginx.conf répète add_header Content-Security-Policy dans chaque location enfant redéfinissant add_header
    const cspAddMatches = nginxConf.match(/add_header\s+Content-Security-Policy\s+\$csp_policy\s+always;/g) || [];
    expect(cspAddMatches.length).toBeGreaterThanOrEqual(3);
  });

  test('2. Application sous Chromium avec en-tête HTTP CSP réel : 0 violation, chatbot OK, pas de fuite de jeton DOM/URL et blocage XSS inline', async ({
    page,
  }) => {
    const nginxCsp = extractNginxCsp();
    const { server, baseUrl } = await startDistStaticServerWithCsp(nginxCsp);

    try {
      const SECRET_TOKEN = 'tok_csp_anon_session_secret_value_abc123';
      const CONV_ID = 'conv-csp-test-901';
      const requestedUrls: string[] = [];
      const consoleErrors: string[] = [];
      const pageErrors: string[] = [];

      await page.addInitScript(() => {
        (window as unknown as { __cspViolations?: { directive: string; blockedUri: string }[] }).__cspViolations = [];
        window.addEventListener('securitypolicyviolation', (ev) => {
          (window as unknown as { __cspViolations: { directive: string; blockedUri: string }[] }).__cspViolations.push({
            directive: ev.effectiveDirective || ev.violatedDirective,
            blockedUri: ev.blockedURI,
          });
        });
      });

      page.on('console', (msg) => {
        if (msg.type() === 'error') {
          consoleErrors.push(msg.text());
        }
      });
      page.on('pageerror', (err) => pageErrors.push(err.message));

      // Simuler Google Fonts en local isolé (la politique CSP est réellement appliquée sur le document HTML par le serveur HTTP)
      await page.route('https://fonts.googleapis.com/**', async (route) => {
        await route.fulfill({ status: 200, contentType: 'text/css', body: '/* mocked google fonts css */' });
      });
      await page.route('https://fonts.gstatic.com/**', async (route) => {
        await route.fulfill({ status: 204, body: '' });
      });

      await page.route('**/api/v1/annonces**', async (route) => {
        requestedUrls.push(route.request().url());
        if (route.request().url().includes('/11111111-1111-1111-1111-111111111111')) {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
              id: '11111111-1111-1111-1111-111111111111',
              titre: 'Appartement Mermoz CSP',
              prix: 350000,
              modeTransaction: 'LOCATION',
              type: 'APPARTEMENT',
              quartier: 'Mermoz',
              ville: 'Dakar',
              statutPublication: 'DISPONIBLE',
              statutVerification: 'VERIFIEE',
              photos: [],
            }),
          });
          return;
        }
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ content: [], totalPages: 0, totalElements: 0 }),
        });
      });

      await page.route('**/api/v1/references**', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ villes: ['Dakar'], quartiers: ['Mermoz'] }),
        });
      });

      await page.route('**/api/v1/ia/conversations', async (route) => {
        requestedUrls.push(route.request().url());
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({
            conversationId: CONV_ID,
            sessionToken: SECRET_TOKEN,
            dateCreation: '2026-11-22T21:00:00',
          }),
        });
      });

      await page.route(`**/api/v1/ia/conversations/${CONV_ID}/messages`, async (route) => {
        requestedUrls.push(route.request().url());
        expect(route.request().headers()['x-ia-session-token']).toBe(SECRET_TOKEN);
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: 'msg-csp-1',
            reponse: 'Voici une sélection conforme sous CSP.',
            annoncesSuggeres: [],
            criteresExtraits: { fournisseurIa: 'gemini', modeSecours: false },
            dateEnvoi: '2026-11-22T21:00:01',
          }),
        });
      });

      await page.route(`**/api/v1/ia/conversations/${CONV_ID}`, async (route) => {
        requestedUrls.push(route.request().url());
        expect(route.request().headers()['x-ia-session-token']).toBe(SECRET_TOKEN);
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            conversationId: CONV_ID,
            dateCreation: '2026-11-22T21:00:00',
            messages: [
              { id: 'u1', role: 'UTILISATEUR', ordre: 1, contenu: 'Studio à Mermoz', dateEnvoi: '2026-11-22T21:00:00' },
              {
                id: 'a1',
                role: 'ASSISTANT',
                ordre: 2,
                contenu: 'Voici une sélection conforme sous CSP.',
                criteresExtraits: { fournisseurIa: 'gemini', modeSecours: false },
                dateEnvoi: '2026-11-22T21:00:01',
              },
            ],
          }),
        });
      });

      // 1. Chargement accueil depuis serveur HTTP réel émettant Content-Security-Policy
      const homeResp = await page.goto(`${baseUrl}/`, { waitUntil: 'networkidle' });
      expect(homeResp?.headers()['content-security-policy']).toBe(nginxCsp);

      await page.locator('button:has-text("Assistant IA E-Courtier")').click();
      await page.locator('[data-testid="ia-input"]').fill('Studio à Mermoz');
      await page.locator('button[type="submit"]:has(svg.lucide-send)').click();
      await expect(page.locator('text=Voici une sélection conforme sous CSP.')).toBeVisible();

      // Vérifier PWA manifest, Service Worker et script prefetch servis avec CSP réel
      const swResp = await page.request.get(`${baseUrl}/registerSW.js`);
      expect(swResp.status()).toBe(200);
      expect(swResp.headers()['content-security-policy']).toBe(nginxCsp);
      const manifestResp = await page.request.get(`${baseUrl}/manifest.webmanifest`);
      expect(manifestResp.status()).toBe(200);
      expect(manifestResp.headers()['content-security-policy']).toBe(nginxCsp);
      const prefetchResp = await page.request.get(`${baseUrl}/prefetch-annonce.js`);
      expect(prefetchResp.status()).toBe(200);
      expect(prefetchResp.headers()['content-security-policy']).toBe(nginxCsp);

      await page.reload({ waitUntil: 'networkidle' });
      await page.locator('button:has-text("Assistant IA E-Courtier")').click();
      await expect(page.locator('text=Voici une sélection conforme sous CSP.')).toBeVisible();

      // Vérifier absence de fuite du jeton anonyme dans le DOM ou les URLs
      const domHtml = await page.evaluate(() => document.documentElement.innerHTML);
      expect(domHtml).not.toContain(SECRET_TOKEN);
      for (const u of requestedUrls) {
        expect(u).not.toContain(SECRET_TOKEN);
      }

      // 2. Navigation sur une page détail avec prefetch-annonce.js actif sous CSP strict
      await page.goto(`${baseUrl}/annonce/11111111-1111-1111-1111-111111111111`, { waitUntil: 'networkidle' });
      const appViolations = await page.evaluate(
        () => (window as unknown as { __cspViolations?: { directive: string; blockedUri: string }[] }).__cspViolations || []
      );

      expect(appViolations, 'Aucune violation CSP légitime autorisée durant la navigation').toEqual([]);
      expect(pageErrors, 'Aucune exception JS non interceptée').toEqual([]);
      expect(consoleErrors, 'Aucune erreur console durant l\'exécution légitime').toEqual([]);

      // 3. Preuve active du blocage XSS inline par script-src 'self'
      await page.evaluate(() => {
        const injected = document.createElement('script');
        injected.textContent = 'window.__xssInlineExecuted = true;';
        document.head.appendChild(injected);
      });
      const xssOutcome = await page.evaluate(() => ({
        executed: (window as unknown as { __xssInlineExecuted?: boolean }).__xssInlineExecuted,
        violations: (window as unknown as { __cspViolations?: { directive: string; blockedUri: string }[] }).__cspViolations || [],
      }));
      expect(xssOutcome.executed).toBeUndefined();
      expect(xssOutcome.violations.some((v) => v.directive.startsWith('script-src'))).toBe(true);
    } finally {
      server.closeAllConnections?.();
      await new Promise<void>((resolve) => server.close(() => resolve()));
    }
  });
});
