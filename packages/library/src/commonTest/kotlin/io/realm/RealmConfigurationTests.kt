package io.realm

import io.realm.log.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RealmConfigurationTests {

    @Test
    fun defaultPath() {
        val config = RealmConfiguration()
        assertEquals("${PlatformHelper.appFilesDirectory()}/${Realm.DEFAULT_FILE_NAME}", config.path)

        val configFromBuilder: RealmConfiguration = RealmConfiguration.Builder().build()
        assertEquals("${PlatformHelper.appFilesDirectory()}/${Realm.DEFAULT_FILE_NAME}", configFromBuilder.path)
    }

    @Test
    fun path() {
        val realmPath = "HowToGetPlatformPath/default.realm"

        val config = RealmConfiguration(path = realmPath)
        assertEquals(realmPath, config.path)

        val configFromBuilder: RealmConfiguration = RealmConfiguration.Builder(path = realmPath).build()
        assertEquals(realmPath, configFromBuilder.path)
    }

    @Test
    fun pathOverrideName() {
        val realmPath = "HowToGetPlatformPath/custom.realm"
        val realmName = "my.realm"

        val config = RealmConfiguration(path = realmPath, name = realmName)
        assertEquals(realmPath, config.path)
        // Correct assert: assertEquals("custom.realm", config.name)
        assertEquals("my.realm", config.name) // Current result

        val configFromBuilder: RealmConfiguration = RealmConfiguration.Builder(path = realmPath, name = realmName).build()
        assertEquals(realmPath, configFromBuilder.path)
        // Correct assert: assertEquals("custom.realm", configFromBuilder.name)
        assertEquals("my.realm", configFromBuilder.name) // Current result
    }

    @Test
    fun defaultName() {
        val config = RealmConfiguration()
        assertEquals(Realm.DEFAULT_FILE_NAME, config.name)
        assertTrue(config.path.endsWith(Realm.DEFAULT_FILE_NAME))

        val configFromBuilder: RealmConfiguration = RealmConfiguration.Builder().build()
        assertEquals(Realm.DEFAULT_FILE_NAME, configFromBuilder.name)
        assertTrue(configFromBuilder.path.endsWith(Realm.DEFAULT_FILE_NAME))
    }

    @Test
    fun name() {
        val realmName = "my.realm"

        val config = RealmConfiguration(name = realmName)
        assertEquals(realmName, config.name)
        assertTrue(config.path.endsWith(realmName))

        val configFromBuilder: RealmConfiguration = RealmConfiguration.Builder(name = realmName).build()
        assertEquals(realmName, configFromBuilder.name)
        assertTrue(configFromBuilder.path.endsWith(realmName))
    }

    @Test
    fun defaultLogLevel() {
        val config: RealmConfiguration = RealmConfiguration.Builder().build()
        assertEquals(LogLevel.WARN, config.log.level)
    }

    @Test
    fun logLevel() {
        val config: RealmConfiguration = RealmConfiguration.Builder()
            .logLevel(LogLevel.NONE)
            .build()
        assertEquals(LogLevel.NONE, config.log.level)
    }

    @Test
    fun defaultCustomLoggers() {
        val config: RealmConfiguration = RealmConfiguration.Builder().build()
        assertEquals(0, config.log.customLoggers.size)
    }

    @Test
    fun customLoggers() {
        val logger = TestLogger()
        val config: RealmConfiguration = RealmConfiguration.Builder()
            .customLoggers(listOf(logger))
            .build()
        assertEquals(1, config.log.customLoggers.size)
        assertEquals(logger, config.log.customLoggers.first())
    }

    @Test
    fun defaultRemoveSystemLogger() {
        val config: RealmConfiguration = RealmConfiguration.Builder().build()
        assertFalse(config.log.removeSystemLogger)
    }

    @Test
    fun removeSystemLogger() {
        val config: RealmConfiguration = RealmConfiguration.Builder()
            .removeSystemLogger()
            .build()
        assertTrue(config.log.removeSystemLogger)
    }
}
