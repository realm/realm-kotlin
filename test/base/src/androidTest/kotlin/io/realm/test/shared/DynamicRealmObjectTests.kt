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

// FIXME Should we put these (and all DynamicX) in it's own package
import io.realm.DynamicRealmObject
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmInstant
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.entities.Sample
import io.realm.get
import io.realm.getList
import io.realm.getListOfNullable
import io.realm.getNullable
import io.realm.internal.asDynamicRealm
import io.realm.observe
import io.realm.query.RealmQuery
import io.realm.schema.ListPropertyType
import io.realm.schema.RealmStorageType
import io.realm.schema.ValuePropertyType
import io.realm.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

private val defaultSample = Sample()

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
                nullableObject = Sample().apply { stringField = "Child" }
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
        val dynamicChild: DynamicRealmObject = first.get("nullableObject")
        assertNotNull(dynamicChild)
        assertEquals("Child", dynamicChild.get("stringField"))
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
        val expectedSample = testSample()
        realm.writeBlocking {
            copyToRealm(expectedSample)
        }
        val dynamicRealm = realm.asDynamicRealm()
        val dynamicSample = dynamicRealm.query("Sample").find().first()

        val schema = dynamicRealm.schema()
        val sampleDescriptor = schema["Sample"]!!

        val properties = sampleDescriptor.properties//.filter { it.type is ValuePropertyType && it.isNullable }
        for (property in properties) {
            val name: String = property.name
            val type = property.type
            when (type) {
                is ValuePropertyType -> {
                    if (type.isNullable) {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                assertEquals(null, dynamicSample.getNullable<Boolean>(name))
                                assertEquals(null, dynamicSample.getNullable(name, type.storageType.kClass))
                            }
                            RealmStorageType.INT -> {
                                assertEquals(null, dynamicSample.getNullable<Long>(property.name))
                                assertEquals(null, dynamicSample.getNullable(property.name, type.storageType.kClass))
                            }
                            RealmStorageType.STRING -> {
                                assertEquals(null, dynamicSample.getNullable<String>(property.name))
                                assertEquals(null, dynamicSample.getNullable(property.name, type.storageType.kClass))
                            }
                            RealmStorageType.OBJECT -> {
                                assertEquals(null, dynamicSample.getNullable<RealmObject>(property.name))
                                assertEquals(null, dynamicSample.getNullable<DynamicRealmObject>(property.name))
                                assertEquals(null, dynamicSample.getNullable(property.name, DynamicRealmObject::class))
                            }
                            RealmStorageType.FLOAT -> {
                                assertEquals(null, dynamicSample.getNullable<Float>(property.name))
                                assertEquals(null, dynamicSample.getNullable(property.name, type.storageType.kClass))
                            }
                            RealmStorageType.DOUBLE -> {
                                assertEquals(null, dynamicSample.getNullable<Double>(property.name))
                                assertEquals(null, dynamicSample.getNullable(property.name, type.storageType.kClass))
                            }
                            RealmStorageType.TIMESTAMP -> {
                                assertEquals(null, dynamicSample.getNullable<RealmInstant>(property.name))
                                assertEquals(null, dynamicSample.getNullable(property.name, type.storageType.kClass))
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                assertEquals(expectedSample.booleanField, dynamicSample.get(name))
                                assertEquals(expectedSample.booleanField, dynamicSample.get<Boolean>(name))
                                assertEquals(expectedSample.booleanField, dynamicSample.get(name, type.storageType.kClass))
                            }
                            RealmStorageType.INT -> {
                                val expectedValue: Long = when (property.name) {
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
                                assertEquals(expectedSample.timestampField, dynamicSample.get(property.name))
                                assertEquals(expectedSample.timestampField, dynamicSample.get<RealmInstant>(property.name))
                                assertEquals(expectedSample.timestampField, dynamicSample.get(property.name, type.storageType.kClass))
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    }
                }
                is ListPropertyType -> {
                    if (type.isNullable) {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                assertEquals(null, dynamicSample.getListOfNullable<Boolean>(property.name)[0])
                                assertEquals(null, dynamicSample.getListOfNullable(property.name, Boolean::class)[0])
                            }
                            RealmStorageType.INT -> {
                                assertEquals(null, dynamicSample.getListOfNullable<Long>(property.name)[0])
                                assertEquals(null, dynamicSample.getListOfNullable(property.name, Long::class)[0])
                            }
                            RealmStorageType.STRING -> {
                                assertEquals(null, dynamicSample.getListOfNullable<String>(property.name)[0])
                                assertEquals(null, dynamicSample.getListOfNullable(property.name, String::class)[0])
                            }
                            RealmStorageType.FLOAT -> {
                                assertEquals(null, dynamicSample.getListOfNullable<Float>(property.name)[0])
                                assertEquals(null, dynamicSample.getListOfNullable(property.name, Float::class)[0])
                            }
                            RealmStorageType.DOUBLE -> {
                                assertEquals(null, dynamicSample.getListOfNullable<Double>(property.name)[0])
                                assertEquals(null, dynamicSample.getListOfNullable(property.name, Double::class)[0])
                            }
                            RealmStorageType.TIMESTAMP -> {
                                assertEquals(null, dynamicSample.getListOfNullable<RealmInstant>(property.name)[0])
                                assertEquals(null, dynamicSample.getListOfNullable(property.name, RealmInstant::class)[0])
                            }
                            RealmStorageType.OBJECT -> {
                                assertEquals(null, dynamicSample.getListOfNullable<DynamicRealmObject>(property.name)[0])
                                assertEquals(null, dynamicSample.getListOfNullable(property.name, DynamicRealmObject::class)[0])
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                val expectedValue = defaultSample.booleanField
                                assertEquals(expectedValue, dynamicSample.getList<Boolean>(property.name)[0])
                                assertEquals(expectedValue, dynamicSample.getList(property.name, Boolean::class)[0])
                            }
                            RealmStorageType.INT -> {
                                val expectedValue: Long? = when (property.name) {
                                    "byteListField" -> defaultSample.byteField.toLong()
                                    "charListField" -> defaultSample.charField.code.toLong()
                                    "shortListField" -> defaultSample.shortField.toLong()
                                    "intListField" -> defaultSample.intField.toLong()
                                    "longListField" -> defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                assertEquals(expectedValue, dynamicSample.getList<Long>(property.name)[0])
                                assertEquals(expectedValue, dynamicSample.getList(property.name, Long::class)[0])
                            }
                            RealmStorageType.STRING -> {
                                val expectedValue = defaultSample.stringField
                                assertEquals(expectedValue, dynamicSample.getList<String>(property.name)[0])
                                assertEquals(expectedValue, dynamicSample.getList(property.name, String::class)[0])
                            }
                            RealmStorageType.FLOAT -> {
                                val expectedValue = defaultSample.floatField
                                assertEquals(expectedValue, dynamicSample.getList<Float>(property.name)[0])
                                assertEquals(expectedValue, dynamicSample.getList(property.name, Float::class)[0])
                            }
                            RealmStorageType.DOUBLE -> {
                                val expectedValue = defaultSample.doubleField
                                assertEquals(expectedValue, dynamicSample.getList<Double>(property.name)[0])
                                assertEquals(expectedValue, dynamicSample.getList(property.name, Double::class)[0])
                            }
                            RealmStorageType.TIMESTAMP -> {
                                val expectedValue = defaultSample.timestampField
                                assertEquals(expectedValue, dynamicSample.getList<RealmInstant>(property.name)[0])
                                assertEquals(expectedValue, dynamicSample.getList(property.name, RealmInstant::class)[0])
                            }
                            RealmStorageType.OBJECT -> {
                                val expectedValue = defaultSample.stringField
                                assertEquals(expectedValue, dynamicSample.getList<DynamicRealmObject>(property.name)[0].get("stringField"))
                                assertEquals(expectedValue, dynamicSample.getList(property.name, DynamicRealmObject::class)[0].get("stringField"))
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    }
                }
            }
            // FIXME There is currently nothing that assert that we have tested all type
            // assertTrue("Untested types: $untested") { untested.isEmpty() }
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
        }.run { assertEquals("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_FIELD'", message) }
    }


    @Test
    fun get_wrongTypeThrows() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        val dynamicRealm = realm.asDynamicRealm()
        val dynamicSample = dynamicRealm.query("Sample").find().first()
        assertFailsWith<ClassCastException> {
            val value = dynamicSample.get<Long>("stringField")
        }.run { assertEquals("java.lang.String cannot be cast to java.lang.Number", message) }
    }

    @Test
    fun get_listThrows() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        val dynamicRealm = realm.asDynamicRealm()
        val dynamicSample = dynamicRealm.query("Sample").find().first()
        assertFailsWith<IllegalArgumentException> {
            dynamicSample.get<RealmList<*>>("stringListField")
        }.run { assertEquals ( "Cannot retrieve RealmList through 'get(...)', use getList(...) instead: stringListField", message) }

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
            assertEquals("Cannot perform this operation on an invalid/deleted object", message)
        }
    }

    private fun testSample(): Sample {
        return Sample().apply {
            booleanListField.add(defaultSample.booleanField)
            byteListField.add(defaultSample.byteField)
            charListField.add(defaultSample.charField)
            shortListField.add(defaultSample.shortField)
            intListField.add(defaultSample.intField)
            longListField.add(defaultSample.longField)
            floatListField.add(defaultSample.floatField)
            doubleListField.add(defaultSample.doubleField)
            stringListField.add(defaultSample.stringField)
            objectListField.add(this)
            timestampListField.add(defaultSample.timestampField)

            nullableStringListField.add(null)
            nullableByteListField.add(null)
            nullableCharListField.add(null)
            nullableShortListField.add(null)
            nullableIntListField.add(null)
            nullableLongListField.add(null)
            nullableBooleanListField.add(null)
            nullableFloatListField.add(null)
            nullableDoubleListField.add(null)
            nullableTimestampListField.add(null)
        }
    }
}
