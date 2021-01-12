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

package io.realm

import io.realm.runtimeapi.RealmModule
import io.realm.util.RunLoopThread
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import test.Sample
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

private val INITIAL = "Hello, World!"
private val FIRST = "FIRST"
private val SECOND = "SECOND"

@OptIn(ExperimentalTime::class)
class NotificationTests {

    @RealmModule(Sample::class)
    class MySchema

    lateinit var configuration: RealmConfiguration

    @BeforeTest
    fun setup() {
        configuration = RealmConfiguration.Builder(schema = MySchema()).build()
        val realm = Realm.open(configuration)
        // FIXME Cleaning up realm to overcome lack of support for deleting actual files
        //  https://github.com/realm/realm-kotlin/issues/95
        realm.beginTransaction()
        realm.objects(Sample::class).delete()
        realm.commitTransaction()
        assertEquals(0, realm.objects(Sample::class).size, "Realm is not empty")
    }

    @Test
    fun objectListener() = RunLoopThread().run {
        val c = Channel<String>(1)

        val realm = Realm.open(configuration)
        realm.beginTransaction()
        val sample = realm.create(Sample::class).apply { stringField = INITIAL }
        realm.commitTransaction()

        sample.observe {
            val stringField = sample.stringField
            this@run.launch {
                c.send(stringField)
            }
        }

        launch {
            realm.beginTransaction()
            assertEquals(INITIAL, c.receive())
            sample.stringField = FIRST
            realm.commitTransaction()
            assertEquals(FIRST, c.receive())
            realm.close()
            terminate()
        }
    }

    @Test
    fun objectListener_cancel() = RunLoopThread().run {
        val c = Channel<String>(1)

        val realm = Realm.open(configuration)
        realm.beginTransaction()
        val sample = realm.create(Sample::class).apply { stringField = INITIAL }
        realm.commitTransaction()

        val token = sample.observe {
            val stringField = sample.stringField
            this@run.launch {
                c.send(stringField)
            }
        }

        launch {
            realm.beginTransaction()
            assertEquals(INITIAL, c.receive())
            sample.stringField = FIRST
            realm.commitTransaction()
            assertEquals(FIRST, c.receive())

            token.cancel()

            realm.beginTransaction()
            sample.stringField = SECOND
            realm.commitTransaction()

            delay(1.seconds)
            assertTrue(c.isEmpty)

            realm.close()
            terminate()
        }
    }

    @Test
    fun resultsListener() = RunLoopThread().run {
        val c = Channel<List<Sample>>(1)

        val realm = Realm.open(configuration)

        val results = realm.objects(Sample::class)
        val token = results.observe {
            val updatedResults = results.toList()
            this@run.launch { c.send(updatedResults) }
        }

        launch {
            realm.beginTransaction()
            assertEquals(0, c.receive().size)
            val sample = realm.create(Sample::class).apply { stringField = INITIAL }
            realm.commitTransaction()

            val result = c.receive()
            assertEquals(1, result.size)
            assertEquals(sample.stringField, result[0].stringField)

            token.cancel()

            realm.beginTransaction()
            realm.create(Sample::class).apply { stringField = INITIAL }
            realm.commitTransaction()

            delay(1.seconds)
            assertTrue(c.isEmpty)

            realm.close()
            terminate()
        }
    }

    // FIXME Add test for closing realm without unregistering listeners
    @Test
    fun closeWithoutCancelingListener() = RunLoopThread().run {
        val c = Channel<String>(1)

        val realm = Realm.open(configuration)
        realm.beginTransaction()
        val sample = realm.create(Sample::class).apply { stringField = INITIAL }
        realm.commitTransaction()

        sample.observe {
            val stringField = sample.stringField
            this@run.launch {
                c.send(stringField)
            }
        }

        launch {
            realm.beginTransaction()
            assertEquals(INITIAL, c.receive())
            sample.stringField = FIRST
            realm.commitTransaction()
            assertEquals(FIRST, c.receive())
            realm.close()
            terminate()
        }
    }

    @Test
    fun closeWithoutCancel() = RunLoopThread().run {
        val c = Channel<String>(1)

        val realm = Realm.open(configuration)
        realm.beginTransaction()
        val sample = realm.create(Sample::class).apply { stringField = INITIAL }
        realm.commitTransaction()

        val token = Realm.observe(sample) {
            val stringField = sample.stringField
            this@run.launch {
                c.send(stringField)
            }
        }

        launch {
            realm.beginTransaction()
            assertEquals(INITIAL, c.receive())
            sample.stringField = FIRST
            realm.commitTransaction()
            assertEquals(FIRST, c.receive())
            // Verify that closing does not cause troubles even though notifications are not
            // cancelled.
            // NOTE Listener is not released either, so leaking the callbacks.
            realm.close()
            // Yield to allow any pending notifications to be triggered
            delay(1.seconds)
            assertTrue(c.isEmpty)
            terminate()
        }
    }

}
