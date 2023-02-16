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

package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.mongodb.HttpLogObfuscator

// FIXME remove this if we ever figure out what's going with this
//  https://github.com/realm/realm-kotlin/issues/1284
internal actual fun getObfuscator(): HttpLogObfuscator = object : HttpLogObfuscator {
    // No-op for Darwin
    override fun obfuscate(input: String): String = input
}
