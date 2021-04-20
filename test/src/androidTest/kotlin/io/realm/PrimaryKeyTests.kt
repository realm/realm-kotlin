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

import test.primarykey.NoPrimaryKey
import test.primarykey.PrimaryKeyString
import test.primarykey.PrimaryKeyStringNullable
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

private val primaryKey = "PRIMARY_KEY"

class PrimaryKeyTests {

    lateinit var tmpDir: String
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = Utils.createTempDir()
        val configuration =
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
        realm.create(PrimaryKeyString::class, primaryKey)
        realm.commitTransaction()
        // Query
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
        val create = realm.create(PrimaryKeyString::class, primaryKey)
        assertFailsWith<RuntimeException> {
            // C-API semantics is currently to return any existing object if already present
            val create1 = realm.create(PrimaryKeyString::class, primaryKey)
        }
        create.primaryKey = "Y"
        println(create.primaryKey)
    }


    @Test
    fun primaryKeyForNonPrimaryKeyObjectThrows() {
        realm.beginTransaction()
        assertFailsWith<RuntimeException> {
            realm.create(NoPrimaryKey::class, primaryKey)
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
}
