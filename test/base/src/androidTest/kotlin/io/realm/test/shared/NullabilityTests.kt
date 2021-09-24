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
package io.realm.test.shared

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.entities.Nullability
import io.realm.test.platform.PlatformUtils
import io.realm.test.util.Utils.createRandomString
import kotlin.reflect.KMutableProperty1
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NullabilityTests {

    lateinit var tmpDir: String
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.with(
            path = "$tmpDir/${createRandomString(16)}.realm",
            schema = setOf(Nullability::class)
        )
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun nullability() {
        realm.writeBlocking {
            val nullability = copyToRealm(Nullability())
            assertNull(nullability.stringNullable)
            assertNotNull(nullability.stringNonNullable)

            nullability.stringNullable = "Realm"
            assertNotNull(nullability.stringNullable)
            nullability.stringNullable = null
            assertNull(nullability.stringNullable)

            // Should we try to verify that compiler will break on this
            // nullability.stringNonNullable = null
            // We could assert that the C-API fails by internals API with
            // io.realm.internal.RealmObjectHelper.realm_set_value(nullability as RealmObjectInternal, Nullability::stringNonNullable, null)
            // but that would require
            // implementation("io.realm.kotlin:cinterop:${Realm.version}")
            //  https://github.com/realm/realm-kotlin/issues/134

            nullability.stringNonNullable = "Realm"
        }

        val nullabilityAfter = realm.objects(Nullability::class)[0]
        assertNull(nullabilityAfter.stringNullable)
        assertNotNull(nullabilityAfter.stringNonNullable)
    }

    @Test
    fun safeNullGetterAndSetter() {
        val nullableFields =
            (Nullability as io.realm.internal.RealmObjectCompanion).`$realm$fields`!!.filter { it.returnType.isMarkedNullable }
                .toMutableSet()

        realm.writeBlocking {
            copyToRealm(Nullability()).also { nullability ->
                fun <T> testProperty(property: KMutableProperty1<Nullability, T?>, value: T) {
                    assertNull(property.get(nullability))
                    property.set(nullability, value)
                    assertEquals(value, property.get(nullability))
                    property.set(nullability, null)
                    assertNull(property.get(nullability))
                    nullableFields.remove(property)
                }
                testProperty(Nullability::stringNullable, "Realm")
                testProperty(Nullability::booleanNullable, true)
                testProperty(Nullability::byteNullable, 0xA)
                testProperty(Nullability::charNullable, 'a')
                testProperty(Nullability::shortNullable , 123)
                testProperty(Nullability::intNullable, 123)
                testProperty(Nullability::longNullability, 123L)
                testProperty(Nullability::floatNullable, 123.456f)
                testProperty(Nullability::doubleField, 123.456)
            }
            assertTrue(nullableFields.isEmpty(), "Untested fields: $nullableFields")
        }
    }
}
