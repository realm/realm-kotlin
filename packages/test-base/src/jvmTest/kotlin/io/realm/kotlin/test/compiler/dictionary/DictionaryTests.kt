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
import io.realm.kotlin.test.compiler.EMBEDDED_CLASS
import io.realm.kotlin.test.compiler.OBJECT_CLASS
import io.realm.kotlin.test.compiler.createFileAndCompile
import io.realm.kotlin.test.compiler.getCode
import io.realm.kotlin.test.compiler.globalNonNullableTypes
import org.junit.Test
import kotlin.test.assertEquals

class DictionaryTests : CollectionTests(
    CollectionType.DICTIONARY,
    globalNonNullableTypes // Objects can only be nullable so test separately
) {

    // ------------------------------------------------
    // RealmDictionary<RealmObject>
    // ------------------------------------------------

    // - Non-nullable RealmObject fails
    @Test
    fun `non-nullable RealmObject dictionary`() {
        val result = createFileAndCompile(
            "nonNullableRealmObjectDictionary.kt",
            getCode(
                collectionType = CollectionType.DICTIONARY,
                elementType = OBJECT_CLASS,
                nullableElementType = false,
                nullableField = false
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
    }

    // - Non-nullable EmbeddedRealmObject fails
    @Test
    fun `non-nullable EmbeddedRealmObject dictionary`() {
        val result = createFileAndCompile(
            "nonNullableRealmObjectDictionary.kt",
            getCode(
                collectionType = CollectionType.DICTIONARY,
                elementType = EMBEDDED_CLASS,
                nullableElementType = false,
                nullableField = false
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
    }

    // ------------------------------------------------
    // RealmDictionary<E?>
    // ------------------------------------------------

    // - RealmObject works
    // Unlike lists and sets, dictionaries of objects/embedded objects may contain null values.
    @Test
    fun `nullable RealmObject dictionary`() {
        val result = createFileAndCompile(
            "nullableRealmObjectDictionary.kt",
            getCode(
                collectionType = CollectionType.DICTIONARY,
                elementType = OBJECT_CLASS,
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
                elementType = EMBEDDED_CLASS,
                nullableElementType = true,
                nullableField = false
            )
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }
}
