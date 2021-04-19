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

package io.realm

import io.realm.internal.RealmModelInternal
import kotlinx.coroutines.flow.Flow

// FIXME API Currently just adding these as extension methods as putting them directly into
//  RealmModel would break compiler plugin. Reiterate along with
//  https://github.com/realm/realm-kotlin/issues/83

fun <T: RealmObject<T>> RealmObject<T>.delete() {
    Realm.delete(this as T)
}

fun <T: RealmObject<T>> RealmObject<T>.addChangeListener(objectChangeListener: Callback): Cancellable {
    return Realm.addChangeListener(this as T, objectChangeListener)
}

fun <T: RealmObject<T>> RealmObject<T>.observe(): Flow<T> {
    val thiz = (this as RealmModelInternal)
    val owner = this.`$realm$owner` as Realm
    return owner.notifierThread.objectChanged(this as T)
}
