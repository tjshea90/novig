package com.tjshea.vigilant.data.rfq

/**
 * Which file configures the parlay maker (PF1, Tj 2026-11-20, verbatim: "Forget my preferences. Tweak the engine for maximum profit based on actual data. Change any of my preferences ... ignore whatever
 * settings are on my phone app"). Two files on `cloud-config`:
 *  - [ENGINE_FILE] (`rfq-maker-engine.json`): the profit profile, written ONLY by `tools/rfq_set.py` (a Claude session, from the graded evidence in `research/`); the phone never writes it.
 *  - [APP_FILE] (`rfq-maker.json`): what the phone's Parlays tab writes (MKV). The original place; kept so a repository without a profile behaves as before.
 * While the engine file exists it is the WHOLE configuration and the phone's file is not read: a tap on the phone changes nothing the engine does (the app says so, `RfqStatus.configLock`). A profile that
 * does not parse leaves the maker OFF (the safe default), never the phone's settings. Remove the engine file (`rfq_set.py --release`) to hand the maker back to the app.
 */
object RfqConfigSource {
    const val ENGINE_FILE = "rfq-maker-engine.json"
    const val APP_FILE = "rfq-maker.json"

    enum class Source { ENGINE, APP, NONE }

    data class Choice(val text: String?, val source: Source) {
        /** The words the app shows on the Parlays tab ("" = the phone's own settings are in charge). */
        val lock: String
            get() = if (source == Source.ENGINE) "Claude's profit profile runs the parlay maker (chosen from graded results). Settings changed on this phone do not apply to it." else ""
    }

    fun choose(engineText: String?, appText: String?): Choice = when {
        !engineText.isNullOrBlank() -> Choice(engineText, Source.ENGINE)
        !appText.isNullOrBlank() -> Choice(appText, Source.APP)
        else -> Choice(null, Source.NONE)
    }
}
