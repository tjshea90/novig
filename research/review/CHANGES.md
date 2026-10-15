
## 2026-10-15 (no rule changed): auto-bet filters judged against the digest
Trap guard 6 h kept (CLV +2.0 to +2.9% inside 6 h vs -0.6 to -0.1% beyond); books-agree and kinds: no evidence of too strict. Plumbing fix instead: the engine re-reads listed bets' Novig books before the trader judges them (27 of 31 had been refused as "no Novig price in the last 3 minutes"). Metric to watch: engine orders per day and the held-back reasons in tradepass-<day>.jsonl. Revert rule: n/a (plumbing).
