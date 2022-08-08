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

package io.realm.kotlin.test.mongodb.shared.internal

import io.realm.kotlin.internal.interop.sync.AppError
import io.realm.kotlin.internal.interop.sync.AppErrorCategory
import io.realm.kotlin.internal.interop.sync.SyncErrorCode
import io.realm.kotlin.internal.interop.sync.SyncErrorCodeCategory
import io.realm.kotlin.mongodb.internal.convertAppError
import io.realm.kotlin.mongodb.internal.convertSyncErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals

const val UNMAPPED_CODE: Int = -1

// This is a placeholder with no actual use
const val MAPPED_CODE: Int = 0

class RealmSyncUtilsTest {
    @Test
    fun convertSyncErrorCode_unmappedErrorCode_categoryTypeUnknown() {
        val syncException = convertSyncErrorCode(
            SyncErrorCode(
                category = SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_UNKNOWN,
                error = null,
                categoryCode = MAPPED_CODE,
                errorCode = UNMAPPED_CODE,
                message = "Placeholder message"
            )
        )

        assertEquals("[Unknown][$UNMAPPED_CODE] Placeholder message.", syncException.message)
    }

    @Test
    fun convertSyncErrorCode_unmappedErrorCode2() {
        val syncException = convertSyncErrorCode(
            SyncErrorCode(
                category = SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CONNECTION,
                error = null,
                categoryCode = MAPPED_CODE,
                errorCode = UNMAPPED_CODE,
                message = "Placeholder message"
            )
        )

        assertEquals("[Connection][Unknown($UNMAPPED_CODE)] Placeholder message.", syncException.message)
    }

    @Test
    fun convertSyncErrorCode_unmappedErrorCategory() {
        val syncException = convertSyncErrorCode(
            SyncErrorCode(
                category = null,
                error = null,
                categoryCode = UNMAPPED_CODE,
                errorCode = UNMAPPED_CODE,
                message = "Placeholder message"
            )
        )

        assertEquals("[$UNMAPPED_CODE][Unknown($UNMAPPED_CODE)] Placeholder message.", syncException.message)
    }

    @Test
    fun convertAppError_unmappedErrorCode() {
        val appException = convertAppError(
            AppError(
                category = AppErrorCategory.RLM_APP_ERROR_CATEGORY_CUSTOM,
                error = null,
                categoryCode = MAPPED_CODE,
                errorCode = UNMAPPED_CODE,
                message = "Placeholder message",
                httpStatusCode = UNMAPPED_CODE,
                linkToServerLog = null
            )
        )

        assertEquals("[Custom][Unknown($UNMAPPED_CODE)] Placeholder message.", appException.message)
    }

    @Test
    fun convertAppError_unmappedErrorCategory() {
        val appException = convertAppError(
            AppError(
                category = null,
                error = null,
                categoryCode = UNMAPPED_CODE,
                errorCode = UNMAPPED_CODE,
                message = "Placeholder message",
                httpStatusCode = UNMAPPED_CODE,
                linkToServerLog = null
            )
        )

        assertEquals("[$UNMAPPED_CODE][Unknown($UNMAPPED_CODE)] Placeholder message.", appException.message)
    }

    @Test
    fun convertAppError_unmappedErrorCategoryAndErrorCode_noMessage() {
        val appException = convertAppError(
            AppError(
                category = null,
                error = null,
                categoryCode = UNMAPPED_CODE,
                errorCode = UNMAPPED_CODE,
                message = null,
                httpStatusCode = UNMAPPED_CODE,
                linkToServerLog = null
            )
        )

        assertEquals("[$UNMAPPED_CODE][Unknown($UNMAPPED_CODE)]", appException.message)
    }

    @Test
    fun convertAppError_unmappedErrorCategoryAndErrorCode_linkServerLog() {
        val appException = convertAppError(
            AppError(
                category = null,
                error = null,
                categoryCode = UNMAPPED_CODE,
                errorCode = UNMAPPED_CODE,
                message = "Placeholder message",
                httpStatusCode = UNMAPPED_CODE,
                linkToServerLog = "http://realm.io"
            )
        )

        assertEquals("[$UNMAPPED_CODE][Unknown($UNMAPPED_CODE)] Placeholder message. Server log entry: http://realm.io", appException.message)
    }

    @Test
    fun convertAppError_unmappedErrorCategoryAndErrorCode_noMessage_linkServerLog() {
        val appException = convertAppError(
            AppError(
                category = null,
                error = null,
                categoryCode = UNMAPPED_CODE,
                errorCode = UNMAPPED_CODE,
                message = null,
                httpStatusCode = UNMAPPED_CODE,
                linkToServerLog = "http://realm.io"
            )
        )

        assertEquals("[$UNMAPPED_CODE][Unknown($UNMAPPED_CODE)] Server log entry: http://realm.io", appException.message)
    }
}
