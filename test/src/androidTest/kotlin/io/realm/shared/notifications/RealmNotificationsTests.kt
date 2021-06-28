package io.realm.shared.notifications

import io.realm.CallbackNotificationTests
import io.realm.FlowNotificationTests
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.VersionId
import io.realm.internal.runBlocking
import io.realm.util.PlatformUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import test.Sample
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RealmNotificationsTests : FlowNotificationTests, CallbackNotificationTests {

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
        val c = Channel<Realm>(1)
        val startingVersion = realm.version
        val observer = async {
            realm.observe().collect {
                c.send(it)
            }
        }
        c.receive().version.let { updatedVersion: VersionId ->
            assertEquals(startingVersion, updatedVersion)
        }
        c.close()
        observer.cancel()
    }

    @Test
    override fun observe() = runBlocking {
        val c = Channel<Realm>(1)
        val startingVersion = realm.version
        val observer = async {
            realm.observe().collect {
                c.send(it)
            }
        }
        assertEquals(startingVersion, c.receive().version)
        realm.write { /* Do nothing */ }
        c.receive().version.let { updatedVersion ->
            assertEquals(VersionId(startingVersion.version + 1), updatedVersion)
        }
        observer.cancel()
        c.close()
        Unit
    }

    @Test
    @Ignore
    override fun cancelObserve() {
        TODO("Wait for a Global change listener to become available")
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

    @Test
    override fun initialCallback() = runBlocking {
        val waitLock = Mutex()
        val token = realm.addChangeListener { updatedRealm: Realm ->
            assertTrue {realm == updatedRealm }
            waitLock.unlock()
        }
        waitLock.lock()
        token.cancel()
    }

    @Test
    override fun updateCallback() = runBlocking {
        val c = Channel<Realm>(1)
        val startingVersion = realm.version
        val token = realm.addChangeListener {
            c.trySend(it)
        }
        c.receive().version.let {
            assertEquals(startingVersion, it)
        }
        realm.write { /* Do nothing */ }
        c.receive().version.let { updatedVersion ->
            assertEquals(VersionId(startingVersion.version + 1), updatedVersion)
        }
        token.cancel()
        c.close()
        Unit
    }

    @Test
    override fun observerDeletedCallback() {
        /* Not relevant for Realms as they cannot be deleted while being observed */
    }
}
