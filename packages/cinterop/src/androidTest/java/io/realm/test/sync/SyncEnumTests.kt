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

package io.realm.test.sync

import io.realm.internal.interop.realm_app_errno_client_e
import io.realm.internal.interop.realm_app_errno_service_e
import io.realm.internal.interop.realm_app_error_category_e
import io.realm.internal.interop.realm_auth_provider_e
import io.realm.internal.interop.realm_sync_client_metadata_mode_e
import io.realm.internal.interop.realm_sync_errno_client_e
import io.realm.internal.interop.realm_sync_errno_connection_e
import io.realm.internal.interop.realm_sync_errno_session_e
import io.realm.internal.interop.realm_sync_error_category_e
import io.realm.internal.interop.realm_user_state_e
import io.realm.internal.interop.sync.AppErrorCategory
import io.realm.internal.interop.sync.AuthProvider
import io.realm.internal.interop.sync.ClientErrorCode
import io.realm.internal.interop.sync.CoreUserState
import io.realm.internal.interop.sync.MetadataMode
import io.realm.internal.interop.sync.ProtocolClientErrorCode
import io.realm.internal.interop.sync.ProtocolConnectionErrorCode
import io.realm.internal.interop.sync.ProtocolSessionErrorCode
import io.realm.internal.interop.sync.ServiceErrorCode
import io.realm.internal.interop.sync.SyncErrorCodeCategory
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

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
    fun appErrorCategory() {
        checkEnum(realm_app_error_category_e::class) { nativeValue ->
            AppErrorCategory.of(nativeValue)
        }
    }

    @Test
    fun authProvider() {
        checkEnum(realm_auth_provider_e::class) { nativeValue ->
            AuthProvider.of(nativeValue)
        }
    }

    @Test
    fun clientErrorCode() {
        checkEnum(realm_app_errno_client_e::class) { nativeValue ->
            ClientErrorCode.fromInt(nativeValue)
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
    fun protocolClientErrorCode() {
        checkEnum(realm_sync_errno_client_e::class) { nativeValue ->
            ProtocolClientErrorCode.fromInt(nativeValue)
        }
    }

    @Test
    fun protocolConnectionErrorCode() {
        checkEnum(realm_sync_errno_connection_e::class) { nativeValue ->
            ProtocolConnectionErrorCode.fromInt(nativeValue)
        }
    }

    @Test
    fun protocolSessionErrorCode() {
        checkEnum(realm_sync_errno_session_e::class) { nativeValue ->
            ProtocolSessionErrorCode.fromInt(nativeValue)
        }
    }

    @Test
    fun serviceErrorCode() {
        checkEnum(realm_app_errno_service_e::class) { nativeValue ->
            ServiceErrorCode.fromInt(nativeValue)
        }
    }

    @Test
    fun syncErrorCodeCategory() {
        checkEnum(realm_sync_error_category_e::class) { nativeValue ->
            SyncErrorCodeCategory.of(nativeValue)
        }
    }

    private inline fun <T : Any> checkEnum(enumClass: KClass<out Any>, mapNativeValue: (Int) -> T) {
        // Fetch all native values
        val coreNativeValues: IntArray = enumClass.java.fields
            .map { it.getInt(null) }
            .toIntArray()

        // Find all enums mapping to those values
        val mappedKotlinEnums = coreNativeValues
            .map { mapNativeValue(it) }
            .toSet()

        // Validate we have a different enum defined for each core native value.
        // Note, we cannot check that the name is mapped correctly, but the chance
        // of that happening should be really low and we will catch all the common
        // cases of adding, removing and renaming enums.
        assertEquals(coreNativeValues.size, mappedKotlinEnums.size)
    }
}
