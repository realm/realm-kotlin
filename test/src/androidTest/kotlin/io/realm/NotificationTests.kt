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

import android.os.Handler
import android.os.HandlerThread
import io.realm.runtimeapi.RealmModule
import org.junit.Before
import org.junit.Test
import test.Sample
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.fail

private val INITIAL = "Hello, World!"
private val FIRST = "FIRST"
private val SECOND = "SECOND"

class NotificationTests {

    @RealmModule(Sample::class)
    class MySchema

    lateinit var realm: Realm

    @Before
    fun setup() {
        val configuration = RealmConfiguration.Builder(schema = MySchema()).build()
        realm = Realm.open(configuration)
        // FIXME Cleaning up realm to overcome lack of support for deleting actual files
        //  https://github.com/realm/realm-kotlin/issues/95
        realm.beginTransaction()
        realm.objects(Sample::class).delete()
        realm.commitTransaction()
        assertEquals(0, realm.objects(Sample::class).size, "Realm is not empty")
    }

    @Test
    fun objectListener() {
        val thread = HandlerThread("test")
        thread.start()
        val handler = Handler(thread.looper)
        val countDownLatch = CountDownLatch(1)

        handler.post {
            val configuration = RealmConfiguration.Builder(schema = InstrumentedTests.MySchema()).build()
            val realm = Realm.open(configuration)
            realm.beginTransaction()
            val sample = realm.create(Sample::class).apply { stringField = INITIAL }
            realm.commitTransaction()
            Realm.addNotificationListener(sample, object : Callback {
                override fun onChange() {
                    println("onChange ${sample.stringField}")
                    when (sample.stringField) {
                        INITIAL -> {}
                        FIRST -> countDownLatch.countDown()
                        else ->
                            fail("Notifications not canceled")
                    }
                    countDownLatch.countDown()
                }
            })
            realm.beginTransaction()
            sample.stringField = FIRST
            realm.commitTransaction()
        }


        countDownLatch.await()
    }

    @Test
    fun objectListener_cancel() {
        val thread = HandlerThread("test")
        thread.start()
        val handler = Handler(thread.looper)
        val listen = CountDownLatch(1)
        val update = CountDownLatch(1)

        var token: Disposable? = null
        handler.post {
            val configuration = RealmConfiguration.Builder(schema = InstrumentedTests.MySchema()).build()
            val realm = Realm.open(configuration)
            realm.beginTransaction()
            val sample = realm.create(Sample::class).apply { stringField = INITIAL }
            realm.commitTransaction()
            token = Realm.addNotificationListener(sample, object : Callback {
                override fun onChange() {
                    println("onChange: ${sample.stringField}")
                    when (sample.stringField) {
                        INITIAL -> {}
                        FIRST -> update.countDown()
                        else ->
                            fail("Notifications not canceled")
                    }
                }
            })
            listen.countDown()
        }
        listen.await()

        realm.beginTransaction()
        realm.objects(Sample::class)[0].stringField = FIRST
        realm.commitTransaction()

        update.await()

        token!!.cancel()

        realm.beginTransaction()
        realm.objects(Sample::class)[0].stringField = SECOND
        realm.commitTransaction()

        Thread.sleep(1000)
    }

    @Test
    fun objectListener_cancelByGC() {
        val thread = HandlerThread("test")
        thread.start()
        val handler = Handler(thread.looper)
        val listen = CountDownLatch(1)
        val update = CountDownLatch(1)

        var token: Disposable? = null
        handler.post {
            val configuration = RealmConfiguration.Builder(schema = InstrumentedTests.MySchema()).build()
            val realm = Realm.open(configuration)
            realm.beginTransaction()
            val sample = realm.create(Sample::class).apply { stringField = INITIAL }
            realm.commitTransaction()
            token = Realm.addNotificationListener(sample, object : Callback {
                override fun onChange() {
                    println("onChange: ${sample.stringField}")
                    when (sample.stringField) {
                        INITIAL -> {}
                        FIRST -> update.countDown()
                        else ->
                            fail("Notifications not canceled")
                    }
                }
            })
            listen.countDown()
        }
        listen.await()

        realm.beginTransaction()
        realm.objects(Sample::class)[0].stringField = FIRST
        realm.commitTransaction()

        update.await()

        // Clear reference to token and trigger garbage collection
        token = null
        System.gc()
        // Leave some time for carbage collector to kick in
        Thread.sleep(2000)

        realm.beginTransaction()
        realm.objects(Sample::class)[0].stringField = SECOND
        realm.commitTransaction()
        CountDownLatch(1).await(5, TimeUnit.SECONDS)
    }

    @Test
    fun resultsListener() {
        val thread = HandlerThread("test")
        thread.start()
        val handler = Handler(thread.looper)
        val countDownLatch = CountDownLatch(1)

        handler.post {
            val configuration = RealmConfiguration.Builder(schema = InstrumentedTests.MySchema()).build()
            realm = Realm.open(configuration)

            val samples = realm.objects(Sample::class)
            samples.addListener(object : Callback {
                override fun onChange() {
                    println("onChange")
                    countDownLatch.countDown()
                }
            })
            realm.beginTransaction()
            val sample = realm.create(Sample::class).apply { stringField = INITIAL }
            realm.commitTransaction()
        }

        countDownLatch.await()
    }
}
