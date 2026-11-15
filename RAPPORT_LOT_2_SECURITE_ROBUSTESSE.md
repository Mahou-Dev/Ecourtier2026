# RAPPORT DE RÉALISATION ET DE CONTRE-AUDIT — LOT 2 : SÉCURITÉ ET ROBUSTESSE

**Projet :** E-Courtier Sénégal (Licence 3 Ingénierie Informatique)  
**Date :** 15 novembre 2026  
**Périmètre :** Authentification & JWT, Révocation multi-canaux, Rôles & RBAC, WebSocket/STOMP, Validation & Upload, Rate Limiting, Configuration Production, Secrets & Docker, Suppression logique, Concurrence & `@Version`, Schedulers distribués, Génération PDF, Gestion d'erreurs & OpenAPI 3.0.1.  
**Exclusion stricte :** Domaine Wave intégralement préservé hors périmètre (0 fichier modifié).  

---

## 1. Résumé exécutif et Verdict

**VERDICT FINAL LOT 2 : `VALIDÉ`**

Toutes les anomalies P0/P1 identifiées dans le périmètre sécurité et robustesse ont été corrigées, testées sur base H2 **et** sur **PostgreSQL 16.15 (Testcontainers isolé avec migrations Flyway V1 à V20)**, contre-auditées de manière hostile (déclenchant trois ajustements critiques durant le contre-audit lui-même : compatibilité dialecte SQL H2/PostgreSQL sur `SchedulerLockService`, `initialDelayString: 15000` sur les schedulers pour éviter un verrouillage prématuré au démarrage, et enregistrement du schéma `ApiErrorResponse` dans `OpenApiCustomizer` sans avertissement Springdoc), puis re-validées après la dernière édition.

---

## 2. Matrice d'audit sécurité et robustesse (CA3-005 à CA3-013 & domaines transverses)

| Domaine / ID | Risque / Anomalie diagnostiquée (Étape A) | Correction apportée (Étapes B & F) | Fichiers impactés | Statut |
|---|---|---|---|---|
| **CA3-005 — Rate Limiting IA** | `IaRateLimiterService` rejetait globalement (HTTP 429) tous les nouveaux clients lorsque `rateLimitMap` atteignait `5 000` clés (`size() >= MAX_MAP_SIZE`), créant un vecteur de déni de service global. | Migration vers `BoundedRateLimiterSupport` (capacité bornée avec éviction LRU/FIFO des clés expirées ou plus anciennes sans blocage global). | `BoundedRateLimiterSupport.java`, `IaRateLimiterService.java` | **Corrigé & Testé** |
| **CA3-005 — Rate Limiting Login** | `LoginRateLimiterService` limitait uniquement par couple `ip:email`, permettant du password-spraying distribué sur des milliers d'emails depuis une seule IP. | Ajout d'un plafond agrégé par IP (`MAX_ATTEMPTS_PER_IP = 25` dans `ipAttemptsMap`) en parallèle du suivi `ip:email`. | `LoginRateLimiterService.java` | **Corrigé & Testé** |
| **CA3-006 — En-têtes proxy en prod** | `application-prod.yml` n'encadrait pas `X-Forwarded-For` / `X-Forwarded-Proto` via Tomcat `remoteip`. | Ajout de `server.tomcat.remoteip` (`remote-ip-header: x-forwarded-for`, `protocol-header: x-forwarded-proto`, `internal-proxies: ${ECOURTIER_TRUSTED_PROXIES:...}`). | `application-prod.yml` | **Corrigé** |
| **CA3-007 — Validateur Production** | `ProductionConfigurationValidator` n'incluait pas le secret hex 64 caractères d'exemple ni `postgres`/`ecourtier`/`admin`/`root`, ignorait les URL `postgres://`/`postgresql://`, et ne nettoyait pas les crochets IPv6 `[::1]` dans la validation CORS. | Enrichissement de `KNOWN_INSECURE_SECRETS`, prise en charge des préfixes `postgres://`/`postgresql://`, nettoyage des crochets IPv6 pour bloquer `[::1]` et `0:0:0:0:0:0:0:1`. | `ProductionConfigurationValidator.java` | **Corrigé & Testé** |
| **CA3-008 — Suppression logique (`supprime`)** | Après `DELETE /api/v1/annonces/{id}`, `MessagerieService` acceptait encore l'ouverture de conversation, `FavoriRepository` exposait toujours les annonces archivées dans la liste/compteur, et les RDV `DEMANDE`/`CONFIRME` restaient actifs et confirmables. | `MessagerieService` passe à `findByIdAndSupprimeFalse` ; `FavoriRepository` filtre `AND f.annonce.supprime = false` ; `AnnonceService.supprimerAnnonceLogique` passe les RDV `DEMANDE`/`CONFIRME` à `ANNULE` ; `RendezVousService.confirmerRendezVous` retourne HTTP `409 Conflict` si `annonce.isSupprime()`. | `MessagerieService.java`, `FavoriRepository.java`, `RendezVousRepository.java`, `RendezVousService.java`, `AnnonceService.java` | **Corrigé & Testé** |
| **CA3-009 — WebSocket / STOMP reconnexion** | `useConversationSocket.ts` utilisait un token statique capturé au montage et tentait des reconnexions infinies malgré un rejet `1008` / session révoquée. | Résolution dynamique du token dans `beforeConnect`, tentative via `tryRefreshAccessToken()`, arrêt propre du client STOMP et appel `logout()` sur code `1008` ou fermetures répétées. | `frontend/src/hooks/useConversationSocket.ts` | **Corrigé & Buildé** |
| **CA3-010 — Schedulers distribués** | `SchedulerLockService` pouvait lever une erreur SQL `23505` lors d'insertions concurrentes et les 3 schedulers passaient `Duration.ZERO` pour `lockAtLeastFor` + démarraient à `t=0` (course avec seeding/tests). | `tryExecuteWithLock(..., Duration lockAtLeastFor, Runnable)` ; détection dialecte SQL (`ON CONFLICT (name) DO NOTHING` sur PostgreSQL 16, `INSERT` + capture `DataIntegrityViolationException` sur H2) ; `LOCK_AT_LEAST_FOR = 10s` et `initialDelayString = 15000ms` sur `TransactionExpirationScheduler` et `BailPdfRecoveryScheduler` (+ `10s` sur `RendezVousService.simulerRappelsDemain`). | `SchedulerLockService.java`, `TransactionExpirationScheduler.java`, `BailPdfRecoveryScheduler.java`, `RendezVousService.java` | **Corrigé & Testé (H2 + PG16)** |
| **CA3-011 — Migrations Flyway V1..V20 PG16** | `FlywayFreshMigrationIntegrationTest` ciblait la base live `ecourtier-db:5432` en `V19` au lieu d'un conteneur PG16 isolé jusqu'à `V20`. | Réécriture sur `@Testcontainers` `postgres:16-alpine` conditionnée par `-Decourtier.test.pg16=true`, appliquant V1 à V20 à neuf. | `FlywayFreshMigrationIntegrationTest.java` | **Corrigé & Testé (PG16)** |
| **CA3-012 — Contrat OpenAPI & erreurs** | Les réponses `4xx`/`5xx` manquaient de schéma d'erreur typé et unifié dans `/v3/api-docs` et `openapi.json`. | Ajout du schéma `ApiErrorResponse` (`timestamp`, `status`, `error`, `erreurs`) et d'un `OpenApiCustomizer` attachant `$ref: #/components/schemas/ApiErrorResponse` aux réponses `4xx`/`5xx` sans contenu JSON ; export + resynchronisation SHA-256. | `OpenApiConfig.java`, `docs/openapi.json`, `backend/src/main/resources/static/openapi.json`, `OpenApiContractIntegrationTest.java` | **Corrigé & Synchronisé** |
| **CA3-013 — Révocation sur changement de rôle** | `UtilisateurManagementService.changerRole` modifiait le rôle en base sans invalider les Access Tokens, Refresh Tokens ni sessions WebSocket actifs de l'utilisateur. | Incrémentation de `tokenVersion`, `revokeAllActiveByUtilisateurId(..., "ROLE_CHANGED")` et `disconnectUserAfterCommit(...)` si `previousRole != nouveauRole`. | `AuthService.java`, `UtilisateurManagementService.java` | **Corrigé & Testé** |
| **Upload & Stockage** | Vérification de confidentialité et traversée de répertoires sur les fichiers uploadés. | `SecurityConfig` n'expose en public que `/api/v1/uploads/annonces/**` ; `/api/v1/annonces/*/documents/*/fichier` et `/api/v1/transactions/**` exigent authentification + contrôle d'accès ; `UploadController` applique `normalize()`/`toRealPath()` ; `FileStorageService` valide extensions, magic-bytes et limites (`5 Mo` photo / `10 Mo` document). | `SecurityConfig.java`, `UploadController.java`, `FileStorageService.java` | **Audité conforme** |
| **Horloge & Concurrence** | Cohérence temporelle `Africa/Dakar` et verrouillage optimiste `@Version`. | `JwtService` injecte `Clock` dans `nowDate()` et `Jwts.parser().clock(...)` ; `@Version` vérifié sur `AnnonceEntity` et `AnnonceOptimisticLockIntegrationTest`. | `JwtService.java`, `AnnonceService.java` | **Audité conforme** |
| **Secrets & Contextes Docker** | Aucun `.dockerignore` dans `backend/` ni `frontend/` (`frontend/Dockerfile` exécute `COPY . .`) ; risque d'inclusion de `.env`/artefacts dans le contexte Docker. | Création de `backend/.dockerignore` et `frontend/.dockerignore` excluant `.env`, `.env.*` (sauf `.env.example`), `target/`, `uploads/`, `node_modules/`, `dist/`, `test-results/`. Vérification `git ls-files` : aucun fichier secret suivi hors `.env.example`. | `backend/.dockerignore`, `frontend/.dockerignore` | **Corrigé & Vérifié** |

*Note DevOps (remise au Lot 4) :* L'audit des fichiers `docker-compose.yml` (valeurs par défaut dev de `POSTGRES_PASSWORD`/`ADMIN_BOOTSTRAP_PASSWORD`), `frontend/Dockerfile` (image dev `npm run dev` à compléter pour la production Nginx multi-stage) et `render.yaml` (alignement `GROQ_API_KEY`) est acté pour traitement complet dans le **Lot 4 — CI/CD et DevOps**.

---

## 3. Contre-audit indépendant (Posture hostile — Étapes E & F)

Lors du contre-audit après les premières corrections du Lot 2, quatre défauts réels ont été découverts et corrigés avant la validation finale :

1. **Incompatibilité dialecte SQL sur H2 (`SchedulerLockService`)** : la syntaxe PostgreSQL `ON CONFLICT (name) DO NOTHING` a provoqué une `BadSqlGrammarException` sur H2 2.3.232 dans `SchedulerLockServiceIntegrationTest`. Résolu par détection paresseuse du dialecte JDBC (`isPostgresDialect()`) conservant `ON CONFLICT (name) DO NOTHING` sur PostgreSQL 16 et utilisant `INSERT` standard + capture `DataIntegrityViolationException` sur H2.
2. **Contention au démarrage des schedulers sur PostgreSQL 16** : l'ajout de `LOCK_AT_LEAST_FOR = 10s` sans `initialDelayString` provoquait une exécution immédiate à `t=0` en profil `dev`, verrouillant `transaction-bail-pdf-recovery` pendant 10 secondes et faisant échouer `Postgres16Phase3IntegrationTest.testTransactionPartialIndexAndPdfRecoveryOnPostgres16` (`ECHEC` au lieu de `GENERE`). Corrigé par ajout de `initialDelayString` (`15000 ms` par défaut, surchargeable par propriété) sur `BailPdfRecoveryScheduler` et `TransactionExpirationScheduler`.
3. **Avertissement Jackson Springdoc et omission du schéma `ApiErrorResponse`** : enregistrer `MapSchema.additionalProperties(new StringSchema())` sur `customOpenAPI()` provoquait un clonage Springdoc erroné avec suppression du composant. Corrigé en enregistrant `ApiErrorResponse` directement dans `apiErrorResponseCustomizer()` (`0` avertissement au démarrage Springdoc).
4. **Absence de `.dockerignore`** : `backend/.env` et répertoires de build locaux n'étaient pas filtrés du contexte Docker ; corrigé via `backend/.dockerignore` et `frontend/.dockerignore`.

---

## 4. Preuves de tests et vérifications (après dernières modifications)

### 4.1 Tests unitaires et intégration H2 (Maven Surefire)
1. **Suite ciblée Lot 2** (`mvn -f backend/pom.xml test -Dtest=SchedulerLockServiceIntegrationTest,AnnonceSuppressionSecurityIntegrationTest,AuthAccessTokenRevocationIntegrationTest,WebSocketSessionSecurityIntegrationTest,ProductionConfigurationValidatorTest,IaRateLimiterTest,LoginRateLimiterServiceTest,RefreshRateLimiterServiceTest`) :
   - `ProductionConfigurationValidatorTest` : **10/10** (0 échec, 0 erreur, 0 ignoré)
   - `SchedulerLockServiceIntegrationTest` : **3/3** (0 échec, 0 erreur, 0 ignoré)
   - `LoginRateLimiterServiceTest` : **3/3** (0 échec, 0 erreur, 0 ignoré)
   - `RefreshRateLimiterServiceTest` : **4/4** (0 échec, 0 erreur, 0 ignoré)
   - `WebSocketSessionSecurityIntegrationTest` : **4/4** (0 échec, 0 erreur, 0 ignoré)
   - `AnnonceSuppressionSecurityIntegrationTest` : **13/13** (0 échec, 0 erreur, 0 ignoré)
   - `IaRateLimiterTest` : **4/4** (0 échec, 0 erreur, 0 ignoré)
   - `AuthAccessTokenRevocationIntegrationTest` : **6/6** (0 échec, 0 erreur, 0 ignoré)
   - **Total suite sécurité Lot 2 : 47 tests exécutés, 0 échec, 0 erreur, 0 ignoré.**
2. **Ré-exécution après ajout d'`initialDelayString` sur les schedulers** (`mvn -f backend/pom.xml test -Dtest=SchedulerLockServiceIntegrationTest,BailPdfReliabilityIntegrationTest,PdfBailGeneratorServiceTest`) :
   - `SchedulerLockServiceIntegrationTest` : **3/3**
   - `BailPdfReliabilityIntegrationTest` : **3/3**
   - `PdfBailGeneratorServiceTest` : **2/2**
   - **Total : 8 tests exécutés, 0 échec, 0 erreur, 0 ignoré.**

### 4.2 Suite PostgreSQL 16 réelle & Flyway V1..V20 (Testcontainers `postgres:16-alpine`, API Docker `1.44`)
Commande exécutée après la dernière modification scheduler (`-Decourtier.test.pg16=true -Dapi.version=1.44`) :
- `FlywayFreshMigrationIntegrationTest` : **1/1** (migrations V1 à V20 appliquées à froid en `0.633s` sur PostgreSQL 16.15).
- `Postgres16Phase3IntegrationTest` : **4/4** (contrainte partielle `uq_transactions_annonce_active`, contrainte CHECK `chk_transactions_statut_document_bail`, reprise PDF `BailPdfRecoveryScheduler`, verrou distribué `scheduler_locks`).
- **Total PG16 : 5 tests exécutés, 0 échec, 0 erreur, 0 ignoré.**

### 4.3 Contrat OpenAPI 3.0.1
- Export et copie vers `backend/src/main/resources/static/openapi.json`.
- Empreinte SHA-256 identique vérifiée sur `docs/openapi.json` et `backend/src/main/resources/static/openapi.json` :  
  `0ed6e2cf7d6d53c0ab3384a7097b65d41b1b676a05414ee6fb94a6eeb50bc242`
- `OpenApiContractIntegrationTest` : **1/1** (0 échec, 0 erreur, 0 ignoré, 0 avertissement `OpenAPIService`).

### 4.4 Frontend (`lint`, `tsc`, `build`)
- `npm --prefix frontend run lint` (`eslint . --max-warnings 0`) : **PASS** (0 avertissement).
- `./frontend/node_modules/.bin/tsc -p frontend/tsconfig.json --noEmit` : **PASS** (0 erreur TypeScript).
- `npm --prefix frontend run build` : **PASS** (`✓ built in 25.42s`, PWA v1.3.0, `61` entrées précachées).

### 4.5 Contrôle d'intégrité et d'exclusion Wave
- Vérification `git status --short` et `git ls-files` : **0** fichier Wave modifié ou créé ; **0** fichier `.env` suivi par Git (uniquement `.env.example` et `frontend/.env.example`).
