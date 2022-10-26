package io.realm.kotlin.test.shared.nonlatin

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NonLatinFieldNames : RealmObject {
    var 베타: String = "베타" // Korean
    var Βήτα: String = "Βήτα" // Greek
    var ЙйКкЛл: String = "ЙйКкЛл" // Cyrillic
    var 山水要: String = "山水要" // Chinese
    var ععسنملل: String = "ععسنملل" // Arabic
    var `😊🙈`: String = "😊🙈" // Emojii
}

class NonLatinClassёжф : RealmObject {
    var prop: String = "property"
    var list: RealmList<String> = realmListOf()
    var nullList: RealmList<String?> = realmListOf()
}

class NonLatinTests {

    lateinit var tmpDir: String
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            RealmConfiguration.Builder(setOf(NonLatinFieldNames::class, NonLatinClassёжф::class))
                .directory(tmpDir)
                .build()
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
    fun nonLatinClassNames() {
        val schema = realm.schema()[NonLatinClassёжф::class.simpleName.toString()]!!
        assertEquals(3, schema.properties.size)
        assertEquals("NonLatinClassёжф", schema.name)
    }

    @Test
    fun nonLatinProperties() {
        val schema = realm.schema()[NonLatinFieldNames::class.simpleName.toString()]!!
        assertEquals(6, schema.properties.size)
        assertNotNull(schema[NonLatinFieldNames().베타])
        assertNotNull(schema[NonLatinFieldNames().Βήτα])
        assertNotNull(schema[NonLatinFieldNames().ЙйКкЛл])
        assertNotNull(schema[NonLatinFieldNames().山水要])
        assertNotNull(schema[NonLatinFieldNames().ععسنملل])
        assertNotNull(schema[NonLatinFieldNames().`😊🙈`])
    }

    @Test
    fun roundtripNonLatinValues() {
        val values = listOf(
            "베타",
            "Βήτα",
            "ЙйКкЛл",
            "山水要",
            "ععسنملل",
            "😊🙈"
        )

        val obj = realm.writeBlocking {
            copyToRealm(
                NonLatinClassёжф().apply {
                    list.addAll(values)
                    nullList.addAll(values)
                }
            )
        }
        assertTrue(obj.list.containsAll(values))
        assertTrue(obj.nullList.containsAll(values))
    }

    // \0 has special semantics both in C++ and in Java, so ensure we can roundtrip
    // it correctly.
    @Test
    fun roundtripNullCharacter() {
        val nullChar = "\u0000"
        val shortNullString = "foo\u0000bar"
        val mediumNullString = "abcdefghijklmnopqrstuvwxyz-\u0000-abcdefghijklmnopqrstuvwxyz"
        val obj = realm.writeBlocking {
            copyToRealm(
                NonLatinClassёжф().apply {
                    prop = nullChar
                    list.addAll(listOf(nullChar, shortNullString, mediumNullString))
                    nullList.addAll(listOf(nullChar, shortNullString, mediumNullString))
                }
            )
        }

        assertEquals(nullChar, obj.prop)
        assertEquals(1, obj.prop.length)

        assertEquals(3, obj.list.size)
        assertEquals(nullChar, obj.list[0])
        assertEquals(shortNullString, obj.list[1])
        assertEquals(mediumNullString, obj.list[2])

        assertEquals(3, obj.nullList.size)
        assertEquals(nullChar, obj.nullList[0])
        assertEquals(shortNullString, obj.nullList[1])
        assertEquals(mediumNullString, obj.nullList[2])
    }
}
