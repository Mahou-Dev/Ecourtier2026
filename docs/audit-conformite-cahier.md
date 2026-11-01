# Rapport d'Audit Qualité & Matrice de Traçabilité — E-Courtier Sénégal

- **Date d'exécution de l'audit** : `2026-10-30` (100 % des preuves ré-exécutées le jour même ; aucun rapport antérieur consulté ou réutilisé)
- **Périmètre audité** : Version locale actuelle (`/home/mahou-dev/Documents/Ecourtier2026`, branche `main` @ `abff552` + arbre de travail local) et déploiement cloud Render (`ecourtier-frontend.onrender.com`, `ecourtier-backend.onrender.com`)
- **Mode d'intervention** : **Observation stricte** — aucun fichier de code applicatif ni de configuration du projet n'a été modifié. Les traces brutes, jeux d'essai et captures d'écran datées sont archivés dans `docs/audit/`.
- **Légende des statuts** : `✅ Conforme` (exigence intégralement satisfaite avec preuve exécutée le `2026-10-30`) · `🟡 Partiel` (implémentation incomplète ou comportant une anomalie avérée) · `❌ Absent` (fonctionnalité non implémentée) · `➖ Hors périmètre` (technologie non retenue dans la stack, avec justification et équivalent)

---

## 1. Matrice de traçabilité complète

### 1.1 Tableau principal (54 exigences du cahier + 4 technologies de la liste commune)

| ID | Exigence | Statut | Preuve exécutée le 2026-10-30 (fichier:ligne, endpoint, test, capture) | Écart constaté (en une phrase) |
| :--- | :--- | :---: | :--- | :--- |
| **C1** | Assistant IA (LLM via API) comprenant des besoins en langage naturel et recommandant des annonces | 🟡 | `LlmExtractionService.java:37-139`, `IAService.java:70-170` ; `POST /api/v1/ia/conversations/{id}/messages` ; tests `LlmExtractionMockServerTest`, `IAIntegrationTest` ; protocole 20 phrases dans `docs/audit/s1_f6_resultats.json` (`[MODE LLM IA]` `gemini-2.5-flash` puis `[MODE FALLBACK IA]`) ; capture `docs/audit/screenshots/02_chatbot_react_s6.png` | La requête SQL de shortlist ignore le critère `etage` (`AnnonceSpecification.java:19-34`), échoue sur les quartiers sans accent/tiret (`Sacre Coeur` -> 0 résultat), bloque toute recommandation tant qu'aucun budget n'est fourni (`IAService.java:93-95`), et bascule dès 5 req/min ou 20 req/jour (HTTP 429 Free Tier) sur un moteur regex à 42,9 % d'exactitude. |
| **C2** | Vérification des annonces (photos, documents, badge) contre la fraude | 🟡 | `AdminAnnonceController.java:34-52`, `AnnonceService.java`, `FileStorageService.java:20-85` ; `POST /api/v1/annonces/{id}/documents`, `PUT /api/v1/admin/annonces/{id}/verifier` (`docs/audit/api_proofs.json` -> `VERIFIEE`) ; tests `MagicBytesValidationTest`, `DocumentVerificationSecurityTest` ; captures `docs/audit/screenshots/06_fiche_annonce_galerie_badge_c2_f2.png`, `docs/audit/screenshots/16a_admin_verification_c2.png` | L'écran `AdminVerificationPage.tsx:174` affiche uniquement le compteur `X fichier(s)` sans aucun lien ni aperçu pour inspecter les pièces avant validation, le backend rejette les fichiers PDF en HTTP 400 (`FileStorageService.java:20`), et le justificatif « photo propriétaire » n'existe pas. |
| **C3** | Workflow sécurisé : visite -> accord -> caution séquestre -> signature -> libération | ✅ | `TransactionService.java:47-200`, `RendezVousService.java:38-108` ; cycle complet exécuté dans `docs/audit/api_proofs.json` (blocage pré-visite `400`, RDV `DEMANDE`->`CONFIRME`->`EFFECTUE`, séquestre `505 000 FCFA` `BLOQUE`, 1re signature maintient `BLOQUE`, 2e signature -> `LIBERE` + `500 000 FCFA` bailleur + bail PDF `1 470 o`) ; test `WorkflowTransactionIntegrationTest` (7/7 passés) ; capture `docs/audit/screenshots/14_dashboard_transactions_modal_c3_f25.png` | Aucun écart sur le flux locatif nominal (les particularités du mode `VENTE` sont tracées sous `F29`). |
| **F1** | Annonces studio/appartement/villa, location ET vente | ✅ | `TypeBien.java`, `ModeTransaction.java`, `CreateAnnonceModal.tsx:165-184` ; créations `POST /api/v1/annonces` (`201 Created`) pour `STUDIO`/`LOCATION`, `APPARTEMENT`/`LOCATION` et `VILLA`/`VENTE` dans `docs/audit/api_proofs.json` ; test `AnnonceIntegrationTest` (5/5) ; capture `docs/audit/screenshots/12_modal_creation_preuves_f14_f5.png` | Aucun écart. |
| **F2** | Photos multiples | ✅ | `AnnonceController.java:133-166`, `PropertyDetailPage.tsx:345-358,756-761`, `CreateAnnonceModal.tsx:71-85,247-275` ; upload multipart de 3 PNG (`201`) et réordonnancement `PUT /api/v1/annonces/{id}/photos/ordre` (`200`) dans `docs/audit/api_proofs.json` ; capture `docs/audit/screenshots/06_fiche_annonce_galerie_badge_c2_f2.png` | Aucun écart. |
| **F3** | Visite 360° optionnelle | ✅ | `AnnonceEntity.java` (`lienVisite360`), `CreateAnnonceModal.tsx:227-232`, `PropertyDetailPage.tsx:332-340,708-735` ; annonce créée avec URL Matterport (`201`, `docs/audit/api_proofs.json`) et modale iframe 360° ouverte et capturée dans `docs/audit/screenshots/07_fiche_annonce_visite360_f3.png` | Aucun écart. |
| **F4** | Localisation GPS (carte Leaflet) | 🟡 | `LeafletMap.tsx:1-145`, `SearchResultsPage.tsx:312-318`, `PropertyDetailPage.tsx:401-413` ; coordonnées GPS persistées via API (`docs/audit/api_proofs.json`) et cartes Leaflet rendues dans `docs/audit/screenshots/03_recherche_carte_leaflet_f4.png` et `06_fiche_annonce_galerie_badge_c2_f2.png` | La modale `CreateAnnonceModal.tsx:24-25,61-62` fige en dur `latitude: 14.745` et `longitude: -17.518` sans champs de saisie GPS ni sélecteur sur carte lors de la publication d'une annonce dans l'interface. |
| **F5** | Équipements | 🟡 | `Equipement.java`, `AnnonceEntity.java` (`@ElementCollection equipements`) ; persistés et relus via `POST`/`GET /api/v1/annonces` (`["WIFI","GARDIEN","PARKING","CLIMATISATION"]` dans `docs/audit/api_proofs.json`) ; vérifié dans `docs/audit/lighthouse_s5_t10_t4.json` (`hasEquipementsField: false`) et `docs/audit/ui_audit_results.json` (`rendersEquipementClimatisation: false`) | Les équipements ne peuvent ni être cochés dans `CreateAnnonceModal.tsx` ni être visualisés sur la fiche `PropertyDetailPage.tsx` (`docs/audit/screenshots/06_fiche_annonce_galerie_badge_c2_f2.png`). |
| **F6** | Assistant : pose des questions (budget, quartier, surface, étage, animaux…) et propose une shortlist personnalisée | 🟡 | `IAService.java:78-116`, `LlmExtractionService.java:239-241`, `RuleBasedCriteriaExtractor.java:149-151` ; `POST /api/v1/ia/conversations/{id}/messages` ; protocole de 5 cas incomplets dans `docs/audit/f6_gemini.json` et `docs/audit/s1_f6_resultats.json` | L'assistant ne relance jamais sur la surface, l'étage, les animaux, le meublé ou le quartier (il émet uniquement une question statique sur budget/type quand le budget manque, et annule toute relance dès qu'un budget est présent), tout en perdant `etage`, `animauxAcceptes` et `superficieMin` lors de la fusion multi-tours (`IAService.java:81-90`). |
| **F7** | Filtre budget (vérifié dans l'interface) | 🟡 | `SearchResultsPage.tsx:29,58,397-408` ; `GET /api/v1/annonces?budgetMin=&budgetMax=` (`docs/audit/api_proofs.json`) ; tiroir de filtres capturé dans `docs/audit/screenshots/04_recherche_filtres_f7_f13.png` (`docs/audit/ui_audit_results.json`) | Dans `SearchResultsPage.tsx`, le filtre budget n'expose aucun `budgetMin`, plafonne `budgetMax` à `2 000 000 FCFA` et l'applique par défaut à `800 000 FCFA`, masquant d'office sur `/recherche` toutes les annonces en `VENTE` (45M–85M FCFA) et les locations haut standing. |
| **F8** | Filtre type (vérifié dans l'interface) | ✅ | `SearchResultsPage.tsx:26,55,361-374` ; `GET /api/v1/annonces?type=APPARTEMENT` (`200 OK`, `docs/audit/api_proofs.json`) ; sélecteur « Type de bien » (`Studio`, `Appartement`, `Villa`) vérifié dans `docs/audit/screenshots/04_recherche_filtres_f7_f13.png` | Aucun écart. |
| **F9** | Filtre surface (vérifié dans l'interface) | 🟡 | `AnnonceController.java:102` (`superficieMin`), `SearchResultsPage.tsx:326-447` ; API vérifiée (`200 OK` dans `docs/audit/api_proofs.json`), absence UI prouvée dans `docs/audit/ui_audit_results.json` (`hasSurfaceInput: false`) et `docs/audit/screenshots/04_recherche_filtres_f7_f13.png` | Le filtre `superficieMin` fonctionne dans l'API REST mais aucun champ ni curseur de surface n'est présent dans l'interface React `SearchResultsPage.tsx`. |
| **F10** | Filtre nombre de pièces (vérifié dans l'interface) | 🟡 | `AnnonceController.java:103` (`nombrePieces`), `SearchResultsPage.tsx:326-447` ; API vérifiée (`200 OK` dans `docs/audit/api_proofs.json`), absence UI prouvée dans `docs/audit/ui_audit_results.json` (`hasPiecesInput: false`) et `docs/audit/screenshots/04_recherche_filtres_f7_f13.png` | Le filtre `nombrePieces` fonctionne côté API mais est totalement absent de l'interface de recherche `SearchResultsPage.tsx`. |
| **F11** | Filtre quartier (vérifié dans l'interface) | 🟡 | `AnnonceController.java:104` (`quartier`), `SearchResultsPage.tsx:25,114,329-343` ; API vérifiée (`200 OK`), absence UI prouvée dans `docs/audit/ui_audit_results.json` (`hasQuartierInput: false`) et `docs/audit/screenshots/04_recherche_filtres_f7_f13.png` | L'interface `SearchResultsPage.tsx` ne filtre que par `ville` sans aucun contrôle de `quartier`, et ignore le paramètre `?q=` transmis depuis la barre de recherche de `HomePage.tsx`. |
| **F12** | Filtre proximité (école, marché, hôpital — vérifié dans l'interface) | 🟡 | `SearchResultsPage.tsx:377-394`, `ProximiteTag.java` ; `GET /api/v1/annonces?proximite=ECOLE` (`200`) vs `?proximite=MOSQUEE` (`400 Bad Request` dans `docs/audit/api_proofs.json`) ; capture `docs/audit/screenshots/04_recherche_filtres_f7_f13.png` | Le sélecteur UI inclut `MOSQUEE` qui déclenche une erreur HTTP 400 (`No enum constant ProximiteTag.MOSQUEE`), omet `UNIVERSITE`, et aucune interface ne permet de saisir ni d'afficher les proximités sur une annonce. |
| **F13** | Filtre meublé/non-meublé (vérifié dans l'interface) | 🟡 | `SearchResultsPage.tsx:31,59,424-435` ; vérifié dans `docs/audit/ui_audit_results.json` (`hasNonMeubleFilter: false`) et `docs/audit/screenshots/04_recherche_filtres_f7_f13.png` | L'interface propose uniquement une case « Logements meublés uniquement » (`meuble=true`) et ne permet jamais de filtrer spécifiquement les logements non-meublés (`meuble=false`). |
| **F14** | Badge « Annonce vérifiée » après preuves (titre foncier, bail précédent, photo propriétaire + pièce d'identité) | 🟡 | `TypeDocumentVerification.java`, `CreateAnnonceModal.tsx:45-46,112-119,290-306`, `FileStorageService.java:20` ; test d'upload PDF rejeté `400` et 3 PNG acceptés `201` dans `docs/audit/api_proofs.json` ; capture `docs/audit/screenshots/12_modal_creation_preuves_f14_f5.png` | L'interface n'accepte qu'un seul fichier à la création (sans ajout ultérieur), le type « photo propriétaire » est absent du backend et de l'UI, et le champ invite au PDF (`accept="image/*,.pdf"`) alors que le serveur rejette tout PDF en HTTP 400. |
| **F15** | RDV : agenda propriétaire/agent | ✅ | `RendezVousController.java:32-90`, `RendezVousService.java:38-127`, `DashboardPage.tsx` ; création, confirmation, anti-chevauchement ±59 min (`409 CONFLICT` dans `docs/audit/api_proofs.json`) ; test `RendezVousServiceTest` (7/7) ; capture `docs/audit/screenshots/13_dashboard_visites_agenda_f15_f24.png` | Aucun écart. |
| **F16** | RDV : confirmation automatique | ❌ | `RendezVousService.java:49-59` ; vérifié via `POST /api/v1/annonces/{id}/rendez-vous` dans `docs/audit/api_proofs.json` (`rdv_created_initial_status: "DEMANDE"`) | Aucune confirmation automatique n'existe : chaque demande reste bloquée en `DEMANDE` jusqu'à une action manuelle `PUT /api/v1/rendez-vous/{id}/confirmer` du propriétaire/admin, sans gestion de créneaux de disponibilité ni message de confirmation. |
| **F17** | RDV : rappel SMS | 🟡 | `RendezVousService.java:129-153`, `RendezVousController.java:91-100`, `EcourtierApplication.java` ; `POST /api/v1/rendez-vous/simuler-rappels` exécuté (`200 OK`, log `[SIMULATION SMS RAPPEL RDV]` capturé dans `docs/audit/api_proofs.json`) | L'annotation `@EnableScheduling` étant absente de toute l'application Spring Boot, le cron `@Scheduled(cron = "0 0 8 * * *")` ne s'exécute jamais automatiquement (seul le déclenchement manuel par un admin émet un `log.info` simulé). |
| **F18** | Caution bloquée via Wave sandbox | ✅ | `TransactionService.java:95-115`, `WaveClientMock.java` ; `POST /api/v1/transactions/{id}/paiement-caution` exécuté (`200 OK`, `referenceWave: "WAVE-SANDBOX-73AB9E01"`, `statutSequestre: "BLOQUE"` dans `docs/audit/api_proofs.json`) ; test `WorkflowTransactionIntegrationTest` ; capture `docs/audit/screenshots/14_dashboard_transactions_modal_c3_f25.png` | Aucun écart fonctionnel (client Wave simulé localement conformément à `T8`, documenté en section 6). |
| **F19** | Caution libérée après signature du bail | ✅ | `TransactionService.java:118-200` ; `POST /api/v1/transactions/{id}/bail/signer` par les 2 parties dans `docs/audit/api_proofs.json` (`statutSequestre: "LIBERE"`, `montantVerseProprietaire: 500000.0`, `dateLiberation` horodatée) ; test `WorkflowTransactionIntegrationTest` | Aucun écart. |
| **F20** | Notation des logements | ✅ | `AvisController.java:33-53`, `AvisService.java:34-99`, `PropertyDetailPage.tsx:309-319,452-544` ; `POST /api/v1/annonces/{id}/avis` (`201`, anti-doublon `409` dans `docs/audit/api_proofs.json`) ; test `AvisServiceTest` (4/4) ; capture `docs/audit/screenshots/08_fiche_annonce_avis_f20.png` | Aucun écart. |
| **F21** | Notation des propriétaires | 🟡 | `UtilisateurController.java:24`, `AvisService.java:101-114`, `PropertyDetailPage.tsx:431-447` ; `GET /api/v1/utilisateurs/{id}/reputation` (`200 OK`, `noteMoyenne: 5.0` dans `docs/audit/api_proofs.json`) ; capture `docs/audit/screenshots/08_fiche_annonce_avis_f20.png` | Il n'existe ni entité ni formulaire d'évaluation propre au propriétaire : la « réputation propriétaire » agrège uniquement la moyenne des notes déposées sur ses annonces de logements. |
| **F22** | Signalement d'arnaques | ✅ | `SignalementController.java:38-72`, `SignalementModal.tsx`, `AdminSignalementsPage.tsx` ; `POST /api/v1/annonces/{id}/signalements` (`201`), `GET /api/v1/admin/signalements` (`200`) et `PUT /api/v1/admin/signalements/{id}/traiter` (`200` -> `SUSPENDUE` dans `docs/audit/api_proofs.json`) ; test `SignalementServiceTest` (2/2) ; capture `docs/audit/screenshots/16b_admin_signalements_f22.png` | Aucun écart. |
| **F23** | Dashboard : gestion des annonces | 🟡 | `DashboardPage.tsx`, `CreateAnnonceModal.tsx` ; vérifié dans `docs/audit/ui_audit_results.json` (`hasEditAnnonceButton: false`, `hasDeleteAnnonceButton: false`) et capture `docs/audit/screenshots/11_dashboard_proprietaire_annonces_f23.png` | Le tableau de bord permet de lister, créer et consulter ses annonces, mais n'offre aucun bouton ni formulaire pour modifier (`PUT`) ou supprimer (`DELETE`) une annonce existante. |
| **F24** | Dashboard : agenda des visites | ✅ | `DashboardPage.tsx` (onglet « Agenda Visites ») ; `GET /api/v1/rendez-vous/mes-rendez-vous` (`200 OK`, `docs/audit/api_proofs.json`) ; capture `docs/audit/screenshots/13_dashboard_visites_agenda_f15_f24.png` | Aucun écart. |
| **F25** | Dashboard : statut des dossiers | ✅ | `DashboardPage.tsx` (onglet « Suivi Transactions »), `TransactionDetailsModal.tsx` ; `GET /api/v1/transactions/mes-transactions` (`200 OK`) ; capture `docs/audit/screenshots/14_dashboard_transactions_modal_c3_f25.png` | Aucun écart. |
| **F26** | Rôle Admin | 🟡 | `AdminStatistiquesController.java:25`, `AdminAnnonceController.java:34-52`, `AdminUtilisateurController.java:28`, `AdminDashboardPage.tsx`, `AdminVerificationPage.tsx`, `AdminSignalementsPage.tsx` ; appels admin vérifiés dans `docs/audit/api_proofs.json` ; captures `15_admin_dashboard_statistiques_f26.png`, `16a_admin_verification_c2.png`, `16b_admin_signalements_f22.png` | L'administration des utilisateurs se limite à un `GET /api/v1/admin/utilisateurs` non relié à l'UI (sans création d'agent, suspension de compte ni modification de rôle), et l'interface de modération ne permet pas d'ouvrir les justificatifs soumis. |
| **F27** | Rôle Agent immobilier | 🟡 | `RegisterPage.tsx:160-175`, `AuthService.java:42`, `DataSeedRunner.java`, `AnnonceController.java:54` ; `POST /api/v1/auth/inscription` avec `role=AGENT` -> `400 Bad Request` et tests de permissions `agent@ecourtier.sn` dans `docs/audit/api_proofs.json` ; capture `docs/audit/screenshots/10_inscription_role_agent_f27.png` | Aucun compte `AGENT` ne peut être créé hors seed de démo (`RegisterPage` propose le choix mais l'API renvoie 400, et aucun endpoint admin de création/promotion n'existe), et l'`AGENT` possède exactement les mêmes droits qu'un `PROPRIETAIRE` sans gestion de mandats tiers. |
| **F28** | Rôle Propriétaire | 🟡 | `AuthService.java`, `AnnonceController.java`, `DashboardPage.tsx` ; parcours propriétaire complet testé dans `docs/audit/api_proofs.json` et captures `11` à `14` | Le propriétaire ne peut ni éditer/supprimer ses annonces depuis le dashboard React (`F23`) ni ajouter des pièces justificatives après la création initiale (`F14`). |
| **F29** | Rôle Locataire/Acheteur (parcours ACHAT d'une annonce en VENTE de bout en bout) | 🟡 | `TransactionService.java:72-77,184-186`, `PdfBailGeneratorService.java:43-66` ; parcours d'achat complet exécuté sur une villa à `65 000 000 FCFA` dans `docs/audit/api_proofs.json` (`F29_vente_achat_end_to_end` -> `statutPublication: "VENDUE"`) | En mode `VENTE`, le serveur exige 100 % du prix de vente (`65 000 000 FCFA`) en tant que « caution » sous séquestre et génère un PDF intitulé « CONTRAT DE BAIL NUMÉRIQUE » qualifiant vendeur et acheteur de « Bailleur » et « Locataire ». |
| **T1** | JWT + rôles | 🟡 | `JwtService.java`, `JwtAuthenticationFilter.java`, `SecurityConfig.java:43-88` ; login 4 rôles (`200`), refresh (`200`), accès anonyme refusé (`401`) et tests RBAC dans `docs/audit/api_proofs.json` ; tests `JwtServiceTest`, `AuthIntegrationTest` | Lorsqu'un utilisateur authentifié appelle une ressource interdite à son rôle (`LOCATAIRE` -> `/api/v1/admin/**`, `AGENT` -> annonce d'autrui), Spring renvoie **HTTP 401** avec encodage ISO-8859-1 corrompu (`"Non authentifi ? Jeton invalide ou expir"`) au lieu de **HTTP 403 Forbidden** faute d'`accessDeniedHandler` et d'autorisation sur `/error` (`SecurityConfig.java:53-70`). |
| **T2** | Swagger/OpenAPI 3.0 sur TOUS les endpoints | 🟡 | `application.yml:51-57` ; `GET /v3/api-docs` (`200 OK`, OpenAPI `3.0.1`, `53` opérations REST sur `53` présentes dans `docs/audit/api-docs-live.json` vs `docs/audit/endpoints_code.txt`) | Les 53 endpoints REST figurent tous dans `/v3/api-docs`, mais **20 endpoints n'ont ni `summary` ni `description`** (`MessagerieRestController`, `RendezVousController`, `TransactionController`) et 4 endpoints `FavoriController` n'ont pas de `description` (24/53 sans description). |
| **T3** | Mobile-first ≥ 360 px | ✅ | Vérification responsive Puppeteer en `360×800 px` sur 5 routes (`/`, `/recherche`, `/annonce/:id`, `/connexion`, `/dashboard`) avec `overflowPx = 0` (`docs/audit/ui_audit_results.json`) ; captures `01_accueil_mobile_360_t3.png`, `05_recherche_mobile_360_t3.png`, `09_fiche_annonce_mobile_360_t3.png` | Aucun écart. |
| **T4** | Offline partiel (Service Worker) | ✅ | `vite.config.ts:10-59`, `OfflineIndicator.tsx` ; test CDP `offline: true` sur build de production dans `docs/audit/lighthouse_s5_t10_t4.json` (`swState: "activated"`, 44 actifs précachés + `public-annonces-cache`, `rendersListingTitleOffline: true`, `rendersOfflineBanner: true`) ; capture `docs/audit/screenshots/17_offline_pwa_sw_t4.png` | Aucun écart. |
| **T5** | Caution ET premier loyer en séquestre | ✅ | `V10__add_montant_premier_loyer_to_transactions.sql`, `TransactionService.java:72-86` ; vérifié dans `docs/audit/api_proofs.json` (`montantCaution: 250000`, `montantPremierLoyer: 250000`, `fraisDossier: 5000`, `montantTotalSequestre: 505000`) et test `WorkflowTransactionIntegrationTest` | Aucun écart. |
| **T6** | Libération au propriétaire après signature du bail PDF par les 2 parties | ✅ | `TransactionService.java:167-200`, `PdfBailGeneratorService.java:25-85` ; vérifié dans `docs/audit/api_proofs.json` (1 signature -> `BLOQUE` & PDF `404` ; 2 signatures -> `LIBERE`, `500 000 FCFA` reversés, PDF `%PDF-1.5` téléchargé) et `WorkflowTransactionIntegrationTest` | Aucun écart. |
| **T7** | Frais de dossier 5 000 FCFA | ✅ | `TransactionService.java:77,170` ; vérifié dans `docs/audit/api_proofs.json` (`fraisDossier: 5000` retenus par la plateforme sur location comme sur vente) et capture `docs/audit/screenshots/14_dashboard_transactions_modal_c3_f25.png` | Aucun écart. |
| **T8** | Paiement Mobile Money simulé pour toute transaction | ✅ | `WaveClientMock.java`, `TransactionService.java:95-115` ; exécuté sur `LOCATION` et `VENTE` dans `docs/audit/api_proofs.json` (`WAVE-SANDBOX-73AB9E01`) ; capture `docs/audit/screenshots/14_dashboard_transactions_modal_c3_f25.png` | Aucun écart (simulation conforme à l'énoncé de `T8`). |
| **T9** | Déploiement cloud gratuit | 🟡 | `render.yaml:1-60` (plans `free`), sondes HTTP dans `docs/audit/render_l6.json` (`https://ecourtier-frontend.onrender.com/` -> `200 OK` en `0,936 s`) | Le service backend Render part en timeout (`> 90,4 s`) sur tous les endpoints et le site statique Render renvoie `404` sur toute route SPA profonde (`/recherche`, `/annonce/:id`) faute de règle `rewrite /* -> /index.html` dans `render.yaml`. |
| **T10** | Accessibilité | 🟡 | Audit Lighthouse mobile `13.5.0` dans `docs/audit/lighthouse_s5_t10_t4.json` (score `89/100` sur `/annonce/:id`, `94/100` sur `/`) ; support `useReducedMotion` dans `SearchResultsPage.tsx:19` | Quatre règles WCAG échouent à l'audit automatisé : `button-name` (bouton icône sans nom accessible), `link-name` (`<a href="/connexion">` sans intitulé), `heading-order` (saut `<h1>` -> `<h3>` dans `PropertyDetailPage.tsx:322,396`) et `color-contrast` insuffisant sur 4 éléments (`bg-terracotta-500 text-white`). |
| **L1** | Git : branches `feature`, `develop`, `main` | 🟡 | `git ls-remote --heads origin`, `git status -sb`, `git rev-list --left-right --count` exécutés le `2026-10-30` | Aucune branche `feature/*` n'existe en local ni sur `origin`, `main` et `develop` ont chacun 1 commit non poussé, et l'arbre local compte 84 fichiers modifiés et 44 chemins non suivis (modules `favori`, `messagerie`, `admin`, migrations `V6`–`V10` non commités). |
| **L2** | README : installation, TOUTES les variables d'environnement lues, démarrage local | 🟡 | `README.md:41-55,209-231,296-310` comparé à `application.yml:1-48`, `docker-compose.yml:1-66` et aux 15 `@Value` recensés dans `docs/audit/metrics.json` | `docker compose up --build -d` échoue sur un clone neuf faute d'instruction de création de `./backend/.env` (`docker-compose.yml:27`), 8 variables lues (`BACKEND_PORT`, `IA_PROVIDER`, `IA_BASE_URL`, `POSTGRES_*`, `FRONTEND_PORT`) manquent dans le README, et la doc mentionne à tort `LangChain4j`, `Bucket4j`, `Flyway V1–V5` et `/openapi.json`. |
| **L3** | UML : cas d'utilisation, classes, séquences à jour | 🟡 | `README.md:60-188` comparé aux 13 entités JPA (`docs/audit/metrics.json`) | Le diagramme Use Case utilise le mot-clé Mermaid invalide `usecaseDiagram` (`README.md:63`), le diagramme de classes omet 8 entités sur 13 ainsi que `montantPremierLoyer` dans `TransactionEntity`, et aucun diagramme ne couvre les favoris, la messagerie WebSocket ou les statistiques admin. |
| **L4** | Collection Postman / `openapi.json` à jour (comparés à `/v3/api-docs`) | 🟡 | `docs/openapi.json` et `docs/E-Courtier.postman_collection.json` comparés à `docs/audit/api-docs-live.json` (`53` opérations live) | Les deux fichiers statiques ne décrivent que 13 routes : **44 endpoints réels manquent**, **4 routes documentées n'existent plus** (`/api/v1/auth/me`, `/api/v1/ia/chat`, `POST /api/v1/rendez-vous`, `POST /api/v1/transactions`), et Postman utilise `minPrix`/`maxPrix` au lieu de `budgetMin`/`budgetMax`. |
| **L5** | Tests : relance `mvn test + jacoco:report` ; nombre, couverture, domaines < 60 % | 🟡 | Exécution Maven + JaCoCo `0.8.12` (`docs/audit/coverage.txt`, `docs/audit/tests_par_classe.txt`) : 30 classes, 106 tests (105 OK, 1 erreur), couverture backend `73,1 %` instr. / `78,8 %` lignes | `DataSeedProfileTest` échoue (`1/106` en erreur à cause de `DatabaseConfig.java:18` forçant l'URL JDBC PostgreSQL sur H2), le domaine métier `domain.favori` plafonne à `46,7 %` lignes (`< 60 %`, `FavoriController` à 0 %), 4 packages techniques sont `< 60 %`, et le frontend ne possède `0` test automatisé. |
| **L6** | Application déployée : curl health, frontend et Swagger sur Render ; base et version | 🟡 | Sondes `docs/audit/render_l6.json` : `frontend /` -> `200 OK` (`0,936 s`), `frontend /recherche` -> `404` (`0,737 s`), `backend /api/v1/health`, `/swagger-ui/index.html`, `/v3/api-docs`, `/api/v1/annonces` -> `TimeoutError > 90,4 s` | Le backend et sa base Render ne répondent pas (`> 90 s` x 4), les routes profondes SPA renvoient `404`, et la version déployée ne contient ni les commits non poussés ni les 128 fichiers locaux non commités. |
| **S1** | L'IA comprend le français naturel et renvoie une shortlist pertinente | 🟡 | Protocole 20 phrases (`docs/audit/s1_phrases_attendus.json` -> `docs/audit/s1_f6_resultats.json`) : Gemini `12/13` phrases exactes (`92,3 %`), Fallback règles `3/7` (`42,9 %`) | Malgré 92,3 % d'extraction exacte par Gemini, la shortlist échoue sur les accents/tirets (`Sacre Coeur`), ignore `etage` en base, bloque à 0 résultat sans budget, et chute à 42,9 % d'exactitude dès que le quota gratuit Gemini (429) active le moteur de règles. |
| **S2** | Badge attribué seulement après validation admin | ✅ | `AnnonceService.java`, `AdminAnnonceController.java:44` ; vérifié dans `docs/audit/api_proofs.json` (`EN_ATTENTE` à la création et après upload des pièces, auto-validation propriétaire bloquée `401`, passage à `VERIFIEE` uniquement après `PUT` par `ADMIN`) ; test `AnnonceServiceTest` ; capture `docs/audit/screenshots/06_fiche_annonce_galerie_badge_c2_f2.png` | Aucun écart sur la règle d'attribution exclusive à l'administrateur. |
| **S3** | RDV de bout en bout + confirmations SMS | 🟡 | `RendezVousService.java:38-153` ; cycle complet exécuté dans `docs/audit/api_proofs.json` (`F15_F16_F17_S3_rdv_and_sms`, log Docker `[SIMULATION SMS RAPPEL RDV]`) | Aucun SMS simulé n'est émis à la création ni à la confirmation d'un RDV, et le rappel de la veille ne part qu'en appelant manuellement `POST /api/v1/rendez-vous/simuler-rappels` (cron `@Scheduled` inactif faute de `@EnableScheduling`), avec sortie uniquement en log stdout. |
| **S4** | Le séquestre bloque jusqu'à signature et libère automatiquement | ✅ | `TransactionService.java:95-200` ; exécuté dans `docs/audit/api_proofs.json` (`BLOQUE` après Wave et après 1re signature -> `LIBERE` automatique dès la 2e signature avec log `[LIBÉRATION AUTOMATIQUE SÉQUESTRE]`) ; test `WorkflowTransactionIntegrationTest` | Aucun écart. |
| **S5** | Page d'annonce < 2 s sur 3G simulée, avec chargement différé des images | 🟡 | `PropertyDetailPage.tsx:347,353` (`loading="lazy"` sur miniatures), audit Lighthouse mobile 3 passes sur build compressé Brotli (`docs/audit/lighthouse_s5_t10_t4.json`) : Fast 3G FCP médian `1,91 s`, LCP médian `3,84 s` ; Slow 3G FCP `4,43 s`, LCP `8,94 s` | Bien que le FCP Fast 3G soit `< 2 s` (`1,91 s`) et `loading="lazy"` présent sur les photos secondaires de la fiche, le LCP Fast 3G (`3,84 s`) et la durée réseau (`6,77 s`, `578 Ko`) dépassent `2 s`, les vignettes de recherche n'ont pas `loading="lazy"`, et `/annonce/:id` renvoie `404` sur Render. |
| **S6** | Chatbot intégré dans React, réponse < 3 s | 🟡 | `AiAssistantWidget.tsx` intégré et capturé dans `docs/audit/screenshots/02_chatbot_react_s6.png` ; 10 appels chronométrés dans `docs/audit/s1_f6_resultats.json` (médiane Gemini `2,134 s`, max `5,028 s` ; médiane Fallback `2,031 s`, max `4,840 s`) | La médiane respecte `< 3 s` en Gemini (`2,13 s`) comme en fallback (`2,03 s`), mais les pics atteignent `5,03 s` (Gemini lent) et `4,84 s` (timeout de lecture avant bascule). |
| **TECH-1** | Technologie liste commune : **Redis** | ➖ | Vérification de `backend/pom.xml` et `docker-compose.yml` (aucune dépendance ni conteneur Redis) ; équivalents dans `CacheConfig.java:18-25` et `LoginRateLimiterService.java:28` | Non retenu dans l'architecture : le cache (`adminStats`, TTL 60 s via `ExpiringConcurrentMapCache`) et le rate-limiting de connexion (`ConcurrentHashMap`, 5 essais / 15 min) sont assurés en mémoire JVM. |
| **TECH-2** | Technologie liste commune : **Firebase FCM** | ➖ | Vérification de `backend/pom.xml` et `frontend/package.json` (aucun SDK Firebase) ; équivalents dans `WebSocketConfig.java:25-35`, `RendezVousService.java:142` et `sonner` | Non retenu : les notifications temps réel passent par WebSocket/STOMP (`/ws`, `/topic/conversations/{id}`), des toasts UI `sonner` et des logs SLF4J simulés pour les rappels SMS. |
| **TECH-3** | Technologie liste commune : **MongoDB** | ➖ | Vérification de `backend/pom.xml`, `application.yml` et `backend/src/main/resources/db/migration/V1..V10` | Non retenu : toutes les données structurées et semi-structurées (historiques IA avec `criteres_extraits` JSON texte, messagerie, tags `@ElementCollection`) sont stockées dans **PostgreSQL 16**. |
| **TECH-4** | Technologie liste commune : **Angular** | ➖ | Vérification de `frontend/package.json:28-58` | Non retenu (alternative frontale de la liste commune) : l'interface cliente SPA/PWA est intégralement développée avec **React 18.3.1 + TypeScript 5.7.3 + Vite 6.1.0**. |

---

### 1.2 Résultats détaillés des protocoles obligatoires

#### Protocole IA & Règle 5 — Test initial Gemini et messages complets `429 RESOURCE_EXHAUSTED`

- **Appel initial Gemini** : Réussi (`[MODE LLM IA] Appel à l'API LLM (provider: gemini, essai 1/2)` vers `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent`). Au total, **18 appels** du protocole (`S1-01` à `S1-13`, `F6-3`, `S6-03`, `S6-04`, `S6-06`, `S6-09`) ont obtenu une réponse réelle de `gemini-2.5-flash`.
- **1er blocage HTTP 429 (limite par minute Free Tier : 5 req/min)** survenu à `S1-14` (`2026-10-30T11:47:46Z`) — message complet journalisé par `LlmExtractionService` :
  ```json
  {
    "error": {
      "code": 429,
      "message": "You exceeded your current quota, please check your plan and billing details. For more information on this error, head to: https://ai.google.dev/gemini-api/docs/rate-limits. To monitor your current usage, head to: https://ai.dev/rate-limit. \n* Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 5, model: gemini-2.5-flash, description: Generate content requests per minute limit exceeded on Free Tier. See https://ai.google.dev/gemini-api/docs/rate-limits.\nPlease retry in 13.896274606s.",
      "status": "RESOURCE_EXHAUSTED",
      "details": [
        {
          "@type": "type.googleapis.com/google.rpc.Help",
          "links": [{ "description": "Learn more about Gemini API quotas", "url": "https://ai.google.dev/gemini-api/docs/rate-limits" }]
        },
        {
          "@type": "type.googleapis.com/google.rpc.QuotaFailure",
          "violations": [{
            "description": "Generate content requests per minute limit exceeded on Free Tier. See https://ai.google.dev/gemini-api/docs/rate-limits.",
            "quotaMetric": "generativelanguage.googleapis.com/generate_content_free_tier_requests",
            "quotaId": "GenerateRequestsPerMinutePerProjectPerModel-FreeTier",
            "quotaDimensions": { "location": "global", "model": "gemini-2.5-flash" },
            "quotaValue": "5"
          }]
        },
        { "@type": "type.googleapis.com/google.rpc.RetryInfo", "retryDelay": "13s" }
      ]
    }
  }
  ```
- **2e blocage HTTP 429 (limite quotidienne Free Tier : 20 req/jour)** survenu lors de la passe espacée `F6` (`2026-10-30T11:52:30Z`, `docs/audit/f6_gemini.json`) :
  ```json
  {
    "error": {
      "code": 429,
      "message": "You exceeded your current quota, please check your plan and billing details. For more information on this error, head to: https://ai.google.dev/gemini-api/docs/rate-limits. To monitor your current usage, head to: https://ai.dev/rate-limit. \n* Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 20, model: gemini-2.5-flash, description: Generate content requests per day limit exceeded on Free Tier. See https://ai.google.dev/gemini-api/docs/rate-limits.\nPlease retry in 19h7m29.75693354s.",
      "status": "RESOURCE_EXHAUSTED",
      "details": [
        {
          "@type": "type.googleapis.com/google.rpc.QuotaFailure",
          "violations": [{
            "quotaMetric": "generativelanguage.googleapis.com/generate_content_free_tier_requests",
            "quotaId": "GenerateRequestsPerDayPerProjectPerModel-FreeTier",
            "quotaDimensions": { "location": "global", "model": "gemini-2.5-flash" },
            "quotaValue": "20"
          }]
        },
        { "@type": "type.googleapis.com/google.rpc.RetryInfo", "retryDelay": "68849s" }
      ]
    }
  }
  ```
  Conformément à la règle 5, le backend a basculé automatiquement sur `RuleBasedCriteriaExtractor` (`[MODE FALLBACK IA]`) et les métriques ci-dessous séparent strictement les deux modes.

#### Protocole `S1` — 20 phrases en français naturel (attendus figés dans `docs/audit/s1_phrases_attendus.json` avant exécution)

| # | Phrase envoyée | Critères attendus | Critères extraits | Mode réel | Phrase 100 % OK | Shortlist (pertinentes / renvoyées / existantes en base) |
| :-: | :--- | :--- | :--- | :---: | :-: | :--- |
| 1 | `Je cherche un appartement F3 à Boucotte, budget 150 000 FCFA par mois` | `APPARTEMENT`, `3p`, `Boucotte`, `150000`, `LOCATION` | `APPARTEMENT`, `3p`, `q=Boucotte`, `150000`, `LOCATION` | `[MODE LLM IA]` | ✅ | `1 / 1 / 1` |
| 2 | `studio meublé à Nema max 100k` | `STUDIO`, `meublé=true`, `Nema`, `100000` | `STUDIO`, `meublé=true`, `q=Nema`, `100000` | `[MODE LLM IA]` | ✅ | `2 / 2 / 2` |
| 3 | `Je veux acheter une villa à Saly, budget 90 millions` | `VILLA`, `VENTE`, `Saly`, `90000000` | `VILLA`, `VENTE`, `v=Saly`, `90000000` | `[MODE LLM IA]` | ✅ | `1 / 1 / 1` |
| 4 | `deux chambres à Mermoz pour 500000 francs` | `nombrePieces=3` (T3 = salon + 2 ch.), `Mermoz`, `500000` | **`nombrePieces=2`**, `q=Mermoz`, `500000` | `[MODE LLM IA]` | ❌ | `1 / 1 / 1` (`>=2` pièces inclut le F4 Mermoz) |
| 5 | `maison à louer à Thiès avec mes chiens, 200000 max` | `VILLA`, `LOCATION`, `Thiès`, `animaux=true`, `200000` | `VILLA`, `LOCATION`, `v=Thiès`, `animaux=true`, `200000` | `[MODE LLM IA]` | ✅ | `1 / 1 / 1` |
| 6 | `appart au 2ème étage à Sacre Coeur budget 300000` | `APPARTEMENT`, `etage=2`, `Sacré-Cœur`, `300000` | `APPARTEMENT`, `etage=2`, `q="Sacre Coeur"`, `300000` | `[MODE LLM IA]` | ✅ | **`0 / 0 / 1`** (échec SQL accent/tiret `Sacre Coeur` vs `Sacré-Cœur` + `etage` ignoré en SQL) |
| 7 | `Je cherche un logement à Almadies avec 150000000 de budget pour acheter` | `VENTE`, `Almadies`, `150000000` | `VENTE`, `q=Almadies`, `150000000` | `[MODE LLM IA]` | ✅ | `0 / 0 / 0` (aucune vente aux Almadies à cet instant) |
| 8 | `F4 non meublé Mermoz 450 000` | `APPARTEMENT`, `4p`, `meublé=false`, `Mermoz`, `450000` | `APPARTEMENT`, `4p`, `meublé=false`, `q=Mermoz`, `450000` | `[MODE LLM IA]` | ✅ | `0 / 0 / 0` (le F4 Mermoz en base est meublé) |
| 9 | `Studio pas cher a ziguinchor moins de 90 000 fcfa` | `STUDIO`, `Ziguinchor`, `90000` | `STUDIO`, `LOCATION`, `v=ziguinchor`, `90000` | `[MODE LLM IA]` | ✅ | `2 / 2 / 2` |
| 10 | `je cherche une vila à nema` *(faute volontaire, sans budget)* | `VILLA`, `Nema` | `VILLA`, `v=nema`, `budget=null` | `[MODE LLM IA]` | ✅ | **`0 / 0 / 2`** (bloqué par la relance budget obligatoire alors que 2 villas existent à Nema) |
| 11 | `appartement de 100 m2 minimum à Dakar, 400k` | `APPARTEMENT`, `superficieMin=100`, `Dakar`, `400000` | `APPARTEMENT`, `superficieMin=100.0`, `v=Dakar`, `400000` | `[MODE LLM IA]` | ✅ | `0 / 0 / 0` |
| 12 | `Je voudrais louer un apartement meublé à Mbour, 120 000 par mois` *(faute volontaire)* | `APPARTEMENT`, `meublé=true`, `Mbour`, `LOCATION`, `120000` | `APPARTEMENT`, `meublé=true`, `v=Mbour`, `LOCATION`, `budgetMin=120000`, `budgetMax=120000` | `[MODE LLM IA]` | ✅ | **`0 / 0 / 1`** (Gemini remplit aussi `budgetMin=120000`, excluant l'appartement à `100 000 FCFA` à Mbour !) |
| 13 | `villa 5 pièces à vendre ziguinchor 50 millions` | `VILLA`, `5p`, `VENTE`, `Ziguinchor`, `50000000` | `VILLA`, `5p`, `VENTE`, `v=Ziguinchor`, `50000000` | `[MODE LLM IA]` | ✅ | `2 / 2 / 2` |
| 14 | `un studio pour étudiant à Boucotte, 60.000 FCFA` | `STUDIO`, `Boucotte`, `60000` | `STUDIO`, `q=Boucotte`, **`budgetMax=null`** | `[MODE FALLBACK IA]` | ❌ | `0 / 0 / 0` (séparateur `.` non reconnu par regex) |
| 15 | `Nous sommes une famille avec un chat, on cherche une maison à Dakar 1,5 million par mois` | `VILLA`, `animaux=true`, `Dakar`, `1500000`, `LOCATION` | `VILLA`, **`animaux=null`**, `v=Dakar`, **`budgetMax=null`**, `LOCATION` | `[MODE FALLBACK IA]` | ❌ | **`0 / 0 / 1`** (ni `chat` ni `1,5 million` reconnus par regex) |
| 16 | `rez-de-chaussée obligatoire, F2 à Sacré-Cœur, budget 200 000` | `APPARTEMENT`, `etage=0`, `2p`, `Sacré-Cœur`, `200000` | `APPARTEMENT`, `etage=0`, `2p`, `q=Sacré-Cœur`, `200000` | `[MODE FALLBACK IA]` | ✅ | `0 / 0 / 0` |
| 17 | `Je veux acheter un appartement à Mermoz` | `APPARTEMENT`, `VENTE`, `Mermoz` | `APPARTEMENT`, `VENTE`, **`quartier=null`** | `[MODE FALLBACK IA]` | ❌ | `0 / 0 / 0` (`Mermoz` absent des 6 quartiers codés en dur dans `RuleBasedCriteriaExtractor.java:91-103`) |
| 18 | `cherche chambre meublée dixieme thies 80k` | `meublé=true`, `Dixième`, `80000` | `meublé=true`, **`lieu=null`**, **`budgetMax=null`** | `[MODE FALLBACK IA]` | ❌ | `0 / 0 / 0` (`dixieme`, `thies` et suffixe `80k` non gérés en fallback) |
| 19 | `Location villa avec piscine Almadies budget max 2 000 000 FCFA` | `VILLA`, `LOCATION`, `Almadies`, `2000000` | `VILLA`, `LOCATION`, `q=Almadies`, `2000000` | `[MODE FALLBACK IA]` | ✅ | `1 / 1 / 1` |
| 20 | `jcherche 3 pieces a ziguinchor pas plus de 100000` | `3p`, `Ziguinchor`, `100000` | `3p`, `v=Ziguinchor`, `100000` | `[MODE FALLBACK IA]` | ✅ | `1 / 1 / 1` |

- **Synthèse d'exactitude `S1` séparée par moteur** :
  - **Gemini (`[MODE LLM IA]`, 13 phrases, 52 champs attendus)** : **12/13 phrases entièrement correctes (`92,3 %`)** ; exactitude par champ : `type` `11/11` (`100 %`), `budgetMax` `12/12` (`100 %`), `lieu` `13/13` (`100 %`), `modeTransaction` `6/6` (`100 %`), `meuble` `3/3` (`100 %`), `animauxAcceptes` `1/1` (`100 %`), `etage` `1/1` (`100 %`), `superficieMin` `1/1` (`100 %`), `nombrePieces` `3/4` (`75 %`, confusion « deux chambres » = 2 pièces au lieu d'un T3). **Total champs : `51/52` (`98,1 %`)**. Pertinence shortlist sur requêtes ayant au moins 1 bien correspondant en base : `6/9` requêtes renvoient les bons biens (`100 %` de précision sur les cartes renvoyées, mais 3 faux zéros sur `S1-06`, `S1-10`, `S1-12`).
  - **Moteur de règles (`[MODE FALLBACK IA]`, 7 phrases, 26 champs attendus)** : **3/7 phrases entièrement correctes (`42,9 %`)** ; exactitude par champ : `type` `5/5` (`100 %`), `modeTransaction` `3/3` (`100 %`), `nombrePieces` `2/2` (`100 %`), `etage` `1/1` (`100 %`), `meuble` `1/1` (`100 %`), `lieu` `5/7` (`71,4 %`), `budgetMax` `3/6` (`50,0 %`), `animauxAcceptes` `0/1` (`0 %`), et `superficieMin` est câblé à `null` (`RuleBasedCriteriaExtractor.java:45,164`). **Total champs : `20/26` (`76,9 %`)**.

#### Protocole `F6` — 5 cas où la surface, l'étage ou les animaux manquent (`docs/audit/f6_gemini.json`)

| Cas | Message utilisateur | Champs omis volontairement | Mode | `questionDeRelance` renvoyée | Demande surface / étage / animaux ? |
| :--- | :--- | :--- | :---: | :--- | :-: |
| `F6-1` | `Je cherche un appartement à Mermoz, budget 500 000` | `superficie`, `etage`, `animaux`, `meuble` | `[MODE FALLBACK IA]` | `null` (lance directement la recherche) | ❌ Non |
| `F6-2` | `studio à Nema 100k` | `superficie`, `etage`, `animaux` | `[MODE FALLBACK IA]` (`100k` non lu) | `"Quel est votre budget approximatif et le type de bien souhaité (ex: appartement, villa, studio) ?"` | ❌ Non (redemande même le type `studio` déjà fourni !) |
| `F6-3` | `J'ai un chien, je cherche une villa à Dakar` | `budget`, `superficie`, `etage` | **`[MODE LLM IA]` (Gemini)** | `"Quel est votre budget approximatif et le type de bien souhaité (ex: appartement, villa, studio) ?"` | ❌ Non (redemande le type déjà extrait `VILLA`, ignore surface/étage) |
| `F6-4` | `Je veux louer quelque chose` | `budget`, `type`, `quartier`, `superficie`, `etage`, `animaux` | `[MODE FALLBACK IA]` | `"Quel est votre budget approximatif et le type de bien souhaité (ex: appartement, villa, studio) ?"` | ❌ Non (uniquement budget + type) |
| `F6-5` | `F3 à Boucotte 150 000 FCFA` | `superficie`, `etage`, `animaux`, `meuble` | `[MODE FALLBACK IA]` *(et identique à `S1-01` en `[MODE LLM IA]`)* | `null` | ❌ Non |

- **Constat `F6`** : Dans **5 cas sur 5**, ni Gemini ni le moteur de règles ne posent de question sur la surface, l'étage, les animaux, l'ameublement ou le quartier (`IAService.java:93-95` force `criteres.setQuestionDeRelance(null)` dès qu'un budget est présent).

#### Protocole `S6` — 10 appels chronométrés séparant Gemini réussi et Fallback (`docs/audit/s1_f6_resultats.json`)

| Appel | Message | Mode effectif | Durée (`s`) | `< 3 s` |
| :---: | :--- | :---: | :---: | :-: |
| `S6-03` | `Villa à louer aux Almadies max 2 000 000 FCFA` | `[MODE LLM IA]` (Gemini) | `2,434 s` | ✅ |
| `S6-04` | `Je cherche une villa à vendre à Ziguinchor pour 50 millions` | `[MODE LLM IA]` (Gemini) | `1,833 s` | ✅ |
| `S6-06` | `Maison avec jardin à Thiès animaux acceptés 200 000 FCFA` | `[MODE LLM IA]` (Gemini) | `1,692 s` | ✅ |
| `S6-09` | `Cherche logement à Dakar sans budget précis` | `[MODE LLM IA]` (Gemini) | **`5,028 s`** | ❌ |
| `S6-01` | `Studio meublé à Nema pour 90 000 FCFA` | `[MODE FALLBACK IA]` (429 immédiat) | `1,522 s` | ✅ |
| `S6-02` | `Appartement F3 à Boucotte budget 120 000 FCFA` | `[MODE FALLBACK IA]` (429 immédiat) | `0,846 s` | ✅ |
| `S6-05` | `Appartement 4 pièces meublé à Mermoz 450 000 FCFA` | `[MODE FALLBACK IA]` (timeout lecture) | `2,541 s` | ✅ |
| `S6-07` | `Villa de vacances à acheter à Saly budget 90 000 000 FCFA` | `[MODE FALLBACK IA]` (timeout lecture) | `2,564 s` | ✅ |
| `S6-08` | `Appartement à louer à Mbour budget 110 000 FCFA` | `[MODE FALLBACK IA]` (timeout cnx+lecture) | **`4,840 s`** | ❌ |
| `S6-10` | `F3 non meublé Sacré-Cœur 300 000 FCFA` | `[MODE FALLBACK IA]` (429 immédiat) | `0,638 s` | ✅ |

- **Gemini réussi (`n = 4`)** : min `1,692 s` · **médiane `2,134 s`** · **maximum `5,028 s`** (sur l'ensemble élargi des 18 appels Gemini du jour : médiane `1,855 s`, 16/18 `< 3 s`).
- **Fallback règles (`n = 6`)** : min `0,638 s` · **médiane `2,031 s`** · **maximum `4,840 s`** (lorsqu'aucune clé API n'est configurée ou qu'un 429 répond vite, le traitement règles pur prend `< 50 ms`, mais l'attente préalable d'un appel Gemini lent consomme jusqu'à `connectTimeout(2500) + readTimeout(2500)`).

#### Protocole `S5 / T10 / T4` — Lighthouse Mobile `13.5.0` (3 passes sans cache, compression Brotli `br`, `docs/audit/lighthouse_s5_t10_t4.json`)

- **Cible locale** : Build de production `vite build` servi avec compression Brotli (`Content-Encoding: br`) sur `http://localhost:5173/annonce/958b59d1-12af-4412-b319-f20f24dfcb87` (annonce vérifiée F4 Mermoz avec 3 photos, carte Leaflet et visite 360°).
- **Cible Render** : `https://ecourtier-frontend.onrender.com/annonce/958b59d1-...` renvoie **HTTP `404 Not Found`** (absence de rewrite SPA) ; une passe de référence Fast 3G a donc été mesurée sur la racine déployée `https://ecourtier-frontend.onrender.com/`.

| Profil & Cible | Passe | FCP | LCP | TTI | Durée réseau | Octets transférés | Perf. | Accessibilité | Bonnes pratiques | PWA (note v13.5) |
| :--- | :-: | :---: | :---: | :---: | :---: | :---: | :-: | :-: | :-: | :--- |
| **Local — Fast 3G** (`1,6 Mb/s`, `150 ms`) | 1 | `1 866 ms` | `3 811 ms` | `3 780 ms` | `6 773 ms` | `577 983 o` | 73 | 89 | 100 | SW actif + offline OK* |
| **Local — Fast 3G** (`1,6 Mb/s`, `150 ms`) | 2 | `2 362 ms` | `3 841 ms` | `3 804 ms` | `9 596 ms` | `578 052 o` | 71 | 89 | 100 | SW actif + offline OK* |
| **Local — Fast 3G** (`1,6 Mb/s`, `150 ms`) | 3 | `1 906 ms` | `3 865 ms` | `3 832 ms` | `5 630 ms` | `578 056 o` | 72 | 89 | 100 | SW actif + offline OK* |
| **Local — Fast 3G (MÉDIANE)** | **Méd.** | **`1 906 ms` (`1,91 s`)** | **`3 841 ms` (`3,84 s`)** | **`3 804 ms`** | **`6 773 ms`** | **`578 052 o` (`564,5 Kio`)** | **72** | **89** | **100** | **Conforme (`T4`)*** |
| **Local — Slow 3G** (`400 kb/s`, `400 ms`) | 1 | `4 408 ms` | `8 915 ms` | `8 881 ms` | `14 750 ms` | `578 086 o` | 45 | 89 | 100 | SW actif + offline OK* |
| **Local — Slow 3G** (`400 kb/s`, `400 ms`) | 2 | `4 428 ms` | `8 937 ms` | `8 891 ms` | `14 742 ms` | `578 007 o` | 45 | 89 | 100 | SW actif + offline OK* |
| **Local — Slow 3G** (`400 kb/s`, `400 ms`) | 3 | `4 445 ms` | `9 026 ms` | `8 979 ms` | `14 845 ms` | `578 036 o` | 44 | 89 | 100 | SW actif + offline OK* |
| **Local — Slow 3G (MÉDIANE)** | **Méd.** | **`4 428 ms` (`4,43 s`)** | **`8 937 ms` (`8,94 s`)** | **`8 891 ms`** | **`14 750 ms`** | **`578 036 o` (`564,5 Kio`)** | **45** | **89** | **100** | **Conforme (`T4`)*** |
| **Render `/` — Fast 3G** (fiche = HTTP 404) | 1 | `3 948 ms` | `11 826 ms` | `4 520 ms` | `11 923 ms` | `540 415 o` (`527,7 Kio`) | 65 | 94 | 100 | Manifeste + `sw.js` présents |

*\*Note PWA : Lighthouse v12+ (ici `13.5.0`) a supprimé le score catégoriel `pwa` ; la conformité PWA/Offline (`T4`) a été auditée directement via Chrome DevTools Protocol (`swState: "activated"`, `workbox-precache` = 44 entrées, `public-annonces-cache` = actif, rechargement hors-ligne de la fiche réussi avec bannière dédiée — capture `docs/audit/screenshots/17_offline_pwa_sw_t4.png`). Score SEO Lighthouse : `92/100` en local, `100/100` sur Render `/`.*

#### Protocole `T2` — Contrôleurs vs `/v3/api-docs` (`docs/audit/endpoints_code.txt` vs `docs/audit/api-docs-live.json`)

- **Dans le code des 17 contrôleurs** : **54 mappings** au total = **53 endpoints REST** (`@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`) + **1 endpoint WebSocket STOMP** (`@MessageMapping("/conversations/{id}/envoyer")` dans `MessagerieStompController.java:26`, hors périmètre OpenAPI HTTP).
- **Dans `GET /v3/api-docs`** : **53 opérations REST** présentes (`0` endpoint REST manquant).
- **20 endpoints dépourvus à la fois de `summary` et de `description` dans `/v3/api-docs`** :
  1. `POST /api/v1/annonces/{id}/conversations` (`MessagerieRestController.java:26`)
  2. `GET /api/v1/conversations/mes-conversations` (`MessagerieRestController.java:35`)
  3. `GET /api/v1/conversations/{id}` (`MessagerieRestController.java:41`)
  4. `GET /api/v1/conversations/{id}/messages` (`MessagerieRestController.java:50`)
  5. `GET /api/v1/conversations/{id}/messages/tous` (`MessagerieRestController.java:60`)
  6. `PUT /api/v1/conversations/{id}/marquer-lu` (`MessagerieRestController.java:69`)
  7. `GET /api/v1/conversations/non-lus/count` (`MessagerieRestController.java:78`)
  8. `POST /api/v1/annonces/{id}/rendez-vous` (`RendezVousController.java:32`)
  9. `PUT /api/v1/rendez-vous/{id}/confirmer` (`RendezVousController.java:43`)
  10. `PUT /api/v1/rendez-vous/{id}/annuler` (`RendezVousController.java:53`)
  11. `PUT /api/v1/rendez-vous/{id}/marquer-effectue` (`RendezVousController.java:64`)
  12. `GET /api/v1/rendez-vous/mes-rendez-vous` (`RendezVousController.java:74`)
  13. `GET /api/v1/annonces/{id}/rendez-vous` (`RendezVousController.java:81`)
  14. `POST /api/v1/rendez-vous/simuler-rappels` (`RendezVousController.java:91`)
  15. `POST /api/v1/annonces/{id}/transactions` (`TransactionController.java:37`)
  16. `POST /api/v1/transactions/{id}/paiement-caution` (`TransactionController.java:48`)
  17. `POST /api/v1/transactions/{id}/bail/signer` (`TransactionController.java:58`)
  18. `GET /api/v1/transactions/mes-transactions` (`TransactionController.java:68`)
  19. `GET /api/v1/transactions/{id}` (`TransactionController.java:77`)
  20. `GET /api/v1/transactions/{id}/bail/pdf` (`TransactionController.java:87`)
- **4 endpoints supplémentaires possédant un `summary` mais sans champ `description`** (`FavoriController.java:29,39,49,59`) : `POST /api/v1/annonces/{id}/favoris`, `DELETE /api/v1/annonces/{id}/favoris`, `GET /api/v1/utilisateurs/moi/favoris`, `GET /api/v1/utilisateurs/moi/favoris/count`.

#### Focus `F27` (Rôle Agent), `F29` (Parcours Vente/Achat), `S3` (Cycle RDV & SMS) et `L2` (Variables d'environnement)

- **`F27` — Compte Agent immobilier** :
  - *Comment est-il créé ?* Uniquement par injection SQL/JPA au démarrage dev dans `DataSeedRunner.java` (`agent@ecourtier.sn`). L'écran `RegisterPage.tsx:160` affiche pourtant une carte cliquable « Courtier / Agent », mais la soumission appelle `POST /api/v1/auth/inscription` où `AuthService.java:42` lève `400 Bad Request` (`"Les comptes ADMIN et AGENT ne peuvent pas être créés via l'inscription publique"`), et `AdminUtilisateurController` ne comporte aucun endpoint `POST`/`PUT` pour créer ou promouvoir un agent.
  - *Que peut faire l'Agent ?* Exactement la même chose qu'un `PROPRIETAIRE` (`@PreAuthorize("hasAnyRole('PROPRIETAIRE', 'AGENT', 'ADMIN')")` sur `AnnonceController.java:54`) : publier ses propres annonces, gérer ses propres RDV/transactions, sans mandat pour le compte d'un propriétaire tiers ni accès aux écrans de modération (`ADMIN` uniquement).
- **`F29` — Parcours ACHAT d'une annonce en VENTE de bout en bout** :
  - Exécuté dans `docs/audit/api_proofs.json` (`F29_vente_achat_end_to_end`) sur une `VILLA` en `VENTE` à `65 000 000 FCFA` : 1) demande RDV (`201`) -> confirmation (`200`) -> visite effectuée (`200`) ; 2) `POST /api/v1/annonces/{id}/transactions` (`201`) calcule `montantCaution = 65 000 000 FCFA` (100 % du prix de vente !), `montantPremierLoyer = 0`, `fraisDossier = 5 000 FCFA`, `montantTotalSequestre = 65 005 000 FCFA` ; 3) paiement Wave simulé (`BLOQUE`) ; 4) double signature -> `statutSequestre = LIBERE`, `montantVerseProprietaire = 65 000 000 FCFA`, annonce passée à `statutPublication = VENDUE`, mais le PDF téléchargé est intitulé `CONTRAT DE BAIL NUMÉRIQUE E-COURTIER SÉNÉGAL` avec « Propriétaire / Bailleur », « Locataire » et « Montant du premier loyer : 0.00 FCFA ».
- **`S3` — Ce qui se passe exactement sur les RDV et SMS** :
  - *À la création (`POST /api/v1/annonces/{id}/rendez-vous`)* : insertion en base avec `statut = DEMANDE` et `rappel_envoye = false` (`RendezVousService.java:49-58`). **Aucun SMS ni log émis.**
  - *À la confirmation (`PUT /api/v1/rendez-vous/{id}/confirmer`)* : contrôle d'absence d'autre RDV `CONFIRME` à `± 59 min` sur la même annonce (`409` sinon), puis mise à jour `statut = CONFIRME` (`RendezVousService.java:62-87`). **Aucun SMS ni log émis.**
  - *La veille* : la méthode `simulerRappelsDemain()` (`RendezVousService.java:129-153`) porte `@Scheduled(cron = "0 0 8 * * *")`, **mais comme `@EnableScheduling` n'est déclaré nulle part, le planificateur Spring ne s'exécute jamais tout seul**. Seul l'appel manuel `POST /api/v1/rendez-vous/simuler-rappels` (réservé `ADMIN`) parcourt les RDV `CONFIRME` du lendemain avec `rappel_envoye = false`, écrit une ligne SLF4J dans la sortie standard (`[SIMULATION SMS RAPPEL RDV] Notification envoyée au demandeur ... (+221...) pour la visite de l'annonce '...' prévue le ...`) et passe le booléen `rendez_vous.rappel_envoye` à `true` (aucune table dédiée d'historique SMS).
- **`L2` — Comparaison variables d'environnement code vs `README.md`** :
  - *Lues par le backend (`application.yml` + `@Value`)* : `SPRING_PROFILES_ACTIVE`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `ADMIN_BOOTSTRAP_EMAIL`, `ADMIN_BOOTSTRAP_PASSWORD`, `IA_API_KEY`, `ECOURTIER_CORS_ALLOWED_ORIGINS` (documentées en §10.2 du README), **plus `BACKEND_PORT`, `IA_PROVIDER`, `IA_BASE_URL`** (absentes du README) et les propriétés internes `@Value` `ecourtier.jwt.access-token-expiration-ms`, `ecourtier.jwt.refresh-token-expiration-ms`, `spring.datasource.driver-class-name`. Aucun appel `System.getenv()` dans le code Java.
  - *Lues par `docker-compose.yml` mais absentes du README* : `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT`, `FRONTEND_PORT`, ainsi que le fichier obligatoire `./backend/.env` (`env_file` ligne 27).
  - *Lue par le frontend (`import.meta.env`)* : `VITE_API_BASE_URL` (`frontend/src/services/api.ts`, documentée).

---

## 2. Synthèse chiffrée par statut et par catégorie

| Catégorie | Total items | ✅ Conforme | 🟡 Partiel | ❌ Absent | ➖ Hors périmètre | Taux `✅` strict |
| :--- | :-: | :-: | :-: | :-: | :-: | :-: |
| **Compétences (`C1`–`C3`)** | **3** | 1 | 2 | 0 | 0 | 33,3 % |
| **Fonctionnel minimal (`F1`–`F29`)** | **29** | 11 | 17 | 1 | 0 | 37,9 % |
| **Contraintes (`T1`–`T10`)** | **10** | 6 | 4 | 0 | 0 | 60,0 % |
| **Livrables (`L1`–`L6`)** | **6** | 0 | 6 | 0 | 0 | 0,0 % |
| **Critères de réussite (`S1`–`S6`)** | **6** | 2 | 4 | 0 | 0 | 33,3 % |
| **Sous-total Exigences Cahier (`C`+`F`+`T`+`L`+`S`)** | **54** | **20 (37,0 %)** | **33 (61,1 %)** | **1 (1,9 %)** | **0 (0,0 %)** | **37,0 %** |
| **Technologies liste commune (`TECH-1`–`TECH-4`)** | **4** | 0 | 0 | 0 | 4 | N/A |
| **TOTAL MATRICE GÉNÉRALE** | **58** | **20** | **33** | **1** | **4** | — |

---

## 3. Écarts triés par criticité, correctif proposé et effort (`S` / `M` / `L`)

### 3.1 Écarts Bloquants (4)

| # | Réf. | Écart constaté | Correctif proposé | Effort |
| :-: | :--- | :--- | :--- | :-: |
| B1 | `L6`, `T9`, `S5` | **Déploiement Render hors service côté API/DB (`Timeout > 90 s`) et `404` sur toutes les routes SPA profondes (`/recherche`, `/annonce/:id`)** | Vérifier les variables `SPRING_DATASOURCE_*` / `ADMIN_BOOTSTRAP_PASSWORD` et l'état de l'instance `ecourtier-db` sur Render, puis ajouter dans `render.yaml` sous `ecourtier-frontend` une règle `routes: [{ type: rewrite, source: /*, destination: /index.html }]` et redéployer `main`. | **S** |
| B2 | `C2`, `F14`, `F26` | **Validation cadastrale « à l'aveugle » dans l'UI Admin et soumission incomplète des preuves (PDF rejeté `400`, 1 seul fichier, pas de photo propriétaire)** | Ajouter `PHOTO_PROPRIETAIRE` dans `TypeDocumentVerification`, autoriser le magic-byte `%PDF-` (`25 50 44 46`) dans `FileStorageService`, permettre l'envoi des 3 pièces dans `CreateAnnonceModal` / `/dashboard`, et ajouter dans `AdminVerificationPage.tsx` l'ouverture authentifiée de `GET /api/v1/annonces/{id}/documents/{docId}/fichier`. | **M** |
| B3 | `F7`, `F9`, `F10`, `F11`, `F12`, `F13` | **Interface de recherche `/recherche` incomplète et cassante** : filtres `surface`, `nombrePieces` et `quartier` absents de l'UI, `MOSQUEE` provoque HTTP `400`, `non-meublé` impossible, et le slider `maxPrice=800 000` (max `2M`) masque par défaut toutes les annonces en `VENTE` | Ajouter les champs `quartier`, `superficieMin`, `nombrePieces`, `budgetMin` et un sélecteur tri-état `Tous / Meublé / Non meublé` dans `SearchResultsPage.tsx`, retirer `MOSQUEE` (ou l'ajouter à `ProximiteTag`), synchroniser `?modeTransaction=&q=`, et adapter l'échelle du budget selon `LOCATION`/`VENTE` (sans plafond actif par défaut). | **M** |
| B4 | `L1` | **128 chemins non commités (84 `M`, 44 `??`), commits locaux non poussés et absence totale de branches `feature/*`** | Structurer des commits atomiques par fonctionnalité sur des branches `feature/*` dédiées (`feature/favoris`, `feature/messagerie-ws`, `feature/admin-stats`, `feature/premier-loyer`), fusionner dans `develop` puis `main`, et pousser vers `origin`. | **S** |

### 3.2 Écarts Majeurs (9)

| # | Réf. | Écart constaté | Correctif proposé | Effort |
| :-: | :--- | :--- | :--- | :-: |
| M1 | `F27`, `F26` | **Impossibilité de créer un compte `AGENT`** (`RegisterPage` propose le rôle mais `AuthService` renvoie `400`, aucun endpoint admin de création/promotion) et absence de fonctionnalités propres à l'agent | Retirer la carte `AGENT` de l'inscription publique ou créer un workflow de demande d'agrément, ajouter `POST /api/v1/admin/utilisateurs/agents` + écran admin de gestion des rôles/statuts, et introduire le champ `proprietaireMandant` sur `AnnonceEntity`. | **M** |
| M2 | `F16`, `F17`, `S3` | **Pas de confirmation automatique des RDV, aucun SMS à la création/confirmation, et cron de rappel `@Scheduled` inactif faute de `@EnableScheduling`** | Ajouter `@EnableScheduling` sur `EcourtierApplication`, émettre une notification SMS (table `notifications_sms` + log/webhook) à la création et à la confirmation, et ajouter une option d'auto-confirmation sur créneaux disponibles du propriétaire. | **M** |
| M3 | `F29` | **Parcours `VENTE`/`ACHAT` incohérent métier** : `montantCaution` = 100 % du prix de vente (`65 000 000 FCFA`) et génération d'un PDF « CONTRAT DE BAIL NUMÉRIQUE » | En mode `VENTE`, calculer un acompte/séquestre paramétrable (ex. 5 % ou 10 % du prix), adapter les libellés UI (« Compromis / Acte de réservation ») et générer un modèle PDF spécifique dans `PdfBailGeneratorService` pour les ventes. | **M** |
| M4 | `F5`, `F4` | **Équipements (`F5`) absents de l'UI de création et de la fiche annonce ; coordonnées GPS (`F4`) figées en dur à la création UI** | Ajouter des cases à cocher `Equipement` et `ProximiteTag` + un sélecteur de point sur carte Leaflet dans `CreateAnnonceModal.tsx`, et afficher la grille des équipements/proximités dans `PropertyDetailPage.tsx`. | **S** |
| M5 | `F23`, `F28` | **Aucune action Modifier (`PUT`) ni Supprimer (`DELETE`) sur les annonces dans `/dashboard`** | Brancher dans `DashboardPage.tsx` une modale d'édition pré-remplie (`PUT /api/v1/annonces/{id}`) et un bouton de suppression avec confirmation (`DELETE /api/v1/annonces/{id}`). | **S** |
| M6 | `C1`, `F6`, `S1`, `S6` | **Limites de l'assistant IA** : relances limitées au budget/type, perte de `etage`/`animaux`/`superficieMin` entre tours, filtre SQL sans `etage` ni normalisation d'accents (`Sacre Coeur`), `budgetMin=budgetMax` bloquant, pics `> 3 s`, et fallback regex faible (`42,9 %`) | Ajouter le prédicat `etage` et `unaccent`/normalisation dans `AnnonceSpecification`, conserver tous les champs dans `IAService.java:81-90`, générer des relances ciblées (quartier, surface, étage, animaux), ignorer `budgetMin` quand un seul montant plafond est cité, abaisser le budget temps global LLM à `2 200 ms`, et enrichir `RuleBasedCriteriaExtractor` (`k`, `million`, `.`, quartiers/villes, `chat`/`chien`). | **M** |
| M7 | `T1` | **Violation d'autorisation RBAC renvoyant `HTTP 401` avec texte corrompu au lieu de `HTTP 403 Forbidden`** | Ajouter `response.setCharacterEncoding("UTF-8")` et un `.accessDeniedHandler(...)` renvoyant `403` dans `SecurityConfig.java:53-59`, autoriser `/error`, et gérer `AccessDeniedException` dans `GlobalExceptionHandler` en `403` (avant le catch générique `Exception`). | **S** |
| M8 | `L5` | **`DataSeedProfileTest` en échec (`105/106`), domaine `favori` à `46,7 %` (`< 60 %`) et `0` test frontend** | Annoter `DatabaseConfig` avec `@Profile("!test")` (ou ne pas écraser l'URL H2 en test), ajouter `FavoriControllerTest` + tests sur `HealthController`/`UploadController`/`GlobalExceptionHandler` pour passer tous les packages `>= 60 %`, et ajouter une suite Vitest/Playwright côté React. | **M** |
| M9 | `L4`, `L2`, `L3` | **Documentation désynchronisée** : `docs/openapi.json` et Postman obsolètes (44 routes manquantes, 4 routes mortes), `README.md` incomplet (`.env` et 8 variables manquants, technos erronées) et UML Mermaid cassé/incomplet | Régénérer `docs/openapi.json` depuis `/v3/api-docs` après ajout des `@Operation`, mettre à jour la collection Postman, documenter `.env.example` + toutes les variables dans `README.md`, remplacer `usecaseDiagram` par `flowchart LR` et compléter les diagrammes de classes/séquences avec les 13 entités. | **S** |

### 3.3 Écarts Mineurs (4)

| # | Réf. | Écart constaté | Correctif proposé | Effort |
| :-: | :--- | :--- | :--- | :-: |
| m1 | `T2` | **20 endpoints REST sans `@Operation` (`summary`/`description`) et 4 sans `description` dans `/v3/api-docs`** | Annoter avec `@Operation(summary = ..., description = ...)` et `@Tag` les méthodes de `MessagerieRestController`, `RendezVousController`, `TransactionController` et `FavoriController`. | **S** |
| m2 | `F21` | **Notation propriétaire calculée indirectement via les notes des logements** | Créer une entité dédiée `AvisProprietaireEntity` (ou des sous-notes accueil/ponctualité/transparence dans `AvisEntity`) et exposer le détail sur le profil bailleur. | **M** |
| m3 | `S5` | **LCP mobile Fast 3G à `3,84 s` (`> 2 s`) sur la fiche annonce et absence de `loading="lazy"` sur les cartes de recherche** | Ajouter `loading="lazy"` et `decoding="async"` sur `SearchResultsPage.tsx:222`, compresser les photos en WebP redimensionnées à l'upload, et charger `LeafletMap` en `React.lazy` sous la ligne de flottaison (`IntersectionObserver`). | **S** |
| m4 | `T10` | **Score Accessibilité Lighthouse à `89/100` (4 règles WCAG en échec)** | Ajouter `aria-label` sur les boutons/liens icônes du `Header` et `FavoriteButton`, remplacer `<h3>` par `<h2>` dans `PropertyDetailPage.tsx:396`, et assombrir `terracotta-500` (`#c2410c` ou `#b83b16`) avec texte blanc pour atteindre un ratio `>= 4.5:1`. | **S** |

---

## 4. Au-delà du cahier (fonctionnalités non demandées mais implémentées)

| Fonctionnalité | Description | Preuve vérifiée le 2026-10-30 |
| :--- | :--- | :--- |
| **1. Système complet de Favoris** | Sauvegarde/retrait d'annonces en favoris, compteur temps réel, anti-doublon atomique (`409 CONFLICT`) et page dédiée `/favoris`. | `V6__favoris.sql`, `FavoriController.java:29-62`, `FavoriService.java`, `FavorisPage.tsx`, `FavoriteButton.tsx` ; testé en live dans `docs/audit/api_proofs.json` (`201`, `409`, `count=1`, `204`) et `FavoriServiceTest` (5/5). |
| **2. Messagerie instantanée temps réel WebSocket / STOMP** | Discussions privées locataire–propriétaire rattachées à une annonce, diffusion temps réel via STOMP `/ws` (`/topic/conversations/{id}`) authentifiée par JWT, tiroir `ChatDrawer` et compteur de non-lus. | `V7__messagerie.sql`, `WebSocketConfig.java`, `StompAuthChannelInterceptor.java`, `MessagerieRestController.java`, `MessagerieStompController.java`, `ChatDrawer.tsx`, `ConversationsTab.tsx` ; vérifié via `POST /api/v1/annonces/{id}/conversations` (`201`) + `GET /non-lus/count` (`200`) dans `docs/audit/api_proofs.json` et `MessagerieStompIntegrationTest` (3/3). |
| **3. Tableau de bord analytique Admin & graphiques Recharts** | Agrégation multi-périodes (`7j`, `30j`, `90j`, `tout`) des KPI utilisateurs, annonces, entonnoir RDV->Transaction->Bail, montants séquestrés/libérés, modération et métriques d'usage IA, mise en cache 60 s. | `AdminStatistiquesController.java:25`, `AdminStatistiquesService.java`, `CacheConfig.java:18-25`, `AdminDashboardPage.tsx` ; vérifié via `GET /api/v1/admin/statistiques` (`200 OK` dans `docs/audit/api_proofs.json`), `AdminStatistiquesControllerTest` (3/3) et capture `docs/audit/screenshots/15_admin_dashboard_statistiques_f26.png`. |
| **4. Inspection binaire anti-spoofing (Magic Bytes)** | Validation des octets d'en-tête réels (`FF D8 FF` JPEG, `89 50 4E 47` PNG, `RIFF....WEBP`) sur chaque fichier téléversé afin de bloquer tout script exécutable renommé en image. | `FileStorageService.java`, `MagicBytesValidationTest` (2/2 passés) et `FileStorageServiceTest` (7/7 passés). |
| **5. Protection anti-brute-force sur l'authentification** | Limitation de débit par couple IP/email sur `POST /api/v1/auth/connexion` (blocage HTTP `429 Too Many Requests` après 5 échecs consécutifs) et messages anti-énumération. | `LoginRateLimiterService.java`, `AuthController.java:38-55` ; vérifié par `LoginRateLimiterServiceTest` (2/2 passés). |
| **6. Calcul déterministe serveur anti-falsification du séquestre** | Ignorance délibérée de tout montant envoyé par le client lors de `POST /api/v1/annonces/{id}/transactions` : calcul exclusif depuis le prix en base. | `TransactionService.java:68-78` ; prouvé dans `docs/audit/api_proofs.json` (envoi de `montantCaution: 1` -> `montantCaution` forcé à `250 000 FCFA` par le serveur) et `WorkflowTransactionIntegrationTest`. |
| **7. Garde anti-injection de prompt IA** | Filtrage préventif de longueur (`<= 500` car.) et de motifs d'injection (`ignore previous instructions`, `system prompt`…) avant tout appel LLM. | `RuleBasedCriteriaExtractor.java:18-25,170-185` ; vérifié par `RuleBasedCriteriaExtractorTest` (5/5 passés). |
| **8. Indicateur réseau hors-ligne & respect de `prefers-reduced-motion`** | Bannière dynamique lors des coupures réseau (`OfflineIndicator`) et désactivation automatique des transitions `framer-motion` si l'utilisateur active la réduction des mouvements. | `OfflineIndicator.tsx` (capture `docs/audit/screenshots/17_offline_pwa_sw_t4.png`), `SearchResultsPage.tsx:19,188-214`, `HomePage.tsx`. |

---

## 5. Métriques du code, de l'architecture et des bundles (`docs/audit/metrics.json`, `docs/audit/bundle_sizes.json`)

### 5.1 Volumétrie du code source (hors tests et hors fichiers générés `target/`, `dist/`, `node_modules/`)

| Périmètre | Fichiers | Lignes totales | SLOC (hors lignes vides et commentaires) | Détail structurel |
| :--- | :-: | :-: | :-: | :--- |
| **Backend Java (`backend/src/main/java`)** | **142** *(+ 28 `package-info.java` = 170)* | **8 004** | **6 705** | **17 contrôleurs** (**53 endpoints REST** + **1 endpoint STOMP**), **13 entités JPA (`@Entity`)** |
| **Migrations SQL Flyway (`db/migration`)** | **10** (`V1` à `V10`) | **245** | **198** | PostgreSQL 16 (`utilisateurs`, `annonces`, `photos`, `documents`, `ia`, `rdv`, `transactions`, `avis`, `signalements`, `favoris`, `messagerie`) |
| **Configuration Backend (`resources/*.yml`)** | **3** | **87** | **74** | `application.yml`, `application-dev.yml`, `application-prod.yml` |
| **Frontend React/TS (`frontend/src`, hors `.d.ts`)** | **48** (`.ts`, `.tsx`, `.css`) | **8 142** | **7 373** | **11 pages**, **25 composants UI/métier**, stores Zustand, services API/WS |
| **Tests automatisés Backend (`backend/src/test`)** | **30** classes | **4 511** | — | **106 tests JUnit 5 / MockMvc** (`105` réussis, `1` erreur ; JaCoCo `73,1 %` instr. / `78,8 %` lignes) |
| **Tests automatisés Frontend** | **0** | **0** | **0** | Aucune suite Vitest / Jest / Playwright dans `frontend/` |

### 5.2 Taille du build de production Frontend (`vite build` -> `dist/`)

- **Total du dossier `dist/` (47 fichiers, incluant images statiques et Service Worker)** : **`1 832 355 octets` (`1,75 Mio`) brut** · **`721 499 octets` (`704,6 Kio`) gzip**.
- **Principaux chunks générés** :
  - `assets/AdminDashboardPage-27v0mGHX.js` (Recharts, chargé à la demande) : `426,0 Ko` brut / `121,8 Ko` gzip
  - `assets/vendor-react-DNiAfDM5.js` (React + Router + TanStack Query) : `247,8 Ko` brut / `80,9 Ko` gzip
  - `hero-dakar-bg.jpg` (image hero page d'accueil) : `233,6 Ko` brut
  - `assets/vendor-leaflet-zcXIbfmv.js` (Leaflet) : `198,9 Ko` brut / `60,1 Ko` gzip
  - `assets/vendor-motion-ahEpz3f1.js` (Framer Motion) : `129,0 Ko` brut / `42,4 Ko` gzip
  - `assets/DashboardPage-x8Uwlf9b.js` : `120,3 Ko` brut / `32,8 Ko` gzip
  - `assets/index-DUBKqNuR.js` (entrée principale) : `101,9 Ko` brut / `30,9 Ko` gzip
  - `assets/vendor-radix-B2aMrg1q.js` : `78,3 Ko` brut / `26,5 Ko` gzip
  - `assets/PropertyDetailPage-xo3OhQzP.js` : `59,9 Ko` brut / `19,9 Ko` gzip
  - `assets/index-DDVMUsJ4.css` (Tailwind CSS purgé) : `60,4 Ko` brut / `10,2 Ko` gzip
  - `workbox-dc521307.js` (runtime PWA) : `22,1 Ko` brut / `7,5 Ko` gzip

### 5.3 Dépendances principales et versions exactes

- **Backend (`backend/pom.xml`)** : Java `21`, Spring Boot `3.4.3` (`web`, `websocket`, `data-jpa`, `security`, `validation`, `test`), PostgreSQL Driver (BOM Spring Boot `42.7.x`), Flyway (`flyway-core` + `flyway-database-postgresql`, BOM `10.20.x`), Springdoc OpenAPI `2.7.0`, JJWT `0.12.6`, LibrePDF OpenPDF `2.0.3`, Lombok, H2 (scope `test`), JaCoCo Maven Plugin `0.8.12`. *(NB : ni `LangChain4j` ni `Bucket4j`, pourtant cités dans `README.md:48,51`, ne figurent dans `pom.xml`.)*
- **Frontend (`frontend/package.json`)** : React `^18.3.1`, TypeScript `^5.7.3`, Vite `^6.1.0`, React Router DOM `^7.1.5`, TanStack React Query `^5.66.0`, Zustand `^5.0.3`, React Hook Form `^7.54.2` + Zod `^3.24.2`, Tailwind CSS `^3.4.17`, Radix UI (`dialog`, `select`, `slider`, `tabs`, `tooltip`, `dropdown-menu`, `popover`), Leaflet `^1.9.4` + React-Leaflet `^4.2.1` + Cluster `^2.1.0`, `@stomp/stompjs` `^7.3.0`, Framer Motion `^12.4.7`, Recharts `^3.10.1`, Yet Another React Lightbox `^3.21.7`, Sonner `^2.0.1`, Lucide React `^0.475.0`, `vite-plugin-pwa` `^1.3.0` + Workbox `^7.4.1`.

---

## 6. Limites actuelles, contournements et dette technique

1. **Paiement Wave entièrement simulé en mémoire (`WaveClientMock.java`)** : Aucune intégration HTTP ni webhook HMAC vers l'API Wave Sandbox réelle n'est cablée ; l'appel `POST /paiement-caution` génère immédiatement un identifiant `WAVE-SANDBOX-XXXXXXXX` et passe le séquestre à `BLOQUE` sans redirection de paiement mobile ni confirmation asynchrone.
2. **SMS entièrement simulés par logs stdout et planificateur `@Scheduled` muet** : Aucune passerelle SMS (Twilio, Orange SMS Sénégal, Infobip) ni table de traçabilité n'est branchée ; en outre, l'oubli de `@EnableScheduling` empêche l'exécution automatique quotidienne à 08h00 (`RendezVousService.java:129`).
3. **Stockage local des fichiers **éphémère et non persistant** sur l'hébergement gratuit Render** : Les photos d'annonces (`uploads/annonces/...`), les pièces cadastrales (`uploads/documents/...`) et les baux PDF signés (`uploads/baux/bail_*.pdf` dans `PdfBailGeneratorService.java:23`) sont écrits sur le système de fichiers local du conteneur Docker. Sur le plan **Render Free** (`render.yaml:14`), aucun disque persistant (`Render Disk`) n'est attaché (et n'est disponible en gratuit) : **chaque redémarrage après mise en veille (15 min d'inactivité) ou redéploiement efface toutes les photos, pièces justificatives et baux PDF téléversés**, laissant en base PostgreSQL des URLs mortes (`404`). Une migration vers un stockage objet (Cloudflare R2, AWS S3, Supabase Storage ou Cloudinary) est indispensable en production.
4. **Cache et limitation de débit stockés en mémoire JVM locale** : `ExpiringConcurrentMapCache` (`CacheConfig.java`) et la `ConcurrentHashMap` de `LoginRateLimiterService.java` ne sont pas partagés entre instances, perdent leur état à chaque redémarrage/sommeil Render, et `LoginRateLimiterService` ne purge pas périodiquement les clés expirées non réinterrogées (risque de croissance mémoire sous attaque par dictionnaire d'adresses IP/emails).
5. **Quota très restrictif de l'API Gemini Free Tier (`5 RPM` / `20 RPD`)** : Sur `gemini-2.5-flash` en offre gratuite, le chatbot épuise son quota journalier en 20 messages pour toute la plateforme puis retombe pendant ~24 h sur `RuleBasedCriteriaExtractor` dont la couverture linguistique est beaucoup plus faible (`42,9 %` au protocole `S1`).
6. **Contournement de configuration `DatabaseConfig.java` cassant les tests `@SpringBootTest` multi-profils** : `DatabaseConfig.java` convertit manuellement les URLs `postgres://` Render mais injecte par défaut `jdbc:postgresql://localhost:5432/ecourtier_db` même sous H2 lorsque le profil `prod` est testé (`DataSeedProfileTest` en erreur).
7. **Dette de sécurité HTTP sur les erreurs d'autorisation (`403` transformés en `401`)** : L'absence d'`accessDeniedHandler` dans `SecurityConfig.java:53-59` combinée au verrouillage de `/error` transforme toute exception `@PreAuthorize` d'un utilisateur connecté en réponse `401` mal encodée, ce qui peut provoquer côté client des déconnexions intempestives au lieu d'un simple message « Accès refusé ».
