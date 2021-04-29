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

package io.realm

import io.realm.util.PlatformUtils
import io.realm.util.TypeDescriptor.allPrimaryKeyFieldTypes
import io.realm.util.TypeDescriptor.rType
import io.realm.util.Utils
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
            RealmConfiguration.Builder(path = "$tmpDir/default.realm")
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
        realm.beginTransaction()
        realm.create(PrimaryKeyString::class, PRIMARY_KEY)
        realm.commitTransaction()

        assertEquals(PRIMARY_KEY, realm.objects(PrimaryKeyString::class)[0].primaryKey)
    }

    @Test
    fun nullPrimaryKey() {
        realm.beginTransaction()
        realm.create(PrimaryKeyStringNullable::class, null)
        realm.commitTransaction()

        assertNull(realm.objects(PrimaryKeyStringNullable::class)[0].primaryKey)
    }

    @Test
    fun missingPrimaryKeyThrows() {
        realm.beginTransaction()
        assertFailsWith<RuntimeException> {
            realm.create(PrimaryKeyString::class)
        }
        realm.rollbackTransaction()

        assertTrue(realm.objects(PrimaryKeyString::class).isEmpty())
    }

    @Test
    @Ignore // https://github.com/realm/realm-core/issues/4595
    fun duplicatePrimaryKeyThrows() {
        realm.beginTransaction()
        val create = realm.create(PrimaryKeyString::class, PRIMARY_KEY)
        assertFailsWith<RuntimeException> {
            // C-API semantics is currently to return any existing object if already present
            val create1 = realm.create(PrimaryKeyString::class, PRIMARY_KEY)
        }
        realm.rollbackTransaction()

        assertEquals(PRIMARY_KEY, realm.objects(PrimaryKeyString::class)[0].primaryKey)
    }

    @Test
    @Ignore // https://github.com/realm/realm-core/issues/4595
    fun duplicateNullPrimaryKeyThrows() {
        realm.beginTransaction()
        val create = realm.create(PrimaryKeyString::class, null)
        assertFailsWith<RuntimeException> {
            // C-API semantics is currently to return any existing object if already present
            val create1 = realm.create(PrimaryKeyString::class, null)
        }
        realm.rollbackTransaction()

        val objects = realm.objects(PrimaryKeyStringNullable::class)
        assertEquals(1, objects.size)
        assertNull(objects[0].primaryKey)
    }

    @Test
    fun primaryKeyForNonPrimaryKeyObjectThrows() {
        realm.beginTransaction()
        assertFailsWith<RuntimeException> {
            realm.create(NoPrimaryKey::class, PRIMARY_KEY)
        }
        realm.rollbackTransaction()

        assertTrue(realm.objects(NoPrimaryKey::class).isEmpty())
    }

    @Test
    fun primaryKeyWithWrongTypeThrows() {
        realm.beginTransaction()
        assertFailsWith<RuntimeException> {
            realm.create(PrimaryKeyString::class, 14)
        }
        realm.rollbackTransaction()

        assertTrue(realm.objects(PrimaryKeyString::class).isEmpty())
    }

    @Test
    fun importUnmanagedWithPrimaryKey() {
        val o = PrimaryKeyString().apply { primaryKey = PRIMARY_KEY }
        realm.beginTransaction()
        realm.copyToRealm(o)
        realm.commitTransaction()

        assertEquals(PRIMARY_KEY, realm.objects(PrimaryKeyString::class)[0].primaryKey)
    }

    @Test
    @Ignore // https://github.com/realm/realm-core/issues/4595
    fun importUnmanagedWithDuplicatePrimaryKeyThrows() {
        val o = PrimaryKeyString()
        realm.beginTransaction()
        realm.copyToRealm(o)
        assertFailsWith<RuntimeException> {
            realm.copyToRealm(o)
        }
        realm.commitTransaction()

        val objects = realm.objects(PrimaryKeyString::class)
        assertEquals(1, objects.size)
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
        val types = allPrimaryKeyFieldTypes.toMutableSet()

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

        val configuration = RealmConfiguration.Builder("$tmpDir/default.realm")
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

        val mediator = configuration.mediator

        val realm = Realm.open(configuration)

        realm.beginTransaction()
        for (c in classes) {
            // We could expose this through the test model definitions instead if that is better to avoid the internals
            val realmObjectCompanion = mediator.companionOf(c)
            realm.copyToRealm(realmObjectCompanion.`$realm$newInstance`() as RealmObject)
            val type = realmObjectCompanion.`$realm$primaryKey`!!.rType()
            assertTrue(types.remove(type), type.toString())
        }
        assertTrue(types.toTypedArray().isEmpty(), "Untested primary keys: $types")
    }
}
