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

package io.realm.kotlin.mongodb.sync

import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmUUID

/**
 * Value container à la BsonValue. This is only meant to be used temporarily until the BSON library
 * is ported to Kotlin multiplatform.
 */
internal class PartitionValue private constructor(private val bsonValue: Any?) {

    constructor(value: String?) : this(bsonValue = value)
    constructor(value: Long?) : this(bsonValue = value)
    constructor(value: Int?) : this(bsonValue = value)
    constructor(value: ObjectId?) : this(bsonValue = value)
    constructor(value: RealmUUID?) : this(bsonValue = value)

    private val valueType: ValueType

    init {
        valueType = when (bsonValue) {
            is String -> ValueType.STRING
            is Long -> ValueType.LONG
            is Int -> ValueType.INT
            is ObjectId -> ValueType.OBJECT_ID
            is RealmUUID -> ValueType.UUID
            null -> ValueType.NULL
            else -> {
                TODO("Unsupported type: ${bsonValue::class}")
            }
        }
    }

    internal enum class ValueType {
        STRING, LONG, INT, NULL, OBJECT_ID, UUID
    }

    /**
     * Returns the corresponding value following the BSON standard for its type for its use within
     * sync.
     */
    fun asSyncPartition(): String {
        return when (valueType) {
            ValueType.STRING -> """"${bsonValue as String}""""
            ValueType.LONG -> """{"${'$'}numberLong":"${bsonValue as Long}"}"""
            ValueType.INT -> """{"${'$'}numberInt":"${bsonValue as Int}"}"""
            ValueType.OBJECT_ID -> """{"${'$'}oid":"${bsonValue as ObjectId}"}"""
            ValueType.UUID -> """{"${'$'}uuid":"${bsonValue as RealmUUID}"}"""
            ValueType.NULL -> """null""" // TODO Is this true
        }
    }

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
        var result = bsonValue?.hashCode() ?: 0
        result = 31 * result + valueType.hashCode()
        return result
    }
}
