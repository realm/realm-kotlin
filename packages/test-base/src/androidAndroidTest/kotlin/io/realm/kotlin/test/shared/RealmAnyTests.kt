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
import io.realm.kotlin.types.EmbeddedRealmObject
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
        val configuration = RealmConfiguration.Builder(
            setOf(
                TestContainer::class,
                TestParent::class,
                TestEmbeddedChild::class
            )
        ).directory(tmpDir)
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

            assertEquals(unmanagedObj.stringField, managedObj.stringField)
            assertEquals(unmanagedObj.byteField, managedObj.byteField)
            assertEquals(unmanagedObj.charField, managedObj.charField)
            assertEquals(unmanagedObj.shortField, managedObj.shortField)
            assertEquals(unmanagedObj.intField, managedObj.intField)
            assertEquals(unmanagedObj.longField, managedObj.longField)
            assertEquals(unmanagedObj.booleanField, managedObj.booleanField)
            assertEquals(unmanagedObj.floatField, managedObj.floatField)
            assertEquals(unmanagedObj.doubleField, managedObj.doubleField)
            assertEquals(unmanagedObj.timestampField, managedObj.timestampField)
            assertEquals(unmanagedObj.objectIdField, managedObj.objectIdField)
            assertEquals(unmanagedObj.uuidField, managedObj.uuidField)
            assertContentEquals(unmanagedObj.byteArrayField, managedObj.byteArrayField)
            assertEquals(unmanagedObj.objectField?.name, managedObj.objectField?.name)
            assertEquals(
                unmanagedObj.embeddedObjectField?.name,
                managedObj.embeddedObjectField?.name
            )
        }
    }
}

class TestContainer : RealmObject {
    var stringField: String? = "Realm"
    var byteField: Byte? = 0xA
    var charField: Char? = 'a'
    var shortField: Short? = 17
    var intField: Int? = 42
    var longField: Long? = 256
    var booleanField: Boolean? = true
    var floatField: Float? = 3.14f
    var doubleField: Double? = 1.19840122
    var timestampField: RealmInstant? = RealmInstant.from(0, 0)
    var objectIdField: ObjectId? = ObjectId.create()
    var uuidField: RealmUUID? = RealmUUID.random()
    var byteArrayField: ByteArray? = byteArrayOf(42)
    var objectField: TestParent? = TestParent()
    var embeddedObjectField: TestEmbeddedChild? = TestEmbeddedChild()

//    var mutableRealmInt: MutableRealmInt? = MutableRealmInt.create(42)
}

class TestParent : RealmObject {
    var name: String? = "Parent"
}

class TestEmbeddedChild : EmbeddedRealmObject {
    var name: String? = "Embedded-child"
}
