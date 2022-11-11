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

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.dynamic.getNullableValue
import io.realm.kotlin.dynamic.getNullableValueList
import io.realm.kotlin.dynamic.getNullableValueSet
import io.realm.kotlin.dynamic.getValue
import io.realm.kotlin.dynamic.getValueList
import io.realm.kotlin.dynamic.getValueSet
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.ext.asFlow
import io.realm.kotlin.internal.asDynamicRealm
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.schema.ListPropertyType
import io.realm.kotlin.schema.RealmPropertyType
import io.realm.kotlin.schema.RealmStorageType
import io.realm.kotlin.schema.SetPropertyType
import io.realm.kotlin.schema.ValuePropertyType
import io.realm.kotlin.test.assertFailsWithMessage
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

val defaultSample = Sample()

@Suppress("LargeClass")
class DynamicRealmObjectTests {

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            RealmConfiguration.Builder(schema = setOf(Sample::class))
                .directory(tmpDir)
                .build()

        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    // FIXME Should maybe go when all other tests are in place
    @Test
    fun dynamicRealm_smoketest() {
        realm.writeBlocking {
            copyToRealm(Sample()).apply {
                stringField = "Parent"
                nullableObject = Sample().apply { stringField = "Child" }
                stringListField.add("STRINGLISTELEMENT")
                objectListField.add(Sample().apply { stringField = "SAMPLELISTELEMENT" })
                objectListField[0]
            }
        }

        val dynamicRealm = realm.asDynamicRealm()

        // dynamic object query
        val query: RealmQuery<out DynamicRealmObject> =
            dynamicRealm.query(Sample::class.simpleName!!)
        val first: DynamicRealmObject? = query.first().find()
        assertNotNull(first)

        // type
        assertEquals("Sample", first.type)

        // get string
        val actual = first.getValue("stringField", String::class)
        assertEquals("Parent", actual)

        // get object
        val dynamicChild: DynamicRealmObject? = first.getObject("nullableObject")
        assertNotNull(dynamicChild)
        assertEquals("Child", dynamicChild.getValue("stringField"))
    }

    @Test
    fun type() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        val dynamicRealm = realm.asDynamicRealm()
        val dynamicSample = dynamicRealm.query("Sample").find().first()
        assertEquals("Sample", dynamicSample.type)
    }

    @Test
    @Suppress("LongMethod", "ComplexMethod", "NestedBlockDepth")
    fun get_allTypes() {
        val expectedSample = testSample()
        realm.writeBlocking {
            copyToRealm(expectedSample)
        }
        val dynamicRealm = realm.asDynamicRealm()
        val dynamicSample = dynamicRealm.query("Sample").find().first()

        val schema = dynamicRealm.schema()
        val sampleDescriptor = schema["Sample"]!!

        val properties = sampleDescriptor.properties
        for (property in properties) {
            val name: String = property.name
            when (val type: RealmPropertyType = property.type) {
                is ValuePropertyType -> {
                    if (type.isNullable) {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                assertEquals(null, dynamicSample.getNullableValue<Boolean>(name))
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue(name, type.storageType.kClass)
                                )
                            }
                            RealmStorageType.INT -> {
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue<Long>(property.name)
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue(
                                        property.name,
                                        type.storageType.kClass
                                    )
                                )
                            }
                            RealmStorageType.STRING -> {
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue<String>(property.name)
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue(
                                        property.name,
                                        type.storageType.kClass
                                    )
                                )
                            }
                            RealmStorageType.OBJECT -> {
                                assertEquals(null, dynamicSample.getObject(property.name))
                            }
                            RealmStorageType.FLOAT -> {
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue<Float>(property.name)
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue(
                                        property.name,
                                        type.storageType.kClass
                                    )
                                )
                            }
                            RealmStorageType.DOUBLE -> {
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue<Double>(property.name)
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue(
                                        property.name,
                                        type.storageType.kClass
                                    )
                                )
                            }
                            RealmStorageType.TIMESTAMP -> {
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue<RealmInstant>(property.name)
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue(
                                        property.name,
                                        type.storageType.kClass
                                    )
                                )
                            }
                            RealmStorageType.OBJECT_ID -> {
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue<ObjectId>(property.name)
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue(
                                        property.name,
                                        type.storageType.kClass
                                    )
                                )
                            }
                            RealmStorageType.UUID -> {
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue<RealmUUID>(property.name)
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue(
                                        property.name,
                                        type.storageType.kClass
                                    )
                                )
                            }
                            RealmStorageType.BINARY -> {
                                assertContentEquals(
                                    null,
                                    dynamicSample.getNullableValue<ByteArray>(property.name)
                                )
                                assertContentEquals(
                                    null,
                                    dynamicSample.getNullableValue(
                                        property.name,
                                        type.storageType.kClass
                                    ) as ByteArray?
                                )
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                assertEquals(
                                    expectedSample.booleanField,
                                    dynamicSample.getValue(name)
                                )
                                assertEquals(
                                    expectedSample.booleanField,
                                    dynamicSample.getValue<Boolean>(name)
                                )
                                assertEquals(
                                    expectedSample.booleanField,
                                    dynamicSample.getValue(property.name, type.storageType.kClass)
                                )
                            }
                            RealmStorageType.INT -> {
                                val expectedValue: Long = when (property.name) {
                                    "byteField" -> expectedSample.byteField.toLong()
                                    "charField" -> expectedSample.charField.code.toLong()
                                    "shortField" -> expectedSample.shortField.toLong()
                                    "intField" -> expectedSample.intField.toLong()
                                    "longField" -> expectedSample.longField
                                    "mutableRealmIntField" -> expectedSample.mutableRealmIntField.get()
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                assertEquals(expectedValue, dynamicSample.getValue(property.name))
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValue<Long>(property.name)
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValue(property.name, type.storageType.kClass)
                                )
                            }
                            RealmStorageType.STRING -> {
                                assertEquals(
                                    expectedSample.stringField,
                                    dynamicSample.getValue(property.name)
                                )
                                assertEquals(
                                    expectedSample.stringField,
                                    dynamicSample.getValue<String>(property.name)
                                )
                                assertEquals(
                                    expectedSample.stringField,
                                    dynamicSample.getValue(property.name, type.storageType.kClass)
                                )
                            }
                            RealmStorageType.FLOAT -> {
                                assertEquals(
                                    expectedSample.floatField,
                                    dynamicSample.getValue(property.name)
                                )
                                assertEquals(
                                    expectedSample.floatField,
                                    dynamicSample.getValue<Float>(property.name)
                                )
                                assertEquals(
                                    expectedSample.floatField,
                                    dynamicSample.getValue(property.name, type.storageType.kClass)
                                )
                            }
                            RealmStorageType.DOUBLE -> {
                                assertEquals(
                                    expectedSample.doubleField,
                                    dynamicSample.getValue(property.name)
                                )
                                assertEquals(
                                    expectedSample.doubleField,
                                    dynamicSample.getValue<Double>(property.name)
                                )
                                assertEquals(
                                    expectedSample.doubleField,
                                    dynamicSample.getValue(property.name, type.storageType.kClass)
                                )
                            }
                            RealmStorageType.TIMESTAMP -> {
                                assertEquals(
                                    expectedSample.timestampField,
                                    dynamicSample.getValue(property.name)
                                )
                                assertEquals(
                                    expectedSample.timestampField,
                                    dynamicSample.getValue<RealmInstant>(property.name)
                                )
                                assertEquals(
                                    expectedSample.timestampField,
                                    dynamicSample.getValue(property.name, type.storageType.kClass)
                                )
                            }
                            RealmStorageType.OBJECT_ID -> {
                                when (name) {
                                    Sample::objectIdField.name -> {
                                        assertEquals(
                                            expectedSample.objectIdField,
                                            dynamicSample.getValue(property.name)
                                        )
                                        assertEquals(
                                            expectedSample.objectIdField,
                                            dynamicSample.getValue<ObjectId>(property.name)
                                        )
                                        assertEquals(
                                            expectedSample.objectIdField,
                                            dynamicSample.getValue(
                                                property.name,
                                                ObjectId::class
                                            )
                                        )
                                    }
                                    Sample::bsonObjectIdField.name -> {
                                        assertEquals(
                                            expectedSample.bsonObjectIdField,
                                            dynamicSample.getValue(property.name)
                                        )
                                        assertEquals(
                                            expectedSample.bsonObjectIdField,
                                            dynamicSample.getValue<BsonObjectId>(property.name)
                                        )
                                        assertEquals(
                                            expectedSample.bsonObjectIdField,
                                            dynamicSample.getValue(
                                                property.name,
                                                type.storageType.kClass
                                            )
                                        )
                                    }
                                }
                            }
                            RealmStorageType.UUID -> {
                                assertEquals(
                                    expectedSample.uuidField,
                                    dynamicSample.getValue(property.name)
                                )
                                assertEquals(
                                    expectedSample.uuidField,
                                    dynamicSample.getValue<RealmUUID>(property.name)
                                )
                                assertEquals(
                                    expectedSample.uuidField,
                                    dynamicSample.getValue(property.name, type.storageType.kClass)
                                )
                            }
                            RealmStorageType.BINARY -> {
                                assertContentEquals(
                                    expectedSample.binaryField,
                                    dynamicSample.getValue(property.name)
                                )
                                assertContentEquals(
                                    expectedSample.binaryField,
                                    dynamicSample.getValue<ByteArray>(property.name)
                                )
                                assertContentEquals(
                                    expectedSample.binaryField,
                                    dynamicSample.getValue(
                                        property.name,
                                        type.storageType.kClass
                                    ) as ByteArray
                                )
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    }
                }
                is ListPropertyType -> {
                    if (type.isComputed) {
                        val linkingObjects = dynamicSample.getBacklinks(property.name)

                        when (property.name) {
                            Sample::objectBacklinks.name -> {
                                assertTrue(linkingObjects.isEmpty())
                            }
                            Sample::listBacklinks.name,
                            Sample::setBacklinks.name -> {
                                assertTrue(linkingObjects.isNotEmpty())
                                val expectedValue = defaultSample.stringField
                                assertEquals(
                                    expectedValue,
                                    linkingObjects.first().getValue("stringField")
                                )
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else if (type.isNullable) {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValueList<Boolean>(property.name)[0]
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        Boolean::class
                                    )[0]
                                )
                            }
                            RealmStorageType.INT -> {
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValueList<Long>(property.name)[0]
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        Long::class
                                    )[0]
                                )
                            }
                            RealmStorageType.STRING -> {
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValueList<String>(property.name)[0]
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        String::class
                                    )[0]
                                )
                            }
                            RealmStorageType.FLOAT -> {
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValueList<Float>(property.name)[0]
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        Float::class
                                    )[0]
                                )
                            }
                            RealmStorageType.DOUBLE -> {
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValueList<Double>(property.name)[0]
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        Double::class
                                    )[0]
                                )
                            }
                            RealmStorageType.TIMESTAMP -> {
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValueList<RealmInstant>(property.name)[0]
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        RealmInstant::class
                                    )[0]
                                )
                            }
                            RealmStorageType.OBJECT_ID -> {
                                when (name) {
                                    Sample::nullableObjectIdField.name -> {
                                        assertEquals(
                                            null,
                                            dynamicSample.getNullableValueList<ObjectId>(
                                                property.name
                                            )[0]
                                        )
                                        assertEquals(
                                            null,
                                            dynamicSample.getNullableValueList(
                                                property.name,
                                                ObjectId::class
                                            )[0]
                                        )
                                    }
                                    Sample::nullableBsonObjectIdField.name -> {
                                        assertEquals(
                                            null,
                                            dynamicSample.getNullableValueList<ObjectId>(property.name)[0]
                                        )
                                        assertEquals(
                                            null,
                                            dynamicSample.getNullableValueList(
                                                property.name,
                                                ObjectId::class
                                            )[0]
                                        )
                                    }
                                }
                            }
                            RealmStorageType.UUID -> {
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValueList<RealmUUID>(property.name)[0]
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        RealmUUID::class
                                    )[0]
                                )
                            }
                            RealmStorageType.BINARY -> {
                                assertContentEquals(
                                    null,
                                    dynamicSample.getNullableValueList<ByteArray>(property.name)[0]
                                )
                                assertContentEquals(
                                    null,
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        ByteArray::class
                                    )[0]
                                )
                            }
                            RealmStorageType.OBJECT -> {
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValueList<DynamicRealmObject>(property.name)[0]
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        DynamicRealmObject::class
                                    )[0]
                                )
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                val expectedValue = defaultSample.booleanField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList<Boolean>(property.name)[0]
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList(property.name, Boolean::class)[0]
                                )
                            }
                            RealmStorageType.INT -> {
                                val expectedValue: Long? = when (property.name) {
                                    "byteListField" -> defaultSample.byteField.toLong()
                                    "charListField" -> defaultSample.charField.code.toLong()
                                    "shortListField" -> defaultSample.shortField.toLong()
                                    "intListField" -> defaultSample.intField.toLong()
                                    "longListField" -> defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList<Long>(property.name)[0]
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList(property.name, Long::class)[0]
                                )
                            }
                            RealmStorageType.STRING -> {
                                val expectedValue = defaultSample.stringField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList<String>(property.name)[0]
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList(property.name, String::class)[0]
                                )
                            }
                            RealmStorageType.FLOAT -> {
                                val expectedValue = defaultSample.floatField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList<Float>(property.name)[0]
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList(property.name, Float::class)[0]
                                )
                            }
                            RealmStorageType.DOUBLE -> {
                                val expectedValue = defaultSample.doubleField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList<Double>(property.name)[0]
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList(property.name, Double::class)[0]
                                )
                            }
                            RealmStorageType.TIMESTAMP -> {
                                val expectedValue = defaultSample.timestampField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList<RealmInstant>(property.name)[0]
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList(
                                        property.name,
                                        RealmInstant::class
                                    )[0]
                                )
                            }
                            RealmStorageType.OBJECT_ID -> {
                                when (name) {
                                    Sample::objectIdListField.name -> {
                                        val expectedValue = defaultSample.objectIdField
                                        assertEquals(
                                            expectedValue,
                                            dynamicSample.getValueList<ObjectId>(property.name)[0]
                                        )
                                        assertEquals(
                                            expectedValue,
                                            dynamicSample.getValueList(
                                                property.name,
                                                ObjectId::class
                                            )[0]
                                        )
                                    }
                                    Sample::bsonObjectIdListField.name -> {
                                        val expectedValue = defaultSample.bsonObjectIdField
                                        assertEquals(
                                            expectedValue,
                                            dynamicSample.getValueList<BsonObjectId>(property.name)[0]
                                        )
                                        assertEquals(
                                            expectedValue,
                                            dynamicSample.getValueList(
                                                property.name,
                                                BsonObjectId::class
                                            )[0]
                                        )
                                    }
                                }
                            }
                            RealmStorageType.UUID -> {
                                val expectedValue = defaultSample.uuidField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList<RealmUUID>(property.name)[0]
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList(property.name, RealmUUID::class)[0]
                                )
                            }
                            RealmStorageType.BINARY -> {
                                val expectedValue = defaultSample.binaryField
                                assertContentEquals(
                                    expectedValue,
                                    dynamicSample.getValueList<ByteArray>(property.name)[0]
                                )
                                assertContentEquals(
                                    expectedValue,
                                    dynamicSample.getValueList(property.name, ByteArray::class)[0]
                                )
                            }
                            RealmStorageType.OBJECT -> {
                                val expectedValue = defaultSample.stringField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList<DynamicRealmObject>(property.name)[0].getValue(
                                        "stringField"
                                    )
                                )
                                assertEquals(
                                    expectedValue,
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
                                assertNull(
                                    dynamicSample.getNullableValueSet<Boolean>(property.name)
                                        .first()
                                )
                                assertNull(
                                    dynamicSample.getNullableValueSet(
                                        property.name,
                                        Boolean::class
                                    ).first()
                                )
                            }
                            RealmStorageType.INT -> {
                                assertNull(
                                    dynamicSample.getNullableValueSet<Long>(property.name).first()
                                )
                                assertNull(
                                    dynamicSample.getNullableValueSet(
                                        property.name,
                                        Long::class
                                    ).first()
                                )
                            }
                            RealmStorageType.STRING -> {
                                assertNull(
                                    dynamicSample.getNullableValueSet<String>(property.name).first()
                                )
                                assertNull(
                                    dynamicSample.getNullableValueSet(
                                        property.name,
                                        String::class
                                    ).first()
                                )
                            }
                            RealmStorageType.FLOAT -> {
                                assertNull(
                                    dynamicSample.getNullableValueSet<Float>(property.name).first()
                                )
                                assertNull(
                                    dynamicSample.getNullableValueSet(
                                        property.name,
                                        Float::class
                                    ).first()
                                )
                            }
                            RealmStorageType.DOUBLE -> {
                                assertNull(
                                    dynamicSample.getNullableValueSet<Double>(property.name).first()
                                )
                                assertNull(
                                    dynamicSample.getNullableValueSet(
                                        property.name,
                                        Double::class
                                    ).first()
                                )
                            }
                            RealmStorageType.TIMESTAMP -> {
                                assertNull(
                                    dynamicSample.getNullableValueSet<RealmInstant>(property.name)
                                        .first()
                                )
                                assertNull(
                                    dynamicSample.getNullableValueSet(
                                        property.name,
                                        RealmInstant::class
                                    ).first()
                                )
                            }
                            RealmStorageType.OBJECT_ID -> {
                                when (name) {
                                    Sample::nullableObjectIdSetField.name -> {
                                        assertNull(
                                            dynamicSample.getNullableValueSet<ObjectId>(
                                                property.name
                                            ).first()
                                        )
                                        assertNull(
                                            dynamicSample.getNullableValueSet(
                                                property.name,
                                                ObjectId::class
                                            ).first()
                                        )
                                    }
                                    Sample::nullableBsonObjectIdSetField.name -> {
                                        assertNull(
                                            dynamicSample.getNullableValueSet<ObjectId>(
                                                property.name
                                            ).first()
                                        )
                                        assertNull(
                                            dynamicSample.getNullableValueSet(
                                                property.name,
                                                ObjectId::class
                                            ).first()
                                        )
                                    }
                                }
                            }
                            RealmStorageType.UUID -> {
                                assertNull(
                                    dynamicSample.getNullableValueSet<RealmUUID>(property.name)
                                        .first()
                                )
                                assertNull(
                                    dynamicSample.getNullableValueSet(
                                        property.name,
                                        RealmUUID::class
                                    ).first()
                                )
                            }
                            RealmStorageType.BINARY -> {
                                assertContentEquals(
                                    null,
                                    dynamicSample.getNullableValueSet<ByteArray>(property.name)
                                        .first()
                                )
                                assertContentEquals(
                                    null,
                                    dynamicSample.getNullableValueSet(
                                        property.name,
                                        ByteArray::class
                                    ).first()
                                )
                            }
                            RealmStorageType.OBJECT -> {
                                assertNull(
                                    dynamicSample.getNullableValueSet<DynamicRealmObject>(
                                        property.name
                                    ).first()
                                )
                                assertNull(
                                    dynamicSample.getNullableValueSet(
                                        property.name,
                                        DynamicRealmObject::class
                                    ).first()
                                )
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                val expectedValue = defaultSample.booleanField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueSet<Boolean>(property.name).first()
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueSet(property.name, Boolean::class).first()
                                )
                            }
                            RealmStorageType.INT -> {
                                val expectedValue: Long? = when (property.name) {
                                    "byteSetField" -> defaultSample.byteField.toLong()
                                    "charSetField" -> defaultSample.charField.code.toLong()
                                    "shortSetField" -> defaultSample.shortField.toLong()
                                    "intSetField" -> defaultSample.intField.toLong()
                                    "longSetField" -> defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueSet<Long>(property.name).first()
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueSet(property.name, Long::class).first()
                                )
                            }
                            RealmStorageType.STRING -> {
                                val expectedValue = defaultSample.stringField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueSet<String>(property.name).first()
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueSet(property.name, String::class).first()
                                )
                            }
                            RealmStorageType.FLOAT -> {
                                val expectedValue = defaultSample.floatField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueSet<Float>(property.name).first()
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueSet(property.name, Float::class).first()
                                )
                            }
                            RealmStorageType.DOUBLE -> {
                                val expectedValue = defaultSample.doubleField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueSet<Double>(property.name).first()
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueSet(property.name, Double::class).first()
                                )
                            }
                            RealmStorageType.TIMESTAMP -> {
                                val expectedValue = defaultSample.timestampField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueSet<RealmInstant>(property.name).first()
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueSet(property.name, RealmInstant::class)
                                        .first()
                                )
                            }
                            RealmStorageType.OBJECT_ID -> {
                                when (name) {
                                    Sample::objectIdSetField.name -> {
                                        val expectedValue = defaultSample.objectIdField
                                        assertEquals(
                                            expectedValue,
                                            dynamicSample.getValueSet<ObjectId>(property.name)
                                                .first()
                                        )
                                        assertEquals(
                                            expectedValue,
                                            dynamicSample.getValueSet(
                                                property.name,
                                                ObjectId::class
                                            ).first()
                                        )
                                    }
                                    Sample::bsonObjectIdSetField.name -> {
                                        val expectedValue = defaultSample.bsonObjectIdField
                                        assertEquals(
                                            expectedValue,
                                            dynamicSample.getValueSet<BsonObjectId>(property.name)
                                                .first()
                                        )
                                        assertEquals(
                                            expectedValue,
                                            dynamicSample.getValueSet(
                                                property.name,
                                                BsonObjectId::class
                                            ).first()
                                        )
                                    }
                                }
                            }
                            RealmStorageType.UUID -> {
                                val expectedValue = defaultSample.uuidField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueSet<RealmUUID>(property.name).first()
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueSet(property.name, RealmUUID::class)
                                        .first()
                                )
                            }
                            RealmStorageType.BINARY -> {
                                val expectedValue = defaultSample.binaryField
                                assertContentEquals(
                                    expectedValue,
                                    dynamicSample.getValueSet<ByteArray>(property.name).first()
                                )
                                assertContentEquals(
                                    expectedValue,
                                    dynamicSample.getValueSet(property.name, ByteArray::class)
                                        .first()
                                )
                            }
                            RealmStorageType.OBJECT -> {
                                val expectedValue = defaultSample.stringField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueSet<DynamicRealmObject>(property.name)
                                        .first().getValue("stringField")
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueSet(
                                        property.name,
                                        DynamicRealmObject::class
                                    ).first().getValue("stringField")
                                )
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    }
                }
                else -> {
                    // Required `else` branch due to https://youtrack.jetbrains.com/issue/KTIJ-18702
                    fail("Unknown type: $type")
                }
            }
            // TODO There is currently nothing that assert that we have tested all type
            // assertTrue("Untested types: $untested") { untested.isEmpty() }
        }
    }

    @Test
    fun get_throwsOnUnkownName() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        val dynamicRealm = realm.asDynamicRealm()
        val dynamicSample = dynamicRealm.query("Sample").find().first()
        assertFailsWithMessage<IllegalArgumentException>("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_FIELD'") {
            dynamicSample.getValue<String>("UNKNOWN_FIELD")
        }
        assertFailsWithMessage<IllegalArgumentException>("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_FIELD'") {
            dynamicSample.getNullableValue<String>("UNKNOWN_FIELD")
        }
        assertFailsWithMessage<IllegalArgumentException>("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_FIELD'") {
            dynamicSample.getObject("UNKNOWN_FIELD")
        }
        assertFailsWithMessage<IllegalArgumentException>("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_FIELD'") {
            dynamicSample.getValueList<String>("UNKNOWN_FIELD")
        }
        assertFailsWithMessage<IllegalArgumentException>("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_FIELD'") {
            dynamicSample.getNullableValueList<String>("UNKNOWN_FIELD")
        }
        assertFailsWithMessage<IllegalArgumentException>("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_FIELD'") {
            dynamicSample.getObjectList("UNKNOWN_FIELD")
        }
    }

    @Test
    fun getValueVariants_throwsOnWrongTypes() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        val dynamicRealm = realm.asDynamicRealm()
        val dynamicSample = dynamicRealm.query("Sample").find().first()

        // Wrong type
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.stringField' as type: 'class kotlin.Long' but actual schema type is 'class kotlin.String'") {
            dynamicSample.getValue<Long>("stringField")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableStringField' as type: 'class kotlin.Long?' but actual schema type is 'class kotlin.String?'") {
            dynamicSample.getNullableValue<Long>("nullableStringField")
        }

        // Wrong nullability
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableStringField' as type: 'class kotlin.Long' but actual schema type is 'class kotlin.String?'") {
            dynamicSample.getValue<Long>("nullableStringField")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.stringField' as type: 'RealmList<class kotlin.Long?>' but actual schema type is 'class kotlin.String'") {
            dynamicSample.getNullableValueList<Long>("stringField")
        }

        // Wrong variants
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableObject' as type: 'class kotlin.String' but actual schema type is 'class io.realm.kotlin.types.BaseRealmObject?'") {
            dynamicSample.getValue<String>("nullableObject")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableObject' as type: 'class kotlin.String?' but actual schema type is 'class io.realm.kotlin.types.BaseRealmObject?'") {
            dynamicSample.getNullableValue<String>("nullableObject")
        }

        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.stringListField' as type: 'class kotlin.Long' but actual schema type is 'RealmList<class kotlin.String>'") {
            dynamicSample.getValue<Long>("stringListField")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableStringListField' as type: 'class kotlin.Long?' but actual schema type is 'RealmList<class kotlin.String?>'") {
            dynamicSample.getNullableValue<Long>("nullableStringListField")
        }
    }

    @Test
    fun getObject_throwsOnWrongTypes() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        val dynamicRealm = realm.asDynamicRealm()
        val dynamicSample = dynamicRealm.query("Sample").find().first()

        // We cannot get wrong mix of types or nullability in the API, so only checking wrong variants
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.stringField' as type: 'class io.realm.kotlin.types.BaseRealmObject?' but actual schema type is 'class kotlin.String'") {
            dynamicSample.getObject("stringField")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.stringListField' as type: 'class io.realm.kotlin.types.BaseRealmObject?' but actual schema type is 'RealmList<class kotlin.String>'") {
            dynamicSample.getObject("stringListField")
        }
    }

    @Test
    fun getListVariants_throwsOnWrongTypes() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        val dynamicRealm = realm.asDynamicRealm()
        val dynamicSample = dynamicRealm.query("Sample").find().first()

        // Wrong type
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.stringListField' as type: 'RealmList<class kotlin.Long>' but actual schema type is 'RealmList<class kotlin.String>'") {
            dynamicSample.getValueList<Long>("stringListField")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableStringListField' as type: 'RealmList<class kotlin.Long?>' but actual schema type is 'RealmList<class kotlin.String?>'") {
            dynamicSample.getNullableValueList<Long>("nullableStringListField")
        }

        // Wrong nullability
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableStringListField' as type: 'RealmList<class kotlin.String>' but actual schema type is 'RealmList<class kotlin.String?>'") {
            dynamicSample.getValueList<String>("nullableStringListField")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.stringListField' as type: 'RealmList<class kotlin.String?>' but actual schema type is 'RealmList<class kotlin.String>'") {
            dynamicSample.getNullableValueList<String>("stringListField")
        }

        // Wrong variants
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.stringField' as type: 'RealmList<class kotlin.String>' but actual schema type is 'class kotlin.String'") {
            dynamicSample.getValueList<String>("stringField")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableObject' as type: 'RealmList<class kotlin.Long>' but actual schema type is 'class io.realm.kotlin.types.BaseRealmObject?'") {
            dynamicSample.getValueList<Long>("nullableObject")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableStringField' as type: 'RealmList<class kotlin.Long?>' but actual schema type is 'class kotlin.String?'") {
            dynamicSample.getNullableValueList<Long>("nullableStringField")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableStringListField' as type: 'RealmList<class io.realm.kotlin.types.BaseRealmObject>' but actual schema type is 'RealmList<class kotlin.String?>'") {
            dynamicSample.getObjectList("nullableStringListField")
        }
    }

    // We don't have an immutable RealmList so verify that we fail in an understandable manner if
    // trying to update the list
    @Test
    fun getValueList_throwsIfModified() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        val dynamicRealm = realm.asDynamicRealm()
        val dynamicSample = dynamicRealm.query("Sample").find().first()

        assertFailsWithMessage<IllegalStateException>("Cannot modify managed objects outside of a write transaction") {
            dynamicSample.getValueList<String>("stringListField").add("IMMUTABLE_LIST_ELEMENT")
        }
        assertFailsWithMessage<IllegalStateException>("Cannot modify managed objects outside of a write transaction") {
            dynamicSample.getNullableValueList<String>("nullableStringListField")
                .add("IMMUTABLE_LIST_ELEMENT")
        }
    }

    @Test
    fun observe_throws() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        val dynamicRealm = realm.asDynamicRealm()
        val query: RealmQuery<out DynamicRealmObject> =
            dynamicRealm.query(Sample::class.simpleName!!)
        val dynamicRealmObject: DynamicRealmObject = query.first().find()!!

        assertFailsWith<UnsupportedOperationException> {
            dynamicRealmObject.asFlow()
        }
    }

    @Test
    fun accessAfterCloseThrows() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        val dynamicRealm = realm.asDynamicRealm()
        // dynamic object query
        val query: RealmQuery<out DynamicRealmObject> =
            dynamicRealm.query(Sample::class.simpleName!!)
        val first: DynamicRealmObject = query.first().find()!!

        realm.close()

        assertFailsWithMessage<IllegalStateException>("Cannot perform this operation on an invalid/deleted object") {
            first.getValue<DynamicRealmObject>("stringField")
        }
    }

    @Suppress("LongMethod")
    private fun testSample(): Sample {
        return Sample().apply {
            booleanListField.add(defaultSample.booleanField)
            byteListField.add(defaultSample.byteField)
            charListField.add(defaultSample.charField)
            shortListField.add(defaultSample.shortField)
            intListField.add(defaultSample.intField)
            longListField.add(defaultSample.longField)
            floatListField.add(defaultSample.floatField)
            doubleListField.add(defaultSample.doubleField)
            stringListField.add(defaultSample.stringField)
            objectListField.add(this)
            timestampListField.add(defaultSample.timestampField)
            objectIdListField.add(defaultSample.objectIdField)
            bsonObjectIdListField.add(defaultSample.bsonObjectIdField)
            uuidListField.add(defaultSample.uuidField)
            binaryListField.add(defaultSample.binaryField)

            booleanSetField.add(defaultSample.booleanField)
            byteSetField.add(defaultSample.byteField)
            charSetField.add(defaultSample.charField)
            shortSetField.add(defaultSample.shortField)
            intSetField.add(defaultSample.intField)
            longSetField.add(defaultSample.longField)
            floatSetField.add(defaultSample.floatField)
            doubleSetField.add(defaultSample.doubleField)
            stringSetField.add(defaultSample.stringField)
            objectSetField.add(this)
            timestampSetField.add(defaultSample.timestampField)
            bsonObjectIdSetField.add(defaultSample.bsonObjectIdField)
            objectIdSetField.add(defaultSample.objectIdField)
            uuidSetField.add(defaultSample.uuidField)
            binarySetField.add(defaultSample.binaryField)

            nullableStringListField.add(null)
            nullableByteListField.add(null)
            nullableCharListField.add(null)
            nullableShortListField.add(null)
            nullableIntListField.add(null)
            nullableLongListField.add(null)
            nullableBooleanListField.add(null)
            nullableFloatListField.add(null)
            nullableDoubleListField.add(null)
            nullableTimestampListField.add(null)
            nullableBsonObjectIdListField.add(null)
            nullableObjectIdListField.add(null)
            nullableUUIDListField.add(null)
            nullableBinaryListField.add(null)

            nullableStringSetField.add(null)
            nullableByteSetField.add(null)
            nullableCharSetField.add(null)
            nullableShortSetField.add(null)
            nullableIntSetField.add(null)
            nullableLongSetField.add(null)
            nullableBooleanSetField.add(null)
            nullableFloatSetField.add(null)
            nullableDoubleSetField.add(null)
            nullableTimestampSetField.add(null)
            nullableBsonObjectIdSetField.add(null)
            nullableObjectIdSetField.add(null)
            nullableUUIDSetField.add(null)
            nullableBinarySetField.add(null)
        }
    }
}
