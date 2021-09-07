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

#ifndef TEST_UTILS_H
#define TEST_UTILS_H

#include <realm/string_data.hpp>

#include <realm/table.hpp>
#include "env_utils.h"

jstring to_jstring(JNIEnv* env, realm::StringData str);

class JStringAccessor {
public:
    JStringAccessor(JNIEnv* env, jstring s) : JStringAccessor(env, s, false) {}; // throws
    JStringAccessor(JNIEnv*, jstring, bool); // throws

    bool is_null_or_empty() {
        return m_is_null || m_size == 0;
    }

    bool is_null() {
        return m_is_null;
    }

    operator realm::StringData() const
    {
        // To solve the link issue by directly using Table::max_string_size
        static constexpr size_t max_string_size = realm::Table::max_string_size;

        if (m_is_null) {
            return realm::StringData();
        }
        else if (m_size > max_string_size) {
            // TODO: Throw an actual exception
            throw 20;
//            THROW_JAVA_EXCEPTION(
//                    m_env, realm::_impl::JavaExceptionDef::IllegalArgument,
//                    realm::util::format(
//                            "The length of 'String' value in UTF8 encoding is %1 which exceeds the max string length %2.",
//                            m_size, max_string_size));
        }
        else {
            return realm::StringData(m_data.get(), m_size);
        }
    }

    operator std::string() const noexcept
    {
        if (m_is_null) {
            return std::string();
        }
        return std::string(m_data.get(), m_size);
    }

private:
    JNIEnv* m_env;
    bool m_is_null;
    std::shared_ptr<char> m_data;
    std::size_t m_size;
};

// Accessor for Java object arrays
template <typename AccessorType, typename ObjectType>
class JObjectArrayAccessor {
public:
    JObjectArrayAccessor(JNIEnv* env, jobjectArray jobject_array)
            : m_env(env)
            , m_jobject_array(jobject_array)
            , m_size(jobject_array ? env->GetArrayLength(jobject_array) : 0)
    {
    }
    ~JObjectArrayAccessor()
    {
    }

    // Not implemented
    JObjectArrayAccessor(JObjectArrayAccessor&&) = delete;
    JObjectArrayAccessor& operator=(JObjectArrayAccessor&&) = delete;
    JObjectArrayAccessor(const JObjectArrayAccessor&) = delete;
    JObjectArrayAccessor& operator=(const JObjectArrayAccessor&) = delete;

    inline jsize size() const noexcept
    {
        return m_size;
    }

    inline AccessorType operator[](const int index) const noexcept
    {
        return AccessorType(m_env, static_cast<ObjectType>(m_env->GetObjectArrayElement(m_jobject_array, index)));
    }

private:
    JNIEnv* m_env;
    jobjectArray m_jobject_array;
    jsize m_size;
};

template<>
inline JStringAccessor JObjectArrayAccessor<JStringAccessor,jstring>::operator[](const int index) const noexcept {
    return JStringAccessor(m_env, static_cast<jstring>(m_env->GetObjectArrayElement(m_jobject_array, index)), true);
}

#endif //TEST_UTILS_H
