package io.realm.shared.notifications

import io.realm.NotificationTests
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.util.PlatformUtils
import test.Sample
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test

class RealmListNotificationsTests : NotificationTests {

    lateinit var tmpDir: String
    lateinit var configuration: RealmConfiguration
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration =
            RealmConfiguration.Builder(path = "$tmpDir/default.realm", schema = setOf(Sample::class)).build()
        realm = Realm.openBlocking(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (!realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    @Ignore
    override fun initialElement() {
        TODO("Waiting for RealmList support")
    }

    @Test
    @Ignore
    override fun observe() {
        TODO("Waiting for RealmList support")
    }

    @Test
    @Ignore
    override fun cancelObserve() {
        TODO("Waiting for RealmList support")
    }

    @Test
    @Ignore
    override fun deleteObservable() {
        TODO("Waiting for RealmList support")
    }

    @Test
    @Ignore
    override fun closeRealmInsideFlowThrows() {
        TODO("Waiting for RealmList support")
    }

    @Test
    @Ignore
    override fun closingRealmDoesNotCancelFlows() {
        TODO("Waiting for RealmList support")
    }
}
