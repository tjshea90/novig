# Bid side split: Over/Under and favorite/underdog (CH75, Tj 2026-10-23 "yes")
Data: engine-dataset `bids.csv.gz`, 575,842 ended bids, 2026-10-16 onward. Quality = mean `fill_ev` (fair after the fill vs price paid), a CLV-style measure, not results.
- Over: 226 fills of 217k bids, fill_ev +0.025 (se .001). Under: 362 fills of 205k, +0.023. Same quality; Unders fill 1.6x as often.
- Dog (fair<.5, non-total): 143 fills, +0.028 (.001). Fav: 78 fills, +0.019 (.002). Dogs fill 2x as often and are ~0.9 pt better on fill_ev (about 4 se).
- Team totals Under +0.026 vs Over +0.023; totals Under/Over both +0.028; prop Under +0.019 (129 fills) vs prop Over +0.031 (28 fills). No side effect on totals; prop Overs did NOT show worse fill_ev here (results, not fill_ev, are what made them lose in the 2026-10-17 study).
- Fill rates are tiny everywhere (0.0-0.4%), so fills are thin: league cuts are n<40.
Verdict: no evidence the Under/underdog tilt buys better quality; a small dog edge in fill_ev and more fills. Not enough for a class B change. Next: settle results by side once 200+ settled bid fills per side exist (join engine-grades).
