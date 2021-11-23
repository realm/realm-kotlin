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

/**
 * TODO : query
 */
// FIXME API-QUERY Final query design is tracked in https://github.com/realm/realm-kotlin/issues/84
//  - Lazy API makes it harded to debug
//  - Postponing execution to actually accessing the elements also prevents query parser errors to
//    be raised. Maybe we can get an option to prevalidate queries in the C-API?
internal abstract class BaseResults<E : Any> constructor(
    protected val realm: RealmReference,
    internal val nativePointer: NativePointer,
    protected val clazz: KClass<E>,
    protected val mediator: Mediator,
    protected val mode: Mode = Mode.RESULTS
) : AbstractList<E>(), RealmResults<E>, Freezable<BaseResults<E>>, Thawable<BaseResults<E>>, Observable<BaseResults<E>>, RealmStateHolder {

    enum class Mode {
        // FIXME Needed to make working with @LinkingObjects easier.
        EMPTY, // RealmResults that is always empty.
        RESULTS // RealmResults wrapping a Realm Core Results.
    }

    abstract fun instantiateResults(
        realmReference: RealmReference,
        nativePointer: NativePointer,
        clazz: KClass<E>,
        mediator: Mediator
    ): BaseResults<E>

    override val size: Int
        get() = RealmInterop.realm_results_count(nativePointer).toInt()

    @Suppress("SpreadOperator")
    override fun query(query: String, vararg args: Any?): BaseResults<E> {
        try {
            return instantiateResults(
                realm,
                RealmInterop.realm_query_find_all(
                    RealmInterop.realm_query_parse(nativePointer, clazz.simpleName!!, query, *args)
                ),
                clazz,
                mediator
            )
        } catch (exception: RealmCoreException) {
            throw genericRealmCoreExceptionHandler("Invalid syntax for query `$query`", exception)
        }
    }

    override fun asFlow(): Flow<BaseResults<E>> {
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
    override fun freeze(frozenRealm: RealmReference): BaseResults<E> {
        val frozenDbPointer = frozenRealm.dbPointer
        val frozenResults = RealmInterop.realm_results_resolve_in(nativePointer, frozenDbPointer)
        return instantiateResults(frozenRealm, frozenResults, clazz, mediator)
    }

    /**
     * Thaw the frozen query result, turning it back into a live, thread-confined RealmResults.
     */
    override fun thaw(liveRealm: RealmReference): BaseResults<E> {
        val liveDbPointer = liveRealm.dbPointer
        val liveResultPtr = RealmInterop.realm_results_resolve_in(nativePointer, liveDbPointer)
        return instantiateResults(liveRealm, liveResultPtr, clazz, mediator)
    }

    override fun registerForNotification(callback: io.realm.internal.interop.Callback): NativePointer {
        return RealmInterop.realm_results_add_notification_callback(nativePointer, callback)
    }

    override fun emitFrozenUpdate(
        frozenRealm: RealmReference,
        change: NativePointer,
        channel: SendChannel<BaseResults<E>>
    ): ChannelResult<Unit>? {
        val frozenResult = freeze(frozenRealm)
        return channel.trySend(frozenResult)
    }

    override fun realmState(): RealmState = realm
}

/**
 * TODO : query
 */
internal class ElementResults<E : RealmObject> constructor(
    realm: RealmReference,
    nativePointer: NativePointer,
    clazz: KClass<E>,
    schema: Mediator
) : BaseResults<E>(realm, nativePointer, clazz, schema) {

    override fun get(index: Int): E {
        val link = RealmInterop.realm_results_get<Link>(nativePointer, index.toLong())
        val model = mediator.createInstanceOf(clazz)
        model.link(realm, mediator, clazz, link)
        @Suppress("UNCHECKED_CAST")
        return model as E
    }

    override fun instantiateResults(
        realmReference: RealmReference,
        nativePointer: NativePointer,
        clazz: KClass<E>,
        mediator: Mediator
    ): BaseResults<E> = ElementResults(realm, nativePointer, clazz, mediator)
}

/**
 * TODO : query
 */
internal class ScalarResults<E : Any> constructor(
    realm: RealmReference,
    nativePointer: NativePointer,
    clazz: KClass<E>,
    mediator: Mediator
) : BaseResults<E>(realm, nativePointer, clazz, mediator) {

    override fun get(index: Int): E = RealmInterop.realm_results_get(nativePointer, index.toLong())

    override fun instantiateResults(
        realmReference: RealmReference,
        nativePointer: NativePointer,
        clazz: KClass<E>,
        mediator: Mediator
    ): BaseResults<E> = ScalarResults(realm, nativePointer, clazz, mediator)
}
