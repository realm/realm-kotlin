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

package io.realm.kotlin.test.compiler.set

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
class SetTests {

    private val baseSupportedPrimitiveClasses = TypeDescriptor.elementTypesForList
        .filter { it.classifier != RealmObject::class } // Cannot have "pure" RealmSet<RealmObject>

    private val nonNullableTypes = baseSupportedPrimitiveClasses
        .filter { it.classifier != RealmAny::class } // No non-nullable RealmSet<RealmAny> allowed
        .map { (it.classifier as KClass<*>).simpleName!! }
        .toSet() // Remove duplicates from nullable types
        .plus(listOf("SampleClass")) // Add object class manually - see name in code strings in Utils.kt

    private val supportedPrimitiveTypes = baseSupportedPrimitiveClasses
        .map { (it.classifier as KClass<*>).simpleName!! }
        .toSet() // Remove duplicates from nullable types

    // ------------------------------------------------
    // RealmSet<E>
    // ------------------------------------------------

    // - supported types
    // TODO DONE
    @Test
    fun `non-nullable set`() {
        // TODO optimize: see comment in TypeDescriptor.elementTypesForSet
        nonNullableTypes.forEach { nonNullableType ->
            val result = createFileAndCompile(
                "nonNullableSet.kt",
                getCode(
                    collectionType = CollectionType.SET,
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
    fun `unsupported non-nullable set - fails`() {
        val unsupportedNonNullableTypes =
            listOf(Exception::class.simpleName!!, RealmAny::class.simpleName!!)
        unsupportedNonNullableTypes.forEach { nonNullableType ->
            val result = createFileAndCompile(
                "unsupportedNonNullableSet.kt",
                getCode(
                    collectionType = CollectionType.SET,
                    contentType = nonNullableType,
                    nullableContent = false,
                    nullableField = false
                )
            )
            assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
            assertTrue(result.messages.contains("Unsupported type for RealmSet"))
        }
    }

    // - Other unsupported types fail too
    // TODO DONE
    @Test
    fun `unsupported type in set - fails`() {
        val result = compileFromSource(
            SourceFile.kotlin(
                "unsupportedTypeSet.kt",
                getCode(
                    collectionType = CollectionType.SET,
                    contentType = "A",
                    nullableContent = false,
                    nullableField = false
                )
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("Unsupported type for RealmSet: 'A'"))
    }

    // - Embedded objects fail
    @Test
    fun `unsupported type in set - EmbeddedRealmObject fails`() {
        val result = compileFromSource(
            SourceFile.kotlin(
                "unsupportedEmbeddedRealmObjectSet.kt",
                getCode(
                    collectionType = CollectionType.SET,
                    contentType = "EmbeddedClass",
                    nullableContent = false,
                    nullableField = false
                )
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmSet does not support embedded realm objects element types"))
    }

    // - nullable Embedded objects fail
    @Test
    fun `unsupported type in set - nullable EmbeddedRealmObject fails`() {
        val result = compileFromSource(
            SourceFile.kotlin(
                "unsupportedEmbeddedRealmObjectSet.kt",
                getCode(
                    collectionType = CollectionType.SET,
                    contentType = "EmbeddedClass",
                    nullableContent = true,
                    nullableField = false
                )
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmSet does not support embedded realm objects element types"))
    }

    // ------------------------------------------------
    // RealmSet<E?>
    // ------------------------------------------------

    // - supported types
    // TODO DONE
    @Test
    fun `nullable primitive type set`() {
        supportedPrimitiveTypes.forEach { nullableType ->
            val result = createFileAndCompile(
                "nullableTypeSet.kt",
                getCode(
                    collectionType = CollectionType.SET,
                    contentType = nullableType,
                    nullableContent = true,
                    nullableField = false
                )
            )
            assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        }
    }

    // - RealmObject fails
    @Test
    fun `nullable RealmObject set - fails`() {
        val result = createFileAndCompile(
            "nullableRealmObjectSet.kt",
            getCode(
                collectionType = CollectionType.SET,
                contentType = "SampleClass",
                nullableContent = true,
                nullableField = false
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmSet does not support nullable realm objects element types"))
    }

    // ------------------------------------------------
    // RealmSet<E>?
    // ------------------------------------------------

    // - nullable sets fail
    @Test
    fun `nullable sets - fails`() {
        supportedPrimitiveTypes.forEach { primitiveType ->
            val result = createFileAndCompile(
                "nullableSet.kt",
                getCode(
                    collectionType = CollectionType.SET,
                    contentType = primitiveType,
                    nullableContent = false,
                    nullableField = true
                )
            )
            assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
            assertTrue(result.messages.contains("a RealmSet field cannot be marked as nullable"))
        }
    }

    // - star projection fails
    @Test
    fun `star projection set - fails`() {
        // Test that a star-projected set fails to compile
        // It is not possible to test a set missing generics since this would not even compile
        val result = compileFromSource(
            SourceFile.kotlin(
                "starProjectionSet.kt",
                getCodeForStarProjection(CollectionType.SET)
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmSet cannot use a '*' projection"))
    }
}
