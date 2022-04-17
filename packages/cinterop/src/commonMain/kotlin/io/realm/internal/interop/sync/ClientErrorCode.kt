package io.realm.internal.interop.sync

/**
 * Wrapper for C-API `realm_app_errno_client`.
 * See https://github.com/realm/realm-core/blob/master/src/realm.h#L2427
 */
expect enum class ClientErrorCode {
    RLM_APP_ERR_CLIENT_USER_NOT_FOUND,
    RLM_APP_ERR_CLIENT_USER_NOT_LOGGED_IN,
    RLM_APP_ERR_CLIENT_APP_DEALLOCATED;

    companion object {
        fun fromInt(nativeValue: Int): ClientErrorCode
    }
}
