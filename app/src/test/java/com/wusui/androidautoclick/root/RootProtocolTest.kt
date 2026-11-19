package com.wusui.androidautoclick.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootProtocolTest {
    @Test
    fun formatsCommands() {
        assertEquals("CONFIG 1080 2400 0", RootProtocol.config(1080, 2400))
        assertEquals("CONFIG 1080 2400 1", RootProtocol.config(1080, 2400, 1))
        assertEquals("TAP 100 200 16", RootProtocol.tap(100.4f, 199.6f, 16))
        assertEquals("SWIPE 1 2 3 4 280", RootProtocol.swipe(1f, 2f, 3f, 4f, 280))
        assertEquals("PING", RootProtocol.PING)
    }

    @Test
    fun clampsDurationAndSize() {
        assertEquals("TAP 0 0 1", RootProtocol.tap(0f, 0f, 0))
        assertEquals("CONFIG 1 1 0", RootProtocol.config(0, -5))
        assertEquals("CONFIG 1080 2400 3", RootProtocol.config(1080, 2400, 7))
        assertEquals("SWIPE 0 0 1 1 60000", RootProtocol.swipe(0f, 0f, 1f, 1f, 99_000))
    }

    @Test
    fun parseConfig_twoFieldsDefaultsRotation0() {
        assertEquals(RootProtocol.ConfigCmd(1080, 2400, 0), RootProtocol.parseConfig("CONFIG 1080 2400"))
        assertEquals(RootProtocol.ConfigCmd(1, 1, 0), RootProtocol.parseConfig("CONFIG 0 -5"))
    }

    @Test
    fun parseConfig_threeFieldsIncludesRotation() {
        assertEquals(RootProtocol.ConfigCmd(1080, 2400, 1), RootProtocol.parseConfig("CONFIG 1080 2400 1"))
        assertEquals(RootProtocol.ConfigCmd(1080, 2400, 3), RootProtocol.parseConfig("CONFIG 1080 2400 7"))
        assertEquals(RootProtocol.ConfigCmd(1080, 2400, 2), RootProtocol.parseConfig("  CONFIG  1080  2400  2  "))
    }

    @Test
    fun parseConfig_rejectsJunk() {
        assertNull(RootProtocol.parseConfig("TAP 1 2 3"))
        assertNull(RootProtocol.parseConfig("CONFIG"))
        assertNull(RootProtocol.parseConfig("CONFIG 1080"))
        assertNull(RootProtocol.parseConfig(""))
    }

    @Test
    fun parsesOk() {
        assertTrue(RootProtocol.isOk("OK"))
        assertTrue(RootProtocol.isOk("OK\n"))
        assertTrue(RootProtocol.isOk(" OK "))
        assertTrue(RootProtocol.isOk("OK evdev"))
        assertTrue(RootProtocol.isOk("OK uinput"))
        assertFalse(RootProtocol.isOk("ERR uinput"))
        assertFalse(RootProtocol.isOk(null))
        assertFalse(RootProtocol.isOk(""))
    }
}
