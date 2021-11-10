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

package io.realm.schema

data class ElementType(val fieldType: FieldType, val nullable: Boolean) {
    // In realm.h this is called PropertyType but this would overlap with RealmProperty.type?
    // Core types?
    // Mimics storage type. This is important to the user if we do custom type maps, etc.
    enum class FieldType {
        BOOL,
        INT,
        STRING,
        OBJECT,
        FLOAT,
        DOUBLE
    }
}


