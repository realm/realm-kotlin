/*
 * Copyright 2023 Realm Inc.
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

package io.realm.kotlin.test

import io.realm.kotlin.internal.interop.ClassInfo
import io.realm.kotlin.internal.interop.CoreLogLevel
import io.realm.kotlin.internal.interop.LiveRealmPointer
import io.realm.kotlin.internal.interop.LiveRealmT
import io.realm.kotlin.internal.interop.LogCallback
import io.realm.kotlin.internal.interop.MemAllocator
import io.realm.kotlin.internal.interop.NativePointer
import io.realm.kotlin.internal.interop.PropertyFlags
import io.realm.kotlin.internal.interop.PropertyInfo
import io.realm.kotlin.internal.interop.PropertyType
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmInterop.realm_get_value
import io.realm.kotlin.internal.interop.getterScope
import kotlin.test.Test

class VersionManagementTests {

    @Test
    fun asdf() {
        RealmInterop.realm_set_log_level(CoreLogLevel.RLM_LOG_LEVEL_ALL)
        RealmInterop.realm_set_log_callback(CoreLogLevel.RLM_LOG_LEVEL_ALL, object : LogCallback {
            override fun log(logLevel: Short, message: String?) {
                println(message)
            }
        })
        val config = RealmInterop.realm_config_new()
        val schema = RealmInterop.realm_schema_new(listOf(
            ClassInfo("Sample", numProperties = 1)
                    to listOf(PropertyInfo("int", type = PropertyType.RLM_PROPERTY_TYPE_INT))
        ))
        RealmInterop.realm_config_set_schema(config, schema)
        RealmInterop.realm_config_set_schema_version(config, 1)

        val scheduler = RealmInterop.realm_create_scheduler()
        val realm: LiveRealmPointer = RealmInterop.realm_open(config, scheduler).first

        val sampleClassKey = RealmInterop.realm_find_class(realm, "Sample")
        val intPropertyKey = RealmInterop.realm_get_col_key(realm, sampleClassKey!!, "int")

        println("WRITE")

        RealmInterop.realm_begin_write(realm)
        val sampleObject = RealmInterop.realm_object_create(realm, sampleClassKey!!)
        println("COMMIT")
        RealmInterop.realm_commit(realm)

        val frozenRealm = RealmInterop.realm_freeze(realm)
        val sampleFrozenObject = RealmInterop.realm_object_resolve_in(sampleObject, frozenRealm)
        println("CLONE")
//        val frozenRealm2 = RealmInterop.realm_clone(frozenRealm)
        val frozenRealm2 = RealmInterop.realm_freeze(frozenRealm)
        val sampleFrozenObject2 = RealmInterop.realm_object_resolve_in(sampleObject, frozenRealm2)
//        RealmInterop.realm_begin_read(frozenRealm2)

        println("CLOSE1")
        RealmInterop.realm_close(realm)  // This does not close DB as there is a reference to an open version
        println("CLOSE2")
        // This will close DB even though frozenRealm2 is still alive
//        RealmInterop.realm_close(frozenRealm) // Closing a cloned version does not fail
        println("CLOSE3")
//        RealmInterop.realm_close(frozenRealm) // Closing a cloned version does not fail
        RealmInterop.realm_close(frozenRealm2)
        println("RELEASE")
//        RealmInterop.realm_release(frozenRealm)

        println("GET")
        val intValue = getterScope {
            realm_get_value(sampleFrozenObject!!, intPropertyKey)
            realm_get_value(sampleFrozenObject2!!, intPropertyKey)
        }.getLong()
        println("RELEASE_OBJECT")
        RealmInterop.realm_release(sampleFrozenObject!!)


    }

    // Access object after close of version - FAILS
    // Access object after close of cloned version - FAILS
    // Access object after release of version -
    // Duplicate close on realm_t on same version

    // Is the semantics of realm_freeze and realm_clone the same??

    // Do we need to clean up the resources of every object/query/result
}
