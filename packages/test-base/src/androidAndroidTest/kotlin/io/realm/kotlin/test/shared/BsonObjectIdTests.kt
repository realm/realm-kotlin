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
package io.realm.kotlin.test.shared

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.Sort
import io.realm.kotlin.query.find
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.use
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.delay
import org.mongodb.kbson.ObjectId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BsonObjectIdTests {

    private fun RealmInstant.asMillis() = epochSeconds * 1000

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (!realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun boundaries() {
        roundTrip(ObjectId("000000000000000000000000")) { value ->
            assertEquals(ObjectId("000000000000000000000000"), value)
        }

        val min = ObjectId(RealmInstant.MIN.asMillis())
        roundTrip(min) { value ->
            assertEquals(min, value)
        }

        roundTrip(ObjectId("ffffffffffffffffffffffff")) { value ->
            assertEquals(ObjectId("ffffffffffffffffffffffff"), value)
        }

        val max = ObjectId(RealmInstant.MAX.asMillis())
        roundTrip(max) { value ->
            assertEquals(max, value)
        }

        roundTrip(ObjectId("56e1fc72e0c917e9c4714161")) { value ->
            assertEquals(ObjectId("56e1fc72e0c917e9c4714161"), value)
        }

        val fromDate = ObjectId(RealmInstant.from(42, 42).asMillis())
        roundTrip(fromDate) { value ->
            assertEquals(fromDate, value)
        }

        val bytes = byteArrayOf(
            0x56.toByte(),
            0xe1.toByte(),
            0xfc.toByte(),
            0x72.toByte(),
            0xe0.toByte(),
            0xc9.toByte(),
            0x17.toByte(),
            0xe9.toByte(),
            0xc4.toByte(),
            0x71.toByte(),
            0x41.toByte(),
            0x61.toByte()
        )
        roundTrip(ObjectId(bytes)) { value ->
            assertEquals(ObjectId(bytes), value)
        }
    }

    // Store value and retrieve it again
    private fun roundTrip(objectId: ObjectId, function: (ObjectId) -> Unit) {
        // Test managed objects
        realm.writeBlocking {
            copyToRealm(
                Sample().apply {
                    bsonObjectIdField = objectId
                }
            )
            val managedObjectId = query<Sample>()
                .first()
                .find { sampleObject ->
                    assertNotNull(sampleObject)
                    sampleObject.bsonObjectIdField
                }
            function(managedObjectId)
            cancelWrite() // So we can use .first()
        }
    }

    @Test
    fun queries() = runBlocking {
        val objectId1 = ObjectId()
        delay(100)
        val objectId2 = ObjectId()
        delay(100)
        val objectId3 = ObjectId()
        delay(100)

        val config = RealmConfiguration.Builder(setOf(Sample::class))
            .directory(tmpDir)
            .build()
        Realm.open(config).use { realm ->
            val objWithPK1 = Sample().apply {
                stringField = "obj1"
                bsonObjectIdField = objectId1
            }
            val objWithPK2 = Sample().apply {
                stringField = "obj2"
                bsonObjectIdField = objectId2
            }
            val objWithPK3 = Sample().apply {
                stringField = "obj3"
                bsonObjectIdField = objectId3
            }

            realm.writeBlocking {
                copyToRealm(objWithPK1)
                copyToRealm(objWithPK2)
                copyToRealm(objWithPK3)
            }

            val ids: RealmResults<Sample> =
                realm.query<Sample>().sort("objectIdField", Sort.ASCENDING).find()
            assertEquals(3, ids.size)

            assertEquals("obj1", ids[0].stringField)
            assertEquals(objectId1, ids[0].bsonObjectIdField)

            assertEquals("obj2", ids[1].stringField)
            assertEquals(objectId2, ids[1].bsonObjectIdField)

            assertEquals("obj3", ids[2].stringField)
            assertEquals(objectId3, ids[2].bsonObjectIdField)
        }
    }
}
