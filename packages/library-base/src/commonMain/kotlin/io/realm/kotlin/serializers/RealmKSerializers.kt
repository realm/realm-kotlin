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
import io.realm.kotlin.ext.toRealmDictionary
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.ext.toRealmSet
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmAny.Type
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.mongodb.kbson.Decimal128

/**
 * KSerializer implementation for [RealmList]. Serialization is done as a regular list structure,
 * whilst deserialization is always done with an unmanaged [RealmList].
 *
 * It supports any serializable type as a type argument. Note that serializers for Realm datatypes
 * require to be manually subscribed.
 *
 * Subscription can be done defining the serializer per property
 * ```
 * class Example : RealmObject {
 *     @Serializable(RealmListSerializer::class)
 *     var myList: RealmList<String> = realmListOf()
 * }
 * ```
 * or per file
 * ```
 * @file:UseSerializers(RealmListSerializer::class)
 *
 * class Example : RealmObject {
 *     var myList: RealmList<String> = realmListOf()
 * }
 * ```
 * In [io.realm.kotlin.serializers] you would find the serializers for all Realm data types.
 */
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

/**
 * KSerializer implementation for [RealmSet]. Serialization is done as a regular list structure,
 * whilst deserialization is always done with an unmanaged [RealmSet].
 *
 * It supports any serializable type as a type argument. Note that serializers for Realm datatypes
 * require to be manually subscribed.
 *
 * Subscription can be done defining the serializer per property
 * ```
 * class Example : RealmObject {
 *     @Serializable(RealmSetSerializer::class)
 *     var mySet: RealmSet<String> = realmSetOf()
 * }
 * ```
 * or per file
 * ```
 * @file:UseSerializers(RealmSetSerializer::class)
 *
 * class Example : RealmObject {
 *     var mySet: RealmSet<String> = realmSetOf()
 * }
 * ```
 * In [io.realm.kotlin.serializers] you would find the serializers for all Realm data types.
 */
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

/**
 * KSerializer implementation for [RealmDictionary]. Serialization is done as a regular map structure,
 * whilst deserialization is always done with an unmanaged [RealmDictionary].
 *
 * It supports any serializable type as a type argument. Note that serializers for Realm datatypes
 * require to be manually subscribed.
 *
 * Subscription can be done defining the serializer per property
 * ```
 * class Example : RealmObject {
 *     @Serializable(RealmDictionarySerializer::class)
 *     var myDictionary: RealmDictionary<String> = realmDictionaryOf()
 * }
 * ```
 * or per file
 * ```
 * @file:UseSerializers(RealmDictionarySerializer::class)
 *
 * class Example : RealmObject {
 *     var myDictionary: RealmDictionary<String> = realmDictionaryOf()
 * }
 * ```
 * In [io.realm.kotlin.serializers] you would find the serializers for all Realm data types.
 */
public class RealmDictionarySerializer<E>(elementSerializer: KSerializer<E>) :
    KSerializer<RealmDictionary<E>> {
    private val serializer = MapSerializer(String.serializer(), elementSerializer)

    override val descriptor: SerialDescriptor =
        serializer.descriptor

    override fun deserialize(decoder: Decoder): RealmDictionary<E> =
        serializer.deserialize(decoder).toRealmDictionary()

    override fun serialize(encoder: Encoder, value: RealmDictionary<E>) {
        serializer.serialize(encoder, value)
    }
}

/**
 * KSerializer implementation for [RealmInstant]. Serialization is done as a specific map structure
 * with two entries: `epochSeconds` and `nanosecondsOfSecond`, whilst deserialization is always done
 * with an unmanaged [RealmInstant].
 *
 * Subscription can be done defining the serializer per property
 * ```
 * class Example : RealmObject {
 *     @Serializable(RealmInstantSerializer::class)
 *     var myInstant: RealmInstant = RealmInstant.now()
 * }
 * ```
 * or per file
 * ```
 * @file:UseSerializers(RealmInstantSerializer::class)
 *
 * class Example : RealmObject {
 *     var myInstant: RealmInstant = RealmInstant.now()
 * }
 * ```
 * In [io.realm.kotlin.serializers] you would find the serializers for all Realm data types.
 */
public class RealmInstantSerializer : KSerializer<RealmInstant> {
    private val serializer = SerializableRealmInstant.serializer()
    override val descriptor: SerialDescriptor = serializer.descriptor

    @Serializable
    private class SerializableRealmInstant(
        var epochSeconds: Long = 0,
        var nanosecondsOfSecond: Int = 0
    )

    override fun deserialize(decoder: Decoder): RealmInstant =
        decoder.decodeSerializableValue(serializer).let { instant: SerializableRealmInstant ->
            RealmInstant.from(
                instant.epochSeconds,
                instant.nanosecondsOfSecond
            )
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

/**
 * KSerializer implementation for [RealmAny]. Serialization is done as a specific map structure
 * that represents the a union type with all possible value types. Deserialization is always done
 * with an unmanaged [RealmAny].
 *
 * Subscription can be done defining the serializer per property
 * ```
 * class Example : RealmObject {
 *     @Serializable(RealmAnySerializer::class)
 *     var myInstant: RealmAny = RealmAny.create("hello world")
 * }
 * ```
 * or per file
 * ```
 * @file:UseSerializers(RealmAnySerializer::class)
 *
 * class Example : RealmObject {
 *     var myInstant: RealmAny = RealmAny.create("hello world")
 * }
 * ```
 * In [io.realm.kotlin.serializers] you would find the serializers for all Realm data types.
 */
public object RealmAnySerializer : KSerializer<RealmAny> {
    @Serializable
    private class SerializableRealmAny {
        lateinit var type: String
        var intValue: Long? = null
        var boolValue: Boolean? = null
        var stringValue: String? = null
        var binaryValue: ByteArray? = null

        @Serializable(with = RealmInstantSerializer::class)
        var realmInstantValue: RealmInstant? = null
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
                Type.TIMESTAMP -> RealmAny.create(it.realmInstantValue!!)
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
                    Type.TIMESTAMP -> realmInstantValue = value.asRealmInstant()
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

/**
 * KSerializer implementation for [RealmUUID]. Serialization is done as a [ByteArray], whilst
 * deserialization is done with an unmanaged [RealmUUID].
 *
 * Subscription can be done defining the serializer per property
 * ```
 * class Example : RealmObject {
 *     @Serializable(RealmUUIDSerializer::class)
 *     var myUUID: RealmUUID = RealmUUID.create()
 * }
 * ```
 * or per file
 * ```
 * @file:UseSerializers(RealmUUIDSerializer::class)
 *
 * class Example : RealmObject {
 *     var myUUID: RealmUUID = RealmUUID.create()
 * }
 * ```
 * In [io.realm.kotlin.serializers] you would find the serializers for all Realm data types.
 */
public class RealmUUIDSerializer : KSerializer<RealmUUID> {
    private val serializer = ByteArraySerializer()
    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun deserialize(decoder: Decoder): RealmUUID =
        RealmUUID.from(decoder.decodeSerializableValue(serializer))

    override fun serialize(encoder: Encoder, value: RealmUUID) {
        encoder.encodeSerializableValue(serializer, value.bytes)
    }
}

/**
 * KSerializer implementation for [ObjectId]. Serialization is done as a [ByteArray], whilst
 * deserialization is done with an unmanaged [ObjectId].
 *
 * Subscription can be done defining the serializer per property
 * ```
 * class Example : RealmObject {
 *     @Serializable(RealmObjectIdSerializer::class)
 *     var myObjectId: ObjectId = ObjectId.create()
 * }
 * ```
 * or per file
 * ```
 * @file:UseSerializers(RealmObjectIdSerializer::class)
 *
 * class Example : RealmObject {
 *     var myObjectId: ObjectId = ObjectId.create()
 * }
 * ```
 * In [io.realm.kotlin.serializers] you would find the serializers for all Realm data types.
 */
public class RealmObjectIdSerializer : KSerializer<ObjectId> {
    private val serializer = ByteArraySerializer()
    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun deserialize(decoder: Decoder): ObjectId =
        ObjectId.from(decoder.decodeSerializableValue(serializer))

    override fun serialize(encoder: Encoder, value: ObjectId) {
        encoder.encodeSerializableValue(serializer, value.asBsonObjectId().toByteArray())
    }
}

/**
 * KSerializer implementation for [MutableRealmInt]. Serialization is done with a primitive long value,
 * whilst deserialization is done with an unmanaged [MutableRealmInt].
 *
 * Subscription can be done defining the serializer per property
 * ```
 * class Example : RealmObject {
 *     @Serializable(MutableRealmIntSerializer::class)
 *     var myMutableRealmInt: MutableRealmInt = MutableRealmInt.create(0)
 * }
 * ```
 * or per file
 * ```
 * @file:UseSerializers(MutableRealmIntSerializer::class)
 *
 * class Example : RealmObject {
 *     var myMutableRealmInt: MutableRealmInt = MutableRealmInt.create(0)
 * }
 * ```
 * In [io.realm.kotlin.serializers] you would find the serializers for all Realm data types.
 */
public class MutableRealmIntSerializer : KSerializer<MutableRealmInt> {
    override val descriptor: SerialDescriptor = Long.serializer().descriptor

    override fun deserialize(decoder: Decoder): MutableRealmInt =
        MutableRealmInt.create(decoder.decodeLong())

    override fun serialize(encoder: Encoder, value: MutableRealmInt) {
        encoder.encodeLong(value.toLong())
    }
}
