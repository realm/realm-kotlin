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
import io.realm.kotlin.compiler.CollectionType
import io.realm.kotlin.test.compiler.CollectionTests
import io.realm.kotlin.test.compiler.createFileAndCompile
import io.realm.kotlin.test.compiler.getCode
import io.realm.kotlin.test.compiler.nonNullableTypes
import io.realm.kotlin.test.util.Compiler.compileFromSource
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Add object class manually - see name in class code strings in Utils.kt
private val setNonNullableTypes = nonNullableTypes.plus("SampleClass")

class SetTests : CollectionTests(
    CollectionType.SET,
    setNonNullableTypes
) {

    // ------------------------------------------------
    // RealmSet<E>
    // ------------------------------------------------

    // - Embedded objects fail
    @Test
    fun `unsupported type in set - EmbeddedRealmObject fails`() {
        val result = compileFromSource(
            SourceFile.kotlin(
                "unsupportedEmbeddedRealmObjectSet.kt",
                getCode(
                    collectionType = CollectionType.SET,
                    elementType = "EmbeddedClass",
                    nullableElementType = false,
                    nullableField = false
                )
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmSet does not support embedded realm objects element types"))
    }

    // ------------------------------------------------
    // RealmSet<E?>
    // ------------------------------------------------

    // - RealmObject fails
    @Test
    fun `nullable RealmObject set - fails`() {
        val result = createFileAndCompile(
            "nullableRealmObjectSet.kt",
            getCode(
                collectionType = CollectionType.SET,
                elementType = "SampleClass",
                nullableElementType = true,
                nullableField = false
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmSet does not support nullable realm objects element types"))
    }

    // - nullable Embedded objects fail
    @Test
    fun `nullable EmbeddedRealmObject - fails`() {
        val result = compileFromSource(
            SourceFile.kotlin(
                "unsupportedEmbeddedRealmObjectSet.kt",
                getCode(
                    collectionType = CollectionType.SET,
                    elementType = "EmbeddedClass",
                    nullableElementType = true,
                    nullableField = false
                )
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("RealmSet does not support embedded realm objects element types"))
    }
}
