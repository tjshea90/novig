#!/usr/bin/env python3
"""Which books can stand behind a fair line, and which of them are one opinion (PX9, Tj 2026-10-25: "Make sure odds come from as many books as possible and are matched correctly ... not double counting
same companies or same odds providers").

Runs on GitHub (.github/workflows/propline.yml, task `bookstudy`) with the PropLine key; a session never sees the key. For every PropLine book (no `bookmakers=` filter: all of them) on the leagues
Vigilant scans it takes the two-way lines (moneyline, spread and total at every number the book posts, alternates included; period and team markets left out), removes each book's margin, and says:

  per book   lines it posts, games, median margin, how far its no-vig chance sits from the OTHER books' median on the same line (mean points, share within 1 point), how old its quotes are
             (now - the older side's `last_seen_at`), and whether Vigilant already asks PropLine for it (`OURS`, = PropLineClient.KNOWN)
  per pair   of books with 150+ shared lines: the share of lines where the two prices are identical, and where the two no-vig chances agree within 0.2 points ("one desk, another margin")

A book is worth adding as its own voice when it posts many lines, tracks the market (not a junk or stale book), and is not a copy of another book; a copy joins that book's company in
`engine/BookCompany` (Tj, 2026-10-11: a book is grouped only with a documented provider or a measured match of 80%+ over 100+ lines).

  python3 -I tools/propline_books.py run <outdir> [sport ...]      (needs PROPLINE_API_KEY)
  python3 -I tools/propline_books.py selftest
"""
import datetime
import json
import os
import statistics
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import propline  # noqa: E402

SPORTS = ("basketball_nba", "hockey_nhl", "football_nfl", "football_ncaaf", "baseball_mlb")
NOT_BOOKS = {"novig", "kalshi", "polymarket", "polymarket_us", "prophetx", "underdog", "prizepicks", "sleeper", "dabble", "parlayplay", "pick6"}


def american_to_decimal(p):
    p = float(p)
    return 1.0 + p / 100.0 if p > 0 else 1.0 + 100.0 / -p


def ts(x):
    try:
        return datetime.datetime.fromisoformat(str(x).replace("Z", "+00:00")).timestamp()
    except (TypeError, ValueError):
        return None


def two_way(e, m):
    """-> (line, price_a, price_b, older_seen_ts) for a full-game two-way market: a moneyline (line None), a spread (the home team's number) or a total (the number), else None."""
    mk = m.get("key")
    if mk not in ("h2h", "spreads", "totals") or m.get("period") not in (None, "", "full", "FULL") or m.get("team"):
        return None
    outs = [o for o in m.get("outcomes") or [] if o.get("price") is not None]
    if len(outs) != 2:
        return None
    if mk == "totals":
        over = [o for o in outs if (o.get("name") or "").lower() == "over"]
        under = [o for o in outs if (o.get("name") or "").lower() == "under"]
        if not over or not under or over[0].get("point") is None:
            return None
        a, b, line = over[0], under[0], float(over[0]["point"])
    else:
        home = [o for o in outs if o.get("side") == "home" or o.get("name") == e.get("home_team")]
        away = [o for o in outs if o.get("side") == "away" or o.get("name") == e.get("away_team")]
        if not home or not away or home[0] is away[0]:
            return None
        a, b = home[0], away[0]
        if mk == "spreads":
            if a.get("point") is None:
                return None
            line = float(a["point"])
        else:
            line = None
    seen = [ts(o.get("last_seen_at")) for o in (a, b)]
    return line, a["price"], b["price"], (min(seen) if all(seen) else None)


def collect(boards, now):
    """[(sport, events)] -> {(sport, event, market, line): {book: (p_first_side, margin, age_s or None, price_a, price_b, alternate)}}"""
    lines = {}
    for sport, events in boards:
        for e in events:
            for b in e.get("bookmakers") or []:
                bk = b.get("key")
                if not bk or bk in NOT_BOOKS:
                    continue
                for m in b.get("markets") or []:
                    t = two_way(e, m)
                    if t is None:
                        continue
                    line, pa, pb, seen = t
                    try:
                        qa, qb = 1.0 / american_to_decimal(pa), 1.0 / american_to_decimal(pb)
                    except (ValueError, ZeroDivisionError):
                        continue
                    if qa + qb <= 1.0:
                        continue
                    lines.setdefault((sport, e.get("id"), m.get("key"), line), {})[bk] = (qa / (qa + qb), qa + qb - 1.0, None if seen is None else now - seen, pa, pb, m.get("line_type") == "alternate")
    return lines


def median(v):
    v = sorted(v)
    return v[len(v) // 2] if v else None


def stats(lines, ours=()):
    """-> (per-book rows, pairs). A line counts for the per-book view when 5+ books post it; the comparison is to the median of the other books."""
    per = {}
    pair = {}
    for key, by in lines.items():
        if len(by) >= 5:
            for bk, (p, margin, age, pa, pb, alt) in by.items():
                others = [v[0] for k, v in by.items() if k != bk]
                d = abs(p - statistics.median(others)) * 100.0
                r = per.setdefault(bk, {"n": 0, "games": set(), "margin": [], "dev": [], "age": [], "alt": 0})
                r["n"] += 1
                r["games"].add((key[0], key[1]))
                r["margin"].append(margin)
                r["dev"].append(d)
                if age is not None:
                    r["age"].append(age)
                r["alt"] += alt
        bks = sorted(by)
        for i in range(len(bks)):
            for k in range(i + 1, len(bks)):
                c = pair.setdefault((bks[i], bks[k]), [0, 0, 0])
                c[0] += 1
                c[1] += (by[bks[i]][3], by[bks[i]][4]) == (by[bks[k]][3], by[bks[k]][4])
                c[2] += abs(by[bks[i]][0] - by[bks[k]][0]) <= 0.002
    rows = []
    for bk, r in per.items():
        rows.append({"book": bk, "ours": bk in ours, "lines": r["n"], "games": len(r["games"]), "margin": median(r["margin"]), "mad": sum(r["dev"]) / len(r["dev"]),
                     "within1": sum(1 for d in r["dev"] if d <= 1.0) / len(r["dev"]), "age_p50": median(r["age"]), "age_p90": (sorted(r["age"])[int(len(r["age"]) * 0.9)] if r["age"] else None),
                     "alt_share": r["alt"] / r["n"]})
    pairs = [{"a": a, "b": b, "n": n, "ident": same / n, "track": track / n} for (a, b), (n, same, track) in pair.items() if n >= 150]
    return sorted(rows, key=lambda r: -r["lines"]), sorted(pairs, key=lambda p: -p["track"])


def clusters(pairs, threshold=0.8):
    """Connected groups of books whose no-vig chances agree within 0.2 points on `threshold` of 150+ shared lines."""
    parent = {}

    def find(x):
        parent.setdefault(x, x)
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    for p in pairs:
        if p["track"] >= threshold:
            parent[find(p["a"])] = find(p["b"])
    groups = {}
    for x in list(parent):
        groups.setdefault(find(x), []).append(x)
    return [sorted(g) for g in groups.values() if len(g) > 1]


def report(rows, pairs, now_label):
    out = ["# PropLine books: coverage, quality and independence (PX9, %s)" % now_label, ""]
    out.append("## Per book (lines posted by 5+ books; deviation = how far its no-vig chance is from the other books' median, in points of chance)")
    out.append("book | ours | lines | games | margin | mean dev | within 1 pt | age p50/p90 s | alt share")
    for r in rows:
        out.append("%s | %s | %d | %d | %.1f%% | %.2f | %.0f%% | %s/%s | %.0f%%" % (r["book"], "yes" if r["ours"] else "NO", r["lines"], r["games"], (r["margin"] or 0) * 100, r["mad"], r["within1"] * 100,
                                                                                    "-" if r["age_p50"] is None else int(r["age_p50"]), "-" if r["age_p90"] is None else int(r["age_p90"]), r["alt_share"] * 100))
    out += ["", "## Each book's closest partners (share of 150+ shared lines where the two no-vig chances agree within 0.2 points): who may share its desk"]
    best = {}
    for p in pairs:
        best.setdefault(p["a"], []).append((p["track"], p["b"], p["n"]))
        best.setdefault(p["b"], []).append((p["track"], p["a"], p["n"]))
    for r in rows:
        top = sorted(best.get(r["book"], []), reverse=True)[:3]
        out.append("%s: %s" % (r["book"], ", ".join("%s %.0f%% (%d)" % (b, t * 100, n) for t, b, n in top) or "no partner with 150+ shared lines"))
    out += ["", "## Pairs that track each other (no-vig chance within 0.2 points on 70%+ of 150+ shared lines; identical = same two prices)"]
    for p in pairs:
        if p["track"] >= 0.7:
            out.append("%s + %s: tracks %.0f%%, identical %.0f%% of %d" % (p["a"], p["b"], p["track"] * 100, p["ident"] * 100, p["n"]))
    out += ["", "## Groups at 80% (one desk behind several books)"]
    for g in clusters(pairs):
        out.append(", ".join(g))
    return out


def cmd_run(outdir, sports):
    keys = propline.keys_from_env()
    i, _ = propline.best_key(keys, [propline.plan_of(k) for k in keys])
    if i is None:
        print("no usable key")
        return 0
    os.makedirs(outdir, exist_ok=True)
    now = time.time()
    boards = []
    for sp in sports:
        st, _, data = propline.request("/v1/sports/%s/odds" % sp, keys[i], {"markets": "h2h,spreads,totals"})
        body = propline.j(data)
        events = body if isinstance(body, list) else (body or {}).get("data") or []
        print("%s: HTTP %s, %d games" % (sp, st, len(events)))
        boards.append((sp, events))
    lines = collect(boards, now)
    rows, pairs = stats(lines, ours=set(propline.OURS))
    text = report(rows, pairs, time.strftime("%Y-%m-%d %H:%M UTC", time.gmtime()))
    with open(os.path.join(outdir, "bookstudy.md"), "w") as f:
        f.write("\n".join(text) + "\n")
    with open(os.path.join(outdir, "bookstudy.json"), "w") as f:
        json.dump({"rows": rows, "pairs": [p for p in pairs if p["track"] >= 0.5]}, f)
    print("\n".join(text))
    return 0


def selftest():
    e = {"id": "g1", "home_team": "H", "away_team": "A", "bookmakers": []}

    def book(k, home_price, away_price, point=-3.5, seen="2026-10-25T18:00:00Z"):
        return {"key": k, "markets": [{"key": "spreads", "outcomes": [{"name": "H", "side": "home", "point": point, "price": home_price, "last_seen_at": seen},
                                                                       {"name": "A", "side": "away", "point": -point, "price": away_price, "last_seen_at": seen}]}]}

    # six books on one line; "copy" has another margin but the same no-vig chance as "a"
    e["bookmakers"] = [book("a", -110, -110), book("copy", -105, -115), book("c", -108, -112), book("d", -112, -108), book("e", -110, -110), book("f", -120, 100)]
    lines = collect([("basketball_nba", [e])], 1792950000.0)
    assert len(lines) == 1 and len(next(iter(lines.values()))) == 6
    assert abs(next(iter(lines.values()))["a"][0] - 0.5) < 1e-9
    big = {}
    for g in range(160):          # 160 identical games so pairs reach 150 shared lines
        big[("basketball_nba", "g%d" % g, "spreads", -3.5)] = {"a": (0.5, 0.045, 60.0, -110, -110, False), "copy": (0.5, 0.045, 80.0, -105, -115, False), "c": (0.51, 0.04, 90.0, -108, -112, True),
                                                               "d": (0.49, 0.04, 90.0, -112, -108, False), "e": (0.5, 0.05, 60.0, -110, -110, False), "f": (0.40, 0.06, 5000.0, -120, 100, False)}
    rows, pairs = stats(big, ours={"a"})
    by = {r["book"]: r for r in rows}
    assert by["f"]["mad"] > 5 and by["a"]["mad"] < 1 and by["a"]["ours"] and not by["c"]["ours"]
    assert any(p["a"] == "a" and p["b"] == "copy" and p["track"] == 1.0 and p["ident"] == 0.0 for p in pairs)
    assert any(p["a"] == "a" and p["b"] == "e" and p["ident"] == 1.0 for p in pairs)
    assert ["a", "copy", "e"] == next(g for g in clusters(pairs) if "a" in g)
    assert two_way(e, {"key": "spreads", "period": "h1", "outcomes": []}) is None
    assert two_way(e, {"key": "totals", "team": "H", "outcomes": []}) is None
    print("propline_books selftest ok")
    return 0


if __name__ == "__main__":
    a = sys.argv[1:]
    if a[:1] == ["selftest"]:
        sys.exit(selftest())
    if a[:1] == ["run"]:
        sys.exit(cmd_run(a[1] if len(a) > 1 else "out/books", tuple(x for x in a[2:] if x) or SPORTS))
    print(__doc__)
    sys.exit(2)
