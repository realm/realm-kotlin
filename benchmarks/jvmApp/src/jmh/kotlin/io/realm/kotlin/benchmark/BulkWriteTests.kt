package io.realm.kotlin.benchmark

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmObject
import io.realm.kotlin.benchmarks.Entity1
import io.realm.kotlin.benchmarks.WithPrimaryKey
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Warmup
import java.util.concurrent.TimeUnit

/**
 * Test speed and scalability of bulk inserting items.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(1)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 100, time = 200, timeUnit = TimeUnit.MILLISECONDS)
open class BulkWriteTests {

    @Param("10", "100", "1000", "10000")
    var size: Int = 0

    @Param("true", "false")
    var usePrimaryKey = false

    var realm: Realm? = null
    lateinit var config: RealmConfiguration
    var data: List<out RealmObject> = listOf()

    @Setup(Level.Iteration)
    fun setUp() {
        config = RealmConfiguration.Builder(schema = setOf(WithPrimaryKey::class, Entity1::class))
            .directory("./build/benchmark-realms")
            .build()
        realm = Realm.open(config)
        val input = ArrayList<RealmObject>(size)
        for (i in 0 until size) {
            val obj: RealmObject = when (usePrimaryKey) {
                true -> WithPrimaryKey().apply { stringField = i.toString() }
                false -> Entity1().apply { stringField = i.toString() }
            }
            input.add(obj)
        }
        data = input
    }

    @TearDown(Level.Iteration)
    fun tearDown() {
        realm?.let {
            it.close()
            Realm.deleteRealm(config)
        }
    }

    @Benchmark()
    fun writeData() {
        realm!!.writeBlocking {
            data.forEach {
                copyToRealm(it)
            }
        }
    }
}
