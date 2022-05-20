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
import io.realm.test.util.Compiler
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmbeddedTests {

    @Test
    fun `embedded object with primary keys fails`() {
        val result = Compiler.compileFromSource(
            source = SourceFile.kotlin(
                "embeddedObjectWithPrimaryKey.kt",
                """
                    import io.realm.EmbeddedObject
                    import io.realm.annotations.PrimaryKey

                    class A : EmbeddedObject {
                        @PrimaryKey
                        var primaryKey1: String? = null
                    }
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("Embedded object is not allowed to have a primary key"))
    }

    @Test
    fun `embedded object lists cannot be nullable`() {
        val result = Compiler.compileFromSource(
            source = SourceFile.kotlin(
                "embeddedObjectNullableList.kt",
                """
                    import io.realm.EmbeddedObject
                    import io.realm.RealmList
                    import io.realm.realmListOf

                    class A : EmbeddedObject {
                        var embeddedList: RealmList<A?> = realmListOf()
                    }
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmLists does not support nullable realm objects element type"))
    }
}
