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

@file:OptIn(ExperimentalKBsonSerializerApi::class)

package io.realm.kotlin.test.mongodb.common.mongo

import io.realm.kotlin.entities.sync.SyncObjectWithAllTypes
import io.realm.kotlin.entities.sync.flx.FlexEmbeddedObject
import io.realm.kotlin.entities.sync.flx.FlexParentObject
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.realmAnyDictionaryOf
import io.realm.kotlin.ext.realmAnyListOf
import io.realm.kotlin.ext.realmDictionaryOf
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.mongodb.mongo.realmSerializerModule
import io.realm.kotlin.test.mongodb.common.utils.assertFailsWithMessage
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmUUID
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import org.mongodb.kbson.ObjectId
import org.mongodb.kbson.serialization.EJson
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MongoDBSerializerTests {

    lateinit var eJson: EJson

    @BeforeTest
    fun setUp() {
        @Suppress("invisible_member")
        eJson = EJson(
            serializersModule = realmSerializerModule(
                setOf(
                    SyncObjectWithAllTypes::class,
                    FlexParentObject::class,
                    FlexEmbeddedObject::class
                )
            )
        )
    }

    @Test
    fun serialize() {
        assertEqualWithoutWhitespace(EXPECTED_EJSON, eJson.encodeToString(syncObjectWithAllTypes))
    }

    @Test
    fun serialize_embeddedObject() {
        assertEqualWithoutWhitespace(
            EXPECTED_EJSON_EMBEDDED,
            eJson.encodeToString(syncObjectWithEmbeddedObject)
        )
    }

    @Test
    fun deserialize() {
        val actual = eJson.decodeFromString<SyncObjectWithAllTypes>(EXPECTED_EJSON)
        with(actual) {
            // Verify all different types
            assertEquals(syncObjectWithAllTypes._id, _id)
            assertEquals(syncObjectWithAllTypes.stringField, stringField)
            assertEquals(syncObjectWithAllTypes.byteField, byteField)
            assertEquals(syncObjectWithAllTypes.charField, charField)
            assertEquals(syncObjectWithAllTypes.shortField, shortField)
            assertEquals(syncObjectWithAllTypes.intField, intField)
            assertEquals(syncObjectWithAllTypes.longField, longField)
            assertEquals(syncObjectWithAllTypes.booleanField, booleanField)
            assertEquals(syncObjectWithAllTypes.doubleField, doubleField)
            assertEquals(syncObjectWithAllTypes.floatField, floatField)
            assertEquals(syncObjectWithAllTypes.decimal128Field, decimal128Field)
            assertEquals(syncObjectWithAllTypes.realmInstantField, realmInstantField)
            assertContentEquals(syncObjectWithAllTypes.binaryField, binaryField)
            assertEquals(syncObjectWithAllTypes.mutableRealmIntField, mutableRealmIntField)
            assertEquals(syncObjectWithAllTypes.objectField!!._id, objectField!!._id)
            assertEquals(syncObjectWithAllTypes.objectIdField, objectIdField)
            assertEquals(syncObjectWithAllTypes.realmUUIDField, realmUUIDField)
            assertEquals(syncObjectWithAllTypes.realmInstantField, realmInstantField)
            // Verify RealmAny with nested lists and dictionaries
            nullableRealmAnyField!!.asList().let {
                val expectedRealmAnyList = syncObjectWithAllTypes.nullableRealmAnyField!!.asList()
                assertEquals(expectedRealmAnyList.size, it.size)
                assertEquals(
                    expectedRealmAnyList[0]!!.asRealmObject<SyncObjectWithAllTypes>()._id,
                    it[0]!!.asRealmObject<SyncObjectWithAllTypes>()._id
                )
                // Nested list
                val expectedNestedList = expectedRealmAnyList[1]!!.asList()
                val actualNestList = it[1]!!.asList()
                assertEquals(expectedNestedList.size, actualNestList.size)
                assertEquals(expectedNestedList[0], actualNestList[0])
                assertEquals(expectedNestedList[1], actualNestList[1])
                assertEquals(
                    expectedNestedList[2]!!.asRealmObject<SyncObjectWithAllTypes>()._id,
                    actualNestList[2]!!.asRealmObject<SyncObjectWithAllTypes>()._id
                )
                // Nested dictionary
                val expectedNestedDictionary = expectedRealmAnyList[2]!!.asDictionary()
                val actualNestDictionary = it[2]!!.asDictionary()
                assertEquals(expectedNestedDictionary.size, actualNestDictionary.size)
                assertEquals(expectedNestedDictionary["int"], actualNestDictionary["int"])
                assertEquals(expectedNestedDictionary["string"], actualNestDictionary["string"])
                assertEquals(
                    expectedNestedDictionary["object"]!!.asRealmObject<SyncObjectWithAllTypes>()._id,
                    actualNestDictionary["object"]!!.asRealmObject<SyncObjectWithAllTypes>()._id
                )
            }
            // Smoke test collection fields. Assume that type specific details are verified by the above
            // tests
            assertEquals(syncObjectWithAllTypes.stringRealmList, stringRealmList)
            assertEquals(syncObjectWithAllTypes.stringRealmSet, stringRealmSet)
            assertEquals(syncObjectWithAllTypes.stringRealmDictionary, stringRealmDictionary)
        }
    }

    @Test
    fun deserialize_embeddedObject() {
        val actual = eJson.decodeFromString<FlexParentObject>(EXPECTED_EJSON_EMBEDDED)
        with(actual) {
            assertEquals(syncObjectWithEmbeddedObject._id, _id)
            assertEquals(syncObjectWithEmbeddedObject.embedded!!.embeddedName, embedded!!.embeddedName)
        }
    }

    @Test
    fun deserialize_defaults() {
        eJson.decodeFromString<SyncObjectWithAllTypes>("{}")
    }

    @Test
    fun deserialize_throwsOnMalformedJSON() {
        assertFailsWith<SerializationException> {
            eJson.decodeFromString<SyncObjectWithAllTypes>("""{ "missing_value" : }""")
        }
    }

    @Test
    fun deserialize_throwsOnUnknownField() {
        assertFailsWithMessage<SerializationException>("Unknown field 'unknown_field' for type SyncObjectWithAllTypes") {
            eJson.decodeFromString<SyncObjectWithAllTypes>("""{ "unknown_field": 1 }""")
        }
    }

    @Test
    fun deserialize_unknownClassRefIsTreatedAsEmbeddedDict() {
        val o = eJson.decodeFromString<SyncObjectWithAllTypes>("""{ "nullableRealmAnyField": { "${"$"}ref": "unknown_class" } }""")
        assertEquals("unknown_class", o.nullableRealmAnyField!!.asDictionary()["${"$"}ref"]!!.asString())
    }
    @Test
    fun deserialize_missingIdIsTreatedAsEmbeddedDict() {
        val o = eJson.decodeFromString<SyncObjectWithAllTypes>(
            """
                { "nullableRealmAnyField": { "${"$"}ref" : "SyncObjectWithAllTyped", "unknown_field" : "UNKNOWN" } }
            """.trimIndent()
        )
        val realmAnyDictionary = o.nullableRealmAnyField!!.asDictionary()
        assertEquals("SyncObjectWithAllTyped", realmAnyDictionary["${"$"}ref"]!!.asString())
        assertEquals("UNKNOWN", realmAnyDictionary["unknown_field"]!!.asString())
    }
}

private fun assertEqualWithoutWhitespace(a: String, b: String) {
    assertEquals(a.replace("\\s+".toRegex(), ""), b.replace("\\s+".toRegex(), ""))
}

// Ensure test is reproducible by clearing random/time dependant values
// EJSON cannot represent nano second precision, so nanosecond fraction must be 0
private val date = RealmInstant.from(172, 0)
private val objectId = ObjectId(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11))
private val uuid = RealmUUID.Companion.from(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15))
private val child = SyncObjectWithAllTypes().apply { _id = "CHILD" }

private val syncObjectWithAllTypes: SyncObjectWithAllTypes = SyncObjectWithAllTypes().apply {

    _id = "PARENT"
    realmInstantField = date
    objectIdField = objectId
    objectIdRealmList = realmListOf(objectId)
    objectIdRealmSet = realmSetOf(objectId)
    objectIdRealmDictionary = realmDictionaryOf("key" to objectId)
    realmUUIDField = uuid
    realmUUIDRealmList = realmListOf(uuid)
    realmUUIDRealmSet = realmSetOf(uuid)
    realmUUIDRealmDictionary = realmDictionaryOf("key" to uuid)
    objectField = child
    nullableRealmAnyField = realmAnyListOf(
        child,
        realmAnyListOf(1, "Realm", child),
        realmAnyDictionaryOf("int" to 1, "string" to "Realm", "object" to child)
    )
}

private val EXPECTED_EJSON_EMBEDDED = """
    {"_id":{"${"$"}oid":"000102030405060708090a0b"},"section":{"${"$"}numberInt":"0"},"name":"","age":{"${"$"}numberInt":"42"},"child":null,"embedded":{"embeddedName":"EMBEDDED"}}
"""

private val syncObjectWithEmbeddedObject = FlexParentObject().apply {
    _id = objectId
    embedded = FlexEmbeddedObject().apply {
        embeddedName = "EMBEDDED"
    }
}

private val EXPECTED_EJSON = """
{
   "_id":"PARENT",
   "stringField":"hello world",
   "byteField":{
      "${"$"}numberInt":"0"
   },
   "charField":{
      "${"$"}numberInt":"0"
   },
   "shortField":{
      "${"$"}numberInt":"0"
   },
   "intField":{
      "${"$"}numberInt":"0"
   },
   "longField":{
      "${"$"}numberLong":"0"
   },
   "booleanField":true,
   "doubleField":{
      "${"$"}numberDouble":"0.0"
   },
   "floatField":{
      "${"$"}numberDouble":"0.0"
   },
   "decimal128Field":{
      "${"$"}numberDecimal":"0"
   },
   "realmInstantField":{
      "${"$"}date":{
         "${"$"}numberLong":"172000"
      }
   },
   "objectIdField":{
      "${"$"}oid":"000102030405060708090a0b"
   },
   "realmUUIDField":{
      "${"$"}binary":{
         "base64":"AAECAwQFBgcICQoLDA0ODw==",
         "subType":"04"
      }
   },
   "binaryField":{
      "${"$"}binary":{
         "base64":"Kg==",
         "subType":"00"
      }
   },
   "mutableRealmIntField":{
      "${"$"}numberLong":"42"
   },
   "objectField":"CHILD",
   "stringNullableField":null,
   "byteNullableField":null,
   "charNullableField":null,
   "shortNullableField":null,
   "intNullableField":null,
   "longNullableField":null,
   "booleanNullableField":null,
   "doubleNullableField":null,
   "floatNullableField":null,
   "decimal128NullableField":null,
   "realmInstantNullableField":null,
   "objectIdNullableField":null,
   "realmUUIDNullableField":null,
   "binaryNullableField":null,
   "objectNullableField":null,
   "mutableRealmIntNullableField":null,
   "nullableRealmAnyField":[
      {
         "${"$"}ref":"SyncObjectWithAllTypes",
         "${"$"}id":"CHILD"
      },
      [
         {
            "${"$"}numberLong":"1"
         },
         "Realm",
         {
            "${"$"}ref":"SyncObjectWithAllTypes",
            "${"$"}id":"CHILD"
         }
      ],
      {
         "int":{
            "${"$"}numberLong":"1"
         },
         "string":"Realm",
         "object":{
            "${"$"}ref":"SyncObjectWithAllTypes",
            "${"$"}id":"CHILD"
         }
      }
   ],
   "nullableRealmAnyForObjectField":null,
   "stringRealmList":[
      "hello world"
   ],
   "byteRealmList":[
      {
         "${"$"}numberInt":"0"
      }
   ],
   "charRealmList":[
      {
         "${"$"}numberInt":"0"
      }
   ],
   "shortRealmList":[
      {
         "${"$"}numberInt":"0"
      }
   ],
   "intRealmList":[
      {
         "${"$"}numberInt":"0"
      }
   ],
   "longRealmList":[
      {
         "${"$"}numberLong":"0"
      }
   ],
   "booleanRealmList":[
      true
   ],
   "doubleRealmList":[
      {
         "${"$"}numberDouble":"0.0"
      }
   ],
   "floatRealmList":[
      {
         "${"$"}numberDouble":"0.0"
      }
   ],
   "decimal128RealmList":[
      {
         "${"$"}numberDecimal":"0.0"
      }
   ],
   "realmInstantRealmList":[
      {
         "${"$"}date":{
            "${"$"}numberLong":"-9223372036854775808"
         }
      }
   ],
   "objectIdRealmList":[
      {
         "${"$"}oid":"000102030405060708090a0b"
      }
   ],
   "realmUUIDRealmList":[
      {
         "${"$"}binary":{
            "base64":"AAECAwQFBgcICQoLDA0ODw==",
            "subType":"04"
         }
      }
   ],
   "binaryRealmList":[
      {
         "${"$"}binary":{
            "base64":"Kg==",
            "subType":"00"
         }
      }
   ],
   "objectRealmList":[
      
   ],
   "nullableRealmAnyRealmList":[
      {
         "${"$"}numberLong":"42"
      }
   ],
   "stringRealmSet":[
      "hello world"
   ],
   "byteRealmSet":[
      {
         "${"$"}numberInt":"0"
      }
   ],
   "charRealmSet":[
      {
         "${"$"}numberInt":"0"
      }
   ],
   "shortRealmSet":[
      {
         "${"$"}numberInt":"0"
      }
   ],
   "intRealmSet":[
      {
         "${"$"}numberInt":"0"
      }
   ],
   "longRealmSet":[
      {
         "${"$"}numberLong":"0"
      }
   ],
   "booleanRealmSet":[
      true
   ],
   "doubleRealmSet":[
      {
         "${"$"}numberDouble":"0.0"
      }
   ],
   "floatRealmSet":[
      {
         "${"$"}numberDouble":"0.0"
      }
   ],
   "decimal128RealmSet":[
      {
         "${"$"}numberDecimal":"0.0"
      }
   ],
   "realmInstantRealmSet":[
      {
         "${"$"}date":{
            "${"$"}numberLong":"-9223372036854775808"
         }
      }
   ],
   "objectIdRealmSet":[
      {
         "${"$"}oid":"000102030405060708090a0b"
      }
   ],
   "realmUUIDRealmSet":[
      {
         "${"$"}binary":{
            "base64":"AAECAwQFBgcICQoLDA0ODw==",
            "subType":"04"
         }
      }
   ],
   "binaryRealmSet":[
      {
         "${"$"}binary":{
            "base64":"Kg==",
            "subType":"00"
         }
      }
   ],
   "objectRealmSet":[
      
   ],
   "nullableRealmAnyRealmSet":[
      {
         "${"$"}numberLong":"42"
      }
   ],
   "stringRealmDictionary":{
      "A":"hello world"
   },
   "byteRealmDictionary":{
      "A":{
         "${"$"}numberInt":"0"
      }
   },
   "charRealmDictionary":{
      "A":{
         "${"$"}numberInt":"0"
      }
   },
   "shortRealmDictionary":{
      "A":{
         "${"$"}numberInt":"0"
      }
   },
   "intRealmDictionary":{
      "A":{
         "${"$"}numberInt":"0"
      }
   },
   "longRealmDictionary":{
      "A":{
         "${"$"}numberLong":"0"
      }
   },
   "booleanRealmDictionary":{
      "A":true
   },
   "doubleRealmDictionary":{
      "A":{
         "${"$"}numberDouble":"0.0"
      }
   },
   "floatRealmDictionary":{
      "A":{
         "${"$"}numberDouble":"0.0"
      }
   },
   "decimal128RealmDictionary":{
      "A":{
         "${"$"}numberDecimal":"0.0"
      }
   },
   "realmInstantRealmDictionary":{
      "A":{
         "${"$"}date":{
            "${"$"}numberLong":"-9223372036854775808"
         }
      }
   },
   "objectIdRealmDictionary":{
      "key":{
         "${"$"}oid":"000102030405060708090a0b"
      }
   },
   "realmUUIDRealmDictionary":{
      "key":{
         "${"$"}binary":{
            "base64":"AAECAwQFBgcICQoLDA0ODw==",
            "subType":"04"
         }
      }
   },
   "binaryRealmDictionary":{
      "A":{
         "${"$"}binary":{
            "base64":"Kg==",
            "subType":"00"
         }
      }
   },
   "nullableObjectRealmDictionary":{
      
   },
   "nullableRealmAnyRealmDictionary":{
      "A":{
         "${"$"}numberLong":"42"
      }
   }
}
""".trimIndent()
