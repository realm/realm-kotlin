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
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.asFlow
import io.realm.delete
import io.realm.notifications.DeletedList
import io.realm.notifications.DeletedObject
import io.realm.notifications.InitialList
import io.realm.notifications.InitialObject
import io.realm.notifications.InitialRealm
import io.realm.notifications.InitialResults
import io.realm.notifications.ListChange
import io.realm.notifications.ObjectChange
import io.realm.notifications.PendingObject
import io.realm.notifications.RealmChange
import io.realm.notifications.ResultsChange
import io.realm.notifications.SingleQueryChange
import io.realm.notifications.UpdatedList
import io.realm.notifications.UpdatedObject
import io.realm.notifications.UpdatedRealm
import io.realm.notifications.UpdatedResults
import io.realm.query
import io.realm.realmListOf
import io.realm.test.platform.PlatformUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.newSingleThreadContext
import kotlin.coroutines.CoroutineContext
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
    private lateinit var context: CoroutineContext
    lateinit var tmpDir: String
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        context = newSingleThreadContext("test-dispatcher")

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

    class Dog : RealmObject {
        var name: String = "NN"
        var age: Int = 0
    }

    class Person : RealmObject {
        var name: String = ""
        var dog: Dog? = null
        var addresses: RealmList<String> = realmListOf()
    }

    @Test
    fun query() {
        // Query examples from readme
        val all = realm.query<Person>().find()

        // Person named 'Carlo'
        val personsByNameQuery = realm.query<Person>("name = $0", "Carlo")
        val filteredByName = personsByNameQuery.find()

        // Person having a dog aged more than 7 with a name starting with 'Fi'
        val filteredByDog =
            realm.query<Person>("dog.age > $0 AND dog.name BEGINSWITH $1", 7, "Fi").find()

        // Observing for changes with Kotlin Coroutine Flows
        CoroutineScope(context).async {
            personsByNameQuery.asFlow().collect { result ->
                println("Realm updated: Number of persons is ${result.list.size}")
            }
        }
    }

    @Test
    fun notifications_realm() {
        // Subscribe for change notifications on a Realm instance
        CoroutineScope(context).async {
            realm.asFlow()
                .collect { realmChange: RealmChange<Realm> ->
                    when (realmChange) {
                        is InitialRealm<*> -> println("Initial Realm")
                        is UpdatedRealm<*> -> println("Realm updated")
                    }
                }
        }
        // out: "Initial Realm"

        // Write data
        realm.writeBlocking {
            copyToRealm(Person())
        }
        // out: "Realm updated"
    }

    @Test
    fun notifications_realmObject() {
        // Person named Carlo
        val person = realm.writeBlocking {
            copyToRealm(Person().apply { name = "Carlo" })
        }

        // Subscribe for change notifications on person
        CoroutineScope(context).async {
            person.asFlow().collect { objectChange: ObjectChange<Person> ->
                when (objectChange) {
                    is InitialObject -> println("Initial object: ${objectChange.obj.name}")
                    is UpdatedObject ->
                        println("Updated object: ${objectChange.obj.name}, changed fields: ${objectChange.changedFields.size}")
                    is DeletedObject -> println("Deleted object")
                }
            }
        }
        // out: "Initial object: Carlo"

        // Change person field `dog`
        realm.writeBlocking {
            findLatest(person)?.dog = Dog()
        }
        // out: "Updated object: Carlo, changed fields: 1"

        // Delete person
        realm.writeBlocking {
            findLatest(person)?.delete()
        }
        // out: "Deleted object"
    }

    @Test
    fun notifications_realmList() {
        // Person named Carlo
        val person = realm.writeBlocking {
            copyToRealm(Person().apply { name = "Carlo" })
        }

        // Subscribe for RealmList change notifications
        CoroutineScope(context).async {
            person.addresses.asFlow()
                .collect { listChange: ListChange<String> ->
                    when (listChange) {
                        is InitialList -> println("Initial list size: ${listChange.list.size}")
                        is UpdatedList -> println("Updated list size: ${listChange.list.size} insertions ${listChange.insertions.size}")
                        is DeletedList -> println("Deleted list")
                    }
                }
        }
        // out: "Initial list size: 0"

        // Add an element to the list
        realm.writeBlocking {
            findLatest(person)?.addresses?.add("123 Fake Street")
        }
        // out: Updated list size: 0 insertions 1"

        // Remove the object that holds the list
        realm.writeBlocking {
            findLatest(person)?.delete()
        }
        // out: "Deleted list"
    }

    @Test
    fun notifications_realmQuery() {
        // Subscribe for change notifications on a query
        CoroutineScope(context).async {
            realm.query<Person>().asFlow()
                .collect { resultsChange: ResultsChange<Person> ->
                    when (resultsChange) {
                        is InitialResults -> println("Initial results size: ${resultsChange.list.size}")
                        is UpdatedResults -> println("Updated results size: ${resultsChange.list.size} insertions ${resultsChange.insertions.size}")
                    }
                }
        }
        // out: "Initial results size: 0"

        // Add an element that matches the query filter
        realm.writeBlocking {
            copyToRealm(Person().apply { name = "Carlo" })
        }
        // out: Updated results size: 0 insertions 1"
    }

    @Test
    fun notifications_realmSingleQuery() {
        // Subscribe for a single object query change notifications
        CoroutineScope(context).async {
            realm.query<Person>("name = $0", "Carlo").first().asFlow()
                .collect { objectChange: SingleQueryChange<Person> ->
                    when (objectChange) {
                        is PendingObject -> println("Pending object")
                        is InitialObject -> println("Initial object: ${objectChange.obj.name}")
                        is UpdatedObject -> println("Updated object: ${objectChange.obj.name}, changed fields: ${objectChange.changedFields.size}")
                        is DeletedObject -> println("Deleted object")
                    }
                }
        }
        // out: "Pending object"

        // Insert an element that matches the query filter
        val person = realm.writeBlocking {
            copyToRealm(Person().apply { name = "Carlo" })
        }
        // out: "Initial object: Carlo"

        // Update one field of the inserted element
        realm.writeBlocking {
            findLatest(person)?.dog = Dog()
        }
        // out: "Updated object: Carlo, changed fields: 1"

        // Delete the element
        realm.writeBlocking {
            findLatest(person)?.delete()
        }
        // out: "Deleted object"
    }
}
