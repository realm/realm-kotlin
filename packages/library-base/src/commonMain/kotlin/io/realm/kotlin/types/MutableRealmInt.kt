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
 * A `MutableRealmInt` is a mutable, [Long]-like, numeric quantity. It behaves almost exactly as a
 * reference to a [Long].
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
 */
public abstract class MutableRealmInt : Comparable<MutableRealmInt>, Number() {

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

    /**
     * `MutableRealmInt`s compare strictly by their [Long] values.
     *
     * @param other the compare target
     * @return -1, 0, or 1, depending on whether this object's value is <, =, or > the target's.
     */
    override fun compareTo(other: MutableRealmInt): Int = get().compareTo(other.get())

    override fun toByte(): Byte = get().toByte()

    override fun toChar(): Char = get().toInt().toChar()

    override fun toDouble(): Double = get().toDouble()

    override fun toFloat(): Float = get().toFloat()

    override fun toInt(): Int = get().toInt()

    override fun toLong(): Long = get()

    override fun toShort(): Short = get().toShort()

    override fun toString(): String = "RealmMutableInt{${get()}}"

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
     * TODO just for visibility, will be removed
     *
     * Long only supports plusAssign via plus so I think we should go for the same behavior, see
     * below.
     *
     * It's not possible to have plusAssign since we break the following:
     *
     * "If the corresponding binary function (that means plus() for plusAssign()) is available too,
     * a is a mutable variable, and the return type of plus is a subtype of the type of a, report an
     * error (ambiguity)."
     *
     * See https://kotlinlang.org/docs/operator-overloading.html#equality-and-inequality-operators
     *
     * Right now RealmObjects only support var fields so we can't support this operator
     */
    // public abstract operator fun plusAssign(other: Number)

    /**
     * TODO just for visibility, will be removed
     *
     * Same as plusAssign
     */
    // public abstract operator fun minusAssign(other: Number)

    public companion object {
        /**
         * Creates a new, unmanaged [MutableRealmInt] with the specified initial value.
         *
         * @param value initial value.
         */
        public fun create(value: Long): MutableRealmInt = UnmanagedMutableRealmInt(value)
    }
}
