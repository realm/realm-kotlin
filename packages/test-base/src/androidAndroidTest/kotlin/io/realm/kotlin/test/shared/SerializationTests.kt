/*
 * Copyright 2023 Realm Inc.
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
@file:UseSerializers(
    RealmListSerializer::class,
    RealmSetSerializer::class,
    RealmAnySerializer::class,
    RealmInstantSerializer::class,
    MutableRealmIntSerializer::class,
    RealmUUIDSerializer::class,
    RealmObjectIdSerializer::class
)

package io.realm.kotlin.test.shared

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.SerializableEmbeddedObject
import io.realm.kotlin.entities.SerializableSample
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.serializers.MutableRealmIntSerializer
import io.realm.kotlin.serializers.RealmAnySerializer
import io.realm.kotlin.serializers.RealmInstantSerializer
import io.realm.kotlin.serializers.RealmListSerializer
import io.realm.kotlin.serializers.RealmObjectIdSerializer
import io.realm.kotlin.serializers.RealmSetSerializer
import io.realm.kotlin.serializers.RealmUUIDSerializer
import io.realm.kotlin.test.GenericTypeSafetyManager
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TypeDescriptor
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KMutableProperty1
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class SerializationTests {
    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    private lateinit var configuration: RealmConfiguration

    private val json = Json {
        serializersModule = SerializersModule {
            polymorphic(RealmObject::class) {
                subclass(SerializableSample::class)
            }

            polymorphic(EmbeddedRealmObject::class) {
                subclass(SerializableEmbeddedObject::class)
            }
        }
    }

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration.Builder(
            setOf(
                SerializableSample::class,
                SerializableEmbeddedObject::class
            )
        )
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

    private fun Collection<TypeDescriptor.RealmFieldType>.mapCollectionDataSets(
        properties: Pair<
            Map<KClass<out Any>, KMutableProperty1<SerializableSample, MutableCollection<Any>>>,
            Map<KClass<out Any>, KMutableProperty1<SerializableSample, MutableCollection<Any?>>>
            >
    ) = map { fieldType: TypeDescriptor.RealmFieldType ->
        CollectionTypeSafetyManager<Any?>(
            elementType = fieldType.elementType,
            properties = properties
        )
    }

    private class CollectionTypeSafetyManager<T>(
        elementType: TypeDescriptor.ElementType,
        properties: Pair<
            Map<KClass<out Any>, KMutableProperty1<SerializableSample, MutableCollection<Any>>>,
            Map<KClass<out Any>, KMutableProperty1<SerializableSample, MutableCollection<Any?>>>
            >,
    ) : GenericTypeSafetyManager<T, SerializableSample, MutableCollection<T>> {

        val classifier: KClassifier = elementType.classifier

        override val dataSetToLoad: List<T> =
            getDataSetForCollectionClassifier(classifier, elementType.nullable)

        override val property = when (elementType.nullable) {
            false -> properties.first[classifier]!!
            true -> properties.second[classifier]!!
        } as KMutableProperty1<SerializableSample, MutableCollection<T>>

        companion object {
            @Suppress("UNCHECKED_CAST", "ComplexMethod")
            fun <T> getDataSetForCollectionClassifier(
                classifier: KClassifier,
                nullable: Boolean
            ): List<T> = when (classifier) {
                Byte::class -> if (nullable) NULLABLE_BYTE_VALUES else BYTE_VALUES
                Char::class -> if (nullable) NULLABLE_CHAR_VALUES else CHAR_VALUES
                Short::class -> if (nullable) NULLABLE_SHORT_VALUES else SHORT_VALUES
                Int::class -> if (nullable) NULLABLE_INT_VALUES else INT_VALUES
                Long::class -> if (nullable) NULLABLE_LONG_VALUES else LONG_VALUES
                Boolean::class -> if (nullable) NULLABLE_BOOLEAN_VALUES else BOOLEAN_VALUES
                Float::class -> if (nullable) NULLABLE_FLOAT_VALUES else FLOAT_VALUES
                Double::class -> if (nullable) NULLABLE_DOUBLE_VALUES else DOUBLE_VALUES
                Decimal128::class -> if (nullable) NULLABLE_DECIMAL128_VALUES else DECIMAL128_VALUES
                String::class -> if (nullable) NULLABLE_STRING_VALUES else STRING_VALUES
                RealmInstant::class -> if (nullable) NULLABLE_TIMESTAMP_VALUES else TIMESTAMP_VALUES
                ObjectId::class -> if (nullable) NULLABLE_OBJECT_ID_VALUES else OBJECT_ID_VALUES
                BsonObjectId::class -> if (nullable) NULLABLE_BSON_OBJECT_ID_VALUES else BSON_OBJECT_ID_VALUES
                RealmUUID::class -> if (nullable) NULLABLE_UUID_VALUES else UUID_VALUES
                ByteArray::class -> if (nullable) NULLABLE_BINARY_VALUES else BINARY_VALUES
                RealmObject::class -> OBJECT_VALUES.map {
                    SerializableSample().apply {
                        stringField = it.stringField
                    }
                }
                RealmAny::class -> REALM_ANY_PRIMITIVE_VALUES + REALM_ANY_REALM_OBJECT
                else -> throw IllegalArgumentException("Wrong classifier: '$classifier'")
            } as List<T>

            val REALM_ANY_REALM_OBJECT = RealmAny.create(
                SerializableSample().apply { stringField = "hello" },
                SerializableSample::class
            )
        }

        override fun toString(): String = property.name

        override fun getCollection(container: SerializableSample): MutableCollection<T> =
            property.get(container)

        override fun createContainerAndGetCollection(realm: MutableRealm): MutableCollection<T> {
            val container = SerializableSample().let {
                realm.copyToRealm(it)
            }
            return property.get(container).also { list ->
                assertNotNull(list)
                assertTrue(list.isEmpty())
            }
        }

        override fun createPrePopulatedContainer(): SerializableSample =
            SerializableSample().also {
                property.get(it)
                    .apply {
                        addAll(dataSetToLoad)
                    }
            }
    }

    private class DictionaryTypeSafetyManager<T> constructor(
        elementType: TypeDescriptor.ElementType,
        properties: Pair<
            Map<KClass<out Any>, KMutableProperty1<SerializableSample, RealmDictionary<Any>>>,
            Map<KClass<out Any>, KMutableProperty1<SerializableSample, RealmDictionary<Any?>>>
            >,
    ) : GenericTypeSafetyManager<Pair<String, T>, SerializableSample, RealmDictionary<T>> {

        val classifier: KClassifier = elementType.classifier

        override val dataSetToLoad: List<Pair<String, T>> = getDataSetForDictionaryClassifier<T>(
            classifier,
            elementType.nullable
        )

        override val property = when (elementType.nullable) {
            false -> properties.first[classifier]!!
            true -> properties.second[classifier]!!
        } as KMutableProperty1<SerializableSample, RealmDictionary<T>>

        companion object {

            fun <T> getDataSetForDictionaryClassifier(
                classifier: KClassifier,
                nullable: Boolean
            ): List<Pair<String, T>> = when (classifier) {
                Byte::class -> if (nullable) {
                    NULLABLE_BYTE_VALUES.mapIndexed { i, value ->
                        Pair(
                            KEYS_FOR_NULLABLE[i],
                            value
                        )
                    }
                } else {
                    BYTE_VALUES.mapIndexed { i, value -> Pair(KEYS_FOR_NULLABLE[i], value) }
                }
                Char::class -> if (nullable) {
                    NULLABLE_CHAR_VALUES.mapIndexed { i, value ->
                        Pair(
                            KEYS_FOR_NULLABLE[i],
                            value
                        )
                    }
                } else {
                    CHAR_VALUES.mapIndexed { i, value -> Pair(KEYS_FOR_NULLABLE[i], value) }
                }
                Short::class -> if (nullable) {
                    NULLABLE_SHORT_VALUES.mapIndexed { i, value ->
                        Pair(
                            KEYS_FOR_NULLABLE[i],
                            value
                        )
                    }
                } else {
                    SHORT_VALUES.mapIndexed { i, value -> Pair(KEYS_FOR_NULLABLE[i], value) }
                }
                Int::class -> if (nullable) {
                    NULLABLE_INT_VALUES.mapIndexed { i, value -> Pair(KEYS_FOR_NULLABLE[i], value) }
                } else {
                    INT_VALUES.mapIndexed { i, value -> Pair(KEYS_FOR_NULLABLE[i], value) }
                }
                Long::class -> if (nullable) {
                    NULLABLE_LONG_VALUES.mapIndexed { i, value ->
                        Pair(
                            KEYS_FOR_NULLABLE[i],
                            value
                        )
                    }
                } else {
                    LONG_VALUES.mapIndexed { i, value -> Pair(KEYS[i], value) }
                }
                Boolean::class -> if (nullable) {
                    NULLABLE_BOOLEAN_VALUES.mapIndexed { i, value ->
                        Pair(
                            KEYS_FOR_NULLABLE[i],
                            value
                        )
                    }
                } else {
                    BOOLEAN_VALUES.mapIndexed { i, value -> Pair(KEYS[i], value) }
                }
                Float::class -> if (nullable) {
                    NULLABLE_FLOAT_VALUES.mapIndexed { i, value ->
                        Pair(
                            KEYS_FOR_NULLABLE[i],
                            value
                        )
                    }
                } else {
                    FLOAT_VALUES.mapIndexed { i, value -> Pair(KEYS[i], value) }
                }
                Double::class -> if (nullable) {
                    NULLABLE_DOUBLE_VALUES.mapIndexed { i, value ->
                        Pair(
                            KEYS_FOR_NULLABLE[i],
                            value
                        )
                    }
                } else {
                    DOUBLE_VALUES.mapIndexed { i, value -> Pair(KEYS[i], value) }
                }
                String::class -> if (nullable) {
                    NULLABLE_STRING_VALUES.mapIndexed { i, value ->
                        Pair(
                            KEYS_FOR_NULLABLE[i],
                            value
                        )
                    }
                } else {
                    STRING_VALUES.mapIndexed { i, value -> Pair(KEYS[i], value) }
                }
                RealmInstant::class -> if (nullable) {
                    NULLABLE_TIMESTAMP_VALUES.mapIndexed { i, value ->
                        Pair(
                            KEYS_FOR_NULLABLE[i],
                            value
                        )
                    }
                } else {
                    TIMESTAMP_VALUES.mapIndexed { i, value -> Pair(KEYS[i], value) }
                }
                ObjectId::class -> if (nullable) {
                    NULLABLE_OBJECT_ID_VALUES.mapIndexed { i, value ->
                        Pair(
                            KEYS_FOR_NULLABLE[i],
                            value
                        )
                    }
                } else {
                    OBJECT_ID_VALUES.mapIndexed { i, value -> Pair(KEYS[i], value) }
                }
                BsonObjectId::class -> if (nullable) {
                    NULLABLE_BSON_OBJECT_ID_VALUES.mapIndexed { i, value ->
                        Pair(KEYS_FOR_NULLABLE[i], value)
                    }
                } else {
                    BSON_OBJECT_ID_VALUES.mapIndexed { i, value -> Pair(KEYS[i], value) }
                }
                RealmUUID::class -> if (nullable) {
                    NULLABLE_UUID_VALUES.mapIndexed { i, value ->
                        Pair(
                            KEYS_FOR_NULLABLE[i],
                            value
                        )
                    }
                } else {
                    UUID_VALUES.mapIndexed { i, value -> Pair(KEYS[i], value) }
                }
                ByteArray::class -> if (nullable) {
                    NULLABLE_BINARY_VALUES.mapIndexed { i, value ->
                        Pair(
                            KEYS_FOR_NULLABLE[i],
                            value
                        )
                    }
                } else {
                    BINARY_VALUES.mapIndexed { i, value -> Pair(KEYS[i], value) }
                }
                RealmObject::class -> {
                    listOf(
                        SerializableSample().apply { stringField = "A" },
                        SerializableSample().apply { stringField = "B" }
                    ).mapIndexed { i, value ->
                        Pair(KEYS_FOR_NULLABLE[i], value)
                    }
                }
                Decimal128::class -> if (nullable) {
                    NULLABLE_DECIMAL128_VALUES.mapIndexed { i, value ->
                        Pair(
                            KEYS_FOR_NULLABLE[i],
                            value
                        )
                    }
                } else {
                    DECIMAL128_VALUES.mapIndexed { i, value -> Pair(KEYS[i], value) }
                }
                RealmAny::class -> {
                    val anyValues =
                        REALM_ANY_PRIMITIVE_VALUES + CollectionTypeSafetyManager.REALM_ANY_REALM_OBJECT

                    // Generate as many keys as RealmAny values
                    var key = 'A'
                    val keys = anyValues.map { key.also { key += 1 } }

                    // Now create pairs of key-RealmAny for the dataset
                    anyValues.mapIndexed { i, value -> Pair(keys[i].toString(), value) }
                }
                else -> throw IllegalArgumentException("Wrong classifier: '$classifier'")
            } as List<Pair<String, T>>
        }

        override fun toString(): String = property.name

        override fun getCollection(container: SerializableSample): RealmDictionary<T> =
            property.get(container)

        override fun createContainerAndGetCollection(realm: MutableRealm): RealmDictionary<T> {
            val container = SerializableSample().let {
                realm.copyToRealm(it)
            }
            return property.get(container)
                .also { dictionary ->
                    assertNotNull(dictionary)
                    assertTrue(dictionary.isEmpty())
                }
        }

        override fun createPrePopulatedContainer(): SerializableSample {
            return SerializableSample().also {
                property.get(it)
                    .apply {
                        putAll(dataSetToLoad)
                    }
            }
        }
    }

    private fun <T> KClassifier.assertValue(
        expected: T,
        actual: T
    ) {
        when (this) {
            ByteArray::class -> assertContentEquals(expected as ByteArray?, actual as ByteArray?)
            RealmObject::class -> assertEquals(
                (expected as SerializableSample).stringField,
                (actual as SerializableSample).stringField
            )
            RealmAny::class -> {
                expected as RealmAny?
                actual as RealmAny?

                if (expected != null && actual != null) {
                    when (expected.type) {
                        RealmAny.Type.OBJECT -> {
                            assertEquals(expected.type, actual.type)
                            assertEquals(
                                expected.asRealmObject<SerializableSample>().stringField,
                                actual.asRealmObject<SerializableSample>().stringField
                            )
                        }
                        else -> assertEquals(expected, actual)
                    }
                } else if (expected != null || actual != null) {
                    fail("One of the RealmAny values is null, expected = $expected, actual = $actual")
                }
            }
            else -> assertEquals(expected, actual)
        }
    }

    @Test
    fun exhaustiveRealmListTester() {
        TypeDescriptor
            .allListFieldTypes
            .mapCollectionDataSets(SerializableSample.listProperties)
            .forEach { dataset ->
                val data = dataset.createPrePopulatedContainer()

                val encoded: String = json.encodeToString(data)
                val decoded: SerializableSample = json.decodeFromString(encoded)

                val originalCollection = dataset.getCollection(data)
                val decodedCollection = dataset.getCollection(decoded)

                originalCollection
                    .zip(decodedCollection)
                    .forEach { (expected, decoded) ->
                        dataset.classifier.assertValue(expected, decoded)
                    }
            }
    }

    @Test
    fun exhaustiveRealmSetTester() {
        TypeDescriptor
            .allSetFieldTypes
            .mapCollectionDataSets(SerializableSample.setProperties)
            .forEach { dataset ->
                val data = dataset.createPrePopulatedContainer()

                val encoded: String = json.encodeToString(data)
                val decoded: SerializableSample = json.decodeFromString(encoded)

                val originalCollection = dataset.getCollection(data)
                val decodedCollection = dataset.getCollection(decoded)

                originalCollection
                    .zip(decodedCollection)
                    .forEach { (expected, decoded) ->
                        dataset.classifier.assertValue(expected, decoded)
                    }
            }
    }

    @Test
    fun exhaustiveRealmDictTester() {
        TypeDescriptor
            .allDictionaryFieldTypes
            .map { fieldType: TypeDescriptor.RealmFieldType ->
                DictionaryTypeSafetyManager<Any?>(
                    elementType = fieldType.elementType,
                    properties = SerializableSample.dictionaryProperties
                )
            }
            .forEach { dataset ->
                val data = dataset.createPrePopulatedContainer()

                val encoded: String = json.encodeToString(data)
                val decoded: SerializableSample = json.decodeFromString(encoded)

                val originalCollection = dataset.getCollection(data)
                val decodedCollection = dataset.getCollection(decoded)

                assertEquals(originalCollection.keys, decodedCollection.keys)
                originalCollection.keys
                    .forEach { key: String ->
                        dataset.classifier.assertValue(
                            originalCollection[key],
                            decodedCollection[key]
                        )
                    }
            }
    }
}
