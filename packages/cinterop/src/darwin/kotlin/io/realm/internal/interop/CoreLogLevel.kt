package io.realm.internal.interop

actual enum class CoreLogLevel(private val internalPriority: Int) {
    RLM_LOG_LEVEL_ALL(realm_wrapper.RLM_LOG_LEVEL_ALL.toInt()),
    RLM_LOG_LEVEL_TRACE(realm_wrapper.RLM_LOG_LEVEL_TRACE.toInt()),
    RLM_LOG_LEVEL_DEBUG(realm_wrapper.RLM_LOG_LEVEL_DEBUG.toInt()),
    RLM_LOG_LEVEL_DETAIL(realm_wrapper.RLM_LOG_LEVEL_DETAIL.toInt()),
    RLM_LOG_LEVEL_INFO(realm_wrapper.RLM_LOG_LEVEL_INFO.toInt()),
    RLM_LOG_LEVEL_WARNING(realm_wrapper.RLM_LOG_LEVEL_WARNING.toInt()),
    RLM_LOG_LEVEL_ERROR(realm_wrapper.RLM_LOG_LEVEL_ERROR.toInt()),
    RLM_LOG_LEVEL_FATAL(realm_wrapper.RLM_LOG_LEVEL_FATAL.toInt()),
    RLM_LOG_LEVEL_OFF(realm_wrapper.RLM_LOG_LEVEL_OFF.toInt());

    actual val priority: Int
        get() = internalPriority
}
