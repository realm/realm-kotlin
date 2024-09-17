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

import io.realm.kotlin.dynamic.DynamicRealm
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.internal.BaseRealmImpl
import io.realm.kotlin.internal.FrozenRealmReferenceImpl
import io.realm.kotlin.internal.InternalConfiguration
import io.realm.kotlin.internal.RealmReference
import io.realm.kotlin.internal.interop.FrozenRealmPointer
import io.realm.kotlin.internal.query.ObjectQuery
import io.realm.kotlin.internal.schema.RealmSchemaImpl
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.schema.RealmSchema

internal open class DynamicRealmImpl(
    configuration: InternalConfiguration,
    dbPointer: FrozenRealmPointer
) : BaseRealmImpl(configuration), DynamicRealm {

    override val realmReference: RealmReference = FrozenRealmReferenceImpl(this, dbPointer)

    override fun query(
        className: String,
        query: String,
        vararg args: Any?
    ): RealmQuery<DynamicRealmObject> {
        return ObjectQuery(
            realmReference,
            realmReference.schemaMetadata.getOrThrow(className).classKey,
            DynamicRealmObject::class,
            configuration.mediator,
            query,
            args
        )
    }

    // FIXME Currently constructs a new instance on each invocation. We could cache this pr. schema
    //  update, but requires that we initialize it all on the actual schema update to allow freezing
    //  it. If we make the schema backed by the actual realm_class_info_t/realm_property_info_t
    //  initialization it would probably be acceptable to initialize on schema updates
    override fun schema(): RealmSchema {
        return RealmSchemaImpl.fromDynamicRealm(realmReference.dbPointer)
    }
}
