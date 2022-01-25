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

package io.realm.test.shared

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmObject
import io.realm.query
import io.realm.test.platform.PlatformUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.newSingleThreadContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Class for source code snippets that are part of our README.
 *
 * NOTE: If changing tests in this file, you would also have to update the corresponding snippets
 * in the README.
 */
class ReadMeTests {

    lateinit var tmpDir: String
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
                RealmConfiguration.Builder(schema = setOf(Person::class, Dog::class))
                        .path("$tmpDir/default.realm")
                        .build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        realm.close()
        PlatformUtils.deleteTempDir(tmpDir)
    }

    class Dog : RealmObject { var name: String = "NN"; var age: Int = 0 }
    class Person : RealmObject { var name: String = ""; var dog: Dog? = null }

    @Test
    fun query() {
        // Setup
        val context = newSingleThreadContext("test-dispatcher")

        // Query examples from readme
        val all = realm.query<Person>().find()

        // Person named 'Carlo'
        val personsByNameQuery = realm.query<Person>("name = $0", "Carlo")
        val filteredByName = personsByNameQuery.find()

        // Person having a dog aged more than 7 with a name starting with 'Fi'
        val filteredByDog = realm.query<Person>("dog.age > $0 AND dog.name BEGINSWITH $1", 7, "Fi").find()

        // Observing for changes with Kotlin Coroutine Flows
        CoroutineScope(context).async {
            personsByNameQuery.asFlow().collect { result ->
                println("Realm updated: Number of persons is ${result.size}")
            }
        }
    }
}
