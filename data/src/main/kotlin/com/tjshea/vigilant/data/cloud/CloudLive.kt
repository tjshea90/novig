package com.tjshea.vigilant.data.cloud

/**
 * The live link between the phone and the running engine (TCL80, Tj 2026-10-17: "I want a true real time monitor ... every couple seconds at most"; "tunnel yes"). Through GitHub the status can be no fresher than the
 * engine's pushes (8 s apart) plus the phone's polls. So the engine also serves its CURRENT status, read-only, from a tiny web server on the runner, reached through a free Cloudflare quick tunnel; the tunnel's
 * address goes into `status.json` ([CloudStatus.liveUrl]) and the phone connects straight to it and falls back to GitHub the moment it cannot. The server hands out the same public status the repo already
 * holds: no key, no list, no command. Pure helpers here; the server is in the `cloud` module, the reader in the app's CloudLink.
 */
object CloudLive {
    /** A direct reading older than this is no longer "live": the phone says "via GitHub" again. */
    const val LIVE_FRESH_MS = 5_000L

    private val TUNNEL = Regex("""https://[a-z0-9-]+\.trycloudflare\.com""")

    /** The quick tunnel's address out of cloudflared's log, or null while it has not printed one yet. */
    fun tunnelUrl(log: String): String? = TUNNEL.find(log)?.value

    /** The address the phone reads: the tunnel plus this run's path token, `/s/<token>/stream` for the stream and `/s/<token>` for one reading. */
    fun url(tunnel: String, token: String): String = tunnel.trimEnd('/') + "/s/" + token

    private val SAFE = Regex("""^https://[a-z0-9-]+\.trycloudflare\.com/s/[0-9a-f]{24,}$""")

    /** Only an address of exactly this shape is ever opened by the phone: a status file can never point it anywhere else. */
    fun safeUrl(u: String): Boolean = SAFE.matches(u)

    /** A token is 24+ lowercase hex letters: nothing else is accepted in the path (the server checks the same rule). */
    fun validToken(t: String): Boolean = t.length >= 24 && t.all { it in '0'..'9' || it in 'a'..'f' }

    /**
     * Server-sent events, line by line: `data: {json}` lines join into one message that ends at the blank line; comment lines (`: ping`) and other fields are ignored. [feed] returns the message text when a
     * blank line completes one, else null.
     */
    class SseReader {
        private val data = StringBuilder()
        fun feed(line: String): String? {
            if (line.isEmpty()) {
                if (data.isEmpty()) return null
                val out = data.toString(); data.setLength(0); return out
            }
            if (line.startsWith(":")) return null
            if (line.startsWith("data:")) {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.removePrefix("data:").removePrefix(" "))
            }
            return null
        }
    }

    /** The words for the phone's chip: whether the engine's status is arriving straight from it or through GitHub. */
    fun chip(directAtMs: Long, now: Long): String =
        if (directAtMs > 0L && now - directAtMs <= LIVE_FRESH_MS) "LIVE (direct)" else "via GitHub (8-10 s behind)"
}
