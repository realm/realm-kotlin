package io.realm

import test.Sample
import kotlin.test.Test
import kotlin.test.assertEquals

class SampleTestsJVM {
    @Test
    fun testHello() {
        val p = Sample()
        p.name = "Realm"
        assertEquals("Hello Realm", p.name)
    }
}
