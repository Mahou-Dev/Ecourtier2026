# RAPPORT DE CONTRE-AUDIT TECHNIQUE INDÉPENDANT — PHASE 3 D'E-COURTIER (HORS WAVE)

**Date de l'audit :** 15 novembre 2026  
**Répertoire racine audité :** `/home/mahou-dev/Documents/Ecourtier2026`  
**Branche & HEAD audités :** `main` (`f2bf8a97af5c9fc43c40d25a0ed9eeacfb25f317`, en avance de 6 commits sur `origin/main`)  
**Rapport audité contradictoirement :** `/home/mahou-dev/Documents/Ecourtier2026/RAPPORT_PHASE_3_SECURISATION_HORS_WAVE_ECOURTIER.md`  
**Posture :** Contre-audit contradictoire indépendant strictement en lecture seule (aucune modification de code, de configuration, de base de données, de branche Git ou des worktrees ; exclusion totale de Wave).

---

## 1. RÉSUMÉ EXÉCUTIF

Ce contre-audit indépendant évalue de manière contradictoire et sur pièces les travaux réalisés lors de la **Phase 3 (Sécurisation et fiabilisation hors Wave)** du projet **E-Courtier Sénégal**, tels que décrits dans `RAPPORT_PHASE_3_SECURISATION_HORS_WAVE_ECOURTIER.md`.

### Constats majeurs
1. **Avancées réelles et vérifiées en Phase 3** :
   - La révocation immédiate des Access Tokens JWT par compteur monotone `utilisateurs.token_version` (claim `"tokenVersion"`) est effective lors du logout authentifié, du changement de mot de passe et de la désactivation administrative, avec coupure des sessions WebSocket STOMP enregistrée en `afterCommit()`.
   - Le verrouillage optimiste sur `PUT /api/v1/annonces/{id}` (`@NotNull` sur `AnnonceUpdateDTO.version`, `SELECT ... FOR UPDATE` + comparaison de version + `flush()` + traduction en HTTP `409 Conflict`) est robuste et prouvé sous concurrence.
   - La suppression logique des annonces (`DELETE /api/v1/annonces/{id}`) est unifiée et atomiquement protégée contre les courses avec `initierTransaction` via un ordre canonique de verrouillage pessimiste (`Annonce -> Transaction`).
   - La génération du PDF de bail utilise un fichier temporaire unique (`.<filename>.<UUID>.tmp`), un déplacement atomique (`ATOMIC_MOVE` avec repli `REPLACE_EXISTING`), un suivi d'état persisté (`NON_GENERE`, `A_GENERER`, `GENERE`, `ECHEC`) sans annuler la transaction en cas d'erreur d'E/S, un endpoint de régénération protégé (`POST /api/v1/transactions/{id}/bail/pdf/generer`) et un scheduler de reprise (`BailPdfRecoveryScheduler`).
   - Le contrôle continu des trames STOMP `CONNECT`, `SUBSCRIBE` et `SEND` (`WebSocketAuthInterceptor`) vérifie la participation à la conversation, l'expiration du JWT, le statut actif du compte et `tokenVersion`, complété par un intercepteur sortant et un balayage périodique toutes les 5 secondes.
   - L'outillage ESLint v10 (`frontend/eslint.config.js`) s'exécute réellement avec **0 erreur et 0 avertissement**, et les deux fichiers OpenAPI (`docs/openapi.json` et `backend/src/main/resources/static/openapi.json`) sont strictement identiques au bit près (`SHA-256: a0696fd513527d7cee18e568985b8c3f20c222e288b1e1de75f04068d7fc5594`, **63 chemins / 70 opérations HTTP**).

2. **Anomalies et écarts contradictoires découverts (0 P0, 4 P1, 9 P2, 4 P3)** :
   - **Aucune anomalie critique P0** n'a été identifiée sur le périmètre hors Wave.
   - **Quatre anomalies de gravité élevée (P1)** empêchent une validation sans réserve de la Phase 3 :
     - **`CA3-001` (P1 — Sécurité JWT)** : Contrairement à l'affirmation explicite du rapport de livraison Phase 3 (§ 1 et § 2.2), la détection de réutilisation frauduleuse d'un Refresh Token (`REVOCATION_REUSE_DETECTED` dans `AuthService.rafraichir`, lignes 196–201) révoque uniquement la famille de Refresh Tokens mais **n'incrémente pas `token_version` et ne coupe pas les sessions WebSocket**. L'attaquant conserve donc un Access Token valide pendant jusqu'à 60 minutes.
     - **`CA3-002` (P1 — Sécurité Déconnexion)** : `POST /api/v1/auth/deconnexion` (`AuthService.deconnecter`, lignes 240–259), exposé en `permitAll()`, accepte sans authentification Bearer n'importe quel Refresh Token **déjà utilisé, déjà révoqué ou expiré** et incrémente `token_version` tout en fermant toutes les sessions WebSocket de l'utilisateur. Un ancien Refresh Token compromis ou expiré permet ainsi de déconnecter la victime en boucle (DoS ciblé), et l'omission de l'en-tête `Authorization` contourne le contrôle d'appartenance (lignes 246–248).
     - **`CA3-003` (P1 — Frontend Transaction UI)** : Dans `TransactionDetailsModal.tsx` (lignes 40–53), un `useMemo` trop restrictif combiné au state statique `selectedTransaction` de `DashboardPage.tsx` (lignes 672–679) ignore `overrideTx` lors du passage `INITIALISE -> BLOQUE` (paiement caution), lors de la 1re signature (`NON_SIGNE -> SIGNE_LOCATAIRE` / `SIGNE_PROPRIETAIRE`) et lors d'un échec PDF (`statutDocumentBail === 'ECHEC'`). La modale ouverte reste figée sur l'ancien état jusqu'à fermeture/réouverture manuelle.
     - **`CA3-004` (P1 — Fonctionnalité annoncée absente du Frontend)** : Contrairement à ce qu'affirme le rapport de livraison Phase 3 (§ 1, § 2.7 et § 3.6 point 4), **aucun sélecteur ni appel à `PUT /api/v1/admin/annonces/{id}/statut` n'a été implémenté dans `AdminVerificationPage.tsx` ni ailleurs dans `frontend/src/`** (et aucun test E2E ne le teste dans `phase3-real-backend.spec.ts`). Une annonce suspendue suite à un signalement ne peut toujours pas être réactivée depuis l'interface administrateur.

- **Note de maturité Phase 3 (hors Wave) :** **78 / 100**
- **Verdict technique :** **`PHASE 3 NON VALIDÉE`** au sens strict de la grille d'audit (§ 20 : présence de 4 anomalies P1 confirmées, malgré l'absence de P0). *Note : Le projet est pleinement apte à une démonstration/soutenance universitaire, mais non prêt pour une mise en production.*

---

## 2. PÉRIMÈTRE ET LIMITES

### 2.1 Périmètre audité
- **Backend Java 21 / Spring Boot 3.4.3** : authentification JWT (`token_version`, rotation/révocation des refresh tokens), limitation de débit (`BoundedRateLimiterSupport`, login, refresh, IA), validation de configuration de production (`ProductionConfigurationValidator`), verrouillage optimiste `@Version` et suppression logique des annonces, génération atomique et reprise des PDF de bail, sécurité WebSocket STOMP (`WebSocketAuthInterceptor`, `WebSocketSessionRegistry`), horloge `Africa/Dakar` (`Clock`), verrouillage distribué des schedulers (`SchedulerLockService`).
- **Base de données & Migrations Flyway** : inspection de `V20__phase3_security_pdf_and_scheduler_locks.sql` et de sa cohérence avec `V1`–`V19` et les entités JPA / PostgreSQL 16.
- **Frontend React 19 / TypeScript / Vite** : pages d'administration, modales d'édition d'annonce et de suivi de transaction, client WebSocket STOMP (`useConversationSocket.ts`), ESLint v10 (`frontend/eslint.config.js`), responsive mobile 375 px.
- **Contrat OpenAPI 3.0.1** : `docs/openapi.json` et `backend/src/main/resources/static/openapi.json` confrontés aux 20 contrôleurs du backend.
- **Suites de tests** : tests unitaires, tests d'intégration H2, test d'intégration Testcontainers PostgreSQL 16 (`Postgres16Phase3IntegrationTest`), tests E2E Playwright mockés et full-stack (`phase3-real-backend.spec.ts`).

### 2.2 Exclusions et limites respectées
- **Exclusion totale de Wave** : conformément à la section 3.4, aucun service, contrôleur, entité, table (`paiements_wave`), secret ou traitement financier Wave n'a été modifié ni sollicité.
- **Non-modification absolue** : aucune commande Git mutante, aucune modification de code ou de configuration, et aucune connexion aux bases de données existantes (`5432` / `5435`) n'ont été effectuées. Seul le présent fichier `/home/mahou-dev/Documents/Ecourtier2026/RAPPORT_CONTRE_AUDIT_PHASE_3_ECOURTIER.md` a été créé après vérification de son absence préalable.

---

## 3. ÉTAT INITIAL GIT ET WORKTREES (ÉTAPE 1)

Relevé effectué avant toute analyse via des commandes strictement informatives (`git rev-parse --show-toplevel`, `git rev-parse HEAD`, `git status -sb -uall`, `git worktree list --porcelain`) :

- **Répertoire racine réel :** `/home/mahou-dev/Documents/Ecourtier2026`
- **Commit `HEAD` :** `f2bf8a97af5c9fc43c40d25a0ed9eeacfb25f317`
- **Branche courante :** `main` (`## main...origin/main [devant 6]` — 6 commits en avance, 0 en retard par rapport à `origin/main` `git@github.com:Mahou-Dev/Ecourtier2026.git`)
- **Conflits de fusion (`U`) :** `0`
- **Fichiers suivis modifiés (`M`) :** `129` fichiers
- **Fichiers non suivis (`??`) :** `68` fichiers (dont `RAPPORT_PHASE_3_SECURISATION_HORS_WAVE_ECOURTIER.md` présent à la racine, 22662 octets / 145 lignes)

### État des 6 Worktrees Git
| Worktree | Chemin | Branche / État | Commit `HEAD` | Observation |
| :--- | :--- | :--- | :--- | :--- |
| **Principal (racine)** | `/home/mahou-dev/Documents/Ecourtier2026` | `refs/heads/main` | `f2bf8a97af5c9fc43c40d25a0ed9eeacfb25f317` | Worktree actif contenant l'ensemble des travaux Phases 1, 2 et 3 non commités. |
| **convergence** | `/home/mahou-dev/Documents/Ecourtier2026-convergence` | `refs/heads/integration/phase-9d-convergence` | `66038410eaa02ec19bf7058d2a194f0eb342210e` | Enregistré dans `.git/worktrees/Ecourtier2026-convergence` (hors périmètre sandbox, préservé intact). |
| **lot1** | `/home/mahou-dev/Documents/Ecourtier2026-lot1` | `detached` | `d161762406ce5f0c685441d13f6e91bc5602338a` | Enregistré dans `.git/worktrees/Ecourtier2026-lot1` (préservé intact). |
| **lot2** | `/home/mahou-dev/Documents/Ecourtier2026-lot2` | `detached` | `d161762406ce5f0c685441d13f6e91bc5602338a` | Enregistré dans `.git/worktrees/Ecourtier2026-lot2` (préservé intact). |
| **lot3** | `/home/mahou-dev/Documents/Ecourtier2026-lot3` | `detached` | `d161762406ce5f0c685441d13f6e91bc5602338a` | Enregistré dans `.git/worktrees/Ecourtier2026-lot3` (préservé intact). |
| **validation** | `/home/mahou-dev/Documents/Ecourtier2026-validation` | `detached` | `d161762406ce5f0c685441d13f6e91bc5602338a` | Enregistré dans `.git/worktrees/Ecourtier2026-validation` (préservé intact). |

---

## 4. MÉTHODOLOGIE D'AUDIT

1. **Photographie initiale non destructive** de l'état Git (`HEAD`, index, fichiers suivis modifiés, fichiers non suivis, métadonnées `.git/worktrees/*`) stockée hors dépôt pour comparaison finale stricte (Section 16).
2. **Séparation des changements par phase** : les travaux des Phases 1, 2 et 3 n'ayant pas été commités séparément sur `main`, l'attribution à la Phase 3 a été établie en croisant l'état constaté dans `RAPPORT_CONTRE_AUDIT_PHASE_2_ECOURTIER.md`, les nouveaux fichiers (`V20`, `SchedulerLock*`, `BoundedRateLimiterSupport`, `StatutDocumentBail`, `BailPdfRecoveryScheduler`, `eslint.config.js`, tests Phase 3) et l'inspection ligne par ligne du code source actuel.
3. **Vérification contradictoire systématique** : chaque affirmation de `RAPPORT_PHASE_3_SECURISATION_HORS_WAVE_ECOURTIER.md` a été confrontée au code réel (appels entrants/sortants, transactions, verrous SQL, concurrence, cas limites) et aux assertions effectives des tests.

---

## 5. FICHIERS ET CHANGEMENTS EXAMINÉS (ÉTAPE 2)

| Fichier | Statut Git | Attribution Phase 3 | Fonctionnalité concernée | Relation avec le rapport Phase 3 |
| :--- | :---: | :---: | :--- | :--- |
| `backend/src/main/resources/db/migration/V20__phase3_security_pdf_and_scheduler_locks.sql` | `??` | Démontrée (Nouveau Phase 3) | `token_version`, colonnes suivi PDF bail, table `scheduler_locks` | Conforme sur l'objectif, mais écarts sur les noms de colonnes/statuts cités dans le rapport (voir § 15 et `CA3-011`/`CA3-017`). |
| `backend/src/main/java/com/ecourtier/common/security/JwtService.java` | `M` | Démontrée (Phase 2 + Phase 3 A1) | Claim `"tokenVersion"` dans l'Access Token et extraction `extractTokenVersion` | Le claim est nommé `"tokenVersion"` (ligne 56) et non `"tv"` comme écrit dans le rapport. |
| `backend/src/main/java/com/ecourtier/common/security/JwtAuthenticationFilter.java` | `M` | Démontrée (Phase 3 A1) | Vérification par requête de `actif` et `tokenVersion` en base | Confirmé (lignes 60–78), avec double requête DB par appel (`CA3-014`). |
| `backend/src/main/java/com/ecourtier/domain/utilisateur/service/AuthService.java` | `M` | Démontrée (Phase 2 + Phase 3 A1) | Rotation refresh token, `deconnecter`, `revokeAccessTokensAndDisconnectWs` | Écart majeur sur le replay refresh token (`CA3-001`) et faille sur `deconnecter` (`CA3-002`). |
| `backend/src/main/java/com/ecourtier/domain/utilisateur/service/UtilisateurManagementService.java` | `M` | Démontrée (Phase 3 A1) | Révocation `token_version` + WS `afterCommit` sur `changerStatut` et `changerMotDePasse` | Confirmé (lignes 153–161, 191–198). Omission sur `changerRole` (`CA3-013`). |
| `backend/src/main/java/com/ecourtier/common/security/BoundedRateLimiterSupport.java` | `??` | Démontrée (Nouveau Phase 3 A2) | Normalisation IP et éviction bornée des buckets de rate limiting | Présent, mais vulnérable à l'éviction depuis une seule IP sur `LoginRateLimiterService` (`CA3-005`). |
| `backend/src/main/java/com/ecourtier/common/security/LoginRateLimiterService.java` & `RefreshRateLimiterService.java` | `M` / `??` | Démontrée (Phase 2 + Phase 3 A2) | Rate limiting login (`5 / 15 min`) et refresh (`20 / 5 min`) | Utilisent `BoundedRateLimiterSupport` (`MAX_ENTRIES = 10_000`). |
| `backend/src/main/java/com/ecourtier/domain/ia/service/IaRateLimiterService.java` | `M` | Phase 2 (Non modifié par Phase 3 A2) | Rate limiting chatbot IA (`5 000` clés max) | N'utilise **pas** `BoundedRateLimiterSupport` ; bloque tous les nouveaux clients en 429 à 5 000 clés (`CA3-005`). |
| `backend/src/main/java/com/ecourtier/common/config/ProductionConfigurationValidator.java` | `M` | Démontrée (Phase 3 A3) | Validation au démarrage en profil `prod`/`production` | Présent, mais oublie le secret dev d'`application-dev.yml:30` et `[::1]` (`CA3-007`). |
| `backend/src/main/java/com/ecourtier/domain/annonce/dto/AnnonceUpdateDTO.java` & `AnnonceService.java` | `M` | Démontrée (Phase 3 B1 & B2) | `@NotNull version`, verrou pessimiste + optimiste `409`, suppression logique unifiée | Confirmé et solide côté `AnnonceService` ; oublis inter-modules sur RDV/Favoris/Messagerie (`CA3-008`). |
| `backend/src/main/java/com/ecourtier/domain/transaction/service/PdfBailGeneratorService.java`, `TransactionService.java`, `BailPdfRecoveryScheduler.java`, `StatutDocumentBail.java` | `M` / `??` | Démontrée (Phase 3 C) | Écriture PDF `.tmp` + `ATOMIC_MOVE`, états `StatutDocumentBail`, reprise planifiée et manuelle | Confirmé dans son fonctionnement ; n'inspecte pas l'en-tête `%PDF-` dans `PdfBailGeneratorService` (`CA3-015`). |
| `backend/src/main/java/com/ecourtier/common/security/WebSocketAuthInterceptor.java` & `WebSocketSessionRegistry.java` | `M` / `??` | Démontrée (Phase 2 + Phase 3 D) | Contrôle `CONNECT`/`SUBSCRIBE`/`SEND`, canal sortant et sweep périodique 5s | Confirmé ; fuite sur connexions brutes sans `CONNECT` et déconnexion globale côté frontend à l'expiration (`CA3-009`). |
| `backend/src/main/java/com/ecourtier/common/scheduler/SchedulerLockService.java` & `SchedulerLockEntity.java` | `??` | Démontrée (Nouveau Phase 3 E) | Verrouillage SQL distribué des tâches `@Scheduled` | Libération immédiate (`lockAtLeastFor = 0`) en `finally` et erreurs SQL `23505` sur contention (`CA3-010`). |
| `frontend/src/pages/AdminVerificationPage.tsx`, `TransactionDetailsModal.tsx`, `EditAnnonceModal.tsx`, `DashboardPage.tsx`, `useConversationSocket.ts`, `frontend/eslint.config.js` | `M` / `??` | Démontrée (Phase 3 F) | UI Admin, suivi PDF, gestion 409, WebSocket STOMP, ESLint v10 | Action admin `PUT /admin/annonces/{id}/statut` absente (`CA3-004`) et bug `useMemo` dans `TransactionDetailsModal` (`CA3-003`). |
| `docs/openapi.json` & `backend/src/main/resources/static/openapi.json` + 19 contrôleurs REST | `M` | Démontrée (Phase 3 G) | Documentation OpenAPI 3.0.1 et synchronisation binaire | Synchronisés (63 chemins / 70 opérations), mais schémas d'erreur `4xx`/`5xx` absents et codes HTTP inexacts (`CA3-012`). |
| `backend/src/test/java/...` (12 fichiers de test Phase 2/3) & `frontend/e2e/phase3-real-backend.spec.ts` | `M` / `??` | Démontrée (Phase 3 H & I) | Tests unitaires, intégration H2, Testcontainers PG16, E2E Playwright | Couverture réelle importante, mais `FlywayFreshMigrationIntegrationTest` figé à V19 (`CA3-011`) et contournements API dans le test E2E. |

---

## 6. AUDIT DÉTAILLÉ A1 À J (ÉTAPE 3)

### 6.1 AUDIT A1 — JWT, `token_version` et Révocation (§ 5)
- **A. Émission des tokens (`JwtService.java`, lignes 51–90)** :
  - Tous les Access Tokens incluent le claim `"tokenVersion"` (ligne 56 : `extraClaims.put("tokenVersion", user.getTokenVersion() != null ? user.getTokenVersion() : 0L)`). *Note : Le rapport de livraison Phase 3 l'appelle à tort `"tv"`.*
  - Les Refresh Tokens (`generateRefreshToken`, lignes 68–74) ne portent pas `"tokenVersion"` mais un `jti` UUID unique persisté sous forme de hash SHA-256 dans `refresh_tokens`.
  - Tout jeton d'accès dépourvu du claim `"tokenVersion"` (`extractTokenVersion(jwt) == null`) est immédiatement rejeté en `401` par `JwtAuthenticationFilter` (ligne 67) et `WebSocketAuthInterceptor` (ligne 119).
- **B. Validation REST (`JwtAuthenticationFilter.java`, lignes 33–102)** :
  - À chaque requête HTTP portant un `Bearer` token, le filtre vérifie la signature/expiration, charge l'utilisateur en base sans cache (`loadUserByUsername` ligne 57 puis `utilisateurRepository.findByEmail` ligne 60), renvoie immédiatement `403 Forbidden` si `!entity.isActif()` (lignes 61–63, 87–99), et compare `tokenVersionClaim.longValue() != currentVersion` (ligne 67).
  - Si la base est indisponible, le bloc `catch (Exception e)` (ligne 82) efface silencieusement le `SecurityContext` sans journaliser l'erreur (fail-closed en `401` sur les routes protégées).
- **C & D. Révocation et failles identifiées** :
  - **Logout authentifié, changement de mot de passe (`changerMotDePasse`, lignes 191–197) et désactivation admin (`changerStatut`, lignes 153–161)** : incrémentent transactionnellement `token_version`, révoquent les Refresh Tokens actifs et ferment les sessions WebSocket dans un callback `TransactionSynchronization.afterCommit()`.
  - **Anomalie P1 `CA3-001` (Replay d'un Refresh Token)** : dans `AuthService.rafraichir` (lignes 196–201), lorsqu'un Refresh Token déjà utilisé (`usedAt != null`) ou révoqué (`revokedAt != null`) est rejoué, seule la famille de Refresh Tokens est révoquée (`refreshTokenRepository.revokeFamily(...)`). `incrementTokenVersion` et `disconnectUser` ne sont **jamais appelés**.
  - **Anomalie P1 `CA3-002` (Déconnexion non authentifiée avec ancien Refresh Token)** : `AuthService.deconnecter` (lignes 242–255) ne vérifie ni si le Refresh Token fourni est expiré, ni s'il a déjà été consommé ou révoqué, et n'exécute le contrôle d'appartenance (ligne 246) que si `authenticatedEmail != null`. Un attaquant disposant d'un ancien Refresh Token périmé/révoqué peut appeler `POST /api/v1/auth/deconnexion` sans Bearer token pour incrémenter `token_version` et couper les sessions actives de la victime à volonté. De plus, la déconnexion d'un seul appareil incrémente le `token_version` global de l'utilisateur et coupe les sessions de tous ses autres appareils.
- **Statut A1 :** **`PARTIELLEMENT CONFIRMÉ`** *(révocation effective sur logout/mot de passe/désactivation, mais incorrecte sur replay de refresh token `CA3-001` et vulnérable au rejeu de déconnexion `CA3-002`)*.

---

### 6.2 AUDIT A2 — Rate Limiting et Limitation des Ressources (§ 6)
- **Algorithme et structure (`BoundedRateLimiterSupport.java`, lignes 17–166)** :
  - Fenêtre fixe (`AttemptWindow` avec `windowStart`, `lastAccess`, `AtomicInteger attempts`), capacité bornée à `MAX_ENTRIES = 10_000` dans `LoginRateLimiterService` (5 tentatives / 15 min par couple `ip:email`) et `RefreshRateLimiterService` (20 tentatives / 5 min par `ip`).
- **Failles et écarts identifiés** :
  - **Absence de `UploadRateLimiterFilter`** : contrairement au tableau récapitulatif du rapport Phase 3 (§ 1 Phase A2), la classe `UploadRateLimiterFilter.java` **n'existe pas** dans le dépôt et aucun rate limiter ne protège les téléversements.
  - **`IaRateLimiterService` non migré (`IaRateLimiterService.java`, lignes 78–88)** : n'utilise pas `BoundedRateLimiterSupport` ; dès que `5 000` clés sont présentes en mémoire sur une fenêtre d'une minute, il lève `429 TOO_MANY_REQUESTS` pour **tous** les nouveaux clients (déni de service global).
  - **Contournement de `LoginRateLimiterService` depuis une seule IP (`CA3-005`)** : la clé étant `ip:email` sans compteur agrégé par IP, un attaquant depuis une seule IP peut saturer les `10 000` entrées avec des emails factices (`ip:fake1@x.com` .. `ip:fake10000@x.com`) à 5 tentatives pour forcer `BoundedRateLimiterSupport.ensureCapacity` (lignes 127–138) à évincer par lots de `1 000` les entrées les plus anciennes — y compris le bucket `ip:victime@ecourtier.sn` déjà bloqué !
  - **Confiance dans `X-Forwarded-For` en production (`CA3-006`)** : `application-prod.yml` (lignes 20–21) active `server.forward-headers-strategy: native` sans restreindre `server.tomcat.remoteip.internal-proxies`.
- **Statut A2 :** **`PARTIELLEMENT CONFIRMÉ`**.

---

### 6.3 AUDIT A3 — Configuration de Production (§ 7)
- **Implémentation (`ProductionConfigurationValidator.java`, lignes 1–183)** :
  - S'exécute via `ApplicationRunner.run` (lignes 65–80) lorsque le profil actif est `prod` ou `production`. Vérifie la présence et la longueur ($\ge 32$ caractères) de `ecourtier.jwt.secret`, le mot de passe PostgreSQL, le format HTTPS sans `*` des origines CORS (`ecourtier.cors.allowed-origins`), et la clé API IA lorsque `ecourtier.ia.enabled=true`.
- **Failles identifiées (`CA3-007`)** :
  1. Le secret JWT de développement par défaut défini dans `application-dev.yml` (ligne 30) et `application-test.yml` (ligne 19) fait 64 caractères et **ne figure pas dans `KNOWN_INSECURE_SECRETS`** (lignes 49–56) : s'il est copié en production, il est accepté sans erreur.
  2. `application.yml` (ligne 5) définit `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}` : si `SPRING_PROFILES_ACTIVE` est oublié en production, l'application démarre en profil `dev` et désactive tous les contrôles de `ProductionConfigurationValidator`.
  3. `validateDatabasePassword()` (lignes 96–99) ne s'exécute que si `spring.datasource.url` commence par `"jdbc:postgresql:"`, alors que `DatabaseConfig.java` (lignes 82–98) accepte les URL cloud `"postgres://"` et `"postgresql://"` (qui contournent donc la vérification du mot de passe DB).
  4. `validateCorsAllowedOrigins()` (lignes 135–141) compare `"::1".equals(host)`, mais `URI.create("https://[::1]:5173").getHost()` renvoie `"[::1]"` avec crochets, ce qui contourne le filtre d'hôte local.
- **Statut A3 :** **`PARTIELLEMENT CONFIRMÉ`**.

---

### 6.4 AUDIT B1 — Versionnement Optimiste des Annonces (§ 8)
- **Vérification de bout en bout** :
  - `AnnonceController.modifierAnnonce` (`AnnonceController.java`, ligne 105) applique `@Valid @RequestBody AnnonceUpdateDTO`.
  - `AnnonceUpdateDTO.version` (lignes 77–79) est annoté `@NotNull`. Toute requête `PUT /api/v1/annonces/{id}` sans `version` est rejetée en `400 Bad Request` (et `AnnonceService.modifierAnnonce` lignes 146–149 double ce contrôle).
  - `AnnonceService.modifierAnnonce` (lignes 151–199) acquiert un verrou pessimiste `findByIdAndSupprimeFalseForUpdate(id)`, compare `Objects.equals(dto.getVersion(), annonce.getVersion())` (levant `409 Conflict` en cas d'écart), applique les modifications et force `annonceRepository.flush()` (ligne 199), garantissant l'incrémentation atomique de `@Version` (`AnnonceEntity.java`, ligne 116) et la traduction de tout `ObjectOptimisticLockingFailureException` en `409` (`GlobalExceptionHandler.java`, ligne 150).
  - Les tests `AnnonceOptimisticLockIntegrationTest` (notamment `testConcurrenceDeuxThreadsMemeVersion` avec `CountDownLatch` aux lignes 227–274) et `Postgres16Phase3IntegrationTest` prouvent la résistance aux courses concurrentes.
- **Limites mineures** :
  - Les mutations partielles d'état (`PUT /admin/annonces/{id}/statut`, `PUT /admin/annonces/{id}/verifier`) ne prennent pas de champ `version` en entrée mais incrémentent bien `AnnonceEntity.version` en base.
  - Côté frontend (`EditAnnonceModal.tsx`, lignes 146–155), un `409` ferme immédiatement la modale (`onClose()`), faisant perdre les saisies non enregistrées de l'utilisateur (`CA3-016`).
- **Statut B1 :** **`CONFIRMÉ`**.

---

### 6.5 AUDIT B2 — Suppression Logique des Annonces (§ 9)
- **Atomicité et protection vis-à-vis des transactions** :
  - La surcharge non sécurisée `supprimerAnnonceLogique(UUID)` a bien été supprimée.
  - `AnnonceService.supprimerAnnonceLogique(UUID id, String actorEmail, boolean isAdmin)` (lignes 258–299) verrouille l'annonce avec `findByIdAndSupprimeFalseForUpdate(id)` (ligne 260), vérifie `statutPublication == DISPONIBLE` (ligne 271), verrouille et vérifie l'absence de toute transaction active (`INITIALISE`, `BLOQUE`) aux lignes 279–293, puis passe `supprime = true`.
  - Parce que `TransactionService.initierTransaction` (ligne 257) verrouille lui aussi l'annonce en premier via `findByIdAndSupprimeFalseForUpdate(annonceId)`, les deux opérations sont strictement sérialisées (prouvé par `AnnonceSuppressionSecurityIntegrationTest.testConcurrenceSuppressionEtInitiationTransaction`, lignes 312–382).
  - Une annonce supprimée logiquement disparaît des recherches publiques, de la liste `mes-annonces`, des statistiques admin et refuse toute nouvelle transaction (`404`), tout nouveau RDV (`404`), tout nouveau favori (`404`), tout nouvel avis (`404`) et tout nouveau signalement (`404`).
- **Oublis inter-modules identifiés (`CA3-008`)** :
  1. **Rendez-vous existants non annulés et toujours actifs** : `supprimerAnnonceLogique` n'annule pas les rendez-vous `DEMANDE` ou `CONFIRME` existants ; pire, `RendezVousService.confirmerRendezVous` (ligne 133), `marquerEffectue` (ligne 227) et le cron de rappels SMS à 09h00 (`executerRappelsDemainInterne`, ligne 285) ne vérifient pas `annonce.isSupprime()`.
  2. **Favoris existants** : `FavoriRepository` (lignes 23–29) ne filtre pas `f.annonce.supprime = false` dans `listerFavoris` ni dans `compterFavoris`.
  3. **Initiation de conversation** : `MessagerieService.initierConversation` (ligne 72) appelle `annonceRepository.findById(annonceId)` au lieu de `findByIdAndSupprimeFalse(annonceId)`, permettant d'ouvrir une nouvelle conversation sur une annonce supprimée logiquement.
- **Statut B2 :** **`PARTIELLEMENT CONFIRMÉ`**.

---

### 6.6 AUDIT C — Génération et Récupération des PDF de Bail (§ 10)
- **A & B. Génération atomique et cohérence transactionnelle** :
  - `PdfBailGeneratorService.generatePdfBail` (lignes 33–146) écrit dans un fichier temporaire unique par invocation (`.` + `filename` + `.` + `UUID.randomUUID()` + `.tmp`, ligne 47), vérifie sa taille $> 0$ (ligne 118), effectue un déplacement atomique `Files.move(..., REPLACE_EXISTING, ATOMIC_MOVE)` avec repli `REPLACE_EXISTING` (lignes 122–126) et supprime le `.tmp` dans le bloc `finally` (ligne 140).
  - *Écart mineur (`CA3-015`)* : contrairement à l'affirmation du rapport Phase 3 (§ 2.4), `PdfBailGeneratorService` ne vérifie **pas** la présence des octets d'en-tête `%PDF-` avant le `Files.move` (cette vérification n'est présente que dans les tests).
  - Lors de la seconde signature (`signerBail`), un échec de génération PDF ne fait pas échouer la signature ni la libération du séquestre : la transaction passe à `StatutDocumentBail.ECHEC` (et non `EN_ERREUR` comme écrit dans le rapport Phase 3) avec incrémentation de `pdfBailTentatives` et sauvegarde de `pdfBailErreur`.
  - Lors d'un appel manuel à `POST /api/v1/transactions/{id}/bail/pdf/generer` (`genererOuRegenererPdfBail`, lignes 1432–1472), l'incrémentation de `pdfBailTentatives` est exécutée dans `executeInTransaction` et **commitée avant** que l'exception HTTP `503 SERVICE_UNAVAILABLE` ne soit levée (ligne 1466).
- **C. Reprise automatique et à la demande** :
  - `BailPdfRecoveryScheduler` (toutes les 60s, `fixedDelayString = "${ecourtier.transactions.pdf-recovery-interval-ms:60000}"`) traite les transactions `LIBERE` + `SIGNE_COMPLET` en statut `A_GENERER` ou `ECHEC` ayant `pdfBailTentatives < 5` (configurable).
  - L'accès concurrent entre le scheduler et l'endpoint manuel est sérialisé par le verrou pessimiste `lockAnnonceThenTransaction(txId)`.
  - Si `statutDocumentBail == GENERE` en base mais que le fichier a disparu du disque, `GET /api/v1/transactions/{id}/bail/pdf` (`TransactionController.java`, lignes 245–256) et `POST /api/v1/transactions/{id}/bail/pdf/generer` (`TransactionService.java`, ligne 1452) détectent l'absence du fichier via `fichierPdfExiste(tx)` et le régénèrent automatiquement à la volée.
- **D. Sécurité d'accès aux PDF** :
  - Aucun gestionnaire de ressources statiques (`WebMvcConfigurer`) n'expose le dossier `uploads/baux/` (seul `/api/v1/uploads/annonces/**` est exposé par `UploadController`).
  - Le téléchargement (`GET /api/v1/transactions/{id}/bail/pdf`) et la régénération (`POST /api/v1/transactions/{id}/bail/pdf/generer`) sont tous deux protégés par `@PreAuthorize("@transactionSecurityService.isStakeholderOrAdmin(#id, authentication)")`, rejetant tout tiers avec `403 Forbidden`.
- **Statut C :** **`CONFIRMÉ`** *(avec observations P3 `CA3-015` et `CA3-017`)*.

---

### 6.7 AUDIT D — WebSocket / STOMP et Expiration des Sessions (§ 11)
- **Contrôles `CONNECT`, `SUBSCRIBE`, `SEND` et canal sortant (`WebSocketAuthInterceptor.java`, lignes 58–184)** :
  - `CONNECT` exige un Access Token valide, un compte actif et `tokenVersionClaim == currentVersion`.
  - `SUBSCRIBE` et `SEND` imposent des expressions régulières strictes (`/topic/conversations/{uuid}` et `/app/conversations/{uuid}/envoyer`), vérifient que l'utilisateur est participant de la conversation (`conversationSecurityService.isParticipant`), et revérifient l'expiration du JWT, `utilisateur.isActif()` et `tokenVersion` en base. En cas d'expiration ou de révocation, la session est immédiatement fermée (`closeSession(..., POLICY_VIOLATION)`).
  - L'intercepteur sortant (`outboundChannelInterceptor`, lignes 79–97) bloque tout message sortant vers une session expirée ou révoquée.
  - Le balayage périodique `sweepExpiredOrRevokedSessions()` (`WebSocketSessionRegistry.java`, lignes 160–196) s'exécute toutes les **5 secondes** (`${ecourtier.websocket.session-sweep-ms:5000}`).
- **Failles et limites identifiées (`CA3-009` & `CA3-014`)** :
  1. **Sessions WebSocket brutes sans trame `CONNECT` jamais purgées** : `WebSocketConfig.afterConnectionEstablished` (ligne 66) enregistre toute connexion WebSocket dans `sessionsById` dès le handshake HTTP (`/ws/**` est `permitAll()`). Si un client n'envoie jamais de trame STOMP `CONNECT`, `metadataBySessionId.get(sessionId)` reste `null` et `sweepExpiredOrRevokedSessions()` l'ignore indéfiniment (`if (metadata == null) continue;` ligne 173).
  2. **Déconnexion globale forcée côté Frontend après expiration naturelle du JWT (`useConversationSocket.ts`, lignes 80–100)** : lorsque l'Access Token expire naturellement (au bout d'1h), le backend ferme la socket ; 3 secondes plus tard (`reconnectDelay: 3000`), `useConversationSocket.ts` tente de se reconnecter avec le **même `accessToken` expiré**, reçoit une trame STOMP `ERROR` contenant `"expiré"`, et appelle `useAuthStore.getState().logout()` (ligne 98) au lieu de rafraîchir le jeton via le `refreshToken` de 7 jours !
  3. **Amplification N+1 requêtes SQL** : `sweepExpiredOrRevokedSessions()` exécute 1 requête `findByEmail` par session toutes les 5 secondes, et `outboundChannelInterceptor` exécute 1 requête `findByEmail` par message sortant.
- **Statut D :** **`PARTIELLEMENT CONFIRMÉ`**.

---

### 6.8 AUDIT E — Horloge `Africa/Dakar` et Schedulers (§ 12)
- **Cohérence temporelle (`Clock`)** :
  - Aucune annotation `@CreationTimestamp` ou `@UpdateTimestamp` ne subsiste dans le code. Un bean `Clock.system(ZoneId.of("Africa/Dakar"))` est déclaré dans `SchedulingConfig.java` (ligne 16) et injecté dans les principaux services.
  - **Oublis identifiés (`CA3-013`)** : `JwtService.java` (lignes 77, 78, 189, 226, 243) utilise toujours `new Date()` (horloge système JVM non injectable pour les tests), `CacheConfig.java` (lignes 39, 48) utilise `System.currentTimeMillis()`, et `AnnonceService.cloturerInitialiseSurSuspension` (ligne 693) ainsi que `UtilisateurManagementService` (lignes 157, 194) appellent `LocalDateTime.now(DAKAR_ZONE)` directement au lieu d'utiliser le bean `Clock`.
- **Verrouillage distribué des Schedulers (`SchedulerLockService.java`, lignes 1–168)** :
  - L'acquisition (`tryAcquire`, lignes 79–119) s'exécute dans une transaction `REQUIRES_NEW` via `JdbcTemplate` (`UPDATE scheduler_locks ... WHERE name = ? AND lock_until <= ?`, suivi d'un `INSERT` en cas de `0` ligne mise à jour).
  - **Faille de déduplication multi-instances (`CA3-010`)** : les 3 schedulers hors Wave (`TransactionExpirationScheduler:31`, `BailPdfRecoveryScheduler:35`, `RendezVousService:260`) appellent `tryExecuteWithLock(lockName, lockAtMostFor, task)` qui fixe **`lockAtLeastFor = Duration.ZERO`** (ligne 58) et appelle `release()` dans le bloc `finally` (ligne 74), ramenant `lock_until = now` dès la fin de `task.run()`. Dans un cluster multi-instances où une tâche rapide (ex. cron quotidien `simulerRappelsDemain` à 09h00) s'exécute en 15 ms sur le nœud A, le verrou est immédiatement relâché à `09:00:00.015`, de sorte que le nœud B dont l'horloge ou le déclencheur cron a 30 ms de décalage ré-acquiert le verrou à `09:00:00.045` et ré-exécute la tâche !
  - **Pollution des journaux PostgreSQL par erreurs `23505`** : lorsqu'un verrou existe déjà et est actif (`lock_until > now`), `UPDATE` renvoie `0` et `tryAcquire` tente systématiquement un `INSERT INTO scheduler_locks`, provoquant une erreur PostgreSQL `ERROR: duplicate key value violates unique constraint "scheduler_locks_pkey"` à chaque tick sur les instances passives.
- **Statut E :** **`PARTIELLEMENT CONFIRMÉ`**.

---

### 6.9 AUDIT F — Frontend et Interface Administrateur (§ 13)
- **Vérification contradictoire des fonctionnalités annoncées** :
  1. **Gestion administrative du statut d'annonce (`CA3-004` — P1)** : **ABSENTE DU FRONTEND.** Contrairement à ce qu'affirme le rapport de livraison Phase 3 (§ 1, § 2.7 et § 3.6 point 4), `AdminVerificationPage.tsx` (lignes 1–492) ne contient aucun sélecteur de statut (`DISPONIBLE`, `SUSPENDUE`, `LOUEE`, `VENDUE`) et aucun fichier de `frontend/src/` n'appelle `PUT /api/v1/admin/annonces/{id}/statut`.
  2. **Suivi et régénération du PDF de bail (`TransactionDetailsModal.tsx`, lignes 594–662)** : les bannières d'état `ECHEC`, `A_GENERER`, `GENERE` et le bouton `"Régénérer le PDF"` (`POST /api/v1/transactions/{id}/bail/pdf/generer`) sont bien présents. Toutefois, **l'anomalie P1 `CA3-003`** dans le `useMemo` de `TransactionDetailsModal.tsx` (lignes 40–53) empêche la modale ouverte sur `DashboardPage` / `PropertyDetailPage` de se rafraîchir après paiement de la caution (`BLOQUE`), après la première signature (`SIGNE_LOCATAIRE` / `SIGNE_PROPRIETAIRE`) ou après un échec de génération PDF (`ECHEC`).
  3. **Gestion des conflits HTTP 409 (`EditAnnonceModal.tsx`, lignes 133, 146–155)** : la version est bien transmise dans le payload `PUT /api/v1/annonces/{id}`, mais en cas de `409`, `onClose()` ferme immédiatement la modale et détruit les modifications saisies par l'utilisateur (`CA3-016`).
  4. **Qualité ESLint v10 & Responsive 375 px** : `npm --prefix frontend run lint` s'exécute avec **0 erreur et 0 warning**, et le correctif de débordement horizontal mobile (`overflow-x: hidden` sur `body` et `max-w-[100vw]` dans `App.tsx`) est en place.
- **Statut F :** **`INCORRECT`** *(sur l'affirmation relative à `AdminVerificationPage.tsx` `CA3-004` et en raison du bug bloquant de mise à jour d'état de `TransactionDetailsModal.tsx` `CA3-003`)*.

---

### 6.10 AUDIT G — Contrat OpenAPI 3.0.1 (§ 14)
- **Parité binaire et couverture des routes** :
  - `docs/openapi.json` et `backend/src/main/resources/static/openapi.json` sont strictement identiques (`SHA-256: a0696fd513527d7cee18e568985b8c3f20c222e288b1e1de75f04068d7fc5594`, 8 200 lignes).
  - Le contrat documente **63 chemins et 70 opérations HTTP** (61 chemins / 68 opérations sur les 18 `@RestController` hors Wave + 2 chemins / 2 opérations sur `WaveWebhookController`), soit 100 % des routes REST du backend (*le rapport Phase 3 indique par erreur 56 chemins*).
- **Anomalies contractuelles identifiées (`CA3-012`)** :
  1. **Absence totale de schéma d'erreur (`ErrorResponse`) sur les réponses `4xx`/`5xx`** : les annotations `@ApiResponse` ne précisant pas `content = @Content(...)`, Springdoc associe le schéma de succès `2xx` (ex. `AnnonceResponseDTO` ou `format: binary` sur `GET /bail/pdf`) à toutes les réponses `400`, `401`, `403`, `404`, `409`, `503`.
  2. **Codes HTTP fantômes documentés mais jamais implémentés** :
     - `POST /api/v1/annonces/{id}/signalements` documente `409` (doublon) et `400` (auto-signalement), alors que `SignalementService.creerSignalement` (lignes 41–59) n'implémente aucun de ces deux contrôles.
     - `POST /api/v1/annonces/{id}/rendez-vous` et `PUT /api/v1/rendez-vous/{id}/{confirmer,annuler,marquer-effectue}` documentent `409`, alors que `RendezVousService` ne lève jamais `409` sur ces méthodes.
  3. **Codes HTTP réels omis** : `403 Forbidden` (compte désactivé / mauvais propriétaire de refresh token) est omis sur `POST /api/v1/auth/connexion`, `/rafraichir` et `/deconnexion`.
  4. **Sécurité OpenAPI imprécise** : `/api/v1/webhooks/wave` n'a pas `security: []`, `/api/v1/auth/deconnexion` est marqué `security: []` alors que le Bearer token est requis si aucun `refreshToken` n'est fourni, et les routes `/api/v1/ia/**` ne documentent pas le paramètre d'en-tête `X-Session-Token`.
- **Statut G :** **`PARTIELLEMENT CONFIRMÉ`**.

---

### 6.11 AUDIT H — Migration V20 et Intégrité PostgreSQL 16 (§ 15)
- **Examen de `V20__phase3_security_pdf_and_scheduler_locks.sql` (lignes 1–41)** :
  - Ajoute `utilisateurs.token_version BIGINT NOT NULL DEFAULT 0`, `transactions.{statut_document_bail, pdf_bail_erreur, pdf_bail_tentatives, pdf_bail_derniere_tentative}`, la contrainte `chk_transactions_statut_document_bail CHECK (statut_document_bail IN ('NON_GENERE', 'A_GENERER', 'GENERE', 'ECHEC'))` et la table `scheduler_locks (name VARCHAR(64) PRIMARY KEY, lock_until TIMESTAMP, locked_at TIMESTAMP, locked_by VARCHAR(255))`.
  - Compatible avec PostgreSQL 16 (`16.15`) et validé par Hibernate `ddl-auto=validate` dans `Postgres16Phase3IntegrationTest`.
- **Anomalies identifiées (`CA3-011`)** :
  1. **`FlywayFreshMigrationIntegrationTest.java` (lignes 61 et 65) est cassé si activé** : il affirme encore `assertEquals("19", current.getVersion().getVersion())` et `assertEquals(19, applied.length)` ! Il n'a pas fait échouer `mvn test` uniquement parce que `@EnabledIf("isDatabaseReachable")` l'a ignoré (`Skipped: 1`).
  2. **Rétro-alimentation (`UPDATE`) imprécise dans `V20` (lignes 26–29)** : `UPDATE transactions SET statut_document_bail = 'A_GENERER' WHERE statut_sequestre = 'LIBERE' AND (pdf_bail_path IS NULL OR TRIM(pdf_bail_path) = '')` ne filtre pas `AND statut_bail = 'SIGNE_COMPLET'`.
  3. **Absence de `DROP CONSTRAINT IF EXISTS`** avant `ADD CONSTRAINT chk_transactions_statut_document_bail` (lignes 31–33).
- **Statut H :** **`PARTIELLEMENT CONFIRMÉ`**.

---

### 6.12 AUDIT I — Qualité et Validité des Tests (§ 16)
- **Vérification des suites de tests** :
  - **Backend H2 (`mvn -f backend/pom.xml -o test`)** : 378 tests au total, dont 373 exécutés (0 échec, 0 erreur) et **5 ignorés (`Skipped: 5`)** (`Postgres16Phase3IntegrationTest` [4 tests] conditionné par `-Decourtier.test.pg16=true`, et `FlywayFreshMigrationIntegrationTest` [1 test] conditionné par la joignabilité de `ecourtier-db:5432` et dont l'assertion sur `V19` est obsolète).
  - **PostgreSQL 16 via Testcontainers (`Postgres16Phase3IntegrationTest`)** : 4 tests réels exécutés contre `postgres:16-alpine` (PostgreSQL `16.15`), appliquant réellement les 20 migrations Flyway (`V1`–`V20`), vérifiant `uq_transactions_annonce_active`, `@Version`, `SchedulerLockService`, `token_version` et la reprise PDF.
  - **Faiblesses méthodologiques identifiées dans les tests** :
    - `IAServiceClockTest.java` (lignes 95–107) simule `messageIARepository.countUserMessagesSince` avec un `AtomicInteger` Mockito en mémoire sans tester la requête JPQL réelle sur base de données.
    - `WebSocketSessionSecurityIntegrationTest.java` (ligne 368) invoque manuellement `webSocketSessionRegistry.sweepExpiredOrRevokedSessions()` au lieu de vérifier le déclenchement automatique du `@Scheduled`.
    - Dans `frontend/e2e/phase3-real-backend.spec.ts`, le test `C` (lignes 459–470) exécute l'initiation de transaction et le paiement de caution par requêtes HTTP directes (`request.post`) avant d'ouvrir `TransactionDetailsModal` dans le navigateur, ce qui a masqué le bug P1 `CA3-003` ! De plus, aucun test de `phase3-real-backend.spec.ts` ne teste la régénération PDF sur échec (`ECHEC`) ni le changement de statut admin d'une annonce (`CA3-004`), contrairement à ce que prétend le rapport de livraison Phase 3 (§ 3.6 points 3 et 4).
- **Statut I :** **`PARTIELLEMENT CONFIRMÉ`**.

---

### 6.13 AUDIT J — Régressions et Interactions entre Modules (§ 17)
L'analyse transversale met en évidence 5 interactions défectueuses entre modules :
1. **Interaction Rotation Refresh Token $\leftrightarrow$ Révocation Access Token / WebSocket (`CA3-001`)** : la détection d'un replay de Refresh Token invalide la famille de Refresh Tokens mais laisse les Access Tokens et les WebSockets actifs.
2. **Interaction Déconnexion d'un appareil $\leftrightarrow$ Multi-sessions et Rejeu (`CA3-002`)** : `deconnecter` coupe tous les appareils de l'utilisateur via `token_version` et accepte des Refresh Tokens déjà révoqués ou expirés sans authentification Bearer.
3. **Interaction Suppression logique d'annonce $\leftrightarrow$ RDV / Favoris / Messagerie (`CA3-008`)** : une annonce `supprime = true` laisse ses RDV actifs (et éligibles aux rappels SMS), reste comptabilisée et listée dans les favoris, et permet d'initier de nouvelles conversations.
4. **Interaction Expiration naturelle du JWT (1h) $\leftrightarrow$ Client WebSocket STOMP (`CA3-009`)** : l'expiration normale du JWT d'accès provoque un rejet STOMP lors de la reconnexion automatique 3s plus tard, ce qui déclenche `useAuthStore.getState().logout()` et détruit la session complète (malgré un Refresh Token valide 7 jours).
5. **Interaction État Parent `DashboardPage` $\leftrightarrow$ `TransactionDetailsModal` (`CA3-003`)** : l'absence de synchronisation de `selectedTransaction` dans `DashboardPage` combinée au filtre `useMemo` de `TransactionDetailsModal` bloque la progression visuelle du workflow transactionnel dans la modale.

---

## 7. MATRICE DE VALIDATION DE LA PHASE 3 (§ 19)

| Domaine | Affirmation du rapport | Statut | Preuve | Anomalies |
| :--- | :--- | :---: | :--- | :--- |
| **JWT / `token_version`** | Révocation REST immédiate sur logout, mot de passe, désactivation et replay refresh | **PARTIELLEMENT CONFIRMÉ** | `JwtService.java:56,196`, `JwtAuthenticationFilter.java:60-78`, `AuthService.java:196-201,240-287`, `UtilisateurManagementService.java:153-198` | `CA3-001` (P1), `CA3-002` (P1), `CA3-014` (P3), `CA3-017` (P3) |
| **Refresh tokens** | Rotation stricte et révocation complète de session sur replay | **PARTIELLEMENT CONFIRMÉ** | `AuthService.java:180-233` verrouille par `findByJtiForUpdate` et révoque la famille, mais n'incrémente pas `token_version` sur replay | `CA3-001` (P1), `CA3-002` (P1) |
| **Rate limiting** | Limites bornées en mémoire sur login, refresh, upload et IA | **PARTIELLEMENT CONFIRMÉ** | `BoundedRateLimiterSupport.java:104-165`, `LoginRateLimiterService.java`, `RefreshRateLimiterService.java`, `IaRateLimiterService.java:78-88` | `CA3-005` (P2), `CA3-006` (P2), `CA3-017` (P3) |
| **Production config** | Validation stricte JWT, DB, CORS HTTPS et clé IA au démarrage `prod` | **PARTIELLEMENT CONFIRMÉ** | `ProductionConfigurationValidator.java:49-182`, `application.yml:5`, `application-dev.yml:30` | `CA3-007` (P2) |
| **Version annonce** | `@Version` obligatoire (`400` si omis) et `409 Conflict` sous concurrence | **CONFIRMÉ** | `AnnonceUpdateDTO.java:77-79`, `AnnonceService.java:144-199`, `AnnonceOptimisticLockIntegrationTest.java:173-274` | `CA3-016` (P3 — UX fermeture modale sur 409) |
| **Suppression logique** | Unification et blocage atomique si transaction/séquestre actif | **PARTIELLEMENT CONFIRMÉ** | `AnnonceService.java:258-299`, `AnnonceSuppressionSecurityIntegrationTest.java:312-382` ; oublis sur RDV, Favoris et Messagerie | `CA3-008` (P2) |
| **PDF** | Génération atomique via `.tmp` et protection d'accès | **CONFIRMÉ** | `PdfBailGeneratorService.java:33-146`, `TransactionController.java:221-295` | `CA3-015` (P3 — pas de check `%PDF-` dans le générateur) |
| **PDF recovery** | Suivi d'état persisté, régénération manuelle et reprise planifiée (< 5 tentatives) | **CONFIRMÉ** | `TransactionService.java:1432-1549`, `BailPdfRecoveryScheduler.java:1-46`, `BailPdfReliabilityIntegrationTest.java` | `CA3-017` (P3 — statut réel `ECHEC` et non `EN_ERREUR`) |
| **WebSocket** | Contrôle `CONNECT`/`SEND`/`SUBSCRIBE`, canal sortant, sweep périodique et fermeture `afterCommit` | **PARTIELLEMENT CONFIRMÉ** | `WebSocketAuthInterceptor.java:58-184`, `WebSocketSessionRegistry.java:128-196`, `useConversationSocket.ts:80-105` | `CA3-009` (P2), `CA3-014` (P3) |
| **Clock Dakar** | Uniformisation `Africa/Dakar` et remplacement des `now()` directs | **PARTIELLEMENT CONFIRMÉ** | `SchedulingConfig.java:16`, entités `@PrePersist`, mais `new Date()` dans `JwtService` et `LocalDateTime.now(DAKAR_ZONE)` résiduels | `CA3-013` (P2) |
| **Schedulers** | Verrouillage distribué multi-instances via `scheduler_locks` | **PARTIELLEMENT CONFIRMÉ** | `SchedulerLockService.java:57-152` relâche immédiatement le verrou en `finally` (`lockAtLeastFor = 0`) et génère des erreurs SQL `23505` | `CA3-010` (P2) |
| **Frontend** | Sélecteur statut admin annonce, régénération PDF, gestion 409, ESLint v10, mobile 375 px | **INCORRECT** | `AdminVerificationPage.tsx:1-492` (sélecteur statut absent), `TransactionDetailsModal.tsx:40-53` (`useMemo` bloquant `BLOQUE`/`SIGNE_LOCATAIRE`/`ECHEC`), ESLint v10 OK (0 warning) | `CA3-003` (P1), `CA3-004` (P1), `CA3-016` (P3) |
| **OpenAPI** | Synchronisation complète et exactitude du contrat OpenAPI 3.0.1 | **PARTIELLEMENT CONFIRMÉ** | Parité binaire `docs/openapi.json` = `static/openapi.json` (63 chemins / 70 opérations), mais schémas d'erreur `4xx`/`5xx` erronés et statuts fantômes/omis | `CA3-012` (P2), `CA3-017` (P3) |
| **Migration V20** | Caractère additif et compatibilité PostgreSQL 16 | **PARTIELLEMENT CONFIRMÉ** | `V20__phase3_security_pdf_and_scheduler_locks.sql:1-41` validé sur PG 16.15, mais `FlywayFreshMigrationIntegrationTest` cassé (attend V19) et `UPDATE` non filtré sur `SIGNE_COMPLET` | `CA3-011` (P2) |
| **Tests** | Fiabilité, couverture réelle et reproductibilité (H2, PG16, Playwright) | **PARTIELLEMENT CONFIRMÉ** | 373/378 tests H2 exécutés, 4/4 PG16 Testcontainers, 28/28 E2E mockés, 5/5 E2E réels, mais contournements API dans le test E2E `C` et mock repository dans `IAServiceClockTest` | `CA3-003` (P1), `CA3-004` (P1), `CA3-011` (P2) |

---

## 8. LISTE DES ANOMALIES IDENTIFIÉES (`CA3-001` À `CA3-017`) (§ 18)

### Anomalies P0 — Critiques
*Aucune anomalie P0 n'a été identifiée sur le périmètre hors Wave.*

---

### Anomalies P1 — Élevées

#### `CA3-001` — La détection de réutilisation d'un Refresh Token (`REVOCATION_REUSE_DETECTED`) ne révoque ni les Access Tokens (`token_version`) ni les sessions WebSocket
- **Domaine :** Authentification / JWT / WebSocket (Audit A1 & J)
- **Gravité :** **P1 — Élevée** *(Anomalie confirmée)*
- **Fichier et emplacement exact :** `backend/src/main/java/com/ecourtier/domain/utilisateur/service/AuthService.java`, méthode `rafraichir(RefreshTokenRequestDTO)`, lignes `196–201`.
- **Comportement observé :** Lorsqu'un Refresh Token déjà utilisé (`stored.getUsedAt() != null`) ou déjà révoqué (`stored.getRevokedAt() != null`) est présenté à `POST /api/v1/auth/rafraichir`, le service appelle uniquement `refreshTokenRepository.revokeFamily(stored.getFamilyId(), now, REVOCATION_REUSE_DETECTED)` puis lève `ResponseStatusException(HttpStatus.UNAUTHORIZED)`. Il n'appelle **pas** `revokeAccessTokensAndDisconnectWs(user.getId(), user.getEmail())` (`utilisateurRepository.incrementTokenVersion` + `webSocketSessionRegistry.disconnectUser`).
- **Preuve :** Lignes 196–201 de `AuthService.java` (contrairement à l'affirmation du rapport de livraison Phase 3 § 1 et § 2.2 point 2).
- **Scénario d'exploitation :** Un attaquant vole le Refresh Token $R_1$ d'un utilisateur et appelle `/api/v1/auth/rafraichir` en premier, obtenant un couple $(A_2, R_2)$ valide. Lorsque le client légitime tente ensuite d'utiliser $R_1$, la réutilisation est détectée et $R_2$ est révoqué, mais l'Access Token $A_2$ de l'attaquant (ainsi que toute session WebSocket STOMP ouverte avec $A_2$) reste pleinement valide pendant toute sa durée de vie (jusqu'à 60 minutes).
- **Conséquence métier ou technique :** Maintien d'accès non autorisé REST et WebSocket pendant jusqu'à 1 heure après détection formelle d'une compromission de session.
- **Recommandation :** Appeler `if (stored.getUtilisateur() != null) { revokeAccessTokensAndDisconnectWs(stored.getUtilisateur().getId(), stored.getUtilisateur().getEmail()); }` à la ligne 198 de `AuthService.java` (l'annotation `@Transactional(noRollbackFor = ResponseStatusException.class)` garantit déjà le commit de l'incrémentation et le déclenchement d'`afterCommit`).
- **Niveau de confiance :** 100 % (Confirmé sur code source).

---

#### `CA3-002` — `POST /api/v1/auth/deconnexion` accepte sans authentification tout Refresh Token déjà révoqué, consommé ou expiré pour incrémenter `token_version` et couper les sessions actives
- **Domaine :** Authentification / Déconnexion (Audit A1 & J)
- **Gravité :** **P1 — Élevée** *(Anomalie confirmée)*
- **Fichier et emplacement exact :** `backend/src/main/java/com/ecourtier/domain/utilisateur/service/AuthService.java`, méthode `deconnecter(String refreshToken, String authenticatedEmail)`, lignes `240–259` ; `AuthController.java`, lignes `97–118` ; `SecurityConfig.java`, ligne `90`.
- **Comportement observé :**
  1. `POST /api/v1/auth/deconnexion` est accessible sans authentification (`permitAll()`).
  2. `AuthService.deconnecter` recherche le Refresh Token uniquement par son hash SHA-256 (`findByTokenHashForUpdate`, ligne 243) sans vérifier `jwtService.extractValidRefreshJti(refreshToken)`, ni `stored.getRevokedAt() == null`, ni `stored.getUsedAt() == null`, ni `stored.getExpiresAt().isAfter(now)`.
  3. Le contrôle d'appartenance (lignes 246–248) n'est exécuté que si `authenticatedEmail != null`.
  4. Même si le jeton fourni est déjà révoqué ou expiré depuis des semaines, la ligne 252 appelle `revokeAccessTokensAndDisconnectWs(...)`, incrémentant `utilisateurs.token_version` et fermant toutes les sessions WebSocket de l'utilisateur.
- **Preuve :** `AuthService.java`, lignes 242–255.
- **Scénario d'exploitation :** Quiconque récupère un ancien Refresh Token expiré ou déjà révoqué dans un log ou un historique navigateur peut envoyer en boucle `POST /api/v1/auth/deconnexion` avec `{"refreshToken": "<ancien_token>"}` (sans en-tête `Authorization`) pour invalider instantanément tous les nouveaux Access Tokens et couper toutes les connexions WebSocket de la victime.
- **Conséquence métier ou technique :** Déni de service ciblé permanent sur le compte d'un utilisateur à partir d'un jeton historique pourtant révoqué ou expiré, et contournement du contrôle d'appartenance.
- **Recommandation :** Valider `jwtService.extractValidRefreshJti(refreshToken) != null`, n'incrémenter `token_version` que si le Refresh Token était effectivement actif (`stored.getRevokedAt() == null && stored.getUsedAt() == null && stored.getExpiresAt().isAfter(now)`), ou exiger un Access Token valide pour révoquer les Access Tokens.
- **Niveau de confiance :** 100 % (Confirmé sur code source).

---

#### `CA3-003` — Filtrage `useMemo` défectueux dans `TransactionDetailsModal.tsx` et absence de mise à jour de `selectedTransaction` dans `DashboardPage.tsx` bloquant l'interface après paiement ou signature
- **Domaine :** Frontend / Workflow Transaction & Bail PDF (Audit F & J)
- **Gravité :** **P1 — Élevée** *(Anomalie confirmée)*
- **Fichier et emplacement exact :** `frontend/src/components/transaction/TransactionDetailsModal.tsx`, lignes `38–53` ; `frontend/src/pages/DashboardPage.tsx`, lignes `68` et `672–679` ; `frontend/src/pages/PropertyDetailPage.tsx`, lignes `121` et `1106–1117`.
- **Comportement observé :**
  - `DashboardPage.tsx` et `PropertyDetailPage.tsx` passent à `TransactionDetailsModal` un objet `selectedTransaction` stocké dans un `useState` statique qui n'est jamais mis à jour lorsque `onRefresh()` invalide la requête TanStack Query `['my-transactions']`.
  - Dans `TransactionDetailsModal.tsx` (lignes 40–53), `useMemo` ne fusionne `overrideTx` avec `transactionProp` **que** si `overrideTx.statutSequestre` vaut `'REMBOURSE'`, `'ANNULE'` ou `'EXPIRE'`, ou si `overrideTx.statutDocumentBail === 'GENERE'`.
  - Par conséquent, lorsque l'utilisateur :
    1. clique sur **« Payer la caution »** (`INITIALISE -> BLOQUE`),
    2. clique en premier sur **« Signer le bail numérique »** (`NON_SIGNE -> SIGNE_LOCATAIRE` ou `SIGNE_PROPRIETAIRE`),
    3. ou signe en second alors que la génération PDF échoue (`SIGNE_COMPLET` + `statutDocumentBail: 'ECHEC'`),
    la condition du `useMemo` vaut `false` et la modale ouverte continue d'afficher l'ancien état (`transactionProp`) !
- **Preuve :** `TransactionDetailsModal.tsx` lignes 40–53 et `DashboardPage.tsx` lignes 672–679.
- **Scénario de reproduction :** Ouvrir un dossier `INITIALISE` depuis `/dashboard`, cliquer sur « Payer la caution » : le toast de succès s'affiche, mais la modale reste sur « En attente de dépôt » avec le bouton de paiement au lieu d'afficher le bouton « Signer le bail numérique », jusqu'à ce que l'utilisateur ferme puis rouvre manuellement la modale.
- **Conséquence métier ou technique :** Rupture du parcours utilisateur principal (paiement du séquestre, signature électronique et affichage de l'alerte d'échec PDF) à l'intérieur de la modale de transaction.
- **Recommandation :** Dans `TransactionDetailsModal.tsx` (lignes 40–53), retourner `{ ...transactionProp, ...overrideTx }` dès lors que `overrideTx && overrideTx.id === transactionProp.id`, et dans `DashboardPage.tsx` / `PropertyDetailPage.tsx`, dériver la transaction sélectionnée depuis les données fraîches de `useQuery` par son `selectedTransactionId`.
- **Niveau de confiance :** 100 % (Confirmé sur code source).

---

#### `CA3-004` — Fonctionnalité annoncée absente : aucun sélecteur ni appel à `PUT /api/v1/admin/annonces/{id}/statut` n'existe dans `AdminVerificationPage.tsx` ni dans le frontend
- **Domaine :** Frontend Administration / Annonces (Audit F & I)
- **Gravité :** **P1 — Élevée** *(Anomalie confirmée — Fonctionnalité annoncée non implémentée)*
- **Fichier et emplacement exact :** `frontend/src/pages/AdminVerificationPage.tsx` (lignes `1–492`), `frontend/src/services/api.ts`, `frontend/src/services/admin.service.ts`, `frontend/e2e/phase3-real-backend.spec.ts` (lignes `1–531`), et `RAPPORT_PHASE_3_SECURISATION_HORS_WAVE_ECOURTIER.md` (§ 1, § 2.7 lignes 87–88, § 3.6 ligne 143).
- **Comportement observé :** Le rapport de livraison Phase 3 affirme avoir ajouté dans `AdminVerificationPage.tsx` un sélecteur dédié (`DISPONIBLE`, `SUSPENDUE`, `LOUEE`, `VENDUE`) connecté à `PUT /api/v1/admin/annonces/{id}/statut` ainsi qu'un test E2E réel vérifiant la transition `DISPONIBLE -> SUSPENDUE -> DISPONIBLE`. Or, l'inspection intégrale du code frontend et des tests E2E démontre qu'**aucun contrôle UI, aucune fonction de service et aucun test E2E n'appellent `/api/v1/admin/annonces/{id}/statut` côté frontend**.
- **Preuve :** `AdminVerificationPage.tsx` (lignes 124–175 : seule `verifyMutation` vers `/api/v1/admin/annonces/${id}/verifier` existe) et `phase3-real-backend.spec.ts` (lignes 1–531 : aucun test ne visite `AdminVerificationPage` pour changer le statut de publication).
- **Conséquence métier ou technique :** Lorsqu'un administrateur suspend une annonce via le traitement d'un signalement (`AdminSignalementsPage.tsx`), ni le propriétaire (bloqué par le backend sur l'état `SUSPENDUE`) ni l'administrateur (dépourvu d'interface dédiée) ne peuvent lever la suspension depuis l'interface web.
- **Recommandation :** Implémenter réellement le sélecteur de statut de publication administrateur connecté à `PUT /api/v1/admin/annonces/{id}/statut` et son test E2E dans `phase3-real-backend.spec.ts`.
- **Niveau de confiance :** 100 % (Confirmé sur code source).

---

### Anomalies P2 — Moyennes

#### `CA3-005` — Contournement de `LoginRateLimiterService` depuis une seule IP par éviction (`BoundedRateLimiterSupport`), DoS global dans `IaRateLimiterService`, et absence de `UploadRateLimiterFilter`
- **Domaine :** Sécurité / Rate Limiting (Audit A2)
- **Gravité :** **P2 — Moyenne** *(Anomalie confirmée)*
- **Fichier et emplacement exact :** `backend/src/main/java/com/ecourtier/common/security/BoundedRateLimiterSupport.java` (lignes `104–140`), `LoginRateLimiterService.java` (lignes `80–83`), `IaRateLimiterService.java` (lignes `78–88`).
- **Comportement observé :**
  1. Dans `LoginRateLimiterService`, la clé de bucket est `ip:email` sans limite globale par IP. Une seule adresse IP peut envoyer 5 tentatives pour `10 000` emails factices afin de saturer `MAX_ENTRIES = 10_000` et forcer `BoundedRateLimiterSupport.ensureCapacity` (lignes 127–138) à évincer les 1 000 entrées les plus anciennes — y compris le compteur `ip:victime@ecourtier.sn` déjà bloqué à 5 tentatives.
  2. `IaRateLimiterService` n'utilise pas `BoundedRateLimiterSupport` : dès que `MAX_TRACKED_KEYS = 5_000` est atteint, il rejette **tous** les nouveaux utilisateurs avec `429 TOO_MANY_REQUESTS` (lignes 81–87).
  3. La classe `UploadRateLimiterFilter.java` mentionnée dans le rapport de livraison Phase 3 (§ 1 Phase A2) n'existe pas.
- **Recommandation :** Ajouter un compteur par IP seule dans `LoginRateLimiterService`, refuser l'éviction des entrées `attempts >= maxAttempts` avant expiration de leur fenêtre, migrer `IaRateLimiterService` vers `BoundedRateLimiterSupport`, et ajouter un rate limiter sur les uploads.
- **Niveau de confiance :** 100 % (Confirmé sur code source).

---

#### `CA3-006` — `server.forward-headers-strategy: native` activé dans `application-prod.yml` sans restriction de proxies de confiance
- **Domaine :** Sécurité Réseau / Rate Limiting (Audit A2 & A3)
- **Gravité :** **P2 — Moyenne** *(Risque plausible confirmé par configuration)*
- **Fichier et emplacement exact :** `backend/src/main/resources/application-prod.yml`, lignes `20–21`.
- **Comportement observé :** `server.forward-headers-strategy: native` active la `RemoteIpValve` de Tomcat avec sa liste par défaut de réseaux privés (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `127.0.0.0/8`). Si l'application est déployée dans un conteneur dont le pair TCP entrant appartient à une plage privée sans écraser `X-Forwarded-For`, un attaquant peut forger `X-Forwarded-For` pour contourner tous les rate limiters basés sur `request.getRemoteAddr()`.
- **Recommandation :** Configurer explicitement `server.tomcat.remoteip.internal-proxies` ou rendre `forward-headers-strategy` configurable par variable d'environnement selon la topologie réelle du reverse proxy.
- **Niveau de confiance :** 95 % (Confirmé dans `application-prod.yml`).

---

#### `CA3-007` — Lacunes de `ProductionConfigurationValidator` (secret dev `application-dev.yml` non banni, profil par défaut `dev`, URL `postgres://` et origine CORS `https://[::1]`)
- **Domaine :** Configuration de Production (Audit A3)
- **Gravité :** **P2 — Moyenne** *(Anomalie confirmée)*
- **Fichier et emplacement exact :** `backend/src/main/java/com/ecourtier/common/config/ProductionConfigurationValidator.java` (lignes `49–56`, `95–107`, `135–141`), `application.yml` (ligne `5`), `application-dev.yml` (ligne `30`).
- **Comportement observé :**
  1. Le secret JWT par défaut de 64 caractères défini dans `application-dev.yml` (ligne 30) et `application-test.yml` (ligne 19) est absent de `KNOWN_INSECURE_SECRETS` (lignes 49–56) et passe donc la validation `prod`.
  2. `application.yml` (ligne 5) définit `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}` : l'oubli de la variable d'environnement `SPRING_PROFILES_ACTIVE` démarre le serveur en mode `dev` sans aucune validation de production.
  3. `validateDatabasePassword` (ligne 97) teste uniquement `datasourceUrl.startsWith("jdbc:postgresql:")` et est contourné si une URL cloud `postgres://...` (supportée par `DatabaseConfig.java:82`) est fournie.
  4. `validateCorsAllowedOrigins` (ligne 139) compare `"::1".equals(host)` alors que `URI.getHost()` retourne `"[::1]"`.
- **Recommandation :** Ajouter le secret de `application-dev.yml:30` à `KNOWN_INSECURE_SECRETS`, vérifier les URL `postgres://` / `postgresql://`, normaliser les crochets IPv6 et `InetAddress.isLoopbackAddress()`, et imposer une longueur minimale au mot de passe DB.
- **Niveau de confiance :** 100 % (Confirmé sur code source).

---

#### `CA3-008` — Effets de bord de la suppression logique d'annonce (`supprime = true`) sur les Rendez-vous, les Favoris et la Messagerie
- **Domaine :** Annonces / Intégrité inter-modules (Audit B2 & J)
- **Gravité :** **P2 — Moyenne** *(Anomalie confirmée)*
- **Fichier et emplacement exact :** `AnnonceService.java` (lignes `258–299`), `RendezVousService.java` (lignes `133`, `227`, `285`), `FavoriRepository.java` (lignes `23–29`), `MessagerieService.java` (ligne `72`), `AvisService.java` (ligne `83`).
- **Comportement observé :**
  1. Lorsqu'une annonce `DISPONIBLE` est supprimée logiquement (`supprime = true`), ses rendez-vous futurs en statut `DEMANDE` ou `CONFIRME` ne sont pas annulés ; ils peuvent encore être confirmés (`confirmerRendezVous`), marqués `EFFECTUE` (`marquerEffectue`) et déclencher des SMS de rappel lors du cron de 09h00 (`executerRappelsDemainInterne`).
  2. `FavoriRepository.findByUtilisateurIdOrderByDateAjoutDesc` et `countByUtilisateurId` ne filtrent pas `WHERE f.annonce.supprime = false` : les annonces supprimées restent affichées et comptées dans les favoris.
  3. `MessagerieService.initierConversation` (ligne 72) utilise `annonceRepository.findById(annonceId)` au lieu de `findByIdAndSupprimeFalse(annonceId)`, autorisant l'ouverture d'une nouvelle conversation sur une annonce supprimée.
- **Recommandation :** Annuler les RDV futurs lors de `supprimerAnnonceLogique`, filtrer `annonce.supprime = false` dans `RendezVousService`, `FavoriRepository` et `AvisService`, et utiliser `findByIdAndSupprimeFalse` dans `MessagerieService.initierConversation`.
- **Niveau de confiance :** 100 % (Confirmé sur code source).

---

#### `CA3-009` — Déconnexion globale forcée (`logout()`) à l'expiration naturelle du JWT sur WebSocket STOMP et absence de purge des connexions WebSocket sans trame `CONNECT`
- **Domaine :** WebSocket STOMP / Frontend & Backend (Audit D & J)
- **Gravité :** **P2 — Moyenne** *(Anomalie confirmée)*
- **Fichier et emplacement exact :** `frontend/src/hooks/useConversationSocket.ts` (lignes `45–50`, `80–105`) ; `backend/src/main/java/com/ecourtier/common/config/WebSocketConfig.java` (ligne `66`) ; `WebSocketSessionRegistry.java` (lignes `172–175`).
- **Comportement observé :**
  1. Côté frontend, lorsqu'une session WebSocket est fermée par le serveur suite à l'expiration naturelle de l'Access Token (1h), `useConversationSocket.ts` tente de se reconnecter 3 secondes plus tard (`reconnectDelay: 3000`) avec le même `accessToken` expiré, reçoit une trame STOMP `ERROR` contenant `"expiré"`, et exécute `useAuthStore.getState().logout()` (ligne 98) au lieu de rafraîchir le token via `/api/v1/auth/rafraichir`.
  2. Côté backend, `WebSocketConfig.afterConnectionEstablished` ajoute toute connexion `/ws` dans `sessionsById` avant le frame `CONNECT`, et `sweepExpiredOrRevokedSessions()` ignore indéfiniment les sessions où `metadata == null` (ligne 173).
- **Recommandation :** Rafraîchir le token avant reconnexion STOMP (via `beforeConnect` dans `@stomp/stompjs`), et horodater l'ouverture des sockets brutes dans `WebSocketSessionRegistry` pour fermer celles qui n'ont pas envoyé `CONNECT` sous 10 secondes.
- **Niveau de confiance :** 100 % (Confirmé sur code source).

---

#### `CA3-010` — `SchedulerLockService.tryExecuteWithLock` relâche immédiatement le verrou en `finally` (`lockAtLeastFor = 0`) et génère des erreurs SQL `23505` sur contention
- **Domaine :** Schedulers & Verrouillage distribué (Audit E)
- **Gravité :** **P2 — Moyenne** *(Anomalie confirmée)*
- **Fichier et emplacement exact :** `backend/src/main/java/com/ecourtier/common/scheduler/SchedulerLockService.java`, lignes `57–77`, `94–111`, `121–152` ; `TransactionExpirationScheduler.java:31`, `BailPdfRecoveryScheduler.java:35`, `RendezVousService.java:260`.
- **Comportement observé :**
  1. La méthode à 3 arguments `tryExecuteWithLock(lockName, lockAtMostFor, task)` appelée par les 3 schedulers passe `lockAtLeastFor = Duration.ZERO` et appelle `release()` dans `finally`, ramenant `lock_until = now` dès la fin du traitement. Si un job rapide se termine en 15 ms sur l'instance A, une instance B déclenchée 30 ms plus tard voit `lock_until <= now` et exécute le job une seconde fois.
  2. Dans `tryAcquire` (lignes 94–111), lorsque le verrou est déjà détenu (`lock_until > now`), `UPDATE` retourne `0` et le code exécute systématiquement `INSERT INTO scheduler_locks`, générant une erreur `SQLState 23505` (`duplicate key value violates unique constraint "scheduler_locks_pkey"`) dans les journaux PostgreSQL à chaque tick.
- **Recommandation :** Imposer un `lockAtLeastFor` positif (ex. `Duration.ofSeconds(15)` à `Duration.ofMinutes(1)`) sur les schedulers et utiliser `INSERT INTO scheduler_locks ... ON CONFLICT (name) DO UPDATE SET ... WHERE scheduler_locks.lock_until <= EXCLUDED.locked_at` (ou vérifier l'existence avant `INSERT`).
- **Niveau de confiance :** 100 % (Confirmé sur code source).

---

#### `CA3-011` — `FlywayFreshMigrationIntegrationTest` cassé (attend V19 au lieu de V20) et `UPDATE` trop large dans `V20`
- **Domaine :** Migrations Flyway & Tests PostgreSQL (Audit H & I)
- **Gravité :** **P2 — Moyenne** *(Anomalie confirmée)*
- **Fichier et emplacement exact :** `backend/src/test/java/com/ecourtier/common/config/FlywayFreshMigrationIntegrationTest.java` (lignes `61` et `65`) ; `backend/src/main/resources/db/migration/V20__phase3_security_pdf_and_scheduler_locks.sql` (lignes `26–33`).
- **Comportement observé :**
  1. `FlywayFreshMigrationIntegrationTest.java` vérifie encore `assertEquals("19", current.getVersion().getVersion())` et `assertEquals(19, applied.length)` alors que `V20` a été ajoutée.
  2. Dans `V20` (lignes 26–29), l'`UPDATE` initialisant `statut_document_bail = 'A_GENERER'` filtre uniquement `WHERE statut_sequestre = 'LIBERE' AND (pdf_bail_path IS NULL ...)` sans vérifier `AND statut_bail = 'SIGNE_COMPLET'`.
- **Recommandation :** Mettre à jour `FlywayFreshMigrationIntegrationTest` vers `"20"` / `20`, et ajouter `AND statut_bail = 'SIGNE_COMPLET'` dans toute requête de reprise/backfill.
- **Niveau de confiance :** 100 % (Confirmé sur code source).

---

#### `CA3-012` — Dérives du contrat OpenAPI 3.0.1 : schémas d'erreur `4xx`/`5xx` erronés, codes HTTP `409` fantômes, codes `403` omis et annotations `@SecurityRequirements` imprécises
- **Domaine :** Documentation API / OpenAPI (Audit G)
- **Gravité :** **P2 — Moyenne** *(Anomalie confirmée)*
- **Fichier et emplacement exact :** `docs/openapi.json`, `backend/src/main/resources/static/openapi.json`, `SignalementController.java:45-51`, `RendezVousController.java:45-106`, `AuthController.java:56-103`, `IAController.java:48-99`.
- **Comportement observé :**
  1. Aucun schéma `ErrorResponse` n'est défini : toutes les réponses `4xx`/`5xx` héritent du schéma de succès `2xx` (ou `format: binary`).
  2. `SignalementController` et `RendezVousController` documentent des réponses `409 Conflict` qui n'existent pas dans `SignalementService` ni dans `RendezVousService`.
  3. `AuthController` omet `403 Forbidden` sur `/connexion`, `/rafraichir` et `/deconnexion`.
  4. `POST /api/v1/webhooks/wave` n'a pas `security: []` dans `docs/openapi.json`, et `IAController` ne documente pas l'en-tête `X-Session-Token`.
- **Recommandation :** Définir un `@Schema(implementation = ErrorResponseDTO.class)` sur les `@ApiResponse` d'erreur et aligner les codes HTTP documentés sur les exceptions réellement levées.
- **Niveau de confiance :** 100 % (Confirmé sur code source et `docs/openapi.json`).

---

#### `CA3-013` — Appels temporels résiduels contournant le bean `Clock` (`JwtService`, `CacheConfig`, `AnnonceService`, `UtilisateurManagementService`) et absence de révocation sur `changerRole`
- **Domaine :** Cohérence temporelle & Administration Utilisateurs (Audit A1 & E)
- **Gravité :** **P2 — Moyenne** *(Anomalie confirmée)*
- **Fichier et emplacement exact :** `JwtService.java` (lignes `77, 78, 189, 226, 243`), `CacheConfig.java` (lignes `39, 48`), `AnnonceService.java` (ligne `693`), `UtilisateurManagementService.java` (lignes `101–126, 157, 194`).
- **Comportement observé :**
  1. `JwtService` utilise `new Date()` au lieu du bean `Clock` injecté, créant un décalage dans les tests à horloge fixe entre `JwtService.isAccessTokenValid` et `WebSocketAuthInterceptor` / `WebSocketSessionRegistry`.
  2. `AnnonceService.cloturerInitialiseSurSuspension` (méthode statique, ligne 693) et `UtilisateurManagementService` (lignes 157, 194) appellent `LocalDateTime.now(DAKAR_ZONE)` au lieu du bean `Clock`.
  3. `UtilisateurManagementService.changerRole` (lignes 101–126) ne révoque pas les jetons ni les sessions WebSocket lors d'un changement de rôle.
- **Recommandation :** Injecter `Clock` dans `JwtService` et `UtilisateurManagementService`, passer `now()` en paramètre de `cloturerInitialiseSurSuspension`, et appeler `incrementTokenVersion` + `disconnectUserAfterCommit` dans `changerRole`.
- **Niveau de confiance :** 100 % (Confirmé sur code source).

---

### Anomalies P3 — Faibles / Dette Technique et Documentaire

#### `CA3-014` — Double requête SQL et quadruple parsing JWT par appel HTTP dans `JwtAuthenticationFilter`, et requêtes N+1 dans `WebSocketSessionRegistry`
- **Domaine :** Performance / Sécurité (Audit A1 & D) — **Gravité : P3** *(Confirmé)*
- **Emplacement :** `JwtAuthenticationFilter.java` (lignes `51–65`) et `WebSocketSessionRegistry.java` (lignes `142, 184`).
- **Observation :** Chaque requête REST authentifiée parse 4 fois le JWT et exécute 2 fois `SELECT ... FROM utilisateurs WHERE email = ?`. Le sweep WebSocket exécute 1 requête SQL par session ouverte toutes les 5 secondes.

#### `CA3-015` — Absence de vérification de l'en-tête `%PDF-` dans `PdfBailGeneratorService` et exposition de `pdfBailPath` dans `TransactionResponseDTO`
- **Domaine :** Génération PDF (Audit C) — **Gravité : P3** *(Confirmé)*
- **Emplacement :** `PdfBailGeneratorService.java` (lignes `118–130`), `TransactionController.java` (lignes `241–245`), `TransactionResponseDTO.java` (ligne `56`).
- **Observation :** Contrairement au rapport Phase 3 (§ 2.4), `PdfBailGeneratorService` vérifie uniquement `Files.size(tmpPath) > 0` sans lire les 5 premiers octets `%PDF-`. `TransactionController.telechargerPdfBail` vérifie `file.exists()` mais pas `file.length() > 0`.

#### `CA3-016` — Fermeture destructive de `EditAnnonceModal.tsx` sur HTTP 409, absence de refresh 401 sur `downloadPdfBail`/`fetchProtectedBlob`, et pagination/invalidation incomplètes dans `AdminVerificationPage.tsx`
- **Domaine :** Frontend UX (Audit F & J) — **Gravité : P3** *(Confirmé)*
- **Emplacement :** `EditAnnonceModal.tsx` (lignes `146–155`), `frontend/src/services/api.ts` (lignes `171–238`), `AdminVerificationPage.tsx` (lignes `76, 137`).

#### `CA3-017` — Inexactitudes factuelles dans le rapport de livraison `RAPPORT_PHASE_3_SECURISATION_HORS_WAVE_ECOURTIER.md`
- **Domaine :** Documentation / Traçabilité — **Gravité : P3** *(Confirmé)*
- **Emplacement :** `RAPPORT_PHASE_3_SECURISATION_HORS_WAVE_ECOURTIER.md` (§ 1, § 2.1 à § 2.8, § 3).
- **Observation :** Le rapport de livraison mentionne un claim `"tv"` (réel : `"tokenVersion"`), un état `EN_ERREUR` et une colonne `url_document_bail` (réels : `ECHEC` et `pdf_bail_path`), des colonnes `lock_name`/`locked_until` avec index `idx_scheduler_locks_until` (réels : `name`/`lock_until` sans index secondaire), une classe `UploadRateLimiterFilter.java` (inexistante), un sweep WebSocket à 30s `evictExpiredOrRevokedSessions` (réel : 5s `sweepExpiredOrRevokedSessions`), 56 chemins OpenAPI (réel : 63 chemins / 70 opérations), et l'ajout d'un sélecteur `PUT /api/v1/admin/annonces/{id}/statut` dans `AdminVerificationPage.tsx` (inexistant).

---

## 9. RÉSULTATS DES TESTS RÉELLEMENT EXÉCUTÉS (§ 16)

Conformément aux règles de sécurité (§ 3.3 et § 16), seuls des contrôles et tests **strictement isolés et sans aucun effet sur les bases de données existantes (`5432`/`5435`) ni sur les fichiers suivis** ont été exécutés :

| # | Vérification / Suite exécutée | Commande exacte | Répertoire (`Cwd`) | Environnement | Résultat brut observé |
| :-: | :--- | :--- | :--- | :--- | :--- |
| **1** | **ESLint v10 (Frontend + E2E)** | `npm --prefix frontend run lint` | `/home/mahou-dev/Documents/Ecourtier2026` | Node / ESLint v10.15.0 (`eslint . --max-warnings 0`) | **Code 0 — `0 error, 0 warning`** |
| **2** | **Parité binaire OpenAPI** | `cmp -s docs/openapi.json backend/src/main/resources/static/openapi.json && sha256sum docs/openapi.json backend/src/main/resources/static/openapi.json` | `/home/mahou-dev/Documents/Ecourtier2026` | Linux `cmp` + `sha256sum` | **Code 0 — Identiques (`a0696fd513527d7cee18e568985b8c3f20c222e288b1e1de75f04068d7fc5594`)** |
| **3** | **Suite complète Backend (Unitaires + Intégration H2)** *(exécutée en `task-21577`)* | `mvn -f backend/pom.xml -o test` | `/home/mahou-dev/Documents/Ecourtier2026` | Java 21.0.4, Spring Boot 3.4.3, H2 en mémoire (`MODE=PostgreSQL`), profil `test` | **Code 0 — `Tests run: 378, Failures: 0, Errors: 0, Skipped: 5` (`BUILD SUCCESS`, 02:00 min)** |
| **4** | **Intégration PostgreSQL 16 jetable via Testcontainers** *(exécutée en `task-21532`)* | `mvn -f backend/pom.xml -o test -Dtest=Postgres16Phase3IntegrationTest -Decourtier.test.pg16=true -Dapi.version=1.44` | `/home/mahou-dev/Documents/Ecourtier2026` | Conteneur isolé jetable `postgres:16-alpine` (PostgreSQL `16.15`), Flyway `V1`–`V20` | **Code 0 — `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0` (`BUILD SUCCESS`, 43.487 s)** |

---

## 10. TESTS INSPECTÉS MAIS NON RÉ-EXÉCUTÉS LORS DE CE CONTRE-AUDIT (§ 16)

1. **`FlywayFreshMigrationIntegrationTest.java`** (`backend/src/test/java/com/ecourtier/common/config/FlywayFreshMigrationIntegrationTest.java`) :
   - **Raison de non-exécution :** Ce test se connecte par défaut à `jdbc:postgresql://localhost:5432/ecourtier_db` et exécute `flyway.clean()` suivi de `flyway.migrate()` (lignes 47–59), ce qui est **destructif** pour une base locale et strictement interdit par la section 3.3. De plus, l'inspection statique a prouvé qu'il contient une assertion obsolète (`assertEquals("19", ...)` aux lignes 61 et 65, anomalie `CA3-011`).
2. **Suites E2E Playwright (`frontend/e2e/*.spec.ts`)** :
   - **Statut :** Inspectées intégralement ligne par ligne (et exécutées précédemment lors de la session de développement en `task-21465` [28/28 mockés] et `task-21322` [5/5 backend réel]), mais non relancées pendant ce contre-audit en lecture seule afin de ne démarrer aucun serveur susceptible d'écrire dans le dépôt ou de toucher un conteneur existant. L'inspection du code de `phase3-real-backend.spec.ts` a suffi à démontrer les lacunes de couverture (`CA3-003` et `CA3-004`).

---

## 11. ANALYSE DES RISQUES RÉSIDUELS

1. **Risque de maintien d'accès après vol de Refresh Token (`CA3-001`)** : en cas de vol et d'utilisation concurrente d'un Refresh Token, la détection du replay coupe le renouvellement mais laisse l'Access Token de l'attaquant actif jusqu'à son expiration naturelle (1h).
2. **Risque de déni de service d'authentification (`CA3-002` & `CA3-005`)** : un ancien Refresh Token compromis permet de déconnecter un utilisateur en boucle via `/api/v1/auth/deconnexion`, et une attaque par dictionnaire d'emails depuis une seule IP peut évincer les entrées de `LoginRateLimiterService` ou saturer globalement `IaRateLimiterService` (`429`).
3. **Risque de blocage fonctionnel dans l'interface utilisateur (`CA3-003` & `CA3-004`)** : l'utilisateur qui paie une caution ou signe un bail dans `TransactionDetailsModal` sur `/dashboard` ne voit pas l'interface passer à l'étape suivante sans fermer et rouvrir la modale, et l'administrateur ne dispose d'aucun bouton pour réactiver une annonce `SUSPENDUE`.
4. **Risque d'exécution dupliquée de tâches planifiées en cluster (`CA3-010`)** : la libération immédiate du verrou dans `SchedulerLockService` (`lockAtLeastFor = Duration.ZERO`) expose les tâches planifiées rapides à une double exécution si deux nœuds ont un léger décalage d'horloge ou de déclenchement.
5. **Périmètre Wave non audité et simulé** : l'intégration financière Wave (`WaveClientMock`) demeure une simulation locale hors périmètre de cette Phase 3 et ne peut en aucun cas traiter de vrais paiements en production.

---

## 12. NOTE DE MATURITÉ DÉTAILLÉE (SUR 100) (§ 20)

| Critère d'évaluation | Barème | Note attribuée | Justification fondée sur les preuves |
| :--- | :---: | :---: | :--- |
| **1. Sécurité et authentification** | 25 pts | **18 / 25** | Révocation `token_version` effective sur logout/mot de passe/désactivation et STOMP verrouillé par participant, mais pénalisé par l'absence de révocation des Access Tokens sur replay de Refresh Token (`CA3-001`), le rejeu non authentifié sur `/deconnexion` (`CA3-002`), le contournement d'éviction de `LoginRateLimiterService` (`CA3-005`) et les lacunes de `ProductionConfigurationValidator` (`CA3-007`). |
| **2. Intégrité métier et données** | 20 pts | **17 / 20** | Verrouillage optimiste `@Version` + pessimiste `FOR UPDATE` très solide sur `AnnonceService` et `TransactionService`, migration `V20` validée sur PostgreSQL 16.15 ; pénalisé par les effets de bord de `supprime = true` sur les RDV, favoris et conversations (`CA3-008`) et l'`UPDATE` trop large de `V20` (`CA3-011`). |
| **3. Fiabilité et traitements asynchrones** | 15 pts | **12 / 15** | Écriture atomique `.tmp` + `ATOMIC_MOVE` et reprise sur erreur PDF bien conçues (persistance des tentatives hors rollback), mais pénalisé par `lockAtLeastFor = Duration.ZERO` et les erreurs `23505` dans `SchedulerLockService` (`CA3-010`) ainsi que la déconnexion globale forcée à l'expiration WS (`CA3-009`). |
| **4. Backend et architecture** | 10 pts | **8.5 / 10** | Architecture claire, séparation des responsabilités et gestion propre des transactions (`TransactionSynchronization.afterCommit`, `TransactionTemplate`), pénalisée par le double `SELECT` / quadruple parsing JWT dans `JwtAuthenticationFilter` (`CA3-014`) et les appels `new Date()` résiduels dans `JwtService` (`CA3-013`). |
| **5. Frontend et expérience utilisateur** | 10 pts | **6 / 10** | ESLint v10 sans aucun warning, build propre et responsive mobile 375 px corrigé, mais fortement pénalisé par le bug `useMemo` bloquant la modale `TransactionDetailsModal` (`CA3-003`), l'absence totale du sélecteur de statut admin annoncé dans `AdminVerificationPage.tsx` (`CA3-004`) et la fermeture destructive sur `409` dans `EditAnnonceModal.tsx` (`CA3-016`). |
| **6. Tests et reproductibilité** | 15 pts | **13 / 15** | 373 tests H2 passants, 4 tests d'intégration sur vrai PostgreSQL 16.15 (Testcontainers) et suites Playwright opérationnelles, mais pénalisé par `FlywayFreshMigrationIntegrationTest` cassé (`CA3-011`), le contournement de l'UI par appels API dans le test E2E `C` (qui a masqué `CA3-003`), et le mock repository dans `IAServiceClockTest`. |
| **7. Documentation et traçabilité** | 5 pts | **3.5 / 5** | Parité binaire parfaite entre `docs/openapi.json` et `static/openapi.json` (63 chemins), mais pénalisé par l'absence de schéma d'erreur `4xx`/`5xx`, les codes HTTP fantômes/omis (`CA3-012`) et les inexactitudes factuelles du rapport de livraison Phase 3 (`CA3-017`). |
| **TOTAL MATURITÉ PHASE 3 (HORS WAVE)** | **100 pts** | **78 / 100** | **Socle technique sérieusement renforcé, mais comportant 4 anomalies P1 confirmées à corriger.** |

---

## 13. VERDICT TECHNIQUE (§ 20)

Selon les critères stricts définis à la section 20 :
- `PHASE 3 VALIDÉE` requiert *aucune anomalie P0/P1* ;
- `PHASE 3 NON VALIDÉE` s'applique dès lors qu'existe *au moins une anomalie P0/P1 ou un défaut majeur de preuve sur un composant critique*.

Compte tenu des **4 anomalies P1 confirmées (`CA3-001`, `CA3-002`, `CA3-003`, `CA3-004`)**, le verdict formel est :

> ### **VERDICT : `PHASE 3 NON VALIDÉE`** *(0 anomalie P0, 4 anomalies P1, 9 anomalies P2, 4 anomalies P3)*

---

## 14. APTITUDE À LA SOUTENANCE UNIVERSITAIRE

- **Verdict Soutenance :** **APTE AVEC PRÉCAUTION SUR LE PARCOURS DE DÉMONSTRATION.**
- Le projet compile, passe 373 tests backend H2, 4 tests PostgreSQL 16 Testcontainers, 28 tests E2E mockés, 5 tests E2E backend réel et 0 warning ESLint v10.
- *Point d'attention pendant la démonstration en direct* : à cause de `CA3-003`, lors de la démonstration du paiement de caution ou de la première signature du bail dans `TransactionDetailsModal` sur `/dashboard`, il faut fermer et rouvrir la modale (ou rafraîchir la page) pour voir le bouton suivant apparaître tant que `CA3-003` n'est pas corrigé.

---

## 15. APTITUDE À LA MISE EN PRODUCTION ET AUX PAIEMENTS RÉELS

1. **Aptitude à la mise en production :** **NON APTE.** Les anomalies `CA3-001` (maintien de l'Access Token après replay de Refresh Token), `CA3-002` (déconnexion forcée par rejeu d'un ancien Refresh Token), `CA3-003` (gel d'état dans `TransactionDetailsModal`), `CA3-004` (impossibilité de réactiver une annonce suspendue en UI), `CA3-005`/`CA3-006` (contournements de rate limiting) et `CA3-007` (`ProductionConfigurationValidator`) doivent impérativement être corrigées avant toute exposition publique.
2. **Aptitude à traiter de vrais paiements :** **NON APTE.** Le module Wave est simulé (`WaveClientMock`) et a été exclu du périmètre de la Phase 3.

---

## 16. ÉTAT FINAL GIT ET WORKTREES & CONTRÔLE DE NON-RÉGRESSION (§ 21)

Vérification finale exécutée après rédaction du présent rapport :
- **Commit `HEAD` :** `f2bf8a97af5c9fc43c40d25a0ed9eeacfb25f317` *(strictement identique à l'état initial)*
- **Branche :** `main...origin/main [devant 6]` *(strictement identique à l'état initial)*
- **Fichiers suivis modifiés (`M`) :** `129` fichiers *(strictement identiques à l'état initial, aucune modification ajoutée ni perdue)*
- **Fichiers non suivis (`??`) :** `69` fichiers (`68` fichiers initiaux intacts + uniquement le nouveau rapport autorisé `RAPPORT_CONTRE_AUDIT_PHASE_3_ECOURTIER.md`)
- **Worktrees :** Les 6 worktrees (`Ecourtier2026`, `Ecourtier2026-convergence`, `Ecourtier2026-lot1`, `Ecourtier2026-lot2`, `Ecourtier2026-lot3`, `Ecourtier2026-validation`) sont strictement intacts.

---

## 17. LISTE DES POINTS À CORRIGER ULTÉRIEUREMENT (PAR PRIORITÉ) & RÉSUMÉ DES PREUVES

### Priorité 1 — Corrections P1 (Bloquantes pour la validation de la Phase 3)
1. **`CA3-001` (`AuthService.java:196-201`)** : Appeler `revokeAccessTokensAndDisconnectWs(stored.getUtilisateur().getId(), stored.getUtilisateur().getEmail())` lors de la détection d'un replay de Refresh Token (`REVOCATION_REUSE_DETECTED`).
2. **`CA3-002` (`AuthService.java:240-259`)** : Dans `deconnecter`, vérifier que le Refresh Token est valide, non expiré et non déjà révoqué/consommé avant d'incrémenter `token_version`, et ne pas couper les Access Tokens des autres appareils si une seule famille est révoquée sans demande globale.
3. **`CA3-003` (`TransactionDetailsModal.tsx:40-53`, `DashboardPage.tsx:672-679`, `PropertyDetailPage.tsx:1106-1117`)** : Supprimer le filtrage restrictif du `useMemo` dans `TransactionDetailsModal.tsx` pour appliquer systématiquement `overrideTx` (y compris pour `BLOQUE`, `SIGNE_LOCATAIRE`, `SIGNE_PROPRIETAIRE`, `ECHEC`) et synchroniser `selectedTransaction` avec les données fraîches de `useQuery`.
4. **`CA3-004` (`AdminVerificationPage.tsx` / `admin.service.ts`)** : Ajouter dans l'interface d'administration le contrôle permettant d'appeler `PUT /api/v1/admin/annonces/{id}/statut` (notamment pour réactiver une annonce `SUSPENDUE -> DISPONIBLE`) et ajouter le test E2E correspondant dans `phase3-real-backend.spec.ts`.

### Priorité 2 — Corrections P2 (Sécurité, Cohérence inter-modules, Schedulers & OpenAPI)
5. **`CA3-005` & `CA3-006` (`BoundedRateLimiterSupport.java:127-138`, `LoginRateLimiterService.java:80-83`, `IaRateLimiterService.java:78-88`, `application-prod.yml:20-21`)** : Ajouter un quota par IP seule sur le login, ne jamais évincer les entrées bloquées actives, migrer `IaRateLimiterService` vers `BoundedRateLimiterSupport`, ajouter un rate limiter sur les uploads, et restreindre `internal-proxies`.
6. **`CA3-007` (`ProductionConfigurationValidator.java:49-56,95-143`, `application.yml:5`)** : Bannir le secret de `application-dev.yml:30`, valider les URL `postgres://`, rejeter `[::1]` et les IP privées en CORS `prod`.
7. **`CA3-008` (`AnnonceService.java:258-299`, `RendezVousService.java`, `FavoriRepository.java`, `MessagerieService.java:72`)** : Annuler les RDV futurs lors de `supprimerAnnonceLogique` et filtrer `annonce.supprime = false` dans les RDV, favoris et conversations.
8. **`CA3-009` (`useConversationSocket.ts:80-105`, `WebSocketSessionRegistry.java:172-175`)** : Rafraîchir l'Access Token avant reconnexion WebSocket au lieu d'appeler `logout()`, et purger les sockets brutes sans trame `CONNECT`.
9. **`CA3-010` (`SchedulerLockService.java:57-112`)** : Configurer un `lockAtLeastFor` non nul sur les schedulers et éviter l'`INSERT` systématique sur verrou actif (`23505`).
10. **`CA3-011` (`FlywayFreshMigrationIntegrationTest.java:61,65`, `V20:26-33`)** : Mettre à jour l'assertion de version Flyway de `19` à `20` et filtrer `statut_bail = 'SIGNE_COMPLET'`.
11. **`CA3-012` (`docs/openapi.json` & contrôleurs REST)** : Documenter le schéma `ErrorResponse` sur les réponses `4xx`/`5xx` et corriger les codes HTTP fantômes (`409` RDV/Signalements) et omis (`403` Auth).
12. **`CA3-013` (`JwtService.java`, `UtilisateurManagementService.java`, `AnnonceService.java:693`)** : Injecter et utiliser `Clock` dans `JwtService` et révoquer les jetons lors de `changerRole`.

### Priorité 3 — Corrections P3 (Performance, Finitions PDF & UX)
13. **`CA3-014` à `CA3-017`** : Parser le JWT une seule fois et charger l'utilisateur une seule fois dans `JwtAuthenticationFilter`, vérifier les octets `%PDF-` dans `PdfBailGeneratorService`, préserver la saisie utilisateur sur `409` dans `EditAnnonceModal.tsx`, et utiliser l'intercepteur de refresh 401 dans `downloadPdfBail` / `fetchProtectedBlob`.
