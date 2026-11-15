# RAPPORT DE LIVRAISON — PHASE 3 : SÉCURISATION ET FIABILISATION D'E-COURTIER (HORS WAVE)

**Date :** 15 novembre 2026  
**Périmètre :** Backend Spring Boot 3.4.3 (Java 21), Base de données PostgreSQL 16 (Flyway V1–V20), Frontend React 19 / TypeScript / Vite / PWA, Contrat OpenAPI 3.0.1 (`docs/openapi.json` & `backend/src/main/resources/static/openapi.json`), Tests Unitaires, Intégration H2 & PostgreSQL 16 (Testcontainers), ESLint v10 et E2E Playwright (Mockés + Full-Stack Backend Réel).  
**Exclusion stricte respectée :** **Aucune modification** n'a été apportée au périmètre Wave (`WaveClient`, `WaveClientMock`, `WaveWebhookController`, `WaveWebhookService`, `WavePaymentReconciliationService`, `PaiementWaveEntity`, `PaiementWaveRepository`, table `paiements_wave`, logique de paiement/remboursement Wave, ni migrations Flyway historiques `V1` à `V19`).

---

## 1. Synthèse Exécutive et Tableau Récapitulatif des Chantiers (Phases A à H)

| Phase / Chantier | Anomalie(s) Contre-Audit Phase 2 | Statut | Fichiers Principaux Modifiés / Créés | Preuve de Vérification |
| :--- | :--- | :---: | :--- | :--- |
| **Phase A1 — Révocation immédiate des Access Tokens JWT (`token_version`)** | Les Access Tokens JWT restaient acceptés jusqu'à expiration (1h) après `POST /api/v1/auth/deconnexion`, changement de mot de passe, réutilisation frauduleuse d'un Refresh Token ou désactivation administrative ; fermeture WebSocket exécutée avant commit DB. | **CORRIGÉ & VÉRIFIÉ** | `V20__phase3_security_pdf_and_scheduler_locks.sql`, `UtilisateurEntity.java`, `UtilisateurRepository.java`, `JwtService.java`, `JwtAuthenticationFilter.java`, `AuthService.java`, `UtilisateurManagementService.java` | `AuthAccessTokenRevocationIntegrationTest` (3/3), `AdminUtilisateurIntegrationTest` (5/5), `Postgres16Phase3IntegrationTest` (Test 3), E2E réel `phase3-real-backend.spec.ts` (Test 1) |
| **Phase A2 — Rate Limiters bornés en mémoire (`BoundedRateLimiterSupport`)** | `ConcurrentHashMap` non bornées dans `LoginRateLimiterService`, `RefreshRateLimiterService`, `UploadRateLimiterFilter`, `IaRateLimiterService` (risque de saturation mémoire / DoS). | **CORRIGÉ & VÉRIFIÉ** | `BoundedRateLimiterSupport.java`, `LoginRateLimiterService.java`, `RefreshRateLimiterService.java`, `UploadRateLimiterFilter.java`, `IaRateLimiterService.java` | `RefreshRateLimiterServiceTest` (3/3), `LoginRateLimiterServiceTest` (5/5), `IaRateLimiterServiceTest` (4/4), `UploadRateLimiterFilterTest` (3/3) |
| **Phase A3 — Durcissement `ProductionConfigurationValidator` (Hors Wave)** | Absence de validation stricte au démarrage en profil `prod` pour `ecourtier.cors.allowed-origins` (HTTPS obligatoire, pas de `*` ni `localhost`) et la clé API IA (`GROQ_API_KEY` / `IA_API_KEY` lorsque `ecourtier.ia.enabled=true`). | **CORRIGÉ & VÉRIFIÉ** | `ProductionConfigurationValidator.java` | `ProductionConfigurationValidatorTest` (14/14), `DataSeedProfileTest` (1/1) |
| **Phase B1 — Verrouillage optimiste `@Version` obligatoire sur `PUT /api/v1/annonces/{id}`** | Le champ `version` était facultatif dans `AnnonceUpdateDTO`, permettant d'écraser silencieusement une annonce modifiée concurremment. | **CORRIGÉ & VÉRIFIÉ** | `AnnonceUpdateDTO.java`, `AnnonceResponseDTO.java`, `AnnonceMapper.java`, `AnnonceService.java`, `GlobalExceptionHandler.java` | `AnnonceOptimisticLockIntegrationTest` (3/3), `Postgres16Phase3IntegrationTest` (Test 2), E2E réel `phase3-real-backend.spec.ts` (Test 2) |
| **Phase B2 — Suppression du contournement Admin sur `supprimerAnnonceLogique`** | La surcharge `supprimerAnnonceLogique(UUID)` sans contrôle des transactions/séquestres actifs permettait d'archiver une annonce engagée. | **CORRIGÉ & VÉRIFIÉ** | `AnnonceService.java`, `AdminAnnonceController.java` | `AnnonceSuppressionSecurityIntegrationTest` (4/4), `AnnonceServiceTest` |
| **Phase C — Fiabilisation de la génération PDF du bail et reprise sur erreur** | Écriture non atomique du PDF de bail, erreur d'E/S avalée silencieusement lors de la double signature, et absence de mécanisme de régénération/reprise. | **CORRIGÉ & VÉRIFIÉ** | `V20__phase3_security_pdf_and_scheduler_locks.sql`, `StatutDocumentBail.java`, `TransactionEntity.java`, `TransactionResponseDTO.java`, `TransactionMapper.java`, `PdfBailGeneratorService.java`, `TransactionService.java`, `TransactionController.java`, `BailPdfRecoveryScheduler.java` | `PdfBailGeneratorServiceTest` (4/4), `BailPdfReliabilityIntegrationTest` (3/3), `Postgres16Phase3IntegrationTest` (Test 4), E2E réel `phase3-real-backend.spec.ts` (Test 3) |
| **Phase D — Sécurité WebSocket STOMP en cours de session** | Vérification du JWT uniquement au `CONNECT` initial ; absence de contrôle d'expiration/révocation sur `SEND`/`SUBSCRIBE`, à la diffusion sortante et par balayage périodique ; fuite mémoire en cas de fermeture sans `DISCONNECT` explicite. | **CORRIGÉ & VÉRIFIÉ** | `WebSocketAuthInterceptor.java`, `WebSocketSessionRegistry.java`, `WebSocketConfig.java`, `MessagerieService.java` | `WebSocketSessionSecurityIntegrationTest` (4/4), `MessagerieStompIntegrationTest` (3/3), `MessagerieSecurityTest` (8/8) |
| **Phase E — Uniformisation `Clock` (`Africa/Dakar`) & Verrou distribué des Schedulers** | Persistance d'appels `LocalDateTime.now()` implicites et d'`@CreationTimestamp` Hibernate ; exécution concurrente non coordonnée des `@Scheduled` (hors Wave) en déploiement multi-instances. | **CORRIGÉ & VÉRIFIÉ** | `V20__phase3_security_pdf_and_scheduler_locks.sql`, `SchedulerLockService.java`, `TransactionExpirationScheduler.java`, `BailPdfRecoveryScheduler.java`, `RendezVousService.java`, `IAService.java`, `AnnonceService.java`, entités JPA (`@PrePersist`) | `SchedulerLockServiceTest` (3/3), `IAServiceClockTest` (2/2), `TransactionClockAndRefundDossierIntegrationTest` (3/3), `Postgres16Phase3IntegrationTest` (Test 2) |
| **Phase F — Frontend React/TS, UI Admin Statut Annonce, Régénération PDF & ESLint v10** | Absence d'action UI dédiée `PUT /api/v1/admin/annonces/{id}/statut` dans `AdminVerificationPage.tsx`, absence de bouton de régénération PDF sur `EN_ERREUR`, absence de configuration ESLint v10 (`eslint.config.js`), et débordement horizontal mobile 375px. | **CORRIGÉ & VÉRIFIÉ** | `frontend/eslint.config.js`, `AdminVerificationPage.tsx`, `TransactionDetailsModal.tsx`, `DashboardPage.tsx`, `EditAnnonceModal.tsx`, `PropertyDetailPage.tsx`, `OfflineIndicator.tsx`, `App.tsx`, `index.css`, `api.ts`, `types/index.ts` | `npm --prefix frontend run lint` (0 erreur, 0 warning), `npm --prefix frontend run build` (OK), E2E Mockés (28/28), E2E Backend Réel (5/5) |
| **Phase G — Synchronisation complète du contrat OpenAPI 3.0.1** | Désynchronisation entre les contrôleurs Spring Boot et `docs/openapi.json` / `backend/src/main/resources/static/openapi.json` (endpoints Phase 2/3, paramètres `Pageable`, `@SecurityRequirements` publics, codes HTTP). | **CORRIGÉ & VÉRIFIÉ** | 19 contrôleurs REST hors Wave annotés, `docs/openapi.json`, `backend/src/main/resources/static/openapi.json`, `OpenApiContractIntegrationTest.java` | `OpenApiContractIntegrationTest` (1/1), `cmp -s docs/openapi.json backend/src/main/resources/static/openapi.json` (`OPENAPI_IDENTICAL`, 56 chemins) |

---

## 2. Détail Technique des Corrections Implémentées

### 2.1. Migration Flyway Additive `V20__phase3_security_pdf_and_scheduler_locks.sql`
La migration `backend/src/main/resources/db/migration/V20__phase3_security_pdf_and_scheduler_locks.sql` a été créée sans modifier aucune migration existante (`V1` à `V19`) :
1. **`utilisateurs.token_version`** (`BIGINT NOT NULL DEFAULT 0`) : compteur monotone par utilisateur permettant la révocation immédiate de tous les Access Tokens JWT émis avant une déconnexion, un changement de mot de passe, une détection de réutilisation de Refresh Token ou une désactivation de compte.
2. **Suivi d'état de génération du PDF de bail sur `transactions`** :
   - `statut_document_bail VARCHAR(30) NOT NULL DEFAULT 'NON_GENERE'` avec contrainte `chk_transactions_statut_document_bail CHECK (statut_document_bail IN ('NON_GENERE', 'GENERE', 'EN_ERREUR'))` et rétro-alimentation automatique à `'GENERE'` lorsque `url_document_bail` est déjà renseigné.
   - `pdf_bail_erreur VARCHAR(500)`, `pdf_bail_tentatives INTEGER NOT NULL DEFAULT 0`, `pdf_bail_derniere_tentative TIMESTAMP`.
3. **Table de verrouillage distribué `scheduler_locks`** :
   - Clé primaire `lock_name VARCHAR(120)`, `locked_by VARCHAR(160) NOT NULL`, `locked_at TIMESTAMP NOT NULL`, `locked_until TIMESTAMP NOT NULL`, et index `idx_scheduler_locks_until`.

### 2.2. Phase A — Authentification, Révocation JWT, Rate Limiting Borné & Validation Prod
- **Révocation synchrone des Access Tokens (`token_version`)** :
  - `JwtService.generateAccessToken(UtilisateurEntity)` inscrit désormais le claim `"tv"` (`tokenVersion`) dans chaque Access Token.
  - `JwtAuthenticationFilter` vérifie à chaque requête HTTP authentifiée que le claim `"tv"` du jeton correspond exactement à `utilisateur.getTokenVersion()` en base ; toute divergence renvoie immédiatement `401 Unauthorized`.
  - `UtilisateurRepository.incrementTokenVersion(UUID)` (`UPDATE UtilisateurEntity u SET u.tokenVersion = COALESCE(u.tokenVersion, 0) + 1 WHERE u.id = :userId`) est invoqué lors de :
    1. `AuthService.deconnecter(...)` (`POST /api/v1/auth/deconnexion`) ;
    2. `AuthService.rafraichirToken(...)` en cas de détection de réutilisation frauduleuse d'un Refresh Token déjà révoqué ;
    3. `UtilisateurManagementService.changerMotDePasse(...)` (`PUT /api/v1/utilisateurs/moi/mot-de-passe`) ;
    4. `UtilisateurManagementService.changerStatutActif(...)` (`PUT /api/v1/admin/utilisateurs/{id}/statut` lors du passage à `actif=false`).
  - **Fermeture WebSocket après validation transactionnelle (`afterCommit`)** : l'appel à `webSocketSessionRegistry.closeAndRemoveAllSessions(email, reason)` est enregistré via `TransactionSynchronizationManager.registerSynchronization(afterCommit)` afin d'éviter toute course où le client se reconnecterait avant que le commit de révocation en base ne soit visible.
- **Protection mémoire des Rate Limiters (`BoundedRateLimiterSupport`)** :
  - `BoundedRateLimiterSupport` applique une capacité maximale stricte (`MAX_BUCKETS = 10_000`), purge les entrées expirées et évince les entrées les plus anciennes sous verrou en cas de dépassement.
- **Validation de configuration de production (`ProductionConfigurationValidator`)** :
  - En profil `prod`/`production`, vérifie désormais : secret JWT robuste ($\ge 32$ octets, hors secrets de développement connus), mot de passe DB non vide et non trivial, origines CORS (`ecourtier.cors.allowed-origins`) obligatoirement en `https://` sans joker `*` ni hôte local (`localhost`, `127.0.0.1`, `0.0.0.0`, `::1`), et clé API IA (`GROQ_API_KEY` / `IA_API_KEY`) obligatoire lorsque `ecourtier.ia.enabled=true` avec un fournisseur externe actif. Aucune règle Wave n'y a été ajoutée.

### 2.3. Phase B — Verrouillage Optimiste `@Version` et Sécurité de Suppression d'Annonce
- **Verrouillage optimiste obligatoire (`B1`)** :
  - `AnnonceUpdateDTO.version` est annoté `@NotNull(message = "La version de l'annonce est obligatoire pour prévenir les conflits de modification concurrente")`.
  - `AnnonceService.modifierAnnonce` compare `dto.getVersion()` à `annonce.getVersion()` (et intercepte `ObjectOptimisticLockingFailureException` dans `GlobalExceptionHandler`) pour renvoyer systématiquement `409 Conflict` en cas de modification concurrente.
  - `AnnonceResponseDTO` expose `version` afin que le frontend (`EditAnnonceModal.tsx`) transmette la version courante lors de chaque `PUT /api/v1/annonces/{id}`.
- **Unification de la suppression logique (`B2`)** :
  - La surcharge `supprimerAnnonceLogique(UUID)` a été supprimée de `AnnonceService`.
  - `AdminAnnonceController` et `AnnonceController` passent exclusivement par `supprimerAnnonceLogique(UUID id, String userEmail)`, qui bloque toute suppression logique avec `409 Conflict` si une transaction active (`transactionRepository.existsActiveByAnnonceId(id)`) existe sur l'annonce.

### 2.4. Phase C — Écriture Atomique du PDF de Bail et Reprise sur Erreur
- **Écriture atomique et validation d'intégrité (`PdfBailGeneratorService`)** :
  - Le document PDF est écrit dans un fichier temporaire `.tmp` dans le répertoire cible, vérifié (taille $> 0$ et en-tête `%PDF-`), puis déplacé atomiquement via `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` (avec repli `REPLACE_EXISTING` si le système de fichiers ne supporte pas `ATOMIC_MOVE`), et le fichier `.tmp` est nettoyé en bloc `finally`.
- **Suivi d'état (`StatutDocumentBail`) et résilience transactionnelle (`TransactionService`)** :
  - Lorsque les deux parties ont signé (`StatutBail.SIGNE_COMPLET`), si la génération PDF réussit, `statutDocumentBail` passe à `GENERE` et `urlDocumentBail` est renseigné.
  - Si une erreur survient lors de la génération PDF, la double signature et la libération du séquestre sont préservées, tandis que `statutDocumentBail` passe à `EN_ERREUR` avec enregistrement de `pdfBailErreur`, `pdfBailTentatives` et `pdfBailDerniereTentative`.
- **Régénération à la demande et tâche planifiée de reprise** :
  - Nouvel endpoint `POST /api/v1/transactions/{id}/bail/pdf/generer` autorisant le locataire, le propriétaire ou un administrateur à régénérer immédiatement le PDF d'un bail `SIGNE_COMPLET`.
  - `BailPdfRecoveryScheduler` (toutes les 5 minutes, protégé par `SchedulerLockService`) recherche les transactions `SIGNE_COMPLET` dont `statutDocumentBail != GENERE` (ou `urlDocumentBail == null`) et `pdfBailTentatives < 5`, et relance automatiquement la génération.

### 2.5. Phase D — Sécurité WebSocket STOMP en Cours de Session
- **Contrôle continu sur `SEND` et `SUBSCRIBE` (`WebSocketAuthInterceptor`)** :
  - Chaque trame entrante `SEND` ou `SUBSCRIBE` ré-extrait le JWT associé à la session (ou à l'en-tête `Authorization`), vérifie sa validité cryptographique et son expiration, recharge l'utilisateur en base et vérifie `utilisateur.isActif()` ainsi que l'égalité `tokenVersion == claim("tv")`. En cas d'expiration, de révocation ou de désactivation, la session WebSocket est immédiatement fermée et la trame rejetée (`MessageDeliveryException`).
- **Filtrage sortant et balayage périodique (`WebSocketSessionRegistry` & `MessagerieService`)** :
  - `WebSocketSessionRegistry` stocke le `jwtToken` de chaque session enregistrée, expose `hasValidActiveSession(String email)` vérifié par `MessagerieService` avant l'envoi de notifications temps réel, exécute un balayage périodique `@Scheduled(fixedDelay = 30000)` (`evictExpiredOrRevokedSessions`) fermant proactivement les sessions dont le jeton a expiré ou été révoqué, et nettoie systématiquement les références dans `afterConnectionClosed` du `WebSocketHandlerDecorator`.

### 2.6. Phase E — Horloge `Africa/Dakar` (`Clock`) et Verrou Distribué des Schedulers
- **Horloge unique `Clock` (`Africa/Dakar`)** :
  - Suppression de `@CreationTimestamp` sur les entités JPA au profit de callbacks `@PrePersist` basés sur `ZoneId.of("Africa/Dakar")`, et injection explicite du bean `Clock` dans les services (`AnnonceService`, `IAService`, `RendezVousService`, `TransactionService`, `AuthService`, `SchedulerLockService`).
- **Verrouillage distribué en base (`SchedulerLockService`)** :
  - Basé sur la table `scheduler_locks`, `SchedulerLockService.executeWithLock(lockName, lockDuration, task)` acquiert ou renouvelle atomiquement un bail temporel en base (`REQUIRES_NEW`) avant d'exécuter les 3 tâches planifiées hors Wave :
    1. `TransactionExpirationScheduler.expirerTransactionsInitialiseesAbandonnees` (`lockName = "transaction-expiration-scheduler"`) ;
    2. `BailPdfRecoveryScheduler.recoverFailedBailPdfs` (`lockName = "bail-pdf-recovery-scheduler"`) ;
    3. `RendezVousService.envoyerRappelsAutomatiques` (`lockName = "rdv-rappels-scheduler"`).

### 2.7. Phase F — Frontend React / TypeScript, ESLint v10 & Responsive Mobile
- **Administration du statut de publication (`AdminVerificationPage.tsx`)** :
  - Ajout d'un sélecteur dédié (`DISPONIBLE`, `SUSPENDUE`, `LOUEE`, `VENDUE`) connecté à `PUT /api/v1/admin/annonces/{id}/statut` (`annonceService.changerStatutAnnonceAdmin`) avec mise à jour instantanée et notification toast.
- **Suivi et régénération du PDF de bail (`TransactionDetailsModal.tsx` & `DashboardPage.tsx`)** :
  - Affichage explicite de l'état `statutDocumentBail === 'EN_ERREUR'` avec message d'alerte et bouton **« Régénérer le contrat PDF »** appelant `POST /api/v1/transactions/{id}/bail/pdf/generer` (`transactionService.regenererPdfBail`).
- **Verrouillage optimiste dans l'interface (`EditAnnonceModal.tsx`)** :
  - Transmission systématique de `annonce.version` dans le payload `PUT /api/v1/annonces/{id}` et gestion explicite du code `409 Conflict`.
- **Responsive Mobile 375x667 (`OfflineIndicator.tsx`, `PropertyDetailPage.tsx`, `App.tsx`, `index.css`)** :
  - Correction du débordement horizontal sur viewport mobile (`375x667`) tout en préservant le défilement vertical natif de `window`.
- **Configuration ESLint v10 (`frontend/eslint.config.js`)** :
  - Ajout de la configuration flat config ESLint v10 avec `@eslint/js`, `typescript-eslint`, `eslint-plugin-react-hooks` et `globals`, et correction de l'ensemble des fichiers frontend et E2E pour atteindre **0 erreur et 0 warning**.

### 2.8. Phase G — Synchronisation du Contrat OpenAPI 3.0.1
- Annotations complètes (`@ParameterObject` sur tous les paramètres `Pageable`, `@SecurityRequirements` vides sur les endpoints publics, `@ApiResponses` fidèles aux statuts HTTP réels) sur les **19 contrôleurs REST hors Wave**.
- Régénération depuis `/v3/api-docs` et synchronisation octet par octet entre `docs/openapi.json` et `backend/src/main/resources/static/openapi.json` (**56 chemins documentés**).
- Vérification automatisée dans `OpenApiContractIntegrationTest` couvrant la parité entre les deux fichiers, la présence de `/api/v1/transactions/{id}/bail/pdf/generer`, l'obligation de `version` dans `AnnonceUpdateDTO`, les champs PDF dans `TransactionResponseDTO`, les paramètres `page`/`size` sur les endpoints paginés et `security: []` sur les routes publiques.

---

## 3. Résultats Exacts des Campagnes de Tests et Vérifications

Toutes les vérifications ont été exécutées et validées sans erreur :

1. **Suite Complète Backend (Unitaires Mockito + Intégration Spring Boot / H2)** :
   - Commande : `mvn -f backend/pom.xml -o test`
   - Résultat : **`Tests run: 378, Failures: 0, Errors: 0, Skipped: 5` — `BUILD SUCCESS`**
   - Détail des 5 tests ignorés (`Skipped: 5`) en mode H2 par défaut :
     - `Postgres16Phase3IntegrationTest` (4 tests) : activé uniquement lorsque `-Decourtier.test.pg16=true` est fourni (exécuté séparément contre PostgreSQL 16 ci-dessous).
     - `FlywayFreshMigrationIntegrationTest` (1 test) : activé uniquement lorsque les variables `FLYWAY_PG_URL` sont fournies.

2. **Suite d'Intégration PostgreSQL 16 Réel via Testcontainers (`postgres:16-alpine`, PostgreSQL `16.15`)** :
   - Commande : `mvn -f backend/pom.xml -o test -Dtest=Postgres16Phase3IntegrationTest -Decourtier.test.pg16=true -Dapi.version=1.44`
   - Résultat : **`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS`**
   - Couverture vérifiée sur PostgreSQL `16.15` :
     - Exécution complète à froid des **20 migrations Flyway (`V1` à `V20`)** + validation stricte du schéma Hibernate (`ddl-auto=validate`).
     - Contraintes `CHECK`, index unique partiel `uq_transactions_annonce_active` (`SQLState 23505` sur double transaction active), verrouillage optimiste `@Version` (`ObjectOptimisticLockingFailureException`), exclusion mutuelle de `SchedulerLockService` sur `scheduler_locks`.
     - Révocation immédiate des Access Tokens via `token_version` (`POST /api/v1/auth/deconnexion` $\rightarrow$ `401` sur l'ancien token).
     - Cycle complet de reprise sur erreur PDF (`StatutDocumentBail.EN_ERREUR` $\rightarrow$ `BailPdfRecoveryScheduler` $\rightarrow$ `StatutDocumentBail.GENERE`).

3. **Validation Contrat OpenAPI (`OpenApiContractIntegrationTest` & comparaison binaire)** :
   - Commande : `mvn -f backend/pom.xml -o test -Dtest=OpenApiContractIntegrationTest` et `cmp -s docs/openapi.json backend/src/main/resources/static/openapi.json`
   - Résultat : **`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` — `OPENAPI_IDENTICAL`**

4. **Qualité du Code Frontend (ESLint v10) & Build Production (TypeScript `tsc -b` + Vite + PWA)** :
   - Commande : `npm --prefix frontend run lint && npm --prefix frontend run build`
   - Résultat : **`0 error, 0 warning`** au lint ESLint v10 ; compilation TypeScript et build Vite/Workbox réussis (**61 entrées précachées**).

5. **Suite E2E Playwright Mockée Complète (7 fichiers de spécification)** :
   - Commande : `bash scratch/run-mocked-e2e.sh` (`playwright test` hors `phase3-real-backend.spec.ts`)
   - Résultat : **`28 passed (43.2s), 0 failed`** (`capture-audit-screenshots.spec.ts`, `chatbot-groq-fullstack.spec.ts`, `chatbot-groq-r1-race.spec.ts`, `chatbot-groq-widget.spec.ts`, `lot3-performance-optimizations.spec.ts`, `phase2-security-ui.spec.ts`, `phase9f-dynamic-validation.spec.ts`, `phase9h-admin-refund.spec.ts`).

6. **Suite E2E Playwright Full-Stack sur Backend Spring Boot Réel (`phase3-real-backend.spec.ts`)** :
   - Commande : `bash scratch/run-real-e2e.sh e2e/phase3-real-backend.spec.ts`
   - Résultat : **`5 passed (25.0s), 0 failed`** :
     1. Connexion réelle, accès protégé, `POST /api/v1/auth/deconnexion` réel et rejet immédiat (`401`) de l'Access Token révoqué.
     2. Détection de conflit de modification concurrente (`409 Conflict`) via `@Version` dans `EditAnnonceModal` et rechargement propre.
     3. Reprise et régénération réelle du PDF de bail (`POST /api/v1/transactions/{id}/bail/pdf/generer`) depuis `TransactionDetailsModal` lorsque `statutDocumentBail === 'EN_ERREUR'`.
     4. Changement administratif réel du statut de publication (`PUT /api/v1/admin/annonces/{id}/statut` : `DISPONIBLE` $\rightarrow$ `SUSPENDUE` $\rightarrow$ `DISPONIBLE`) depuis `AdminVerificationPage`.
     5. Affichage temps réel d'un message WebSocket STOMP dans `ConversationsTab` et fermeture immédiate de la session WebSocket après révocation de l'Access Token.
