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
import io.realm.annotations.RealmClass
import io.realm.kotlin.demo.javacompatibility.TAG
import io.realm.kotlin.demo.javacompatibility.data.Repository

// Realm Kotlin will try to process this class if using io.realm.RealmObject so use
// io.realm.RealmModel/@RealmClass approach instead
@RealmClass
open class JavaEntity : RealmModel {
    var name: String = "JAVA"
}

class JavaRepository(appContext: Context) : Repository {

    var realm: Realm

    init {
        Realm.init(appContext)
        realm = Realm.getInstance(
            RealmConfiguration.Builder()
                .name("java.realm")
                .allowWritesOnUiThread(true)
                .build()
        )
        realm.executeTransaction {
            realm.createObject(JavaEntity::class.java)
            val entities = realm.where(JavaEntity::class.java).findAll()
            Log.d(TAG, "JAVA: ${entities.size}")
        }
    }

    override val count: Int = realm.where(JavaEntity::class.java).findAll().count()

}
