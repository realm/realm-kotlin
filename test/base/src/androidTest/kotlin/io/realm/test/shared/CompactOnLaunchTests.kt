package io.realm.test.shared

import io.realm.CompactOnLaunchCallback
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.entities.Sample
import io.realm.test.platform.PlatformUtils
import io.realm.test.platform.platformFileSystem
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue


class CompactOnLaunchTests {

    private lateinit var tmpDir: String
    private lateinit var configBuilder: RealmConfiguration.Builder

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configBuilder = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .path("$tmpDir/default.realm")
    }

    @AfterTest
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun compactOnLaunch_defaultConfiguration() {
        val defaultConfig = RealmConfiguration.with(schema = setOf())
        assertNull(defaultConfig.compactOnLaunchCallback)
    }

    @Test
    fun compactOnLaunch_defaultWhenEnabled() {
        val config = RealmConfiguration.Builder()
            .compactOnLaunch()
            .build()
        assertEquals(Realm.DEFAULT_COMPACT_ON_LAUNCH_CALLBACK, config.compactOnLaunchCallback)
    }

    @Test
    fun defaultCallback_boundaries() {
        val callback = Realm.DEFAULT_COMPACT_ON_LAUNCH_CALLBACK
        assertFalse(callback.invoke(50*1024*1024, 40*1024*1024))
        assertFalse(callback.invoke(50*1024*1024+8, 25*1024*1024))
        assertFalse(callback.invoke(50*1024*1024+8, 25*1024*1024+3))
        assertTrue(callback.invoke(50*1024*1024+8, 25*1024*1024+4))
        assertTrue(callback.invoke(50*1024*1024+8, 25*1024*1024+5))
    }

    @Test
    fun compact_emptyRealm() {
        var config = configBuilder.compactOnLaunch { _, _ -> false }.build()
        println("Open")
        Realm.open(config).close()
        val before: Long = platformFileSystem.metadata(config.path.toPath()).size!!
        config = configBuilder.compactOnLaunch { _, _ -> true }.build()
        Realm.open(config).close()
        val after: Long = platformFileSystem.metadata(config.path.toPath()).size!!
        assertTrue(before >= after)
    }

    @Test
    fun compact_populatedRealm() {

    }

    @Test
    fun compact_encryptedRealm() {

    }

    @Test
    fun compact_throwsInCallback() {

    }



//    fun compactRealm_encryptedEmptyRealm() {
//        val realmConfig: RealmConfiguration =
//            configFactory.createConfiguration("enc.realm", TestHelper.getRandomKey())
//        var realm: Realm = getInstance(realmConfig)
//        realm.close()
//        Assert.assertTrue(compactRealm(realmConfig))
//        realm = getInstance(realmConfig)
//        Assert.assertFalse(realm.isClosed())
//        Assert.assertTrue(realm.isEmpty())
//        realm.close()
//    }
//
//    @Test
//    fun compactRealm_encryptedPopulatedRealm() {
//        val DATA_SIZE = 100
//        val realmConfig: RealmConfiguration =
//            configFactory.createConfiguration("enc.realm", TestHelper.getRandomKey())
//        var realm: Realm = getInstance(realmConfig)
//        populateTestRealm(realm, DATA_SIZE)
//        realm.close()
//        Assert.assertTrue(compactRealm(realmConfig))
//        realm = getInstance(realmConfig)
//        Assert.assertFalse(realm.isClosed())
//        assertEquals(DATA_SIZE, realm.where(AllTypes::class.java).count())
//        realm.close()
//    }
//
//    @Test
//    @Throws(IOException::class)
//    fun compactRealm_emptyRealm() {
//        val REALM_NAME = "test.realm"
//        val realmConfig: RealmConfiguration = configFactory.createConfiguration(REALM_NAME)
//        val realm: Realm = getInstance(realmConfig)
//        realm.close()
//        val before = File(realmConfig.path).length()
//        Assert.assertTrue(compactRealm(realmConfig))
//        val after = File(realmConfig.path).length()
//        Assert.assertTrue(before >= after)
//    }
//
//    @Test
//    @Throws(IOException::class)
//    fun compactRealm_populatedRealm() {
//        val REALM_NAME = "test.realm"
//        val realmConfig: RealmConfiguration = configFactory.createConfiguration(REALM_NAME)
//        val realm: Realm = getInstance(realmConfig)
//        populateTestRealm(realm, 100)
//        realm.close()
//        val before = File(realmConfig.path).length()
//        Assert.assertTrue(compactRealm(realmConfig))
//        val after = File(realmConfig.path).length()
//        Assert.assertTrue(before >= after)
//    }
//
//    @Test
//    fun compactRealm_onExternalStorage() {
//        val externalFilesDir: File = context.getExternalFilesDir(null)
//        val config: RealmConfiguration = configFactory.createConfigurationBuilder()
//            .directory(externalFilesDir)
//            .name("external.realm")
//            .build()
//        deleteRealm(config)
//        var realm: Realm = getInstance(config)
//        realm.close()
//        Assert.assertTrue(compactRealm(config))
//        realm = getInstance(config)
//        realm.close()
//        deleteRealm(config)
//    }
//
//    private fun populateTestRealmForCompact(realm: Realm, sizeInMB: Int) {
//        val oneMBData = ByteArray(1024 * 1024)
//        realm.beginTransaction()
//        for (i in 0 until sizeInMB) {
//            realm.createObject(AllTypes::class.java).setColumnBinary(oneMBData)
//        }
//        realm.commitTransaction()
//    }
//
//    private fun populateTestRealmAndCompactOnLaunch(compactOnLaunch: CompactOnLaunchCallback): Pair<Long, Long> {
//        return populateTestRealmAndCompactOnLaunch(compactOnLaunch, 1)
//    }
//
//    private fun populateTestRealmAndCompactOnLaunch(
//        compactOnLaunch: CompactOnLaunchCallback?,
//        sizeInMB: Int
//    ): Pair<Long, Long> {
//        val REALM_NAME = "test.realm"
//        var realmConfig: RealmConfiguration = configFactory.createConfiguration(REALM_NAME)
//        var realm: Realm = getInstance(realmConfig)
//        populateTestRealmForCompact(realm, sizeInMB)
//        realm.beginTransaction()
//        realm.deleteAll()
//        realm.commitTransaction()
//        realm.close()
//        val before = File(realmConfig.path).length()
//        realmConfig = if (compactOnLaunch != null) {
//            configFactory.createConfigurationBuilder()
//                .name(REALM_NAME)
//                .compactOnLaunch(compactOnLaunch)
//                .build()
//        } else {
//            configFactory.createConfigurationBuilder()
//                .name(REALM_NAME)
//                .compactOnLaunch()
//                .build()
//        }
//        realm = getInstance(realmConfig)
//        realm.close()
//        val after = File(realmConfig.path).length()
//        return Pair(before, after)
//    }
//
//    @Test
//    @Throws(IOException::class)
//    fun compactOnLaunch_shouldCompact() {
//        val (first, second) = populateTestRealmAndCompactOnLaunch(object :
//            CompactOnLaunchCallback() {
//            fun shouldCompact(totalBytes: Long, usedBytes: Long): Boolean {
//                Assert.assertTrue(totalBytes > usedBytes)
//                return true
//            }
//        })
//        Assert.assertTrue(first > second)
//    }
//
//    @Test
//    @Throws(IOException::class)
//    fun compactOnLaunch_shouldNotCompact() {
//        val (first, second) = populateTestRealmAndCompactOnLaunch(object :
//            CompactOnLaunchCallback() {
//            fun shouldCompact(totalBytes: Long, usedBytes: Long): Boolean {
//                Assert.assertTrue(totalBytes > usedBytes)
//                return false
//            }
//        })
//        assertEquals(first, second)
//    }
//
//    @Test
//    @Throws(IOException::class)
//    fun compactOnLaunch_multipleThread() {
//        val REALM_NAME = "test.realm"
//        val compactOnLaunchCount = AtomicInteger(0)
//        val realmConfig: RealmConfiguration = configFactory.createConfigurationBuilder()
//            .name(REALM_NAME)
//            .compactOnLaunch(object : CompactOnLaunchCallback() {
//                fun shouldCompact(totalBytes: Long, usedBytes: Long): Boolean {
//                    compactOnLaunchCount.incrementAndGet()
//                    return true
//                }
//            })
//            .build()
//        var realm: Realm = getInstance(realmConfig)
//        realm.close()
//        // WARNING: We need to init the schema first and close the Realm to make sure the relevant logic works in Object
//        // Store. See https://github.com/realm/realm-object-store/blob/master/src/shared_realm.cpp#L58
//        // Called once.
//        Assert.assertEquals(1, compactOnLaunchCount.get().toLong())
//        realm = getInstance(realmConfig)
//        Assert.assertEquals(2, compactOnLaunchCount.get().toLong())
//        val thread = Thread {
//            val bgRealm: Realm = getInstance(realmConfig)
//            bgRealm.close()
//            // compactOnLaunch should not be called anymore!
//            Assert.assertEquals(2, compactOnLaunchCount.get().toLong())
//        }
//        thread.start()
//        try {
//            thread.join()
//        } catch (e: InterruptedException) {
//            Assert.fail()
//        }
//        realm.close()
//        Assert.assertEquals(2, compactOnLaunchCount.get().toLong())
//    }
//
//    @Test
//    @Throws(IOException::class)
//    fun compactOnLaunch_insufficientAmount() {
//        val (first, second) = populateTestRealmAndCompactOnLaunch(object :
//            CompactOnLaunchCallback() {
//            fun shouldCompact(totalBytes: Long, usedBytes: Long): Boolean {
//                val thresholdSize = (50 * 1024 * 1024).toLong()
//                return totalBytes > thresholdSize && usedBytes.toDouble() / totalBytes.toDouble() < 0.5
//            }
//        }, 1)
//        val thresholdSize = (50 * 1024 * 1024).toLong()
//        Assert.assertTrue(first < thresholdSize)
//        assertEquals(first, second)
//    }
//
//    @Test
//    fun compactOnLaunch_throwsInTheCallback() {
//        val exception = RuntimeException()
//        val realmConfig: RealmConfiguration = configFactory.createConfigurationBuilder()
//            .name("compactThrowsTest")
//            .compactOnLaunch(object : CompactOnLaunchCallback() {
//                fun shouldCompact(totalBytes: Long, usedBytes: Long): Boolean {
//                    throw exception
//                }
//            })
//            .build()
//        var realm: Realm? = null
//        try {
//            realm = getInstance(realmConfig)
//            Assert.fail()
//        } catch (expected: RuntimeException) {
//            Assert.assertSame(exception, expected)
//        } finally {
//            realm?.close()
//        }
//    }
//
//    @Test
//    @Throws(IOException::class)
//    fun defaultCompactOnLaunch() {
//        val (first, second) = populateTestRealmAndCompactOnLaunch(null, 50)
//        val thresholdSize = (50 * 1024 * 1024).toLong()
//        Assert.assertTrue(first > thresholdSize)
//        Assert.assertTrue(first > second)
//    }
//
//    @Test
//    fun defaultCompactOnLaunch_onlyCallback() {
//        val callback = DefaultCompactOnLaunchCallback()
//        val thresholdSize = (50 * 1024 * 1024).toLong()
//        val big = thresholdSize + 1024
//        Assert.assertFalse(callback.shouldCompact(big, (big * 0.6).toLong()))
//        Assert.assertTrue(callback.shouldCompact(big, (big * 0.3).toLong()))
//        val small = thresholdSize - 1024
//        Assert.assertFalse(callback.shouldCompact(small, (small * 0.6).toLong()))
//        Assert.assertFalse(callback.shouldCompact(small, (small * 0.3).toLong()))
//    }
//
//    @Test
//    @Throws(IOException::class)
//    fun defaultCompactOnLaunch_insufficientAmount() {
//        val (first, second) = populateTestRealmAndCompactOnLaunch(null, 1)
//        val thresholdSize = (50 * 1024 * 1024).toLong()
//        Assert.assertTrue(first < thresholdSize)
//        assertEquals(first, second)
//    }
}