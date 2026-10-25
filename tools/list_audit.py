#!/usr/bin/env python3
"""Audit of the engine's published bet list: how many books stand behind each bet, from which feeds, how old, and whether the sides line up (PX9, Tj 2026-10-25: "monitor the scanned bets for many
sources, correct mapping, and fresh odds").

Reads the list the engine publishes (`feed.json.gz` on the `cloud-feed` branch; `git fetch origin cloud-feed` first) and prints:
  - the distribution of companies behind the fair odds (one book a company already), the feeds that sent them, and quote ages by feed;
  - per bet: league, kind, line, selection, companies, feeds, oldest and newest quote, and the problems it finds:
      EXCHANGE-ONLY   every book behind the fair is an exchange (Kalshi, Polymarket, SX Bet): no sportsbook agrees
      THIN            one company behind it although the game is covered by other feeds (the merged game id names more than one feed)
      STALE           a quote older than the plain limit for its game (5 min within 3 h of the start, else 10) from a feed that is not SportsGameOdds
      SIDE            the row's fair probability is not the fair line's probability of the side the row says (orientation mix-up)
      PRICE           a book's price for the bet's side is more than 12 points of chance away from the row's fair (a mapping mistake or a stale quote)
  python3 tools/list_audit.py [--feed path-to-feed.json.gz]
"""
import gzip
import json
import subprocess
import sys
import time

EXCHANGES = {"kalshi", "polymarket", "sxbet", "novig", "prophetx", "matchbook", "smarkets", "betfair_ex", "betfair_ex_eu"}
SLOW = ("sgo",)
# The main desks of engine/BookCompany, to catch one company counted twice in a bet's fair (a key a feed spells differently, a book missing from a group).
GROUPS = {
    "kambi": {"betrivers", "unibet", "livescorebet", "betparx", "parx", "ballybet", "coolbet", "sugarhouse", "rushbet"},
    "entain": {"betmgm", "ladbrokes", "ladbrokes_au", "ladbrokes_be", "coral", "bwin", "supersport", "sportingbet"},
    "flutter": {"fanduel", "paddypower", "betfair_sportsbook", "sportsbet", "skybet"},
    "superbet": {"superbet", "superbet_pl", "superbet_ro", "napoleon"},
    "fortuna": {"fortuna_cz", "fortuna_pl"},
    "digitalgaming": {"betonlineag", "lowvig", "sportsbetting_ag"},
    "supergroup": {"betway", "betway_za"},
    "penn": {"espnbet", "thescorebet"},
    "caesars": {"williamhill_us", "caesars"},
    "hardrock": {"hardrockbet", "hardrock"},
    "bookmaker": {"bookmaker", "bookmaker_eu", "bookmakereu"},
    "marathon": {"marathon", "marathonbet"},
    "betfair": {"betfair_ex", "betfair_ex_eu", "betfairexchange"},
    "1xbet": {"1xbet", "onexbet"},
    "kaizen": {"stoiximan", "betano"},
}
SIDE_INDEX = {"HOME": 0, "AWAY": 1, "OVER": 0, "UNDER": 1, "YES": 0, "NO": 1}


def load(path=None):
    if path:
        return json.load(gzip.open(path))
    raw = subprocess.run(["git", "show", "origin/cloud-feed:feed.json.gz"], capture_output=True).stdout
    return json.loads(gzip.decompress(raw))


def audit(feed, now_ms=None):
    at = feed.get("at") or int(time.time() * 1000)
    now_ms = now_ms or at
    rows = feed.get("rows") or []
    out = {"rows": [], "feeds": {}, "ages": {}, "companies": {}}
    for r in rows:
        f = r.get("fair") or {}
        pb = f.get("pb") or []
        feeds = sorted({(p.get("b") or {}).get("s") or "?" for p in pb})
        books = [(p.get("b") or {}).get("k") for p in pb]
        ages = [(at - (p.get("b") or {}).get("u")) / 1000.0 for p in pb if (p.get("b") or {}).get("u")]
        start = (r.get("ev") or {}).get("ts") or 0
        near = start - now_ms <= 3 * 3600 * 1000
        problems = []
        if pb and all(k in EXCHANGES for k in books):
            problems.append("EXCHANGE-ONLY")
        merged = ((r.get("lk") or {}).get("r") or "")
        if len(pb) <= 1 and merged.count("+") >= 1:
            problems.append("THIN(%d feeds matched the game)" % (merged.count("+") + 1))
        limit = 300.0 if near else 600.0
        for p in pb:
            b = p.get("b") or {}
            if b.get("u") and not str(b.get("s") or "").startswith(SLOW) and (at - b["u"]) / 1000.0 > limit:
                problems.append("STALE(%s %s %ds)" % (b.get("k"), b.get("s"), (at - b["u"]) / 1000))
        for g, members in GROUPS.items():
            hit = [k for k in books if k in members]
            if len(hit) > 1:
                problems.append("DUPLICATE-COMPANY(%s: %s)" % (g, "+".join(hit)))
        idx = SIDE_INDEX.get(r.get("tg"))
        fp = r.get("fp")
        if idx is not None and fp is not None and f.get("p") and idx < len(f["p"]) and not any(abs(f["p"][idx] - fp - c) < 1e-6 for c in CUTS):
            problems.append("SIDE(tg %s: fair side %.3f vs row %.3f)" % (r.get("tg"), f["p"][idx], fp))
        if idx is not None and f.get("p") and idx < len(f["p"]):
            for p in pb:
                fpb = p.get("fp")
                if fpb and idx < len(fpb) and abs(fpb[idx] - f["p"][idx]) > 0.12:
                    problems.append("PRICE(%s %.0f%% vs fair %.0f%%)" % ((p.get("b") or {}).get("k"), fpb[idx] * 100, f["p"][idx] * 100))
        for p in pb:
            b = p.get("b") or {}
            s = b.get("s") or "?"
            out["feeds"][s] = out["feeds"].get(s, 0) + 1
            out.setdefault("by_book", {})[b.get("k")] = out.setdefault("by_book", {}).get(b.get("k"), 0) + 1
            if p.get("sh"):
                out.setdefault("sharp", {})[(b.get("k"), s)] = out.setdefault("sharp", {}).get((b.get("k"), s), 0) + 1
            if b.get("u"):
                out["ages"].setdefault(s, []).append((at - b["u"]) / 1000.0)
        out["companies"][len(pb)] = out["companies"].get(len(pb), 0) + 1
        out["rows"].append({"lg": r.get("lg"), "kind": (r.get("mk") or {}).get("ty"), "line": (r.get("lk") or {}).get("l"), "sel": r.get("sel"), "game": ((r.get("ev") or {}).get("ds") or "")[:34],
                            "n": len(pb), "feeds": feeds, "oldest": max(ages) if ages else None, "newest": min(ages) if ages else None, "near": near, "problems": problems})
    return out


def show(out):
    n = len(out["rows"])
    print("%d listed bets" % n)
    print("companies behind the fair: " + ", ".join("%s: %d" % (k if k < 5 else "5+", sum(v for kk, v in out["companies"].items() if (kk if kk < 5 else 5) == (k if k < 5 else 5))) for k in sorted({min(k, 5) for k in out["companies"]})))
    print("quotes by feed: " + ", ".join("%s %d" % kv for kv in sorted(out["feeds"].items(), key=lambda kv: -kv[1])))
    for s, a in sorted(out["ages"].items()):
        a = sorted(a)
        print("  age %-14s n=%-3d median %4ds  p90 %4ds  max %4ds" % (s, len(a), a[len(a) // 2], a[int(len(a) * 0.9)], a[-1]))
    if out.get("by_book"):
        print("books (quotes behind the listed bets): " + ", ".join("%s %d" % kv for kv in sorted(out["by_book"].items(), key=lambda kv: -kv[1])[:30]))
        print("sharp books by feed: " + (", ".join("%s/%s %d" % (k[0], k[1], v) for k, v in sorted(out["sharp"].items(), key=lambda kv: -kv[1])) or "none"))
        kinds = {}
        for r in out["rows"]:
            kinds.setdefault(r["kind"], []).append(r["n"])
        print("companies per bet by kind: " + ", ".join("%s %s" % (k, "/".join(str(x) for x in sorted(v))) for k, v in sorted(kinds.items())))
    bad = [r for r in out["rows"] if r["problems"]]
    print("bets with problems: %d of %d" % (len(bad), n))
    for r in out["rows"]:
        print("%-5s %-10s %-6s %-34s %-26s n=%d %s oldest %s newest %s %s" % (r["lg"], r["kind"], r["line"], r["game"], (r["sel"] or "")[:26], r["n"], ",".join(r["feeds"]),
                                                                           "-" if r["oldest"] is None else "%ds" % r["oldest"], "-" if r["newest"] is None else "%ds" % r["newest"], " ".join(r["problems"])))


def selftest():
    feed = {"at": 1_800_000_000_000, "rows": [
        {"lg": "NFL", "ev": {"ts": 1_800_000_000_000 + 3_600_000, "ds": "A @ B"}, "mk": {"ty": "TOTAL"}, "lk": {"l": 44.5, "r": "kalshi:K+pl:1"}, "sel": "Over 44.5", "tg": "OVER", "fp": 0.52,
         "fair": {"p": [0.52, 0.48], "pb": [{"b": {"k": "kalshi", "s": "kalshi", "u": 1_800_000_000_000 - 20_000}, "fp": [0.52, 0.48]}]}},
        {"lg": "NBA", "ev": {"ts": 1_800_000_000_000 + 3_600_000, "ds": "C @ D"}, "mk": {"ty": "SPREAD"}, "lk": {"l": 3.5, "r": "pl:2"}, "sel": "C -3.5", "tg": "AWAY", "fp": 0.51,
         "fair": {"p": [0.49, 0.51], "pb": [{"b": {"k": "draftkings", "s": "propline", "u": 1_800_000_000_000 - 400_000}, "fp": [0.49, 0.51]}, {"b": {"k": "fanduel", "s": "parlay", "u": 1_800_000_000_000 - 10_000}, "fp": [0.30, 0.70]}]}},
    ]}
    o = audit(feed)
    assert o["rows"][0]["problems"][:2] == ["EXCHANGE-ONLY", "THIN(2 feeds matched the game)"], o["rows"][0]["problems"]
    p = o["rows"][1]["problems"]
    assert any(x.startswith("STALE(draftkings propline 400s)") for x in p) and any(x.startswith("PRICE(fanduel") for x in p) and not any(x.startswith("SIDE") for x in p), p
    feed["rows"][1]["fp"] = 0.49
    assert any(x.startswith("SIDE") for x in audit(feed)["rows"][1]["problems"])
    print("list_audit selftest ok")
    return 0


if __name__ == "__main__":
    a = sys.argv[1:]
    if a[:1] == ["selftest"]:
        sys.exit(selftest())
    path = a[a.index("--feed") + 1] if "--feed" in a else None
    show(audit(load(path)))
