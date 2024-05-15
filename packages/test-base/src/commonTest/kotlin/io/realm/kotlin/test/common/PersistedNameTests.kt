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

package io.realm.kotlin.test.common

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.dynamic.getValue
import io.realm.kotlin.ext.backlinks
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.internal.asDynamicRealm
import io.realm.kotlin.migration.AutomaticSchemaMigration
import io.realm.kotlin.query.max
import io.realm.kotlin.query.min
import io.realm.kotlin.query.sum
import io.realm.kotlin.schema.RealmStorageType
import io.realm.kotlin.test.common.utils.assertFailsWithMessage
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.use
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
    // Aggregators
    // --------------------------------------------------

    @Test
    fun aggregators_byPublicName() {
        realm.writeBlocking {
            copyToRealm(PersistedNameSample())
        }

        assertEquals(
            expected = 10,
            actual = realm.query<PersistedNameSample>().sum<Int>("publicNameIntField").find()
        )

        assertEquals(
            expected = 10,
            actual = realm.query<PersistedNameSample>().max<Int>("publicNameIntField").find()
        )

        assertEquals(
            expected = 10,
            actual = realm.query<PersistedNameSample>().min<Int>("publicNameIntField").find()
        )
    }

    @Test
    fun aggregators_byPersistedName() {
        realm.writeBlocking {
            copyToRealm(PersistedNameSample())
        }

        assertEquals(
            expected = 10,
            actual = realm.query<PersistedNameSample>().sum<Int>("persistedNameIntField").find()
        )

        assertEquals(
            expected = 10,
            actual = realm.query<PersistedNameSample>().max<Int>("persistedNameIntField").find()
        )

        assertEquals(
            expected = 10,
            actual = realm.query<PersistedNameSample>().min<Int>("persistedNameIntField").find()
        )
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

        val dynamicSample = realm.asDynamicRealm().query("AlternativePersistedNameSample")
            .find()
            .single()

        assertNotNull(dynamicSample)
        assertEquals("Realm", dynamicSample.getValue("persistedNameStringField"))
        // We can access property via the public name because the dynamic Realm is build upon a typed
        // Realm via the extension function `asDynamicRealm`.
        assertEquals("Realm", dynamicSample.getValue("publicNameStringField"))
    }

    // --------------------------------------------------
    // Backlinks
    // --------------------------------------------------

    @Test
    fun backlinks_canPointToPersistedName() {
        val config = RealmConfiguration
            .Builder(schema = setOf(PersistedNameParentSample::class, PersistedNameChildSample::class))
            .name("backlinks.realm")
            .directory("$tmpDir/foo")
            .build()
        Realm.open(config).use { realm ->
            realm.writeBlocking {
                // Add a child with 5 parents
                val child = copyToRealm(PersistedNameChildSample())
                val parents = Array(5) {
                    this.copyToRealm(PersistedNameParentSample(it))
                }
                assertEquals(0, child.publicNameParents.size)
                parents.forEach { parent ->
                    parent.publicNameChildField = child
                }
            }

            val queriedChild = realm.query<PersistedNameChildSample>()
                .find()
                .single()

            assertEquals(5, queriedChild.publicNameParents.size)
            assertEquals(1, queriedChild.publicNameParents.query("id = 3").find().size)
        }
    }

    @Test
    fun backlinks_canBeQueriedWithPersistedName() {
        val config = RealmConfiguration
            .Builder(schema = setOf(PersistedNameParentSample::class, PersistedNameChildSample::class))
            .name("backlinks.realm")
            .directory("$tmpDir/foo")
            .build()
        Realm.open(config).use { realm ->
            realm.writeBlocking {
                // Add a child with 5 parents
                val childA = copyToRealm(PersistedNameChildSample())
                val childB = copyToRealm(PersistedNameChildSample())

                val parentsA = Array(5) {
                    this.copyToRealm(PersistedNameParentSample(it))
                }
                val parentsB = Array(5) {
                    this.copyToRealm(PersistedNameParentSample(5))
                }
                parentsA.forEach { parent ->
                    parent.publicNameChildField = childA
                }
                parentsB.forEach { parent ->
                    parent.publicNameChildField = childB
                }
            }
            assertEquals(1, realm.query<PersistedNameChildSample>("ANY persistedNameParents.id < 3").find().size)
            assertEquals(1, realm.query<PersistedNameChildSample>("ALL persistedNameParents.id == 5").find().size)
            assertEquals(2, realm.query<PersistedNameChildSample>("ALL persistedNameParents.id < 10").find().size)
        }
    }

    // --------------------------------------------------
    // Schema & Migration
    // --------------------------------------------------

    @Test
    fun schema_propertyUsesPersistedName() {
        val realmClass = realm.schema()["AlternativePersistedNameSample"]!!

        assertNotNull(realmClass["persistedNameStringField"])
        assertEquals(RealmStorageType.STRING, realmClass["persistedNameStringField"]!!.type.storageType)
        assertNull(realmClass["publicNameStringField"])
    }

    @Test
    fun dynamicRealmSchema_propertyUsesPersistedName() {
        val realmClass = realm.asDynamicRealm().schema()["AlternativePersistedNameSample"]!!

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

    @Test
    fun backlinkQueryOnPersistedClassName() {
        val config = RealmConfiguration
            .Builder(schema = setOf(RealmParent::class, RealmChild::class))
            .directory(tmpDir)
            .name("persistedClassName.realm")
            .build()
        Realm.open(config).use { realm ->
            realm.writeBlocking {
                repeat(5) { no: Int ->
                    copyToRealm(
                        RealmParent().apply {
                            this.id = no
                            this.child = RealmChild(no)
                        }
                    )
                }
            }

            assertEquals(5, realm.query<RealmParent>().count().find())
            val result = realm.query<RealmChild>("@links.PersistedParent.child.id == $0", 1).find()
            assertEquals(1, result.size)
            val child: RealmChild = result.first()
            assertEquals(1, child.parents.size)
            assertEquals(1, child.parents.first().id)
        }
    }

    @Test
    fun persistedName_on_objectLink() {
        val config = RealmConfiguration
            .Builder(schema = setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .name("objectLinks.realm")
            .build()

        Realm.open(config).use { realm ->
            realm.writeBlocking {
                copyToRealm(
                    Parent().apply {
                        this.child = Child().apply { name = "child1" }
                        this.children.add(
                            Child().apply {
                                name = "first-child"
                                children.add(
                                    Child().apply { name = "first-grand-child" }
                                )
                            }
                        )
                    }
                )
            }

            assertEquals(1, realm.query<Parent>().count().find())
            assertEquals(3, realm.query<Child>().count().find())

            val parent = realm.query<Parent>().first().find()!!
            assertEquals("child1", parent.child!!.name)
            val child2 = parent.children.first()
            assertEquals("first-child", child2.name)
            assertEquals("first-grand-child", child2.children.first().name)
        }
    }

    @Test
    fun schemaWithOverlappingClassNamesThrow() {
        assertFailsWithMessage<IllegalArgumentException>("The schema has declared the following class names multiple times: PersistedParent") {
            RealmConfiguration.create(schema = setOf(RealmParent::class, PersistedParent::class, RealmChild::class))
        }

        // Clash between model name and @PersistedName
        assertFailsWithMessage<IllegalArgumentException>("The schema has declared the following class names multiple times: PersistedParent") {
            RealmConfiguration.create(schema = setOf(PersistedParent::class, RealmParent::class, RealmChild::class))
        }

        // Clash between two @PersistedName annotations
        assertFailsWithMessage<IllegalArgumentException>("The schema has declared the following class names multiple times: PersistedParent") {
            RealmConfiguration.create(schema = setOf(RealmParent::class, RealmParent2::class, RealmChild::class))
        }
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

@PersistedName("AlternativePersistedNameSample")
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

    @PersistedName("sameName")
    var sameName = "Realm"

    // Ensure that we test that we support multiple fields that have their public name "cleared" in
    // the underlying schema due to being equal to the persisted name.
    @PersistedName("sameName2")
    var sameName2 = "Realm"

    @PersistedName("persistedNameIntField")
    var publicNameIntField: Int = 10
}

class PersistedNameParentSample(var id: Int) : RealmObject {
    constructor() : this(0)

    @PersistedName("persistedNameChildField")
    var publicNameChildField: PersistedNameChildSample? = null
}

class PersistedNameChildSample : RealmObject {
    @PersistedName("persistedNameParents")
    val publicNameParents by backlinks(PersistedNameParentSample::publicNameChildField)
}

@PersistedName("PersistedParent")
class RealmParent(var id: Int) : RealmObject {
    constructor() : this(0)
    var child: RealmChild? = null
}

@PersistedName("PersistedParent")
class RealmParent2(var id: Int) : RealmObject {
    constructor() : this(0)
    var child: RealmChild? = null
}

// Should conflict with RealmParent if included in the same Realm.
class PersistedParent(var id: Int) : RealmObject {
    constructor() : this(0)
    var child: RealmChild? = null
}

class RealmChild(var id: Int) : RealmObject {
    constructor() : this(0)
    val parents by backlinks(RealmParent::child)
}

class Parent : RealmObject {
    var name = "parent"
    var child: Child? = null

    @PersistedName("renamedChildren")
    var children: RealmList<Child> = realmListOf()
}

@PersistedName(name = "RenamedChild")
class Child : RealmObject {
    var name = "child"
    @PersistedName("renamedChildren")
    var children: RealmList<Child> = realmListOf()
}
