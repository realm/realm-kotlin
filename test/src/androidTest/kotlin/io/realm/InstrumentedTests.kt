package io.realm

import android.support.test.runner.AndroidJUnit4
import junit.framework.TestCase.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstrumentedTests {

    // Smoke test of compiling with library
    @Test
    fun contextIsNotNull() {
        assertNotNull(RealmInitProvider.applicationContext)
    }
}
