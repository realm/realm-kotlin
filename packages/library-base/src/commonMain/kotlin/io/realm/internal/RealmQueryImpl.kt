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
import io.realm.RealmQuery
import io.realm.RealmResults
import io.realm.RealmScalarQuery
import io.realm.RealmSingleQuery
import io.realm.Sort
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlin.reflect.KClass

/**
 * TODO : query
 */
internal class RealmQueryImpl<E : RealmObject> constructor(
    private val realmReference: RealmReference,
    private val clazz: KClass<E>,
    private val mediator: Mediator,
    private val query: String,
    private vararg val args: Any?
) : RealmQuery<E>, Thawable<BaseResults<E>> {

    @Suppress("SpreadOperator")
    private val queryPointer: Lazy<NativePointer> = lazy {
        // TODO : query - make it so that it takes into consideration previous queries
        RealmInterop.realm_query_parse(
            realmReference.dbPointer,
            clazz.simpleName!!,
            query,
            *args
        )
    }

    private val resultsPointer: NativePointer by lazy {
        RealmInterop.realm_query_find_all(queryPointer.value)
    }

    override fun find(): RealmResults<E> =
        ElementResults(realmReference, resultsPointer, clazz, mediator)

    override fun query(filter: String, vararg arguments: Any?): RealmQuery<E> {
        TODO("Not yet implemented")
    }

    override fun sort(property: String, sortOrder: Sort): RealmQuery<E> {
        TODO("Not yet implemented")
    }

    override fun sort(vararg propertyAndSortOrder: Pair<String, Sort>): RealmQuery<E> {
        TODO("Not yet implemented")
    }

    override fun distinct(property: String): RealmQuery<E> {
        TODO("Not yet implemented")
    }

    override fun first(): RealmSingleQuery<E> {
        TODO("Not yet implemented")
    }

    override fun <T : Any> min(property: String, type: KClass<T>): RealmScalarQuery<T> {
        TODO("Not yet implemented")
    }

    override fun <T : Any> max(property: String, type: KClass<T>): RealmScalarQuery<T> {
        TODO("Not yet implemented")
    }

    override fun <T : Any> sum(property: String, type: KClass<T>): RealmScalarQuery<T> {
        TODO("Not yet implemented")
    }

    override fun <T : Any> average(property: String, type: KClass<T>): RealmScalarQuery<Double> {
        if (!type.isNumber()) {
            throw IllegalArgumentException("Average can only be executed on numeral properties.")
        }
        return AverageQuery(realmReference, queryPointer, mediator, clazz, property)
    }

    override fun count(): RealmScalarQuery<Long> =
        CountQuery(realmReference, queryPointer, mediator)

    override fun thaw(liveRealm: RealmReference): BaseResults<E> {
        val liveResults = RealmInterop.realm_results_resolve_in(resultsPointer, liveRealm.dbPointer)
        return ElementResults(liveRealm, liveResults, clazz, mediator)
    }

    override fun asFlow(): Flow<RealmResults<E>> = realmReference.owner
        .registerObserver(this)
        .onStart { realmReference.checkClosed() }

    private fun KClass<*>.isNumber(): Boolean {
        // TODO Expand to support other numeric types, e.g. Decimal128
        return this.simpleName == Int::class.simpleName ||
            this.simpleName == Double::class.simpleName ||
            this.simpleName == Flow::class.simpleName ||
            this.simpleName == Long::class.simpleName
    }
}
