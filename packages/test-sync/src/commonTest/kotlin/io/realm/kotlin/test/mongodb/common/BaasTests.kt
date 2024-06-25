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

package io.realm.kotlin.test.mongodb.common

import io.realm.kotlin.entities.sync.ChildPk
import io.realm.kotlin.entities.sync.flx.FlexChildObject
import io.realm.kotlin.entities.sync.flx.FlexEmbeddedObject
import io.realm.kotlin.entities.sync.flx.FlexParentObject
import io.realm.kotlin.ext.realmDictionaryOf
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.test.mongodb.util.SchemaProcessor
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.ObjectId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class RegularObject : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var name: String = "hello world1"
    var dog: BsonObjectId = ObjectId()
    var cycle: RegularObject? = null
    var dogs: RealmList<Doggy> = realmListOf()
    var dogMap: RealmDictionary<Doggy?> = realmDictionaryOf()
    var strings: RealmList<String> = realmListOf()
    var nullableStrings: RealmList<String?> = realmListOf()
}

class Doggy: RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
}

class EmbeddedObject : EmbeddedRealmObject {
    var nameEmb: String = "hello world2"
}

class BaasTests {

    @BeforeTest
    fun setup() {
    }

    @AfterTest
    fun tearDown() {
    }

    @Test
    fun testing() {
        val classes = setOf(RegularObject::class, EmbeddedObject::class, Doggy::class)

        val schemas = SchemaProcessor.process("database", classes)
        println(Json { prettyPrint = true }.encodeToString(schemas))
    }

    @Test
    fun moreTesting() {
        val classes = setOf(
            ChildPk::class,
            FlexChildObject::class,
            FlexEmbeddedObject::class,
            FlexParentObject::class,
        )

        val processor = SchemaProcessor.process("databaseName", classes)
    }


}
