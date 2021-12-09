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
import io.realm.entities.Sample
import io.realm.entities.link.Child
import io.realm.entities.link.Parent
import io.realm.query
import io.realm.query.find
import io.realm.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MigrationTests {

    lateinit var tmpDir: String

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
        val path = "$tmpDir/default.realm"
        RealmConfiguration.Builder(
            schema = setOf(Sample::class)
        ).path(path).build().also {
            Realm.open(it).run {
                writeBlocking {
                    copyToRealm(Sample().apply { stringField = "Kotlin!" })
                }
                close()
            }
        }

        RealmConfiguration.Builder(
            schema = setOf(Sample::class, Parent::class, Child::class)
        ).path(path).build().also {
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

                query<Sample>().count().find { countValue ->
                    assertEquals(1L, countValue)
                }
                close()
            }
        }
    }

    @Test
    fun automaticMigrationRemovingClasses() {
        val path = "$tmpDir/default.realm"

        RealmConfiguration.Builder(
            schema = setOf(Sample::class, Parent::class, Child::class)
        ).path(path).build().also {
            Realm.open(it).run {
                writeBlocking {
                    copyToRealm(Child().apply { name = "Kotlin!" })
                }
                close()
            }
        }

        RealmConfiguration.Builder(
            schema = setOf(Parent::class, Child::class)
        ).path(path).build().also {
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
    fun resetFileShouldNotDeleteWhenAddingClass() {
        val path = "$tmpDir/default.realm"
        RealmConfiguration.Builder(
            schema = setOf(Sample::class),
        ).path(path).build().also {
            Realm.open(it).run {
                writeBlocking {
                    copyToRealm(Sample().apply { stringField = "Kotlin!" })
                }
                close()
            }
        }

        RealmConfiguration.Builder(
            schema = setOf(Sample::class, Parent::class, Child::class),
        ).path(path)
            .deleteRealmIfMigrationNeeded()
            .build().also {
                Realm.open(it).run {
                    query<Sample>()
                        .first()
                        .find { sample ->
                            assertNotNull(sample)
                            assertEquals("Kotlin!", sample.stringField)
                        }
                    close()
                }
            }
    }

    @Test
    fun resetFileShouldNotDeleteWhenRemovingClass() {
        val path = "$tmpDir/default.realm"
        RealmConfiguration.Builder(
            schema = setOf(Sample::class, Parent::class, Child::class),
        ).path(path).build().also {
            Realm.open(it).run {
                writeBlocking {
                    copyToRealm(Child().apply { name = "Kotlin!" })
                }
                close()
            }
        }

        RealmConfiguration.Builder(
            schema = setOf(Parent::class, Child::class)
        ).path(path)
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

    // TODO add test for adding/remove columns when we have an API to open with an existing Realm.
    // https://github.com/realm/realm-kotlin/issues/304
}
