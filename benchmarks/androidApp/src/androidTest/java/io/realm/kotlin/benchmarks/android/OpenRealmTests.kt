package io.realm.kotlin.benchmarks.android

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.kotlin.benchmarks.LARGE_SCHEMA
import io.realm.kotlin.benchmarks.SINGLE_SCHEMA
import io.realm.kotlin.benchmarks.SMALL_SCHEMA
import io.realm.kotlin.benchmarks.SchemaSize
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class OpenRealmTests(val schemaSize: SchemaSize) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "schema-{0}")
        fun initParameters(): Collection<Array<Any>> {
            return SchemaSize.values().map {
                arrayOf(it)
            }
        }
    }

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var config: RealmConfiguration
    private var realm: Realm? = null

    @Before
    fun setUp() {
        val schema = when (schemaSize) {
            SchemaSize.SMALL -> SMALL_SCHEMA
            SchemaSize.LARGE -> LARGE_SCHEMA
            SchemaSize.SINGLE -> SINGLE_SCHEMA
            else -> throw java.lang.IllegalArgumentException("Unknown arg: '$schemaSize'")
        }
        config = RealmConfiguration.Builder(schema)
            .directory("./build/benchmark-realms")
            .build()
    }

    @After
    fun tearDown() {
        realm?.let {
            Realm.deleteRealm(config)
        }
    }

    @Test()
    fun openRealm() {
        benchmarkRule.measureRepeated {
            realm = Realm.open(config)
            runWithTimingDisabled {
                realm!!.close()
            }
        }
    }
}
