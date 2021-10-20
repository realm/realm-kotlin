//package io.realm.internal.interop
//
//import realm_wrapper.realm_log_level
//import realm_wrapper.realm_log_level_e
//
//actual enum class CoreLogLevel(private val internalPriority: Int) {
//    RLM_LOG_LEVEL_ALL(realm_log_level_e.RLM_LOG_LEVEL_ALL.value.toInt()),
//    RLM_LOG_LEVEL_TRACE(realm_log_level_e.RLM_LOG_LEVEL_TRACE.value.toInt()),
//    RLM_LOG_LEVEL_DEBUG(realm_log_level_e.RLM_LOG_LEVEL_DEBUG.value.toInt()),
//    RLM_LOG_LEVEL_DETAIL(realm_log_level_e.RLM_LOG_LEVEL_DETAIL.value.toInt()),
//    RLM_LOG_LEVEL_INFO(realm_log_level_e.RLM_LOG_LEVEL_INFO.value.toInt()),
//    RLM_LOG_LEVEL_WARNING(realm_log_level_e.RLM_LOG_LEVEL_WARNING.value.toInt()),
//    RLM_LOG_LEVEL_ERROR(realm_log_level_e.RLM_LOG_LEVEL_ERROR.value.toInt()),
//    RLM_LOG_LEVEL_FATAL(realm_log_level_e.RLM_LOG_LEVEL_FATAL.value.toInt()),
//    RLM_LOG_LEVEL_OFF(realm_log_level_e.RLM_LOG_LEVEL_OFF.value.toInt());
//
//    actual val priority: Int
//        get() = internalPriority
//}
