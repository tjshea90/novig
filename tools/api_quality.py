#!/usr/bin/env python3
"""Per-API value table from the engine's apiq-<day>-run<id>.jsonl.gz journals (CH58).

Usage: python3 -I tools/api_quality.py <file-or-dir> [...]   |   python3 -I tools/api_quality.py selftest
Each line is {"atMs":..,"card":{id,name,paid,role,p90AgeS,offPinnaclePts,answeredPct,fedLines,soleLines,...}}.
Verdict: KEEP (unique lines or fresh and close to Pinnacle), WATCH, DROP-CANDIDATE (answers little, no sole lines),
BUY-CANDIDATE is only said for a paid-tier question and needs a human read of the numbers.
"""
import gzip, json, os, statistics, sys


def rows(paths):
    for p in paths:
        fs = [os.path.join(p, f) for f in sorted(os.listdir(p))] if os.path.isdir(p) else [p]
        for f in fs:
            if "apiq" not in os.path.basename(f):
                continue
            op = gzip.open if f.endswith(".gz") else open
            with op(f, "rt") as fh:
                for line in fh:
                    line = line.strip()
                    if line:
                        try:
                            yield json.loads(line)
                        except ValueError:
                            pass


def med(xs):
    xs = [x for x in xs if x is not None]
    return statistics.median(xs) if xs else None


ACTIVE = ("active", "standby", "failing")


def day_of(ms):
    import datetime
    return datetime.datetime.fromtimestamp((ms or 0) / 1000, datetime.timezone.utc).strftime("%Y-%m-%d")


def summarize(rs):
    by = {}
    for r in rs:
        c = r["card"]
        by.setdefault(c["id"], []).append((r.get("atMs"), c))
    out = []
    for id_, pairs in by.items():
        cs = [c for _, c in pairs]
        # Fed and sole lines are judged over the scans the API was ON (a switched-off API feeds nothing by design; FX 2026-10-23: medians over every scan called a part-time API useless).
        active = [c for c in cs if c.get("role") in ACTIVE]
        asked = [c for c in active if c.get("answeredPct") is not None]
        o = dict(id=id_, name=cs[-1].get("name", id_), paid=cs[-1].get("paid", False), scans=len(cs), on=len(active),
                 days=len({day_of(t) for t, _ in pairs}),
                 p90_age_s=med([c.get("p90AgeS") for c in active]), off_pinn=med([c.get("offPinnaclePts") for c in active]),
                 answered=med([c.get("answeredPct") for c in asked]),
                 fed=med([c.get("fedLines", 0) for c in active]), sole=med([c.get("soleLines", 0) for c in active]),
                 failing=round(100 * sum(1 for c in cs if c.get("role") == "failing") / len(cs)),
                 off_share=round(100 * (len(cs) - len(active)) / len(cs)))
        if not active:
            o["verdict"] = "NO DATA (off in every scan)"
        elif (o["sole"] or 0) > 0:
            o["verdict"] = "KEEP (unique lines)"
        elif o["answered"] is not None and o["answered"] < 50:
            o["verdict"] = "DROP-CANDIDATE (answers under half the time, no unique lines)" if not o["paid"] else "WATCH (paid, answers little)"
        elif (o["fed"] or 0) == 0:
            o["verdict"] = "DROP-CANDIDATE (feeds no lines)" if not o["paid"] else "WATCH (paid, feeds no lines)"
        else:
            o["verdict"] = "KEEP (overlaps; fresh/close)" if (o["off_pinn"] is None or o["off_pinn"] < 1.0) else "WATCH (far from Pinnacle)"
        out.append(o)
    return sorted(out, key=lambda o: (-(o["sole"] or 0), -(o["fed"] or 0)))


def by_day(rs):
    """{api name: {day: (scans on, median fed, median sole, median p90 s, failing scans)}}: is a verdict the same every day, or one day's accident?"""
    acc = {}
    for r in rs:
        c = r["card"]
        acc.setdefault(c.get("name", c["id"]), {}).setdefault(day_of(r.get("atMs")), []).append(c)
    out = {}
    for name, days in acc.items():
        out[name] = {}
        for d, cs in sorted(days.items()):
            on = [c for c in cs if c.get("role") in ACTIVE]
            out[name][d] = (len(on), med([c.get("fedLines", 0) for c in on]), med([c.get("soleLines", 0) for c in on]),
                            med([c.get("p90AgeS") for c in on]), sum(1 for c in cs if c.get("role") == "failing"))
    return out


def main(argv):
    if argv[:1] == ["selftest"]:
        rs = [{"atMs": 1, "card": {"id": "a", "name": "A", "paid": True, "role": "active", "p90AgeS": 30, "answeredPct": 100, "fedLines": 10, "soleLines": 2}},
              {"atMs": 2, "card": {"id": "b", "name": "B", "paid": False, "role": "active", "answeredPct": 20, "fedLines": 0, "soleLines": 0}}]
        rs += [{"atMs": 3, "card": {"id": "c", "name": "C", "paid": False, "role": "off", "fedLines": 0, "soleLines": 0}},
               {"atMs": 4, "card": {"id": "c", "name": "C", "paid": False, "role": "off", "fedLines": 0, "soleLines": 0}},
               {"atMs": 5, "card": {"id": "c", "name": "C", "paid": False, "role": "active", "answeredPct": 100, "fedLines": 40, "soleLines": 0}},
               {"atMs": 6, "card": {"id": "d", "name": "D", "paid": True, "role": "off"}}]
        s = summarize(rs)
        v = {o["id"]: o for o in s}
        assert v["a"]["verdict"].startswith("KEEP") and v["b"]["verdict"].startswith("DROP"), s
        # part-time: judged on the one scan it was on (40 lines), not the median of all three (0)
        assert v["c"]["fed"] == 40 and v["c"]["verdict"].startswith("KEEP") and v["c"]["off_share"] == 67, v["c"]
        assert v["d"]["verdict"].startswith("NO DATA"), v["d"]
        assert by_day(rs)["C"]["1970-01-01"][0] == 1
        print("selftest ok")
        return 0
    rs = list(rows(argv))
    s = summarize(rs)
    print("Per-API value over %d scorecards (one per API per finished engine scan). fed/sole/p90/answered are medians over the scans the API was ON; on = scans on, off%% = share of scans off." % len(rs))
    print("%-22s %4s %5s %5s %5s %6s %7s %8s %6s %6s %5s  %s" % ("api", "days", "scans", "on", "off%", "p90s", "offPin", "answered", "fed", "sole", "fail%", "verdict"))
    for o in s:
        print("%-22s %4d %5d %5d %5d %6s %7s %8s %6s %6s %5d  %s" % (o["name"][:22], o["days"], o["scans"], o["on"], o["off_share"], o["p90_age_s"], o["off_pinn"], o["answered"], o["fed"], o["sole"], o["failing"], o["verdict"]))
    print()
    print("By day (scans on / median fed / median sole / median p90 s / failing scans):")
    for name, days in sorted(by_day(rs).items()):
        print("  %-22s %s" % (name[:22], "  ".join("%s %d/%s/%s/%s/%d" % (d[5:], *v) for d, v in days.items())))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
