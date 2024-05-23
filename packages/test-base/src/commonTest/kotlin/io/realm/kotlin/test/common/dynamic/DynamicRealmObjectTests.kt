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

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
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
import io.realm.kotlin.ext.asFlow
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmDictionaryOf
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmSet
import io.realm.kotlin.internal.asDynamicRealm
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.schema.ListPropertyType
import io.realm.kotlin.schema.MapPropertyType
import io.realm.kotlin.schema.RealmPropertyType
import io.realm.kotlin.schema.RealmStorageType
import io.realm.kotlin.schema.SetPropertyType
import io.realm.kotlin.schema.ValuePropertyType
import io.realm.kotlin.test.common.utils.assertFailsWithMessage
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
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
                            RealmStorageType.DECIMAL128 -> {
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue<Decimal128>(property.name)
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
                                    dynamicSample.getNullableValue<BsonObjectId>(property.name)
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
                            RealmStorageType.ANY -> {
                                // The testing pattern doesn't work for RealmAny since we only land
                                // here in case the type is nullable and the expected value only
                                // takes into consideration the default value, which is null.
                                // However, we need to test it with different values.
                                // See 'get_realmAny()'
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
                            RealmStorageType.DECIMAL128 -> {
                                assertEquals(
                                    expectedSample.decimal128Field,
                                    dynamicSample.getValue(property.name)
                                )
                                assertEquals(
                                    expectedSample.decimal128Field,
                                    dynamicSample.getValue<Decimal128>(property.name)
                                )
                                assertEquals(
                                    expectedSample.decimal128Field,
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
                        fun <T> assertionsForNullable(listFromSample: RealmList<T?>) {
                            assertNull(listFromSample[0])
                        }

                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList<Boolean>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        Boolean::class
                                    )
                                )
                            }
                            RealmStorageType.INT -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList<Long>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        Long::class
                                    )
                                )
                            }
                            RealmStorageType.STRING -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList<String>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        String::class
                                    )
                                )
                            }
                            RealmStorageType.FLOAT -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList<Float>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        Float::class
                                    )
                                )
                            }
                            RealmStorageType.DOUBLE -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList<Double>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        Double::class
                                    )
                                )
                            }
                            RealmStorageType.DECIMAL128 -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList<Decimal128>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        Decimal128::class
                                    )
                                )
                            }
                            RealmStorageType.TIMESTAMP -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList<RealmInstant>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        RealmInstant::class
                                    )
                                )
                            }
                            RealmStorageType.OBJECT_ID -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList<BsonObjectId>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        BsonObjectId::class
                                    )
                                )
                            }
                            RealmStorageType.UUID -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList<RealmUUID>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        RealmUUID::class
                                    )
                                )
                            }
                            RealmStorageType.BINARY -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList<ByteArray>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        ByteArray::class
                                    )
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
                            RealmStorageType.ANY -> {
                                // The testing pattern doesn't work for RealmAny since we only land
                                // here in case the type is nullable and the expected value only
                                // takes into consideration the default value, which is an empty
                                // list.
                                // However, we need to test it with different values.
                                // See 'get_realmAnyList()'
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
                            RealmStorageType.DECIMAL128 -> {
                                val expectedValue = defaultSample.decimal128Field
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList<Decimal128>(property.name)[0]
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList(property.name, Decimal128::class)[0]
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
                        fun <T> assertionsForNullable(setFromSample: RealmSet<T?>) {
                            assertNull(setFromSample.first())
                        }

                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet<Boolean>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet(property.name, Boolean::class)
                                )
                            }
                            RealmStorageType.INT -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet<Long>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet(property.name, Long::class)
                                )
                            }
                            RealmStorageType.STRING -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet<String>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet(property.name, String::class)
                                )
                            }
                            RealmStorageType.FLOAT -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet<Float>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet(property.name, Float::class)
                                )
                            }
                            RealmStorageType.DOUBLE -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet<Double>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet(property.name, Double::class)
                                )
                            }
                            RealmStorageType.TIMESTAMP -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet<RealmInstant>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet(
                                        property.name,
                                        RealmInstant::class
                                    )
                                )
                            }
                            RealmStorageType.OBJECT_ID -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet<BsonObjectId>(
                                        property.name
                                    )
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet(
                                        property.name,
                                        BsonObjectId::class
                                    )
                                )
                            }
                            RealmStorageType.UUID -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet<RealmUUID>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet(property.name, RealmUUID::class)
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
                            RealmStorageType.DECIMAL128 -> {
                                assertNull(
                                    dynamicSample.getNullableValueSet<Decimal128>(property.name)
                                        .first()
                                )
                                assertNull(
                                    dynamicSample.getNullableValueSet(
                                        property.name,
                                        Decimal128::class
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
                            RealmStorageType.ANY -> {
                                // The testing pattern doesn't work for RealmAny since we only land
                                // here in case the type is nullable and the expected value only
                                // takes into consideration the default value, which is an empty
                                // set.
                                // However, we need to test it with different values.
                                // See 'get_realmAnySet()'
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        fun <T> assertionsForValue(setFromSample: RealmSet<T>, expectedValue: T) {
                            if (expectedValue is ByteArray) {
                                assertContentEquals(
                                    expectedValue,
                                    setFromSample.first() as ByteArray
                                )
                            } else {
                                assertEquals(expectedValue, setFromSample.first())
                            }
                        }

                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name),
                                    defaultSample.booleanField
                                )
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name, Boolean::class),
                                    defaultSample.booleanField
                                )
                            }
                            RealmStorageType.INT -> {
                                val expectedValue: Long = when (property.name) {
                                    Sample::byteSetField.name ->
                                        defaultSample.byteField.toLong()
                                    Sample::charSetField.name ->
                                        defaultSample.charField.code.toLong()
                                    Sample::shortSetField.name -> defaultSample.shortField.toLong()
                                    Sample::intSetField.name ->
                                        defaultSample.intField.toLong()
                                    Sample::longSetField.name ->
                                        defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name),
                                    expectedValue
                                )
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name, Long::class),
                                    expectedValue
                                )
                            }
                            RealmStorageType.STRING -> {
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name),
                                    defaultSample.stringField
                                )
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name, String::class),
                                    defaultSample.stringField
                                )
                            }
                            RealmStorageType.FLOAT -> {
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name),
                                    defaultSample.floatField
                                )
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name, Float::class),
                                    defaultSample.floatField
                                )
                            }
                            RealmStorageType.DOUBLE -> {
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name),
                                    defaultSample.doubleField
                                )
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name, Double::class),
                                    defaultSample.doubleField
                                )
                            }
                            RealmStorageType.TIMESTAMP -> {
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name),
                                    defaultSample.timestampField
                                )
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name, RealmInstant::class),
                                    defaultSample.timestampField
                                )
                            }
                            RealmStorageType.OBJECT_ID -> {
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name),
                                    defaultSample.bsonObjectIdField
                                )
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name, BsonObjectId::class),
                                    defaultSample.bsonObjectIdField
                                )
                            }
                            RealmStorageType.UUID -> {
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name),
                                    defaultSample.uuidField
                                )
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name, RealmUUID::class),
                                    defaultSample.uuidField
                                )
                            }
                            RealmStorageType.BINARY -> {
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name),
                                    defaultSample.binaryField
                                )
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name, ByteArray::class),
                                    defaultSample.binaryField
                                )
                            }
                            RealmStorageType.DECIMAL128 -> {
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name),
                                    defaultSample.decimal128Field
                                )
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name, Decimal128::class),
                                    defaultSample.decimal128Field
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
                is MapPropertyType -> {
                    if (type.isNullable) {
                        fun <T> assertionsForNullable(dictionaryFromSample: RealmDictionary<T?>) {
                            assertNull(dictionaryFromSample["A"])
                        }

                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary<Boolean>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary(
                                        property.name,
                                        Boolean::class
                                    )
                                )
                            }
                            RealmStorageType.INT -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary<Long>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary(
                                        property.name,
                                        Long::class
                                    )
                                )
                            }
                            RealmStorageType.STRING -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary<String>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary(
                                        property.name,
                                        String::class
                                    )
                                )
                            }
                            RealmStorageType.BINARY -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary<ByteArray>(
                                        property.name
                                    )
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary(
                                        property.name,
                                        ByteArray::class
                                    )
                                )
                            }
                            RealmStorageType.OBJECT -> when (property.name) {
                                "nullableObjectDictionaryFieldNull" -> {
                                    assertionsForNullable(
                                        dynamicSample.getNullableValueDictionary<DynamicRealmObject>(
                                            property.name
                                        )
                                    )
                                    assertionsForNullable(
                                        dynamicSample.getNullableValueDictionary(
                                            property.name,
                                            DynamicRealmObject::class
                                        )
                                    )
                                }
                                "nullableObjectDictionaryFieldNotNull" -> {
                                    dynamicSample.getNullableValueDictionary<DynamicRealmObject>(
                                        property.name
                                    ).also { dictionaryFromSample ->
                                        val inner = assertNotNull(dictionaryFromSample["A"])
                                        assertEquals("INNER", inner.getValue("stringField"))
                                    }
                                }
                            }
                            RealmStorageType.FLOAT -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary<Float>(
                                        property.name
                                    )
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary(
                                        property.name,
                                        Float::class
                                    )
                                )
                            }
                            RealmStorageType.DOUBLE -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary<Double>(
                                        property.name
                                    )
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary(
                                        property.name,
                                        Double::class
                                    )
                                )
                            }
                            RealmStorageType.DECIMAL128 -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary<Decimal128>(
                                        property.name
                                    )
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary(
                                        property.name,
                                        Decimal128::class
                                    )
                                )
                            }
                            RealmStorageType.TIMESTAMP -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary<RealmInstant>(
                                        property.name
                                    )
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary(
                                        property.name,
                                        RealmInstant::class
                                    )
                                )
                            }
                            RealmStorageType.OBJECT_ID -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary<BsonObjectId>(
                                        property.name
                                    )
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary(
                                        property.name,
                                        BsonObjectId::class
                                    )
                                )
                            }
                            RealmStorageType.UUID -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary<RealmUUID>(
                                        property.name
                                    )
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary(
                                        property.name,
                                        RealmUUID::class
                                    )
                                )
                            }
                            RealmStorageType.ANY -> {
                                // The testing pattern doesn't work for RealmAny since we only land
                                // here in case the type is nullable and the expected value only
                                // takes into consideration the default value, which is an empty
                                // set.
                                // However, we need to test it with different values.
                                // See 'get_realmAnySet()'
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        fun <T> assertionsForValue(
                            dictionaryFromSample: RealmDictionary<T>,
                            expectedValue: T
                        ) {
                            if (expectedValue is ByteArray) {
                                assertContentEquals(expectedValue, dictionaryFromSample["A"] as ByteArray)
                            } else {
                                assertEquals(expectedValue, dictionaryFromSample["A"])
                            }
                        }

                        when (type.storageType) {
                            RealmStorageType.BOOL -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                defaultSample.booleanField
                            )
                            RealmStorageType.INT -> {
                                val expectedValue: Long = when (property.name) {
                                    Sample::byteDictionaryField.name ->
                                        defaultSample.byteField.toLong()
                                    Sample::charDictionaryField.name ->
                                        defaultSample.charField.code.toLong()
                                    Sample::shortDictionaryField.name ->
                                        defaultSample.shortField.toLong()
                                    Sample::intDictionaryField.name ->
                                        defaultSample.intField.toLong()
                                    Sample::longDictionaryField.name ->
                                        defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                assertionsForValue(
                                    dynamicSample.getValueDictionary(property.name),
                                    expectedValue
                                )
                            }
                            RealmStorageType.STRING -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                defaultSample.stringField
                            )
                            RealmStorageType.BINARY -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                defaultSample.binaryField
                            )
                            RealmStorageType.OBJECT -> {
                                // No testing needed since dictionaries of objects can only be
                                // nullable and that has been tested above
                            }
                            RealmStorageType.FLOAT -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                defaultSample.floatField
                            )
                            RealmStorageType.DOUBLE -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                defaultSample.doubleField
                            )
                            RealmStorageType.DECIMAL128 -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                defaultSample.decimal128Field
                            )
                            RealmStorageType.TIMESTAMP -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                defaultSample.timestampField
                            )
                            RealmStorageType.OBJECT_ID -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                defaultSample.bsonObjectIdField
                            )
                            RealmStorageType.UUID -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                defaultSample.uuidField
                            )
                            RealmStorageType.ANY -> {
                                // Tested outside similarly to lists
                            }
                            else -> Unit
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
    fun get_realmAny() {
        // It's not possible to integrate in get_allTypes() since RealmAny can only be nullable in
        // the context of the test.
        // Provide at least the following types: null, primitive, RealmObject, DynamicRealmObject
        val realmAnyValues = listOf(
            null,
            RealmAny.create("Hello"),
            RealmAny.create(Sample().apply { stringField = "INNER" }),
        )

        for (expected in realmAnyValues) {
            val unmanagedSample = Sample().apply { nullableRealmAnyField = expected }
            realm.writeBlocking { copyToRealm(unmanagedSample) }
            val dynamicRealm = realm.asDynamicRealm()
            val dynamicSample = dynamicRealm.query("Sample")
                .find()
                .first()

            val actualReified = dynamicSample.getNullableValue<RealmAny>(Sample::nullableRealmAnyField.name)
            val actual = dynamicSample.getNullableValue(Sample::nullableRealmAnyField.name, RealmAny::class)

            // Test we throw if trying to retrieve the object using its actual class instead of
            // DynamicRealmObject
            if (actualReified?.type == RealmAny.Type.OBJECT) {
                assertFailsWith<ClassCastException> {
                    actualReified?.asRealmObject<Sample>()
                }
                assertFailsWith<ClassCastException> {
                    actual?.asRealmObject(Sample::class)
                }

                // Retrieve values now
                assertEquals(
                    expected?.asRealmObject<Sample>()?.stringField,
                    actualReified?.asRealmObject<DynamicRealmObject>()?.getValue("stringField")
                )
                assertEquals(
                    expected?.asRealmObject<Sample>()?.stringField,
                    actual?.asRealmObject<DynamicRealmObject>()?.getValue("stringField")
                )
            } else {
                assertEquals(expected, actualReified)
                assertEquals(expected, actual)
            }
            realm.writeBlocking { delete(query(Sample::class)) }
        }

        // The code is allowed by our semantics but is a rather obscure use case:
        // 1 - write an object using a regular realm
        // 2 - retrieve that object using a dynamic realm
        // 3 - wrap the dynamic object inside RealmAny and put it inside a container and save it to
        //     a regular realm
        // In principle we shouldn't allow dynamic objects to be written using a regular realm but
        // 'copyToRealm' isn't constrained to prevent it.

        // First write a container holding a RealmAny field wrapping a DynamicRealmObject
        val sample = Sample().apply { stringField = "INNER" }
        realm.writeBlocking { copyToRealm(sample) }
        realm.asDynamicRealm()
            .also { dynamicRealm ->
                // Retrieve the container we just stored
                val dynamicSample = dynamicRealm.query("Sample")
                    .find()
                    .first()
                realm.writeBlocking {
                    // Create a RealmAny instance with the INNER object - call the container OUTER
                    val latestDynamicSample = findLatest(dynamicSample)
                    val outer = Sample().apply {
                        stringField = "OUTER"
                        nullableRealmAnyField = RealmAny.create(latestDynamicSample!!)
                    }
                    copyToRealm(outer)
                }
            }

        // Then retrieve OUTER object and get the RealmAny value that contains the INNER DynamicRealmObject
        realm.asDynamicRealm()
            .also { dynamicRealm ->
                val dynamicInner = dynamicRealm.query("Sample", "stringField = $0", "OUTER")
                    .find()
                    .first()
                val actual = dynamicInner.getNullableValue<RealmAny>("nullableRealmAnyField")
                val actualObject = actual?.asRealmObject<DynamicRealmObject>()
                val actualString = actualObject?.getValue<String>("stringField")
                assertEquals(sample.stringField, actualString)
            }
    }

    @Test
    fun get_realmAnyList() {
        val realmAnyValues = realmListOf(
            null,
            RealmAny.create("Hello"),
            RealmAny.create(Sample().apply { stringField = "INNER" }),
        )

        val unmanagedSample = Sample().apply {
            nullableRealmAnyListField = realmAnyValues
        }
        realm.writeBlocking { copyToRealm(unmanagedSample) }
        realm.asDynamicRealm()
            .also { dynamicRealm ->
                val dynamicSample = dynamicRealm.query("Sample")
                    .find()
                    .first()

                val actualReifiedList = dynamicSample.getNullableValueList<RealmAny>(
                    Sample::nullableRealmAnyListField.name
                )
                val actualList = dynamicSample.getNullableValueList(
                    Sample::nullableRealmAnyListField.name,
                    RealmAny::class
                )
                for (i in realmAnyValues.indices) {
                    val expected = realmAnyValues[i]
                    val actualReified = actualReifiedList[i]
                    val actual = actualList[i]
                    if (actual?.type == RealmAny.Type.OBJECT) {
                        // Test we throw if trying to retrieve the object using its actual class instead of
                        // DynamicRealmObject
                        assertFailsWith<ClassCastException> {
                            actualReified?.asRealmObject<Sample>()
                        }
                        assertFailsWith<ClassCastException> {
                            actualReified?.asRealmObject(Sample::class)
                        }
                        assertFailsWith<ClassCastException> {
                            actual?.asRealmObject<Sample>()
                        }
                        assertFailsWith<ClassCastException> {
                            actual?.asRealmObject(Sample::class)
                        }

                        // Retrieve values now
                        assertEquals(
                            expected?.asRealmObject<Sample>()?.stringField,
                            actualReified?.asRealmObject<DynamicRealmObject>()?.getValue("stringField")
                        )
                        assertEquals(
                            expected?.asRealmObject<Sample>()?.stringField,
                            actual?.asRealmObject<DynamicRealmObject>()?.getValue("stringField")
                        )
                    } else {
                        assertEquals(expected, actualReified)
                        assertEquals(expected, actual)
                    }
                }
            }

        // In case of testing lists we have to skip dynamic managed objects inside a
        // RealmList<RealmAny> since the semantics prevent us from writing a DynamicRealmObject
        // in this way, rightfully so. The reason for this is that we use the regular realm's
        // accessors which go through the non-dynamic path so objects inside the list are expected
        // to be non-dynamic - the 'issueDynamicObject' flag is always false following this path.
        // This should be tested for DynamicMutableRealm instead.
    }

    @Test
    fun get_realmAny_nestedCollectionsInList() {
        val unmanagedSample = Sample().apply {
            nullableRealmAnyField = RealmAny.create(
                realmListOf(
                    RealmAny.create(
                        realmListOf(RealmAny.create(Sample().apply { stringField = "INNER_LIST" }))
                    ),
                    RealmAny.create(
                        realmDictionaryOf("key" to RealmAny.create(Sample().apply { stringField = "INNER_DICT" }))
                    ),
                )
            )
        }
        realm.writeBlocking { copyToRealm(unmanagedSample) }
        realm.asDynamicRealm()
            .also { dynamicRealm ->
                val dynamicSample = dynamicRealm.query("Sample")
                    .find()
                    .first()

                val actualList = dynamicSample.getNullableValue(
                    Sample::nullableRealmAnyField.name,
                    RealmAny::class
                )!!.asList()

                actualList[0]!!.let { innerList ->
                    val actualSample = innerList.asList()[0]!!.asRealmObject<DynamicRealmObject>()
                    assertIs<DynamicRealmObject>(actualSample)
                    assertEquals("INNER_LIST", actualSample.getValue("stringField"))
                }
                actualList[1]!!.let { innerDictionary ->
                    val actualSample =
                        innerDictionary.asDictionary()!!["key"]!!.asRealmObject<DynamicRealmObject>()
                    assertIs<DynamicRealmObject>(actualSample)
                    assertEquals("INNER_DICT", actualSample.getValue("stringField"))
                }
            }
    }

    @Test
    fun get_realmAnySet() {
        val realmAnyValues = realmListOf(
            null,
            RealmAny.create("Hello"),
            RealmAny.create(Sample().apply { stringField = "INNER" }),
        )

        val unmanagedSample = Sample().apply {
            nullableRealmAnySetField = realmAnyValues.toRealmSet()
        }
        realm.writeBlocking { copyToRealm(unmanagedSample) }
        realm.asDynamicRealm()
            .also { dynamicRealm ->
                val dynamicSample = dynamicRealm.query("Sample")
                    .find()
                    .first()

                val actualReifiedSet = dynamicSample.getNullableValueSet<RealmAny>(
                    Sample::nullableRealmAnySetField.name
                )
                val actualSet = dynamicSample.getNullableValueSet(
                    Sample::nullableRealmAnySetField.name,
                    RealmAny::class
                )

                fun assertions(actual: RealmAny?) {
                    if (actual?.type == RealmAny.Type.OBJECT) {
                        var assertionSucceeded = false
                        for (value in realmAnyValues) {
                            if (value?.type == RealmAny.Type.OBJECT) {
                                assertEquals(
                                    value.asRealmObject<Sample>().stringField,
                                    actual.asRealmObject<DynamicRealmObject>()
                                        .getValue("stringField")
                                )
                                assertionSucceeded = true
                                return
                            }
                        }
                        assertTrue(assertionSucceeded)
                    } else {
                        assertTrue(realmAnyValues.contains(actual))
                    }
                }

                for (actual in actualReifiedSet) {
                    assertions(actual)
                }
                for (actual in actualSet) {
                    assertions(actual)
                }
            }

        // In case of testing sets we have to skip dynamic managed objects inside a
        // RealmSet<RealmAny> since the semantics prevent us from writing a DynamicRealmObject
        // in this way, rightfully so. The reason for this is that we use the regular realm's
        // accessors which go through the non-dynamic path so objects inside the set are expected
        // to be non-dynamic - the 'issueDynamicObject' flag is always false following this path.
        // This should be tested for DynamicMutableRealm instead.
    }

    @Test
    fun get_realmAnyDictionary() {
        val realmAnyValues = realmDictionaryOf(
            "A" to null,
            "B" to RealmAny.create("Hello"),
            "C" to RealmAny.create(Sample().apply { stringField = "INNER" }),
        )

        val unmanagedSample = Sample().apply {
            nullableRealmAnyDictionaryField = realmAnyValues
        }
        realm.writeBlocking { copyToRealm(unmanagedSample) }
        realm.asDynamicRealm()
            .also { dynamicRealm ->
                val dynamicSample = dynamicRealm.query("Sample")
                    .find()
                    .first()

                val actualReifiedDictionary = dynamicSample.getNullableValueDictionary<RealmAny>(
                    Sample::nullableRealmAnyDictionaryField.name
                )
                val actualDictionary = dynamicSample.getNullableValueDictionary(
                    Sample::nullableRealmAnyDictionaryField.name,
                    RealmAny::class
                )
                for (entry in realmAnyValues.entries) {
                    val expected = realmAnyValues[entry.key]
                    val actualReified = actualReifiedDictionary[entry.key]
                    val actual = actualDictionary[entry.key]
                    if (actual?.type == RealmAny.Type.OBJECT) {
                        // Test we throw if trying to retrieve the object using its actual class instead of
                        // DynamicRealmObject
                        assertFailsWith<ClassCastException> {
                            actualReified?.asRealmObject<Sample>()
                        }
                        assertFailsWith<ClassCastException> {
                            actualReified?.asRealmObject(Sample::class)
                        }
                        assertFailsWith<ClassCastException> {
                            actual.asRealmObject<Sample>()
                        }
                        assertFailsWith<ClassCastException> {
                            actual.asRealmObject(Sample::class)
                        }

                        // Retrieve values now
                        assertEquals(
                            expected?.asRealmObject<Sample>()?.stringField,
                            actualReified?.asRealmObject<DynamicRealmObject>()?.getValue("stringField")
                        )
                        assertEquals(
                            expected?.asRealmObject<Sample>()?.stringField,
                            actual.asRealmObject<DynamicRealmObject>()?.getValue("stringField")
                        )
                    } else {
                        assertEquals(expected, actualReified)
                        assertEquals(expected, actual)
                    }
                }
            }

        // In case of testing dictionaries we have to skip dynamic managed objects inside a
        // RealmDictionary<RealmAny> since the semantics prevent us from writing a DynamicRealmObject
        // in this way, rightfully so. The reason for this is that we use the regular realm's
        // accessors which go through the non-dynamic path so objects inside the list are expected
        // to be non-dynamic - the 'issueDynamicObject' flag is always false following this path.
        // This should be tested for DynamicMutableRealm instead.
    }

    @Test
    fun get_realmAny_nestedCollectionsInDictionary() {
        val unmanagedSample = Sample().apply {
            nullableRealmAnyField = RealmAny.create(
                realmDictionaryOf(
                    "list" to RealmAny.create(
                        realmListOf(RealmAny.create(Sample().apply { stringField = "INNER_LIST" }))
                    ),
                    "dict" to RealmAny.create(
                        realmDictionaryOf("key" to RealmAny.create(Sample().apply { stringField = "INNER_DICT" }))
                    ),
                )
            )
        }
        realm.writeBlocking { copyToRealm(unmanagedSample) }
        realm.asDynamicRealm()
            .also { dynamicRealm ->
                val dynamicSample = dynamicRealm.query("Sample")
                    .find()
                    .first()

                val actualDictionary = dynamicSample.getNullableValue(
                    Sample::nullableRealmAnyField.name,
                    RealmAny::class
                )!!.asDictionary()

                actualDictionary["list"]!!.let { innerList ->
                    val innerSample = innerList.asList()[0]!!
                    val actualSample = innerSample.asRealmObject<DynamicRealmObject>()
                    assertIs<DynamicRealmObject>(actualSample)
                    assertEquals("INNER_LIST", actualSample.getValue("stringField"))

                    assertFailsWith<ClassCastException> {
                        innerSample.asRealmObject<Sample>()
                    }
                }
                actualDictionary["dict"]!!.let { innerDictionary ->
                    val innerSample = innerDictionary.asDictionary()!!["key"]!!
                    val actualSample =
                        innerSample.asRealmObject<DynamicRealmObject>()
                    assertIs<DynamicRealmObject>(actualSample)
                    assertEquals("INNER_DICT", actualSample.getValue("stringField"))

                    assertFailsWith<ClassCastException> {
                        innerSample.asRealmObject<Sample>()
                    }
                }
            }
    }

    @Test
    fun get_throwsOnUnknownName() {
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
    fun list_query() {
        realm.writeBlocking {
            copyToRealm(
                Sample().apply {
                    (1..5).forEach { objectListField.add(Sample().apply { intField = it }) }
                }
            )
        }
        val dynamicRealm = realm.asDynamicRealm()
        val dynamicSample = dynamicRealm.query("Sample").find().first()

        val results = dynamicSample.getObjectList("objectListField").query("intField > 2").find()
        assertEquals(3, results.size)
        results.forEach { assertTrue { it.getValue<Long>("intField") > 2 } }
    }

    @Test
    fun set_query() {
        realm.writeBlocking {
            copyToRealm(
                Sample().apply {
                    (1..5).forEach { objectSetField.add(Sample().apply { intField = it }) }
                }
            )
        }
        val dynamicRealm = realm.asDynamicRealm()
        val dynamicSample = dynamicRealm.query("Sample").find().first()

        val results = dynamicSample.getObjectSet("objectSetField").query("intField > 2").find()
        assertEquals(3, results.size)
        results.forEach { assertTrue { it.getValue<Long>("intField") > 2 } }
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

        assertFailsWithMessage<IllegalStateException>("Cannot modify managed List outside of a write transaction.") {
            dynamicSample.getValueList<String>("stringListField").add("IMMUTABLE_LIST_ELEMENT")
        }
        assertFailsWithMessage<IllegalStateException>("Cannot modify managed List outside of a write transaction.") {
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
            bsonObjectIdListField.add(defaultSample.bsonObjectIdField)
            uuidListField.add(defaultSample.uuidField)
            binaryListField.add(defaultSample.binaryField)
            decimal128ListField.add(defaultSample.decimal128Field)

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
            uuidSetField.add(defaultSample.uuidField)
            binarySetField.add(defaultSample.binaryField)
            decimal128SetField.add(defaultSample.decimal128Field)

            booleanDictionaryField["A"] = defaultSample.booleanField
            byteDictionaryField["A"] = defaultSample.byteField
            charDictionaryField["A"] = defaultSample.charField
            shortDictionaryField["A"] = defaultSample.shortField
            intDictionaryField["A"] = defaultSample.intField
            longDictionaryField["A"] = defaultSample.longField
            floatDictionaryField["A"] = defaultSample.floatField
            doubleDictionaryField["A"] = defaultSample.doubleField
            stringDictionaryField["A"] = defaultSample.stringField
            timestampDictionaryField["A"] = defaultSample.timestampField
            bsonObjectIdDictionaryField["A"] = defaultSample.bsonObjectIdField
            uuidDictionaryField["A"] = defaultSample.uuidField
            binaryDictionaryField["A"] = defaultSample.binaryField
            decimal128DictionaryField["A"] = defaultSample.decimal128Field

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
            nullableUUIDListField.add(null)
            nullableBinaryListField.add(null)
            nullableDecimal128ListField.add(null)

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
            nullableUUIDSetField.add(null)
            nullableBinarySetField.add(null)
            nullableDecimal128SetField.add(null)

            nullableStringDictionaryField["A"] = null
            nullableByteDictionaryField["A"] = null
            nullableCharDictionaryField["A"] = null
            nullableShortDictionaryField["A"] = null
            nullableIntDictionaryField["A"] = null
            nullableLongDictionaryField["A"] = null
            nullableBooleanDictionaryField["A"] = null
            nullableFloatDictionaryField["A"] = null
            nullableDoubleDictionaryField["A"] = null
            nullableTimestampDictionaryField["A"] = null
            nullableBsonObjectIdDictionaryField["A"] = null
            nullableUUIDDictionaryField["A"] = null
            nullableBinaryDictionaryField["A"] = null
            nullableDecimal128DictionaryField["A"] = null
            nullableObjectDictionaryFieldNull["A"] = null
            nullableObjectDictionaryFieldNotNull["A"] = Sample().apply { stringField = "INNER" }
        }
    }
}
