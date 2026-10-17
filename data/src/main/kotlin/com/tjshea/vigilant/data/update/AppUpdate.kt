package com.tjshea.vigilant.data.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The newest Vigilant release on GitHub as the app's updater sees it (Tj, 2026-10-16: "make it so GitHub/Claude can push updates directly to the vigilant android app ... a push notification and an install update
 * button which automatically downloads the update from GitHub"). Pure: the GitHub `releases/latest` answer in, a decision out; the app does the downloading and installing.
 */
data class AppUpdate(
    val tag: String,
    val name: String,
    val notes: String,
    /** The APK asset: its public download URL, its API URL (works with a token for a private repo) and size in bytes. */
    val apkUrl: String,
    val apkApiUrl: String,
    val apkBytes: Long,
    val publishedAt: String,
) {
    /** "0.112.0" out of "v0.112.0". */
    val version: String get() = tag.removePrefix("v")

    companion object {
        /** The release in [text] (GitHub's `releases/latest` JSON) or null when it is a draft, a prerelease, or has no APK. */
        fun parse(text: String): AppUpdate? = runCatching {
            val o = Json.parseToJsonElement(text).jsonObject
            if (o["draft"]?.jsonPrimitive?.content == "true" || o["prerelease"]?.jsonPrimitive?.content == "true") return null
            val apk = o["assets"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { a -> a.str("name").let { it.endsWith(".apk") && !it.contains("mapping") } } ?: return null
            AppUpdate(
                tag = o.str("tag_name"), name = o.str("name"), notes = o.str("body"), apkUrl = apk.str("browser_download_url"), apkApiUrl = apk.str("url"),
                apkBytes = apk["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L, publishedAt = o.str("published_at"),
            ).takeIf { it.tag.isNotBlank() && it.apkUrl.isNotBlank() }
        }.getOrNull()

        private val VERSION_TAG = Regex("""^v\d+\.\d+\.\d+$""")

        /**
         * The newest app release in [text] (GitHub's `releases?per_page=30` array). `releases/latest` answers with the newest Release of ANY kind, and the data
         * archives (`propline-archive-*`, `cloud-archive-*`) now come after the app's own, so the app's release is picked out by its `vN.N.N` tag and its APK.
         */
        fun parseList(text: String): AppUpdate? = runCatching {
            Json.parseToJsonElement(text).jsonArray.mapNotNull { e -> e.jsonObject.takeIf { VERSION_TAG.matches(it.str("tag_name")) }?.let { parse(it.toString()) } }
                .maxWithOrNull { a, b -> compare(a.tag, b.tag) }
        }.getOrNull()

        private fun JsonObject.str(k: String) = this[k]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonPrimitive?.content.orEmpty()

        /** Dotted numbers compared part by part ("0.112.0" > "0.111.9" > "0.99.0"); anything non-numeric counts as 0. */
        fun compare(a: String, b: String): Int {
            val x = a.removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
            val y = b.removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(x.size, y.size)) {
                val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
                if (c != 0) return c
            }
            return 0
        }

        /** True when [latest] is a newer release than the running [current] version name. */
        fun isNewer(latest: AppUpdate, current: String): Boolean = compare(latest.tag, current) > 0
    }
}
