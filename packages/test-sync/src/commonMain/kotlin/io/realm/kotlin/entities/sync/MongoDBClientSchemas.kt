/*
 * Copyright 2024 Realm Inc.
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

package io.realm.kotlin.entities.sync

import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.Serializable
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.ObjectId
import kotlin.random.Random

@Serializable
@Suppress("ConstructorParameterNaming")
class CollectionDataType(var name: String = "Default", @PrimaryKey var _id: Int = Random.nextInt()) :
    RealmObject {
    constructor() : this("Default")

    // Supplying custom companion object to work around that multiple K2 FIR extension clashes if
    // they both generate a Companion.
    // https://youtrack.jetbrains.com/issue/KT-62194/K2-Two-compiler-plugins-interference-in-generated-companion-object
    companion object
}

class ParentCollectionDataType : RealmObject {
    @PrimaryKey
    @Suppress("VariableNaming")
    var _id: ObjectId = BsonObjectId()
    var name: String = "PARENT-DEFAULT"
    var child: ChildCollectionDataType? = null
    var embeddedChild: EmbeddedChildCollectionDataType? = null
    var any: RealmAny? = null

    // Supplying custom companion object to work around that multiple K2 FIR extension clashes if
    // they both generate a Companion.
    // https://youtrack.jetbrains.com/issue/KT-62194/K2-Two-compiler-plugins-interference-in-generated-companion-object
    companion object
}

@Serializable
class ChildCollectionDataType : RealmObject {
    @PrimaryKey
    @Suppress("VariableNaming")
    var _id: ObjectId = BsonObjectId()
    var name: String = "CHILD-DEFAULT"

    // Supplying custom companion object to work around that multiple K2 FIR extension clashes if
    // they both generate a Companion.
    // https://youtrack.jetbrains.com/issue/KT-62194/K2-Two-compiler-plugins-interference-in-generated-companion-object
    companion object
}

@Serializable
class EmbeddedChildCollectionDataType : EmbeddedRealmObject {
    @Suppress("VariableNaming")
    var _id: ObjectId = BsonObjectId()
    var name: String = "EMBEDDEDCHILD-DEFAULT"

    // Supplying custom companion object to work around that multiple K2 FIR extension clashes if
    // they both generate a Companion.
    // https://youtrack.jetbrains.com/issue/KT-62194/K2-Two-compiler-plugins-interference-in-generated-companion-object
    companion object
}

internal val COLLECTION_SCHEMAS = setOf(
    CollectionDataType::class,
    ParentCollectionDataType::class,
    ChildCollectionDataType::class,
    EmbeddedChildCollectionDataType::class,
)
