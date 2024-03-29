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

package io.realm.kotlin.demo.javacompatibility.data.kotlin

import android.util.Log
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.demo.javacompatibility.TAG
import io.realm.kotlin.demo.javacompatibility.data.Repository
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class KotlinEntity : RealmObject {
    @PrimaryKey
    var id: String = ""
    var name: String = "KOTLIN"
}

class KotlinRepository: Repository {

    val realm: Realm

    init {
        val config = RealmConfiguration.Builder(setOf(KotlinEntity::class))
            .name("kotlin.realm")
            .build()
        Realm.deleteRealm(config)
        realm = Realm.open(config)
        realm.writeBlocking { copyToRealm(KotlinEntity()) }
        val entities = realm.query<KotlinEntity>().find()
        Log.w(TAG, "KOTLIN: ${entities.size}")
    }

    override val count = realm.query<KotlinEntity>().find().size
}
