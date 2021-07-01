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
import io.realm.util.PlatformUtils
import test.Sample
import test.link.Child
import test.link.Parent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
            path = path,
            schema = setOf(Sample::class)
        ).build().also {
            Realm.open(it).run {
                writeBlocking {
                    copyToRealm(Sample().apply { stringField = "Kotlin!" })
                }
                close()
            }
        }

        RealmConfiguration.Builder(
            path = path,
            schema = setOf(Sample::class, Parent::class, Child::class)
        ).build().also {
            Realm.open(it).run {
                objects(Sample::class).first().run {
                    assertEquals("Kotlin!", stringField)
                }
                // make sure the added classes are available in the new schema
                writeBlocking {
                    copyToRealm(Child())
                }

                assertEquals(1, objects(Sample::class).count())
                close()
            }
        }
    }

    @Test
    fun automaticMigrationRemovingClasses() {
        val path = "$tmpDir/default.realm"

        RealmConfiguration(
            path = path,
            schema = setOf(Sample::class, Parent::class, Child::class)
        ).also {
            Realm.open(it).run {
                writeBlocking {
                    copyToRealm(Child().apply { name = "Kotlin!" })
                }
                close()
            }
        }

        RealmConfiguration(
            path = path,
            schema = setOf(Parent::class, Child::class)
        ).also {
            Realm.open(it).run {
                objects(Child::class).first().run {
                    assertEquals("Kotlin!", name)
                }
                close()
            }
        }
    }

    @Test
    fun resetFileShouldNotDeleteWhenAddingClass() {
        val path = "$tmpDir/default.realm"
        RealmConfiguration(
            path = path,
            schema = setOf(Sample::class),
        ).also {
            Realm.open(it).run {
                writeBlocking {
                    copyToRealm(Sample().apply { stringField = "Kotlin!" })
                }
                close()
            }
        }

        RealmConfiguration.Builder(
            path = path,
            schema = setOf(Sample::class, Parent::class, Child::class),
        ).deleteRealmIfMigrationNeeded()
            .build().also {
                Realm.open(it).run {
                    objects(Sample::class).first().run {
                        assertEquals("Kotlin!", stringField)
                    }
                    close()
                }
            }
    }

    @Test
    fun resetFileShouldNotDeleteWhenRemovingClass() {
        val path = "$tmpDir/default.realm"
        RealmConfiguration(
            path = path,
            schema = setOf(Sample::class, Parent::class, Child::class),
        ).also {
            Realm.open(it).run {
                writeBlocking {
                    copyToRealm(Child().apply { name = "Kotlin!" })
                }
                close()
            }
        }

        RealmConfiguration.Builder(
            path = path,
            schema = setOf(Parent::class, Child::class)
        ).deleteRealmIfMigrationNeeded()
            .build()
            .also {
                Realm.open(it).run {
                    objects(Child::class).first().run {
                        assertEquals("Kotlin!", name)
                    }
                    close()
                }
            }
    }

    // TODO add test for adding/remove columns when we have an API to open with an existing Realm.
    // https://github.com/realm/realm-kotlin/issues/304
}
