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

package io.realm.test.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.realm.compiler.Registrar
import io.realm.test.util.Compiler
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
                        import io.realm.RealmObject
                        import io.realm.RealmConfiguration

                        class NoZeroArgCtor(var name: String) : RealmObject

                        val configuration =
                            RealmConfiguration.with(schema = setOf(NoZeroArgCtor::class))
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
                        import io.realm.RealmObject

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
                "multiple_ctor.kt",
                """
                        import io.realm.RealmObject
                        data class Foo(var name: String, var age: Int) : RealmObject {
                            constructor() : this ("John" , 42)
                        }
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }
}
