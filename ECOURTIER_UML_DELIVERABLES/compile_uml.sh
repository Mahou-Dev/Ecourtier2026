#!/usr/bin/env bash
# ==============================================================================
# Script de compilation des diagrammes UML E-Courtier Sénégal
# Génère les formats SVG et PNG à partir des fichiers sources .puml
# ==============================================================================
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "$DIR"

echo "=== E-COURTIER SÉNÉGAL — COMPILATION DU DOSSIER UML ==="

# 1. Génération directe des SVG vectoriels haute résolution
if command -v python3 >/dev/null 2>&1; then
    echo "[INFO] Génération des 9 SVG vectoriels haute fidélité via generate_svgs.py..."
    python3 generate_svgs.py
else
    echo "[WARN] Python 3 n'est pas disponible pour la génération directe SVG."
fi

# 2. Compilation PlantUML native si plantuml ou plantuml.jar est présent
if command -v plantuml >/dev/null 2>&1; then
    echo "[INFO] Compilation avec l'exécutable système plantuml..."
    plantuml -tsvg *.puml -o "$DIR/svg_plantuml"
    plantuml -tpng *.puml -o "$DIR/png_plantuml"
    echo "[SUCCESS] Rendu PlantUML terminé."
elif [ -f "/usr/share/plantuml/plantuml.jar" ]; then
    echo "[INFO] Compilation avec /usr/share/plantuml/plantuml.jar..."
    java -jar /usr/share/plantuml/plantuml.jar -tsvg *.puml -o "$DIR/svg_plantuml"
    java -jar /usr/share/plantuml/plantuml.jar -tpng *.puml -o "$DIR/png_plantuml"
    echo "[SUCCESS] Rendu PlantUML terminé."
else
    echo "[INFO] PlantUML CLI non installé sur cet environnement hôte."
    echo "[INFO] Les 9 fichiers SVG officiels prêts à l'emploi sont disponibles dans $DIR/svg/"
    echo "[INFO] Les 9 fichiers sources .puml sont éditables avec l'extension PlantUML (VS Code / IntelliJ) ou sur https://www.plantuml.com/plantuml/uml/"
fi

echo "=== DOSSIER UML PRÊT DANS $DIR ==="
