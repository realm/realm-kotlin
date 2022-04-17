package io.realm.internal.interop.sync

/**
 * Wrapper for C-API `realm_app_error_category`.
 * See https://github.com/realm/realm-core/blob/master/src/realm.h#L2396
 */
expect enum class AppErrorCategory {
    RLM_APP_ERROR_CATEGORY_HTTP,
    RLM_APP_ERROR_CATEGORY_JSON,
    RLM_APP_ERROR_CATEGORY_CLIENT,
    RLM_APP_ERROR_CATEGORY_SERVICE,
    RLM_APP_ERROR_CATEGORY_CUSTOM;
}
