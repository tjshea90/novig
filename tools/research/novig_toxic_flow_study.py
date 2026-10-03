#!/usr/bin/env python3
"""Does a resting Novig bid get picked off after one-sided buying? RESEARCH.md §72, re-runnable.

Tj, 2026-10-03: "avoiding bets that look like positive EV but are actually sharp bets on the other side and the rest of the markets lag ...
this information should guide the vigilant app on both taking and making bets and bids".

A bid on outcome X is filled by a taker BUYING the other side Y (NOVIG_API.md §7). If takers have just been buying Y hard (the money that
§71's trap rule watches), the next Y-buyer is more likely to be informed too, and a bid on X is what that buyer hits. This re-quotes a
simulated bid every 10 minutes from 24 h before Novig's close (as Vigilant's maker does, §70.3), on both sides of every two-outcome market
in Novig's published trades, and splits every 10-minute interval by what the app can see when it (re)posts:

  flow    dollars takers paid for the OTHER side in the 15 min before (what fills our bid), and for OUR side;
  drop    how far our side's price fell over the hour before (Novig's level an hour ago minus now, in ¢);
  left    hours before the close; kind; the bid's price band.

For each slice: intervals quoted, fill rate (a maker fill on X below our bid during the interval: the "through" bound of §70.1), EV@close per
fill (Novig's close / bid − 1, test A close), value per quoted interval (fill rate × EV per fill), and ROI at settlement where the last trade
shows a winner (game lines mostly). The fair is §70.1's stand-in: w=0 Novig's own price then (median of its last 7 trades), w=0.25 a fair
that already knows a quarter of the move to the close (a sharp book leading Novig). Margin 4% (Vigilant's default).

    pip install pandas numpy
    python3 tools/research/novig_toxic_flow_study.py [--cache DIR] [--from 2026-08-03] [--to 2026-10-02]
"""
import argparse, json, os, sys, urllib.request
import numpy as np
import pandas as pd

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import novig_size_study as s  # noqa: E402
from novig_maker_study import prep, snap_down  # noqa: E402

rng = np.random.default_rng(721)
MARGIN = 0.04
STEP_H = 10 / 60
K_FAIR = 7
WS = (0.0, 0.25)


def simulate(d, close_a, a_won):
    out = []
    for mid_, g in d.groupby('marketId', sort=False):
        g = g.sort_values('t')
        cut = g.cutH.iat[0]
        closeA = close_a[mid_]
        won = a_won.get(mid_, np.nan)
        tk = g[g.side == 'TAKER']
        tt, tpa, tisA, tp, tc = (tk.t.to_numpy(float), tk.pA.to_numpy(float), tk.isA.to_numpy(bool), tk.p.to_numpy(float),
                                 tk.cost.to_numpy(float))
        if len(tt) < 5:
            continue
        grid = np.arange(cut - 0.5 - 24, cut - 0.5, STEP_H)
        jj = np.searchsorted(tt, grid)
        ok = jj >= 3
        if not ok.any():
            continue
        grid, jj = grid[ok], jj[ok]
        nowA = np.array([np.median(tpa[max(0, j - K_FAIR):j]) for j in jj])
        # Novig's level for A an hour before each quote (median of trades 45-75 min before), for the drop
        hourA = np.full(len(grid), np.nan)
        for i, t0 in enumerate(grid):
            a, b = np.searchsorted(tt, t0 - 1.25), np.searchsorted(tt, t0 - 0.75)
            if b - a >= 3:
                hourA[i] = np.median(tpa[a:b])
        # taker dollars per side in the 15 min before each quote
        cA = np.concatenate([[0], np.cumsum(np.where(tisA, tc, 0))])
        cB = np.concatenate([[0], np.cumsum(np.where(~tisA, tc, 0))])
        j15 = np.searchsorted(tt, grid - 0.25)
        flowA, flowB = cA[jj] - cA[j15], cB[jj] - cB[j15]
        kind_, league = g.kind.iat[0], g.league.iat[0]
        for x in (True, False):
            mk = g[(g.side == 'MAKER') & (g.isA == x)]
            mt, mp = mk.t.to_numpy(float), mk.p.to_numpy(float)
            close = closeA if x else 1 - closeA
            xwon = won if x else (1 - won if won == won else np.nan)
            xt, xp = tt[tisA == x], tp[tisA == x]
            offer = np.full(len(grid), np.nan)
            if len(xt):
                li = np.searchsorted(xt, grid) - 1
                lc = np.clip(li, 0, None)
                offer = np.where((li >= 0) & (xt[lc] >= grid - 2), xp[lc], np.nan)
            nowX = nowA if x else 1 - nowA
            hourX = hourA if x else 1 - hourA
            drop = 100 * (hourX - nowX)  # positive: our side got cheaper over the hour
            other = flowB if x else flowA
            ours = flowA if x else flowB
            k = np.searchsorted(grid, mt, side='right') - 1
            inwin = (k >= 0) & (mt < grid[-1] + STEP_H)
            for w in WS:
                fA = nowA + w * (closeA - nowA)
                fx = fA if x else 1 - fA
                b = snap_down(fx / (1 + MARGIN))
                post = np.isnan(offer) | (b < offer - 1e-9)
                filled = np.zeros(len(grid), bool)
                hit = inwin & (mp < b[np.clip(k, 0, len(grid) - 1)] - 1e-9)
                filled[np.unique(k[hit])] = True
                filled &= post
                for i in np.nonzero(post)[0]:
                    out.append((mid_, w, x, kind_, league, cut - 0.5 - grid[i], b[i], close, xwon, filled[i], other[i], ours[i], drop[i]))
    return pd.DataFrame(out, columns=['marketId', 'w', 'isA', 'kind', 'league', 'left', 'b', 'close', 'won', 'filled', 'other', 'ours', 'drop'])


def stat(x):
    """Fill rate, EV@close per fill (95% over markets), value per quoted interval, ROI per fill where results exist."""
    if len(x) < 200:
        return None
    f = x[x.filled]
    if len(f) < 30:
        return f'quoted {len(x):>9,}  fills {len(f):>6,}'
    f = f.assign(ev=100 * (f.close / f.b - 1), roi=100 * (f.won / f.b - 1))
    g = f.groupby('marketId').ev.agg(['sum', 'size'])
    sm, n, k = g['sum'].values, g['size'].values, len(g)
    boot = [sm[i].sum() / n[i].sum() for i in (rng.integers(0, k, k) for _ in range(300))]
    won = f.dropna(subset=['won'])
    roi = '-'
    if len(won) >= 50:
        gw = won.groupby('marketId').roi.agg(['sum', 'size'])
        sw, nw, kw = gw['sum'].values, gw['size'].values, len(gw)
        bw = [sw[i].sum() / nw[i].sum() for i in (rng.integers(0, kw, kw) for _ in range(300))]
        roi = f'{sw.sum() / nw.sum():+6.2f} [{np.percentile(bw, 2.5):+.1f}, {np.percentile(bw, 97.5):+.1f}] (n={len(won):,})'
    fill = len(f) / len(x)
    ev = sm.sum() / n.sum()
    return (f'quoted {len(x):>9,}  fill {100 * fill:5.1f}%  EV@close/fill {ev:+5.2f} [{np.percentile(boot, 2.5):+.2f}, '
            f'{np.percentile(boot, 97.5):+.2f}]  per quote {fill * ev:+.3f}%  ROI/fill {roi}')


def show(label, x):
    r = stat(x)
    if r:
        print(f'  {label:<46} {r}')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--from', dest='start', default=None)
    ap.add_argument('--to', dest='end', default=None)
    ap.add_argument('--cache', default=os.path.join(os.environ.get('TMPDIR', '/tmp'), 'novig-trades'))
    ap.add_argument('--save', default=None)
    a = ap.parse_args()
    with urllib.request.urlopen(f'{s.DATA}/index.json', timeout=30) as r:
        days = [x for x in json.load(r)['dates'] if (not a.start or x >= a.start) and (not a.end or x <= a.end)]
    print(f'{len(days)} days, {days[0]} .. {days[-1]}')
    d, close_a, a_won = prep(s.load(days, a.cache))
    q = simulate(d, close_a, a_won)
    if a.save:
        q.to_pickle(a.save)
    print(f'{len(q):,} quoted 10-min intervals in {q.marketId.nunique():,} markets')
    for w in WS:
        z = q[q.w == w]
        print(f'\n===== fair stand-in w={w} (margin 4%, re-quoted every 10 min from 24 h before the close) =====')
        for kd in ('game', 'prop', 'period', 'teamtotal'):
            y = z[z.kind == kd]
            if len(y) == 0:
                continue
            print(f'\n-- {kd}')
            show('all', y)
            for lo, hi in ((0, 1), (1, 100), (100, 1000), (1000, 1e12)):
                show(f'other side bought ${lo:g}-{hi:g} in 15 min', y[(y.other >= lo) & (y.other < hi)])
            for lo, hi in ((0, 1), (1, 100), (100, 1e12)):
                show(f'OUR side bought ${lo:g}-{hi:g} in 15 min', y[(y.ours >= lo) & (y.ours < hi)])
            for lo, hi in ((-99, -2), (-2, -0.5), (-0.5, 0.5), (0.5, 2), (2, 99)):
                show(f'our side moved {-hi:+g}..{-lo:+g}c over the hour', y[(y.drop >= lo) & (y.drop < hi)])
            show('guard: other >= $100 AND drop >= 1c', y[(y.other >= 100) & (y.drop >= 1)])
            show('rest (guard not firing)', y[~((y.other >= 100) & (y.drop >= 1))])
            for lo, hi in ((0, 0.1), (0.1, 0.2), (0.2, 0.35), (0.35, 0.5), (0.5, 0.65), (0.65, 1)):
                show(f'bid {lo:.2f}-{hi:.2f}', y[(y.b >= lo) & (y.b < hi)])
            for lo, hi in ((0, 1), (1, 3), (3, 6), (6, 12), (12, 25)):
                show(f'{lo}-{hi} h before the close', y[(y.left >= lo) & (y.left < hi)])


if __name__ == '__main__':
    main()
