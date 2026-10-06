"""Shared loader and statistics for the v0.70.1 scan-study analysis (every analyst uses these, so every number is defined the same way).

    import sys; sys.path.insert(0, '<out_dir>/lib')   # <out_dir> is what extract.py was given
    from load import load, looks, clv_stats, roi_stats, by, split_by_date, DATA
    df = load()          # one row per bet (2080), flat columns; see COLUMNS below

Run Python as plain `python3 script.py` from a work directory of your own (not the data directory).
"""
import json
import os
import numpy as np
import pandas as pd

# lib/ and study/ are siblings (extract.py makes both): the data is found relative to this file, wherever the pair is put.
DATA = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'study') + os.sep
ET = 'America/New_York'

COLUMNS = """
id, src (c/w/v combos), lister_c/lister_v/lister_w (bool), screen (None = shown by the app's list), shown (bool), league, sport, kind, market, selection, event,
firstSeenMs, startsAtMs, first_et (Timestamp, Eastern), start_et, firstDay / startDay (Eastern dates), hour (Eastern hour of the first look), game (event|startsAtMs: the CLUSTER for CIs),
status (WON/LOST/PENDING/VOID), profit (units at the first-listed price, 1u staked; NaN while open), settled (bool),
american, cost (= implied prob = $ per $1 payout), ev (listed EV at first look), fair, edge_cost (fair - cost),
closeFair, closeAmerican, closeVia, close_src (pinnacle/espn/novig_trades/tracker/none), close_trades (N trades behind a Novig-trades close, else NaN), has_close (bool),
clv (= closeFair/cost - 1 at the first-listed price; NaN without a close), clvBest, clvLast, bestAmerican, lastAmerican, novigClose,
available (Novig $ at the price), minToStartFirst, lastListedMinToStart, listedMin, gone, looks, placedByTj, placedAmerican,
cnoBooks, booksTwoSided, booksAgreeing, agreeShare, sharpVerdict (PASSED / VETOED / NO_SHARP / NaN = no book page read),
close_ok (a close that is not a 1-2 trade Novig-trades price), clv_ok (clv where close_ok, else NaN),
ab_* = atBet fields: ab_version, ab_preset, ab_scanner, ab_how, ab_sharpBook, ab_sharpEv, ab_checkEv, ab_checkFair, ab_verdict, ab_fairMethod, ab_pinnacleOnly, ab_cnoOneWay, ab_cnoListAgeSec, ab_fairAgeSec, ab_novigAgeSec, ab_pinnacleAgeSec, ab_fullKelly, ab_otherAmerican, ab_twoSided, ab_oneSided, ab_agreeing,
s (raw list of looks: [minBeforeStart, kind, american, ev, fair, books, dollars, agreeing, companiesBothSides, checkEv, sharpVerdict]), cols (CNO's printed columns)
""".strip()


def _close_src(via):
    if not isinstance(via, str):
        return 'none'
    v = via.lower()
    if 'pinnacle' in v:
        return 'pinnacle'
    if 'espn' in v:
        return 'espn'
    if 'novig' in v and 'last trades' in v:
        return 'novig_trades'
    if v.startswith('tracker'):
        return 'tracker'
    return 'other'


def _trades(via):
    if isinstance(via, str) and "last trades (" in via:
        try:
            return int(via.split('(')[1].split(')')[0])
        except Exception:
            return np.nan
    return np.nan


def load(path=DATA + 'study_bets.jsonl'):
    rows = []
    for line in open(path):
        d = json.loads(line)
        ab = d.get('atBet') or {}
        r = {k: v for k, v in d.items() if k not in ('atBet', 'vig', 'cno')}
        for k, v in ab.items():
            if k in ('books', 'dissent', 'fairBooks', 'fairSharp'):
                r['ab_' + k] = v   # lists/dicts kept raw
            else:
                r['ab_' + k] = v
        r['vig'] = d.get('vig')
        r['cno'] = d.get('cno')
        rows.append(r)
    df = pd.DataFrame(rows)
    df['first_et'] = pd.to_datetime(df.firstSeenMs, unit='ms', utc=True).dt.tz_convert(ET)
    df['start_et'] = pd.to_datetime(df.startsAtMs, unit='ms', utc=True).dt.tz_convert(ET)
    df['firstDay'] = df.first_et.dt.date
    df['startDay'] = df.start_et.dt.date
    df['hour'] = df.first_et.dt.hour
    df['game'] = df.event.astype(str) + '|' + df.startsAtMs.astype(str)
    df['shown'] = df.screen.isna()
    for ch in 'cvw':
        df['lister_' + ch] = df.src.str.split('+').apply(lambda p, ch=ch: ch in p)
    df['settled'] = df.status.isin(['WON', 'LOST', 'PUSH'])
    df['close_src'] = df.closeVia.apply(_close_src)
    df['close_trades'] = df.closeVia.apply(_trades)
    df['has_close'] = df.clv.notna()
    # A Novig-trades close of 1-2 trades is a price, not a close (the study file says so): close_ok = a close that is not that.
    df['close_ok'] = df.has_close & ~((df.close_src == 'novig_trades') & (df.close_trades < 3))
    df['clv_ok'] = df.clv.where(df.close_ok)
    df['edge_cost'] = df.fair - df.cost
    return df


def looks(df):
    """One row per look (exploded from `s`): id, game, i (look number), minToStart, kind, american, ev, fair, books, dollars, agreeing, companies, checkEv, sharp."""
    out = []
    for _, r in df.iterrows():
        for i, x in enumerate(r['s'] or []):
            x = list(x) + [None] * (11 - len(x))
            out.append((r['id'], r['game'], i, *x[:11]))
    return pd.DataFrame(out, columns=['id', 'game', 'i', 'minToStart', 'kind', 'american', 'ev', 'fair', 'books', 'dollars', 'agreeing', 'companies', 'checkEv', 'sharp'])


def _cluster_boot(values, games, n=2000, seed=0):
    """Mean of `values` with a 95% CI that resamples GAMES (the cluster), not rows. Deterministic."""
    v = np.asarray(values, float)
    g = pd.factorize(np.asarray(games))[0]
    k = g.max() + 1 if len(g) else 0
    if len(v) == 0:
        return (np.nan, np.nan, np.nan, 0)
    sums = np.bincount(g, weights=v, minlength=k)
    cnts = np.bincount(g, minlength=k).astype(float)
    rng = np.random.default_rng(seed)
    idx = rng.integers(0, k, size=(n, k))
    boots = sums[idx].sum(1) / cnts[idx].sum(1)
    return (v.mean(), np.percentile(boots, 2.5), np.percentile(boots, 97.5), k)


def clv_stats(sub, n=2000):
    """CLV over the rows with a close: n bets, games, mean CLV, 95% game-cluster CI, share that beat the close (clv > 0)."""
    s = sub[sub.clv.notna()]
    m, lo, hi, k = _cluster_boot(s.clv.values, s.game.values, n)
    return dict(n=len(s), games=k, clv=m, lo=lo, hi=hi, beat=float((s.clv > 0).mean()) if len(s) else np.nan)


def roi_stats(sub, n=2000):
    """ROI at the first-listed price (1 unit a bet) over SETTLED bets (WON/LOST/PUSH): n, games, mean profit per unit, 95% game-cluster CI."""
    s = sub[sub.profit.notna()]
    m, lo, hi, k = _cluster_boot(s.profit.values, s.game.values, n)
    return dict(n=len(s), games=k, roi=m, lo=lo, hi=hi)


def by(df, col, fn=None, min_n=1):
    """Group by `col` (or list of cols) and report counts, CLV (game-cluster CI) and ROI for each group."""
    fn = fn or (lambda g: {**{('clv_' + a): b for a, b in clv_stats(g, 500).items()}, **{('roi_' + a): b for a, b in roi_stats(g, 500).items()}, 'ev_listed': g.ev.mean(), 'bets': len(g)})
    rows = []
    for key, g in df.groupby(col, dropna=False):
        if len(g) < min_n:
            continue
        rows.append({'group': key, **fn(g)})
    return pd.DataFrame(rows)


def split_by_date(df, col='firstDay'):
    """(first_half, second_half) split by DATE so that each half has about the same number of GAMES; never random. Returns (a, b, cut_day)."""
    days = sorted(df[col].unique())
    games_per_day = df.groupby(col).game.nunique()
    cum = games_per_day.reindex(days).cumsum()
    cut = cum[cum >= cum.iloc[-1] / 2].index[0]
    a = df[df[col] <= cut]
    b = df[df[col] > cut]
    return a, b, cut


def rule_report(df, mask, label='rule', n_boot=2000):
    """Everything a strategy needs reported, for the bets picked by boolean `mask` (aligned to df): bets, games, CLV (all closes) and CLV on close_ok with game-cluster 95% CIs,
    share beating the close, ROI at the first-listed price, expected bets and games a DAY (over the days the study logged), average Novig $ available, and the same on each DATE HALF
    (split_by_date on firstDay: first-half rules are fitted, second-half is the check)."""
    sub = df[mask]
    a, b, cut = split_by_date(df)
    days = max(1.0, (df.firstSeenMs.max() - df.firstSeenMs.min()) / 86_400_000.0)   # the study's real span in days (about 2.5), not calendar dates
    out = dict(label=label, bets=len(sub), games=int(sub.game.nunique()), per_day=len(sub) / days, games_per_day=sub.game.nunique() / days,
               avg_available=float(sub.available.mean()) if len(sub) else np.nan, ev_listed=float(sub.ev.mean()) if len(sub) else np.nan,
               clv_all=clv_stats(sub, n_boot), clv_ok=clv_stats(sub.assign(clv=sub.clv_ok), n_boot), roi=roi_stats(sub, n_boot), cut_day=str(cut))
    for name, half in (('first_half', a), ('second_half', b)):
        h = sub[sub.index.isin(half.index)]
        out[name] = dict(bets=len(h), games=int(h.game.nunique()), clv=clv_stats(h, 500), roi=roi_stats(h, 500))
    return out
