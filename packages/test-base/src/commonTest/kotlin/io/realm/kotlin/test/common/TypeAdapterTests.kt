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
package io.realm.kotlin.test.common

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.adapters.AllTypes
import io.realm.kotlin.entities.adapters.RealmInstantBsonDateTimeAdapterInstanced
import io.realm.kotlin.entities.adapters.UsingInstancedAdapter
//import io.realm.kotlin.entities.adapters.UsingInstancedAdapter
import io.realm.kotlin.entities.adapters.UsingSingletonAdapter
import io.realm.kotlin.test.platform.PlatformUtils
import org.mongodb.kbson.BsonDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * This test suite should cover these points
 * Schema validation
 * Singleton / instanced adapters
 * All types including collections
 * Nullability
 * Default values
 * Compatibility with other annotations:
 * - PrimaryKey
 * - Index
 * - Fulltext search
 * - PersistedName
 * - Ignore
 * Backlinks
 * missing instance type adapters
 * copyFromRealm
 * access objects realm?
 */
class TypeAdapterTests {
    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    private lateinit var configuration: RealmConfiguration

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration.Builder(setOf(UsingSingletonAdapter::class, UsingInstancedAdapter::class, AllTypes::class))
            .directory(tmpDir)
            .typeAdapters {
                add(RealmInstantBsonDateTimeAdapterInstanced())
            }
            .build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun useSingletonAdapter() {
        val expectedDate = BsonDateTime()

        val unmanagedObject = UsingSingletonAdapter().apply {
            this.date = expectedDate
        }
        assertEquals(expectedDate, unmanagedObject.date)

        val managedObject = realm.writeBlocking {
            copyToRealm(unmanagedObject)
        }

        assertEquals(expectedDate, managedObject.date)
    }

    @Test
    fun useInstancedAdapter() {
        val expectedDate = BsonDateTime()

        val unmanagedObject = UsingInstancedAdapter().apply {
            this.date = expectedDate
        }
        assertEquals(expectedDate, unmanagedObject.date)

        val managedObject = realm.writeBlocking {
            copyToRealm(unmanagedObject)
        }

        assertEquals(expectedDate, managedObject.date)
    }

    @Test
    fun allTypes() {
        val unmanagedObject = AllTypes()

        val managedObject = realm.writeBlocking {
            copyToRealm(unmanagedObject)
        }

        assertEquals(unmanagedObject, managedObject)
    }

}
