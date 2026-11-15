# RAPPORT DE VALIDATION — LOT 1 (CONTRE-AUDIT PHASE 3.1 : CA3-001 À CA3-004)

**Projet :** E-Courtier Sénégal (Licence 3 Ingénierie Informatique)  
**Date du contrôle :** 15 novembre 2026  
**Périmètre :** Anomalies critiques `CA3-001`, `CA3-002`, `CA3-003`, `CA3-004` + synchronisation du contrat OpenAPI  
**Statut Wave :** Hors périmètre strict — aucune modification, aucun appel ni réactivation effectué

---

## 1. Synthèse exécutive et verdict du Lot 1

| Identifiant | Intitulé | Verdict | Criticité résiduelle |
| :--- | :--- | :--- | :--- |
| **CA3-001** | Révocation des Access Tokens et des sessions WebSocket après rejeu d'un Refresh Token | **VALIDÉ** | P3 mineure (reconnexion auto STOMP côté client traitée en Lot 2 `CA3-009`) |
| **CA3-002** | Protection contre le déni de service / déconnexion forcée par ancien ou faux Refresh Token | **VALIDÉ** | Aucune |
| **CA3-003** | Synchronisation temps réel de l'état des transactions dans `TransactionDetailsModal` | **VALIDÉ** | Aucune |
| **CA3-004** | Suspension (`DISPONIBLE -> SUSPENDUE`) et réactivation (`SUSPENDUE -> DISPONIBLE`) administratives des annonces | **VALIDÉ** | P3 mineure (pagination UI limitée à `size=50` sur l'onglet « Toutes les annonces ») |

**Verdict global Lot 1 : VALIDÉ.**  
Aucune anomalie P0/P1 n'a été relevée dans le périmètre `CA3-001`..`CA3-004`. Tous les tests backend ciblés (27/27), la compilation TypeScript/ESLint/Vite et les deux suites E2E Playwright (4/4 mockées + 6/6 sur backend réel isolé H2) ont réussi sans régression.

---

## 2. Diagnostic, analyse de code et contre-audit hostile par anomalie

### 2.1 CA3-001 — Révocation des Access Tokens et sessions WebSocket lors d'un rejeu de Refresh Token

- **Chaîne examinée :**
  - `backend/src/main/java/com/ecourtier/domain/utilisateur/service/AuthService.java` (`rafraichir`, L188-246)
  - `backend/src/main/java/com/ecourtier/common/security/JwtAuthenticationFilter.java` (L77-88)
  - `backend/src/main/java/com/ecourtier/domain/utilisateur/repository/RefreshTokenRepository.java`
  - `backend/src/main/java/com/ecourtier/common/security/WebSocketSessionRegistry.java` et `WebSocketAuthInterceptor.java`
- **Mécanisme vérifié :**
  - `AuthService.rafraichir` est annoté `@Transactional(noRollbackFor = ResponseStatusException.class)` afin que la révocation en base reste persistée lorsqu'une `401 Unauthorized` est levée.
  - Le jeton est chargé sous verrou pessimiste (`findByJtiForUpdate(jti)`).
  - En cas de rejeu (`stored.getUsedAt() != null`), `revokeFamily(familyId, now, REVOCATION_REUSE_DETECTED)` révoque l'ensemble de la famille compromise et `disconnectFamilyWsAfterCommit(familyId)` programme la fermeture immédiate des sessions STOMP associées à `familyId` via `TransactionSynchronizationManager.registerSynchronization(... afterCommit())`.
  - À chaque requête REST, `JwtAuthenticationFilter` extrait `familyId`, vérifie `refreshTokenRepository.isFamilyActive(familyId, entity.getId(), now)` en plus de `tv` (`tokenVersion`) et `entity.isActif()`.
- **Contre-audit hostile :**
  - *Isolation multi-appareils :* le rejeu sur l'appareil A (`familyId_A`) révoque uniquement les jetons et WebSockets de `familyId_A` ; l'appareil B (`familyId_B`) conserve son Access Token, son Refresh Token et sa session STOMP active.
  - *Cas limite vérifié :* une tentative ultérieure avec un jeton déjà révoqué (`stored.getRevokedAt() != null`) renvoie `401` sans révoquer les autres appareils.
  - *Point à durcir en Lot 2 (`CA3-009`) :* lorsque le serveur ferme la session STOMP après révocation, le hook frontend `useConversationSocket.ts` tente encore des reconnexions périodiques tant que le client n'a pas rafraîchi ou vidé son état auth.

### 2.2 CA3-002 — Protection contre la déconnexion forcée par token invalide

- **Chaîne examinée :**
  - `AuthController.java` (`POST /api/v1/auth/deconnexion`) et `AuthService.java` (`deconnecter`, L259-312)
- **Mécanisme vérifié :**
  - Tout refresh token inconnu, expiré, déjà consommé (`usedAt != null`) ou déjà révoqué (`revokedAt != null`) lève immédiatement `401 UNAUTHORIZED` sans modifier `token_version` ni révoquer la famille d'un autre appareil.
  - Si un utilisateur authentifié soumet un refresh token appartenant à un autre utilisateur, `deconnecter` rejette en `403 FORBIDDEN`.
  - Un appel anonyme sans refresh token échoue en `401 UNAUTHORIZED`.
- **Contre-audit hostile :**
  - Aucun vecteur DoS permettant à un attaquant possédant un vieux jeton intercepté ou forgé de déconnecter une session légitime active (REST ou STOMP).

### 2.3 CA3-003 — Synchronisation de l'état des transactions dans l'interface

- **Chaîne examinée :**
  - `frontend/src/components/transaction/TransactionDetailsModal.tsx` (L58-79, L122-220)
  - `frontend/src/pages/DashboardPage.tsx` (L672-687)
- **Mécanisme vérifié :**
  - `TransactionDetailsModal` maintient un état `overrideTx` fusionné via `useMemo` (`{ ...transactionProp, ...overrideTx }`) dès que `overrideTx.id === transactionProp.id`.
  - Après paiement de caution (`handlePaiementCaution`), annulation (`handleAnnulerTransaction`), signature (`handleSignerBail`) ou régénération PDF (`handleRegeneratePdf`), `syncTransactionState` met à jour `overrideTx`, patche le cache TanStack Query `['my-transactions']` via `setQueryData`, invalide les requêtes liées et déclenche `onRefresh()`.
  - `DashboardPage.tsx` réconcilie dynamiquement `selectedTransaction` contre `transactionsList.find(...)`.
- **Contre-audit hostile :**
  - Le bouton « Signer le bail électroniquement » reste bloqué en `INITIALISE` et s'active immédiatement après passage en `BLOQUE` dans la même instance ouverte de la modale, sans fermeture ni rechargement manuel.

### 2.4 CA3-004 — Suspension et réactivation des annonces par l'administrateur

- **Chaîne examinée :**
  - `AdminAnnonceController.java` (`PUT /api/v1/admin/annonces/{id}/statut`, `@PreAuthorize("hasRole('ADMIN')")`)
  - `AnnonceStatutUpdateDTO.java`, `AnnonceService.java` (`changerStatutPublicationAdmin`, L664-722), `AnnonceStatutStateMachine.java`
  - `frontend/src/services/admin.service.ts`, `frontend/src/pages/AdminVerificationPage.tsx` (L189-250)
- **Mécanisme vérifié :**
  - Seul un compte `ADMIN` est autorisé (`403 Forbidden` pour `PROPRIETAIRE`/`LOCATAIRE`/`AGENT`).
  - Seules les transitions `DISPONIBLE -> SUSPENDUE` et `SUSPENDUE -> DISPONIBLE` sont acceptées, avec contrôle de version optimiste (`version` -> `409 Conflict` en cas de concurrence/version obsolète) et blocage `409` en cas de transaction `BLOQUE` active ou transition illégale (`DISPONIBLE -> LOUEE`).
  - Côté UI (`AdminVerificationPage.tsx`), `updateAnnonceInLists` met à jour et invalide les caches TanStack Query et affiche toute erreur `409`/`403` dans un bloc `role="alert"`.
- **Observation mineure (P3) :** dans l'onglet « Toutes les annonces », la requête utilise une taille fixe (`size=50`) sans sélecteur de page (`CA3-016`, à adresser ou documenter en Lot 3).

### 2.5 Contrat OpenAPI synchronisé

- Vérification SHA-256 : `docs/openapi.json` et `backend/src/main/resources/static/openapi.json` sont strictement identiques (`287e3324fe0461bc85c8b7042576ac305fdd8d9ab37a6b95f4eaffe6113de7a6`).
- `OpenApiContractIntegrationTest` valide l'exposition des routes `/api/v1/auth/deconnexion`, `/api/v1/auth/rafraichir`, `/api/v1/annonces/{id}` et des schémas associés.

---

## 3. Preuves d'exécution des tests (après inspection, sans modification)

### 3.1 Tests backend ciblés (Maven, profil `test` H2)
Commande exécutée :
`mvn -f backend/pom.xml test -Dtest=AuthAccessTokenRevocationIntegrationTest,AuthServiceTest,WebSocketSessionSecurityIntegrationTest,AnnonceOptimisticLockIntegrationTest,OpenApiContractIntegrationTest`

| Classe de test | Exécutés | Réussis | Échecs | Erreurs | Ignorés |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `OpenApiContractIntegrationTest` | 1 | 1 | 0 | 0 | 0 |
| `WebSocketSessionSecurityIntegrationTest` | 4 | 4 | 0 | 0 | 0 |
| `AnnonceOptimisticLockIntegrationTest` | 4 | 4 | 0 | 0 | 0 |
| `AuthAccessTokenRevocationIntegrationTest` | 5 | 5 | 0 | 0 | 0 |
| `AuthServiceTest` | 13 | 13 | 0 | 0 | 0 |
| **Total ciblé Lot 1** | **27** | **27** | **0** | **0** | **0** |

### 3.2 Contrôles frontend et E2E Playwright
- `npm --prefix frontend run lint` (`eslint . --max-warnings 0`) : **PASS (0 erreur, 0 avertissement)**
- `./frontend/node_modules/.bin/tsc -p frontend/tsconfig.json --noEmit` : **PASS**
- `npm --prefix frontend run build` (`vite build`) : **PASS** (PWA `61 entries`, `dist/` généré)
- `npx playwright test e2e/phase2-security-ui.spec.ts` (API mockée sur preview isolé `127.0.0.1:4173`) : **4/4 PASSED (13.3s)**
- `npx playwright test e2e/phase3-real-backend.spec.ts` (backend Spring Boot isolé H2 `spring-boot:test-run` sur `127.0.0.1:18080` avec `ECOURTIER_CORS_ALLOWED_ORIGINS` + preview `127.0.0.1:4173`, sans toucher aux conteneurs Docker 5432/5435/8080/3000) : **6/6 PASSED (38.8s)**

---

## 4. Intégration CI prévue (Lot 4)

Les 5 classes de tests backend ci-dessus (`mvn test`), le lint/typecheck/build frontend et les deux suites Playwright (`phase2-security-ui.spec.ts` et `phase3-real-backend.spec.ts`) seront intégrés automatiquement dans les jobs backend et E2E du pipeline CI GitHub Actions mis en place au Lot 4.
