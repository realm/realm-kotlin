package io.realm.internal.interop.sync

import realm_wrapper.realm_app_errno_client

actual enum class ClientErrorCode(val nativeValue: realm_app_errno_client) {
    RLM_APP_ERR_CLIENT_USER_NOT_FOUND(realm_wrapper.RLM_APP_ERR_CLIENT_APP_DEALLOCATED),
    RLM_APP_ERR_CLIENT_USER_NOT_LOGGED_IN(realm_wrapper.RLM_APP_ERR_CLIENT_USER_NOT_LOGGED_IN),
    RLM_APP_ERR_CLIENT_APP_DEALLOCATED(realm_wrapper.RLM_APP_ERR_CLIENT_APP_DEALLOCATED);

    actual companion object {
        actual fun fromInt(nativeValue: Int): ClientErrorCode {
            for (value in values()) {
                if (value.nativeValue.toInt() == nativeValue) {
                    return value
                }
            }
            error("Unknown client error code: $nativeValue")
        }

        internal fun of(nativeValue: realm_app_errno_client): ClientErrorCode {
            for (value in values()) {
                if (value.nativeValue == nativeValue) {
                    return value
                }
            }
            error("Unknown client error code: $nativeValue")
        }
    }
}
