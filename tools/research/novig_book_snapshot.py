#!/usr/bin/env python3
"""What a posted (maker) bid would face on Novig right now: RESEARCH.md §70, re-runnable.

Tj, 2026-10-03: "figure out the optimal way to get the most positive EV out of my make orders but also a good chance that the orders get
filled". The trade files (novig_maker_study.py) show fills, never the book. This reads Novig's public books (no key) for a sample of open
pregame markets, spread over kinds of market and hours to the start, and records what a new bid would sit behind: the spread in grid steps,
the best bid's size and how many orders are queued at it (the book lists each order, best price first, earliest first: the queue).

    python3 tools/research/novig_book_snapshot.py [--per 60] [--rate 3] [--out books.jsonl]
then
    python3 tools/research/novig_book_snapshot.py --summary books.jsonl
"""
import argparse, json, random, sys, time, urllib.request
from collections import defaultdict

API = 'https://api.novig.com/v3/public/catalog'
LEAGUES = ['NFL', 'NCAAF', 'MLB', 'WNBA', 'NHL', 'NBA', 'NCAAB', 'EPL', 'MLS', 'La Liga', 'Bundesliga', 'Serie A', 'Champions League']
HOURS = [(0, 1), (1, 3), (3, 6), (6, 12), (12, 24), (24, 72), (72, 1e9)]


def kind(mt: str) -> str:  # same buckets as novig_strategy_study.kind
    if any(f in mt for f in ('WINNER', 'SERIES', 'ROUND', 'CHAMPION', 'MVP', 'AWARD')):
        return 'futures'
    if mt in ('MONEY', 'SPREAD', 'TOTAL') or mt.startswith('MONEYLINE_3_WAY'):
        return 'game line'
    if mt == 'TEAM_TOTAL':
        return 'team total'
    if mt.endswith('_1H') or mt.startswith('FIRST_INNING') or mt.endswith('_1Q') or mt.endswith('_F5') or 'FIRST_HALF' in mt or 'FIRST_FIVE' in mt:
        return 'period line'
    if 'CORNERS' in mt or mt == 'BOTH_TEAMS_TO_SCORE':
        return 'other game prop'
    return 'player prop'


def get(url):
    for i in range(4):
        try:
            with urllib.request.urlopen(url, timeout=20) as r:
                return json.load(r)
        except Exception as e:  # noqa: BLE001 (a research script: retry, then give up on this one)
            if i == 3:
                print('skip', url, e, file=sys.stderr)
                return None
            time.sleep(2 ** i)


def markets():
    out = []
    for lg in LEAGUES:
        after = None
        while True:
            q = f'{API}/markets?league={urllib.request.quote(lg)}&eventStatus=OPEN_PREGAME&limit=5000' + (f'&after={after}' if after else '')
            page = get(q)
            if not page:
                break
            out += [m | {'league': lg} for m in page['items'] if m.get('status') == 'OPEN' and len(m.get('outcomes', [])) == 2]
            after = page.get('next')
            if not after or len(page['items']) < 5000:
                break
            time.sleep(0.4)
    return out


def side(ladder):
    if not ladder:
        return None
    best = max(float(o['price']) for o in ladder)
    at = [o for o in ladder if abs(float(o['price']) - best) < 1e-9]
    near = [o for o in ladder if float(o['price']) >= best - 0.0101]
    return {'bid': best, 'qty': sum(o['qty'] for o in at), 'n': len(at), 'near': sum(o['qty'] for o in near), 'orders': len(ladder)}


def run(a):
    now = time.time() * 1000
    ms = markets()
    print(f'{len(ms)} open pregame two-outcome markets', file=sys.stderr)
    cells = defaultdict(list)
    for m in ms:
        h = (m['startsTs'] - now) / 3.6e6
        k = kind(m['marketType'])
        if k == 'futures' or h < 0:
            continue
        hb = next(i for i, (lo, hi) in enumerate(HOURS) if lo <= h < hi)
        cells[(k, hb)].append(m)
    pick = []
    for c, xs in cells.items():
        random.shuffle(xs)
        pick += xs[:a.per]
    random.shuffle(pick)
    print(f'reading {len(pick)} books at {a.rate}/s', file=sys.stderr)
    with open(a.out, 'w') as f:
        for m in pick:
            t0 = time.time()
            b = get(f'{API}/markets/{m["marketId"]}/book')
            if b is not None:
                o = b.get('orders', {})
                A, B = (x['outcomeId'] for x in m['outcomes'])
                f.write(json.dumps({'marketId': m['marketId'], 'league': m['league'], 'type': m['marketType'], 'kind': kind(m['marketType']),
                                    'hours': (m['startsTs'] - time.time() * 1000) / 3.6e6, 'a': side(o.get(A)), 'b': side(o.get(B))}) + '\n')
                f.flush()
            time.sleep(max(0.0, 1 / a.rate - (time.time() - t0)))


def summary(path):
    rows = [json.loads(x) for x in open(path)]
    print(f'{len(rows)} books read')
    g = defaultdict(list)
    for r in rows:
        hb = next(i for i, (lo, hi) in enumerate(HOURS) if lo <= r['hours'] < hi)
        g[(r['kind'], hb)].append(r)
        g[(r['kind'], 'all')].append(r)
    print(f'{"kind":<12} {"hours":>7} {"n":>4} {"two-sided":>9} {"spread steps (median)":>22} {"best bid $ (median)":>20} {"orders at best (median)":>24} {"none":>5}')
    for key in sorted(g, key=lambda k: (k[0], 99 if k[1] == 'all' else k[1])):
        xs = g[key]
        two = [r for r in xs if r['a'] and r['b']]
        def step(p):
            return 0.001 if p <= 0.05 or p >= 0.95 else 0.005
        sp = sorted(round((1 - r['b']['bid'] - r['a']['bid']) / step(r['a']['bid'])) for r in two)
        usd = sorted(min(r['a']['qty'] * r['a']['bid'], r['b']['qty'] * r['b']['bid']) / 100 for r in two)
        nq = sorted(max(r['a']['n'], r['b']['n']) for r in two)
        none = sum(1 for r in xs if not r['a'] and not r['b'])
        med = lambda v: v[len(v) // 2] if v else float('nan')  # noqa: E731
        hl = 'all' if key[1] == 'all' else (f'{HOURS[key[1]][0]}-{HOURS[key[1]][1]}' if HOURS[key[1]][1] < 1e8 else f'{HOURS[key[1]][0]}+')
        print(f'{key[0]:<12} {hl:>7} {len(xs):>4} {len(two) / len(xs):>9.0%} {med(sp):>22} {med(usd):>20.0f} {med(nq):>24} {none:>5}')


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--per', type=int, default=60, help='books per (kind, hours) cell')
    ap.add_argument('--rate', type=float, default=3.0)
    ap.add_argument('--out', default='books.jsonl')
    ap.add_argument('--summary')
    a = ap.parse_args()
    summary(a.summary) if a.summary else run(a)
