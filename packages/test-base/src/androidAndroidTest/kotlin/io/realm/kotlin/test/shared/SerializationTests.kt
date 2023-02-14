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
    RealmSetSerializer::class,
    RealmAnySerializer::class,
    RealmInstantSerializer::class,
    MutableRealmIntSerializer::class,
    RealmUUIDSerializer::class,
    RealmObjectIdSerializer::class
)

package io.realm.kotlin.test.shared

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.link.Child
import io.realm.kotlin.entities.link.Parent
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.serializers.MutableRealmIntSerializer
import io.realm.kotlin.serializers.RealmAnySerializer
import io.realm.kotlin.serializers.RealmInstantSerializer
import io.realm.kotlin.serializers.RealmListSerializer
import io.realm.kotlin.serializers.RealmObjectIdSerializer
import io.realm.kotlin.serializers.RealmSetSerializer
import io.realm.kotlin.serializers.RealmUUIDSerializer
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TypeDescriptor
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.mongodb.kbson.BsonObjectId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@Serializable
class SerializableAllDatatypes : RealmObject {
    var realmList: RealmList<String> = realmListOf()
    var realmSet: RealmSet<String> = realmSetOf()
    var mutableRealmInt: MutableRealmInt? = null
    var realmAny: RealmAny? = null
    var realmInstant: RealmInstant? = null
    var realmUUID: RealmUUID? = null
    var realmObjectId: ObjectId? = null
    var realmObject: SerializableAllDatatypes? = null
    var realmEmbeddedObject: SerializableEmbeddedObject? = null
}

@Serializable
class SerializableEmbeddedObject : EmbeddedRealmObject {
    var name: String = ""
}

class SerializationTests {
    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    private lateinit var configuration: RealmConfiguration


    private val json = Json {
        serializersModule = SerializersModule {
            polymorphic(RealmObject::class) {
                subclass(SerializableAllDatatypes::class)
            }
        }
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
        for (type in TypeDescriptor.anyClassifiers.keys) {
            when (type) {

            }
        }
        val encoded = json.encodeToString(
            SerializableAllDatatypes().apply {
                realmAny = RealmAny.create(RealmUUID.random())
                realmObject = SerializableAllDatatypes()
                realmEmbeddedObject = SerializableEmbeddedObject()
            }
        )
        println(encoded)
        json.decodeFromString<SerializableAllDatatypes>(encoded)
    }
}