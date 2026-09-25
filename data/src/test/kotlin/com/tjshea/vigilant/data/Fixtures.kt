package com.tjshea.vigilant.data

/**
 * Wire-format fixtures shaped exactly like responses recorded from the live APIs on 2026-09-25
 * (Novig's public v3 routes, NOVIG_API.md §5) and The Odds API v4 docs. Trimmed to one game:
 * Baltimore Ravens @ Dallas Cowboys.
 */
object Fixtures {
    const val EVENT_ID = "01a0aa74-36dd-7251-b18e-486f25a0821b"
    const val ML_MARKET = "01a0aa74-36db-7163-8ff2-1027a5c4f54f"
    const val ML_DAL = "01a0aa74-36db-7163-8ff2-103e7c9a7496"
    const val ML_BAL = "01a0aa74-36db-7163-8ff2-104c4ec8c5ed"
    const val SPREAD_MARKET = "sp-3.5"
    const val SPREAD_DAL = "sp-dal"
    const val SPREAD_BAL = "sp-bal"
    const val ALT_SPREAD_MARKET = "sp-20.5"
    const val TOTAL_MARKET = "tot-47.5"
    const val TOTAL_OVER = "tot-over"
    const val TOTAL_UNDER = "tot-under"

    /** 2026-09-27T20:25:00Z */
    const val START_MS = 1790540700000L

    val novigEvents = """
        {"items":[{"eventId":"$EVENT_ID","description":"Baltimore Ravens @ Dallas Cowboys","sport":"FOOTBALL","league":"NFL","status":"OPEN_PREGAME","startsTs":$START_MS}]}
    """.trimIndent()

    private const val FEE = """{"coefficient":"0.03","makerCredit":"0.5","charged":"WHEN_LIVE"}"""

    val novigMarkets = """
        {"items":[
          {"marketId":"$ML_MARKET","description":"DAL","eventId":"$EVENT_ID","marketType":"MONEY","status":"OPEN","voids":"FMV","startsTs":$START_MS,"fee":$FEE,
           "outcomes":[{"outcomeId":"$ML_DAL","name":"DAL","status":"TBD"},{"outcomeId":"$ML_BAL","name":"BAL","status":"TBD"}]},
          {"marketId":"$SPREAD_MARKET","description":"DAL +3.5","eventId":"$EVENT_ID","marketType":"SPREAD","status":"OPEN","voids":"FMV","startsTs":$START_MS,"fee":$FEE,
           "outcomes":[{"outcomeId":"$SPREAD_DAL","name":"DAL +3.5","status":"TBD"},{"outcomeId":"$SPREAD_BAL","name":"BAL -3.5","status":"TBD"}]},
          {"marketId":"$ALT_SPREAD_MARKET","description":"DAL +20.5","eventId":"$EVENT_ID","marketType":"SPREAD","status":"OPEN","voids":"FMV","startsTs":$START_MS,"fee":$FEE,
           "outcomes":[{"outcomeId":"alt-dal","name":"DAL +20.5","status":"TBD"},{"outcomeId":"alt-bal","name":"BAL -20.5","status":"TBD"}]},
          {"marketId":"$TOTAL_MARKET","description":"BAL @ DAL t47.5","eventId":"$EVENT_ID","marketType":"TOTAL","status":"OPEN","voids":"FMV","startsTs":$START_MS,"fee":$FEE,
           "outcomes":[{"outcomeId":"$TOTAL_OVER","name":"Over 47.5","status":"TBD"},{"outcomeId":"$TOTAL_UNDER","name":"Under 47.5","status":"TBD"}]}
        ]}
    """.trimIndent()

    /** Live book, 2026-09-25: DAL bid 0.38 and BAL bid 0.615, so taking DAL costs 0.385 and BAL 0.62. */
    val mlBook = """
        {"marketId":"$ML_MARKET","seq":2020,"orders":{
          "$ML_DAL":[{"orderId":"a","price":"0.38","qty":516409},{"orderId":"b","price":"0.375","qty":200000},{"orderId":"c","price":"0.375","qty":150000}],
          "$ML_BAL":[{"orderId":"d","price":"0.615","qty":48541},{"orderId":"e","price":"0.615","qty":200000},{"orderId":"f","price":"0.61","qty":4098}]
        }}
    """.trimIndent()

    val spreadBook = """
        {"marketId":"$SPREAD_MARKET","seq":7,"orders":{
          "$SPREAD_DAL":[{"orderId":"g","price":"0.47","qty":10000}],
          "$SPREAD_BAL":[{"orderId":"h","price":"0.5","qty":20000}]
        }}
    """.trimIndent()

    val totalBook = """
        {"marketId":"$TOTAL_MARKET","seq":3,"orders":{
          "$TOTAL_OVER":[{"orderId":"i","price":"0.49","qty":5000}],
          "$TOTAL_UNDER":[{"orderId":"j","price":"0.49","qty":5000}]
        }}
    """.trimIndent()

    /** The Odds API: Pinnacle's devigged DAL (~0.398) beats Novig's 0.385 take price: a ~3.4% edge. */
    val oddsApi = """
        [{"id":"ref-1","sport_key":"americanfootball_nfl","commence_time":"2026-09-27T20:25:00Z",
          "home_team":"Dallas Cowboys","away_team":"Baltimore Ravens",
          "bookmakers":[
            {"key":"pinnacle","title":"Pinnacle","last_update":"2026-09-25T02:00:00Z","markets":[
              {"key":"h2h","outcomes":[{"name":"Baltimore Ravens","price":1.62},{"name":"Dallas Cowboys","price":2.45}]},
              {"key":"spreads","outcomes":[{"name":"Baltimore Ravens","price":1.95,"point":-3.5},{"name":"Dallas Cowboys","price":1.93,"point":3.5}]},
              {"key":"totals","outcomes":[{"name":"Over","price":1.91,"point":47.5},{"name":"Under","price":1.97,"point":47.5}]}]},
            {"key":"draftkings","title":"DraftKings","last_update":"2026-09-25T02:01:00Z","markets":[
              {"key":"h2h","outcomes":[{"name":"Baltimore Ravens","price":1.57},{"name":"Dallas Cowboys","price":2.45}]},
              {"key":"spreads","outcomes":[{"name":"Baltimore Ravens","price":1.91,"point":-3.0},{"name":"Dallas Cowboys","price":1.91,"point":3.0}]}]},
            {"key":"fanduel","title":"FanDuel","last_update":"2026-09-25T01:59:00Z","markets":[
              {"key":"h2h","outcomes":[{"name":"Baltimore Ravens","price":1.56},{"name":"Dallas Cowboys","price":2.50}]}]}
          ]}]
    """.trimIndent()
}
