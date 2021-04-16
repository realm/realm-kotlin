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

package io.realm

import io.realm.util.RuntimeUtils
import platform.CoreFoundation.CFRunLoopGetCurrent
import platform.CoreFoundation.CFRunLoopRun
import platform.CoreFoundation.CFRunLoopStop
import platform.Foundation.NSNumber
import platform.darwin.DISPATCH_QUEUE_PRIORITY_BACKGROUND
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.posix.sleep
import test.Sample
import kotlin.native.concurrent.AtomicInt
import kotlin.native.concurrent.freeze
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class BackgroundNotificationTests {

    @RealmModule(Sample::class)
    class MySchema

    lateinit var tmpDir: String
    lateinit var configuration: RealmConfiguration

    @BeforeTest
    fun setup() {
        tmpDir = Utils.createTempDir()
        configuration =
            RealmConfiguration.Builder(schema = MySchema(), path = "$tmpDir/default.realm").build().freeze()
    }

    @AfterTest
    fun tearDown() {
        Utils.deleteTempDir(tmpDir)
    }

    @Test
    fun notificationOnBackgroundWrite() {
        RuntimeUtils.printlntid("foreground")

        val x = AtomicInt(0)

        val realm = Realm.open(configuration)

        realm.beginTransaction()
        val sample = realm.create(Sample::class).apply { stringField = "asdf" }
        realm.commitTransaction()

        sample.observe {
            RuntimeUtils.printlntid("changed")
            x.increment()
            if (x.compareAndSet(2, 3)) {
                CFRunLoopStop(CFRunLoopGetCurrent())
            }
        }

        val queue = dispatch_get_global_queue(NSNumber(DISPATCH_QUEUE_PRIORITY_BACKGROUND).integerValue, 0)
        dispatch_async(queue,  {
            RuntimeUtils.printlntid("background")
            val realmBg = Realm.open(configuration)
            val sampleBg = realmBg.objects(Sample::class)[0]
            realmBg.beginTransaction()
            sampleBg.stringField = "123"
            realmBg.commitTransaction()
        }.freeze())

        RuntimeUtils.printlntid("awaiting notification")
        CFRunLoopRun()
        assertTrue(x.value == 3)
    }

    @Test
    fun backgroundNotificationOnWrite() {
        RuntimeUtils.printlntid("foreground")

        val x = AtomicInt(0)

        val queue = dispatch_get_global_queue(NSNumber(DISPATCH_QUEUE_PRIORITY_BACKGROUND).integerValue, 0)
        dispatch_async(queue,  {
            RuntimeUtils.printlntid("background")
            val bgRealm = Realm.open(configuration)

            bgRealm.beginTransaction()
            val sample = bgRealm.create<Sample>()
            bgRealm.commitTransaction()

            RuntimeUtils.printlntid("observing")
            val observe = sample.observe {
                RuntimeUtils.printlntid("change: ${x.value}")
                x.compareAndSet(2, 3)
            }
            x.increment()
        }.freeze())

        while(x.value != 1) {
            sleep(1)
        }

        RuntimeUtils.printlntid("updating")

        val realm = Realm.open(configuration)
        val sample = realm.objects(Sample::class)[0]
        realm.beginTransaction()
        sample.stringField = "REALM"
        x.increment()
        realm.commitTransaction()

        RuntimeUtils.printlntid("awaiting notification")
        while(x.value != 3) {
            sleep(1)
        }
    }

}
