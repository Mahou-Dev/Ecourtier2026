# RAPPORT FINAL — MISSION RENDER-PAYTECH-01 : INTÉGRATION PAYTECH / WAVE SANDBOX ET REMÉDIATION DU MODULE DE PAIEMENT E-COURTIER

**Projet :** E-Courtier Sénégal — Projet 14  
**Date :** 24 novembre 2026  
**Dépôt / Worktree :** `/home/mahou-dev/Documents/Ecourtier2026-compliance`  
**Branche :** `fix/chatbot-nlu-gemini-prod` (synchronisée avec `origin/main` au commit `60f9fc8`)  
**Statut Git :** Modifications locales non commitées, aucun push, aucune fusion, aucun déploiement Render, aucune modification de `render.yaml` ni de la base de production.

---

## Synthèse de qualification (distinction explicite exigée)

| Catégorie | Périmètre exact dans E-Courtier |
| :--- | :--- |
| **Intégration réalisée (code, contrat, persistance et tests isolés)** | Architecture unifiée `PaymentGateway` (`PayTechPaymentGateway`, `PayTechClient`, `PayTechPaymentProperties`, `PayTechWebhookService`, `PayTechWebhookController`, `MockPaymentGateway`), endpoints PayTech documentés (`POST /payment/request-payment`, `GET /payment/get-status`, `POST /api/v1/webhooks/paytech`), authentification IPN HMAC-SHA256 + secours SHA-256, migration Flyway additive `V23`, garde-fous stricts `env=test` (`PayTechPaymentProperties`, `PayTechClient`, `PayTechWebhookService`, `ProductionConfigurationValidator`), remédiations `ESCROW-SIGN-001`, `ESCROW-FE-001`, `ESCROW-ARCH-001`, `ESCROW-SIGN-002`, `ESCROW-OPS-001`, `ESCROW-BE-001`, et couverture complète des 20 scénarios obligatoires (mocks `MockRestServiceServer`, Spring Boot + PostgreSQL 16 + Playwright). |
| **Intégration préparée mais non activée par défaut** | Activation runtime `PAYMENT_PROVIDER=paytech` (`PayTechPaymentGateway`). Par défaut `ecourtier.payment.provider=mock` et `ecourtier.payment.paytech.zero-debit-guaranteed=false`. Les clés `PAYTECH_API_KEY` / `PAYTECH_API_SECRET` et les URLs publiques Render (`PAYTECH_IPN_URL`, `PAYTECH_SUCCESS_URL`, `PAYTECH_CANCEL_URL`) restent à renseigner ultérieurement sur l'hébergeur après validation explicite du blocage 100–150 FCFA. |
| **Simulation locale contrôlée (zéro débit réel)** | `MockPaymentGateway` (références préfixées `SIM-LOCAL-`, `SIM-REFUND-`, `SIM-TRANSFER-`, persistées en base `paiements_wave` pour survivre aux redémarrages), ainsi que les opérations de remboursement (`initiateRefund`) et de reversement propriétaire (`initiateOwnerTransfer`) dans `PayTechPaymentGateway` (statut explicite `TRANSFERT_SIMULE`, aucun appel réseau de décaissement réel). |
| **Test réseau Sandbox PayTech (interrompu par règle bloquante)** | **Aucun appel réseau réel exécuté vers `paytech.sn`.** La documentation officielle (`https://doc.paytech.sn/doc_paytech.php#environments`) confirme qu'en mode `env=test`, un paiement prélève réellement **100 à 150 FCFA** au client ; en outre `https://backend.paytech.sn/api` retourne `502 Bad Gateway` (NGINX/CapRover) et aucune clé Sandbox n'est provisionnée dans le dépôt. Conformément à la Section 3, les tests réseau réels ont été interrompus et remplacés par des tests isolés via `MockRestServiceServer` et simulation locale contrôlée. |
| **Fonctionnalité non disponible ou non démontrée** | 1) Séquestre bancaire réglementé (non fourni par PayTech ; E-Courtier n'est pas un établissement de séquestre réglementé). 2) Reversement automatique réel vers Wave sans débit réel (l'API PayTech Transfer `POST /transfer/transferFund` / `WAVE_SN_API_CASH_IN` ne documente aucun mode de simulation zéro débit et débite le solde marchand `transferableDispo`). 3) Stockage objet durable des PDF (S3/R2/GCS — `ESCROW-ARCH-002`, hors changement Render). 4) Signature électronique qualifiée certifiée eIDAS/PKI (les empreintes SHA-256 internes assurent l'intégrité applicative sans prétendre à une qualification légale certifiée). |

---

## 1. État initial du dépôt

- **Worktree actif inspecté :** `/home/mahou-dev/Documents/Ecourtier2026-compliance` sur la branche `fix/chatbot-nlu-gemini-prod` au commit `60f9fc8` (identique à `origin/main`). Un second worktree (`/home/mahou-dev/Documents/Ecourtier2026`) contenant d'anciennes modifications locales a été intégralement préservé sans aucune modification.
- **Note de transparence sur la source d'audit :** Le fichier nommé `Audit du projet 14.txt` n'existait pas sous ce nom exact dans le dépôt. L'audit correspondant — contenant les 11 identifiants d'anomalies exacts (`ESCROW-WAVE-001` à `ESCROW-BE-001`) et le constat des 57 tests simulés — se trouve à la racine du dépôt dans [rapport_audit_senior_paiement_sequestre_wave.md](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/rapport_audit_senior_paiement_sequestre_wave.md) et a servi de référence intégrale.
- **État initial du module de paiement avant intervention :**
  - `WaveClientMock` était la seule implémentation active de `WaveClient`, générant immédiatement des références fictives `WAVE-SANDBOX-...` et `WAVE-REFUND-SANDBOX-...` stockées dans une `ConcurrentHashMap` volatile en mémoire.
  - `TransactionService.signerBail` autorisait `ROLE_ADMIN` à signer à la place du locataire puis du propriétaire (deux appels successifs suffisaient à provoquer `SIGNE_COMPLET` et `LIBERE`).
  - `TransactionService.verifierEtDeclencherLiberation` passait `statutSequestre` à `LIBERE` et calculait `montantVerseProprietaire` sans tracer aucun ordre de transfert ni indiquer que le reversement était absent/simulé.
  - `TransactionDetailsModal.tsx` affichait un bouton « Payer via Wave » confirmant immédiatement le paiement en UI et présentant la transaction comme un « Séquestre bloqué sur compte sécurisé ».
  - Le schéma PostgreSQL était à la migration Flyway `V22`.

---

## 2. Fichiers inspectés

### Documentation officielle externe (consultation lecture seule)
- `https://doc.paytech.sn/doc_paytech.php` (extraction locale de référence : endpoints `POST /payment/request-payment`, `GET /payment/get-status`, `POST /payment/refund-payment`, `POST /transfer/transferFund`, webhooks `sale_complete`, `sale_canceled`, `refund_complete`, `transfer_success`, `transfer_failed`, calcul HMAC-SHA256 `hmac_compute`, fallback `api_key_sha256` / `api_secret_sha256`, comportement `env=test` 100–150 FCFA).
- Collection Postman officielle PayTech (`paytech_postman.json` référencée sur `doc.paytech.sn`).
- `https://backend.paytech.sn/api` (constaté en erreur HTTP `502 Bad Gateway` côté NGINX/CapRover lors de la vérification).

### Fichiers du dépôt inspectés
- **Audit :** [rapport_audit_senior_paiement_sequestre_wave.md](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/rapport_audit_senior_paiement_sequestre_wave.md)
- **Configuration & Infrastructure :** [application.yml](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/resources/application.yml), [application-test.yml](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/test/resources/application-test.yml), [.env.example](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/.env.example), [render.yaml](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/render.yaml) (lecture seule), [nginx.conf](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/frontend/nginx.conf) (lecture seule), [SecurityConfig.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/common/config/SecurityConfig.java), [ProductionConfigurationValidator.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/common/config/ProductionConfigurationValidator.java), [TransactionSecurityService.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/common/security/TransactionSecurityService.java)
- **Module Transaction Backend :** `WaveClient.java`, `WaveClientMock.java`, `TransactionController.java`, `WaveWebhookController.java`, `TransactionService.java`, `WaveWebhookService.java`, `WavePaymentReconciliationService.java`, `PdfBailGeneratorService.java`, `TransactionEntity.java`, `PaiementWaveEntity.java`, `StatutPaiementWave.java`, `TypeOperationWave.java`, `TransactionRepository.java`, `PaiementWaveRepository.java`, `TransactionResponseDTO.java`, `TransactionMapper.java`, et migrations `V1..V22`.
- **Frontend & E2E :** `TransactionDetailsModal.tsx`, `PropertyDetailPage.tsx`, `AdminTransactionsPage.tsx`, `TransactionsTab.tsx`, `dashboardModel.ts`, `api.ts`, `types/index.ts`, `csp-security-compliance.spec.ts`, `phase2-security-ui.spec.ts`, `phase9h-admin-refund.spec.ts`, `phase3-real-backend.spec.ts`, `lot2-2-c-detail.spec.ts`.

---

## 3. Anomalies du rapport d'audit prises en compte

| Code | Criticité initiale | Statut après mission | Détail de la remédiation / analyse |
| :--- | :--- | :--- | :--- |
| **ESCROW-SIGN-001** | Critique (Priorité absolue) | **Corrigée & testée** | Suppression totale du droit de signature pour `ROLE_ADMIN` dans [TransactionSecurityService.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/common/security/TransactionSecurityService.java) (`isContractParty`), [TransactionController.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/controller/TransactionController.java) (`@PreAuthorize`), [TransactionService.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionService.java) (403 Forbidden défense en profondeur) et [TransactionDetailsModal.tsx](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/frontend/src/components/transaction/TransactionDetailsModal.tsx) (aucun bouton de signature pour ADMIN + bandeau d'avertissement). |
| **ESCROW-WAVE-001** | Critique | **Intégration préparée & testée en isolé (Sandbox bloqué par 100–150 FCFA)** | Client HTTP Spring 6 `RestClient` réel implémenté dans [PayTechClient.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/gateway/PayTechClient.java) et orchestré par [PayTechPaymentGateway.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/gateway/PayTechPaymentGateway.java) (`POST /payment/request-payment`, `GET /payment/get-status`, `env=test`, `target_payment=Wave`, timeouts, validation stricte, corrélation `ref_command`/`token`). |
| **ESCROW-FIN-001** | Critique | **Remédiée en simulation locale transparente** | L'API PayTech Transfer (`POST /transfer/transferFund`, `WAVE_SN_API_CASH_IN`) ne garantit pas un mode de simulation sans débit réel. Lors de la libération logique après double signature, `initiateOwnerTransfer` exécute une **simulation locale tracée** en base (`TypeOperationWave.TRANSFERT_PROPRIETAIRE`, `statutTransfertProprietaire = TRANSFERT_SIMULE`, référence `SIM-TRANSFER-...`), jamais marquée comme un virement réel. Les IPN `transfer_success`/`transfer_failed` sont également gérés pour de futurs transferts non simulés sans jamais écraser un transfert simulé. |
| **ESCROW-FE-001** | Critique | **Corrigée & testée (Playwright)** | Suppression de toute fausse confirmation : lorsque le backend renvoie `INITIALISE` + `EN_ATTENTE_CONFIRMATION`, le frontend affiche un panneau d'attente (`paytech-pending-panel`), bloque la redirection automatique tant que `zeroDebitGaranti !== true` (avec avertissement 100–150 FCFA), verrouille la signature et propose le bouton « Vérifier le statut du paiement » (`GET /paiement/statut`). Les références fictives `WAVE-SANDBOX-...` ont été supprimées au profit de `SIM-LOCAL-...` clairement étiquetées simulation. |
| **ESCROW-ARCH-001** | Haute | **Corrigée (terminologie honnête)** | Remplacement des formulations trompeuses (« Séquestre bloqué sur compte sécurisé », « Séquestre bloqué via Wave sandbox ») par une terminologie exacte (« Paiement via PayTech (Wave) », « Paiement confirmé », « Paiement confirmé (simulation locale) », « Remboursement simulé », « Transfert simulé », « Libération logique du dossier ») + mention explicite dans la modale et le PDF qu'E-Courtier n'est pas un établissement de séquestre bancaire réglementé. |
| **ESCROW-SIGN-002** | Haute | **Remédiée de manière cohérente et honnête** | Ajout de l'aperçu PDF pré-signature (`GET /api/v1/transactions/{id}/bail/apercu-pdf`), gel de l'empreinte SHA-256 canonique des termes du contrat (`bail_document_sha256`) dès le paiement/première signature avec vérification anti-altération avant la seconde signature, calcul d'empreintes de traçabilité horodatées par partie (`signature_locataire_sha256`, `signature_proprietaire_sha256`) intégrées dans le PDF final, avec mention honnête que cela ne constitue pas une signature qualifiée eIDAS/PKI. |
| **ESCROW-OPS-001** | Moyenne | **Corrigée & testée** | `MockPaymentGateway` s'appuie désormais sur `PaiementWaveRepository` en base de données en secours de son cache mémoire : un redémarrage du backend (vidage du cache) ne fait plus perdre l'état des opérations ni les remboursements. |
| **ESCROW-BE-001** | Moyenne | **Corrigée & testée** | Nouveau webhook authentifié PayTech `POST /api/v1/webhooks/paytech` ([PayTechWebhookController.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/controller/PayTechWebhookController.java) + [PayTechWebhookService.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/service/PayTechWebhookService.java)) vérifiant `hmac_compute` (HMAC-SHA256 à temps constant) avec secours `api_key_sha256` + `api_secret_sha256`, rejetant strictement `env=prod`, les signatures invalides (401), les références inconnues (404), et prenant en charge JSON et `application/x-www-form-urlencoded`. |
| **ESCROW-SEC-001** | Moyenne | **Analysée — aucun relâchement CSP requis** | Les appels PayTech sont 100 % côté serveur (`PayTechClient`) et la redirection vers la page de checkout PayTech s'effectue par navigation de niveau supérieur (`window.location.assign`), sans script tiers, sans iframe et sans `fetch` navigateur vers `paytech.sn`. La CSP stricte existante (`render.yaml` non modifié et `nginx.conf` non modifié, vérifiés par `csp-security-compliance.spec.ts`) reste donc pleinement valide. |
| **ESCROW-DB-001** | Basse/Évolutive | **Évaluée — schéma enrichi sans rupture** | Extension additive de `paiements_wave` (multi-prestataires) et `transactions` (colonnes PayTech, transfert simulé et empreintes SHA-256) via `V23` sans renommage destructif ni perte de données existantes. L'extraction vers des tables dédiées `contrats_bail` / `signatures_contrat` est recommandée pour une mission dédiée ultérieure. |
| **ESCROW-ARCH-002** | Moyenne/Infra | **Documentée (hors périmètre Render)** | Le stockage PDF reste local sur disque (`uploads/baux`) avec régénération résiliente (`BailPdfRecoveryScheduler`) ; la migration vers un stockage objet durable (S3/R2/GCS) nécessite une configuration d'infrastructure Render interdite dans cette mission. |

---

## 4. Architecture retenue

L'architecture existante a été **adaptée sans créer de pile parallèle**, en distinguant clairement le **prestataire de paiement** (`PAYTECH`, `MOCK_LOCAL`) du **moyen de paiement** (`Wave`) :

- **`PaymentGateway`** ([PaymentGateway.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/gateway/PaymentGateway.java)) : abstraction unique (`initiatePayment`, `checkPaymentStatus`, `initiateRefund`, `verifyRefundStatus`, `initiateOwnerTransfer`, `isZeroDebitGuaranteed`, `isSimulated`).
- **`PayTechPaymentProperties`** ([PayTechPaymentProperties.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/gateway/PayTechPaymentProperties.java)) : configuration typée `@ConfigurationProperties(prefix = "ecourtier.payment.paytech")`, garde-fous `assertSandboxOnly()` (rejet de tout `env != test`) et `validateSandboxReady()`, `toString()` masquant intégralement `apiKey` et `apiSecret`.
- **`PayTechClient`** ([PayTechClient.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/gateway/PayTechClient.java)) : client HTTP Spring 6 `RestClient` dédié avec timeouts (`connect-timeout-ms`, `read-timeout-ms`), headers `API_KEY` / `API_SECRET`, requêtes `POST /payment/request-payment` (`env=test`, `target_payment=Wave`, `currency=XOF`, `ref_command` unique, `ipn_url`, `success_url`, `cancel_url`, `custom_field`) et `GET /payment/get-status?token_payment={token}`, rejet des réponses invalides (`502`) ou `env=prod` (`503`), timeouts (`504`) et journalisation assainie sans secrets.
- **`PayTechPaymentGateway`** ([PayTechPaymentGateway.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/gateway/PayTechPaymentGateway.java)) : actif uniquement si `ecourtier.payment.provider=paytech`. `initiatePayment` retourne **toujours** `ExternalPaymentState.PENDING` (jamais confirmé synchroniquement). `initiateRefund` et `initiateOwnerTransfer` exécutent une simulation locale traçable (`SIM-REFUND-...` / `SIM-TRANSFER-...`, zéro débit réel).
- **`MockPaymentGateway`** ([MockPaymentGateway.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/gateway/MockPaymentGateway.java)) : simulation locale par défaut (`ecourtier.payment.provider=mock`, `matchIfMissing=true`), émettant des références `SIM-LOCAL-...` / `SIM-REFUND-...` / `SIM-TRANSFER-...` persistées et relues en base `paiements_wave` après redémarrage. `WaveClient` (`@Deprecated`) étend `PaymentGateway` et `WaveClientMock` étend `MockPaymentGateway` pour préserver la rétrocompatibilité des tests existants sans dupliquer la logique.
- **`PayTechWebhookController` & `PayTechWebhookService`** : endpoint `POST /api/v1/webhooks/paytech` (JSON + `application/x-www-form-urlencoded`), vérification cryptographique à temps constant (`hmac_compute` puis fallback `api_key_sha256` + `api_secret_sha256`), rejet `env=prod`, traitement idempotent des 5 événements PayTech (`sale_complete`, `sale_canceled`, `refund_complete`, `transfer_success`, `transfer_failed`).
- **Robustesse préservée (Section 9) :** verrouillage pessimiste ordonné `Annonce -> Transaction`, table outbox `paiements_wave`, clés d'idempotence uniques, références externes uniques, détection et remboursement automatique des paiements tardifs (`TARDIF_A_REMBOURSER`), réconciliation (`WavePaymentReconciliationService`), montants `BigDecimal` calculés exclusivement côté serveur, et transitions d'états contrôlées.

**Décision sur la table `paiements_wave` :** plutôt qu'un renommage risqué en `paiements` qui aurait cassé des requêtes/migrations/contraintes existantes, la table `paiements_wave` a été **enrichie de façon additive** (`V23`) avec les colonnes multi-prestataires (`prestataire`, `moyen_paiement`, `environnement`, `mode_simulation`, `ref_command`, `token_externe`, `redirect_url`) et les nouveaux statuts/types (`EN_ATTENTE_CONFIRMATION`, `ANNULE`, `SIMULE`, `TRANSFERT_PROPRIETAIRE`).

---

## 5. Fichiers modifiés et créés

### Fichiers créés
- [V23__paytech_gateway_and_signature_traceability.sql](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/resources/db/migration/V23__paytech_gateway_and_signature_traceability.sql)
- [PaymentGateway.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/gateway/PaymentGateway.java)
- [PayTechPaymentProperties.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/gateway/PayTechPaymentProperties.java)
- [PayTechClient.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/gateway/PayTechClient.java)
- [PayTechPaymentGateway.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/gateway/PayTechPaymentGateway.java)
- [MockPaymentGateway.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/gateway/MockPaymentGateway.java)
- [PayTechWebhookController.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/controller/PayTechWebhookController.java)
- [PayTechWebhookService.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/service/PayTechWebhookService.java)
- [PayTechWebhookPayloadDTO.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/dto/PayTechWebhookPayloadDTO.java)
- [PayTechWebhookResponseDTO.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/dto/PayTechWebhookResponseDTO.java)
- [PayTechGatewayAndClientUnitTest.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/test/java/com/ecourtier/domain/transaction/gateway/PayTechGatewayAndClientUnitTest.java)
- [PayTechEndToEndSandboxIntegrationTest.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/test/java/com/ecourtier/domain/transaction/controller/PayTechEndToEndSandboxIntegrationTest.java)
- [paytech-sandbox-ui.spec.ts](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/frontend/e2e/paytech-sandbox-ui.spec.ts)

### Fichiers modifiés
- **Configuration & Contrats :** [.env.example](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/.env.example), [application.yml](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/resources/application.yml), [application-test.yml](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/test/resources/application-test.yml), [docs/openapi.json](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/docs/openapi.json), [backend/src/main/resources/static/openapi.json](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/resources/static/openapi.json)
- **Sécurité & Validation :** [SecurityConfig.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/common/config/SecurityConfig.java), [ProductionConfigurationValidator.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/common/config/ProductionConfigurationValidator.java), [TransactionSecurityService.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/common/security/TransactionSecurityService.java)
- **Module Transaction Backend :** [WaveClient.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/client/WaveClient.java), [WaveClientMock.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/client/WaveClientMock.java), [TransactionController.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/controller/TransactionController.java), [TransactionService.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionService.java), [WavePaymentReconciliationService.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/service/WavePaymentReconciliationService.java), [PdfBailGeneratorService.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/service/PdfBailGeneratorService.java), [TransactionEntity.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/entity/TransactionEntity.java), [PaiementWaveEntity.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/entity/PaiementWaveEntity.java), [StatutPaiementWave.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/entity/StatutPaiementWave.java), [TypeOperationWave.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/entity/TypeOperationWave.java), [TransactionRepository.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/repository/TransactionRepository.java), [PaiementWaveRepository.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/repository/PaiementWaveRepository.java), [TransactionResponseDTO.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/dto/TransactionResponseDTO.java), [TransactionMapper.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/java/com/ecourtier/domain/transaction/mapper/TransactionMapper.java)
- **Tests Backend :** [ProductionConfigurationValidatorTest.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/test/java/com/ecourtier/common/config/ProductionConfigurationValidatorTest.java), [BailSignatureLifecycleIntegrationTest.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/test/java/com/ecourtier/domain/transaction/controller/BailSignatureLifecycleIntegrationTest.java), [WorkflowTransactionIntegrationTest.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/test/java/com/ecourtier/domain/transaction/controller/WorkflowTransactionIntegrationTest.java), [TransactionServiceTest.java](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/test/java/com/ecourtier/domain/transaction/service/TransactionServiceTest.java)
- **Frontend & Tests E2E :** [index.ts](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/frontend/src/types/index.ts), [api.ts](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/frontend/src/services/api.ts), [dashboardModel.ts](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/frontend/src/lib/dashboardModel.ts), [TransactionDetailsModal.tsx](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/frontend/src/components/transaction/TransactionDetailsModal.tsx), [AdminTransactionsPage.tsx](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/frontend/src/pages/AdminTransactionsPage.tsx), [PropertyDetailPage.tsx](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/frontend/src/pages/PropertyDetailPage.tsx), [lot2-2-c-detail.spec.ts](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/frontend/e2e/lot2-2-c-detail.spec.ts), [phase2-security-ui.spec.ts](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/frontend/e2e/phase2-security-ui.spec.ts), [phase3-real-backend.spec.ts](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/frontend/e2e/phase3-real-backend.spec.ts), [phase9h-admin-refund.spec.ts](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/frontend/e2e/phase9h-admin-refund.spec.ts)

---

## 6. Migrations Flyway

- **Migration additive créée :** [V23__paytech_gateway_and_signature_traceability.sql](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/resources/db/migration/V23__paytech_gateway_and_signature_traceability.sql)
  - Enrichit `paiements_wave` (sans perte ni destruction de données) : `prestataire`, `moyen_paiement`, `environnement`, `mode_simulation`, `ref_command`, `token_externe`, `redirect_url` ; élargit les contraintes CHECK (`TRANSFERT_PROPRIETAIRE`, `EN_ATTENTE_CONFIRMATION`, `ANNULE`, `SIMULE`) et ajoute des index uniques partiels (`uq_paiements_wave_ref_command`, `uq_paiements_wave_token_externe`).
  - Enrichit `transactions` : `prestataire_paiement`, `moyen_paiement`, `mode_simulation_paiement`, `ref_command_paytech`, `token_paiement_externe`, `redirect_url_paiement`, `statut_paiement_externe`, `statut_transfert_proprietaire`, `reference_transfert_proprietaire`, `date_transfert_proprietaire`, `bail_document_sha256`, `signature_locataire_sha256`, `signature_proprietaire_sha256` + index unique partiel `uq_transactions_ref_command_paytech`.
- **Validation réelle PostgreSQL 16 :** exécutée via Testcontainers (`PostgreSQL 16.15`, `Postgres16Phase3IntegrationTest`) avec `ddl-auto: validate` — `Successfully applied 23 migrations to schema "public", now at version v23 (0.232s)`.

---

## 7. Configuration PayTech attendue (Sandbox uniquement, backend uniquement)

Variables documentées dans [.env.example](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/.env.example) et [application.yml](file:///home/mahou-dev/Documents/Ecourtier2026-compliance/backend/src/main/resources/application.yml) (aucune valeur réelle ajoutée, `render.yaml` non modifié) :

| Variable | Valeur par défaut / attendue | Statut actuel | Rôle |
| :--- | :--- | :--- | :--- |
| `PAYMENT_PROVIDER` | `mock` (par défaut) ou `paytech` | Définie (`mock`) | Sélectionne `MockPaymentGateway` ou `PayTechPaymentGateway`. |
| `PAYTECH_ENV` | **`test`** (seule valeur autorisée) | Définie (`test`) | Toute autre valeur (notamment `prod`) bloque le démarrage et les appels. |
| `PAYTECH_TARGET_PAYMENT` | `Wave` | Définie (`Wave`) | Restreint le canal de paiement au moyen Wave Sénégal. |
| `PAYTECH_BASE_URL` | `https://paytech.sn/api` | Définie | URL de base officielle documentée de l'API PayTech. |
| `PAYTECH_API_KEY` | *(secret backend à renseigner)* | **Manquante** | Clé API marchand PayTech de test (header `API_KEY`). |
| `PAYTECH_API_SECRET` | *(secret backend à renseigner)* | **Manquante** | Secret API marchand PayTech de test (header `API_SECRET` + HMAC IPN). |
| `PAYTECH_IPN_URL` | `https://<backend>/api/v1/webhooks/paytech` | **Manquante** | URL HTTPS publique de notification IPN PayTech. |
| `PAYTECH_REFUND_NOTIF_URL` | `https://<backend>/api/v1/webhooks/paytech` | Optionnelle | URL HTTPS pour `refund_complete` (retombe sur `PAYTECH_IPN_URL`). |
| `PAYTECH_SUCCESS_URL` | `https://<frontend>/dashboard?payment=success` | **Manquante** | URL HTTPS de retour succès navigateur. |
| `PAYTECH_CANCEL_URL` | `https://<frontend>/dashboard?payment=cancel` | **Manquante** | URL HTTPS de retour annulation navigateur. |
| `PAYTECH_ZERO_DEBIT_GUARANTEED` | `false` | Définie (`false`) | Bloque la redirection automatique frontend tant que le zéro débit Sandbox n'est pas garanti. |

---

## 8. Fonctionnalités réellement intégrées

- Abstraction unifiée `PaymentGateway` et sélection conditionnelle `mock` vs `paytech`.
- Client HTTP `PayTechClient` (`RestClient`, timeouts, validation JSON stricte, corrélation `ref_command` / `token`, masquage des secrets dans les logs et exceptions).
- Flux asynchrone PayTech (`INITIALISE` + `EN_ATTENTE_CONFIRMATION` -> confirmation uniquement sur IPN authentifié ou polling `GET /api/v1/transactions/{id}/paiement/statut`).
- Endpoint webhook `POST /api/v1/webhooks/paytech` authentifié par `HMAC-SHA256` (`hmac_compute`) avec secours `SHA-256`, idempotent, résistant aux rejeux, aux références inconnues et rejetant `env=prod`.
- Interdiction absolue de signature par `ADMIN` (sécurité Spring + métier + UI).
- Aperçu PDF pré-signature (`GET /api/v1/transactions/{id}/bail/apercu-pdf`) + empreinte SHA-256 canonique gelée + empreintes de signature par partie.
- Persistance en base des opérations simulées survivant aux redémarrages (`MockPaymentGateway` + `PaiementWaveRepository`).
- UI React honnête (pas de fausse confirmation, blocage redirection sans garantie zéro-débit, avertissement 100–150 FCFA, terminologie non-séquestre).
- Synchronisation OpenAPI (`docs/openapi.json` et `backend/src/main/resources/static/openapi.json` strictement identiques).

---

## 9. Fonctionnalités encore simulées

- **Paiement local par défaut (`MockPaymentGateway`)** : utilisé lorsque `PAYMENT_PROVIDER=mock`, produit des références `SIM-LOCAL-...` (zéro débit réel).
- **Remboursement (`initiateRefund`)** : même lorsque `PAYMENT_PROVIDER=paytech`, le remboursement côté backend exécute une simulation locale (`SIM-REFUND-...`, zéro débit réel) afin de respecter l'interdiction absolue de tout mouvement financier réel ; l'IPN `refund_complete` reste prêt en réception.
- **Reversement propriétaire (`initiateOwnerTransfer`)** : lors de la libération logique après double signature, aucun appel `POST /transfer/transferFund` n'est effectué ; le transfert est enregistré localement avec le statut explicite `TRANSFERT_SIMULE` et une référence `SIM-TRANSFER-...`.

---

## 10. Tests exécutés et résultats exacts

### Synthèse des suites exécutées

| Suite / Commande | Résultat exact | Détails |
| :--- | :--- | :--- |
| **Backend complet** (`mvn test`) | **470 exécutés, 0 échec, 0 erreur, 6 ignorés** | Les 6 tests ignorés sont les tests Docker/PostgreSQL 16 conditionnels (`FlywayFreshMigrationIntegrationTest` : 1, `Postgres16Phase3IntegrationTest` : 4, `Postgres16IaConcurrencyIntegrationTest` : 1). |
| **PostgreSQL 16 réel + Flyway V1..V23** (`mvn test -Dtest=Postgres16Phase3IntegrationTest -Decourtier.test.pg16=true`) | **4 exécutés, 0 échec, 0 erreur** | PostgreSQL `16.15` Testcontainers, `23` migrations Flyway appliquées (`v23`), `ddl-auto: validate`, unicité partielle et reprise PDF validées. |
| **Module Paiement / PayTech / Validateur** (`mvn test -Dtest="*Transaction*,*Wave*,*Bail*,*PayTech*,ProductionConfigurationValidatorTest,AnnonceStatutSecurityIntegrationTest"`) | **91 exécutés, 0 échec, 0 erreur** | Inclut les 57 tests transactionnels mis à jour + 12 nouveaux tests PayTech + 2 nouveaux tests de démarrage Sandbox. |
| **Parité OpenAPI** (`OpenApiContractIntegrationTest` + `cmp -s`) | **1 exécuté, 0 échec (`OPENAPI_SYNC_OK`)** | `docs/openapi.json` == `backend/src/main/resources/static/openapi.json`. |
| **Frontend TypeScript, ESLint & Build** (`npx tsc --noEmit && npm run lint && npm run build`) | **Succès — 0 erreur, 0 warning** | `--max-warnings 0`, bundle Vite PWA généré (`dist/`). |
| **Frontend Playwright E2E** (5 fichiers : `paytech-sandbox-ui`, `phase2-security-ui`, `phase9h-admin-refund`, `csp-security-compliance`, `lot2-2-c-detail`) | **15 exécutés, 15 réussis, 0 échec** | Exécutés contre le bundle `dist/` via `vite preview` (arrêté automatiquement en fin de test). |

### Matrice de couverture des 20 tests obligatoires (Section 10)

| # | Scénario obligatoire | Test(s) exact(s) | Statut |
| :-: | :--- | :--- | :--- |
| 1 | Initialisation PayTech en Sandbox | `PayTechGatewayAndClientUnitTest.testInitialisationPayTechSandboxRetournePending` + `PayTechEndToEndSandboxIntegrationTest.testFluxPayTechPendingPuisIpnSaleCompleteEtRejeuIdempotent` | **PASS** (mock HTTP) |
| 2 | Refus de toute configuration `prod` | `PayTechGatewayAndClientUnitTest.testRefusConfigurationProd` + `ProductionConfigurationValidatorTest.testPayTechProdEnvBlockedAtStartup` + `PayTechGatewayAndClientUnitTest.testGetPaymentStatusPendingConfirmedEtRejetProd` | **PASS** |
| 3 | Refus si paramètres Sandbox absents | `PayTechGatewayAndClientUnitTest.testRefusSiParametresSandboxAbsents` | **PASS** |
| 4 | Réponse PayTech invalide | `PayTechGatewayAndClientUnitTest.testReponsePayTechInvalide` | **PASS** |
| 5 | Timeout réseau | `PayTechGatewayAndClientUnitTest.testTimeoutReseauPayTech` | **PASS** |
| 6 | Paiement en attente | `PayTechGatewayAndClientUnitTest.testGetPaymentStatusPendingConfirmedEtRejetProd` + `PayTechEndToEndSandboxIntegrationTest.testFluxPayTechPendingPuisIpnSaleCompleteEtRejeuIdempotent` | **PASS** |
| 7 | Paiement confirmé | `PayTechEndToEndSandboxIntegrationTest.testFluxPayTechPendingPuisIpnSaleCompleteEtRejeuIdempotent` | **PASS** |
| 8 | Paiement annulé | `PayTechEndToEndSandboxIntegrationTest.testIpnSaleCanceledFrauduleuxEtReferenceInconnue` | **PASS** |
| 9 | Notification frauduleuse | `PayTechEndToEndSandboxIntegrationTest.testIpnSaleCanceledFrauduleuxEtReferenceInconnue` (HMAC falsifié, SHA-256 invalide, `env=prod`) | **PASS** |
| 10 | Notification répétée | `PayTechEndToEndSandboxIntegrationTest.testFluxPayTechPendingPuisIpnSaleCompleteEtRejeuIdempotent` | **PASS** |
| 11 | Référence inconnue | `PayTechEndToEndSandboxIntegrationTest.testIpnSaleCanceledFrauduleuxEtReferenceInconnue` (`404`) | **PASS** |
| 12 | Remboursement simulé | `PayTechGatewayAndClientUnitTest.testRemboursementEtTransfertSimulesSansAppelReseau` + `PayTechEndToEndSandboxIntegrationTest.testPaiementTardifRefundEtTransferIpn` | **PASS** |
| 13 | Transfert simulé | `PayTechGatewayAndClientUnitTest.testRemboursementEtTransfertSimulesSansAppelReseau` + `PayTechEndToEndSandboxIntegrationTest.testDoubleSignatureAuthentifieeSha256AdminInterditEtTransfertSimule` | **PASS** |
| 14 | Paiement tardif | `PayTechEndToEndSandboxIntegrationTest.testPaiementTardifRefundEtTransferIpn` | **PASS** |
| 15 | Redémarrage backend et persistance | `PayTechEndToEndSandboxIntegrationTest.testRedemarrageBackendPersistanceOperations` | **PASS** |
| 16 | ADMIN interdit de signer | `BailSignatureLifecycleIntegrationTest` (tests 1, 4, 5) + `PayTechEndToEndSandboxIntegrationTest.testDoubleSignatureAuthentifieeSha256AdminInterditEtTransfertSimule` + `paytech-sandbox-ui.spec.ts` (test 2) | **PASS** |
| 17 | Double signature authentifiée | `BailSignatureLifecycleIntegrationTest` (tests 2, 3, 6, 12) + `PayTechEndToEndSandboxIntegrationTest.testDoubleSignatureAuthentifieeSha256AdminInterditEtTransfertSimule` | **PASS** |
| 18 | Absence de libération avant double signature | `PayTechEndToEndSandboxIntegrationTest.testDoubleSignatureAuthentifieeSha256AdminInterditEtTransfertSimule` | **PASS** |
| 19 | Absence de fausse confirmation frontend | `frontend/e2e/paytech-sandbox-ui.spec.ts` (tests 1 & 2) | **PASS** |
| 20 | Absence de fuite de secrets dans les logs | `PayTechGatewayAndClientUnitTest` (`ListAppender<ILoggingEvent>` sur `PayTechClient` + `PayTechPaymentGateway` vérifiant l'absence totale d'`apiKey` et `apiSecret` sur tous les tests, y compris timeout et erreurs) | **PASS** |

---

## 11. Vérification de l'absence d'appels de production

- **Garde-fous multi-couches :**
  1. `ProductionConfigurationValidator.validatePayTechSandboxConfiguration()` refuse le démarrage (`IllegalStateException`) pour tout `PAYTECH_ENV != test` lorsque `PAYMENT_PROVIDER=paytech` (y compris en profil `dev`/`test`).
  2. `PayTechPaymentProperties.assertSandboxOnly()` refuse tout `env != test` avant chaque appel HTTP.
  3. `PayTechClient` rejette également toute réponse PayTech indiquant `env=prod`.
  4. `PayTechWebhookService` rejette avec `403 Forbidden` tout IPN portant `env=prod`.
- **Audit statique :** `grep -rn 'env=prod\|"prod"' backend/src/main/java/com/ecourtier/domain/transaction/ frontend/src/` confirme que la chaîne `"prod"` n'apparaît **uniquement** dans les garde-fous de rejet et commentaires.
- **Audit Render :** `git diff render.yaml` est strictement vide. Aucune requête réseau vers PayTech n'a été envoyée durant les tests.

---

## 12. Vérification de l'absence de secrets exposés

- **Scan de secrets Git (`git grep`) :** aucune clé API réelle (`PAYTECH_API_KEY`, `PAYTECH_API_SECRET`, `AIzaSy...`, `gsk_...`, clé privée) n'est présente dans les sources, configurations ou tests.
- **Isolation Frontend :** `grep -rn "PAYTECH_API_KEY\|PAYTECH_API_SECRET\|API_SECRET" frontend/src/` retourne **0 occurrence**.
- **Protection des logs :** `PayTechPaymentProperties.toString()` masque intégralement `apiKey` et `apiSecret` ; `PayTechClient.sanitizeErrorMessage(...)` redacte toute occurrence accidentelle de clé/secret dans les messages d'exception ; vérifié automatiquement par `ListAppender` dans `PayTechGatewayAndClientUnitTest`.

---

## 13. Blocages liés aux identifiants ou capacités Sandbox

1. **Débit réel en Sandbox PayTech (100 à 150 FCFA) :** La documentation officielle PayTech (`#environments`) précise qu'en mode `env=test`, un paiement prélève aléatoirement 100 à 150 FCFA réels au payeur. Cette condition bloquante (Section 3) empêche l'exécution de tout test réseau de paiement Sandbox tant qu'un parcours garanti zéro débit n'est pas confirmé ou explicitement autorisé.
2. **Absence de simulation zéro débit sur l'API PayTech Transfer :** L'API de transfert `POST /transfer/transferFund` (`WAVE_SN_API_CASH_IN`) débite le solde marchand `transferableDispo` et ne documente aucun environnement de simulation sans mouvement financier.
3. **Indisponibilité du Swagger PayTech (`https://backend.paytech.sn/api`) :** Retourne une erreur `502 Bad Gateway` côté infrastructure PayTech ; la spécification a donc été vérifiée à partir de `https://doc.paytech.sn/doc_paytech.php` et de la collection Postman officielle.
4. **Identifiants et URLs publiques non provisionnés :** `PAYTECH_API_KEY`, `PAYTECH_API_SECRET`, `PAYTECH_IPN_URL`, `PAYTECH_SUCCESS_URL` et `PAYTECH_CANCEL_URL` ne sont pas encore configurés dans l'environnement cible (et la modification de Render est interdite dans cette mission).

---

## 14. Anomalies restantes

- **`ESCROW-ARCH-002` (stockage PDF sur disque éphémère Render) :** Non résolue dans cette mission car elle requiert un bucket externe (S3 / Cloudflare R2 / GCS) et de nouvelles variables Render ; le mécanisme de régénération automatique (`BailPdfRecoveryScheduler`) et l'aperçu PDF en mémoire atténuent l'impact sans remplacer un stockage durable.
- **`ESCROW-DB-001` (tables dédiées aux contrats et signatures) :** La traçabilité SHA-256 et les colonnes PayTech ont été ajoutées de manière additive dans `transactions` et `paiements_wave` (`V23`) pour zéro régression ; une normalisation complète dans des tables dédiées `contrats_bail` et `signatures_contrat` reste une amélioration structurelle souhaitable.
- **Reversement financier réel vers les propriétaires (`ESCROW-FIN-001`) :** Reste en simulation locale (`TRANSFERT_SIMULE`) tant qu'une API de décaissement ou un établissement partenaire agréé ne permet pas un workflow de paiement/reversement certifié.
- **Qualification réglementaire séquestre (`ESCROW-ARCH-001`) :** L'interface et les PDF sont désormais honnêtes, mais un véritable séquestre réglementé exigerait un partenariat bancaire / établissement de monnaie électronique agréé BCEAO.

---

## 15. Recommandations pour la mission suivante

1. **Revue, commit et fusion contrôlée** des modifications actuelles après validation explicite de l'équipe (OpenAPI synchronisé, 470 tests backend + 4 tests PG16 + 15 tests Playwright verts).
2. **Décision produit & conformité sur le Sandbox PayTech :** confirmer avec un compte marchand de test dédié si un test contrôlé de bout en bout (IPN sur URL HTTPS Render staging) est autorisé malgré la mécanique documentée de 100–150 FCFA, ou conserver `PAYMENT_PROVIDER=mock` pour les environnements de démonstration.
3. **Configuration Render dédiée (mission infra séparée) :** injecter les secrets `PAYTECH_API_KEY`, `PAYTECH_API_SECRET` et les URLs publiques HTTPS (`PAYTECH_IPN_URL`, `PAYTECH_SUCCESS_URL`, `PAYTECH_CANCEL_URL`) uniquement via le panneau Render (jamais dans Git), en maintenant `PAYTECH_ENV=test`.
4. **Remédiation `ESCROW-ARCH-002` :** migrer la persistance des PDF signés (`PdfBailGeneratorService`) vers un stockage objet durable (Cloudflare R2 ou AWS S3) avec URL signée temporaire et archivage de l'empreinte SHA-256 du fichier PDF généré.
5. **Évolution `ESCROW-DB-001` & reversement réel :** planifier la création des tables dédiées `contrats_bail` / `signatures_contrat` et étudier l'intégration d'un partenaire financier agréé si un véritable séquestre bancaire réglementé est requis en production.
