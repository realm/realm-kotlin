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
import io.realm.kotlin.dynamic.get
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
import kotlin.test.assertIs
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
        val child: DynamicMutableRealmObject? = parent.get("nullableObject")
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
            assertEquals("PARENT", get("stringField"))
            val child: DynamicMutableRealmObject? = parent.get("nullableObject")
            assertNotNull(child)
            assertTrue(child.isManaged())
            assertEquals("CHILD", child.get("stringField"))
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
                                assertEquals(true, dynamicSample.get<Boolean?>(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.get<Boolean?>(name))
                            }
                            RealmStorageType.INT -> {
                                dynamicSample.set(name, 42L)
                                assertEquals(42L, dynamicSample.get<Long?>(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.get<Long?>(name))
                            }
                            RealmStorageType.STRING -> {
                                dynamicSample.set(name, "STRING")
                                assertEquals("STRING", dynamicSample.get<String?>(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.get<String?>(name))
                            }
                            RealmStorageType.OBJECT -> {
                                dynamicSample.set(name, Sample())
                                val nullableObject = dynamicSample.get<DynamicRealmObject?>(name)
                                assertNotNull(nullableObject)
                                assertEquals("Realm", nullableObject.get("stringField"))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.get<DynamicRealmObject?>(name))
                            }
                            RealmStorageType.FLOAT -> {
                                dynamicSample.set(name, 4.2f)
                                assertEquals(4.2f, dynamicSample.get<Float?>(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.get<Float?>(name))
                            }
                            RealmStorageType.DOUBLE -> {
                                dynamicSample.set(name, 4.2)
                                assertEquals(4.2, dynamicSample.get<Double?>(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.get<Double?>(name))
                            }
                            RealmStorageType.DECIMAL128 -> {
                                val value = Decimal128("1.84467440731231618E-615")
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.get<Decimal128?>(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.get<Decimal128?>(name))
                            }
                            RealmStorageType.TIMESTAMP -> {
                                val value = RealmInstant.from(100, 100)
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.get<RealmInstant?>(name))
                                dynamicSample.set(name, null)
                                assertEquals(
                                    null,
                                    dynamicSample.get<RealmInstant?>(name)
                                )
                            }
                            RealmStorageType.OBJECT_ID -> {
                                val value = BsonObjectId()
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.get<BsonObjectId?>(name))
                                dynamicSample.set(name, null)
                                assertEquals(
                                    null,
                                    dynamicSample.get<BsonObjectId?>(name)
                                )
                            }
                            RealmStorageType.UUID -> {
                                val value = RealmUUID.random()
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.get<RealmUUID?>(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.get<RealmUUID?>(name))
                            }
                            RealmStorageType.BINARY -> {
                                val value = byteArrayOf(42)
                                dynamicSample.set(name, value)
                                assertContentEquals(value, dynamicSample.get<ByteArray?>(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.get<ByteArray?>(name))
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
                                assertEquals(null, dynamicSample.get<RealmAny?>(name))

                                // ... and primitives...
                                val value = RealmAny.create(42)
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.get<RealmAny?>(name))

                                // ... and dynamic mutable unmanaged objects ...
                                DynamicMutableRealmObject.create(
                                    "Sample",
                                    mapOf("stringField" to "Custom1")
                                ).also { dynamicMutableUnmanagedObject ->
                                    val dynamicRealmAny =
                                        RealmAny.create(dynamicMutableUnmanagedObject)
                                    dynamicSample.set(name, dynamicRealmAny)
                                    val expectedValue =
                                        dynamicMutableUnmanagedObject.get<String>("stringField")
                                    val actualValue = dynamicSample.get<RealmAny?>(name)
                                        ?.asRealmObject<DynamicRealmObject>()
                                        ?.get<String>("stringField")
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
                                        dynamicMutableManagedObject.get<String>("stringField")
                                    val managedDynamicMutableObject =
                                        dynamicSample.get<RealmAny?>(name)
                                            ?.asRealmObject<DynamicMutableRealmObject>()
                                    val actualValue =
                                        managedDynamicMutableObject?.get<String>("stringField")
                                    assertEquals(expectedValue, actualValue)

                                    // Check we did indeed get a dynamic mutable object
                                    managedDynamicMutableObject?.set("stringField", "NEW")
                                    assertEquals(
                                        "NEW",
                                        managedDynamicMutableObject?.get<String>("stringField")
                                    )
                                }
                                // Collections in RealmAny are tested in
                                // testSetsInRealmAny()
                                // testNestedCollectionsInListInRealmAny()
                                // testNestedCollectionsInDictionarytInRealmAny()
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                dynamicSample.set(name, true)
                                assertEquals(true, dynamicSample.get(name))
                            }
                            RealmStorageType.INT -> {
                                dynamicSample.set(name, 42L)
                                assertEquals(42L, dynamicSample.get(name))
                            }
                            RealmStorageType.STRING -> {
                                dynamicSample.set(name, "STRING")
                                assertEquals("STRING", dynamicSample.get(name))
                            }
                            RealmStorageType.FLOAT -> {
                                dynamicSample.set(name, 4.2f)
                                assertEquals(4.2f, dynamicSample.get(name))
                            }
                            RealmStorageType.DOUBLE -> {
                                dynamicSample.set(name, 4.2)
                                assertEquals(4.2, dynamicSample.get(name))
                            }
                            RealmStorageType.DECIMAL128 -> {
                                val value = Decimal128("1.84467440731231618E-615")
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.get(name))
                            }
                            RealmStorageType.TIMESTAMP -> {
                                val value = RealmInstant.from(100, 100)
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.get(name))
                            }
                            RealmStorageType.OBJECT_ID -> {
                                val value = BsonObjectId()
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.get(name))
                            }
                            RealmStorageType.UUID -> {
                                val value = RealmUUID.random()
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.get(name))
                            }
                            RealmStorageType.BINARY -> {
                                val value = byteArrayOf(42)
                                dynamicSample.set(name, value)
                                assertContentEquals(value, dynamicSample.get<ByteArray>(name))
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
                                        get<RealmList<DynamicRealmObject>>(Sample::objectListField.name).add(
                                            dynamicSample
                                        )
                                    }
                                    "setBacklinks" -> {
                                        get<RealmSet<DynamicRealmObject>>(Sample::objectSetField.name).add(
                                            dynamicSample
                                        )
                                    }
                                    else -> error("Unhandled backlinks property: ${property.name}")
                                }
                            }
                        )
                        assertTrue(linkingObjects.isNotEmpty())
                        assertEquals(
                            target.get<String>(Sample::stringField.name),
                            linkingObjects.first().get(Sample::stringField.name)
                        )
                    } else if (type.isNullable) {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> assertionsForNullable(
                                dynamicSample,
                                dynamicSample.get<RealmList<Boolean?>>(property.name),
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
                                    dynamicSample,
                                    dynamicSample.get<RealmList<Long?>>(property.name),
                                    property,
                                    value,
                                    Long::class
                                )
                            }
                            RealmStorageType.STRING -> assertionsForNullable(
                                dynamicSample,
                                dynamicSample.get<RealmList<String?>>(property.name),
                                property,
                                "NEW_ELEMENT",
                                String::class
                            )
                            RealmStorageType.FLOAT -> assertionsForNullable(
                                dynamicSample,
                                dynamicSample.get<RealmList<Float?>>(property.name),
                                property,
                                1.234f,
                                Float::class
                            )
                            RealmStorageType.DOUBLE -> assertionsForNullable(
                                dynamicSample,
                                dynamicSample.get<RealmList<Double?>>(property.name),
                                property,
                                1.234,
                                Double::class
                            )
                            RealmStorageType.DECIMAL128 -> assertionsForNullable(
                                dynamicSample,
                                dynamicSample.get<RealmList<BsonDecimal128?>>(property.name),
                                property,
                                Decimal128("1.84467440731231618E-615"),
                                BsonDecimal128::class
                            )
                            RealmStorageType.TIMESTAMP -> assertionsForNullable(
                                dynamicSample,
                                dynamicSample.get<RealmList<RealmInstant?>>(property.name),
                                property,
                                RealmInstant.from(100, 100),
                                RealmInstant::class
                            )
                            RealmStorageType.OBJECT_ID -> assertionsForNullable(
                                dynamicSample,
                                dynamicSample.get<RealmList<BsonObjectId?>>(property.name),
                                property,
                                BsonObjectId(),
                                BsonObjectId::class
                            )
                            RealmStorageType.UUID -> assertionsForNullable(
                                dynamicSample,
                                dynamicSample.get<RealmList<RealmUUID?>>(property.name),
                                property,
                                RealmUUID.random(),
                                RealmUUID::class
                            )
                            RealmStorageType.BINARY -> {
                                // TODO use assertionsForNullable when we add support for structural equality for RealmList<ByteArray>
                                val value = byteArrayOf(42)
                                dynamicSample.get<RealmList<ByteArray?>>(property.name)
                                    .add(value)
                                dynamicSample.get<RealmList<ByteArray?>>(property.name)
                                    .add(null)
                                // FIXME DByteArray::class
                                val listOfNullable = dynamicSample.get<RealmList<ByteArray?>>(
                                    property.name,
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
                                dynamicSample.get<RealmList<RealmAny?>>(name)
                                    .also { list ->
                                        assertEquals(1, list.size)
                                        assertEquals(null, list[0])
                                    }

                                // ... and primitives...
                                val value = RealmAny.create(42)
                                dynamicSample.set(name, realmListOf(value))
                                dynamicSample.get<RealmList<RealmAny?>>(name)
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
                                        dynamicMutableUnmanagedObject.get<String>("stringField")
                                    val actualList =
                                        dynamicSample.get<RealmList<RealmAny?>>(name)
                                    assertEquals(1, actualList.size)
                                    val actualValue = actualList[0]
                                        ?.asRealmObject<DynamicRealmObject>()
                                        ?.get<String>("stringField")
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
                                        dynamicMutableManagedObject.get<String>("stringField")
                                    val actualList =
                                        dynamicSample.get<RealmList<RealmAny?>>(name)
                                    assertEquals(1, actualList.size)
                                    val managedDynamicMutableObject = actualList[0]
                                        ?.asRealmObject<DynamicMutableRealmObject>()
                                    val actualValue =
                                        managedDynamicMutableObject?.get<String>("stringField")
                                    assertEquals(expectedValue, actualValue)

                                    // Check we did indeed get a dynamic mutable object
                                    managedDynamicMutableObject?.set("stringField", "NEW")
                                    assertEquals(
                                        "NEW",
                                        managedDynamicMutableObject?.get<String>("stringField")
                                    )
                                }
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmList<Boolean>>(property.name),
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
                                    dynamicSample,
                                    dynamicSample.get<RealmList<Long>>(property.name),
                                    property,
                                    value,
                                    Long::class
                                )
                            }
                            RealmStorageType.STRING -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmList<String>>(property.name),
                                property,
                                "NEW_ELEMENT",
                                String::class
                            )
                            RealmStorageType.FLOAT -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmList<Float>>(property.name),
                                property,
                                1.234f,
                                Float::class
                            )
                            RealmStorageType.DOUBLE -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmList<Double>>(property.name),
                                property,
                                1.234,
                                Double::class
                            )
                            RealmStorageType.DECIMAL128 -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmList<BsonDecimal128>>(property.name),
                                property,
                                Decimal128("1.84467440731231618E-615"),
                                BsonDecimal128::class
                            )
                            RealmStorageType.TIMESTAMP -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmList<RealmInstant>>(property.name),
                                property,
                                RealmInstant.from(100, 100),
                                RealmInstant::class
                            )
                            RealmStorageType.OBJECT_ID -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmList<BsonObjectId>>(property.name),
                                property,
                                BsonObjectId(),
                                BsonObjectId::class
                            )
                            RealmStorageType.UUID -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmList<RealmUUID>>(property.name),
                                property,
                                RealmUUID.random(),
                                RealmUUID::class
                            )
                            RealmStorageType.BINARY -> {
                                // TODO use assertionsForValue when we add support for structural equality for RealmList<ByteArray>
                                val value = byteArrayOf(42)
                                dynamicSample.get<RealmList<ByteArray>>(property.name).add(value)
                                assertContentEquals(
                                    value,
                                    dynamicSample.get<RealmList<ByteArray>>(property.name)[0]
                                )
                            }
                            RealmStorageType.OBJECT -> {
                                val value = dynamicMutableRealm.copyToRealm(
                                    DynamicMutableRealmObject.create("Sample")
                                ).set("stringField", "NEW_OBJECT")
                                dynamicSample.get<RealmList<DynamicRealmObject>>(property.name)
                                    .add(value)
                                assertEquals(
                                    "NEW_OBJECT",
                                    dynamicSample.get<RealmList<DynamicRealmObject>>(
                                        property.name,
//                                        DynamicRealmObject::class
                                    )[0].get("stringField")
                                )
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    }
                }
                is SetPropertyType -> {
                    if (type.isNullable) {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> assertionsForNullable(
                                dynamicSample,
                                dynamicSample.get<RealmSet<Boolean?>>(property.name),
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
                                    dynamicSample,
                                    dynamicSample.get<RealmSet<Long?>>(property.name),
                                    property,
                                    value,
                                    Long::class
                                )
                            }
                            RealmStorageType.STRING -> assertionsForNullable<String>(
                                dynamicSample,
                                dynamicSample.get<RealmSet<String?>>(property.name),
                                property,
                                "NEW_ELEMENT",
                                String::class
                            )
                            RealmStorageType.FLOAT -> assertionsForNullable(
                                dynamicSample,
                                dynamicSample.get<RealmSet<Float?>>(property.name),
                                property,
                                1.234f,
                                Float::class
                            )
                            RealmStorageType.DOUBLE -> assertionsForNullable(
                                dynamicSample,
                                dynamicSample.get<RealmSet<Double?>>(property.name),
                                property,
                                1.234,
                                Double::class
                            )
                            RealmStorageType.TIMESTAMP -> assertionsForNullable(
                                dynamicSample,
                                dynamicSample.get<RealmSet<RealmInstant?>>(property.name),
                                property,
                                RealmInstant.from(100, 100),
                                RealmInstant::class
                            )
                            RealmStorageType.OBJECT_ID -> assertionsForNullable(
                                dynamicSample,
                                dynamicSample.get<RealmSet<BsonObjectId?>>(property.name),
                                property,
                                BsonObjectId(),
                                BsonObjectId::class
                            )
                            RealmStorageType.UUID -> assertionsForNullable(
                                dynamicSample,
                                dynamicSample.get<RealmSet<RealmUUID?>>(property.name),
                                property,
                                RealmUUID.random(),
                                RealmUUID::class
                            )
                            RealmStorageType.BINARY -> assertionsForNullable(
                                dynamicSample,
                                dynamicSample.get<RealmSet<ByteArray?>>(property.name),
                                property,
                                byteArrayOf(42),
                                ByteArray::class
                            )
                            RealmStorageType.DECIMAL128 -> assertionsForNullable(
                                dynamicSample,
                                dynamicSample.get<RealmSet<Decimal128?>>(property.name),
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
                                dynamicSample.get<RealmSet<RealmAny?>>(name)
                                    .also { set ->
                                        assertEquals(1, set.size)
                                        assertEquals(null, set.iterator().next())
                                    }

                                // ... and primitives...
                                val value = RealmAny.create(42)
                                dynamicSample.set(name, realmSetOf(value))
                                dynamicSample.get<RealmSet<RealmAny?>>(name)
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
                                        dynamicMutableUnmanagedObject.get<String>("stringField")
                                    val actualSet =
                                        dynamicSample.get<RealmSet<RealmAny?>>(name)
                                    assertEquals(1, actualSet.size)
                                    val actualValue = actualSet.iterator().next()
                                        ?.asRealmObject<DynamicRealmObject>()
                                        ?.get<String>("stringField")
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
                                        dynamicMutableManagedObject.get<String>("stringField")
                                    val actualSet =
                                        dynamicSample.get<RealmSet<RealmAny?>>(name)
                                    assertEquals(1, actualSet.size)
                                    val managedDynamicMutableObject = actualSet.iterator().next()
                                        ?.asRealmObject<DynamicMutableRealmObject>()
                                    val actualValue =
                                        managedDynamicMutableObject?.get<String>("stringField")
                                    assertEquals(expectedValue, actualValue)

                                    // Check we did indeed get a dynamic mutable object
                                    managedDynamicMutableObject?.set("stringField", "NEW")
                                    assertEquals(
                                        "NEW",
                                        managedDynamicMutableObject?.get<String>("stringField")
                                    )
                                }
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {

                        when (type.storageType) {
                            RealmStorageType.BOOL -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmSet<Boolean>>(property.name),
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
                                    dynamicSample,
                                    dynamicSample.get<RealmSet<Long>>(property.name),
                                    property,
                                    value,
                                    Long::class
                                )
                            }
                            RealmStorageType.STRING -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmSet<String>>(property.name),
                                property,
                                "NEW_ELEMENT",
                                String::class
                            )
                            RealmStorageType.FLOAT -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmSet<Float>>(property.name),
                                property,
                                1.234f,
                                Float::class
                            )
                            RealmStorageType.DOUBLE -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmSet<Double>>(property.name),
                                property,
                                1.234,
                                Double::class
                            )
                            RealmStorageType.TIMESTAMP -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmSet<RealmInstant>>(property.name),
                                property,
                                RealmInstant.from(100, 100),
                                RealmInstant::class
                            )
                            RealmStorageType.OBJECT_ID -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmSet<BsonObjectId>>(property.name),
                                property,
                                BsonObjectId(),
                                BsonObjectId::class
                            )
                            RealmStorageType.UUID -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmSet<RealmUUID>>(property.name),
                                property,
                                RealmUUID.random(),
                                RealmUUID::class
                            )
                            RealmStorageType.BINARY -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmSet<ByteArray>>(property.name),
                                property,
                                byteArrayOf(42),
                                ByteArray::class
                            )
                            RealmStorageType.DECIMAL128 -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmSet<Decimal128>>(property.name),
                                property,
                                Decimal128("1.84467440731231618E-615"),
                                BsonDecimal128::class
                            )
                            RealmStorageType.OBJECT -> {
                                val value = dynamicMutableRealm.copyToRealm(
                                    DynamicMutableRealmObject.create("Sample")
                                ).set("stringField", "NEW_OBJECT")
                                dynamicSample.get<RealmSet<DynamicRealmObject>>(property.name)
                                    .add(value)

                                // Loop through the set to find the element as indices aren't available
                                var found = false
                                dynamicSample.get<RealmSet<DynamicRealmObject>>(property.name)
                                    .forEach {
                                        if (it.get<String>("stringField") == "NEW_OBJECT") {
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
                        when (type.storageType) {
                            RealmStorageType.BOOL -> assertionsForNullable(
                                dynamicSample,
                                dynamicSample.get<RealmDictionary<Boolean?>>(property.name),
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
                                    dynamicSample,
                                    dynamicSample.get<RealmDictionary<Long?>>(property.name),
                                    property,
                                    value,
                                    Long::class
                                )
                            }
                            RealmStorageType.STRING -> assertionsForNullable(
                                dynamicSample,
                                dynamicSample.get<RealmDictionary<String?>>(property.name),
                                property,
                                "NEW_ELEMENT",
                                String::class
                            )
                            RealmStorageType.BINARY -> assertionsForNullable(
                                dynamicSample,
                                dynamicSample.get<RealmDictionary<ByteArray?>>(property.name),
                                property,
                                byteArrayOf(42),
                                ByteArray::class
                            )
                            RealmStorageType.OBJECT -> {
                                val value = dynamicMutableRealm.copyToRealm(
                                    DynamicMutableRealmObject.create("Sample")
                                ).set("stringField", "NEW_OBJECT")
                                dynamicSample.get<RealmDictionary<DynamicRealmObject?>>(
                                    property.name
                                )["A"] =
                                    value
                                dynamicSample.get<RealmDictionary<DynamicRealmObject?>>(
                                    property.name
                                )["B"] =
                                    null

                                val nullableObjDictionary =
                                    dynamicSample.get<RealmDictionary<DynamicRealmObject?>>(
                                        property.name,
                                    )
                                assertEquals(2, nullableObjDictionary.size)
                                assertTrue(nullableObjDictionary.containsKey("A"))
                                assertTrue(nullableObjDictionary.containsKey("B"))
                                assertFalse(nullableObjDictionary.containsKey("C"))
                                nullableObjDictionary["A"].also { obj ->
                                    assertNotNull(obj)
                                    assertEquals(
                                        "NEW_OBJECT",
                                        obj.get("stringField")
                                    )
                                }
                            }
                            RealmStorageType.FLOAT -> assertionsForNullable(
                                dynamicSample,
                                dynamicSample.get<RealmDictionary<Float?>>(property.name),
                                property,
                                1.234f,
                                Float::class
                            )
                            RealmStorageType.DOUBLE -> assertionsForNullable(
                                dynamicSample,
                                dynamicSample.get<RealmDictionary<Double?>>(property.name),
                                property,
                                1.234,
                                Double::class
                            )
                            RealmStorageType.DECIMAL128 -> assertionsForNullable(
                                dynamicSample,
                                dynamicSample.get<RealmDictionary<Decimal128?>>(property.name),
                                property,
                                Decimal128("1.84467440731231618E-615"),
                                Decimal128::class
                            )
                            RealmStorageType.TIMESTAMP -> assertionsForNullable(
                                dynamicSample,
                                dynamicSample.get<RealmDictionary<RealmInstant?>>(property.name),
                                property,
                                RealmInstant.from(100, 100),
                                RealmInstant::class
                            )
                            RealmStorageType.OBJECT_ID -> assertionsForNullable(
                                dynamicSample,
                                dynamicSample.get<RealmDictionary<BsonObjectId?>>(property.name),
                                property,
                                BsonObjectId(),
                                BsonObjectId::class
                            )
                            RealmStorageType.UUID -> assertionsForNullable(
                                dynamicSample,
                                dynamicSample.get<RealmDictionary<RealmUUID?>>(property.name),
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
                                dynamicSample.get<RealmDictionary<RealmAny?>>(name)
                                    .also { dictionary ->
                                        assertEquals(1, dictionary.size)
                                        assertNull(dictionary["A"])
                                    }

                                // ... and primitives...
                                val value = RealmAny.create(42)
                                dynamicSample.set(name, realmDictionaryOf<RealmAny?>("A" to value))
                                dynamicSample.get<RealmDictionary<RealmAny?>>(name)
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
                                    dynamicSample.set(
                                        name,
                                        realmDictionaryOf("A" to dynamicRealmAny)
                                    )
                                    val expectedValue =
                                        dynamicMutableUnmanagedObject.get<String>("stringField")
                                    val actualDictionary =
                                        dynamicSample.get<RealmDictionary<RealmAny?>>(name)
                                    assertEquals(1, actualDictionary.size)
                                    val actualValue = actualDictionary["A"]
                                        ?.asRealmObject<DynamicRealmObject>()
                                        ?.get<String>("stringField")
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
                                    dynamicSample.set(
                                        name,
                                        realmDictionaryOf("A" to dynamicRealmAny)
                                    )
                                    val expectedValue =
                                        dynamicMutableManagedObject.get<String>("stringField")
                                    val actualDictionary =
                                        dynamicSample.get<RealmDictionary<RealmAny?>>(name)
                                    assertEquals(1, actualDictionary.size)
                                    val managedDynamicMutableObject = actualDictionary["A"]
                                        ?.asRealmObject<DynamicMutableRealmObject>()
                                    val actualValue =
                                        managedDynamicMutableObject?.get<String>("stringField")
                                    assertEquals(expectedValue, actualValue)

                                    // Check we did indeed get a dynamic mutable object
                                    managedDynamicMutableObject?.set("stringField", "NEW")
                                    assertEquals(
                                        "NEW",
                                        managedDynamicMutableObject?.get<String>("stringField")
                                    )
                                }
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {

                        when (type.storageType) {
                            RealmStorageType.BOOL -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmDictionary<Boolean>>(property.name),
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
                                    dynamicSample,
                                    dynamicSample.get<RealmDictionary<Long>>(property.name),
                                    property,
                                    value,
                                    Long::class
                                )
                            }
                            RealmStorageType.STRING -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmDictionary<String>>(property.name),
                                property,
                                "NEW_ELEMENT",
                                String::class
                            )
                            RealmStorageType.BINARY -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmDictionary<ByteArray>>(property.name),
                                property,
                                byteArrayOf(42),
                                ByteArray::class
                            )
                            RealmStorageType.OBJECT -> {
                                val value = dynamicMutableRealm.copyToRealm(
                                    DynamicMutableRealmObject.create("Sample")
                                ).set("stringField", "NEW_OBJECT")
                                dynamicSample.get<RealmDictionary<DynamicRealmObject>>(property.name)["A"] =
                                    value

                                val objDictionary = dynamicSample.get<RealmDictionary<DynamicRealmObject>>(
                                    property.name,
                                )
                                assertEquals(1, objDictionary.size)
                                assertTrue(objDictionary.containsKey("A"))
                                assertFalse(objDictionary.containsKey("B"))
                                val objFromDictionary = assertNotNull(objDictionary["A"])
                                assertEquals(
                                    "NEW_OBJECT",
                                    objFromDictionary.get<String>("stringField")
                                )
                            }
                            RealmStorageType.FLOAT -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmDictionary<Float>>(property.name),
                                property,
                                1.234F,
                                Float::class
                            )
                            RealmStorageType.DOUBLE -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmDictionary<Double>>(property.name),
                                property,
                                1.234,
                                Double::class
                            )
                            RealmStorageType.DECIMAL128 -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmDictionary<Decimal128>>(property.name),
                                property,
                                Decimal128("1.84467440731231618E-615"),
                                Decimal128::class
                            )
                            RealmStorageType.TIMESTAMP -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmDictionary<RealmInstant>>(property.name),
                                property,
                                RealmInstant.from(100, 100),
                                RealmInstant::class
                            )
                            RealmStorageType.OBJECT_ID -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmDictionary<BsonObjectId>>(property.name),
                                property,
                                BsonObjectId(),
                                BsonObjectId::class
                            )
                            RealmStorageType.UUID -> assertionsForValue(
                                dynamicSample,
                                dynamicSample.get<RealmDictionary<RealmUUID>>(property.name),
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
    fun testNestedCollectionsInListInRealmAny() {
        val dynamicSampleInner = dynamicMutableRealm.copyToRealm(
            DynamicMutableRealmObject.create("Sample", "stringField" to "INNER")
        )
        dynamicMutableRealm.copyToRealm(
            DynamicMutableRealmObject.create(
                "Sample",
                "nullableRealmAnyField" to RealmAny.create(
                    realmListOf(
                        RealmAny.create(
                            realmListOf(
                                RealmAny.create(
                                    DynamicMutableRealmObject.create(
                                        "Sample",
                                        "stringField" to "INNER_LIST"
                                    )
                                )
                            )
                        ),
                        RealmAny.create(
                            realmDictionaryOf(
                                "key" to RealmAny.create(
                                    DynamicMutableRealmObject.create(
                                        "Sample",
                                        "stringField" to "INNER_DICT"
                                    )
                                )
                            )
                        ),
                    )
                )
            )
        ).let {
            val list = it.get<RealmAny?>("nullableRealmAnyField")!!.asList()
            // Verify that we get mutable instances out of the collections
            list[0]!!.asList().let { embeddedList ->
                val o = embeddedList.first()!!
                    .asRealmObject<DynamicMutableRealmObject>()
                assertIs<DynamicMutableRealmObject>(o)
                assertEquals("INNER_LIST", o.get("stringField"))
                embeddedList.add(RealmAny.Companion.create(dynamicSampleInner))
            }
            list[1]!!.asDictionary().let { embeddedDictionary ->
                val o = embeddedDictionary["key"]!!
                    .asRealmObject<DynamicMutableRealmObject>()
                assertIs<DynamicMutableRealmObject>(o)
                assertEquals("INNER_DICT", o.get("stringField"))
                embeddedDictionary.put("UPDATE", RealmAny.Companion.create(dynamicSampleInner))
            }
        }
    }

    @Test
    fun testNestedCollectionsInDictionarytInRealmAny() {
        val dynamicSampleInner = dynamicMutableRealm.copyToRealm(
            DynamicMutableRealmObject.create(
                "Sample",
                "stringField" to "INNER"
            )
        )
        // Collections in dictionary
        dynamicMutableRealm.copyToRealm(
            DynamicMutableRealmObject.create(
                "Sample",
                "nullableRealmAnyField" to RealmAny.create(
                    realmDictionaryOf(
                        "list" to RealmAny.create(
                            realmListOf(
                                RealmAny.create(
                                    DynamicMutableRealmObject.create(
                                        "Sample",
                                        "stringField" to "INNER_LIST"
                                    )
                                )
                            )
                        ),
                        "dict" to RealmAny.create(
                            realmDictionaryOf(
                                "key" to RealmAny.create(
                                    DynamicMutableRealmObject.create(
                                        "Sample",
                                        "stringField" to "INNER_DICT"
                                    )
                                )
                            )
                        ),
                    )
                )
            )
        ).let {
            val dict = it.get<RealmAny?>("nullableRealmAnyField")!!.asDictionary()
            // Verify that we get mutable instances out of the collections
            dict["list"]!!.asList().let { embeddedList ->
                val o = embeddedList.first()!!
                    .asRealmObject<DynamicMutableRealmObject>()
                assertIs<DynamicMutableRealmObject>(o)
                assertEquals("INNER_LIST", o.get("stringField"))
                embeddedList.add(RealmAny.Companion.create(dynamicSampleInner))
            }
            dict["dict"]!!.asDictionary().let { embeddedDictionary ->
                val o = embeddedDictionary["key"]!!
                    .asRealmObject<DynamicMutableRealmObject>()
                assertIs<DynamicMutableRealmObject>(o)
                assertEquals("INNER_DICT", o.get("stringField"))
                embeddedDictionary.put("UPDATE", RealmAny.Companion.create(dynamicSampleInner))
            }
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
                assertEquals("child1", get<DynamicMutableRealmObject?>("child")!!.get<String?>("id"))
            }
    }

    @Test
    fun set_overwriteEmbeddedRealmObject() {
        val parent =
            dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("EmbeddedParent"))
        parent.set("child", DynamicMutableRealmObject.create("EmbeddedChild", "id" to "child1"))
        dynamicMutableRealm.query("EmbeddedParent").find().single().run {
            assertEquals("child1", get<DynamicMutableRealmObject?>("child")!!.get<String?>("id"))
            parent.set("child", DynamicMutableRealmObject.create("EmbeddedChild", "id" to "child2"))
        }
        dynamicMutableRealm.query("EmbeddedChild")
            .find()
            .single()
            .run {
                assertEquals("child2", get<String?>("id"))
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
        assertEquals("UPDATED_PRIMARY_KEY", o.get("primaryKey"))
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
                assertEquals("INIT", get<String?>("name"))
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
                assertEquals("UPDATED", get<String?>("name"))
            }
    }

    // ---------------------------------------------------------------------
    // Lists
    // ---------------------------------------------------------------------

    @Test
    fun list_add_embeddedRealmObject() {
        val parent: DynamicMutableRealmObject =
            dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("EmbeddedParent"))
        parent.get<RealmList<DynamicMutableRealmObject>>("childrenList").add(
            DynamicMutableRealmObject.create(
                "EmbeddedChild",
                "subTree" to DynamicMutableRealmObject.create("EmbeddedParent", "id" to "subParent")
            )
        )

        dynamicMutableRealm.query("EmbeddedChild")
            .find()
            .single()
            .run {
                assertEquals("subParent", get<DynamicMutableRealmObject?>("subTree")!!.get<String?>("id"))
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
        parent.get<RealmList<DynamicMutableRealmObject>>("objectListField")
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
        parent.get<RealmList<DynamicMutableRealmObject>>("objectListField")
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
        parent.get<RealmList<DynamicMutableRealmObject>>("childrenList")
            .addAll(listOf(child, child))

        dynamicMutableRealm.query("EmbeddedChild").find().run {
            assertEquals(2, size)
            assertEquals("subParent", get(0).get<DynamicMutableRealmObject?>("subTree")!!.get<String?>("id"))
            assertEquals("subParent", get(1).get<DynamicMutableRealmObject?>("subTree")!!.get<String?>("id"))
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
        parent.get<RealmList<DynamicMutableRealmObject>>("objectListField")
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
        parent.get<RealmList<DynamicMutableRealmObject>>("objectListField").addAll(0, listOf(intermediate, intermediate))

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
        parent.get<RealmList<DynamicMutableRealmObject>>("objectListField").run {
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
        parent.get<RealmSet<DynamicMutableRealmObject>>("objectSetField")
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
        parent.get<RealmSet<DynamicMutableRealmObject>>("objectSetField")
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
        parent.get<RealmDictionary<DynamicMutableRealmObject?>>("childrenDictionary")["A"] =
            DynamicMutableRealmObject.create(
                "EmbeddedChild",
                "subTree" to DynamicMutableRealmObject.create("EmbeddedParent", "id" to "subParent")
            )

        dynamicMutableRealm.query("EmbeddedChild")
            .find()
            .single()
            .run {
                assertEquals("subParent", get<DynamicMutableRealmObject?>("subTree")!!.get<String?>("id"))
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
        parent.get<RealmDictionary<DynamicMutableRealmObject?>>("childrenDictionary")
            .putAll(listOf("A" to child, "B" to child))

        dynamicMutableRealm.query("EmbeddedChild").find().run {
            assertEquals(2, size)
            assertEquals("subParent", get(0).get<DynamicMutableRealmObject?>("subTree")!!.get<String?>("id"))
            assertEquals("subParent", get(1).get<DynamicMutableRealmObject?>("subTree")!!.get<String?>("id"))
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
            "nullableObjectDictionaryFieldNotNull" to realmDictionaryOf(
                "A" to child2,
                "B" to child2
            )
        )
        val parent = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        parent.get<RealmDictionary<DynamicMutableRealmObject?>>("nullableObjectDictionaryFieldNotNull").run {
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
            "nullableObjectDictionaryFieldNotNull" to realmDictionaryOf(
                "A" to child2,
                "B" to child2
            )
        )
        val parent = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        parent.get<RealmDictionary<DynamicMutableRealmObject?>>("nullableObjectDictionaryFieldNotNull").run {
            putAll(listOf("A" to child1, "B" to intermediate))
        }
        dynamicMutableRealm.query("Sample").find().run {
            assertEquals(4, size)
        }
    }

    @Test
    fun copyToRealm_embeddedObject_throws() {
        assertFailsWith<IllegalArgumentException> {
            dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("EmbeddedChild"))
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

inline fun <reified T> assertionsForValue(
    dynamicRealmObject: DynamicRealmObject,
    setFromSample: RealmSet<T>,
    property: RealmProperty,
    value: T,
    clazz: KClass<T & Any>
) {
    setFromSample.add(value)
    val setOfValue = dynamicRealmObject.get<RealmSet<T>>(property.name)
    assertEquals(1, setOfValue.size)
    assertTrue(setOfValue.contains(value))
}

inline fun <reified T> assertionsForNullable(
    dynamicRealmObject: DynamicRealmObject,
    listFromSample: RealmList<T?>,
    property: RealmProperty,
    value: T,
    clazz: KClass<T & Any>,
) {
    listFromSample.add(value)
    listFromSample.add(null)
    val listOfNullable: RealmList<T?> = dynamicRealmObject.get<RealmList<T?>>(
        property.name,
    )
    assertEquals(2, listOfNullable.size)
    assertEquals(value, listOfNullable[0])
    assertNull(listOfNullable[1])
}

inline fun <reified T> assertionsForValue(
    dynamicRealmObject: DynamicRealmObject,
    listFromSample: RealmList<T>,
    property: RealmProperty,
    value: T,
    clazz: KClass<T & Any>
) {
    listFromSample.add(value)
    val valueList = dynamicRealmObject.get<RealmList<T>>(
        property.name,
    )
    assertEquals(1, valueList.size)
    @Suppress("UNCHECKED_CAST")
    assertEquals(value, valueList[0] as T)
}
inline fun <reified T> assertionsForNullable(
    dynamicRealmObject: DynamicRealmObject,
    setFromSample: RealmSet<T?>,
    property: RealmProperty,
    value: T,
    clazz: KClass<T & Any>
) {
    setFromSample.add(value)
    setFromSample.add(null)
    val setOfNullable = dynamicRealmObject.get<RealmSet<T?>>(
        property.name,
//                                clazz
    )
    assertEquals(2, setOfNullable.size)
    assertTrue(setOfNullable.contains(value as Any?))
    assertTrue(setOfNullable.contains(null))
}
inline fun <reified T> assertionsForValue(
    dynamicRealmObject: DynamicRealmObject,
    dictionaryFromSample: RealmDictionary<T>,
    property: RealmProperty,
    value: T,
    clazz: KClass<T & Any>
) {
    dictionaryFromSample["A"] = value
    val dictionaryOfValue = dynamicRealmObject.get<RealmDictionary<T>>(
        property.name,
    )
    assertEquals(1, dictionaryOfValue.size)
    assertTrue(dictionaryOfValue.containsKey("A"))
    assertFalse(dictionaryOfValue.containsKey("B"))
    assertTrue(dictionaryOfValue.containsValue(value as T))
    assertTrue(
        dictionaryOfValue.entries
            .contains(realmDictionaryEntryOf("A" to value as T))
    )
}

inline fun <reified T> assertionsForNullable(
    dynamicRealmObject: DynamicRealmObject,
    dictionaryFromSample: RealmDictionary<T?>,
    property: RealmProperty,
    value: T,
    clazz: KClass<T & Any>
) {
    dictionaryFromSample["A"] = value
    dictionaryFromSample["B"] = null
    val dictionaryOfNullable = dynamicRealmObject.get<RealmDictionary<T?>>(
        property.name,
    )
    assertEquals(2, dictionaryOfNullable.size)
    assertTrue(dictionaryOfNullable.containsKey("A"))
    assertTrue(dictionaryOfNullable.containsKey("B"))
    assertFalse(dictionaryOfNullable.containsKey("C"))
    assertTrue(dictionaryOfNullable.containsValue(value as Any?))
    assertTrue(dictionaryOfNullable.containsValue(null))
    dictionaryOfNullable.entries.also { entries: MutableSet<MutableMap.MutableEntry<String, T?>> ->
        assertTrue(
            entries.contains(realmDictionaryEntryOf("A" to value as T?))
        )
        assertTrue(
            entries.contains(realmDictionaryEntryOf("B" to null))
        )
    }
}
