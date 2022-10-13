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
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@Suppress("LargeClass")
class RealmAnyTests {

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(setOf(TestContainer::class))
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

    @Test
    fun transport() {
        realm.writeBlocking {
            val unmanagedObj = TestContainer()
            val managedObj = copyToRealm(unmanagedObj)

            // GETTERS
            println("---> GETTER STRING")
            val stringValue = managedObj.stringField
//            println("---> GETTER BYTE")
//            val byteValue = managedObj.byteField
//            println("---> GETTER CHAR")
//            val charValue = managedObj.charField
//            println("---> GETTER SHORT")
//            val shortValue = managedObj.shortField
//            println("---> GETTER INT")
//            val intValue = managedObj.intField
//            println("---> GETTER LONG")
//            val longValue = managedObj.longField
//            println("---> GETTER BOOLEAN")
//            val booleanValue = managedObj.booleanField
//            println("---> GETTER FLOAT")
//            val floatValue = managedObj.floatField
//            println("---> GETTER DOUBLE")
//            val doubleValue = managedObj.doubleField
//            println("---> GETTER TIMESTAMP")
//            val timestampValue = managedObj.timestampField
//            println("---> GETTER OBJECTID")
//            val objectIdValue = managedObj.objectIdField
//            println("---> GETTER UUID")
//            val uuidValue = managedObj.uuidField
//            println("---> GETTER BYTEARRAY")
//            val byteArrayValue = managedObj.byteArrayField

            // ASSERTIONS
            assertEquals(unmanagedObj.stringField, stringValue)
            println("---> STRING DONE")
//            assertEquals(unmanagedObj.byteField, byteValue)
//            println("---> BYTE DONE")
//            assertEquals(unmanagedObj.charField, charValue)
//            println("---> CHAR DONE")
//            assertEquals(unmanagedObj.shortField, shortValue)
//            println("---> SHORT DONE")
//            assertEquals(unmanagedObj.intField, intValue)
//            println("---> INT DONE")
//            assertEquals(unmanagedObj.longField, longValue)
//            println("---> LONG DONE")
//            assertEquals(unmanagedObj.booleanField, booleanValue)
//            println("---> BOOLEAN DONE")
//            assertEquals(unmanagedObj.floatField, floatValue)
//            println("---> FLOAT DONE")
//            assertEquals(unmanagedObj.doubleField, doubleValue)
//            println("---> DOUBLE DONE")
//            assertEquals(unmanagedObj.timestampField, timestampValue)
//            println("---> TIMESTAMP DONE")
//            assertEquals(unmanagedObj.objectIdField, objectIdValue)
//            println("---> OBJECTID DONE")
//            assertEquals(unmanagedObj.uuidField, uuidValue)
//            println("---> UUID DONE")
//            assertContentEquals(unmanagedObj.byteArrayField, byteArrayValue)
//            println("---> BYTEARRAY DONE")

            println("---------------------------> DONE")
        }
    }
}

class TestContainer : RealmObject {
    var stringField: String? = "Realm"
//    var byteField: Byte? = 0xA
//    var charField: Char? = 'a'
//    var shortField: Short? = 17
//    var intField: Int? = 42
//    var longField: Long? = 256
//    var booleanField: Boolean? = true
//    var floatField: Float? = 3.14f
//    var doubleField: Double? = 1.19840122
//    var timestampField: RealmInstant? = RealmInstant.from(0,0)
//    var objectIdField: ObjectId? = ObjectId.create()
//    var uuidField: RealmUUID? = RealmUUID.random()
//    var byteArrayField: ByteArray? = byteArrayOf(42)

//    var mutableRealmInt: MutableRealmInt? = MutableRealmInt.create(42)
}
