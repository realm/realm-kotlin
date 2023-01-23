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
import io.realm.kotlin.test.compiler.createFileAndCompile
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
class ListTests {

    private val baseSupportedPrimitiveClasses = TypeDescriptor.elementTypesForList
        .filter { it.classifier != RealmObject::class } // Cannot have "pure" RealmSet<RealmObject>

    private val nonNullableTypes = baseSupportedPrimitiveClasses
        .filter { it.classifier != RealmAny::class } // No non-nullable RealmList<RealmAny> allowed
        .map { (it.classifier as KClass<*>).simpleName!! }
        .toSet() // Remove duplicates from nullable types
        .plus("NonNullableList") // Add object class manually

    private val supportedPrimitiveTypes = baseSupportedPrimitiveClasses
        .map { (it.classifier as KClass<*>).simpleName!! }
        .toSet() // Remove duplicates from nullable types

    // ------------------------------------------------
    // RealmList<E>
    // ------------------------------------------------

    // - supported types
    @Test
    fun `non-nullable list`() {
        // TODO optimize: see comment in TypeDescriptor.elementTypesForList to avoid this filter
        nonNullableTypes.forEach { nonNullableType ->
            val result = createFileAndCompile(
                "nonNullableList.kt",
                NON_NULLABLE_LIST_CODE.format(nonNullableType)
            )
            assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        }
    }

    // - RealmAny fails - mixed is always non-null and other recognized types fail too
    @Test
    fun `unsupported non-nullable list - fails`() {
        val unsupportedNonNullableTypes =
            listOf(Exception::class.simpleName, RealmAny::class.simpleName)
        unsupportedNonNullableTypes.forEach {
            val result = createFileAndCompile(
                "unsupportedNonNullableList.kt",
                NON_NULLABLE_LIST_CODE.format(it)
            )
            assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
            assertTrue(result.messages.contains("Unsupported type for RealmLists"))
        }
    }

    // - other unsupported types fails
    @Test
    fun `unsupported type in list - fails`() {
        val result = compileFromSource(SourceFile.kotlin("nullableList.kt", UNSUPPORTED_TYPE))
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("Unsupported type for RealmLists: 'A'"))
    }

    // ------------------------------------------------
    // RealmList<E?>
    // ------------------------------------------------

    // - supported types
    @Test
    fun `nullable primitive type list`() {
        supportedPrimitiveTypes.forEach { primitiveType ->
            val result = createFileAndCompile(
                "nullableTypeList.kt",
                NULLABLE_TYPE_CODE.format(primitiveType)
            )
            assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        }
    }

    // - RealmObject fails
    @Test
    fun `nullable RealmObject list - fails`() {
        val result = createFileAndCompile(
            "nullableTypeList.kt",
            NULLABLE_TYPE_CODE.format("NullableTypeList")
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmLists do not support nullable realm objects element types"))
    }

    // ------------------------------------------------
    // RealmList<E>?
    // ------------------------------------------------

    // - nullable lists fail
    @Test
    fun `nullable lists - fails`() {
        supportedPrimitiveTypes.forEach { primitiveType ->
            val result =
                createFileAndCompile("nullableList.kt", NULLABLE_LIST_CODE.format(primitiveType))
            assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
            assertTrue(result.messages.contains("a RealmList field cannot be marked as nullable"))
        }
    }

    // - star projection fails
    @Test
    fun `star projection list - fails`() {
        // Test that a star-projected list fails to compile
        // It is not possible to test a list missing generics since this would not even compile
        val result = compileFromSource(SourceFile.kotlin("nullableList.kt", STAR_PROJECTION))
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmLists cannot use a '*' projection"))
    }
}

private val NON_NULLABLE_LIST_CODE = """
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.BsonDecimal128

import java.lang.Exception

class NonNullableList : RealmObject {
    var nonNullableList: RealmList<%s> = realmListOf()
}
""".trimIndent()

private val NULLABLE_LIST_CODE = """
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.BsonDecimal128

import java.lang.Exception

class NullableList : RealmObject {
    var nullableList: RealmList<%s>? = realmListOf()
}
""".trimIndent()

private val NULLABLE_TYPE_CODE = """
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.BsonDecimal128

import java.lang.Exception

class NullableTypeList : RealmObject {
    var nullableList: RealmList<%s?> = realmListOf()
}
""".trimIndent()

private val STAR_PROJECTION = """
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId

import java.lang.Exception

class NullableTypeList : RealmObject {
    var list: RealmList<*> = realmListOf<String>()
}
""".trimIndent()

private val UNSUPPORTED_TYPE = """
    import io.realm.kotlin.ext.realmListOf
    import io.realm.kotlin.types.ObjectId
    import io.realm.kotlin.types.RealmInstant
    import io.realm.kotlin.types.RealmList
    import io.realm.kotlin.types.RealmObject
    import io.realm.kotlin.types.RealmUUID
    import org.mongodb.kbson.BsonObjectId

    import java.lang.Exception

    class A

    class NullableTypeList : RealmObject {
        var list: RealmList<A> = realmListOf()
    }
""".trimIndent()
