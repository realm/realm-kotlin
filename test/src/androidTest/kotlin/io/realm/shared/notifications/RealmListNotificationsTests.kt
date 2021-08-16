package io.realm.shared.notifications

import io.realm.NotificationTests
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmList
import io.realm.internal.freeze
import io.realm.shared.OBJECT_VALUES
import io.realm.util.PlatformUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import test.list.RealmListContainer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class RealmListNotificationsTests : NotificationTests {

    lateinit var tmpDir: String
    lateinit var configuration: RealmConfiguration
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration =
            RealmConfiguration(
                path = "$tmpDir/default.realm",
                schema = setOf(RealmListContainer::class)
            )
        realm = Realm(configuration)
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
        val dataSet = OBJECT_VALUES

        realm.writeBlocking {
            val managedContainer = copyToRealm(RealmListContainer())
            managedContainer.objectListField
                .addAll(dataSet)
        }

        runBlocking {
            val channel = Channel<RealmList<*>>(capacity = 1)
            val observer = async {
                val container = realm.objects<RealmListContainer>()
                    .first()

                container.objectListField
                    .observe()
                    .collect { flowList ->
                        channel.send(flowList)
                    }
            }

            // Assertion after empty list is emitted
            val emittedList = channel.receive()
            assertNotNull(emittedList)
            assertEquals(dataSet.size, emittedList.size)

            observer.cancel()
            channel.close()
        }
    }

    @Test
    override fun observe() {
        val dataSet = OBJECT_VALUES

        realm.writeBlocking {
            // Just create an empty container with empty lists
            copyToRealm(RealmListContainer())
        }

        runBlocking {
            val channel = Channel<RealmList<*>>(capacity = 1)
            val observer = async {
                val container = realm.objects<RealmListContainer>()
                    .first()

                container.objectListField
                    .observe()
                    .collect { flowList ->
                        channel.send(flowList)
                    }
            }

            // Assertion after empty list is emitted
            val firstEmittedList = channel.receive()
            assertNotNull(firstEmittedList)
            assertEquals(0, firstEmittedList.size)

            // Trigger update
            realm.writeBlocking {
                val queriedContainer = objects<RealmListContainer>()
                    .first()
                val queriedList = queriedContainer.objectListField
                queriedList.addAll(dataSet)
            }

            // Assertion after list is updated
            val updatedList = channel.receive()
            assertNotNull(updatedList)
            assertEquals(dataSet.size, updatedList.size)

            observer.cancel()
            channel.close()
        }
    }

    @Test
    override fun cancelObserve() {
        runBlocking {
            // Freeze values since native complains if we reference a package-level defined variable
            // inside a write block
            val values = OBJECT_VALUES.freeze()
            val managedContainer = realm.write {
                copyToRealm(RealmListContainer())
            }
            val channel1 = Channel<RealmList<*>>(1)
            val channel2 = Channel<RealmList<*>>(1)
            val observer1 = async {
                managedContainer.objectListField
                    .observe()
                    .collect { flowList ->
                        channel1.trySend(flowList)
                    }
            }
            val observer2 = async {
                managedContainer.objectListField
                    .observe()
                    .collect { flowList ->
                        channel2.trySend(flowList)
                    }
            }

            // Ignore first emission with empty lists
            channel1.receive()
            channel2.receive()

            // Trigger an update
            realm.write {
                val objects = objects<RealmListContainer>()
                val queriedContainer = objects
                    .first()
                queriedContainer.objectListField
                    .addAll(values.map { copyToRealm(it) })
            }
            assertEquals(OBJECT_VALUES.size, channel1.receive().size)
            assertEquals(OBJECT_VALUES.size, channel2.receive().size)

            // Cancel observer 1
            observer1.cancel()

            // Trigger another update
            realm.write {
                val queriedContainer = objects<RealmListContainer>()
                    .first()
                queriedContainer.objectListField
                    .add(copyToRealm(RealmListContainer().apply { stringField = "C" }))
            }

            // Check channel 1 didn't receive the update
            assertEquals(OBJECT_VALUES.size + 1, channel2.receive().size)
            assertTrue(channel1.isEmpty)

            observer2.cancel()
            channel1.close()
            channel2.close()
        }
    }

    @Test
    override fun deleteObservable() {
        runBlocking {
            // Freeze values since native complains if we reference a package-level defined variable
            // inside a write block
            val values = OBJECT_VALUES.freeze()
            val channel = Channel<RealmList<*>>(capacity = 1)
            val managedContainer = realm.write {
                RealmListContainer()
                    .apply {
                        objectListField.addAll(values.map { copyToRealm(it) })
                    }.let { container ->
                        copyToRealm(container)
                    }
            }
            val observer = async {
                managedContainer.objectListField
                    .observe()
                    .collect { flowList ->
                        channel.send(flowList)
                    }
            }

            // Assert container got populated correctly
            val emittedList = channel.receive()
            assertNotNull(emittedList)
            assertEquals(OBJECT_VALUES.size, emittedList.size)

            realm.write {
                delete(findLatest(managedContainer)!!)
            }

            observer.cancel()
            channel.close()
        }
    }

    @Test
    @Ignore // FIXME Wait for https://github.com/realm/realm-kotlin/pull/300 to be merged before fleshing this out
    override fun closeRealmInsideFlowThrows() {
        TODO("Waiting for RealmList support")
    }

    @Test
    override fun closingRealmDoesNotCancelFlows() {
        runBlocking {
            val channel = Channel<RealmList<*>>(capacity = 1)
            val container = realm.write {
                copyToRealm(RealmListContainer())
            }
            val observer = async {
                container.objectListField
                    .observe()
                    .collect { flowList ->
                        channel.trySend(flowList)
                    }
                fail("Flow should not be canceled.")
            }
            assertTrue(channel.receive().isEmpty())
            realm.close()
            observer.cancel()
            channel.close()
        }
    }
}
