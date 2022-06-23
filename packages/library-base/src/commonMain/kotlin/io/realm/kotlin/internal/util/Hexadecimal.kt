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

package io.realm.kotlin.internal.util

private val HEX_CHARS = ('0'..'9') + ('a'..'f')

/**
 * Generates an hexadecimal string representation of the byte array.
 *
 * @return a string representation of [ByteArray] in hexadecimal format.
 */
@Suppress("MagicNumber")
internal fun ByteArray.toHexString(startIndex: Int = 0, endIndex: Int = this.size): String {
    val chars = CharArray((endIndex - startIndex) * 2)

    var i = 0
    for (index in startIndex until endIndex) {
        val byte = this[index].toInt()
        chars[i++] = HEX_CHARS[byte shr 4 and 0xF]
        chars[i++] = HEX_CHARS[byte and 0xF]
    }

    return chars.concatToString()
}

/**
 * Generates a byte array out of a hexadecimal string representation.
 *
 * It does not perform any validation, the String size must be even.
 *
 * @return the [ByteArray] represented by the string.
 */
@Suppress("MagicNumber")
internal fun String.parseHex(): ByteArray {
    val byteArray = ByteArray(length / 2)
    for (i in byteArray.indices) {
        byteArray[i] = substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
    return byteArray
}

internal const val HEX_PATTERN = "[0-9a-fA-F]"
