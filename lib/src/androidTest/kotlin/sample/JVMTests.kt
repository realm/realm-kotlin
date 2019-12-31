package sample

import kotlin.test.Test
import kotlin.test.assertNotNull

class JVMTests {
    @Test
    fun simpleTest() {
        assertNotNull(Runtime.version())
    }
}