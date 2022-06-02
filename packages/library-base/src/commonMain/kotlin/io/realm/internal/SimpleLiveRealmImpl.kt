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

package io.realm.internal

import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.internal.BaseRealmImpl
import io.realm.kotlin.internal.InternalConfiguration
import io.realm.kotlin.internal.InternalMutableRealm
import io.realm.kotlin.internal.InternalTypedRealm
import io.realm.kotlin.internal.LiveRealmReference
import io.realm.kotlin.internal.WriteTransactionManager
import io.realm.kotlin.internal.copyToRealm
import io.realm.kotlin.internal.interop.LiveRealmPointer
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmObject
import kotlin.reflect.KClass

/**
 * Minimal live Realm implementation that only allows queries and write transactions without being
 * able to observe for changes.
 */
public class SimpleLiveRealmImpl(
    dbPointer: LiveRealmPointer,
    configuration: InternalConfiguration,
) : InternalTypedRealm, InternalMutableRealm, WriteTransactionManager, BaseRealmImpl(configuration) {

    override val realmReference: LiveRealmReference = LiveRealmReference(this, dbPointer)

    override fun cancelWrite() {
        super.cancelWrite()
    }

    /**
     * This version of copyToRealm allows deep copying outdated objects, from a older Realm version. It
     * treats them as a unmanaged objects that would then copy.
     *
     * It is required to allow copying objects during the discard local client reset strategy, from
     * the before Realm to the after one.
     */
    override fun <T : RealmObject> copyToRealm(instance: T, updatePolicy: UpdatePolicy): T {
        return copyToRealm(
            mediator = configuration.mediator,
            realmReference = realmReference,
            element = instance,
            updatePolicy = updatePolicy,
            allowCopyOnOutdatedObjects = true
        )
    }

    override fun <T : BaseRealmObject> query(
        clazz: KClass<T>,
        query: String,
        vararg args: Any?
    ): RealmQuery<T> = super.query(clazz, query, *args)
}
