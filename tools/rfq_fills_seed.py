#!/usr/bin/env python3
"""One-off: the legs of the parlays the maker filled BEFORE the engine kept them (MK15, the parlay hub).

  python3 -I tools/rfq_fills_seed.py OUT.json CATALOG.json.gz[,CATALOG2...] JOURNAL.jsonl.gz [...]

Reads the `QUOTE_EXECUTED` rows of the `rfq-<day>` journals, joins each to the whole `CREATED` row (legs, fair, price), names each leg's event and market from Novig's PUBLIC catalog (one market list per event,
no key) and writes the engine's `FillRecord` list (the same JSON as `rfq-fills.json`), which ships as `data/src/main/resources/rfq_fills_seed.json` so the hub shows them and can read their results.
"""
import gzip, json, os, sys, time, urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rfq_outcomes as ro

BASE = "https://api.novig.com/v3/public/catalog"


def get(path, **q):
    url = BASE + path + "?" + "&".join("%s=%s" % kv for kv in q.items())
    for a in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "research-client"}), timeout=60) as r:
                return json.load(r)
        except Exception:
            time.sleep(2 + 3 * a)
    return {}


def rows(files):
    for fn in files:
        try:
            with gzip.open(fn, "rt") as f:
                for line in f:
                    try:
                        r = json.loads(line); r["d"] = json.loads(r.get("data") or "{}"); yield r
                    except ValueError:
                        pass
        except EOFError:
            pass


def main(out, cats, files):
    cat = {"outcomes": {}, "events": {}}
    for p in cats.split(","):
        c = json.load(gzip.open(p, "rt"))
        for k in cat:
            for kk, v in c[k].items():
                cat[k].setdefault(kk, v)
    created, fills = {}, {}
    for r in rows(files):
        if r["kind"] == "CREATED" and r["d"].get("decision") == "quote":
            created[r["rfqId"]] = (r["atMs"], r["d"])
        elif r["kind"] == "QUOTE_EXECUTED":
            fills.setdefault(r["rfqId"], r)
    market_of, events = {}, set()
    for rid in fills:
        if rid in created:
            for oid in created[rid][1]["outcomeIds"]:
                o = cat["outcomes"].get(oid)
                if o:
                    events.add(o[5])
    for eid in sorted(events):
        d = get("/markets", event=eid, marketType="MONEY,SPREAD,TOTAL,TEAM_TOTAL", limit=500)     # the parlay legs are game lines; an NFL game has thousands of prop markets
        for m in d.get("items", []):
            for o in m.get("outcomes", []):
                market_of[o["outcomeId"]] = m["marketId"]
        time.sleep(0.4)
    recs, espn = [], {}
    for rid, r in sorted(fills.items(), key=lambda kv: kv[1]["atMs"]):
        if rid not in created:
            continue
        at, c = created[rid]
        legs = []
        for i, oid in enumerate(c["outcomeIds"]):
            o = cat["outcomes"].get(oid) or [None] * 7
            lj = c["legs"][i] if len(c.get("legs", [])) == len(c["outcomeIds"]) else {}
            ev = cat["events"].get(o[5]) or {}
            leg = {"outcomeId": oid, "marketId": market_of.get(oid, ""), "label": lj.get("label", ""), "desc": ev.get("desc", ""), "league": o[0] or "", "type": o[1] or "",
                   "startsMs": lj.get("startsMs", 0), "p": lj.get("p", 0.0)}
            # a game that is over and a game line: the buyer's result from ESPN's final score (the market has left Novig's catalog by now)
            if leg["startsMs"] and leg["startsMs"] < time.time() * 1000 - 4 * 3600_000 and o[0] in ro.SPORT and o[1] in ("MONEY", "SPREAD", "TOTAL"):
                g = ro.match_game(ev.get("desc"), leg["startsMs"], ro.games_for(o[0], [leg["startsMs"]], espn))
                sib = [x[6] for x in cat["outcomes"].values() if x[5] == o[5] and x[1] == o[1] and x[2] == o[2]]
                res = ro.grade_leg(o[1], o[2], o[6], sib, g) if g else None
                if res:
                    leg["result"] = res.upper()
            legs.append(leg)
        recs.append({"rfqId": rid, "quotedMs": r["atMs"], "price": r["d"]["price"], "fair": c["fair"], "wager": r["d"]["wager"], "legs": legs})
    json.dump(recs, open(out, "w"), indent=1)
    print(len(recs), "fills;", sum(1 for x in recs for l in x["legs"] if l["marketId"]), "of", sum(len(x["legs"]) for x in recs), "legs with a market id", file=sys.stderr)


if __name__ == "__main__":
    if len(sys.argv) < 4:
        print(__doc__); sys.exit(0)
    main(sys.argv[1], sys.argv[2], sys.argv[3:])
