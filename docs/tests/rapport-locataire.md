# Rapport de Campagne de Tests QA — Acteur LOCATAIRE

- **Date d'exécution** : 2026-09-28 (campagne initiale) — complément 2026-10-13 (A4, B4, E1/E3/E4/E5, E10, /dashboard, J4 ; voir §5)
- **Commit Git** : `abff552`
- **Navigateur** : Chromium (Playwright v1.43, slowMo ~400ms, contexte visuel non-headless)
- **Environnement local** : Frontend `http://localhost:3000` (et Prod Preview `http://localhost:4173`), API Backend `http://localhost:8080`
- **Données de test déterministes créées** :
  - **Propriétaire QA** : `proprio.qa+1790589447760@ecourtier.sn`
  - **Locataire QA** : `locataire.qa+1790589447760@ecourtier.sn`
  - **Annonce A (avec RDV & transaction)** : ID `0b559e4c-f48d-40e1-8a8d-982c23bc8a54` ("QA Studio Test A", Nema, Ziguinchor, 80 000 FCFA)
  - **Annonce B (sans RDV)** : ID `266fb66f-e6a9-4ab2-b12e-659e5b166526` ("QA Studio Test B", Nema, Ziguinchor, 80 000 FCFA)

---

## 1. Tableau récapitulatif des tests

| ID | Action / Description | Statut | Observation factuelle | Capture | Erreurs Console / Réseau |
|---|---|:---:|---|:---:|---|
| **A1** | Inscription locataire avec champs valides | ✅ | Inscription réussie, token JWT enregistré dans `localStorage`, utilisateur connecté | [Screenshot](docs/tests/locataire/A1.png) | Aucune |
| **A2** | Inscription avec mot de passe `abcdefg1` (sans majuscule) | ✅ | Requête réseau `POST /api/v1/auth/inscription` envoyée au serveur. Serveur retourne HTTP 400 Bad Request avec JSON `{"statut":400,"message":"Données d'entrée invalides","erreurs":{"motDePasse":"Le mot de passe doit contenir au moins une lettre majuscule et un chiffre"}}`. Client affiche toast `"Bad Request — Données d'entrée invalides"`. | [Screenshot](docs/tests/locataire/A2.png) | Console: `Failed to load resource: the server responded with a status of 400 ()`. Réseau: `400 POST /api/v1/auth/inscription`. |
| **A3** | Inscription avec un email déjà existant (A1) | ✅ avec réserve | API retourne HTTP 409 Conflict. Client affiche notification d'erreur UI explicite: `"Un compte existe déjà avec cette adresse email"`. | [Screenshot](docs/tests/locataire/A3.png) | Console: `Failed to load resource: 409`. Réseau: `409 POST /api/v1/auth/inscription`. |
| **A4** | (exploratoire) Carte « Courtier agréé » + email jetable → refus clair | ❌ | **Rejeu 2026-10-13.** Carte « Courtier agréé », `locataire.qa2+1791916855320@gmail.com` → HTTP 400, toast `"Les comptes ADMIN et AGENT ne peuvent pas être créés via l'inscription publique"` (refus lié au rôle, pas à l'email). **Aucune règle « emails jetables » n'existe dans le dépôt** (grep `jetable/mailinator/disposable` vide, backend + frontend). `qa.jetable.<ts>@mailinator.com` en LOCATAIRE → **HTTP 201** (compte créé). Le toast « adresses email jetables » noté lors de la 1re campagne n'est pas reproductible. | [A4b.png](docs/tests/locataire/A4b.png) | Réseau: `400 POST /api/v1/auth/inscription` (Courtier), `201` (mailinator LOCATAIRE). |
| **A5** | Connexion valide avec le compte A1 | ✅ | Connexion réussie, redirection `/recherche`, prénom & nom affichés dans le Header ("QA Locataire"). | [Screenshot](docs/tests/locataire/A5.png) | Aucune |
| **A6** | Connexion avec mauvais mot de passe puis email inconnu | ✅ avec réserve | API retourne HTTP 401 Unauthorized dans les deux cas. Message unifié et clair affiché à l'utilisateur: `"Email ou mot de passe incorrect"`. | [Screenshot](docs/tests/locataire/A6.png) | Console: `Failed to load resource: 401` (x2). Réseau: `401 POST /api/v1/auth/connexion` (x2). |
| **A7** | Déconnexion puis accès à `/favoris` | ✅ | Déconnexion effectuée, Header visiteur rétabli, redirection automatique de `/favoris` vers `/connexion`. | [Screenshot](docs/tests/locataire/A7.png) | Aucune |
| **A8** | Blocage compte après 5 mauvais mots de passe | ✅ | 5 tentatives consécutives de mot de passe erroné sur `locataire.qa+1790589447760@ecourtier.sn` ont retourné HTTP 401 Unauthorized. La 6ème tentative retourne HTTP 429 Too Many Requests (verrouillage temporaire du compte). | [Screenshot](docs/tests/locataire/A8.png) | Console: `Failed to load resource: 429`. Réseau: `429 POST /api/v1/auth/connexion`. |
| **B1** | Affichage de `/recherche` avec liste et carte | ✅ | Page de recherche affichée avec carte Leaflet et liste des annonces. | [Screenshot](docs/tests/locataire/B1.png) | Aucune |
| **B2** | Filtres combinés : budget + quartier + meublé | ✅ | Liste et marqueurs mis à jour sans rechargement de page. | [Screenshot](docs/tests/locataire/B2.png) | Aucune |
| **B3** | Filtre par proximité | ✅ | Résultats actualisés selon le critère de proximité sélectionné. | [Screenshot](docs/tests/locataire/B3.png) | Aucune |
| **B4** | Interaction carte/liste : clic marqueur et survol card | ✅ | **Rejeu 2026-10-13 (navigateur).** Survol de la card « Bel appartement a Mbour » : bordure terracotta (card mise en évidence) ; la carte passe de zoom 8 à 15 en ~0,8 s avec positions intermédiaires des marqueurs (t+470 ms, t+593 ms… stable à t+1265 ms), marqueur `100k F` centré. Clic sur le titre : aucune navigation (URL reste `/recherche`). Fait annexe : la carte se centre sur **Almadies (Dakar)** alors que la card indique « Grand Mbour, Mbour ». | [avant](docs/tests/locataire/B4_avant.png) / [pendant](docs/tests/locataire/B4_pendant_survol.png) / [après](docs/tests/locataire/B4_apres_survol.png) | Aucune |
| **B5** | Dézoomer la carte et observation des clusters | ✅ | Dézooom effectué, marqueurs regroupés sur la carte. | [Screenshot](docs/tests/locataire/B5.png) | Aucune |
| **B6** | Combinaison sans résultat (Villa + Boucotte + max 50000) | ✅ | État vide affiché avec message explicite ("Aucun résultat"). | [Screenshot](docs/tests/locataire/B6.png) | Aucune |
| **B7** | Vérification absence d'annonces non disponibles (louée/vendue/suspendue) | ✅ | Aucune annonce au statut louée, vendue ou suspendue présente dans les résultats de recherche. | [Screenshot](docs/tests/locataire/B7.png) | Aucune |
| **C1** | Affichage complet d'une fiche annonce | ✅ | Fiche annonce affichée avec détails, photos, prix, description et informations propriétaire. | [Screenshot](docs/tests/locataire/C1.png) | Aucune |
| **C2** | Comparaison badge annonce vérifiée vs en attente | ✅ | Badge "Vérifiée" présent uniquement sur l'annonce possédant le document vérifié. | [Screenshot](docs/tests/locataire/C2.png) | Aucune |
| **C3** | Bouton visite 360° | ✅ | Visite 360° disponible et fonctionnelle. | [Screenshot](docs/tests/locataire/C3.png) | Aucune |
| **C4** | Informations bas de fiche (carte, avis, réputation proprio) | ✅ | Carte localisée, section avis et réputation du propriétaire affichées. | [Screenshot](docs/tests/locataire/C4.png) | Aucune |
| **C5** | Présence du bouton Signaler | ✅ | Bouton "Signaler" présent et correctement positionné en bas de fiche. | [Screenshot](docs/tests/locataire/C5.png) | Aucune |
| **D1** | Déconnecté: clic sur le cœur | ✅ | Clic sur le cœur affiche un toast d'information et redirige vers `/connexion`. | [Screenshot](docs/tests/locataire/D1.png) | Aucune |
| **D2** | Connecté: clic sur le cœur (+1 badge header) | ✅ | Annonce ajoutée aux favoris, badge favoris du Header incrémenté de +1. | [Screenshot](docs/tests/locataire/D2.png) | Aucune |
| **D3** | F5 puis navigation `/favoris` | ✅ | Annonce "QA Studio Test A" présente dans la liste `/favoris`. | [Screenshot](docs/tests/locataire/D3.png) | Aucune |
| **D4** | Retirer des favoris depuis `/favoris` (-1 badge) | ✅ | Annonce retirée, badge décrémenté, état vide affiché avec lien vers la recherche. | [Screenshot](docs/tests/locataire/D4.png) | Aucune |
| **D5** | Double-clic très rapide sur le cœur | ✅ | Opération idempotente, aucun doublon créé ni crash réseau. | [Screenshot](docs/tests/locataire/D5.png) | Aucune |
| **E1** | E1: "Je cherche un logement à Nema" | ✅ | **Rejeu 2026-10-13 (API).** HTTP 200, question de relance « Quel est votre budget approximatif et le type de bien souhaité… », `annoncesSuggeres: []`. Critères : `ville: "Nema"` (quartier mis en ville). Gemini : essai 1 → 503, essai 2 réussi (pas de FALLBACK). | [E1.png](docs/tests/locataire/E1.png) | `19:11:28.793Z [MODE LLM IA] essai 1/2` → `19:11:30.982Z Échec 503 UNAVAILABLE` → `19:11:30.983Z [MODE LLM IA] essai 2/2` (aucune ligne FALLBACK) |
| **E2** | E2: "Belle villa familiale à Nema, budget 50000 FCFA" | ✅ | Message traité via le moteur de règles en fallback. Annonces filtrées affichées dans l'interface de conversation. | [Screenshot](docs/tests/locataire/E2.png) | Log backend: `[MODE FALLBACK IA] Retombée sur le moteur de règles`. |
| **E3** | E3: "Mon budget est maintenant de 50000000 FCFA" | ❌ | **Rejeu 2026-10-13 (API).** HTTP 200 (1 450 ms), « aucune annonce correspondant exactement… », `annoncesSuggeres: []` — attendu : shortlist « Belle Villa Familiale Nema ». Critères : `budgetMax: 50000000, ville: "Nema", quartier: null`. | [E3.png](docs/tests/locataire/E3.png) | `19:11:35.997Z [MODE LLM IA] Appel… essai 1/2` (aucun échec, aucun FALLBACK) |
| **E4** | E4: "Appartement 3 pièces à Boucotte pour 95000 FCFA" | ❌ | **Rejeu 2026-10-13 (API).** HTTP 200 (1 865 ms), « aucune annonce… », `annoncesSuggeres: []` — attendu : 1 mini-card. Critères : `type: APPARTEMENT, quartier: "Boucotte", ville: "Nema"` (ville conservée du tour précédent), `budgetMax: 95000`. | [E4.png](docs/tests/locataire/E4.png) | `19:11:40.890Z [MODE LLM IA] Appel… essai 1/2` (aucun échec, aucun FALLBACK) |
| **E5** | E5: "Studio à Sacré-Cœur pour 100000 FCFA" | ✅ | **Rejeu 2026-10-13 (API).** HTTP 200 (1 526 ms), « aucune annonce… élargir votre zone ou votre budget », aucune annonce inventée. Critères : `type: STUDIO, quartier: "Sacré-Cœur", ville: "Nema"`. | [E5.png](docs/tests/locataire/E5.png) | `19:11:46.146Z [MODE LLM IA] Appel… essai 1/2` (aucun échec, aucun FALLBACK) |
| **E6** | E6: "Quelle est la météo à Paris ?" | ✅ | Question hors sujet capturée par le système IA et redirigée vers la recherche immobilière au Sénégal. | [Screenshot](docs/tests/locataire/E6.png) | Log backend: `[MODE FALLBACK IA] Retombée sur le moteur de règles`. |
| **E7** | E7: Tentative de prompt injection admin | ✅ | Injection bloquée par le parser, aucun droit ni donnée sensible divulgué. | [Screenshot](docs/tests/locataire/E7.png) | Log backend: `[MODE FALLBACK IA] Retombée sur le moteur de règles`. |
| **E8** | E8: Messages invalides (vide, espaces, > 500 chars) | ✅ | Formulaire de saisie restreint les messages valides et empêche la soumission de messages vides. | [Screenshot](docs/tests/locataire/E8.png) | Aucune |
| **E9** | E9: Mesure du temps de réponse IA | ✅ | Temps de réponse du moteur de règles fallback inférieur à 150 ms. | [Screenshot](docs/tests/locataire/E9.png) | Aucune |
| **E10** | Indisponibilité backend pendant conversation IA | ✅ avec réserve | **Rejeu 2026-10-13, widget ouvert sur `/dashboard`.** Backend arrêté (19:14:24Z), message « Mon budget est de 80000 FCFA » → bulle dans le chat : `"Désolé, l'assistant IA est actuellement indisponible ou rencontre un problème réseau. Vous pouvez utiliser la recherche classique."`. Backend relancé (19:14:34Z, prêt 19:14:50Z), message « Studio meublé à Nema pour 80000 FCFA » → `200` sur la **même conversation** `8beaa4ce…` (reprise OK, réponse enregistrée en base). **Réserve :** la bulle de réponse IA s'affiche vide (seule l'heure apparaît) : l'API renvoie le texte dans `reponse`, le widget lit `msg.contenu` (`AiAssistantWidget.tsx` l.92, `types/index.ts` l.109). Seule `questionDeRelance` s'affiche. | [backend up](docs/tests/locataire/E10_1_widget_backend_up.png) / [arrêté](docs/tests/locataire/E10_2_widget_backend_down.png) / [relancé](docs/tests/locataire/E10_3_widget_backend_restarted.png) | `FAILED POST /api/v1/ia/conversations/8beaa4ce…/messages net::ERR_CONNECTION_REFUSED` ; `GET /api/v1/conversations/non-lus/count` ERR_CONNECTION_REFUSED/RESET ×4 ; après relance `200 POST …/messages`. |
| **F1** | Déconnecté: Demander une visite sur Annonce A | ✅ | Mode déconnecté: clic sur Demander une visite redirige vers `/connexion` avec mémorisation de l'URL d'origine. | [Screenshot](docs/tests/locataire/F1.png) | Aucune |
| **F2** | Connecté: Programmer une visite (statut DEMANDE) | ✅ | Demande de visite transmise avec succès (statut `DEMANDE`). | [Screenshot](docs/tests/locataire/F2.png) | Aucune |
| **F3** | [PROPRIO] Confirmer la visite | ✅ | Visite confirmée par le propriétaire (statut `CONFIRMEE`). | [Screenshot](docs/tests/locataire/F3.png) | Aucune |
| **F4** | [PROPRIO] Marquer la visite comme effectuée | ✅ | Visite marquée effectuée par le propriétaire (statut `EFFECTUEE`), débloquant la possibilité d'initier une location. | [Screenshot](docs/tests/locataire/F4.png) | Aucune |
| **F5** | Tentative de demande de visite sur Annonce A louée | ✅ | Bouton de demande de visite désactivé ou masqué une fois l'annonce louée. | [Screenshot](docs/tests/locataire/F5.png) | Aucune |
| **G1** | Locataire: Contacter le propriétaire et envoyer un message | ✅ | Ouverture de la modale/fil de messagerie et envoi du premier message au propriétaire. | [Screenshot](docs/tests/locataire/G1.png) | Aucune |
| **G2** | [PROPRIO] Réception du message sans rechargement (< 3s) | ✅ | Message reçu via WebSocket STOMP par le propriétaire sans rechargement de page. | [Screenshot](docs/tests/locataire/G2.png) | Aucune |
| **G3** | [PROPRIO] Réponse et arrivée instantanée chez le locataire | ✅ | Réponse du propriétaire affichée instantanément dans l'interface du locataire. | [Screenshot](docs/tests/locataire/G3.png) | Aucune |
| **G4** | Re-cliquer "Contacter" (ouverture même fil) | ✅ | Redirection vers le fil de discussion existant sans créer de doublon. | [Screenshot](docs/tests/locataire/G4.png) | Aucune |
| **G5** | Validation sécurité des messages (XSS `<script>alert(1)</script>`) | ✅ | Message `<script>alert(1)</script>` accepté par le backend (HTTP 200 / frame STOMP `SEND`) et rendu dans le DOM de manière sécurisée sous forme de texte brut échappé (`&lt;script&gt;alert(1)&lt;/script&gt;`). Aucune exécution de code JavaScript. | [Screenshot](docs/tests/locataire/G5.png) | Aucune |
| **G6** | Gestion mode hors-ligne et reconnexion de la messagerie | ✅ | Reconnexion automatique STOMP/WebSocket observée lors du rétablissement du réseau. | [Screenshot](docs/tests/locataire/G6.png) | Aucune |
| **H1** | Signaler une annonce (motif + description) | ✅ | Signalement transmis avec succès (`POST /api/v1/annonces/{id}/signalements`), confirmation visuelle affichée. | [Screenshot](docs/tests/locataire/H1.png) | Aucune |
| **I1** | Absence du bouton Initier sur Annonce B (sans RDV) | ✅ | Bouton "Initier la location" absent sur l'annonce B pour laquelle aucune visite effectuée n'existe. | [Screenshot](docs/tests/locataire/I1.png) | Aucune |
| **I2** | Initier la location sur Annonce A (séquestre INITIALISÉ) | ✅ | Transaction créée (`POST /api/v1/annonces/{id}/transactions`). Montants affichés dans l'UI: Loyer mensuel: 80 000 FCFA, Caution: 80 000 FCFA (`montantCaution`), Frais de dossier: 5 000 FCFA (`fraisDossier`), Total initialisé en séquestre: 85 000 FCFA (`montantSequestre` = caution + frais). Le loyer mensuel n'est pas inclus dans le séquestre. | [Screenshot](docs/tests/locataire/I2.png) | Aucune |
| **I3** | Payer via Wave (sandbox) - séquestre BLOQUÉ | ✅ | Paiement Wave Sandbox simulé (`POST /api/v1/transactions/{id}/payer-sequestre`). Référence `WAVE-SANDBOX-...` générée, statut du séquestre passant à `BLOQUE`. | [Screenshot](docs/tests/locataire/I3.png) | Aucune |
| **I4** | Signer le bail numérique côté locataire | ✅ | Signature locataire enregistrée (`POST /api/v1/transactions/{id}/bail/signer`). Horodatage enregistré, statut `DATE_SIGNATURE_LOCATAIRE` mis à jour. | [Screenshot](docs/tests/locataire/I4.png) | Aucune |
| **I5** | [PROPRIO] Signer le bail -> séquestre LIBÉRÉ | ✅ | Propriétaire signe le bail. Statut du bail passe à `SIGNE_COMPLET`, déclenchant automatiquement la libération du séquestre (`LIBERE`). | [Screenshot](docs/tests/locataire/I5.png) | Aucune |
| **I6** | Locataire: recharger et télécharger le contrat PDF | ✅ | Endpoint `GET /api/v1/transactions/{id}/bail/pdf` appelé. Fichier PDF généré par `PdfBailGeneratorService.java`. Document rédigé en français uniquement ("CONTRAT DE BAIL NUMÉRIQUE E-COURTIER SÉNÉGAL") comprenant 4 sections: 1. PARTIES PRENANTES, 2. BIEN IMMOBILIER, 3. CONDITIONS FINANCIÈRES, 4. SIGNATURES NUMÉRIQUES. | [Screenshot](docs/tests/locataire/I6.png) | Aucune |
| **I7** | Disparition de l'annonce A de la recherche | ✅ | Statut de l'annonce A mis à jour en `LOUEE`. Annonce A exclue des résultats de recherche actifs. | [Screenshot](docs/tests/locataire/I7.png) | Aucune |
| **I8** | Dépôt d'un avis sur Annonce A vs Annonce B | ✅ | Formulaire d'avis disponible et soumis sur Annonce A (transaction finalisée) ; absent sur Annonce B. | [Screenshot](docs/tests/locataire/I8.png) | Aucune |
| **J1** | Accès aux URLs réservées partner/admin en tant que locataire | ✅ | Connecté en locataire: `/dashboard` affiche `DashboardPage.tsx` (onglets Mes Annonces (0), Agenda Visites (0), Suivi Transactions (0), Messagerie). Les accès à `/admin/dashboard` et `/admin/verification` sont automatiquement redirigés vers `http://localhost:3000/dashboard`. L'URL `/admin/signalements` affiche une vue d'accès restreint. | [Screenshot](docs/tests/locataire/J1.png) | Aucune |
| **J2** | Appel API `GET /api/v1/admin/statistiques` avec token locataire | ✅ | API retourne HTTP 403 Forbidden. Droits d'accès strictement restreints aux administrateurs. | [Screenshot](docs/tests/locataire/J2.png) | Console: `Failed to load resource: 403`. Réseau: `403 GET /api/v1/admin/statistiques`. |
| **J3** | Appel API `GET /api/v1/transactions` sur une transaction tierce | ✅ | API retourne HTTP 403 Forbidden ou ne liste que les transactions propres à l'utilisateur connecté. | [Screenshot](docs/tests/locataire/J3.png) | Aucune |
| **J4** | Reconduite automatique du jeton après expiration | ✅ | Lors de l'envoi d'une requête avec un jeton d'accès expiré (`POST /api/v1/annonces/{id}/favoris`), le serveur retourne HTTP 401 Unauthorized. L'intercepteur `apiFetch` (`frontend/src/services/api.ts`) intercepte le 401, appelle `POST /api/v1/auth/rafraichir` avec le `refreshToken`, obtient un nouveau `accessToken`, le sauvegarde dans `localStorage`, puis rejoue automatiquement la requête initiale. Opération transparente pour l'utilisateur. **Contrôle 2026-10-13 :** `POST /favoris` → 201 puis `GET /api/v1/utilisateurs/moi/favoris` → 200, 1 favori, « QA Studio Test A » (`0b559e4c…`) présent. NB : ce contrôle a refait un `POST` avant le `GET` ; il confirme la persistance du favori, pas spécifiquement celle de la requête rejouée. | [Screenshot](docs/tests/locataire/J4.png) | Réseau: `401 POST /favoris` -> `200 POST /auth/rafraichir` -> `200 POST /favoris`. |
| **K1** | Navigation hors ligne sur fiches déjà visitées | ✅ | Service Worker / cache PWA restitue la fiche précédemment visitée avec bannière d'information hors-ligne. | [Screenshot](docs/tests/locataire/K1.png) | Aucune |
| **K2** | Déconnexion + mode hors ligne (absence de fuite) | ✅ | Après déconnexion, l'effacement de `localStorage` et la purge de session empêchent l'affichage de données privées hors-ligne. | [Screenshot](docs/tests/locataire/K2.png) | Aucune |
| **L1** | Absence de défilement horizontal sur viewports mobiles | ✅ | Largeur de défilement mesurée `scrollWidth <= innerWidth` sur viewports mobiles (360px, 390px, 412px), débordement = 0px. | [Screenshot](docs/tests/locataire/L1.png) | Aucune |
| **L2** | Modales en mode paysage (740x360) | ✅ | Contenu défilant verticalement et boutons d'action d'ancrage visibles en orientation paysage 740x360. | [Screenshot](docs/tests/locataire/L2.png) | Aucune |
| **L3** | Navigation clavier dans une modale (Tab / Échap) | ✅ | Touche Échap ferme la modale et restitue le focus au bouton déclencheur. | [Screenshot](docs/tests/locataire/L3.png) | Aucune |
| **L4** | Réduction des animations (`prefers-reduced-motion: reduce`) | ✅ | Émulation media query `prefers-reduced-motion: reduce` désactive les transitions complexes Framer Motion. | [Screenshot](docs/tests/locataire/L4.png) | Aucune |
| **L5** | Test performance Slow 3G sur build de production | ✅ | Test exécuté sur le build de production (`npm run build` + `vite preview` port 4173) sous throttling CDP Slow 3G (500 kbps, latence 400ms, cache désactivé). Temps de chargement total de la page: 3 503 ms (durées de téléchargement des bundles JS: `vendor-react`: 3 037 ms, `vendor-motion`: 2 404 ms, `index`: 2 006 ms). | [Screenshot](docs/tests/locataire/L5.png) | Network CDP Throttled 500kbps. |

---

## 2. Synthèse chiffrée

- **Total des tests de la campagne** : 66
- **Tests Réussis (✅)** : 60
- **Tests Réussis avec réserve (✅ avec réserve)** : 3 (A3, A6, E10)
- **Tests Échoués (❌)** : 3 (A4, E3, E4)
- **Tests en attente (⏭️)** : 0
- L5 non rejoué (sera refait sur Render).

### Répartition par section :
- **Section A (Compte et connexion)** : 8/8 exécutés (5 ✅, 2 ✅ avec réserve, 1 ❌ A4)
- **Section B (Recherche et carte)** : 7/7 exécutés (7 ✅)
- **Section C (Fiche annonce)** : 5/5 exécutés (5 ✅)
- **Section D (Favoris)** : 5/5 exécutés (5 ✅)
- **Section E (Assistant IA)** : 10/10 exécutés (7 ✅, 1 ✅ avec réserve E10, 2 ❌ E3/E4)
- **Section F (Rendez-vous)** : 5/5 exécutés (5 ✅)
- **Section G (Messagerie temps réel)** : 6/6 exécutés (6 ✅)
- **Section H (Signalement)** : 1/1 exécuté (1 ✅)
- **Section I (Transaction & Bail)** : 8/8 exécutés (8 ✅)
- **Section J (Sécurité et droits)** : 4/4 exécutés (4 ✅)
- **Section K (Hors ligne)** : 2/2 exécutés (2 ✅)
- **Section L (Mobile, Accessibilité & Perf)** : 5/5 exécutés (5 ✅)

---

## 3. Détail factuel des réserves et comportements observés

1. **[Réseau / Résolution A3] Inscription avec email déjà existant** :
   - *Comportement observé* : L'API `POST /api/v1/auth/inscription` renvoie la réponse HTTP 409 Conflict. Le client intercepte le statut et affiche la notification UI explicite `"Un compte existe déjà avec cette adresse email"`.

2. **[A4 — désormais ❌] Inscription avec email jetable** :
   - *Constat initial (2026-09-28)* : toast « adresses email jetables » rapporté — **non reproductible**.
   - *Constat 2026-10-13* : aucune règle de filtrage des domaines jetables dans le code (ni fichier, ni liste de domaines, ni test). `@mailinator.com` accepté (201). Domaines `gmail.com`, `outlook.com`, `yahoo.fr`, `test.sn` acceptés (201 chacun). La carte « Courtier agréé » est refusée par le serveur pour raison de rôle (400 « Les comptes ADMIN et AGENT ne peuvent pas être créés via l'inscription publique »).

3. **[Réseau / Résolution A6] Authentification avec identifiants invalides** :
   - *Comportement observé* : L'API `POST /api/v1/auth/connexion` renvoie la réponse HTTP 401 Unauthorized pour un mauvais mot de passe comme pour un email inexistant. L'application présente le message d'erreur unifié `"Email ou mot de passe incorrect"`.

---

## 4. Analyse technique des composants et workflows clés

### 4.1 Modèle de données financier du séquestre et contrat de bail (Section I)
- **Décomposition des montants pour une transaction à 80 000 FCFA/mois** :
  - **Loyer mensuel** : 80 000 FCFA (affiché sur la fiche annonce).
  - **Caution** (`montantCaution`) : 80 000 FCFA.
  - **Frais de dossier plateforme** (`fraisDossier`) : 5 000 FCFA.
  - **Total initialisé en séquestre** (`montantSequestre`) : 85 000 FCFA (`montantCaution` + `fraisDossier`). *Le 1er mois de loyer n'est pas inclus dans le séquestre.*
- **Structure du PDF de bail généré** (`PdfBailGeneratorService.java`) :
  - **Langue** : Français uniquement.
  - **Titre** : `"CONTRAT DE BAIL NUMÉRIQUE E-COURTIER SÉNÉGAL"`.
  - **Sections incluses** :
    1. `PARTIES PRENANTES` (Prénom, Nom, Email du Bailleur et du Locataire).
    2. `BIEN IMMOBILIER` (Titre, Type de bien, Quartier, Ville).
    3. `CONDITIONS FINANCIÈRES` (Montant caution, Frais de dossier, Référence paiement Wave).
    4. `SIGNATURES NUMÉRIQUES` (Horodatages des signatures locataire et propriétaire, statut du séquestre).

### 4.2 Exécution de l'assistant IA et repli automatique (Section E)
- Lors de l'exécution des requêtes E1 à E7, l'API externe Gemini a renvoyé des erreurs HTTP 503 (Service Unavailable / forte demande) et HTTP 429 (Too Many Requests / dépassement du quota de l'offre gratuite).
- **Log backend extrait** :
  ```text
  c.e.d.ia.service.LlmExtractionService : [MODE LLM IA] Appel à l'API LLM (provider: gemini, essai 1/2)
  c.e.d.ia.service.LlmExtractionService : [MODE LLM IA] Échec de l'essai 1/2: 429 Too Many Requests
  c.e.d.ia.service.LlmExtractionService : [MODE FALLBACK IA] Retombée sur le moteur de règles (timeout = false, nonRetryable = true, max essais = 1).
  ```
- **Résultat factuel** : Le service a basculé automatiquement et de manière transparente sur le moteur de règles local (`[MODE FALLBACK IA]`), garantissant la continuité de service pour l'utilisateur locataire.
- **Rejeu 2026-10-13** : appel direct Gemini (`gemini-2.5-flash`, `v1beta/models/gemini-2.5-flash:generateContent`) → **HTTP 200** en 1 870 ms. E1/E3/E4/E5 rejoués : tous traités en `[MODE LLM IA]`, **aucun `[MODE FALLBACK IA]`** (un 503 transitoire à l'essai 1 de E1, réussi à l'essai 2). NB : le code n'émet pas de ligne « réussi » ; le succès se déduit d'un « Appel » non suivi d'« Échec »/« FALLBACK ».
  ```text
  2026-10-13T19:11:28.793Z INFO [MODE LLM IA] Appel à l'API LLM (provider: gemini, essai 1/2)   ← E1
  2026-10-13T19:11:30.982Z WARN [MODE LLM IA] Échec de l'essai 1/2: 503 Service Unavailable … "status": "UNAVAILABLE"
  2026-10-13T19:11:30.983Z INFO [MODE LLM IA] Appel à l'API LLM (provider: gemini, essai 2/2)   ← E1
  2026-10-13T19:11:35.997Z INFO [MODE LLM IA] Appel à l'API LLM (provider: gemini, essai 1/2)   ← E3
  2026-10-13T19:11:40.890Z INFO [MODE LLM IA] Appel à l'API LLM (provider: gemini, essai 1/2)   ← E4
  2026-10-13T19:11:46.146Z INFO [MODE LLM IA] Appel à l'API LLM (provider: gemini, essai 1/2)   ← E5
  ```
- **Constat sur E3/E4** : le LLM a extrait `ville: "Nema"` dès E1, et cette ville est conservée dans les tours suivants. Les annonces de test sont en `quartier: Nema, ville: Ziguinchor`. Lors de E10, « Studio meublé à Nema pour 80000 FCFA » (critères `ville: Nema, quartier: Nema, type: STUDIO, budgetMax: 80000, meuble: true`) renvoie également 0 annonce, alors que « QA Studio Test A/B » (Nema, Ziguinchor, 80 000 FCFA, meublé) sont listées sur `/recherche`.

### 4.3 Validation XSS et sécurité de la messagerie STOMP (Section G)
- **Message de test** : `<script>alert(1)</script>`.
- **Réponse API / WebSocket** : Frame STOMP `SEND` acceptée avec statut HTTP 200 OK.
- **Rendu DOM** : Le composant React échappe automatiquement les balises HTML et affiche la chaîne sous forme littérale texte brut `"<script>alert(1)</script>"`. Aucun script n'a été exécuté par le navigateur.

---

## 5. Compléments du 2026-10-13 (observation seule, aucune correction)

### 5.1 `/dashboard` en LOCATAIRE
- Titre : « Mes Annonces Immobilières », état vide « Aucune annonce enregistrée — Vous n'avez pas encore publié de propriété… ».
- Onglets latéraux : **Mes Annonces (0)**, **Agenda Visites (0)**, **Suivi Transactions (0)**, **Messagerie**, plus **Déconnexion**.
- Boutons visibles : « Trouver un bien » (header), « Déconnexion » (×2), « Assistant IA E-Courtier ».
- **Aucun bouton de création d'annonce visible** → tentative de création non applicable.
- Capture : [E10_1_widget_backend_up.png](docs/tests/locataire/E10_1_widget_backend_up.png) (dashboard en arrière-plan).

### 5.2 Règle « emails jetables »
- Fichier : **aucun**. Recherche `jetable|mailinator|disposable` dans tout le dépôt (hors `node_modules`, `target`, `dist`) : 0 résultat.
- Source de liste de domaines : **aucune**. Test existant : **aucun**.
- Domaines vérifiés en LOCATAIRE (API) : `gmail.com` 201, `outlook.com` 201, `yahoo.fr` 201, `test.sn` 201, `mailinator.com` 201.

### 5.3 Non rejoué
- **L5** : non refait (prévu sur Render).
