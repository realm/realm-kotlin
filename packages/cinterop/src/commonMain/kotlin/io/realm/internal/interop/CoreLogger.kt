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
 * Top-level logger interface used to expose basic functionality to the interop layer. This is
 * necessary, for example, in the Kotlin/Native layer as a logger implemented there can only access
 * this interface and not the SDK-level interface `RealmLogger`.
 */
interface CoreLogger {

    /**
     * Needed by Kotlin/Native loggers to be able to communicate the level to the C-API.
     */
    val coreLogLevel: CoreLogLevel

    fun log(level: Short, message: String)
}
