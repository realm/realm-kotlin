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
import io.realm.kotlin.internal.interop.sync.ClientErrorCode
import io.realm.kotlin.internal.interop.sync.ProtocolConnectionErrorCode
import io.realm.kotlin.internal.interop.sync.SyncErrorCode
import io.realm.kotlin.internal.interop.sync.SyncErrorCodeCategory
import io.realm.kotlin.mongodb.internal.convertAppError
import io.realm.kotlin.mongodb.internal.convertSyncErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals

class RealmSyncUtilsTest {

    @Test
    fun convertSyncErrorCode_unknownErrorCode1() {
        val syncException = convertSyncErrorCode(
            SyncErrorCode(
                category = SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_UNKNOWN.toInt(),
                value = 9999,
                message = "Placeholder message"
            )
        )

        assertEquals("[Unknown][9999] Placeholder message", syncException.message)
    }

    @Test
    fun convertSyncErrorCode_unknownErrorCode2() {
        val syncException = convertSyncErrorCode(
            SyncErrorCode(
                category = SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CONNECTION.toInt(),
                value = 9999,
                message = "Placeholder message"
            )
        )

        assertEquals("[Connection][Unknown(9999)] Placeholder message", syncException.message)
    }

    @Test
    fun convertSyncErrorCode_unknownErrorCategory() {
        val syncException = convertSyncErrorCode(
            SyncErrorCode(
                category = 9999,
                value = ProtocolConnectionErrorCode.RLM_SYNC_ERR_CONNECTION_CONNECTION_CLOSED.toInt(),
                message = "Placeholder message"
            )
        )

        assertEquals("[9999][Unknown(100)] Placeholder message", syncException.message)
    }

    @Test
    fun convertAppError_unknownErrorCode() {
        val appException = convertAppError(
            AppError(
                category = AppErrorCategory.RLM_APP_ERROR_CATEGORY_CUSTOM.toInt(),
                errorCode = 9999,
                message = "Placeholder message",
                httpStatusCode = 9999,
                linkToServerLog = null
            )
        )

        assertEquals("[Custom][Unknown(9999)] Placeholder message.", appException.message)
    }

    @Test
    fun convertAppError_unknownErrorCategory() {
        val appException = convertAppError(
            AppError(
                category = 9999,
                errorCode = ClientErrorCode.RLM_APP_ERR_CLIENT_USER_NOT_FOUND.toInt(),
                message = "Placeholder message",
                httpStatusCode = 9999,
                linkToServerLog = null
            )
        )

        assertEquals("[9999][Unknown(1)] Placeholder message.", appException.message)
    }
}
