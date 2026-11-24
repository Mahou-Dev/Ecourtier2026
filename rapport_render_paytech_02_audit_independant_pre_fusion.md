# RAPPORT D'AUDIT INDÉPENDANT PRÉ-FUSION — MISSION RENDER-PAYTECH-02

**Projet :** E-Courtier Sénégal — Projet 14  
**Date :** 24 novembre 2026  
**Nature de la mission :** Audit technique indépendant en lecture seule (aucune correction appliquée)  
**Dépôt audité :** `/home/mahou-dev/Documents/Ecourtier2026-compliance`  
**Branche active :** `fix/chatbot-nlu-gemini-prod` (`60f9fc8`, alignée sur `origin/main`)  
**Verdict pré-fusion :** **C — FUSION BLOQUÉE** (`1` anomalie `P0`, `4` anomalies `P1` bloquantes, `5` anomalies `P2`, `1` anomalie `P3`)

---

## 1. Résumé exécutif

L'audit indépendant des modifications apportées par la mission `RENDER-PAYTECH-01` (36 fichiers suivis modifiés + 13 fichiers de code/test/migration non suivis) confirme des progrès architecturaux et sécuritaires réels :
- La configuration par défaut reste sûre (`PAYMENT_PROVIDER=mock`, `render.yaml` inchangé, aucun secret réel dans Git ni dans le bundle frontend `dist/`).
- La faille critique **`ESCROW-SIGN-001`** (usurpation de signature par `ROLE_ADMIN`) est solidement corrigée sur quatre couches indépendantes (Spring Security SpEL `isContractParty`, contrôleur, service métier, interface React).
- L'abstraction unifiée `PaymentGateway`, la persistance des opérations simulées en base (`MockPaymentGateway` + `paiements_wave`), la migration additive `V23` et la parité OpenAPI sont globalement cohérentes.
- Les 470 tests backend (dont seuil JaCoCo `>= 60 %`) et les 15 tests Playwright mockés annoncés sont reproductibles.

Cependant, l'audit indépendant a mis en évidence **5 anomalies bloquantes (`1 P0` et `4 P1`)** qui empêchent la fusion en l'état :
1. **`AUDIT-PT-001` (P0 — Bloquant)** : Absence de garde-fou backend bloquant l'appel réseau `POST /payment/request-payment` et l'exposition de `redirectUrlPaiement` lorsque `PAYMENT_PROVIDER=paytech`, `PAYTECH_ENV=test` et `zeroDebitGuaranteed=false` (risque de création de session checkout capable de débiter 100 à 150 FCFA réels).
2. **`AUDIT-PT-002` (P1 — Bloquant)** : Faiblesses critiques dans `PayTechWebhookService` : le repli statique `api_key_sha256` / `api_secret_sha256` reste accepté sur `sale_complete` et `sale_canceled`, permettant le contournement total du contrôle de montant si `item_price`/`final_item_price` sont omis (`receivedAmount == null` ignoré dans `TransactionService:1546`), la devise `XOF` n'est jamais contrôlée et un champ `env` absent est accepté.
3. **`AUDIT-PT-003` (P1 — Bloquant)** : Régression CI prouvée sur PostgreSQL 16 : **`FlywayFreshMigrationIntegrationTest` échoue** (`expected: <22> but was: <23>`), ce qui fait échouer le job CI #3 (`postgres16-flyway-integration`) défini dans `.github/workflows/ci.yml`.
4. **`AUDIT-PT-004` (P1 — Bloquant)** : Résolution IPN trop permissive via `custom_field` sans contrôle de correspondance `ref_command`/`token` vis-à-vis de la transaction enregistrée.
5. **`AUDIT-PT-005` (P1 — Bloquant)** : Régression d'état terminal dans `handleExternalTransferNotification` (`transfer_failed` écrase un transfert `CONFIRME`) et absence de re-vérification d'état en Phase 3 de `effectuerPaiementCaution` (cas `PENDING`) après appel HTTP hors verrou SQL.

**Conclusion :** **Verdict `C — FUSION BLOQUÉE`**. Une mission de remédiation ciblée corrigeant `AUDIT-PT-001` à `AUDIT-PT-005` (et idéalement `AUDIT-PT-006` à `AUDIT-PT-010`) suivie d'une re-validation CI complète est requise avant tout commit/fusion.

---

## 2. État initial Git (relevé avant inspection et préservé intégralement)

- **Répertoire de travail :** `/home/mahou-dev/Documents/Ecourtier2026-compliance`
- **Branche active :** `fix/chatbot-nlu-gemini-prod` (tracking `origin/fix/chatbot-nlu-gemini-prod`)
- **HEAD :** `60f9fc811ada4930ef47f5fe41fa9f07e0d8929f` (`60f9fc8`, aligné sur `origin/main`, `origin/HEAD`)
- **Index Git (`git diff --cached --stat`) :** vide (aucun fichier stagé).
- **Différences suivies non stagées :** `36 files changed, 2756 insertions(+), 655 deletions(-)` (`render.yaml` : `0` modification).
- **Fichiers non suivis avant audit (`15` fichiers/dossiers) :**
  - `backend/src/main/java/com/ecourtier/domain/transaction/controller/PayTechWebhookController.java`
  - `backend/src/main/java/com/ecourtier/domain/transaction/dto/PayTechWebhookPayloadDTO.java`
  - `backend/src/main/java/com/ecourtier/domain/transaction/dto/PayTechWebhookResponseDTO.java`
  - `backend/src/main/java/com/ecourtier/domain/transaction/gateway/` (`MockPaymentGateway.java`, `PaymentGateway.java`, `PayTechClient.java`, `PayTechPaymentGateway.java`, `PayTechPaymentProperties.java`)
  - `backend/src/main/java/com/ecourtier/domain/transaction/service/PayTechWebhookService.java`
  - `backend/src/main/resources/db/migration/V23__paytech_gateway_and_signature_traceability.sql`
  - `backend/src/test/java/com/ecourtier/domain/transaction/controller/PayTechEndToEndSandboxIntegrationTest.java`
  - `backend/src/test/java/com/ecourtier/domain/transaction/gateway/PayTechGatewayAndClientUnitTest.java`
  - `frontend/e2e/paytech-sandbox-ui.spec.ts`
  - `rapport_audit_senior_paiement_sequestre_wave.md`
  - `rapport_render_paytech_01_integration_sandbox.md`
- **Worktrees existantes (9, toutes préservées) :**
  - `/home/mahou-dev/Documents/Ecourtier2026` (`68ba303`, `feature/frontend-redesign-premium-saas`)
  - `/home/mahou-dev/Documents/Ecourtier2026-compliance` (`60f9fc8`, `fix/chatbot-nlu-gemini-prod`)
  - `/home/mahou-dev/Documents/Ecourtier2026-convergence` (`6603841`, `integration/phase-9d-convergence`)
  - `/home/mahou-dev/Documents/Ecourtier2026-fix-header-logout` (`b9258f5`, `fix/header-logout-aria-label`)
  - `/home/mahou-dev/Documents/Ecourtier2026-lot1`, `-lot2`, `-lot3`, `-validation` (`d161762`, detached HEAD)
  - `/home/mahou-dev/Documents/Ecourtier2026-render-gemini` (`f99bb06`, `fix/render-gemini-prod-validator`)

---

## 3. Périmètre exact inspecté

- **Backend paiement :** [PaymentGateway.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/gateway/PaymentGateway.java), [PayTechPaymentProperties.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/gateway/PayTechPaymentProperties.java), [PayTechClient.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/gateway/PayTechClient.java), [PayTechPaymentGateway.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/gateway/PayTechPaymentGateway.java), [MockPaymentGateway.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/gateway/MockPaymentGateway.java), [WaveClient.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/client/WaveClient.java), [WaveClientMock.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/client/WaveClientMock.java), [PayTechWebhookController.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/controller/PayTechWebhookController.java), [PayTechWebhookService.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/service/PayTechWebhookService.java), [PayTechWebhookPayloadDTO.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/dto/PayTechWebhookPayloadDTO.java), [WaveWebhookController.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/controller/WaveWebhookController.java), [WaveWebhookService.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/service/WaveWebhookService.java).
- **Backend métier & sécurité :** [TransactionService.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionService.java), [TransactionController.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/controller/TransactionController.java), [TransactionSecurityService.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/common/security/TransactionSecurityService.java), [WavePaymentReconciliationService.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/service/WavePaymentReconciliationService.java), [TransactionExpirationScheduler.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionExpirationScheduler.java), [PdfBailGeneratorService.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/service/PdfBailGeneratorService.java), [TransactionEntity.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/entity/TransactionEntity.java), [PaiementWaveEntity.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/entity/PaiementWaveEntity.java), [TransactionRepository.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/repository/TransactionRepository.java), [PaiementWaveRepository.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/repository/PaiementWaveRepository.java), [TransactionResponseDTO.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/dto/TransactionResponseDTO.java), [TransactionMapper.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/mapper/TransactionMapper.java), [ProductionConfigurationValidator.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/common/config/ProductionConfigurationValidator.java), [SecurityConfig.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/common/config/SecurityConfig.java).
- **Base de données & configuration :** inventaire `V1..V23`, lecture approfondie de `V13`, `V14`, `V18`, `V19`, `V20`, `V22`, [V23__paytech_gateway_and_signature_traceability.sql](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/resources/db/migration/V23__paytech_gateway_and_signature_traceability.sql), [application.yml](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/resources/application.yml), [application-dev.yml](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/resources/application-dev.yml), [application-prod.yml](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/resources/application-prod.yml), [application-test.yml](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/test/resources/application-test.yml), [.env.example](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/.env.example), [render.yaml](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/render.yaml), [.github/workflows/ci.yml](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/.github/workflows/ci.yml).
- **Frontend & bundle :** [TransactionDetailsModal.tsx](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/frontend/src/components/transaction/TransactionDetailsModal.tsx), [PropertyDetailPage.tsx](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/frontend/src/pages/PropertyDetailPage.tsx), [TransactionsTab.tsx](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/frontend/src/components/dashboard/TransactionsTab.tsx), [AdminTransactionsPage.tsx](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/frontend/src/pages/AdminTransactionsPage.tsx), [api.ts](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/frontend/src/services/api.ts), [index.ts](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/frontend/src/types/index.ts), [dashboardModel.ts](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/frontend/src/lib/dashboardModel.ts), et scan de `frontend/dist/`.
- **Tests & contrats :** [PayTechGatewayAndClientUnitTest.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/test/java/com/ecourtier/domain/transaction/gateway/PayTechGatewayAndClientUnitTest.java), [PayTechEndToEndSandboxIntegrationTest.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/test/java/com/ecourtier/domain/transaction/controller/PayTechEndToEndSandboxIntegrationTest.java), [BailSignatureLifecycleIntegrationTest.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/test/java/com/ecourtier/domain/transaction/controller/BailSignatureLifecycleIntegrationTest.java), [Postgres16Phase3IntegrationTest.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/test/java/com/ecourtier/common/db/Postgres16Phase3IntegrationTest.java), [FlywayFreshMigrationIntegrationTest.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/test/java/com/ecourtier/common/config/FlywayFreshMigrationIntegrationTest.java), [paytech-sandbox-ui.spec.ts](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/frontend/e2e/paytech-sandbox-ui.spec.ts), [phase3-real-backend.spec.ts](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/frontend/e2e/phase3-real-backend.spec.ts), [csp-security-compliance.spec.ts](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/frontend/e2e/csp-security-compliance.spec.ts), comparaison directe avec la documentation officielle PayTech (`doc.paytech.sn` + collection Postman officielle).

---

## 4. Méthodologie

1. Relevé et gel vérifié de l'état Git (aucune modification ni commande destructive).
2. Relecture critique ligne par ligne de chaque classe Java, migration SQL, configuration YAML/CI et composant TypeScript/React.
3. Confrontation directe de `PayTechClient` et `PayTechWebhookService` aux spécifications officielles PayTech (endpoints, payloads, headers, HMAC, fallback SHA-256, réponses JSON).
4. Recherche systématique de chemins alternatifs (`WaveWebhookController`, `WaveClient`, `TransactionController`, tâches `@Scheduled`, `TransactionMapper`).
5. Ré-exécution indépendante en local isolé (zéro réseau financier, zéro écriture production) de la suite backend + `jacoco:check`, de la suite CI complète PostgreSQL 16 Testcontainers (incluant `FlywayFreshMigrationIntegrationTest`), de la parité OpenAPI, du lint/TSC frontend et des 15 tests Playwright.

---

## 5. Vérification des déclarations RENDER-PAYTECH-01

| Déclaration RENDER-PAYTECH-01 | Vérification indépendante | Verdict |
| :--- | :--- | :--- |
| Abstraction unifiée `PaymentGateway` + `PayTechPaymentGateway` + `PayTechClient` + `MockPaymentGateway` | Présente et utilisée dans `TransactionService` et `WavePaymentReconciliationService`. Aucun bean `src/main` n'injecte plus `WaveClient`. Toutefois aucun `@SpringBootTest` ne démarre avec `ecourtier.payment.provider=paytech`. | **Partiellement vérifiée** |
| Webhook `PayTechWebhookService` authentifié et sûr | Authentification HMAC présente, mais **faille P1 (`AUDIT-PT-002`)** : fallback SHA-256 accepté sur `sale_complete` avec contournement du montant si `item_price` absent, devise non vérifiée, `env` optionnel, fallback `custom_field` trop permissif (`AUDIT-PT-004`). | **Réfutée (P1 bloquants)** |
| Migration Flyway V23 validée sur PostgreSQL 16 | `V23` s'applique bien sur PostgreSQL 16.15 avec `ddl-auto: validate`, **mais casse `FlywayFreshMigrationIntegrationTest`** (`expected <22> but was <23>`) — échec prouvé du job CI #3 (`AUDIT-PT-003`). | **Partiellement vérifiée (CI cassée)** |
| Suppression du droit de signature ADMIN (`ESCROW-SIGN-001`) | Confirmée sur 4 niveaux (`isContractParty`, `@PreAuthorize`, `TransactionService.signerBail`, UI `TransactionDetailsModal`). Aucun chemin alternatif trouvé. | **Confirmée** |
| Simulation locale persistante (paiements, remboursements, transferts) | Persistée en base `paiements_wave` ; mais `MockPaymentGateway.findPersistedPayment()` accepte à tort `EN_COURS` (`AUDIT-PT-008`) et `refundByOriginalPaymentReference` reste volatil en appel direct. | **Partiellement vérifiée** |
| Variable `PAYTECH_ZERO_DEBIT_GUARANTEED` documentée dans `.env.example` et `application.yml` | **Inexact** : absente de `.env.example` et codée en dur `zero-debit-guaranteed: false` dans `application.yml:101` (et ignorée par `PayTechPaymentGateway` + `TransactionMapper`). | **Réfutée** |
| 470 tests backend réussis + JaCoCo | Reproduit exactement : `470 exécutés, 0 échec, 0 erreur, 6 ignorés`, `jacoco:check` validé. | **Confirmée** |
| 4 tests PostgreSQL 16 réussis | `Postgres16Phase3IntegrationTest` passe `4/4` et `Postgres16IaConcurrencyIntegrationTest` passe `1/1`, **mais le 6e test PG16 (`FlywayFreshMigrationIntegrationTest`) échoue (`1/1` en échec)**. | **Incomplète (échec CI masqué)** |
| 15 tests Playwright réussis | Reproduit exactement (`15/15`, `30.7s`). Tous exécutés sur routes API mockées (`page.route`) ; `e2e/phase3-real-backend.spec.ts` (backend réel) n'a pas été exécuté. | **Confirmée (avec nuance mock)** |

---

## 6. Audit critique du zéro débit réel

### Analyse complète des chemins réseau et drapeaux de sécurité
- **Endpoints PayTech référencés dans `src/main` :** uniquement `POST {baseUrl}/payment/request-payment` (`PayTechClient.java:121`) et `GET {baseUrl}/payment/get-status?token_payment={token}` (`PayTechClient.java:179`). **Aucune occurrence** de `/payment/refund-payment`, `/transfer/transferFund` ni aucun autre endpoint de décaissement dans `src/main`.
- **Activation de `PayTechPaymentGateway` :** `@ConditionalOnProperty(name = "ecourtier.payment.provider", havingValue = "paytech")`. Si `PAYMENT_PROVIDER` vaut `mock` ou est absent -> `WaveClientMock` (`MockPaymentGateway`). Si valeur inconnue -> échec de démarrage Spring (fail-closed).
- **Valeurs de `PAYTECH_ENV` :**
  - Si `PAYTECH_ENV=prod` (ou toute valeur non vide `!= test`) avec `PAYMENT_PROVIDER=paytech` : bloqué au démarrage par `ProductionConfigurationValidator:111` et à l'exécution par `PayTechPaymentProperties.assertSandboxOnly():49`.
  - Si `PAYTECH_ENV` est absent : `application.yml:91` résout `test`.
  - Si `PAYTECH_ENV=""` (chaîne vide explicite) : `ProductionConfigurationValidator:110` la normalise en `"test"` (démarre), mais `PayTechPaymentProperties.assertSandboxOnly():49` la rejette à l'exécution (`503`).
- **Faille critique P0 (`AUDIT-PT-001`) sur `zeroDebitGuaranteed` :**
  - `PayTechPaymentProperties.zeroDebitGuaranteed` vaut `false` par défaut (`application.yml:101`, non mappé sur `${PAYTECH_ZERO_DEBIT_GUARANTEED:...}`, bien que surchargeable par binding relaxé Spring `ECOURTIER_PAYMENT_PAYTECH_ZERO_DEBIT_GUARANTEED=true`).
  - **Problème critique :** `PayTechPaymentProperties.validateSandboxReady()`, `PayTechPaymentGateway.initiatePayment()` et `PayTechClient.requestPayment()` **ne vérifient JAMAIS `zeroDebitGuaranteed`**. Dès que `PAYMENT_PROVIDER=paytech`, `PAYTECH_ENV=test` et les clés/URLs sont configurés, un appel `POST /api/v1/transactions/{id}/paiement-caution` exécute réellement `POST https://paytech.sn/api/payment/request-payment` et retourne l'URL de paiement active `redirectUrlPaiement` dans le JSON de réponse (utilisable directement par un client API pour déclencher le débit de 100–150 FCFA).
  - Côté `TransactionMapper.java:53`, `.zeroDebitGaranti(Boolean.TRUE.equals(entity.getModeSimulationPaiement()))` renvoie toujours `false` pour un paiement PayTech (même si `zeroDebitGuaranteed=true`), ce qui bloque la redirection automatique dans `TransactionDetailsModal.tsx:156` mais laisse le backend sans protection indépendante.
- **Remboursement & Transfert propriétaire :** `PayTechPaymentGateway.initiateRefund()` (L124–145) et `initiateSimulatedOwnerTransfer()` (L155–169) n'effectuent **aucun appel HTTP** et produisent exclusivement des simulations locales (`SIM-REFUND-...`, `SIM-TRANSFER-...`, `TRANSFERT_SIMULE`).
- **Tâche planifiée `WavePaymentReconciliationService` :** ne déclenche aucun paiement ni transfert réel (elle appelle uniquement `checkPaymentStatus` et `initiateRefund` simulé pour `TARDIF_A_REMBOURSER`).

### Matrice de vérification zéro débit réel (Section 5)

| Scénario | Résultat attendu | Résultat observé (preuve code) | Verdict |
| :--- | :--- | :--- | :--- |
| Configuration par défaut (`PAYMENT_PROVIDER` absent / `mock`) | Mock local | `WaveClientMock` (`MockPaymentGateway`) actif, références `SIM-LOCAL-`, 0 appel HTTP | **CONFORME** |
| PAYTECH activé sans clés | Refus sécurisé | Démarre avec `WARN` (`ProductionConfigurationValidator:122`), puis refuse tout appel via `validateSandboxReady()` -> `503` | **CONFORME** |
| PAYTECH avec `env=prod` | Refus absolu | Bloqué au démarrage (`ProductionConfigurationValidator:111`), dans `PayTechPaymentProperties:49`, `PayTechClient:278`, `PayTechWebhookService:268` | **CONFORME** |
| PAYTECH avec `env=test` (clés présentes) | Aucun paiement réseau sans zéro-débit garanti | **ÉCHEC (`AUDIT-PT-001`)** : `PayTechClient.requestPayment()` exécute `POST /payment/request-payment` et expose `redirectUrlPaiement` même si `zeroDebitGuaranteed=false` | **NON CONFORME (P0)** |
| `zeroDebitGuaranteed=false` | Aucune redirection ni paiement | Frontend bloque l'auto-redirection (`TransactionDetailsModal.tsx:156`), **mais le backend lance quand même la requête PayTech et retourne `redirectUrlPaiement`** | **NON CONFORME (P0)** |
| Remboursement | Simulation locale uniquement | `PayTechPaymentGateway.initiateRefund()` et `MockPaymentGateway.initiateRefund()` : 100 % simulation locale (`SIM-REFUND-`), 0 appel `/payment/refund-payment` | **CONFORME** |
| Transfert propriétaire | Simulation locale uniquement | `initiateSimulatedOwnerTransfer()` : 100 % simulation locale (`SIM-TRANSFER-`, `TRANSFERT_SIMULE`), 0 appel `/transfer/transferFund` | **CONFORME** |

---

## 7. Audit de l'authentification et du client PayTech (`PayTechClient`)

Comparaison directe de [PayTechClient.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/gateway/PayTechClient.java) avec la documentation officielle (`https://doc.paytech.sn/doc_paytech.php`) et la collection Postman officielle PayTech :

| Élément | Documentation officielle PayTech | Implémentation `PayTechClient.java` | Conformité / Hypothèse |
| :--- | :--- | :--- | :--- |
| Méthode & endpoint init | `POST https://paytech.sn/api/payment/request-payment` | `POST {baseUrl}/payment/request-payment` (L121–128) | **Confirmé** |
| Headers auth | `API_KEY`, `API_SECRET` | `.header("API_KEY", ...)` / `.header("API_SECRET", ...)` (L131–132) | **Confirmé** |
| Paramètres payload | `item_name`, `item_price` (number), `currency` (`XOF`), `ref_command`, `command_name`, `env` (`test`), `target_payment` (`Wave`), `ipn_url` (HTTPS), `refund_notif_url` (HTTPS), `success_url`, `cancel_url`, `custom_field` (string JSON) | Tous présents avec types exacts (`long` XOF, `custom_field` JSON sérialisé, `env="test"`, `target_payment="Wave"`) (L99–119) | **Confirmé** (sauf `refund_notif_url` non validé en HTTPS et `http://localhost` toléré sur `ipn_url`) |
| Réponse init | `{"success": 1, "token": "...", "redirect_url": "...", "redirectUrl": "..."}` | `PayTechRawPaymentResponse` vérifie `success == 1`, `token`, `redirect_url`/`redirectUrl` (L216–234) | **Partiel (`AUDIT-PT-006`)** : aucune validation de schéma/hôte sur `redirect_url` |
| Endpoint statut | `GET /payment/get-status?token_payment={token}` (headers `API_KEY`/`API_SECRET`) | Identique (L179–189) | **Endpoint confirmé** |
| **Structure JSON réponse `get-status`** | **NON DOCUMENTÉE** (ni sur `doc.paytech.sn` L870–882, ni dans `paytech_postman.json`) | Parse `type_event`, `status`, `payment_status`, `payment.status`, `payment.state`, `env`, `payment.env`, `final_item_price`, `item_price` (L274–293) | **HYPOTHÈSE NON CONFIRMÉE (`AUDIT-PT-007`)** + substitution silencieuse du montant nul dans `PayTechPaymentGateway:117` + rejet uniquement de `"prod"` (pas `!= "test"`) |

---

## 8. Audit critique des webhooks (`PayTechWebhookController` / `PayTechWebhookService` / `WaveWebhookService`)

- **HMAC-SHA256 (`hmac_compute`) :**
  - Formules conformes à la documentation officielle : `${final_item_price || item_price}|${ref_command}|${api_key}` (paiements) et `${amount}|${id_transfer}|${api_key}` (transferts), clé `api_secret`, comparaison en temps constant `MessageDigest.isEqual` (`PayTechWebhookService.java:329–364`).
  - Note : contrairement à un HMAC sur le corps brut HTTP, PayTech signe une concaténation de champs métier. Cela rend d'autant plus critique la validation des champs **non couverts par le HMAC** (`type_event`, `currency`, `env`, `token`, `custom_field`).
- **Failles identifiées sur les webhooks :**
  1. **`AUDIT-PT-002` (P1 bloquant) — Contournement du montant, absence de contrôle devise & repli SHA-256 trop large :**
     - `verifierAuthenticitePayTech` (L283–326) autorise le repli `api_key_sha256` + `api_secret_sha256` sur **tous** les événements, y compris `sale_complete` et `sale_canceled` (alors que PayTech envoie `hmac_compute` sur ces événements). Ces deux empreintes sont statiques et ne protègent pas l'intégrité du payload.
     - En l'absence de `hmac_compute`, aucun montant n'est exigé ; `handleSaleComplete` (L124) produit `receivedAmount = null`, qui court-circuite la comparaison dans `TransactionService:1546` (`if (expectedAmount != null && ...)`).
     - `payload.getCurrency()` n'est jamais comparé à `XOF` ; `payload.getEnv() == null` n'est pas rejeté (L268).
  2. **`AUDIT-PT-004` (P1 bloquant) — Résolution permissve via `custom_field` sans contrôle `ref_command`/`token` :**
     - `resolveTransactionId` (L366–396) bascule sur `custom_field` lorsque `ref_command` et `token` sont inconnus, et `confirmWavePaymentInTransaction` ne vérifie pas que `refCommand` et `token` correspondent aux valeurs déjà initialisées sur `tx`.
  3. **`AUDIT-PT-005` (P1 bloquant) — Régression sur `transfer_failed` après `transfer_success` :**
     - `TransactionService.handleExternalTransferNotification` (L1856–1874) écrase `CONFIRME` par `ECHOUE`.
  4. **`AUDIT-PT-009` (P2) — Routage `PayTechWebhookController` & webhook hérité `/api/v1/webhooks/wave` :**
     - `PayTechWebhookController:63–64` priorise `request.getParameterMap()` sur le corps JSON dès qu'un query param existe dans l'URL, et lit le flux sans plafond de taille.
     - `/api/v1/webhooks/wave` reste `permitAll()` dans `SecurityConfig:95` (rejeté `401` par défaut tant que `WAVE_WEBHOOK_SECRET` est vide, mais contourne `env=test` et accepte `amount == null` si ce secret venait à être défini).
- **Points positifs vérifiés :** rejeu `sale_complete` idempotent (`idempotentReplay=true`), paiement tardif sur transaction `EXPIRE`/`ANNULE`/`REMBOURSE` marqué `TARDIF_A_REMBOURSER` sans réactiver la transaction, `sale_canceled` après `BLOQUE` sans régression (`L1747`), `sanitizeAuditPayload` exclut les secrets, hashes d'auth et numéros de téléphone client.

---

## 9. Audit financier et machine à états (`TransactionService`)

- **Transitions vérifiées (`INITIALISE`, `BLOQUE`, `LIBERE`, `REMBOURSE`, `ANNULE`, `EXPIRE`) :**
  - Calcul des montants 100 % côté serveur en `BigDecimal` (`initierTransaction` L326–339) : LOCATION (`caution = prix`, `1er loyer = prix`, `frais = 5000`), VENTE (`acompte = prix * 10%` arrondi `HALF_UP` à `0` décimale, `1er loyer = 0`, `frais = 5000`). Tout montant envoyé par le client est ignoré.
  - Arrondi XOF dans `PayTechClient:80` : `setScale(0, RoundingMode.HALF_UP).longValueExact()` cohérent avec les montants FCFA entiers.
  - Remboursement impossible après `LIBERE` ou `SIGNE_COMPLET` (`L812–826`) ; annulation impossible après paiement ou signature (`L674–693`) ; paiements tardifs isolés en `TARDIF_A_REMBOURSER` sans rouvrir un dossier clos (`L1561–1588`).
  - Libération logique uniquement après double signature (`SIGNE_COMPLET` + `BLOQUE`, `L1121`) ; transfert propriétaire explicitement simulé (`TRANSFERT_SIMULE`, `SIM-TRANSFER-...`).
- **Anomalies constatées dans la machine à états :**
  - `AUDIT-PT-005` (P1) : absence de re-vérification `statutSequestre == INITIALISE && dossierActif == true` en Phase 3 de `effectuerPaiementCaution` (Cas A `PENDING`, L520–554) ; et `handleExternalTransferNotification` (L1856–1874) permet `CONFIRME -> ECHOUE`.
  - `AUDIT-PT-008` (P2) : `WavePaymentReconciliationService.marquerEchec()` ne remet pas à jour `TransactionEntity.statutPaiementExterne`.

---

## 10. Audit de la faille ADMIN (`ESCROW-SIGN-001`) et des signatures (`ESCROW-SIGN-002`)

### Contrôle multi-couches `ESCROW-SIGN-001` — **VALIDÉ**
- **Spring Security / SpEL :** `TransactionController:187` `@PreAuthorize("@transactionSecurityService.isContractParty(#id, authentication)")`. `TransactionSecurityService.isContractParty()` (L43–62) ne comporte aucune clause `isAdmin` : seuls les emails exacts de `tx.locataire` ou `tx.proprietaire` retournent `true`.
- **Service métier :** `TransactionService.signerBail()` (L999–1008) re-vérifie `isLocataire || isProprietaire` et lève `403 Forbidden` pour tout tiers ou `ROLE_ADMIN`.
- **Unicité par partie :** `appliquerSignatureLocataire` et `appliquerSignatureProprietaire` (L1223–1237) lèvent `400 Bad Request` en cas de double signature par la même partie.
- **Chemins alternatifs :** `grep` exhaustif sur `signerBail`, `/bail/signer`, `appliquerSignature`, `SIGNE_COMPLET` confirme qu'il n'existe **aucun autre endpoint ou méthode** permettant à `ADMIN` de signer ou de déclencher `SIGNE_COMPLET`/`LIBERE`. (Note : `ADMIN` conserve le droit d'appeler `/paiement-caution` et `/annuler` via `isLocataireOrAdmin`, ainsi que `/rembourser`, mais ne peut jamais signer.)
- **Frontend :** `TransactionDetailsModal.tsx:834–848` n'affiche le bouton de signature qu'aux parties et affiche un encart explicite pour `ADMIN`.

### Audit `ESCROW-SIGN-002` (SHA-256 & Aperçu PDF) — **VALIDÉ AVEC RÉSERVES (P2/P3)**
- L'aperçu pré-signature (`GET /api/v1/transactions/{id}/bail/apercu-pdf`) est fonctionnel, réservé aux parties/ADMIN, et n'est accessible qu'en statut `BLOQUE`/`LIBERE`.
- L'empreinte `bailDocumentSha256` est figée dès confirmation du paiement (`L1601–1603`) ou première signature (`L1079–1080`), re-vérifiée avant toute signature (`L1081–1087`, `409 Conflict` si divergente), et les traces `signatureLocataireHash`/`signatureProprietaireHash` sont intégrées au PDF final avec **mention explicite qu'il ne s'agit pas d'une signature électronique qualifiée certifiée**.
- **Limites constatées :**
  1. `computeCanonicalContractSha256()` (`PdfBailGeneratorService.java:43–59`) ne couvre pas `annonce.titre`, `annonce.quartier`, `annonce.ville`, `annonce.prix` (VENTE) ni les noms/emails des parties pourtant imprimés dans le PDF (`AUDIT-PT-011`).
  2. L'empreinte porte sur des champs canoniques, non sur le binaire PDF final (d'où absence de preuve cryptographique de conformité octet-à-octet entre aperçu et PDF final).
  3. Aucun test automatisé ne vérifie le rejet `409` en cas d'altération entre signature 1 et signature 2 (`AUDIT-PT-010`).

---

## 11. Audit de la migration Flyway V23

- **Compatibilité `V1..V22` et PostgreSQL 16 :** `V23__paytech_gateway_and_signature_traceability.sql` est strictement additive, sans destruction ni `UPDATE` massif, et s'exécute proprement sur PostgreSQL `16.15` (`0.232s`) avec validation Hibernate `ddl-auto: validate` réussie.
- **Préservation des données historiques :** les nouvelles colonnes `NOT NULL` ont toutes des valeurs `DEFAULT` constantes (`prestataire='MOCK_LOCAL'`, `moyen_paiement='Wave'`, `environnement='test'`, `mode_simulation=TRUE`, `mode_simulation_paiement=TRUE`), exécutées en métadonnées (instantané sur PostgreSQL 11+, bref verrou `ACCESS EXCLUSIVE`). Les anciens statuts `paiements_wave` (`V19`) sont tous inclus dans `chk_paiements_wave_statut`.
- **Contraintes CHECK & index uniques partiels :**
  - Le bloc `DO $$` (L14–28) supprime proprement les contraintes CHECK anonymes de `V19` sur `type_operation` et `statut` avant de créer `chk_paiements_wave_type_operation` et `chk_paiements_wave_statut` (scan de validation sur `paiements_wave`, rapide sur table de taille normale).
  - L'index `uq_paiements_wave_tx_op_actif` est étendu à `EN_ATTENTE_CONFIRMATION` (sans inclure `ANNULE`/`ECHOUE`, ce qui autorise proprement une nouvelle tentative après annulation/échec).
  - Les 4 nouveaux index uniques partiels (`ref_command`, `token_externe`, `ref_command_paytech`, `token_paiement_externe`) ont tous `WHERE col IS NOT NULL` (zéro risque de doublon sur les lignes historiques qui valent `NULL`).
- **Anomalie bloquante liée à V23 (`AUDIT-PT-003`, P1) :** `FlywayFreshMigrationIntegrationTest.java:68` exige encore exactement `version == "22"` et `applied.length == 22`, provoquant un échec systématique du job CI #3 PostgreSQL 16.
- **Écart mineur (`AUDIT-PT-011`, P3) :** `transactions.reference_wave` reste `VARCHAR(100)` alors que `paiements_wave.reference_externe` et `transactions.token_paiement_externe` sont en `VARCHAR(150)`.

---

## 12. Audit de concurrence et d'idempotence

- **Mécanismes correctement préservés :**
  - Verrouillage pessimiste ordonné canonique `Annonce -> Transaction` (`lockAnnonceThenTransaction`, L174–178) respecté partout.
  - Aucun appel HTTP (`initiatePayment`, `checkPaymentStatus`, `initiateRefund`) exécuté sous verrou SQL (`effectuerPaiementCaution`, `rembourserTransaction`, `verifierStatutPaiementExterne` utilisent tous le découpage Phase 1 verrou court -> Phase 2 hors transaction -> Phase 3 verrou court).
  - Clés d'idempotence uniques, index unique partiel `uq_paiements_wave_tx_op_actif` incluant `EN_ATTENTE_CONFIRMATION`, retour idempotent sur double appel `effectuerPaiementCaution` lorsque déjà `EN_ATTENTE_CONFIRMATION` (L425–438).
- **Failles de concurrence / idempotence constatées :**
  1. **`AUDIT-PT-005` (P1 bloquant) :** Phase 3 Cas A (`effectuerPaiementCaution` L520–554) ne re-vérifie pas `statutSequestre == INITIALISE && dossierActif == true` après le retour HTTP (course avec `annulerTransaction`, expiration ou IPN anticipé), et `handleExternalTransferNotification` (L1856) permet à un événement `transfer_failed` hors ordre d'écraser `CONFIRME`.
  2. **`AUDIT-PT-008` (P2) :** `WavePaymentReconciliationService.scheduledReconcile()` n'est pas protégé par `SchedulerLockService` en multi-instances, peut marquer `paiements_wave` en `ECHOUE` après 2 minutes sans mettre à jour `transactions.statut_paiement_externe`, et `MockPaymentGateway.findPersistedPayment()` traite `EN_COURS` comme un paiement confirmé.
  3. **`TransactionService:1173` (note mineure) :** le `try/catch` autour de `paiementWaveRepository.saveAndFlush(transferLog)` à l'intérieur d'une transaction PostgreSQL ne protège pas contre l'état `current transaction is aborted` si la contrainte échoue (comportement finalement sûr par rollback, mais gestion d'erreur trompeuse).

---

## 13. Audit frontend

- **Comportements vérifiés conformes :**
  - Aucun faux succès affiché lorsque `statutSequestre=INITIALISE` et `statutPaiementExterne=EN_ATTENTE_CONFIRMATION` : panneau `paytech-pending-panel` avec avertissement 100–150 FCFA et bouton « Vérifier le statut du paiement ».
  - Signature verrouillée jusqu'à confirmation backend authentifiée ; aucun bouton de signature pour `ADMIN`.
  - Références `SIM-LOCAL-` et transferts `SIM-TRANSFER-` (`TRANSFERT_SIMULE`) explicitement étiquetés comme simulations locales sans mouvement financier réel ; montants issus du serveur ; bundle `frontend/dist/` exempt de tout secret (`CLEAN_DIST`).
- **Failles et écarts frontend identifiés :**
  1. **`AUDIT-PT-006` (P2) — Absence de garde anti-open-redirect / XSS sur `window.location.assign` :** `TransactionDetailsModal.tsx:156–158` redirige directement vers `res.redirectUrlPaiement` sans valider ni le protocole `https:` ni le domaine `paytech.sn`.
  2. **`AUDIT-PT-010` (P2) — Couverture E2E partielle :** `paytech-sandbox-ui.spec.ts` fonctionne uniquement sur routes mockées et ne teste ni la persistance visuelle après rechargement de page (`page.reload()`), ni l'affichage des erreurs réseau `502`/`503`/`504`.
  3. **`AUDIT-PT-011` (P3) — Terminologie résiduelle :** mentions « Séquestre » encore présentes dans `TransactionsTab.tsx` (L52, L185, L210–211), `AdminTransactionsPage.tsx` (L85, L99, L194, L247–248) et `frontend/index.html:8` ; badge `Remboursé (simulé)` codé en dur dans `AdminTransactionsPage.tsx:69`.

---

## 14. Audit sécurité, configuration, CSP/CORS et secrets

- **Secrets :** aucun secret réel détecté dans Git, les tests ou `frontend/dist/`. `PayTechPaymentProperties.toString()` redacte `apiKey`/`apiSecret`, `PayTechClient.sanitizeErrorMessage()` masque les clés dans les messages d'erreur, `PayTechWebhookService.sanitizeAuditPayload()` n'enregistre ni secrets, ni hashes d'auth, ni `client_phone`/`destination_number` en base.
- **Render & CSP/CORS :** `render.yaml` strictement intact (`git diff render.yaml` vide). `csp-security-compliance.spec.ts` passe `2/2` (`script-src 'self'`, absence `unsafe-eval`, `object-src 'none'`, `form-action 'self'`, `frame-ancestors 'self'`). CORS production strictement validé par `ProductionConfigurationValidator` (HTTPS obligatoire, pas de `*`, pas de `localhost`).
- **OpenAPI :** `cmp -s docs/openapi.json backend/src/main/resources/static/openapi.json` confirme l'identité binaire exacte (`OPENAPI_IDENTICAL`).
- **Points d'attention sécurité :** `AUDIT-PT-001` (P0), `AUDIT-PT-002` (P1), `AUDIT-PT-004` (P1), `AUDIT-PT-006` (P2), `AUDIT-PT-009` (P2), ainsi que la divergence mineure `PAYTECH_ENV=""` entre `ProductionConfigurationValidator` et `PayTechPaymentProperties` (`AUDIT-PT-011`).

---

## 15. Résultats des tests indépendants (exécutés localement en isolation)

| Vérification / Commande exécutée | Catégorie | Résultat exact observé | Commentaire d'audit |
| :--- | :--- | :--- | :--- |
| `mvn -B -f backend/pom.xml test jacoco:check -Djacoco.skip=false` | Tests unitaires + intégration H2 + JaCoCo | **470 exécutés, 0 échec, 0 erreur, 6 ignorés** — `All coverage checks have been met` (`01:49 min`) | Reproductible à 100 %. Les 6 ignorés sont les tests gardés par `-Decourtier.test.pg16=true`. |
| `mvn -B -f backend/pom.xml test -Decourtier.test.pg16=true -Dtest=FlywayFreshMigrationIntegrationTest,Postgres16Phase3IntegrationTest,Postgres16IaConcurrencyIntegrationTest` | Intégration PostgreSQL `16.15` Testcontainers (commande CI job #3 exacte) | **6 exécutés, 1 ÉCHEC, 0 erreur, 0 ignoré (`BUILD FAILURE`, `39.15s`)** : `Postgres16Phase3IntegrationTest` **4/4 PASS**, `Postgres16IaConcurrencyIntegrationTest` **1/1 PASS**, **`FlywayFreshMigrationIntegrationTest` 0/1 FAIL** (`expected: <22> but was: <23>`) | **Preuve directe d'échec CI (`AUDIT-PT-003`)** masqué dans `RENDER-PAYTECH-01` qui n'avait lancé que `Postgres16Phase3IntegrationTest`. |
| `cmp -s docs/openapi.json backend/src/main/resources/static/openapi.json` + contrôle séquence Flyway | Précontrôles CI job #1 | **PASS** (`OPENAPI_IDENTICAL`, `23` migrations, `0` doublon) | Conforme. |
| `npx --prefix frontend tsc -p frontend/tsconfig.json --noEmit && npm --prefix frontend run lint` | Qualité frontend CI job #4 | **PASS** (`0` erreur TypeScript, `0` warning ESLint `--max-warnings 0`) | Conforme. |
| Playwright 5 specs (`paytech-sandbox-ui`, `phase2-security-ui`, `phase9h-admin-refund`, `csp-security-compliance`, `lot2-2-c-detail`) sur `vite preview` | E2E frontend simulé (`page.route`) | **15 exécutés, 15 réussis, 0 échec (`30.7s`)** | Reproductible ; mais **100 % sur backend mocké**. |
| `e2e/phase3-real-backend.spec.ts` (Playwright sur backend réel) | E2E full-stack CI job #5 | **Non exécuté** | Non lancé afin d'éviter toute création de fichiers PDF dans `backend/uploads/baux/` pendant cet audit strictement lecture seule. |
| Tests réseau live PayTech Sandbox | Réseau externe PayTech | **Non exécuté (interdit)** | Interdit par la règle zéro débit réel (100–150 FCFA en `env=test`) et absence de clés. |

---

## 16. Tableau des anomalies P0 à P3

| ID | Gravité | Fichier | Méthode | Preuve | Scénario (sans mouvement financier) | Impact | Recommandation | Bloquant fusion |
| :--- | :-: | :--- | :--- | :--- | :--- | :--- | :--- | :-: |
| **AUDIT-PT-001** | **P0** | `PayTechPaymentProperties.java`, `PayTechPaymentGateway.java`, `PayTechClient.java`, `TransactionMapper.java` | `validateSandboxReady()` L60–73, `initiatePayment()` L53–89, `requestPayment()` L67–164, `toResponseDTO()` L53 | `validateSandboxReady()` et `requestPayment()` ne vérifient jamais `zeroDebitGuaranteed` ; `POST /payment/request-payment` est exécuté et `redirectUrlPaiement` retourné en JSON même si `zeroDebitGuaranteed=false`. `TransactionMapper:53` ignore `properties.isZeroDebitGuaranteed()`. | Configurer `PAYMENT_PROVIDER=paytech`, `PAYTECH_ENV=test`, `zeroDebitGuaranteed=false` et appeler `POST /api/v1/transactions/{id}/paiement-caution` contre un mock HTTP : l'appel sortant part et `redirectUrlPaiement` est renvoyé au client. | Violation directe de l'exigence zéro débit réel (création d'une session checkout PayTech débitant 100–150 FCFA si URL ouverte via l'API). | Bloquer côté backend tout appel `request-payment` (retourner `503`) et ne jamais exposer `redirectUrlPaiement` tant que le zéro-débit n'est pas garanti par un verrou backend indépendant ; supprimer l'incohérence `TransactionMapper`. | **Oui** |
| **AUDIT-PT-002** | **P1** | `PayTechWebhookService.java`, `TransactionService.java` | `verifierAuthenticitePayTech()` L268–326, `handleSaleComplete()` L116–136, `confirmWavePaymentInTransaction()` L1546 | 1) Fallback statique `api_key_sha256`+`api_secret_sha256` accepté sur `sale_complete`/`sale_canceled`. 2) Sans `hmac_compute`, `item_price` peut être omis -> `receivedAmount=null` -> `L1546` saute le contrôle montant. 3) `currency` jamais vérifié. 4) `env` absent accepté (`L268`). | Envoyer un `POST /api/v1/webhooks/paytech` avec `type_event=sale_complete`, `ref_command` valide, `api_key_sha256` + `api_secret_sha256`, sans `hmac_compute`, sans `item_price`, sans `env` et avec `currency=USD` -> `200 OK`, transaction passe `BLOQUE`. | Contournement de l'intégrité financière des webhooks (confirmation sans montant ni devise vérifiés, downgrade vers empreintes statiques sans liaison au payload). | Exiger `hmac_compute` sur `sale_complete`/`sale_canceled` ; rendre `expectedAmount` obligatoire (`!= null` et `compareTo == 0`) ; exiger `currency` == `XOF` (case-insensitive) et `env` explicitement présent et égal à `test`. | **Oui** |
| **AUDIT-PT-003** | **P1** | `FlywayFreshMigrationIntegrationTest.java`, `.github/workflows/ci.yml` | `testFlywayMigrationsOnFreshDatabase()` L59–70 | Échec reproduit sur PG 16.15 : `expected: <22> but was: <23>` (job CI #3 `postgres16-flyway-integration` en `BUILD FAILURE`). | Exécuter `mvn -B -f backend/pom.xml test -Decourtier.test.pg16=true -Dtest=FlywayFreshMigrationIntegrationTest`. | Casse immédiate du pipeline CI sur toute PR / fusion vers `main` ou `develop`. | Mettre à jour `FlywayFreshMigrationIntegrationTest` vers `V23` et ajouter des assertions PostgreSQL 16 dédiées sur les nouvelles contraintes/index de `V23`. | **Oui** |
| **AUDIT-PT-004** | **P1** | `PayTechWebhookService.java`, `TransactionService.java` | `resolveTransactionId()` L366–396, `confirmWavePaymentInTransaction()` L1543–1600 | Si `ref_command` et `token` sont inconnus, `resolveTransactionId` bascule sur `custom_field` et confirme `tx` sans vérifier la concordance avec `tx.refCommandPaytech` ni `tx.tokenPaiementExterne` ; accès proxy `byRefCmd.get().getTransaction().getId()` hors transaction. | Envoyer un IPN authentifié portant `ref_command=AUTRE_CMD`, `token=AUTRE_TOK` et `custom_field={"transactionId":"<txId>"}` sur une transaction déjà initialisée avec `ECOURTIER-XXX`. | Risque de corrélation croisée / confirmation d'une transaction avec une commande ou un token PayTech étranger. | Supprimer le fallback `custom_field` quand `ref_command` est présent ; vérifier l'égalité stricte de `ref_command` et `token` avec l'opération active ; projeter `p.transaction.id` en JPQL. | **Oui** |
| **AUDIT-PT-005** | **P1** | `TransactionService.java` | `handleExternalTransferNotification()` L1856–1875, `effectuerPaiementCaution()` Phase 3 Cas A L520–554 | 1) `transfer_failed` reçu après `transfer_success` écrase `CONFIRME -> ECHOUE` (reproduit dans `PayTechEndToEndSandboxIntegrationTest:438–445`). 2) Phase 3 `PENDING` ne re-vérifie pas `statutSequestre == INITIALISE && dossierActif == true` après le retour HTTP. | 1) Envoyer `transfer_success` puis `transfer_failed` pour le même `id_transfer`. 2) Annuler ou expirer `tx` pendant l'attente HTTP de `initiatePayment` (Phase 2). | Corruption d'un état terminal `CONFIRME` par événement hors ordre ; écriture de `EN_ATTENTE_CONFIRMATION` sur une transaction déjà annulée/expirée/payée. | Interdire toute régression d'un transfert déjà `CONFIRME` ; en Phase 3 Cas A, re-contrôler `statutSequestre == INITIALISE && dossierActif == true` avant mutation. | **Oui** |
| **AUDIT-PT-006** | **P2** | `PayTechClient.java`, `TransactionDetailsModal.tsx` | `parseRequestPaymentResponse()` L217–241, `handlePaiementCaution()` L156–158 | Aucune validation d'origine/schéma sur `redirect_url` avant stockage et passage à `window.location.assign(res.redirectUrlPaiement)`. | Simuler un JSON de réponse contenant `"redirect_url": "javascript:..."` ou `"https://evil.example"` (en cas de compromission de réponse ou variable). | Risque d'open redirect / XSS DOM si `zeroDebitGaranti` devenait `true`. | Valider `https://` et hôte exact `paytech.sn` (ou chemin `/payment/checkout/`) côté backend et frontend avant toute redirection. | Non (requis avant activation PayTech) |
| **AUDIT-PT-007** | **P2** | `PayTechClient.java`, `PayTechPaymentGateway.java` | `parsePaymentStatusResponse()` L254–314, `checkPaymentStatus()` L117 | Schéma JSON `GET /payment/get-status` absent de la doc officielle PayTech ; rejet uniquement de `env="prod"` (pas `!= "test"`) ; `confirmedAmount` substitué par `expectedAmount` si absent dans la réponse. | Retourner `{"success":1,"status":"sale_complete","env":"live"}` sans `item_price` sur `MockRestServiceServer` -> accepté comme `CONFIRMED` avec `expectedAmount`. | Risque d'interprétation erronée du statut PayTech réel et de validation sans preuve de montant. | Rejeter tout `env` absent ou `!= "test"`, refuser la promotion à `CONFIRMED` si le montant retourné est absent, et valider le schéma réel sur contrat PayTech avant mise en service. | Non |
| **AUDIT-PT-008** | **P2** | `WavePaymentReconciliationService.java`, `MockPaymentGateway.java` | `scheduledReconcile()` L58, `reconcilierCandidat()` L146–176, `findPersistedPayment()` L351, `initiateEscrowRefund()` L286 | `marquerEchec` laisse `tx.statutPaiementExterne=EN_ATTENTE_CONFIRMATION` après `PT2M` ; absence de `SchedulerLockService` ; `findPersistedPayment()` accepte `EN_COURS` ; `refundByOriginalPaymentReference` volatil en mémoire. | Laisser un paiement `EN_ATTENTE_CONFIRMATION` échouer en réconciliation après 2 min -> `paiements_wave=ECHOUE` mais `transactions.statut_paiement_externe` reste `EN_ATTENTE_CONFIRMATION`. | Désynchronisation outbox/transaction, exécutions concurrentes en multi-instances, fausse confirmation potentielle d'un `EN_COURS` abandonné en mode mock. | Synchroniser `tx.statutPaiementExterne`, verrouiller `scheduledReconcile` via `SchedulerLockService`, retirer `EN_COURS` de `findPersistedPayment()` et vérifier en base l'absence de remboursement existant. | Non |
| **AUDIT-PT-009** | **P2** | `PayTechWebhookController.java`, `SecurityConfig.java`, `WaveWebhookService.java` | `recevoirIpnPayTech()` L63–74, `securityFilterChain()` L95, `traiterWebhook()` L79–85 | 1) `!request.getParameterMap().isEmpty()` court-circuite le parsing JSON si un query param est présent ; lecture flux sans limite de taille. 2) `/api/v1/webhooks/wave` reste `permitAll()` (et accepte `amount == null` si `WAVE_WEBHOOK_SECRET` est renseigné). | Envoyer `POST /api/v1/webhooks/paytech?source=pt` avec corps JSON valide -> corps JSON ignoré -> `400`/`401`. | Rejet intempestif d'IPN JSON avec query param et maintien d'une surface webhook héritée exposée. | Basculer sur `getParameterMap()` uniquement pour `application/x-www-form-urlencoded`, borner la taille du corps, et désactiver `/api/v1/webhooks/wave` hors profil test/mock. | Non |
| **AUDIT-PT-010** | **P2** | `PayTechEndToEndSandboxIntegrationTest.java`, `paytech-sandbox-ui.spec.ts` | Suite de tests | Aucun `@SpringBootTest` avec `ecourtier.payment.provider=paytech` ; aucun test du rejet `409` sur altération SHA-256 entre signatures ; aucun test Playwright de `page.reload()` en attente ni d'erreur `502`/`503`/`504` ; `phase3-real-backend.spec.ts` non rejoué. | Inspecter les tests existants via `grep`. | Angles morts de régression sur le câblage Spring PayTech, l'anti-altération SHA-256 et la résilience UI. | Ajouter ces 4 cas de test isolés et exécuter `phase3-real-backend.spec.ts` avant fusion. | Non |
| **AUDIT-PT-011** | **P3** | `PayTechPaymentProperties.java`, `ProductionConfigurationValidator.java`, `PdfBailGeneratorService.java`, `TransactionEntity.java`, `TransactionsTab.tsx`, `AdminTransactionsPage.tsx`, `index.html`, `.env.example` | Divers (voir §10, §11, §13, §14) | `refundNotifUrl` non validé ; `PAYTECH_ENV=""` traité différemment au démarrage vs exécution ; `PAYTECH_ZERO_DEBIT_GUARANTEED` absent de `.env.example` malgré le rapport 01 ; `computeCanonicalContractSha256` omet titre/quartier/ville/prix VENTE/noms ; `reference_wave VARCHAR(100)` vs `150` ; libellés résiduels « Séquestre » et « Remboursé (simulé) ». | Lecture directe des fichiers cités. | Incohérences mineures de documentation, d'empreinte contractuelle et de terminologie UI. | Harmoniser validations d'URL/env, enrichir l'empreinte canonique, aligner la longueur `reference_wave` (si nécessaire) et nettoyer les derniers libellés UI. | Non |

---

## 17. Matrice de décision pré-fusion

| Domaine | Verdict | Bloquant ? |
| :--- | :--- | :-: |
| Zéro débit réel | **NON CONFORME** (`AUDIT-PT-001` : aucun verrou backend quand `zeroDebitGuaranteed=false`) | **Oui (P0)** |
| Configuration Sandbox | **VALIDÉ SOUS RÉSERVES** (`env=test` forcé, défaut `mock`, mais divergence `PAYTECH_ENV=""` et `zero-debit-guaranteed` non connecté) | Non |
| Client PayTech | **VALIDÉ SOUS RÉSERVES** (`request-payment` conforme ; `redirect_url` non validé ; schéma `get-status` non documenté par PayTech) | Non |
| Webhook et authentification | **NON CONFORME** (`AUDIT-PT-002` contournement montant/devise/SHA-256, `AUDIT-PT-004` `custom_field`) | **Oui (P1)** |
| Idempotence | **NON CONFORME** (`AUDIT-PT-005` régression `CONFIRME -> ECHOUE` sur `transfer_failed`) | **Oui (P1)** |
| Machine à états | **NON CONFORME** (`AUDIT-PT-005` Phase 3 Cas A sans re-contrôle d'état) | **Oui (P1)** |
| Signatures et ADMIN | **VALIDÉ** (`ESCROW-SIGN-001` corrigé sur 4 couches ; réserves mineures P2/P3 sur périmètre SHA-256 et test tamper) | Non |
| Montants financiers | **VALIDÉ SOUS RÉSERVES** (`BigDecimal` serveur OK, mais vulnérable côté webhook via `AUDIT-PT-002`) | **Oui (via P1)** |
| Migration V23 | **NON CONFORME (CI)** (`V23` SQL valide sur PG16 mais casse `FlywayFreshMigrationIntegrationTest` — `AUDIT-PT-003`) | **Oui (P1)** |
| PostgreSQL 16 | **NON CONFORME (CI)** (`5/6` tests PG16 passent, `1/6` échoue sur `FlywayFreshMigrationIntegrationTest`) | **Oui (P1)** |
| Frontend | **VALIDÉ SOUS RÉSERVES** (ESCROW-FE-001 corrigé ; manque validation URL redirect `AUDIT-PT-006` et tests reload/erreur) | Non |
| Secrets | **VALIDÉ** (0 secret dans Git / logs / `dist/`, redaction vérifiée) | Non |
| CSP / CORS | **VALIDÉ** (`render.yaml` intact, `csp-security-compliance` `2/2` PASS, CORS prod strict) | Non |
| Tests backend | **NON CONFORME (CI PG16)** (`470/470` H2 + JaCoCo PASS, mais `FlywayFreshMigrationIntegrationTest` FAIL) | **Oui (P1)** |
| Tests frontend | **VALIDÉ SOUS RÉSERVES** (`15/15` mockés PASS + TSC/ESLint `0` erreur ; `phase3-real-backend` non exécuté) | Non |
| Persistance des simulations | **VALIDÉ SOUS RÉSERVES** (survit au redémarrage via `paiements_wave` ; réserve `EN_COURS` dans `MockPaymentGateway`) | Non |
| Documentation OpenAPI | **VALIDÉ** (`docs/openapi.json` == `backend/src/main/resources/static/openapi.json`) | Non |

---

## 18. Verdict final

# **C — FUSION BLOQUÉE**

Au moins une anomalie **P0** (`AUDIT-PT-001`) et quatre anomalies **P1** bloquantes (`AUDIT-PT-002`, `AUDIT-PT-003`, `AUDIT-PT-004`, `AUDIT-PT-005`) — dont un échec CI reproduit empiriquement sur PostgreSQL 16 — interdisent la fusion des modifications locales en l'état.

---

## 19. Conditions nécessaires pour une éventuelle fusion

1. **Corriger `AUDIT-PT-001` (P0) :** interdire au niveau backend tout appel sortant `POST /payment/request-payment` et toute exposition de `redirectUrlPaiement` lorsque `zeroDebitGuaranteed` n'est pas garanti, avec verrou backend explicite et tests dédiés.
2. **Corriger `AUDIT-PT-002` & `AUDIT-PT-004` (P1) :**
   - Imposer `hmac_compute` sur `sale_complete` et `sale_canceled` ;
   - Exiger `expectedAmount != null` et strictement égal à `montantTotalSequestre`, `currency == XOF` (insensible à la casse) et `env == "test"` explicitement présent ;
   - Supprimer le fallback `custom_field` lorsque `ref_command` est présent et vérifier la correspondance stricte `ref_command`/`token` avec la transaction cible.
3. **Corriger `AUDIT-PT-003` (P1) :** mettre à jour `FlywayFreshMigrationIntegrationTest` pour `V23` et valider les contraintes/index `V23` sur PostgreSQL 16, puis rejouer la commande CI complète (`6/6` verts).
4. **Corriger `AUDIT-PT-005` (P1) :** empêcher `handleExternalTransferNotification` d'écraser un statut terminal `CONFIRME`, et re-valider `statutSequestre == INITIALISE && dossierActif == true` en Phase 3 Cas A (`PENDING`) de `effectuerPaiementCaution`.
5. **Recommandé avant fusion (`P2`) :** valider l'origine `https://paytech.sn/` sur `redirect_url` (`AUDIT-PT-006`), ajouter un test de démarrage Spring avec `ecourtier.payment.provider=paytech` + test tamper SHA-256 `409` (`AUDIT-PT-010`), corriger le routage `PayTechWebhookController` (`AUDIT-PT-009`), durcir `WavePaymentReconciliationService` et `MockPaymentGateway` (`AUDIT-PT-008`), et exécuter `e2e/phase3-real-backend.spec.ts` contre le backend réel.

---

## 20. Liste des vérifications restant impossibles à ce stade

1. **Test réseau réel PayTech Sandbox (`POST /payment/request-payment`, checkout Wave, IPN entrant réel, `GET /payment/get-status`) :** impossible sans enfreindre la règle absolue zéro débit réel (la documentation officielle confirme un prélèvement de 100 à 150 FCFA en `env=test`) et en l'absence de clés Sandbox provisionnées.
2. **Validation du schéma JSON exact de `GET /payment/get-status` :** non publié sur `https://doc.paytech.sn/doc_paytech.php`, absent de `paytech_postman.json`, et Swagger `https://backend.paytech.sn/api` indisponible (`502 Bad Gateway`).
3. **Test de transfert propriétaire réel (`POST /transfer/transferFund` / `WAVE_SN_API_CASH_IN`) :** impossible sans débiter un solde marchand réel (`transferableDispo`).
4. **Migration `V23` sur une copie anonymisée des données de production Render :** non disponible dans cet environnement local isolé (testée uniquement sur base vierge PostgreSQL 16.15 via Testcontainers).
5. **Exécution de `e2e/phase3-real-backend.spec.ts` :** non lancée durant cet audit strictement lecture seule afin de ne créer aucun fichier PDF dans `backend/uploads/baux/`.
