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

import io.realm.BaseRealmObject
import io.realm.internal.interop.LiveRealmPointer
import io.realm.query.RealmQuery
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

    override fun <T : BaseRealmObject> query(
        clazz: KClass<T>,
        query: String,
        vararg args: Any?
    ): RealmQuery<T> = super.query(clazz, query, *args)
}
