# Run the research (Tj: read this, it is 5 lines)

1. Install the newest Vigilant release (Releases page).
2. **Settings → Research mode** (a page of its own, under Data & help) **→ switch on.** It turns on every recorder that places nothing (live feed test, burst recorder, paper lab, paper bids). It never turns on real-money betting. Your real Bids and auto-bet settings are untouched.
3. Leave the app open and the phone plugged in through the evening games, and through a few days of pregame (the paper bids use the same lines your Bids tab uses, so keep Bids on).
4. After 2-3 evenings: **Settings → Research mode → "Share research file with Claude"**, send me that file. Also tap **Share diagnostics** (Settings → Diagnostics & about) and send that one.
5. Tell me what you noticed (a game that looked odd, a price that looked wrong). I do the rest.

Everything the app records is in `files/lab/` on the phone; the research file is that plus tables. Nothing here spends money.

No phone needed for one recorder: GitHub's Actions tab → "Research recorder" → Run workflow records Novig vs Kalshi for up to 5 hours on GitHub's servers and saves the tape to the `research-data` branch (it also runs by itself every evening at 7 PM Eastern).
Pinnodds is dormant: nothing here uses it.


## The GitHub lab (no phone) — what to do once
1. GitHub › this repo › Settings › Secrets and variables › Actions › **New repository secret**: name `SGO_API_KEY`, value = your SportsGameOdds key (several keys: separate with commas). Nothing else.
2. Actions › **Research lab (GitHub)** › Run workflow (it also runs by itself every 6 hours, around the clock). A run is 5.5 hours; without the secret it still runs on public data.
3. Send nothing: Claude reads the `lab-data` branch (`latest.txt` and `archive/`). Keep sending the phone's research file too; the two are the same format and are read together.
GitHub turns scheduled workflows off after 60 days without repository activity: if a run stops, open Actions and re-enable it.
