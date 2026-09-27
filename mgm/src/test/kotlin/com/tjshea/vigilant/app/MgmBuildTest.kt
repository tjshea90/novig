package com.tjshea.vigilant.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.book.Sportsbook
import com.tjshea.vigilant.data.cno.CnoView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The real Vigilant MGM build (this module compiles `app`'s sources with `BOOK = "betmgm"`):
 * its own app id, its own name, and every book-specific part on BetMGM. The screens themselves are
 * tested in `app` (MgmAppTest) with the book switched, so both apps share one set of screen tests.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MgmBuildTest {

    @Test fun `this build prices BetMGM under its own app id and name`() {
        assertEquals("betmgm", BuildConfig.BOOK)
        assertEquals("com.tjshea.vigilant.betmgm", BuildConfig.APPLICATION_ID)
        assertEquals(Sportsbook.BETMGM, AppBook.current)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("Vigilant MGM", context.getString(R.string.app_name))
    }

    @Test fun `its CNO list, bet links and scanner are BetMGM's`() {
        assertTrue(UiState(loaded = true).cnoUrl.contains("site_id=${Sportsbook.BETMGM.cnoSiteId}"))
        assertEquals(CnoView.defaultFor("4"), UiState().cnoUrl)
        assertEquals("https://sports.nj.betmgm.com/en/sports?options=1234567-88-99&type=Single",
            AppBook.betLink("x", com.tjshea.vigilant.data.book.BookRef("1234567", "88-99"), "nj"))
    }
}
