/*
 * Copyright 2022 Realm Inc.
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
import io.realm.kotlin.compiler.Registrar
import io.realm.kotlin.test.util.Compiler
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelDefinitionTests {
    @Test
    fun `no_zero_arg_constructor_should_fail`() {
        val result = Compiler.compileFromSource(
            plugins = listOf(Registrar()),
            source = SourceFile.kotlin(
                "no_zero_arg_ctor.kt",
                """
                        import io.realm.kotlin.types.RealmObject
                        import io.realm.kotlin.RealmConfiguration

                        class NoZeroArgCtor(var name: String) : RealmObject

                        val configuration =
                            RealmConfiguration.create(schema = setOf(NoZeroArgCtor::class))
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, "Compilation should fail without a zero arg constructor")
        assertTrue(result.messages.contains("sources/no_zero_arg_ctor.kt: (4, 1): [Realm] Cannot find primary zero arg constructor"))
    }

    @Test
    fun `constructor_overloads_should_work`() {
        val result = Compiler.compileFromSource(
            plugins = listOf(Registrar()),
            source = SourceFile.kotlin(
                "multiple_ctor.kt",
                """
                        import io.realm.kotlin.types.RealmObject

                        class MultipleConstructors(var firstName: String, var lastName: String, var age: Int) : RealmObject {
                            constructor(firstName: String, lastName: String) : this (firstName, lastName, 42)
                            constructor(foreName: String) : this (foreName, "Doe")
                            constructor() : this ("John")
                        }
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    @Test
    fun `data_class_with_default_constructor`() {
        val result = Compiler.compileFromSource(
            plugins = listOf(Registrar()),
            source = SourceFile.kotlin(
                "data_class.kt",
                """
                        import io.realm.kotlin.types.RealmObject
                        data class Foo(val name: String) : RealmObject
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.INTERNAL_ERROR, result.exitCode, "Compilation should fail when using data class")
        assertTrue(result.messages.contains("Data class 'Foo' is not currently supported"))
    }

    @Test
    fun `enum_class`() {
        val result = Compiler.compileFromSource(
            plugins = listOf(Registrar()),
            source = SourceFile.kotlin(
                "enum_class.kt",
                """
                        import io.realm.kotlin.types.RealmObject
                        enum class Foo : RealmObject { NORTH, SOUTH }
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.INTERNAL_ERROR, result.exitCode, "Compilation should fail when using enum class")
        assertTrue(result.messages.contains("Enum class 'Foo' is not supported."))
    }

    @Test
    fun `object_declaration`() {
        val result = Compiler.compileFromSource(
            plugins = listOf(Registrar()),
            source = SourceFile.kotlin(
                "object_declaration.kt",
                """
                        import io.realm.kotlin.types.RealmObject
                        object Foo : RealmObject
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.INTERNAL_ERROR, result.exitCode, "Compilation should fail when using object declaration")
        assertTrue(result.messages.contains("Object declarations are not supported."))
    }

    @Test
    fun `anonymous_object`() {
        val result = Compiler.compileFromSource(
            plugins = listOf(Registrar()),
            source = SourceFile.kotlin(
                "anonymous_object.kt",
                """
                        import io.realm.kotlin.types.RealmObject
                        val Foo = object: RealmObject {}
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.INTERNAL_ERROR, result.exitCode, "Compilation should fail when using anonymous objects")
        assertTrue(result.messages.contains("Anonymous objects are not supported."))
    }

    @Test
    fun `persisted_properties_with_val_should_fail`() {
        val result = Compiler.compileFromSource(
            plugins = listOf(Registrar()),
            source = SourceFile.kotlin(
                "persisted_properties_val.kt",
                """
                        import io.realm.kotlin.types.RealmObject
                        class Person(val name: String) : RealmObject
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, "Persisted properties does not allow val")
        assertTrue(result.messages.contains("Persisted properties must be marked with `var`"))
    }

    @Test
    fun `persisted_properties_with_lateinit_should_fail`() {
        val result = Compiler.compileFromSource(
            plugins = listOf(Registrar()),
            source = SourceFile.kotlin(
                "persisted_properties_lateinit.kt",
                """
                        import io.realm.kotlin.types.RealmObject
                        class Person : RealmObject {
                            lateinit var name: String
                        }
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, "Persisted properties does not allow lateinit")
        assertTrue(result.messages.contains("Persisted properties must not be marked with `lateinit`."))
    }
}
