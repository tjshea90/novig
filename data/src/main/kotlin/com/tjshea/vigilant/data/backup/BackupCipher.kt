package com.tjshea.vigilant.data.backup

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The full-backup file's encryption (Tj, 2026-10-30: "save a backup of all data in the vigilant app including all keys and all private keys and trading keys ... a 100% mirror copy ... to transfer to Samsung secure folder").
 *
 * The backup holds every key in the clear INSIDE the encryption (the Android Keystore that seals them on a phone cannot be copied to another phone, so they are opened here and sealed again by the target), so
 * the passphrase is the only protection: PBKDF2-SHA256 (210,000 rounds) into AES-256-GCM. The file is streamed in 1 MiB chunks so a large archive never sits in memory, each chunk with its own nonce, its
 * position and a last-chunk flag authenticated (a reordered, dropped or cut-short file fails; it is never half-accepted).
 *
 * Layout: `VIGBAK1\n` | iterations (int) | salt (16) | nonce (8) | chunks of { length (int, high bit = last chunk) | AES-GCM ciphertext }.
 */
object BackupCipher {
    private const val MAGIC = "VIGBAK1\n"
    const val ITERATIONS = 210_000
    private const val CHUNK = 1 shl 20
    private const val SALT = 16
    private const val NONCE = 8
    private const val LAST = 0x80000000.toInt()
    private const val MAX_CHUNK = CHUNK + 64

    private fun key(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun cipher(mode: Int, key: SecretKeySpec, nonce: ByteArray, index: Int, last: Boolean): Cipher {
        val iv = ByteBuffer.allocate(12).put(nonce).putInt(index).array()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(128, iv))
            updateAAD(ByteBuffer.allocate(5).putInt(index).put(if (last) 1 else 0).array())
        }
    }

    /** A stream that encrypts into [out]; close it to write the last chunk (closing it also closes [out]). */
    fun encryptingStream(out: OutputStream, passphrase: String, iterations: Int = ITERATIONS, random: SecureRandom = SecureRandom()): OutputStream {
        require(passphrase.isNotEmpty()) { "a passphrase is needed" }
        val salt = ByteArray(SALT).also(random::nextBytes)
        val nonce = ByteArray(NONCE).also(random::nextBytes)
        val key = key(passphrase, salt, iterations)
        out.write(MAGIC.toByteArray(Charsets.US_ASCII))
        out.write(ByteBuffer.allocate(4).putInt(iterations).array())
        out.write(salt)
        out.write(nonce)
        return object : OutputStream() {
            private val buf = ByteArray(CHUNK)
            private var n = 0
            private var index = 0
            private var closed = false

            private fun emit(last: Boolean) {
                val ct = cipher(Cipher.ENCRYPT_MODE, key, nonce, index++, last).doFinal(buf, 0, n)
                out.write(ByteBuffer.allocate(4).putInt(if (last) ct.size or LAST else ct.size).array())
                out.write(ct)
                n = 0
            }

            override fun write(b: Int) { write(byteArrayOf(b.toByte()), 0, 1) }

            override fun write(b: ByteArray, off: Int, len: Int) {
                var o = off
                var left = len
                while (left > 0) {
                    // A full buffer is written only once more data arrives, so the chunk left at close is the last one.
                    if (n == CHUNK) emit(false)
                    val k = minOf(left, CHUNK - n)
                    System.arraycopy(b, o, buf, n, k)
                    n += k; o += k; left -= k
                }
            }

            override fun flush() { out.flush() }

            override fun close() {
                if (closed) return
                closed = true
                emit(true)
                out.flush()
                out.close()
            }
        }
    }

    /**
     * The decrypted stream, or null when [input] is not a backup or [passphrase] is wrong (the first chunk is opened before returning, so nothing is read on from a wrong passphrase). Later damage throws an
     * [IOException] from the stream; the caller discards what it wrote.
     */
    fun decryptingStream(input: InputStream, passphrase: String): InputStream? {
        val head = ByteArray(MAGIC.length)
        if (!readFully(input, head) || String(head, Charsets.US_ASCII) != MAGIC) return null
        val iter = ByteArray(4).also { if (!readFully(input, it)) return null }.let { ByteBuffer.wrap(it).int }
        if (iter !in 1_000..5_000_000) return null
        val salt = ByteArray(SALT).also { if (!readFully(input, it)) return null }
        val nonce = ByteArray(NONCE).also { if (!readFully(input, it)) return null }
        val key = key(passphrase, salt, iter)
        val stream = object : InputStream() {
            private var chunk = ByteArray(0)
            private var pos = 0
            private var index = 0
            private var done = false

            /** Opens the next chunk; false at the end of the file, an [IOException] for a cut-short or damaged one. */
            fun next(): Boolean {
                if (done) return false
                val lenBytes = ByteArray(4)
                if (!readFully(input, lenBytes)) throw EOFException("the backup file is cut short")
                val raw = ByteBuffer.wrap(lenBytes).int
                val last = raw and LAST != 0
                val len = raw and LAST.inv()
                if (len < 16 || len > MAX_CHUNK) throw IOException("the backup file is damaged")
                val ct = ByteArray(len)
                if (!readFully(input, ct)) throw EOFException("the backup file is cut short")
                chunk = try {
                    cipher(Cipher.DECRYPT_MODE, key, nonce, index++, last).doFinal(ct)
                } catch (e: AEADBadTagException) {
                    throw IOException("wrong passphrase or damaged backup")
                }
                pos = 0
                if (last) { done = true; if (input.read() != -1) throw IOException("the backup file has extra data after its end") }
                return true
            }

            override fun read(): Int {
                while (pos >= chunk.size) if (!next()) return -1
                return chunk[pos++].toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                while (pos >= chunk.size) if (!next()) return -1
                val k = minOf(len, chunk.size - pos)
                System.arraycopy(chunk, pos, b, off, k)
                pos += k
                return k
            }

            override fun close() { input.close() }
        }
        return try { stream.next(); stream } catch (_: IOException) { null }
    }

    private fun readFully(input: InputStream, into: ByteArray): Boolean {
        var got = 0
        while (got < into.size) {
            val r = input.read(into, got, into.size - got)
            if (r < 0) return false
            got += r
        }
        return true
    }

    /** Small helper for tests and callers holding bytes. */
    fun encrypt(plain: ByteArray, passphrase: String, iterations: Int = ITERATIONS): ByteArray {
        val bos = ByteArrayOutputStream()
        encryptingStream(bos, passphrase, iterations).use { it.write(plain) }
        return bos.toByteArray()
    }

    fun decrypt(blob: ByteArray, passphrase: String): ByteArray? =
        runCatching { decryptingStream(blob.inputStream(), passphrase)?.use { it.readBytes() } }.getOrNull()
}
