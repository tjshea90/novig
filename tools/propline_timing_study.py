#!/usr/bin/env python3
"""Timing study (TCL68; Tj 2026-10-16): what would an early / mid / late TAKER at Novig have earned? Standard library only; no Claude usage.

For finished games in PropLine's resolved-prop archive (Release propline-archive-*: resolved-<sport>-novig / -pinnacle .csv.gz), reads Novig's price history (`/odds/history?bookmakers=novig&changes_only=true`, Pro) and, for each lead
time L before the start (72, 48, 24, 12, 6, 3, 1 hours), takes the last Novig price at or before start-L at the SAME line as Pinnacle's close, scores it against Pinnacle's devigged close (CLV) and the real result (ROI).
The "selected" rows (CLV >= +2% at that moment) are what a rule 'bet when Novig's price beats the sharp close by 2%' would have taken at that lead time. Output: timing.csv.gz (one row per bet and lead) and timing.md.

  python3 tools/propline_timing_study.py run ARCHIVE_DIR OUTDIR [--events N] [--sports a,b]
  python3 tools/propline_timing_study.py selftest
Keys: PROPLINE_API_KEY (the Pro one is found by tools/propline.py's plan check).
"""
import csv, datetime, glob, gzip, json, os, statistics as st, sys, urllib.parse
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import propline as pl

LEADS = [72, 48, 24, 12, 6, 3, 1]
KINDS = ("totals", "spreads", "h2h")


def prob(a):
    a = float(a)
    return 100 / (a + 100) if a > 0 else -a / (-a + 100)


def dec(a):
    a = float(a)
    return 1 + a / 100 if a > 0 else 1 + 100 / -a


def ts(x):
    try:
        return datetime.datetime.fromisoformat(x.replace("Z", "+00:00")).timestamp()
    except Exception:
        return None


def pt(x):
    try:
        return round(float(x), 3)
    except Exception:
        return None


def price_at(snaps, t, point):
    """Last snapshot at or before t with the given point (None = no point)."""
    best = None
    for s in snaps:
        r = ts(s.get("recorded_at") or "")
        if r is None or r > t:
            continue
        if pt(s.get("point")) != point:
            continue
        best = s
    return best


def load(archive_dir, sports):
    pin, bets = {}, {}
    for f in glob.glob(os.path.join(archive_dir, "resolved-*-pinnacle*.csv.gz")):
        for r in csv.DictReader(gzip.open(f, "rt")):
            if r["player_name"].startswith("(Water") or r["team"] or not r["closing_price"]:
                continue
            pin[(r["event_id"], r["market"], r["player_name"], pt(r["closing_point"] or r["line"]), r["outcome_name"])] = prob(r["closing_price"])
    for f in glob.glob(os.path.join(archive_dir, "resolved-*-novig*.csv.gz")):
        sport = os.path.basename(f).split("-")[1]
        if sports and sport not in sports:
            continue
        for r in csv.DictReader(gzip.open(f, "rt")):
            if r["player_name"].startswith("(Water") or r["team"] or r["resolution"] not in ("won", "lost") or r["outcome_name"] not in ("Over", "Under"):
                continue
            bets.setdefault((sport, r["event_id"]), []).append(r)
    return pin, bets


def evaluate(rows, history, pin, start):
    """rows: archive rows of ONE game; history: {outcome_id: snapshots}. -> list of dicts, one per (bet, lead)."""
    out = []
    for r in rows:
        snaps = history.get(str(r["outcome_id"]))
        if not snaps:
            continue
        m, pl_, side = r["market"], r["player_name"], r["outcome_name"]
        point = pt(r["closing_point"] or r["line"])
        a = pin.get((r["event_id"], m, pl_, point, side))
        b = pin.get((r["event_id"], m, pl_, point, "Under" if side == "Over" else "Over"))
        if a is None or b is None:
            continue
        fair = a / (a + b)
        for L in LEADS:
            s = price_at(snaps, start - L * 3600, point)
            if not s or s.get("price") is None:
                continue
            d = dec(s["price"])
            out.append({"lead": L, "sport": r["sport_key"], "market": m, "kind": "prop" if pl_ else m, "side": side, "clv": fair * d - 1, "d": d, "win": r["resolution"] == "won", "prob": 1 / d})
    return out


def summarize(rows):
    lines = ["# Timing study: Novig's price at each lead time vs Pinnacle's close, with real results", "",
             "| kind | side | lead (h before start) | all bets: n, mean CLV | selected (CLV>=+2%): n, ROI, t |", "|:--|:--|--:|--|--|"]
    for kind in ("prop", "totals", "spreads", "h2h"):
        for side in ("Over", "Under"):
            for L in LEADS:
                sub = [x for x in rows if x["kind"] == kind and x["side"] == side and x["lead"] == L]
                if len(sub) < 40:
                    continue
                sel = [x for x in sub if x["clv"] >= 0.02]
                cell = "n<30"
                if len(sel) >= 30:
                    v = [(x["d"] - 1) if x["win"] else -1 for x in sel]
                    roi = st.mean(v)
                    sd = st.pstdev(v) or 1
                    cell = "%d, %+.1f%%, t=%.1f" % (len(sel), 100 * roi, roi / (sd / len(v) ** 0.5))
                lines.append("| %s | %s | %d | %d, %+.1f%% | %s |" % (kind, side, L, len(sub), 100 * st.mean(x["clv"] for x in sub), cell))
    return "\n".join(lines) + "\n"


def cmd_run(archive_dir, outdir, n_events, sports):
    keys = pl.keys_from_env()
    key, plan = pl.need_paid(keys) if keys else (None, None)
    if key is None:
        print("no paid key")
        return 0
    os.makedirs(outdir, exist_ok=True)
    pin, bets = load(archive_dir, sports)
    events = sorted(bets.items(), key=lambda kv: -max(ts(r["commence_time"]) or 0 for r in kv[1]))[:n_events]
    allrows = []
    for i, ((sport, eid), rows) in enumerate(events):
        start = ts(rows[0]["commence_time"])
        mk = sorted({r["market"] for r in rows if r["market"] in KINDS or r["player_name"]})[:40]
        st_, h, data = pl.request("/v1/sports/%s/events/%s/odds/history" % (sport, eid), key, {"markets": ",".join(mk), "bookmakers": "novig", "changes_only": "true", "relative_from": "-80h", "relative_to": "0"})
        body = pl.j(data) if st_ == 200 else None
        hist = {}
        for bk in (body or {}).get("bookmakers", []):
            for m in bk.get("markets", []):
                for o in m.get("outcomes", []):
                    hist[str(o.get("outcome_id"))] = o.get("snapshots") or []
        allrows += evaluate(rows, hist, pin, start)
        if i % 25 == 0:
            print("%d/%d events, %d bet-leads" % (i, len(events), len(allrows)), flush=True)
    with gzip.open(os.path.join(outdir, "timing.csv.gz"), "wt", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["lead", "sport", "market", "kind", "side", "clv", "d", "win", "prob"])
        w.writeheader()
        w.writerows(allrows)
    open(os.path.join(outdir, "timing.md"), "w").write(summarize(allrows))
    print(summarize(allrows))
    return 0


def selftest():
    snaps = [{"recorded_at": "2026-10-10T00:00:00Z", "price": 150, "point": 5.5}, {"recorded_at": "2026-10-10T10:00:00Z", "price": 120, "point": 5.5}, {"recorded_at": "2026-10-10T11:00:00Z", "price": 200, "point": 6.5}]
    t = ts("2026-10-10T12:00:00Z")
    assert price_at(snaps, t, 5.5)["price"] == 120 and price_at(snaps, t - 11 * 3600, 5.5)["price"] == 150 and price_at(snaps, t - 13 * 3600, 5.5) is None
    row = {"event_id": "1", "market": "totals", "player_name": "", "closing_point": "5.5", "line": "5.5", "outcome_name": "Over", "outcome_id": "9", "resolution": "won", "sport_key": "x"}
    pin = {("1", "totals", "", 5.5, "Over"): 0.5, ("1", "totals", "", 5.5, "Under"): 0.5}
    got = evaluate([row], {"9": snaps}, pin, t)
    byl = {g["lead"]: g for g in got}
    assert abs(byl[1]["clv"] - (0.5 * dec(120) - 1)) < 1e-9 and byl[12]["d"] == dec(150) and 24 not in byl
    assert "Timing study" in summarize(got)
    print("timing study selftest ok")
    return 0


if __name__ == "__main__":
    a = sys.argv[1:]
    if not a or a[0] == "selftest":
        sys.exit(selftest())
    if a[0] == "run":
        n = int(a[a.index("--events") + 1]) if "--events" in a else 400
        sp = set(a[a.index("--sports") + 1].split(",")) if "--sports" in a else None
        sys.exit(cmd_run(a[1], a[2], n, sp))
    print(__doc__)
