package io.realm

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import io.realm.runtimeapi.NativePointer
import io.realm.runtimeapi.RealmCompanion
import io.realm.runtimeapi.RealmModelInternal
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import test.Sample
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class InstrumentedTests {

    val context = InstrumentationRegistry.getInstrumentation().context

    // Smoke test of compiling with library
    @Test
    fun contextIsNotNull() {
        assertNotNull(RealmInitProvider.applicationContext)
    }

    // FIXME Lacks platform abstraction of paths to be able to move this to commonTest
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

        assertEquals("", sample.name)
        sample.name = "Hello, World!"
        assertEquals("Hello, World!", sample.name)
        realm.commitTransaction()
    }

    @Test
    fun testRealmModelInternalPropertiesGenerated() {
        val p = Sample()
        val realmModel: RealmModelInternal = p as? RealmModelInternal ?: error("Supertype RealmModelInternal was not added to Sample class")

        // Accessing getters/setters
        realmModel.`$realm$IsManaged` = true
        realmModel.`$realm$ObjectPointer` = LongPointerWrapper(0xCAFEBABE)
        realmModel.`$realm$Pointer` = LongPointerWrapper(0XCAFED00D)
        realmModel.`$realm$TableName` = "Sample"

        assertEquals(true, realmModel.`$realm$IsManaged`)
        assertEquals(0xCAFEBABE, (realmModel.`$realm$ObjectPointer` as LongPointerWrapper).ptr)
        assertEquals(0XCAFED00D, (realmModel.`$realm$Pointer` as LongPointerWrapper).ptr)
        assertEquals("Sample", realmModel.`$realm$TableName`)
    }
}
