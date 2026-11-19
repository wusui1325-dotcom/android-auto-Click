package com.wusui.androidautoclick.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootProtocolTest {
    @Test
    fun formatsCommands() {
        assertEquals("CONFIG 1080 2400", RootProtocol.config(1080, 2400))
        assertEquals("TAP 100 200 16", RootProtocol.tap(100.4f, 199.6f, 16))
        assertEquals("SWIPE 1 2 3 4 280", RootProtocol.swipe(1f, 2f, 3f, 4f, 280))
        assertEquals("PING", RootProtocol.PING)
    }

    @Test
    fun clampsDurationAndSize() {
        assertEquals("TAP 0 0 1", RootProtocol.tap(0f, 0f, 0))
        assertEquals("CONFIG 1 1", RootProtocol.config(0, -5))
        assertEquals("SWIPE 0 0 1 1 60000", RootProtocol.swipe(0f, 0f, 1f, 1f, 99_000))
    }

    @Test
    fun parsesOk() {
        assertTrue(RootProtocol.isOk("OK"))
        assertTrue(RootProtocol.isOk("OK\n"))
        assertTrue(RootProtocol.isOk(" OK "))
        assertFalse(RootProtocol.isOk("ERR uinput"))
        assertFalse(RootProtocol.isOk(null))
        assertFalse(RootProtocol.isOk(""))
    }
}
