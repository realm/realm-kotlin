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
import io.realm.internal.interop.Link
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmCoreException
import io.realm.internal.interop.RealmInterop
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

// FIXME API-QUERY Final query design is tracked in https://github.com/realm/realm-kotlin/issues/84
//  - Lazy API makes it harded to debug
//  - Postponing execution to actually accessing the elements also prevents query parser errors to
//    be raised. Maybe we can get an option to prevalidate queries in the C-API?

internal class RealmResultsImpl<T : RealmObject> : AbstractList<T>, RealmResults<T>, RealmStateHolder, Observable<RealmResultsImpl<T>> {

    private val mode: Mode
    private val realm: RealmReference
    private val clazz: KClass<T>
    private val schema: Mediator
    internal val result: NativePointer

    private enum class Mode {
        // FIXME Needed to make working with @LinkingObjects easier.
        EMPTY, // RealmResults that is always empty.
        RESULTS // RealmResults wrapping a Realm Core Results.
    }
    // Wrap existing native Results class
    private constructor(realm: RealmReference, results: NativePointer, clazz: KClass<T>, schema: Mediator) {
        this.mode = Mode.RESULTS
        this.realm = realm
        this.result = results
        this.clazz = clazz
        this.schema = schema
    }

    internal companion object {
        internal fun <T : RealmObject> fromQuery(realm: RealmReference, query: NativePointer, clazz: KClass<T>, schema: Mediator): RealmResultsImpl<T> {
            // realm_query_find_all doesn't fully evaluate until you interact with it.
            return RealmResultsImpl(realm, RealmInterop.realm_query_find_all(query), clazz, schema)
        }

        internal fun <T : RealmObject> fromResults(realm: RealmReference, results: NativePointer, clazz: KClass<T>, schema: Mediator): RealmResultsImpl<T> {
            return RealmResultsImpl(realm, results, clazz, schema)
        }
    }

    override fun realmState(): RealmState {
        return realm
    }

    override val size: Int
        get() = RealmInterop.realm_results_count(result).toInt()

    override fun get(index: Int): T {
        val link: Link = RealmInterop.realm_results_get<T>(result, index.toLong())
        val model = schema.createInstanceOf(clazz) as RealmObjectInternal
        model.link(realm, schema, clazz, link)
        @Suppress("UNCHECKED_CAST")
        return model as T
    }

    @Suppress("SpreadOperator")
    override fun query(query: String, vararg args: Any?): RealmResultsImpl<T> {
        try {
            return fromQuery(
                realm,
                RealmInterop.realm_query_parse(result, clazz.simpleName!!, query, *args),
                clazz,
                schema,
            )
        } catch (exception: RealmCoreException) {
            throw genericRealmCoreExceptionHandler("Invalid syntax for query `$query`", exception)
        }
    }

    override fun observe(): Flow<RealmResultsImpl<T>> {
        realm.checkClosed()
        return realm.owner.registerObserver(this)
    }

    override fun delete() {
        // TODO OPTIMIZE Are there more efficient ways to do this? realm_query_delete_all is not
        //  available in C-API yet, but should probably await final query design
        //  https://github.com/realm/realm-kotlin/issues/84
        RealmInterop.realm_results_delete_all(result)
    }

    /**
     * Returns a frozen copy of this query result. If it is already frozen, the same instance
     * is returned.
     */
    override fun freeze(realm: RealmReference): RealmResultsImpl<T> {
        val frozenDbPointer = realm.dbPointer
        val frozenResults = RealmInterop.realm_results_resolve_in(result, frozenDbPointer)
        return fromResults(realm, frozenResults, clazz, schema)
    }

    /**
     * Thaw the frozen query result, turning it back into a live, thread-confined RealmResults.
     */
    override fun thaw(realm: RealmReference): RealmResultsImpl<T> {
        val liveDbPointer = realm.dbPointer
        val liveResultPtr = RealmInterop.realm_results_resolve_in(result, liveDbPointer)
        return fromResults(realm, liveResultPtr, clazz, schema)
    }

    override fun registerForNotification(callback: io.realm.internal.interop.Callback): NativePointer {
        return RealmInterop.realm_results_add_notification_callback(result, callback)
    }

    override fun emitFrozenUpdate(
        frozenRealm: RealmReference,
        change: NativePointer,
        channel: SendChannel<RealmResultsImpl<T>>
    ): ChannelResult<Unit>? {
        val frozenResult = freeze(frozenRealm)
        return channel.trySend(frozenResult)
    }
}
