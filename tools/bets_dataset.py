#!/usr/bin/env python3
"""One row per real bet or bid fill, with an ORIGIN, from every place they are saved (TCL69, Tj 2026-10-16: studies must tell auto bets, bids and his manual bets apart).

  python3 tools/bets_dataset.py table --phone phone/bets/*.jsonl [--engine trades.json]          the ROI/CLV table by origin (what the daily digest prints)
  python3 tools/bets_dataset.py csv   --phone phone/bets/*.jsonl [--engine trades.json] --out bets.csv.gz
  python3 tools/bets_dataset.py selftest

origin = manual | manual-bid | phone-auto | phone-bid | lock | engine-auto | engine-bid | other
  phone files (phone-data branch, `made` bet|bid and `how` manual|auto|lock): bet/manual -> manual, bet/auto -> phone-auto, bid/auto -> phone-bid, bid/manual -> manual-bid, bet/lock -> lock.
  engine ledger (trades.json on cloud-feed; a note starting `bid` is a filled bid): engine-bid, else engine-auto.
RULE (ROUTINE.md): Tj's manual bets are reported beside the others but NEVER counted as evidence for the auto-bet or bid rules (he bets anything by hand); judge rules on phone-auto, phone-bid, engine-auto and engine-bid.
ROI = profit / dollars staked, over settled bets only. Reads files only.
"""
import argparse, csv, glob, gzip, json, math, sys
from collections import defaultdict

ORIGINS_FOR_RULES = ("phone-auto", "phone-bid", "engine-auto", "engine-bid")


def origin_phone(r):
    made, how = r.get("made"), r.get("how")
    return {("bet", "manual"): "manual", ("bet", "auto"): "phone-auto", ("bid", "auto"): "phone-bid", ("bid", "manual"): "manual-bid", ("bet", "lock"): "lock"}.get((made, how), "other")


def rows_phone(paths):
    seen = {}
    for p in paths:
        for line in open(p, encoding="utf-8"):
            line = line.strip()
            if not line.startswith("{"):
                continue
            try:
                r = json.loads(line)
            except ValueError:
                continue
            if "id" in r and r.get("made") in ("bet", "bid"):
                seen[r["id"]] = r
    for r in seen.values():
        res = r.get("result")
        yield {"origin": origin_phone(r), "id": r["id"], "day": r.get("day"), "league": r.get("league"), "kind": r.get("kind"), "selection": r.get("selection"), "event": r.get("event"),
               "lead_min": r.get("leadMin"), "price": r.get("cost"), "stake": r.get("stake"), "ev": r.get("ev"), "clv": r.get("clv"), "result": res if res in ("won", "lost", "push") else "",
               "profit": r.get("profit") if res in ("won", "lost", "push") else "", "source": "phone-data"}


def rows_engine(path):
    d = json.load(open(path))
    for e in d.get("entries", []):
        st = (e.get("status") or "").lower()
        res = {"won": "won", "lost": "lost", "push": "push", "void": "push"}.get(st, "")
        cost = e.get("cost") or 0.0
        yield {"origin": "engine-bid" if str(e.get("note", "")).startswith("bid") else "engine-auto", "id": e.get("id"), "day": "", "league": e.get("league"), "kind": e.get("kind"),
               "selection": e.get("selection"), "event": e.get("event"), "lead_min": None, "price": e.get("avgPrice"), "stake": cost, "ev": e.get("evPercent"), "clv": None,
               "result": res, "profit": e.get("pnl") if res else "", "source": "engine"}


def stat(name, rs):
    gr = [r for r in rs if r["result"] and r.get("stake")]
    n = len(gr)
    if not n:
        return "| %s | %d | 0 | | | | |" % (name, len(rs))
    stake = sum(float(r["stake"]) for r in gr)
    profit = sum(float(r["profit"] or 0) for r in gr)
    rets = [float(r["profit"] or 0) / float(r["stake"]) for r in gr]
    m = sum(rets) / n
    sd = (sum((x - m) ** 2 for x in rets) / max(n - 1, 1)) ** 0.5
    z = m / (sd / math.sqrt(n)) if sd > 0 and n > 1 else 0.0
    cl = [float(r["clv"]) for r in gr if r.get("clv") not in ("", None)]
    return "| %s | %d | %d | $%.2f | %+.2f | %+.1f%% | %+.2f | %s |" % (name, len(rs), n, stake, profit, 100 * profit / stake if stake else 0, z, ("%+.1f%% (%d)" % (100 * sum(cl) / len(cl), len(cl))) if cl else "")


def table(rows):
    L = ["== BETS AND BIDS BY ORIGIN (TCL69: judge the rules on phone-auto, phone-bid, engine-auto, engine-bid; manual is shown, never evidence for them) ==", "",
         "| origin | rows | settled | staked | profit | ROI | z | CLV (n) |", "|:--|--:|--:|--:|--:|--:|--:|:--|"]
    g = defaultdict(list)
    for r in rows:
        g[r["origin"]].append(r)
    for o in sorted(g, key=lambda k: -len(g[k])):
        L.append(stat(o, g[o]))
    rules = [r for r in rows if r["origin"] in ORIGINS_FOR_RULES]
    L.append(stat("ALL FOR RULES (auto + bids)", rules))
    for title, key in (("kind", "kind"), ("league", "league")):
        L += ["", "-- rule evidence by %s (auto + bids only) --" % title, "| %s | rows | settled | staked | profit | ROI | z | CLV (n) |" % title, "|:--|--:|--:|--:|--:|--:|--:|:--|"]
        gg = defaultdict(list)
        for r in rules:
            gg[r.get(key) or "?"].append(r)
        for k in sorted(gg, key=lambda k: -len(gg[k]))[:14]:
            L.append(stat(k, gg[k]))
    return "\n".join(L) + "\n"


def selftest():
    assert origin_phone({"made": "bet", "how": "manual"}) == "manual" and origin_phone({"made": "bid", "how": "auto"}) == "phone-bid" and origin_phone({"made": "x"}) == "other"
    rows = [dict(origin="manual", result="lost", stake=1, profit=-1, clv=None, kind="Player props", league="NFL"), dict(origin="phone-auto", result="won", stake=1, profit=0.9, clv=0.02, kind="Team totals", league="NHL"),
            dict(origin="engine-bid", result="", stake=1, profit="", clv=None, kind="Team totals", league="NHL")]
    t = table(rows)
    assert "| manual | 1 | 1 |" in t and "ALL FOR RULES" in t and "| phone-auto | 1 | 1 | $1.00 | +0.90 | +90.0%" in t, t
    print("bets_dataset selftest ok"); return 0


def main(a):
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd")
    for n in ("table", "csv"):
        p = sub.add_parser(n); p.add_argument("--phone", nargs="*", default=[]); p.add_argument("--engine"); p.add_argument("--out")
    sub.add_parser("selftest")
    x = ap.parse_args(a)
    if x.cmd == "selftest":
        return selftest()
    if x.cmd not in ("table", "csv"):
        ap.print_help(); return 1
    rows = list(rows_phone([f for g in x.phone for f in glob.glob(g)]))
    if x.engine:
        try:
            rows += list(rows_engine(x.engine))
        except Exception as e:  # noqa: BLE001
            print("engine ledger not read:", e, file=sys.stderr)
    if x.cmd == "table":
        print(table(rows)); return 0
    with gzip.open(x.out, "wt", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["origin", "id", "day", "league", "kind", "selection", "event", "lead_min", "price", "stake", "ev", "clv", "result", "profit", "source"])
        w.writeheader(); w.writerows(rows)
    print("wrote", x.out, len(rows), "rows"); return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
