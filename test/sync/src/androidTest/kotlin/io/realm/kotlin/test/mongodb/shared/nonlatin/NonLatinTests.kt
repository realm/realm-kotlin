package io.realm.kotlin.test.mongodb.shared.nonlatin

import io.realm.kotlin.Realm
import io.realm.kotlin.entities.sync.ObjectIdPk
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.TESTAPP_PARTITION
import io.realm.kotlin.test.mongodb.asTestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.types.ObjectId
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NonLatinTests {

    private lateinit var partitionValue: String
    private lateinit var user: User
    private lateinit var app: TestApp

    @BeforeTest
    fun setup() {
        partitionValue = TestHelper.randomPartitionValue()
        app = TestApp(TESTAPP_PARTITION)
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

    /**
     * - Insert a string with the null character in MongoDB using the command server
     */
    @Test
    fun readNullCharacterFromMongoDB() {
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
                        "name": "foo\u0000bar",
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
            val char1 = "foo\u0000bar".toCharArray()
            val char2 = insertedObject.name.toCharArray()
            assertEquals("foo\u0000bar", insertedObject.name)
            assertContentEquals(char1, char2)
            realm.close()
            job.cancel()
        }
    }
}
