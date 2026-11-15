#!/usr/bin/env python3
"""
Générateur de diagrammes vectoriels SVG haute fidélité pour le dossier UML E-Courtier Sénégal.
Génère 9 fichiers SVG professionnels correspondant aux 9 spécifications PlantUML .puml.
"""

import os

SVG_DIR = "/home/mahou-dev/Documents/Ecourtier2026/ECOURTIER_UML_DELIVERABLES/svg"
os.makedirs(SVG_DIR, exist_ok=True)

def write_svg(filename, content):
    filepath = os.path.join(SVG_DIR, filename)
    with open(filepath, "w", encoding="utf-8") as f:
        f.write(content.strip() + "\n")
    print(f"Généré : {filepath}")

# 1. use_cases_ecourtier.svg
svg_1 = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1100 800" width="100%" height="100%" style="background:#ffffff; font-family:'Inter', Arial, sans-serif;">
  <defs>
    <filter id="shadow" x="-5%" y="-5%" width="110%" height="110%"><feDropShadow dx="2" dy="2" stdDeviation="3" flood-opacity="0.1"/></filter>
  </defs>

  <rect x="20" y="20" width="1060" height="760" rx="12" fill="#F8FAFC" stroke="#CBD5E1" stroke-width="2"/>
  <text x="550" y="60" text-anchor="middle" font-size="22" font-weight="700" fill="#0F172A">E-COURTIER SÉNÉGAL — DIAGRAMME DES CAS D'UTILISATION (PAR RÔLE)</text>
  <text x="550" y="85" text-anchor="middle" font-size="13" fill="#64748B">Modélisation UML 2.5 des fonctionnalités majeures par profil utilisateur</text>

  <!-- Acteurs -->
  <g transform="translate(60, 140)">
    <circle cx="40" cy="30" r="18" fill="#E2E8F0" stroke="#475569" stroke-width="2"/>
    <line x1="40" y1="48" x2="40" y2="90" stroke="#475569" stroke-width="2"/>
    <line x1="15" y1="65" x2="65" y2="65" stroke="#475569" stroke-width="2"/>
    <line x1="40" y1="90" x2="20" y2="125" stroke="#475569" stroke-width="2"/>
    <line x1="40" y1="90" x2="60" y2="125" stroke="#475569" stroke-width="2"/>
    <text x="40" y="145" text-anchor="middle" font-size="13" font-weight="600" fill="#1E293B">Visiteur</text>
  </g>

  <g transform="translate(60, 310)">
    <circle cx="40" cy="30" r="18" fill="#DBEAFE" stroke="#2563EB" stroke-width="2"/>
    <line x1="40" y1="48" x2="40" y2="90" stroke="#2563EB" stroke-width="2"/>
    <line x1="15" y1="65" x2="65" y2="65" stroke="#2563EB" stroke-width="2"/>
    <line x1="40" y1="90" x2="20" y2="125" stroke="#2563EB" stroke-width="2"/>
    <line x1="40" y1="90" x2="60" y2="125" stroke="#2563EB" stroke-width="2"/>
    <text x="40" y="145" text-anchor="middle" font-size="13" font-weight="600" fill="#1E40AF">Locataire</text>
  </g>

  <g transform="translate(60, 480)">
    <circle cx="40" cy="30" r="18" fill="#FEF3C7" stroke="#D97706" stroke-width="2"/>
    <line x1="40" y1="48" x2="40" y2="90" stroke="#D97706" stroke-width="2"/>
    <line x1="15" y1="65" x2="65" y2="65" stroke="#D97706" stroke-width="2"/>
    <line x1="40" y1="90" x2="20" y2="125" stroke="#D97706" stroke-width="2"/>
    <line x1="40" y1="90" x2="60" y2="125" stroke="#D97706" stroke-width="2"/>
    <text x="40" y="145" text-anchor="middle" font-size="13" font-weight="600" fill="#B45309">Propriétaire / Agent</text>
  </g>

  <g transform="translate(940, 310)">
    <circle cx="40" cy="30" r="18" fill="#FEE2E2" stroke="#DC2626" stroke-width="2"/>
    <line x1="40" y1="48" x2="40" y2="90" stroke="#DC2626" stroke-width="2"/>
    <line x1="15" y1="65" x2="65" y2="65" stroke="#DC2626" stroke-width="2"/>
    <line x1="40" y1="90" x2="20" y2="125" stroke="#DC2626" stroke-width="2"/>
    <line x1="40" y1="90" x2="60" y2="125" stroke="#DC2626" stroke-width="2"/>
    <text x="40" y="145" text-anchor="middle" font-size="13" font-weight="600" fill="#991B1B">Administrateur</text>
  </g>

  <!-- Boîtes -->
  <rect x="220" y="110" width="310" height="290" rx="10" fill="#FFFFFF" stroke="#94A3B8" stroke-dasharray="4" filter="url(#shadow)"/>
  <text x="240" y="135" font-size="14" font-weight="700" fill="#0F172A">1. Découverte &amp; Compte</text>
  <rect x="240" y="150" width="270" height="40" rx="20" fill="#F1F5F9" stroke="#64748B"/>
  <text x="375" y="175" text-anchor="middle" font-size="12" fill="#1E293B">Rechercher annonces &amp; carte Leaflet</text>
  <rect x="240" y="200" width="270" height="40" rx="20" fill="#F1F5F9" stroke="#64748B"/>
  <text x="375" y="225" text-anchor="middle" font-size="12" fill="#1E293B">Assistant IA Samba (Critères &amp; Chat)</text>
  <rect x="240" y="250" width="270" height="40" rx="20" fill="#F1F5F9" stroke="#64748B"/>
  <text x="375" y="275" text-anchor="middle" font-size="12" fill="#1E293B">Inscription &amp; Connexion (JWT / Refresh)</text>
  <rect x="240" y="300" width="270" height="40" rx="20" fill="#F1F5F9" stroke="#64748B"/>
  <text x="375" y="325" text-anchor="middle" font-size="12" fill="#1E293B">Prise de RDV visite &amp; Rappel SMS</text>
  <rect x="240" y="350" width="270" height="40" rx="20" fill="#F1F5F9" stroke="#64748B"/>
  <text x="375" y="375" text-anchor="middle" font-size="12" fill="#1E293B">Messagerie instantanée STOMP</text>

  <rect x="220" y="420" width="310" height="330" rx="10" fill="#FFFFFF" stroke="#94A3B8" stroke-dasharray="4" filter="url(#shadow)"/>
  <text x="240" y="445" font-size="14" font-weight="700" fill="#0F172A">2. Séquestre &amp; Bail Numérique</text>
  <rect x="240" y="460" width="270" height="40" rx="20" fill="#EFF6FF" stroke="#2563EB"/>
  <text x="375" y="485" text-anchor="middle" font-size="12" font-weight="600" fill="#1E40AF">Initier dossier de transaction</text>
  <rect x="240" y="515" width="270" height="45" rx="20" fill="#FEF3C7" stroke="#D97706"/>
  <text x="375" y="535" text-anchor="middle" font-size="11" font-weight="600" fill="#92400E">Payer séquestre (Wave Sandbox)</text>
  <text x="375" y="550" text-anchor="middle" font-size="10" fill="#78350F">Caution + 1er Loyer + 5000 FCFA</text>
  <rect x="240" y="575" width="270" height="40" rx="20" fill="#ECFDF5" stroke="#059669"/>
  <text x="375" y="600" text-anchor="middle" font-size="12" font-weight="600" fill="#065F46">Signature du bail (Dakar horodaté)</text>
  <rect x="240" y="630" width="270" height="40" rx="20" fill="#ECFDF5" stroke="#059669"/>
  <text x="375" y="655" text-anchor="middle" font-size="12" font-weight="600" fill="#065F46">Génération &amp; Téléchargement PDF Bail</text>
  <rect x="240" y="685" width="270" height="45" rx="20" fill="#EFF6FF" stroke="#2563EB"/>
  <text x="375" y="705" text-anchor="middle" font-size="11" font-weight="600" fill="#1E40AF">Libération automatique des fonds</text>
  <text x="375" y="720" text-anchor="middle" font-size="10" fill="#1E3A8A">Après double signature locataire + bailleur</text>

  <rect x="580" y="110" width="320" height="640" rx="10" fill="#FFFFFF" stroke="#DC2626" stroke-dasharray="4" filter="url(#shadow)"/>
  <text x="600" y="135" font-size="14" font-weight="700" fill="#991B1B">3. Administration &amp; Contrôle</text>
  <rect x="605" y="160" width="270" height="50" rx="20" fill="#FEF2F2" stroke="#DC2626"/>
  <text x="740" y="185" text-anchor="middle" font-size="12" font-weight="600" fill="#991B1B">Audit documents fonciers / identité</text>
  <text x="740" y="200" text-anchor="middle" font-size="10" fill="#7F1D1D">Attribution / Refus du Badge Vérifié</text>
  <rect x="605" y="230" width="270" height="50" rx="20" fill="#FEF2F2" stroke="#DC2626"/>
  <text x="740" y="255" text-anchor="middle" font-size="12" font-weight="600" fill="#991B1B">Gestion statut annonces (CA3-004)</text>
  <text x="740" y="270" text-anchor="middle" font-size="10" fill="#7F1D1D">DISPONIBLE &lt;--&gt; SUSPENDUE</text>
  <rect x="605" y="300" width="270" height="45" rx="20" fill="#FEF2F2" stroke="#DC2626"/>
  <text x="740" y="325" text-anchor="middle" font-size="12" font-weight="600" fill="#991B1B">Modération &amp; Désactivation comptes</text>
  <text x="740" y="340" text-anchor="middle" font-size="10" fill="#7F1D1D">Révocation immédiate sessions REST / WS</text>
  <rect x="605" y="365" width="270" height="40" rx="20" fill="#FEF2F2" stroke="#DC2626"/>
  <text x="740" y="390" text-anchor="middle" font-size="12" font-weight="600" fill="#991B1B">Traitement des signalements d'arnaques</text>
  <rect x="605" y="425" width="270" height="50" rx="20" fill="#FEF2F2" stroke="#DC2626"/>
  <text x="740" y="450" text-anchor="middle" font-size="12" font-weight="600" fill="#991B1B">Tableau de bord financier (KPIs)</text>
  <text x="740" y="465" text-anchor="middle" font-size="10" fill="#7F1D1D">Volume séquestre bloqué, libéré &amp; commissions</text>
  <rect x="605" y="495" width="270" height="40" rx="20" fill="#FEF2F2" stroke="#DC2626"/>
  <text x="740" y="520" text-anchor="middle" font-size="12" font-weight="600" fill="#991B1B">Reprise forcée PDF bail en erreur</text>
  <rect x="605" y="555" width="270" height="50" rx="20" fill="#FEF2F2" stroke="#DC2626"/>
  <text x="740" y="580" text-anchor="middle" font-size="12" font-weight="600" fill="#991B1B">Gestion des litiges &amp; remboursements</text>
  <text x="740" y="595" text-anchor="middle" font-size="10" fill="#7F1D1D">Arbitrage séquestre en cas de désaccord</text>

  <!-- Liens -->
  <line x1="120" y1="200" x2="240" y2="170" stroke="#94A3B8" stroke-width="1.5"/>
  <line x1="120" y1="200" x2="240" y2="220" stroke="#94A3B8" stroke-width="1.5"/>
  <line x1="120" y1="200" x2="240" y2="270" stroke="#94A3B8" stroke-width="1.5"/>
  <line x1="130" y1="360" x2="240" y2="320" stroke="#2563EB" stroke-width="1.5"/>
  <line x1="130" y1="360" x2="240" y2="370" stroke="#2563EB" stroke-width="1.5"/>
  <line x1="130" y1="360" x2="240" y2="480" stroke="#2563EB" stroke-width="1.5"/>
  <line x1="130" y1="360" x2="240" y2="535" stroke="#2563EB" stroke-width="1.5"/>
  <line x1="130" y1="360" x2="240" y2="595" stroke="#2563EB" stroke-width="1.5"/>
  <line x1="140" y1="530" x2="240" y2="370" stroke="#D97706" stroke-width="1.5"/>
  <line x1="140" y1="530" x2="240" y2="595" stroke="#D97706" stroke-width="1.5"/>
  <line x1="140" y1="530" x2="240" y2="705" stroke="#D97706" stroke-width="1.5"/>
  <line x1="940" y1="360" x2="875" y2="185" stroke="#DC2626" stroke-width="1.5"/>
  <line x1="940" y1="360" x2="875" y2="255" stroke="#DC2626" stroke-width="1.5"/>
  <line x1="940" y1="360" x2="875" y2="320" stroke="#DC2626" stroke-width="1.5"/>
  <line x1="940" y1="360" x2="875" y2="385" stroke="#DC2626" stroke-width="1.5"/>
  <line x1="940" y1="360" x2="875" y2="450" stroke="#DC2626" stroke-width="1.5"/>
  <line x1="940" y1="360" x2="875" y2="515" stroke="#DC2626" stroke-width="1.5"/>
</svg>"""

# 2. class_diagram_domain.svg
svg_2 = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 880" width="100%" height="100%" style="background:#ffffff; font-family:'Inter', Arial, sans-serif;">
  <rect x="20" y="20" width="1160" height="840" rx="12" fill="#F8FAFC" stroke="#CBD5E1" stroke-width="2"/>
  <text x="600" y="55" text-anchor="middle" font-size="20" font-weight="700" fill="#0F172A">E-COURTIER SÉNÉGAL — DIAGRAMME DE CLASSES MÉTIER (JPA DOMAIN MODEL)</text>
  <text x="600" y="80" text-anchor="middle" font-size="12" fill="#64748B">Entités JPA 3, Attributs, Enums, Clés Étrangères et Contrôle de Concurrence @Version</text>

  <!-- UtilisateurEntity -->
  <g transform="translate(50, 110)">
    <rect width="250" height="240" rx="8" fill="#FFFFFF" stroke="#475569" stroke-width="1.5"/>
    <rect width="250" height="32" rx="8" fill="#F1F5F9" stroke="#475569" stroke-width="1.5"/>
    <text x="125" y="22" text-anchor="middle" font-size="13" font-weight="700" fill="#0F172A">&lt;&lt;Entity&gt;&gt; UtilisateurEntity</text>
    <text x="12" y="52" font-size="11" fill="#334155">- id : UUID &lt;&lt;PK&gt;&gt;</text>
    <text x="12" y="70" font-size="11" fill="#334155">- email : String &lt;&lt;Unique&gt;&gt;</text>
    <text x="12" y="88" font-size="11" fill="#334155">- motDePasse : String (BCrypt)</text>
    <text x="12" y="106" font-size="11" fill="#334155">- nom, prenom, telephone : String</text>
    <text x="12" y="124" font-size="11" fill="#334155">- role : RoleUtilisateur</text>
    <text x="12" y="142" font-size="11" fill="#334155">- actif : boolean</text>
    <text x="12" y="160" font-size="11" font-weight="600" fill="#DC2626">- tokenVersion : long (Révoc. JWT)</text>
    <text x="12" y="178" font-size="11" fill="#334155">- dateCreation : Instant</text>
    <line x1="0" y1="190" x2="250" y2="190" stroke="#CBD5E1"/>
    <text x="12" y="210" font-size="11" fill="#0369A1">+ isActif() : boolean</text>
    <text x="12" y="228" font-size="11" fill="#0369A1">+ incrementerTokenVersion() : void</text>
  </g>

  <!-- RefreshTokenEntity -->
  <g transform="translate(50, 390)">
    <rect width="250" height="180" rx="8" fill="#FFFFFF" stroke="#475569" stroke-width="1.5"/>
    <rect width="250" height="32" rx="8" fill="#F1F5F9" stroke="#475569" stroke-width="1.5"/>
    <text x="125" y="22" text-anchor="middle" font-size="13" font-weight="700" fill="#0F172A">&lt;&lt;Entity&gt;&gt; RefreshTokenEntity</text>
    <text x="12" y="52" font-size="11" fill="#334155">- id : UUID &lt;&lt;PK&gt;&gt;</text>
    <text x="12" y="70" font-size="11" fill="#334155">- tokenHash : String &lt;&lt;SHA-256&gt;&gt;</text>
    <text x="12" y="88" font-size="11" fill="#334155">- familleId : UUID</text>
    <text x="12" y="106" font-size="11" fill="#334155">- sequence : long</text>
    <text x="12" y="124" font-size="11" fill="#334155">- consomme, revoque : boolean</text>
    <text x="12" y="142" font-size="11" fill="#334155">- dateExpiration : Instant</text>
    <line x1="0" y1="150" x2="250" y2="150" stroke="#CBD5E1"/>
    <text x="12" y="168" font-size="11" fill="#0369A1">+ estValide(now) / revoquer()</text>
  </g>

  <!-- AnnonceEntity -->
  <g transform="translate(360, 110)">
    <rect width="280" height="360" rx="8" fill="#FFFFFF" stroke="#475569" stroke-width="1.5"/>
    <rect width="280" height="32" rx="8" fill="#F1F5F9" stroke="#475569" stroke-width="1.5"/>
    <text x="140" y="22" text-anchor="middle" font-size="13" font-weight="700" fill="#0F172A">&lt;&lt;Entity&gt;&gt; AnnonceEntity</text>
    <text x="12" y="52" font-size="11" fill="#334155">- id : UUID &lt;&lt;PK&gt;&gt;</text>
    <text x="12" y="70" font-size="11" fill="#334155">- titre, description : String</text>
    <text x="12" y="88" font-size="11" fill="#334155">- typeBien : TypeBien (APPART, VILLA...)</text>
    <text x="12" y="106" font-size="11" fill="#334155">- modeTransaction : ModeTransaction</text>
    <text x="12" y="124" font-size="11" fill="#334155">- statutPublication : StatutPublication</text>
    <text x="12" y="142" font-size="11" fill="#334155">- statutVerification : StatutVerification</text>
    <text x="12" y="160" font-size="11" fill="#334155">- prix, montantCaution : BigDecimal</text>
    <text x="12" y="178" font-size="11" fill="#334155">- superficie : Double, nombrePieces : int</text>
    <text x="12" y="196" font-size="11" fill="#334155">- quartier, ville : String</text>
    <text x="12" y="214" font-size="11" fill="#334155">- latitude, longitude : Double</text>
    <text x="12" y="232" font-size="11" fill="#334155">- badgeVerifie : boolean</text>
    <text x="12" y="250" font-size="11" fill="#334155">- supprime : boolean (Suppr. Logique)</text>
    <text x="12" y="268" font-size="11" font-weight="600" fill="#D97706">- version : Long &lt;&lt;@Version Optimistic&gt;&gt;</text>
    <text x="12" y="286" font-size="11" fill="#334155">- proprietaire : UtilisateurEntity &lt;&lt;FK&gt;&gt;</text>
    <line x1="0" y1="298" x2="280" y2="298" stroke="#CBD5E1"/>
    <text x="12" y="318" font-size="11" fill="#0369A1">+ estDisponible() : boolean</text>
    <text x="12" y="336" font-size="11" fill="#0369A1">+ suspendre() / reactiver()</text>
    <text x="12" y="352" font-size="11" fill="#0369A1">+ marquerSupprime() : void</text>
  </g>

  <!-- TransactionEntity -->
  <g transform="translate(690, 110)">
    <rect width="280" height="340" rx="8" fill="#FFFFFF" stroke="#475569" stroke-width="1.5"/>
    <rect width="280" height="32" rx="8" fill="#F1F5F9" stroke="#475569" stroke-width="1.5"/>
    <text x="140" y="22" text-anchor="middle" font-size="13" font-weight="700" fill="#0F172A">&lt;&lt;Entity&gt;&gt; TransactionEntity</text>
    <text x="12" y="52" font-size="11" fill="#334155">- id : UUID &lt;&lt;PK&gt;&gt;</text>
    <text x="12" y="70" font-size="11" fill="#334155">- montantLoyer : BigDecimal</text>
    <text x="12" y="88" font-size="11" fill="#334155">- montantCaution : BigDecimal</text>
    <text x="12" y="106" font-size="11" fill="#334155">- montantFraisPlateforme : 5000 FCFA</text>
    <text x="12" y="124" font-size="11" font-weight="600" fill="#059669">- montantTotalSequestre : BigDecimal</text>
    <text x="12" y="142" font-size="11" fill="#334155">- statutSequestre : StatutSequestre</text>
    <text x="12" y="160" font-size="11" fill="#334155">- statutBail : StatutBail</text>
    <text x="12" y="178" font-size="11" fill="#334155">- statutDocumentBail : StatutDocumentBail</text>
    <text x="12" y="196" font-size="11" fill="#334155">- referencePaiementWave : String</text>
    <text x="12" y="214" font-size="11" fill="#334155">- urlDocumentBail : String</text>
    <text x="12" y="232" font-size="11" fill="#334155">- pdfBailTentatives : int</text>
    <text x="12" y="250" font-size="11" fill="#334155">- locataire, proprietaire : Utilisateur &lt;&lt;FK&gt;&gt;</text>
    <text x="12" y="268" font-size="11" fill="#334155">- annonce : AnnonceEntity &lt;&lt;FK Unique&gt;&gt;</text>
    <line x1="0" y1="280" x2="280" y2="280" stroke="#CBD5E1"/>
    <text x="12" y="300" font-size="11" fill="#0369A1">+ estPretePourLiberation() : boolean</text>
    <text x="12" y="318" font-size="11" fill="#0369A1">+ signerLocataire() / signerProprietaire()</text>
    <text x="12" y="334" font-size="11" fill="#0369A1">+ libererFonds(now) : void</text>
  </g>

  <!-- RendezVousEntity -->
  <g transform="translate(360, 520)">
    <rect width="280" height="160" rx="8" fill="#FFFFFF" stroke="#475569" stroke-width="1.5"/>
    <rect width="280" height="32" rx="8" fill="#F1F5F9" stroke="#475569" stroke-width="1.5"/>
    <text x="140" y="22" text-anchor="middle" font-size="13" font-weight="700" fill="#0F172A">&lt;&lt;Entity&gt;&gt; RendezVousEntity</text>
    <text x="12" y="52" font-size="11" fill="#334155">- id : UUID &lt;&lt;PK&gt;&gt;</text>
    <text x="12" y="70" font-size="11" fill="#334155">- dateHeure : LocalDateTime</text>
    <text x="12" y="88" font-size="11" fill="#334155">- statut : StatutRendezVous</text>
    <text x="12" y="106" font-size="11" fill="#334155">- rappelSmsEnvoye : boolean</text>
    <text x="12" y="124" font-size="11" fill="#334155">- locataire, annonce : FK</text>
    <line x1="0" y1="132" x2="280" y2="132" stroke="#CBD5E1"/>
    <text x="12" y="150" font-size="11" fill="#0369A1">+ confirmer() / refuser() / annuler()</text>
  </g>

  <!-- SchedulerLockEntity -->
  <g transform="translate(690, 520)">
    <rect width="280" height="130" rx="8" fill="#FFFFFF" stroke="#475569" stroke-width="1.5"/>
    <rect width="280" height="32" rx="8" fill="#F1F5F9" stroke="#475569" stroke-width="1.5"/>
    <text x="140" y="22" text-anchor="middle" font-size="13" font-weight="700" fill="#0F172A">&lt;&lt;Entity&gt;&gt; SchedulerLockEntity</text>
    <text x="12" y="52" font-size="11" fill="#334155">- lockName : String &lt;&lt;PK&gt;&gt;</text>
    <text x="12" y="70" font-size="11" fill="#334155">- lockedBy : String</text>
    <text x="12" y="88" font-size="11" fill="#334155">- lockedUntil : Instant</text>
    <line x1="0" y1="96" x2="280" y2="96" stroke="#CBD5E1"/>
    <text x="12" y="116" font-size="11" fill="#0369A1">+ isLocked(now) : boolean</text>
  </g>

  <!-- Enums -->
  <g transform="translate(1010, 110)">
    <rect width="140" height="150" rx="6" fill="#FEF3C7" stroke="#D97706"/>
    <text x="70" y="22" text-anchor="middle" font-size="11" font-weight="700" fill="#92400E">&lt;&lt;Enum&gt;&gt; StatutSequestre</text>
    <text x="10" y="44" font-size="10" fill="#78350F">• INITIALISE</text>
    <text x="10" y="62" font-size="10" fill="#78350F">• BLOQUE</text>
    <text x="10" y="80" font-size="10" fill="#78350F">• LIBERE</text>
    <text x="10" y="98" font-size="10" fill="#78350F">• REMBOURSE</text>
    <text x="10" y="116" font-size="10" fill="#78350F">• LITIGE</text>
    <text x="10" y="134" font-size="10" fill="#78350F">• ANNULE</text>
  </g>

  <g transform="translate(1010, 280)">
    <rect width="140" height="120" rx="6" fill="#FEF3C7" stroke="#D97706"/>
    <text x="70" y="22" text-anchor="middle" font-size="11" font-weight="700" fill="#92400E">&lt;&lt;Enum&gt;&gt; StatutBail</text>
    <text x="10" y="44" font-size="10" fill="#78350F">• NON_SIGNE</text>
    <text x="10" y="62" font-size="10" fill="#78350F">• SIGNE_LOCATAIRE</text>
    <text x="10" y="80" font-size="10" fill="#78350F">• SIGNE_PROPRIETAIRE</text>
    <text x="10" y="98" font-size="10" fill="#78350F">• SIGNE_COMPLET</text>
  </g>

  <!-- Relations -->
  <line x1="175" y1="350" x2="175" y2="390" stroke="#475569" stroke-width="1.5"/>
  <text x="185" y="375" font-size="10" fill="#64748B">1..*</text>

  <line x1="300" y1="200" x2="360" y2="200" stroke="#475569" stroke-width="1.5"/>
  <text x="325" y="195" font-size="10" fill="#64748B">1..*</text>

  <line x1="640" y1="220" x2="690" y2="220" stroke="#475569" stroke-width="1.5"/>
  <text x="655" y="215" font-size="10" fill="#64748B">1..1</text>
</svg>"""

# 3. sequence_auth_jwt_rotation.svg
svg_3 = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1050 780" width="100%" height="100%" style="background:#ffffff; font-family:'Inter', Arial, sans-serif;">
  <rect x="20" y="20" width="1010" height="740" rx="12" fill="#F8FAFC" stroke="#CBD5E1" stroke-width="2"/>
  <text x="525" y="55" text-anchor="middle" font-size="20" font-weight="700" fill="#0F172A">SÉQUENCE : AUTHENTIFICATION, ROTATION STRICTE &amp; DÉTECTION DE REJEU</text>
  <text x="525" y="80" text-anchor="middle" font-size="12" fill="#64748B">Flux sécurisé avec validation du claim 'tv' (token_version) et révocation immédiate</text>

  <!-- Colonnes / Participants -->
  <g transform="translate(80, 110)">
    <rect width="100" height="36" rx="6" fill="#EFF6FF" stroke="#2563EB"/>
    <text x="50" y="22" text-anchor="middle" font-size="12" font-weight="600" fill="#1E40AF">Client SPA</text>
    <line x1="50" y1="36" x2="50" y2="600" stroke="#94A3B8" stroke-dasharray="4"/>
  </g>

  <g transform="translate(260, 110)">
    <rect width="110" height="36" rx="6" fill="#F1F5F9" stroke="#475569"/>
    <text x="55" y="22" text-anchor="middle" font-size="12" font-weight="600" fill="#0F172A">AuthController</text>
    <line x1="55" y1="36" x2="55" y2="600" stroke="#94A3B8" stroke-dasharray="4"/>
  </g>

  <g transform="translate(460, 110)">
    <rect width="100" height="36" rx="6" fill="#F1F5F9" stroke="#475569"/>
    <text x="50" y="22" text-anchor="middle" font-size="12" font-weight="600" fill="#0F172A">AuthService</text>
    <line x1="50" y1="36" x2="50" y2="600" stroke="#94A3B8" stroke-dasharray="4"/>
  </g>

  <g transform="translate(640, 110)">
    <rect width="100" height="36" rx="6" fill="#ECFDF5" stroke="#059669"/>
    <text x="50" y="22" text-anchor="middle" font-size="12" font-weight="600" fill="#065F46">JwtService</text>
    <line x1="50" y1="36" x2="50" y2="600" stroke="#94A3B8" stroke-dasharray="4"/>
  </g>

  <g transform="translate(820, 110)">
    <rect width="120" height="36" rx="6" fill="#EDE9FE" stroke="#7C3AED"/>
    <text x="60" y="22" text-anchor="middle" font-size="12" font-weight="600" fill="#5B21B6">PostgreSQL 16</text>
    <line x1="60" y1="36" x2="60" y2="600" stroke="#94A3B8" stroke-dasharray="4"/>
  </g>

  <!-- Phase 1 : Login -->
  <rect x="50" y="165" width="950" height="24" fill="#DBEAFE" opacity="0.5"/>
  <text x="60" y="181" font-size="11" font-weight="700" fill="#1E40AF">1. CONNEXION INITIALE (POST /api/v1/auth/connexion)</text>

  <line x1="130" y1="205" x2="315" y2="205" stroke="#2563EB" stroke-width="2"/>
  <polygon points="315,201 325,205 315,209" fill="#2563EB"/>
  <text x="220" y="198" text-anchor="middle" font-size="11" fill="#1E293B">email, motDePasse</text>

  <line x1="315" y1="225" x2="510" y2="225" stroke="#2563EB" stroke-width="1.5"/>
  <text x="410" y="220" text-anchor="middle" font-size="11" fill="#1E293B">connecter(email, pass)</text>

  <line x1="510" y1="245" x2="880" y2="245" stroke="#7C3AED" stroke-width="1.5"/>
  <text x="700" y="240" text-anchor="middle" font-size="11" fill="#5B21B6">SELECT user &amp; token_version (1)</text>

  <line x1="510" y1="265" x2="690" y2="265" stroke="#059669" stroke-width="1.5"/>
  <text x="600" y="260" text-anchor="middle" font-size="11" fill="#065F46">generateAccessToken(tv=1)</text>

  <line x1="510" y1="285" x2="880" y2="285" stroke="#7C3AED" stroke-width="1.5"/>
  <text x="700" y="280" text-anchor="middle" font-size="11" fill="#5B21B6">INSERT refresh_token (seq=1, fam=F1)</text>

  <line x1="315" y1="310" x2="130" y2="310" stroke="#059669" stroke-width="1.5" stroke-dasharray="4"/>
  <text x="220" y="305" text-anchor="middle" font-size="11" fill="#065F46">200 OK { JWT (tv:1), RT1 }</text>

  <!-- Phase 2 : Refresh -->
  <rect x="50" y="335" width="950" height="24" fill="#FEF3C7" opacity="0.5"/>
  <text x="60" y="351" font-size="11" font-weight="700" fill="#92400E">2. ROTATION STRICTE DU REFRESH TOKEN (POST /api/v1/auth/rafraichir-token)</text>

  <line x1="130" y1="375" x2="315" y2="375" stroke="#2563EB" stroke-width="2"/>
  <text x="220" y="370" text-anchor="middle" font-size="11" fill="#1E293B">rafraichir(RT1)</text>

  <line x1="510" y1="395" x2="880" y2="395" stroke="#7C3AED" stroke-width="1.5"/>
  <text x="700" y="390" text-anchor="middle" font-size="11" fill="#5B21B6">SELECT ... FOR UPDATE (consomme=F)</text>

  <line x1="510" y1="415" x2="880" y2="415" stroke="#7C3AED" stroke-width="1.5"/>
  <text x="700" y="410" text-anchor="middle" font-size="11" fill="#5B21B6">UPDATE RT1 (consomme=T) + INSERT RT2 (seq=2)</text>

  <line x1="315" y1="440" x2="130" y2="440" stroke="#059669" stroke-width="1.5" stroke-dasharray="4"/>
  <text x="220" y="435" text-anchor="middle" font-size="11" fill="#065F46">200 OK { JWT (tv:1), RT2 }</text>

  <!-- Phase 3 : Replay Attack -->
  <rect x="50" y="465" width="950" height="24" fill="#FEE2E2" opacity="0.7"/>
  <text x="60" y="481" font-size="11" font-weight="700" fill="#991B1B">3. DÉTECTION ATTAQUE PAR REJEU &amp; RÉVOCATION IMMÉDIATE DE LA FAMILLE ET DU TOKEN_VERSION</text>

  <line x1="130" y1="510" x2="315" y2="510" stroke="#DC2626" stroke-width="2"/>
  <text x="220" y="505" text-anchor="middle" font-size="11" font-weight="600" fill="#DC2626">Attaquant rejoue RT1 !</text>

  <line x1="510" y1="530" x2="880" y2="530" stroke="#DC2626" stroke-width="1.5"/>
  <text x="700" y="525" text-anchor="middle" font-size="11" fill="#DC2626">SELECT ... RT1 (consomme=TRUE !) -&gt; ALERTE REJEU</text>

  <line x1="510" y1="555" x2="880" y2="555" stroke="#DC2626" stroke-width="2"/>
  <text x="700" y="550" text-anchor="middle" font-size="11" font-weight="700" fill="#991B1B">UPDATE refresh_tokens SET revoque=true WHERE famille_id='F1'</text>

  <line x1="510" y1="580" x2="880" y2="580" stroke="#DC2626" stroke-width="2"/>
  <text x="700" y="575" text-anchor="middle" font-size="11" font-weight="700" fill="#991B1B">UPDATE utilisateurs SET token_version = token_version + 1 (1 -&gt; 2)</text>

  <line x1="315" y1="610" x2="130" y2="610" stroke="#DC2626" stroke-width="1.5" stroke-dasharray="4"/>
  <text x="220" y="605" text-anchor="middle" font-size="11" font-weight="600" fill="#DC2626">401 Unauthorized (Session compromise révoquée)</text>

  <!-- Phase 4 : Access Token Rejet -->
  <rect x="50" y="635" width="950" height="24" fill="#F1F5F9"/>
  <text x="60" y="651" font-size="11" font-weight="700" fill="#334155">4. TOUT ANCIEN JWT (tv=1) EST IMMÉDIATEMENT REJETÉ SUR LE FILTRE</text>

  <line x1="130" y1="680" x2="315" y2="680" stroke="#334155" stroke-width="1.5"/>
  <text x="220" y="675" text-anchor="middle" font-size="11" fill="#334155">GET /api/v1/utilisateurs/moi (Bearer JWT avec tv=1)</text>

  <line x1="315" y1="700" x2="880" y2="700" stroke="#334155" stroke-width="1.5"/>
  <text x="600" y="695" text-anchor="middle" font-size="11" fill="#334155">Filtre compare claim tv (1) avec DB token_version (2) -&gt; 1 != 2</text>

  <line x1="315" y1="725" x2="130" y2="725" stroke="#DC2626" stroke-width="2" stroke-dasharray="4"/>
  <text x="220" y="720" text-anchor="middle" font-size="11" font-weight="700" fill="#DC2626">HTTP 401 Unauthorized (Accès révoqué sans délai de grâce)</text>
</svg>"""

# 4. sequence_escrow_lifecycle.svg
svg_4 = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1050 780" width="100%" height="100%" style="background:#ffffff; font-family:'Inter', Arial, sans-serif;">
  <rect x="20" y="20" width="1010" height="740" rx="12" fill="#F8FAFC" stroke="#CBD5E1" stroke-width="2"/>
  <text x="525" y="55" text-anchor="middle" font-size="20" font-weight="700" fill="#0F172A">SÉQUENCE : CYCLE COMPLET DU SÉQUESTRE &amp; LIBÉRATION DES FONDS</text>
  <text x="525" y="80" text-anchor="middle" font-size="12" fill="#64748B">Blocage Wave Sandbox, Double Signature Dakar et Déblocage Automatique du Propriétaire</text>

  <!-- Acteurs et Composants -->
  <g transform="translate(60, 110)">
    <rect width="90" height="34" rx="6" fill="#DBEAFE" stroke="#2563EB"/>
    <text x="45" y="21" text-anchor="middle" font-size="11" font-weight="600" fill="#1E40AF">Locataire</text>
    <line x1="45" y1="34" x2="45" y2="610" stroke="#94A3B8" stroke-dasharray="4"/>
  </g>

  <g transform="translate(220, 110)">
    <rect width="120" height="34" rx="6" fill="#F1F5F9" stroke="#475569"/>
    <text x="60" y="21" text-anchor="middle" font-size="11" font-weight="600" fill="#0F172A">TransactionService</text>
    <line x1="60" y1="34" x2="60" y2="610" stroke="#94A3B8" stroke-dasharray="4"/>
  </g>

  <g transform="translate(410, 110)">
    <rect width="110" height="34" rx="6" fill="#FEF3C7" stroke="#D97706"/>
    <text x="55" y="21" text-anchor="middle" font-size="11" font-weight="600" fill="#92400E">Wave Sandbox</text>
    <line x1="55" y1="34" x2="55" y2="610" stroke="#94A3B8" stroke-dasharray="4"/>
  </g>

  <g transform="translate(600, 110)">
    <rect width="90" height="34" rx="6" fill="#FEF3C7" stroke="#D97706"/>
    <text x="45" y="21" text-anchor="middle" font-size="11" font-weight="600" fill="#B45309">Propriétaire</text>
    <line x1="45" y1="34" x2="45" y2="610" stroke="#94A3B8" stroke-dasharray="4"/>
  </g>

  <g transform="translate(760, 110)">
    <rect width="110" height="34" rx="6" fill="#ECFDF5" stroke="#059669"/>
    <text x="55" y="21" text-anchor="middle" font-size="11" font-weight="600" fill="#065F46">PdfGenerator</text>
    <line x1="55" y1="34" x2="55" y2="610" stroke="#94A3B8" stroke-dasharray="4"/>
  </g>

  <g transform="translate(930, 110)">
    <rect width="80" height="34" rx="6" fill="#EDE9FE" stroke="#7C3AED"/>
    <text x="40" y="21" text-anchor="middle" font-size="11" font-weight="600" fill="#5B21B6">Postgres</text>
    <line x1="40" y1="34" x2="40" y2="610" stroke="#94A3B8" stroke-dasharray="4"/>
  </g>

  <!-- 1. Initiation -->
  <line x1="105" y1="180" x2="280" y2="180" stroke="#2563EB" stroke-width="2"/>
  <text x="190" y="175" text-anchor="middle" font-size="11" fill="#1E293B">initier(annonceId, 250 000 FCFA)</text>

  <line x1="280" y1="205" x2="970" y2="205" stroke="#7C3AED" stroke-width="1.5"/>
  <text x="600" y="200" text-anchor="middle" font-size="10" fill="#5B21B6">Calcul séquestre (Caution 250k + Loyer 250k + Frais 5k = 505 000 FCFA) -&gt; INSERT tx (INITIALISE)</text>

  <!-- 2. Paiement Wave -->
  <line x1="105" y1="240" x2="280" y2="240" stroke="#2563EB" stroke-width="2"/>
  <text x="190" y="235" text-anchor="middle" font-size="11" fill="#1E293B">payerSequestre(txId)</text>

  <line x1="280" y1="265" x2="465" y2="265" stroke="#D97706" stroke-width="1.5"/>
  <text x="370" y="260" text-anchor="middle" font-size="11" fill="#92400E">executerPaiement(505 000 FCFA)</text>

  <line x1="465" y1="290" x2="280" y2="290" stroke="#059669" stroke-width="1.5" stroke-dasharray="4"/>
  <text x="370" y="285" text-anchor="middle" font-size="11" fill="#065F46">CONFIRMED (ref: WAVE-SANDBOX-001)</text>

  <line x1="280" y1="315" x2="970" y2="315" stroke="#7C3AED" stroke-width="1.5"/>
  <text x="600" y="310" text-anchor="middle" font-size="10" fill="#5B21B6">UPDATE tx SET statut_sequestre='BLOQUE' (Fonds séquestrés)</text>

  <!-- 3. Signature Locataire -->
  <line x1="105" y1="360" x2="280" y2="360" stroke="#2563EB" stroke-width="2"/>
  <text x="190" y="355" text-anchor="middle" font-size="11" fill="#1E293B">signerBail(locataire)</text>

  <line x1="280" y1="380" x2="970" y2="380" stroke="#7C3AED" stroke-width="1.5"/>
  <text x="600" y="375" text-anchor="middle" font-size="10" fill="#5B21B6">UPDATE tx SET statut_bail='SIGNE_LOCATAIRE', date_sig_loc=now()</text>

  <!-- 4. Signature Propriétaire -->
  <line x1="645" y1="420" x2="280" y2="420" stroke="#D97706" stroke-width="2"/>
  <text x="460" y="415" text-anchor="middle" font-size="11" fill="#92400E">signerBail(proprietaire)</text>

  <line x1="280" y1="445" x2="970" y2="445" stroke="#7C3AED" stroke-width="1.5"/>
  <text x="600" y="440" text-anchor="middle" font-size="10" fill="#5B21B6">UPDATE tx SET statut_bail='SIGNE_COMPLET', date_sig_prop=now()</text>

  <!-- 5. Déclenchement Automatique Libération -->
  <rect x="230" y="475" width="750" height="45" rx="6" fill="#ECFDF5" stroke="#059669"/>
  <text x="600" y="495" text-anchor="middle" font-size="11" font-weight="700" fill="#065F46">CONDITION SÉQUESTRE SATISFAITE : BLOQUE + SIGNE_COMPLET</text>
  <text x="600" y="510" text-anchor="middle" font-size="10" fill="#047857">Libération automatique : 500 000 FCFA au bailleur | 5 000 FCFA commission E-Courtier</text>

  <line x1="280" y1="540" x2="970" y2="540" stroke="#7C3AED" stroke-width="1.5"/>
  <text x="600" y="535" text-anchor="middle" font-size="10" fill="#5B21B6">UPDATE transactions SET statut_sequestre='LIBERE' &amp;&amp; UPDATE annonces SET statut='LOUEE'</text>

  <line x1="280" y1="575" x2="815" y2="575" stroke="#059669" stroke-width="2"/>
  <text x="540" y="570" text-anchor="middle" font-size="11" fill="#065F46">genererDocumentBail(txId) -&gt; Écriture atomique PDF OpenPDF</text>

  <line x1="815" y1="605" x2="970" y2="605" stroke="#7C3AED" stroke-width="1.5"/>
  <text x="890" y="600" text-anchor="middle" font-size="10" fill="#5B21B6">statut_document_bail='GENERE'</text>

  <line x1="280" y1="640" x2="645" y2="640" stroke="#059669" stroke-width="2" stroke-dasharray="4"/>
  <text x="460" y="635" text-anchor="middle" font-size="11" font-weight="600" fill="#065F46">Notification Propriétaire : Fonds débloqués + Bail PDF disponible</text>
</svg>"""

# 5. sequence_bail_pdf_signature.svg
svg_5 = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1050 780" width="100%" height="100%" style="background:#ffffff; font-family:'Inter', Arial, sans-serif;">
  <rect x="20" y="20" width="1010" height="740" rx="12" fill="#F8FAFC" stroke="#CBD5E1" stroke-width="2"/>
  <text x="525" y="55" text-anchor="middle" font-size="20" font-weight="700" fill="#0F172A">SÉQUENCE : GÉNÉRATION ATOMIQUE DU BAIL PDF &amp; REPRISE PAR SCHEDULER</text>
  <text x="525" y="80" text-anchor="middle" font-size="12" fill="#64748B">Écriture fichier temporaire, renommage atomique et auto-guérison sous verrou PostgreSQL</text>

  <!-- Participants -->
  <g transform="translate(60, 110)">
    <rect width="120" height="34" rx="6" fill="#F1F5F9" stroke="#475569"/>
    <text x="60" y="21" text-anchor="middle" font-size="11" font-weight="600" fill="#0F172A">TransactionService</text>
    <line x1="60" y1="34" x2="60" y2="600" stroke="#94A3B8" stroke-dasharray="4"/>
  </g>

  <g transform="translate(260, 110)">
    <rect width="130" height="34" rx="6" fill="#ECFDF5" stroke="#059669"/>
    <text x="65" y="21" text-anchor="middle" font-size="11" font-weight="600" fill="#065F46">PdfBailGenerator</text>
    <line x1="65" y1="34" x2="65" y2="600" stroke="#94A3B8" stroke-dasharray="4"/>
  </g>

  <g transform="translate(480, 110)">
    <rect width="110" height="34" rx="6" fill="#EFF6FF" stroke="#2563EB"/>
    <text x="55" y="21" text-anchor="middle" font-size="11" font-weight="600" fill="#1E40AF">Disque Local FS</text>
    <line x1="55" y1="34" x2="55" y2="600" stroke="#94A3B8" stroke-dasharray="4"/>
  </g>

  <g transform="translate(680, 110)">
    <rect width="130" height="34" rx="6" fill="#FEF3C7" stroke="#D97706"/>
    <text x="65" y="21" text-anchor="middle" font-size="11" font-weight="600" fill="#92400E">RecoveryScheduler</text>
    <line x1="65" y1="34" x2="65" y2="600" stroke="#94A3B8" stroke-dasharray="4"/>
  </g>

  <g transform="translate(890, 110)">
    <rect width="90" height="34" rx="6" fill="#EDE9FE" stroke="#7C3AED"/>
    <text x="45" y="21" text-anchor="middle" font-size="11" font-weight="600" fill="#5B21B6">PostgreSQL</text>
    <line x1="45" y1="34" x2="45" y2="600" stroke="#94A3B8" stroke-dasharray="4"/>
  </g>

  <!-- 1. Génération normale -->
  <line x1="120" y1="180" x2="325" y2="180" stroke="#059669" stroke-width="2"/>
  <text x="220" y="175" text-anchor="middle" font-size="11" fill="#065F46">genererDocumentBail(txId)</text>

  <line x1="325" y1="205" x2="535" y2="205" stroke="#2563EB" stroke-width="1.5"/>
  <text x="430" y="200" text-anchor="middle" font-size="10" fill="#1E40AF">1. Création bail_&lt;id&gt;.pdf.tmp (flux OpenPDF)</text>

  <line x1="325" y1="230" x2="535" y2="230" stroke="#2563EB" stroke-width="1.5"/>
  <text x="430" y="225" text-anchor="middle" font-size="10" fill="#1E40AF">2. Atomic Move : .tmp -&gt; bail_&lt;id&gt;.pdf</text>

  <line x1="325" y1="260" x2="935" y2="260" stroke="#7C3AED" stroke-width="1.5"/>
  <text x="630" y="255" text-anchor="middle" font-size="10" fill="#5B21B6">UPDATE tx SET statut_document_bail='GENERE', url='/uploads/baux/bail_&lt;id&gt;.pdf'</text>

  <!-- 2. Cas d'incident I/O -->
  <rect x="50" y="295" width="950" height="24" fill="#FEE2E2" opacity="0.6"/>
  <text x="60" y="311" font-size="11" font-weight="700" fill="#991B1B">CAS D'INCIDENT I/O DISQUE : CAPTURE &amp; TRANSITION VERS 'EN_ERREUR'</text>

  <line x1="325" y1="345" x2="535" y2="345" stroke="#DC2626" stroke-width="1.5"/>
  <text x="430" y="340" text-anchor="middle" font-size="10" fill="#DC2626">Panne disque / Coupure flux I/O</text>

  <line x1="325" y1="375" x2="935" y2="375" stroke="#DC2626" stroke-width="2"/>
  <text x="630" y="370" text-anchor="middle" font-size="11" font-weight="600" fill="#991B1B">UPDATE tx SET statut_document_bail='EN_ERREUR', pdf_bail_tentatives = pdf_bail_tentatives + 1</text>

  <!-- 3. Tâche de fond automatique -->
  <rect x="50" y="415" width="950" height="24" fill="#FEF3C7" opacity="0.6"/>
  <text x="60" y="431" font-size="11" font-weight="700" fill="#92400E">REPRISE PAR BAILPDFRECOVERYSCHEDULER TOUTES LES 10 MINUTES</text>

  <line x1="745" y1="465" x2="935" y2="465" stroke="#7C3AED" stroke-width="2"/>
  <text x="840" y="460" text-anchor="middle" font-size="11" fill="#5B21B6">Acquisition Verrou Multi-Instances (tryAcquireLock)</text>

  <line x1="745" y1="495" x2="935" y2="495" stroke="#7C3AED" stroke-width="1.5"/>
  <text x="840" y="490" text-anchor="middle" font-size="10" fill="#5B21B6">SELECT * WHERE statut='EN_ERREUR' AND tentatives &lt; 5</text>

  <line x1="745" y1="525" x2="325" y2="525" stroke="#059669" stroke-width="2"/>
  <text x="530" y="520" text-anchor="middle" font-size="11" font-weight="600" fill="#065F46">Régénération atomique automatique du document PDF</text>

  <line x1="325" y1="555" x2="535" y2="555" stroke="#059669" stroke-width="1.5"/>
  <text x="430" y="550" text-anchor="middle" font-size="10" fill="#065F46">Écriture réussie bail_&lt;id&gt;.pdf</text>

  <line x1="325" y1="585" x2="935" y2="585" stroke="#7C3AED" stroke-width="2"/>
  <text x="630" y="580" text-anchor="middle" font-size="11" font-weight="600" fill="#065F46">UPDATE tx SET statut_document_bail='GENERE' (Dossier réparé)</text>

  <line x1="745" y1="620" x2="935" y2="620" stroke="#7C3AED" stroke-width="1.5"/>
  <text x="840" y="615" text-anchor="middle" font-size="10" fill="#5B21B6">Libération du verrou distribué</text>
</svg>"""

# 6. component_architecture.svg
svg_6 = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1150 820" width="100%" height="100%" style="background:#ffffff; font-family:'Inter', Arial, sans-serif;">
  <rect x="20" y="20" width="1110" height="780" rx="12" fill="#F8FAFC" stroke="#CBD5E1" stroke-width="2"/>
  <text x="575" y="55" text-anchor="middle" font-size="20" font-weight="700" fill="#0F172A">ARCHITECTURE LOGICIELLE COMPLÈTE DES COMPOSANTS (C4 LEVEL 2)</text>
  <text x="575" y="80" text-anchor="middle" font-size="12" fill="#64748B">Découplage Frontend SPA React 19, Backend Spring Boot 3.4 modulaire et Persistance</text>

  <!-- Frontend Container -->
  <g transform="translate(50, 110)">
    <rect width="320" height="650" rx="10" fill="#EFF6FF" stroke="#2563EB" stroke-width="1.5"/>
    <text x="160" y="30" text-anchor="middle" font-size="14" font-weight="700" fill="#1E40AF">Frontend Client SPA (React 19 / Vite 6)</text>

    <rect x="20" y="50" width="280" height="60" rx="6" fill="#FFFFFF" stroke="#93C5FD"/>
    <text x="35" y="75" font-size="12" font-weight="600" fill="#1E3A8A">React Router &amp; Route Guards</text>
    <text x="35" y="95" font-size="10" fill="#64748B">Routage public, locataire, bailleur, admin</text>

    <rect x="20" y="125" width="280" height="60" rx="6" fill="#FFFFFF" stroke="#93C5FD"/>
    <text x="35" y="150" font-size="12" font-weight="600" fill="#1E3A8A">Zustand Auth Store (useAuthStore)</text>
    <text x="35" y="170" font-size="10" fill="#64748B">Gestion JWT, token_version, session sécurisée</text>

    <rect x="20" y="200" width="280" height="60" rx="6" fill="#FFFFFF" stroke="#93C5FD"/>
    <text x="35" y="225" font-size="12" font-weight="600" fill="#1E3A8A">TanStack React Query Cache</text>
    <text x="35" y="245" font-size="10" fill="#64748B">Mise en cache requêtes, optimistic locking UI</text>

    <rect x="20" y="275" width="280" height="60" rx="6" fill="#FFFFFF" stroke="#93C5FD"/>
    <text x="35" y="300" font-size="12" font-weight="600" fill="#1E3A8A">Leaflet &amp; OpenStreetMap</text>
    <text x="35" y="320" font-size="10" fill="#64748B">Cartographie Dakar, géolocalisation &amp; pins</text>

    <rect x="20" y="350" width="280" height="60" rx="6" fill="#FFFFFF" stroke="#93C5FD"/>
    <text x="35" y="375" font-size="12" font-weight="600" fill="#1E3A8A">STOMP.js &amp; SockJS WebSocket</text>
    <text x="35" y="395" font-size="10" fill="#64748B">Messagerie temps réel propriétaire-locataire</text>

    <rect x="20" y="425" width="280" height="60" rx="6" fill="#FFFFFF" stroke="#93C5FD"/>
    <text x="35" y="450" font-size="12" font-weight="600" fill="#1E3A8A">Service Worker PWA (Workbox)</text>
    <text x="35" y="470" font-size="10" fill="#64748B">Mode hors-ligne, 61 assets pré-cachés</text>

    <rect x="20" y="500" width="280" height="120" rx="6" fill="#FFFFFF" stroke="#93C5FD"/>
    <text x="35" y="525" font-size="12" font-weight="600" fill="#1E3A8A">Modules UI Spécialisés</text>
    <text x="35" y="545" font-size="10" fill="#64748B">• AiAssistantWidget (Chat IA Samba)</text>
    <text x="35" y="565" font-size="10" fill="#64748B">• TransactionDetailsModal (Séquestre)</text>
    <text x="35" y="585" font-size="10" fill="#64748B">• AdminVerificationPage (Audit annonces)</text>
    <text x="35" y="605" font-size="10" fill="#64748B">• CreateAnnonceModal &amp; EditAnnonceModal</text>
  </g>

  <!-- Backend Container -->
  <g transform="translate(420, 110)">
    <rect width="400" height="650" rx="10" fill="#F8FAFC" stroke="#059669" stroke-width="1.5"/>
    <text x="200" y="30" text-anchor="middle" font-size="14" font-weight="700" fill="#065F46">Backend Service (Spring Boot 3.4 / Java 21)</text>

    <!-- Sécurité -->
    <rect x="20" y="45" width="360" height="70" rx="6" fill="#FEF2F2" stroke="#DC2626"/>
    <text x="35" y="68" font-size="12" font-weight="700" fill="#991B1B">Filtres Sécurité &amp; Rate Limiting</text>
    <text x="35" y="86" font-size="10" fill="#7F1D1D">• JwtAuthenticationFilter (contrôle strict claim 'tv')</text>
    <text x="35" y="102" font-size="10" fill="#7F1D1D">• LoginRateLimiter (BoundedRateLimiter anti-DDoS)</text>

    <!-- Contrôleurs -->
    <rect x="20" y="130" width="360" height="110" rx="6" fill="#FFFFFF" stroke="#CBD5E1"/>
    <text x="35" y="152" font-size="12" font-weight="700" fill="#0F172A">Contrôleurs REST &amp; WebSocket (20 contrôleurs)</text>
    <text x="35" y="172" font-size="10" fill="#334155">• AuthController / UtilisateurCompteController</text>
    <text x="35" y="190" font-size="10" fill="#334155">• AnnonceController / AdminAnnonceController</text>
    <text x="35" y="208" font-size="10" fill="#334155">• TransactionController / IAController / Messagerie</text>
    <text x="35" y="226" font-size="10" fill="#334155">• AdminStatistiquesController / UploadController</text>

    <!-- Services Métier -->
    <rect x="20" y="255" width="360" height="190" rx="6" fill="#ECFDF5" stroke="#059669"/>
    <text x="35" y="278" font-size="12" font-weight="700" fill="#065F46">Services Métier Découplés</text>
    <text x="35" y="298" font-size="10" fill="#064E3B">• AuthService (rotation refresh tokens, révocation famille)</text>
    <text x="35" y="316" font-size="10" fill="#064E3B">• AnnonceService &amp; AnnonceStatutStateMachine</text>
    <text x="35" y="334" font-size="10" fill="#064E3B">• TransactionService (séquestre, baux, libération)</text>
    <text x="35" y="352" font-size="10" fill="#064E3B">• RendezVousService (gestion conflits &amp; SMS)</text>
    <text x="35" y="370" font-size="10" fill="#064E3B">• IAService &amp; LlmExtractionService (hybride)</text>
    <text x="35" y="388" font-size="10" fill="#064E3B">• PdfBailGeneratorService (OpenPDF horodaté Dakar)</text>
    <text x="35" y="406" font-size="10" fill="#064E3B">• BailPdfRecoveryScheduler (reprise automatique)</text>
    <text x="35" y="424" font-size="10" fill="#064E3B">• SchedulerLockService (verrou multi-instances PG)</text>

    <!-- Repositories JPA -->
    <rect x="20" y="460" width="360" height="160" rx="6" fill="#F1F5F9" stroke="#94A3B8"/>
    <text x="35" y="485" font-size="12" font-weight="700" fill="#0F172A">Couche Persistance Spring Data JPA</text>
    <text x="35" y="508" font-size="10" fill="#334155">• UtilisateurRepository &amp; RefreshTokenRepository</text>
    <text x="35" y="528" font-size="10" fill="#334155">• AnnonceRepository &amp; AnnonceSpecification (requêtes)</text>
    <text x="35" y="548" font-size="10" fill="#334155">• TransactionRepository (recherche index partiel actif)</text>
    <text x="35" y="568" font-size="10" fill="#334155">• RendezVousRepository &amp; MessagerieRepository</text>
    <text x="35" y="588" font-size="10" fill="#334155">• SchedulerLockRepository &amp; StatistiquesRepository</text>
  </g>

  <!-- Infrastructure & Externes -->
  <g transform="translate(865, 110)">
    <rect width="235" height="650" rx="10" fill="#FFFBEB" stroke="#D97706" stroke-width="1.5"/>
    <text x="117" y="30" text-anchor="middle" font-size="14" font-weight="700" fill="#92400E">Infrastructure &amp; Tiers</text>

    <!-- PostgreSQL -->
    <rect x="15" y="50" width="205" height="160" rx="6" fill="#EDE9FE" stroke="#7C3AED"/>
    <text x="30" y="75" font-size="12" font-weight="700" fill="#5B21B6">PostgreSQL 16.15</text>
    <text x="30" y="95" font-size="10" fill="#4C1D95">• 20 migrations Flyway V1..V20</text>
    <text x="30" y="113" font-size="10" fill="#4C1D95">• Contraintes FK ON DELETE</text>
    <text x="30" y="131" font-size="10" fill="#4C1D95">• Index uq_transactions_active</text>
    <text x="30" y="149" font-size="10" fill="#4C1D95">• Verrou scheduler_locks</text>
    <text x="30" y="167" font-size="10" fill="#4C1D95">• Schéma public isolé</text>
    <text x="30" y="185" font-size="10" fill="#4C1D95">• Volume docker_postgres_data</text>

    <!-- Groq -->
    <rect x="15" y="235" width="205" height="110" rx="6" fill="#FEF3C7" stroke="#D97706"/>
    <text x="30" y="260" font-size="12" font-weight="700" fill="#92400E">Groq Cloud AI</text>
    <text x="30" y="280" font-size="10" fill="#78350F">• API REST HTTPS /v1/chat</text>
    <text x="30" y="298" font-size="10" fill="#78350F">• Llama-3.3-70b-versatile</text>
    <text x="30" y="316" font-size="10" fill="#78350F">• Fallback heuristique local</text>

    <!-- Disque local -->
    <rect x="15" y="370" width="205" height="130" rx="6" fill="#FFFFFF" stroke="#94A3B8"/>
    <text x="30" y="395" font-size="12" font-weight="700" fill="#0F172A">Stockage Fichiers</text>
    <text x="30" y="415" font-size="10" fill="#334155">• /app/uploads/photos/</text>
    <text x="30" y="433" font-size="10" fill="#334155">• /app/uploads/documents/</text>
    <text x="30" y="451" font-size="10" fill="#334155">• /app/uploads/baux/ (PDF)</text>
    <text x="30" y="469" font-size="10" fill="#334155">• Téléchargement protégé</text>

    <!-- Wave Sandbox -->
    <rect x="15" y="525" width="205" height="100" rx="6" fill="#F1F5F9" stroke="#64748B"/>
    <text x="30" y="550" font-size="12" font-weight="700" fill="#334155">Wave Client (Sandbox)</text>
    <text x="30" y="570" font-size="10" fill="#64748B">• Simulation séquestre</text>
    <text x="30" y="588" font-size="10" fill="#64748B">• Caution + loyer + 5000</text>
    <text x="30" y="606" font-size="10" fill="#64748B">• Exclusion production</text>
  </g>

  <!-- Connecteurs -->
  <line x1="370" y1="200" x2="420" y2="200" stroke="#2563EB" stroke-width="2"/>
  <polygon points="415,196 425,200 415,204" fill="#2563EB"/>

  <line x1="820" y1="350" x2="865" y2="350" stroke="#059669" stroke-width="2"/>
  <polygon points="860,346 870,350 860,354" fill="#059669"/>
</svg>"""

# 7. deployment_topology.svg
svg_7 = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1150 800" width="100%" height="100%" style="background:#ffffff; font-family:'Inter', Arial, sans-serif;">
  <rect x="20" y="20" width="1110" height="760" rx="12" fill="#F8FAFC" stroke="#CBD5E1" stroke-width="2"/>
  <text x="575" y="55" text-anchor="middle" font-size="20" font-weight="700" fill="#0F172A">TOPOLOGIE DE DÉPLOIEMENT EN PRODUCTION HARDENED &amp; CONTENEURS</text>
  <text x="575" y="80" text-anchor="middle" font-size="12" fill="#64748B">Isolation réseau Docker, utilisateurs sans privilèges (non-root) et gestion des secrets</text>

  <!-- Utilisateur -->
  <g transform="translate(60, 240)">
    <circle cx="40" cy="30" r="18" fill="#DBEAFE" stroke="#2563EB" stroke-width="2"/>
    <line x1="40" y1="48" x2="40" y2="90" stroke="#2563EB" stroke-width="2"/>
    <line x1="15" y1="65" x2="65" y2="65" stroke="#2563EB" stroke-width="2"/>
    <line x1="40" y1="90" x2="20" y2="125" stroke="#2563EB" stroke-width="2"/>
    <line x1="40" y1="90" x2="60" y2="125" stroke="#2563EB" stroke-width="2"/>
    <text x="40" y="145" text-anchor="middle" font-size="12" font-weight="600" fill="#1E40AF">Client Web / PWA</text>
    <text x="40" y="160" text-anchor="middle" font-size="10" fill="#64748B">(Navigateur mobile/desktop)</text>
  </g>

  <!-- Hôte / Cloud -->
  <rect x="220" y="110" width="870" height="630" rx="10" fill="#FFFFFF" stroke="#334155" stroke-width="2"/>
  <text x="240" y="135" font-size="14" font-weight="700" fill="#0F172A">Hôte Linux Dédié / Cluster Cloud Render (docker-compose.prod.yml)</text>

  <!-- Conteneur Nginx Frontend -->
  <g transform="translate(250, 160)">
    <rect width="240" height="380" rx="8" fill="#EFF6FF" stroke="#2563EB" stroke-width="1.5"/>
    <text x="120" y="25" text-anchor="middle" font-size="12" font-weight="700" fill="#1E40AF">ecourtier-frontend-prod</text>
    <text x="120" y="42" text-anchor="middle" font-size="10" fill="#64748B">Nginx 1.27 Alpine Unprivileged</text>

    <rect x="15" y="60" width="210" height="45" rx="4" fill="#FFFFFF" stroke="#93C5FD"/>
    <text x="25" y="80" font-size="11" font-weight="600" fill="#1E3A8A">Port Écoute : 8080</text>
    <text x="25" y="95" font-size="9" fill="#64748B">Mapping hôte 80/443 -&gt; 8080</text>

    <rect x="15" y="120" width="210" height="70" rx="4" fill="#FFFFFF" stroke="#93C5FD"/>
    <text x="25" y="140" font-size="11" font-weight="600" fill="#1E3A8A">Sécurité Conteneur</text>
    <text x="25" y="156" font-size="9" fill="#1E293B">• Exécution non-root (UID 101)</text>
    <text x="25" y="170" font-size="9" fill="#1E293B">• Read-only rootfs (tmpfs /tmp)</text>
    <text x="25" y="184" font-size="9" fill="#1E293B">• Headers CSP, HSTS, X-Frame</text>

    <rect x="15" y="205" width="210" height="75" rx="4" fill="#FFFFFF" stroke="#93C5FD"/>
    <text x="25" y="225" font-size="11" font-weight="600" fill="#1E3A8A">Proxy Reverse Nginx</text>
    <text x="25" y="242" font-size="9" fill="#1E293B">• /api/v1 -&gt; backend:8080</text>
    <text x="25" y="256" font-size="9" fill="#1E293B">• /ws-ecourtier -&gt; backend:8080</text>
    <text x="25" y="270" font-size="9" fill="#1E293B">• Compression Gzip &amp; Cache PWA</text>

    <rect x="15" y="295" width="210" height="65" rx="4" fill="#ECFDF5" stroke="#059669"/>
    <text x="25" y="315" font-size="11" font-weight="600" fill="#065F46">Healthcheck Conteneur</text>
    <text x="25" y="332" font-size="9" fill="#047857">• curl -f http://127.0.0.1:8080/</text>
    <text x="25" y="348" font-size="9" fill="#047857">• Interval: 30s, Retries: 3</text>
  </g>

  <!-- Réseau Privé Interne -->
  <rect x="530" y="160" width="530" height="540" rx="8" fill="#F8FAFC" stroke="#059669" stroke-width="1.5" stroke-dasharray="4"/>
  <text x="550" y="185" font-size="12" font-weight="700" fill="#065F46">Réseau Privé Interne Isolé (ecourtier-net-prod / bridge)</text>

  <!-- Conteneur Backend -->
  <g transform="translate(550, 205)">
    <rect width="235" height="340" rx="8" fill="#FFFFFF" stroke="#059669" stroke-width="1.5"/>
    <text x="117" y="25" text-anchor="middle" font-size="12" font-weight="700" fill="#065F46">ecourtier-backend-prod</text>
    <text x="117" y="42" text-anchor="middle" font-size="10" fill="#64748B">Temurin 21 JRE Alpine</text>

    <rect x="15" y="60" width="205" height="65" rx="4" fill="#ECFDF5" stroke="#059669"/>
    <text x="25" y="80" font-size="11" font-weight="600" fill="#065F46">Sécurité Runtime</text>
    <text x="25" y="96" font-size="9" fill="#047857">• Utilisateur ecourtier (UID 1000)</text>
    <text x="25" y="110" font-size="9" fill="#047857">• Profil Spring 'prod' validé</text>

    <rect x="15" y="135" width="205" height="75" rx="4" fill="#ECFDF5" stroke="#059669"/>
    <text x="25" y="155" font-size="11" font-weight="600" fill="#065F46">Secrets Injectés (Env)</text>
    <text x="25" y="171" font-size="9" fill="#047857">• JWT_SECRET (&gt;= 32 car.)</text>
    <text x="25" y="185" font-size="9" fill="#047857">• POSTGRES_PASSWORD</text>
    <text x="25" y="199" font-size="9" fill="#047857">• GROQ_API_KEY (chiffré)</text>

    <rect x="15" y="220" width="205" height="100" rx="4" fill="#FFFFFF" stroke="#CBD5E1"/>
    <text x="25" y="240" font-size="11" font-weight="600" fill="#0F172A">Volumes &amp; Healthcheck</text>
    <text x="25" y="256" font-size="9" fill="#334155">• Volume /app/uploads monté</text>
    <text x="25" y="270" font-size="9" fill="#334155">• Chown UID 1000 au démarrage</text>
    <text x="25" y="284" font-size="9" fill="#334155">• Healthcheck /api/v1/health (200)</text>
    <text x="25" y="298" font-size="9" fill="#334155">• Flyway migre auto au start</text>
  </g>

  <!-- Conteneur PostgreSQL -->
  <g transform="translate(815, 205)">
    <rect width="220" height="340" rx="8" fill="#EDE9FE" stroke="#7C3AED" stroke-width="1.5"/>
    <text x="110" y="25" text-anchor="middle" font-size="12" font-weight="700" fill="#5B21B6">ecourtier-postgres-prod</text>
    <text x="110" y="42" text-anchor="middle" font-size="10" fill="#64748B">PostgreSQL 16.15 Bullseye</text>

    <rect x="15" y="60" width="190" height="70" rx="4" fill="#FFFFFF" stroke="#C4B5FD"/>
    <text x="25" y="80" font-size="11" font-weight="600" fill="#4C1D95">Isolation Totale</text>
    <text x="25" y="96" font-size="9" fill="#334155">• Port 5432 NON publié sur hôte</text>
    <text x="25" y="110" font-size="9" fill="#334155">• Accessible SEULEMENT par backend</text>
    <text x="25" y="124" font-size="9" fill="#334155">• Aucune IP externe routée</text>

    <rect x="15" y="140" width="190" height="85" rx="4" fill="#FFFFFF" stroke="#C4B5FD"/>
    <text x="25" y="160" font-size="11" font-weight="600" fill="#4C1D95">Persistance des Données</text>
    <text x="25" y="176" font-size="9" fill="#334155">• Volume : postgres_prod_data</text>
    <text x="25" y="190" font-size="9" fill="#334155">• Montage sur /var/lib/postgresql/data</text>
    <text x="25" y="204" font-size="9" fill="#334155">• Données résilientes aux reboots</text>

    <rect x="15" y="235" width="190" height="85" rx="4" fill="#FFFFFF" stroke="#C4B5FD"/>
    <text x="25" y="255" font-size="11" font-weight="600" fill="#4C1D95">Healthcheck Natif</text>
    <text x="25" y="271" font-size="9" fill="#334155">• pg_isready -U ecourtier</text>
    <text x="25" y="285" font-size="9" fill="#334155">• Démarrage backend conditionné :</text>
    <text x="25" y="299" font-size="9" font-weight="600" fill="#059669">  service_healthy obligatoire</text>
  </g>

  <!-- Flèches -->
  <line x1="140" y1="310" x2="250" y2="310" stroke="#2563EB" stroke-width="2"/>
  <polygon points="245,306 255,310 245,314" fill="#2563EB"/>
  <text x="195" y="300" text-anchor="middle" font-size="10" font-weight="600" fill="#1E40AF">HTTPS 443</text>

  <line x1="490" y1="340" x2="550" y2="340" stroke="#2563EB" stroke-width="2"/>
  <polygon points="545,336 555,340 545,344" fill="#2563EB"/>
  <text x="520" y="330" text-anchor="middle" font-size="10" font-weight="600" fill="#2563EB">Proxy :8080</text>

  <line x1="785" y1="340" x2="815" y2="340" stroke="#7C3AED" stroke-width="2"/>
  <polygon points="810,336 820,340 810,344" fill="#7C3AED"/>
  <text x="800" y="330" text-anchor="middle" font-size="10" font-weight="600" fill="#7C3AED">JDBC :5432</text>
</svg>"""

# 8. state_machine_annonces_transactions.svg
svg_8 = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1150 780" width="100%" height="100%" style="background:#ffffff; font-family:'Inter', Arial, sans-serif;">
  <rect x="20" y="20" width="1110" height="740" rx="12" fill="#F8FAFC" stroke="#CBD5E1" stroke-width="2"/>
  <text x="575" y="55" text-anchor="middle" font-size="20" font-weight="700" fill="#0F172A">DIAGRAMME D'ÉTATS : CYCLES DE VIE DES ANNONCES &amp; TRANSACTIONS SÉQUESTRE</text>
  <text x="575" y="80" text-anchor="middle" font-size="12" fill="#64748B">Automates d'états finis avec règles d'admissibilité des transitions et synchronisation</text>

  <!-- Cycle de vie Annonce -->
  <g transform="translate(50, 110)">
    <rect width="480" height="610" rx="10" fill="#EFF6FF" stroke="#2563EB" stroke-width="1.5"/>
    <text x="240" y="30" text-anchor="middle" font-size="13" font-weight="700" fill="#1E40AF">1. CYCLE DE VIE D'UNE ANNONCE IMMOBILIÈRE</text>

    <!-- États -->
    <rect x="180" y="60" width="120" height="40" rx="8" fill="#F1F5F9" stroke="#64748B"/>
    <text x="240" y="85" text-anchor="middle" font-size="12" font-weight="600" fill="#1E293B">BROUILLON</text>

    <rect x="40" y="160" width="170" height="45" rx="8" fill="#FEF3C7" stroke="#D97706"/>
    <text x="125" y="180" text-anchor="middle" font-size="11" font-weight="600" fill="#92400E">EN_ATTENTE_</text>
    <text x="125" y="195" text-anchor="middle" font-size="11" font-weight="600" fill="#92400E">VERIFICATION</text>

    <rect x="270" y="160" width="170" height="45" rx="8" fill="#FEE2E2" stroke="#DC2626"/>
    <text x="355" y="187" text-anchor="middle" font-size="12" font-weight="600" fill="#991B1B">REJETEE</text>

    <rect x="155" y="270" width="170" height="50" rx="8" fill="#DCFCE7" stroke="#059669" stroke-width="2"/>
    <text x="240" y="295" text-anchor="middle" font-size="13" font-weight="700" fill="#065F46">DISPONIBLE</text>
    <text x="240" y="310" text-anchor="middle" font-size="9" fill="#047857">(Visible recherche publique)</text>

    <rect x="40" y="390" width="170" height="50" rx="8" fill="#FEE2E2" stroke="#DC2626"/>
    <text x="125" y="415" text-anchor="middle" font-size="12" font-weight="700" fill="#991B1B">SUSPENDUE</text>
    <text x="125" y="430" text-anchor="middle" font-size="9" fill="#7F1D1D">(Action Admin CA3-004)</text>

    <rect x="270" y="390" width="170" height="50" rx="8" fill="#EFF6FF" stroke="#2563EB"/>
    <text x="355" y="415" text-anchor="middle" font-size="12" font-weight="700" fill="#1E40AF">LOUEE / VENDUE</text>
    <text x="355" y="430" text-anchor="middle" font-size="9" fill="#1E3A8A">(Séquestre libéré)</text>

    <rect x="155" y="520" width="170" height="45" rx="8" fill="#F1F5F9" stroke="#94A3B8"/>
    <text x="240" y="542" text-anchor="middle" font-size="11" font-weight="600" fill="#64748B">SUPPRIMÉE LOGIQUE</text>
    <text x="240" y="555" text-anchor="middle" font-size="9" fill="#94A3B8">(supprime=true)</text>

    <!-- Transitions -->
    <line x1="240" y1="100" x2="125" y2="160" stroke="#2563EB" stroke-width="1.5"/>
    <line x1="125" y1="205" x2="240" y2="270" stroke="#059669" stroke-width="2"/>
    <text x="150" y="240" font-size="9" fill="#065F46">Audit Admin OK</text>

    <line x1="200" y1="320" x2="125" y2="390" stroke="#DC2626" stroke-width="2"/>
    <text x="135" y="355" font-size="9" fill="#DC2626">Suspendre</text>

    <line x1="125" y1="390" x2="200" y2="320" stroke="#059669" stroke-width="2" stroke-dasharray="3"/>
    <text x="190" y="365" font-size="9" fill="#059669">Réactiver</text>

    <line x1="280" y1="320" x2="355" y2="390" stroke="#2563EB" stroke-width="2"/>
    <text x="340" y="355" font-size="9" fill="#2563EB">Bail complet</text>

    <line x1="240" y1="320" x2="240" y2="520" stroke="#94A3B8" stroke-width="1.5"/>
  </g>

  <!-- Cycle de vie Transaction / Séquestre -->
  <g transform="translate(580, 110)">
    <rect width="520" height="610" rx="10" fill="#FFFBEB" stroke="#D97706" stroke-width="1.5"/>
    <text x="260" y="30" text-anchor="middle" font-size="13" font-weight="700" fill="#92400E">2. CYCLE DE VIE SÉQUESTRE &amp; CONTRAT DE BAIL</text>

    <!-- États -->
    <rect x="180" y="60" width="160" height="45" rx="8" fill="#F1F5F9" stroke="#64748B"/>
    <text x="260" y="82" text-anchor="middle" font-size="12" font-weight="600" fill="#1E293B">INITIALISE</text>
    <text x="260" y="96" text-anchor="middle" font-size="9" fill="#64748B">(Dossier créé, expire 48h)</text>

    <rect x="370" y="150" width="120" height="40" rx="8" fill="#FEE2E2" stroke="#DC2626"/>
    <text x="430" y="175" text-anchor="middle" font-size="11" font-weight="600" fill="#991B1B">ANNULE</text>

    <rect x="160" y="180" width="200" height="55" rx="8" fill="#FEF3C7" stroke="#D97706" stroke-width="2"/>
    <text x="260" y="205" text-anchor="middle" font-size="13" font-weight="700" fill="#92400E">SEQUESTRE_BLOQUE</text>
    <text x="260" y="222" text-anchor="middle" font-size="9" fill="#78350F">(Caution + Loyer + 5000 consignés)</text>

    <!-- Sous-état Bail -->
    <rect x="120" y="270" width="280" height="150" rx="8" fill="#FFFFFF" stroke="#059669" stroke-dasharray="4"/>
    <text x="260" y="290" text-anchor="middle" font-size="11" font-weight="700" fill="#065F46">Processus de Double Signature Électronique</text>

    <rect x="140" y="310" width="240" height="30" rx="4" fill="#EFF6FF" stroke="#2563EB"/>
    <text x="260" y="330" text-anchor="middle" font-size="10" font-weight="600" fill="#1E40AF">1. Signature Locataire (SIGNE_LOCATAIRE)</text>

    <rect x="140" y="360" width="240" height="30" rx="4" fill="#ECFDF5" stroke="#059669"/>
    <text x="260" y="380" text-anchor="middle" font-size="10" font-weight="600" fill="#065F46">2. Signature Propriétaire (SIGNE_COMPLET)</text>

    <rect x="40" y="470" width="130" height="45" rx="8" fill="#FEE2E2" stroke="#DC2626"/>
    <text x="105" y="492" text-anchor="middle" font-size="11" font-weight="600" fill="#991B1B">LITIGE</text>
    <text x="105" y="505" text-anchor="middle" font-size="9" fill="#7F1D1D">(Arbitrage admin)</text>

    <rect x="180" y="520" width="160" height="50" rx="8" fill="#DCFCE7" stroke="#059669" stroke-width="2"/>
    <text x="260" y="542" text-anchor="middle" font-size="13" font-weight="700" fill="#065F46">LIBERE</text>
    <text x="260" y="558" text-anchor="middle" font-size="9" fill="#047857">(Reversé au bailleur)</text>

    <rect x="360" y="520" width="130" height="50" rx="8" fill="#F1F5F9" stroke="#64748B"/>
    <text x="425" y="542" text-anchor="middle" font-size="12" font-weight="600" fill="#334155">REMBOURSE</text>
    <text x="425" y="558" text-anchor="middle" font-size="9" fill="#64748B">(Restitué au locataire)</text>

    <!-- Transitions -->
    <line x1="260" y1="105" x2="260" y2="180" stroke="#D97706" stroke-width="2"/>
    <text x="270" y="145" font-size="10" fill="#92400E">Paiement Wave OK</text>

    <line x1="340" y1="80" x2="370" y2="150" stroke="#DC2626" stroke-width="1.5"/>
    <text x="375" y="110" font-size="9" fill="#DC2626">Timeout 48h</text>

    <line x1="260" y1="235" x2="260" y2="270" stroke="#059669" stroke-width="2"/>
    <line x1="260" y1="420" x2="260" y2="520" stroke="#059669" stroke-width="2"/>
    <text x="270" y="460" font-size="10" font-weight="700" fill="#065F46">Double signature validée</text>
  </g>
</svg>"""

# 9. cicd_pipeline_architecture.svg
svg_9 = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1150 820" width="100%" height="100%" style="background:#ffffff; font-family:'Inter', Arial, sans-serif;">
  <rect x="20" y="20" width="1110" height="780" rx="12" fill="#F8FAFC" stroke="#CBD5E1" stroke-width="2"/>
  <text x="575" y="55" text-anchor="middle" font-size="20" font-weight="700" fill="#0F172A">CHAÎNE CI/CD GITHUB ACTIONS &amp; LIVRAISON CONTINUE SÉCURISÉE</text>
  <text x="575" y="80" text-anchor="middle" font-size="12" fill="#64748B">Pipeline automatisé en 6 phases séquentielles avec barrière de validation Staging</text>

  <!-- Déclencheur -->
  <g transform="translate(60, 110)">
    <rect width="1000" height="40" rx="8" fill="#EFF6FF" stroke="#2563EB"/>
    <text x="500" y="25" text-anchor="middle" font-size="12" font-weight="700" fill="#1E40AF">DÉCLENCHEURS : Push sur 'main', Pull Request, ou Déclenchement Manuel (workflow_dispatch)</text>
  </g>

  <!-- Étapes du Pipeline -->
  <g transform="translate(60, 175)">
    <!-- Étape 1 -->
    <rect y="0" width="150" height="420" rx="8" fill="#FFFFFF" stroke="#475569" stroke-width="1.5"/>
    <rect y="0" width="150" height="32" rx="8" fill="#F1F5F9"/>
    <text x="75" y="21" text-anchor="middle" font-size="11" font-weight="700" fill="#0F172A">1. Précontrôles</text>
    <text x="12" y="55" font-size="10" fill="#334155">• Arborescence repo</text>
    <text x="12" y="75" font-size="10" fill="#334155">• Unicité Flyway</text>
    <text x="12" y="90" font-size="9" fill="#64748B">  (V1..V20 sans doublon)</text>
    <text x="12" y="115" font-size="10" fill="#334155">• Scan Anti-Secrets</text>
    <text x="12" y="130" font-size="9" fill="#64748B">  (pas de .env/.pem)</text>
    <text x="12" y="155" font-size="10" fill="#334155">• Présence Dockerfiles</text>
    <text x="12" y="175" font-size="10" fill="#334155">• Formatage YAML</text>
    <rect x="15" y="360" width="120" height="30" rx="4" fill="#ECFDF5" stroke="#059669"/>
    <text x="75" y="380" text-anchor="middle" font-size="10" font-weight="700" fill="#065F46">STATUT : VERT</text>

    <!-- Étape 2 -->
    <rect x="170" y="0" width="150" height="420" rx="8" fill="#FFFFFF" stroke="#059669" stroke-width="1.5"/>
    <rect x="170" y="0" width="150" height="32" rx="8" fill="#ECFDF5"/>
    <text x="245" y="21" text-anchor="middle" font-size="11" font-weight="700" fill="#065F46">2. Backend Java 21</text>
    <text x="182" y="55" font-size="10" fill="#064E3B">• Temurin JDK 21 LTS</text>
    <text x="182" y="75" font-size="10" fill="#064E3B">• Maven Compile</text>
    <text x="182" y="95" font-size="10" fill="#064E3B">• 395 tests unitaires</text>
    <text x="182" y="115" font-size="10" fill="#064E3B">• JUnit 5 Surefire</text>
    <text x="182" y="135" font-size="10" fill="#064E3B">• Contrôle JaCoCo</text>
    <text x="182" y="150" font-size="9" font-weight="600" fill="#059669">  (Seuil requis: 60%)</text>
    <text x="182" y="165" font-size="9" font-weight="700" fill="#059669">  (Réel : 86.85%)</text>
    <rect x="185" y="360" width="120" height="30" rx="4" fill="#ECFDF5" stroke="#059669"/>
    <text x="245" y="380" text-anchor="middle" font-size="10" font-weight="700" fill="#065F46">STATUT : VERT</text>

    <!-- Étape 3 -->
    <rect x="340" y="0" width="150" height="420" rx="8" fill="#FFFFFF" stroke="#7C3AED" stroke-width="1.5"/>
    <rect x="340" y="0" width="150" height="32" rx="8" fill="#EDE9FE"/>
    <text x="415" y="21" text-anchor="middle" font-size="11" font-weight="700" fill="#5B21B6">3. PostgreSQL 16</text>
    <text x="352" y="55" font-size="10" fill="#4C1D95">• Testcontainers PG16</text>
    <text x="352" y="75" font-size="10" fill="#4C1D95">• Exécution Flyway</text>
    <text x="352" y="90" font-size="9" fill="#5B21B6">  (V1 à V20 à blanc)</text>
    <text x="352" y="115" font-size="10" fill="#4C1D95">• Contraintes d'unicité</text>
    <text x="352" y="135" font-size="10" fill="#4C1D95">• Index partiel actif</text>
    <text x="352" y="155" font-size="10" fill="#4C1D95">• Verrous multi-inst.</text>
    <text x="352" y="175" font-size="10" fill="#4C1D95">• Reprise PDF en base</text>
    <rect x="355" y="360" width="120" height="30" rx="4" fill="#ECFDF5" stroke="#059669"/>
    <text x="415" y="380" text-anchor="middle" font-size="10" font-weight="700" fill="#065F46">STATUT : VERT</text>

    <!-- Étape 4 -->
    <rect x="510" y="0" width="150" height="420" rx="8" fill="#FFFFFF" stroke="#2563EB" stroke-width="1.5"/>
    <rect x="510" y="0" width="150" height="32" rx="8" fill="#EFF6FF"/>
    <text x="585" y="21" text-anchor="middle" font-size="11" font-weight="700" fill="#1E40AF">4. Frontend Web</text>
    <text x="522" y="55" font-size="10" fill="#1E3A8A">• Node 20 LTS</text>
    <text x="522" y="75" font-size="10" fill="#1E3A8A">• npm ci (reproductible)</text>
    <text x="522" y="95" font-size="10" fill="#1E3A8A">• ESLint strict</text>
    <text x="522" y="110" font-size="9" fill="#2563EB">  (--max-warnings 0)</text>
    <text x="522" y="130" font-size="10" fill="#1E3A8A">• TypeScript (tsc)</text>
    <text x="522" y="150" font-size="10" fill="#1E3A8A">• Build Vite 6</text>
    <text x="522" y="170" font-size="10" fill="#1E3A8A">• Service Worker PWA</text>
    <rect x="525" y="360" width="120" height="30" rx="4" fill="#ECFDF5" stroke="#059669"/>
    <text x="585" y="380" text-anchor="middle" font-size="10" font-weight="700" fill="#065F46">STATUT : VERT</text>

    <!-- Étape 5 -->
    <rect x="680" y="0" width="150" height="420" rx="8" fill="#FFFFFF" stroke="#D97706" stroke-width="1.5"/>
    <rect x="680" y="0" width="150" height="32" rx="8" fill="#FEF3C7"/>
    <text x="755" y="21" text-anchor="middle" font-size="11" font-weight="700" fill="#92400E">5. E2E Playwright</text>
    <text x="692" y="55" font-size="10" fill="#78350F">• Backend réel :18080</text>
    <text x="692" y="75" font-size="10" fill="#78350F">• Frontend preview :4173</text>
    <text x="692" y="95" font-size="10" fill="#78350F">• Chromium Headless</text>
    <text x="692" y="115" font-size="10" fill="#78350F">• 10 tests complets</text>
    <text x="692" y="130" font-size="9" fill="#92400E">  (Séquestre, Révoc,</text>
    <text x="692" y="145" font-size="9" fill="#92400E">   Baux, Lock 409)</text>
    <text x="692" y="165" font-size="10" fill="#78350F">• Traces &amp; captures</text>
    <rect x="695" y="360" width="120" height="30" rx="4" fill="#ECFDF5" stroke="#059669"/>
    <text x="755" y="380" text-anchor="middle" font-size="10" font-weight="700" fill="#065F46">STATUT : VERT</text>

    <!-- Étape 6 -->
    <rect x="850" y="0" width="150" height="420" rx="8" fill="#FFFFFF" stroke="#334155" stroke-width="1.5"/>
    <rect x="850" y="0" width="150" height="32" rx="8" fill="#F1F5F9"/>
    <text x="925" y="21" text-anchor="middle" font-size="11" font-weight="700" fill="#0F172A">6. Images Docker</text>
    <text x="862" y="55" font-size="10" fill="#334155">• Syntax Compose</text>
    <text x="862" y="75" font-size="10" fill="#334155">• Image Backend JRE</text>
    <text x="862" y="90" font-size="9" fill="#64748B">  (non-root UID 1000)</text>
    <text x="862" y="115" font-size="10" fill="#334155">• Image Frontend</text>
    <text x="862" y="130" font-size="9" fill="#64748B">  (Nginx UID 101)</text>
    <text x="862" y="155" font-size="10" fill="#334155">• Healthcheck natif</text>
    <rect x="865" y="360" width="120" height="30" rx="4" fill="#ECFDF5" stroke="#059669"/>
    <text x="925" y="380" text-anchor="middle" font-size="10" font-weight="700" fill="#065F46">STATUT : VERT</text>
  </g>

  <!-- Livraison Staging -->
  <g transform="translate(60, 620)">
    <rect width="1000" height="130" rx="8" fill="#FFFFFF" stroke="#059669" stroke-width="2"/>
    <text x="500" y="30" text-anchor="middle" font-size="13" font-weight="700" fill="#065F46">LIVRAISON CONTINUE &amp; VALIDATION MANUELLE STAGING (cd-release.yml)</text>
    
    <rect x="30" y="50" width="280" height="60" rx="6" fill="#F8FAFC" stroke="#CBD5E1"/>
    <text x="45" y="75" font-size="11" font-weight="600" fill="#0F172A">Publication Artefacts</text>
    <text x="45" y="95" font-size="10" fill="#64748B">JAR Boot + SPA Dist + OpenAPI 3.0.1</text>

    <rect x="360" y="50" width="280" height="60" rx="6" fill="#FEF3C7" stroke="#D97706"/>
    <text x="375" y="75" font-size="11" font-weight="600" fill="#92400E">Barrière Manuelle Staging</text>
    <text x="375" y="95" font-size="10" fill="#78350F">Approbation formelle requise avant push</text>

    <rect x="690" y="50" width="280" height="60" rx="6" fill="#ECFDF5" stroke="#059669"/>
    <text x="705" y="75" font-size="11" font-weight="600" fill="#065F46">Déploiement Pré-production</text>
    <text x="705" y="95" font-size="10" fill="#047857">Environnement cloud isolé prêt pour démo</text>
  </g>
</svg>"""

write_svg("use_cases_ecourtier.svg", svg_1)
write_svg("class_diagram_domain.svg", svg_2)
write_svg("sequence_auth_jwt_rotation.svg", svg_3)
write_svg("sequence_escrow_lifecycle.svg", svg_4)
write_svg("sequence_bail_pdf_signature.svg", svg_5)
write_svg("component_architecture.svg", svg_6)
write_svg("deployment_topology.svg", svg_7)
write_svg("state_machine_annonces_transactions.svg", svg_8)
write_svg("cicd_pipeline_architecture.svg", svg_9)

print("SUCCÈS : Les 9 fichiers SVG haute résolution ont été générés dans", SVG_DIR)
