/*
 * Copyright 2024 Realm Inc.
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

package io.realm.kotlin.test.mongodb.jvm

import io.realm.kotlin.test.mongodb.common.FLEXIBLE_SYNC_SCHEMA
import io.realm.kotlin.test.mongodb.common.FLEXIBLE_SYNC_SCHEMA_COUNT
import io.realm.kotlin.types.RealmObject
import kotlin.reflect.full.isSubclassOf
import kotlin.test.Test
import kotlin.test.assertEquals

class FlexibleSyncSchemaTests {

    @Test
    fun flexibleSyncSchemaCount() {
        assertEquals(
            FLEXIBLE_SYNC_SCHEMA_COUNT,
            FLEXIBLE_SYNC_SCHEMA.filter { it.isSubclassOf(RealmObject::class) }.size
        )
    }
}
