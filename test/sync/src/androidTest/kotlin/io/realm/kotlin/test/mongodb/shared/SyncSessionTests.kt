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
package io.realm.kotlin.test.mongodb.shared

import io.ktor.client.features.ClientRequestException
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.entities.sync.BinaryObject
import io.realm.kotlin.entities.sync.ChildPk
import io.realm.kotlin.entities.sync.ObjectIdPk
import io.realm.kotlin.entities.sync.ParentPk
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.exceptions.ClientResetRequiredException
import io.realm.kotlin.mongodb.exceptions.SyncException
import io.realm.kotlin.mongodb.sync.DiscardUnsyncedChangesStrategy
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.sync.SyncSession
import io.realm.kotlin.mongodb.syncSession
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.asTestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.test.util.use
import io.realm.kotlin.types.ObjectId
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class SyncSessionTests {

    private lateinit var partitionValue: String
    private lateinit var user: User
    private lateinit var app: TestApp

    @BeforeTest
    fun setup() {
        partitionValue = TestHelper.randomPartitionValue()
        app = TestApp()
        val (email, password) = TestHelper.randomEmail() to "password1234"
        user = runBlocking {
            app.createUserAndLogIn(email, password)
        }
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun session() {
        val config = createSyncConfig(user)
        Realm.open(config).use { realm: Realm ->
            val session: SyncSession = realm.syncSession
            assertNotNull(session)
        }
    }

    @Test
    fun sessionPauseAndResume() {
        val config = createSyncConfig(user)
        Realm.open(config).use { realm: Realm ->
            runBlocking {
                // default state should be active
                assertEquals(SyncSession.State.ACTIVE, realm.syncSession.state)

                // pausing the session sets it in Inactive state
                realm.syncSession.pause()
                assertEquals(SyncSession.State.INACTIVE, realm.syncSession.state)

                // resuming the session sets it in Active state
                realm.syncSession.resume()
                assertEquals(SyncSession.State.ACTIVE, realm.syncSession.state)
            }
        }
    }

    @Test
    fun sessionResumeMultipleTimes() {
        val config = createSyncConfig(user)
        Realm.open(config).use { realm: Realm ->
            runBlocking {
                // default state should be active
                assertEquals(SyncSession.State.ACTIVE, realm.syncSession.state)

                // resuming an active session should do nothing
                realm.syncSession.resume()
                assertEquals(SyncSession.State.ACTIVE, realm.syncSession.state)

                // resuming an active session should do nothing
                realm.syncSession.resume()
                assertEquals(SyncSession.State.ACTIVE, realm.syncSession.state)
            }
        }
    }

    @Test
    fun sessionPauseMultipleTimes() {
        val config = createSyncConfig(user)
        Realm.open(config).use { realm: Realm ->
            runBlocking {
                // default state should be active
                assertEquals(SyncSession.State.ACTIVE, realm.syncSession.state)

                // resuming an active session should do nothing
                realm.syncSession.pause()
                assertEquals(SyncSession.State.INACTIVE, realm.syncSession.state)

                // resuming an active session should do nothing
                realm.syncSession.pause()
                assertEquals(SyncSession.State.INACTIVE, realm.syncSession.state)
            }
        }
    }

    // The same object is returned for each call to `Realm.session`
    @Test
    fun session_identity() {
        val config = createSyncConfig(user)
        Realm.open(config).use { realm: Realm ->
            val session1: SyncSession = realm.syncSession
            val session2: SyncSession = realm.syncSession
            assertSame(session1, session2)
        }
    }

    // If multiple instances of the same Realm is opened. The Kotlin SyncSession objects will
    // differ, but they point to the same underlying Core Sync Session.
    @Test
    @Suppress("invisible_reference", "invisible_member")
    fun session_sharedStateBetweenRealms() {
        val config1 = createSyncConfig(user, "realm1.realm")
        val config2 = createSyncConfig(user, "realm2.realm")
        val realm1 = Realm.open(config1)
        val realm2 = Realm.open(config2)
        assertNotEquals(realm1.configuration.path, realm2.configuration.path)
        assertNotSame(realm1.syncSession, realm2.syncSession)
        RealmInterop.realm_equals(
            (realm1.syncSession as io.realm.kotlin.mongodb.internal.SyncSessionImpl).nativePointer,
            (realm2.syncSession as io.realm.kotlin.mongodb.internal.SyncSessionImpl).nativePointer
        )
    }

    @Test
    fun session_localRealmThrows() {
        val config = RealmConfiguration.Builder(schema = setOf(ParentPk::class, ChildPk::class))
            .build()
        Realm.open(config).use { realm ->
            assertFailsWith<IllegalStateException> { realm.syncSession }
        }
    }

    @Test
    fun downloadAllServerChanges_illegalArgumentThrows() {
        openSyncRealm { realm ->
            val session: SyncSession = realm.syncSession
            assertFailsWith<IllegalArgumentException> {
                session.downloadAllServerChanges(0.seconds)
            }
            assertFailsWith<IllegalArgumentException> {
                session.downloadAllServerChanges((-1).seconds)
            }
        }
    }

    @Test
    fun downloadAllServerChanges_returnFalseOnTimeOut() {
        openSyncRealmWithPreconditions({ realm ->
            // Write a large ByteArray so that we increase the chance the timeout works
            realm.writeBlocking {
                val obj = BinaryObject()
                copyToRealm(obj)
            }
        }) { realm ->
            val session = realm.syncSession
            assertFalse(session.downloadAllServerChanges(timeout = 1.nanoseconds))
        }
    }

    @Test
    fun uploadAllLocalChanges_illegalArgumentThrows() {
        openSyncRealm { realm ->
            val session: SyncSession = realm.syncSession
            assertFailsWith<IllegalArgumentException> {
                session.uploadAllLocalChanges(0.seconds)
            }
            assertFailsWith<IllegalArgumentException> {
                session.uploadAllLocalChanges((-1).seconds)
            }
        }
    }

    @Test
    fun uploadAllLocalChanges_returnFalseOnTimeOut() {
        openSyncRealmWithPreconditions({ realm ->
            // Write a large ByteArray so that we increase the chance the timeout works
            realm.writeBlocking {
                val obj = BinaryObject()
                copyToRealm(obj)
            }
        }) { realm ->
            val session = realm.syncSession
            assertFalse(session.uploadAllLocalChanges(timeout = 1.nanoseconds))
        }
    }

    @Test
    @Ignore // See https://github.com/realm/realm-kotlin/issues/872
    fun uploadAndDownloadChangesSuccessfully() = runBlocking {
        val user1 = app.createUserAndLogIn(TestHelper.randomEmail(), "123456")
        val user2 = app.createUserAndLogIn(TestHelper.randomEmail(), "123456")

        val config1 = SyncConfiguration.Builder(
            user1,
            partitionValue,
            schema = setOf(ParentPk::class, ChildPk::class)
        ).name("user1.realm")
            .build()
        val config2 = SyncConfiguration.Builder(
            user2,
            partitionValue,
            schema = setOf(ParentPk::class, ChildPk::class)
        ).name("user2.realm")
            .build()

        val realm1 = Realm.open(config1)
        val realm2 = Realm.open(config2)

        try {
            realm1.write {
                for (i in 0 until 10) {
                    copyToRealm(
                        ParentPk().apply {
                            _id = i.toString()
                        }
                    )
                }
            }
            assertEquals(10, realm1.query<ParentPk>().count().find())
            assertEquals(0, realm2.query<ParentPk>().count().find())
            assertTrue(realm1.syncSession.uploadAllLocalChanges())

            // Due to the Server Translator, there is a small delay between data
            // being uploaded and it not being immediately ready for download
            // on another Realm. In order to reduce the flakyness, we are
            // re-evaluating the assertion multiple times.
            for (i in 1200 downTo 0) { // Wait for max 2 minutes.
                assertTrue(realm2.syncSession.downloadAllServerChanges())
                when (val size = realm2.query<ParentPk>().count().find()) {
                    10L -> break // Test succeeded
                    0L -> {
                        // Race condition: Server has not yet propagated data to user 2.
                        if (i == 0) {
                            throw kotlin.AssertionError("Realm failed to receive download data. Size: $size")
                        }
                        delay(100)
                    }
                    else -> {
                        // The server might not send all data at once, so just print intermediate
                        // steps here for debugging purposes
                        println("Received size: $size")
                    }
                }
            }
        } finally {
            realm1.close()
            realm2.close()
        }
    }

    // SyncSessions available inside a syncClientResetStrategy are disconnected from the underlying
    // Realm instance and some APIs could have a difficult time understanding semantics. For now, we
    // just disallow calling these APIs from these instances.
    @Test
    @Ignore // TODO remove ignore when the this is fixed https://github.com/realm/realm-kotlin/issues/867
    fun syncSessionFromErrorHandlerCannotUploadAndDownloadChanges() = runBlocking {
        val channel = Channel<SyncSession>(1)
        var wrongSchemaRealm: Realm? = null
        val job = async {

            // Create server side Realm with one schema
            var config = SyncConfiguration.Builder(
                user,
                partitionValue,
                schema = setOf(ParentPk::class, ChildPk::class)
            ).build()
            val realm = Realm.open(config)
            realm.syncSession.uploadAllLocalChanges()
            realm.close()

            // Create same Realm with another schema, which will cause a Client Reset.
            config = SyncConfiguration.Builder(
                user,
                partitionValue,
                schema = setOf(ParentPk::class, io.realm.kotlin.entities.sync.bogus.ChildPk::class)
            ).name("new_realm.realm")
                .syncClientResetStrategy(object : DiscardUnsyncedChangesStrategy {
                    @Suppress("TooGenericExceptionThrown")
                    override fun onBeforeReset(realm: TypedRealm) {
                        // TODO This approach doesn't work on native until these callbacks return a
                        //  boolean - the exceptions make the thread crash on Kotlin Native
                        throw Exception("Land on onError")
                    }

                    override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                        // Writing "fail("whatever") makes no sense here because Core will redirect
                        // the execution to onError below
                    }

                    override fun onError(
                        session: SyncSession,
                        exception: ClientResetRequiredException
                    ) {
                        channel.trySend(session)
                    }
                }).build()
            wrongSchemaRealm = Realm.open(config)
        }
        val session = channel.receive()
        try {
            assertFailsWith<IllegalStateException> {
                session.uploadAllLocalChanges()
            }.also {
                assertTrue(it.message!!.contains("Uploading and downloading changes is not allowed"))
            }
            assertFailsWith<IllegalStateException> {
                session.downloadAllServerChanges()
            }.also {
                assertTrue(it.message!!.contains("Uploading and downloading changes is not allowed"))
            }
        } finally {
            wrongSchemaRealm?.close()
            job.cancel()
            channel.close()
        }
        Unit
    }

    @Test
    fun uploadDownload_throwsUnderlyingSyncError() {
        openSyncRealm { realm ->
            val session = realm.syncSession
            app.pauseSync()
            assertFailsWith<SyncException> {
                session.uploadAllLocalChanges()
            }.also {
                assertTrue(it.message!!.contains("End of input", ignoreCase = true), it.message)
            }
            assertFailsWith<SyncException> {
                session.downloadAllServerChanges()
            }.also {
                assertTrue(it.message!!.contains("End of input", ignoreCase = true), it.message)
            }
            app.startSync()
        }
    }

    /**
     * - Insert a document in MongoDB using the command server
     * - Fetch the object id of the newly inserted document
     * - Open a Realm with the same partition key as the inserted document
     * - Wait for Sync to fetch the document as a valid RealmObject with the matching ObjectId as a PK
     */
    @Test
    fun syncingObjectIdFromMongoDB() {
        val adminApi = app.asTestApp
        runBlocking {
            val config =
                SyncConfiguration.Builder(user, partitionValue, schema = setOf(ObjectIdPk::class))
                    .build()
            val realm = Realm.open(config)

            val json: JsonObject = adminApi.insertDocument(
                ObjectIdPk::class.simpleName!!,
                """
                    {
                        "name": "$partitionValue",
                        "realm_id" : "$partitionValue"
                    }
                """.trimIndent()
            )!!
            val oid = json["insertedId"]!!.jsonObject["${'$'}oid"]!!.jsonPrimitive.content
            assertNotNull(oid)

            val channel = Channel<ObjectIdPk>(1)
            val job = async {
                realm.query<ObjectIdPk>("_id = $0", ObjectId.from(oid)).first()
                    .asFlow().collect {
                        if (it.obj != null) {
                            channel.trySend(it.obj!!)
                        }
                    }
            }

            val insertedObject = channel.receive()
            assertEquals(oid, insertedObject._id.toString())
            assertEquals(partitionValue, insertedObject.name)
            realm.close()
            job.cancel()
        }
    }

    /**
     * - Insert a RealmObject and sync it
     * - Query the MongoDB database to make sure the document was inserted with the correct ObjectID
     */
    @Test
    fun syncingObjectIdFromRealm() {
        val adminApi = app.asTestApp
        val objectId = ObjectId.create()
        val oid = objectId.toString()

        runBlocking {
            val job = async {
                val config = SyncConfiguration.Builder(
                    user,
                    partitionValue,
                    schema = setOf(ObjectIdPk::class)
                )
                    .build()
                val realm = Realm.open(config)

                val objWithPK = ObjectIdPk().apply {
                    name = partitionValue
                    _id = objectId
                }

                realm.write {
                    copyToRealm(objWithPK)
                }

                realm.syncSession.uploadAllLocalChanges()
                realm.close()
            }

            var oidAsString: String? = null
            var attempts = 150 // waiting a max of 30s
            do {
                delay(200) // let Sync integrate the changes
                @Suppress("EmptyCatchBlock") // retrying
                try {
                    val syncedDocumentJson =
                        adminApi.queryDocument(
                            clazz = ObjectIdPk::class.simpleName!!,
                            query = """{"_id":{"${'$'}oid":"$oid"}}"""
                        )
                    oidAsString =
                        syncedDocumentJson?.get("_id")?.jsonObject?.get("${'$'}oid")?.jsonPrimitive?.content
                } catch (e: ClientRequestException) {
                }
            } while (oidAsString == null && attempts-- > 0)

            assertEquals(oid, oidAsString)
            job.cancel()
        }
    }

    private fun openSyncRealmWithPreconditions(
        preconditions: (suspend (Realm) -> Unit)? = null,
        block: suspend (Realm) -> Unit
    ) {
        val config = SyncConfiguration.Builder(
            user,
            partitionValue,
            schema = setOf(ParentPk::class, ChildPk::class, BinaryObject::class)
        ).build()

        if (preconditions != null) {
            Realm.open(config).use { realm ->
                runBlocking {
                    preconditions(realm)
                }
            }
        }

        Realm.open(config).use { realm ->
            runBlocking {
                block(realm)
            }
        }
    }

    private fun openSyncRealm(block: suspend (Realm) -> Unit) {
        openSyncRealmWithPreconditions(null, block)
    }

    @Suppress("LongParameterList")
    private fun createSyncConfig(
        user: User,
        name: String = DEFAULT_NAME,
    ): SyncConfiguration = SyncConfiguration.Builder(
        schema = setOf(ParentPk::class, ChildPk::class),
        user = user,
        partitionValue = partitionValue
    ).name(name)
        .build()
}
