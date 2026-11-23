# 🇸🇳 E-Courtier Sénégal — Plateforme Immobilière Sécurisée & Intelligente

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://adoptium.net/)
[![React](https://img.shields.io/badge/React-19.0.0-blue.svg)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.7-blue.svg)](https://www.typescriptlang.org/)
[![Vite](https://img.shields.io/badge/Vite-6.2-purple.svg)](https://vitejs.dev/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16.15-blue.svg)](https://www.postgresql.org/)
[![Flyway](https://img.shields.io/badge/Flyway-V1..V21-red.svg)](https://flywaydb.org/)
[![JaCoCo](https://img.shields.io/badge/JaCoCo%20Line%20Coverage-86.85%25-brightgreen.svg)](backend/pom.xml)
[![Playwright](https://img.shields.io/badge/Playwright%20E2E-10%2F10%20Pass-green.svg)](frontend/playwright.config.ts)
[![CI/CD](https://img.shields.io/badge/GitHub%20Actions-CI%2FCD%20Ready-brightgreen.svg)](.github/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**E-Courtier Sénégal** est une plateforme web immobilière intelligente, sécurisée et adaptée aux réalités socio-économiques et géographiques du Sénégal (Dakar, Thiès, Ziguinchor, Saly, Mbour, Saint-Louis).  
Développée dans le cadre d'un projet de fin d'études de **Licence 3 Ingénierie Informatique (2026)**, l'application résout les problématiques majeures du secteur locatif et transactionnel sénégalais : arnaques aux faux baux, doubles réservations, paiements sauvages de cautions non protégées et absence de vérification cadastrale.

---

## 📋 TABLE DES MATIÈRES

1. [Problématiques & Solutions E-Courtier](#1-problématiques--solutions-e-courtier)
2. [Architecture Technique & Stack Technologique](#2-architecture-technique--stack-technologique)
3. [Dossier de Modélisation UML 2.5 (9 Modèles)](#3-dossier-de-modélisation-uml-25)
4. [Pratique CI/CD & Pipeline Automatisé](#4-pratique-cicd--pipeline-automatisé)
5. [Politique de Sécurité & Garde-Fous](#5-politique-de-sécurité--garde-fous)
6. [Statut Réel des Intégrations (Transparence & Exclusion Wave)](#6-statut-réel-des-intégrations-transparence--exclusion-wave)
7. [Installation & Démarrage Rapide](#7-installation--démarrage-rapide)
8. [Campagne de Tests & Preuves de Non-Régression](#8-campagne-de-tests--preuves-de-non-régression)
9. [Documentation des API (OpenAPI 3.0.1 & Swagger)](#9-documentation-des-api-openapi-301--swagger)
10. [Guide de Démonstration Reproductible (Soutenance)](#10-guide-de-démonstration-reproductible-soutenance)
11. [Comptes de Démonstration Pré-configurés](#11-comptes-de-démonstration-pré-configurés)
12. [Déploiement en Production Cloud (Render & Docker Prod)](#12-déploiement-en-production-cloud-render--docker-prod)

---

## 1. PROBLÉMATIQUES & SOLUTIONS E-COURTIER

| Défi Immobilier au Sénégal | Solution Technologique Apportée par E-Courtier |
| :--- | :--- |
| **Arnaques aux fausses annonces & usurpations** | Audit documentaire préalable des titres fonciers et baux (`DocumentVerificationEntity`), attribution contrôlée du **Badge Vérifié** par l'administration. |
| **Versement sauvage de la caution avant signature** | **Séquestre numérique automatisé** : les fonds (caution + 1er loyer + 5 000 FCFA de frais) sont consignés sur un compte séquestre tiers de confiance avant tout engagement. |
| **Litiges sur les conditions du bail** | **Génération atomique du contrat de bail officiel** (OpenPDF) avec horodatage certifié en heure de Dakar (`Africa/Dakar`) et double signature électronique mutuelle. |
| **Recherche fastidieuse & barrière de la langue** | **Assistant IA Samba** : dialogue en langage naturel (Français et Wolof), extraction intelligente de critères et génération instantanée d'une shortlist de biens. |
| **Concurrence & modifications simultanées** | **Verrouillage optimiste `@Version`** : détection et rejet immédiat des conflits d'édition concurrente (HTTP 409 Conflict). |
| **Connexion instable ou zones blanches** | **Application Web Progressive (PWA)** avec Service Worker Workbox (61 ressources pré-cachées pour navigation fluide hors-ligne). |

---

## 2. ARCHITECTURE TECHNIQUE & STACK TECHNOLOGIQUE

```mermaid
graph TD
    User([Utilisateur / Locataire / Bailleur]) -->|HTTPS 443 / WSS| Nginx[Conteneur Nginx 1.27 Unprivileged (Port 8080)]
    Nginx -->|Fichiers Statiques| SPA[Client SPA React 19 / TypeScript / Vite 6]
    Nginx -->|Reverse-Proxy /api/v1| Spring[Backend Spring Boot 3.4.3 / Java 21 LTS]
    Nginx -->|Reverse-Proxy /ws-ecourtier| WSServer[Serveur STOMP WebSocket]
    Spring -->|JDBC HikariPool :5432| Postgres[(Base PostgreSQL 16.15 / Flyway V1..V21)]
    Spring -->|Stockage Sécurisé Non-Root| FS[/app/uploads : Photos, Titres & Baux PDF/]
    Spring -->|Requêtes sortantes HTTPS| Gemini[API Google Gemini : gemini-flash-lite-latest]
```

- **Backend** : Java 21 LTS, Spring Boot 3.4.3, Spring Security 6 (Filtre JWT sans état + vérification de `token_version` + preuve anonyme `X-IA-Session-Token`), Spring Data JPA, Hibernate 6, OpenPDF 2.0.2, Flyway 10, Bucket4j Bounded Rate Limiting.
- **Frontend** : React 19.0.0, TypeScript 5.7, Vite 6.2, Tailwind CSS 3.4, TanStack React Query v5, Zustand, Lucide React, Leaflet 1.9, vite-plugin-pwa (Workbox).
- **Base de Données** : PostgreSQL 16.15 avec 21 migrations Flyway séquentielles, contraintes d'intégrité, index partiels (`uq_transactions_annonce_active`) et table de verrous multi-instances (`scheduler_locks`).
- **Conteneurisation** : Docker multi-stage durci (utilisateurs non-root : UID 101 pour Nginx, UID 1000 pour Spring Boot).

---

## 3. DOSSIER DE MODÉLISATION UML 2.5

L'ensemble des modèles UML est disponible dans le répertoire [`ECOURTIER_UML_DELIVERABLES/`](ECOURTIER_UML_DELIVERABLES/) au format source PlantUML (`.puml`) et rendus vectoriels haute fidélité (`.svg`) :

| N° | Diagramme | Fichier Source | Rendu Vectoriel Web | Description Technique |
| :---: | :--- | :--- | :--- | :--- |
| **01** | **Cas d'Utilisation** | [`use_cases_ecourtier.puml`](ECOURTIER_UML_DELIVERABLES/use_cases_ecourtier.puml) | [`svg/use_cases_ecourtier.svg`](ECOURTIER_UML_DELIVERABLES/svg/use_cases_ecourtier.svg) | Modélisation des rôles Visiteur, Locataire, Propriétaire et Administrateur. |
| **02** | **Classes Métier** | [`class_diagram_domain.puml`](ECOURTIER_UML_DELIVERABLES/class_diagram_domain.puml) | [`svg/class_diagram_domain.svg`](ECOURTIER_UML_DELIVERABLES/svg/class_diagram_domain.svg) | Entités JPA complètes, champs, enums, cardinalités et verrou optimiste `@Version`. |
| **03** | **Séquence Auth & JWT** | [`sequence_auth_jwt_rotation.puml`](ECOURTIER_UML_DELIVERABLES/sequence_auth_jwt_rotation.puml) | [`svg/sequence_auth_jwt_rotation.svg`](ECOURTIER_UML_DELIVERABLES/svg/sequence_auth_jwt_rotation.svg) | Rotation stricte des Refresh Tokens, détection de rejeu et révocation de session. |
| **04** | **Séquence Séquestre** | [`sequence_escrow_lifecycle.puml`](ECOURTIER_UML_DELIVERABLES/sequence_escrow_lifecycle.puml) | [`svg/sequence_escrow_lifecycle.svg`](ECOURTIER_UML_DELIVERABLES/svg/sequence_escrow_lifecycle.svg) | Blocage des fonds Wave Sandbox et déblocage automatique après double signature. |
| **05** | **Séquence Bail PDF** | [`sequence_bail_pdf_signature.puml`](ECOURTIER_UML_DELIVERABLES/sequence_bail_pdf_signature.puml) | [`svg/sequence_bail_pdf_signature.svg`](ECOURTIER_UML_DELIVERABLES/svg/sequence_bail_pdf_signature.svg) | Écriture atomique temporaire `.pdf.tmp` et reprise automatique par scheduler. |
| **06** | **Architecture Composants** | [`component_architecture.puml`](ECOURTIER_UML_DELIVERABLES/component_architecture.puml) | [`svg/component_architecture.svg`](ECOURTIER_UML_DELIVERABLES/svg/component_architecture.svg) | Découplage C4 Level 2 : SPA React, passerelle sécurité, services et persistance. |
| **07** | **Topologie Déploiement** | [`deployment_topology.puml`](ECOURTIER_UML_DELIVERABLES/deployment_topology.puml) | [`svg/deployment_topology.svg`](ECOURTIER_UML_DELIVERABLES/svg/deployment_topology.svg) | Conteneurs durcis, utilisateurs non-root et isolation du port PostgreSQL 5432. |
| **08** | **Machine à États** | [`state_machine_annonces_transactions.puml`](ECOURTIER_UML_DELIVERABLES/state_machine_annonces_transactions.puml) | [`svg/state_machine_annonces_transactions.svg`](ECOURTIER_UML_DELIVERABLES/svg/state_machine_annonces_transactions.svg) | Automates d'états : Annonces (`DISPONIBLE`/`SUSPENDUE`) et Séquestre (`BLOQUE`/`LIBERE`). |
| **09** | **Architecture CI/CD** | [`cicd_pipeline_architecture.puml`](ECOURTIER_UML_DELIVERABLES/cicd_pipeline_architecture.puml) | [`svg/cicd_pipeline_architecture.svg`](ECOURTIER_UML_DELIVERABLES/svg/cicd_pipeline_architecture.svg) | Pipeline GitHub Actions en 6 phases avec barrière manuelle de livraison Staging. |

---

## 4. PRATIQUE CI/CD & PIPELINE AUTOMATISÉ

Le dépôt intègre deux pipelines automatisés GitHub Actions :

1. **Intégration Continue (CI)** — [`.github/workflows/ci.yml`](.github/workflows/ci.yml) :
   - **Étape 1 : Précontrôles & Sécurité** : Vérification d'arborescence, unicité stricte des préfixes Flyway V1 à V21, scan anti-fuite de secrets (`git ls-files`).
   - **Étape 2 : Backend Java 21** : Compilation Maven, tests unitaires et d'intégration, vérification bloquante JaCoCo (seuil minimum 60% de lignes couvertes).
   - **Étape 3 : Base de données PostgreSQL 16 réelle** : Exécution Testcontainers sur schéma vierge des 21 migrations Flyway, vérification des index partiels et des verrous de concurrence.
   - **Étape 4 : Frontend Node 20 & Qualité** : Linter ESLint strict (`--max-warnings 0`), typage TypeScript (`tsc --noEmit`), build Vite PWA (61 ressources pré-cachées).
   - **Étape 5 : Tests de Bout en Bout Playwright** : Démarrage conjoint du backend réel (port 18080) et du preview Vite (port 4173), exécution des 10 scénarios E2E.
   - **Étape 6 : Validation des Conteneurs Docker** : Validation syntaxique Docker Compose (`dev` et `prod`), construction des images de production.
2. **Livraison Continue (CD)** — [`.github/workflows/cd-release.yml`](.github/workflows/cd-release.yml) :
   - Packaging et signature des artefacts (JAR Spring Boot, bundle Nginx SPA, contrat OpenAPI 3.0.1).
   - Barrière manuelle d'approbation avant tout déploiement sur l'environnement Staging.

---

## 5. POLITIQUE DE SÉCURITÉ & GARDE-FOUS

1. **Authentification & Double Token** : Access Token JWT courte durée (15 min) avec claim `tv` (version de jeton) couplé à un Refresh Token opaque 256 bits haché en SHA-256 (7 jours).
2. **Révocation Instantanée** : La déconnexion, le changement de mot de passe ou la désactivation d'un compte incrémente `token_version` en base, invalidant immédiatement tous les JWT en circulation et coupant les sessions WebSocket STOMP sans attendre l'expiration naturelle.
3. **Protection Anti-Rejeu (Replay Attack)** : Toute réutilisation d'un Refresh Token déjà consommé entraîne l'invalidation automatique de toute la famille de jetons et la déconnexion d'urgence.
4. **Anti-Brute-Force & Rate Limiting Bounded** : Limitation en mémoire bornée (`BoundedRateLimiterSupport`, capacité maximale 10 000 entrées) sur `/connexion`, `/rafraichir-token` et `/chat` pour prémunir le serveur contre l'épuisement mémoire.
5. **Contrôle Strict en Production (`ProductionConfigurationValidator`)** : En profil `prod`, démarrage bloqué si le secret JWT fait moins de 32 caractères, s'il fait partie d'une liste de secrets triviaux connus, ou si la base de données pointe vers H2.
6. **Suppression Logique & Isolation** : Les annonces avec `supprime = true` sont totalement invisibles des recherches publiques et de l'API utilisateur.

---

## 6. STATUT RÉEL DES INTÉGRATIONS (TRANSPARENCE & EXCLUSION WAVE)

Conformément à la déontologie d'audit et aux consignes du projet :
- **Intégration Wave Money** : **TOTALEMENT EXCLUE DU PÉRIMÈTRE DE MODIFICATION**.
  - Le système de paiement séquestre fonctionne à travers l'implémentation de simulation sandbox **`WaveClientMock.java`**.
  - **Aucun appel réseau réel n'est effectué vers l'infrastructure de production Wave**.
  - Aucune clé secrète Wave de production n'est stockée ni manipulée.
  - Tous les flux financiers sont rigoureusement vérifiés et modélisés en environnement de démonstration sandbox.
- **Assistant IA Samba** : Intégration hybride avec client HTTP direct vers l'API Cloud **Groq (Llama-3.3-70b-versatile)** protégée par un rate limiter et doublée d'un moteur d'extraction heuristique local (`RuleBasedCriteriaExtractor.java`) en cas d'indisponibilité réseau.
- **Rappels SMS de Visite** : Enregistrés en base de données et tracés pour démonstration via `rappelSmsEnvoye = true` sans surcoût d'opérateur télécom externe.

---

## 7. INSTALLATION & DÉMARRAGE RAPIDE

### Préréquis
- Docker Desktop & Docker Compose v2+
- Git
- JDK 21 & Node.js 20+ (uniquement en cas de développement hors Docker)

### Option A — Démarrage en Conteneurs de Développement (Recommandé)

```bash
git clone https://github.com/Mahou-Dev/Ecourtier2026.git
cd Ecourtier2026

# Démarrer la stack de développement complète
docker compose up --build
```
- **Frontend SPA (Vite Hot-Reload)** : `http://localhost:3000`
- **Backend API Spring Boot** : `http://localhost:8080`
- **Swagger UI** : `http://localhost:8080/swagger-ui.html`
- **Documentation OpenAPI JSON** : `http://localhost:8080/openapi.json`

### Option B — Démarrage en Conteneurs Durcis de Production

```bash
# Générer un secret JWT sécurisé pour le test
export POSTGRES_PASSWORD="MonMotDePassePostgresSecurise2026!"
export JWT_SECRET="uneCleSecreteTresLongueEtEntropiqueDeProduction2026_Minimum64Caracteres!"
export ADMIN_BOOTSTRAP_PASSWORD="MonSuperMotDePasseAdmin2026!"

docker compose -f docker-compose.prod.yml up --build -d
```
- **Application Web Sécurisée (Nginx Non-Root)** : `http://localhost:8080`

---

## 8. CAMPAGNE DE TESTS & PREUVES DE NON-RÉGRESSION

Toutes les suites de tests sont automatisées, reproductibles et s'exécutent en local comme en intégration continue :

### 8.1 Tests Backend & Couverture JaCoCo

```bash
mvn -f backend/pom.xml test jacoco:check
```
- **Résultat Lot 5 :** `395 tests exécutés, 0 échec, 0 erreur`.
- **Couverture de code JaCoCo :** **86.85% de lignes couvertes** (seuil requis >= 60%).

### 8.2 Tests de Persistance PostgreSQL 16 (Testcontainers)

```bash
mvn -f backend/pom.xml test -Decourtier.test.pg16=true -Dapi.version=1.44 -Dtest=FlywayFreshMigrationIntegrationTest,Postgres16Phase3IntegrationTest
```
- **Validation :** 21 migrations Flyway appliquées avec succès sur moteur PostgreSQL 16 officiel.

### 8.3 Contrôles Frontend (Lint & Types)

```bash
# Contrôle ESLint strict (zéro avertissement toléré)
npm --prefix frontend run lint

# Vérification du typage statique TypeScript
npx --prefix frontend tsc -p frontend/tsconfig.json --noEmit

# Build de production Vite & PWA
npm --prefix frontend run build
```

### 8.4 Tests de Bout en Bout E2E Playwright

```bash
# Exécution de la suite complète E2E sur backend réel
bash .github/scripts/run_e2e_local.sh # ou via le script de test
npx --prefix frontend playwright test --config frontend/playwright.config.ts e2e/phase2-security-ui.spec.ts e2e/phase3-real-backend.spec.ts
```
- **Résultat Lot 5 :** `10/10 tests passés avec succès` (séquestre Wave, double signature, révocation, lock 409).

---

## 9. DOCUMENTATION DES API (OPENAPI 3.0.1 & SWAGGER)

Le contrat d'API REST est formalisé selon la spécification **OpenAPI 3.0.1** :
- Fichier racine : [`docs/openapi.json`](docs/openapi.json)
- Fichier embarqué backend : `backend/src/main/resources/static/openapi.json`
- **Synchronisation vérifiée :** Test d'intégration automatisé `OpenApiContractIntegrationTest.java` garantissant l'égalité stricte des sommes de contrôle SHA-256.
- Interface interactive Swagger UI : accessible sur `http://localhost:8080/swagger-ui.html` en local.

---

## 10. GUIDE DE DÉMONSTRATION REPRODUCTIBLE (SOUTENANCE)

Voici le scénario fluide et sans accroc recommandé pour la soutenance devant le jury :

```text
SCÉNARIO DÉMONSTRATION EN 7 ÉTAPES CLÉS (DURÉE : 12-15 MINUTES)

1. Découverte Visiteur & Recherche Avancée :
   - Accéder à la page d'accueil (http://localhost:3000).
   - Explorer la carte interactive Leaflet (quartiers Almadies, Mermoz, Fann).
   - Appliquer des filtres combinés : Appartement, Budget 200 000 - 500 000 FCFA, Meublé.

2. Interaction avec l'Assistant IA Samba :
   - Ouvrir la bulle de chat en bas à droite.
   - Poser une question en langage naturel : « Je cherche un studio meublé à Dakar avec parking pour 300 000 FCFA ».
   - Observer l'extraction des critères par le modèle IA et l'affichage de la shortlist ciblée.

3. Connexion Locataire & Prise de Rendez-vous :
   - Se connecter avec le compte locataire de démo (locataire.demo@ecourtier.sn / LocatairePass123!).
   - Choisir une annonce, sélectionner une date de visite et valider la demande.
   - Observer la création du rendez-vous et le traçage du rappel SMS.

4. Initiation de Transaction & Paiement du Séquestre :
   - Depuis la fiche détaillée, cliquer sur « Louer ce bien / Démarrer le dossier ».
   - Constater le détail des montants calculés côté serveur : Caution + Premier Loyer + 5 000 FCFA.
   - Cliquer sur « Payer le séquestre (Wave Sandbox) ».
   - Observer le changement immédiat d'état vers 'SÉQUESTRE BLOQUÉ' et le déverrouillage de la signature.

5. Double Signature du Bail Électronique & Génération PDF :
   - Signer en tant que Locataire (validation horodatée en heure de Dakar).
   - Ouvrir une session Propriétaire (proprio.demo@ecourtier.sn / ProprioPass123!) dans un autre onglet.
   - Le propriétaire signe à son tour le bail.
   - Observer le déclenchement immédiat de la libération des fonds (statut 'LIBÉRÉ') et le passage de l'annonce à 'LOUÉE'.
   - Télécharger et ouvrir le document officiel PDF généré par OpenPDF.

6. Démonstration de Robustesse Concurrente (@Version Lock) :
   - Tenter de modifier simultanément la même annonce depuis deux fenêtres.
   - Constater le rejet élégant HTTP 409 Conflict sur la seconde sauvegarde avec invitation à actualiser.

7. Espace Administration & Contrôle :
   - Se connecter en Administrateur (admin@ecourtier.sn / AdminPass123!).
   - Consulter la page de vérification des documents légaux (titres fonciers).
   - Suspendre puis réactiver une annonce (vérification de la machine à états CA3-004).
   - Consulter le tableau de bord financier avec le volume séquestré et les commissions perçues.
```

---

## 11. COMPTES DE DÉMONSTRATION PRÉ-CONFIGURÉS

> [!NOTE]  
> Ces comptes sont injectés automatiquement en environnement de développement (`--spring.profiles.active=dev`) par `DemoDataSeederService.java`. En environnement `prod`, ce service est **strictement désactivé**.

| Rôle Utilisateur | Adresse Email | Mot de Passe | Prérogatives |
| :--- | :--- | :--- | :--- |
| **Administrateur** | `admin@ecourtier.sn` | `AdminPass123!` | Audit documents fonciers, modération utilisateurs, stats financières. |
| **Agent Immobilier** | `agent@ecourtier.sn` | `AgentPass123!` | Dépôt de mandats, gestion d'annonces avec badge « Agent agréé ». |
| **Propriétaire Démo** | `proprio.demo@ecourtier.sn` | `ProprioPass123!` | Publication de biens, acceptation de visites, signature de bail, encaissement. |
| **Locataire Démo** | `locataire.demo@ecourtier.sn` | `LocatairePass123!` | Favoris, réservation de visites, consignation séquestre, signature bail. |

---

## 12. DÉPLOIEMENT EN PRODUCTION CLOUD (RENDER & DOCKER PROD)

Le projet intègre un blueprint Infrastructure as Code [`render.yaml`](render.yaml) configuré pour un déploiement clé en main sur la plateforme **Render** :
1. **Service Web Backend** : image Docker Java 21 non-root, validation automatique des secrets au démarrage.
2. **Site Statique Frontend** : bundle Nginx SPA avec routage HTML5 et politique de sécurité HSTS/CSP.
3. **Base de Données Managée** : PostgreSQL 16 isolé avec sauvegarde automatique.

---

## 📄 LICENCE & MENTIONS ACADÉMIQUES

Projet réalisé dans le cadre de la Licence 3 Ingénierie Informatique (2026).  
Code distribué sous licence MIT. Tous droits réservés pour les marques et référentiels officiels de l'État du Sénégal.
