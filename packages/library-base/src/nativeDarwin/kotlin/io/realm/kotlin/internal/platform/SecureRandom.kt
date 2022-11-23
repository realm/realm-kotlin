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

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

// https://developer.apple.com/documentation/security/randomization_services
internal actual fun fillRandomBytes(array: ByteArray) {
    if (array.isEmpty()) return

    array.usePinned { pin ->
        val ptr = pin.addressOf(0)
        val status = SecRandomCopyBytes(kSecRandomDefault, array.size.convert(), ptr)
        if (status != errSecSuccess) {
            error("Error filling random bytes. errorCode=$status")
        }
    }
}

internal actual fun seedExtraRandomBytes(array: ByteArray) {
    if (array.isEmpty()) return

    try {
        array.usePinned { pin ->
            val ptr = pin.addressOf(0)
            val file = fopen("/dev/urandom", "wb")
            if (file != null) {
                fwrite(ptr, 1.convert(), array.size.convert(), file)
                for (n in 0 until array.size) array[n] = ptr[n]
                fclose(file)
            }
        }
    } catch (e: Throwable) {
        e.printStackTrace()
    }
}
