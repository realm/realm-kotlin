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

import io.realm.internal.manage
import io.realm.internal.unmanage
import io.realm.Callback
import io.realm.interop.RealmInterop
import io.realm.runtimeapi.NativePointer
import io.realm.runtimeapi.RealmModel
import io.realm.runtimeapi.RealmModelInternal
import kotlin.reflect.KClass

// TODO API-PUBLIC Document platform specific internals (RealmInitilizer, etc.)
class Realm {
    private var dbPointer: NativePointer? = null // TODO API-INTERNAL nullable to avoid "'lateinit' modifier is not allowed on properties of primitive types"
    private lateinit var realmConfiguration: RealmConfiguration

    companion object {
        fun open(realmConfiguration: RealmConfiguration): Realm {
            // TODO API-INTERNAL
            //  IN Android use lazy property delegation init to load the shared library use the
            //  function call (lazy init to do any preprocessing before starting Realm eg: log level etc)
            //  or implement an init method which is a No-OP in iOS but in Android it load the shared library

            val realm = Realm()
            realm.realmConfiguration = realmConfiguration
            realm.dbPointer = RealmInterop.realm_open(realmConfiguration.nativeConfig)
            return realm
        }

        // FIXME API-MUTABLE-REALM This should actually only be possible on a mutable realm, i.e. inside
        //  a transaction
        // FIXME EVALUATE Should this be on RealmModel instead?
        fun <T : RealmModel> delete(obj: T) {
            val internalObject = obj as RealmModelInternal
            internalObject.`$realm$ObjectPointer`?.let { RealmInterop.realm_object_delete(it) }
                ?: throw IllegalArgumentException("Cannot delete unmanaged object")
            internalObject.unmanage()
        }

        fun <T : RealmModel> addNotificationListener(obj: T, callback: Callback) {
            val internalObject = obj as RealmModelInternal
            internalObject.`$realm$ObjectPointer`?.let {
                RealmInterop.realm_object_add_notification_callback(it, object: io.realm.interop.Callback {
                    override fun onChange(change: NativePointer) {
                        // FIXME Clean up debug output
//                        println("change: isDeleted:${realm_wrapper.realm_object_changes_is_deleted(change)}")
//                        val count = realm_wrapper.realm_object_changes_get_num_modified_properties(change)
//                        println("change: updates:$count")
//                        memScoped {
//                            val colKeys = allocArray<realm_col_key>(count.toInt())
//                            val realmObjectChangesGetModifiedProperties =
//                                    realm_wrapper.realm_object_changes_get_modified_properties(change, colKeys, count)
//                            println("change: updates fetched:$realmObjectChangesGetModifiedProperties")
//                        }
                        // Perform actual callbackTODO("Not yet implemented")
                        callback.onChange()
                    }
                })
            } ?: throw IllegalArgumentException("Cannot register listeners on unmanaged object")
        }
    }

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

    //    reflection is not supported in K/N so we can't offer method like
    //    inline fun <reified T : RealmModel> create() : T
    //    to create a dynamically managed model. we're limited thus to persist methods
    //    were we take an already created un-managed instance and return a new manageable one
    //    (note since parameter are immutable in Kotlin, we need to create a new instance instead of
    //    doing this operation in place)
    fun <T : RealmModel> create(type: KClass<T>): T {
        val objectType = type.simpleName ?: error("Cannot get class name")
        val managedModel = realmConfiguration.schema.newInstance(type) as RealmModelInternal // TODO make newInstance return RealmModelInternal
        val key = RealmInterop.realm_find_class(dbPointer!!, objectType)
        return managedModel.manage(
            dbPointer!!,
            type,
            RealmInterop.realm_object_create(dbPointer!!, key)
        )
    }

    fun <T : RealmModel> objects(clazz: KClass<T>): RealmResults<T> {
        return RealmResults(
            dbPointer!!,
            @Suppress("SpreadOperator") // TODO PERFORMANCE Spread operator triggers detekt
            { RealmInterop.realm_query_parse(dbPointer!!, clazz.simpleName!!, "TRUEPREDICATE") },
            clazz,
            realmConfiguration.schema
        )
    }

    // FIXME Consider adding a delete-all along with query support
    //  https://github.com/realm/realm-kotlin/issues/64
    // fun <T : RealmModel> delete(clazz: KClass<T>)
}
