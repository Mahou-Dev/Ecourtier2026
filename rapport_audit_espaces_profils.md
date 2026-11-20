# RAPPORT D'AUDIT COMPLET & DIAGNOSTIC DES ESPACES PROFILS — E-COURTIER SÉNÉGAL

**Mission :** Mission 1 — Audit architectural, ergonomique, UI/UX, sécurité & qualité des espaces profils  
**Périmètre :** Parcours compte, authentification, favoris, espace personnel / partenaire (`/dashboard`), messagerie, transactions/séquestres et interfaces liées (vues admin utilisateurs / modération / profil public sur fiche annonce)  
**Branche auditée :** `feature/frontend-redesign-premium-saas` (`HEAD 304e38c8c33c6eedef7677c0978c219bf6f324bd`)  
**Statut d'exécution :** Audit 100 % en lecture seule — zéro modification de code applicatif, zéro modification backend, zéro intervention Wave, zéro opération Git destructive.

---

## 1. Résumé exécutif

### Constat global sans complaisance

Alors que la vitrine d'accueil (`/`), la recherche (`/recherche`) et la fiche annonce (`/annonce/:id`) ont atteint un niveau d'exécution solide dans l'identité visuelle **Sahel Premium PropTech**, les **espaces profils et applicatifs privés** accusent un retard architectural et ergonomique majeur. Ils donnent aujourd'hui le sentiment d'un assemblage de modules techniques juxtaposés autour d'un composant unique fourre-tout (`DashboardPage.tsx`, 828 lignes), plutôt que d'espaces personnels et professionnels cohérents.

L'audit complet du code source et la campagne de mesures Playwright menée sur les 6 viewports de référence (`320×568`, `390×844`, `768×1024`, `1024×768`, `1280×720`, `1440×900`) mettent en évidence **trois ruptures bloquantes sur tablette (`768×1024`)**, des **incohérences de données et de rôles**, une **dette graphique et de composants critiques** ainsi que des **manques fonctionnels majeurs** vis-à-vis des standards SaaS/PropTech.

### Forces existantes confirmées

1. **Socle d'authentification et sécurité JWT robuste côté serveur & client :** rotation stricte des refresh tokens (`AuthService.java`), révocation de famille de sessions sur changement de mot de passe/rôle ou désactivation (`UtilisateurManagementService.java`), persistance des tokens en `sessionStorage` (`ecourtier-auth`), file d'attente de rafraîchissement unique sans boucle sur `403`, et nettoyage de session (`clearClientSessionData()` dans `useAuthStore.ts`).
2. **Exclusion PWA stricte des données privées :** la configuration Workbox (`frontend/vite.config.ts`) exclut les chunks `Admin*`, `DashboardPage*`, `MonComptePage*` et restreint `runtimeCaching` aux seules requêtes GET anonymes publiques sans credentials.
3. **Chaîne métier transactionnelle réelle :** la modale `TransactionDetailsModal.tsx` gère de bout en bout les cycles `LOCATION` et `VENTE`, la consignation, la signature bipartite du bail, les erreurs/régénérations PDF (`statutDocumentBail`) et les remboursements admin.
4. **Création encadrée des comptes agents (`AGENT`) :** séparation claire entre inscription publique (`LOCATAIRE | PROPRIETAIRE`) et création administrateur avec mot de passe temporaire affiché une seule fois (`AdminUtilisateursPage.tsx`).

### Faiblesses critiques (P0 / P1)

1. **Ruptures de mise en page critiques à `768×1024` (tablette portrait) :**
   - `DashboardPage.tsx` onglet `ANNONCES` : les titres d'annonces s'effondrent en ruban vertical d'une lettre par ligne (`scrollHeight = 3268 px` pour 3 cartes).
   - `ConversationsTab.tsx` dans `DashboardPage` : la zone de discussion est écrasée à ~128 px de large, les bulles s'affichent à 2–4 caractères par ligne et le bouton `Envoyer` est rogné par `overflow-hidden`.
   - `AdminUtilisateursPage.tsx` : le tableau `<table>` (`924 px` de large) déborde hors du conteneur `overflow-hidden` de `768 px`, rendant la colonne `Actions` (`Changer le rôle`, `Désactiver`) totalement inaccessible.
2. **Messagerie mobile cassée sur petit écran (`320×568`) :** la zone de chat `min-h-[480px]` combinée aux deux en-têtes (`Header` + barre sombre `DashboardPage`) pousse la barre de saisie en bas de page (`scrollHeight = 909 px`). De plus, `ConversationsTab.tsx` utilise une palette **Slate/Amber/Dark** étrangère au design system Sahel et ignore silencieusement les erreurs WebSocket renvoyées par `useConversationSocket.ts` (`error` jamais déstructuré).
3. **Absence de garde de route centrale et fragilité runtime :** `App.tsx` n'encapsule aucune route privée dans un `<ProtectedRoute>`. La page `/dashboard` redirige vers `/connexion` sans transmettre `state.from` (`AdminDashboardPage`, `AdminUtilisateursPage`, `AdminVerificationPage`, `AdminTransactionsPage` font de même). En cas d'erreur de rendu (absence d'`ErrorBoundary` global et d'assertion `Array.isArray`), tout l'arbre React blanchit.
4. **Identité et confiance trompeuses :**
   - `Avatar.tsx` dépend du service externe `ui-avatars.com` : hors ligne ou sous CSP stricte, les avatars du tableau de bord s'affichent en icônes cassées (`naturalWidth === 0`).
   - `DashboardPage.tsx` (L205, L310) et `PropertyDetailPage.tsx` (L940) passent `isVerified={true}` sans condition à `Avatar`, affichant un bouclier vérifié même pour des comptes non vérifiés ou `LOCATAIRE`.
   - Les codes rôles bruts (`PROPRIETAIRE`, `LOCATAIRE`, `ADMIN`, `AGENT`) sont exposés tels quels dans l'interface (`Header`, `MonComptePage`, `DashboardPage`, `PropertyDetailPage`).
5. **Incohérences métier & données :**
   - `DashboardPage.tsx` (L708-709) affiche en dur `Frais: 5 000` au lieu d'utiliser `transaction.fraisDossier` (démontré en capture à `8 510 000 = 8 500 000 + 5 000` sur une vente à `fraisDossier: 10 000`).
   - Un compte `LOCATAIRE` ouvrant `/dashboard` déclenche inutilement `GET /api/v1/annonces?mes_annonces=true&page=0&size=50`.
   - `FavorisPage.tsx` conserve les anciens bugs d'affichage (`0 m²`, `1 pièces`, absence de badge de disponibilité `LOUEE`/`VENDUE`/`SUSPENDUE`).
   - `EditAnnonceModal.tsx` ne permet ni d'ajouter/supprimer des photos, ni de soumettre les justificatifs cadastraux (`F14`), ni d'éditer `lienVisite360` (contrairement à `CreateAnnonceModal.tsx` et au panneau caché en bas de `PropertyDetailPage.tsx`).
6. **Collisions CSS & dépendances externes bloquées :**
   - `Footer.tsx` (L19 et L163) place `lg:px-8` après `sm:pr-44`, écrasant la marge droite de `11rem` dès `1024 px` et provoquant des chevauchements entre le bouton flottant Samba IA et les liens/textes du footer (`contact@e-courtier.sn` recouvert à `1280×720`).
   - Samba IA chevauche des éléments interactifs sur `/inscription` (`390×844`, `1024×768`), `/mon-compte` (`390×844`), `/dashboard` (`320×568`, `390×844`) et `/admin/utilisateurs` (`320×568`, `1024×768`, `1280×720`).
   - `LoginPage.tsx` (L67) et `RegisterPage.tsx` (L69) chargent encore des URL `images.unsplash.com` externes.

### Opportunité de transformation SaaS / PropTech

Sans toucher au backend existant, une refonte frontend structurée (Lot 3.x) permet de transformer l'actuel triptyque dispersé (`/mon-compte` minimaliste + `/favoris` isolé + `/dashboard` monolithique à double barre sombre) en **espaces profils différenciés par rôle**, alignés visuellement sur le standard Sahel Premium de `/`, dotés d'une véritable navigation adaptative, de tableaux de bord synthétiques orientés action, d'une messagerie Sahel fluide, et d'un `Mon compte` complet dans les limites exactes des API disponibles.

---

## 2. Cartographie exhaustive des espaces profils existants

### 2.1 Routes frontend (`frontend/src/App.tsx`)

| Route | Composant | Garde route (`App.tsx`) | Garde interne page | `state.from` transmis | Footer appliqué (`Footer.tsx`) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `/connexion` | `LoginPage` | Aucune | Aucune (ne redirige pas si déjà connecté) | Consommé si présent | Complet (`!isHome`) |
| `/inscription` | `RegisterPage` | Aucune | Aucune (ne redirige pas si déjà connecté) | Non | Complet (`!isHome`) |
| `/mon-compte` | `MonComptePage` | Aucune | `useEffect` -> `/connexion` | Oui (`'/mon-compte'`) | Complet (`!isHome`) |
| `/favoris` | `FavorisPage` | Aucune | `useEffect` -> `/connexion` | Oui (`'/favoris'`) | Complet (`!isHome`) |
| `/dashboard` | `DashboardPage` | Aucune | `useEffect` -> `/connexion` | **Non (`null`)** | Compact (`bg-[#162823]`) |
| `/admin` | `Navigate` | Aucune | Redirige vers `/admin/dashboard` | Non | — |
| `/admin/dashboard` | `AdminDashboardPage` | Aucune | `useEffect` (`!user` -> `/connexion`, `!==ADMIN` -> `/dashboard`) | **Non** | Compact (`bg-[#162823]`) |
| `/admin/utilisateurs` | `AdminUtilisateursPage` | Aucune | `useEffect` (`!user` -> `/connexion`, `!==ADMIN` -> `/dashboard`) | **Non** | Compact (`bg-[#162823]`) |
| `/admin/verification` | `AdminVerificationPage` | Aucune | `useEffect` (`!user` -> `/connexion`, `!==ADMIN` -> `/dashboard`) | **Non** | Compact (`bg-[#162823]`) |
| `/admin/signalements` | `AdminSignalementsPage` | Aucune | `<Navigate replace />` (`!user` -> `/connexion`, `!==ADMIN` -> `/`) | **Non** | Compact (`bg-[#162823]`) |
| `/admin/transactions` | `AdminTransactionsPage` | Aucune | `useEffect` (`!user` -> `/connexion`, `!==ADMIN` -> `/dashboard`) | **Non** | Compact (`bg-[#162823]`) |
| *(non routée)* | `ComingSoonPage` | — | Page legacy présente dans `pages/ComingSoonPage.tsx` (rôles fictifs `CHERCHEUR`, `PROPRIETAIRE`, `COURTIER`) | — | — |

### 2.2 Composants, stores, hooks et services impliqués

| Couche | Fichier | Rôle exact |
| :--- | :--- | :--- |
| État auth | `frontend/src/store/useAuthStore.ts` | Persistance `sessionStorage` (`ecourtier-auth`), `utilisateur`, `accessToken`, `refreshToken`, `logout()` (`POST /api/v1/auth/deconnexion`), `clearClientSessionData()` (`caches.delete`, `queryClient.clear`, suppression clés `ecourtier_ia_conversation_*`). |
| Client HTTP | `frontend/src/services/api.ts` | `apiFetch`, `refreshSessionToken` (rotation verrouillée via `failedQueue`), déconnexion forcée sur `403` si message contient `désactivé`, `fetchProtectedBlob`, `genererPdfBail`, `downloadPdfBail`. |
| Services | `frontend/src/services/admin.service.ts` | Appels admin (`/api/v1/admin/*`) **+ méthode compte utilisateur** `changerMonMotDePasse` (`PUT /api/v1/utilisateurs/moi/mot-de-passe`). |
| Services | `frontend/src/services/messagerie.service.ts` | REST messagerie (`/api/v1/conversations/*`, `/api/v1/annonces/:id/conversations`). |
| Hooks | `frontend/src/hooks/useConversationSocket.ts` | WebSocket STOMP (`@stomp/stompjs`) sur `/ws`, souscription `/topic/conversations/:id`, publication `/app/conversations/:id/envoyer`, refresh token sur close `1008` / 3 échecs. |
| Hooks | `frontend/src/hooks/useReferences.ts` | `GET /api/v1/references` (`typesBien`, `equipements`, `proximites`, `modesTransaction`, `typesDocument`). |
| Layout | `frontend/src/components/layout/Header.tsx` | Navigation contextuelle selon `utilisateur?.role`, compteurs favoris et messages non lus (`refetchInterval: 10000`), accès `Mon compte`, drawer mobile `< xl`. |
| Layout | `frontend/src/components/layout/Footer.tsx` | Rendu conditionnel par route (`/recherche` nul, `/dashboard` et `/admin/*` compact, autres complet). |
| Dashboard | `frontend/src/components/dashboard/{CreateAnnonceModal,EditAnnonceModal,SmsSimulesTab}.tsx` | Modales CRUD annonces et journal SMS simulés. |
| Messagerie | `frontend/src/components/messagerie/{ConversationsTab,ChatDrawer}.tsx` | Vue onglet `/dashboard?tab=messages` + tiroir portail depuis fiche annonce. |
| Transactions | `frontend/src/components/transaction/TransactionDetailsModal.tsx` | Détail séquestre, paiement Wave sandbox, annulation, signature bail, téléchargement/régénération PDF, remboursement admin. |
| UI partagés | `frontend/src/components/ui/{Avatar,Badge,Button,Card,Drawer,FavoriteButton,Input,Modal,OfflineIndicator,PatternClaustra,Select,SignalementModal,Skeleton,Tabs,Toast,Tooltip}.tsx` | Primitives design system (Radix + Tailwind). |
| Carte | `frontend/src/components/map/LocationPickerMap.tsx` | Sélection lat/lng dans `CreateAnnonceModal` / `EditAnnonceModal`. |
| Fiche publique | `frontend/src/pages/PropertyDetailPage.tsx` | Affiche l'encart vendeur/agent (`L931-971`), réputation (`/reputation`), tiroir chat (`ChatDrawer`) et gestion pièces justificatives (`L974-1050+`). |

### 2.3 Endpoints backend et rôle réel (`backend/src/main/java/`)

| Domaine | Endpoint backend réel | Rôles autorisés / comportement | Couverture frontend |
| :--- | :--- | :--- | :--- |
| Rôles enum | `Role.java` | Uniquement `ADMIN`, `AGENT`, `PROPRIETAIRE`, `LOCATAIRE`. | Identique dans `UtilisateurDTO` (`types/index.ts`). |
| Auth | `POST /api/v1/auth/inscription` | Public ; bloque `ADMIN` et `AGENT` (400) ; exige mot de passe `>=8`, 1 majuscule, 1 chiffre (`InscriptionRequestDTO.java`). | `RegisterPage.tsx` (sans check majuscule/chiffre côté client). |
| Auth | `POST /api/v1/auth/connexion`, `rafraichir`, `deconnexion` | Public / rotation refresh / révocation famille (`AuthController.java`). | `LoginPage.tsx`, `api.ts`, `useAuthStore.ts`. |
| Mon compte | `GET /api/v1/utilisateurs/moi` | Tout utilisateur authentifié (`UtilisateurCompteController.java`). | **Non appelé par le frontend** (l'UI lit uniquement l'objet figé au login dans `sessionStorage`). |
| Mon compte | `PUT /api/v1/utilisateurs/moi/mot-de-passe` | Tout utilisateur authentifié ; révoque tokens & WS (`UtilisateurManagementService.java`). | `MonComptePage.tsx` via `adminService.changerMonMotDePasse`. |
| Profil édition / avatar | *(aucun endpoint)* | **Absent du backend** : pas de `PUT /api/v1/utilisateurs/moi` ni upload `photoProfilUrl`. | Lecture seule dans `MonComptePage.tsx`. |
| Réputation | `GET /api/v1/utilisateurs/{id}/reputation` | Public (`UtilisateurController.java`), agrège les avis sur les annonces du propriétaire/agent. | Utilisé sur `PropertyDetailPage.tsx`, **absent** de `/dashboard` et `/mon-compte`. |
| Favoris | `GET /api/v1/utilisateurs/moi/favoris`, `/favoris/count`, `POST/DELETE /api/v1/annonces/{id}/favoris` | Authentifié (`FavoriController.java`). | `FavorisPage.tsx`, `FavoriteButton.tsx`. Note : `Header.tsx` appelle `/favoris?size=1` au lieu de `/favoris/count`. |
| Annonces perso | `GET /api/v1/annonces?mes_annonces=true`, `POST/PUT/DELETE /api/v1/annonces/*`, photos, docs | Création/gestion propre + suppression logique + upload photos/documents. | `DashboardPage.tsx`, `CreateAnnonceModal`, `EditAnnonceModal`, `PropertyDetailPage`. |
| Rendez-vous | `GET /api/v1/rendez-vous/mes-rendez-vous`, `PUT /{id}/confirmer\|annuler\|marquer-effectue` | `RendezVousController.java`. | `DashboardPage.tsx` onglet `VISITES`. |
| Transactions | `GET /api/v1/transactions/mes-transactions`, `/api/v1/admin/transactions`, `POST .../paiement-caution`, `/annuler`, `/bail/signer`, `/rembourser`, PDF | `TransactionController.java`. | `DashboardPage.tsx`, `AdminTransactionsPage.tsx`, `TransactionDetailsModal.tsx`. |
| Messagerie | `/api/v1/conversations/*`, `/api/v1/annonces/{id}/conversations`, STOMP `/ws` | `MessagerieRestController.java`, `WebSocket`. | `ConversationsTab.tsx`, `ChatDrawer.tsx`, `Header.tsx`. |
| SMS simulés | `GET /api/v1/notifications-sms/mes-sms` | `NotificationSmsController.java`. | `SmsSimulesTab.tsx`. |
| Admin utilisateurs | `GET/POST /api/v1/admin/utilisateurs`, `PUT /{id}/role`, `PUT /{id}/statut` | `ADMIN` uniquement (`AdminUtilisateurController.java`). | `AdminUtilisateursPage.tsx`. |

---

## 3. État détaillé des fonctionnalités existantes

| Fonctionnalité | Localisation | Statut réel | Constats factuels vérifiés |
| :--- | :--- | :--- | :--- |
| Connexion email / mot de passe | `LoginPage.tsx` | **Opérationnelle et vérifiée** | Fonctionne avec `apiFetch`, gestion erreurs et `fromLocation`. Redirige cependant `ADMIN` vers `/dashboard` au lieu de `/admin/dashboard`. Image Unsplash externe ; message « version universitaire » & `support@ecourtier.sn` dissonant. |
| Mot de passe oublié | `LoginPage.tsx` (L133-160) | **Interface uniquement / simulation** | Simple bandeau d'information statique renvoyant vers `support@ecourtier.sn` (aucun serveur SMTP/endpoint reset backend). |
| Inscription publique (`LOCATAIRE` / `PROPRIETAIRE`) | `RegisterPage.tsx` | **Partielle** | Endpoint `POST /api/v1/auth/inscription` opérationnel ; note informative Agent OK ; téléphone pré-rempli à `+221770000000` ; pas de validation majuscule+chiffre avant soumission ; cartes profil en `<div>` non accessibles clavier. |
| Déconnexion multi-surfaces | `Header.tsx`, `DashboardPage.tsx`, `useAuthStore.ts` | **Opérationnelle et vérifiée** | Révocation serveur (`keepalive: true`), purge React Query + caches + conversations IA. Redirection incohérente (`Header` -> `/`, `Dashboard` -> `/connexion`). |
| Consultation profil connecté | `MonComptePage.tsx` | **Partielle** | Affiche les données de `sessionStorage` figées au login (`prenom`, `nom`, `email`, `telephone`, `role`). N'interroge jamais `GET /api/v1/utilisateurs/moi` pour détecter une désactivation, un changement de rôle ou de données. |
| Modification profil & photo de profil | `MonComptePage.tsx`, `UtilisateurCompteController.java` | **Absente** | `photoProfilUrl` existe dans `UtilisateurDTO` et `UtilisateurEntity`, mais ni endpoint ni UI d'édition/upload n'existent. |
| Changement de mot de passe | `MonComptePage.tsx`, `admin.service.ts` | **Opérationnelle et vérifiée** | Checklist temps réel (`8 car.`, `1 majuscule`, `1 chiffre`), appel `PUT /api/v1/utilisateurs/moi/mot-de-passe`, toast et déconnexion automatique forcée. |
| Sauvegarde & liste des favoris | `FavorisPage.tsx`, `FavoriteButton.tsx`, `Header.tsx` | **Partielle** | Ajout/retrait optimiste et pagination fonctionnels. Cartes régressives par rapport à `/recherche` (`0 m²`, `1 pièces`, pas d'étiquette `Louée`/`Vendue`/`Suspendue`). |
| Publication / édition / suppression d'annonces | `DashboardPage.tsx`, `CreateAnnonceModal.tsx`, `EditAnnonceModal.tsx` | **Partielle** | Création + photos + justificatifs + suppression logique OK ; contrôle optimiste `version` (`409`) présent sur édition. Manques : `EditAnnonceModal` ne gère ni photos, ni docs, ni `lienVisite360` ; pas de pagination (`size=50` fixe) ; risque de doublon si upload photo échoue après `POST /annonces` ; bug `768×1024`. |
| Agenda des visites & transitions statut | `DashboardPage.tsx` (L551-665) | **Partielle** | Transitions `confirmer`, `marquer-effectue`, `annuler` fonctionnelles. Absence de lien direct vers la fiche du bien avant statut `EFFECTUE`, pas de filtre/tri, libellé propriétaire affiché aux locataires. |
| Suivi séquestres & signature bail PDF | `DashboardPage.tsx` (L667-742), `TransactionDetailsModal.tsx` | **Opérationnelle et vérifiée** (hors paiement réel) | Modale complète (`LOCATION` & `VENTE`), signature bipartite, PDF horodaté, régénération si `ECHEC`/`A_GENERER`. Défaut : carte liste `/dashboard` code `Frais: 5 000` en dur au lieu de `tx.fraisDossier`. |
| Messagerie temps réel WebSocket + REST | `ConversationsTab.tsx`, `useConversationSocket.ts`, `ChatDrawer.tsx` | **Partielle** | Historique REST + STOMP + portail `ChatDrawer` opérationnels. Cassée visuellement en tablette `768×1024` et mobile `320×568` (input poussé hors fold) ; erreurs d'envoi/XSS silencieuses (`error` non lu) ; pas de lien vers l'annonce ; charte Slate/Amber étrangère au design Sahel. |
| Notifications SMS simulées | `SmsSimulesTab.tsx` | **Interface uniquement / simulation** | Affiche correctement `GET /api/v1/notifications-sms/mes-sms` (polling 15s) avec mention explicite *Mode démonstration*. Aucun filtre, marqueur lu ou pagination. |
| Gestion des utilisateurs & agents (Admin) | `AdminUtilisateursPage.tsx` | **Partielle** | Recherche, filtres rôle/statut, création `AGENT` + copie mdp temporaire unique, changement rôle/statut fonctionnels. Tableau inutilisable sur `768×1024` (colonne Actions coupée). |
| Score de réputation propriétaire/agent | `PropertyDetailPage.tsx` (L953-968), `UtilisateurController.java` | **Partielle** | Visible côté visiteur sur la fiche annonce via `/reputation`, totalement absent des tableaux de bord propriétaire/agent et de `Mon compte`. |
| Mot de passe expiré / vérification identité KYC utilisateur / préférences notifications | *(aucun)* | **Absente** | Aucun parcours dédié côté profil utilisateur. |

---

## 4. Audit fonctionnel par espace et par rôle

### 4.1 `LOCATAIRE` (Locataire / Acheteur)
- **Navigation Header (`Header.tsx` L233-243) :** liens vers `Favoris`, `Messages` (`/dashboard?tab=messages`), `Mes visites & dossiers` (`/dashboard?tab=visites`), `Mon compte` (`/mon-compte`).
- **Frictions majeures :**
  - L'utilisateur locataire dispose de **trois entrées séparées** (`/favoris`, `/dashboard`, `/mon-compte`) sans vue synthétique de son parcours (prochaine visite, dossier en attente de paiement, bail à signer).
  - Sur `/dashboard`, le système appelle inutilement `GET /api/v1/annonces?mes_annonces=true&page=0&size=50` (prouvé dans la trace réseau Playwright).
  - Sur l'onglet `VISITES`, le sous-titre indique : *« Suivez et gérez les demandes de rendez-vous de visite programmées sur vos biens »* (`DashboardPage.tsx` L556) — wording erroné destiné aux propriétaires.
  - Sur une visite `DEMANDE` ou `CONFIRME`, aucun lien ne permet au locataire d'ouvrir l'annonce concernée ; seul `EFFECTUE` propose `Initier location / achat`.

### 4.2 `PROPRIETAIRE` (Bailleur / Vendeur)
- **Navigation & accès :** `Espace Partenaire` (`/dashboard`, défaut onglet `ANNONCES`), création/édition/suppression de ses annonces, gestion visites, transactions, messagerie, SMS.
- **Frictions majeures :**
  - **Rupture complète sur `EditAnnonceModal.tsx` :** une fois l'annonce publiée, le modal d'édition ne propose ni la gestion des photos, ni l'upload des justificatifs de vérification (`F14`), ni le lien de visite virtuelle 360°. Le propriétaire doit cliquer `Consulter` pour aller sur la fiche publique `/annonce/:id` et trouver le formulaire de justificatifs en bas de page (`PropertyDetailPage.tsx` L974), ou constater qu'aucune UI de gestion de photos post-création n'y est reliée dans l'espace partenaire.
  - **Motifs de rejet peu visibles :** si une annonce est `REJETEE`, la liste `DashboardPage` affiche seulement un badge `Vérification Rejetée` sans exposer le motif ni bouton direct pour soumettre les pièces manquantes.
  - **Absence d'indicateurs clés :** aucun récapitulatif haut de page (annonces vérifiées vs en attente, visites à confirmer, fonds sous séquestre/libérés, note moyenne de réputation pourtant disponible via `GET /api/v1/utilisateurs/{id}/reputation`).

### 4.3 `AGENT` (Courtier / Agent immobilier)
- **Constat fonctionnel :** dans `DashboardPage.tsx` et `MonComptePage.tsx`, l'expérience `AGENT` est **strictement identique** à celle d'un `PROPRIETAIRE` sans différenciation métier.
- **Manques spécifiques PropTech :**
  - Aucune distinction visuelle d'agence / carte professionnelle ou badge courtier dédié dans `/dashboard` ou `/mon-compte`.
  - Si un agent ou partenaire publie > 50 biens, `size=50` sans pagination masque les annonces suivantes ; aucun filtre par statut (`DISPONIBLE`, `LOUEE`, `VERIFIEE`) ou champ de recherche n'existe dans `Mes Annonces`.
  - Dans `PropertyDetailPage.tsx` (L954), le bloc réputation affiche « Réputation Propriétaire » même lorsque l'auteur est un `AGENT`.

### 4.4 `ADMIN` (Administrateur)
- **Constat fonctionnel :** `Header.tsx` (L221-255) affiche simultanément `Administration` (`/admin/dashboard`) et `Espace Partenaire` (`/dashboard`). Après connexion, `LoginPage.tsx` (L51) l'envoie vers `/dashboard` plutôt que `/admin/dashboard`.
- **Interactions avec les comptes :**
  - Sur `/admin/utilisateurs`, l'admin crée des comptes `AGENT` (avec mot de passe temporaire généré ou saisi), modifie les rôles et désactive/réactive des comptes (avec protections anti-auto-suppression et dernier admin actif).
  - Sur `/dashboard`, un `ADMIN` voit les boutons `Modifier`/`Supprimer` sur toute annonce (`utilisateur.role === 'ADMIN'`, L510), mais pas le bouton `Créer une annonce` (limité à `PROPRIETAIRE || AGENT`, L432), créant une asymétrie ambiguë entre `/dashboard` et `/admin/verification`.
  - Aucun détail enrichi (nombre d'annonces, signalements ou dossiers d'un utilisateur) n'est consultable depuis la fiche ligne `/admin/utilisateurs`.

### 4.5 `/mon-compte`, `/favoris`, `/connexion`, `/inscription`
- **`/mon-compte` :** limité à 2 cartes (`Informations du compte` en lecture seule + `Changer mon mot de passe`). Manque d'accès rapides vers favoris/messages/visites/dashboard, d'avatar réel, d'information sur la politique de session/déconnexion, et de synchronisation serveur `/utilisateurs/moi`.
- **`/favoris` :** pagination (`size=12`), empty state soigné avec `PatternClaustra`, mais aucune possibilité de filtrer (Location/Vente, Vérifiés, Disponibles uniquement) et cartes moins soignées que celles de `/recherche`.
- **`/connexion` & `/inscription` :** pas de bouton afficher/masquer le mot de passe (`type="password"` fixe) dans `LoginPage`, `RegisterPage` et `MonComptePage` ; aucun lien retour ou aide contextuelle unifiée (`support@ecourtier.sn` vs `contact@e-courtier.sn`).

---

## 5. Audit UX / ergonomie SaaS et parcours utilisateur

1. **Architecture d'information fragmentée et redondante :**
   - Sur `/dashboard`, l'utilisateur authentifié subit **trois niveaux de navigation empilés** sur mobile et deux sur desktop : le `Header` global (avec liens `Favoris`, `Messages`, `Espace Partenaire`, `Mon compte • ROLE`, `Déconnexion`), **puis** un bandeau ou sidebar sombre (`bg-sahel-900`) répétant le nom, le rôle, un autre bouton `Déconnexion` et les 5 onglets.
   - Sur `/admin/*`, l'en-tête combine le `Header` global + `AdminNavTabs` (qui passe sur deux lignes à `1280×720` avec `Signalements` orphelin en capture).
2. **Désynchronisation URL ↔ onglets sur `/dashboard` :**
   - L'initialisation lit `?tab=messages|sms|visites|sequestres` (`DashboardPage.tsx` L44-74), mais cliquer sur un onglet dans la sidebar ne met pas à jour `searchParams`. Conséquences : un rechargement F5 ou bouton « Précédent » perd l'onglet actif, et la classe active du lien `Messages` dans `Header.tsx` (`location.search.includes('messages')`, L198) reste fausse après bascule manuelle d'onglet.
3. **Jargon technique exposé à l'utilisateur final :**
   - Rôles bruts sans traduction typographique : `LOCATAIRE`, `PROPRIETAIRE`, `ADMIN`, `AGENT` (`Header.tsx` L275, `MonComptePage.tsx` L89/119, `DashboardPage.tsx` L211/314, `PropertyDetailPage.tsx` L945).
   - Titre onglet « SMS simulés » et sous-titre orienté développeur (« Mode démonstration » / « SmsGatewayLogImpl » côté code) au lieu d'un libellé métier clair (« Historique des notifications SMS » + badge « Démo »).
   - Référence technique tronquée `#tx-12345` en titre de modale transactionnelle.
4. **Cibles d'action et fluidité inter-écrans :**
   - Sur `VISITES` et `MESSAGES`, les titres de biens ne sont pas cliquables vers `/annonce/:id`.
   - Aucune confirmation explicite avant d'annuler une visite (`handleCancelVisit` annule en 1 clic sans dialogue de confirmation ni saisie de motif, envoyant un motif générique en dur `'Annulé depuis le tableau de bord'`, L158).
   - Les `empty states` de `DashboardPage` (sauf `ANNONCES`) ne proposent aucun bouton d'action (ex. `Explorer les biens` sur `VISITES` pour un locataire).

---

## 6. Audit UI / design system & cohérence visuelle

| Critère visuel | Référence Sahel Premium (`/`, `/recherche`) | État actuel des espaces profils |
| :--- | :--- | :--- |
| Palette chromatique | Crème sable `#F5F0E8` (`sand-100`), terracotta `#B85C42`, vert Sahel `#1C352E`, or/ocher `#D5AD72`, bordures `#E2DACB`. | **Rupture sévère sur `ConversationsTab.tsx` :** utilise `bg-white dark:bg-slate-900`, `border-slate-200`, `amber-600`, `from-amber-500 to-orange-600` (style générique Slate/Orange en désaccord complet avec `DashboardPage`). |
| Typographie | `font-editorial` (`Manrope` / `Plus Jakarta Sans`), hiérarchie nette. | `MonComptePage`, `FavorisPage`, `DashboardPage` utilisent encore `font-display` et des tailles hétérogènes (`p-4 sm:p-6` vs `p-6` sur deux cartes adjacentes dans `MonComptePage`). |
| Avatars & médias | Photos webp locales de haute tenue (`PropertyVisual`), zéro image cassée. | **`Avatar.tsx` (L36) :** dépendance `https://ui-avatars.com/api/...` provoquant des images brisées (`alt="Moussa Ndiaye"` coupé dans le cercle) en contexte hors ligne ou réseau restreint. **`LoginPage` / `RegisterPage` :** images Unsplash externes avec texte alt visible en haut à gauche en cas d'échec réseau. |
| Badges de confiance | Badge vérifié strictement conditionné à `VERIFIEE`. | `Avatar` reçoit `isVerified` inconditionnel (`true`) dans `DashboardPage` (L205, L310) et `PropertyDetailPage` (L940), galvaudant l'icône bouclier vérifié. |
| Pied de page & dégagement flottant | Marge droite réservée pour Samba IA. | **`Footer.tsx` (L19, L163) :** bug Tailwind `lg:px-8` écrasant `sm:pr-44` ; à `1280×720`, le widget Samba IA mord sur `contact@e-courtier.sn` (`dashboard-prop-messages_1280x720.png`) et sur la colonne « Zone d'intervention » (`favoris_1280x720.png`). |
| Boutons header mobile (`< 360 px`) | Bouton primaire clair et lisible. | À `320×568` (`mon-compte_320x568.png`), le bouton header `Trouver un bien` masque son texte (`hidden xs:inline`) et devient un rond orange anonyme à icône étoile entre le profil et le menu burger. |

---

## 7. Audit technique, sécurité frontend, gestion d'état et fiabilité

| Catégorie | Constat précis dans le code | Impact | Sévérité |
| :--- | :--- | :--- | :--- |
| **Routage & protection des accès** | `App.tsx` (L49-63) ne déclare aucun wrapper `<ProtectedRoute>`. Chaque page protégée (`DashboardPage`, `MonComptePage`, `FavorisPage`, `Admin*Page`) implémente son propre `useEffect` ou `<Navigate>`, sans cohérence : `DashboardPage` et 4 pages Admin omettent `{ state: { from } }`. | Perte de la destination demandée après reconnexion ; code dupliqué dans 7 pages. | **P1** |
| **Résilience aux erreurs & crash UI** | Absence d'`ErrorBoundary` dans `App.tsx`/`main.tsx`. Dans `useConversationSocket.ts` (L24), `setMessages(history)` ne vérifie pas `Array.isArray(history)`. Lors d'une réponse non-tableau, `.map()` provoque un écran blanc total sur toute l'application. | Risque de page blanche irréversible en cas d'anomalie API. | **P0** |
| **Remontée d'erreurs messagerie** | `ConversationsTab.tsx` (L31) n'extrait pas `error` retourné par `useConversationSocket`. | En mode hors ligne ou rejet client `<script>`, l'utilisateur clique `Envoyer` sans aucun feedback ni message d'erreur. | **P1** |
| **Fraîcheur des données utilisateur** | Le frontend ne consomme jamais `GET /api/v1/utilisateurs/moi` (`UtilisateurCompteController.java`). L'identité et le rôle proviennent uniquement de `sessionStorage` (`ecourtier-auth`). | Si le rôle/téléphone change avant expiration de l'access token ou sans reconnexion, l'UI affiche des métadonnées obsolètes. | **P2** |
| **Appels réseau inutiles / sous-optimaux** | 1) `DashboardPage.tsx` (L106-110) exécute `/api/v1/annonces?mes_annonces=true` pour les comptes `LOCATAIRE` (`enabled: !!utilisateur`). 2) `Header.tsx` (L49) appelle `/utilisateurs/moi/favoris?size=1` au lieu de `/utilisateurs/moi/favoris/count`. | Surcharge API systématique sans utilité fonctionnelle. | **P2** |
| **Dépendances externes bloquantes** | 1) `Avatar.tsx` -> `ui-avatars.com`. 2) `LoginPage.tsx` & `RegisterPage.tsx` -> `images.unsplash.com`. 3) `LocationPickerMap.tsx` (L12-14) -> `unpkg.com/leaflet@1.9.4/...` + tuiles OSM. 4) `index.html` -> Google Fonts. | Images cassées en environnement hors ligne / réseau filtré ; non-respect du socle d'assets locaux établi à `/` et `/recherche`. | **P1** |
| **Fuites mémoire & soumission non atomique** | `CreateAnnonceModal.tsx` (L437) crée des `URL.createObjectURL(file)` dans `render` sans jamais appeler `URL.revokeObjectURL`. De plus, la soumission crée d'abord l'annonce (`POST`), puis enchaîne photos et documents : un échec d'upload laisse une annonce orpheline en base et une seconde validation crée un doublon. | Fuites mémoire navigateur et risque de doublons d'annonces. | **P1** |
| **Sécurité & données sensibles** | Bon points vérifiés : JWT en `sessionStorage`, `apiFetch` gère le `403 désactivé`, Workbox (`vite.config.ts`) exclut les routes/chunks privés. Point d'attention : `PropertyDetailPage.tsx` (L948) affiche le numéro de téléphone de l'auteur en clair aux visiteurs anonymes. | Exposition directe du téléphone sur les pages publiques (à arbitrer côté produit/RGPD). | **P2** |
| **Donnée financière hardcodée** | `DashboardPage.tsx` (L708-709) affiche `+ Frais: 5 000` en dur dans la liste au lieu de `tx.fraisDossier` (qui vaut pourtant `10 000` possiblement selon les transactions, affiché correctement dans `TransactionDetailsModal.tsx` L322). | Incohérence comptable visible dans le récapitulatif séquestre. | **P1** |

---

## 8. Audit responsive multi-écrans (6 viewports obligatoires)

Campagne exécutée par Playwright (`audit_profils_matrix.mjs` en lecture seule avec API simulées et inspection visuelle des captures PNG). Les 6 viewports (`320×568`, `390×844`, `768×1024`, `1024×768`, `1280×720`, `1440×900`) ont été mesurés sur l'ensemble des routes profils et modales.

### 8.1 Tableau matriciel complet (mesures réelles)

| Espace / Vue | `320×568` (XS Mobile) | `390×844` (Mobile) | `768×1024` (Tablette) | `1024×768` (Laptop) | `1280×720` (Desktop) | `1440×900` (Large) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `/connexion` | `ov=0`, `h=1296`, `img=1` (Unsplash), `touch<40=9` | `ov=0`, `h=1465`, `img=1`, `touch<40=9` | `ov=0`, `h=1447`, `img=1`, `touch<40=9` | `ov=0`, `h=1091`, `img=1`, `touch<40=9` | `ov=0`, `h=1023`, `img=1`, `touch<40=10` | `ov=0`, `h=1203`, `img=1`, `touch<40=10` |
| `/inscription` | `ov=0`, `h=1884`, `img=1`, `touch<40=8` | `ov=0`, `h=1757`, `img=1`, **`samba=1` (`input`)** | `ov=0`, `h=1447`, `img=1`, `touch<40=8` | `ov=0`, `h=1186`, `img=1`, **`samba=1` (`S'inscrire`)** | `ov=0`, `h=1166`, `img=1`, `touch<40=9` | `ov=0`, `h=1203`, `img=1`, `touch<40=9` |
| `/mon-compte` | `ov=0`, `h=2038`, `touch<40=6`, icône Header sans label | `ov=0`, `h=1939`, **`samba=1` (`input`)**, `touch<40=6` | `ov=0`, `h=1447`, `touch<40=4` | `ov=0`, `h=1291`, `touch<40=4` | `ov=0`, `h=1271`, `touch<40=8` | `ov=0`, `h=1271`, `touch<40=8` |
| `/favoris` | `ov=0`, `h=2221`, `touch<40=15`, `0 m²` / `1 pièces` | `ov=0`, `h=2081`, `touch<40=15` | `ov=0`, `h=1451`, `touch<40=13` | `ov=0`, `h=952`, `touch<40=13` | `ov=0`, `h=923`, `touch<40=17`, footer chevauché Samba | `ov=0`, `h=923`, `touch<40=17` |
| `/dashboard` (`ANNONCES`) | `ov=0`, `h=1426`, `img=2`, **`samba=1`** (`prix`), `touch<40=19` | `ov=0`, `h=1199`, `img=2`, `touch<40=19` | **`CRITIQUE P0`** : `h=3268`, titre en colonne 1 lettre/ligne, `img=2` | `ov=0`, `h=814`, `img=2`, `touch<40=11` | `ov=0`, `h=766`, `img=2`, `touch<40=15` | `ov=0`, `h=946`, `img=2`, `touch<40=15` |
| `/dashboard?tab=visites` | `ov=0`, `h=1221`, `img=2`, `touch<40=16` | `ov=0`, `h=1064`, `img=2`, **`samba=2`** | `ov=0`, `h=1070`, `img=2`, bouton `Marquer effectué` sur 2 lignes | `ov=0`, `h=814`, `img=2`, `touch<40=8` | `ov=0`, `h=766`, `img=2`, `touch<40=12` | `ov=0`, `h=946`, `img=2`, `touch<40=12` |
| `/dashboard?tab=sequestres` | `ov=0`, `h=1185`, `img=2`, **`samba=1` (`Frais`)** | `ov=0`, `h=986`, `img=2`, `touch<40=14` | `ov=0`, `h=1070`, `img=2`, badge wrap 2 lignes | `ov=0`, `h=814`, `img=2`, `touch<40=6` | `ov=0`, `h=766`, `img=2`, `touch<40=10` | `ov=0`, `h=946`, `img=2`, `touch<40=10` |
| `/dashboard?tab=messages` | **`P1`** `h=909`, input chat hors fold (`min-h-[480px]`), **`samba=1`** | `ov=0`, `h=1012`, `touch<40=13` | **`CRITIQUE P0`** : chat large ~128px, bulles 2–4 car., bouton `Envoyer` coupé | `ov=0`, `h=814`, `touch<40=5` | `ov=0`, `h=766`, footer `contact@` touché par Samba | `ov=0`, `h=946`, `touch<40=9` |
| `/dashboard?tab=sms` | `ov=0`, `h=726`, `img=2`, **`samba=1`**, `touch<40=12` | `ov=0`, `h=898`, `img=2`, `touch<40=12` | `ov=0`, `h=1070`, `img=2`, `touch<40=4` | `ov=0`, `h=814`, `img=2`, `touch<40=4` | `ov=0`, `h=766`, `img=2`, `touch<40=8` | `ov=0`, `h=946`, `img=2`, `touch<40=8` |
| `/dashboard` (`LOCATAIRE`) | `ov=0`, `h=1146`, `img=2`, `touch<40=14` | `ov=0`, `h=1024`, `img=2`, **`samba=1` (`rdv`)** | `ov=0`, `h=1070`, `img=2`, `touch<40=6` | `ov=0`, `h=814`, `img=2`, `touch<40=7` | `ov=0`, `h=766`, `img=2`, `touch<40=11` | `ov=0`, `h=946`, `img=2`, `touch<40=11` |
| `/admin/utilisateurs` | `ov=0`, `h=1537`, **`samba=1` (`Créer un compte`)**, `touch<40=25` | `ov=0`, `h=1508`, `touch<40=25` | **`CRITIQUE P0`** : `table` débordant à `right=949` (`+181 px`), colonne `Actions` invisible | `ov=0`, `h=926`, **`samba=1` (`Désactiver`)** | `ov=0`, `h=846`, **`samba=1` (`Réactiver`)**, `AdminNavTabs` sur 2 lignes | `ov=0`, `h=1026`, `touch<40=28` |

*(Légende : `ov` = `documentElement.scrollWidth - innerWidth` ; `h` = `scrollHeight` px ; `img` = nombre de `<img>` avec `naturalWidth === 0` sous blocage externe ; `touch<40` = contrôles interactifs `< 40 px` en largeur ou hauteur ; `samba` = collisions DOM directes avec le bouton flottant Samba IA au viewport initial.)*

### 8.2 Modales & accessibilité tactile / clavier

- **Modales (`320×568` & `1280×720`) :** `CreateAnnonceModal`, `EditAnnonceModal` et `TransactionDetailsModal` affichent `docOverflowX = 0` grâce à `Modal.tsx` (`max-w-[calc(100vw-1rem)]`, scroll interne `min-h-0 flex-1`). En revanche, `CreateAnnonceModal` L394 utilise `grid-cols-2` sur `Proximités` même à `320 px` alors que `EditAnnonceModal` utilise `grid-cols-1 xs:grid-cols-2` ; et `LocationPickerMap` échoue à charger ses marqueurs `unpkg.com` si le réseau tiers est bloqué (`6` images cassées en modale).
- **Cibles tactiles (`< 40 px` / norme `44 px`) :**
  - Les boutons `Button size="sm"` (`py-1.5` = `28–32 px` de hauteur), les liens du `Header` (`w-9 h-9` = `36×36 px`), les onglets mobiles du `DashboardPage` (`py-1.5` = `29 px` de hauteur) et les `<input>`/`<select>` d'`AdminUtilisateursPage` (`34 px`) sont systématiquement sous les 44 px recommandés sur mobile (`12` à `25` contrôles par vue).
- **Accessibilité clavier :**
  - `RegisterPage.tsx` (L121-156) rend les cartes de choix de rôle (`Locataire / Acheteur`, `Propriétaire`) sous forme de `<div>` cliquables sans `role="button"`, `tabIndex={0}`, `aria-pressed` ni gestion `Enter`/`Space`.

---

## 9. Diagnostic comparatif : écart avec les standards SaaS / PropTech modernes

| Dimension | Standard SaaS / PropTech (Airbnb, Bien'ici, SaaS B2B moderne) | État actuel E-Courtier | Écart |
| :--- | :--- | :--- | :--- |
| **Navigation de compte** | Menu utilisateur unifié (avatar -> dropdown profil, paramètres, raccourcis rôle) ; séparation nette *Compte personnel* vs *Espace opérationnel*. | Double header/sidebar sombre redondant sur `/dashboard` ; `Header` + `AdminNavTabs` sur `/admin` ; zéro menu déroulant sur l'avatar. | Élevé |
| **Vue d'accueil espace utilisateur** | Synthèse orientée action (prochain RDV, alerte dossier à payer/signer, KPI portfolio, note de réputation, tâches en attente). | Liste brute d'annonces ou de visites sans compteurs résumés, ni tri/filtre, ni priorité d'action. | Élevé |
| **Gestion de profil & sécurité** | Édition coordonnées + avatar local/upload, historique de sessions/appareils, vérification d'identité/statut compte visible, changement mot de passe sécurisé. | Lecture seule totale des coordonnées + formulaire mot de passe uniquement (`MonComptePage`). | Critique (partiellement contraint backend) |
| **Espace Partenaire / Agent** | Tableau filtrable/paginé, statistiques d'annonces, dépôt/suivi pièces justificatives et photos dans un même flux d'édition. | Édition scindée (`EditAnnonceModal` incomplet + formulaire docs caché sur fiche publique `/annonce/:id`), limite `size=50` sans pagination. | Élevé |
| **Messagerie intégrée** | Thread fluide lié au bien cliquable, indicateur d'état réseau clair, gestion erreurs envoi, thème identitaire homogène. | Thème Slate/Orange déconnecté du thème Sahel, erreur envoi silencieuse, cassée sur tablette `768 px` et serrée sur `320 px`. | Élevé |
| **Tablettes (`768×1024`)** | Responsive adaptatif pensé pour iPad portrait (`drawer` ou barre horizontale compacte, tableaux scrollables ou bascule cartes). | 3 bugs P0 bloquants sur `768×1024` (lettres empilées, chat 128 px, tableau admin coupé). | Critique |

---

## 10. Architecture cible proposée pour les espaces profils

**Contrainte cardinale :** Zéro modification backend. Préservation intégrale des routes publiques et privées existantes (`/connexion`, `/inscription`, `/mon-compte`, `/favoris`, `/dashboard`, `/admin/*`), des flux STOMP/Wave/PDF et de la compatibilité avec les tests E2E existants.

### 10.1 Séparation claire des responsabilités (frontières propres)

1. **Socle transverse profil & sécurité (`src/components/auth/` & `src/hooks/` & `src/lib/`) :**
   - **`ProtectedRoute`** centralisé dans `App.tsx` : gère `!utilisateur` (avec `{ state: { from: location.pathname + location.search } }`), contrôle `allowedRoles`, et unifie l'ErrorBoundary React global.
   - **Hydratation profil légère :** appel non bloquant à `GET /api/v1/utilisateurs/moi` (hook `useCurrentProfile`) au montage session pour maintenir l'utilisateur synchronisé sans changer le store.
   - **Normalisation rôles & statuts (`src/lib/roleLabels.ts`) :** mapper `LOCATAIRE` -> *Locataire / Acheteur*, `PROPRIETAIRE` -> *Propriétaire*, `AGENT` -> *Agent immobilier*, `ADMIN` -> *Administrateur*, avec badges visuels Sahel cohérents (plus de code enum brut en UI).
   - **Composant `Avatar.tsx` 100 % autonome hors ligne :** rendu monogramme SVG/CSS aux couleurs Sahel (`bg-[#1C352E]`, `bg-[#B85C42]`) lorsque `photoProfilUrl` est absent ou en `onError`, et retrait de `isVerified` par défaut.

2. **`/mon-compte` — Hub Profil Personnel & Sécurité (tous rôles) :**
   - En-tête Sahel raffiné avec avatar monogramme, libellé de rôle traduit, note de réputation (`GET /api/v1/utilisateurs/{id}/reputation` si `PROPRIETAIRE`/`AGENT`) et accès rapides contextuels selon le rôle (`Mes favoris`, `Mon espace visites`, `Espace partenaire`, `Administration`).
   - Carte **Identité & Coordonnées** + encart explicatif honnête indiquant que la modification d'email/téléphone passe par le support/administrateur tant qu'aucun endpoint d'auto-édition n'est ouvert (aucune fausse UI simulée).
   - Carte **Sécurité du compte** : changement de mot de passe (avec bouton afficher/masquer l'œil), rappel des règles (révocation des autres sessions après validation).

3. **`/favoris` — Sélection sauvegardée cohérente avec `/recherche` :**
   - Harmonisation complète des cartes avec celles de `SearchResultsPage` : affichage conditionnel propre de `superficie`, singulier/pluriel `1 pièce` / `N pièces`, badge clair si `statutPublication !== 'DISPONIBLE'` (`Louée`, `Vendue`, `Réservée`, `Suspendue`), filtres rapides client-side sur la page courante (Disponibles uniquement / Location / Vente) et gestion du recul de page lorsque le dernier favori d'une page `> 0` est retiré.

4. **`/dashboard` — Espace Opérationnel Adaptatif par Rôle (`?tab=...` synchronisé) :**
   - **Suppression du double bandeau sombre redondant** : remplacer l'en-tête profil+déconnexion dupliqué par un **bandeau contextuel Sahel clair** (`bg-white` + accent `#1C352E` / `#B85C42`) avec KPI synthétiques du rôle et navigation à onglets synchronisée bidirectionnellement avec `?tab=` (`navigate`/`setSearchParams`).
   - **Responsive iPad-first (`768×1024`) :** basculer la sidebar verticale fixe à `lg:` (`>= 1024 px`) ou utiliser des onglets horizontaux scrollables / pilules en `< lg` (`< 1024 px`), libérant toute la largeur `768 px` pour les cartes `Annonces` et la messagerie split/pane.
   - **Différenciation par rôle :**
     - `LOCATAIRE` : onglet par défaut `VISITES` avec wording adapté (*« Vos demandes de visite et prochains rendez-vous »*), **aucun** appel à `mes_annonces=true`, lien cliquable vers `/annonce/:id` sur chaque visite et transaction, actions de confirmation d'annulation via modale.
     - `PROPRIETAIRE` / `AGENT` : KPI en tête (annonces vérifiées, en attente, visites à traiter, transactions actives + réputation), champ de recherche/filtre par statut sur `Mes Annonces`, pagination complète (`Mes Annonces`), gestion du motif de rejet directement sur la carte avec bouton *Compléter le dossier* ouvrant un onglet/tiroir d'édition complet (photos + `lienVisite360` + justificatifs `F14` réunis au même endroit sans dupliquer le backend), et affichage correct de `tx.fraisDossier`.
   - **Refonte `ConversationsTab.tsx` en design system Sahel :**
     - Remplacement intégral de `slate-* / amber-* / dark:*` par `sand-* / argile-* / terracotta-* / sahel-*`.
     - Panneau conversation en mode liste/détail basculant jusqu'à `lg:` (`1024 px`) pour éviter l'écrasement à `768 px`.
     - Hauteur fluide sur mobile (`h-[calc(100dvh-190px)] min-h-[340px]`) pour garder la barre de saisie visible sans scroller sur `320×568`.
     - Affichage explicite des erreurs retournées par `useConversationSocket` + lien direct vers l'annonce objet de la conversation.

5. **`/connexion` & `/inscription` — Entrée unifiée, accessible et hors-ligne-ready :**
   - Remplacement des deux photos Unsplash par des visuels locaux haute définition issus de `src/assets/home/`.
   - Toggle afficher/masquer mot de passe, validation checklist mot de passe en direct sur `/inscription` (8 car., 1 majuscule, 1 chiffre aligné sur `InscriptionRequestDTO`), suppression du téléphone factice `+221770000000` pré-rempli, cartes rôles converties en véritables `<button type="button" aria-pressed=...>`.
   - Si déjà authentifié (`utilisateur != null`), redirection vers l'espace adapté (`ADMIN` -> `/admin/dashboard`, `LOCATAIRE` -> `/recherche`, `PROPRIETAIRE`/`AGENT` -> `/dashboard`) ou `state.from`.
   - Unification email contact (`contact@e-courtier.sn`) et retrait de la mention « version universitaire » dans `/connexion`.

6. **Transversal (`Footer`, `Header`, `Samba IA`, `AdminUtilisateursPage`) :**
   - Correction `Footer.tsx` : supprimer l'écrasement `lg:px-8` sur `sm:pr-44` (`lg:pl-8 lg:pr-44`) sur le footer compact et complet.
   - `AdminUtilisateursPage.tsx` : passer le tableau desktop à `hidden lg:block overflow-x-auto` (cartes sous `lg:` soit `< 1024 px`), réglant net la troncature à `768×1024` ; garder le libellé utilisateur clair sur bouton `Créer un compte`.
   - Ajouter un `padding-bottom` suffisant (`pb-20 sm:pb-24`) ou dégagement sur les listes contenant des CTA en bas à droite pour prévenir les collisions Samba IA sur `320 px` / `390 px` / `1024 px`.

---

## 11. Plan de transformation structuré et priorisé par lots

| Lot | Périmètre & intitulé | Priorité | Fichiers principaux concernés | Dépendances | Critères de validation |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Lot 3.A** | **Socle transverse, sécurité & corrections critiques `768×1024` / `Footer`** | **P0** | `App.tsx`, `ProtectedRoute`, `Avatar.tsx`, `Footer.tsx`, `Header.tsx`, `useConversationSocket.ts`, `roleLabels.ts` | Aucune (lot socle) | 0 image cassée offline sur `Avatar`, 0 chevauchement Samba/`Footer`, garde centralisée avec `state.from`, `Array.isArray` + `ErrorBoundary` en place, `/favoris/count` utilisé dans `Header`. |
| **Lot 3.B** | **Auth (`/connexion`, `/inscription`) & Hub `/mon-compte` + `/favoris`** | **P1** | `LoginPage.tsx`, `RegisterPage.tsx`, `MonComptePage.tsx`, `FavorisPage.tsx`, `Input.tsx` (option toggle password) | Lot 3.A | 0 ressource Unsplash externe, accessibilité clavier 100 % sur `/inscription`, politique mdp vérifiée avant POST, cartes `/favoris` alignées `/recherche` (`1 pièce`, statuts `Louée`/`Vendue`/`Suspendue`). |
| **Lot 3.C** | **Refonte `/dashboard` adaptatif par rôle + CRUD complet annonces & transactions** | **P0 / P1** | `DashboardPage.tsx`, `EditAnnonceModal.tsx`, `CreateAnnonceModal.tsx`, `SmsSimulesTab.tsx`, `LocationPickerMap.tsx` | Lot 3.A | 0 bug colonne vertical `768×1024`, suppression double header sombre, 0 appel `mes_annonces=true` pour `LOCATAIRE`, pagination + filtre annonces, gestion photos & justificatifs dans édition, `tx.fraisDossier` exact, libération des Blob URLs. |
| **Lot 3.D** | **Refonte Messagerie Sahel (`ConversationsTab`) & Tableau Admin `768 px`** | **P0 / P1** | `ConversationsTab.tsx`, `AdminUtilisateursPage.tsx`, `AdminNavTabs.tsx` | Lot 3.A, Lot 3.C | Messagerie 100 % palette Sahel, exploitable sans scroll horizontal ni écrasement sur `320×568` et `768×1024`, erreurs WebSocket affichées, `AdminUtilisateursPage` accessible sans coupure à `768×1024`. |
| **Lot 3.E** | **Validation QA Playwright 6 viewports & non-régression multi-rôles** | **P0** | Nouveaux specs `frontend/e2e/lot3-*.spec.ts` + re-exécution `home-redesign`, `lot2-1`, `lot2-2-*`, `phase2-security-ui`, `responsive-audit` | Lots 3.A–3.D | 0 régression sur `/`, `/recherche`, `/annonce/:id`, `0` overflow/collision sur les 6 viewports, `tsc`, `lint --max-warnings 0`, `vite build` verts. |

---

## 12. Risques, limites techniques et contraintes backend

1. **Aucune modification backend autorisée :**
   - **Édition des informations personnelles (nom, prénom, téléphone, avatar `photoProfilUrl`) :** `UtilisateurCompteController.java` ne fournit que `GET /api/v1/utilisateurs/moi` et `PUT /api/v1/utilisateurs/moi/mot-de-passe`. Le Lot 3 ne pourra pas rendre ces champs modifiables en base sans évolution backend ultérieure ; il doit donc exposer une vue propre, honnête (sans fausse sauvegarde mockée), prête à être branchée.
   - **Réinitialisation mot de passe oublié par email / OTP :** aucun endpoint public backend n'existe aujourd'hui ; le flux restera informatif (avec coordonnées cohérentes `contact@e-courtier.sn`).
   - **Historique de sessions actives / journal d'audit personnel / KYC utilisateur hors annonce :** non exposés côté API.
2. **Contraintes carte `LocationPickerMap.tsx` :**
   - Les tuiles `tile.openstreetmap.org` resteront dépendantes du réseau en production, mais les icônes de marqueur `unpkg.com` peuvent être remplacées par des assets/SVG Leaflet locaux embarqués dans le bundle Vite (`leaflet/dist/images/*` ou icône CSS `L.divIcon`).
3. **Préservation des sélecteurs E2E existants :**
   - Des tests existants (`phase2-security-ui.spec.ts`, `responsive-audit.spec.ts`, `lot2-1-foundation.spec.ts`, `capture-audit-screenshots.spec.ts`) s'appuient sur certains libellés exacts (ex. `Mes favoris`, `Administration`, `Retour aux conversations`, `Écrivez un message...`, `Changer mon mot de passe`, `Gestion des Utilisateurs`, `Créer un compte`, `Les comptes agents sont créés par l'administrateur`). Tout refactoring UI devra préserver ces ancrages sémantiques ou `aria-label`.
4. **Règle absolue sur Wave :**
   - Aucune intervention sur le module Wave, ses webhooks ou endpoints de paiement ; `TransactionDetailsModal.tsx` reste strictement préservé fonctionnellement.

---

## 13. Tableau de synthèse rapide

| Élément / Espace | Priorité | Statut actuel | Anomalies majeures vérifiées | Action cible (sans modifier le backend) |
| :--- | :--- | :--- | :--- | :--- |
| **`/dashboard` — Tab Annonces (`768×1024`)** | **P0** | Cassé tablette | Titre en vertical 1 lettre/ligne (`scrollH=3268`), `size=50` sans pagination, double header | Basculer sidebar à `lg:`, passer cartes en layout fluide `flex-1 min-w-0`, ajouter filtres/pagination |
| **`ConversationsTab` (`768×1024` & `320×568`)** | **P0** | Cassé tablette / mobile | Colonne chat 128 px (`Env...` coupé à `768 px`), input hors fold à `320 px`, palette Slate/Orange, `error` WS ignoré | Palette Sahel, mode panneau unique `< lg`, hauteur responsive mobile, remonter `error` + lien annonce |
| **`AdminUtilisateursPage` (`768×1024`)** | **P0** | Cassé tablette | Table `924 px` coupée par `overflow-hidden`, actions `Changer rôle`/`Désactiver` inaccessibles ; collision Samba `320/1024/1280` | Cartes jusqu'à `< lg` (`1024 px`), `overflow-x-auto` sur table desktop, dégagement bottom pour Samba |
| **Résilience & Routes (`App.tsx`)** | **P0** | Partielle | Pas de `ProtectedRoute`, `state.from` perdu sur `/dashboard` & Admin, pas d'`ErrorBoundary` ni check `Array.isArray` | Wrapper `<ProtectedRoute>`, `ErrorBoundary` global, validation tableaux WebSocket |
| **`Avatar.tsx` & Badges confiance** | **P1** | Dégradé offline | Image cassée `ui-avatars.com` ; `isVerified` forcé à `true` sur tous les utilisateurs dans `Dashboard` & `Detail` | Fallback monogramme SVG/CSS local Sahel ; conditionner `isVerified` au rôle/vérification réelle |
| **`Footer.tsx` (`lg:px-8` override)** | **P1** | Collision Samba IA | `lg:px-8` annule `sm:pr-44`, Samba IA recouvre `contact@e-courtier.sn` et colonne droite à `1280 px` | Remplacer par `lg:pl-8 lg:pr-44` sur footers compact & complet |
| **`/dashboard` — Rôles & Données** | **P1** | Partielle | `mes_annonces=true` appelé pour `LOCATAIRE`, `Frais: 5 000` hardcodé, wording proprio affiché aux locataires, `EditAnnonceModal` incomplet | Conditionner `mes_annonces` au rôle, afficher `tx.fraisDossier`, wording/KPI par rôle, édition photos+docs+360° |
| **`/favoris`** | **P1** | Partielle | Affiche `0 m²` et `1 pièces`, aucun statut `Louée`/`Vendue`/`Suspendue` | Unifier composant carte avec `/recherche`, ajouter statut publication & filtres rapides |
| **`/connexion` & `/inscription`** | **P1** | Partielle | Photos Unsplash externes, div rôles non clavier, tel `+221770000000` pré-rempli, pas de check mdp majuscule/chiffre | Visuels locaux, `<button aria-pressed>`, checklist mdp `RegisterPage`, œil mdp, unifier mail support |
| **`/mon-compte`** | **P2** | Minimale | Aucun appel `/utilisateurs/moi`, pas de réputation ni raccourcis espaces, rôle brut | Sync `/utilisateurs/moi`, libellés rôles traduits, carte réputation, raccourcis parcours & mention édition |
| **Cibles tactiles (`< 40 px`)** | **P2** | Insuffisante | `12` à `28` contrôles `< 40 px` (pilules `Dashboard`, `Button size="sm"`, `<select>` admin) | Monter hauteur min interactive mobile à `min-h-[44px]` sur contrôles tactiles critiques |

---

## 14. Verdict final & conditions d'autorisation

**Verdict d'audit : REFONTE FRONTEND REQUISE AVANT MISE EN PRODUCTION DES ESPACES UTILISATEURS.**

L'infrastructure sécurité/API sous-jacente est saine, mais l'interface actuelle des espaces profils présente des **défauts bloquants avérés** (en particulier sur `768×1024` et `320×568`), une dette visuelle incompatible avec le niveau atteint sur `/`, `/recherche` et `/annonce/:id`, ainsi qu'une expérience indifférenciée entre `LOCATAIRE`, `PROPRIETAIRE`, `AGENT` et `ADMIN`.

### Conditions pour passer à l'implémentation (démarrage Lot 3)

1. Validation explicite par l'utilisateur du découpage en lots **3.A -> 3.B -> 3.C -> 3.D -> 3.E**.
2. Confirmation du périmètre **100 % frontend** (sans création d'endpoints d'édition de profil/avatar ou d'email SMTP en backend, sauf décision explicite contraire).
3. Arbitrage optionnel sur l'exposition publique du numéro de téléphone propriétaire dans `PropertyDetailPage.tsx` (L948) vis-à-vis des visiteurs non connectés.
4. Maintien strict des invariants : préservation intégrale de `/`, `/recherche`, `/annonce/:id`, des lots 2.1/2.2 validés, aucune intervention Wave, aucune opération Git destructive.

---

*Audit arrêté en attente de validation explicite avant toute modification de code.*
