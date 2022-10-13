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

import io.realm.kotlin.notifications.DeletedObject
import io.realm.kotlin.notifications.ObjectChange
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedObject
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.BaseRealmObject
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.filter

internal class RealmLinkingObjectsImpl<E : BaseRealmObject>(
    val targetObject: RealmObjectReference<*>?,
    val realmResults: RealmResultsImpl<E>,
) : AbstractList<E>(),
    RealmResults<E> by realmResults,
    Flowable<ResultsChange<E>> {

    override val size: Int by realmResults::size

    override fun get(index: Int): E = realmResults[index]

    override fun query(query: String, vararg args: Any?): RealmQuery<E> =
        realmResults.query(query, *args)

    /**
     * Because linking objects don't have native notifications when the target object gets deleted we
     * cannot properly close the communication channel.
     *
     * The logic within this flow tries to address this issue by combining a notification flow from the
     * target object, that has the deletion event, with the linking object notifications flow.
     *
     * The fix uses the combine, that combines new events on a flow with the latest event emitted by
     * the other flow.
     *
     * First we filter the target object flow to discard any UpdateObject event. We only require of
     * the InitialObject event to allow the combine to emit values, and the DeletedObject to cancel the
     * flow.
     *
     * Then we combine it with the linking objects flow, and apply a transformation t
     * hat only emit
     * values, if the object has not been deleted, if not it closes cancels the flow.
     */
    override fun asFlow(): Flow<ResultsChange<E>> {
        val resultsFlow: Flow<ResultsChange<E>> = realmResults.asFlow()
        val targetFlow: Flow<ObjectChange<*>> = targetObject!!.asFlow()

        return targetFlow
            .filter { change: ObjectChange<*> ->
                change !is UpdatedObject
            }
            .combineTransform(resultsFlow) { objectChange: ObjectChange<*>, resultsChange: ResultsChange<E> ->
                if (objectChange is DeletedObject) {
                    currentCoroutineContext().cancel()
                } else {
                    emit(resultsChange)
                }
            }
    }
}
