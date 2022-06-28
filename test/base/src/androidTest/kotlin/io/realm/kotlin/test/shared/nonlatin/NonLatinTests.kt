package io.realm.kotlin.test.shared.nonlatin

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.entities.link.Child
import io.realm.kotlin.entities.link.Parent
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.use
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
            RealmConfiguration.Builder(schema = setOf(Parent::class, Child::class, Sample::class))
                .directory(tmpDir)
                .build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun nonLatinClassNames() {
        val config = RealmConfiguration.Builder(setOf(NonLatinClassёжф::class))
            .directory(tmpDir)
            .build()

        Realm.open(config).use {
            val schema = it.schema()[NonLatinClassёжф::class.simpleName.toString()]!!
            assertEquals(3, schema.properties.size)
            assertEquals("NonLatinClassёжф", schema.name)
        }
    }

    @Test
    fun nonLatinProperties() {
        val config = RealmConfiguration.Builder(setOf(NonLatinFieldNames::class))
            .directory(tmpDir)
            .build()

        Realm.open(config).use {
            val schema = it.schema()[NonLatinFieldNames::class.simpleName.toString()]!!
            assertEquals(6, schema.properties.size)
            schema.properties.forEach {
                println(it.name)
            }
            assertNotNull(schema[NonLatinFieldNames().베타])
            assertNotNull(schema[NonLatinFieldNames().Βήτα])
            assertNotNull(schema[NonLatinFieldNames().ЙйКкЛл])
            assertNotNull(schema[NonLatinFieldNames().山水要])
            assertNotNull(schema[NonLatinFieldNames().ععسنملل])
            assertNotNull(schema[NonLatinFieldNames().`😊🙈`])
        }
    }

    @Test
    fun roundtripNonLatinValues() {
        val config = RealmConfiguration.Builder(setOf(NonLatinClassёжф::class))
            .directory(tmpDir)
            .build()

        Realm.open(config).use { realm ->
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
    }

    @Test
    fun roundTripProblematicUTF8Chars() {
        // Our UTF8 handling code optimizes short strings, so make some test strings longer than
        // the short buffer of 48 chars.
        val prefix = "abcdefghijklmnopqrstuvwxyz"
        val suffix = "abcdefghijklmnopqrstuvwxyz"

        val values = listOf(
            "\uC000",
            "\u0000",
            0xC0.toByte().toInt().toChar().toString(),
            0x00.toByte().toInt().toChar().toString(),
        )
        val config = RealmConfiguration.Builder(setOf(NonLatinClassёжф::class))
            .directory(tmpDir)
            .build()

        Realm.open(config).use { realm ->
            realm.writeBlocking {
                values.forEach { str ->
                    val obj = copyToRealm(
                        NonLatinClassёжф().apply {
                            prop = str
                        }
                    )
                    println("'$str' vs. '${obj.prop}'")
                    assertEquals(str, obj.prop)
                }
            }
        }
    }

    // \0 has special semantics both in C++ and in Java, so ensure we can roundtrip
    // it correctly.
    @Test
    fun roundtripNullCharacter() {
        val config = RealmConfiguration.Builder(setOf(NonLatinClassёжф::class))
            .directory(tmpDir)
            .build()

        Realm.open(config).use { realm ->
            val nullChar = "\u0000"
            val shortNullCharOddLength = "foo\u0000ba"
            val shortNullCharEvenLength = "foo\u0000bar"
            val mediumNullString = "abcdefghijklmnopqrstuvwxyz-\u0000-abcdefghijklmnopqrstuvwxyz"
            val obj = realm.writeBlocking {
                copyToRealm(
                    NonLatinClassёжф().apply {
                        prop = nullChar
                        list.addAll(listOf(nullChar, shortNullCharEvenLength, shortNullCharOddLength, mediumNullString))
                        nullList.addAll(listOf(nullChar, shortNullCharEvenLength, shortNullCharOddLength, mediumNullString))
                    }
                )
            }
            println("'$nullChar' vs '${obj.list[0]}'")
            println("'$shortNullCharEvenLength' vs '${obj.list[1]}'")
            println("'$shortNullCharOddLength' vs '${obj.list[2]}'")
            println("'$mediumNullString' vs '${obj.list[3]}'")

            assertEquals(nullChar, obj.prop)
            assertEquals(1, obj.prop.length)

            assertEquals(4, obj.list.size)
            assertEquals(nullChar, obj.list[0])
            assertEquals(shortNullCharEvenLength, obj.list[1])
            assertEquals(shortNullCharOddLength, obj.list[2])
            assertEquals(mediumNullString, obj.list[3])

            assertEquals(4, obj.nullList.size)
            assertEquals(nullChar, obj.nullList[0])
            assertEquals(shortNullCharEvenLength, obj.nullList[1])
            assertEquals(shortNullCharOddLength, obj.nullList[2])
            assertEquals(mediumNullString, obj.nullList[3])
        }
    }
}
