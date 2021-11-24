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

package io.realm.internal.interop

// FIXME API-INTERNAL Compiler does not pick up the actual if not in a separate file, so not
//  following RealmEnums.kt structure, but might have to move anyway, so keeping the structure
//  unaligned for now.
actual enum class PropertyFlag(override val nativeValue: Int) : NativeEnumerated {
    RLM_PROPERTY_NORMAL(realm_property_flags_e.RLM_PROPERTY_NORMAL),
    RLM_PROPERTY_NULLABLE(realm_property_flags_e.RLM_PROPERTY_NULLABLE),
    RLM_PROPERTY_PRIMARY_KEY(realm_property_flags_e.RLM_PROPERTY_PRIMARY_KEY),
    RLM_PROPERTY_INDEXED(realm_property_flags_e.RLM_PROPERTY_INDEXED),
}
