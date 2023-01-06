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

package io.realm.kotlin.test.compiler.list

import com.tschuchort.compiletesting.KotlinCompilation
import io.realm.kotlin.compiler.CollectionType
import io.realm.kotlin.test.compiler.CollectionTests
import io.realm.kotlin.test.compiler.createFileAndCompile
import io.realm.kotlin.test.compiler.getCode
import io.realm.kotlin.test.compiler.nonNullableTypes
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Add object class manually - see name in class code strings in Utils.kt
private val listNonNullableTypes = nonNullableTypes.plus("SampleClass")

class ListTests : CollectionTests(
    CollectionType.LIST,
    listNonNullableTypes
) {

    // ------------------------------------------------
    // List<E?> - specific list cases
    // ------------------------------------------------

    // - RealmObject and EmbeddedRealmObject fail
    // Lists and sets of objects/embedded objects may NOT contain null values
    @Test
    fun `nullable RealmObject list - fails`() {
        listOf("SampleClass", "EmbeddedClass")
            .forEach { realmObjectType ->
                val result = createFileAndCompile(
                    "nullableRealmObjectList.kt",
                    getCode(
                        collectionType = CollectionType.LIST,
                        elementType = realmObjectType,
                        nullableElementType = true,
                        nullableField = false
                    )
                )
                assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
                assertTrue(result.messages.contains("RealmList does not support nullable realm objects element types"))
            }
    }
}
