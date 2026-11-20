#!/usr/bin/env python3
"""Fit and check the player-prop ladder extrapolation (MK21, research/ladder_extrapolation_2026-11-20.md).

Novig lists a player's stat at many strikes; the books post the main number and a few rungs. For every (game, player, stat) with 3+ strikes in PropLine's resolved-prop archive the strike whose Novig CLOSING over
probability is nearest even is the base, and every other strike is predicted:  p(K) = cdf( probit(p_base) + beta * (L - K) / L**gamma )  with L the base strike. beta is fitted per stat on two thirds of the games
(least squares on the probability), the error is read on the other third: mean absolute error in probability points by distance, and the relative error of a side by probability band. The engine uses gamma 0.5 and
the betas in `RfqLadder.SLOPES` (data/.../rfq/RfqLadder.kt): re-run this when a newer archive is out and move the table only on a clearly better held-out error.

Usage: python3 -I tools/ladder_extrap.py resolved-<sport>-novig-<from>_<to>.csv.gz [...]     (the `propline-archive-YYYY-MM-DD` Release assets)
       python3 -I tools/ladder_extrap.py selftest
"""
import collections, csv, gzip, math, random, sys
from statistics import NormalDist

N = NormalDist()


def prob(american):
    a = float(american)
    return 100 / (a + 100) if a > 0 else -a / (-a + 100)


def probit(p):
    return N.inv_cdf(min(max(p, 1e-4), 1 - 1e-4))


def load(fn):
    """(event, player, market) -> {strike: (over probability devigged against the same strike's Under, 1 if the Over won)}; ladders of 3+ strikes only"""
    g = collections.defaultdict(lambda: collections.defaultdict(dict))
    with gzip.open(fn, 'rt') as f:
        for r in csv.DictReader(f):
            m = r['market']
            if not m.startswith('player_') or not r['closing_price'] or not r['player_name'] or not r['line'] or r['resolution'] not in ('won', 'lost'):
                continue
            g[(r['event_id'], r['player_name'], m)][float(r['line'])][r['outcome_name']] = (prob(r['closing_price']), 1 if r['resolution'] == 'won' else 0)
    out = {}
    for k, lines in g.items():
        ladder = {}
        for L, s in lines.items():
            if 'Over' in s and 'Under' in s:
                po, yo = s['Over']
                pu, _ = s['Under']
                if po + pu > 0 and 0.02 < po / (po + pu) < 0.98:
                    ladder[L] = (po / (po + pu), yo)
        if len(ladder) >= 3:
            out[k] = ladder
    return out


def rows_by_stat(data, gam):
    by = collections.defaultdict(list)
    for (ev, pl, m), ladder in data.items():
        Lm = min(ladder, key=lambda L: abs(ladder[L][0] - .5))
        pb = ladder[Lm][0]
        for K, (p, y) in ladder.items():
            if K != Lm:
                by[m].append((ev, (Lm - K) / (max(Lm, 0.5) ** gam), p, pb, y))
    return by


def fit(rows, seed=3):
    """beta by grid search on the training third-out games; returns (beta, held-out mean absolute error, rows)"""
    evs = sorted(set(r[0] for r in rows))
    random.seed(seed)
    random.shuffle(evs)
    test = set(evs[:len(evs) // 3])
    tr = [r for r in rows if r[0] not in test]
    te = [r for r in rows if r[0] in test]
    best = None
    for b in [x / 200 for x in range(5, 600)]:
        e = sum((N.cdf(probit(pb) + b * x) - p) ** 2 for _, x, p, pb, _ in tr)
        if best is None or e < best[0]:
            best = (e, b)
    b = best[1]
    mae = sum(abs(N.cdf(probit(pb) + b * x) - p) for _, x, p, pb, _ in te) / max(1, len(te))
    return b, mae, te


def main(files):
    for fn in files:
        data = load(fn)
        print('==', fn, 'ladders', len(data))
        for gam in (0.5, 0.75, 1.0):
            by = rows_by_stat(data, gam)
            print(' gamma', gam)
            for m, rows in sorted(by.items(), key=lambda kv: -len(kv[1])):
                if len(rows) < 120:
                    continue
                b, mae, te = fit(rows)
                print('   %-34s n=%5d beta %.3f held-out MAE %.4f' % (m, len(rows), b, mae))
        # relative error of a side by probability band at gamma 0.5, all stats pooled, each stat with its own beta
        by = rows_by_stat(data, 0.5)
        allr = []
        for m, rows in by.items():
            if len(rows) >= 120:
                b, _, te = fit(rows)
                allr += [(p, N.cdf(probit(pb) + b * x), y) for _, x, p, pb, y in te]
        print(' relative error of a side (predicted / Novig close), held-out, both sides')
        for lo, hi in [(0.02, 0.1), (0.1, 0.25), (0.25, 0.5), (0.5, 0.75), (0.75, 0.9), (0.9, 0.99)]:
            xs = sorted(math.log(qh / q) for p, ph, _ in allr for q, qh in ((p, ph), (1 - p, 1 - ph)) if lo <= q < hi and q > 0 and qh > 0)
            if xs:
                mu = sum(xs) / len(xs)
                print('   q in [%.2f,%.2f) n %5d mean %+.1f%% p10 %+.0f%% p90 %+.0f%%' % (lo, hi, len(xs), 100 * mu, 100 * (math.exp(xs[len(xs) // 10]) - 1), 100 * (math.exp(xs[9 * len(xs) // 10]) - 1)))


def selftest():
    random.seed(5)
    data = {}
    beta = 0.6
    for g in range(300):
        Lm = random.choice([10.5, 20.5, 30.5])
        pb = random.uniform(0.4, 0.6)
        ladder = {Lm: (pb, 0)}
        for d in (-6, -3, 3, 6):
            K = Lm + d
            ladder[K] = (N.cdf(probit(pb) + beta * (Lm - K) / math.sqrt(Lm)), 0)
        data[(g, 'p', 'player_points')] = ladder
    by = rows_by_stat(data, 0.5)
    b, mae, _ = fit(by['player_points'])
    assert abs(b - beta) < 0.03 and mae < 0.01, (b, mae)
    print('ladder_extrap selftest ok')


if __name__ == '__main__':
    if sys.argv[1:] == ['selftest']:
        selftest()
    else:
        main(sys.argv[1:])
