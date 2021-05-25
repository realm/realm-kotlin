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
package io.realm.shared

import io.realm.Cancellable
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.util.PlatformUtils
import io.realm.util.RunLoopThread
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import test.Sample
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

private const val INITIAL = "Hello, World!"
private const val FIRST = "FIRST"
private const val SECOND = "SECOND"

/**
 * Verify common behavior for notifications across all supported.
 * See [RealmTests], [RealmResultsTests], [RealmObjectTests] for notification tests only relevant to that specific type.
 */
@OptIn(ExperimentalTime::class)
class NotificationTests {

    enum class ClassType {
        REALM,
        REALM_RESULTS,
        REALM_LIST,
        REALM_OBJECT
    }

    lateinit var tmpDir: String
    lateinit var configuration: RealmConfiguration
    lateinit var realm: Realm

    private val testScope = CoroutineScope(CoroutineName("TestScope"))

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration(path = "$tmpDir/default.realm", schema = setOf(Sample::class))
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        testScope.cancel()
        realm.close()
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun addChangeListener() {
        ClassType.values().forEach {
            when(it) {
                ClassType.REALM_RESULTS -> addChangeListenerResults()
                else -> {
                    // FIXME: This section shouldn't be ignored
                }
            }
        }
    }

    @Test
    fun cancelTokenStopsListener() {
        ClassType.values().forEach {
            when(it) {
                ClassType.REALM_RESULTS -> cancelTokenStopsListenerResults()
                else -> {
                    // FIXME: This section shouldn't be ignored
                }
            }
        }
    }

    @Test
    fun observe() {
        ClassType.values().forEach {
            when(it) {
                ClassType.REALM -> observeRealm()
                ClassType.REALM_RESULTS -> observeRealmResults()
                ClassType.REALM_LIST -> observeRealmList()
                ClassType.REALM_OBJECT -> observeRealmObject()
                else -> throw NotImplementedError(it.toString())
            }
        }
    }

    @Test
    fun cancelObserve() {

    }




    private fun observeRealmObject() {
        // FIXME
    }

    private fun observeRealmList() {
        // FIXME
    }

    private fun observeRealmResults() {
        @Suppress("JoinDeclarationAndAssignment")
        lateinit var listenerJob: Job
        listenerJob = testScope.launch {
            realm.objects(Sample::class).observe().collect {
                assertEquals(1, it.size)
                listenerJob.cancel()
            }
        }

        runBlocking {
            realm.write {
                copyToRealm(Sample().apply { stringField = "Foo" })
            }
            listenerJob.join()
        }
    }

    private fun observeRealm() {
        // FIXME
    }


    fun addChangeListenerResults() = RunLoopThread().run {
        realm.objects(Sample::class).addChangeListener {
            assertEquals(1, it.size)
            terminate()
        }

        realm.writeBlocking {
            copyToRealm(Sample())
        }
    }


    fun cancelTokenStopsListenerResults() = RunLoopThread().run {

        var val1 = 0
        lateinit var token1: Cancellable
        token1 = realm.objects(Sample::class).addChangeListener {
            val1 = it.size
            assertEquals(1, it.size)
            token1.cancel()
        }

        var val2 = 0
        val token2 = realm.objects(Sample::class).addChangeListener {
            val2 = it.size
            assertEquals(1, it.size)
            if (val2 == 2) {
                assertEquals(1, val1)
                terminate()
            }
        }

        realm.writeBlocking {
            copyToRealm(Sample())
        }

        realm.writeBlocking {
            copyToRealm(Sample())
        }
    }


}
