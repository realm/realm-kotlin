package io.realm.internal.interop

actual enum class CoreLogLevel(private val internalPriority: Int) {
    RLM_LOG_LEVEL_ALL(realm_log_level_e.RLM_LOG_LEVEL_ALL),
    RLM_LOG_LEVEL_TRACE(realm_log_level_e.RLM_LOG_LEVEL_TRACE),
    RLM_LOG_LEVEL_DEBUG(realm_log_level_e.RLM_LOG_LEVEL_DEBUG),
    RLM_LOG_LEVEL_DETAIL(realm_log_level_e.RLM_LOG_LEVEL_DETAIL),
    RLM_LOG_LEVEL_INFO(realm_log_level_e.RLM_LOG_LEVEL_INFO),
    RLM_LOG_LEVEL_WARNING(realm_log_level_e.RLM_LOG_LEVEL_WARNING),
    RLM_LOG_LEVEL_ERROR(realm_log_level_e.RLM_LOG_LEVEL_ERROR),
    RLM_LOG_LEVEL_FATAL(realm_log_level_e.RLM_LOG_LEVEL_FATAL),
    RLM_LOG_LEVEL_OFF(realm_log_level_e.RLM_LOG_LEVEL_OFF);

    actual val priority: Int
        get() = internalPriority

    actual companion object {
        actual fun valueFromPriority(priority: Short): CoreLogLevel = when (priority.toInt()) {
            RLM_LOG_LEVEL_ALL.priority -> RLM_LOG_LEVEL_ALL
            RLM_LOG_LEVEL_TRACE.priority -> RLM_LOG_LEVEL_TRACE
            RLM_LOG_LEVEL_DEBUG.priority -> RLM_LOG_LEVEL_DEBUG
            RLM_LOG_LEVEL_DETAIL.priority -> RLM_LOG_LEVEL_DETAIL
            RLM_LOG_LEVEL_INFO.priority -> RLM_LOG_LEVEL_INFO
            RLM_LOG_LEVEL_WARNING.priority -> RLM_LOG_LEVEL_WARNING
            RLM_LOG_LEVEL_ERROR.priority -> RLM_LOG_LEVEL_ERROR
            RLM_LOG_LEVEL_FATAL.priority -> RLM_LOG_LEVEL_FATAL
            RLM_LOG_LEVEL_OFF.priority -> RLM_LOG_LEVEL_OFF
            else -> throw IllegalArgumentException("Invalid log level: $priority")
        }
    }
}
