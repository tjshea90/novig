#!/usr/bin/env python3
"""Who is right when a price disagrees with the market? RESEARCH.md §72, re-runnable.

Tj, 2026-10-03: "find historical betting information from different sources, especially sharp data, which shows how sharp money can be
spotted and avoid the other side of those bets ... the timing of positive EV bets, types of bets".

Novig keeps no record of the books' prices beside its trades, so this uses the one free, large, public history that has a SHARP book's
early AND closing price beside many soft books and the results: football-data.co.uk (Joseph Buchdahl's site). Each match has Pinnacle's
price early (collected Friday afternoon for weekend games, Tuesday for midweek: about 1-3 days before) and at the close, the same for
bet365 / Bet&Win / William Hill / BetVictor / Interwetten, plus the best (Max) and average (Avg) price across ~40 books. Markets: 1X2,
over/under 2.5 goals, Asian handicap. Seasons 2019/20 on (when the closing columns start).

Questions, each a Vigilant rule:
  A. A soft book's price beats the SHARP fair early: how much of that edge is still there at the sharp close, and in results, by edge size?
  B. The best price anywhere beats the CONSENSUS fair (the average book, devigged; Vigilant's and CNO's method when no sharp book prices a
     line): same, early vs at the close (timing), by edge size (are big gifts traps?), by how much the books disagree.
  C. When one venue disagrees with the consensus, does the close move to that venue (it was informed: the other side was a trap) or to the
     consensus (it was stale: a gift)? lambda = (sharp close - consensus early) / (venue early - consensus early), per venue.
  D. Favorite-longshot bias at the sharp close (matters for bids on cheap sides).

    pip install pandas numpy
    python3 tools/research/sharp_anchor_study.py --dir DIR      # downloads into DIR on first run (~150 small CSVs)

Every interval is a 95% bootstrap over matches. CLV% = price taken x sharp close fair prob - 1 (Buchdahl: the ratio to Pinnacle's close is
the expected return); ROI% = flat-stake return on the results.
"""
import argparse, os, subprocess, sys
import numpy as np
import pandas as pd

LEAGUES = 'E0 E1 E2 E3 EC SC0 SC1 SC2 SC3 D1 D2 I1 I2 SP1 SP2 F1 F2 N1 B1 P1 T1 G1'.split()
SEASONS = '1920 2021 2122 2223 2324 2425 2526'.split()
SOFT = ['B365', 'BW', 'WH', 'VC', 'IW']
rng = np.random.default_rng(72)


def fetch(d):
    os.makedirs(d, exist_ok=True)
    parts = []
    for s in SEASONS:
        for lg in LEAGUES:
            f = os.path.join(d, f'{lg}_{s}.csv')
            if not os.path.exists(f) or os.path.getsize(f) == 0:
                subprocess.run(['curl', '-sL', '-m', '60', '-o', f, f'https://www.football-data.co.uk/mmz4281/{s}/{lg}.csv'])
            try:
                x = pd.read_csv(f, encoding='latin1', on_bad_lines='skip')
            except Exception:
                continue
            if 'PSCH' not in x.columns:
                continue
            x['lg'], x['season'] = lg, s
            parts.append(x)
    d = pd.concat(parts, ignore_index=True)
    return d.dropna(subset=['FTR', 'PSH', 'PSD', 'PSA', 'PSCH', 'PSCD', 'PSCA']).reset_index(drop=True)


def power_devig(odds):
    """Fair probabilities from one book's prices: p_i = (1/o_i)^k with sum 1 (the 'logarithm' method Buchdahl uses; it puts more of
    the margin on long shots than the proportional method)."""
    q = 1.0 / odds
    lo, hi = np.full(len(q), 0.5), np.full(len(q), 3.0)
    for _ in range(60):
        k = (lo + hi) / 2
        s = np.nansum(q ** k[:, None], axis=1)
        lo, hi = np.where(s > 1, k, lo), np.where(s > 1, hi, k)
    p = q ** ((lo + hi) / 2)[:, None]
    return p / p.sum(axis=1, keepdims=True)


def ci(values, groups):
    """Mean and a 95% interval bootstrapped over groups (matches)."""
    df = pd.DataFrame({'v': values, 'g': groups}).dropna()
    if len(df) < 30:
        return None
    a = df.groupby('g').v.agg(['sum', 'size'])
    sm, n, k = a['sum'].values, a['size'].values, len(a)
    boot = [sm[i].sum() / n[i].sum() for i in (rng.integers(0, k, k) for _ in range(300))]
    return sm.sum() / n.sum(), np.percentile(boot, 2.5), np.percentile(boot, 97.5), len(df)


def fmt(r, pct=True):
    if r is None:
        return '-'
    m, lo, hi, n = r
    f = 100 if pct else 1
    return f'{f * m:+.2f} [{f * lo:+.2f}, {f * hi:+.2f}]'


def table(title, rows):
    print(f'\n## {title}')
    for r in rows:
        print('  ' + r)


def outcomes(d):
    """Long table: one row per match x outcome for the three markets, with every price we need."""
    out = []
    # 1X2
    won = {'H': d.FTR.eq('H'), 'D': d.FTR.eq('D'), 'A': d.FTR.eq('A')}
    pin_o = power_devig(d[['PSH', 'PSD', 'PSA']].values.astype(float))
    pin_c = power_devig(d[['PSCH', 'PSCD', 'PSCA']].values.astype(float))
    avg_o = power_devig(d[['AvgH', 'AvgD', 'AvgA']].values.astype(float)) if 'AvgH' in d else None
    avg_c = power_devig(d[['AvgCH', 'AvgCD', 'AvgCA']].values.astype(float))
    for i, o in enumerate('HDA'):
        r = pd.DataFrame({'match': d.index, 'lg': d.lg, 'season': d.season, 'mkt': '1X2', 'side': o, 'won': won[o].astype(float).values,
                          'pinO': pin_o[:, i], 'pinC': pin_c[:, i], 'avgO': avg_o[:, i], 'avgC': avg_c[:, i],
                          'pinOodds': d[f'PS{o}'].values, 'pinCodds': d[f'PSC{o}'].values,
                          'maxO': d[f'Max{o}'].values, 'maxC': d[f'MaxC{o}'].values,
                          'avgOodds': d[f'Avg{o}'].values, 'avgCodds': d[f'AvgC{o}'].values})
        for b in SOFT:
            r[f'{b}O'] = d[f'{b}{o}'].values if f'{b}{o}' in d else np.nan
            r[f'{b}C'] = d[f'{b}C{o}'].values if f'{b}C{o}' in d else np.nan
        out.append(r)
    # Over/under 2.5
    goals = d.FTHG + d.FTAG
    ou = d.dropna(subset=['P>2.5', 'P<2.5', 'PC>2.5', 'PC<2.5'])
    g = goals.loc[ou.index]
    po = power_devig(ou[['P>2.5', 'P<2.5']].values.astype(float))
    pc = power_devig(ou[['PC>2.5', 'PC<2.5']].values.astype(float))
    ao = power_devig(ou[['Avg>2.5', 'Avg<2.5']].values.astype(float))
    ac = power_devig(ou[['AvgC>2.5', 'AvgC<2.5']].values.astype(float))
    for i, (o, w) in enumerate([('>2.5', g > 2.5), ('<2.5', g < 2.5)]):
        r = pd.DataFrame({'match': ou.index, 'lg': ou.lg, 'season': ou.season, 'mkt': 'OU2.5', 'side': o, 'won': w.astype(float).values,
                          'pinO': po[:, i], 'pinC': pc[:, i], 'avgO': ao[:, i], 'avgC': ac[:, i],
                          'pinOodds': ou[f'P{o}'].values, 'pinCodds': ou[f'PC{o}'].values,
                          'maxO': ou[f'Max{o}'].values, 'maxC': ou[f'MaxC{o}'].values,
                          'avgOodds': ou[f'Avg{o}'].values, 'avgCodds': ou[f'AvgC{o}'].values})
        r['B365O'] = ou[f'B365{o}'].values
        r['B365C'] = ou[f'B365C{o}'].values
        out.append(r)
    # Asian handicap: only where the early and closing line are the same (else the prices are for different bets)
    ah = d.dropna(subset=['AHh', 'AHCh', 'PAHH', 'PAHA', 'PCAHH', 'PCAHA'])
    ah = ah[ah.AHh == ah.AHCh]
    margin = (ah.FTHG - ah.FTAG + ah.AHh).values
    # quarter lines settle half and half; keep whole and half lines for a clean win/lose/push
    keep = (np.abs(ah.AHh * 4) % 2 == 0)
    ah, margin = ah[keep], margin[keep]
    po = power_devig(ah[['PAHH', 'PAHA']].values.astype(float))
    pc = power_devig(ah[['PCAHH', 'PCAHA']].values.astype(float))
    ao = power_devig(ah[['AvgAHH', 'AvgAHA']].values.astype(float))
    ac = power_devig(ah[['AvgCAHH', 'AvgCAHA']].values.astype(float))
    for i, (o, w) in enumerate([('AH home', margin > 0), ('AH away', margin < 0)]):
        won_ = np.where(margin == 0, np.nan, w.astype(float))  # a push returns the stake: left out of ROI
        r = pd.DataFrame({'match': ah.index, 'lg': ah.lg, 'season': ah.season, 'mkt': 'AH', 'side': o, 'won': won_,
                          'pinO': po[:, i], 'pinC': pc[:, i], 'avgO': ao[:, i], 'avgC': ac[:, i],
                          'pinOodds': ah['PAHH' if i == 0 else 'PAHA'].values, 'pinCodds': ah['PCAHH' if i == 0 else 'PCAHA'].values,
                          'maxO': ah['MaxAHH' if i == 0 else 'MaxAHA'].values, 'maxC': ah['MaxCAHH' if i == 0 else 'MaxCAHA'].values,
                          'avgOodds': ah['AvgAHH' if i == 0 else 'AvgAHA'].values,
                          'avgCodds': ah['AvgCAHH' if i == 0 else 'AvgCAHA'].values})
        r['B365O'] = ah['B365AHH' if i == 0 else 'B365AHA'].values
        r['B365C'] = ah['B365CAHH' if i == 0 else 'B365CAHA'].values
        out.append(r)
    x = pd.concat(out, ignore_index=True)
    x['mid'] = x.mkt + ':' + x.match.astype(str)
    return x


def realized(x, price, fair, label, buckets=((0.01, 0.02), (0.02, 0.03), (0.03, 0.05), (0.05, 0.08), (0.08, 0.12), (0.12, 1.0))):
    """Bets where price x fair - 1 is in each bucket: expected EV, CLV vs the sharp close, ROI on results, and kept = CLV / EV."""
    ev = x[price] * x[fair] - 1
    clv = x[price] * x.pinC - 1
    roi = np.where(x.won.isna(), np.nan, x[price] * x.won - 1)
    rows = []
    for lo, hi in buckets:
        m = (ev >= lo) & (ev < hi) & x[price].notna()
        if m.sum() < 30:
            continue
        e = ev[m].mean()
        c = ci(clv[m].values, x.mid[m].values)
        r = ci(roi[m], x.mid[m].values)
        rows.append(f'{label:<34} EV {100 * lo:>4.0f}-{100 * hi:<4.0f}%  shown {100 * e:+6.2f}  CLV {fmt(c):<26} kept {100 * c[0] / e:+5.0f}%'
                    f'  ROI {fmt(r):<26} n={m.sum():,}')
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--dir', default=os.path.join(os.environ.get('TMPDIR', '/tmp'), 'football-data'))
    a = ap.parse_args()
    d = fetch(a.dir)
    x = outcomes(d)
    print(f'{len(d):,} matches ({d.season.min()}..{d.season.max()}, {d.lg.nunique()} leagues); {len(x):,} match-outcomes '
          f'(1X2 {x.mkt.eq("1X2").sum():,}, OU2.5 {x.mkt.eq("OU2.5").sum():,}, AH {x.mkt.eq("AH").sum():,})')

    # Sanity: the sharp close is the benchmark. Is it calibrated? (and the soft average?)
    rows = []
    for col, nm in (('pinC', 'Pinnacle close'), ('pinO', 'Pinnacle early'), ('avgC', 'Average close'), ('avgO', 'Average early')):
        y = x.dropna(subset=['won', col])
        brier = ((y[col] - y.won) ** 2).mean()
        ll = -(y.won * np.log(y[col]) + (1 - y.won) * np.log(1 - y[col])).mean()
        rows.append(f'{nm:<16} Brier {brier:.5f}  log-loss {ll:.5f}  n={len(y):,}')
    table('Accuracy of each fair (lower is better)', rows)

    # D. Favorite-longshot bias at the sharp close: ROI of betting Pinnacle's own closing price by its fair probability.
    rows = []
    y = x.dropna(subset=['won'])
    for lo, hi in ((0, .1), (.1, .2), (.2, .35), (.35, .5), (.5, .65), (.65, .8), (.8, 1.01)):
        m = (y.pinC >= lo) & (y.pinC < hi)
        wr = ci((y.won - y.pinC)[m].values, y.mid[m].values)
        rows.append(f'fair {lo:.2f}-{hi:.2f}: wins minus fair {fmt(wr)} pts   n={m.sum():,}')
    table('D. Calibration of the sharp close by price (wins - fair prob, in points; FLB = negative at the cheap end)', rows)

    # A. A soft book beats the sharp fair early.
    rows = []
    for b in ['B365', 'BW', 'WH', 'VC']:
        rows += realized(x, f'{b}O', 'pinO', f'{b} early vs Pinnacle early')
    rows += realized(x, 'maxO', 'pinO', 'Best price early vs Pinnacle early')
    rows += realized(x, 'maxC', 'pinC', 'Best price at close vs Pinnacle close')
    table('A. A price over the SHARP fair: what is kept at the sharp close and in results', rows)

    # B. The best price beats the CONSENSUS fair (no sharp book in the fair): early vs late.
    rows = realized(x, 'maxO', 'avgO', 'Best price early vs consensus early')
    rows += realized(x, 'maxC', 'avgC', 'Best price at close vs consensus close')
    rows += realized(x, 'B365O', 'avgO', 'bet365 early vs consensus early')
    rows += realized(x, 'pinOodds', 'avgO', 'Pinnacle early vs consensus early')
    table('B. A price over the CONSENSUS fair (Vigilant\'s method with no sharp book): early vs at the close', rows)

    # B by market
    rows = []
    for mk in ('1X2', 'OU2.5', 'AH'):
        y = x[x.mkt == mk]
        rows += realized(y, 'maxO', 'avgO', f'{mk}: best early vs consensus early', ((0.02, 0.05), (0.05, 1.0)))
        rows += realized(y, 'maxC', 'avgC', f'{mk}: best close vs consensus close', ((0.02, 0.05), (0.05, 1.0)))
    table('B by market', rows)

    # B by disagreement among the books: the best price's margin over the average price (how far out the best book is).
    rows = []
    out_ = x.maxO / x.avgOodds - 1
    for lo, hi in ((0, .03), (.03, .06), (.06, .10), (.10, 9)):
        y = x[(out_ >= lo) & (out_ < hi)]
        rows += realized(y, 'maxO', 'avgO', f'best {100 * lo:.0f}-{100 * hi:.0f}% over avg price', ((0.02, 0.05), (0.05, 1.0)))
    table('B by how far the best book is above the average book (one book far out = it may be the informed one, or wrong)', rows)

    # C. lambda: when a venue disagrees with the consensus early, how far does the close move toward that venue? Judged two ways: by the
    # sharp close (Pinnacle's own move, so biased toward Pinnacle) and by the consensus close (the average book's own move).
    rows = []
    y1 = x[x.mkt == '1X2'].copy()
    blend = []
    for venue, col in (('Pinnacle', 'pinO'), ('bet365', 'B365O'), ('Bet&Win', 'BWO'), ('William Hill', 'WHO'), ('BetVictor', 'VCO')):
        y = y1.dropna(subset=[col, 'avgO', 'avgC']).copy()
        if col != 'pinO':
            vq = (1 / y[col]).groupby(y.match).transform('sum')
            y = y[vq.groupby(y.match).transform('size') == 3] if False else y
            y['vf'] = (1 / y[col]) / vq  # the venue's own fair, proportional devig of its three prices
        else:
            y['vf'] = y.pinO
        gap = y.vf - y.avgO
        for lo, hi in ((0.01, 0.02), (0.02, 0.04), (0.04, 1)):
            m = (gap.abs() >= lo) & (gap.abs() < hi)
            r1 = ci(((y.pinC - y.avgO)[m] / gap[m]).clip(-3, 4).values, y.match[m].values)
            r2 = ci(((y.avgC - y.avgO)[m] / gap[m]).clip(-3, 4).values, y.match[m].values)
            rows.append(f'{venue:<13} off the consensus by {100 * lo:.0f}-{100 * hi:.0f} pts: Pinnacle close moves {fmt(r1)}%, average close '
                        f'{fmt(r2)}% of the way to it  n={m.sum():,}')
        # Benter's blend: logistic regression of the result on both fairs' logits; the weights say how much each one knows.
        z = y.dropna(subset=['won'])
        lg = lambda p: np.log(np.clip(p, 1e-4, 1 - 1e-4) / (1 - np.clip(p, 1e-4, 1 - 1e-4)))
        X = np.column_stack([lg(z.avgO.values), lg(z.vf.values)])
        w = np.zeros(2)
        for _ in range(30):
            pr = 1 / (1 + np.exp(-X @ w))
            g = X.T @ (z.won.values - pr)
            H = (X * (pr * (1 - pr))[:, None]).T @ X
            w = w + np.linalg.solve(H, g)
        blend.append(f'{venue:<13} result ~ consensus early x {w[0]:+.2f}  +  {venue} early x {w[1]:+.2f}   (share {100 * w[1] / w.sum():.0f}% {venue})')
    table('C. Who was informed? Share of the gap (venue early vs consensus early) the close moved toward the venue', rows)
    table('C2. Benter blend on the results (1X2, early prices): how much weight each fair earns', blend)

    # E. The sharp veto's bar: bets the CONSENSUS calls +EV (2.5%+, Vigilant's default edge), split by what the SHARP book's own fair says
    # about the same price. Early prices (the veto judges a live price, before the close), judged by the sharp close and the results.
    rows = []
    for price, nm in (('maxO', 'best price'), ('B365O', 'bet365')):
        y = x.dropna(subset=[price, 'avgO', 'pinO']).copy()
        cev = y[price] * y.avgO - 1
        sev = y[price] * y.pinO - 1
        y = y[cev >= 0.025]
        sev = sev[cev >= 0.025]
        for lo, hi in ((-1, -0.02), (-0.02, 0), (0, 0.01), (0.01, 0.02), (0.02, 0.04), (0.04, 9)):
            m = (sev >= lo) & (sev < hi)
            if m.sum() < 30:
                continue
            clv = (y[price] * y.pinC - 1)[m]
            roi = np.where(y.won[m].isna(), np.nan, (y[price] * y.won - 1)[m])
            rows.append(f'{nm:<10} consensus EV 2.5%+, sharp EV {100 * lo:>4.0f}..{100 * hi:<4.0f}%: shown {100 * (y[price] * y.avgO - 1)[m].mean():+5.2f}'
                        f'  CLV {fmt(ci(clv.values, y.mid[m].values)):<26} ROI {fmt(ci(roi, y.mid[m].values)):<26} n={m.sum():,}')
    table('E. Consensus says +EV; what the sharp book adds (the sharp veto today vetoes only at sharp EV <= 0)', rows)


    # F. Another AI's report (RESEARCH.md §73) claims lines drift toward favorites late ("bet favorites early, underdogs late"). Pinnacle early → close,
    # the favorite's fair probability change by its early fair, in points (+ = the favorite shortened), and whether betting it early beat the close.
    rows = []
    y = x[x.mkt != 'AH'].dropna(subset=['pinO', 'pinC'])
    fav = y[y.pinO >= 0.5]
    for lo, hi in ((0.5, 0.6), (0.6, 0.7), (0.7, 0.8), (0.8, 1.01)):
        m = (fav.pinO >= lo) & (fav.pinO < hi)
        rows.append(f'favorite early {lo:.1f}-{hi:.1f}: fair moved {fmt(ci((fav.pinC - fav.pinO)[m].values, fav.mid[m].values))} pts by the close  '
                    f'(CLV of its early Pinnacle price {fmt(ci((fav.pinOodds * fav.pinC - 1)[m].values, fav.mid[m].values))}%)  n={m.sum():,}')
    dog = y[y.pinO < 0.36]
    rows.append(f'underdog early under 0.36 (+180 or longer): shortened {100 * (dog.pinC > dog.pinO).mean():.0f}%, lengthened {100 * (dog.pinC < dog.pinO).mean():.0f}%  '
                f'(CLV of its early price {fmt(ci((dog.pinOodds * dog.pinC - 1).values, dog.mid.values))}%)  n={len(dog):,}')
    table('F. Do sharp prices drift toward favorites before the start? (Pinnacle early → close, 1X2 and over/under)', rows)


if __name__ == '__main__':
    main()
