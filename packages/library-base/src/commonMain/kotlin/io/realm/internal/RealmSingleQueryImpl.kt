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
 *
 */

package io.realm.internal

import io.realm.RealmObject
import io.realm.RealmSingleQuery
import io.realm.internal.interop.Callback
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlin.reflect.KClass

internal class RealmSingleQueryImpl<E : RealmObject>(
    private val realmReference: RealmReference,
    private val queryPointer: NativePointer,
    private val clazz: KClass<E>,
    private val mediator: Mediator
) : RealmSingleQuery<E>, Thawable<BaseResults<E>> {

    override fun find(): E? = RealmInterop.realm_query_find_first(queryPointer)
        ?.toRealmObject(clazz, mediator, realmReference)

    // It's not possible to subscribe to the object in case the result from calling 'first' the
    // first time is null because there aren't any objects
    override fun asFlow(): Flow<E?> {
        return realmReference.owner
            .registerObserver(this)
            .onStart { realmReference.checkClosed() }
            .map { results ->
                if (results.isEmpty()) {
                    null
                } else {
                    val first = results[0]
//                    (first as RealmObjectInternal).freeze()
                    first
                }
            }
    }

    // Call query_find_first and freeze object or use LIMIT(1) and return results.first()?
    override fun thaw(liveRealm: RealmReference): BaseResults<E>? {
        val resultsPointer = RealmInterop.realm_query_find_all(
            RealmInterop.realm_query_append_query(queryPointer, "TRUEPREDICATE LIMIT(1)")
        )
        val liveResults = RealmInterop.realm_results_resolve_in(resultsPointer, liveRealm.dbPointer)
        return ElementResults(liveRealm, liveResults, clazz, mediator)
    }
}


///*
// * Copyright 2021 Realm Inc.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// *
// */
//
//package io.realm.internal
//
//import io.realm.RealmObject
//import io.realm.RealmSingleQuery
//import io.realm.internal.interop.NativePointer
//import io.realm.internal.interop.RealmInterop
//import kotlinx.coroutines.channels.ChannelResult
//import kotlinx.coroutines.channels.SendChannel
//import kotlinx.coroutines.flow.Flow
//import kotlinx.coroutines.flow.map
//import kotlinx.coroutines.flow.onStart
//import kotlin.reflect.KClass
//
//internal class RealmSingleQueryImpl<E : RealmObject>(
//    private val realmReference: RealmReference,
//    private val queryPointer: NativePointer,
//    private val clazz: KClass<E>,
//    private val mediator: Mediator
//) : RealmSingleQuery<E>, Freezable<BaseResults<E>>, Thawable<BaseResults<E>>, Observable<E> {
//
//    override fun find(): E? = RealmInterop.realm_query_find_first(queryPointer)
//        ?.toRealmObject(clazz, mediator, realmReference)
//
//    // It's not possible to subscribe to the object in case the result from calling 'first' the
//    // first time is null because there aren't any objects - use LIMIT(1) instead?
//    override fun asFlow(): Flow<E?> = realmReference.owner
//        .registerObserver(this)
//        .onStart { realmReference.checkClosed() }
//        .map { results ->
//            if (results.isEmpty()) null
//            else {
//                val first: E = results.first()
//                (first as RealmObjectInternal).free
//                first
//            }
//        }
//
////    override fun asFlow(): Flow<E?> = realmReference.owner
////        .registerObserver(this)
////        .onStart { realmReference.checkClosed() }
////        .map { first ->
////            first as E?
////        }
//
//    override fun freeze(frozenRealm: RealmReference): Observable<BaseResults<E>>? {
//        TODO("Not yet implemented")
//    }
//
//    override fun thaw(liveRealm: RealmReference): BaseResults<E>? {
//        val resultsPointer = RealmInterop.realm_query_find_all(
//            RealmInterop.realm_query_append_query(queryPointer, "TRUEPREDICATE LIMIT(1)")
//        )
//        val liveResults = RealmInterop.realm_results_resolve_in(resultsPointer, liveRealm.dbPointer)
//        return ElementResults(liveRealm, liveResults, clazz, mediator)
//    }
//
//    override fun registerForNotification(callback: Callback): NativePointer {
//        TODO("Not yet implemented")
//    }
//
//    override fun emitFrozenUpdate(
//        frozenRealm: RealmReference,
//        change: NativePointer,
//        channel: SendChannel<E>
//    ): ChannelResult<Unit>? {
//        TODO("Not yet implemented")
//    }
//
////    override fun thaw(liveRealm: RealmReference): RealmObjectInternal? =
////        find()?.let { first ->
////            (first as RealmObjectInternal).thaw(liveRealm)
////        }
//}


//package io.realm.internal
//
//import io.realm.RealmObject
//import io.realm.RealmSingleQuery
//import io.realm.internal.interop.NativePointer
//import io.realm.internal.interop.RealmInterop
//import kotlinx.coroutines.flow.Flow
//import kotlinx.coroutines.flow.map
//import kotlinx.coroutines.flow.onStart
//import kotlin.reflect.KClass
//
///**
// * TODO : query
// */
//internal class RealmSingleQueryImpl<E : RealmObject>(
//    private val realmReference: RealmReference,
//    private val queryPointer: NativePointer,
//    private val clazz: KClass<E>,
//    private val mediator: Mediator
//) : RealmSingleQuery<E>, Thawable<BaseResults<E>> {
//
//    override fun find(): E? = RealmInterop.realm_query_find_first(queryPointer)
//        ?.toRealmObject(clazz, mediator, realmReference)
//
//    override fun asFlow(): Flow<E?> {
//        return realmReference.owner
//            .registerObserver(this)
//            .onStart { realmReference.checkClosed() }
//            .map { results ->
//                if (results.isEmpty()) null
//                else results.first()
//            }
//    }
//
//    override fun thaw(liveRealm: RealmReference): Observable<BaseResults<E>>? {
//        val resultsPointer = RealmInterop.realm_query_find_all(queryPointer)
//        val liveResults = RealmInterop.realm_results_resolve_in(resultsPointer, liveRealm.dbPointer)
//        return ElementResults(liveRealm, liveResults, clazz, mediator)
//    }
//}
