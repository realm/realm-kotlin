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

package io.realm.kotlin.internal.query

import io.realm.kotlin.internal.ChangeFlow
import io.realm.kotlin.internal.CoreNotifiable
import io.realm.kotlin.internal.LiveRealm
import io.realm.kotlin.internal.Mediator
import io.realm.kotlin.internal.Notifiable
import io.realm.kotlin.internal.RealmResultsImpl
import io.realm.kotlin.internal.ResultChangeFlow
import io.realm.kotlin.internal.interop.ClassKey
import io.realm.kotlin.internal.interop.NativePointer
import io.realm.kotlin.internal.interop.RealmResultsT
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.types.BaseRealmObject
import kotlinx.coroutines.channels.ProducerScope
import kotlin.reflect.KClass

internal class QueryResultNotifiable<E : BaseRealmObject>(
    val results: NativePointer<RealmResultsT>,
    val classKey: ClassKey,
    val clazz: KClass<E>,
    val mediator: Mediator
) : Notifiable<RealmResultsImpl<E>, ResultsChange<E>> {
    override fun coreObservable(liveRealm: LiveRealm): CoreNotifiable<RealmResultsImpl<E>, ResultsChange<E>>? {
        return thawResults(liveRealm.realmReference, results, classKey, clazz, mediator)
    }

    override fun changeFlow(scope: ProducerScope<ResultsChange<E>>): ChangeFlow<RealmResultsImpl<E>, ResultsChange<E>> {
        return ResultChangeFlow(scope)
    }
}
