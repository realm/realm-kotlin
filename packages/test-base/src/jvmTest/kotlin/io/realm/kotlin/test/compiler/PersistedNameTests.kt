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
import kotlin.test.assertContains
import kotlin.test.assertEquals

class PersistedNameTests {
    @Test
    fun `empty annotation fails`() {
        val result = Compiler.compileFromSource(
            plugins = listOf(Registrar()),
            source = SourceFile.kotlin(
                "persistedNameAnnotationEmpty.kt",
                """
                    import io.realm.kotlin.types.RealmObject
                    import io.realm.kotlin.types.annotations.PersistedName
                    
                    class InvalidSample : RealmObject {
                        @PersistedName("")
                        var publicName1: String? = ""
                    }
                """.trimIndent()
            )
        )

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        val compilerLog = result.messages
        assertContains(compilerLog, "Names must contain at least 1 character")
    }

    @Test
    fun `same persisted and public name warns`() {
        val result = Compiler.compileFromSource(
            plugins = listOf(Registrar()),
            source = SourceFile.kotlin(
                "samePersistedAndPublicName.kt",
                """
                    import io.realm.kotlin.types.RealmObject
                    import io.realm.kotlin.types.annotations.PersistedName
                    
                    class InvalidSample : RealmObject {
                        @PersistedName("sameName")
                        var sameName: String? = ""
                    }
                """.trimIndent()
            )
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    @Test
    fun `duplicate name fails`() {
        val result = Compiler.compileFromSource(
            plugins = listOf(Registrar()),
            source = SourceFile.kotlin(
                "persistedNameAnnotationDuplicate.kt",
                """
                    import io.realm.kotlin.types.RealmObject
                    import io.realm.kotlin.types.annotations.PersistedName
                    
                    class InvalidSample : RealmObject {
                        // Duplicate names (annotation and a lexically later field)
                        @PersistedName("duplicateName1")
                        var publicName1: String? = ""
                        var duplicateName1: String? = ""
                    
                        // Duplicate names (annotation and a lexically previous field)
                        var duplicateName2: String? = ""
                        @PersistedName("duplicateName2")
                        var publicName2: String? = ""
                    
                        // Duplicate names (annotation and another annotation)
                        @PersistedName("duplicateName3")
                        var publicName3: String? = ""
                        @PersistedName("duplicateName3")
                        var publicName4: String? = ""
                    }
                """.trimIndent()
            )
        )

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        val compilerLog = result.messages
        assertContains(compilerLog, "Kotlin names and persisted names must be unique. 'duplicateName1' has already been used")
        assertContains(compilerLog, "Kotlin names and persisted names must be unique. 'duplicateName2' has already been used")
        assertContains(compilerLog, "Kotlin names and persisted names must be unique. 'duplicateName3' has already been used")
    }
}
