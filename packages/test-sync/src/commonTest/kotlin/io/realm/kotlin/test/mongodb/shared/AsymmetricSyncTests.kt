package io.realm.kotlin.test.mongodb.shared

import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.syncSession
import io.realm.kotlin.schema.RealmClassKind
import io.realm.kotlin.test.mongodb.TEST_APP_FLEX
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.types.AsymmetricRealmObject
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PersistedName
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.ObjectId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DeviceParent : RealmObject {
    @PersistedName("_id")
    @PrimaryKey
    var id: ObjectId = ObjectId()
    var device: Device? = null
}

class Measurement : AsymmetricRealmObject {
    @PersistedName("_id")
    @PrimaryKey
    var id: ObjectId = ObjectId()
    var type: String = "temperature"
    var value: Float = 0.0f
    var device: Device? = null
}

class BackupDevice() : EmbeddedRealmObject {
    constructor(name: String, serialNumber: String) : this() {
        this.name = name
        this.serialNumber = serialNumber
    }
    var name: String = ""
    var serialNumber: String = ""
}

class Device() : EmbeddedRealmObject {
    constructor(name: String, serialNumber: String) : this() {
        this.name = name
        this.serialNumber = serialNumber
    }
    var name: String = ""
    var serialNumber: String = ""
    var backupDevice: BackupDevice? = null
}

class AsymmetricSyncTests {

    private lateinit var app: TestApp
    private lateinit var realm: Realm
    private lateinit var config: SyncConfiguration

    @BeforeTest
    fun setup() {
        app = TestApp(appName = TEST_APP_FLEX)
        val (email, password) = TestHelper.randomEmail() to "password1234"
        val user = runBlocking {
            app.createUserAndLogIn(email, password)
        }
        config = SyncConfiguration.Builder(
            user,
            schema = setOf(
                Measurement::class,
                Device::class,
                BackupDevice::class,
                DeviceParent::class
            )
        ).initialSubscriptions {
            it.query<DeviceParent>().subscribe()
        }.build()
        realm = Realm.open(config)
    }

    @AfterTest
    fun tearDown() {
        realm.close()
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun copyToRealm() {
        realm.writeBlocking {
            repeat(10) { no ->
                copyToRealm(
                    Measurement().apply {
                        value = 42.0f + no.toFloat()
                        device = Device(name = "living room", serialNumber = "TMP432142")
                    }
                )
            }
        }
        // TOD How to verify this?
    }

    @Test
    fun copyToRealm_samePrimaryKey_throws() {
        val generatedId = ObjectId()
        realm.writeBlocking {
            copyToRealm(
                Measurement().apply {
                    id = generatedId
                }
            )
            assertFailsWith<IllegalArgumentException>() {
                copyToRealm(
                    Measurement().apply {
                        id = generatedId
                    }
                )
            }
        }
    }

    @Test
    fun realmClassSchema_isAsymmetric() {
        assertEquals(RealmClassKind.ASYMMETRIC, realm.schema()[Measurement::class.simpleName!!]!!.kind)
    }

    @Test
    fun findLatestThrows() {
        realm.writeBlocking {
            // There is no way to get a managed Measurement, but calling `findLatest` on an
            // unmanaged AsymmetricObjet should still fail.
            assertFailsWith<IllegalArgumentException> {
                findLatest(Measurement())
            }
        }
    }

    // If you have A -> B, where A is an asymmetric object and B is embedded it is still possible
    // to query for B. However, no objects belong to asymmetric objects will be found.
    @Test
    fun nestedEmbeddedHierarchyIsQueryable() = runBlocking {
        realm.syncSession.pause()
        realm.write {
            repeat(10) { no ->
                copyToRealm(
                    Measurement().apply {
                        value = 42.0f + no.toFloat()
                        device = Device(name = "living room", serialNumber = "TMP432142").apply {
                            backupDevice = BackupDevice(name = "kitchen", serialNumber = "TMP14090")
                        }
                    }
                )
            }
            repeat(10) {
                copyToRealm(
                    DeviceParent().apply {
                        device = Device(name = "living room", serialNumber = "TMP432142")
                    }
                )
            }
        }
        // Will only find the embedded objects belong to standard realm objects
        assertEquals(10, realm.query<Device>().count().find())

        // Nested embedded objects are not searchable either if the top-level is asymmetric.
        assertEquals(0, realm.query<BackupDevice>().count().find())
    }
}
