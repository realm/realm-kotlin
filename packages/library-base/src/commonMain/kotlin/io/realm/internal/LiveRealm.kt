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

package io.realm.internal

import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.RegistrationToken
import io.realm.internal.interop.NativePointer
import kotlinx.coroutines.CoroutineDispatcher

/**
 * A live realm that can be updated and receive notifications on data and schema changes when
 * updated by other threads.
 *
 * NOTE: Must be constructed with a single thread dispatch and must be constructed on the same
 * thread that is backing the dispatcher.
 */
open class LiveRealm: BaseRealmImpl {

    private val realmChangeRegistration: RegistrationToken

    constructor(
        configuration: InternalRealmConfiguration,
        dispatcher: CoroutineDispatcher? = null
    ) : super(configuration, RealmInterop.realm_open(configuration.nativeConfig, dispatcher))

    init {
        realmChangeRegistration = RealmInterop.realm_add_realm_changed_callback(realmReference.dbPointer, ::onRealmChanged)
    }

    var snapshot: RealmReference = RealmReference(realmReference.owner, RealmInterop.realm_freeze(realmReference.dbPointer))

    open fun onRealmChanged() {
        snapshot = RealmReference(realmReference.owner, RealmInterop.realm_freeze(realmReference.dbPointer))
    }

    override fun close() {
        RealmInterop.realm_remove_realm_changed_callback(realmReference.dbPointer, realmChangeRegistration)
        super.close()
    }
}
