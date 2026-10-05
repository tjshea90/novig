#!/usr/bin/env python3
"""Can a two-leg lock on Novig's IN-GAME game lines be made to pay, with no knowledge of the future? RESEARCH.md §83, re-runnable.

Tj, 2026-10-05: "consider whether it would be plausible to make a live betting arbitrage system for the app ... track rapidly moving live odds
across live events on novig ... find when to place bets on one side of a live event and then when to place bets on the other side ... resulting in
guaranteed profit. It can utilize both make and take bets."

On an exchange the two outcomes of a market sum to 1, so buying A at the ask and B at the ask costs 1 + the spread (never a profit at one instant,
checked on 73 live books in §83.3). A profit exists only if the price MOVES between the legs: buy A at a1, later buy B at b2, profit 1 - a1 - b2 - fees.
That is a bet on direction until the second leg fills. This script measures what a rule that does NOT know the future earns on Novig's own live
trades: enter on a fixed clock, exit by a fixed rule, count everything.

Data: Novig's published trades (NOVIG_API.md §10). The file has no start time and no live flag (a taker's cost carries no fee, checked), so the live
window is INFERRED: game lines (MONEY, SPREAD, TOTAL) in leagues with a clock, whose last trades are decided (price over .97 or under .03, so the last
trade is about the end of the game), and the window is [last trade - WINDOW_H[league], last trade - 5 min]: inside play for any game that lasted
longer than the window. Only entries priced .10-.90 count. A TAKER row is one taker order (its average price); it names the outcome the taker bought,
so it is an executable ask for that outcome and a bid for the other (bid_A = 1 - ask_B).

Strategies, each entered every ENTRY_EVERY s in the window on each outcome, per $1 of payout, fees as Novig charges them in play
(taker 0.03*P*(1-P); a maker earns half of the taker's fee on its fill):
  T  taker-taker: buy X at the last ask seen (no older than STALE s), then take the other side as soon as 1 - a1 - b2 - fees >= M within H s; if it
     never does, the position is held and marked at the mid (the last bid and ask seen), no exit cost: the most generous mark.
  F  the same, but at H the other side is bought anyway ("forced lock": what a guarantee costs), at the last ask seen.
  M  maker-maker: a PO bid on X at the best bid seen fills when a taker's price reaches it ('through' = strictly past: the lower bound; 'touch' = at it:
     the upper bound); then a bid on the other outcome at 1 - b - M waits H2 s; unfilled at H2 the position is marked at the mid (M) or locked by
     taking the other side (MF). Credits count.

    pip install pandas numpy
    python3 tools/research/novig_live_lock_study.py [--from 2026-09-05] [--to 2026-10-03] [--cache DIR]
"""
import argparse, json, os, subprocess, sys
import numpy as np
import pandas as pd

DATA = 'https://data.novig.com/reporting/trade-data'
WINDOW_H = {'NFL': 2.25, 'NCAAF': 2.25, 'MLB': 2.0, 'NHL': 1.75, 'WNBA': 1.5, 'NBA': 1.5, 'NCAAB': 1.25}
GAME_LINES = ('MONEY', 'SPREAD', 'TOTAL')
ENTRY_EVERY = 20.0   # s
STALE = 90.0         # s: an ask or bid older than this is not a price
rng = np.random.default_rng(83)


def fee(p):  # taker fee per $1 of payout at execution price p
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
    d['t'] = pd.to_datetime(d.timestamp, utc=True, format='ISO8601').astype('int64') / 1e9
    d['p'] = d.cost / d.qty
    d = d[['t', 'marketId', 'outcomeId', 'league', 'marketType', 'p', 'qty']].sort_values('t').reset_index(drop=True)
    d.to_pickle(pkl)
    if os.path.exists(raw):
        os.remove(raw)
    return d


def last_seen(times, values, t):
    """The value at the latest time <= t, and its age; (nan, inf) when there is none."""
    i = np.searchsorted(times, t, side='right') - 1
    if i < 0:
        return np.nan, np.inf
    return values[i], t - times[i]


def market_book(m):
    """For one market's taker rows: ask/bid series for outcome A (the smaller outcome id) and B, as (times, prices)."""
    a_id = m.outcomeId.min()
    isA = (m.outcomeId == a_id).to_numpy()
    t = m.t.to_numpy()
    p = m.p.to_numpy()
    # a taker who bought A at p: ask_A = p, and the B bid = 1 - p. A taker who bought B at p: ask_B = p, A bid = 1 - p.
    return {'askA': (t[isA], p[isA]), 'askB': (t[~isA], p[~isA]), 'pA': np.where(isA, p, 1 - p), 't': t}


def one_market(m, win_lo, win_hi, H, M, H2):
    """All entries of one market: a list of dicts, one per (entry time, first leg)."""
    bk = market_book(m)
    out = []
    askT = {'A': bk['askA'], 'B': bk['askB']}
    for t0 in np.arange(win_lo, win_hi, ENTRY_EVERY):
        for X, Y in (('A', 'B'), ('B', 'A')):
            a1, age = last_seen(*askT[X], t0)
            if not (age <= STALE and 0.10 <= a1 <= 0.90):
                continue
            bY_ask, ageY = last_seen(*askT[Y], t0)             # the other side's ask now (for the mid)
            mid_x = a1 if not (ageY <= STALE) else (a1 + (1 - bY_ask)) / 2   # X mid = (ask_X + bid_X)/2, bid_X = 1 - ask_Y
            rec = {'a1': a1, 'spread_seen': (a1 + bY_ask - 1) if ageY <= STALE else np.nan}
            # T / F: take the other side when 1 - a1 - b2 - fees >= M
            ty, py = askT[Y]
            j0 = np.searchsorted(ty, t0, side='right'); j1 = np.searchsorted(ty, t0 + H, side='right')
            lock_t = np.nan; lock_pnl = np.nan
            for j in range(j0, j1):
                net = 1 - a1 - py[j] - fee(a1) - fee(py[j])
                if net >= M:
                    lock_t, lock_pnl = ty[j] - t0, net
                    break
            rec['T_locked'] = not np.isnan(lock_t)
            # marks at the end of the horizon
            mx, agx = last_seen(*askT[X], t0 + H); my, agy = last_seen(*askT[Y], t0 + H)
            if np.isnan(mx):
                mx = a1
            if np.isnan(my):
                end_mid_x = mx
                end_ask_y = 1 - mx
            else:
                end_mid_x = (mx + (1 - my)) / 2
                end_ask_y = my
            rec['T_pnl'] = lock_pnl if rec['T_locked'] else end_mid_x - a1 - fee(a1)
            rec['F_pnl'] = lock_pnl if rec['T_locked'] else 1 - a1 - end_ask_y - fee(a1) - fee(end_ask_y)
            # M / MF: a PO bid on X at the best bid seen (bid_X = 1 - ask_Y last seen); through / touch fills
            bid_x, bage = last_seen(*askT[Y], t0)
            bid_x = 1 - bid_x
            if bage <= STALE and 0.10 <= bid_x <= 0.90:
                for mode in ('through', 'touch'):
                    ok = (py > 1 - bid_x + 1e-9) if mode == 'through' else (py >= 1 - bid_x - 1e-9)
                    ok[:j0] = False
                    idx = np.nonzero(ok[:j1])[0]
                    if len(idx):
                        tf = ty[idx[0]]
                        c = 1 - bid_x - M                       # the lock bid on Y
                        cr1 = 0.5 * fee(bid_x)                  # maker credit on fill 1: half of the taker's fee at that price
                        tx, px = askT[X]                        # a taker buying X at >= 1 - c hits our Y bid
                        k0 = np.searchsorted(tx, tf, side='right'); k1 = np.searchsorted(tx, tf + H2, side='right')
                        ok2 = (px[k0:k1] > 1 - c + 1e-9) if mode == 'through' else (px[k0:k1] >= 1 - c - 1e-9)
                        if ok2.any():
                            pnl = M + cr1 + 0.5 * fee(c)
                            rec[f'M_{mode}'] = pnl; rec[f'MF_{mode}'] = pnl; rec[f'M_{mode}_locked'] = True
                        else:
                            mxe, _ = last_seen(*askT[X], tf + H2); mye, _ = last_seen(*askT[Y], tf + H2)
                            if np.isnan(mxe):
                                mxe = bid_x
                            if np.isnan(mye):
                                end_mid, end_ask = mxe, 1 - mxe
                            else:
                                end_mid, end_ask = (mxe + (1 - mye)) / 2, mye
                            rec[f'M_{mode}'] = end_mid - bid_x + cr1
                            rec[f'MF_{mode}'] = 1 - bid_x - end_ask - fee(end_ask) + cr1
                            rec[f'M_{mode}_locked'] = False
                    else:
                        rec[f'M_{mode}'] = np.nan
            out.append(rec)
    return out


def cluster_mean(df, col):
    """Mean over entries and a 95% interval bootstrapped over markets."""
    d = df[df[col].notna()]
    if not len(d):
        return np.nan, np.nan, np.nan, 0, 0
    g = d.groupby('market')[col].agg(['sum', 'count'])
    s, c = g['sum'].to_numpy(), g['count'].to_numpy()
    mean = s.sum() / c.sum()
    bs = []
    for _ in range(600):
        i = rng.integers(0, len(g), len(g))
        bs.append(s[i].sum() / c[i].sum())
    return mean, np.percentile(bs, 2.5), np.percentile(bs, 97.5), len(d), len(g)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--from', dest='a', default='2026-09-05')
    ap.add_argument('--to', dest='b', default='2026-10-03')
    ap.add_argument('--cache', default=os.path.join(os.environ.get('TMPDIR', '/tmp'), 'novig-trades'))
    ap.add_argument('--horizons', default='60,180,600')
    ap.add_argument('--margins', default='0.01,0.02,0.04')
    a = ap.parse_args()
    os.makedirs(a.cache, exist_ok=True)
    days = [x for x in json.load(open(os.path.join(a.cache, 'index.json')) if os.path.exists(os.path.join(a.cache, 'index.json')) else
            __import__('urllib.request').request.urlopen(f'{DATA}/index.json'))['dates'] if a.a <= x <= a.b]
    hs = [float(x) for x in a.horizons.split(',')]
    ms = [float(x) for x in a.margins.split(',')]
    frames = []
    nmk = 0
    for day in days:
        d = load_day(day, a.cache)
        for mid, m in d.groupby('marketId'):
            if m.outcomeId.nunique() != 2 or len(m) < 40:
                continue
            league = m.league.iloc[0]
            m = m.sort_values('t')
            tail = m.assign(pa=np.where(m.outcomeId == m.outcomeId.min(), m.p, 1 - m.p)).pa.tail(5).median()
            if not (tail >= 0.97 or tail <= 0.03):
                continue
            end = m.t.iloc[-1]
            lo, hi = end - WINDOW_H[league] * 3600, end - 300
            inwin = m[(m.t >= lo - STALE) & (m.t <= hi + max(hs) + 600)]
            if len(inwin) < 25:
                continue
            nmk += 1
            for H in hs:
                for M in ms:
                    H2 = H
                    for r in one_market(inwin, lo, hi, H, M, H2):
                        r.update(market=mid, league=league, mtype=m.marketType.iloc[0], day=day, H=H, M=M)
                        frames.append(r)
        print(day, 'markets so far', nmk, 'entries', len(frames), file=sys.stderr)
    df = pd.DataFrame(frames)
    df['band'] = pd.cut(df.a1, [0.1, 0.3, 0.7, 0.9], labels=['10-30c', '30-70c', '70-90c'])
    df.to_pickle(os.path.join(a.cache, 'live_lock_entries.pkl'))
    print(f'\n{nmk} markets, {df.market.nunique()} with entries, {len(df)} entries, days {days[0]}..{days[-1]}')
    print('seen spread (ask_A + ask_B - 1): median %.3f, p25 %.3f, p75 %.3f' % tuple(df.spread_seen.dropna().quantile([.5, .25, .75])))
    for H in hs:
        for M in ms:
            s = df[(df.H == H) & (df.M == M)]
            print(f'\n--- horizon {H:.0f}s, lock target {M:.2f}  (per $1 of payout; mean over entries, 95% over markets)')
            for col, label in (('T_pnl', 'T  taker-taker, unlocked marked at mid'), ('F_pnl', 'F  taker-taker, forced lock at the horizon'),
                               ('M_through', 'M  maker-maker, through fills, unlocked at mid'), ('MF_through', 'MF maker-maker, through fills, forced lock'),
                               ('M_touch', 'M  maker-maker, touch fills, unlocked at mid'), ('MF_touch', 'MF maker-maker, touch fills, forced lock')):
                mean, lo_, hi_, n, k = cluster_mean(s, col)
                extra = ''
                if col == 'T_pnl':
                    extra = f'  locked {100 * s.T_locked.mean():.0f}%'
                if col.startswith('M_') and n:
                    flag = s[f'{col}_locked'].dropna() if f'{col}_locked' in s else pd.Series(dtype=float)
                    extra = f'  fills {100 * s[col].notna().mean():.0f}% of entries, of those locked {100 * flag.mean():.0f}%' if len(flag) else ''
                print(f'  {label:52s} {100 * mean:+6.2f}c [{100 * lo_:+.2f}, {100 * hi_:+.2f}]  n={n} markets={k}{extra}')


if __name__ == '__main__':
    main()
