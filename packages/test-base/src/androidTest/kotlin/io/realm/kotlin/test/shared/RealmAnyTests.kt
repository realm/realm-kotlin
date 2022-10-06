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
import io.realm.kotlin.types.MutableRealmInt
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
        realm.schema().classes.forEach {
            it.properties.forEach { property ->
                println("---> '${property.name}', nullable: ${property.isNullable}")
            }
        }
        realm.writeBlocking {
            val unmanagedObj = TestContainer()
            val managedObj = copyToRealm(unmanagedObj)

//            val stringValue = managedObj.stringField
//            val byteValue = managedObj.byteField
//            val charValue = managedObj.charField
            val shortValue = managedObj.shortField
//            val intValue = managedObj.intField
//            val longValue = managedObj.longField
//            val booleanValue = managedObj.booleanField
//            val floatValue = managedObj.floatField
//            val doubleValue = managedObj.doubleField
//            val timestampValue = managedObj.timestampField
//            val objectIdValue = managedObj.objectIdField
//            val uuidValue = managedObj.uuidField
//            val byteArrayValue = managedObj.byteArrayField
//            val mutableRealmIntValue = managedObj.mutableRealmInt

//            assertEquals(unmanagedObj.stringField, stringValue)
//            assertEquals(unmanagedObj.byteField, byteValue)
//            assertEquals(unmanagedObj.charField, charValue)
            assertEquals(unmanagedObj.shortField, shortValue)
//            assertEquals(unmanagedObj.intField, intValue)
//            assertEquals(unmanagedObj.longField, longValue)
//            assertEquals(unmanagedObj.booleanField, booleanValue)
//            assertEquals(unmanagedObj.floatField, floatValue)
//            assertEquals(unmanagedObj.doubleField, doubleValue)
//            assertEquals(unmanagedObj.timestampField, timestampValue)
//            assertEquals(unmanagedObj.objectIdField, objectIdValue)
//            assertEquals(unmanagedObj.uuidField, uuidValue)
//            assertContentEquals(unmanagedObj.byteArrayField, byteArrayValue)
//            assertEquals(unmanagedObj.mutableRealmInt, mutableRealmIntValue)
            println("---> DONE")
        }
    }
}

class TestContainer : RealmObject {
//    var stringField: String? = "Realm"
//    var byteField: Byte? = 0xA
//    var charField: Char? = 'a'
//    var shortFieldNonNullable: Short = 17 // FIXME non-nullable fields fail on native
    var shortField: Short? = 17
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
