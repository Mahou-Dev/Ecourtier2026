import { test, expect } from '@playwright/test';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const BASE_URL = 'http://127.0.0.1:4173';

test.describe('PHASE 9H — G-01 : Contrat et Workflow Administrateur de Remboursement Séquestre', () => {

  test('G-01 : Un utilisateur non-admin accédant à /admin/transactions est redirigé vers /connexion', async ({ page }) => {
    await page.goto(`${BASE_URL}/admin/transactions`);
    await page.waitForURL(/.*(connexion|dashboard).*/);
    expect(page.url()).not.toContain('/admin/transactions');
  });

  test('G-01 : Consultation du dossier avec séquestre remboursé affiche les métadonnées de remboursement', async ({ page }) => {
    // 1. Définir une session ADMIN
    const adminUser = {
      utilisateur: {
        id: '00000000-0000-0000-0000-000000000001',
        email: 'admin@ecourtier.sn',
        prenom: 'Moussa',
        nom: 'Sow',
        role: 'ADMIN',
        actif: true,
      },
      accessToken: 'mock-admin-token-2026',
      refreshToken: 'mock-refresh-token',
    };

    // 2. Mock des endpoints transactions
    await page.route('**/api/v1/admin/transactions*', async (route) => {
      const mockResponse = {
        content: [
          {
            id: 'tx-refunded-1234-5678-90ab',
            annonceId: 'ann-001',
            annonceTitre: 'Villa de Luxe Les Almadies',
            modeTransaction: 'LOCATION',
            annoncePrix: 850000,
            locataireId: 'user-loc-01',
            locataireNomComplet: 'Awa Ndiaye',
            proprietaireId: 'user-prop-01',
            proprietaireNomComplet: 'Ibrahima Fall',
            montantCaution: 850000,
            montantPremierLoyer: 850000,
            fraisDossier: 5000,
            montantTotalSequestre: 1705000,
            montantVerseProprietaire: 1700000,
            statutSequestre: 'REMBOURSE',
            referenceWave: 'WAVE-SANDBOX-PAID-001',
            datePaiement: '2026-11-01T10:00:00Z',
            referenceRemboursementWave: 'WAVE-REFUND-SANDBOX-999',
            montantRembourse: 1705000,
            dateRemboursement: '2026-11-05T14:30:00Z',
            motifRemboursement: "Désistement suite à accord amiable entre locataire et bailleur",
            statutBail: 'NON_SIGNE',
            dateCreation: '2026-11-01T09:00:00Z',
          },
        ],
        totalPages: 1,
        totalElements: 1,
        size: 15,
        number: 0,
      };
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockResponse),
      });
    });

    // 3. Charger la page en injectant l'état auth dans le sessionStorage
    await page.addInitScript((userData) => {
      sessionStorage.setItem('ecourtier-auth', JSON.stringify({ state: userData, version: 0 }));
    }, adminUser);

    await page.goto(`${BASE_URL}/admin/transactions`);

    // 4. Vérifier l'affichage du titre et du tableau
    await expect(page.locator('h1')).toContainText('Gestion des Séquestres & Remboursements');
    await expect(page.locator('tbody tr')).toHaveCount(1);
    await expect(page.locator('tbody tr')).toContainText('Villa de Luxe Les Almadies');
    await expect(page.locator('tbody tr')).toContainText('1 705 000 FCFA');
    await expect(page.locator('tbody tr')).toContainText('WAVE-REFUND-SANDBOX-999');

    // 5. Cliquer sur le bouton Détails
    await page.locator('button:has-text("Détails")').click();

    // 6. Vérifier l'affichage du modal avec les informations de remboursement Wave
    await expect(page.locator('div[role="dialog"]')).toBeVisible();
    await expect(page.locator('div[role="dialog"]')).toContainText('Séquestre Remboursé');
    await expect(page.locator('div[role="dialog"]')).toContainText('WAVE-REFUND-SANDBOX-999');
    await expect(page.locator('div[role="dialog"]')).toContainText("Désistement suite à accord amiable");
  });

  test('G-01 : Exécution d\'un remboursement administrateur sur dossier bloqué', async ({ page }) => {
    const adminUser = {
      utilisateur: {
        id: '00000000-0000-0000-0000-000000000001',
        email: 'admin@ecourtier.sn',
        prenom: 'Moussa',
        nom: 'Sow',
        role: 'ADMIN',
        actif: true,
      },
      accessToken: 'mock-admin-token-2026',
      refreshToken: 'mock-refresh-token',
    };

    let txStatus = 'BLOQUE';
    let refundRef = '';

    await page.route('**/api/v1/admin/transactions*', async (route) => {
      const mockResponse = {
        content: [
          {
            id: 'tx-bloque-9999-8888-7777',
            annonceId: 'ann-002',
            annonceTitre: 'Appartement F4 Fann Résidence',
            modeTransaction: 'LOCATION',
            annoncePrix: 500000,
            locataireId: 'user-loc-02',
            locataireNomComplet: 'Babacar Diop',
            proprietaireId: 'user-prop-02',
            proprietaireNomComplet: 'Fatou Kane',
            montantCaution: 500000,
            montantPremierLoyer: 500000,
            fraisDossier: 5000,
            montantTotalSequestre: 1005000,
            montantVerseProprietaire: 1000000,
            statutSequestre: txStatus,
            referenceWave: 'WAVE-SANDBOX-PAID-002',
            datePaiement: '2026-11-08T12:00:00Z',
            referenceRemboursementWave: refundRef || undefined,
            montantRembourse: refundRef ? 1005000 : undefined,
            motifRemboursement: refundRef ? 'Annulation demandée par le locataire' : undefined,
            statutBail: 'NON_SIGNE',
            dateCreation: '2026-11-08T11:00:00Z',
          },
        ],
        totalPages: 1,
        totalElements: 1,
        size: 15,
        number: 0,
      };
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockResponse),
      });
    });

    await page.route('**/api/v1/transactions/tx-bloque-9999-8888-7777/rembourser', async (route) => {
      txStatus = 'REMBOURSE';
      refundRef = 'WAVE-REFUND-SANDBOX-SUCCESS-777';
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'tx-bloque-9999-8888-7777',
          statutSequestre: 'REMBOURSE',
          referenceRemboursementWave: 'WAVE-REFUND-SANDBOX-SUCCESS-777',
          montantRembourse: 1005000,
          dateRemboursement: new Date().toISOString(),
          motifRemboursement: 'Annulation demandée par le locataire',
        }),
      });
    });

    await page.addInitScript((userData) => {
      sessionStorage.setItem('ecourtier-auth', JSON.stringify({ state: userData, version: 0 }));
    }, adminUser);

    await page.goto(`${BASE_URL}/admin/transactions`);

    // Ouvrir le modal
    await page.locator('button:has-text("Rembourser / Gérer")').click();
    await expect(page.locator('div[role="dialog"]')).toBeVisible();

    // Vérifier la présence du bloc Admin de remboursement
    await expect(page.locator('div[role="dialog"]')).toContainText('Action Administrateur : Rembourser le séquestre');

    // Cliquer sur "Initier le remboursement"
    await page.locator('button:has-text("Initier le remboursement")').click();

    // Remplir le motif et confirmer
    const motifInput = page.locator('input[placeholder*="Ex: Désistement"]');
    await expect(motifInput).toBeVisible();
    await motifInput.fill('Annulation demandée par le locataire');

    // Cliquer sur le bouton de confirmation
    await page.locator('button:has-text("Confirmer le remboursement Wave")').click();

    // Vérifier la transition et le badge de succès
    await expect(page.locator('div[role="dialog"]')).toContainText('Séquestre Remboursé');
  });
});
