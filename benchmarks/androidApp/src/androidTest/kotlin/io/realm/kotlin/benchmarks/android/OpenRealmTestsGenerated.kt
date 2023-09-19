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
import io.realm.generated.openCloseRealmClassesMap
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.types.RealmObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass

@RunWith(Parameterized::class)
class OpenRealmTestsGenerated(
    private val className: String,
    private val schemaSize: Int,
) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}-schema-size-{1}")
        fun initParameters(): Collection<Array<*>> {
            val schemaSizes = listOf(1, 10, 100)

            return openCloseRealmClassesMap.keys
                .flatMap {className ->
                    schemaSizes.map { schemaSize -> arrayOf(className, schemaSize) }
                }
                .toList()
        }
    }

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var config: RealmConfiguration
    private var realm: Realm? = null

    @Before
    fun setUp() {
        val schema: Set<KClass<out RealmObject>> =
            openCloseRealmClassesMap[className]!!
                .subList(0, schemaSize)
                .toSet()

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
