/*
 * Copyright 2023 Realm Inc.
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
@file:UseSerializers(
    RealmListSerializer::class,
    MutableRealmIntSerializer::class
)

package io.realm.kotlin.test.shared

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.link.Child
import io.realm.kotlin.entities.link.Parent
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.serializers.MutableRealmIntSerializer
import io.realm.kotlin.serializers.RealmListSerializer
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@Serializable
class SerializableAllDatatypes : RealmObject {
    var realmList: RealmList<String> = realmListOf()
//    var realmSet: RealmSet<String> = realmSetOf()
//    var realmAny: RealmAny = RealmAny.create(0),
//    var realmInstant: RealmInstant = RealmInstant.now(),
    var mutableRealmInt: MutableRealmInt = MutableRealmInt.create(0)
//    var realmUUID: RealmUUID = RealmUUID.random(),
//    var realmObjectId: ObjectId = ObjectId.create(),
//    var realmObject: SerializableAllDatatypes = SerializableAllDatatypes(),
//    var realmEmbeddedObject: SerializableEmbeddedObject = SerializableEmbeddedObject(),
}

//@Serializable
//class SerializableEmbeddedObject() : EmbeddedRealmObject {
//    var name: String = ""
//}

class SerializationTests {
    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    private lateinit var configuration: RealmConfiguration


    private val json = Json {
        encodeDefaults = true
    }
    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration.Builder(setOf(Parent::class, Child::class))
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
    fun serializeUnmanagedObject() {
        val encoded = json.encodeToString(
            SerializableAllDatatypes()
        )
        println(encoded)
    }
}