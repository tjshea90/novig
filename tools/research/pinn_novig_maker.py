#!/usr/bin/env python3
"""MAKE orders (resting bids) on Novig priced from Pinnacle's live fair price: would they have filled, and what would a fill have been worth?
RESEARCH.md section 120.3.  Uses the tapes pinn_novig_lag.py / pinn_pregame.py write (Pinnacle fair, Novig books) and Novig's public TRADE history for the same markets
(the public route's outcomeId/price are the RESTING order's, so a trade at q on outcome X is a bid on X at q being filled).  Sends nothing, needs no key.

    python3 tools/research/pinn_novig_maker.py fetch trades.ndjson tape1.ndjson [tape2.ndjson ...]
    python3 tools/research/pinn_novig_maker.py sim trades.ndjson tape1.ndjson [...] [--ttl 60] [--live | --pre] [--post-latency 0.3] [--cancel-latency 2.0]

The simulated bid, per Novig book read and outcome X when none is up: price p = floor_to_half_cent(Pinnacle fair for X / (1 + margin)), only if Pinnacle moved <= 20 s before, p is under Novig's ask for X
(post-only: a bid at or over it would be a take) and 0.05 <= p <= 0.95.  It rests ttl seconds.  Variants: (none) rests the whole ttl; (pin) is cancelled [cancel-latency] after a Pinnacle change that leaves
it under +1% EV; (pin+score) also never posts within 30 s of a score and is cancelled [cancel-latency] after one.  A trade on X at a price <= p after post-latency fills it ('optimistic', queue ignored);
a trade strictly below p fills it for certain ('strict').  Value of a fill = Pinnacle's fair for X at the trade time (and 30 s / 120 s later) - p, plus the maker credit in play (50% of the taker's
fee 0.03*P*(1-P), P the taker's price 1-p), over p.  The tape cannot see the queue ahead of the bid, our own size, or whether Novig would accept the order during a pause: bounds, not a result.
"""
import bisect, collections, json, os, statistics, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pinn_novig_lag as L

MARGINS = (0.01, 0.02, 0.03, 0.04)
VARIANTS = ('none', 'pin', 'pin+score')


def tapes_load(paths):
    pin = collections.defaultdict(list); nov = collections.defaultdict(list); mkt = {}; match = {}; scores = collections.defaultdict(list)
    for p in paths:
        a, b, c, d, e, _ = L.load(p)
        for k, v in a.items(): pin[k] += v
        for k, v in b.items(): nov[k] += v
        mkt.update(c); match.update(d)
        for k, v in e.items(): scores[k] += v
    for v in list(pin.values()) + list(nov.values()) + list(scores.values()): v.sort(key=lambda x: x['t'])
    return pin, nov, mkt, match, scores


def fetch(out, paths):
    pin, nov, mkt, match, scores = tapes_load(paths)
    t_lo = min(o['t'] for v in nov.values() for o in v) - 120000
    n = 0
    with open(out, 'w') as f:
        for mid, m in mkt.items():
            if m['pid'] not in match or mid not in nov: continue
            st, d, _ = L.http(f'{L.NOVIG}/catalog/markets/{mid}')
            if st != 200: continue
            names = {o['outcomeId']: o['name'] for o in d.get('outcomes', [])}
            after = None
            while True:
                url = f'{L.NOVIG}/catalog/markets/{mid}/trades?limit=1000' + (f'&after={after}' if after else '')
                st, d, tag = L.http(url)
                if st == 429:
                    time.sleep(float(tag or 1)); continue
                if st != 200 or not d: break
                items = d.get('items') or []
                for x in items:
                    if x['ts'] >= t_lo:
                        f.write(json.dumps({'mid': mid, 'name': names.get(x['outcomeId']), 'price': float(x['price']), 'qty': x['qty'], 'ts': x['ts']}) + '\n'); n += 1
                if not items or items[-1]['ts'] < t_lo or not d.get('next'): break
                after = d['next']
                time.sleep(0.25)
            time.sleep(0.25)
    print(f'{n} trades written for {len(mkt)} markets to {out}')


def floor_tick(x):
    return int(x * 200 + 1e-9) / 200.0


def sim(trades_path, paths, ttl=60.0, live=True, post_lat=0.3, cancel_lat=2.0):
    pin, nov, mkt, match, scores = tapes_load(paths)
    tr = collections.defaultdict(lambda: collections.defaultdict(list))
    for line in open(trades_path):
        d = json.loads(line)
        if d['name']: tr[d['mid']][d['name']].append((d['ts'], d['price'], d['qty']))
    for mid in tr:
        for nm in tr[mid]: tr[mid][nm].sort()
    sport_of = {}
    for (pid, key), v in pin.items():
        if v and v[0].get('sport'): sport_of[pid] = v[0]['sport']
    t_end = max(o['t'] for v in nov.values() for o in v)
    res = collections.defaultdict(lambda: {'bids': 0, 'bid_s': 0.0, 'fills': [], 'fills_strict': []})
    for mid, obs in nov.items():
        m = mkt.get(mid)
        if not m or m['pid'] not in match or mid not in tr: continue
        series = L.pin_series(pin, m); sm = L.side_map(m)
        if len(series) < 3: continue
        ts = [x['t'] for x in series]; sc_t = [s['t'] for s in scores.get(m['pid'], [])]
        sport = sport_of.get(m['pid'], '?')
        for x in sm:
            side = sm[x]; other = [n for n in sm if n != x][0]
            trs = tr[mid].get(x, []); trt = [a for a, _, _ in trs]
            busy = {(mg, v): 0 for mg in MARGINS for v in VARIANTS}
            for o in obs:
                bb = o['bb']
                if bb.get(other) is None or o['t'] > t_end - ttl * 1000: continue
                k = bisect.bisect_right(ts, o['t']) - 1
                if k < 0 or o['t'] - ts[k] > 20000 or side not in series[k]['fairp']: continue
                fair = series[k]['fairp'][side]; ask = 1.0 - bb[other][0]
                for mg in MARGINS:
                    p = floor_tick(fair / (1 + mg))
                    if p < 0.05 or p > 0.95 or p >= ask - 0.0025: continue
                    for v in VARIANTS:
                        if o['t'] < busy[(mg, v)]: continue
                        start = o['t'] + post_lat * 1000; end = o['t'] + ttl * 1000
                        if v == 'pin+score' and any(0 <= o['t'] - s <= 30000 for s in sc_t): continue
                        if v in ('pin', 'pin+score'):
                            for j in range(k + 1, len(series)):
                                if series[j]['t'] > end: break
                                if series[j]['fairp'].get(side, 1) < p * 1.01:
                                    end = min(end, series[j]['t'] + cancel_lat * 1000); break
                        if v == 'pin+score':
                            nxt = [s for s in sc_t if o['t'] < s <= end]
                            if nxt: end = min(end, nxt[0] + cancel_lat * 1000)
                        i0 = bisect.bisect_right(trt, start); fill = None; strict = None
                        for a, q, _ in trs[i0:]:
                            if a > end: break
                            if q <= p + 1e-9 and fill is None: fill = (a, q)
                            if q < p - 1e-9: strict = (a, q); break
                        r = res[(sport if sport else '?', mg, v)]; r['bids'] += 1
                        stop = fill[0] if fill else end
                        busy[(mg, v)] = stop
                        r['bid_s'] += max(0.0, (stop - start) / 1000.0)
                        for kind, fl in (('fills', fill), ('fills_strict', strict if strict else (fill if fill and fill[1] < p - 1e-9 else None))):
                            if fl is None: continue
                            a = fl[0]; kk = bisect.bisect_right(ts, a) - 1
                            if kk < 0: continue
                            def f_at(sec):
                                j = bisect.bisect_right(ts, a + sec * 1000) - 1
                                return series[j]['fairp'].get(side) if ts[j] + 0 <= t_end and a + sec * 1000 <= t_end else None
                            credit = 0.5 * 0.03 * p * (1 - p) if live else 0.0
                            def ev(f):
                                return None if f is None else (f - p + credit) / p
                            r[kind].append({'ev0': ev(series[kk]['fairp'].get(side)), 'ev30': ev(f_at(30)), 'ev120': ev(f_at(120)), 'wait': (a - start) / 1000.0, 'p': p, 'age': (a - ts[kk]) / 1000.0})
    def med(xs):
        xs = [x for x in xs if x is not None]
        return statistics.median(xs) if xs else None
    def fm(x, f='{:+.2f}'):
        return 'n/a' if x is None else f.format(100 * x)
    print(f'maker simulation: ttl {ttl:.0f}s, post latency {post_lat}s, cancel latency {cancel_lat}s, {"live (maker credit on)" if live else "pregame (no credit)"}; bid prices from Pinnacle power-devigged fair / (1+margin)')
    print('columns: sport margin variant | bids, bid-hours | fills optimistic (per bid-hour of one resting bid) EV at fill / +30 s / +120 s (median, % of price) | strict fills (rate) EV +120 s')
    allr = collections.defaultdict(lambda: {'bids': 0, 'bid_s': 0.0, 'fills': [], 'fills_strict': []})
    for (sp, mg, v), r in res.items():
        for key in ((sp, mg, v), ('ALL', mg, v)):
            a = allr[key]; a['bids'] += r['bids']; a['bid_s'] += r['bid_s']; a['fills'] += r['fills']; a['fills_strict'] += r['fills_strict']
    for (sp, mg, v), r in sorted(allr.items(), key=lambda kv: (kv[0][0] != 'ALL', kv[0][0], kv[0][1], VARIANTS.index(kv[0][2]))):
        h = r['bid_s'] / 3600.0
        if r['bids'] < 20: continue
        f, fs = r['fills'], r['fills_strict']
        print(f'  {sp:11s} {mg:4.0%} {v:10s} | bids {r["bids"]:5d} {h:5.1f} h | fills {len(f):4d} ({len(f) / h if h else 0:5.1f}/bid-hour)  EV {fm(med([x["ev0"] for x in f]))}% / {fm(med([x["ev30"] for x in f]))}% / {fm(med([x["ev120"] for x in f]))}% '
              f'| strict {len(fs):4d} ({len(fs) / h if h else 0:4.1f}/h) +120s {fm(med([x["ev120"] for x in fs]))}%')


if __name__ == '__main__':
    a = sys.argv[1:]
    if not a: sys.exit(__doc__)
    if a[0] == 'fetch':
        fetch(a[1], a[2:])
    elif a[0] == 'sim':
        opts = {}; rest = []; i = 1
        while i < len(a):
            if a[i] in ('--ttl', '--post-latency', '--cancel-latency'): opts[a[i]] = float(a[i + 1]); i += 2
            elif a[i] in ('--live', '--pre'): opts[a[i]] = True; i += 1
            else: rest.append(a[i]); i += 1
        sim(rest[0], rest[1:], ttl=opts.get('--ttl', 60.0), live='--pre' not in opts, post_lat=opts.get('--post-latency', 0.3), cancel_lat=opts.get('--cancel-latency', 2.0))
