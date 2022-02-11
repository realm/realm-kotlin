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

package io.realm.test.shared

import io.realm.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class DynamicMutableRealmObjectTests {

    private lateinit var tmpDir: String

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
    }

    @AfterTest
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun x() {

    }

    // Dynamic readable
    // - All types of objects get
    // - Type
    // - Fields
    //

//    @Test
//    fun create_throwsWithPrimaryKey() {
//        migration(
//            initialSchema = setOf(Sample::class),
//            migratedSchema = setOf(SampleMigrated::class),
//            migration = { (oldRealm, newRealm) ->
//                newRealm.createObject("SampleMigrated", "NON_PRIMARY_KEY_CLASS")
//            }
//        ).close()
//    }
//
//
//    @Test
//    fun createPrimaryKey_absentPrimaryKey() {
//        migration(
//            initialSchema = setOf(Sample::class),
//            migratedSchema = setOf(PrimaryKeyString::class),
//            migration = { (oldRealm, newRealm) ->
//                newRealm.createObject("PrimaryKeyString")
//            }
//        ).close()
//    }
//
//    @Test
//    fun createPrimaryKey_throwsWithWrongPrimaryKeyType() {
//        migration(
//            initialSchema = setOf(Sample::class),
//            migratedSchema = setOf(PrimaryKeyString::class),
//            migration = { (oldRealm, newRealm) ->
//                newRealm.createObject("PrimaryKeyString", 42)
//            }
//        ).close()
//    }
//
//    @Test
//    fun createPrimaryKey_nullablePrimaryKey() {
//        migration(
//            initialSchema = setOf(Sample::class),
//            migratedSchema = setOf(PrimaryKeyStringNullable::class),
//            migration = { (oldRealm, newRealm) ->
//                newRealm.createObject("PrimaryKeyStringNullable", null)
//            }
//        ).close()
//    }

    // createObject_withNullStringPrimaryKey
    // createObject_withNullBytePrimaryKey
    // createObject_withNullShortPrimaryKey
    // ... all other types
    // createObject_illegalPrimaryKeyValue
    // createObject_absentPrimaryKeyThrows

}
