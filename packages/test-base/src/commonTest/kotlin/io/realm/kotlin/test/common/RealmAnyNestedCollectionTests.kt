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

    // Set
    // - Import primitive values
    // - Set primitive values
    // - Notifications
    @Test
    fun setInRealmAny_copyToRealm() = runBlocking {
        val sample = Sample().apply { stringField = "SAMPLE" }
        realm.write {
            val instance = JsonStyleRealmObject().apply {
                // Assigning set
                // - How to prevent adding a set containing non-any elements?
                //   - Can we do this on the fly!?
                value = RealmAny.create(
                    realmSetOf(
                        RealmAny.create(5),
                        RealmAny.create("Realm"),
                        RealmAny.create(sample)
                    )
                )
            }
            copyToRealm(instance)
        }
        val anyValue: RealmAny = realm.query<JsonStyleRealmObject>().find().single().value!!
        assertEquals(RealmAny.Type.SET, anyValue.type)
    }

    @Test
    fun setInRealmAny_assignment() = runBlocking {
        realm.write {
            copyToRealm(Sample().apply { stringField = "SAMPLE" })
            val instance = copyToRealm(JsonStyleRealmObject())
            // Assigning set
            // - How to prevent adding a set containing non-any elements?
            //   - Can we do this on the fly!?
            instance.value = RealmAny.create(
                realmSetOf(
                    RealmAny.create(5),
                    RealmAny.create(4),
                    RealmAny.create(6)
                )
            )
            instance
        }
        val anyValue: RealmAny = realm.query<JsonStyleRealmObject>().find().single().value!!
        assertEquals(RealmAny.Type.SET, anyValue.type)
    }

    @Test
    fun setInRealmAny_throwsOnNestedCollections_copyToRealm() = runBlocking<Unit> {
        realm.write {
            JsonStyleRealmObject(ObjectId().toString()).apply {
                value =
                    RealmAny.create(realmSetOf(RealmAny.create(realmListOf(RealmAny.create(5)))))
            }.let {
                assertFailsWithMessage<IllegalArgumentException>("Cannot add collections to RealmSets") {
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
                assertFailsWithMessage<IllegalArgumentException>("Cannot add collections to RealmSets") {
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

            assertFailsWithMessage<IllegalArgumentException>("Cannot add collections to RealmSets") {
                set.add(RealmAny.create(realmListOf()))
            }

            assertFailsWithMessage<IllegalArgumentException>("Cannot add collections to RealmSets") {
                set.add(RealmAny.create(realmDictionaryOf()))
            }
        }
    }

    @Test
    fun listInRealmAny_copyToRealm() = runBlocking {
        val sample = Sample().apply { stringField = "SAMPLE" }
        realm.write<Unit> {
            JsonStyleRealmObject().apply {
                // Assigning list
                // - Quite verbose!?
                // - How to prevent/support adding a set containing non-any elements?
                //   - Can we do this on the fly!?
                //   - Type system to allow only `RealmAny.create(list: RealmList<RealmAny>)`
                value = RealmAny.create(
                    realmListOf(
                        RealmAny.create(5),
                        RealmAny.create("Realm"),
                        RealmAny.create(sample),
                    )
                )
                //     - Could add:
                //         fun realmAnyListOf(vararg: Any): RealmList<RealmAny>
                //       or
                //         fun Iterable.toRealmAnyList(): RealmList<RealmAny>
                //       to allow convenience like
                //         realmAnyListOf(5, "Realm", realmAnyListOf())
                //         listOf(3, "Realm", realmAnyListOf()).toRealmAnyList()
            }.let {
                copyToRealm(it)
            }
        }
        val instance = realm.query<JsonStyleRealmObject>().find().single()
        val anyValue: RealmAny = instance.value!!
        assertEquals(RealmAny.Type.LIST, anyValue.type)
//        TabbedStringBuilder().dumpRealmAny(anyValue)
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
        // FIXME Duplicate references not identified through RealmAny imports
//        assertEquals(1, realm.query<Sample>().find().size)

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
            instance.value = RealmAny.create(realmListOf(RealmAny.create(5)))

            val nestedList = instance.value!!.asList()
            assertEquals(5, nestedList[0]!!.asInt())

            // FIXME Overwrite nested list element with new list just updates existing list
            instance.value = realmAnyListOf(7)
            assertFailsWithMessage<IllegalStateException>("This is an ex-list") {
                assertEquals(5, nestedList[0]!!.asInt())
            }

            // Overwrite nested list element with new collection of different type
            instance.value = realmAnySetOf(8)

            // Update old list
            assertFailsWithMessage<IllegalStateException>("This is an ex-list") {
                nestedList.add(RealmAny.create(1))
            }
            // FIXME Throws RLM_ERR_INDEX_OUT_OF_BOUNDS instead of RLM_ERR_ILLEGAL_OPERATION
            assertFailsWithMessage<IllegalStateException>("This is an ex-list") {
                val realmAny = nestedList[0]
            }
        }
    }

    // List
    // - Notifications
    //   - Parent bound deletion

    // Dict
    // - Import primitive values, SET, LIST, MAP
    // - Put primitive values, SET, LIST, MAP
    //   - Deletes other lists
    // - Notifications
    //   - Parent bound deletion

    // Others
    // - Queries for nested elements??
    // - No collections as primary key arguments - RealmAny is not supported at all
    // - No collections as query arguments - DONE
    // - toJson/fromJson
    // - Serialization
    // - Dynamic API
    // - Importing objects with cache through a setter for nested collections

    @Test
    fun dictionaryInRealmAny_copyToRealm() = runBlocking {
        val sample = Sample().apply { stringField = "SAMPLE" }
        // Import
        realm.write {
            // Normal realm link/object reference
            JsonStyleRealmObject().apply {
                // Assigning dictornary with nested lists and dictionaries
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
        // FIXME Duplicate references not identified through RealmAny imports
//        assertEquals(1, realm.query<Sample>().find().size)
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
        } // FIXME Duplicate references not identified through RealmAny imports
//        assertEquals(1, realm.query<Sample>().find().size)
    }

    @Test
    fun nestedCollectionsInDictionary_put_invalidatesOldElement() = runBlocking {
        realm.write {
            val instance = copyToRealm(
                JsonStyleRealmObject().apply {
                    value = RealmAny.create(
                        realmDictionaryOf("key" to RealmAny.create(realmListOf(RealmAny.create(5))))
                    )
                }
            )
            val nestedList = instance.value!!.asDictionary()["key"]!!.asList()
            assertEquals(5, nestedList[0]!!.asInt())
            // Overwrite nested list element with new list
            instance.value!!.asDictionary()["key"] = RealmAny.create(realmListOf(RealmAny.create(7)))
            // FIXME This shouldn't be true. We shouldn't have overwrite the old list
//            assertEquals(5, nestedList[0]!!.asInt())
            // Overwrite nested list element with new collection of different type
            instance.value!!.asDictionary()["key"] = RealmAny.create(realmSetOf(RealmAny.create(8)))
            // Access the old list
            // FIXME Seems like we don't throw a nice error when accessing a delete collection
            //  Overwriting with different collection type seems to ruin original item without
            //  throwing proper fix
            nestedList[0] = RealmAny.create(5)
            val realmAny = nestedList[0]
            assertEquals(7, realmAny!!.asInt())
        }
    }

    @Test
    fun query_ThrowsOnNestedCollectionArguments() {
        assertFailsWithMessage<IllegalArgumentException>("Cannot use nested collections as primary keys or query arguments") {
            realm.query<JsonStyleRealmObject>("value == $0", RealmAny.create(realmSetOf()))
        }
        assertFailsWithMessage<IllegalArgumentException>("Cannot use nested collections as primary keys or query arguments") {
            realm.query<JsonStyleRealmObject>("value == $0", RealmAny.create(realmListOf()))
        }
        assertFailsWithMessage<IllegalArgumentException>("Cannot use nested collections as primary keys or query arguments") {
            realm.query<JsonStyleRealmObject>("value == $0", RealmAny.create(realmDictionaryOf()))
        }
    }

    @Test
    fun query() = runBlocking<Unit> {
        realm.write {
            copyToRealm(JsonStyleRealmObject().apply {
                _id = "SET"
//                value = RealmAny.create(realmSetOf(RealmAny.create(1), RealmAny.create(2), RealmAny.create(3)))
                value = realmAnySetOf(1, 2, 3)
            })
            copyToRealm(JsonStyleRealmObject().apply {
                _id = "LIST"
                value = realmAnyListOf(4, 5, 6)
            })
            copyToRealm(JsonStyleRealmObject().apply {
                _id = "DICT"
                value = realmAnyDictionaryOf(
                        "key1" to 7,
                        "key2" to 8,
                        "key3" to 9,
                    )
            })
            copyToRealm(JsonStyleRealmObject().apply {
                _id = "EMBEDDED"
                value = realmAnyListOf(
                    setOf(1, 2, 3),
                    listOf(4, 5, 6),
                    mapOf(
                        "key1" to 7,
                        "key2" to 8,
                        "key3" to listOf(9),
                    )
                )
            })
        }

        assertEquals(4, realm.query<JsonStyleRealmObject>().find().size)

        // Matching sets
//        realm.query<JsonStyleRealmObject>("value[0] == 1").find().single().run {
//            assertEquals("SET", id)
//        }
//        realm.query<JsonStyleRealmObject>("value[*] == 1").find().single().run {
//            assertEquals("SET", id)
//        }
        // Size
        // [RLM_ERR_INVALID_QUERY]: Operation '@size' is not supported on property of type 'mixed'
        // assertEquals(1, realm.query<JsonStyleRealmObject>("value[*].@size == 3").find().size)

        // Matching lists
        realm.query<JsonStyleRealmObject>("value[0] == 4").find().single().run {
            assertEquals("LIST", _id)
        }
        realm.query<JsonStyleRealmObject>("value[*] == 4").find().single().run {
            assertEquals("LIST", _id)
        }
        // Size
        // [RLM_ERR_INVALID_QUERY]: Operation '@size' is not supported on property of type 'mixed'
        // assertEquals(1, realm.query<JsonStyleRealmObject>("value[1].@size == 3").find().size)

        // Matching dictionaries
        assertEquals(1, realm.query<JsonStyleRealmObject>("value.key1 == 7").find().size)
        assertEquals(1, realm.query<JsonStyleRealmObject>("value['key1'] == 7").find().size)
        assertEquals(0, realm.query<JsonStyleRealmObject>("value.unknown == 3").find().size)
        assertEquals(1, realm.query<JsonStyleRealmObject>("value.@keys == 'key1'").find().size)
        assertEquals(0, realm.query<JsonStyleRealmObject>("value.@keys == 'unknown'").find().size)

        // None
        assertTrue { realm.query<JsonStyleRealmObject>("value[*] == 10").find().isEmpty() }

        // Matching across all elements and in nested structures
//        realm.query<JsonStyleRealmObject>("value[*][*] == 1").find().single().run {
//            assertEquals("EMBEDDED", id)
//        }
        realm.query<JsonStyleRealmObject>("value[*][*] == 4").find().single().run {
            assertEquals("EMBEDDED", _id)
        }
        realm.query<JsonStyleRealmObject>("value[*][*] == 7").find().single().run {
            assertEquals("EMBEDDED", _id)
        }
        realm.query<JsonStyleRealmObject>("value[*].@keys == 'key1'").find().single().run {
            assertEquals("EMBEDDED", _id)
        }
        realm.query<JsonStyleRealmObject>("value[*].key3[0] == 9").find().single().run {
            assertEquals("EMBEDDED", _id)
        }
    }
}

class TabbedStringBuilder {
    private val builder = StringBuilder()
    internal var indentation = 0
    internal fun append(s: String) = builder.append("\t".repeat(indentation) + s + "\n")
    override fun toString(): String {
        return builder.toString()
    }
}

fun TabbedStringBuilder.dumpRealmAny(value: RealmAny?) {
    if (value == null) {
        append("null")
        return
    }
    when (value.type) {
        RealmAny.Type.SET, RealmAny.Type.LIST -> {
            val collection: Collection<RealmAny?> =
                if (value.type == RealmAny.Type.SET) value.asSet() else value.asList()
            append("[")
            indentation += 1
            collection.map { dumpRealmAny(it) }
            indentation -= 1
            append("]")
        }
        RealmAny.Type.DICTIONARY -> value.asDictionary().let { dictionary ->
            append("{")
            indentation += 1
            dictionary.map { (key, element) ->
                append("$key:")
                indentation += 1
                dumpRealmAny(element)
                indentation -= 1
            }
            indentation -= 1
            append("}")
        }
        else -> append(value.toString())
    }
}
