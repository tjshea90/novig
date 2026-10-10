#!/usr/bin/env python3
"""Live bid presets: a grid over the stored Pinnacle/Novig tapes (RESEARCH.md section 124, TASKS.md SW).  Sends nothing, needs no key, opens no socket.

    python3 -I tools/research/live_bid_grid.py trades.ndjson tape1.ndjson [tape2.ndjson ...] [--post-latency 5.3] [--cancel-latency 5.3]

Same simulation as pinn_novig_maker.py (a resting bid at floor_tick(Pinnacle fair / (1 + margin)), post-only, filled by a trade at or under its price after it landed, valued against Pinnacle's
fair at the fill and 30 s / 120 s later, plus the 50% maker credit on the market's fee) with the questions the live-bid feature needs answered:
  * does a bid that LEADS its side's book (price above the best bid already resting) behave differently from one that sits behind it?
  * the guard's EV floor (pull when the fair falls to price x (1 + floor)), 0%, 1%, 2%;
  * the hold-off after a score (0, 15, 30 s);
  * the ttl (15, 30, 60 s);
  * not only the median fill but the mean, the share of fills that were picked off (EV at +120 s under zero), and the worst tenth: a median hides the fills that lose.
Latencies default to the 5.3 s Novig takes to answer a live order (RESEARCH.md 123).
"""
import bisect, collections, json, os, statistics, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pinn_novig_lag as L
from pinn_novig_maker import tapes_load, floor_tick

MARGINS = (0.02, 0.03, 0.04, 0.05)
TTLS = (15.0, 30.0, 60.0)
FLOORS = (0.0, 0.01, 0.02)
HOLDS = (0, 15, 30)


def run(trades_path, paths, post_lat, cancel_lat):
    pin, nov, mkt, match, scores = tapes_load(paths)
    tr = collections.defaultdict(lambda: collections.defaultdict(list))
    for line in open(trades_path):
        d = json.loads(line)
        if d['name']:
            tr[d['mid']][d['name']].append((d['ts'], d['price'], d['qty']))
    for mid in tr:
        for nm in tr[mid]:
            tr[mid][nm].sort()
    t_end = max(o['t'] for v in nov.values() for o in v)
    res = collections.defaultdict(lambda: {'bids': 0, 'bid_s': 0.0, 'fills': []})
    for mid, obs in nov.items():
        m = mkt.get(mid)
        if not m or m['pid'] not in match or mid not in tr:
            continue
        series = L.pin_series(pin, m)
        sm = L.side_map(m)
        if len(series) < 3:
            continue
        ts = [x['t'] for x in series]
        sc_t = [s['t'] for s in scores.get(m['pid'], [])]
        fee_c = float(m.get('fee', {}).get('coefficient', 0.03)) if isinstance(m.get('fee'), dict) else 0.03
        for x in sm:
            side = sm[x]
            other = [n for n in sm if n != x][0]
            trs = tr[mid].get(x, [])
            trt = [a for a, _, _ in trs]
            for ttl in TTLS:
                busy = {}
                for o in obs:
                    bb = o['bb']
                    if bb.get(other) is None or o['t'] > t_end - ttl * 1000:
                        continue
                    k = bisect.bisect_right(ts, o['t']) - 1
                    if k < 0 or o['t'] - ts[k] > 20000 or side not in series[k]['fairp']:
                        continue
                    fair = series[k]['fairp'][side]
                    ask = 1.0 - bb[other][0]
                    own_best = bb[x][0] if bb.get(x) else 0.0
                    for mg in MARGINS:
                        p = floor_tick(fair / (1 + mg))
                        if p < 0.05 or p > 0.95 or p >= ask - 0.0025:
                            continue
                        lead = 'lead' if p > own_best + 1e-9 else ('join' if abs(p - own_best) < 1e-9 else 'behind')
                        for floor in FLOORS:
                            for hold in HOLDS:
                                key = (ttl, mg, floor, hold)
                                if o['t'] < busy.get((key, x, mid), 0):
                                    continue
                                if hold and any(0 <= o['t'] - s <= hold * 1000 for s in sc_t):
                                    continue
                                start = o['t'] + post_lat * 1000
                                end = o['t'] + ttl * 1000
                                for j in range(k + 1, len(series)):
                                    if series[j]['t'] > end:
                                        break
                                    if series[j]['fairp'].get(side, 1) < p * (1 + floor):
                                        end = min(end, series[j]['t'] + cancel_lat * 1000)
                                        break
                                if hold:
                                    nxt = [s for s in sc_t if o['t'] < s <= end]
                                    if nxt:
                                        end = min(end, nxt[0] + cancel_lat * 1000)
                                i0 = bisect.bisect_right(trt, start)
                                fill = None
                                strict = False
                                for a, q, _ in trs[i0:]:
                                    if a > end:
                                        break
                                    if q <= p + 1e-9 and fill is None:
                                        fill = (a, q)
                                    if q < p - 1e-9:
                                        strict = True
                                        if fill is None:
                                            fill = (a, q)
                                        break
                                r = res[(lead,) + key]
                                r['bids'] += 1
                                stop = fill[0] if fill else end
                                busy[(key, x, mid)] = stop
                                r['bid_s'] += max(0.0, (stop - start) / 1000.0)
                                if fill is None:
                                    continue
                                a = fill[0]
                                kk = bisect.bisect_right(ts, a) - 1
                                if kk < 0:
                                    continue

                                def f_at(sec):
                                    if a + sec * 1000 > t_end:
                                        return None
                                    j = bisect.bisect_right(ts, a + sec * 1000) - 1
                                    return series[j]['fairp'].get(side) if j >= 0 else None
                                credit = 0.5 * fee_c * p * (1 - p)

                                def ev(f, with_credit=True):
                                    return None if f is None else (f - p + (credit if with_credit else 0.0)) / p
                                r['fills'].append({'ev0': ev(series[kk]['fairp'].get(side)), 'ev120': ev(f_at(120)), 'ev120nc': ev(f_at(120), False), 'strict': strict})
    return res


def stats(fills, key):
    xs = [f[key] for f in fills if f[key] is not None]
    if not xs:
        return None
    xs.sort()
    return {'n': len(xs), 'med': statistics.median(xs), 'mean': sum(xs) / len(xs), 'neg': sum(1 for v in xs if v < 0) / len(xs), 'p10': xs[int(0.1 * (len(xs) - 1))]}


def main():
    a = sys.argv[1:]
    post, cancel = 5.3, 5.3
    rest = []
    i = 0
    while i < len(a):
        if a[i] == '--post-latency':
            post = float(a[i + 1]); i += 2
        elif a[i] == '--cancel-latency':
            cancel = float(a[i + 1]); i += 2
        else:
            rest.append(a[i]); i += 1
    res = run(rest[0], rest[1:], post, cancel)
    print(f'live bid grid: place latency {post} s, cancel latency {cancel} s, maker credit at the market\'s own fee; EV = (Pinnacle fair then - price + credit) / price')
    print('columns: position ttl margin floor hold | bids, bid-hours | fills (per bid-hour) | EV at +120 s: median / mean / share under 0 / 10th percentile (n)')
    rows = []
    for key, r in res.items():
        h = r['bid_s'] / 3600.0
        if r['bids'] < 20 or h <= 0:
            continue
        s = stats(r['fills'], 'ev120')
        rows.append((key, r, h, s))
    rows.sort(key=lambda t: (t[0][0], t[0][1], t[0][2], t[0][3], t[0][4]))
    for (pos, ttl, mg, floor, hold), r, h, s in rows:
        f = len(r['fills'])
        tail = 'n/a' if not s else f"{100 * s['med']:+.1f}% / {100 * s['mean']:+.1f}% / {100 * s['neg']:.0f}% / {100 * s['p10']:+.1f}% (n={s['n']})"
        print(f'  {pos:6s} {ttl:3.0f}s {mg:4.0%} floor {floor:3.0%} hold {hold:2d}s | bids {r["bids"]:5d} {h:5.1f} h | fills {f:4d} ({f / h:4.1f}/h) | {tail}')


if __name__ == '__main__':
    main()
