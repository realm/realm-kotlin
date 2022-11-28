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

@file:Suppress("invisible_member", "invisible_reference")

package io.realm.kotlin.test.shared

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.dynamic.getValue
import io.realm.kotlin.ext.backlinks
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.internal.asDynamicRealm
import io.realm.kotlin.migration.AutomaticSchemaMigration
import io.realm.kotlin.schema.RealmStorageType
import io.realm.kotlin.test.assertFailsWithMessage
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
import kotlin.reflect.KMutableProperty1
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
        if (this::realm.isInitialized && !realm.isClosed()) {
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

        assertCanQuerySingle(
            property = PersistedNameSample::publicNameStringField,
            nameToQueryBy = "persistedNameStringField",
            value = "Realm"
        )

        assertCanQuerySingle(
            property = PersistedNameSample::publicNameObjectIdField,
            nameToQueryBy = "persistedNameObjectIdField",
            value = objectId
        )

        assertCanQuerySingle(
            property = PersistedNameSample::publicNameBsonObjectIdField,
            nameToQueryBy = "persistedNameBsonObjectIdField",
            value = bsonObjectId
        )
    }

    @Test
    fun query_byPublicName() {
        realm.writeBlocking {
            copyToRealm(PersistedNameSample())
        }

        assertCanQuerySingle(
            property = PersistedNameSample::publicNameStringField,
            nameToQueryBy = "publicNameStringField",
            value = "Realm"
        )

        assertCanQuerySingle(
            property = PersistedNameSample::publicNameObjectIdField,
            nameToQueryBy = "publicNameObjectIdField",
            value = objectId
        )

        assertCanQuerySingle(
            property = PersistedNameSample::publicNameBsonObjectIdField,
            nameToQueryBy = "publicNameBsonObjectIdField",
            value = bsonObjectId
        )
    }

    @Test
    fun query_byPrimaryKeyPersistedName() {
        realm.writeBlocking {
            copyToRealm(PersistedNameSample())
        }

        assertCanQuerySingle(
            property = PersistedNameSample::publicNamePrimaryKey,
            nameToQueryBy = "persistedNamePrimaryKey",
            value = bsonObjectId
        )
    }

    @Test
    fun query_byPrimaryKeyPublicName() {
        realm.writeBlocking {
            copyToRealm(PersistedNameSample())
        }

        assertCanQuerySingle(
            property = PersistedNameSample::publicNamePrimaryKey,
            nameToQueryBy = "publicNamePrimaryKey",
            value = bsonObjectId
        )
    }

    @Test
    fun query_byEmojiPersistedName() {
        realm.writeBlocking {
            copyToRealm(PersistedNameSample())
        }

        assertCanQuerySingle(
            property = PersistedNameSample::publicNameWithoutEmoji,
            nameToQueryBy = "persistedNameWithEmoji😊",
            value = "Realm"
        )
    }

    @Test
    fun dynamicRealmQuery_getValueByPersistedName() {
        realm.writeBlocking {
            copyToRealm(PersistedNameSample())
        }

        val dynamicSample = realm.asDynamicRealm().query(PersistedNameSample::class.simpleName!!)
            .find()
            .single()

        assertNotNull(dynamicSample)
        assertEquals("Realm", dynamicSample.getValue("persistedNameStringField"))
        assertFailsWithMessage<IllegalArgumentException>("Schema for type '${PersistedNameSample::class.simpleName!!}' doesn't contain a property named 'publicNameStringField'") {
            dynamicSample.getValue("publicNameStringField")
        }
    }

    // --------------------------------------------------
    // Backlinks
    // --------------------------------------------------

    /*
    FIXME Our backlinks test is failing.
          Notes for fixing support for adding @PersistedName annotation to fields using backlinks:

    ----------------
    Current problem:
    ----------------
    If adding `@PersistedName` annotation to a field using backlinks (see `PersistedNameParentSample`
    and `PersistedNameChildSample` at the end of this file), schema validation will fail when opening
    the realm because it's looking for the property using the public name rather than the persisted one:
    ```
    Caused by: io.realm.kotlin.internal.interop.RealmCoreLogicException: [18]: Schema validation failed due to the following errors:
    - Property 'PersistedNameParentSample.publicNameChildField' declared as origin of linking objects property 'PersistedNameChildSample.parents' does not exist
    ```

    ------------------
    Possible solution:
    ------------------
    In `RealmModelSyntheticPropertiesGeneration.addSchemaMethodBody()`, we do a call to
    `getLinkingObjectPropertyName(backingField)` when adding the link property name:
    ```
    // Link property name
    putValueArgument(
        arg++,
        if (type == linkingObjectType) {
            val targetProperty = getLinkingObjectPropertyName(backingField)
            irString(targetProperty.identifier)
        } else {
            irString("")
        }
    )
    ```
    Thus, `targetProperty.identifier` is the public name used, not the persisted name.

    The function `getLinkingObjectPropertyName` in `IrUtils` should return the persisted name.
    The persisted name can be accessed through `SchemaProperty.persistedName` or on
    `IrField.getAnnotation()` (see `SchemaProperty` for exact details on how to get annotation value).
    */

    @Test
    fun backlinks_canPointToPersistedName() {
        val config = RealmConfiguration
            .Builder(schema = setOf(PersistedNameParentSample::class, PersistedNameChildSample::class))
            .name("backlinks.realm")
            .directory("$tmpDir/foo")
            .build()
        val realm = Realm.open(config)

        realm.writeBlocking {
            // Add a child with two parents
            val child = PersistedNameChildSample()
            copyToRealm(child)
            copyToRealm(PersistedNameParentSample(id = 1).apply { publicNameChildField = child })
            copyToRealm(PersistedNameParentSample(id = 2).apply { publicNameChildField = child })
        }

        val queriedChild = realm.query<PersistedNameChildSample>()
            .find()
            .single()

        assertEquals(2, queriedChild.parents.size)
        assertEquals(1, queriedChild.parents.query("id = 1").find().size)
    }

    // --------------------------------------------------
    // Schema & Migration
    // --------------------------------------------------

    @Test
    fun schema_propertyUsesPersistedName() {
        val realmClass = realm.schema()[PersistedNameSample::class.simpleName!!]!!

        assertNotNull(realmClass["persistedNameStringField"])
        assertEquals(RealmStorageType.STRING, realmClass["persistedNameStringField"]!!.type.storageType)
        assertNull(realmClass["publicNameStringField"])
    }

    @Test
    fun dynamicRealmSchema_propertyUsesPersistedName() {
        val realmClass = realm.asDynamicRealm().schema()[PersistedNameSample::class.simpleName!!]!!

        assertNotNull(realmClass["persistedNameStringField"])
        assertEquals(RealmStorageType.STRING, realmClass["persistedNameStringField"]!!.type.storageType)
        assertNull(realmClass["publicNameStringField"])
    }

    @Test
    fun schema_changingPersistedName_triggersMigration() {
        val oldSchema = setOf(io.realm.kotlin.entities.migration.before.PersistedNameChangeMigrationSample::class)
        val newSchema = setOf(io.realm.kotlin.entities.migration.after.PersistedNameChangeMigrationSample::class)

        // Open a realm with the old schema
        val oldConfig = RealmConfiguration
            .Builder(schema = oldSchema)
            .name("migration.realm")
            .directory("$tmpDir/foo")
            .build()
        val oldRealm = Realm.open(oldConfig)

        // Add an object to the realm and close it
        oldRealm.writeBlocking {
            copyToRealm(io.realm.kotlin.entities.migration.before.PersistedNameChangeMigrationSample())
        }
        oldRealm.close()

        // Open a realm with the new schema
        var migrationTriggered = false
        val newConfig = RealmConfiguration
            .Builder(schema = newSchema)
            .name("migration.realm")
            .directory("$tmpDir/foo")
            .schemaVersion(1)
            .migration(
                AutomaticSchemaMigration {
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
            .name("migration.realm")
            .directory("$tmpDir/foo")
            .build()
        val oldRealm = Realm.open(oldConfig)

        // Add an object to the realm and close it
        oldRealm.writeBlocking {
            copyToRealm(io.realm.kotlin.entities.migration.before.PublicNameChangeMigrationSample())
        }
        oldRealm.close()

        // Open a realm with the new schema
        val newConfig = RealmConfiguration
            .Builder(schema = newSchema)
            .name("migration.realm")
            .directory("$tmpDir/foo")
            .migration(
                AutomaticSchemaMigration {
                    fail("Migration triggered")
                }
            )
            .build()

        // Migration should not be needed
        Realm.open(newConfig).close()
    }

    private fun <T> assertCanQuerySingle(property: KMutableProperty1<PersistedNameSample, T>, nameToQueryBy: String, value: T) {
        realm.query<PersistedNameSample>("$nameToQueryBy = $0", value)
            .find()
            .single()
            .run {
                assertNotNull(this)
                assertEquals(value, property.getValue(this, property))
            }
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

    @PersistedName("persistedNameWithEmoji😊")
    var publicNameWithoutEmoji = "Realm"
}

class PersistedNameParentSample(var id: Int) : RealmObject {
    constructor() : this(0)

    @PersistedName("persistedNameChildField")
    var publicNameChildField: PersistedNameChildSample? = null
}

class PersistedNameChildSample : RealmObject {
    val parents by backlinks(PersistedNameParentSample::publicNameChildField)
}
