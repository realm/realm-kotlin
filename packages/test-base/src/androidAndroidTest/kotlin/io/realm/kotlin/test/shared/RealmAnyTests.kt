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
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Suppress("LargeClass")
class RealmAnyTests {

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    private val supportedKotlinTypes = setOf(
        Short::class,
        Int::class,
        Byte::class,
        Char::class,
        Long::class,
        Boolean::class,
        String::class,
        Float::class,
        Double::class,
        ObjectId::class,
        BsonObjectId::class,
        ByteArray::class,
        RealmInstant::class,
        RealmUUID::class,
        TestParent::class
    )

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
    // DONE lists
    // DONE sets
    // TODO remember to comment out 'check_value_assignable' in object.cpp L330 and list.cpp and set.cpp until mixed columns are allowed in the C-API - see https://github.com/realm/realm-core/issues/5985

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
    fun unmanaged_incorrectTypeThrows() {
        for (type in supportedKotlinTypes) {
            when (type) {
                Short::class ->
                    loopThroughSupportedTypes(Short::class, RealmAny.create(10.toShort()))
                Int::class ->
                    loopThroughSupportedTypes(Int::class, RealmAny.create(10))
                Byte::class ->
                    loopThroughSupportedTypes(Byte::class, RealmAny.create(10.toByte()))
                Char::class ->
                    loopThroughSupportedTypes(Char::class, RealmAny.create(10.toChar()))
                Long::class ->
                    loopThroughSupportedTypes(Long::class, RealmAny.create(10L))
                Boolean::class ->
                    loopThroughSupportedTypes(Boolean::class, RealmAny.create(true))
                String::class ->
                    loopThroughSupportedTypes(String::class, RealmAny.create("hello"))
                Float::class ->
                    loopThroughSupportedTypes(Float::class, RealmAny.create(10F))
                Double::class ->
                    loopThroughSupportedTypes(Double::class, RealmAny.create(10.0))
                ObjectId::class ->
                    loopThroughSupportedTypes(ObjectId::class, RealmAny.create(ObjectId.create()))
                BsonObjectId::class ->
                    loopThroughSupportedTypes(BsonObjectId::class, RealmAny.create(BsonObjectId()))
                ByteArray::class ->
                    loopThroughSupportedTypes(ByteArray::class, RealmAny.create(byteArrayOf(42)))
                RealmInstant::class ->
                    loopThroughSupportedTypes(RealmInstant::class, RealmAny.create(RealmInstant.now()))
                RealmUUID::class ->
                    loopThroughSupportedTypes(RealmUUID::class, RealmAny.create(RealmUUID.random()))
                TestParent::class -> {
                    loopThroughSupportedTypes(TestParent::class, RealmAny.create(TestParent()))
                }
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

    @Test
    fun managed_incorrectTypeThrows() {
        for (type in supportedKotlinTypes) {
            when (type) {
                Short::class -> createManagedRealmAny { RealmAny.create(10.toShort()) }.let {
                    assertFailsWith<IllegalStateException> { assertNotNull(it).asRealmUUID() }
                }
                Int::class -> createManagedRealmAny { RealmAny.create(10) }.let {
                    assertFailsWith<IllegalStateException> { assertNotNull(it).asRealmUUID() }
                }
                Byte::class -> createManagedRealmAny { RealmAny.create(10.toByte()) }.let {
                    assertFailsWith<IllegalStateException> { assertNotNull(it).asRealmUUID() }
                }
                Char::class -> createManagedRealmAny { RealmAny.create(10.toChar()) }.let {
                    assertFailsWith<IllegalStateException> { assertNotNull(it).asRealmUUID() }
                }
                Long::class -> createManagedRealmAny { RealmAny.create(10L) }.let {
                    assertFailsWith<IllegalStateException> { assertNotNull(it).asRealmUUID() }
                }
                Boolean::class -> createManagedRealmAny { RealmAny.create(true) }.let {
                    assertFailsWith<IllegalStateException> { assertNotNull(it).asRealmUUID() }
                }
                String::class -> createManagedRealmAny { RealmAny.create("hello") }.let {
                    assertFailsWith<IllegalStateException> { assertNotNull(it).asRealmUUID() }
                }
                Float::class -> createManagedRealmAny { RealmAny.create(10F) }.let {
                    assertFailsWith<IllegalStateException> { assertNotNull(it).asRealmUUID() }
                }
                Double::class -> createManagedRealmAny { RealmAny.create(10.0) }.let {
                    assertFailsWith<IllegalStateException> { assertNotNull(it).asRealmUUID() }
                }
                ObjectId::class -> createManagedRealmAny { RealmAny.create(ObjectId.create()) }.let {
                    assertFailsWith<IllegalStateException> { assertNotNull(it).asRealmUUID() }
                }
                BsonObjectId::class -> createManagedRealmAny { RealmAny.create(BsonObjectId()) }.let {
                    assertFailsWith<IllegalStateException> { assertNotNull(it).asRealmUUID() }
                }
                ByteArray::class -> createManagedRealmAny {
                    RealmAny.create(byteArrayOf(42, 43, 44))
                }.let {
                    assertFailsWith<IllegalStateException> { assertNotNull(it).asRealmUUID() }
                }
                RealmInstant::class -> createManagedRealmAny {
                    RealmAny.create(RealmInstant.now())
                }.let {
                    assertFailsWith<IllegalStateException> { assertNotNull(it).asRealmUUID() }
                }
                RealmUUID::class -> createManagedRealmAny { RealmAny.create(RealmUUID.random()) }.let {
                    assertFailsWith<IllegalStateException> { assertNotNull(it).asFloat() }
                }
                TestParent::class -> createManagedRealmAny { RealmAny.create(TestParent()) }.let {
                    assertFailsWith<IllegalStateException> { assertNotNull(it).asRealmUUID() }
                }
            }
        }
    }

    @Test
    fun managed_null() {
        val anyField = createManagedRealmAny { null }
        assertNull(anyField)
    }

    @Test
    fun managed_short() {
        val anyField = assertNotNull(createManagedRealmAny { RealmAny.create(10.toShort()) })
        assertEquals(10.toShort(), anyField.asShort())
        assertEquals(RealmAny.create(10.toShort()), anyField)
        assertEquals(RealmAny.Type.INT, anyField.type)
    }

    @Test
    fun managed_int() {
        val anyField = assertNotNull(createManagedRealmAny { RealmAny.create(10) })
        assertEquals(10, anyField.asInt())
        assertEquals(RealmAny.create(10), anyField)
        assertEquals(RealmAny.Type.INT, anyField.type)
    }

    @Test
    fun managed_byte() {
        val anyField = assertNotNull(createManagedRealmAny { RealmAny.create(10.toByte()) })
        assertEquals(10, anyField.asByte())
        assertEquals(RealmAny.create(10.toByte()), anyField)
        assertEquals(RealmAny.Type.INT, anyField.type)
    }

    @Test
    fun managed_char() {
        val anyField = assertNotNull(createManagedRealmAny { RealmAny.create(10.toChar()) })
        assertEquals(10.toChar(), anyField.asChar())
        assertEquals(RealmAny.create(10.toChar()), anyField)
        assertEquals(RealmAny.Type.INT, anyField.type)
    }

    @Test
    fun managed_long() {
        val anyField = assertNotNull(createManagedRealmAny { RealmAny.create(10L) })
        assertEquals(10L, anyField.asLong())
        assertEquals(RealmAny.create(10L), anyField)
        assertEquals(RealmAny.Type.INT, anyField.type)
    }

    @Test
    fun managed_boolean() {
        val anyField = assertNotNull(createManagedRealmAny { RealmAny.create(true) })
        assertEquals(true, anyField.asBoolean())
        assertEquals(RealmAny.create(true), anyField)
        assertEquals(RealmAny.Type.BOOLEAN, anyField.type)
    }

    @Test
    fun managed_string() {
        val anyField = assertNotNull(createManagedRealmAny { RealmAny.create("Realm") })
        assertEquals("Realm", anyField.asString())
        assertEquals(RealmAny.create("Realm"), anyField)
        assertEquals(RealmAny.Type.STRING, anyField.type)
    }

    @Test
    fun managed_float() {
        val anyField = assertNotNull(createManagedRealmAny { RealmAny.create(42F) })
        assertEquals(42F, anyField.asFloat())
        assertEquals(RealmAny.create(42F), anyField)
        assertEquals(RealmAny.Type.FLOAT, anyField.type)
    }

    @Test
    fun managed_double() {
        val anyField = assertNotNull(createManagedRealmAny { RealmAny.create(42.0) })
        assertEquals(42.0, anyField.asDouble())
        assertEquals(RealmAny.create(42.0), anyField)
        assertEquals(RealmAny.Type.DOUBLE, anyField.type)
    }

    @Test
    fun managed_realmObjectId() {
        val objectId = ObjectId.create()
        val anyField = assertNotNull(createManagedRealmAny { RealmAny.create(objectId) })
        assertEquals(objectId, anyField.asRealmObjectId())
        assertEquals(RealmAny.create(objectId), anyField)
        assertEquals(RealmAny.Type.OBJECT_ID, anyField.type)
    }

    @Test
    fun managed_objectId() {
        val objectId = BsonObjectId()
        val anyField = assertNotNull(createManagedRealmAny { RealmAny.create(objectId) })
        assertEquals(objectId, anyField.asObjectId())
        assertEquals(RealmAny.create(objectId), anyField)
        assertEquals(RealmAny.Type.OBJECT_ID, anyField.type)
    }

    @Test
    fun managed_byteArray() {
        val byteArray = byteArrayOf(42, 41, 40)
        val anyField = assertNotNull(createManagedRealmAny { RealmAny.create(byteArray) })
        assertContentEquals(byteArray, anyField.asByteArray())
        assertEquals(RealmAny.create(byteArray), anyField)
        assertEquals(RealmAny.Type.BYTE_ARRAY, anyField.type)
    }

    @Test
    fun managed_realmInstant() {
        val instant = RealmInstant.now()
        val anyField = assertNotNull(createManagedRealmAny { RealmAny.create(instant) })
        assertEquals(instant, anyField.asRealmInstant())
        assertEquals(RealmAny.create(instant), anyField)
        assertEquals(RealmAny.Type.REALM_INSTANT, anyField.type)
    }

    @Test
    fun managed_realmUUID() {
        val uuid = RealmUUID.random()
        val anyField = assertNotNull(createManagedRealmAny { RealmAny.create(uuid) })
        assertEquals(uuid, anyField.asRealmUUID())
        assertEquals(RealmAny.create(uuid), anyField)
        assertEquals(RealmAny.Type.REALM_UUID, anyField.type)
    }

    @Test
    fun managed_realmObject() {
        val obj: TestParent = TestParent().apply { name = "AAA" }
        val managedAnyField = assertNotNull(createManagedRealmAny { RealmAny.create(obj) })
        assertEquals(obj.name, managedAnyField.asRealmObject<TestParent>().name)
        assertEquals(RealmAny.Type.REALM_OBJECT, managedAnyField.type)
    }

//    @Test
//    fun managed_updateThroughAllTypes() {
//        for (type in supportedKotlinTypes) {
//            when (type) {
//                Short::class -> {
//                    val realmAny = createManagedRealmAny { RealmAny.create(10.toShort()) }
//                    supportedKotlinTypes.forEach {
//                        realm.writeBlocking {
//                            when (it) {
//                                Short::class -> {
//                                    realmAn
//                                    assertEquals()
//                                    TODO()
//                                }
//                                Int::class -> TODO()
//                                Byte::class -> TODO()
//                                Char::class -> TODO()
//                                Long::class -> TODO()
//                                Boolean::class -> TODO()
//                                String::class -> TODO()
//                                Float::class -> TODO()
//                                Double::class -> TODO()
//                                ObjectId::class -> TODO()
//                                BsonObjectId::class -> TODO()
//                                ByteArray::class -> TODO()
//                                RealmInstant::class -> TODO()
//                                RealmUUID::class -> TODO()
//                                TestParent::class -> TODO()
//                            }
//                        }
//                    }
//                    RealmAny.create(10.toShort()).let {
//                        assertFailsWith<IllegalStateException> { it.asRealmUUID() }
//                    }
//                }
//                Int::class -> RealmAny.create(10).let {
//                    assertFailsWith<IllegalStateException> { it.asRealmUUID() }
//                }
//                Byte::class -> RealmAny.create(10.toByte()).let {
//                    assertFailsWith<IllegalStateException> { it.asRealmUUID() }
//                }
//                Char::class -> RealmAny.create(10.toChar()).let {
//                    assertFailsWith<IllegalStateException> { it.asRealmUUID() }
//                }
//                Long::class -> RealmAny.create(10L).let {
//                    assertFailsWith<IllegalStateException> { it.asRealmUUID() }
//                }
//                Boolean::class -> RealmAny.create(true).let {
//                    assertFailsWith<IllegalStateException> { it.asRealmUUID() }
//                }
//                String::class -> RealmAny.create("hello").let {
//                    assertFailsWith<IllegalStateException> { it.asRealmUUID() }
//                }
//                Float::class -> RealmAny.create(10F).let {
//                    assertFailsWith<IllegalStateException> { it.asRealmUUID() }
//                }
//                Double::class -> RealmAny.create(10.0).let {
//                    assertFailsWith<IllegalStateException> { it.asRealmUUID() }
//                }
//                ObjectId::class -> RealmAny.create(ObjectId.create()).let {
//                    assertFailsWith<IllegalStateException> { it.asRealmUUID() }
//                }
//                BsonObjectId::class -> RealmAny.create(BsonObjectId()).let {
//                    assertFailsWith<IllegalStateException> { it.asRealmUUID() }
//                }
//                ByteArray::class -> RealmAny.create(byteArrayOf(42, 43, 44)).let {
//                    assertFailsWith<IllegalStateException> { it.asRealmUUID() }
//                }
//                RealmInstant::class -> RealmAny.create(RealmInstant.now()).let {
//                    assertFailsWith<IllegalStateException> { it.asRealmUUID() }
//                }
//                RealmUUID::class -> RealmAny.create(RealmUUID.random()).let {
//                    assertFailsWith<IllegalStateException> { it.asFloat() }
//                }
//                TestParent::class -> RealmAny.create(TestParent()).let {
//                    assertFailsWith<IllegalStateException> { it.asRealmUUID() }
//                }
//            }
//        }
//    }

    private fun createManagedRealmAny(block: () -> RealmAny?): RealmAny? {
        return realm.writeBlocking {
            copyToRealm(
                RealmAnyContainer().apply {
                    this.anyField = block()
                }
            )
        }.anyField
    }

    private fun loopThroughSupportedTypes(excludedType: KClass<*>, value: RealmAny) {
        supportedKotlinTypes.filter {it != excludedType }
            .forEach { clazz ->
                when (clazz) {
                    // Exclude these numerals as the underlying value is the same
                    Int::class -> if (excludedType != Short::class &&
                        excludedType != Int::class &&
                        excludedType != Byte::class &&
                        excludedType != Char::class &&
                        excludedType != Long::class) {
                        assertFailsWith<IllegalStateException> { value.asInt() }
                    }
                    // Exclude these numerals as the underlying value is the same
                    Byte::class -> if (excludedType != Short::class &&
                        excludedType != Int::class &&
                        excludedType != Byte::class &&
                        excludedType != Char::class &&
                        excludedType != Long::class) {
                        assertFailsWith<IllegalStateException> { value.asByte() }
                    }
                    // Exclude these numerals as the underlying value is the same
                    Char::class -> if (excludedType != Short::class &&
                        excludedType != Int::class &&
                        excludedType != Byte::class &&
                        excludedType != Char::class &&
                        excludedType != Long::class) {
                        assertFailsWith<IllegalStateException> { value.asChar() }
                    }
                    // Exclude these numerals as the underlying value is the same
                    Long::class -> if (excludedType != Short::class &&
                        excludedType != Int::class &&
                        excludedType != Byte::class &&
                        excludedType != Char::class &&
                        excludedType != Long::class) {
                        assertFailsWith<IllegalStateException> { value.asLong() }
                    }
                    Boolean::class ->
                        assertFailsWith<IllegalStateException> { value.asBoolean() }
                    String::class ->
                        assertFailsWith<IllegalStateException> { value.asString() }
                    Float::class ->
                        assertFailsWith<IllegalStateException> { value.asFloat() }
                    Double::class ->
                        assertFailsWith<IllegalStateException> { value.asDouble() }
                    // Exclude BsonObjectId as the underlying value is the same
                    ObjectId::class -> if (excludedType != BsonObjectId::class) {
                        assertFailsWith<IllegalStateException> { value.asRealmObjectId() }
                    }
                    // Exclude ObjectId as the underlying value is the same
                    BsonObjectId::class -> if (excludedType != ObjectId::class) {
                        assertFailsWith<IllegalStateException> { value.asObjectId() }
                    }
                    ByteArray::class ->
                        assertFailsWith<IllegalStateException> { value.asByteArray() }
                    RealmInstant::class ->
                        assertFailsWith<IllegalStateException> { value.asRealmInstant() }
                    RealmUUID::class ->
                        assertFailsWith<IllegalStateException> { value.asRealmUUID() }
                    TestParent::class -> assertFailsWith<IllegalStateException> {
                        value.asRealmObject<TestParent>()
                    }
                }
            }
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
    var obj: TestParent? = TestParent()
}

class IndexedRealmAnyContainer : RealmObject {
    @Index
    var anyField: RealmAny? = RealmAny.create(42.toShort())
}

class NotInSchema : RealmObject {
    var name: String? = "not in schema"
}
