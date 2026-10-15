package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.LocalClock
import com.tjshea.vigilant.app.ui.UsageSection
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.app.ui.cloudCallsToday
import com.tjshea.vigilant.data.cloud.CloudUsage
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.keys.KeyUsage
import com.tjshea.vigilant.data.keys.ProviderUsage
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMerge
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tj, 2026-10-15: "Make sure I can still see accurate api usage meters in the app on my phone." With the scan on GitHub the engine spends the keys too: the meter shows what BOTH spent, says how much was
 * GitHub's, and says how old the provider's own figure is.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h1600dp-xxhdpi")
class CloudUsageUiTest {
    @get:Rule val compose = createComposeRule()

    private val now = SampleScan.NOW
    private val key = "propline-live-key-0123456789abcdef"
    private val day = QuotaPolicy.PROPLINE.periodStart(now)

    private fun ledger(used: Int, calls: Int, todayCalls: Int, lastCall: Long) = UsageBook(
        mapOf("propline" to ProviderUsage(keys = mapOf(key to KeyUsage(periodStart = day, used = used, calls = calls, lastCallMs = lastCall)), dayStart = QuotaPolicy.NOVIG.periodStart(now), callsToday = todayCalls)),
    )

    private fun state(local: UsageBook, cloud: UsageBook, cloudAt: Long = now - 20_000): UiState {
        val base = SampleScan.state().withKeys(ApiProvider.PROPLINE, listOf(key))
        return base.copy(usage = UsageMerge.merge(local, cloud, now), cloudUsage = cloud, cloudUsageAtMs = cloudAt, settings = base.settings.copy(usePropLine = true))
    }

    private fun show(s: UiState) = compose.setContent {
        CompositionLocalProvider(LocalClock provides { now }) { VigilantTheme(darkTheme = true) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { UsageSection(s) } } }
    }

    @Test
    fun `the meter counts what the phone and GitHub both spent, and says how much was GitHub's`() {
        val s = state(local = ledger(used = 12, calls = 12, todayCalls = 12, lastCall = now - 5_000), cloud = ledger(used = 300, calls = 300, todayCalls = 300, lastCall = now - 2_000))
        show(s)
        // 312 of the day's 1,000 are used, 688 left (counted: this phone's calls plus GitHub's)
        compose.onNode(hasText("312/1000 used", substring = true)).assertIsDisplayed()
        compose.onNode(hasText("688 left", substring = true)).assertIsDisplayed()
        compose.onNode(hasText("312 requests today", substring = true)).assertIsDisplayed()
        compose.onNodeWithTag("usageCloudShare").assertIsDisplayed()
        compose.onNode(hasText("of which the GitHub engine made 300", substring = true)).assertIsDisplayed()
        assertEquals(300, cloudCallsToday(s, "propline", now))
    }

    @Test
    fun `with nothing from GitHub the meter is what it always was, and an old day's GitHub count is not today's`() {
        val s = state(local = ledger(used = 12, calls = 12, todayCalls = 12, lastCall = now - 5_000), cloud = UsageBook())
        show(s)
        compose.onNode(hasText("12/1000 used", substring = true)).assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTag("usageCloudShare").fetchSemanticsNodes().size)
        val yesterday = QuotaPolicy.NOVIG.periodStart(now) - 86_400_000L
        val old = UsageBook(mapOf("propline" to ProviderUsage(dayStart = yesterday, callsToday = 900)))
        assertEquals(0, cloudCallsToday(state(UsageBook(), old), "propline", now))
    }

    @Test
    fun `a provider's own figure says how old it is, whichever machine read it`() {
        val figure = UsageBook(mapOf("parlay" to ProviderUsage(keys = mapOf("pk" to KeyUsage(periodStart = QuotaPolicy.PARLAY.periodStart(now), used = 380, remaining = 620, limit = 1000, calls = 80, lastCallMs = now - 120_000, figureAtMs = now - 120_000)), dayStart = QuotaPolicy.NOVIG.periodStart(now), callsToday = 80)))
        val base = SampleScan.state().withKeys(ApiProvider.PARLAY, listOf("pk"))
        show(base.copy(usage = UsageMerge.merge(UsageBook(), figure, now), cloudUsage = figure, cloudUsageAtMs = now - 10_000, settings = base.settings.copy(useParlay = true)))
        compose.onNode(hasText("620 left (the API's own figure, read", substring = true)).assertIsDisplayed()
    }

    @Test
    fun `the container lays the engine's published usage over the phone's, keyed by this phone's own keys, and a key it does not have is dropped`() {
        val app = ApplicationProvider.getApplicationContext<VigilantApp>()
        val c = app.container
        kotlinx.coroutines.runBlocking { c.keyStore.setKeys(ApiProvider.PROPLINE, listOf(key)) }
        val published = CloudUsage.decode(CloudUsage.encode(ledger(7, 7, 7, now - 1_000), "run-1", now, listOf(key)))!!
        val withOther = published.copy(book = UsageBook(published.book.providers.mapValues { (_, p) -> p.copy(keys = p.keys + ("deadbeef00" to KeyUsage(used = 999))) }))
        c.cloudUsage.value = withOther
        val book = c.cloudUsageBook()
        assertEquals(setOf(key), book.providers.getValue("propline").keys.keys)
        val merged = c.mergedUsage(ledger(3, 3, 3, now - 9_000), now)
        assertEquals(10, merged.providers.getValue("propline").keys.getValue(key).used)
        c.cloudUsage.value = null
    }
}
