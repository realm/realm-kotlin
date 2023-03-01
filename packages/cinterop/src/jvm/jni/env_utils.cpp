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
#include "java_class_global_def.hpp"
#include <stdexcept> // needed for Linux centos7 build

static JavaVM *cached_jvm = 0;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *jvm, void *reserved) {
    cached_jvm = jvm;
    realm::_impl::JavaClassGlobalDef::initialize(realm::jni_util::get_env());
    return JNI_VERSION_1_2;
}

namespace realm {
    namespace jni_util {
        JNIEnv * get_env(bool attach_if_needed, bool is_daemon_thread, realm::util::Optional<std::string> thread_name) {
            JNIEnv *env;
            jint rc = cached_jvm->GetEnv((void **)&env, JNI_VERSION_1_2);
            if (rc == JNI_EDETACHED) {
                if (attach_if_needed) {
                   #if defined(__ANDROID__)
                        JNIEnv **jenv = &env;
                    #else
                        void **jenv = (void **) &env;
                    #endif
                    JavaVMAttachArgs args;
                    args.version = JNI_VERSION_1_2;
                    args.group = nullptr;
                    if (thread_name.has_value()) {
                        args.name = (char*) thread_name.value().c_str();
                    } else {
                        args.name = nullptr;
                    }
                    jint ret;
                    if (is_daemon_thread) {
                        ret = cached_jvm->AttachCurrentThreadAsDaemon(jenv, &args);
                    } else {
                        ret = cached_jvm->AttachCurrentThread(jenv, &args);
                    }
                    if (ret != JNI_OK) throw std::runtime_error("Could not attach JVM on thread ");
                } else {
                    throw std::runtime_error("current thread not attached");
                }
            }
            if (rc == JNI_EVERSION)
                throw std::runtime_error("jni version not supported");

            return env;
        }

        void detach_current_thread() {
            cached_jvm->DetachCurrentThread();
        }

        JNIEnv * get_env_or_null() {
            JNIEnv *env;
            jint rc = cached_jvm->GetEnv((void **)&env, JNI_VERSION_1_2);
            if (rc == JNI_EDETACHED) {
                #if defined(__ANDROID__)
                    JNIEnv **jenv = &env;
                #else
                    void **jenv = (void **) &env;
                #endif
                cached_jvm->AttachCurrentThread(jenv, nullptr);
            }
            if (rc == JNI_EVERSION)
                throw std::runtime_error("jni version not supported");
            return env;
        }

        jmethodID lookup(JNIEnv *jenv, const char *class_name, const char *method_name,
                         const char *signature) {
            jclass localClass = jenv->FindClass(class_name);
            return jenv->GetMethodID(localClass, method_name, signature);
        }

        void keep_global_ref(JavaGlobalRefByMove& ref)
        {
            m_global_refs.push_back(std::move(ref));
        }
    }
}
