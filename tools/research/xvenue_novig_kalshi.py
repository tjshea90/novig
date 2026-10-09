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
            for e, status in novig_games(league):
                na, nh = e['description'].split(' @ ', 1)
                for ke in kev:
                    mk = ke.get('markets') or []
                    if len(mk) != 2: continue
                    subs = [m.get('yes_sub_title', '') for m in mk]
                    if not ((L.same_team(nh, subs[0]) and L.same_team(na, subs[1])) or (L.same_team(nh, subs[1]) and L.same_team(na, subs[0]))): continue
                    nm = novig_money(e['eventId'])
                    if not nm: break
                    mid, names, asks, bids = nm
                    # Novig outcome names are abbreviations: tie each to a Kalshi market by team name, via the event's description order is not reliable, so use the abbreviation's letters
                    for kmk in mk:
                        team = kmk.get('yes_sub_title', '')
                        abbr = kmk['ticker'].rsplit('-', 1)[-1]
                        n_out = next((n for n in names if n.upper() == abbr.upper()), None)
                        if n_out is None: continue
                        other = [n for n in names if n != n_out][0]
                        yes_ask, yes_bid, no_ask, no_bid = f(kmk.get('yes_ask_dollars')), f(kmk.get('yes_bid_dollars')), f(kmk.get('no_ask_dollars')), f(kmk.get('no_bid_dollars'))
                        live = status == 'OPEN_INGAME'
                        # cover A: buy team on Novig + NOT team on Kalshi; cover B: buy the other on Novig + team (YES) on Kalshi
                        for label, nov_price, k_price in (('NOVIG ' + n_out + ' + KALSHI NO', asks.get(n_out), no_ask), ('NOVIG ' + other + ' + KALSHI YES ' + n_out, asks.get(other), yes_ask)):
                            if nov_price is None or k_price is None: continue
                            fee_n = 0.03 * nov_price * (1 - nov_price) if live else 0.0
                            fee_k = 0.07 * k_price * (1 - k_price)
                            rows.append({'t': int(time.time() * 1000), 'league': league, 'event': e['description'], 'live': live, 'cover': label, 'novig': round(nov_price, 4), 'kalshi': round(k_price, 4), 'cost': round(nov_price + k_price, 4),
                                         'net': round(1 - nov_price - k_price - fee_n - fee_k, 4), 'gap_mid': None if yes_bid is None or yes_ask is None else round((yes_bid + yes_ask) / 2, 4),
                                         'novig_depth': (bids[other] or [0, 0])[1] if label.startswith('NOVIG ' + n_out) else (bids[n_out] or [0, 0])[1], 'kalshi_size': f(kmk.get('yes_ask_size_fp' if 'YES' in label else 'yes_bid_size_fp'))})
                    break
        if out:
            for r in rows: out.write(json.dumps(r) + '\n')
            out.flush()
        best = sorted(rows, key=lambda r: -r['net'])[:5]
        print(f"[{time.strftime('%H:%M:%S')}] {len(rows)} covers priced on {len({r['event'] for r in rows})} games; locked-profit covers {sum(r['net'] > 0 for r in rows)}; best nets {[ (r['net'], r['event'][:28], r['cover'][:24], r['live']) for r in best[:3]]}", flush=True)
        if mode != 'watch' or time.time() >= until: break
        time.sleep(20)


if __name__ == '__main__':
    main()
