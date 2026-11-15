# RAPPORT DE CONTRE-AUDIT INDÉPENDANT — PHASE 1 REMÉDIATION P0 — E-COURTIER

- **Objet** : Contre-vérification technique, fonctionnelle, sécuritaire et base de données des corrections décrites dans [`RAPPORT_PHASE_1_REMEDIATION_P0_ECOURTIER.md`](file:///home/mahou-dev/Documents/Ecourtier2026/RAPPORT_PHASE_1_REMEDIATION_P0_ECOURTIER.md).
- **Posture** : Audit indépendant en lecture seule, sans modification des sources, tests, migrations ou bases partagées.
- **Échelle de verdicts utilisée** : `VALIDÉ INDÉPENDAMMENT` | `VALIDÉ AVEC RÉSERVES` | `NON VALIDÉ` | `NON VÉRIFIABLE`.

---

## 1. IDENTIFICATION DU DÉPÔT ET PROTOCOLE DE VÉRIFICATION

| Élément | Valeur observée |
| :--- | :--- |
| Dépôt local | `/home/mahou-dev/Documents/Ecourtier2026` |
| Branche active | `main` |
| Commit `HEAD` | `f2bf8a97af5c9fc43c40d25a0ed9eeacfb25f317` (`Merge branch 'integration/phase-9d-convergence' into main`) |
| État local vs historique | Modifications de travail locales non commitées (codes P0/IA + migrations non suivies `V16`, `V17`, `V18`) préservées intégralement |
| Migrations historiques `V1`–`V15` | Aucune modification (`git diff --name-status HEAD -- backend/src/main/resources/db/migration/` vide) |
| Bases réelles préservées | `ecourtier-db`, `ecourtier-postgres`, `ecourtier-backend` non touchées |
| Environnement PostgreSQL 16 isolé | Conteneur éphémère `ecourtier-pg16-audit` (`postgres:16-alpine`, PostgreSQL `16.15`, port `127.0.0.1:55440`), détruit en fin de campagne et vérifié absent via `docker ps -a` |

---

## 2. SYNTHÈSE DES VERDICTS

| Périmètre | Verdict | Constat principal |
| :--- | :--- | :--- |
| **P0-1 / Axe 1 — JWT & WebSocket** | **`VALIDÉ AVEC RÉSERVES`** | Séparation stricte `ACCESS`/`REFRESH` prouvée sur REST et `CONNECT` STOMP ; réserves P2 sur la persistance des sessions STOMP après désactivation de compte, l'absence de révocation/rotation des refresh tokens (`jti`), l'absence de `iss`/`aud` et de rate-limiting sur `/rafraichir`. |
| **P0-2 / Axe 2 — Machine à états Annonces** | **`VALIDÉ AVEC RÉSERVES`** | `PUT /annonces/{id}` verrouillé pour non-admin et endpoint admin `/statut` restreint à `DISPONIBLE <-> SUSPENDUE` sans séquestre `BLOQUE` ; réserves P1/P2 sur le contournement par suppression logique `supprimerAnnonceLogique` (sans `FOR UPDATE` ni contrôle des transactions actives), repli non verrouillé dans `SignalementService`, absence de `version` dans les DTO HTTP et absence d'UI admin/OpenAPI pour `/statut`. |
| **P0-3 / Axe 3 — Cycle de vie Transaction** | **`VALIDÉ AVEC RÉSERVES`** | `ANNULE`, `EXPIRE`, TTL `PT24H`, index partiel `uq_transactions_annonce_active` et verrou `Annonce -> Transaction` opérationnels ; réserves P1/P2 majeures sur `WaveClientMock` unique (aucun client/webhook Wave réel) avec appel PSP sous verrou DB `FOR UPDATE`, signature bail possible avant paiement + bug admin `SIGNE_PROPRIETAIRE -> SIGNE_LOCATAIRE`, divergence `@CreationTimestamp` vs `Clock`, et désynchronisation OpenAPI. |
| **Axe 4 — Flyway V18 & PostgreSQL 16** | **`VALIDÉ AVEC RÉSERVES`** | V1–V18 validées à blanc (`ddl-auto=validate`), upgrade peuplé V17→V18 exécuté sans perte, rollback DDL/DML atomique prouvé et index partiel/verrou `55P03` prouvés sur PostgreSQL 16.15 ; réserve méthodologique : le dépôt ne contenait pas de test automatisé V17→V18 peuplé ni de test de rollback (prouvés par banc externe), et V18 renseigne `date_expiration` même sur les anciens `INITIALISE` à `dossier_actif = FALSE`. |
| **Axe 5 — Qualité & Authenticité des Tests** | **`VALIDÉ AVEC RÉSERVES`** | Suite Maven complète verte (`290` tests, `0` échec, `1` ignoré en mode H2 ; `7/7` passés sur PG16 pour Flyway/V14) ; mais les tests d'intégration P0 (`TransactionLifecycleP03IntegrationTest`, etc.) tournent sur H2 `MODE=PostgreSQL` avec `WaveClientMock`, `AnnonceServiceTest`/`SignalementServiceTest` utilisent Mockito, `eslint` est absent et il existe `0` test frontend (E2E ou unitaire) pour P0-1/P0-2/P0-3. |
| **Axe 6 — Non-Régression & Contrats API** | **`VALIDÉ AVEC RÉSERVES`** | Suites backend (`290` tests) et build TypeScript/Vite PWA sans régression ; mais `docs/openapi.json` et `backend/src/main/resources/static/openapi.json` sont obsolètes (endpoints et statuts P0 absents). |
| **VERDICT GLOBAL PHASE 1 P0** | **`VALIDÉ AVEC RÉSERVES`** | Les trois vulnérabilités P0 initiales sont effectivement résorbées sur leurs chemins principaux sans régression des suites existantes, mais des réserves concrètes P1/P2 exigent une Phase 2 avant mise en production. |

---

## 3. AXE 1 — CONTRE-AUDIT JWT & SÉCURITÉ REST / WEBSOCKET (P0-1)

### 3.1 Vérifications positives reproduites dans le code et les tests
1. **Génération et claim `tokenType`** : [`JwtService.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/JwtService.java#L54-L85) définit `TOKEN_TYPE_ACCESS = "ACCESS"` (ligne 84) et `TOKEN_TYPE_REFRESH = "REFRESH"` (ligne 85), injectés explicitement dans `buildToken` (lignes 54-65).
2. **Validation stricte par usage** : [`isAccessTokenValid`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/JwtService.java#L92-L106) et [`isTokenValid`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/JwtService.java#L108-L114) exigent `"ACCESS"`, tandis que [`isRefreshToken`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/JwtService.java#L116-L120) exige `"REFRESH"`. [`parseValidClaims`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/JwtService.java#L130-L145) contrôle signature HMAC, `subject` non blanc et expiration (`!expiration.before(new Date())`).
3. **Filtre REST** : [`JwtAuthenticationFilter.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/JwtAuthenticationFilter.java#L49-L92) appelle `jwtService.isAccessTokenValid(jwt)` *avant* `userDetailsService.loadUserByUsername`, puis rejette tout utilisateur introuvable ou inactif (`!entity.isActif()`) en HTTP `403`.
4. **Handshake WebSocket & `CONNECT` STOMP** : [`WebSocketAuthInterceptor.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/WebSocketAuthInterceptor.java#L42-L47) exige `isAccessTokenValid(token)` et `utilisateur.isActif()` sur `StompCommand.CONNECT`. Confirmé en exécution réelle par [`MessagerieStompIntegrationTest.stompConnect_WithRefreshToken_Rejected`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/domain/messagerie/MessagerieStompIntegrationTest.java#L334-L364).
5. **Renouvellement de session** : [`AuthService.rafraichir`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/utilisateur/service/AuthService.java#L111-L136) rejette tout access token ou refresh invalide (`401`) et tout compte désactivé (`403`).
6. **Compatibilité descendante** : un jeton antérieur dépourvu du claim `tokenType` (ou mal typé) échoue sur `isAccessTokenValid` et `isRefreshToken` — comportement sécuritaire souhaité impliquant une reconnexion unique au déploiement.

### 3.2 Réserves et faiblesses identifiées (Axe 1)
1. **Sessions STOMP déjà ouvertes immunisées contre la désactivation de compte (P2)** : [`WebSocketAuthInterceptor.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/WebSocketAuthInterceptor.java#L61-L95) ne revérifie pas `utilisateur.isActif()` ni l'expiration du token sur `SUBSCRIBE` (lignes 61-76) ou `SEND` (lignes 77-95), et [`MessagerieService.envoyerMessage`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/messagerie/service/MessagerieService.java#L103-L108) ne contrôle pas `expediteur.isActif()`. Un utilisateur suspendu conserve sa session STOMP active jusqu'à fermeture de la socket.
2. **Refresh tokens rejouables sans révocation serveur (P2)** : [`AuthService.rafraichir`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/utilisateur/service/AuthService.java#L111-L136) ne révoque pas l'ancien refresh token (pas de `jti`, pas de table/blacklist de rotation, aucun endpoint de révocation côté serveur dans [`AuthController.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/utilisateur/controller/AuthController.java#L57-L62)). Un refresh token dérobé reste rejouable pendant `604800000 ms` (7 jours).
3. **Absence de claims `iss` / `aud` et absence de rate-limiting sur `/api/v1/auth/rafraichir` (P2)** : `buildToken` (lignes 54-65) ne renseigne ni `issuer`, ni `audience`, ni `jti`, et [`AuthController.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/utilisateur/controller/AuthController.java#L57-L62) n'applique pas de limiteur de débit dédié au rafraîchissement.

---

## 4. AXE 2 — CONTRE-AUDIT DE LA MACHINE À ÉTATS DES ANNONCES (P0-2)

### 4.1 Matrice des transitions reconstituée sur le code réel

| Source → Cible | Non-Admin (`PUT /api/v1/annonces/{id}`) | Admin (`PUT /api/v1/admin/annonces/{id}/statut`) | Workflow Transactionnel (`TransactionService`) |
| :--- | :--- | :--- | :--- |
| `DISPONIBLE → DISPONIBLE` | Autorisé (no-op) | Autorisé (no-op, `false`) | `false` |
| `DISPONIBLE → SUSPENDUE` | **Refusé (`403`)** | **Autorisé si aucun `BLOQUE`** (`409` si `BLOQUE` ; clôture `INITIALISE`) | **Refusé (`409`)** |
| `SUSPENDUE → DISPONIBLE` | **Refusé (`403`)** | **Autorisé** | **Refusé (`409`)** |
| `DISPONIBLE ↔ RESERVEE` | **Refusé (`403`)** | **Refusé (`409`)** | **Autorisé** (init ↔ annulation/expiration/remboursement) |
| `DISPONIBLE / RESERVEE → LOUEE / VENDUE` | **Refusé (`403`)** | **Refusé (`409`)** | **Autorisé** (`signerBail` à `SIGNE_COMPLET` + `BLOQUE`) |
| `LOUEE / VENDUE / RESERVEE → DISPONIBLE / SUSPENDUE` | **Refusé (`403`)** | **Refusé (`409`)** | Uniquement `RESERVEE → DISPONIBLE` (annul./expir./remb.) ; `LOUEE`/`VENDUE` terminaux sauf `LOUEE/VENDUE` figé |
| `SUSPENDUE → RESERVEE / LOUEE / VENDUE` | **Refusé (`403`)** | **Refusé (`409`)** | **Refusé (`409`)** |

Les appels directs à `setStatutPublication` sont exclusivement centralisés dans [`AnnonceStatutStateMachine.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/service/AnnonceStatutStateMachine.java#L83) (ligne 83 et ligne 120).

### 4.2 Vecteurs de contournement, concurrence et lacunes UI détectés
1. **Vecteur critique P1 — Contournement et casse d'intégrité via `supprimerAnnonceLogique`** : [`AnnonceService.supprimerAnnonceLogique`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/service/AnnonceService.java#L223-L230) (appelé par `DELETE /api/v1/annonces/{id}` dans [`AnnonceController.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/controller/AnnonceController.java#L95-L96)) utilise `findByIdAndSupprimeFalse(id)` **sans verrou `FOR UPDATE`**, et marque `supprime = true` **sans vérifier si l'annonce a une transaction `BLOQUE`, `INITIALISE`, ni si son statut est `RESERVEE`, `LOUEE`, `VENDUE` ou `SUSPENDUE`**. Conséquence directe : un propriétaire peut supprimer logiquement un bien sous séquestre `BLOQUE`, ce qui provoque ensuite l'absence d'enregistrement lors du `findByIdAndSupprimeFalseForUpdate` dans [`TransactionService.lockAnnonceThenTransaction`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionService.java#L136) et déclenche un `409 Conflict` sur `signerBail` (ligne 706) tout en contournant le verrou `Annonce -> Transaction`.
2. **Repli non verrouillé dans `SignalementService.traiterSignalement` (P2)** : aux [lignes 78-79 de `SignalementService.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/avis/service/SignalementService.java#L78-L79), `annonceRepository.findByIdAndSupprimeFalseForUpdate(annonceId).orElse(signalement.getAnnonce())` bascule sans verrou pessimiste sur une annonce supprimée logiquement et modifie tout de même son statut.
3. **Verrouillage optimiste `@Version` incomplet côté HTTP (P2)** : [`AnnonceEntity.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/entity/AnnonceEntity.java#L115-L118) ajoute `@Version private Long version = 0L`, mais ni [`AnnonceResponseDTO.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/dto/AnnonceResponseDTO.java#L1-L63), ni [`AnnonceUpdateDTO.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/dto/AnnonceUpdateDTO.java#L1-L76), ni [`EditAnnonceModal.tsx`](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/src/components/dashboard/EditAnnonceModal.tsx#L104-L127) ne transmettent `version` ou `ETag`/`If-Match`. Les pertes de mise à jour sur formulaires HTTP simultanés restent possibles ; seul le verrou pessimiste DB protège la concurrence de services backend.
4. **Absence d'interface d'administration pour `PUT /api/v1/admin/annonces/{id}/statut` (P2)** : la recherche dans `frontend/src` confirme qu'aucune page admin n'appelle `/api/v1/admin/annonces/{id}/statut` (`AdminVerificationPage.tsx` n'utilise que `/en-attente-verification` et `/verifier`). Seule la suspension via traitement de signalement est accessible depuis l'UI.

---

## 5. AXE 3 — CONTRE-AUDIT DU CYCLE DE VIE TRANSACTIONNEL (P0-3)

### 5.1 Conformité nominale vérifiée
- **Expiration configurable** : [`TransactionService.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionService.java#L86) lit `ecourtier.transactions.initialise-expiration` (`PT24H` par défaut dans [`application.yml`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/resources/application.yml#L63-L64), délai fixe scheduler `60000 ms`). Ce délai `PT24H` ne figure pas dans le cahier des charges d'origine : il s'agit d'un choix d'architecture raisonnable.
- **Annulation explicite + Expiration paresseuse + Scheduler** : `POST /api/v1/transactions/{id}/annuler` ([`annulerTransaction`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionService.java#L434-L480)) clôture en `ANNULE` + `dossierActif = false`, expiration à la volée sur `initierTransaction` / `effectuerPaiementCaution` / `signerBail`, et lots scheduler [`expirerTransactionsExpirees`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionService.java#L488-L516) par tranches de `100` jusqu'à `10 000` dossiers par passage.
- **Ordre des verrous `Annonce -> Transaction`** : respecté de manière uniforme dans `initierTransaction`, `effectuerPaiementCaution`, `annulerTransaction`, `rembourserTransaction`, `signerBail`, `expirerTransactionIndividuelle`, `AnnonceService.changerStatutPublicationAdmin` et `SignalementService.traiterSignalement` (hors cas d'annonce supprimée logiquement).

### 5.2 Anomalies structurelles et réserves critiques (Axe 3)
1. **Intégration Wave 100 % mockée + appel PSP sous transaction SQL et verrous DB (P1 majeur)** :
   - Seul [`WaveClientMock.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/client/WaveClientMock.java#L151-L192) implémente `WaveClient` (`ConcurrentHashMap` en mémoire) ; il n'existe ni client HTTP Wave Sandbox, ni endpoint webhook de confirmation asynchrone, ni vérification HMAC, ni mécanisme de remboursement de rattrapage en cas de paiement tardif post-expiration.
   - Dans [`TransactionService.effectuerPaiementCaution`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionService.java#L378-L420), `waveClient.initiateEscrowCheckout(...)` et `verifyPaymentStatus(...)` sont invoqués **à l'intérieur de `@Transactional` tout en maintenant les verrous pessimistes `FOR UPDATE` sur `Annonce` et `Transaction`** (ligne 316). En production sur réseau réel : (a) la latence HTTP bloque toute concurrence sur l'annonce et la transaction ; (b) si le processus crashe après `initiateEscrowCheckout`, si le commit SQL échoue ou si `verifyPaymentStatus` rejette après débit effectif chez Wave, le rollback DB remet la transaction en `INITIALISE` sans `referenceWave` ni compensation financière, aboutissant ensuite à une expiration `EXPIRE` malgré les fonds encaissés par Wave.
2. **Signature du bail autorisée avant paiement + régression P1 lorsque `ADMIN` signe un bail `SIGNE_PROPRIETAIRE` (P1)** :
   - [`TransactionService.signerBail`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionService.java#L657-L764) autorise les signatures à `INITIALISE` (y compris dans [`TransactionDetailsModal.tsx`](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/src/components/transaction/TransactionDetailsModal.tsx#L545)), alors que `annulerTransaction` et `expirerTransactionIndividuelle` ne vérifient pas `statutBail` et clôturent librement un bail déjà `SIGNE_COMPLET`.
   - Aux [lignes 738-747 de `TransactionService.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionService.java#L738-L747), si un `ADMIN` appelle `signerBail` sur un dossier au statut `SIGNE_PROPRIETAIRE` (`dateSignatureLocataire == null` et `dateSignatureProprietaire != null`), le branchement vérifie d'abord `tx.getDateSignatureLocataire() == null` et force `tx.setStatutBail(StatutBail.SIGNE_LOCATAIRE)` (ligne 741) sans tester que `dateSignatureProprietaire` est déjà renseignée — **régression d'état** empêchant le passage à `SIGNE_COMPLET` et la libération des fonds.
3. **Incohérence d'horloges / fuseaux horaires entre entité et service (P2)** :
   - [`SchedulingConfig.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/config/SchedulingConfig.java#L15-L16) expose un bean `Clock` en `Africa/Dakar` (injecté via `@Autowired` dans [`TransactionService.setClock`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionService.java#L95-L100)), mais [`TransactionEntity.dateCreation`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/entity/TransactionEntity.java#L105) porte `@CreationTimestamp`, ce qui fait écraser `dateCreation(currentNow)` par l'horloge système JVM lors du persist Hibernate ! De même, [`AnnonceService.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/service/AnnonceService.java#L567) (lignes 567 et 614) utilise `LocalDateTime.now()` sans `Clock`, et toutes les colonnes Postgres sont en `TIMESTAMP WITHOUT TIME ZONE`. Un conteneur hébergé dans un fuseau différent d'`Africa/Dakar` introduit un décalage entre `date_creation` et `date_expiration`.
4. **Sémantique mixte de `dossierActif` et exécution multi-instance du scheduler (P2)** :
   - `ANNULE` et `EXPIRE` positionnent `dossierActif = false` et `dateCloture`, tandis que [`rembourserTransaction`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionService.java#L640-L642) laisse `dossierActif = true` malgré `REMBOURSE` et `dateCloture` ; côté UI, [`PropertyDetailPage.tsx`](file:///home/mahou-dev/Documents/Ecourtier2026/frontend/src/pages/PropertyDetailPage.tsx#L272-L279) filtre explicitement les statuts pour autoriser la réinitiation.
   - [`TransactionExpirationScheduler.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionExpirationScheduler.java#L17-L29) ne dispose ni de ShedLock ni de verrou consultatif PostgreSQL : l'intégrité reste assurée par `FOR UPDATE` ligne à ligne, au prix de contention/requêtes redondantes en déploiement multi-instances.

---

## 6. AXE 4 — CONTRE-AUDIT FLYWAY `V18` & POSTGRESQL 16 RÉEL

Toutes les vérifications ci-dessous ont été exécutées sur un conteneur éphémère isolé `ecourtier-pg16-audit` (`postgres:16-alpine` / PostgreSQL `16.15`) avec Flyway `10.20.1` et Hibernate ORM `6.6.8.Final`, puis nettoyé.

### 6.1 Base vierge `V1`–`V18` + validation JPA (`ddl-auto=validate`)
- Exécution directe contre PostgreSQL `16.15` de `FlywayFreshMigrationIntegrationTest` + `V14MigrationReconciliationTest` :
  - **`Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`** — `BUILD SUCCESS`.
- L'historique `flyway_schema_history` enregistre bien les rangs `1` à `18` en `success = t`.
- Contraintes et index vérifiés dans PostgreSQL (`pg_constraint`, `pg_indexes`, `information_schema.columns`) :
  - `transactions_statut_sequestre_check` : `CHECK (((statut_sequestre)::text = ANY ((ARRAY['INITIALISE', 'BLOQUE', 'LIBERE', 'REMBOURSE', 'ANNULE', 'EXPIRE'])::text[])))`.
  - `uq_transactions_annonce_active` : `CREATE UNIQUE INDEX uq_transactions_annonce_active ON public.transactions USING btree (annonce_id) WHERE (((statut_sequestre)::text = ANY ((ARRAY['INITIALISE'::character varying, 'BLOQUE'::character varying])::text[])) AND (dossier_actif = true))`.
  - `idx_transactions_initialise_expiration` : `CREATE INDEX idx_transactions_initialise_expiration ON public.transactions USING btree (date_expiration) WHERE (((statut_sequestre)::text = 'INITIALISE'::text) AND (dossier_actif = true))`.
  - `annonces.version` : `bigint`, `NOT NULL`, `DEFAULT 0`.

### 6.2 Upgrade incrémental `V17 → V18` sur base historique peuplée (preuve externe)
Scénario exécuté sur `ecourtier_legacy_v17_to_v18` : migration jusqu'à `V17`, insertion de `4` annonces (`DISPONIBLE`, `RESERVEE`, `LOUEE`, `SUSPENDUE`) et `6` transactions historiques (`INITIALISE` -48h active, `INITIALISE` -2h active, `INITIALISE` -72h à `dossier_actif = FALSE`, `BLOQUE`, `LIBERE`, `REMBOURSE`), puis migration Flyway vers `V18` :
- **Résultat** : migration réussie (`target=18`), toutes les données historiques intactes, `annonces.version = 0` sur les `4` annonces, `date_expiration - date_creation = 1 day` (`24h`) sur les lignes `INITIALISE`.
- **Observations de contre-audit** :
  1. Le backfill `V18` renseigne aussi `date_expiration` sur la ligne historique `INITIALISE` ayant `dossier_actif = FALSE` (`has_exp = t`), car l'`UPDATE` dans [`V18__p0_transaction_lifecycle_and_annonce_version.sql`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/resources/db/migration/V18__p0_transaction_lifecycle_and_annonce_version.sql) filtre `statut_sequestre = 'INITIALISE' AND date_expiration IS NULL` sans filtrer `dossier_actif = TRUE`. Cela reste sans effet applicatif grâce au filtre `AND t.dossierActif = true` dans [`TransactionRepository.findExpiredActiveInitialiseIds`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/repository/TransactionRepository.java#L36-L39).
  2. Une transaction `INITIALISE` vieille de `48h` adossée à une annonce `RESERVEE` n'est pas expirée à chaud pendant le SQL `V18` : sa libération dépend de l'exécution du scheduler après démarrage (`60000 ms`) ou d'un appel paresseux.

### 6.3 Atomicité du rollback DDL/DML en cas d'échec `V18` + verrou & unicité PG16
- **Rollback V18 (`ecourtier_rollback_test`)** : après migration `V1`–`V17`, exécution via Flyway d'une copie hors dépôt de `V18` échouant par `RAISE EXCEPTION` après les DDL/DML. PostgreSQL/Flyway a annulé l'ensemble de la transaction :
  - `annonces.version exists after rollback? false`
  - `transactions V18 cols existing after rollback: []`
  - `transactions_statut_sequestre_check` restauré exactement à `['INITIALISE', 'BLOQUE', 'LIBERE', 'REMBOURSE']`
  - `flyway_schema_history` maintenu proprement à la version `17` (`success = true`).
- **Preuves concurrence & index partiel PG16** :
  - Deux sessions JDBC concurrentes : `SELECT ... FOR UPDATE NOWAIT` sur une annonce déjà verrouillée `FOR UPDATE` rejette immédiatement avec `SQLState=55P03` (`lock_not_available`).
  - Coexistence vérifiée sur une même `annonce_id` d'une transaction `ANNULE`, d'une `EXPIRE` et d'une `INITIALISE` active ; l'insertion d'une seconde `INITIALISE` avec `dossier_actif = TRUE` sur cette annonce est rejetée par `uq_transactions_annonce_active` avec `SQLState=23505`.
- **Réserve méthodologique Axe 4** : le dépôt lui-même ne livrait pas de test automatisé couvrant l'upgrade `V17 → V18` sur base peuplée ni le rollback d'échec `V18` (seulement [`FlywayFreshMigrationIntegrationTest.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/common/config/FlywayFreshMigrationIntegrationTest.java#L56-L58) sur base vierge et [`V14MigrationReconciliationTest.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/domain/transaction/service/V14MigrationReconciliationTest.java#L542-L640) limité à `V14`).

---

## 7. AXE 5 — QUALITÉ ET AUTHENTICITÉ DES TESTS CITÉS

| Suite / Classe | Nb tests observé | Mode réel d'exécution | Valeur probante & Limites constatées |
| :--- | :--- | :--- | :--- |
| [`JwtServiceTest`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/common/security/JwtServiceTest.java#L1-L154) | `5` (0 échec) | Unitaire JUnit | Preuve solide sur la génération/validation cryptographique `ACCESS` vs `REFRESH`. |
| [`AuthIntegrationTest`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/domain/utilisateur/controller/AuthIntegrationTest.java#L1-L317) | `5` (0 échec) | MockMvc + H2 | Prouve rejet refresh token sur routes protégées REST et access token sur `/rafraichir`, ainsi que comptes désactivés sur REST. |
| [`MessagerieStompIntegrationTest`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/domain/messagerie/MessagerieStompIntegrationTest.java#L334-L364) | `4` (0 échec) | Serveur Tomcat réel + WebSocket STOMP + H2 | Véritable client STOMP sur port aléatoire : prouve le rejet d'un `REFRESH` token au `CONNECT`. Ne teste pas l'envoi de messages après désactivation d'une session déjà connectée. |
| [`AnnonceStatutStateMachineTest`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/domain/annonce/service/AnnonceStatutStateMachineTest.java#L17-L116) | `3` (0 échec) | Unitaire POJO | Exhaustif sur la matrice de la state machine pure, sans base ni sécurité Spring. |
| [`AnnonceStatutSecurityIntegrationTest`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/domain/annonce/controller/AnnonceStatutSecurityIntegrationTest.java#L1-L408) | `5` (0 échec) | MockMvc + H2 + `WaveClientMock` | Prouve les 403/409 REST et concurrence `AnnonceService`, mais ne teste ni `DELETE` (`supprimerAnnonceLogique`), ni perte de mise à jour HTTP GET→PUT, ni PostgreSQL réel. |
| [`AnnonceServiceTest`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/domain/annonce/service/AnnonceServiceTest.java#L150-L183) | `23` (0 échec) | Mockito | Tests unitaires mockés : `testSupprimerAnnonceLogique` (lignes 174-182) ne teste qu'un cas nominal sans transaction active ni verrou. |
| [`SignalementServiceTest`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/domain/avis/service/SignalementServiceTest.java#L35-L131) | `2` (0 échec) | Mockito + `@Spy AnnonceStatutStateMachine` | Mocke les repositories : ne couvre pas la collision réelle DB ni le fallback sur annonce supprimée. |
| [`TransactionLifecycleP03IntegrationTest`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/domain/transaction/controller/TransactionLifecycleP03IntegrationTest.java#L1-L607) | `4` (0 échec) | MockMvc + H2 (`MODE=PostgreSQL`) + `WaveClientMock` | Scénarios P0-3 complets (courses paiement/annulation et paiement/expiration, scheduler multi-thread), mais **sur H2 sans index partiel unique natif PG16 et avec PSP mocké en mémoire**. |
| [`WorkflowTransactionIntegrationTest`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/domain/transaction/controller/WorkflowTransactionIntegrationTest.java#L694-L759) | `15` (0 échec) | MockMvc + H2 + `WaveClientMock` | Bonne couverture workflow/idempotence, limitée à H2 et mock Wave. |
| [`TransactionServiceTest`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/domain/transaction/service/TransactionServiceTest.java#L374-L455) | `9` (0 échec) | Mockito | Contrôle fin des erreurs `WaveClient`, mais n'examine pas le crash post-checkout ni `ADMIN` signant `SIGNE_PROPRIETAIRE`. |
| [`FlywayFreshMigrationIntegrationTest`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/common/config/FlywayFreshMigrationIntegrationTest.java#L20-L58) & [`V14MigrationReconciliationTest`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/test/java/com/ecourtier/domain/transaction/service/V14MigrationReconciliationTest.java#L82-L110) | `1` + `6` (`7/7` en PG16) | Spring Boot + PG16 / JDBC PG16 (sinon skipped/H2 par défaut) | En `mvn test` standard sans variables DB, `FlywayFreshMigrationIntegrationTest` est ignoré (`Skipped: 1`) et `V14MigrationReconciliationTest` bascule sur H2. Note mineure sur la ligne 160 du rapport Phase 1 : la commande citée passe `-Dpg16.jdbc.url=...` / `-DDB_HOST=...` qui sont bien supportées (`System.getProperty` ligne 82 de `V14MigrationReconciliationTest`, et `${DB_HOST}`/`System.getProperty` dans `FlywayFreshMigrationIntegrationTest`), ce qui confirme la validité du mode PG16 lorsque le conteneur ciblé est actif. |
| Frontend E2E (`frontend/e2e/`) & Vitest | `0` test P0 | Playwright non exécuté sur P0 | **Aucun test E2E ou unitaire frontend** n'existe pour l'annulation de transaction, les badges `ANNULE`/`EXPIRE`, le blocage `SUSPENDUE` ou `/rafraichir`. |

---

## 8. AXE 6 — NON-RÉGRESSION TRANSVERSE

1. **Suite Maven complète exécutée indépendamment** (`mvn -f backend/pom.xml -o test`) :
   - **`Tests run: 290, Failures: 0, Errors: 0, Skipped: 1`** — `BUILD SUCCESS` (`01:16 min`).
   - L'unique test ignoré en mode défaut est `FlywayFreshMigrationIntegrationTest`, validé indépendamment à `1/1` (`7/7` avec `V14MigrationReconciliationTest`) sur PostgreSQL `16.15`.
2. **Couverture JaCoCo mesurée (`backend/target/site/jacoco/jacoco.xml`, 107 classes)** :
   - Instructions : **`85,27 %`** (`16 430` couvertes / `19 268`, soit `2 838` manquées) ;
   - Branches : **`61,85 %`** (`1 579` couvertes / `2 553`, soit `974` manquées) ;
   - Lignes : **`88,68 %`** (`3 461` couvertes / `3 903`, soit `442` manquées) ;
   - Méthodes : **`83,96 %`** (`450` / `536`).
3. **Compilation et build Frontend (`frontend/`)** :
   - `./frontend/node_modules/.bin/tsc -p frontend/tsconfig.json --noEmit` + `npm --prefix frontend run build` : **exit code 0**, build Vite + PWA (`61 entries`, `1048.64 KiB`) généré en `6.93 s`.
   - `npm --prefix frontend run lint` : **échec outillage (`exit 127`, `eslint: commande introuvable`)** — aucune vérification ESLint exécutable dans l'environnement actuel.
4. **Dérive du contrat OpenAPI (Anomalie P2 avérée)** :
   - Vérification directe sur [`docs/openapi.json`](file:///home/mahou-dev/Documents/Ecourtier2026/docs/openapi.json#L3238-L3246) et [`backend/src/main/resources/static/openapi.json`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/resources/static/openapi.json#L222) :
     - Aucun des deux fichiers ne déclare `POST /api/v1/transactions/{id}/annuler` ni `PUT /api/v1/admin/annonces/{id}/statut`.
     - Le schéma `TransactionResponseDTO.statutSequestre` reste limité à `["INITIALISE", "BLOQUE", "LIBERE", "REMBOURSE"]` (les valeurs `ANNULE`, `EXPIRE`, ainsi que les champs `dossierActif`, `dateExpiration`, `dateCloture`, `motifCloture` sont absents).
5. **Requêtes statistiques administrateur (`AdminStatistiquesRepository`)** :
   - Vérification sur [`AdminStatistiquesRepository.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/admin/repository/AdminStatistiquesRepository.java#L51-L57) : les indicateurs de volume séquestre et commission filtrent strictement sur `BLOQUE` et `LIBERE` — les statuts `ANNULE` et `EXPIRE` ne polluent donc pas les KPI financiers.

---

## 9. INVENTAIRE DES RÉSERVES ET ANOMALIES RÉSIDUELLES

| ID | Gravité | Axe | Localisation exacte | Description factuelle |
| :--- | :--- | :--- | :--- | :--- |
| **RES-01** | **P1** | Axe 2 | [`AnnonceService.java#L223-L230`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/service/AnnonceService.java#L223-L230) | `supprimerAnnonceLogique` (`DELETE /api/v1/annonces/{id}`) ne prend pas de verrou `FOR UPDATE` et ne bloque pas la suppression sur annonce ayant une transaction `BLOQUE`/`INITIALISE` ou un statut `RESERVEE`/`LOUEE`/`VENDUE`/`SUSPENDUE`, cassant ensuite `lockAnnonceThenTransaction` / `signerBail`. |
| **RES-02** | **P1** | Axe 3 | [`TransactionService.java#L378-L420`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionService.java#L378-L420), [`WaveClientMock.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/client/WaveClientMock.java#L151-L192) | Intégration Wave uniquement mockée (zéro client HTTP Sandbox, zéro webhook/HMAC) et appel synchrone PSP à l'intérieur de `@Transactional` sous verrous pessimistes `Annonce -> Transaction` sans réconciliation ni remboursement compensatoire en cas de crash post-débit ou paiement tardif. |
| **RES-03** | **P1** | Axe 3 | [`TransactionService.java#L657-L747`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/service/TransactionService.java#L657-L747) | Double faille métier sur le bail : (a) signature autorisée alors que `statutSequestre == INITIALISE` puis annulable/expirable sans contrôle de `statutBail` ; (b) si `ADMIN` appelle `signerBail` à `SIGNE_PROPRIETAIRE`, la condition `getDateSignatureLocataire() == null` (ligne 741) fait **régresser** `statutBail` à `SIGNE_LOCATAIRE` au lieu de conclure `SIGNE_COMPLET`. |
| **RES-04** | **P2** | Axe 1 | [`WebSocketAuthInterceptor.java#L61-L95`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/WebSocketAuthInterceptor.java#L61-L95), [`MessagerieService.java#L103-L108`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/messagerie/service/MessagerieService.java#L103-L108) | Aucune revérification de `actif` ou d'expiration sur les frames `SEND`/`SUBSCRIBE` après le `CONNECT` initial. |
| **RES-05** | **P2** | Axe 1 | [`JwtService.java#L54-L65`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/common/security/JwtService.java#L54-L65), [`AuthService.java#L111-L136`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/utilisateur/service/AuthService.java#L111-L136) | Refresh tokens rejouables jusqu'à 7 jours sans `jti` ni révocation au logout/renouvellement ; absence de `iss`/`aud` et absence de rate-limiter sur `/rafraichir`. |
| **RES-06** | **P2** | Axe 2 | [`AnnonceResponseDTO.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/dto/AnnonceResponseDTO.java#L1-L63), [`AnnonceUpdateDTO.java`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/dto/AnnonceUpdateDTO.java#L1-L76), [`SignalementService.java#L78-L79`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/avis/service/SignalementService.java#L78-L79) | `@Version` absent des contrats HTTP/DTO (écrans d'édition exposés au lost update HTTP) ; fallback non verrouillé `.orElse(signalement.getAnnonce())` sur annonce supprimée ; absence d'UI admin pour `PUT /api/v1/admin/annonces/{id}/statut`. |
| **RES-07** | **P2** | Axe 3/4 | [`TransactionEntity.java#L105`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/transaction/entity/TransactionEntity.java#L105), [`AnnonceService.java#L567-L614`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/java/com/ecourtier/domain/annonce/service/AnnonceService.java#L567-L614), [`V18`](file:///home/mahou-dev/Documents/Ecourtier2026/backend/src/main/resources/db/migration/V18__p0_transaction_lifecycle_and_annonce_version.sql) | `@CreationTimestamp` ignore le bean `Clock` (`Africa/Dakar`) au profit de l'horloge JVM ; `AnnonceService` utilise `LocalDateTime.now()` ; `REMBOURSE` conserve `dossierActif=true` ; `V18` backfille `date_expiration` même sur les anciens `INITIALISE` inactifs ; scheduler dépourvu de verrou distribué. |
| **RES-08** | **P2** | Axe 5/6 | [`docs/openapi.json`](file:///home/mahou-dev/Documents/Ecourtier2026/docs/openapi.json#L3238-L3246), `backend/src/main/resources/static/openapi.json`, `frontend/e2e/` | Spécifications OpenAPI statiques non régénérées ; zéro test E2E/unitaire frontend sur les flux P0 ; tests d'intégration P0 exécutés en H2 par défaut (aucun test PG16 d'upgrade `V17→V18` livré dans le dépôt) ; `eslint` indisponible. |

---

## 10. RECOMMANDATIONS PRIORISÉES (PHASE 2 — POUR INFORMATION)

1. **Priorité P1 (Intégrité transactionnelle & métier)** :
   - Verrouiller `Annonce -> Transaction` dans `AnnonceService.supprimerAnnonceLogique` et rejeter en `409 Conflict` toute suppression sur annonce `RESERVEE`/`LOUEE`/`VENDUE`/`SUSPENDUE` ou porteuse d'une transaction `INITIALISE`/`BLOQUE` active.
   - Interdire `signerBail` tant que `statutSequestre != BLOQUE` (ou interdire l'annulation/expiration lorsque `statutBail != NON_SIGNE`), et corriger le branchement `ADMIN` dans `signerBail` afin qu'il tienne compte de `statutBail == SIGNE_PROPRIETAIRE` / `SIGNE_LOCATAIRE`.
   - Sortir tout appel HTTP PSP Wave réel de la transaction SQL (pattern *Outbox / PENDING_PAYMENT* + callback webhook signé HMAC + tâche de réconciliation).
2. **Priorité P2 (Sécurité session, horloges & contrats API)** :
   - Vérifier `utilisateur.isActif()` sur les frames STOMP `SEND` (ou dans `MessagerieService.envoyerMessage`) et introduire une rotation révocable des refresh tokens (`jti` stocké + révocation au logout/refresh + rate-limit sur `/rafraichir`).
   - Exposer `version` dans `AnnonceResponseDTO` / `AnnonceUpdateDTO` (`EditAnnonceModal.tsx`) et brancher `PUT /api/v1/admin/annonces/{id}/statut` dans l'interface d'administration.
   - Remplacer `@CreationTimestamp` sur `TransactionEntity` par une attribution explicite contrôlée par `Clock` (`Africa/Dakar`), généraliser `Clock` à `AnnonceService`, et aligner `dossierActif` sur `REMBOURSE` / `LIBERE`.
   - Régénérer `docs/openapi.json` et `backend/src/main/resources/static/openapi.json`, ajouter des tests Playwright P0 et une suite automatisée de migration incrémentale PostgreSQL 16.

---

## 11. ATTESTATION D'INTÉGRITÉ DE L'ENVIRONNEMENT

- Aucun fichier source, fichier de test ou script Flyway n'a été créé, modifié ou supprimé par ce contre-audit (`git status` et `git diff` inchangés sur `backend/`, `frontend/`, `docs/`, hors création du présent rapport).
- Le conteneur éphémère `ecourtier-pg16-audit` a été arrêté et supprimé (`docker rm -f ecourtier-pg16-audit`), et les bases existantes (`ecourtier-db`, `ecourtier-postgres`) sont demeurées intactes.
