# Rapport de Diagnostic Technique — Mission RENDER-GEMINI-11

**Projet :** E-Courtier Sénégal — Projet 14 (Licence 3 Ingénierie Informatique)  
**Objet :** Diagnostic de l'indisponibilité du chatbot en production et plan de correction ciblé  
**Date du diagnostic :** 23 novembre 2026  
**Dépôt analysé :** `/home/mahou-dev/Documents/Ecourtier2026-compliance`  
**Branche PR #4 inspectée :** `fix/render-gemini-production-compliance` (`0ca66d19097cc5cbc7b27bdbabef419ea7d3c362`, basée sur les commits `b5123db`, `fcad3ea`, `0ca66d1`)  
**Branche cible / production observée :** `origin/main` (`258a5eb703c7610651e8513d5de7b28c1b30da8a`)

---

## 1. Résumé exécutif du diagnostic

L'affichage en production du message :

> « Désolé, l'assistant IA est actuellement indisponible ou rencontre un problème réseau. Vous pouvez utiliser la recherche classique. »

est **entièrement expliqué par deux mécanismes combinés présents en production (`origin/main` `258a5eb`), dont la correction existe sur la branche PR #4 (`0ca66d1`) mais n'a pas encore été fusionnée ni déployée sur Render** :

1. **La production Render exécute toujours `origin/main` (`258a5eb`), antérieur à la PR #4.**  
   Les observations passives réalisées le 23 novembre 2026 à `08:51 UTC` confirment que `https://ecourtier-frontend.onrender.com` sert un bundle construit le `Sun, 22 Nov 2026 09:46:20 UTC` contenant encore le texte d'erreur exact de `origin/main` sans `X-IA-Session-Token`, et que `https://ecourtier-backend.onrender.com/openapi.json` fait `230 074` octets (identique au byte près à `origin/main`, contre `234 102` octets dans la PR #4) sans aucune trace de `sessionToken`, `X-IA-Session-Token`, `motifSecours` ou `fournisseurIa`.
2. **En production (`origin/main`), la session anonyme cross-site déclenche un HTTP `403`, ensuite masqué en erreur réseau/indisponibilité par le frontend.**  
   Sur `origin/main`, le backend (`IAService.java:216-225`) crée un cookie anonyme `ecourtier_ia_session` avec `SameSite=Lax`. Or `ecourtier-frontend.onrender.com` et `ecourtier-backend.onrender.com` appartiennent à deux sites distincts du fait de l'inscription d'`onrender.com` dans la Public Suffix List : le navigateur n'envoie pas ce cookie sur les requêtes XHR/Fetch cross-site (`POST /messages` et `GET /{id}`). Le backend rejette alors la requête en **HTTP `403 Forbidden`** (*« Accès refusé : preuve de possession requise pour cette conversation anonyme »*). De son côté, `origin/main:AiAssistantWidget.tsx:227-246` ne traite explicitement que les statuts `429` et `400` : tout `403`, `401`, `404`, `500/502/503/504` ou échec réseau déclenche le message fourre-tout observé.

**Ni CORS, ni Gemini, ni PostgreSQL ne sont en panne sur les endpoints observés** : `GET /api/v1/health` répond `HTTP 200 UP`, le preflight `OPTIONS /api/v1/ia/conversations` répond `HTTP/2 200` avec `access-control-allow-origin: https://ecourtier-frontend.onrender.com`, et le repli local déterministe `RuleBasedCriteriaExtractor` retourne `HTTP 200` dès que Gemini est en timeout, quota `429`, erreur HTTP ou sans clé.

Sur la branche PR #4 (`fix/render-gemini-production-compliance` `0ca66d1`), la remédiation (`X-IA-Session-Token` côté header + `sessionStorage`, `iaErrors.ts`, diagnostics `motifSecours`) a été validée localement sur l'ensemble des 12 scénarios demandés. En revanche, **aucun correctif n'ayant été fusionné dans `main` ni déployé sur Render pendant cette mission de diagnostic**, le problème demeure actif en production.

---

## 2. Chaîne complète d'apparition du message frontend

### 2.1 État actuellement en production (`origin/main` `258a5eb`)

- **Composant React :** `AiAssistantWidget` (`origin/main:frontend/src/components/ai/AiAssistantWidget.tsx`, chunk déployé `/assets/AiAssistantWidget-BiJyGg_S.js`).
- **Fonction déclenchante :** bloc `catch (err: unknown)` de `handleSendMessage` (`origin/main:frontend/src/components/ai/AiAssistantWidget.tsx:227-246`).
- **Chaîne d'exécution menant au texte affiché :**

```text
Utilisateur envoie un message
  -> POST /api/v1/ia/conversations (201) + Set-Cookie: ecourtier_ia_session=...; SameSite=Lax
  -> POST /api/v1/ia/conversations/{id}/messages (cross-site sur *.onrender.com : cookie Lax omis)
  -> origin/main:IAService.verifierDroitsConversation() -> HTTP 403 Forbidden
  -> origin/main:apiFetch() lève Error avec err.status = 403
  -> origin/main:AiAssistantWidget.handleSendMessage() :
       status === 429 ? non
       status === 400 ? non
       sinon -> displayText = "Désolé, l'assistant IA est actuellement indisponible ou rencontre un problème réseau. Vous pouvez utiliser la recherche classique."
```

- **Exceptions et codes HTTP écrasés dans `origin/main` :**
  - `HTTP 403` (cookie/jeton anonyme absent, invalide ou expiré) -> écrasé en message générique indisponible/réseau ;
  - `HTTP 401` (JWT expiré) et `Error('Session expirée')` sans `.status` (`origin/main:api.ts:116-119`) -> écrasés en message générique indisponible/réseau ;
  - `HTTP 404` (conversation absente) -> écrasé en message générique indisponible/réseau ;
  - `HTTP 500`, `502`, `503`, `504` (erreur serveur / proxy Render en réveil ou indisponible) -> écrasés en message générique indisponible/réseau ;
  - Erreur réseau réelle (`TypeError: Failed to fetch`, DNS, coupure, preflight CORS bloqué) -> même message générique indisponible/réseau ;
  - Seuls `HTTP 429` et `HTTP 400` affichent un texte dédié sur `origin/main`.
- **Cas « Gemini indisponible mais fallback local fonctionnel » :** lorsque Gemini échoue (clé absente, timeout, `429`, `400`, `403`, `500`), `IAService` + `LlmExtractionService` bascule sur `RuleBasedCriteriaExtractor` et retourne **`HTTP 200`** avec une réponse complète et les annonces vérifiées ; ce message d'erreur **n'apparaît donc pas** à cause de Gemini lorsque la session est valide.

### 2.2 État dans la PR #4 non encore fusionnée (`fix/render-gemini-production-compliance` `0ca66d1`)

Dans la PR #4 (`0ca66d1`), la chaîne `"Désolé, l'assistant IA est actuellement indisponible ou rencontre un problème réseau..."` a été supprimée et remplacée par le module `frontend/src/components/ai/iaErrors.ts` (`mapIaError`, `isNetworkError`, `IA_ERROR_MESSAGES`, `describeMotifSecours`) consommé par `AiAssistantWidget.tsx:249-265`.

| Condition / Code HTTP | Comportement `origin/main` (en production) | Comportement PR #4 (`0ca66d1`, local) |
|---|---|---|
| Erreur réseau réelle (`TypeError`, offline, port fermé, preflight bloqué) | Message générique indisponible/réseau | `kind: 'network'` -> « Connexion impossible : vérifiez votre connexion internet puis réessayez dans quelques instants. » |
| `HTTP 400` (message invalide / politique sécurité / > max) | Message API ou max 500 caractères | Distingue `security-policy`, `too-long` (max 1000 car.) et `bad-request` |
| `HTTP 401` (JWT expiré / non authentifié) | Masqué dans message générique indisponible/réseau | `kind: 'unauthorized'` -> « Session expirée, reconnectez-vous pour continuer. » (`apiFetch` attache `status = 401` via `withStatus`) |
| `HTTP 403` avec `conversationId` actif | Masqué dans message générique indisponible/réseau | `kind: 'conversation-invalid'` + `resetConversation: true` -> « Votre session de conversation a expiré ou n'est plus valide. Une nouvelle conversation a été démarrée : renvoyez votre message. » |
| `HTTP 403` sans `conversationId` | Masqué dans message générique indisponible/réseau | `kind: 'forbidden'` -> « Accès refusé. » |
| `HTTP 404` avec `conversationId` actif | Masqué dans message générique indisponible/réseau | `kind: 'conversation-not-found'` + `resetConversation: true` -> « Cette conversation est introuvable. Une nouvelle conversation a été démarrée : renvoyez votre message. » |
| `HTTP 429` (rate limiter IA) | Message quota | `kind: 'rate-limit'` -> message backend ou « Limite de requêtes IA atteinte... » |
| `HTTP 500`, `502`, `503`, `504` | Masqué avec erreur réseau | `kind: 'unavailable'` -> « L'assistant IA est momentanément indisponible. Vous pouvez utiliser la recherche classique. » (distinct de `network`) |
| Gemini indisponible + fallback local OK | `HTTP 200` (mais bloqué en amont en anonyme par `403` cookie) | `HTTP 200`, badge `Mode secours local` (`data-testid="ia-fallback-badge"` + infobulle `describeMotifSecours(motifSecours)`) |

---

## 3. Hypothèses analysées avec verdict pour chacune

| Hypothèse | Verdict | Preuves techniques |
|---|---|---|
| **H1. Production Render restée sur `origin/main` (`258a5eb`) avant PR #4** | **CONFIRMÉE (cause directe du maintien de l'anomalie)** | Le frontend live sert `AiAssistantWidget-BiJyGg_S.js` (`last-modified: Sun, 22 Nov 2026 09:46:20 UTC`, heure du PR #3) avec l'ancien message et sans `X-IA-Session-Token`. `/openapi.json` du backend live pèse `230 074` octets comme `origin/main` (vs `234 102` dans `0ca66d1`). |
| **H2. Cookie anonyme `SameSite=Lax` bloqué en cross-site sur `*.onrender.com` -> `403`** | **CONFIRMÉE (cause technique primaire côté `origin/main`)** | `origin/main:IAService.java:216-225` émet `.sameSite("Lax")`. `ecourtier-frontend.onrender.com` et `ecourtier-backend.onrender.com` étant sur le suffixe public `onrender.com`, le navigateur n'envoie pas le cookie en cross-site -> `verifierDroitsConversation` rejette en `403 Forbidden`. |
| **H3. Masquage côté frontend (`origin/main:AiAssistantWidget.tsx:234-245`)** | **CONFIRMÉE (amplificateur UX direct)** | Le `catch` de `origin/main` assimile `403`, `401`, `404` et `500/5xx` à une erreur réseau / indisponibilité globale, cachant la vraie cause (`403` de session anonyme) derrière le texte signalé. |
| **H4. Blocage CORS sur Render** | **INFIRMÉE** | `OPTIONS https://ecourtier-backend.onrender.com/api/v1/ia/conversations` avec `Origin: https://ecourtier-frontend.onrender.com` et `Access-Control-Request-Headers: Content-Type, X-IA-Session-Token` répond `HTTP/2 200` avec `access-control-allow-origin: https://ecourtier-frontend.onrender.com` et `access-control-allow-credentials: true`. |
| **H5. Échec Google Gemini provoquant un `500` backend** | **INFIRMÉE** | `GeminiLLMProvider` + `LlmExtractionService` capturent `CLE_ABSENTE`, `IA_DESACTIVEE`, `TIMEOUT`, `QUOTA` (`429`), `ERREUR_HTTP` (`400/401/403/404/500/503`) et `REPONSE_INVALIDE` pour basculer en `RuleBasedCriteriaExtractor` (`HTTP 200`), prouvé par tests unitaires et E2E. |
| **H6. Ancien hostname `ecourtier-backend-uz6e.onrender.com` en timeout** | **HORS CHEMIN CRITIQUE ACTUEL** | `ecourtier-backend-uz6e.onrender.com` a expiré après `15.001s` (`HTTP_CODE=000`) en contrôle passif, mais `/assets/index-CzU5S-X5.js` du frontend live pointe bien vers `https://ecourtier-backend.onrender.com` (`HTTP 200 UP`). |
| **H7. Réveil lent Render Free Tier + absence de timeout client `fetch()`** | **FACTEUR AGGRAVANT SECONDAIRE POSSIBLE** | Sur Render plan gratuit, un redémarrage à froid dépasse parfois plusieurs dizaines de secondes. `frontend/src/services/api.ts:100` appelle `fetch(...)` sans `AbortController` / timeout dédié : un proxy `502/503` donne `unavailable`, et une coupure TCP donne `network`. |

---

## 4. Analyse du code actuel (branche PR #4 `0ca66d1` vs `origin/main` `258a5eb`)

### 4.1 Frontend (`frontend/src`)

- **Configuration API :** `/assets/index-CzU5S-X5.js` en production et `render.yaml:77-78` configurent `VITE_API_BASE_URL=https://ecourtier-backend.onrender.com`, lu dans `frontend/src/services/api.ts:7` via `resolveApiBaseUrl()`.
- **Client HTTP (`frontend/src/services/api.ts`) :**
  - `apiFetch` (`lignes 100-170`) envoie `credentials: 'include'`, gère `403` sans déconnecter une session IA anonyme lorsqu'un en-tête `X-IA-Session-Token` est présent (`lignes 119-127`), et gère `401` avec `withStatus(..., 401)` (`lignes 131-151`) puis lève un `Error` enrichi d'un champ `.status` pour tout `!response.ok`.
  - **Point d'attention mineur identifié :** `fetch(url, ...)` à la `ligne 100` ne pose pas de timeout client borné (en cas de cold start Render prolongé, l'utilisateur attend le timeout navigateur avant de tomber sur `network` ou `502`/`504` proxy).
- **Session anonyme (`frontend/src/services/iaSession.ts`) :**
  - Remplace la dépendance cookie `SameSite=Lax` par le jeton retourné dans le JSON de `POST /api/v1/ia/conversations` (`sessionToken`), stocké uniquement dans `sessionStorage` (`ecourtier_ia_conversation_anon` et `ecourtier_ia_session_token_<id>`) et transmis via l'en-tête HTTP `X-IA-Session-Token` (`buildConversationHeaders`).
  - Les comptes authentifiés n'émettent pas de jeton anonyme et `purgeAnonymousIaSession` isole strictement les contextes anonyme vs authentifié.
- **Widget & mapping erreurs (`AiAssistantWidget.tsx` & `iaErrors.ts`) :**
  - Restauration au montage (`useEffect`): en cas d'échec `GET /api/v1/ia/conversations/{id}` avec statut `400`, `401`, `403` ou `404` (`isAbandonedConversationStatus`), la session anonyme locale est immédiatement purgée sans boucle infinie (`1` seul `GET` en build de production `vite preview` ; notons qu'en mode développement Vite + `React.StrictMode` dans `main.tsx:7`, React exécute un double montage initial produisant 2 `GET` — sans impact en production).
  - Envoi (`handleSendMessage`) : appelle `POST /api/v1/ia/conversations` si nécessaire, persiste `conversationId` + `sessionToken`, appelle `POST /messages`, puis en cas d'exception passe par `mapIaError(err, { conversationScoped })`. Les erreurs `403`/`404` scopées réinitialisent la conversation (`resetConversation: true`) et informent l'utilisateur de renvoyer son message.
  - Badge de repli (`AiAssistantWidget.tsx:548-557`) : affiche `Mode secours local` (`data-testid="ia-fallback-badge"`) avec `title`/`aria-label` explicite généré par `describeMotifSecours(msg.motifSecours)`.

### 4.2 Backend (`backend/src`), CORS et Render (`render.yaml`, `application-prod.yml`)

- **Contrôleur & sécurité IA (`IAController.java`, `IAService.java`, `IaRateLimiterService.java`) :**
  - `POST /api/v1/ia/conversations` (`IAController:42-61`) : crée la conversation ; en mode anonyme (`IAService:209-214`), génère un jeton aléatoire côté serveur (ignorant tout en-tête client — correctif `REV-02`), stocke uniquement son empreinte SHA-256 et son expiration (`session_expiration`), et retourne `sessionToken` au client dans `ConversationCreateResponseDTO`.
  - `POST /api/v1/ia/conversations/{id}/messages` et `GET /api/v1/ia/conversations/{id}` : extraient exclusivement `X-IA-Session-Token` (`IAService:168-175`) pour les conversations anonymes (`verifierDroitsConversation`, `IAService:623-657`) et comparent le condensat en temps constant (`MessageDigest.isEqual`). Tout manque, falsification ou expiration lève `403 Forbidden` (avec motif précis JSON).
  - Limitation de débit (`IaRateLimiterService:33-37`) : `10 req/min` en anonyme, `30 req/min` en authentifié (`429 Too Many Requests`).
  - Transactions (`IAService:245-360`) : lecture courte -> appels Gemini hors transaction SQL -> écriture courte avec verrou pessimiste / séquence `ordre` (`V21` + `V22`).
- **Gemini & repli local (`GeminiLLMProvider.java`, `LlmExtractionService.java`) :**
  - Modèle par défaut `gemini-flash-lite-latest`, clé transmise via en-tête `x-goog-api-key`, timeout configurable `ecourtier.ia.gemini.timeout-ms` (`4000 ms` dans `application-prod.yml:22` et `render.yaml:48`), cooldown non bloquant sur HTTP `429`.
  - Toute défaillance Gemini (`CLE_ABSENTE`, `IA_DESACTIVEE`, `TIMEOUT`, `QUOTA`, `ERREUR_HTTP`, `REPONSE_INVALIDE`, `FOURNISSEUR_NON_AUTORISE`) déclenche la bascule propre vers `RuleBasedCriteriaExtractor` et `IAService.genererReponseConversationnelleLocale`, retournant `HTTP 200` sans jamais remonter une exception Gemini non gérée vers le client.
- **CORS (`SecurityConfig.java:128-144`, `render.yaml:49-50`) :**
  - `setAllowedOrigins(origins)` alimenté par `ECOURTIER_CORS_ALLOWED_ORIGINS` (`https://ecourtier-frontend.onrender.com` dans `render.yaml:50`), méthodes `GET, POST, PUT, DELETE, OPTIONS`, `allowedHeaders: ["*"]`, `allowCredentials: true`, `maxAge: 3600L`.
  - Reverse proxy Render (`application-prod.yml:29-35`) : `forward-headers-strategy: native`.
- **Conformité périmètre :** aucun appel Groq, OpenAI ou Claude en exécution ; aucune référence Railway ; aucune modification dans WAVE ni `domain.transaction`.

---

## 5. Résultats des vérifications passives en production

*Exécutées le 23 novembre 2026 autour de `08:51 UTC`, strictement en lecture seule (`GET`/`OPTIONS`), sans création de conversation ni écriture en base de production :*

| Cible / Requête passive | Statut / Résultat observé | Enseignement clé |
|---|---|---|
| `GET https://ecourtier-frontend.onrender.com/` | `HTTP 200`, `last-modified: Sun, 22 Nov 2026 09:46:20 UTC`, `x-content-type-options: nosniff`, **aucun header `Content-Security-Policy`** | Build antérieur à PR #4 (correspond au merge PR #3 du 22/11 `09:53 UTC`, CSP absent car ajouté seulement dans PR #4) |
| Bundle live `https://ecourtier-frontend.onrender.com/assets/index-CzU5S-X5.js` | `130 767` octets, `VITE_API_BASE_URL` compilé = `https://ecourtier-backend.onrender.com` | Pointe bien sur le backend principal (et non `-uz6e`) |
| Chunk live `https://ecourtier-frontend.onrender.com/assets/AiAssistantWidget-BiJyGg_S.js` | Contient textuellement `Désolé, l'assistant IA est actuellement indisponible ou rencontre un problème réseau. Vous pouvez utiliser la recherche classique.` ; aucune mention de `X-IA-Session-Token` ni `iaSession` | **Prouve que la UI déployée est `origin/main` (`258a5eb`)** |
| `GET https://ecourtier-backend.onrender.com/api/v1/health` | `HTTP 200` (`0.347916s`) : `{"status":"UP","environment":"prod","version":"1.0.0","timestamp":"2026-11-23T08:51:02.886595879Z","service":"E-Courtier Backend"}` | Backend principal opérationnel et base PostgreSQL saine |
| `OPTIONS https://ecourtier-backend.onrender.com/api/v1/ia/conversations` (`Origin: https://ecourtier-frontend.onrender.com`, `Access-Control-Request-Headers: Content-Type, X-IA-Session-Token`) | `HTTP/2 200`, `access-control-allow-origin: https://ecourtier-frontend.onrender.com`, `access-control-allow-credentials: true`, `access-control-allow-headers: Content-Type, X-IA-Session-Token`, `access-control-allow-methods: GET,POST,PUT,DELETE,OPTIONS`, `access-control-max-age: 3600` | **CORS totalement sain (non bloquant)** |
| `GET https://ecourtier-backend.onrender.com/openapi.json` | `230 074` octets (`origin/main` exact = `230 074` vs PR #4 = `234 102`) ; zéro occurrence de `X-IA-Session-Token`, `sessionToken`, `motifSecours`, `fournisseurIa` | **Prouve que le backend déployé est `origin/main` (`258a5eb`), avec cookie `SameSite=Lax` sans `sessionToken` JSON** |
| `https://ecourtier-backend-uz6e.onrender.com` (`/api/v1/health` & `OPTIONS`) | Timeout après `15.001s` (`HTTP_CODE=000`) | Hôte secondaire inactif/en veille, non utilisé par `VITE_API_BASE_URL` |

---

## 6. Résultats des 12 scénarios de test locaux

Trois campagnes complémentaires ont été exécutées après inspection du dépôt `/home/mahou-dev/Documents/Ecourtier2026-compliance` (`fix/render-gemini-production-compliance` `0ca66d1`) :

1. **Tests Backend JUnit / MockMvc / Intégration (`mvn test`) :** **35 tests exécutés, 0 échec, 0 erreur, 0 ignoré** (`GeminiAndDiagnosticsComplianceTest` : 8, `AnonymousConversationSecurityIntegrationTest` : 15, `IAIntegrationTest` : 3, `IAServiceClockTest` : 3, `Rev01FaqJuridiqueDiagnosticsIntegrationTest` : 6).
2. **Tests Playwright existants sur build frontend (`vite preview`) :** **`e2e/ia-unit-session-errors.spec.ts`, `e2e/ia-compliance-widget.spec.ts`, `e2e/csp-security-compliance.spec.ts` -> 8/8 réussis (`10.3s`)** (note : en mode dev `npx vite` uniquement, le `<React.StrictMode>` de `main.tsx` déclenche un double effet de montage faisant échouer l'assertion stricte `staleGetCount === 1` à `2` ; en build de production `vite preview`, le résultat est bien `1`).
3. **Banc end-to-end complet cross-site réel en local (`vite preview` sur `http://127.0.0.1:13000` -> backend Spring Boot réel sur `http://localhost:18080`, profils `dev,test` H2 avec 8 annonces seedées -> mock HTTP Gemini sur `http://127.0.0.1:19090`) :** tous les scénarios exécutés sans échec (`exit code 0`).

| # | Scénario obligatoire | Condition simulée / exécutée | Résultat observé (PR #4 `0ca66d1`) |
|---|---|---|---|
| **1** | **Création conversation anonyme** | Clic widget -> `POST http://localhost:18080/api/v1/ia/conversations` depuis origine distincte `http://127.0.0.1:13000` | **PASS** — `201 Created`, `conversationId` + `sessionToken` stockés en `sessionStorage` uniquement (absent de `localStorage`), CORS OK. |
| **2** | **Envoi message avec `X-IA-Session-Token`** | Envoi via formulaire chat -> `POST .../messages` avec header `X-IA-Session-Token` | **PASS** — En-tête `X-IA-Session-Token` transmis cross-site, validé par hash SHA-256 backend, réponse **`200 OK`** affichée. |
| **3** | **Envoi message sans jeton ni cookie** | `fetch` direct vers `POST .../messages` sur conversation anonyme sans `X-IA-Session-Token` ni cookie | **PASS** — Backend répond **`403 Forbidden`** : `{"error":"Accès refusé : preuve de possession requise pour cette conversation anonyme"}`. |
| **4** | **Rechargement F5 avec conversation persistée** | `page.reload()` sur `http://127.0.0.1:13000` puis réouverture du widget | **PASS** — `GET .../conversations/{id}` émis avec `X-IA-Session-Token` -> **`200 OK`**, historique et shortlist restaurés à l'identique. |
| **5** | **Session expirée ou révoquée (`403`)** | Envoi avec jeton corrompu en navigateur + cas réel `sessionExpiration < now` couvert par `AnonymousConversationSecurityIntegrationTest` (15 tests passés) | **PASS** — Backend répond **`403 Forbidden`**, frontend purge `sessionStorage`, réinitialise `conversationId` à `null` et affiche `IA_ERROR_MESSAGES.conversationInvalid` (*« Votre session de conversation a expiré ou n'est plus valide... »*). |
| **6** | **Backend retourne `403`** | (6a) `403` avec `conversationId` sur `/messages` réel ; (6b) `403` sur création sans `conversationId` | **PASS** — (6a) affiche `conversationInvalid` + reset ; (6b) affiche `IA_ERROR_MESSAGES.forbidden` (*« Accès refusé. »*), jamais le message réseau. |
| **7** | **Backend retourne `500`** | Réponse `HTTP 500` sur `/messages` dans le navigateur | **PASS** — UI affiche `IA_ERROR_MESSAGES.unavailable` (*« L'assistant IA est momentanément indisponible. Vous pouvez utiliser la recherche classique. »*), bien distinct de l'erreur réseau. |
| **8** | **Gemini en timeout** | Mock Gemini bloque `2000ms` face à `timeout-ms=800` | **PASS** — `GeminiLLMProvider` timeout -> `IAService` répond **`HTTP 200`**, `motifSecours: "TIMEOUT"`, badge `Mode secours local` avec infobulle *« Réponse générée par le moteur local : l'assistant avancé a mis trop de temps à répondre. »*. |
| **9** | **Gemini en quota `429`** | Mock Gemini retourne `HTTP 429` + `Retry-After: 1` (+ test UI `429` dans suite Playwright widget) | **PASS** — `IAService` bascule et répond **`HTTP 200`**, `motifSecours: "QUOTA"`, badge `Mode secours local` (*« ...la capacité de l'assistant avancé est momentanément atteinte. »*). Si rate limiter backend `429` survient, UI affiche le message quota `429`. |
| **10** | **Gemini retourne `400` ou `403`** | Mock Gemini retourne `HTTP 400`, puis `HTTP 403` | **PASS** — Les deux cas retournent **`HTTP 200`** côté API E-Courtier avec `motifSecours: "ERREUR_HTTP"` et badge `Mode secours local` (*« ...est momentanément indisponible. »*). |
| **11** | **Fallback local fonctionnel** | Requête *« Appartement à louer à Boucotte Ziguinchor budget 100000 FCFA »* pendant panne simulée Gemini | **PASS** — **`HTTP 200`**, `RuleBasedCriteriaExtractor` extrait les critères, trouve l'annonce seedée réelle vérifiée en base H2 (*« Appartement F3 Centre Boucotte »*, `95 000 FCFA`) et l'affiche en carte cliquable dans le chat. |
| **12** | **Backend totalement injoignable** | `route.abort('failed')` dans le widget + `fetch` direct vers port fermé `127.0.0.1:19999` (`TypeError: Failed to fetch`) | **PASS** — UI affiche uniquement `IA_ERROR_MESSAGES.network` (*« Connexion impossible : vérifiez votre connexion internet puis réessayez dans quelques instants. »*), distinct de tous les codes HTTP `401/403/404/429/500`. |

*(Contrôle bonus UI `HTTP 401` sur `/messages` également validé : affiche bien « Session expirée, reconnectez-vous pour continuer. »).*

---

## 7. Cause racine exacte retenue

La cause racine exacte de l'erreur observée en production est :

1. **Décalage de version déployée :** la branche `main` et l'environnement Render de production servent encore le commit `258a5eb` (PR #3), **avant** les correctifs de la PR #4 (`fix/render-gemini-production-compliance` `0ca66d1`).
2. **Dysfonctionnement combiné session anonyme + gestion d'erreur dans `origin/main` (`258a5eb`) :**
   - `IAService` (`origin/main`) émettait un cookie `ecourtier_ia_session` avec `SameSite=Lax`, rejeté/omis par les navigateurs lors des appels cross-site entre `ecourtier-frontend.onrender.com` et `ecourtier-backend.onrender.com`, déclenchant systématiquement un **`403 Forbidden`** après l'initialisation `201` de la conversation ;
   - `AiAssistantWidget.tsx` (`origin/main`) n'interceptait que `429` et `400`, transformant silencieusement ce `403` (comme tout `401`, `404` ou `5xx`) en : *« Désolé, l'assistant IA est actuellement indisponible ou rencontre un problème réseau. Vous pouvez utiliser la recherche classique. »*.

---

## 8. Plan de correction ciblé (sans dérive hors périmètre)

**Principe strict :** ne pas toucher à WAVE ni `domain.transaction`, ne pas utiliser Railway, et conserver uniquement Google Gemini (`gemini-flash-lite-latest`) + `RuleBasedCriteriaExtractor`.

1. **Action prioritaire (débloque la production) — Fusionner PR #4 et redéployer sur Render** *(hors périmètre d'exécution de la présente mission de diagnostic ; requiert autorisation explicite de fusion/déploiement)* :
   - Fusionner `fix/render-gemini-production-compliance` (`0ca66d1`) dans `main` (PR #4).
   - Déployer **`ecourtier-backend`** depuis `main` en vérifiant l'exécution automatique des migrations Flyway `V21` et `V22` et des variables : `SPRING_PROFILES_ACTIVE=prod`, `IA_ENABLED=true`, `AI_PROVIDER=gemini`, `IA_PROVIDER=gemini`, `GEMINI_MODEL=gemini-flash-lite-latest`, `GEMINI_TIMEOUT_MS=4000`, `ECOURTIER_CORS_ALLOWED_ORIGINS=https://ecourtier-frontend.onrender.com`, `GEMINI_API_KEY` présent côté Render.
   - Déployer **`ecourtier-frontend`** depuis `main` (`VITE_API_BASE_URL=https://ecourtier-backend.onrender.com`, CSP et en-têtes `render.yaml` à jour).
2. **Durcissement frontend optionnel ultérieur (non bloquant pour la correction du bug `403`) :**
   - Dans `frontend/src/services/api.ts:100`, ajouter un timeout client borné (ex. `AbortController` combiné avec un délai adapté aux cold starts Render gratuits, sans interférer avec l'annulation appelant) afin d'éviter un chargement infini lorsque le service backend Render sort de veille.
   - En option UX, distinguer côté `iaErrors.ts` les codes passerelle `502`/`503`/`504` (démarrage serveur Render en cours : *« Le serveur démarre ou est temporairement surchargé, réessayez dans 30 secondes. »*) du code `500`.

---

## 9. Vérifications à demander côté navigateur et logs Render

### A. Dans le navigateur utilisateur (DevTools -> Onglet **Network** & **Application**)

1. Ouvrir `https://ecourtier-frontend.onrender.com` en navigation privée (pour écarter tout cache navigateur/service worker).
2. Ouvrir DevTools -> **Network** -> filtrer `ia/conversations`, envoyer un message dans le chatbot et relever :
   - `POST /api/v1/ia/conversations` : statut attendu `201`, corps JSON attendu **après déploiement PR #4** : `conversationId` + `sessionToken` (**sur la prod actuelle `origin/main`, seul `conversationId` est présent et l'en-tête réponse `Set-Cookie` porte `SameSite=Lax`**).
   - `POST /api/v1/ia/conversations/{id}/messages` :
     - Si le statut est **`403`** et que Request Headers n'affiche ni `Cookie: ecourtier_ia_session=...` ni `X-IA-Session-Token` -> **confirme l'état pré-PR #4 (`SameSite=Lax` bloqué)** ;
     - Si le statut est **`(failed)` / `ERR_TIMED_OUT`** ou **`502`/`503`** pendant ~30-50s -> réveil à froid Render Free Tier ;
     - Si le statut est **`429`** -> quota IP anonyme `10 req/min` atteint ;
     - Si le statut est **`200`** -> vérifier `criteresExtraits.fournisseurIa` (`gemini` vs `fallback-local`) et `criteresExtraits.motifSecours`.
3. Onglet **Application -> Session Storage** (`https://ecourtier-frontend.onrender.com`) : après déploiement PR #4, `ecourtier_ia_conversation_anon` et `ecourtier_ia_session_token_<id>` doivent apparaître en session anonyme.

### B. Dans le dashboard Render (Service **`ecourtier-backend`**)

1. Onglet **Events** : vérifier quel commit est actuellement déployé (`258a5eb` actuellement vs commit de fusion PR #4 après déploiement) et que les déploiements frontend/backend ont réussi sans rollback.
2. Onglet **Environment** : vérifier (sans jamais afficher les valeurs secrètes) que `GEMINI_API_KEY` est défini, `IA_ENABLED=true`, `AI_PROVIDER=gemini`, `GEMINI_MODEL=gemini-flash-lite-latest`, `GEMINI_TIMEOUT_MS=4000`, `ECOURTIER_CORS_ALLOWED_ORIGINS=https://ecourtier-frontend.onrender.com`.
3. Onglet **Logs** : filtrer sur les lignes :
   - `Tentative d'accès sans jeton de session` (indique des appels `403` anonymes) ;
   - `IA_MESSAGE_PROCESSED` (affiche `provider=gemini` ou `provider=fallback-local`, `motif=...`, `totalMs=...`, `llmMs=...`) ;
   - `[VÉRIFICATION PRODUCTION]` au démarrage (vérifie l'absence d'alerte `GEMINI_API_KEY absente`) et logs Flyway (`V21`, `V22` appliquées).

---

## 10. Risques résiduels

- **Latence de sortie de veille (Render Free Plan) :** après inactivité, le premier réveil d'un service web gratuit Docker Spring Boot prend plusieurs dizaines de secondes ; tant que le conteneur n'est pas actif, le client sans timeout dédié peut atteindre un timeout réseau navigateur ou recevoir un `502`/`503` du proxy Render.
- **Limitation par IP partagée (`10 req/min` anonyme) :** derrière un même NAT d'école, d'entreprise ou d'opérateur mobile, plusieurs visiteurs anonymes partageant la même IP publique peuvent déclencher HTTP `429`.
- **Cache client transitoire lors d'un futur déploiement :** un onglet laissé ouvert sur l'ancien bundle JS pendant le redéploiement frontend nécessitera un rafraîchissement complet pour charger le client `X-IA-Session-Token`.
- **Service secondaire `ecourtier-backend-uz6e.onrender.com` :** ce nom d'hôte ancien est injoignable (timeout) ; il reste autorisé dans la CSP de `render.yaml` (`PR #4`), mais n'est pas utilisé si `VITE_API_BASE_URL` pointe sur `https://ecourtier-backend.onrender.com`.

---

## 11. Verdict final obligatoire

**`CAUSE IDENTIFIÉE MAIS CORRECTION NON APPLIQUÉE`**

> **Précision sur l'état du dépôt :** La cause en production (`origin/main` `258a5eb` déployé sur Render : cookie cross-site `SameSite=Lax` bloqué provoquant un `403`, masqué en erreur réseau par `AiAssistantWidget.tsx`) est **prouvée et reproductible**. La correction complète (`X-IA-Session-Token`, `sessionStorage`, `iaErrors.ts`) **existe déjà et passe l'ensemble des tests locaux et des 12 scénarios sur la branche PR #4 (`fix/render-gemini-production-compliance`, commit `0ca66d1`)**, mais **n'est pas encore appliquée sur `main` ni en production Render** (hors autorisation de fusion/déploiement de la présente mission).
