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
package io.realm.kotlin.internal.query

import io.realm.kotlin.internal.ObjectBoundRealmResults
import io.realm.kotlin.internal.RealmObjectReference
import io.realm.kotlin.internal.bind
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmScalarNullableQuery
import io.realm.kotlin.query.RealmScalarQuery
import io.realm.kotlin.query.RealmSingleQuery
import io.realm.kotlin.query.Sort
import io.realm.kotlin.types.BaseRealmObject
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * This set of classes wraps the queries around the lifecycle of a Realm object, once the
 * object is deleted the flow would complete.
 */
internal class ObjectBoundQuery<E : BaseRealmObject>(
    val targetObject: RealmObjectReference<*>,
    val realmQuery: RealmQuery<E>,
) : RealmQuery<E> by realmQuery {
    override fun find(): RealmResults<E> = ObjectBoundRealmResults(
        targetObject,
        realmQuery.find()
    )

    override fun query(filter: String, vararg arguments: Any?): RealmQuery<E> = ObjectBoundQuery(
        targetObject,
        realmQuery.query(filter, *arguments)
    )

    override fun asFlow(): Flow<ResultsChange<E>> = realmQuery.asFlow().bind(targetObject)

    override fun sort(property: String, sortOrder: Sort): RealmQuery<E> = ObjectBoundQuery(
        targetObject,
        realmQuery.sort(property, sortOrder)
    )

    override fun sort(
        propertyAndSortOrder: Pair<String, Sort>,
        vararg additionalPropertiesAndOrders: Pair<String, Sort>
    ): RealmQuery<E> = ObjectBoundQuery(
        targetObject,
        realmQuery.sort(propertyAndSortOrder, *additionalPropertiesAndOrders)
    )

    override fun distinct(property: String, vararg extraProperties: String): RealmQuery<E> =
        ObjectBoundQuery(
            targetObject,
            realmQuery.distinct(property, *extraProperties)
        )

    override fun limit(limit: Int): RealmQuery<E> = ObjectBoundQuery(
        targetObject,
        realmQuery.limit(limit)
    )

    override fun first(): RealmSingleQuery<E> = ObjectBoundRealmSingleQuery(
        targetObject,
        realmQuery.first()
    )

    override fun <T : Any> min(property: String, type: KClass<T>): RealmScalarNullableQuery<T> =
        ObjectBoundRealmScalarNullableQuery(
            targetObject,
            realmQuery.min(property, type)
        )

    override fun <T : Any> max(property: String, type: KClass<T>): RealmScalarNullableQuery<T> =
        ObjectBoundRealmScalarNullableQuery(
            targetObject,
            realmQuery.max(property, type)
        )

    override fun <T : Any> sum(property: String, type: KClass<T>): RealmScalarQuery<T> =
        ObjectBoundRealmScalarQuery(
            targetObject,
            realmQuery.sum(property, type)
        )

    override fun count(): RealmScalarQuery<Long> = ObjectBoundRealmScalarQuery(
        targetObject,
        realmQuery.count()
    )
}

internal class ObjectBoundRealmSingleQuery<E : BaseRealmObject>(
    val targetObject: RealmObjectReference<*>,
    val realmQuery: RealmSingleQuery<E>
) : RealmSingleQuery<E> by realmQuery {
    override fun asFlow(): Flow<SingleQueryChange<E>> = realmQuery.asFlow().bind(targetObject)
}

internal class ObjectBoundRealmScalarNullableQuery<E>(
    val targetObject: RealmObjectReference<*>,
    val realmQuery: RealmScalarNullableQuery<E>
) : RealmScalarNullableQuery<E> by realmQuery {
    override fun asFlow(): Flow<E?> = realmQuery.asFlow().bind(targetObject)
}

internal class ObjectBoundRealmScalarQuery<E>(
    val targetObject: RealmObjectReference<*>,
    val realmQuery: RealmScalarQuery<E>
) : RealmScalarQuery<E> by realmQuery {
    override fun asFlow(): Flow<E> = realmQuery.asFlow().bind(targetObject)
}
