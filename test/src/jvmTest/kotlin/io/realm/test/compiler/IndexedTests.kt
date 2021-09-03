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

package io.realm.test.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.realm.interop.CollectionType
import io.realm.util.CompilerTest.compileFromSource
import io.realm.util.TypeDescriptor.allFieldTypes
import org.junit.Test
import kotlin.reflect.KClassifier
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IndexedTests {
    @Test
    fun `indexed supportness`() {
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
        )
        for (type in allFieldTypes) {
            // TODO Consider adding verification of compiler errors when marking collection
            //  types as primary keys
            if (type.collectionType != CollectionType.RLM_COLLECTION_TYPE_NONE) {
                continue
            }

            val elementType = type.elementType
            val default = if (!elementType.nullable) defaults[elementType.classifier] ?: error("unmapped default") else null

            val kotlinLiteral = type.toKotlinLiteral()
            val result = compileFromSource(
                plugins = listOf(io.realm.compiler.Registrar()),
                source = SourceFile.kotlin(
                    "indexing.kt",
                    """
                        import io.realm.RealmObject
                        import io.realm.RealmConfiguration
                        import io.realm.annotations.Indexed

                        class A : RealmObject {
                            @Indexed
                            var indexedKey: $kotlinLiteral = $default
                        }

                        val configuration =
                            RealmConfiguration(schema = setOf(A::class))
                    """.trimIndent()
                )
            )
            if (type.isIndexingSupported) {
                assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, type.toString())
            } else {
                assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, type.toString())
                assertTrue(result.messages.contains("but must be of type"))
            }
        }
    }
}
