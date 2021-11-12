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

package io.realm.internal.schema

import io.realm.schema.MutableRealmClass
import io.realm.schema.MutableRealmProperty
import io.realm.schema.RealmClass
import io.realm.schema.RealmProperty

data class RealmClassImpl(
    override var name: String,
    override var embedded: Boolean,
    override val properties: MutableSet<MutableRealmProperty>
) : MutableRealmClass {

    override fun get(key: String): MutableRealmProperty? = properties.firstOrNull { it.name == key }
    override fun primaryKey(): MutableRealmProperty? = properties.firstOrNull { it.primaryKey }

}
