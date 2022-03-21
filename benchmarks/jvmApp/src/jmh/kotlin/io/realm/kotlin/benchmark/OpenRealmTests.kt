package io.realm.kotlin.benchmark

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.kotlin.benchmarks.LARGE_SCHEMA
import io.realm.kotlin.benchmarks.SINGLE_SCHEMA
import io.realm.kotlin.benchmarks.SMALL_SCHEMA
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

    enum class SchemaSize {
        SINGLE, SMALL, LARGE
    }

    @Param("SINGLE", "SMALL", "LARGE")
    var schemaSize: String = SchemaSize.SINGLE.name
    var realm: Realm? = null
    lateinit var config: RealmConfiguration

    @Setup(Level.Invocation)
    fun setUp() {
        val schema = when (schemaSize) {
            SchemaSize.SMALL.name -> SMALL_SCHEMA
            SchemaSize.LARGE.name -> LARGE_SCHEMA
            SchemaSize.SINGLE.name -> SINGLE_SCHEMA
            else -> throw java.lang.IllegalArgumentException("Unknown arg: '$schemaSize'")
        }
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
