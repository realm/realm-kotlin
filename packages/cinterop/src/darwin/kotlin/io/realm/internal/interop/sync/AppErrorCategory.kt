package io.realm.internal.interop.sync

import realm_wrapper.realm_app_error_category

actual enum class AppErrorCategory(val nativeValue: realm_app_error_category) {
    RLM_APP_ERROR_CATEGORY_HTTP(realm_app_error_category.RLM_APP_ERROR_CATEGORY_HTTP),
    RLM_APP_ERROR_CATEGORY_JSON(realm_app_error_category.RLM_APP_ERROR_CATEGORY_JSON),
    RLM_APP_ERROR_CATEGORY_CLIENT(realm_app_error_category.RLM_APP_ERROR_CATEGORY_CLIENT),
    RLM_APP_ERROR_CATEGORY_SERVICE(realm_app_error_category.RLM_APP_ERROR_CATEGORY_SERVICE),
    RLM_APP_ERROR_CATEGORY_CUSTOM(realm_app_error_category.RLM_APP_ERROR_CATEGORY_CUSTOM);

    companion object {
        fun of(nativeValue: realm_app_error_category): AppErrorCategory {
            for (value in values()) {
                if (value.nativeValue == nativeValue) {
                    return value
                }
            }
            error("Unknown app error category: $nativeValue")
        }
    }
}
