#!/usr/bin/env python3
"""College basketball against-the-spread log, ratings and backtest (NC1, Tj 2026-11-05: "log and track scores and closing spreads every day for every college basketball team that has
betting odds ... automatically in the background, on github"; his old method: each team's offensive points per game and a green-plus / red-minus average of how it did against the spread).

  python3 -I tools/ncaab_ats.py run      --data-dir D [--days-back 3]            # the daily job: log finished games, rebuild ratings and the slate
  python3 -I tools/ncaab_ats.py collect  --data-dir D --from 2025-11-03 --to 2026-04-07   # backfill a range of days (idempotent)
  python3 -I tools/ncaab_ats.py rate     --data-dir D                            # ratings.json from the games already logged
  python3 -I tools/ncaab_ats.py backtest --data-dir D [--split 2026-02-01]       # walk-forward test of the method + fitted weights (model.json)
  python3 -I tools/ncaab_ats.py selftest

Data (all public, no key): ESPN's scoreboard (final scores, the day's schedule) and ESPN's core odds feed (ESPN BET's OPENING and CLOSING spread and total for every game that has a line; a game with no
line is a non-Division I opponent or an exhibition and is not logged, which is also the "small school" filter). Files under D (the `ncaab-data` branch, append-only):
  games/YYYY-MM.jsonl  one game per line: id, d (Eastern date), t (start ms), h/a (ESPN team ids), hs/as (final), neu (neutral site), sp (closing HOME spread, negative = home favoured), tt (closing total), osp/ott (opening), ml (home money line)
  teams.json           id -> {n: school name, ab: abbreviation}
  ratings.json         per team: games used, points for/against per game, the against-the-spread average (tier mean) and its symbol, the last results; plus the model weights
  slate.json           today's and tomorrow's games with ESPN's current line (a fallback: the app shows Novig's)
  model.json / backtest.md   weights fitted by `backtest` and the evidence behind them
The tiers (Tj's method): cover margin = (team's margin) + (its closing spread). |margin| < tie = 0 (a "tie"), < dbl = +/-1 (a green plus / red minus), else +/-2 (double). A team's average
tier is the mean over its games. Outliers are left out of every average: games with a spread of `mismatch` points or more (a big school against a small one) and, for the points, any score further
than `z` robust standard deviations from the team's median.
"""
import argparse, concurrent.futures as cf, datetime as dt, json, math, os, statistics, sys, time, urllib.request, urllib.error
from zoneinfo import ZoneInfo

ET = ZoneInfo("America/New_York")
SB = "https://site.api.espn.com/apis/site/v2/sports/basketball/mens-college-basketball/scoreboard?dates={d}&groups=50&limit=400"
ODDS = "https://sports.core.api.espn.com/v2/sports/basketball/leagues/mens-college-basketball/events/{e}/competitions/{e}/odds"
UA = "Vigilant-ncaab-log/1.0 (+https://github.com/tjshea90/novig)"

CFG = dict(tie=3.0, dbl=10.0, mismatch=20.0, z=2.0, sd_floor=6.0, min_games=3, cm_clip=25.0, recent=8, trim_min_n=6)
# weights used until `backtest` has fitted better ones: points of expected cover margin per point of scoring difference / tier of difference. They are Tj's judgement, not measured.
DEFAULT_MODEL = dict(kO=0.0, kD=0.0, kR=0.0, kT=0.0, sigma=11.0, source="none", note="no weights fitted yet")


# ---------------------------------------------------------------------------------------------- fetching

def http_json(url, tries=4, timeout=25):
    last = None
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            last = e
            if e.code == 404:
                return None
        except Exception as e:  # network trouble: wait and try again
            last = e
        time.sleep(1.5 * (i + 1))
    raise RuntimeError(f"giving up on {url}: {last}")


def num(s):
    """'-6.5' / '+3.5' / '156.5' / 'PK' -> float or None."""
    if s is None:
        return None
    t = str(s).strip().replace("+", "")
    if t.upper() in ("PK", "PICK", "EVEN"):
        return 0.0
    try:
        return float(t)
    except ValueError:
        return None


def home_spread(item, which):
    """The HOME team's spread at `which` ('open' / 'close' / 'current'), negative when the home team is favoured."""
    blk = ((item.get("homeTeamOdds") or {}).get(which)) or {}
    sp = num(((blk.get("pointSpread") or {}).get("american")))
    if sp is not None:
        return sp
    if which in ("close", "current") and item.get("spread") is not None:
        mag = abs(float(item["spread"]))
        if (item.get("homeTeamOdds") or {}).get("favorite"):
            return -mag
        if (item.get("awayTeamOdds") or {}).get("favorite"):
            return mag
        return 0.0 if mag == 0 else float(item["spread"])
    return None


def total_line(item, which):
    t = num((((item.get(which) or {}).get("total")) or {}).get("american"))
    if t is None and which in ("close", "current"):
        t = num(item.get("overUnder"))
    return t


def home_ml(item, which):
    blk = ((item.get("homeTeamOdds") or {}).get(which)) or {}
    return num((blk.get("moneyLine") or {}).get("american"))


def sane(sp, tt):
    return sp is not None and tt is not None and abs(sp) <= 60 and 90 <= tt <= 260


def fetch_odds(eid):
    """A pregame provider's lines for an event: dict(sp, tt, osp, ott, ml, pv) or None when it has none. ESPN changed providers mid-season 2025-26 (ESPN BET, then DraftKings), so the
    first pregame provider with a CLOSING block wins (DraftKings, then ESPN BET, then any other); the live-odds feed ("... Live Odds") is never used."""
    d = http_json(ODDS.format(e=eid))
    if not d:
        return None
    items = [i for i in d.get("items", []) if "live" not in str((i.get("provider") or {}).get("name", "")).lower()]
    rank = {"100": 0, "58": 1}
    items.sort(key=lambda i: (rank.get(str((i.get("provider") or {}).get("id")), 9), "close" not in i))
    for it in items:
        sp = home_spread(it, "close") if "close" in it else None
        tt = total_line(it, "close") if "close" in it else None
        if sp is None:
            sp = home_spread(it, "current")
        if tt is None:
            tt = total_line(it, "current")
        if not sane(sp, tt):
            continue
        out = dict(sp=sp, tt=tt, pv=str((it.get("provider") or {}).get("id")))
        osp = home_spread(it, "open")
        if osp is not None:
            out["osp"] = osp
        ot = total_line(it, "open")
        if ot is not None:
            out["ott"] = ot
        ml = home_ml(it, "close") or home_ml(it, "current")
        if ml is not None:
            out["ml"] = ml
        return out
    return None


def event_info(e):
    """The scoreboard event as {id, t, state, neu, home{...}, away{...}} or None."""
    try:
        c = e["competitions"][0]
        h = next(x for x in c["competitors"] if x["homeAway"] == "home")
        a = next(x for x in c["competitors"] if x["homeAway"] == "away")
        st = c["status"]["type"]
        t = int(dt.datetime.fromisoformat(e["date"].replace("Z", "+00:00")).timestamp() * 1000)

        def team(x):
            tm = x["team"]
            rk = (x.get("curatedRank") or {}).get("current")
            return dict(id=str(tm["id"]), n=tm.get("location") or tm.get("shortDisplayName") or tm.get("displayName"), ab=tm.get("abbreviation") or "",
                        dn=tm.get("displayName") or "", score=x.get("score"), rank=rk if isinstance(rk, int) and 0 < rk < 99 else None)
        return dict(id=str(e["id"]), t=t, state=st.get("state"), done=bool(st.get("completed")), neu=1 if c.get("neutralSite") else 0, home=team(h), away=team(a),
                    detail=st.get("shortDetail") or "")
    except Exception:
        return None


def day_events(date):
    d = http_json(SB.format(d=date.strftime("%Y%m%d")))
    return [i for i in (event_info(e) for e in (d or {}).get("events", [])) if i]


def et_date(ms):
    return dt.datetime.fromtimestamp(ms / 1000, tz=ET).date()


# ---------------------------------------------------------------------------------------------- storage

def month_file(data_dir, d):
    return os.path.join(data_dir, "games", d[:7] + ".jsonl")


def load_games(data_dir):
    out = {}
    gdir = os.path.join(data_dir, "games")
    if os.path.isdir(gdir):
        for f in sorted(os.listdir(gdir)):
            if f.endswith(".jsonl"):
                with open(os.path.join(gdir, f)) as fh:
                    for line in fh:
                        line = line.strip()
                        if line:
                            g = json.loads(line)
                            out[g["id"]] = g
    return out


def save_games(data_dir, games):
    by_month = {}
    for g in games.values():
        by_month.setdefault(g["d"][:7], []).append(g)
    os.makedirs(os.path.join(data_dir, "games"), exist_ok=True)
    for m, lst in by_month.items():
        lst.sort(key=lambda g: (g["t"], g["id"]))
        path = os.path.join(data_dir, "games", m + ".jsonl")
        body = "".join(json.dumps(g, separators=(",", ":")) + "\n" for g in lst)
        if not os.path.exists(path) or open(path).read() != body:  # only touch a file that changed
            with open(path, "w") as fh:
                fh.write(body)


def load_json(path, default):
    try:
        with open(path) as fh:
            return json.load(fh)
    except Exception:
        return default


def save_json(path, obj):
    body = json.dumps(obj, separators=(",", ":"), sort_keys=True)
    if os.path.exists(path) and open(path).read() == body:
        return False
    with open(path, "w") as fh:
        fh.write(body)
    return True


def collect(data_dir, days, workers=8, log=print):
    """Log every finished game of `days` that has a closing line. Returns (new games, finished games without a line)."""
    games = load_games(data_dir)
    teams = load_json(os.path.join(data_dir, "teams.json"), {})
    new = nolines = 0
    for day in days:
        evs = [e for e in day_events(day) if e["done"] and e["home"]["score"] is not None and e["away"]["score"] is not None]
        todo = [e for e in evs if e["id"] not in games]
        with cf.ThreadPoolExecutor(workers) as ex:
            lines = list(ex.map(lambda e: fetch_odds(e["id"]), todo))
        for e, ln in zip(todo, lines):
            if not ln:
                nolines += 1
                continue
            try:
                hs, as_ = int(e["home"]["score"]), int(e["away"]["score"])
            except (TypeError, ValueError):
                continue
            g = dict(id=e["id"], d=et_date(e["t"]).isoformat(), t=e["t"], h=e["home"]["id"], a=e["away"]["id"], hs=hs, **{"as": as_}, neu=e["neu"], sp=ln["sp"], tt=ln["tt"])
            for k in ("osp", "ott", "ml", "pv"):
                if k in ln:
                    g[k] = ln[k]
            games[g["id"]] = g
            new += 1
        for e in evs:
            for side in ("home", "away"):
                tm = e[side]
                teams[tm["id"]] = dict(n=tm["n"], ab=tm["ab"])
        log(f"{day}: {len(evs)} finished, {len(todo)} new, {sum(1 for e, l in zip(todo, lines) if l)} with a line")
        if todo:  # save as we go: a run cut off mid-backfill keeps every day it finished
            save_games(data_dir, games)
            save_json(os.path.join(data_dir, "teams.json"), teams)
    save_games(data_dir, games)
    save_json(os.path.join(data_dir, "teams.json"), teams)
    return new, nolines


def build_slate(data_dir, today, log=print):
    """Today's and tomorrow's games (Eastern) with ESPN BET's current line, for the app's schedule."""
    teams = load_json(os.path.join(data_dir, "teams.json"), {})
    out = []
    for day in (today, today + dt.timedelta(days=1)):
        evs = [e for e in day_events(day)]
        with cf.ThreadPoolExecutor(8) as ex:
            lines = list(ex.map(lambda e: None if e["done"] else fetch_odds(e["id"]), evs))
        for e, ln in zip(evs, lines):
            g = dict(id=e["id"], t=e["t"], d=et_date(e["t"]).isoformat(), state=e["state"], neu=e["neu"], detail=e["detail"],
                     h=dict(id=e["home"]["id"], n=e["home"]["n"], dn=e["home"]["dn"], ab=e["home"]["ab"], rk=e["home"]["rank"]),
                     a=dict(id=e["away"]["id"], n=e["away"]["n"], dn=e["away"]["dn"], ab=e["away"]["ab"], rk=e["away"]["rank"]))
            if e["state"] != "pre" and e["home"]["score"] is not None:
                g["hs"], g["as"] = e["home"]["score"], e["away"]["score"]
            if ln:
                g["sp"], g["tt"] = ln["sp"], ln["tt"]
            for side in ("home", "away"):
                tm = e[side]
                teams.setdefault(tm["id"], dict(n=tm["n"], ab=tm["ab"]))
            out.append(g)
        log(f"slate {day}: {len(evs)} games, {sum(1 for l in lines if l)} with a line")
    save_json(os.path.join(data_dir, "teams.json"), teams)
    out.sort(key=lambda g: (g["t"], g["id"]))
    return out


# ---------------------------------------------------------------------------------------------- ratings

def tier(cm, cfg=CFG):
    a = abs(cm)
    s = 1 if cm > 0 else -1
    return 0 if a < cfg["tie"] else (s if a < cfg["dbl"] else 2 * s)


def sym_of(ats):
    return "++" if ats >= 1.2 else "+" if ats >= 0.4 else "=" if ats > -0.4 else "-" if ats > -1.2 else "--"


def trimmed_mean(xs, cfg=CFG):
    """Mean without scores far from the team's median (only once there are `trim_min_n` games). Returns (mean, dropped)."""
    if len(xs) >= cfg["trim_min_n"]:
        med = statistics.median(xs)
        mad = statistics.median(abs(x - med) for x in xs)
        sd = max(1.4826 * mad, cfg["sd_floor"])
        kept = [x for x in xs if abs(x - med) <= cfg["z"] * sd]
        if kept:
            return sum(kept) / len(kept), len(xs) - len(kept)
    return sum(xs) / len(xs), 0


def team_game(g, team):
    """A logged game seen from `team`: points for/against, its closing spread, the cover margin."""
    home = g["h"] == team
    pf, pa = (g["hs"], g["as"]) if home else (g["as"], g["hs"])
    tsp = g["sp"] if home else -g["sp"]
    return dict(t=g["t"], pf=pf, pa=pa, tsp=tsp, cm=(pf - pa) + tsp)


def rate_team(tgames, cfg=CFG):
    """The team's ratings from its games (oldest first), or None with fewer than `min_games` usable ones."""
    usable = [x for x in tgames if abs(x["tsp"]) < cfg["mismatch"]]
    if len(usable) < cfg["min_games"]:
        return None
    ppg, dropped_f = trimmed_mean([x["pf"] for x in usable], cfg)
    papg, dropped_a = trimmed_mean([x["pa"] for x in usable], cfg)
    cms = [max(-cfg["cm_clip"], min(cfg["cm_clip"], x["cm"])) for x in usable]
    tiers = [tier(x["cm"], cfg) for x in usable]
    ats = sum(tiers) / len(tiers)
    return dict(g=len(usable), all=len(tgames), ex=len(tgames) - len(usable) + max(dropped_f, dropped_a), ppg=round(ppg, 2), papg=round(papg, 2), ats=round(ats, 3),
                cm=round(sum(cms) / len(cms), 2), sym=sym_of(ats), last=tiers[-cfg["recent"]:])


def season_of(d):
    y, m = int(d[:4]), int(d[5:7])
    return y + 1 if m >= 8 else y


def build_ratings(games, as_of_ms, teams, cfg=CFG, model=None):
    """ratings.json: every team with enough games this season, as of `as_of_ms`."""
    season = season_of(et_date(as_of_ms).isoformat())
    per = {}
    for g in sorted(games.values(), key=lambda g: (g["t"], g["id"])):
        if g["t"] > as_of_ms or season_of(g["d"]) != season:
            continue
        for side in ("h", "a"):
            per.setdefault(g[side], []).append(team_game(g, g[side]))
    out = {}
    for tid, lst in per.items():
        r = rate_team(lst, cfg)
        if r:
            nm = teams.get(tid, {})
            r["n"], r["ab"] = nm.get("n", tid), nm.get("ab", "")
            out[tid] = r
    return dict(asOf=as_of_ms, season=season, cfg=cfg, teams=out, model=model or DEFAULT_MODEL, games=sum(1 for g in games.values() if season_of(g["d"]) == season))


# ---------------------------------------------------------------------------------------------- backtest

def walk_forward(games, cfg=CFG):
    """One row per game with both teams rated BEFORE it (games strictly earlier in the same season): features and the two results to explain."""
    per = {}
    rows = []
    for g in sorted(games.values(), key=lambda g: (g["t"], g["id"])):
        s = season_of(g["d"])
        rh = rate_team(per.get((s, g["h"]), []), cfg)
        ra = rate_team(per.get((s, g["a"]), []), cfg)
        if rh and ra:
            rows.append(dict(
                id=g["id"], d=g["d"], t=g["t"], sp=g["sp"], tt=g["tt"],
                dO=rh["ppg"] - ra["ppg"], dD=rh["papg"] - ra["papg"], dR=rh["ats"] - ra["ats"], dCM=rh["cm"] - ra["cm"],
                sys_total=(rh["ppg"] + ra["papg"]) / 2 + (ra["ppg"] + rh["papg"]) / 2,
                neu=g.get("neu", 0), ratH=rh["ats"], ratA=ra["ats"], imp=((rh["ppg"] - rh["papg"]) - (ra["ppg"] - ra["papg"])) / 2,
                cover=(g["hs"] - g["as"]) + g["sp"], over=(g["hs"] + g["as"]) - g["tt"], hs=g["hs"], **{"as": g["as"]}))
        for side in ("h", "a"):
            per.setdefault((s, g[side]), []).append(team_game(g, g[side]))
    return rows


def ols(X, y):
    """Least squares with an intercept (normal equations); returns (coefs, t-stats). Tiny and dependency-free."""
    n, k = len(X), len(X[0]) + 1
    A = [[1.0] + list(r) for r in X]
    XtX = [[sum(A[i][a] * A[i][b] for i in range(n)) for b in range(k)] for a in range(k)]
    Xty = [sum(A[i][a] * y[i] for i in range(n)) for a in range(k)]
    inv = invert(XtX)
    beta = [sum(inv[a][b] * Xty[b] for b in range(k)) for a in range(k)]
    res = [y[i] - sum(A[i][a] * beta[a] for a in range(k)) for i in range(n)]
    s2 = sum(r * r for r in res) / max(1, n - k)
    t = [beta[a] / math.sqrt(max(1e-12, s2 * inv[a][a])) for a in range(k)]
    return beta, t


def invert(M):
    n = len(M)
    A = [row[:] + [1.0 if i == j else 0.0 for j in range(n)] for i, row in enumerate(M)]
    for c in range(n):
        p = max(range(c, n), key=lambda r: abs(A[r][c]))
        A[c], A[p] = A[p], A[c]
        d = A[c][c] or 1e-12
        A[c] = [v / d for v in A[c]]
        for r in range(n):
            if r != c:
                f = A[r][c]
                A[r] = [A[r][j] - f * A[c][j] for j in range(2 * n)]
    return [row[n:] for row in A]


def bucket_report(pairs, edges):
    """pairs = [(signal in points, won 1/0/0.5 or None for a push)]; the hit rate for |signal| in each bucket."""
    out = []
    for lo, hi in zip(edges, edges[1:] + [1e9]):
        sel = [w for s, w in pairs if lo <= abs(s) < hi and w is not None]
        if sel:
            out.append((lo, hi, len(sel), sum(sel) / len(sel)))
    return out


def side_result(edge, outcome):
    """The signal says back the side `edge` points toward (positive = home / over); outcome is the actual cover / over margin. 1 won, 0 lost, None push."""
    if outcome == 0:
        return None
    return 1.0 if (edge > 0) == (outcome > 0) else 0.0


def backtest(games, split, cfg=CFG, log=print):
    rows = walk_forward(games, cfg)
    train = [r for r in rows if r["d"] < split]
    test = [r for r in rows if r["d"] >= split]
    rep = [f"# College basketball method backtest\n", f"games with both teams rated: {len(rows)} (train < {split}: {len(train)}, test >= {split}: {len(test)}); winning needs 52.38% at -110.\n"]
    model = dict(DEFAULT_MODEL)
    if len(train) < 200 or len(test) < 100:
        rep.append("Too few games to fit anything yet.\n")
        return model, "\n".join(rep)

    def fit(rs, cols, target):
        beta, t = ols([[r[c] for c in cols] for r in rs], [r[target] for r in rs])
        return beta, t

    cols = ["dO", "dD", "dR"]
    beta, t = fit(train, cols, "cover")
    rep.append(f"## Spread: cover margin ~ points for/against difference and against-the-spread difference (train)\n")
    rep.append("| term | coefficient | t |\n|-|-|-|")
    for nm, b, tt_ in zip(["intercept"] + cols, beta, t):
        rep.append(f"| {nm} | {b:+.3f} | {tt_:+.2f} |")
    rep.append("")

    def edge_of(r, b):
        return b[1] * r["dO"] + b[2] * r["dD"] + b[3] * r["dR"]

    # out of sample: back the side the fitted edge points to; bucket by the size of the edge
    pairs = [(edge_of(r, beta), side_result(edge_of(r, beta), r["cover"])) for r in test]
    rep.append("### Out of sample: backing the side the edge points to, by size of the edge (points)\n")
    rep.append("| edge | games | covered |\n|-|-|-|")
    for lo, hi, n, p in bucket_report(pairs, [0, 1, 2, 3, 4]):
        rep.append(f"| {lo}-{'+' if hi > 1e8 else hi} | {n} | {p:.1%} |")
    allp = [w for _, w in pairs if w is not None]
    rep.append(f"| all | {len(allp)} | {sum(allp) / len(allp):.1%} |\n")

    # Tj's own rule of thumb, no fitting: team ahead on BOTH scoring and the against-the-spread average
    both = [(1 if r["dO"] > 0 else -1, r) for r in test if r["dO"] * r["dR"] > 0 and abs(r["dR"]) >= 1.0 and abs(r["dO"]) >= 3.0]
    wl = [side_result(s, r["cover"]) for s, r in both]
    wl = [w for w in wl if w is not None]
    if wl:
        rep.append(f"Tj's rule of thumb with no fitting (the team ahead on scoring by 3+ AND on the against-the-spread average by 1+ tiers, back it): {len(wl)} games, covered {sum(wl) / len(wl):.1%}\n")

    # The signals the app shows, no fitting: judged on every rated game, then on each half (a signal that only works in one half is noise)
    HCA = 3.0
    for r in rows:
        r["gap_s"] = r["imp"] + (0.0 if r["neu"] else HCA) + r["sp"]      # >0: the averages like home more than the line does
    mid = sorted(r["d"] for r in rows)[len(rows) // 2]
    rep.append("## The signals the app shows (no fitting; all rated games, then first half / second half of the season)\n")

    def table(title, pairs_all, edges, unit="points"):
        rep.append(f"### {title}\n")
        rep.append(f"| size ({unit}) | games | right | first half | second half |\n|-|-|-|-|-|")
        for lo, hi in zip(edges, edges[1:] + [1e9]):
            def rate(sel):
                w = [x for s_, x, d in sel if lo <= abs(s_) < hi and x is not None]
                return (len(w), sum(w) / len(w)) if w else (0, float("nan"))
            n_all, p_all = rate(pairs_all)
            n1, p1 = rate([t for t in pairs_all if t[2] < mid])
            n2, p2 = rate([t for t in pairs_all if t[2] >= mid])
            if n_all:
                rep.append(f"| {lo}-{'+' if hi > 1e8 else hi} | {n_all} | {p_all:.1%} | {p1:.1%} ({n1}) | {p2:.1%} ({n2}) |")
        rep.append("")

    table("Spread: back the side the AVERAGES favour more than the line does (net points per game gap vs the spread)",
          [(r["gap_s"], side_result(r["gap_s"], r["cover"]), r["d"]) for r in rows], [0, 3, 6, 9, 12])
    table("Spread: back the team with the BETTER against-the-spread average (your green plus / red minus)",
          [(r["dR"], side_result(r["dR"], r["cover"]), r["d"]) for r in rows], [0.0, 0.5, 1.0, 1.5], "tiers")
    table("Spread: FADE that team (back the one with the worse against-the-spread average)",
          [(-r["dR"], side_result(-r["dR"], r["cover"]), r["d"]) for r in rows], [0.0, 0.5, 1.0, 1.5], "tiers")
    both = [(r["dO"], side_result(r["dO"], r["cover"]), r["d"]) for r in rows if r["dO"] * r["dR"] > 0 and abs(r["dR"]) >= 0.5]
    table("Spread: your rule - the team ahead on BOTH scoring and the against-the-spread average (gap in points per game)", both, [0, 3, 6, 9, 12])
    for r in rows:
        r["dT"] = r["sys_total"] - r["tt"]
    table("Total: Over when the matchup estimate (each side's scoring and the other's points allowed) is above the line, Under when below",
          [(r["dT"], side_result(r["dT"], r["over"]), r["d"]) for r in rows], [0, 3, 6, 9, 12])
    rows_sum = [(r["ratH"] + r["ratA"], side_result(r["ratH"] + r["ratA"], r["over"]) if False else None, r["d"]) for r in rows]

    # totals
    for r in rows:
        r["dT"] = r["sys_total"] - r["tt"]
    tb, tt_ = fit(train, ["dT"], "over")
    rep.append("## Total: actual total minus the line ~ (matchup estimate of the total minus the line) (train)\n")
    rep.append(f"intercept {tb[0]:+.3f} (t {tt_[0]:+.2f}), slope {tb[1]:+.3f} (t {tt_[1]:+.2f})\n")
    test_t = [r for r in rows if r["d"] >= split]
    tp = [(r["dT"], side_result(r["dT"], r["over"])) for r in test_t]
    rep.append("### Out of sample: Over when the estimate is above the line, Under when below, by the gap (points)\n")
    rep.append("| gap | games | right |\n|-|-|-|")
    for lo, hi, n, p in bucket_report(tp, [0, 3, 6, 9, 12]):
        rep.append(f"| {lo}-{'+' if hi > 1e8 else hi} | {n} | {p:.1%} |")
    allt = [w for _, w in tp if w is not None]
    rep.append(f"| all | {len(allt)} | {sum(allt) / len(allt):.1%} |\n")
    sd = statistics.pstdev([r["cover"] for r in rows])
    model.update(kO=round(beta[1], 4), kD=round(beta[2], 4), kR=round(beta[3], 4), kT=round(tb[1], 4), sigma=round(sd, 2), source="backtest",
                 note=f"fitted on {len(train)} games before {split}; the spread terms' t-stats are {t[1]:+.1f}, {t[2]:+.1f}, {t[3]:+.1f}; the total slope's is {tt_[1]:+.1f}")
    log("\n".join(rep))
    return model, "\n".join(rep)


# ---------------------------------------------------------------------------------------------- commands

def today_et():
    return dt.datetime.now(ET).date()


def drange(a, b):
    d = a
    while d <= b:
        yield d
        d += dt.timedelta(days=1)


def rebuild(data_dir, log=print):
    games = load_games(data_dir)
    teams = load_json(os.path.join(data_dir, "teams.json"), {})
    model = load_json(os.path.join(data_dir, "model.json"), DEFAULT_MODEL)
    now = int(time.time() * 1000)
    r = build_ratings(games, now, teams, CFG, model)
    changed = save_json(os.path.join(data_dir, "ratings.json"), r)
    log(f"ratings: {len(r['teams'])} teams from {r['games']} games this season{' (changed)' if changed else ''}")


def main(argv):
    sys.stdout.reconfigure(line_buffering=True)  # progress shows at once in a workflow log
    ap = argparse.ArgumentParser()
    ap.add_argument("cmd", choices=["run", "collect", "rate", "backtest", "selftest"])
    ap.add_argument("--data-dir", default="ncaab-data")
    ap.add_argument("--days-back", type=int, default=3)
    ap.add_argument("--from", dest="start")
    ap.add_argument("--to", dest="end")
    ap.add_argument("--split", default=None)
    a = ap.parse_args(argv)
    if a.cmd == "selftest":
        return selftest()
    os.makedirs(a.data_dir, exist_ok=True)
    if a.cmd == "run":
        t = today_et()
        new, nol = collect(a.data_dir, list(drange(t - dt.timedelta(days=a.days_back), t)))
        print(f"logged {new} new games ({nol} finished without a line: not Division I or no market)")
        slate = build_slate(a.data_dir, t)
        save_json(os.path.join(a.data_dir, "slate.json"), dict(asOf=int(time.time() * 1000), games=slate))
        rebuild(a.data_dir)
    elif a.cmd == "collect":
        s, e = dt.date.fromisoformat(a.start), dt.date.fromisoformat(a.end or today_et().isoformat())
        new, nol = collect(a.data_dir, list(drange(s, e)))
        print(f"logged {new} new games ({nol} without a line)")
        rebuild(a.data_dir)
    elif a.cmd == "rate":
        rebuild(a.data_dir)
    elif a.cmd == "backtest":
        games = load_games(a.data_dir)
        split = a.split or "2026-02-01"
        model, report = backtest(games, split)
        save_json(os.path.join(a.data_dir, "model.json"), model)
        with open(os.path.join(a.data_dir, "backtest.md"), "w") as fh:
            fh.write(report + "\n")
        rebuild(a.data_dir)
    return 0


# ---------------------------------------------------------------------------------------------- selftest

def selftest():
    import tempfile
    ok = 0

    def check(cond, msg):
        nonlocal ok
        if not cond:
            raise SystemExit("FAIL: " + msg)
        ok += 1

    # tiers: a tie inside 3, a plus from 3, a double from 10, mirrored for the losers
    check([tier(x) for x in (0, 2.9, -2.9, 3, -3, 9.9, 10, -10, 25)] == [0, 0, 0, 1, -1, 1, 2, -2, 2], "tiers")
    check([sym_of(x) for x in (1.5, 0.5, 0.0, -0.5, -1.5)] == ["++", "+", "=", "-", "--"], "symbols")
    # the cover margin: -6.5 favourite winning by 9 covers by 2.5, losing by 3 misses by 9.5
    g = dict(id="1", d="2025-11-15", t=1, h="H", a="A", hs=86, **{"as": 77}, sp=-6.5)
    check(team_game(g, "H")["cm"] == 2.5 and team_game(g, "A")["cm"] == -2.5 and team_game(g, "A")["tsp"] == 6.5, "cover margin both sides")
    # spread parsing: favourite flags decide the sign when only the top-level spread is there
    check(home_spread({"spread": -3.5, "homeTeamOdds": {"favorite": False}, "awayTeamOdds": {"favorite": True}}, "close") == 3.5, "away favourite -> home spread positive")
    it = {"homeTeamOdds": {"close": {"pointSpread": {"american": "-6.5"}}, "open": {"pointSpread": {"american": "-3.5"}}}, "close": {"total": {"american": "156.5"}}, "open": {"total": {"american": "154.5"}}}
    check(home_spread(it, "close") == -6.5 and home_spread(it, "open") == -3.5 and total_line(it, "close") == 156.5, "close/open parsing")
    check(num("PK") == 0.0 and num("+3.5") == 3.5 and num(None) is None and num("x") is None, "num")
    # outliers: a 20-point spread game is left out of everything; one wild score is trimmed from the points only
    tg = [dict(t=i, pf=70 + (i % 3), pa=65, tsp=-2.0, cm=5.0 + (i % 3)) for i in range(8)]
    tg.append(dict(t=9, pf=110, pa=50, tsp=-28.0, cm=32.0))            # a cupcake: dropped by the mismatch rule
    r = rate_team(tg)
    check(r["g"] == 8 and r["all"] == 9 and r["ppg"] == round(statistics.mean(70 + (i % 3) for i in range(8)), 2), "mismatch game left out")
    tg2 = [dict(t=i, pf=70, pa=65, tsp=-1.0, cm=4.0) for i in range(7)] + [dict(t=8, pf=120, pa=65, tsp=-1.0, cm=54.0)]
    r2 = rate_team(tg2)
    check(r2["ppg"] == 70.0 and r2["ats"] > 0.8, f"a 120-point game is trimmed from ppg ({r2['ppg']})")
    check(rate_team(tg[:2]) is None, "fewer than min_games -> no rating")
    # walk-forward uses only earlier games: the second meeting sees the first, the first sees nothing
    games = {}
    for i in range(12):
        games[str(i)] = dict(id=str(i), d="2025-11-%02d" % (3 + i), t=1_000_000 + i * 86_400_000, h="A" if i % 2 else "B", a="C", hs=80, **{"as": 70}, sp=-5.0, tt=150.0)
    rows = walk_forward(games)
    check(all(r["d"] >= "2025-11-06" for r in rows) and len(rows) > 0, "no row before both teams have min_games prior games")
    # OLS recovers a known relation
    X = [[float(i % 7), float(i % 5)] for i in range(200)]
    y = [3.0 + 2.0 * a - 1.0 * b + (0.001 * ((i * 7) % 11 - 5)) for i, (a, b) in enumerate(X)]
    beta, t = ols(X, y)
    check(abs(beta[0] - 3) < 0.05 and abs(beta[1] - 2) < 0.01 and abs(beta[2] + 1) < 0.01, "ols")
    check(side_result(2.0, 3.0) == 1.0 and side_result(-2.0, 3.0) == 0.0 and side_result(2.0, 0) is None, "side result")
    # storage round trip, idempotent
    with tempfile.TemporaryDirectory() as d:
        save_games(d, {"1": dict(id="1", d="2025-11-15", t=5, h="H", a="A", hs=86, **{"as": 77}, sp=-6.5, tt=150.5)})
        check(load_games(d)["1"]["sp"] == -6.5, "games round trip")
        check(save_json(os.path.join(d, "x.json"), {"a": 1}) is True and save_json(os.path.join(d, "x.json"), {"a": 1}) is False, "save_json unchanged -> False")
    check(season_of("2025-11-03") == 2026 and season_of("2026-03-20") == 2026 and season_of("2026-10-30") == 2027, "season label")
    print(f"ncaab_ats selftest: {ok} checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
