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

package io.realm.test

import io.realm.internal.DynamicMutableRealmImpl
import io.realm.internal.InternalConfiguration
import io.realm.internal.TransactionalRealm
import io.realm.internal.interop.RealmInterop

/**
 * Special dynamic mutable realm that operates on it's own shared realm to allow us to test the
 * [DynamicMutableRealm] API outside of a migration.
 */
internal class DynamicMutableTransactionRealm(configuration: InternalConfiguration) : DynamicMutableRealmImpl(configuration, RealmInterop.realm_open(configuration.nativeConfig, null)), TransactionalRealm {
    fun close() {
        realmReference.close()
    }
}
