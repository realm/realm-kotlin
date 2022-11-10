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

@file:Suppress("invisible_member", "invisible_reference")

package io.realm.kotlin.test.shared.dynamic

import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.dynamic.DynamicMutableRealm
import io.realm.kotlin.dynamic.DynamicMutableRealmObject
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.dynamic.getNullableValue
import io.realm.kotlin.dynamic.getNullableValueList
import io.realm.kotlin.dynamic.getNullableValueSet
import io.realm.kotlin.dynamic.getValue
import io.realm.kotlin.dynamic.getValueList
import io.realm.kotlin.dynamic.getValueSet
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.entities.embedded.embeddedSchema
import io.realm.kotlin.entities.embedded.embeddedSchemaWithPrimaryKey
import io.realm.kotlin.entities.primarykey.PrimaryKeyString
import io.realm.kotlin.entities.primarykey.PrimaryKeyStringNullable
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.internal.InternalConfiguration
import io.realm.kotlin.schema.ListPropertyType
import io.realm.kotlin.schema.RealmClass
import io.realm.kotlin.schema.RealmProperty
import io.realm.kotlin.schema.RealmPropertyType
import io.realm.kotlin.schema.RealmSchema
import io.realm.kotlin.schema.RealmStorageType
import io.realm.kotlin.schema.SetPropertyType
import io.realm.kotlin.schema.ValuePropertyType
import io.realm.kotlin.test.StandaloneDynamicMutableRealm
import io.realm.kotlin.test.assertFailsWithMessage
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmUUID
import kotlinx.coroutines.test.runTest
import org.mongodb.kbson.BsonObjectId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Suppress("LargeClass")
class DynamicMutableRealmObjectTests {

    private lateinit var tmpDir: String
    private lateinit var configuration: RealmConfiguration
    private lateinit var dynamicMutableRealm: DynamicMutableRealm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration.Builder(
            schema = setOf(
                Sample::class,
                PrimaryKeyString::class,
                PrimaryKeyStringNullable::class
            ) + embeddedSchema + embeddedSchemaWithPrimaryKey
        )
            .directory(tmpDir)
            .build()

        // We use a StandaloneDynamicMutableRealm that allows us to manage the write transaction
        // which is not possible on the public DynamicMutableRealm.
        dynamicMutableRealm =
            StandaloneDynamicMutableRealm(configuration as InternalConfiguration).apply {
                beginTransaction()
            }
    }

    @AfterTest
    fun tearDown() {
        if (this::dynamicMutableRealm.isInitialized && !dynamicMutableRealm.isClosed()) {
            (dynamicMutableRealm as StandaloneDynamicMutableRealm).close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun get_returnsDynamicMutableObject() {
        val parent = dynamicMutableRealm.copyToRealm(
            DynamicMutableRealmObject.create(
                "Sample",
                "stringField" to "PARENT",
                "nullableObject" to DynamicMutableRealmObject.create(
                    "Sample",
                    "stringField" to "CHILD"
                )
            )
        )
        assertTrue(parent.isManaged())
        val child: DynamicMutableRealmObject? = parent.getObject("nullableObject")
        assertNotNull(child)
        assertTrue(child.isManaged())
        child.set("stringField", "UPDATED_CHILD")
    }

    @Test
    fun create_fromMap() {
        val parent = dynamicMutableRealm.copyToRealm(
            DynamicMutableRealmObject.create(
                "Sample",
                mapOf(
                    "stringField" to "PARENT",
                    "nullableObject" to DynamicMutableRealmObject.create(
                        "Sample",
                        mapOf("stringField" to "CHILD")
                    )
                )
            )
        )
        parent.run {
            assertEquals("PARENT", getValue("stringField"))
            val child: DynamicMutableRealmObject? = parent.getObject("nullableObject")
            assertNotNull(child)
            assertTrue(child.isManaged())
            assertEquals("CHILD", child.getValue("stringField"))
        }
    }

    @Test
    @Suppress("LongMethod", "ComplexMethod")
    fun set_allTypes() = runTest {
        val dynamicSample: DynamicMutableRealmObject =
            dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        assertNotNull(dynamicSample)

        val schema: RealmSchema = dynamicMutableRealm.schema()
        val sampleDescriptor: RealmClass = schema["Sample"]!!

        val properties: Collection<RealmProperty> = sampleDescriptor.properties
        for (property: RealmProperty in properties) {
            val name: String = property.name
            val type: RealmPropertyType = property.type
            when (type) {
                is ValuePropertyType -> {
                    if (type.isNullable) {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                dynamicSample.set(name, true)
                                assertEquals(true, dynamicSample.getNullableValue(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullableValue<Boolean>(name))
                            }
                            RealmStorageType.INT -> {
                                dynamicSample.set(name, 42L)
                                assertEquals(42L, dynamicSample.getNullableValue(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullableValue<Long>(name))
                            }
                            RealmStorageType.STRING -> {
                                dynamicSample.set(name, "STRING")
                                assertEquals("STRING", dynamicSample.getNullableValue(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullableValue<String>(name))
                            }
                            RealmStorageType.OBJECT -> {
                                dynamicSample.set(name, Sample())
                                val nullableObject = dynamicSample.getObject(name)
                                assertNotNull(nullableObject)
                                assertEquals("Realm", nullableObject.getValue("stringField"))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getObject(name))
                            }
                            RealmStorageType.FLOAT -> {
                                dynamicSample.set(name, 4.2f)
                                assertEquals(4.2f, dynamicSample.getNullableValue(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullableValue<Float>(name))
                            }
                            RealmStorageType.DOUBLE -> {
                                dynamicSample.set(name, 4.2)
                                assertEquals(4.2, dynamicSample.getNullableValue(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullableValue<Double>(name))
                            }
                            RealmStorageType.TIMESTAMP -> {
                                val value = RealmInstant.from(100, 100)
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.getNullableValue(name))
                                dynamicSample.set(name, null)
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue<RealmInstant>(name)
                                )
                            }
                            RealmStorageType.OBJECT_ID -> {
                                when (name) {
                                    Sample::nullableObjectIdField.name -> {
                                        val value = ObjectId.create()
                                        dynamicSample.set(name, value)
                                        assertEquals(value, dynamicSample.getNullableValue(name))
                                        dynamicSample.set(name, null)
                                        assertEquals(
                                            null,
                                            dynamicSample.getNullableValue<ObjectId>(name)
                                        )
                                    }
                                    Sample::nullableBsonObjectIdField.name -> {
                                        val value = BsonObjectId()
                                        dynamicSample.set(name, value)
                                        assertEquals(value, dynamicSample.getNullableValue(name))
                                        dynamicSample.set(name, null)
                                        assertEquals(
                                            null,
                                            dynamicSample.getNullableValue<ObjectId>(name)
                                        )
                                    }
                                }
                            }
                            RealmStorageType.UUID -> {
                                val value = RealmUUID.random()
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.getNullableValue(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullableValue<RealmUUID>(name))
                            }
                            RealmStorageType.BINARY -> {
                                val value = byteArrayOf(42)
                                dynamicSample.set(name, value)
                                assertContentEquals(value, dynamicSample.getNullableValue(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullableValue<ByteArray>(name))
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                dynamicSample.set(name, true)
                                assertEquals(true, dynamicSample.getValue(name))
                            }
                            RealmStorageType.INT -> {
                                dynamicSample.set(name, 42L)
                                assertEquals(42L, dynamicSample.getValue(name))
                            }
                            RealmStorageType.STRING -> {
                                dynamicSample.set(name, "STRING")
                                assertEquals("STRING", dynamicSample.getValue(name))
                            }
                            RealmStorageType.FLOAT -> {
                                dynamicSample.set(name, 4.2f)
                                assertEquals(4.2f, dynamicSample.getValue(name))
                            }
                            RealmStorageType.DOUBLE -> {
                                dynamicSample.set(name, 4.2)
                                assertEquals(4.2, dynamicSample.getValue(name))
                            }
                            RealmStorageType.TIMESTAMP -> {
                                val value = RealmInstant.from(100, 100)
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.getValue(name))
                            }
                            RealmStorageType.OBJECT_ID -> {
                                when (name) {
                                    Sample::objectIdField.name -> {
                                        val value = ObjectId.create()
                                        dynamicSample.set(name, value)
                                        assertEquals(value, dynamicSample.getValue(name))
                                    }
                                    Sample::bsonObjectIdField.name -> {
                                        val value = BsonObjectId()
                                        dynamicSample.set(name, value)
                                        assertEquals(value, dynamicSample.getValue(name))
                                    }
                                }
                            }
                            RealmStorageType.UUID -> {
                                val value = RealmUUID.random()
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.getValue(name))
                            }
                            RealmStorageType.BINARY -> {
                                val value = byteArrayOf(42)
                                dynamicSample.set(name, value)
                                assertContentEquals(value, dynamicSample.getValue(name))
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    }
                }
                is ListPropertyType -> {
                    if (type.isComputed) {
                        val linkingObjects = dynamicSample.getBacklinks(property.name)
                        assertTrue(linkingObjects.isEmpty())
                        val target = dynamicMutableRealm.copyToRealm(
                            DynamicMutableRealmObject.create("Sample").apply {
                                set(Sample::stringField.name, "dynamic value")

                                when (property.name) {
                                    "objectBacklinks" -> {
                                        set(Sample::nullableObject.name, dynamicSample)
                                    }
                                    "listBacklinks" -> {
                                        getValueList<DynamicRealmObject>(Sample::objectListField.name).add(
                                            dynamicSample
                                        )
                                    }
                                    "setBacklinks" -> {
                                        getValueSet<DynamicRealmObject>(Sample::objectSetField.name).add(
                                            dynamicSample
                                        )
                                    }
                                    else -> error("Unhandled backlinks property: ${property.name}")
                                }
                            }
                        )
                        assertTrue(linkingObjects.isNotEmpty())
                        assertEquals(
                            target.getValue<String>(Sample::stringField.name),
                            linkingObjects.first().getValue(Sample::stringField.name)
                        )
                    } else if (type.isNullable) {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                val value = true
                                dynamicSample.getNullableValueList<Boolean>(property.name)
                                    .add(value)
                                dynamicSample.getNullableValueList<Boolean>(property.name).add(null)
                                val listOfNullable = dynamicSample.getNullableValueList(
                                    property.name,
                                    Boolean::class
                                )
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
                                dynamicSample.getNullableValueList<Long>(property.name).add(value)
                                dynamicSample.getNullableValueList<Long>(property.name).add(null)
                                val listOfNullable =
                                    dynamicSample.getNullableValueList(property.name, Long::class)
                                assertEquals(value, listOfNullable[0])
                                assertEquals(null, listOfNullable[1])
                            }
                            RealmStorageType.STRING -> {
                                val value = "NEW_ELEMENT"
                                dynamicSample.getNullableValueList<String>(property.name).add(value)
                                dynamicSample.getNullableValueList<String>(property.name).add(null)
                                val listOfNullable =
                                    dynamicSample.getNullableValueList(property.name, String::class)
                                assertEquals(value, listOfNullable[0])
                                assertEquals(null, listOfNullable[1])
                            }
                            RealmStorageType.FLOAT -> {
                                val value = 1.234f
                                dynamicSample.getNullableValueList<Float>(property.name).add(value)
                                dynamicSample.getNullableValueList<Float>(property.name).add(null)
                                val listOfNullable =
                                    dynamicSample.getNullableValueList(property.name, Float::class)
                                assertEquals(value, listOfNullable[0])
                                assertEquals(null, listOfNullable[1])
                            }
                            RealmStorageType.DOUBLE -> {
                                val value = 1.234
                                dynamicSample.getNullableValueList<Double>(property.name).add(value)
                                dynamicSample.getNullableValueList<Double>(property.name).add(null)
                                val listOfNullable =
                                    dynamicSample.getNullableValueList(property.name, Double::class)
                                assertEquals(value, listOfNullable[0])
                                assertEquals(null, listOfNullable[1])
                            }
                            RealmStorageType.TIMESTAMP -> {
                                val value = RealmInstant.from(100, 100)
                                dynamicSample.getNullableValueList<RealmInstant>(property.name)
                                    .add(value)
                                dynamicSample.getNullableValueList<RealmInstant>(property.name)
                                    .add(null)
                                val listOfNullable = dynamicSample.getNullableValueList(
                                    property.name,
                                    RealmInstant::class
                                )
                                assertEquals(value, listOfNullable[0])
                                assertEquals(null, listOfNullable[1])
                            }
                            RealmStorageType.OBJECT_ID -> {
                                when (name) {
                                    Sample::nullableObjectIdListField.name -> {
                                        val value = ObjectId.create()
                                        dynamicSample.getNullableValueList<ObjectId>(property.name)
                                            .add(value)
                                        dynamicSample.getNullableValueList<ObjectId>(property.name)
                                            .add(null)
                                        val listOfNullable = dynamicSample.getNullableValueList(
                                            property.name,
                                            ObjectId::class
                                        )
                                        assertEquals(value, listOfNullable[0])
                                        assertEquals(null, listOfNullable[1])
                                    }
                                    Sample::nullableBsonObjectIdListField.name -> {
                                        val value = BsonObjectId()
                                        dynamicSample.getNullableValueList<BsonObjectId>(property.name)
                                            .add(value)
                                        dynamicSample.getNullableValueList<BsonObjectId>(property.name)
                                            .add(null)
                                        val listOfNullable = dynamicSample.getNullableValueList(
                                            property.name,
                                            BsonObjectId::class
                                        )
                                        assertEquals(value, listOfNullable[0])
                                        assertEquals(null, listOfNullable[1])
                                    }
                                }
                            }
                            RealmStorageType.UUID -> {
                                val value = RealmUUID.random()
                                dynamicSample.getNullableValueList<RealmUUID>(property.name)
                                    .add(value)
                                dynamicSample.getNullableValueList<RealmUUID>(property.name)
                                    .add(null)
                                val listOfNullable = dynamicSample.getNullableValueList(
                                    property.name,
                                    RealmUUID::class
                                )
                                assertEquals(value, listOfNullable[0])
                                assertEquals(null, listOfNullable[1])
                            }
                            RealmStorageType.BINARY -> {
                                val value = byteArrayOf(42)
                                dynamicSample.getNullableValueList<ByteArray>(property.name)
                                    .add(value)
                                dynamicSample.getNullableValueList<ByteArray>(property.name)
                                    .add(null)
                                val listOfNullable = dynamicSample.getNullableValueList(
                                    property.name,
                                    ByteArray::class
                                )
                                assertContentEquals(value, listOfNullable[0])
                                assertEquals(null, listOfNullable[1])
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                val value = true
                                dynamicSample.getValueList<Boolean>(property.name).add(value)
                                assertEquals(
                                    value,
                                    dynamicSample.getValueList(property.name, Boolean::class)[0]
                                )
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
                                dynamicSample.getValueList<Long>(property.name).add(value)
                                assertEquals(
                                    value,
                                    dynamicSample.getValueList(property.name, Long::class)[0]
                                )
                            }
                            RealmStorageType.STRING -> {
                                val value = "NEW_ELEMENT"
                                dynamicSample.getValueList<String>(property.name).add(value)
                                assertEquals(
                                    value,
                                    dynamicSample.getValueList(property.name, String::class)[0]
                                )
                            }
                            RealmStorageType.FLOAT -> {
                                val value = 1.234f
                                dynamicSample.getValueList<Float>(property.name).add(value)
                                assertEquals(
                                    value,
                                    dynamicSample.getValueList(property.name, Float::class)[0]
                                )
                            }
                            RealmStorageType.DOUBLE -> {
                                val value = 1.234
                                dynamicSample.getValueList<Double>(property.name).add(value)
                                assertEquals(
                                    value,
                                    dynamicSample.getValueList(property.name, Double::class)[0]
                                )
                            }
                            RealmStorageType.TIMESTAMP -> {
                                val value = RealmInstant.from(100, 100)
                                dynamicSample.getValueList<RealmInstant>(property.name).add(value)
                                assertEquals(
                                    value,
                                    dynamicSample.getValueList(
                                        property.name,
                                        RealmInstant::class
                                    )[0]
                                )
                            }
                            RealmStorageType.OBJECT_ID -> {
                                when (name) {
                                    Sample::objectIdListField.name -> {
                                        val value = ObjectId.create()
                                        dynamicSample.getValueList<ObjectId>(property.name)
                                            .add(value)
                                        assertEquals(
                                            value,
                                            dynamicSample.getValueList(
                                                property.name,
                                                ObjectId::class
                                            )[0]
                                        )
                                    }
                                    Sample::bsonObjectIdListField.name -> {
                                        val value = BsonObjectId()
                                        dynamicSample.getValueList<BsonObjectId>(property.name)
                                            .add(value)
                                        assertEquals(
                                            value,
                                            dynamicSample.getValueList(
                                                property.name,
                                                BsonObjectId::class
                                            )[0]
                                        )
                                    }
                                }
                            }
                            RealmStorageType.UUID -> {
                                val value = RealmUUID.random()
                                dynamicSample.getValueList<RealmUUID>(property.name).add(value)
                                assertEquals(
                                    value,
                                    dynamicSample.getValueList(property.name, RealmUUID::class)[0]
                                )
                            }
                            RealmStorageType.BINARY -> {
                                val value = byteArrayOf(42)
                                dynamicSample.getValueList<ByteArray>(property.name).add(value)
                                assertContentEquals(
                                    value,
                                    dynamicSample.getValueList(property.name, ByteArray::class)[0]
                                )
                            }
                            RealmStorageType.OBJECT -> {
                                val value = dynamicMutableRealm.copyToRealm(
                                    DynamicMutableRealmObject.create("Sample")
                                ).set("stringField", "NEW_OBJECT")
                                dynamicSample.getValueList<DynamicRealmObject>(property.name)
                                    .add(value)
                                assertEquals(
                                    "NEW_OBJECT",
                                    dynamicSample.getValueList(
                                        property.name,
                                        DynamicRealmObject::class
                                    )[0].getValue("stringField")
                                )
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    }
                }
                is SetPropertyType -> {
                    if (type.isNullable) {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                val value = true
                                dynamicSample.getNullableValueSet<Boolean>(property.name).add(value)
                                dynamicSample.getNullableValueSet<Boolean>(property.name).add(null)
                                val setOfNullable =
                                    dynamicSample.getNullableValueSet(property.name, Boolean::class)
                                assertTrue(setOfNullable.contains(value))
                                assertTrue(setOfNullable.contains(null))
                            }
                            RealmStorageType.INT -> {
                                val value: Long = when (property.name) {
                                    "nullableByteSetField" -> defaultSample.byteField.toLong()
                                    "nullableCharSetField" -> defaultSample.charField.code.toLong()
                                    "nullableShortSetField" -> defaultSample.shortField.toLong()
                                    "nullableIntSetField" -> defaultSample.intField.toLong()
                                    "nullableLongSetField" -> defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                dynamicSample.getNullableValueSet<Long>(property.name).add(value)
                                dynamicSample.getNullableValueSet<Long>(property.name).add(null)
                                val setOfNullable =
                                    dynamicSample.getNullableValueSet(property.name, Long::class)
                                assertTrue(setOfNullable.contains(value))
                                assertTrue(setOfNullable.contains(null))
                            }
                            RealmStorageType.STRING -> {
                                val value = "NEW_ELEMENT"
                                dynamicSample.getNullableValueSet<String>(property.name).add(value)
                                dynamicSample.getNullableValueSet<String>(property.name).add(null)
                                val setOfNullable =
                                    dynamicSample.getNullableValueSet(property.name, String::class)
                                assertTrue(setOfNullable.contains(value))
                                assertTrue(setOfNullable.contains(null))
                            }
                            RealmStorageType.FLOAT -> {
                                val value = 1.234f
                                dynamicSample.getNullableValueSet<Float>(property.name).add(value)
                                dynamicSample.getNullableValueSet<Float>(property.name).add(null)
                                val setOfNullable =
                                    dynamicSample.getNullableValueSet(property.name, Float::class)
                                assertTrue(setOfNullable.contains(value))
                                assertTrue(setOfNullable.contains(null))
                            }
                            RealmStorageType.DOUBLE -> {
                                val value = 1.234
                                dynamicSample.getNullableValueSet<Double>(property.name).add(value)
                                dynamicSample.getNullableValueSet<Double>(property.name).add(null)
                                val setOfNullable =
                                    dynamicSample.getNullableValueSet(property.name, Double::class)
                                assertTrue(setOfNullable.contains(value))
                                assertTrue(setOfNullable.contains(null))
                            }
                            RealmStorageType.TIMESTAMP -> {
                                val value = RealmInstant.from(100, 100)
                                dynamicSample.getNullableValueSet<RealmInstant>(property.name)
                                    .add(value)
                                dynamicSample.getNullableValueSet<RealmInstant>(property.name)
                                    .add(null)
                                val setOfNullable = dynamicSample.getNullableValueSet(
                                    property.name,
                                    RealmInstant::class
                                )
                                assertTrue(setOfNullable.contains(value))
                                assertTrue(setOfNullable.contains(null))
                            }
                            RealmStorageType.OBJECT_ID -> {
                                when (name) {
                                    Sample::nullableObjectIdSetField.name -> {
                                        val value = ObjectId.create()
                                        dynamicSample.getNullableValueSet<ObjectId>(property.name)
                                            .add(value)
                                        dynamicSample.getNullableValueSet<ObjectId>(property.name)
                                            .add(null)
                                        val setOfNullable = dynamicSample.getNullableValueSet(
                                            property.name,
                                            ObjectId::class
                                        )
                                        assertTrue(setOfNullable.contains(value))
                                        assertTrue(setOfNullable.contains(null))
                                    }
                                    Sample::nullableBsonObjectIdSetField.name -> {
                                        val value = BsonObjectId()
                                        dynamicSample.getNullableValueSet<BsonObjectId>(property.name)
                                            .add(value)
                                        dynamicSample.getNullableValueSet<BsonObjectId>(property.name)
                                            .add(null)
                                        val setOfNullable = dynamicSample.getNullableValueSet(
                                            property.name,
                                            BsonObjectId::class
                                        )
                                        assertTrue(setOfNullable.contains(value))
                                        assertTrue(setOfNullable.contains(null))
                                    }
                                }
                            }
                            RealmStorageType.UUID -> {
                                val value = RealmUUID.random()
                                dynamicSample.getNullableValueSet<RealmUUID>(property.name)
                                    .add(value)
                                dynamicSample.getNullableValueSet<RealmUUID>(property.name)
                                    .add(null)
                                val setOfNullable = dynamicSample.getNullableValueSet(
                                    property.name,
                                    RealmUUID::class
                                )
                                assertTrue(setOfNullable.contains(value))
                                assertTrue(setOfNullable.contains(null))
                            }
                            RealmStorageType.BINARY -> {
                                val value = byteArrayOf(42)
                                dynamicSample.getNullableValueSet<ByteArray>(property.name)
                                    .add(value)
                                dynamicSample.getNullableValueSet<ByteArray>(property.name)
                                    .add(null)
                                val setOfNullable = dynamicSample.getNullableValueSet(
                                    property.name,
                                    ByteArray::class
                                )
                                assertTrue(setOfNullable.contains(value))
                                assertTrue(setOfNullable.contains(null))
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                val value = true
                                dynamicSample.getValueSet<Boolean>(property.name).add(value)
                                assertTrue(
                                    dynamicSample.getValueSet(property.name, Boolean::class)
                                        .contains(value)
                                )
                            }
                            RealmStorageType.INT -> {
                                val value: Long = when (property.name) {
                                    "byteSetField" -> defaultSample.byteField.toLong()
                                    "charSetField" -> defaultSample.charField.code.toLong()
                                    "shortSetField" -> defaultSample.shortField.toLong()
                                    "intSetField" -> defaultSample.intField.toLong()
                                    "longSetField" -> defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                dynamicSample.getValueSet<Long>(property.name).add(value)
                                assertTrue(
                                    dynamicSample.getValueSet(property.name, Long::class)
                                        .contains(value)
                                )
                            }
                            RealmStorageType.STRING -> {
                                val value = "NEW_ELEMENT"
                                dynamicSample.getValueSet<String>(property.name).add(value)
                                assertTrue(
                                    dynamicSample.getValueSet(property.name, String::class)
                                        .contains(value)
                                )
                            }
                            RealmStorageType.FLOAT -> {
                                val value = 1.234f
                                dynamicSample.getValueSet<Float>(property.name).add(value)
                                assertTrue(
                                    dynamicSample.getValueSet(property.name, Float::class)
                                        .contains(value)
                                )
                            }
                            RealmStorageType.DOUBLE -> {
                                val value = 1.234
                                dynamicSample.getValueSet<Double>(property.name).add(value)
                                assertTrue(
                                    dynamicSample.getValueSet(property.name, Double::class)
                                        .contains(value)
                                )
                            }
                            RealmStorageType.TIMESTAMP -> {
                                val value = RealmInstant.from(100, 100)
                                dynamicSample.getValueSet<RealmInstant>(property.name).add(value)
                                assertTrue(
                                    dynamicSample.getValueSet(
                                        property.name,
                                        RealmInstant::class
                                    ).contains(value)
                                )
                            }
                            RealmStorageType.OBJECT_ID -> {
                                when (name) {
                                    Sample::objectIdSetField.name -> {
                                        val value = ObjectId.create()
                                        dynamicSample.getValueSet<ObjectId>(property.name)
                                            .add(value)
                                        assertTrue(
                                            dynamicSample.getValueSet(
                                                property.name,
                                                ObjectId::class
                                            ).contains(value)
                                        )
                                    }
                                    Sample::bsonObjectIdSetField.name -> {
                                        val value = BsonObjectId()
                                        dynamicSample.getValueSet<BsonObjectId>(property.name)
                                            .add(value)
                                        assertTrue(
                                            dynamicSample.getValueSet(
                                                property.name,
                                                BsonObjectId::class
                                            ).contains(value)
                                        )
                                    }
                                }
                            }
                            RealmStorageType.UUID -> {
                                val value = RealmUUID.random()
                                dynamicSample.getValueSet<RealmUUID>(property.name).add(value)
                                assertTrue(
                                    dynamicSample.getValueSet(
                                        property.name,
                                        RealmUUID::class
                                    ).contains(value)
                                )
                            }
                            RealmStorageType.BINARY -> {
                                val value = byteArrayOf(42)
                                dynamicSample.getValueSet<ByteArray>(property.name).add(value)
                                assertTrue(
                                    dynamicSample.getValueSet(
                                        property.name,
                                        ByteArray::class
                                    ).contains(value)
                                )
                            }
                            RealmStorageType.OBJECT -> {
                                val value = dynamicMutableRealm.copyToRealm(
                                    DynamicMutableRealmObject.create("Sample")
                                ).set("stringField", "NEW_OBJECT")
                                dynamicSample.getValueSet<DynamicRealmObject>(property.name)
                                    .add(value)

                                // Loop through the set to find the element as indices aren't available
                                var found = false
                                dynamicSample.getValueSet(property.name, DynamicRealmObject::class)
                                    .forEach {
                                        if (it.getValue<String>("stringField") == "NEW_OBJECT") {
                                            found = true
                                            return@forEach
                                        }
                                    }
                                assertTrue(found)
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    }
                }
            }
            // TODO There is currently nothing that assert that we have tested all type
            // assertTrue("Untested types: $untested") { untested.isEmpty() }
        }
    }

    @Test
    fun set_detectsDuplicates() {
        val child = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "child"
        )
        val intermediate = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "intermedidate",
            "nullableObject" to child,
            "objectListField" to realmListOf(child, child)
        )
        val parent = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        parent.set("nullableObject", intermediate)

        dynamicMutableRealm.query("Sample").find().run {
            assertEquals(3, size)
        }
    }

    @Test
    fun set_embeddedRealmObject() {
        val parent =
            dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("EmbeddedParent"))
        parent.set("child", DynamicMutableRealmObject.create("EmbeddedChild", "id" to "child1"))
        dynamicMutableRealm.query("EmbeddedParent")
            .find()
            .single()
            .run {
                assertEquals("child1", getObject("child")!!.getNullableValue("id"))
            }
    }

    @Test
    fun set_overwriteEmbeddedRealmObject() {
        val parent =
            dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("EmbeddedParent"))
        parent.set("child", DynamicMutableRealmObject.create("EmbeddedChild", "id" to "child1"))
        dynamicMutableRealm.query("EmbeddedParent").find().single().run {
            assertEquals("child1", getObject("child")!!.getNullableValue("id"))
            parent.set("child", DynamicMutableRealmObject.create("EmbeddedChild", "id" to "child2"))
        }
        dynamicMutableRealm.query("EmbeddedChild")
            .find()
            .single()
            .run {
                assertEquals("child2", getNullableValue("id"))
            }
    }

    @Test
    fun set_throwsWithWrongType_stringInt() {
        val sample = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        assertFailsWithMessage<IllegalArgumentException>("Property 'Sample.stringField' of type 'class kotlin.String' cannot be assigned with value '42' of type 'class kotlin.Int'") {
            sample.set("stringField", 42)
        }
    }

    @Test
    fun set_throwsWithWrongType_longInt() {
        val sample = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        assertFailsWithMessage<IllegalArgumentException>("Property 'Sample.intField' of type 'class kotlin.Long' cannot be assigned with value '42' of type 'class kotlin.Int'") {
            sample.set("intField", 42)
        }
    }

    @Test
    fun set_throwsOnNullForRequiredField() {
        val o = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        assertFailsWithMessage<IllegalArgumentException>("Property 'Sample.stringField' of type 'class kotlin.String' cannot be assigned with value 'null'") {
            o.set("stringField", null)
        }
    }

    // This tests the current behavior of actually being able to update a primary key attribute on
    // a dynamic realm as it is required for migrations and that is the only place we actually
    // expose dynamic realms right now
    @Test
    fun set_primaryKey() {
        val o = dynamicMutableRealm.copyToRealm(
            DynamicMutableRealmObject.create(
                "PrimaryKeyString",
                mapOf("primaryKey" to "PRIMARY_KEY")
            )
        )
        o.set("primaryKey", "UPDATED_PRIMARY_KEY")
        assertEquals("UPDATED_PRIMARY_KEY", o.getValue("primaryKey"))
    }

    @Test
    fun set_updatesExistingObjectInTree() {
        val parent = dynamicMutableRealm.copyToRealm(
            DynamicMutableRealmObject.create(
                "EmbeddedParentWithPrimaryKey",
                "id" to 2L,
                "child" to DynamicMutableRealmObject.create(
                    "EmbeddedChildWithPrimaryKeyParent",
                    "subTree" to DynamicMutableRealmObject.create(
                        "EmbeddedParentWithPrimaryKey",
                        "id" to 1L,
                        "name" to "INIT"
                    )
                )
            )
        )
        dynamicMutableRealm.query("EmbeddedParentWithPrimaryKey", "id = 1")
            .find()
            .single()
            .run {
                assertEquals("INIT", getNullableValue("name"))
            }

        dynamicMutableRealm.run {
            findLatest(parent)!!.run {
                set(
                    "child",
                    DynamicMutableRealmObject.create(
                        "EmbeddedParentWithPrimaryKey",
                        "subTree" to DynamicMutableRealmObject.create(
                            "EmbeddedParentWithPrimaryKey",
                            "id" to 1L,
                            "name" to "UPDATED"
                        )
                    )
                )
            }
        }

        dynamicMutableRealm.query("EmbeddedParentWithPrimaryKey", "id = 1")
            .find()
            .single()
            .run {
                assertEquals("UPDATED", getNullableValue("name"))
            }
    }

    // ---------------------------------------------------------------------
    // Lists
    // ---------------------------------------------------------------------

    @Test
    fun list_add_embeddedRealmObject() {
        val parent =
            dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("EmbeddedParent"))
        parent.getObjectList("children").add(
            DynamicMutableRealmObject.create(
                "EmbeddedChild",
                "subTree" to DynamicMutableRealmObject.create("EmbeddedParent", "id" to "subParent")
            )
        )

        dynamicMutableRealm.query("EmbeddedChild")
            .find()
            .single()
            .run {
                assertEquals("subParent", getObject("subTree")!!.getNullableValue("id"))
            }
    }

    @Test
    fun list_add_detectsDuplicates() {
        val child = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "child"
        )
        val intermediate = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "intermedidate",
            "nullableObject" to child,
            "objectListField" to realmListOf(child, child)
        )
        val parent = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        parent.getObjectList("objectListField")
            .add(intermediate)

        dynamicMutableRealm.query("Sample")
            .find()
            .run {
                assertEquals(3, size)
            }
    }

    @Test
    fun list_addWithIndex_detectsDuplicates() {
        val child = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "child"
        )
        val intermediate = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "intermedidate",
            "nullableObject" to child,
            "objectListField" to realmListOf(child, child)
        )
        val parent = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        parent.getObjectList("objectListField")
            .add(0, intermediate)

        dynamicMutableRealm.query("Sample").find().run {
            assertEquals(3, size)
        }
    }

    @Test
    fun list_addAll_embeddedRealmObject() {
        val parent =
            dynamicMutableRealm.copyToRealm(
                DynamicMutableRealmObject.create(
                    "EmbeddedParent",
                    "id" to "parent"
                )
            )
        val child = DynamicMutableRealmObject.create(
            "EmbeddedChild",
            "subTree" to DynamicMutableRealmObject.create("EmbeddedParent", "id" to "subParent")
        )
        parent.getObjectList("children")
            .addAll(listOf(child, child))

        dynamicMutableRealm.query("EmbeddedChild").find().run {
            assertEquals(2, size)
            assertEquals("subParent", get(0).getObject("subTree")!!.getNullableValue("id"))
            assertEquals("subParent", get(1).getObject("subTree")!!.getNullableValue("id"))
        }
    }

    @Test
    fun list_addAll_detectsDuplicates() {
        val child = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "child"
        )
        val intermediate = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "intermedidate",
            "nullableObject" to child,
            "objectListField" to realmListOf(child, child)
        )
        val parent = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        parent.getObjectList("objectListField")
            .addAll(listOf(intermediate, intermediate))

        dynamicMutableRealm.query("Sample").find().run {
            assertEquals(3, size)
        }
    }

    @Test
    fun list_addAllWithIndex_detectsDuplicates() {
        val child = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "child"
        )
        val intermediate = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "intermedidate",
            "nullableObject" to child,
            "objectListField" to realmListOf(child, child)
        )
        val parent = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        parent.getObjectList("objectListField").addAll(0, listOf(intermediate, intermediate))

        dynamicMutableRealm.query("Sample").find().run {
            assertEquals(3, size)
        }
    }

    @Test
    fun list_set_detectsDuplicates() {
        val child1 = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "child1"
        )
        val child2 = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "child2"
        )
        val intermediate = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "intermedidate",
            "nullableObject" to child2,
            "objectListField" to realmListOf(child2, child2)
        )
        val parent = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        parent.getObjectList("objectListField").run {
            add(child1)
            set(0, intermediate)
        }
        dynamicMutableRealm.query("Sample").find().run {
            assertEquals(4, size)
        }
    }

    // ---------------------------------------------------------------------
    // Sets
    // ---------------------------------------------------------------------

    @Test
    fun set_add_detectsDuplicates() {
        val child = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "child"
        )
        val intermediate = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "intermedidate",
            "nullableObject" to child,
            "objectSetField" to realmSetOf(child, child)
        )
        val parent = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        parent.getObjectSet("objectSetField")
            .add(intermediate)

        dynamicMutableRealm.query("Sample")
            .find()
            .run {
                assertEquals(3, size)
            }
    }

    @Test
    fun set_addAll_detectsDuplicates() {
        val child = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "child"
        )
        val intermediate = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "intermedidate",
            "nullableObject" to child,
            "objectSetField" to realmSetOf(child, child)
        )
        val parent = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        parent.getObjectSet("objectSetField")
            .addAll(setOf(intermediate, intermediate))

        dynamicMutableRealm.query("Sample")
            .find()
            .run {
                assertEquals(3, size)
            }
    }
}
