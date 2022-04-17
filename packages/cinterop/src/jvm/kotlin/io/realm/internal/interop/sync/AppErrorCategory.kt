package io.realm.internal.interop.sync

import io.realm.internal.interop.NativeEnumerated
import io.realm.internal.interop.realm_app_error_category_e

actual enum class AppErrorCategory(override val nativeValue: Int) : NativeEnumerated {
    RLM_APP_ERROR_CATEGORY_HTTP(realm_app_error_category_e.RLM_APP_ERROR_CATEGORY_HTTP),
    RLM_APP_ERROR_CATEGORY_JSON(realm_app_error_category_e.RLM_APP_ERROR_CATEGORY_JSON),
    RLM_APP_ERROR_CATEGORY_CLIENT(realm_app_error_category_e.RLM_APP_ERROR_CATEGORY_CLIENT),
    RLM_APP_ERROR_CATEGORY_SERVICE(realm_app_error_category_e.RLM_APP_ERROR_CATEGORY_SERVICE),
    RLM_APP_ERROR_CATEGORY_CUSTOM(realm_app_error_category_e.RLM_APP_ERROR_CATEGORY_CUSTOM);

    companion object {
        @JvmStatic
        fun of(nativeValue: Int): AppErrorCategory {
            for (value in values()) {
                if (value.nativeValue == nativeValue) {
                    return value
                }
            }
            error("Unknown app error category value: $nativeValue")
        }
    }
}
