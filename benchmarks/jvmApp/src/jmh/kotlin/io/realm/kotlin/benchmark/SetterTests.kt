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
import java.util.concurrent.TimeUnit
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
    fun managedSetString1000(blackhole: Blackhole) {
        realm.writeBlocking {
            val obj = findLatest(managedObject)!!
            repeat(1000) {
                obj.stringField = "A"
            }
            blackhole.consume(obj)
        }
    }

    @Benchmark
    fun managedSetString2000(blackhole: Blackhole) {
        realm.writeBlocking {
            val obj = findLatest(managedObject)!!
            repeat(2000) {
                obj.stringField = "A"
            }
            blackhole.consume(obj)
        }
    }

    @Benchmark
    fun managedSetString3000(blackhole: Blackhole) {
        realm.writeBlocking {
            val obj = findLatest(managedObject)!!
            repeat(3000) {
                obj.stringField = "A"
            }
            blackhole.consume(obj)
        }
    }

    @Benchmark
    fun managedSetString4000(blackhole: Blackhole) {
        realm.writeBlocking {
            val obj = findLatest(managedObject)!!
            repeat(4000) {
                obj.stringField = "A"
            }
            blackhole.consume(obj)
        }
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
    fun managedSetDouble1000(blackhole: Blackhole) {
        realm.writeBlocking {
            val obj = findLatest(managedObject)!!
            repeat(1000) {
                obj.doubleField = 42.0
            }
            blackhole.consume(obj)
        }
    }

    @Benchmark
    fun managedSetDouble2000(blackhole: Blackhole) {
        realm.writeBlocking {
            val obj = findLatest(managedObject)!!
            repeat(2000) {
                obj.doubleField = 42.0
            }
            blackhole.consume(obj)
        }
    }

    @Benchmark
    fun managedSetDouble3000(blackhole: Blackhole) {
        realm.writeBlocking {
            val obj = findLatest(managedObject)!!
            repeat(3000) {
                obj.doubleField = 42.0
            }
            blackhole.consume(obj)
        }
    }

    @Benchmark
    fun managedSetDouble4000(blackhole: Blackhole) {
        realm.writeBlocking {
            val obj = findLatest(managedObject)!!
            repeat(4000) {
                obj.doubleField = 42.0
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
    fun managedSetObject1000(blackhole: Blackhole) {
        realm.writeBlocking {
            val obj = findLatest(managedObject)!!
            repeat(1000) {
                obj.objectField = Entity1()
            }
            blackhole.consume(obj)
        }
    }

    @Benchmark
    fun managedSetObject2000(blackhole: Blackhole) {
        realm.writeBlocking {
            val obj = findLatest(managedObject)!!
            repeat(2000) {
                obj.objectField = Entity1()
            }
            blackhole.consume(obj)
        }
    }

    @Benchmark
    fun managedSetObject3000(blackhole: Blackhole) {
        realm.writeBlocking {
            val obj = findLatest(managedObject)!!
            repeat(3000) {
                obj.objectField = Entity1()
            }
            blackhole.consume(obj)
        }
    }

    @Benchmark
    fun managedSetObject4000(blackhole: Blackhole) {
        realm.writeBlocking {
            val obj = findLatest(managedObject)!!
            repeat(4000) {
                obj.objectField = Entity1()
            }
            blackhole.consume(obj)
        }
    }

    @Benchmark
    fun managedSetObject5000(blackhole: Blackhole) {
        realm.writeBlocking {
            val obj = findLatest(managedObject)!!
            repeat(5000) {
                obj.objectField = Entity1()
            }
            blackhole.consume(obj)
        }
    }

    @Benchmark
    fun managedSetList1000(blackhole: Blackhole) {
        realm.writeBlocking {
            val obj = findLatest(managedObject)!!
            repeat(1000) {
                obj.objectListField = realmListOf(Entity1(), Entity1(), Entity1())
            }
            blackhole.consume(obj)
        }
    }

    @Benchmark
    fun managedSetList2000(blackhole: Blackhole) {
        realm.writeBlocking {
            val obj = findLatest(managedObject)!!
            repeat(2000) {
                obj.objectListField = realmListOf(Entity1(), Entity1(), Entity1())
            }
            blackhole.consume(obj)
        }
    }

    @Benchmark
    fun managedSetList3000(blackhole: Blackhole) {
        realm.writeBlocking {
            val obj = findLatest(managedObject)!!
            repeat(3000) {
                obj.objectListField = realmListOf(Entity1(), Entity1(), Entity1())
            }
            blackhole.consume(obj)
        }
    }

    @Benchmark
    fun managedSetList4000(blackhole: Blackhole) {
        realm.writeBlocking {
            val obj = findLatest(managedObject)!!
            repeat(4000) {
                obj.objectListField = realmListOf(Entity1(), Entity1(), Entity1())
            }
            blackhole.consume(obj)
        }
    }

    @Benchmark
    fun managedSetList5000(blackhole: Blackhole) {
        realm.writeBlocking {
            val obj = findLatest(managedObject)!!
            repeat(5000) {
                obj.objectListField = realmListOf(Entity1(), Entity1(), Entity1())
            }
            blackhole.consume(obj)
        }
    }
}
