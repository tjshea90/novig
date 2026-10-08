#!/usr/bin/env python3
"""Deep read of a pinn_novig_lag.py tape (Pinnacle on the Pinnodds socket against Novig's public live books).
RESEARCH.md section 120.2.  Reads one or more tapes (they are merged); sends nothing; needs no key.

    python3 tools/research/pinn_novig_deep.py tape1.ndjson [tape2.ndjson ...] [--min-ev 0.02]

Sections: [A] coverage by sport; [B] edge rows by sport/market, with what the same ask was worth against Pinnacle's fair 30 s / 120 s later (CLV-style);
[C] runs of edge (flash after a Pinnacle move vs standing disagreement); [D] how long Novig takes to re-quote after a Pinnacle move, by sport;
[E] what Novig's book does around a score (reads, spread, depth, first change) and what Pinnacle does (suspend / first reprice); [F] anomalies (crossed books, far disagreements).
Everything is on the tape's own clock: Novig is polled about every 4.5 s per market (p90 12 s), so nothing shorter than that is visible.
"""
import bisect, collections, json, os, statistics, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pinn_novig_lag as L


def load_many(paths):
    pin = collections.defaultdict(list); nov = collections.defaultdict(list); mkt = {}; match = {}; scores = collections.defaultdict(list); closes = []
    for p in paths:
        a, b, c, d, e, f = L.load(p)
        for k, v in a.items(): pin[k] += v
        for k, v in b.items(): nov[k] += v
        mkt.update(c); match.update(d)
        for k, v in e.items(): scores[k] += v
        closes += f
    for v in pin.values(): v.sort(key=lambda x: x['t'])
    for v in nov.values(): v.sort(key=lambda x: x['t'])
    for v in scores.values(): v.sort(key=lambda x: x['t'])
    closes.sort(key=lambda x: x['t'])
    return pin, nov, mkt, match, scores, closes


def med(xs):
    xs = [x for x in xs if x is not None]
    return statistics.median(xs) if xs else None


def med100(xs):
    m = med(xs)
    return None if m is None else 100 * m


def pct(xs, q):
    xs = sorted(x for x in xs if x is not None)
    return xs[min(len(xs) - 1, int(q * len(xs)))] if xs else None


def fmt(x, f='{:.1f}'):
    return 'n/a' if x is None else f.format(x)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    min_ev = 0.02
    if '--min-ev' in sys.argv:
        min_ev = float(sys.argv[sys.argv.index('--min-ev') + 1]); args = [a for a in args if a != str(sys.argv[sys.argv.index('--min-ev') + 1])]
    pin, nov, mkt, match, scores, closes = load_many(args)
    sport_of = {}
    for (pid, key), v in pin.items():
        if v and v[0].get('sport'): sport_of[pid] = v[0]['sport']
    t0 = min([d['t'] for v in nov.values() for d in v] or [0]); t1 = max([d['t'] for v in nov.values() for d in v] or [0])
    print(f'tapes {len(args)} · span {(t1 - t0) / 60000:.0f} min · matched games {len(match)} · novig markets {len(nov)} · pinnacle series {len(pin)} · score changes {sum(len(v) for v in scores.values())} · closes {len(closes)}')

    # ---- A coverage
    print('\n[A] coverage by sport (matched games, novig markets read, novig reads, pinnacle changes on matched games)')
    byS = collections.defaultdict(lambda: [set(), 0, 0, 0])
    for mid, obs in nov.items():
        m = mkt.get(mid)
        if not m or m['pid'] not in match: continue
        s = sport_of.get(m['pid'], '?'); byS[s][0].add(m['pid']); byS[s][1] += 1; byS[s][2] += len(obs)
    for (pid, key), v in pin.items():
        if pid in match: byS[sport_of.get(pid, '?')][3] += len(v)
    for s, (g, nm, nr, npn) in sorted(byS.items(), key=lambda kv: -kv[1][2]):
        print(f'  {s:12s} games {len(g):3d}  markets {nm:3d}  novig reads {nr:6d}  pinnacle changes {npn:6d}')

    # ---- rows
    rows = []
    for mid, obs in nov.items():
        m = mkt.get(mid)
        if not m or m['pid'] not in match: continue
        series = L.pin_series(pin, m); sm = L.side_map(m)
        if not series: continue
        ts = [x['t'] for x in series]
        for o in obs:
            bb = o['bb']
            if len(bb) != 2 or any(v is None for v in bb.values()): continue
            k = bisect.bisect_right(ts, o['t']) - 1
            if k < 0: continue
            pr = series[k]; age = (o['t'] - pr['t']) / 1000.0
            names = list(bb.keys())
            for x in names:
                side = sm.get(x)
                if side not in pr['fairp']: continue
                other = [n for n in names if n != x][0]
                ask = 1.0 - bb[other][0]; depth = bb[other][1]; fee = L.fee_of(ask)
                fair = pr['fairp'][side]
                def later(sec):
                    j = bisect.bisect_right(ts, o['t'] + sec * 1000) - 1
                    if j <= k or ts[j] > t1 or o['t'] + sec * 1000 > t1: return None   # nothing seen to follow up with
                    return series[j]['fairp'].get(side)
                f30, f120 = later(30), later(120)
                rows.append({'mid': mid, 'pid': m['pid'], 'mt': m['mt'], 'x': x, 'side': side, 't': o['t'], 'ask': ask, 'depth': depth, 'fair': fair, 'age': age,
                             'ev': fair / (ask + fee) - 1.0, 'ev30': None if f30 is None else f30 / (ask + fee) - 1.0, 'ev120': None if f120 is None else f120 / (ask + fee) - 1.0,
                             'sport': sport_of.get(m['pid'], '?'), 'lim': pr.get('lim'), 'vig': pr.get('vig'), 'bidx': bb[x][0], 'spread': 1.0 - bb[names[0]][0] - bb[names[1]][0]})
    print(f'\nrows (novig read x outcome): {len(rows)}')

    # ---- B edge by sport / market, with the follow-up
    print(f'\n[B] rows with EV >= {min_ev:.0%} at the ask (fee in) against Pinnacle power-devigged fair, and the same ask against Pinnacle 30 s / 120 s later (only rows where Pinnacle moved again)')
    groups = collections.defaultdict(list)
    for r in rows: groups[(r['sport'], r['mt'])].append(r); groups[('ALL', r['mt'])].append(r); groups[('ALL', 'ALL')].append(r)
    for g, rs in sorted(groups.items(), key=lambda kv: (kv[0][0] != 'ALL', kv[0])):
        sel = [r for r in rs if r['ev'] >= min_ev]
        if len(rs) < 30: continue
        print(f'  {g[0]:11s} {g[1]:6s} rows {len(rs):6d}  edge rows {len(sel):5d} ({len(sel) / len(rs):5.1%})  median EV all {fmt(med100([r["ev"] for r in rs]), "{:+.2f}")}%  '
              f'edge rows: EV now {fmt(med100([r["ev"] for r in sel]), "{:+.1f}")}%  +30s {fmt(med100([r["ev30"] for r in sel]), "{:+.1f}")}% (n={sum(r["ev30"] is not None for r in sel)})  '
              f'+120s {fmt(med100([r["ev120"] for r in sel]), "{:+.1f}")}% (n={sum(r["ev120"] is not None for r in sel)})  median depth {fmt(med([r["depth"] for r in sel]), "{:.0f}")}')
    print('  by seconds since Pinnacle last changed the line (ALL): share of rows with EV >= min, median EV at +120 s for those rows')
    for lo, hi in ((0, 2), (2, 5), (5, 10), (10, 20), (20, 60), (60, 1e9)):
        sel = [r for r in rows if lo <= r['age'] < hi]; e = [r for r in sel if r['ev'] >= min_ev]
        if sel: print(f'    {lo:3.0f}-{hi:5.0f}s  n {len(sel):6d}  edge {len(e) / len(sel):6.1%}  EV+120s median {fmt(med100([r["ev120"] for r in e]), "{:+.1f}")}% (n={sum(r["ev120"] is not None for r in e)})')
    print('  by Pinnacle limit (a small limit is a soft quote):')
    for lo, hi in ((0, 100), (100, 500), (500, 2000), (2000, 1e12)):
        sel = [r for r in rows if r['lim'] is not None and lo <= r['lim'] < hi]; e = [r for r in sel if r['ev'] >= min_ev]
        if sel: print(f'    limit {lo:5.0f}-{hi:12.0f}  n {len(sel):6d}  edge {len(e) / len(sel):6.1%}  EV+120s median {fmt(med100([r["ev120"] for r in e]), "{:+.1f}")}% (n={sum(r["ev120"] is not None for r in e)})')

    # ---- C runs of edge
    print(f'\n[C] runs of consecutive novig reads on one outcome with EV >= {min_ev:.0%}: how long they last and whether they began with a Pinnacle move')
    runs = []
    by_out = collections.defaultdict(list)
    for r in rows: by_out[(r['mid'], r['x'])].append(r)
    for key, rs in by_out.items():
        rs.sort(key=lambda r: r['t']); cur = []
        for r in rs + [None]:
            if r is not None and r['ev'] >= min_ev:
                cur.append(r)
            else:
                if cur:
                    runs.append((cur[0]['sport'], cur[0]['mt'], (cur[-1]['t'] - cur[0]['t']) / 1000.0 + 4.5, cur[0]['age'], max(c['ev'] for c in cur), cur[0]['depth'], len(cur)))
                cur = []
    if runs:
        flash = [r for r in runs if r[3] <= 10]; stand = [r for r in runs if r[3] > 10]
        print(f'  runs {len(runs)}: begin within 10 s of a Pinnacle move {len(flash)} (median length {fmt(med([r[2] for r in flash]))} s, p90 {fmt(pct([r[2] for r in flash], .9))} s); begin later (standing disagreement) {len(stand)} (median {fmt(med([r[2] for r in stand]))} s, p90 {fmt(pct([r[2] for r in stand], .9))} s)')
        print(f'  longest runs: {sorted([round(r[2]) for r in runs])[-8:]} s · single-read runs (not takeable in 4.5 s): {sum(r[6] == 1 for r in runs)} of {len(runs)}')

    # ---- D re-quote latency
    print('\n[D] after a Pinnacle move of >= 1.5 fair points on a matched market: seconds until Novig\'s best bid on either side changes (reads are ~4.5 s apart, so the floor is about 2 s)')
    lat = collections.defaultdict(list); noresp = collections.defaultdict(int); tot = collections.defaultdict(int)
    for mid, obs in nov.items():
        m = mkt.get(mid)
        if not m or m['pid'] not in match: continue
        series = L.pin_series(pin, m); sm = L.side_map(m)
        if len(series) < 2 or len(obs) < 3: continue
        ot = [o['t'] for o in obs]
        for a_, b_ in zip(series, series[1:]):
            if b_['t'] > t1 - 60000: break
            side = 'home' if 'home' in b_['fairp'] else 'over'
            if side not in a_['fairp']: continue
            d = abs(b_['fairp'][side] - a_['fairp'][side])
            if d < 0.015: continue
            j = bisect.bisect_right(ot, b_['t']) - 1
            if j < 0 or b_['t'] - ot[j] > 15000: continue
            base = {k: v[0] for k, v in obs[j]['bb'].items() if v}
            s = sport_of.get(m['pid'], '?'); tot[s] += 1; tot['ALL'] += 1
            hit = None
            for o in obs[j + 1:]:
                if o['t'] - b_['t'] > 60000: break
                cur = {k: v[0] for k, v in o['bb'].items() if v}
                if any(abs(cur.get(k, 0) - base.get(k, 0)) >= 0.005 for k in base):
                    hit = (o['t'] - b_['t']) / 1000.0; break
            if hit is None: noresp[s] += 1; noresp['ALL'] += 1
            else: lat[s].append(hit); lat['ALL'].append(hit)
    for s in sorted(tot, key=lambda s: (s != 'ALL', -tot[s])):
        print(f'  {s:11s} moves {tot[s]:4d}  no change within 60 s {noresp[s]:3d}  re-quote latency median {fmt(med(lat[s]))} s  p25 {fmt(pct(lat[s], .25))}  p75 {fmt(pct(lat[s], .75))}  p90 {fmt(pct(lat[s], .9))}')

    # ---- E around a score
    print('\n[E] around a Pinnacle score change: novig reads per second, book spread, first novig change, and Pinnacle suspend / first reprice')
    ev_rows = []
    for pid, sl in scores.items():
        if pid not in match: continue
        mids = [mid for mid, m in mkt.items() if m['pid'] == pid and mid in nov]
        pk = [(key, v) for (p, key), v in pin.items() if p == pid]
        for s in sl:
            if s['old'] is None or s['new'] == s['old']: continue
            ts_ = s['t']; first_nov = []; spread_b = []; spread_a = []; reads_b = 0; reads_a = 0; depth_b = []; depth_a = []
            for mid in mids:
                obs = nov[mid]; ot = [o['t'] for o in obs]
                i = bisect.bisect_right(ot, ts_) - 1
                if i < 0 or ts_ - ot[i] > 15000: continue
                base = {k: v[0] for k, v in obs[i]['bb'].items() if v}
                reads_b += sum(1 for o in obs if ts_ - 10000 <= o['t'] < ts_); reads_a += sum(1 for o in obs if ts_ <= o['t'] < ts_ + 10000)
                for o in obs[i:]:
                    cur = {k: v[0] for k, v in o['bb'].items() if v}
                    if o['t'] >= ts_ and any(abs(cur.get(k, 0) - base.get(k, 0)) >= 0.005 for k in base):
                        first_nov.append((o['t'] - ts_) / 1000.0); break
                    if o['t'] - ts_ > 60000: break
                for o in obs:
                    if ts_ - 10000 <= o['t'] < ts_ and len(o['bb']) == 2: spread_b.append(1 - sum(v[0] for v in o['bb'].values() if v)); depth_b += [v[1] for v in o['bb'].values() if v]
                    if ts_ + 2000 <= o['t'] < ts_ + 12000 and len(o['bb']) == 2: spread_a.append(1 - sum(v[0] for v in o['bb'].values() if v)); depth_a += [v[1] for v in o['bb'].values() if v]
            pin_first = None
            for key, v in pk:
                for x in v:
                    if x['t'] > ts_:
                        d = (x['t'] - ts_) / 1000.0
                        if pin_first is None or d < pin_first: pin_first = d
                        break
            susp = [c for c in closes if c['pid'] == pid and ts_ - 5000 <= c['t'] <= ts_ + 15000]
            ev_rows.append({'sport': sport_of.get(pid, '?'), 'nov_first': min(first_nov) if first_nov else None, 'pin_first': pin_first, 'susp': bool(susp), 'rb': reads_b, 'ra': reads_a,
                            'sp_b': med(spread_b), 'sp_a': med(spread_a), 'd_b': med(depth_b), 'd_a': med(depth_a), 'markets': len(first_nov)})
    print(f'  scores on matched games with novig reads before and after: {len(ev_rows)}')
    if ev_rows:
        print(f'  first Pinnacle reprice after the score: median {fmt(med([r["pin_first"] for r in ev_rows]))} s (n={sum(r["pin_first"] is not None for r in ev_rows)})')
        print(f'  first novig best-bid change after the score: median {fmt(med([r["nov_first"] for r in ev_rows]))} s  p25 {fmt(pct([r["nov_first"] for r in ev_rows], .25))}  p75 {fmt(pct([r["nov_first"] for r in ev_rows], .75))} (n={sum(r["nov_first"] is not None for r in ev_rows)} of {len(ev_rows)})')
        print(f'  Pinnacle market suspended within -5..+15 s of the score: {sum(r["susp"] for r in ev_rows)} of {len(ev_rows)}')
        print(f'  novig reads in the 10 s before / after the score (per market, all markets of the game): {sum(r["rb"] for r in ev_rows)} / {sum(r["ra"] for r in ev_rows)}  (a pause that stopped quoting would show fewer after)')
        print(f'  novig book spread (1 - bidA - bidB) median before {fmt(med100([r["sp_b"] for r in ev_rows]), "{:.2f}")} pts, +2..12 s after {fmt(med100([r["sp_a"] for r in ev_rows]), "{:.2f}")} pts; depth before {fmt(med([r["d_b"] for r in ev_rows]), "{:.0f}")}, after {fmt(med([r["d_a"] for r in ev_rows]), "{:.0f}")}')
        for s in sorted({r['sport'] for r in ev_rows}):
            ss = [r for r in ev_rows if r['sport'] == s]
            print(f'    {s:11s} scores {len(ss):3d}  novig first change median {fmt(med([r["nov_first"] for r in ss]))} s  pinnacle first reprice median {fmt(med([r["pin_first"] for r in ss]))} s')

    # ---- F anomalies
    print('\n[F] anomalies')
    crossed = [r for r in rows if r['spread'] < -0.001]
    print(f'  novig books crossed or locked (bidA + bidB >= 1: a free cover if real): {len(crossed)} rows; wide books (spread > 8 pts): {sum(r["spread"] > 0.08 for r in rows)} rows ({len(rows) and sum(r["spread"] > 0.08 for r in rows) / len(rows):.1%})')
    big = [r for r in rows if abs(r['fair'] - (1 - (1 - r['ask']))) > 0.15]
    print(f'  rows where novig ask and Pinnacle fair differ by more than 15 points: {len(big)} (likely a wrong match, a finished game or a stale quote; never trade these)')
    cnt = collections.Counter((r['sport'], r['mt']) for r in big)
    print('   by sport/market:', dict(cnt.most_common(6)))
    print('  edge rows by novig depth (contracts at the ask): ' + ', '.join(f'<{lo}: {sum(1 for r in rows if r["ev"] >= min_ev and r["depth"] < lo)}' for lo in (100, 1000, 10000, 100000)))


if __name__ == '__main__':
    main()
