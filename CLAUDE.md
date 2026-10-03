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
full in `BRIEF.md`; `bootstrap.sh` prints a short version at session start.
The app is **Vigilant**: Kotlin + Compose, modules `engine` / `data` / `app`,
built and released by GitHub Actions; `BUILDLOG.md` has the current version.

## Vigilant MGM is dormant — every request is for Vigilant (Novig) unless Tj names MGM

**Standing instruction from Tj (2026-09-27, verbatim):** "from now on, everything in this repo and anything I
ask you to do will always be for the regular vigilant app for novig, unless I explicitly request something for
novig mgm. Novig mgm should be dormant and no changes made at all unless I ask for it. All future work and
versions and GitHub releases will be for regular vigilant for novig only unless I say otherwise."

What that means in practice:
- **Every task, version and Release is Vigilant's** (`app`, `com.tjshea.vigilant`). Never read a request as
  covering Vigilant MGM unless Tj names it.
- **`mgm/` is frozen at v0.17.0** (its last APK is on that Release). Don't edit anything under `mgm/`. The
  module isn't in the build at all unless you pass `-Pmgm` (`settings.gradle.kts`), so `./gradlew test`,
  CI, `ship.sh` and `release.yml` build, test and publish Vigilant alone.
- **The shared code stays shared.** `app`'s sources still carry `AppBook` switches and `MgmAppTest` still runs
  in `app`'s suite: they cost nothing and keep a revival cheap. Don't add MGM work to keep them current;
  a new Vigilant feature doesn't need an MGM variant.
- **To revive it** (only when Tj asks): build with `-Pmgm`, add `mgm` back to `release.yml`'s build and
  signature check, and re-read RESEARCH.md §25.

## Mobile data and storage are not a constraint

Tj, 2026-09-30: "My mobile data is fast and unlimited and my phone storage is large. Choose accuracy and speed over mobile data or phone
storage always." Never trade accuracy or speed for data or storage (no Wi-Fi-only reads, no smaller downloads, no thinner caches). API
credits and rate limits are still a real budget (BRIEF.md).

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
latest from GitHub and printed `CHECKPOINT.md`, `TASKS.md`'s open items (one
line each, with line numbers), the tail of `INBOX.md`, and the rules into
your context. The briefing has to stay under Claude Code's 10,000-character
hook cap, or Claude sees only a 2,000-character preview of it
(`tools/test_resume.sh` checks this). **Do not re-run bootstrap, do
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

## Skills for this app — load the matching one before the work, every task

`.claude/skills/` holds skills whose full text loads only when used. Load the matching one before
writing, changing or reviewing that kind of code, on every task (features and fixes too, not only
reviews):

| Before you touch… | Load |
| :- | :- |
| A Compose screen or component: state, `remember`, hoisting, effects, Flow collection in UI | `compose-state-and-effects` |
| Anything drawn per frame, per scroll or in a list (the feed, CNO list, widget, mini window), or a jank/recomposition question | `compose-performance` |
| Coroutines, scopes, services, `StateFlow`/`SharedFlow`/`Channel`, cancellation (scans, CNO reads, the websocket, auto-scan, settling) | `kotlin-concurrency-and-flow` |
| A Compose UI or screenshot test | `compose-ui-testing-patterns` |
| Tj says "light tests" or "full tests" | `test-protocols` |

Use the Skill tool. If a session's skill list doesn't show them yet (they sit under `novig/`, so
they appear once Claude works on files here), read `.claude/skills/<name>/SKILL.md` directly.
The first four are Chris Banes' (Apache-2.0, copied unchanged: `.claude/skills/THIRD_PARTY_NOTICES.md`);
where one disagrees with this file or BRIEF.md, this repo's rules win.

**Run tests with `bash tools/test.sh`** (the whole floor), or name tasks and filters:
`bash tools/test.sh :data:test --tests '*CnoChecksTest'`. It prints one line per module and only the
failing tests' messages (or the compiler errors), never Gradle's whole log (kept in
/tmp/vigilant-test.log), so a test run costs a few lines of context instead of hundreds.

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

**3. Milestone — `bash ship.sh "note"`.** The release gate: fast checks, the
full Gradle test suite (everything CI runs when a local SDK exists, per
BRIEF.md build trap 6), versionCode strictly above every BUILDLOG.md entry,
then push. Then trigger `release.yml`, confirm the Release, and run
`tools/record-release.sh` (see "Releasing").

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
everything a session needs from GitHub in about 120 lines (under 10,000 characters).

## Test protocols — "light tests" and "full tests"

Whenever Tj says "light tests" or "full tests" (any close wording: "light
testing", "full test", "comprehensive tests"), follow
`.claude/skills/test-protocols/SKILL.md` right away, on any account, from any
cold start, with no further explanation needed. It also holds the map of the
app's tabs and subsystems that a sweep goes through.

## Credentials — never commit one

`tools/secretscan.sh` blocks the autosave hook from committing anything
shaped like a live credential (API keys, GitHub tokens, private keys,
`sk-ant-...` keys). If it trips, remove the credential — do not bypass it. A
key that reaches a commit has to be rotated, not deleted: GitHub keeps
commit objects reachable by SHA even after history is rewritten. Keep real
secrets in a local, gitignored `.env` — see `.gitignore`.

## Releasing

Tj's instruction for this project: Claude writes the code, GitHub Actions
builds and signs the APK, Claude triggers that build and confirms it went
green, and Tj gets a link — not the raw APK bytes relayed through chat, and
not a build that happened inside this container.

**This is built and working** (since v0.1.0): `app/build.gradle.kts` signs with
the committed keystore, `.github/workflows/release.yml` builds, verifies the
certificate, tags server-side and publishes the Release, and `ship.sh` gates it.
The order per release:

1. `bash ship.sh "note"` after CI (`ci.yml`) is green on the commit.
2. Trigger `release.yml` on `main` (`mcp__github__actions_run_trigger`).
3. Confirm with `mcp__github__get_release_by_tag`.
4. `bash tools/record-release.sh vX.Y.Z <code> "note"`.
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

CI builds the real release (see "Releasing"). A local build is possible once
BRIEF.md build trap 6 is set up (`bash tools/setup-android.sh`, one command): `ANDROID_HOME=/opt/android-sdk ./gradlew
:app:assembleRelease` (R8-minified, ~4.6MB). Use it to check a change compiles
and to render screenshots, not to hand Tj an APK.

## This repo's visibility

**Public** (confirmed 2026-09-25: the GitHub API answers anonymously). A leaked
credential is exposed immediately and permanently, and Release links work for
anyone. Test fixtures that must contain key-shaped text carry the scanner's
`FAKE` marker on the same line (see `tools/secretscan.sh`); never commit a real
key.

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

## ParlayAPI — permanent research memory

ParlayAPI (parlay-api.com, Tj's $5 Starter plan since 2026-09-30) is documented for this project in **`PARLAY_API.md`**: how Vigilant
uses it, its real answer shapes (they differ from its docs), costs, credits, plan limits, and the build guide (§6) for the features
Tj asked for next (TASKS.md §M). Real answers, trimmed and keyless, are in `data/src/test/resources/parlay-*.json`. Read it before
writing or changing any ParlayAPI code, and update it whenever something is verified or turns out wrong. Never commit a ParlayAPI key.

## Scan study — permanent research memory

Every bet a CNO or Vigilant scan lists is logged, graded and closed in the background (v0.57.0, Tj 2026-10-03), and Settings › Diagnostics & about › **Share scan study with Claude** makes one file for
Claude to find what beats the close: the READ ME in `data/study/StudyExport.kt` says how, `RESEARCH.md` §75 says why it is built this way. Code: `data/.../study/` (`ScanStudy` observes and grades,
`StudyJournal` is the append-only store, `files/study/study-<day>.jsonl`), wired in `VigilantApp` (`study`, `settleStudy`) and `MainViewModel.shareScanStudy`. It reuses `AtBets`, `BetSettler`,
`CloseBackfill` and `ClosingLine`; its only request of its own is the wide read (v0.58.0, RESEARCH.md §76: CNO's list read a second time in a session of its own with the filters opened, every row logged and flagged hidden/shown; `CnoFeed.readWide`, paced, switch in Settings), which the app's list, alerts, auto-bet and widget never read. When Tj sends a scan-study file, work from its READ ME and its splits, and ask him before changing any rule it suggests.

## Project rules

See `BRIEF.md` for what's decided about this project and what's still open:
the keystore rules, toolchain pins, build traps and locked architecture
decisions, all in force now. `bootstrap.sh` prints the short version at
every session start.
