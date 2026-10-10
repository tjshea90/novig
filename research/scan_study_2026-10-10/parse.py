import json, sys, math, random, collections
import pandas as pd
F='/root/.claude/uploads/e24cce0e-cdce-51ce-b7d7-f01dce035d93/5d6ed178-vigilant-scan-study-v0.85.3-2026-10-10-0302.txt'
lines=open(F).read().split('\n')
i0=next(i for i,l in enumerate(lines) if l.startswith('== EVERY BET'))
rows=[]
for l in lines[i0+1:]:
    if l.startswith('{'):
        try: rows.append(json.loads(l))
        except Exception as e: pass
df=pd.DataFrame(rows)
print(len(df), 'cols', list(df.columns)[:60])
df.to_pickle('bets.pkl')
