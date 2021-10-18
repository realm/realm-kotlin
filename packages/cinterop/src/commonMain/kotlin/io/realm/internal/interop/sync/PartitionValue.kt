/*
 * Copyright 2020 Realm Inc.
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

package io.realm.internal.interop.sync

/**
 * Value container à la BsonValue. This is only meant to be used temporarily until the BSON library
 * is ported to Kotlin multiplatform.
 */
class PartitionValue private constructor(
    private val stringValue: String? = null,
    private val longValue: Long? = null
) {

    constructor(stringValue: String) : this(stringValue = stringValue, longValue = null)
    constructor(longValue: Long) : this(stringValue = null, longValue = longValue)

    private enum class ValueType {
        STRING, LONG
    }

    private val valueType: ValueType = when {
        stringValue != null -> ValueType.STRING
        longValue != null -> ValueType.LONG
        else -> throw IllegalStateException("Wrong partition value")
    }

    fun asLong(): Long = checkValidType(ValueType.LONG).let { longValue!! }

    fun asString(): String = checkValidType(ValueType.STRING).let { stringValue!! }

    /**
     * Returns the corresponding value following the BSON standard for its type for its use within
     * sync.
     */
    fun asSyncPartition(): String {
        return when (valueType) {
            ValueType.STRING -> """"${asString()}""""
            ValueType.LONG -> """{"${'$'}numberLong":"${asLong()}"}"""
        }
    }

<<<<<<< HEAD
    fun asLong(): Long = checkValidType(ValueType.LONG).let { longValue!! }

    fun asString(): String = checkValidType(ValueType.STRING).let { stringValue!! }

=======
>>>>>>> master
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is PartitionValue) {
            if (asSyncPartition() == other.asSyncPartition()) {
                return true
            }
        }

        return false
    }

    override fun hashCode(): Int {
        var result = stringValue?.hashCode() ?: 0
        result = 31 * result + (longValue?.hashCode() ?: 0)
        result = 31 * result + valueType.hashCode()
        return result
    }

    private fun checkValidType(expectedValueType: ValueType) {
        if (expectedValueType != valueType) {
            throw IllegalStateException("This partition value is not a ${expectedValueType.name} but a ${valueType.name}")
        }
        when (valueType) {
            ValueType.STRING ->
                if (stringValue == null) throw IllegalStateException("String value cannot be null")
            ValueType.LONG ->
                if (longValue == null) throw IllegalStateException("Long value cannot be null")
        }
    }
}
