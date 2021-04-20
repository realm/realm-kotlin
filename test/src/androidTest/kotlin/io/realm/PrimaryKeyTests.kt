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

import io.realm.util.allPrimaryKeyTypes
import io.realm.util.rType
import org.junit.Assert.assertArrayEquals
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
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private val PRIMARY_KEY = "PRIMARY_KEY"

class PrimaryKeyTests {

    private lateinit var tmpDir: String
    private lateinit var configuration: RealmConfiguration
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = Utils.createTempDir()
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
        Utils.deleteTempDir(tmpDir)
    }


    @Test
    fun string() {
        realm.beginTransaction()
        realm.create(PrimaryKeyString::class, PRIMARY_KEY)
        realm.commitTransaction()
        // Query
        assertEquals(PRIMARY_KEY, realm.objects(PrimaryKeyString::class)[0].primaryKey)
    }

    @Test
    fun nullPrimaryKey() {
        realm.beginTransaction()
        realm.create(PrimaryKeyStringNullable::class, null)
        realm.commitTransaction()
    }

    @Test
    fun missingPrimaryKeyThrows() {
        realm.beginTransaction()
        assertFailsWith<RuntimeException> {
            realm.create(PrimaryKeyString::class)
        }
    }

    @Test
    fun duplicatePrimaryKeyThrows() {
        realm.beginTransaction()
        val create = realm.create(PrimaryKeyString::class, PRIMARY_KEY)
        assertFailsWith<RuntimeException> {
            // C-API semantics is currently to return any existing object if already present
            val create1 = realm.create(PrimaryKeyString::class, PRIMARY_KEY)
        }
        create.primaryKey = "Y"
        println(create.primaryKey)
    }


    @Test
    fun primaryKeyForNonPrimaryKeyObjectThrows() {
        realm.beginTransaction()
        assertFailsWith<RuntimeException> {
            realm.create(NoPrimaryKey::class, PRIMARY_KEY)
        }
    }

    @Test
    fun primaryKeyWithWrongTypeThrows() {
        realm.beginTransaction()
        assertFailsWith<RuntimeException> {
            realm.create(PrimaryKeyString::class, 14)
        }
    }

    @Test
    fun importUnmanagedWithPrimaryKey() {
        val o = PrimaryKeyString()
        realm.beginTransaction()
        realm.copyToRealm(o)
        realm.commitTransaction()
    }

    @Test
    fun importUnmanagedWithDuplicatePrimaryKeyThrows() {
        val o = PrimaryKeyString()
        realm.beginTransaction()
        val copyToRealm = realm.copyToRealm(o)
        x(copyToRealm)
        assertFailsWith<RuntimeException> {
            realm.copyToRealm(o)
        }
        realm.commitTransaction()
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun <T: RealmObject> x(t: T) {
        val typeOf: KType = typeOf<PrimaryKeyString>()
        (typeOf as io.realm.internal.RealmObjectCompanion).`$realm$schema`()
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

        assertTrue(expectedTypes.containsAll(allPrimaryKeyTypes))
        expectedTypes.removeAll(allPrimaryKeyTypes)
        assertTrue(expectedTypes.isEmpty(), "$expectedTypes")
    }

    // - Test all types that supports primary keys can in fact be used
    @Test
    @Suppress("invisible_reference", "invisible_member")
    fun testAllTypes() {
        val types = allPrimaryKeyTypes.toMutableSet()

        // TODO Maybe we would only need to iterate underlying Realm types?
        val classes: Array<KClass<out RealmObject>> = arrayOf(
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

        val mediator =  configuration.mediator

        val realm = Realm.open(configuration)

        realm.beginTransaction()
        for (c in classes) {
            // We could expose this through the test model definitions instead if that is better to avoid the internals
            val realmObjectCompanion = mediator.companionOf(c)
            realm.copyToRealm(realmObjectCompanion.`$realm$newInstance`() as RealmObject)
            val type = realmObjectCompanion.`$realm$primaryKey`.rType()
            assertTrue(types.remove(type), type.toString())
        }
        assertArrayEquals("Untested primary keys: $types", types.toTypedArray(), emptyArray())
    }

    // TODO Test all types that cannot be supported raises compilation error. Probably fits better in
    //  compiler plugin test

}
