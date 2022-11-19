/*
 * Copyright 2022 JetBrains s.r.o.
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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.example.kmmsample.androidApp

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PersistedName

// Smoke testing that we can access fields using `@PersistedName` after obfuscation

class PersistedNameTest() : RealmObject {

    constructor(stringField: String) : this() {
        publicNameStringField = stringField
    }

    @PersistedName("persistedNameStringField")
    var publicNameStringField: String = ""

    companion object {
        lateinit var realm: Realm

        fun testQueryByPersistedAndPublicName() {
            try {
                val configuration = RealmConfiguration
                    .Builder(schema = setOf(PersistedNameTest::class))
                    .name("persistedName-test.realm")
                    .deleteRealmIfMigrationNeeded()
                    .build()
                realm = Realm.open(configuration)

                // Add 2 objects to the realm
                realm.writeBlocking {
                    delete(query<PersistedNameTest>())
                    copyToRealm(PersistedNameTest("Value1"))
                    copyToRealm(PersistedNameTest("Value2"))
                }

                // Query by persisted name
                realm.query<PersistedNameTest>("persistedNameStringField = $0", "Value1")
                    .find()
                    .single()
                    .run {
                        if (this.publicNameStringField != "Value1") {
                            throw java.lang.RuntimeException("PersistedNameTest failed: Cannot query by persisted name.")
                        }
                    }

                // Query by public name
                realm.query<PersistedNameTest>("publicNameStringField = $0", "Value1")
                    .find()
                    .single()
                    .run {
                        if (this.publicNameStringField != "Value1") {
                            throw java.lang.RuntimeException("PersistedNameTest failed: Cannot query by public name.")
                        }
                    }
            } finally {
                if (this::realm.isInitialized && !realm.isClosed()) {
                    realm.close()
                }
            }
        }
    }
}
