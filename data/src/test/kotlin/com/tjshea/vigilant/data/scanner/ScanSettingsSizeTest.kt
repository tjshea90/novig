package com.tjshea.vigilant.data.scanner

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ScanSettings is one data class with a parameter per setting. The JVM (and Android's dex format) allow at most 255 argument slots in a method, a double or long taking two, and the compiler
 * adds a bitmask per 32 defaults to `copy$default` and the synthetic constructor. This fails with room to spare, so the setting that would break the build is moved into its own class before
 * it does (v0.82.0 stood at 222 of 255).
 */
class ScanSettingsSizeTest {
    private fun slots(types: Array<Class<*>>) = 1 + types.sumOf { if (it == java.lang.Double.TYPE || it == java.lang.Long.TYPE) 2 else 1 as Int }

    @Test fun noScanSettingsMethodNearsTheLimitOf255ArgumentSlots() {
        val c = ScanSettings::class.java
        val worst = (c.declaredConstructors.map { slots(it.parameterTypes) } + c.declaredMethods.map { slots(it.parameterTypes) }).max()
        assertTrue("ScanSettings needs $worst of 255 argument slots: split settings into groups before adding more", worst <= 245)
    }
}
