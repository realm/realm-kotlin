/*
 * Copyright 2020 Realm Inc.
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

import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.internal.interop.Callback
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmCoreException
import io.realm.internal.interop.RealmInterop
import io.realm.notifications.InitialResultsImpl
import io.realm.notifications.ResultsChange
import io.realm.notifications.UpdatedResultsImpl
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * Primitive results are not exposed through the public API but might be needed when implementing
 * `RealmDictionary.values` as Core returns those as results.
 */
// TODO OPTIMIZE Perhaps we should map the output of dictionary.values to a RealmList so that
//  primitive typed results are never ever exposed publicly.
internal class RealmResultsImpl<E : RealmObject> constructor(
    private val realm: RealmReference,
    internal val nativePointer: NativePointer,
    private val clazz: KClass<E>,
    private val mediator: Mediator,
    private val mode: Mode = Mode.RESULTS
) : AbstractList<E>(), RealmResults<E>, Observable<RealmResultsImpl<E>, ResultsChange<E>>, RealmStateHolder, Flowable<ResultsChange<E>> {

    enum class Mode {
        // FIXME Needed to make working with @LinkingObjects easier.
        EMPTY, // RealmResults that is always empty.
        RESULTS // RealmResults wrapping a Realm Core Results.
    }

    override val size: Int
        get() = RealmInterop.realm_results_count(nativePointer).toInt()

    override fun get(index: Int): E {
        val link = RealmInterop.realm_results_get(nativePointer, index.toLong())
        val model = mediator.createInstanceOf(clazz)
        model.link(realm, mediator, clazz, link)
        @Suppress("UNCHECKED_CAST")
        return model as E
    }

    @Suppress("SpreadOperator")
    override fun query(query: String, vararg args: Any?): RealmResultsImpl<E> {
        try {
            val table = clazz.simpleName!!
            val queryPointer = RealmInterop.realm_query_parse(nativePointer, table, query, *args)
            val resultsPointer = RealmInterop.realm_query_find_all(queryPointer)
            return RealmResultsImpl(realm, resultsPointer, clazz, mediator)
        } catch (exception: RealmCoreException) {
            throw genericRealmCoreExceptionHandler("Invalid syntax for query `$query`", exception)
        }
    }

    override fun asFlow(): Flow<ResultsChange<E>> {
        realm.checkClosed()
        return realm.owner.registerObserver(this)
    }

    override fun delete() {
        // TODO OPTIMIZE Are there more efficient ways to do this? realm_query_delete_all is not
        //  available in C-API yet, but should probably await final query design
        //  https://github.com/realm/realm-kotlin/issues/84
        RealmInterop.realm_results_delete_all(nativePointer)
    }

    /**
     * Returns a frozen copy of this query result. If it is already frozen, the same instance
     * is returned.
     */
    override fun freeze(frozenRealm: RealmReference): RealmResultsImpl<E> {
        val frozenDbPointer = frozenRealm.dbPointer
        val frozenResults = RealmInterop.realm_results_resolve_in(nativePointer, frozenDbPointer)
        return RealmResultsImpl(frozenRealm, frozenResults, clazz, mediator)
    }

    /**
     * Thaw the frozen query result, turning it back into a live, thread-confined RealmResults.
     */
    override fun thaw(liveRealm: RealmReference): RealmResultsImpl<E> {
        val liveDbPointer = liveRealm.dbPointer
        val liveResultPtr = RealmInterop.realm_results_resolve_in(nativePointer, liveDbPointer)
        return RealmResultsImpl(liveRealm, liveResultPtr, clazz, mediator)
    }

    override fun registerForNotification(callback: Callback): NativePointer {
        return RealmInterop.realm_results_add_notification_callback(nativePointer, callback)
    }

    override fun emitFrozenUpdate(
        frozenRealm: RealmReference,
        change: NativePointer,
        channel: SendChannel<ResultsChange<E>>
    ): ChannelResult<Unit>? {
        val frozenResult = freeze(frozenRealm)

        val builder = ListChangeSetBuilderImpl(change)

        return if (builder.isEmpty()) {
            channel.trySend(InitialResultsImpl(frozenResult))
        } else {
            channel.trySend(UpdatedResultsImpl(frozenResult, builder.build()))
        }
    }

    override fun realmState(): RealmState = realm
}
