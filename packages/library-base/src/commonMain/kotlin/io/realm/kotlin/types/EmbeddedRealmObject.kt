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

package io.realm.kotlin.types

/**
 * Marker interface to define an embedded model.
 *
 * Embedded objects have a slightly different behavior than normal objects:
 * - They must have exactly 1 parent linking to them when the embedded object is added to
 *   the Realm. Embedded objects can be the parent of other embedded objects. The parent
 *   cannot be changed later, except by copying the object.
 * - They cannot have fields annotated with `@PrimaryKey`.
 * - When a parent object is deleted, all embedded objects are also deleted.
 */
public interface EmbeddedRealmObject : BaseRealmObject
