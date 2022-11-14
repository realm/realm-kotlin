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

package io.realm.kotlin.internal

import io.realm.kotlin.internal.RealmObjectHelper.NOT_IN_A_TRANSACTION_MSG
import io.realm.kotlin.internal.interop.PropertyKey
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.MutableRealmInt
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
internal class ManagedMutableRealmInt(
    private val obj: RealmObjectReference<out BaseRealmObject>,
    private val propertyKey: PropertyKey,
    private val converter: RealmValueConverter<Long>
) : MutableRealmInt() {

    override fun get(): Long {
        obj.checkValid()
        val realmValue = RealmInterop.realm_get_value(obj.objectPointer, propertyKey)
        return converter.realmValueToPublic(realmValue)!!
    }

    override fun set(value: Number) = operationInternal("Cannot set", value) {
        val convertedValue = converter.publicToRealmValue(value.toLong())
        RealmInterop.realm_set_value(
            obj.objectPointer,
            propertyKey,
            convertedValue,
            false
        )
    }

    override fun increment(value: Number) = operationInternal("Cannot increment/decrement", value) {
        RealmInterop.realm_object_add_int(
            obj.objectPointer,
            propertyKey,
            value.toLong()
        )
    }

    override fun decrement(value: Number) = increment(-value.toLong())

    private inline fun operationInternal(message: String, value: Number, block: () -> Unit) {
        obj.checkValid()
        try {
            block()
        } catch (exception: Throwable) {
            throw CoreExceptionConverter.convertToPublicException(
                exception,
                "$message `${obj.className}.$${obj.metadata[propertyKey]!!.name}` with passed value `$value`: $NOT_IN_A_TRANSACTION_MSG",
            )
        }
    }
}

@Serializable(with = ManagedMutableRealmIntSerializer::class)
internal class UnmanagedMutableRealmInt(
    private var value: Long = 0
) : MutableRealmInt() {

    override fun get(): Long = value

    override fun set(value: Number) {
        this.value = value.toLong()
    }

    override fun increment(value: Number) {
        this.value = this.value + value.toLong()
    }

    override fun decrement(value: Number) = increment(-value.toLong())
}

private object ManagedMutableRealmIntSerializer : KSerializer<MutableRealmInt> {
    private val serializer = Long.serializer()

    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun deserialize(decoder: Decoder): MutableRealmInt =
        MutableRealmInt.create(serializer.deserialize(decoder))

    override fun serialize(encoder: Encoder, value: MutableRealmInt) =
        serializer.serialize(encoder, value.get())
}
