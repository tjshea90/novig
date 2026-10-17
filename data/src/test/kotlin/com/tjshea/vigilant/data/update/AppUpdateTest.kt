package com.tjshea.vigilant.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    private fun release(draft: Boolean = false, pre: Boolean = false, assets: String = """[{"name":"vigilant-v0.113.0.apk","url":"https://api/x/1","browser_download_url":"https://dl/x.apk","size":4800000},{"name":"vigilant-v0.113.0-mapping.txt.gz","url":"u","browser_download_url":"d","size":1}]""") =
        """{"tag_name":"v0.113.0","name":"Vigilant v0.113.0","body":"notes","draft":$draft,"prerelease":$pre,"published_at":"2026-10-16T01:00:00Z","assets":$assets}"""

    @Test
    fun `the apk asset is found and the mapping file is not taken for it`() {
        val u = AppUpdate.parse(release())!!
        assertEquals("0.113.0", u.version)
        assertEquals("https://dl/x.apk", u.apkUrl)
        assertEquals("https://api/x/1", u.apkApiUrl)
        assertEquals(4_800_000L, u.apkBytes)
    }

    @Test
    fun `drafts, prereleases and releases without an apk are no update`() {
        assertNull(AppUpdate.parse(release(draft = true)))
        assertNull(AppUpdate.parse(release(pre = true)))
        assertNull(AppUpdate.parse(release(assets = "[]")))
        assertNull(AppUpdate.parse("not json"))
    }

    @Test
    fun `versions compare as numbers, not text`() {
        val u = AppUpdate.parse(release())!!
        assertTrue(AppUpdate.isNewer(u, "0.112.0"))
        assertTrue(AppUpdate.isNewer(u, "0.99.9"))
        assertFalse(AppUpdate.isNewer(u, "0.113.0"))
        assertFalse(AppUpdate.isNewer(u, "0.200.0"))
        assertEquals(1, AppUpdate.compare("v0.112.0", "0.111.20"))
        assertNotNull(u)
    }

    @Test
    fun `a data archive release listed first does not hide the app release`() {
        val apk = """[{"name":"vigilant-v0.114.18.apk","url":"u","browser_download_url":"https://dl/a.apk","size":9}]"""
        fun rel(tag: String, assets: String) = """{"tag_name":"$tag","name":"$tag","body":"","draft":false,"prerelease":false,"published_at":"2026-10-17T00:00:00Z","assets":$assets}"""
        val list = "[" + listOf(rel("propline-archive-2026-10-17", "[]"), rel("v0.114.17", apk.replace("0.114.18", "0.114.17")), rel("v0.114.18", apk), rel("cloud-archive-2026-10-17", apk)).joinToString(",") + "]"
        assertEquals("v0.114.18", AppUpdate.parseList(list)!!.tag)
        assertNull(AppUpdate.parseList("[" + rel("propline-archive-2026-10-17", "[]") + "]"))
        assertNull(AppUpdate.parseList("not json"))
    }
}
