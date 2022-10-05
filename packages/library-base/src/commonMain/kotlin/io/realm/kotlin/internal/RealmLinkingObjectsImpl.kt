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
package io.realm.kotlin.internal

import io.realm.kotlin.internal.interop.Callback
import io.realm.kotlin.internal.interop.RealmChangesPointer
import io.realm.kotlin.internal.interop.RealmNotificationTokenPointer
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.internal.InitialResultsImpl
import io.realm.kotlin.notifications.internal.UpdatedResultsImpl
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.BaseRealmObject
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow

internal class RealmLinkingObjectsImpl<E : BaseRealmObject>(
    val targetObject: RealmObjectReference<*>?,
    val realmResults: RealmResultsImpl<E>,
) : AbstractList<E>(),
    RealmResults<E>,
    InternalDeleteable by realmResults,
    Observable<RealmLinkingObjectsImpl<E>, ResultsChange<E>>,
    RealmStateHolder by realmResults,
    Flowable<ResultsChange<E>> by realmResults {

    override fun emitFrozenUpdate(
        frozenRealm: RealmReference,
        change: RealmChangesPointer,
        channel: SendChannel<ResultsChange<E>>
    ): ChannelResult<Unit>? {
        val frozenResult = freeze(frozenRealm)

        val builder = ListChangeSetBuilderImpl(change)

        return if (builder.isEmpty()) {
            channel.trySend(InitialResultsImpl(frozenResult))
        } else {
            if (targetObject!!.isValid()) {
                channel.trySend(UpdatedResultsImpl(frozenResult, builder.build()))
            } else {
                channel.close()
                null
            }
        }
    }

    override val size: Int by realmResults::size

    override fun get(index: Int): E = realmResults[index]

    override fun registerForNotification(callback: Callback<RealmChangesPointer>): RealmNotificationTokenPointer {
        return realmResults.registerForNotification(callback)
    }

    override fun freeze(frozenRealm: RealmReference): RealmLinkingObjectsImpl<E> {
        val frozenResults = realmResults.freeze(frozenRealm)
        val frozenObject = targetObject?.freeze(frozenRealm)
        return RealmLinkingObjectsImpl(frozenObject, frozenResults)
    }

    override fun thaw(liveRealm: RealmReference): RealmLinkingObjectsImpl<E> {
        val thawedResults = realmResults.thaw(liveRealm)
        val thawedObject = targetObject?.thaw(liveRealm)
        return RealmLinkingObjectsImpl(thawedObject, thawedResults)
    }

    override fun query(query: String, vararg args: Any?): RealmQuery<E> =
        realmResults.query(query, *args)

    override fun asFlow(): Flow<ResultsChange<E>> {
        return targetObject!!.owner.run {
            checkClosed()
            owner.registerObserver(this@RealmLinkingObjectsImpl)
        }
    }
}
