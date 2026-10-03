#!/usr/bin/env python3
"""Are Novig's "gift" prices traps? RESEARCH.md §71, re-runnable.

Tj, 2026-10-03: "some of my 'gift' positive EV bets moved against me dramatically, and I think they were made by sharp bettors with
information not yet reflected by other sports books. See if there is a way to find these trap bets and avoid them."

On Novig every price a taker gets is someone else's resting bid on the other side (NOVIG_API.md §7): a "gift" on X is a bid on Y that sits
above where Y has been trading. This measures, on Novig's own published trades (NOVIG_API.md §10, public, no key), what became of every
taker who bought X at a price well under X's own recent level on Novig (the gift), against Novig's close (test A, §62), split by what the
app can see live before it bets (the public /trades route and the book): whether the other side was being BOUGHT just before (flow-led:
takers pushing Y up) or the price just appeared in the book (quote-led: nobody traded), how big the gift is, how far from the close, the
kind of market, and the size of the order the gift came from.

    pip install pandas numpy
    python3 tools/research/novig_trap_study.py [--from 2026-08-03] [--to 2026-10-02] [--cache DIR]

CLV¢ = Novig's close minus the price paid, in cents; kept = CLV / the gift (100% = the whole gift was real, 0 or less = a trap).
Every number: mean over taker orders, 95% interval bootstrapped over markets.
"""
import argparse, json, os, sys, urllib.request
import numpy as np
import pandas as pd

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import novig_size_study as s  # noqa: E402
from novig_strategy_study import kind  # noqa: E402

rng = np.random.default_rng(71)


def ci(x, col):
    g = x.groupby('marketId')[col].agg(['sum', 'size'])
    sm, n, k = g['sum'].values, g['size'].values, len(g)
    if k < 5:
        return f'{sm.sum() / max(n.sum(), 1):+.2f} (only {k} markets)'
    boot = [sm[i].sum() / n[i].sum() for i in (rng.integers(0, k, k) for _ in range(400))]
    return f'{sm.sum() / n.sum():+.2f} [{np.percentile(boot, 2.5):+.2f}, {np.percentile(boot, 97.5):+.2f}]'


def line(label, x):
    if len(x) < 20:
        return
    won = x.dropna(subset=['won'])
    roi = ci(won, 'roi') if len(won) >= 20 else '-'
    kept = 100 * x['clv¢'].sum() / max(x['gift¢'].sum(), 1e-9) if 'gift¢' in x else float('nan')
    print(f'  {label:<44} CLV¢ {ci(x, "clv¢"):<24} gift¢ {x["gift¢"].mean():5.2f}  kept {kept:+5.0f}%  ROI% {roi:<24} n={len(x):,} mk={x.marketId.nunique():,}')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--from', dest='start', default=None)
    ap.add_argument('--to', dest='end', default=None)
    ap.add_argument('--cache', default=os.path.join(os.environ.get('TMPDIR', '/tmp'), 'novig-trades'))
    ap.add_argument('--save', default=None, help='pickle every taker order with its gift and flow here, for more slicing')
    a = ap.parse_args()
    with urllib.request.urlopen(f'{s.DATA}/index.json', timeout=30) as r:
        days = [x for x in json.load(r)['dates'] if (not a.start or x >= a.start) and (not a.end or x <= a.end)]
    print(f'{len(days)} days, {days[0]} .. {days[-1]}')
    d = s.load(days, a.cache)
    d['kind'] = d.marketType.map(lambda m: kind(str(m)))
    d = d[d.kind != 'futures']

    last = d.sort_values('ts').groupby('marketId').pA.last()
    a_won = pd.Series(np.where(last >= 0.97, 1.0, np.where(last <= 0.03, 0.0, np.nan)), index=last.index)
    end = d[d.side == 'TAKER'].groupby('marketId').ts.max()
    d = d.assign(cut=d.marketId.map(end) - pd.to_timedelta(d.league.map(s.LONGEST_GAME_H), unit='h'))
    win = d[(d.side == 'TAKER') & (d.ts < d.cut) & (d.ts >= d.cut - pd.Timedelta(minutes=30))]
    c = win.groupby('marketId').pA.agg(['median', 'size'])
    close_a = c[c['size'] >= 3]['median']
    pre = d[(d.ts < d.cut - pd.Timedelta(minutes=30)) & d.marketId.isin(close_a.index)]

    # One row per taker order; the maker rows of the same fill tell how big the order it hit was.
    tk = pre[pre.side == 'TAKER'].groupby(['marketId', 'ts', 'outcomeId'], as_index=False).agg(
        cost=('cost', 'sum'), qty=('qty', 'sum'), isA=('isA', 'first'), league=('league', 'first'), kind=('kind', 'first'),
        cut=('cut', 'first'))
    mk = pre[pre.side == 'MAKER'].groupby(['marketId', 'ts'], as_index=False).agg(makerMax=('cost', 'max'), makers=('cost', 'size'))
    tk = tk.merge(mk, on=['marketId', 'ts'], how='left')
    tk['p'] = tk.cost / tk.qty
    tk['pA'] = np.where(tk.isA, tk.p, 1 - tk.p)
    tk['close'] = np.where(tk.isA, tk.marketId.map(close_a), 1 - tk.marketId.map(close_a))
    aw = tk.marketId.map(a_won)
    tk['won'] = np.where(tk.isA, aw, 1 - aw)
    tk['hours'] = (tk.cut - pd.Timedelta(minutes=30) - tk.ts).dt.total_seconds() / 3600
    tk = tk.sort_values(['marketId', 'ts']).reset_index(drop=True)
    print(f'{len(tk):,} pregame taker orders in {tk.marketId.nunique():,} two-outcome markets with a close')

    # Novig's own level for A over the hour before (every trade both sides, as a price for A), and the flow on each side in the 15 min before.
    g = tk.set_index('ts').groupby('marketId', sort=False)
    tk['lvlA60'] = g.pA.rolling('60min', closed='left').median().values
    tk['nPrev60'] = g.pA.rolling('60min', closed='left').count().values
    tk['costA'] = np.where(tk.isA, tk.cost, 0.0)
    tk['costB'] = np.where(tk.isA, 0.0, tk.cost)
    g = tk.set_index('ts').groupby('marketId', sort=False)
    fa = g.costA.rolling('15min', closed='left').sum().values
    fb = g.costB.rolling('15min', closed='left').sum().values
    tk['oppFlow15'] = np.where(tk.isA, fb, fa)
    tk['ownFlow15'] = np.where(tk.isA, fa, fb)
    tk['lvl'] = np.where(tk.isA, tk.lvlA60, 1 - tk.lvlA60)  # this order's side's own level
    tk['gift¢'] = 100 * (tk.lvl - tk.p)
    tk['clv¢'] = 100 * (tk.close - tk.p)
    tk['roi'] = 100 * (tk.won / tk.p - 1)
    base = tk[tk.nPrev60 >= 3]
    if a.save:
        tk.to_pickle(a.save)
    print(f'{len(base):,} orders with 3+ trades in the hour before (a level to compare with)')

    print('\n== 0. Every taker, by how far under its own side\'s recent level it bought (gift¢)')
    for lo, hi in [(-100, -1), (-1, 1), (1, 2), (2, 3), (3, 5), (5, 8), (8, 100)]:
        line(f'gift {lo:+}..{hi:+}¢', base[(base['gift¢'] >= lo) & (base['gift¢'] < hi)])

    gifts = base[(base['gift¢'] >= 2) & (base.p >= 0.08) & (base.p <= 0.92)]
    print(f'\n{len(gifts):,} gift orders (2¢+ under the level, price 0.08-0.92)')
    print('\n== 1. Where the gift came from: the other side being bought in the 15 min before (flow-led) or a quote nobody traded (quote-led)')
    for lab, x in [('quote-led (no trade on the other side)', gifts[gifts.oppFlow15 == 0]),
                   ('flow-led, other side bought < $100', gifts[(gifts.oppFlow15 > 0) & (gifts.oppFlow15 < 100)]),
                   ('flow-led, other side bought $100-1,000', gifts[(gifts.oppFlow15 >= 100) & (gifts.oppFlow15 < 1000)]),
                   ('flow-led, other side bought $1,000+', gifts[gifts.oppFlow15 >= 1000])]:
        line(lab, x)
        for k in ('player prop', 'game line', 'period line', 'team total'):
            line(f'   {k}', x[x.kind == k])

    print('\n== 2. By the time to the close (gifts)')
    for lo, hi in [(0, 1), (1, 3), (3, 6), (6, 12), (12, 24), (24, 1e9)]:
        x = gifts[(gifts.hours >= lo) & (gifts.hours < hi)]
        line(f'{lo}-{hi}h' if hi < 1e8 else f'{lo}h+', x)
        line(f'   quote-led', x[x.oppFlow15 == 0])
        line(f'   flow-led', x[x.oppFlow15 > 0])

    print('\n== 3. By the biggest resting order the gift was taken from (maker fill size)')
    for lo, hi in [(0, 25), (25, 100), (100, 500), (500, 2000), (2000, 1e12)]:
        line(f'maker ${lo}-{hi}' if hi < 1e11 else f'maker ${lo}+', gifts[(gifts.makerMax >= lo) & (gifts.makerMax < hi)])

    print('\n== 4. By the size of the gift and kind')
    for k in ('player prop', 'game line', 'period line', 'team total'):
        for lo, hi in [(2, 3), (3, 5), (5, 8), (8, 100)]:
            line(f'{k} gift {lo}-{hi}¢', gifts[(gifts.kind == k) & (gifts['gift¢'] >= lo) & (gifts['gift¢'] < hi)])

    print('\n== 5. By league (gifts, player props and game lines)')
    for k in ('player prop', 'game line'):
        for lg, x in gifts[gifts.kind == k].groupby('league'):
            if x.marketId.nunique() >= 30:
                line(f'{k} {lg}', x)

    print('\n== 6. The other side kept being bought right after (the next 15 min): what a follower of the move would see')
    tk2 = tk.set_index('ts')
    # flow in the 15 min AFTER each order (forward window via reversed time is awkward in pandas; use searchsorted per market)
    nxt = []
    for mid, x in gifts.groupby('marketId'):
        m = tk[tk.marketId == mid]
        tsv = m.ts.values
        opp = ~m.isA.values
        for _, r in x.iterrows():
            i0 = np.searchsorted(tsv, r.ts.to_datetime64(), side='right')
            i1 = np.searchsorted(tsv, (r.ts + pd.Timedelta(minutes=15)).to_datetime64(), side='right')
            side = m.isA.values[i0:i1] != r.isA
            nxt.append((r.name, m.cost.values[i0:i1][side].sum()))
    if nxt:
        f = pd.Series(dict(nxt))
        gifts = gifts.assign(oppNext15=f)
        line('other side bought $0 in the next 15 min', gifts[gifts.oppNext15 == 0])
        line('other side bought $1-500 next', gifts[(gifts.oppNext15 > 0) & (gifts.oppNext15 < 500)])
        line('other side bought $500+ next', gifts[gifts.oppNext15 >= 500])


if __name__ == '__main__':
    main()
