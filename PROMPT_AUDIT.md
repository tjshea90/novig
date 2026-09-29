# Prompt audit of the novig repo (2026-09-29)

Run for Tj's "run the prompt audit" (TASKS X1), following the procedure `/claude-api prompt-audit` runs (the claude-api skill's `shared/prompt-audit.md`). **Nothing here has been applied.** The report says what's stale or contradictory and why; `PROMPT_AUDIT.patch` holds every proposed edit (`git apply PROMPT_AUDIT.patch`, checked against the commit this was written on), and each finding's own diff is below so any subset can be taken.

## Assumptions

- **Scope:** the text that reaches Claude from this repo: `CLAUDE.md` (loaded every session), `BRIEF.md` (the standing rules it points to), `bootstrap.sh`'s rules block and status lines and `tools/resume.sh`'s warnings (both printed into every session's briefing), and `tools/ckpt.sh`'s CHECKPOINT.md template. `tools/autosave.sh` and `tools/toobig.sh` only print messages to Tj. Not read: `.claude/settings.json` (hook definitions; the audit's rules skip settings files). Out of scope: RESEARCH.md and NOVIG_API.md (reference, read on demand), TASKS.md, CHECKPOINT.md and INBOX.md (working state). The repo had no skills, agents, commands or rules files when this ran; Chris Banes' four skills were added right after (TASKS X2) and are kept unchanged as third-party files, so they are outside this audit.
- **Target model:** Claude Opus 5.5, the model running this audit (instruction files for a coding agent are audited against the model reading them). Direction of every contradiction comes from `git blame` on the full history (1,988 commits).

## Summary

The instruction text has no shouting, no dated reasoning scaffolds and no invented prohibitions; what it has is drift. It was written on 2026-09-20, before any app code, and the app outgrew it:

1. **The test protocols' floor is the pre-app one (A1).** Light and full tests say to run only `tools/test_*.sh`; a full test that follows the text literally skips all 760 app tests, while the "Automated floor" bullet added later says Gradle.
2. **Every session starts by reading false rules (A8).** The briefing's rules block says "NO signing keystore exists yet" and "no architecture decision has been locked in", both untrue since the first release, and contradicted by BRIEF.md and CLAUDE.md.
3. **BRIEF.md describes ~100 lines of deleted code as current decisions (B9, B10, F1-F5)**: the GraphQL Novig client with proxies, `KeyRotator`, encrypted key storage, `EvScanner`. Several contradict newer bullets in the same file.

Separately, half of CLAUDE.md (the test protocols and the app map) is loaded every session but used only when Tj says "light/full tests"; A11 moves it into a project skill behind a pointer, halving CLAUDE.md.

**Counts:** Group 1 (dated prompt text): 5 findings, all in 1d (A3, A4, A9, B3, B4: time-conditional and migration-relative wording, one unenforced TODO); 1a-1c, 1e and 1f found nothing. Group 2 (brittle configuration): 16 findings (A3 counts in both groups) and most of the flags. Group 3 (tool descriptions): not applicable. Group 4 (request config): not applicable (no model calls, no subagents). 13 high and 8 medium findings with edits (A1-A11, B1-B10), 11 flags without edits.

## Findings with a proposed edit

### High confidence

| ID | Location | Evidence | Pattern | Why it's stale | Action |
| :- | :- | :- | :- | :- | :- |
| A1 | CLAUDE.md:382-385, 415-416 | Light tests step 1: "Run the same automated floor `tools/ckpt.sh` runs: whatever `tools/test_*.{js,sh,py}` exist, plus `npm test`…"; Full tests step 1: "Run the automated suite (step 1 of light tests) as the floor" | Group 2: instruction passages that contradict each other | The "Automated floor" bullet (L371-375, blamed 2026-09-25/27) says the floor is `./gradlew :engine:test :data:test :app:testDebugUnitTest`; step 1 (2026-09-20, before any app code) still means only `tools/test_resume.sh`. Followed literally, a full test's floor skips all 760 app tests. | rewrite |
| A2 | CLAUDE.md:395-401 | Light tests step 4: "the pre-installed Chromium is fair game for markup/CSS/layout checks" | Group 2: contradiction / stale method | Written 2026-09-20 for a possible web UI. The app is Compose; the newer floor bullet (L372-373, 2026-09-27) says `-Pscreenshots` is "the 'Chromium check' for a Compose app". | rewrite |
| A3 | CLAUDE.md:417-421 | Full tests step 2: "once there are tabs/screens/subsystems to name, list them here… Until then: read every source file that exists" | Group 1d / Group 2: time-sensitive content | The list it waits for exists in the same section (L260-375, since v0.4.0). The "until then" branch no longer applies. | rewrite |
| A5 | CLAUDE.md:465-475 | "follow Portfolio's `CLAUDE.md` \"Releasing\" section and `ship.sh`/`.github/workflows/android.yml` as the reference implementation once there is an actual Android project to build" | Group 2: contradiction / time-sensitive content | The next paragraph (L477, 2026-09-25) says the pipeline "is built and working (since v0.1.0)"; this repo's own `release.yml` is the reference now, not Portfolio's `android.yml`. | rewrite |
| A6 | CLAUDE.md:526-532 | "the rules (the eventual signing keystore, toolchain pins, build traps, locked architecture decisions) that will apply the moment there's something for them to govern" | Group 2: time-sensitive content | BRIEF.md: "the keystore was generated 2026-09-20"; every one of those rules governs now. | rewrite |
| A7 | CLAUDE.md:21-25 | "As of this writing (2026-09-25) the app is **Vigilant v0.4+**… Do not treat `BRIEF.md`'s remaining TBD sections as settled… they're marked TBD on purpose." | Group 2: volatile specifics | BUILDLOG.md's latest release is v0.19.6; BRIEF.md has no TBD section left (the word appears only in its own status paragraph). Also, `bootstrap.sh` prints BRIEF.md's short version, not BRIEF.md. | rewrite |
| A8 | bootstrap.sh:104-113 (printed into every session's briefing) | "No strategy, data source or architecture decision has been locked in yet" / "NO signing keystore exists yet. The day one is generated…" / "once real code exists" | Group 2: instruction files that contradict each other | Blamed 2026-09-20, before the app. BRIEF.md ("the keystore was generated 2026-09-20", "Locked architecture decisions") and CLAUDE.md L477 (2026-09-25) say the opposite. This is the one block every session reads at start-up. | rewrite |
| B1 | BRIEF.md:13-20 | "**Status as of 2026-09-20:** first real app code exists… (54 passing unit tests…)… a basic Compose UI running on sample data… sections still genuinely open stay marked TBD" | Group 2: volatile specifics | BUILDLOG.md: v0.19.6 (760 tests at ship); no sample-data path remains in main code; no TBD section remains. | rewrite |
| B2 | BRIEF.md:38-41 | "Not yet decided: automated bet placement…, position tracking, or anything beyond finding and surfacing the edge" | Group 2: volatile specifics | Position tracking was decided and built (TASKS N1-N5: `BetTracker`, `BetSettler`, Tracker tab). Automated placement is still undecided, so that guard stays. | rewrite |
| B5 | BRIEF.md:135-148 | "**Known, permanent constraint on this dev container: no Android SDK.** … not expected to change… the `app` module can only be verified by CI" | Group 2: passages that contradict each other (same file) | Build trap 6 (L270-291, 2026-09-25/27) installs the SDK and runs every module's tests here, and the cloud environment's setup script does it (TASKS S3). CLAUDE.md's floor depends on it. | rewrite |
| B8 | BRIEF.md:438-439 | "Chip order NFL, NCAAF, MLB, WNBA, NHL, then NBA, NCAAB, UFC, Boxing." | Group 2: volatile specifics | `data/scanner/Leagues.kt` `ALL`: NFL, NCAAF, MLB, WNBA, NHL, ATP, WTA, NBA, NCAAB, UFC, Boxing (tennis since v0.19.0). | rewrite |
| B9 | BRIEF.md:496-551 | "**`NovigGraphQlClient` supplies the Novig leg (wired 2026-09-22…)**" plus SharpAPI history, proxies, direct mode, `SampleNovigRepository` | Group 2: stale facts (named code no longer exists) | None of `NovigGraphQlClient`, `NovigApiClient`, `NovigDirectAccessException`, `SampleNovigRepository`, `SampleReferenceOddsRepository` exists; the same file's 2026-09-25 bullet (L330-331) says the GraphQL/proxy client was deleted. The history stays in RESEARCH.md §4 and git. | remove |
| B10 | BRIEF.md:552-568 | "**Automatic multi-key rotation: `KeyRotator` (`data/keys/KeyRotator.kt`)…**" | Group 2: stale facts | `KeyRotator` and `ApiProvider.NOVIG_PROXY` don't exist; rotation is `KeyPool`/`UsageMeter`, described in the 2026-09-25 bullet (L430-437). | remove |

### Medium confidence

| ID | Location | Evidence | Pattern | Why it's stale | Action |
| :- | :- | :- | :- | :- | :- |
| A4 | CLAUDE.md:426-428 | "duplicate requests against the Novig API (or whatever data source(s) this ends up using)" | Group 1d: migration-relative phrasing | The sources are settled (Novig, CNO, the reference-odds providers); the hedge describes a future that already happened. | rewrite |
| A9 | bootstrap.sh:11-16, 28-36 | "a build system now exists — bootstrap.sh has not been updated to check it yet (toolchain pins, keystore, SDK). Do that next time real build rules land"; header: "NO APP CODE EXISTS YET" | Group 1d: unenforced instruction (enforce in code what can be) | A standing TODO printed every session since the app landed. The fix is the check it asks for: SDK present (build trap 6) and the committed keystore present. | rewrite (adds two status checks) |
| A10 | tools/resume.sh:178 | "the session stopped without finishing a step — almost certainly a usage cap." | Group 2: recency trap / accuracy | The warning also fires after a restart mid-turn: it fired in this session after the container restarted (2026-09-28 ~22:40Z), with no usage cap involved. "Almost certainly" overstates it; the instruction (read the diff, finish the change) is unchanged. | rewrite |
| A11 | CLAUDE.md:247-454 ("Test protocols", 17,630 of 35,855 characters) | The light/full test procedures and the 12,296-character map of the app's tabs and subsystems, loaded into every session | Group 2: verbose instruction file (every paragraph pays its token cost on every load) | Claude Code's skills guidance: when a section of CLAUDE.md has grown into a procedure, make it a skill, whose body loads only when used. These protocols run only when Tj says "light tests"/"full tests". A short pointer stays in CLAUDE.md so the trigger can't be missed. CLAUDE.md goes from 35,855 to 18,017 characters. Apply A1-A4 first; A11 moves their fixed text. | move (to `.claude/skills/test-protocols/SKILL.md`) |
| B3 | BRIEF.md:51-54 | "## The rule that will apply the moment a keystore exists" / "**Now true —** …in force starting now" | Group 1d: migration-relative phrasing | A heading written as a diff against the time before the keystore existed. | rewrite |
| B4 | BRIEF.md:130-131 | "eventually the foreground WebSocket service from RESEARCH.md §7" | Group 1d: time-sensitive content | `ScanService`/`AutoScanService` exist in `app`; the websocket itself is `data/novig/stream/NovigStream`. | rewrite |
| B6 | BRIEF.md:152, 183, 207, 232 | "Three hit and fixed 2026-09-20" then "A fourth one", "A sixth", "A seventh" bullets, beside the heading "### Build trap 6" | Group 2: ambiguous reference | Two different "sixth" traps (the `weight` import bullet and the setup-android heading). CLAUDE.md, `tools/setup-android.sh` and TASKS.md cite "build trap 6" meaning the heading. Dropping the ordinals from the bullets leaves one trap 6. | rewrite |
| B7 | BRIEF.md:302-306 | "**What Tj can do:** paste `tools/setup-android.sh`'s contents into the cloud environment's **Setup script**…" | Group 2: volatile specifics | Done: the environment's setup script ran 2026-09-28 19:12Z and sessions start with `/opt/android-sdk` and the Gradle mirror (TASKS S3, ticked). | rewrite |

## Flags: for Tj to decide (no edit proposed)

Kept out of the diff on purpose: each touches a rule, a prohibition or a security choice, where the audit's rules say a person decides even when a newer passage already superseded the old one.

| ID | Location | Evidence | Why flagged, and a suggested resolution |
| :- | :- | :- | :- |
| F1 | BRIEF.md:466-471 | "Reference-line source order: prefer a sharp book… alone… Implemented in `engine`'s `Consensus` object — do not quietly change this… without checking with Tj" | Superseded by Tj's own 2026-09-25 decision in the same file (L332-338: per-book devig, then SHARP / MARKET_AVERAGE / BLEND, default BLEND 70% sharp; code `ScanSettings.fairSource = BLEND`), and `Consensus` no longer exists. Left as a flag because the old passage is a "don't change without asking" rule. Suggested: replace with one line pointing at the BLEND bullet. |
| F2 | BRIEF.md:569-586 | "API keys are stored encrypted on-device, never in plaintext, never committed." | Contradicted by the 2026-09-25 bullet (L430-437, Tj: "don't worry about security, they are free keys"): keys live in plain `api_keys.json`. `EncryptedApiKeyStore`/`KeyCipher` still exist only to move old keys over once; `SettingsViewModel` is gone. A safety rule, so flagged. Suggested: "The old encrypted store is read once to move keys into `api_keys.json`; never commit a key." |
| F3 | BRIEF.md:478-489 | "`engine.Fees`/`FeeResult` models this explicitly — `FeeResult.Unknown`… every `TradeContext` has a confirmed formula" | `FeeResult`, `TradeContext` and `EvScannerTest` are gone. The rule still holds in the new code: fees are `MarketFee` from each market's `fee` object (L391-393), and `Pricing.kt:199` skips any market whose fee can't be read, so an unknown fee never becomes $0. A safety rule, so flagged. Suggested: keep the rule, name `MarketFee`/`Fees.takerFee` and the `Pricing` skip. |
| F4 | BRIEF.md:490-495 | "The UI must never present sample/demo data as if it were live. `ScannerViewModel`/`OpportunitiesScreen` carry explicit `novigIsLive`/`referenceIsLive` flags" | No `Sample*` class remains in main code and those flags are gone. A prohibition, so flagged. Suggested: "Vigilant has no sample-data path; any future one must say so on screen." |
| F5 | BRIEF.md:587-609 | "Nothing loads until the user acts" (`ScannerViewModel`, `ScanUiState.Idle`, `toggleSport`, `rescan()`, `EvScanner.scan()`) | Every named class is gone; the rule lives on, newer and complete, in "Manual scans only" (L394-406, 2026-09-28). A prohibition, so flagged. Suggested: remove as covered. |
| F6 | BRIEF.md:388-390 and 394-406 | "Rechecks are the one network action besides a scan… Still nothing on a timer." / "CrazyNinjaOdds' list is one asked-for exception… Background auto-scan… is the other" | Timers exist that the rules don't list: the widget's rescan (opt-in `widgetRescanMinutes`, `WidgetRescan.kt`, Tj 2026-09-27) reads odds on a timer while CNO's list is up; `SettleWorker` reads scores every 3 h. Rewording would loosen a prohibition's text, so it's Tj's call. |
| F7 | BRIEF.md:84-88 | "Revisit this the day the app holds real Novig API credentials or anything else worth protecting — switch back to a Secret-held keystore" | That day has come (Novig `trading::read` key since v0.4.0; the websocket since v0.19.0). A security decision, not a wording fix: a keystore change forces an uninstall, which erases the app's saved data (same section). Tj's call. |
| F8 | CLAUDE.md:9-17, 66-77, 136-146, 176-186, 486-490, 492-497; BRIEF.md:183-206, 251-268 | Incident stories told in full (fantasy-football's four forked sessions, Portfolio's 565 stranded commits, the 2026-09-15 inbox gap, the default-branch and cancelled-release traps) | Group 2 "history narratives": a rule's authority is what it prescribes. They also carry the reasons behind the rules (keep-list: context is never cruft), so low confidence and no diff; condensing each to one clause would save about 4,000 characters. |
| F9 | CLAUDE.md:432-437 | "checked against whatever this project ends up designating as its ground truth… that ground-truth document does not exist yet" | BRIEF.md's "Locked architecture decisions" (fair odds, taker price, fees, freshness) work as that ground truth today; designating it is Tj's call. Low. |
| F10 | BRIEF.md:172-182 | "This bit `ScannerViewModel`" | The class is gone; the rule (a reflection-built class needs a real no-argument constructor) still holds. Low. |
| F11 | BRIEF.md:29-34 | "Nothing about that device's specific chipset, RAM, or display has been researched yet as of this writing" | May still be true; a research gap rather than a wording problem. Low. |

## Noticed outside the prompt surface

- `engine/Fees.kt`: `MarketFee.GAME`'s comment says "used only by tests and sample data", but `CnoBooks.kt:217` and `BetTracker.kt:145` use it for real bets. A live futures bet would be charged the game schedule (0.03, only when live) instead of futures (0.06, always). Worth a look in the next full test.
- `tools/test_resume.sh` header still says the Android/Gradle/keystore parts "don't exist in this project yet" (a code comment, not read at session start).
- `bootstrap.sh` labels `git rev-list --count HEAD` as "checkpoints" (it counts every commit), and its `[ -d .git ]` test misreports inside a git worktree, where `.git` is a file.
- `tools/toobig.sh` said sessions resume "in well under a hundred lines"; after the briefing fix it's about 120. Fixed directly in this change (it completes the earlier briefing fix, not an audit hunk).

## Proposed diff, one finding at a time

Order matters in three places: A3 sits next to A2, A11 moves the text A1-A4 fix, and B10 follows B9. Everything else applies on its own. Checked on a clean checkout with the whole patch applied: `bash -n` on the scripts, `tools/test_resume.sh` all green, and the session briefing at 6,352 characters (limit 10,000).

<details><summary>A1 diff</summary>

```diff
diff --git a/CLAUDE.md b/CLAUDE.md
--- a/CLAUDE.md
+++ b/CLAUDE.md
@@ -379,10 +379,9 @@
 Purpose: catch obvious bugs/UI issues and anything the CURRENT session's own
 changes broke elsewhere in the app. Not a general audit.
 
-1. Run the same automated floor `tools/ckpt.sh` runs: whatever
-   `tools/test_*.{js,sh,py}` exist, plus `npm test` if `package.json`
-   declares one. If none exist yet, say so plainly rather than reporting a
-   false "all green".
+1. Run `tools/test_*.sh` (what `tools/ckpt.sh` runs) and the Gradle tests of
+   every module this session changed (see "Automated floor" above). Say what
+   ran; never report "all green" for tests that didn't run.
 2. Re-read only the files this session actually touched, plus (via `grep`)
    whatever else calls into them, looking for obvious bugs and issues: stale
    copy, missing guards, a render or response that no longer matches
@@ -412,8 +411,8 @@
 
 Purpose: a comprehensive pass over the ENTIRE app, not just recent changes.
 
-1. Run the automated suite (step 1 of light tests) as the floor, not the
-   ceiling.
+1. Run the whole automated floor above (all three modules, `-Pscreenshots`,
+   every PNG looked at) as the floor, not the ceiling.
 2. Sweep the whole app — once there are tabs/screens/subsystems to name,
    list them here and go through each in turn, the way Portfolio's
    `CLAUDE.md` does. Until then: read every source file that exists,
```

</details>

<details><summary>A2 diff</summary>

```diff
diff --git a/CLAUDE.md b/CLAUDE.md
--- a/CLAUDE.md
+++ b/CLAUDE.md
@@ -391,13 +391,11 @@
    bugs on this account's other projects (a shared helper's new behavior
    silently feeding a different consumer, a duplicated normalizer drifting
    from the one it was copied from).
-4. If the change is visually checkable once there is a UI, the pre-installed
-   Chromium is fair game for markup/CSS/layout checks — this account's other
-   projects have used exactly that to catch UI bugs live rather than only in
-   code. Anything that needs the actual Android runtime, a native bridge, or
-   real network calls does not work from a bare browser; say so rather than
-   implying a real device check happened. There is no real device or
-   emulator in this environment.
+4. If the change is visible, render it: `-Pscreenshots` writes every screen
+   to `app/screenshots/`; look at the PNGs the change affects. There is no
+   device or emulator here, so say what a screenshot can't show (real
+   network, the overlay over Novig, notifications) rather than implying a
+   device check happened.
 5. Fix anything found. If any fix was non-trivial, re-run step 1 (and step 4
    if it touched anything visual) before calling it done — confirm the fix
    didn't break something else.
```

</details>

<details><summary>A3 diff</summary>

```diff
diff --git a/CLAUDE.md b/CLAUDE.md
--- a/CLAUDE.md
+++ b/CLAUDE.md
@@ -411,10 +411,8 @@
 
 1. Run the whole automated floor above (all three modules, `-Pscreenshots`,
    every PNG looked at) as the floor, not the ceiling.
-2. Sweep the whole app — once there are tabs/screens/subsystems to name,
-   list them here and go through each in turn, the way Portfolio's
-   `CLAUDE.md` does. Until then: read every source file that exists,
-   cross-check anything one module assumes about another, verify load
+2. Sweep the whole app: go through each tab and subsystem listed above in
+   turn, cross-check anything one module assumes about another, verify load
    order, look for stale copy vs. actual behavior.
 3. Specifically look for, and fix:
    - **Code/UI improvements** — dead branches, inconsistent formatting,
```

</details>

<details><summary>A4 diff</summary>

```diff
diff --git a/CLAUDE.md b/CLAUDE.md
--- a/CLAUDE.md
+++ b/CLAUDE.md
@@ -418,9 +418,9 @@
    - **Code/UI improvements** — dead branches, inconsistent formatting,
      stale or misleading copy, accessibility gaps, missing dark/light
      handling.
-   - **Network efficiency** — duplicate requests against the Novig API (or
-     whatever data source(s) this ends up using) or redundant
-     re-computation of anything derived from a response already fetched.
+   - **Network efficiency** — duplicate requests against Novig, CNO or the
+     reference-odds providers, or redundant re-computation of anything
+     derived from a response already fetched.
    - **Caching and data retention** — nothing the app has fetched, computed,
      or the user has entered should be silently lost, overwritten, or
      mis-filed by a race.
```

</details>

<details><summary>A5 diff</summary>

```diff
diff --git a/CLAUDE.md b/CLAUDE.md
--- a/CLAUDE.md
+++ b/CLAUDE.md
@@ -457,17 +457,12 @@
 commit objects reachable by SHA even after history is rewritten. Keep real
 secrets in a local, gitignored `.env` — see `.gitignore`.
 
-## Releasing — the plan, ported from Portfolio
+## Releasing
 
 Tj's instruction for this project: Claude writes the code, GitHub Actions
 builds and signs the APK, Claude triggers that build and confirms it went
 green, and Tj gets a link — not the raw APK bytes relayed through chat, and
-not a build that happened inside this container. This is exactly Portfolio's
-model (as opposed to fantasy-football's, where a local `build.sh` still
-produces the APK and a separate workflow only republishes it) — follow
-Portfolio's `CLAUDE.md` "Releasing" section and `ship.sh`/`.github/workflows/android.yml`
-as the reference implementation once there is an actual Android project to
-build.
+not a build that happened inside this container.
 
 **This is built and working** (since v0.1.0): `app/build.gradle.kts` signs with
 the committed keystore, `.github/workflows/release.yml` builds, verifies the
```

</details>

<details><summary>A6 diff</summary>

```diff
diff --git a/CLAUDE.md b/CLAUDE.md
--- a/CLAUDE.md
+++ b/CLAUDE.md
@@ -515,8 +515,7 @@
 
 ## Project rules
 
-See `BRIEF.md` for the full list of what's actually decided about this
-project versus what's still open, and for the rules (the eventual signing
-keystore, toolchain pins, build traps, locked architecture decisions) that
-will apply the moment there's something for them to govern. `bootstrap.sh`
-prints the short version at every session start.
+See `BRIEF.md` for what's decided about this project and what's still open:
+the keystore rules, toolchain pins, build traps and locked architecture
+decisions, all in force now. `bootstrap.sh` prints the short version at
+every session start.
```

</details>

<details><summary>A7 diff</summary>

```diff
diff --git a/CLAUDE.md b/CLAUDE.md
--- a/CLAUDE.md
+++ b/CLAUDE.md
@@ -18,11 +18,9 @@
 
 **The project's own standing rules** (what's actually decided about the
 platform, target hardware, and purpose; what's still open) are written in
-full in `BRIEF.md` and printed at session start by `bootstrap.sh`. As of
-this writing (2026-09-25) the app is **Vigilant v0.4+**: Kotlin + Compose,
-modules `engine` / `data` / `app`, built and released by GitHub Actions. Do not
-treat `BRIEF.md`'s remaining TBD sections as settled just because they're
-written down; they're marked TBD on purpose.
+full in `BRIEF.md`; `bootstrap.sh` prints a short version at session start.
+The app is **Vigilant**: Kotlin + Compose, modules `engine` / `data` / `app`,
+built and released by GitHub Actions; `BUILDLOG.md` has the current version.
 
 ## Vigilant MGM is dormant — every request is for Vigilant (Novig) unless Tj names MGM
 
```

</details>

<details><summary>A8 diff</summary>

```diff
diff --git a/bootstrap.sh b/bootstrap.sh
--- a/bootstrap.sh
+++ b/bootstrap.sh
@@ -105,15 +105,12 @@
 echo "#  THE RULES THAT MUST NOT BE BROKEN  (full text: BRIEF.md)"
 echo "##############################################################################"
 cat <<'SHORT'
-- Android 16 (API 36) target, optimized for a Moto G 2026 — see BRIEF.md for
-  what that constrains once real code exists.
-- Purpose: profit using the Novig sportsbook. No strategy, data source or
-  architecture decision has been locked in yet — BRIEF.md says so plainly
-  rather than inventing rules nobody has actually decided.
-- NO signing keystore exists yet. The day one is generated, BRIEF.md's
-  "irreplaceable keystore" rule (ported from this account's other Android
-  projects) applies immediately and without exception: never regenerate it,
-  never change the applicationId, always bump versionCode.
+- Android 16 (API 36) target, optimized for a Moto G 2026.
+- Purpose: profit using the Novig sportsbook. What's decided (fair odds,
+  taker price, fees, when the app reads odds) is in BRIEF.md's "Locked
+  architecture decisions".
+- The signing keystore is committed and permanent (BRIEF.md): never
+  regenerate it, never change the applicationId, always bump versionCode.
 - Checkpoint constantly:  bash tools/ckpt.sh "did" "next"   (fast, no gate)
   Ship at milestones:     bash ship.sh "note"               (full gate)
 - Write new requests into TASKS.md, in Tj's own words, before writing any
```

</details>

<details><summary>A9 diff</summary>

```diff
diff --git a/bootstrap.sh b/bootstrap.sh
--- a/bootstrap.sh
+++ b/bootstrap.sh
@@ -8,12 +8,8 @@
 # and the standing rules. Narrative detail belongs in CHECKPOINT.md, TASKS.md,
 # CLAUDE.md and BRIEF.md, not in growing this file.
 #
-# NO APP CODE EXISTS YET. This project's first job was the checkpoint system
-# itself (tools/, this file, CLAUDE.md, BRIEF.md) — there is no build system,
-# no keystore, no Gradle/Kotlin (or other) project scaffold to check yet.
-# The checks below are deliberately generic until that scaffold exists; add
-# the real ones (toolchain, signing keystore, SDK, build output) the same
-# way Portfolio's bootstrap.sh does, the day there is something to check.
+# The build checks stay to what a session needs before its first build: the
+# Android SDK (BRIEF.md build trap 6) and the committed signing keystore.
 set -uo pipefail
 D="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; cd "$D" || exit 1
 echo "== novig — bootstrap =="
@@ -24,15 +20,19 @@
 command -v python3 >/dev/null 2>&1 && echo "  OK    python3 $(python3 -V 2>&1 | cut -d' ' -f2) (used by install-hooks.sh, ckpt.sh, capture_inbox.sh)" || echo "  WARN  no python3 — hook JSON emission falls back to plain text"
 command -v node >/dev/null 2>&1 && echo "  OK    node $(node -v 2>&1)" || echo "  note  no node yet — fine until a JS test suite exists"
 
-# --- no build system yet: say so plainly rather than checking for things
-#     that were never created, which would look like a failed check ---
-if [ -f app/build.gradle.kts ] || [ -f build.gradle.kts ] || [ -f package.json ]; then
-  echo "  note  a build system now exists — bootstrap.sh has not been updated"
-  echo "        to check it yet (toolchain pins, keystore, SDK). Do that next"
-  echo "        time real build rules land, the way Portfolio's bootstrap.sh does."
-else
-  echo "  note  no build system yet (no Gradle/Kotlin, no package.json). Expected"
-  echo "        at this stage — the app itself has not been started."
+# --- what the first build needs ---
+if [ -f app/build.gradle.kts ]; then
+  SDK="${ANDROID_HOME:-/opt/android-sdk}"
+  if [ -d "$SDK/platforms" ]; then
+    echo "  OK    Android SDK at $SDK (build with ANDROID_HOME=$SDK)"
+  else
+    echo "  WARN  no Android SDK at $SDK: bash tools/setup-android.sh (BRIEF.md build trap 6)"
+  fi
+  if [ -f app/keystore/vigilant-debug.jks ]; then
+    echo "  OK    signing keystore present (permanent: BRIEF.md)"
+  else
+    echo "  WARN  app/keystore/vigilant-debug.jks is missing: restore it from git, never regenerate it"
+  fi
 fi
 
 # --- is the safety net actually installed? ---
```

</details>

<details><summary>A10 diff</summary>

```diff
diff --git a/tools/resume.sh b/tools/resume.sh
--- a/tools/resume.sh
+++ b/tools/resume.sh
@@ -175,7 +175,7 @@
         echo "  !!    THE LAST SESSION WAS INTERRUPTED MID-CHANGE."
         echo "        $SINCE automatic checkpoint(s) were saved AFTER the last"
         echo "        deliberate one, which means the session stopped without"
-        echo "        finishing a step — almost certainly a usage cap."
+        echo "        finishing a step (a usage cap, or a restart mid-turn)."
         echo
         echo "        CHECKPOINT.md below describes the last DELIBERATE"
         echo "        checkpoint, NOT the current HEAD. The code in these files"
```

</details>

<details><summary>A11: 414 changed lines, in PROMPT_AUDIT.patch</summary>

Too long to repeat here; see `PROMPT_AUDIT.patch` or `git apply` it.

</details>

<details><summary>B1 diff</summary>

```diff
diff --git a/BRIEF.md b/BRIEF.md
--- a/BRIEF.md
+++ b/BRIEF.md
@@ -10,14 +10,10 @@
 the Play Store — the same distribution model as this account's other
 Android projects (fantasy-football, Portfolio).
 
-**Status as of 2026-09-20:** first real app code exists — a Gradle
-multi-module project (`engine`, `data`, `app`) with a working devig/EV
-engine (54 passing unit tests, verified for real in this dev container),
-data-layer scaffolding for both the Novig and reference-odds legs, and a
-basic Compose UI running on sample data. See `TASKS.md` for what's done vs.
-open, and RESEARCH.md for the research this was built from. Sections below
-that used to say TBD are now filled in with the decisions made building it
-— sections still genuinely open stay marked TBD rather than invented.
+**Status:** shipping. `BUILDLOG.md` has every release and the current
+version, `TASKS.md` what's done vs. open, RESEARCH.md the research behind
+each decision. No section below is TBD any more; what's still undecided
+says so where it applies.
 
 ## What is actually decided
 
```

</details>

<details><summary>B2 diff</summary>

```diff
diff --git a/BRIEF.md b/BRIEF.md
--- a/BRIEF.md
+++ b/BRIEF.md
@@ -32,9 +32,10 @@
   line (a sharp book like Pinnacle/Circa if fetched, else average whatever
   major books were fetched — Tj's own instruction, 2026-09-20) and compare
   it to Novig's live price, as close to real time as the data sources
-  allow. Not yet decided: automated bet placement (the engine computes EV,
-  nothing places a trade yet), position tracking, or anything beyond
-  finding and surfacing the edge — those remain open, ask before assuming.
+  allow. Tj places bets himself in Novig's app (Vigilant opens the bet
+  slip; nothing places a bet on its own), and every bet he marks placed is
+  tracked and settled (Tracker tab). Automated bet placement is not
+  decided: ask before assuming.
 - **Distribution:** sideloaded signed release APK, built and signed by
   GitHub Actions (not this container). Claude triggers the build via the
   GitHub API and confirms it went green; Tj gets a link, not a raw file.
```

</details>

<details><summary>B3 diff</summary>

```diff
diff --git a/BRIEF.md b/BRIEF.md
--- a/BRIEF.md
+++ b/BRIEF.md
@@ -45,10 +45,10 @@
   does *not* match Portfolio's secret-based one; it matches
   fantasy-football's committed-keystore one, Tj's explicit call).
 
-## The rule that will apply the moment a keystore exists
-
-**Now true — the keystore was generated 2026-09-20.** This rule is in
-force starting now, ported from this account's other two Android projects
+## The keystore rule
+
+**The keystore was generated 2026-09-20** and this rule is in force,
+ported from this account's other two Android projects
 where getting it wrong has cost real user data. Read this alongside the
 note right after it — the *security model* here is deliberately not
 Portfolio's, but the *permanence* rules below apply exactly the same way
```

</details>

<details><summary>B4 diff</summary>

```diff
diff --git a/BRIEF.md b/BRIEF.md
--- a/BRIEF.md
+++ b/BRIEF.md
@@ -124,8 +124,8 @@
 - **Module layout:** `engine` (plain Kotlin/JVM — the devig/EV math, zero
   Android dependency on purpose) → `data` (plain Kotlin/JVM — repositories,
   the Novig/The-Odds-API clients, also zero Android dependency) → `app`
-  (the actual Android module: Compose UI, manifest, eventually the
-  foreground WebSocket service from RESEARCH.md §7). Deliberate: keeping
+  (the actual Android module: Compose UI, manifest, the foreground scan
+  services). Deliberate: keeping
   `engine`/`data` Android-free is what lets their real logic be unit tested
   in a container with no Android SDK — see the next point.
 
```

</details>

<details><summary>B5 diff</summary>

```diff
diff --git a/BRIEF.md b/BRIEF.md
--- a/BRIEF.md
+++ b/BRIEF.md
@@ -129,20 +129,13 @@
   `engine`/`data` Android-free is what lets their real logic be unit tested
   in a container with no Android SDK — see the next point.
 
-**Known, permanent constraint on this dev container: no Android SDK.**
-`ANDROID_HOME`/`ANDROID_SDK_ROOT` are unset here and there's no `sdkmanager`
-— confirmed 2026-09-20, not expected to change. This is *why* the module
-split above exists: `engine` and `data` compile and run their real test
-suites here (`./gradlew --configure-on-demand :engine:test :data:test` —
-54 tests, all green as of this writing), but the `app` module can only be
-verified by CI (`.github/workflows/ci.yml`). This matches this project's own
-release model (`CLAUDE.md`'s "Releasing" section) — GitHub Actions builds
-the real thing, not this container — so treat it as the expected shape,
-not a gap to keep re-flagging. **Confirmed green end-to-end 2026-09-20**
-(run 35493330913: all tests across all three modules pass, `assembleDebug`
-succeeds, a real debug APK was produced). Any session working on
-`app`-module code should confirm it via a pushed CI run, not assume compile-correctness from
-review alone.
+**The dev container has no Android SDK until `tools/setup-android.sh` runs**
+(build trap 6; the cloud environment's setup script runs it, so sessions
+start with `/opt/android-sdk`). With it, all three modules build and test
+here (`./gradlew :engine:test :data:test :app:testDebugUnitTest`); without
+it, `engine` and `data` still do (plain Kotlin/JVM: the reason for the
+module split). GitHub Actions (`ci.yml`) stays the authority on green and
+builds every release.
 
 ## Build traps
 
```

</details>

<details><summary>B6 diff</summary>

```diff
diff --git a/BRIEF.md b/BRIEF.md
--- a/BRIEF.md
+++ b/BRIEF.md
@@ -139,7 +139,7 @@
 
 ## Build traps
 
-Three hit and fixed 2026-09-20, standing into the future — don't
+Hit and fixed since 2026-09-20, standing into the future — don't
 re-diagnose these from scratch:
 
 - **`android-actions/setup-android@v3` unconditionally fails.** It runs
@@ -170,7 +170,7 @@
   ViewModel (or anything else instantiated via reflection by an Android
   framework class) has a default-only constructor, it needs
   `@JvmOverloads`.
-- **A fourth one, found and fixed 2026-09-20, bigger than the others: this
+- **Found and fixed 2026-09-20, bigger than the others: this
   repo's actual GitHub default branch was NOT `main`** — it was
   `claude/novig-checkpoint-tests-qqgnnb`, an old session branch from when
   this repo was first created (whichever branch existed at repo-creation
@@ -194,7 +194,7 @@
   `default_branch` on the repo via the API before assuming a
   `workflow_dispatch` 404 is a workflow-syntax problem, which is what it
   looked like at first here.
-- **A sixth, found and fixed 2026-09-20: an explicit import of `weight`
+- **Found and fixed 2026-09-20: an explicit import of `weight`
   from `androidx.compose.foundation.layout` breaks `Modifier.weight()`
   instead of merely being redundant.** `Modifier.weight(1f)` inside a
   `Column { }`/`Row { }` body resolves via `ColumnScope.weight`/
@@ -219,7 +219,7 @@
   had already succeeded. `release.yml`'s verify step now strips colons and
   lowercases both sides before comparing — don't go back to a literal
   string match.
-- **A seventh, found and fixed 2026-09-22: cancelling an in-flight
+- **Found and fixed 2026-09-22: cancelling an in-flight
   `release.yml` run doesn't stop it instantly, and a step already running
   when the cancel signal arrives can still finish** — hit for real
   shipping v0.3.2's release: a run got cancelled while its "create the
```

</details>

<details><summary>B7 diff</summary>

```diff
diff --git a/BRIEF.md b/BRIEF.md
--- a/BRIEF.md
+++ b/BRIEF.md
@@ -289,11 +289,11 @@
 offenders (up to 30 days per Sonatype); repeated requests during a block extend it, so retrying
 makes it worse. Nothing one account does changes the pool's total; only the platform (Anthropic)
 can take it up with Sonatype ("infrastructure provider" path). The fix on our side is to not ask
-Central at all: Google's mirror above. **What Tj can do:** paste `tools/setup-android.sh`'s contents
-into the cloud environment's **Setup script** (session title bar › cloud environment menu › Edit), so
-every new session starts with the SDK and both mirrors in place before the first build; keep Network
-access allowing `maven-central.storage-download.googleapis.com` and `dl.google.com`; and optionally
-report it to Anthropic (github.com/anthropics/claude-code issues) so they raise it with Sonatype.
+Central at all: Google's mirror above. **Done on Tj's side (seen 2026-09-28):** the cloud environment's
+**Setup script** installs the SDK and both mirrors before Claude starts (`/opt/android-sdk` and
+`~/.gradle/init.d/mirror.gradle.kts` are there at session start); keep Network access allowing
+`maven-central.storage-download.googleapis.com` and `dl.google.com`. Optional: report it to Anthropic
+(github.com/anthropics/claude-code issues) so they raise it with Sonatype.
 Sources: central.sonatype.org/faq/429-error/, central.sonatype.org/faq/429-contact-support/,
 robolectric.org/configuring/.
 
```

</details>

<details><summary>B8 diff</summary>

```diff
diff --git a/BRIEF.md b/BRIEF.md
--- a/BRIEF.md
+++ b/BRIEF.md
@@ -425,8 +425,8 @@
   it can't afford a call, and rests a spent key until its provider's reset (1st of the month /
   midnight UTC), so rotation falls back to key 1 after each reset. Limits and ToS: RESEARCH.md §12
   (pinnapi's terms forbid circumventing its rate limits: warned in Settings).
-- **Leagues and markets (Tj, 2026-09-25 ~15:20Z).** Chip order NFL, NCAAF, MLB, WNBA, NHL, then
-  NBA, NCAAB, UFC, Boxing. Soccer, CFL, KBO and NPB are removed entirely (no 3-way markets remain).
+- **Leagues and markets (Tj, 2026-09-25 ~15:20Z).** Chip order NFL, NCAAF, MLB, WNBA, NHL, ATP, WTA
+  (tennis since v0.19.0), then NBA, NCAAB, UFC, Boxing. Soccer, CFL, KBO and NPB are removed entirely (no 3-way markets remain).
   Alternative markets priced where a fair source exists (RESEARCH.md §13, §15): 1st-half/F5 spreads
   and totals, MLB 1st-inning totals (NRFI/YRFI, Kalshi `KXMLBRFI`), team totals, and NFL/MLB/WNBA
   player props that Kalshi quotes on the same line (incl. pitcher outs, earned runs, walks, pass
```

</details>

<details><summary>B9: 56 changed lines, in PROMPT_AUDIT.patch</summary>

Too long to repeat here; see `PROMPT_AUDIT.patch` or `git apply` it.

</details>

<details><summary>B10: 17 changed lines, in PROMPT_AUDIT.patch</summary>

Too long to repeat here; see `PROMPT_AUDIT.patch` or `git apply` it.

</details>

## Afterwards

Once Tj has picked, apply the chosen hunks, re-run `tools/test_resume.sh`, and delete this file and `PROMPT_AUDIT.patch` (git keeps them). Re-run the audit when a new Claude model becomes the default, as the procedure advises.
