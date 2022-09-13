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

package io.realm.kotlin.types

import io.realm.kotlin.internal.UnmanagedMutableRealmInt

/**
 * A `MutableRealmInt` is a mutable, [Long]-like, numeric quantity.
 *
 * `MutableRealmInt`s are most interesting as members of a managed [RealmObject] object in a
 * synchronized Realm. When managed, the [increment] and [decrement] operators implement a
 * [conflict-free replicated data type](https://en.wikipedia.org/wiki/Conflict-free_replicated_data_type):
 * simultaneous increments and decrements from multiple distributed clients will be aggregated
 * correctly. For instance, if the value of a `counter` field for the object representing a `User`
 * named Fred is currently 0, then the following code, executed on two different devices,
 * simultaneously, even if connected by a slow, unreliable network, will **always** cause the value
 * of `counter` to eventually converge on the value 2.
 * ```
 * realm.write {
 *      query<User>("name = $0", "Fred")
 *          .first()
 *          .find()!!
 *          .counter
 *          .increment(1)
 * }
 * ```
 * Note that the [set] operator must be used with care. It will quash the effects of any prior calls
 * to [increment] or [decrement]. Although the value of a `MutableRealmInt` will always converge
 * across devices, the specific value on which it converges will depend on the actual order in which
 * operations took place. Mixing [set] with [increment] and/or [decrement] is, therefore, not
 * advised, unless fuzzy counting is acceptable.
 *
 * `MutableRealmInt`s **cannot be primary keys**.
 *
 * `MutableRealmInt`s cannot store `null` values. However, it is possible to declare nullable
 * `MutableRealmInt` class members:
 * ```
 * var counter: MutableRealmInteger? = null
 * ```
 *
 * A reference to a managed `MutableRealmInt` is subject to all of the constraints that apply to the
 * model object from which it was obtained: It can only be mutated within a transaction and it
 * becomes invalid if the Realm backing it is closed. Note that a reference to a managed
 * `MutableRealmInt` retains a reference to the model object to which it belongs. For example in
 * this code:
 * ```
 * val counter: MutableRealmInt = realm.query<User>("user = $0", "Fred")
 *      .first()
 *      .find()!!
 *      .counter
 * ```
 * the `counter` holds a reference to the `User` model object from which it was obtained. Neither
 * can be GCed until all references to both are unreachable.
 *
 * It is worth noting that sharing `MutableRealmInt`s across `RealmObject`s results in different
 * behaviors depending on whether the objects are managed. For example, in this code `userA`
 * and `userB` are *unmanaged* instances:
 * ```
 * val userA: User = ... // userA.counter = 42
 * val userB: User = ... // userB.counter = null
 * userB.counter = userA.counter // both now point to the same reference
 * userA.counter.increment()
 * println(userA.counter.get()) // 43
 * println(userB.counter.get()) // 43
 * ```
 * The assignment is done by reference as expected. However, on managed objects it is done by value
 * as is the case for all other Realm primitive types. This means that the last two lines in the
 * code above will yield a different result in case `userA` and `userB` are managed objects:
 * ```
 * managedUserA.counter.increment()
 * println(managedUserA.counter.get()) // 43
 * println(managedUserB.counter.get()) // 42
 * ```
 *
 * In addition to the API functions, `MutableRealmInt` is a subclass of [Number]. This allows users
 * to convert the boxed values stored in the instance to other numeric types. Moreover, the class
 * provides a set of operators and infix functions similar to the ones provided by [Long]:
 * - Unary prefix operators: [unaryPlus], [unaryMinus]
 * - Increment and decrement operators: [inc], [dec]
 * - Arithmetic operators: [plus], [minus], [times], [div], [rem]
 * - Equality operators: [equals]
 * - Comparison operators: [compareTo]
 * - Bitwise functions: [shl], [shr], [ushr], [and], [or], [xor], [inv]
 *
 * Both binary operators and logic bitwise functions enforce conversion of the received value to
 * `Long` for convenience so precision loss may occur when computing the result depending on the
 * type of the received [Number]. Additionally, all these operators and infix functions **do not
 * mutate the instance on which they are executed**. For example, calling `counter.inc()` will not
 * modify `counter` but rather create a new `MutableRealmInt` with the updated value. **The only
 * operations that result in a mutated value are [set], [increment] and [decrement]**.
 */
public abstract class MutableRealmInt : Number() {

    /**
     * Gets the `MutableRealmInt` value.
     *
     * @return the value.
     */
    public abstract fun get(): Long

    /**
     * Sets the `MutableRealmInt` value.
     *
     * Calling [set] forcibly sets the `MutableRealmInt` to the provided value. Doing this removes
     * the effects of any calls to [increment] and [decrement] received before the call to `set`.
     *
     * @param value new value.
     */
    public abstract fun set(value: Number)

    /**
     * Increments the `MutableRealmInt`, adding the value of the argument. Increment/decrement
     * operations from all devices are reflected in the new value, which is guaranteed to converge.
     * This operation should not be confused with the [inc] operator, which returns a new value
     * whereas this one modifies the value itself.
     *
     * @param value quantity to be added to the `MutableRealmInt`.
     */
    public abstract fun increment(value: Number)

    /**
     * Decrements the `MutableRealmInt`, subtracting the value of the argument. Increment/decrement
     * operations from all devices are reflected in the new value, which is guaranteed to converge.
     * This operation should not be confused with the [dec] operator, which returns a new value
     * whereas this one modifies the value itself.
     *
     * @param value quantity to be subtracted from the `MutableRealmInt`.
     */
    public abstract fun decrement(value: Number)

    /**
     * Two `MutableRealmInt`s are equal if and only if their [Long] values are equal.
     *
     * @param other compare target
     * @return `true` if the target has the same value.
     */
    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null) return false
        if (other !is MutableRealmInt) return false

        val thisValue = get()
        val otherValue = other.get()
        return thisValue == otherValue
    }

    /**
     * A `MutableRealmInt`'s hash code is, exactly, the hash code of its value.
     *
     * @return the hash code of its value
     */
    override fun hashCode(): Int = get().hashCode()

    override fun toByte(): Byte = get().toByte()

    override fun toChar(): Char = get().toInt().toChar()

    override fun toDouble(): Double = get().toDouble()

    override fun toFloat(): Float = get().toFloat()

    override fun toInt(): Int = get().toInt()

    override fun toLong(): Long = get()

    override fun toShort(): Short = get().toShort()

    override fun toString(): String = "RealmMutableInt{${get()}}"

    /**
     * `MutableRealmInt`s compare strictly by their [Long] values.
     *
     * @param other the compare target
     * @return -1, 0, or 1, depending on whether this object's value is <, =, or > the target's.
     */
    public operator fun compareTo(other: MutableRealmInt): Int = get().compareTo(other.get())

    /**
     * Adds the other value to this value.
     * The other value will be converted to [Long] before applying the operator.
     */
    public operator fun plus(other: Number): MutableRealmInt = create(get() + other.toLong())

    /**
     * Subtracts the other value from this value.
     * The other value will be converted to [Long] before applying the operator.
     */
    public operator fun minus(other: Number): MutableRealmInt = create(get() - other.toLong())

    /**
     * Multiplies this value by the other value.
     * The other value will be converted to [Long] before applying the operator.
     */
    public operator fun times(other: Number): MutableRealmInt = create(get() * other.toLong())

    /**
     * Divides this value by the other value, truncating the result to an integer that is closer to
     * zero.
     * The other value will be converted to [Long] before applying the operator.
     */
    public operator fun div(other: Number): MutableRealmInt = create(get() / other.toLong())

    /**
     * Calculates the remainder of truncating division of this value by the other value.
     * The result is either zero or has the same sign as the dividend and has the absolute value
     * less than the absolute value of the divisor.
     * The other value will be converted to [Long] before applying the operator.
     */
    public operator fun rem(other: Number): MutableRealmInt = create(get() % other.toLong())

    /**
     * Returns this value incremented by one.
     */
    public operator fun inc(): MutableRealmInt = create(get() + 1)

    /**
     * Returns this value decremented by one.
     */
    public operator fun dec(): MutableRealmInt = create(get() - 1)

    /**
     * Returns this value.
     */
    public operator fun unaryPlus(): MutableRealmInt = create(get().unaryPlus())

    /**
     * Returns the negative of this value.
     */
    public operator fun unaryMinus(): MutableRealmInt = create(get().unaryMinus())

    /**
     * Shifts this value left by the [bitCount] number of bits.
     *
     * Note that only the six lowest-order bits of the [bitCount] are used as the shift distance.
     * The shift distance actually used is therefore always in the range `0..63`.
     */
    public infix fun shl(bitCount: Int): MutableRealmInt = create(get().shl(bitCount))

    /**
     * Shifts this value right by the [bitCount] number of bits, filling the leftmost bits with
     * copies of the sign bit.
     *
     * Note that only the six lowest-order bits of the [bitCount] are used as the shift distance.
     * The shift distance actually used is therefore always in the range `0..63`.
     */
    public infix fun shr(bitCount: Int): MutableRealmInt = create(get().shr(bitCount))

    /**
     * Shifts this value right by the [bitCount] number of bits, filling the leftmost bits with
     * zeros.
     *
     * Note that only the six lowest-order bits of the [bitCount] are used as the shift distance.
     * The shift distance actually used is therefore always in the range `0..63`.
     */
    public infix fun ushr(bitCount: Int): MutableRealmInt = create(get().ushr(bitCount))

    /**
     * Performs a bitwise AND operation between the two values.
     */
    public infix fun and(other: Number): MutableRealmInt = create(get().and(other.toLong()))

    /**
     * Performs a bitwise OR operation between the two values.
     */
    public infix fun or(other: Number): MutableRealmInt = create(get().or(other.toLong()))

    /**
     * Performs a bitwise XOR operation between the two values.
     */
    public infix fun xor(other: Number): MutableRealmInt = create(get().xor(other.toLong()))

    /**
     * Inverts the bits in this value.
     */
    public fun inv(): MutableRealmInt = create(get().inv())

    public companion object {
        /**
         * Creates a new, unmanaged [MutableRealmInt] with the specified initial value.
         *
         * @param value initial value.
         */
        public fun create(value: Long): MutableRealmInt = UnmanagedMutableRealmInt(value)
    }
}
