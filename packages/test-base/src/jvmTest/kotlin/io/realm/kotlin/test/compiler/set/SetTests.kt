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

@file:OptIn(ExperimentalCompilerApi::class)

package io.realm.kotlin.test.compiler.set

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.realm.kotlin.test.compiler.createFileAndCompile
import io.realm.kotlin.test.util.Compiler.compileFromSource
import io.realm.kotlin.test.util.TypeDescriptor
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmObject
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
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
        .plus("NonNullableSet") // Add object class manually

    private val supportedPrimitiveTypes = baseSupportedPrimitiveClasses
        .map { (it.classifier as KClass<*>).simpleName!! }
        .toSet() // Remove duplicates from nullable types

    // ------------------------------------------------
    // RealmSet<E>
    // ------------------------------------------------

    // - supported types
    @Test
    fun `non-nullable set`() {
        // TODO optimize: see comment in TypeDescriptor.elementTypesForSet to avoid this filter
        nonNullableTypes.forEach { nonNullableType ->
            val result = createFileAndCompile(
                "nonNullableSet.kt",
                NON_NULLABLE_SET_CODE.format(nonNullableType)
            )
            assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        }
    }

    // - RealmAny fails - mixed is always non-null
    @Test
    fun `unsupported non-nullable set - fails`() {
        val unsupportedNonNullableTypes =
            listOf(Exception::class.simpleName, RealmAny::class.simpleName)
        unsupportedNonNullableTypes.forEach {
            val result = createFileAndCompile(
                "unsupportedNonNullableSet.kt",
                NON_NULLABLE_SET_CODE.format(it)
            )
            assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
            assertTrue(result.messages.contains("Unsupported type for RealmSet"))
        }
    }

    // - other unsupported types fails
    @Test
    fun `unsupported type in set - fails`() {
        val result = compileFromSource(SourceFile.kotlin("nullableSet.kt", UNSUPPORTED_TYPE))
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("Unsupported type for RealmSets: 'A'"))
    }

    // - Embedded objects fail
    @Test
    fun `unsupported type in set - EmbeddedRealmObject fails`() {
        val result = compileFromSource(SourceFile.kotlin("nullableSet.kt", EMBEDDED_TYPE))
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmSets do not support embedded realm objects element types"))
    }

    // ------------------------------------------------
    // RealmSet<E?>
    // ------------------------------------------------

    // - supported types
    @Test
    fun `nullable primitive type set`() {
        supportedPrimitiveTypes.forEach { primitiveType ->
            val result =
                createFileAndCompile("nullableTypeSet.kt", NULLABLE_TYPE_CODE.format(primitiveType))
            assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        }
    }

    // - RealmObject fails
    @Test
    fun `nullable RealmObject set - fails`() {
        val result =
            createFileAndCompile("nullableTypeSet.kt", NULLABLE_TYPE_CODE.format("NullableTypeSet"))
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmSets do not support nullable realm objects element types"))
    }

    // ------------------------------------------------
    // RealmSet<E>?
    // ------------------------------------------------

    // - nullable sets fail
    @Test
    fun `nullable sets - fails`() {
        supportedPrimitiveTypes.forEach { primitiveType ->
            val result =
                createFileAndCompile("nullableSet.kt", NULLABLE_SET_CODE.format(primitiveType))
            assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
            assertTrue(result.messages.contains("a RealmSet field cannot be marked as nullable"))
        }
    }

    // - star projection fails
    @Test
    fun `star projection set - fails`() {
        // Test that a star-projected set fails to compile
        // It is not possible to test a set missing generics since this would not even compile
        val result = compileFromSource(SourceFile.kotlin("nullableSet.kt", STAR_PROJECTION))
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmSets cannot use a '*' projection"))
    }
}

private val NON_NULLABLE_SET_CODE = """
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.BsonDecimal128

import java.lang.Exception

class NonNullableSet : RealmObject {
    var nonNullableSet: RealmSet<%s> = realmSetOf()
}
""".trimIndent()

private val NULLABLE_SET_CODE = """
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.BsonDecimal128

import java.lang.Exception

class NullableSet : RealmObject {
    var nullableSet: RealmSet<%s>? = realmSetOf()
}
""".trimIndent()

private val NULLABLE_TYPE_CODE = """
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.BsonDecimal128

import java.lang.Exception

class NullableTypeSet : RealmObject {
    var nullableSet: RealmSet<%s?> = realmSetOf()
}
""".trimIndent()

private val STAR_PROJECTION = """
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId

import java.lang.Exception

class NullableTypeSet : RealmObject {
    var set: RealmSet<*> = realmSetOf<String>()
}
""".trimIndent()

private val UNSUPPORTED_TYPE = """
    import io.realm.kotlin.ext.realmSetOf
    import io.realm.kotlin.types.ObjectId
    import io.realm.kotlin.types.RealmInstant
    import io.realm.kotlin.types.RealmObject
    import io.realm.kotlin.types.RealmSet
    import io.realm.kotlin.types.RealmUUID
    import org.mongodb.kbson.BsonObjectId

    import java.lang.Exception

    class A

    class NullableTypeSet : RealmObject {
        var set: RealmSet<A> = realmSetOf()
    }
""".trimIndent()

private val EMBEDDED_TYPE = """
    import io.realm.kotlin.ext.realmSetOf
    import io.realm.kotlin.types.EmbeddedRealmObject
    import io.realm.kotlin.types.ObjectId
    import io.realm.kotlin.types.RealmInstant
    import io.realm.kotlin.types.RealmObject
    import io.realm.kotlin.types.RealmSet
    import io.realm.kotlin.types.RealmUUID
    import org.mongodb.kbson.BsonObjectId

    import java.lang.Exception

    class Embedded : EmbeddedRealmObject

    class NullableTypeSet : RealmObject {
        var set: RealmSet<Embedded> = realmSetOf()
    }
""".trimIndent()
