# RAPPORT DE VALIDATION — LOT 3 : FONCTIONNALITÉS MÉTIER & INTÉGRATION FULL-STACK (E-COURTIER SÉNÉGAL)

**Date :** 15 novembre 2026  
**Dépôt :** `/home/mahou-dev/Documents/Ecourtier2026` — Branche `main` (`HEAD` : `f2bf8a97af5c9fc43c40d25a0ed9eeacfb25f317`)  
**Périmètre :** Audit full-stack des 13 parcours métier, corrections P1 d'intégration (hors Wave), resynchronisation OpenAPI, contre-audit hostile et validation automatisée.  
**Statut Wave :** **100 % HORS PÉRIMÈTRE — AUCUNE MODIFICATION** (classification purement descriptive de son état réel sandbox/mocké).

---

## 1. SYNTHÈSE EXÉCUTIVE

L'audit des 13 parcours métier (Frontend React/TypeScript ↔ API REST/STOMP Spring Boot ↔ Services métier ↔ Persistance JPA/Flyway) a mis en évidence **14 défauts P1 d'intégration** (recherche texte libre `q` déconnectée, re-soumission après rejet bloquée à `REJETEE`, auto-RDV et transitions RDV permissives, auto-signalement/double signalement non bloqués malgré le contrat OpenAPI, course possible sur le rafraîchissement de token entre `api.ts` et `useConversationSocket.ts`, boutons UI exposés à des rôles non autorisés, garde de rôle admin manquante côté UI sur les signalements, badge escrow trompeur, cohérence NAT du rate-limiter IP sur succès).

Toutes les anomalies P1 confirmées **hors Wave** ont été corrigées, accompagnées de **4 corrections d'isolation/synchronisation de tests révélées lors de l'exécution conjointe de la suite complète** (`MessagerieServiceTest`, `AdminUtilisateurIntegrationTest`, `MessagerieStompIntegrationTest`, `AuthIntegrationTest`).

**Verdict Lot 3 : VALIDÉ** (sous réserve explicite des fonctionnalités documentées honnêtement comme *Simulées* ou *Absentes*).

---

## 2. MATRICE COMPLÈTE DES 13 PARCOURS MÉTIER

Légende des classifications :
- **Opérationnelle et testée** : Chaîne complète Frontend → API → Service → PostgreSQL/H2 opérationnelle et couverte par des tests automatisés.
- **Implémentée mais partiellement couverte en E2E** : Logique réelle complète, testée côté backend/UI unitaire, couverture E2E partielle.
- **Partielle** : Fonctionnelle avec limitations identifiées ou sous-parcours absents.
- **Simulée / Mock** : Mécanisme volontairement simulé en local / base de données sans prestataire externe réel.
- **Absente** : Lien ou fonctionnalité non implémentée.

| # | Parcours | Frontend vérifié | API & Contrôleurs | Persistance & Flyway | Autorisations & Sécurité | Classification réelle |
|---|---------|------------------|-------------------|----------------------|--------------------------|---------------------|
| 1 | **Inscription & Connexion** | `LoginPage.tsx`, `RegisterPage.tsx`, `useAuthStore.ts`, `api.ts` | `AuthController` (`/inscription`, `/connexion`, `/rafraichir`, `/deconnexion`) | `utilisateurs`, `refresh_tokens` (`V19`) | BCrypt, JWT accès court + famille refresh, `token_version`, rate limiting IP+email, protection anti-déconnexion par tiers | **Opérationnelle et testée** *(sous-parcours « Mot de passe oublié ? » `#reset` et vérification email/SMS : **Absents**)* |
| 2 | **Gestion des profils & rôles** | `MonComptePage.tsx`, `AdminUtilisateursPage.tsx` | `UtilisateurCompteController` (`GET /moi`, `PUT /mot-de-passe`), `AdminUtilisateurController` | `utilisateurs` | Rétrogradation dernier admin bloquée, désactivation/changement rôle révoquent immédiatement `token_version` & sessions WS | **Opérationnelle et testée** *(édition profil `PUT /utilisateurs/moi` : **Absente**)* |
| 3 | **Création & gestion des annonces** | `DashboardPage.tsx`, `CreateAnnonceModal.tsx`, `EditAnnonceModal.tsx`, `FavorisPage.tsx` | `AnnonceController` CRUD + photos + statut, `FavoriController` | `annonces`, `photos`, `favoris`, `@Version` (`V18`) | `PROPRIETAIRE`/`AGENT` uniquement à la création (`UI` alignée), ownership vérifié, `@Version` 409 Conflict, suppression logique bloquée si bail/séquestre/suspension | **Opérationnelle et testée** |
| 4 | **Recherche & filtrage immobilier** | `HomePage.tsx`, `SearchResultsPage.tsx` | `GET /api/v1/annonces` (`AnnonceSpecification`), `/references` | Index SQL `idx_annonces_*` | Filtres publics excluant `supprime=true`, `LOUEE`, `VENDUE`, `SUSPENDUE` ; paramètre texte libre `q` + `sort=dateCreation,desc` + lien `quartier` corrigés | **Opérationnelle et testée** |
| 5 | **Géolocalisation** | `LeafletMap.tsx`, `LocationPickerMap.tsx`, `QuartierAutocomplete.tsx`, `lib/geo.ts` | `AnnonceController` (coordonnées Sénégal), `/annonces/quartiers` | Colonnes `latitude`, `longitude`, table `annonce_proximites` | Validation bounding box Sénégal (`12.2–16.75`, `-17.6– -11.3`), autocomplétion quartiers réels ; **Proximités = tags énumérés (`ProximiteTag`), PAS de calcul spatial Haversine/PostGIS** | **Partielle** *(carte Leaflet + picker GPS réels ; recherche par rayon GPS absente, tags statiques)* |
| 6 | **Vérification documentaire** | `CreateAnnonceModal.tsx`, `AdminVerificationPage.tsx` | `AnnonceController` (`POST /documents`, `GET /fichier`), `AdminAnnonceController` | `documents_verification` (`V5`–`V20`) | Magic bytes JPEG/PNG/WebP/PDF, taille max 10 Mo, téléchargement privé (auteur/admin), re-soumission après `REJETEE` repasse à `EN_ATTENTE`, badge `VERIFIEE` exclusif admin | **Opérationnelle et testée** |
| 7 | **Assistant IA** | `AiAssistantWidget.tsx` | `IAController` (`/message`, `/historique`), `IAService`, `GroqClientService` | `conversations_ia`, `messages_ia` (`V17`) | Token de session anonyme isolé (`sessionToken` UUID), limite 500 chars (`@Size` + `maxLength`), rate limiter `IaRateLimiterService`, extraction Groq live + repli automatique déterministe `RuleBasedCriteriaExtractor` | **Opérationnelle et testée** *(mode réel si `GROQ_API_KEY` configurée, repli rule-based transparent sinon)* |
| 8 | **Rendez-vous & notifications SMS** | `PropertyDetailPage.tsx`, `DashboardPage.tsx`, `SmsSimulesTab.tsx` | `RendezVousController`, `NotificationSmsController` | `rendez_vous`, `notifications_sms` | Anti-chevauchement `±60 min`, **auto-RDV refusé (`400`)**, machine d'états stricte (`DEMANDE → CONFIRME/ANNULE`, `CONFIRME → EFFECTUE/ANNULE`, refus `409` sinon), rappels planifiés avec verrou distribué (`SchedulerLockService`) | **RDV : Opérationnelle et testée** / **SMS : Simulée** (`SmsGatewayLogImpl`, statut `SIMULE` en DB, sans passerelle Orange/Twilio) |
| 9 | **Messagerie temps réel** | `ChatDrawer.tsx`, `ConversationsTab.tsx`, `useConversationSocket.ts` | `MessagerieRestController`, `MessagerieStompController` | `conversations_chat`, `messages_chat` | JWT validé sur `CONNECT`/`SEND`/`SUBSCRIBE` + isolation participant + balayage 5s `WebSocketSessionRegistry` + verrou de rafraîchissement partagé `refreshSessionToken()` HTTP/WS | **Opérationnelle et testée** |
| 10 | **Transactions & séquestre** | `PropertyDetailPage.tsx`, `DashboardPage.tsx`, `TransactionDetailsModal.tsx`, `AdminTransactionsPage.tsx` | `TransactionController`, `TransactionService` *(Wave Webhook/Client **exclus de toute modification**)* | `transactions`, `paiements_wave` (`V18`–`V19`) | Machine d'états escrow réelle (`INITIALISE → BLOQUE → LIBERE / REMBOURSE / ANNULE / EXPIRE`), index d'unicité partiel `uq_transactions_annonce_active`, expiration planifiée ; **Passerelle de paiement Wave : Simulée (`WaveClientMock`)** | **Séquestre métier : Opérationnelle et testée** / **Paiement Wave : Simulé (hors périmètre)** |
| 11 | **Bail & signature** | `TransactionDetailsModal.tsx` | `TransactionController` (`/bail/signer`, `/bail/pdf`, `/bail/pdf/generer`) | `transactions` (`statut_bail`, `statut_document_bail`, `pdf_bail_*`) | Double signature requise avant libération des fonds, écriture PDF atomique via fichier temporaire + récupération `BailPdfRecoveryScheduler` ; **Signature électronique = horodatage `Africa/Dakar` + tampon textuel OpenPDF (PAS de certificat PKCS#7/X.509)** | **Workflow & PDF : Opérationnelle et testée** / **Signature cryptographique qualifiée : Simulée (horodatage + tampon applicatif)** |
| 12 | **Avis & signalements** | `PropertyDetailPage.tsx`, `SignalementModal.tsx`, `AdminSignalementsPage.tsx` | `AvisController`, `SignalementController` | `avis`, `signalements` | Avis soumis à visite effectuée ou transaction active, auto-signalement (`400`) et doublon (`409`) bloqués, modération admin protégée (pas de réouverture ni suspension si `REJETE`), pagination UI admin + garde `ADMIN` | **Opérationnelle et testée** |
| 13 | **Administration** | `AdminDashboardPage.tsx`, `AdminUtilisateursPage.tsx`, `AdminVerificationPage.tsx`, `AdminSignalementsPage.tsx`, `AdminTransactionsPage.tsx` | `AdminStatistiquesController`, `AdminUtilisateurController`, `AdminAnnonceController`, `SignalementController` | Agrégations SQL PostgreSQL/JPA | `@PreAuthorize("hasRole('ADMIN')")` sur tous les contrôleurs + gardes UI sur toutes les pages admin ; KPIs financiers et volumes calculés depuis la persistance réelle | **Opérationnelle et testée** |

---

## 3. DÉTAIL DES CORRECTIONS P1 APPLIQUÉES (LOT 3)

### 3.1 Backend (Java 21 / Spring Boot 3)

1. **Recherche texte libre `q` end-to-end :**
   - `AnnonceSpecification.filterAnnonces(...)` (`backend/src/main/java/com/ecourtier/domain/annonce/repository/AnnonceSpecification.java`) : ajout de l'argument optionnel `q` filtrant par `LIKE LOWER(%q%)` sur `titre`, `description`, `quartier`, `ville`.
   - `AnnonceService.rechercherAnnonces(...)` et `AnnonceController.rechercherAnnonces(...)` : exposition du `@RequestParam(required = false) String q`.
   - Tests ajoutés : `AnnonceFiltresEtVerificationIntegrationTest.testRechercheTextuelleQ()`.
2. **Re-soumission documentaire après rejet :**
   - `AnnonceService.ajouterDocumentVerification(...)` : si l'annonce est au statut `StatutVerification.REJETEE`, l'upload d'une nouvelle pièce réinitialise `statutVerification` à `EN_ATTENTE`, efface `dateVerification` et nettoie les anciens `motifRejet` pour réintégrer la file de modération admin.
   - Test ajouté : vérification dans `AnnonceFiltresEtVerificationIntegrationTest` (étape 6 après rejet admin).
3. **Règles métier & machine d'états Rendez-vous (`RendezVousService`) :**
   - `demanderRendezVous(...)` : rejet `400 BAD_REQUEST` si le demandeur est le propriétaire de l'annonce.
   - `confirmerRendezVous(...)` : exige le statut `DEMANDE` (sinon `409 CONFLICT`).
   - `annulerRendezVous(...)` : refuse `ANNULE` et `EFFECTUE` (`409 CONFLICT`).
   - `marquerEffectue(...)` : exige `CONFIRME` (`409 CONFLICT`).
   - Tests mis à jour/ajoutés dans `RendezVousServiceTest` (`testAnnulerEtEffectue`, `testDemanderRendezVousSelfRefused`).
4. **Règles anti-abus & modération Signalements (`SignalementRepository`, `SignalementService`) :**
   - `SignalementRepository.existsByAnnonceIdAndAuteurId(UUID, UUID)`.
   - `SignalementService.creerSignalement(...)` : bloque l'auto-signalement (`400 BAD_REQUEST`) et les doublons par auteur/annonce (`409 CONFLICT`), en cohérence exacte avec les annotations `@ApiResponses` de `SignalementController`.
   - `SignalementService.traiterSignalement(...)` : refuse le retour à `EN_ATTENTE` (`400`), le retraitement d'un signalement déjà clos (`409`), et `suspendreAnnonce = true` si `statut == REJETE` (`400`).
   - Tests ajoutés dans `SignalementServiceTest` (`testCreerSignalementSelfReport400`, `testCreerSignalementDuplicate409`, `testTraiterSignalementTransitionsInvalides`).
5. **Robustesse IP NAT du Rate Limiter & Isolation de la suite de tests :**
   - `LoginRateLimiterService.resetAttempts(ip, email)` : décrémente le compteur IP (`ipAttemptsMap`) lors d'une connexion réussie afin que des utilisateurs légitimes partageant un même NAT/campus ne s'auto-bloquent pas au bout de 25 connexions, tout en conservant le blocage strict sur les échecs répétés.
   - Tests mis à jour pour l'exécution conjointe complète (`mvn test`) : `MessagerieServiceTest` (`findByIdAndSupprimeFalse`), `AdminUtilisateurIntegrationTest` (vérification `401` sur token pré-rétrogradation + rechargement entité avant génération nouveau token `403`), `MessagerieStompIntegrationTest.expiredJwtOnOpenStompSession_RejectsSend` (gestion déterministe du socket fermé par balayage 5s), `AuthIntegrationTest` (remise à zéro `LoginRateLimiterService` entre tests via `@BeforeEach`/`@AfterEach`).

### 3.2 Frontend (React / TypeScript / Vite)

1. **`HomePage.tsx` & `SearchResultsPage.tsx` :**
   - Les cartes quartiers de `HomePage.tsx` transmettent désormais `?city=...&quartier=...`.
   - `SearchResultsPage.tsx` gère l'état `qFilter` (synchronisation URL et API `q`) et utilise le format Spring Data `sort=dateCreation,desc` (au lieu de `tri`/`direction` ignorés par `Pageable`).
2. **`CreateAnnonceModal.tsx` :**
   - Prétraitement Zod (`z.preprocess`) sur le champ optionnel `etage` afin qu'un champ vide devienne `undefined` plutôt que `0`.
3. **`api.ts` & `useConversationSocket.ts` :**
   - Extraction d'un verrou partagé `refreshSessionToken()` dans `api.ts` utilisé à la fois par `apiFetch` et `useConversationSocket.ts`, éliminant les doubles appels concurrents `/auth/rafraichir` (source d'auto-révocation par détection de rejeu).
   - Extraction des détails de validation (`errorJson.erreurs`) dans `apiFetch` et fallback `err.error || err.message` dans `downloadPdfBail`.
4. **`AiAssistantWidget.tsx` :**
   - Limite `maxLength={500}` sur la zone de saisie et affichage explicite des réponses backend `400` et `429` au lieu d'un message générique réseau.
5. **`DashboardPage.tsx` & `Header.tsx` :**
   - Boutons `"Créer une annonce"` / `"Publier un bien"` restreints à `PROPRIETAIRE | AGENT` (cohérent avec `@PreAuthorize("hasAnyRole('PROPRIETAIRE', 'AGENT')")`).
   - Onglet `"Mes Annonces"` masqué pour `LOCATAIRE` (qui ouvre directement l'onglet `"VISITES"`), lien `"Mes visites & dossiers"` (`/dashboard?tab=visites`) ajouté dans `Header.tsx`, et support URL `?tab=visites|sequestres`.
   - Boutons RDV `Confirmer` / `Marquer effectué` réservés au propriétaire/agent/admin non-demandeur (`isVisitOwner`), avec bouton `Annuler` disponible pour `DEMANDE` et `CONFIRME`.
6. **`TransactionDetailsModal.tsx` :**
   - Le badge `"Séquestre bloqué"` n'est plus affiché en dur dès que `referenceWave` existe, mais reflète réellement `transaction.statutSequestre` (`BLOQUE`, `LIBERE`, `REMBOURSE`, `EXPIRE`).
7. **`AdminSignalementsPage.tsx` :**
   - Garde de rôle `ADMIN` synchrone (`Navigate`) + condition `enabled: isAdmin` + pagination complète (`Précédent` / `Suivant` avec compteurs).

---

## 4. PREUVES D'EXÉCUTION ET CONTRAT OPENAPI

| Vérification | Commande / Portée | Résultat observé |
|--------------|-------------------|------------------|
| **Suite Backend Lot 3** (`23` classes ciblées : Auth, Admin, Annonce, Favoris, Références, Documents, IA/Groq, RDV, Messagerie REST+STOMP, WorkflowTransaction, Bail PDF, Avis, Signalements, Stats Admin) | `mvn -f backend/pom.xml test -Dtest="Auth*,AdminUtilisateur*,Annonce*,Favori*,Reference*,Document*,IA*,Chatbot*,Groq*,RendezVous*,Messagerie*,WorkflowTransaction*,Bail*,Avis*,Signalement*,AdminStatistiques*"` | **Tests run: 238, Failures: 0, Errors: 0, Skipped: 0** (`BUILD SUCCESS`) |
| **Contrat OpenAPI 3.0.1** (après ajout de `q` sur `GET /api/v1/annonces`) | Export `/v3/api-docs` + `mvn -f backend/pom.xml test -Dtest=OpenApiContractIntegrationTest` | **Tests run: 1, Failures: 0, Errors: 0, Skipped: 0** — SHA-256 identique (`f0329c2d8628a8e077ecc27c4a099f1be863e36bfbe1462f610001761ffe4827`) sur `docs/openapi.json` et `backend/src/main/resources/static/openapi.json` |
| **Qualité Frontend ESLint** | `npm --prefix frontend run lint` | **0 erreur, 0 avertissement** |
| **Typage TypeScript** | `npx --prefix frontend tsc -p frontend/tsconfig.json --noEmit` | **0 erreur** |
| **Build Production Vite + PWA** | `npm --prefix frontend run build` | **Built in 31.79s**, PWA v1.3.0 (`61` entrées précachées) |

---

## 5. CONTRE-AUDIT INDÉPENDANT POST-CORRECTIONS (ÉTAPE E)

- **Absence de régression sur Wave :** `git status --short` confirme qu'aucun fichier Wave (`WaveClient*`, `WaveWebhook*`, `PaiementWave*`) n'a été modifié durant le Lot 3.
- **Risque de boucle infinie HTTP/WS lors de rotation refresh :** supprimé grâce à `refreshSessionToken()` partagé ; un token révoqué déclenche `logout()` une seule fois et vide les promesses en file.
- **Cohérence RBAC Frontend/Backend :**
  - `POST /api/v1/annonces` ↔ UI (`PROPRIETAIRE` / `AGENT` uniquement).
  - `PUT /api/v1/rendez-vous/{id}/confirmer` et `/marquer-effectue` ↔ UI propriétaire non-demandeur ; auto-RDV bloqué côté serveur (`400`).
  - `POST /api/v1/annonces/{id}/signalements` ↔ auto-signalement (`400`) et doublon (`409`) bloqués côté serveur.
  - `/admin/signalements` ↔ garde `ADMIN` frontend + `@PreAuthorize("hasRole('ADMIN')")` backend.
- **Points restants documentés avec transparence (hors P0/P1 bloquants) :**
  1. Réinitialisation de mot de passe (« Mot de passe oublié ? » `#reset`) et édition de profil (`PUT /api/v1/utilisateurs/moi`) absentes du périmètre actuel (documentées dans la matrice).
  2. Notifications SMS (`SmsGatewayLogImpl`), passerelle Wave (`WaveClientMock`, exclue) et signature électronique du bail (horodatage + tampon applicatif OpenPDF sans PKCS#7) explicitement classés **Simulés**.
  3. Validation de l'exécution E2E Playwright bout-en-bout (`phase2-security-ui.spec.ts`, `phase3-real-backend.spec.ts`) et de la couverture JaCoCo globale prévue au **Lot 5**.

---

## 6. VERDICT FINAL LOT 3

> **VERDICT : LOT 3 VALIDÉ.**  
> Les 13 parcours métier sont cartographiés avec exactitude et transparence (réel vs simulé vs absent), toutes les anomalies P1 d'intégration confirmées hors Wave ont été corrigées, le contrat OpenAPI est resynchronisé (SHA-256 `f0329c2d8628a8e077ecc27c4a099f1be863e36bfbe1462f610001761ffe4827`), **238/238** tests backend Lot 3 et le build complet frontend passent sans erreur ni avertissement.
