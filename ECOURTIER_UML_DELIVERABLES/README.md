# DOSSIER DE MODÉLISATION UML 2.5 — E-COURTIER SÉNÉGAL

**Projet :** Plateforme Immobilière Intelligente E-Courtier Sénégal  
**Cadre Académique :** Licence 3 Ingénierie Informatique (2026)  
**Norme de Modélisation :** UML 2.5 / C4 Model  
**Formats Disponibles :** Sources PlantUML (`.puml`), Rendu Vectoriel Web (`.svg`)

---

## 1. VUE D'ENSEMBLE DU DOSSIER

Ce dossier regroupe la modélisation architecturale, conceptuelle et comportementale complète de l'application **E-Courtier Sénégal**. Chaque diagramme est rigoureusement synchronisé avec l'implémentation effective du code source (Java 21 / Spring Boot 3.4 et React 19 / TypeScript / Vite 6).

### Sommaire des Diagrammes

| Réf | Titre du Diagramme | Fichier Source (`.puml`) | Rendu Vectoriel (`.svg`) | Domaine d'Application dans le Code |
| :---: | :--- | :--- | :--- | :--- |
| **01** | **Cas d'Utilisation Globaux** | `use_cases_ecourtier.puml` | `svg/use_cases_ecourtier.svg` | Tous modules frontend et contrôleurs |
| **02** | **Diagramme de Classes Métier** | `class_diagram_domain.puml` | `svg/class_diagram_domain.svg` | Entités JPA (`backend/src/main/java/.../domain/`) |
| **03** | **Séquence Auth & Rotation JWT** | `sequence_auth_jwt_rotation.puml` | `svg/sequence_auth_jwt_rotation.svg` | `AuthService.java`, `JwtService.java`, `RefreshTokenEntity.java` |
| **04** | **Séquence Séquestre & Libération** | `sequence_escrow_lifecycle.puml` | `svg/sequence_escrow_lifecycle.svg` | `TransactionService.java`, `TransactionEntity.java`, `WaveClientMock.java` |
| **05** | **Séquence Bail PDF & Scheduler** | `sequence_bail_pdf_signature.puml` | `svg/sequence_bail_pdf_signature.svg` | `PdfBailGeneratorService.java`, `BailPdfRecoveryScheduler.java` |
| **06** | **Architecture des Composants** | `component_architecture.puml` | `svg/component_architecture.svg` | Modules React/TypeScript et couches Spring Boot |
| **07** | **Topologie de Déploiement** | `deployment_topology.puml` | `svg/deployment_topology.svg` | `docker-compose.prod.yml`, `backend/Dockerfile`, `frontend/Dockerfile` |
| **08** | **Machine à États Annonces & Tx** | `state_machine_annonces_transactions.puml` | `svg/state_machine_annonces_transactions.svg` | `AnnonceStatutStateMachine.java`, `StatutSequestre.java` |
| **09** | **Architecture Pipeline CI/CD** | `cicd_pipeline_architecture.puml` | `svg/cicd_pipeline_architecture.svg` | `.github/workflows/ci.yml`, `.github/workflows/cd-release.yml` |

---

## 2. DESCRIPTION DÉTAILLÉE DE CHAQUE MODÈLE

### 01. Diagramme des Cas d'Utilisation (`use_cases_ecourtier.puml`)
- **Acteurs modélisés :**
  - **Visiteur :** Consultation des annonces, recherche multicritères, exploration cartographique Leaflet, dialogue avec l'assistant IA Samba, inscription et connexion.
  - **Locataire (hérite de Visiteur) :** Gestion des favoris, demande de visite avec rappel SMS automatique, initiation de séquestre, paiement Wave sandbox, double signature du bail, évaluation et signalement.
  - **Propriétaire / Agent (hérite de Visiteur) :** Dépôt d'annonces, upload de photos et documents légaux (titre foncier, bail), acceptation/refus de visites, signature de bail et encaissement des fonds débloqués.
  - **Administrateur :** Modération et audit des documents justificatifs (attribution du badge vérifié), gestion du statut des annonces (suspension/réactivation CA3-004), modération des utilisateurs (révocation immédiate REST/WS), suivi des statistiques financières.

### 02. Diagramme de Classes Métier (`class_diagram_domain.puml`)
- **Modèle de données relationnel JPA 3 :**
  - `UtilisateurEntity` : gestion du compte, rôle (`ADMIN`, `AGENT`, `PROPRIETAIRE`, `LOCATAIRE`), statut `actif`, et champ de sécurité `tokenVersion` (incrémenté lors de révocation ou compromission).
  - `RefreshTokenEntity` : stockage sécurisé du hash SHA-256 du refresh token, regroupement par `familleId`, numéro de séquence, état `consomme` et `revoque`.
  - `AnnonceEntity` : attributs immobiliers (prix, charges, caution, superficie, coordonnées GPS), statut de publication (`BROUILLON`, `DISPONIBLE`, `SUSPENDUE`, `LOUEE`...), statut de vérification documentaire, suppression logique (`supprime=true`), et **estampille de concurrence optimiste `@Version Long version`**.
  - `TransactionEntity` : séquestre découpé (loyer, caution, frais de plateforme de 5 000 FCFA), `statutSequestre` (`INITIALISE`, `BLOQUE`, `LIBERE`...), `statutBail` (`SIGNE_LOCATAIRE`, `SIGNE_COMPLET`), `statutDocumentBail` (`GENERE`, `EN_ERREUR`), dates certifiées Dakar.
  - `SchedulerLockEntity` : table de verrouillage distribué pour instances multiples sous PostgreSQL (`tryAcquireLock`).

### 03. Diagramme de Séquence Auth & Rotation Refresh Token (`sequence_auth_jwt_rotation.puml`)
- **Mécanismes illustrés :**
  1. Authentification initiale avec émission conjointe d'un JWT (durée 15 min, claim `tv: 1`) et d'un Refresh Token opaque 256 bits (durée 7 jours, séquence 1).
  2. Rotation stricte : chaque rafraîchissement consomme l'ancien token et en émet un nouveau (séquence incrémentée).
  3. **Détection d'attaque par rejeu :** si un attaquant rejoue un token déjà consommé, le serveur invalide immédiatement toute la famille (`familleId`) et incrémente `tokenVersion` sur l'utilisateur, provoquant la déconnexion immédiate de toutes les sessions actives.

### 04. Diagramme de Séquence Séquestre & Libération des Fonds (`sequence_escrow_lifecycle.puml`)
- **Règles métier :**
  - Verrouillage de l'annonce lors de la création du dossier (contrainte d'unicité `uq_transactions_annonce_active`).
  - Paiement sécurisé sur compte séquestre via Wave Sandbox (caution + 1er loyer + 5 000 FCFA de frais).
  - Déblocage automatique après double signature électronique : le bailleur reçoit caution + 1er loyer, E-Courtier conserve les 5 000 FCFA, l'annonce bascule en statut `LOUEE`.

### 05. Diagramme de Séquence Bail PDF & Auto-Guérison (`sequence_bail_pdf_signature.puml`)
- **Architecture de robustesse I/O :**
  - Génération atomique via fichier temporaire `.pdf.tmp` puis déplacement atomique pour éviter tout fichier corrompu en lecture concurrente.
  - Horodatage officiel certifié en zone `Africa/Dakar`.
  - En cas d'erreur I/O, bascule en statut `EN_ERREUR` et incrémentation de `pdfBailTentatives`.
  - Tâche de fond `BailPdfRecoveryScheduler` (toutes les 10 min) sous verrou PostgreSQL pour réparer automatiquement les baux en échec.

### 06. Architecture des Composants (`component_architecture.puml`)
- **Découpage en couches C4 :**
  - **Client SPA :** React 19, Vite 6, Tailwind CSS, Zustand, React Query, Leaflet, Workbox PWA (mode hors-ligne avec 61 assets pré-cachés).
  - **Filtres de sécurité :** `JwtAuthenticationFilter`, `LoginRateLimiterFilter`, `WebSocketAuthInterceptor`.
  - **Couche Métier :** 20 contrôleurs REST/WS, services métier isolés, machine à états finis `AnnonceStatutStateMachine`.
  - **Infrastructure :** PostgreSQL 16.15 (20 migrations Flyway), API Groq Cloud (Llama-3.3-70b), volume de stockage local sécurisé `/app/uploads`.

### 07. Topologie de Déploiement (`deployment_topology.puml`)
- **Durcissement des conteneurs :**
  - Conteneur Nginx Frontend non-root (Alpine Unprivileged, UID 101, port 8080).
  - Conteneur Backend Spring Boot non-root (Eclipse Temurin 21 JRE, UID 1000).
  - Conteneur PostgreSQL 16.15 isolé : **port 5432 non exposé sur l'hôte**, accessible uniquement via le réseau bridge interne `ecourtier-net-prod`.
  - Volumes persistants chiffrés pour les données PostgreSQL et les uploads.

### 08. Diagramme d'États Annonces & Transactions (`state_machine_annonces_transactions.puml`)
- **Cycles de vie finis :**
  - Annonce : `BROUILLON` -> `EN_ATTENTE_VERIFICATION` -> `DISPONIBLE` <-> `SUSPENDUE` (CA3-004) -> `LOUEE` / `VENDUE` / `SUPPRIMEE_LOGIQUE`.
  - Transaction : `INITIALISE` (timeout 48h) -> `SEQUESTRE_BLOQUE` -> `BAIL_SIGNE_LOCATAIRE` -> `SIGNE_COMPLET` -> `LIBERE` (ou `LITIGE` -> `REMBOURSE`).

### 09. Architecture CI/CD (`cicd_pipeline_architecture.puml`)
- **6 étapes de validation automatisée :**
  1. Précontrôles & scan anti-secrets.
  2. Compilation Java 21, 395 tests unitaires et couverture JaCoCo (86.85% >= 60%).
  3. Validation PostgreSQL 16 Testcontainers (migrations Flyway V1 à V20).
  4. Linter ESLint strict (`--max-warnings 0`), typage TypeScript et build Vite PWA.
  5. 10 tests de bout en bout Playwright sur instance Spring Boot réelle.
  6. Validation des images de conteneurs Docker durcies.
  - Barrière manuelle de validation Staging avant mise en production.

---

## 3. COMMENT EXPLOITER CE DOSSIER

1. **Visualisation immédiate :** Tous les diagrammes vectoriels sont consultables dans le répertoire `svg/` à l'aide de n'importe quel navigateur web ou visualiseur d'images.
2. **Édition et recompilation :**
   - Les fichiers `.puml` sont éditables avec l'extension officielle PlantUML pour VS Code ou IntelliJ.
   - Le script `./compile_uml.sh` permet de régénérer les fichiers SVG à tout moment.
