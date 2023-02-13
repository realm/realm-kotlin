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

package io.realm.kotlin.mongodb

import io.realm.kotlin.mongodb.internal.HttpLogObfuscatorImpl

/**
 * The `HttpLogObfuscator` keeps sensitive information from being displayed in output traces.
 */
public interface HttpLogObfuscator {
    /**
     * Obfuscates a log entry depending on whether the data being sent contains information deemed
     * sensitive. The following information is considered sensitive data:
     * - passwords
     * - tokens of any kind - see [Credentials] for more information
     * - custom function arguments - see [Functions] for more information
     *
     * @param input the original log entry to be obfuscated.
     * @return the log entry to be shown
     */
    public fun obfuscate(input: String): String

    public companion object {
        /**
         * Creates an obfuscator which removes sensitive data related to login operations. This
         * is the obfuscator used by default by [AppConfiguration].
         */
        public fun create(): HttpLogObfuscator = HttpLogObfuscatorImpl()
    }
}
