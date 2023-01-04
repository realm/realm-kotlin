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

package io.realm.kotlin.internal

import io.realm.kotlin.exceptions.RealmException
import io.realm.kotlin.internal.interop.CoreError
import io.realm.kotlin.internal.interop.CoreErrorConverter
import io.realm.kotlin.internal.interop.sync.ErrorCategory
import io.realm.kotlin.internal.interop.sync.ErrorCode

/**
 * This class is a work-around for `cinterop` not being able to access `library-base` and
 * `library-sync` types, which is e.g. problematic when it comes to exceptions which break the
 * normal event flow.
 *
 * This class works around this by providing a way for `library-base` to install delegates
 * in `cinterop`. Then `cinterop` can use it to the public API to map internal errors when needed.
 * This works, but require that we can install the delegate at an appropriate time.
 *
 * Such a single point in time doesn't really exist as we don't have a public `Realm.init()` like
 * in Realm Java, so instead we need to make sure that all API entry points initializes this
 * class before touching any native code.
 *
 * With the current API, these entry points could be narrowed down to configuration classes
 * since they are prerequisite for interacting with any other Realm API
 *
 * - Configuration (for both RealmConfiguration and SyncConfiguration)
 * - AppConfiguration
 *
 * In theory, it it possible to start using our types: `RealmInstant`, `ObjectId`, etc., but before
 * a Realm is opened all of these will go through _unmanaged_ code paths so should be safe.
 *
 * @see io.realm.kotlin.internal.interop.CoreErrorConverterobject
 */
public object RealmInteropBridge {

    /**
     * This must be called before any calls to `io.realm.kotlin.internal.interop.RealmInterop`.
     * Failing to do so will result in unspecified behaviour.
     */
    public fun initialize() {
        CoreExceptionConverter.initialize()
    }
}

/**
 * Class for mapping between core exception types and public exception types. This works in two
 * ways:
 *
 * 1. It installs a delegate in `cinterop`, allowing `cinterop` to delegate the mapping if types
 *    to `library-base`, which have access to the public API types.
 *
 * 2. It exposes a method `CoreExceptionConverter.convertToPublicException` which allows code in
 *    `library-base` to convert exceptions from cinterop to something more appropriate. The reason
 *     being that sometimes cinterop doesn't have the full context in terms of what exception to
 *     throw, so using this method, allows specific methods to replace the exception being thrown.
 *     Like throwing `IllegalArgumentException` instead of `RealmException`.
 */
public object CoreExceptionConverter {

    public fun initialize() {
        // Just wrap all core exceptions in a public RealmException for now, we should be able t
        // throw subclasses of this without i being a breaking change.
        CoreErrorConverter.initialize { cause: CoreError ->
            with(cause.category) {
                if (cause.errorCode == ErrorCode.RLM_ERR_INDEX_OUT_OF_BOUNDS) IndexOutOfBoundsException(cause.message)
                else if(hasFlag(ErrorCategory.RLM_ERR_CAT_INVALID_ARG)) IllegalArgumentException(cause.message)
                else if(hasFlag(ErrorCategory.RLM_ERR_CAT_LOGIC) || hasFlag(ErrorCategory.RLM_ERR_CAT_RUNTIME)) IllegalStateException(cause.message)
                else RealmException(cause.message)
            }
        }
    }
}
