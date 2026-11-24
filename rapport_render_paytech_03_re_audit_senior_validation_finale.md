# RAPPORT RENDER-PAYTECH-03 — RÉ-AUDIT SENIOR INDÉPENDANT, REMÉDIATION FINALE & VALIDATION PRÉ-COMMIT

**Projet :** E-Courtier Sénégal — Projet 14  
**Date :** 24 novembre 2026  
**Branche :** `fix/chatbot-nlu-gemini-prod` (base `60f9fc8`, alignée sur `origin/main`)  
**Verdict final :** **A — PRÉ-FUSION VALIDÉE (prêt pour commit et revue humaine)**

---

## 1. Synthèse de la vérification des 11 anomalies `AUDIT-PT-001` à `AUDIT-PT-011`

Toutes les anomalies identifiées lors de l'audit indépendant pré-fusion (`rapport_render_paytech_02_audit_independant_pre_fusion.md`, verdict initial **C — FUSION BLOQUÉE**) ont été vérifiées dans le code source, les migrations SQL, le contrat OpenAPI et les tests :

| ID | Gravité | Statut | Preuve technique vérifiée |
|---|---|---|---|
| `AUDIT-PT-001` | P0 | **Résolu** | Verrou backend `assertZeroDebitGuaranteed()` + `validateOutboundPaymentAllowed()` dans `PayTechPaymentProperties` / `PayTechClient` / `PayTechPaymentGateway` ; masquage `redirectUrlPaiement` dans `TransactionMapper` ; blocage démarrage `prod` contre `paytech.sn` si `zero-debit-guaranteed=true` dans `ProductionConfigurationValidator`. |
| `AUDIT-PT-002` | P1 | **Résolu** | `hmac_compute` obligatoire sur `sale_complete`, `sale_canceled`, `transfer_success` (fallback SHA-256 restreint à `refund_complete` et `transfer_failed`) ; montant positif + `currency=XOF` obligatoires ; `env=test` obligatoire (`403` sur absence ou toute valeur != `test`) ; contrôle strict montant dans `TransactionService.confirmWavePaymentInTransaction()`. |
| `AUDIT-PT-003` | P1 | **Résolu** | `FlywayFreshMigrationIntegrationTest` mis à jour sur `V23` avec vérification explicite des 2 contraintes CHECK et des 5 index uniques partiels `V23` sur PostgreSQL 16. |
| `AUDIT-PT-004` | P1 | **Résolu** | Suppression du fallback permissif `custom_field` ; projections JPQL directes sur `transaction.id` dans `PaiementWaveRepository` ; validation croisée `ref_command` et `token` contre la session active. |
| `AUDIT-PT-005` | P1 | **Résolu** | Protection de l'état terminal `CONFIRME` contre `transfer_failed` hors ordre ; re-contrôle d'état (`dossierActif`, `INITIALISE`, `referenceWave == null`, non expiré) en Phase 3 Cas A (`PENDING`) ; idempotence indexée sur `tokenPaiementExterne`. |
| `AUDIT-PT-006` | P2 | **Résolu** | Validation stricte `https://paytech.sn` / `*.paytech.sn` (sans `userInfo`) dans `PayTechClient.validateTrustedPayTechRedirectUrl()` et `TransactionDetailsModal.tsx`. |
| `AUDIT-PT-007` | P2 | **Résolu** | `PayTechClient.parsePaymentStatusResponse()` rejette tout `env != test` et refuse `CONFIRMED` sans `env=test` ; `PayTechPaymentGateway.checkPaymentStatus()` ne substitue jamais le montant attendu si absent (`UNKNOWN`). |
| `AUDIT-PT-008` | P2 | **Résolu** | Verrou distribué `SchedulerLockService` sur `WavePaymentReconciliationService.scheduledReconcile()` ; pas d'échec prématuré sur `UNKNOWN` pour `EN_ATTENTE_CONFIRMATION` ; synchronisation `statutPaiementExterne` sur `FAILED` ; exclusion de `EN_COURS` et contrôle DB des remboursements antérieurs dans `MockPaymentGateway`. |
| `AUDIT-PT-009` | P2 | **Résolu** | `PayTechWebhookController` limite `getParameterMap()` à `application/x-www-form-urlencoded` et plafonne la taille du corps à 64 Ko (`413`) ; `WaveWebhookService` hérité désactivé (`403`) si `PAYMENT_PROVIDER=paytech` et exige `amount > 0`. |
| `AUDIT-PT-010` | P2 | **Résolu** | Ajout de `PayTechProviderContextBootIntegrationTest` (`@SpringBootTest` avec `ecourtier.payment.provider=paytech`), tests altération SHA-256 (`409`), attaques IPN, `503` zéro débit et anti-open-redirect UI dans `paytech-sandbox-ui.spec.ts`. |
| `AUDIT-PT-011` | P3 | **Résolu** | Validation `refundNotifUrl` optionnelle ; rejet `PAYTECH_ENV=""` ; empreinte SHA-256 enrichie (titre, quartier, ville, prix, emails) ; libellés `AdminTransactionsPage.tsx` et `.env.example` mis à jour. |

---

## 2. Anomalies supplémentaires détectées et corrigées lors du ré-audit senior

Le ré-audit ligne à ligne a identifié 4 défauts subtils supplémentaires, tous corrigés et couverts par des tests de non-régression :

1. **`RE-AUDIT-01` (Haute — Collision d'index uniques PostgreSQL 16 sur `refund_complete`) :**
   - *Cause :* Sur PostgreSQL 16 réel, `V19` (`uq_paiements_wave_reference_externe`) et `V23` (`uq_paiements_wave_ref_command`) imposent l'unicité sur `paiements_wave`. Lorsqu'un IPN `refund_complete` arrivait avec le même `ref_command` et le même `token` que la ligne `PAIEMENT_SEQUESTRE`, `TransactionService.handleExternalRefundNotification()` provoquait une violation d'unicité SQL (`DataIntegrityViolationException`).
   - *Correction :* Préfixage déterministe `REFUND-...` sur la référence externe de remboursement, non-duplication de `ref_command` sur la ligne `REMBOURSEMENT` et contrôle strict du montant `refundedAmount == montantTotalSequestre` (`400` en cas d'écart).
   - *Preuve :* Test #5 ajouté dans `Postgres16Phase3IntegrationTest` (exécuté sur PostgreSQL 16 réel) et test dédié ajouté dans `PayTechEndToEndSandboxIntegrationTest`.

2. **`RE-AUDIT-02` (Moyenne — Filtrage `TypeOperationWave.TRANSFERT_PROPRIETAIRE` dans `handleExternalTransferNotification`) :**
   - *Cause :* La recherche par `tokenExterne` / `referenceExterne` / `refCommand` pouvait résoudre une opération `PAIEMENT_SEQUESTRE` au lieu d'un `TRANSFERT_PROPRIETAIRE`.
   - *Correction :* Filtrage strict `.filter(p -> p.getTypeOperation() == TypeOperationWave.TRANSFERT_PROPRIETAIRE)` (retourne `404` sinon), couvert en test H2 et PostgreSQL 16.

3. **`RE-AUDIT-03` (Défense en profondeur) :**
   - Contrôle préalable `request.getContentLengthLong() > MAX_WEBHOOK_BODY_BYTES` (`413`) avant lecture des paramètres form-urlencoded dans `PayTechWebhookController`.
   - Fallback sur `PAYTECH_ZERO_DEBIT_GUARANTEED` dans `ProductionConfigurationValidator`.
   - Passage strict de `statusResult.getConfirmedAmount()` sans substitution implicite pour les paiements non simulés dans `TransactionService.verifierStatutPaiementExterne()`.
   - Rejet des URLs contenant des informations d'authentification (`userInfo`) dans `PayTechClient` et `TransactionDetailsModal.tsx`.

4. **`RE-AUDIT-04` (Isolation E2E Playwright contre backend réel `18080`) :**
   - Ajout d'un intercepteur catch-all `**/api/v1/**` dans les scénarios mockés de `paytech-sandbox-ui.spec.ts` (test #2) et `phase9h-admin-refund.spec.ts` afin d'éviter les appels non interceptés vers le backend réel lors du job CI Phase 3.

---

## 3. Batterie complète de validation et non-régression exécutée

| Contrôle | Commande / Périmètre | Résultat |
|---|---|---|
| **Précontrôles CI** | Synchro `docs/openapi.json` == `backend/src/main/resources/static/openapi.json`, séquentialité Flyway `V1..V23` (`0` doublon), `git diff render.yaml` vide | **PASS** |
| **Scan de secrets** | Sources suivies, nouveaux fichiers, rapports Markdown et bundle `frontend/dist/` | **CLEAN (0 secret)** |
| **Tests Backend + JaCoCo** | `mvn -B -f backend/pom.xml test jacoco:check -Djacoco.skip=false` | **477 exécutés, 0 échec, 0 erreur** (7 PG16 ignorés hors flag), **JaCoCo PASS** |
| **Suite PostgreSQL 16 (Testcontainers)** | `FlywayFreshMigrationIntegrationTest`, `Postgres16Phase3IntegrationTest`, `Postgres16IaConcurrencyIntegrationTest` (`-Decourtier.test.pg16=true`) | **7/7 PASS** |
| **Qualité Frontend** | `tsc --noEmit`, `eslint . --max-warnings 0`, `vite build` + PWA | **0 erreur, 0 warning** |
| **Suite E2E Playwright Phase 3 (Backend Spring Boot réel `18080` + `vite preview` `4173`)** | `phase2-security-ui.spec.ts`, `phase3-real-backend.spec.ts`, `csp-security-compliance.spec.ts`, `paytech-sandbox-ui.spec.ts`, `phase9h-admin-refund.spec.ts`, `lot2-2-c-detail.spec.ts` | **22/22 PASS** |

---

## 4. Garanties sécurité maintenues

- **Zéro débit réel garanti :** `PAYMENT_PROVIDER=mock` et `ecourtier.payment.paytech.zero-debit-guaranteed=false` par défaut ; aucun appel réseau vers PayTech exécuté.
- **Aucune modification Render / déploiement :** `render.yaml` strictement inchangé ; aucun secret écrit sur disque ni dans Git.
- **Recommandation sécurité opérateur :** effectuer une rotation des clés API/Secret PayTech Sandbox communiquées dans l'historique de conversation avant toute réutilisation ultérieure.
