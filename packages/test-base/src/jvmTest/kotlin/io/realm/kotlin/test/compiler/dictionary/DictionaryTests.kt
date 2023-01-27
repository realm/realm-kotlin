/*
 * Copyright 2023 Realm Inc.
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

package io.realm.kotlin.test.compiler.dictionary

import com.tschuchort.compiletesting.KotlinCompilation
import io.realm.kotlin.compiler.CollectionType
import io.realm.kotlin.test.compiler.CollectionTests
import io.realm.kotlin.test.compiler.createFileAndCompile
import io.realm.kotlin.test.compiler.getCode
import io.realm.kotlin.test.compiler.nonNullableTypes
import org.junit.Test
import kotlin.test.assertEquals

// Add object classes manually, remember dictionaries support embedded objects too - see names in class code strings in Utils.kt
private val dictionaryNonNullableTypes =
    nonNullableTypes.plus(listOf("SampleClass", "EmbeddedClass"))

class DictionaryTests : CollectionTests(
    CollectionType.DICTIONARY,
    dictionaryNonNullableTypes
) {

    // ------------------------------------------------
    // RealmDictionary<E?> - specific dictionary cases
    // ------------------------------------------------

    // - RealmObject works
    // Unlike lists and sets, dictionaries of objects/embedded objects may contain null values.
    @Test
    fun `nullable RealmObject dictionary`() {
        val result = createFileAndCompile(
            "nullableRealmObjectDictionary.kt",
            getCode(
                collectionType = CollectionType.DICTIONARY,
                elementType = "SampleClass",
                nullableElementType = true,
                nullableField = false
            )
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    // - EmbeddedRealmObject works
    // Unlike lists and sets, dictionaries of objects/embedded objects may contain null values.
    @Test
    fun `nullable EmbeddedRealmObject dictionary`() {
        val result = createFileAndCompile(
            "nullableEmbeddedRealmObjectDictionary.kt",
            getCode(
                collectionType = CollectionType.DICTIONARY,
                elementType = "EmbeddedClass",
                nullableElementType = true,
                nullableField = false
            )
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }
}
