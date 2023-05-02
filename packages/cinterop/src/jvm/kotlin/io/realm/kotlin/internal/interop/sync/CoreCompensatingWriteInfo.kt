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
package io.realm.kotlin.internal.interop.sync

import io.realm.kotlin.internal.interop.RealmValue
import io.realm.kotlin.internal.interop.asRealmValueT

actual class CoreCompensatingWriteInfo(
    actual val reason: String,
    actual val objectName: String,
    primaryKeyPtr: Long
) {

    actual val primaryKey: RealmValue = RealmValue(primaryKeyPtr.asRealmValueT())
}