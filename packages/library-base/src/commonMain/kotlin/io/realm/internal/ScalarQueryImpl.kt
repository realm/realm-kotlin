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

import io.realm.RealmScalarQuery
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlin.reflect.KClass

/**
 * TODO : query
 */
internal abstract class ScalarQueryImpl<T : Any> constructor(
    protected val realmReference: RealmReference,
    protected val queryPointer: NativePointer,
    protected val mediator: Mediator
) : RealmScalarQuery<T>, Thawable<BaseResults<T>> {

    abstract fun getScalarClass(): KClass<T>

    abstract fun Flow<BaseResults<T>?>.queryMapper(): Flow<T?>

    override fun thaw(liveRealm: RealmReference): BaseResults<T> {
        val liveDbPointer = liveRealm.dbPointer
        val queryResults = RealmInterop.realm_query_find_all(queryPointer)
        val liveResultPtr = RealmInterop.realm_results_resolve_in(queryResults, liveDbPointer)
        return ScalarResults(liveRealm, liveResultPtr, getScalarClass(), mediator)
    }

    override fun asFlow(): Flow<T?> = realmReference.owner
        .registerObserver(this)
        .onStart { realmReference.checkClosed() }
        .queryMapper()
}

/**
 * TODO : query
 */
internal class CountQuery constructor(
    realmReference: RealmReference,
    queryPointer: NativePointer,
    mediator: Mediator
) : ScalarQueryImpl<Long>(realmReference, queryPointer, mediator) {

    override fun find(): Long = RealmInterop.realm_query_count(queryPointer)

    override fun getScalarClass(): KClass<Long> = Long::class

    override fun Flow<BaseResults<Long>?>.queryMapper(): Flow<Long> = this.map {
        requireNotNull(it).size.toLong()
    }
}

/**
 * TODO : query
 */
internal class AverageQuery constructor(
    realmReference: RealmReference,
    queryPointer: NativePointer,
    mediator: Mediator,
    private val clazz: KClass<out Any>,
    private val property: String
) : ScalarQueryImpl<Double>(realmReference, queryPointer, mediator) {

    override fun find(): Double? = findInternal(queryPointer)

    override fun getScalarClass(): KClass<Double> = Double::class

    override fun Flow<BaseResults<Double>?>.queryMapper(): Flow<Double?> = this.map {
        it?.let { results ->
            findFromResults(results.nativePointer)
        }
    }

    private fun findInternal(queryPointer: NativePointer): Double? =
        findFromResults(RealmInterop.realm_query_find_all(queryPointer))

    private fun findFromResults(resultsPointer: NativePointer): Double? =
        RealmInterop.realm_get_col_key(realmReference.dbPointer, clazz.simpleName!!, property)
            .let { colKey -> RealmInterop.realm_results_average(resultsPointer, colKey.key) }
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
//import io.realm.RealmScalarQuery
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
//internal abstract class ScalarQueryImpl<T : Any> constructor(
//    protected val realmReference: RealmReference,
//    protected val queryPointer: Lazy<NativePointer>,
//    protected val mediator: Mediator
//) : RealmScalarQuery<T>, Thawable<BaseResults<T>> {
//
//    abstract fun getScalarClass(): KClass<T>
//
//    abstract fun Flow<BaseResults<T>?>.queryMapper(): Flow<T?>
//
//    override fun thaw(liveRealm: RealmReference): BaseResults<T> {
//        val liveDbPointer = liveRealm.dbPointer
//        val queryResults = RealmInterop.realm_query_find_all(queryPointer.value)
//        val liveResultPtr = RealmInterop.realm_results_resolve_in(queryResults, liveDbPointer)
//        return ScalarResults(liveRealm, liveResultPtr, getScalarClass(), mediator)
//    }
//
//    override fun asFlow(): Flow<T?> = realmReference.owner
//        .registerObserver(this)
//        .onStart { realmReference.checkClosed() }
//        .queryMapper()
//}
//
///**
// * TODO : query
// */
//internal class CountQuery constructor(
//    realmReference: RealmReference,
//    queryPointer: Lazy<NativePointer>,
//    mediator: Mediator
//) : ScalarQueryImpl<Long>(realmReference, queryPointer, mediator) {
//
//    override fun find(): Long = RealmInterop.realm_query_count(queryPointer.value)
//
//    override fun getScalarClass(): KClass<Long> = Long::class
//
//    override fun Flow<BaseResults<Long>?>.queryMapper(): Flow<Long> = this.map {
//        requireNotNull(it).size.toLong()
//    }
//}
//
///**
// * TODO : query
// */
//internal class AverageQuery constructor(
//    realmReference: RealmReference,
//    queryPointer: Lazy<NativePointer>,
//    mediator: Mediator,
//    private val clazz: KClass<out Any>,
//    private val property: String
//) : ScalarQueryImpl<Double>(realmReference, queryPointer, mediator) {
//
//    override fun find(): Double? = findInternal(queryPointer.value)
//
//    override fun getScalarClass(): KClass<Double> = Double::class
//
//    override fun Flow<BaseResults<Double>?>.queryMapper(): Flow<Double?> = this.map {
//        it?.let { results ->
//            findFromResults(results.nativePointer)
//        }
//    }
//
//    private fun findInternal(queryPointer: NativePointer): Double? =
//        findFromResults(RealmInterop.realm_query_find_all(queryPointer))
//
//    private fun findFromResults(resultsPointer: NativePointer): Double? =
//        RealmInterop.realm_get_col_key(realmReference.dbPointer, clazz.simpleName!!, property)
//            .let { colKey -> RealmInterop.realm_results_average(resultsPointer, colKey.key) }
//}
