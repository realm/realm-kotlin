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

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmObject
import io.realm.kotlin.benchmarks.SCHEMAS
import io.realm.kotlin.benchmarks.SchemaSize
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Warmup
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * Benchmarking how fast it is to open a Realm.
 * Since this should never happen in hot path, we attempt to benchmark the cold start case.
 *
 * TODO Need to verify if `SingleShotTime` also include setup and teardown: http://mail.openjdk.java.net/pipermail/jmh-dev/2014-September/001364.html
 */
@Fork(1)
@Warmup(iterations = 0)
@Measurement(iterations = 100)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
open class OpenRealmTests {

    @Param("SINGLE", "SMALL", "LARGE") // Must match enum names
    var schemaSize: String = SchemaSize.SINGLE.name
    var realm: Realm? = null
    lateinit var config: RealmConfiguration

    @Setup(Level.Invocation)
    fun setUp() {
        val schema: Set<KClass<out RealmObject>> = SCHEMAS[schemaSize]!!.schemaObjects
        config = RealmConfiguration.Builder(schema)
            .directory("./build/benchmark-realms")
            .build()
    }

    @TearDown(Level.Invocation)
    fun tearDown() {
        realm?.let {
            it.close()
            Realm.deleteRealm(config)
        }
    }

    @Benchmark()
    fun openRealm() {
        realm = Realm.open(config)
    }
}
