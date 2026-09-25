# novig — working agreement

Tj's Android app, targeting Android 16 (API 36) and optimized for a Moto G
2026, built to profit using the Novig sportsbook. Worked on across Claude
Code sessions and accounts — a new session may pick this up on a different
device, a different account, or simply after a previous session's usage ran
out mid-task. Everything below exists to make that handoff lossless.

The handoff mechanism (raw-inbox capture, autosave hook, deliberate
checkpoint, secret scan, milestone ship gate) is ported from and adapted for
this project from the same Claude-Code-native pattern already proven on
this account's other two repos, **fantasy-football** and **Portfolio** —
each of which learned pieces of it the hard way, on real incidents, before
this project ever existed. Where a rule below cites one of them, that
incident is real and already happened *there*; it is adopted here
preemptively, before it has a chance to happen on this project too, not
because it already has.

**The project's own standing rules** (what's actually decided about the
platform, target hardware, and purpose; what's still open) are written in
full in `BRIEF.md` and printed at session start by `bootstrap.sh`. As of
this writing there is no app code and no build system yet — the first job on
this project was standing up everything below. Do not treat `BRIEF.md`'s TBD
sections as settled just because they're written down; they're marked TBD on
purpose.

## FIRST ACTION OF EVERY SESSION — install the hooks, don't assume they exist

This account runs several repos side by side under one Claude Code
container (`novig`, `fantasy-football`, `Portfolio`). Confirmed on this
account's other repos: Claude Code on the web does **not** load hooks from a
repo's own `.claude/settings.json` when the repo sits in a subdirectory of
the session root — `CLAUDE_PROJECT_DIR` is unset and the harness resolves
one project root for the whole container. A repo's own settings file is
kept as a fallback (for the case this repo is ever opened standalone), but
it is not what protects a session running inside this container.

**So, before anything else this session, run this once:**
```bash
bash tools/install-hooks.sh
```
It finds the session root itself, MERGES the hooks into whatever settings
file is already there (preserving any other project's permissions, env, and
hooks), and says what it did. Idempotent — safe to run any time.

**Every hook this installs is an absolute path into THIS repo's own `tools/`
directory, never a glob over sibling directories.** Portfolio's own version
of this pattern has each hook scan every repo under the session root and run
that repo's `resume.sh`/`autosave.sh` in turn, aggregating them into one
combined briefing — a reasonable design there, since Portfolio owns all of
those repos equally. It is the wrong shape here: `fantasy-football` and
`Portfolio` are **read only** for this project's work, and `autosave.sh`'s
whole job is `git add -A && git commit && git push`. Running it inside
either of those repos' real checkouts — even as an unintended side effect of
infrastructure built for this repo — would be exactly the kind of change to
another repo this project must never make. So this repo's hooks touch
nothing but itself, full stop; see the header of `tools/install-hooks.sh`
for the reasoning in full.

**If you forget, two things catch it for you** — `tools/ckpt.sh` and
`tools/resume.sh` both repair the hooks before they do anything else. So a
session that never reads this section still gets the safety net back the
moment it checkpoints or resumes. That is a backstop, not a reason to skip
this step: until something calls one of them, nothing is being saved.

Verification is not instant — firing can lag a tool call or two behind the
edit rather than commit synchronously with it. Check over a few tool calls,
not just the next one:
```bash
# after 2-3 real edits, in a LATER tool call:
git log --oneline -3   # expect fresh "auto-checkpoint:" commit(s) in there
```
If several edits go by with none appearing, the hooks aren't firing at all —
fall back to running `bash tools/ckpt.sh "did" "next"` after every step BY
HAND for the rest of the session, and say so plainly; that becomes the only
safety net.

**Whether it is currently installed, without changing anything:**
```bash
bash tools/install-hooks.sh --check && echo installed || echo MISSING
```

## Starting a session

A `SessionStart` hook has already run `tools/resume.sh`, which pulled the
latest from GitHub and printed `CHECKPOINT.md`, `TASKS.md`, the tail of
`INBOX.md`, and the rules into your context. **Do not re-run bootstrap, do
not re-plan, do not re-read finished work.** Continue from **Do this next**
in `CHECKPOINT.md`, or the first unticked `[ ]` in `TASKS.md`.

**Check the `INBOX.md` tail against `TASKS.md` before assuming you know the
whole job.** `INBOX.md` is a raw, guaranteed-captured log of every message Tj
sends (see "When Tj asks for something new" below) — if it names something
`TASKS.md` does not yet cover, that is a request a previous session never
got around to writing down, not a stale duplicate.

**If you did not see that briefing, run `bash tools/resume.sh` before doing
anything else, and tell Tj it did not fire** — it means the hook is not
running, and the automatic saving below almost certainly is not either, so
this session is working without a safety net.

Read the two warnings it can raise:

- **"INTERRUPTED MID-CHANGE"** — the last session was killed by a usage cap
  part-way through a step. The tree is clean, but only because a hook
  committed a half-written change. `CHECKPOINT.md` describes the state
  *before* that, so it is stale. Run the `git diff` it names, finish that
  change, checkpoint it — then start anything new.
- **"UNCOMMITTED WORK IS PRESENT"** — the same thing, one step worse: not
  even the hook got to it. `git diff` is what was in flight.

## Branches — `main` is the only source of truth

Claude Code on the web puts each session on its own auto-generated branch
rather than reusing one — a platform decision this repo cannot bind from the
inside. On this account's other two repos, that exact behavior has already
caused real damage: on fantasy-football, four sessions in one day forked
from the same point on `main`, each thinking it was the sole continuation of
that project's working agreement, and shipped incompatible versions with
`main` never moving — untangling it cost most of a day. On Portfolio, 565
commits sat on a stranded feature branch while `main` still showed the
original README, for the length of a whole migration, before anyone
noticed. Treat that risk as equally real here from day one, not as something
to start worrying about only after it happens on this project too:

- **Before starting real work, check whether `main` has moved past your
  starting point** (`git log origin/main` vs your branch's merge-base). If
  it has, someone else's finished work is sitting there uninherited — pull
  it in before adding more on top, the same way you would if
  `CHECKPOINT.md` had described an interrupted session.
- **When you finish something worth keeping, get it onto `main`.** `push.sh`
  fast-forwards `origin/main` to match on every successful push,
  automatically, regardless of which branch is checked out — that's the
  actual fix, and it needs no action from you. `tools/resume.sh` reports
  `origin/main`'s sync status on every session start as a sanity check on
  that automation.
- **If you discover another branch with real, uninherited work on it** (not
  just an old abandoned experiment), tell Tj plainly before merging it in
  blind — a design decision on one branch may contradict one just made on
  another. Reconciling divergent work is a judgment call each time, not
  something to automate away.
- The most reliable way to avoid a new branch appearing at all: Tj resuming
  the *same* Claude Code session/conversation rather than starting a new one
  from claude.ai/code. That is a habit on his end, not something this file
  can enforce.

## When Tj asks for something new

**Write the request into `TASKS.md` in his own words, as unticked `[ ]`
boxes, and checkpoint it before writing any code.** Until it is written into
`TASKS.md` as real steps, nobody has actually planned the work — a message
sitting in a chat window is not a task list.

**You do not have to race a usage cap to get the raw request itself onto
disk.** A `UserPromptSubmit` hook (`tools/capture_inbox.sh`) already writes
every message Tj sends to `INBOX.md`, verbatim, and commits+pushes it the
instant it arrives — before you have read a single file. This closes a real
gap found on fantasy-football (2026-09-15): a session spent its whole budget
reading the codebase for a new feature, was cut off before ever writing the
request to `TASKS.md`, and the `PostToolUse` autosave hook (which only fires
on `Edit|Write|NotebookEdit|Bash`) never fired either, because a pure
research stretch trips none of those. Nothing reached disk, anywhere, and
the next session opened cold with no way to know the request had ever been
made.

This does not lower the bar on writing `TASKS.md` promptly — a raw inbox
entry is not a plan, and a long research stretch before turning it into one
is still worth avoiding. It means a forgotten or interrupted `TASKS.md` write
is a recoverable gap instead of a total loss: the exact words are always on
`INBOX.md`, and `resume.sh` prints its tail every session specifically so
this is never missed.

Tick a `TASKS.md` box only when it is written, tested and committed, and
name the test that proves it. The next session will not re-verify a ticked
box.

## Saving work — three levels, plus a backstop underneath the first one

**0. The raw message itself (hooks — happens without you, before you do
anything).** `tools/capture_inbox.sh` (a `UserPromptSubmit` hook) appends
every message Tj sends to `INBOX.md`, verbatim, and commits+pushes it the
moment it arrives — before any tool call, before any judgment about whether
it is "worth" saving yet. This is not a substitute for level 1 below or for
writing `TASKS.md`; it exists only so the exact words are never lost even if
nothing else gets written down before a usage cap hits. You do not call it,
and you should not need it if you write `TASKS.md` promptly — treat it as
the net under the net.

**1. Automatic (hooks — happens without you).** `tools/autosave.sh` commits
and pushes after every file edit and every bash command, and again on Stop.
It has no gate and runs no tests: a broken half-edit that is committed is
recoverable, the same edit uncommitted dies with the session. This is what
survives a usage cap landing mid-change. You do not call it.

**2. Deliberate — `bash tools/ckpt.sh "what I just did" "what comes next"`.**
**Run this after every completed step, not at the end of the session.** The
autosave hook can preserve your *files* but it cannot know your *intent* —
"what comes next" is the one thing no diff can reconstruct and the one thing
the next session most needs. It runs whatever fast checks exist (discovered,
not hard-coded — see the comment in `tools/ckpt.sh`), records green or red
honestly without gating, rewrites `CHECKPOINT.md`, commits and pushes.
Skipping it is how a handoff loses a day even though every file was saved.

**3. Milestone — `bash ship.sh "note"`.** The full release gate. Right now,
before a build system exists, this mostly means "the same fast checks as
`ckpt.sh`, plus an honest statement that there is nothing to build yet" —
see `ship.sh`'s own header. Fill in the real gate (a full test suite,
version-code monotonicity, the GitHub Actions trigger) the day there is an
actual app to gate, following the shape already proven on Portfolio's
`ship.sh`.

## Before your usage runs out

You will usually get no warning, which is why level 2 is per-step rather
than per-session. If you *do* notice you are running low, spend the
remaining budget on `tools/ckpt.sh` with an honest, specific "what comes
next" — not on one more edit.

## Large sessions

A `PreCompact` hook (`tools/toobig.sh`) fires when the conversation has grown
enough to auto-compact. Compaction rewrites earlier messages rather than
shrinking what a warm cache already covers, so the cheaper move is usually:
checkpoint, then start a fresh session — `tools/resume.sh` rebuilds
everything a session needs from GitHub in well under a hundred lines.

## Test protocols — "light tests" and "full tests"

**Standing instruction, not a `TASKS.md` job — this section IS the
explanation, so no clarification is needed when Tj asks.** Whenever Tj says
"light tests"/"light test"/"light testing", or "full tests"/"full
test"/"full testing"/"comprehensive tests" (any close wording), run the
matching protocol below immediately, on any account, from any cold start,
with zero further explanation required. Neither protocol is a `TASKS.md`
step to tick off; both end with `tools/ckpt.sh` recording what was found and
fixed (a full test that finds nothing worth withholding ends with
`ship.sh` per "Releasing" below — a light test does not ship on its own
unless Tj asks).

**The app's real surface (v0.4.0+), so "sweep the whole app" is concrete:**

- **Tabs:** +EV feed (`FeedScreen` + `OpportunitySheet` detail), Games
  (`GamesScreen`: board + per-game line table), Tracker (`TrackerScreen`: P/L,
  ROI, CLV), Settings (`SettingsScreen`).
- **Subsystems:** fair-odds math (`engine`: `FairValue`, `Devig`, `Fees`,
  `EvMath`); Novig data (`data/novig`: `NovigPublicClient`, `NovigText`);
  reference odds (`data/reference/TheOddsApiClient`); matching and pricing
  (`data/match/TeamMatcher`, `data/scanner/Planner` + `Pricing`); refresh timing
  and credits (`data/scanner/Scanner`, `MainViewModel.runLiveLoop`); persistence
  (`data/store/JsonFileStore`, `data/tracker/BetTracker`, `EncryptedApiKeyStore`).
- **Automated floor:** `./gradlew :engine:test :data:test :app:testDebugUnitTest`
  (needs BRIEF.md build trap 6 locally). Add `-Pscreenshots` and look at every PNG
  in `app/screenshots/`: this is the "Chromium check" for a Compose app.
  `VIGILANT_LIVE=1 ... --tests '*LiveNovigSmokeTest'` re-verifies matching against
  Novig's real catalog.

### Light tests — low usage, run after the session's own work is done

Purpose: catch obvious bugs/UI issues and anything the CURRENT session's own
changes broke elsewhere in the app. Not a general audit.

1. Run the same automated floor `tools/ckpt.sh` runs: whatever
   `tools/test_*.{js,sh,py}` exist, plus `npm test` if `package.json`
   declares one. If none exist yet, say so plainly rather than reporting a
   false "all green".
2. Re-read only the files this session actually touched, plus (via `grep`)
   whatever else calls into them, looking for obvious bugs and issues: stale
   copy, missing guards, a render or response that no longer matches
   behavior.
3. Check whether anything ELSE in the app could have broken from this
   session's changes — the "who else calls this" check that has caught real
   bugs on this account's other projects (a shared helper's new behavior
   silently feeding a different consumer, a duplicated normalizer drifting
   from the one it was copied from).
4. If the change is visually checkable once there is a UI, the pre-installed
   Chromium is fair game for markup/CSS/layout checks — this account's other
   projects have used exactly that to catch UI bugs live rather than only in
   code. Anything that needs the actual Android runtime, a native bridge, or
   real network calls does not work from a bare browser; say so rather than
   implying a real device check happened. There is no real device or
   emulator in this environment.
5. Fix anything found. If any fix was non-trivial, re-run step 1 (and step 4
   if it touched anything visual) before calling it done — confirm the fix
   didn't break something else.
6. `tools/ckpt.sh "light test: <what was found/fixed>" "<what's next>"`.

Budget discipline: this is deliberately narrow. Don't re-read files the
session didn't touch, don't chase pre-existing issues unrelated to this
session's changes — note them for a future full test instead.

### Full tests — no usage/time ceiling, best effort

Purpose: a comprehensive pass over the ENTIRE app, not just recent changes.

1. Run the automated suite (step 1 of light tests) as the floor, not the
   ceiling.
2. Sweep the whole app — once there are tabs/screens/subsystems to name,
   list them here and go through each in turn, the way Portfolio's
   `CLAUDE.md` does. Until then: read every source file that exists,
   cross-check anything one module assumes about another, verify load
   order, look for stale copy vs. actual behavior.
3. Specifically look for, and fix:
   - **Code/UI improvements** — dead branches, inconsistent formatting,
     stale or misleading copy, accessibility gaps, missing dark/light
     handling.
   - **Network efficiency** — duplicate requests against the Novig API (or
     whatever data source(s) this ends up using) or redundant
     re-computation of anything derived from a response already fetched.
   - **Caching and data retention** — nothing the app has fetched, computed,
     or the user has entered should be silently lost, overwritten, or
     mis-filed by a race.
   - **Engine/logic correctness** — anything related to the actual
     profit/edge-finding strategy checked against whatever this project ends
     up designating as its ground truth (the equivalent of
     fantasy-football's `RULES_2026.md` or Portfolio's scoring/accounting
     rules) — that ground-truth document does not exist yet; note in
     `BRIEF.md` once it does.
   - **Bugs or corruption from recent changes** — diff against the last few
     ships if that's the fastest way to spot what moved.
   - **Resource/battery waste** — anything polling, syncing, or holding a
     wake lock with no reason to while the app isn't in active use, given
     this is meant to run acceptably on a Moto G 2026; the app should sleep
     properly when backgrounded.
4. Fix everything found. Per this account's standing testing convention,
   give each fix a named test confirmed to FAIL against the pre-fix code
   where a test can prove it; where it can't (e.g. a pure UI render with no
   test harness), a source-text pin is the established fallback.
5. Full regression, verified by BOTH exit code AND output content, not a
   bare `grep FAIL` — Portfolio caught itself missing a silent crash that way
   once already (zero "FAIL" lines printed is not the same as green).
6. When clean: `tools/ckpt.sh` recording the full findings/fixes, then
   `ship.sh` and "Releasing" below to publish the Release and send Tj the
   link — a full test is exactly ship-worthy work, so don't leave it
   uncommitted-to-a-release unless Tj says not to ship yet.

## Credentials — never commit one

`tools/secretscan.sh` blocks the autosave hook from committing anything
shaped like a live credential (API keys, GitHub tokens, private keys,
`sk-ant-...` keys). If it trips, remove the credential — do not bypass it. A
key that reaches a commit has to be rotated, not deleted: GitHub keeps
commit objects reachable by SHA even after history is rewritten. Keep real
secrets in a local, gitignored `.env` — see `.gitignore`.

## Releasing — the plan, ported from Portfolio

Tj's instruction for this project: Claude writes the code, GitHub Actions
builds and signs the APK, Claude triggers that build and confirms it went
green, and Tj gets a link — not the raw APK bytes relayed through chat, and
not a build that happened inside this container. This is exactly Portfolio's
model (as opposed to fantasy-football's, where a local `build.sh` still
produces the APK and a separate workflow only republishes it) — follow
Portfolio's `CLAUDE.md` "Releasing" section and `ship.sh`/`.github/workflows/android.yml`
as the reference implementation once there is an actual Android project to
build.

**None of the mechanics below exist yet** — there is no `app/build.gradle.kts`,
no signing keystore, no `.github/workflows/*.yml` for this repo. Build them
in this order, the day real app code exists:

1. Stand up the Android project scaffold and decide the toolchain (see
   `BRIEF.md`'s open TBDs).
2. Generate the signing keystore, record its certificate fingerprint in
   `BRIEF.md` immediately (never regenerate it afterward — see `BRIEF.md`),
   and add it to GitHub Secrets (never commit it — see `.gitignore`).
3. Write `.github/workflows/android.yml` (or equivalent) modeled on
   Portfolio's: build, sign with the Secret-held keystore, verify the
   certificate on the artifact it just produced, create the release tag
   server-side (a Claude container gets HTTP 403 pushing `refs/tags/*` —
   confirmed on Portfolio, and there's no reason to expect this container's
   egress policy to differ), publish as a GitHub Release.
4. Fill in `ship.sh`'s real gate: a full test suite, a versionCode strictly
   higher than every one in `BUILDLOG.md` (this file already exists, empty,
   ready for the first entry), then trigger the workflow via
   `mcp__github__actions_run_trigger` and confirm green via
   `mcp__github__get_release_by_tag` before telling Tj anything.
5. Send Tj the Release page link — plain tappable text on its own line,
   **never inside a fenced code block**. A code block reads as copyable text
   in some clients but is not a clickable, long-press-copyable link the way
   a bare URL is on a phone; this exact mistake was made and corrected on
   fantasy-football (2026-09-14) — don't repeat it here.

**If the repo is private**, a Release link can 404 for Tj the same way it
did on Portfolio — that's GitHub returning 404 (not 403) to anyone viewing a
private repo without access, not a broken link. Check the repo's visibility
before assuming either way; the fix, if it happens, is confirming the
Release exists (`get_release_by_tag`) and telling Tj to check he's logged
into the right account in that browser, not regenerating the link.

## Building the APK

**Not possible yet.** `bash build.sh` does not exist because there is no
Android project scaffold to build. Once one exists, add a `build.sh` (for a
local debug build, mirroring fantasy-football's) or rely on `ship.sh`'s
`--local` fallback (mirroring Portfolio's) — pick whichever matches the
toolchain decision in `BRIEF.md`.

## This repo's visibility

Not yet confirmed as part of this work — check with the GitHub tools
available (repo visibility, e.g. via a repository-info call) before assuming
public or private, since it changes both the secret-scanning stakes (a
public repo makes a leaked credential immediately and permanently exposed —
this account's fantasy-football repo is public and treats every checkpoint
accordingly) and the Release-link behavior above.

## Novig API — permanent research memory

Novig's official API (v3, beta access since 2026-09-25) is documented for
this project in **`NOVIG_API.md`**. It covers hosts, public vs. signed routes,
the `NOVIG-V3` signing scheme, the websocket, book semantics ("taker price =
1 − best opposing bid"), fees, location checks, and what's still unverified.
Read it before writing or changing any Novig data code, and update it
whenever something is verified or turns out wrong. It is the one place a
fresh session learns this, so don't re-research what's already recorded there.
`RESEARCH.md` holds the wider EV/market research. This repo is **public**:
never commit a Novig key, PEM, or key ID paired with a private key.

## Project rules

See `BRIEF.md` for the full list of what's actually decided about this
project versus what's still open, and for the rules (the eventual signing
keystore, toolchain pins, build traps, locked architecture decisions) that
will apply the moment there's something for them to govern. `bootstrap.sh`
prints the short version at every session start.
