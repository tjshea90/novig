#!/usr/bin/env python3
"""Do Novig prices drift toward favorites before the start, and how much do they move late? RESEARCH.md §73, re-runnable.

Tj, 2026-10-03, with another AI's report: "If the information is accurate, research and see if any of the information can improve the logic,
accuracy, or profitability of vigilant". The report claims (from an SBR forum post on MLB 2015-18 sportsbook moneylines, n=9,813) that "in the
last 2 hours lines drift toward favorites (avg −3.4¢)", so "bet favorites early, underdogs late", and that on prediction markets "the final two
hours often move less than one tick". Both are testable on Novig's own trades (NOVIG_API.md §10).

For every two-outcome pregame market with a close (test A, §62: the median taker price in the 30 min before the latest possible start), the
side priced over 0.5 at T hours before the close is the favorite; its drift = close − its price at T (the median of the trades within ±15 min of
T), in cents (a positive drift = the favorite shortened). Also: how far prices move from T to the close, in Novig grid steps (0.5¢ between 0.05
and 0.95). Every interval: 95% bootstrap over markets.

    pip install pandas numpy
    python3 tools/research/novig_drift_study.py [--cache DIR]
"""
import argparse, json, os, sys, urllib.request
import numpy as np
import pandas as pd

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import novig_size_study as s  # noqa: E402
from novig_maker_study import prep  # noqa: E402

rng = np.random.default_rng(73)
LEADS = [0.5, 1, 2, 3, 6, 12, 24]


def ci(v):
    v = np.asarray(v, float)
    v = v[~np.isnan(v)]
    if len(v) < 30:
        return f'{np.mean(v) if len(v) else float("nan"):+.2f} (n={len(v)})'
    b = [rng.choice(v, len(v)).mean() for _ in range(400)]
    return f'{v.mean():+.2f} [{np.percentile(b, 2.5):+.2f}, {np.percentile(b, 97.5):+.2f}]'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--cache', default=os.path.join(os.environ.get('TMPDIR', '/tmp'), 'novig-trades'))
    a = ap.parse_args()
    with urllib.request.urlopen(f'{s.DATA}/index.json', timeout=30) as r:
        days = json.load(r)['dates']
    d, close_a, a_won = prep(s.load(days, a.cache))
    tk = d[d.side == 'TAKER']
    rows = []
    for mid_, g in tk.groupby('marketId', sort=False):
        t, pa = g.t.to_numpy(float), g.pA.to_numpy(float)
        o = np.argsort(t)
        t, pa = t[o], pa[o]
        close_start = g.cutH.iat[0] - 0.5
        ca = close_a[mid_]
        for h in LEADS:
            at = close_start - h
            sel = (t >= at - 0.25) & (t <= at + 0.25)
            if sel.sum() < 2:
                continue
            p = float(np.median(pa[sel]))
            fav_a = p >= 0.5
            fav_p = p if fav_a else 1 - p
            fav_close = ca if fav_a else 1 - ca
            won = a_won.get(mid_, np.nan)
            fav_won = won if fav_a else (1 - won if won == won else np.nan)
            rows.append((mid_, g.league.iat[0], g.kind.iat[0], h, fav_p, fav_close, fav_won))
    x = pd.DataFrame(rows, columns=['marketId', 'league', 'kind', 'lead', 'p', 'close', 'won'])
    x['drift'] = 100 * (x.close - x.p)
    step = np.where((x.p <= 0.05) | (x.p >= 0.95), 0.1, 0.5)
    x['steps'] = (x.drift.abs() / step).round(6)
    print(f'{len(days)} days; {x.marketId.nunique():,} markets with a price near each lead time\n')

    print('## The favorite\'s drift to the close (¢; + = the favorite shortened), by hours before the close')
    for kd in ('game line', 'player prop', 'period line'):
        y = x[x.kind == kd]
        print(f'\n-- {kd}')
        for h in LEADS:
            z = y[y.lead == h]
            if len(z) < 30:
                continue
            print(f'  {h:>4} h  all favorites {ci(z.drift):<24} n={len(z):,}   moved < 1 step: {100 * (z.steps < 1).mean():3.0f}%   '
                  f'median |move| {z.drift.abs().median():.2f}¢')
        z = y[y.lead == 2]
        for lo, hi in ((0.5, 0.6), (0.6, 0.7), (0.7, 0.8), (0.8, 1.0)):
            w = z[(z.p >= lo) & (z.p < hi)]
            if len(w) >= 30:
                print(f'     2 h, favorite at {lo:.1f}-{hi:.1f}: drift {ci(w.drift):<24} n={len(w):,}')
        # The report's dog rule: underdogs of +180 or longer (price ≤ 0.357) at T-2h moved toward the favorite 54% of the time.
        w = z[z.p >= 1 - 0.357]
        if len(w) >= 30:
            print(f'     2 h, underdog +180 or longer: moved toward the favorite {100 * (w.drift > 0).mean():.0f}%, away {100 * (w.drift < 0).mean():.0f}%, '
                  f'unchanged {100 * (w.drift == 0).mean():.0f}%  (n={len(w):,})')
    print('\n## Game lines by league, 2 h before the close')
    y = x[(x.kind == 'game line') & (x.lead == 2)]
    for lg, z in y.groupby('league'):
        if len(z) >= 60:
            print(f'  {lg:<10} favorite drift {ci(z.drift):<24} n={len(z):,}')
    print('\n## Does the drift show in results? Favorites bought at 2 h vs at the close (game lines with a result; ROI %)')
    z = y.dropna(subset=['won'])
    print(f'  bought at 2 h: ROI {ci(100 * (z.won / z.p - 1))}   at the close: {ci(100 * (z.won / z.close - 1))}   n={len(z):,}')


if __name__ == '__main__':
    main()
