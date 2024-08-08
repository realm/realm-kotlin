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

package io.realm.kotlin.demo.javacompatibility.data.java

import android.content.Context
import android.util.Log
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmModel
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass
import io.realm.annotations.RealmField
import io.realm.kotlin.createObject
import io.realm.kotlin.demo.javacompatibility.TAG
import io.realm.kotlin.demo.javacompatibility.data.Repository
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLog
import io.realm.mongodb.App
import io.realm.mongodb.AppConfiguration
import io.realm.mongodb.Credentials
import io.realm.mongodb.sync.Subscription
import io.realm.mongodb.sync.SyncConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.bson.types.ObjectId

// Realm Kotlin will try to process this class if using io.realm.RealmObject so use
// io.realm.RealmModel/@RealmClass approach instead
@RealmClass
open class JavaEntity : RealmModel {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var name: String = "JAVA"
}

open class JavaEntityWithBaseObject : RealmObject() {
    @PrimaryKey
    @RealmField("_id")
    var id: String = ""
}

var app = App("devicesync-iieas")

class JavaRepository(appContext: Context) : Repository {

    var realm: Realm

    init {
        Realm.init(appContext)
        io.realm.log.RealmLog.setLevel(io.realm.log.LogLevel.ALL)

        val user = runBlocking(Dispatchers.IO) {
            app.login(Credentials.emailPassword("xxxx", "xxxx"))
        }
        val config = SyncConfiguration.Builder(user)
            .initialSubscriptions { realm, subscriptions ->
                subscriptions.addOrUpdate(Subscription.create("JavaEntity", realm.where(JavaEntity::class.java)))
            }
            .name("java.realm")
            .allowWritesOnUiThread(true)
            .build()
        realm = Realm.getInstance(config)
        realm.executeTransaction {
            realm.createObject(JavaEntity::class.java, ObjectId())
            val entities = realm.where(JavaEntity::class.java).findAll()
            Log.d(TAG, "JAVA: ${entities.size}")
        }
    }

    override val count: Int = realm.where(JavaEntity::class.java).findAll().count()

    override fun close() {
        realm.close()
    }
}
