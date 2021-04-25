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

import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop

// TODO API-PUBLIC Document platform specific internals (RealmInitilizer, etc.)
class Realm private constructor(configuration: RealmConfiguration, dbPointer: NativePointer) :
    BaseRealm(configuration, dbPointer) {

    companion object {
        /**
         * Default name for Realm files unless overridden by [RealmConfiguration.Builder.name].
         */
        public const val DEFAULT_FILE_NAME = "default.realm"

        fun open(realmConfiguration: RealmConfiguration): Realm {
            // TODO API-INTERNAL
            //  IN Android use lazy property delegation init to load the shared library use the
            //  function call (lazy init to do any preprocessing before starting Realm eg: log level etc)
            //  or implement an init method which is a No-OP in iOS but in Android it load the shared library
            val realm = Realm(realmConfiguration, RealmInterop.realm_open(realmConfiguration.nativeConfig))
            realm.log.info("Opened Realm: ${realmConfiguration.path}")
            return realm
        }
    }

    /**
     * Open a Realm instance. This instance grants access to an underlying Realm file defined by
     * the provided [RealmConfiguration].
     *
     * FIXME Figure out how to describe the constructor better
     * FIXME Once the implementation of this class moves to the frozen architecture
     *  this constructor should be the primary way to open Realms (as you only need
     *  to do it once pr. app).
     */
    public constructor(configuration: RealmConfiguration) :
        this(configuration, RealmInterop.realm_open(configuration.nativeConfig))

    /**
     * TODO Add docs when this method is implemeted
     */
    suspend fun <R> write(function: MutableRealm.() -> R): R {
        TODO("Awaiting implementation of the Frozen Architecture")
    }

    /**
     * NOTE Avoid calling this method on the UI thread, instead use [Realm.write].
     *
     * Modify the underlying Realm file by creating a write transaction on the current thread. Write
     * transactions automatically commit any changes made when the closure returns unless
     * [MutableRealm.cancelWrite] was called.
     *
     * The write transaction always represent the latest version of data in the Realm file, even if the calling
     * Realm not yet represent this.
     *
     * TODO Better explanation here.
     * @return any value returned from the provided write function.
     */
    @Suppress("TooGenericExceptionCaught")
    fun <R> writeBlocking(function: MutableRealm.() -> R): R {
        // While not efficiently to open a new Realm just for a write, it makes it a lot
        // easier to control the API surface between Realm and MutableRealm
        val writerRealm = MutableRealm(configuration, dbPointer)
        try {
            writerRealm.beginTransaction()
            val returnValue: R = function(writerRealm)
            writerRealm.commitTransaction()
            return returnValue
        } catch (e: Exception) {
            // Only cancel writes for exceptions. For errors assume that something has gone
            // horribly wrong and the process is exiting. And canceling the write might just
            // hide the true underlying error.
            writerRealm.cancelWrite()
            throw e
        }
    }

    /**
     * Close this Realm and all underlying resources. Accessing any methods or Realm Objects after this
     * method has been called will then an [IllegalStateException].
     */
    public override fun close() {
        super.close()
    }
}
