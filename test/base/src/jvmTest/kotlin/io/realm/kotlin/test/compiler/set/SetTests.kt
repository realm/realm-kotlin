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
import io.realm.kotlin.test.compiler.createFileAndCompile
import io.realm.kotlin.test.util.Compiler.compileFromSource
import io.realm.kotlin.test.util.TypeDescriptor
import io.realm.kotlin.types.RealmObject
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Cannot trigger these from within the IDE due to https://youtrack.jetbrains.com/issue/KT-46195
// Execute the tests from the CLI with `./gradlew jvmTest`
class SetTests {

    private val supportedPrimitiveTypes = TypeDescriptor.classifiers.keys.filter {
        // Filter out RealmObject
        it != RealmObject::class
    }.map {
        (it as KClass<*>).simpleName!!
    }

    private val allSupportedTypes = supportedPrimitiveTypes.plus("NonNullableSet")

    // ------------------------------------------------
    // RealmSet<E>
    // - supported types
    // - unsupported type fails
    // ------------------------------------------------

    @Test
    fun `non-nullable set`() {
        allSupportedTypes.forEach { primitiveType ->
            val result = createFileAndCompile(
                "nonNullableSet.kt",
                NON_NULLABLE_SET_CODE.format(primitiveType)
            )
            assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        }
    }

    @Test
    fun `unsupported non-nullable set - fails`() {
        val result = createFileAndCompile(
            "unsupportedNonNullableSet.kt",
            NON_NULLABLE_SET_CODE.format("Exception")
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("Unsupported type for RealmSet"))
    }

    // ------------------------------------------------
    // RealmSet<E?>
    // - supported types
    // - RealmObject fails
    // ------------------------------------------------

    @Test
    fun `nullable primitive type set`() {
        supportedPrimitiveTypes.forEach { primitiveType ->
            val result =
                createFileAndCompile("nullableTypeSet.kt", NULLABLE_TYPE_CODE.format(primitiveType))
            assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        }
    }

    @Test
    fun `nullable RealmObject set - fails`() {
        val result =
            createFileAndCompile("nullableTypeSet.kt", NULLABLE_TYPE_CODE.format("NullableTypeSet"))
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmSets do not support nullable realm objects element types"))
    }

    // ------------------------------------------------
    // RealmSet<E>?
    // - nullable sets fail
    // - star projection fails
    // ------------------------------------------------

    @Test
    fun `nullable sets - fails`() {
        supportedPrimitiveTypes.forEach { primitiveType ->
            val result =
                createFileAndCompile("nullableSet.kt", NULLABLE_SET_CODE.format(primitiveType))
            assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
            assertTrue(result.messages.contains("a RealmSet field cannot be marked as nullable"))
        }
    }

    @Test
    fun `star projection set - fails`() {
        // Test that a star-projected set fails to compile
        // It is not possible to test a set missing generics since this would not even compile
        val result = compileFromSource(SourceFile.kotlin("nullableSet.kt", STAR_PROJECTION))
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmSets cannot use a '*' projection"))
    }

    @Test
    fun `unsupported type in set - fails`() {
        val result = compileFromSource(SourceFile.kotlin("nullableSet.kt", UNSUPPORTED_TYPE))
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("Unsupported type for RealmSets: 'A'"))
    }

    @Test
    fun `unsupported type in set - EmbeddedRealmObject fails`() {
        val result = compileFromSource(SourceFile.kotlin("nullableSet.kt", EMBEDDED_TYPE))
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmSets do not support embedded realm objects element types"))
    }
}

private val NON_NULLABLE_SET_CODE = """
import io.realm.kotlin.types.realmSetOf
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmObject

import java.lang.Exception

class NonNullableSet : RealmObject {
    var nonNullableSet: RealmSet<%s> = realmSetOf()
}
""".trimIndent()

private val NULLABLE_SET_CODE = """
import io.realm.kotlin.types.realmSetOf
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmObject

import java.lang.Exception

class NullableSet : RealmObject {
    var nullableSet: RealmSet<%s>? = realmSetOf()
}
""".trimIndent()

private val NULLABLE_TYPE_CODE = """
import io.realm.kotlin.types.realmSetOf
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmObject

import java.lang.Exception

class NullableTypeSet : RealmObject {
    var nullableSet: RealmSet<%s?> = realmSetOf()
}
""".trimIndent()

private val STAR_PROJECTION = """
import io.realm.kotlin.types.realmSetOf
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmObject

import java.lang.Exception

class NullableTypeSet : RealmObject {
    var set: RealmSet<*> = realmSetOf<String>()
}
""".trimIndent()

private val UNSUPPORTED_TYPE = """
    import io.realm.kotlin.types.realmSetOf
    import io.realm.kotlin.types.RealmInstant
    import io.realm.kotlin.types.ObjectId
    import io.realm.kotlin.types.RealmSet
    import io.realm.kotlin.types.RealmObject

    import java.lang.Exception

    class A

    class NullableTypeSet : RealmObject {
        var set: RealmSet<A> = realmSetOf()
    }
""".trimIndent()

private val EMBEDDED_TYPE = """
    import io.realm.kotlin.types.realmSetOf
    import io.realm.kotlin.types.EmbeddedRealmObject
    import io.realm.kotlin.types.ObjectId
    import io.realm.kotlin.types.RealmInstant
    import io.realm.kotlin.types.RealmSet
    import io.realm.kotlin.types.RealmObject

    import java.lang.Exception

    class Embedded : EmbeddedRealmObject

    class NullableTypeSet : RealmObject {
        var set: RealmSet<Embedded> = realmSetOf()
    }
""".trimIndent()
