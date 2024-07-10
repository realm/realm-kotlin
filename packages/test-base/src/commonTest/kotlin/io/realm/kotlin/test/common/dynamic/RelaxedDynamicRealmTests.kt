/*
 * Copyright 2024 Realm Inc.
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

package io.realm.kotlin.test.common.dynamic

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.dynamic.getinterface.RelaxedRealmObject
import io.realm.kotlin.dynamic.getinterface.get
import io.realm.kotlin.dynamic.getinterface.relaxed
import io.realm.kotlin.dynamic.getinterface.set
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.test.common.A
import io.realm.kotlin.test.common.RealmListTests
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.get
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RelaxedDynamicRealmTests {

    lateinit var realm: Realm
    private lateinit var tmpDir: String

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(setOf(Sample::class, A::class))
            .relaxedSchema(true)
            .directory(tmpDir)
            .build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun getRealmAny() = runBlocking {

        var sample = realm.write { copyToRealm(Sample())
        }
        sample = realm.write {
            findLatest(sample)!!.also {
                val relaxed = it.relaxed
//                sample["test"]
                relaxed["test"] = 5
                relaxed["nullableObject"] = RealmAny.create(Sample())
                relaxed.get<RealmList<String>>("stringListField").add("String")
            }
        }
        val relaxed: RelaxedRealmObject = sample.relaxed
        assertEquals(5, relaxed["test"])
        //
        val actualSample: Sample = relaxed.get<Sample>("nullableObject")

//        actualSample["test"]
        val actualRelaxed: RelaxedRealmObject = relaxed.get<RelaxedRealmObject>("nullableObject")
        // Typed fields are available but cannot use index operator unless unwrapped to RelaxedRealmObject again
        assertEquals(5, actualSample.intField)
        assertEquals(5, actualSample.get("sadf"))
        // Typed fields are not available
        //actualRelaxed.intField
        // If type cannot be derived then we need to use get<T> to
//        actualRelaxed["intField"]
        assertEquals("String", relaxed.get<RealmList<String>>("stringListField")[0])

        // With this going from relaxed mode to static mode, will not onlly require that user is chaning the get("name") to static field accessors
        // but will also have to address the indirection through the relaxed-type.
    }
}
