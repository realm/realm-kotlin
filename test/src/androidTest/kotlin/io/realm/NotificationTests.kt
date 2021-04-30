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

import io.realm.util.PlatformUtils
import io.realm.util.RunLoopThread
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import test.Sample
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

private const val INITIAL = "Hello, World!"
private const val FIRST = "FIRST"
private const val SECOND = "SECOND"

// FIXME This file is sym-linked into the `nativeTest`-equivalent. Ideally it would just all be in
//  `commonTest` but as it is currently not possible to trigger common tests on Android it is
//  replicated in the various source sets. Sym-linking to `commonTest` would create overlapping
//  definitions.
//  https://youtrack.jetbrains.com/issue/KT-34535
@OptIn(ExperimentalTime::class)
class NotificationTests {

    lateinit var tmpDir: String
    lateinit var configuration: RealmConfiguration

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration(path = "$tmpDir/default.realm", schema = setOf(Sample::class))
    }

    @AfterTest
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun resultsListener() = RunLoopThread().run {
        val c = Channel<List<Sample>>(1)

        val realm = Realm.open(configuration)

        val results = realm.objects(Sample::class)

        assertEquals(0, results.size)

        val token = results.observe {
            assertTrue(it is RealmResults<Sample>)
            assertEquals(results, it)
            val updatedResults = results.toList()
            this@run.launch { c.send(updatedResults) }
        }

        launch {
            val size = c.receive().size
            val sample = realm.writeBlocking {
                assertEquals(0, size)
                create(Sample::class).apply { stringField = INITIAL }
            }

            val result = c.receive()
            assertEquals(1, result.size)
            assertEquals(sample.stringField, result[0].stringField)

            token.cancel()

            realm.writeBlocking {
                create(Sample::class).apply { stringField = INITIAL }
            }

            delay(1.seconds)
            assertTrue(c.isEmpty)

            realm.close()
            terminate()
        }
    }
}
