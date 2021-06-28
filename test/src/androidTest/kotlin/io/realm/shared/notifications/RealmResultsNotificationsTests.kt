package io.realm.shared.notifications

import co.touchlab.stately.concurrency.AtomicInt
import io.realm.CallbackNotificationTests
import io.realm.Cancellable
import io.realm.FlowNotificationTests
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmResults
import io.realm.log.LogLevel
import io.realm.util.PlatformUtils
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
import kotlin.test.assertTrue
import kotlin.test.fail

class RealmResultsNotificationsTests : FlowNotificationTests, CallbackNotificationTests {

    lateinit var tmpDir: String
    lateinit var configuration: RealmConfiguration
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration =
            RealmConfiguration.Builder(path = "$tmpDir/default.realm", schema = setOf(Sample::class)).log(LogLevel.DEBUG).build()
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
        val c = Channel<RealmResults<Sample>>(1)
        val observer = async {
            realm.objects(Sample::class).observe().collect {
                c.trySend(it)
            }
        }
        val initialElement: RealmResults<Sample> = c.receive()
        assertEquals(0, initialElement.size)
        c.close()
        observer.cancel()
    }

    @Test
    override fun observe() = runBlocking {
        val c = Channel<Int>(capacity = 1)
        val observer = async {
            realm.objects(Sample::class).observe().collect {
                c.trySend(it.size)
            }
        }
        realm.write {
            copyToRealm(Sample().apply { stringField = "Foo" })
        }
        assertEquals(1, c.receive())
        c.close()
        observer.cancel()
    }

    @Test
    override fun cancelObserve() = runBlocking {
        val c1 = Channel<RealmResults<Sample>>(1)
        val c2 = Channel<RealmResults<Sample>>(1)
        val observer1 = async {
            realm.objects(Sample::class).observe().collect {
                c1.trySend(it)
            }
        }
        val observer2 = async {
            realm.objects(Sample::class).observe().collect {
                c2.trySend(it)
            }
        }
        realm.write {
            copyToRealm(Sample().apply { stringField = "Bar" })
        }
        assertEquals(1, c1.receive().size)
        assertEquals(1, c2.receive().size)
        observer1.cancel()
        realm.write {
            copyToRealm(Sample().apply { stringField = "Baz" })
        }
        assertEquals(2, c2.receive().size)
        assertTrue(c1.isEmpty)
        observer2.cancel()
        c1.close()
        c2.close()
        Unit
    }

    @Test
    override fun deleteObservable() = runBlocking {
        val c = Channel<RealmResults<Sample>>(1)
        realm.write {
            copyToRealm(
                Sample().apply {
                    stringField = "Foo"
                }
            )
        }
        val observer = async {
            realm.objects(Sample::class).observe().collect {
                c.trySend(it)
            }
        }
        assertEquals(1, c.receive().size)
        realm.write {
            delete(objects(Sample::class).first())
        }
        assertEquals(0, c.receive().size)
        c.close()
        observer.cancel()
    }

    @Test
    @Ignore // FIXME Not correctly imlemented yet
    override fun closeRealmInsideFlowThrows() = runBlocking {
        val c = Channel<Int>(capacity = 1)
        val counter = AtomicInt(0)
        val observer1 = async {
            realm.objects(Sample::class).observe().collect {
                when (counter.incrementAndGet()) {
                    1 -> c.trySend(it.size)
                    2 -> {
                        realm.close()
                        c.trySend(-1)
                        println("realm closed")
                    }
                }
            }
        }
        val observer2 = async {
            realm.objects(Sample::class).observe().collect {
                println(it.first().stringField)
                println("$it -> ${realm.isClosed()}")
            }
        }
        realm.write {
            copyToRealm(Sample().apply { stringField = "Foo" })
        }
        assertEquals(1, c.receive())
        realm.write {
            copyToRealm(Sample().apply { stringField = "Bar" })
        }
        assertEquals(-1, c.receive())
        c.close()
        observer1.cancel()
        observer2.cancel()
    }

    @Test
    override fun closingRealmDoesNotCancelFlows() = runBlocking {
        val c = Channel<Int>(capacity = 1)
        val observer = async {
            realm.objects(Sample::class).observe().collect {
                c.send(it.size)
            }
            fail("Flow should not be canceled.")
        }
        realm.write {
            copyToRealm(Sample().apply { stringField = "Foo" })
        }
        assertEquals(1, c.receive())
        realm.close()
        c.close()
        observer.cancel()
    }

    override fun initialCallback() = runBlocking {
        val c = Channel<RealmResults<Sample>>(1)
        val token = realm.objects(Sample::class).addChangeListener {
            c.trySend(it)
        }
        val initialElement: RealmResults<Sample> = c.receive()
        assertEquals(0, initialElement.size)
        c.close()
        token.cancel()
    }

    override fun updateCallback() = runBlocking {
        val c = Channel<Int>(capacity = 1)
        val token = realm.objects(Sample::class).addChangeListener {
            c.trySend(it.size)
        }
        realm.write {
            copyToRealm(Sample().apply { stringField = "Foo" })
        }
        assertEquals(1, c.receive())
        c.close()
        token.cancel()
    }

    override fun observerDeletedCallback() {
        /* It isn't possible to delete the parent of a RealmResults */
    }
}
