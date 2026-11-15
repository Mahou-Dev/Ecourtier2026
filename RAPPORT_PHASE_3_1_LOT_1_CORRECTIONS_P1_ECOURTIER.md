# RAPPORT D'INGÉNIERIE — PHASE 3.1 / LOT 1
## Correction ciblée, non-régression et preuves pour les 4 anomalies P1 (`CA3-001` à `CA3-004`) — Hors Wave

- **Projet :** E-COURTIER SÉNÉGAL
- **Date d'achèvement :** 2026-11-15
- **Dépôt racine :** `/home/mahou-dev/Documents/Ecourtier2026`
- **Branche / HEAD :** `main...origin/main [devant 6]` (`f2bf8a97af5c9fc43c40d25a0ed9eeacfb25f317`)
- **Référentiel d'audit initial :** [RAPPORT_CONTRE_AUDIT_PHASE_3_ECOURTIER.md](file:///home/mahou-dev/Documents/Ecourtier2026/RAPPORT_CONTRE_AUDIT_PHASE_3_ECOURTIER.md) (`PHASE 3 NON VALIDÉE — 78/100`)
- **Verdict Lot 1 :** **LOT 1 VALIDÉ — PRÊT POUR LOT 2**

---

## A. Résumé exécutif

La mission Phase 3.1 / Lot 1 avait pour périmètre exclusif la résolution des quatre anomalies prioritaires **P1** relevées lors du contre-audit de la Phase 3 (`CA3-001`, `CA3-002`, `CA3-003`, `CA3-004`), dans le respect strict de la préservation du dépôt (worktrees et modifications locales existantes) et de l'**exclusion absolue de Wave**.

Toutes les causes racines identifiées ont été corrigées de façon minimale et ciblée, sans régression ni dérive hors périmètre :

1. **`CA3-001` (Rejeu de Refresh Token -> Access Tokens & WebSockets) :** Le claim `familyId` est désormais embarqué dans les Access Tokens de session et validé à chaque requête REST ainsi qu'à la connexion/activité STOMP. Lors d'un rejeu (`REVOCATION_REUSE_DETECTED`), toute la famille compromise (`RefreshTokenEntity`) et ses sessions WebSocket STOMP sont immédiatement révoquées/fermées après commit, rejetant tous les Access Tokens de cette famille en `401 Unauthorized` tout en **préservant les sessions actives des autres appareils**.
2. **`CA3-002` (Protection anti-DoS `/deconnexion` et `/rafraichir`) :** Toute présentation d'un Refresh Token expiré, déjà révoqué, déjà consommé ou inconnu sur `/api/v1/auth/deconnexion` ou `/api/v1/auth/rafraichir` (jeton expiré/révoqué) renvoie `401 Unauthorized` sans aucun effet de bord (ni révocation de famille, ni incrémentation de `token_version`, ni coupure WebSocket d'une session légitime). Une déconnexion valide ne révoque que la famille concernée et n'incrémente `token_version` que s'il ne subsiste aucune autre session active.
3. **`CA3-003` (Rafraîchissement immédiat de `TransactionDetailsModal`) :** Suppression du filtrage restrictif qui bloquait `overrideTx` sur les statuts intermédiaires (`BLOQUE`, `SIGNE_LOCATAIRE`, `SIGNE_PROPRIETAIRE`, `ECHEC`), synchronisation directe du cache TanStack Query, dérivation dynamique de la transaction sélectionnée dans les pages parentes, réinitialisation d'état à la fermeture/changement de dossier et garde anti-double-clic.
4. **`CA3-004` (Gestion administrative du statut d'annonce et version optimiste) :** Ajout de `changerStatutAnnonce` (`PUT /api/v1/admin/annonces/{id}/statut`), exposition complète dans `AdminVerificationPage.tsx` (vue dédiée, badges, boutons rapides de suspension/réactivation `DISPONIBLE <-> SUSPENDUE`, sélecteur cible, motif, gestion d'erreur `409`), support optionnel de `version` dans `AnnonceStatutUpdateDTO` + contrôle `409 Conflict` dans `AnnonceService`, et synchronisation binaire exacte des spécifications OpenAPI (`docs/openapi.json` et `static/openapi.json`).

**Synthèse des validations exécutées après la dernière modification de code et de test :**
- Suite backend Maven (`mvn test`) : **`384` tests exécutés au bilan Surefire, `0` échec, `0` erreur, `5` ignorés** (`1` dans `FlywayFreshMigrationIntegrationTest` faute d'hôte externe `ecourtier-db:5432` + `4` dans `Postgres16Phase3IntegrationTest` exécutés séparément ci-dessous).
- Suite PostgreSQL 16 Testcontainers (`-Decourtier.test.pg16=true`, PostgreSQL `16.15`, Flyway V1 à V20) : **`4` tests passés, `0` échec, `0` erreur, `0` ignoré**.
- Qualité & build frontend (`eslint . --max-warnings 0 && npx tsc --noEmit && npm run build`) : **succès (`code 0`)**, 2 841 modules transformés.
- Playwright E2E API mockée (`e2e/phase2-security-ui.spec.ts`) : **`4 passed (12.0s)`**.
- Playwright E2E backend réel isolé H2 (`e2e/phase3-real-backend.spec.ts`, profil `test`) : **`6 passed (39.1s)`**.

---

## B. État initial et préservation du dépôt

Avant toute modification, l'état initial du dépôt a été capturé dans l'artefact de travail `phase3_1_initial_git_state.txt` :

- **Racine Git :** `/home/mahou-dev/Documents/Ecourtier2026`
- **Branche :** `## main...origin/main [devant 6]`
- **Commit `HEAD` :** `f2bf8a97af5c9fc43c40d25a0ed9eeacfb25f317` (inchangé tout au long de la mission)
- **Worktrees existants (tous préservés intactement) :**
  1. `/home/mahou-dev/Documents/Ecourtier2026` (`HEAD f2bf8a97af5c9fc43c40d25a0ed9eeacfb25f317`, `refs/heads/main`)
  2. `/home/mahou-dev/Documents/Ecourtier2026-convergence` (`HEAD 66038410eaa02ec19bf7058d2a194f0eb342210e`, `refs/heads/integration/phase-9d-convergence`, prunable)
  3. `/home/mahou-dev/Documents/Ecourtier2026-lot1` (`HEAD d161762406ce5f0c685441d13f6e91bc5602338a`, détaché, prunable)
  4. `/home/mahou-dev/Documents/Ecourtier2026-lot2` (`HEAD d161762406ce5f0c685441d13f6e91bc5602338a`, détaché, prunable)
  5. `/home/mahou-dev/Documents/Ecourtier2026-lot3` (`HEAD d161762406ce5f0c685441d13f6e91bc5602338a`, détaché, prunable)
  6. `/home/mahou-dev/Documents/Ecourtier2026-validation` (`HEAD d161762406ce5f0c685441d13f6e91bc5602338a`, détaché, prunable)

Aucune commande destructive ou altérant l'historique Git (`reset`, `clean`, `checkout`, `switch`, `stash`, `rebase`, `merge`, `commit`, `push`, `pull`) n'a été exécutée. Aucun fichier local préexistant hors périmètre CA3-001..CA3-004 n'a été écrasé.

---

## C. Anomalie `CA3-001` — Révocation des Access Tokens et sessions WebSocket lors d'un rejeu de Refresh Token

### 1. Diagnostic et cause racine
Selon le contre-audit (`RAPPORT_CONTRE_AUDIT_PHASE_3_ECOURTIER.md`, lignes 308–317), lors de la détection d'un Refresh Token déjà consommé (`usedAt != null`) dans `AuthService.rafraichir`, seule la famille de Refresh Tokens était révoquée (`REVOCATION_REUSE_DETECTED`). Les Access Tokens déjà émis pour cette session restaient acceptés jusqu'à expiration sur REST et WebSocket STOMP. Par ailleurs, incrémenter globalement `token_version` aurait coupé sans distinction les autres appareils légitimement connectés du même utilisateur.

### 2. Correction mise en œuvre
Une révocation **ciblée par session (`familyId`)** a été implémentée de bout en bout :
- [JwtService.java](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/JwtService.java) (`generateSessionAccessToken` l.68, `extractFamilyId` l.239) : ajout du claim `familyId` aux Access Tokens générés lors des connexions/inscriptions/rotations, tout en conservant `generateAccessToken(UtilisateurEntity)` pour éviter toute ambiguïté avec les stubs Mockito stricts existants.
- [RefreshTokenRepository.java](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/utilisateur/repository/RefreshTokenRepository.java) (`isFamilyActive` l.61, `existsActiveByUtilisateurId` l.75) : requête vérifiant qu'une famille donnée possède encore au moins un Refresh Token actif (`revokedAt IS NULL`, `usedAt IS NULL`, `expiresAt > :now`).
- [JwtAuthenticationFilter.java](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/JwtAuthenticationFilter.java) (l.77–87) : pour tout JWT portant `familyId`, vérification de `refreshTokenRepository.isFamilyActive(familyId, entity.getId(), now)` à chaque requête HTTP authentifiée, avec rejet immédiat si la famille est révoquée et exposition de l'attribut `jwtFamilyId`.
- [WebSocketSessionRegistry.java](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/WebSocketSessionRegistry.java) (`disconnectFamily` l.140, `isSessionValidAndActive` l.156–185, `sweepExpiredOrRevokedSessions` l.223–234) et [WebSocketAuthInterceptor.java](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/WebSocketAuthInterceptor.java) (l.100, l.123–132, l.188–191) : suivi du `familyId` sur chaque session STOMP enregistrée, fermeture ciblée par famille, et blocage lors des trames `CONNECT`, `SUBSCRIBE`, `SEND` et des messages sortants si la famille n'est plus active.
- [AuthService.java](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/utilisateur/service/AuthService.java) (l.208–209, `disconnectFamilyWsAfterCommit` l.314–324) : sur détection de rejeu (`usedAt != null`), révocation de la famille (`REVOCATION_REUSE_DETECTED`) et déclenchement après commit de `disconnectFamilyWsAfterCommit(stored.getFamilyId())` sans invalider les sessions d'autres appareils (`device B`).

### 3. Preuves de validation (`CA3-001`)
- [AuthAccessTokenRevocationIntegrationTest.java](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/domain/utilisateur/controller/AuthAccessTokenRevocationIntegrationTest.java) (l.200–241, 5 tests passés) : prouve qu'après rejeu de $R_{1,A}$, les Access Tokens $A_1$ et $A_2$ ainsi que $R_{2,A}$ sont immédiatement rejetés en `401` sur `/api/v1/utilisateurs/moi`, `/api/v1/conversations/mes-conversations` et `/api/v1/auth/rafraichir`, tandis que l'appareil indépendant `B` reste authentifié (`200 OK`) et conserve sa capacité de rotation.
- [WebSocketSessionSecurityIntegrationTest.java](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/common/security/WebSocketSessionSecurityIntegrationTest.java) (l.446–503, 4 tests passés) : prouve que le rejeu sur l'appareil `A` ferme immédiatement la session STOMP `wsDevA`, bloque les reconnexions avec $A_1$/$A_2$ et conserve active la session WebSocket `wsDevB`.
- [phase3-real-backend.spec.ts](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/e2e/phase3-real-backend.spec.ts) (test n°6, l.546+) : validé contre le backend H2 réel.

---

## D. Anomalie `CA3-002` — Protection anti-DoS de `/api/v1/auth/deconnexion` et `/api/v1/auth/rafraichir`

### 1. Diagnostic et cause racine
Selon le contre-audit (`RAPPORT_CONTRE_AUDIT_PHASE_3_ECOURTIER.md`, lignes 321–334), `POST /api/v1/auth/deconnexion` (`permitAll()`) acceptait un Refresh Token déjà consommé, révoqué ou expiré en base pour déclencher `revokeAccessTokensAndDisconnectWs(...)` et incrémenter `token_version`, permettant à un tiers possédant un ancien jeton d'effectuer un déni de service par déconnexions répétées de la victime.

### 2. Correction mise en œuvre
- [AuthController.java](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/utilisateur/controller/AuthController.java) (l.109) et [AuthService.java](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/utilisateur/service/AuthService.java) (l.227, l.286–304) :
  - Rejet systématique en `401 Unauthorized` **sans aucun effet de bord** (aucune révocation de famille, aucune incrémentation de `token_version`, aucune fermeture WebSocket) lorsqu'un Refresh Token expiré, déjà révoqué, déjà consommé ou inconnu est soumis sur `/api/v1/auth/deconnexion` (ou lorsqu'un jeton expiré/déjà révoqué est soumis sur `/api/v1/auth/rafraichir`).
  - Lors d'une déconnexion valide (via un Refresh Token actif ou un Access Token de session authentifié), révocation uniquement de la famille concernée et appel de `disconnectFamilyWsAfterCommit`, l'incrémentation globale de `token_version` n'intervenant plus que lorsque `!refreshTokenRepository.existsActiveByUtilisateurId(...)`.

### 3. Preuves de validation (`CA3-002`)
- [AuthServiceTest.java](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/domain/utilisateur/service/AuthServiceTest.java) (l.335–382, 13 tests passés) : `testRafraichirTokenDejaRevoqueOuExpireSansEffetDeBord` et `testDeconnecterRejetteAncienRefreshTokenSansEffetDeBord` vérifient le statut `401` et l'absence d'appel à `revokeFamily` et `incrementTokenVersion`.
- [AuthAccessTokenRevocationIntegrationTest.java](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/domain/utilisateur/controller/AuthAccessTokenRevocationIntegrationTest.java) (l.244–312) : quatre jetons périmés/invalides (consommé, révoqué, expiré en base, forgé) sur `/api/v1/auth/deconnexion` et `/rafraichir` sont tous rejetés en `401`, `tokenVersionAfterAttacks == tokenVersionBeforeAttacks`, et la session active légitime de la victime reste opérationnelle (`200 OK`).
- [WebSocketSessionSecurityIntegrationTest.java](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/common/security/WebSocketSessionSecurityIntegrationTest.java) (l.480–496) : confirme que les appels `/api/v1/auth/deconnexion` avec $R_{1,A}$ consommé ou $R_{2,A}$ révoqué retournent `401` sans couper `wsDevB`.
- [phase3-real-backend.spec.ts](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/e2e/phase3-real-backend.spec.ts) (test n°6, l.546+) : validé contre le backend H2 réel.

---

## E. Anomalie `CA3-003` — Rafraîchissement immédiat de `TransactionDetailsModal` et synchronisation parent

### 1. Diagnostic et cause racine
Selon le contre-audit (`RAPPORT_CONTRE_AUDIT_PHASE_3_ECOURTIER.md`, lignes 338–354), deux défauts combinés figeaient la modale transaction après paiement de la caution ou première signature :
1. `TransactionDetailsModal.tsx` filtrait dans un `useMemo` l'application de `overrideTx` aux seuls statuts finaux (`REMBOURSE`, `ANNULE`, `EXPIRE`) ou `GENERE`, ignorant les transitions intermédiaires (`BLOQUE`, `SIGNE_LOCATAIRE`, `SIGNE_PROPRIETAIRE`, `ECHEC`).
2. Les pages parentes conservaient une référence statique `selectedTransaction` non resynchronisée lorsque TanStack Query revalidait la liste des transactions.

### 2. Correction mise en œuvre (strictement hors Wave)
Aucune intégration ou logique Wave n'a été modifiée ; seuls l'état local et la synchronisation UI à partir des réponses API existantes ont été corrigés :
- [TransactionDetailsModal.tsx](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/src/components/transaction/TransactionDetailsModal.tsx) :
  - Fusion systématique `{ ...transactionProp, ...overrideTx }` lorsque `overrideTx.id === transactionProp.id` (l.59–64), sans restriction de statut.
  - Ajout de `syncTransactionState` (l.66+) : mise à jour immédiate du cache `['my-transactions']` et invalidation de `['my-transactions']`, `['admin-transactions']`, `['annonce', id]`, `['my-annonces']` et `['annonces']`, suivie de `onRefresh()`.
  - Scope de `refundConfirmTxId` sur l'ID de transaction (l.48, l.83), réinitialisation d'état dans `handleModalClose` (l.51, l.266, l.697) lors de la fermeture ou du changement de transaction, et verrouillage anti-double-clic `isAnyActionPending` (l.88) sur l'ensemble des actions de la modale (l.102, 123, 145, 170, 192, 213).
- [DashboardPage.tsx](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/src/pages/DashboardPage.tsx), [PropertyDetailPage.tsx](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/src/pages/PropertyDetailPage.tsx) et [AdminTransactionsPage.tsx](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/src/pages/AdminTransactionsPage.tsx) : dérivation dynamique du dossier sélectionné à partir des résultats live `useQuery` via `.find(...)` et rafraîchissement cohérent via `onRefresh`.

### 3. Preuves de validation (`CA3-003`)
- [phase2-security-ui.spec.ts](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/e2e/phase2-security-ui.spec.ts) (test n°3, l.232+, `CA3-003: TransactionDetailsModal actualise immédiatement son état après paiement caution puis signature sans fermeture de la modale`) : vérifie en UI le passage immédiat de `INITIALISE` (`En attente de dépôt`) vers `BLOQUE` puis l'apparition du bouton de signature et l'actualisation post-signature **sans fermer la modale**.
- [phase3-real-backend.spec.ts](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/e2e/phase3-real-backend.spec.ts) (test n°5, l.431+) : vérifie la progression complète en UI sur le backend réel H2.

---

## F. Anomalie `CA3-004` — Administration du statut d'annonce, contrôle de version et synchronisation OpenAPI

### 1. Diagnostic et cause racine
Selon le contre-audit (`RAPPORT_CONTRE_AUDIT_PHASE_3_ECOURTIER.md`, lignes 358–366), l'interface d'administration annoncée pour piloter `PUT /api/v1/admin/annonces/{id}/statut` (notamment la suspension et la réactivation `SUSPENDUE -> DISPONIBLE`) était absente de `AdminVerificationPage.tsx` et d'`admin.service.ts`. Par ailleurs, le DTO backend `AnnonceStatutUpdateDTO` et la spécification OpenAPI ne portaient pas le contrôle optionnel de `version` optimiste pour cet endpoint.

### 2. Correction mise en œuvre
- **Backend :**
  - [AnnonceStatutUpdateDTO.java](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/dto/AnnonceStatutUpdateDTO.java) (l.15–25) : ajout du champ optionnel `Long version`.
  - [AdminAnnonceController.java](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/controller/AdminAnnonceController.java) (l.85–89) et [AnnonceService.java](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/service/AnnonceService.java) (`changerStatutPublicationAdmin`, l.664) : vérification du champ `version` s'il est fourni (retour `409 Conflict` en cas de version obsolète), application des règles d'[AnnonceStatutStateMachine.java](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/service/AnnonceStatutStateMachine.java) (l.39–80 : autorisant `DISPONIBLE <-> SUSPENDUE` et rejetant en `409` les statuts transactionnels `RESERVEE`, `LOUEE`, `VENDUE`), et persistance immédiate via `saveAndFlush`.
- **OpenAPI :**
  - Synchronisation de [docs/openapi.json](file:///home/mahou-dev/Documents/Ecourtier2026/docs/openapi.json) et [backend/src/main/resources/static/openapi.json](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/resources/static/openapi.json) : empreinte SHA-256 strictement identique (`287e3324fe0461bc85c8b7042576ac305fdd8d9ab37a6b95f4eaffe6113de7a6`), `63` chemins et `70` opérations exposés, dont `PUT /api/v1/admin/annonces/{id}/statut` et `AnnonceStatutUpdateDTO` (`statutPublication`, `motif`, `version`).
- **Frontend :**
  - [admin.service.ts](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/src/services/admin.service.ts) (`changerStatutAnnonce`, l.92) : appel typé vers `PUT /api/v1/admin/annonces/{id}/statut`.
  - [AdminVerificationPage.tsx](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/src/pages/AdminVerificationPage.tsx) : bascule de vue `viewMode` (`'EN_ATTENTE' | 'TOUTES'`, l.46, 95, 100–101, 324, 335), affichage des badges de statut de publication, boutons rapides `Suspendre l'annonce` (l.471) et `Réactiver l'annonce` (l.483), sélecteur `<select>` (`aria-label="Statut de publication cible pour {titre}"`, l.491), champ motif optionnel, alerte `role="alert"` affichant les messages d'erreur explicites (`409 Conflict`), et mise à jour des listes/caches TanStack Query via `updateAnnonceInLists` (l.189, 231).

### 3. Preuves de validation (`CA3-004`)
- [AnnonceOptimisticLockIntegrationTest.java](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/domain/annonce/controller/AnnonceOptimisticLockIntegrationTest.java) (l.288–339, 4 tests passés) : `testAdminStatutPublicationSuspendReactivateAndOptimisticConflict` vérifie `403` (non-admin), `DISPONIBLE -> SUSPENDUE` (`200`, `version: 1`), réactivation avec version obsolète `0` (`409`), `SUSPENDUE -> DISPONIBLE` avec version `1` (`200`, `version: 2`) et transition illégale `DISPONIBLE -> LOUEE` (`409`).
- [AnnonceStatutSecurityIntegrationTest.java](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/domain/annonce/controller/AnnonceStatutSecurityIntegrationTest.java) (l.373+, 5 tests passés).
- [OpenApiContractIntegrationTest.java](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/common/controller/OpenApiContractIntegrationTest.java) (1 test passé).
- [phase2-security-ui.spec.ts](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/e2e/phase2-security-ui.spec.ts) (test n°4, l.338+, `CA3-004: AdminVerificationPage permet la suspension (DISPONIBLE -> SUSPENDUE), la réactivation (SUSPENDUE -> DISPONIBLE) et affiche une erreur claire en cas de transition refusée (409)`).
- [phase3-real-backend.spec.ts](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/e2e/phase3-real-backend.spec.ts) (test n°6, l.546+) : parcours complet sur `/admin/verification` face au backend réel H2.

---

## G. Respect strict de l'exclusion Wave

Conformément aux instructions impératives :
- `git diff --name-only HEAD | grep -iE "wave|paiement"` : **aucun fichier suivi lié à Wave modifié**.
- `git status --short -- "*Wave*" "*wave*" "*Paiement*" "*paiement*"` : seuls figurent les 10 fichiers préexistants déjà non suivis (`??`) dans l'état initial (`WaveWebhookController.java`, `WaveWebhookPayloadDTO.java`, `WaveWebhookResponseDTO.java`, `PaiementWaveEntity.java`, `StatutPaiementWave.java`, `TypeOperationWave.java`, `PaiementWaveRepository.java`, `WavePaymentReconciliationService.java`, `WaveWebhookService.java`, `WavePaymentRobustnessIntegrationTest.java`), sans aucune altération.
- Aucune migration, configuration, clé/secret, endpoint ou logique Wave n'a été modifiée ni appelée directement lors de ces corrections.

---

## H. Résultats complets des vérifications (après la dernière édition)

| Vérification | Commande / Périmètre | Résultat observé exact | Précisions / Cas ignorés |
|---|---|---|---|
| **Suite Backend complète (H2 / MockMvc / STOMP)** | `mvn test` (dans `backend/`) | **`Tests run: 384, Failures: 0, Errors: 0, Skipped: 5`** (`BUILD SUCCESS`, 04:19 min) | 379 tests exécutés et réussis. Les 5 cas ignorés sont : `FlywayFreshMigrationIntegrationTest` (1, `@EnabledIf("isDatabaseReachable")` sur `ecourtier-db:5432`) et `Postgres16Phase3IntegrationTest` (4, `@EnabledIfSystemProperty("ecourtier.test.pg16=true")`). |
| — dont tests ciblés Phase 3.1 (inclus dans `mvn test`) | `AuthAccessTokenRevocationIntegrationTest` (5), `AuthPhase2SecurityIntegrationTest` (6), `AuthServiceTest` (13), `WebSocketSessionSecurityIntegrationTest` (4), `AnnonceOptimisticLockIntegrationTest` (4), `AnnonceStatutSecurityIntegrationTest` (5), `AnnonceSuppressionSecurityIntegrationTest` (12), `OpenApiContractIntegrationTest` (1), `ProductionConfigurationValidatorTest` (7), `RefreshRateLimiterServiceTest` (4), `SchedulerLockServiceIntegrationTest` (3), `BailPdfReliabilityIntegrationTest` (3) | **100 % réussis (`0` échec, `0` erreur, `0` ignoré)** | Vérifiés individuellement dans le journal Maven Surefire de cette session. |
| **PostgreSQL 16 réel (Testcontainers `postgres:16-alpine`)** | `mvn test -Dtest=Postgres16Phase3IntegrationTest -Decourtier.test.pg16=true` | **`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`** (`BUILD SUCCESS`, PostgreSQL `16.15`, migrations V1 à V20 appliquées en `00:00.603s`) | Couvre : migrations V1..V20 + index partiels/CHECK, `@Version` sur `AnnonceEntity`, index partiel `uq_transactions_annonce_active` + reprise PDF, et `scheduler_locks`. |
| **Synchronisation OpenAPI** | `sha256sum docs/openapi.json backend/src/main/resources/static/openapi.json` | **Identique (`287e3324fe0461bc85c8b7042576ac305fdd8d9ab37a6b95f4eaffe6113de7a6`)** | `63` chemins, `70` opérations, `PUT /api/v1/admin/annonces/{id}/statut` et `AnnonceStatutUpdateDTO` (`statutPublication`, `motif`, `version`) vérifiés. |
| **Frontend Lint, Types & Build** | `npm run lint && npx tsc --noEmit && npm run build` (dans `frontend/`) | **Succès (`code 0`)** : ESLint (`--max-warnings 0`, 0 warning), `tsc`, `vite build` en `17.56s` | 2 841 modules transformés ; PWA `generateSW` générée (`61 entries`, `1053.82 KiB`). |
| **Playwright E2E — UI Sécurité (API mockée)** | `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4173 npx playwright test e2e/phase2-security-ui.spec.ts` | **`4 passed (12.0s)`** | Exécuté contre `vite preview` issu du build final propre. |
| **Playwright E2E — Backend réel H2 isolé** | `spring-boot:test-run -Dspring-boot.run.profiles=test` (`18080`, `jdbc:h2:mem:testdb`) + `phase3-real-backend.spec.ts` | **`6 passed (39.1s)`** | Aucune base persistante ciblée (`create-drop` sur H2 en mémoire). Inclut le parcours combiné `CA3-001`, `CA3-002` et `CA3-004`. |

---

## I. Verdict final du Lot 1

| Anomalie | Gravité initiale | Statut final | Preuves principales |
|---|---|---|---|
| `CA3-001` | P1 — Élevée | **CORRIGÉE ET VÉRIFIÉE** | `JwtService`, `JwtAuthenticationFilter`, `WebSocketSessionRegistry`, `WebSocketAuthInterceptor`, `AuthService`, `AuthAccessTokenRevocationIntegrationTest`, `WebSocketSessionSecurityIntegrationTest`, `phase3-real-backend.spec.ts` |
| `CA3-002` | P1 — Élevée | **CORRIGÉE ET VÉRIFIÉE** | `AuthService`, `AuthController`, `AuthServiceTest`, `AuthAccessTokenRevocationIntegrationTest`, `WebSocketSessionSecurityIntegrationTest`, `phase3-real-backend.spec.ts` |
| `CA3-003` | P1 — Élevée | **CORRIGÉE ET VÉRIFIÉE** | `TransactionDetailsModal.tsx`, `DashboardPage.tsx`, `PropertyDetailPage.tsx`, `AdminTransactionsPage.tsx`, `phase2-security-ui.spec.ts`, `phase3-real-backend.spec.ts` |
| `CA3-004` | P1 — Élevée | **CORRIGÉE ET VÉRIFIÉE** | `AnnonceStatutUpdateDTO`, `AnnonceService`, `docs/openapi.json`, `admin.service.ts`, `AdminVerificationPage.tsx`, `AnnonceOptimisticLockIntegrationTest`, `OpenApiContractIntegrationTest`, `phase2-security-ui.spec.ts`, `phase3-real-backend.spec.ts` |

### Décision formelle
**`LOT 1 VALIDÉ — PRÊT POUR LOT 2`**

Les quatre anomalies bloquantes **P1** identifiées par le contre-audit de la Phase 3 sont résolues et démontrées par les tests unitaires, tests d'intégration Spring/H2, tests d'intégration PostgreSQL 16 Testcontainers, et tests E2E Playwright. Les anomalies résiduelles P2/P3 relevées dans le rapport de contre-audit hors Lot 1 restent à traiter dans les lots suivants.
