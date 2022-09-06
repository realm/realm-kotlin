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

package io.realm.kotlin.notifications

import io.realm.kotlin.BaseRealm

/**
 * This sealed interface describe the possible changes that can be observed to a Realm.
 *
 * The specific states are represented by the subclasses [InitialRealm] and [UpdatedRealm].
 *
 * Changes can thus be consumed in a number of ways:
 *
 * ```
 * // Variant 1: Switch on the sealed interface
 * realm.asFlow()
 *   .collect { realmChange: RealmChange ->
 *       when(realmChange) {
 *          is InitialRealm -> setInitialState(realmChange.realm)
 *          is UpdatedRealm -> setUpdatedState(realmChange.realm)
 *       }
 *   }
 *
 *
 * // Variant 2: Just pass on the realm
 * realm.asFlow()
 *   .collect { realmChange: RealmChange ->
 *       handleChange(realmChange.realm)
 *   }
 * ```
 */
public sealed interface RealmChange<R : BaseRealm> {
    /**
     * Returns the realm instance that was affected by the change event.
     */
    public val realm: R
}

/**
 * Initial event to be observed on a Realm flow. It contains a reference to the original Realm instance.
 */
public interface InitialRealm<R : BaseRealm> : RealmChange<R>

/**
 * Realm flow event that describes that an update has been performed on to the observed Realm instance.
 */
public interface UpdatedRealm<R : BaseRealm> : RealmChange<R>
