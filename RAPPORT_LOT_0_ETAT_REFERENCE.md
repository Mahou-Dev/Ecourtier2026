# RAPPORT LOT 0 — ÉTAT DE RÉFÉRENCE INITIAL DU DÉPÔT E-COURTIER SÉNÉGAL

- **Date de relevé :** 15 novembre 2026
- **Posture :** Lecture seule stricte — **aucune modification** effectuée sur le code source, les configurations, les worktrees, les bases de données ou l'historique Git.
- **Périmètre :** Cartographie complète du dépôt, chaîne d'outils, configurations PostgreSQL/Flyway/Docker/Render, inventaire CI/CD, travaux antérieurs et risques de configuration initiaux (sans exposition de secrets).

---

## 1. Identification Git, Fournisseur et Worktrees (Section 3)

| Paramètre | Valeur relevée sur pièces |
| :--- | :--- |
| **Chemin réel du dépôt principal** | `/home/mahou-dev/Documents/Ecourtier2026` |
| **Fournisseur Git distant** | **GitHub** (`origin : git@github.com:Mahou-Dev/Ecourtier2026.git`) |
| **Branche active** | `main` (`## main...origin/main [devant 6]`) |
| **Commit `HEAD`** | `f2bf8a97af5c9fc43c40d25a0ed9eeacfb25f317` |
| **Plateforme CI/CD existante** | **AUCUNE** (`.github/`, `.gitlab*`, `Jenkinsfile*`, `.circleci*`, `azure-pipelines*` absents) |
| **Modifications locales initiales** | `130` fichiers suivis modifiés (`M`), `72` fichiers/répertoires non suivis (`??`), `0` conflit (`U`) |
| **Fichiers `.env` suivis dans Git** | `0` (protégés par `.gitignore` lignes 9–14 ; seuls `.env.example` et `frontend/.env.example` sont suivis) |

### Cartographie des 6 Worktrees Git connus
| # | Chemin du Worktree | Branche / État | Commit `HEAD` | État Git |
| :-: | :--- | :--- | :--- | :--- |
| 1 | `/home/mahou-dev/Documents/Ecourtier2026` | `refs/heads/main` | `f2bf8a97af5c9fc43c40d25a0ed9eeacfb25f317` | Principal actif |
| 2 | `/home/mahou-dev/Documents/Ecourtier2026-convergence` | `refs/heads/integration/phase-9d-convergence` | `66038410eaa02ec19bf7058d2a194f0eb342210e` | Enregistré (`prunable`) |
| 3 | `/home/mahou-dev/Documents/Ecourtier2026-lot1` | `(detached HEAD)` | `d161762406ce5f0c685441d13f6e91bc5602338a` | Enregistré (`prunable`) |
| 4 | `/home/mahou-dev/Documents/Ecourtier2026-lot2` | `(detached HEAD)` | `d161762406ce5f0c685441d13f6e91bc5602338a` | Enregistré (`prunable`) |
| 5 | `/home/mahou-dev/Documents/Ecourtier2026-lot3` | `(detached HEAD)` | `d161762406ce5f0c685441d13f6e91bc5602338a` | Enregistré (`prunable`) |
| 6 | `/home/mahou-dev/Documents/Ecourtier2026-validation` | `(detached HEAD)` | `d161762406ce5f0c685441d13f6e91bc5602338a` | Enregistré (`prunable`) |

---

## 2. Environnement et Chaîne d'Outils Locale Relevée

- **Java :** OpenJDK `21.0.4` LTS (Eclipse Adoptium / Temurin)
- **Build Backend :** Apache Maven `3.9.11`
- **Node / npm :** Node.js `v26.12.0`, npm `11.20.0`
- **Conteneurisation :** Docker `29.9.2`, Docker Compose `v5.6.1`
- **Base de données & Utilitaires :** `psql`, `pg_isready`, `jq`, `python3` présents ; `plantuml`, `dot`, `gh` absents du `$PATH`.
- **Conteneurs locaux actifs observés :** `ecourtier-backend` (`8080`), `ecourtier-frontend` (`3000`), `ecourtier-db` (`postgres:16-alpine`, `5435`), `ecourtier-postgres` (`postgres:16-alpine`, `5432`) — **intouchables durant toute la mission**.

---

## 3. Cartographie Architecture, Composants et Dépendances

### 3.1 Backend (`backend/pom.xml`)
- **Socle :** Spring Boot `3.4.3`, Java `21`
- **Dépendances majeures :** `spring-boot-starter-web`, `spring-boot-starter-websocket` (STOMP), `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-validation`, `postgresql`, `flyway-core` + `flyway-database-postgresql`, `jjwt` `0.12.6`, `springdoc-openapi-starter-webmvc-ui` `2.7.0`, `openpdf` `2.0.3`, `lombok`.
- **Outils de test et qualité :** `spring-boot-starter-test`, `spring-security-test`, `h2` (`test`), `testcontainers` `1.20.2` (`postgresql`, `jdbc`, `junit-jupiter`), `jacoco-maven-plugin` `0.8.12`, `maven-surefire-plugin`.
- **Domaines métier (`backend/src/main/java/com/ecourtier/`) :**
  - `common/` : `config`, `controller` (`UploadController`, `AdminSeedController`), `exception` (`GlobalExceptionHandler`), `health`, `scheduler` (`SchedulerLockEntity`, `SchedulerLockService`), `security` (`JwtService`, `JwtAuthenticationFilter`, `WebSocketAuthInterceptor`, `WebSocketSessionRegistry`, `BoundedRateLimiterSupport`, `LoginRateLimiterService`, `RefreshRateLimiterService`, `*SecurityService`), `service` (`FileStorageService`, `DemoDataSeederService`).
  - `domain/admin/` : `AdminStatistiquesController/Service/Repository/DTO`.
  - `domain/annonce/` : CRUD annonces, `AnnonceSpecification`, `DocumentVerificationEntity`, `PhotoAnnonceEntity`, `AnnonceStatutStateMachine`, `AdminAnnonceController`.
  - `domain/avis/` : `AvisController/Service`, `SignalementController/Service`, `UtilisateurController`.
  - `domain/favori/` : `FavoriController/Service/Repository/Entity`.
  - `domain/ia/` : `IAController`, `IAService`, `LlmExtractionService`, `RuleBasedCriteriaExtractor`, `GroqClientService`, `IaRateLimiterService`.
  - `domain/messagerie/` : `MessagerieRestController`, `MessagerieStompController`, `MessagerieService`.
  - `domain/reference/` : `ReferenceController`.
  - `domain/rendezvous/` : `RendezVousController/Service`, `NotificationSmsController/Service`, `SmsGatewayLogImpl` (simulation).
  - `domain/transaction/` : `TransactionController/Service`, `PdfBailGeneratorService`, `BailPdfRecoveryScheduler`, `TransactionExpirationScheduler`.
  - `domain/transaction/*Wave*` *(**EXCLUSION TOTALE DE WAVE — PÉRIMÈTRE GELÉ**)* : `WaveWebhookController`, `WaveWebhookService`, `WavePaymentReconciliationService`, `PaiementWaveEntity/Repository`.
  - `domain/utilisateur/` : `AuthController/Service`, `AdminUtilisateurController`, `UtilisateurCompteController`, `UtilisateurManagementService`, `RefreshTokenEntity/Repository`.

### 3.2 Frontend (`frontend/package.json`)
- **Socle :** React `^18.3.1`, TypeScript `^5.7.3`, Vite `^6.1.0`, Tailwind CSS `^3.4.17`
- **Bibliothèques clés :** `@tanstack/react-query` `^5.66.0`, `react-router-dom` `^7.1.5`, `zustand` `^5.0.3`, `@stomp/stompjs` `^7.3.0`, `leaflet` / `react-leaflet` `^4.2.1`, `recharts` `^3.10.1`, `framer-motion` `^12.4.7`, `zod` `^3.24.2`, `vite-plugin-pwa` `^1.3.0`.
- **Qualité / Tests :** ESLint `^10.15.0` (`eslint.config.js`), `@playwright/test` `^1.64.1` (`playwright.config.ts`, `frontend/e2e/`). **Aucun framework de tests unitaires/composants React (Vitest/Jest) n'est installé dans `frontend/package.json`.**

### 3.3 Base de données & Flyway
- **20 migrations SQL** ordonnées de `V1__init_schema.sql` à `V20__phase3_security_pdf_and_scheduler_locks.sql` sous `backend/src/main/resources/db/migration/`.
- `application.yml` : `spring.jpa.hibernate.ddl-auto: validate`, `jdbc.time_zone: UTC`, `flyway.enabled: true`.

---

## 4. Rapports des Phases Antérieures Examinés

| Fichier préexistant | Rôle / Bilan |
| :--- | :--- |
| `RAPPORT_AUDIT_GLOBAL_CONFORMITE_ECOURTIER.md` | Audit global initial |
| `RAPPORT_PHASE_1_REMEDIATION_P0_ECOURTIER.md` / `RAPPORT_CONTRE_AUDIT_PHASE_1_P0_ECOURTIER.md` | Remédiation P0 (lifecycle transaction, `@Version` annonces, etc.) |
| `RAPPORT_PHASE_2_SECURISATION_ECOURTIER.md` / `RAPPORT_CONTRE_AUDIT_PHASE_2_ECOURTIER.md` | Sécurisation JWT refresh tokens, rate limiting, WS STOMP |
| `RAPPORT_PHASE_3_SECURISATION_HORS_WAVE_ECOURTIER.md` / `RAPPORT_CONTRE_AUDIT_PHASE_3_ECOURTIER.md` | Phase 3 hors Wave — Contre-audit : **NON VALIDÉE (78/100)** avec 4 P1 (`CA3-001` à `CA3-004`), 9 P2 (`CA3-005` à `CA3-013`), 4 P3 (`CA3-014` à `CA3-017`) |
| `RAPPORT_PHASE_3_1_LOT_1_CORRECTIONS_P1_ECOURTIER.md` | Corrections appliquées pour `CA3-001` à `CA3-004` (à contre-auditer en Lot 1) |
| `RAPPORT_CHATBOT_GROQ_5.md` / `RAPPORT_CHATBOT_GROQ_7.md` | Audits chatbot IA Groq |
| `docs/audit-conformite-cahier.md`, `docs/rapport-tests-integration.md`, `docs/openapi.json`, `docs/E-Courtier.postman_collection.json` | Documentation existante |

---

## 5. Configurations Dangereuses ou Fragiles Identifiées à l'État Initial

*(Aucun secret réel n'est affiché.)*

1. **`backend/src/main/resources/application.yml` (ligne 5) :** `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}` bascule silencieusement en profil `dev` si la variable est absente en production, désactivant `ProductionConfigurationValidator` et exposant les logs SQL et secrets de dev.
2. **`backend/src/main/resources/application-dev.yml` (lignes 5, 30) :** valeurs fallback en clair (`SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET` 64 hex) qui, selon le contre-audit Phase 3 (`CA3-007`), ne sont pas bloquées par `KNOWN_INSECURE_SECRETS`.
3. **`backend/src/main/resources/application-prod.yml` (lignes 20–21) :** `forward-headers-strategy: native` sans restriction `internal-proxies` (`CA3-006`).
4. **`docker-compose.yml` (lignes 9, 23, 27, 32, 34, 48–60) :**
   - Valeurs par défaut de mots de passe (`POSTGRES_PASSWORD`, `ADMIN_BOOTSTRAP_PASSWORD`).
   - Service `backend` compilé via `Dockerfile.dev` (`mvn spring-boot:run`).
   - Service `frontend` utilisant `frontend/Dockerfile` (`node:20-alpine`, `npm install`, `npm run dev`), inapte à une livraison immuable de production.
5. **Absence de `.dockerignore` (`backend/.dockerignore`, `frontend/.dockerignore`) :** `frontend/Dockerfile` exécute `COPY . .`, exposant le contexte de build à embarquer `node_modules` ou d'éventuels fichiers locaux `.env` s'ils étaient créés dans `frontend/`.
6. **Absence de `HEALTHCHECK` dans `backend/Dockerfile` et `frontend/Dockerfile`.**
7. **`render.yaml` :** définit `IA_API_KEY` (ligne 37) mais pas `GROQ_API_KEY` (alors que le fournisseur par défaut dans `application.yml:54` est `groq` qui lit `GROQ_API_KEY` ou `IA_API_KEY` — à vérifier au Lot 2/4), et utilise `plan: free`.
8. **`backend/src/test/java/com/ecourtier/common/config/FlywayFreshMigrationIntegrationTest.java` :** exécute `flyway.clean()` sur `localhost:5432` par défaut et attend la version `19` au lieu de `20` (`CA3-011`).
9. **Absence totale de pipeline CI/CD versionnée** dans un dépôt configuré sur GitHub (`origin git@github.com:Mahou-Dev/Ecourtier2026.git`).

---

**Verdict Lot 0 :** **ÉTAT DE RÉFÉRENCE ÉTABLI — DÉPÔT INTACT — PRÊT POUR LOT 1.**
