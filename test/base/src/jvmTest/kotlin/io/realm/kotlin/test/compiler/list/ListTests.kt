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
import io.realm.kotlin.test.util.Compiler.compileFromSource
import io.realm.kotlin.test.util.TypeDescriptor
import io.realm.kotlin.types.RealmObject
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Cannot trigger these from within the IDE due to https://youtrack.jetbrains.com/issue/KT-46195
// Execute the tests from the CLI with `./gradlew jvmTest`
class ListTests {

    private val supportedPrimitiveTypes = TypeDescriptor.classifiers.keys.filter {
        // Filter out RealmObject
        it != RealmObject::class
    }.map {
        (it as KClass<*>).simpleName!!
    }

    private val allSupportedTypes = supportedPrimitiveTypes.plus("NonNullableList")

    // ------------------------------------------------
    // RealmList<E>
    // - supported types
    // - unsupported type fails
    // ------------------------------------------------

    @Test
    fun `non-nullable list`() {
        allSupportedTypes.forEach { primitiveType ->
            val result = NON_NULLABLE_LIST_CODE.format(primitiveType)
                .let {
                    SourceFile.kotlin("nonNullableList.kt", it)
                }.let {
                    compileFromSource(it)
                }
            assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        }
    }

    @Test
    fun `unsupported non-nullable list - fails`() {
        val result = NON_NULLABLE_LIST_CODE.format("Exception")
            .let {
                SourceFile.kotlin("unsupportedNonNullableList.kt", it)
            }.let {
                compileFromSource(it)
            }
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("Unsupported type for lists"))
    }

    // ------------------------------------------------
    // RealmList<E?>
    // - supported types
    // - RealmObject fails
    // ------------------------------------------------

    @Test
    fun `nullable primitive type list`() {
        supportedPrimitiveTypes.forEach { primitiveType ->
            val result = NULLABLE_TYPE_CODE.format(primitiveType)
                .let {
                    SourceFile.kotlin("nullableTypeList.kt", it)
                }.let {
                    compileFromSource(it)
                }
            assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        }
    }

    @Test
    fun `nullable RealmObject list - fails`() {
        val result = NULLABLE_TYPE_CODE.format("NullableTypeList")
            .let {
                SourceFile.kotlin("nullableTypeList.kt", it)
            }.let {
                compileFromSource(it)
            }
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmLists does not support nullable realm objects element type"))
    }

    // ------------------------------------------------
    // RealmList<E>?
    // - nullable lists fail
    // - star projection fails
    // ------------------------------------------------

    @Test
    fun `nullable lists - fails`() {
        supportedPrimitiveTypes.forEach { primitiveType ->
            val result = NULLABLE_LIST_CODE.format(primitiveType)
                .let {
                    SourceFile.kotlin("nullableList.kt", it)
                }.let {
                    compileFromSource(it)
                }
            assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
            assertTrue(result.messages.contains("a RealmList field cannot be marked as nullable"))
        }
    }

    @Test
    fun `star projection list - fails`() {
        // Test that a star-projected list fails to compile
        // It is not possible to test a list missing generics since this would not even compile
        val result = compileFromSource(SourceFile.kotlin("nullableList.kt", STAR_PROJECTION))
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmLists cannot use a '*' projection"))
    }

    @Test
    fun `unsupported type in list - fails`() {
        val result = compileFromSource(SourceFile.kotlin("nullableList.kt", UNSUPPORTED_TYPE))
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("Unsupported type for lists: 'A'"))
    }
}

private val NON_NULLABLE_LIST_CODE = """
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID

import java.lang.Exception

class NonNullableList : RealmObject {
    var nonNullableList: RealmList<%s> = realmListOf()
}
""".trimIndent()

private val NULLABLE_LIST_CODE = """
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID

import java.lang.Exception

class NullableList : RealmObject {
    var nullableList: RealmList<%s>? = realmListOf()
}
""".trimIndent()

private val NULLABLE_TYPE_CODE = """
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID

import java.lang.Exception

class NullableTypeList : RealmObject {
    var nullableList: RealmList<%s?> = realmListOf()
}
""".trimIndent()

private val STAR_PROJECTION = """
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID

import java.lang.Exception

class NullableTypeList : RealmObject {
    var list: RealmList<*> = realmListOf<String>()
}
""".trimIndent()

private val UNSUPPORTED_TYPE = """
    import io.realm.kotlin.ext.realmListOf
    import io.realm.kotlin.types.RealmInstant
    import io.realm.kotlin.types.ObjectId
    import io.realm.kotlin.types.RealmList
    import io.realm.kotlin.types.RealmObject
    import io.realm.kotlin.types.RealmUUID

    import java.lang.Exception

    class A

    class NullableTypeList : RealmObject {
        var list: RealmList<A> = realmListOf()
    }
""".trimIndent()
