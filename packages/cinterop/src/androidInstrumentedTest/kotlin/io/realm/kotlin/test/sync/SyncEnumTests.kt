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

package io.realm.kotlin.test.sync

import io.realm.kotlin.internal.interop.CategoryFlags
import io.realm.kotlin.internal.interop.ErrorCategory
import io.realm.kotlin.internal.interop.ErrorCode
import io.realm.kotlin.internal.interop.realm_auth_provider_e
import io.realm.kotlin.internal.interop.realm_errno_e
import io.realm.kotlin.internal.interop.realm_error_category_e
import io.realm.kotlin.internal.interop.realm_sync_client_metadata_mode_e
import io.realm.kotlin.internal.interop.realm_sync_connection_state_e
import io.realm.kotlin.internal.interop.realm_sync_errno_connection_e
import io.realm.kotlin.internal.interop.realm_sync_errno_session_e
import io.realm.kotlin.internal.interop.realm_sync_session_resync_mode_e
import io.realm.kotlin.internal.interop.realm_sync_session_state_e
import io.realm.kotlin.internal.interop.realm_user_state_e
import io.realm.kotlin.internal.interop.realm_web_socket_errno_e
import io.realm.kotlin.internal.interop.sync.AuthProvider
import io.realm.kotlin.internal.interop.sync.CoreConnectionState
import io.realm.kotlin.internal.interop.sync.CoreSyncSessionState
import io.realm.kotlin.internal.interop.sync.CoreUserState
import io.realm.kotlin.internal.interop.sync.MetadataMode
import io.realm.kotlin.internal.interop.sync.SyncConnectionErrorCode
import io.realm.kotlin.internal.interop.sync.SyncSessionErrorCode
import io.realm.kotlin.internal.interop.sync.SyncSessionResyncMode
import io.realm.kotlin.internal.interop.sync.WebsocketErrorCode
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Test that sync related enum wrappers map all values, which is relevant when the Core API changes.
 * This test is isolated to the JVM as Native doesn't have the reflection capabilities required
 * to test this efficiently.
 */
class SyncEnumTests {

    @BeforeTest
    fun setup() {
        System.loadLibrary("realmc")
    }

    @Test
    fun errorCategory() {
        checkEnum(realm_error_category_e::class) { nativeValue ->
            ErrorCategory.of(nativeValue)
        }
        assertEquals(ErrorCategory.values().size, CategoryFlags.CATEGORY_ORDER.size)
    }

    @Test
    fun authProvider() {
        checkEnum(realm_auth_provider_e::class) { nativeValue ->
            AuthProvider.of(nativeValue)
        }
    }

    @Test
    fun clientErrorCode() {
        checkEnum(realm_errno_e::class) { nativeValue ->
            ErrorCode.of(nativeValue)
        }
    }

    @Test
    fun coreUserState() {
        checkEnum(realm_user_state_e::class) { nativeValue ->
            CoreUserState.of(nativeValue)
        }
    }

    @Test
    fun metadataMode() {
        checkEnum(realm_sync_client_metadata_mode_e::class) { nativeValue ->
            MetadataMode.of(nativeValue)
        }
    }

    @Test
    fun syncConnectionErrorCode() {
        checkEnum(realm_sync_errno_connection_e::class) { nativeValue ->
            SyncConnectionErrorCode.of(nativeValue)
        }
    }

    @Test
    fun syncSessionErrorCode() {
        checkEnum(realm_sync_errno_session_e::class) { nativeValue ->
            SyncSessionErrorCode.of(nativeValue)
        }
    }

    @Test
    fun websocketErrorCode() {
        checkEnum(realm_web_socket_errno_e::class) { nativeValue ->
            WebsocketErrorCode.of(nativeValue)
        }
    }

    @Test
    fun syncSessionResyncMode() {
        checkEnum(realm_sync_session_resync_mode_e::class) { nativeValue ->
            SyncSessionResyncMode.fromInt(nativeValue)
        }
    }

    @Test
    fun syncSessionState() {
        checkEnum(realm_sync_session_state_e::class) { nativeValue ->
            CoreSyncSessionState.of(nativeValue)
        }
    }

    @Test
    fun syncSessionConnectionState() {
        checkEnum(realm_sync_connection_state_e::class) { nativeValue ->
            CoreConnectionState.of(nativeValue)
        }
    }

    private inline fun <T : Any> checkEnum(
        enumClass: KClass<out Any>,
        mapNativeValue: (Int) -> T?
    ) {
        // Fetch all native values
        val coreNativeValues: IntArray = enumClass.java.fields
            .map { it.getInt(null) }
            .toIntArray()

        // Find all enums mapping to those values
        val mappedKotlinEnums: Set<T> = coreNativeValues
            .map {
                mapNativeValue(it) ?: fail("${enumClass.simpleName}: unmapped native value $it")
            }
            .toSet()

        // Validate we have a different enum defined for each core native value.
        // Note, we cannot check that the name is mapped correctly, but the chance
        // of that happening should be really low and we will catch all the common
        // cases of adding, removing and renaming enums.
        assertEquals(coreNativeValues.size, mappedKotlinEnums.size)
    }
}
