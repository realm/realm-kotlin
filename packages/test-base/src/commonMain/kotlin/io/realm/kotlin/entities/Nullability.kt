/*
 * Copyright 2021 Realm Inc.
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

package io.realm.kotlin.entities

import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128

class Nullability : RealmObject {
    var stringNullable: String? = null
    var stringNonNullable: String = ""
    var booleanNullable: Boolean? = null

    var byteNullable: Byte? = null
    var charNullable: Char? = null
    var shortNullable: Short? = null
    var intNullable: Int? = null
    var longNullability: Long? = null

    var floatNullable: Float? = null
    var doubleField: Double? = null
    var decimal128Field: Decimal128? = null
    var timestampField: RealmInstant? = null
    var bsonObjectIdField: BsonObjectId? = null
    var uuidField: RealmUUID? = null
    var binaryField: ByteArray? = null
    var mutableRealmIntField: MutableRealmInt? = null
    var realmAnyField: RealmAny? = null
    var objectField: Nullability? = null
}
