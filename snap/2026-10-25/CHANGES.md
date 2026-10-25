# API docs changes since the last snapshot

added 7, changed 8, removed 0

## Added
- parlayapi/parlay-api.com/blog/building-betting-agent-claude-2026-05-19.html
- parlayapi/parlay-api.com/changelog.html
- parlayapi/parlay-api.com/docs/versioning.html
- parlayapi/parlay-api.com/integrations.html
- propline/prop-line.com/api-versioning.html
- propline/prop-line.com/recipes.html
- propline/prop-line.com/sportsbook-accuracy.html

## Changed: data/kalshi-sports-series.json
```diff
-{"series":[{"additional_prohibitions":["Current players, coaches, and staff of the league, association, or organization(s) governing the event. For college leagues/associations specifically, or where otherwise appropriate (as identified by the Exchange), this applies to current players/coaches/staf
+{"series":[{"additional_prohibitions":["Current players, coaches, and staff of the league, association, or organization(s) governing the event. For college leagues/associations specifically, or where otherwise appropriate (as identified by the Exchange), this applies to current players/coaches/staf
```

## Changed: data/sharpapi-health.json
```diff
-{"newest_odds_age_s":2,"status":"ok"}
+{"newest_odds_age_s":0,"status":"ok"}
```

## Changed: data/sxbet-leagues.json
```diff
-{"status":"success","data":[{"leagueId":246,"label":"NCAAF","sportId":8,"popularity":2927728.344651,"eventsByType":{"game-lines":32}},{"leagueId":1,"label":"NBA","sportId":1,"popularity":2526968.617068,"eventsByType":{"first-half-lines":17,"first-period-lines":8,"game-lines":33,"second-period-lines
+{"status":"success","data":[{"leagueId":246,"label":"NCAAF","sportId":8,"popularity":2879473.03089,"eventsByType":{"game-lines":37}},{"leagueId":1,"label":"NBA","sportId":1,"popularity":2526968.617068,"eventsByType":{"first-half-lines":17,"first-period-lines":8,"game-lines":33,"second-period-lines"
```

## Changed: oddspapi/oddspapi.io/us/docs.html
```diff
-]</code></pre></div></div></section><section class="mb-8"><h2 class="text-2xl font-semibold mb-2">Host</h2><p>All requests use the host<!-- --> <a href="https://api.oddspapi.io" target="_blank" rel="noopener noreferrer" class="text-blue-500!">https://api.oddspapi.io</a></p></section><section class=
+]</code></pre></div></div></section><section class="mb-8"><h2 class="text-2xl font-semibold mb-2">Host</h2><p>All requests use the host<!-- --> <a href="https://api.oddspapi.io" target="_blank" rel="noopener noreferrer" class="text-blue-500!">https://api.oddspapi.io</a></p></section><section class=
```

## Changed: parlayapi/parlay-api.com/docs.html
```diff
-            <tr><td><code>500</code></td><td>Server error</td><td>Retry. If it persists, email <a href="/cdn-cgi/l/email-protection" class="__cf_email__" data-cfemail="c6b5b3b6b6a9b4b286b6a7b4aaa7bfeba7b6afe8a5a9ab">[email&#160;protected]</a></td></tr>
+            <tr><td><code>500</code></td><td>Server error</td><td>Retry. If it persists, email <a href="/cdn-cgi/l/email-protection" class="__cf_email__" data-cfemail="fc8f898c8c938e88bc8c9d8e909d85d19d8c95d29f9391">[email&#160;protected]</a></td></tr>
```

## Changed: pinnodds/pinnodds.com/docs.html
```diff
-    <a href="/cdn-cgi/l/email-protection#9cf5f2faf3dcecf5f2f2f3f8f8efb2fff3f1">Support</a>
+    <a href="/cdn-cgi/l/email-protection#8ae3e4ece5cafae3e4e4e5eeeef9a4e9e5e7">Support</a>
-    <a href="/cdn-cgi/l/email-protection#5e373038311e2e373030313a3a2d703d3133"><i>06</i>Support</a>
+    <a href="/cdn-cgi/l/email-protection#2b42454d446b5b424545444f4f5805484446"><i>06</i>Support</a>
-      <b>⚠ One SSE connection per account.</b> Opening a second SSE connection with the same key evicts the first. EventSource auto-reconnect after a network blip works fine (the dying old socket gets reaped before the new one registers). If you genuinely need two or more independent connections — 
+      <b>⚠ One SSE connection per account.</b> Opening a second SSE connection with the same key evicts the first. EventSource auto-reconnect after a network blip works fine (the dying old socket gets reaped before the new one registers). If you genuinely need two or more independent connections — 
-    <p style="color: var(--muted); font-size: 13px; margin-top:12px">Also listed in the <a href="https://registry.modelcontextprotocol.io" target="_blank" rel="noopener">official MCP registry</a> as <code>com.pinnodds/odds</code>. Questions: <a href="/cdn-cgi/l/email-protection#472e29212807372e2929
+    <p style="color: var(--muted); font-size: 13px; margin-top:12px">Also listed in the <a href="https://registry.modelcontextprotocol.io" target="_blank" rel="noopener">official MCP registry</a> as <code>com.pinnodds/odds</code>. Questions: <a href="/cdn-cgi/l/email-protection#7a13141c153a0a131414
-    <tr><td class="name">Payouts</td><td class="desc">Processed manually. Once your unpaid balance reaches <b>$50+</b> we'll reach out to arrange transfer (crypto, bank transfer, or PayPal — whichever you prefer). Faster payouts are available on request — email <a href="/cdn-cgi/l/email-protection#
+    <tr><td class="name">Payouts</td><td class="desc">Processed manually. Once your unpaid balance reaches <b>$50+</b> we'll reach out to arrange transfer (crypto, bank transfer, or PayPal — whichever you prefer). Faster payouts are available on request — email <a href="/cdn-cgi/l/email-protection#
-  <b>Need something we don't have?</b> Historical archives, custom rate above 30 req/sec, or a market we don't surface yet — email <a href="/cdn-cgi/l/email-protection#c9a0a7afa689b9a0a7a7a6adadbae7aaa6a4"><span class="__cf_email__" data-cfemail="9af3f4fcf5daeaf3f4f4f5fefee9b4f9f5f7">[email&#160;pr
+  <b>Need something we don't have?</b> Historical archives, custom rate above 30 req/sec, or a market we don't surface yet — email <a href="/cdn-cgi/l/email-protection#a3cacdc5cce3d3cacdcdccc7c7d08dc0ccce"><span class="__cf_email__" data-cfemail="650c0b030a25150c0b0b0a0101164b060a08">[email&#160;pr
-    <a href="/cdn-cgi/l/email-protection#bad3d4dcd5facad3d4d4d5dedec994d9d5d7"><span class="__cf_email__" data-cfemail="aac3c4ccc5eadac3c4c4c5ceced984c9c5c7">[email&#160;protected]</span></a>
+    <a href="/cdn-cgi/l/email-protection#acc5c2cac3ecdcc5c2c2c3c8c8df82cfc3c1"><span class="__cf_email__" data-cfemail="5b32353d341b2b323535343f3f2875383436">[email&#160;protected]</span></a>
```

## Changed: pinnwire/pinnwire.com/docs.html
```diff
-<p class="note">Every plan: all 13 sports, live + prematch, full line depth, specials. Buy directly at <a href="/#pricing">pinnwire.com/#pricing</a> (card checkout) — questions: <a href="/cdn-cgi/l/email-protection#f182909d9482b181989f9f86988394df929e9c"><span class="__cf_email__" data-cfemail="ceb
+<p class="note">Every plan: all 13 sports, live + prematch, full line depth, specials. Buy directly at <a href="/#pricing">pinnwire.com/#pricing</a> (card checkout) — questions: <a href="/cdn-cgi/l/email-protection#cdbeaca1a8be8dbda4a3a3baa4bfa8e3aea2a0"><span class="__cf_email__" data-cfemail="631
```

## Changed: propline/api.prop-line.com/openapi.json
```diff
-{"openapi":"3.1.0","info":{"title":"Player Props API","description":"Real-time player prop betting lines from Bovada. The-odds-api compatible format.","version":"0.1.0"},"paths":{"/v1/sports":{"get":{"tags":["Sports"],"summary":"List Sports","description":"List all available sports.","operationId":
+{"openapi":"3.1.0","info":{"title":"Player Props API","description":"Real-time player prop betting lines from Bovada. The-odds-api compatible format.","version":"0.1.0"},"paths":{"/v1/sports":{"get":{"tags":["Sports"],"summary":"List Sports","description":"List all available sports.","operationId":
```

