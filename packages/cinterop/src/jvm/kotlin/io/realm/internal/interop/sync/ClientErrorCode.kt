package io.realm.internal.interop.sync

import io.realm.internal.interop.NativeEnumerated
import io.realm.internal.interop.realm_app_errno_client_e

actual enum class ClientErrorCode(override val nativeValue: Int) : NativeEnumerated {
    RLM_APP_ERR_CLIENT_USER_NOT_FOUND(realm_app_errno_client_e.RLM_APP_ERR_CLIENT_APP_DEALLOCATED),
    RLM_APP_ERR_CLIENT_USER_NOT_LOGGED_IN(realm_app_errno_client_e.RLM_APP_ERR_CLIENT_USER_NOT_LOGGED_IN),
    RLM_APP_ERR_CLIENT_APP_DEALLOCATED(realm_app_errno_client_e.RLM_APP_ERR_CLIENT_APP_DEALLOCATED);

    actual companion object {
        actual fun fromInt(nativeValue: Int): ClientErrorCode {
            for (value in values()) {
                if (value.nativeValue == nativeValue) {
                    return value
                }
            }
            error("Unknown client error code: $nativeValue")
        }

        internal fun of(nativeValue: Int): ClientErrorCode {
            for (value in values()) {
                if (value.nativeValue == nativeValue) {
                    return value
                }
            }
            error("Unknown client error code: $nativeValue")
        }
    }
}
