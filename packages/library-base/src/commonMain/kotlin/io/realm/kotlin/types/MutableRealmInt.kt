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
 * `MutableRealmInt`s are most interesting as members of a managed [RealmObject] object. When
 * managed, the [increment] and [decrement] operators implement a
 * [conflict-free replicated data type](https://en.wikipedia.org/wiki/Conflict-free_replicated_data_type):
 * simultaneous increments and decrements from multiple distributed clients will be aggregated
 * correctly. For instance, if the value of a `counter` field for the object representing a `User`
 * named Fred is currently 0, then the following code, executed on two different devices,
 * simultaneously, even if connected by only a slow, unreliable network, will **always** cause the
 * value of `counter` to eventually converge on the value 2.
 * ```
 * realm.query<Sample>("name = $0", "Fred")
 *      .first()
 *      .find()!!
 *      .counter
 *      .increment(1)
 * ```
 * Note that the [set] operator must be used with extreme care. It will quash the effects of any
 * prior calls to [increment] or [decrement]. Although the value of a `MutableRealmInt` will always
 * converge across devices, the specific value on which it converges will depend on the actual order
 * in which operations took place. Mixing [set] with [increment] and/or [decrement] is, therefore,
 * not advised, unless fuzzy counting is acceptable.
 *
 * `MutableRealmInt`s **may not be primary keys**.
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
public interface MutableRealmInt : Comparable<MutableRealmInt> {

    /**
     * Gets the `MutableRealmInt` value.
     *
     * @return the value.
     */
    public fun get(): Long

    /**
     * Sets the `MutableRealmInt` value.
     *
     * Calling [set] forcibly sets the `MutableRealmInt` to the provided value. Doing this
     * obliterates the effects of any calls to [increment] and [decrement] perceived before the call
     * to `set`.
     *
     * @param value new value.
     */
    public fun set(value: Long)

    /**
     * Increments the `MutableRealmInt`, adding the value of the argument. Increment/decrement
     * operations from all devices are reflected in the new value, which is guaranteed to converge.
     *
     * @param value quantity to be added to the `MutableRealmInt`.
     */
    public fun increment(value: Long)

    /**
     * Decrements the `MutableRealmInt`, subtracting the value of the argument. Increment/decrement
     * operations from all devices are reflected in the new value, which is guaranteed to converge.
     *
     * @param value quantity to be subtracted from the `MutableRealmInt`.
     */
    public fun decrement(value: Long)

    /**
     * Two `MutableRealmInt`s are equal if and only if their [Long] values are equal.
     *
     * @param other compare target
     * @return `true` if the target has the same value.
     */
    override fun equals(other: Any?): Boolean

    /**
     * A `MutableRealmInt`'s hash code is, exactly, the hash code of its value.
     *
     * @return the hash code of its value
     */
    override fun hashCode(): Int

    /**
     * `MutableRealmInt`s compare strictly by their [Long] values.
     *
     * @param other the compare target
     * @return -1, 0, or 1, depending on whether this object's value is <, =, or > the target's.
     */
    override fun compareTo(other: MutableRealmInt): Int {
        val thisValue = get()
        val otherValue = other.get()
        return thisValue.compareTo(otherValue)
    }

    public companion object {
        /**
         * Creates a new, unmanaged [MutableRealmInt] with the specified initial value.
         *
         * @param value initial value.
         */
        public fun of(value: Long): MutableRealmInt = UnmanagedMutableRealmInt(value)
    }
}
