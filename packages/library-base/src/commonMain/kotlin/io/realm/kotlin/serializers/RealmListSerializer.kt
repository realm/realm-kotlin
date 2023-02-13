package io.realm.kotlin.serializers

import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.RealmList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

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

public class MutableRealmIntSerializer : KSerializer<MutableRealmInt> {
    override val descriptor: SerialDescriptor
        get() = Long.serializer().descriptor

    override fun deserialize(decoder: Decoder): MutableRealmInt = MutableRealmInt.create(decoder.decodeLong())

    override fun serialize(encoder: Encoder, value: MutableRealmInt) {
        encoder.encodeLong(value.toLong())
    }
}
