/*
 * Copyright 2021 Realm Inc.
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

package io.realm.test.shared

import io.realm.QuerySort
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmQuery
import io.realm.RealmResults
import io.realm.entities.Sample
import io.realm.find
import io.realm.internal.platform.singleThreadDispatcher
import io.realm.test.platform.PlatformUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("LargeClass")
class QueryTests {

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .path("$tmpDir/default.realm").build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (!realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun find() {
        realm.query(Sample::class)
            .find { results -> assertEquals(0, results.size) }

        val stringValue = "some string"
        realm.writeBlocking {
            // Queries inside a write transaction are live and can be reused within the closure
            val query = query(Sample::class)

            assertEquals(0, query.find().size)
            copyToRealm(Sample().apply { stringField = stringValue })
            assertEquals(1, query.find().size)
        }

        // No filter
        realm.query(Sample::class)
            .find { results -> assertEquals(1, results.size) }

        // Filter by string
        realm.query(Sample::class, "stringField = $0", stringValue)
            .find { results -> assertEquals(1, results.size) }

        // Filter by string that doesn't match
        realm.query(Sample::class, "stringField = $0", "invalid string")
            .find { results -> assertEquals(0, results.size) }
    }

    @Test
    fun find_malformedQueryThrows() {
        assertFailsWith<IllegalArgumentException> {
            realm.query(Sample::class, "stringField = $0")
        }.let {
            assertTrue(it.message!!.contains("Have you specified all parameters"))
        }

        assertFailsWith<IllegalArgumentException> {
            realm.query(Sample::class, "stringField = 42")
        }.let {
            assertTrue(it.message!!.contains("Wrong query field"))
        }

        assertFailsWith<IllegalArgumentException> {
            realm.query(Sample::class, "nonExistingField = 13")
        }.let {
            assertTrue(it.message!!.contains("Wrong query field"))
        }
    }

    @Test
    @Ignore
    fun asFlow() {
        // TODO
    }

    @Test
    @Ignore
    fun asFlow_throwsInsideWrite() {
        // TODO
    }

    @Test
    fun composedQuery() {
        val value1 = 1
        val value2 = 2
        val value3 = 3
        val value4 = 4

        realm.writeBlocking {
            copyToRealm(Sample().apply { intField = value1 })
            copyToRealm(Sample().apply { intField = value2 })
            copyToRealm(Sample().apply { intField = value3 })
            copyToRealm(Sample().apply { intField = value4 })
        }

        realm.query(Sample::class, "intField > 1")
            .query("intField > 2")
            .find()
            .let { results ->
                assertEquals(2, results.size)
            }

        realm.query(Sample::class, "intField > 1")
            .query("intField > 2")
            .sort(Sample::intField.name, QuerySort.DESCENDING)
            .find()
            .let { results ->
                assertEquals(2, results.size)
                val i0 = results[0].intField
                val i1 = results[1].intField
                assertEquals(value4, i0)
                assertEquals(value3, i1)
            }

        realm.query(Sample::class, "intField > 1")
            .query("intField > 2")
            .sort(Sample::intField.name, QuerySort.DESCENDING)
            .query("intField < 4")
            .find()
            .let { results ->
                assertEquals(1, results.size)
                assertEquals(value3, results[0].intField)
            }
    }

    @Test
    fun sort_emptyResults() {
        realm.query(Sample::class)
            .sort(Sample::intField.name)
            .find { results ->
                assertTrue(results.isEmpty())
            }
    }

    @Test
    fun sort_singleField() {
        val john = 6 to "John"
        val mary = 10 to "Mary"
        val ruth = 13 to "Ruth"
        val values = listOf(john, mary, ruth)

        realm.writeBlocking {
            values.forEach { (intValue, stringValue) ->
                copyToRealm(
                    Sample().apply {
                        intField = intValue
                        stringField = stringValue
                    }
                )
            }
        }

        // No filter, default ascending sorting
        realm.query(Sample::class)
            .sort(Sample::intField.name)
            .find { results ->
                assertEquals(3, results.size)
                assertEquals(john.first, results[0].intField)
                assertEquals(john.second, results[0].stringField)
                assertEquals(mary.first, results[1].intField)
                assertEquals(mary.second, results[1].stringField)
                assertEquals(ruth.first, results[2].intField)
                assertEquals(ruth.second, results[2].stringField)
            }

        // No filter, sort descending
        realm.query(Sample::class)
            .sort(Sample::intField.name to QuerySort.DESCENDING)
            .find { results ->
                assertEquals(3, results.size)
                assertEquals(ruth.first, results[0].intField)
                assertEquals(ruth.second, results[0].stringField)
                assertEquals(mary.first, results[1].intField)
                assertEquals(mary.second, results[1].stringField)
                assertEquals(john.first, results[2].intField)
                assertEquals(john.second, results[2].stringField)
            }
    }

    @Test
    fun sort_multipleFields() {
        val john = 6 to "John"
        val mary = 10 to "Mary"
        val ruth = 13 to "Ruth"
        val values = listOf(john, mary, ruth)

        realm.writeBlocking {
            values.forEach { (intValue, stringValue) ->
                copyToRealm(
                    Sample().apply {
                        intField = intValue
                        stringField = stringValue
                    }
                )
            }
        }

        // No filter, multiple sortings
        realm.query(Sample::class)
            .sort(
                Sample::stringField.name to QuerySort.DESCENDING,
                Sample::intField.name to QuerySort.ASCENDING
            ).find { results ->
                assertEquals(3, results.size)
                assertEquals(ruth.first, results[0].intField)
                assertEquals(ruth.second, results[0].stringField)
                assertEquals(mary.first, results[1].intField)
                assertEquals(mary.second, results[1].stringField)
                assertEquals(john.first, results[2].intField)
                assertEquals(john.second, results[2].stringField)
            }
    }

    @Test
    fun sort_throwsIfInvalidProperty() {
        assertFailsWith<IllegalArgumentException> {
            realm.query(Sample::class)
                .sort("invalid")
        }
    }

    @Test
    @Ignore
    fun distinct() {
        // TODO
    }

    @Test
    fun limit() {
        val value1 = 1
        val value2 = 2
        val value3 = 3
        realm.writeBlocking {
            copyToRealm(Sample().apply { intField = value1 })
            copyToRealm(Sample().apply { intField = value2 })
            copyToRealm(Sample().apply { intField = value3 })
        }

        realm.query(Sample::class)
            .sort(Sample::intField.name, QuerySort.DESCENDING)
            .limit(2)
            .find { results ->
                assertEquals(2, results.size)
                assertEquals(value3, results[0].intField)
            }

        realm.query(Sample::class)
            .sort(Sample::intField.name, QuerySort.DESCENDING)
            .limit(2)
            .limit(1)
            .find { results ->
                assertEquals(1, results.size)
                assertEquals(value3, results[0].intField)
            }
    }

    @Test
    @Ignore
    fun limit_withSortAndDistinct() {
        // TODO
    }

    @Test
    @Ignore
    fun limit_asSubQuery() {
        // TODO
    }

    @Test
    fun limit_throwsIfInvalidValue() {
        assertFailsWith<IllegalArgumentException> {
            realm.query(Sample::class)
                .limit(-42)
        }
    }

    @Test
    @Ignore
    fun average_generic_emptyColumns() {
        // TODO
    }

    @Test
    @Ignore
    fun average_double_emptyColumns() {
        // TODO
    }

    @Test
    @Ignore
    fun average_generic_find() {
        // TODO
    }

    @Test
    @Ignore
    fun average_generic_find_throwsIfInvalidType() {
        // TODO
    }

    @Test
    @Ignore
    fun average_generic_asFlow() {
        // TODO
    }

    @Test
    @Ignore
    fun average_generic_asFlow_throwsIfInvalidType() {
        // TODO
    }

    @Test
    @Ignore
    fun average_generic_asFlow_throwsIfInvalidProperty() {
        // TODO
    }

    @Test
    @Ignore
    fun average_double_find() {
        // TODO
    }

    @Test
    @Ignore
    fun average_double_find_throwsIfInvalidProperty() {
        // TODO
    }

    @Test
    @Ignore
    fun average_double_asFlow() {
        // TODO
    }

    @Test
    @Ignore
    fun average_double_asFlow_throwsIfInvalidType() {
        // TODO
    }

    @Test
    @Ignore
    fun average_asFlow_throwsInsideWrite() {
        // TODO
    }

    @Test
    @Ignore
    fun count_find() {
        // TODO
    }

    @Test
    @Ignore
    fun count_asFlow() {
        // TODO
    }

    @Test
    @Ignore
    fun count_asFlow_throwsInsideWrite() {
        // TODO
    }

    @Test
    @Ignore
    fun sum_find() {
        // TODO
    }

    @Test
    @Ignore
    fun sum_asFlow() {
        // TODO
    }

    @Test
    @Ignore
    fun sum_generic_asFlow_throwsIfInvalidType() {
        // TODO
    }

    @Test
    @Ignore
    fun sum_generic_asFlow_throwsIfInvalidProperty() {
        // TODO
    }

    @Test
    @Ignore
    fun max_find() {
        // TODO
    }

    @Test
    @Ignore
    fun max_asFlow() {
        // TODO
    }

    @Test
    @Ignore
    fun max_generic_asFlow_throwsIfInvalidType() {
        // TODO
    }

    @Test
    @Ignore
    fun max_generic_asFlow_throwsIfInvalidProperty() {
        // TODO
    }

    @Test
    @Ignore
    fun min_find() {
        // TODO
    }

    @Test
    @Ignore
    fun min_asFlow() {
        // TODO
    }

    @Test
    @Ignore
    fun min_generic_asFlow_throwsIfInvalidType() {
        // TODO
    }

    @Test
    @Ignore
    fun min_generic_asFlow_throwsIfInvalidProperty() {
        // TODO
    }

    @Test
    @Ignore
    fun first_find() {
        // TODO
    }

    @Test
    @Ignore
    fun first_find_empty() {
        // TODO
    }

    @Test
    @Ignore
    fun first_asFlow() {
        // TODO
    }

//    @Test
//    fun playground_multiThreadScenario() {
//        val channel = Channel<Pair<RealmResults<Sample>, Long?>>(1)
//        var query: RealmQuery<Sample>? = null
//        val scope = singleThreadDispatcher("1")
//
//        runBlocking {
//            realm.writeBlocking {
//                copyToRealm(Sample().apply { intField = 666 })
//            }
//
//            // Create a non-evaluated query (everything is lazy)
//            query = realm.query(Sample::class, "intField == $0", 666)
//
//            // Core pointers are evaluated as soon as we get results!
//            assertEquals(1, query!!.find().size)
//            assertEquals(1, query!!.count().find())
//
//            val obs = async(scope) {
//                // Having evaluated it before, we reuse the query object from a different thread
//                val results = query!!.find()
//                val count = query!!.count().find()
//                channel.send(results to count)
//            }
//            val pair = channel.receive()
//            assertEquals(1, pair.first.size)
//            assertEquals(1, pair.second)
//
//            channel.close()
//            obs.cancel()
//        }
//    }
}
