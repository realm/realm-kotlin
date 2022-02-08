/*
 * Copyright 2021 Realm Inc.
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
package io.realm.internal.interop

/**
 * Wrapper around Core Timestamp values.
 * See https://github.com/realm/realm-core/blob/master/src/realm/timestamp.hpp for more information
 */
interface Timestamp {
    val seconds: Long
    val nanoSeconds: Int
}

// Implementation that should only be used within the cinterop module.
internal data class TimestampImpl(override val seconds: Long, override val nanoSeconds: Int) : Timestamp
