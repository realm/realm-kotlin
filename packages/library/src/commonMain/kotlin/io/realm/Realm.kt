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

import io.realm.internal.NotificationToken
import io.realm.internal.RealmModelInternal
import io.realm.internal.copyToRealm
import io.realm.internal.unmanage
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
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
        fun <T : RealmObject> delete(obj: T) {
            val internalObject = obj as RealmModelInternal
            internalObject.`$realm$ObjectPointer`?.let { RealmInterop.realm_object_delete(it) }
                ?: throw IllegalArgumentException("Cannot delete unmanaged object")
            internalObject.unmanage()
        }

        /**
         * Observe change.
         *
         * Triggers calls to [callback] when there are changes to [obj].
         *
         * To receive asynchronous callbacks this must be called:
         * - Android: on a thread with a looper
         * - iOS/macOS: on the main thread (as we currently do not support opening Realms with
         *   different schedulers similarly to
         *   https://github.com/realm/realm-cocoa/blob/master/Realm/RLMRealm.mm#L424)
         *
         * Notes:
         * - Calls are triggered synchronously on a [beginTransaction] when the version is advanced.
         * - Ignoring the return value will eliminate the possibility to cancel the registration
         *   and will leak the [callback] and potentially the internals related to the registration.
         */
        // @CheckReturnValue Not available for Kotlin?
        fun <T : RealmObject> observe(obj: T, callback: Callback): Cancellable {
            val internalObject = obj as RealmModelInternal
            internalObject.`$realm$ObjectPointer`?.let {
                val internalCallback = object : io.realm.interop.Callback {
                    override fun onChange(objectChanges: NativePointer) {
                        // FIXME Need to expose change details to the user
                        //  https://github.com/realm/realm-kotlin/issues/115
                        callback.onChange()
                    }
                }
                val token = RealmInterop.realm_object_add_notification_callback(it, internalCallback)
                return NotificationToken(internalCallback, token)
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

    fun <T : RealmObject> create(type: KClass<T>): T {
        return io.realm.internal.create(realmConfiguration.schema, dbPointer!!, type)
    }
    // Convenience inline method for the above to skip KClass argument
    inline fun <reified T : RealmObject> create(): T { return create(T::class) }

    fun <T : RealmObject> create(type: KClass<T>, primaryKey: Any?): T {
        return io.realm.internal.create(realmConfiguration.schema, dbPointer!!, type, primaryKey)
    }

    /**
     * Creates a copy of an object in the Realm.
     *
     * This will create a copy of an object and all it's children. Any already managed objects will
     * not be copied, including the root `instance`. So invoking this with an already managed
     * object is a no-operation.
     *
     * @param instance The object to create a copy from.
     * @return The managed version of the `instance`.
     */
    fun <T : RealmObject> copyToRealm(instance: T): T {
        return copyToRealm(realmConfiguration.schema, dbPointer!!, instance)
    }

    fun <T : RealmObject> objects(clazz: KClass<T>): RealmResults<T> {
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

    fun close() {
        dbPointer?.let {
            RealmInterop.realm_close(it)
        }
        dbPointer = null
    }
}
