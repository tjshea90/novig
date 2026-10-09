#!/usr/bin/env python3
"""Novig's own alternate-line ladders (totals and spreads of one game): do odd prices exist, and are any of them takeable for a locked profit?
RESEARCH.md section 120.6 (Tj's 2026-10-09 screenshots: 'Under 52.5 99.9%, Under 53.5 90%, Under 54.5 99.9%').  Reads tapes from pinn_novig_lag.py; sends nothing.

    python3 tools/research/pinn_novig_ladder.py tape1.ndjson [tape2.ndjson ...]

On Novig an outcome's displayed price is what BUYING it costs = 1 - the best bid on the OTHER outcome; with no bid on the other outcome there is no price and the app shows 99.9% (a cap, not an offer).
For totals: Over j + Under k with j <= k covers every score (it pays $1, or $2 when the total lands between), so askOver(j) + askUnder(k) < 1 would be a locked profit before fees;
Over k + Under k = 1 + the spread.  Reports: how often a side has no price, how often a strike is the ONLY priced one in its ladder, and every cover under $1 and under $1.01 (fee-free pregame).
"""
import collections, json, os, statistics, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pinn_novig_lag as L


def main(paths):
    nov = collections.defaultdict(list); mkt = {}; match = {}
    for p in paths:
        _, b, c, d, _, _ = L.load(p)
        for k, v in b.items(): nov[k] += v
        mkt.update(c); match.update(d)
    by_game = collections.defaultdict(list)
    for mid, m in mkt.items():
        if mid in nov and m['mt'] in ('TOTAL',): by_game[m['pid']].append(mid)
    sides = collections.Counter(); isolated = 0; ladders = 0; covers = []; near = 0; total_pairs = 0
    for pid, mids in by_game.items():
        if len(mids) < 2: continue
        times = sorted(o['t'] for mid in mids for o in nov[mid])
        step = 6000
        t = times[0]
        while t < times[-1]:
            ladder = {}
            for mid in mids:
                last = None
                for o in nov[mid]:
                    if t - 6000 <= o['t'] <= t: last = o
                if not last: continue
                strike = float(mkt[mid]['strike']); bb = last['bb']; sm = L.side_map(mkt[mid])
                over = [n for n in bb if sm.get(n) == 'over']; under = [n for n in bb if sm.get(n) == 'under']
                if not over or not under: continue
                bo, bu = bb[over[0]], bb[under[0]]
                askO = None if bu is None else 1 - bu[0]; askU = None if bo is None else 1 - bo[0]   # buy Over = 1 - best bid on Under
                ladder[strike] = (askO, askU)
                sides['over priced' if askO is not None else 'over NO price'] += 1; sides['under priced' if askU is not None else 'under NO price'] += 1
            if len(ladder) >= 3:
                ladders += 1
                priced = [k for k, (a, b) in ladder.items() if a is not None or b is not None]
                if len(priced) == 1: isolated += 1
                ks = sorted(ladder)
                for j in ks:
                    for k in ks:
                        if j <= k and ladder[j][0] is not None and ladder[k][1] is not None:
                            total_pairs += 1; c = ladder[j][0] + ladder[k][1]
                            if c < 1.0: covers.append((pid, j, k, round(c, 3), int(t)))
                            elif c < 1.01 and j < k: near += 1
            t += step
    print(f'games with 2+ total strikes: {len([g for g in by_game.values() if len(g) >= 2])}; ladder snapshots (3+ strikes read within 6 s): {ladders}')
    print('prices per side per strike read:', dict(sides), f'-> {sides["over NO price"] + sides["under NO price"]} of {sum(sides.values())} sides ({(sides["over NO price"] + sides["under NO price"]) / max(1, sum(sides.values())):.0%}) show no price (the app\'s 99.9%)')
    print(f'snapshots where exactly one strike has any price: {isolated}')
    print(f'cover pairs checked {total_pairs}; locked-profit covers (Over j + Under k, j <= k, asks sum < $1): {len(covers)}; j < k covers under $1.01: {near}')
    for c in sorted(covers, key=lambda x: x[3])[:10]: print('   ', c)


if __name__ == '__main__':
    main(sys.argv[1:])
