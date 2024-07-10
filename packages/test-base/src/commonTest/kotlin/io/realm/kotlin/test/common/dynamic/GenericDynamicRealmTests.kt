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
import io.realm.kotlin.annotations.DynamicAPI
import io.realm.kotlin.annotations.ExperimentalGeoSpatialApi
import io.realm.kotlin.dynamic.getgeneric.get
import io.realm.kotlin.dynamic.getgeneric.set
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(DynamicAPI::class)
class GenericDynamicRealmTests {

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
                it["test"] = 5
//                it.get<RealmList<String>>("stringListField").add("Realm")
//                it.get<String>("stringListField")
                // This will still allow you to do generic runtime paths by asking for an RealmAny
                val x : RealmAny = it.get<RealmAny>("stringListField")
                when(x.type) {

                }
            }
        }
        @OptIn(ExperimentalGeoSpatialApi::class)
        val actual2: Int = sample["test"]
        val string  = sample.stringField
//        val string  = sample.get<Int>("stringField")
//        val string  = sample.get<String>("stringField")
//        val string  = sample.get<String?>("stringField")
//        val string  = sample.get<RealmAny>("stringField")
        assertEquals(5, actual2)

        val actual = sample.get<RealmList<String>>("stringListField")[0]
        assertEquals("Realm", actual)

        // Gives some flexibility as you can also ask for a RealmAny
        val actual1 = sample.get<RealmAny>("stringListField")
        assertEquals("Realm", actual1.asList()[0]!!.asString())

        // Gives also the option of using a DynamicRealmObject

//        realm.copyFromRealm(DynamicMutableRealmObject.create("Person", mapOf()))
    }
}
