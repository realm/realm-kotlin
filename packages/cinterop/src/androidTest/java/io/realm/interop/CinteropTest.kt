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
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CinteropTest {

    @Test
    fun cinterop_swig() {
        val context = InstrumentationRegistry.getInstrumentation().context

        System.loadLibrary("realmc")
        println(realmc.realm_get_library_version())

        val class_1 = realm_class_info_t().apply {
            name = "foo"
            primary_key = ""
            num_properties = 3
            num_computed_properties = 0
            key = realm_table_key_t()
            flags = realm_class_flags_e.RLM_CLASS_NORMAL
        }

        val prop_1_1 = realm_property_info_t().apply {
            name = "int"
            public_name = ""
            type = realm_property_type_e.RLM_PROPERTY_TYPE_INT
            collection_type = realm_collection_type_e.RLM_COLLECTION_TYPE_NONE
            link_target = ""
            link_origin_property_name = ""
            key = realm_col_key_t()
            flags = realm_property_flags_e.RLM_PROPERTY_NORMAL
        }
        val prop_1_2 = realm_property_info_t().apply {
            name = "str"
            public_name = ""
            type = realm_property_type_e.RLM_PROPERTY_TYPE_STRING
            collection_type = realm_collection_type_e.RLM_COLLECTION_TYPE_NONE
            link_target = ""
            link_origin_property_name = ""
            key = realm_col_key_t()
            flags = realm_property_flags_e.RLM_PROPERTY_NORMAL
        }
        val prop_1_3 = realm_property_info_t().apply {
            name = "bars"
            public_name = ""
            type = realm_property_type_e.RLM_PROPERTY_TYPE_OBJECT
            collection_type = realm_collection_type_e.RLM_COLLECTION_TYPE_LIST
            link_target = "bar"
            link_origin_property_name = ""
            key = realm_col_key_t()
            flags = realm_property_flags_e.RLM_PROPERTY_NORMAL
        }

        val class_2 = realm_class_info_t().apply {
            name = "bar"
            primary_key = "int"
            num_properties = 2
            num_computed_properties = 0
            key = realm_table_key_t()
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
                    key = realm_col_key_t()
                    flags = realm_property_flags_e.RLM_PROPERTY_INDEXED or realm_property_flags_e.RLM_PROPERTY_PRIMARY_KEY
                },
                realm_property_info_t().apply {
                    name = "strings"
                    public_name = ""
                    type = realm_property_type_e.RLM_PROPERTY_TYPE_STRING
                    collection_type = realm_collection_type_e.RLM_COLLECTION_TYPE_LIST
                    link_target = ""
                    link_origin_property_name = ""
                    key = realm_col_key_t()
                    flags = realm_property_flags_e.RLM_PROPERTY_NORMAL or realm_property_flags_e.RLM_PROPERTY_NULLABLE
                }
            ).forEachIndexed { i, prop ->
                realmc.propertyArray_setitem(properties, i, prop)
            }
        }
        realmc.propertyArrayArray_setitem(props, 1, properties_2)

        val realmSchemaNew = realmc.realm_schema_new(classes, 2, props)
        assertTrue(realmc.realm_schema_validate(realmSchemaNew))

        val config: Long = realmc.realm_config_new()

        realmc.realm_config_set_path(config, context.filesDir.absolutePath + "/c_api_test.realm")
        realmc.realm_config_set_schema(config, realmSchemaNew)
        realmc.realm_config_set_schema_mode(config, realm_schema_mode_e.RLM_SCHEMA_MODE_AUTOMATIC)
        realmc.realm_config_set_schema_version(config, BigInteger("1"))

        val realm = realmc.realm_open(config)

        realmc.realm_release(config)
        realmc.realm_release(realmSchemaNew)

        // Schema validates
        val schema = realmc.realm_get_schema(realm)
        assertTrue(realmc.realm_schema_validate(schema))
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
        assertEquals(foo_info.key.table_key, findFirstValue.link.target_table.table_key)
        val realmObjectGetKey = realmc.realm_object_get_key(foo1)
        // Will not be true unless executed on a fresh realm
        assertEquals(realmObjectGetKey.obj_key, findFirstValue.link.target.obj_key)

        val results = realmc.realm_query_find_all(query)

        realmc.realm_results_count(results, count)
        assertEquals(1, count.value)
        // TODO Query basics? min, max, sum, average
        //  https://github.com/realm/realm-kotlin/issues/64
        val minFound = booleanArrayOf(false)
        val minValue = realm_value_t()
        realmc.realm_results_min(results, foo_int_property.key, minValue, minFound)
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
}
