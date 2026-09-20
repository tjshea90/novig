package com.tjshea.vigilant.data.novig

/** Read-only access to Novig's live board — everything a scanner needs, nothing trading needs. */
interface NovigRepository {
    suspend fun getOpenMarkets(limit: Int = 50): List<NovigEvent>
}
