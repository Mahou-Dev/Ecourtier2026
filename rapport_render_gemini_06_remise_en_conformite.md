# RAPPORT TECHNIQUE `RENDER-GEMINI-06` — REMISE EN CONFORMITÉ INTÉGRALE ET VALIDATION TECHNIQUE D'E-COURTIER SÉNÉGAL

**Date d'exécution :** 22 novembre 2026  
**Mission :** `RENDER-GEMINI-06` — Diagnostic, correction de bout en bout, tests et préparation à la livraison des anomalies relevées dans [`rapport_render_gemini_04_test_reel_agent_ia.md`](../Ecourtier2026/rapport_render_gemini_04_test_reel_agent_ia.md)  
**Périmètre traité :** Backend Java 21 / Spring Boot 3.4.3 / Flyway PostgreSQL 16 + Frontend React / TypeScript / Vite + Contrat OpenAPI 3.0.1 + Documentation  
**Exclusion stricte respectée :** Module WAVE, paiements séquestre live et environnement de production Render **non modifiés et non appelés** (`git diff -- '*wave*' '*Wave*'` vide).

---

## A. Synthèse exécutive

L'audit de production du 22 novembre 2026 (`rapport_render_gemini_04_test_reel_agent_ia.md`) avait établi une note de **48 / 100**, deux anomalies bloquantes **P0** (`ANO-PROD-01` : blocage `HTTP 403` de 100 % des visiteurs anonymes cross-origin ; `ANO-PROD-02` : `13/13` réponses IA générées par `fallback-local` sans preuve d'exécution effective de Google Gemini), quatre anomalies majeures **P1** (`ANO-PROD-03` comparatifs NLU, `ANO-PROD-04` inversion chronologique après `F5`, `ANO-PROD-05` transparence juridique / titres fonciers `IA-10`, `ANO-PROD-06` catalogue vérifié vide en production), quatre anomalies **P2** (`ANO-PROD-07` troncature à 500 caractères, `ANO-PROD-08` pertes NLU / préférence meublé stricte, `ANO-PROD-09` réponses négatives prématurées / relances rigides, `ANO-PROD-10` latences élevées sans télémétrie) et une anomalie **P3** (`ANO-PROD-11` ergonomie des erreurs `403`/`400`/`500` et formatage FCFA).

À l'issue de la remise en conformité réalisée dans le worktree isolé `/home/mahou-dev/Documents/Ecourtier2026-compliance` sur la branche dédiée `fix/render-gemini-production-compliance` (commit `b5123db1c8fb57fd62b9b6be1527422d97c3d33b`) :

- **9 anomalies sur 11** (`ANO-PROD-01`, `03`, `04`, `05`, `07`, `08`, `09`, `11` + requêtage SQL / télémétrie `ANO-PROD-10`) sont **entièrement corrigées dans le code et validées par tests automatisés**.
- **2 points dépendent encore d'une action opérationnelle en production Render** (hors périmètre exécutable localement sans modifier la production) :
  - **`ANO-PROD-02` :** le code Gemini (`gemini-flash-lite-latest`), les codes diagnostiques non sensibles (`motifSecours`) et l'orchestration exclusive ont été corrigés et vérifiés sur serveur HTTP mock local ; la confirmation sur `ecourtier-backend.onrender.com` requiert la vérification de la variable `GEMINI_API_KEY` sur ce service et un smoke-test post-déploiement.
  - **`ANO-PROD-06` :** le requêtage et le classement sur catalogue mixte ont été corrigés et prouvés sur données synthétiques ; le peuplement et la validation administrative d'annonces réelles dans la base PostgreSQL de production restent à effectuer par l'administrateur.

---

## B. Audit Git, isolation de l'espace de travail et exclusion WAVE

| Élément vérifié | Valeur observée |
| :--- | :--- |
| Dépôt préexistant (intact) | `/home/mahou-dev/Documents/Ecourtier2026` — branche `feature/frontend-redesign-premium-saas` (`68ba303`), aucune modification effectuée par cette mission |
| Worktree isolé dédié | `/home/mahou-dev/Documents/Ecourtier2026-compliance` |
| Branche de travail | `fix/render-gemini-production-compliance` (créée depuis `origin/main` `258a5eb`) |
| Commit préparé | `b5123db1c8fb57fd62b9b6be1527422d97c3d33b` (`37` fichiers modifiés/créés, `3402` insertions, `427` suppressions) |
| Fichiers WAVE modifiés | `0` (`git diff -- '*wave*' '*Wave*'` vide) |
| Fichiers sensibles suivis / fuites secrets | `0` (`git ls-files` + `git grep` conformes au pipeline CI ; `0` occurrence `AIza` dans `frontend/dist/`) |

---

## C. `ANO-PROD-01` — Authentification anonyme cross-site via `X-IA-Session-Token` et isolation multi-sessions

### Cause racine confirmée
Le frontend (`ecourtier-frontend.onrender.com`) et le backend (`ecourtier-backend.onrender.com`) résident sur des sous-domaines distincts d'un suffixe public (`.onrender.com`). Le cookie `ecourtier_ia_session` émis en `SameSite=Lax` était écarté par les navigateurs sur les appels `fetch` cross-origin, provoquant systématiquement `HTTP 403` dès le premier message anonyme.

### Remédiations appliquées
1. **Backend (`IAService.java`, `IAController.java`, `ConversationCreateResponseDTO.java`) :**
   - Suppression complète de l'émission et de la lecture de cookie `ecourtier_ia_session` pour l'assistant IA.
   - À la création d'une conversation **anonyme uniquement**, `ConversationCreateResponseDTO` retourne le champ `sessionToken` (`@JsonInclude(NON_NULL)`), tandis que seul son condensat SHA-256 (`sessionTokenHash`) est persisté en base avec expiration en heure `Africa/Dakar` (`+7 jours`).
   - Les appels ultérieurs (`POST /api/v1/ia/conversations/{id}/messages` et `GET /api/v1/ia/conversations/{id}`) valident la preuve anonyme exclusivement via l'en-tête HTTP `X-IA-Session-Token` (comparaison en temps constant `MessageDigest.isEqual`).
   - Suppression de la réassignation silencieuse d'une conversation anonyme à un compte authentifié lors d'un appel ultérieur.
   - En session **authentifiée**, `sessionToken` vaut `null` (jamais émis) et seul le propriétaire (`conversation.utilisateur.email`) est autorisé.
   - Documentation explicite du header `X-IA-Session-Token` dans Swagger/OpenAPI (`IAController.java`, `openapi.json`).
2. **Frontend (`frontend/src/services/iaSession.ts`, `frontend/src/services/api.ts`, `frontend/src/store/useAuthStore.ts`, `frontend/src/components/ai/AiAssistantWidget.tsx`) :**
   - Création du module pur `iaSession.ts` : pour `anon`, `conversationId` et `sessionToken` sont persistés exclusivement en `sessionStorage` (`ecourtier_ia_conversation_anon` et `ecourtier_ia_session_token_<id>`), jamais en `localStorage` et jamais en paramètre d'URL.
   - Tout ancien `ecourtier_ia_conversation_anon` hérité en `localStorage` sans jeton est purgé automatiquement au chargement.
   - `buildConversationHeaders(userId, conversationId)` attache `X-IA-Session-Token` uniquement pour l'utilisateur anonyme ; l'utilisateur connecté transmet exclusivement son `Authorization: Bearer`.
   - `api.ts` préserve `err.status` sur les réponses d'erreur et empêche qu'un `401`/`403` sur une route IA anonyme ne déclenche un `logout()` global.
   - `useAuthStore.ts` appelle `purgeAnonymousIaSession()` dans `clearClientSessionData()` et `setAuth()` afin qu'une session anonyme et une session authentifiée ne se contaminent jamais.

### Preuves de validation
- `AnonymousConversationSecurityIntegrationTest` (`14/14` passés, incluant le preflight CORS `OPTIONS` depuis `https://ecourtier-frontend.onrender.com` autorisant `X-IA-Session-Token` et la vérification d'absence totale du secret et du hash dans les corps JSON GET/POST).
- `ia-unit-session-errors.spec.ts` + `ia-compliance-widget.spec.ts` (cycle anonyme complet, F5, reset, isolation JWT).
- Test cross-origin sur serveur Spring Boot réel isolé (`http://127.0.0.1:4173` -> `http://127.0.0.1:18080`) : `OPTIONS` (`200`), `GET` sans jeton (`403`), `POST` 2 messages avec `X-IA-Session-Token` (`200`), `GET` historique (`200`), et Playwright `phase3-real-backend.spec.ts -g "A1/IA"` (`1/1` passé).

---

## D. `ANO-PROD-02` — Architecture Google Gemini exclusive (`gemini-flash-lite-latest`) & diagnostics non sensibles

### Remédiations appliquées
1. **Rappel strict de la politique LLM :** seul Google Gemini (`gemini-flash-lite-latest`, configuré côté backend via `GEMINI_API_KEY`) est autorisé comme fournisseur externe, avec `RuleBasedCriteriaExtractor` comme unique mécanisme local de secours. Groq, OpenAI et Claude restent totalement inactifs et rejetés à l'exécution (`FOURNISSEUR_NON_AUTORISE`).
2. **Diagnostic structuré non sensible (`CriteresExtraitsDTO.motifSecours`, `LlmExtractionService.java`, `GeminiLLMProvider.java`, `IAService.java`) :**
   - Ajout de `GenerationOutcome` et `generateConversationalResponseWithDiagnostic` permettant de conserver la trace exacte de la bascule en secours local (`CLE_ABSENTE`, `IA_DESACTIVEE`, `FOURNISSEUR_NON_AUTORISE`, `TIMEOUT`, `QUOTA`, `ERREUR_HTTP`, `REPONSE_INVALIDE`) sans jamais exposer `GEMINI_API_KEY`.
   - Validation du comportement sur `Retry-After` en HTTP `429` (`QUOTA`, activation d'un cooldown évitant de marteler l'API).
   - Aucune fuite du motif brut interne côté utilisateur final : `describeMotifSecours(motif)` dans `iaErrors.ts` affiche une infobulle lisible et neutre sur `[data-testid="ia-fallback-badge"]` uniquement quand `modeSecours === true`.
3. **Statut honnête Gemini réel vs simulé :**
   - **SIMULÉ (serveur HTTP local `127.0.0.1`) :** `GeminiAndDiagnosticsComplianceTest` et `LlmExtractionMockServerTest` valident le nominal HTTP `200`, l'envoi exclusif de la clé dans l'en-tête `x-goog-api-key`, l'absence d'appel à `GroqClientService`/`OpenAILLMProvider`/`ClaudeLLMProvider` (`verifyNoInteractions`), et les 7 codes `motifSecours` (`CLE_ABSENTE`, `IA_DESACTIVEE`, `FOURNISSEUR_NON_AUTORISE`, `QUOTA`, `ERREUR_HTTP` 400/401/403/404/500/503, `TIMEOUT`, `REPONSE_INVALIDE`).
   - **NON EXÉCUTÉ EN PRODUCTION :** aucun appel réel à Google Gemini ni inspection des variables secrètes Render n'a été effectué.

---

## E. `ANO-PROD-04` — Ordre chronologique déterministe après rechargement (`F5`)

### Cause racine confirmée
`IAService.envoyerMessage()` persistait les messages `UTILISATEUR` et `ASSISTANT` avec le même `LocalDateTime now`, et `MessageIARepository` triait uniquement par `dateEnvoi ASC`. À horodatage égal, PostgreSQL retournait parfois la réponse `ASSISTANT` avant la question `UTILISATEUR` après `F5`.

### Remédiations appliquées
1. **Migration Flyway `V21__add_message_sequence_order_to_messages_ia.sql` :**
   - Ajout de la colonne `ordre BIGINT NOT NULL DEFAULT 0`.
   - Backfill déterministe des données existantes via `ROW_NUMBER() OVER (PARTITION BY conversation_id ORDER BY date_envoi ASC, CASE WHEN role = 'UTILISATEUR' THEN 0 ELSE 1 END ASC, id ASC)`.
   - Création de l'index composite `idx_messages_ia_conv_ordre (conversation_id, ordre, date_envoi, id)`.
2. **Backend (`MessageIAEntity.java`, `ConversationIAEntity.java`, `MessageIARepository.java`, `IAService.java`, `MessageItemDTO.java`) :**
   - Attribution atomique de `ordre = maxOrdre + 1` au message `UTILISATEUR` et `maxOrdre + 2` au message `ASSISTANT`, sans altérer `dateEnvoi` (préservant intégralement `IAServiceClockTest`).
   - Exposition du champ `ordre` dans `MessageItemDTO` et tri systématique par `ORDER BY m.ordre ASC, m.dateEnvoi ASC, m.id ASC`.
3. **Frontend (`iaErrors.ts`, `AiAssistantWidget.tsx`) :**
   - Fonction pure `orderHistoryMessages` triant de façon stable par `ordre` croissant lorsque tous les messages en disposent, tout en conservant l'ordre serveur brut en mode dégradé.

### Preuves de validation
- `ComplianceAno03To11IntegrationTest.testAno04_OrdreChronologiqueDeterministeSousHorlogeFigee` et `testAno04_CompatibiliteLignesHeriteesOrdreZero`.
- `FlywayFreshMigrationIntegrationTest` + `Postgres16Phase3IntegrationTest` exécutés sur **PostgreSQL `16.15` réel via Testcontainers** (`5/5` passés, validation Hibernate `ddl-auto=validate` OK).
- `ia-compliance-widget.spec.ts` Test 1 (réponses désordonnées côté JSON réordonnées `1 -> 2 -> 3 -> 4` après `F5`).

---

## F. `ANO-PROD-03`, `ANO-PROD-05`, `ANO-PROD-08`, `ANO-PROD-09` — NLU, comparatifs, transparence juridique et préférences

### Remédiations appliquées
1. **`ANO-PROD-03` (`IA-03` comparatifs & négations — `RuleBasedCriteriaExtractor.java`, `GeminiLLMProvider.java`, `LlmExtractionService.java`) :**
   - Réécriture de `extractTypeBien()` pour évaluer toutes les mentions et leur contexte gauche/droite (exclusion de *"plutôt qu'un(e)"*, *"au lieu d'un(e)"*, *"à la place d'"*, *"et non"*, *"non un(e)"*, *"pas un(e)/de"*, *"remplacer X par Y"*, *"je préfère X à Y"*).
   - Correction identique sur `extractModeTransaction()` (*"acheter plutôt que louer"* -> `VENTE`).
   - En enrichissement hybride (`LlmExtractionService`), le type et le mode de transaction extraits par Gemini ne sont pas écrasés par un motif regex local ambigu.
2. **`ANO-PROD-05` (`IA-10` vérification juridique et anti-arnaque — `RuleBasedCriteriaExtractor.java`, `IAService.java`, `GeminiLLMProvider.java`) :**
   - Détection dédiée de l'intention `FAQ_VERIFICATION_JURIDIQUE` (exécution sur texte normalisé sans accent afin d'éviter les échecs de `\b` Java sur caractères accentués).
   - Garde explicite empêchant le déroutement de `IA-08` (*"Je cherche une maison fiable et sécurisée à louer à Dakar"*) vers la FAQ.
   - Conservation intégrale des critères précédents (`preserveCriteriaForLegalFaq`) lorsqu'une question de confiance survient au milieu d'une recherche.
   - Réponse transparente couvrant les 7 axes métier (statut `VERIFIEE` requis, contrôle d'identité/justificatifs, pièces usuelles au Sénégal, visite physique préalable avant tout paiement, traçabilité plateforme, limites honnêtes et rôle du notaire pour un achat).
3. **`ANO-PROD-08` & `ANO-PROD-09` (`Preferences souples`, attentes complémentaires & guidage progressif) :**
   - Ajout de `preferencesSouples`, `attentesComplementaires` et `precisionsAClarifier` dans `CriteresExtraitsDTO`, `RuleBasedCriteriaExtractor`, `GeminiLLMProvider` et `IAService`.
   - Exclusion explicite de *"cuisine équipée"* du déclenchement `meuble = true`.
   - Traitement de *"je préférerais ... meublé"* en préférence souple (`meuble = null` non éliminatoire, bonus de pertinence `+15` dans `IAService.calculerScorePertinence()`), prise en compte de `"quartier calme"`, `"cour"`, `"cuisine"`, `"jardin"`, `"grand terrain"`, `"balcon/terrasse"`, et intégration dans le récapitulatif textuel (y compris équipements et proximités).
   - Suppression du message négatif prématuré *"Malheureusement, aucune annonce ne correspond..."* sur les demandes purement exploratoires/indécises (`demandeExploratoire`), avec relance ciblée et `precisionsAClarifier` cohérent.

### Preuves de validation
- `ComplianceAno03To11IntegrationTest` (`testAno05`, `testAno09`, `testAno08Et11`, `testAno08_DemandeExploratoireSansEchecPremature`) + `RuleBasedCriteriaExtractorTest` (`10/10`) + `IAServiceMultiTourAno07IntegrationTest` (`5/5`) + `AssistantIaRemediationComprehensiveTest`.

---

## G. `ANO-PROD-06`, `ANO-PROD-10`, `ANO-PROD-11` — Catalogue, télémétrie et lisibilité UI

1. **Catalogue (`AnnonceRepository.java`, `IAService.java`) :** remplacement de `annonceRepository.findAll()` dans le moteur de recommandation par une requête filtrée en base avec `@EntityGraph(attributePaths = {"utilisateur"})` (`findBySupprimeFalseAndStatutPublicationAndStatutVerification(DISPONIBLE, VERIFIEE)`), couplée à un filtre défensif et au masquage PII (`sanitizeAnnonceForAssistant`). Validé sur jeu synthétique mixte (`VERIFIEE+DISPONIBLE`, `REJETEE`, `EN_ATTENTE`, `LOUEE`). **En production Render (`ANO-PROD-06`), aucune donnée n'a été modifiée : l'administration doit publier/valider des annonces réelles.**
2. **Télémétrie (`MessageIaResponseDTO.java`, `IAService.java`, `AiAssistantWidget.tsx`) :** ajout de `IaMetriquesDTO` (`extractMs`, `searchMs`, `generationMs`, `llmMs`, `totalMs`) et pose des attributs DOM `data-ui-total-ms`, `data-total-ms`, `data-network-ms` sans surcharge visuelle.
3. **Ergonomie erreurs & FCFA (`iaErrors.ts`, `IAService.java`) :** distinction claire des erreurs `400` sécurité, `400` longueur, `401`, `403` conversation invalide, `404`, `429`, `5xx` et perte réseau ; formatage FCFA lisible côté backend (`300 000 FCFA`) et frontend (`formatFcfa` -> `300\u00a0000\u00a0FCFA`).

---

## H. `ANO-PROD-07` — Saisie jusqu'à 1 000 caractères et comportement mobile

- Backend (`MessageUserRequestDTO.java`, `RuleBasedCriteriaExtractor.java`, `IAService.java`) : plafond porté à `1 000` caractères (`> 1000` rejeté en `HTTP 400`, `1000` accepté).
- Frontend (`AiAssistantWidget.tsx`, `iaErrors.ts`) : `<textarea data-testid="ia-input">` auto-extensible (`maxHeight` ~4 lignes) avec `maxLength={1000}`, compteur `charCount/1000` (`[data-testid="ia-char-counter"]`), alerte `aria-live="polite"` à partir de `900`, indicateur explicite à `1000` ou en cas de collage tronqué (`[data-testid="ia-char-limit-message"]`), soumission `Enter` et saut de ligne `Shift+Enter`, rendu vérifié en viewport mobile `375x812` sans débordement horizontal.

---

## I. Synthèse des validations exécutées et limites honnêtes

| Suite / Vérification | Environnement d'exécution | Résultat exact observé | Statut |
| :--- | :--- | :--- | :--- |
| Suite backend complète + JaCoCo (`mvn -B test jacoco:check -Djacoco.skip=false`) | H2 mode PostgreSQL (JVM Java 21) | `434` tests exécutés (`5` PG16 skippés hors flag), **`0` échec, `0` erreur** ; couverture globale lignes **`82,11 %` (`5798/7061`)**, branches **`60,26 %` (`3246/5387`)**, package `domain.ia` **`80,02 %` (`1922/2402`)** (`IAService` `86,01 %`, `RuleBasedCriteriaExtractor` `86,46 %`, `GeminiLLMProvider` `84,04 %`, `LlmExtractionService` `68,64 %`, `IAController` `100,00 %`, `ProductionConfigurationValidator` `92,04 %`) | **TEST RÉUSSI** |
| Migrations Flyway `V1..V21` + Hibernate `validate` (`-Decourtier.test.pg16=true`) | **PostgreSQL `16.15` réel (Testcontainers Docker)** | `FlywayFreshMigrationIntegrationTest` + `Postgres16Phase3IntegrationTest` : **`5 / 5` passés (`0` échec)** | **TEST RÉUSSI** |
| Tests ciblés IA, Sécurité, OpenAPI & Config (`com.ecourtier.domain.ia.**`, `OpenApiContractIntegrationTest`) | H2 + MockMvc + `HttpServer` local + Mockito | **`121 / 121` actifs passés (`0` échec, `1` PG16 skippé)** | **TEST RÉUSSI** (Gemini simulé en HTTP local) |
| Frontend qualité (`npm run lint`, `tsc && vite build`, grep `AIza` dans `dist/`) | Node 20 / Linux | `0` erreur, `0` avertissement ESLint, build Vite + PWA `63` entrées, `0` clé API dans `dist/` | **TEST RÉUSSI** |
| Playwright Unitaire + Widget Compliance (`ia-unit-session-errors`, `ia-compliance-widget`, `chatbot-groq-widget`, `chatbot-groq-r1-race`) | Vite preview `127.0.0.1:3100` (API routées Playwright) | **`19 / 19` passés (`44.1s`)** | **TEST RÉUSSI (API simulées)** |
| Cross-origin Full-Stack isolé (`127.0.0.1:4173` -> Spring Boot réel `127.0.0.1:18080`, profil `test`) | Backend Spring Boot réel + Frontend Vite + Chromium Playwright | `OPTIONS` preflight=`200`, `GET` sans token=`403`, `POST` msg1=`200`, `POST` msg2=`200`, `GET` avec `X-IA-Session-Token`=`200`, `phase3-real-backend.spec.ts -g "A1/IA"` **`1 / 1` passé (`8.4s`)** | **TEST RÉUSSI** |
| Précontrôles CI & Docker Compose | Git / `docs/openapi.json` / Docker Compose CLI | `OPENAPI_SYNC_OK`, `FLYWAY_PREFIX_OK: 21 files`, `CI_SECRET_SCAN_PASS`, `DOCKER_COMPOSE_OK` (`dev` & `prod`) | **TEST RÉUSSI** |
| `chatbot-groq-fullstack.spec.ts` (ports codés `8089`/`5173` + annonces seedées) | Non lancé (mis à jour pour `X-IA-Session-Token` et `[data-testid="ia-input"]`) | Remplacé opérationnellement par le test cross-origin réel sur `18080`/`4173` pour éviter toute interférence avec l'environnement `8080`/`3000` | **NON EXÉCUTÉ TEL QUEL** |
| Appels live Google Gemini et catalogue réel Render (`ecourtier-backend.onrender.com`) | Production Render (non modifiée) | Interdit/hors périmètre local ; requiert déploiement + vérification de `GEMINI_API_KEY` + seed annonces en production | **NON EXÉCUTÉ (PRODUCTION)** |

---

## J. Checklist opérationnelle de mise en conformité sur Render (post-livraison)

1. Créer une Pull Request depuis `fix/render-gemini-production-compliance` (commit `b5123db`) vers `main` et attendre le passage complet du pipeline CI GitHub Actions (ne jamais fusionner tant que la revue humaine et la CI ne sont pas vertes).
2. Dans le tableau de bord Render du service exact appelé par le frontend (**`ecourtier-backend`** — et vérifier que l'ancien service **`ecourtier-backend-uz6e`** n'entretient aucune confusion de configuration) :
   - Confirmer `IA_ENABLED=true`, `AI_PROVIDER=gemini`, `IA_PROVIDER=gemini`, `GEMINI_MODEL=gemini-flash-lite-latest`, `ECOURTIER_CORS_ALLOWED_ORIGINS=https://ecourtier-frontend.onrender.com`.
   - Vérifier que **`GEMINI_API_KEY`** est renseignée avec une clé Google Gemini valide et non restreinte par quota/facturation.
3. Déployer le backend (Flyway appliquera automatiquement `V21__add_message_sequence_order_to_messages_ia.sql` sur PostgreSQL 16) puis déployer le frontend statique (`VITE_API_BASE_URL=https://ecourtier-backend.onrender.com`).
4. **Catalogue (`ANO-PROD-06`) :** depuis le back-office administrateur en production, vérifier/publier au moins `3` à `5` annonces réelles `VERIFIEE + DISPONIBLE` (ex. Dakar, Ziguinchor, Saly).
5. **Smoke-test post-déploiement Render :**
   - En navigation privée (anonyme), envoyer un message de `> 500` caractères (`~600` car.) -> attendre `HTTP 200`, `"fournisseurIa": "gemini"`, `"modeSecours": false`, puis recharger la page (`F5`) -> historique affiché dans l'ordre `[UTILISATEUR, ASSISTANT]`.
   - Tester `"un appartement plutôt qu'une villa"` (`IA-03`), une question juridique/fiabilité (`IA-10`), et consulter les logs Render `IA_MESSAGE_PROCESSED` en cas d'apparition du motif de secours.

---

## K. Matrice complète de résolution `ANO-PROD-01` .. `ANO-PROD-11`

| ID | Gravité initiale | Problème initial (`rapport_04`) | Fichiers corrigés / créés | Preuve / Test associé | Statut final |
| :--- | :---: | :--- | :--- | :--- | :--- |
| **`ANO-PROD-01`** | **P0** | Cookie `SameSite=Lax` bloqué cross-site -> `403` sur 100 % des visiteurs anonymes | `IAService.java`, `IAController.java`, `ConversationCreateResponseDTO.java`, `iaSession.ts`, `api.ts`, `useAuthStore.ts`, `AiAssistantWidget.tsx`, `types/index.ts`, `openapi.json` | `AnonymousConversationSecurityIntegrationTest` (`14/14`), `ia-compliance-widget.spec.ts`, smoke réel `18080`/`4173` | **CORRIGÉ & VALIDÉ LOCALEMENT** |
| **`ANO-PROD-02`** | **P0** | `13/13` réponses IA en `fallback-local`, aucune exécution Gemini constatée en prod | `GeminiLLMProvider.java`, `LlmExtractionService.java`, `CriteresExtraitsDTO.java`, `IAService.java`, `iaErrors.ts`, `AiAssistantWidget.tsx` | `GeminiAndDiagnosticsComplianceTest`, `LlmExtractionMockServerTest`, `LlmExtractionServiceTest` | **CODE CORRIGÉ (SIMULÉ) — CLÉ RENDER À VÉRIFIER EN PROD** |
| **`ANO-PROD-03`** | **P1** | Comparatifs (`"appartement plutôt qu'une maison"`) mal extraits (`VILLA`) | `RuleBasedCriteriaExtractor.java`, `GeminiLLMProvider.java`, `LlmExtractionService.java` | `ComplianceAno03To11IntegrationTest.testAno05_*` | **CORRIGÉ & VALIDÉ** |
| **`ANO-PROD-04`** | **P1** | Inversion `[ASSISTANT, UTILISATEUR]` après `F5` à horodatage égal | `V21__add_message_sequence_order_to_messages_ia.sql`, `MessageIAEntity.java`, `ConversationIAEntity.java`, `MessageIARepository.java`, `IAService.java`, `MessageItemDTO.java`, `iaErrors.ts`, `AiAssistantWidget.tsx` | `ComplianceAno03To11IntegrationTest.testAno04_*`, PG16 Testcontainers (`V1..V21`), `ia-compliance-widget.spec.ts` | **CORRIGÉ & VALIDÉ (H2 + PG16)** |
| **`ANO-PROD-05`** | **P1** | Réponse hors cible sur honnêteté propriétaire / titre foncier (`IA-10`) | `RuleBasedCriteriaExtractor.java`, `GeminiLLMProvider.java`, `IAService.java` | `ComplianceAno03To11IntegrationTest.testAno09_*` | **CORRIGÉ & VALIDÉ** |
| **`ANO-PROD-06`** | **P1** | `0` annonce vérifiée en production (bloquant pour la recommandation réelle) | `AnnonceRepository.java`, `IAService.java` | `ComplianceAno03To11IntegrationTest.testAno08Et11_*` | **REQUÊTE CORRIGÉE & TESTÉE — SEED PROD REQUIS** |
| **`ANO-PROD-07`** | **P2** | Troncature silencieuse à `500` caractères | `MessageUserRequestDTO.java`, `RuleBasedCriteriaExtractor.java`, `IAService.java`, `AiAssistantWidget.tsx`, `iaErrors.ts` | `IAIntegrationTest`, `RuleBasedCriteriaExtractorTest`, `ChatbotFunctionalAndSecurityTest`, `ia-compliance-widget.spec.ts` | **CORRIGÉ & VALIDÉ** |
| **`ANO-PROD-08`** | **P2** | Perte `"cour"`, `"cuisine"`, `"terrain"`, `"calme"` ; `"meublé"` trop strict ; résumé incomplet | `CriteresExtraitsDTO.java`, `RuleBasedCriteriaExtractor.java`, `GeminiLLMProvider.java`, `LlmExtractionService.java`, `IAService.java` | `ComplianceAno03To11IntegrationTest.testAno08Et11_*` | **CORRIGÉ & VALIDÉ** |
| **`ANO-PROD-09`** | **P2** | Refus prématuré *"aucune annonce"* sans budget/type (`IA-04/05`) ; relances rigides | `CriteresExtraitsDTO.java`, `RuleBasedCriteriaExtractor.java`, `GeminiLLMProvider.java`, `IAService.java` | `ComplianceAno03To11IntegrationTest.testAno08_DemandeExploratoireSansEchecPremature` | **CORRIGÉ & VALIDÉ** |
| **`ANO-PROD-10`** | **P2** | Latence élevée sans visibilité serveur/client ; `findAll()` sur les annonces | `AnnonceRepository.java`, `MessageIaResponseDTO.java`, `IAService.java`, `AiAssistantWidget.tsx`, `types/index.ts` | `ComplianceAno03To11IntegrationTest`, `ia-compliance-widget.spec.ts` (`metriques` + `data-ui-total-ms`) | **TÉLÉMÉTRIE & SQL CORRIGÉS — COLD START RENDER À OBSERVER EN PROD** |
| **`ANO-PROD-11`** | **P3** | Erreur UI *"problème réseau"* trompeuse sur `403`, FCFA non formaté, `403` POST non réinitialisé | `iaErrors.ts`, `api.ts`, `IAService.java`, `AiAssistantWidget.tsx` | `ia-unit-session-errors.spec.ts`, `ia-compliance-widget.spec.ts`, `ComplianceAno03To11IntegrationTest` | **CORRIGÉ & VALIDÉ** |
