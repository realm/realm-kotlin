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

#include "env_utils.h"

static JavaVM *cached_jvm = 0;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *jvm, void *reserved) {
    cached_jvm = jvm;
    return JNI_VERSION_1_2;
}

namespace realm {
    namespace jni_util {
        JNIEnv * get_env(bool attach_if_needed) {
            JNIEnv *env;
            jint rc = cached_jvm->GetEnv((void **)&env, JNI_VERSION_1_2);
            if (rc == JNI_EDETACHED) {
                if (attach_if_needed) {
                   #if defined(__ANDROID__)
                        JNIEnv **jenv = &env;
                    #else
                        void **jenv = (void **) &env;
                    #endif
                    jint ret = cached_jvm->AttachCurrentThread(jenv, nullptr);
                    if (ret != JNI_OK) throw std::runtime_error("Could not attach JVM on thread ");
                } else {
                    throw std::runtime_error("current thread not attached");
                }
            }
            if (rc == JNI_EVERSION)
                throw std::runtime_error("jni version not supported");
            return env;
        }
    }
}
