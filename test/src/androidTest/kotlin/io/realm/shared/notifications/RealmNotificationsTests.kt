package io.realm.shared.notifications

import io.realm.NotificationTests
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.VersionId
import io.realm.internal.runBlocking
import io.realm.util.PlatformUtils
import io.realm.util.Utils
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import test.Sample
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class RealmNotificationsTests : NotificationTests {

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
            val c = Channel<Realm>(1)
            val startingVersion = realm.version
            val observer = async {
                realm.observe().collect {
                    c.send(it)
                }
            }
            assertEquals(startingVersion, c.receive().version)
            realm.write { /* Do nothing */ }
            assertEquals(VersionId(startingVersion.version + 1), c.receive().version)
            observer.cancel()
            c.close()
        }
    }

    @Test
    @Ignore
    override fun cancelObserve() {
        TODO("Wait for a Global change listener to become available")
    }

    @Test
    override fun initialElement() {
        runBlocking {
            val c = Channel<Realm>(1)
            val startingVersion = realm.version
            val observer = async {
                realm.observe().collect {
                    c.send(it)
                }
            }
            assertEquals(startingVersion, c.receive().version)
            observer.cancel()
            c.close()
        }
    }

    @Test
    override fun deleteObservable() {
        // Realms cannot be deleted, so Realm Flows do not need to handle this case
    }

    @Test
    @Ignore
    override fun closeRealmInsideFlowThrows() {
        TODO("Wait for a Global change listener to become available")
    }

    @Test
    @Ignore
    override fun closingRealmDoesNotCancelFlows() {
        TODO("Wait for a Global change listener to become available")
    }
}
