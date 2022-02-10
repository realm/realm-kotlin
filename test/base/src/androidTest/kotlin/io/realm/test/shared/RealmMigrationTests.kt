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

import io.realm.*
import io.realm.entities.Sample
import io.realm.entities.migration.SampleMigrated
import io.realm.entities.schema.SchemaVariations
import io.realm.internal.asDynamicRealm
import io.realm.query.RealmQuery
import io.realm.test.platform.PlatformUtils
import io.realm.test.util.use
import kotlin.test.*

class RealmMigrationTests {

    private lateinit var tmpDir: String

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
    }

    @AfterTest
    fun tearDown() {
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

        val newConfiguration = RealmConfiguration.Builder(schema = setOf(SampleMigrated::class))
            .schemaVersion(1)
            .migration(AutomaticRealmMigration { context ->
                val oldRealm: DynamicRealm = context.oldRealm
                val newRealm: DynamicMutableRealm = context.newRealm

                println("Migration: ${oldRealm.version().version}->${newRealm.version().version}")
                println("Old schema: ${oldRealm.schema()}")
                println("New schema: ${newRealm.schema()}")
                // FIXME Don't have access to schema version :|/

                val query: RealmQuery<out DynamicRealmObject> = oldRealm.query(Sample::class.simpleName!!)
                val first: DynamicRealmObject? = query.first().find()
                assertNotNull(first)
                assertEquals("ASDF", first.get("stringField"))

                // FIXME Assert something on the query
                val newquery: RealmQuery<DynamicMutableRealmObject> = newRealm.query(SampleMigrated::class.simpleName!!)

                // Create a new object
                val newObject = newRealm.createObject("SampleMigrated")
                assertEquals("", newObject.get("name"))

                newObject.set("name", "MIGRATEDVALUE")
                newObject.set("self", newObject)

                // FIXME Need primary key
            })
            .path("$tmpDir/default.realm").build()

        // Open realm and trigger migration
        val newRealm = Realm.open(newConfiguration)

        // Verify that migrated entries are accessible through the typed realm
        val first = newRealm.query<SampleMigrated>().find().first()
        assertEquals("MIGRATEDVALUE", first.name)
        assertEquals("MIGRATEDVALUE", first.self!!.name)
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
                objectListField[0]
            }
        }

        val dynamicRealm = realm.asDynamicRealm()

        // dynamic object query
        val query: RealmQuery<out DynamicRealmObject> = dynamicRealm.query(Sample::class.simpleName!!)
        val first: DynamicRealmObject? = query.first().find()
        assertNotNull(first)

        // type
        assertEquals("Sample", first.type)

        // get string
        val actual = first.get("stringField", String::class)
        assertEquals("Parent", actual)

        // get object
        val dynamicChild: DynamicRealmObject? = first.get("child")
        assertNotNull(dynamicChild)
        assertEquals("Child", dynamicChild.get("stringField"))

        // string list
        // FIXME Doesn't verify generic type
        val stringList1: RealmList<String>? = first.get("stringListField")
        // FIXME Do we need separate getList method
//        val get: RealmList<String>? = first.getList("stringListField", String::class)
//        val stringList2: RealmList<String>? = get

        assertEquals("STRINGLISTELEMENT", stringList1!![0])
        // FIXME Is it acceptable that this is a mutable list?
        assertFailsWith<IllegalStateException> {
            stringList1.add("another element")
        }

        // object list
        val objectList: RealmList<DynamicRealmObject>? = first.get("objectListField")
        val dynamicRealmObject = objectList!![0]
        assertEquals("SAMPLELISTELEMENT", dynamicRealmObject.get("stringField"))

        // FIXME Is this the right exception?
        assertFailsWith<NotImplementedError> {
            dynamicRealmObject.observe()
        }

        realm.close()

        assertFailsWith<IllegalStateException> {
            first.get<DynamicRealmObject>("stringField")
        }.run {
            // FIXME Seems like message for accessing objects on closed realm is wrong
            assertTrue { message!!.contains("Cannot perform this operation on an invalid/deleted object") }
        }
    }

    // FIXME
    fun iterate() {
        // emurate
        // migrate
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
