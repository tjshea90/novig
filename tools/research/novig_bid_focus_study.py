#!/usr/bin/env python3
"""Which bids fill FAST, win OFTEN and still beat the close? RESEARCH.md §88.4.

Tj, 2026-10-05: "make an option for it to make auto bids for only the bets which have the maximum chance of being filled quickly and also are
decent chance for me to win the bet (remove longshots and keep favorites and small underdogs for my side of the bet to win) but remain positive EV
and the best chance at beating clv. Do whatever research is needed to achieve this."

It re-uses §70's simulation (novig_maker_study.simulate: a bid on a side at fair / (1 + margin), on Novig's grid, filled when a taker traded THROUGH
it, against Novig's 60 published days of trades) and slices its static posts by what Tj is choosing between:
  - the bid's price b (= the chance the side wins, roughly: b is the fair's price less the margin),
  - how long it takes to fill (10 min, 30 min, 1 h, until the close),
  - what a fill is worth at the close (EV@close, CLV in cents) and how often the fill wins at settlement (won, where the last trade shows a winner).
The "fast" yardstick is the share of posted bids that fill within 30 minutes and what those fills are worth. A fair stand-in w (0 = Novig's own price,
0.25 / 0.5 = a fair that already knows that share of the move to the close) brackets how good Vigilant's fair is (§70.1).

    pip install pandas numpy
    python3 tools/research/novig_bid_focus_study.py [--from 2026-09-20] [--to 2026-10-04] [--cache DIR] [--margin 4]
"""
import argparse, json, os, sys, urllib.request
import numpy as np
import pandas as pd

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import novig_size_study as s  # noqa: E402
import novig_maker_study as m  # noqa: E402

BANDS = [(0.0, 0.20), (0.20, 0.30), (0.30, 0.40), (0.40, 0.50), (0.50, 0.60), (0.60, 0.70), (0.70, 1.0)]
FAST_H = {'10 min': 10 / 60, '30 min': 0.5, '1 h': 1.0, '3 h': 3.0}


def cluster_ci(x, col):
    """Mean of col with a 95% interval, resampling markets (bids in one market are not independent)."""
    g = x.groupby('marketId')[col].agg(['sum', 'size'])
    sm, n, k = g['sum'].values, g['size'].values, len(g)
    if k < 10:
        return f'{sm.sum() / max(n.sum(), 1):+.2f} (n={n.sum()})'
    rng = np.random.default_rng(88)
    boot = [sm[i].sum() / n[i].sum() for i in (rng.integers(0, k, k) for _ in range(300))]
    return f'{sm.sum() / n.sum():+.2f} [{np.percentile(boot, 2.5):+.2f}, {np.percentile(boot, 97.5):+.2f}]'


def row(label, P, ttl_h):
    """One line: posted bids, the share that fill within ttl_h, and what the fills are worth."""
    if len(P) == 0:
        return
    f = P[P.thrH <= ttl_h].copy()
    fill = len(f) / len(P)
    if len(f) < 20:
        print(f'  {label:<34} posted {len(P):>6,}  fill {fill:>4.0%}  (under 20 fills)')
        return
    f['ev'] = 100 * (f.close / f.b - 1)
    f['clv'] = 100 * (f.close - f.b)
    f['won1'] = f.won
    won = f.dropna(subset=['won'])
    print(f'  {label:<34} posted {len(P):>6,}  fill {fill:>4.0%}  wins {100 * won.won.mean() if len(won) >= 30 else float("nan"):>3.0f}%  '
          f'EV@close% {cluster_ci(f, "ev")}  CLV¢ {cluster_ci(f, "clv")}  per posted bid {fill * f.ev.mean():+.2f}%')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--from', dest='start', default=None)
    ap.add_argument('--to', dest='end', default=None)
    ap.add_argument('--cache', default=os.path.join(os.environ.get('TMPDIR', '/tmp'), 'novig-trades'))
    ap.add_argument('--margin', type=float, default=4.0)
    ap.add_argument('--save', default=None)
    a = ap.parse_args()
    with urllib.request.urlopen(f'{s.DATA}/index.json', timeout=30) as r:
        days = [x for x in json.load(r)['dates'] if (not a.start or x >= a.start) and (not a.end or x <= a.end)]
    print(f'{len(days)} days, {days[0]} .. {days[-1]}')
    d, close_a, a_won = m.prep(s.load(days, a.cache))
    print(f'{d.marketId.nunique():,} markets with a close; simulating ...', flush=True)
    r, _q = m.simulate(d, close_a, a_won)
    if a.save:
        pd.to_pickle(r, a.save)
    post = r[(r.cross == 0) & (r.m == a.margin)]
    print(f'{len(post):,} simulated bids at {a.margin:g}% under the fair (bids that would have taken are left out)')
    print('fill = a taker traded THROUGH the bid within the time shown; wins = share of filled bids whose side won (where known);')
    print('EV@close / CLV¢ per FILLED bid (intervals resample markets); "per posted bid" = fill share x EV@close.')
    for w in m.WS:
        P = post[post.w == w]
        print(f'\n######## fair stand-in w={w}')
        for h in (1, 3, 6):
            print(f'\n== posted {h} h before the close, by the bid\'s price (= roughly the chance the side wins), fills within 30 min / until the close')
            for lo, hi in BANDS:
                B = P[(P.h == h) & (P.b >= lo) & (P.b < hi)]
                row(f'bid {lo:.2f}-{hi:.2f}  within 30 min', B, 0.5)
                row(f'bid {lo:.2f}-{hi:.2f}  until the close', B, 1e9)
        print('\n== by kind of market (posted 3 h before), fills within 1 h, by price band')
        for k in ('player prop', 'period line', 'team total', 'game line'):
            for lo, hi in BANDS:
                B = P[(P.h == 3) & (P.kind == k) & (P.b >= lo) & (P.b < hi)]
                row(f'{k} {lo:.2f}-{hi:.2f}', B, 1.0)
        print('\n== candidate rules (posted 3 h before, fills within 1 h)')
        for name, cond in [
            ('everything', P.b >= 0.0),
            ('bid 0.30-0.65 (no longshots, no heavy favorites)', (P.b >= 0.30) & (P.b < 0.65)),
            ('bid 0.35-0.65', (P.b >= 0.35) & (P.b < 0.65)),
            ('bid 0.40-0.65', (P.b >= 0.40) & (P.b < 0.65)),
            ('bid 0.25-0.50 (small underdogs, pick-ems)', (P.b >= 0.25) & (P.b < 0.50)),
            ('props+team totals, 0.30-0.65', P.kind.isin(['player prop', 'team total']) & (P.b >= 0.30) & (P.b < 0.65)),
            ('props+period+team totals, 0.30-0.65', P.kind.isin(['player prop', 'team total', 'period line']) & (P.b >= 0.30) & (P.b < 0.65)),
        ]:
            row(name, P[(P.h == 3) & cond], 1.0)


if __name__ == '__main__':
    main()
