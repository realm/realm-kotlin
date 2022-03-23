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
