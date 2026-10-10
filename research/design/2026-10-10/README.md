# UI design candidates — 2026-10-10 (TASKS.md SU)

Tj: "Use the best android UI plugins to come up with different designs and ui for this app, send me screenshots of different
candidates but don't change anything yet". **Nothing in the app changed.** Each PNG is the +EV tab mocked from the SAME sample
board (`SampleScan`, priced by the real Planner/Pricing code, EV floor 0.5% so there are 7 bets), at the Moto G size the app's own
screenshots use (393x851 dp, xxhdpi = 1179x2553 px). `contact_sheet.png` has all six side by side.

| | Candidate | Idea |
| :- | :- | :- |
| A | Today's app | The real `FeedScreen` and the real 7-tab bar, for comparison. |
| B | Desk | Trading terminal: pure-black OLED, JetBrains Mono numbers in columns (EV, pick, Novig, fair, ¼ Kelly), one dense row per bet (all 7 fit with room to spare), the tapped row opens in place with depth, books and Open/Bet. Amber chrome. |
| C | Expressive | Android 16's Material 3 Expressive: tonal colors from one green seed, 34 sp display title, 28 dp cards with an EV "blob", pill stats, segmented time window, floating toolbar + Scan FAB. |
| D | Signal | Numbers drawn: EV heat on each card's edge (blue 0.5–2%, green 2–4%, gold 4%+), a price-vs-fair gauge (the lit stretch is the edge), a freshness ring, summary tiles (bets, best edge, Kelly total). |
| E | Daylight | Light fintech watchlist (Manrope): dark hero card with today's suggested total and an EV bar chart, white rows with price + EV pill. Readable outdoors. |
| F | Neon | OLED black-to-violet, glass cards with a cyan-magenta rim, gradient EV pills, Space Grotesk, floating glass bar. The loudest. |

B–F also propose a **5-tab bar** (+EV, CNO, Bids, Tracker, More: Games, Auto-bet and Settings under More) instead of today's 7
(each tab gets ~78 dp instead of ~47 dp). Any candidate can keep 7 tabs instead.

## Tools used / considered
- Rendered with the repo's own Roborazzi + Robolectric rig (NATIVE graphics), the `compose-ui-testing-patterns` skill.
- Harness: `app/src/test/kotlin/com/tjshea/vigilant/app/design/DesignCandidatesTest.kt` (test-only, skipped unless `-Pscreenshots`,
  so CI and the test floor never run it). Re-render:
  `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*DesignCandidatesTest' -Pscreenshots` → `app/screenshots/design/`.
- Fonts (OFL, not committed; a missing one falls back to the platform font): Inter from `/usr/share/fonts/opentype/inter/`, and from
  `https://cdn.jsdelivr.net/npm/@expo-google-fonts/<family>@0.4.1/<weight>/<File>.ttf`: jetbrains-mono, space-grotesk, manrope,
  copied into `app/build/design-fonts/`.
- Plugin catalog (2026-10-10, none enabled on the account): **Design** (Anthropic: design-critique, design-system,
  accessibility-review) is the best fit for the next step; Android Emulator QA and Oh My Android need an emulator / a Mac;
  Superdesign sends designs to its own canvas service; KotlinSense is a Kotlin language server.

## Next
Tj picks one (or mixes: e.g. B's rows + D's gauge + C's floating bar). Then: the bet sheet, CNO, Tracker and Settings in that
style, light + dark, then build it behind the existing screens' tests.
