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

package io.realm.kotlin.test.compiler.dictionary

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.realm.kotlin.compiler.CollectionType
import io.realm.kotlin.test.compiler.createFileAndCompile
import io.realm.kotlin.test.compiler.getCode
import io.realm.kotlin.test.compiler.getCodeForStarProjection
import io.realm.kotlin.test.util.Compiler.compileFromSource
import io.realm.kotlin.test.util.TypeDescriptor
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmObject
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Cannot trigger these from within the IDE due to https://youtrack.jetbrains.com/issue/KT-46195
// Execute the tests from the CLI with `./gradlew jvmTest`
class DictionaryTests {

    private val baseSupportedPrimitiveClasses = TypeDescriptor.elementTypesForDictionary
        .filter { it.classifier != RealmObject::class } // Cannot have "pure" RealmDictionary<RealmObject>

    private val nonNullableTypes = baseSupportedPrimitiveClasses
        .filter { it.classifier != RealmAny::class } // No non-nullable RealmDictionary<RealmAny> allowed
        .map { (it.classifier as KClass<*>).simpleName!! }
        .toSet() // Remove duplicates from nullable types
        .plus(listOf("SampleClass", "EmbeddedClass")) // Add object classes manually - see name in code strings in Utils.kt

    private val supportedPrimitiveTypes = baseSupportedPrimitiveClasses
        .map { (it.classifier as KClass<*>).simpleName!! }
        .toSet() // Remove duplicates from nullable types

    // ------------------------------------------------
    // RealmDictionary<E>
    // ------------------------------------------------

    // - supported types
    // TODO DONE
    @Test
    fun `non-nullable dictionary`() {
        // TODO optimize: see comment in TypeDescriptor.elementTypesForDictionary
        nonNullableTypes.forEach { nonNullableType ->
            val result = createFileAndCompile(
                "nonNullableDictionary.kt",
                getCode(
                    collectionType = CollectionType.DICTIONARY,
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
    // TODO DONE
    @Test
    fun `unsupported non-nullable dictionary - fails`() {
        val unsupportedNonNullableTypes =
            listOf(Exception::class.simpleName!!, RealmAny::class.simpleName!!)
        unsupportedNonNullableTypes.forEach { nonNullableType ->
            val result = createFileAndCompile(
                "unsupportedNonNullableDictionary.kt",
                getCode(
                    collectionType = CollectionType.DICTIONARY,
                    contentType = nonNullableType,
                    nullableContent = false,
                    nullableField = false
                )
            )
            assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
            assertTrue(result.messages.contains("Unsupported type for RealmDictionary"))
        }
    }

    // - Other unsupported types fail too
    // TODO DONE
    @Test
    fun `unsupported type in dictionary - fails`() {
        val result = compileFromSource(
            SourceFile.kotlin(
                "unsupportedTypeDictionary.kt",
                getCode(
                    collectionType = CollectionType.DICTIONARY,
                    contentType = "A",
                    nullableContent = false,
                    nullableField = false
                )
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("Unsupported type for RealmDictionary: 'A'"))
    }

    // ------------------------------------------------
    // RealmDictionary<E?>
    // ------------------------------------------------

    // - supported types
    @Test
    fun `nullable primitive type dictionary`() {
        supportedPrimitiveTypes.forEach { nullableType ->
            val result = createFileAndCompile(
                "nullableTypeDictionary.kt",
                getCode(
                    collectionType = CollectionType.DICTIONARY,
                    contentType = nullableType,
                    nullableContent = true,
                    nullableField = false
                )
            )
            assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        }
    }

    // - RealmObject works
    // Unlike lists and sets, dictionaries of objects/embedded objects may contain null values.
    @Test
    fun `nullable RealmObject dictionary`() {
        val result = createFileAndCompile(
            "nullableRealmObjectDictionary.kt",
            getCode(
                collectionType = CollectionType.DICTIONARY,
                contentType = "SampleClass",
                nullableContent = true,
                nullableField = false
            )
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    // - EmbeddedRealmObject works
    // Unlike lists and sets, dictionaries of objects/embedded objects may contain null values.
    @Test
    fun `nullable EmbeddedRealmObject dictionary`() {
        val result = createFileAndCompile(
            "nullableEmbeddedRealmObjectDictionary.kt",
            getCode(
                collectionType = CollectionType.DICTIONARY,
                contentType = "EmbeddedClass",
                nullableContent = true,
                nullableField = false
            )
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    // ------------------------------------------------
    // RealmDictionary<E>?
    // ------------------------------------------------

    // - nullable dictionary field fails
    @Test
    fun `nullable dictionaries - fails`() {
        supportedPrimitiveTypes.forEach { primitiveType ->
            val result = createFileAndCompile(
                "nullableDictionary.kt",
                getCode(
                    collectionType = CollectionType.DICTIONARY,
                    contentType = primitiveType,
                    nullableContent = false,
                    nullableField = true
                )
            )
            assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
            assertTrue(result.messages.contains("a RealmDictionary field cannot be marked as nullable"))
        }
    }

    // - star projection fails
    @Test
    fun `star projection dictionary - fails`() {
        // Test that a star-projected dictionary fails to compile
        // It is not possible to test a dictionary missing generics since this would not even compile
        val result = compileFromSource(
            SourceFile.kotlin(
                "starProjectionDictionary.kt",
                getCodeForStarProjection(CollectionType.DICTIONARY)
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmDictionary cannot use a '*' projection"))
    }
}
