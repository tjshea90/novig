package com.tjshea.vigilant.data.reference

/**
 * Responses trimmed from the live APIs on 2026-09-25 (Polymarket Gamma, Kalshi v2) and from
 * pinnapi's published docs (no key existed to call it live).
 */
object ExchangeFixtures {

    /** Ravens @ Cowboys, 2026-09-27 20:25Z. Values as seen live, plus a few edge cases. */
    val polymarketNfl = """
    [
      {"id":"1","question":"Ravens vs. Cowboys","outcomes":"[\"Ravens\", \"Cowboys\"]","bestBid":0.62,"bestAsk":0.63,"line":null,
       "sportsMarketType":"moneyline","gameStartTime":"2026-09-27 20:25:00+00","liquidityNum":297040.3,"active":true,"closed":false,"acceptingOrders":true,
       "events":[{"slug":"nfl-bal-dal-2026-09-27"}]},
      {"id":"2","question":"Spread: Ravens (-3.5)","outcomes":"[\"Ravens\", \"Cowboys\"]","bestBid":0.48,"bestAsk":0.49,"line":-3.5,
       "sportsMarketType":"spreads","gameStartTime":"2026-09-27 20:25:00+00","liquidityNum":219451,"active":true,"closed":false,
       "events":[{"slug":"nfl-bal-dal-2026-09-27"}]},
      {"id":"3","question":"Ravens vs. Cowboys: O/U 47.5","outcomes":"[\"Over\", \"Under\"]","bestBid":0.63,"bestAsk":0.70,"line":47.5,
       "sportsMarketType":"totals","gameStartTime":"2026-09-27 20:25:00+00","liquidityNum":4244,"active":true,"closed":false,
       "events":[{"slug":"nfl-bal-dal-2026-09-27"}]},
      {"id":"4","question":"Ravens vs. Cowboys: O/U 50.5","outcomes":"[\"Over\", \"Under\"]","bestBid":0.59,"bestAsk":0.60,"line":50.5,
       "sportsMarketType":"totals","gameStartTime":"2026-09-27 20:25:00+00","liquidityNum":92800,"active":true,"closed":false,
       "events":[{"slug":"nfl-bal-dal-2026-09-27"}]},
      {"id":"5","question":"Ravens vs. Cowboys: O/U 51.5","outcomes":"[\"Over\", \"Under\"]","bestBid":0.55,"bestAsk":0.56,"line":51.5,
       "sportsMarketType":"totals","gameStartTime":"2026-09-27 20:25:00+00","liquidityNum":250,"active":true,"closed":false,
       "events":[{"slug":"nfl-bal-dal-2026-09-27"}]},
      {"id":"6","question":"Spread: Ravens (-3)","outcomes":"[\"Ravens\", \"Cowboys\"]","bestBid":0.52,"bestAsk":0.53,"line":-3.0,
       "sportsMarketType":"spreads","gameStartTime":"2026-09-27 20:25:00+00","liquidityNum":50000,"active":true,"closed":false,
       "events":[{"slug":"nfl-bal-dal-2026-09-27"}]},
      {"id":"7","question":"Panthers vs. Browns","outcomes":"[\"Panthers\", \"Browns\"]","bestBid":0.57,"bestAsk":0.58,"line":null,
       "sportsMarketType":"moneyline","gameStartTime":"2026-09-27 17:00:00+00","liquidityNum":236990.6,"active":true,"closed":false,
       "events":[{"slug":"nfl-car-cle-2026-09-27"}]}
    ]
    """.trimIndent()

    /** NFL game, spread and total events for Carolina @ Cleveland, exactly as Kalshi shaped them. */
    val kalshiNflGame = """
    {"events":[{"event_ticker":"KXNFLGAME-26SEP27CARCLE","series_ticker":"KXNFLGAME","title":"Carolina vs Cleveland","sub_title":"CAR vs CLE (Sep 27)",
      "markets":[
        {"ticker":"KXNFLGAME-26SEP27CARCLE-CLE","title":"Cleveland wins","yes_sub_title":"Cleveland","yes_bid_dollars":"0.4200","yes_ask_dollars":"0.4300","floor_strike":null,"status":"active"},
        {"ticker":"KXNFLGAME-26SEP27CARCLE-CAR","title":"Carolina wins","yes_sub_title":"Carolina","yes_bid_dollars":"0.5700","yes_ask_dollars":"0.5800","floor_strike":null,"status":"active"}]}],
     "cursor":""}
    """.trimIndent()

    val kalshiNflSpread = """
    {"events":[{"event_ticker":"KXNFLSPREAD-26SEP27CARCLE","series_ticker":"KXNFLSPREAD","title":"CAR Panthers vs CLE Browns: Spread","sub_title":"CAR vs CLE (Sep 27)",
      "markets":[
        {"ticker":"KXNFLSPREAD-26SEP27CARCLE-CAR3","title":"CAR Panthers wins by over 2.5 points?","yes_bid_dollars":"0.4500","yes_ask_dollars":"0.4600","floor_strike":2.5,"status":"active"},
        {"ticker":"KXNFLSPREAD-26SEP27CARCLE-CAR21","title":"CAR Panthers wins by over 20.5 points?","yes_bid_dollars":"0.0700","yes_ask_dollars":"0.0800","floor_strike":20.5,"status":"active"},
        {"ticker":"KXNFLSPREAD-26SEP27CARCLE-CLE4","title":"CLE Browns wins by over 3.5 points?","yes_bid_dollars":"0.2000","yes_ask_dollars":"0.3000","floor_strike":3.5,"status":"active"},
        {"ticker":"KXNFLSPREAD-26SEP27CARCLE-CAR7","title":"CAR Panthers wins by over 7 points?","yes_bid_dollars":"0.2000","yes_ask_dollars":"0.2100","floor_strike":7.0,"status":"active"}]}],
     "cursor":""}
    """.trimIndent()

    val kalshiNflTotal = """
    {"events":[{"event_ticker":"KXNFLTOTAL-26SEP27CARCLE","series_ticker":"KXNFLTOTAL","title":"CAR Panthers vs CLE Browns: Total Points","sub_title":"CAR vs CLE (Sep 27)",
      "markets":[
        {"ticker":"KXNFLTOTAL-26SEP27CARCLE-44","title":"Full Game: over 43.5 points scored?","yes_sub_title":"Over 43.5 points scored","yes_bid_dollars":"0.5100","yes_ask_dollars":"0.5200","floor_strike":43.5,"status":"active"}]}],
     "cursor":""}
    """.trimIndent()

    /** Baseball codes carry the Eastern start time; game 2 of a doubleheader has its own code. */
    val kalshiMlbGame = """
    {"events":[
      {"event_ticker":"KXMLBGAME-26SEP251840PITDET","series_ticker":"KXMLBGAME","title":"Pittsburgh vs Detroit","sub_title":"PIT vs DET (Sep 25)",
       "markets":[
        {"ticker":"KXMLBGAME-26SEP251840PITDET-PIT","yes_sub_title":"Pittsburgh","yes_bid_dollars":"0.4900","yes_ask_dollars":"0.5000","status":"active"},
        {"ticker":"KXMLBGAME-26SEP251840PITDET-DET","yes_sub_title":"Detroit","yes_bid_dollars":"0.5000","yes_ask_dollars":"0.5100","status":"active"}]},
      {"event_ticker":"KXMLBGAME-26SEP251845NYMWSH","series_ticker":"KXMLBGAME","title":"New York M vs Washington","sub_title":"NYM vs WSH (Sep 25)",
       "markets":[
        {"ticker":"KXMLBGAME-26SEP251845NYMWSH-NYM","yes_sub_title":"New York M","yes_bid_dollars":"0.5400","yes_ask_dollars":"0.5500","status":"active"},
        {"ticker":"KXMLBGAME-26SEP251845NYMWSH-WSH","yes_sub_title":"Washington","yes_bid_dollars":"0.4500","yes_ask_dollars":"0.4600","status":"active"}]}],
     "cursor":""}
    """.trimIndent()

    val kalshiUfc = """
    {"events":[{"event_ticker":"KXUFCFIGHT-26SEP26HERDUM","series_ticker":"KXUFCFIGHT","title":"Fight Night: Hernandez vs Dumas","sub_title":"Hernandez vs Dumas",
      "markets":[
        {"ticker":"KXUFCFIGHT-26SEP26HERDUM-HER","yes_sub_title":"Luis Hernandez","yes_bid_dollars":"0.6600","yes_ask_dollars":"0.6700","status":"active"},
        {"ticker":"KXUFCFIGHT-26SEP26HERDUM-DUM","yes_sub_title":"Sedriques Dumas","yes_bid_dollars":"0.3200","yes_ask_dollars":"0.3300","status":"active"}]}],
     "cursor":""}
    """.trimIndent()

    /** pinnapi `/kit/v1/markets?sport_id=5&event_type=prematch`, in their documented shape. */
    val pinnapiFootball = """
    {"sport_id":5,"sport_name":"Football","last":297592,"events":[
      {"event_id":1628594960,"sport_id":5,"league_id":889,"league_name":"NFL","starts":"2026-09-27T20:25:00Z","last":1790300000,
       "home":"Dallas Cowboys","away":"Baltimore Ravens","event_type":"prematch","is_have_odds":true,
       "periods":{"num_0":{"number":0,"description":"Game",
         "money_line":{"home":2.45,"away":1.62},
         "spreads":{"3.5":{"hdp":3.5,"home":1.93,"away":1.95,"max":10000},"3.0":{"hdp":3.0,"home":2.02,"away":1.86,"max":10000}},
         "totals":{"47.5":{"points":47.5,"over":1.91,"under":1.97,"max":5000}}}}},
      {"event_id":1628594961,"sport_id":5,"league_id":880,"league_name":"NCAA","starts":"2026-09-26T19:30:00Z",
       "home":"Oklahoma Sooners","away":"Texas Longhorns","event_type":"prematch",
       "periods":{"num_0":{"money_line":{"home":1.80,"away":2.10}}}}
    ]}
    """.trimIndent()
}
