# TASKS — the current job

## Tj's request, 2026-09-20 (his own words — full text in INBOX.md)

> All work will be done in the novig repo. Do not touch or make any changes
> to the other repos. They are to be read only. I'm starting a new project
> called novig. It will be an android 16 app optimized for a moto g 2026.
> The purpose of the app is to profit using the novig sports book. I will
> have Claude make the code, and trigger GitHub actions to sign and make the
> apk, then Claude send me a link to the finished APK.
>
> To begin, all work on this project needs to have a strong checkpoint
> system in place to save all progress and work without losing any data
> even if Claude usage is interrupted in the middle of a task.
>
> For this, review the linked repos called fantasy-football and portfolio.
> From those two repos, copy and adapt the checkpoint system for this
> project. Look for all the parts of the repos that have checkpoint and data
> save systems and figure out how they work then implement it in the novig
> repo. Make sure everything is adapted for novig, because a lot of things
> you read and copy only apply to the other projects. The checkpoint system
> you make should be based on the other repos but not exact copies because
> you may need to take out or alter wording or logic specific to those
> repos that don't apply here.
>
> Also copy and adapt the "run light test" and "run full test" systems in
> the portfolio repo

### Progress on this request

- [x] Read fantasy-football's and Portfolio's checkpoint mechanics in full:
      `resume.sh`, `ckpt.sh`, `autosave.sh`, `capture_inbox.sh` (ff only),
      `push.sh`, `unpushed.sh`, `toobig.sh`, `secretscan.sh`,
      `install-hooks.sh` + `session-root-hooks.json` + `tools/hooks/*`
      (Portfolio only), `test_resume.sh` (Portfolio only), `bootstrap.sh`,
      `ship.sh`, `record-release.sh`, and both `CLAUDE.md`/`BRIEF.md` files.
- [x] Design the adapted system for novig: Portfolio's session-root hook
      install (confirmed the only mechanism that actually fires hooks in
      this multi-repo container) as the base, PLUS fantasy-football's
      `INBOX.md`/`capture_inbox.sh` raw-message backstop layered in as a
      fifth hook event (`UserPromptSubmit`) that Portfolio's own template
      doesn't define. Deliberately dropped Portfolio's multi-repo
      AGGREGATION piece (each hook globbing every repo under the session
      root and running ITS `autosave.sh`, i.e. committing+pushing in it) —
      that would mean this repo's infrastructure executing `git commit`/
      `git push` inside fantasy-football's and Portfolio's real checkouts,
      which are read-only for this project's work. novig's installed hooks
      are absolute paths into this repo's own `tools/` only; verified this
      empirically too (see CHECKPOINT.md and `tools/test_resume.sh` section 4).
- [x] Implement `tools/`: `secretscan.sh`, `push.sh`, `unpushed.sh`,
      `autosave.sh`, `capture_inbox.sh`, `ckpt.sh`, `resume.sh`, `toobig.sh`,
      `install-hooks.sh`, `session-root-hooks.json`, `record-release.sh`
      (signature adapted — takes versionCode explicitly since the toolchain
      isn't decided yet, see its own header).
- [x] Write `.claude/settings.json` (standalone fallback), `.gitignore`,
      `bootstrap.sh`, `BRIEF.md` (honest about what's decided vs. TBD —
      Android 16/API 36, Moto G 2026, Novig-sportsbook-profit purpose are
      real; toolchain, keystore, and the actual profit strategy are not
      invented), `CLAUDE.md` (the full working agreement), `ship.sh`
      (refuses honestly — there's no build system yet to gate).
- [x] Adapt Portfolio's `test_resume.sh` hermetic suite for novig: same
      JSON-emission / hook-merge / checkpoint-numbering / secretscan /
      unpushed-refuses-to-guess checks, stripped of everything Android/
      Gradle/keystore-specific (none of that exists here yet), plus new
      checks for the INBOX capture path Portfolio doesn't have.
- [x] Copy and adapt Portfolio's "light tests" / "full tests" protocols into
      `CLAUDE.md`, written generically (no tabs/subsystems exist yet to name)
      but structured so a future session extends them the same way
      Portfolio names its own tabs and subsystems, instead of rediscovering
      the pattern from scratch.
- [x] Run the adapted `tools/test_resume.sh` for real and fix whatever it
      finds, before claiming any of this works.
- [x] Checkpoint and push everything for real (`tools/ckpt.sh`), confirm the
      hooks are actually installed and firing in this session, and report
      back honestly about what was verified vs. what is still TBD (the
      actual app, its architecture, and the release pipeline all remain
      unbuilt on purpose — this request was scoped to the checkpoint/test
      system only).

## Not yet started (deliberately out of scope for the checkpoint-system request above)

Tj's first message also describes the eventual app itself — an Android 16
app, optimized for a Moto G 2026, that profits using the Novig sportsbook,
with GitHub Actions building and signing the APK. The checkpoint-system
request above did not include building it.

## Tj's request, 2026-09-20T04:36:03Z (his own words — full text in INBOX.md)

> Research methods this app can use to find positive ev for novig sports
> book odds constantly updating in real time to capture odds movements and
> new positive EV bets. Currently I'm using oddsjam, but I cannot afford the
> subscription. Look for a way to do something just like oddsjam, but
> either free or less than 30 dollars per month. My goal is to build an app
> that is equal to or better than oddsjam at finding positive EV bets on
> novig. Begin deep research across the internet on what is needed for this
> app and how to do it so we can begin building it. Maybe make a research
> findings file permanently on GitHub in this repo so all research is
> saved.

### Progress on this request

- [ ] Research what "positive EV" detection actually requires: a de-vigged
      fair-odds/true-probability model, a source of sharp/consensus lines to
      devig from, and a feed of Novig's own live odds to compare against.
- [ ] Research how OddsJam and similar positive-EV tools (OddsJam, Odds
      Report/BettingPros, Unabated, RebelBetting, the free
      r/sportsbook/Discord scanners, etc.) actually source their data and
      compute EV, as far as it's publicly documented — what they charge for
      and why, so we know what we're actually trying to replace.
- [ ] Find real, current options for a live odds-data feed usable at $0–$30/mo:
      free/cheap odds APIs (The Odds API, OddsJam's own API tier, Pinnacle's
      public-ish feed, sportsbook-scraping approaches and their ToS/legal
      risk), and whether Novig itself exposes any API/websocket feed for its
      own lines (it's a peer-to-peer exchange, which may change what's
      available vs. a traditional book).
- [ ] Research the actual math: devigging methods (multiplicative,
      power/Shin, etc.), how to pick a fair-value reference line, EV
      calculation, and how real-time line movement should be handled
      (polling vs. streaming/websocket, update frequency vs. cost/battery on
      a Moto G 2026).
- [ ] Write findings into a permanent, cited research file in this repo
      (not just chat) covering: data source options and their real costs/
      limits, the EV math needed, an honest assessment of what's feasible at
      or under $30/mo, and a recommended architecture to start building
      against.
- [ ] Checkpoint the research file. Do not start writing app code from this
      request alone — architecture/build decisions still need Tj's
      sign-off per BRIEF.md's TBD sections.
