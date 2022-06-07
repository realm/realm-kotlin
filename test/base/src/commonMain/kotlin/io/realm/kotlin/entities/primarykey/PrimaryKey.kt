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

package io.realm.kotlin.entities.primarykey

import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlin.random.Random
import kotlin.random.nextULong

class NoPrimaryKey : RealmObject {
    var nonPrimaryKey: String = Random.nextULong().toString()
}

class PrimaryKeyByte : RealmObject {
    @PrimaryKey
    var primaryKey: Byte = Random.nextULong().toByte()
}

class PrimaryKeyByteNullable : RealmObject {
    @PrimaryKey
    var primaryKey: Byte? = Random.nextULong().toByte()
}

class PrimaryKeyChar : RealmObject {
    @PrimaryKey
    var primaryKey: Char = Random.nextULong().toByte().toChar()
}

class PrimaryKeyCharNullable : RealmObject {
    @PrimaryKey
    var primaryKey: Char? = Random.nextULong().toByte().toChar()
}

class PrimaryKeyShort : RealmObject {
    @PrimaryKey
    var primaryKey: Short = Random.nextULong().toShort()
}

class PrimaryKeyShortNullable : RealmObject {
    @PrimaryKey
    var primaryKey: Short? = Random.nextULong().toShort()
}
class PrimaryKeyInt : RealmObject {
    @PrimaryKey
    var primaryKey: Int = Random.nextInt()
}

class PrimaryKeyIntNullable : RealmObject {
    @PrimaryKey
    var primaryKey: Int? = Random.nextInt()
}

class PrimaryKeyLong : RealmObject {
    @PrimaryKey
    var primaryKey: Long = Random.nextLong()
}

class PrimaryKeyLongNullable : RealmObject {
    @PrimaryKey
    var primaryKey: Long? = Random.nextLong()
}

class PrimaryKeyString : RealmObject {
    @PrimaryKey
    var primaryKey: String = Random.nextULong().toString()
}

class PrimaryKeyStringNullable : RealmObject {
    @PrimaryKey
    var primaryKey: String? = Random.nextULong().toString()
}

class PrimaryKeyObjectId : RealmObject {
    @PrimaryKey
    var primaryKey: ObjectId = ObjectId.create()
}

class PrimaryKeyObjectIdNullable : RealmObject {
    @PrimaryKey
    var primaryKey: ObjectId? = ObjectId.from("507f191e810c19729de860ea")
}
