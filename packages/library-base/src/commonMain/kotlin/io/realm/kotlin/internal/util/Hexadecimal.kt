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
 * @return a string representation of [ByteArray] in hexadecimal format
 */
@Suppress("MagicNumber")
internal fun ByteArray.toHexString(): String {
    val chars = CharArray(size * 2)
    var i = 0
    for (b in this) {
        chars[i++] = HEX_CHARS[b.toInt() shr 4 and 0xF]
        chars[i++] = HEX_CHARS[b.toInt() and 0xF]
    }
    return chars.concatToString()
}
