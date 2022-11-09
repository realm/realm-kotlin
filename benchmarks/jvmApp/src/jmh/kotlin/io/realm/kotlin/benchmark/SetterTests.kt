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
@Measurement(iterations = 100, time = 200, timeUnit = TimeUnit.MILLISECONDS)
open class SetterTests {

    private lateinit var config: RealmConfiguration
    private lateinit var realm: Realm
    private lateinit var unmanagedObject: Entity1
    private lateinit var managedObject: Entity1

    /**
     * The idea of testing a setter on a managed object is somewhat difficult to achieve in
     * isolation. Modifying a managed object's property can only be done inside the scope of a write
     * transaction and only after having obtained an updated version of said object. Creating a
     * write transaction and calling 'findLatest' to obtain an updated object adds quite a lot of
     * overhead to the benchmark so we need a way to properly measure setters in isolation.
     *
     * Until then, we can try to increase the relative weight of the call to the setter so that it
     * is heavier than the overhead caused by creating a write transaction and fetching an updated
     * object. This can be achieved by calling a setter a number of times inside a loop until the
     * throughput values start to decrease with the increase of iterations.
     *
     * We are testing a number of data types as stated above. Also, for better isolation in the
     * RealmObject setter benchmark, we use a managed object created inside the transaction.
     *
     * For lists we only test calls to 'list.set' instead of testing the setter itself.
     */

    @Setup(Level.Iteration)
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

    @TearDown(Level.Iteration)
    fun after() {
        realm.close()
        Realm.deleteRealm(config)
    }

    @Benchmark
    fun managedSetString5000(blackhole: Blackhole) {
        realm.writeBlocking {
            val obj = findLatest(managedObject)!!
            repeat(5000) {
                obj.stringField = "A"
            }
            blackhole.consume(obj)
        }
    }

    @Benchmark
    fun managedSetDouble5000(blackhole: Blackhole) {
        realm.writeBlocking {
            val obj = findLatest(managedObject)!!
            repeat(5000) {
                obj.doubleField = 42.0
            }
            blackhole.consume(obj)
        }
    }

    @Benchmark
    fun managedSetObject5000(blackhole: Blackhole) {
        realm.writeBlocking {
            val newObj = copyToRealm(Entity1())
            val obj = findLatest(managedObject)!!
            repeat(5000) {
                obj.objectField = newObj
            }
            blackhole.consume(obj)
        }
    }

    @Benchmark
    fun managedSetList5000(blackhole: Blackhole) {
        realm.writeBlocking {
            val listObj = copyToRealm(Entity1())
            val obj = findLatest(managedObject)!!
            repeat(5000) {
                obj.objectListField.set(0, listObj)
            }
            blackhole.consume(obj)
        }
    }

    @Benchmark
    fun managedSetLong5000(blackhole: Blackhole) {
        realm.writeBlocking {
            val obj = findLatest(managedObject)!!
            repeat(5000) {
                obj.longField = 42L
            }
            blackhole.consume(obj)
        }
    }

    @Benchmark
    fun managedSetBoolean5000(blackhole: Blackhole) {
        realm.writeBlocking {
            val obj = findLatest(managedObject)!!
            repeat(5000) {
                obj.booleanField = true
            }
            blackhole.consume(obj)
        }
    }
}
