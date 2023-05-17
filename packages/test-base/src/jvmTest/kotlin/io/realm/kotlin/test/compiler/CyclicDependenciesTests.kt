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
import io.realm.kotlin.test.util.Compiler.compileFromSource
import org.junit.Test
import kotlin.test.assertEquals

class CyclicDependenciesTests {

    @Test
    fun `cyclic`() {
        val result = compileFromSource(
            source = SourceFile.kotlin(
                "cyclic.kt",
                """
                    import io.realm.kotlin.types.RealmObject
                    import io.realm.kotlin.RealmConfiguration
                    import io.realm.kotlin.types.annotations.PrimaryKey

                    class A : RealmObject, Comparable<A.X> {
                        @PrimaryKey
                        var primaryKey1: String? = null
                        class X
                        override fun compareTo(other: X): Int {
                            return 0
                        }
                    }

                    val configuration =
                        RealmConfiguration.create(schema = setOf(A::class))
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    @Test
    fun `cyclic_without_realm_model`() {
        val result = compileFromSource(
            source = SourceFile.kotlin(
                "cyclic_without_realm_model.kt",
                """
                    interface Generic<T>
                    interface Outer : Generic<Outer.Inner> {
                        class Inner
                    }
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    @Test
    fun `cyclic_fq_name_import`() {
        val result = compileFromSource(
            source = SourceFile.kotlin(
                "cyclic_fq_name_import.kt",
                """
                    interface Generic<T>
                    class Foo : Generic<Foo.Inner>, io.realm.kotlin.types.RealmObject {
                            class Inner
                    }
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    @Test
    fun `cyclic_embedded`() {
        val result = compileFromSource(
            source = SourceFile.kotlin(
                "cyclic_embedded.kt",
                """
                    import io.realm.kotlin.types.EmbeddedRealmObject
                    import io.realm.kotlin.RealmConfiguration

                    class A : EmbeddedRealmObject, Comparable<A.X> {
                        class X
                        override fun compareTo(other: X): Int {
                            return 0
                        }
                    }

                    val configuration =
                        RealmConfiguration.create(schema = setOf(A::class))
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    @Test
    fun `cyclic_fq_name_import_embedded`() {
        val result = compileFromSource(
            source = SourceFile.kotlin(
                "cyclic_fq_name_import_embedded.kt",
                """
                    interface Generic<T>
                    class Foo : Generic<Foo.Inner>, io.realm.kotlin.types.EmbeddedRealmObject {
                            class Inner
                    }
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }
}
