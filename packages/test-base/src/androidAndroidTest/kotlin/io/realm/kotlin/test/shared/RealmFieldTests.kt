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
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.query.find
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import io.realm.kotlin.types.annotations.PrimaryKey
import io.realm.kotlin.types.annotations.RealmField
import org.mongodb.kbson.BsonObjectId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private val bsonObjectId = BsonObjectId("507f191e810c19729de860ea")
private val objectId = ObjectId.from("507f191e810c19729de860ea")

class RealmFieldTests {

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration
            .Builder(schema = setOf(RealmFieldSample::class, RealmFieldPrimaryKeySample::class))
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

    // --------------------------------------------------
    // Query
    // --------------------------------------------------

    @Test
    fun query_byInternalName() {
        realm.writeBlocking {
            copyToRealm(RealmFieldSample())
        }

        realm.query<RealmFieldSample>("internalNameStringField = $0", "Realm")
            .first()
            .find { first ->
                assertNotNull(first)
                assertEquals("Realm", first.publicNameStringField)
            }

        realm.query<RealmFieldSample>("internalNameObjectIdField = $0", objectId)
            .first()
            .find { first ->
                assertNotNull(first)
                assertEquals(objectId, first.publicNameObjectIdField)
            }

        realm.query<RealmFieldSample>("internalNameBsonObjectIdField = $0", bsonObjectId)
            .first()
            .find { first ->
                assertNotNull(first)
                assertEquals(bsonObjectId, first.publicNameBsonObjectIdField)
            }
    }

    @Test
    fun query_byPublicName() {
        realm.writeBlocking {
            copyToRealm(RealmFieldSample())
        }

        realm.query<RealmFieldSample>("publicNameStringField = $0", "Realm")
            .first()
            .find { first ->
                assertNotNull(first)
                assertEquals("Realm", first.publicNameStringField)
            }

        realm.query<RealmFieldSample>("publicNameObjectIdField = $0", objectId)
            .first()
            .find { first ->
                assertNotNull(first)
                assertEquals(objectId, first.publicNameObjectIdField)
            }

        realm.query<RealmFieldSample>("publicNameBsonObjectIdField = $0", bsonObjectId)
            .first()
            .find { first ->
                assertNotNull(first)
                assertEquals(bsonObjectId, first.publicNameBsonObjectIdField)
            }
    }

    @Test
    fun query_primaryKey_byInternalName() {
        realm.writeBlocking {
            copyToRealm(RealmFieldPrimaryKeySample())
        }

        realm.query<RealmFieldPrimaryKeySample>("internalNamePrimaryKey = $0", bsonObjectId)
            .first()
            .find { first ->
                assertNotNull(first)
                assertEquals(bsonObjectId, first.publicNamePrimaryKey)
            }
    }

    @Test
    fun query_primaryKey_byPublicName() {
        realm.writeBlocking {
            copyToRealm(RealmFieldPrimaryKeySample())
        }

        realm.query<RealmFieldPrimaryKeySample>("publicNamePrimaryKey = $0", bsonObjectId)
            .first()
            .find { first ->
                assertNotNull(first)
                assertEquals(bsonObjectId, first.publicNamePrimaryKey)
            }
    }

    // --------------------------------------------------
    // Schema
    // --------------------------------------------------

    @Test
    fun schema_realmProperty_usesInternalName() {
        val classFromSchema = realm.schema()[RealmFieldSample::class.simpleName!!]!!

        var realmFieldAnnotatedProperty = classFromSchema["internalNameStringField"]
        assertNotNull(realmFieldAnnotatedProperty)

        realmFieldAnnotatedProperty = classFromSchema["publicNameStringField"]
        assertNull(realmFieldAnnotatedProperty)
    }
}

private class RealmFieldSample() : RealmObject {

    @RealmField("internalNameStringField")
    var publicNameStringField: String = "Realm"

    @RealmField("internalNameTimestampField")
    var publicNameTimestampField: RealmInstant = RealmInstant.from(100, 1000)

    @RealmField("internalNameObjectIdField")
    var publicNameObjectIdField: ObjectId = objectId

    @RealmField("internalNameBsonObjectIdField")
    var publicNameBsonObjectIdField: BsonObjectId = bsonObjectId

    @RealmField("internalNameUuidField")
    var publicNameUuidField: RealmUUID = RealmUUID.from("46423f1b-ce3e-4a7e-812f-004cf9c42d76")

    @RealmField("internalNameBinaryField")
    var publicNameBinaryField: ByteArray = byteArrayOf(42)

    @RealmField("internalNameStringListField")
    var publicNameStringListField: RealmList<String> = realmListOf()

    @RealmField("internalNameStringSetField")
    var publicNameStringSetField: RealmSet<String> = realmSetOf()

    @RealmField("internalNameChildField")
    var publicNameChild: RealmFieldSample? = null
}

private class RealmFieldPrimaryKeySample() : RealmObject {
    @RealmField("internalNamePrimaryKey")
    @PrimaryKey
    var publicNamePrimaryKey: BsonObjectId = bsonObjectId
}
