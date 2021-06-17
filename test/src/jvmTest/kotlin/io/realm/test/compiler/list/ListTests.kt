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

package io.realm.test.compiler.list

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.realm.RealmObject
import io.realm.util.CompilerTest.compileFromSource
import io.realm.util.TypeDescriptor
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun `unsupported non-nullable list fail`() {
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
    fun `nullable RealmObject list fail`() {
        val result = NULLABLE_TYPE_CODE.format("NullableTypeList")
            .let {
                SourceFile.kotlin("nullableTypeList.kt", it)
            }.let {
                compileFromSource(it)
            }
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmLists can only contain non-nullable RealmObjects"))
    }

    // ------------------------------------------------
    // RealmList<E>?
    // - nullable lists fail
    // ------------------------------------------------

    @Test
    fun `nullable lists fail`() {
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
}

private val NON_NULLABLE_LIST_CODE = """
import io.realm.RealmList
import io.realm.RealmObject

import java.lang.Exception

class NonNullableList : RealmObject {
    var nonNullableList: RealmList<%s> = RealmList()
}
""".trimIndent()

private val NULLABLE_LIST_CODE = """
import io.realm.RealmList
import io.realm.RealmObject

import java.lang.Exception

class NullableList : RealmObject {
    var nullableList: RealmList<%s>? = RealmList()
}
""".trimIndent()

private val NULLABLE_TYPE_CODE = """
import io.realm.RealmList
import io.realm.RealmObject

import java.lang.Exception

class NullableTypeList : RealmObject {
    var nullableList: RealmList<%s?> = RealmList()
}
""".trimIndent()
