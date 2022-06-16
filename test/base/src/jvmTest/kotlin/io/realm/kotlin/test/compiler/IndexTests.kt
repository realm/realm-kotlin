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

package io.realm.kotlin.test.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.realm.kotlin.internal.interop.CollectionType
import io.realm.kotlin.test.util.Compiler.compileFromSource
import io.realm.kotlin.test.util.TypeDescriptor.allFieldTypes
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmUUID
import org.junit.Test
import kotlin.reflect.KClassifier
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IndexTests {
    @Test
    fun `index supportness`() {
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
            String::class to "\"Realm\"",
            RealmInstant::class to "RealmInstant.from(42, 420)",
            ObjectId::class to "ObjectId.create()",
            RealmUUID::class to "RealmUUID.random()",
        )
        for (type in allFieldTypes) {
            // TODO Consider adding verification of compiler errors when marking collection
            //  types as having an index
            if (type.collectionType != CollectionType.RLM_COLLECTION_TYPE_NONE) {
                continue
            }

            val elementType = type.elementType
            val default = if (!elementType.nullable) defaults[elementType.classifier] ?: error("unmapped default") else null

            val kotlinLiteral = type.toKotlinLiteral()
            val result = compileFromSource(
                plugins = listOf(io.realm.kotlin.compiler.Registrar()),
                source = SourceFile.kotlin(
                    "indexing.kt",
                    """
                        import io.realm.kotlin.types.RealmInstant
                        import io.realm.kotlin.types.ObjectId
                        import io.realm.kotlin.types.RealmObject
                        import io.realm.kotlin.types.RealmUUID
                        import io.realm.kotlin.RealmConfiguration
                        import io.realm.kotlin.types.annotations.Index

                        class A : RealmObject {
                            @Index
                            var indexedKey: $kotlinLiteral = $default
                        }

                        val configuration =
                            RealmConfiguration.create(schema = setOf(A::class))
                    """.trimIndent()
                )
            )
            if (type.isIndexingSupported) {
                assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
            } else {
                assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, type.toString())
                assertTrue(result.messages.contains(Regex("sources/indexing.kt: \\(9, 5\\): .*but must be of type")))
            }
        }
    }

    @Test
    fun `index_collection_unsupported`() {
        val result = compileFromSource(
            plugins = listOf(io.realm.kotlin.compiler.Registrar()),
            source = SourceFile.kotlin(
                "indexing_collections.kt",
                """
                        import io.realm.kotlin.types.RealmObject
                        import io.realm.kotlin.types.RealmList
                        import io.realm.kotlin.ext.realmListOf
                        import io.realm.kotlin.RealmConfiguration
                        import io.realm.kotlin.types.annotations.Index

                        class A : RealmObject {
                            @Index
                            var indexedKey: RealmList<Char> = realmListOf()
                        }

                        val configuration =
                            RealmConfiguration.create(schema = setOf(A::class))
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, "anyType")
        assertTrue(result.messages.contains("sources/indexing_collections.kt: (8, 5): [Realm] Indexed key indexedKey is of type RealmList but must be of type"))
    }
}
