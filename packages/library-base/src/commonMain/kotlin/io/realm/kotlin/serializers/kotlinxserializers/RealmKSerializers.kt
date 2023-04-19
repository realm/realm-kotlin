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
package io.realm.kotlin.serializers.kotlinxserializers

import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.toRealmDictionary
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.ext.toRealmSet
import io.realm.kotlin.internal.toDuration
import io.realm.kotlin.internal.toRealmInstant
import io.realm.kotlin.types.MutableRealmInt
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import org.mongodb.kbson.BsonBinary
import org.mongodb.kbson.BsonBinarySubType
import org.mongodb.kbson.BsonDateTime
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128
import kotlin.time.Duration.Companion.milliseconds

/**
 * KSerializer implementation for [RealmList]. Serialization is done as a generic list structure,
 * whilst deserialization is done into an unmanaged [RealmList].
 *
 * It supports any serializable type as a type argument.
 *
 * The serializer must be registered per property:
 * ```
 * class Example : RealmObject {
 *     @Serializable(RealmListKSerializer::class)
 *     var myList: RealmList<String> = realmListOf()
 * }
 * ```
 * or per file:
 * ```
 * @file:UseSerializers(RealmListKSerializer::class)
 *
 * class Example : RealmObject {
 *     var myList: RealmList<String> = realmListOf()
 * }
 * ```
 *
 * Adding the following code snippet to a Kotlin file would conveniently register any field using a
 * Realm datatype to its correspondent serializer:
 *
 * ```
 * @file:UseSerializers(
 *     RealmListKSerializer::class,
 *     RealmSetKSerializer::class,
 *     RealmAnyKSerializer::class,
 *     RealmInstantKSerializer::class,
 *     MutableRealmIntKSerializer::class,
 *     RealmUUIDKSerializer::class
 * )
 * ```
 *
 * Serializers for all Realm data types can be found in [io.realm.kotlin.serializers].
 */
public class RealmListKSerializer<E>(elementSerializer: KSerializer<E>) :
    KSerializer<RealmList<E>> {
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
 * KSerializer implementation for [RealmSet]. Serialization is done as a generic list structure,
 * whilst deserialization is done into an unmanaged [RealmSet].
 *
 * It supports any serializable type as a type argument.
 *
 * The serializer must be registered per property:
 * ```
 * class Example : RealmObject {
 *     @Serializable(RealmSetKSerializer::class)
 *     var mySet: RealmSet<String> = realmSetOf()
 * }
 * ```
 * or per file:
 * ```
 * @file:UseSerializers(RealmSetKSerializer::class)
 *
 * class Example : RealmObject {
 *     var mySet: RealmSet<String> = realmSetOf()
 * }
 * ```
 *
 * Adding the following code snippet to a Kotlin file would conveniently register any field using a
 * Realm datatype to its correspondent serializer:
 *
 * ```
 * @file:UseSerializers(
 *     RealmListKSerializer::class,
 *     RealmSetKSerializer::class,
 *     RealmAnyKSerializer::class,
 *     RealmInstantKSerializer::class,
 *     MutableRealmIntKSerializer::class,
 *     RealmUUIDKSerializer::class
 * )
 * ```
 *
 * Serializers for all Realm data types can be found in [io.realm.kotlin.serializers].
 */
public class RealmSetKSerializer<E>(elementSerializer: KSerializer<E>) : KSerializer<RealmSet<E>> {
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
 * KSerializer implementation for [RealmDictionary]. Serialization is done as a generic map structure,
 * whilst deserialization is done into an unmanaged [RealmDictionary].
 *
 * It supports any serializable type as a type argument.
 *
 * The serializer must be registered per property:
 * ```
 * class Example : RealmObject {
 *     @Serializable(RealmDictionaryKSerializer::class)
 *     var myDictionary: RealmDictionary<String> = realmDictionaryOf()
 * }
 * ```
 * or per file:
 * ```
 * @file:UseSerializers(RealmDictionaryKSerializer::class)
 *
 * class Example : RealmObject {
 *     var myDictionary: RealmDictionary<String> = realmDictionaryOf()
 * }
 * ```
 *
 * Adding the following code snippet to a Kotlin file would conveniently register any field using a
 * Realm datatype to its correspondent serializer:
 *
 * ```
 * @file:UseSerializers(
 *     RealmListKSerializer::class,
 *     RealmSetKSerializer::class,
 *     RealmAnyKSerializer::class,
 *     RealmInstantKSerializer::class,
 *     MutableRealmIntKSerializer::class,
 *     RealmUUIDKSerializer::class
 * )
 * ```
 *
 * Serializers for all Realm data types can be found in [io.realm.kotlin.serializers].
 */
public class RealmDictionaryKSerializer<E>(elementSerializer: KSerializer<E>) :
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
 * KSerializer implementation for [RealmInstant]. Serialization is done with [BsonDateTime],
 * whilst deserialization is done with an unmanaged [MutableRealmInt]. Serialization can incur in
 * precision loss because [RealmInstant] has nanoseconds precision whilst [BsonDateTime] milliseconds.
 *
 * The serializer must be registered per property:
 * ```
 * class Example : RealmObject {
 *     @Serializable(RealmInstantKSerializer::class)
 *     var myInstant: RealmInstant = RealmInstant.now()
 * }
 * ```
 * or per file:
 * ```
 * @file:UseSerializers(RealmInstantKSerializer::class)
 *
 * class Example : RealmObject {
 *     var myInstant: RealmInstant = RealmInstant.now()
 * }
 * ```
 *
 * Adding the following code snippet to a Kotlin file would conveniently register any field using a
 * Realm datatype to its correspondent serializer:
 *
 * ```
 * @file:UseSerializers(
 *     RealmListKSerializer::class,
 *     RealmSetKSerializer::class,
 *     RealmAnyKSerializer::class,
 *     RealmInstantKSerializer::class,
 *     MutableRealmIntKSerializer::class,
 *     RealmUUIDKSerializer::class
 * )
 * ```
 *
 * Serializers for all Realm data types can be found in [io.realm.kotlin.serializers].
 */
public object RealmInstantKSerializer : KSerializer<RealmInstant> {
    private val serializer = BsonDateTime.serializer()
    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun deserialize(decoder: Decoder): RealmInstant =
        decoder.decodeSerializableValue(serializer).value.milliseconds.toRealmInstant()

    override fun serialize(encoder: Encoder, value: RealmInstant) {
        encoder.encodeSerializableValue(
            serializer = serializer,
            value = BsonDateTime(value.toDuration().inWholeMilliseconds)
        )
    }
}

/**
 * KSerializer implementation for [RealmAny]. Serialization is done as a specific map structure
 * that represents the a union type with all possible value types:
 *
 * ```
 * realmAny:
 *     type: [INT, BOOL, STRING, BINARY, TIMESTAMP, FLOAT, DOUBLE, DECIMAL128, OBJECT_ID, UUID, OBJECT]
 *     int: Long?
 *     bool: Boolean?
 *     string: String?
 *     binary: ByteArray?
 *     instant: RealmInstant?
 *     float: Float?
 *     double: Double?
 *     decimal128: Decimal128?
 *     objectId: ObjectId?
 *     uuid: RealmUUID?
 *     realmObject: RealmObject?
 * ```
 *
 * Deserialization is done with an unmanaged [RealmAny].
 *
 * The serializer must be registered per property:
 * ```
 * class Example : RealmObject {
 *     @Serializable(RealmAnyKSerializer::class)
 *     var myInstant: RealmAny = RealmAny.create("hello world")
 * }
 * ```
 * or per file:
 * ```
 * @file:UseSerializers(RealmAnyKSerializer::class)
 *
 * class Example : RealmObject {
 *     var myInstant: RealmAny = RealmAny.create("hello world")
 * }
 * ```
 *
 * Serialization of [RealmAny] instances containing [RealmObject] require of a [SerializersModule]
 * mapping such objects to the polymorphic [RealmObject] interface:
 *
 * ```
 * val json = Json {
 *     serializersModule = SerializersModule {
 *         polymorphic(RealmObject::class) {
 *             subclass(SerializableSample::class)
 *         }
 *     }
 * }
 * ```
 *
 * Adding the following code snippet to a Kotlin file would conveniently register any field using a
 * Realm datatype to its correspondent serializer:
 *
 * ```
 * @file:UseSerializers(
 *     RealmListKSerializer::class,
 *     RealmSetKSerializer::class,
 *     RealmAnyKSerializer::class,
 *     RealmInstantKSerializer::class,
 *     MutableRealmIntKSerializer::class,
 *     RealmUUIDKSerializer::class
 * )
 * ```
 *
 * Serializers for all Realm data types can be found in [io.realm.kotlin.serializers].
 */
public object RealmAnyKSerializer : KSerializer<RealmAny> {

    /**
     * This class represents a union type of all possible RealmAny types. We cannot write a regular
     * serializer because we need to be able to resolve the serializer for a RealmObject in runtime,
     * and the only way to do it is through kserialization internal functions.
     */
    @Serializable
    private class SerializableRealmAny {
        lateinit var type: String
        var int: Long? = null
        var bool: Boolean? = null
        var string: String? = null
        var binary: ByteArray? = null

        @Serializable(RealmInstantKSerializer::class)
        var instant: RealmInstant? = null
        var float: Float? = null
        var double: Double? = null
        var decimal128: Decimal128? = null
        var objectId: BsonObjectId? = null

        @Serializable(RealmUUIDKSerializer::class)
        var uuid: RealmUUID? = null
        var realmObject: RealmObject? = null
    }

    private val serializer = SerializableRealmAny.serializer()
    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun deserialize(decoder: Decoder): RealmAny {
        return decoder.decodeSerializableValue(serializer).let {
            when (Type.valueOf(it.type)) {
                Type.INT -> RealmAny.create(it.int!!.toLong())
                Type.BOOL -> RealmAny.create(it.bool!!)
                Type.STRING -> RealmAny.create(it.string!!)
                Type.BINARY -> RealmAny.create(it.binary!!)
                Type.TIMESTAMP -> RealmAny.create(it.instant!!)
                Type.FLOAT -> RealmAny.create(it.float!!)
                Type.DOUBLE -> RealmAny.create(it.double!!)
                Type.DECIMAL128 -> RealmAny.create(it.decimal128!!)
                Type.OBJECT_ID -> RealmAny.create(it.objectId!!)
                Type.UUID -> RealmAny.create(it.uuid!!)
                Type.OBJECT -> RealmAny.create(it.realmObject!!)
            }
        }
    }

    override fun serialize(encoder: Encoder, value: RealmAny) {
        encoder.encodeSerializableValue(
            serializer,
            SerializableRealmAny().apply {
                type = value.type.name
                when (value.type) {
                    Type.INT -> int = value.asLong()
                    Type.BOOL -> bool = value.asBoolean()
                    Type.STRING -> string = value.asString()
                    Type.BINARY -> binary = value.asByteArray()
                    Type.TIMESTAMP -> instant = value.asRealmInstant()
                    Type.FLOAT -> float = value.asFloat()
                    Type.DOUBLE -> double = value.asDouble()
                    Type.DECIMAL128 -> decimal128 = value.asDecimal128()
                    Type.OBJECT_ID -> objectId = BsonObjectId(
                        value.asObjectId().toByteArray()
                    )
                    Type.UUID -> uuid = value.asRealmUUID()
                    Type.OBJECT -> realmObject = value.asRealmObject()
                }
            }
        )
    }
}

/**
 * KSerializer implementation for [RealmUUID]. Serialization is done as a [BsonBinary] of
 * [BsonBinarySubType.UUID_STANDARD], whilst deserialization is done with an unmanaged [RealmUUID].
 *
 * The serializer must be registered per property:
 * ```
 * class Example : RealmObject {
 *     @Serializable(RealmUUIDKSerializer::class)
 *     var myUUID: RealmUUID = RealmUUID.create()
 * }
 * ```
 * or per file:
 * ```
 * @file:UseSerializers(RealmUUIDKSerializer::class)
 *
 * class Example : RealmObject {
 *     var myUUID: RealmUUID = RealmUUID.create()
 * }
 * ```
 *
 * Adding the following code snippet to a Kotlin file would conveniently register any field using a
 * Realm datatype to its correspondent serializer:
 *
 * ```
 * @file:UseSerializers(
 *     RealmListKSerializer::class,
 *     RealmSetKSerializer::class,
 *     RealmAnyKSerializer::class,
 *     RealmInstantKSerializer::class,
 *     MutableRealmIntKSerializer::class,
 *     RealmUUIDKSerializer::class
 * )
 * ```
 *
 * Serializers for all Realm data types can be found in [io.realm.kotlin.serializers].
 */
public object RealmUUIDKSerializer : KSerializer<RealmUUID> {
    private val serializer = BsonBinary.serializer()
    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun deserialize(decoder: Decoder): RealmUUID =
        RealmUUID.from(decoder.decodeSerializableValue(serializer).data)

    override fun serialize(encoder: Encoder, value: RealmUUID) {
        encoder.encodeSerializableValue(
            serializer = serializer,
            value = BsonBinary(BsonBinarySubType.UUID_STANDARD, value.bytes)
        )
    }
}

/**
 * KSerializer implementation for [MutableRealmInt]. Serialization is done with a primitive long value,
 * whilst deserialization is done with an unmanaged [MutableRealmInt].
 *
 * The serializer must be registered per property:
 * ```
 * class Example : RealmObject {
 *     @Serializable(MutableRealmIntKSerializer::class)
 *     var myMutableRealmInt: MutableRealmInt = MutableRealmInt.create(0)
 * }
 * ```
 * or per file:
 * ```
 * @file:UseSerializers(MutableRealmIntKSerializer::class)
 *
 * class Example : RealmObject {
 *     var myMutableRealmInt: MutableRealmInt = MutableRealmInt.create(0)
 * }
 * ```
 *
 * Adding the following code snippet to a Kotlin file would conveniently register any field using a
 * Realm datatype to its correspondent serializer:
 *
 * ```
 * @file:UseSerializers(
 *     RealmListKSerializer::class,
 *     RealmSetKSerializer::class,
 *     RealmAnyKSerializer::class,
 *     RealmInstantKSerializer::class,
 *     MutableRealmIntKSerializer::class,
 *     RealmUUIDKSerializer::class
 * )
 * ```
 *
 * Serializers for all Realm data types can be found in [io.realm.kotlin.serializers].
 */
public object MutableRealmIntKSerializer : KSerializer<MutableRealmInt> {
    override val descriptor: SerialDescriptor = Long.serializer().descriptor

    override fun deserialize(decoder: Decoder): MutableRealmInt =
        MutableRealmInt.create(decoder.decodeLong())

    override fun serialize(encoder: Encoder, value: MutableRealmInt) {
        encoder.encodeLong(value.toLong())
    }
}
