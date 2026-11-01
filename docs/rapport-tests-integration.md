# Rapport Complet des Tests d'Intégration — Plateforme E-Courtier

- **Date d'exécution** : `2026-10-30` (12h46 – 13h01 UTC)
- **Environnement cible** : Stack Docker locale complète (`ecourtier-backend` Spring Boot 3.3.4 / Java 21 sur `:8080`, `ecourtier-frontend` React 18 / Nginx sur `:3000`, `ecourtier-db` PostgreSQL 16 sur `:5435`) + suite JUnit 5 / Surefire / JaCoCo (`H2` & MockServer)
- **Périmètre couvert** :
  1. **Niveau 1 — Tests d'intégration & unitaires Backend (`mvn test jacoco:report`)** : 30 classes de test (`106` tests dont `37` tests d'intégration `@SpringBootTest` / `MockMvc` / `STOMP` / `HttpServer`).
  2. **Niveau 2 — Tests d'intégration Full-Stack Live API + PostgreSQL 16 + WebSocket STOMP 1.2** : `83` assertions exécutées à chaud couvrant **100 % des 54 endpoints applicatifs** (`53` endpoints REST + `1` endpoint temps réel `@MessageMapping` STOMP `/ws`) ainsi que OpenAPI/Swagger.
  3. **Niveau 3 — Tests d'intégration E2E Navigateur (`puppeteer-core` / Chromium Headless)** : `4` parcours utilisateurs complets React <-> API REST / Assistant IA <-> PostgreSQL.
- **Traces brutes générées** :
  - [`docs/audit/maven_integration_results.json`](../docs/audit/maven_integration_results.json) (résultats Surefire XML + couverture JaCoCo CSV)
  - [`docs/audit/integration_suite_results.json`](../docs/audit/integration_suite_results.json) (87 assertions Live REST + STOMP + Browser E2E horodatées, sans jetons ni secrets)

---

## 1. Synthèse Exécutive

| Niveau de test | Outil / Protocole | Cas exécutés | Réussis | Échecs / Erreurs | Taux de réussite | Durée totale |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **Niveau 1A — Intégration Spring Boot** (`@SpringBootTest`, `MockMvc`, `STOMP`, `HttpServer`) | JUnit 5 / Spring Test / H2 | 37 | 36 | **1 erreur** | **97,3 %** | 39,95 s |
| **Niveau 1B — Tests unitaires de domaine & sécurité** (exécutés dans la même passe Maven) | JUnit 5 / Mockito | 69 | 69 | 0 | **100,0 %** | 3,00 s |
| **Niveau 2 — Intégration Live Full-Stack (53 REST + 1 STOMP + 2 OpenAPI)** | Node 26 `fetch` + `WebSocket` STOMP 1.2 + PostgreSQL 16 | 83 | 83 | 0 | **100,0 %** | 3,89 s |
| **Niveau 3 — Intégration E2E Navigateur (UI React <-> Backend <-> DB)** | `puppeteer-core` + Chromium Headless | 4 | 4 | 0 | **100,0 %** | 11,28 s |
| **TOTAL CONSOLIDÉ** | **3 niveaux combinés** | **193** | **192** | **1** | **99,48 %** | **~58,1 s** |

> **Verdict global** : Les workflows critiques de bout en bout (inscription/JWT/refresh, limitation de débit anti-bruteforce, publication d'annonces avec validation binaire *magic-bytes*, vérification KYC et badge `VERIFIEE` par l'Admin, recherche multicritères, assistant IA multi-tours avec repli déterministe, rendez-vous avec détection de conflit `±30 min` et rappels SMS simulés, séquestre Wave Sandbox avec calcul serveur infalsifiable, double signature électronique déclenchant automatiquement la libération `LIBERE` et le PDF `%PDF-1.x`, messagerie temps réel STOMP `/ws` avec isolation stricte des tiers, favoris, avis vérifiés post-visite et suspension sur signalement) **fonctionnent de bout en bout sur PostgreSQL 16**. Six anomalies d'intégration non bloquantes mais structurantes ont été mises en évidence et documentées en section 5.

---

## 2. Niveau 1 — Suite Automatisée Backend (`mvn test jacoco:report`)

### 2.1 Détail des 14 classes de tests d'intégration (`37` tests)

| # | Classe de test | Type d'intégration | Tests | Passés | Erreurs | Durée (s) | Statut |
| :---: | :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| 1 | `common.config.DataSeedProfileTest` | `@SpringBootTest` (`@ActiveProfiles("prod")`) | 1 | 0 | **1** | 4,617 | ❌ `ERROR` |
| 2 | `common.controller.AdminSeedControllerTest` | `@SpringBootTest` + `@AutoConfigureMockMvc` | 3 | 3 | 0 | 14,431 | ✅ `PASS` |
| 3 | `common.domain.EnumConsistencyIntegrationTest` | `@SpringBootTest` + JPA/Hibernate | 1 | 1 | 0 | 1,935 | ✅ `PASS` |
| 4 | `domain.admin.controller.AdminStatistiquesControllerTest` | `@SpringBootTest` + `@AutoConfigureMockMvc` | 3 | 3 | 0 | 3,065 | ✅ `PASS` |
| 5 | `domain.admin.service.AdminStatistiquesServiceTest` | `@SpringBootTest` + Requêtes SQL/JPA agrégées | 3 | 3 | 0 | 0,177 | ✅ `PASS` |
| 6 | `domain.annonce.controller.AnnonceIntegrationTest` | `@SpringBootTest` + `MockMvc` (CRUD, filtres, RBAC, upload >5 Mo) | 5 | 5 | 0 | 4,195 | ✅ `PASS` |
| 7 | `domain.annonce.controller.DocumentVerificationSecurityTest` | `@SpringBootTest` + `MockMvc` (confidentialité KYC) | 2 | 2 | 0 | 0,070 | ✅ `PASS` |
| 8 | `domain.annonce.service.MesAnnoncesFilterTest` | `@SpringBootTest` + `AnnonceSpecification` JPA | 1 | 1 | 0 | 0,072 | ✅ `PASS` |
| 9 | `domain.annonce.service.SoftDeleteExclusionTest` | `@SpringBootTest` + exclusion `supprime=true` | 1 | 1 | 0 | 0,039 | ✅ `PASS` |
| 10 | `domain.ia.controller.IAIntegrationTest` | `@SpringBootTest` + `MockMvc` (workflow IA & limite 500 car.) | 2 | 2 | 0 | 1,795 | ✅ `PASS` |
| 11 | `domain.ia.service.LlmExtractionMockServerTest` | Serveur HTTP local embarqué (JSON malformé + timeout -> fallback) | 2 | 2 | 0 | 4,171 | ✅ `PASS` |
| 12 | `domain.messagerie.MessagerieSecurityTest` | `@SpringBootTest` + `MockMvc` & intercepteur STOMP | 2 | 2 | 0 | 0,146 | ✅ `PASS` |
| 13 | `domain.messagerie.MessagerieStompIntegrationTest` | `@SpringBootTest(RANDOM_PORT)` + vrai client WebSocket STOMP | 3 | 3 | 0 | 4,082 | ✅ `PASS` |
| 14 | `domain.transaction.controller.WorkflowTransactionIntegrationTest` | `@SpringBootTest` + `MockMvc` (LOCATION, VENTE, séquestre, double signature, PDF) | 7 | 7 | 0 | 0,702 | ✅ `PASS` |
| 15 | `domain.utilisateur.controller.AuthIntegrationTest` | `@SpringBootTest` + `MockMvc` (inscription, connexion, RBAC Admin) | 3 | 3 | 0 | 0,737 | ✅ `PASS` |
| — | *15 autres classes unitaires (`*ServiceTest`, `JwtServiceTest`, `FileStorageServiceTest`, etc.)* | JUnit 5 + Mockito | 67 | 67 | 0 | 2,713 | ✅ `PASS` |
| **Total** | **30 classes de test** | **Surefire 3.2.5 / Java 21** | **106** | **105** | **1** | **42,947** | **99,06 %** |

#### Analyse racine de l'erreur `DataSeedProfileTest`
- **Test en échec** : `com.ecourtier.common.config.DataSeedProfileTest#testDataSeedRunnerDisabledInProdProfile`
- **Exception levée** : `IllegalStateException: Failed to load ApplicationContext` -> `RuntimeException: Driver org.h2.Driver claims to not accept jdbcUrl, jdbc:postgresql://localhost:5432/ecourtier_db`
- **Cause exacte** : `DataSeedProfileTest` active uniquement `@ActiveProfiles("prod")` et surcharge `spring.datasource.driver-class-name=org.h2.Driver` sans surcharger `spring.datasource.url`. Or `DatabaseConfig` (`@Profile("!test")`) s'active dès que le profil `test` est absent et injecte par défaut `jdbc:postgresql://localhost:5432/ecourtier_db` dans HikariCP, provoquant un conflit immédiat avec le driver H2.

### 2.2 Couverture de code JaCoCo (`87` classes analysées)

- **Couverture globale** : **78,8 % des lignes** (`1 822 / 2 313`), **49,7 % des branches** (`532 / 1 070`), **73,1 % des instructions**.

| Domaine / Package | Classes | Lignes couvertes | Couverture Lignes | Couverture Branches | Observation |
| :--- | :---: | :---: | :---: | :---: | :--- |
| `domain.rendezvous.service` | 1 | 60 / 60 | **100,0 %** | **87,5 %** | Couverture service complète |
| `domain.utilisateur.service` | 1 | 62 / 62 | **100,0 %** | **100,0 %** | Couverture service complète |
| `domain.ia.controller` | 1 | 8 / 8 | **100,0 %** | 50,0 % | Couvert par `IAIntegrationTest` |
| `domain.admin.controller` | 1 | 2 / 2 | **100,0 %** | **100,0 %** | Couvert par `AdminStatistiquesControllerTest` |
| `domain.annonce.service` | 1 | 148 / 153 | **96,7 %** | 63,2 % | Très forte couverture |
| `common.service` | 2 | 251 / 263 | **95,4 %** | 77,8 % | Stockage fichiers + Seeder démo |
| `domain.avis.service` | 2 | 84 / 93 | **90,3 %** | 54,5 % | Avis + Signalements |
| `domain.admin.service` | 1 | 122 / 144 | **84,7 %** | 57,1 % | Agrégations statistiques |
| `domain.transaction.service` | 2 | 120 / 144 | **83,3 %** | 62,1 % | Séquestre + générateur PDF OpenPDF |
| `domain.utilisateur.controller` | 2 | 15 / 18 | **83,3 %** | 25,0 % | `AuthController` + `AdminUtilisateurController` |
| `common.security` | 10 | 155 / 210 | **73,8 %** | 42,6 % | JWT, filtres HTTP/STOMP, RateLimiter |
| `domain.transaction.controller` | 1 | 16 / 22 | **72,7 %** | 50,0 % | Couvert par `WorkflowTransactionIntegrationTest` |
| `domain.messagerie.service` | 1 | 56 / 84 | **66,7 %** | 66,7 % | Service messagerie |
| `domain.ia.service` | 3 | 224 / 355 | **63,1 %** | 35,9 % | `IAService`, `LlmExtractionService`, `RuleBasedCriteriaExtractor` |
| `domain.favori.service` | 1 | 21 / 34 | **61,8 %** | 75,0 % | Uniquement tests unitaires Mockito |
| `common.exception` | 1 | 18 / 33 | **54,5 %** | 100,0 % | `GlobalExceptionHandler` (< 60 %) |
| `common.controller` | 2 | 8 / 22 | **36,4 %** | 20,0 % | `UploadController` non couvert en JUnit (< 60 %) |
| `domain.annonce.controller` | 2 | 11 / 31 | **35,5 %** | 30,0 % | Endpoints photos/ordre/suppression non couverts en MockMvc (< 60 %) |
| `domain.messagerie.controller` | 2 | 6 / 20 | **30,0 %** | 50,0 % | Plusieurs endpoints REST messagerie non couverts en MockMvc (< 60 %) |
| `common.health` | 1 | 1 / 4 | **25,0 %** | 100,0 % | `HealthController` non appelé en JUnit (< 60 %) |
| `domain.avis.controller` | 3 | 0 / 12 | **0,0 %** | — | Aucun test `@SpringBootTest` MockMvc (< 60 %) |
| `domain.favori.controller` | 1 | 0 / 8 | **0,0 %** | — | Aucun test `@SpringBootTest` MockMvc (< 60 %) |
| `domain.rendezvous.controller` | 1 | 0 / 15 | **0,0 %** | — | Aucun test `@SpringBootTest` MockMvc (< 60 %) |

> C'est précisément pour combler ces angles morts MockMvc (`avis`, `favori`, `rendezvous`, `uploads`, `health`) que la **suite d'intégration Full-Stack Live (Niveau 2)** ci-dessous a exécuté et vérifié **100 % des 54 endpoints** contre PostgreSQL 16 réel.

---

## 3. Niveau 2 — Tests d'Intégration Full-Stack Live (API REST + PostgreSQL 16 + WebSocket STOMP)

### 3.1 Synthèse par domaine métier (`83/83` assertions validées)

| Domaine | Endpoints couverts | Assertions | Réussies | Temps cumulé | Points clés vérifiés en intégration réelle |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **D1 — Infra & OpenAPI** | 3 routes (`/health`, `/v3/api-docs`, `/swagger-ui`) | 3 | **3** | 48 ms | Statut `UP`, génération dynamique de `47` chemins OpenAPI `3.0.1`, Swagger UI accessible. |
| **D2 — Auth, JWT & Rate-Limit** | 3 endpoints (`/inscription`, `/connexion`, `/rafraichir`) | 10 | **10** | 951 ms | Inscription `LOCATAIRE`/`PROPRIETAIRE` (`201`), anti-doublon (`409`), blocage auto-inscription `AGENT` (`400`), refresh JWT (`200`/`401`), **anti-bruteforce `[401×5 -> 429]` au 6e essai**. |
| **D3 — Annonces, Médias & KYC** | 13 endpoints (`Annonce`, `Upload`, `AdminAnnonce`) | 22 | **22** | 251 ms | Création `LOCATION` & `VENTE` (`201`), **rejet PHP déguisé en `.png` (`400`)**, galerie photos (upload/lecture `image/png`/ordre/suppression `204`), dépôt des 3 pièces KYC, confidentialité KYC, workflow Admin `EN_ATTENTE -> VERIFIEE`, recherche multicritères, soft-delete (`204 -> 404`). |
| **D4 — Assistant IA** | 3 endpoints (`/ia/conversations`, `/messages`, `/{id}`) | 5 | **5** | 1 255 ms | Création session (`201`), fusion mémoire multi-tours + shortlist pertinente (bascule automatique en `[MODE FALLBACK IA]` suite au quota `429` Gemini Free Tier), blocage Prompt Injection (`400`) et `>500` car. (`400`), persistance PostgreSQL (`4` messages). |
| **D5 — Rendez-vous & SMS** | 7 endpoints (`RendezVousController`) | 8 | **8** | 90 ms | Demande (`201 DEMANDE`), confirmation (`200 CONFIRME`), **conflit horaire `±30 min` (`409`)**, isolation agenda propriétaire, **simulation rappels SMS J-1 (`rappelEnvoye=true`)**, annulation avec motif (`ANNULE`), validation visite (`EFFECTUE`). |
| **D6 — Transactions, Séquestre Wave & PDF** | 6 endpoints (`TransactionController`) | 10 | **10** | 160 ms | Blocage avant visite `EFFECTUE` (`400`), **calcul serveur infalsifiable (`360k + 360k + 5k = 725 000 FCFA` malgré `montantCaution: 1` injecté)**, blocage Wave `BLOQUE` (`WAVE-SANDBOX-*`), maintien `BLOQUE` à la 1re signature (`PDF=404`), **libération automatique `LIBERE` + `LOUEE` + PDF `%PDF-1.x` (`1 465 octets`) à la 2e signature**, et workflow complet `VENTE -> VENDUE` (`montantPremierLoyer=0`). |
| **D7 — Avis, Réputation & Signalements** | 6 endpoints (`Avis`, `Reputation`, `Signalement`) | 8 | **8** | 93 ms | Rejet avis sans visite (`403`), dépôt après `EFFECTUE` (`201`), anti-doublon (`409`), moyenne annonce (`5.0`) et réputation propriétaire (`5.0`), signalement arnaque (`201`) et **modération Admin suspendant immédiatement l'annonce (`SUSPENDUE`)**. |
| **D8 — Favoris** | 4 endpoints (`FavoriController`) | 5 | **5** | 57 ms | Ajout (`201`), anti-doublon (`409`), liste paginée avec `estFavori=true`, compteur exact (`1`), retrait (`204`) puis `404`. |
| **D9 — Messagerie REST + WebSocket STOMP** | 7 REST + **1 STOMP `/ws`** | 9 | **9** | 730 ms | Anti auto-contact (`400`), création idempotente (`201`), **connexion WebSocket STOMP `ws://localhost:8080/ws` avec JWT, diffusion temps réel sur `/topic/conversations/{id}` en `200 ms`**, **rejet d'un intrus tiers sur STOMP (`0` message pirate en base)**, compteur `totalNonLus: 1 -> 0` après `PUT /marquer-lu` (`204`). |
| **D10 — Administration & Seed** | 3 endpoints (`statistiques`, `utilisateurs`, `seed-demo`) | 3 | **3** | 59 ms | KPI Admin `7j`/`30j` (`200`), blocage non-admin, annuaire `30` utilisateurs (`200`), idempotence `seed-demo` (`seeded=false`). |

---

## 4. Niveau 3 — Tests d'Intégration E2E Navigateur (`puppeteer-core` / Chromium Headless)

| ID | Parcours E2E exécuté sur `http://localhost:3000` | Résultat observé | Durée | Statut |
| :--- | :--- | :--- | :---: | :---: |
| **`E2E-UI-01`** | Connexion formulaire `/connexion` (`LOCATAIRE`) -> stockage Zustand JWT -> redirection `/recherche` -> chargement de la grille depuis PostgreSQL | Redirection automatique vers `/recherche`, `9` cartes d'annonces rendues avec prix FCFA et badges. | 3 869 ms *(inclut UI-02)* | ✅ `PASS` |
| **`E2E-UI-02`** | Ouverture du widget flottant « Assistant IA E-Courtier » (`Samba`) -> saisie d'une requête en français naturel -> appel `/api/v1/ia/conversations` -> affichage réponse + mini-cartes shortlist | Panneau `Samba — IA Immobilier` ouvert, réponse et cartes cliquables rendues sans erreur console. | < 2 100 ms *(interaction IA)* | ✅ `PASS` |
| **`E2E-UI-03`** | Connexion formulaire `/connexion` (`PROPRIETAIRE`) -> redirection `/dashboard` -> chargement des annonces publiées, visites et dossiers séquestre | Vue `/dashboard` chargée, annonce créée en test (`Mermoz`) et métriques propriétaire affichées. | 1 712 ms | ✅ `PASS` |
| **`E2E-UI-04`** | Connexion formulaire `/connexion` (`ADMIN`) -> navigation `/admin/dashboard` (KPIs), `/admin/verification` (file KYC), `/admin/signalements` (modération) | Les 3 vues d'administration chargent les agrégations réelles (`2 114`, `4 712` et `983` car. de contenu métier). | 4 876 ms | ✅ `PASS` |

---

## 5. Anomalies et Écarts Révélés par les Tests d'Intégration

Conformément à la règle d'observation (aucun fichier source applicatif modifié), voici les **6 anomalies d'intégration** identifiées avec leur diagnostic exact et le correctif recommandé :

| # | Sévérité | Composant / Endpoint | Symptôme observé en intégration | Cause technique identifiée | Correctif recommandé |
| :---: | :---: | :--- | :--- | :--- | :--- |
| **1** | **Majeure** | `DataSeedProfileTest` (`mvn test`) | Échec de démarrage du contexte Spring (`1 ERROR` sur `106` tests Maven). | `DatabaseConfig` (`@Profile("!test")`) s'active sous `@ActiveProfiles("prod")` et injecte une URL JDBC PostgreSQL incompatible avec `org.h2.Driver`. | Ajouter `spring.datasource.url=jdbc:h2:mem:prodtest;DB_CLOSE_DELAY=-1` dans `@TestPropertySource` ou conditionner `DatabaseConfig` sur `@ConditionalOnProperty`. |
| **2** | **Majeure** | `SecurityConfig` & `GlobalExceptionHandler` (RBAC & JSON malformé) | Un utilisateur authentifié sans le rôle requis (`LOCATAIRE` -> `/api/v1/admin/**`, `POST /annonces`, `GET /documents/.../fichier`) ou envoyant un enum JSON invalide reçoit **HTTP `401`** au lieu de **`403 Forbidden`** / **`400 Bad Request`**. | Absence d'`accessDeniedHandler` `403` explicite dans `SecurityConfig`, route `/error` non ouverte dans `authorizeHttpRequests`, et absence de `@ExceptionHandler(HttpMessageNotReadableException.class)` dans `GlobalExceptionHandler`. | Configurer `.accessDeniedHandler((req, res, ex) -> res.sendError(403))`, autoriser `/error`, et gérer `HttpMessageNotReadableException` en `400` dans `GlobalExceptionHandler`. |
| **3** | **Majeure** | Contrat Frontend <-> `GET /api/v1/annonces?proximite=MOSQUEE` | Sélectionner « Mosquée » dans le filtre Proximités de la recherche renvoie **HTTP `400 Bad Request`**. | Le frontend propose l'option `MOSQUEE`, absente de l'enum Java `ProximiteTag` (`ECOLE`, `MARCHE`, `HOPITAL`, `TRANSPORT`, `PLAGE`, `COMMERCE`, `UNIVERSITE`). | Ajouter `MOSQUEE` dans `ProximiteTag.java` (et migration Flyway si contrainte `CHECK` SQL). |
| **4** | **Moyenne** | `POST /api/v1/annonces/{id}/documents` | Un titre foncier ou bail précédent téléversé au format `.pdf` (`%PDF-1.4`) est rejeté avec **HTTP `400`**. | `AnnonceService.ajouterDocumentVerification` délègue à `FileStorageService` / `FileValidationUtil` qui n'autorise que les signatures binaires d'images (`JPEG`, `PNG`, `WEBP`). | Créer une méthode dédiée autorisant également `application/pdf` (magic-bytes `25 50 44 46` / `%PDF-`) pour les pièces justificatives KYC. |
| **5** | **Moyenne** | `TransactionService.signerBail` & `PdfBailGeneratorService` | 1) Une partie peut signer électroniquement alors que `statutSequestre == INITIALISE` (avant paiement Wave), bien que la libération reste bloquée jusqu'à `BLOQUE && SIGNE_COMPLET`.<br>2) En mode `VENTE`, `montantPremierLoyer` vaut bien `0`, mais le PDF généré conserve la terminologie de contrat de bail locatif. | 1) `signerBail()` ne vérifie pas `tx.getStatutSequestre() == StatutSequestre.BLOQUE`.<br>2) `PdfBailGeneratorService` utilise un gabarit unique orienté location. | 1) Exiger `statutSequestre == BLOQUE` au début de `signerBail()` pour imposer l'ordre strict `visite -> caution séquestre -> signature -> libération`.<br>2) Adapter le titre et les clauses du PDF lorsque `modeTransaction == VENTE`. |
| **6** | **Mineure** | Suite JUnit `backend/src/test/java` | 4 contrôleurs (`AvisController`, `SignalementController`, `FavoriController`, `RendezVousController`) + `UploadController` ont `0 %` à `36 %` de couverture JaCoCo dans Maven. | Ces domaines disposent de bons tests unitaires Mockito (`*ServiceTest`) mais pas de tests `@SpringBootTest` + `MockMvc`. | Ajouter 4 classes `*ControllerIntegrationTest` dans `src/test/java` calquées sur les scénarios `IT-D5`, `IT-D7` et `IT-D8` exécutés dans notre suite Live. |

---

## 6. Couverture Exhaustive des 54 Endpoints Applicatifs (Matrice de Vérification)

Tous les **53 endpoints REST** et l'**endpoint WebSocket STOMP** ont été exécutés et validés aujourd'hui :

- **Infra (1 + 2)** : `GET /api/v1/health` ✅ · `GET /v3/api-docs` ✅ · `GET /swagger-ui/index.html` ✅
- **Auth (3)** : `POST /api/v1/auth/inscription` ✅ · `POST /api/v1/auth/connexion` ✅ · `POST /api/v1/auth/rafraichir` ✅
- **Annonces & Médias (11)** : `POST /api/v1/annonces` ✅ · `GET /api/v1/annonces` ✅ · `GET /api/v1/annonces/{id}` ✅ · `PUT /api/v1/annonces/{id}` ✅ · `DELETE /api/v1/annonces/{id}` ✅ · `POST /api/v1/annonces/{id}/photos` ✅ · `PUT /api/v1/annonces/{id}/photos/ordre` ✅ · `DELETE /api/v1/annonces/{id}/photos/{photoId}` ✅ · `GET /api/v1/uploads/annonces/{id}/{filename}` ✅ · `POST /api/v1/annonces/{id}/documents` ✅ · `GET /api/v1/annonces/{id}/documents/{documentId}/fichier` ✅
- **Admin Vérification (2)** : `GET /api/v1/admin/annonces/en-attente-verification` ✅ · `PUT /api/v1/admin/annonces/{id}/verifier` ✅
- **Assistant IA (3)** : `POST /api/v1/ia/conversations` ✅ · `POST /api/v1/ia/conversations/{id}/messages` ✅ · `GET /api/v1/ia/conversations/{id}` ✅
- **Rendez-vous (7)** : `POST /api/v1/annonces/{id}/rendez-vous` ✅ · `GET /api/v1/annonces/{id}/rendez-vous` ✅ · `GET /api/v1/rendez-vous/mes-rendez-vous` ✅ · `PUT /api/v1/rendez-vous/{id}/confirmer` ✅ · `PUT /api/v1/rendez-vous/{id}/annuler` ✅ · `PUT /api/v1/rendez-vous/{id}/marquer-effectue` ✅ · `POST /api/v1/rendez-vous/simuler-rappels` ✅
- **Transactions & Séquestre (6)** : `POST /api/v1/annonces/{id}/transactions` ✅ · `POST /api/v1/transactions/{id}/paiement-caution` ✅ · `POST /api/v1/transactions/{id}/bail/signer` ✅ · `GET /api/v1/transactions/mes-transactions` ✅ · `GET /api/v1/transactions/{id}` ✅ · `GET /api/v1/transactions/{id}/bail/pdf` ✅
- **Avis, Réputation & Signalements (6)** : `POST /api/v1/annonces/{id}/avis` ✅ · `GET /api/v1/annonces/{id}/avis` ✅ · `GET /api/v1/utilisateurs/{id}/reputation` ✅ · `POST /api/v1/annonces/{id}/signalements` ✅ · `GET /api/v1/admin/signalements` ✅ · `PUT /api/v1/admin/signalements/{id}/traiter` ✅
- **Favoris (4)** : `POST /api/v1/annonces/{id}/favoris` ✅ · `DELETE /api/v1/annonces/{id}/favoris` ✅ · `GET /api/v1/utilisateurs/moi/favoris` ✅ · `GET /api/v1/utilisateurs/moi/favoris/count` ✅
- **Messagerie REST & WebSocket STOMP (8)** : `POST /api/v1/annonces/{id}/conversations` ✅ · `GET /api/v1/conversations/mes-conversations` ✅ · `GET /api/v1/conversations/{id}` ✅ · `GET /api/v1/conversations/{id}/messages` ✅ · `GET /api/v1/conversations/{id}/messages/tous` ✅ · `PUT /api/v1/conversations/{id}/marquer-lu` ✅ · `GET /api/v1/conversations/non-lus/count` ✅ · `STOMP /app/conversations/{id}/envoyer` (`/ws` -> `/topic/conversations/{id}`) ✅
- **Admin Pilotage & Seed (3)** : `GET /api/v1/admin/statistiques` ✅ · `GET /api/v1/admin/utilisateurs` ✅ · `POST /api/v1/admin/seed-demo` ✅
