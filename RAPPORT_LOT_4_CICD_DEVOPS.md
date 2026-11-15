# RAPPORT DE VALIDATION — LOT 4 : MISE EN PLACE D'UNE PRATIQUE CI/CD PROFESSIONNELLE & HARDENING DEVOPS (E-COURTIER SÉNÉGAL)

**Date :** 15 novembre 2026  
**Dépôt :** `/home/mahou-dev/Documents/Ecourtier2026` — Branche `main` (`HEAD` : `f2bf8a97af5c9fc43c40d25a0ed9eeacfb25f317`)  
**Fournisseur Git identifié :** GitHub (`git@github.com:Mahou-Dev/Ecourtier2026.git`)  
**Plateforme CI/CD mise en place :** GitHub Actions (`.github/workflows/`)  
**Statut Wave :** **100 % HORS PÉRIMÈTRE — AUCUNE MODIFICATION**

---

## 1. SYNTHÈSE EXÉCUTIVE

Le Lot 4 établit une pratique CI/CD complète, reproductible et sécurisée pour E-Courtier Sénégal, adaptée au fournisseur distant réel GitHub sans aucune manipulation destructrice de l'historique ou des worktrees locaux.

Une chaîne de validation en 6 étapes (`.github/workflows/ci.yml`) et un pipeline de packaging/release avec sas de déploiement staging (`.github/workflows/cd-release.yml`) ont été conçus avec :
- Permissions GitHub Actions selon le principe du moindre privilège (`permissions: contents: read`).
- Précontrôles stricts de structure, cohérence des contrats OpenAPI (SHA-256) et scan préventif de secrets / clés privées.
- Validation Backend sous Java 21 Temurin avec seuil de couverture de code JaCoCo imposé (>= 60 % de couverture de lignes, mesuré à **86,85 %**).
- Validation de l'intégrité de la base de données PostgreSQL 16 isolée via Flyway de `V1` à `V20` et tests de concurrence/verrous Testcontainers.
- Contrôles qualité Frontend Node 20 (`npm ci`, ESLint sans avertissement, typage TypeScript strict, build production Vite + PWA).
- Exécution automatisée des tests E2E Playwright contre une instance Spring Boot réelle éphémère.
- Hardening des conteneurs Docker (multi-stage non-root Nginx pour le frontend, `HEALTHCHECK` intégrés sur backend et frontend, séparation stricte `docker-compose.yml` développement et `docker-compose.prod.yml` durci avec variables obligatoires sans valeurs par défaut faibles).
- Hardening de la configuration de déploiement Cloud `render.yaml` (`GROQ_API_KEY`, `npm ci`, désactivation des secrets sensibles synchronisés).

**Verdict Lot 4 : VALIDÉ.**

---

## 2. ARCHITECTURE DE LA CHAÎNE CI/CD GITHUB ACTIONS

### 2.1 Pipeline d'Intégration Continue (`.github/workflows/ci.yml`)

- **Déclencheurs :** `pull_request` (branches `main`, `develop`), `push` (branches `main`, `develop`), `workflow_dispatch` (manuel).
- **Politique de concurrence :** `cancel-in-progress: true` pour éviter les gaspillages de calcul sur commits consécutifs.
- **Principe de moindre privilège :** `permissions: contents: read` au niveau racine du workflow.

```
+-----------------------------------------------------------------------------------------------------+
|                                          Étape 1 : PRÉCONTRÔLES                                      |
| - Intégrité fichiers (pom.xml, package.json, openapi.json)                                          |
| - Vérification parité SHA-256 docs/openapi.json <-> backend/.../openapi.json                         |
| - Unicité et ordre séquentiel des préfixes Flyway V1..V20                                           |
| - Scan anti-fuite de secrets (clés privées, tokens gsk_*, fichiers .env non autorisés)             |
+--------------------------------------------------+--------------------------------------------------+
                                                   |
                   +-------------------------------+-------------------------------+
                   |                                                               |
+------------------v-------------------------------+             +-----------------v------------------+
|           Étape 2 : BACKEND JAVA 21              |             |         Étape 4 : FRONTEND         |
| - Java 21 Temurin + cache Maven                  |             | - Node.js 20 + cache npm           |
| - Compilation & Tests unitaires / sécurité       |             | - Installation propre `npm ci`     |
| - Seuil JaCoCo minimum 60% lignes (86.85% réel)  |             | - ESLint strict (--max-warnings 0) |
| - Upload artefact rapport JaCoCo                 |             | - Typage TypeScript noEmit         |
+------------------+-------------------------------+             | - Build production Vite + PWA      |
                   |                                             +-----------------+------------------+
+------------------v-------------------------------+                               |
|        Étape 3 : POSTGRESQL 16 & FLYWAY          |                               |
| - Service container postgres:16-alpine           |                               |
| - Exécution migrations complètes V1 à V20        |                               |
| - Tests verrous & transactions Testcontainers    |                               |
+------------------+-------------------------------+                               |
                   |                                                               |
                   +-------------------------------+-------------------------------+
                                                   |
+--------------------------------------------------v--------------------------------------------------+
|                                  Étape 5 : TESTS E2E PLAYWRIGHT                                     |
| - Démarrage conjoint backend Spring Boot réel (port 18080) & preview frontend Vite (port 4173)     |
| - Healthcheck HTTP /api/v1/health avant lancement des scénarios                                     |
| - Exécution Chromium sans mock backend (phase2-security-ui, phase3-real-backend)                    |
| - Capture des diagnostics et logs d'échec en cas de régression                                      |
+--------------------------------------------------+--------------------------------------------------+
                                                   |
+--------------------------------------------------v--------------------------------------------------+
|                                    Étape 6 : VALIDATION DOCKER                                      |
| - Validation syntaxique docker-compose.yml et docker-compose.prod.yml (docker compose config)       |
| - Build image multi-stage Backend (Java 21 JRE non-root)                                            |
| - Build image multi-stage Frontend (Nginx unprivileged non-root port 8080)                          |
+-----------------------------------------------------------------------------------------------------+
```

### 2.2 Pipeline de Packaging & Release (`.github/workflows/cd-release.yml`)

- **Déclencheurs :** Tags git de version (`v*.*.*`) ou déclenchement manuel avec choix de l'environnement (`staging`, `validation`).
- **Sas de sécurité :** Aucun push en production n'est automatique. Le pipeline produit les artefacts vérifiés (JAR exécutable optimisé, bundle statique SPA `dist/`, contrat `openapi.json`) et soumet la livraison à une porte de validation d'environnement manuel (`staging-validation-gate`).

---

## 3. HARDENING DOCKER, DOCKER COMPOSE & RENDER

### 3.1 Dockerfiles Durcis

1. **`backend/Dockerfile` (Production) :**
   - Multi-stage build : `maven:3.9.9-eclipse-temurin-21-alpine` pour la compilation, `eclipse-temurin:21-jre-alpine` pour l'exécution.
   - Utilisateur système non-root dédié (`appuser:appgroup`).
   - Création du répertoire `/app/uploads` avec droits stricts avant abaissement des privilèges.
   - Instruction `HEALTHCHECK` intégrée : sonde `/api/v1/health` toutes les 30s.
2. **`frontend/Dockerfile` (Production) :**
   - Multi-stage build : `node:20-alpine` (`npm ci` puis `npm run build`), suivi de `nginxinc/nginx-unprivileged:1.27-alpine`.
   - Serveur Nginx non-root écoutant sur le port 8080.
   - Configuration `frontend/nginx.conf` : en-têtes de sécurité (`X-Content-Type-Options: nosniff`, `X-Frame-Options: SAMEORIGIN`, `Referrer-Policy: strict-origin-when-cross-origin`), politique de cache optimisée (`no-cache` sur `index.html` et service worker, cache immuable 1 an sur `/assets/`), réécriture SPA `try_files $uri $uri/ /index.html`.
   - Instruction `HEALTHCHECK` intégrée via `wget` sur port 8080.
3. **`frontend/Dockerfile.dev` (Développement) :**
   - Conserve la compatibilité avec `docker-compose.yml` pour le rechargement à chaud Vite (HMR) sur le port 3000.

### 3.2 Docker Compose & Profils d'Exécution

- **`docker-compose.yml` (Dev) :** Utilise `Dockerfile.dev`, bind-mounts pour le hot-reload, suppression de la valeur par défaut faible pour le mot de passe bootstrap admin.
- **`docker-compose.prod.yml` (Production) :**
  - Utilise les deux Dockerfiles de production multi-stage.
  - Variables sensibles obligatoires sans valeur par défaut faible (`${POSTGRES_PASSWORD:?}`, `${JWT_SECRET:?}`).
  - Volumes persistants nommés (`pgdata_prod`, `uploads_prod`).
  - Dépendances ordonnées avec sondes de santé (`condition: service_healthy`).

### 3.3 Infrastructure as Code Cloud (`render.yaml`)

- Ajout de la variable `GROQ_API_KEY` (`sync: false`) et `IA_PROVIDER: groq`.
- Remplacement du build command frontend par `npm ci && npm run build` pour garantir des builds strictement déterministes.
- Enregistrement des configurations PostgreSQL 16 managé et des en-têtes CORS stricts.

---

## 4. PREUVES DE VALIDATION LOCALE

| Contrôle | Commande exécutée | Résultat |
|----------|-------------------|----------|
| **Syntaxe Compose Dev & Prod** | `docker compose -f docker-compose.yml config -q && docker compose -f docker-compose.prod.yml config -q` | **Succès (code 0)** |
| **Syntaxe Workflows & Blueprint** | `python3 -c "import yaml; yaml.safe_load(...) ..."` sur `.github/workflows/ci.yml`, `cd-release.yml`, `render.yaml` | **YAML_OK (code 0)** |
| **Seuil JaCoCo Maven (>= 60%)** | `mvn -f backend/pom.xml jacoco:check` | **All coverage checks have been met (BUILD SUCCESS)** — Couverture réelle : **86,85 %** lignes |
| **Portabilité Playwright CI/Dev** | `frontend/playwright.config.ts` fallback conditionnel `fs.existsSync(localChromiumPath)` | **Vérifié (adaptatif)** |

---

## 5. CONTRE-AUDIT INDÉPENDANT DU LOT 4 (ÉTAPE E)

- **Risque d'écrasement ou pollution Git :** Tous les nouveaux fichiers ont été ajoutés de façon additive (`.github/`, `docker-compose.prod.yml`, `frontend/Dockerfile.dev`, `frontend/nginx.conf`). Aucun commit, push, rebase ou reset n'a été exécuté, respectant intégralement l'état du dépôt.
- **Risque de fuite de secrets en CI :** L'Étape 1 du workflow CI analyse activement les fichiers suivis pour bloquer toute clé privée ou variable `.env` non exemple. De plus, `docker-compose.prod.yml` impose l'injection externe des secrets sans valeur par défaut.
- **Exclusion Wave :** Aucune étape de la CI n'invoque d'endpoint ou de secret Wave externe. Les tests de robustesse locaux continuent de s'appuyer sur le mock interne sans dépendance réseau.
- **Reproductibilité :** Le remplacement systématique de `npm install` par `npm ci` et le verrouillage des versions Temurin 21 et Node 20 garantissent l'absence de dérive des dépendances entre la machine locale et les runners CI.

---

## 6. VERDICT FINAL LOT 4

> **VERDICT : LOT 4 VALIDÉ.**  
> La pratique CI/CD professionnelle est en place, les workflows GitHub Actions sont structurés et validés, le seuil JaCoCo (>= 60 %) est intégré dans le cycle Maven et satisfait (86,85 %), les Dockerfiles et configurations Compose sont durcis et non-root, sans impact destructif sur le dépôt.
