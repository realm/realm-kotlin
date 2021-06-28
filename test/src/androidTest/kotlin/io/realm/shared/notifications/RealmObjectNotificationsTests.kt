package io.realm.shared.notifications

import io.realm.addChangeListener
import io.realm.CallbackNotificationTests
import io.realm.FlowNotificationTests
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.observe
import io.realm.util.PlatformUtils
import io.realm.util.update
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.runBlocking
import test.Sample
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class RealmObjectNotificationsTests : FlowNotificationTests, CallbackNotificationTests {

    lateinit var tmpDir: String
    lateinit var configuration: RealmConfiguration
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration =
            RealmConfiguration(path = "$tmpDir/default.realm", schema = setOf(Sample::class))
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (!realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    override fun initialElement() = runBlocking {
        val c = Channel<Sample?>(1)
        val obj = realm.write {
            copyToRealm(
                Sample().apply { stringField = "Foo" }
            )
        }
        val observer = async {
            obj.observe().collect {
                c.trySend(it)
            }
        }
        assertEquals("Foo", c.receive()!!.stringField)
        c.close()
        observer.cancel()
    }

    @Test
    override fun observe() = runBlocking {
        val c = Channel<Sample?>(1)
        val obj: Sample = realm.write {
            copyToRealm(Sample().apply { stringField = "Foo" })
        }
        val observer = async {
            obj.observe().collect {
                c.trySend(it)
            }
        }
        assertEquals("Foo", c.receive()!!.stringField)
        obj.update {
            stringField = "Bar"
        }
        assertEquals("Bar", c.receive()!!.stringField)
        obj.update {
            stringField = "Baz"
        }
        assertEquals("Baz", c.receive()!!.stringField)
        c.close()
        observer.cancel()
    }

    @Test
    override fun cancelObserve() = runBlocking {
        val obj: Sample = realm.write {
            copyToRealm(Sample().apply { stringField = "Foo" })
        }
        val c1 = Channel<Sample?>(1)
        val c2 = Channel<Sample?>(1)
        val observer1 = async {
            obj.observe().collect {
                c1.trySend(it)
            }
        }
        val observer2 = async {
            obj.observe().collect {
                c2.trySend(it)
            }
        }
        obj.update {
            stringField = "Bar"
        }
        assertEquals("Bar", c1.receive()!!.stringField)
        assertEquals("Bar", c2.receive()!!.stringField)
        observer1.cancel()
        obj.update {
            stringField = "Baz"
        }
        assertEquals("Baz", c2.receive()!!.stringField)
        assertTrue(c1.isEmpty)
        c1.close()
        c2.close()
        observer2.cancel()
}

    @Test
    override fun deleteObservable() = runBlocking {
        val c = Channel<Sample?>(1)
        val obj: Sample = realm.write {
            copyToRealm(Sample().apply { stringField = "Foo" })
        }
        val observer = async {
            obj.observe()
                .onCompletion {
                    // Emit sentinel value to signal that flow completed
                    c.send(Sample())
                }
                .collect {
                    c.send(it)
                }
        }
        assertNotNull(c.receive())
        realm.write {
            delete(findLatest(obj)!!)
        }
        assertEquals(Sample().stringField, c.receive()!!.stringField) // Test for sentinel value
        c.close()
        observer.cancel()
    }

    @Test
    @Ignore
    override fun closeRealmInsideFlowThrows() {
        TODO("Not yet implemented")
    }

    @Test
    override fun closingRealmDoesNotCancelFlows() = runBlocking {
        val c = Channel<Sample?>(1)
        val obj: Sample = realm.write {
            copyToRealm(Sample().apply { stringField = "Foo" })
        }
        val observer = async {
            obj.observe().collect {
                c.trySend(it)
            }
            fail("Flow should not be canceled.")
        }
        assertEquals("Foo", c.receive()!!.stringField)
        realm.close()
        c.close()
        observer.cancel()
    }

    override fun initialCallback() = runBlocking {
        val c = Channel<Sample?>(1)
        val obj: Sample = realm.write {
            copyToRealm(
                Sample().apply { stringField = "Foo" }
            )
        }
        val observer = obj.addChangeListener {
            c.trySend(it)
        }
        assertEquals("Foo", c.receive()!!.stringField)
        c.close()
        observer.cancel()
    }

    override fun updateCallback() = runBlocking {
        val c = Channel<Sample?>(1)
        val obj: Sample = realm.write {
            copyToRealm(Sample().apply { stringField = "Foo" })
        }
        val token = obj.addChangeListener {
            c.trySend(it)
        }
        assertEquals("Foo", c.receive()!!.stringField)
        obj.update {
            stringField = "Bar"
        }
        assertEquals("Bar", c.receive()!!.stringField)
        obj.update {
            stringField = "Baz"
        }
        assertEquals("Baz", c.receive()!!.stringField)
        c.close()
        token.cancel()
    }

    override fun observerDeletedCallback() = runBlocking {
        val c = Channel<Sample?>(1)
        val obj: Sample = realm.write {
            copyToRealm(Sample().apply { stringField = "Foo" })
        }
        val token = obj.addChangeListener {
            c.trySend(it)
        }
        assertEquals("Foo", c.receive()!!.stringField)
        realm.write {
            findLatest(obj)?.let {
                delete(it)
            }
        }
        assertNull(c.receive())
        c.close()
        token.cancel()
    }

    @Test
    override fun addingListenerOnUnmanagedObjectThrows() {
        val obj = Sample()
        assertFailsWith<IllegalStateException> { obj.addChangeListener { fail() } }
    }
}
