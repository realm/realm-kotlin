/*
 * Copyright 2023 Realm Inc.
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

package io.realm.kotlin.test.common

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.JsonStyleRealmObject
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmAnyDictionaryOf
import io.realm.kotlin.ext.realmAnyListOf
import io.realm.kotlin.ext.realmAnySetOf
import io.realm.kotlin.ext.realmDictionaryOf
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.test.common.utils.assertFailsWithMessage
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.RealmAny
import org.mongodb.kbson.ObjectId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RealmAnyNestedCollectionTests {

    private lateinit var configBuilder: RealmConfiguration.Builder
    private lateinit var configuration: RealmConfiguration
    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configBuilder = RealmConfiguration.Builder(
            setOf(
                JsonStyleRealmObject::class,
                Sample::class,
            )
        ).directory(tmpDir)
        configuration = configBuilder.build()
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
    fun setInRealmAny_copyToRealm() = runBlocking {
        val sample = Sample().apply { stringField = "SAMPLE" }
        realm.write {
            val instance = JsonStyleRealmObject().apply {
                value = RealmAny.create(
                    realmSetOf(
                        RealmAny.create(5),
                        RealmAny.create("Realm"),
                        RealmAny.create(sample),
                    )
                )
            }
            copyToRealm(instance)
        }
        val anyValue: RealmAny = realm.query<JsonStyleRealmObject>().find().single().value!!
        assertEquals(RealmAny.Type.SET, anyValue.type)
        anyValue.asSet().toMutableSet().let { embeddedSet ->
            assertTrue { embeddedSet.remove(RealmAny.create(5)) }
            assertTrue { embeddedSet.remove(RealmAny.create("Realm")) }
            assertEquals("SAMPLE", embeddedSet.single()!!.asRealmObject<Sample>().stringField)
        }
    }

    @Test
    fun setInRealmAny_assignment() = runBlocking {
        val sample = Sample().apply { stringField = "SAMPLE" }
        realm.write {
            val instance = copyToRealm(JsonStyleRealmObject())
            instance.value = RealmAny.create(
                realmSetOf(
                    RealmAny.create(5),
                    RealmAny.create("Realm"),
                    RealmAny.create(sample),
                )
            )
            instance
        }
        val anyValue: RealmAny = realm.query<JsonStyleRealmObject>().find().single().value!!
        assertEquals(RealmAny.Type.SET, anyValue.type)
        anyValue.asSet().toMutableSet().let { embeddedSet ->
            assertTrue { embeddedSet.remove(RealmAny.create(5)) }
            assertTrue { embeddedSet.remove(RealmAny.create("Realm")) }
            assertEquals("SAMPLE", embeddedSet.single()!!.asRealmObject<Sample>().stringField)
        }
    }

    @Test
    fun setInRealmAny_throwsOnNestedCollections_copyToRealm() = runBlocking<Unit> {
        realm.write {
            JsonStyleRealmObject(ObjectId().toString()).apply {
                value =
                    RealmAny.create(realmSetOf(RealmAny.create(realmListOf(RealmAny.create(5)))))
            }.let {
                assertFailsWithMessage<IllegalArgumentException>("Sets cannot contain other collections") {
                    copyToRealm(it)
                }
            }
            JsonStyleRealmObject(ObjectId().toString()).apply {
                value = RealmAny.create(
                    realmSetOf(
                        RealmAny.create(realmDictionaryOf("key" to RealmAny.create(5)))
                    )
                )
            }.let {
                assertFailsWithMessage<IllegalArgumentException>("Sets cannot contain other collections") {
                    copyToRealm(it)
                }
            }
        }
    }

    @Test
    fun setInRealmAny_throwsOnNestedCollections_add() = runBlocking<Unit> {
        realm.write {
            val instance = copyToRealm(
                JsonStyleRealmObject().apply { value = RealmAny.create(realmSetOf()) }
            )
            val set = instance.value!!.asSet()

            val realmAnyList = RealmAny.create(realmListOf())
            assertFailsWithMessage<IllegalArgumentException>("Sets cannot contain other collections") {
                set.add(realmAnyList)
            }

            val realmAnyDictionary = RealmAny.create(realmDictionaryOf())
            assertFailsWithMessage<IllegalArgumentException>("Sets cannot contain other collections") {
                set.add(realmAnyDictionary)
            }
        }
    }

    @Test
    fun listInRealmAny_copyToRealm() = runBlocking {
        val sample = Sample().apply { stringField = "SAMPLE" }
        realm.write<Unit> {
            JsonStyleRealmObject().apply {
                value = RealmAny.create(
                    realmListOf(
                        RealmAny.create(5),
                        RealmAny.create("Realm"),
                        RealmAny.create(sample),
                    )
                )
            }.let {
                copyToRealm(it)
            }
        }
        val instance = realm.query<JsonStyleRealmObject>().find().single()
        val anyValue: RealmAny = instance.value!!
        assertEquals(RealmAny.Type.LIST, anyValue.type)
    }

    @Test
    fun nestedCollectionsInList_copyToRealm() = runBlocking<Unit> {
        val sample = Sample().apply { stringField = "SAMPLE" }
        realm.write {
            JsonStyleRealmObject().apply {
                value = RealmAny.create(
                    realmListOf(
                        // Primitive values
                        RealmAny.create(5),
                        RealmAny.create("Realm"),
                        RealmAny.create(sample),
                        // Embedded list
                        RealmAny.create(
                            realmListOf(
                                RealmAny.create(5),
                                RealmAny.create("Realm"),
                                RealmAny.create(sample),
                            )
                        ),
                        // Embedded set
                        RealmAny.create(
                            realmSetOf(
                                RealmAny.create(5),
                                RealmAny.create("Realm"),
                                RealmAny.create(sample),
                            )
                        ),
                        // Embedded map
                        RealmAny.create(
                            realmDictionaryOf(
                                "keyInt" to RealmAny.create(5),
                                "keyString" to RealmAny.create("Realm"),
                                "keyObject" to RealmAny.create(sample)
                            )
                        ),
                    )
                )
            }.let {
                copyToRealm(it)
            }
        }
        val instance = realm.query<JsonStyleRealmObject>().find().single()
        val anyValue: RealmAny = instance.value!!
        assertEquals(RealmAny.Type.LIST, anyValue.type)

        // Assert structure
        anyValue.asList().let {
            assertEquals(RealmAny.create(5), it[0])
            assertEquals(RealmAny.create("Realm"), it[1])
            assertEquals("SAMPLE", it[2]!!.asRealmObject<Sample>().stringField)
            it[3]!!.asList().let { embeddedList ->
                assertEquals(RealmAny.create(5), embeddedList[0])
                assertEquals(RealmAny.create("Realm"), embeddedList[1])
                assertEquals("SAMPLE", embeddedList[2]!!.asRealmObject<Sample>().stringField)
            }
            it[4]!!.asSet().toMutableSet().let { embeddedSet ->
                assertTrue { embeddedSet.remove(RealmAny.create(5)) }
                assertTrue { embeddedSet.remove(RealmAny.create("Realm")) }
                assertEquals("SAMPLE", embeddedSet.single()!!.asRealmObject<Sample>().stringField)
            }
            it[5]!!.asDictionary().toMutableMap().let { embeddedDict ->
                assertEquals(RealmAny.create(5), embeddedDict["keyInt"])
                assertEquals(RealmAny.create("Realm"), embeddedDict["keyString"])
                assertEquals(
                    "SAMPLE",
                    embeddedDict["keyObject"]!!.asRealmObject<Sample>().stringField
                )
            }
        }
    }

    @Test
    fun nestedCollectionsInList_add() = runBlocking {
        realm.write {
            val sample = copyToRealm(Sample().apply { stringField = "SAMPLE" })
            val instance =
                copyToRealm(JsonStyleRealmObject().apply { value = RealmAny.create(realmListOf()) })
            instance.value!!.asList().run {
                add(RealmAny.create(5))
                add(RealmAny.create("Realm"))
                add(RealmAny.create(sample))
                // Embedded list
                add(
                    RealmAny.create(
                        realmListOf(
                            RealmAny.create(5),
                            RealmAny.create("Realm"),
                            RealmAny.create(sample),
                        )
                    ),
                )
                // Embedded set
                add(
                    RealmAny.create(
                        realmSetOf(
                            RealmAny.create(5),
                            RealmAny.create("Realm"),
                            RealmAny.create(sample),
                        )
                    ),
                )
                // Embedded map
                add(
                    RealmAny.create(
                        realmDictionaryOf(
                            "keyInt" to RealmAny.create(5),
                            "keyString" to RealmAny.create("Realm"),
                            "keyObject" to RealmAny.create(sample)
                        )
                    ),
                )
            }
        }
        val anyList: RealmAny = realm.query<JsonStyleRealmObject>().find().single().value!!
        anyList.asList().let {
            assertEquals(RealmAny.create(5), it[0])
            assertEquals(RealmAny.create("Realm"), it[1])
            assertEquals("SAMPLE", it[2]!!.asRealmObject<Sample>().stringField)
            it[3]!!.asList().let { embeddedList ->
                assertEquals(RealmAny.create(5), embeddedList[0])
                assertEquals(RealmAny.create("Realm"), embeddedList[1])
                assertEquals("SAMPLE", embeddedList[2]!!.asRealmObject<Sample>().stringField)
            }
            it[4]!!.asSet().toMutableSet().let { embeddedSet ->
                assertTrue { embeddedSet.remove(RealmAny.create(5)) }
                assertTrue { embeddedSet.remove(RealmAny.create("Realm")) }
                assertEquals("SAMPLE", embeddedSet.single()!!.asRealmObject<Sample>().stringField)
            }
            it[5]!!.asDictionary().toMutableMap().let { embeddedDict ->
                assertEquals(RealmAny.create(5), embeddedDict["keyInt"])
                assertEquals(RealmAny.create("Realm"), embeddedDict["keyString"])
                assertEquals(
                    "SAMPLE",
                    embeddedDict["keyObject"]!!.asRealmObject<Sample>().stringField
                )
            }
        }
    }

    @Test
    fun nestedCollectionsInList_set() = runBlocking {
        realm.write {
            val sample = copyToRealm(Sample().apply { stringField = "SAMPLE" })
            val instance =
                copyToRealm(
                    JsonStyleRealmObject().apply {
                        value = RealmAny.create(
                            realmListOf(
                                RealmAny.create(1),
                                RealmAny.create(1),
                                RealmAny.create(1),
                                RealmAny.create(1),
                            )
                        )
                    }
                )
            instance.value!!.asList().run {
                // Embedded list
                set(
                    0,
                    RealmAny.create(
                        realmListOf(
                            RealmAny.create(5),
                            RealmAny.create(sample),
                        )
                    ),
                )
                // Embedded set
                set(
                    1,
                    RealmAny.create(
                        realmSetOf(
                            RealmAny.create(5),
                            RealmAny.create("Realm"),
                            RealmAny.create(sample),
                        )
                    ),
                )
                // Embedded map
                set(
                    2,
                    RealmAny.create(
                        realmDictionaryOf(
                            "keyInt" to RealmAny.create(5),
                            "keyString" to RealmAny.create("Realm"),
                            "keyObject" to RealmAny.create(sample)
                        )
                    ),
                )
            }
        }

        val anyValue3: RealmAny = realm.query<JsonStyleRealmObject>().find().single().value!!
        anyValue3.asList().let {
            it[0]!!.asList().let { embeddedList ->
                assertEquals(RealmAny.create(5), embeddedList[0])
                assertEquals("SAMPLE", embeddedList[1]!!.asRealmObject<Sample>().stringField)
            }
            it[1]!!.asSet().toMutableSet().let { embeddedSet ->
                assertTrue { embeddedSet.remove(RealmAny.create(5)) }
                assertTrue { embeddedSet.remove(RealmAny.create("Realm")) }
                assertEquals("SAMPLE", embeddedSet.single()!!.asRealmObject<Sample>().stringField)
            }
            it[2]!!.asDictionary().toMutableMap().let { embeddedDict ->
                assertEquals(RealmAny.create(5), embeddedDict["keyInt"])
                assertEquals(RealmAny.create("Realm"), embeddedDict["keyString"])
                assertEquals(
                    "SAMPLE",
                    embeddedDict["keyObject"]!!.asRealmObject<Sample>().stringField
                )
            }
        }
    }

    @Test
    fun nestedCollectionsInList_set_invalidatesOldElement() = runBlocking<Unit> {
        realm.write {
            val instance = copyToRealm(JsonStyleRealmObject())
            instance.value = realmAnyListOf(realmAnyListOf(5))

            // Store local reference to existing list
            var nestedList = instance.value!!.asList()[0]!!.asList()
            // Accessing returns excepted value 5
            assertEquals(5, nestedList[0]!!.asInt())

            // Overwriting exact list with new list
            instance.value!!.asList()[0] = realmAnyListOf(7)
            assertFailsWithMessage<IllegalStateException>("List is no longer valid") {
                nestedList[0]
            }

            nestedList = instance.value!!.asList()[0]!!.asList()
            assertEquals(7, nestedList[0]!!.asInt())

            // Overwriting root entry
            instance.value = null
            assertFailsWithMessage<IllegalStateException>("List is no longer valid") {
                nestedList[0]
            }

            // Recreating list doesn't bring things back to shape
            instance.value = realmAnyListOf(realmAnyListOf(8))
            assertFailsWithMessage<IllegalStateException>("List is no longer valid") {
                nestedList[0]
            }
        }
    }

    @Test
    fun dictionaryInRealmAny_copyToRealm() = runBlocking {
        val sample = Sample().apply { stringField = "SAMPLE" }
        // Import
        realm.write {
            // Normal realm link/object reference
            JsonStyleRealmObject().apply {
                // Assigning dictionary with nested lists and dictionaries
                value = RealmAny.create(
                    realmDictionaryOf(
                        "keyInt" to RealmAny.create(5),
                        "keySet" to RealmAny.create(
                            realmSetOf(
                                RealmAny.create(5),
                                RealmAny.create("Realm"),
                                RealmAny.create(sample),
                            )
                        ),
                        "keyList" to RealmAny.create(
                            realmListOf(
                                RealmAny.create(5),
                                RealmAny.create("Realm"),
                                RealmAny.create(sample)
                            )
                        ),
                        "keyDictionary" to RealmAny.create(
                            realmDictionaryOf(
                                "keyInt" to RealmAny.create(5),
                                "keyString" to RealmAny.create("Realm"),
                                "keyObject" to RealmAny.create(sample)
                            )
                        ),
                    )
                )
            }.let {
                copyToRealm(it)
            }
        }

        val jsonStyleRealmObject: JsonStyleRealmObject =
            realm.query<JsonStyleRealmObject>().find().single()
        val anyValue: RealmAny = jsonStyleRealmObject.value!!
        assertEquals(RealmAny.Type.DICTIONARY, anyValue.type)
        anyValue.asDictionary().run {
            assertEquals(4, size)
            assertEquals(5, get("keyInt")!!.asInt())
            get("keySet")!!.asSet().toMutableSet().let { embeddedSet ->
                assertEquals(3, embeddedSet.size)
                assertTrue { embeddedSet.remove(RealmAny.create(5)) }
                assertTrue { embeddedSet.remove(RealmAny.create("Realm")) }
                assertEquals(
                    "SAMPLE",
                    embeddedSet.single()!!.asRealmObject<Sample>().stringField
                )
                assertEquals(1, embeddedSet.size)
            }

            get("keyList")!!.asList().let { embeddedList ->
                assertEquals(RealmAny.create(5), embeddedList[0])
                assertEquals(RealmAny.create("Realm"), embeddedList[1])
                assertEquals("SAMPLE", embeddedList[2]!!.asRealmObject<Sample>().stringField)
            }
            get("keyDictionary")!!.asDictionary().let { embeddedDict ->
                assertEquals(RealmAny.create(5), embeddedDict["keyInt"])
                assertEquals(RealmAny.create("Realm"), embeddedDict["keyString"])
                assertEquals(
                    "SAMPLE",
                    embeddedDict["keyObject"]!!.asRealmObject<Sample>().stringField
                )
            }
        }
    }
    @Test
    fun dictionaryInRealmAny_put() = runBlocking {
        val sample = Sample().apply { stringField = "SAMPLE" }
        // Import
        realm.write {
            copyToRealm(
                JsonStyleRealmObject().apply {
                    // Assigning dictionary with nested lists and dictionaries
                    value = RealmAny.create(realmDictionaryOf())
                }
            )
            query<JsonStyleRealmObject>().find().single().value!!.asDictionary().run {
                put("keyInt", RealmAny.create(5))
                put("keySet", realmAnySetOf(5, "Realm", sample))
                put("keyList", realmAnyListOf(5, "Realm", sample))
                put(
                    "keyDictionary",
                    realmAnyDictionaryOf(
                        "keyInt" to 5,
                        "keyString" to "Realm",
                        "keyObject" to sample,
                    ),
                )
            }
        }

        val jsonStyleRealmObject: JsonStyleRealmObject =
            realm.query<JsonStyleRealmObject>().find().single()
        val anyValue: RealmAny = jsonStyleRealmObject.value!!
        assertEquals(RealmAny.Type.DICTIONARY, anyValue.type)
        anyValue.asDictionary().run {
            assertEquals(4, size)
            assertEquals(5, get("keyInt")!!.asInt())
            get("keySet")!!.asSet().toMutableSet().let { embeddedSet ->
                assertEquals(3, embeddedSet.size)
                assertTrue { embeddedSet.remove(RealmAny.create(5)) }
                assertTrue { embeddedSet.remove(RealmAny.create("Realm")) }
                assertEquals(
                    "SAMPLE",
                    embeddedSet.single()!!.asRealmObject<Sample>().stringField
                )
                assertEquals(1, embeddedSet.size)
            }

            get("keyList")!!.asList().let { embeddedList ->
                assertEquals(RealmAny.create(5), embeddedList[0])
                assertEquals(RealmAny.create("Realm"), embeddedList[1])
                assertEquals("SAMPLE", embeddedList[2]!!.asRealmObject<Sample>().stringField)
            }
            get("keyDictionary")!!.asDictionary().let { embeddedDict ->
                assertEquals(RealmAny.create(5), embeddedDict["keyInt"])
                assertEquals(RealmAny.create("Realm"), embeddedDict["keyString"])
                assertEquals(
                    "SAMPLE",
                    embeddedDict["keyObject"]!!.asRealmObject<Sample>().stringField
                )
            }
        }
    }

    @Test
    fun nestedCollectionsInDictionary_put_invalidatesOldElement() = runBlocking<Unit> {
        realm.write {
            val instance = copyToRealm(
                JsonStyleRealmObject().apply {
                    value = RealmAny.create(
                        realmDictionaryOf("key" to RealmAny.create(realmListOf(RealmAny.create(5))))
                    )
                }
            )
            // Store local reference to existing list
            var nestedList = instance.value!!.asDictionary()["key"]!!.asList()
            // Accessing returns excepted value 5
            assertEquals(5, nestedList[0]!!.asInt())
            // Overwriting exact list with new list
            instance.value!!.asDictionary()["key"] = realmAnyListOf(7)

            // Fails due to https://github.com/realm/realm-core/issues/6895
            assertFailsWithMessage<IllegalStateException>("List is no longer valid") {
                nestedList[0]
            }

            // Getting updated reference to embedded list
            nestedList = instance.value!!.asDictionary()["key"]!!.asList()
            assertEquals(7, nestedList[0]!!.asInt())

            // Overwriting root entry
            instance.value = null
            assertFailsWithMessage<IllegalStateException>("List is no longer valid") {
                nestedList[0]
            }
        }
    }

    @Test
    fun updateMixed_invalidatesOldElement() = runBlocking<Unit> {
        realm.write {
            val instance = copyToRealm(JsonStyleRealmObject())
            instance.value = RealmAny.create(realmListOf(RealmAny.create(5)))

            // Store local reference to existing list
            val nestedList = instance.value!!.asList()
            // Accessing returns excepted value 5
            nestedList[0]!!.asInt()

            // Overwriting with new list
            instance.value = realmAnyListOf(7)
            // Accessing original orphaned list return 7 from the new instance, but expected ILLEGAL_STATE_EXCEPTION["List is no longer valid"]
            assertFailsWithMessage<IllegalStateException>("List is no longer valid") {
                nestedList[0]
            }

            // Overwriting with null value
            instance.value = null
            // Throws excepted ILLEGAL_STATE_EXCEPTION["List is no longer valid"]
            assertFailsWithMessage<IllegalStateException>("List is no longer valid") {
                nestedList[0]
            }

            // Updating to a new list
            instance.value = realmAnyListOf(7)
            // Accessing original orphaned list return 7 from the new instance again, but expected ILLEGAL_STATE_EXCEPTION["List is no longer valid"]
            assertFailsWithMessage<IllegalStateException>("List is no longer valid") {
                nestedList[0]
            }
        }
    }

    @Test
    fun query_ThrowsOnNestedCollectionArguments() {
        assertFailsWithMessage<IllegalArgumentException>("Invalid query argument: Cannot pass unmanaged collections as input argument") {
            realm.query<JsonStyleRealmObject>("value == $0", RealmAny.create(realmSetOf()))
        }
        assertFailsWithMessage<IllegalArgumentException>("Invalid query argument: Cannot pass unmanaged collections as input argument") {
            realm.query<JsonStyleRealmObject>("value == $0", RealmAny.create(realmListOf()))
        }
        assertFailsWithMessage<IllegalArgumentException>("Invalid query argument: Cannot pass unmanaged collections as input argument") {
            realm.query<JsonStyleRealmObject>("value == $0", RealmAny.create(realmDictionaryOf()))
        }
    }

    @Test
    fun query() = runBlocking<Unit> {
        realm.write {
            copyToRealm(
                JsonStyleRealmObject().apply {
                    id = "SET"
                    value = realmAnySetOf(1, 2, 3)
                }
            )
            copyToRealm(
                JsonStyleRealmObject().apply {
                    id = "LIST"
                    value = realmAnyListOf(4, 5, 6)
                }
            )
            copyToRealm(
                JsonStyleRealmObject().apply {
                    id = "DICT"
                    value = realmAnyDictionaryOf(
                        "key1" to 7,
                        "key2" to 8,
                        "key3" to 9,
                    )
                }
            )
            copyToRealm(
                JsonStyleRealmObject().apply {
                    id = "EMBEDDED"
                    value = realmAnyListOf(
                        setOf(1, 2, 3),
                        listOf(4, 5, 6),
                        mapOf(
                            "key1" to 7,
                            "key2" to 8,
                            "key3" to listOf(9),
                        )
                    )
                }
            )
        }

        assertEquals(4, realm.query<JsonStyleRealmObject>().find().size)

        // Matching lists
        realm.query<JsonStyleRealmObject>("value[0] == 4").find().single().run {
            assertEquals("LIST", id)
        }
        realm.query<JsonStyleRealmObject>("value[*] == 4").find().single().run {
            assertEquals("LIST", id)
        }

        // Matching dictionaries
        assertEquals(1, realm.query<JsonStyleRealmObject>("value.key1 == 7").find().size)
        assertEquals(1, realm.query<JsonStyleRealmObject>("value['key1'] == 7").find().size)
        assertEquals(0, realm.query<JsonStyleRealmObject>("value.unknown == 3").find().size)
        assertEquals(1, realm.query<JsonStyleRealmObject>("value.@keys == 'key1'").find().size)
        assertEquals(0, realm.query<JsonStyleRealmObject>("value.@keys == 'unknown'").find().size)

        // None
        assertTrue { realm.query<JsonStyleRealmObject>("value[*] == 10").find().isEmpty() }

        // Matching across all elements and in nested structures
        realm.query<JsonStyleRealmObject>("value[*][*] == 4").find().single().run {
            assertEquals("EMBEDDED", id)
        }
        realm.query<JsonStyleRealmObject>("value[*][*] == 7").find().single().run {
            assertEquals("EMBEDDED", id)
        }
        realm.query<JsonStyleRealmObject>("value[*].@keys == 'key1'").find().single().run {
            assertEquals("EMBEDDED", id)
        }
        realm.query<JsonStyleRealmObject>("value[*].key3[0] == 9").find().single().run {
            assertEquals("EMBEDDED", id)
        }
    }
}
