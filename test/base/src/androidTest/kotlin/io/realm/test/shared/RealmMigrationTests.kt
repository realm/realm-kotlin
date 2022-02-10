@file:Suppress("invisible_member", "invisible_reference")
/*
 * Copyright 2022 Realm Inc.
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

import io.realm.BaseRealm
import io.realm.RealmList
import io.realm.DynamicRealm
import io.realm.DynamicRealmObject
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.entities.Sample
import io.realm.entities.schema.SchemaVariations
import io.realm.get
import io.realm.internal.asDynamicRealm
import io.realm.query.RealmQuery
import io.realm.test.platform.PlatformUtils
import io.realm.test.util.use
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class RealmMigrationTests {

    private lateinit var tmpDir: String
//    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
    }

    @AfterTest
    fun tearDown() {
//        if (!realm.isClosed()) {
//            realm.close()
//        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun migration() {
        val configuration =
            RealmConfiguration.Builder(schema = setOf(SchemaVariations::class, Sample::class))
                .path("$tmpDir/default.realm").build()

        Realm.open(configuration).use {
            it.writeBlocking {
                copyToRealm(Sample()).stringField = "ASDF"
            }
        }

        val newConfiguration = RealmConfiguration.Builder(schema = setOf(io.realm.entities.migration.SampleMigrated::class))
            .schemaVersion(1)
//            .migration { oldRealm: UntypedRealm, newRealm: MutableRealm -> // , oldVersion, newVersion ->
            .migration { oldRealm: DynamicRealm, newRealm: BaseRealm -> // , oldVersion, newVersion ->
                println("Migration: ${oldRealm.version().version}->${newRealm.version().version}")
                println("Old schema: ${oldRealm.schema()}")
                println("New schema: ${newRealm.schema()}")

                val query: RealmQuery<DynamicRealmObject> = oldRealm.query(Sample::class.simpleName!!)
                val first: DynamicRealmObject? = query.first().find()
                assertNotNull(first)
                assertEquals("ASDF", first.get("stringField"))
//                first.set("stringfield")
//                assert
//                oldRealm.
                // BaseRealm does not have objects, so we need a dynamic API
                // oldRealm.objects<Sample>()
//                val newSample: SampleMigrated = newRealm.copyToRealm(SampleMigrated()).also { it.name = "MIGRATED" }
//                newRealm.findLatest(newSample)?.let {
//                    assertEquals("MIGRATED", it.name)
//                } ?: fail("Couldn't find new object")
            }
            .path("$tmpDir/default.realm").build()
        val newRealm = Realm.open(newConfiguration)
    }

    @Test
    fun dynamicRealm() {
        val configuration =
            RealmConfiguration.Builder(schema = setOf(SchemaVariations::class, Sample::class))
                .path("$tmpDir/default.realm").build()

        val realm = Realm.open(configuration)
        realm.writeBlocking {
            copyToRealm(Sample()).apply {
                stringField = "Parent"
                child = Sample().apply { stringField = "Child" }
                stringListField.add("STRINGLISTELEMENT")
                objectListField.add(Sample().apply { stringField = "SAMPLELISTELEMENT" })
            }
        }

        val dynamicRealm = realm.asDynamicRealm()

        val query: RealmQuery<DynamicRealmObject> = dynamicRealm.query(Sample::class.simpleName!!)
        val first: DynamicRealmObject? = query.first().find()
        assertNotNull(first)
        assertEquals("Parent", first.get("stringField"))

        //
        val dynamicChild: DynamicRealmObject? = first.get("child")
        assertNotNull(dynamicChild)
        assertEquals("Child", dynamicChild.get("stringField"))

        // string list
        // FIXME Doesn't verify type
        val stringList: RealmList<String>? = first.get("stringListField")
        assertEquals("STRINGLISTELEMENT", stringList!![0])

        val objectList: RealmList<DynamicRealmObject>? = first.get("objectListField")
        assertEquals("SAMPLELISTELEMENT", objectList!![0].get("stringField"))

        realm.close()

        // Something wrong with the types herre
        assertFailsWith<Exception> {
            first.get<DynamicRealmObject>("stringField")
        }
    }

    // Dynamic readable
    // - All types of objects get
    // - Type
    // - Fields
    //
    // Dynamic writeable
    // - Create object
    // - All types of objects set
    //
    // Migration
    // - schema version
}
