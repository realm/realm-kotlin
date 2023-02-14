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
import io.realm.kotlin.entities.link.Child
import io.realm.kotlin.entities.link.Parent
import io.realm.kotlin.entities.list.RealmListContainer
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
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
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128
import kotlin.reflect.KClassifier
import kotlin.reflect.KMutableProperty1
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

    private val listTesters = TypeDescriptor
        .allListFieldTypes
        .map {
            val elementType = it.elementType
            when (val classifier = elementType.classifier) {
                RealmObject::class -> SerializableListTypeSafetyManager(
                    classifier = classifier,
                    property = SerializableSample::objectListField,
                    dataSetToLoad = OBJECT_VALUES.map {
                        SerializableSample().apply {
                            stringField = it.stringField
                        }
                    }
                )
                ByteArray::class -> getTypeSafety(
                    classifier,
                    elementType.nullable
                )
                RealmAny::class -> SerializableListTypeSafetyManager(
                    classifier = classifier,
                    property = SerializableSample.nullableProperties[classifier]!!,
                    dataSetToLoad = getDataSetForClassifier(classifier, true)
                )
                else -> getTypeSafety(classifier, elementType.nullable)
            }
        }

    private fun getTypeSafety(
        classifier: KClassifier,
        nullable: Boolean
    ): SerializableListTypeSafetyManager<*> =
        when {
            nullable -> SerializableListTypeSafetyManager(
                classifier = classifier,
                property = SerializableSample.nullableProperties[classifier]!!,
                dataSetToLoad = getDataSetForClassifier(classifier, true)
            )
            else -> SerializableListTypeSafetyManager(
                classifier = classifier,
                property = SerializableSample.nonNullableProperties[classifier]!!,
                dataSetToLoad = getDataSetForClassifier(classifier, false)
            )
        }

    @Suppress("UNCHECKED_CAST", "ComplexMethod")
    private fun <T> getDataSetForClassifier(
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
        RealmObject::class -> OBJECT_VALUES
        RealmAny::class -> REALM_ANY_PRIMITIVE_VALUES + REALM_ANY_REALM_OBJECT
        else -> throw IllegalArgumentException("Wrong classifier: '$classifier'")
    } as List<T>

    private val REALM_ANY_REALM_OBJECT = RealmAny.create(
        SerializableSample().apply { stringField = "hello" },
        SerializableSample::class
    )

    internal class SerializableListTypeSafetyManager<T>(
        val classifier: KClassifier,
        override val property: KMutableProperty1<SerializableSample, RealmList<T>>,
        override val dataSetToLoad: List<T>
    ) : GenericTypeSafetyManager<T, SerializableSample, RealmList<T>> {

        override fun toString(): String = property.name

        override fun getCollection(container: SerializableSample): RealmList<T> =
            property.get(container)

        override fun createContainerAndGetCollection(realm: MutableRealm): RealmList<T> {
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

    @Test
    fun exhaustiveRealmListTester() {
        for (tester in listTesters) {
            val data = tester.createPrePopulatedContainer()

            val encoded: String = json.encodeToString(data)
            val decoded: SerializableSample = json.decodeFromString(encoded)

            val originalCollection = tester.getCollection(data)
            val decodedCollection = tester.getCollection(decoded)

            originalCollection.forEachIndexed { index, element ->
                tester.classifier.assertValue(element, decodedCollection[index])
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
            else -> assertEquals(expected, actual)
        }
    }

}