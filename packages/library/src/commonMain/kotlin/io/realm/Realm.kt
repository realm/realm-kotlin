/*
 * Copyright 2020 Realm Inc.
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

package io.realm

import io.realm.interop.RealmInterop
import io.realm.runtimeapi.NativePointer
import io.realm.runtimeapi.RealmModel
import io.realm.runtimeapi.RealmModelInternal
import kotlin.reflect.KClass

// TODO Document platform specific internals (RealmInitilizer, etc.)
class Realm {
    private var dbPointer: NativePointer? = null // TODO nullable to avoid "'lateinit' modifier is not allowed on properties of primitive types"
    private lateinit var realmConfiguration: RealmConfiguration

    companion object {
        fun open(realmConfiguration: RealmConfiguration): Realm {
            // TODO
            // IN Android use lazy property delegation init to load the shared library
            //   use the function call (lazy init to do any preprocessing before starting Realm eg: log level etc)
            //  or implement an init method which is a No-OP in iOS but in Android it load the shared library

            val realm = Realm()
            realm.realmConfiguration = realmConfiguration
            realm.dbPointer = RealmInterop.realm_open(realmConfiguration.nativeConfig)
            return realm
        }
    }
    //    fun open(dbName: String, schema: String) : Realm
    fun beginTransaction() {
        RealmInterop.realm_begin_write(dbPointer!!)
    }

    fun commitTransaction() {
        RealmInterop.realm_commit(dbPointer!!)
    }

    fun cancelTransaction() {
        TODO()
    }

    fun registerListener(f: () -> Unit) {
    }

    fun <T : RealmModel> objects(clazz: KClass<T>, query: String): RealmResults<T> {
        val objectType = clazz.simpleName ?: error("Cannot get class name") // TODO infer type from T
        // TODO check nullability of pointer and throw
        val query: NativePointer = TODO()
        return RealmResults(
            query,
            clazz,
            realmConfiguration.modelFactory
        )
    }
    //    reflection is not supported in K/N so we can't offer method like
    //    inline fun <reified T : RealmModel> create() : T
    //    to create a dynamically managed model. we're limited thus to persist methods
    //    were we take an already created un-managed instance and return a new manageable one
    //    (note since parameter are immutable in Kotlin, we need to create a new instance instead of
    //    doing this operation in place)
    fun <T : RealmModel> create(type: KClass<T>): T {
        val objectType = type.simpleName ?: error("Cannot get class name")
        val managedModel = realmConfiguration.modelFactory.invoke(type) as RealmModelInternal
        val key = RealmInterop.realm_find_class(dbPointer!!, objectType)
        managedModel.`$realm$Pointer` = dbPointer
        managedModel.`$realm$ObjectPointer` = RealmInterop.realm_object_create(dbPointer!!, key)
        managedModel.`$realm$IsManaged` = true
        managedModel.`$realm$TableName` = objectType
        return managedModel as T
    }
}
