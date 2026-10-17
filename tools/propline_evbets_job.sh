#!/usr/bin/env bash
# The PropLine +EV bets job of .github/workflows/propline.yml (TCL75, Tj 2026-10-17): every row the engine's PropLine sweep journaled (Releases cloud-archive-*, propline-ev-*.jsonl.gz) graded against PropLine's
# own resolved-prop archive (Releases propline-archive-*), a flat CSV + statistics in Release `propline-ev-bets`, and the report to Tj when 300 / 600 / 1000 bets are graded (tools/propline_ev_bets.py).
set -u
python3 -I tools/propline_ev_bets.py selftest
mkdir -p in/ev in/arch out
for tag in $(gh release list --limit 200 --json tagName -q '.[].tagName' | grep '^cloud-archive-'); do gh release download "$tag" -p 'propline-ev-*.jsonl.gz' -D in/ev --clobber || true; done
for tag in $(gh release list --limit 200 --json tagName -q '.[].tagName' | grep '^propline-archive-'); do gh release download "$tag" -p 'resolved-*.csv.gz' -D in/arch --clobber || true; done
gh release view propline-ev-bets >/dev/null 2>&1 || gh release create propline-ev-bets --title "PropLine +EV bets: graded" --notes "Every PropLine +EV line the engine saw, one row per unique bet, graded with PropLine's own results (tools/propline_ev_bets.py). Replaced each run; reported.json remembers which report thresholds were sent."
gh release download propline-ev-bets -p reported.json -D out --clobber >/dev/null 2>&1 || true
ls in/ev in/arch 2>/dev/null | head -40
if ls in/ev/*.jsonl.gz >/dev/null 2>&1 && ls in/arch/*.csv.gz >/dev/null 2>&1; then
  git config --global user.name "github-actions[bot]"; git config --global user.email "41898282+github-actions[bot]@users.noreply.github.com"
  python3 -I tools/propline_ev_bets.py run --journals in/ev/*.jsonl.gz --archive in/arch/*.csv.gz --out out/evbets.csv.gz --summary out/evbets.md --state out/reported.json --notify
  gh release upload propline-ev-bets out/evbets.csv.gz out/evbets.md --clobber
  [ -f out/reported.json ] && gh release upload propline-ev-bets out/reported.json --clobber
else
  echo "no journals or no archive yet"
fi
