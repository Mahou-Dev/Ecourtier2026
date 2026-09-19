#!/bin/bash
set -e

BASE_URL="http://localhost:8080/api/v1"
echo "=== 1. CONNEXIONS (LOCATAIRE, PROPRIETAIRE, ADMIN) ==="

# Locataire
LOC_TOKEN=$(curl -s -X POST "$BASE_URL/auth/connexion" \
  -H "Content-Type: application/json" \
  -d '{"email":"diop.moussa@gmail.com","motDePasse":"Pass123!"}' | jq -r '.accessToken')

# Admin
ADMIN_TOKEN=$(curl -s -X POST "$BASE_URL/auth/connexion" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@ecourtier.sn","motDePasse":"AdminPass123!"}' | jq -r '.accessToken')

echo "Token Locataire: ${LOC_TOKEN:0:15}..."
echo "Token Admin: ${ADMIN_TOKEN:0:15}..."

echo ""
echo "=== 2. TEST RECHERCHE ANNONCES (AVEC FILTRE PROXIMITE ET EXCLUSION SUSPENDUE) ==="
curl -s -X GET "$BASE_URL/annonces?proximite=ECOLE" | jq '.totalElements, .content[0].titre'

echo ""
echo "=== 3. TEST ETAGE ET 360 ° DE LA FICHE ANNONCE ==="
ANNONCE_ID=$(curl -s -X GET "$BASE_URL/annonces" | jq -r '.content[0].id')
PROPRIETAIRE_ID=$(curl -s -X GET "$BASE_URL/annonces/$ANNONCE_ID" | jq -r '.auteur.id')
echo "Annonce ID: $ANNONCE_ID | Propriétaire ID: $PROPRIETAIRE_ID"
curl -s -X GET "$BASE_URL/annonces/$ANNONCE_ID" | jq '{id: .id, titre: .titre, etage: .etage, lienVisite360: .lienVisite360}'

echo ""
echo "=== 4. TEST REPUTATION PROPRIETAIRE ==="
curl -s -X GET "$BASE_URL/utilisateurs/$PROPRIETAIRE_ID/reputation" | jq '.'

echo ""
echo "=== 5. TEST CREATION AVIS ET DOUBLE NOTE CONFLIT (409) / SANS RDV (403) ==="
# Attempt avis without completed RDV or second time
curl -s -o /tmp/avis_err.json -w "%{http_code}\n" -X POST "$BASE_URL/annonces/$ANNONCE_ID/avis" \
  -H "Authorization: Bearer $LOC_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"note": 5, "commentaire": "Superbe logement, très propre !"}'
cat /tmp/avis_err.json

echo ""
echo "=== 6. TEST SIGNALEMENT D'ANNONCE + MODERATION ADMIN (SUSPENSION) ==="
SIG_RESP=$(curl -s -X POST "$BASE_URL/annonces/$ANNONCE_ID/signalements" \
  -H "Authorization: Bearer $LOC_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"motif": "FAUSSE_ANNONCE", "description": "Photos non conformes à la réalité."}')
echo "$SIG_RESP" | jq '.'
SIG_ID=$(echo "$SIG_RESP" | jq -r '.id')

echo "Liste signalements admin:"
curl -s -X GET "$BASE_URL/admin/signalements?statut=EN_ATTENTE" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.totalElements'

echo "Traitement signalement + suspension de l'annonce:"
curl -s -X PUT "$BASE_URL/admin/signalements/$SIG_ID/traiter" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"statut": "TRAITE", "suspendreAnnonce": true}' | jq '.'

echo "Vérification exclusion de la recherche publique:"
curl -s -X GET "$BASE_URL/annonces" | jq -r '.content[].id' | grep "$ANNONCE_ID" || echo "Annonce $ANNONCE_ID bien exclue des recherches publiques (Statut = SUSPENDUE)"
