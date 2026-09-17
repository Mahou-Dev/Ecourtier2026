# 🏡 E-Courtier — Fondations Techniques

**E-Courtier** est une plateforme intelligente de recherche de logement (location & vente) adaptée aux réalités du marché sénégalais. Elle digitalise la recherche immobilière grâce à un assistant IA, la vérification des annonces et un workflow transactionnel sécurisé (visites, caution séquestre, signature de bail, libération des fonds).

> [!NOTE]
> Ce dépôt contient les **fondations techniques unifiées** (Spring Boot 3 + React 18 / Vite / Design System Custom + Docker Compose) sans logique métier active à cette étape.

---

## 🚀 Prérequis

- **Docker & Docker Compose** (version 2.x+)
- **Node.js** 20+ et **npm** 10+ (pour le développement frontend local)
- **Java 21 OpenJDK** et **Maven 3.9+** (pour le développement backend local)

---

## 📦 Structure du Projet

```text
Ecourtier2026/
├── docker-compose.yml       # Dev stack: Postgres + Backend + Frontend
├── .env.example             # Modèle de variables d'environnement globales
├── .gitignore               # Exclusions Git à la racine
├── README.md                # Documentation principale
├── backend/                 # Project Spring Boot 3 (Java 21)
│   ├── Dockerfile
│   ├── .gitignore
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/ecourtier/
│       │   ├── common/      # Security, OpenAPI CORS, Health Check
│       │   └── domain/      # 6 Domaines métiers (utilisateur, annonce, rendezvous, transaction, ia, notification)
│       └── resources/       # application.yml, application-dev.yml, application-prod.yml
└── frontend/                # Project React 18 + TypeScript + Vite
    ├── Dockerfile
    ├── .gitignore
    ├── .env.example
    ├── package.json
    ├── tailwind.config.ts   # Design System Custom (Terre Cuite, Vert Sahel, Sable)
    ├── vite.config.ts
    └── src/
        ├── components/      # UI atomic (Button, Card, Badge) & Layout (Header, Footer)
        ├── pages/           # Landing page Coming Soon
        ├── services/        # Client API avec TanStack Query Provider
        ├── types/           # Interfaces TypeScript
        └── lib/             # Helpers & Stores Zustand
```

---

## ⚡ Démarrage Rapide avec Docker (Command Unique)

1. Copier le fichier d'environnement modèle :
   ```bash
   cp .env.example .env
   cp frontend/.env.example frontend/.env
   ```

2. Lancer la stack complète :
   ```bash
   docker compose up --build -d
   ```

3. Services accessibles :
   - **Frontend React** : [http://localhost:3000](http://localhost:3000)
   - **Backend API Health Check** : [http://localhost:8080/api/v1/health](http://localhost:8080/api/v1/health)
   - **Swagger UI (OpenAPI 3.0)** : [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
   - **PostgreSQL DB** : `localhost:5432` (`ecourtier_db` / `ecourtier_user` / `ecourtier_secure_pass_2026`)

---

## 🛠️ Démarrage Local Sans Docker (Optionnel)

### 1. Base de données PostgreSQL
Lancez l'instance PostgreSQL via Docker :
```bash
docker compose up db -d
```

### 2. Backend Spring Boot
```bash
cd backend
./mvnw spring-boot:run
```

### 3. Frontend React Vite
```bash
cd frontend
npm install
npm run dev
```

---

## 🎨 Design System & Charte Graphique E-Courtier

- **Terre Cuite (Terracotta)** : `#C85A32` *(Chaleur du bâti sénégalais)*
- **Vert Sahel (Sahel Green)** : `#1D3E35` *(Sérénité, sécurité & confiance)*
- **Sable Chaud (Warm Sand)** : `#FDFBF7` *(Blanc cassé naturel pour fonds & surfaces)*
- **Ocre Solaire (Sun Gold)** : `#E09F3E` *(Mises en avant & badges dynamiques)*
- **Anthracite Chaud** : `#1E232A` *(Texte haut contraste)*
- **Typographies** : `Plus Jakarta Sans` (En-têtes) & `Inter` (Corps)
