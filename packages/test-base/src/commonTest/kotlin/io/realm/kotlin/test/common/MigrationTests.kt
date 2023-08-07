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
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.entities.link.Child
import io.realm.kotlin.entities.link.Parent
import io.realm.kotlin.entities.migration.embedded.before.EmbeddedMigrationChild
import io.realm.kotlin.entities.migration.embedded.before.EmbeddedMigrationParent
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.migration.AutomaticSchemaMigration
import io.realm.kotlin.query.find
import io.realm.kotlin.test.common.utils.assertFailsWithMessage
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.use
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MigrationTests {

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
    fun automaticMigrationAddingNewClasses() {
        RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .build()
            .also {
                Realm.open(it)
                    .run {
                        writeBlocking {
                            copyToRealm(Sample().apply { stringField = "Kotlin!" })
                        }
                        close()
                    }
            }

        RealmConfiguration.Builder(schema = setOf(Sample::class, Parent::class, Child::class))
            .directory(tmpDir)
            .build()
            .also {
                Realm.open(it).run {
                    query<Sample>()
                        .first()
                        .find { sample ->
                            assertNotNull(sample)
                            assertEquals("Kotlin!", sample.stringField)
                        }
                    // make sure the added classes are available in the new schema
                    writeBlocking {
                        copyToRealm(Child())
                    }

                    query<Sample>()
                        .count()
                        .find { countValue ->
                            assertEquals(1L, countValue)
                        }
                    close()
                }
            }
    }

    @Test
    fun automaticMigrationRemovingClasses() {
        RealmConfiguration.Builder(schema = setOf(Sample::class, Parent::class, Child::class))
            .directory(tmpDir)
            .build()
            .also {
                Realm.open(it)
                    .run {
                        writeBlocking {
                            copyToRealm(Child().apply { name = "Kotlin!" })
                        }
                        close()
                    }
            }

        RealmConfiguration.Builder(schema = setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .build()
            .also {
                Realm.open(it).run {
                    query<Child>()
                        .first()
                        .find { child ->
                            assertNotNull(child)
                            assertEquals("Kotlin!", child.name)
                        }
                    close()
                }
            }
    }

    @Test
    fun resetFileShouldTriggerWhenAddingClass() {
        RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .build()
            .also {
                Realm.open(it).run {
                    writeBlocking {
                        copyToRealm(Sample().apply { stringField = "Kotlin!" })
                    }
                    close()
                }
            }

        RealmConfiguration.Builder(schema = setOf(Sample::class, Parent::class, Child::class))
            .directory(tmpDir)
            .deleteRealmIfMigrationNeeded()
            .build()
            .also {
                Realm.open(it).run {
                    query<Sample>()
                        .first()
                        .find { sample ->
                            assertNull(sample)
                        }
                    close()
                }
            }
    }

    @Test
    fun resetFileShouldNotDeleteWhenRemovingClass() {
        RealmConfiguration.Builder(schema = setOf(Sample::class, Parent::class, Child::class))
            .directory(tmpDir)
            .build()
            .also {
                Realm.open(it).run {
                    writeBlocking {
                        copyToRealm(Child().apply { name = "Kotlin!" })
                    }
                    close()
                }
            }

        RealmConfiguration.Builder(schema = setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .deleteRealmIfMigrationNeeded()
            .build()
            .also {
                Realm.open(it).run {
                    query<Child>()
                        .first()
                        .find { child ->
                            assertNotNull(child)
                            assertEquals("Kotlin!", child.name)
                        }
                    close()
                }
            }
    }

    @Test
    fun migrationThrowsOnViolatingEmbeddedObjectConstraints() = runBlocking<Unit> {
        val initialConfiguration = RealmConfiguration.Builder(
            schema = setOf(
                io.realm.kotlin.entities.migration.embedded.before.EmbeddedMigrationParent::class,
                io.realm.kotlin.entities.migration.embedded.before.EmbeddedMigrationChild::class
            )
        )
            .directory(tmpDir)
            .build()

        Realm.open(initialConfiguration).use {
            it.write {
                copyToRealm(EmbeddedMigrationChild().apply { id = "orphaned-child" })
            }
        }

        val migratedConfiguration = RealmConfiguration.Builder(
            schema = setOf(
                io.realm.kotlin.entities.migration.embedded.after.EmbeddedMigrationParent::class,
                io.realm.kotlin.entities.migration.embedded.after.EmbeddedMigrationChild::class,
            )
        )
            .directory(tmpDir)
            .schemaVersion(1)
            .migration(AutomaticSchemaMigration { })
            .build()

        assertFailsWithMessage<IllegalStateException>("Cannot convert 'EmbeddedMigrationChild' to embedded: at least one object has no incoming links and would be deleted.") {
            Realm.open(migratedConfiguration).use { }
        }
    }

    @Test
    fun automaticBacklinkHandling_deleteOrphanedChildren() = runBlocking {
        val initialConfiguration = RealmConfiguration.Builder(
            schema = setOf(
                io.realm.kotlin.entities.migration.embedded.before.EmbeddedMigrationParent::class,
                io.realm.kotlin.entities.migration.embedded.before.EmbeddedMigrationChild::class
            )
        )
            .directory(tmpDir)
            .build()

        Realm.open(initialConfiguration).use {
            it.write {
                copyToRealm(
                    EmbeddedMigrationParent().apply {
                        child = EmbeddedMigrationChild().apply { id = "child-with-parent" }
                    }
                )
                copyToRealm(EmbeddedMigrationChild().apply { id = "orphaned-child" })
            }
        }

        val migratedConfiguration = RealmConfiguration.Builder(
            schema = setOf(
                io.realm.kotlin.entities.migration.embedded.after.EmbeddedMigrationParent::class,
                io.realm.kotlin.entities.migration.embedded.after.EmbeddedMigrationChild::class,
            )
        )
            .directory(tmpDir)
            .schemaVersion(1)
            .migration(AutomaticSchemaMigration { }, resolveEmbeddedObjectConstraints = true)
            .build()

        Realm.open(migratedConfiguration).use {
            val childWithParent =
                it.query<io.realm.kotlin.entities.migration.embedded.after.EmbeddedMigrationChild>()
                    .find().single()
            assertEquals("child-with-parent", childWithParent.id)
        }
    }

    @Test
    fun automaticBacklinkHandling_cloneDuplicateReferences() = runBlocking {
        val initialConfiguration = RealmConfiguration.Builder(
            schema = setOf(
                io.realm.kotlin.entities.migration.embedded.before.EmbeddedMigrationParent::class,
                io.realm.kotlin.entities.migration.embedded.before.EmbeddedMigrationChild::class
            )
        )
            .directory(tmpDir)
            .build()

        Realm.open(
            initialConfiguration
        ).use {
            it.write {
                // Add two parents referencing the same child
                val child = copyToRealm(EmbeddedMigrationChild().apply { id = "child-with-parent" })
                copyToRealm(
                    EmbeddedMigrationParent().apply {
                        id = "mom1"
                        this.child = child
                    }
                )
                copyToRealm(
                    EmbeddedMigrationParent().apply {
                        id = "mom2"
                        this.child = child
                    }
                )
            }
        }

        val migratedConfiguration = RealmConfiguration.Builder(
            schema = setOf(
                io.realm.kotlin.entities.migration.embedded.after.EmbeddedMigrationParent::class,
                io.realm.kotlin.entities.migration.embedded.after.EmbeddedMigrationChild::class,
            )
        )
            .directory(tmpDir)
            .schemaVersion(1)
            .migration(AutomaticSchemaMigration { }, resolveEmbeddedObjectConstraints = true)
            .build()

        Realm.open(migratedConfiguration).use {
            assertEquals(
                2,
                it.query<io.realm.kotlin.entities.migration.embedded.after.EmbeddedMigrationChild>()
                    .find().count()
            )
        }
    }

    // TODO add test for adding/remove columns when we have an API to open with an existing Realm.
    // https://github.com/realm/realm-kotlin/issues/304
}
