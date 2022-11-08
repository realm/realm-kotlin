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

package io.realm.kotlin.test.shared

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.find
import io.realm.kotlin.test.assertFailsWithMessage
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.asRealmObject
import org.mongodb.kbson.BsonObjectId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Suppress("LargeClass")
class RealmAnyTests {

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(
            setOf(
                IndexedRealmAnyContainer::class,
                RealmAnyContainer::class,
                TestParent::class,
                TestEmbeddedChild::class
            )
        ).directory(tmpDir)
            .build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    // DONE - unmanaged exhaustively
    // DONE - managed exhaustively
    // DONE - accessors: done via managed tests
    // DONE - queries: done via managed tests
    // DONE - nullability
    // DONE - indexing
    // DONE - missing schema class when saving a RealmAny containing a non-schema object
    // TODO lists
    // TODO sets
    // TODO remember to comment out 'check_value_assignable' in object.cpp L330 until mixed columns are allowed in the C-API

    @Test
    fun missingClassFromSchema_unmanagedWorks() {
        val value = NotInSchema()
        val realmAny = RealmAny.create(value)
        assertEquals(value, realmAny.asRealmObject())
    }

    @Test
    fun missingClassFromSchema_managedThrows() {
        val notInSchema = NotInSchema()
        realm.writeBlocking {
            val unmanaged = IndexedRealmAnyContainer()
            val managed = copyToRealm(unmanaged)
            val realmAnyWithClassNotInSchema = RealmAny.create(notInSchema)
            assertFailsWithMessage<IllegalArgumentException>("Schema does not contain a class named 'NotInSchema'") {
                managed.anyField = realmAnyWithClassNotInSchema
            }
        }
    }

    @Test
    fun unmanaged_short() {
        val realmAny = RealmAny.create(10.toShort())
        assertEquals(10, realmAny.asShort())
        assertEquals(RealmAny.create(10.toShort()), realmAny)
        assertEquals(RealmAny.Type.INT, realmAny.type)
    }

    @Test
    fun unmanaged_int() {
        val realmAny = RealmAny.create(10)
        assertEquals(10, realmAny.asInt())
        assertEquals(RealmAny.create(10), realmAny)
        assertEquals(RealmAny.Type.INT, realmAny.type)
    }

    @Test
    fun unmanaged_byte() {
        val realmAny = RealmAny.create(10.toByte())
        assertEquals(10.toByte(), realmAny.asByte())
        assertEquals(RealmAny.create(10.toByte()), realmAny)
        assertEquals(RealmAny.Type.INT, realmAny.type)
    }

    @Test
    fun unmanaged_char() {
        val realmAny = RealmAny.create(10.toChar())
        assertEquals(10.toChar(), realmAny.asChar())
        assertEquals(RealmAny.create(10.toChar()), realmAny)
        assertEquals(RealmAny.Type.INT, realmAny.type)
    }

    @Test
    fun unmanaged_long() {
        val realmAny = RealmAny.create(10L)
        assertEquals(10L, realmAny.asLong())
        assertEquals(RealmAny.create(10L), realmAny)
        assertEquals(RealmAny.Type.INT, realmAny.type)
    }

    @Test
    fun unmanaged_boolean() {
        val realmAny = RealmAny.create(true)
        assertEquals(true, realmAny.asBoolean())
        assertEquals(RealmAny.create(true), realmAny)
        assertEquals(RealmAny.Type.BOOLEAN, realmAny.type)
    }

    @Test
    fun unmanaged_string() {
        val realmAny = RealmAny.create("Realm")
        assertEquals("Realm", realmAny.asString())
        assertEquals(RealmAny.create("Realm"), realmAny)
        assertEquals(RealmAny.Type.STRING, realmAny.type)
    }

    @Test
    fun unmanaged_float() {
        val realmAny = RealmAny.create(42F)
        assertEquals(42F, realmAny.asFloat())
        assertEquals(RealmAny.create(42F), realmAny)
        assertEquals(RealmAny.Type.FLOAT, realmAny.type)
    }

    @Test
    fun unmanaged_double() {
        val realmAny = RealmAny.create(42.0)
        assertEquals(42.0, realmAny.asDouble())
        assertEquals(RealmAny.create(42.0), realmAny)
        assertEquals(RealmAny.Type.DOUBLE, realmAny.type)
    }

    @Test
    fun unmanaged_realmObjectId() {
        val objectId = ObjectId.from("000000000000000000000000")
        val realmAny = RealmAny.create(objectId)
        assertEquals(objectId, realmAny.asRealmObjectId())
        assertEquals(RealmAny.create(objectId), realmAny)
        assertEquals(RealmAny.Type.OBJECT_ID, realmAny.type)
    }

    @Test
    fun unmanaged_objectId() {
        val objectId = BsonObjectId("000000000000000000000000")
        val realmAny = RealmAny.create(objectId)
        assertEquals(objectId, realmAny.asObjectId())
        assertEquals(RealmAny.create(objectId), realmAny)
        assertEquals(RealmAny.Type.OBJECT_ID, realmAny.type)
    }

    @Test
    fun unmanaged_byteArray() {
        val byteArray = byteArrayOf(42, 41, 40)
        val realmAny = RealmAny.create(byteArray)
        assertContentEquals(byteArray, realmAny.asByteArray())
        assertEquals(RealmAny.create(byteArray), realmAny)
        assertEquals(RealmAny.Type.BYTE_ARRAY, realmAny.type)
    }

    @Test
    fun unmanaged_realmInstant() {
        val instant = RealmInstant.now()
        val realmAny = RealmAny.create(instant)
        assertEquals(instant, realmAny.asRealmInstant())
        assertEquals(RealmAny.create(instant), realmAny)
        assertEquals(RealmAny.Type.REALM_INSTANT, realmAny.type)
    }

    @Test
    fun unmanaged_realmUuid() {
        val uuid = RealmUUID.from("ffffffff-ffff-ffff-ffff-ffffffffffff")
        val realmAny = RealmAny.create(uuid)
        assertEquals(uuid, realmAny.asRealmUUID())
        assertEquals(RealmAny.create(uuid), realmAny)
        assertEquals(RealmAny.Type.REALM_UUID, realmAny.type)
    }

    @Test
    fun unmanaged_realmObject() {
        val obj = TestParent()
        val realmAny = RealmAny.create(obj)
        assertEquals(obj, realmAny.asRealmObject<TestParent>())
        assertEquals(RealmAny.create(obj), realmAny)
        assertEquals(RealmAny.Type.REALM_OBJECT, realmAny.type)
    }

    private fun getManagedRealmAny(block: () -> RealmAny?): RealmAny? {
        realm.writeBlocking {
            val realmAnyObject = copyToRealm(RealmAnyContainer())
            realmAnyObject.anyField = block()
        }

        val container = realm.query<RealmAnyContainer>()
            .first()
            .find { assertNotNull(it) }

        return container.anyField
    }

    @Test
    fun managed_null() {
        val anyField = getManagedRealmAny { null }
        assertNull(anyField)
    }

    @Test
    fun managed_short() {
        val anyField = getManagedRealmAny { RealmAny.create(10.toShort()) }
        assertEquals(10.toShort(), anyField!!.asShort())
        assertEquals(RealmAny.create(10.toShort()), anyField)
        assertEquals(RealmAny.Type.INT, anyField.type)
    }

    @Test
    fun managed_int() {
        val anyField = getManagedRealmAny { RealmAny.create(10) }
        assertEquals(10, anyField!!.asInt())
        assertEquals(RealmAny.create(10), anyField)
        assertEquals(RealmAny.Type.INT, anyField.type)
    }

    @Test
    fun managed_byte() {
        val anyField = getManagedRealmAny { RealmAny.create(10.toByte()) }
        assertEquals(10, anyField!!.asByte())
        assertEquals(RealmAny.create(10.toByte()), anyField)
        assertEquals(RealmAny.Type.INT, anyField.type)
    }

    @Test
    fun managed_char() {
        val anyField = getManagedRealmAny { RealmAny.create(10.toChar()) }
        assertEquals(10.toChar(), anyField!!.asChar())
        assertEquals(RealmAny.create(10.toChar()), anyField)
        assertEquals(RealmAny.Type.INT, anyField.type)
    }

    @Test
    fun managed_long() {
        val anyField = getManagedRealmAny { RealmAny.create(10L) }
        assertEquals(10L, anyField!!.asLong())
        assertEquals(RealmAny.create(10L), anyField)
        assertEquals(RealmAny.Type.INT, anyField.type)
    }

    @Test
    fun managed_boolean() {
        val anyField = getManagedRealmAny { RealmAny.create(true) }
        assertEquals(true, anyField!!.asBoolean())
        assertEquals(RealmAny.create(true), anyField)
        assertEquals(RealmAny.Type.BOOLEAN, anyField.type)
    }

    @Test
    fun managed_string() {
        val anyField = getManagedRealmAny { RealmAny.create("Realm") }
        assertEquals("Realm", anyField!!.asString())
        assertEquals(RealmAny.create("Realm"), anyField)
        assertEquals(RealmAny.Type.STRING, anyField.type)
    }

    @Test
    fun managed_float() {
        val anyField = getManagedRealmAny { RealmAny.create(42F) }
        assertEquals(42F, anyField!!.asFloat())
        assertEquals(RealmAny.create(42F), anyField)
        assertEquals(RealmAny.Type.FLOAT, anyField.type)
    }

    @Test
    fun managed_double() {
        val anyField = getManagedRealmAny { RealmAny.create(42.0) }
        assertEquals(42.0, anyField!!.asDouble())
        assertEquals(RealmAny.create(42.0), anyField)
        assertEquals(RealmAny.Type.DOUBLE, anyField.type)
    }

    @Test
    fun managed_realmObjectId() {
        val objectId = ObjectId.create()
        val anyField = getManagedRealmAny { RealmAny.create(objectId) }
        assertEquals(objectId, anyField!!.asRealmObjectId())
        assertEquals(RealmAny.create(objectId), anyField)
        assertEquals(RealmAny.Type.OBJECT_ID, anyField.type)
    }

    @Test
    fun managed_objectId() {
        val objectId = BsonObjectId()
        val anyField = getManagedRealmAny { RealmAny.create(objectId) }
        assertEquals(objectId, anyField!!.asObjectId())
        assertEquals(RealmAny.create(objectId), anyField)
        assertEquals(RealmAny.Type.OBJECT_ID, anyField.type)
    }

    @Test
    fun managed_byteArray() {
        val byteArray = byteArrayOf(42, 41, 40)
        val anyField = getManagedRealmAny { RealmAny.create(byteArray) }
        assertContentEquals(byteArray, anyField!!.asByteArray())
        assertEquals(RealmAny.create(byteArray), anyField)
        assertEquals(RealmAny.Type.BYTE_ARRAY, anyField.type)
    }

    @Test
    fun managed_realmInstant() {
        val instant = RealmInstant.now()
        val anyField = getManagedRealmAny { RealmAny.create(instant) }
        assertEquals(instant, anyField!!.asRealmInstant())
        assertEquals(RealmAny.create(instant), anyField)
        assertEquals(RealmAny.Type.REALM_INSTANT, anyField.type)
    }

    @Test
    fun managed_realmUUID() {
        val uuid = RealmUUID.random()
        val anyField = getManagedRealmAny { RealmAny.create(uuid) }
        assertEquals(uuid, anyField!!.asRealmUUID())
        assertEquals(RealmAny.create(uuid), anyField)
        assertEquals(RealmAny.Type.REALM_UUID, anyField.type)
    }

    @Test
    fun managed_realmObject() {
        val obj = TestParent()
        val anyField = getManagedRealmAny { RealmAny.create(obj) }
        assertEquals(obj.name, anyField!!.asRealmObject<TestParent>().name)
        assertEquals(
            RealmAny.create(obj).asRealmObject<TestParent>().name,
            anyField.asRealmObject<TestParent>().name
        )
        assertEquals(RealmAny.Type.REALM_OBJECT, anyField.type)
    }
}

class TestParent : RealmObject {
    var name: String? = "Parent"
}

class TestEmbeddedChild : EmbeddedRealmObject {
    var name: String? = "Embedded-child"
}

class RealmAnyContainer : RealmObject {
    var anyField: RealmAny? = RealmAny.create(42.toShort())
}

class IndexedRealmAnyContainer : RealmObject {
    @Index
    var anyField: RealmAny? = RealmAny.create(42.toShort())
}

class NotInSchema : RealmObject {
    var name: String? = "not in schema"
}
