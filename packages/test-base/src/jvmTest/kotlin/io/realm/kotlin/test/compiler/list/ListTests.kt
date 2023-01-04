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

package io.realm.kotlin.test.compiler.list

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.realm.kotlin.compiler.CollectionType
import io.realm.kotlin.test.compiler.createFileAndCompile
import io.realm.kotlin.test.compiler.getCode
import io.realm.kotlin.test.compiler.getCodeForStarProjection
import io.realm.kotlin.test.util.Compiler
import io.realm.kotlin.test.util.Compiler.compileFromSource
import io.realm.kotlin.test.util.TypeDescriptor
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmObject
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val baseSupportedPrimitiveClasses = TypeDescriptor.elementTypesForList
    .filter { it.classifier != RealmObject::class } // Cannot have "pure" Collection<RealmObject>

private val nonNullableTypes = baseSupportedPrimitiveClasses
    .filter { it.classifier != RealmAny::class } // No non-nullable RealmList<RealmAny> allowed
    .map { (it.classifier as KClass<*>).simpleName!! }
    .toSet() // Remove duplicates from nullable types

private val supportedPrimitiveTypes = baseSupportedPrimitiveClasses
    .map { (it.classifier as KClass<*>).simpleName!! }
    .toSet() // Remove duplicates from nullable types

// Add object class manually - see name in class code strings in Utils.kt
private val listNonNullableTypes = nonNullableTypes.plus("SampleClass")

// Add object class manually - see name in class code strings in Utils.kt
private val setNonNullableTypes = nonNullableTypes.plus("SampleClass")

// Add object classes manually, remember dictionaries support embedded objects too - see names in class code strings in Utils.kt
private val dictionaryNonNullableTypes =
    nonNullableTypes.plus(listOf("SampleClass", "EmbeddedClass"))

class CollectionTests {

    // ------------------------------------------------
    // Collection<E>
    // ------------------------------------------------

    // - supported types
    // TODO DONE
    @Test
    fun `non-nullable collection`() {
        // TODO optimize: see comment in TypeDescriptor.elementTypesForSet
        for (collectionType in CollectionType.values()) {
            val typesToTest = when (collectionType) {
                CollectionType.NONE -> continue
                CollectionType.LIST -> listNonNullableTypes
                CollectionType.SET -> setNonNullableTypes
                CollectionType.DICTIONARY -> dictionaryNonNullableTypes
            }
            typesToTest.forEach { nonNullableType ->
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
    }

    // - RealmAny fails if non-nullable (mixed is always non-null)
    // - Other unsupported types fail too (nullability is irrelevant in this case)
    // TODO DONE
    @Test
    fun `unsupported non-nullable collection - fails`() {
        val unsupportedNonNullableTypes = listOf(
            Exception::class.simpleName!!,
            RealmAny::class.simpleName!!
        )
        for (collectionType in CollectionType.values()) {
            if (collectionType != CollectionType.NONE) {
                unsupportedNonNullableTypes.forEach { nonNullableType ->
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
        }
    }

    // - Other unsupported types fail too
    // TODO DONE
    @Test
    fun `unsupported type in collection - fails`() {
        for (collectionType in CollectionType.values()) {
            if (collectionType != CollectionType.NONE) {
                val result = compileFromSource(
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
        }
    }

    // ------------------------------------------------
    // Collection<E?>
    // ------------------------------------------------

    // - supported types
    // TODO DONE
    @Test
    fun `nullable primitive type collection`() {
        for (collectionType in CollectionType.values()) {
            if (collectionType != CollectionType.NONE) {
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
        }
    }

    // ------------------------------------------------
    // Collection<E>?
    // ------------------------------------------------

    // - nullable sets fail
    @Test
    fun `nullable collection field - fails`() {
        for (collectionType in CollectionType.values()) {
            if (collectionType != CollectionType.NONE) {
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
        }
    }

    // - star projection fails
    @Test
    fun `star projection collection - fails`() {
        for (collectionType in CollectionType.values()) {
            if (collectionType != CollectionType.NONE) {
                // Test that a star-projected set fails to compile
                // It is not possible to test a set missing generics since this would not even compile
                val result = compileFromSource(
                    SourceFile.kotlin(
                        "starProjectionCollection.kt",
                        getCodeForStarProjection(collectionType)
                    )
                )
                assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
                assertTrue(result.messages.contains("${collectionType.description} cannot use a '*' projection"))
            }
        }
    }
}

// Cannot trigger these from within the IDE due to https://youtrack.jetbrains.com/issue/KT-46195
// Execute the tests from the CLI with `./gradlew jvmTest`
class ListTests {

    private val baseSupportedPrimitiveClasses = TypeDescriptor.elementTypesForList
        .filter { it.classifier != RealmObject::class } // Cannot have "pure" RealmList<RealmObject>

    private val nonNullableTypes = baseSupportedPrimitiveClasses
        .filter { it.classifier != RealmAny::class } // No non-nullable RealmList<RealmAny> allowed
        .map { (it.classifier as KClass<*>).simpleName!! }
        .toSet() // Remove duplicates from nullable types
        .plus("SampleClass") // Add object class manually - see name in code strings in Utils.kt

    private val supportedPrimitiveTypes = baseSupportedPrimitiveClasses
        .map { (it.classifier as KClass<*>).simpleName!! }
        .toSet() // Remove duplicates from nullable types

    // ------------------------------------------------
    // RealmList<E>
    // ------------------------------------------------

    // - supported types
    // TODO DONE
    @Test
    fun `non-nullable list`() {
        // TODO optimize: see comment in TypeDescriptor.elementTypesForList
        nonNullableTypes.forEach { nonNullableType ->
            val result = createFileAndCompile(
                "nonNullableList.kt",
                getCode(
                    collectionType = CollectionType.LIST,
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
    fun `unsupported non-nullable list - fails`() {
        val unsupportedNonNullableTypes =
            listOf(Exception::class.simpleName!!, RealmAny::class.simpleName!!)
        unsupportedNonNullableTypes.forEach { nonNullableType ->
            val result = createFileAndCompile(
                "unsupportedNonNullableList.kt",
                getCode(
                    collectionType = CollectionType.LIST,
                    contentType = nonNullableType,
                    nullableContent = false,
                    nullableField = false
                )
            )
            assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
            assertTrue(result.messages.contains("Unsupported type for RealmList"))
        }
    }

    // - Other unsupported types fails
    // TODO DONE
    @Test
    fun `unsupported type in list - fails`() {
        val result = Compiler.compileFromSource(
            SourceFile.kotlin(
                "unsupportedTypeList.kt",
                getCode(
                    collectionType = CollectionType.LIST,
                    contentType = "A",
                    nullableContent = false,
                    nullableField = false
                )
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("Unsupported type for RealmList: 'A'"))
    }

    // ------------------------------------------------
    // RealmList<E?>
    // ------------------------------------------------

    // - supported types
    // TODO DONE
    @Test
    fun `nullable primitive type list`() {
        supportedPrimitiveTypes.forEach { nullableType ->
            val result = createFileAndCompile(
                "nullableTypeList.kt",
                getCode(
                    collectionType = CollectionType.LIST,
                    contentType = nullableType,
                    nullableContent = true,
                    nullableField = false
                )
            )
            assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        }
    }

    // - RealmObject fails
    // Lists and sets of objects/embedded objects may NOT contain null values
    @Test
    fun `nullable RealmObject list - fails`() {
        val result = createFileAndCompile(
            "nullableRealmObjectList.kt",
            getCode(
                collectionType = CollectionType.LIST,
                contentType = "SampleClass",
                nullableContent = true,
                nullableField = false
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmList does not support nullable realm objects element types"))
    }

    // - EmbeddedRealmObject fails
    // Lists of objects/embedded objects may NOT contain null values.
    @Test
    fun `nullable EmbeddedRealmObject list - fails`() {
        val result = createFileAndCompile(
            "nullableEmbeddedRealmObjectList.kt",
            getCode(
                collectionType = CollectionType.LIST,
                contentType = "EmbeddedClass",
                nullableContent = true,
                nullableField = false
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmList does not support nullable realm objects element types"))
    }

    // ------------------------------------------------
    // RealmList<E>?
    // ------------------------------------------------

    // - nullable lists fail
    @Test
    fun `nullable lists - fails`() {
        supportedPrimitiveTypes.forEach { primitiveType ->
            val result = createFileAndCompile(
                "nullableList.kt",
                getCode(
                    collectionType = CollectionType.LIST,
                    contentType = primitiveType,
                    nullableContent = false,
                    nullableField = true
                )
            )
            assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
            assertTrue(result.messages.contains("a RealmList field cannot be marked as nullable"))
        }
    }

    // - star projection fails
    @Test
    fun `star projection list - fails`() {
        // Test that a star-projected list fails to compile
        // It is not possible to test a list missing generics since this would not even compile
        val result = compileFromSource(
            SourceFile.kotlin(
                "starProjectionList.kt",
                getCodeForStarProjection(CollectionType.LIST)
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmList cannot use a '*' projection"))
    }
}
