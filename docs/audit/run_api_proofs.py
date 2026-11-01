#!/usr/bin/env python3
"""
Exécution de bout en bout des preuves API aujourd'hui (audit qualité E-Courtier).
Ne journalise aucun jeton JWT ni clé secrète.
"""
import io
import json
import struct
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import zlib
from datetime import datetime, timedelta, timezone

BASE = "http://localhost:8080"
TS = int(time.time())


def make_png_bytes(width=4, height=4, rgb=(194, 94, 64)):
    def chunk(tag, data):
        return (
            struct.pack("!I", len(data))
            + tag
            + data
            + struct.pack("!I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    raw = b""
    for _ in range(height):
        raw += b"\x00" + bytes(rgb) * width
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack("!IIBBBBB", width, height, 8, 2, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw))
        + chunk(b"IEND", b"")
    )


def req(method, path, body=None, token=None, raw_body=None, content_type="application/json"):
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None
    if raw_body is not None:
        data = raw_body
        headers["Content-Type"] = content_type
    elif body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    r = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(r, timeout=15) as resp:
            raw = resp.read()
            dt = round(time.perf_counter() - t0, 3)
            ctype = resp.headers.get("Content-Type", "")
            if "application/pdf" in ctype or "image/" in ctype or "octet-stream" in ctype:
                return resp.status, {"binary_len": len(raw), "content_type": ctype, "head_ascii": raw[:32].decode("latin-1", "replace"), "raw_bytes": raw}, dt
            txt = raw.decode("utf-8", "replace")
            try:
                parsed = json.loads(txt) if txt else None
            except Exception:
                parsed = txt
            return resp.status, parsed, dt
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        dt = round(time.perf_counter() - t0, 3)
        try:
            parsed = json.loads(raw) if raw else None
        except Exception:
            parsed = raw
        return e.code, parsed, dt


def multipart_upload(path, parts, token):
    """parts: list of (field_name, filename, mime_type, bytes_content)"""
    boundary = f"----AuditBoundary{int(time.time()*1000)}"
    buf = io.BytesIO()
    for field, filename, mime, content in parts:
        buf.write(f"--{boundary}\r\n".encode())
        buf.write(f'Content-Disposition: form-data; name="{field}"; filename="{filename}"\r\n'.encode())
        buf.write(f"Content-Type: {mime}\r\n\r\n".encode())
        buf.write(content)
        buf.write(b"\r\n")
    buf.write(f"--{boundary}--\r\n".encode())
    return req("POST", path, token=token, raw_body=buf.getvalue(), content_type=f"multipart/form-data; boundary={boundary}")


def sanitize_auth(resp):
    if not isinstance(resp, dict):
        return resp
    out = dict(resp)
    if "accessToken" in out:
        out["accessToken"] = f"<JWT_REDACTED len={len(out['accessToken'])}>"
    if "refreshToken" in out:
        out["refreshToken"] = f"<JWT_REDACTED len={len(out['refreshToken'])}>"
    return out


def extract_pdf_strings(pdf_bytes):
    # OpenPDF writes uncompressed or FlateDecode streams; extract readable text
    text_chunks = []
    # Find streams and decompress if possible
    idx = 0
    while True:
        s = pdf_bytes.find(b"stream", idx)
        if s == -1:
            break
        e = pdf_bytes.find(b"endstream", s)
        if e == -1:
            break
        chunk = pdf_bytes[s + 6 : e].strip(b"\r\n")
        try:
            dec = zlib.decompress(chunk).decode("latin-1", "replace")
            text_chunks.append(dec)
        except Exception:
            text_chunks.append(chunk.decode("latin-1", "replace"))
        idx = e + 9
    joined = "\n".join(text_chunks)
    # Extract parenthesized PDF literals (...)
    import re
    literals = re.findall(r"\(([^()]*)\)", joined)
    return " ".join(literals)


def main():
    proofs = {"executed_at": datetime.now(timezone.utc).isoformat(), "steps": {}}

    # 1. T1 & Auth: Connexion des 4 rôles + inscription nouveau locataire + blocage AGENT public
    tokens = {}
    for label, email, pwd in [
        ("admin", "admin@ecourtier.sn", os.environ["AUDIT_ADMIN_PASSWORD"]),
        ("agent", "agent@ecourtier.sn", os.environ["AUDIT_AGENT_PASSWORD"]),
        ("proprio", "proprio.demo@ecourtier.sn", os.environ["AUDIT_PROPRIO_PASSWORD"]),
        ("locataire_demo", "locataire.demo@ecourtier.sn", os.environ["AUDIT_LOCATAIRE_PASSWORD"]),
    ]:
        st, data, dt = req("POST", "/api/v1/auth/connexion", {"email": email, "motDePasse": pwd})
        tokens[label] = data["accessToken"]
        if label == "proprio":
            proprio_refresh = data["refreshToken"]
            proprio_id = data["utilisateur"]["id"]
        proofs["steps"][f"auth_login_{label}"] = {"status": st, "dt": dt, "response": sanitize_auth(data)}

    # Refresh token test
    st, data, dt = req("POST", "/api/v1/auth/rafraichir", {"refreshToken": proprio_refresh})
    proofs["steps"]["T1_refresh_token"] = {"status": st, "dt": dt, "response": sanitize_auth(data)}

    # Unauthenticated vs Forbidden role checks
    st401, d401, _ = req("GET", "/api/v1/admin/utilisateurs")
    st403_admin, d403_admin, _ = req("GET", "/api/v1/admin/utilisateurs", token=tokens["locataire_demo"])
    st403_create, d403_create, _ = req("POST", "/api/v1/annonces", {"titre": "Test interdit", "description": "Description assez longue pour passer la validation bean.", "type": "STUDIO", "modeTransaction": "LOCATION", "prix": 100000, "superficie": 25, "nombrePieces": 1, "quartier": "Plateau", "ville": "Dakar"}, token=tokens["locataire_demo"])
    proofs["steps"]["T1_rbac_checks"] = {
        "admin_no_token": {"status": st401, "response": d401},
        "admin_as_locataire": {"status": st403_admin, "response": d403_admin},
        "create_annonce_as_locataire": {"status": st403_create, "response": d403_create},
    }

    # F27: Public registration with role AGENT -> 400
    st_ag_reg, d_ag_reg, _ = req("POST", "/api/v1/auth/inscription", {
        "nom": "Sall",
        "prenom": "Ibrahima",
        "email": f"agent.test.{TS}@ecourtier.sn",
        "motDePasse": os.environ["AUDIT_TEST_PASSWORD"],
        "telephone": "+221771112233",
        "role": "AGENT",
    })
    proofs["steps"]["F27_register_agent_blocked"] = {"status": st_ag_reg, "response": d_ag_reg}

    # Register fresh LOCATAIRE/ACHETEUR for clean E2E tests
    buyer_email = f"audit.acheteur.{TS}@ecourtier.sn"
    st_buy, d_buy, _ = req("POST", "/api/v1/auth/inscription", {
        "nom": "Fall",
        "prenom": "Aminata",
        "email": buyer_email,
        "motDePasse": os.environ["AUDIT_TEST_PASSWORD"],
        "telephone": "+221772223344",
        "role": "LOCATAIRE",
    })
    tokens["acheteur"] = d_buy["accessToken"]
    proofs["steps"]["auth_register_acheteur"] = {"status": st_buy, "response": sanitize_auth(d_buy)}

    # 2. F1, F3, F4, F5, F27: Create listings (LOCATION Appartement by PROPRIETAIRE, VENTE Villa by AGENT, STUDIO Location by PROPRIETAIRE)
    st_loc, ann_loc, _ = req("POST", "/api/v1/annonces", {
        "titre": f"Audit Appartement F3 Sacré-Cœur {TS}",
        "description": "Appartement F3 spacieux avec climatisation, parking et visite virtuelle 360 pour audit qualité E-Courtier.",
        "type": "APPARTEMENT",
        "modeTransaction": "LOCATION",
        "prix": 250000,
        "superficie": 85.0,
        "nombrePieces": 3,
        "etage": 2,
        "quartier": "Sacré-Cœur",
        "ville": "Dakar",
        "latitude": 14.7162,
        "longitude": -17.4674,
        "meuble": True,
        "animauxAcceptes": True,
        "lienVisite360": "https://my.matterport.com/show/?m=audit360demo",
        "equipements": ["CLIMATISATION", "PARKING", "WIFI", "GARDIEN"],
        "proximites": ["ECOLE", "MARCHE", "HOPITAL"],
    }, token=tokens["proprio"])
    loc_id = ann_loc["id"]
    proofs["steps"]["F1_F3_F4_F5_create_location_appart"] = {
        "status": st_loc,
        "id": loc_id,
        "statutVerification_initial": ann_loc.get("statutVerification"),
        "equipements": ann_loc.get("equipements"),
        "proximites": ann_loc.get("proximites"),
        "lienVisite360": ann_loc.get("lienVisite360"),
    }

    # Agent creates VENTE Villa + tries editing proprio's annonce
    st_vente, ann_vente, _ = req("POST", "/api/v1/annonces", {
        "titre": f"Audit Villa Vente Almadies {TS}",
        "description": "Villa R+1 en vente directe avec piscine et jardin arboré aux Almadies pour test du parcours achat de bout en bout.",
        "type": "VILLA",
        "modeTransaction": "VENTE",
        "prix": 65000000,
        "superficie": 280.0,
        "nombrePieces": 5,
        "etage": 0,
        "quartier": "Almadies",
        "ville": "Dakar",
        "latitude": 14.7445,
        "longitude": -17.5162,
        "meuble": False,
        "animauxAcceptes": True,
        "equipements": ["PISCINE", "PARKING", "GROUPE_ELECTROGENE"],
        "proximites": ["PLAGE", "ECOLE"],
    }, token=tokens["agent"])
    vente_id = ann_vente["id"]
    st_ag_edit_other, d_ag_edit_other, _ = req("PUT", f"/api/v1/annonces/{loc_id}", {"titre": "Tentative modification par agent tiers"}, token=tokens["agent"])
    st_ag_admin, d_ag_admin, _ = req("GET", "/api/v1/admin/utilisateurs", token=tokens["agent"])
    proofs["steps"]["F27_agent_capabilities"] = {
        "create_own_vente_villa_status": st_vente,
        "vente_id": vente_id,
        "edit_other_owner_listing_status": st_ag_edit_other,
        "edit_other_owner_listing_resp": d_ag_edit_other,
        "access_admin_users_status": st_ag_admin,
    }

    # Also verify STUDIO exists/creatable for F1 completeness
    st_stu, ann_stu, _ = req("POST", "/api/v1/annonces", {
        "titre": f"Audit Studio Plateau {TS}",
        "description": "Studio fonctionnel au Plateau proche des commodités et transports pour vérification F1.",
        "type": "STUDIO",
        "modeTransaction": "LOCATION",
        "prix": 110000,
        "superficie": 32.0,
        "nombrePieces": 1,
        "etage": 1,
        "quartier": "Plateau",
        "ville": "Dakar",
        "latitude": 14.6692,
        "longitude": -17.4381,
        "meuble": False,
        "animauxAcceptes": False,
    }, token=tokens["proprio"])
    proofs["steps"]["F1_create_studio"] = {"status": st_stu, "id": ann_stu.get("id"), "type": ann_stu.get("type")}

    # 3. F2: Upload 3 photos + reorder
    png1 = make_png_bytes(8, 8, (194, 94, 64))
    png2 = make_png_bytes(8, 8, (44, 76, 59))
    png3 = make_png_bytes(8, 8, (217, 155, 38))
    st_ph, photos_resp, _ = multipart_upload(
        f"/api/v1/annonces/{loc_id}/photos",
        [
            ("files", "facade.png", "image/png", png1),
            ("files", "salon.png", "image/png", png2),
            ("files", "chambre.png", "image/png", png3),
        ],
        token=tokens["proprio"],
    )
    photo_ids = [p["id"] for p in photos_resp]
    st_reord, reord_resp, _ = req(
        "PUT",
        f"/api/v1/annonces/{loc_id}/photos/ordre",
        {"photoIdsEnOrdre": list(reversed(photo_ids))},
        token=tokens["proprio"],
    )
    proofs["steps"]["F2_multi_photos_and_reorder"] = {
        "upload_status": st_ph,
        "uploaded_count": len(photos_resp),
        "photos": photos_resp,
        "reorder_status": st_reord,
        "reordered": reord_resp,
    }

    # 4. F14, C2, S2: Verification documents (.pdf rejected vs 3 PNG enum types) + non-admin blocked + admin approval
    fake_pdf = b"%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF\n"
    st_pdf_doc, d_pdf_doc, _ = multipart_upload(
        f"/api/v1/annonces/{loc_id}/documents?typeDocument=TITRE_FONCIER",
        [("file", "titre_foncier.pdf", "application/pdf", fake_pdf)],
        token=tokens["proprio"],
    )
    docs_uploaded = {}
    for dtype in ["TITRE_FONCIER", "BAIL_PRECEDENT", "PIECE_IDENTITE"]:
        st_d, d_d, _ = multipart_upload(
            f"/api/v1/annonces/{loc_id}/documents?typeDocument={dtype}",
            [("file", f"{dtype.lower()}.png", "image/png", png1)],
            token=tokens["proprio"],
        )
        docs_uploaded[dtype] = {"status": st_d, "response": d_d}

    # Non-admin tries to validate badge -> 403
    st_non_adm_verif, d_non_adm_verif, _ = req(
        "PUT",
        f"/api/v1/admin/annonces/{loc_id}/verifier",
        {"statutVerification": "VERIFIEE"},
        token=tokens["proprio"],
    )
    # Admin lists pending & validates badge -> 200 VERIFIEE
    st_pend, d_pend, _ = req("GET", "/api/v1/admin/annonces/en-attente-verification?size=20", token=tokens["admin"])
    st_verif, d_verif, _ = req(
        "PUT",
        f"/api/v1/admin/annonces/{loc_id}/verifier",
        {"statutVerification": "VERIFIEE"},
        token=tokens["admin"],
    )
    # Also verify the VENTE villa so we can test full VENTE workflow on a verified listing
    req("PUT", f"/api/v1/admin/annonces/{vente_id}/verifier", {"statutVerification": "VERIFIEE"}, token=tokens["admin"])

    proofs["steps"]["C2_F14_S2_verification"] = {
        "pdf_upload_rejected": {"status": st_pdf_doc, "response": d_pdf_doc},
        "png_uploads_by_enum": docs_uploaded,
        "proprio_self_verify_blocked": {"status": st_non_adm_verif, "response": d_non_adm_verif},
        "admin_pending_count": d_pend.get("totalElements") if isinstance(d_pend, dict) else None,
        "admin_verify_status": st_verif,
        "statutVerification_after_admin": d_verif.get("statutVerification"),
        "documents_after_admin": d_verif.get("documentsVerification"),
    }

    # 5. F7-F13 API filter checks + F12 MOSQUEE bug proof
    st_f_ok, d_f_ok, _ = req(
        "GET",
        "/api/v1/annonces?" + urllib.parse.urlencode({
            "budgetMin": 100000,
            "budgetMax": 300000,
            "type": "APPARTEMENT",
            "superficieMin": 70,
            "nombrePieces": 3,
            "quartier": "Sacré-Cœur",
            "proximite": "ECOLE",
            "meuble": "true",
        }),
    )
    st_mosq, d_mosq, _ = req("GET", "/api/v1/annonces?proximite=MOSQUEE")
    proofs["steps"]["F7_F13_api_filters"] = {
        "combined_filters_status": st_f_ok,
        "combined_filters_matches": d_f_ok.get("totalElements") if isinstance(d_f_ok, dict) else None,
        "proximite_MOSQUEE_bug": {"status": st_mosq, "response": d_mosq},
    }

    # 6. C3, F15-F19, S3, S4, T5-T8: Complete LOCATION workflow
    # 6a. Initiate transaction BEFORE visit -> 400
    st_tx_pre, d_tx_pre, _ = req("POST", f"/api/v1/annonces/{loc_id}/transactions", {"montantCaution": 1}, token=tokens["acheteur"])

    # 6b. Create RDV for tomorrow 14:00 (for SMS reminder test too)
    tomorrow_14h = (datetime.now() + timedelta(days=1)).replace(hour=14, minute=0, second=0, microsecond=0).strftime("%Y-%m-%dT%H:%M:%S")
    tomorrow_14h30 = (datetime.now() + timedelta(days=1)).replace(hour=14, minute=30, second=0, microsecond=0).strftime("%Y-%m-%dT%H:%M:%S")

    st_rdv1, rdv1, _ = req("POST", f"/api/v1/annonces/{loc_id}/rendez-vous", {"dateHeure": tomorrow_14h, "notes": "Visite audit location"}, token=tokens["acheteur"])
    st_rdv2, rdv2, _ = req("POST", f"/api/v1/annonces/{loc_id}/rendez-vous", {"dateHeure": tomorrow_14h30, "notes": "Visite chevauchante +30min"}, token=tokens["locataire_demo"])

    # Confirm RDV 1 -> 200 CONFIRME; Confirm overlapping RDV 2 -> 409 CONFLICT
    st_conf1, rdv1_conf, _ = req("PUT", f"/api/v1/rendez-vous/{rdv1['id']}/confirmer", token=tokens["proprio"])
    st_conf2, rdv2_conf, _ = req("PUT", f"/api/v1/rendez-vous/{rdv2['id']}/confirmer", token=tokens["proprio"])

    # Trigger manual SMS reminder endpoint & inspect docker logs
    log_since = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    time.sleep(1)
    st_sms, d_sms, _ = req("POST", "/api/v1/rendez-vous/simuler-rappels", token=tokens["admin"])
    time.sleep(0.5)
    logs_sms = subprocess.check_output(["docker", "logs", "--since", log_since, "ecourtier-backend"], stderr=subprocess.STDOUT).decode("utf-8", "replace")
    sms_lines = [ln for ln in logs_sms.splitlines() if "SIMULATION SMS" in ln or "RendezVous" in ln]

    # Check agenda endpoint & mark RDV 1 EFFECTUE
    st_my_rdv, my_rdv, _ = req("GET", "/api/v1/rendez-vous/mes-rendez-vous", token=tokens["proprio"])
    my_rdv_items = my_rdv.get("content", []) if isinstance(my_rdv, dict) else (my_rdv if isinstance(my_rdv, list) else [])
    rdv1_after_sms = next((x for x in my_rdv_items if isinstance(x, dict) and x.get("id") == rdv1["id"]), None)
    st_eff1, rdv1_eff, _ = req("PUT", f"/api/v1/rendez-vous/{rdv1['id']}/marquer-effectue", token=tokens["proprio"])

    proofs["steps"]["F15_F16_F17_S3_rdv_and_sms"] = {
        "tx_before_visit_blocked": {"status": st_tx_pre, "response": d_tx_pre},
        "rdv_created_initial_status": rdv1.get("statut"),
        "rdv_confirm_status": st_conf1,
        "rdv_confirmed_statut": rdv1_conf.get("statut"),
        "overlap_30min_confirm_status": st_conf2,
        "overlap_30min_response": rdv2_conf,
        "simuler_rappels_http": {"status": st_sms, "response": d_sms},
        "rappelEnvoye_after_trigger": rdv1_after_sms.get("rappelEnvoye") if rdv1_after_sms else None,
        "backend_sms_log_lines": sms_lines,
        "rdv_effectue_status": st_eff1,
    }

    # 6c. Initiate LOCATION transaction (sending forged montantCaution=1 to prove server recalculation)
    st_tx_loc, tx_loc, _ = req("POST", f"/api/v1/annonces/{loc_id}/transactions", {"montantCaution": 1}, token=tokens["acheteur"])
    tx_loc_id = tx_loc["id"]

    # Pay escrow via Wave mock
    log_since_tx = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    time.sleep(1)
    st_pay_loc, tx_loc_paid, _ = req("POST", f"/api/v1/transactions/{tx_loc_id}/paiement-caution", token=tokens["acheteur"])

    # Sign 1/2 (Locataire) -> must stay BLOQUE, PDF must be 404
    st_sig1, tx_loc_sig1, _ = req("POST", f"/api/v1/transactions/{tx_loc_id}/bail/signer", token=tokens["acheteur"])
    st_pdf_before, d_pdf_before, _ = req("GET", f"/api/v1/transactions/{tx_loc_id}/bail/pdf", token=tokens["acheteur"])

    # Sign 2/2 (Propriétaire) -> must auto-release to LIBERE + generate PDF + mark listing LOUEE
    st_sig2, tx_loc_sig2, _ = req("POST", f"/api/v1/transactions/{tx_loc_id}/bail/signer", token=tokens["proprio"])
    st_pdf_after, d_pdf_after, _ = req("GET", f"/api/v1/transactions/{tx_loc_id}/bail/pdf", token=tokens["acheteur"])
    pdf_loc_text = extract_pdf_strings(d_pdf_after["raw_bytes"]) if isinstance(d_pdf_after, dict) and "raw_bytes" in d_pdf_after else ""
    if isinstance(d_pdf_after, dict):
        d_pdf_after.pop("raw_bytes", None)

    _, ann_loc_after, _ = req("GET", f"/api/v1/annonces/{loc_id}")
    logs_tx = subprocess.check_output(["docker", "logs", "--since", log_since_tx, "ecourtier-backend"], stderr=subprocess.STDOUT).decode("utf-8", "replace")
    tx_log_lines = [ln for ln in logs_tx.splitlines() if "WAVE" in ln or "LIBÉRATION" in ln or "PDF" in ln]

    proofs["steps"]["C3_F18_F19_T5_T6_T7_T8_S4_location_escrow"] = {
        "initiated": {
            "status": st_tx_loc,
            "id": tx_loc_id,
            "montantCaution": tx_loc.get("montantCaution"),
            "montantPremierLoyer": tx_loc.get("montantPremierLoyer"),
            "fraisDossier": tx_loc.get("fraisDossier"),
            "montantTotalSequestre": tx_loc.get("montantTotalSequestre"),
            "statutSequestre": tx_loc.get("statutSequestre"),
            "statutBail": tx_loc.get("statutBail"),
        },
        "after_wave_payment": {
            "status": st_pay_loc,
            "referenceWave": tx_loc_paid.get("referenceWave"),
            "statutSequestre": tx_loc_paid.get("statutSequestre"),
        },
        "after_1st_signature_locataire": {
            "status": st_sig1,
            "statutBail": tx_loc_sig1.get("statutBail"),
            "statutSequestre_still_blocked": tx_loc_sig1.get("statutSequestre"),
            "pdf_before_2nd_sig_status": st_pdf_before,
        },
        "after_2nd_signature_proprietaire": {
            "status": st_sig2,
            "statutBail": tx_loc_sig2.get("statutBail"),
            "statutSequestre_auto_released": tx_loc_sig2.get("statutSequestre"),
            "montantVerseProprietaire": tx_loc_sig2.get("montantVerseProprietaire"),
            "dateLiberation": tx_loc_sig2.get("dateLiberation"),
            "annonce_statutPublication": ann_loc_after.get("statutPublication"),
            "pdf_download": d_pdf_after,
            "pdf_extracted_text": pdf_loc_text[:600],
        },
        "backend_logs": tx_log_lines,
    }

    # 7. F29: End-to-End VENTE (ACHAT) workflow on `vente_id` (65 000 000 FCFA)
    tomorrow_16h = (datetime.now() + timedelta(days=1)).replace(hour=16, minute=0, second=0, microsecond=0).strftime("%Y-%m-%dT%H:%M:%S")
    _, rdv_v, _ = req("POST", f"/api/v1/annonces/{vente_id}/rendez-vous", {"dateHeure": tomorrow_16h, "notes": "Visite achat villa"}, token=tokens["acheteur"])
    req("PUT", f"/api/v1/rendez-vous/{rdv_v['id']}/confirmer", token=tokens["agent"])
    req("PUT", f"/api/v1/rendez-vous/{rdv_v['id']}/marquer-effectue", token=tokens["agent"])

    st_tx_v, tx_v, _ = req("POST", f"/api/v1/annonces/{vente_id}/transactions", {}, token=tokens["acheteur"])
    tx_v_id = tx_v["id"]
    req("POST", f"/api/v1/transactions/{tx_v_id}/paiement-caution", token=tokens["acheteur"])
    req("POST", f"/api/v1/transactions/{tx_v_id}/bail/signer", token=tokens["acheteur"])
    _, tx_v_done, _ = req("POST", f"/api/v1/transactions/{tx_v_id}/bail/signer", token=tokens["agent"])
    _, d_pdf_v, _ = req("GET", f"/api/v1/transactions/{tx_v_id}/bail/pdf", token=tokens["acheteur"])
    pdf_vente_text = extract_pdf_strings(d_pdf_v["raw_bytes"]) if isinstance(d_pdf_v, dict) and "raw_bytes" in d_pdf_v else ""
    if isinstance(d_pdf_v, dict):
        d_pdf_v.pop("raw_bytes", None)
    _, ann_vente_after, _ = req("GET", f"/api/v1/annonces/{vente_id}")

    proofs["steps"]["F29_vente_achat_end_to_end"] = {
        "tx_init_status": st_tx_v,
        "montantCaution_equals_full_sale_price": tx_v.get("montantCaution"),
        "montantPremierLoyer": tx_v.get("montantPremierLoyer"),
        "fraisDossier": tx_v.get("fraisDossier"),
        "montantTotalSequestre": tx_v.get("montantTotalSequestre"),
        "final_statutSequestre": tx_v_done.get("statutSequestre"),
        "final_statutBail": tx_v_done.get("statutBail"),
        "montantVerseProprietaire": tx_v_done.get("montantVerseProprietaire"),
        "annonce_statutPublication_after_sale": ann_vente_after.get("statutPublication"),
        "pdf_vente_extracted_text": pdf_vente_text[:600],
    }

    # 8. F20, F21, F22, F26: Avis, Réputation, Signalement, Admin Moderation & Stats
    st_av1, d_av1, _ = req("POST", f"/api/v1/annonces/{loc_id}/avis", {"note": 5, "commentaire": "Excellent appartement conforme aux photos et visite fluide."}, token=tokens["acheteur"])
    st_av_dup, d_av_dup, _ = req("POST", f"/api/v1/annonces/{loc_id}/avis", {"note": 4, "commentaire": "Deuxième avis doublon."}, token=tokens["acheteur"])
    st_rep, d_rep, _ = req("GET", f"/api/v1/utilisateurs/{proprio_id}/reputation")

    st_sig_create, d_sig_create, _ = req("POST", f"/api/v1/annonces/{ann_stu['id']}/signalements", {
        "motif": "ARNAQUE_SUSPECTEE",
        "description": "Signalement de test audit qualité pour vérifier la modération admin.",
    }, token=tokens["acheteur"])
    sig_id = d_sig_create["id"]
    st_sig_list, d_sig_list, _ = req("GET", "/api/v1/admin/signalements?statut=EN_ATTENTE", token=tokens["admin"])
    st_sig_proc, d_sig_proc, _ = req("PUT", f"/api/v1/admin/signalements/{sig_id}/traiter", {"statut": "TRAITE", "suspendreAnnonce": True}, token=tokens["admin"])
    _, ann_stu_after_susp, _ = req("GET", f"/api/v1/annonces/{ann_stu['id']}")

    st_stats, d_stats, _ = req("GET", "/api/v1/admin/statistiques?periode=30j", token=tokens["admin"])
    st_users, d_users, _ = req("GET", "/api/v1/admin/utilisateurs", token=tokens["admin"])

    proofs["steps"]["F20_F21_F22_F26_avis_reputation_signalement_admin"] = {
        "avis_create": {"status": st_av1, "response": d_av1},
        "avis_duplicate_blocked": {"status": st_av_dup, "response": d_av_dup},
        "reputation_proprietaire": {"status": st_rep, "response": d_rep},
        "signalement_create": {"status": st_sig_create, "id": sig_id},
        "signalement_admin_list_total": d_sig_list.get("totalElements") if isinstance(d_sig_list, dict) else None,
        "signalement_traiter_suspend": {"status": st_sig_proc, "response": d_sig_proc, "annonce_statut_after": ann_stu_after_susp.get("statutPublication")},
        "admin_statistiques": {"status": st_stats, "keys": list(d_stats.keys()) if isinstance(d_stats, dict) else None, "activiteCommerciale": d_stats.get("activiteCommerciale") if isinstance(d_stats, dict) else None},
        "admin_utilisateurs_count": len(d_users) if isinstance(d_users, list) else None,
    }

    # 9. Beyond-spec: Favoris & Messagerie REST
    st_fav_add, _, _ = req("POST", f"/api/v1/annonces/{loc_id}/favoris", token=tokens["acheteur"])
    st_fav_dup, d_fav_dup, _ = req("POST", f"/api/v1/annonces/{loc_id}/favoris", token=tokens["acheteur"])
    st_fav_cnt, d_fav_cnt, _ = req("GET", "/api/v1/utilisateurs/moi/favoris/count", token=tokens["acheteur"])
    st_fav_lst, d_fav_lst, _ = req("GET", "/api/v1/utilisateurs/moi/favoris", token=tokens["acheteur"])
    st_fav_del, _, _ = req("DELETE", f"/api/v1/annonces/{loc_id}/favoris", token=tokens["acheteur"])

    st_conv, d_conv, _ = req("POST", f"/api/v1/annonces/{loc_id}/conversations", token=tokens["acheteur"])
    conv_id = d_conv["id"]
    st_msg, d_msg, _ = req("POST", f"/api/v1/conversations/{conv_id}/messages", {"contenu": "Bonjour, le bien est-il disponible immédiatement ?"}, token=tokens["acheteur"])
    st_unread, d_unread, _ = req("GET", "/api/v1/conversations/non-lus/count", token=tokens["proprio"])

    proofs["steps"]["hors_cahier_favoris_messagerie"] = {
        "favoris": {
            "add_status": st_fav_add,
            "dup_status": st_fav_dup,
            "dup_resp": d_fav_dup,
            "count": d_fav_cnt,
            "list_total": d_fav_lst.get("totalElements") if isinstance(d_fav_lst, dict) else None,
            "delete_status": st_fav_del,
        },
        "messagerie": {
            "create_conv_status": st_conv,
            "conv_id": conv_id,
            "send_msg_status": st_msg,
            "msg_id": d_msg.get("id") if isinstance(d_msg, dict) else None,
            "proprio_unread_count": d_unread,
        },
    }

    # Leave one fresh verified multi-photo + 360 listing AVAILABLE for UI screenshots & Lighthouse S5!
    st_ui, ann_ui, _ = req("POST", "/api/v1/annonces", {
        "titre": "Appartement Témoin Audit Visite 360 & Galerie",
        "description": "Appartement F4 lumineux à Mermoz avec galerie multi-photos, carte GPS, équipements complets et visite 360 interactive.",
        "type": "APPARTEMENT",
        "modeTransaction": "LOCATION",
        "prix": 350000,
        "superficie": 110.0,
        "nombrePieces": 4,
        "etage": 2,
        "quartier": "Mermoz",
        "ville": "Dakar",
        "latitude": 14.7085,
        "longitude": -17.4725,
        "meuble": True,
        "animauxAcceptes": True,
        "lienVisite360": "https://my.matterport.com/show/?m=audit360ui",
        "equipements": ["CLIMATISATION", "PARKING", "WIFI", "RESERVE_EAU"],
        "proximites": ["ECOLE", "HOPITAL", "TRANSPORT"],
    }, token=tokens["proprio"])
    ui_id = ann_ui["id"]
    multipart_upload(
        f"/api/v1/annonces/{ui_id}/photos",
        [
            ("files", "salon_mermoz.png", "image/png", png1),
            ("files", "cuisine_mermoz.png", "image/png", png2),
            ("files", "terrasse_mermoz.png", "image/png", png3),
        ],
        token=tokens["proprio"],
    )
    multipart_upload(
        f"/api/v1/annonces/{ui_id}/documents?typeDocument=TITRE_FONCIER",
        [("file", "tf_mermoz.png", "image/png", png1)],
        token=tokens["proprio"],
    )
    req("PUT", f"/api/v1/admin/annonces/{ui_id}/verifier", {"statutVerification": "VERIFIEE"}, token=tokens["admin"])
    proofs["steps"]["ui_target_annonce"] = {"id": ui_id, "status": st_ui}

    with open("docs/audit/api_proofs.json", "w", encoding="utf-8") as f:
        json.dump(proofs, f, ensure_ascii=False, indent=2)
    print("OK -> docs/audit/api_proofs.json | ui_target_annonce =", ui_id)


if __name__ == "__main__":
    main()
