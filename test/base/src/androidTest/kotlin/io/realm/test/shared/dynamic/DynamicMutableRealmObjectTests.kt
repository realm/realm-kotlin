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

import io.realm.RealmConfiguration
import io.realm.RealmInstant
import io.realm.dynamic.DynamicMutableRealm
import io.realm.dynamic.DynamicMutableRealmObject
import io.realm.dynamic.DynamicRealmObject
import io.realm.dynamic.getNullableValue
import io.realm.dynamic.getNullableValueList
import io.realm.dynamic.getValue
import io.realm.dynamic.getValueList
import io.realm.entities.Sample
import io.realm.entities.primarykey.PrimaryKeyString
import io.realm.entities.primarykey.PrimaryKeyStringNullable
import io.realm.internal.InternalConfiguration
import io.realm.realmListOf
import io.realm.schema.ListPropertyType
import io.realm.schema.RealmClass
import io.realm.schema.RealmProperty
import io.realm.schema.RealmPropertyType
import io.realm.schema.RealmSchema
import io.realm.schema.RealmStorageType
import io.realm.schema.ValuePropertyType
import io.realm.test.StandaloneDynamicMutableRealm
import io.realm.test.assertFailsWithMessage
import io.realm.test.platform.PlatformUtils
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DynamicMutableRealmObjectTests {

    private lateinit var tmpDir: String
    private lateinit var configuration: RealmConfiguration
    private lateinit var dynamicMutableRealm: DynamicMutableRealm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration.Builder(schema = setOf(Sample::class, PrimaryKeyString::class, PrimaryKeyStringNullable::class))
            .directory(tmpDir)
            .build()

        // We use a StandaloneDynamicMutableRealm that allows us to manage the write transaction
        // which is not possible on the public DynamicMutableRealm.
        dynamicMutableRealm = StandaloneDynamicMutableRealm(configuration as InternalConfiguration).apply {
            beginTransaction()
        }
    }

    @AfterTest
    fun tearDown() {
        if (this::dynamicMutableRealm.isInitialized && !dynamicMutableRealm.isClosed()) {
            (dynamicMutableRealm as StandaloneDynamicMutableRealm).close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun get_returnsDynamicMutableObject() {
        val parent = dynamicMutableRealm.copyToRealm(
            DynamicMutableRealmObject.create(
                "Sample",
                "stringField" to "PARENT",
                "nullableObject" to DynamicMutableRealmObject.create("Sample", "stringField" to "CHILD")
            )
        )
        val child: DynamicMutableRealmObject? = parent.getObject("nullableObject")
        assertNotNull(child)
        child.set("stringField", "UPDATED_CHILD")
    }

    @Test
    @Suppress("LongMethod", "ComplexMethod")
    fun set_allTypes() = runTest {
        val dynamicSample: DynamicMutableRealmObject = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        assertNotNull(dynamicSample)

        val schema: RealmSchema = dynamicMutableRealm.schema()
        val sampleDescriptor: RealmClass = schema["Sample"]!!

        val properties: Collection<RealmProperty> = sampleDescriptor.properties
        for (property: RealmProperty in properties) {
            val name: String = property.name
            val type: RealmPropertyType = property.type
            when (type) {
                is ValuePropertyType -> {
                    if (type.isNullable) {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                dynamicSample.set(name, true)
                                assertEquals(true, dynamicSample.getNullableValue(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullableValue<Boolean>(name))
                            }
                            RealmStorageType.INT -> {
                                dynamicSample.set(name, 42L)
                                assertEquals(42L, dynamicSample.getNullableValue(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullableValue<Long>(name))
                            }
                            RealmStorageType.STRING -> {
                                dynamicSample.set(name, "STRING")
                                assertEquals("STRING", dynamicSample.getNullableValue(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullableValue<String>(name))
                            }
                            RealmStorageType.OBJECT -> {
                                dynamicSample.set(name, Sample())
                                val nullableObject = dynamicSample.getObject(name)
                                assertNotNull(nullableObject)
                                assertEquals("Realm", nullableObject.getValue("stringField"))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getObject(name))
                            }
                            RealmStorageType.FLOAT -> {
                                dynamicSample.set(name, 4.2f)
                                assertEquals(4.2f, dynamicSample.getNullableValue(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullableValue<Float>(name))
                            }
                            RealmStorageType.DOUBLE -> {
                                dynamicSample.set(name, 4.2)
                                assertEquals(4.2, dynamicSample.getNullableValue(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullableValue<Double>(name))
                            }
                            RealmStorageType.TIMESTAMP -> {
                                val value = RealmInstant.fromEpochSeconds(100, 100)
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.getNullableValue(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullableValue<RealmInstant>(name))
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                dynamicSample.set(name, true)
                                assertEquals(true, dynamicSample.getValue(name))
                            }
                            RealmStorageType.INT -> {
                                dynamicSample.set(name, 42L)
                                assertEquals(42L, dynamicSample.getValue(name))
                            }
                            RealmStorageType.STRING -> {
                                dynamicSample.set(name, "STRING")
                                assertEquals("STRING", dynamicSample.getValue(name))
                            }
                            RealmStorageType.FLOAT -> {
                                dynamicSample.set(name, 4.2f)
                                assertEquals(4.2f, dynamicSample.getValue(name))
                            }
                            RealmStorageType.DOUBLE -> {
                                dynamicSample.set(name, 4.2)
                                assertEquals(4.2, dynamicSample.getValue(name))
                            }
                            RealmStorageType.TIMESTAMP -> {
                                val value = RealmInstant.fromEpochSeconds(100, 100)
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.getValue(name))
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    }
                }
                is ListPropertyType -> {
                    if (type.isNullable) {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                val value = true
                                dynamicSample.getNullableValueList<Boolean>(property.name).add(value)
                                dynamicSample.getNullableValueList<Boolean>(property.name).add(null)
                                val listOfNullable = dynamicSample.getNullableValueList(property.name, Boolean::class)
                                assertEquals(value, listOfNullable[0])
                                assertEquals(null, listOfNullable[1])
                            }
                            RealmStorageType.INT -> {
                                val value: Long = when (property.name) {
                                    "nullableByteListField" -> defaultSample.byteField.toLong()
                                    "nullableCharListField" -> defaultSample.charField.code.toLong()
                                    "nullableShortListField" -> defaultSample.shortField.toLong()
                                    "nullableIntListField" -> defaultSample.intField.toLong()
                                    "nullableLongListField" -> defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                dynamicSample.getNullableValueList<Long>(property.name).add(value)
                                dynamicSample.getNullableValueList<Long>(property.name).add(null)
                                val listOfNullable = dynamicSample.getNullableValueList(property.name, Long::class)
                                assertEquals(value, listOfNullable[0])
                                assertEquals(null, listOfNullable[1])
                            }
                            RealmStorageType.STRING -> {
                                val value = "NEW_ELEMENT"
                                dynamicSample.getNullableValueList<String>(property.name).add(value)
                                dynamicSample.getNullableValueList<String>(property.name).add(null)
                                val listOfNullable = dynamicSample.getNullableValueList(property.name, String::class)
                                assertEquals(value, listOfNullable[0])
                                assertEquals(null, listOfNullable[1])
                            }
                            RealmStorageType.FLOAT -> {
                                val value = 1.234f
                                dynamicSample.getNullableValueList<Float>(property.name).add(value)
                                dynamicSample.getNullableValueList<Float>(property.name).add(null)
                                val listOfNullable = dynamicSample.getNullableValueList(property.name, Float::class)
                                assertEquals(value, listOfNullable[0])
                                assertEquals(null, listOfNullable[1])
                            }
                            RealmStorageType.DOUBLE -> {
                                val value = 1.234
                                dynamicSample.getNullableValueList<Double>(property.name).add(value)
                                dynamicSample.getNullableValueList<Double>(property.name).add(null)
                                val listOfNullable = dynamicSample.getNullableValueList(property.name, Double::class)
                                assertEquals(value, listOfNullable[0])
                                assertEquals(null, listOfNullable[1])
                            }
                            RealmStorageType.TIMESTAMP -> {
                                val value = RealmInstant.fromEpochSeconds(100, 100)
                                dynamicSample.getNullableValueList<RealmInstant>(property.name).add(value)
                                dynamicSample.getNullableValueList<RealmInstant>(property.name).add(null)
                                val listOfNullable = dynamicSample.getNullableValueList(property.name, RealmInstant::class)
                                assertEquals(value, listOfNullable[0])
                                assertEquals(null, listOfNullable[1])
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        when (type.storageType) {
                            RealmStorageType.BOOL -> {
                                val value = true
                                dynamicSample.getValueList<Boolean>(property.name).add(value)
                                assertEquals(value, dynamicSample.getValueList(property.name, Boolean::class)[0])
                            }
                            RealmStorageType.INT -> {
                                val value: Long = when (property.name) {
                                    "byteListField" -> defaultSample.byteField.toLong()
                                    "charListField" -> defaultSample.charField.code.toLong()
                                    "shortListField" -> defaultSample.shortField.toLong()
                                    "intListField" -> defaultSample.intField.toLong()
                                    "longListField" -> defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                dynamicSample.getValueList<Long>(property.name).add(value)
                                assertEquals(value, dynamicSample.getValueList(property.name, Long::class)[0])
                            }
                            RealmStorageType.STRING -> {
                                val value = "NEW_ELEMENT"
                                dynamicSample.getValueList<String>(property.name).add(value)
                                assertEquals(value, dynamicSample.getValueList(property.name, String::class)[0])
                            }
                            RealmStorageType.FLOAT -> {
                                val value = 1.234f
                                dynamicSample.getValueList<Float>(property.name).add(value)
                                assertEquals(value, dynamicSample.getValueList(property.name, Float::class)[0])
                            }
                            RealmStorageType.DOUBLE -> {
                                val value = 1.234
                                dynamicSample.getValueList<Double>(property.name).add(value)
                                assertEquals(value, dynamicSample.getValueList(property.name, Double::class)[0])
                            }
                            RealmStorageType.TIMESTAMP -> {
                                val value = RealmInstant.fromEpochSeconds(100, 100)
                                dynamicSample.getValueList<RealmInstant>(property.name).add(value)
                                assertEquals(value, dynamicSample.getValueList(property.name, RealmInstant::class)[0])
                            }
                            RealmStorageType.OBJECT -> {
                                val value = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample")).set("stringField", "NEW_OBJECT")
                                dynamicSample.getValueList<DynamicRealmObject>(property.name).add(value)
                                assertEquals("NEW_OBJECT", dynamicSample.getValueList(property.name, DynamicRealmObject::class)[0].getValue("stringField"))
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
    fun set_detectsDuplicates() {
        val child = DynamicMutableRealmObject.create(
                "Sample",
                "stringField" to "child"
        )
        val intermediate = DynamicMutableRealmObject.create(
                "Sample",
                "stringField" to "intermedidate",
                "nullableObject" to child,
                "objectListField" to realmListOf(child, child)
        )
        val parent = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        parent.set("nullableObject", intermediate)

        dynamicMutableRealm.query("Sample").find().run {
            assertEquals(3, size)
        }
    }

    @Test
    fun set_throwsWithWrongType_stringInt() {
        val sample = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        assertFailsWithMessage<IllegalArgumentException>("Property 'Sample.stringField' of type 'class kotlin.String' cannot be assigned with value '42' of type 'class kotlin.Int'") {
            sample.set("stringField", 42)
        }
    }

    @Test
    fun set_throwsWithWrongType_longInt() {
        val sample = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        assertFailsWithMessage<IllegalArgumentException>("Property 'Sample.intField' of type 'class kotlin.Long' cannot be assigned with value '42' of type 'class kotlin.Int'") {
            sample.set("intField", 42)
        }
    }

    @Test
    fun set_throwsOnNullForRequiredField() {
        val o = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        assertFailsWithMessage<IllegalArgumentException>("Property 'Sample.stringField' of type 'class kotlin.String' cannot be assigned with value 'null' of type 'class java.lang.Void?'") {
            o.set("stringField", null)
        }
    }

    // This tests the current behavior of actually being able to update a primary key attribute on
    // a dynamic realm as it is required for migrations and that is the only place we actually
    // expose dynamic realms right now
    @Test
    fun set_primaryKey() {
        val o = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("PrimaryKeyString", mapOf("primaryKey" to "PRIMARY_KEY")))
        o.set("primaryKey", "UPDATED_PRIMARY_KEY")
        assertEquals("UPDATED_PRIMARY_KEY", o.getValue("primaryKey"))
    }

    @Test
    fun list_add_detectsDuplicates() {
        val child = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "child"
        )
        val intermediate = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "intermedidate",
            "nullableObject" to child,
            "objectListField" to realmListOf(child, child)
        )
        val parent = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        parent.getObjectList("objectListField").add(intermediate)

        dynamicMutableRealm.query("Sample").find().run {
            assertEquals(3, size)
        }
    }

    @Test
    fun list_addWithIndex_detectsDuplicates() {
        val child = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "child"
        )
        val intermediate = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "intermedidate",
            "nullableObject" to child,
            "objectListField" to realmListOf(child, child)
        )
        val parent = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        parent.getObjectList("objectListField").add(0, intermediate)

        dynamicMutableRealm.query("Sample").find().run {
            assertEquals(3, size)
        }
    }

    @Test
    fun list_addAll_detectsDuplicates() {
        val child = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "child"
        )
        val intermediate = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "intermedidate",
            "nullableObject" to child,
            "objectListField" to realmListOf(child, child)
        )
        val parent = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        parent.getObjectList("objectListField").addAll(listOf(intermediate, intermediate))

        dynamicMutableRealm.query("Sample").find().run {
            assertEquals(3, size)
        }
    }

    @Test
    fun list_addAllWithIndex_detectsDuplicates() {
        val child = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "child"
        )
        val intermediate = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "intermedidate",
            "nullableObject" to child,
            "objectListField" to realmListOf(child, child)
        )
        val parent = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        parent.getObjectList("objectListField").addAll(0, listOf(intermediate, intermediate))

        dynamicMutableRealm.query("Sample").find().run {
            assertEquals(3, size)
        }
    }

    @Test
    fun list_set_detectsDuplicates() {
        val child1 = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "child1"
        )
        val child2 = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "child2"
        )
        val intermediate = DynamicMutableRealmObject.create(
            "Sample",
            "stringField" to "intermedidate",
            "nullableObject" to child2,
            "objectListField" to realmListOf(child2, child2)
        )
        val parent = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        parent.getObjectList("objectListField").run {
            add(child1)
            set(0, intermediate)
        }
        dynamicMutableRealm.query("Sample").find().run {
            assertEquals(4, size)
        }
    }
}
