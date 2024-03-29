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

#include "java_class.hpp"
#include "env_utils.h"
#include <realm/util/assert.hpp>

using namespace realm::jni_util;

JavaClass::JavaClass()
    : m_ref_owner()
    , m_class(nullptr)
{
}

JavaClass::JavaClass(JNIEnv* env, const char* class_name, bool free_on_unload)
    : m_ref_owner(get_jclass(env, class_name))
    , m_class(reinterpret_cast<jclass>(m_ref_owner.get()))
{
    if (free_on_unload) {
        // Move the ownership of global ref to JNIUtils which will be released when JNI_OnUnload.
        keep_global_ref(m_ref_owner);
    }
}

JavaClass::JavaClass(JavaClass&& rhs)
    : m_ref_owner(std::move(rhs.m_ref_owner))
    , m_class(rhs.m_class)
{
    rhs.m_class = nullptr;
}

JavaGlobalRefByMove JavaClass::get_jclass(JNIEnv* env, const char* class_name)
{
    jclass cls = env->FindClass(class_name);
    REALM_ASSERT_RELEASE_EX(cls, class_name);

    JavaGlobalRefByMove cls_ref(env, cls, true);
    return cls_ref;
}
