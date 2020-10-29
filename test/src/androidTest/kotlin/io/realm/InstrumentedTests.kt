package io.realm

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import io.realm.runtimeapi.RealmCompanion
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import test.Sample


@RunWith(AndroidJUnit4::class)
class InstrumentedTests {

    val context = InstrumentationRegistry.getInstrumentation().context

    // Smoke test of compiling with library
    @Test
    fun contextIsNotNull() {
        assertNotNull(RealmInitProvider.applicationContext)
    }

    @Test
    fun realmConfig() {
        val configuration = RealmConfiguration.Builder()
                .path(context.filesDir.absolutePath + "/library-test.realm")
                .factory { kClass ->
                    when (kClass) {
                        Sample::class -> Sample()
                        else -> TODO()
                    }
                }
                .classes(listOf(Sample.Companion as RealmCompanion))
                .build()
        val realm = Realm.open(configuration)
        realm.beginTransaction()
        val sample = realm.create(Sample::class)
        val x = sample.name

        assertEquals("", sample.test)
        sample.test = "Hello, World!"
        realm.commitTransaction()
        assertEquals("Hello, World!", sample.test)

    }

}

