/*
 * Copyright 2020 Realm Inc.
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

// FIXME API-INTERNAL Compiler does not pick up the actual if not in a separate file, so not
//  following RealmEnums.kt structure, but might have to move anyway, so keeping the structure
//  unaligned for now.
actual enum class PropertyType(override val nativeValue: Int) : NativeEnumerated {
    RLM_PROPERTY_TYPE_INT(realm_property_type_e.RLM_PROPERTY_TYPE_INT),
    RLM_PROPERTY_TYPE_BOOL(realm_property_type_e.RLM_PROPERTY_TYPE_BOOL),
    RLM_PROPERTY_TYPE_STRING(realm_property_type_e.RLM_PROPERTY_TYPE_STRING),
    RLM_PROPERTY_TYPE_BINARY(realm_property_type_e.RLM_PROPERTY_TYPE_BINARY),
    RLM_PROPERTY_TYPE_MIXED(realm_property_type_e.RLM_PROPERTY_TYPE_MIXED),
    RLM_PROPERTY_TYPE_TIMESTAMP(realm_property_type_e.RLM_PROPERTY_TYPE_TIMESTAMP),
    RLM_PROPERTY_TYPE_FLOAT(realm_property_type_e.RLM_PROPERTY_TYPE_FLOAT),
    RLM_PROPERTY_TYPE_DOUBLE(realm_property_type_e.RLM_PROPERTY_TYPE_DOUBLE),
    RLM_PROPERTY_TYPE_OBJECT(realm_property_type_e.RLM_PROPERTY_TYPE_OBJECT),
    RLM_PROPERTY_TYPE_LINKING_OBJECTS(realm_property_type_e.RLM_PROPERTY_TYPE_LINKING_OBJECTS),
    RLM_PROPERTY_TYPE_DECIMAL128(realm_property_type_e.RLM_PROPERTY_TYPE_DECIMAL128),
    RLM_PROPERTY_TYPE_OBJECT_ID(realm_property_type_e.RLM_PROPERTY_TYPE_OBJECT_ID),
    RLM_PROPERTY_TYPE_UUID(realm_property_type_e.RLM_PROPERTY_TYPE_UUID)
    ;

    // TODO OPTIMIZE
    actual companion object {
        actual fun from(nativeValue: Int): PropertyType {
            return values().find { it.nativeValue == nativeValue } ?: error("Unknown property type: $nativeValue")
        }
    }
}
