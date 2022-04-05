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

package io.realm.internal.interop

import kotlin.jvm.JvmInline

// Wraps a value passed in and out of the C-API
// Could be used to also hold a pointer to the underlying struct and let any conversion be lazy
// initialized. So, basically having both a platform specific
//   val native: realm_value_t
//   val value: T
// This probably requires the value only to be used on a single thread, but would allow us to read
// realm any/mixed out and insert them without actually doing the conversion to the user facing
// Kotlin value
@JvmInline
value class RealmValue(val value: Any?)

