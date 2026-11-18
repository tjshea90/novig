package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.reference.PropLineClient
import com.tjshea.vigilant.data.match.TeamMatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * PropLine's `POST /v1/bets/settle` as the grader of last resort (Tj, 2026-11-18: "Build the grader into the app and github engine"; PLR5). It grades a game line (moneyline, spread, game or team total)
 * from the same box scores PropLine's own grader used, at ANY line (an alternate nobody posted, a quarter line), which the ESPN/MLB/SGO score chain cannot always do. Measured 2026-11-17: 5 of 5 known
 * singles and 12 of 12 two-leg parlays right (research/propline_unused_features_2026-11-17.md).
 *
 * It only ever runs for a bet [BetSettler] could not grade itself, and it is fail-closed: a leg PropLine marks `unmatched`/`pending`, a `redacted` answer (free key), a half win or half loss (the tracker has
 * no half status; the bet stays a tap) and anything unreadable leave the bet exactly as it was. Props are left to the box-score chain; a first-half or first-inning line is not asked (market names unverified).
 */
class PropLineSettle(
    private val client: PropLineClient,
    private val games: PropLineScores,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun grade(bet: TrackedBet, pick: BetGrader.Pick): BetGrader.Grade.Result? {
        if (!games.enabled) return null
        val sport = PropLineScores.sportKey(bet.league) ?: return null
        val day = FreeScores.etDate(bet.startsTs)
        val game = listOf(day, day.minusDays(1), day.plusDays(1)).firstNotNullOfOrNull { d -> games.games(bet.league, d)?.let { BetGrader.gameOf(bet, it) } } ?: return null
        if (!game.final || game.called) return null
        val body = bodyOf(bet, pick, sport, game) ?: return null
        val raw = try {
            client.settleRaw(body.toString()) ?: return null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        val status = statusOf(raw, json, bet.id) ?: return null
        return BetGrader.Grade.Result(status, "PropLine settled it from the final box score: Final ${game.away} ${game.awayScore ?: "?"}, ${game.home} ${game.homeScore ?: "?"}")
    }

    companion object {
        /** The one-bet request for [pick] in [game] (null when this kind of bet is not asked). `ref` is the bet's id, `price`/`stake` are only the units of its payout, which is never read. */
        fun bodyOf(bet: TrackedBet, pick: BetGrader.Pick, sport: String, game: GameScore): JsonArray? {
            val eventId = game.id.removePrefix(PropLineScores.PREFIX)
            fun sideName(team: String): String? {
                val h = TeamMatcher.similarity(team, game.home)
                val a = TeamMatcher.similarity(team, game.away)
                return if (h == a) null else if (h > a) game.home else game.away
            }
            val leg: JsonObject = when (pick) {
                is BetGrader.Pick.Moneyline -> buildJsonObject { put("market", "h2h"); put("selection", sideName(pick.team) ?: return null) }
                is BetGrader.Pick.Spread -> if (pick.period != BetGrader.Period.GAME) return null else buildJsonObject {
                    put("market", "spreads"); put("selection", sideName(pick.team) ?: return null); put("point", pick.line)
                }
                is BetGrader.Pick.Total -> if (pick.period != BetGrader.Period.GAME) return null else buildJsonObject {
                    put("market", "totals"); put("selection", if (pick.over) "Over" else "Under"); put("side", if (pick.over) "Over" else "Under"); put("point", pick.line)
                }
                is BetGrader.Pick.TeamTotal -> buildJsonObject {
                    put("market", "totals"); put("team", sideName(pick.team) ?: return null); put("selection", if (pick.over) "Over" else "Under"); put("side", if (pick.over) "Over" else "Under"); put("point", pick.line)
                }
                else -> return null
            }
            return buildJsonArray {
                add(buildJsonObject {
                    put("ref", bet.id); put("sport_key", sport); put("event_id", eventId.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(eventId)); put("price", -110); put("stake", 10)
                    for ((k, v) in leg) put(k, v)
                })
            }
        }

        /** The bet's status from a settle answer: only a clean win, loss, push or void (never half results, unmatched, pending or redacted). */
        fun statusOf(raw: String, json: Json, ref: String): BetStatus? {
            val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
            if ((root["redacted"] as? JsonPrimitive)?.contentOrNull == "true") return null
            val row = (root["bets"] as? JsonArray)?.firstOrNull { ((it as? JsonObject)?.get("ref") as? JsonPrimitive)?.contentOrNull == ref } as? JsonObject ?: return null
            return when ((row["result"] as? JsonPrimitive)?.contentOrNull) {
                "won" -> BetStatus.WON
                "lost" -> BetStatus.LOST
                "push" -> BetStatus.PUSH
                "void" -> BetStatus.VOID
                else -> null
            }
        }
    }
}
