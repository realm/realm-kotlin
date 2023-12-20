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
@file:Suppress("invisible_member", "invisible_reference")

package io.realm.kotlin.entities.adapters

import io.realm.kotlin.internal.asBsonDateTime
import io.realm.kotlin.internal.toRealmInstant
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmTypeAdapter
import io.realm.kotlin.types.annotations.TypeAdapter
import org.mongodb.kbson.BsonDateTime
import kotlin.time.Duration.Companion.milliseconds

class UsingSingletonAdapter : RealmObject {
    @TypeAdapter(adapter = RealmInstantBsonDateTimeAdapterSingleton::class)
    var date: BsonDateTime = BsonDateTime()
}

object RealmInstantBsonDateTimeAdapterSingleton : RealmTypeAdapter<RealmInstant, BsonDateTime> {
    override fun fromRealm(realmValue: RealmInstant): BsonDateTime = realmValue.asBsonDateTime()

    override fun toRealm(value: BsonDateTime): RealmInstant =
        value.value.milliseconds.toRealmInstant()
}
