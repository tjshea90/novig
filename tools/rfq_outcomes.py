#!/usr/bin/env python3
"""Did the parlay maker's fair come true? (MK14 / MK5, Tj 2026-11-19: "expected profitability for when I add money").

  python3 -I tools/rfq_outcomes.py run CATALOG.json.gz[,CATALOG2...] JOURNAL.jsonl.gz [...]   grade every priced trade whose legs are game lines, from ESPN's public scoreboards (no key)
  python3 -I tools/rfq_outcomes.py selftest

The engine's `rfq-<day>` journals hold, for every parlay trade on Novig that the maker could price, our fair probability, the price the market traded at, the price our rule would have quoted and
whether it would have won (`EXECUTED` rows joined to the whole `CREATED` row for the legs). The paper counters assume our fair is TRUE; this tool grades the legs (moneyline, spread, total of NFL, NBA,
NHL, NCAAB, NCAAF) from the final scores and answers the three questions that decide whether real money earns anything:
  1. calibration: do the parlays we priced win as often as the fair says (mean fair vs hit rate, by bucket)?
  2. adverse selection: among the trades our price would have WON (it was lower than the market's), is the hit rate still the fair, or the price the market paid? A maker that wins only the trades the market
     over-prices loses; the realized profit of the would-win trades at OUR price is printed (+ means the paper edge was real).
  3. where: by number of legs, same game or not, league.
A leg is graded only when the game is final and the market is a moneyline, spread or total whose outcome the catalog names (team totals and halves are skipped); a parlay with an unresolved leg is skipped
(counted), a push voids the parlay. The catalogs are `tools/rfq_legs.py catalog` snapshots (settled markets leave Novig's catalog, so outcome ids are resolved from a snapshot taken while open).
"""
import collections, datetime, gzip, json, re, sys, urllib.request

SPORT = {"NFL": ("football", "nfl", ""), "NBA": ("basketball", "nba", ""), "NHL": ("hockey", "nhl", ""),
         "NCAAB": ("basketball", "mens-college-basketball", "&groups=50&limit=400"), "NCAAF": ("football", "college-football", "&groups=80&limit=400")}


def norm(s):
    return re.sub(r"[^a-z0-9 ]", "", (s or "").lower().replace("&", "and").replace(".", "")).strip()


def fetch(league, day):
    sp, lg, extra = SPORT[league]
    url = "https://site.api.espn.com/apis/site/v2/sports/%s/%s/scoreboard?dates=%s%s" % (sp, lg, day, extra)
    for attempt in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "research-client"}), timeout=40) as r:
                return json.load(r).get("events", [])
        except Exception:
            pass
    return []


def games_for(league, starts_ms_list, cache):
    out = []
    days = set()
    for ms in starts_ms_list:
        d = datetime.datetime.fromtimestamp(ms / 1000, datetime.UTC)
        days.add(d.strftime("%Y%m%d")); days.add((d - datetime.timedelta(days=1)).strftime("%Y%m%d"))
    for day in sorted(days):
        if (league, day) not in cache:
            evs = []
            for e in fetch(league, day):
                c = (e.get("competitions") or [{}])[0]
                if not c.get("status", {}).get("type", {}).get("completed"):
                    continue
                comp = {x.get("homeAway"): x for x in c.get("competitors", [])}
                if "home" not in comp or "away" not in comp:
                    continue
                try:
                    hs, aws = float(comp["home"]["score"]), float(comp["away"]["score"])
                except (KeyError, ValueError):
                    continue
                t = comp["home"]["team"]; a = comp["away"]["team"]
                evs.append({"startMs": int(datetime.datetime.fromisoformat(e["date"].replace("Z", "+00:00")).timestamp() * 1000), "hs": hs, "as": aws,
                            "home": {norm(t.get(k)) for k in ("displayName", "shortDisplayName", "location", "name")}, "away": {norm(a.get(k)) for k in ("displayName", "shortDisplayName", "location", "name")},
                            "habbr": (t.get("abbreviation") or "").lower(), "aabbr": (a.get("abbreviation") or "").lower()})
            cache[(league, day)] = evs
        out += cache[(league, day)]
    return out


def match_game(desc, starts_ms, games):
    """'Away @ Home' (Novig) -> the ESPN game whose teams carry those names and which started within 6 hours."""
    m = re.match(r"^(.*?) @ (.*)$", desc or "")
    if not m:
        return None
    a, h = norm(m.group(1)), norm(m.group(2))
    best = None
    for g in games:
        if abs(g["startMs"] - starts_ms) > 6 * 3600_000:
            continue
        ok_a = any(n == a or n.startswith(a + " ") for n in g["away"]); ok_h = any(n == h or n.startswith(h + " ") for n in g["home"])
        if ok_a and ok_h:
            best = g; break
    return best


def grade_leg(mt, strike, name, siblings, g):
    """'win' / 'loss' / 'push' for one outcome, or None when it cannot be resolved."""
    if mt == "TOTAL":
        m = re.match(r"^(Over|Under) ([0-9.]+)$", name or "")
        if not m:
            return None
        tot, k = g["hs"] + g["as"], float(m.group(2))
        if tot == k:
            return "push"
        return "win" if (tot > k) == (m.group(1) == "Over") else "loss"
    if mt in ("MONEY", "SPREAD"):
        m = re.match(r"^([A-Za-z0-9&.\-]+)( [+-][0-9.]+)?$", name or "")
        if not m:
            return None
        abbr = m.group(1).lower()
        side = "home" if abbr == g["habbr"] else "away" if abbr == g["aabbr"] else None
        if side is None:
            others = [s for s in siblings if s != name]
            for o in others:
                om = re.match(r"^([A-Za-z0-9&.\-]+)", o)
                oa = om.group(1).lower() if om else ""
                if oa == g["habbr"]:
                    side = "away"
                elif oa == g["aabbr"]:
                    side = "home"
        if side is None:
            return None
        margin = (g["hs"] - g["as"]) if side == "home" else (g["as"] - g["hs"])
        if mt == "SPREAD":
            if not m.group(2):
                return None
            margin += float(m.group(2))
        if margin == 0:
            return "push"
        return "win" if margin > 0 else "loss"
    return None


def load(files):
    for fn in files:
        op = gzip.open if fn.endswith(".gz") else open
        try:
            with op(fn, "rt") as f:
                for line in f:
                    try:
                        r = json.loads(line); r["d"] = json.loads(r.get("data") or "{}"); yield r
                    except ValueError:
                        continue
        except EOFError:
            print("truncated journal, read what is there:", fn, file=sys.stderr)


def build(cats, files):
    cat = {"outcomes": {}, "events": {}}
    for c in cats:
        for k in ("outcomes", "events"):
            for kk, v in c[k].items():
                cat[k].setdefault(kk, v)
    sib = collections.defaultdict(list)
    for oid, o in cat["outcomes"].items():
        sib[(o[5], o[1], o[2])].append(o[6])
    created, execs = {}, []
    for r in load(files):
        if r.get("kind") == "CREATED" and r["d"].get("decision") in ("quote", "decline") and r["d"].get("fair") is not None:
            created[r["rfqId"]] = r["d"]
        elif r.get("kind") == "EXECUTED" and r["d"].get("fair") is not None:
            execs.append(r)
    cache, rows, skipped = {}, [], collections.Counter()
    for r in execs:
        c = created.get(r["rfqId"])
        d = r["d"]
        if not c:
            skipped["no whole CREATED row"] += 1; continue
        legs = []
        for oid in c["outcomeIds"]:
            o = cat["outcomes"].get(oid)
            if not o:
                legs = None; skipped["leg not in the catalog snapshot"] += 1; break
            legs.append((oid, o))
        if legs is None:
            continue
        if any(o[0] not in SPORT or o[1] not in ("MONEY", "SPREAD", "TOTAL") for _, o in legs):
            skipped["a leg is not a graded game line"] += 1; continue
        results, events, legrec = [], set(), []
        for i, (oid, o) in enumerate(legs):
            league, mt, strike, st, starts, eid, name = o
            ev = cat["events"].get(eid) or {}
            g = match_game(ev.get("desc"), ev.get("startsTs") or starts, games_for(league, [ev.get("startsTs") or starts], cache))
            if not g:
                results = None; skipped["game not final / not matched on ESPN"] += 1; break
            res = grade_leg(mt, strike, name, sib[(eid, mt, strike)], g)
            if res is None:
                results = None; skipped["leg name not resolvable"] += 1; break
            results.append(res); events.add(eid)
            lp = (c.get("legs") or [])
            legrec.append((oid, lp[i]["p"] if len(lp) == len(legs) and "p" in lp[i] else None, res, eid))
        if results is None:
            continue
        if "push" in results:
            skipped["a push voids the parlay"] += 1; continue
        win = all(x == "win" for x in results)
        rows.append({"win": win, "fair": d["fair"], "exec": d["price"], "wager": d["wager"], "n": len(legs), "same": len(events) < len(legs), "league": legs[0][1][0] if len({l[1][0] for l in legs}) == 1 else "mixed",
                     "legrec": legrec, "ourPrice": d.get("ourPrice"), "ruleWouldWin": d.get("ruleWouldWin"), "rulePrice": d.get("rulePrice"), "maxWager": c.get("maxWager")})
    return rows, skipped


def report(rows, skipped):
    out = []
    n = len(rows)
    out.append("graded parlays: %d   skipped: %s" % (n, "; ".join("%s %d" % kv for kv in skipped.most_common())))
    if not n:
        return "\n".join(out)
    hit = sum(r["win"] for r in rows) / n; mf = sum(r["fair"] for r in rows) / n; me = sum(r["exec"] for r in rows) / n
    se = (hit * (1 - hit) / n) ** .5
    out.append("calibration: hit rate %.2f%% (SE %.2f) vs mean fair %.2f%% vs mean traded price %.2f%%  (hit - fair = %+.2f points)" % (hit * 100, se * 100, mf * 100, me * 100, (hit - mf) * 100))
    out.append("")
    out.append("| fair bucket | n | hit rate | mean fair | mean traded price |")
    out.append("|--|--:|--:|--:|--:|")
    for lo, hi in ((0, .1), (.1, .2), (.2, .35), (.35, .5), (.5, .7), (.7, 1.01)):
        b = [r for r in rows if lo <= r["fair"] < hi]
        if b:
            out.append("| %d-%d%% | %d | %.1f%% | %.1f%% | %.1f%% |" % (lo * 100, min(hi, 1) * 100, len(b), 100 * sum(r["win"] for r in b) / len(b), 100 * sum(r["fair"] for r in b) / len(b), 100 * sum(r["exec"] for r in b) / len(b)))
    # The parlays share legs (one popular favorite sits in hundreds of them), so their results are not independent: the table above overstates its own precision. Distinct legs are the honest unit.
    seen = {}
    for r in rows:
        for oid, p, res, eid in r["legrec"]:
            if p is not None and oid not in seen:
                seen[oid] = (p, res == "win", eid)
    if seen:
        out.append("")
        out.append("distinct legs graded: %d in %d games; leg-level calibration (our single-leg fair vs how often the leg won):" % (len(seen), len({v[2] for v in seen.values()})))
        out.append("")
        out.append("| leg fair | n | hit rate | mean fair |")
        out.append("|--|--:|--:|--:|")
        for lo, hi in ((0, .3), (.3, .45), (.45, .55), (.55, .7), (.7, .85), (.85, 1.01)):
            b = [v for v in seen.values() if lo <= v[0] < hi]
            if b:
                out.append("| %d-%d%% | %d | %.1f%% | %.1f%% |" % (lo * 100, min(hi, 1) * 100, len(b), 100 * sum(v[1] for v in b) / len(b), 100 * sum(v[0] for v in b) / len(b)))
        hv = sum(v[1] for v in seen.values()) / len(seen); mv = sum(v[0] for v in seen.values()) / len(seen)
        out.append("legs overall: hit %.1f%% vs mean fair %.1f%% (SE %.1f)" % (hv * 100, mv * 100, 100 * (hv * (1 - hv) / len(seen)) ** .5))
    w = [r for r in rows if r.get("ruleWouldWin")]
    out.append("")
    if w:
        k = len(w); h = sum(r["win"] for r in w) / k
        pnl = 0.0; staked = 0.0; coll = 0.0; exp = 0.0
        for r in w:
            pq = r["rulePrice"]; s = min(r["wager"], r.get("maxWager") or r["wager"])
            pnl += s - (s / pq) * (1.0 if r["win"] else 0.0)
            exp += s * (pq - r["fair"]) / pq
            staked += s; coll += s * (1 - pq) / pq
        out.append("trades our rule's price would have WON (%d): hit rate %.1f%% vs their mean fair %.1f%% vs our mean price %.1f%%" % (k, h * 100, 100 * sum(r["fair"] for r in w) / k, 100 * sum(r["rulePrice"] for r in w) / k))
        out.append("  realized profit at OUR price: $%+.2f on $%.0f of user stake ($%.0f of our collateral), expected if the fair were true: $%+.2f  -> realized/expected %s" % (pnl, staked, coll, exp, ("%.0f%%" % (100 * pnl / exp)) if abs(exp) > 1e-9 else "n/a"))
    out.append("")
    out.append("| split | n | hit rate | mean fair | would-win n | realized $ at our price | expected $ |")
    out.append("|--|--:|--:|--:|--:|--:|--:|")
    def split(label, pred):
        b = [r for r in rows if pred(r)]
        if not b:
            return
        ww = [r for r in b if r.get("ruleWouldWin")]
        pnl = sum(min(r["wager"], r.get("maxWager") or r["wager"]) * (1 - (1.0 if r["win"] else 0.0) / r["rulePrice"]) for r in ww)
        ex = sum(min(r["wager"], r.get("maxWager") or r["wager"]) * (r["rulePrice"] - r["fair"]) / r["rulePrice"] for r in ww)
        out.append("| %s | %d | %.1f%% | %.1f%% | %d | %+.2f | %+.2f |" % (label, len(b), 100 * sum(r["win"] for r in b) / len(b), 100 * sum(r["fair"] for r in b) / len(b), len(ww), pnl, ex))
    for nl in (2, 3, 4):
        split("%d legs" % nl, lambda r, nl=nl: r["n"] == nl)
    split("5+ legs", lambda r: r["n"] >= 5)
    split("same game", lambda r: r["same"]); split("different games", lambda r: not r["same"])
    for lg in sorted({r["league"] for r in rows}):
        split(lg, lambda r, lg=lg: r["league"] == lg)
    return "\n".join(out)


def selftest():
    g = {"startMs": 0, "hs": 110.0, "as": 100.0, "home": {"milwaukee bucks", "bucks", "milwaukee"}, "away": {"portland trail blazers", "trail blazers", "portland"}, "habbr": "mil", "aabbr": "por"}
    assert match_game("Portland Trail Blazers @ Milwaukee Bucks", 3600_000, [g]) is g
    assert grade_leg("TOTAL", "209.5", "Over 209.5", [], g) == "push" if False else grade_leg("TOTAL", "209.5", "Over 209.5", [], g) == "win"
    assert grade_leg("TOTAL", "210", "Over 210", [], g) == "push" and grade_leg("TOTAL", "212.5", "Over 212.5", [], g) == "loss"
    assert grade_leg("SPREAD", "10.5", "MIL -10.5", [], g) == "loss" and grade_leg("SPREAD", "10.5", "POR +10.5", [], g) == "win"
    assert grade_leg("MONEY", "0", "MIL", ["MIL", "POR"], g) == "win" and grade_leg("MONEY", "0", "POR", ["MIL", "POR"], g) == "loss"
    assert grade_leg("MONEY", "0", "XXX", ["XXX", "POR"], g) == "win"      # the home team by elimination of the away abbreviation
    rows = [{"legrec": [], "win": True, "fair": .4, "exec": .45, "wager": 10.0, "n": 2, "same": False, "league": "NBA", "ourPrice": .43, "ruleWouldWin": True, "rulePrice": .43, "maxWager": 10.0},
            {"legrec": [], "win": False, "fair": .4, "exec": .45, "wager": 10.0, "n": 2, "same": False, "league": "NBA", "ourPrice": .43, "ruleWouldWin": True, "rulePrice": .43, "maxWager": 10.0}]
    s = report(rows, collections.Counter())
    assert "graded parlays: 2" in s and "realized profit at OUR price: $+" in s or "realized profit at OUR price: $-" in s, s
    print("rfq_outcomes selftest ok"); return 0


if __name__ == "__main__":
    a = sys.argv[1:]
    if a == ["selftest"]:
        sys.exit(selftest())
    if len(a) >= 3 and a[0] == "run":
        cats = [json.load(gzip.open(p, "rt")) for p in a[1].split(",")]
        rows, sk = build(cats, a[2:])
        print(report(rows, sk)); sys.exit(0)
    print(__doc__)
