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

package io.realm.kotlin.internal.interop

// Could potentially work without an open realm - sort of non-live object-ish
// FIXME Do we need a type variable here?
// TODO OPTIMIZE Consider just wrapping a native pointer?
class Link(
    var classKey: ClassKey,
    // Could potentially be narrowed to Int, but Swig automatically returns long for the underlying
    // uint32_t, while cinterop uses UInt!?
    var objKey: Long,
)
