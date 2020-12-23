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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
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

        val token = Realm.addNotificationListener(sample, object : Callback {
            override fun onChange() {
                val stringField = sample.stringField
                this@run.launch {
                    c.send(stringField)
                }
            }
        })

        launch {
            realm.beginTransaction()
            assertEquals(INITIAL, c.receive())
            sample.stringField = FIRST
            realm.commitTransaction()
            assertEquals(FIRST, c.receive())

            token.cancel()
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

        val token = Realm.addNotificationListener(sample, object : Callback {
            override fun onChange() {
                val stringField = sample.stringField
                this@run.launch {
                    c.send(stringField)
                }
            }
        })

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

            terminate()
        }
    }

    @Test
    fun resultsListener() = RunLoopThread().run {
        val c = Channel<List<Sample>>(1)

        val realm = Realm.open(configuration)

        val results = realm.objects(Sample::class)
        val token = results.addListener(object : Callback {
            override fun onChange() {
                val updatedResults = results.toList()
                this@run.launch { c.send(updatedResults) }
            }
        })

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


//    @Test
//    fun resultsListener() = RunLoopThread().run {
//        val c = Channel<List<Sample>>(1)
//
//        val realm = Realm.open(configuration)
//
//        val results = realm.objects(Sample::class)
//        results.addListener(object : Callback {
//            override fun onChange() {
//                val updatedResults = results.toList()
//                println("onChange: $updatedResults")
//                this@run.launch { c.send(updatedResults) }
//            }
//        })
//
//        launch {
//            realm.beginTransaction()
//            assertEquals(0, c.receive().size)
//            val sample = realm.create(Sample::class).apply { stringField = INITIAL }
//            realm.commitTransaction()
//
//            val result = c.receive()
//            assertEquals(1, result.size)
//            assertEquals(sample.stringField, result[0].stringField)
//
//            terminate()
//        }
//    }


//    @OptIn(ExperimentalTime::class)
//    @Test
//    fun dispatcher2() {
//        val c = Channel<String>(1)
//        val context = UIDispatcher
//        val scope = CoroutineScope(context)
//        val job = scope.launch {
//            println("launch")
//            val receive = c.receive()
//            println("launch $receive")
//
//            delay(1.seconds)
//            c.send("Hello")
//            println("launch")
//        }
//        println("loop")
//        scope.launch {
//            c.send("START")
//            val receive = c.receive()
//            println("received $receive")
//            CFRunLoopStop(CFRunLoopGetCurrent())
//        }
//        CFRunLoopRun()
//        println("exit")
//    }

//    @Test
//    fun objectListener() {
//
//        val realm = Realm.open(configuration)
//        realm.beginTransaction()
//        val sample = realm.create(Sample::class).apply { stringField = INITIAL }
//        realm.commitTransaction()
////        val c = Channel<String>()
//        println("TEST")
//
////        val launch: Job = scope.launch {
//        println("TEST1")
//        val token = Realm.addNotificationListener(sample, object : Callback {
//            override fun onChange() {
//                println("TEST ${sample.stringField}")
////                        scope.async {
////                            c.send(sample.stringField)
////                        }
////                        offer(sample)
////                        println("onChange ${sample.stringField}")
////                        when (sample.stringField) {
////                            INITIAL -> {
////                            }
////                            FIRST -> {}// CFRunLoopStop(CFRunLoopGetCurrent());
////                            else ->
////                                fail("Notifications not canceled")
////                        }
//            }
//        })
//        println("TEST2")
////        }
//
////        runBlocking {
////            launch.join()
////            println("TEST2")
////        }
//
////        runBlocking {
//////            val x = callbackFlow<Sample> {
//////                println("listening")
////                val token = Realm.addNotificationListener(sample, object : Callback {
////                    override fun onChange() {
////                        println("TEST ${sample.stringField}")
//////                        scope.async {
//////                            c.send(sample.stringField)
//////                        }
//////                        offer(sample)
//////                        println("onChange ${sample.stringField}")
//////                        when (sample.stringField) {
//////                            INITIAL -> {
//////                            }
//////                            FIRST -> {}// CFRunLoopStop(CFRunLoopGetCurrent());
//////                            else ->
//////                                fail("Notifications not canceled")
//////                        }
////                    }
////                })
//////                awaitClose {
//////                    token.cancel()
//////                }
//////            }
//////            async {
//////                async {
//////                    println("Collecting")
//////                    x.collect {
//////                        println("Collected")
//////                        c.send(it.stringField)
//////                    }
//////                }
//////                println("Collecting2")
//////            }
////
////
////
//////            val y = async {
//////                async {
//////                    x.collect {
//////                        println("${it.stringField}")
//////                    }
//////                }
//////                println("Done")
//////            }
//////            y.await()
//////            async {
////            println("Begin")
////            realm.beginTransaction()
////            println("Update")
////            sample.stringField = FIRST
////            println("Commit")
////            realm.commitTransaction()
//////            }
////        scope.async {
//////        runBlocking {
////            val receive = c.receive()
////            println("received $receive")
////        }.await()
////
//////        realm.beginTransaction()
//////        val sample = realm.create(Sample::class).apply { stringField = INITIAL }
//////        realm.commitTransaction()
////
//////        Realm.addNotificationListener(sample, object : Callback {
//////            override fun onChange() {
//////                println("onChange ${sample.stringField}")
//////                when (sample.stringField) {
//////                    INITIAL -> {}
//////                    FIRST -> CFRunLoopStop(CFRunLoopGetCurrent());
//////                    else ->
//////                        fail("Notifications not canceled")
//////                }
//////            }
//////        })
//////
////
//////            println("ebterubg")
//////
//////            CFRunLoopRun()
//////            println("ASDF")
////        }
//    }

//    @Test
//    @OptIn(kotlin.time.ExperimentalTime::class)
//    fun coroutine() {
//        runBlocking {
//            val realm = Realm.open(configuration)
//
//            realm.beginTransaction()
//            val sample = realm.create(Sample::class).apply { stringField = "Hello, World!" }
//            realm.commitTransaction()
//
//            Realm.addNotificationListener(sample, object : Callback {
//                override fun onChange() {
//                    if (sample.stringField == "ASDF") {
//                        CFRunLoopStop(CFRunLoopGetCurrent());
//                    }
//                }
//            })
//
//            realm.beginTransaction()
//            realm.commitTransaction()
//
//            CFRunLoopRun()
//        }
//    }

//    @Test
//    fun objectListener_cancel() {
//        val thread = HandlerThread("test")
//        thread.start()
//        val handler = Handler(thread.looper)
//        val listen = CountDownLatch(1)
//        val update = CountDownLatch(1)
//
//        var token: Disposable? = null
//        handler.post {
//            val configuration = RealmConfiguration.Builder(schema = InstrumentedTests.MySchema()).build()
//            val realm = Realm.open(configuration)
//            realm.beginTransaction()
//            val sample = realm.create(Sample::class).apply { stringField = INITIAL }
//            realm.commitTransaction()
//            token = Realm.addNotificationListener(sample, object : Callback {
//                override fun onChange() {
//                    println("onChange: ${sample.stringField}")
//                    when (sample.stringField) {
//                        INITIAL -> {}
//                        FIRST -> update.countDown()
//                        else ->
//                            fail("Notifications not canceled")
//                    }
//                }
//            })
//            listen.countDown()
//        }
//        listen.await()
//
//        realm.beginTransaction()
//        realm.objects(Sample::class)[0].stringField = FIRST
//        realm.commitTransaction()
//
//        update.await()
//
//        token!!.cancel()
//
//        realm.beginTransaction()
//        realm.objects(Sample::class)[0].stringField = SECOND
//        realm.commitTransaction()
//
//        Thread.sleep(1000)
//    }

//    @Test
//    fun objectListener_cancelByGC() {
//        val thread = HandlerThread("test")
//        thread.start()
//        val handler = Handler(thread.looper)
//        val listen = CountDownLatch(1)
//        val update = CountDownLatch(1)
//
//        var token: Disposable? = null
//        handler.post {
//            val configuration = RealmConfiguration.Builder(schema = InstrumentedTests.MySchema()).build()
//            val realm = Realm.open(configuration)
//            realm.beginTransaction()
//            val sample = realm.create(Sample::class).apply { stringField = INITIAL }
//            realm.commitTransaction()
//            token = Realm.addNotificationListener(sample, object : Callback {
//                override fun onChange() {
//                    println("onChange: ${sample.stringField}")
//                    when (sample.stringField) {
//                        INITIAL -> {}
//                        FIRST -> update.countDown()
//                        else ->
//                            fail("Notifications not canceled")
//                    }
//                }
//            })
//            listen.countDown()
//        }
//        listen.await()
//
//        realm.beginTransaction()
//        realm.objects(Sample::class)[0].stringField = FIRST
//        realm.commitTransaction()
//
//        update.await()
//
//        // Clear reference to token and trigger garbage collection
//        token = null
//        System.gc()
//        // Leave some time for carbage collector to kick in
//        Thread.sleep(2000)
//
//        realm.beginTransaction()
//        realm.objects(Sample::class)[0].stringField = SECOND
//        realm.commitTransaction()
//        CountDownLatch(1).await(5, TimeUnit.SECONDS)
//    }
//
//
//
//    @Test
//    fun notification_results() {
//        val samples = realm.objects(Sample::class)
//        samples.addListener(object : Callback {
//            override fun onChange() {
//                println("onChange")
//                CFRunLoopStop(CFRunLoopGetCurrent());
//            }
//        })
//        realm.beginTransaction()
//        val sample = realm.create(Sample::class).apply { stringField = "Hello, World!" }
//        realm.commitTransaction()
//        CFRunLoopRun()
//    }
//
//    @Test
//    fun resultsListener() {
//        val thread = HandlerThread("test")
//        thread.start()
//        val handler = Handler(thread.looper)
//        val countDownLatch = CountDownLatch(1)
//
//        handler.post {
//            val configuration = RealmConfiguration.Builder(schema = InstrumentedTests.MySchema()).build()
//            realm = Realm.open(configuration)
//
//            val samples = realm.objects(Sample::class)
//            samples.addListener(object : Callback {
//                override fun onChange() {
//                    println("onChange")
//                    countDownLatch.countDown()
//                }
//            })
//            realm.beginTransaction()
//            val sample = realm.create(Sample::class).apply { stringField = INITIAL }
//            realm.commitTransaction()
//        }
//
//        countDownLatch.await()
//    }

}
