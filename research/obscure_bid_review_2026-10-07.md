# Adversarial review of the small-market bid fill (v0.71.3), 2026-10-07

An independent reviewer ran MakerPlan.plan / MakerQuote.decide in a scratch copy. Six defects were CONFIRMED and are fixed (each with a test and a mutant that fails without the fix: `ObscureFillTest`):

1. **Flapping on the per-game limit.** A popular bid held up by its game's limit anyway still counted as "needs room", so a small-market bid came down, went back up, came down. Now only a popular bid that WOULD go up if every small-market bid we placed came down makes room (`MakerPlan.plan`, `without(...)`).
2. **Priority inversion on the per-game limit.** A small-market bid on a game used its limit and blocked a popular bid on the same game. Now it comes down for it.
3. **Unfittable popular bid.** A popular bid dearer than the wallet / dollar limit even with every small-market bid gone held back all small-market bids and took the resting ones down. Now small-market bids use the money a popular bid is too big for (the case the feature is for).
4. **Over-cancel.** The dollars needed ignored free headroom: both small-market bids came down when one was enough. Now the fewest, least valuable ones.
5. **Hand-approved small-market bids** were taken down to make room. Now only auto-make bids are.
6. **"Half stake" was not half under Kelly.** Kelly sized at the wider margin's lower price saw a bigger edge: 0.74x of a popular bid at 6%, 1.18x at the widest chip. Now sized at the popular bid's own price, then multiplied by the share.

Also done: recommendations (auto-make off) list popular bids before small-market ones (`MakerPlan.RECOMMEND_ORDER`); the Bids report counts small-market bids from the first one posted (not only after a fill); the two measured-gap skip reasons group as one line each in the Bids tab.

Left as is (reviewer's "possible", not confirmed or accepted): `MakerGuard` pools small-market fills with popular ones (a run of thin-market picked-off fills can halt every bid: watch it); `MakerLines.from` now works out book fairs for small-market lines that pass the cheap checks (more CPU per 20 s pass; a pass is still well under its budget on the tests, unmeasured on the phone); with the 4% margin chip a resting popular bid on a line that turns small-market keeps its price until it expires; the fill ships ON for existing Quick & likely users (as Tj asked).
