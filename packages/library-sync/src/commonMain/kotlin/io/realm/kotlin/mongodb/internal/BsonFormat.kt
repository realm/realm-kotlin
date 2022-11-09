package io.realm.kotlin.mongodb.internal

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.SerializersModule
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.BsonValue

internal interface BsonFormat {
    /**
     * Serializes and encodes the given [value] to string using the given [serializer].
     *
     * @throws SerializationException in case of any encoding-specific error
     * @throws IllegalArgumentException if the encoded input does not comply format's specification
     */
    fun <T> encodeToBsonValue(
        serializer: SerializationStrategy<T>,
        value: T
    ): BsonValue

    /**
     * Decodes and deserializes the given [bsonValue] to the value of type [T] using the given [deserializer].
     *
     * @throws SerializationException in case of any decoding-specific error
     * @throws IllegalArgumentException if the decoded input is not a valid instance of [T]
     */
    fun <T> decodeFromBsonValue(
        deserializer: DeserializationStrategy<T>,
        bsonValue: BsonValue
    ): T
}

public object Bson : BsonFormat {
    override fun <T> encodeToBsonValue(serializer: SerializationStrategy<T>, value: T): BsonValue {
        TODO("Not yet implemented")
    }

    override fun <T> decodeFromBsonValue(
        deserializer: DeserializationStrategy<T>,
        bsonValue: BsonValue
    ): T = deserializer.deserialize(BsonValueDecoder(this, bsonValue))
}

@OptIn(ExperimentalSerializationApi::class)
private class BsonMapDecoder(
    val bson: Bson,
    val value: BsonDocument
): CompositeDecoder {
    private val keys = value.keys.toList()
    private val size: Int = keys.size
    private var position = -1

    override val serializersModule: SerializersModule
        get() = TODO("Not yet implemented")

    override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int): Boolean {
        TODO("Not yet implemented")
    }

    override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte {
        TODO("Not yet implemented")
    }

    override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char {
        TODO("Not yet implemented")
    }

    override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double {
        TODO("Not yet implemented")
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        while (position < size - 1) {
            position++
            return position
        }
        return CompositeDecoder.DECODE_DONE
    }

    override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float {
        return value[descriptor.getElementName(0)]!!.asDouble().value.toFloat()
    }

    override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder {
        TODO("Not yet implemented")
    }

    override fun decodeIntElement(descriptor: SerialDescriptor, index: Int): Int {
        TODO("Not yet implemented")
    }

    override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long {
        TODO("Not yet implemented")
    }

    @ExperimentalSerializationApi
    override fun <T : Any> decodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T?>,
        previousValue: T?
    ): T? {
        TODO("Not yet implemented")
    }

    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?
    ): T {
        TODO("Not yet implemented")
    }

    override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short {
        TODO("Not yet implemented")
    }

    override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String {
        TODO("Not yet implemented")
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        // Do nothing
    }
}

private class BsonValueDecoder(
    val bson: Bson,
    val value: BsonValue
) : Decoder {
    override val serializersModule: SerializersModule
        get() = TODO()

    // Decoder
    @OptIn(ExperimentalSerializationApi::class)
    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
       return when(descriptor.kind) {
            StructureKind.LIST -> TODO("Missing list")
            StructureKind.MAP -> TODO("Missing map")
            else -> BsonMapDecoder(bson, value.asDocument())
        }
    }

    override fun decodeBoolean(): Boolean {
        TODO("Not yet implemented")
    }

    override fun decodeByte(): Byte {
        TODO("Not yet implemented")
    }

    override fun decodeChar(): Char {
        TODO("Not yet implemented")
    }

    override fun decodeDouble(): Double = value.asDouble().value

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        TODO("Not yet implemented")
    }

    override fun decodeFloat(): Float = value.asDouble().value.toFloat()

    override fun decodeInline(descriptor: SerialDescriptor): Decoder {
        TODO("Not yet implemented")
    }

    override fun decodeInt(): Int {
        TODO("Not yet implemented")
    }

    override fun decodeLong(): Long {
        TODO("Not yet implemented")
    }

    @ExperimentalSerializationApi
    override fun decodeNotNullMark(): Boolean {
        TODO("Not yet implemented")
    }

    @ExperimentalSerializationApi
    override fun decodeNull(): Nothing? {
        TODO("Not yet implemented")
    }

    override fun decodeShort(): Short {
        TODO("Not yet implemented")
    }

    override fun decodeString(): String {
        TODO("Not yet implemented")
    }
}

internal interface BsonDecoder : Decoder, CompositeDecoder {
    val bson: Bson
    fun decodeBsonElement(): BsonValue
}