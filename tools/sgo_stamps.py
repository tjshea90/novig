#!/usr/bin/env python3
"""What SportsGameOdds' `lastUpdatedAt` means (PX9h, Tj 2026-10-25: "Shouldn't you review the docs thoroughly for ... figuring out the freshness problem?").

SGO's docs (llms-full, 2026-10-25) give `lastUpdatedAt` per book and alternate line but never say whether it is the time the PRICE LAST CHANGED or the time SGO LAST READ the book. The engine dates
every SGO quote by it (Freshness drops one older than the limit; the list shows its age), and SGO supplied 60 of the 75 quotes behind the listed bets at a median 452 s on 2026-10-25. If it is
"last changed", those ages overstate how old the prices are and current prices are being thrown away as too old; if it is "last read", the ages are true.

Two measurements, on Tj's SGO key (secret SGO_API_KEY) and PropLine key (PROPLINE_API_KEY), run by .github/workflows/sgo-probe.yml:
  1. SGO's main lines read twice, GAP seconds apart: for each (game, oddID, book), did the price change, did the stamp move? A stamp that moves while the price holds = "last read".
  2. SGO's DraftKings / FanDuel / Fanatics prices against PropLine's copy of the same line (those three books reach PropLine by push, seconds behind the book): an SGO price that equals the
     push-fed price although its stamp is minutes old = the price is current and the stamp is "last changed".
  python3 tools/sgo_stamps.py run OUTDIR [NFL,NCAAF] [GAP]     python3 tools/sgo_stamps.py selftest
"""
import calendar
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

SGO = "https://api.sportsgameodds.com/v2"
PL = "https://api.prop-line.com/v1"
MAIN = ["points-home-game-ml-home", "points-away-game-ml-away", "points-home-game-sp-home", "points-away-game-sp-away", "points-all-game-ou-over", "points-all-game-ou-under"]
PUSH = {"draftkings", "fanduel", "fanatics"}
PL_SPORT = {"NFL": "americanfootball_nfl", "NCAAF": "americanfootball_ncaaf", "NBA": "basketball_nba", "NHL": "icehockey_nhl"}


def get(url, headers):
    # SGO's Cloudflare refuses Python's default User-Agent (error 1010, 2026-10-25); the engine's OkHttp is let through.
    req = urllib.request.Request(url, headers=dict(headers, Accept="application/json", **{"User-Agent": "okhttp/4.12.0"}))
    try:
        with urllib.request.urlopen(req, timeout=90) as r:
            return r.status, json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        return e.code, (e.read() or b"")[:300].decode("utf-8", "replace")


def ms(t):
    if not t:
        return None
    if isinstance(t, (int, float)):
        return float(t)
    try:
        return calendar.timegm(time.strptime(t[:19], "%Y-%m-%dT%H:%M:%S")) * 1000.0
    except ValueError:
        return None


def sgo_board(key, league):
    """{(eventID, oddID, book): (odds, line, stampMs)} for the league's main lines, plus {eventID: (home, away)}."""
    out, teams, cursor = {}, {}, None
    for _ in range(10):
        q = {"leagueID": league, "oddsAvailable": "true", "oddID": ",".join(MAIN), "limit": "50"}
        if cursor:
            q["cursor"] = cursor
        st, body = get(SGO + "/events?" + urllib.parse.urlencode(q), {"x-api-key": key})
        if st != 200 or not isinstance(body, dict):
            print("SGO", league, "HTTP", st, str(body)[:200])
            break
        for ev in body.get("data") or []:
            eid = ev.get("eventID")
            t = ev.get("teams") or {}
            teams[eid] = tuple(((t.get(s) or {}).get("names") or {}).get("long") or "" for s in ("home", "away"))
            for oid, o in (ev.get("odds") or {}).items():
                for book, b in (o.get("byBookmaker") or {}).items():
                    if b.get("available") is False:
                        continue
                    out[(eid, oid, book)] = (b.get("odds"), b.get("spread") or b.get("overUnder"), ms(b.get("lastUpdatedAt")))
        cursor = body.get("nextCursor")
        if not cursor:
            break
    return out, teams


def compare_reads(a, b, gap_s):
    """Read 1 vs read 2 of the same quotes: how often the stamp moved with and without a price change."""
    r = {"both": 0, "price_same_stamp_same": 0, "price_same_stamp_moved": 0, "price_changed_stamp_moved": 0, "price_changed_stamp_same": 0}
    for k, (o1, l1, s1) in a.items():
        if k not in b or s1 is None or b[k][2] is None:
            continue
        o2, l2, s2 = b[k]
        r["both"] += 1
        same = (o1, l1) == (o2, l2)
        moved = s2 > s1
        r[("price_same" if same else "price_changed") + ("_stamp_moved" if moved else "_stamp_same")] += 1
    r["gap_s"] = gap_s
    return r


def norm(s):
    return "".join(ch for ch in (s or "").lower() if ch.isalnum())


def american(p):
    try:
        return int(float(p))
    except (TypeError, ValueError):
        return None


def against_push(sgo, teams, pl_events, now_ms):
    """SGO's push-book prices vs PropLine's: ages of SGO's stamp where the two prices agree / disagree (PropLine's quote must be under 2 min old by its last_seen_at)."""
    index = {}
    for ev in pl_events or []:
        g = (norm(ev.get("home_team")), norm(ev.get("away_team")))
        for bk in ev.get("bookmakers") or []:
            if bk.get("key") not in PUSH:
                continue
            for m in bk.get("markets") or []:
                for oc in m.get("outcomes") or []:
                    seen = ms(oc.get("last_seen_at"))
                    if seen is None or now_ms - seen > 120_000:
                        continue
                    side = "home" if norm(oc.get("name")) == g[0] else "away" if norm(oc.get("name")) == g[1] else norm(oc.get("name"))
                    index[(g, bk["key"], m.get("key"), side, oc.get("point"))] = american(oc.get("price"))
    kinds = {"ml": "h2h", "sp": "spreads", "ou": "totals"}
    agree, differ = [], []
    for (eid, oid, book), (odds, line, stamp) in sgo.items():
        if book not in PUSH or stamp is None:
            continue
        g = tuple(norm(x) for x in teams.get(eid, ("", "")))
        parts = oid.split("-")
        side = parts[-1]
        side = {"over": "over", "under": "under"}.get(side, side)
        pt = None
        if line not in (None, ""):
            try:
                pt = float(line)
            except ValueError:
                pt = None
        key = (g, book, kinds.get(parts[3]), side, pt)
        if key not in index:
            continue
        age = (now_ms - stamp) / 1000.0
        (agree if index[key] == american(odds) else differ).append(age)
    return agree, differ


def summary(v):
    v = sorted(v)
    if not v:
        return "n=0"
    q = lambda p: v[min(len(v) - 1, int(p * len(v)))]
    return "n=%d median %ds p90 %ds; %d%% older than 5 min" % (len(v), q(0.5), q(0.9), 100 * sum(1 for x in v if x > 300) // len(v))


def cmd_run(outdir, leagues=("NFL", "NCAAF"), gap=120):
    sk = (os.environ.get("SGO_API_KEY") or "").split(",")[0].strip()
    pk = [k.strip() for k in (os.environ.get("PROPLINE_API_KEY") or "").split(",") if k.strip()]
    if not sk:
        print("no SGO key")
        return 0
    os.makedirs(outdir, exist_ok=True)
    L = ["# SportsGameOdds lastUpdatedAt (PX9h, %s)" % time.strftime("%Y-%m-%d %H:%M UTC", time.gmtime()), ""]
    for lg in leagues:
        a, teams = sgo_board(sk, lg)
        t1 = time.time()
        time.sleep(gap)
        b, teams2 = sgo_board(sk, lg)
        teams.update(teams2)
        now = time.time() * 1000
        r = compare_reads(a, b, round(time.time() - t1))
        ages = [(now - s) / 1000.0 for (_, _, s) in b.values() if s]
        L.append("## %s: %d quotes read twice %ds apart" % (lg, r["both"], r["gap_s"]))
        L.append("- price same, stamp same: %d · price same, stamp MOVED: %d · price changed, stamp moved: %d · price changed, stamp same: %d" % (
            r["price_same_stamp_same"], r["price_same_stamp_moved"], r["price_changed_stamp_moved"], r["price_changed_stamp_same"]))
        L.append("- stamp ages at read 2: " + summary(ages))
        if pk and lg in PL_SPORT:
            best = None
            for k in pk:   # the paid key first: PropLine's plan is its own X-Daily-Limit, read by any call
                st, body = get(PL + "/sports/%s/odds?" % PL_SPORT[lg] + urllib.parse.urlencode({"markets": "h2h,spreads,totals", "bookmakers": ",".join(sorted(PUSH))}), {"X-API-Key": k})
                if st == 200:
                    best = body
                    break
            ag, df = against_push(b, teams, best if isinstance(best, list) else (best or {}).get("data") if isinstance(best, dict) else None, time.time() * 1000)
            L.append("- SGO push-book prices EQUAL to PropLine's (<2 min) copy: SGO stamp age " + summary(ag))
            L.append("- SGO push-book prices DIFFERENT from PropLine's copy: SGO stamp age " + summary(df))
        L.append("")
    L.append("Reading: many 'price same, stamp MOVED' = the stamp is SGO's last read (ages true). None, with old-stamped SGO prices equal to PropLine's push copy = the stamp is the last price CHANGE (ages overstated).")
    with open(os.path.join(outdir, "sgo_stamps.md"), "w") as f:
        f.write("\n".join(L) + "\n")
    print("\n".join(L))
    return 0


def selftest():
    a = {("e", "o", "dk"): ("-110", None, 1000.0), ("e", "o", "fd"): ("-110", None, 1000.0), ("e", "o2", "dk"): ("+100", "3.5", 1000.0)}
    b = {("e", "o", "dk"): ("-110", None, 1000.0), ("e", "o", "fd"): ("-110", None, 5000.0), ("e", "o2", "dk"): ("+105", "3.5", 6000.0)}
    r = compare_reads(a, b, 120)
    assert (r["both"], r["price_same_stamp_same"], r["price_same_stamp_moved"], r["price_changed_stamp_moved"]) == (3, 1, 1, 1), r
    now = 10_000_000.0
    sgo = {("e1", "points-home-game-ml-home", "draftkings"): ("-150", None, now - 600_000), ("e1", "points-all-game-ou-over", "fanduel"): ("-110", "47.5", now - 60_000)}
    teams = {"e1": ("Dallas Cowboys", "Baltimore Ravens")}
    iso = lambda m: time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(m / 1000))
    pl = [{"home_team": "Dallas Cowboys", "away_team": "Baltimore Ravens", "bookmakers": [
        {"key": "draftkings", "markets": [{"key": "h2h", "outcomes": [{"name": "Dallas Cowboys", "price": -150, "point": None, "last_seen_at": iso(now - 10_000)}]}]},
        {"key": "fanduel", "markets": [{"key": "totals", "outcomes": [{"name": "Over", "price": -115, "point": 47.5, "last_seen_at": iso(now - 10_000)}]}]}]}]
    ag, df = against_push(sgo, teams, pl, now)
    assert len(ag) == 1 and 590 < ag[0] < 610 and len(df) == 1, (ag, df)
    print("sgo_stamps selftest ok")
    return 0


if __name__ == "__main__":
    a = sys.argv[1:] or ["selftest"]
    if a[0] == "selftest":
        sys.exit(selftest())
    if a[0] == "run":
        sys.exit(cmd_run(a[1] if len(a) > 1 else "out/sgo-stamps", tuple(x for x in a[2].split(",") if x) if len(a) > 2 and a[2] else ("NFL", "NCAAF"), int(a[3]) if len(a) > 3 and a[3] else 120))
    print(__doc__)
