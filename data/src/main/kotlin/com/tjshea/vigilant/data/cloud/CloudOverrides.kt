package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Settings changes asked for from the GitHub side (the engine, or any Claude session), so the phone and GitHub show the same settings in BOTH directions (Tj, 2026-10-15: "All the settings should be
 * mirrored between the phone app and the GitHub app, including API toggles"). The phone has always written its settings to `cloud-config/settings.json` for the engine to read; this is the way back: one
 * small file per request under `overrides/` on the same branch. The phone applies each request ONCE (it remembers the ids), then publishes its settings as usual, so both sides agree, and anything Tj changes
 * on the phone afterwards wins until a newer request arrives. Only the API switches are accepted (the whitelist is [ApiToggles.IDS]); money limits, keys and the trading switches are never changed this way.
 */
object CloudOverrides {
    const val DIR = "overrides"
    const val BRANCH = "cloud-config"

    @Serializable
    data class Request(
        val id: String,
        val at: Long,
        /** "engine" or "claude:<account tag>". */
        val by: String,
        /** Why, in words: shown to Tj with the change. */
        val reason: String = "",
        /** API switch id -> wanted state. */
        val apis: Map<String, Boolean> = emptyMap(),
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(r: Request): String = json.encodeToString(Request.serializer(), r)
    fun decode(text: String): Request? = runCatching { json.decodeFromString(Request.serializer(), text) }.getOrNull()?.takeIf { it.id.isNotBlank() }
    fun fileName(r: Request): String = String.format(java.util.Locale.US, "%013d-%s.json", r.at, r.id.filter { it.isLetterOrDigit() || it == '-' }.take(24))

    /** [s] with the request's API switches set; unknown ids are ignored. */
    fun apply(s: ScanSettings, r: Request): ScanSettings = r.apis.entries.fold(s) { acc, (id, on) -> if (id in ApiToggles.IDS) ApiToggles.setOn(acc, id, on) else acc }

    /** What a request changed in [before], as words for the message and the log: "ParlayAPI off, PropLine on". */
    fun describe(before: ScanSettings, r: Request): String =
        r.apis.entries.filter { (id, on) -> id in ApiToggles.IDS && ApiToggles.isOn(before, id) != on }.joinToString(", ") { (id, on) -> "$id ${if (on) "on" else "off"}" }
}
