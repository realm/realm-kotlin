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
import io.realm.kotlin.migration.AutomaticSchemaMigration
import io.realm.kotlin.query.find
import io.realm.kotlin.schema.RealmStorageType
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import io.realm.kotlin.types.annotations.PersistedName
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.BsonObjectId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

private val bsonObjectId = BsonObjectId("507f191e810c19729de860ea")
private val objectId = ObjectId.from("507f191e810c19729de860ea")

class PersistedNameTests {

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration
            .Builder(schema = setOf(PersistedNameSample::class))
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
    fun query_byPersistedName() {
        realm.writeBlocking {
            copyToRealm(PersistedNameSample())
        }

        realm.query<PersistedNameSample>("persistedNameStringField = $0", "Realm")
            .first()
            .find { first ->
                assertNotNull(first)
                assertEquals("Realm", first.publicNameStringField)
            }

        realm.query<PersistedNameSample>("persistedNameObjectIdField = $0", objectId)
            .first()
            .find { first ->
                assertNotNull(first)
                assertEquals(objectId, first.publicNameObjectIdField)
            }

        realm.query<PersistedNameSample>("persistedNameBsonObjectIdField = $0", bsonObjectId)
            .first()
            .find { first ->
                assertNotNull(first)
                assertEquals(bsonObjectId, first.publicNameBsonObjectIdField)
            }
    }

    @Test
    fun query_byPublicName() {
        realm.writeBlocking {
            copyToRealm(PersistedNameSample())
        }

        realm.query<PersistedNameSample>("publicNameStringField = $0", "Realm")
            .first()
            .find { first ->
                assertNotNull(first)
                assertEquals("Realm", first.publicNameStringField)
            }

        realm.query<PersistedNameSample>("publicNameObjectIdField = $0", objectId)
            .first()
            .find { first ->
                assertNotNull(first)
                assertEquals(objectId, first.publicNameObjectIdField)
            }

        realm.query<PersistedNameSample>("publicNameBsonObjectIdField = $0", bsonObjectId)
            .first()
            .find { first ->
                assertNotNull(first)
                assertEquals(bsonObjectId, first.publicNameBsonObjectIdField)
            }
    }

    @Test
    fun query_primaryKey_byPersistedName() {
        realm.writeBlocking {
            copyToRealm(PersistedNameSample())
        }

        realm.query<PersistedNameSample>("persistedNamePrimaryKey = $0", bsonObjectId)
            .first()
            .find { first ->
                assertNotNull(first)
                assertEquals(bsonObjectId, first.publicNamePrimaryKey)
            }
    }

    @Test
    fun query_primaryKey_byPublicName() {
        realm.writeBlocking {
            copyToRealm(PersistedNameSample())
        }

        realm.query<PersistedNameSample>("publicNamePrimaryKey = $0", bsonObjectId)
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
    fun schema_realmProperty_usesPersistedName() {
        val classFromSchema = realm.schema()[PersistedNameSample::class.simpleName!!]!!

        var persistedNameAnnotatedProperty = classFromSchema["persistedNameStringField"]
        assertNotNull(persistedNameAnnotatedProperty)
        assertEquals(RealmStorageType.STRING, persistedNameAnnotatedProperty.type.storageType)

        persistedNameAnnotatedProperty = classFromSchema["publicNameStringField"]
        assertNull(persistedNameAnnotatedProperty)
    }

    @Test
    fun schema_changingPersistedName_triggersMigration() {
        val oldSchema = setOf(io.realm.kotlin.entities.migration.before.PersistedNameChangeMigrationSample::class)
        val newSchema = setOf(io.realm.kotlin.entities.migration.after.PersistedNameChangeMigrationSample::class)

        // Open a realm with the old schema
        val oldConfig = RealmConfiguration
            .Builder(schema = oldSchema)
            .name("foo")
            .directory("$tmpDir/bar")
            .build()
        var oldRealm = Realm.open(oldConfig)

        // Add an object to the realm and close it
        oldRealm.writeBlocking {
            copyToRealm(io.realm.kotlin.entities.migration.before.PersistedNameChangeMigrationSample())
        }
        oldRealm.close()

        // Open a realm with the new schema
        var migrationTriggered = false
        val newConfig = RealmConfiguration
            .Builder(schema = newSchema)
            .name("foo")
            .directory("$tmpDir/bar")
            .schemaVersion(1)
            .migration(
                AutomaticSchemaMigration { context ->
                    val realmClass = context.newRealm.schema()["PersistedNameChangeMigrationSample"]!!
                    assertNotNull(realmClass["newPersistedName"])
                    assertNull(realmClass["oldPersistedName"])
                    migrationTriggered = true
                }
            )
            .build()
        Realm.open(newConfig).close()

        // Migration should have been triggered
        assertTrue { migrationTriggered }
    }

    @Test
    fun schema_changingPublicName_doesNotTriggerMigration() {
        val oldSchema = setOf(io.realm.kotlin.entities.migration.before.PublicNameChangeMigrationSample::class)
        val newSchema = setOf(io.realm.kotlin.entities.migration.after.PublicNameChangeMigrationSample::class)

        // Open a realm with the old schema
        val oldConfig = RealmConfiguration
            .Builder(schema = oldSchema)
            .name("foo")
            .directory("$tmpDir/bar")
            .build()
        var oldRealm = Realm.open(oldConfig)

        // Add an object to the realm and close it
        oldRealm.writeBlocking {
            copyToRealm(io.realm.kotlin.entities.migration.before.PublicNameChangeMigrationSample())
        }
        oldRealm.close()

        // Open a realm with the new schema
        val newConfig = RealmConfiguration
            .Builder(schema = newSchema)
            .name("foo")
            .directory("$tmpDir/bar")
            .migration(
                AutomaticSchemaMigration {
                    fail("Migration triggered")
                }
            )
            .build()

        // Migration should not be needed
        Realm.open(newConfig).close()
    }
}

class PersistedNameSample : RealmObject {

    @PersistedName("persistedNamePrimaryKey")
    @PrimaryKey
    var publicNamePrimaryKey: BsonObjectId = bsonObjectId

    @PersistedName("persistedNameStringField")
    var publicNameStringField: String = "Realm"

    @PersistedName("persistedNameTimestampField")
    var publicNameTimestampField: RealmInstant = RealmInstant.from(100, 1000)

    @PersistedName("persistedNameObjectIdField")
    var publicNameObjectIdField: ObjectId = objectId

    @PersistedName("persistedNameBsonObjectIdField")
    var publicNameBsonObjectIdField: BsonObjectId = bsonObjectId

    @PersistedName("persistedNameUuidField")
    var publicNameUuidField: RealmUUID = RealmUUID.from("46423f1b-ce3e-4a7e-812f-004cf9c42d76")

    @PersistedName("persistedNameBinaryField")
    var publicNameBinaryField: ByteArray = byteArrayOf(42)

    @PersistedName("persistedNameStringListField")
    var publicNameStringListField: RealmList<String> = realmListOf()

    @PersistedName("persistedNameStringSetField")
    var publicNameStringSetField: RealmSet<String> = realmSetOf()

    @PersistedName("persistedNameChildField")
    var publicNameChild: PersistedNameSample? = null
}
