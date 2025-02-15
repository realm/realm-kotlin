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

package io.realm.kotlin.internal.dynamic

import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.internal.RealmObjectHelper
import io.realm.kotlin.internal.RealmObjectInternal
import io.realm.kotlin.internal.RealmObjectReference
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.BaseRealmObject
import kotlin.reflect.KType

public open class DynamicRealmObjectImpl : DynamicRealmObject, RealmObjectInternal {
    override val type: String
        get() = this.`io_realm_kotlin_objectReference`!!.className

    // This should never be null after initialization of a dynamic object, but we currently cannot
    // represent that in the type system as we one some code paths construct the Kotlin object
    // before having the realm object reference
    override var `io_realm_kotlin_objectReference`: RealmObjectReference<out BaseRealmObject>? = null

    override fun <T> get(propertyName: String, type: KType): T {
        return RealmObjectHelper.dynamicGetFromKType(
            obj = this.`io_realm_kotlin_objectReference`!!,
            propertyName = propertyName,
            type = type
        )
    }

    override fun getBacklinks(propertyName: String): RealmResults<out DynamicRealmObject> {
        return RealmObjectHelper.dynamicGetBacklinks(`io_realm_kotlin_objectReference`!!, propertyName)
    }
}
