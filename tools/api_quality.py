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


def summarize(rs):
    by = {}
    for r in rs:
        c = r["card"]
        by.setdefault(c["id"], []).append(c)
    out = []
    for id_, cs in by.items():
        active = [c for c in cs if c.get("role") in ("active", "standby", "failing")]
        asked = [c for c in cs if c.get("answeredPct") is not None]
        o = dict(id=id_, name=cs[-1].get("name", id_), paid=cs[-1].get("paid", False), scans=len(cs),
                 p90_age_s=med([c.get("p90AgeS") for c in cs]), off_pinn=med([c.get("offPinnaclePts") for c in cs]),
                 answered=med([c.get("answeredPct") for c in asked]),
                 fed=med([c.get("fedLines", 0) for c in cs]), sole=med([c.get("soleLines", 0) for c in cs]),
                 off_share=round(100 * (len(cs) - len(active)) / len(cs)))
        if (o["sole"] or 0) > 0:
            o["verdict"] = "KEEP (unique lines)"
        elif o["answered"] is not None and o["answered"] < 50:
            o["verdict"] = "DROP-CANDIDATE (answers under half the time, no unique lines)" if not o["paid"] else "WATCH (paid, answers little)"
        elif (o["fed"] or 0) == 0:
            o["verdict"] = "DROP-CANDIDATE (feeds no lines)" if not o["paid"] else "WATCH (paid, feeds no lines)"
        else:
            o["verdict"] = "KEEP (overlaps; fresh/close)" if (o["off_pinn"] is None or o["off_pinn"] < 1.0) else "WATCH (far from Pinnacle)"
        out.append(o)
    return sorted(out, key=lambda o: -(o["sole"] or 0))


def main(argv):
    if argv[:1] == ["selftest"]:
        rs = [{"atMs": 1, "card": {"id": "a", "name": "A", "paid": True, "role": "active", "p90AgeS": 30, "answeredPct": 100, "fedLines": 10, "soleLines": 2}},
              {"atMs": 2, "card": {"id": "b", "name": "B", "paid": False, "role": "active", "answeredPct": 20, "fedLines": 0, "soleLines": 0}}]
        s = summarize(rs)
        assert s[0]["verdict"].startswith("KEEP") and s[1]["verdict"].startswith("DROP"), s
        print("selftest ok")
        return 0
    s = summarize(rows(argv))
    print("%-22s %5s %6s %7s %8s %6s %6s  %s" % ("api", "scans", "p90s", "offPin", "answered", "fed", "sole", "verdict"))
    for o in s:
        print("%-22s %5d %6s %7s %8s %6s %6s  %s" % (o["name"][:22], o["scans"], o["p90_age_s"], o["off_pinn"], o["answered"], o["fed"], o["sole"], o["verdict"]))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
