package io.realm.kotlin.internal.interop

/**
 * Wrapper for C-API `realm_error_t`.
 * See https://github.com/realm/realm-core/blob/master/src/realm.h#L231
 */
class CoreError(
    categoriesNativeValue: Int,
    val errorCodeNativeValue: Int,
    messageNativeValue: String?,
) {
    val categories: CategoryFlags = CategoryFlags((categoriesNativeValue))
    val errorCode: ErrorCode? = ErrorCode.of(errorCodeNativeValue)
    val message = messageNativeValue

    operator fun contains(category: ErrorCategory): Boolean = category in categories
}

data class CategoryFlags(val categoryFlags: Int) {

    companion object {
        /**
         * See error code mapping to categories here:
         * https://github.com/realm/realm-core/blob/master/src/realm/error_codes.cpp#L29
         *
         * In most cases, only 1 category is assigned, but some errors have multiple. So instead of
         * overwhelming the user with many categories, we only select the most important to show
         * in the error message. "important" is of course tricky to define, but generally
         * we consider vague categories like [ErrorCategory.RLM_ERR_CAT_RUNTIME] as less important
         * than more specific ones like [ErrorCategory.RLM_ERR_CAT_JSON_ERROR].
         *
         * In the current implementation, categories between index 0 and 7 are considered equal
         * and the order is somewhat arbitrary. No error codes has multiple of these categories
         * associated either.
         */
        val CATEGORY_ORDER: List<ErrorCategory> = listOf(
            ErrorCategory.RLM_ERR_CAT_SYSTEM_ERROR,
            ErrorCategory.RLM_ERR_CAT_FILE_ACCESS,
            ErrorCategory.RLM_ERR_CAT_INVALID_ARG,
            ErrorCategory.RLM_ERR_CAT_LOGIC,
            ErrorCategory.RLM_ERR_CAT_RUNTIME,
        )
    }

    /**
     * Returns a description of the most important category defined in [categoryFlags].
     * If no known categories are found, the integer values for all the categories is returned
     * as debugging information.
     */
    val description: String = CATEGORY_ORDER.firstOrNull { category ->
        this.contains(category)
    }?.description ?: "$categoryFlags"

    /**
     * Check whether a given [ErrorCategory] is included in the [categoryFlags].
     */
    operator fun contains(category: ErrorCategory): Boolean = (categoryFlags and category.nativeValue) != 0
}
