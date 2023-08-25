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

@file:Suppress("invisible_member", "invisible_reference")

package io.realm.kotlin.test.mongodb.common.internal

import io.realm.kotlin.internal.interop.CoreError
import io.realm.kotlin.internal.interop.ErrorCategory
import io.realm.kotlin.internal.interop.ErrorCode
import io.realm.kotlin.internal.interop.UnknownCodeDescription
import io.realm.kotlin.internal.interop.sync.AppError
import io.realm.kotlin.internal.interop.sync.SyncError
import io.realm.kotlin.mongodb.internal.convertAppError
import io.realm.kotlin.mongodb.internal.convertSyncError
import kotlin.test.Test
import kotlin.test.assertEquals

const val UNMAPPED_CATEGORY_CODE: Int = 0
const val UNMAPPED_ERROR_CODE: Int = -1

class RealmSyncUtilsTest {

    @Test
    fun convertSyncErrorCode_unmappedErrorCode2() {
        val syncException = convertSyncError(
            SyncError(
                CoreError(
                    categoriesNativeValue = ErrorCategory.RLM_ERR_CAT_CLIENT_ERROR.nativeValue,
                    errorCodeNativeValue = UNMAPPED_ERROR_CODE,
                    messageNativeValue = "Placeholder message"
                )
            )
        )

        assertEquals(
            "[Client][Unknown($UNMAPPED_ERROR_CODE)] Placeholder message.",
            syncException.message
        )
    }

    @Test
    fun convertSyncErrorCode_unmappedErrorCategory() {
        val syncException = convertSyncError(
            SyncError(
                CoreError(
                    categoriesNativeValue = UNMAPPED_CATEGORY_CODE,
                    errorCodeNativeValue = UNMAPPED_ERROR_CODE,
                    messageNativeValue = "Placeholder message"
                )
            )
        )

        assertEquals(
            "[$UNMAPPED_CATEGORY_CODE][Unknown($UNMAPPED_ERROR_CODE)] Placeholder message.",
            syncException.message
        )
    }

    // Core also has a concept of an "unknown" error code. It is being reported the same way
    // as a truly unknown code with the description "Unknown(<value>)"
    @Test
    fun convertSyncErrorCode_unknownNativeErrrorCode() {
        val syncException = convertSyncError(
            SyncError(
                CoreError(
                    categoriesNativeValue = ErrorCategory.RLM_ERR_CAT_CLIENT_ERROR.nativeValue,
                    errorCodeNativeValue = ErrorCode.RLM_ERR_UNKNOWN.nativeValue,
                    messageNativeValue = "Placeholder message"
                )
            )
        )

        assertEquals(
            "[Client][Unknown(2000000)] Placeholder message.",
            syncException.message
        )
    }

    @Test
    fun convertAppError_unmappedErrorCode() {
        val appException = convertAppError(
            AppError(
                categoryFlags = ErrorCategory.RLM_ERR_CAT_CUSTOM_ERROR.nativeValue,
                code = UnknownCodeDescription(UNMAPPED_ERROR_CODE),
                message = "Placeholder message",
                httpStatusCode = UNMAPPED_ERROR_CODE,
                linkToServerLog = null
            )
        )

        assertEquals("[Custom][Unknown($UNMAPPED_ERROR_CODE)] Placeholder message.", appException.message)
    }

    @Test
    fun convertAppError_unmappedErrorCategory() {
        val appException = convertAppError(
            AppError(
                categoryFlags = UnknownCodeDescription(UNMAPPED_CATEGORY_CODE).nativeValue,
                code = UnknownCodeDescription(UNMAPPED_ERROR_CODE),
                message = "Placeholder message",
                httpStatusCode = UNMAPPED_ERROR_CODE,
                linkToServerLog = null
            )
        )

        assertEquals(
            "[$UNMAPPED_CATEGORY_CODE][Unknown($UNMAPPED_ERROR_CODE)] Placeholder message.",
            appException.message
        )
    }

    @Test
    fun convertAppError_unmappedErrorCategoryAndErrorCode_noMessage() {
        val appException = convertAppError(
            AppError(
                categoryFlags = UnknownCodeDescription(UNMAPPED_CATEGORY_CODE).nativeValue,
                code = UnknownCodeDescription(UNMAPPED_ERROR_CODE),
                message = null,
                httpStatusCode = UNMAPPED_ERROR_CODE,
                linkToServerLog = null
            )
        )

        assertEquals("[$UNMAPPED_CATEGORY_CODE][Unknown($UNMAPPED_ERROR_CODE)]", appException.message)
    }

    @Test
    fun convertAppError_unmappedErrorCategoryAndErrorCode_linkServerLog() {
        val appException = convertAppError(
            AppError(
                categoryFlags = UnknownCodeDescription(UNMAPPED_CATEGORY_CODE).nativeValue,
                code = UnknownCodeDescription(UNMAPPED_ERROR_CODE),
                message = "Placeholder message",
                httpStatusCode = UNMAPPED_ERROR_CODE,
                linkToServerLog = "http://realm.io"
            )
        )

        assertEquals(
            "[$UNMAPPED_CATEGORY_CODE][Unknown($UNMAPPED_ERROR_CODE)] Placeholder message. Server log entry: http://realm.io",
            appException.message
        )
    }

    @Test
    fun convertAppError_unmappedErrorCategoryAndErrorCode_noMessage_linkServerLog() {
        val appException = convertAppError(
            AppError(
                categoryFlags = UnknownCodeDescription(UNMAPPED_CATEGORY_CODE).nativeValue,
                code = UnknownCodeDescription(UNMAPPED_ERROR_CODE),
                message = null,
                httpStatusCode = UNMAPPED_ERROR_CODE,
                linkToServerLog = "http://realm.io"
            )
        )

        assertEquals(
            "[$UNMAPPED_CATEGORY_CODE][Unknown($UNMAPPED_ERROR_CODE)] Server log entry: http://realm.io",
            appException.message
        )
    }
}
