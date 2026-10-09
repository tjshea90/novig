#!/usr/bin/env python3
"""Novig against Kalshi on the same games: is there ever a locked profit (YES at one venue + NO at the other for under $1 after fees), and how far apart are the two venues' prices?
RESEARCH.md section 121 (ways to profit through the Novig API).  Public reads only on both sides; sends nothing.

    python3 tools/research/xvenue_novig_kalshi.py snapshot [--out snap.ndjson]      one read of every matched game
    python3 tools/research/xvenue_novig_kalshi.py watch --minutes 8 --out snap.ndjson  repeat every ~20 s

Per matched game moneyline (Novig MONEY market <-> Kalshi `KX<LEAGUE>GAME` event, the same two teams): for each team T, buying T on Novig costs askN(T) and buying NOT-T on Kalshi costs
Kalshi's `no_ask` for T's market (= 1 - Kalshi's yes_bid); the cover pays $1 either way.  Cost = askN(T) + kalshi_no_ask(T).  Fees: Kalshi taker 0.07*P*(1-P) a contract (rounded up to the cent
per order, ignored here: a small order pays more), Novig 0 before the game and 0.03*P*(1-P) in play.  Reports locked profit if cost + fees < 1.
"""
import json, sys, time, re, os, unicodedata
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pinn_novig_lag as L

KALSHI = 'https://api.elections.kalshi.com/trade-api/v2'
SERIES = {'NFL': 'KXNFLGAME', 'NBA': 'KXNBAGAME', 'NHL': 'KXNHLGAME', 'MLB': 'KXMLBGAME', 'NCAAF': 'KXNCAAFGAME'}


def novig_games(league):
    out = []
    for status in ('OPEN_INGAME', 'OPEN_PREGAME'):
        st, b, _ = L.http(f"{L.NOVIG}/catalog/events?status={status}&league={L.urllib.request.quote(league)}&limit=100")
        if st != 200 or not b: continue
        for e in b.get('items', []):
            if ' @ ' not in (e.get('description') or ''): continue
            out.append((e, status))
    return out


def novig_money(eid):
    st, b, _ = L.http(f"{L.NOVIG}/catalog/markets?event={eid}&marketType=MONEY&limit=10")
    if st != 200 or not b or not b.get('items'): return None
    m = b['items'][0]
    st, bk, _ = L.http(f"{L.NOVIG}/catalog/markets/{m['marketId']}/book")
    if st != 200 or not bk: return None
    bids = {}
    for o in m['outcomes']:
        lv = (bk.get('orders') or {}).get(o['outcomeId']) or []
        bids[o['name']] = (max(float(x['price']) for x in lv), sum(int(x['qty']) for x in lv if abs(float(x['price']) - max(float(y['price']) for y in lv)) < 1e-9)) if lv else None
    names = [o['name'] for o in m['outcomes']]
    asks = {n: (None if bids[[x for x in names if x != n][0]] is None else 1 - bids[[x for x in names if x != n][0]][0]) for n in names}
    return m['marketId'], names, asks, bids


def kalshi_games(series):
    st, d, _ = L.http(f'{KALSHI}/events?series_ticker={series}&status=open&with_nested_markets=true&limit=200')
    return (d or {}).get('events', []) if d else []


def f(x):
    try: return float(x)
    except Exception: return None


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else 'snapshot'
    out = open(sys.argv[sys.argv.index('--out') + 1], 'a') if '--out' in sys.argv else None
    minutes = float(sys.argv[sys.argv.index('--minutes') + 1]) if '--minutes' in sys.argv else 0
    until = time.time() + minutes * 60
    while True:
        rows = []
        for league, series in SERIES.items():
            kev = kalshi_games(series)
            if not kev: continue
            index = {}   # a game is the same game only when the SET of team abbreviations is the same on both venues ("Green Bay" and "Tampa Bay" share a word)
            for ke in kev:
                mk = ke.get('markets') or []
                if len(mk) == 2: index[frozenset(m['ticker'].rsplit('-', 1)[-1].upper() for m in mk)] = (ke, mk)
            for e, status in novig_games(league):
                if status == 'OPEN_PREGAME' and e.get('startsTs', 0) - time.time() * 1000 > 30 * 3600_000: continue
                nm = novig_money(e['eventId'])
                if not nm: continue
                mid, names, asks, bids = nm
                hit = index.get(frozenset(n.upper() for n in names))
                if not hit: continue
                ke, mk = hit
                for kmk in mk:
                    abbr = kmk['ticker'].rsplit('-', 1)[-1]
                    n_out = next((n for n in names if n.upper() == abbr.upper()), None)
                    if n_out is None: continue
                    other = [n for n in names if n != n_out][0]
                    yes_ask, yes_bid, no_ask, no_bid = f(kmk.get('yes_ask_dollars')), f(kmk.get('yes_bid_dollars')), f(kmk.get('no_ask_dollars')), f(kmk.get('no_bid_dollars'))
                    live = status == 'OPEN_INGAME'
                    # cover A: buy the team on Novig + NOT the team on Kalshi; cover B: buy the other team on Novig + the team (YES) on Kalshi
                    for label, nov_price, k_price in (('NOVIG ' + n_out + ' + KALSHI NO', asks.get(n_out), no_ask), ('NOVIG ' + other + ' + KALSHI YES ' + n_out, asks.get(other), yes_ask)):
                        if nov_price is None or k_price is None: continue
                        fee_n = 0.03 * nov_price * (1 - nov_price) if live else 0.0
                        fee_k = 0.07 * k_price * (1 - k_price)
                        rows.append({'t': int(time.time() * 1000), 'league': league, 'event': e['description'], 'live': live, 'cover': label, 'novig': round(nov_price, 4), 'kalshi': round(k_price, 4), 'cost': round(nov_price + k_price, 4),
                                     'net': round(1 - nov_price - k_price - fee_n - fee_k, 4), 'gap_mid': None if yes_bid is None or yes_ask is None else round((yes_bid + yes_ask) / 2, 4),
                                     'novig_depth': (bids[other] or [0, 0])[1] if label.startswith('NOVIG ' + n_out) else (bids[n_out] or [0, 0])[1], 'kalshi_size': f(kmk.get('yes_ask_size_fp' if 'YES' in label else 'yes_bid_size_fp'))})
        if out:
            for r in rows: out.write(json.dumps(r) + '\n')
            out.flush()
        best = sorted(rows, key=lambda r: -r['net'])[:5]
        print(f"[{time.strftime('%H:%M:%S')}] {len(rows)} covers priced on {len({r['event'] for r in rows})} games; locked-profit covers {sum(r['net'] > 0 for r in rows)}; best nets {[ (r['net'], r['event'][:28], r['cover'][:24], r['live']) for r in best[:3]]}", flush=True)
        if mode != 'watch' or time.time() >= until: break
        time.sleep(20)




def fast(minutes, outpath):
    """Matched LIVE games only, each read about every 3 s on both venues (one Novig book + one Kalshi event per game): how long does a locked cover last, and how big is it?"""
    out = open(outpath, 'a')
    until = time.time() + minutes * 60
    matched = {}; last_match = 0
    while time.time() < until:
        if time.time() - last_match > 90:
            matched = {}
            for league, series in SERIES.items():
                kev = kalshi_games(series)
                index = {}
                for ke in kev:
                    mk = ke.get('markets') or []
                    if len(mk) == 2: index[frozenset(m['ticker'].rsplit('-', 1)[-1].upper() for m in mk)] = ke
                for e, status in novig_games(league):
                    if status != 'OPEN_INGAME': continue
                    nm = novig_money(e['eventId'])
                    if not nm: continue
                    mid, names, asks, bids = nm
                    ke = index.get(frozenset(n.upper() for n in names))
                    if ke: matched[e['eventId']] = (league, e['description'], mid, names, ke['event_ticker'])
            last_match = time.time()
            print(f"[{time.strftime('%H:%M:%S')}] matched live games: {[v[1][:30] for v in matched.values()]}", flush=True)
        n_locked = 0
        for eid, (league, desc, mid, names, kticker) in matched.items():
            t0 = time.time()
            st, bk, _ = L.http(f"{L.NOVIG}/catalog/markets/{mid}/book")
            st2, kd, _ = L.http(f'{KALSHI}/markets?event_ticker={kticker}&limit=5')
            if st != 200 or not bk or st2 != 200 or not kd: continue
            t1 = time.time()
            m = None
            stm, md, _ = L.http(f"{L.NOVIG}/catalog/markets/{mid}") if False else (0, None, None)
            bids = {}
            for oid, lv in (bk.get('orders') or {}).items():
                if lv: bids[oid] = (max(float(x['price']) for x in lv), sum(int(x['qty']) for x in lv if abs(float(x['price']) - max(float(y['price']) for y in lv)) < 1e-9))
            # outcome ids -> names come from the market read at matching time: refetch lazily once
            if 'ids' not in globals(): globals()['ids'] = {}
            if mid not in ids:
                s3, md, _ = L.http(f"{L.NOVIG}/catalog/markets/{mid}")
                ids[mid] = {o['outcomeId']: o['name'] for o in md['outcomes']} if md else {}
            nb = {ids[mid].get(oid): v for oid, v in bids.items()}
            for kmk in kd.get('markets', []):
                abbr = kmk['ticker'].rsplit('-', 1)[-1].upper()
                n_out = next((n for n in names if n.upper() == abbr), None)
                if n_out is None: continue
                other = [n for n in names if n != n_out][0]
                yes_ask, no_ask = f(kmk.get('yes_ask_dollars')), f(kmk.get('no_ask_dollars'))
                for label, bidside, k_price, ksize in (('NOVIG ' + n_out + ' + KALSHI NO', other, no_ask, f(kmk.get('yes_bid_size_fp'))), ('NOVIG ' + other + ' + KALSHI YES ' + n_out, n_out, yes_ask, f(kmk.get('yes_ask_size_fp')))):
                    b = nb.get(bidside)
                    if b is None or k_price is None: continue
                    nov_price = 1 - b[0]
                    fee_n = 0.03 * nov_price * (1 - nov_price); fee_k = 0.07 * k_price * (1 - k_price)
                    net = 1 - nov_price - k_price - fee_n - fee_k
                    n_locked += net > 0
                    out.write(json.dumps({'t': int(t0 * 1000), 'span_ms': int((t1 - t0) * 1000), 'league': league, 'event': desc, 'cover': label, 'novig': round(nov_price, 4), 'kalshi': k_price, 'net': round(net, 4), 'novig_depth': b[1], 'kalshi_size': ksize}) + '\n')
        out.flush()
        time.sleep(1.5)
    print('done')


if __name__ == '__main__':
    if len(sys.argv) > 1 and sys.argv[1] == 'fast':
        fast(float(sys.argv[sys.argv.index('--minutes') + 1]), sys.argv[sys.argv.index('--out') + 1])
    else:
        main()
