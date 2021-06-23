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
 */

package io.realm.internal

import io.realm.BaseRealm
import io.realm.RealmConfiguration
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.interop.RealmInterop
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.reflect.KClass

// FIXME Just a prototype of the Notifier to be able to test
// TODO Maybe abstract common base functionality of thread local live realm with dispatcher out from
//  this and SuspendableWriter
class Notifier(
    configuration: RealmConfiguration,
    val dispatcher: CoroutineDispatcher = configuration.writeDispatcher(
        configuration.path
    )
) {
    class NotifierRealm(configuration: RealmConfiguration, dispatcher: CoroutineDispatcher) :
        BaseRealm(configuration, RealmInterop.realm_open(configuration.nativeConfig, dispatcher))

    // Must only be accessed from the dispatchers thread
    private val realm: BaseRealm by lazy {
        NotifierRealm(configuration, dispatcher)
    }

    fun <T : RealmObject> observe(clazz: KClass<T>): Flow<RealmResults<T>> {
        return callbackFlow<RealmResults<T>> {
            val token = realm.objects(clazz).observe {
                offer(it)
            }
            awaitClose { token.cancel() }
        }
    }
    inline fun <reified T : RealmObject> observe(): Flow<RealmResults<T>> {
        return observe(T::class)
    }
}
