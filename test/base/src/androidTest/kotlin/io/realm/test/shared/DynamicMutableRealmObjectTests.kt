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

import io.realm.DynamicMutableRealm
import io.realm.DynamicMutableRealmObject
import io.realm.DynamicRealmObject
import io.realm.RealmConfiguration
import io.realm.RealmInstant
import io.realm.entities.Sample
import io.realm.entities.migration.SampleMigrated
import io.realm.entities.primarykey.PrimaryKeyString
import io.realm.entities.primarykey.PrimaryKeyStringNullable
import io.realm.get
import io.realm.getList
import io.realm.getListOfNullable
import io.realm.getNullable
import io.realm.internal.InternalConfiguration
import io.realm.schema.ListPropertyType
import io.realm.schema.RealmStorageType
import io.realm.schema.ValuePropertyType
import io.realm.test.DynamicMutableTransactionRealm
import io.realm.test.platform.PlatformUtils
import kotlinx.coroutines.test.runTest
import org.junit.Ignore
import org.junit.Test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class DynamicMutableRealmObjectTests {

    private lateinit var tmpDir: String
    private lateinit var configuration: RealmConfiguration
    private lateinit var dynamicMutableRealm: DynamicMutableRealm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration.Builder(schema = setOf(Sample::class, SampleMigrated::class, PrimaryKeyString::class, PrimaryKeyStringNullable::class))
            .path("$tmpDir/default.realm").build()

        dynamicMutableRealm = DynamicMutableTransactionRealm(configuration as InternalConfiguration).apply {
            beginTransaction()
        }
    }

    @AfterTest
    fun tearDown() {
        if (this::dynamicMutableRealm.isInitialized && !dynamicMutableRealm.isClosed()) {
            (dynamicMutableRealm as DynamicMutableTransactionRealm).close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun set_allTypes() = runTest {
        val dynamicSample = dynamicMutableRealm.createObject("Sample")
        assertNotNull(dynamicSample)

        val schema = dynamicMutableRealm.schema()
        val sampleDescriptor = schema["Sample"]!!

        val properties = sampleDescriptor.properties
        for (property in properties) {
            val name: String = property.name
            val type = property.type
            when (type) {
                is ValuePropertyType -> {
                    if (type.isNullable) {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                dynamicSample.set(name, true)
                                assertEquals(true, dynamicSample.getNullable(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullable<Boolean>(name))
                            }
                            RealmStorageType.INT -> {
                                dynamicSample.set(name, 42L)
                                assertEquals(42L, dynamicSample.getNullable(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullable<Long>(name))
                            }
                            RealmStorageType.STRING -> {
                                dynamicSample.set(name, "STRING")
                                assertEquals("STRING", dynamicSample.getNullable(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullable<String>(name))
                            }
                            RealmStorageType.OBJECT -> {
                                dynamicSample.set(name, Sample())
                                val nullableObject = dynamicSample.getNullable<DynamicRealmObject>(name)
                                assertNotNull(nullableObject)
                                assertEquals("Realm", nullableObject.get("stringField"))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullable<DynamicMutableRealmObject>(name))
                            }
                            RealmStorageType.FLOAT -> {
                                dynamicSample.set(name, 4.2f)
                                assertEquals(4.2f, dynamicSample.getNullable(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullable<Float>(name))
                            }
                            RealmStorageType.DOUBLE -> {
                                dynamicSample.set(name, 4.2)
                                assertEquals(4.2, dynamicSample.getNullable(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullable<Double>(name))
                            }
                            RealmStorageType.TIMESTAMP -> {
                                val value = RealmInstant.fromEpochSeconds(100, 100)
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.getNullable(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullable<RealmInstant>(name))
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                dynamicSample.set(name, true)
                                assertEquals(true, dynamicSample.getNullable(name))
                            }
                            RealmStorageType.INT -> {
                                dynamicSample.set(name, 42L)
                                assertEquals(42L, dynamicSample.getNullable(name))
                            }
                            RealmStorageType.STRING -> {
                                dynamicSample.set(name, "STRING")
                                assertEquals("STRING", dynamicSample.getNullable(name))
                            }
                            RealmStorageType.FLOAT -> {
                                dynamicSample.set(name, 4.2f)
                                assertEquals(4.2f, dynamicSample.getNullable(name))
                            }
                            RealmStorageType.DOUBLE -> {
                                dynamicSample.set(name, 4.2)
                                assertEquals(4.2, dynamicSample.getNullable(name))
                            }
                            RealmStorageType.TIMESTAMP -> {
                                val value = RealmInstant.fromEpochSeconds(100, 100)
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.getNullable(name))
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    }
                }
                is ListPropertyType -> {
                    if (type.isNullable) {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                val value = true
                                dynamicSample.getListOfNullable<Boolean>(property.name).add(value)
                                dynamicSample.getListOfNullable<Boolean>(property.name).add(null)
                                val listOfNullable = dynamicSample.getListOfNullable(property.name, Boolean::class)
                                assertEquals(value, listOfNullable[0])
                                assertEquals(null, listOfNullable[1])
                            }
                            RealmStorageType.INT -> {
                                val value: Long = when (property.name) {
                                    "nullableByteListField" -> defaultSample.byteField.toLong()
                                    "nullableCharListField" -> defaultSample.charField.code.toLong()
                                    "nullableShortListField" -> defaultSample.shortField.toLong()
                                    "nullableIntListField" -> defaultSample.intField.toLong()
                                    "nullableLongListField" -> defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                dynamicSample.getListOfNullable<Long>(property.name).add(value)
                                dynamicSample.getListOfNullable<Long>(property.name).add(null)
                                val listOfNullable = dynamicSample.getListOfNullable(property.name, Long::class)
                                assertEquals(value, listOfNullable[0])
                                assertEquals(null, listOfNullable[1])
                            }
                            RealmStorageType.STRING -> {
                                val value = "NEW_ELEMENT"
                                dynamicSample.getListOfNullable<String>(property.name).add(value)
                                dynamicSample.getListOfNullable<String>(property.name).add(null)
                                val listOfNullable = dynamicSample.getListOfNullable(property.name, String::class)
                                assertEquals(value, listOfNullable[0])
                                assertEquals(null, listOfNullable[1])
                            }
                            RealmStorageType.FLOAT -> {
                                val value = 1.234f
                                dynamicSample.getListOfNullable<Float>(property.name).add(value)
                                dynamicSample.getListOfNullable<Float>(property.name).add(null)
                                val listOfNullable = dynamicSample.getListOfNullable(property.name, Float::class)
                                assertEquals(value, listOfNullable[0])
                                assertEquals(null, listOfNullable[1])
                            }
                            RealmStorageType.DOUBLE -> {
                                val value = 1.234
                                dynamicSample.getListOfNullable<Double>(property.name).add(value)
                                dynamicSample.getListOfNullable<Double>(property.name).add(null)
                                val listOfNullable = dynamicSample.getListOfNullable(property.name, Double::class)
                                assertEquals(value, listOfNullable[0])
                                assertEquals(null, listOfNullable[1])
                            }
                            RealmStorageType.TIMESTAMP -> {
                                val value = RealmInstant.fromEpochSeconds(100, 100)
                                dynamicSample.getListOfNullable<RealmInstant>(property.name).add(value)
                                dynamicSample.getListOfNullable<RealmInstant>(property.name).add(null)
                                val listOfNullable = dynamicSample.getListOfNullable(property.name, RealmInstant::class)
                                assertEquals(value, listOfNullable[0])
                                assertEquals(null, listOfNullable[1])
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                val value = true
                                dynamicSample.getList<Boolean>(property.name).add(value)
                                assertEquals(value, dynamicSample.getList(property.name, Boolean::class)[0])
                            }
                            RealmStorageType.INT -> {
                                val value: Long = when (property.name) {
                                    "byteListField" -> defaultSample.byteField.toLong()
                                    "charListField" -> defaultSample.charField.code.toLong()
                                    "shortListField" -> defaultSample.shortField.toLong()
                                    "intListField" -> defaultSample.intField.toLong()
                                    "longListField" -> defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                dynamicSample.getList<Long>(property.name).add(value)
                                assertEquals(value, dynamicSample.getList(property.name, Long::class)[0])
                            }
                            RealmStorageType.STRING -> {
                                val value = "NEW_ELEMENT"
                                dynamicSample.getList<String>(property.name).add(value)
                                assertEquals(value, dynamicSample.getList(property.name, String::class)[0])
                            }
                            RealmStorageType.FLOAT -> {
                                val value = 1.234f
                                dynamicSample.getList<Float>(property.name).add(value)
                                assertEquals(value, dynamicSample.getList(property.name, Float::class)[0])
                            }
                            RealmStorageType.DOUBLE -> {
                                val value = 1.234
                                dynamicSample.getList<Double>(property.name).add(value)
                                assertEquals(value, dynamicSample.getList(property.name, Double::class)[0])
                            }
                            RealmStorageType.TIMESTAMP -> {
                                val value = RealmInstant.fromEpochSeconds(100, 100)
                                dynamicSample.getList<RealmInstant>(property.name).add(value)
                                assertEquals(value, dynamicSample.getList(property.name, RealmInstant::class)[0])
                            }
                            RealmStorageType.OBJECT -> {
                                val value = dynamicMutableRealm.createObject("Sample").set("stringField",  "NEW_OBJECT" )
                                dynamicSample.getList<DynamicRealmObject>(property.name).add(value)
                                assertEquals("NEW_OBJECT", dynamicSample.getList(property.name, DynamicRealmObject::class)[0].get("stringField"))
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    }
                }
            }
            // FIXME There is currently nothing that assert that we have tested all type
            // assertTrue("Untested types: $untested") { untested.isEmpty() }
        }
    }

    @Test
    fun get_returnsDynamicMutableObject() {
        val parent = dynamicMutableRealm.createObject("Sample")
            .set("stringField", "PARENT")
            .set("nullableObject", dynamicMutableRealm.createObject("Sample")
                .set("stringField", "CHILD")
            )
        val child: DynamicMutableRealmObject = parent.get("nullableObject")
        assertNotNull(child)
        child.set("stringField", "UPDATED_CHILD")
    }

    @Test
    fun set_throwsWithWrongType() {
        val sample = dynamicMutableRealm.createObject("Sample")
        assertFailsWith<IllegalArgumentException> {
            sample.set("stringField", 42)
        }.run {
            assertEquals("Property `Sample.stringField` cannot be assigned with value '42' of wrong type", message)
        }
    }

    @Test
    fun set_throwsOnNullForRequiredField() {
        val o = dynamicMutableRealm.createObject("Sample")
        assertFailsWith<IllegalArgumentException> {
            o.set("stringField", null)
        }.run {
            assertEquals("Required property `Sample.stringField` cannot be null", message)
        }
    }

    @Test
    @Ignore // Guard not implemented yet https://github.com/realm/realm-kotlin/issues/353
    fun set_throwsOnPrimaryKeyUpdate() {
        val o = dynamicMutableRealm.createObject("PrimaryKeyString", "PRIMARY_KEY")
        assertFailsWith<IllegalArgumentException> {
            o.set("primaryKey", "UPDATED_PRIMARY_KEY")
        }.run {
            assertEquals("Required property `Sample.stringField` cannot be null", message)
        }
    }
}
