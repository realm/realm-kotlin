/*
 * Copyright 2021 Realm Inc.
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

package io.realm.internal

import io.realm.DynamicRealm
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.schema.RealmClassImpl
import io.realm.internal.schema.RealmPropertyImpl
import io.realm.schema.RealmSchema

class DynamicRealmImpl(configuration: InternalRealmConfiguration, val dbPointer: NativePointer) : DynamicRealm,
    BaseRealmImpl(
        configuration, dbPointer
    ) {
    override fun schema(schema: RealmSchema, version: Long) {
        val schemaPointer = schema.classes.map {
            val realmClassImpl = it as RealmClassImpl
            realmClassImpl.toCoreClass() to realmClassImpl.properties.map { (it as RealmPropertyImpl).toCoreProperty() }
        }.let{
            RealmInterop.realm_schema_new(it)
        }
        RealmInterop.realm_update_schema(dbPointer, schemaPointer, version)
    }
}
