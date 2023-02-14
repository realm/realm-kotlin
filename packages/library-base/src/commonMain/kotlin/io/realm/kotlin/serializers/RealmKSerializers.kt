/*
 * Copyright 2023 Realm Inc.
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
package io.realm.kotlin.serializers

import io.realm.kotlin.ext.asBsonObjectId
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.ext.toRealmSet
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmAny.Type
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.mongodb.kbson.Decimal128

// TODO document
public class RealmListSerializer<E>(elementSerializer: KSerializer<E>) : KSerializer<RealmList<E>> {
    private val serializer = ListSerializer(elementSerializer)

    override val descriptor: SerialDescriptor =
        serializer.descriptor

    override fun deserialize(decoder: Decoder): RealmList<E> =
        serializer.deserialize(decoder).toRealmList()

    override fun serialize(encoder: Encoder, value: RealmList<E>) {
        serializer.serialize(encoder, value)
    }
}

// TODO document
public class RealmSetSerializer<E>(elementSerializer: KSerializer<E>) : KSerializer<RealmSet<E>> {
    private val serializer = SetSerializer(elementSerializer)

    override val descriptor: SerialDescriptor =
        serializer.descriptor

    override fun deserialize(decoder: Decoder): RealmSet<E> =
        serializer.deserialize(decoder).toRealmSet()

    override fun serialize(encoder: Encoder, value: RealmSet<E>) {
        serializer.serialize(encoder, value)
    }
}

// TODO document
public class RealmInstantSerializer : KSerializer<RealmInstant> {
    private val serializer = SerializableRealmInstant.serializer()
    override val descriptor: SerialDescriptor = serializer.descriptor

    @Serializable
    private class SerializableRealmInstant(
        var epochSeconds: Long = 0,
        var nanosecondsOfSecond: Int = 0
    )

    override fun deserialize(decoder: Decoder): RealmInstant =
        decoder.decodeSerializableValue(serializer).let { it: SerializableRealmInstant ->
            RealmInstant.from(it.epochSeconds, it.nanosecondsOfSecond)
        }

    override fun serialize(encoder: Encoder, value: RealmInstant) {
        encoder.encodeSerializableValue(
            serializer,
            SerializableRealmInstant(
                value.epochSeconds,
                value.nanosecondsOfSecond
            )
        )
    }
}

// TODO document
public object RealmAnySerializer : KSerializer<RealmAny> {
    @Serializable
    private class SerializableRealmAny {
        lateinit var type: String
        var intValue: Long? = null
        var boolValue: Boolean? = null
        var stringValue: String? = null
        var binaryValue: ByteArray? = null

        @Serializable(with = RealmInstantSerializer::class)
        var realmInstant: RealmInstant? = null
        var floatValue: Float? = null
        var doubleValue: Double? = null
        var decimal128Value: Decimal128? = null

        @Serializable(with = RealmObjectIdSerializer::class)
        var objectIdValue: ObjectId? = null

        @Serializable(with = RealmUUIDSerializer::class)
        var uuidValue: RealmUUID? = null
        var objectValue: RealmObject? = null
    }

    private val serializer = SerializableRealmAny.serializer()
    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun deserialize(decoder: Decoder): RealmAny {
        return decoder.decodeSerializableValue(serializer).let {
            when (Type.valueOf(it.type)) {
                Type.INT -> RealmAny.create(it.intValue!!.toLong())
                Type.BOOL -> RealmAny.create(it.boolValue!!)
                Type.STRING -> RealmAny.create(it.stringValue!!)
                Type.BINARY -> RealmAny.create(it.binaryValue!!)
                Type.TIMESTAMP -> RealmAny.create(it.realmInstant!!)
                Type.FLOAT -> RealmAny.create(it.floatValue!!)
                Type.DOUBLE -> RealmAny.create(it.doubleValue!!)
                Type.DECIMAL128 -> RealmAny.create(it.decimal128Value!!)
                Type.OBJECT_ID -> RealmAny.create(it.objectIdValue!!.asBsonObjectId())
                Type.UUID -> RealmAny.create(it.uuidValue!!)
                Type.OBJECT -> RealmAny.create(it.objectValue!!)
            }

        }
    }

    override fun serialize(encoder: Encoder, value: RealmAny) {
        encoder.encodeSerializableValue(
            serializer,
            SerializableRealmAny().apply {
                type = value.type.name
                when (value.type) {
                    Type.INT -> intValue = value.asLong()
                    Type.BOOL -> boolValue = value.asBoolean()
                    Type.STRING -> stringValue = value.asString()
                    Type.BINARY -> binaryValue = value.asByteArray()
                    Type.TIMESTAMP -> realmInstant = value.asRealmInstant()
                    Type.FLOAT -> floatValue = value.asFloat()
                    Type.DOUBLE -> doubleValue = value.asDouble()
                    Type.DECIMAL128 -> decimal128Value = value.asDecimal128()
                    Type.OBJECT_ID -> objectIdValue = ObjectId.from(
                        value.asObjectId().toByteArray()
                    )
                    Type.UUID -> uuidValue = value.asRealmUUID()
                    Type.OBJECT -> objectValue = value.asRealmObject()
                }

            }
        )
    }
}

// TODO document
public class RealmUUIDSerializer : KSerializer<RealmUUID> {
    private val serializer = ByteArraySerializer()
    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun deserialize(decoder: Decoder): RealmUUID =
        RealmUUID.from(decoder.decodeSerializableValue(serializer))

    override fun serialize(encoder: Encoder, value: RealmUUID) {
        encoder.encodeSerializableValue(serializer, value.bytes)
    }
}

// TODO document
public class RealmObjectIdSerializer : KSerializer<ObjectId> {
    private val serializer = ByteArraySerializer()
    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun deserialize(decoder: Decoder): ObjectId =
        ObjectId.from(decoder.decodeSerializableValue(serializer))

    override fun serialize(encoder: Encoder, value: ObjectId) {
        encoder.encodeSerializableValue(serializer, value.asBsonObjectId().toByteArray())
    }
}

// TODO document
public class MutableRealmIntSerializer : KSerializer<MutableRealmInt> {
    override val descriptor: SerialDescriptor = Long.serializer().descriptor

    override fun deserialize(decoder: Decoder): MutableRealmInt =
        MutableRealmInt.create(decoder.decodeLong())

    override fun serialize(encoder: Encoder, value: MutableRealmInt) {
        encoder.encodeLong(value.toLong())
    }
}
