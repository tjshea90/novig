#!/usr/bin/env python3
"""What the legs of Novig's RFQ parlays are (MK3, Tj 2026-11-18; research/novig_rfq_maker_2026-11-18.md).

  python3 -I tools/rfq_legs.py catalog OUT.json.gz      read Novig's PUBLIC catalog (events + open markets, no key) into outcomeId -> {league, marketType, strike, live, startsTs}
  python3 -I tools/rfq_legs.py legs CATALOG.json.gz FILES...   FILES: `rfq-<day>*.jsonl(.gz)` journals (Releases `cloud-archive-YYYY-MM-DD`), best the unsampled first run

Answers what `rfq_summary.py` cannot: of the legs Novig is asked to price, which league and market type they are, how many are in play or already started, how many are priced by the engine, and what
the TRADED parlays look like (legs, stake). That decides where the engine's fair coverage has to grow first. Nothing here sends anything to Novig but public GETs (one every 0.3 s).
"""
import collections, glob, gzip, json, sys, time, urllib.request

BASE = "https://api.novig.com/v3/public/catalog"


def get(path, **q):
    url = BASE + path + "?" + "&".join("%s=%s" % (k, v) for k, v in q.items())
    for attempt in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "research-client"}), timeout=60) as r:
                return json.load(r)
        except Exception as e:                       # 429 / reset: wait and retry
            time.sleep(2 + attempt * 3)
            last = e
    raise last


def pages(path, **q):
    after = None
    while True:
        d = get(path, **dict(q, **({"after": after} if after else {})))
        for it in d.get("items", []):
            yield it
        after = d.get("next")
        if not after:
            return
        time.sleep(0.3)


def build_catalog(out):
    events = {}
    for st in ("OPEN_PREGAME", "OPEN_INGAME", "CLOSED_PREGAME"):
        for e in pages("/events", status=st, limit=5000):
            events[e["eventId"]] = {"league": e.get("league"), "sport": e.get("sport"), "status": e.get("status"), "startsTs": e.get("startsTs"), "desc": e.get("description")}
    print("events", len(events), file=sys.stderr)
    outcomes, n = {}, 0
    for st in ("OPEN_PREGAME", "OPEN_INGAME"):
        for m in pages("/markets", eventStatus=st, limit=5000):
            n += 1
            ev = events.get(m.get("eventId"), {})
            for o in m.get("outcomes", []):
                outcomes[o["outcomeId"]] = [ev.get("league"), m.get("marketType"), m.get("strike"), ev.get("status"), m.get("startsTs"), m.get("eventId"), o.get("name")]
    print("markets", n, "outcomes", len(outcomes), file=sys.stderr)
    with gzip.open(out, "wt") as f:
        json.dump({"at": int(time.time() * 1000), "events": events, "outcomes": outcomes}, f)


def rows(files):
    for pat in files:
        for fn in sorted(glob.glob(pat)):
            op = gzip.open if fn.endswith(".gz") else open
            with op(fn, "rt") as f:
                for line in f:
                    try:
                        r = json.loads(line)
                        r["d"] = json.loads(r.get("data") or "{}")
                        yield r
                    except ValueError:
                        continue


def describe(cat, oid):
    o = cat["outcomes"].get(oid)
    if not o:
        return ("(not in the open catalog)", "-", "gone")
    league, mtype, strike, status, starts, ev, name = o
    return (league or "?", mtype or "?", "live" if status == "OPEN_INGAME" else "pregame")


def pct(xs, q):
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(q * len(xs)))] if xs else 0.0


def legs_report(cat, files):
    created, executed = {}, []
    for r in rows(files):
        if r.get("kind") == "CREATED":
            created[r["rfqId"]] = r["d"]
        elif r.get("kind") == "EXECUTED":
            executed.append(r)
    by_kind, by_state, by_league, rfq_in_play = collections.Counter(), collections.Counter(), collections.Counter(), 0
    rfq_all_known, rfq_pre_only, priceable_by_scan = 0, 0, 0
    for rid, d in created.items():
        ids = d.get("outcomeIds", [])
        ds = [describe(cat, i) for i in ids]
        for lg, mt, st in ds:
            by_kind[mt] += 1; by_state[st] += 1; by_league[lg] += 1
        if any(st == "live" for _, _, st in ds):
            rfq_in_play += 1
        if all(st == "pregame" for _, _, st in ds):
            rfq_pre_only += 1
        if d.get("decision") == "quote":
            priceable_by_scan += 1
    n = len(created)
    print("RFQs with outcome ids: %d (journal rows; a sampled journal holds a sample)" % n)
    print("  every leg pregame and in the catalog: %d (%.0f%%); at least one leg in play: %d (%.0f%%); priced by the engine: %d" % (rfq_pre_only, 100.0 * rfq_pre_only / max(n, 1), rfq_in_play, 100.0 * rfq_in_play / max(n, 1), priceable_by_scan))
    print("legs by state:", dict(by_state.most_common()))
    print("legs by league:", ", ".join("%s %d" % kv for kv in by_league.most_common(14)))
    print("legs by market type:", ", ".join("%s %d" % kv for kv in by_kind.most_common(20)))
    # Of the RFQs with 2-3 legs that are all pregame, what stops the engine pricing them?
    why = collections.Counter(); both = collections.Counter()
    for rid, d in created.items():
        ids = d.get("outcomeIds", [])
        if not 2 <= len(ids) <= 3:
            continue
        ds = [describe(cat, i) for i in ids]
        if any(st != "pregame" for _, _, st in ds):
            continue
        if d.get("decision") == "quote":
            continue
        reason = (d.get("reason") or "?")
        if "no fair line" in reason:
            for lg, mt, st in ds:
                both[(lg, mt)] += 1
        why[reason[:50]] += 1
    print("\n2-3 leg all-pregame RFQs the engine did not quote, by reason:", dict(why.most_common(6)))
    print("legs of the 'no fair line' ones, by (league, market type) -- the coverage to build first:")
    for (lg, mt), c in both.most_common(15):
        print("   %-14s %-22s %d" % (lg, mt, c))
    # the trades
    seen = [e for e in executed if e["rfqId"] in created]
    legs_n = collections.Counter(len(created[e["rfqId"]].get("outcomeIds", [])) for e in seen)
    stakes = [e["d"].get("wager", 0) for e in executed]
    print("\ntrades: %d (%d matched to a journaled RFQ); stake median $%.0f p25 $%.0f p75 $%.0f p90 $%.0f; legs of the matched: %s" % (len(executed), len(seen), pct(stakes, .5), pct(stakes, .25), pct(stakes, .75), pct(stakes, .9), dict(sorted(legs_n.items()))))
    tk = collections.Counter()
    for e in seen:
        for i in created[e["rfqId"]].get("outcomeIds", []):
            lg, mt, st = describe(cat, i); tk[(lg, mt)] += 1
    print("legs of TRADED parlays by (league, market type):", ", ".join("%s/%s %d" % (a[0], a[1], c) for a, c in tk.most_common(14)))
    priced = [e for e in executed if e["d"].get("fair") is not None]
    if priced:
        two = [e for e in priced if len(created.get(e["rfqId"], {}).get("outcomeIds", [])) == 2]
        print("priced trades %d (2-leg %d); implied parlay price median %.3f; stake median $%.0f" % (len(priced), len(two), pct([e["d"]["price"] for e in priced], .5), pct([e["d"]["wager"] for e in priced], .5)))


def selftest():
    cat = {"outcomes": {"a": ["NBA", "MONEY", None, "OPEN_PREGAME", 1, "e1", "X"], "b": ["NBA", "POINTS", "22.5", "OPEN_INGAME", 1, "e2", "Over"]}}
    assert describe(cat, "a") == ("NBA", "MONEY", "pregame"); assert describe(cat, "b")[2] == "live"; assert describe(cat, "zz")[2] == "gone"
    assert pct([1, 2, 3, 4], .5) == 3
    print("selftest ok")


if __name__ == "__main__":
    a = sys.argv[1:]
    if a[:1] == ["catalog"] and len(a) == 2:
        build_catalog(a[1])
    elif a[:1] == ["legs"] and len(a) >= 3:
        with gzip.open(a[1], "rt") as f:
            legs_report(json.load(f), a[2:])
    elif a[:1] == ["selftest"]:
        selftest()
    else:
        print(__doc__); sys.exit(2)
