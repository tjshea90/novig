import csv,gzip,glob,pickle,sys,collections
S=sys.argv[1]
rows={}
for f in glob.glob(S+'/pl/*/resolved-*-novig-*.csv.gz'):
    for r in csv.DictReader(gzip.open(f,'rt')):
        if r['player_name'].startswith('(Watermark)'): continue
        rows[r['outcome_id'] or (r['event_id'],r['market'],r['player_name'],r['outcome_name'],r['line'])]=r
rows=list(rows.values())
print(len(rows))
c=collections.Counter((r['sport_key'],r['market'] if not r['player_name'] else 'PROP') for r in rows)
for k,v in c.most_common(30): print(k,v)
print(min(r['commence_time'] for r in rows), max(r['commence_time'] for r in rows))
print(collections.Counter(r['resolution'] for r in rows))
print(sum(1 for r in rows if r['actual_value']), sum(1 for r in rows if r['closing_price']), sum(1 for r in rows if r['opening_price']))
pickle.dump(rows,open(S+'/tr/novig.pkl','wb'))
