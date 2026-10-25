#!/usr/bin/env bash
# The `grade` task of .github/workflows/propline.yml (PS3, Tj 2026-10-25: "is the engine using the propline grade endpoint to grade my actual bets against the close?"):
# every real bet and bid (the phone's journal on phone-data: hand bets, the phone's auto-bet and imported Novig bets; the engine's own ledger trades.json on cloud-feed) graded by PropLine's
# POST /v1/clv/grade (Pinnacle-led closing line, ev_vs_close_pct), tools/propline_grade.py -> Release `propline-grade` (graded.csv.gz, SUMMARY.md; replaced each run).
set -u
python3 -I tools/propline_grade.py selftest || exit 1
git fetch -q --depth 1 origin phone-data cloud-feed || exit 1
mkdir -p in/bets out/grade
for f in $(git ls-tree --name-only origin/phone-data phone/bets/ | grep '/bets-.*\.jsonl$'); do git show "origin/phone-data:$f" > "in/bets/$(basename "$f")"; done
git show origin/cloud-feed:trades.json > in/trades.json 2>/dev/null || echo '{}' > in/trades.json
gh release download propline-grade -p graded.csv.gz -D in --clobber >/dev/null 2>&1 || true
python3 -I tools/propline_grade.py run --bets in/bets --trades in/trades.json --out out/grade --previous in/graded.csv.gz || exit 1
if [ -f out/grade/SUMMARY.md ]; then
  gh release view propline-grade >/dev/null 2>&1 || gh release create propline-grade --title "Real bets graded by PropLine's closing line" --notes "Tj's hand bets, the phone's auto-bets and the engine's bets and bids, graded by PropLine POST /v1/clv/grade (tools/propline_grade.py). Replaced each run."
  gh release upload propline-grade out/grade/* --clobber
fi
