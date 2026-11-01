#!/usr/bin/env python3
"""Audit S1 / F6 / S6 : exécute les 20 phrases S1, les 5 cas F6 et 10 appels S6
contre l'API locale, chronomètre chaque requête, et associe chaque appel aux
lignes de log backend ([MODE LLM IA] / [MODE FALLBACK IA]).
Aucun jeton JWT ni clé API n'est affiché."""
import json, os, time, subprocess, unicodedata, urllib.request, datetime, statistics

API = "http://localhost:8080/api/v1"
BASE = "/home/mahou-dev/Documents/Ecourtier2026/docs/audit"
spec = json.load(open(f"{BASE}/s1_phrases_attendus.json"))

def req(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(API + path, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(r, timeout=30) as resp:
        return resp.status, json.loads(resp.read() or b"null")

def norm(s):
    if s is None:
        return None
    s = str(s).replace("œ", "oe").replace("Œ", "oe")
    s = unicodedata.normalize("NFD", s).encode("ascii", "ignore").decode().lower()
    return s.replace("-", " ").replace("  ", " ").strip()

def logs_since(ts):
    out = subprocess.run(
        ["docker", "logs", "--since", ts, "ecourtier-backend"],
        capture_output=True, text=True
    )
    lines = (out.stdout + out.stderr).splitlines()
    return [l for l in lines if ("MODE LLM" in l or "MODE FALLBACK" in l or "MODE DÉMO" in l or "MODE DEMO" in l)]

def mode_of(lines):
    txt = "\n".join(lines)
    if "MODE FALLBACK IA" in txt or "MODE DÉMO IA" in txt or "MODE DEMO IA" in txt:
        return "FALLBACK_REGLES"
    if "MODE LLM IA" in txt and "Appel à l'API LLM" in txt:
        return "GEMINI_OK"
    return "INCONNU"

ts = int(time.time())
email = f"audit.loc+{ts}@ecourtier.sn"
test_password = os.environ["AUDIT_TEST_PASSWORD"]
_, tok = req("POST", "/auth/inscription", {
    "nom": "Audit", "prenom": "Loc", "email": email,
    "motDePasse": test_password, "telephone": "+221770000099", "role": "LOCATAIRE"
})
token = tok["accessToken"]

catalogue = req("GET", "/annonces?size=100")[1]["content"]

def check_field(k, exp, crit):
    if k == "lieu":
        return norm(exp) in (norm(crit.get("quartier")), norm(crit.get("ville")))
    v = crit.get(k)
    if k in ("budgetMax", "superficieMin"):
        return v is not None and abs(float(v) - float(exp)) < 1
    return v == exp

def annonce_ok(a, att):
    for k, e in att.items():
        if k == "type" and a.get("type") != e:
            return False
        if k == "modeTransaction" and a.get("modeTransaction") != e:
            return False
        if k == "budgetMax" and float(a["prix"]) > e:
            return False
        if k == "lieu" and norm(e) not in (norm(a.get("quartier")), norm(a.get("ville"))):
            return False
        if k == "meuble" and a.get("meuble") != e:
            return False
        if k == "animauxAcceptes" and a.get("animauxAcceptes") != e:
            return False
        if k == "nombrePieces" and (a.get("nombrePieces") or 0) < e:
            return False
        if k == "etage" and a.get("etage") != e:
            return False
        if k == "superficieMin" and (a.get("superficie") or 0) < e:
            return False
    return True

def run(texte):
    _, conv = req("POST", "/ia/conversations", {}, token)
    time.sleep(0.15)
    start = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%fZ")
    t0 = time.perf_counter()
    _, rep = req("POST", f"/ia/conversations/{conv['conversationId']}/messages", {"message": texte}, token)
    dt = time.perf_counter() - t0
    time.sleep(0.35)
    lines = logs_since(start)
    return rep, dt, lines

results = {
    "meta": {"date": datetime.datetime.now(datetime.timezone.utc).isoformat(), "compte": email},
    "s1": [],
    "f6": [],
    "s6": []
}

print("=== EXECUTION S1 (20 phrases) ===", flush=True)
for p in spec["phrases"]:
    rep, dt, lines = run(p["texte"])
    crit = rep.get("criteresExtraits") or {}
    champs = {k: check_field(k, e, crit) for k, e in p["attendu"].items()}
    sugg = rep.get("annoncesSuggeres") or []
    pert = [annonce_ok(a, p["attendu"]) for a in sugg]
    attendues = [a["titre"] for a in catalogue if annonce_ok(a, p["attendu"])]
    mode = mode_of(lines)
    results["s1"].append({
        "id": p["id"],
        "texte": p["texte"],
        "attendu": p["attendu"],
        "extrait": {k: crit.get(k) for k in ("type", "modeTransaction", "budgetMax", "quartier", "ville", "nombrePieces", "etage", "meuble", "animauxAcceptes", "superficieMin")},
        "champs_ok": champs,
        "phrase_ok": all(champs.values()),
        "reponse": rep.get("reponse"),
        "relance": rep.get("questionDeRelance"),
        "suggestions": [{"titre": a["titre"], "type": a.get("type"), "prix": a.get("prix"), "quartier": a.get("quartier"), "ville": a.get("ville"), "etage": a.get("etage")} for a in sugg],
        "suggestions_pertinentes": sum(pert),
        "nb_suggestions": len(sugg),
        "annonces_pertinentes_catalogue": attendues,
        "duree_s": round(dt, 3),
        "mode": mode,
        "logs": lines
    })
    print(f"S1-{p['id']:02d} | {mode:15s} | {dt:5.2f}s | ok={str(all(champs.values())):5s} | sugg={sum(pert)}/{len(sugg)} (cat={len(attendues)}) | {champs}", flush=True)
    time.sleep(0.5)

print("\n=== EXECUTION F6 (5 cas incomplets) ===", flush=True)
for c in spec["f6_cas_incomplets"]:
    rep, dt, lines = run(c["texte"])
    mode = mode_of(lines)
    results["f6"].append({
        "id": c["id"],
        "texte": c["texte"],
        "manque": c["manque"],
        "reponse": rep.get("reponse"),
        "relance": rep.get("questionDeRelance"),
        "criteresExtraits": rep.get("criteresExtraits"),
        "nb_suggestions": len(rep.get("annoncesSuggeres") or []),
        "duree_s": round(dt, 3),
        "mode": mode,
        "logs": lines
    })
    print(f"{c['id']} | {mode:15s} | {dt:5.2f}s | relance={rep.get('questionDeRelance')!r} | rep={rep.get('reponse')[:85]!r}", flush=True)
    time.sleep(0.5)

print("\n=== EXECUTION S6 (10 appels chronométrés dédiés) ===", flush=True)
s6_prompts = [
    "Studio meublé à Nema pour 90 000 FCFA",
    "Appartement F3 à Boucotte budget 120 000 FCFA",
    "Villa à louer aux Almadies max 2 000 000 FCFA",
    "Je cherche une villa à vendre à Ziguinchor pour 50 millions",
    "Appartement 4 pièces meublé à Mermoz 450 000 FCFA",
    "Maison avec jardin à Thiès animaux acceptés 200 000 FCFA",
    "Villa de vacances à acheter à Saly budget 90 000 000 FCFA",
    "Appartement à louer à Mbour budget 110 000 FCFA",
    "Cherche logement à Dakar sans budget précis",
    "F3 non meublé Sacré-Cœur 300 000 FCFA"
]
for idx, pr in enumerate(s6_prompts, 1):
    rep, dt, lines = run(pr)
    mode = mode_of(lines)
    results["s6"].append({
        "id": f"S6-{idx:02d}",
        "texte": pr,
        "duree_s": round(dt, 3),
        "mode": mode,
        "nb_suggestions": len(rep.get("annoncesSuggeres") or []),
        "logs": lines
    })
    print(f"S6-{idx:02d} | {mode:15s} | {dt:5.3f}s | sugg={len(rep.get('annoncesSuggeres') or [])}", flush=True)
    time.sleep(0.5)

json.dump(results, open(f"{BASE}/s1_f6_resultats.json", "w"), ensure_ascii=False, indent=2)
print("\nTerminé ->", f"{BASE}/s1_f6_resultats.json")
