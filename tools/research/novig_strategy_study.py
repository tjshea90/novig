#!/usr/bin/env python3
"""Where does money get made on Novig, and by whom? RESEARCH.md §69, re-runnable.

Tj, 2026-10-03: "Novig has betting history with liquidity ... can these be used to find successful strategies for betting? The goal is to use
novig to make as much money as possible." This measures, on Novig's own published trades (NOVIG_API.md §10, public, no key), who beats Novig's
closing price and who doesn't, split the ways a strategy could act on:
  - TAKER (hit the book: what Vigilant's bets are) vs MAKER (the resting order that was hit: what a posted bid would be)
  - kind of market: game lines, player props, team totals, period lines
  - how long before the close the trade was made
  - the price of the side bought (favorites vs long shots)
  - league
and checks CLV against real results where the market's last traded price says who won (>= 0.97 / <= 0.03).

    pip install pandas numpy
    python3 tools/research/novig_strategy_study.py [--from 2026-08-03] [--to 2026-10-01] [--cache DIR]

Uses novig_size_study.load (day files cached as pickles). The close is test A's (§62): a market's median taker price in the 30 min before
(last trade − the league's longest game), so always pregame, about 1-2 h before the start; trades in the 30 min before that are left out.
Every number: mean over orders (takers) or fills (makers), 95% interval bootstrapped over markets.
"""
import argparse, json, os, sys, urllib.request
import numpy as np
import pandas as pd

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import novig_size_study as s  # noqa: E402

rng = np.random.default_rng(69)
FUTURES = ('WINNER', 'SERIES', 'ROUND', 'CHAMPION', 'MVP', 'AWARD')


def kind(mt: str) -> str:
    if any(f in mt for f in FUTURES):
        return 'futures'
    if mt in ('MONEY', 'SPREAD', 'TOTAL') or mt.startswith('MONEYLINE_3_WAY'):
        return 'game line'
    if mt in ('TEAM_TOTAL',):
        return 'team total'
    if mt.endswith('_1H') or mt.startswith('FIRST_INNING') or mt.endswith('_1Q') or mt.endswith('_F5') or 'FIRST_HALF' in mt or 'FIRST_FIVE' in mt:
        return 'period line'
    if 'CORNERS' in mt or mt == 'BOTH_TEAMS_TO_SCORE':
        return 'other game prop'
    return 'player prop'


def ci(x, col):
    g = x.groupby('marketId')[col].agg(['sum', 'size'])
    sm, n, k = g['sum'].values, g['size'].values, len(g)
    if k < 5:
        return f'(n={n.sum()}, {k} markets)'
    boot = [sm[i].sum() / n[i].sum() for i in (rng.integers(0, k, k) for _ in range(500))]
    return f'{sm.sum() / n.sum():+.2f} [{np.percentile(boot, 2.5):+.2f}, {np.percentile(boot, 97.5):+.2f}]'


def line(label, x):
    if len(x) == 0:
        return
    won = x.dropna(subset=['won'])
    roi = ci(won, 'roi') if len(won) else '-'
    print(f'  {label:<34} CLV¢ {ci(x, "clv¢")}  EV@close% {ci(x, "ev%")}  ROI% {roi}  n={len(x):,} mk={x.marketId.nunique():,} (results {len(won):,})')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--from', dest='start', default=None)
    ap.add_argument('--to', dest='end', default=None)
    ap.add_argument('--cache', default=os.path.join(os.environ.get('TMPDIR', '/tmp'), 'novig-trades'))
    a = ap.parse_args()
    with urllib.request.urlopen(f'{s.DATA}/index.json', timeout=30) as r:
        days = [x for x in json.load(r)['dates'] if (not a.start or x >= a.start) and (not a.end or x <= a.end)]
    print(f'{len(days)} days, {days[0]} .. {days[-1]}')
    d = s.load(days, a.cache)
    d['kind'] = d.marketType.map(lambda m: kind(str(m)))
    d = d[d.kind != 'futures']
    print(f'{len(d):,} straight-trade rows in {d.marketId.nunique():,} two-outcome markets')

    # Who won: the market's last traded price (in play, near the end) for outcome A.
    last = d.sort_values('ts').groupby('marketId').pA.last()
    a_won = pd.Series(np.where(last >= 0.97, 1.0, np.where(last <= 0.03, 0.0, np.nan)), index=last.index)

    # The close (test A of §62).
    end = d[d.side == 'TAKER'].groupby('marketId').ts.max()
    d = d.assign(cut=d.marketId.map(end) - pd.to_timedelta(d.league.map(s.LONGEST_GAME_H), unit='h'))
    win = d[(d.side == 'TAKER') & (d.ts < d.cut) & (d.ts >= d.cut - pd.Timedelta(minutes=30))]
    c = win.groupby('marketId').pA.agg(['median', 'size'])
    close_a = c[c['size'] >= 3]['median']
    pre = d[(d.ts < d.cut - pd.Timedelta(minutes=30)) & d.marketId.isin(close_a.index)].copy()
    pre['close'] = np.where(pre.isA, pre.marketId.map(close_a), 1 - pre.marketId.map(close_a))
    aw = pre.marketId.map(a_won)
    pre['won'] = np.where(pre.isA, aw, 1 - aw)
    pre['hours'] = (pre.cut - pd.Timedelta(minutes=30) - pre.ts).dt.total_seconds() / 3600

    # One row per taker order (an order can fill against several makers); makers are their fills.
    t = pre[pre.side == 'TAKER'].groupby(['ts', 'marketId', 'outcomeId'], as_index=False).agg(
        cost=('cost', 'sum'), qty=('qty', 'sum'), close=('close', 'first'), won=('won', 'first'), league=('league', 'first'),
        kind=('kind', 'first'), hours=('hours', 'first'))
    t['p'] = t.cost / t.qty
    m = pre[pre.side == 'MAKER'].copy()
    for x in (t, m):
        x['clv¢'] = 100 * (x.close - x.p)
        x['ev%'] = 100 * (x.close / x.p - 1)
        x['roi%'] = 100 * (x.won / x.p - 1)
        x.rename(columns={'roi%': 'roi'}, inplace=True)
    print(f'{len(t):,} taker orders, {len(m):,} maker fills before the close; results known for {t.won.notna().mean():.0%} of takers')
    print('CLV¢ = Novig\'s close minus the price paid (cents of probability); EV@close% = close/price − 1; ROI% = flat-stake return at settlement.')

    print('\n== 1. By kind of market (all sizes)')
    for k, _ in t.groupby('kind'):
        line(f'TAKER {k}', t[t.kind == k])
        line(f'MAKER {k}', m[m.kind == k])

    print('\n== 2. Small orders only (under $100: what a retail bettor places or posts)')
    for k, _ in t.groupby('kind'):
        line(f'TAKER {k} <$100', t[(t.kind == k) & (t.cost < 100)])
        line(f'MAKER {k} <$100', m[(m.kind == k) & (m.cost < 100)])

    print('\n== 3. By time before the close (game lines and player props)')
    bins = [(0, 1), (1, 3), (3, 6), (6, 12), (12, 24), (24, 72), (72, 1e9)]
    for k in ('game line', 'player prop'):
        for lo, hi in bins:
            lab = f'{lo}-{hi}h' if hi < 1e8 else f'{lo}h+'
            line(f'TAKER {k} {lab}', t[(t.kind == k) & (t.hours >= lo) & (t.hours < hi)])
            line(f'MAKER {k} {lab}', m[(m.kind == k) & (m.hours >= lo) & (m.hours < hi)])

    print('\n== 4. By the price of the side bought (all kinds)')
    for lo, hi in [(0, .2), (.2, .35), (.35, .5), (.5, .65), (.65, .8), (.8, 1.01)]:
        line(f'TAKER price {lo:.2f}-{min(hi, 1):.2f}', t[(t.p >= lo) & (t.p < hi)])
        line(f'MAKER price {lo:.2f}-{min(hi, 1):.2f}', m[(m.p >= lo) & (m.p < hi)])

    print('\n== 5. By league (player props, then game lines)')
    for k in ('player prop', 'game line'):
        for lg, x in t[t.kind == k].groupby('league'):
            if x.marketId.nunique() >= 30:
                line(f'TAKER {k} {lg}', x)
                line(f'MAKER {k} {lg}', m[(m.kind == k) & (m.league == lg)])

    print('\n== 6. Does CLV match results? (takers, by CLV band)')
    for lo, hi in [(-100, -2), (-2, -0.5), (-0.5, 0.5), (0.5, 2), (2, 100)]:
        line(f'TAKER CLV {lo:+}¢..{hi:+}¢', t[(t['clv¢'] >= lo) & (t['clv¢'] < hi)])


if __name__ == '__main__':
    main()
