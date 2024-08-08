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
package io.realm.kotlin.test.common

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmDictionaryOf
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue


@Suppress("LargeClass")
class RealmInsertTests {

    private lateinit var configuration: RealmConfiguration
    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration.Builder(
            schema = setOf(
                SimplePOJO::class,
                SimpleEmbedded::class,
            )
        ).directory(tmpDir).build()
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
    fun insertToRealmLinks() {
        val simplePojo0 = realm.writeBlocking {
            copyToRealm(SimplePOJO().apply { primaryKey = 0; stringField = "simplePojo0" })
//                insertToRealm(simplePojo2)
        }
        val simplePojo1 =
            SimplePOJO().apply { primaryKey = 1; stringField = "simplePojo1"; parent = simplePojo0 }
        val simplePojo2 =
            SimplePOJO().apply { primaryKey = 2; stringField = "simplePojo2"; parent = simplePojo1 }

        realm.writeBlocking {
            insertToRealm(simplePojo2)
        }
        assertEquals(3, realm.query<SimplePOJO>().count().find())
        assertEquals(
            "simplePojo0",
            realm.query<SimplePOJO>("primaryKey = 2").first().find()?.parent?.parent?.stringField
        )
    }

    @Test
    fun insertToRealmRealmList() {
        val simplePojo: SimplePOJO = SimplePOJO().apply {
            primaryKey = 0;
            stringField = "simplePojo0";
            nullableStringListField.addAll(
                arrayOf("One", "Two", null, "Three")
            )
        }
        realm.writeBlocking {
            insertToRealm(simplePojo)
        }
        assertEquals(1, realm.query<SimplePOJO>().count().find())

        val first: SimplePOJO = realm.query<SimplePOJO>().first().find()!!
        assertEquals("simplePojo0", first.stringField)
        assertEquals(4, first.nullableStringListField.size)
        assertEquals("One", first.nullableStringListField[0])
        assertEquals("Two", first.nullableStringListField[1])
        assertEquals(null, first.nullableStringListField[2])
        assertEquals("Three", first.nullableStringListField[3])

        assertTrue(first.objectListField.isEmpty())

        // making sure clean is called before inserting new elements
        realm.writeBlocking {
            // do an update with an existing object with the same primary key but different list elements
            val simplePojoUpdated: SimplePOJO = SimplePOJO().apply {
                primaryKey = 0;
                stringField = "simplePojo0 updated";
                nullableStringListField.addAll(
                    arrayOf("Nabil", "Hachicha")
                )
                objectListField
                    .addAll(
                        arrayOf(copyToRealm(SimplePOJO().apply {
                            primaryKey = 1
                            stringField = "simplePojo1"
                            nullableStringListField.addAll(
                                arrayOf("SP1", null, "SP2")
                            )
                        }),// managed
                            SimplePOJO().apply {
                                primaryKey = 2; stringField = "simplePojo2"
                            } // unmanaged
                        )
                    )
            }
            insertToRealm(simplePojoUpdated, updatePolicy = UpdatePolicy.ALL)
//                findLatest(first)!!.let {
//                    it.stringField = "simplePojo0 updated"
//                    it.stringField = "simplePojo0 updated" // check we can't modify the
//                }
        }

        assertEquals(3, realm.query<SimplePOJO>().count().find())
        var updated: SimplePOJO = realm.query<SimplePOJO>().first().find()!!
        assertEquals("simplePojo0 updated", updated.stringField)
        assertEquals(2, updated.nullableStringListField.size)
        assertEquals("Nabil", updated.nullableStringListField[0])
        assertEquals("Hachicha", updated.nullableStringListField[1])

        assertEquals(2, updated.objectListField.size)
        assertEquals("simplePojo1", updated.objectListField[0].stringField)
        assertEquals(3, updated.objectListField[0].nullableStringListField.size)
        assertEquals("SP1", updated.objectListField[0].nullableStringListField[0])
        assertNull(updated.objectListField[0].nullableStringListField[1])
        assertEquals("SP2", updated.objectListField[0].nullableStringListField[2])
        assertEquals("simplePojo2", updated.objectListField[1].stringField)

        // embedded list
        realm.writeBlocking {
            // do an update with an existing object with the same primary key but different list elements
            val simplePojoUpdated: SimplePOJO = SimplePOJO().apply {
                primaryKey = 0
                stringField = "simplePojo0 updated 2"
                embeddedObjectListField
                    .addAll(arrayOf(
                        SimpleEmbedded().apply { id = "embedded1"; },
                        SimpleEmbedded().apply { id = "embedded2"; }
                    ))
            }
            insertToRealm(simplePojoUpdated, updatePolicy = UpdatePolicy.ALL)
        }

        updated = realm.query<SimplePOJO>().first().find()!!
        assertEquals("simplePojo0 updated 2", updated.stringField)
//            assertEquals(2, updated.nullableStringListField.size)
        assertEquals(2, updated.embeddedObjectListField.size)
        assertEquals("embedded1", updated.embeddedObjectListField[0].id)
        assertEquals("embedded2", updated.embeddedObjectListField[1].id)

    }

    @Test
    fun insertToRealmEmbedded() {
        realm.writeBlocking {
            insertToRealm(
                SimplePOJO().apply {
                    primaryKey = 0
                    stringField = "SimplePOJO 0"
                    embedded = SimpleEmbedded().apply {
                        id = "42"; link =
                        SimplePOJO().apply { primaryKey = 1; stringField = "SimplePOJO 1" }
                    }
                })
        }

        assertEquals(2, realm.query<SimplePOJO>().count().find())
        assertEquals(1, realm.query<SimpleEmbedded>().count().find())
    }

    @Test
    fun insertToRealmAny() {
        realm.writeBlocking {
            insertToRealm(
                SimplePOJO().apply {
                    primaryKey = 0
                    stringField = "SimplePOJO 0"
                    nullableRealmAnyField = RealmAny.create("string any field")
                })
        }

        assertEquals(1, realm.query<SimplePOJO>().count().find())
        var pojo: SimplePOJO = realm.query<SimplePOJO>().first().find()!!
        assertEquals(0, pojo.primaryKey)
        assertEquals("SimplePOJO 0", pojo.stringField)
        assertEquals(RealmAny.Type.STRING, pojo.nullableRealmAnyField?.type)
        assertEquals("string any field", pojo.nullableRealmAnyField?.asString())

        realm.writeBlocking {
            val updatedPOJO = SimplePOJO().apply {
                primaryKey = 0
                stringField = "updated 1"
                nullableRealmAnyField = null
            }

            insertToRealm(updatedPOJO, updatePolicy = UpdatePolicy.ALL)
        }

        pojo = realm.query<SimplePOJO>().first().find()!!
        assertEquals("updated 1", pojo.stringField)
        assertNull(pojo.nullableRealmAnyField)

        realm.writeBlocking {
            val managedPojo = copyToRealm(SimplePOJO().apply { primaryKey = 1 })

            val updatedPOJO = SimplePOJO().apply {
                primaryKey = 0
                stringField = "updated 2"
                nullableRealmAnyField = RealmAny.create(managedPojo)
            }

            insertToRealm(updatedPOJO, updatePolicy = UpdatePolicy.ALL)
        }

        pojo = realm.query<SimplePOJO>("primaryKey = 0").first().find()!!
        assertEquals("updated 2", pojo.stringField)
        assertEquals(RealmAny.Type.OBJECT, pojo.nullableRealmAnyField?.type)
        assertEquals(1, pojo.nullableRealmAnyField?.asRealmObject<SimplePOJO>()?.primaryKey)

        realm.writeBlocking {
            val updatedPOJO = SimplePOJO().apply {
                primaryKey = 0
                stringField = "updated 3"
                nullableRealmAnyField = RealmAny.create(
                    realmListOf(
                        RealmAny.create("One"),
                        null,
                        RealmAny.create(17)
                    )
                ) // try embedded FIXME enabling this crash the test
            }

            insertToRealm(updatedPOJO, updatePolicy = UpdatePolicy.ALL)
        }

        pojo = realm.query<SimplePOJO>("primaryKey = 0").first().find()!!
        assertEquals("updated 3", pojo.stringField)
        assertEquals(RealmAny.Type.LIST, pojo.nullableRealmAnyField?.type)
        var asList: RealmList<RealmAny?> = pojo.nullableRealmAnyField?.asList()!!
        assertEquals(3, asList.size)
        assertEquals("One", asList[0]?.asString())
        assertNull(asList[1])
        assertEquals(17, asList[2]?.asInt())

        // deeply nested list
        realm.writeBlocking {
            val updatedPOJO = SimplePOJO().apply {
                primaryKey = 0
                stringField = "updated 4"
                nullableRealmAnyField =
                    RealmAny.create(
                        realmListOf(
                            RealmAny.create("One"), null, RealmAny.create(17),
                            RealmAny.create(
                                realmListOf(
                                    RealmAny.create("Level2"),
                                    RealmAny.create(
                                        realmListOf(
                                            RealmAny.create(42),
                                            null,
                                            null,
                                            RealmAny.create("Level2 String")
                                        )
                                    )
                                )
                            )
                        )
                    )
            }

            insertToRealm(updatedPOJO, updatePolicy = UpdatePolicy.ALL)
        }

        pojo = realm.query<SimplePOJO>("primaryKey = 0").first().find()!!
        assertEquals("updated 4", pojo.stringField)
        assertEquals(RealmAny.Type.LIST, pojo.nullableRealmAnyField?.type)
        asList = pojo.nullableRealmAnyField?.asList()!! // level 1
        assertEquals(4, asList.size)
        assertEquals("One", asList[0]?.asString())
        assertNull(asList[1])
        assertEquals(17, asList[2]?.asInt())

        assertEquals(RealmAny.Type.LIST, asList[3]?.type)
        var nestedList1 = asList[3]?.asList()!! // level 1
        assertEquals(2, nestedList1.size)
        assertEquals("Level2", nestedList1[0]?.asString())

        assertEquals(RealmAny.Type.LIST, nestedList1[1]?.type)
        var nestedList2 = nestedList1[1]?.asList()!! // level 2
        assertEquals(4, nestedList2.size)
        assertEquals(42, nestedList2[0]?.asInt())
        assertNull(nestedList2[1]?.asInt())
        assertNull(nestedList2[2]?.asInt())
        assertEquals("Level2 String", nestedList2[3]?.asString())

        realm.writeBlocking {
            val updatedPOJO = SimplePOJO().apply {
                primaryKey = 0
                stringField = "updated 5"
                realmAnyListField = realmListOf(
                    RealmAny.create(10),
                    null,
                    RealmAny.create(true),
                    RealmAny.create(
                        realmListOf(
                            RealmAny.create("str1"),
                            RealmAny.create(
                                realmListOf(
                                    RealmAny.create("Level2"),
                                    RealmAny.create(
                                        realmListOf(
                                            RealmAny.create(42),
                                            null,
                                            null,
                                            RealmAny.create("Level2 String")
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            }
            insertToRealm(updatedPOJO, updatePolicy = UpdatePolicy.ALL)
        }

        pojo = realm.query<SimplePOJO>("primaryKey = 0").first().find()!!
        assertEquals("updated 5", pojo.stringField)
        assertEquals(4, pojo.realmAnyListField.size)

        assertEquals(10, pojo.realmAnyListField[0]?.asInt())
        assertNull(pojo.realmAnyListField[1])
        assertTrue(pojo.realmAnyListField[2]?.asBoolean()!!)
        assertEquals(RealmAny.Type.LIST, pojo.realmAnyListField[3]?.type)
        nestedList1 = pojo.realmAnyListField[3]?.asList()!!
        assertEquals(2, nestedList1.size)
        assertEquals("str1", nestedList1[0]?.asString())
        assertEquals(RealmAny.Type.LIST, nestedList1[1]?.type)
        nestedList2 = nestedList1[1]?.asList()!!
        assertEquals(2, nestedList2.size)

        assertEquals("Level2", nestedList2[0]?.asString())
        assertEquals(RealmAny.Type.LIST, nestedList2[1]?.type)
        val nestedList3 = nestedList2[1]?.asList()!!
        assertEquals(4, nestedList3.size)
        assertEquals(42, nestedList3[0]?.asInt())
        assertNull(nestedList3[1])
        assertNull(nestedList3[2])
        assertEquals("Level2 String", nestedList3[3]?.asString())
    }

    @Test
    fun insertToRealmAnyDictionary() {
        realm.writeBlocking {
            insertToRealm(SimplePOJO().apply {
                primaryKey = 10
                nullableRealmAnyField = RealmAny.create(
                    realmDictionaryOf(
                        "d1" to RealmAny.create("One"),
                        "d2" to null,
                        "d3" to RealmAny.create(17)
                    )
                ) // try embedded
            })
        }
        val pojo = realm.query<SimplePOJO>("primaryKey = 10").first().find()!!
        assertEquals(3, pojo.nullableRealmAnyField?.asDictionary()?.size)
        assertEquals("One", pojo.nullableRealmAnyField?.asDictionary()?.get("d1")?.asString())
        assertNull(pojo.nullableRealmAnyField?.asDictionary()?.get("d2"))
        assertEquals(17, pojo.nullableRealmAnyField?.asDictionary()?.get("d3")?.asInt())
    }

    // FIXME this test will fail even when using copyToRealm
    // apparently changing the content of RealmAny from a RealmList to primitive type crash
    // E REALM   : packages/external/core/src/realm/array.cpp:430: [realm-core-14.7.0] Assertion failed: ndx <= m_size
    @Test
    fun insertWithAnyBug() {
        realm.writeBlocking {
            val updatedPOJO = SimplePOJO().apply {
                primaryKey = 0
                stringField = "updated 1"
                nullableRealmAnyField = null
            }

            copyToRealm(updatedPOJO, updatePolicy = UpdatePolicy.ALL)
        }

        realm.writeBlocking {
            val managedPojo = copyToRealm(SimplePOJO().apply { primaryKey = 1 })

            val updatedPOJO = SimplePOJO().apply {
                primaryKey = 0
                stringField = "updated 2"
                nullableRealmAnyField = RealmAny.create(managedPojo)
            }

            copyToRealm(updatedPOJO, updatePolicy = UpdatePolicy.ALL)
        }

        realm.writeBlocking {
            val updatedPOJO = SimplePOJO().apply {
                primaryKey = 0
//                    nullableRealmAnyField = RealmAny.create(42)
                nullableRealmAnyField = RealmAny.create(realmListOf())
//                    nullableRealmAnyField = RealmAny.create(realmListOf(RealmAny.create("One"), null, RealmAny.create(17))) // FIXME enabling this crash the test
            }

            copyToRealm(updatedPOJO, updatePolicy = UpdatePolicy.ALL)
        }

        // The following transaction fails with : packages/external/core/src/realm/array.cpp:430: [realm-core-14.7.0] Assertion failed: ndx <= m_size
        realm.writeBlocking {
            copyToRealm(SimplePOJO().apply {
//                    primaryKey = 10
//                    nullableRealmAnyField = RealmAny.create(realmDictionaryOf("d1" to RealmAny.create("One"), "d2" to null, "d3" to RealmAny.create(17))) // try embedded
            })
        }
    }

    @Test
    fun insertToRealmSet() {
        val simplePojo = SimplePOJO().apply {
            primaryKey = 0
            stringField = "simplePojo"
            realmSetField.addAll(arrayOf(1, 2, 3))

        }

        realm.writeBlocking {
            insertToRealm(simplePojo)
        }
        var first: SimplePOJO = realm.query<SimplePOJO>().first().find()!!
        assertEquals("simplePojo", first.stringField)
        assertEquals(3, first.realmSetField.size)
        assertEquals(1, first.realmSetField.elementAt(0))
        assertEquals(2, first.realmSetField.elementAt(1))
        assertEquals(3, first.realmSetField.elementAt(2))

        realm.writeBlocking {
            insertToRealm(SimplePOJO().apply {
                primaryKey = 0; stringField = "simplePojo update"
                realmSetObjectField.addAll(arrayOf(
                    copyToRealm(SimplePOJO().apply {
                        primaryKey = 1; stringField = "simplePojo1"
                    }),
                    SimplePOJO().apply { primaryKey = 2; stringField = "simplePojo2" }
                ))
            }, updatePolicy = UpdatePolicy.ALL)
        }

        first = realm.query<SimplePOJO>().first().find()!!
        assertEquals("simplePojo update", first.stringField)
        assertEquals(2, first.realmSetObjectField.size)
        assertEquals(1, first.realmSetObjectField.elementAt(0).primaryKey)
        assertEquals("simplePojo1", first.realmSetObjectField.elementAt(0).stringField)
        assertEquals(2, first.realmSetObjectField.elementAt(1).primaryKey)
        assertEquals("simplePojo2", first.realmSetObjectField.elementAt(1).stringField)

        realm.writeBlocking {
            insertToRealm(SimplePOJO().apply {
                primaryKey = 0
                stringField = "simplePojo update 2"
                realmSetAnyField.addAll(
                    arrayOf(
                        RealmAny.create(42),
                        null,
                        RealmAny.create(SimplePOJO().apply { primaryKey = 3 }),
//                        RealmAny.create(realmListOf(RealmAny.create("One"), RealmAny.create("Two"))) TODO this should be supported
                    )
                )
            }, UpdatePolicy.ALL)
        }
        first = realm.query<SimplePOJO>().first().find()!!
        assertEquals("simplePojo update 2", first.stringField)
        assertEquals(3, first.realmSetAnyField.size)
        assertEquals(42, first.realmSetAnyField.elementAt(1)?.asInt())
        assertNull(first.realmSetAnyField.elementAt(0))// TODO it looks like RealmAny of null is stored at first position? set doesn't guarantee position
        assertEquals(
            3,
            first.realmSetAnyField.elementAt(2)?.asRealmObject<SimplePOJO>()?.primaryKey
        )
//            assertEquals(2, first.realmSetAnyField.elementAt(3)?.asList()?.size)
//            assertEquals("One", first.realmSetAnyField.elementAt(3)?.asList()?.get(0)?.asString())
//            assertEquals("Two", first.realmSetAnyField.elementAt(3)?.asList()?.get(1)?.asString())
    }

    @Test
    fun insertToRealmDictionary() {
        val simplePojo = SimplePOJO().apply {
            primaryKey = 0
            stringField = "simplePojo"
            realmDictionaryField["k1"] = 1
            realmDictionaryField["k2"] = 2
            realmDictionaryField["k3"] = 3
        }

        realm.writeBlocking {
            insertToRealm(simplePojo)
        }

        var first: SimplePOJO = realm.query<SimplePOJO>().first().find()!!
        assertEquals("simplePojo", first.stringField)
        assertEquals(3, first.realmDictionaryField.size)
        assertEquals(3, first.realmDictionaryField["k3"])
        assertEquals(1, first.realmDictionaryField["k1"])
        assertEquals(2, first.realmDictionaryField["k2"])

        realm.writeBlocking {
            insertToRealm(SimplePOJO().apply {
                primaryKey = 0
                stringField = "updated"
                realmDictionaryObjectField["key1"] = SimplePOJO().apply { primaryKey = 1 }
                realmDictionaryObjectField["key2"] = null
                realmDictionaryObjectField["key3"] =
                    copyToRealm(SimplePOJO().apply { primaryKey = 2 })
            }, updatePolicy = UpdatePolicy.ALL)
        }

        first = realm.query<SimplePOJO>().first().find()!!
        assertEquals("updated", first.stringField)
        assertEquals(3, first.realmDictionaryObjectField.size)
        assertEquals(1, first.realmDictionaryObjectField["key1"]?.primaryKey)
        assertNull(first.realmDictionaryObjectField["key2"])
        assertEquals(2, first.realmDictionaryObjectField["key3"]?.primaryKey)

        realm.writeBlocking {
            insertToRealm(SimplePOJO().apply {
                primaryKey = 0
                stringField = "updated 2"
                realmDictionaryEmbeddedField["key1"] = SimpleEmbedded().apply { id = "e1" }
                realmDictionaryEmbeddedField["key2"] = null
                realmDictionaryEmbeddedField["key3"] = copyToRealm(SimplePOJO().apply {
                    primaryKey = 3; embedded = SimpleEmbedded().apply { id = "e2" }
                }).embedded
            }, updatePolicy = UpdatePolicy.ALL)
        }

        first = realm.query<SimplePOJO>().first().find()!!
        assertEquals("updated 2", first.stringField)
        assertEquals(3, first.realmDictionaryEmbeddedField.size)
        assertEquals("e1", first.realmDictionaryEmbeddedField["key1"]?.id)
        assertNull(first.realmDictionaryEmbeddedField["key2"])
        assertEquals("e2", first.realmDictionaryEmbeddedField["key3"]?.id)


        realm.writeBlocking {
            insertToRealm(SimplePOJO().apply {
                primaryKey = 0
                stringField = "updated RealmAny"
                realmDictionaryAnyField["key_Primitive"] = RealmAny.create(42)
                realmDictionaryAnyField["key_Null"] = null
                realmDictionaryAnyField["key_RealmList"] =
                    RealmAny.create(realmListOf(RealmAny.create("One"), null, RealmAny.create(19)))
                realmDictionaryAnyField["key_RealmObject"] =
                    RealmAny.create(copyToRealm(SimplePOJO().apply { primaryKey = 4 }))
                realmDictionaryAnyField["key_RealmDictionary"] = RealmAny.create(
                    realmDictionaryOf(
                        "d1" to RealmAny.create(7),
                        "d2" to null,
                        "d3" to RealmAny.create("foo")
                    )
                )
            }, updatePolicy = UpdatePolicy.ALL)
        }

        first = realm.query<SimplePOJO>().first().find()!!
        assertEquals("updated RealmAny", first.stringField)
        assertEquals(5, first.realmDictionaryAnyField.size)

        assertEquals(42, first.realmDictionaryAnyField["key_Primitive"]?.asInt())

        assertNull(first.realmDictionaryAnyField["key_Null"])

        assertEquals(3, first.realmDictionaryAnyField["key_RealmList"]?.asList()?.size)
        assertEquals(
            "One",
            first.realmDictionaryAnyField["key_RealmList"]?.asList()?.get(0)?.asString()
        )
        assertNull(first.realmDictionaryAnyField["key_RealmList"]?.asList()?.get(1))
        assertEquals(19, first.realmDictionaryAnyField["key_RealmList"]?.asList()?.get(2)?.asInt())

        assertEquals(
            4,
            first.realmDictionaryAnyField["key_RealmObject"]?.asRealmObject<SimplePOJO>()?.primaryKey
        )

        assertEquals(3, first.realmDictionaryAnyField["key_RealmDictionary"]?.asDictionary()?.size)
        assertEquals(
            7,
            first.realmDictionaryAnyField["key_RealmDictionary"]?.asDictionary()?.get("d1")?.asInt()
        )
        assertNull(first.realmDictionaryAnyField["key_RealmDictionary"]?.asDictionary()?.get("d2"))
        assertEquals(
            "foo",
            first.realmDictionaryAnyField["key_RealmDictionary"]?.asDictionary()?.get("d3")
                ?.asString()
        )

        realm.writeBlocking {
            insertToRealm(SimplePOJO().apply {
                primaryKey = 0
                nullableRealmAnyField = RealmAny.create(
                    realmDictionaryOf(
                        "d1" to RealmAny.create("One"),
                        "d2" to null,
                        "d3" to RealmAny.create(17)
                    )
                ) // try embedded
            }, updatePolicy = UpdatePolicy.ALL)
        }
        val pojo = realm.query<SimplePOJO>("primaryKey = 0").first().find()!!
        assertEquals(3, pojo.nullableRealmAnyField?.asDictionary()?.size)
        assertEquals("One", pojo.nullableRealmAnyField?.asDictionary()?.get("d1")?.asString())
        assertNull(pojo.nullableRealmAnyField?.asDictionary()?.get("d2"))
        assertEquals(17, pojo.nullableRealmAnyField?.asDictionary()?.get("d3")?.asInt())
    }
}

class SimpleEmbedded : EmbeddedRealmObject {
    var id: String? = ""
    var link: SimplePOJO? = null
}

class SimplePOJO : RealmObject {
    @PrimaryKey
    var primaryKey: Long = 42L
    var stringField: String = "Realm"
    var parent: SimplePOJO? = null
    var embedded: SimpleEmbedded? = null
    var nullableRealmAnyField: RealmAny? = null


    //    var stringListField: RealmList<String> = realmListOf()
//    var byteListField: RealmList<Byte> = realmListOf()
//    var charListField: RealmList<Char> = realmListOf()
//    var shortListField: RealmList<Short> = realmListOf()
//    var intListField: RealmList<Int> = realmListOf()
//    var longListField: RealmList<Long> = realmListOf()
//    var booleanListField: RealmList<Boolean> = realmListOf()
//    var floatListField: RealmList<Float> = realmListOf()
//    var doubleListField: RealmList<Double> = realmListOf()
//    var timestampListField: RealmList<RealmInstant> = realmListOf()
//    var bsonObjectIdListField: RealmList<BsonObjectId> = realmListOf()
//    var binaryListField: RealmList<ByteArray> = realmListOf()
    var objectListField: RealmList<SimplePOJO> = realmListOf()
    var embeddedObjectListField: RealmList<SimpleEmbedded> = realmListOf()
    var realmAnyListField: RealmList<RealmAny?> = realmListOf()
    var nullableStringListField: RealmList<String?> = realmListOf()

    var realmSetField: RealmSet<Int> = realmSetOf()
    var realmSetObjectField: RealmSet<SimplePOJO> = realmSetOf()
    var realmSetAnyField: RealmSet<RealmAny?> = realmSetOf()

    var realmDictionaryField: RealmDictionary<Int> = realmDictionaryOf()
    var realmDictionaryObjectField: RealmDictionary<SimplePOJO?> = realmDictionaryOf()
    var realmDictionaryEmbeddedField: RealmDictionary<SimpleEmbedded?> = realmDictionaryOf()
    var realmDictionaryAnyField: RealmDictionary<RealmAny?> = realmDictionaryOf()
//

//    var nullableByteListField: RealmList<Byte?> = realmListOf()
//    var nullableCharListField: RealmList<Char?> = realmListOf()
//    var nullableShortListField: RealmList<Short?> = realmListOf()
//    var nullableIntListField: RealmList<Int?> = realmListOf()
//    var nullableLongListField: RealmList<Long?> = realmListOf()
//    var nullableBooleanListField: RealmList<Boolean?> = realmListOf()
//    var nullableFloatListField: RealmList<Float?> = realmListOf()
//    var nullableDoubleListField: RealmList<Double?> = realmListOf()
//    var nullableTimestampListField: RealmList<RealmInstant?> = realmListOf()
//    var nullableBsonObjectIdListField: RealmList<BsonObjectId?> = realmListOf()
//    var nullableBinaryListField: RealmList<ByteArray?> = realmListOf()
}
