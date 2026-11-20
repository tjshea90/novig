#!/usr/bin/env python3
"""How to profit as a Novig parlay maker, and are the live settings right? (PS1, Tj 2026-11-20: "Do another study on how to best profit with the parlay system. Verify your current settings").

  python3 -I tools/rfq_calibration_study.py run JOURNAL_DIR OUT_DIR [--sample N]    JOURNAL_DIR: the `rfq-*.jsonl.gz` assets of the `cloud-archive-*` Releases (engine journals); writes OUT_DIR/STUDY.md and executed.csv.gz
  python3 -I tools/rfq_calibration_study.py selftest

What it adds to `rfq_pnl_study.py` (which grades only the parlays that EXECUTED):
 A. LEG calibration on every leg of every priced RFQ (executed or not, so no buyer selection): the share of legs that won against our fair, by probability bucket and market type, counted once per distinct leg
    and weighted by how often buyers put the leg in a parlay, with a bootstrap over GAMES for the spread (legs of one game win and lose together).
 B. PARLAY calibration by shape on all sampled RFQs against the executed ones: is the gap between our fair and what happened the buyers' selection or the fair itself?
 C. Maker P&L at the clearing price by lead time, books behind the fair, hour of day, same game, league and market pair, each with the shared-leg null (one latent outcome per distinct leg).
 D. A replay of the live profile on the executed flow, with the wallet's cash, the concentration guards and every setting varied one at a time (the ablation that says whether a setting earns its place).
Sports days are ET 5 am to 5 am; ESPN's public scoreboard grades (no key). Pregame RFQs only.
"""
import collections, csv, datetime, glob, gzip, json, math, os, random, re, sys, zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np  # noqa: E402
import rfq_pnl_study as P  # noqa: E402  (espn_games, Grader)

ET = -5      # November: Eastern Standard Time, hours from UTC
_RID = re.compile(r'"rfqId":"([^"]+)"')


# ---------------------------------------------------------------------------------------------------------------------------------------------- loading
def _files(directory):
    return sorted(glob.glob(os.path.join(directory, "*.jsonl.gz")))


def load_executed(directory):
    ex = {}
    for fn in _files(directory):
        try:
            for line in gzip.open(fn, "rt"):
                if '"EXECUTED"' not in line:
                    continue
                try:
                    r = json.loads(line); d = json.loads(r["data"])
                except (ValueError, KeyError):
                    continue
                if d.get("price"):
                    ex[r["rfqId"]] = (r["atMs"], float(d["price"]), float(d.get("wager") or 0.0))
        except EOFError:
            pass
    return ex


def load_created(directory, execs, sample):
    """-> {rfqId: (atMs, fair, minWager, outcomes, ps, books)} for every executed RFQ and 1 in `sample` of the others (2 to 4 legs, our fair present), and {outcome: leg dict}."""
    legs, rfqs = {}, {}
    for fn in _files(directory):
        try:
            for line in gzip.open(fn, "rt"):
                if '"CREATED"' not in line:
                    continue
                m = _RID.search(line)
                if not m:
                    continue
                rid = m.group(1)
                if rid not in execs and zlib.crc32(rid.encode()) % sample:
                    continue
                try:
                    r = json.loads(line); d = json.loads(r["data"])
                except (ValueError, KeyError):
                    continue
                ls = [l for l in (d.get("legs") or []) if isinstance(l, dict) and l.get("outcome")]
                if d.get("fair") is None or not (2 <= len(ls) <= 4) or len(ls) != len(d.get("legs") or []):
                    continue
                outs = tuple(l["outcome"] for l in ls)
                for l in ls:
                    t = legs.get(l["outcome"])
                    if t is None:
                        t = legs[l["outcome"]] = {"label": l.get("label") or "", "desc": l.get("desc") or "", "league": l.get("league") or "", "startsMs": l.get("startsMs") or 0, "type": l.get("type") or "",
                                                  "event": l.get("event") or "", "psum": 0.0, "pn": 0, "res": None}
                    t["psum"] += l.get("p") or 0.0; t["pn"] += 1
                rfqs[rid] = (r["atMs"], float(d["fair"]), float(d.get("minWager") or 10.0), outs, tuple(float(l.get("p") or 0.0) for l in ls), tuple(int(l.get("books") or 0) for l in ls))
        except EOFError:
            pass
    return rfqs, legs


def grade_legs(legs, games):
    g = P.Grader(games)
    for o, l in legs.items():
        l["res"] = g.leg({"label": l["label"], "desc": l["desc"], "league": l["league"], "startsMs": l["startsMs"], "type": l["type"]})


def build_rows(rfqs, legs, execs):
    rows = []
    for rid, (at, fair, minw, outs, ps, books) in rfqs.items():
        L = [legs[o] for o in outs]
        if any(l["res"] is None for l in L) or any(l["startsMs"] <= at for l in L):
            continue            # an ungradable leg, or in play
        res = [l["res"] for l in L if l["res"] != "PUSH"]
        if not res:
            continue
        ex = execs.get(rid)
        starts = [l["startsMs"] for l in L]
        ets = (at / 3.6e6 + ET) % 24
        rows.append({"id": rid, "at": at, "fair": fair, "minw": minw, "outs": outs, "ps": ps, "books": books, "n": len(outs), "buyer": int(all(x == "WIN" for x in res)), "price": ex[1] if ex else None, "wager": ex[2] if ex else None,
                     "lead_h": (min(starts) - at) / 3.6e6, "last_h": (max(starts) - at) / 3.6e6, "same": len({l["event"] for l in L}) < len(L), "types": "+".join(sorted(l["type"] for l in L)), "league": L[0]["league"],
                     "hour": int(ets), "day": datetime.datetime.fromtimestamp(at / 1000 + ET * 3600 - 5 * 3600, datetime.timezone.utc).strftime("%m-%d"), "end": max(starts) + 3.5 * 3.6e6})
    return rows


# ---------------------------------------------------------------------------------------------------------------------------------------------- statistics
def null_sim(rows, legs, draws=400, seed=11):
    """-> win matrix (draws x rows) with one latent outcome per distinct leg drawn at that leg's mean fair: what a parlay set earns when OUR fair is exactly right and legs win or lose together."""
    index = {}
    for r in rows:
        for o in r["outs"]:
            index.setdefault(o, len(index))
    pm = np.ones(len(index) + 1)
    for o, i in index.items():
        pm[i] = legs[o]["psum"] / max(1, legs[o]["pn"])
    rng = np.random.default_rng(seed)
    O = rng.random((draws, len(index) + 1)) < pm[None, :]
    O[:, -1] = True                                                    # the padding leg always wins
    L = np.full((len(rows), 4), len(index), dtype=np.int64)
    for k, r in enumerate(rows):
        for j, o in enumerate(r["outs"]):
            L[k, j] = index[o]
    win = np.ones((draws, len(rows)), dtype=bool)
    for j in range(4):
        win &= O[:, L[:, j]]
    return win


def roi_line(name, rows, sel, win=None, idx=None, out=print):
    """One row of a table: n, price, ROI at the clearing price, and with `win`/`idx` (null_sim over `rows`) the spread of that ROI if our fair were right."""
    s = [k for k, r in enumerate(rows) if sel(r)]
    if len(s) < 40:
        out("%-44s n=%d (too few)" % (name, len(s))); return None
    price = np.array([rows[k]["price"] for k in s]); pnl = np.array([rows[k]["price"] - rows[k]["buyer"] for k in s])
    m = pnl.mean(); txt = "%-44s n=%5d price %.2f ROI %+6.1f%%" % (name, len(s), price.mean(), 100 * m)
    if win is not None:
        sim = (price[None, :] - win[:, s]).mean(axis=1)
        txt += "  null %+5.1f%% sd %.1f%%  z %+.1f" % (100 * sim.mean(), 100 * sim.std(), (m - sim.mean()) / max(1e-9, sim.std()))
    out(txt)
    return m


def calibration_buckets(items, edges, rng, boots=300):
    """items: (event, weight, p, win). -> per bucket (n, weight, mean p, win rate, se of win-p over games by bootstrap)."""
    out = []
    events = sorted({e for e, *_ in items})
    eidx = {e: i for i, e in enumerate(events)}
    for lo, hi in zip(edges[:-1], edges[1:]):
        sub = [(eidx[e], w, p, win) for e, w, p, win in items if lo <= p < hi]
        if len(sub) < 20:
            out.append((lo, hi, len(sub), None, None, None, None)); continue
        W = np.zeros(len(events)); S = np.zeros(len(events))
        for e, w, p, win in sub:
            W[e] += w; S[e] += w * (win - p)
        tw = sum(w for _, w, _, _ in sub)
        rate = sum(w * win for _, w, _, win in sub) / tw
        mp = sum(w * p for _, w, p, _ in sub) / tw
        m = rng.multinomial(len(events), np.full(len(events), 1.0 / len(events)), size=boots)
        bw = m @ W; bs = m @ S
        se = float(np.std(bs[bw > 0] / bw[bw > 0]))
        out.append((lo, hi, len(sub), tw, mp, rate, se))
    return out


# ---------------------------------------------------------------------------------------------------------------------------------------------- the live profile
PROFILE = {"maxLegs": 2, "maxLegFair": 0.8, "maxFair": 0.65, "minFair": 0.25, "minLeadMs": 60_000, "maxDaysAhead": 2, "minBooks": 2, "onlyToday": False, "margin": 0.001, "minPoints": 0.0005, "fairErrorRel": 0.001,
           "minReturn": 0.001, "holdFloorDays": 1.0, "maxSameParlay": 1, "maxParlaysPerOutcome": 2, "walletLegShare": 0.25, "sameGamePremium": 0.0, "marginAdd": 0.0}


def up(x):
    return math.ceil(x * 1000.0 - 1e-7) / 1000.0


def our_price(fair, n, lead_ms, c):
    """RfqPricer.quotePrice + the safe-edge floor (LIVE), in Python."""
    days = max(0.0, lead_ms) / 86_400_000.0 + 0.25
    r = c["minReturn"] * max(c["holdFloorDays"], days)
    q = up(max((fair + r) / (1 + r), fair + c["minPoints"] * max(1, n - 1), fair * (1 + c["margin"])))
    return max(q, up(fair * (1 + c["fairErrorRel"] * math.sqrt(n)))) + c["marginAdd"]


def passes(r, c):
    if r["n"] > c["maxLegs"] or max(r["ps"]) > c["maxLegFair"] or r["fair"] > c["maxFair"] or r["fair"] < c["minFair"]:
        return False
    if r["lead_h"] * 3.6e6 < c["minLeadMs"] or r["last_h"] > c["maxDaysAhead"] * 24 or min(r["books"]) < c["minBooks"]:
        return False
    if c["onlyToday"]:
        end = r["at"] - (r["at"] % 86_400_000) + 10 * 3_600_000          # the slate ends at 5 am ET = 10:00 UTC (November)
        if end <= r["at"]:
            end += 86_400_000
        if r["at"] + r["last_h"] * 3.6e6 > end:
            return False
    return True


def replay(rows, c, wallet=145.0):
    """Take every executed pregame parlay the config would quote and win (executed price above our price; a tie is half), in time order, from a wallet whose cash is locked until the game ends.
    -> dict(fills, pnl, pot, collateral peak, ...) in dollars on the executed stakes. wallet=None: no cash limit."""
    ex = sorted((r for r in rows if r["price"] is not None and passes(r, c)), key=lambda r: r["at"])
    cash = wallet if wallet is not None else 1e12
    open_ = []          # (release ms, collateral, pnl, outs)
    legcol = collections.defaultdict(float)
    fills = []; skipped = collections.Counter()
    for r in ex:
        while open_ and open_[0][0] <= r["at"]:
            _, col, pnl, outs = open_.pop(0)
            cash += col + pnl
            for o in outs:
                legcol[o] -= col
        p = our_price(r["fair"], r["n"], r["lead_h"] * 3.6e6, c)
        if r["n"] == 2 and r["same"] and c["sameGamePremium"]:
            p = up(p * (1 + c["sameGamePremium"]))
        if r["price"] < p - 1e-9:
            skipped["market below our price"] += 1; continue
        if abs(r["price"] - p) < 1e-9 and (zlib.crc32(r["id"].encode()) & 1):
            skipped["tie lost"] += 1; continue
        w = r["wager"] or 10.0
        col = w * (1 - p) / p
        if col > cash - 1.0:
            skipped["cash short"] += 1; continue
        if c["maxSameParlay"] and any(set(o[3]) == set(r["outs"]) for o in open_):
            skipped["identical"] += 1; continue
        if c["maxParlaysPerOutcome"] and any(sum(1 for o in open_ if x in o[3]) >= c["maxParlaysPerOutcome"] for x in r["outs"]):
            skipped["outcome in 2"] += 1; continue
        if wallet is not None and any(legcol[x] + col > c["walletLegShare"] * wallet for x in r["outs"]):
            skipped["leg share"] += 1; continue
        pnl = w / p * (p - r["buyer"])
        cash -= col
        for o in r["outs"]:
            legcol[o] += col
        open_.append((r["end"], col, pnl, r["outs"])); open_.sort(key=lambda x: x[0])
        fills.append((r["at"], pnl, col, w / p, r["buyer"], r["day"]))
    pnl = sum(f[1] for f in fills); pot = sum(f[3] for f in fills)
    days = max(1, len({f[5] for f in fills}))
    return {"fills": len(fills), "pnl": pnl, "pot": pot, "roi": pnl / pot if pot else 0.0, "wins": sum(1 - f[4] for f in fills), "per_day": len(fills) / max(1, len({r["day"] for r in ex})), "skipped": dict(skipped), "col": sum(f[2] for f in fills),
            "by_day": {d: sum(f[1] for f in fills if f[5] == d) for d in sorted({f[5] for f in fills})}}


# ---------------------------------------------------------------------------------------------------------------------------------------------- report
def report(rows, legs, out):
    ex = [r for r in rows if r["price"] is not None]
    out("# Parlay maker study (tools/rfq_calibration_study.py)\n")
    out("Graded pregame RFQs: %d (executed: %d, sampled others: %d); distinct legs graded: %d; days %s\n" % (len(rows), len(ex), len(rows) - len(ex), sum(1 for l in legs.values() if l["res"] in ("WIN", "LOSS", "PUSH")), sorted({r["day"] for r in rows})))
    rng = np.random.default_rng(3)

    out("## A. Is OUR leg fair calibrated? (every leg of every priced RFQ, executed or not)\n")
    used = collections.Counter(o for r in rows for o in r["outs"])
    for label, key in (("per distinct leg", lambda o: 1.0), ("weighted by how often buyers put the leg in a parlay", lambda o: float(used[o]))):
        items = [(l["event"], key(o), l["psum"] / l["pn"], 1.0 if l["res"] == "WIN" else 0.0) for o, l in legs.items() if o in used and l["res"] in ("WIN", "LOSS")]
        out("**%s** (%d legs, %d games)\n" % (label, len(items), len({e for e, *_ in items})))
        out("| our fair | legs | mean fair | won | won - fair | se over games |\n|---|---|---|---|---|---|")
        for lo, hi, n, tw, mp, rate, se in calibration_buckets(items, [0, .2, .3, .4, .5, .6, .7, .8, .9, 1.01], rng):
            out("| %.1f-%.1f | %d | %s | %s | %s | %s |" % (lo, hi, n, "%.3f" % mp if mp is not None else "-", "%.3f" % rate if rate is not None else "-", "%+.1f pts" % (100 * (rate - mp)) if rate is not None else "-", "%.1f pts" % (100 * se) if se is not None else "-"))
        out("")
    out("By market type (weighted): ")
    for t in ("MONEY", "SPREAD", "TOTAL"):
        items = [(l["event"], float(used[o]), l["psum"] / l["pn"], 1.0 if l["res"] == "WIN" else 0.0) for o, l in legs.items() if o in used and l["res"] in ("WIN", "LOSS") and l["type"] == t]
        if len(items) >= 50:
            tw = sum(w for _, w, _, _ in items); mp = sum(w * p for _, w, p, _ in items) / tw; rt = sum(w * x for _, w, _, x in items) / tw
            out("%s: %d legs, fair %.3f, won %.3f (%+.1f pts); " % (t, len(items), mp, rt, 100 * (rt - mp)))
    out("")

    out("\n## B. Parlay calibration by shape: all sampled RFQs against the ones that executed (buyer selection)\n")
    out("| shape | all: n | fair | buyer won | executed: n | fair | buyer won |\n|---|---|---|---|---|---|---|")
    shapes = [("2 legs, no leg >= .8", lambda r: r["n"] == 2 and max(r["ps"]) < .8), ("2 legs, both .5-.8", lambda r: r["n"] == 2 and min(r["ps"]) >= .5 and max(r["ps"]) < .8),
              ("2 legs, a leg < .5", lambda r: r["n"] == 2 and min(r["ps"]) < .5 and max(r["ps"]) < .8), ("2 legs, a leg >= .8", lambda r: r["n"] == 2 and max(r["ps"]) >= .8), ("3 legs", lambda r: r["n"] == 3), ("4 legs", lambda r: r["n"] == 4)]
    for name, f in shapes:
        a = [r for r in rows if f(r)]; e = [r for r in a if r["price"] is not None]
        if len(a) >= 30 and len(e) >= 30:
            out("| %s | %d | %.3f | %.3f | %d | %.3f | %.3f |" % (name, len(a), np.mean([r["fair"] for r in a]), np.mean([r["buyer"] for r in a]), len(e), np.mean([r["fair"] for r in e]), np.mean([r["buyer"] for r in e])))

    win = null_sim(ex, legs)
    out("\n## C. Maker P&L per $ of pot at the clearing price, executed parlays (the profile class: 2 legs, no leg >= .8, fair >= .25); `null` = what the segment earns if our fair is exactly right and legs are linked\n")
    out("```")
    cls = lambda r: r["n"] == 2 and max(r["ps"]) < .8 and r["fair"] >= .25
    roi_line("profile class", ex, cls, win, out=out)
    roi_line("all executed", ex, lambda r: True, win, out=out)
    out("-- lead time (hours from the RFQ to the first start)")
    for lo, hi in [(0, .25), (.25, 1), (1, 3), (3, 8), (8, 24), (24, 99)]:
        roi_line("lead [%g,%g) h" % (lo, hi), ex, lambda r, lo=lo, hi=hi: cls(r) and lo <= r["lead_h"] < hi, win, out=out)
    out("-- fewest companies behind a leg's fair")
    for lo, hi in [(0, 2), (2, 3), (3, 5), (5, 8), (8, 99)]:
        roi_line("books [%d,%d)" % (lo, hi), ex, lambda r, lo=lo, hi=hi: cls(r) and lo <= min(r["books"]) < hi, win, out=out)
    out("-- hour of the day (ET) the RFQ arrived")
    for lo, hi in [(0, 6), (6, 12), (12, 18), (18, 24)]:
        roi_line("hour [%d,%d) ET" % (lo, hi), ex, lambda r, lo=lo, hi=hi: cls(r) and lo <= r["hour"] < hi, win, out=out)
    out("-- same game or two games; the market pair; the league of the first leg")
    roi_line("same game", ex, lambda r: cls(r) and r["same"], win, out=out)
    roi_line("two games", ex, lambda r: cls(r) and not r["same"], win, out=out)
    for t in sorted({r["types"] for r in ex if cls(r)}):
        roi_line("types " + t, ex, lambda r, t=t: cls(r) and r["types"] == t, win, out=out)
    for lg in sorted({r["league"] for r in ex if cls(r)}):
        roi_line("league " + lg, ex, lambda r, lg=lg: cls(r) and r["league"] == lg, win, out=out)
    out("-- fair of the parlay")
    for lo, hi in [(.25, .3), (.3, .35), (.35, .45), (.45, .55), (.55, .7)]:
        roi_line("fair [%.2f,%.2f)" % (lo, hi), ex, lambda r, lo=lo, hi=hi: r["n"] == 2 and max(r["ps"]) < .8 and lo <= r["fair"] < hi, win, out=out)
    out("```")

    out("\n## D. Replay of the live profile on the executed flow, wallet $145, every setting varied one at a time\n")
    out("`fills` = parlays the config would have quoted AND won (the market's price at or above our quote), taken in time order from the wallet's free cash; P&L in dollars at the executed stakes (~$10); `pnl/day` over the days with executed flow.\n")
    out("| variant | fills | per day | pnl $ | ROI of pot | peak days | skipped (cash / identical / outcome / leg share) |\n|---|---|---|---|---|---|---|")
    variants = [("PROFILE (live)", {}), ("no wallet limit", {"_wallet": None}), ("wallet $300", {"_wallet": 300.0}),
                ("maxLegs 3", {"maxLegs": 3}), ("maxLegFair .7", {"maxLegFair": 0.7}), ("maxLegFair .9", {"maxLegFair": 0.9}), ("minFair .15", {"minFair": 0.15}), ("minFair .35", {"minFair": 0.35}), ("maxFair .5", {"maxFair": 0.5}), ("maxFair .8", {"maxFair": 0.8}),
                ("onlyToday", {"onlyToday": True}), ("maxDaysAhead 1", {"maxDaysAhead": 1}), ("minLead 10 min", {"minLeadMs": 600_000}), ("minLead 30 min", {"minLeadMs": 1_800_000}), ("minLead 1 h", {"minLeadMs": 3_600_000}),
                ("minBooks 3", {"minBooks": 3}), ("margin +0.5 pt", {"marginAdd": 0.005}), ("margin +1 pt", {"marginAdd": 0.01}), ("margin -0.5 pt", {"marginAdd": -0.005}),
                ("no guards", {"maxSameParlay": 0, "maxParlaysPerOutcome": 0, "walletLegShare": 9.0})]
    results = {}
    for name, ch in variants:
        c = dict(PROFILE); wallet = ch.pop("_wallet", 145.0) if "_wallet" in ch else 145.0
        c.update(ch)
        res = results[name] = replay(rows, c, wallet)
        sk = res["skipped"]
        out("| %s | %d | %.1f | %+.2f | %+.1f%% | %s | %d / %d / %d / %d |" % (name, res["fills"], res["per_day"], res["pnl"], 100 * res["roi"], " ".join("%s:%+.1f" % (d, v) for d, v in res["by_day"].items()), sk.get("cash short", 0), sk.get("identical", 0), sk.get("outcome in 2", 0), sk.get("leg share", 0)))
    return results


def run(journals, out_dir, sample=6):
    execs = load_executed(journals)
    rfqs, legs = load_created(journals, execs, sample)
    ats = [v[0] for v in rfqs.values()]
    first = datetime.datetime.fromtimestamp(min(ats) / 1000, datetime.timezone.utc).date() - datetime.timedelta(days=1)
    last = datetime.datetime.fromtimestamp(max(ats) / 1000, datetime.timezone.utc).date() + datetime.timedelta(days=2)
    games = P.espn_games(first, last)
    grade_legs(legs, games)
    rows = build_rows(rfqs, legs, execs)
    os.makedirs(out_dir, exist_ok=True)
    lines = []
    report(rows, legs, lines.append)
    open(os.path.join(out_dir, "STUDY.md"), "w").write("\n".join(lines) + "\n")
    with gzip.open(os.path.join(out_dir, "executed.csv.gz"), "wt", newline="") as f:
        w = csv.writer(f)
        w.writerow(["rfqId", "atMs", "day", "price", "wager", "fair", "n", "buyer", "lead_h", "same", "types", "league", "books_min", "legs_p"])
        for r in rows:
            if r["price"] is not None:
                w.writerow([r["id"], r["at"], r["day"], r["price"], r["wager"], "%.4f" % r["fair"], r["n"], r["buyer"], "%.2f" % r["lead_h"], int(r["same"]), r["types"], r["league"], min(r["books"]), "|".join("%.3f" % p for p in r["ps"])])
    print("\n".join(lines))
    return 0


def selftest():
    c = dict(PROFILE)
    assert our_price(0.40, 2, 3 * 3.6e6, c) in (0.401, 0.402) and up(0.2) == 0.2 and up(0.2001) == 0.201
    rows = []
    rng = random.Random(5)
    for i in range(200):
        a, b = rng.choice([.55, .6, .65]), rng.choice([.55, .6, .65])
        win = rng.random() < a * b
        rows.append({"id": "r%d" % i, "at": 1_700_000_000_000 + i * 600_000, "fair": a * b, "minw": 10.0, "outs": ("A%d" % (i % 10), "B%d" % (i % 10)), "ps": (a, b), "books": (5, 5), "n": 2, "buyer": int(win), "price": a * b + 0.004, "wager": 10.0,
                     "lead_h": 3.0, "last_h": 3.0, "same": False, "types": "MONEY+MONEY", "league": "NBA", "hour": 19, "day": "11-19", "end": 1_700_000_000_000 + i * 600_000 + 4 * 3.6e6})
    legs = {o: {"psum": 0.6 * 5, "pn": 5, "label": "", "desc": "", "league": "NBA", "startsMs": 0, "type": "MONEY", "event": o[1:], "res": "WIN" if hash(o) % 2 else "LOSS"} for r in rows for o in r["outs"]}
    res = replay(rows, c, 145.0)
    assert res["fills"] > 0 and res["col"] > 0
    nolimit = replay(rows, c, None)
    assert nolimit["fills"] >= res["fills"]
    win = null_sim(rows, legs, draws=50)
    assert win.shape == (50, 200)
    lines = []
    report(rows, legs, lines.append)
    assert any(l.startswith("| PROFILE") for l in lines)
    print("rfq_calibration_study selftest ok"); return 0


if __name__ == "__main__":
    if sys.argv[1:] == ["selftest"]:
        sys.exit(selftest())
    if len(sys.argv) >= 4 and sys.argv[1] == "run":
        samp = int(sys.argv[sys.argv.index("--sample") + 1]) if "--sample" in sys.argv else 6
        sys.exit(run(sys.argv[2], sys.argv[3], samp))
    sys.exit(__doc__)
