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
import io.realm.get
import io.realm.internal.asDynamicRealm
import io.realm.observe
import io.realm.query.RealmQuery
import io.realm.schema.ListPropertyType
import io.realm.schema.RealmPropertyType
import io.realm.schema.RealmStorageType
import io.realm.schema.ValuePropertyType
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
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            RealmConfiguration.Builder(schema = setOf(Sample::class))
                .path("$tmpDir/default.realm").build()

        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    // FIXME Should maybe go when all other tests are in place
    @Test
    fun dynamicRealm_smoketest() {
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
    }

    @Test
    fun type() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        val dynamicRealm = realm.asDynamicRealm()
        val dynamicSample = dynamicRealm.query("Sample").find().first()
        assertEquals("Sample", dynamicSample.type)
    }

    @Test
    fun get_allTypes() {
        // Entitify with default values
        val expectedSample = Sample()
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        val dynamicRealm = realm.asDynamicRealm()
        val dynamicSample = dynamicRealm.query("Sample").find().first()

        val schema = dynamicRealm.schema()
        val sampleDescriptor = schema["Sample"]!!
        for (property in sampleDescriptor.properties) {
            val type: RealmPropertyType = property.type
            when (type) {
                is ValuePropertyType -> {
                    when(type.storageType) {
                        RealmStorageType.BOOL -> {
                            assertEquals(true, dynamicSample.get(property.name))
                            assertEquals(true, dynamicSample.get<Boolean>(property.name))
                            assertEquals(true, dynamicSample.get(property.name, type.storageType.kClass))
                        }
                        RealmStorageType.INT -> {
                            val expectedValue: Long = when(property.name) {
                                "byteField" -> expectedSample.byteField.toLong()
                                "charField" -> expectedSample.charField.code.toLong()
                                "shortField" -> expectedSample.shortField.toLong()
                                "intField" -> expectedSample.intField.toLong()
                                "longField" -> expectedSample.longField
                                else -> error("Unexpected integral field ${property.name}")
                            }
                            assertEquals(expectedValue, dynamicSample.get(property.name))
                            assertEquals(expectedValue, dynamicSample.get<Long>(property.name))
                            assertEquals(expectedValue, dynamicSample.get(property.name, type.storageType.kClass))

                        }
                        RealmStorageType.STRING -> {
                            assertEquals(expectedSample.stringField, dynamicSample.get(property.name))
                            assertEquals(expectedSample.stringField, dynamicSample.get<String>(property.name))
                            assertEquals(expectedSample.stringField, dynamicSample.get(property.name, type.storageType.kClass))
                        }
                        RealmStorageType.OBJECT -> {
                            // Return io.realm.internal.interop.Link@752184f
                            // assertNull(dynamicSample.get(property.name))
                            assertEquals(expectedSample.stringField, dynamicSample.get<DynamicRealmObject>(property.name)?.get("stringField"))
                            // Not working as `RealmObject` doesn't have `get`
                            // assertEquals(expectedSample.stringField, dynamicSample.get<RealmObject>(property.name)?.get("stringField"))
                            assertEquals(expectedSample.stringField, dynamicSample.get<DynamicRealmObject>(property.name)?.get("stringField"))
                            // Not working as `type.storagetype.kClass` is RealmObject
                            // assertEquals(expectedSample.stringField, dynamicSample.get(property.name, type.storageType.kClass)?.get<String>("stringField"))
                            assertEquals(expectedSample.stringField, dynamicSample.get(property.name, DynamicRealmObject::class)?.get("stringField"))
                        }
                        RealmStorageType.FLOAT -> {
                            assertEquals(expectedSample.floatField, dynamicSample.get(property.name))
                            assertEquals(expectedSample.floatField, dynamicSample.get<Float>(property.name))
                            assertEquals(expectedSample.floatField, dynamicSample.get(property.name, type.storageType.kClass))

                        }
                        RealmStorageType.DOUBLE -> {
                            assertEquals(expectedSample.doubleField, dynamicSample.get(property.name))
                            assertEquals(expectedSample.doubleField, dynamicSample.get<Double>(property.name))
                            assertEquals(expectedSample.doubleField, dynamicSample.get(property.name, type.storageType.kClass))
                        }
                        RealmStorageType.TIMESTAMP -> {
                            // Fails with
                            // expected:<RealmInstant(epochSeconds=100, nanosecondsOfSecond=1000)> but was:<TimestampImpl(seconds=100, nanoSeconds=1000)>
                            // assertEquals(expectedSample.timestampField, dynamicSample.get(property.name))
                            // assertEquals(expectedSample.timestampField, dynamicSample.get<RealmInstant>(property.name))
                            // assertEquals(expectedSample.timestampField, dynamicSample.get(property.name, type.storageType.kClass))
                        }
                        else -> Unit
                    }
                }
                is ListPropertyType -> {
                    when(type.storageType) {
//                        RealmStorageType.BOOL -> TODO()
//                        RealmStorageType.INT -> TODO()
//                        RealmStorageType.STRING -> TODO()
//                        RealmStorageType.OBJECT -> TODO()
//                        RealmStorageType.FLOAT -> TODO()
//                        RealmStorageType.DOUBLE -> TODO()
//                        RealmStorageType.TIMESTAMP -> TODO()
                        else -> Unit
                    }

                }
            }
        }
    }

    @Test
    fun get_unknownNameThrows() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        val dynamicRealm = realm.asDynamicRealm()
        val dynamicSample = dynamicRealm.query("Sample").find().first()
        assertFailsWith<IllegalArgumentException> {
            dynamicSample.get<String>("UNKNOWN_FIELD")
        }.let { it.message.contentEquals("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_FIELD'") }
    }

    @Test
    fun get_wrongTypeThrows() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        val dynamicRealm = realm.asDynamicRealm()
        val dynamicSample = dynamicRealm.query("Sample").find().first()
        assertFailsWith<ClassCastException> {
            dynamicSample.get<Long>("stringField")
        }.let { it.message.contentEquals("java.lang.String cannot be cast to java.lang.Long") }
    }

    @Test
    fun observeThrows() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        val dynamicRealm = realm.asDynamicRealm()
        val query: RealmQuery<out DynamicRealmObject> = dynamicRealm.query(Sample::class.simpleName!!)
        val dynamicRealmObject: DynamicRealmObject = query.first().find()!!

        // FIXME Is this the right exception?
        assertFailsWith<NotImplementedError> {
            dynamicRealmObject.observe()
        }
    }

    @Test
    fun accessAfterCloseThrows() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        val dynamicRealm = realm.asDynamicRealm()
        // dynamic object query
        val query: RealmQuery<out DynamicRealmObject> = dynamicRealm.query(Sample::class.simpleName!!)
        val first: DynamicRealmObject = query.first().find()!!

        realm.close()

        assertFailsWith<IllegalStateException> {
            first.get<DynamicRealmObject>("stringField")
        }.run {
            // FIXME Seems like message for accessing objects on closed realm is wrong
            assertTrue { message!!.contains("Cannot perform this operation on an invalid/deleted object") }
        }
    }
}
