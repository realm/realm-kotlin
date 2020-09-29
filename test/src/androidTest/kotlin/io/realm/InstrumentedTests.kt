package io.realm

import android.support.test.runner.AndroidJUnit4
import junit.framework.TestCase.*
import org.junit.Test
import org.junit.runner.RunWith
import test.Sample


@RunWith(AndroidJUnit4::class)
class InstrumentedTests {

    // Smoke test of compiling with library
    @Test
    fun contextIsNotNull() {
        assertNotNull(RealmInitProvider.applicationContext)
    }

    // Smoke test of compiler plugin
    @Test
    fun testCompilerPlugin() {
        val p = Sample()
        p.name = "Realm"
        assertEquals("Hello Realm", p.name)
    }

}
