#!/usr/bin/env python3
"""Does big money on Novig know something? RESEARCH.md §62, re-runnable.

Tj, 2026-10-02: "sharp bets can be found on novig by analyzing liquidity ... if large
liquidity is offered on certain bets that it is probably betting syndicates or sharps".
This measures it on Novig's own published trades (NOVIG_API.md §10, public, no key):
every pregame straight trade's price against Novig's later price for the same outcome
(CLV, in cents of probability), split by size, for the TAKER (who hit the book) and the
MAKER (the resting liquidity that was hit), plus what a follower would have got.

    pip install pandas numpy
    python3 tools/research/novig_size_study.py [--from 2026-08-03] [--to 2026-09-30] [--cache DIR]

Day files are ~80 MB; only straight trades are kept (cached as pickles in --cache).
Tests, all with 95% intervals from a bootstrap over markets (trades in one market move together):
  A  every two-outcome market in the leagues below. The trades carry no start time, so a
     market's "close" is its median price in the 30 min before (last trade − the league's
     longest game): always pregame, about 1-2 h before the start.
  B  NFL game lines with the true close: kickoff snapped to the NFL slots, close = the
     median price in the 10 min before kickoff.
  C  follow: the next taker buy of the same outcome after a big trade (what a follower pays).
"""
import argparse, glob, json, os, subprocess, urllib.request
import numpy as np
import pandas as pd

DATA = 'https://data.novig.com/reporting/trade-data'
LONGEST_GAME_H = {'NFL': 4.0, 'NCAAF': 4.5, 'MLB': 4.0, 'WNBA': 2.75, 'NHL': 3.0, 'NBA': 3.0, 'NCAAB': 3.0,
                  'MLS': 2.25, 'EPL': 2.25, 'La Liga': 2.25, 'Bundesliga': 2.25, 'Serie A': 2.25, 'Ligue 1': 2.25,
                  'Champions League': 2.25, 'CFL': 3.75, 'NPB': 4.5, 'KBO': 4.5}
SIZES = [(0, 100), (100, 1000), (1000, 5000), (5000, 1e12), (10000, 1e12)]
rng = np.random.default_rng(62)


def load(days, cache):
    os.makedirs(cache, exist_ok=True)
    parts = []
    for day in days:
        pkl = os.path.join(cache, f's{day}.pkl')
        if not os.path.exists(pkl):
            raw = os.path.join(cache, f'raw{day}.csv')
            subprocess.run(['curl', '-sS', '-m', '300', '-o', raw, f'{DATA}/{day}/trades.csv'], check=True)
            d = pd.read_csv(raw)
            d = d[d.tradeType == 'STRAIGHT'].drop(columns=['tradeType', 'legs'])
            d['ts'] = pd.to_datetime(d.timestamp, utc=True, format='ISO8601')
            d.drop(columns=['timestamp']).to_pickle(pkl)
            os.remove(raw)
        parts.append(pd.read_pickle(pkl))
    d = pd.concat(parts, ignore_index=True)
    d = d[d.league.isin(LONGEST_GAME_H)].copy()
    d['p'] = d.cost / d.qty  # price paid per $1 of payout, for the outcome on this row
    two = d.groupby('marketId').outcomeId.nunique()
    d = d[d.marketId.map(two) == 2]
    first = d.groupby('marketId').outcomeId.min()
    d['isA'] = d.outcomeId == d.marketId.map(first)
    d['pA'] = np.where(d.isA, d.p, 1 - d.p)  # every row as a price for outcome A
    return d


def with_close(d, close_a):
    d = d[d.marketId.isin(close_a.index)].copy()
    d['close'] = np.where(d.isA, d.marketId.map(close_a), 1 - d.marketId.map(close_a))
    d['clv'] = d.close - d.p
    return d


def takers(d):  # one taker order can fill against several makers: one row per order
    t = d[d.side == 'TAKER'].groupby(['ts', 'marketId', 'outcomeId'], as_index=False).agg(
        cost=('cost', 'sum'), qty=('qty', 'sum'), close=('close', 'first'), league=('league', 'first'),
        marketType=('marketType', 'first'))
    t['p'] = t.cost / t.qty
    t['clv'] = t.close - t.p
    return t


def ci(x, col='clv'):
    g = x.groupby('marketId')[col].agg(['sum', 'size'])
    s, n, k = g['sum'].values, g['size'].values, len(g)
    if k == 0:
        return 'none'
    boot = [s[i].sum() / n[i].sum() for i in (rng.integers(0, k, k) for _ in range(1000))]
    return (f'{100 * s.sum() / n.sum():+.2f}¢ [{100 * np.percentile(boot, 2.5):+.2f}, '
            f'{100 * np.percentile(boot, 97.5):+.2f}]  n={n.sum()} markets={k}')


def by_size(t, m, title):
    print(f'\n== {title}  (CLV: + = beat Novig\'s later price)')
    for lo, hi in SIZES:
        lab = f'${lo:,}+' if hi > 1e11 else f'${lo:,}-{hi:,}'
        print(f'  {lab:>13}  TAKER {ci(t[(t.cost >= lo) & (t.cost < hi)])}')
        print(f'  {"":>13}  MAKER {ci(m[(m.cost >= lo) & (m.cost < hi)])}')


def test_a(d):
    end = d[d.side == 'TAKER'].groupby('marketId').ts.max()
    d = d.assign(cut=d.marketId.map(end) - pd.to_timedelta(d.league.map(LONGEST_GAME_H), unit='h'))
    win = d[(d.side == 'TAKER') & (d.ts < d.cut) & (d.ts >= d.cut - pd.Timedelta(minutes=30))]
    c = win.groupby('marketId').pA.agg(['median', 'size'])
    pre = with_close(d[d.ts < d.cut - pd.Timedelta(minutes=30)], c[c['size'] >= 3]['median'])
    t, m = takers(pre), pre[pre.side == 'MAKER']
    by_size(t, m, 'A: every league, close ~1-2 h before the start')
    big = t[t.cost >= 10000]
    for lg, x in big.groupby('league'):
        print(f'     $10k+ takers, {lg:>6}: {ci(x)}')
    for mo, x in big.groupby(big.ts.dt.month):
        print(f'     $10k+ takers, month {mo}: {ci(x)}')
    return pre, t, m


def test_b(d):
    d = d[(d.league == 'NFL') & d.marketType.isin(['MONEY', 'SPREAD', 'TOTAL'])]
    if d.empty:
        return
    end = d[d.side == 'TAKER'].groupby('marketId').ts.max()
    days = pd.date_range(d.ts.min().normalize(), d.ts.max().normalize() + pd.Timedelta(days=1), tz='UTC')
    slots = pd.Series(sorted(day + pd.Timedelta(hours=h, minutes=mi)
                             for day in days for h, mi in [(13, 30), (17, 0), (20, 5), (0, 15)]))

    def kick(te):  # 20:05 and 20:25 starts both snap to 20:05: a close a little early is still pregame
        c = slots[slots <= te - pd.Timedelta(hours=2.75)]
        return c.iloc[-1] if len(c) else pd.NaT
    k = end.map(kick)
    dur = (end - k).dt.total_seconds() / 3600
    k = k[(dur > 2.75) & (dur < 4.0)]  # traded to near the end of the game
    d = d[d.marketId.isin(k.index)].assign(kick=lambda x: x.marketId.map(k))
    win = d[(d.side == 'TAKER') & (d.ts < d.kick) & (d.ts >= d.kick - pd.Timedelta(minutes=10))]
    c = win.groupby('marketId').pA.agg(['median', 'size'])
    pre = with_close(d[d.ts < d.kick - pd.Timedelta(minutes=10)], c[c['size'] >= 3]['median'])
    by_size(takers(pre), pre[pre.side == 'MAKER'], 'B: NFL game lines, true close (10 min before kickoff)')


def test_c(d, t, m):
    allt = d[d.side == 'TAKER'].groupby(['marketId', 'outcomeId', 'ts'], as_index=False).agg(
        cost=('cost', 'sum'), qty=('qty', 'sum'))
    allt['p'] = allt.cost / allt.qty
    nxt = allt.set_index(['marketId', 'outcomeId']).sort_index()

    def follow(sig, title):
        got = []
        for r in sig.itertuples():
            try:
                y = nxt.loc[(r.marketId, r.outcomeId)]
            except KeyError:
                continue
            y = y[(y.ts > r.ts + pd.Timedelta(seconds=0.5)) & (y.ts <= r.ts + pd.Timedelta(minutes=15))]
            if len(y):
                got.append((r.marketId, r.close, r.p, y.p.iloc[0], (y.ts.iloc[0] - r.ts).total_seconds()))
        f = pd.DataFrame(got, columns=['marketId', 'close', 'p', 'pf', 'lag'])
        if f.empty:
            return
        f['clv'] = f.close - f.pf
        print(f'  {title}: signal paid {100 * f.p.mean():.1f}¢, follower {100 * (f.pf - f.p).mean():+.2f}¢ more '
              f'(median {f.lag.median():.0f}s later); follower CLV {ci(f)}; EV at the close '
              f'{100 * (f.close / f.pf - 1).mean():+.2f}%')
    print('\n== C: follow (buy the same outcome at the next taker price, within 15 min)')
    follow(t[t.cost >= 10000], 'TAKER $10k+')
    follow(t[t.cost >= 2000], 'TAKER $2k+ ')
    mm = m[m.cost >= 2000].groupby(['ts', 'marketId', 'outcomeId'], as_index=False).agg(
        cost=('cost', 'sum'), qty=('qty', 'sum'), close=('close', 'first'))
    mm['p'] = mm.cost / mm.qty
    follow(mm, 'MAKER $2k+ (the "big liquidity" side)')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--from', dest='start', default=None)
    ap.add_argument('--to', dest='end', default=None)
    ap.add_argument('--cache', default=os.path.join(os.environ.get('TMPDIR', '/tmp'), 'novig-trades'))
    a = ap.parse_args()
    with urllib.request.urlopen(f'{DATA}/index.json', timeout=30) as r:
        days = [x for x in json.load(r)['dates'] if (not a.start or x >= a.start) and (not a.end or x <= a.end)]
    print(f'{len(days)} days, {days[0]} .. {days[-1]}')
    d = load(days, a.cache)
    print(f'{len(d):,} straight-trade rows in {d.marketId.nunique():,} two-outcome markets')
    pre, t, m = test_a(d)
    test_b(d)
    test_c(pre, t, m)


if __name__ == '__main__':
    main()
