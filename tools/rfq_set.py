#!/usr/bin/env python3
"""Set the Novig parlay maker's mode and caps (MK, Tj 2026-11-18: "I want to become a parlay maker and try to profit from it"; research/novig_rfq_maker_2026-11-18.md).

  python3 tools/rfq_set.py --show
  python3 tools/rfq_set.py mode=SHADOW --why "listen and journal, send nothing"
  python3 tools/rfq_set.py mode=LIVE margin=0.08 maxStake=12 maxCollateral=45 maxOpenCollateral=90 --why "evidence: ..."
  python3 tools/rfq_set.py mode=OFF --why "..."

Writes `rfq-maker.json` on `cloud-config` as ONE plain commit (the engine reads it with the settings within seconds; no file = OFF). Modes: OFF nothing; SHADOW registers the subaccount as a maker,
listens, journals and sends NOTHING; LIVE quotes and confirms within the caps (collateral is the maker's own money against the user's stake, taken from the Vigilant subaccount's balance).
LIVE needs the evidence rule in the research file (200+ priced executed RFQs, the market's median margin over our fair above the margin plus a cushion); log every change in research/review/CHANGES.md.
Never a key here; STOP ALL stays Tj's.
"""
import json, os, sys, tempfile, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from tell_tj import git, me, BRANCH  # noqa

def _bool(v):
    if v.lower() in ("true", "1", "yes", "on"): return True
    if v.lower() in ("false", "0", "no", "off"): return False
    raise ValueError("not a boolean: %r" % v)


FIELDS = {"allHighMaxLegs": int, "allHighLegFloor": float, "legCalibration": _bool, "onlyToday": _bool, "longshotsOnly": _bool, "longshotMaxChance": float, "longshotPremium": float, "walletCollateral": _bool, "walletShare": float, "walletLegShare": float, "dayLossStopPct": float, "maxLegFair": float, "liveMinReturn": float, "sameGamePremium": float, "propPremium": float, "allowProps": _bool, "probe": _bool, "marketPricing": _bool, "probeMinSamples": int, "propPairsFromMarket": _bool, "propPairs": _bool, "ladderExtrapolate": _bool, "ladderVotes": _bool, "propLegsShadowMax": int, "maxPropLegs": int, "liveParlays": str, "liveMaxFair": float, "manyLegsNeedLongshot": float, "liveMaxLegFair": float, "liveMaxAgeMs": int, "liveMaxSpread": float, "livePremium": float, "livePointsPerLeg": float, "liveMaxCollateral": float, "propPairsLive": _bool, "propPairsCashReserve": float, "propCashReserve": float, "reserveAfterCloseMs": int, "minBooksGameLines": int, "extrapolatedLegPremium": float, "fairErrorRel": float, "cashReserve": float, "maxLegCollateral": float, "mode": str, "margin": float, "maxStake": float, "maxCollateral": float, "maxOpenCollateral": float, "minBooks": int, "maxFairAgeMs": int,
          "maxLegs": int, "minReturn": float, "minPoints": float, "minLeadMs": int, "maxDaysAhead": int, "minFair": float, "maxFair": float, "dayLossStop": float}
LIMITS = {"longshotMaxChance": (0.03, 0.6), "longshotPremium": (0.0, 0.5), "walletShare": (0.05, 1.0), "walletLegShare": (0.05, 1.0), "dayLossStopPct": (0.0, 1.0), "maxLegFair": (0.5, 1.0), "liveMinReturn": (0.0, 0.5), "cashReserve": (0.0, 100.0), "maxLegCollateral": (0.0, 500.0), "fairErrorRel": (0.0, 0.2), "sameGamePremium": (-1.0, 0.5), "propPremium": (0.0, 1.0), "probeMinSamples": (10, 2000), "margin": (0.0, 0.5), "maxStake": (10.0, 100.0), "maxCollateral": (10.0, 250.0), "maxOpenCollateral": (10.0, 500.0), "maxLegs": (2, 100), "minReturn": (0.0, 0.5), "minPoints": (0.0, 0.1)}


def main(argv):
    why = argv[argv.index("--why") + 1] if "--why" in argv else "rfq_set"
    rest = [a for i, a in enumerate(argv) if a not in ("--why", "--show") and not (i and argv[i - 1] == "--why")]
    git("fetch", "-q", "origin", "+refs/heads/%s:refs/vigilant/rfq-set-%s" % (BRANCH, BRANCH))
    parent = git("rev-parse", "refs/vigilant/rfq-set-" + BRANCH).stdout.strip()
    old = git("show", parent + ":rfq-maker.json", check=False)
    cfg = json.loads(old.stdout) if old.returncode == 0 and old.stdout.strip() else {"mode": "OFF"}
    if "--show" in argv or not rest:
        print(json.dumps(cfg, indent=1)); return 0
    for pr in rest:
        k, v = pr.split("=", 1)
        if k not in FIELDS:
            raise SystemExit("unknown field %r (known: %s)" % (k, ", ".join(FIELDS)))
        val = FIELDS[k](v)
        if k in ("mode", "liveParlays") and val not in ("OFF", "SHADOW", "LIVE"):
            raise SystemExit("%s is OFF, SHADOW or LIVE" % k)
        if k in LIMITS and not (LIMITS[k][0] <= val <= LIMITS[k][1]):
            raise SystemExit("refused: %s must be between %s and %s" % (k, *LIMITS[k]))
        print(k, cfg.get(k), "->", val); cfg[k] = val
    text = json.dumps(cfg, separators=(",", ":"))
    with tempfile.TemporaryDirectory() as d:
        env = dict(os.environ, GIT_INDEX_FILE=os.path.join(d, "idx"))
        git("read-tree", parent, env=env)
        blob = git("hash-object", "-w", "--stdin", inp=text).stdout.strip()
        git("update-index", "--add", "--cacheinfo", "100644,%s,rfq-maker.json" % blob, env=env)
        tree = git("write-tree", env=env).stdout.strip()
        cenv = dict(os.environ, GIT_AUTHOR_NAME="Claude", GIT_AUTHOR_EMAIL="noreply@anthropic.com", GIT_COMMITTER_NAME="Claude", GIT_COMMITTER_EMAIL="noreply@anthropic.com")
        c = git("commit-tree", tree, "-m", "rfq_set (%s): %s" % (me().replace("claude:", "")[:30], why[:100]), "-p", parent, env=cenv).stdout.strip()
    r = git("push", "-q", "origin", "%s:refs/heads/%s" % (c, BRANCH), check=False)
    if r.returncode:
        print("push failed (cloud-config moved meanwhile; run again):", r.stderr.strip()); return 1
    print("pushed", c[:9], text); return 0


def selftest():
    assert FIELDS["mode"]("SHADOW") == "SHADOW" and FIELDS["margin"]("0.08") == 0.08 and FIELDS["maxLegs"]("3") == 3
    assert FIELDS["marketPricing"]("true") is True and FIELDS["probe"]("off") is False and FIELDS["sameGamePremium"]("0.02") == 0.02
    print("rfq_set selftest ok"); return 0


if __name__ == "__main__":
    sys.exit(selftest() if sys.argv[1:] == ["selftest"] else main(sys.argv[1:]))
