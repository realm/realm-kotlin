/*
 * Copyright 2021 Realm Inc.
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

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmResults
import io.realm.entities.MultipleConstructors
import io.realm.entities.Sample
import io.realm.entities.embedded.EmbeddedChild
import io.realm.entities.embedded.EmbeddedInnerChild
import io.realm.entities.embedded.EmbeddedParent
import io.realm.entities.schema.SchemaVariations
import io.realm.internal.interop.PropertyType
import io.realm.internal.platform.runBlocking
import io.realm.internal.schema.RealmClassImpl
import io.realm.query
import io.realm.schema.ListPropertyType
import io.realm.schema.RealmPropertyType
import io.realm.schema.RealmStorageType
import io.realm.schema.ValuePropertyType
import io.realm.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

private val SCHEMA_VARIATION_CLASS_NAME = SchemaVariations::class.simpleName!!

/**
 * Test of public schema API.
 *
 * This test suite doesn't exhaust all modeling features, but should have full coverage of the
 * schema API code paths.
 */
public class RealmSchemaTests {

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            RealmConfiguration.Builder(schema = setOf(SchemaVariations::class, Sample::class, EmbeddedParent::class, EmbeddedChild::class, EmbeddedInnerChild::class))
                .directory(tmpDir)
                .build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (!realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun realmClass() {
        val schema = realm.schema()

        assertEquals(5, schema.classes.size)

        val schemaVariationsDescriptor = schema[SCHEMA_VARIATION_CLASS_NAME]
            ?: fail("Couldn't find class")
        assertEquals(SCHEMA_VARIATION_CLASS_NAME, schemaVariationsDescriptor.name)
        assertFalse(schemaVariationsDescriptor.isEmbedded)
        assertEquals("string", schemaVariationsDescriptor.primaryKey?.name)

        val sampleName = "Sample"
        val sampleDescriptor = schema[sampleName] ?: fail("Couldn't find class")
        assertEquals(sampleName, sampleDescriptor.name)
        assertFalse(sampleDescriptor.isEmbedded)
        assertNull(sampleDescriptor.primaryKey)

        val embeddedChildName = "EmbeddedChild"
        val embeddedChildDescriptor = schema[embeddedChildName] ?: fail("Couldn't find class")
        assertEquals(embeddedChildName, embeddedChildDescriptor.name)
        assertTrue(embeddedChildDescriptor.isEmbedded)
        assertNull(embeddedChildDescriptor.primaryKey)
    }

    @Test
    fun realmClass_notFound() {
        val schema = realm.schema()
        assertNull(schema["non-existing_class"])
    }

    @Test
    fun realmProperty() {
        val schema = realm.schema()

        val schemaVariationsDescriptor = schema[SCHEMA_VARIATION_CLASS_NAME]
            ?: fail("Couldn't find class")

        schemaVariationsDescriptor["string"]!!.run {
            assertEquals("string", name)
            type.run {
                assertIs<ValuePropertyType>(this)
                assertEquals(RealmStorageType.STRING, storageType)
                assertFalse(isNullable)
                assertTrue(isPrimaryKey)
                assertFalse(isIndexed)
            }
            assertFalse(isNullable)
        }
        schemaVariationsDescriptor["nullableString"]!!.run {
            assertEquals("nullableString", name)
            type.run {
                assertIs<ValuePropertyType>(this)
                assertEquals(RealmStorageType.STRING, storageType)
                assertTrue(isNullable)
                assertFalse(isPrimaryKey)
                assertTrue(isIndexed)
            }
            assertTrue(isNullable)
        }
        schemaVariationsDescriptor["stringList"]!!.run {
            assertEquals("stringList", name)
            type.run {
                assertIs<ListPropertyType>(this)
                assertEquals(RealmStorageType.STRING, storageType)
                assertFalse(this.isNullable)
            }
            assertFalse(isNullable)
        }
        schemaVariationsDescriptor["nullableStringList"]!!.run {
            assertEquals("nullableStringList", name)
            type.run {
                assertIs<ListPropertyType>(this)
                assertEquals(RealmStorageType.STRING, storageType)
                assertTrue(this.isNullable)
            }
            assertFalse(isNullable)
        }
    }

    @Test
    fun realmProperty_notFound() {
        val schema = realm.schema()
        val schemaVariationDescriptor = schema[SCHEMA_VARIATION_CLASS_NAME]!!
        assertNull(schemaVariationDescriptor["non-existing-property"])
    }

    // This test is just showing how we could use the public Schema API to perform exhaustive tests.
    // It overlaps with the TypeDescriptor infrastructure, so we should probably just update the
    // type descriptor infrastructure to use this information or make the various TypeDescriptor
    // properties available through the public schema API, ex.
    //
    // class ValuePropertyType {
    //     companion object {
    //         val supportedStorageTypes: Set<RealmStorageTypes> = setOf( ....)
    //     }
    // }
    @Test
    @Suppress("NestedBlockDepth")
    fun schema_optionCoverage() {
        // Property options
        @Suppress("invisible_member")
        val propertyTypeMap =
            RealmPropertyType.subTypes.map { it to RealmStorageType.values().toMutableSet() }.toMap()
                .toMutableMap()

        val schema = realm.schema()

        // Verify properties of SchemaVariations
        val classDescriptor = schema["SchemaVariations"] ?: fail("Couldn't find class")
        assertEquals("SchemaVariations", classDescriptor.name)
        for (property in classDescriptor.properties) {
            property.type.run {
                propertyTypeMap.getValue(this::class).remove(this.storageType)
            }
        }

        assertTrue(
            propertyTypeMap.none
            { (_, v) -> v.isNotEmpty() },
            "Field types not exhausted: $propertyTypeMap"
        )
    }

    @Test
    fun multipleConstructors() {
        val config = RealmConfiguration
            .Builder(schema = setOf(MultipleConstructors::class))
            .directory(tmpDir).build()
        val realm = Realm.open(config)

        val firstCtor = MultipleConstructors() // this uses all defaults: "John", "Doe", 42
        val secondCtor = MultipleConstructors(foreName = "Thanos") // Thanos, Doe, 42
        val thirdCtor = MultipleConstructors(firstName = "Jack", lastName = "Reacher")
        val fourthCtor = MultipleConstructors("Lee", "Child", 67)

        realm.writeBlocking {
            this.copyToRealm(firstCtor)
            this.copyToRealm(secondCtor)
            this.copyToRealm(thirdCtor)
            this.copyToRealm(fourthCtor)
        }

        val people: RealmResults<MultipleConstructors> = realm.query<MultipleConstructors>().sort("firstName").find()
        assertEquals(4, people.size)

        assertEquals("Jack", people[0].firstName)
        assertEquals("Reacher", people[0].lastName)
        assertEquals(42, people[0].age)

        assertEquals("John", people[1].firstName)
        assertEquals("Doe", people[1].lastName)
        assertEquals(42, people[1].age)

        assertEquals("Lee", people[2].firstName)
        assertEquals("Child", people[2].lastName)
        assertEquals(67, people[2].age)

        assertEquals("Thanos", people[3].firstName)
        assertEquals("Doe", people[3].lastName)
        assertEquals(42, people[3].age)

        realm.close()
    }

    @Test
    @Suppress("invisible_reference", "invisible_member")
    // We don't have any way to verify that the schema is actually changed since we cannot open
    // realms in dynamic mode, hence schema will only get it's (anyway stable!?) keys updated and
    // not see any new classes/properties. Thus only verifying that we have an updated key cache
    // instance
    fun schemaChanged() = runBlocking {
        val schema = realm.schema() as io.realm.internal.schema.RealmSchemaImpl
        val schemaVariationsDescriptor: RealmClassImpl = schema["SchemaVariations"]!!
        val sampleDescriptor: RealmClassImpl = schema["Sample"]!!

        // Get an object from the initial schema
        val sample1 = realm.write {
            copyToRealm(Sample())
        }
        // And grab the class metadata instance
        val classCache = (sample1 as io.realm.internal.RealmObjectInternal).`io_realm_kotlin_objectReference`!!.metadata

        val sample2 = realm.write {
            copyToRealm(Sample())
        }

        // Assert that this is the same for subsequent objects of the same type
        assertTrue(classCache === (sample2 as io.realm.internal.RealmObjectInternal).`io_realm_kotlin_objectReference`!!.metadata)

        // Update the schema
        (realm as io.realm.internal.RealmImpl).updateSchema(
            io.realm.internal.schema.RealmSchemaImpl(
                listOf(
                    schemaVariationsDescriptor,
                    sampleDescriptor,
                    RealmClassImpl(
                        io.realm.internal.interop.ClassInfo("NEW_CLASS", numProperties = 1),
                        listOf(io.realm.internal.interop.PropertyInfo("NEW_PROPERTY", type = PropertyType.RLM_PROPERTY_TYPE_STRING))
                    )
                )
            )
        )

        // And verify that new objects have a new class meta data instance
        val sample3 = realm.write {
            copyToRealm(Sample())
        }
        assertFalse(classCache === (sample3 as io.realm.internal.RealmObjectInternal).`io_realm_kotlin_objectReference`!!.metadata)
        // and that the old frozen objects still have the original class meta data instance
        assertTrue(classCache === (sample1 as io.realm.internal.RealmObjectInternal).`io_realm_kotlin_objectReference`!!.metadata)
        assertTrue(classCache === (sample2 as io.realm.internal.RealmObjectInternal).`io_realm_kotlin_objectReference`!!.metadata)
    }
}
