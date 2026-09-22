package com.tjshea.vigilant.data.keys

/**
 * The providers Tj wires credentials for (RESEARCH.md §4). [NOVIG_PROXY] entries aren't API keys
 * at all — they're rotating-proxy connection strings (`user:pass@host:port`) optionally used by
 * [com.tjshea.vigilant.data.novig.NovigGraphQlClient] (RESEARCH.md §4.4 — with zero configured,
 * that client falls back to a direct connection instead of refusing to run, per
 * [ApiKeyStore.isNovigDirectModeEnabled]); reusing [ApiKeyStore]/[KeyRotator] for them is
 * deliberate rather than building a second, parallel storage/rotation mechanism for what is
 * functionally the same problem (a list of credentials to try in order, rotating past ones that
 * fail).
 */
enum class ApiProvider(val storageKey: String, val displayName: String) {
    NOVIG_PROXY("novig_proxy", "Novig (direct)"),
    THE_ODDS_API("the_odds_api", "The Odds API"),
}

/**
 * Persists the actual API keys Tj types into the app (his real credentials — see
 * [com.tjshea.vigilant.data.keys.KeyRotator] for what consumes the list this returns). Order is
 * preserved and matters: it's the order [KeyRotator] tries keys in. The real, Android-side
 * implementation lives in the `app` module (encrypted on-device storage) — this interface stays
 * here, alongside `KeyRotator`, so it has no Android dependency.
 *
 * Also carries [isNovigDirectModeEnabled]/[setNovigDirectModeEnabled] — a single boolean, not a
 * credential, but tightly coupled to the same Novig-provider Settings state this interface already
 * owns (Tj's own request, 2026-09-22T05:38:31Z, after asking whether a free alternative to paid
 * proxies existed for [com.tjshea.vigilant.data.novig.NovigGraphQlClient]). Folded in here rather
 * than a whole second store/interface for one flag; not a secret, so it doesn't need [KeyCipher]'s
 * encryption the way the credential lists above do.
 */
interface ApiKeyStore {
    suspend fun getKeys(provider: ApiProvider): List<String>
    suspend fun setKeys(provider: ApiProvider, keys: List<String>)
    suspend fun isNovigDirectModeEnabled(): Boolean
    suspend fun setNovigDirectModeEnabled(enabled: Boolean)
}
