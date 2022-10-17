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
package io.realm.kotlin.benchmark

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.benchmarks.Entity1
import io.realm.kotlin.ext.realmListOf
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

// About Warmup/ Measurement values: https://stackoverflow.com/a/40081542/1389357
// About Forks: https://stackoverflow.com/a/35147232/1389357
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(1)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 200, time = 200, timeUnit = TimeUnit.MILLISECONDS)
open class AccessorTests {

    private lateinit var config: RealmConfiguration
    private lateinit var realm: Realm
    private lateinit var unmanagedObject: Entity1
    private lateinit var managedObject: Entity1

    @Setup(Level.Trial)
    fun before() {
        config = RealmConfiguration.Builder(schema = setOf(Entity1::class))
            .directory("./build")
            .build()
        realm = Realm.open(config)
        managedObject = realm.writeBlocking {
            unmanagedObject = Entity1().apply {
                stringField = "Medium long string"
                booleanField = true
                longField = 42L
                doubleField = 1.234
                objectField = Entity1()
                objectListField = realmListOf(Entity1(), Entity1(), Entity1())
            }
            copyToRealm(unmanagedObject)
        }
    }

    @TearDown(Level.Trial)
    fun after() {
        realm.close()
        Realm.deleteRealm(config)
    }

//    @Benchmark
//    fun managedReadString(blackhole: Blackhole) {
//        blackhole.consume(managedObject.stringField)
//    }
//
//    @Benchmark
//    fun managedReadLong(blackhole: Blackhole) {
//        blackhole.consume(managedObject.longField)
//    }
//
//    @Benchmark
//    fun managedReadDouble(blackhole: Blackhole) {
//        blackhole.consume(managedObject.doubleField)
//    }
//
//    @Benchmark
//    fun managedReadBoolean(blackhole: Blackhole) {
//        blackhole.consume(managedObject.booleanField)
//    }

    @Benchmark
    fun managedReadObject(blackhole: Blackhole) {
        blackhole.consume(managedObject.objectField)
    }

//    @Benchmark
//    fun managedReadList(blackhole: Blackhole) {
//        blackhole.consume(managedObject.objectListField as Any)
//    }
//
//    @Benchmark
//    fun unmanagedReadString(blackhole: Blackhole) {
//        blackhole.consume(unmanagedObject.stringField)
//    }
//
//    @Benchmark
//    fun unmanagedReadLong(blackhole: Blackhole) {
//        blackhole.consume(unmanagedObject.longField)
//    }
//
//    @Benchmark
//    fun unmanagedReadDouble(blackhole: Blackhole) {
//        blackhole.consume(unmanagedObject.doubleField)
//    }
//
//    @Benchmark
//    fun unmanagedReadBoolean(blackhole: Blackhole) {
//        blackhole.consume(unmanagedObject.booleanField)
//    }
//
//    @Benchmark
//    fun unmanagedReadObject(blackhole: Blackhole) {
//        blackhole.consume(unmanagedObject.objectField)
//    }
//
//    @Benchmark
//    fun unmanagedReadList(blackhole: Blackhole) {
//        blackhole.consume(unmanagedObject.objectListField as Any)
//    }
}
