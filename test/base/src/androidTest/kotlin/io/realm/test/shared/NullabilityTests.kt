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
import io.realm.RealmInstant
import io.realm.entities.Nullability
import io.realm.query
import io.realm.test.platform.PlatformUtils
import io.realm.test.util.TypeDescriptor
import kotlin.reflect.KClassifier
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
        val configuration = RealmConfiguration.Builder(
            schema = setOf(Nullability::class)
        ).path("$tmpDir/default.realm").build()
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

        val nullabilityAfter = realm.query<Nullability>().find()[0]
        assertNull(nullabilityAfter.stringNullable)
        assertNotNull(nullabilityAfter.stringNonNullable)
    }

    @Test
    fun safeNullGetterAndSetter() {
        realm.writeBlocking {
            val nullableFieldTypes: MutableSet<KClassifier> = TypeDescriptor.allSingularFieldTypes.map { it.elementType }.filter { it.nullable }
                .map { it.classifier }.toMutableSet()

            copyToRealm(Nullability()).also { nullableObj: Nullability ->
                fun <T> testProperty(property: KMutableProperty1<Nullability, T?>, value: T) {
                    assertNull(property.get(nullableObj))
                    property.set(nullableObj, value)
                    assertEquals(value, property.get(nullableObj))
                    property.set(nullableObj, null)
                    assertNull(property.get(nullableObj))
                    nullableFieldTypes.remove(property.returnType.classifier)
                }
                testProperty(Nullability::stringNullable, "Realm")
                testProperty(Nullability::booleanNullable, true)
                testProperty(Nullability::byteNullable, 0xA)
                testProperty(Nullability::charNullable, 'a')
                testProperty(Nullability::shortNullable, 123)
                testProperty(Nullability::intNullable, 123)
                testProperty(Nullability::longNullability, 123L)
                testProperty(Nullability::floatNullable, 123.456f)
                testProperty(Nullability::doubleField, 123.456)
                testProperty(Nullability::objectField, null)
                testProperty(Nullability::timestampField, RealmInstant.fromEpochSeconds(42, 420))
                // Manually removing RealmObject as nullableFieldTypes is not referencing the
                // explicit subtype (Nullability). Don't know how to make the linkage without
                // so it also works on Native.
                nullableFieldTypes.remove(io.realm.RealmObject::class)
            }
            assertTrue(nullableFieldTypes.isEmpty(), "Untested fields: $nullableFieldTypes")
        }
    }
}
