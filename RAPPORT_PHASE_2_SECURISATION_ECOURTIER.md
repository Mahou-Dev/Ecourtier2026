# RAPPORT DE PHASE 2 — SÉCURISATION, INTÉGRITÉ MÉTIER ET ROBUSTESSE TRANSACTIONNELLE — E-COURTIER

**Date d'exécution** : `2026-11-14` (`09:23 UTC`)  
**Dépôt analysé** : `/home/mahou-dev/Documents/Ecourtier2026`  
**Branche active** : `main` (`HEAD` : `f2bf8a9` — *Merge branch 'integration/phase-9d-convergence' into main*)  
**Périmètre** : Traitement des réserves **RES-01 à RES-08** issues de [`RAPPORT_CONTRE_AUDIT_PHASE_1_P0_ECOURTIER.md`](file:///home/mahou-dev/Documents/Ecourtier2026/RAPPORT_CONTRE_AUDIT_PHASE_1_P0_ECOURTIER.md) en continuité de [`RAPPORT_PHASE_1_REMEDIATION_P0_ECOURTIER.md`](file:///home/mahou-dev/Documents/Ecourtier2026/RAPPORT_PHASE_1_REMEDIATION_P0_ECOURTIER.md).

---

## 1. VERDICT EXÉCUTIF

**Statut global de la Phase 2 : RÉSOLUTIONS P1 ET P2 IMPLÉMENTÉES ET VÉRIFIÉES (AVEC LIMITES EXPLICITES SUR L'ENVIRONNEMENT DE SIMULATION PSP ET L'OUTILLAGE LINT).**

Toutes les failles et réserves critiques **P1** (`RES-01`, `RES-02`, `RES-03`) et les réserves **P2** (`RES-04`, `RES-05`, `RES-06`, `RES-07`, `RES-08`) ont reçu des corrections ciblées dans le code backend/frontend, accompagnées d'une migration Flyway additive (`V19`), d'une resynchronisation des spécifications OpenAPI et d'une campagne de tests reproductibles sur **H2**, **PostgreSQL 16.15** et **Playwright**.

Limites et faits non exécutés mentionnés explicitement par transparence :
1. **Prestataire Wave (`RES-02`)** : [`WaveClientMock`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/client/WaveClientMock.java) est volontairement conservé pour les tests et démonstrations ; aucun appel vers une API Wave réelle ni aucune clé secrète de production n'a été utilisé. L'architecture est désormais prête (outbox `paiements_wave`, appel hors transaction SQL, webhook HMAC-SHA256 et réconciliation asynchrone) pour brancher un futur client HTTP réel sans réécriture du domaine.
2. **ESLint Frontend (`RES-08`)** : `eslint` n'est pas installé dans l'environnement (`frontend/node_modules/.bin/eslint` et binaire global absents). La vérification TypeScript stricte (`tsc`) et le bundle Vite/PWA ont en revanche été exécutés avec succès.
3. **Tests E2E Playwright Phase 2 (`RES-08`)** : Exécutés contre `vite preview` avec interception et simulation des réponses `/api/v1/**` (`page.route`) pour isoler les comportements UI critiques.
4. **UI Admin sur `PUT /api/v1/admin/annonces/{id}/statut` (`RES-06`)** : L'endpoint administratif et son schéma OpenAPI sont en place et testés côté backend, mais aucun nouveau contrôle UI dédié n'a été ajouté dans `AdminVerificationPage.tsx`.
5. **Verrou distribué scheduler (`RES-07`)** : L'application fonctionne avec un scheduler Spring local mono-instance (`TransactionExpirationScheduler` et `WavePaymentReconciliationService`) ; l'introduction de ShedLock reste pertinente en cas de déploiement multi-réplicas.

---

## 2. INTÉGRITÉ DU DÉPÔT ET PROTECTION DE L'ENVIRONNEMENT

- **Aucune opération destructrice Git** : Aucun `git reset`, `clean`, `checkout`, `switch`, `stash`, `rebase`, `commit`, `push`, `merge` ni déploiement n'a été exécuté.
- **Worktrees préservés** : `/home/mahou-dev/Documents/Ecourtier2026`, `-convergence`, `-lot1`, `-lot2`, `-lot3`, `-validation` inchangés.
- **Migrations historiques immuables** : `V1` à `V18` n'ont subi aucune modification (`git diff` vide sur `V1`–`V18`). La seule nouvelle migration ajoutée est additive : [`V19__phase2_security_payment_and_refresh_tokens.sql`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/resources/db/migration/V19__phase2_security_payment_and_refresh_tokens.sql).
- **Isolation PostgreSQL 16** : Toutes les vérifications PostgreSQL 16 ont été menées sur un conteneur éphémère isolé `ecourtier-pg16-phase2` (`postgres:16-alpine`, port `127.0.0.1:55441`), intégralement détruit en fin de campagne (`docker rm -f ecourtier-pg16-phase2`). Les conteneurs préexistants (`ecourtier-db`, `ecourtier-postgres`, `ecourtier-backend`, `ecourtier-frontend`, `sununatt-*`, `samaadresse-*`) n'ont pas été touchés.
- **Hygiène des secrets** : Contrôle sur `git diff` confirmant l'absence de toute clé Groq/Wave/JWT réelle (`NO_SECRETS_FOUND_IN_DIFF`).

---

## 3. MATRICE DE DIAGNOSTIC ET SUIVI DES RÉSERVES (RES-01 À RES-08)

| ID | Gravité | Fichier / Méthode principal(e) | Problème constaté (Contre-audit Phase 1) | Correction appliquée (Phase 2) | Preuve par tests (observée après dernières éditions) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **RES-01** | **P1** | [`AnnonceService.supprimerAnnonceLogique`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/service/AnnonceService.java#L251-L309) | Suppression logique sans verrou `FOR UPDATE`, ni contrôle sur `RESERVEE`/`LOUEE`/`VENDUE`/`SUSPENDUE` ou transactions `INITIALISE`/`BLOQUE` actives. | Verrouillage canonique `Annonce -> Transaction`, vérification propriétaire/admin (`403`), blocage `409 Conflict` sur transactions actives (`INITIALISE`/`BLOQUE`) et statuts `RESERVEE`/`LOUEE`/`VENDUE`/`SUSPENDUE`. | `AnnonceSuppressionSecurityIntegrationTest` (**10/10** H2 & **10/10** PG16), `AnnonceServiceTest` (**24/24**). |
| **RES-02** | **P1** | [`TransactionService.effectuerPaiementCaution`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionService.java#L358-L534), [`WaveWebhookService`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/service/WaveWebhookService.java), [`WavePaymentReconciliationService`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/service/WavePaymentReconciliationService.java) | Appel synchrone à `WaveClient` à l'intérieur de `@Transactional` sous verrous pessimistes ; absence d'outbox, d'idempotence, de webhook HMAC et de réconciliation/compensation. | Découplage en 3 phases (claim DB court -> appel PSP hors TX/verrou -> confirmation DB courte), table `paiements_wave` (`V19`), endpoint `POST /api/v1/webhooks/wave` HMAC-SHA256, gestion paiements tardifs (`TARDIF_A_REMBOURSER` + remboursement compensatoire) et réconciliation planifiée. `WaveClientMock` conservé. | `WavePaymentRobustnessIntegrationTest` (**10/10** H2 & **10/10** PG16), `WorkflowTransactionIntegrationTest` (**15/15**), `TransactionServiceTest` (**9/9**). |
| **RES-03** | **P1** | [`TransactionService.signerBail`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionService.java#L869-L977), [`TransactionService.annulerTransaction`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionService.java#L537-L593), [`TransactionDetailsModal.tsx`](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/src/components/transaction/TransactionDetailsModal.tsx#L545-L570) | Signature autorisée en `INITIALISE`, absence de garde de signature sur annulation/expiration, et régression d'état `SIGNE_PROPRIETAIRE -> SIGNE_LOCATAIRE` via branchement `ADMIN`. | Signature strictement restreinte au statut `BLOQUE` (`409` en `INITIALISE`/`ANNULE`/`EXPIRE`/`REMBOURSE`/`LIBERE`), branchement `ADMIN` corrigeant l'ordre (`dateSignatureLocataire == null` vs `dateSignatureProprietaire == null` menant à `SIGNE_COMPLET`), blocage annulation/expiration si signature présente (`hasBailSignature`), UI alignée (`TransactionDetailsModal.tsx`). | `BailSignatureLifecycleIntegrationTest` (**12/12** H2 & **12/12** PG16), `TransactionLifecycleP03IntegrationTest` (**4/4** H2 & PG16), Playwright (**1/1** scénario dédié). |
| **RES-04** | **P2** | [`WebSocketAuthInterceptor`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/WebSocketAuthInterceptor.java#L41-L122), [`MessagerieService.envoyerMessage`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/messagerie/service/MessagerieService.java#L109-L149), [`UtilisateurManagementService.changerStatut`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/utilisateur/service/UtilisateurManagementService.java#L129-L172) | Aucune revérification de `actif` ou d'expiration JWT sur les frames `SEND`/`SUBSCRIBE` après `CONNECT` ; sessions STOMP maintenues après désactivation. | Contrôle d'expiration JWT et `isUtilisateurActif` à `CONNECT`, `SUBSCRIBE` et `SEND` ; `MessagerieService.envoyerMessage` rejette `isActif() == false` en `403` ; `WebSocketSessionRegistry.disconnectUser` déconnecte activement l'utilisateur en `afterCommit` dès désactivation. | `MessagerieStompIntegrationTest` (**6/6** H2 & **6/6** PG16), `MessagerieSecurityTest` (**3/3**), `MessagerieServiceTest` (**11/11**), `MessagerieRestControllerIntegrationTest` (**1/1**). |
| **RES-05** | **P2** | [`JwtService`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/JwtService.java#L51-L115), [`AuthService`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/utilisateur/service/AuthService.java#L175-L255), [`RefreshRateLimiterService`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/RefreshRateLimiterService.java) | Refresh tokens rejouables sans `jti` ni révocation ; absence de `iss`/`aud` et de rate-limit sur `/rafraichir`. | Emission de refresh tokens à `jti` persisté (hash SHA-256 dans `refresh_tokens`, `V19`), rotation à usage unique avec détection de rejeu révoquant toute la famille (`revokeFamily`), claims `iss`/`aud` validés, endpoint `POST /api/v1/auth/deconnexion` + `useAuthStore.ts`, rate-limiter `RefreshRateLimiterService` (20 essais / 60s -> `429`). | `AuthPhase2SecurityIntegrationTest` (**6/6** H2 & **6/6** PG16), `AuthServiceTest` (**11/11**), `JwtServiceTest` (**6/6**), `AuthIntegrationTest` (**5/5**), `AdminUtilisateurIntegrationTest` (**5/5**), Playwright logout (**1/1**). |
| **RES-06** | **P2** | [`AnnonceResponseDTO`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/dto/AnnonceResponseDTO.java), [`AnnonceUpdateDTO`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/dto/AnnonceUpdateDTO.java), [`GlobalExceptionHandler`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/exception/GlobalExceptionHandler.java), [`SignalementService`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/avis/service/SignalementService.java), [`EditAnnonceModal.tsx`](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/src/components/dashboard/EditAnnonceModal.tsx) | `@Version` absent des DTO HTTP (risque de *lost update*) ; `.orElse(signalement.getAnnonce())` non verrouillé sur annonce supprimée ; UI admin absente pour `PUT /api/v1/admin/annonces/{id}/statut`. | Exposition de `version` dans `AnnonceResponseDTO`/`AnnonceUpdateDTO`/`AnnonceMapper`, validation de version sous `PESSIMISTIC_WRITE` + mapping `ObjectOptimisticLockingFailureException -> 409 Conflict`, transmission et gestion `409` dans `EditAnnonceModal.tsx`, rejet `409 Conflict` dans `SignalementService.traiterSignalement` sur annonce supprimée. *(Limite : UI admin `AdminVerificationPage` non modifiée).* | `AnnonceOptimisticLockIntegrationTest` (**3/3** H2 & **3/3** PG16), `SignalementServiceTest` (**3/3**), `AvisAndSignalementIntegrationTest` (**3/3**), `GlobalExceptionHandlerTest` (**10/10**), Playwright (`EditAnnonceModal` version & `409` **1/1**). |
| **RES-07** | **P2** | [`TransactionEntity`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/entity/TransactionEntity.java), [`RendezVousEntity`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/rendezvous/entity/RendezVousEntity.java), [`TransactionService`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionService.java), [`AnnonceService`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/service/AnnonceService.java), [`AdminStatistiquesService`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/admin/service/AdminStatistiquesService.java) | `@CreationTimestamp` contournant `Clock` (`Africa/Dakar`), `LocalDateTime.now()` brut dans les services, `REMBOURSE` laissant `dossierActif=true`, absence de verrou distribué scheduler. | Suppression `@CreationTimestamp` au profit de `@PrePersist` sur `ZoneId.of("Africa/Dakar")`, unification de `Clock` (`Africa/Dakar`) injectable dans `TransactionService`, `AnnonceService`, `AdminStatistiquesService`, `MessagerieService` et `WavePaymentReconciliationService` ; passage explicite de `dossierActif = false` sur `REMBOURSE` (`rembourserTransaction` et `finalizeReconciledRefund`). *(Limite : scheduler mono-instance).* | `TransactionClockAndRefundDossierIntegrationTest` (**2/2** H2 & **2/2** PG16), `AdminStatistiquesServiceTest` (**6/6**), `AnnonceServiceTest` (**24/24**). |
| **RES-08** | **P2** | [`docs/openapi.json`](file:///home/mahou-dev/Documents/Ecourtier2026/docs/openapi.json), [`openapi.json` static](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/resources/static/openapi.json), `OpenApiContractIntegrationTest`, suite PG16 & Playwright | Spécifications OpenAPI statiques désynchronisées ; absence de tests PG16 de migration incrémentale/IT et de tests E2E UI P0 ; `eslint` indisponible. | Synchronisation à l'identique des 2 fichiers OpenAPI (`5573` lignes, `@ApiResponses` sur 6 contrôleurs + `OpenApiContractIntegrationTest`), campagne PostgreSQL 16.15 (`V1..V19` fresh, upgrade peuplé `V18->V19`, rollback `V19` cassé, 8 suites IT = 53 tests), tests Playwright ciblés (`phase2-security-ui.spec.ts`). `eslint` absent documenté. | `OpenApiContractIntegrationTest` (**1/1**), `FlywayFreshMigrationIntegrationTest` (**1/1** PG16), `53/53` IT sur PG16.15, Playwright (**2/2** mockés), build Vite/TS (**exit 0**). |

---

## 4. DÉTAIL TECHNIQUE DES CORRECTIONS PAR AXE

### 4.1 Axe P1 — Intégrité des annonces et suppression logique (`RES-01`)

1. **Verrouillage canonique et atomique** : `AnnonceService.supprimerAnnonceLogique(UUID id, String actorEmail, boolean isAdmin)` acquiert le verrou pessimiste selon l'ordre canonique `Annonce -> Transaction` avant d'évaluer l'éligibilité à la suppression.
2. **Règles métier bloquantes (`409 Conflict`)** :
   - Présence d'une transaction active au statut `INITIALISE` ou `BLOQUE` ;
   - Annonce au statut de publication `RESERVEE`, `LOUEE`, `VENDUE` ou `SUSPENDUE` (préservation du dossier de modération y compris face à un appel `DELETE` administratif, la réouverture administrative passant exclusivement par `PUT /api/v1/admin/annonces/{id}/statut`).
3. **Contrôle d'accès (`403 Forbidden`)** : Rejet de tout utilisateur non propriétaire (autre propriétaire ou locataire).
4. **Tests de non-corruption et concurrence** : [`AnnonceSuppressionSecurityIntegrationTest`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/domain/annonce/controller/AnnonceSuppressionSecurityIntegrationTest.java) exécute notamment 5 itérations concurrentes (`CountDownLatch` à 2 threads) entre `DELETE /api/v1/annonces/{id}` et `POST /api/v1/annonces/{id}/transactions`, vérifiant qu'aucune annonce supprimée ne porte de transaction active et qu'une transaction initiée bloque la suppression en `409`.

---

### 4.2 Axe P1 — Sécurisation du cycle de signature du bail (`RES-03`)

1. **Préconditions strictes de signature (`TransactionService.signerBail`)** :
   - Vérification du verrou `lockAnnonceThenTransaction(transactionId)` et de `dossierActif == true` (`409 Conflict` sinon).
   - Blocage en `409 Conflict` si `statutSequestre` est `REMBOURSE`, `ANNULE`, `EXPIRE`, `LIBERE` ou encore `INITIALISE` (avec clôture préalable si le délai de 24h d'un `INITIALISE` sans référence Wave ni signature est expiré).
   - Blocage en `409 Conflict` si l'annonce est supprimée ou `SUSPENDUE`.
2. **Correction du branchement administratif et prévention de régression** :
   - Pour le rôle `ADMIN` (tiers signataire pour suppléance administrative), l'ordre de complétion teste d'abord `tx.getDateSignatureLocataire() == null` puis `tx.getDateSignatureProprietaire() == null`. Ainsi, lorsqu'un bail est déjà `SIGNE_PROPRIETAIRE`, l'intervention d'un administrateur enregistre la signature locataire manquante et conclut `SIGNE_COMPLET` (puis déclenche la libération automatique) sans jamais régresser vers `SIGNE_LOCATAIRE`.
3. **Protection de l'annulation, de l'expiration et de la libération** :
   - `annulerTransaction` et `expirerTransactionIndividuelle` refusent désormais toute clôture si `hasBailSignature(tx)` est vrai (`409 Conflict`).
   - `verifierEtDeclencherLiberation` exige conjointement `statutBail == StatutBail.SIGNE_COMPLET` et `statutSequestre == StatutSequestre.BLOQUE` avant de passer à `LIBERE`, transiter l'annonce (`LOUEE`/`VENDUE`) via `AnnonceStatutStateMachine`, et générer le bail PDF.
4. **Cohérence UI** : [`TransactionDetailsModal.tsx`](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/src/components/transaction/TransactionDetailsModal.tsx#L547-L568) masque le bouton d'action de signature lorsque `statutSequestre !== 'BLOQUE'` et affiche le message explicite d'attente de confirmation du séquestre Wave.

---

### 4.3 Axe P1 — Architecture paiement robuste et préparation Wave (`RES-02`)

1. **Élimination des appels PSP sous transaction SQL longue (`TransactionService.effectuerPaiementCaution`)** :
   - **Phase 1 (TX SQL courte, verrou `Annonce -> Transaction`)** : Validation d'état et prise de jeton d'opération (`claimPaiementWaveOperation`) dans la table outbox [`paiements_wave`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/entity/PaiementWaveEntity.java) (`StatutPaiementWave` : `INITIE`, `EN_COURS`, `A_RECONCILIER`, `CONFIRME`, `ECHOUE`, `TARDIF_A_REMBOURSER`, `RECONCILIE`), protégée en base PostgreSQL par les index partiels uniques `uq_paiements_wave_tx_op_actif` et `uq_paiements_wave_reference_externe` (`V19`).
   - **Phase 2 (HORS transaction SQL, SANS verrou DB)** : Appel réseau `waveClient.initiateEscrowCheckout(...)` puis `waveClient.verifyPaymentStatus(...)`. En cas d'erreur de communication ou d'incertitude post-initiation, transition `REQUIRES_NEW` vers `A_RECONCILIER` ou `ECHOUE` avec retour `503`/`502`.
   - **Phase 3 (TX SQL courte)** : `confirmWavePaymentInTransaction` re-verrouille `Annonce -> Transaction` ; en cas d'échec de persistance SQL post-débit Wave, l'opération reste tracée en `A_RECONCILIER` pour reprise automatique.
2. **Webhook HMAC et notifications tardives/dupliquées** :
   - [`WaveWebhookController`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/controller/WaveWebhookController.java) expose `POST /api/v1/webhooks/wave`.
   - [`WaveWebhookService.traiterWebhook`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/service/WaveWebhookService.java#L42) vérifie la signature HMAC-SHA256 et délègue à `TransactionService.confirmWavePaymentFromExternal`.
   - Les doublons sont rendus idempotents ; un paiement confirmé tardivement sur dossier expiré ou clôturé ne réouvre ni ne libère le séquestre : il est basculé vers `TARDIF_A_REMBOURSER`, puis remboursé de manière compensatoire par [`WavePaymentReconciliationService`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/service/WavePaymentReconciliationService.java#L187) (également déclenchable manuellement par un administrateur via `POST /api/v1/admin/transactions/reconcilier-wave`).
3. **Statut du client Wave** : `WaveClientMock` reste l'unique implémentation active ; aucun endpoint Wave réel n'a été inventé.

---

### 4.4 Axe P2 — Sécurité WebSocket STOMP et Refresh Tokens (`RES-04`, `RES-05`)

- **WebSocket STOMP (`RES-04`)** :
  - [`WebSocketAuthInterceptor.preSend`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/WebSocketAuthInterceptor.java#L41-L122) contrôle non seulement le frame `CONNECT`, mais aussi `SUBSCRIBE` et `SEND` : appartenance à la conversation (`conversationSecurityService.isParticipant`), expiration JWT mémorisée en attribut de session (`jwtExpiresAtMillis`), et statut actif en base (`isUtilisateurActif`).
  - [`MessagerieService.envoyerMessage`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/messagerie/service/MessagerieService.java#L132-L134) revérifie `expediteur.isActif()`.
  - [`UtilisateurManagementService.changerStatut`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/utilisateur/service/UtilisateurManagementService.java#L153-L170) révoque tous les refresh tokens actifs de l'utilisateur lors d'une désactivation (`actif = false`) et ferme ses sessions STOMP ouvertes via [`WebSocketSessionRegistry.disconnectUser`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/WebSocketSessionRegistry.java#L57) dans `TransactionSynchronization.afterCommit()`.
- **Refresh Tokens et déconnexion (`RES-05`)** :
  - [`JwtService`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/JwtService.java#L67-L109) émet des jetons avec `jti`, `iss` et `aud` strictement validés.
  - [`AuthService.rafraichir`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/utilisateur/service/AuthService.java#L175-L227) vérifie le hash SHA-256 en table `refresh_tokens` sous `findByJtiForUpdate` ; toute tentative de réutilisation d'un token déjà utilisé/révoqué invalide l'ensemble de la famille (`revokeFamily`).
  - `POST /api/v1/auth/deconnexion` ([`AuthService.deconnecter`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/utilisateur/service/AuthService.java#L234)) est câblé avec [`useAuthStore.ts`](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/src/store/useAuthStore.ts#L37).
  - [`RefreshRateLimiterService`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/RefreshRateLimiterService.java) limite `/api/v1/auth/rafraichir` à 20 requêtes par fenêtre de 60 secondes par IP (`429 Too Many Requests`).

---

### 4.5 Axe P2 — Verrou optimiste HTTP, Signalements, Horloges `Africa/Dakar` et OpenAPI (`RES-06`, `RES-07`, `RES-08`)

- **Optimistic Locking & Signalement (`RES-06`)** :
  - `version` ajouté dans `AnnonceResponseDTO`, `AnnonceUpdateDTO`, `AnnonceMapper` et `AnnonceDTO` (`frontend/src/types/index.ts`).
  - `AnnonceService.modifierAnnonce` rejette en `409 Conflict` une version divergente sous verrou `PESSIMISTIC_WRITE`, et [`GlobalExceptionHandler`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/exception/GlobalExceptionHandler.java) mappe `ObjectOptimisticLockingFailureException` en HTTP `409` JSON UTF-8.
  - [`EditAnnonceModal.tsx`](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/src/components/dashboard/EditAnnonceModal.tsx) transmet `version` dans le `PUT` et réagit aux réponses `409` par un message utilisateur + rafraîchissement.
  - [`SignalementService.traiterSignalement`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/avis/service/SignalementService.java) remplace l'ancien fallback `.orElse(signalement.getAnnonce())` par un rejet explicite `409 Conflict` lorsqu'un signalement porte sur une annonce supprimée.
- **Horloges `Africa/Dakar` & `dossierActif` (`RES-07`)** :
  - Retrait de `@CreationTimestamp` sur `TransactionEntity` et `RendezVousEntity` au profit d'un `@PrePersist` alimenté en `LocalDateTime.now(ZoneId.of("Africa/Dakar"))`.
  - Injection de `Clock` configuré sur `Africa/Dakar` dans `TransactionService`, `AnnonceService`, `AdminStatistiquesService`, `MessagerieService` et `WavePaymentReconciliationService`.
  - Dans `TransactionService`, `rembourserTransaction` et `finalizeReconciledRefund` positionnent désormais explicitement `tx.setDossierActif(Boolean.FALSE)` lors du passage au statut `REMBOURSE`.
- **Contrat OpenAPI (`RES-08`)** :
  - Annotations `@ApiResponses` (codes réels `200`/`201`/`204`/`400`/`401`/`403`/`404`/`409`/`429`/`502`/`503`, sans codes `422` fictifs) ajoutées sur `AuthController`, `AnnonceController`, `AdminAnnonceController`, `TransactionController`, `WaveWebhookController` et `SignalementController`.
  - Régénération à partir de `/v3/api-docs` en conditions d'exécution : [`docs/openapi.json`](file:///home/mahou-dev/Documents/Ecourtier2026/docs/openapi.json) et [`backend/src/main/resources/static/openapi.json`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/resources/static/openapi.json) sont strictement identiques (`5573` lignes, ajout des 8 opérations manquantes dont `/transactions/{id}/annuler`, `/transactions/{id}/rembourser`, `/transactions/reconcilier-wave`, `/webhooks/wave`, `/auth/deconnexion`, `/admin/annonces/{id}/statut`, `version` sur les DTO d'annonce et nouveaux champs `TransactionResponseDTO`).
  - [`OpenApiContractIntegrationTest`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/common/controller/OpenApiContractIntegrationTest.java) vérifie automatiquement cette parité dans la suite Maven.

---

## 5. RÉSULTATS DES TESTS ET MÉTRIQUES OBSERVÉS (EXÉCUTION APRÈS DERNIÈRES ÉDITIONS)

### 5.1 Suite Backend complète (H2 / environnement Maven hors-ligne par défaut)

Commande : `mvn -f backend/pom.xml -o test`

- **Résultat global** : **`Tests run: 347, Failures: 0, Errors: 0, Skipped: 1`** (`BUILD SUCCESS`, `01:37 min`).
- **Détail de l'unique test ignoré** : `com.ecourtier.common.config.FlywayFreshMigrationIntegrationTest` (`Skipped: 1` en mode défaut hors PostgreSQL accessible), **exécuté et validé séparément sur PostgreSQL 16.15** (voir § 5.3).
- **Couverture JaCoCo (`backend/target/site/jacoco/jacoco.csv`, `123` classes)** :
  - **Instructions** : **`84.13 %`** (`19 555 / 23 245`)
  - **Branches** : **`61.68 %`** (`1 954 / 3 168`)
  - **Lignes** : **`87.18 %`** (`4 150 / 4 760`)
  - **Méthodes** : **`85.03 %`** (`568 / 668`)
  - Extraits de couverture lignes/branches par packages critiques :
    - `com.ecourtier.domain.annonce.service` : lignes `331/344 (96.2%)`, branches `228/293 (77.8%)`
    - `com.ecourtier.domain.avis.service` : lignes `95/100 (95.0%)`, branches `16/22 (72.7%)`
    - `com.ecourtier.domain.utilisateur.service` : lignes `180/201 (89.6%)`, branches `59/96 (61.5%)`
    - `com.ecourtier.domain.transaction.client` : lignes `80/90 (88.9%)`, branches `47/76 (61.8%)`
    - `com.ecourtier.common.security` : lignes `362/414 (87.4%)`, branches `207/344 (60.2%)`
    - `com.ecourtier.domain.messagerie.service` : lignes `93/108 (86.1%)`, branches `27/38 (71.1%)`
    - `com.ecourtier.domain.transaction.service` : lignes `818/1000 (81.8%)`, branches `446/722 (61.8%)`

### 5.2 Répartition par suites critiques Phase 2 (Surefire XML)

- `AnnonceSuppressionSecurityIntegrationTest` : `10/10` | `AnnonceOptimisticLockIntegrationTest` : `3/3` | `AnnonceServiceTest` : `24/24`
- `BailSignatureLifecycleIntegrationTest` : `12/12` | `WavePaymentRobustnessIntegrationTest` : `10/10` | `TransactionLifecycleP03IntegrationTest` : `4/4`
- `TransactionClockAndRefundDossierIntegrationTest` : `2/2` | `WorkflowTransactionIntegrationTest` : `15/15` | `TransactionServiceTest` : `9/9`
- `AuthPhase2SecurityIntegrationTest` : `6/6` | `AuthServiceTest` : `11/11` | `JwtServiceTest` : `6/6` | `AuthIntegrationTest` : `5/5` | `AdminUtilisateurIntegrationTest` : `5/5`
- `MessagerieStompIntegrationTest` : `6/6` | `MessagerieSecurityTest` : `3/3` | `MessagerieServiceTest` : `11/11`
- `SignalementServiceTest` : `3/3` | `AvisAndSignalementIntegrationTest` : `3/3` | `AdminStatistiquesServiceTest` : `6/6`
- `OpenApiContractIntegrationTest` : `1/1` | `CommonControllersTest` : `4/4`

### 5.3 Campagne PostgreSQL 16.15 isolée (`ecourtier-pg16-phase2`)

Exécutée contre une instance PostgreSQL `16.15` dédiée (`postgres:16-alpine` sur `127.0.0.1:55441`), avec `hibernate.ddl-auto=validate` :

1. **Migration fraîche `V1..V19` (`FlywayFreshMigrationIntegrationTest`)** : `Successfully applied 19 migrations to schema "public", now at version v19`, validation JPA `validate` OK -> **`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`**.
2. **Migration incrémentale peuplée `V18 -> V19` (`ecourtier_upgrade_test`)** : Application `V1..V18` (`18` migrations) -> insertion de données peuplées (`utilisateurs`, `annonces`, `rendez_vous`, `transactions` `BLOQUE` / `SIGNE_LOCATAIRE` / `WAVE_HIST_V18_TEST`) -> application incrémentale `V19` (`target=19`) -> transaction historique intégralement préservée et création des tables `paiements_wave` et `refresh_tokens` avec index partiels uniques confirmée.
3. **Rollback transactionnel d'une `V19` invalide (`ecourtier_rollback_test`)** : Exécution sur base `V1..V18` d'une migration `V19` fautive contenant un `CREATE TABLE tmp_should_rollback` suivi d'une syntaxe SQL invalide -> échec intercepté par Flyway (`Changes successfully rolled back`), table `tmp_should_rollback` absente (`0`) et schéma maintenu proprement en version `18`.
4. **8 suites d'intégration exécutées directement sur PostgreSQL 16.15 (`ecourtier_pg16_it`, `V1..V19` + `ddl-auto=validate`)** :
   - `AnnonceOptimisticLockIntegrationTest` : `3/3`
   - `AnnonceSuppressionSecurityIntegrationTest` : `10/10`
   - `MessagerieStompIntegrationTest` : `6/6`
   - `BailSignatureLifecycleIntegrationTest` : `12/12`
   - `TransactionLifecycleP03IntegrationTest` : `4/4`
   - `WavePaymentRobustnessIntegrationTest` : `10/10`
   - `TransactionClockAndRefundDossierIntegrationTest` : `2/2`
   - `AuthPhase2SecurityIntegrationTest` : `6/6`
   - **Total PostgreSQL 16.15** : **`Tests run: 53, Failures: 0, Errors: 0, Skipped: 0`** (`BUILD SUCCESS`).

### 5.4 Vérifications Frontend (`frontend/`)

1. **Compilation TypeScript & Build Production (`npm --prefix frontend run build` = `tsc && vite build`)** : **exit code `0`**, bundle généré en `8.57 s`, Service Worker PWA (`61 entries`, `1049.61 KiB`).
2. **Linting (`eslint`)** : `ESLINT_NOT_INSTALLED` (binaire `eslint` non présent dans `frontend/node_modules/.bin` ni sur le système).
3. **Playwright E2E (`frontend/e2e/phase2-security-ui.spec.ts`)** : Exécuté sur Chromium contre `vite preview` avec APIs `/api/v1/**` mockées :
   - *Signature du bail verrouillée en `INITIALISE` et débloquée en `BLOQUE`* : **PASSED (`1.2 s`)** ;
   - *`EditAnnonceModal` transmet `version` (`3`), gère un conflit HTTP `409`, et la déconnexion appelle `POST /api/v1/auth/deconnexion` avec `refreshToken` et `Bearer` token* : **PASSED (`1.1 s`)** ;
   - **Total** : **`2 passed (3.1s)`**.

---

## 6. CONCLUSION

Les vulnérabilités et fragilités résiduelles P1 et P2 soulevées lors du contre-audit de Phase 1 ont été corrigées de manière cohérente sur toute la chaîne (modèle PostgreSQL 16 Flyway `V19`, services verrouillés et découpés hors transaction SQL longue pour Wave, sécurité STOMP et rotation JWT révocable, contrats DTO/OpenAPI et garde-fous frontend), avec preuves reproductibles sur H2, PostgreSQL 16.15 et Chromium/Playwright.
