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

package io.realm.kotlin.test.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.realm.kotlin.internal.interop.CollectionType
import io.realm.kotlin.test.util.Compiler.compileFromSource
import io.realm.kotlin.test.util.TypeDescriptor.allFieldTypes
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmUUID
import org.junit.Test
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128
import kotlin.reflect.KClassifier
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypeAdaptersTests {
    // TODO: Add tests to validate type adapter definitions
    // TODO: Q. Shall we fail with declaring type adapters or when we apply them?


    @Test
    fun nonRealmTypesThrow() {
        val result = compileFromSource(
            plugins = listOf(io.realm.kotlin.compiler.Registrar()),
            source = SourceFile.kotlin(
                "typeAdapter_unsupported_type.kt",
                """
                    import io.realm.kotlin.types.RealmInstant
                    import io.realm.kotlin.types.RealmObject
                    import io.realm.kotlin.types.RealmTypeAdapter
                    import io.realm.kotlin.types.annotations.TypeAdapter
                       
                    class UserType
                    
                    class NonRealmType
                    
                    class TestObject : RealmObject {
                        @TypeAdapter(adapter = UnsupportedRealmTypeParameter::class)
                        var userType: UserType = UserType()
                    }
                    
                    object UnsupportedRealmTypeParameter : RealmTypeAdapter<NonRealmType, UserType> {
                        override fun fromRealm(realmValue: NonRealmType): UserType = TODO()
                    
                        override fun toRealm(value: UserType): NonRealmType = TODO()
                    }
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(result.messages.contains("Invalid type parameter, only Realm types are supported"), result.messages)
    }

    // TODO: What happens if we apply the annotation to: Delegates backlinks
}
