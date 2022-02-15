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

import io.realm.AutomaticSchemaMigration
import io.realm.DynamicMutableRealm
import io.realm.DynamicMutableRealmObject
import io.realm.DynamicRealm
import io.realm.DynamicRealmObject
import io.realm.MutableRealm
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.component1
import io.realm.component2
import io.realm.delete
import io.realm.entities.Sample
import io.realm.entities.migration.SampleMigrated
import io.realm.entities.primarykey.PrimaryKeyString
import io.realm.entities.schema.SchemaVariations
import io.realm.enumerate
import io.realm.get
import io.realm.query
import io.realm.query.RealmQuery
import io.realm.test.platform.PlatformUtils
import io.realm.test.util.use
import kotlinx.atomicfu.atomic
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertIsNot
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun migration_smoketest() {
        val configuration =
            RealmConfiguration.Builder(schema = setOf(SchemaVariations::class, Sample::class))
                .path("$tmpDir/default.realm")
                .build()

        Realm.open(configuration).use {
            it.writeBlocking {
                copyToRealm(Sample()).stringField = "ASDF"
            }
        }

        val newConfiguration = RealmConfiguration.Builder(schema = setOf(SampleMigrated::class))
            .path("$tmpDir/default.realm")
            .schemaVersion(1)
            .migration(AutomaticSchemaMigration { (oldRealm, newRealm) ->
                // FIXME Don't have access to schema version :|/

                val query: RealmQuery<out DynamicRealmObject> = oldRealm.query(Sample::class.simpleName!!)
                val first: DynamicRealmObject? = query.first().find()
                assertNotNull(first)
                assertEquals("ASDF", first.get("stringField"))

                // FIXME Assert something on the query
                val newQuery: RealmQuery<DynamicMutableRealmObject> = newRealm.query(SampleMigrated::class.simpleName!!)

                // Create a new object
                val newObject = newRealm.createObject("SampleMigrated")
                assertEquals("", newObject.get("name"))

                newObject.set("name", "MIGRATEDVALUE")
                newObject.set("self", newObject)

                // FIXME Need primary key
            })
            .build()

        // Open realm and trigger migration
        val newRealm = Realm.open(newConfiguration)

        // Verify that migrated entries are accessible through the typed realm
        val first = newRealm.query<SampleMigrated>().find().first()
        assertEquals("MIGRATEDVALUE", first.name)
        assertEquals("MIGRATEDVALUE", first.self!!.name)
    }

    @Test
    fun schema() {
        migration(
            initialSchema = setOf(SchemaVariations::class, Sample::class),
            migratedSchema = setOf(SampleMigrated::class),
            migration = { (oldRealm, newRealm) ->
                assertIs<DynamicRealm>(oldRealm)
                assertIsNot<DynamicMutableRealm>(oldRealm)
                oldRealm.schema().let { oldSchema ->
                    assertEquals(0, oldRealm.schemaVersion())
                    assertEquals(2, oldSchema.classes.size)
                    assertNotNull(oldSchema["Sample"])
                    assertNotNull(oldSchema["SchemaVariations"])
                    assertNull(oldSchema["SampleMigrated"])
                }

                assertIs<DynamicRealm>(newRealm)
                assertIs<DynamicMutableRealm>(newRealm)
                newRealm.schema().let { newSchema ->
                    assertEquals(1, newRealm.schemaVersion())
                    assertEquals(1, newSchema.classes.size)
                    assertNull(newSchema["Sample"])
                    assertNull(newSchema["SchemaVariations"])
                    assertNotNull(newSchema["SampleMigrated"])
                }
            }
        )
    }

    // TODO Test all schema modifications (theoretically test core behavior, so postponed for now)
    //  - Keep existing class
    //  - Add class
    //  - Remove class
    //  - Modify class
    //    - Add property
    //    - Remove property
    //    - Rename (remove+add with different type)
    //    - Change property attributes (is this technically a remove and add?)
    //      - Nullability
    //      - Primary key
    //      - Index

    @Test
    fun create() {
        val value = "MIGRATED_NAME"
        migration(
            initialSchema = setOf(Sample::class),
            migratedSchema = setOf(SampleMigrated::class),
            migration = { (oldRealm, newRealm) -> newRealm.createObject("SampleMigrated").set("name", value) }
        ).use {
            assertEquals(value, it.query<SampleMigrated>().find().first().name)
        }
    }

    @Test
    fun createPrimaryKey() {
        val primaryKey = "PRIMARY_KEY"
        migration(
            initialSchema = setOf(Sample::class),
            migratedSchema = setOf(PrimaryKeyString::class),
            migration = { (oldRealm, newRealm) -> newRealm.createObject("PrimaryKeyString", primaryKey) }
        ).use {
            assertEquals(primaryKey, it.query<PrimaryKeyString>().find().first().primaryKey)
        }
    }

    @Test
    fun query() {
        migration(
            initialSchema = setOf(Sample::class),
            initialData = {
                for (i in 0..9) {
                    copyToRealm(Sample().apply { intField = i % 2 })
                }
            },
            migratedSchema = setOf(io.realm.entities.migration.Sample::class),
            // FIXME Can we get this to have the DataMigrationContext as receiver
            migration = {
                val oldSamples: RealmResults<out DynamicRealmObject> = it.oldRealm.query("Sample", "intField = 0").find()
                assertEquals(5, oldSamples.size)
                oldSamples.forEach { assertEquals(0, it.get<Long>("intField")) }

                val newSamples: RealmResults<out DynamicRealmObject> = it.newRealm.query("Sample", "intField = 0").find()
                assertEquals(5, newSamples.size)
                newSamples.forEach { assertEquals(0, it.get<Long>("intField")) }
            }
        ).close()
    }

    @Test
    fun create_unknownClassFails() {
        migration(
            initialSchema = setOf(Sample::class),
            migratedSchema = setOf(SampleMigrated::class),
            migration = { (oldRealm, newRealm) ->
                // FIXME Should be InvalidArgumentException
                assertFailsWith<java.lang.IllegalStateException> {
                    newRealm.createObject("Sample")
                }
            }
        ).close()
    }

    @Test
    fun enumerate() {
        val initialValue = "INITIAL_VALUE"
        val migratedValue = "MIGRATED_VALUE"
        migration(
            initialSchema = setOf(Sample::class),
            initialData = { copyToRealm(Sample().apply { stringField = initialValue }) },
            migratedSchema = setOf(io.realm.entities.migration.Sample::class),
            // FIXME Can we get this to have the DataMigrationContext as receiver
            migration = {
                it.enumerate("Sample") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject ->
                    assertEquals(initialValue, oldObject.get("stringField"))
                    assertEquals(initialValue, newObject.get("stringField"))
                    newObject.set("stringField", migratedValue)
                }
            }
        ).use {
            assertEquals(migratedValue, it.query<io.realm.entities.migration.Sample>().find().first().stringField)
        }
    }

    @Test
    fun enumerate_throwsOnInvalidName() {
        val initialValue = "INITIAL_VALUE"
        migration(
            initialSchema = setOf(Sample::class),
            initialData = { copyToRealm(Sample().apply { stringField = initialValue }) },
            migratedSchema = setOf(io.realm.entities.migration.Sample::class),
            // FIXME Can we get this to have the DataMigrationContext as receiver
            migration = {
                assertFailsWith<IllegalArgumentException> {
                    it.enumerate("NON_EXISTING_CLASS") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject ->
                    }
                }
            }
        ).close()
    }

    @Test
    fun delete() {
        migration(
            initialSchema = setOf(Sample::class),
            initialData = {
                for (i in 0..9) {
                    copyToRealm(Sample().apply { intField = i % 2 })
                }
            },
            migratedSchema = setOf(io.realm.entities.migration.Sample::class),
            // FIXME Can we get this to have the DataMigrationContext as receiver
            migration = {
                it.enumerate("Sample") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject ->
                    if (newObject.get<Long>("intField") == 0L) {
                        newObject.delete()
                    }
                }
            }
        ).use {
            val samples = it.query<io.realm.entities.migration.Sample>().find()
            assertEquals(5, samples.size)
            samples.forEach { assertEquals(1, it.intField) }
        }
    }

    @Test
    fun deleteAll() {
        migration(
            initialSchema = setOf(Sample::class),
            initialData = {
                for (i in 0..9) {
                    copyToRealm(Sample().apply { intField = i % 2 })
                }
            },
            migratedSchema = setOf(io.realm.entities.migration.Sample::class),
            // FIXME Can we get this to have the DataMigrationContext as receiver
            migration = {
                val samples = it.newRealm.query("Sample").find()
                assertEquals(10, samples.size)
                samples.delete()
            }
        ).use {
            val samples = it.query<io.realm.entities.migration.Sample>().find()
            assertEquals(0, samples.size)
        }
    }

    @Test
    fun migrationError_throwingCausesMigrationToFail() {
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .path("$tmpDir/default.realm")
            .build()
        Realm.open(configuration).close()

        val newConfiguration = RealmConfiguration.Builder(schema = setOf(io.realm.entities.migration.Sample::class))
            .path("$tmpDir/default.realm")
            .schemaVersion(1)
            .migration(AutomaticSchemaMigration {
                throw RuntimeException("User error")
            })
            .build()

        // FIXME Wrong exception
        assertFailsWith<IllegalArgumentException> {
            Realm.open(newConfiguration)
        }
    }

    @Test
    fun migrationError_throwsIfVersionIsNotUpdated() {
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .path("$tmpDir/default.realm")
            .build()
        Realm.open(configuration).close()

        val newConfiguration = RealmConfiguration.Builder(schema = setOf(io.realm.entities.migration.Sample::class))
            .path("$tmpDir/default.realm")
            .migration(AutomaticSchemaMigration {  })
            .build()

        assertFailsWith<java.lang.IllegalStateException> {
            Realm.open(newConfiguration)
        }
    }

    @Test
    fun migrationError_throwsOnDuplicatePrimaryKey() {
        val configuration = RealmConfiguration.Builder(schema = setOf(PrimaryKeyString::class))
            .path("$tmpDir/default.realm")
            .build()
        Realm.open(configuration).use {
            it.writeBlocking {
                copyToRealm(PrimaryKeyString().apply { primaryKey = "PRIMARY_KEY1" })
                copyToRealm(PrimaryKeyString().apply { primaryKey = "PRIMARY_KEY2" })
            }
        }

        val newConfiguration = RealmConfiguration.Builder(schema = setOf(Sample::class, PrimaryKeyString::class))
            .path("$tmpDir/default.realm")
            .schemaVersion(1)
            .migration(AutomaticSchemaMigration {
                it.enumerate("PrimaryKeyString") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject ->
                    newObject.set("primaryKey", "PRIMARY_KEY")
                }
            })
            .build()

        assertFailsWith<java.lang.IllegalStateException> {
            Realm.open(newConfiguration)
        }.let {
            it.message!!.contains("Primary key property 'class_PrimaryKeyString.primaryKey' has duplicate values after migration.")
        }
    }

    private fun migration(initialSchema: Set<KClass<out RealmObject>>, migratedSchema: Set<KClass<out RealmObject>>, migration: AutomaticSchemaMigration, initialData: MutableRealm.() -> Unit = {}): Realm {
        val migrated = atomic(false)
        val configuration =
            RealmConfiguration.Builder(schema = initialSchema)
                .path("$tmpDir/default.realm")
                .build()
        Realm.open(configuration).use {
            it.writeBlocking {
                initialData()
            }
        }

        val newConfiguration = RealmConfiguration.Builder(schema = migratedSchema)
            .path("$tmpDir/default.realm")
            .schemaVersion(1)
            .migration(AutomaticSchemaMigration {
                migration.migrate(it)
                migrated.value = true

            })
            .build()
        val migratedRealm = Realm.open(newConfiguration)
        assertTrue { migrated.value }
        return migratedRealm
    }
}
