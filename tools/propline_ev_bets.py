#!/usr/bin/env python3
"""Does PropLine's +EV scanner pick profitable bets? (TCL75, Tj 2026-10-17: "track the supposed propline +ev bets ... save the bets ... confirm the novig price is still available, the listed ev from propline, and whether the
bet won or lost (the grading which propline offers) ... after several hundred of these bets saved, automatically report back to me with the statistics").

  python3 tools/propline_ev_bets.py run --journals propline-ev-*.jsonl.gz --archive resolved-*.csv.gz --out evbets.csv.gz --summary evbets.md [--state reported.json --notify]
  python3 tools/propline_ev_bets.py selftest

--journals: the engine's `propline-ev-<day>-run<id>.jsonl.gz` (Releases cloud-archive-*): every sweep's PlEvRow, one per line (a line is journaled again each sweep it is found in).
--archive: PropLine's resolved-prop exports (Releases propline-archive-*): the grade (`resolution`), Pinnacle's and Novig's closes.
One row per UNIQUE bet (event, market, player, point, side): the first sighting is "the bet" (PropLine's Novig price, listed EV, fair, the other books' prices, books agreeing, Vigilant's own EV for it), plus how many
sweeps saw it, its best listed EV, whether the engine's own Novig read still had the price (novigNow*), and, once its game is graded, `won/lost/push`, `roi` (at the listed price, pregame: no fee), `clv_pinn`.
With --notify and --state: when the graded count first reaches 300, 600 and 1000, a message (app notification + chat line) with the statistics goes to Tj (tools/tell_tj.py). Reads files only; never a key.
"""
import argparse, csv, datetime, gzip, io, json, os, re, sys
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import propline_clv as clv  # noqa: E402

THRESHOLDS = (300, 600, 1000)
LEAGUE_FIX = {"NCAA Football": "NCAAF", "College Football": "NCAAF"}
# PropLine market key -> a Novig-style market title that propline_clv.parse_bet maps back to the same key
KEY_TITLE = {}
for _title, _key in clv.STAT_KEYS.items():
    KEY_TITLE.setdefault(_key, _title)


def cost_of(american):
    return clv.implied(american) if american not in (None, "") else None


def read_journals(paths):
    for p in paths:
        op = gzip.open if p.endswith(".gz") else open
        with op(p, "rt") as f:
            for line in f:
                line = line.strip()
                if line.startswith("{"):
                    try:
                        yield json.loads(line)
                    except ValueError:
                        pass


def bet_key(r):
    return (r.get("eventId"), r.get("market"), (r.get("player") or ""), r.get("point"), (r.get("outcome") or "").lower())


def unique_bets(rows):
    """Sightings -> one record per bet. The first sighting with a positive listed EV is the bet; later ones only update counts and the best EV."""
    bets = {}
    for r in sorted(rows, key=lambda x: x.get("seenAtMs", 0)):
        k = bet_key(r)
        b = bets.get(k)
        if b is None:
            bets[k] = dict(first=r, sightings=1, best_ev=r.get("evPct", 0.0), last_seen=r.get("seenAtMs", 0), with_novig_now=1 if r.get("novigNowPrice") is not None else 0)
        else:
            b["sightings"] += 1
            b["best_ev"] = max(b["best_ev"], r.get("evPct", 0.0))
            b["last_seen"] = r.get("seenAtMs", 0)
            b["with_novig_now"] += 1 if r.get("novigNowPrice") is not None else 0
            if b["first"].get("novigNowPrice") is None and r.get("novigNowPrice") is not None:
                b["novig_now_later"] = r
    return list(bets.values())


def to_line_row(r):
    """A PlEvRow as the engine's lines.csv row that propline_clv.grade_bet reads."""
    market, side, point = r.get("market", ""), r.get("outcome", ""), r.get("point")
    player = r.get("player") or ""
    league = LEAGUE_FIX.get(r.get("league", ""), r.get("league", ""))
    if player:
        title = KEY_TITLE.get(market)
        row = {"kind": "PROP", "market": title or market, "selection": "%s %s %s" % (player, side, point)}
    elif market == "h2h":
        row = {"kind": "MONEYLINE", "market": "Moneyline", "selection": side}
    elif market == "spreads":
        row = {"kind": "SPREAD", "market": "Spread", "selection": "%s %s" % (side, ("%+g" % point) if point is not None else "")}
    elif market == "totals":
        row = {"kind": "TOTAL", "market": "Total", "selection": "%s %s" % (side, point)}
    else:
        row = {"kind": "OTHER", "market": market, "selection": side}
    row.update({"league": league, "event": "%s @ %s" % (r.get("away", ""), r.get("home", "")),
                "starts_utc": datetime.datetime.fromtimestamp(r.get("startsAtMs", 0) / 1000, datetime.timezone.utc).strftime("%Y-%m-%d %H:%M:%S"),
                "cost": cost_of(r.get("price"))})
    return row


def result_of(resolution):
    t = (resolution or "").lower()
    if "win" in t or t in ("won", "w"):
        return "won"
    if "los" in t or t in ("lost", "l"):
        return "lost"
    if "push" in t or "void" in t or "cancel" in t or "tie" in t:
        return "push"
    return ""


def grade(arch, bets):
    out = []
    for b in bets:
        r = b["first"]
        row = to_line_row(r)
        g = clv.grade_bet(arch, row)
        cost = cost_of(r.get("price"))
        res = result_of(g.get("pl_resolution"))
        roi = ""
        if res and cost:
            roi = (1.0 / cost - 1.0) if res == "won" else (-1.0 if res == "lost" else 0.0)
        now_price = r.get("novigNowPrice")
        out.append({
            "first_seen_utc": datetime.datetime.fromtimestamp(r.get("seenAtMs", 0) / 1000, datetime.timezone.utc).strftime("%Y-%m-%d %H:%M"),
            "league": r.get("league"), "market": r.get("market"), "player": r.get("player") or "", "point": r.get("point"), "side": r.get("outcome"),
            "event": "%s @ %s" % (r.get("away"), r.get("home")), "starts_utc": row["starts_utc"],
            "kind": "prop" if r.get("player") else "game line",
            "pl_price": r.get("price"), "pl_cost": round(cost, 5) if cost else "", "pl_ev_pct": r.get("evPct"), "pl_best_ev_pct": b["best_ev"], "pl_fair": r.get("fairProb"), "fair_source": r.get("fairSource"),
            "pl_price_age_min": round((r.get("seenAtMs", 0) - r.get("updatedAtMs", 0)) / 60000, 1) if r.get("updatedAtMs") else "",
            "two_sided_books": r.get("twoSided"), "agree_books": r.get("agree"), "pinnacle_agrees": r.get("pinnacleAgrees"),
            "vigilant_ev": r.get("vigilantEv"), "vigilant_stars": r.get("vigilantStars"), "vigilant_fair": r.get("vigilantFair"),
            "novig_now_price": now_price, "novig_now_age_s": round((r.get("seenAtMs", 0) - r["novigNowAtMs"]) / 1000) if r.get("novigNowAtMs") else "",
            "novig_now_depth": r.get("novigNowDepth"), "price_still_there": ("" if now_price is None else int(abs(now_price - r["price"]) <= 5 or (now_price >= r["price"]))),
            "sightings": b["sightings"], "sightings_with_novig_read": b["with_novig_now"], "trend": r.get("trend") or "",
            "result": res, "roi": roi if roi == "" else round(roi, 5), "pl_status": g.get("pl_status"), "pl_event_id": g.get("pl_event_id"), "pl_resolution": g.get("pl_resolution"),
            "pl_actual": g.get("pl_actual"), "clv_pinn": g.get("clv_pinn"), "pl_close_fair": g.get("pl_close_fair"), "beat_novig_close": g.get("beat_novig_close"),
        })
    return out


def stat(name, rs):
    gr = [x for x in rs if x["result"] in ("won", "lost", "push")]
    n = len(gr)
    if n == 0:
        return "| %s | %d | 0 | | | | |" % (name, len(rs))
    rets = [float(x["roi"]) for x in gr]
    mean = sum(rets) / n
    sd = (sum((v - mean) ** 2 for v in rets) / max(n - 1, 1)) ** 0.5
    z = mean / (sd / n ** 0.5) if sd > 0 and n > 1 else 0.0
    won = sum(1 for x in gr if x["result"] == "won")
    cv = [float(x["clv_pinn"]) for x in gr if x.get("clv_pinn") not in ("", None)]
    ev = [float(x["pl_ev_pct"]) for x in gr if x.get("pl_ev_pct") is not None]
    return "| %s | %d | %d | %.1f%% | %+.1f%% | %+.2f | %+.1f%% | %s |" % (
        name, len(rs), n, 100.0 * won / n, 100 * mean, z, sum(ev) / len(ev) if ev else 0.0, ("%+.1f%% (%d)" % (100 * sum(cv) / len(cv), len(cv))) if cv else "")


def summarize(rows):
    L = ["# PropLine +EV bets: graded results (staked at PropLine's listed Novig price; pregame, no fee)", "",
         "| group | bets | graded | win % | ROI | z | avg listed EV | CLV vs Pinnacle (n) |", "|:--|--:|--:|--:|--:|--:|--:|:--|"]
    L.append(stat("ALL", rows))
    def split(col, fn=lambda x: x):
        g = defaultdict(list)
        for r in rows:
            g[fn(r.get(col))].append(r)
        for k in sorted(g, key=lambda k: str(k)):
            L.append(stat("%s=%s" % (col, k), g[k]))
    split("kind"); split("league"); split("side")
    def evband(v):
        v = float(v or 0)
        return "<3%" if v < 3 else "3-5%" if v < 5 else "5-8%" if v < 8 else "8%+"
    split("pl_ev_pct", evband)
    split("pinnacle_agrees", lambda v: "yes" if v is True else "no" if v is False else "n/a")
    split("price_still_there", lambda v: "n/a (not in the engine's scan)" if v in ("", None) else ("yes" if v in (1, "1") else "no"))
    def agree(v):
        return "n/a"
    sr = defaultdict(int)
    for r in rows:
        sr[(r.get("pl_status") or "")[:40]] += 1
    L += ["", "Match status: " + "; ".join("%s %d" % (k, v) for k, v in sorted(sr.items(), key=lambda kv: -kv[1])[:8]),
          "Staking: each graded bet is $1 at PropLine's listed Novig price; CLV is Pinnacle's devigged close / that price - 1."]
    return "\n".join(L) + "\n"


def graded(rows):
    return [r for r in rows if r["result"] in ("won", "lost", "push")]


def notify(rows, summary, state_path):
    """The statistics to Tj when the graded count first reaches a threshold (300, 600, 1000); the state file remembers which were sent."""
    n = len(graded(rows))
    try:
        sent = json.load(open(state_path))
    except Exception:
        sent = {"sent": []}
    due = [t for t in THRESHOLDS if n >= t and t not in sent["sent"]]
    if not due:
        print("graded %d: nothing to report (thresholds %s, sent %s)" % (n, THRESHOLDS, sent["sent"]))
        return False
    top = max(due)
    gr = graded(rows)
    rets = [float(x["roi"]) for x in gr]
    mean = sum(rets) / len(rets)
    sd = (sum((v - mean) ** 2 for v in rets) / max(len(rets) - 1, 1)) ** 0.5
    z = mean / (sd / len(rets) ** 0.5) if sd > 0 else 0.0
    verdict = "PROFITABLE" if mean > 0 and z >= 2 else "LOSING" if mean < 0 and z <= -2 else "not yet clear either way"
    title = "PropLine +EV bets after %d graded: %+.1f%% ROI (%s)" % (len(gr), 100 * mean, verdict)
    body = summary.split("\n\n", 1)[0] + "\n" + "\n".join(summary.split("\n")[3:12]) + "\nFull tables: Release propline-ev-bets (evbets.md, evbets.csv.gz). Verdict rule: |z| >= 2."
    sys.path.insert(0, HERE)
    import tell_tj
    # Messages only (CR, Tj 2026-10-19: the chat is a conversation; GitHub's reports are not posted into it any more)
    tell_tj.say(title, body[:1800], kind="info", priority=1, who="claude:github")
    sent["sent"] = sorted(set(sent["sent"]) | set(due))
    json.dump(sent, open(state_path, "w"))
    print("reported at", top)
    return True


def run(journals, archives, out, summary, state=None, do_notify=False):
    arch = clv.Archive()
    arch.load(archives)
    rows = list(read_journals(journals))
    print("journal rows %d; archive events %d" % (len(rows), len(arch.events)))
    bets = unique_bets(rows)
    res = grade(arch, bets)
    if not res:
        print("no bets yet")
        return 0
    with gzip.open(out, "wt", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(res[0].keys()), extrasaction="ignore")
        w.writeheader()
        w.writerows(res)
    text = summarize(res)
    if summary:
        open(summary, "w").write(text)
    print(text)
    if do_notify and state:
        notify(res, text, state)
    return 0


def selftest():
    r1 = {"eventId": "e1", "market": "player_points", "player": "A B", "point": 20.5, "outcome": "Over", "price": 110, "evPct": 4.0, "fairProb": 0.5, "seenAtMs": 1000, "updatedAtMs": 400, "league": "NBA",
          "home": "Boston Celtics", "away": "New York Knicks", "startsAtMs": 2_000_000_000_000, "novigNowPrice": 105}
    r2 = dict(r1, seenAtMs=2000, evPct=6.0, novigNowPrice=None)
    bets = unique_bets([r2, r1])
    assert len(bets) == 1 and bets[0]["sightings"] == 2 and bets[0]["best_ev"] == 6.0 and bets[0]["first"]["seenAtMs"] == 1000
    row = to_line_row(r1)
    assert row["kind"] == "PROP" and clv.parse_bet(row)[0][0] == "player_points", row
    assert to_line_row(dict(r1, player="", market="totals"))["kind"] == "TOTAL"
    assert result_of("win") == "won" and result_of("Loss") == "lost" and result_of("push") == "push" and result_of("") == ""
    rows = [dict(result="won", roi=0.9, clv_pinn="0.02", pl_ev_pct=4.0, kind="prop", league="NBA", side="Over", pinnacle_agrees=True, price_still_there=1, pl_status="ok") for _ in range(3)] + \
           [dict(result="lost", roi=-1.0, clv_pinn="", pl_ev_pct=3.0, kind="game line", league="NHL", side="Under", pinnacle_agrees=None, price_still_there="", pl_status="no event") ]
    text = summarize(rows)
    assert "| ALL | 4 | 4 | 75.0% |" in text, text
    assert len(graded(rows)) == 4
    print("propline_ev_bets selftest ok")
    return 0


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd")
    r = sub.add_parser("run")
    r.add_argument("--journals", nargs="+", required=True); r.add_argument("--archive", nargs="+", required=True)
    r.add_argument("--out", required=True); r.add_argument("--summary"); r.add_argument("--state"); r.add_argument("--notify", action="store_true")
    sub.add_parser("selftest")
    a = ap.parse_args()
    if a.cmd == "selftest":
        sys.exit(selftest())
    if a.cmd == "run":
        sys.exit(run(a.journals, a.archive, a.out, a.summary, a.state, a.notify))
    ap.print_help()
