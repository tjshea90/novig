# active-memory handoff: novig (Vigilant app and GitHub engine), acct-27a37e (tj Gmail) session

**Handoff #1** · 2026-10-18 · Lineage: #1 (2026-10-18): account naming, merge and cleanup, nine-site API review, Sportmicro probe, Novig false-alert fix, v0.117.3 release in flight

## 0. Instructions for Claude (read first)

You are continuing work from a previous chat. That chat is gone; this file is the complete context and the source of truth.

1. Read this whole file before replying.
2. Follow sections 3 (Style), 4 (Hard rules) and 5 (Corrections) in every reply, for the rest of this chat. They override your defaults.
3. Use the values in section 8 exactly. Never round, re-estimate, or "correct" them.
4. Do not suggest anything listed in section 7 (Changed / rejected) again unless the user brings it up.
5. Code word: None active. If the user types /amcodeword, start every reply with the phrase they choose (default "Yes Boss!").
6. Your first reply: at most 5 lines covering the goal, the current state, and the next step (section 11). Mention any files from section 13 that were not attached. Ask the questions in section 12 if there are any. End with "Ready to continue with <next step>?" Then wait for the user's go.

(Repo note: `CLAUDE.md`, `CHECKPOINT.md`, `TASKS.md` and the SessionStart briefing in the repo root already carry the project's standing rules; this file only adds this session's state.)

## 1. Mission
- **Goal:** Keep Vigilant (Android app, Kotlin + Compose, package `com.tjshea.vigilant`) and its GitHub-hosted engine profitable on the Novig sportsbook, and make the three Claude accounts work as one team.
- **Done looks like:** Tj gets releases as plain Release links; the engine runs without false alerts; `CHECKPOINT.md` and `TASKS.md` let any account resume cold.
- **Context:** Repo tjshea90/novig (public). This session's account is acct-27a37e (tj Gmail), which holds the repo lease.

## 2. About the user (as relevant to this work)
- Tj, owner of the repo and the three Claude accounts. Android 16 target, Moto G 2026. Gives short chat commands, often "force" to take the lease. Wants usage saved and progress never lost.

## 3. Style & communication
- **Language:** English.
- **Tone:** direct, short, plain.
- **Reply length:** brief; lead with the result, then a few bullets.
- **Formatting:** plain text and short bullets; release links as bare URLs on their own line, never in code blocks.
- **Working style:** act on clear requests; say what was verified and what was not.
- **Avoid:** claiming something works before it is confirmed; long recaps.

## 4. Hard rules (word for word)
1. "Any time you are referred to by your session id account number, 27A37E, add the name tj Gmail so I know which account it is" (also mirage for acct-5d1e2d, tj outlook for acct-7542bf)
2. Only STOP ALL (`killed`) and the dead-man rules are off limits to every session; any other setting or switch may be changed with `python3 tools/engine_set.py path=value --why "..."`.
3. No `curl` egress from this shell (a hook blocks it); use WebFetch or the GitHub tools; keys never printed, committed or pasted in chat.
4. After each step run `bash tools/ckpt.sh "did" "next"`.
5. Releases: `bash ship.sh "note"` (bump versionCode first), trigger `release.yml`, confirm with `get_release_by_tag`, run `bash tools/record-release.sh vX.Y.Z <code> "note"`, send the link.
6. No multi-agent Workflows or subagent fan-out for this repo.
7. A message typed to a session that does not own the chat is relayed; the hook says do not act unless Tj says "force".

## 5. Corrections log
| # | Claude did | The user wanted |
|---|---|---|
| 1 | Held off acting when the hook said stand down | Tj answered "Force" each time; "force" takes the lease and authorizes the action |

## 6. Decisions
| Decision | Why |
|---|---|
| Sportmicro tested through a GitHub Actions probe with repo secret `SPORTMICRO_API_KEY` | No app change needed; key never in chat |
| `NovigErrorWatch` counts only error lines starting with "Novig" | A ParlayAPI 502 raised a false "Novig is failing" alert and a 10 s to 2 min scan wait |
| Old `ccr-*` and `claude/*` branches are not deleted by sessions | Irreversible; list is in `research/coordination/BRANCH_AUDIT_2026-10-18.md` |
| `TASKS.md` triage closes nothing automatically | Done-ness not proven; see `research/TASKS_TRIAGE_2026-10-18.md` |

## 7. Changed / rejected
- Sportmicro in-app key field and Test button → GitHub Actions probe (no app change)
- Flashscore scraper (omkar.cloud) ❌ free plan is 1,000 requests a month, too small for live polling
- odds-api.net ($30 for Pinnacle + Betfair exchange) ❌ not recommended: no in-play, no trial, we already have Pinnacle; Tj's call
- thesports.com, coinsqueens, sitepoint, Postman, betting.exchange ❌ nothing useful

## 8. Data & facts (exact)
| Item | Value |
|---|---|
| Release in flight | v0.117.3, versionCode 336 (previous v0.117.2, code 335) |
| Release run | Actions run 39157500000 (`release.yml`) |
| Ship gate | 3,931 tests, 3,902 passed, 29 skipped, Gradle exit 0 |
| Latest checkpoints | ckpt 3659 (watch fix), 3660 (audit + ENGINE_ADMIN rule) |
| bidDeployKelly | 20 (was 13; acct-7542bf (tj outlook) set it) |
| Sportmicro socket | `wss://{sport}.sportmicro.com/live/score?token=KEY`, header `Authorization: Bearer KEY`, sports basketball, hockey, american-football |
| First keyed probe run | 403 everywhere, REST body "error code: 1010" (Cloudflare browser-signature block) |
| Fix applied | browser User-Agent on REST and websocket; re-run queued 180 s |
| Flashscore scraper plans | Free $0 1,000 req/month; Starter $16 20,000; Grow $48 100,000; Scale $148 400,000 |
| odds-api.net plans | Starter $30 (50K req, 60/min, 2 books), Growth $50, Builder $90, Live $250, Pro $500 |

## 9. People, terms & names
- **People:** Tj (owner). Accounts: acct-5d1e2d (mirage), acct-7542bf (tj outlook), acct-27a37e (tj Gmail).
- **Terms:** lease = who may work (`tools/coord.py`); STOP ALL = `killed` master switch; TCL118 = the API-site review task.
- **Names in use:** branch `ccr-28648a70-j699u7`; `tools/sportmicro_probe.py`; `.github/workflows/sportmicro-probe.yml`; Release tag `sportmicro-probe`; `research/api_sites_review_2026-10-18.md`; `research/coordination/ONE_TEAM.md`; `research/coordination/ENGINE_ADMIN.md`; `research/review/CHANGES.md`; `data/.../cloud/NovigErrorWatch.kt`.

## 10. Work state
| Item | Status | Version / location | Notes |
|---|---|---|---|
| Account names, merge with main, ONE_TEAM index, branch audit | final | pushed | branches not deleted |
| TASKS.md triage | done as an index | `research/TASKS_TRIAGE_2026-10-18.md` | 249 open items, none closed |
| Nine-site review | final | `research/api_sites_review_2026-10-18.md` | Flashscore dropped |
| NovigErrorWatch fix | built, tested (4 tests) | ckpt 3659 | released in v0.117.3 |
| v0.117.3 release | build running | run 39157500000 | not yet confirmed, not yet recorded |
| Sportmicro probe | built; re-run after UA fix queued | Actions run (check list_workflow_runs, per_page 1) | result unread |

## 11. Next steps
1. **Next action:** confirm Release v0.117.3 with `mcp__github__get_release_by_tag`, run `bash tools/record-release.sh v0.117.3 336 "v0.117.3: the engine's Novig outage alert and retry wait now count only Novig's own server errors (another API's 502, e.g. ParlayAPI, no longer shows 'Novig is failing' or slows the scan loop)"`, and give Tj the Release link as a bare URL.
2. Read the newest Sportmicro probe run's log (use job logs with tail_lines about 40; avoid large outputs) and tell Tj whether the free key opens the socket and which REST paths answer.
3. If the socket works: compare frame times with ESPN's score changes (`espn.jsonl`); decide if it feeds the live lane (TCL118b).

## 12. Open questions ⚠️
- Does the Sportmicro free key allow the WebSocket? Unknown until the rerun is read.
- Release v0.117.3 in the app: Tj has not yet been told it is out.

## 13. Re-attach checklist
Nothing to attach (everything is in the repo).

---
<sub>Audit: 13/13 sections · 7 rules · 1 correction · 14 data points · secrets removed: none found · generated by active-memory</sub>
