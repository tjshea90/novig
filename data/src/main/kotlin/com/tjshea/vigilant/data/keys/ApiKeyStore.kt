package com.tjshea.vigilant.data.keys

/** The two providers Tj wires keys for (RESEARCH.md §4.2/§4.3) — SharpAPI for the Novig leg, The Odds API for the reference leg. */
enum class ApiProvider(val storageKey: String, val displayName: String) {
    SHARP_API("sharp_api", "SharpAPI"),
    THE_ODDS_API("the_odds_api", "The Odds API"),
}

/**
 * Persists the actual API keys Tj types into the app (his real credentials — see
 * [com.tjshea.vigilant.data.keys.KeyRotator] for what consumes the list this returns). Order is
 * preserved and matters: it's the order [KeyRotator] tries keys in. The real, Android-side
 * implementation lives in the `app` module (encrypted on-device storage) — this interface stays
 * here, alongside `KeyRotator`, so it has no Android dependency.
 */
interface ApiKeyStore {
    suspend fun getKeys(provider: ApiProvider): List<String>
    suspend fun setKeys(provider: ApiProvider, keys: List<String>)
}
