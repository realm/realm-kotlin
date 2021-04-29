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

import io.realm.internal.RealmLog
import io.realm.internal.RealmModelInternal
import io.realm.internal.copyToRealm
import io.realm.internal.unmanage
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import kotlin.reflect.KClass

// TODO API-PUBLIC Document platform specific internals (RealmInitilizer, etc.)
class Realm private constructor(configuration: RealmConfiguration, dbPointer: NativePointer) {

    // Public properties
    /**
     * Configuration used to configure this Realm instance.
     */
    val configuration: RealmConfiguration

    // Private/Internal properties
    private var dbPointer: NativePointer? = null
    internal val log: RealmLog

    companion object {
        /**
         * Default name for Realm files unless overridden by [RealmConfiguration.Builder.name].
         */
        public const val DEFAULT_FILE_NAME = "default.realm"

        /**
         * Default tag used by log entries
         */
        public const val DEFAULT_LOG_TAG = "REALM"

        fun open(realmConfiguration: RealmConfiguration): Realm {
            // TODO API-INTERNAL
            //  IN Android use lazy property delegation init to load the shared library use the
            //  function call (lazy init to do any preprocessing before starting Realm eg: log level etc)
            //  or implement an init method which is a No-OP in iOS but in Android it load the shared library

            val realm = Realm(realmConfiguration, RealmInterop.realm_open(realmConfiguration.nativeConfig))
            realm.log.info("Opened Realm: ${realmConfiguration.path}")
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
    }

    init {
        this.dbPointer = dbPointer
        this.configuration = configuration
        this.log = RealmLog(configuration = configuration.log)
    }

    fun beginTransaction() {
        RealmInterop.realm_begin_write(dbPointer!!)
    }

    fun commitTransaction() {
        RealmInterop.realm_commit(dbPointer!!)
    }

    // TODO Add @throws when Realm exception hierarchy is settled
    //  https://github.com/realm/realm-kotlin/issues/70
    /**
     * Roll back the current write transaction.
     *
     * @throws RuntimeException if there is currently no write transaction.
     */
    // TODO Add test for this, but since it is part of transaction behaviour it only makes sense
    //  when implementing our background thread backed write method
    fun rollbackTransaction() {
        RealmInterop.realm_rollback(dbPointer!!)
    }

    fun <T : RealmObject> create(type: KClass<T>): T {
        return io.realm.internal.create(configuration.mediator, dbPointer!!, type)
    }
    // Convenience inline method for the above to skip KClass argument
    inline fun <reified T : RealmObject> create(): T { return create(T::class) }

    fun <T : RealmObject> create(type: KClass<T>, primaryKey: Any?): T {
        return io.realm.internal.create(realmConfiguration.mediator, dbPointer!!, type, primaryKey)
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
        return copyToRealm(configuration.mediator, dbPointer!!, instance)
    }

    fun <T : RealmObject> objects(clazz: KClass<T>): RealmResults<T> {
        return RealmResults(
            dbPointer!!,
            @Suppress("SpreadOperator") // TODO PERFORMANCE Spread operator triggers detekt
            { RealmInterop.realm_query_parse(dbPointer!!, clazz.simpleName!!, "TRUEPREDICATE") },
            clazz,
            configuration.mediator
        )
    }
    // Convenience inline method for the above to skip KClass argument
    inline fun <reified T : RealmObject> objects(): RealmResults<T> { return objects(T::class) }

    // FIXME Consider adding a delete-all along with query support
    //  https://github.com/realm/realm-kotlin/issues/64
    // fun <T : RealmModel> delete(clazz: KClass<T>)

    fun close() {
        dbPointer?.let {
            RealmInterop.realm_close(it)
        }
        dbPointer = null
        log.info("Realm closed: ${configuration.path}")
    }
}
