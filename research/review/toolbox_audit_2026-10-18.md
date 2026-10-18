# Toolbox audit for TCL122 (v1.0), 2026-10-18, acct-7542bf (tj outlook)

Used in this job (already available, nothing for Tj to turn on):
- Repo skills: compose-state-and-effects, compose-performance, kotlin-concurrency-and-flow,
  compose-ui-testing-patterns, test-protocols (full tests at the end).
- Built-in: dataviz (chart rules for the P/L and live graphs: one axis, labelled zero line, direct labels),
  simplify + code-review (adversarial pass over the diff before the release).
- GitHub MCP tools: Actions triggers/logs, releases (CI, release.yml, engine runs).
- Monitor + tools/chat_watch.sh for the app chat.

Available but NOT enabled (optional; Tj enables at claude.ai > Plugins if wanted):
- KotlinSense / kotlin-lsp: Kotlin language-server diagnostics. Would need kotlin-language-server in the
  container; `bash tools/test.sh` (Gradle compile + tests) already gives the same errors, so not needed.
- Android Emulator QA: needs an emulator (no KVM here); the repo's Robolectric UI tests cover it.

Skipped on purpose: hawkscan (web DAST; this is an Android app + JVM engine, no web server to scan),
the many unrelated MCP servers (Canva, Figma, Datadog...) that need sign-in and add nothing here.
