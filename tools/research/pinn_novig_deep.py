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
    far_rows = [r for r in rows if abs(r['fair'] - r['ask']) > 0.15]
    rows = [r for r in rows if abs(r['fair'] - r['ask']) <= 0.15]   # a 15-point gap is a wrong line match, a finished game or a stale quote: reported in [F], never in the edge numbers
    print(f'\nrows (novig read x outcome): {len(rows)} (+ {len(far_rows)} with novig and Pinnacle more than 15 points apart, left out and listed in [F])')

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
    big = far_rows
    print(f'  rows where novig ask and Pinnacle fair differ by more than 15 points: {len(big)} (likely a wrong match, a finished game or a stale quote; never trade these)')
    cnt = collections.Counter((r['sport'], r['mt']) for r in big)
    print('   by sport/market:', dict(cnt.most_common(6)))
    print('  edge rows by novig depth (contracts at the ask): ' + ', '.join(f'<{lo}: {sum(1 for r in rows if r["ev"] >= min_ev and r["depth"] < lo)}' for lo in (100, 1000, 10000, 100000)))
    outside_box(pin, nov, mkt, match, scores, closes, rows, sport_of, t1)


def corr(a, b):
    try:
        return statistics.correlation(a, b) if len(a) > 5 else None
    except Exception:
        return None


def outside_box(pin, nov, mkt, match, scores, closes, rows, sport_of, t1):
    """[G] patterns people do not normally look for."""
    print('\n[G] OUTSIDE THE BOX')
    # G1 who leads whom: correlation of Pinnacle's fair change with Novig's mid change, both ways, on a 5 s grid (10 s changes)
    print('  [G1] lead-lag: corr( change in Pinnacle home fair over 10 s at t , change in Novig home mid over 10 s at t+lag ). A peak at a positive lag = Novig follows Pinnacle; at a negative lag = Novig leads')
    pairs = collections.defaultdict(lambda: ([], []))
    for mid, obs in nov.items():
        m = mkt.get(mid)
        if not m or m['mt'] != 'MONEY' or m['pid'] not in match: continue
        series = L.pin_series(pin, m); sm = L.side_map(m); hn = [n for n, sd in sm.items() if sd == 'home']
        if len(series) < 3 or len(obs) < 8 or not hn: continue
        hn = hn[0]; an = [n for n in sm if n != hn][0]
        ts = [x['t'] for x in series]; ot = [o['t'] for o in obs]
        lo = max(ts[0], ot[0]); hi = min(ts[-1], ot[-1])
        grid = list(range(int(lo), int(hi), 5000))
        if len(grid) < 12: continue
        def pv(t):
            k = bisect.bisect_right(ts, t) - 1; return series[k]['fairp']['home'] if k >= 0 else None
        def nv(t):
            k = bisect.bisect_right(ot, t) - 1
            if k < 0: return None
            bb = obs[k]['bb']
            if bb.get(hn) is None or bb.get(an) is None: return None
            return (bb[hn][0] + (1 - bb[an][0])) / 2.0
        P = [pv(t) for t in grid]; N = [nv(t) for t in grid]
        dP = [(P[i] - P[i - 2]) if P[i] is not None and P[i - 2] is not None else None for i in range(len(grid))]
        dN = [(N[i] - N[i - 2]) if N[i] is not None and N[i - 2] is not None else None for i in range(len(grid))]
        for lag in range(-6, 7):
            for i in range(2, len(grid)):
                j = i + lag
                if 2 <= j < len(grid) and dP[i] is not None and dN[j] is not None:
                    pairs[(sport_of.get(m['pid'], '?'), lag * 5)][0].append(dP[i]); pairs[(sport_of.get(m['pid'], '?'), lag * 5)][1].append(dN[j])
                    pairs[('ALL', lag * 5)][0].append(dP[i]); pairs[('ALL', lag * 5)][1].append(dN[j])
    for sp in sorted({k[0] for k in pairs}, key=lambda x: (x != 'ALL', x)):
        line = [(lag, corr(*pairs[(sp, lag)])) for lag in range(-30, 31, 5) if (sp, lag) in pairs]
        line = [(l, c) for l, c in line if c is not None]
        if line:
            best = max(line, key=lambda x: x[1])
            print(f'    {sp:11s} n={len(pairs[(sp, 0)][0]):5d}  corr by lag(s): ' + ' '.join(f'{l:+d}:{c:.2f}' for l, c in line) + f'   peak at {best[0]:+d} s')

    # G2 order-book imbalance as a predictor
    print('  [G2] Novig order-book imbalance (bid size on home minus away, as a share) vs what Novig and Pinnacle do next 30 s')
    xs = []; yn = []; yp = []
    for mid, obs in nov.items():
        m = mkt.get(mid)
        if not m or m['mt'] != 'MONEY' or m['pid'] not in match: continue
        series = L.pin_series(pin, m); sm = L.side_map(m); hn = [n for n, sd in sm.items() if sd == 'home']
        if len(series) < 3 or len(obs) < 8 or not hn: continue
        hn = hn[0]; an = [n for n in sm if n != hn][0]; ts = [x['t'] for x in series]; ot = [o['t'] for o in obs]
        for i, o in enumerate(obs):
            bb = o['bb']
            if bb.get(hn) is None or bb.get(an) is None or o['t'] + 30000 > t1: continue
            tot = bb[hn][1] + bb[an][1]
            if tot <= 0: continue
            j = bisect.bisect_right(ot, o['t'] + 30000) - 1
            if j <= i: continue
            b2 = obs[j]['bb']
            if b2.get(hn) is None or b2.get(an) is None: continue
            k0 = bisect.bisect_right(ts, o['t']) - 1; k1 = bisect.bisect_right(ts, o['t'] + 30000) - 1
            if k0 < 0 or k1 < 0: continue
            xs.append((bb[hn][1] - bb[an][1]) / tot)
            yn.append((b2[hn][0] + 1 - b2[an][0]) / 2 - (bb[hn][0] + 1 - bb[an][0]) / 2)
            yp.append(series[k1]['fairp']['home'] - series[k0]['fairp']['home'])
    c1, c2 = corr(xs, yn), corr(xs, yp)
    print(f'    n={len(xs)}  corr(imbalance, next Novig mid change) {fmt(c1, "{:+.2f}")}  corr(imbalance, next Pinnacle fair change) {fmt(c2, "{:+.2f}")}')
    big = [(x, y) for x, y in zip(xs, yp) if abs(x) > 0.6]
    if big: print(f'    |imbalance| > 0.6: n={len(big)}, Pinnacle moved the way the heavy bid side points in {sum(1 for x, y in big if x * y > 0)} of {sum(1 for x, y in big if x * y != 0)} moves')

    # G3 Pinnacle's own margin and limit as a warning
    print('  [G3] Pinnacle margin (vig) or limit change as a warning of a move in the next 60 s')
    dv_big = []; dv_small = []; lim_drop = []; lim_ok = []
    for (pid, key), v in pin.items():
        if pid not in match or not key.startswith('s;0;') or len(v) < 3: continue
        for i in range(1, len(v) - 1):
            a, b = v[i - 1], v[i]
            nxt = [x for x in v[i + 1:] if x['t'] - b['t'] <= 60000]
            if not nxt: continue
            side = next(iter(b['fairp'])); mv = abs(nxt[-1]['fairp'].get(side, b['fairp'][side]) - b['fairp'][side])
            (dv_big if (b['vig'] - a['vig']) > 0.01 else dv_small).append(mv)
            if a.get('lim') and b.get('lim'): (lim_drop if b['lim'] < a['lim'] else lim_ok).append(mv)
    print(f'    margin widened by > 1 pt: n={len(dv_big)} mean |next-60 s fair move| {fmt(None if not dv_big else 100 * statistics.mean(dv_big), "{:.2f}")} pts; otherwise n={len(dv_small)} {fmt(None if not dv_small else 100 * statistics.mean(dv_small), "{:.2f}")} pts')
    print(f'    limit cut: n={len(lim_drop)} mean next-60 s move {fmt(None if not lim_drop else 100 * statistics.mean(lim_drop), "{:.2f}")} pts; limit not cut n={len(lim_ok)} {fmt(None if not lim_ok else 100 * statistics.mean(lim_ok), "{:.2f}")} pts')

    # G4 jump reversion and flicker
    print('  [G4] after a Pinnacle moneyline jump of >= 2 fair points: how much of it is still there later; split by whether a score came in the 10 s before')
    out = collections.defaultdict(list)
    for (pid, key), v in pin.items():
        if pid not in match or key != 's;0;m' or len(v) < 3: continue
        sc = [x['t'] for x in scores.get(pid, [])]
        for i in range(1, len(v)):
            d = v[i]['fairp']['home'] - v[i - 1]['fairp']['home']
            if abs(d) < 0.02 or v[i]['t'] + 120000 > t1: continue
            cause = 'score' if any(0 <= v[i]['t'] - t <= 10000 for t in sc) else 'price-only'
            ts = [x['t'] for x in v]
            def at(sec):
                k = bisect.bisect_right(ts, v[i]['t'] + sec * 1000) - 1
                return v[k]['fairp']['home']
            kept = {sec: (at(sec) - v[i - 1]['fairp']['home']) / d for sec in (5, 10, 30, 120)}
            out[cause].append(kept)
    for cause, l in out.items():
        print(f'    {cause:10s} jumps {len(l):3d}  share of the jump still there after 5 s {fmt(100 * med([x[5] for x in l]), "{:.0f}")}%  10 s {fmt(100 * med([x[10] for x in l]), "{:.0f}")}%  30 s {fmt(100 * med([x[30] for x in l]), "{:.0f}")}%  120 s {fmt(100 * med([x[120] for x in l]), "{:.0f}")}%  (median)  flicker (>=50% undone within 10 s) {sum(1 for x in l if x[10] < 0.5)}')

    # G5 market-type order after a score, G9 break-even delay
    print('  [G5] after a score, which novig market re-quotes first (seconds to the first best-bid change, median by market type)')
    first = collections.defaultdict(list)
    delays = (2.0, 3.5, 5.3, 8.0, 12.0)
    ev_at = collections.defaultdict(list); gap_at = collections.defaultdict(list)
    for pid, sl in scores.items():
        if pid not in match: continue
        for s in sl:
            if s['old'] is None or s['new'] == s['old']: continue
            scorer_home = (s['new'][0] or 0) > (s['old'][0] or 0); scorer_away = (s['new'][1] or 0) > (s['old'][1] or 0)
            if scorer_home == scorer_away: continue
            for mid, m in mkt.items():
                if m['pid'] != pid or mid not in nov: continue
                obs = nov[mid]; ot = [o['t'] for o in obs]; i = bisect.bisect_right(ot, s['t']) - 1
                if i < 0 or s['t'] - ot[i] > 15000: continue
                base = {k: v[0] for k, v in obs[i]['bb'].items() if v}
                for o in obs[i + 1:]:
                    cur = {k: v[0] for k, v in o['bb'].items() if v}
                    if any(abs(cur.get(k, 0) - base.get(k, 0)) >= 0.005 for k in base):
                        first[m['mt']].append((o['t'] - s['t']) / 1000.0); break
                    if o['t'] - s['t'] > 60000: break
                if m['mt'] != 'MONEY': continue
                series = L.pin_series(pin, m); sm = L.side_map(m)
                if not series: continue
                ts = [x['t'] for x in series]
                if s['t'] + 45000 > t1: continue
                kf = bisect.bisect_right(ts, s['t'] + 30000) - 1
                if kf < 0: continue
                fair_later = series[kf]['fairp']
                hn = [n for n, sd in sm.items() if sd == 'home']
                if not hn: continue
                hn = hn[0]; an = [n for n in sm if n != hn][0]
                buy = hn if scorer_home else an; other = an if scorer_home else hn
                for d in delays:
                    j = bisect.bisect_left(ot, s['t'] + d * 1000)
                    if j >= len(ot) or ot[j] - s['t'] > (d + 6) * 1000: continue
                    bb = obs[j]['bb']
                    if bb.get(other) is None: continue
                    ask = 1 - bb[other][0]; fee = L.fee_of(ask)
                    fl = fair_later['home' if buy == hn else 'away']
                    ev_at[d].append(fl / (ask + fee) - 1.0)
    for mt, l in sorted(first.items()):
        print(f'    {mt:7s} markets {len(l):3d}  first change median {fmt(med(l))} s  p25 {fmt(pct(l, .25))}  p75 {fmt(pct(l, .75))}')
    print('  [G9] THE ORDER-DELAY TEST: buy the team that just scored (moneyline) at novig\'s ask as it stood d seconds after the score, judged against Pinnacle\'s fair 30 s after the score (fee in)')
    for d in delays:
        l = ev_at.get(d, [])
        if l: print(f'    order lands {d:4.1f} s after the score: n={len(l):3d}  median EV {fmt(100 * med(l), "{:+.1f}")}%  mean {fmt(100 * statistics.mean(l), "{:+.1f}")}%  share with EV > 0: {sum(1 for x in l if x > 0) / len(l):.0%}  share >= 2%: {sum(1 for x in l if x >= 0.02) / len(l):.0%}')

    # G6 price-grid rounding
    print('  [G6] novig price grid: EV of the ask by tick (the ask is 1 - best bid; a half-cent tick rounds in someone\'s favour)')
    tick = collections.defaultdict(list)
    for r in rows:
        tick['half-cent (x.5)' if abs((r['ask'] * 100) % 1 - 0.5) < 1e-6 else 'whole cent'].append(r['ev'])
    for k, l in tick.items(): print(f'    {k:16s} rows {len(l):5d}  median EV {fmt(100 * med(l), "{:+.2f}")}%  share >= 2%: {sum(1 for x in l if x >= 0.02) / len(l):.1%}')

    # G7 outcomes the score already decided
    print('  [G7] totals the score has already decided: an Over whose strike is below the points scored should cost ~$1; an Under should be worthless')
    dead = []; seen_t = 0
    for mid, obs in nov.items():
        m = mkt.get(mid)
        if not m or m['mt'] != 'TOTAL' or m['pid'] not in match: continue
        series = L.pin_series(pin, m)
        sc_series = [(x['t'], x.get('score')) for x in pin.get((m['pid'], 's;0;m'), []) if x.get('score')] or [(x['t'], x.get('score')) for x in series if x.get('score')]
        if not sc_series: continue
        ts = [t for t, _ in sc_series]; sm = L.side_map(m)
        for o in obs:
            k = bisect.bisect_right(ts, o['t']) - 1
            if k < 0: continue
            sc = sc_series[k][1]
            if not sc or sc[0] is None or sc[1] is None: continue
            pts = sc[0] + sc[1]; strike = float(m.get('strike') or 0)
            seen_t += 1
            if pts > strike:
                for name, v in o['bb'].items():
                    if v is None: continue
                    if sm.get(name) == 'over':
                        other = [n for n in o['bb'] if n != name]
                        if other and o['bb'][other[0]]:
                            ask = 1 - o['bb'][other[0]][0]
                            if ask < 0.97: dead.append((m['pid'], strike, pts, round(ask, 3), o['t']))
    print(f'    total reads checked {seen_t}; Over already decided and still offered under 97 cents: {len(dead)}' + (f' (first: {dead[:3]})' if dead else ''))

    # G8 score-effect model
    print('  [G8] what a score does to the moneyline fair (scorer\'s win chance, points, 10 s after), by sport and by whether the scorer was leading, tied or trailing')
    eff = collections.defaultdict(list)
    for pid, sl in scores.items():
        if pid not in match: continue
        v = pin.get((pid, 's;0;m'), [])
        if len(v) < 3: continue
        ts = [x['t'] for x in v]
        for s in sl:
            if s['old'] is None: continue
            dh = (s['new'][0] or 0) - (s['old'][0] or 0); da = (s['new'][1] or 0) - (s['old'][1] or 0)
            if (dh > 0) == (da > 0) or s['t'] + 12000 > t1: continue
            k0 = bisect.bisect_right(ts, s['t'] - 500) - 1; k1 = bisect.bisect_right(ts, s['t'] + 10000) - 1
            if k0 < 0 or k1 < 0: continue
            home = dh > 0; a = v[k0]['fairp']['home']; b = v[k1]['fairp']['home']
            gain = (b - a) if home else -(b - a)
            lead = (s['old'][0] or 0) - (s['old'][1] or 0); lead = lead if home else -lead
            state = 'tied' if lead == 0 else ('leading' if lead > 0 else 'trailing')
            eff[(sport_of.get(pid, '?'), state)].append(gain)
    for k, l in sorted(eff.items()):
        print(f'    {k[0]:11s} scorer {k[1]:8s} n={len(l):3d}  median gain {fmt(100 * med(l), "{:+.1f}")} pts  p25 {fmt(100 * pct(l, .25), "{:+.1f}")}  p75 {fmt(100 * pct(l, .75), "{:+.1f}")}')


if __name__ == '__main__':
    main()
