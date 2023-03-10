package io.realm.kotlin.jvm

import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.dynamic.DynamicRealm
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.test.util.use
import io.realm.kotlinx.dataframe.readRealm
import io.realm.kotlinx.dataframe.toDataFrame
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.isEmpty
import org.jetbrains.kotlinx.dataframe.columns.FrameColumn
import org.jetbrains.kotlinx.dataframe.columns.ValueColumn
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataFrameTests {

    private lateinit var testFile: File
    private lateinit var dynamicTestConfig: RealmConfiguration

    @BeforeTest
    fun setUp() {
        testFile = File("../ext-dataframe/jupyter/random_games.realm")
        dynamicTestConfig = RealmConfiguration.Builder(setOf())
            .directory(File(testFile.absolutePath).parentFile.absolutePath)
            .name(testFile.name)
            .build()
    }

    @Test
    fun nonExistingFile_throws() {
        assertFailsWith<IllegalArgumentException> {
            DataFrame.readRealm(realmFile = File("not_existing.realm"))
        }
    }

    @Test
    fun topLevelFrameIsTables() {
        val df = DataFrame.readRealm(testFile)
        assertTrue(df.containsColumn("class"))
        assertTrue(df.containsColumn("data"))
        assertTrue(df["class"] is ValueColumn)
        assertTrue(df["data"] is FrameColumn<*>)
        assertEquals(3, df["class"].size())
    }

    @Test
    fun dynamicRealm_toDataFrame() {
        DynamicRealm.open(dynamicTestConfig).use { realm ->
            val df = realm.toDataFrame()
            assertTrue(df.containsColumn("class"))
            assertTrue(df.containsColumn("data"))
            assertTrue(df["class"] is ValueColumn)
            assertTrue(df["data"] is FrameColumn<*>)
            assertEquals(3, df["class"].size())
        }
    }

    @Test
    fun dynamicRealmResults_toDataFrame() {
        DynamicRealm.open(dynamicTestConfig).use { realm ->
            val results = realm.query("GamePlayer").find()
            val df: AnyFrame = results.toDataFrame(realm)
            assertTrue(df.containsColumn("name"))
            assertTrue(df.containsColumn("type"))
            assertEquals(2, df.rowsCount())
        }
    }

    @Test
    fun dynamicRealmLists_objects_toDataFrame() {
        DynamicRealm.open(dynamicTestConfig).use { realm ->
            val obj: DynamicRealmObject = realm.query("SaveGame").first().find()!!
            val df: AnyFrame = obj.getObjectList("steps").toDataFrame(realm)
            assertTrue(df.containsColumn("x"))
            assertTrue(df.containsColumn("y"))
            assertTrue(df.containsColumn("type"))
            assertFalse(df.isEmpty())
        }
    }

    @Test
    fun dynamicRealmObject_toDataFrame() {
        DynamicRealm.open(dynamicTestConfig).use { realm ->
            val obj: DynamicRealmObject = realm.query("GamePlayer").first().find()!!
            val df: AnyFrame = obj.toDataFrame(realm)
            assertTrue(df.containsColumn("name"))
            assertTrue(df.containsColumn("type"))
            assertEquals(1, df.rowsCount())
        }
    }

    @Test
    fun syncDataFrame() {
        val app = App.create("realm-jupyter-example-gszpo")
        runBlocking {
            val user = app.login(Credentials.anonymous(false))
            val config = SyncConfiguration.Builder(user = user, partitionValue = "example", schema = setOf())
                .build()
            DynamicRealm.open(config).use { realm ->
                val df = realm.toDataFrame()
                assertEquals(3, df.rowsCount())
            }
        }
        app.close()
    }


//    @Test
//    fun accessClassData() {
//        val df = DataFrame.readRealm(File("../ext-dataframe/jupyter/random_games.realm"))
//        val gamesRow: DataFrame<Any?> = df.filter { el: DataRow<Any?> -> el["class"] == "SaveGame" }
//        var data: DataFrame<*> = gamesRow["data"][0] as AnyFrame
//        data = data.explode("player1", "player2", "winner")
//        data = data.pivot("winner").count().df().add("total") { this.rowSum().toInt() }
//        data = data.flatten()
//        data = data
//            .rename(data.getColumn(0)).into("X")
//            .rename(data.getColumn(1)).into("O")
//            .rename(data.getColumn(2)).into("null")
//        data.print()
//        data = data.convert("X", "O", "null").with {
//            it as Int / (this["total"] as Int).toFloat()
//        }
//        val map = data.toMap()
//    }

}