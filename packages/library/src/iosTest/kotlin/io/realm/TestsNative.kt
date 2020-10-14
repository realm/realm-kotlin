package io.realm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestsNative {
    @Test
    fun testHello() {
        assertTrue(1 == 1)
    }

    @Test
    fun cinterop_klib() {
        assertEquals("10.0.0-beta.5", io.realm.interop.RealmInterop.realm_get_library_version())
    }
}
