package com.tjshea.vigilant.data.keys

/**
 * Providers whose credentials Tj types into Settings. The Novig proxy list from v0.3.x is gone:
 * Novig's official public routes need no key and no proxy (NOVIG_API.md §5).
 */
enum class ApiProvider(val storageKey: String, val displayName: String) {
    THE_ODDS_API("the_odds_api", "The Odds API"),

    /** pinnapi.com's Pinnacle feed. The free trial key allows 100 requests a day. */
    PINNAPI("pinnapi", "Pinnacle (pinnapi)"),
}

/**
 * Tj's own API keys, in the order [KeyRotator] should try them. The Android implementation
 * (`app` module) encrypts every entry with an Android Keystore key before it touches disk.
 */
interface ApiKeyStore {
    suspend fun getKeys(provider: ApiProvider): List<String>
    suspend fun setKeys(provider: ApiProvider, keys: List<String>)
}
