package io.realm.shared.notifications

import co.touchlab.stately.concurrency.AtomicInt
import io.realm.NotificationTests
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmResults
import io.realm.util.PlatformUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.runBlocking
import test.Sample
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class RealmResultsNotificationsTests : NotificationTests {

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
    override fun initialElement() {
        runBlocking {
            val c = Channel<RealmResults<Sample>>(1)
            val observer = async {
                realm.objects(Sample::class).observe().collect {
                    c.trySend(it)
                }
            }
            val initialElement: RealmResults<Sample> = c.receive()
            assertEquals(0, initialElement.size)
            observer.cancel()
            c.close()
        }
    }

    @Test
    override fun observe() {
        runBlocking {
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
            observer.cancel()
            c.close()
        }
    }

    @Test
    override fun cancelObserve() {
        runBlocking {
            val c1 = Channel<RealmResults<Sample>>(1)
            val c2 = Channel<RealmResults<Sample>>(1)
            val observer1 = async {
                realm.objects(Sample::class).observe().filterNot { it.isEmpty() }.collect {
                    c1.trySend(it)
                }
            }
            val observer2 = async {
                realm.objects(Sample::class).observe().filterNot { it.isEmpty() }.collect {
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
        }
    }

    @Test
    override fun deleteObservable() {
        runBlocking {
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
            observer.cancel()
            c.close()
        }
    }

    @Test
    @Ignore // FIXME Not correctly imlemented yet
    override fun closeRealmInsideFlowThrows() {
        runBlocking {
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
            observer1.cancel()
            observer2.cancel()
            c.close()
        }
    }

    @Test
    override fun closingRealmDoesNotCancelFlows() {
        runBlocking {
            val c = Channel<Int>(capacity = 1)
            val observer = async {
                realm.objects(Sample::class).observe().filterNot { it.isEmpty() }.collect {
                    c.send(it.size)
                }
                fail("Flow should not be canceled.")
            }
            realm.write {
                copyToRealm(Sample().apply { stringField = "Foo" })
            }
            assertEquals(1, c.receive())
            realm.close()
            observer.cancel()
            c.close()
        }
    }
}
