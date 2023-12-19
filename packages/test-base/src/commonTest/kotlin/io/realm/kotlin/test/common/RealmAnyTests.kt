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

package io.realm.kotlin.test.common

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.entities.embedded.EmbeddedChild
import io.realm.kotlin.entities.embedded.EmbeddedParent
import io.realm.kotlin.entities.embedded.embeddedSchema
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.notifications.DeletedObject
import io.realm.kotlin.notifications.InitialObject
import io.realm.kotlin.notifications.PendingObject
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.notifications.UpdatedObject
import io.realm.kotlin.query.find
import io.realm.kotlin.test.common.utils.assertFailsWithMessage
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TestChannel
import io.realm.kotlin.test.util.TypeDescriptor
import io.realm.kotlin.test.util.receiveOrFail
import io.realm.kotlin.test.util.use
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import io.realm.kotlin.types.annotations.Index
import kotlinx.coroutines.async
import org.mongodb.kbson.BsonDecimal128
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128
import org.mongodb.kbson.ObjectId
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

@Suppress("LargeClass")
class RealmAnyTests {

    private lateinit var configBuilder: RealmConfiguration.Builder
    private lateinit var configuration: RealmConfiguration
    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configBuilder = RealmConfiguration.Builder(
            embeddedSchema +
                IndexedRealmAnyContainer::class +
                RealmAnyContainer::class +
                Sample::class
        ).directory(tmpDir)
        configuration = configBuilder.build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun missingClassFromSchema_unmanagedWorks() {
        val value = NotInSchema()
        val realmAny = RealmAny.create(value, NotInSchema::class)
        assertEquals(value, realmAny.asRealmObject())
    }

    @Test
    fun missingClassFromSchema_managedThrows() {
        val notInSchema = NotInSchema()
        realm.writeBlocking {
            val unmanaged = IndexedRealmAnyContainer()
            val managed = copyToRealm(unmanaged)
            val realmAnyWithClassNotInSchema = RealmAny.create(notInSchema, NotInSchema::class)
            assertFailsWithMessage<IllegalArgumentException>("Schema does not contain a class named 'NotInSchema'") {
                managed.anyField = realmAnyWithClassNotInSchema
            }
        }
    }

    // There is currently no way for us to instantiate a DynamicRealmObject when getting an object
    // from a RealmAny if the class is not present in the schema unlike in the Java SDK.
    @Test
    fun missingNewClassInOlderSchema_throws() {
        // Open original schema first
        val originalConfig = RealmConfiguration.Builder(
            setOf(RealmAnyContainer::class, NotInSchema::class)
        ).directory(tmpDir)
            .name("testDb")
            .build()
        Realm.open(originalConfig).use { realm ->
            realm.writeBlocking {
                val unmanagedContainer = RealmAnyContainer(RealmAny.create(NotInSchema()))
                copyToRealm(unmanagedContainer)
            }
            realm.query<NotInSchema>()
                .first()
                .find {
                    assertNotNull(it)
                }
        }

        // Open a realm that has a subset of the original schema and get the container stored above
        val configWithNewClass = RealmConfiguration.Builder(
            setOf(RealmAnyContainer::class)
        ).directory(tmpDir)
            .name("testDb")
            .build()
        Realm.open(configWithNewClass).use { realm ->
            realm.query<RealmAnyContainer>()
                .first()
                .find {
                    assertNotNull(it)
                    assertFailsWithMessage<IllegalArgumentException>("The object class is not present") {
                        it.anyField
                    }
                }
        }
    }

    @Test
    fun unmanaged_incorrectTypeThrows() {
        supportedRealmAnys.forEach {
            assertThrowsOnInvalidType(it.key, it.value)
        }
    }

    @Test
    fun unmanaged_coreIntValuesAreTheSame() {
        assertCoreIntValuesAreTheSame(
            fromLong = RealmAny.create(42L),
            fromInt = RealmAny.create(42),
            fromChar = RealmAny.create(42.toChar()),
            fromShort = RealmAny.create(42.toShort()),
            fromByte = RealmAny.create(42.toByte())
        )
    }

    @Test
    fun unmanaged_numericOverflow() {
        assertNumericOverflow {
            assertNotNull(it)
        }
    }

    @Test
    fun unmanaged_allTypes() {
        for (type in TypeDescriptor.anyClassifiers.keys) {
            when (type) {
                Short::class -> {
                    val realmAny = RealmAny.create(10.toShort())
                    assertEquals(10, realmAny.asShort())
                    assertEquals(RealmAny.create(10.toShort()), realmAny)
                    assertEquals(RealmAny.Type.INT, realmAny.type)
                }
                Int::class -> {
                    val realmAny = RealmAny.create(10)
                    assertEquals(10, realmAny.asInt())
                    assertEquals(RealmAny.create(10), realmAny)
                    assertEquals(RealmAny.Type.INT, realmAny.type)
                }
                Byte::class -> {
                    val realmAny = RealmAny.create(10.toByte())
                    assertEquals(10.toByte(), realmAny.asByte())
                    assertEquals(RealmAny.create(10.toByte()), realmAny)
                    assertEquals(RealmAny.Type.INT, realmAny.type)
                }
                Char::class -> {
                    val realmAny = RealmAny.create(10.toChar())
                    assertEquals(10.toChar(), realmAny.asChar())
                    assertEquals(RealmAny.create(10.toChar()), realmAny)
                    assertEquals(RealmAny.Type.INT, realmAny.type)
                }
                Long::class -> {
                    val realmAny = RealmAny.create(10L)
                    assertEquals(10L, realmAny.asLong())
                    assertEquals(RealmAny.create(10L), realmAny)
                    assertEquals(RealmAny.Type.INT, realmAny.type)
                }
                Boolean::class -> {
                    val realmAny = RealmAny.create(true)
                    assertEquals(true, realmAny.asBoolean())
                    assertEquals(RealmAny.create(true), realmAny)
                    assertEquals(RealmAny.Type.BOOL, realmAny.type)
                }
                String::class -> {
                    val realmAny = RealmAny.create("Realm")
                    assertEquals("Realm", realmAny.asString())
                    assertEquals(RealmAny.create("Realm"), realmAny)
                    assertEquals(RealmAny.Type.STRING, realmAny.type)
                }
                Float::class -> {
                    val realmAny = RealmAny.create(42F)
                    assertEquals(42F, realmAny.asFloat())
                    assertEquals(RealmAny.create(42F), realmAny)
                    assertEquals(RealmAny.Type.FLOAT, realmAny.type)
                }
                Double::class -> {
                    val realmAny = RealmAny.create(42.0)
                    assertEquals(42.0, realmAny.asDouble())
                    assertEquals(RealmAny.create(42.0), realmAny)
                    assertEquals(RealmAny.Type.DOUBLE, realmAny.type)
                }
                Decimal128::class -> {
                    val realmAny = RealmAny.create(Decimal128("1.5"))
                    assertEquals(Decimal128("1.5"), realmAny.asDecimal128())
                    assertEquals(RealmAny.create(Decimal128("1.5")), realmAny)
                    assertEquals(RealmAny.Type.DECIMAL128, realmAny.type)
                }
                BsonObjectId::class -> {
                    val objectId = BsonObjectId("000000000000000000000000")
                    val realmAny = RealmAny.create(objectId)
                    assertEquals(objectId, realmAny.asObjectId())
                    assertEquals(RealmAny.create(objectId), realmAny)
                    assertEquals(RealmAny.Type.OBJECT_ID, realmAny.type)
                }
                ByteArray::class -> {
                    val byteArray = byteArrayOf(42, 41, 40)
                    val realmAny = RealmAny.create(byteArray)
                    assertContentEquals(byteArray, realmAny.asByteArray())
                    assertEquals(RealmAny.create(byteArray), realmAny)
                    assertEquals(RealmAny.Type.BINARY, realmAny.type)
                }
                RealmInstant::class -> {
                    val instant = RealmInstant.now()
                    val realmAny = RealmAny.create(instant)
                    assertEquals(instant, realmAny.asRealmInstant())
                    assertEquals(RealmAny.create(instant), realmAny)
                    assertEquals(RealmAny.Type.TIMESTAMP, realmAny.type)
                }
                RealmUUID::class -> {
                    val uuid = RealmUUID.from("ffffffff-ffff-ffff-ffff-ffffffffffff")
                    val realmAny = RealmAny.create(uuid)
                    assertEquals(uuid, realmAny.asRealmUUID())
                    assertEquals(RealmAny.create(uuid), realmAny)
                    assertEquals(RealmAny.Type.UUID, realmAny.type)
                }
                RealmObject::class -> {
                    val obj = Sample()
                    val realmAny = RealmAny.create(obj, Sample::class)
                    assertEquals(obj, realmAny.asRealmObject())
                    assertEquals(RealmAny.create(obj, Sample::class), realmAny)
                    assertEquals(RealmAny.Type.OBJECT, realmAny.type)
                }
                else -> fail("Missing testing for type $type")
            }
        }
    }

    @Test
    fun unmanaged_asRealmObjectWrongCastThrows() {
        val realmAny = RealmAny.create(Sample())
        assertFailsWith<ClassCastException> {
            realmAny.asRealmObject<RealmAnyContainer>()
        }
    }

    @Test
    fun managed_asRealmObjectWrongCastThrows() {
        val realmAny = RealmAny.create(Sample())
        val container = RealmAnyContainer(realmAny)
        val managedContainer = realm.writeBlocking {
            copyToRealm(container)
        }
        val managedRealmAny = assertNotNull(managedContainer.anyField)
        assertFailsWith<ClassCastException> {
            managedRealmAny.asRealmObject<RealmAnyContainer>()
        }
    }

    // Currently we don't allow casting a typed Realm object to a DynamicRealmObject when
    // read as part of a typed Realm. See https://github.com/realm/realm-kotlin/issues/1423
    // for why we might want to allow that. For now, capture the behaviour.
    @Test
    fun managed_asRealmObjectThrowsForDynamicRealmObject() {
        realm.writeBlocking {
            val liveObject = copyToRealm(
                Sample().apply {
                    this.stringField = "parentObject"
                    this.nullableRealmAnyField = RealmAny.create(
                        Sample().apply {
                            this.stringField = "realmAnyObject"
                        }
                    )
                }
            )
            assertEquals(2, query<Sample>().count().find())
            assertFailsWith<ClassCastException> {
                val dynamicObject = liveObject.nullableRealmAnyField!!.asRealmObject<DynamicRealmObject>()
            }
        }
    }

    @Test
    fun managed_incorrectTypeThrows() {
        supportedRealmAnys.forEach { (type, value: RealmAny) ->
            assertThrowsOnInvalidType(type, createManagedRealmAny { value }!!)
        }
    }

    @Test
    fun managed_updateThroughAllTypes() {
        loopSupportedTypes(createManagedContainer())
    }

    @Test
    fun managed_coreIntValuesAreTheSame() {
        assertCoreIntValuesAreTheSame(
            fromLong = assertNotNull(createManagedRealmAny { RealmAny.create(42) }),
            fromInt = assertNotNull(createManagedRealmAny { RealmAny.create(42L) }),
            fromChar = assertNotNull(createManagedRealmAny { RealmAny.create(42.toShort()) }),
            fromShort = assertNotNull(createManagedRealmAny { RealmAny.create(42.toByte()) }),
            fromByte = assertNotNull(createManagedRealmAny { RealmAny.create(42.toChar()) })
        )
    }

    @Test
    fun managed_numericOverflow() {
        assertNumericOverflow {
            assertNotNull(createManagedRealmAny { it })
        }
    }

    @Test
    fun managed_deletedObject() {
        val managedContainer = realm.writeBlocking {
            val unmanagedContainer = RealmAnyContainer(RealmAny.create(Sample()))
            copyToRealm(unmanagedContainer)
        }
        realm.writeBlocking {
            delete(query<Sample>())
        }
        realm.writeBlocking {
            val updatedContainer = findLatest(managedContainer)
            assertNull(assertNotNull(updatedContainer).anyField)
        }
    }

    @Test
    fun managed_deleteObjectInsideRealmAnyTriggersUpdateInContainer() {
        runBlocking {
            val sampleChannel = TestChannel<SingleQueryChange<Sample>>()
            val containerChannel = TestChannel<SingleQueryChange<RealmAnyContainer>>()

            val sampleObserver = async {
                realm.query<Sample>()
                    .first()
                    .asFlow()
                    .collect {
                        sampleChannel.send(it)
                    }
            }
            val containerObserver = async {
                realm.query<RealmAnyContainer>()
                    .first()
                    .asFlow()
                    .collect {
                        containerChannel.send(it)
                    }
            }

            assertIs<PendingObject<Sample>>(sampleChannel.receiveOrFail())
            assertIs<PendingObject<RealmAnyContainer>>(containerChannel.receiveOrFail())

            val unmanagedContainer = RealmAnyContainer(RealmAny.create(Sample()))
            realm.writeBlocking {
                copyToRealm(unmanagedContainer)
            }

            assertIs<InitialObject<Sample>>(sampleChannel.receiveOrFail())
            assertIs<InitialObject<RealmAnyContainer>>(containerChannel.receiveOrFail())

            realm.writeBlocking {
                delete(query<Sample>())
            }

            val deletedObjectEvent = sampleChannel.receiveOrFail()
            val updatedContainerEvent = containerChannel.receiveOrFail()
            assertIs<DeletedObject<Sample>>(deletedObjectEvent)
            assertNull(deletedObjectEvent.obj)
            assertIs<UpdatedObject<RealmAnyContainer>>(updatedContainerEvent)
            assertNull(assertNotNull(updatedContainerEvent.obj).anyField)

            sampleObserver.cancel()
            containerObserver.cancel()
            sampleChannel.close()
            containerChannel.close()
        }
    }

    @Test
    fun equals() {
        RealmAny.Type.values().forEach { type ->
            when (type) {
                RealmAny.Type.INT -> {
                    assertEquals(RealmAny.create(1), RealmAny.create(Char(1)))
                    assertEquals(RealmAny.create(1), RealmAny.create(1.toByte()))
                    assertEquals(RealmAny.create(1), RealmAny.create(1.toShort()))
                    assertEquals(RealmAny.create(1), RealmAny.create(1.toInt()))
                    assertEquals(RealmAny.create(1), RealmAny.create(1.toLong()))
                    assertNotEquals(RealmAny.create(1), RealmAny.create(2))
                }
                RealmAny.Type.BOOL -> {
                    assertEquals(RealmAny.create(true), RealmAny.create(true))
                    assertNotEquals(RealmAny.create(true), RealmAny.create(false))
                }
                RealmAny.Type.STRING -> {
                    assertEquals(RealmAny.create("Realm"), RealmAny.create("Realm"))
                    assertNotEquals(RealmAny.create("Realm"), RealmAny.create("Not Realm"))
                }
                RealmAny.Type.BINARY -> {
                    assertEquals(
                        RealmAny.create(byteArrayOf(1, 2)), RealmAny.create(byteArrayOf(1, 2))
                    )
                    assertNotEquals(
                        RealmAny.create(byteArrayOf(1, 2)), RealmAny.create(byteArrayOf(2, 1))
                    )
                }
                RealmAny.Type.TIMESTAMP -> {
                    val now = RealmInstant.now()
                    assertEquals(RealmAny.create(now), RealmAny.create(now))
                    assertNotEquals(RealmAny.create(RealmInstant.from(1, 1)), RealmAny.create(now))
                }
                RealmAny.Type.FLOAT -> {
                    assertEquals(RealmAny.create(1.5f), RealmAny.create(1.5f))
                    assertNotEquals(RealmAny.create(1.2f), RealmAny.create(1.3f))
                }
                RealmAny.Type.DOUBLE -> {
                    assertEquals(RealmAny.create(1.5), RealmAny.create(1.5))
                    assertNotEquals(RealmAny.create(1.2), RealmAny.create(1.3))
                }
                RealmAny.Type.DECIMAL128 -> {
                    assertEquals(RealmAny.create(Decimal128("1E64")), RealmAny.create(Decimal128("1E64")))
                    assertNotEquals(RealmAny.create(Decimal128("1E64")), RealmAny.create(Decimal128("-1E64")))
                }
                RealmAny.Type.OBJECT_ID -> {
                    val value = ObjectId()
                    assertEquals(RealmAny.create(value), RealmAny.create(value))
                    assertNotEquals(RealmAny.create(ObjectId()), RealmAny.create(value))
                }
                RealmAny.Type.UUID -> {
                    val value = RealmUUID.random()
                    assertEquals(RealmAny.create(value), RealmAny.create(value))
                    assertNotEquals(RealmAny.create(RealmUUID.random()), RealmAny.create(value))
                }
                RealmAny.Type.OBJECT -> {
                    val realmObject = Sample()
                    // Same object is equal
                    assertEquals(RealmAny.create(realmObject), RealmAny.create(realmObject))
                    // Different kind of objects are not equal
                    assertNotEquals(RealmAny.create(RealmAnyContainer()), RealmAny.create(realmObject))
                    // Different objects of same type are not equal
                    assertNotEquals(RealmAny.create(Sample()), RealmAny.create(realmObject))
                }
            }
        }
    }

    @Test
    fun embeddedObject_worksInsideParent() {
        val embeddedChild = EmbeddedChild("CHILD")
        val parent = EmbeddedParent().apply {
            id = "PARENT"
            child = embeddedChild
        }

        // Check writing a parent with an embedded object works
        val validContainer = RealmAnyContainer(RealmAny.create(parent))
        realm.writeBlocking {
            copyToRealm(validContainer)
        }
        assertEquals(1, realm.query<EmbeddedParent>().count().find())
        assertEquals(1, realm.query<EmbeddedChild>().count().find())
    }

    private fun assertCoreIntValuesAreTheSame(
        fromInt: RealmAny,
        fromLong: RealmAny,
        fromShort: RealmAny,
        fromByte: RealmAny,
        fromChar: RealmAny
    ) {
        assertEquals(fromLong, fromInt)
        assertEquals(fromLong, fromChar)
        assertEquals(fromLong, fromShort)
        assertEquals(fromLong, fromByte)

        assertEquals(fromInt, fromLong)
        assertEquals(fromInt, fromChar)
        assertEquals(fromInt, fromShort)
        assertEquals(fromInt, fromByte)

        assertEquals(fromShort, fromLong)
        assertEquals(fromShort, fromInt)
        assertEquals(fromShort, fromChar)
        assertEquals(fromShort, fromByte)

        assertEquals(fromByte, fromLong)
        assertEquals(fromByte, fromInt)
        assertEquals(fromByte, fromChar)
        assertEquals(fromByte, fromShort)
    }

    private fun assertNumericOverflow(block: ((RealmAny) -> RealmAny)) {
        fun assertNumericCoercionOverflows(managedRealmAny: RealmAny, block: (RealmAny) -> Number) {
            assertFailsWithMessage<ArithmeticException>("Cannot convert value with") {
                block(managedRealmAny)
            }
        }

        fun assertCharCoercionOverflows(managedRealmAny: RealmAny) {
            assertFailsWithMessage<ArithmeticException>("Cannot convert value with") {
                managedRealmAny.asChar()
            }
        }

        listOf(
            Long::class to RealmAny.create(Long.MAX_VALUE),
            Int::class to RealmAny.create(Int.MAX_VALUE),
            Char::class to RealmAny.create(Char.MAX_VALUE),
            Short::class to RealmAny.create(Short.MAX_VALUE)
        ).forEach { (clazz, realmAny) ->
            val actualValue = block(realmAny)
            when (clazz) {
                Long::class -> {
                    assertNumericCoercionOverflows(actualValue) { it.asInt() }
                    assertCharCoercionOverflows(actualValue)
                    assertNumericCoercionOverflows(actualValue) { it.asShort() }
                    assertNumericCoercionOverflows(actualValue) { it.asByte() }
                }
                Int::class -> {
                    assertCharCoercionOverflows(actualValue)
                    assertNumericCoercionOverflows(actualValue) { it.asShort() }
                    assertNumericCoercionOverflows(actualValue) { it.asByte() }
                }
                Char::class -> {
                    assertNumericCoercionOverflows(actualValue) { it.asShort() }
                    assertNumericCoercionOverflows(actualValue) { it.asByte() }
                }
                Short::class -> {
                    assertNumericCoercionOverflows(actualValue) { it.asByte() }
                }
                else -> fail("Unexpected clazz: $clazz")
            }
        }
    }

    private fun createManagedContainer(
        realmAnyInitializer: (() -> RealmAny?)? = null
    ): RealmAnyContainer = realm.writeBlocking {
        copyToRealm(
            RealmAnyContainer().apply {
                realmAnyInitializer?.let { block ->
                    this.anyField = block()
                }
            }
        )
    }

    private fun createManagedRealmAny(block: (() -> RealmAny?)? = null): RealmAny? =
        createManagedContainer(block).anyField

    /**
     * Loops through supported RealmAny types. It stores RealmAny instances containing all possible
     * values on the same object and asserts the value is updated accordingly.
     */
    private fun loopSupportedTypes(container: RealmAnyContainer) {
        fun MutableRealm.setAndAssert(expected: RealmAny, container: RealmAnyContainer) {
            val managedContainer = findLatest(container)!!
            managedContainer.anyField = expected
            val actualManaged = managedContainer.anyField
            assertNotNull(actualManaged)

            when (expected.type) {
                RealmAny.Type.OBJECT -> assertEquals(
                    expected.asRealmObject<Sample>().stringField,
                    actualManaged.asRealmObject<Sample>().stringField
                )
                else -> assertEquals(expected, actualManaged)
            }
        }

        // Test we can set a RealmAny value to a field with all supported types
        realm.writeBlocking {
            supportedRealmAnys.forEach {
                setAndAssert(it.value, container)
            }
        }
    }

    /**
     * Exhaustively checks that getting a value using the wrong 'as' function throws an exception.
     */
    private fun assertThrowsOnInvalidType(excludedType: KClassifier, value: RealmAny) {
        TypeDescriptor.anyClassifiers.keys.filter { it != excludedType }
            .forEach { candidateClass ->
                when (candidateClass) {
                    // Exclude these numerals as the underlying value is the same
                    Short::class -> if (excludedType != Short::class &&
                        excludedType != Int::class &&
                        excludedType != Byte::class &&
                        excludedType != Char::class &&
                        excludedType != Long::class
                    ) {
                        assertFailsWith<IllegalStateException> { value.asShort() }
                    }
                    // Exclude these numerals as the underlying value is the same
                    Int::class -> if (excludedType != Short::class &&
                        excludedType != Int::class &&
                        excludedType != Byte::class &&
                        excludedType != Char::class &&
                        excludedType != Long::class
                    ) {
                        assertFailsWith<IllegalStateException> { value.asInt() }
                    }
                    // Exclude these numerals as the underlying value is the same
                    Byte::class -> if (excludedType != Short::class &&
                        excludedType != Int::class &&
                        excludedType != Byte::class &&
                        excludedType != Char::class &&
                        excludedType != Long::class
                    ) {
                        assertFailsWith<IllegalStateException> { value.asByte() }
                    }
                    // Exclude these numerals as the underlying value is the same
                    Char::class -> if (excludedType != Short::class &&
                        excludedType != Int::class &&
                        excludedType != Byte::class &&
                        excludedType != Char::class &&
                        excludedType != Long::class
                    ) {
                        assertFailsWith<IllegalStateException> { value.asChar() }
                    }
                    // Exclude these numerals as the underlying value is the same
                    Long::class -> if (excludedType != Short::class &&
                        excludedType != Int::class &&
                        excludedType != Byte::class &&
                        excludedType != Char::class &&
                        excludedType != Long::class
                    ) {
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
                    Decimal128::class ->
                        assertFailsWith<IllegalStateException> { value.asDecimal128() }
                    BsonObjectId::class ->
                        assertFailsWith<IllegalStateException> { value.asObjectId() }
                    ByteArray::class ->
                        assertFailsWith<IllegalStateException> { value.asByteArray() }
                    RealmInstant::class ->
                        assertFailsWith<IllegalStateException> { value.asRealmInstant() }
                    RealmUUID::class ->
                        assertFailsWith<IllegalStateException> { value.asRealmUUID() }
                    RealmObject::class -> assertFailsWith<IllegalStateException> {
                        value.asRealmObject<Sample>()
                    }
                    else -> fail("Untested type: $candidateClass")
                }
            }
    }

    companion object {
        internal val defaultValues: Map<KClassifier, Any> = TypeDescriptor.anyClassifiers.mapValues {
            when (it.key) {
                Short::class -> -12.toShort()
                Int::class -> 13
                Byte::class -> 14.toByte()
                Char::class -> 15.toChar()
                Long::class -> 16L
                Boolean::class -> false
                String::class -> "hello"
                Float::class -> 17F
                Double::class -> 18.0
                Decimal128::class -> Decimal128("1")
                ObjectId::class -> BsonObjectId()
                ByteArray::class -> byteArrayOf(42, 43, 44)
                RealmInstant::class -> RealmInstant.now()
                RealmUUID::class -> RealmUUID.random()
                RealmObject::class -> Sample()
                else -> error("RealmAny supporting classifier does not have a default values: ${it.key}")
            }
        }
        val supportedRealmAnys: Map<KClassifier, RealmAny> = defaultValues.mapValues { (type, defaultValue: Any) ->
            create(defaultValue)
        }

        fun create(value: Any?): RealmAny = when (value) {
            is Byte -> RealmAny.create(value)
            is Char -> RealmAny.create(value)
            is Short -> RealmAny.create(value)
            is Int -> RealmAny.create(value)
            is Long -> RealmAny.create(value)
            is Boolean -> RealmAny.create(value)
            is String -> RealmAny.create(value)
            is Float -> RealmAny.create(value)
            is Double -> RealmAny.create(value)
            is BsonDecimal128 -> RealmAny.create(value)
            is BsonObjectId -> RealmAny.create(value)
            is ByteArray -> RealmAny.create(value)
            is RealmInstant -> RealmAny.create(value)
            is RealmUUID -> RealmAny.create(value)
            is RealmObject -> RealmAny.create(value, value::class as KClass<out RealmObject>)
            else -> {
                fail("Cannot create a RealmValue for value: $value of type ${value?.let { value::class }}")
            }
        }
    }
}

class RealmAnyContainer() : RealmObject {

    var anyField: RealmAny? = RealmAny.create(42.toShort())

    constructor(anyField: RealmAny?) : this() {
        this.anyField = anyField
    }
}

// This class is used to test can use an indexed RealmAny field
class IndexedRealmAnyContainer : RealmObject {
    @Index
    var anyField: RealmAny? = RealmAny.create(42.toShort())
}

class NotInSchema : RealmObject {
    var name: String? = "not in schema"
}
