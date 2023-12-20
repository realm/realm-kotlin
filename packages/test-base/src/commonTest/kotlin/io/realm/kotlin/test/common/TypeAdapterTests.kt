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
package io.realm.kotlin.test.common

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.adapters.AllTypes
import io.realm.kotlin.entities.adapters.RealmInstantBsonDateTimeAdapterInstanced
import io.realm.kotlin.entities.adapters.ReferencedObject
import io.realm.kotlin.entities.adapters.UsingInstancedAdapter
import io.realm.kotlin.entities.adapters.UsingSingletonAdapter
import io.realm.kotlin.ext.realmDictionaryOf
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.internal.interop.CollectionType
import io.realm.kotlin.schema.RealmStorageType
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TypeDescriptor
import io.realm.kotlin.test.util.TypeDescriptor.unsupportedRealmTypeAdaptersClassifiers
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonDateTime
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128
import kotlin.reflect.KMutableProperty1
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * This test suite should cover these points:
 * - [x] Schema validation
 * - [x] Singleton / instanced adapters
 * - [-] All types including collections
 * - [x] Default values
 * - [x] copyFromRealm
 */
class TypeAdapterTests {
    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    private lateinit var configuration: RealmConfiguration

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration.Builder(
            setOf(
                UsingSingletonAdapter::class,
                UsingInstancedAdapter::class,
                AllTypes::class,
                ReferencedObject::class
            )
        )
            .directory(tmpDir)
            .typeAdapters {
                add(RealmInstantBsonDateTimeAdapterInstanced())
            }
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

    @Test
    fun schemaValidation() {
        val realmProperty =
            realm.schema()[UsingSingletonAdapter::class.simpleName!!]!![UsingSingletonAdapter::date.name]!!
        assertEquals(RealmStorageType.TIMESTAMP, realmProperty.type.storageType)
    }

    @Test
    fun useSingletonAdapter() {
        val expectedDate = BsonDateTime()

        val unmanagedObject = UsingSingletonAdapter().apply {
            this.date = expectedDate
        }
        assertEquals(expectedDate, unmanagedObject.date)

        val managedObject = realm.writeBlocking {
            copyToRealm(unmanagedObject)
        }

        assertEquals(expectedDate, managedObject.date)
    }

    @Test
    fun useInstancedAdapter() {
        val expectedDate = BsonDateTime()

        val unmanagedObject = UsingInstancedAdapter().apply {
            this.date = expectedDate
        }
        assertEquals(expectedDate, unmanagedObject.date)

        val managedObject = realm.writeBlocking {
            copyToRealm(unmanagedObject)
        }

        assertEquals(expectedDate, managedObject.date)
    }

    @Test
    fun roundTripAllTypes() {
        validateProperties(AllTypes.properties)
        validateProperties(AllTypes.adaptedTypeParameterProperties)
    }

    private fun validateProperties(properties: Map<TypeDescriptor.RealmFieldType, KMutableProperty1<AllTypes, out Any?>>) {
        TypeDescriptor.allFieldTypes
            .filterNot {
                // TODO Deactivated because a bug with contains on RealmSet
                it.collectionType == CollectionType.RLM_COLLECTION_TYPE_SET
            }
            .filterNot { fieldType ->
                fieldType.elementType.classifier in unsupportedRealmTypeAdaptersClassifiers
            }
            .filter { fieldType ->
                fieldType in properties
            }
            .forEach { fieldType: TypeDescriptor.RealmFieldType ->
                val testProperty: KMutableProperty1<AllTypes, Any?> =
                    properties[fieldType]!! as KMutableProperty1<AllTypes, Any?>

                val testClassifier = fieldType.elementType.classifier

                val unmanaged = AllTypes()

                val testValues = testValues[testClassifier]!!

                when (fieldType.collectionType) {
                    CollectionType.RLM_COLLECTION_TYPE_NONE -> {
                        if (fieldType.elementType.nullable) {
                            testValues + null
                        } else {
                            testValues
                        }
                    }

                    CollectionType.RLM_COLLECTION_TYPE_LIST -> {
                        if (fieldType.elementType.nullable) {
                            listOf(
                                realmListOf<Any?>().apply {
                                    addAll(testValues)
                                    add(null)
                                }
                            )
                        } else {
                            listOf(
                                realmListOf<Any>().apply {
                                    addAll(testValues)
                                }
                            )
                        }
                    }
                    CollectionType.RLM_COLLECTION_TYPE_SET -> {
                        if (fieldType.elementType.nullable) {
                            listOf(
                                realmSetOf<Any?>().apply {
                                    addAll(testValues)
                                    add(null)
                                }
                            )
                        } else {
                            listOf(
                                realmSetOf<Any>().apply {
                                    addAll(testValues)
                                }
                            )
                        }
                    }

                    CollectionType.RLM_COLLECTION_TYPE_DICTIONARY -> {
                        val dictValues = testValues
                            .mapIndexed { index, any ->
                                "$index" to any
                            }

                        if (fieldType.elementType.nullable) {
                            listOf(
                                realmDictionaryOf<Any?>().apply {
                                    putAll(dictValues)
                                    put("", null)
                                }
                            )
                        } else {
                            listOf(
                                realmDictionaryOf<Any>().apply {
                                    putAll(dictValues)
                                }
                            )
                        }
                    }
                    else -> throw IllegalStateException("Unhandled case")
                }.forEach { expected ->
                    testProperty.set(unmanaged, expected)

                    val managed = realm.writeBlocking {
                        copyToRealm(unmanaged)
                    }

                    val actual = testProperty.get(managed)

                    when (testClassifier) {
                        ByteArray::class -> {
                            assertContentEquals(
                                expected = expected as ByteArray?,
                                actual = actual as ByteArray?,
                                message = "non matching values for property ${testProperty.name}"
                            )
                        }

                        else -> {
                            assertEquals(
                                expected = expected,
                                actual = actual,
                                message = "non matching values for property ${testProperty.name}"
                            )
                        }
                    }
                }
            }
    }
}

private val testValues = mapOf(
    String::class to listOf("adfasdf"),
    Long::class to listOf(Long.MIN_VALUE, Long.MAX_VALUE),
    Boolean::class to listOf(false, true),
    Float::class to listOf(Float.MIN_VALUE, Float.MAX_VALUE),
    Double::class to listOf(Double.MIN_VALUE, Double.MAX_VALUE),
    Decimal128::class to listOf(Decimal128("0")),
    RealmInstant::class to listOf(RealmInstant.MAX),
    ObjectId::class to listOf(ObjectId.create()),
    BsonObjectId::class to listOf(BsonObjectId()),
    RealmUUID::class to listOf(RealmUUID.random()),
    ByteArray::class to listOf(byteArrayOf(0, 0, 0, 0)),
    RealmAny::class to listOf(RealmAny.create("")),
    RealmObject::class to listOf(ReferencedObject().apply { stringField = "hello world" }),
)
