package com.tjshea.vigilant.app

/** The words of Settings › BoltOdds Pro (BOLTODDS_API.md). */
object BoltOddsText {
    const val INTRO = "BoltOdds Pro pushes odds from many sportsbooks over a WebSocket the moment they change, with a snapshot of the starting state. Vigilant keeps two sockets open while scans use them (the game lines, and the player props; Pro allows three) and prices from what they have already delivered, so a scan costs nothing and a quote is as fresh as the last push. Scores, play-by-play and historical odds are not part of Pro."
    const val SWITCH_TITLE = "Use BoltOdds"
    fun switchSub(hasKey: Boolean) = if (!hasKey) "Add a key first. Until you do, nothing changes." else "Off: nothing is sent to BoltOdds and no socket is open. On: BoltOdds joins the fair line beside every other API you have on, and the sockets open while a scan needs them."
    const val ALL_BOOKS_TITLE = "Every sportsbook BoltOdds lists"
    const val ALL_BOOKS_SUB = "Off: only the books picked under Fair odds. On: every book BoltOdds carries joins the fair line (each still has to price both sides, and a company that owns several books counts once)."
    const val NOTES = "BoltOdds is read after SportsGameOdds and OddsPapi for a book several send, until its first Test sample has been checked against what it really sends. The first scan after a connection waits a few seconds for the snapshot; a connection that drops empties its board, so an old quote is never taken for a current one. A refused key rests and the next key is tried."
}
