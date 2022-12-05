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

import io.realm.kotlin.internal.query.ObjectBoundQuery
import io.realm.kotlin.internal.util.trySendWithBufferOverflowCheck
import io.realm.kotlin.notifications.DeletedObject
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.BaseRealmObject
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Class that binds a RealmResults to an Object lifecycle. Flows resulting from this class would be
 * completed once the object gets deleted.
 *
 * It makes it possible to simulate the behavior of RealmLists on subqueries and backlinks.
 */
internal class ObjectBoundRealmResults<E : BaseRealmObject>(
    val targetObject: RealmObjectReference<*>,
    val realmResults: RealmResults<E>,
) : RealmResults<E> by realmResults,
    Flowable<ResultsChange<E>> {

    override val size: Int by realmResults::size

    override fun get(index: Int): E = realmResults[index]

    override fun query(query: String, vararg args: Any?): RealmQuery<E> = ObjectBoundQuery(
        targetObject,
        realmResults.query(query, *args)
    )

    /**
     * Because backlinks don't have native notifications when the target object gets deleted we
     * cannot properly close the communication channel.
     *
     * The logic within this flow tries to address this issue by combining a notification flow from the
     * target object, which emits deletion events, with the linking object notifications flow.
     *
     * First we filter the target object flow to discard any UpdateObject event. We only require of
     * the InitialObject event to allow the combine to emit values, and the DeletedObject to cancel the
     * flow.
     *
     * Then we combine it with the backlinks flow, and apply a transformation t
     * hat only emit
     * values, if the object has not been deleted, if not it closes cancels the flow.
     */

    override fun asFlow(): Flow<ResultsChange<E>> {
        return realmResults.asFlow().bind(targetObject)
    }
}

/**
 * Binds a flow to an object lifecycle. It allows flows on queries to complete once the object gets
 * deleted. It is used on sub-queries and backlinks.
 */
internal fun <T> Flow<T>.bind(reference: RealmObjectReference<out BaseRealmObject>): Flow<T> =
    channelFlow {
        // Listen for object deletions
        reference.asFlow()
            .map { it is DeletedObject }
            .onStart { false }
            .distinctUntilChanged()
            // Combine object deletion events with the actual result changes
            .combine(this@bind) { deleted: Boolean, resultsChange: T -> deleted to resultsChange }
            // We shouldn't use produceIn as that will fuse backpressure strategy to our
            // internal flows, but we cannot allow that as we rely on some of the internal
            // events for the logic to work, so just collect
            .collect { (deleted, resultChange) ->
                if (deleted) {
                    close()
                } else {
                    trySendWithBufferOverflowCheck(resultChange)?.let {
                        // Cancel scope if the user does not keep up to signal that we are loosing
                        // events
                        cancel(it)
                    }
                }
            }
    }
