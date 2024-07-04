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
import io.realm.kotlin.dynamic.get
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
        val actual = first.get("stringField", String::class)
        assertEquals("Parent", actual)

        // get object
        val dynamicChild: DynamicRealmObject? = first.get<DynamicRealmObject?>("nullableObject")
        assertNotNull(dynamicChild)
        assertEquals("Child", dynamicChild.get("stringField"))
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
                                assertEquals(null, dynamicSample.get<Boolean?>(name))
                                assertEquals(
                                    null,
                                    dynamicSample.get<Boolean?>(name, Boolean::class)
                                )
                            }
                            RealmStorageType.INT -> {
                                assertEquals(
                                    null,
                                    dynamicSample.get<Long?>(property.name)
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.get<Long?>(
                                        property.name,
                                        Long::class
                                    )
                                )
                            }
                            RealmStorageType.STRING -> {
                                assertEquals(
                                    null,
                                    dynamicSample.get<String?>(property.name)
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.get<String?>(
                                        property.name,
                                        String::class
                                    )
                                )
                            }
                            RealmStorageType.OBJECT -> {
                                assertEquals(null, dynamicSample.get<DynamicRealmObject?>(property.name))
                            }
                            RealmStorageType.FLOAT -> {
                                assertEquals(
                                    null,
                                    dynamicSample.get<Float?>(property.name)
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.get<Float?>(
                                        property.name,
                                        Float::class
                                    )
                                )
                            }
                            RealmStorageType.DOUBLE -> {
                                assertEquals(
                                    null,
                                    dynamicSample.get<Double?>(property.name)
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.get<Double?>(
                                        property.name,
                                        Double::class
                                    )
                                )
                            }
                            RealmStorageType.DECIMAL128 -> {
                                assertEquals(
                                    null,
                                    dynamicSample.get<Decimal128?>(property.name)
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.get<Decimal128?>(
                                        property.name,
                                        Decimal128::class
                                    )
                                )
                            }
                            RealmStorageType.TIMESTAMP -> {
                                assertEquals(
                                    null,
                                    dynamicSample.get<RealmInstant?>(property.name)
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.get<RealmInstant?>(
                                        property.name,
                                        RealmInstant::class
                                    )
                                )
                            }
                            RealmStorageType.OBJECT_ID -> {
                                assertEquals(
                                    null,
                                    dynamicSample.get<BsonObjectId?>(property.name)
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.get<BsonObjectId?>(
                                        property.name,
                                        BsonObjectId::class
                                    )
                                )
                            }
                            RealmStorageType.UUID -> {
                                assertEquals(
                                    null,
                                    dynamicSample.get<RealmUUID?>(property.name)
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.get<RealmUUID?>(
                                        property.name,
                                        RealmUUID::class
                                    )
                                )
                            }
                            RealmStorageType.BINARY -> {
                                assertContentEquals(
                                    null,
                                    dynamicSample.get<ByteArray?>(property.name)
                                )
                                assertContentEquals(
                                    null,
                                    dynamicSample.get<ByteArray?>(
                                        property.name,
                                        ByteArray::class
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
                                    dynamicSample.get(name)
                                )
                                assertEquals(
                                    expectedSample.booleanField,
                                    dynamicSample.get<Boolean>(name)
                                )
                                assertEquals(
                                    expectedSample.booleanField,
                                    dynamicSample.get(property.name, Boolean::class)
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
                                assertEquals(expectedValue, dynamicSample.get(property.name))
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.get<Long>(property.name)
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.get(property.name, Long::class)
                                )
                            }
                            RealmStorageType.STRING -> {
                                assertEquals(
                                    expectedSample.stringField,
                                    dynamicSample.get(property.name)
                                )
                                assertEquals(
                                    expectedSample.stringField,
                                    dynamicSample.get<String>(property.name)
                                )
                                assertEquals(
                                    expectedSample.stringField,
                                    dynamicSample.get(property.name, String::class)
                                )
                            }
                            RealmStorageType.FLOAT -> {
                                assertEquals(
                                    expectedSample.floatField,
                                    dynamicSample.get(property.name)
                                )
                                assertEquals(
                                    expectedSample.floatField,
                                    dynamicSample.get<Float>(property.name)
                                )
                                assertEquals(
                                    expectedSample.floatField,
                                    dynamicSample.get(property.name, Float::class)
                                )
                            }
                            RealmStorageType.DOUBLE -> {
                                assertEquals(
                                    expectedSample.doubleField,
                                    dynamicSample.get(property.name)
                                )
                                assertEquals(
                                    expectedSample.doubleField,
                                    dynamicSample.get<Double>(property.name)
                                )
                                assertEquals(
                                    expectedSample.doubleField,
                                    dynamicSample.get(property.name, Double::class)
                                )
                            }
                            RealmStorageType.DECIMAL128 -> {
                                assertEquals(
                                    expectedSample.decimal128Field,
                                    dynamicSample.get(property.name)
                                )
                                assertEquals(
                                    expectedSample.decimal128Field,
                                    dynamicSample.get<Decimal128>(property.name)
                                )
                                assertEquals(
                                    expectedSample.decimal128Field,
                                    dynamicSample.get(property.name, Decimal128::class)
                                )
                            }
                            RealmStorageType.TIMESTAMP -> {
                                assertEquals(
                                    expectedSample.timestampField,
                                    dynamicSample.get(property.name)
                                )
                                assertEquals(
                                    expectedSample.timestampField,
                                    dynamicSample.get<RealmInstant>(property.name)
                                )
                                assertEquals(
                                    expectedSample.timestampField,
                                    dynamicSample.get(property.name, RealmInstant::class)
                                )
                            }
                            RealmStorageType.OBJECT_ID -> {
                                assertEquals(
                                    expectedSample.bsonObjectIdField,
                                    dynamicSample.get(property.name)
                                )
                                assertEquals(
                                    expectedSample.bsonObjectIdField,
                                    dynamicSample.get<BsonObjectId>(property.name)
                                )
                                assertEquals(
                                    expectedSample.bsonObjectIdField,
                                    dynamicSample.get(
                                        property.name,
                                        BsonObjectId::class
                                    )
                                )
                            }
                            RealmStorageType.UUID -> {
                                assertEquals(
                                    expectedSample.uuidField,
                                    dynamicSample.get(property.name)
                                )
                                assertEquals(
                                    expectedSample.uuidField,
                                    dynamicSample.get<RealmUUID>(property.name)
                                )
                                assertEquals(
                                    expectedSample.uuidField,
                                    dynamicSample.get(property.name, RealmUUID::class)
                                )
                            }
                            RealmStorageType.BINARY -> {
                                assertContentEquals(
                                    expectedSample.binaryField,
                                    dynamicSample.get<ByteArray>(property.name)
                                )
                                assertContentEquals(
                                    expectedSample.binaryField,
                                    dynamicSample.get(property.name, ByteArray::class)
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
                                    linkingObjects.first().get("stringField")
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
                                    dynamicSample.get<RealmList<Boolean?>>(property.name)
                                )
//                                assertionsForNullable(
//                                    dynamicSample.getList(
//                                        property.name,
//                                        Boolean::class
//                                    )
//                                )
                            }
                            RealmStorageType.INT -> {
                                assertionsForNullable(
                                    dynamicSample.get<RealmList<Long?>>(property.name)
                                )
//                                assertionsForNullable(
//                                    dynamicSample.getList(
//                                        property.name,
//                                        Long::class
//                                    )
//                                )
                            }
                            RealmStorageType.STRING -> {
                                assertionsForNullable(
                                    dynamicSample.get<RealmList<String?>>(property.name)
                                )
//                                assertionsForNullable(
//                                    dynamicSample.getList(
//                                        property.name,
//                                        String::class
//                                    )
//                                )
                            }
                            RealmStorageType.FLOAT -> {
                                assertionsForNullable(
                                    dynamicSample.get<RealmList<Float?>>(property.name)
                                )
//                                assertionsForNullable(
//                                    dynamicSample.getList(
//                                        property.name,
//                                        Float::class
//                                    )
//                                )
                            }
                            RealmStorageType.DOUBLE -> {
                                assertionsForNullable(
                                    dynamicSample.get<RealmList<Double?>>(property.name)
                                )
//                                assertionsForNullable(
//                                    dynamicSample.getList(
//                                        property.name,
//                                        Double::class
//                                    )
//                                )
                            }
                            RealmStorageType.DECIMAL128 -> {
                                assertionsForNullable(
                                    dynamicSample.get<RealmList<Decimal128?>>(property.name)
                                )
//                                assertionsForNullable(
//                                    dynamicSample.getList(
//                                        property.name,
//                                        Decimal128::class
//                                    )
//                                )
                            }
                            RealmStorageType.TIMESTAMP -> {
                                assertionsForNullable(
                                    dynamicSample.get<RealmList<RealmInstant?>>(property.name)
                                )
//                                assertionsForNullable(
//                                    dynamicSample.getList(
//                                        property.name,
//                                        RealmInstant::class
//                                    )
//                                )
                            }
                            RealmStorageType.OBJECT_ID -> {
                                assertionsForNullable(
                                    dynamicSample.get<RealmList<BsonObjectId?>>(property.name)
                                )
//                                assertionsForNullable(
//                                    dynamicSample.getList(
//                                        property.name,
//                                        BsonObjectId::class
//                                    )
//                                )
                            }
                            RealmStorageType.UUID -> {
                                assertionsForNullable(
                                    dynamicSample.get<RealmList<RealmUUID?>>(property.name)
                                )
//                                assertionsForNullable(
//                                    dynamicSample.getList(
//                                        property.name,
//                                        RealmUUID::class
//                                    )
//                                )
                            }
                            RealmStorageType.BINARY -> {
                                assertionsForNullable(
                                    dynamicSample.get<RealmList<ByteArray?>>(property.name)
                                )
//                                assertionsForNullable(
//                                    dynamicSample.getList(
//                                        property.name,
//                                        ByteArray::class
//                                    )
//                                )
                            }
                            RealmStorageType.OBJECT -> {
                                assertEquals(
                                    null,
                                    dynamicSample.get<RealmList<DynamicRealmObject?>>(property.name)[0]
                                )
//                                assertEquals(
//                                    null,
//                                    dynamicSample.get<RealmList<DynamicRealmObject?>>(
//                                        property.name,
//                                        DynamicRealmObject::class
//                                    )[0]
//                                )
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
                                    dynamicSample.get<RealmList<Boolean>>(property.name)[0]
                                )
//                                assertEquals(
//                                    expectedValue,
//                                    dynamicSample.getList(property.name, Boolean::class)[0]
//                                )
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
                                    dynamicSample.get<RealmList<Long>>(property.name)[0]
                                )
//                                assertEquals(
//                                    expectedValue,
//                                    dynamicSample.getList(property.name, Long::class)[0]
//                                )
                            }
                            RealmStorageType.STRING -> {
                                val expectedValue = defaultSample.stringField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.get<RealmList<String>>(property.name)[0]
                                )
//                                assertEquals(
//                                    expectedValue,
//                                    dynamicSample.getList(property.name, String::class)[0]
//                                )
                            }
                            RealmStorageType.FLOAT -> {
                                val expectedValue = defaultSample.floatField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.get<RealmList<Float>>(property.name)[0]
                                )
//                                assertEquals(
//                                    expectedValue,
//                                    dynamicSample.getList(property.name, Float::class)[0]
//                                )
                            }
                            RealmStorageType.DOUBLE -> {
                                val expectedValue = defaultSample.doubleField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.get<RealmList<Double>>(property.name)[0]
                                )
//                                assertEquals(
//                                    expectedValue,
//                                    dynamicSample.getList(property.name, Double::class)[0]
//                                )
                            }
                            RealmStorageType.DECIMAL128 -> {
                                val expectedValue = defaultSample.decimal128Field
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.get<RealmList<Decimal128>>(property.name)[0]
                                )
//                                assertEquals(
//                                    expectedValue,
//                                    dynamicSample.getList(property.name, Decimal128::class)[0]
//                                )
                            }
                            RealmStorageType.TIMESTAMP -> {
                                val expectedValue = defaultSample.timestampField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.get<RealmList<RealmInstant>>(property.name)[0]
                                )
//                                assertEquals(
//                                    expectedValue,
//                                    dynamicSample.getList(
//                                        property.name,
//                                        RealmInstant::class
//                                    )[0]
//                                )
                            }
                            RealmStorageType.OBJECT_ID -> {
                                val expectedValue = defaultSample.bsonObjectIdField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.get<RealmList<BsonObjectId>>(property.name)[0]
                                )
//                                assertEquals(
//                                    expectedValue,
//                                    dynamicSample.getList(
//                                        property.name,
//                                        BsonObjectId::class
//                                    )[0]
//                                )
                            }
                            RealmStorageType.UUID -> {
                                val expectedValue = defaultSample.uuidField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.get<RealmList<RealmUUID>>(property.name)[0]
                                )
//                                assertEquals(
//                                    expectedValue,
//                                    dynamicSample.getList(property.name, RealmUUID::class)[0]
//                                )
                            }
                            RealmStorageType.BINARY -> {
                                val expectedValue = defaultSample.binaryField
                                assertContentEquals(
                                    expectedValue,
                                    dynamicSample.get<RealmList<ByteArray>>(property.name)[0]
                                )
//                                assertContentEquals(
//                                    expectedValue,
//                                    dynamicSample.getList(property.name, ByteArray::class)[0]
//                                )
                            }
                            RealmStorageType.OBJECT -> {
                                val expectedValue = defaultSample.stringField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.get<RealmList<DynamicRealmObject>>(property.name)[0].get(
                                        "stringField"
                                    )
                                )
//                                assertEquals(
//                                    expectedValue,
//                                    dynamicSample.getList(
//                                        property.name,
//                                        DynamicRealmObject::class
//                                    )[0].get("stringField")
//                                )
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
                                    dynamicSample.get<RealmSet<Boolean?>>(property.name)
                                )
//                                assertionsForNullable(
//                                    dynamicSample.getNullableValueSet(property.name, Boolean::class)
//                                )
                            }
                            RealmStorageType.INT -> {
                                assertionsForNullable(
                                    dynamicSample.get<RealmSet<Long?>>(property.name)
                                )
//                                assertionsForNullable(
//                                    dynamicSample.get<RealmSet<*>>(property.name, Long::class)
//                                )
                            }
                            RealmStorageType.STRING -> {
                                assertionsForNullable(
                                    dynamicSample.get<RealmSet<String?>>(property.name)
                                )
//                                assertionsForNullable(
//                                    dynamicSample.get<RealmSet<*>>(property.name, String::class)
//                                )
                            }
                            RealmStorageType.FLOAT -> {
                                assertionsForNullable(
                                    dynamicSample.get<RealmSet<Float?>>(property.name)
                                )
//                                assertionsForNullable(
//                                    dynamicSample.get<RealmSet<*>>(property.name, Float::class)
//                                )
                            }
                            RealmStorageType.DOUBLE -> {
                                assertionsForNullable(
                                    dynamicSample.get<RealmSet<Double?>>(property.name)
                                )
//                                assertionsForNullable(
//                                    dynamicSample.get<RealmSet<*>>(property.name, Double::class)
//                                )
                            }
                            RealmStorageType.TIMESTAMP -> {
                                assertionsForNullable(
                                    dynamicSample.get<RealmSet<RealmInstant?>>(property.name)
                                )
//                                assertionsForNullable(
//                                    dynamicSample.get<RealmSet<*>>(
//                                        property.name,
//                                        RealmInstant::class
//                                    )
//                                )
                            }
                            RealmStorageType.OBJECT_ID -> {
                                assertionsForNullable(
                                    dynamicSample.get<RealmSet<BsonObjectId?>>(
                                        property.name
                                    )
                                )
//                                assertionsForNullable(
//                                    dynamicSample.get<RealmSet<*>>(
//                                        property.name,
//                                        BsonObjectId::class
//                                    )
//                                )
                            }
                            RealmStorageType.UUID -> {
                                assertionsForNullable(
                                    dynamicSample.get<RealmSet<RealmUUID?>>(property.name)
                                )
//                                assertionsForNullable(
//                                    dynamicSample.get<RealmSet<*>>(property.name, RealmUUID::class)
//                                )
                            }
                            RealmStorageType.BINARY -> {
                                assertContentEquals(
                                    null,
                                    dynamicSample.get<RealmSet<ByteArray?>>(property.name)
                                        .first()
                                )
//                                assertContentEquals(
//                                    null,
//                                    dynamicSample.get<RealmSet<ByteArray>>(
//                                        property.name,
//                                        ByteArray::class,
//                                    ).first()
//                                )
                            }
                            RealmStorageType.DECIMAL128 -> {
                                assertNull(
                                    dynamicSample.get<RealmSet<Decimal128?>>(property.name)
                                        .first()
                                )
//                                assertNull(
//                                    dynamicSample.get<RealmSet<*>>(
//                                        property.name,
//                                        Decimal128::class
//                                    ).first()
//                                )
                            }
                            RealmStorageType.OBJECT -> {
                                assertNull(
                                    dynamicSample.get<RealmSet<DynamicRealmObject?>>(
                                        property.name
                                    ).first()
                                )
//                                assertNull(
//                                    dynamicSample.get<RealmSet<*>>(
//                                        property.name,
//                                        DynamicRealmObject::class
//                                    ).first()
//                                )
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
                                    dynamicSample.get<RealmSet<Boolean>>(property.name),
                                    defaultSample.booleanField
                                )
//                                assertionsForValue(
//                                    dynamicSample.get<RealmSet<*>>(property.name, Boolean::class),
//                                    defaultSample.booleanField
//                                )
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
                                    dynamicSample.get<RealmSet<Long>>(property.name),
                                    expectedValue
                                )
//                                assertionsForValue(
//                                    dynamicSample.get<RealmSet<*>>(property.name, Long::class),
//                                    expectedValue
//                                )
                            }
                            RealmStorageType.STRING -> {
                                assertionsForValue(
                                    dynamicSample.get<RealmSet<String>>(property.name),
                                    defaultSample.stringField
                                )
//                                assertionsForValue(
//                                    dynamicSample.get<RealmSet<*>>(property.name, String::class),
//                                    defaultSample.stringField
//                                )
                            }
                            RealmStorageType.FLOAT -> {
                                assertionsForValue(
                                    dynamicSample.get<RealmSet<Float>>(property.name),
                                    defaultSample.floatField
                                )
//                                assertionsForValue(
//                                    dynamicSample.get<RealmSet<*>>(property.name, Float::class),
//                                    defaultSample.floatField
//                                )
                            }
                            RealmStorageType.DOUBLE -> {
                                assertionsForValue(
                                    dynamicSample.get<RealmSet<Double>>(property.name),
                                    defaultSample.doubleField
                                )
//                                assertionsForValue(
//                                    dynamicSample.get<RealmSet<*>>(property.name, Double::class),
//                                    defaultSample.doubleField
//                                )
                            }
                            RealmStorageType.TIMESTAMP -> {
                                assertionsForValue(
                                    dynamicSample.get<RealmSet<RealmInstant>>(property.name),
                                    defaultSample.timestampField
                                )
//                                assertionsForValue(
//                                    dynamicSample.get<RealmSet<*>>(property.name, RealmInstant::class),
//                                    defaultSample.timestampField
//                                )
                            }
                            RealmStorageType.OBJECT_ID -> {
                                assertionsForValue(
                                    dynamicSample.get<RealmSet<BsonObjectId>>(property.name),
                                    defaultSample.bsonObjectIdField
                                )
//                                assertionsForValue(
//                                    dynamicSample.get<RealmSet<*>>(property.name, BsonObjectId::class),
//                                    defaultSample.bsonObjectIdField
//                                )
                            }
                            RealmStorageType.UUID -> {
                                assertionsForValue(
                                    dynamicSample.get<RealmSet<RealmUUID>>(property.name),
                                    defaultSample.uuidField
                                )
//                                assertionsForValue(
//                                    dynamicSample.get<RealmSet<*>>(property.name, RealmUUID::class),
//                                    defaultSample.uuidField
//                                )
                            }
                            RealmStorageType.BINARY -> {
                                assertionsForValue(
                                    dynamicSample.get<RealmSet<ByteArray>>(property.name),
                                    defaultSample.binaryField
                                )
//                                assertionsForValue(
//                                    dynamicSample.get<RealmSet<*>>(property.name, ByteArray::class),
//                                    defaultSample.binaryField
//                                )
                            }
                            RealmStorageType.DECIMAL128 -> {
                                assertionsForValue(
                                    dynamicSample.get<RealmSet<Decimal128>>(property.name),
                                    defaultSample.decimal128Field
                                )
//                                assertionsForValue(
//                                    dynamicSample.get<RealmSet<*>>(property.name, Decimal128::class),
//                                    defaultSample.decimal128Field
//                                )
                            }
                            RealmStorageType.OBJECT -> {
                                val expectedValue = defaultSample.stringField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.get<RealmSet<DynamicRealmObject>>(property.name)
                                        .first().get("stringField")
                                )
//                                assertEquals(
//                                    expectedValue,
//                                    dynamicSample.get<RealmSet<*>>(
//                                        property.name,
//                                        DynamicRealmObject::class
//                                    ).first().get("stringField")
//                                )
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
                                    dynamicSample.get<RealmDictionary<Boolean?>>(property.name)
                                )
//                                assertionsForNullable(
//                                    dynamicSample.get<RealmDictionary<>>(
//                                        property.name,
//                                        Boolean::class
//                                    )
//                                )
                            }
                            RealmStorageType.INT -> {
                                assertionsForNullable(
                                    dynamicSample.get<RealmDictionary<Long?>>(property.name)
                                )
//                                assertionsForNullable(
//                                    dynamicSample.get<RealmDictionary<>>(
//                                        property.name,
//                                        Long::class
//                                    )
//                                )
                            }
                            RealmStorageType.STRING -> {
                                assertionsForNullable(
                                    dynamicSample.get<RealmDictionary<String?>>(property.name)
                                )
//                                assertionsForNullable(
//                                    dynamicSample.get<RealmDictionary<>>(
//                                        property.name,
//                                        String::class
//                                    )
//                                )
                            }
                            RealmStorageType.BINARY -> {
                                assertionsForNullable(
                                    dynamicSample.get<RealmDictionary<ByteArray?>>(
                                        property.name
                                    )
                                )
//                                assertionsForNullable(
//                                    dynamicSample.get<RealmDictionary<>>(
//                                        property.name,
//                                        ByteArray::class
//                                    )
//                                )
                            }
                            RealmStorageType.OBJECT -> when (property.name) {
                                "nullableObjectDictionaryFieldNull" -> {
                                    assertionsForNullable(
                                        dynamicSample.get<RealmDictionary<DynamicRealmObject?>>(
                                            property.name
                                        )
                                    )
//                                    assertionsForNullable(
//                                        dynamicSample.get<RealmDictionary<>>(
//                                            property.name,
//                                            DynamicRealmObject::class
//                                        )
//                                    )
                                }
                                "nullableObjectDictionaryFieldNotNull" -> {
                                    dynamicSample.get<RealmDictionary<DynamicRealmObject?>>(
                                        property.name
                                    ).also { dictionaryFromSample ->
                                        val inner = assertNotNull(dictionaryFromSample["A"])
                                        assertEquals("INNER", inner.get("stringField"))
                                    }
                                }
                            }
                            RealmStorageType.FLOAT -> {
                                assertionsForNullable(
                                    dynamicSample.get<RealmDictionary<Float?>>(
                                        property.name
                                    )
                                )
//                                assertionsForNullable(
//                                    dynamicSample.get<RealmDictionary<>>(
//                                        property.name,
//                                        Float::class
//                                    )
//                                )
                            }
                            RealmStorageType.DOUBLE -> {
                                assertionsForNullable(
                                    dynamicSample.get<RealmDictionary<Double?>>(
                                        property.name
                                    )
                                )
//                                assertionsForNullable(
//                                    dynamicSample.get<RealmDictionary<>>(
//                                        property.name,
//                                        Double::class
//                                    )
//                                )
                            }
                            RealmStorageType.DECIMAL128 -> {
                                assertionsForNullable(
                                    dynamicSample.get<RealmDictionary<Decimal128?>>(
                                        property.name
                                    )
                                )
//                                assertionsForNullable(
//                                    dynamicSample.get<RealmDictionary<>>(
//                                        property.name,
//                                        Decimal128::class
//                                    )
//                                )
                            }
                            RealmStorageType.TIMESTAMP -> {
                                assertionsForNullable(
                                    dynamicSample.get<RealmDictionary<RealmInstant?>>(
                                        property.name
                                    )
                                )
//                                assertionsForNullable(
//                                    dynamicSample.get<RealmDictionary<>>(
//                                        property.name,
//                                        RealmInstant::class
//                                    )
//                                )
                            }
                            RealmStorageType.OBJECT_ID -> {
                                assertionsForNullable(
                                    dynamicSample.get<RealmDictionary<BsonObjectId?>>(
                                        property.name
                                    )
                                )
//                                assertionsForNullable(
//                                    dynamicSample.get<RealmDictionary<>>(
//                                        property.name,
//                                        BsonObjectId::class
//                                    )
//                                )
                            }
                            RealmStorageType.UUID -> {
                                assertionsForNullable(
                                    dynamicSample.get<RealmDictionary<RealmUUID?>>(
                                        property.name
                                    )
                                )
//                                assertionsForNullable(
//                                    dynamicSample.get<RealmDictionary<>>(
//                                        property.name,
//                                        RealmUUID::class
//                                    )
//                                )
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
                                dynamicSample.get<RealmDictionary<Boolean>>(property.name),
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
                                    dynamicSample.get<RealmDictionary<Long>>(property.name),
                                    expectedValue
                                )
                            }
                            RealmStorageType.STRING -> assertionsForValue(
                                dynamicSample.get<RealmDictionary<String>>(property.name),
                                defaultSample.stringField
                            )
                            RealmStorageType.BINARY -> assertionsForValue(
                                dynamicSample.get<RealmDictionary<ByteArray>>(property.name),
                                defaultSample.binaryField
                            )
                            RealmStorageType.OBJECT -> {
                                // No testing needed since dictionaries of objects can only be
                                // nullable and that has been tested above
                            }
                            RealmStorageType.FLOAT -> assertionsForValue(
                                dynamicSample.get<RealmDictionary<Float>>(property.name),
                                defaultSample.floatField
                            )
                            RealmStorageType.DOUBLE -> assertionsForValue(
                                dynamicSample.get<RealmDictionary<Double>>(property.name),
                                defaultSample.doubleField
                            )
                            RealmStorageType.DECIMAL128 -> assertionsForValue(
                                dynamicSample.get<RealmDictionary<Decimal128>>(property.name),
                                defaultSample.decimal128Field
                            )
                            RealmStorageType.TIMESTAMP -> assertionsForValue(
                                dynamicSample.get<RealmDictionary<RealmInstant>>(property.name),
                                defaultSample.timestampField
                            )
                            RealmStorageType.OBJECT_ID -> assertionsForValue(
                                dynamicSample.get<RealmDictionary<BsonObjectId>>(property.name),
                                defaultSample.bsonObjectIdField
                            )
                            RealmStorageType.UUID -> assertionsForValue(
                                dynamicSample.get<RealmDictionary<RealmUUID>>(property.name),
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

            val actualReified = dynamicSample.get<RealmAny?>(Sample::nullableRealmAnyField.name)
//            val actual = dynamicSample.get(Sample::nullableRealmAnyField.name, RealmAny::class)

            // Test we throw if trying to retrieve the object using its actual class instead of
            // DynamicRealmObject
            if (actualReified?.type == RealmAny.Type.OBJECT) {
                assertFailsWith<ClassCastException> {
                    actualReified?.asRealmObject<Sample>()
                }
//                assertFailsWith<ClassCastException> {
//                    actual?.asRealmObject(Sample::class)
//                }

                // Retrieve values now
                assertEquals(
                    expected?.asRealmObject<Sample>()?.stringField,
                    actualReified?.asRealmObject<DynamicRealmObject>()?.get<String>("stringField")
                )
//                assertEquals(
//                    expected?.asRealmObject<Sample>()?.stringField,
//                    actual?.asRealmObject<DynamicRealmObject>()?.get("stringField")
//                )
            } else {
                assertEquals(expected, actualReified)
//                assertEquals(expected, actual)
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
                val actual = dynamicInner.get<RealmAny?>("nullableRealmAnyField")
                val actualObject = actual?.asRealmObject<DynamicRealmObject>()
                val actualString = actualObject?.get<String>("stringField")
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

                val actualReifiedList = dynamicSample.get<RealmList<RealmAny?>>(
                    Sample::nullableRealmAnyListField.name
                )
                val actualList = dynamicSample.get<RealmList<RealmAny?>>(
                    Sample::nullableRealmAnyListField.name,
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
                            actualReified?.asRealmObject<DynamicRealmObject>()?.get<String>("stringField")
                        )
                        assertEquals(
                            expected?.asRealmObject<Sample>()?.stringField,
                            actual?.asRealmObject<DynamicRealmObject>()?.get<String>("stringField")
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

                val actualList = dynamicSample.get<RealmAny?>(
                    Sample::nullableRealmAnyField.name,
                )!!.asList()

                actualList[0]!!.let { innerList ->
                    val actualSample = innerList.asList()[0]!!.asRealmObject<DynamicRealmObject>()
                    assertIs<DynamicRealmObject>(actualSample)
                    assertEquals("INNER_LIST", actualSample.get("stringField"))
                }
                actualList[1]!!.let { innerDictionary ->
                    val actualSample =
                        innerDictionary.asDictionary()!!["key"]!!.asRealmObject<DynamicRealmObject>()
                    assertIs<DynamicRealmObject>(actualSample)
                    assertEquals("INNER_DICT", actualSample.get("stringField"))
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

                val actualReifiedSet = dynamicSample.get<RealmSet<RealmAny?>>(
                    Sample::nullableRealmAnySetField.name
                )
//                val actualSet = dynamicSample.get<RealmSet<RealmAny?>>(
//                    Sample::nullableRealmAnySetField.name,
//                )

                fun assertions(actual: RealmAny?) {
                    if (actual?.type == RealmAny.Type.OBJECT) {
                        var assertionSucceeded = false
                        for (value in realmAnyValues) {
                            if (value?.type == RealmAny.Type.OBJECT) {
                                assertEquals(
                                    value.asRealmObject<Sample>().stringField,
                                    actual.asRealmObject<DynamicRealmObject>()
                                        .get("stringField")
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
//                for (actual in actualSet) {
//                    assertions(actual)
//                }
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

                val actualReifiedDictionary = dynamicSample.get<RealmDictionary<RealmAny?>>(
                    Sample::nullableRealmAnyDictionaryField.name
                )
                val actualDictionary = dynamicSample.get<RealmDictionary<RealmAny?>>(
                    Sample::nullableRealmAnyDictionaryField.name,
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
                            expected?.asRealmObject<Sample>()!!.stringField,
                            actualReified?.asRealmObject<DynamicRealmObject>()?.get<String>("stringField")
                        )
                        assertEquals(
                            expected?.asRealmObject<Sample>()?.stringField,
                            actual.asRealmObject<DynamicRealmObject>()?.get<String>("stringField")
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

                val actualDictionary = dynamicSample.get<RealmAny?>(
                    Sample::nullableRealmAnyField.name,
                )!!.asDictionary()

                actualDictionary["list"]!!.let { innerList ->
                    val innerSample = innerList.asList()[0]!!
                    val actualSample = innerSample.asRealmObject<DynamicRealmObject>()
                    assertIs<DynamicRealmObject>(actualSample)
                    assertEquals("INNER_LIST", actualSample.get("stringField"))

                    assertFailsWith<ClassCastException> {
                        innerSample.asRealmObject<Sample>()
                    }
                }
                actualDictionary["dict"]!!.let { innerDictionary ->
                    val innerSample = innerDictionary.asDictionary()!!["key"]!!
                    val actualSample =
                        innerSample.asRealmObject<DynamicRealmObject>()
                    assertIs<DynamicRealmObject>(actualSample)
                    assertEquals("INNER_DICT", actualSample.get("stringField"))

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
            dynamicSample.get<String>("UNKNOWN_FIELD")
        }
        assertFailsWithMessage<IllegalArgumentException>("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_FIELD'") {
            dynamicSample.get<String>("UNKNOWN_FIELD")
        }
        assertFailsWithMessage<IllegalArgumentException>("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_FIELD'") {
            dynamicSample.get<DynamicRealmObject>("UNKNOWN_FIELD")
        }
        assertFailsWithMessage<IllegalArgumentException>("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_FIELD'") {
            dynamicSample.get<RealmList<String>>("UNKNOWN_FIELD")
        }
        assertFailsWithMessage<IllegalArgumentException>("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_FIELD'") {
            dynamicSample.get<RealmList<String>>("UNKNOWN_FIELD")
        }
        assertFailsWithMessage<IllegalArgumentException>("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_FIELD'") {
            dynamicSample.get<RealmList<DynamicRealmObject>>("UNKNOWN_FIELD")
        }
    }

    @Test
    fun getVariants_throwsOnWrongTypes() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        val dynamicRealm = realm.asDynamicRealm()
        val dynamicSample = dynamicRealm.query("Sample").find().first()

        // Wrong type
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.stringField' as type: 'class kotlin.Long' but actual schema type is 'class kotlin.String'") {
            dynamicSample.get<Long>("stringField")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableStringField' as type: 'class kotlin.Long?' but actual schema type is 'class kotlin.String?'") {
            dynamicSample.get<Long?>("nullableStringField")
        }

        // Wrong nullability
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableStringField' as type: 'class kotlin.Long' but actual schema type is 'class kotlin.String?'") {
            dynamicSample.get<Long>("nullableStringField")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.stringField' as type: 'RealmList<class kotlin.Long?>' but actual schema type is 'class kotlin.String'") {
            dynamicSample.get<RealmList<Long?>>("stringField")
        }

        // Wrong variants
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableObject' as type: 'class kotlin.String' but actual schema type is 'class io.realm.kotlin.types.BaseRealmObject?'") {
            dynamicSample.get<String>("nullableObject")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableObject' as type: 'class kotlin.String?' but actual schema type is 'class io.realm.kotlin.types.BaseRealmObject?'") {
            dynamicSample.get<String?>("nullableObject")
        }

        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.stringListField' as type: 'class kotlin.Long' but actual schema type is 'RealmList<class kotlin.String>'") {
            dynamicSample.get<Long>("stringListField")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableStringListField' as type: 'class kotlin.Long?' but actual schema type is 'RealmList<class kotlin.String?>'") {
            dynamicSample.get<Long?>("nullableStringListField")
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
            dynamicSample.get<DynamicRealmObject?>("stringField")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.stringListField' as type: 'class io.realm.kotlin.types.BaseRealmObject?' but actual schema type is 'RealmList<class kotlin.String>'") {
            dynamicSample.get<DynamicRealmObject?>("stringListField")
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

        val results = dynamicSample.get<RealmList<DynamicRealmObject>>("objectListField").query("intField > 2").find()
        assertEquals(3, results.size)
        results.forEach { assertTrue { it.get<Long>("intField") > 2 } }
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

        val results = dynamicSample.get<RealmSet<DynamicRealmObject>>("objectSetField").query("intField > 2").find()
        assertEquals(3, results.size)
        results.forEach { assertTrue { it.get<Long>("intField") > 2 } }
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
            dynamicSample.get<RealmList<Long>>("stringListField")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableStringListField' as type: 'RealmList<class kotlin.Long?>' but actual schema type is 'RealmList<class kotlin.String?>'") {
            dynamicSample.get<RealmList<Long?>>("nullableStringListField")
        }

        // Wrong nullability
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableStringListField' as type: 'RealmList<class kotlin.String>' but actual schema type is 'RealmList<class kotlin.String?>'") {
            dynamicSample.get<RealmList<String>>("nullableStringListField")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.stringListField' as type: 'RealmList<class kotlin.String?>' but actual schema type is 'RealmList<class kotlin.String>'") {
            dynamicSample.get<RealmList<String?>>("stringListField")
        }

        // Wrong variants
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.stringField' as type: 'RealmList<class kotlin.String>' but actual schema type is 'class kotlin.String'") {
            dynamicSample.get<RealmList<String>>("stringField")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableObject' as type: 'RealmList<class kotlin.Long>' but actual schema type is 'class io.realm.kotlin.types.BaseRealmObject?'") {
            dynamicSample.get<RealmList<Long>>("nullableObject")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableStringField' as type: 'RealmList<class kotlin.Long?>' but actual schema type is 'class kotlin.String?'") {
            dynamicSample.get<RealmList<Long?>>("nullableStringField")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableStringListField' as type: 'RealmList<class io.realm.kotlin.types.BaseRealmObject>' but actual schema type is 'RealmList<class kotlin.String?>'") {
            dynamicSample.get<RealmList<DynamicRealmObject>>("nullableStringListField")
        }
    }

    // We don't have an immutable RealmList so verify that we fail in an understandable manner if
    // trying to update the list
    @Test
    fun getList_throwsIfModified() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        val dynamicRealm = realm.asDynamicRealm()
        val dynamicSample = dynamicRealm.query("Sample").find().first()

        assertFailsWithMessage<IllegalStateException>("Cannot modify managed List outside of a write transaction.") {
            dynamicSample.get<RealmList<String>>("stringListField").add("IMMUTABLE_LIST_ELEMENT")
        }
        assertFailsWithMessage<IllegalStateException>("Cannot modify managed List outside of a write transaction.") {
            dynamicSample.get<RealmList<String?>>("nullableStringListField")
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
            first.get<DynamicRealmObject>("stringField")
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
