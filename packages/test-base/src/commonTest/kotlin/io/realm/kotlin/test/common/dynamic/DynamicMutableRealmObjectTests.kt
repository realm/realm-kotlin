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

package io.realm.kotlin.test.common.dynamic

import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.dynamic.DynamicMutableRealm
import io.realm.kotlin.dynamic.DynamicMutableRealmObject
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.dynamic.getNullableValue
import io.realm.kotlin.dynamic.getNullableValueDictionary
import io.realm.kotlin.dynamic.getNullableValueList
import io.realm.kotlin.dynamic.getNullableValueSet
import io.realm.kotlin.dynamic.getValue
import io.realm.kotlin.dynamic.getValueDictionary
import io.realm.kotlin.dynamic.getValueList
import io.realm.kotlin.dynamic.getValueSet
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.entities.embedded.embeddedSchema
import io.realm.kotlin.entities.embedded.embeddedSchemaWithPrimaryKey
import io.realm.kotlin.entities.primarykey.PrimaryKeyString
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.realmDictionaryEntryOf
import io.realm.kotlin.ext.realmDictionaryOf
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.internal.InternalConfiguration
import io.realm.kotlin.schema.ListPropertyType
import io.realm.kotlin.schema.MapPropertyType
import io.realm.kotlin.schema.RealmClass
import io.realm.kotlin.schema.RealmProperty
import io.realm.kotlin.schema.RealmPropertyType
import io.realm.kotlin.schema.RealmSchema
import io.realm.kotlin.schema.RealmStorageType
import io.realm.kotlin.schema.SetPropertyType
import io.realm.kotlin.schema.ValuePropertyType
import io.realm.kotlin.test.StandaloneDynamicMutableRealm
import io.realm.kotlin.test.common.utils.assertFailsWithMessage
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import kotlinx.coroutines.test.runTest
import org.mongodb.kbson.BsonDecimal128
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
                            RealmStorageType.DECIMAL128 -> {
                                val value = Decimal128("1.84467440731231618E-615")
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.getNullableValue(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullableValue<Decimal128>(name))
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
                            RealmStorageType.ANY -> {
                                // Check writing a regular object using the Dynamic API throws
                                val objectValue = RealmAny.create(
                                    PrimaryKeyString(),
                                    PrimaryKeyString::class
                                )
                                assertFailsWith<IllegalArgumentException> {
                                    dynamicSample.set(name, objectValue)
                                }

                                // Test we can set null ...
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullableValue<RealmAny>(name))

                                // ... and primitives...
                                val value = RealmAny.create(42)
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.getNullableValue(name))

                                // ... and dynamic mutable unmanaged objects ...
                                DynamicMutableRealmObject.create(
                                    "Sample",
                                    mapOf("stringField" to "Custom1")
                                ).also { dynamicMutableUnmanagedObject ->
                                    val dynamicRealmAny =
                                        RealmAny.create(dynamicMutableUnmanagedObject)
                                    dynamicSample.set(name, dynamicRealmAny)
                                    val expectedValue =
                                        dynamicMutableUnmanagedObject.getValue<String>("stringField")
                                    val actualValue = dynamicSample.getNullableValue<RealmAny>(name)
                                        ?.asRealmObject<DynamicRealmObject>()
                                        ?.getValue<String>("stringField")
                                    assertEquals(expectedValue, actualValue)
                                }

                                // ... and dynamic mutable managed objects
                                dynamicMutableRealm.copyToRealm(
                                    DynamicMutableRealmObject.create(
                                        "Sample",
                                        mapOf("stringField" to "Custom2")
                                    )
                                ).also { dynamicMutableManagedObject ->
                                    val dynamicRealmAny =
                                        RealmAny.create(dynamicMutableManagedObject)
                                    dynamicSample.set(name, dynamicRealmAny)
                                    val expectedValue =
                                        dynamicMutableManagedObject.getValue<String>("stringField")
                                    val managedDynamicMutableObject = dynamicSample.getNullableValue<RealmAny>(name)
                                        ?.asRealmObject<DynamicMutableRealmObject>()
                                    val actualValue = managedDynamicMutableObject?.getValue<String>("stringField")
                                    assertEquals(expectedValue, actualValue)

                                    // Check we did indeed get a dynamic mutable object
                                    managedDynamicMutableObject?.set("stringField", "NEW")
                                    assertEquals("NEW", managedDynamicMutableObject?.getValue("stringField"))
                                }
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
                            RealmStorageType.DECIMAL128 -> {
                                val value = Decimal128("1.84467440731231618E-615")
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.getValue(name))
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
                        fun <T> assertionsForNullable(
                            listFromSample: RealmList<T?>,
                            property: RealmProperty,
                            value: T,
                            clazz: KClass<*>
                        ) {
                            listFromSample.add(value)
                            listFromSample.add(null)
                            val listOfNullable = dynamicSample.getNullableValueList(
                                property.name,
                                clazz
                            )
                            assertEquals(2, listOfNullable.size)
                            assertEquals(value, listOfNullable[0])
                            assertNull(listOfNullable[1])
                        }

                        when (type.storageType) {
                            RealmStorageType.BOOL -> assertionsForNullable(
                                dynamicSample.getNullableValueList(property.name),
                                property,
                                true,
                                Boolean::class
                            )
                            RealmStorageType.INT -> {
                                val value: Long = when (property.name) {
                                    "nullableByteListField" -> defaultSample.byteField.toLong()
                                    "nullableCharListField" -> defaultSample.charField.code.toLong()
                                    "nullableShortListField" -> defaultSample.shortField.toLong()
                                    "nullableIntListField" -> defaultSample.intField.toLong()
                                    "nullableLongListField" -> defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList(property.name),
                                    property,
                                    value,
                                    Long::class
                                )
                            }
                            RealmStorageType.STRING -> assertionsForNullable(
                                dynamicSample.getNullableValueList(property.name),
                                property,
                                "NEW_ELEMENT",
                                String::class
                            )
                            RealmStorageType.FLOAT -> assertionsForNullable(
                                dynamicSample.getNullableValueList(property.name),
                                property,
                                1.234f,
                                Float::class
                            )
                            RealmStorageType.DOUBLE -> assertionsForNullable(
                                dynamicSample.getNullableValueList(property.name),
                                property,
                                1.234,
                                Double::class
                            )
                            RealmStorageType.DECIMAL128 -> assertionsForNullable(
                                dynamicSample.getNullableValueList(property.name),
                                property,
                                Decimal128("1.84467440731231618E-615"),
                                BsonDecimal128::class
                            )
                            RealmStorageType.TIMESTAMP -> assertionsForNullable(
                                dynamicSample.getNullableValueList(property.name),
                                property,
                                RealmInstant.from(100, 100),
                                RealmInstant::class
                            )
                            RealmStorageType.OBJECT_ID -> when (name) {
                                Sample::nullableObjectIdListField.name -> assertionsForNullable(
                                    dynamicSample.getNullableValueList(property.name),
                                    property,
                                    ObjectId.create(),
                                    ObjectId::class
                                )
                                Sample::nullableBsonObjectIdListField.name -> assertionsForNullable(
                                    dynamicSample.getNullableValueList(property.name),
                                    property,
                                    BsonObjectId(),
                                    BsonObjectId::class
                                )
                            }
                            RealmStorageType.UUID -> assertionsForNullable(
                                dynamicSample.getNullableValueList(property.name),
                                property,
                                RealmUUID.random(),
                                RealmUUID::class
                            )
                            RealmStorageType.BINARY -> {
                                // TODO use assertionsForNullable when we add support for structural equality for RealmList<ByteArray>
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
                            RealmStorageType.ANY -> {
                                // Check writing a regular object using the Dynamic API throws
                                val objectValue = RealmAny.create(
                                    PrimaryKeyString(),
                                    PrimaryKeyString::class
                                )
                                assertFailsWith<ClassCastException> {
                                    dynamicSample.set(name, realmListOf(objectValue))
                                }

                                // Test we can set null ...
                                dynamicSample.set(name, realmListOf<RealmAny?>(null))
                                dynamicSample.getNullableValueList<RealmAny>(name)
                                    .also { list ->
                                        assertEquals(1, list.size)
                                        assertEquals(null, list[0])
                                    }

                                // ... and primitives...
                                val value = RealmAny.create(42)
                                dynamicSample.set(name, realmListOf(value))
                                dynamicSample.getNullableValueList<RealmAny>(name)
                                    .also { list ->
                                        assertEquals(1, list.size)
                                        assertEquals(value, list[0])
                                    }

                                // ... and dynamic mutable unmanaged objects ...
                                DynamicMutableRealmObject.create(
                                    "Sample",
                                    mapOf("stringField" to "Custom1")
                                ).also { dynamicMutableUnmanagedObject ->
                                    val dynamicRealmAny =
                                        RealmAny.create(dynamicMutableUnmanagedObject)
                                    dynamicSample.set(name, realmListOf(dynamicRealmAny))
                                    val expectedValue =
                                        dynamicMutableUnmanagedObject.getValue<String>("stringField")
                                    val actualList =
                                        dynamicSample.getNullableValueList<RealmAny>(name)
                                    assertEquals(1, actualList.size)
                                    val actualValue = actualList[0]
                                        ?.asRealmObject<DynamicRealmObject>()
                                        ?.getValue<String>("stringField")
                                    assertEquals(expectedValue, actualValue)
                                }

                                // ... and dynamic mutable managed objects
                                dynamicMutableRealm.copyToRealm(
                                    DynamicMutableRealmObject.create(
                                        "Sample",
                                        mapOf("stringField" to "Custom2")
                                    )
                                ).also { dynamicMutableManagedObject ->
                                    val dynamicRealmAny =
                                        RealmAny.create(dynamicMutableManagedObject)
                                    dynamicSample.set(name, realmListOf(dynamicRealmAny))
                                    val expectedValue =
                                        dynamicMutableManagedObject.getValue<String>("stringField")
                                    val actualList =
                                        dynamicSample.getNullableValueList<RealmAny>(name)
                                    assertEquals(1, actualList.size)
                                    val managedDynamicMutableObject = actualList[0]
                                        ?.asRealmObject<DynamicMutableRealmObject>()
                                    val actualValue = managedDynamicMutableObject?.getValue<String>("stringField")
                                    assertEquals(expectedValue, actualValue)

                                    // Check we did indeed get a dynamic mutable object
                                    managedDynamicMutableObject?.set("stringField", "NEW")
                                    assertEquals("NEW", managedDynamicMutableObject?.getValue("stringField"))
                                }
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        fun <T> assertionsForValue(
                            listFromSample: RealmList<T>,
                            property: RealmProperty,
                            value: T,
                            clazz: KClass<*>
                        ) {
                            listFromSample.add(value)
                            val valueList = dynamicSample.getValueList(
                                property.name,
                                clazz
                            )
                            assertEquals(1, valueList.size)
                            assertEquals(value, valueList[0] as T)
                        }

                        when (type.storageType) {
                            RealmStorageType.BOOL -> assertionsForValue(
                                dynamicSample.getValueList(property.name),
                                property,
                                true,
                                Boolean::class
                            )
                            RealmStorageType.INT -> {
                                val value: Long = when (property.name) {
                                    "byteListField" -> defaultSample.byteField.toLong()
                                    "charListField" -> defaultSample.charField.code.toLong()
                                    "shortListField" -> defaultSample.shortField.toLong()
                                    "intListField" -> defaultSample.intField.toLong()
                                    "longListField" -> defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                assertionsForValue(
                                    dynamicSample.getValueList(property.name),
                                    property,
                                    value,
                                    Long::class
                                )
                            }
                            RealmStorageType.STRING -> assertionsForValue(
                                dynamicSample.getValueList(property.name),
                                property,
                                "NEW_ELEMENT",
                                String::class
                            )
                            RealmStorageType.FLOAT -> assertionsForValue(
                                dynamicSample.getValueList(property.name),
                                property,
                                1.234f,
                                Float::class
                            )
                            RealmStorageType.DOUBLE -> assertionsForValue(
                                dynamicSample.getValueList(property.name),
                                property,
                                1.234,
                                Double::class
                            )
                            RealmStorageType.DECIMAL128 -> assertionsForValue(
                                dynamicSample.getValueList(property.name),
                                property,
                                Decimal128("1.84467440731231618E-615"),
                                BsonDecimal128::class
                            )
                            RealmStorageType.TIMESTAMP -> assertionsForValue(
                                dynamicSample.getValueList(property.name),
                                property,
                                RealmInstant.from(100, 100),
                                RealmInstant::class
                            )
                            RealmStorageType.OBJECT_ID -> when (name) {
                                Sample::objectIdListField.name -> assertionsForValue(
                                    dynamicSample.getValueList(property.name),
                                    property,
                                    ObjectId.create(),
                                    ObjectId::class
                                )
                                Sample::bsonObjectIdListField.name -> assertionsForValue(
                                    dynamicSample.getValueList(property.name),
                                    property,
                                    BsonObjectId(),
                                    BsonObjectId::class
                                )
                            }
                            RealmStorageType.UUID -> assertionsForValue(
                                dynamicSample.getValueList(property.name),
                                property,
                                RealmUUID.random(),
                                RealmUUID::class
                            )
                            RealmStorageType.BINARY -> {
                                // TODO use assertionsForValue when we add support for structural equality for RealmList<ByteArray>
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
                        fun <T> assertionsForNullable(
                            setFromSample: RealmSet<T?>,
                            property: RealmProperty,
                            value: T,
                            clazz: KClass<*>
                        ) {
                            setFromSample.add(value)
                            setFromSample.add(null)
                            val setOfNullable = dynamicSample.getNullableValueSet(
                                property.name,
                                clazz
                            )
                            assertEquals(2, setOfNullable.size)
                            assertTrue(setOfNullable.contains(value as Any?))
                            assertTrue(setOfNullable.contains(null))
                        }

                        when (type.storageType) {
                            RealmStorageType.BOOL -> assertionsForNullable(
                                dynamicSample.getNullableValueSet(property.name),
                                property,
                                true,
                                Boolean::class
                            )
                            RealmStorageType.INT -> {
                                val value: Long = when (property.name) {
                                    "nullableByteSetField" -> defaultSample.byteField.toLong()
                                    "nullableCharSetField" -> defaultSample.charField.code.toLong()
                                    "nullableShortSetField" -> defaultSample.shortField.toLong()
                                    "nullableIntSetField" -> defaultSample.intField.toLong()
                                    "nullableLongSetField" -> defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet(property.name),
                                    property,
                                    value,
                                    Long::class
                                )
                            }
                            RealmStorageType.STRING -> assertionsForNullable(
                                dynamicSample.getNullableValueSet(property.name),
                                property,
                                "NEW_ELEMENT",
                                String::class
                            )
                            RealmStorageType.FLOAT -> assertionsForNullable(
                                dynamicSample.getNullableValueSet(property.name),
                                property,
                                1.234f,
                                Float::class
                            )
                            RealmStorageType.DOUBLE -> assertionsForNullable(
                                dynamicSample.getNullableValueSet(property.name),
                                property,
                                1.234,
                                Double::class
                            )
                            RealmStorageType.TIMESTAMP -> assertionsForNullable(
                                dynamicSample.getNullableValueSet(property.name),
                                property,
                                RealmInstant.from(100, 100),
                                RealmInstant::class
                            )
                            RealmStorageType.OBJECT_ID -> when (name) {
                                Sample::nullableObjectIdSetField.name -> assertionsForNullable(
                                    dynamicSample.getNullableValueSet(property.name),
                                    property,
                                    ObjectId.create(),
                                    ObjectId::class
                                )
                                Sample::nullableBsonObjectIdSetField.name -> assertionsForNullable(
                                    dynamicSample.getNullableValueSet(property.name),
                                    property,
                                    BsonObjectId(),
                                    BsonObjectId::class
                                )
                            }
                            RealmStorageType.UUID -> assertionsForNullable(
                                dynamicSample.getNullableValueSet(property.name),
                                property,
                                RealmUUID.random(),
                                RealmUUID::class
                            )
                            RealmStorageType.BINARY -> assertionsForNullable(
                                dynamicSample.getNullableValueSet(property.name),
                                property,
                                byteArrayOf(42),
                                ByteArray::class
                            )
                            RealmStorageType.DECIMAL128 -> assertionsForNullable(
                                dynamicSample.getNullableValueSet(property.name),
                                property,
                                Decimal128("1.84467440731231618E-615"),
                                Decimal128::class
                            )
                            RealmStorageType.ANY -> {
                                // Check writing a regular object using the Dynamic API throws
                                val objectValue = RealmAny.create(
                                    PrimaryKeyString(),
                                    PrimaryKeyString::class
                                )
                                assertFailsWith<ClassCastException> {
                                    dynamicSample.set(name, realmSetOf(objectValue))
                                }

                                // Test we can set null ...
                                dynamicSample.set(name, realmSetOf<RealmAny?>(null))
                                dynamicSample.getNullableValueSet<RealmAny>(name)
                                    .also { set ->
                                        assertEquals(1, set.size)
                                        assertEquals(null, set.iterator().next())
                                    }

                                // ... and primitives...
                                val value = RealmAny.create(42)
                                dynamicSample.set(name, realmSetOf(value))
                                dynamicSample.getNullableValueSet<RealmAny>(name)
                                    .also { set ->
                                        assertEquals(1, set.size)
                                        assertEquals(value, set.iterator().next())
                                    }

                                // ... and dynamic mutable unmanaged objects ...
                                DynamicMutableRealmObject.create(
                                    "Sample",
                                    mapOf("stringField" to "Custom1")
                                ).also { dynamicMutableUnmanagedObject ->
                                    val dynamicRealmAny =
                                        RealmAny.create(dynamicMutableUnmanagedObject)
                                    dynamicSample.set(name, realmSetOf(dynamicRealmAny))
                                    val expectedValue =
                                        dynamicMutableUnmanagedObject.getValue<String>("stringField")
                                    val actualSet =
                                        dynamicSample.getNullableValueSet<RealmAny>(name)
                                    assertEquals(1, actualSet.size)
                                    val actualValue = actualSet.iterator().next()
                                        ?.asRealmObject<DynamicRealmObject>()
                                        ?.getValue<String>("stringField")
                                    assertEquals(expectedValue, actualValue)
                                }

                                // ... and dynamic mutable managed objects
                                dynamicMutableRealm.copyToRealm(
                                    DynamicMutableRealmObject.create(
                                        "Sample",
                                        mapOf("stringField" to "Custom2")
                                    )
                                ).also { dynamicMutableManagedObject ->
                                    val dynamicRealmAny =
                                        RealmAny.create(dynamicMutableManagedObject)
                                    dynamicSample.set(name, realmSetOf(dynamicRealmAny))
                                    val expectedValue =
                                        dynamicMutableManagedObject.getValue<String>("stringField")
                                    val actualSet =
                                        dynamicSample.getNullableValueSet<RealmAny>(name)
                                    assertEquals(1, actualSet.size)
                                    val managedDynamicMutableObject = actualSet.iterator().next()
                                        ?.asRealmObject<DynamicMutableRealmObject>()
                                    val actualValue = managedDynamicMutableObject?.getValue<String>("stringField")
                                    assertEquals(expectedValue, actualValue)

                                    // Check we did indeed get a dynamic mutable object
                                    managedDynamicMutableObject?.set("stringField", "NEW")
                                    assertEquals("NEW", managedDynamicMutableObject?.getValue("stringField"))
                                }
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        fun <T> assertionsForValue(
                            setFromSample: RealmSet<T>,
                            property: RealmProperty,
                            value: T,
                            clazz: KClass<*>
                        ) {
                            setFromSample.add(value)
                            val setOfValue = dynamicSample.getValueSet(property.name, clazz)
                            assertEquals(1, setOfValue.size)
                            assertTrue(setOfValue.contains(value as Any))
                        }

                        when (type.storageType) {
                            RealmStorageType.BOOL -> assertionsForValue(
                                dynamicSample.getValueSet(property.name),
                                property,
                                true,
                                Boolean::class
                            )
                            RealmStorageType.INT -> {
                                val value: Long = when (property.name) {
                                    "byteSetField" -> defaultSample.byteField.toLong()
                                    "charSetField" -> defaultSample.charField.code.toLong()
                                    "shortSetField" -> defaultSample.shortField.toLong()
                                    "intSetField" -> defaultSample.intField.toLong()
                                    "longSetField" -> defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name),
                                    property,
                                    value,
                                    Long::class
                                )
                            }
                            RealmStorageType.STRING -> assertionsForValue(
                                dynamicSample.getValueSet(property.name),
                                property,
                                "NEW_ELEMENT",
                                String::class
                            )
                            RealmStorageType.FLOAT -> assertionsForValue(
                                dynamicSample.getValueSet(property.name),
                                property,
                                1.234f,
                                Float::class
                            )
                            RealmStorageType.DOUBLE -> assertionsForValue(
                                dynamicSample.getValueSet(property.name),
                                property,
                                1.234,
                                Double::class
                            )
                            RealmStorageType.TIMESTAMP -> assertionsForValue(
                                dynamicSample.getValueSet(property.name),
                                property,
                                RealmInstant.from(100, 100),
                                RealmInstant::class
                            )
                            RealmStorageType.OBJECT_ID -> when (name) {
                                Sample::objectIdSetField.name -> assertionsForValue(
                                    dynamicSample.getValueSet(property.name),
                                    property,
                                    ObjectId.create(),
                                    ObjectId::class
                                )
                                Sample::bsonObjectIdSetField.name -> assertionsForValue(
                                    dynamicSample.getValueSet(property.name),
                                    property,
                                    BsonObjectId(),
                                    BsonObjectId::class
                                )
                            }
                            RealmStorageType.UUID -> assertionsForValue(
                                dynamicSample.getValueSet(property.name),
                                property,
                                RealmUUID.random(),
                                RealmUUID::class
                            )
                            RealmStorageType.BINARY -> assertionsForValue(
                                dynamicSample.getValueSet(property.name),
                                property,
                                byteArrayOf(42),
                                ByteArray::class
                            )
                            RealmStorageType.DECIMAL128 -> assertionsForValue(
                                dynamicSample.getValueSet(property.name),
                                property,
                                Decimal128("1.84467440731231618E-615"),
                                BsonDecimal128::class
                            )
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
                is MapPropertyType -> {
                    if (type.isNullable) {
                        fun <T> assertionsForNullable(
                            dictionaryFromSample: RealmDictionary<T?>,
                            property: RealmProperty,
                            value: T,
                            clazz: KClass<*>
                        ) {
                            dictionaryFromSample["A"] = value
                            dictionaryFromSample["B"] = null
                            val dictionaryOfNullable = dynamicSample.getNullableValueDictionary(
                                property.name,
                                clazz
                            )
                            assertEquals(2, dictionaryOfNullable.size)
                            assertTrue(dictionaryOfNullable.containsKey("A"))
                            assertTrue(dictionaryOfNullable.containsKey("B"))
                            assertFalse(dictionaryOfNullable.containsKey("C"))
                            assertTrue(dictionaryOfNullable.containsValue(value as Any?))
                            assertTrue(dictionaryOfNullable.containsValue(null))
                            dictionaryOfNullable.entries.also { entries ->
                                assertTrue(
                                    entries.contains(realmDictionaryEntryOf("A" to value as Any?))
                                )
                                assertTrue(
                                    entries.contains(realmDictionaryEntryOf("B" to null))
                                )
                            }
                        }

                        when (type.storageType) {
                            RealmStorageType.BOOL -> assertionsForNullable(
                                dynamicSample.getNullableValueDictionary(property.name),
                                property,
                                true,
                                Boolean::class
                            )
                            RealmStorageType.INT -> {
                                val value: Long = when (property.name) {
                                    "nullableByteDictionaryField" -> defaultSample.byteField.toLong()
                                    "nullableCharDictionaryField" -> defaultSample.charField.code.toLong()
                                    "nullableShortDictionaryField" -> defaultSample.shortField.toLong()
                                    "nullableIntDictionaryField" -> defaultSample.intField.toLong()
                                    "nullableLongDictionaryField" -> defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary(property.name),
                                    property,
                                    value,
                                    Long::class
                                )
                            }
                            RealmStorageType.STRING -> assertionsForNullable(
                                dynamicSample.getNullableValueDictionary(property.name),
                                property,
                                "NEW_ELEMENT",
                                String::class
                            )
                            RealmStorageType.BINARY -> assertionsForNullable(
                                dynamicSample.getNullableValueDictionary(property.name),
                                property,
                                byteArrayOf(42),
                                ByteArray::class
                            )
                            RealmStorageType.OBJECT -> {
                                val value = dynamicMutableRealm.copyToRealm(
                                    DynamicMutableRealmObject.create("Sample")
                                ).set("stringField", "NEW_OBJECT")
                                dynamicSample.getNullableValueDictionary<DynamicRealmObject>(property.name)["A"] =
                                    value
                                dynamicSample.getNullableValueDictionary<DynamicRealmObject>(property.name)["B"] =
                                    null

                                val nullableObjDictionary =
                                    dynamicSample.getNullableValueDictionary(
                                        property.name,
                                        DynamicRealmObject::class
                                    )
                                assertEquals(2, nullableObjDictionary.size)
                                assertTrue(nullableObjDictionary.containsKey("A"))
                                assertTrue(nullableObjDictionary.containsKey("B"))
                                assertFalse(nullableObjDictionary.containsKey("C"))
                                nullableObjDictionary["A"].also { obj ->
                                    assertNotNull(obj)
                                    assertEquals(
                                        "NEW_OBJECT",
                                        obj.getValue("stringField")
                                    )
                                }
                            }
                            RealmStorageType.FLOAT -> assertionsForNullable(
                                dynamicSample.getNullableValueDictionary(property.name),
                                property,
                                1.234f,
                                Float::class
                            )
                            RealmStorageType.DOUBLE -> assertionsForNullable(
                                dynamicSample.getNullableValueDictionary(property.name),
                                property,
                                1.234,
                                Double::class
                            )
                            RealmStorageType.DECIMAL128 -> assertionsForNullable(
                                dynamicSample.getNullableValueDictionary(property.name),
                                property,
                                Decimal128("1.84467440731231618E-615"),
                                Decimal128::class
                            )
                            RealmStorageType.TIMESTAMP -> assertionsForNullable(
                                dynamicSample.getNullableValueDictionary(property.name),
                                property,
                                RealmInstant.from(100, 100),
                                RealmInstant::class
                            )
                            RealmStorageType.OBJECT_ID -> when (name) {
                                Sample::objectIdSetField.name -> assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary(property.name),
                                    property,
                                    ObjectId.create(),
                                    ObjectId::class
                                )
                                Sample::bsonObjectIdSetField.name -> assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary(property.name),
                                    property,
                                    BsonObjectId(),
                                    BsonObjectId::class
                                )
                            }
                            RealmStorageType.UUID -> assertionsForNullable(
                                dynamicSample.getNullableValueDictionary(property.name),
                                property,
                                RealmUUID.random(),
                                RealmUUID::class
                            )
                            RealmStorageType.ANY -> {
                                // Check writing a regular object using the Dynamic API throws
                                val objectValue = RealmAny.create(
                                    PrimaryKeyString(),
                                    PrimaryKeyString::class
                                )
                                assertFailsWith<ClassCastException> {
                                    dynamicSample.set(
                                        name,
                                        realmDictionaryOf<RealmAny?>("A" to objectValue)
                                    )
                                }

                                // Test we can set null ...
                                dynamicSample.set(name, realmDictionaryOf<RealmAny?>("A" to null))
                                dynamicSample.getNullableValueDictionary<RealmAny>(name)
                                    .also { dictionary ->
                                        assertEquals(1, dictionary.size)
                                        assertNull(dictionary["A"])
                                    }

                                // ... and primitives...
                                val value = RealmAny.create(42)
                                dynamicSample.set(name, realmDictionaryOf<RealmAny?>("A" to value))
                                dynamicSample.getNullableValueDictionary<RealmAny>(name)
                                    .also { dictionary ->
                                        assertEquals(1, dictionary.size)
                                        assertEquals(value, dictionary["A"])
                                    }

                                // ... and dynamic mutable unmanaged objects ...
                                DynamicMutableRealmObject.create(
                                    "Sample",
                                    mapOf("stringField" to "Custom1")
                                ).also { dynamicMutableUnmanagedObject ->
                                    val dynamicRealmAny =
                                        RealmAny.create(dynamicMutableUnmanagedObject)
                                    dynamicSample.set(name, realmDictionaryOf("A" to dynamicRealmAny))
                                    val expectedValue =
                                        dynamicMutableUnmanagedObject.getValue<String>("stringField")
                                    val actualDictionary =
                                        dynamicSample.getNullableValueDictionary<RealmAny>(name)
                                    assertEquals(1, actualDictionary.size)
                                    val actualValue = actualDictionary["A"]
                                        ?.asRealmObject<DynamicRealmObject>()
                                        ?.getValue<String>("stringField")
                                    assertEquals(expectedValue, actualValue)
                                }

                                // ... and dynamic mutable managed objects
                                dynamicMutableRealm.copyToRealm(
                                    DynamicMutableRealmObject.create(
                                        "Sample",
                                        mapOf("stringField" to "Custom2")
                                    )
                                ).also { dynamicMutableManagedObject ->
                                    val dynamicRealmAny =
                                        RealmAny.create(dynamicMutableManagedObject)
                                    dynamicSample.set(name, realmDictionaryOf("A" to dynamicRealmAny))
                                    val expectedValue =
                                        dynamicMutableManagedObject.getValue<String>("stringField")
                                    val actualDictionary =
                                        dynamicSample.getNullableValueDictionary<RealmAny>(name)
                                    assertEquals(1, actualDictionary.size)
                                    val managedDynamicMutableObject = actualDictionary["A"]
                                        ?.asRealmObject<DynamicMutableRealmObject>()
                                    val actualValue = managedDynamicMutableObject?.getValue<String>("stringField")
                                    assertEquals(expectedValue, actualValue)

                                    // Check we did indeed get a dynamic mutable object
                                    managedDynamicMutableObject?.set("stringField", "NEW")
                                    assertEquals("NEW", managedDynamicMutableObject?.getValue("stringField"))
                                }
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        fun <T> assertionsForValue(
                            dictionaryFromSample: RealmDictionary<T>,
                            property: RealmProperty,
                            value: T,
                            clazz: KClass<*>
                        ) {
                            dictionaryFromSample["A"] = value
                            val dictionaryOfValue = dynamicSample.getValueDictionary(
                                property.name,
                                clazz
                            )
                            assertEquals(1, dictionaryOfValue.size)
                            assertTrue(dictionaryOfValue.containsKey("A"))
                            assertFalse(dictionaryOfValue.containsKey("B"))
                            assertTrue(dictionaryOfValue.containsValue(value as Any))
                            assertTrue(
                                dictionaryOfValue.entries
                                    .contains(realmDictionaryEntryOf("A" to value as Any))
                            )
                        }
                        when (type.storageType) {
                            RealmStorageType.BOOL -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                property,
                                true,
                                Boolean::class
                            )
                            RealmStorageType.INT -> {
                                val value: Long = when (property.name) {
                                    "byteDictionaryField" -> defaultSample.byteField.toLong()
                                    "charDictionaryField" -> defaultSample.charField.code.toLong()
                                    "shortDictionaryField" -> defaultSample.shortField.toLong()
                                    "intDictionaryField" -> defaultSample.intField.toLong()
                                    "longDictionaryField" -> defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                assertionsForValue(
                                    dynamicSample.getValueDictionary(property.name),
                                    property,
                                    value,
                                    Long::class
                                )
                            }
                            RealmStorageType.STRING -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                property,
                                "NEW_ELEMENT",
                                String::class
                            )
                            RealmStorageType.BINARY -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                property,
                                byteArrayOf(42),
                                ByteArray::class
                            )
                            RealmStorageType.OBJECT -> {
                                val value = dynamicMutableRealm.copyToRealm(
                                    DynamicMutableRealmObject.create("Sample")
                                ).set("stringField", "NEW_OBJECT")
                                dynamicSample.getValueDictionary<DynamicRealmObject>(property.name)["A"] =
                                    value

                                val objDictionary = dynamicSample.getValueDictionary(
                                    property.name,
                                    DynamicRealmObject::class
                                )
                                assertEquals(1, objDictionary.size)
                                assertTrue(objDictionary.containsKey("A"))
                                assertFalse(objDictionary.containsKey("B"))
                                val objFromDictionary = assertNotNull(objDictionary["A"])
                                assertEquals(
                                    "NEW_OBJECT",
                                    objFromDictionary.getValue<String>("stringField")
                                )
                            }
                            RealmStorageType.FLOAT -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                property,
                                1.234F,
                                Float::class
                            )
                            RealmStorageType.DOUBLE -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                property,
                                1.234,
                                Double::class
                            )
                            RealmStorageType.DECIMAL128 -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                property,
                                Decimal128("1.84467440731231618E-615"),
                                Decimal128::class
                            )
                            RealmStorageType.TIMESTAMP -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                property,
                                RealmInstant.from(100, 100),
                                RealmInstant::class
                            )
                            RealmStorageType.OBJECT_ID -> when (name) {
                                Sample::objectIdSetField.name -> assertionsForValue(
                                    dynamicSample.getValueDictionary(property.name),
                                    property,
                                    ObjectId.create(),
                                    ObjectId::class
                                )
                                Sample::bsonObjectIdSetField.name -> assertionsForValue(
                                    dynamicSample.getValueDictionary(property.name),
                                    property,
                                    BsonObjectId(),
                                    BsonObjectId::class
                                )
                            }
                            RealmStorageType.UUID -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                property,
                                RealmUUID.random(),
                                RealmUUID::class
                            )
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
            "stringField" to "intermediate",
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
        parent.getObjectList("childrenList").add(
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
            "stringField" to "intermediate",
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
            "stringField" to "intermediate",
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
        parent.getObjectList("childrenList")
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
            "stringField" to "intermediate",
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
            "stringField" to "intermediate",
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
            "stringField" to "intermediate",
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
            "stringField" to "intermediate",
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
            "stringField" to "intermediate",
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

    // ---------------------------------------------------------------------
    // Dictionaries
    // ---------------------------------------------------------------------

    @Test
    fun dictionary_put_embeddedRealmObject() {
        val parent =
            dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("EmbeddedParent"))
        parent.getObjectDictionary("childrenDictionary")["A"] =
            DynamicMutableRealmObject.create(
                "EmbeddedChild",
                "subTree" to DynamicMutableRealmObject.create("EmbeddedParent", "id" to "subParent")
            )

        dynamicMutableRealm.query("EmbeddedChild")
            .find()
            .single()
            .run {
                assertEquals("subParent", getObject("subTree")!!.getNullableValue("id"))
            }
    }

    @Test
    fun dictionary_putAll_embeddedRealmObject() {
        val parent = dynamicMutableRealm.copyToRealm(
            DynamicMutableRealmObject.create(
                "EmbeddedParent",
                "id" to "parent"
            )
        )
        val child = DynamicMutableRealmObject.create(
            "EmbeddedChild",
            "subTree" to DynamicMutableRealmObject.create("EmbeddedParent", "id" to "subParent")
        )
        parent.getObjectDictionary("childrenDictionary")
            .putAll(listOf("A" to child, "B" to child))

        dynamicMutableRealm.query("EmbeddedChild").find().run {
            assertEquals(2, size)
            assertEquals("subParent", get(0).getObject("subTree")!!.getNullableValue("id"))
            assertEquals("subParent", get(1).getObject("subTree")!!.getNullableValue("id"))
        }
    }

    @Test
    fun dictionary_put_detectsDuplicates() {
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
            "stringField" to "intermediate",
            "nullableObject" to child2,
            "nullableObjectDictionaryFieldNotNull" to realmDictionaryOf("A" to child2, "B" to child2)
        )
        val parent = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        parent.getObjectDictionary("nullableObjectDictionaryFieldNotNull").run {
            put("A", child1)
            put("B", intermediate)
        }
        dynamicMutableRealm.query("Sample").find().run {
            assertEquals(4, size)
        }
    }

    @Test
    fun dictionary_putAll_detectsDuplicates() {
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
            "stringField" to "intermediate",
            "nullableObject" to child2,
            "nullableObjectDictionaryFieldNotNull" to realmDictionaryOf("A" to child2, "B" to child2)
        )
        val parent = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        parent.getObjectDictionary("nullableObjectDictionaryFieldNotNull").run {
            putAll(listOf("A" to child1, "B" to intermediate))
        }
        dynamicMutableRealm.query("Sample").find().run {
            assertEquals(4, size)
        }
    }

    @Test
    fun throwsOnRealmAnyPrimaryKey() {
        val instance = DynamicMutableRealmObject.create(
            "PrimaryKeyString",
            "primaryKey" to RealmAny.create("PRIMARY_KEY"),
        )
        assertFailsWithMessage<IllegalArgumentException>("Cannot use object 'RealmAny{type=STRING, value=PRIMARY_KEY}' of type 'RealmAnyImpl' as primary key argument") {
            dynamicMutableRealm.copyToRealm(instance)
        }
    }
}
