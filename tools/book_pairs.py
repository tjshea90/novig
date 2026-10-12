#!/usr/bin/env python3
"""Which books quote the IDENTICAL price on the same line (TBC1). Reads SportsGameOdds sample files (the app's "Test and share sample" output: a header, then the raw /v2/events answer, which may be cut
at its size cap) and prints, for every pair of books with >= MIN lines in common, the share of lines with the same odds and the same point. Unrelated books match 2-4%; one trading desk 45-100%.
Usage: tools/book_pairs.py sample.txt [...]"""
import json, sys, itertools, collections, re
MIN = int(__import__("os").environ.get("MIN", 20))

def events(text):
    i = text.find('{"nextCursor"')
    if i < 0: i = text.find('{"success"')
    s = text[i:]
    dec = json.JSONDecoder()
    k = s.find('"data":[')
    if k < 0: return
    pos = k + len('"data":[')
    while pos < len(s):
        while pos < len(s) and s[pos] in ', \n': pos += 1
        if pos >= len(s) or s[pos] != '{': break
        try:
            obj, end = dec.raw_decode(s, pos)
        except Exception:
            break          # the answer was cut here
        yield obj
        pos = end

def lines(paths):
    seen = {}
    for p in paths:
        for ev in events(open(p, encoding='utf-8', errors='ignore').read()):
            for oid, o in (ev.get('odds') or {}).items():
                for book, q in (o.get('byBookmaker') or {}).items():
                    if not isinstance(q, dict) or not q.get('available', True): continue
                    key = (ev.get('eventID'), oid)
                    seen.setdefault(key, {})[book] = (str(q.get('odds')), str(q.get('spread') or q.get('overUnder') or ''))
    return seen

def main(paths):
    data = lines(paths)
    common = collections.Counter(); same = collections.Counter()
    for _, books in data.items():
        for a, b in itertools.combinations(sorted(books), 2):
            common[(a, b)] += 1
            if books[a] == books[b]: same[(a, b)] += 1
    print(f"{len(data)} lines")
    rows = [(same[k] / n, n, k) for k, n in common.items() if n >= MIN]
    for r, n, k in sorted(rows, reverse=True)[:60]:
        print(f"  {r*100:5.1f}% of {n:5d}  {k[0]} / {k[1]}")

if __name__ == '__main__':
    main(sys.argv[1:])
