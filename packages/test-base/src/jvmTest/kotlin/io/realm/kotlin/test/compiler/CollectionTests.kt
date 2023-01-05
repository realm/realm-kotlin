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
import io.realm.kotlin.compiler.CollectionType
import io.realm.kotlin.test.util.Compiler
import io.realm.kotlin.test.util.TypeDescriptor
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmObject
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal val baseSupportedPrimitiveClasses = TypeDescriptor.elementTypesForList
    .filter { it.classifier != RealmObject::class } // Cannot have "pure" Collection<RealmObject>

internal val nonNullableTypes = baseSupportedPrimitiveClasses
    .filter { it.classifier != RealmAny::class } // No non-nullable RealmList<RealmAny> allowed
    .map { (it.classifier as KClass<*>).simpleName!! }
    .toSet() // Remove duplicates from nullable types

internal val supportedPrimitiveTypes = baseSupportedPrimitiveClasses
    .map { (it.classifier as KClass<*>).simpleName!! }
    .toSet() // Remove duplicates from nullable types

/**
 * These tests are shared across collections and dictionaries.
 *
 * Logic-specific tests should be added in children classes. For example nullability of
 * RealmObject/EmbeddedRealmObject dictionaries should be tested only in DictionaryTests since
 * RealmSet doesn't support EmbeddedObjects.
 */
abstract class CollectionTests(
    private val collectionType: CollectionType,
    private val nonNullableTypes: Set<String>
) {

    init {
        if (collectionType == CollectionType.NONE) {
            throw IllegalArgumentException("Only collections can be tested here")
        }
    }

    // ------------------------------------------------
    // Collection<E>
    // ------------------------------------------------

    // - supported types
    @Test
    fun `non-nullable collection`() {
        // TODO optimize: see comment in TypeDescriptor.elementTypesForDictionary
        nonNullableTypes.forEach { nonNullableType ->
            val result = createFileAndCompile(
                "nonNullableCollection.kt",
                getCode(
                    collectionType = collectionType,
                    contentType = nonNullableType,
                    nullableContent = false,
                    nullableField = false
                )
            )
            assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        }
    }

    // - RealmAny fails if non-nullable (mixed is always non-null)
    // - Other unsupported types fail too (nullability is irrelevant in this case)
    @Test
    fun `unsupported non-nullable collection - fails`() {
        listOf(Exception::class.simpleName!!, RealmAny::class.simpleName!!)
            .forEach { nonNullableType ->
                val result = createFileAndCompile(
                    "unsupportedNonNullableCollection.kt",
                    getCode(
                        collectionType = collectionType,
                        contentType = nonNullableType,
                        nullableContent = false,
                        nullableField = false
                    )
                )
                assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
                assertTrue(result.messages.contains("Unsupported type for ${collectionType.description}"))
            }
    }

    // - Other unsupported types fail too
    @Test
    fun `unsupported type in collection - fails`() {
        val result = Compiler.compileFromSource(
            SourceFile.kotlin(
                "unsupportedTypeCollection.kt",
                getCode(
                    collectionType = collectionType,
                    contentType = "A",
                    nullableContent = false,
                    nullableField = false
                )
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("Unsupported type for ${collectionType.description}: 'A'"))
    }

    // ------------------------------------------------
    // Collection<E?>
    // ------------------------------------------------

    // - supported types
    @Test
    fun `nullable primitive type collection`() {
        supportedPrimitiveTypes.forEach { nullableType ->
            val result = createFileAndCompile(
                "nullableTypeCollection.kt",
                getCode(
                    collectionType = collectionType,
                    contentType = nullableType,
                    nullableContent = true,
                    nullableField = false
                )
            )
            assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        }
    }

    // ------------------------------------------------
    // Collection<E>?
    // ------------------------------------------------

    // - nullable collection field fails
    @Test
    fun `nullable collection field - fails`() {
        supportedPrimitiveTypes.forEach { primitiveType ->
            val result = createFileAndCompile(
                "nullableCollection.kt",
                getCode(
                    collectionType = collectionType,
                    contentType = primitiveType,
                    nullableContent = false,
                    nullableField = true
                )
            )
            assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
            assertTrue(result.messages.contains("a ${collectionType.description} field cannot be marked as nullable"))
        }
    }

    // - star projection fails
    @Test
    fun `star projection collection - fails`() {
        // Test that a star-projected set fails to compile
        // It is not possible to test a set missing generics since this would not even compile
        val result = Compiler.compileFromSource(
            SourceFile.kotlin(
                "starProjectionCollection.kt",
                getCode(collectionType, userStarProjection = true)
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("${collectionType.description} cannot use a '*' projection"))
    }
}
