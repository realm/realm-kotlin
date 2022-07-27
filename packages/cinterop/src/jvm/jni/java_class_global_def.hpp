/*
 * Copyright 2017 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef REALM_JNI_IMPL_CLASS_GLOBAL_DEF_HPP
#define REALM_JNI_IMPL_CLASS_GLOBAL_DEF_HPP

#include "env_utils.h"
#include "java_class.hpp"
#include "java_method.hpp"

#include <memory>

#include <realm/util/assert.hpp>

namespace realm {

class BinaryData;

namespace _impl {

// Global static jclass pool initialized when JNI_OnLoad() called.
//
// Only load absolutely necessary classes which might be initialized in native threads as FindClass
// is a relatively slow operation and this pool is initialized when our library is loaded which
// will most often be when the app starts.
//
// FindClass will fail if it is called from a native thread (e.g.: the sync client thread.). But usually it is not a
// problem if the FindClass is called from an JNI method. So keeping a static JavaClass var locally is still preferred
// if it is possible.
class JavaClassGlobalDef {
private:
    JavaClassGlobalDef(JNIEnv* env)
        : m_java_util_hashmap(env, "java/util/HashMap", false)
        , m_java_lang_int(env, "java/lang/Integer", false)
        , m_kotlin_jvm_functions_function0(env, "kotlin/jvm/functions/Function0", false)
        , m_kotlin_jvm_functions_function1(env, "kotlin/jvm/functions/Function1", false)
        , m_io_realm_kotlin_internal_interop_sync_network_transport(env, "io/realm/kotlin/internal/interop/sync/NetworkTransport", false)
        , m_io_realm_kotlin_internal_interop_sync_response(env, "io/realm/kotlin/internal/interop/sync/Response", false)
        , m_io_realm_kotlin_internal_interop_long_pointer_wrapper(env, "io/realm/kotlin/internal/interop/LongPointerWrapper", false)
        , m_io_realm_kotlin_internal_interop_sync_sync_error(env, "io/realm/kotlin/internal/interop/sync/SyncError", false)
        , m_io_realm_kotlin_internal_interop_sync_app_error(env, "io/realm/kotlin/internal/interop/sync/AppError", false)
        , m_io_realm_kotlin_internal_interop_sync_log_callback(env, "io/realm/kotlin/internal/interop/SyncLogCallback", false)
        , m_io_realm_kotlin_internal_interop_sync_error_callback(env, "io/realm/kotlin/internal/interop/SyncErrorCallback", false)
        , m_io_realm_kotlin_internal_interop_sync_jvm_sync_session_transfer_completion_callback(env, "io/realm/kotlin/internal/interop/sync/JVMSyncSessionTransferCompletionCallback", false)
        , m_io_realm_kotlin_internal_interop_sync_response_callback_impl(env, "io/realm/kotlin/internal/interop/sync/ResponseCallbackImpl", false)
        , m_io_realm_kotlin_internal_interop_subscription_set_callback(env, "io/realm/kotlin/internal/interop/SubscriptionSetCallback", false)
        , m_io_realm_kotlin_internal_interop_sync_before_client_reset_handler(env, "io/realm/kotlin/internal/interop/SyncBeforeClientResetHandler", false)
        , m_io_realm_kotlin_internal_interop_sync_after_client_reset_handler(env, "io/realm/kotlin/internal/interop/SyncAfterClientResetHandler", false)
        , m_io_realm_kotlin_internal_interop_core_error_utils(env, "io/realm/kotlin/internal/interop/CoreErrorUtils", false)
    {
    }

    jni_util::JavaClass m_java_util_hashmap;
    jni_util::JavaClass m_java_lang_int;
    jni_util::JavaClass m_kotlin_jvm_functions_function0;
    jni_util::JavaClass m_kotlin_jvm_functions_function1;
    jni_util::JavaClass m_io_realm_kotlin_internal_interop_sync_network_transport;
    jni_util::JavaClass m_io_realm_kotlin_internal_interop_sync_response;
    jni_util::JavaClass m_io_realm_kotlin_internal_interop_long_pointer_wrapper;
    jni_util::JavaClass m_io_realm_kotlin_internal_interop_sync_sync_error;
    jni_util::JavaClass m_io_realm_kotlin_internal_interop_sync_app_error;
    jni_util::JavaClass m_io_realm_kotlin_internal_interop_sync_log_callback;
    jni_util::JavaClass m_io_realm_kotlin_internal_interop_sync_error_callback;
    jni_util::JavaClass m_io_realm_kotlin_internal_interop_sync_jvm_sync_session_transfer_completion_callback;
    jni_util::JavaClass m_io_realm_kotlin_internal_interop_sync_response_callback_impl;
    jni_util::JavaClass m_io_realm_kotlin_internal_interop_subscription_set_callback;
    jni_util::JavaClass m_io_realm_kotlin_internal_interop_sync_before_client_reset_handler;
    jni_util::JavaClass m_io_realm_kotlin_internal_interop_sync_after_client_reset_handler;
    jni_util::JavaClass m_io_realm_kotlin_internal_interop_core_error_utils;

    inline static std::unique_ptr<JavaClassGlobalDef>& instance()
    {
        static std::unique_ptr<JavaClassGlobalDef> instance;
        return instance;
    };

public:
    // Called in JNI_OnLoad
    static void initialize(JNIEnv* env)
    {
        REALM_ASSERT(!instance());
        instance().reset(new JavaClassGlobalDef(env));
    }
    // Called in JNI_OnUnload
    static void release()
    {
        REALM_ASSERT(instance());
        instance().release();
    }

    inline static const jni_util::JavaClass& java_util_hashmap()
    {
        return instance()->m_java_util_hashmap;
    }

    inline static jobject new_int(JNIEnv* env, int32_t value)
    {
        static jni_util::JavaMethod init(env,
                                         instance()->m_java_lang_int,
                                         "<init>",
                                         "(I)V");
        return env->NewObject(instance()->m_java_lang_int, init, value);
    }

    inline static const jni_util::JavaClass& network_transport_class()
    {
        return instance()->m_io_realm_kotlin_internal_interop_sync_network_transport;
    }

    inline static const jni_util::JavaClass& network_transport_response_class()
    {
        return instance()->m_io_realm_kotlin_internal_interop_sync_response;
    }

    inline static const jni_util::JavaClass& long_pointer_wrapper()
    {
        return instance()->m_io_realm_kotlin_internal_interop_long_pointer_wrapper;
    }

    inline static const jni_util::JavaClass& sync_error()
    {
        return instance()->m_io_realm_kotlin_internal_interop_sync_sync_error;
    }

    inline static const jni_util::JavaClass& app_error()
    {
        return instance()->m_io_realm_kotlin_internal_interop_sync_app_error;
    }

    inline static const jni_util::JavaClass& sync_log_callback()
    {
        return instance()->m_io_realm_kotlin_internal_interop_sync_log_callback;
    }

    inline static const jni_util::JavaClass& sync_error_callback()
    {
        return instance()->m_io_realm_kotlin_internal_interop_sync_error_callback;
    }

    inline static const jni_util::JavaClass& sync_session_transfer_completion_callback()
    {
        return instance()->m_io_realm_kotlin_internal_interop_sync_jvm_sync_session_transfer_completion_callback;
    };

    inline static const jni_util::JavaClass& app_response_callback()
    {
        return instance()->m_io_realm_kotlin_internal_interop_sync_response_callback_impl;
    };

    inline static const jni_util::JavaClass& subscriptionset_changed_callback() {
        return instance()->m_io_realm_kotlin_internal_interop_subscription_set_callback;
    }

    inline static const jni_util::JavaClass& sync_before_client_reset() {
        return instance()->m_io_realm_kotlin_internal_interop_sync_before_client_reset_handler;
    }

    inline static const jni_util::JavaClass& sync_after_client_reset() {
        return instance()->m_io_realm_kotlin_internal_interop_sync_after_client_reset_handler;
    }

    inline static const jni_util::JavaClass& core_error_utils()
    {
        return instance()->m_io_realm_kotlin_internal_interop_core_error_utils;
    }

    inline static const jni_util::JavaMethod function0Method(JNIEnv* env) {
        return jni_util::JavaMethod(env, instance()->m_kotlin_jvm_functions_function0, "invoke",
                                    "()Ljava/lang/Object;");
    }

    inline static const jni_util::JavaMethod function1Method(JNIEnv* env) {
        return jni_util::JavaMethod(env, instance()->m_kotlin_jvm_functions_function1, "invoke",
                "(Ljava/lang/Object;)Ljava/lang/Object;");
    }
};

} // namespace realm
} // namespace jni_impl


#endif // REALM_JNI_IMPL_CLASS_GLOBAL_DEF_HPP
