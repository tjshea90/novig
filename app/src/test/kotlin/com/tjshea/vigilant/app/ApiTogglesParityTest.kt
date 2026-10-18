package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.cloud.ApiToggles
import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/** The engine's API table (`ApiToggles`, data module) must flip exactly what the phone's Settings › APIs switch flips (`ApiEntry`), so a request from GitHub and a tap on the phone mean the same (TCL28). */
class ApiTogglesParityTest {
    private val ids = mapOf(
        "SGO" to "sgo",
        "BOLTODDS" to "boltodds",
        "ODDSPAPI" to "oddspapi",
        "ODDSPAPI_FREE" to "oddspapi_free",
        "UNABATED" to "unabated",
        "PARLAY" to "parlay",
        "PROPLINE" to "propline",
        "ODDS_API" to "odds_api",
        "PINNWIRE" to "pinnwire",
        "PINNAPI" to "pinnapi",
        "KALSHI" to "kalshi",
        "POLYMARKET" to "polymarket",
        "SXBET" to "sxbet"
    )

    @Test fun `every phone API switch has an engine id, and both flip the same setting`() {
        assertEquals(ApiEntry.entries.filter { it != ApiEntry.PINNODDS }.map { it.name }.toSet(), ids.keys)
        assertEquals(ids.values.toSet(), ApiToggles.IDS.toSet())
        for ((name, id) in ids) {
            val e = ApiEntry.valueOf(name)
            for (start in listOf(ScanSettings(), e.setOn(ScanSettings(), true), e.setOn(ScanSettings(), false))) {
                for (on in listOf(true, false)) {
                    val phone = e.setOn(start, on)
                    val engine = ApiToggles.setOn(start, id, on)
                    assertEquals("$name -> $on", e.isOn(phone), e.isOn(engine))
                    assertEquals("$name -> $on isOn", on, ApiToggles.isOn(engine, id))
                }
            }
        }
    }
}
