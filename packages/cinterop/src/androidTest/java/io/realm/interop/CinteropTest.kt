/*
 * Copyright 2020 Realm Inc.
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

package io.realm.interop

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Direct tests of the 'swig' low level C-API wrapper for JVM platforms.
// These test are not thought as being exhaustive, but is more to provide a playground for
// experiments and maybe more relevant for reproduction of C-API issues.
@RunWith(AndroidJUnit4::class)
class CinteropTest {

    @BeforeTest
    fun setup() {
        System.loadLibrary("realmc")
    }

    @Test
    fun version() {
        assertEquals("10.5.2", realmc.realm_get_library_version())
    }

    // Test various schema migration with automatic flag:
    //  - If you add or remove a class you don't need to update the schema version.
    //  - If you add/remove a column you need to set a greater version number for migration.
    //  - If you rename a column it will be treated as removing and adding a column, so it needs a greater version number for migration.
    //    (note: data will not be migrated into the renamed column)
    @Test
    fun schema_migration_automatic() {
        System.loadLibrary("realmc")
        val path = Files.createTempDirectory("android_tests").absolutePathString() + "/c_api_test.realm"

        val class_1 = createClass("foo", 1)
        val prop_1_1 = createIntProperty("int")
        val schema_1 = createSchema(listOf(Pair(class_1, listOf(prop_1_1))))

        val config_1: Long = realmc.realm_config_new()
        realmc.realm_config_set_path(config_1, path)
        realmc.realm_config_set_schema(config_1, schema_1)
        realmc.realm_config_set_schema_mode(config_1, realm_schema_mode_e.RLM_SCHEMA_MODE_AUTOMATIC)
        realmc.realm_config_set_schema_version(config_1, 1)

        realmc.realm_open(config_1).also { realm ->
            // insert some data
            realmc.realm_begin_write(realm)
            val foo1 = realmc.realm_object_create(realm, findTable(realm, "foo").key)
            val foo_int_property = findProperty(realm, "foo", "int")
            realmc.realm_set_value(foo1, foo_int_property.key, realm_value_t().apply { type = realm_value_type_e.RLM_TYPE_INT; integer = 42 }, false)

            realmc.realm_commit(realm)

            // close Realm
            realmc.realm_release(config_1)
            realmc.realm_release(schema_1)
            realmc.realm_close(realm)
        }

        //*** Renaming a column is treated as removing then adding a new column (data on the old column will not be migrated) ***//
        val class_2_renamed_col = createClass("foo", 1)
        val prop_2_1_renamed_col = createIntProperty("int_renamed")
        val schema_2_renamed_col = createSchema(listOf(Pair(class_2_renamed_col, listOf(prop_2_1_renamed_col))))

        val config_2_renamed_col: Long = realmc.realm_config_new()
        realmc.realm_config_set_path(config_2_renamed_col, path)
        realmc.realm_config_set_schema(config_2_renamed_col, schema_2_renamed_col)
        realmc.realm_config_set_schema_mode(config_2_renamed_col, realm_schema_mode_e.RLM_SCHEMA_MODE_AUTOMATIC)
        realmc.realm_config_set_schema_version(config_2_renamed_col, 1)

        assertFailsWith<RuntimeException> {
            realmc.realm_open(config_2_renamed_col)
        }.run {
            assertEquals(
                "[18]: Migration is required due to the following errors:\n" +
                        "- Property 'foo.int' has been removed.\n" +
                        "- Property 'foo.int_renamed' has been added.",
                message
            )
        }
        // Incrementing the schema version migrate the Realm automatically
        realmc.realm_config_set_schema_version(config_2_renamed_col, 2)
        realmc.realm_open(config_2_renamed_col).also { realm ->
            // make sure data was preserved
            val foo_class = findTable(realm, "foo").key
            var query: Long = realmc.realm_query_parse(realm, foo_class, "TRUEPREDICATE", 0, realm_value_t())
            val count = realm_size_t()
            realmc.realm_query_count(query, count)
            assertEquals(1, count.value)

            // but data will not be migrated on the new column
            query = realmc.realm_query_parse(realm, foo_class, "int_renamed == $0", 1, realm_value_t().apply { type = realm_value_type_e.RLM_TYPE_INT; integer = 42 })
            realmc.realm_query_count(query, count)
            assertEquals(0, count.value)

            // old column was removed
            assertFailsWith<RuntimeException> {
                realmc.realm_query_parse(realm, foo_class, "int == $0", 1, realm_value_t().apply { type = realm_value_type_e.RLM_TYPE_INT; integer = 42 })
            }.run {
                assertEquals(
                    "[36]: 'foo' has no property: 'int'",
                    message
                )
            }

            // close Realm
            realmc.realm_release(config_2_renamed_col)
            realmc.realm_release(schema_2_renamed_col)
            realmc.realm_close(realm)
        }


        // *** Using the same schema version with a new column throws an exception *** //
        val class_2 = createClass("foo", 2)
        val prop_2_2 = createIntProperty("newColumn")
        val schema_2 = createSchema(listOf(Pair(class_2, listOf(prop_2_1_renamed_col, prop_2_2))))

        val config_2: Long = realmc.realm_config_new()
        realmc.realm_config_set_path(config_2, path)
        realmc.realm_config_set_schema(config_2, schema_2)
        realmc.realm_config_set_schema_mode(config_2, realm_schema_mode_e.RLM_SCHEMA_MODE_AUTOMATIC)
        realmc.realm_config_set_schema_version(config_2, 2)

        assertFailsWith<RuntimeException> {
            realmc.realm_open(config_2)
        }.run {
            assertEquals(
                "[18]: Migration is required due to the following errors:\n" +
                    "- Property 'foo.newColumn' has been added.",
                message
            )
        }

        // Incrementing the schema version migrate the Realm automatically
        realmc.realm_config_set_schema_version(config_2, 3)
        realmc.realm_open(config_2).also { realm ->
            // make sure data was preserved
            val query: Long = realmc.realm_query_parse(realm, findTable(realm, "foo").key, "TRUEPREDICATE", 0, realm_value_t())
            val count = realm_size_t()
            realmc.realm_query_count(query, count)
            assertEquals(1, count.value)

            // close Realm
            realmc.realm_release(config_2)
            realmc.realm_release(schema_2)
            realmc.realm_close(realm)
        }



        // *** Using the same schema version when removing a column throws an exception *** //
        val class_3 = createClass("foo", 1)
        val prop_3_1 = createIntProperty("newColumn")
        val schema_3 = createSchema(listOf(Pair(class_3, listOf(prop_3_1))))

        val config_3: Long = realmc.realm_config_new()
        realmc.realm_config_set_path(config_3, path)
        realmc.realm_config_set_schema(config_3, schema_3)
        realmc.realm_config_set_schema_mode(config_3, realm_schema_mode_e.RLM_SCHEMA_MODE_AUTOMATIC)
        realmc.realm_config_set_schema_version(config_3, 3)

        assertFailsWith<RuntimeException> {
            realmc.realm_open(config_3)
        }.run {
            assertEquals(
                "[18]: Migration is required due to the following errors:\n" +
                    "- Property 'foo.int_renamed' has been removed.",
                message
            )
        }
        // Incrementing the schema version migrate the Realm automatically
        realmc.realm_config_set_schema_version(config_3, 4)
        realmc.realm_open(config_3).also { realm ->
            // make sure data was preserved
            val query: Long = realmc.realm_query_parse(realm, findTable(realm, "foo").key, "TRUEPREDICATE", 0, realm_value_t())
            val count = realm_size_t()
            realmc.realm_query_count(query, count)
            assertEquals(1, count.value)

            // close Realm
            realmc.realm_release(config_3)
            realmc.realm_release(schema_3)
            realmc.realm_close(realm)
        }

        // *** Using the same schema version when adding a new class will not throw *** //
        val class_4 = createClass("baz", 1)
        val prop_4_1 = createIntProperty("col1")
        val schema_4 = createSchema(listOf(Pair(class_4, listOf(prop_4_1)), Pair(class_3, listOf(prop_3_1))))

        val config_4: Long = realmc.realm_config_new()
        realmc.realm_config_set_path(config_4, path)
        realmc.realm_config_set_schema(config_4, schema_4)
        realmc.realm_config_set_schema_mode(config_4, realm_schema_mode_e.RLM_SCHEMA_MODE_AUTOMATIC)
        realmc.realm_config_set_schema_version(config_4, 4)

        realmc.realm_open(config_4).also { realm ->
            // make sure data was preserved
            var query: Long = realmc.realm_query_parse(realm, findTable(realm, "foo").key, "TRUEPREDICATE", 0, realm_value_t())
            val count = realm_size_t()
            realmc.realm_query_count(query, count)
            assertEquals(1, count.value)

            // new class is available
            query = realmc.realm_query_parse(realm, findTable(realm, "baz").key, "TRUEPREDICATE", 0, realm_value_t())
            realmc.realm_query_count(query, count)
            assertEquals(0, count.value)

            // close Realm
            realmc.realm_release(config_4)
            realmc.realm_release(schema_4)
            realmc.realm_close(realm)
        }

        // *** Using the same schema version when removing a class will not throw *** //
        val schema_5 = createSchema(listOf(Pair(class_3, listOf(prop_3_1))))

        val config_5: Long = realmc.realm_config_new()
        realmc.realm_config_set_path(config_5, path)
        realmc.realm_config_set_schema(config_5, schema_5)
        realmc.realm_config_set_schema_mode(config_5, realm_schema_mode_e.RLM_SCHEMA_MODE_AUTOMATIC)
        realmc.realm_config_set_schema_version(config_5, 4)

        realmc.realm_open(config_5).also { realm ->
            // make sure data was preserved
            val query: Long = realmc.realm_query_parse(realm, findTable(realm, "foo").key, "TRUEPREDICATE", 0, realm_value_t())
            val count = realm_size_t()
            realmc.realm_query_count(query, count)
            assertEquals(1, count.value)

            // close Realm
            realmc.realm_release(config_5)
            realmc.realm_release(schema_5)
            realmc.realm_close(realm)
        }
    }

    @Test
    fun schema_migration_reset() {
        System.loadLibrary("realmc")
        val path = Files.createTempDirectory("android_tests").absolutePathString() + "/c_api_test.realm"

        val class_1 = createClass("foo", 1)
        val prop_1_1 = createIntProperty("int")
        val schema_1 = createSchema(listOf(Pair(class_1, listOf(prop_1_1))))

        val config_1: Long = realmc.realm_config_new()
        realmc.realm_config_set_path(config_1, path)
        realmc.realm_config_set_schema(config_1, schema_1)
        realmc.realm_config_set_schema_mode(config_1, realm_schema_mode_e.RLM_SCHEMA_MODE_RESET_FILE)
        realmc.realm_config_set_schema_version(config_1, 0)

        realmc.realm_open(config_1).also { realm ->
            // insert some data
            realmc.realm_begin_write(realm)
            realmc.realm_object_create(realm, findTable(realm, "foo").key)
            realmc.realm_commit(realm)

            // close Realm
            realmc.realm_release(config_1)
            realmc.realm_release(schema_1)
            realmc.realm_close(realm)
        }

        // **** Using the same schema version and adding a new column reset the file  *** //
        val class_2 = createClass("foo", 2)
        val prop_2_1 = createIntProperty("int")
        val prop_2_2 = createIntProperty("newColumn")
        val schema_2 = createSchema(listOf(Pair(class_2, listOf(prop_2_1, prop_2_2))))

        val config_2: Long = realmc.realm_config_new()
        realmc.realm_config_set_path(config_2, path)
        realmc.realm_config_set_schema(config_2, schema_2)
        realmc.realm_config_set_schema_mode(config_2, realm_schema_mode_e.RLM_SCHEMA_MODE_RESET_FILE)
        realmc.realm_config_set_schema_version(config_2, 0)

        realmc.realm_open(config_2).also { realm ->
            // make sure the Realm is empty (reset)
            val foo_class = findTable(realm, "foo")
            val query: Long = realmc.realm_query_parse(realm, foo_class.key, "TRUEPREDICATE", 0, realm_value_t())
            val count = realm_size_t()
            realmc.realm_query_count(query, count)
            assertEquals(0, count.value)

            // adding some data
            realmc.realm_begin_write(realm)
            realmc.realm_object_create(realm, foo_class.key)
            realmc.realm_commit(realm)

            // close Realm
            realmc.realm_release(config_2)
            realmc.realm_release(schema_2)
            realmc.realm_close(realm)
        }

        // **** Using the same schema version and removing a column reset the file  *** //
        val class_3 = createClass("foo", 1)
        val prop_3_1 = createIntProperty("newColumn")
        val schema_3 = createSchema(listOf(Pair(class_3, listOf(prop_3_1))))

        val config_3: Long = realmc.realm_config_new()
        realmc.realm_config_set_path(config_3, path)
        realmc.realm_config_set_schema(config_3, schema_3)
        realmc.realm_config_set_schema_mode(config_3, realm_schema_mode_e.RLM_SCHEMA_MODE_RESET_FILE)
        realmc.realm_config_set_schema_version(config_3, 0)

        realmc.realm_open(config_3).also { realm ->
            // make sure the Realm is empty (reset)
            val query: Long = realmc.realm_query_parse(realm, findTable(realm, "foo").key, "TRUEPREDICATE", 0, realm_value_t())
            val count = realm_size_t()
            realmc.realm_query_count(query, count)
            assertEquals(0, count.value)

            // close Realm
            realmc.realm_release(config_3)
            realmc.realm_release(schema_3)
            realmc.realm_close(realm)
        }
    }

    @ExperimentalPathApi
    @Test
    fun cinterop_swig() {
        System.loadLibrary("realmc")

        val rlmInvalidPropertyKey = realmc.getRLM_INVALID_PROPERTY_KEY()
        val rlmInvalidClassKey = realmc.getRLM_INVALID_CLASS_KEY()

        val class_1 = realm_class_info_t().apply {
            name = "foo"
            primary_key = ""
            num_properties = 3
            num_computed_properties = 0
            key = rlmInvalidClassKey
            flags = realm_class_flags_e.RLM_CLASS_NORMAL
        }

        val prop_1_1 = realm_property_info_t().apply {
            name = "int"
            public_name = ""
            type = realm_property_type_e.RLM_PROPERTY_TYPE_INT
            collection_type = realm_collection_type_e.RLM_COLLECTION_TYPE_NONE
            link_target = ""
            link_origin_property_name = ""
            key = rlmInvalidPropertyKey
            flags = realm_property_flags_e.RLM_PROPERTY_NORMAL
        }
        val prop_1_2 = realm_property_info_t().apply {
            name = "str"
            public_name = ""
            type = realm_property_type_e.RLM_PROPERTY_TYPE_STRING
            collection_type = realm_collection_type_e.RLM_COLLECTION_TYPE_NONE
            link_target = ""
            link_origin_property_name = ""
            key = rlmInvalidPropertyKey
            flags = realm_property_flags_e.RLM_PROPERTY_NORMAL
        }
        val prop_1_3 = realm_property_info_t().apply {
            name = "bars"
            public_name = ""
            type = realm_property_type_e.RLM_PROPERTY_TYPE_OBJECT
            collection_type = realm_collection_type_e.RLM_COLLECTION_TYPE_LIST
            link_target = "bar"
            link_origin_property_name = ""
            key = rlmInvalidPropertyKey
            flags = realm_property_flags_e.RLM_PROPERTY_NORMAL
        }

        val class_2 = realm_class_info_t().apply {
            name = "bar"
            primary_key = "int"
            num_properties = 2
            num_computed_properties = 0
            key = rlmInvalidClassKey
            flags = realm_class_flags_e.RLM_CLASS_NORMAL
        }

        val classes = realmc.new_classArray(2)
        val props = realmc.new_propertyArrayArray(2)

        realmc.classArray_setitem(classes, 0, class_1)
        realmc.classArray_setitem(classes, 1, class_2)

        val properties_1 = realmc.new_propertyArray(3).also {
            realmc.propertyArray_setitem(it, 0, prop_1_1)
            realmc.propertyArray_setitem(it, 1, prop_1_2)
            realmc.propertyArray_setitem(it, 2, prop_1_3)
        }
        realmc.propertyArrayArray_setitem(props, 0, properties_1)

        val properties_2 = realmc.new_propertyArray(2).also { properties ->
            listOf(
                realm_property_info_t().apply {
                    name = "int"
                    public_name = ""
                    type = realm_property_type_e.RLM_PROPERTY_TYPE_INT
                    collection_type = realm_collection_type_e.RLM_COLLECTION_TYPE_NONE
                    link_target = ""
                    link_origin_property_name = ""
                    key = rlmInvalidPropertyKey
                    flags = realm_property_flags_e.RLM_PROPERTY_INDEXED or realm_property_flags_e.RLM_PROPERTY_PRIMARY_KEY
                },
                realm_property_info_t().apply {
                    name = "strings"
                    public_name = ""
                    type = realm_property_type_e.RLM_PROPERTY_TYPE_STRING
                    collection_type = realm_collection_type_e.RLM_COLLECTION_TYPE_LIST
                    link_target = ""
                    link_origin_property_name = ""
                    key = rlmInvalidPropertyKey
                    flags = realm_property_flags_e.RLM_PROPERTY_NORMAL or realm_property_flags_e.RLM_PROPERTY_NULLABLE
                }
            ).forEachIndexed { i, prop ->
                realmc.propertyArray_setitem(properties, i, prop)
            }
        }
        realmc.propertyArrayArray_setitem(props, 1, properties_2)

        val realmSchemaNew = realmc.realm_schema_new(classes, 2, props)
        assertTrue(realmc.realm_schema_validate(realmSchemaNew, realm_schema_validation_mode_e.RLM_SCHEMA_VALIDATION_BASIC.toLong()))

        val config: Long = realmc.realm_config_new()

        val path = Files.createTempDirectory("android_tests").absolutePathString()
        realmc.realm_config_set_path(config, path + "/c_api_test.realm")
        realmc.realm_config_set_schema(config, realmSchemaNew)
        realmc.realm_config_set_schema_mode(config, realm_schema_mode_e.RLM_SCHEMA_MODE_AUTOMATIC)
        realmc.realm_config_set_schema_version(config, 1)

        val realm = realmc.realm_open(config)

        realmc.realm_release(config)
        realmc.realm_release(realmSchemaNew)

        // Schema validates
        val schema = realmc.realm_get_schema(realm)
        assertTrue(realmc.realm_schema_validate(schema, realm_schema_validation_mode_e.RLM_SCHEMA_VALIDATION_BASIC.toLong()))
        realmc.realm_release(schema)

        assertEquals(2, realmc.realm_get_num_classes(realm))

        // Output variables
        val found: BooleanArray = booleanArrayOf(false)
        val foo_info = realm_class_info_t()

        assertFalse(found[0])
        realmc.realm_find_class(realm, "foo", found, foo_info)
        assertTrue(found[0])
        realmc.realm_find_class(realm, "fo", found, foo_info)
        assertFalse(found[0])
        val bar_info = realm_class_info_t()
        realmc.realm_find_class(realm, "bar", found, bar_info)
        assertTrue(found[0])

        // Output variables
        val foo_int_property = realm_property_info_t()
        realmc.realm_find_property(realm, foo_info.key, "int", found, foo_int_property)
        assertTrue(found[0])
        val foo_str_property = realm_property_info_t()
        realmc.realm_find_property(realm, foo_info.key, "str", found, foo_str_property)
        assertTrue(found[0])
        // TODO API-FULL Repeat for all properties on all classes

        // Missing primary key
        val realmBeginWrite: Boolean = realmc.realm_begin_write(realm)
        assertFailsWith<RuntimeException> {
            val realmObjectCreate: Long = realmc.realm_object_create(realm, bar_info.key)
        }
        realmc.realm_commit(realm)

        // Objects
        val realmBeginWrite2: Boolean = realmc.realm_begin_write(realm)
        val foo1: Long = realmc.realm_object_create(realm, foo_info.key)
        val realmValueT = realm_value_t().apply {
            type = realm_value_type_e.RLM_TYPE_INT
            integer = 123
        }
        realmc.realm_set_value(foo1, foo_int_property.key, realm_value_t().apply { type = realm_value_type_e.RLM_TYPE_INT; integer = 123 }, false)
        realmc.realm_set_value(foo1, foo_str_property.key, realm_value_t().apply { type = realm_value_type_e.RLM_TYPE_STRING; string = "Hello, World!" }, false)
        val bar1: Long = realmc.realm_object_create_with_primary_key(realm, bar_info.key, realm_value_t().apply { type = realm_value_type_e.RLM_TYPE_INT; integer = 1 })

        realmc.realm_get_value(foo1, foo_int_property.key, realm_value_t())

        // TODO API-FULL Find with primary key

        // Query basics
        val query: Long = realmc.realm_query_parse(realm, foo_info.key, "str == $0", 1, realm_value_t().apply { type = realm_value_type_e.RLM_TYPE_STRING; string = "Hello, World!" })

        val count = realm_size_t()
        realmc.realm_query_count(query, count)

        val findFirstValue = realm_value_t()
        val findFirstFound = booleanArrayOf(false)
        realmc.realm_query_find_first(query, findFirstValue, findFirstFound)
        assertTrue(findFirstFound[0])
        assertEquals(realm_value_type_e.RLM_TYPE_LINK, findFirstValue.type)
        assertEquals(foo_info.key, findFirstValue.link.target_table)
        val realmObjectGetKey = realmc.realm_object_get_key(foo1)
        // Will not be true unless executed on a fresh realm
        assertEquals(realmObjectGetKey, findFirstValue.link.target)

        val results: Long = realmc.realm_query_find_all(query)

        realmc.realm_results_count(results, count)
        assertEquals(1, count.value)
        // TODO Query basics? min, max, sum, average
        //  https://github.com/realm/realm-kotlin/issues/64
        val minFound = booleanArrayOf(false)
        val minValue = realm_value_t()
        realmc.realm_results_min(results, foo_int_property.key, minValue, minFound)
        assertTrue(minFound.get(0))
        assertEquals(realm_value_type_e.RLM_TYPE_INT, minValue.type)
        assertEquals(123, minValue.integer)

        // TODO API-FULL Set wrong field type

        // TODO Deletes
        //  https://github.com/realm/realm-kotlin/issues/67
        // TODO Lists
        //  https://github.com/realm/realm-kotlin/issues/68

        // TODO Notifications
        //  https://github.com/realm/realm-kotlin/issues/65

        realmc.realm_commit(realm)
    }

    @Test
    fun parentChildRelationship() {
        val context = InstrumentationRegistry.getInstrumentation().context

        System.loadLibrary("realmc")
        println(realmc.realm_get_library_version())

        val rlmInvalidPropertyKey = realmc.getRLM_INVALID_PROPERTY_KEY()
        val rlmInvalidClassKey = realmc.getRLM_INVALID_CLASS_KEY()

        val class_1 = realm_class_info_t().apply {
            name = "Parent"
            primary_key = ""
            num_properties = 1
            num_computed_properties = 0
            key = rlmInvalidClassKey
            flags = realm_class_flags_e.RLM_CLASS_NORMAL
        }

        val prop_1_1 = realm_property_info_t().apply {
            name = "child"
            public_name = ""
            type = realm_property_type_e.RLM_PROPERTY_TYPE_OBJECT
            collection_type = realm_collection_type_e.RLM_COLLECTION_TYPE_NONE
            link_target = "Child"
            link_origin_property_name = ""
            key = rlmInvalidPropertyKey
            flags = realm_property_flags_e.RLM_PROPERTY_NULLABLE
        }

        val class_2 = realm_class_info_t().apply {
            name = "Child"
            primary_key = ""
            num_properties = 1
            num_computed_properties = 0
            key = rlmInvalidClassKey
            flags = realm_class_flags_e.RLM_CLASS_NORMAL
        }
        val prop_2_1 = realm_property_info_t().apply {
            name = "name"
            public_name = ""
            type = realm_property_type_e.RLM_PROPERTY_TYPE_STRING
            collection_type = realm_collection_type_e.RLM_COLLECTION_TYPE_NONE
            link_target = ""
            link_origin_property_name = ""
            key = rlmInvalidPropertyKey
            flags = realm_property_flags_e.RLM_PROPERTY_NORMAL
        }

        val classes = realmc.new_classArray(2)
        val props = realmc.new_propertyArrayArray(2)

        realmc.classArray_setitem(classes, 0, class_1)
        realmc.classArray_setitem(classes, 1, class_2)

        val properties_1 = realmc.new_propertyArray(1).also {
            realmc.propertyArray_setitem(it, 0, prop_1_1)
        }
        realmc.propertyArrayArray_setitem(props, 0, properties_1)

        val properties_2 = realmc.new_propertyArray(1).also {
            realmc.propertyArray_setitem(it, 0, prop_2_1)
        }
        realmc.propertyArrayArray_setitem(props, 1, properties_2)

        val realmSchemaNew = realmc.realm_schema_new(classes, 2, props)
        assertTrue(realmc.realm_schema_validate(realmSchemaNew, realm_schema_validation_mode_e.RLM_SCHEMA_VALIDATION_BASIC.toLong()))

        val config: Long = realmc.realm_config_new()

        realmc.realm_config_set_path(config, context.filesDir.absolutePath + "/c_api_link.realm")
        realmc.realm_config_set_schema(config, realmSchemaNew)
        realmc.realm_config_set_schema_mode(config, realm_schema_mode_e.RLM_SCHEMA_MODE_AUTOMATIC)
        realmc.realm_config_set_schema_version(config, 1)

        val realm = realmc.realm_open(config)

        realmc.realm_release(config)
        realmc.realm_release(realmSchemaNew)

        // Schema validates
        val schema = realmc.realm_get_schema(realm)
        assertTrue(realmc.realm_schema_validate(schema, realm_schema_validation_mode_e.RLM_SCHEMA_VALIDATION_BASIC.toLong()))
        realmc.realm_release(schema)

        assertEquals(2, realmc.realm_get_num_classes(realm))

        // Output variables
        val found: BooleanArray = booleanArrayOf(false)
        val parent_info = realm_class_info_t()

        assertFalse(found[0])
        realmc.realm_find_class(realm, "Parent", found, parent_info)
        assertTrue(found[0])

        val child_info = realm_class_info_t()
        realmc.realm_find_class(realm, "Child", found, child_info)
        assertTrue(found[0])

        // Output variables
        val child_property = realm_property_info_t()
        realmc.realm_find_property(realm, parent_info.key, "child", found, child_property)
        assertTrue(found[0])

        // Objects
        realmc.realm_begin_write(realm)
        val parent1: Long = realmc.realm_object_create(realm, parent_info.key)
        val child: Long = realmc.realm_object_create(realm, child_info.key)
        realmc.realm_set_value(parent1, child_property.key, realm_value_t().apply { type = realm_value_type_e.RLM_TYPE_LINK; link = realmc.realm_object_as_link(child) }, false)

        realmc.realm_get_value(parent1, child_property.key, realm_value_t())
    }

    private fun createIntProperty(propertyName: String): realm_property_info_t {
        return realm_property_info_t().apply {
            name = propertyName
            public_name = ""
            type = realm_property_type_e.RLM_PROPERTY_TYPE_INT
            collection_type = realm_collection_type_e.RLM_COLLECTION_TYPE_NONE
            link_target = ""
            link_origin_property_name = ""
            key = realmc.getRLM_INVALID_PROPERTY_KEY()
            flags = realm_property_flags_e.RLM_PROPERTY_NORMAL
        }
    }

    private fun createClass(className: String, numberOfProperties: Long): realm_class_info_t {
        return realm_class_info_t().apply {
            name = className
            primary_key = ""
            num_properties = numberOfProperties
            num_computed_properties = 0
            key = realmc.getRLM_INVALID_CLASS_KEY()
            flags = realm_class_flags_e.RLM_CLASS_NORMAL
        }
    }

    private fun createSchema(properties: List<Pair<realm_class_info_t, List<realm_property_info_t>>>): Long {
        val classes = realmc.new_classArray(properties.size) // Array of classes
        val classesProperties = realmc.new_propertyArrayArray(properties.size) // Array of array (properties, 1 array per class)
        for ((classIndex, classProperties: Pair<realm_class_info_t, List<realm_property_info_t>>) in properties.withIndex()) {
            realmc.classArray_setitem(classes, classIndex, classProperties.first)
            val properties = realmc.new_propertyArray(classProperties.second.size).also {
                for ((propertyIndex, property) in classProperties.second.withIndex()) {
                    realmc.propertyArray_setitem(it, propertyIndex, property)
                }
            }
            realmc.propertyArrayArray_setitem(classesProperties, classIndex, properties)
        }
        return realmc.realm_schema_new(classes, properties.size.toLong(), classesProperties)
    }

    private fun findTable(realm: Long, name: String): realm_class_info_t {
        val class_info = realm_class_info_t()
        val found: BooleanArray = booleanArrayOf(false)
        realmc.realm_find_class(realm, name, found, class_info)
        assertTrue(found[0])
        return class_info
    }

    private fun findProperty(realm: Long, table: String, propertyName: String): realm_property_info_t {
        val property = realm_property_info_t()
        val found: BooleanArray = booleanArrayOf(false)
        realmc.realm_find_property(realm, findTable(realm, table).key, propertyName, found, property)
        assertTrue(found[0])
        return property
    }
}
