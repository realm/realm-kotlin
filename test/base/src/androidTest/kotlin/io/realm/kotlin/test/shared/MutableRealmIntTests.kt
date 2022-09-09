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
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.internal.RealmObjectReference
import io.realm.kotlin.test.assertFailsWithMessage
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.MutableRealmInt
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MutableRealmIntTests {

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(setOf(Sample::class))
            .directory(tmpDir)
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

    @Test
    fun unmanaged_boundaries() {
        val upperBoundRealmInt = MutableRealmInt.create(Long.MAX_VALUE + 1)
        assertEquals(Long.MAX_VALUE + 1, upperBoundRealmInt.get())
        val lowerBoundRealmInt = MutableRealmInt.create(Long.MIN_VALUE - 1)
        assertEquals(Long.MIN_VALUE - 1, lowerBoundRealmInt.get())
    }

    @Test
    fun unmanaged_set() {
        val realmInt: MutableRealmInt = MutableRealmInt.create(42)
        assertEquals(42L, realmInt.get())
        realmInt.set(22.toByte())
        assertEquals(22L, realmInt.get())
        realmInt.set(22.toDouble())
        assertEquals(22L, realmInt.get())
        realmInt.set(22.toFloat())
        assertEquals(22L, realmInt.get())
        realmInt.set(22)
        assertEquals(22L, realmInt.get())
        realmInt.set(22.toLong())
        assertEquals(22L, realmInt.get())
        realmInt.set(22.toShort())
        assertEquals(22L, realmInt.get())
    }

    @Test
    fun unmanaged_increment() {
        val realmInt = MutableRealmInt.create(42)

        realmInt.increment(1.toByte())
        assertEquals(43L, realmInt.get())
        realmInt.increment(1.toDouble())
        assertEquals(44L, realmInt.get())
        realmInt.increment(1.toFloat())
        assertEquals(45L, realmInt.get())
        realmInt.increment(1)
        assertEquals(46L, realmInt.get())
        realmInt.increment(1.toLong())
        assertEquals(47L, realmInt.get())
        realmInt.increment(1.toShort())
        assertEquals(48L, realmInt.get())
        realmInt.increment(-1)
        assertEquals(47L, realmInt.get())
    }

    @Test
    fun unmanaged_decrement() {
        val realmInt = MutableRealmInt.create(42)

        realmInt.decrement(1.toByte())
        assertEquals(41L, realmInt.get())
        realmInt.decrement(1.toDouble())
        assertEquals(40L, realmInt.get())
        realmInt.decrement(1.toFloat())
        assertEquals(39L, realmInt.get())
        realmInt.decrement(1)
        assertEquals(38L, realmInt.get())
        realmInt.decrement(1.toLong())
        assertEquals(37L, realmInt.get())
        realmInt.decrement(1.toShort())
        assertEquals(36L, realmInt.get())
        realmInt.decrement(-1)
        assertEquals(37L, realmInt.get())
    }

    @Test
    fun unmanaged_equality() {
        val sample1 = Sample()
        val sample2 = Sample()
        equalityTest(sample1, sample2)
    }

    @Test
    fun unmanaged_nullability() {
        nullabilityTest(Sample())
    }

    @Test
    fun unmanaged_compareTo() {
        val r1 = MutableRealmInt.create(0)
        val r2 = MutableRealmInt.create(Long.MAX_VALUE)

        assertEquals(-1, r1.compareTo(r2))
        assertTrue(r1 < r2)
        assertTrue(r2 > r1)

        r2.decrement(Long.MAX_VALUE)
        assertEquals(0, r1.compareTo(r2))
        assertEquals(r1, r2)
        assertEquals(r2, r1)

        r2.decrement(Long.MAX_VALUE)
        assertEquals(1, r1.compareTo(r2))
        assertTrue(r1 > r2)
        assertTrue(r2 < r1)
    }

    @Test
    fun unmanaged_shareValueAcrossInstances() {
        val counter = MutableRealmInt.create(42)
        val foo = Sample()
        val bar = Sample()

        foo.mutableRealmIntField = counter
        bar.mutableRealmIntField = foo.mutableRealmIntField
        bar.mutableRealmIntField.increment(1)
        val fooValue = foo.mutableRealmIntField.get()
        val barValue = bar.mutableRealmIntField.get()

        assertEquals(fooValue, barValue)
    }

    @Test
    fun unmanaged_plusOperator() {
        val value = 1L
        val expectedResult: Long = value + value
        val result = MutableRealmInt.create(value) + MutableRealmInt.create(value)
        assertEquals(expectedResult, result.get())
    }

    @Test
    fun unmanaged_minusOperator() {
        val valueA = 4L
        val valueB = 2L
        val expectedResult: Long = valueA - valueB
        val result = MutableRealmInt.create(valueA) - MutableRealmInt.create(valueB)
        assertEquals(expectedResult, result.get())
    }

    @Test
    fun unmanaged_timesOperator() {
        val valueA = 4L
        val valueB = 2L
        val expectedResult: Long = valueA * valueB
        val result = MutableRealmInt.create(valueA) * MutableRealmInt.create(valueB)
        assertEquals(expectedResult, result.get())
    }

    @Test
    fun unmanaged_divOperator() {
        val valueA = 4L
        val valueB = 2L
        val expectedResult: Long = valueA / valueB
        val result = MutableRealmInt.create(valueA) / MutableRealmInt.create(valueB)
        assertEquals(expectedResult, result.get())
    }

    @Test
    fun unmanaged_remOperator() {
        val valueA = 4L
        val valueB = 2L
        val expectedResult: Long = valueA % valueB
        val result = MutableRealmInt.create(valueA) % MutableRealmInt.create(valueB)
        assertEquals(expectedResult, result.get())
    }

    @Test
    fun unmanaged_incOperator() {
        val value = 4L
        val expectedResult = value.inc() // Use function instead of '++' to avoid modifying the original value used for fidelity
        val result = MutableRealmInt.create(value).inc()
        assertEquals(expectedResult, result.get())
    }

    @Test
    fun unmanaged_decOperator() {
        val value = 4L
        val expectedResult = value.dec() // Use function instead of '--' to avoid modifying the original value used for fidelity
        val result = MutableRealmInt.create(value).dec()
        assertEquals(expectedResult, result.get())
    }

    @Test
    fun managed_boundaries() {
        realm.writeBlocking {
            val upperBoundRealmInt = copyToRealm(
                Sample().apply { mutableRealmIntField = MutableRealmInt.create(Long.MAX_VALUE) }
            ).mutableRealmIntField
            assertEquals(Long.MAX_VALUE, upperBoundRealmInt.get())
            upperBoundRealmInt.increment(1)
            assertEquals(Long.MAX_VALUE + 1, upperBoundRealmInt.get())

            val lowerBoundRealmInt = copyToRealm(
                Sample().apply { mutableRealmIntField = MutableRealmInt.create(Long.MIN_VALUE) }
            ).mutableRealmIntField
            assertEquals(Long.MIN_VALUE, lowerBoundRealmInt.get())
            lowerBoundRealmInt.decrement(1)
            assertEquals(Long.MIN_VALUE - 1, lowerBoundRealmInt.get())
        }
    }

    @Test
    fun managed_set() {
        realm.writeBlocking {
            val realmInt = copyToRealm(Sample()).mutableRealmIntField
            realmInt.set(22.toByte())
            assertEquals(22L, realmInt.get())
            realmInt.set(22.toDouble())
            assertEquals(22L, realmInt.get())
            realmInt.set(22.toFloat())
            assertEquals(22L, realmInt.get())
            realmInt.set(22)
            assertEquals(22L, realmInt.get())
            realmInt.set(22.toLong())
            assertEquals(22L, realmInt.get())
            realmInt.set(22.toShort())
            assertEquals(22L, realmInt.get())
        }
    }

    @Test
    fun managed_increment() {
        realm.writeBlocking {
            val realmInt = copyToRealm(Sample()).mutableRealmIntField
            realmInt.set(42)

            realmInt.increment(1.toByte())
            assertEquals(43L, realmInt.get())
            realmInt.increment(1.toDouble())
            assertEquals(44L, realmInt.get())
            realmInt.increment(1.toFloat())
            assertEquals(45L, realmInt.get())
            realmInt.increment(1)
            assertEquals(46L, realmInt.get())
            realmInt.increment(1.toLong())
            assertEquals(47L, realmInt.get())
            realmInt.increment(1.toShort())
            assertEquals(48L, realmInt.get())
            realmInt.increment(-1)
            assertEquals(47L, realmInt.get())
        }
    }

    @Test
    fun managed_decrement() {
        realm.writeBlocking {
            val realmInt = copyToRealm(Sample()).mutableRealmIntField
            realmInt.set(42)

            realmInt.decrement(1.toByte())
            assertEquals(41L, realmInt.get())
            realmInt.decrement(1.toDouble())
            assertEquals(40L, realmInt.get())
            realmInt.decrement(1.toFloat())
            assertEquals(39L, realmInt.get())
            realmInt.decrement(1)
            assertEquals(38L, realmInt.get())
            realmInt.decrement(1.toLong())
            assertEquals(37L, realmInt.get())
            realmInt.decrement(1.toShort())
            assertEquals(36L, realmInt.get())
            realmInt.decrement(-1)
            assertEquals(37L, realmInt.get())
        }
    }

    @Test
    fun managed_equality() {
        realm.writeBlocking {
            val c1 = copyToRealm(Sample())
            val c2 = copyToRealm(Sample())
            equalityTest(c1, c2)
        }
    }

    @Test
    fun managed_nullability() {
        realm.writeBlocking {
            val c1 = copyToRealm(Sample())
            nullabilityTest(c1)
        }
    }

    @Test
    fun managed_compareTo() {
        realm.writeBlocking {
            val c1 = copyToRealm(Sample())
            val r1 = c1.mutableRealmIntField
            r1.set(0)

            val c2 = copyToRealm(Sample())
            val r2 = c2.mutableRealmIntField
            r2.set(Long.MAX_VALUE)
            assertEquals(-1, r1.compareTo(r2))

            r2.decrement(Long.MAX_VALUE)
            assertEquals(0, r1.compareTo(r2))
            r2.decrement(Long.MAX_VALUE)
            assertEquals(1, r1.compareTo(r2))
        }
    }

    @Test
    fun managed_setOutsideTransactionThrows() {
        val r = realm.writeBlocking {
            copyToRealm(Sample())
        }.mutableRealmIntField

        assertFailsWithMessage<IllegalStateException>("Cannot set") {
            r.set(22)
        }
    }

    @Test
    fun managed_incrementOutsideTransactionThrows() {
        val r = realm.writeBlocking {
            copyToRealm(Sample())
        }.mutableRealmIntField

        assertFailsWithMessage<IllegalStateException>("Cannot increment/decrement") {
            r.increment(1)
        }
        assertFailsWithMessage<IllegalStateException>("Cannot increment/decrement") {
            r.decrement(1)
        }
    }

    @Test
    fun managed_accessors() {
        val r = MutableRealmInt.create(22)
        realm.writeBlocking {
            val sample = copyToRealm(Sample())
            assertNotNull(sample.mutableRealmIntField)
            sample.mutableRealmIntField = r
            val managedMutableInt = sample.mutableRealmIntField
            assertNotNull(managedMutableInt)
            assertEquals(22, managedMutableInt.get())

            assertNull(sample.nullableMutableRealmIntField)
            sample.nullableMutableRealmIntField = r
            val managedNullableMutableRealmInt = sample.nullableMutableRealmIntField
            assertNotNull(managedNullableMutableRealmInt)
            assertEquals(22, managedNullableMutableRealmInt.get())
        }
    }

    @Test
    fun managed_shareValueAcrossInstances() {
        realm.writeBlocking {
            val counter = MutableRealmInt.create(42)
            val managedFoo = copyToRealm(Sample())
            val managedBar = copyToRealm(Sample())

            managedFoo.mutableRealmIntField = counter
            managedBar.mutableRealmIntField = managedFoo.mutableRealmIntField
            managedBar.mutableRealmIntField.increment(1)
            val managedFooValue = managedFoo.mutableRealmIntField.get()
            val managedBarValue = managedBar.mutableRealmIntField.get()

            // Values obviously diverge since we don't copy the reference but the value, just as we
            // do with any other primitive datatype for managed objects
            assertEquals(42, managedFooValue)
            assertEquals(43, managedBarValue)
        }
    }

    @Test
    fun managed_deleteParentObjectInvalidatesInstance() {
        realm.writeBlocking {
            val managedSample = copyToRealm(Sample())
            val mutableInt = assertNotNull(managedSample.mutableRealmIntField)
            assertEquals(42, mutableInt.get())

            delete(managedSample)

            assertFailsWithMessage<IllegalStateException>(RealmObjectReference.INVALID_OBJECT_MSG) {
                mutableInt.get()
            }
            assertFailsWithMessage<IllegalStateException>(RealmObjectReference.INVALID_OBJECT_MSG) {
                mutableInt.set(22)
            }
            assertFailsWithMessage<IllegalStateException>(RealmObjectReference.INVALID_OBJECT_MSG) {
                mutableInt.increment(1)
            }
            assertFailsWithMessage<IllegalStateException>(RealmObjectReference.INVALID_OBJECT_MSG) {
                mutableInt.decrement(1)
            }
        }
    }

    @Test
    fun managed_plusOperator() {
        val value = 1L
        val expectedResult: Long = value + value
        realm.writeBlocking {
            val realmIntA = copyToRealm(Sample()).mutableRealmIntField.also { it.set(value) }
            val realmIntB = copyToRealm(Sample()).mutableRealmIntField.also { it.set(value) }
            val result = realmIntA + realmIntB
            assertEquals(expectedResult, result.get())
        }
    }

    @Test
    fun managed_minusOperator() {
        val valueA = 4L
        val valueB = 2L
        val expectedResult: Long = valueA - valueB
        realm.writeBlocking {
            val realmIntA = copyToRealm(Sample()).mutableRealmIntField.also { it.set(valueA) }
            val realmIntB = copyToRealm(Sample()).mutableRealmIntField.also { it.set(valueB) }
            val result = realmIntA - realmIntB
            assertEquals(expectedResult, result.get())
        }
    }

    @Test
    fun managed_timesOperator() {
        val valueA = 4L
        val valueB = 2L
        val expectedResult: Long = valueA * valueB
        realm.writeBlocking {
            val realmIntA = copyToRealm(Sample()).mutableRealmIntField.also { it.set(valueA) }
            val realmIntB = copyToRealm(Sample()).mutableRealmIntField.also { it.set(valueB) }
            val result = realmIntA * realmIntB
            assertEquals(expectedResult, result.get())
        }
    }

    @Test
    fun managed_divOperator() {
        val valueA = 4L
        val valueB = 2L
        val expectedResult: Long = valueA / valueB
        realm.writeBlocking {
            val realmIntA = copyToRealm(Sample()).mutableRealmIntField.also { it.set(valueA) }
            val realmIntB = copyToRealm(Sample()).mutableRealmIntField.also { it.set(valueB) }
            val result = realmIntA / realmIntB
            assertEquals(expectedResult, result.get())
        }
    }

    @Test
    fun managed_remOperator() {
        val valueA = 4L
        val valueB = 2L
        val expectedResult: Long = valueA % valueB
        realm.writeBlocking {
            val realmIntA = copyToRealm(Sample()).mutableRealmIntField.also { it.set(valueA) }
            val realmIntB = copyToRealm(Sample()).mutableRealmIntField.also { it.set(valueB) }
            val result = realmIntA % realmIntB
            assertEquals(expectedResult, result.get())
        }
    }

    @Test
    fun managed_incOperator() {
        val value = 4L
        val expectedResult = value.inc() // Use function instead of '++' to avoid modifying the original value used for fidelity
        realm.writeBlocking {
            val realmIntA = copyToRealm(Sample()).mutableRealmIntField.also { it.set(value) }
            val result = realmIntA.inc()
            assertEquals(expectedResult, result.get())
        }
    }

    @Test
    fun managed_decOperator() {
        val value = 4L
        val expectedResult = value.dec() // Use function instead of '--' to avoid modifying the original value used for fidelity
        realm.writeBlocking {
            val realmIntA = copyToRealm(Sample()).mutableRealmIntField.also { it.set(value) }
            val result = realmIntA.dec()
            assertEquals(expectedResult, result.get())
        }
    }

    private fun equalityTest(c1: Sample, c2: Sample) {
        assertNotSame(c1, c2)
        c1.mutableRealmIntField.set(7)
        c2.mutableRealmIntField.set(7)
        assertTrue(c1.mutableRealmIntField !== c2.mutableRealmIntField)
        assertEquals(c1.mutableRealmIntField, c2.mutableRealmIntField)

        val r1 = c1.mutableRealmIntField
        r1.increment(1)
        assertEquals(r1, c1.mutableRealmIntField)
        assertTrue(assertNotNull(c1.mutableRealmIntField.get()) == 8L)
        assertNotEquals(assertNotNull(c1.mutableRealmIntField.get()), c2.mutableRealmIntField.get())
        assertTrue(c1.mutableRealmIntField.get() == 8L)

        val n = c1.mutableRealmIntField.get()
        assertNotNull(n)
        assertTrue(n == 8L)
        assertEquals(n, c1.mutableRealmIntField.get())
        assertTrue(n == c1.mutableRealmIntField.get())
        c1.mutableRealmIntField.increment(1)
        assertNotEquals(n, c1.mutableRealmIntField.get())
        assertFalse(n == c1.mutableRealmIntField.get())
        assertNotEquals(n, r1.get())

        // Assertions for nullable fields
        assertNull(c1.nullableMutableRealmIntField)
        assertNull(c2.nullableMutableRealmIntField)
        assertEquals(c1.nullableMutableRealmIntField, c2.nullableMutableRealmIntField)
    }

    private fun nullabilityTest(c1: Sample) {
        assertNull(c1.nullableMutableRealmIntField)
        c1.nullableMutableRealmIntField = MutableRealmInt.create(0L)
        assertNotNull(c1.nullableMutableRealmIntField)
        c1.nullableMutableRealmIntField = null
        assertNull(c1.nullableMutableRealmIntField)
    }
}
