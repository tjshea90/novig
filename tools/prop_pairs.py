#!/usr/bin/env python3
"""Same-game player-prop pair correlation from PropLine's resolved-prop archive (MK20, research/prop_pairs_2026-11-19.md).

For every game, every player's props are reduced to one line per (player, stat): the one whose Novig closing price is nearest even, as an Over and an Under record (probability = the devigged closing
probability, outcome = the result). For every two such records of one game the ratio  (sum of both-hit) / (sum of p_i * p_j)  says how much more (or less) often the pair hit together than independence
predicts; its 95% interval is a bootstrap over GAMES (pairs of one game are not independent). Classes: same player / different players x Over-Over, Under-Under, Over-Under; and, for the same player, by
pair of stats. `RfqPricer` (data/.../rfq/PropPairs.kt) uses the UPPER bounds, never below 1.0, because the maker sells the parlay.

Usage: python3 -I tools/prop_pairs.py resolved-<sport>-novig-<from>_<to>.csv.gz [...]   (the files are the `propline-archive-YYYY-MM-DD` Release assets)
       python3 -I tools/prop_pairs.py selftest
"""
import collections, csv, gzip, random, sys


def prob(american):
    a = float(american)
    return 100 / (a + 100) if a > 0 else -a / (-a + 100)


def load(fn):
    """event -> {(player, market, line): {side: (closing probability, 1 if it won else 0)}}"""
    ev = collections.defaultdict(dict)
    with gzip.open(fn, 'rt') as f:
        for r in csv.DictReader(f):
            m = r['market']
            if r['resolution'] not in ('won', 'lost') or not r['closing_price'] or not r['player_name'] or not m.startswith('player_'):
                continue
            ev[r['event_id']].setdefault((r['player_name'], m, r['line']), {})[r['outcome_name']] = (prob(r['closing_price']), 1 if r['resolution'] == 'won' else 0)
    return ev


def records(ev):
    out = {}
    for e, d in ev.items():
        best = {}
        for (pl, m, line), sides in d.items():
            if 'Over' in sides and 'Under' in sides:
                po, yo = sides['Over']
                pu, _ = sides['Under']
                if po + pu <= 0:
                    continue
                p = po / (po + pu)
                k = (pl, m)
                if k not in best or abs(p - .5) < abs(best[k][0] - .5):
                    best[k] = (p, yo)
        recs = []
        for (pl, m), (p, yo) in best.items():
            if 0.05 < p < 0.95:
                recs.append((pl, m, 'O', p, yo))
                recs.append((pl, m, 'U', 1 - p, 1 - yo))
        out[e] = recs
    return out


def side_class(si, sj):
    s = ''.join(sorted(si + sj))
    return 'OO' if s == 'OO' else 'UU' if s == 'UU' else 'OU'


def pooled(recs):
    """(same|diff, side combo) -> event -> [both hit, product of probabilities, pairs]; and for the same player the same by pair of stats"""
    cls = collections.defaultdict(lambda: collections.defaultdict(lambda: [0.0, 0.0, 0]))
    stat = collections.defaultdict(lambda: collections.defaultdict(lambda: [0.0, 0.0, 0]))
    for e, rs in recs.items():
        for i in range(len(rs)):
            pi, mi, si, pri, yi = rs[i]
            for j in range(i + 1, len(rs)):
                pj, mj, sj, prj, yj = rs[j]
                if (pi, mi) == (pj, mj):
                    continue
                side = side_class(si, sj)
                x = cls[('same' if pi == pj else 'diff', side)][e]
                x[0] += yi * yj; x[1] += pri * prj; x[2] += 1
                if pi == pj:
                    a, b = sorted([mi, mj])
                    y = stat[(a, b, side)][e]
                    y[0] += yi * yj; y[1] += pri * prj; y[2] += 1
    return cls, stat


def ratio(per_event, boots=300, seed=1):
    yy = sum(v[0] for v in per_event.values()); pp = sum(v[1] for v in per_event.values()); n = sum(v[2] for v in per_event.values())
    if pp <= 0:
        return None
    evs = list(per_event.values()); random.seed(seed); rs = []
    for _ in range(boots):
        sm = [random.choice(evs) for _ in evs]
        a = sum(v[0] for v in sm); b = sum(v[1] for v in sm)
        if b > 0:
            rs.append(a / b)
    rs.sort()
    lo = rs[int(len(rs) * 0.025)] if rs else yy / pp
    hi = rs[min(len(rs) - 1, int(len(rs) * 0.975))] if rs else yy / pp
    return yy / pp, lo, hi, n, len(evs)


def main(files):
    for fn in files:
        recs = records(load(fn))
        cls, stat = pooled(recs)
        print('==', fn, 'games', len(recs))
        for c, d in sorted(cls.items()):
            r = ratio(d)
            if r:
                print('  %-12s pairs %8d games %4d  ratio %.3f (95%% %.3f-%.3f)' % (' '.join(c), r[3], r[4], r[0], r[1], r[2]))
        rows = [(ratio(d), c) for c, d in stat.items()]
        rows = sorted([(r, c) for r, c in rows if r and r[3] >= 60], key=lambda t: -t[0][0])
        print('  -- same player, by pair of stats (>=60 pairs), highest first')
        for r, c in rows[:25]:
            print('  %-70s pairs %5d ratio %.2f (%.2f-%.2f)' % (' | '.join(c), r[3], r[0], r[1], r[2]))


def selftest():
    # two stats of one player that always hit together at p = .5 each: ratio 1 / .25 = 4; independent coins: about 1
    recs = {}
    random.seed(3)
    for g in range(400):
        y = random.randint(0, 1)
        recs[g] = [('P', 'a', 'O', .5, y), ('P', 'a', 'U', .5, 1 - y), ('P', 'b', 'O', .5, y), ('P', 'b', 'U', .5, 1 - y),
                   ('Q', 'a', 'O', .5, random.randint(0, 1)), ('Q', 'a', 'U', .5, 0)]
    cls, stat = pooled(recs)
    r = ratio(cls[('same', 'OO')])
    assert abs(r[0] - 2.0) < 0.2, r        # P's a and b Over: both hit together half the games: hit rate .5 / .25 = 2
    assert ratio(cls[('same', 'OU')])[0] < 0.01
    assert side_class('O', 'U') == 'OU' and side_class('U', 'U') == 'UU'
    print('prop_pairs selftest ok')


if __name__ == '__main__':
    if sys.argv[1:] == ['selftest']:
        selftest()
    else:
        main(sys.argv[1:])
