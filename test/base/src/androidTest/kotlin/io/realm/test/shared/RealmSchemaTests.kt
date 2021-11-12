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

package io.realm.test.shared;

import io.realm.BaseRealm
import io.realm.Realm
import io.realm.DynamicRealm
import io.realm.schema.MutableRealmSchema
import io.realm.schema.MutableRealmProperty
import io.realm.schema.CollectionType
import io.realm.schema.ElementType
import io.realm.RealmConfiguration
import io.realm.entities.link.Child
import io.realm.entities.link.Parent
import io.realm.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

public class RealmSchemaTests {

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(schema = setOf(Parent::class, Child::class))
            .path("$tmpDir/default.realm").build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (!realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun schemaTest() {
        val schema = realm.schema()
    }

    @Test
    fun migration() {
        realm.close()
        val newConfiguration = RealmConfiguration.Builder(schema = setOf(io.realm.entities.migration.Child::class))
            .schemaVersion(1)
            .migration { oldRealm: DynamicRealm?, newRealm: DynamicRealm? -> // , oldVersion, newVersion ->
                println("Migration: ${oldRealm!!.version().version}->${newRealm!!.version().version}")
                println("Old schema: ${oldRealm!!.schema()}")
                println("New schema: ${newRealm!!.schema()}")
                val schema = MutableRealmSchema(oldRealm!!.schema())
                schema.classes.removeIf { it.name == "Parent" }
                schema.classes.first { it.name == "Child" }.let {
                    it.properties.add(MutableRealmProperty("SADF", CollectionType.NONE, ElementType.FieldType.STRING, false, false, false))
                }
                newRealm.schema(schema, newRealm.version().version)
            }
            .path("$tmpDir/default.realm").build()
        val newRealm = Realm.open(newConfiguration)
    }

    @Test
    fun migrationExamples() {
//        val oldSchema = realm.schema()
//        val schema: MutableRealmSchema = MutableRealmSchema(oldSchema)
//        // Add class
//        schema.classes.add(MutableRealmClass())
//        schema.classes.toSet()
//        // Remove class
//        schema.classes.removeIf { it.name == "ASDF" }
//        // Rename class (optimized as it shouldn't remove data)
//        schema.classes.find { it.name == "ASDF" }?.also { it.name = "asd" }
//        // Flip embedded
//        schema.classes.find { it.name == "ASDF"}?.also { it.embedded = !it.embedded }
//
//
//        // Add property
//        schema.classes.find { it.name == "SADF" }?.also {
//            it.properties.add(MutableRealmProperty())
//        }
//        // Remove property
//        schema.classes.find { it.name == "SADF" }?.also {
//            it.properties.removeIf { it.name == ""}
//        }
//        // Rename/Change property (optimized without removing it)
//        schema.classes.find { it.name == "SADF" }?.also {
//            it.properties.find{ it.name == ""}?.also { it.primaryKey = true}
//        }
//
//        val oldSchema = realm.schema()
//        val schema = MutableRealmSchema(oldSchema)
//        // Add class
//        schema.classes.add(MutableRealmClass())
//        // Remove class
//        schema.classes.removeIf { it.name == "ASDF" }
//        // Rename class
//        schema.classes.find { it.name == "ASDF" }?.also { it.name = "asd" }
//        // Flip embedded
//        schema.classes.find { it.name == "ASDF"}?.also { it.embedded = !it.embedded }
//
//
//        // Add property
//        schema.classes.find { it.name == "SADF" }?.also {
//            it.properties.add(MutableRealmProperty())
//        }
//        // Remove property
//        schema.classes.find { it.name == "SADF" }?.also {
//            it.properties.removeIf { it.name == ""}
//        }
//        // Rename/Change property
//        schema.classes.find { it.name == "SADF" }?.also {
//            it.properties.find{ it.name == ""}?.also { it.primaryKey = true}
//        }


    }
}
