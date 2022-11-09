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
package io.realm.kotlin.test.shared

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.Nullability
import io.realm.kotlin.ext.query
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TypeDescriptor
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId
import kotlin.reflect.KMutableProperty1
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
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
        ).directory(tmpDir).build()
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
            // io.realm.kotlin.internal.RealmObjectHelper.realm_set_value(nullability as RealmObjectInternal, Nullability::stringNonNullable, null)
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
            val nullableFieldTypes = TypeDescriptor.allSingularFieldTypes
                .map { it.elementType }
                .filter { it.nullable }
                .map { it.classifier }
                .toMutableSet()

            copyToRealm(Nullability()).also { nullableObj: Nullability ->
                fun <T> testProperty(property: KMutableProperty1<Nullability, T?>, value: T) {
                    assertNull(property.get(nullableObj))
                    property.set(nullableObj, value)
                    if (value is ByteArray) {
                        assertContentEquals(value, property.get(nullableObj) as ByteArray)
                    } else {
                        assertEquals(value, property.get(nullableObj))
                    }
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
                testProperty(Nullability::timestampField, RealmInstant.from(42, 420))
                testProperty(Nullability::objectIdField, ObjectId.from("507f191e810c19729de860ea"))
                testProperty(Nullability::bsonObjectIdField, BsonObjectId("507f191e810c19729de860ea"))
                testProperty(Nullability::uuidField, RealmUUID.random())
                testProperty(Nullability::binaryField, byteArrayOf(42))
                testProperty(Nullability::mutableRealmIntField, MutableRealmInt.create(42))
                testProperty(Nullability::realmAnyField, RealmAny.create(42))
                // Manually removing RealmObject as nullableFieldTypes is not referencing the
                // explicit subtype (Nullability). Don't know how to make the linkage without
                // so it also works on Native.
                nullableFieldTypes.remove(io.realm.kotlin.types.RealmObject::class)
            }
            assertTrue(nullableFieldTypes.isEmpty(), "Untested fields: $nullableFieldTypes")
        }
    }
}
