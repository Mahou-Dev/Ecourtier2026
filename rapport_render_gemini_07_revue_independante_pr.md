# RAPPORT DE MISSION RENDER-GEMINI-07 — REVUE INDÉPENDANTE, AUDIT DU DIFF ET PRÉPARATION DE LA PULL REQUEST

**Projet :** E-Courtier Sénégal — Projet 14 (Licence 3 Ingénierie Informatique)  
**Date d'audit :** 22 novembre 2026  
**Worktree audité :** `/home/mahou-dev/Documents/Ecourtier2026-compliance`  
**Branche auditée :** `fix/render-gemini-production-compliance`  
**Commit audité (`HEAD`) :** `b5123db1c8fb57fd62b9b6be1527422d97c3d33b` (`mahou@Dev`, 22 nov. 2026 11:56:41 +0000)  
**Branche cible :** `main` (`origin/main` = `258a5eb703c7610651e8513d5de7b28c1b30da8a`)  
**Pull Request GitHub :** [`#4 — fix: restore anonymous IA sessions and improve Gemini production compliance`](https://github.com/Mahou-Dev/Ecourtier2026/pull/4) (**Statut : `Open`**, non fusionnée)

---

## 1. Résumé exécutif

La présente revue indépendante a audité intégralement le commit `b5123db1c8fb57fd62b9b6be1527422d97c3d33b` sur la branche `fix/render-gemini-production-compliance` par rapport à `origin/main` (`37 fichiers modifiés : +3402 / -427`), sans considérer `rapport_render_gemini_06_remise_en_conformite.md` comme preuve.

### Verdict global
- **Qualité du diff et cohérence technique :** Le correctif répond directement aux causes racines architecturales identifiées lors de l'audit `RENDER-GEMINI-04` (session anonyme bloquée en cross-origin par `SameSite` -> remplacée par jeton cryptographique `X-IA-Session-Token` stocké en SHA-256 côté serveur ; migration Flyway `V21` pour un ordre chronologique déterministe des messages ; alignement NLU et FAQ juridique ; télémétrie et transparence UI du repli local ; validation stricte de configuration Render pour Google Gemini).
- **Non-régression et tests indépendants vérifiés :**
  - Backend Maven hors périmètre exclu `domain.transaction` : **`365` tests exécutés, `0` échec, `0` erreur, `5` ignorés** (`BUILD SUCCESS`).
  - Migration PostgreSQL 16 (Testcontainers `postgres:16-alpine`) : `FlywayFreshMigrationIntegrationTest` validé (**`1/1 PASS`**, versions `V1` à `V21` appliquées en `0.264s`). `Postgres16Phase3IntegrationTest` (`4` tests) est volontairement exclu/non exécuté car il importe `com.ecourtier.domain.transaction.*`.
  - Frontend React/TypeScript : `eslint . --max-warnings 0` OK, `tsc && vite build` OK (`8.18s`, `63` entrées PWA), scan secrets sur `dist/` propre, et Playwright isolé sur `vite preview` (`127.0.0.1:3100`) : **`19/19 PASS`**.
  - Vérification runtime cross-origin éphémère (`Spring Boot H2 dev,test :18080` + bundle isolé `vite preview :4173` avec `VITE_API_BASE_URL=http://127.0.0.1:18080`) : preflight `OPTIONS` `HTTP 200`, `POST /api/v1/ia/conversations` `HTTP 201` sans `Set-Cookie`, rejet `HTTP 403` sans jeton ou avec jeton invalide, validation `HTTP 400` à `1001` caractères, rate limiter anonyme `HTTP 429` vérifié au-delà de `10 req/min`, extraction NLU/FAQ/ordre `1..8`, et scénario réel Chromium avec rechargement `F5` (`HTTP 200`, `0` erreur console).
- **Point critique de sincérité (Mocks vs Production) :** Aucun appel réseau réel vers l'API Google Gemini avec une clé live n'a été effectué ni dans les tests locaux (qui reposent sur un serveur HTTP local mocké ou sur `RuleBasedCriteriaExtractor` avec `CLE_ABSENTE`), ni contre l'environnement Render réel (aucun déploiement ni écriture en base de production n'étant autorisés). Par conséquent, **`ANO-PROD-01` (dans sa validation prod cross-origin), `ANO-PROD-02`, `ANO-PROD-10` (latence prod/Gemini)** sont classés **`CORRIGÉ MAIS NON VALIDÉ`** jusqu'à vérification post-déploiement sur Render, et **`ANO-PROD-06` (catalogue vérifié en base de production)** est classé **`BLOQUÉ PAR UNE DÉPENDANCE EXTERNE`**.
- **État Pull Request :** La branche dédiée `fix/render-gemini-production-compliance` a été poussée sur `origin` et la Pull Request **#4** (`fix: restore anonymous IA sessions and improve Gemini production compliance`) a été ouverte vers `main` **sans fusion (`Open`)** et **sans déploiement Render**.

---

## 2. Vérification de l'environnement Git et intégrité des dépôts

Toutes les références annoncées pour la Phase A ont été vérifiées directement par commandes Git locales et `git ls-remote origin` :

| Élément vérifié | Valeur observée | Conformité |
|---|---|---|
| Worktree dédié | `/home/mahou-dev/Documents/Ecourtier2026-compliance` | Conforme |
| Branche active | `fix/render-gemini-production-compliance` | Conforme |
| `HEAD` local | `b5123db1c8fb57fd62b9b6be1527422d97c3d33b` (`fix(ia): remediate ANO-PROD-01..11 compliance (X-IA-Session-Token, ordre V21, NLU, Gemini diagnostics, UX 1000 chars)`) | Exact |
| `origin/main` | `258a5eb703c7610651e8513d5de7b28c1b30da8a` (`Merge pull request #3 from Mahou-Dev/fix/header-logout-aria-label`) | Exact |
| `merge-base` & divergence | `merge-base = 258a5eb703c7610651e8513d5de7b28c1b30da8a` ; `0` commit de retard, `1` commit d'avance (`b5123db`) | Propre / linéaire |
| Statistiques diff `origin/main..HEAD` | `37 files changed, 3402 insertions(+), 427 deletions(-)` (`30` modifiés, `7` ajoutés, `0` supprimés) | Exact |
| Index Git & working tree | Index vide ; fichiers non suivis avant mission : uniquement `rapport_render_gemini_06_remise_en_conformite.md` (ajouté en fin de mission : `rapport_render_gemini_07_revue_independante_pr.md`) | Conforme |
| Dépôt préexistant `/home/mahou-dev/Documents/Ecourtier2026` | Branche `feature/frontend-redesign-premium-saas` (`68ba3031c1b0fd40d5d659aa0bd568a07340b666`), worktrees (`9` entrées) intacts | Intact |
| État distant post-Phase D | `refs/heads/main` inchangé (`258a5eb703c7610651e8513d5de7b28c1b30da8a`) ; `refs/heads/fix/render-gemini-production-compliance` et `refs/pull/4/head` pointent tous deux sur `b5123db1c8fb57fd62b9b6be1527422d97c3d33b` | Vérifié |

---

## 3. Inventaire du diff examiné (`origin/main..b5123db`)

### 3.1 Production — Backend Java / SQL / OpenAPI (`19` fichiers dont `1` ajouté)
- `backend/src/main/resources/db/migration/V21__add_message_sequence_order_to_messages_ia.sql` *(Ajouté)* : ajout colonne `ordre BIGINT`, rétro-remplissage `ROW_NUMBER()`, `NOT NULL`, index `idx_messages_ia_conv_ordre`.
- `backend/src/main/java/com/ecourtier/domain/annonce/repository/AnnonceRepository.java` : requête ciblée `@EntityGraph(attributePaths = {"utilisateur"})` sur annonces disponibles et vérifiées (`supprime = false`).
- `backend/src/main/java/com/ecourtier/domain/ia/client/GeminiLLMProvider.java` : durcissement diagnostics non sensibles (`motifSecours`, `Retry-After` cooldown, masquage de clé via `sanitizeSecretFromText`/`sanitizeHttpException`, en-tête `x-goog-api-key`), consignes comparatives/FAQ juridique et extraction critères enrichie.
- `backend/src/main/java/com/ecourtier/domain/ia/controller/IAController.java` : documentation OpenAPI `@Parameter` pour `X-IA-Session-Token` sur `POST /{id}/messages` et `GET /{id}` (`limite 1000 caractères`).
- `backend/src/main/java/com/ecourtier/domain/ia/dto/` :
  - `ConversationCreateResponseDTO.java` (`sessionToken` ajouté)
  - `CriteresExtraitsDTO.java` (`fournisseurIa`, `modeSecours`, `motifSecours`, `preferencesSouples`, `attentesComplementaires`, `precisionsAClarifier`, champs `@JsonIgnore`)
  - `MessageItemDTO.java` (`ordre`, `criteresExtraits`)
  - `MessageIaResponseDTO.java` (`IaMetriquesDTO` avec `extractMs`, `searchMs`, `generationMs`, `llmMs`, `totalMs`)
  - `MessageUserRequestDTO.java` (`@Size(max = 1000)`)
- `backend/src/main/java/com/ecourtier/domain/ia/entity/ConversationIAEntity.java` et `MessageIAEntity.java` : ajout `ordre` et tri déterministe `@OrderBy("ordre ASC, dateEnvoi ASC, id ASC")`.
- `backend/src/main/java/com/ecourtier/domain/ia/repository/MessageIARepository.java` : méthode `findMaxOrdreByConversationId`.
- `backend/src/main/java/com/ecourtier/domain/ia/service/` :
  - `IAService.java` : génération et hachage SHA-256 / comparaison constante (`MessageDigest.isEqual`) du `sessionToken`, affectation `ordre`, requête annonces vérifiées/disponibles avec masquage PII, préservation critères sur FAQ juridique (`preserveCriteriaForLegalFaq`), télémétrie `metriques`.
  - `LlmExtractionService.java` : propagation du diagnostic réel de repli local et fusion multi-tours.
  - `RuleBasedCriteriaExtractor.java` : règles comparatives/négations (`plutôt que`, `pas une ..., mais ...`), `preferencesSouples`, `attentesComplementaires`, FAQ juridique (`FAQ_VERIFICATION_JURIDIQUE`), tolérance 1000 caractères.
- `backend/src/main/resources/static/openapi.json` et `docs/openapi.json` : contrat synchronisé (vérifié identique octet pour octet).

### 3.2 Production — Frontend TypeScript / React et documentation (`7` fichiers dont `2` ajoutés)
- `frontend/src/services/iaSession.ts` *(Ajouté)* : gestion `sessionStorage` par `conversationId` (`ecourtier_ia_session_token_<id>`), injection `X-IA-Session-Token`, purge sélective/globale.
- `frontend/src/utils/iaErrors.ts` *(Ajouté)* : messages d'erreur différenciés (`400`, `403`, `404`, `429`, `5xx`), tri `sortMessagesDeterministically`, détection diagnostic secours Gemini vs local, extraction métriques UI.
- `frontend/src/services/api.ts` : ajout automatique `X-IA-Session-Token` et nettoyage ciblé sur `403`/`404` IA.
- `frontend/src/store/useAuthStore.ts` : purge de la session IA anonyme lors du `login`/`logout`.
- `frontend/src/types/index.ts` : typage aligné OpenAPI (`sessionToken`, `ordre`, `metriques`, `fournisseurIa`, `modeSecours`, `motifSecours`, `preferencesSouples`, etc.).
- `frontend/src/components/ai/AiAssistantWidget.tsx` : limite `1000` caractères avec compteur/alerte `[data-testid="ia-char-counter"]` / `[data-testid="ia-char-limit-message"]`, badge discret `[data-testid="ia-fallback-badge"]`, latence `data-ui-total-ms`, gestion `403` sans faux message réseau.
- `README.md` : mise à jour descriptive de l'architecture et des commandes de validation.

### 3.3 Tests ajoutés / modifiés (`11` fichiers dont `4` ajoutés)
- **Backend (`7` fichiers, dont `3` ajoutés) :**
  - `AnonymousConversationSecurityIntegrationTest.java` *(Ajouté)*
  - `ComplianceAno03To11IntegrationTest.java` *(Ajouté)*
  - `GeminiAndDiagnosticsComplianceTest.java` *(Ajouté)*
  - `IAIntegrationTest.java`
  - `ChatbotFunctionalAndSecurityTest.java`
  - `RuleBasedCriteriaExtractorTest.java`
  - `FlywayFreshMigrationIntegrationTest.java`
- **Frontend E2E Playwright (`4` fichiers, dont `1` ajouté) :**
  - `frontend/e2e/ia-compliance-widget.spec.ts` *(Ajouté)*
  - `frontend/e2e/ia-unit-session-errors.spec.ts`
  - `frontend/e2e/chatbot-groq-widget.spec.ts`
  - `frontend/e2e/chatbot-groq-r1-race.spec.ts`
  - `frontend/e2e/chatbot-groq-fullstack.spec.ts` *(modifié dans le diff)*

---

## 4. Analyse architecturale et technique indépendante

### 4.1 Architecture IA exclusive Google Gemini
- La revue du code source (`backend/src/main`, `frontend/src`, `render.yaml`, `.env.example`, `docker-compose*.yml`) confirme qu'**aucun appel runtime vers Groq (`GroqClientService`), OpenAI (`OpenAILLMProvider`) ou Claude (`ClaudeLLMProvider`)** n'est exécuté dans le flux IA de production.
- `ProductionConfigurationValidator.java` impose explicitement `ecourtier.ia.provider=gemini` lorsque l'IA est activée en profil `prod` (et rejette tout autre fournisseur, y compris `groq`, `openai`, `claude`).
- `GeminiLLMProvider` appelle exclusivement `generativelanguage.googleapis.com/v1beta` (modèle par défaut `gemini-flash-lite-latest`), transmet la clé par en-tête HTTP `x-goog-api-key` (jamais en query string), applique un cooldown borné sur `429` (`Retry-After`) et masque toute occurrence éventuelle de secret dans les logs (`sanitizeSecretFromText`, `sanitizeHttpException`).
- **Point d'attention architectural mineur :** `LlmExtractionService` injecte encore `groqClientService`, `openaiProvider` et `claudeProvider` en champs `@SuppressWarnings("unused")` (code mort d'injection) et conserve une méthode interne `callLegacyLlmApi` utilisée uniquement par `LlmExtractionMockServerTest`. Ce maintien ne présente pas de risque d'exécution externe en production tant que le chemin nominal passe par `GeminiLLMProvider`, mais gagnera à être nettoyé ultérieurement.

### 4.2 Fiabilité NLU, comparatifs, préférences souples et FAQ juridique
- L'extraction locale (`RuleBasedCriteriaExtractor`) et le prompt Gemini traitent explicitement les négations/comparatifs courants (« un appartement plutôt qu'une villa » -> `APPARTEMENT`, « pas une maison, mais un appartement » -> `APPARTEMENT`, « acheter plutôt que louer » -> `VENTE`) ainsi que la distinction entre filtres durs (`meuble=true`) et préférences qualitatives souples (`je préférerais un logement meublé`, `quartier calme` -> `preferencesSouples`, `cour`, `cuisine équipée`, `jardin` -> `attentesComplementaires`).
- Les questions de fiabilité/titre foncier (`FAQ_VERIFICATION_JURIDIQUE`) préservent les critères déjà extraits au lieu d'effacer le contexte et évitent l'affichage trompeur d'un message d'absence de résultat.
- Un **bug mineur P3** subsiste dans `RuleBasedCriteriaExtractor.java` (`lines 213-222`) : pour l'intention `FAQ_VERIFICATION_JURIDIQUE`, `builder.motifSecours("CLE_ABSENTE")` est positionné en amont, sans tenir compte d'un contexte où `IA_ENABLED=false` (`IA_DESACTIVEE`) ou `AI_PROVIDER` non autorisé (`FOURNISSEUR_NON_AUTORISE`).

### 4.3 Qualité des recommandations et télémétrie
- `IAService` ne charge plus l'intégralité de la table `annonces` via `findAll()`, mais utilise `findBySupprimeFalseAndStatutPublicationAndStatutVerification(StatutPublication.DISPONIBLE, StatutVerification.VERIFIEE)` avec `@EntityGraph(attributePaths = {"utilisateur"})`, garantissant la disponibilité et l'absence de `LazyInitializationException` avec `open-in-view: false`.
- `MessageIaResponseDTO.metriques` distingue `extractMs`, `searchMs`, `generationMs`, `llmMs` et `totalMs`, complétés côté widget par `data-ui-total-ms` et un libellé adapté lorsqu'un mode secours local a répondu (`fallback-local`).

---

## 5. Revue sécurité, secrets, CORS, sessions et Render

1. **Authentification anonyme cross-origin sans cookie (`X-IA-Session-Token`) :**
   - À la création (`POST /api/v1/ia/conversations`), lorsque l'utilisateur n'est pas authentifié, `IAService` émet un `sessionToken` (`SecureRandom` 32 octets / 64 caractères hexadécimaux) dans le corps JSON et enregistre uniquement son condensat **SHA-256** en base (`tokenHash`). La vérification s'effectue à temps constant via `MessageDigest.isEqual`.
   - Aucun `Set-Cookie` cross-site problématique n'est requis sur `.onrender.com`.
   - **Réserve d'architecture sécurité :** le stockage dans `sessionStorage` expose le jeton anonyme en cas de vulnérabilité XSS côté frontend ; ni `render.yaml` (`runtime: static`) ni `frontend/nginx.conf` (`lines 1-29`, qui posent `X-Content-Type-Options`, `X-Frame-Options` et `Referrer-Policy`) ne configurent actuellement d'en-tête `Content-Security-Policy` (`CSP`). De même, `IAService.java` (`lines 185-213`) accepte à la création un `X-IA-Session-Token` fourni par le client si non vide avant de générer côté serveur (hygiène mineure : génération systématique côté serveur préférable).
2. **CORS (`SecurityConfig.java`) :**
   - Vérifié : `allowedOrigins` piloté par `ECOURTIER_CORS_ALLOWED_ORIGINS`, méthodes `GET/POST/PUT/DELETE/OPTIONS`, `allowCredentials=true`, `allowedHeaders=*`. Confirmé en runtime réel sur `OPTIONS /api/v1/ia/conversations` depuis `http://127.0.0.1:4173` avec `Access-Control-Request-Headers: Content-Type, X-IA-Session-Token` (`HTTP 200`).
3. **Secrets & configuration Render :**
   - Scan statique de l'ensemble du diff, des fichiers Docker/Compose, de `render.yaml`, de `.env.example`, des `application-*.yml` et du bundle frontend `dist/` : **aucune clé réelle (`AIza...`, `gsk_...`, JWT de prod, mot de passe DB)** n'est présente dans le dépôt.
   - `ProductionConfigurationValidator` bloque tout démarrage `prod` sans `JWT_SECRET` valide, sans mot de passe DB non trivial, avec CORS `*` ou `localhost`, ou avec un fournisseur LLM non autorisé.
   - **Écart de déploiement Render constaté dès `RENDER-GEMINI-06` et non résolu dans Git :** l'architecture de production réelle doit confirmer quel service backend Render est actif (`ecourtier-backend.onrender.com` vs `ecourtier-backend-uz6e.onrender.com`), l'alignement exact de `VITE_API_BASE_URL` côté frontend, `ECOURTIER_CORS_ALLOWED_ORIGINS` et la présence effective de `GEMINI_API_KEY`.

---

## 6. Revue PostgreSQL / Flyway `V21` / JPA / OpenAPI / Docker

- **Flyway `V21` :** `V21__add_message_sequence_order_to_messages_ia.sql` ajoute `ordre BIGINT`, attribue un rang déterministe `ROW_NUMBER() OVER (PARTITION BY conversation_id ORDER BY date_envoi ASC, id ASC)` aux messages existants, rend la colonne `NOT NULL` et ajoute l'index composite `idx_messages_ia_conv_ordre (conversation_id, ordre)`.
- **Validité PostgreSQL 16 réelle :** les 21 migrations `V1..V21` ont été exécutées from-scratch contre un conteneur Docker `postgres:16-alpine` (PostgreSQL `16.15`) via `FlywayFreshMigrationIntegrationTest` en `0.264s` sans erreur.
- **Réserve mineure de concurrence (`IAService.java:423-448`) :** `findMaxOrdreByConversationId(conversationId) + 1L` s'exécute sans verrou pessimiste sur la ligne `conversations_ia` ni contrainte SQL `UNIQUE (conversation_id, ordre)` dans `V21`. Le frontend bloque les soumissions simultanées (`disabled={isSending}`), mais deux appels API concurrents directs sur une même conversation pourraient théoriquement générer deux lignes de même `ordre`.
- **OpenAPI & Docker :**
  - `cmp -s docs/openapi.json backend/src/main/resources/static/openapi.json` -> identiques (`OPENAPI_SYNC_OK`).
  - `docker-compose.yml` et `docker-compose.prod.yml` valides en syntaxe YAML/Compose (les commandes sans variables d'environnement refusent intentionnellement de rendre le YAML lorsque `JWT_SECRET`/`POSTGRES_PASSWORD` manquent).

---

## 7. Résultats des tests et vérifications réellement exécutés

Tous les tests ci-dessous ont été exécutés directement dans le worktree `/home/mahou-dev/Documents/Ecourtier2026-compliance` lors de cette mission `RENDER-GEMINI-07` **sans aucun appel ni modification liés au périmètre WAVE/transactions**.

### 7.1 Backend Maven (hors `com.ecourtier.domain.transaction.**`)
- **Commande :** `mvn -B test -Dtest='!com.ecourtier.domain.transaction.**.*Test' -Djacoco.skip=true -DfailIfNoTests=false`
- **Résultat global :** **`Tests run: 365, Failures: 0, Errors: 0, Skipped: 5` — `BUILD SUCCESS`**
- **Détail clé sur le périmètre IA & configuration :**
  - `GeminiLLMProviderTest` : `14/14` (`0` échec, serveur HTTP mock)
  - `GeminiAndDiagnosticsComplianceTest` : `7/7` (`0` échec, serveur HTTP mock)
  - `AnonymousConversationSecurityIntegrationTest` : `14/14` (`0` échec)
  - `IAIntegrationTest` : `3/3` (`0` échec)
  - `AssistantIaRemediationComprehensiveTest` : `5/5` (`0` échec)
  - `ChatbotFunctionalAndSecurityTest` : `12/12` (`0` échec)
  - `ComplianceAno03To11IntegrationTest` : `6/6` (`0` échec)
  - `IAServiceClockTest` : `3/3` (`0` échec)
  - `IAServiceMultiTourAno07IntegrationTest` : `5/5` (`0` échec)
  - `IaRateLimiterTest` : `4/4` (`0` échec)
  - `LlmExtractionMockServerTest` : `2/2` (`0` échec)
  - `LlmExtractionServiceTest` : `6/6` (`0` échec)
  - `RuleBasedCriteriaExtractorTest` : `10/10` (`0` échec)
  - `ProductionConfigurationValidatorTest` : `15/15` (`0` échec)
  - `OpenApiContractIntegrationTest` : `1/1` (`0` échec)
  - Les `5` tests ignorés dans cette passe correspondent uniquement aux classes annotées `@EnabledIfSystemProperty(named = "ecourtier.test.pg16", matches = "true")` : `FlywayFreshMigrationIntegrationTest` (`1`) et `Postgres16Phase3IntegrationTest` (`4`).

### 7.2 PostgreSQL 16 Testcontainers (ciblé hors `domain.transaction`)
- **Commande :** `mvn -B test -Decourtier.test.pg16=true -Dtest=FlywayFreshMigrationIntegrationTest -Djacoco.skip=true`
- **Résultat :** **`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`** (`BUILD SUCCESS`, `postgres:16-alpine` / PostgreSQL `16.15`, migrations `V1..V21` appliquées avec succès).
- **Exclus / `NON TESTÉ` :** `Postgres16Phase3IntegrationTest` (`4` tests) non exécuté car la classe importe `com.ecourtier.domain.transaction.*` et teste la table `transactions` (exclusion stricte WAVE/transactions respectée).

### 7.3 Frontend Build, Lint, Scan Secrets & Playwright isolé (`:3100`)
- `npm run lint` (`eslint . --max-warnings 0`) : **`0` erreur, `0` avertissement**.
- `npm run build` (`tsc && vite build`) : **succès en `8.18s`**, `63` entrées PWA générées ; scan `grep` `AIza|gsk_|GROQ_API_KEY` dans `dist/` : **aucune fuite**.
- Playwright isolé sur `vite preview` (`http://127.0.0.1:3100`, routes réseau IA mockées dans les specs) :
  - `ia-unit-session-errors.spec.ts` + `ia-compliance-widget.spec.ts` + `chatbot-groq-widget.spec.ts` + `chatbot-groq-r1-race.spec.ts` : **`19 passed (34.1s)`**.
- **Non exécutés (`NON TESTÉ`) :** `frontend/e2e/chatbot-groq-fullstack.spec.ts` (suppose services lancés sur `8089`/`5173`), `phase2-security-ui.spec.ts` et `phase3-real-backend.spec.ts` (exclus car ils référencent WAVE/transactions).

### 7.4 Vérification éphémère cross-origin réelle (`Spring Boot :18080` + `vite preview :4173`)
Serveurs lancés de façon isolée (`dev,test` sur H2 mémoire sans impacter aucune base locale, puis détruits et ports vérifiés libérés) :
- **Catalog seed `GET /api/v1/annonces?size=20` :** `7` annonces disponibles pour tests locaux.
- **CORS preflight `OPTIONS /api/v1/ia/conversations` (`Origin: http://127.0.0.1:4173`) :** `HTTP 200`, `Access-Control-Allow-Origin: http://127.0.0.1:4173`, `Access-Control-Allow-Headers: Content-Type, X-IA-Session-Token`.
- **Création session anonyme `POST /api/v1/ia/conversations` :** `HTTP 201`, émission de `sessionToken` (`64` hex), absence de `Set-Cookie`.
- **Contrôles d'accès et validation :**
  - `GET /{id}` sans `X-IA-Session-Token` -> `HTTP 403`
  - `GET /{id}` avec jeton invalide -> `HTTP 403`
  - `POST /{id}/messages` sans jeton -> `HTTP 403`
  - `POST /{id}/messages` à `1001` caractères -> `HTTP 400`
  - Dépassement de quota anonyme (`> 10 req/min`) -> `HTTP 429` effectif.
- **Scénarios NLU/FAQ/Ordre réels via `RuleBasedCriteriaExtractor` (`CLE_ABSENTE` locale) :**
  - `"un appartement plutôt qu'une villa"` -> type `APPARTEMENT` (`1` suggestion)
  - `"pas une maison, mais un appartement"` -> type `APPARTEMENT` (`1` suggestion)
  - `"acheter plutôt que louer"` -> `modeTransaction: "VENTE"` cumulé avec type `APPARTEMENT`
  - `"je préférerais un logement meublé"` -> `preferencesSouples: ["logement meublé de préférence"]` (sans filtre strict `meuble: true`), relance budget sans `"aucune annonce"` prématuré
  - `"avec cuisine équipée dans un quartier calme avec cour et jardin à Mermoz"` -> `quartier: "Mermoz"`, `preferencesSouples` enrichies (`"quartier calme"`), `attentesComplementaires: ["cour", "cuisine équipée", "jardin"]`
  - `"Comment être sûr que les annonces sont fiables ?"` -> réponse FAQ anti-arnaque / vérification foncière préservant `type: "APPARTEMENT"` et `quartier: "Mermoz"`
  - `GET /api/v1/ia/conversations/{id}` : `8` messages restitués en ordre strict `1..8`.
- **Parcours Chromium Playwright réel cross-origin (`127.0.0.1:4173` -> `127.0.0.1:18080`) :**
  - Compteur `0/1000` -> `1000/1000` + `"Limite de 1000 caractères atteinte"`.
  - Envoi message réel -> `POST /conversations -> 201`, `POST /messages -> 200`, badge `"Mode secours local"`, stockage `sessionStorage` (`ecourtier_ia_session_token_<id>` `64` chars + `ecourtier_ia_conversation_anon` `36` chars).
  - Rechargement complet `F5` (`page.reload()`) -> `GET /conversations/<id> -> 200`, conversation restaurée dans l'ordre chronologique (`2` messages persistés affichés ; l'accueil statique client initial n'est pas persisté en base), `0` `console.error`, `0` `pageerror`.

---

## 8. Anomalies, risques et réserves identifiés (Statuts officiels)

### 8.1 Matrice des anomalies `ANO-PROD-01` .. `ANO-PROD-11`

| ID | Criticité | Description | Statut officiel | Justification factuelle vérifiée |
|---|---|---|---|---|
| `ANO-PROD-01` | P0 | Blocage `403` cross-origin des visiteurs anonymes (`SameSite` cookie) | **`CORRIGÉ MAIS NON VALIDÉ`** | Entièrement corrigé et prouvé en cross-origin réel local (`18080` / `4173` + `F5` + `14/14` JUnit) ; non re-testé sur l'environnement Render réel (non déployé). |
| `ANO-PROD-02` | P0 | `13/13` réponses via `fallback-local`, aucune preuve d'exécution Gemini live | **`CORRIGÉ MAIS NON VALIDÉ`** | Client Gemini exclusif, masquage secrets et `motifSecours` vérifiés sur serveur HTTP mocké (`14+7` tests) ; aucune exécution réelle vers Google Gemini avec `GEMINI_API_KEY` Render réalisée. |
| `ANO-PROD-03` | P1 | Comparatifs & négations NLU (`plutôt que`, `pas X, mais Y`) | **`CORRIGÉ ET VALIDÉ`** | Validé par JUnit (`ComplianceAno03To11IntegrationTest`) et requêtes HTTP réelles sur `:18080`. *(Le comportement avec Gemini live reste couvert avec `ANO-PROD-02`.)* |
| `ANO-PROD-04` | P1 | Inversion chronologique après `F5` | **`CORRIGÉ ET VALIDÉ`** | Colonne `ordre` (`V21`) validée sur PostgreSQL 16 (`1/1`), ordre `1..8` vérifié sur `:18080` et tri défensif UI validé sous Playwright (`F5` et réponse désordonnée). *(Réserve mineure de concurrence ci-dessous.)* |
| `ANO-PROD-05` | P1 | Réponses hors cible sur fiabilité / titre foncier (`IA-10`) | **`CORRIGÉ ET VALIDÉ`** | Validé par JUnit et appel HTTP réel `:18080` avec préservation des critères multi-tours. |
| `ANO-PROD-06` | P1 | `0` annonce vérifiée en production (recommandations vides) | **`BLOQUÉ PAR UNE DÉPENDANCE EXTERNE`** | La requête SQL filtrée `@EntityGraph` est corrigée et validée localement, mais la base de production nécessite des annonces réelles validées/publiées par l'administrateur (hors périmètre code Git). |
| `ANO-PROD-07` | P2 | Troncature silencieuse à `500` caractères | **`CORRIGÉ ET VALIDÉ`** | `@Size(max = 1000)` vérifié (`HTTP 400` à `1001` chars sur `:18080`), UI `0/1000`..`1000/1000` et alerte vérifiées sous Playwright réel. |
| `ANO-PROD-08` | P2 | Pertes mots-clés (`cour`, `cuisine`, `calme`) et filtre `meuble` strict | **`CORRIGÉ ET VALIDÉ`** | `preferencesSouples` et `attentesComplementaires` vérifiés par JUnit et appels HTTP réels `:18080`. |
| `ANO-PROD-09` | P2 | Réponses *"aucune annonce"* prématurées / relances rigides | **`CORRIGÉ ET VALIDÉ`** | Demande partielle (`logement meublé de préférence`) orientée vers précision du budget sans refus prématuré sur `:18080`. |
| `ANO-PROD-10` | P2 | Latences élevées en production et absence de télémétrie / `findAll()` | **`CORRIGÉ MAIS NON VALIDÉ`** | Suppression `findAll()` (`@EntityGraph`) et exposition télémétrie `metriques` / `data-ui-total-ms` validées localement, mais la latence réelle Render/Gemini live reste à mesurer en prod. |
| `ANO-PROD-11` | P3 | Erreur UI *"problème réseau"* sur `403`, FCFA, reset sur `403`/`404` | **`CORRIGÉ ET VALIDÉ`** | Gestion des statuts HTTP et formatage vérifiés dans `ia-unit-session-errors`, `ia-compliance-widget`, `chatbot-groq-r1-race` et tests backend. |

### 8.2 Points d'attention techniques complémentaires relevés en revue indépendante

| ID | Gravité | Composant / Fichier | Statut | Détail factuel |
|---|---|---|---|---|
| `REV-01` | Mineur (P3) | `RuleBasedCriteriaExtractor.java` (`lines 213-222`) & `LlmExtractionService.java` | **`À CORRIGER`** | Sur `FAQ_VERIFICATION_JURIDIQUE`, `motifSecours("CLE_ABSENTE")` est figé même si `IA_ENABLED=false` ou `AI_PROVIDER` non autorisé (code diagnostic incohérent hors cas clé absente). |
| `REV-02` | Mineur (Hygiène) | `IAService.java` (`lines 185-213`) | **`À CORRIGER`** | À la création anonyme, un `X-IA-Session-Token` fourni par le client est réutilisé s'il n'est pas vide ; il serait plus strict de toujours générer côté serveur. |
| `REV-03` | Mineur (Concurrence) | `V21` & `IAService.java` (`lines 423-448`) | **`À CORRIGER`** | Index non unique `idx_messages_ia_conv_ordre` sans contrainte `UNIQUE (conversation_id, ordre)` ni verrou pessimiste conversation lors d'appels API directs strictement simultanés. |
| `REV-04` | Sécurité / Ops | `frontend/nginx.conf` (`lines 1-29`), `render.yaml` | **`À CORRIGER`** | Aucun en-tête `Content-Security-Policy` (`CSP`) n'est défini alors que le jeton anonyme est conservé dans `sessionStorage`. |
| `REV-05` | Couverture exclue | `Postgres16Phase3IntegrationTest` (`4`), `chatbot-groq-fullstack.spec.ts`, suites transaction/WAVE | **`NON TESTÉ`** | Non exécutés dans cette campagne soit par exclusion stricte WAVE/transactions, soit car hors portée serveur distant `8089`/`5173`. |

Ces réserves `REV-01..04` ne constituent pas des régressions par rapport à `main` et n'empêchent pas l'ouverture de la Pull Request pour revue humaine.

---

## 9. État de la Pull Request

- **Dépôt :** `Mahou-Dev/Ecourtier2026`
- **Pull Request créée :** **[#4 — `fix: restore anonymous IA sessions and improve Gemini production compliance`](https://github.com/Mahou-Dev/Ecourtier2026/pull/4)**
- **Branche source :** `fix/render-gemini-production-compliance` (`b5123db1c8fb57fd62b9b6be1527422d97c3d33b`)
- **Branche cible :** `main` (`258a5eb703c7610651e8513d5de7b28c1b30da8a`)
- **Statut vérifié :** **`Open`** (capture navigateur vérifiée + `git ls-remote origin refs/pull/4/head` = `b5123db1c8fb57fd62b9b6be1527422d97c3d33b`, `refs/heads/main` inchangé).
- **Description PR :** Inclut les 10 sections requises (contexte `ANO-PROD-01..11`, résumé des correctifs, sécurité SHA-256 / nuance `sessionStorage` + CSP, migration Flyway `V21`, résultats exacts des tests locaux, limites des tests mockés, variables Render requises, prérequis catalogue `ANO-PROD-06`, procédure de validation post-déploiement, déclaration explicite d'absence de merge/déploiement).

---

## 10. Actions manuelles restantes pour l'utilisateur / équipe

1. **Revue & Fusion GitHub :**
   - Relire la Pull Request [#4](https://github.com/Mahou-Dev/Ecourtier2026/pull/4) et ses `37` fichiers modifiés, décider de l'intégration éventuelle de `REV-01..04` avant ou dans une PR dédiée, puis fusionner vers `main` lorsque validée.
2. **Configuration Render :**
   - Confirmer le service backend actif (`ecourtier-backend.onrender.com` vs `ecourtier-backend-uz6e.onrender.com`) et aligner `VITE_API_BASE_URL` côté frontend.
   - Renseigner/vérifier `GEMINI_API_KEY` (clé API Google AI valide, jamais exposée), `IA_ENABLED=true`, `AI_PROVIDER=gemini`, `GEMINI_MODEL=gemini-flash-lite-latest` et `ECOURTIER_CORS_ALLOWED_ORIGINS=https://ecourtier-frontend.onrender.com`.
   - Envisager l'ajout d'en-têtes `Content-Security-Policy` sur l'hébergement statique frontend.
3. **Déploiement Render & Données métier (`ANO-PROD-06`) :**
   - Déployer manuellement sur Render après fusion vers `main`.
   - Publier et valider administrativement (`VERIFIEE` + `DISPONIBLE`) des annonces réelles dans la base PostgreSQL de production.
4. **Validation finale post-déploiement en production :**
   - Vérifier les logs Render (démarrage `Google Gemini`, absence de fallback forcé) ;
   - Exécuter un parcours visiteur anonyme sur `https://ecourtier-frontend.onrender.com` (`POST /conversations -> 201`, réponse Gemini réelle sans badge `"Mode secours local"`, rechargement `F5 -> 200` et ordre chronologique préservé).

---

## 11. Confirmation explicite des exclusions et règles respectées

- **WAVE strictement exclu :** Aucun fichier, test, route, API, configuration ou donnée WAVE / `domain.transaction` n'a été consulté, modifié, appelé ou exécuté (`365` tests backend lancés via `!com.ecourtier.domain.transaction.**.*Test` ; `Postgres16Phase3IntegrationTest`, `phase2-security-ui.spec.ts` et `phase3-real-backend.spec.ts` non exécutés).
- **Hébergement & LLM :** Aucun recours à Railway ; aucun usage runtime de Groq, OpenAI ou Claude.
- **Secrets :** Aucune clé API, jeton ou mot de passe réel exposé dans le code, la PR ou ce rapport.
- **Sécurité Git & Production :** Aucun `reset`, `clean`, `stash`, `checkout`, `rebase`, `cherry-pick`, `prune`, `git add .` ; aucun push direct ni merge sur `main` ; aucun déploiement Render ni modification de base de données de production ; dépôt préexistant `/home/mahou-dev/Documents/Ecourtier2026` (`feature/frontend-redesign-premium-saas`) et ses worktrees vérifiés intacts.
