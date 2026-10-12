#!/usr/bin/env python3
"""TAN3b: cluster-robust tests (bets in one game are not independent) + calibration + regression.
Run: python3 -I tools/research/tan_cluster_2026-10-12.py
Residual of a bet = profit - stake*ev (what it paid minus what its own fair chance promised).
z (cluster) = sum(residual) / sqrt(sum over games of (game's summed residual)^2): honest when a
slate's overs/unders move together. Calibration = actual win rate vs the mean fair chance."""
import sys, os, math, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np
import importlib.util
spec = importlib.util.spec_from_file_location("tb", os.path.join(os.path.dirname(os.path.abspath(__file__)), "tan_bets_2026-10-12.py"))
tb = importlib.util.module_from_spec(spec); spec.loader.exec_module(tb)

rows = tb.load()
base = [r for r in rows if not r.get("outlier") and not r.get("lockFor")]
S = [r for r in base if tb.settled(r) and r.get("ev") is not None and r.get("fair")]
for r in S:
    r["res"] = r["profit"] - r["stake"] * r["ev"]
    r["win"] = 1 if r["result"] == "won" else 0
    r["side"] = tb.side(r)
    r["game"] = r["event"] + "|" + r["starts"][:10]

def zc(rows, key="game"):
    if len(rows) < 2: return float("nan"), 0
    cl = collections.defaultdict(float)
    for r in rows: cl[r[key]] += r["res"]
    tot = sum(r["res"] for r in rows)
    den = math.sqrt(sum(v * v for v in cl.values()))
    return (tot / den if den else float("nan")), len(cl)

def line(label, rows):
    if not rows: return
    st = sum(r["stake"] for r in rows); pr = sum(r["profit"] for r in rows); ex = sum(r["stake"] * r["ev"] for r in rows)
    z, ng = zc(rows)
    zd, nd = zc(rows, "day")
    wr = sum(r["win"] for r in rows) / len(rows); mf = sum(r["fair"] for r in rows) / len(rows)
    print(f"{label:<40} n={len(rows):<4} games={ng:<4} ROI={pr/st*100:6.1f}% exp={ex/st*100:4.1f}% resid={ (pr-ex):7.2f} z_game={z:5.1f} z_day={zd:5.1f} | win {wr*100:4.1f}% vs fair {mf*100:4.1f}% ({(wr-mf)*100:+4.1f}pp)")

def sel(f): return [r for r in S if f(r)]
print("=== results vs fair, cluster-robust (by game / by day) ===")
line("ALL", S)
for made in ("bet", "bid"):
    line(f"made={made}", sel(lambda r: r["made"] == made))
bets = sel(lambda r: r["made"] == "bet")
prop = lambda r: r["kind"] == "Player props"
line("bets: props", [r for r in bets if prop(r)])
line("bets: props Over", [r for r in bets if prop(r) and r["side"] == "Over"])
line("bets: props Under", [r for r in bets if prop(r) and r["side"] == "Under"])
line("bets: non-props", [r for r in bets if not prop(r)])
line("bids: props Over", [r for r in S if r["made"] == "bid" and prop(r) and r["side"] == "Over"])
line("bids: props Under", [r for r in S if r["made"] == "bid" and prop(r) and r["side"] == "Under"])
line("ALL: props Over", [r for r in S if prop(r) and r["side"] == "Over"])
line("ALL: props Under", [r for r in S if prop(r) and r["side"] == "Under"])
for lg in ("NFL", "NHL", "MLB", "WNBA", "NCAAF"):
    line(f"props Over {lg}", [r for r in S if prop(r) and r["side"] == "Over" and r["league"] == lg])
    line(f"props Under {lg}", [r for r in S if prop(r) and r["side"] == "Under" and r["league"] == lg])
print("\n=== by market (props), ALL made ===")
mk = collections.defaultdict(list)
for r in S:
    if prop(r): mk[(r["league"], r["market"], r["side"])].append(r)
for k in sorted(mk, key=lambda k: -len(mk[k]))[:24]:
    line(str(k), mk[k])

print("\n=== calibration by fair-probability band (all settled) ===")
for a, b in [(0, .3), (.3, .4), (.4, .45), (.45, .5), (.5, .55), (.55, .6), (.6, 1)]:
    line(f"fair {a}-{b}", sel(lambda r: a <= r["fair"] < b))

print("\n=== EV band x made (cluster-robust) ===")
for a, b in [(0, .02), (.02, .03), (.03, .04), (.04, .05), (.05, .06)]:
    line(f"bets EV {a}-{b}", [r for r in bets if a <= r["ev"] < b])
    line(f"bids EV {a}-{b}", [r for r in S if r["made"] == "bid" and a <= r["ev"] < b])

print("\n=== lead time x CLV (bets + bids), all with a true close ===")
C = [r for r in base if r.get("clv") is not None]
def clvline(label, rows):
    n = len(rows)
    if n < 2: print(f"{label:<34} n={n}"); return
    m = sum(r["clv"] for r in rows) / n
    sd = math.sqrt(sum((r["clv"] - m) ** 2 for r in rows) / (n - 1))
    cl = collections.defaultdict(float)
    for r in rows: cl[r["event"] + r["starts"][:10]] += r["clv"] - m
    sec = math.sqrt(sum(v * v for v in cl.values()))
    tg = (m * n) / sec if sec else float("nan")
    I = [r for r in rows if r.get("closeIndependent")]
    im = sum(r["clv"] for r in I) / len(I) if I else float("nan")
    print(f"{label:<34} n={n:<4} CLV {m*100:5.2f}% t={m/(sd/math.sqrt(n)):5.1f} t_game={tg:5.1f} beat={sum(1 for r in rows if r['clv']>0)/n*100:3.0f}% | indep {im*100:5.2f}% n={len(I)}")
for made in ("bet", "bid", None):
    sub = [r for r in C if made is None or r["made"] == made]
    print(f"-- made={made}")
    for a, b in [(0, 1), (1, 2), (2, 4), (4, 6), (6, 9), (9, 13), (13, 24), (24, 1e9)]:
        clvline(f"lead {a}-{b} h", [r for r in sub if a <= (r["leadMin"] or 0) / 60 < b])
