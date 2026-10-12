package com.tjshea.vigilant.data.boltodds

import okio.ByteString
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/**
 * A BoltOdds websocket frame's text. The docs print JSON and their own client reads `recv()` whatever the frame type, so a BINARY frame is read too: UTF-8 as it is, or gzip (1f 8b) / zlib (78 xx) inflated first.
 * Anything that will not inflate is read as UTF-8 text, so a frame is never lost to a guess about its packing.
 */
internal object BoltFrames {
    fun decode(bytes: ByteString): String {
        val b = bytes.toByteArray()
        try {
            if (b.size >= 2 && b[0] == 0x1f.toByte() && b[1] == 0x8b.toByte()) return GZIPInputStream(b.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (b.size >= 2 && b[0] == 0x78.toByte() && (b[1].toInt() and 0xff) in ZLIB_SECOND) return InflaterInputStream(b.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            // not packed after all: fall through
        }
        return String(b, Charsets.UTF_8)
    }

    private val ZLIB_SECOND = setOf(0x01, 0x5e, 0x9c, 0xda)
}
