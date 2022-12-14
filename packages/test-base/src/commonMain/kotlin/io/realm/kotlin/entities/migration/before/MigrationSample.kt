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

package io.realm.kotlin.entities.migration.before

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PersistedName

@Suppress("MagicNumber")
class MigrationSample : RealmObject {
    var firstName: String = "First"
    var lastName: String = "Last"
    var property: String = "Realm"
    var type: Int = 42
}

class PersistedNameChangeMigrationSample : RealmObject {
    @PersistedName("oldPersistedName")
    var unchangedPublicName = "Realm"
}

class PublicNameChangeMigrationSample : RealmObject {
    @PersistedName("unchangedPersistedName")
    var oldPublicName = "Realm"
}
