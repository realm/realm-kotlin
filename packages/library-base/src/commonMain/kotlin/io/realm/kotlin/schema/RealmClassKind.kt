/*
 * Copyright 2023 Realm Inc.
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

package io.realm.kotlin.schema

/**
 * Enum describing what kind of Realm object it is.
 */
public enum class RealmClassKind {
    /**
     * Standard Realm objects are the default kind of object in Realm, and they extend the
     * [io.realm.kotlin.types.RealmObject] interface.
     */
    STANDARD,
    /**
     * Embedded Realm objects extend the [io.realm.kotlin.types.EmbeddedRealmObject] interface.
     *
     * These kinds of classes must always have exactly one parent object when added to a realm. This
     * means they are deleted when the parent object is delete or the embedded object property is
     * overwritten.
     *
     * See [io.realm.kotlin.types.EmbeddedRealmObject] for more details.
     */
    EMBEDDED,
}
