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
        , m_io_realm_network_transport(env, "io/realm/internal/interop/sync/NetworkTransport", false)
        , m_io_realm_response(env, "io/realm/internal/interop/sync/Response", false)
    {
    }

    jni_util::JavaClass m_java_util_hashmap;
    jni_util::JavaClass m_io_realm_network_transport;
    jni_util::JavaClass m_io_realm_response;

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

    inline static const jni_util::JavaClass& network_transport_response_class()
    {
        return instance()->m_io_realm_response;
    }

    inline static const jni_util::JavaClass& network_transport_class()
    {
        return instance()->m_io_realm_network_transport;
    }

    inline static const jni_util::JavaClass& java_util_hashmap()
    {
        return instance()->m_java_util_hashmap;
    }
};

} // namespace realm
} // namespace jni_impl


#endif // REALM_JNI_IMPL_CLASS_GLOBAL_DEF_HPP
