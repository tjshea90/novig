#!/usr/bin/env python3
"""If Vigilant had posted bids on Novig, how often would they have filled, and what would the fills have been worth? RESEARCH.md §70.

Tj, 2026-10-03: "do deep research on how to do make orders on novig (post orders). The goal of the make orders is to get positive ev orders
filled ... the optimal way to get the most positive EV out of my make orders but also a good chance that the orders get filled ... how long
the make orders should be placed before they expire ... the best timing and types of bets ... how to get the most clv out of make bets".

Novig's trade files (NOVIG_API.md §10) list every fill, both sides, but not the book, so a posted bid is SIMULATED against them:
  - A bid on outcome X at price b would have filled when a taker traded through it: Novig matches best price first, then earliest
    (docs: "The array order is the queue"), so any MAKER fill on X at a price BELOW b means every bid at b had already filled.
    That is the lower bound ("through"). A maker fill AT b would have reached it only if the queue ahead was used up: the upper bound
    ("touch"). The truth is in between.
  - Post-only: a bid at or above X's last traded offer would have taken instead of resting; those are counted as "would take" and left out.
  - The fair price at posting time: Vigilant's comes from other books and isn't in these files. Two stand-ins:
      w=0    Novig's own price then (the median of its last 7 trades, as a price for X): what a quote with no outside information gets.
      w=0.5  half-way from that to Novig's close: a fair that already knows half of the coming move (a sharp book leading Novig).
    A fair that knew the close (w=1) makes every fill worth exactly the margin, so it says nothing; the truth for Vigilant's sharp-book fair
    is between w=0 and w=0.5 (Novig's liquidity providers quote off the same sharp books; §62.2).
  - CLV = Novig's close (test A, §62: the median taker price 30 min before the latest possible start) minus b; EV@close = close/b − 1; ROI at
    settlement where the last trade shows the winner. Fills are allowed until that cut-off (always pregame: GOLIVE voids resting orders).

    pip install pandas numpy
    python3 tools/research/novig_maker_study.py [--from 2026-08-03] [--to 2026-10-01] [--cache DIR]
"""
import argparse, json, os, sys, urllib.request
import numpy as np
import pandas as pd

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import novig_size_study as s  # noqa: E402
from novig_strategy_study import kind  # noqa: E402

rng = np.random.default_rng(70)
MARGINS = [1.0, 2.0, 3.0, 4.0, 6.0, 8.0]           # EV% at the fair: the bid is fair / (1 + margin), on the grid
POST_H = [0.5, 1, 3, 6, 12, 24, 48]              # hours before the close the bid is posted
TTL_H = [0.25, 1, 3, 6, 1e9]                        # hours it rests (1e9 = until the close)
REQUOTE_MIN = 10                                    # minutes between re-quotes in the re-quoting test
K_FAIR = 7                                          # trades in the stand-in fair
WS = (0.0, 0.25, 0.5)                               # how far the fair leads Novig toward its close
LEFT_BINS = [0, 0.5, 1, 2, 3, 6, 12, 24]            # hours before the close, for fills while re-quoting
ALLF = {}                                           # (w, margin, kind, bin) -> [fills, sum EV@close%, sum CLV¢]


def snap_down(p):
    """Novig's grid (NOVIG_API.md §7), rounded down: 0.001 steps under 0.050 and over 0.950, 0.005 between."""
    m = np.floor(np.round(p * 1000, 6)).astype(int)
    m = np.where((m > 50) & (m < 950), m - m % 5, m)
    return np.clip(m, 1, 999) / 1000.0


def tick_below(p):
    return np.where((p <= 0.050) | (p > 0.950), 0.001, 0.005)


def prep(d):
    d['kind'] = d.marketType.map(lambda m: kind(str(m)))
    d = d[d.kind != 'futures'].copy()
    d['p'] = d.p.round(3)
    last = d.sort_values('ts').groupby('marketId').pA.last()
    a_won = pd.Series(np.where(last >= 0.97, 1.0, np.where(last <= 0.03, 0.0, np.nan)), index=last.index)
    end = d[d.side == 'TAKER'].groupby('marketId').ts.max()
    d['cut'] = d.marketId.map(end) - pd.to_timedelta(d.league.map(s.LONGEST_GAME_H), unit='h')
    win = d[(d.side == 'TAKER') & (d.ts < d.cut) & (d.ts >= d.cut - pd.Timedelta(minutes=30))]
    c = win.groupby('marketId').pA.agg(['median', 'size'])
    close_a = c[c['size'] >= 3]['median']
    d = d[d.marketId.isin(close_a.index) & (d.ts < d.cut)].copy()
    epoch = pd.Timestamp('2026-01-01', tz='UTC')
    d['t'] = (d.ts - epoch) / pd.Timedelta(hours=1)   # hours
    d['cutH'] = (d.cut - epoch) / pd.Timedelta(hours=1)
    return d, close_a, a_won


def simulate(d, close_a, a_won):
    """One row per (market, side, posting hour, fair kind, margin): the bid, whether and when it filled, for every TTL."""
    rows, req = [], []
    for mid_, g in d.groupby('marketId', sort=False):
        g = g.sort_values('t')
        cut = g.cutH.iat[0]
        closeA = close_a[mid_]
        won = a_won.get(mid_, np.nan)
        tk = g[g.side == 'TAKER']
        tt, tpa = tk.t.to_numpy(float), tk.pA.to_numpy(float)
        tisA, tp = tk.isA.to_numpy(bool), tk.p.to_numpy(float)
        mk = {x: g[(g.side == 'MAKER') & (g.isA == x)] for x in (True, False)}
        mt = {x: mk[x].t.to_numpy(float) for x in mk}
        mp = {x: mk[x].p.to_numpy(float) for x in mk}
        info = (g.league.iat[0], g.kind.iat[0])
        # --- static posts: one bid, posted at cut − h, resting for each TTL
        for h in POST_H:
            t0 = cut - 0.5 - h  # the close is the half hour before the cut; h is counted from the close's start
            j = np.searchsorted(tt, t0)
            if j < 3 or tt[j - 1] < t0 - 6:
                continue
            nowA = np.median(tpa[max(0, j - K_FAIR):j])
            for x in (True, False):
                close = closeA if x else 1 - closeA
                xwon = won if x else (1 - won if won == won else np.nan)
                # X's last traded offer in the 2 h before posting (a bid at or above it would take, not rest)
                sel = (tisA[:j] == x) & (tt[:j] >= t0 - 2)
                offer = tp[:j][sel][-1] if sel.any() else np.nan
                i0 = np.searchsorted(mt[x], t0)
                ft, fp = mt[x][i0:], mp[x][i0:]
                for w in WS:
                    fairA = nowA + w * (closeA - nowA)
                    fair = fairA if x else 1 - fairA
                    bids = snap_down(fair / (1 + np.array(MARGINS) / 100))
                    for m, b in zip(MARGINS, bids):
                        if b < 0.02 or b > 0.98:
                            continue
                        if offer == offer and b >= offer - 1e-9:
                            rows.append((mid_, x, h, w, m, b, fair, close, xwon, *info, 1, np.nan, np.nan))
                            continue
                        thr = np.nonzero(fp < b - 1e-9)[0]
                        tou = np.nonzero(fp <= b + 1e-9)[0]
                        rows.append((mid_, x, h, w, m, b, fair, close, xwon, *info, 0,
                                     ft[thr[0]] - t0 if len(thr) else np.nan, ft[tou[0]] - t0 if len(tou) else np.nan))
        # --- re-quoting: from 24 h before the close, cancel and re-post every REQUOTE_MIN at the fair then (the stand-in fair at that moment)
        grid = np.arange(cut - 0.5 - 24, cut, REQUOTE_MIN / 60)
        jj = np.searchsorted(tt, grid)
        ok = jj >= 3
        if not ok.any():
            continue
        grid, jj = grid[ok], jj[ok]
        nowA = np.array([np.median(tpa[max(0, j - K_FAIR):j]) for j in jj])
        for x in (True, False):
            close = closeA if x else 1 - closeA
            xwon = won if x else (1 - won if won == won else np.nan)
            # X's last traded offer at each re-quote (2 h back): a bid at or above it would take, so none is posted then
            xt = tt[tisA == x]
            xp = tp[tisA == x]
            offer = np.full(len(grid), np.nan)
            if len(xt):
                li = np.searchsorted(xt, grid) - 1
                lc = np.clip(li, 0, None)
                offer = np.where((li >= 0) & (xt[lc] >= grid - 2), xp[lc], np.nan)
            # which interval each maker fill on X fell in
            k = np.searchsorted(grid, mt[x], side='right') - 1
            kc = np.clip(k, 0, len(grid) - 1)
            left = cut - 0.5 - mt[x]
            for w in WS:
                fA = nowA + w * (closeA - nowA)
                fx = fA if x else 1 - fA
                for m in MARGINS:
                    b = snap_down(fx / (1 + m / 100))
                    b = np.where(np.isnan(offer) | (b < offer - 1e-9), b, np.nan)
                    hit = (k >= 0) & (mp[x] < b[kc] - 1e-9) & (mt[x] >= grid[0])
                    if hit.any():
                        idx = np.nonzero(hit)[0]
                        i = idx[0]
                        req.append((mid_, x, w, m, b[k[i]], fx[k[i]], close, xwon, *info, left[i]))
                        # every re-quote interval that filled (one fill each): what fills are worth by time left before the close
                        _, first_in = np.unique(k[idx], return_index=True)
                        for j in idx[first_in]:
                            hb = int(np.searchsorted(LEFT_BINS, left[j], side='right') - 1)
                            acc = ALLF.setdefault((w, m, info[1], hb), [0, 0.0, 0.0])
                            acc[0] += 1
                            acc[1] += 100 * (close / b[k[j]] - 1)
                            acc[2] += 100 * (close - b[k[j]])
                    else:
                        req.append((mid_, x, w, m, np.nan, np.nan, close, xwon, *info, np.nan))
    cols = ['marketId', 'isA', 'h', 'w', 'm', 'b', 'fair', 'close', 'won', 'league', 'kind', 'cross', 'thrH', 'touH']
    r = pd.DataFrame(rows, columns=cols)
    q = pd.DataFrame(req, columns=['marketId', 'isA', 'w', 'm', 'b', 'fair', 'close', 'won', 'league', 'kind', 'hoursLeft'])
    return r, q


def ci_mean(x, col):
    g = x.groupby('marketId')[col].agg(['sum', 'size'])
    sm, n, k = g['sum'].values, g['size'].values, len(g)
    if k < 10:
        return f'{sm.sum() / max(n.sum(), 1):+.2f} (n={n.sum()})'
    boot = [sm[i].sum() / n[i].sum() for i in (rng.integers(0, k, k) for _ in range(400))]
    return f'{sm.sum() / n.sum():+.2f} [{np.percentile(boot, 2.5):+.2f}, {np.percentile(boot, 97.5):+.2f}]'


def report(label, r, ttl=1e9, bound='thrH'):
    """r: static posts (not crossing). Fill within ttl by the given bound; value per fill and per posted bid."""
    if len(r) == 0:
        return
    f = r[r[bound] <= ttl].copy()
    fill = len(f) / len(r)
    if len(f) == 0:
        print(f'  {label:<40} posted {len(r):>6,}  filled 0')
        return
    f['clv'] = 100 * (f.close - f.b)
    f['ev'] = 100 * (f.close / f.b - 1)
    f['roi'] = 100 * (f.won / f.b - 1)
    won = f.dropna(subset=['won'])
    per_post = fill * f.ev.mean()
    print(f'  {label:<40} posted {len(r):>6,}  fill {fill:>5.0%}  CLV¢ {ci_mean(f, "clv")}  EV@close% {ci_mean(f, "ev")}  '
          f'ROI% {ci_mean(won, "roi") if len(won) >= 30 else "-"} (n={len(won)})  per posted bid {per_post:+.2f}%  '
          f'median wait {60 * f[bound].median():.0f} min')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--from', dest='start', default=None)
    ap.add_argument('--to', dest='end', default=None)
    ap.add_argument('--cache', default=os.path.join(os.environ.get('TMPDIR', '/tmp'), 'novig-trades'))
    ap.add_argument('--save', default=None, help='write the simulated rows here (pickle) for further slicing')
    a = ap.parse_args()
    with urllib.request.urlopen(f'{s.DATA}/index.json', timeout=30) as r:
        days = [x for x in json.load(r)['dates'] if (not a.start or x >= a.start) and (not a.end or x <= a.end)]
    print(f'{len(days)} days, {days[0]} .. {days[-1]}')
    d, close_a, a_won = prep(s.load(days, a.cache))
    print(f'{d.marketId.nunique():,} markets with a close; simulating ...', flush=True)
    r, q = simulate(d, close_a, a_won)
    if a.save:
        pd.to_pickle((r, q), a.save)
    post = r[r.cross == 0]
    print(f'{len(r):,} simulated bids, {r.cross.mean():.0%} would have taken (at or above the offer) and are left out')
    print('fill = traded THROUGH the bid before it expired (lower bound); "touch" rows = a fill AT the bid (upper bound, queue permitting).')
    print('CLV¢ / EV@close% / ROI% are per filled bid; "per posted bid" = fill rate × EV@close (what each bid you post earns on average).')

    for w in (0.0, 0.5):
        P = post[post.w == w]
        what = "Novig's own price then" if w == 0 else 'half-way to the close: a fair that leads Novig'
        print(f'\n######## fair stand-in w={w} ({what})')
        print('\n== 1. Margin below the fair (posted 3 h before the close, resting until the close; all kinds)')
        for m in MARGINS:
            report(f'{m:.1f}¢ below fair', P[(P.h == 3) & (P.m == m)])
            report(f'{m:.1f}¢ below fair (touch)', P[(P.h == 3) & (P.m == m)], bound='touH')
        print('\n== 2. When to post (1.0¢ and 2.0¢ below fair, resting until the close)')
        for m in (1.0, 2.0):
            for h in POST_H:
                report(f'{m:.1f}¢, posted {h:g} h before the close', P[(P.h == h) & (P.m == m)])
        print('\n== 3. How long it rests (posted 6 h before the close, 1.0¢ and 2.0¢ below fair)')
        for m in (1.0, 2.0):
            for ttl in TTL_H:
                report(f'{m:.1f}¢, expires after {"the close" if ttl > 1e8 else f"{ttl:g} h"}', P[(P.h == 6) & (P.m == m)], ttl=ttl)
        print('\n== 4. Kind of market (posted 3 h before, until the close)')
        for k in sorted(P.kind.unique()):
            for m in (1.0, 2.0, 3.0):
                report(f'{k} {m:.1f}¢', P[(P.h == 3) & (P.m == m) & (P.kind == k)])
        print('\n== 5. Price of the side bid on (3 h, 2.0¢)')
        for lo, hi in [(0, .2), (.2, .35), (.35, .5), (.5, .65), (.65, .8), (.8, 1)]:
            report(f'bid {lo:.2f}-{hi:.2f}', P[(P.h == 3) & (P.m == 2.0) & (P.b >= lo) & (P.b < hi)])
        print('\n== 6. League (3 h, 2.0¢; leagues with 100+ markets)')
        x = P[(P.h == 3) & (P.m == 2.0)]
        for lg, y in x.groupby('league'):
            if y.marketId.nunique() >= 100:
                report(lg, y)
        print('\n== 7. Both sides bid (3 h, until the close): both filled = a lock of the two margins')
        for m in MARGINS:
            y = P[(P.h == 3) & (P.m == m)]
            both = y.groupby('marketId').filter(lambda z: len(z) == 2)
            n = both.marketId.nunique()
            two = both.groupby('marketId').thrH.apply(lambda v: v.notna().all()).sum()
            print(f'  {m:.1f}¢: {n:,} markets bid on both sides, both filled in {two / max(n, 1):.0%}')

    print('\n######## Re-quoting every 10 min at the fair then (w=0), from 24 h before the close: the first fill per side')
    for m in MARGINS:
        y = q[q.m == m]
        f = y.dropna(subset=['b']).copy()
        f['clv'] = 100 * (f.close - f.b)
        f['ev'] = 100 * (f.close / f.b - 1)
        f['roi'] = 100 * (f.won / f.b - 1)
        won = f.dropna(subset=['won'])
        fill = len(f) / len(y)
        print(f'  {m:.1f}¢ re-quoted: sides {len(y):,} fill {fill:.0%}  CLV¢ {ci_mean(f, "clv")}  EV@close% {ci_mean(f, "ev")}  '
              f'ROI% {ci_mean(won, "roi") if len(won) >= 30 else "-"}  per side {fill * f.ev.mean():+.2f}%  median {f.hoursLeft.median():.1f} h before the close')
        for k in ('game line', 'player prop'):
            fk = f[f.kind == k]
            if len(fk) > 30:
                print(f'      {k:<12} fill {len(fk) / len(y[y.kind == k]):.0%}  EV@close% {ci_mean(fk, "ev")}')


if __name__ == '__main__':
    main()
