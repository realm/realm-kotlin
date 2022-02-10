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

package io.realm.notifications

import io.realm.BaseRealm

/**
 * A [RealmChange] describes the type of changes that can be observed on a realm.
 */
sealed interface RealmChange<R : BaseRealm> {
    /**
     * Returns the newest version of the Realm.
     */
    val realm: R
}
/**
 * [InitialRealm] describes the initial event observed on a Realm flow. It contains the Realm instance
 * it was subscribed to.
 */
interface InitialRealm<R : BaseRealm> : RealmChange<R>

/**
 * [UpdatedRealm] describes a Realm update event to be observed on a Realm flow after the [InitialRealm].
 * It contains a Realm instance of the updated Realm.
 */
interface UpdatedRealm<R : BaseRealm> : RealmChange<R>
