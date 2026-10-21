#!/usr/bin/env bash
# The CLV job of .github/workflows/propline.yml (TCL56): the engine's bets (Release engine-dataset) against PropLine's archive (Releases propline-archive-*), Pinnacle's devigged close as the fair.
set -u
python3 -I tools/propline_clv.py selftest
mkdir -p in/lines in/arch out
gh release download engine-dataset -p 'lines-*.csv.gz' -D in/lines --clobber || true
for tag in $(gh release list --limit 200 --json tagName -q '.[].tagName' | grep '^propline-archive-'); do gh release download "$tag" -p 'resolved-*.csv.gz' -D in/arch --clobber || true; done
ls in/lines in/arch | head -60
if ls in/lines/*.csv.gz >/dev/null 2>&1 && ls in/arch/*.csv.gz >/dev/null 2>&1; then
  python3 -I tools/propline_clv.py run --lines in/lines/*.csv.gz --archive in/arch/*.csv.gz --out out/propline-clv.csv.gz --summary out/propline-clv.md
  gh release view propline-clv >/dev/null 2>&1 || gh release create propline-clv --title "PropLine CLV of the engine's bets" --notes "Closing-line value of every bet the engine saw, from PropLine's resolved-prop archive (tools/propline_clv.py). Replaced by each run."
  gh release upload propline-clv out/propline-clv.csv.gz out/propline-clv.md --clobber
  # The engine's own orders (auto-bets and bid fills) at the price each was filled at, against the same closes (CH57, tools/orders_clv.py): Release engine-dataset, replaced each run.
  python3 -I tools/orders_clv.py selftest
  if gh api -H "Accept: application/vnd.github.raw" "repos/${GITHUB_REPOSITORY:-tjshea90/novig}/contents/trades.json?ref=cloud-feed" > in/trades.json 2>/dev/null && [ -s in/trades.json ]; then
    python3 -I tools/orders_clv.py run --trades in/trades.json --clv out/propline-clv.csv.gz --out out/orders-clv.csv.gz --summary out/orders-clv.md && \
      gh release upload engine-dataset out/orders-clv.csv.gz out/orders-clv.md --clobber || echo "orders clv not uploaded"
  else
    echo "no trades.json on cloud-feed"
  fi
else
  echo "no lines or no archive yet"
fi
