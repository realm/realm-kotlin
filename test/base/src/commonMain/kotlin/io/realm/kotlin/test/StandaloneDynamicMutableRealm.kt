@file:Suppress("invisible_member", "invisible_reference")
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

package io.realm.kotlin.test

import io.realm.kotlin.internal.InternalConfiguration
import io.realm.kotlin.internal.dynamic.DynamicMutableRealmImpl
import io.realm.kotlin.internal.interop.RealmInterop

/**
 * Special dynamic mutable realm with methods for managing a write transaction.
 *
 * The normal [DynamicMutableRealm] is currently only available in a migration where core manages
 * the transaction part of the migration. This class provides a dynamic mutable realm that operates
 * on it's own shared realm with the ability to manage the write transaction, which allows us to
 * test the [DynamicMutableRealm] API outside of a migration.
 */
internal class StandaloneDynamicMutableRealm(configuration: InternalConfiguration) :
    DynamicMutableRealmImpl(
        configuration,
        RealmInterop.realm_open(configuration.createNativeConfiguration(), null)
    ) {

    override fun close() {
        realmReference.close()
    }
}
