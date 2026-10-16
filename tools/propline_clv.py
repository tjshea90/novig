#!/usr/bin/env python3
"""Closing-line value of every bet the engine saw, from PropLine's resolved-prop archive (TCL56; Tj 2026-10-16: "save stats on whether I beat the CLV (use this for clv on all the bets it offers)").

  python3 tools/propline_clv.py run --lines lines-*.csv.gz --archive resolved-*.csv.gz --out clv.csv.gz [--summary clv.md]
  python3 tools/propline_clv.py selftest

--lines: the engine's `lines-<day>.csv.gz` (Release `engine-dataset`, columns in its DATA_DICTIONARY.md): one row per bet its scan saw, shown or not, at Novig's price then (`american`, `cost`).
--archive: PropLine's `resolved-<sport>-<book>-<since>_<until>.csv.gz` (Release `propline-archive-*`, written by .github/workflows/propline.yml): per outcome, the book's price, its closing price/point and opening
price/point, the grade (`resolution`, `actual_value`). Needs the Novig and Pinnacle exports.
For each bet found among PropLine's events (sport, start time within 2 h, both teams) it adds: `pl_close_fair` = the bet's side's probability in Pinnacle's CLOSING two-way (or three-way) market, devigged
(multiplicative; PropLine's own /clv/grade uses the sharpest book too), `clv_pinn = pl_close_fair / cost - 1` (the honest number: positive = the price beat the sharp close), `pl_novig_close_cost` (what Novig itself
closed at) and `beat_novig_close`, the closing point when the number moved (`pl_point_moved`; a bet at 46.5 into a 47.5 close beat the line, which `clv_pinn` cannot say), and PropLine's grade. Unmatched bets say why.
Never a key; reads files only.
"""
import argparse, csv, gzip, io, json, re, sys, unicodedata, datetime
from collections import defaultdict

LEAGUE_SPORT = {"NBA": "basketball_nba", "NHL": "hockey_nhl", "NFL": "football_nfl", "NCAAF": "football_ncaaf", "MLB": "baseball_mlb", "WNBA": "basketball_wnba", "NCAAB": "basketball_ncaab",
                "ATP": "tennis", "WTA": "tennis", "UFC": "mma_ufc"}
STAT_KEYS = {  # Novig market title (lower, letters only) -> PropLine market key
    "points": "player_points", "rebounds": "player_rebounds", "assists": "player_assists", "threepointersmade": "player_threes", "threes": "player_threes",
    "pointsreboundsassists": "player_points_rebounds_assists", "pointsassists": "player_points_assists", "pointsrebounds": "player_points_rebounds", "reboundsassists": "player_rebounds_assists",
    "steals": "player_steals", "blocks": "player_blocks", "shotsongoal": "player_shots_on_goal", "saves": "goalie_saves", "goals": "player_goals",
    "hits": "batter_hits", "totalbases": "batter_total_bases", "pitcherstrikeouts": "pitcher_strikeouts", "strikeouts": "pitcher_strikeouts", "rbis": "batter_rbis", "homeruns": "batter_home_runs",
    "passingyards": "player_pass_yds", "rushingyards": "player_rush_yds", "receivingyards": "player_reception_yds", "receptions": "player_receptions", "passingtouchdowns": "player_pass_tds",
}


def norm(s):
    s = unicodedata.normalize("NFKD", s or "").encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9 ]+", " ", s).split()


def same_team(a, b):
    """Two spellings of one team/player: one's tokens are within the other's, or their last words agree."""
    x, y = norm(a), norm(b)
    if not x or not y:
        return False
    return set(x) <= set(y) or set(y) <= set(x) or x[-1] == y[-1]


def implied(american):
    a = float(american)
    return 100.0 / (a + 100.0) if a > 0 else -a / (-a + 100.0)


def devig(prices):
    """{outcome: american} -> {outcome: fair probability} (multiplicative); None unless two or more sides."""
    if len(prices) < 2:
        return None
    raw = {k: implied(v) for k, v in prices.items()}
    tot = sum(raw.values())
    return {k: v / tot for k, v in raw.items()} if tot > 0 else None


def num(x):
    try:
        return float(x)
    except (TypeError, ValueError):
        return None


def read_csv(path):
    with gzip.open(path, "rt", newline="") as f:
        yield from csv.DictReader(f)


class Archive:
    def __init__(self):
        self.events = {}                      # event_id -> dict(sport, start (datetime), home, away)
        self.pinn = defaultdict(dict)         # group key -> {outcome_name: closing price}
        self.novig = {}                       # group key + (outcome_name,) -> row summary
        self.by_sport = defaultdict(list)     # sport -> [event_id]

    @staticmethod
    def gkey(r):
        cp = r.get("closing_point") or ""
        if r["market"] == "spreads" and cp:
            # a spread's two sides carry opposite numbers (-3.5 / +3.5): one group, keyed by the size of the number
            n = num(cp)
            cp = "" if n is None else repr(abs(n))
        return (r["event_id"], r["market"], r.get("market_description") or "", r.get("team") or "", r.get("player_name") or "", cp)

    def add(self, r):
        if (r.get("player_name") or "").startswith("(Watermark)") or (r.get("home_team") or "").startswith("(Watermark)"):
            return
        eid = r["event_id"]
        if eid not in self.events:
            try:
                start = datetime.datetime.fromisoformat(r["commence_time"].replace("Z", "+00:00"))
            except ValueError:
                return
            self.events[eid] = {"sport": r["sport_key"], "start": start, "home": r["home_team"], "away": r["away_team"]}
            self.by_sport[r["sport_key"]].append(eid)
        book = r.get("bookmaker")
        if book == "pinnacle" and num(r.get("closing_price")) is not None:
            self.pinn[self.gkey(r)][r["outcome_name"]] = num(r["closing_price"])
        elif book == "novig":
            self.novig[self.gkey(r) + (r["outcome_name"],)] = {
                "close": num(r.get("closing_price")), "price": num(r.get("price_american")), "resolution": r.get("resolution"), "actual": r.get("actual_value"), "line": num(r.get("line"))}

    def load(self, paths):
        for p in paths:
            for r in read_csv(p):
                self.add(r)

    def find_event(self, league, start, home_away):
        sport = LEAGUE_SPORT.get(league)
        if not sport:
            return None, "league %s not archived" % league
        a, h = home_away
        best = None
        for eid in self.by_sport.get(sport, []):
            ev = self.events[eid]
            if abs((ev["start"] - start).total_seconds()) > 2 * 3600:
                continue
            if (same_team(a, ev["away"]) and same_team(h, ev["home"])) or (same_team(a, ev["home"]) and same_team(h, ev["away"])):
                best = eid
                break
        return (best, None) if best else (None, "event not in the archive")


def parse_bet(row):
    """A lines.csv row -> (market key, market_description hint, team, player, outcome name, point) or (None, why)."""
    kind, sel, market = row["kind"], row["selection"], row["market"]
    m = re.match(r"^(.*?)\s*([+-]?\d+(?:\.\d+)?)$", sel)
    if kind == "MONEYLINE":
        return ("h2h", "", "", "", sel, None), None
    if kind == "SPREAD" and m:
        return ("spreads", "", "", "", m.group(1).strip(), num(m.group(2))), None
    if kind == "TOTAL" and m:
        return ("totals", "", "", "", m.group(1).strip(), num(m.group(2))), None
    if kind == "TEAM_TOTAL" and m:
        team_side = m.group(1).strip()
        mm = re.match(r"^(.*)\s+(Over|Under)$", team_side)
        if mm:
            return ("totals", "", mm.group(1).strip(), "", mm.group(2), num(m.group(2))), None
    if kind == "PROP":
        key = STAT_KEYS.get("".join(norm(market)))
        mm = re.match(r"^(.*)\s+(Over|Under)\s+([0-9.]+)$", sel)
        if key and mm:
            return (key, "", "", mm.group(1).strip(), mm.group(2), num(mm.group(3))), None
        return None, "prop type not mapped (%s)" % market
    return None, "kind %s not mapped (%s)" % (kind, market)


def grade_bet(arch, row):
    """-> dict of the pl_* columns for one lines.csv row."""
    out = {"pl_event_id": "", "pl_status": "", "pl_close_fair": "", "pl_pinn_close_price": "", "pl_novig_close_cost": "", "pl_novig_close_price": "", "pl_close_point": "",
           "pl_point_moved": "", "clv_pinn": "", "beat_novig_close": "", "pl_resolution": "", "pl_actual": ""}
    pb, why = parse_bet(row)
    if pb is None:
        out["pl_status"] = why
        return out
    market, desc, team, player, outcome, point = pb
    ev = row["event"]
    if " @ " not in ev:
        out["pl_status"] = "event name not 'A @ B'"
        return out
    away, home = [x.strip() for x in ev.split(" @ ", 1)]
    home = re.sub(r"\s+(Quarterfinals|Semifinals|Final|Round of \d+|Round \d+)$", "", home)
    try:
        start = datetime.datetime.fromisoformat(row["starts_utc"].replace(" ", "T")).replace(tzinfo=datetime.timezone.utc)
    except ValueError:
        out["pl_status"] = "bad start time"
        return out
    eid, why = arch.find_event(row["league"], start, (away, home))
    if eid is None:
        out["pl_status"] = why
        return out
    out["pl_event_id"] = eid
    # the PropLine groups of this event and market
    cands = [k for k in arch.pinn if k[0] == eid and k[1] == market and (not team or same_team(k[3], team)) and (not player or same_team(k[4], player)) and (market != "totals" or bool(k[3]) == bool(team))]
    if market == "h2h":
        cands = [k for k in cands if not k[2] or "money" in k[2].lower() or True]
    pick = None
    for k in cands:
        cp = num(k[5])
        want = abs(point) if (market == "spreads" and point is not None) else point
        if want is None or cp is None or abs(cp - want) < 1e-9:
            pick = k
            if cp is not None and want is not None and abs(cp - want) < 1e-9:
                break
    if pick is None:
        out["pl_status"] = "no Pinnacle close at this line (moved, or not offered)"
        return out
    prices = arch.pinn[pick]
    # which of Pinnacle's outcome names is our side
    side = None
    for name in prices:
        if (market in ("totals",) or player) and name.lower() == outcome.lower():
            side = name
        elif market in ("h2h", "spreads") and same_team(name, outcome):
            side = name
    fair = devig(prices)
    if side is None or fair is None:
        out["pl_status"] = "Pinnacle sides not matched"
        return out
    cp = num(pick[5])
    out["pl_pinn_close_price"] = prices[side]
    out["pl_close_point"] = "" if cp is None else cp
    if market == "spreads":
        # spreads were grouped by the size of the number: the bet and the close are the same line (a different size would not have been picked)
        out["pl_status"] = "ok"
    elif point is not None and cp is not None and abs(cp - point) > 1e-9:
        # the number moved: spreads are quoted per side (our side's number is the close for that side); for totals the move itself is the information
        out["pl_point_moved"] = round(cp - point, 3)
        if market == "totals":
            out["pl_status"] = "line moved %s -> %s: no like-for-like price" % (point, cp)
            return out
        out["pl_status"] = "line moved %s -> %s: sides compared at their own closing numbers" % (point, cp)
    else:
        out["pl_status"] = "ok"
    cost = num(row.get("cost"))
    if cost:
        out["pl_close_fair"] = round(fair[side], 5)
        out["clv_pinn"] = round(fair[side] / cost - 1, 5)
    nov = None
    for k, v in arch.novig.items():
        if k[:6] == pick[:5] + (pick[5],) and (k[6].lower() == outcome.lower() or same_team(k[6], outcome)):
            nov = v
            break
    if nov and nov["close"] is not None:
        out["pl_novig_close_price"] = nov["close"]
        ncost = implied(nov["close"])
        out["pl_novig_close_cost"] = round(ncost, 5)
        if cost:
            out["beat_novig_close"] = 1 if cost < ncost else 0
        out["pl_resolution"] = nov.get("resolution") or ""
        out["pl_actual"] = nov.get("actual") or ""
    return out


def summarize(rows):
    def stat(name, rs):
        c = [float(r["clv_pinn"]) for r in rs if r.get("clv_pinn") not in ("", None)]
        if not c:
            return "| %s | %d | 0 | | | |" % (name, len(rs))
        mean = sum(c) / len(c)
        sd = (sum((x - mean) ** 2 for x in c) / max(len(c) - 1, 1)) ** 0.5
        t = mean / (sd / len(c) ** 0.5) if sd > 0 and len(c) > 1 else 0.0
        beat = 100.0 * sum(1 for x in c if x > 0) / len(c)
        return "| %s | %d | %d | %+.2f%% | %.0f%% | %.1f |" % (name, len(rs), len(c), 100 * mean, beat, t)
    L = ["# PropLine CLV of the engine's bets (clv_pinn = Pinnacle's devigged close / our cost - 1)", "", "| group | bets | with a close | mean CLV | beat the close | t |", "|:--|--:|--:|--:|--:|--:|"]
    L.append(stat("ALL", rows))
    for col in ("shown", "league", "kind"):
        groups = defaultdict(list)
        for r in rows:
            groups[r.get(col, "")].append(r)
        for k in sorted(groups):
            L.append(stat("%s=%s" % (col, k), groups[k]))
    st = defaultdict(int)
    for r in rows:
        st[r.get("pl_status", "")[:50]] += 1
    L += ["", "Match status: " + "; ".join("%s %d" % (k, v) for k, v in sorted(st.items(), key=lambda x: -x[1])[:10])]
    return "\n".join(L) + "\n"


def run(lines, archives, out, summary):
    arch = Archive()
    arch.load(archives)
    print("archive: %d events, %d Pinnacle closes, %d Novig rows" % (len(arch.events), len(arch.pinn), len(arch.novig)))
    rows = []
    for p in lines:
        for r in read_csv(p):
            g = grade_bet(arch, r)
            r.update(g)
            rows.append(r)
    if not rows:
        print("no bets")
        return 0
    with gzip.open(out, "wt", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()), extrasaction="ignore")
        w.writeheader()
        w.writerows(rows)
    text = summarize(rows)
    if summary:
        open(summary, "w").write(text)
    print(text)
    return 0


def selftest():
    arch = Archive()
    def row(book, outcome, close, price, market="spreads", line="-3.5", cpoint="-3.5", team="", player=""):
        return {"event_id": "1", "sport_key": "basketball_nba", "commence_time": "2026-10-16T23:00:00+00:00", "home_team": "Philadelphia 76ers", "away_team": "Boston Celtics", "market": market,
                "market_description": "", "team": team, "player_name": player, "bookmaker": book, "outcome_name": outcome, "line": line, "price_american": str(price), "closing_price": str(close),
                "closing_point": cpoint, "resolution": "won", "actual_value": ""}
    for r in [row("pinnacle", "Philadelphia 76ers", -110, -105), row("pinnacle", "Boston Celtics", -110, -115), row("novig", "Philadelphia 76ers", -108, -104), row("novig", "Boston Celtics", -112, -116),
              row("pinnacle", "Over", -105, -105, "totals", "220.5", "220.5"), row("pinnacle", "Under", -115, -115, "totals", "220.5", "220.5"),
              row("pinnacle", "Over", -110, -110, "totals", "221.5", "221.5"), row("pinnacle", "Under", -110, -110, "totals", "221.5", "221.5")]:
        arch.add(r)
    arch.add(dict(row("pinnacle", "X", -100, -100), player_name="(Watermark) Z", event_id="9"))
    assert len(arch.events) == 1
    bet = {"id": "a", "league": "NBA", "kind": "SPREAD", "market": "Spread", "selection": "Philadelphia 76ers -3.5", "event": "Boston Celtics @ Philadelphia 76ers", "starts_utc": "2026-10-16 23:00:00", "cost": "0.50", "american": "100"}
    g = grade_bet(arch, bet)
    assert g["pl_status"] == "ok" and abs(float(g["pl_close_fair"]) - 0.5) < 1e-9 and abs(float(g["clv_pinn"])) < 1e-9, g
    assert g["beat_novig_close"] == 1 and g["pl_resolution"] == "won", g       # novig closed at -108 (cost .519): 0.50 < .519
    tot = dict(bet, kind="TOTAL", market="Total", selection="Over 220.5", cost="0.50")
    g = grade_bet(arch, tot)
    assert g["pl_status"] == "ok" and abs(float(g["pl_close_fair"]) - 0.4892) < 1e-3 and abs(float(g["clv_pinn"]) + 0.0217) < 1e-3, g   # Over closed -105/-115: fair .4892 (cost .50: -2.2%)
    moved = dict(tot, selection="Over 219.5")
    g = grade_bet(arch, moved)
    assert "line moved" in g["pl_status"] or g["pl_status"] .startswith("no Pinnacle close"), g
    assert grade_bet(arch, dict(bet, league="CFL"))["pl_status"].startswith("league")
    assert grade_bet(arch, dict(bet, event="Nobody @ Nowhere"))["pl_status"] == "event not in the archive"
    assert devig({"a": -110, "b": -110}) == {"a": 0.5, "b": 0.5} and devig({"a": 100}) is None
    assert same_team("Boston Celtics", "Celtics") and not same_team("Boston Celtics", "Brooklyn Nets")
    print("propline_clv selftest ok")
    return 0


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("run"); r.add_argument("--lines", nargs="+", required=True); r.add_argument("--archive", nargs="+", required=True); r.add_argument("--out", required=True); r.add_argument("--summary")
    sub.add_parser("selftest")
    a = ap.parse_args()
    sys.exit(selftest() if a.cmd == "selftest" else run(a.lines, a.archive, a.out, a.summary))
