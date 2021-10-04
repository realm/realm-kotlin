/*
 * Copyright 2021 Realm Inc.
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

#ifndef TEST_ENV_UTILS_H
#define TEST_ENV_UTILS_H

#include <jni.h>
#include <cstring>
#include <string>
#include <vector>
#include "java_global_ref_by_move.hpp"

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved);

namespace realm {
    namespace jni_util {
        static std::vector<JavaGlobalRefByMove> m_global_refs;

        JNIEnv * get_env(bool attach_if_needed = false);
        // TODO Migrate java_method.{hpp,cpp} realm-java or implement similar caching mechanism to
        //  hold global references to classes and look up methods
        jmethodID lookup(JNIEnv *jenv, const char *class_name, const char *method_name,
                         const char *signature);

        void keep_global_ref(JavaGlobalRefByMove& ref);
    }
}

#endif //TEST_ENV_UTILS_H
