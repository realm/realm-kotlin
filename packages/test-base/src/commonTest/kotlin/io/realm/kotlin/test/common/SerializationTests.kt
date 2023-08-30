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
    RealmListKSerializer::class,
    RealmSetKSerializer::class,
    RealmAnyKSerializer::class,
    RealmInstantKSerializer::class,
    MutableRealmIntKSerializer::class,
    RealmUUIDKSerializer::class
)
@file:Suppress("UNCHECKED_CAST", "invisible_member", "invisible_reference")

package io.realm.kotlin.test.common

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.SerializableEmbeddedObject
import io.realm.kotlin.entities.SerializableSample
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.realmAnyDictionaryOf
import io.realm.kotlin.ext.realmAnyListOf
import io.realm.kotlin.ext.realmAnySetOf
import io.realm.kotlin.internal.restrictToMillisPrecision
import io.realm.kotlin.serializers.MutableRealmIntKSerializer
import io.realm.kotlin.serializers.RealmAnyKSerializer
import io.realm.kotlin.serializers.RealmDictionaryKSerializer
import io.realm.kotlin.serializers.RealmInstantKSerializer
import io.realm.kotlin.serializers.RealmListKSerializer
import io.realm.kotlin.serializers.RealmSetKSerializer
import io.realm.kotlin.serializers.RealmUUIDKSerializer
import io.realm.kotlin.test.common.utils.GenericTypeSafetyManager
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TypeDescriptor
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
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

            contextual(RealmSet::class) { args ->
                RealmSetKSerializer(RealmAnyKSerializer.nullable)
            }
            contextual(RealmList::class) { args ->
                RealmListKSerializer(RealmAnyKSerializer.nullable)
            }
            contextual(RealmDictionary::class) { args ->
                RealmDictionaryKSerializer(RealmAnyKSerializer.nullable)
            }
        }
    }

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration
            .Builder(setOf(SerializableSample::class, SerializableEmbeddedObject::class))
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

    private val OBJECT_VALUES = listOf(
        SerializableSample().apply { stringField = "A" },
        SerializableSample().apply { stringField = "B" }
    )

    private fun Collection<TypeDescriptor.RealmFieldType>.mapCollectionDataSets(
        properties: Map<KClass<out Any>, KMutableProperty1<*, *>>,
        nullableProperties: Map<KClass<out Any>, KMutableProperty1<*, *>>,
    ): List<CollectionTypeSafetyManager<Any?>> = map { fieldType: TypeDescriptor.RealmFieldType ->
        CollectionTypeSafetyManager<Any?>(
            dataSet = getDataSetForCollectionClassifier(
                classifier = fieldType.elementType.classifier,
                nullable = fieldType.elementType.nullable,
                realmObjects = OBJECT_VALUES
            ),
            property = when (fieldType.elementType.nullable) {
                false -> properties[fieldType.elementType.classifier]
                true -> nullableProperties[fieldType.elementType.classifier]
            }!! as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            classifier = fieldType.elementType.classifier
        )
    }

    private class CollectionTypeSafetyManager<T>(
        override val property: KMutableProperty1<SerializableSample, MutableCollection<T>>,
        dataSet: List<T>,
        val classifier: KClassifier
    ) : GenericTypeSafetyManager<T, SerializableSample, MutableCollection<T>> {

        // Drop RealmInstant to milliseconds precision
        override val dataSetToLoad: List<T> = when (classifier) {
            RealmInstant::class -> dataSet.map {
                (it as RealmInstant?)?.restrictToMillisPrecision() as T
            }
            else -> dataSet
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
        dataSet: List<Pair<String, T>>,
        override val property: KMutableProperty1<SerializableSample, RealmDictionary<T>>,
        val classifier: KClassifier
    ) : GenericTypeSafetyManager<Pair<String, T>, SerializableSample, RealmDictionary<T>> {

        // Drop RealmInstant to milliseconds precision
        override val dataSetToLoad: List<Pair<String, T>> = when (classifier) {
            RealmInstant::class -> dataSet.map { entry ->
                entry.first to (entry.second as RealmInstant?)?.restrictToMillisPrecision() as T
            }
            else -> dataSet
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
                            // Recursively assert the contained object
                            RealmObject::class.assertValue<RealmObject>(
                                expected.asRealmObject(),
                                expected.asRealmObject()
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
    fun exhaustiveElementTypesTester() {
        val expected = SerializableSample().apply {
            nullableObject = SerializableSample()
            realmEmbeddedObject = SerializableEmbeddedObject()
        }
        val encoded: String = json.encodeToString(expected)
        val decoded: SerializableSample = json.decodeFromString(encoded)

        TypeDescriptor.elementTypes
            .filterNot { it.classifier == RealmAny::class } // tested in exhaustiveRealmAnyTester
            .filterNot {
                // filter out as it deprecated and we don't provide a serializer
                it.classifier == ObjectId::class
            }
            .forEach { elementType ->
                val property: KMutableProperty1<SerializableSample, out Any?> =
                    when (elementType.nullable) {
                        true -> SerializableSample.nullableProperties[elementType.classifier]
                        false -> SerializableSample.properties[elementType.classifier]
                    }!!

                elementType.classifier.assertValue(property.get(expected), property.get(decoded))
            }
    }

    @Test
    fun exhaustiveRealmAnyTester() {
        RealmAny.Type.values()
            .map { type: RealmAny.Type ->
                type to when (type) {
                    RealmAny.Type.INT -> SerializableSample().apply {
                        nullableRealmAnyField = RealmAny.create(longField)
                    }
                   RealmAny.Type.FLOAT -> SerializableSample().apply {
                        nullableRealmAnyField = RealmAny.create(floatField)
                    }
                    RealmAny.Type.DOUBLE -> SerializableSample().apply {
                        nullableRealmAnyField = RealmAny.create(doubleField)
                    }
                    RealmAny.Type.BINARY -> SerializableSample().apply {
                        nullableRealmAnyField = RealmAny.create(binaryField)
                    }
                    RealmAny.Type.BOOL -> SerializableSample().apply {
                        nullableRealmAnyField = RealmAny.create(booleanField)
                    }
                    RealmAny.Type.STRING -> SerializableSample().apply {
                        nullableRealmAnyField = RealmAny.create(stringField)
                    }
                    RealmAny.Type.DECIMAL128 -> SerializableSample().apply {
                        nullableRealmAnyField = RealmAny.create(decimal128Field)
                    }
                    RealmAny.Type.TIMESTAMP -> SerializableSample().apply {
                        nullableRealmAnyField = RealmAny.create(timestampField)
                    }
                    RealmAny.Type.OBJECT -> SerializableSample().apply {
                        nullableRealmAnyField = RealmAny.create(bsonObjectIdField)
                    }
                    RealmAny.Type.UUID -> SerializableSample().apply {
                        nullableRealmAnyField = RealmAny.create(uuidField)
                    }
                    RealmAny.Type.OBJECT_ID -> SerializableSample().apply {
                        SerializableSample().let {
                            nullableObject = it
                            nullableRealmAnyField = RealmAny.create(it)
                        }
                    }
                    RealmAny.Type.OBJECT -> SerializableSample().apply {
                        SerializableSample().let {
                            nullableObject = it
                            nullableRealmAnyField = RealmAny.create(it)
                        }
                    }
                    RealmAny.Type.SET -> SerializableSample().apply {
                        nullableRealmAnyField = realmAnySetOf(1, 2, 3)
                    }
                    RealmAny.Type.LIST -> SerializableSample().apply {
                        nullableRealmAnyField = realmAnyListOf(RealmAny.create(1), RealmAny.create(2))
                    }
                    RealmAny.Type.DICTIONARY -> SerializableSample().apply {
                        nullableRealmAnyField = realmAnyDictionaryOf("key1" to RealmAny.create(1), "key2" to RealmAny.create(2))
                    }
                    else -> throw IllegalStateException("Untested type $type")
                }
            }
            .forEach { (type, expected) ->
                val encoded: String = json.encodeToString(expected)
                val decoded: SerializableSample = json.decodeFromString(encoded)

                RealmAny::class.assertValue(
                    expected.nullableRealmAnyField,
                    decoded.nullableRealmAnyField
                )
            }
    }

    private fun List<CollectionTypeSafetyManager<Any?>>.exhaustiveCollectionTesting() =
        forEach { dataset: CollectionTypeSafetyManager<Any?> ->
            listOf(
                dataset.createPrePopulatedContainer(), // Unmanaged value
                realm.writeBlocking { dataset.createPrePopulatedContainer() } // Managed value
            ).forEach { data ->
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

    /**
     * The following function exhaustively test all possible nullable or non-nullable values
     *
     * It does so, by serializing/deserializing a Realm object containing a dataset for an specific type.
     *
     * The process goes like:
     * - mapCollectionDataSets: For each field type create a CollectionTypeSafetyManager, a class
     *   that allows the creation of a RealmObject with a dataset for the given type.
     * - exhaustiveCollectionTesting: Instantiate a managed and an unmanaged RealmObjects, each one
     *   would be serialized and deserialized, and then validate that the deserialized and original
     *   values match.
     */
    @Test
    fun exhaustiveRealmListTest() {
        TypeDescriptor
            .allListFieldTypes
            .filterNot {
                // filter out as it deprecated and we don't provide a serializer
                it.elementType.classifier == ObjectId::class
            }
            .mapCollectionDataSets(
                properties = SerializableSample.listNonNullableProperties,
                nullableProperties = SerializableSample.listNullableProperties
            )
            .exhaustiveCollectionTesting()
    }

    /**
     * The following function exhaustively test all possible nullable or non-nullable values
     *
     * It does so, by serializing/deserializing a Realm object containing a dataset for an specific type.
     *
     * The process goes like:
     * - mapCollectionDataSets: For each field type create a CollectionTypeSafetyManager, a class
     *   that allows the creation of a RealmObject with a dataset for the given type.
     * - exhaustiveCollectionTesting: Instantiate a managed and an unmanaged RealmObjects, each one
     *   would be serialized and deserialized, and then validate that the deserialized and original
     *   values match.
     */
    @Test
    fun exhaustiveRealmSetTest() {
        TypeDescriptor
            .allSetFieldTypes
            .filterNot {
                // filter out as it deprecated and we don't provide a serializer
                it.elementType.classifier == ObjectId::class
            }
            .mapCollectionDataSets(
                properties = SerializableSample.setNonNullableProperties,
                nullableProperties = SerializableSample.setNullableProperties
            )
            .exhaustiveCollectionTesting()
    }

    /**
     * The following function exhaustively test all possible nullable or non-nullable values
     *
     * It does so, by serializing/deserializing a Realm object containing a dataset for an specific type.
     *
     * The process goes like:
     * - mapCollectionDataSets: For each field type create a CollectionTypeSafetyManager, a class
     *   that allows the creation of a RealmObject with a dataset for the given type.
     * - Instantiate a managed and an unmanaged RealmObjects, each one would be serialized and
     *   deserialized, and then validate that the deserialized and original values match.
     */
    @Test
    fun exhaustiveRealmDictTest() {
        TypeDescriptor
            .allDictionaryFieldTypes
            .filterNot {
                // filter out as it deprecated and we don't provide a serializer
                it.elementType.classifier == ObjectId::class
            }
            .map { fieldType: TypeDescriptor.RealmFieldType ->
                DictionaryTypeSafetyManager<Any?>(
                    dataSet = getDataSetForDictionaryClassifier(
                        fieldType.elementType.classifier,
                        fieldType.elementType.nullable,
                        OBJECT_VALUES
                    ),
                    property = when (fieldType.elementType.nullable) {
                        false -> SerializableSample.dictNonNullableProperties[fieldType.elementType.classifier]
                        true -> SerializableSample.dictNullableProperties[fieldType.elementType.classifier]
                    }!! as KMutableProperty1<SerializableSample, RealmDictionary<Any?>>,
                    classifier = fieldType.elementType.classifier
                )
            }
            .forEach { dataset: DictionaryTypeSafetyManager<Any?> ->
                listOf(
                    dataset.createPrePopulatedContainer(),
                    realm.writeBlocking {
                        dataset.createPrePopulatedContainer()
                    }
                ).forEach { data ->
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
}
