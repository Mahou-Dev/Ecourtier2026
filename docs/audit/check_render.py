#!/usr/bin/env python3
import json
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

URLS = [
    ("backend_health", "https://ecourtier-backend.onrender.com/api/v1/health"),
    ("backend_swagger_ui", "https://ecourtier-backend.onrender.com/swagger-ui/index.html"),
    ("backend_api_docs", "https://ecourtier-backend.onrender.com/v3/api-docs"),
    ("backend_annonces_db", "https://ecourtier-backend.onrender.com/api/v1/annonces?size=5"),
    ("frontend_root", "https://ecourtier-frontend.onrender.com/"),
    ("frontend_spa_route", "https://ecourtier-frontend.onrender.com/recherche"),
]


def probe(url, timeout=90):
    t0 = time.perf_counter()
    req = urllib.request.Request(url, headers={"User-Agent": "ECourtier-Audit/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read()
            dt = round(time.perf_counter() - t0, 3)
            headers = {k.lower(): v for k, v in resp.headers.items()}
            txt = raw.decode("utf-8", "replace")
            return {"status": resp.status, "dt_sec": dt, "headers": headers, "body": txt}
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        dt = round(time.perf_counter() - t0, 3)
        headers = {k.lower(): v for k, v in e.headers.items()} if e.headers else {}
        return {"status": e.code, "dt_sec": dt, "headers": headers, "body": raw[:1000]}
    except Exception as e:
        dt = round(time.perf_counter() - t0, 3)
        return {"status": None, "dt_sec": dt, "error": repr(e)}


def main():
    out = {"executed_at": datetime.now(timezone.utc).isoformat(), "probes": {}}
    for name, url in URLS:
        res = probe(url, timeout=90)
        summary = {
            "url": url,
            "status": res.get("status"),
            "dt_sec": res.get("dt_sec"),
            "x_render_routing": res.get("headers", {}).get("x-render-routing"),
            "content_type": res.get("headers", {}).get("content-type"),
        }
        if "error" in res:
            summary["error"] = res["error"]
        body = res.get("body", "")
        if name == "backend_api_docs" and res.get("status") == 200:
            try:
                spec = json.loads(body)
                paths = list(spec.get("paths", {}).keys())
                ops = sum(len(m) for m in spec.get("paths", {}).values())
                schemas = spec.get("components", {}).get("schemas", {})
                tx_props = list(schemas.get("TransactionResponseDTO", {}).get("properties", {}).keys())
                summary["openapi_paths_count"] = len(paths)
                summary["openapi_ops_count"] = ops
                summary["has_favoris"] = any("favoris" in p for p in paths)
                summary["has_messagerie"] = any("conversations" in p and "/ia/" not in p for p in paths)
                summary["has_admin_statistiques"] = "/api/v1/admin/statistiques" in paths
                summary["has_montantPremierLoyer"] = "montantPremierLoyer" in tx_props
                summary["transaction_dto_props"] = tx_props
            except Exception as ex:
                summary["parse_error"] = repr(ex)
        elif name == "backend_annonces_db" and res.get("status") == 200:
            try:
                d = json.loads(body)
                summary["totalElements"] = d.get("totalElements")
                summary["sample_titles"] = [x.get("titre") for x in d.get("content", [])[:3]]
                if d.get("content"):
                    summary["first_id"] = d["content"][0].get("id")
                    summary["has_estFavori_field"] = "estFavori" in d["content"][0]
            except Exception:
                summary["body_preview"] = body[:500]
        else:
            summary["body_preview"] = body[:500]
        out["probes"][name] = summary
        print(name, "->", summary.get("status"), f"({summary.get('dt_sec')}s)", summary.get("x_render_routing", ""))

    with open("docs/audit/render_l6.json", "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print("Saved docs/audit/render_l6.json")


if __name__ == "__main__":
    main()
