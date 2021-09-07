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

#include "utils.h"

#include <realm/unicode.hpp>
#include <sstream>
#include <iomanip>
#include <realm/util/safe_int_ops.hpp>

#include "utf8.hpp"

using namespace realm;
using namespace realm::util;
using namespace realm::jni_util;
using namespace realm::_impl;

// This assumes that 'jchar' is an integral type with at least 16
// non-sign value bits, that is, an unsigned 16-bit integer, or any
// signed or unsigned integer with more than 16 bits.
struct JcharTraits {
    static jchar to_int_type(jchar c) noexcept
    {
        return c;
    }
    static jchar to_char_type(jchar i) noexcept
    {
        return i;
    }
};

static std::string string_to_hex(const std::string& message, realm::StringData& str, const char* in_begin, const char* in_end,
                                 jchar* out_curr, jchar* out_end, size_t retcode, size_t error_code)
{
    std::ostringstream ret;

    const char* s = str.data();
    ret << message << " ";
    ret << "error_code = " << error_code << "; ";
    ret << "retcode = " << retcode << "; ";
    ret << "StringData.size = " << str.size() << "; ";
    ret << "StringData.data = " << str << "; ";
    ret << "StringData as hex = ";
    for (std::string::size_type i = 0; i < str.size(); ++i)
        ret << " 0x" << std::hex << std::setfill('0') << std::setw(2) << (int)s[i];
    ret << "; ";
    ret << "in_begin = " << in_begin << "; ";
    ret << "in_end = " << in_end << "; ";
    ret << "out_curr = " << out_curr << "; ";
    ret << "out_end = " << out_end << ";";
    return ret.str();
}

jstring to_jstring(JNIEnv* env, realm::StringData str)
{
    if (str.is_null()) {
        return NULL;
    }

    // For efficiency, if the incoming UTF-8 string is sufficiently
    // small, we will attempt to store the UTF-16 output into a stack
    // allocated buffer of static size. Otherwise we will have to
    // dynamically allocate the output buffer after calculating its
    // size.

    const size_t stack_buf_size = 48;
    jchar stack_buf[stack_buf_size];
    std::unique_ptr<jchar[]> dyn_buf;

    const char* in_begin = str.data();
    const char* in_end = str.data() + str.size();
    jchar* out_begin = stack_buf;
    jchar* out_curr = stack_buf;
    jchar* out_end = stack_buf + stack_buf_size;

    typedef Utf8x16<jchar, JcharTraits> Xcode;

    if (str.size() <= stack_buf_size) {
        size_t retcode = Xcode::to_utf16(in_begin, in_end, out_curr, out_end);
        if (retcode != 0) {
            throw util::runtime_error(string_to_hex("Failure when converting short string to UTF-16", str, in_begin, in_end,
                                                    out_curr, out_end, size_t(0), retcode));
        }
        if (in_begin == in_end) {
            goto transcode_complete;
        }
    }

    {
        const char* in_begin2 = in_begin;
        size_t error_code;
        size_t size = Xcode::find_utf16_buf_size(in_begin2, in_end, error_code);
        if (in_begin2 != in_end) {
            throw util::runtime_error(string_to_hex("Failure when computing UTF-16 size", str, in_begin, in_end, out_curr,
                                                    out_end, size, error_code));
        }
        if (int_add_with_overflow_detect(size, stack_buf_size)) {
            throw util::runtime_error("String size overflow");
        }
        dyn_buf.reset(new jchar[size]);
        out_curr = std::copy(out_begin, out_curr, dyn_buf.get());
        out_begin = dyn_buf.get();
        out_end = dyn_buf.get() + size;
        size_t retcode = Xcode::to_utf16(in_begin, in_end, out_curr, out_end);
        if (retcode != 0) {
            throw util::runtime_error(string_to_hex("Failure when converting long string to UTF-16", str, in_begin, in_end,
                                                    out_curr, out_end, size_t(0), retcode));
        }
        REALM_ASSERT(in_begin == in_end);
    }

    transcode_complete : {
    jsize out_size;
    if (int_cast_with_overflow_detect(out_curr - out_begin, out_size)) {
        throw util::runtime_error("String size overflow");
    }

    return env->NewString(out_begin, out_size);
}
}
