#!/usr/bin/env python3
"""Do makers profit at the prices the Novig parlay market trades? (MK5, Tj 2026-11-20: "make the parlay engine work and be profitable")

  python3 -I tools/rfq_outcomes.py run --catalogs catalog-*.json.gz --journals rfq-*.jsonl.gz [--min-legs 2 --max-legs 3]

Grades the market's own EXECUTED parlays whose legs are all game lines (moneyline, spread, game total) from the games' FINAL scores (ESPN's public scoreboard, no key), using the catalog snapshots
(rfq-catalog Release, rfq_legs.py catalog) to name each leg's outcome id. A trade is: the buyer's stake `wager` at `price` = the probability the parlay wins; if the buyer wins the makers pay wager/price, if
he loses they keep the wager. Reports the makers' realized return on collateral by number of legs, price band and league, and the buyer's realized win rate against the average price (a buyer win rate above the
price = the market's makers lose). Never sends anything to Novig.
"""
import argparse, collections, datetime as dt, gzip, json, re, sys, urllib.request

ESPN = {"NBA": "basketball/nba", "NHL": "hockey/nhl", "NFL": "football/nfl", "NCAAB": "basketball/mens-college-basketball&groups=50", "NCAAF": "football/college-football&groups=80", "MLB": "baseball/mlb"}
GAME_LINES = {"MONEY", "SPREAD", "TOTAL"}


def norm(s):
    return re.sub(r"[^a-z0-9 ]", "", (s or "").lower()).strip()


def sim(a, b):
    a, b = set(norm(a).split()), set(norm(b).split())
    return len(a & b) / max(1, min(len(a), len(b))) if a and b else 0.0


def load_catalogs(files):
    outcomes, events = {}, {}
    for fn in files:
        c = json.load(gzip.open(fn, "rt"))
        for k, v in c["outcomes"].items():
            outcomes[k] = v
        events.update(c["events"])
    return outcomes, events


_scores = {}


def games(league, day):
    key = (league, day)
    if key in _scores:
        return _scores[key]
    path = ESPN.get(league)
    out = []
    if path:
        url = f"https://site.api.espn.com/apis/site/v2/sports/{path.split('&')[0]}/scoreboard?dates={day.strftime('%Y%m%d')}&limit=400" + ("&" + path.split("&", 1)[1] if "&" in path else "")
        try:
            j = json.load(urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "research"}), timeout=60))
            for e in j.get("events", []):
                st = e["status"]["type"]
                if not st.get("completed"):
                    continue
                comp = e["competitions"][0]["competitors"]
                h = [c for c in comp if c["homeAway"] == "home"][0]; a = [c for c in comp if c["homeAway"] == "away"][0]
                out.append({"home": h["team"]["displayName"], "away": a["team"]["displayName"], "hs": int(h["score"]), "as": int(a["score"]), "start": e["date"]})
        except Exception as ex:  # noqa: BLE001
            print("espn", league, day, ex, file=sys.stderr)
    _scores[key] = out
    return out


def find_game(league, desc, starts_ms):
    if " @ " not in desc:
        return None
    away, home = [x.strip() for x in desc.split(" @ ", 1)]
    d0 = (dt.datetime.utcfromtimestamp(starts_ms / 1000) - dt.timedelta(hours=5)).date()
    best = None
    for d in (d0, d0 - dt.timedelta(days=1), d0 + dt.timedelta(days=1)):
        for g in games(league, d):
            s = sim(away, g["away"]) + sim(home, g["home"])
            s2 = sim(away, g["home"]) + sim(home, g["away"])
            if max(s, s2) >= 1.4 and (best is None or max(s, s2) > best[0]):
                best = (max(s, s2), g, s2 > s)
    return best


def grade_leg(o, events):
    """WIN/LOSS/PUSH for the buyer's leg, or None."""
    league, mtype, strike, status, starts, ev, name = o
    if mtype not in GAME_LINES:
        return None
    desc = (events.get(ev) or {}).get("desc") or ""
    f = find_game(league, desc, starts or 0)
    if not f:
        return None
    _, g, swapped = f
    away_n, home_n = (g["home"], g["away"]) if swapped else (g["away"], g["home"])
    away_s, home_s = (g["hs"], g["as"]) if swapped else (g["as"], g["hs"])
    # The feed's own home/away: the ids' names refer to teams, so use the ESPN team names.
    teams = {g["home"]: g["hs"], g["away"]: g["as"]}
    if mtype == "TOTAL":
        m = re.match(r"(over|under)\s+([\d.]+)", (name or "").strip(), re.I)
        if not m:
            return None
        tot = g["hs"] + g["as"]; line = float(m.group(2))
        if tot == line:
            return "PUSH"
        return "WIN" if (tot > line) == (m.group(1).lower() == "over") else "LOSS"
    team = max(teams, key=lambda t: sim(name or "", t))
    if sim(name or "", team) < 0.5:
        return None
    other = [t for t in teams if t != team][0]
    if mtype == "MONEY":
        return "PUSH" if teams[team] == teams[other] else ("WIN" if teams[team] > teams[other] else "LOSS")
    m = re.search(r"([+-]\d+(?:\.\d+)?)\s*$", (name or "").strip())
    if not m:
        return None
    diff = teams[team] + float(m.group(1)) - teams[other]
    return "PUSH" if diff == 0 else ("WIN" if diff > 0 else "LOSS")


def run(args):
    outcomes, events = load_catalogs(args.catalogs)
    trades = {}
    for fn in args.journals:
        for line in gzip.open(fn, "rt"):
            try:
                r = json.loads(line)
            except ValueError:
                continue
            if r.get("kind") != "EXECUTED":
                continue
            try:
                d = json.loads(r["data"])
            except ValueError:
                continue
            ids = d.get("outcomeIds") or []
            if args.min_legs <= len(ids) <= args.max_legs and d.get("wager") and d.get("price"):
                trades[r["rfqId"]] = (ids, float(d["wager"]), float(d["price"]))
    print("executed trades in range:", len(trades))
    stats = collections.defaultdict(lambda: [0, 0.0, 0.0, 0, 0.0])   # n, wager, maker pnl, buyer wins, sum price
    graded = skipped = 0
    for rid, (ids, w, p) in trades.items():
        os_ = [outcomes.get(i) for i in ids]
        if any(o is None for o in os_) or any(o[1] not in GAME_LINES for o in os_):
            skipped += 1; continue
        res = [grade_leg(o, events) for o in os_]
        if any(x is None for x in res) or "PUSH" in res:
            skipped += 1; continue
        buyer_wins = all(x == "WIN" for x in res)
        pnl = -(w / p - w) if buyer_wins else w
        league = os_[0][0]
        pb = "<.2" if p < .2 else ".2-.4" if p < .4 else ".4-.6" if p < .6 else ".6+"
        for key in (("ALL",), ("legs", len(ids)), ("price", pb), ("league", league), ("legs+price", len(ids), pb)):
            s = stats[key]; s[0] += 1; s[1] += w; s[2] += pnl; s[3] += int(buyer_wins); s[4] += p
        graded += 1
    print("graded", graded, "skipped (not game lines / unknown / push)", skipped)
    print(f"{'class':<22}{'n':>6}{'wager$':>10}{'maker P&L $':>13}{'ROI on wager':>14}{'buyer wins':>12}{'avg price':>11}")
    for key in sorted(stats, key=lambda k: (len(k), str(k))):
        n, w, pnl, bw, sp = stats[key]
        print(f"{' '.join(map(str, key)):<22}{n:>6}{w:>10.0f}{pnl:>13.1f}{100 * pnl / max(w, 1e-9):>13.1f}%{100 * bw / n:>11.1f}%{100 * sp / n:>10.1f}%")


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("cmd", choices=["run"])
    ap.add_argument("--catalogs", nargs="+", required=True)
    ap.add_argument("--journals", nargs="+", required=True)
    ap.add_argument("--min-legs", type=int, default=2)
    ap.add_argument("--max-legs", type=int, default=3)
    run(ap.parse_args(argv))


if __name__ == "__main__":
    main(sys.argv[1:])
