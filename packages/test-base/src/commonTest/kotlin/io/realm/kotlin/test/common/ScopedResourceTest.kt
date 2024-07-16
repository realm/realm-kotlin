/*
 * Copyright 2023 Realm Inc.
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

package io.realm.kotlin.test.common

import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.RealmResource
import io.realm.kotlin.ScopedResource
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.internal.RealmStateHolder
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mapAndRelease
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test

class ScopedResourceTest {

    private lateinit var configuration: RealmConfiguration
    lateinit var realm : Realm

    @BeforeTest
    fun setup() {
         configuration = RealmConfiguration.Builder(setOf(Sample::class)).build()
        realm = Realm.open(configuration)
    }

    data class Unmanaged(val id: String)

    // Questions: Which cases do we want to address with query/result
    @Test
    fun inline() {
        val query: RealmQuery<Sample> = realm.query<Sample>()
        val result: RealmResults<Sample> = query.find()
        val unmanaged: List<Unmanaged> = result.mapAndRelease { it ->
            Unmanaged(it.stringField)
            // But what if we access it.link.asdf then we have a GC leak or need to track it.
        }
        // query and result is still referencing realm.version
        // all objects will be closed
    }

    // Questions: Which cases do we want to address with query/result
    @Test
    fun flow() = runBlocking {
        val query: RealmQuery<Sample> = realm.query<Sample>()
        val flow: Flow<ResultsChange<Sample>> = query.asFlow()
//        flow.collect { it ->
//            val result: RealmResults<Sample> = it.list
//            //
//        }
        // result should be released here
        // query is still referencing realm.version
        // flow references notifier.version
        // flow reference to notifier.version should be released when flow is complete
    }

    // Has the advantage that you don't need to transfer anything unless needed
    @Test
    fun scoped() {
        // FIXME Just required to initialize the notifier before anything else since `scoped` is touching the notifier.realm in this POC
        val x = realm.asFlow()
        val scope : ScopedResource = object : ScopedResource {}
        // Could we selectively return something that is then not released, a bit like write.
        // Can we do that - Would need to track all references in object graphs and exclude them from the list.
        val unmanged = realm.scoped {
            val scopedQuery: RealmQuery<Sample> = query<Sample>()
            val scopedResult: RealmResults<Sample> = scopedQuery.find()
            scopedResult.map { Unmanaged(it.stringField) }
        }
        unmanged.size
        println(realm.activeVersions())
        realm.close()
        Realm.deleteRealm(configuration)

        // scopedQuery, scopedResult and all objects accessed is closed

        // Idea is to resolves all query/result/object references inside scope and keep track
        // of them
        // Could be building block for exposing different scopes:
        // Realm.scope/pinned { } - Ensure that all query/results/objects reference the same versions
        //   Also solves issues
        // obj.pinned/latest(objects to resolve or automatic?) { ... } that automatically resolves all objects in the given scope - sort of adding an implicit findLatest(o) on all object refs (RealmResources)
//        RealmResource.pinned(x, u, c) {
//
//        }
        // Live ??

        // Questions
        // - Can we issue multiple references to the same version and close them individually?
    }

    suspend fun <T> Flow<T>.collectAndRelease(x: FlowCollector<T>) {
        this.collect {
            x.emit(it)
            it.realmReference.close()
        }
    }
    fun collectAndRelease() = runBlocking {
        realm.asFlow()
            // Implicit realm_close of the version after collection
            .collectAndRelease { it ->
            }
    }

    class RealmResourceContext: CoroutineContext.Element {

        override val key: CoroutineContext.Key<*> = Key

        fun close() {}
        public companion object Key : CoroutineContext.Key<RealmResourceContext>
    }

    fun flowContext() = runBlocking {
        withContext(this.coroutineContext + RealmResourceContext()) {

        val resources = coroutineContext[RealmResourceContext.Key]!!
        realm.asScopedFlow()
            .collectAndRelease { it ->

            }
        }
    }


    class ResourceScope: CoroutineScope {
        override val coroutineContext: CoroutineContext
            get() = TODO("Not yet impl]emented")
    }

    @Test
    @Ignore // Not doable since we cannot thaw a realm ... or maybe misreading comments from "realm_create_thread_safe_reference"
    fun live() {
        // FIXME Just required to initialize the notifier before anything else
        val x = realm.asFlow()
        val scope : ScopedResource = object : ScopedResource {}
        // Could we selectively return something that is then not released, a bit like write.
        // Can we do that - Would need to track all references in object graphs and exclude them from the list.
        val unmanged = realm.scoped {
            val scopedQuery: RealmQuery<Sample> = query<Sample>()
            val scopedResult: RealmResults<Sample> = scopedQuery.find()
            scopedResult
            scopedResult.map { Unmanaged(it.stringField) }
        }
        unmanged.size
        println(realm.activeVersions())
        realm.close()
        Realm.deleteRealm(configuration)

        // scopedQuery, scopedResult and all objects accessed is closed

        // Idea is to resolves all query/result/object references inside scope and keep track
        // of them
        // Could be building block for exposing different scopes:
        // Realm.scope/pinned { } - Ensure that all query/results/objects reference the same versions
        //   Also solves issues
        // obj.pinned/latest(objects to resolve or automatic?) { ... } that automatically resolves all objects in the given scope - sort of adding an implicit findLatest(o) on all object refs (RealmResources)
//        RealmResource.pinned(x, u, c) {
//
//        }
        // Live ??

        // Questions
        // - Can we issue multiple references to the same version and close them individually?
    }
}
