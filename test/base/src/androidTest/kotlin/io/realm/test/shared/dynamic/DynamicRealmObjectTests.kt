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

package io.realm.test.shared.dynamic

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmInstant
import io.realm.dynamic.DynamicRealmObject
import io.realm.dynamic.getNullableValue
import io.realm.dynamic.getNullableValueList
import io.realm.dynamic.getValue
import io.realm.dynamic.getValueList
import io.realm.entities.Sample
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
import kotlin.test.assertTrue

val defaultSample = Sample()

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
        val actual = first.getValue("stringField", String::class)
        assertEquals("Parent", actual)

        // get object
        val dynamicChild: DynamicRealmObject? = first.getObject("nullableObject")
        assertNotNull(dynamicChild)
        assertEquals("Child", dynamicChild.getValue("stringField"))
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
    @Suppress("LongMethod", "ComplexMethod", "NestedBlockDepth")
    fun get_allTypes() {
        val expectedSample = testSample()
        realm.writeBlocking {
            copyToRealm(expectedSample)
        }
        val dynamicRealm = realm.asDynamicRealm()
        val dynamicSample = dynamicRealm.query("Sample").find().first()

        val schema = dynamicRealm.schema()
        val sampleDescriptor = schema["Sample"]!!

        val properties = sampleDescriptor.properties
        for (property in properties) {
            val name: String = property.name
            val type = property.type
            when (type) {
                is ValuePropertyType -> {
                    if (type.isNullable) {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                assertEquals(null, dynamicSample.getNullableValue<Boolean>(name))
                                assertEquals(null, dynamicSample.getNullableValue(name, type.storageType.kClass))
                            }
                            RealmStorageType.INT -> {
                                assertEquals(null, dynamicSample.getNullableValue<Long>(property.name))
                                assertEquals(null, dynamicSample.getNullableValue(property.name, type.storageType.kClass))
                            }
                            RealmStorageType.STRING -> {
                                assertEquals(null, dynamicSample.getNullableValue<String>(property.name))
                                assertEquals(null, dynamicSample.getNullableValue(property.name, type.storageType.kClass))
                            }
                            RealmStorageType.OBJECT -> {
                                assertEquals(null, dynamicSample.getObject(property.name))
                            }
                            RealmStorageType.FLOAT -> {
                                assertEquals(null, dynamicSample.getNullableValue<Float>(property.name))
                                assertEquals(null, dynamicSample.getNullableValue(property.name, type.storageType.kClass))
                            }
                            RealmStorageType.DOUBLE -> {
                                assertEquals(null, dynamicSample.getNullableValue<Double>(property.name))
                                assertEquals(null, dynamicSample.getNullableValue(property.name, type.storageType.kClass))
                            }
                            RealmStorageType.TIMESTAMP -> {
                                assertEquals(null, dynamicSample.getNullableValue<RealmInstant>(property.name))
                                assertEquals(null, dynamicSample.getNullableValue(property.name, type.storageType.kClass))
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                assertEquals(expectedSample.booleanField, dynamicSample.getValue(name))
                                assertEquals(expectedSample.booleanField, dynamicSample.getValue<Boolean>(name))
                                assertEquals(expectedSample.booleanField, dynamicSample.getValue(property.name, type.storageType.kClass))
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
                                assertEquals(expectedValue, dynamicSample.getValue(property.name))
                                assertEquals(expectedValue, dynamicSample.getValue<Long>(property.name))
                                assertEquals(expectedValue, dynamicSample.getValue(property.name, type.storageType.kClass))
                            }
                            RealmStorageType.STRING -> {
                                assertEquals(expectedSample.stringField, dynamicSample.getValue(property.name))
                                assertEquals(expectedSample.stringField, dynamicSample.getValue<String>(property.name))
                                assertEquals(expectedSample.stringField, dynamicSample.getValue(property.name, type.storageType.kClass))
                            }
                            RealmStorageType.FLOAT -> {
                                assertEquals(expectedSample.floatField, dynamicSample.getValue(property.name))
                                assertEquals(expectedSample.floatField, dynamicSample.getValue<Float>(property.name))
                                assertEquals(expectedSample.floatField, dynamicSample.getValue(property.name, type.storageType.kClass))
                            }
                            RealmStorageType.DOUBLE -> {
                                assertEquals(expectedSample.doubleField, dynamicSample.getValue(property.name))
                                assertEquals(expectedSample.doubleField, dynamicSample.getValue<Double>(property.name))
                                assertEquals(expectedSample.doubleField, dynamicSample.getValue(property.name, type.storageType.kClass))
                            }
                            RealmStorageType.TIMESTAMP -> {
                                assertEquals(expectedSample.timestampField, dynamicSample.getValue(property.name))
                                assertEquals(expectedSample.timestampField, dynamicSample.getValue<RealmInstant>(property.name))
                                assertEquals(expectedSample.timestampField, dynamicSample.getValue(property.name, type.storageType.kClass))
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    }
                }
                is ListPropertyType -> {
                    if (type.isNullable) {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                assertEquals(null, dynamicSample.getNullableValueList<Boolean>(property.name)[0])
                                assertEquals(null, dynamicSample.getNullableValueList(property.name, Boolean::class)[0])
                            }
                            RealmStorageType.INT -> {
                                assertEquals(null, dynamicSample.getNullableValueList<Long>(property.name)[0])
                                assertEquals(null, dynamicSample.getNullableValueList(property.name, Long::class)[0])
                            }
                            RealmStorageType.STRING -> {
                                assertEquals(null, dynamicSample.getNullableValueList<String>(property.name)[0])
                                assertEquals(null, dynamicSample.getNullableValueList(property.name, String::class)[0])
                            }
                            RealmStorageType.FLOAT -> {
                                assertEquals(null, dynamicSample.getNullableValueList<Float>(property.name)[0])
                                assertEquals(null, dynamicSample.getNullableValueList(property.name, Float::class)[0])
                            }
                            RealmStorageType.DOUBLE -> {
                                assertEquals(null, dynamicSample.getNullableValueList<Double>(property.name)[0])
                                assertEquals(null, dynamicSample.getNullableValueList(property.name, Double::class)[0])
                            }
                            RealmStorageType.TIMESTAMP -> {
                                assertEquals(null, dynamicSample.getNullableValueList<RealmInstant>(property.name)[0])
                                assertEquals(null, dynamicSample.getNullableValueList(property.name, RealmInstant::class)[0])
                            }
                            RealmStorageType.OBJECT -> {
                                assertEquals(null, dynamicSample.getNullableValueList<DynamicRealmObject>(property.name)[0])
                                assertEquals(null, dynamicSample.getNullableValueList(property.name, DynamicRealmObject::class)[0])
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                val expectedValue = defaultSample.booleanField
                                assertEquals(expectedValue, dynamicSample.getValueList<Boolean>(property.name)[0])
                                assertEquals(expectedValue, dynamicSample.getValueList(property.name, Boolean::class)[0])
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
                                assertEquals(expectedValue, dynamicSample.getValueList<Long>(property.name)[0])
                                assertEquals(expectedValue, dynamicSample.getValueList(property.name, Long::class)[0])
                            }
                            RealmStorageType.STRING -> {
                                val expectedValue = defaultSample.stringField
                                assertEquals(expectedValue, dynamicSample.getValueList<String>(property.name)[0])
                                assertEquals(expectedValue, dynamicSample.getValueList(property.name, String::class)[0])
                            }
                            RealmStorageType.FLOAT -> {
                                val expectedValue = defaultSample.floatField
                                assertEquals(expectedValue, dynamicSample.getValueList<Float>(property.name)[0])
                                assertEquals(expectedValue, dynamicSample.getValueList(property.name, Float::class)[0])
                            }
                            RealmStorageType.DOUBLE -> {
                                val expectedValue = defaultSample.doubleField
                                assertEquals(expectedValue, dynamicSample.getValueList<Double>(property.name)[0])
                                assertEquals(expectedValue, dynamicSample.getValueList(property.name, Double::class)[0])
                            }
                            RealmStorageType.TIMESTAMP -> {
                                val expectedValue = defaultSample.timestampField
                                assertEquals(expectedValue, dynamicSample.getValueList<RealmInstant>(property.name)[0])
                                assertEquals(expectedValue, dynamicSample.getValueList(property.name, RealmInstant::class)[0])
                            }
                            RealmStorageType.OBJECT -> {
                                val expectedValue = defaultSample.stringField
                                assertEquals(expectedValue, dynamicSample.getValueList<DynamicRealmObject>(property.name)[0].getValue("stringField"))
                                assertEquals(expectedValue, dynamicSample.getValueList(property.name, DynamicRealmObject::class)[0].getValue("stringField"))
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    }
                }
            }
            // TODO There is currently nothing that assert that we have tested all type
            // assertTrue("Untested types: $untested") { untested.isEmpty() }
        }
    }

    @Test
    fun get_throwsOnUknownName() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        val dynamicRealm = realm.asDynamicRealm()
        val dynamicSample = dynamicRealm.query("Sample").find().first()
        assertFailsWith<IllegalArgumentException> {
            dynamicSample.getValue<String>("UNKNOWN_FIELD")
        }.run { assertEquals("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_FIELD'", message) }
        assertFailsWith<IllegalArgumentException> {
            dynamicSample.getNullableValue<String>("UNKNOWN_FIELD")
        }.run { assertEquals("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_FIELD'", message) }
        assertFailsWith<IllegalArgumentException> {
            dynamicSample.getObject("UNKNOWN_FIELD")
        }.run { assertEquals("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_FIELD'", message) }
        assertFailsWith<IllegalArgumentException> {
            dynamicSample.getValueList<String>("UNKNOWN_FIELD")
        }.run { assertEquals("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_FIELD'", message) }
        assertFailsWith<IllegalArgumentException> {
            dynamicSample.getNullableValueList<String>("UNKNOWN_FIELD")
        }.run { assertEquals("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_FIELD'", message) }
        assertFailsWith<IllegalArgumentException> {
            dynamicSample.getObjectList("UNKNOWN_FIELD")
        }.run { assertEquals("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_FIELD'", message) }
    }

    @Test
    fun getValueVariants_throwsOnWrongTypes() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        val dynamicRealm = realm.asDynamicRealm()
        val dynamicSample = dynamicRealm.query("Sample").find().first()

        // Wrong type
        assertFailsWith<IllegalArgumentException> {
            dynamicSample.getValue<Long>("stringField")
        }.run { assertEquals("Trying to access property 'Sample.stringField' as type: 'class kotlin.Long' but actual schema type is 'class kotlin.String'", message) }
        assertFailsWith<IllegalArgumentException> {
            dynamicSample.getNullableValue<Long>("nullableStringField")
        }.run { assertEquals("Trying to access property 'Sample.nullableStringField' as type: 'class kotlin.Long?' but actual schema type is 'class kotlin.String?'", message) }

        // Wrong nullability
        assertFailsWith<IllegalArgumentException> {
            dynamicSample.getValue<Long>("nullableStringField")
        }.run { assertEquals("Trying to access property 'Sample.nullableStringField' as type: 'class kotlin.Long' but actual schema type is 'class kotlin.String?'", message) }
        assertFailsWith<IllegalArgumentException> {
            dynamicSample.getNullableValueList<Long>("stringField")
        }.run { assertEquals("Trying to access property 'Sample.stringField' as type: 'RealmList<class kotlin.Long?>' but actual schema type is 'class kotlin.String'", message) }

        // Wrong variants
        assertFailsWith<IllegalArgumentException> {
            dynamicSample.getValue<String>("nullableObject")
        }.run { assertEquals("Trying to access property 'Sample.nullableObject' as type: 'class kotlin.String' but actual schema type is 'class io.realm.RealmObject?'", message) }
        assertFailsWith<IllegalArgumentException> {
            dynamicSample.getNullableValue<String>("nullableObject")
        }.run { assertEquals("Trying to access property 'Sample.nullableObject' as type: 'class kotlin.String?' but actual schema type is 'class io.realm.RealmObject?'", message) }

        assertFailsWith<IllegalArgumentException> {
            dynamicSample.getValue<Long>("stringListField")
        }.run { assertEquals("Trying to access property 'Sample.stringListField' as type: 'class kotlin.Long' but actual schema type is 'RealmList<class kotlin.String>'", message) }
        assertFailsWith<IllegalArgumentException> {
            dynamicSample.getNullableValue<Long>("nullableStringListField")
        }.run { assertEquals("Trying to access property 'Sample.nullableStringListField' as type: 'class kotlin.Long?' but actual schema type is 'RealmList<class kotlin.String?>'", message) }
    }

    @Test
    fun getObject_throwsOnWrongTypes() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        val dynamicRealm = realm.asDynamicRealm()
        val dynamicSample = dynamicRealm.query("Sample").find().first()

        // We cannot get wrong mix of types or nullability in the API, so only checking wrong variants
        assertFailsWith<IllegalArgumentException> {
            dynamicSample.getObject("stringField")
        }.run { assertEquals("Trying to access property 'Sample.stringField' as type: 'class io.realm.RealmObject?' but actual schema type is 'class kotlin.String'", message) }
        assertFailsWith<IllegalArgumentException> {
            dynamicSample.getObject("stringListField")
        }.run { assertEquals("Trying to access property 'Sample.stringListField' as type: 'class io.realm.RealmObject?' but actual schema type is 'RealmList<class kotlin.String>'", message) }
    }
    @Test
    fun getListVariants_throwsOnWrongTypes() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        val dynamicRealm = realm.asDynamicRealm()
        val dynamicSample = dynamicRealm.query("Sample").find().first()

        // Wrong type
        assertFailsWith<IllegalArgumentException> {
            dynamicSample.getValueList<Long>("stringListField")
        }.run { assertEquals("Trying to access property 'Sample.stringListField' as type: 'RealmList<class kotlin.Long>' but actual schema type is 'RealmList<class kotlin.String>'", message) }
        assertFailsWith<IllegalArgumentException> {
            dynamicSample.getNullableValueList<Long>("nullableStringListField")
        }.run { assertEquals("Trying to access property 'Sample.nullableStringListField' as type: 'RealmList<class kotlin.Long?>' but actual schema type is 'RealmList<class kotlin.String?>'", message, message) }

        // Wrong nullability
        assertFailsWith<IllegalArgumentException> {
            dynamicSample.getValueList<String>("nullableStringListField")
        }.run { assertEquals("Trying to access property 'Sample.nullableStringListField' as type: 'RealmList<class kotlin.String>' but actual schema type is 'RealmList<class kotlin.String?>'", message, message) }
        assertFailsWith<IllegalArgumentException> {
            dynamicSample.getNullableValueList<String>("stringListField")
        }.run { assertEquals("Trying to access property 'Sample.stringListField' as type: 'RealmList<class kotlin.String?>' but actual schema type is 'RealmList<class kotlin.String>'", message, message) }

        // Wrong variants
        assertFailsWith<IllegalArgumentException> {
            dynamicSample.getValueList<String>("stringField")
        }.run { assertEquals("Trying to access property 'Sample.stringField' as type: 'RealmList<class kotlin.String>' but actual schema type is 'class kotlin.String'", message, message) }
        assertFailsWith<IllegalArgumentException> {
            dynamicSample.getValueList<Long>("nullableObject")
        }.run { assertEquals("Trying to access property 'Sample.nullableObject' as type: 'RealmList<class kotlin.Long>' but actual schema type is 'class io.realm.RealmObject?'", message, message) }
        assertFailsWith<IllegalArgumentException> {
            dynamicSample.getNullableValueList<Long>("nullableStringField")
        }.run { assertEquals("Trying to access property 'Sample.nullableStringField' as type: 'RealmList<class kotlin.Long?>' but actual schema type is 'class kotlin.String?'", message, message) }
        assertFailsWith<IllegalArgumentException> {
            dynamicSample.getObjectList("nullableStringListField")
        }.run { assertEquals("Trying to access property 'Sample.nullableStringListField' as type: 'RealmList<class io.realm.RealmObject>' but actual schema type is 'RealmList<class kotlin.String?>'", message, message) }
    }

    // We don't have an immutable RealmList so verify that we fail in an understandable manner if
    // trying to update the list
    @Test
    fun getValueList_throwsIfModified() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        val dynamicRealm = realm.asDynamicRealm()
        val dynamicSample = dynamicRealm.query("Sample").find().first()

        assertFailsWith<IllegalStateException> {
            dynamicSample.getValueList<String>("stringListField").add("IMMUTABLE_LIST_ELEMENT")
        }.run {
            assertTrue(message!!.contains("Cannot modify managed objects outside of a write transaction"))
        }
        assertFailsWith<IllegalStateException> {
            dynamicSample.getNullableValueList<String>("nullableStringListField").add("IMMUTABLE_LIST_ELEMENT")
        }.run {
            assertTrue(message!!.contains("Cannot modify managed objects outside of a write transaction"))
        }
    }

    @Test
    fun observe_throws() {
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
            first.getValue<DynamicRealmObject>("stringField")
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
