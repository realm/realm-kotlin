package io.realm.shared.notifications

import io.realm.NotificationTests
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.observe
import io.realm.util.PlatformUtils
import io.realm.util.Utils
import io.realm.util.update
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import test.Sample
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class RealmObjectNotificationsTests : NotificationTests {

    lateinit var tmpDir: String
    lateinit var configuration: RealmConfiguration
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration =
            RealmConfiguration(path = "$tmpDir/${Utils.createRandomString(16)}.realm", schema = setOf(Sample::class))
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
    override fun observe() {
        runBlocking {
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
            observer.cancel()
            c.close()
        }
    }

    @Test
    override fun cancelObserve() {
        runBlocking {
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
            observer2.cancel()
            c1.close()
            c2.close()
        }
    }

    @Test
    override fun initialElement() {
        runBlocking {
            val c = Channel<Sample?>(1)
            val obj = realm.write {
                copyToRealm(Sample().apply {
                    stringField = "Foo"
                })
            }
            val observer = async {
                obj.observe().collect {
                    c.trySend(it)
                }
            }
            assertEquals("Foo", c.receive()!!.stringField)
            observer.cancel()
            c.close()
        }
    }

    @Test
    override fun deleteObservable() {
        runBlocking {
            val c = Channel<Sample?>(1)
            val obj: Sample = realm.write {
                copyToRealm(Sample().apply { stringField = "Foo" })
            }
            val observer = async {
                obj.observe().collect {
                    c.trySend(it)
                }
                // Emit sentinel value to signal that flow completed
                c.send(Sample())
            }
            assertNotNull(c.receive())
            realm.write {
                delete(findLatest(obj)!!)
            }
            assertNull(c.receive()) // Null is sent when object is deleted
            assertEquals(Sample().stringField, c.receive()!!.stringField) // Test for sentinel value
            observer.cancel()
            c.close()
        }
    }

    @Test
    @Ignore
    override fun closeRealmInsideFlowThrows() {
        TODO("Not yet implemented")
    }

    @Test
    override fun closingRealmDoesNotCancelFlows() {
        runBlocking {
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
            observer.cancel()
            c.close()
        }
    }
}