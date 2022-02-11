@file:Suppress("invisible_member", "invisible_reference")
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

import io.realm.DynamicRealmObject
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmList
import io.realm.entities.Sample
import io.realm.entities.schema.SchemaVariations
import io.realm.get
import io.realm.internal.asDynamicRealm
import io.realm.observe
import io.realm.query.RealmQuery
import io.realm.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DynamicRealmObjectTests {

    private lateinit var tmpDir: String

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
    }

    @AfterTest
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
    }

    // Dynamic readable
    // - All types of objects get
    // - Type
    // - Fields
    //

    // Public constructor from managed object?
    // constructor_deletedObjectThrows
    // constructor_unmanagedObjectThrows

    // typedGettersAndSetters
    // setter_null
    // setter_nullOnRequiredFieldsThrows
    // typedSetter_null
    // setObject_differentType
    // setNull_changePrimaryKeyThrows
    // typedGetter_illegalFieldNameThrows
    // typedGetter_wrongUnderlyingTypeThrows
    // typedSetter_illegalFieldNameThrows
    // typedSetter_wrongUnderlyingTypeThrows
    // typedSetter_changePrimaryKeyThrows

    @Test
    fun dynamicRealm() {
        val configuration =
            RealmConfiguration.Builder(schema = setOf(SchemaVariations::class, Sample::class))
                .path("$tmpDir/default.realm").build()

        val realm = Realm.open(configuration)
        realm.writeBlocking {
            copyToRealm(Sample()).apply {
                stringField = "Parent"
                child = Sample().apply { stringField = "Child" }
                stringListField.add("STRINGLISTELEMENT")
                objectListField.add(Sample().apply { stringField = "SAMPLELISTELEMENT" })
                objectListField[0]
            }
        }

        val dynamicRealm = realm.asDynamicRealm()

        // dynamic object query
        val query: RealmQuery<out DynamicRealmObject> = dynamicRealm.query(Sample::class.simpleName!!)
        val first: DynamicRealmObject? = query.first().find()
        assertNotNull(first)

        // type
        assertEquals("Sample", first.type)

        // get string
        val actual = first.get("stringField", String::class)
        assertEquals("Parent", actual)

        // get object
        val dynamicChild: DynamicRealmObject? = first.get("child")
        assertNotNull(dynamicChild)
        assertEquals("Child", dynamicChild.get("stringField"))

        // string list
        // FIXME Doesn't verify generic type
        val stringList1: RealmList<String>? = first.get("stringListField")
        // FIXME Do we need separate getList method
//        val get: RealmList<String>? = first.getList("stringListField", String::class)
//        val stringList2: RealmList<String>? = get

        assertEquals("STRINGLISTELEMENT", stringList1!![0])
        // FIXME Is it acceptable that this is a mutable list?
        assertFailsWith<IllegalStateException> {
            stringList1.add("another element")
        }

        // object list
        val objectList: RealmList<DynamicRealmObject>? = first.get("objectListField")
        val dynamicRealmObject = objectList!![0]
        assertEquals("SAMPLELISTELEMENT", dynamicRealmObject.get("stringField"))

        // FIXME Is this the right exception?
        assertFailsWith<NotImplementedError> {
            dynamicRealmObject.observe()
        }

        realm.close()

        assertFailsWith<IllegalStateException> {
            first.get<DynamicRealmObject>("stringField")
        }.run {
            // FIXME Seems like message for accessing objects on closed realm is wrong
            assertTrue { message!!.contains("Cannot perform this operation on an invalid/deleted object") }
        }
    }

}
