#!/usr/bin/env python3
"""Can you follow verified sharp bettors? RESEARCH.md §72, re-runnable.

Tj, 2026-10-03: "also consider whether it would be practical or plausible to 'follow' verified sharp bets and make the same bets as the sharp
money".

Novig's trades carry no account (NOVIG_API.md §10), so nobody can be followed there. Polymarket's ledger is public per account, with a sports
profit leaderboard, so it is the one place to test "follow the sharps" on real money:
  1. the cohort: Polymarket's sports leaderboard (all-time and this month, by profit) - the "verified" winners anyone could pick;
  2. their PREGAME buys on resolved sports markets (gamma-api: game start, result; clob prices-history: the price minute by minute);
  3. skill: CLV = the price at the game's start (the close) minus what they paid, in cents; ROI at the result;
  4. persistence: is an account's CLV in its older half of trades repeated in its newer half? (a profit ranking alone surfaces luck);
  5. copying: the price a follower would get 1, 5 and 30 minutes after the account's trade (the last traded price then: optimistic, a real
     copy also pays the spread), and that copy's CLV and ROI.

    pip install pandas numpy
    python3 tools/research/polymarket_follow_study.py [--cache DIR] [--wallets 60] [--per 120]
"""
import argparse, json, os, sys, time, hashlib, urllib.request, urllib.parse
from concurrent.futures import ThreadPoolExecutor
import numpy as np
import pandas as pd

rng = np.random.default_rng(722)
DATA, GAMMA, CLOB = 'https://data-api.polymarket.com', 'https://gamma-api.polymarket.com', 'https://clob.polymarket.com'
CACHE = None


def get(url, tries=4):
    key = os.path.join(CACHE, hashlib.sha1(url.encode()).hexdigest() + '.json')
    if os.path.exists(key):
        with open(key) as f:
            return json.load(f)
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'vigilant-research'})
            with urllib.request.urlopen(req, timeout=40) as r:
                d = json.load(r)
            with open(key, 'w') as f:
                json.dump(d, f)
            return d
        except Exception:
            time.sleep(1.5 * (i + 1))
    return None


def cohort(n):
    seen = {}
    for period in ('ALL', 'MONTH'):
        for off in range(0, n, 50):
            rows = get(f'{DATA}/v1/leaderboard?category=SPORTS&timePeriod={period}&orderBy=PNL&limit=50&offset={off}') or []
            for r in rows:
                seen.setdefault(r['proxyWallet'], r)
    return list(seen.values())


def trades_of(wallet, pages=8):
    out = []
    for p in range(pages):
        rows = get(f'{DATA}/trades?user={wallet}&limit=500&offset={500 * p}&takerOnly=false')
        if not rows:
            break
        out += rows
        if len(rows) < 500:
            break
    return out


def markets(cids):
    cids = list(cids)
    urls = [f'{GAMMA}/markets?' + '&'.join(f'condition_ids={c}' for c in cids[i:i + 40]) + f'&closed={closed}&limit=100'
            for i in range(0, len(cids), 40) for closed in ('true', 'false')]
    info = {}
    with ThreadPoolExecutor(8) as ex:
        for ms in ex.map(get, urls):
            for m in ms or []:
                info[m['conditionId']] = m
    return info


def history(asset, t0, t1):
    d = get(f'{CLOB}/prices-history?market={asset}&startTs={int(t0)}&endTs={int(t1)}&fidelity=1')
    if not d or not d.get('history'):
        return None
    h = pd.DataFrame(d['history'])
    return h.t.to_numpy(float), h.p.to_numpy(float)


def at(hist, t):
    """The last price at or before t (None before the first point)."""
    ts, ps = hist
    i = np.searchsorted(ts, t, side='right') - 1
    return ps[i] if i >= 0 else np.nan


def main():
    global CACHE
    ap = argparse.ArgumentParser()
    ap.add_argument('--cache', default=os.path.join(os.environ.get('TMPDIR', '/tmp'), 'pm-cache'))
    ap.add_argument('--wallets', type=int, default=60, help='leaderboard depth per period')
    ap.add_argument('--per', type=int, default=120, help='pregame buys sampled per account (newest first)')
    a = ap.parse_args()
    CACHE = a.cache
    os.makedirs(CACHE, exist_ok=True)

    co = cohort(a.wallets)
    print(f'cohort: {len(co)} accounts from the sports leaderboard (all-time + month, top {a.wallets} each)')
    with ThreadPoolExecutor(8) as ex:
        allt = dict(zip([c['proxyWallet'] for c in co], ex.map(trades_of, [c['proxyWallet'] for c in co])))
    rows = [dict(t, wallet=w) for w, ts in allt.items() for t in ts if t.get('side') == 'BUY']
    t = pd.DataFrame(rows)
    print(f'{len(t):,} buys fetched')
    # Each account's newest buys only (4x the sample, before the pregame and resolved filters): the market lookups stay in the thousands.
    t = t.sort_values('timestamp', ascending=False).groupby('wallet').head(4 * a.per)
    info = markets(set(t.conditionId))
    t['start'] = t.conditionId.map(lambda c: pd.Timestamp(info[c]['gameStartTime']).timestamp()
                                   if c in info and info[c].get('gameStartTime') else np.nan)
    t = t[t.start.notna() & (t.timestamp < t.start - 60)]

    def result(r):
        m = info[r.conditionId]
        try:
            outs, prices, toks = json.loads(m['outcomes']), json.loads(m['outcomePrices']), json.loads(m['clobTokenIds'])
        except Exception:
            return np.nan
        if not m.get('closed') or r.asset not in toks:
            return np.nan
        p = float(prices[toks.index(r.asset)])
        return p if p in (0.0, 1.0) else np.nan
    t['won'] = t.apply(result, axis=1)
    t = t[t.won.notna()]
    t['kind'] = t.conditionId.map(lambda c: info[c].get('sportsMarketType') or 'other')
    t = t.sort_values('timestamp', ascending=False).groupby('wallet').head(a.per)
    print(f'{len(t):,} pregame buys on resolved markets, {t.wallet.nunique()} accounts, {t.conditionId.nunique():,} markets')

    def prices(r):
        h = history(r.asset, r.timestamp - 600, r.start)
        if h is None:
            return (np.nan,) * 5
        return (at(h, r.timestamp - 60), at(h, r.timestamp + 60), at(h, r.timestamp + 300), at(h, r.timestamp + 1800), at(h, r.start - 1))
    with ThreadPoolExecutor(8) as ex:
        px = list(ex.map(prices, [r for r in t.itertuples()]))
    t[['before', 'p1', 'p5', 'p30', 'close']] = pd.DataFrame(px, index=t.index)
    t = t[t.close.notna()]
    t['clv'] = 100 * (t.close - t.price)
    t['roi'] = 100 * (t.won / t.price - 1)
    for k in ('p1', 'p5', 'p30'):
        t[f'clv_{k}'] = 100 * (t.close - t[k])
        t[f'roi_{k}'] = 100 * (t.won / t[k] - 1)
    t = t[(t.price > 0.02) & (t.price < 0.98)]
    print(f'{len(t):,} with a price history to the start\n')

    def ci(v, g):
        df = pd.DataFrame({'v': v, 'g': g}).dropna()
        if len(df) < 30:
            return '-'
        s_ = df.groupby('g').v.agg(['sum', 'size'])
        sm, n, k = s_['sum'].values, s_['size'].values, len(s_)
        b = [sm[i].sum() / n[i].sum() for i in (rng.integers(0, k, k) for _ in range(400))]
        return f'{sm.sum() / n.sum():+6.2f} [{np.percentile(b, 2.5):+.2f}, {np.percentile(b, 97.5):+.2f}]'

    g = t.conditionId
    print('## The accounts themselves (pregame buys; intervals over markets)')
    print(f'  CLV {ci(t.clv, g)} ¢   ROI {ci(t.roi, g)} %   n={len(t):,}')
    print(f'  price 1 min before their buy → 1 min after: {ci(100 * (t.p1 - t.before), g)} ¢ (their own impact + news)')
    print('\n## A follower copying them (last traded price then; a real copy also pays the spread)')
    for k, nm in (('p1', '1 min later'), ('p5', '5 min later'), ('p30', '30 min later')):
        print(f'  {nm:<13} paid {ci(100 * (t[k] - t.price), g)} ¢ more   CLV {ci(t[f"clv_{k}"], g)} ¢   ROI {ci(t[f"roi_{k}"], g)} %')
    print('\n## By kind of market (accounts / copy 5 min later)')
    for kd, y in t.groupby('kind'):
        if len(y) >= 100:
            print(f'  {kd:<14} CLV {ci(y.clv, y.conditionId)}  copy CLV {ci(y.clv_p5, y.conditionId)}   n={len(y):,}')
    print('\n## By hours before the start (accounts)')
    lead = (t.start - t.timestamp) / 3600
    for lo, hi in ((0, 1), (1, 6), (6, 24), (24, 1e9)):
        y = t[(lead >= lo) & (lead < hi)]
        print(f'  {lo}-{hi} h  CLV {ci(y.clv, y.conditionId)}  ROI {ci(y.roi, y.conditionId)}  copy@5min CLV {ci(y.clv_p5, y.conditionId)}  n={len(y):,}')

    # Persistence: each account's CLV in its older half vs its newer half.
    per = []
    for w, y in t.sort_values('timestamp').groupby('wallet'):
        if len(y) < 30:
            continue
        h = len(y) // 2
        per.append((w, y.clv.iloc[:h].mean(), y.clv.iloc[h:].mean(), y.roi.iloc[:h].mean(), y.roi.iloc[h:].mean(), len(y)))
    p = pd.DataFrame(per, columns=['w', 'clv1', 'clv2', 'roi1', 'roi2', 'n'])
    print(f'\n## Persistence over {len(p)} accounts with 30+ pregame buys (older half -> newer half)')
    if len(p) >= 8:
        print(f'  correlation of CLV: {p.clv1.corr(p.clv2):+.2f}   of ROI: {p.roi1.corr(p.roi2):+.2f}')
        top = p[p.clv1 >= p.clv1.quantile(0.75)]
        bot = p[p.clv1 <= p.clv1.quantile(0.25)]
        print(f'  best quarter by older CLV ({len(top)}): older {top.clv1.mean():+.2f}¢ -> newer {top.clv2.mean():+.2f}¢ CLV, newer ROI {top.roi2.mean():+.1f}%')
        print(f'  worst quarter ({len(bot)}):            older {bot.clv1.mean():+.2f}¢ -> newer {bot.clv2.mean():+.2f}¢ CLV, newer ROI {bot.roi2.mean():+.1f}%')
        print(f'  share of accounts with positive newer-half CLV: {100 * (p.clv2 > 0).mean():.0f}%')


if __name__ == '__main__':
    main()
