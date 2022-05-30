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

import io.realm.kotlin.RealmList
import io.realm.kotlin.RealmObject
import io.realm.kotlin.annotations.PrimaryKey
import io.realm.kotlin.realmListOf

// Convenience set of classes to ease inclusion of classes referenced by this top level model node
val embeddedSchemaWithPrimaryKey = setOf(EmbeddedParentWithPrimaryKey::class, EmbeddedChild::class, EmbeddedInnerChild::class)

class EmbeddedParentWithPrimaryKey : RealmObject {
    @PrimaryKey
    var id: String? = null
    var child: EmbeddedChild? = null
    var childList: RealmList<EmbeddedChild> = realmListOf()
}
