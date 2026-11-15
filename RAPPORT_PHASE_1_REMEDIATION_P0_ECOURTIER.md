# RAPPORT — PHASE 1 : REMÉDIATION DES 3 VULNÉRABILITÉS P0 (E-COURTIER SÉNÉGAL)

- **Date de génération** : 2026-11-13
- **Dépôt** : `/home/mahou-dev/Documents/Ecourtier2026`
- **Branche / Commit de référence** : `main` (`f2bf8a9`, `Merge branch 'integration/phase-9d-convergence' into main`)
- **Périmètre** : Remédiation des 3 anomalies critiques **P0-1**, **P0-2** et **P0-3**, migration Flyway **V18**, alignement frontend et preuves de validation (unitaires, intégration, sécurité, concurrence, PostgreSQL 16).

---

## 1. Synthèse exécutive

| Anomalie | Description initiale | Statut | Preuves principales |
| :--- | :--- | :--- | :--- |
| **P0-1** | Confusion potentielle entre `ACCESS` token et `REFRESH` token en REST et STOMP/WebSocket | **CORRIGÉE ET VÉRIFIÉE** | [`JwtService.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/JwtService.java#L84-L140), [`JwtAuthenticationFilter.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/JwtAuthenticationFilter.java#L49-L75), [`WebSocketAuthInterceptor.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/WebSocketAuthInterceptor.java#L34-L60), `JwtServiceTest` (5/5), `AuthIntegrationTest` (5/5), `MessagerieStompIntegrationTest` (4/4) |
| **P0-2** | Réactivation abusive ou modification non contrôlée de `statutPublication` d'une annonce | **CORRIGÉE ET VÉRIFIÉE** | [`AnnonceStatutStateMachine.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/service/AnnonceStatutStateMachine.java), [`AnnonceEntity.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/entity/AnnonceEntity.java), [`AdminAnnonceController.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/controller/AdminAnnonceController.java), `AnnonceStatutStateMachineTest` (3/3), `AnnonceStatutSecurityIntegrationTest` (5/5) |
| **P0-3** | Transaction laissée à l'état `INITIALISE` bloquant indéfiniment une annonce via `uq_transactions_annonce_active` | **CORRIGÉE ET VÉRIFIÉE** | [`TransactionService.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionService.java), [`TransactionExpirationScheduler.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionExpirationScheduler.java#L17-L29), [`V18__p0_transaction_lifecycle_and_annonce_version.sql`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/resources/db/migration/V18__p0_transaction_lifecycle_and_annonce_version.sql), PostgreSQL 16.15 (`FlywayFreshMigrationIntegrationTest` 1/1 + `V14MigrationReconciliationTest` 6/6 + preuve SQL), `TransactionLifecycleP03IntegrationTest` (4/4), build frontend `0` erreur |

---

## 2. Respect des contraintes de préservation et d'intégrité

- **Aucun commit, push, merge, rebase, reset, stash ou déploiement** effectué (`HEAD` conservé sur `f2bf8a9`).
- **Worktrees parallèles préservés** sans modification : `Ecourtier2026-convergence` (`6603841`), `Ecourtier2026-lot1`, `Ecourtier2026-lot2`, `Ecourtier2026-lot3`, `Ecourtier2026-validation` (`d161762`).
- **Modifications préexistantes conservées intégralement** (lots Chatbot Groq / IA, `V16`, `V17`, `ReferenceController`, `frontend/e2e/chatbot-groq-*.spec.ts`, `.env.example`, `RAPPORT_*.md`).
- **Migrations existantes `V1`–`V17` inchangées** : les évolutions SQL P0 ont été isolées dans `V18__p0_transaction_lifecycle_and_annonce_version.sql`.
- **Aucune exposition de secrets** : `.env` non lu ni modifié, aucune clé inscrite dans le code, les tests ou ce rapport.
- **Bases de développement non modifiées** : la validation PostgreSQL 16 a été menée exclusivement sur un conteneur éphémère isolé (`ecourtier-pg16-p0-verify`), puis ce conteneur a été supprimé et son retrait vérifié via `docker ps -a`.

---

## 3. P0-1 — Séparation stricte Access Token vs Refresh Token

### 3.1 Cause racine

La validation JWT ne garantissait pas uniformément sur tous les points d'entrée REST et WebSocket/STOMP que le claim `type` devait être strictement égal à `"ACCESS"` (par opposition à `"REFRESH"`). Un `REFRESH` token signé avec la même clé pouvait ainsi risquer d'être accepté sur une route protégée ou lors d'un `CONNECT` STOMP.

### 3.2 Architecture et corrections apportées

1. **Typage explicite et méthodes dédiées dans [`JwtService.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/JwtService.java)** :
   - Constantes `TOKEN_TYPE_ACCESS = "ACCESS"` et `TOKEN_TYPE_REFRESH = "REFRESH"` (lignes 84–85), positionnées respectivement par `generateAccessToken` (ligne 42) et `generateRefreshToken` (ligne 50).
   - `parseValidClaims(String token)` (lignes 130–140+) centralise l'extraction sécurisée des claims, vérifie un `subject` non vide et une date `expiration` non dépassée.
   - `isAccessTokenValid(String token)` (lignes 102–106) et `isAccessTokenValid(String token, String expectedEmail)` (lignes 92–100) exigent `TOKEN_TYPE_ACCESS.equals(claims.get("type", String.class))`.
   - `isTokenValid(String, String)` et `isTokenValid(String)` (lignes 108–114) délèguent vers `isAccessTokenValid(...)`.
   - `isRefreshToken(String token)` (lignes 116–120) exige `TOKEN_TYPE_REFRESH.equals(claims.get("type", String.class))`.

2. **Rejet préalable dans [`JwtAuthenticationFilter.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/JwtAuthenticationFilter.java#L50-L73)** :
   - Dès réception du header `Authorization: Bearer ...`, le filtre vérifie `jwtService.isAccessTokenValid(jwt)` **avant** même de charger `UserDetailsService.loadUserByUsername(...)`.
   - Tout refresh token ou jeton mal typé entraîne un `SecurityContextHolder.clearContext()` immédiat ; le contrôle est réaffirmé avec `jwtService.isAccessTokenValid(jwt, userDetails.getUsername())` ainsi que `entity.isActif()` (retour HTTP `403` si compte désactivé).

3. **Rejet sur handshake STOMP dans [`WebSocketAuthInterceptor.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/WebSocketAuthInterceptor.java#L34-L60)** :
   - Lors de `StompCommand.CONNECT`, l'intercepteur exige `jwtService.isAccessTokenValid(token)` et `utilisateur.isActif()`. Un refresh token provoque explicitement `IllegalArgumentException("Token JWT STOMP invalide, expiré ou type non autorisé")`.

### 3.3 Vérifications P0-1

- `JwtServiceTest` : `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`
- `AuthIntegrationTest` : `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`
- `MessagerieStompIntegrationTest` : `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`

---

## 4. P0-2 — Machine à états explicite pour `statutPublication` et verrouillage concurrent

### 4.1 Cause racine

La mise à jour d'annonce (`PUT /api/v1/annonces/{id}`) ne disposait ni d'une machine à états centralisée distinguant strictement les rôles (`PROPRIETAIRE`/`AGENT`, `ADMIN`, workflow transactionnel), ni d'un verrouillage optimiste/pessimiste systématique. Un propriétaire ou agent risquait ainsi de réactiver une annonce `SUSPENDUE` par l'administration ou déjà engagée (`RESERVEE`, `LOUEE`, `VENDUE`).

### 4.2 Règles métier appliquées et architecture

- Les 5 valeurs de `StatutPublication` (`DISPONIBLE`, `RESERVEE`, `LOUEE`, `VENDUE`, `SUSPENDUE`) sont conservées.
- Un propriétaire ou agent est **strictement interdit** (`403 FORBIDDEN`) de modifier `statutPublication` via `PUT /api/v1/annonces/{id}`. Seules les transitions administratives (`DISPONIBLE <-> SUSPENDUE`) et le workflow transactionnel séquestre peuvent muter `statutPublication`.

### 4.3 Corrections apportées

1. **Verrouillage optimiste sur [`AnnonceEntity.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/entity/AnnonceEntity.java)** :
   - Ajout de `@Version @Column(name = "version", nullable = false) private Long version = 0L;`, adossé à `V18` (`version BIGINT NOT NULL DEFAULT 0`).

2. **Machine à états centralisée dans [`AnnonceStatutStateMachine.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/service/AnnonceStatutStateMachine.java)** :
   - Contrôle statique vérifié via `grep -rn "setStatutPublication" backend/src/main/` : les **2 seuls appels** dans toute la base source Java de production se trouvent aux lignes 83 et 120 de `AnnonceStatutStateMachine.java`.
   - `assertModifierAnnonceStatutAutorise` : rejette en `403 FORBIDDEN` tout changement de `statutPublication` par un non-admin (`null` ou valeur identique à l'actuelle tolérés sans mutation).
   - `appliquerTransitionAdmin` : autorise uniquement `DISPONIBLE <-> SUSPENDUE` et lève `409 CONFLICT` si un séquestre `BLOQUE` est actif, si la cible est réservée aux transactions (`RESERVEE`, `LOUEE`, `VENDUE`), ou si l'état source est `RESERVEE`, `LOUEE` ou `VENDUE`.
   - `appliquerTransitionTransaction` : autorise `DISPONIBLE <-> RESERVEE`, `RESERVEE -> LOUEE`, `DISPONIBLE -> LOUEE`, `RESERVEE -> VENDUE`, `DISPONIBLE -> VENDUE`, et interdit toute transition depuis ou vers `SUSPENDUE`.

3. **Contrôleurs, services et effet de bord sur transactions `INITIALISE`** :
   - [`AnnonceController.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/controller/AnnonceController.java) et [`AdminAnnonceController.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/controller/AdminAnnonceController.java) : introduction de `PUT /api/v1/admin/annonces/{id}/statut` avec [`AnnonceStatutUpdateDTO.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/dto/AnnonceStatutUpdateDTO.java).
   - [`AnnonceService.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/service/AnnonceService.java) et [`SignalementService.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/avis/service/SignalementService.java) : verrouillage pessimiste (`findByIdAndSupprimeFalseForUpdate`), refus de suspension si une transaction `BLOQUE` existe, et clôture automatique en `ANNULE` d'une éventuelle transaction `INITIALISE` non payée lors d'une suspension administrative.

### 4.4 Vérifications P0-2

- `AnnonceStatutStateMachineTest` : `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`
- `AnnonceStatutSecurityIntegrationTest` : `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`
- `AnnonceServiceTest` : `Tests run: 23, Failures: 0, Errors: 0, Skipped: 0`
- `AnnonceIntegrationTest` : `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`
- `SignalementServiceTest` : `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`

---

## 5. P0-3 — Cycle de vie complet des transactions `INITIALISE`, annulation et expiration automatique

### 5.1 Cause racine

Lorsqu'un locataire initiait une transaction (`StatutSequestre.INITIALISE`, `dossier_actif = TRUE`) puis abandonnait avant le paiement Wave, aucune annulation explicite ni expiration par TTL n'était prévue. En base PostgreSQL, l'index unique partiel `uq_transactions_annonce_active` (`WHERE statut_sequestre IN ('INITIALISE', 'BLOQUE') AND dossier_actif = true`) empêchait alors définitivement toute autre transaction sur l'annonce.

### 5.2 Modèle, API, service et ordonnancement

1. **Modèle de données et DTO** :
   - [`StatutSequestre.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/entity/StatutSequestre.java) : ajout des deux statuts terminaux `ANNULE` et `EXPIRE`.
   - [`TransactionEntity.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/entity/TransactionEntity.java), [`TransactionResponseDTO.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/dto/TransactionResponseDTO.java), [`TransactionMapper.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/mapper/TransactionMapper.java) : ajout des champs `dateExpiration`, `dateCloture`, `motifCloture` et exposition de `dossierActif`.
   - [`AnnulationTransactionRequestDTO.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/dto/AnnulationTransactionRequestDTO.java) : motif optionnel validé (`@Size(max = 500)`).

2. **Endpoints et sécurité** :
   - [`TransactionController.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/controller/TransactionController.java) : ajout de `POST /api/v1/transactions/{id}/annuler` avec `@PreAuthorize("@transactionSecurityService.isLocataireOrAdmin(#id, authentication)")`.

3. **Règles métier, verrouillage canonique et TTL dans [`TransactionService.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionService.java)** :
   - TTL par défaut configuré à **`PT24H`** (`ecourtier.transactions.initialise-expiration: ${TRANSACTION_INITIALISE_EXPIRATION:PT24H}` dans [`application.yml`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/resources/application.yml#L62-L64)) avec horloge injectée `Clock` (`SchedulingConfig`).
   - **Ordre canonique de verrouillage `Annonce -> Transaction`** (`lockAnnonceThenTransaction`) combiné aux requêtes de [`TransactionRepository.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/repository/TransactionRepository.java) (`findAnnonceIdById`, `findByIdForUpdate`, `findActiveInitialiseByAnnonceIdForUpdate`, `findExpiredActiveInitialiseIds`), évitant les deadlocks entre initiation, paiement, annulation, signature et expiration.
   - **Expiration paresseuse** :
     - Dans `initierTransaction` : si une transaction `INITIALISE` existante sur l'annonce est expirée, elle est automatiquement clôturée en `EXPIRE` (`dossierActif = false`, `dateCloture`, `motifCloture`) avant d'initier la nouvelle.
     - Dans `effectuerPaiementCaution` et `signerBail` : si la transaction `INITIALISE` a dépassé `dateExpiration`, la transition vers `EXPIRE` est persistée et commitée via `TransactionTemplate` avant de lever `409 CONFLICT` (aucun rollback ne perd la clôture, et **0 appel Wave** n'est émis).
   - **Couplage strict avec l'annonce** : `effectuerPaiementCaution` fait passer l'annonce `DISPONIBLE -> RESERVEE`, et la libération après double signature la fait passer vers `LOUEE` ou `VENDUE` via `AnnonceStatutStateMachine`.

4. **Scheduler multi-instance idempotent** :
   - [`TransactionExpirationScheduler.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionExpirationScheduler.java) : `@Scheduled(fixedDelayString = "${ecourtier.transactions.expiration-check-fixed-delay-ms:60000}")` exécute `transactionService.expirerTransactionsExpirees()`, qui traite chaque candidat via `expirerTransactionIndividuelle(UUID, LocalDateTime)` dans une transaction unitaire verrouillant `Annonce -> Transaction`. Deux exécutions concurrentes ne clôturent la transaction qu'une seule fois.

### 5.3 Migration Flyway V18 et vérification PostgreSQL 16 réelle

Fichier créé : [`V18__p0_transaction_lifecycle_and_annonce_version.sql`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/resources/db/migration/V18__p0_transaction_lifecycle_and_annonce_version.sql) :
- `annonces.version BIGINT NOT NULL DEFAULT 0` ;
- `transactions.date_expiration`, `transactions.date_cloture`, `transactions.motif_cloture` (avec rétro-remplissage `date_creation + INTERVAL '24 hours'` pour les lignes `INITIALISE`) ;
- `transactions_statut_sequestre_check` élargie à `'INITIALISE', 'BLOQUE', 'LIBERE', 'REMBOURSE', 'ANNULE', 'EXPIRE'` ;
- index partiel `idx_transactions_initialise_expiration ON transactions (date_expiration) WHERE statut_sequestre = 'INITIALISE' AND dossier_actif = TRUE`.

**Preuves directes obtenues sur PostgreSQL 16.15** (conteneur temporaire `ecourtier-pg16-p0-verify`, base `ecourtier_v11_fresh`) :
1. Exécution de `FlywayFreshMigrationIntegrationTest` (`ddl-auto=validate`) + `V14MigrationReconciliationTest` : **`Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`**, `BUILD SUCCESS`.
2. Table `flyway_schema_history` : 18 migrations (`version` `1` à `18`), toutes avec `success = t`.
3. Inspection `\d annonces` et `\d transactions` : colonnes, contraintes (`transactions_statut_sequestre_check`, `chk_transactions_bloque_dossier_actif`) et index partiels (`uq_transactions_annonce_active`, `idx_transactions_initialise_expiration`) conformes.
4. Preuve SQL transactionnelle (`DO $$ ...`) :
   - `PROOF_ANNONCE_VERSION: 0`
   - `PROOF_1_OK: duplicate INITIALISE blocked by uq_transactions_annonce_active (duplicate key value violates unique constraint "uq_transactions_annonce_active")`
   - `PROOF_2_OK: new INITIALISE allowed after ANNULE`
   - `PROOF_3_OK: new INITIALISE allowed after EXPIRE`
   - `PROOF_TOTAL_ROWS: 3` (données de test nettoyées ensuite et conteneur temporaire supprimé).

### 5.4 Alignement Frontend (React / TypeScript)

- [`frontend/src/types/index.ts`](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/src/types/index.ts) : extension de `StatutSequestre` avec `'ANNULE' | 'EXPIRE'`, ajout de `dossierActif?`, `dateExpiration?`, `dateCloture?`, `motifCloture?` et `AnnulationTransactionRequestDTO`.
- [`frontend/src/pages/PropertyDetailPage.tsx`](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/src/pages/PropertyDetailPage.tsx) : filtrage des dossiers existants sur les seuls statuts actifs (`dossierActif !== false && (INITIALISE || BLOQUE || LIBERE)`), permettant de réinitier une transaction dès qu'un dossier précédent est `ANNULE`, `EXPIRE` ou `REMBOURSE`.
- [`frontend/src/pages/DashboardPage.tsx`](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/src/pages/DashboardPage.tsx) : ajout des badges pour `SUSPENDUE` (`variant="rose"`), `ANNULE` et `EXPIRE`.
- [`frontend/src/pages/AdminTransactionsPage.tsx`](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/src/pages/AdminTransactionsPage.tsx) : ajout des filtres et badges `ANNULE` et `EXPIRE`.
- [`frontend/src/components/transaction/TransactionDetailsModal.tsx`](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/src/components/transaction/TransactionDetailsModal.tsx) : action `Annuler la transaction` sur `INITIALISE` (`handleAnnulerTransaction`), garde `isClosedTerminal` masquant les actions de signature pour les dossiers clos, affichage de `dateExpiration` ainsi que des détails de clôture (`ANNULE`, `EXPIRE`, `REMBOURSE`).

---

## 6. Résultats des campagnes de vérification finales

Toutes les commandes ci-dessous ont été exécutées **après la dernière modification de code et de test**.

### 6.1 Backend — Tests avec PostgreSQL 16.15

Commande exécutée dans `backend/` :
`mvn test -Dtest=FlywayFreshMigrationIntegrationTest,V14MigrationReconciliationTest -DDB_HOST=127.0.0.1 -DDB_PORT=55439 -Dpg16.jdbc.url=jdbc:postgresql://127.0.0.1:55439/ecourtier_v11_fresh -Dpg16.jdbc.user=ecourtier_user -Dpg16.jdbc.password=ecourtier_secure_pass_2026`

- `FlywayFreshMigrationIntegrationTest` : `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`
- `V14MigrationReconciliationTest` : `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`
- Total : **`Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`** — `BUILD SUCCESS`.

### 6.2 Backend — Suite complète (`mvn test` dans `backend/`)

Résultat global : **`Tests run: 290, Failures: 0, Errors: 0, Skipped: 1`** — `BUILD SUCCESS` (rapport JaCoCo généré sur 107 classes).
- L'unique test ignoré lors de ce lancement sans base externe est `FlywayFreshMigrationIntegrationTest` (`@EnabledIf("isDatabaseReachable")`), validé explicitement par la campagne PostgreSQL 16.15 ci-dessus (`1/1` réussi).
- Suites ciblées notables :
  - `TransactionLifecycleP03IntegrationTest` : `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0` (annulation + déblocage annonce, expiration paresseuse `initierTransaction` / `paiement-caution` / `signer-bail`, scheduler parallèle multi-instance, course paiement vs annulation, course paiement vs expiration sur dossier expiré avec `0` appel Wave).
  - `WorkflowTransactionIntegrationTest` : `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`
  - `TransactionServiceTest` : `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`
  - `AnnonceStatutSecurityIntegrationTest` : `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`
  - `AnnonceStatutStateMachineTest` : `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`
  - `AnnonceServiceTest` : `Tests run: 23, Failures: 0, Errors: 0, Skipped: 0`
  - `AnnonceIntegrationTest` : `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`
  - `SignalementServiceTest` : `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`
  - `JwtServiceTest` : `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`
  - `AuthIntegrationTest` : `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`
  - `MessagerieStompIntegrationTest` : `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`
  - `EnumConsistencyIntegrationTest` : `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`

### 6.3 Frontend — TypeScript & Build Production (`frontend/`)

- `npx tsc --noEmit` : code de sortie `0`, aucune erreur.
- `npm run build` : code de sortie `0` (`built in 7.04s`, PWA `generateSW`, `precache 61 entries (1048.64 KiB)`).

---

## 7. Inventaire des fichiers Phase 1 créés ou modifiés

### Backend — Créés
- [`backend/src/main/java/com/ecourtier/domain/annonce/dto/AnnonceStatutUpdateDTO.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/dto/AnnonceStatutUpdateDTO.java)
- [`backend/src/main/java/com/ecourtier/domain/annonce/service/AnnonceStatutStateMachine.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/service/AnnonceStatutStateMachine.java)
- [`backend/src/main/java/com/ecourtier/domain/transaction/dto/AnnulationTransactionRequestDTO.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/dto/AnnulationTransactionRequestDTO.java)
- [`backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionExpirationScheduler.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionExpirationScheduler.java)
- [`backend/src/main/resources/db/migration/V18__p0_transaction_lifecycle_and_annonce_version.sql`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/resources/db/migration/V18__p0_transaction_lifecycle_and_annonce_version.sql)
- [`backend/src/test/java/com/ecourtier/domain/annonce/service/AnnonceStatutStateMachineTest.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/domain/annonce/service/AnnonceStatutStateMachineTest.java)
- [`backend/src/test/java/com/ecourtier/domain/annonce/controller/AnnonceStatutSecurityIntegrationTest.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/domain/annonce/controller/AnnonceStatutSecurityIntegrationTest.java)
- [`backend/src/test/java/com/ecourtier/domain/transaction/controller/TransactionLifecycleP03IntegrationTest.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/domain/transaction/controller/TransactionLifecycleP03IntegrationTest.java)

### Backend — Modifiés pour la Phase 1
- `JwtService.java`, `JwtAuthenticationFilter.java`, `WebSocketAuthInterceptor.java`
- `AnnonceEntity.java`, `AnnonceController.java`, `AdminAnnonceController.java`, `AnnonceService.java`, `SignalementService.java`
- `StatutSequestre.java`, `TransactionEntity.java`, `TransactionResponseDTO.java`, `TransactionMapper.java`, `TransactionRepository.java`, `TransactionService.java`, `TransactionController.java`
- `application.yml`, `application-test.yml`, `FlywayFreshMigrationIntegrationTest.java`
- `JwtServiceTest.java`, `AuthIntegrationTest.java`, `MessagerieStompIntegrationTest.java`, `AnnonceServiceTest.java`, `SignalementServiceTest.java`, `TransactionServiceTest.java`, `WorkflowTransactionIntegrationTest.java`

### Frontend — Modifiés pour la Phase 1
- [`frontend/src/types/index.ts`](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/src/types/index.ts)
- [`frontend/src/pages/PropertyDetailPage.tsx`](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/src/pages/PropertyDetailPage.tsx)
- [`frontend/src/pages/DashboardPage.tsx`](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/src/pages/DashboardPage.tsx)
- [`frontend/src/pages/AdminTransactionsPage.tsx`](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/src/pages/AdminTransactionsPage.tsx)
- [`frontend/src/components/transaction/TransactionDetailsModal.tsx`](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/src/components/transaction/TransactionDetailsModal.tsx)

### Remarque hors périmètre direct
- Un redémarrage préalable indépendant du conteneur applicatif de développement `ecourtier-backend` (état observé `Restarting`) existait indépendamment de ce travail, aucune opération n'ayant été effectuée sur les conteneurs de développement persistants.
