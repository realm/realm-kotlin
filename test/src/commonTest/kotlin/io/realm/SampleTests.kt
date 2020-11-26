/*
 * Copyright 2020 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm

import io.realm.runtimeapi.RealmCompanion
import io.realm.runtimeapi.RealmModelInternal
import test.Sample
import kotlin.test.Test
import kotlin.test.assertEquals

class SampleTests {

    @Test
    fun testSyntheticSchemaMethodIsGenerated() {
        val expected = "{\"name\": \"Sample\", \"properties\": [{\"name\": {\"type\": \"string\", \"nullable\": \"false\"}}]}"
        assertEquals(expected, Sample.`$realm$schema`())
        @Suppress("CAST_NEVER_SUCCEEDS")
        val actual: RealmCompanion = Sample.Companion as RealmCompanion
        assertEquals(expected, actual.`$realm$schema`())
    }

    @Test
    fun testRealmModelInternalAndMarkerAreImplemented() {
        val p = Sample()
        @Suppress("CAST_NEVER_SUCCEEDS")
        p as? RealmModelInternal
            ?: error("Supertype RealmModelInternal was not added to Sample class")
    }

    @Test
    fun realmConfig() {
        @Suppress("CAST_NEVER_SUCCEEDS")
        val configuration = RealmConfiguration.Builder()
            // Should be removed once we have module generation in place
            .factory { kClass ->
                when (kClass) {
                    Sample::class -> Sample()
                    else -> TODO()
                }
            }
            // Should be removed once we have module generation in place
            .classes(
                listOf(
                    Sample.Companion as RealmCompanion
                )
            )
            .build()
        val realm = Realm.open(configuration)
        realm.beginTransaction()
        val sample = realm.create(Sample::class)
        kotlin.test.assertEquals("", sample.name)
        sample.name = "Hello, World!"
        kotlin.test.assertEquals("Hello, World!", sample.name)
        realm.commitTransaction()
    }
}
