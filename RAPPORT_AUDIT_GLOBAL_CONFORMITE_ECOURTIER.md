# RAPPORT D'AUDIT GLOBAL APPROFONDI — CONFORMITÉ ET ÉTAT RÉEL D'E-COURTIER

**Projet :** E-Courtier Sénégal — Projet Tutoré 14 / 14 (Licence 3 Ingénierie Informatique, Université Assane Seck de Ziguinchor)  
**Dépôt local audité :** `/home/mahou-dev/Documents/Ecourtier2026`  
**Branche locale active :** `main`  
**Commit HEAD local :** `f2bf8a97af5c9fc43c40d25a0ed9eeacfb25f317` (6 commits locaux non poussés devant `origin/main` + 22 fichiers suivis modifiés + fichiers non suivis)  
**Date d'audit :** 13 novembre 2026  
**Statut de mission :** Audit indépendant strictement non destructif (lecture seule, aucun correctif appliqué, aucun commit/push/déploiement).

---

## 1. Résumé exécutif

### Verdict global : **NON PRÊT POUR LA PRODUCTION / PARTIELLEMENT CONFORME (DÉMO ACADÉMIQUE SOUS RÉSERVES)**

Le dépôt local d'E-Courtier Sénégal présente une base architecturale Java 21 / Spring Boot 3.4.3 et React 18 / TypeScript substantielle, dotée d'une suite backend de **275 tests** (**88,51 %** des lignes et **61,63 %** des branches couvertes selon JaCoCo) et de **17 migrations Flyway** validées sur PostgreSQL 16.

Cependant, l'inspection chirurgicale du code local réel et les exécutions de vérification démontrent des **failles de sécurité critiques/majeures (P0/P1)**, des **blocages fonctionnels métier**, plusieurs **intégrations externes purement simulées** ainsi que des **régressions UI/responsive avérées** :

1. **P0 — Faille d'authentification JWT (`JwtService.java:89-104`) :** `isTokenValid` ne vérifie pas le claim `type == "ACCESS"`, permettant d'utiliser un **`refreshToken` (durée 7 jours)** dans `Authorization: Bearer` sur l'ensemble de l'API REST et du WebSocket STOMP.
2. **P0 — Contournement de suspension & réactivation unilatérale d'annonce (`AnnonceService.java:143`) :** `PUT /api/v1/annonces/{id}` accepte librement `statutPublication` dans `AnnonceUpdateDTO` sans contrôle de rôle ni machine à états : un propriétaire dont l'annonce a été placée en **`SUSPENDUE`** (fraude/signalement) ou **`LOUEE`/`VENDUE`/`RESERVEE`** peut la repasser lui-même à **`DISPONIBLE`**.
3. **P0 — Verrouillage permanent d'annonce sur transaction non payée (`TransactionService.java`, `V14:176-178`) :** Aucun endpoint (ni locataire, ni propriétaire, ni admin) et aucune expiration `@Scheduled` ne permettent d'annuler ou de fermer une transaction au statut `INITIALISE` (`dossier_actif = TRUE`). L'index unique `uq_transactions_annonce_active` bloque définitivement toute nouvelle transaction sur l'annonce concernée.
4. **P1 — Régression d'état sur signature administrateur (`TransactionService.java:412-423`) & signature avant paiement (`365-386`) :** Un `ADMIN` appelant `/bail/signer` après signature préalable du propriétaire rétrograde `statutBail` de `SIGNE_PROPRIETAIRE` vers `SIGNE_LOCATAIRE`, bloquant la libération du séquestre ; par ailleurs, la signature est possible avant paiement (`INITIALISE`), et la signature PDF n'est qu'un horodatage textuel sans preuve cryptographique.
5. **P1 — Intégrations externes clés uniquement simulées :** Paiement Wave (`WaveClientMock.java` en `ConcurrentHashMap` mémoire, 0 webhook, 0 HMAC, perte d'état au redémarrage) et rappels SMS (`SmsGatewayLogImpl.java` écriture DB `SIMULE` + log SLF4J uniquement).
6. **P1 — Échec réel du test responsive mobile `/annonce/:id` (`lot3-performance-optimizations.spec.ts` Test 13) :** Lorsque `OfflineIndicator` s'affiche sur mobile `375x667`, son positionnement `fixed bottom-4 left-4` sans contrainte de largeur fait déborder la page horizontalement (`scrollWidth > clientWidth`) ; en parallèle, le menu `<nav className="hidden md:flex">` masque la navigation principale sous 768 px sans menu hamburger.
7. **P1 — Fonctionnalités décoratives, liens morts & filtres cassés côté frontend :** Lien mort `<a href="#reset">` sans flux de réinitialisation (`LoginPage.tsx:132`), paramètre de recherche `q` supprimé sur `/recherche`, paramètre `tri=` ignoré par Spring `Pageable` (`sort=`), absence de filtres `etage` et `equipements`, proximité purement déclarative sans calcul GPS, badges `isVerified` codés en dur sur des comptes propriétaires sans vérification KYC (`PropertyDetailPage.tsx:627`, `DashboardPage.tsx:183`), image `/placeholder.jpg` inexistante, et `npm run lint` cassé (`eslint` absent).

---

## 2. Identification exacte du dépôt local audité

- **Chemin racine :** `/home/mahou-dev/Documents/Ecourtier2026`
- **Référentiel cahier des charges vérifié :** `/home/mahou-dev/Téléchargements/briefs_projets_L3_2I.pdf` (page 1 : contraintes transversales communes ; pages 29–30 : Projet 14 / 14 — E-Courtier).
- **Processus et conteneurs présents au moment de l'audit (`docker ps`) :**
  - `ecourtier-backend` : `Restarting` (boucle d'arrêt : `ecourtier-db` de développement locale n'a pas les migrations V16–V17 alors que `application-dev.yml` fixe `SPRING_FLYWAY_ENABLED=false` et `ddl-auto: validate`).
  - `ecourtier-frontend` : `Up` (`0.0.0.0:3000->3000`).
  - `ecourtier-db` : `Up (healthy)` (`5435->5432`) et `ecourtier-postgres` : `Up (healthy)` (`5432->5432`). Aucune base réelle ou de dev n'a été modifiée.
- **Worktrees existants (intacts) :** `Ecourtier2026` (`main`), `Ecourtier2026-convergence` (`6603841`), `Ecourtier2026-lot1`, `Ecourtier2026-lot2`, `Ecourtier2026-lot3`, `Ecourtier2026-validation` (`d161762`).

---

## 3. État Git et comparaison avec les références distantes disponibles

Commandes Git exécutées strictement en lecture seule (`git rev-parse`, `git branch -vv`, `git status`, `git log`, `git diff`, `git ls-files`) :

- **Branche active :** `main`
- **HEAD local :** `f2bf8a97af5c9fc43c40d25a0ed9eeacfb25f317` (*Merge branch 'integration/phase-9d-convergence' into main*)
- **Comparaison locale avec `origin/main` (`6aa3d33`, tag `v1.0.0-rc2`, sans `fetch` ni `pull`) :**
  - **6 commits locaux en avance** sur `origin/main` (`0` en retard) : `f2bf8a9`, `6603841`, `d161762`, `315b914`, `1d84062`, `64eeb47`.
  - GitHub (`origin/main`) ne contient donc ni les correctifs Phase 9H/convergence (`transaction` V13/V14, sécurité R1/R2/RBAC, KPIs stats), ni les évolutions locales non commitées ci-dessous.
- **Modifications locales non commitées (22 fichiers suivis, `+573 / -95` lignes) :**
  - Backend : `.env.example`, `TypeBien.java` (`CHAMBRE`), `IAController.java`, `ConversationIAEntity.java`, `IAService.java`, `LlmExtractionService.java`, `RuleBasedCriteriaExtractor.java`, `ReferenceController.java`, `application.yml`, `application-dev.yml`, `FlywayFreshMigrationIntegrationTest.java`, `IAIntegrationTest.java`, `LlmExtractionServiceTest.java`, `ReferenceIntegrationTest.java`, `application-test.yml`.
  - Frontend : `AiAssistantWidget.tsx`, `CreateAnnonceModal.tsx`, `EditAnnonceModal.tsx`, `SearchResultsPage.tsx`, `api.ts`, `useAuthStore.ts`, `types/index.ts`.
- **Fichiers non suivis préexistants + présent rapport :**
  - Code/migrations/tests : `GroqClientService.java`, `V16__add_chambre_to_type_bien.sql`, `V17__ia_conversation_anonymous_session.sql`, `GroqClientServiceTest.java`, `AnonymousConversationSecurityIntegrationTest.java`, `ChatbotFunctionalAndSecurityTest.java`, `chatbot-groq-widget.spec.ts`, `chatbot-groq-r1-race.spec.ts`, `chatbot-groq-fullstack.spec.ts`.
  - Rapports historiques : `RAPPORT_CHATBOT_GROQ_5.md`, `RAPPORT_CHATBOT_GROQ_7.md`.
  - Livrable de la présente mission : `RAPPORT_AUDIT_GLOBAL_CONFORMITE_ECOURTIER.md`.

---

## 4. Méthodologie et limites de l'audit

- **Méthode :** Inspection directe des contrôleurs, entités, services, requêtes SQL/JPA, migrations Flyway, filtres de sécurité, composants React, hooks WebSocket, configuration PWA, Dockerfile/Compose/Render et tests existants, croisée avec les résultats d'exécution non destructifs réellement lancés.
- **Limites factuelles (sans modification de configuration ni appel payant externe) :**
  1. **API Groq live :** Bien que `.env` et `backend/.env` contiennent `GROQ_API_KEY` (non vide, non affichée), Spring Boot lancé hors Docker ne charge pas `.env` automatiquement et aucun appel réseau payant vers `api.groq.com` n'a été déclenché durant cet audit ; les tests ont validé le client HTTP via `MockRestServiceServer` et le workflow complet via le fallback déterministe.
  2. **Déploiement cloud Render :** Aucun redéploiement ni sonde réseau externe n'a été lancé pour modifier l'état distant ; la dernière sonde archivée (`docs/audit/render_l6.json`, 30/10/2026) montrait un backend en timeout (`> 90,4 s`) et `/recherche` en 404 (antérieur au `render.yaml` du 01/11).
  3. **Suites Playwright liées au serveur `:3000` / `:4173` :** Les suites `capture-audit-screenshots.spec.ts`, `recherche-filtres.spec.ts`, `phase9f-dynamic-validation.spec.ts` et `phase9h-admin-refund.spec.ts` n'ont pas été relancées dans cet audit car `ecourtier-backend` local crashait sur `ecourtier-db` désynchronisée.

---

## 5. Inventaire des technologies

- **Backend (`backend/pom.xml`) :** Java `21`, Spring Boot `3.4.3` (`spring-boot-starter-web`, `data-jpa`, `security`, `validation`, `websocket`), PostgreSQL JDBC (`runtime`), Flyway (`flyway-core`, `flyway-database-postgresql`, V1–V17), JJWT `0.12.6`, OpenPDF `2.0.3`, Springdoc OpenAPI `2.7.0`, Lombok, JaCoCo `0.8.12`, H2 (scope `test`). *Note : `README.md` mentionne à tort `LangChain4j` et `Bucket4j`, absents de `pom.xml`.*
- **Frontend (`frontend/package.json`) :** React `18.3.1`, TypeScript `5.7.3`, Vite `6.1.0`, `vite-plugin-pwa` `1.3.0` (Workbox `7.4.1`), Tailwind CSS `3.4.17`, React Router `7.1.5`, TanStack React Query `5.66.0`, Zustand `5.0.3`, React Hook Form `7.54.2` + Zod `3.24.2`, `@stomp/stompjs` `7.3.0`, Leaflet `1.9.4` + `react-leaflet` `4.2.1`, Recharts `3.10.1`, Lucide `0.475.0`, Playwright `1.64.1`.

---

## 6. Matrice exhaustive de conformité au cahier des charges

### Tableau de synthèse par module

*(Classifications de la Section 11 : `Validées` = EFFECTIVE ET VALIDÉE ; `Partielles` = PARTIELLE ; `Simulées` = SIMULÉE / MOCKÉE ; `Absentes` = ABSENTE ; `Non vérifiées` regroupe `IMPLÉMENTÉE NON VALIDÉE` et `BLOQUÉE` ; `Non effectives` regroupe les fonctions présentes mais défaillantes/cassées `NON EFFECTIVE`.)*

| Module | Exigences | Validées | Partielles | Simulées | Absentes | Non effectives | Non vérifiées | Verdict |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| A. Authentification & Utilisateurs | 6 | 1 | 4 | 0 | 1 | 0 | 0 | Partiellement conforme (faille JWT P0) |
| B. Gestion des annonces | 6 | 3 | 1 | 0 | 0 | 1 | 1 | Partiellement conforme (faille statut P0) |
| C. Vérification documentaire & anti-fraude | 3 | 2 | 0 | 0 | 0 | 1 | 0 | Conforme côté annonces / KYC user fictif |
| D. Recherche & filtres avancés | 5 | 1 | 2 | 0 | 1 | 1 | 0 | Partiellement conforme (bugs `tri`/`q`, 0 distance GPS) |
| E. Cartographie & géolocalisation | 2 | 1 | 1 | 0 | 0 | 0 | 0 | Partiellement conforme |
| F. Chatbot immobilier & IA Groq | 4 | 2 | 1 | 0 | 0 | 0 | 1 | Robuste sur socle/fallback ; Groq live non validé |
| G. Rendez-vous & visites | 2 | 1 | 1 | 0 | 0 | 0 | 0 | Partiellement conforme |
| H. Notifications SMS | 1 | 0 | 0 | 1 | 0 | 0 | 0 | Uniquement simulé |
| I. Transactions, séquestre & Wave | 5 | 1 | 1 | 1 | 2 | 0 | 0 | Non prêt (Wave mock + deadlock `INITIALISE` P0) |
| J. Contrats & signature électronique | 2 | 0 | 2 | 0 | 0 | 0 | 0 | Non qualifié / bug régression signature admin P1 |
| K. Messagerie & communication | 2 | 1 | 1 | 0 | 0 | 0 | 0 | Partiellement conforme (faille WS compte désactivé) |
| L. Évaluations, signalements & admin | 4 | 3 | 1 | 0 | 0 | 0 | 0 | Globalement conforme |
| M. Transversal (OpenAPI, Responsive, A11y, PWA, Tests) | 5 | 1 | 3 | 0 | 0 | 1 | 0 | Partiellement conforme (échec mobile `375px` & lint) |
| N. DevOps, Déploiement & Livrables académiques | 2 (+1 N/A) | 0 | 1 | 0 | 0 | 0 | 1 | Bloqué / Incomplet (UML/Postman/Render live) |
| **TOTAL (49 applicables + 1 N/A)** | **49 (+1 N/A)** | **17** | **19** | **2** | **4** | **4** | **3** | **PARTIELLEMENT CONFORME** |

### Matrice détaillée par exigence élémentaire

| ID | Module | Exigence | Classification | Preuve / Fichier & Ligne | Limite / Risque |
|---|---|---|---|---|---|
| `AUTH-01` | A | Inscription publique (`LOCATAIRE`/`PROPRIETAIRE`) | **EFFECTIVE ET VALIDÉE** | `AuthController.java:31`, `AuthService.java:33-71`, `RegisterPage.tsx` | Interdit `ADMIN`/`AGENT`, hash BCrypt, test `AuthIntegrationTest` (4/4). |
| `AUTH-02` | A | Connexion JWT + RBAC 4 rôles | **PARTIELLE** | `AuthController.java:38`, `JwtService.java:89-104` | **Critique P0 :** `isTokenValid` ne vérifie pas `type == "ACCESS"`, acceptant un `refreshToken` (7j) comme `accessToken`. |
| `AUTH-03` | A | Renouvellement token (`POST /rafraichir`) | **PARTIELLE** | `AuthController.java:57`, `AuthService.java:112-127` | Émet une nouvelle paire sans révoquer ni rotater l'ancien `refreshToken`. |
| `AUTH-04` | A | Déconnexion & révocation de session | **PARTIELLE** | `useAuthStore.ts:32-57` (aucun endpoint dans `AuthController.java`) | Logout 100 % côté client ; les JWT restent valides côté serveur jusqu'à expiration. |
| `AUTH-05` | A | Réinitialisation mot de passe oublié | **ABSENTE** | `LoginPage.tsx:131-135` (`<a href="#reset">`) | Lien mort purement visuel, zéro backend/frontend. |
| `AUTH-06` | A | Activation / désactivation des comptes | **PARTIELLE** | `JwtAuthenticationFilter.java:55-59`, `WebSocketAuthInterceptor.java:44-95` | Rejet immédiat sur REST, mais session WebSocket ouverte non coupée sur `SUBSCRIBE`/`SEND`. |
| `ANN-01` | B | CRUD `STUDIO`, `APPARTEMENT`, `VILLA`, `CHAMBRE` (`LOCATION`/`VENTE`) | **EFFECTIVE ET VALIDÉE** | `AnnonceController.java:53-86`, `TypeBien.java`, `CreateAnnonceModal.tsx` | Validé par `AnnonceIntegrationTest` et `AnnonceServiceTest`. |
| `ANN-02` | B | Photos multiples (max 10, 5 Mo, magic bytes, anti-traversal) | **EFFECTIVE ET VALIDÉE** | `AnnonceController:139-173`, `FileStorageService:19-41,223-233`, `UploadController:39-112` | Validé par `UploadSecurityTest` et `FileStorageServiceTest`. |
| `ANN-03` | B | Gestion des photos/documents lors de la modification | **PARTIELLE** | `EditAnnonceModal.tsx:1-362` vs `AnnonceController:139-173` | Endpoints `DELETE /photos/{id}` et `PUT /photos/ordre` jamais appelés dans l'UI. |
| `ANN-04` | B | Visite 360° optionnelle (`lienVisite360`) | **IMPLÉMENTÉE NON VALIDÉE** | `CreateAnnonceModal.tsx:298-303`, `AnnonceEntity:97-98`, `PropertyDetailPage.tsx` | Absent de `EditAnnonceModal.tsx` ; aucun test dédié exécuté. |
| `ANN-05` | B | Intégrité des statuts publication (`DISPONIBLE`/`SUSPENDUE`/`LOUEE`/`VENDUE`) | **NON EFFECTIVE** | `AnnonceUpdateDTO.java:72`, `AnnonceService.java:143` | **Critique P0 :** le propriétaire peut réactiver en `DISPONIBLE` une annonce `SUSPENDUE` par un admin ou `LOUEE`/`VENDUE` via `PUT /annonces/{id}`. |
| `ANN-06` | B | Suppression logique & exclusion requêtes | **EFFECTIVE ET VALIDÉE** | `AnnonceController:88-95`, `AnnonceService:206-212`, `SoftDeleteExclusionTest` | Exclue des annonces publiques et recherches. |
| `VER-01` | C | Dépôt confidentiel de preuves (`TITRE_FONCIER`, `BAIL_PRECEDENT`, `PIECE_IDENTITE`) | **EFFECTIVE ET VALIDÉE** | `AnnonceController:175-213`, `FileStorageService:47-67`, `DocumentVerificationSecurityTest` | Stockage privé `uploads/documents/`, accès réservé auteur/admin. |
| `VER-02` | C | Validation/rejet admin, badge serveur & réévaluation sur modif | **EFFECTIVE ET VALIDÉE** | `AdminAnnonceController:34-53`, `AnnonceService:120-203,504-564`, `AdminVerificationPage.tsx` | Contrôle serveur strict des pièces requises ; `adminId` validateur non tracé en base. |
| `VER-03` | C | Vérification des comptes propriétaires/agents | **NON EFFECTIVE** | `UtilisateurEntity.java:29-69`, `PropertyDetailPage.tsx:621-627`, `DashboardPage.tsx:183` | Aucun KYC utilisateur en BDD ; l'UI affiche `<Avatar isVerified />` et `"propriétaire identifié"` en dur. |
| `FIL-01` | D | Filtres `ville`, `quartier`, `type`, `budget`, `superficieMin`, `pieces`, `meuble`, `animaux`, `verifie`, pagination | **EFFECTIVE ET VALIDÉE** | `AnnonceController:103-137`, `AnnonceSpecification:83-174`, `SearchResultsPage.tsx` | Note : liste des 4 villes codée en dur en UI ; aucun `superficieMax`. |
| `FIL-02` | D | Filtres `etage`, `equipements`, `chambres` & recherche texte `q` Hero | **ABSENTE** | `HomePage.tsx:40`, `SearchResultsPage.tsx:39-171`, `AnnonceSpecification.java` | `etage` et `equipements` non filtrables en recherche ; champ `q` du Hero supprimé silencieusement sur `/recherche`. |
| `FIL-03` | D | Proximité (école, marché, hôpital) | **PARTIELLE** | `ProximiteTag.java`, `AnnonceSpecification:177-187`, `SearchResultsPage:941-976` | Uniquement tags déclaratifs (OU logique) ; **zéro calcul de distance GPS**. |
| `FIL-04` | D | Exclusion des annonces indisponibles | **PARTIELLE** | `AnnonceSpecification.java:108-111` | Exclut `LOUEE`, `VENDUE`, `SUSPENDUE`, mais **pas `RESERVEE`**. |
| `FIL-05` | D | Tri dynamique des annonces | **NON EFFECTIVE** | `HomePage.tsx:33`, `SearchResultsPage.tsx:212-213`, `AnnonceController:106` | Frontend envoie `tri=`/`direction=` ignorés par Spring `Pageable` (`sort=`) ; zéro sélecteur de tri en UI. |
| `MAP-01` | E | Localisation GPS Sénégal & picker création/édition | **EFFECTIVE ET VALIDÉE** | `LocationPickerMap.tsx:28-116`, `AnnonceService:348-359` | Bornes Sénégal contrôlées client et serveur. |
| `MAP-02` | E | Carte interactive des résultats & géolocalisation recherche | **PARTIELLE** | `LeafletMap.tsx:27-147`, `SearchResultsPage.tsx:233,697` | Affiche uniquement les 12 biens de la page courante ; pas de recherche "autour de moi". |
| `IA-01` | F | Widget intégré, contexte multi-tours, recherche PostgreSQL & fallback | **EFFECTIVE ET VALIDÉE** | `AiAssistantWidget.tsx`, `IAService.java`, `chatbot-groq-fullstack.spec.ts` (4/4) | Démontré sur fallback déterministe avec vraie BDD PostgreSQL 16. |
| `IA-02` | F | Appel réel API Groq (< 3s) | **BLOQUÉE** | `GroqClientService.java:34-146`, `application.yml:52-55`, `.env` | Clé présente dans `.env`, mais testée uniquement sur mock/fallback ; `application.yml:52` masque `IA_API_KEY` (`render.yaml`). |
| `IA-03` | F | Isolation conversations, sécurité anonyme R2 & courses async R1 | **EFFECTIVE ET VALIDÉE** | `IAService.java` (cookie `HttpOnly` SHA-256), `V17`, `AiAssistantWidget.tsx` | `AnonymousConversationSecurityIntegrationTest` (14/14 PG16) + `chatbot-groq-r1-race.spec.ts` (6/6). |
| `IA-04` | F | Protection prompt injections | **PARTIELLE** | `RuleBasedCriteriaExtractor.java:19-32`, `GroqClientService.java:131,178-206` | Contournable par `\n` (absence de `Pattern.DOTALL`), pas d'échappement `</user_query_untrusted>`. |
| `RDV-01` | G | Demande visite, conflits ±60 min, auto-confirm, annulation | **PARTIELLE** | `RendezVousController.java`, `RendezVousService.java:49-221` | Pas d'endpoint de modification/report ; `confirmer`/`annuler` sans garde d'état sur RDV déjà `EFFECTUE`. |
| `RDV-02` | G | Agenda & historique propriétaire/agent/locataire | **EFFECTIVE ET VALIDÉE** | `RendezVousController:86-109`, `DashboardPage.tsx:464-497`, `RendezVousWorkflowSmsIntegrationTest` | Isolation assurée par `RendezVousSecurityService`. |
| `SMS-01` | H | Confirmations et rappels J-1 par SMS | **SIMULÉE / MOCKÉE** | `SmsGatewayLogImpl.java:19-53`, `notifications_sms`, `SmsSimulesTab.tsx` | Persistance DB `SIMULE` + log SLF4J uniquement ; 0 passerelle SMS ni retry externe. |
| `TX-01` | I | Calcul serveur caution, 1er loyer, frais 5 000 FCFA & verrouillage | **EFFECTIVE ET VALIDÉE** | `TransactionService.java:75,109-168,173-241`, `V13`, `V14` | `@PESSIMISTIC_WRITE`, `@Version` et index uniques SQL validés par tests. |
| `TX-02` | I | Paiement Mobile Money Wave Sandbox | **SIMULÉE / MOCKÉE** | `WaveClientMock.java:14-191` | Simulation locale en `ConcurrentHashMap` ; 0 appel HTTP Wave Sandbox ; perdu si reboot serveur. |
| `TX-03` | I | Webhooks Wave & vérification signature HMAC | **ABSENTE** | `TransactionController.java` | Aucun endpoint webhook ni vérification de signature dans le dépôt. |
| `TX-04` | I | Annulation ou expiration transaction `INITIALISE` abandonnée | **ABSENTE** | `TransactionController.java`, `V14:176-178` (`uq_transactions_annonce_active`) | **Critique P0 :** bloque définitivement l'annonce en BDD sans recours API ni cron TTL. |
| `TX-05` | I | Remboursement admin, historique & libération auto séquestre | **PARTIELLE** | `TransactionService:244-358,432-469`, `PropertyDetailPage.tsx:272` | Pas de litige utilisateur ; libération sans virement `WaveClient` ; UI `PropertyDetailPage:272` ne filtre pas `REMBOURSE`. |
| `SIG-01` | J | Génération, conservation et téléchargement PDF du bail | **PARTIELLE** | `PdfBailGeneratorService.java:30-117`, `TransactionService:459-468` | Erreur PDF silencieusement avalée (`LIBERE` sans PDF), fuite `FileOutputStream`, stockage éphémère, absence de n° contrat malgré libellé UI. |
| `SIG-02` | J | Signature électronique locataire/propriétaire & intégrité | **PARTIELLE** | `TransactionService:360-430` | Simple texte horodaté sans signature cryptographique ni hash ; **bug P1 (`414-416`)** : signature admin après propriétaire rétrograde `statutBail` ; signature permise avant paiement. |
| `MSG-01` | K | Messagerie temps réel STOMP + persistance + isolation | **EFFECTIVE ET VALIDÉE** | `MessagerieStompController`, `MessagerieRestController`, `MessagerieStompIntegrationTest` | Isolation participants validée par tests d'intégration. |
| `MSG-02` | K | Sécurité session active WS, lecture temps réel & erreurs UI | **PARTIELLE** | `WebSocketAuthInterceptor:61-95`, `ConversationsTab.tsx:29,74`, `ChatDrawer.tsx:58` | `isActif()` non vérifié sur `SUBSCRIBE`/`SEND` ; erreurs WS ignorées dans `ConversationsTab` ; tri SQL incohérent avec Swagger. |
| `ADM-01` | L | Notation logements (après visite effectuée) & réputation | **EFFECTIVE ET VALIDÉE** | `AvisController`, `AvisService:38-98`, `AvisAndSignalementIntegrationTest` | Contrôle visite `EFFECTUE` + anti-doublon ; note : `obtenirAvisAnnonce` ne filtre pas `supprime=false`. |
| `ADM-02` | L | Signalement d'arnaques & modération admin | **PARTIELLE** | `SignalementService:35-79`, `AdminSignalementsPage.tsx:14-30` | Aucun anti-doublon/anti-auto-signalement/rate-limit ; `AdminSignalementsPage` sans garde frontend ni UI pagination. |
| `ADM-03` | L | Indicateurs financiers & statistiques admin | **EFFECTIVE ET VALIDÉE** | `AdminStatistiquesService.java`, `AdminStatistiquesRepository:51-58`, `AdminDashboardPage.tsx` | Calculs séquestre/frais exacts (`BLOQUE`/`LIBERE`) ; stats annonces/modération non filtrées par période. |
| `ADM-04` | L | Gestion utilisateurs, rôles & tableaux de bord | **EFFECTIVE ET VALIDÉE** | `AdminUtilisateurController`, `UtilisateurManagementService`, `DashboardPage.tsx` | Protections anti-auto-rétrogradation et dernier admin ; garde `DashboardPage` ok. |
| `TRV-01` | M | API REST documentée via Swagger / OpenAPI 3.0 | **PARTIELLE** | `docs/openapi.json` vs 19 `@RestController` | **54 / 57 chemins (94,74 %)** et **61 / 64 opérations (95,31 %)** ; 3 endpoints remboursement/admin transactions manquants. |
| `TRV-02` | M | Interface Mobile-first responsive >= 360 px | **NON EFFECTIVE** | `OfflineIndicator.tsx:23`, `Header.tsx:62`, `lot3-performance-optimizations.spec.ts` Test 13 | **Échec réel Playwright** à `375x667` sur `/annonce/:id` (`scrollWidth > clientWidth` dû à `OfflineIndicator`) + nav mobile cachée `< 768px`. |
| `TRV-03` | M | Accessibilité (clavier, contrastes, labels, ARIA) | **PARTIELLE** | `Header.tsx:181-185`, `PropertyDetailPage.tsx:490,581+`, `DashboardPage.tsx:293` | Bouton Connexion mobile sans `aria-label`, saut `h1->h3`, `<main>` imbriqué, `<label>` sans `htmlFor`, contraste `terracotta-500` (~4,05:1). |
| `TRV-04` | M | Mode offline partiel (Service Worker) | **EFFECTIVE ET VALIDÉE** | `vite.config.ts:10-100`, `OfflineIndicator.tsx`, mesure Playwright (`swRegistered: true`) | Cache public `StaleWhileRevalidate` excluant auth/cookies/admin ; manque icônes PNG 192/512 dans manifeste (`favicon.ico` absent). |
| `TRV-05` | M | Tests automatisés >= 60 % & lint | **PARTIELLE** | `mvn test` (275/275 OK, 88,51 % lignes JaCoCo), `frontend/package.json:9` | `npm run lint` échoue (`eslint` introuvable) ; **0 test unitaire** frontend ; Test 13 E2E Lot3 échoue. |
| `DEV-01` | N | Déploiement cloud & reproductibilité Docker | **BLOQUÉE** | `render.yaml`, `docker-compose.yml:26-33`, `backend/Dockerfile`, `docs/audit/render_l6.json` | État live Render non vérifié (timeout backend 30/10) ; `ecourtier-backend` local crash ; 0 workflow CI/CD ; piège `IA_PROVIDER:-gemini`. |
| `DOC-01` | N | Documentation README, UML & collection Postman | **PARTIELLE** | `README.md:48-188,214-316`, `docs/E-Courtier.postman_collection.json` | UML Mermaid `usecaseDiagram` invalide + 5/13 entités ; Postman avec 4 routes mortes ; `README.md` omet `./backend/.env` et cite `LangChain4j`/`Bucket4j`. |
| `LIV-01` | N | Présentation orale 10 min | **NON APPLICABLE** | Hors dépôt logiciel | Livrable de soutenance à préparer. |

---

## 7. Résultats détaillés par fonctionnalité

Les constats détaillés par domaine (`A` à `L`) figurent dans la matrice ci-dessus et dans les sections techniques suivantes, avec mention systématique des écarts entre UI, API et base de données :
- **Éléments purement décoratifs ou non câblés détectés dans le frontend :**
  - `LoginPage.tsx:132` : `<a href="#reset">Mot de passe oublié ?</a>` (ancre morte, aucune route ni API).
  - `ComingSoonPage.tsx:31-36, 262-264` : page non routée dans `App.tsx`, formulaire sans appel API et bouton `"Charte Graphique"` sans gestionnaire.
  - `HomePage.tsx:178-228` : carte vitrine immobilière 100 % statique (`Sacré-Cœur 3`, photo Unsplash).
  - `HomePage.tsx:269-298` : clics sur les cartes quartiers redirigent uniquement sur `city`, en ignorant le nom du quartier.
  - `HomePage.tsx:40` -> `SearchResultsPage.tsx:39-171` : le paramètre naturel `q` est envoyé par la page d'accueil puis supprimé sans effet sur `/recherche`.
  - `SearchResultsPage.tsx:212-213` : envoie `tri=dateCreation&direction=DESC` ignoré par Spring Data (`sort=`).
  - `SearchResultsPage.tsx:554` : fallback vers `'/placeholder.jpg'` inexistant dans `frontend/public/`.
  - `PropertyDetailPage.tsx:627` & `DashboardPage.tsx:183` : badge `<Avatar isVerified />` et libellé `"Proposé par un propriétaire identifié"` affichés inconditionnellement sans vérification KYC des comptes.
  - `DashboardPage.tsx:558-561` : texte `"Frais: 5 000 FCFA"` partiellement codé en dur.

---

## 8. Audit technique backend

- **Points solides :** Architecture en couches propre (`Controller -> Service -> Repository`), `@Transactional` sur les mutations critiques, verrouillage pessimiste (`PESSIMISTIC_WRITE`) et optimiste (`@Version`) sur le séquestre, validation DTO via Jakarta Bean Validation, gestion centralisée des exceptions (`GlobalExceptionHandler`), contrôle des droits par `@PreAuthorize` + services de sécurité dédiés, 17 migrations Flyway cohérentes validées sur PostgreSQL 16 (`ddl-auto: validate`).
- **Faiblesses techniques critiques / majeures :**
  - `JwtService.java:89-104` ne contrôle pas le type de token (`ACCESS` vs `REFRESH`).
  - `AnnonceService.modifierAnnonce:143` accepte un changement arbitraire de `statutPublication` par le propriétaire sans vérifier l'état courant (`SUSPENDUE`, `LOUEE`, `VENDUE`, `RESERVEE`).
  - `TransactionService` (`360-468`) mélange responsabilités de signature, libération séquestre et génération PDF sur disque dans un `try/catch` qui masque les erreurs de génération PDF, tout en contenant un bug de régression d'état pour l'admin (`414-416`).
  - `PdfBailGeneratorService.java:45, 74-80` : ressource `FileOutputStream` hors `try-with-resources` et risque de `NullPointerException` sur `transaction.getAnnonce()`.
  - `RendezVousService.java:120-211` : absence de machine à états stricte sur confirmation/annulation (un RDV `EFFECTUE` peut être annulé ou confirmé).
  - `SignalementService.java:56-60` : absence d'`@EntityGraph` sur `listerSignalements` (requêtes N+1 sur `annonce` et `auteur`).
  - `WebSocketConfig` / `MessagerieStompController.java:26-39` : absence de `@Valid` et de `@MessageExceptionHandler` sur les messages STOMP.
  - Chargement `.env` : aucune prise en charge native par Spring Boot hors Docker ; `application.yml:52` définit `ecourtier.ia.groq.api-key: ${GROQ_API_KEY:}` à chaîne vide, masquant le fallback `${ecourtier.ia.api-key:}` utilisé dans `GroqClientService.java:34-35`.

---

## 9. Audit technique frontend

- **Points solides :** Séparation claire pages/composants/services/stores, `React.lazy()` + découpage Rollup des chunks lourds (`Leaflet`, `Recharts`, `Lightbox`, `Motion`), stockage JWT en `sessionStorage` (et non `localStorage`), garde asynchrone R1 robuste dans `AiAssistantWidget.tsx` (`AbortController` + `generationRef` + `activeSessionRef`), cookies `HttpOnly` activés via `credentials: 'include'`.
- **Points défaillants :**
  - **Régression mobile `375x667` confirmée** : `OfflineIndicator.tsx:23` provoque un débordement horizontal sur `/annonce/:id` dès que `navigator.onLine` est faux (`lot3` Test 13 en échec).
  - **Navigation mobile incomplète** (`Header.tsx:62`) : liens `Favoris`, `Messages`, `Administration`, `Espace Partenaire` masqués sous 768 px sans menu mobile.
  - **Accessibilité** : icône `Connexion` sans `aria-label` sous 640 px (`Header.tsx:181-185`), sauts `h1 -> h3`, `<main>` imbriqué (`DashboardPage.tsx:293`), `<label>` sans `htmlFor`, contraste insuffisant sur `terracotta-500` (~4,05:1).
  - **Incohérences UI / API :** `PropertyDetailPage.tsx:272` ne filtre pas les transactions `REMBOURSE`, `PropertyDetailPage.tsx:1088` et `DashboardPage.tsx:657` laissent `selectedTx` périmé après paiement/signature, `AdminSignalementsPage.tsx:14-30` n'a ni garde `ADMIN` frontend ni contrôles de pagination, `ConversationsTab.tsx:29,74` ignore les erreurs STOMP.
  - **Assets & Qualité :** `/placeholder.jpg` manquant, `favicon.ico` inexistant dans `public/` mais déclaré dans `vite.config.ts:94`, `npm run lint` inopérant (`eslint` manquant), 0 test unitaire/composant React.

---

## 10. Audit de sécurité

### Vulnérabilités et risques confirmés

1. **[P0] Réutilisation d'un Refresh Token comme Access Token (`JwtService.java:89-104`)**
   - *Preuve :* `generateAccessToken` et `generateRefreshToken` posent `"type": "ACCESS"` / `"REFRESH"` (`38-52`), mais `isTokenValid(token, email)` et `isTokenValid(token)` vérifient uniquement `extractUsername` et `!isTokenExpired` (`89-104`).
   - *Scénario & Impact :* Tout attaquant interceptant un `refreshToken` (durée 7 jours vs 1h pour l'access token) peut l'envoyer directement dans `Authorization: Bearer <refreshToken>` sur tous les endpoints REST (`JwtAuthenticationFilter.java:55`) et STOMP CONNECT (`WebSocketAuthInterceptor.java:42`), élargissant la fenêtre d'exploitation d'un facteur 168x.
2. **[P0] Contournement de suspension d'annonce pour fraude (`AnnonceService.java:143`, `AnnonceUpdateDTO.java:72`)**
   - *Preuve :* `PUT /api/v1/annonces/{id}` (`@PreAuthorize("@annonceSecurityService.isOwnerOrAdmin...")`) affecte directement `dto.getStatutPublication()` sans vérifier si l'annonce a été mise à `SUSPENDUE` par un admin (`SignalementService.java:70-72`) ou verrouillée en `RESERVEE`/`LOUEE`/`VENDUE` par `TransactionService`.
   - *Scénario & Impact :* Un propriétaire frauduleux dont l'annonce est suspendue après signalement appelle `PUT /api/v1/annonces/{id}` avec `{"statutPublication":"DISPONIBLE", ...}` et remet immédiatement son annonce frauduleuse en ligne sans intervention admin.
3. **[P1] Maintien d'un compte désactivé sur session WebSocket active (`WebSocketAuthInterceptor.java:61-95`)**
   - *Preuve :* `isActif()` est vérifié sur `CONNECT` (`44-47`), mais ni sur `SUBSCRIBE`, ni sur `SEND`, ni dans `MessagerieService.envoyerMessage` (`107-108`).
   - *Scénario & Impact :* Un client STOMP déjà connecté reste capable de lire et d'envoyer des messages après désactivation administrative de son compte (`PUT /admin/utilisateurs/{id}/statut`).
4. **[P1] Substitution de signature par un administrateur (`TransactionService.java:412-423`)**
   - *Preuve :* `@PreAuthorize("@transactionSecurityService.isStakeholderOrAdmin...")` autorise un `ADMIN` sur `/bail/signer`, et le `else` signe au nom du locataire/propriétaire sans tracer l'identité du signataire réel.
5. **[P2] Contournement du filtre anti-injection IA par saut de ligne & balise (`RuleBasedCriteriaExtractor.java:19-32`, `GroqClientService.java:131,178-206`)**
   - *Preuve :* Les regex utilisent `.*` sans `Pattern.DOTALL` ; `\n` neutralise la détection (`"ignore\nprevious instructions"`). De plus, `userMessage` n'échappe pas `</user_query_untrusted>` et les titres d'annonces en base sont injectés bruts dans le prompt système (risque d'injection indirecte).
6. **[P2] Absence de révocation serveur des JWT au logout & non-rotation des refresh tokens (`AuthController.java`, `AuthService.java:112-127`)**
   - *Impact :* Un token volé avant déconnexion reste valide côté API jusqu'à son expiration.
7. **[P2] Absence d'anti-spam / unicité sur les signalements (`SignalementService.java:35-53`)**
   - *Impact :* Un utilisateur authentifié peut soumettre un nombre illimité de signalements contre la même annonce.

### Contrôles de sécurité validés
- Protection IDOR/BOLA solide sur documents de vérification (`AnnonceController:188-213`), transactions (`TransactionSecurityService`), RDV (`RendezVousSecurityService`), messagerie REST (`ConversationSecurityService`) et conversations IA anonymes (cookie `HttpOnly` SHA-256 `V17` + R1 frontend).
- Uploads sécurisés : taille plafonnée (5 Mo / 10 Mo), magic bytes, noms UUID, défenses anti-path-traversal et anti-symlinks dans `UploadController`.
- Rate limiting sur `/auth/connexion` (5 essais / 15 min) et `/ia/**` (10 req/min anonyme, 30 req/min authentifié ; non distribué en mémoire).
- CORS explicite sans `*` avec `allowCredentials(true)`. Aucun secret exposé dans le code suivi.

---

## 11. Audit des paiements et intégrations externes

| Intégration | Niveau réel observé | Preuve code / exécution | Limite / Risque |
|---|---|---|---|
| **PostgreSQL 16** | **Opérationnelle en local / test** (non prouvée sur Render live) | Migrations V1–V17 validées sur PostgreSQL 16.15 temporaire (`FlywayFreshMigrationIntegrationTest` 1/1). | Conteneur `ecourtier-db` dev local désynchronisé (manque V16–V17). |
| **Groq / LLM** | **Présente & testée en mock / fallback ; clé locale non testée en live** | `GroqClientService.java`, `GroqClientServiceTest` (mock `RestTemplate`), `.env`/`backend/.env` contiennent `GROQ_API_KEY`. | `application.yml:52` ombre `IA_API_KEY` (`render.yaml`) ; 2 appels séquentiels de 3,5 s max chacun sans retry (risque > 3 s). |
| **Wave Mobile Money** | **Simulation locale en mémoire (Mock)** | `WaveClientMock.java:14-191` (`ConcurrentHashMap`), aucun appel HTTP Wave, 0 webhook, 0 signature HMAC. | **Bloquant :** état effacé au redémarrage serveur ; remboursement impossible après reboot ; aucun versement réel lors de `LIBERE`. |
| **Passerelle SMS** | **Simulation locale (DB + Log)** | `SmsGatewayLogImpl.java:19-53` (`statut = "SIMULE"` dans `notifications_sms`). | Aucun fournisseur SMS (Twilio, Orange, SendFlash), aucun mécanisme de reprise. |
| **Stockage fichiers** | **Disque local éphémère** | `FileStorageService.java` (`uploads/annonces`, `uploads/documents`), `PdfBailGeneratorService` (`uploads/baux`). | Perte des photos, preuves et baux PDF à chaque redéploiement de conteneur sans volume persistant (`render.yaml`). |
| **Cartographie Leaflet / OSM** | **Fonctionnelle côté client** | `LeafletMap.tsx`, `LocationPickerMap.tsx`, `tiles.openstreetmap.org`. | Dépend du réseau client ; hors cache offline. |
| **WebSocket STOMP** | **Fonctionnelle en test d'intégration** | `WebSocketConfig.java`, `MessagerieStompController.java`, `MessagerieStompIntegrationTest`. | Reconnexion en boucle sur erreur JWT, `isActif()` non re-vérifié en session. |
| **Render Cloud** | **Configurée, non validée opérationnellement** | `render.yaml`, `backend/Dockerfile` ; `docs/audit/render_l6.json` (30/10 : timeout backend). | Absence de preuve live actuelle ; manque `GROQ_API_KEY` et disque persistant dans `render.yaml`. |

---

## 12. Audit des tests

### État réel des exécutions

1. **Backend — Exécutés et réussis (`mvn test`, 13/11/2026) :**
   - **275 tests exécutés, 0 échec, 0 erreur, 1 ignoré** (`FlywayFreshMigrationIntegrationTest` ignoré uniquement en sandbox sans réseau hôte).
   - **JaCoCo réel (`backend/target/site/jacoco/jacoco.xml`) :** **Instructions `85,29 %`** (`15 227 / 17 853`), **Lignes `88,51 %`** (`3 189 / 3 603`), **Branches `61,63 %`** (`1 402 / 2 275`). Dépasse les 60 % globaux, mais `domain/annonce/controller` n'est qu'à 36,4 % sur les branches et `domain/ia/client` à 48,8 %.
2. **Backend + PostgreSQL 16.15 temporaire (12/11/2026 sur même état de code) — Exécutés et réussis :**
   - `FlywayFreshMigrationIntegrationTest` : **1/1 réussi** (V1–V17 + `ddl-auto: validate`).
   - `AnonymousConversationSecurityIntegrationTest` : **14/14 réussis** (`ddl-auto: validate`).
3. **Frontend Playwright E2E exécutés :**
   - `lot3-performance-optimizations.spec.ts` (**exécuté le 13/11/2026**) : **12 réussis, 1 échoué** (Test 13 responsive mobile `375x667` sur `/annonce/:id` — échec réel causé par `OfflineIndicator` débordant horizontalement).
   - `chatbot-groq-widget.spec.ts` (**exécuté le 12/11/2026 sur même build**, mocké) : **5/5 réussis**.
   - `chatbot-groq-r1-race.spec.ts` (**exécuté le 12/11/2026 sur même build**, timing mocké) : **6/6 réussis**.
   - `chatbot-groq-fullstack.spec.ts` (**exécuté le 12/11/2026 sur même build**, full-stack réel Spring Boot `:8089` + PostgreSQL 16 `:5439`) : **4/4 réussis**.
4. **Frontend qualité / outillage en échec ou absent :**
   - `npm run build` / `npx tsc --noEmit` : **réussi** (0 erreur TypeScript).
   - `npm run lint` (**exécuté le 12/11/2026**) : **ÉCHEC** (`eslint: commande introuvable`, exit 127).
   - **Tests unitaires / composants React :** **0** (aucun framework Vitest/Jest installé).
5. **Tests Playwright non exécutés dans cet audit :**
   - `recherche-filtres.spec.ts`, `capture-audit-screenshots.spec.ts` (nécessitent le serveur Docker `:3000` + `:8080` dont le backend est actuellement en crash-loop), `phase9f-dynamic-validation.spec.ts` et `phase9h-admin-refund.spec.ts` (nécessitent un serveur preview sur `:4173`).

---

## 13. Audit du déploiement

- **`backend/Dockerfile` :** Build multi-étapes propre (`maven:3.9.9-eclipse-temurin-21-alpine` -> `eclipse-temurin:21-jre-alpine`, utilisateur non-root `appuser`).
- **`frontend/Dockerfile` :** Image orientée développement (`node:20-alpine` avec `CMD ["npm", "run", "dev"]`), inadaptée à une production statique/Nginx (aucun fichier Nginx présent).
- **`docker-compose.yml` :**
  - Exige `./backend/.env` (`lines 26-27`), étape absente de la documentation d'installation `README.md`.
  - Ligne `33` (`IA_PROVIDER: ${IA_PROVIDER:-gemini}`) écrase `IA_PROVIDER=groq` de `./backend/.env` si le `.env` racine n'existe pas.
- **`render.yaml` :**
  - Blueprint Free (Francfort) pour `ecourtier-backend`, `ecourtier-frontend` (`static` avec rewrite `/* -> /index.html`) et `ecourtier-db` (PostgreSQL 16).
  - **Défauts pour la production :** absence de disque persistant (`disk:` absent sur Render -> perte des photos, documents de vérification et baux PDF à chaque redémarrage), absence de `GROQ_API_KEY` (`IA_API_KEY` seul est ombré par `application.yml:52`), mise en veille du plan Free (`> 90 s` sur la sonde `render_l6.json`), aucune pipeline CI/CD (`.github/workflows` inexistant).

---

## 14. Anomalies P0 à P3

| Réf | Gravité | Module | Description & Preuve | Impact | Recommandation & Dépendances |
|---|---|---|---|---|---|
| **ANOM-P0-01** | **P0 — CRITIQUE** | Sécurité / Auth | `JwtService.isTokenValid` (`JwtService.java:89-104`) n'exige pas `"ACCESS".equals(type)`. | Un `refreshToken` (7j) donne accès complet aux endpoints REST et WebSocket. | Vérifier strictement `"ACCESS".equals(claimType)` dans `isTokenValid` ; ajouter tests négatifs. |
| **ANOM-P0-02** | **P0 — CRITIQUE** | Annonces / Anti-fraude | `AnnonceService.modifierAnnonce:143` applique librement `dto.getStatutPublication()` sans rôle ni contrôle d'état. | Un propriétaire peut réactiver en `DISPONIBLE` une annonce `SUSPENDUE` (arnaque) ou `LOUEE`/`VENDUE`/`RESERVEE`. | Interdire au propriétaire la sortie de `SUSPENDUE` et les transitions réservées à l'admin/séquestre (`PUT /annonces/{id}`). |
| **ANOM-P0-03** | **P0 — CRITIQUE** | Transactions / Séquestre | Aucune annulation ni expiration TTL des transactions `INITIALISE`, bloquées à vie par `uq_transactions_annonce_active` (`V14:176-178`). | Une transaction abandonnée verrouille définitivement le bien pour tous les utilisateurs, sans déblocage admin API. | Ajouter endpoint d'annulation (locataire/admin sur `INITIALISE`) + cron `@Scheduled` d'expiration (ex. 24h) passant `dossier_actif = false`. |
| **ANOM-P1-01** | **P1 — MAJEUR** | Signature / Transactions | Régression `statutBail` (`TransactionService.java:414-416` : admin écrase `SIGNE_PROPRIETAIRE` en `SIGNE_LOCATAIRE`), signature avant paiement (`365-386`) et substitution admin sans audit (`412-423`). | Bloque la libération du séquestre et permet de signer au nom d'autrui ou sans fonds bloqués. | Interdire la signature admin par substitution, exiger `statutSequestre == BLOQUE` et corriger le calcul de transition vers `SIGNE_COMPLET`. |
| **ANOM-P1-02** | **P1 — MAJEUR** | Paiement Wave | Simulation purement en mémoire (`WaveClientMock.java:14-191`), 0 webhook, 0 signature HMAC, 0 versement réel à la libération (`verifierEtDeclencherLiberation`). | Perte des paiements sandbox au reboot serveur ; aucune intégration Wave Sandbox réelle. | Implémenter un client HTTP Wave Sandbox + webhook HMAC vérifié (ou persister la simulation en BDD si sandbox indisponible). |
| **ANOM-P1-03** | **P1 — MAJEUR** | SMS | `SmsGatewayLogImpl.java:19-53` ne fait qu'écrire en base (`statut = "SIMULE"`) + logs. | Aucun SMS de confirmation ou rappel J-1 réellement envoyé. | Connecter un fournisseur SMS Sandbox (Twilio/Orange/SendFlash) avec gestion d'échec/retry. |
| **ANOM-P1-04** | **P1 — MAJEUR** | Recherche / Filtres | Proximité (`ProximiteTag`) sans calcul GPS, champ `q` supprimé (`SearchResultsPage`), `tri=` ignoré (`Pageable` attend `sort=`), `etage`/`equipements` non filtrables, `RESERVEE` non exclue. | Critères de recherche incomplets et résultats incohérents entre Home Hero, filtres avancés et backend. | Implémenter `q`, `etage`, `equipements`, calcul Haversine pour proximité, corriger `sort=`, exclure `RESERVEE` par défaut. |
| **ANOM-P1-05** | **P1 — MAJEUR** | Responsive / UX | Échec avéré `lot3` Test 13 (`375x667` sur `/annonce/:id`) dû à `OfflineIndicator.tsx:23` + navigation mobile cachée `< 768px` (`Header.tsx:62`). | Débordement horizontal mobile en mode offline et liens Favoris/Messages/Admin inaccessibles sur mobile. | Ajouter `right-4 max-w-[calc(100vw-2rem)]` sur `OfflineIndicator` et ajouter un menu tiroir mobile dans `Header.tsx`. |
| **ANOM-P1-06** | **P1 — MAJEUR** | Sécurité / WebSocket | `WebSocketAuthInterceptor:61-95` ne re-vérifie pas `isActif()` sur `SUBSCRIBE`/`SEND` ; `@Valid`/`@MessageExceptionHandler` absents de `MessagerieStompController`. | Compte désactivé conserve son accès WebSocket actif jusqu'à fermeture du socket. | Re-vérifier `utilisateur.isActif()` sur `SUBSCRIBE`/`SEND` et révoquer la session WS lors de `changerStatut`. |
| **ANOM-P1-07** | **P1 — MAJEUR** | Auth / UX | Lien mort `<a href="#reset">` (`LoginPage.tsx:132`) ; déconnexion sans révocation JWT serveur (`AuthController`). | Mot de passe oublié inutilisable ; tokens valides après logout. | Implémenter flux reset password (ou retirer le lien mort) + liste de révocation JWT / rotation refresh token. |
| **ANOM-P2-01** | **P2 — IMPORTANT** | Signature / PDF | Erreur PDF silencieusement avalée (`TransactionService:459-468`), fuite `FileOutputStream`, stockage éphémère, absence de n° contrat et de hash SHA-256 (`PdfBailGeneratorService`). | Transaction `LIBERE` sans contrat téléchargeable ; perte sur redéploiement Render. | Utiliser `try-with-resources`, stocker hash SHA-256, ajouter n° contrat, rendre génération robuste et persistante (S3/volume). |
| **ANOM-P2-02** | **P2 — IMPORTANT** | IA Groq | `application.yml:52` ombre `IA_API_KEY`, `docker-compose.yml:33` force `gemini`, contournement regex `\n` (`RuleBasedCriteriaExtractor:19-32`) et `</user_query_untrusted>`, 2 appels 3,5s risquant > 3s. | Clé Groq ignorée sur Render, injection prompt sur multi-lignes, dépassement potentiel du SLA 3s. | Unifier `IA_API_KEY`/`GROQ_API_KEY`, activer `Pattern.DOTALL`, échapper balises XML/titres, fusionner ou paralléliser appels. |
| **ANOM-P2-03** | **P2 — IMPORTANT** | Vérification / UI | Badges `<Avatar isVerified />` et `"propriétaire identifié"` codés en dur (`PropertyDetailPage:627`, `DashboardPage:183`) sans KYC utilisateur. | Risque de tromper l'utilisateur sur la fiabilité d'un compte ou d'une annonce non vérifiée. | Lier le badge au `statutVerification === 'VERIFIEE'` ou ajouter une vraie vérification KYC utilisateur. |
| **ANOM-P2-04** | **P2 — IMPORTANT** | RDV & Modération | Pas de modification/report de RDV ni garde d'état `EFFECTUE` (`RendezVousService`) ; `SignalementService` sans unicité/rate-limit ni `@EntityGraph` ; `AdminSignalementsPage` sans garde frontend. | Transitions incohérentes, spam de signalements possible et requêtes N+1. | Ajouter endpoint de replanification RDV, gardes d'état, contrainte unicité signalement + garde `ADMIN` frontend. |
| **ANOM-P2-05** | **P2 — IMPORTANT** | Qualité / Livrables | `npm run lint` cassé (`eslint` absent), 0 test unitaire frontend, OpenAPI à 94,74 % (3 routes manquantes), UML invalide (`usecaseDiagram`, 5/13 entités), Postman obsolète. | Dégradation qualité frontend, documentation non alignée avec l'API. | Installer/configurer ESLint + Vitest, régénérer `docs/openapi.json`, corriger Postman/README/UML. |
| **ANOM-P2-06** | **P2 — IMPORTANT** | UX / Transactions | `PropertyDetailPage:272` ne filtre pas `REMBOURSE` sur `userTransactions`, `onRefresh` garde `selectedTx` obsolète (`PropertyDetailPage:1088`, `DashboardPage:657`). | Empêche l'initiation après remboursement et fige l'état du modal après action. | Filtrer `dossierActif && statutSequestre !== 'REMBOURSE'` et synchroniser l'état modal comme `AdminTransactionsPage`. |
| **ANOM-P3-01** | **P3 — MINEUR** | Accessibilité / PWA / Annonces | Connexion mobile sans `aria-label` (`Header:181`), sauts `h1->h3`, `<main>` imbriqué, contraste `terracotta-500`, `EditAnnonceModal` sans photos/360°, `/placeholder.jpg` & `favicon.ico` absents, `adminId` non tracé sur vérif, `avis` ignore `supprime=false`. | Ergonomie, accessibilité WCAG et complétude édition réduites. | Corriger attributs ARIA/contrastes/landmarks, ajouter photos dans `EditAnnonceModal`, corriger assets publics et traçabilité admin. |

---

## 15. Score de maturité détaillé

**Méthode de calcul :** Note pondérée sur 100 points distinguant rigoureusement les fonctionnalités **effectivement validées** des fonctionnalités **simulées**, **cassées/contournables** ou **non vérifiées**. Aucun point complet n'est attribué à un mock (Wave, SMS) ou à une fonction affectée d'une faille P0/P1.

### Taux objectifs calculés (sur 49 exigences techniques applicables)
- **Taux d'exigences EFFECTIVES ET VALIDÉES :** $17 / 49 \times 100 =$ **34,69 %**
- **Taux d'exigences PARTIELLES :** $19 / 49 \times 100 =$ **38,78 %**
- **Taux de fonctionnalités SIMULÉES / MOCKÉES :** $2 / 49 \times 100 =$ **4,08 %**
- **Taux de fonctionnalités ABSENTES :** $4 / 49 \times 100 =$ **8,16 %**
- **Taux de fonctionnalités NON EFFECTIVES :** $4 / 49 \times 100 =$ **8,16 %**
- **Taux d'exigences NON VÉRIFIÉES (Implémentées non validées / Bloquées) :** $3 / 49 \times 100 =$ **6,12 %**

### Pondération et maturité par domaine (/ 100)

| Domaine | Poids | Note obtenue | Maturité (%) | Justification |
|---|---:|---:|---:|---|
| Architecture & Backend Java/Spring | 12 | 9,0 | 75,0 % | Couches propres, verrous concurrents solides ; pénalisé par `modifierAnnonce`, `signerBail`, `.env`/props |
| Base de données & Migrations Flyway | 8 | 7,0 | 87,5 % | 17 migrations validées sur PG16, index uniques solides ; dev DB locale désynchronisée, manque contrainte signalements/TTL |
| Frontend React / UX / Responsive | 12 | 7,0 | 58,3 % | Riche UI et R1 solide ; échec mobile Test 13 (`OfflineIndicator`), liens morts `#reset`/`q`/`tri`, `EditAnnonceModal` incomplet |
| Sécurité globale & RBAC | 15 | 8,0 | 53,3 % | Très bon durcissement R2/IDOR/uploads, mais lourdement sanctionné par **3 failles P0** (`JwtService` REFRESH, statut `SUSPENDUE`, verrouillage `INITIALISE`) |
| Annonces, Vérification, Filtres & Cartes | 12 | 7,5 | 62,5 % | Vérification et CRUD robustes ; proximité non GPS, `etage`/`equipements`/`tri`/`q` manquants, badge user KYC codé en dur |
| Intelligence artificielle (Chatbot Groq) | 10 | 6,5 | 65,0 % | Multi-tours DB + R1 + R2 validés full-stack ; Groq réel non validé en live et failles regex `\n`/XML |
| Rendez-vous & SMS | 6 | 3,0 | 50,0 % | RDV conflits OK, mais sans replanification ni garde `EFFECTUE`, et SMS 100 % simulé DB/log |
| Paiements, Séquestre & Signature PDF | 12 | 4,0 | 33,3 % | Machine de calcul/libération solide, mais Wave mock mémoire, 0 webhook, deadlock `INITIALISE`, signature non crypto + bug admin |
| Tests & Qualité | 7 | 5,0 | 71,4 % | 275 tests backend (88,51 % JaCoCo), 4/4 full-stack E2E, mais Test 13 E2E en échec, `eslint` cassé et 0 test unitaire frontend |
| DevOps, Déploiement & Livrables | 6 | 2,5 | 41,7 % | `Dockerfile`/`render.yaml` présents, mais état live non prouvé, 0 CI/CD, UML/Postman obsolètes |
| **TOTAL MATURITÉ GLOBALE** | **100** | **59,5 / 100** | **59,5 %** | **Maturité académique/prototype avancée, mais inapte à la production en l'état** |

- **Distinction obligatoire :**
  - *Maturité technique (architecture/code) :* bonne structure modulaire (~70 %).
  - *Maturité académique (soutenance L3) :* présentable sur les flux principaux et le chatbot, mais risquée sur les questions critiques (proxi GPS, signature, Wave/SMS simulés, UML/Postman à mettre à jour).
  - *Aptitude à la production :* **non apte** (blocages sécurité P0, pertes de fichiers sans stockage persistant, paiements/SMS factices).

---

## 16. Fonctionnalités réellement prêtes pour une démonstration académique

- Inscription et connexion multi-rôles (`LOCATAIRE`, `PROPRIETAIRE`, `ADMIN`), changement de mot de passe (`/mon-compte`).
- Création d'annonce (avec photos uploadées, GPS Sénégal, équipements, tags de proximité et pièces justificatives), suppression logique et consultation détaillée avec carte Leaflet.
- Vérification administrative complète des pièces justificatives PDF/images (`AdminVerificationPage`) et attribution du badge vérifié.
- Recherche multicritère de base (ville, quartier, type, budget, surface min, pièces, meublé, animaux, vérifiées, pagination) et gestion des favoris.
- Chatbot Samba (sur moteur déterministe ou avec clé Groq exportée en variable d'environnement), restauration après F5, isolation multi-utilisateurs et protection R1/R2.
- Workflow de démonstration (sous réserve d'éviter les scénarios d'abandon/admin-signature) : prise de RDV -> confirmation -> initiation séquestre (5 000 FCFA frais) -> paiement simulé -> signature des 2 parties -> libération + PDF téléchargeable.
- Messagerie temps réel WebSocket, avis après visite, signalements, statistiques financières admin et mode offline PWA des annonces publiques.

---

## 17. Fonctionnalités utilisables dans un environnement de test

Toutes les fonctionnalités ci-dessus plus : tests d'intégration API/BDD (H2 et PostgreSQL 16 temporaire avec `SPRING_FLYWAY_ENABLED=true`), tests de montée en charge SQL (`PerformanceANO11Test`), simulation Wave et simulation SMS (en gardant à l'esprit que `WaveClientMock` est volatil au redémarrage).

---

## 18. Fonctionnalités non prêtes pour la production

- Paiement Wave réel (aucun client HTTP Wave Sandbox, aucun webhook HMAC, aucune réconciliation/remboursement réel).
- Envoi réel de SMS (aucun connecteur Twilio/Orange/SendFlash).
- Authentification JWT & WebSocket (accepte les refresh tokens en accès REST/WS, pas de révocation de session au logout, compte désactivé non coupé sur socket ouvert).
- Gestion d'états des annonces et transactions (contournement `SUSPENDUE` via `PUT /annonces/{id}`, verrouillage permanent d'annonce sur `INITIALISE` abandonné, régression admin signature bail).
- Stockage des médias/PDF sur Render (disque éphémère sans volume/S3 -> perte de données).
- Signature électronique à valeur probante (absence de scellement/hash/certificat) et accessibilité/responsive mobile en mode offline.

---

## 19. Plan de remédiation priorisé

1. **Priorité P0 — Correctifs critiques immédiats (bloquants sécurité & blocages métier) :**
   - Restreindre `JwtService.isTokenValid` aux seuls tokens dont le claim `"type"` vaut `"ACCESS"` (+ test d'intégration négatif).
   - Verrouiller `AnnonceService.modifierAnnonce` : interdire la modification directe de `statutPublication` vers `DISPONIBLE` si l'annonce est `SUSPENDUE`, `RESERVEE`, `LOUEE` ou `VENDUE` (sauf workflow dédié).
   - Ajouter `POST /api/v1/transactions/{id}/annuler` (sur `INITIALISE` par demandeur/admin) + tâche `@Scheduled` d'expiration automatique des `INITIALISE` abandonnées (`dossier_actif = false`).
2. **Priorité P1 — Correctifs majeurs fonctionnels, sécurité WS & responsive :**
   - Corriger `TransactionService.signerBail` : supprimer la signature par substitution `ADMIN`, imposer `statutSequestre == BLOQUE`, corriger le calcul vers `SIGNE_COMPLET`, rendre la génération PDF transactionnelle/rejouable (`try-with-resources`), ajouter un n° de contrat et un hash SHA-256.
   - Re-vérifier `isActif()` dans `WebSocketAuthInterceptor` (`SUBSCRIBE`/`SEND`) et ajouter `@Valid`/`@MessageExceptionHandler` dans `MessagerieStompController`.
   - Corriger `OfflineIndicator.tsx:23` (`right-4 max-w-[calc(100vw-2rem)]`) et ajouter le menu de navigation mobile dans `Header.tsx` pour faire repasser `lot3` Test 13.
   - Aligner recherche/filtres : traduire `tri`/`direction` en `sort=` (ou ajouter sélecteur de tri UI), exploiter le paramètre `q`, exclure `RESERVEE` par défaut, implémenter le calcul Haversine de proximité (ou documenter explicitement la limitation) et ajouter `etage`/`equipements`.
   - Corriger ou retirer le lien mort `<a href="#reset">` dans `LoginPage.tsx` ; retirer les badges `isVerified` codés en dur sur `PropertyDetailPage` et `DashboardPage`.
   - Décider/implémenter le branchement réel (ou le statut Sandbox assumé) de Wave (webhooks HMAC) et d'une passerelle SMS.
3. **Priorité P2 / P3 — Robustesse IA, OpenAPI, Outillage & Livrables académiques :**
   - Corriger le binding `GROQ_API_KEY` / `IA_API_KEY` dans `application.yml`, ajouter `Pattern.DOTALL` et l'échappement XML dans `RuleBasedCriteriaExtractor` / `GroqClientService`, et réaliser un benchmark réel (< 3s) avec une clé Groq active.
   - Régénérer `docs/openapi.json` (100 % des 57 chemins), mettre à jour `E-Courtier.postman_collection.json`, corriger les diagrammes UML et les instructions `./backend/.env` dans `README.md`.
   - Réparer `npm run lint` (`eslint`), ajouter des tests unitaires Vitest frontend, corriger les défauts WCAG (`aria-label` mobile, `h1->h2`, `<main>` unique) et valider un déploiement Render complet avec stockage persistant / S3.

---

## 20. Conclusion objective et synthèses finales

### Tableau synthétique d'état

| Catégorie | Éléments concernés |
|---|---|
| **Ce qui est réellement terminé** | Inscription/connexion, CRUD annonces & photos sécurisées, dépôt & validation admin des preuves d'annonces, filtres de base & favoris, carte `LocationPickerMap`, persistance/isolation R1+R2 du chatbot IA, calcul & verrouillage concurrentiel du séquestre, messagerie STOMP de base, avis & KPIs financiers admin, Service Worker cache public. |
| **Ce qui reste à finaliser** | Vérification de `type=="ACCESS"` sur JWT, garde `statutPublication`, annulation/TTL des `INITIALISE`, correction régression/prérequis de `signerBail`, flux `q`/`tri`/`etage`/`equipements`, garde WS compte désactivé, `EditAnnonceModal` photos, responsive `OfflineIndicator` + menu mobile, OpenAPI (3 routes), `eslint`, UML/Postman/README, déploiement cloud vérifié. |
| **Ce qui ne fonctionne pas** | Réinitialisation mot de passe (`#reset` lien mort), tri des annonces (`tri=` ignoré), recherche naturelle `q` depuis HomePage, filtre `etage`/`equipements`, `npm run lint` (`eslint` absent), Test 13 responsive mobile `375x667` sur `/annonce/:id`, `ecourtier-backend` local (crash sur `ecourtier-db` dev), image fallback `/placeholder.jpg`. |
| **Ce qui est uniquement simulé** | Paiement Mobile Money Wave (`WaveClientMock` mémoire), rappels & notifications SMS (`SmsGatewayLogImpl` base/logs), extraction IA sans clé Groq exportée (fallback déterministe). |
| **Ce qui présente un risque critique** | **(1)** `refreshToken` accepté comme `accessToken` (`JwtService.java:89-104`) ; **(2)** Propriétaire pouvant réactiver une annonce `SUSPENDUE` (`AnnonceService.java:143`) ; **(3)** Annonce verrouillée à vie par transaction `INITIALISE` abandonnée (`V14:176-178`). |
| **Ce qui empêche une mise en production** | Les 3 risques critiques ci-dessus, absence de Wave/SMS réels et de webhooks HMAC, stockage disque local éphémère sur Render sans volume/S3, absence de révocation JWT au logout et de coupure WS des comptes désactivés, signature PDF non cryptographique et défaut de validation du déploiement cloud. |
