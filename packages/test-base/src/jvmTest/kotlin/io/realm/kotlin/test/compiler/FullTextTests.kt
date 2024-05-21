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

@file:OptIn(ExperimentalCompilerApi::class)

package io.realm.kotlin.test.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.realm.kotlin.internal.interop.CollectionType
import io.realm.kotlin.test.util.Compiler.compileFromSource
import io.realm.kotlin.test.util.TypeDescriptor.allFieldTypes
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmUUID
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128
import kotlin.reflect.KClassifier
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FullTextTests {
    @Test
    fun fulltext_supported() {
        // TODO Consider placing these in PropertyDescriptor.kt for reuse
        val defaults = mapOf<KClassifier, Any>(
            Boolean::class to true,
            Byte::class to "1",
            Char::class to "\'c\'",
            Short::class to "1",
            Int::class to "1",
            Long::class to "1",
            Float::class to "1.4f",
            Double::class to "1.4",
            Decimal128::class to "BsonDecimal128(\"1.4E100\")",
            String::class to "\"Realm\"",
            RealmInstant::class to "RealmInstant.from(42, 420)",
            BsonObjectId::class to "BsonObjectId()",
            RealmUUID::class to "RealmUUID.random()",
            ByteArray::class to "byteArrayOf(42)",
            MutableRealmInt::class to "MutableRealmInt.create(42)",
            RealmAny::class to "RealmAny(42)"
        )
        for (type in allFieldTypes) {
            // TODO Consider adding verification of compiler errors when marking collection
            //  types as having an index
            if (type.collectionType != CollectionType.RLM_COLLECTION_TYPE_NONE) {
                continue
            }

            val elementType = type.elementType
            val default = if (!elementType.nullable) defaults[elementType.classifier]
                ?: error("unmapped default") else null

            val kotlinLiteral = type.toKotlinLiteral()
            val result = compileFromSource(
                plugins = listOf(io.realm.kotlin.compiler.Registrar()),
                source = SourceFile.kotlin(
                    "fulltext.kt",
                    """
                        import io.realm.kotlin.types.MutableRealmInt
                        import io.realm.kotlin.types.RealmAny
                        import io.realm.kotlin.types.RealmInstant
                        import io.realm.kotlin.types.RealmObject
                        import io.realm.kotlin.types.RealmUUID
                        import io.realm.kotlin.types.annotations.FullText
                        import io.realm.kotlin.RealmConfiguration
                        import org.mongodb.kbson.BsonObjectId
                        import org.mongodb.kbson.BsonDecimal128

                        class A : RealmObject {
                            @FullText
                            var fulltextKey: $kotlinLiteral = $default
                        }

                        val configuration =
                            RealmConfiguration.create(schema = setOf(A::class))
                    """.trimIndent()
                )
            )
            if (type.isFullTextSupported) {
                assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
            } else {
                assertEquals(
                    KotlinCompilation.ExitCode.COMPILATION_ERROR,
                    result.exitCode,
                    type.toString()
                )
                assertTrue(result.messages.contains(Regex("Full-text key .* is of type .* but must be of type")), result.messages)
            }
        }
    }

    @Test
    fun fulltext_and_index_cannot_be_combined() {
        val result = compileFromSource(
            plugins = listOf(io.realm.kotlin.compiler.Registrar()),
            source = SourceFile.kotlin(
                "fulltext_index.kt",
                """
                        import io.realm.kotlin.types.RealmObject
                        import io.realm.kotlin.RealmConfiguration
                        import io.realm.kotlin.types.annotations.FullText
                        import io.realm.kotlin.types.annotations.Index

                        class A : RealmObject {
                            @FullText
                            @Index
                            var fullTextKey: String = ""
                        }

                        val configuration =
                            RealmConfiguration.create(schema = setOf(A::class))
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(result.messages.contains("@FullText and @Index cannot be combined on property fullTextKey"), result.messages)
    }

    @Test
    fun fulltext_and_primarykey_cannot_be_combined() {
        val result = compileFromSource(
            plugins = listOf(io.realm.kotlin.compiler.Registrar()),
            source = SourceFile.kotlin(
                "fulltext_primarykey.kt",
                """
                        import io.realm.kotlin.types.RealmObject
                        import io.realm.kotlin.RealmConfiguration
                        import io.realm.kotlin.types.annotations.FullText
                        import io.realm.kotlin.types.annotations.PrimaryKey

                        class A : RealmObject {
                            @FullText
                            @PrimaryKey
                            var fullTextKey: String = ""
                        }

                        val configuration =
                            RealmConfiguration.create(schema = setOf(A::class))
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(result.messages.contains("@PrimaryKey and @FullText cannot be combined on property fullTextKey"), result.messages)
    }

    @Test
    fun fulltext_collections_unsupported() {
        val collectionsAndDefaults = listOf(
            "RealmList" to "realmListOf()",
            "RealmSet" to "realmSetOf()"
        )
        for (pair in collectionsAndDefaults) {
            val result = compileFromSource(
                plugins = listOf(io.realm.kotlin.compiler.Registrar()),
                source = SourceFile.kotlin(
                    "fulltext_collections.kt",
                    """
                        import io.realm.kotlin.types.RealmObject
                        import io.realm.kotlin.types.RealmList
                        import io.realm.kotlin.types.RealmSet
                        import io.realm.kotlin.ext.realmListOf
                        import io.realm.kotlin.ext.realmSetOf
                        import io.realm.kotlin.RealmConfiguration
                        import io.realm.kotlin.types.annotations.FullText

                        class A : RealmObject {
                            @FullText
                            var fullTextKey: ${pair.first}<Char> = ${pair.second}
                        }

                        val configuration =
                            RealmConfiguration.create(schema = setOf(A::class))
                    """.trimIndent()
                )
            )
            assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, "anyType")
            assertTrue(result.messages.contains("Full-text key fullTextKey is of type ${pair.first} but must be of type"), result.messages)
        }
    }
}
