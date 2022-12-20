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

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.entities.embedded.EmbeddedChild
import io.realm.kotlin.entities.embedded.EmbeddedParent
import io.realm.kotlin.entities.embedded.embeddedSchema
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.notifications.DeletedObject
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.notifications.UpdatedObject
import io.realm.kotlin.query.find
import io.realm.kotlin.test.assertFailsWithMessage
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.use
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import io.realm.kotlin.types.annotations.Index
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
import kotlin.time.Duration.Companion.seconds

@Suppress("LargeClass")
class RealmAnyTests {

    private lateinit var configBuilder: RealmConfiguration.Builder
    private lateinit var configuration: RealmConfiguration
    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    private val supportedTypesAndValues = listOf(
        Short::class to 10.toShort(),
        Int::class to 10,
        Byte::class to 10.toByte(),
        Char::class to 10.toChar(),
        Long::class to 10L,
        Boolean::class to true,
        String::class to "hello",
        Float::class to 10F,
        Double::class to 10.0,
        BsonObjectId::class to BsonObjectId(),
        ByteArray::class to byteArrayOf(42, 43, 44),
        RealmInstant::class to RealmInstant.now(),
        RealmUUID::class to RealmUUID.random(),
        Sample::class to Sample()
    )

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
        for (type in supportedTypesAndValues) {
            when (type.first) {
                Short::class ->
                    assertThrowsOnInvalidType(Short::class, RealmAny.create(type.second as Short))
                Int::class ->
                    assertThrowsOnInvalidType(Int::class, RealmAny.create(type.second as Int))
                Byte::class ->
                    assertThrowsOnInvalidType(Byte::class, RealmAny.create(type.second as Byte))
                Char::class ->
                    assertThrowsOnInvalidType(Char::class, RealmAny.create(type.second as Char))
                Long::class ->
                    assertThrowsOnInvalidType(Long::class, RealmAny.create(type.second as Long))
                Boolean::class -> assertThrowsOnInvalidType(
                    Boolean::class,
                    RealmAny.create(type.second as Boolean)
                )
                String::class ->
                    assertThrowsOnInvalidType(String::class, RealmAny.create(type.second as String))
                Float::class ->
                    assertThrowsOnInvalidType(Float::class, RealmAny.create(type.second as Float))
                Double::class ->
                    assertThrowsOnInvalidType(Double::class, RealmAny.create(type.second as Double))
                BsonObjectId::class -> assertThrowsOnInvalidType(
                    BsonObjectId::class,
                    RealmAny.create(type.second as BsonObjectId)
                )
                ByteArray::class -> assertThrowsOnInvalidType(
                    ByteArray::class,
                    RealmAny.create(type.second as ByteArray)
                )
                RealmInstant::class -> assertThrowsOnInvalidType(
                    RealmInstant::class,
                    RealmAny.create(type.second as RealmInstant)
                )
                RealmUUID::class -> assertThrowsOnInvalidType(
                    RealmUUID::class,
                    RealmAny.create(type.second as RealmUUID)
                )
                Sample::class -> assertThrowsOnInvalidType(
                    Sample::class,
                    RealmAny.create(type.second as Sample, Sample::class)
                )
            }
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
        for (type in supportedTypesAndValues) {
            when (type.first) {
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
                Sample::class -> {
                    val obj = Sample()
                    val realmAny = RealmAny.create(obj, Sample::class)
                    assertEquals(obj, realmAny.asRealmObject())
                    assertEquals(RealmAny.create(obj, Sample::class), realmAny)
                    assertEquals(RealmAny.Type.OBJECT, realmAny.type)
                }
                else -> throw UnsupportedOperationException("Missing testing for type $type")
            }
        }
    }

    @Test
    fun managed_incorrectTypeThrows() {
        for (type in supportedTypesAndValues) {
            when (type.first) {
                Short::class -> assertThrowsOnInvalidType(
                    Short::class,
                    createManagedRealmAny { RealmAny.create(10.toShort()) }!!
                )
                Int::class -> assertThrowsOnInvalidType(
                    Int::class,
                    createManagedRealmAny { RealmAny.create(10) }!!
                )
                Byte::class -> assertThrowsOnInvalidType(
                    Byte::class,
                    createManagedRealmAny { RealmAny.create(10.toByte()) }!!
                )
                Char::class -> assertThrowsOnInvalidType(
                    Char::class,
                    createManagedRealmAny { RealmAny.create(10.toChar()) }!!
                )
                Long::class -> assertThrowsOnInvalidType(
                    Long::class,
                    createManagedRealmAny { RealmAny.create(10L) }!!
                )
                Boolean::class -> assertThrowsOnInvalidType(
                    Boolean::class,
                    createManagedRealmAny { RealmAny.create(true) }!!
                )
                String::class -> assertThrowsOnInvalidType(
                    String::class,
                    createManagedRealmAny { RealmAny.create("hello") }!!
                )
                Float::class -> assertThrowsOnInvalidType(
                    Float::class,
                    createManagedRealmAny { RealmAny.create(10F) }!!
                )
                Double::class -> assertThrowsOnInvalidType(
                    Double::class,
                    createManagedRealmAny { RealmAny.create(10.0) }!!
                )
                BsonObjectId::class -> assertThrowsOnInvalidType(
                    BsonObjectId::class,
                    createManagedRealmAny { RealmAny.create(BsonObjectId()) }!!
                )
                ByteArray::class -> assertThrowsOnInvalidType(
                    ByteArray::class,
                    createManagedRealmAny { RealmAny.create(byteArrayOf(42, 43, 44)) }!!
                )
                RealmInstant::class -> assertThrowsOnInvalidType(
                    RealmInstant::class,
                    createManagedRealmAny { RealmAny.create(RealmInstant.now()) }!!
                )
                RealmUUID::class -> assertThrowsOnInvalidType(
                    RealmUUID::class,
                    createManagedRealmAny { RealmAny.create(RealmUUID.random()) }!!
                )
                Sample::class -> assertThrowsOnInvalidType(
                    Sample::class,
                    createManagedRealmAny { RealmAny.create(Sample(), Sample::class) }!!
                )
            }
        }
    }

    @Test
    fun managed_updateThroughAllTypes() {
        // For each supported type make sure we can set a RealmAny value with all different types
        for (valueType in supportedTypesAndValues) {
            when (valueType.first) {
                Short::class -> loopSupportedTypes(createManagedContainer())
                Int::class -> loopSupportedTypes(createManagedContainer())
                Byte::class -> loopSupportedTypes(createManagedContainer())
                Char::class -> loopSupportedTypes(createManagedContainer())
                Long::class -> loopSupportedTypes(createManagedContainer())
                Boolean::class -> loopSupportedTypes(createManagedContainer())
                String::class -> loopSupportedTypes(createManagedContainer())
                Float::class -> loopSupportedTypes(createManagedContainer())
                Double::class -> loopSupportedTypes(createManagedContainer())
                BsonObjectId::class -> loopSupportedTypes(createManagedContainer())
                ByteArray::class -> loopSupportedTypes(createManagedContainer())
                RealmInstant::class -> loopSupportedTypes(createManagedContainer())
                RealmUUID::class -> loopSupportedTypes(createManagedContainer())
                Sample::class -> loopSupportedTypes(createManagedContainer())
            }
        }
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
            val sampleChannel = Channel<SingleQueryChange<Sample>>(1)
            val containerChannel = Channel<SingleQueryChange<RealmAnyContainer>>(1)

            val sampleObserver = async {
                realm.query<Sample>()
                    .first()
                    .asFlow()
                    .collect {
                        if (it is DeletedObject<Sample>) {
                            sampleChannel.trySend(it)
                        }
                    }
            }
            val containerObserver = async {
                realm.query<RealmAnyContainer>()
                    .first()
                    .asFlow()
                    .collect {
                        if (it is UpdatedObject<RealmAnyContainer>) {
                            containerChannel.trySend(it)
                        }
                    }
            }

            val deletionJob = async {
                delay(1.seconds)
                realm.writeBlocking {
                    delete(query<Sample>())
                }
            }

            val unmanagedContainer = RealmAnyContainer(RealmAny.create(Sample()))
            realm.writeBlocking {
                copyToRealm(unmanagedContainer)
            }

            val deletedObjectChange = sampleChannel.receive()
            val updatedContainerChange = containerChannel.receive()
            assertNull(deletedObjectChange.obj)
            assertNull(assertNotNull(updatedContainerChange.obj).anyField)

            sampleObserver.cancel()
            containerObserver.cancel()
            deletionJob.cancel()
            sampleChannel.close()
            containerChannel.close()
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
            supportedTypesAndValues.forEach { candidate ->
                when (candidate.first) {
                    Int::class -> setAndAssert(RealmAny.create(candidate.second as Int), container)
                    Byte::class ->
                        setAndAssert(RealmAny.create(candidate.second as Byte), container)
                    Char::class ->
                        setAndAssert(RealmAny.create(candidate.second as Char), container)
                    Long::class ->
                        setAndAssert(RealmAny.create(candidate.second as Long), container)
                    Boolean::class ->
                        setAndAssert(RealmAny.create(candidate.second as Boolean), container)
                    String::class ->
                        setAndAssert(RealmAny.create(candidate.second as String), container)
                    Float::class ->
                        setAndAssert(RealmAny.create(candidate.second as Float), container)
                    Double::class ->
                        setAndAssert(RealmAny.create(candidate.second as Double), container)
                    BsonObjectId::class ->
                        setAndAssert(RealmAny.create(candidate.second as BsonObjectId), container)
                    ByteArray::class ->
                        setAndAssert(RealmAny.create(candidate.second as ByteArray), container)
                    RealmInstant::class ->
                        setAndAssert(RealmAny.create(candidate.second as RealmInstant), container)
                    RealmUUID::class ->
                        setAndAssert(RealmAny.create(candidate.second as RealmUUID), container)
                    Sample::class -> setAndAssert(
                        RealmAny.create(candidate.second as Sample, Sample::class),
                        container
                    )
                }
            }
        }
    }

    /**
     * Exhaustively checks that getting a value using the wrong 'as' function throws an exception.
     */
    private fun assertThrowsOnInvalidType(excludedType: KClass<*>, value: RealmAny) {
        supportedTypesAndValues.filter { it.first != excludedType }
            .forEach { candidateClass ->
                when (candidateClass.first) {
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
                    BsonObjectId::class ->
                        assertFailsWith<IllegalStateException> { value.asObjectId() }
                    ByteArray::class ->
                        assertFailsWith<IllegalStateException> { value.asByteArray() }
                    RealmInstant::class ->
                        assertFailsWith<IllegalStateException> { value.asRealmInstant() }
                    RealmUUID::class ->
                        assertFailsWith<IllegalStateException> { value.asRealmUUID() }
                    Sample::class -> assertFailsWith<IllegalStateException> {
                        value.asRealmObject<Sample>()
                    }
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
