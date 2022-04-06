/*
 * Copyright 2021 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.realm.test.shared

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmObject
import io.realm.entities.primarykey.NoPrimaryKey
import io.realm.entities.primarykey.PrimaryKeyByte
import io.realm.entities.primarykey.PrimaryKeyByteNullable
import io.realm.entities.primarykey.PrimaryKeyChar
import io.realm.entities.primarykey.PrimaryKeyCharNullable
import io.realm.entities.primarykey.PrimaryKeyInt
import io.realm.entities.primarykey.PrimaryKeyIntNullable
import io.realm.entities.primarykey.PrimaryKeyLong
import io.realm.entities.primarykey.PrimaryKeyLongNullable
import io.realm.entities.primarykey.PrimaryKeyShort
import io.realm.entities.primarykey.PrimaryKeyShortNullable
import io.realm.entities.primarykey.PrimaryKeyString
import io.realm.entities.primarykey.PrimaryKeyStringNullable
import io.realm.query
import io.realm.query.find
import io.realm.test.assertFailsWithMessage
import io.realm.test.platform.PlatformUtils
import io.realm.test.util.TypeDescriptor.allPrimaryKeyFieldTypes
import io.realm.test.util.TypeDescriptor.rType
import kotlin.reflect.typeOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PRIMARY_KEY = "PRIMARY_KEY"

class PrimaryKeyTests {

    private lateinit var tmpDir: String
    private lateinit var configuration: RealmConfiguration
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration =
            RealmConfiguration.Builder(
                setOf(
                    PrimaryKeyString::class,
                    PrimaryKeyStringNullable::class,
                    NoPrimaryKey::class

                )
            )
                .directory(tmpDir)
                .build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun string() {
        realm.writeBlocking {
            copyToRealm(PrimaryKeyString().apply { primaryKey = PRIMARY_KEY })
        }

        realm.query<PrimaryKeyString>()
            .find { results ->
                assertEquals(PRIMARY_KEY, results[0].primaryKey)
            }
    }

    @Test
    fun nullPrimaryKey() {
        realm.writeBlocking {
            copyToRealm(PrimaryKeyStringNullable().apply { primaryKey = null })
        }

        realm.query<PrimaryKeyStringNullable>()
            .find { results ->
                assertNull(results[0].primaryKey)
            }
    }

    @Test
    fun duplicatePrimaryKeyThrows() {
        realm.writeBlocking {
            val obj = PrimaryKeyString().apply { primaryKey = PRIMARY_KEY }
            copyToRealm(obj)
            assertFailsWith<IllegalArgumentException> {
                copyToRealm(obj)
            }
        }

        realm.query<PrimaryKeyString>()
            .find { results ->
                assertEquals(PRIMARY_KEY, results[0].primaryKey)
            }
    }

    @Test
    fun duplicateNullPrimaryKeyThrows() {
        realm.writeBlocking {
            val obj = PrimaryKeyStringNullable().apply { primaryKey = null }
            copyToRealm(obj)
            assertFailsWith<IllegalArgumentException> {
                copyToRealm(obj)
            }
        }

        realm.query<PrimaryKeyStringNullable>()
            .find { results ->
                assertEquals(1, results.size)
                assertNull(results[0].primaryKey)
            }
    }

    @Test
    fun updateWithDuplicatePrimaryKeyThrows() {
        realm.writeBlocking {
            copyToRealm(PrimaryKeyString().apply { primaryKey = PRIMARY_KEY }).run {
                assertFailsWithMessage<IllegalArgumentException>("Cannot update primary key property 'PrimaryKeyString.primaryKey'") {
                    primaryKey = PRIMARY_KEY
                }
            }
        }
    }

    @Test
    @OptIn(ExperimentalStdlibApi::class)
    fun verifyPrimaryKeyTypeSupport() {
        val expectedTypes = setOf(
            typeOf<Byte>(),
            typeOf<Byte?>(),
            typeOf<Char>(),
            typeOf<Char?>(),
            typeOf<Short>(),
            typeOf<Short?>(),
            typeOf<Int>(),
            typeOf<Int?>(),
            typeOf<Long>(),
            typeOf<Long?>(),
            typeOf<String>(),
            typeOf<String?>()
        ).map { it.rType() }.toMutableSet()

        assertTrue(expectedTypes.containsAll(allPrimaryKeyFieldTypes))
        expectedTypes.removeAll(allPrimaryKeyFieldTypes)
        assertTrue(expectedTypes.isEmpty(), "$expectedTypes")
    }

    @Test
    @Suppress("invisible_reference", "invisible_member")
    fun testPrimaryKeyForAllSupportedTypes() {

        // TODO Maybe we would only need to iterate underlying Realm types?
        val classes = arrayOf(
            PrimaryKeyByte::class,
            PrimaryKeyByteNullable::class,
            PrimaryKeyChar::class,
            PrimaryKeyCharNullable::class,
            PrimaryKeyShort::class,
            PrimaryKeyShortNullable::class,
            PrimaryKeyInt::class,
            PrimaryKeyIntNullable::class,
            PrimaryKeyLong::class,
            PrimaryKeyLongNullable::class,
            PrimaryKeyString::class,
            PrimaryKeyStringNullable::class,
        )

        val configuration = RealmConfiguration.Builder(
            setOf(
                PrimaryKeyByte::class,
                PrimaryKeyByteNullable::class,
                PrimaryKeyChar::class,
                PrimaryKeyCharNullable::class,
                PrimaryKeyShort::class,
                PrimaryKeyShortNullable::class,
                PrimaryKeyInt::class,
                PrimaryKeyIntNullable::class,
                PrimaryKeyLong::class,
                PrimaryKeyLongNullable::class,
                PrimaryKeyString::class,
                PrimaryKeyStringNullable::class,
            )
        )
            .directory(tmpDir)
            .build()

//        @Suppress("invisible_reference", "invisible_member")
        val mediator = (configuration as io.realm.internal.RealmConfigurationImpl).mediator

        val realm = Realm.open(configuration)

        realm.writeBlocking {
            val types = allPrimaryKeyFieldTypes.toMutableSet()
            for (c in classes) {
                // We could expose this through the test model definitions instead if that is better to avoid the internals
                val realmObjectCompanion = mediator.companionOf(c)
                copyToRealm(realmObjectCompanion.`io_realm_kotlin_newInstance`() as RealmObject)
                val type = realmObjectCompanion.`io_realm_kotlin_primaryKey`!!.rType()
                assertTrue(types.remove(type), type.toString())
            }
            assertTrue(types.toTypedArray().isEmpty(), "Untested primary keys: $types")
        }
    }
}
