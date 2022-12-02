/*
 * Copyright 2022 Realm Inc.
 * Copyright 2017-2019 Carlos Ballesteros Velasco and contributors
    * https://github.com/korlibs/korge/graphs/contributors
    * https://github.com/korlibs-archive/
 * All rights reserved.

 * MIT License

 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.

 * See: https://github.com/korlibs/korge/tree/main/krypto
*/

package io.realm.kotlin.internal.platform

import kotlin.random.Random

internal expect fun fillRandomBytes(array: ByteArray)

@Suppress("MagicNumber")
internal object SecureRandom : Random() {
    private fun getInt(): Int {
        val temp = ByteArray(4)
        fillRandomBytes(temp)
        val a = temp[0].toInt() and 0xFF
        val b = temp[1].toInt() and 0xFF
        val c = temp[2].toInt() and 0xFF
        val d = temp[3].toInt() and 0xFF
        return (a shl 24) or (b shl 16) or (c shl 8) or (d shl 0)
    }

    override fun nextBytes(array: ByteArray, fromIndex: Int, toIndex: Int): ByteArray {
        val random = ByteArray(toIndex - fromIndex)
        fillRandomBytes(random)
        random.copyInto(array, fromIndex, 0, random.size)
        return array
    }

    override fun nextBits(bitCount: Int): Int {
        /** Takes upper [bitCount] bits (0..32) from random integer.
         *  See: https://github.com/JetBrains/kotlin/blob/fe81ad0bbe413af2b0485afec4ff0317b7b27888/
         *  libraries/stdlib/jvm/src/kotlin/random/PlatformRandom.kt#L39
         **/
        return getInt().ushr(32 - bitCount) and (-bitCount).shr(31)
    }
}
