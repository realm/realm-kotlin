package io.realm.kotlin.test.shared.notifications

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.embedded.EmbeddedChild
import io.realm.kotlin.entities.embedded.EmbeddedParent
import io.realm.kotlin.entities.embedded.embeddedSchema
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.query.max
import io.realm.kotlin.query.min
import io.realm.kotlin.query.sum
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.shared.QuerySample
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class RealmListQueryTests {

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(schema = setOf(QuerySample::class) + embeddedSchema)
            .directory(tmpDir)
            .build()
        realm = Realm.open(configuration)
        realm.writeBlocking {
            val querySample = QuerySample().apply {
                intField = 1
            }
            for (i in 0..5) {
                querySample.objectListField.add(QuerySample(intField = i))
                if (i % 2 == 0) {
                    querySample.objectListField[i].nullableIntField = i
                } else {
                    querySample.objectListField[i].nullableIntField = null
                }
            }
            copyToRealm(querySample)
            val parent = EmbeddedParent().apply {
                id = "1"
                children.addAll(setOf(EmbeddedChild("child1"), EmbeddedChild("child2"), EmbeddedChild("child3")))
            }
            copyToRealm(parent)
        }
    }

    @AfterTest
    fun tearDown() {
        if (!realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun realmList_simpleQuery() {
        val querySample = realm.query<QuerySample>("intField == $0", 1).find().first()
        val result = querySample.objectListField.query("TRUEPREDICATE", 1).find()
        assertEquals(6, result.size)
    }

    @Test
    fun realmList_simpleQueryEmbeddedObject() {
        val parent = realm.query<EmbeddedParent>("id == $0", "1").find().first()
        val result = parent.children.query("TRUEPREDICATE").find()
        assertEquals(3, result.size)
    }

    @Test
    fun realmList_queryMax() {
        val querySample = realm.query<QuerySample>("intField == $0", 1).find().first()
        val result = querySample.objectListField.query("TRUEPREDICATE").max<Int>("intField").find()
        assertEquals(5, result)
    }

    @Test
    fun realmList_queryMin() {
        val querySample = realm.query<QuerySample>("intField == $0", 1).find().first()
        val result = querySample.objectListField.query("TRUEPREDICATE").min<Int>("intField").find()
        assertEquals(0, result)
    }

    @Test
    fun realmList_queryCount() {
        val querySample = realm.query<QuerySample>("intField == $0", 1).find().first()
        val result = querySample.objectListField.query("TRUEPREDICATE").count().find()
        assertEquals(6, result)
    }

    @Test
    fun realmList_querySum() {
        val querySample = realm.query<QuerySample>("intField == $0", 1).find().first()
        val result = querySample.objectListField.query("TRUEPREDICATE").sum<Int>("intField").find()
        assertEquals(15, result)
    }

    @Test
    fun realmList_queryMaxEmptyList() {
        val querySample = realm.query<QuerySample>("intField == $0", 1).find().first()
        val result = querySample.objectListField.query("FALSEPREDICATE").max<Int>("intField").find()
        assertEquals(null, result)
    }

    @Test
    fun realmList_queryMinEmptyList() {
        val querySample = realm.query<QuerySample>("intField == $0", 1).find().first()
        val result = querySample.objectListField.query("FALSEPREDICATE").min<Int>("intField").find()
        assertEquals(null, result)
    }

    @Test
    fun realmList_queryCountEmptyList() {
        val querySample = realm.query<QuerySample>("intField == $0", 1).find().first()
        val result = querySample.objectListField.query("FALSEPREDICATE").count().find()
        assertEquals(0, result)
    }

    @Test
    fun realmList_querySumEmptyList() {
        val querySample = realm.query<QuerySample>("intField == $0", 1).find().first()
        val result = querySample.objectListField.query("FALSEPREDICATE").sum<Int>("intField").find()
        assertEquals(0, result)
    }

    @Test
    fun realmList_queryMaxWithNullValues() {
        val querySample = realm.query<QuerySample>("intField == $0", 1).find().first()
        val result = querySample.objectListField.query("TRUEPREDICATE").max<Int>("nullableIntField").find()
        assertEquals(4, result)
    }

    @Test
    fun realmList_queryMinWithNullValues() {
        val querySample = realm.query<QuerySample>("intField == $0", 1).find().first()
        val result = querySample.objectListField.query("TRUEPREDICATE").min<Int>("nullableIntField").find()
        assertEquals(0, result)
    }

    @Test
    fun realmList_querySumWithNullValues() {
        val querySample = realm.query<QuerySample>("intField == $0", 1).find().first()
        val result = querySample.objectListField.query("TRUEPREDICATE").sum<Int>("nullableIntField").find()
        assertEquals(6, result)
    }

    @Test
    fun realmList_flowAddElement() {
        runBlocking {
            val querySample = realm.query<QuerySample>("intField == $0", 1).find().first()
            val channel = Channel<ResultsChange<*>>(capacity = 1)
            val observer = async {
                querySample.objectListField
                    .query("TRUEPREDICATE").asFlow()
                    .collect { flowList ->
                        channel.send(flowList)
                    }
            }
            channel.receive().let { resultsChange ->
                assertIs<ResultsChange<*>>(resultsChange)
                assertNotNull(resultsChange.list)
                assertEquals(6, resultsChange.list.size)
            }
            realm.writeBlocking {
                val queriedSample = findLatest(querySample)
                queriedSample!!.objectListField.add(QuerySample(intField = 1))
            }
            channel.receive().let { resultsChange ->
                assertIs<ResultsChange<*>>(resultsChange)
                assertNotNull(resultsChange.list)
                assertEquals(7, resultsChange.list.size)
            }
            observer.cancel()
            channel.close()
        }
    }

    @Test
    fun realmList_flowDeleteElement() {
        runBlocking {
            val querySample = realm.query<QuerySample>("intField == $0", 1).find().first()
            val channel = Channel<ResultsChange<*>>(capacity = 1)
            val observer = async {
                querySample.objectListField
                    .query("TRUEPREDICATE").asFlow()
                    .collect { flowList ->
                        channel.send(flowList)
                    }
            }
            channel.receive().let { resultsChange ->
                assertIs<ResultsChange<*>>(resultsChange)
                assertNotNull(resultsChange.list)
                assertEquals(6, resultsChange.list.size)
            }
            realm.writeBlocking {
                val queriedSample = findLatest(querySample)
                queriedSample!!.objectListField.removeFirst()
            }
            channel.receive().let { resultsChange ->
                assertIs<ResultsChange<*>>(resultsChange)
                assertNotNull(resultsChange.list)
                assertEquals(5, resultsChange.list.size)
            }
            observer.cancel()
            channel.close()
        }
    }

    @Test
    fun realmList_flowDeleteList() {
        runBlocking {
            val querySample = realm.query<QuerySample>("intField == $0", 1).find().first()
            val channel = Channel<ResultsChange<*>>(capacity = 1)
            val observer = async {
                querySample.objectListField
                    .query("TRUEPREDICATE").asFlow()
                    .collect { flowList ->
                        channel.send(flowList)
                    }
            }
            channel.receive().let { resultsChange ->
                assertIs<ResultsChange<*>>(resultsChange)
                assertNotNull(resultsChange.list)
                assertEquals(6, resultsChange.list.size)
            }
            realm.writeBlocking {
                val queriedSample = findLatest(querySample)
                queriedSample!!.objectListField = realmListOf()
            }
            channel.receive().let { resultsChange ->
                assertIs<ResultsChange<*>>(resultsChange)
                assertNotNull(resultsChange.list)
                assertEquals(0, resultsChange.list.size)
            }
            observer.cancel()
            channel.close()
        }
    }
}
