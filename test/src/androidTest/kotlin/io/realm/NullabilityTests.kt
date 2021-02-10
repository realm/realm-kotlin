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

package io.realm

import io.realm.runtimeapi.RealmModule
import test.Nullability
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NullabilityTests {

    @RealmModule(Nullability::class)
    class MySchema

    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        val configuration = RealmConfiguration.Builder(schema = MySchema()).build()
        realm = Realm.open(configuration)
        // FIXME Cleaning up realm to overcome lack of support for deleting actual files
        //  https://github.com/realm/realm-kotlin/issues/95
        realm.beginTransaction()
        realm.objects(Nullability::class).delete()
        realm.commitTransaction()
        assertEquals(0, realm.objects(Nullability::class).size, "Realm is not empty")
    }

    @Test
    fun nullability() {
        realm.beginTransaction()
        val nullability = realm.create(Nullability::class)
        assertNull(nullability.stringNullable)
        // What is the default semantics for this? Can we fix it before default values
        assertNull(nullability.stringNullable)

        nullability.stringNullable = "Realm"
        assertNotNull(nullability.stringNullable)
        nullability.stringNullable = null
        assertNull(nullability.stringNullable)

        // Should we try to verify that compiler will break on this
        // nullability.stringNonNullable = null
        // We could assert that the C-API fails by internals API with
        // io.realm.internal.RealmObjectHelper.realm_set_value(nullability as RealmModelInternal, Nullability::stringNonNullable, null)
        // but that would require
        // implementation("io.realm.kotlin:cinterop:${Realm.version}")

        nullability.stringNonNullable = "Realm"
        realm.commitTransaction()

        val nullabilityAfter = realm.objects(Nullability::class)[0]
        assertNull(nullabilityAfter.stringNullable)
        assertNotNull(nullabilityAfter.stringNonNullable)
    }

}
