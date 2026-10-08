#!/usr/bin/env python3
"""Pinnodds /ws/feed tape recorder (TASKS.md PW1, RESEARCH.md section 116).  Records the raw socket, stamped on arrival.

    PINNODDS_KEY=... python3 tools/research/pinnodds_tape.py record --out tape.ndjson --seconds 120 [--sports 1,2,3,4,5,6,7,8,9,10,11,12,13] [--streams live]
    python3 tools/research/pinnodds_tape.py summary tape.ndjson

ONE socket per Pinnodds account (a second evicts the first), so never run two of these, or this and the app, at once.  Compression is OFF (the docs measure
p99 187 ms against 1241 ms).  Replies to the server's ping with {"type":"pong"}.  The key is read from the environment only and is never written to the tape.
Each tape line is {"t": local receive ms, "m": <the server's message verbatim>} (the first line is {"t":..,"open":true}).
"""
import argparse, asyncio, collections, json, os, ssl, statistics, sys, time

URL = 'wss://pinnodds.com/ws/feed?key='


def ssl_ctx():
    ctx = ssl.create_default_context()
    for f in ('/root/.ccr/ca-bundle.crt',):
        if os.path.exists(f):
            ctx.load_verify_locations(f)
    return ctx


async def record(a):
    import websockets
    key = os.environ.get('PINNODDS_KEY', '')
    if not key:
        sys.exit('set PINNODDS_KEY')
    out = open(a.out, 'w')
    sports = [int(x) for x in a.sports.split(',') if x]
    streams = a.streams.split(',')
    end = time.time() + a.seconds
    async with websockets.connect(URL + key, ssl=ssl_ctx(), proxy=os.environ.get('HTTPS_PROXY') or None, open_timeout=20,
                                  max_size=8 * 1024 * 1024, compression=None) as ws:
        out.write(json.dumps({'t': int(time.time() * 1000), 'open': True}) + '\n')
        await ws.send(json.dumps({'type': 'subscribe', 'streams': streams, 'sport_ids': sports}))
        while time.time() < end:
            try:
                raw = await asyncio.wait_for(ws.recv(), timeout=max(0.5, end - time.time()))
            except asyncio.TimeoutError:
                break
            t = int(time.time() * 1000)
            m = json.loads(raw)
            out.write(json.dumps({'t': t, 'm': m}, separators=(',', ':')) + '\n')
            if m.get('type') == 'ping':
                await ws.send(json.dumps({'type': 'pong'}))
    out.close()


def summary(path):
    types = collections.Counter(); lat = []; topics = collections.Counter(); ops = collections.Counter(); per_sport = collections.Counter()
    first = last = None
    for line in open(path):
        d = json.loads(line)
        if 'm' not in d:
            continue
        m = d['m']; t = d['t']; first = first or t; last = t
        types[m.get('type')] += 1
        if m.get('type') == 'live':
            ops[m.get('op')] += 1; topics[(m.get('topic') or '').split('/')[-1]] += 1; per_sport[m.get('sport_id')] += 1
            if m.get('ts'):
                lat.append(t - m['ts'])
    print('frames by type', dict(types)); print('live ops', dict(ops)); print('live channels', dict(topics)); print('live frames per sport', dict(per_sport))
    if first and last and last > first:
        print('span %.1fs, %.1f frames/s' % ((last - first) / 1000, sum(types.values()) / ((last - first) / 1000)))
    if lat:
        lat.sort(); q = lambda p: lat[min(len(lat) - 1, int(len(lat) * p))]
        print('recv - frame.ts ms  n=%d p50=%d p90=%d p99=%d max=%d' % (len(lat), q(.5), q(.9), q(.99), lat[-1]))


if __name__ == '__main__':
    p = argparse.ArgumentParser(); sp = p.add_subparsers(dest='cmd', required=True)
    r = sp.add_parser('record'); r.add_argument('--out', required=True); r.add_argument('--seconds', type=int, default=120)
    r.add_argument('--sports', default='1,2,3,4,5,6,7,8,9,10,11,12,13'); r.add_argument('--streams', default='live')
    s = sp.add_parser('summary'); s.add_argument('path')
    a = p.parse_args()
    if a.cmd == 'record':
        asyncio.run(record(a))
    else:
        summary(a.path)
