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
package io.realm.shared

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmObject
import io.realm.test.platform.PlatformUtils
import io.realm.util.TypeDescriptor.allPrimaryKeyFieldTypes
import io.realm.util.TypeDescriptor.rType
import io.realm.util.Utils.createRandomString
import test.primarykey.NoPrimaryKey
import test.primarykey.PrimaryKeyByte
import test.primarykey.PrimaryKeyByteNullable
import test.primarykey.PrimaryKeyChar
import test.primarykey.PrimaryKeyCharNullable
import test.primarykey.PrimaryKeyInt
import test.primarykey.PrimaryKeyIntNullable
import test.primarykey.PrimaryKeyLong
import test.primarykey.PrimaryKeyLongNullable
import test.primarykey.PrimaryKeyShort
import test.primarykey.PrimaryKeyShortNullable
import test.primarykey.PrimaryKeyString
import test.primarykey.PrimaryKeyStringNullable
import kotlin.reflect.typeOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
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
            RealmConfiguration.Builder(path = "$tmpDir/${createRandomString(16)}.realm")
                .schema(
                    PrimaryKeyString::class,
                    PrimaryKeyStringNullable::class,
                    NoPrimaryKey::class
                )
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

        assertEquals(PRIMARY_KEY, realm.objects(PrimaryKeyString::class)[0].primaryKey)
    }

    @Test
    fun nullPrimaryKey() {
        realm.writeBlocking {
            copyToRealm(PrimaryKeyStringNullable().apply { primaryKey = null })
        }

        assertNull(realm.objects(PrimaryKeyStringNullable::class)[0].primaryKey)
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

        assertEquals(PRIMARY_KEY, realm.objects(PrimaryKeyString::class)[0].primaryKey)
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

        val objects = realm.objects(PrimaryKeyStringNullable::class)
        assertEquals(1, objects.size)
        assertNull(objects[0].primaryKey)
    }

    @Test
    // Maybe prevent updates of primary key fields completely by forcing it to be vals, but if it
    // is somehow possible (maybe from dynamic API), we should at least throw errors. Filed
    // https://github.com/realm/realm-core/issues/4808
    @Ignore
    fun updateWithDuplicatePrimaryKeyThrows() {
        realm.writeBlocking {
            val first = copyToRealm(PrimaryKeyString().apply { primaryKey = PRIMARY_KEY })
            val second = copyToRealm(PrimaryKeyString().apply { primaryKey = "Other key" })
            assertFailsWith<IllegalArgumentException> {
                second.primaryKey = PRIMARY_KEY
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
            typeOf<String?>(),
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

        val configuration = RealmConfiguration.Builder("$tmpDir/${createRandomString(16)}.realm")
            .schema(
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
            .build()

//        @Suppress("invisible_reference", "invisible_member")
        val mediator = (configuration as io.realm.internal.RealmConfigurationImpl).mediator

        val realm = Realm.open(configuration)

        realm.writeBlocking {
            val types = allPrimaryKeyFieldTypes.toMutableSet()
            for (c in classes) {
                // We could expose this through the test model definitions instead if that is better to avoid the internals
                val realmObjectCompanion = mediator.companionOf(c)
                copyToRealm(realmObjectCompanion.`$realm$newInstance`() as RealmObject)
                val type = realmObjectCompanion.`$realm$primaryKey`!!.rType()
                assertTrue(types.remove(type), type.toString())
            }
            assertTrue(types.toTypedArray().isEmpty(), "Untested primary keys: $types")
        }
    }
}
