#!/usr/bin/env python3
"""Does a free score feed ever tell you about a play before Novig's price moves? RESEARCH.md §95, re-runnable.

Tj, 2026-10-05: "investigate if I have any apis or if there are any free sources that are fast enough that I can profit from live betting on moving ... in your earlier
research you found a way to profit on novig live betting directly after a score or change in a live event. See if this is plausible to replicate."

The cross-line bursts of RESEARCH.md §84 come 5-13 s after ESPN's wall-clock stamp for the play (the stamp is the time of the play, to the second). A free feed
could only lead Novig if it PUBLISHES a play sooner than Novig's makers re-quote. This measures the three clocks for every play of a live game:

    W  the play's own wall-clock stamp (ESPN, to the second)
    S  when this script first SAW the play in ESPN's summary (it polls every POLL s, so S is late by at most POLL)
    M  when Novig's moneyline first traded away from where it was before the play (the tape of tools/research/novig_ladder_tape.py record, engine ms stamps)

    S - W = how stale ESPN's feed is.   M - W = how soon the market reacts.   M - S > 0 would be a lead for the free feed (a window); it is expected to be negative.

    python3 tools/research/espn_lead_lag.py record --sport football/nfl --event 401872979 --minutes 150 --out espn.ndjson
    python3 tools/research/espn_lead_lag.py kalshi --ticker KXNFLGAME-26OCT05ATLNO-NO --minutes 150 --out kalshi.ndjson     (Kalshi's price of the same game: free, keyless)
    python3 tools/research/espn_lead_lag.py analyze espn.ndjson tape-nfl.ndjson [--kalshi kalshi.ndjson] [--jump 0.02]

Needs no key. Polite: one request every POLL seconds. Clock note: S is this machine's clock, W is ESPN's, M is Novig's engine clock; the analysis estimates and prints the
offset it assumed (none: all three are wall clocks, good to about a second).
"""
import argparse, collections, json, statistics, sys, time, urllib.request
from datetime import datetime, timezone

POLL = 2.0


def now_ms():
    return int(time.time() * 1000)


def iso_ms(s):
    return int(datetime.strptime(s.replace('Z', '+0000'), '%Y-%m-%dT%H:%M:%S%z').timestamp() * 1000)


def get(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req, timeout=15) as r:
        return json.loads(r.read().decode())


def plays_of(d):
    out = []
    dr = d.get('drives', {})
    for drv in list(dr.get('previous', [])) + ([dr['current']] if dr.get('current') else []):
        out += drv.get('plays', [])
    return out


def record(a):
    url = f'https://site.api.espn.com/apis/site/v2/sports/{a.sport}/summary?event={a.event}'
    end = time.time() + a.minutes * 60
    out = open(a.out, 'a')
    seen = set()
    wp_seen = set()
    first = True
    print('polling', url, 'until', time.strftime('%H:%M:%S', time.localtime(end)), file=sys.stderr, flush=True)
    while time.time() < end:
        t0 = now_ms()
        try:
            d = get(url)
        except Exception as e:
            print('poll failed', e, file=sys.stderr, flush=True)
            time.sleep(POLL)
            continue
        t1 = now_ms()
        for p in plays_of(d):
            if p['id'] in seen:
                continue
            seen.add(p['id'])
            # The plays already there at the start are history: kept for the record, flagged so the analysis skips them.
            out.write(json.dumps(dict(k='p', id=p['id'], hist=first, wc=iso_ms(p['wallclock']) if p.get('wallclock') else None, s0=t0, s1=t1,
                                      txt=(p.get('text') or '')[:90], per=p.get('period', {}).get('number'), clk=p.get('clock', {}).get('displayValue'),
                                      sc=bool(p.get('scoringPlay')), a=p.get('awayScore'), h=p.get('homeScore'), typ=p.get('type', {}).get('text'))) + '\n')
        for w in d.get('winprobability', []):
            if w['playId'] in wp_seen:
                continue
            wp_seen.add(w['playId'])
            out.write(json.dumps(dict(k='w', id=w['playId'], hist=first, s0=t0, s1=t1, home=w['homeWinPercentage'])) + '\n')
        out.flush()
        first = False
        time.sleep(max(0.0, POLL - (now_ms() - t0) / 1000))


def kalshi(a):
    """Kalshi's yes price for one game-winner market, every 0.5 s (the app's own pace for Kalshi is 2 a second): the one free exchange with live game markets."""
    url = f'https://api.elections.kalshi.com/trade-api/v2/markets/{a.ticker}'
    end = time.time() + a.minutes * 60
    out = open(a.out, 'a')
    print('polling Kalshi', a.ticker, 'until', time.strftime('%H:%M:%S', time.localtime(end)), file=sys.stderr, flush=True)
    while time.time() < end:
        t0 = now_ms()
        try:
            m = get(url)['market']
            t1 = now_ms()
            out.write(json.dumps(dict(k='q', s0=t0, s1=t1, b=float(m['yes_bid_dollars']), a=float(m['yes_ask_dollars']), l=float(m['last_price_dollars']))) + '\n')
            out.flush()
        except Exception as e:
            print('kalshi poll failed', e, file=sys.stderr, flush=True)
            time.sleep(2)
        time.sleep(max(0.0, 0.5 - (now_ms() - t0) / 1000))


def first_move(pts, W, jump, lookback=30_000, horizon=60_000):
    """First time t >= W in [(t, price)] that the price is `jump` or more away from its median in the `lookback` ms before W; None when it never is within `horizon`."""
    before = [x for t, x in pts if W - lookback <= t < W]
    if len(before) < 2:
        return None
    ref = statistics.median(before)
    for t, x in pts:
        if t >= W and t - W <= horizon and abs(x - ref) >= jump:
            return t, x - ref
    return None


def moneyline_series(tape):
    """outcomeId -> [(ts, price)] of the moneyline market(s) in a tape, and each market's two outcome ids."""
    metas = {}
    for line in open(tape):
        r = json.loads(line)
        if r['k'] == 'meta' and r['type'] == 'MONEY':
            metas[r['m']] = r
    series = collections.defaultdict(list)
    for line in open(tape):
        r = json.loads(line)
        if r['k'] == 't' and r['m'] in metas:
            series[(r['m'], r['o'])].append((r['ts'], r['p']))
    for v in series.values():
        v.sort()
    return metas, series


def analyze(a):
    plays = {}
    for line in open(a.espn):
        r = json.loads(line)
        if r['k'] == 'p' and not r['hist'] and r['wc']:
            plays[r['id']] = r
    metas, series = moneyline_series(a.tape)
    if not plays or not series:
        print('no live plays or no moneyline trades in these files')
        return
    kal = []
    if a.kalshi:
        for line in open(a.kalshi):
            r = json.loads(line)
            if r['k'] == 'q':
                kal.append(((r['s0'] + r['s1']) // 2, (r['b'] + r['a']) / 2))
        kal.sort()
    rows = []
    for p in sorted(plays.values(), key=lambda r: r['wc']):
        W, S = p['wc'], (p['s0'] + p['s1']) // 2
        moves = []
        for (m, o), pts in series.items():
            before = [x for t, x in pts if W - 30_000 <= t < W]
            if len(before) < 2:
                continue
            ref = statistics.median(before)
            for t, x in pts:
                if t >= W and abs(x - ref) >= a.jump:
                    moves.append((t, x - ref))
                    break
        if not moves:
            rows.append((p, None, None))
            continue
        M, d = min(moves)
        rows.append((p, M, d))
    kalshi_of = {}
    if kal:
        for p, M, d in rows:
            km = first_move(kal, p['wc'], a.jump)
            kalshi_of[p['id']] = km[0] if km else None
    moved = [(p, M, d) for p, M, d in rows if M is not None and M - p['wc'] <= 60_000]
    print(f'{len(plays)} live plays seen; {len(moved)} moved the moneyline by {a.jump:.2f}+ within a minute of the play')
    if moved:
        sw = [(p['s0'] + p['s1']) // 2 - p['wc'] for p, _, _ in moved]
        mw = [M - p['wc'] for p, M, _ in moved]
        ms = [M - (p['s0'] + p['s1']) // 2 for p, M, _ in moved]
        q = lambda xs, f: sorted(xs)[min(len(xs) - 1, int(f * len(xs)))]
        print(f'  ESPN published (S-W, s):       median {statistics.median(sw)/1000:6.1f}  p10 {q(sw,.1)/1000:6.1f}  p90 {q(sw,.9)/1000:6.1f}')
        print(f'  Novig moved    (M-W, s):       median {statistics.median(mw)/1000:6.1f}  p10 {q(mw,.1)/1000:6.1f}  p90 {q(mw,.9)/1000:6.1f}')
        print(f'  free-feed lead (M-S, s):       median {statistics.median(ms)/1000:6.1f}  p10 {q(ms,.1)/1000:6.1f}  p90 {q(ms,.9)/1000:6.1f}   (positive = ESPN first)')
        print(f'  plays where ESPN showed the play BEFORE the market moved: {sum(1 for x in ms if x > 0)} of {len(ms)}; by 3 s or more: {sum(1 for x in ms if x >= 3000)}')
        if kal:
            both = [(p, M, kalshi_of[p['id']]) for p, M, d in moved if kalshi_of.get(p['id'])]
            if both:
                kn = [K - M for p, M, K in both]
                print(f'  Kalshi vs Novig (K-M, s): median {statistics.median(kn)/1000:6.1f}  p10 {q(kn,.1)/1000:6.1f}  p90 {q(kn,.9)/1000:6.1f}  over {len(both)} plays both moved   (positive = Novig first)')
                print(f'  plays where Kalshi moved BEFORE Novig: {sum(1 for x in kn if x < 0)} of {len(kn)}; by 2 s or more: {sum(1 for x in kn if x <= -2000)}')
        print('  per play (W-clock, S-W, M-W, M-S, K-M, move, play):')
        for (p, M, d), s_w, m_w, m_s in zip(moved, sw, mw, ms):
            K = kalshi_of.get(p['id'])
            ks = f'{(K - M)/1000:6.1f}' if K else '     -'
            print(f"    {time.strftime('%H:%M:%S', time.gmtime(p['wc']/1000))}  {s_w/1000:6.1f}  {m_w/1000:6.1f}  {m_s/1000:6.1f}  {ks}  {d:+.3f}  {p['typ']}: {p['txt'][:50]}")
    quiet = [p for p, M, d in rows if M is None]
    print(f'  plays with no moneyline move of {a.jump:.2f}+ (most of them): {len(quiet)}')


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest='cmd', required=True)
    r = sub.add_parser('record')
    r.add_argument('--sport', default='football/nfl')
    r.add_argument('--event', required=True)
    r.add_argument('--minutes', type=int, default=200)
    r.add_argument('--out', required=True)
    k = sub.add_parser('kalshi')
    k.add_argument('--ticker', required=True)
    k.add_argument('--minutes', type=int, default=200)
    k.add_argument('--out', required=True)
    z = sub.add_parser('analyze')
    z.add_argument('espn')
    z.add_argument('tape')
    z.add_argument('--kalshi')
    z.add_argument('--jump', type=float, default=0.02)
    a = ap.parse_args()
    {'record': record, 'kalshi': kalshi, 'analyze': analyze}[a.cmd](a)


if __name__ == '__main__':
    main()
