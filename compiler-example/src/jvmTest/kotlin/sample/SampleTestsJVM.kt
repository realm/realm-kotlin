package sample

import kotlin.test.Test
import kotlin.test.assertEquals

class SampleTestsJVM {
    @Test
    fun testHello() {
        val p = Person()
        p.name = "Nabil"
        assertEquals("Hello Nabil", p.name)
    }
}
