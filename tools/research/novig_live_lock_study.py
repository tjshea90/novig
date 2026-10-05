#!/usr/bin/env python3
"""Can a two-leg lock on Novig's IN-GAME game lines be made to pay, with no knowledge of the future? RESEARCH.md §83, re-runnable.

Tj, 2026-10-05: "consider whether it would be plausible to make a live betting arbitrage system for the app ... track rapidly moving live odds
across live events on novig ... find when to place bets on one side of a live event and then when to place bets on the other side ... resulting in
guaranteed profit. It can utilize both make and take bets."

On an exchange the two outcomes of a market sum to 1, so buying A at the ask and B at the ask costs 1 + the spread (never a profit at one instant,
checked on live books in §83.3). A profit exists only if the price MOVES between the legs: buy A at a1, later buy B at b2, profit 1 - a1 - b2 - fees.
That is a bet on direction until the second leg fills. This script measures what a rule that does NOT know the future earns on Novig's own trades:
enter on a fixed clock, exit by a fixed rule, count everything.

Data: Novig's published trades (NOVIG_API.md §10). The file has no start time and no live flag (a taker's cost carries no fee: checked), so the live
window is INFERRED: game lines (MONEY, SPREAD, TOTAL) in leagues with a clock whose last trades are decided (price over .97 or under .03, so the last
trade is about the end of the game); the window is [last trade - WINDOW_H[league], last trade - 5 min], inside play for any game that lasted longer
than the window. Entries priced .10-.90 only. A TAKER row is one taker order (its average price); it names the outcome the taker bought, so it is an
executable ask for that outcome and a bid for the other (bid_A = 1 - ask_B). Prices come from the last print seen at the entry time (no older than
STALE s): the book itself is not in the file, so every number here is for a market that trades often enough to have a recent price.

Strategies, entered every ENTRY_EVERY s in the window on each outcome, per $1 of payout, with Novig's in-play fees (taker 0.03*P*(1-P); the maker of
a fill earns half of that fee as a credit):
  T   taker-taker: buy X at the last ask seen, then take the other side the first time 1 - a1 - b2 - fees >= M within H s; never: hold and mark at the
      mid (last ask and last bid seen), no exit cost: the most generous mark.
  F   the same, but at H the other side is bought anyway at the last ask seen ("forced lock": what a guarantee costs).
  M   maker-maker: a post-only bid on X at the best bid seen fills when a taker's price reaches it ('through' = strictly past it: the lower bound;
      'touch' = at it: the upper bound); then a bid on the other outcome at 1 - b - M waits H s; unfilled it is marked at the mid (M) or the other side
      is taken (MF, forced). Both credits count.

    pip install pandas numpy
    python3 tools/research/novig_live_lock_study.py [--from 2026-09-05] [--to 2026-10-03] [--cache DIR]
"""
import argparse, collections, json, os, subprocess, sys, urllib.request
import numpy as np
import pandas as pd

DATA = 'https://data.novig.com/reporting/trade-data'
WINDOW_H = {'NFL': 2.25, 'NCAAF': 2.25, 'MLB': 2.0, 'NHL': 1.75, 'WNBA': 1.5, 'NBA': 1.5, 'NCAAB': 1.25}
GAME_LINES = ('MONEY', 'SPREAD', 'TOTAL')
ENTRY_EVERY = 30.0   # s
STALE = 60.0         # s: a price older than this is not a price
rng = np.random.default_rng(83)


def fee(p):  # the taker's fee per $1 of payout at execution price p
    return 0.03 * p * (1 - p)


def load_day(day, cache):
    pkl = os.path.join(cache, f'k{day}.pkl')
    if os.path.exists(pkl):
        return pd.read_pickle(pkl)
    raw = os.path.join(cache, f'raw{day}.csv')
    if not os.path.exists(raw):
        subprocess.run(['curl', '-sS', '-m', '300', '-o', raw, f'{DATA}/{day}/trades.csv'], check=True)
    d = pd.read_csv(raw, usecols=['timestamp', 'outcomeId', 'marketId', 'league', 'marketType', 'tradeType', 'cost', 'qty', 'side'])
    d = d[(d.tradeType == 'STRAIGHT') & (d.side == 'TAKER') & d.marketType.isin(GAME_LINES) & d.league.isin(WINDOW_H)].copy()
    d['t'] = (pd.to_datetime(d.timestamp, utc=True, format='ISO8601') - pd.Timestamp('1970-01-01', tz='UTC')).dt.total_seconds()  # not astype(int64): its unit varies by pandas version
    d['p'] = d.cost / d.qty
    d = d[['t', 'marketId', 'outcomeId', 'league', 'marketType', 'p', 'qty']].sort_values('t').reset_index(drop=True)
    d.to_pickle(pkl)
    os.path.exists(raw) and os.remove(raw)
    return d


def last_seen(times, values, t):
    """(value at the latest time <= t, its age); (nan, inf) when there is none."""
    i = np.searchsorted(times, t, side='right') - 1
    return (np.nan, np.inf) if i < 0 else (values[i], t - times[i])


def entries(m, lo, hi, combos, acc, league, mid):
    """Run every entry of one market through every (H, M) combination, adding to acc[(H, M, strategy)][(market, league)] = [sum, n]."""
    a_id = m.outcomeId.min()
    isA = (m.outcomeId == a_id).to_numpy()
    t, p = m.t.to_numpy(), m.p.to_numpy()
    ask = {'A': (t[isA], p[isA]), 'B': (t[~isA], p[~isA])}
    for t0 in np.arange(lo, hi, ENTRY_EVERY):
        for X, Y in (('A', 'B'), ('B', 'A')):
            a1, age = last_seen(*ask[X], t0)
            if not (age <= STALE and 0.10 <= a1 <= 0.90):
                continue
            ty, py = ask[Y]
            tx, px = ask[X]
            askY, ageY = last_seen(ty, py, t0)
            bid_x = 1 - askY if ageY <= STALE else np.nan
            for H, M in combos:
                add = lambda strat, v, H=H, M=M: _add(acc, (H, M, strat), (mid, league), v)
                j0 = np.searchsorted(ty, t0, side='right'); j1 = np.searchsorted(ty, t0 + H, side='right')
                net = 1 - a1 - py[j0:j1] - fee(a1) - fee(py[j0:j1])
                hit = np.nonzero(net >= M)[0]
                mxe, _ = last_seen(tx, px, t0 + H)
                mye, _ = last_seen(ty, py, t0 + H)
                mxe = a1 if np.isnan(mxe) else mxe
                end_ask_y = (1 - mxe) if np.isnan(mye) else mye
                end_mid_x = mxe if np.isnan(mye) else (mxe + (1 - mye)) / 2
                if len(hit):
                    add('T', net[hit[0]]); add('F', net[hit[0]]); add('T_locked', 1.0)
                else:
                    add('T', end_mid_x - a1 - fee(a1)); add('F', 1 - a1 - end_ask_y - fee(a1) - fee(end_ask_y)); add('T_locked', 0.0)
                add('spread', (a1 + askY - 1) if ageY <= STALE else np.nan)
                # maker-maker
                if np.isnan(bid_x) or not (0.10 <= bid_x <= 0.90):
                    continue
                for mode in ('through', 'touch'):
                    thr = 1 - bid_x
                    f1 = (py[j0:j1] > thr + 1e-9) if mode == 'through' else (py[j0:j1] >= thr - 1e-9)
                    idx = np.nonzero(f1)[0]
                    if not len(idx):
                        add(f'Mfill_{mode}', 0.0)
                        continue
                    add(f'Mfill_{mode}', 1.0)
                    tf = ty[j0 + idx[0]]
                    c = 1 - bid_x - M
                    cr1 = 0.5 * fee(bid_x)
                    k0 = np.searchsorted(tx, tf, side='right'); k1 = np.searchsorted(tx, tf + H, side='right')
                    f2 = (px[k0:k1] > 1 - c + 1e-9) if mode == 'through' else (px[k0:k1] >= 1 - c - 1e-9)
                    if f2.any():
                        v = M + cr1 + 0.5 * fee(c)
                        add(f'M_{mode}', v); add(f'MF_{mode}', v); add(f'Mlock_{mode}', 1.0)
                    else:
                        mxf, _ = last_seen(tx, px, tf + H); myf, _ = last_seen(ty, py, tf + H)
                        mxf = bid_x if np.isnan(mxf) else mxf
                        end_ask = (1 - mxf) if np.isnan(myf) else myf
                        end_mid = mxf if np.isnan(myf) else (mxf + (1 - myf)) / 2
                        add(f'M_{mode}', end_mid - bid_x + cr1)
                        add(f'MF_{mode}', 1 - bid_x - end_ask - fee(end_ask) + cr1)
                        add(f'Mlock_{mode}', 0.0)


def _add(acc, key, mk, v):
    if v is None or (isinstance(v, float) and np.isnan(v)):
        return
    cell = acc[key][mk]
    cell[0] += v
    cell[1] += 1


def summarize(acc, key, league=None):
    cells = [(mk, c) for mk, c in acc[key].items() if (league is None or mk[1] == league) and c[1] > 0]
    if not cells:
        return None
    s = np.array([c[0] for _, c in cells]); n = np.array([c[1] for _, c in cells])
    mean = s.sum() / n.sum()
    bs = []
    for _ in range(500):
        i = rng.integers(0, len(cells), len(cells))
        bs.append(s[i].sum() / n[i].sum())
    return mean, np.percentile(bs, 2.5), np.percentile(bs, 97.5), int(n.sum()), len(cells)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--from', dest='a', default='2026-09-05')
    ap.add_argument('--to', dest='b', default='2026-10-03')
    ap.add_argument('--cache', default=os.path.join(os.environ.get('TMPDIR', '/tmp'), 'novig-trades'))
    ap.add_argument('--horizons', default='60,300')
    ap.add_argument('--margins', default='0.01,0.03')
    a = ap.parse_args()
    os.makedirs(a.cache, exist_ok=True)
    dates = json.load(urllib.request.urlopen(f'{DATA}/index.json'))['dates']
    days = [x for x in dates if a.a <= x <= a.b]
    combos = [(float(h), float(m)) for h in a.horizons.split(',') for m in a.margins.split(',')]
    acc = collections.defaultdict(lambda: collections.defaultdict(lambda: [0.0, 0]))
    nmk, leagues = 0, collections.Counter()
    for day in days:
        d = load_day(day, a.cache)
        for mid, m in d.groupby('marketId'):
            if m.outcomeId.nunique() != 2 or len(m) < 40:
                continue
            league = m.league.iloc[0]
            m = m.sort_values('t')
            pa = np.where(m.outcomeId == m.outcomeId.min(), m.p, 1 - m.p)
            tail = np.median(pa[-5:])
            if not (tail >= 0.97 or tail <= 0.03):
                continue
            end = m.t.iloc[-1]
            lo, hi = end - WINDOW_H[league] * 3600, end - 300
            inwin = m[(m.t >= lo - STALE) & (m.t <= hi + max(float(h) for h in a.horizons.split(',')) * 2)]
            if len(inwin) < 25:
                continue
            nmk += 1; leagues[league] += 1
            entries(inwin, lo, hi, combos, acc, league, mid)
        print(day, 'markets', nmk, file=sys.stderr, flush=True)
    print(f'\n{nmk} decided game-line markets in {len(days)} days {days[0]}..{days[-1]}: {dict(leagues)}')
    sp = summarize(acc, (combos[0][0], combos[0][1], 'spread'))
    print('spread seen (ask_X + ask_Y - 1), the cost of crossing both books: mean %.3f over %d entries' % (sp[0], sp[3]))
    for H, M in combos:
        print(f'\n--- horizon {H:.0f}s, lock target {M:.2f}: cents per $1 of payout, mean over entries [95% over markets]')
        for strat, label in (('T', 'T  taker-taker, unlocked marked at the mid'), ('F', 'F  taker-taker, forced lock at the horizon'),
                             ('M_through', 'M  maker-maker, through fills, unlocked marked at mid'), ('MF_through', 'MF maker-maker, through fills, forced lock'),
                             ('M_touch', 'M  maker-maker, touch fills, unlocked marked at mid'), ('MF_touch', 'MF maker-maker, touch fills, forced lock')):
            r = summarize(acc, (H, M, strat))
            if not r:
                continue
            extra = ''
            if strat == 'T':
                extra = '  locked %.0f%%' % (100 * summarize(acc, (H, M, 'T_locked'))[0])
            if strat.startswith('M_'):
                mode = strat.split('_')[1]
                fl = summarize(acc, (H, M, f'Mfill_{mode}')); lk = summarize(acc, (H, M, f'Mlock_{mode}'))
                extra = '  filled %.0f%% of entries, of the filled locked %.0f%%' % (100 * fl[0], 100 * lk[0]) if fl and lk else ''
            print(f'  {label:56s} {100 * r[0]:+6.2f}c [{100 * r[1]:+.2f}, {100 * r[2]:+.2f}]  entries={r[3]} markets={r[4]}{extra}')
        for lg in sorted(leagues):
            r = summarize(acc, (H, M, 'T'), lg); f = summarize(acc, (H, M, 'F'), lg)
            if r and f:
                print(f'      {lg:6s} T {100 * r[0]:+.2f}c  F {100 * f[0]:+.2f}c  ({r[3]} entries, {r[4]} markets)')


if __name__ == '__main__':
    main()
