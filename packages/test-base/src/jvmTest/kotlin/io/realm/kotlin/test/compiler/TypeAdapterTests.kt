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

package io.realm.kotlin.test.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.realm.kotlin.internal.interop.CollectionType
import io.realm.kotlin.test.util.Compiler.compileFromSource
import io.realm.kotlin.test.util.TypeDescriptor
import io.realm.kotlin.test.util.TypeDescriptor.allFieldTypes
import io.realm.kotlin.test.util.TypeDescriptor.unsupportedRealmTypeAdaptersClassifiers
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import org.junit.Test
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128
import kotlin.reflect.KClassifier
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * These tests should validate:
 *  - [x] Adapter with a non-realm type should fail
 *  - [x] Adapter annotation on unsupported types: delegate, function etc
 *  - [x] Adapters type supportness
 *  - [x] Adapters type unsupportness
 *  - [x] Adapter not matching public type
 *  - [x] Instanced and singleton adapters
 *  - [x] Other annotations Ignore, Index etc
 *  - [x] Star projections in persistable types
 */
class TypeAdapterTests {

    private val typeAdapterTypes = listOf("object", "class")

    @Test
    fun `invalid persisted type`() {
        typeAdapterTypes.forEach { type ->
            val result = compileFromSource(
                plugins = listOf(io.realm.kotlin.compiler.Registrar()),
                source = SourceFile.kotlin(
                    "typeAdapter_unsupported_type.kt",
                    """
                    import io.realm.kotlin.types.RealmInstant
                    import io.realm.kotlin.types.RealmObject
                    import io.realm.kotlin.types.RealmTypeAdapter
                    import io.realm.kotlin.types.annotations.TypeAdapter
                       
                    class UserType
                    
                    class NonRealmType
                    
                    $type UnsupportedRealmTypeParameter : RealmTypeAdapter<NonRealmType, UserType> {
                        override fun fromRealm(realmValue: NonRealmType): UserType = TODO()
                    
                        override fun toRealm(value: UserType): NonRealmType = TODO()
                    }
                    """.trimIndent()
                )
            )
            assertEquals(
                KotlinCompilation.ExitCode.COMPILATION_ERROR,
                result.exitCode,
                result.messages
            )
            assertTrue(
                result.messages.contains("Invalid adapter persisted type 'NonRealmType', only Realm persistable types are supported."),
                result.messages
            )
        }
    }

    @Test
    fun `not matching user type`() {
        typeAdapterTypes.forEach { type ->
            val result = compileFromSource(
                plugins = listOf(io.realm.kotlin.compiler.Registrar()),
                source = SourceFile.kotlin(
                    "typeAdapter_unsupported_type.kt",
                    """
                    import io.realm.kotlin.types.RealmInstant
                    import io.realm.kotlin.types.RealmObject
                    import io.realm.kotlin.types.RealmTypeAdapter
                    import io.realm.kotlin.types.annotations.TypeAdapter
                       
                    class UserType
                    
                    class OtherUserType
                    
                    class TestObject : RealmObject {
                        @TypeAdapter(adapter = UserTypeAdapter::class)
                        var userType: OtherUserType = OtherUserType()
                    }
                    
                    $type UserTypeAdapter : RealmTypeAdapter<String, UserType> {
                        override fun fromRealm(realmValue: String): UserType = TODO()
                    
                        override fun toRealm(value: UserType): String = TODO()
                    }
                    """.trimIndent()
                )
            )
            assertEquals(
                KotlinCompilation.ExitCode.COMPILATION_ERROR,
                result.exitCode,
                result.messages
            )
            assertTrue(result.messages.contains("Type adapter public type does not match the property type."), result.messages)
        }
    }

    @Test
    fun `applying adapter on backlinked property should fail`() {
        val result = compileFromSource(
            plugins = listOf(io.realm.kotlin.compiler.Registrar()),
            source = SourceFile.kotlin(
                "typeAdapter_on_backlinks_fail.kt",
                """
                    import io.realm.kotlin.ext.backlinks
                    import io.realm.kotlin.types.RealmInstant
                    import io.realm.kotlin.types.RealmObject
                    import io.realm.kotlin.types.RealmTypeAdapter
                    import io.realm.kotlin.types.annotations.TypeAdapter
                       
                    class UserType
                    
                    class TestObject1: RealmObject {
                        var myObject: TestObject2 = TestObject2()
                    }
                    
                    class TestObject2 : RealmObject {
                        @TypeAdapter(adapter = ValidRealmTypeAdapter::class)
                        val userType by backlinks(TestObject1::myObject)
                    }
                    
                    object ValidRealmTypeAdapter : RealmTypeAdapter<String, UserType> {
                        override fun fromRealm(realmValue: String): UserType = TODO()
                    
                        override fun toRealm(value: UserType): String = TODO()
                    }
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(
            result.messages.contains("Type adapters do not support delegated properties"),
            result.messages
        )
    }

    @Test
    fun `validate all adapters supported types`() {
        val defaults = mapOf<KClassifier, Any>(
            Boolean::class to true,
            Long::class to "1",
            Float::class to "1.4f",
            Double::class to "1.4",
            Decimal128::class to "BsonDecimal128(\"1.4E100\")",
            String::class to "\"Realm\"",
            RealmInstant::class to "RealmInstant.from(42, 420)",
            ObjectId::class to "ObjectId.create()",
            BsonObjectId::class to "BsonObjectId()",
            RealmUUID::class to "RealmUUID.random()",
            ByteArray::class to "byteArrayOf(42)",
            RealmObject::class to "TestObject2()",
        )

        allFieldTypes
            .filterNot { type ->
                type.elementType.classifier in unsupportedRealmTypeAdaptersClassifiers
            }
            .forEach { type ->
                val elementType = type.elementType
                val default = if (!elementType.nullable) defaults[elementType.classifier]
                    ?: error("unmapped default") else null

                val kotlinLiteral = if (type.elementType.classifier == RealmObject::class) {
                    type.toKotlinLiteral().replace("RealmObject", "TestObject2")
                } else {
                    type.toKotlinLiteral()
                }

                val additionalAnnotations = mutableSetOf(
                    "",
                    """@PersistedName("testing")"""
                ).apply {
                    if (type.isIndexingSupported) add("@Index")
                    if (type.isPrimaryKeySupported) add("@PrimaryKey")
                    if (type.isFullTextSupported) add("@FullText")
                }

                additionalAnnotations.forEach { annotation ->
                    val result = compileFromSource(
                        plugins = listOf(io.realm.kotlin.compiler.Registrar()),
                        source = SourceFile.kotlin(
                            "typeadapter_supportness_$kotlinLiteral.kt",
                            """
                    import io.realm.kotlin.types.RealmAny
                    import io.realm.kotlin.types.RealmDictionary
                    import io.realm.kotlin.types.RealmList
                    import io.realm.kotlin.types.RealmSet
                    import io.realm.kotlin.types.RealmInstant
                    import io.realm.kotlin.types.MutableRealmInt
                    import io.realm.kotlin.types.RealmObject
                    import io.realm.kotlin.types.RealmTypeAdapter
                    import io.realm.kotlin.types.RealmUUID 
                    import io.realm.kotlin.types.annotations.FullText
                    import io.realm.kotlin.types.annotations.Index
                    import io.realm.kotlin.types.annotations.PersistedName
                    import io.realm.kotlin.types.annotations.PrimaryKey
                    import io.realm.kotlin.types.annotations.TypeAdapter
                    import io.realm.kotlin.types.ObjectId
                    import org.mongodb.kbson.BsonDecimal128
                    import org.mongodb.kbson.BsonObjectId
                       
                    class UserType
                    
                    class NonRealmType
                    
                    class TestObject2: RealmObject {
                        var name: String = ""
                    }
                    
                    class TestObject : RealmObject {
                        $annotation
                        @TypeAdapter(adapter = ValidRealmTypeAdapter::class)
                        var userType: UserType = UserType()
                    }
                    
                    object ValidRealmTypeAdapter : RealmTypeAdapter<$kotlinLiteral, UserType> {
                        override fun fromRealm(realmValue: $kotlinLiteral): UserType = TODO()
                    
                        override fun toRealm(value: UserType): $kotlinLiteral = TODO()
                    }
                            """.trimIndent()
                        )
                    )
                    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
                }
            }
    }

    @Test
    fun `validate all adapters unsupported types`() {
        val defaults = mapOf<KClassifier, Any>(
            Byte::class to "1",
            Char::class to "\'c\'",
            Short::class to "1",
            Int::class to "1",
            MutableRealmInt::class to "MutableRealmInt.create(42)",
        )

        allFieldTypes
            .filter { type ->
                type.elementType.classifier in unsupportedRealmTypeAdaptersClassifiers
            }
            .forEach { type ->
                val elementType = type.elementType
                val default = if (!elementType.nullable) defaults[elementType.classifier]
                    ?: error("unmapped default") else null

                val kotlinLiteral = if (type.elementType.classifier == RealmObject::class) {
                    type.toKotlinLiteral().replace("RealmObject", "TestObject2")
                } else {
                    type.toKotlinLiteral()
                }

                val result = compileFromSource(
                    plugins = listOf(io.realm.kotlin.compiler.Registrar()),
                    source = SourceFile.kotlin(
                        "typeadapter_supportness_$kotlinLiteral.kt",
                        """
                    import io.realm.kotlin.types.RealmAny
                    import io.realm.kotlin.types.RealmDictionary
                    import io.realm.kotlin.types.RealmList
                    import io.realm.kotlin.types.RealmSet
                    import io.realm.kotlin.types.RealmInstant
                    import io.realm.kotlin.types.MutableRealmInt
                    import io.realm.kotlin.types.RealmObject
                    import io.realm.kotlin.types.RealmTypeAdapter
                    import io.realm.kotlin.types.RealmUUID 
                    import io.realm.kotlin.types.annotations.TypeAdapter
                    import io.realm.kotlin.types.ObjectId
                    import org.mongodb.kbson.BsonDecimal128
                    import org.mongodb.kbson.BsonObjectId
                       
                    class UserType
                    
                    class NonRealmType
                    
                    class TestObject2: RealmObject {
                        var name: String = ""
                    }
                    
                    class TestObject : RealmObject {
                        @TypeAdapter(adapter = ValidRealmTypeAdapter::class)
                        var userType: UserType = UserType()
                    }
                    
                    object ValidRealmTypeAdapter : RealmTypeAdapter<$kotlinLiteral, UserType> {
                        override fun fromRealm(realmValue: $kotlinLiteral): UserType = TODO()
                    
                        override fun toRealm(value: UserType): $kotlinLiteral = TODO()
                    }
                        """.trimIndent()
                    )
                )
                assertEquals(
                    KotlinCompilation.ExitCode.COMPILATION_ERROR,
                    result.exitCode,
                    result.messages
                )
                assertTrue(result.messages.contains("only Realm persistable types are supported"), result.messages)
            }
    }

    @Test
    fun `type adapter with star projection`() {
        CollectionType.values()
            .filterNot { collectionType ->
                collectionType == CollectionType.RLM_COLLECTION_TYPE_NONE
            }
            .map { collectionType ->
                TypeDescriptor.RealmFieldType(
                    collectionType = collectionType,
                    elementType = TypeDescriptor.ElementType(
                        classifier = String::class,
                        nullable = false
                    )
                )
            }
            .map {
                it.toKotlinLiteral().replace("String", "*")
            }
            .forEach { kotlinLiteral ->
                val result = compileFromSource(
                    plugins = listOf(io.realm.kotlin.compiler.Registrar()),
                    source = SourceFile.kotlin(
                        "typeadapter_supportness_$kotlinLiteral.kt",
                        """
                    import io.realm.kotlin.types.RealmAny
                    import io.realm.kotlin.types.RealmDictionary
                    import io.realm.kotlin.types.RealmList
                    import io.realm.kotlin.types.RealmSet
                    import io.realm.kotlin.types.RealmInstant
                    import io.realm.kotlin.types.MutableRealmInt
                    import io.realm.kotlin.types.RealmObject
                    import io.realm.kotlin.types.RealmTypeAdapter
                    import io.realm.kotlin.types.RealmUUID
                    import io.realm.kotlin.types.annotations.TypeAdapter
                    import io.realm.kotlin.types.ObjectId
                    import org.mongodb.kbson.BsonDecimal128
                    import org.mongodb.kbson.BsonObjectId

                    class UserType

                    object ValidRealmTypeAdapter : RealmTypeAdapter<$kotlinLiteral, UserType> {
                        override fun fromRealm(realmValue: $kotlinLiteral): UserType = TODO()

                        override fun toRealm(value: UserType): $kotlinLiteral = TODO()
                    }
                        """.trimIndent()
                    )
                )
                assertEquals(
                    KotlinCompilation.ExitCode.COMPILATION_ERROR,
                    result.exitCode,
                    result.messages
                )
                assertTrue(result.messages.contains("cannot use a '*' projection"), result.messages)
            }
    }

    @Test
    fun `apply annotation to type parameter`() {
        allFieldTypes
            .filterNot { type ->
                type.elementType.classifier in unsupportedRealmTypeAdaptersClassifiers
            }
            .filterNot { type ->
                type.collectionType == CollectionType.RLM_COLLECTION_TYPE_NONE
            }
            .forEach { type ->
                val default = when (type.collectionType) {
                    CollectionType.RLM_COLLECTION_TYPE_NONE -> error("Outside of testing scope")
                    CollectionType.RLM_COLLECTION_TYPE_LIST -> "RealmList<@TypeAdapter(ValidRealmTypeAdapter::class) UserType> = realmListOf()"
                    CollectionType.RLM_COLLECTION_TYPE_SET -> "RealmSet<@TypeAdapter(ValidRealmTypeAdapter::class) UserType> = realmSetOf()"
                    CollectionType.RLM_COLLECTION_TYPE_DICTIONARY -> "RealmDictionary<@TypeAdapter(ValidRealmTypeAdapter::class) UserType> = realmDictionaryOf()"
                    else -> error("Unmapped type")
                }

                val adapterType = when (type.elementType.classifier) {
                    RealmObject::class -> "TestObject2${if (type.elementType.nullable) "?" else ""}"
                    else -> type.copy(
                        collectionType = CollectionType.RLM_COLLECTION_TYPE_NONE
                    ).toKotlinLiteral()
                }

                val additionalAnnotations = mutableSetOf(
                    "",
                    """@PersistedName("testing")"""
                )

                additionalAnnotations.forEach { annotation ->
                    val result = compileFromSource(
                        plugins = listOf(io.realm.kotlin.compiler.Registrar()),
                        source = SourceFile.kotlin(
                            "typeadapter_supportness_$adapterType.kt",
                            """
                    import io.realm.kotlin.ext.realmDictionaryOf
                    import io.realm.kotlin.ext.realmListOf
                    import io.realm.kotlin.ext.realmSetOf
                    import io.realm.kotlin.types.RealmAny
                    import io.realm.kotlin.types.RealmDictionary
                    import io.realm.kotlin.types.RealmList
                    import io.realm.kotlin.types.RealmSet
                    import io.realm.kotlin.types.RealmInstant
                    import io.realm.kotlin.types.MutableRealmInt
                    import io.realm.kotlin.types.RealmObject
                    import io.realm.kotlin.types.RealmTypeAdapter
                    import io.realm.kotlin.types.RealmUUID 
                    import io.realm.kotlin.types.annotations.FullText
                    import io.realm.kotlin.types.annotations.Index
                    import io.realm.kotlin.types.annotations.PersistedName
                    import io.realm.kotlin.types.annotations.PrimaryKey
                    import io.realm.kotlin.types.annotations.TypeAdapter
                    import io.realm.kotlin.types.ObjectId
                    import org.mongodb.kbson.BsonDecimal128
                    import org.mongodb.kbson.BsonObjectId
                       
                    class UserType
                    
                    class TestObject2: RealmObject {
                        var field: String = ""
                    }
                    
                    class TestObject : RealmObject {
                        $annotation
                        var userType: $default
                    }
                    
                    object ValidRealmTypeAdapter : RealmTypeAdapter<$adapterType, UserType> {
                        override fun fromRealm(realmValue: $adapterType): UserType = TODO()
                    
                        override fun toRealm(value: UserType): $adapterType = TODO()
                    }
                            """.trimIndent()
                        )
                    )
                    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
                }
            }
    }
}
