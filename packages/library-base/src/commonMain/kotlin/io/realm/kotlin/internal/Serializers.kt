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

import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmUUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Duration.Companion.milliseconds

public object RealmUUIDSerializer : KSerializer<RealmUUID> {
    public val serializer: KSerializer<ByteArray> = ByteArraySerializer()

    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun deserialize(decoder: Decoder): RealmUUID = RealmUUIDImpl(serializer.deserialize(decoder))

    override fun serialize(encoder: Encoder, value: RealmUUID): Unit = serializer.serialize(encoder, (value as RealmUUIDImpl).bytes)
}

internal object RealmInstantSerializer : KSerializer<RealmInstant> {
    private val serializer = Long.serializer()

    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun deserialize(decoder: Decoder): RealmInstant =
        serializer.deserialize(decoder).milliseconds.toRealmInstant()

    override fun serialize(encoder: Encoder, value: RealmInstant): Unit =
        serializer.serialize(encoder, value.toDuration().inWholeMilliseconds)
}

internal object ObjectIdSerializer : KSerializer<ObjectId> {
    private val serializer = ByteArraySerializer()

    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun deserialize(decoder: Decoder): ObjectId = ObjectIdImpl(serializer.deserialize(decoder))

    override fun serialize(encoder: Encoder, value: ObjectId): Unit = serializer.serialize(encoder, (value as ObjectIdImpl).bytes)
}

internal object ManagedMutableRealmIntSerializer : KSerializer<MutableRealmInt> {
    private val serializer = Long.serializer()

    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun deserialize(decoder: Decoder): MutableRealmInt =
        MutableRealmInt.create(serializer.deserialize(decoder))

    override fun serialize(encoder: Encoder, value: MutableRealmInt): Unit =
        serializer.serialize(encoder, value.get())
}
