#!/usr/bin/env python3
"""What the other makers charge, from the engine's market probe (MK12, Tj 2026-11-19; research/novig_parlay_maker_2026-11-19.md).

  python3 -I tools/rfq_market.py run FILES...        files: the engine's `rfq-<day>*.jsonl(.gz)` journals (Releases `cloud-archive-YYYY-MM-DD`)
  python3 -I tools/rfq_market.py selftest

The maker asks Novig's keyless parlay price (`RfqProbe`) for a class-balanced sample of the RFQs it prices; each answer is a `PROBE` row {cls, fair, mkt | NO_PRICE}. A class is `legs|mix|league|band`
(mix: gl game lines, sg same game, prop, psg props in one game). This prints, per class with enough answers: how many nobody quoted, the quartiles of (market price - our fair) in points of pot, the share a
quote that many points over our fair would have beaten (strictly lower wins), and the margin that maximises win share x net edge on the collateral (the engine's `RfqMarketModel.best`, same formula, defaults
fairErrorRel 2% so a 0.7 point floor at two legs and fair 0.25, minReturn 0.4% a day, one day to the games). Then, from EXECUTED rows, how the rule's price and the model's price would each have done on the trades that really happened.
`marketPricing=true` (tools/rfq_set.py) is judged by this table: evidence rule in the research file, section 8. Nothing here sends anything anywhere.
"""
import collections, glob, gzip, json, sys

FAIR_ERROR_REL = 0.02          # RfqConfig.fairErrorRel: the model's floor under a quote is fair x this x sqrt(legs); half of it is the allowance for our fair's error
MIN_RETURN = 0.004
GRID = [0.0025, 0.005, 0.0075, 0.01, 0.0125, 0.015, 0.02, 0.025, 0.03]


def rows(files):
    for pat in files:
        for fn in sorted(glob.glob(pat)):
            op = gzip.open if fn.endswith(".gz") else open
            try:
                with op(fn, "rt") as f:
                    for line in f:
                        if '"kind":"PROBE"' not in line and '"kind":"EXECUTED"' not in line and '"kind": "PROBE"' not in line and '"kind": "EXECUTED"' not in line:
                            continue                                    # millions of CREATED/CLOSED rows are never decoded
                        try:
                            r = json.loads(line)
                            r["d"] = json.loads(r.get("data") or "{}")
                            yield r
                        except ValueError:
                            continue
            except EOFError:                                   # a journal cut off by a dropped download: keep what was read
                continue


def pct(xs, p):
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(len(xs) * p))] if xs else None


def best_margin(gaps, fair, n_legs, lead_days=1.0, rel=FAIR_ERROR_REL, min_return=MIN_RETURN):
    """The margin (probability) maximising win share x net edge, like RfqMarketModel.best; gaps: probabilities or None (nobody quoted)."""
    if not gaps:
        return None, 0.0
    floor = fair * rel * (n_legs ** 0.5)
    allowance = 0.5 * floor
    hold = max(1.0, lead_days + 0.25)
    best, score = None, 0.0
    c = max(floor, 0.0005)
    while c <= 0.06:
        win = sum(1 for g in gaps if g is None or g > c + 1e-9) / len(gaps)
        net = c - allowance - min_return * hold * (1.0 - (fair + c))
        if net > 0 and win * net > score + 1e-12:
            best, score = c, win * net
        c += 0.0005
    return best, score


def probe_table(rs, min_n=30):
    by = collections.defaultdict(list)
    for r in rs:
        if r.get("kind") != "PROBE":
            continue
        d = r["d"]
        st = d.get("st")
        if st == "PRICED" and d.get("mkt") is not None and d.get("fair") is not None:
            by[d["cls"]].append((d["mkt"] - d["fair"], d["fair"], d.get("n", 2)))
        elif st == "NO_PRICE" and d.get("fair") is not None:
            by[d["cls"]].append((None, d["fair"], d.get("n", 2)))
    out = []
    for cls, obs in sorted(by.items(), key=lambda kv: -len(kv[1])):
        if len(obs) < min_n:
            continue
        gaps = [o[0] for o in obs]
        answered = [g * 100 for g in gaps if g is not None]
        fair = sorted(o[1] for o in obs)[len(obs) // 2]
        n_legs = obs[0][2]
        wins = ["%.0f%%" % (100.0 * sum(1 for g in gaps if g is None or g > c + 1e-9) / len(gaps)) for c in GRID]
        b, s = best_margin(gaps, fair, n_legs)
        out.append((cls, len(obs), sum(1 for g in gaps if g is None) / len(obs), pct(answered, .25), pct(answered, .5), pct(answered, .75), wins, b, s))
    return out


def render_probe(tab):
    lines = ["| class | answers | nobody quoted | gap p25 / median / p75 (pts) | share beaten at +" + " / ".join("%g" % (c * 100) for c in GRID) + " pts | best margin (pts) | score |", "|:--|--:|--:|:--|:--|--:|--:|"]
    for cls, n, no, p25, med, p75, wins, b, s in tab:
        f = lambda v: "-" if v is None else "%+.2f" % v
        lines.append("| %s | %d | %.0f%% | %s / %s / %s | %s | %s | %.4f |" % (cls, n, 100 * no, f(p25), f(med), f(p75), " / ".join(wins), "-" if b is None else "%.2f" % (b * 100), s * 100))
    return "\n".join(lines)


def executed_table(rs):
    """On the trades that really happened: how often the rule's price and the model's price would have won, and what each would have earned (points of pot) if our fair is true."""
    n = rw = mw = 0
    re_ = me = 0.0
    for r in rs:
        if r.get("kind") != "EXECUTED":
            continue
        d = r["d"]
        if d.get("rulePrice") is None or d.get("fair") is None:
            continue
        n += 1
        if d.get("ruleWouldWin"):
            rw += 1; re_ += (d["rulePrice"] - d["fair"]) * 100
        if d.get("modelPrice") is not None:
            if d.get("modelWouldWin"):
                mw += 1; me += (d["modelPrice"] - d["fair"]) * 100
    if not n:
        return "no EXECUTED rows with rulePrice yet"
    return "executed trades we price: %d; the rule would have won %d (earning %.2f pts each), the model %d (%.2f pts each)" % (n, rw, re_ / rw if rw else 0.0, mw, me / mw if mw else 0.0)


def selftest():
    rs = []
    for i in range(40):
        rs.append({"kind": "PROBE", "d": {"cls": "2|gl|ATP|mid", "fair": 0.3, "st": "PRICED", "mkt": 0.3 + (0.01 if i % 2 else 0.02), "n": 2}})
    for i in range(35):
        rs.append({"kind": "PROBE", "d": {"cls": "2|gl|NBA|mid", "fair": 0.3, "st": "PRICED", "mkt": 0.3 + 0.002, "n": 2}})
    rs.append({"kind": "PROBE", "d": {"cls": "2|gl|NBA|mid", "fair": 0.3, "st": "NO_PRICE", "n": 2}})
    rs.append({"kind": "PROBE", "d": {"cls": "x", "fair": 0.3, "st": "REFUSED"}})
    tab = probe_table(rs)
    assert [t[0] for t in tab] == ["2|gl|NBA|mid", "2|gl|ATP|mid"] or [t[0] for t in tab] == ["2|gl|ATP|mid", "2|gl|NBA|mid"], tab
    atp = next(t for t in tab if "ATP" in t[0])
    assert abs(atp[4] - 2.0) < 1e-9 and atp[1] == 40 and atp[2] == 0.0, atp
    # ATP: half the answers are 2 points over, half 1 point over: the best margin sits just under 2 points (win 50%) or just under 1 (win 100%): net(c) = c - 0.35pt - cost; just under 2: 0.5 x ~1.35, just under 1: 1.0 x ~0.35
    assert atp[7] is not None and 0.0185 <= atp[7] <= 0.0200, atp
    nba = next(t for t in tab if "NBA" in t[0])
    assert nba[2] > 0.0 and nba[1] == 36, nba
    assert "| 2|gl|ATP|mid | 40 |" in render_probe(tab)
    ex = [{"kind": "EXECUTED", "d": {"fair": 0.3, "price": 0.31, "rulePrice": 0.307, "ruleWouldWin": True, "modelPrice": 0.31, "modelWouldWin": True}},
          {"kind": "EXECUTED", "d": {"fair": 0.3, "price": 0.30, "rulePrice": 0.307, "ruleWouldWin": False, "modelPrice": 0.318, "modelWouldWin": False}}]
    s = executed_table(ex)
    assert "executed trades we price: 2; the rule would have won 1 (earning 0.70 pts each), the model 1 (1.00 pts each)" in s, s
    print("rfq_market selftest ok"); return 0


if __name__ == "__main__":
    a = sys.argv[1:]
    if a == ["selftest"]:
        sys.exit(selftest())
    if a and a[0] == "run" and len(a) > 1:
        # one pass, keeping only the two kinds this reads: a day of journals is millions of CREATED/CLOSED rows
        rs = [r for r in rows(a[1:]) if r.get("kind") in ("PROBE", "EXECUTED")]
        print(render_probe(probe_table(rs)))
        print()
        print(executed_table(rs))
        sys.exit(0)
    print(__doc__)
