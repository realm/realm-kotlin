/*
 * Copyright 2022 Realm Inc.
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

package io.realm.kotlin.internal.interop

actual enum class ValueType(override val nativeValue: Int) : NativeEnumerated {
    RLM_TYPE_NULL(realm_value_type_e.RLM_TYPE_NULL),
    RLM_TYPE_INT(realm_value_type_e.RLM_TYPE_INT),
    RLM_TYPE_BOOL(realm_value_type_e.RLM_TYPE_BOOL),
    RLM_TYPE_STRING(realm_value_type_e.RLM_TYPE_STRING),
    RLM_TYPE_BINARY(realm_value_type_e.RLM_TYPE_BINARY),
    RLM_TYPE_TIMESTAMP(realm_value_type_e.RLM_TYPE_TIMESTAMP),
    RLM_TYPE_FLOAT(realm_value_type_e.RLM_TYPE_FLOAT),
    RLM_TYPE_DOUBLE(realm_value_type_e.RLM_TYPE_DOUBLE),
    RLM_TYPE_DECIMAL128(realm_value_type_e.RLM_TYPE_DECIMAL128),
    RLM_TYPE_OBJECT_ID(realm_value_type_e.RLM_TYPE_OBJECT_ID),
    RLM_TYPE_LINK(realm_value_type_e.RLM_TYPE_LINK),
    RLM_TYPE_UUID(realm_value_type_e.RLM_TYPE_UUID);

    companion object {
        fun from(nativeValue: Int): ValueType = values().find {
            it.nativeValue == nativeValue
        } ?: error("Unknown value type: $nativeValue")
    }
}
