#!/usr/bin/env python3
"""Sitting live quotes on Novig: what "wildly mispriced" looks like, how long it sits, how deep it is.  RESEARCH.md section 123 (SV2).

Public reads only (no key, no signature, no order, no Pinnodds).  Python standard library only.

    python3 tools/research/novig_live_sitting.py sweep   --leagues NCAAF,WNBA --out sweep.ndjson     one read of every live game-line book
    python3 tools/research/novig_live_sitting.py watch   --in sweep.ndjson --minutes 3 --out watch.ndjson   re-read the flagged books every ~2 s
    python3 tools/research/novig_live_sitting.py report  --sweep sweep.ndjson [--watch watch.ndjson]       tables

What it computes (a ladder = every strike of one game's TOTAL, or one team's SPREAD, or a TEAM_TOTAL):
  * per market: best bid on each outcome, the ask (= 1 - best bid on the other outcome), the spread, and whether a side has no price at all (the app shows 99.9%).
  * per ladder: the "ladder fair" of a strike = the market's OWN liquid middle (strikes whose two sides are within MAXSPREAD of each other) read at that strike by interpolating
    log-odds between the nearest liquid strike on each side (flat extrapolation is NOT used: a strike outside the liquid strikes is "no fair").  A quote is flagged when
    ladder_fair / (ask + live fee) - 1 >= EDGE.  That is a statement about the market's own consistency, not about the game: it is NOT proven EV.
  * persistence (watch): the flagged resting ORDERS are followed by orderId (the public book lists each resting order), so how long a flagged quote sat, and whether it was
    taken or pulled, is measured, not guessed.
"""
import argparse, json, math, os, re, sys, time, urllib.parse, urllib.request, urllib.error
from collections import defaultdict

API = 'https://api.novig.com/v3/public/catalog'
KINDS = ('TOTAL', 'SPREAD', 'TEAM_TOTAL', 'MONEY')
MAXSPREAD = 0.06   # a strike is "liquid" when ask - bid is within this
EDGE = 0.03


def get(url, tries=5):
    for i in range(tries):
        try:
            with urllib.request.urlopen(url, timeout=20) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code == 429:
                time.sleep(1.5 * (i + 1))
                continue
            return None
        except Exception:  # noqa: BLE001 (a research script: retry, then give up on this one)
            time.sleep(1 + i)
    return None


def live_events(leagues):
    out = []
    page = get(f'{API}/events?status=OPEN_INGAME&limit=200')
    for e in (page or {}).get('items', []):
        if e['league'] in leagues:
            out.append(e)
    return out


def event_markets(e):
    page = get(f"{API}/markets?event={e['eventId']}&limit=500")
    return [m for m in (page or {}).get('items', []) if m['marketType'] in KINDS and m['status'] == 'OPEN' and len(m['outcomes']) == 2]


def read_book(mid):
    return get(f'{API}/markets/{mid}/book')


def best(levels):
    """(best price, qty at it, total qty, number of orders, [(orderId, price, qty)...]) of one outcome's resting bids."""
    if not levels:
        return None
    ps = [(float(o['price']), int(o['qty']), o['orderId']) for o in levels]
    top = max(p for p, _, _ in ps)
    return {'bid': top, 'qtyTop': sum(q for p, q, _ in ps if p == top), 'qtyAll': sum(q for _, q, _ in ps), 'n': len(ps),
            'orders': [{'id': i, 'p': p, 'q': q} for p, q, i in sorted(ps, reverse=True)[:8]]}


def sweep(args):
    leagues = set(args.leagues.split(','))
    n = 0
    t0 = time.time()
    with open(args.out, 'w') as f:
        for e in live_events(leagues):
            for m in event_markets(e):
                time.sleep(1.0 / args.rate)
                b = read_book(m['marketId'])
                if not b:
                    continue
                o1, o2 = m['outcomes']
                rec = {'t': time.time(), 'league': e['league'], 'event': e['description'], 'eventId': e['eventId'], 'marketId': m['marketId'], 'type': m['marketType'],
                       'strike': float(m['strike']) if m.get('strike') not in (None, '') else None, 'desc': m['description'], 'fee': float(m['fee']['coefficient']),
                       'o': [{'id': o1['outcomeId'], 'name': o1['name'], 'b': best(b['orders'].get(o1['outcomeId']))},
                             {'id': o2['outcomeId'], 'name': o2['name'], 'b': best(b['orders'].get(o2['outcomeId']))}]}
                f.write(json.dumps(rec) + '\n')
                n += 1
    print(f'swept {n} books in {time.time() - t0:.0f} s -> {args.out}')


def sides(rec):
    """For both outcomes: bid, ask (1 - other's bid), qty to buy at the ask."""
    a, b = rec['o']
    out = []
    for x, y in ((a, b), (b, a)):
        bid = x['b']['bid'] if x['b'] else None
        ask = round(1 - y['b']['bid'], 4) if y['b'] else None
        qty = y['b']['qtyTop'] if y['b'] else 0
        out.append({'name': x['name'], 'id': x['id'], 'bid': bid, 'ask': ask, 'askQty': qty})
    return out


def fee(price, c):
    return c * price * (1 - price)


def logit(p):
    p = min(max(p, 1e-4), 1 - 1e-4)
    return math.log(p / (1 - p))


def unlogit(x):
    return 1 / (1 + math.exp(-x))


def ladders(recs):
    """Group TOTAL (by game), TEAM_TOTAL (by game + team) and SPREAD (by game + the alphabetically first team) markets into ladders of (threshold, rec, idx), where idx is the outcome
    that gets LESS likely as the threshold rises (the Over; the team that has to cover a bigger margin).  Outcome order in the API is not fixed, so it is found by name."""
    g = defaultdict(list)
    for r in recs:
        if r['strike'] is None:
            continue
        names = [o['name'] for o in r['o']]
        if r['type'] in ('TOTAL', 'TEAM_TOTAL'):
            idx = next((i for i, n in enumerate(names) if n.startswith('Over')), None)
            if idx is None:
                continue
            key = 'TOTAL' if r['type'] == 'TOTAL' else 'TT:' + r['desc'].split(' TEAM_TOTAL')[0]
            g[(r['eventId'], key)].append((r['strike'], r, idx))
        elif r['type'] == 'SPREAD':
            parsed = []
            for i, n in enumerate(names):
                m = re.match(r'^(.*) ([+-]\d+(?:\.\d+)?)$', n)
                if not m:
                    parsed = []
                    break
                parsed.append((m.group(1), float(m.group(2)), i))
            if len(parsed) != 2:
                continue
            team, line, idx = sorted(parsed)[0]          # the alphabetically first team's outcome; it covers if its margin beats -line
            g[(r['eventId'], 'SP:' + team)].append((-line, r, idx))
    return g


def liquid_mid(r, idx):
    """P(outcome idx wins) from the middle of the book when both sides are priced and tight, else None."""
    s = sides(r)
    me = s[idx]
    if me['ask'] is None or me['bid'] is None:
        return None
    if me['ask'] - me['bid'] > MAXSPREAD:
        return None
    return (me['ask'] + me['bid']) / 2


def ladder_fair(points, thr, idx_of):
    """Interpolated log-odds of the first outcome at threshold thr from the liquid strikes either side; None outside them."""
    liq = []
    for s, r, i in points:
        p = liquid_mid(r, i)
        if p is not None and 0.01 < p < 0.99:
            liq.append((s, p))
    liq.sort()
    lo = [x for x in liq if x[0] < thr]
    hi = [x for x in liq if x[0] > thr]
    if not lo or not hi:
        return None
    (s0, p0), (s1, p1) = lo[-1], hi[0]
    w = (thr - s0) / (s1 - s0)
    return unlogit(logit(p0) + w * (logit(p1) - logit(p0))), (s0, s1)


def flags(recs):
    out = []
    for key, pts in ladders(recs).items():
        pts.sort(key=lambda x: x[0])
        for s, r, i in pts:
            if liquid_mid(r, i) is not None:
                continue     # a liquid strike defines the ladder; it is not judged against itself
            lf = ladder_fair(pts, s, i)
            if not lf:
                continue
            p, nb = lf
            sd = sides(r)
            # orientation: for TOTAL / TEAM_TOTAL outcome 0 = Over (YES rises as the strike FALLS); for SPREAD the same with the threshold encoding above, so P(outcome 0) falls as thr rises.
            for k in (0, 1):
                pw = p if k == i else 1 - p
                ask = sd[k]['ask']
                if ask is None or sd[k]['askQty'] < 100:
                    continue
                fe = fee(ask, r['fee'])
                edge = pw / (ask + fe) - 1
                if edge >= EDGE:
                    out.append({'marketId': r['marketId'], 'event': r['event'], 'league': r['league'], 'type': r['type'], 'strike': s, 'name': sd[k]['name'], 'outcomeId': sd[k]['id'],
                                'ask': ask, 'askQty': sd[k]['askQty'], 'ladderFair': round(pw, 4), 'edge': round(edge, 4), 'neighbours': nb, 'ladder': key[1], 'fee': fe})
    return sorted(out, key=lambda x: -x['edge'])


def load(path):
    return [json.loads(l) for l in open(path) if l.strip()]


def watch(args):
    recs = load(args.inp)
    fl = flags(recs)[: args.top]
    ids = sorted({f['marketId'] for f in fl})
    print(f'watching {len(ids)} markets for {args.minutes} min; {len(fl)} flagged quotes at the start')
    end = time.time() + 60 * args.minutes
    with open(args.out, 'w') as f:
        f.write(json.dumps({'flags': fl, 't': time.time()}) + '\n')
        while time.time() < end:
            t = time.time()
            for mid in ids:
                b = read_book(mid)
                if b:
                    f.write(json.dumps({'t': time.time(), 'marketId': mid, 'orders': {k: [{'id': o['orderId'], 'p': float(o['price']), 'q': int(o['qty'])} for o in v] for k, v in b['orders'].items()}}) + '\n')
                time.sleep(1.0 / args.rate)
            f.flush()
            lag = args.every - (time.time() - t)
            if lag > 0:
                time.sleep(lag)


def report(args):
    recs = load(args.sweep)
    print(f'books read: {len(recs)} in {len({r["eventId"] for r in recs})} games ({", ".join(sorted({r["league"] for r in recs}))})')
    by = defaultdict(lambda: defaultdict(int))
    for r in recs:
        s = sides(r)
        k = r['type']
        by[k]['markets'] += 1
        for x in s:
            by[k]['sides'] += 1
            if x['ask'] is None:
                by[k]['noprice'] += 1
        if all(x['ask'] is not None and x['bid'] is not None for x in s):
            sp = s[0]['ask'] - s[0]['bid']
            by[k]['twosided'] += 1
            by[k]['spread_sum'] += sp
            if sp <= MAXSPREAD:
                by[k]['liquid'] += 1
        if all(x['bid'] is None for x in s):
            by[k]['empty'] += 1
    print('\nkind        markets  two-sided  liquid(<=6pt)  empty  sides with no price   mean spread (two-sided)')
    for k, v in by.items():
        ts = v['twosided'] or 1
        print(f"{k:<11} {v['markets']:>7} {v['twosided']:>10} {v['liquid']:>13} {v['empty']:>6} {v['noprice']:>10} of {v['sides']:<5} {100 * v['spread_sum'] / ts:>7.1f} pts")
    fl = flags(recs)
    print(f'\nflagged sitting quotes (ladder fair beats ask + live fee by {EDGE:.0%}+, size >= 100 contracts, not a liquid strike, between two liquid strikes): {len(fl)}')
    bucket = defaultdict(int)
    for x in fl:
        bucket['%s %s' % (x['league'], x['type'])] += 1
    print(dict(bucket))
    for x in fl[:25]:
        print(f"  {x['league']:<6} {x['event'][:26]:<26} {x['type']:<10} {x['name']:<18} ask {x['ask']:.3f} x{x['askQty']:<7} ladder fair {x['ladderFair']:.3f} edge {x['edge']:+.1%} between {x['neighbours']}")
    if args.watch and os.path.exists(args.watch):
        persistence(args.watch)


def persistence(path):
    rows = load(path)
    head = rows[0]
    fl = {(f['marketId'], f['outcomeId']): f for f in head['flags']}
    t0 = head['t']
    # for each flagged quote: the first time its ASK (= 1 - best bid on the other outcome) was no longer at least the flagged price, i.e. it moved or was taken
    snaps = defaultdict(list)
    for r in rows[1:]:
        snaps[r['marketId']].append(r)
    print(f'\npersistence: {len(fl)} flagged quotes followed for {(rows[-1]["t"] - t0) / 60:.1f} min ({len(rows) - 1} book reads)')
    res = []
    for (mid, oid), f in fl.items():
        seq = snaps.get(mid, [])
        other_orders = None
        gone = None
        last_ok = None
        for s in seq:
            # the order(s) that make the ask: the best bids on the OTHER outcome than oid
            ords = [o for k, v in s['orders'].items() if k != oid for o in v]
            if ords:
                top = max(o['p'] for o in ords)
                ask_now = round(1 - top, 4)
            else:
                ask_now = None
            if ask_now is not None and ask_now <= f['ask'] + 1e-9:
                last_ok = s['t']
            elif gone is None:
                gone = s['t']
        sat = (gone - t0) if gone else None
        res.append((f, sat, last_ok is not None))
    gone_ = [x[1] for x in res if x[1] is not None]
    held = [x for x in res if x[1] is None]
    print(f'  still there at the end: {len(held)} of {len(res)}; gone (price moved up or taken) within the watch: {len(gone_)}')
    if gone_:
        gs = sorted(gone_)
        q = lambda p: gs[min(len(gs) - 1, int(p * len(gs)))]
        print(f'  time until gone: median {q(0.5):.0f} s, p25 {q(0.25):.0f} s, p75 {q(0.75):.0f} s, p90 {q(0.9):.0f} s')
        for lim in (2, 6, 10, 30, 60):
            print(f'    sat at least {lim:>2} s: {sum(1 for x in gs if x >= lim) + len(held)} of {len(res)}')


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    sp = ap.add_subparsers(dest='cmd', required=True)
    a = sp.add_parser('sweep'); a.add_argument('--leagues', default='NCAAF,WNBA'); a.add_argument('--out', required=True); a.add_argument('--rate', type=float, default=4.0)
    w = sp.add_parser('watch'); w.add_argument('--in', dest='inp', required=True); w.add_argument('--minutes', type=float, default=3); w.add_argument('--out', required=True)
    w.add_argument('--top', type=int, default=30); w.add_argument('--rate', type=float, default=4.0); w.add_argument('--every', type=float, default=2.0)
    r = sp.add_parser('report'); r.add_argument('--sweep', required=True); r.add_argument('--watch')
    args = ap.parse_args()
    {'sweep': sweep, 'watch': watch, 'report': report}[args.cmd](args)
