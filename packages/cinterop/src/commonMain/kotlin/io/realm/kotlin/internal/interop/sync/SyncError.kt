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

package io.realm.kotlin.internal.interop.sync

import io.realm.kotlin.internal.interop.CoreError

/**
 * Wrapper for C-API `realm_sync_error`.
 * See https://github.com/realm/realm-core/blob/master/src/realm.h#L3321
 */
data class SyncError constructor(
    val errorCode: CoreError,
    val originalFilePath: String?,
    val recoveryFilePath: String?,
    val isFatal: Boolean,
    val isUnrecognizedByClient: Boolean,
    val isClientResetRequested: Boolean,
    val compensatingWrites: Array<CoreCompensatingWriteInfo>
) {
    // Constructs an SyncError out from a simple code. There are some situations (SyncSessionTransferCompletionCallback)
    // where we receive an error code rather than a full SyncErrorCode, wrapping the code
    // simplifies the error handling logic.
    constructor(
        error: CoreError
    ) : this(
        errorCode = error,
        originalFilePath = null,
        recoveryFilePath = null,
        isFatal = false,
        isUnrecognizedByClient = false,
        isClientResetRequested = false,
        compensatingWrites = emptyArray()
    )

    // Constructor used by JNI so we avoid creating too many objects on the JNI side.
    constructor(
        categoryFlags: Int,
        value: Int,
        message: String,
        originalFilePath: String?,
        recoveryFilePath: String?,
        isFatal: Boolean,
        isUnrecognizedByClient: Boolean,
        isClientResetRequested: Boolean,
        compensatingWrites: Array<CoreCompensatingWriteInfo>
    ) : this(
        CoreError(categoryFlags, value, message),
        originalFilePath,
        recoveryFilePath,
        isFatal,
        isUnrecognizedByClient,
        isClientResetRequested,
        compensatingWrites
    )
}
