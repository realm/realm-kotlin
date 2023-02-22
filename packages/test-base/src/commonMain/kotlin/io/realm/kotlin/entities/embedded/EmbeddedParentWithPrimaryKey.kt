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

package io.realm.kotlin.entities.embedded

import io.realm.kotlin.ext.realmDictionaryOf
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

// Convenience set of classes to ease inclusion of classes referenced by this top level model node
val embeddedSchemaWithPrimaryKey =
    setOf(EmbeddedParentWithPrimaryKey::class, EmbeddedChildWithPrimaryKeyParent::class)

class EmbeddedParentWithPrimaryKey : RealmObject {
    @PrimaryKey
    var id: Int? = null
    var name: String? = "Realm"
    var child: EmbeddedChildWithPrimaryKeyParent? = null
    var childrenList: RealmList<EmbeddedChildWithPrimaryKeyParent> = realmListOf()
    var childrenDictionary: RealmDictionary<EmbeddedChildWithPrimaryKeyParent?> =
        realmDictionaryOf()
}
