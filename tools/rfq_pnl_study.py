#!/usr/bin/env python3
"""What did a maker earn at the CLEARING price? (MK33, Tj 2026-11-20: "a parlay system that runs, has fills and is expected to be profitable").

Every parlay that EXECUTED on Novig (the `EXECUTED` rows of the engine's rfq journals, `cloud-archive-*` Releases) has a price (the probability the market paid) and, once its games are over, an outcome. A maker that
had quoted exactly that price earned  price - 1[the buyer's parlay won]  per $1 of pot (makers pay no fee). Averaged by segment (legs, price band, league, in play, a favourite-heavy parlay...) it says where a maker
makes money without needing our own few fills, and what adverse selection costs: it is the realized value of the auction we are bidding in.

  python3 -I tools/rfq_pnl_study.py run JOURNAL_DIR OUT_DIR       JOURNAL_DIR: the `rfq-*.jsonl.gz` assets; writes OUT_DIR/SUMMARY.md and graded.csv.gz (ESPN's public scoreboard is read, no key)
  python3 -I tools/rfq_pnl_study.py selftest

Grading covers the game lines (moneyline, spread, total) of NBA, NHL, NCAAB, NFL, NCAAF whose legs the journal names (label "Moneyline X" / "Spread X -3.5" / "Total Over 220.5", the game "Away @ Home" or, in older rows, only a
team and a start time). A parlay with a leg that cannot be graded (a prop, an unmatched game) is left out, so the sample is the game-line parlays; the days are SPORTS days (ET 5 am to 5 am). Read the per-day columns:
a few slates decide most of a segment, a segment is only trusted when it holds across days.
"""
import collections, csv, datetime, glob, gzip, json, math, os, pickle, random, re, statistics, sys, time, urllib.request

SPORTS = {"NBA": ("basketball", "nba", ""), "NHL": ("hockey", "nhl", ""), "NCAAB": ("basketball", "mens-college-basketball", "&groups=50&limit=400"),
          "NFL": ("football", "nfl", ""), "NCAAF": ("football", "college-football", "&groups=80&limit=400")}


def norm(s):
    return re.sub(r"[^a-z0-9 ]", "", s.lower().replace("st.", "state").replace("&", "and")).strip()


def toks(s):
    return set(w for w in norm(s).split() if w not in ("the", "of", "university", "univ"))


def load_journals(directory):
    """-> [{rfqId, atMs, price, wager, fair, legs, n}] for every EXECUTED parlay whose RFQ row (with legs) was journaled."""
    created, execs = {}, {}
    for fn in sorted(glob.glob(os.path.join(directory, "*.jsonl.gz"))):
        try:
            for line in gzip.open(fn, "rt"):
                try:
                    r = json.loads(line)
                    d = json.loads(r["data"])
                except (ValueError, KeyError):
                    continue
                k = r.get("kind")
                if k == "CREATED":
                    legs = [l for l in (d.get("legs") or []) if isinstance(l, dict)]
                    if legs and d.get("fair") is not None:
                        created[r["rfqId"]] = (d["fair"], legs)
                elif k == "EXECUTED" and d.get("price"):
                    execs[r["rfqId"]] = (r["atMs"], d)
        except EOFError:
            pass
    rows = []
    for rid, (at, d) in execs.items():
        c = created.get(rid)
        if c:
            rows.append({"rfqId": rid, "atMs": at, "price": d["price"], "wager": d.get("wager") or 0.0, "fair": c[0], "legs": c[1], "n": len(c[1])})
    return rows


def espn_games(first_day, last_day, fetch=None):
    """Completed games from ESPN's public scoreboard for the days [first_day, last_day] (datetime.date), all five leagues."""
    games = []
    fetch = fetch or (lambda url: json.load(urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "research-client"}), timeout=40)))
    day = first_day
    while day <= last_day:
        for lg, (sp, lgs, extra) in SPORTS.items():
            url = "https://site.api.espn.com/apis/site/v2/sports/%s/%s/scoreboard?dates=%s%s" % (sp, lgs, day.strftime("%Y%m%d"), extra)
            try:
                d = fetch(url)
            except Exception as e:                      # one failed day is a smaller sample, not a failed study
                print("espn failed", lg, day, e, file=sys.stderr)
                continue
            for ev in d.get("events", []):
                comp = (ev.get("competitions") or [{}])[0]
                st = (comp.get("status") or ev.get("status") or {}).get("type", {})
                teams = []
                for c in comp.get("competitors", []):
                    t = c.get("team", {})
                    sc = c.get("score")
                    teams.append({"home": c.get("homeAway") == "home", "names": [x for x in (t.get("displayName"), t.get("shortDisplayName"), t.get("name"), t.get("location"), t.get("abbreviation")) if x],
                                  "score": float(sc) if sc not in (None, "") else None})
                if st.get("completed") and len(teams) == 2 and all(t["score"] is not None for t in teams):
                    games.append({"league": lg, "start": ev.get("date"), "teams": teams})
            time.sleep(0.15)
        day += datetime.timedelta(days=1)
    return games


def _name_match(team, name):
    nt = toks(name)
    return max((len(nt & toks(n)) / max(1, min(len(nt), len(toks(n)))) for n in team["names"] if toks(n)), default=0.0)


def _start_ms(g):
    return datetime.datetime.fromisoformat(g["start"].replace("Z", "+00:00")).timestamp() * 1000


class Grader:
    def __init__(self, games):
        self.by_league = collections.defaultdict(list)
        for g in games:
            self.by_league[g["league"]].append(g)
        self.cache = {}

    def _by_desc(self, league, desc, starts):
        m = re.match(r"^(.*?) @ (.*)$", desc or "")
        if not m:
            return None
        away, home = m.group(1), m.group(2)
        best = None
        for g in self.by_league.get(league, []):
            if abs(_start_ms(g) - starts) > 6 * 3.6e6:
                continue
            h = [t for t in g["teams"] if t["home"]]
            a = [t for t in g["teams"] if not t["home"]]
            if not h or not a:
                continue
            s = _name_match(h[0], home) + _name_match(a[0], away)
            if best is None or s > best[0]:
                best = (s, h[0], a[0])
        return best[1:] if best and best[0] >= 1.6 else None

    def _by_team(self, team, starts):
        found = []
        for gl in self.by_league.values():
            for g in gl:
                if abs(_start_ms(g) - starts) > 3 * 3.6e6:
                    continue
                if any(_name_match(t, team) >= 0.99 for t in g["teams"]):
                    found.append(g)
        if len(found) != 1:
            return None
        g = found[0]
        return [t for t in g["teams"] if t["home"]][0], [t for t in g["teams"] if not t["home"]][0]

    def leg(self, l):
        """'WIN' / 'LOSS' / 'PUSH' for a game-line leg the finished games decide, else None."""
        label = l.get("label") or ""
        m0 = re.match(r"^(Moneyline|Spread|Total)\s+(.*)$", label)
        if not m0:
            return None
        kind = {"Moneyline": "MONEY", "Spread": "SPREAD", "Total": "TOTAL"}[m0.group(1)]
        if l.get("type") and l["type"] != kind:
            return None
        key = (kind, l.get("league"), l.get("desc"), m0.group(2) if not l.get("desc") else "", l.get("startsMs"))
        if key not in self.cache:
            if l.get("desc") and l.get("league"):
                self.cache[key] = self._by_desc(l["league"], l["desc"], l.get("startsMs") or 0)
            elif kind != "TOTAL":
                self.cache[key] = self._by_team(re.match(r"^(.*?)\s*([+-][0-9.]+)?$", m0.group(2)).group(1).strip(), l.get("startsMs") or 0)
            else:
                self.cache[key] = None
        g = self.cache[key]
        if not g:
            return None
        h, a = g
        body = m0.group(2)
        if kind == "TOTAL":
            mm = re.match(r"^(Over|Under)\s+([0-9.]+)$", body)
            if not mm:
                return None
            total, line = h["score"] + a["score"], float(mm.group(2))
            return "PUSH" if total == line else ("WIN" if (total > line) == (mm.group(1) == "Over") else "LOSS")
        mm = re.match(r"^(.*?)\s*([+-][0-9.]+)?$", body)
        team = mm.group(1).strip()
        line = float(mm.group(2)) if (kind == "SPREAD" and mm.group(2)) else 0.0
        if _name_match(h, team) >= 0.99:
            mine, opp = h["score"], a["score"]
        elif _name_match(a, team) >= 0.99:
            mine, opp = a["score"], h["score"]
        else:
            return None
        d = mine + line - opp
        return "PUSH" if d == 0 else ("WIN" if d > 0 else "LOSS")


def grade(rows, games):
    gr = Grader(games)
    out = []
    for r in rows:
        res = [gr.leg(l) for l in r["legs"]]
        if any(x is None for x in res):
            continue
        res = [x for x in res if x != "PUSH"]
        if not res:
            continue
        r2 = dict(r)
        r2["buyer"] = all(x == "WIN" for x in res)
        r2["live"] = any((l.get("startsMs") or 0) <= r["atMs"] for l in r["legs"])
        r2["pnl"] = r["price"] - (1.0 if r2["buyer"] else 0.0)
        out.append(r2)
    return out


def sports_day(at_ms):
    return (datetime.datetime.fromtimestamp(at_ms / 1000, datetime.timezone.utc) - datetime.timedelta(hours=10)).strftime("%m-%d")


def segment_line(name, sel):
    n = len(sel)
    if n < 40:
        return None
    mean = sum(r["pnl"] for r in sel) / n
    se = statistics.pstdev([r["pnl"] for r in sel]) / math.sqrt(n)
    stake = sum(r["wager"] / r["price"] * r["pnl"] for r in sel) / max(1e-9, sum(r["wager"] / r["price"] for r in sel))
    byd = collections.defaultdict(list)
    for r in sel:
        byd[sports_day(r["atMs"])].append(r["pnl"])
    days = " ".join("%s:%+.3f(n%d)" % (d, sum(v) / len(v), len(v)) for d, v in sorted(byd.items()))
    return "| %s | %d | %.3f | %.3f | %+.4f | %.4f | %+.4f | %s |" % (name, n, sum(r["buyer"] for r in sel) / n, sum(r["price"] for r in sel) / n, mean, se, stake, days)


def summary(graded):
    L = ["# Maker P&L at the clearing price (tools/rfq_pnl_study.py)", "",
         "Graded executed game-line parlays: **%d** over %d sports days. P&L is per $1 of pot: price - 1[buyer won]; `se` ignores that parlays of one slate share legs (read the day columns); stake-weighted weighs each trade by its pot." % (len(graded), len({sports_day(r["atMs"]) for r in graded})),
         "", "| segment | n | buyer wins | price | maker P&L | se | stake-weighted | per day |", "|---|---|---|---|---|---|---|---|"]
    segs = [("all", lambda r: True), ("pregame", lambda r: not r["live"]), ("in play", lambda r: r["live"])]
    segs += [("legs=%d" % n, lambda r, n=n: r["n"] == n) for n in (2, 3, 4)] + [("legs>=5", lambda r: r["n"] >= 5)]
    segs += [("price %.2f-%.2f" % (lo, hi), lambda r, lo=lo, hi=hi: lo <= r["price"] < hi) for lo, hi in ((0, .1), (.1, .2), (.2, .3), (.3, .5), (.5, .7), (.7, 1.01))]
    segs += [("every leg >= 0.5", lambda r: all(l["p"] >= .5 for l in r["legs"])), ("a leg < 0.4", lambda r: any(l["p"] < .4 for l in r["legs"])),
             ("3+ legs, all >= 0.5", lambda r: r["n"] >= 3 and all(l["p"] >= .5 for l in r["legs"])), ("3+ legs, a leg < 0.5", lambda r: r["n"] >= 3 and any(l["p"] < .5 for l in r["legs"])),
             ("2 legs, price .15-.45", lambda r: r["n"] == 2 and .15 <= r["price"] < .45), ("2 legs, both >= 0.6", lambda r: r["n"] == 2 and all(l["p"] >= .6 for l in r["legs"]))]
    for lg in ("NBA", "NHL", "NFL", "NCAAB", "NCAAF"):
        segs.append(("league " + lg, lambda r, lg=lg: all((l.get("league") or "") in (lg, "") for l in r["legs"]) and any(l.get("league") == lg for l in r["legs"])))
    for name, f in segs:
        line = segment_line(name, [r for r in graded if f(r)])
        if line:
            L.append(line)
    L += ["", "## Our fair against what happened (executed parlays, by our parlay fair)", "", "| our fair | n | realized buyer wins | our fair |", "|---|---|---|---|"]
    for lo, hi in ((0, .1), (.1, .2), (.2, .3), (.3, .45), (.45, .7), (.7, 1.01)):
        s = [r for r in graded if lo <= r["fair"] < hi]
        if len(s) >= 30:
            L.append("| %.2f-%.2f | %d | %.3f | %.3f |" % (lo, hi, len(s), sum(r["buyer"] for r in s) / len(s), sum(r["fair"] for r in s) / len(s)))
    return "\n".join(L) + "\n"


def run(journals, out):
    rows = load_journals(journals)
    if not rows:
        print("no executed parlay with legs in", journals)
        return 1
    days = [datetime.datetime.fromtimestamp(r["atMs"] / 1000, datetime.timezone.utc).date() for r in rows]
    games = espn_games(min(days) - datetime.timedelta(days=1), max(days) + datetime.timedelta(days=1))
    graded = grade(rows, games)
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(out, "SUMMARY.md"), "w") as f:
        f.write("Executed parlays with legs: %d; ESPN completed games: %d\n\n" % (len(rows), len(games)))
        f.write(summary(graded))
    with gzip.open(os.path.join(out, "graded.csv.gz"), "wt", newline="") as f:
        w = csv.writer(f)
        w.writerow(["rfqId", "atMs", "price", "wager", "fair", "n", "live", "buyer", "pnl", "legs"])
        for r in graded:
            w.writerow([r["rfqId"], r["atMs"], r["price"], r["wager"], r["fair"], r["n"], int(r["live"]), int(r["buyer"]), "%.4f" % r["pnl"], "|".join((l.get("label") or "") + "@" + str(round(l["p"], 3)) for l in r["legs"])])
    print(open(os.path.join(out, "SUMMARY.md")).read())
    return 0


def selftest():
    game = lambda lg, away, home, a, h: {"league": lg, "start": "2026-11-19T01:00:00Z", "teams": [{"home": False, "names": [away], "score": float(a)}, {"home": True, "names": [home], "score": float(h)}]}
    games = [game("NBA", "Utah Jazz", "Denver Nuggets", 100, 110), game("NHL", "Boston Bruins", "New York Rangers", 3, 2)]
    start = datetime.datetime(2026, 11, 19, 1, 0, tzinfo=datetime.timezone.utc).timestamp() * 1000
    gr = Grader(games)
    leg = lambda label, desc, lg: {"label": label, "desc": desc, "league": lg, "startsMs": start, "p": .5}
    nba, nhl = "Utah Jazz @ Denver Nuggets", "Boston Bruins @ New York Rangers"
    assert gr.leg(leg("Moneyline Denver Nuggets", nba, "NBA")) == "WIN"
    assert gr.leg(leg("Moneyline Utah Jazz", nba, "NBA")) == "LOSS"
    assert gr.leg(leg("Spread Utah Jazz +10", nba, "NBA")) == "PUSH"
    assert gr.leg(leg("Spread Utah Jazz +10.5", nba, "NBA")) == "WIN"
    assert gr.leg(leg("Total Over 209.5", nba, "NBA")) == "WIN" and gr.leg(leg("Total Under 209.5", nba, "NBA")) == "LOSS"
    assert gr.leg(leg("Total Over 210", nba, "NBA")) == "PUSH"
    assert gr.leg(leg("Moneyline Boston Bruins", nhl, "NHL")) == "WIN"
    assert gr.leg({"label": "Moneyline Utah Jazz", "p": .5, "startsMs": start}) == "LOSS", "an old row with only a team and a time"
    assert gr.leg({"label": "Points Someone Over 20.5", "p": .5, "startsMs": start}) is None
    rows = [{"rfqId": "a", "atMs": start - 3.6e6, "price": 0.4, "wager": 10.0, "fair": 0.38, "n": 2, "legs": [leg("Moneyline Denver Nuggets", nba, "NBA"), leg("Moneyline Boston Bruins", nhl, "NHL")]},
            {"rfqId": "b", "atMs": start - 3.6e6, "price": 0.3, "wager": 10.0, "fair": 0.29, "n": 2, "legs": [leg("Moneyline Denver Nuggets", nba, "NBA"), leg("Moneyline Utah Jazz", nba, "NBA")]}]
    g = grade(rows, games)
    assert [round(r["pnl"], 3) for r in g] == [-0.6, 0.3], g          # a: both legs won: maker pays the pot (0.4 - 1); b: a leg lost: the maker keeps the price
    assert not g[0]["live"] and g[0]["buyer"] and not g[1]["buyer"]
    assert "legs=2" in summary(g * 30)
    print("rfq_pnl_study selftest ok")
    return 0


if __name__ == "__main__":
    a = sys.argv[1:]
    if a[:1] == ["selftest"]:
        sys.exit(selftest())
    if a[:1] == ["run"] and len(a) == 3:
        sys.exit(run(a[1], a[2]))
    print(__doc__)
