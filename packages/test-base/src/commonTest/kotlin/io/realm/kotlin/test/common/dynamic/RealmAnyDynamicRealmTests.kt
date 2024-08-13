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
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.dynamic.getrealmany.get
import io.realm.kotlin.dynamic.getrealmany.set
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.schema.RealmSchema
import io.realm.kotlin.test.common.A
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RealmAnyDynamicRealmTests {

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
                it["nullableObject"] = RealmAny.create(it)
                val asList: RealmList<RealmAny?> = it["stringListField"].asList()
                asList.add(RealmAny.create("String"))
            }
        }
        val actual: Int = sample["test"].asInt()
        assertEquals(5, actual)

        val actualObjectRealmAny: RealmAny = sample["nullableObject"]
        val actualObject = actualObjectRealmAny.asRealmObject<Sample>() // Typed and/or dynamic/related


        val asList: RealmList<RealmAny?> = sample["stringListField"].asList()
        val actual1: String = asList[0]!!.asString()
        assertEquals("String", asList[0]!!.asString())
        assertEquals("String", actual1)


    }
}


class A : RealmObject {
    var properties: Map<String, RealmAny>

    var name: String

    val realm: Realm


    val extraProperties: List<String> // They will always be RealmAny
    val schema: RealmSchema = realm.schema() // properties will be typed potentially RealmAny if the schema says so
}
