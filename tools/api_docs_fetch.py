#!/usr/bin/env python3
"""Fetch every API's published docs verbatim (llms.txt, llms-full.txt, OpenAPI, the pages llms.txt links to).

Tj, 2026-10-22 (TASKS.md AF): "Thoroughly and fully review the docs. Look for potential llms docs ... Every time I ask
about the apis, it seems like Claude figures out more features". So the docs are fetched on a GitHub runner (free, no
Claude usage, no lossy page summaries), kept on the never-rewritten `api-docs` branch (snap/<day>/ + latest/), and each
run writes CHANGES.md: which pages appeared, changed or went away since the last run, with the changed lines. A session
reads `git fetch origin api-docs` and starts from CHANGES.md instead of re-reading everything.

Usage: python3 -I tools/api_docs_fetch.py fetch <outdir>      (network; run by .github/workflows/api-docs.yml)
       python3 -I tools/api_docs_fetch.py diff <old> <new> <out.md>
       python3 -I tools/api_docs_fetch.py selftest
Keyless: no API key is read or sent. Only public documentation hosts are asked.
"""
import difflib
import hashlib
import os
import re
import sys
import time
import urllib.parse
import urllib.request

# (api, seed urls, follow llms.txt links on these hosts, skip links matching)
SEEDS = [
    ("novig", ["https://docs.novig.com/llms.txt", "https://docs.novig.com/llms-full.txt",
               "https://docs.novig.com/api-reference/spec-files/openapi-v3.json",
               "https://docs.novig.com/api-reference/spec-files/openapi31.json",
               "https://docs.novig.com/api-reference/spec-files/orders-spec.json",
               "https://docs.novig.com/api-reference/spec-files/markets.json"],
     r"/deprecated/"),
    ("kalshi", ["https://docs.kalshi.com/llms.txt", "https://docs.kalshi.com/llms-full.txt",
                "https://docs.kalshi.com/openapi.yaml", "https://docs.kalshi.com/asyncapi.yaml"],
     r"/margin-|/fix/|cfbenchmarks|pyth-value|/python-sdk/|/typescript-sdk/"),
    ("sharpapi", ["https://docs.sharpapi.io/llms.txt", "https://docs.sharpapi.io/llms-full.txt",
                  "https://docs.sharpapi.io/openapi.json"], None),
    ("polymarket", ["https://docs.polymarket.com/llms.txt", "https://docs.polymarket.com/llms-full.txt"], None),
    ("sxbet", ["https://api.docs.sx.bet/llms.txt", "https://docs.sx.bet/llms.txt", "https://docs.sx.bet/llms-full.txt",
               "https://api.docs.sx.bet/"], None),
    ("sportsgameodds", ["https://sportsgameodds.com/docs/llms.txt", "https://sportsgameodds.com/llms.txt",
                        "https://sportsgameodds.com/llms-full.txt", "https://sportsgameodds.com/docs/llms-full.txt",
                        "https://sportsgameodds.com/docs/", "https://api.sportsgameodds.com/v2/openapi.json"], None),
    ("oddspapi", ["https://docs.oddspapi.io/llms.txt", "https://docs.oddspapi.io/llms-full.txt",
                  "https://docs.oddspapi.io/api-reference/openapi.json", "https://oddspapi.io/llms.txt",
                  "https://oddspapi.io/us/docs"], None),
    ("unabated", ["https://docs.unabated.com/llms.txt", "https://docs.unabated.com/llms-full.txt"], None),
    ("propline", ["https://prop-line.com/llms.txt", "https://prop-line.com/llms-full.txt",
                  "https://api.prop-line.com/openapi.json", "https://prop-line.com/docs"], None),
    ("parlayapi", ["https://parlay-api.com/llms.txt", "https://parlay-api.com/llms-full.txt",
                   "https://parlay-api.com/openapi.json", "https://parlay-api.com/docs",
                   "https://parlay-api.com/docs/best-practices"], None),
    ("theoddsapi", ["https://the-odds-api.com/llms.txt", "https://the-odds-api.com/llms-full.txt",
                    "https://the-odds-api.com/liveapi/guides/v4/",
                    "https://the-odds-api.com/sports-odds-data/betting-markets.html",
                    "https://the-odds-api.com/sports-odds-data/bookmaker-apis.html"], None),
    ("boltodds", ["https://boltodds.com/llms.txt", "https://boltodds.com/llms-full.txt", "https://boltodds.com/docs",
                  "https://boltodds.com/pricing"], None),
    ("pinnodds", ["https://pinnodds.com/llms.txt", "https://pinnodds.com/llms-full.txt", "https://pinnodds.com/docs"],
     None),
    ("pinnwire", ["https://pinnwire.com/llms.txt", "https://pinnwire.com/llms-full.txt", "https://pinnwire.com/docs"],
     None),
    ("pinnapi", ["https://pinnapi.com/llms.txt", "https://pinnapi.com/llms-full.txt", "https://pinnapi.com/docs"],
     None),
]
# Public reference data (keyless) saved beside the docs, so a session can check coverage offline: which Kalshi sports series exist, which market
# types Novig lists (and which trade live), Polymarket's sports, SX Bet's leagues. Saved as data/<name>.json.
DATA = [
    ("kalshi-sports-series", "https://api.elections.kalshi.com/trade-api/v2/series?category=Sports"),
    ("novig-market-types", "https://api.novig.com/v3/public/types/markets"),
    ("novig-live-market-types", "https://api.novig.com/v3/public/types/markets/live"),
    ("novig-leagues", "https://api.novig.com/v3/public/catalog/leagues"),
    ("polymarket-sports", "https://gamma-api.polymarket.com/sports"),
    ("sxbet-leagues", "https://api.sx.bet/leagues/active"),
    ("sharpapi-health", "https://api.sharpapi.io/api/v1/health"),
]
MAX_FOLLOW = 700           # pages followed from one API's llms.txt
GAP_S = 0.15               # politeness between requests to one host
UA = "Vigilant-docs-mirror/1.0 (+https://github.com/tjshea90/novig)"
LINK = re.compile(r"\]\((https?://[^)\s]+)\)")


def links_of(text, base_host, skip):
    """The same-host documentation links an llms.txt lists, in order, without repeats."""
    out, seen = [], set()
    for url in LINK.findall(text):
        url = url.split("#")[0]
        p = urllib.parse.urlparse(url)
        if p.netloc != base_host or url in seen:
            continue
        if skip and re.search(skip, p.path):
            continue
        if not (p.path.endswith(".md") or p.path.endswith(".txt") or p.path.endswith(".json") or p.path.endswith(".yaml")):
            continue
        seen.add(url)
        out.append(url)
    return out


def local_path(root, api, url):
    p = urllib.parse.urlparse(url)
    path = p.path.strip("/") or "index"
    if path.endswith("/"):
        path += "index"
    if "." not in os.path.basename(path):
        path += ".html"
    safe = re.sub(r"[^A-Za-z0-9._/-]", "_", f"{p.netloc}/{path}")
    return os.path.join(root, api, safe)


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "*/*"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, b""
    except Exception as e:  # noqa: BLE001 (a docs host down is a manifest line, not a crash)
        return f"ERR {type(e).__name__}", b""


def fetch(root):
    os.makedirs(root, exist_ok=True)
    manifest = []
    for api, seeds, skip in SEEDS:
        queue = list(seeds)
        done = set()
        followed = 0
        while queue:
            url = queue.pop(0)
            if url in done:
                continue
            done.add(url)
            status, body = get(url)
            time.sleep(GAP_S)
            sha = hashlib.sha1(body).hexdigest()[:12] if body else "-"
            manifest.append(f"{api}\t{status}\t{len(body)}\t{sha}\t{url}")
            if status != 200 or not body:
                continue
            dest = local_path(root, api, url)
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            with open(dest, "wb") as f:
                f.write(body)
            if url.endswith("/llms.txt") and followed < MAX_FOLLOW:
                host = urllib.parse.urlparse(url).netloc
                for link in links_of(body.decode("utf-8", "replace"), host, skip):
                    if link not in done and followed < MAX_FOLLOW:
                        queue.append(link)
                        followed += 1
    for name, url in DATA:
        status, body = get(url)
        time.sleep(GAP_S)
        manifest.append(f"data\t{status}\t{len(body)}\t{hashlib.sha1(body).hexdigest()[:12] if body else '-'}\t{url}")
        if status == 200 and body:
            os.makedirs(os.path.join(root, "data"), exist_ok=True)
            with open(os.path.join(root, "data", name + ".json"), "wb") as f:
                f.write(body)
    with open(os.path.join(root, "MANIFEST.tsv"), "w") as f:
        f.write("api\tstatus\tbytes\tsha1\turl\n" + "\n".join(manifest) + "\n")
    ok = sum(1 for m in manifest if m.split("\t")[1] == "200")
    print(f"fetched {ok} of {len(manifest)} urls into {root}")


def walk(root):
    out = {}
    for d, _, files in os.walk(root):
        for name in files:
            full = os.path.join(d, name)
            rel = os.path.relpath(full, root)
            if rel != "MANIFEST.tsv":
                out[rel] = full
    return out


def diff(old, new, out_md, max_lines=400):
    """CHANGES.md: pages added, removed and changed between two snapshots, with the changed lines."""
    a, b = (walk(old) if old and os.path.isdir(old) else {}), walk(new)
    added = sorted(set(b) - set(a))
    removed = sorted(set(a) - set(b))
    changed = []
    for rel in sorted(set(a) & set(b)):
        x, y = open(a[rel], "rb").read(), open(b[rel], "rb").read()
        if x != y:
            changed.append(rel)
    lines = [f"# API docs changes since the last snapshot", "",
             f"added {len(added)}, changed {len(changed)}, removed {len(removed)}", ""]
    for title, items in (("Added", added), ("Removed", removed)):
        if items:
            lines += [f"## {title}"] + [f"- {r}" for r in items] + [""]
    budget = max_lines
    for rel in changed:
        lines.append(f"## Changed: {rel}")
        x = open(a[rel], encoding="utf-8", errors="replace").read().splitlines()
        y = open(b[rel], encoding="utf-8", errors="replace").read().splitlines()
        d = [l for l in difflib.unified_diff(x, y, lineterm="", n=0) if not l.startswith(("---", "+++", "@@"))]
        shown = d[:max(0, min(len(d), budget))]
        budget -= len(shown)
        lines += ["```diff"] + [l[:300] for l in shown] + ["```"]
        if len(shown) < len(d):
            lines.append(f"({len(d) - len(shown)} more changed lines)")
        lines.append("")
    with open(out_md, "w") as f:
        f.write("\n".join(lines) + "\n")
    print(f"diff: added {len(added)} changed {len(changed)} removed {len(removed)}")


def selftest():
    txt = ("- [A](https://docs.x.com/a.md): a\n- [B](https://docs.x.com/deprecated/b.md)\n"
           "- [C](https://other.com/c.md)\n- [D](https://docs.x.com/a.md#frag)\n- [E](https://docs.x.com/page)\n"
           "- [F](https://docs.x.com/spec.json)")
    got = links_of(txt, "docs.x.com", r"/deprecated/")
    assert got == ["https://docs.x.com/a.md", "https://docs.x.com/spec.json"], got
    assert local_path("r", "x", "https://docs.x.com/").endswith("x/docs.x.com/index.html")
    assert local_path("r", "x", "https://docs.x.com/a/b.md").endswith("x/docs.x.com/a/b.md")
    import tempfile
    with tempfile.TemporaryDirectory() as t:
        o, n = os.path.join(t, "o"), os.path.join(t, "n")
        os.makedirs(o), os.makedirs(n)
        open(os.path.join(o, "p.md"), "w").write("one\ntwo\n")
        open(os.path.join(n, "p.md"), "w").write("one\nthree\n")
        open(os.path.join(n, "q.md"), "w").write("new\n")
        diff(o, n, os.path.join(t, "c.md"))
        c = open(os.path.join(t, "c.md")).read()
        assert "added 1, changed 1, removed 0" in c and "+three" in c and "-two" in c, c
    print("selftest ok")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "selftest"
    if cmd == "fetch":
        fetch(sys.argv[2])
    elif cmd == "diff":
        diff(sys.argv[2], sys.argv[3], sys.argv[4])
    else:
        selftest()
