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

package io.realm

import io.realm.interop.ClassFlag
import io.realm.interop.CollectionType
import io.realm.interop.Property
import io.realm.interop.PropertyFlag
import io.realm.interop.PropertyType
import io.realm.interop.RealmInterop
import io.realm.interop.SchemaMode
import io.realm.interop.SchemaValidationMode
import io.realm.interop.Table
import io.realm.interop.set
import io.realm.interop.toKString
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.NativePtr
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cValue
import kotlinx.cinterop.cValuesOf
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.toKString
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import realm_wrapper.RLM_CLASS_NORMAL
import realm_wrapper.RLM_COLLECTION_TYPE_NONE
import realm_wrapper.RLM_PROPERTY_NORMAL
import realm_wrapper.RLM_PROPERTY_TYPE_INT
import realm_wrapper.realm_class_info_t
import realm_wrapper.realm_close
import realm_wrapper.realm_config_new
import realm_wrapper.realm_config_set_path
import realm_wrapper.realm_config_set_schema
import realm_wrapper.realm_config_set_schema_mode
import realm_wrapper.realm_config_set_schema_version
import realm_wrapper.realm_config_t
import realm_wrapper.realm_error_t
import realm_wrapper.realm_find_class
import realm_wrapper.realm_get_last_error
import realm_wrapper.realm_get_library_version
import realm_wrapper.realm_get_num_classes
import realm_wrapper.realm_get_schema
import realm_wrapper.realm_is_closed
import realm_wrapper.realm_open
import realm_wrapper.realm_property_info_t
import realm_wrapper.realm_schema_mode_e
import realm_wrapper.realm_schema_new
import realm_wrapper.realm_schema_t
import realm_wrapper.realm_schema_validate
import realm_wrapper.realm_string_t
import realm_wrapper.realm_t
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Direct tests of the 'cinterop' low level C-API wrapper for Darwin platforms.
// These test are not thought as being exhaustive, but is more to provide a playground for
// experiments and maybe more relevant for reproduction of C-API issues.
class CinteropTest {

//    @Test
//    fun version() {
//        assertEquals("10.5.2", realm_get_library_version()!!.toKString())
//    }

    @Test
    fun open_different_versions() {
        memScoped {
            val path = "c_api_test.realm"
            // First config
            val prop_1_1 = alloc<realm_property_info_t>().apply {
                // All strings need to be initialized
                name = "int".cstr.ptr
                public_name = "".cstr.ptr
                link_target = "".cstr.ptr
                link_origin_property_name = "".cstr.ptr
                type = RLM_PROPERTY_TYPE_INT
                collection_type = RLM_COLLECTION_TYPE_NONE
                flags = RLM_PROPERTY_NORMAL.toInt()
            }

            val classProperties_1: CPointer<CPointerVarOf<CPointer<realm_property_info_t>>> = cValuesOf(prop_1_1.ptr).ptr

            val classes_1: CPointer<realm_class_info_t> = allocArray(1)
            classes_1[0].apply {
                name = "foo".cstr.ptr
                primary_key = "".cstr.ptr
                num_properties = 1.toULong()
                num_computed_properties = 0.toULong()
                flags = RLM_CLASS_NORMAL.toInt()
            }

            val realmSchemaNew_1 = realm_schema_new(classes_1, 1.toULong(), classProperties_1)

            val config_1 = realm_config_new()
            realm_config_set_path(config_1, path)
            realm_config_set_schema(config_1, realmSchemaNew_1)
            realm_config_set_schema_mode(config_1, realm_schema_mode_e.RLM_SCHEMA_MODE_AUTOMATIC)
//            realm_config_set_schema_version(config_1, 1)
            realm_config_set_schema_version(config_1, 2)

            val realm_1: CPointer<realm_t>? = realm_open(config_1)
            assertEquals(1U, realm_get_num_classes(realm_1))
            assertNotNull(realm_1)
            val schema: CPointer<realm_schema_t>? = realm_get_schema(realm_1)
            assertNotNull(schema)

            realm_close(realm_1)
            assertTrue(realm_is_closed(realm_1))

            // Second config
            val prop_1_2 = alloc<realm_property_info_t>().apply {
                // All strings need to be initialized
                name = "int".cstr.ptr
                public_name = "".cstr.ptr
                link_target = "".cstr.ptr
                link_origin_property_name = "".cstr.ptr
                type = RLM_PROPERTY_TYPE_INT
                collection_type = RLM_COLLECTION_TYPE_NONE
                flags = RLM_PROPERTY_NORMAL.toInt()
            }

            val classProperties_2: CPointer<CPointerVarOf<CPointer<realm_property_info_t>>> = cValuesOf(prop_1_2.ptr).ptr

            val classes_2: CPointer<realm_class_info_t> = allocArray(1)
            classes_2[0].apply {
                name = "bar".cstr.ptr
                primary_key = "".cstr.ptr
                num_properties = 1.toULong()
                num_computed_properties = 0.toULong()
                flags = RLM_CLASS_NORMAL.toInt()
            }

            val realmSchemaNew_2 = realm_schema_new(classes_2, 1.toULong(), classProperties_2)

            val config_2 = realm_config_new()
            realm_config_set_path(config_2, path)
            realm_config_set_schema(config_2, realmSchemaNew_2)
//            realm_config_set_schema_mode(config_2, realm_schema_mode_e.RLM_SCHEMA_MODE_RESET_FILE)
            realm_config_set_schema_mode(config_2, realm_schema_mode_e.RLM_SCHEMA_MODE_AUTOMATIC)
//            realm_config_set_schema_version(config_2, 0)
            realm_config_set_schema_version(config_2, 1)

//            assertFails {
            val realmOpen = realm_open(config_2)
            val kjahsdkhj = 0
//            }

//            val realm_2: CPointer<realm_t>? = realm_open(config_2)
//            assertEquals(1U, realm_get_num_classes(realm_2))
//            assertNotNull(realm_2)
//            val schema_2: CPointer<realm_schema_t>? = realm_get_schema(realm_2)
//            assertNotNull(schema_2)
        }
    }

//    @Test
//    fun cinterop_cinterop() {
//        memScoped {
//            val prop_1_1 = alloc<realm_property_info_t>().apply {
//                // All strings need to be initialized
//                name = "int".cstr.ptr
//                public_name = "".cstr.ptr
//                link_target = "".cstr.ptr
//                link_origin_property_name = "".cstr.ptr
//                type = RLM_PROPERTY_TYPE_INT
//                collection_type = RLM_COLLECTION_TYPE_NONE
//                flags = RLM_PROPERTY_NORMAL.toInt()
//            }
//
//            val classes: CPointer<realm_class_info_t> = allocArray(1)
//            classes[0].apply {
//                name = "foo".cstr.ptr
//                primary_key = "".cstr.ptr
//                num_properties = 1.toULong()
//                num_computed_properties = 0.toULong()
//                flags = RLM_CLASS_NORMAL.toInt()
//            }
//
//            val classProperties: CPointer<CPointerVarOf<CPointer<realm_property_info_t>>> = cValuesOf(prop_1_1.ptr).ptr
//            val realmSchemaNew = realm_schema_new(classes, 1.toULong(), classProperties)
//
//            assertNoError()
//            assertTrue(realm_schema_validate(realmSchemaNew, SchemaValidationMode.RLM_SCHEMA_VALIDATION_BASIC.nativeValue.toULong()))
//
//            val config = realm_config_new()
//            realm_config_set_path(config, "c_api_test.realm")
//            realm_config_set_schema(config, realmSchemaNew)
//            realm_config_set_schema_mode(config, realm_schema_mode_e.RLM_SCHEMA_MODE_AUTOMATIC)
//            realm_config_set_schema_version(config, 1)
//
//            val realm: CPointer<realm_t>? = realm_open(config)
//            assertEquals(1U, realm_get_num_classes(realm))
//            assertNotNull(realm)
//            val schema: CPointer<realm_schema_t>? = realm_get_schema(realm)
//            assertNotNull(schema)
//
//            val found = alloc<BooleanVar>()
//            val classInfo = alloc<realm_class_info_t>()
//            val realmFindClass = realm_find_class(realm, "foo", found.ptr, classInfo.ptr)
//            assertTrue(realmFindClass)
//            assertTrue(found.value)
//            assertEquals("foo", classInfo.name?.toKString())
//            assertEquals(1UL, classInfo.num_properties)
//
//            val propertyInfo = alloc<realm_property_info_t>()
//            val realmFindProperty = realm_wrapper.realm_find_property(realm, classInfo.key, "int", found.ptr, propertyInfo.ptr)
//            assertTrue(realmFindProperty)
//            assertTrue(found.value)
//            assertEquals("int", propertyInfo.name?.toKString())
//        }
//    }
//
//    @Test
//    fun cinterop_realmInterop() {
//        val tables = listOf(
//            Table(
//                name = "foo",
//                primaryKey = "",
//                flags = setOf(ClassFlag.RLM_CLASS_NORMAL),
//                properties = listOf(
//                    Property(
//                        name = "int",
//                        type = PropertyType.RLM_PROPERTY_TYPE_INT,
//                        collectionType = CollectionType.RLM_COLLECTION_TYPE_NONE,
//                        flags = setOf(PropertyFlag.RLM_PROPERTY_NORMAL)
//                    )
//                )
//            )
//        )
//
//        val schema = RealmInterop.realm_schema_new(tables)
//
//        memScoped {
//            val nativeConfig = RealmInterop.realm_config_new()
//
//            RealmInterop.realm_config_set_path(nativeConfig, "default.realm")
//            RealmInterop.realm_config_set_schema(nativeConfig, schema)
//            RealmInterop.realm_config_set_schema_mode(nativeConfig, SchemaMode.RLM_SCHEMA_MODE_AUTOMATIC)
//            RealmInterop.realm_config_set_schema_version(nativeConfig, 1)
//
//            val realm = RealmInterop.realm_open(nativeConfig)
//            assertEquals(1L, RealmInterop.realm_get_num_classes(realm))
//        }
//    }
//
//    @Test
//    fun realmStringSet_empty() {
//        memScoped {
//            val s = alloc<realm_string_t>()
//            s.set(memScope, "")
//            assertEquals(0UL, s.size)
//            assertNull(s.data)
//        }
//    }
//
//    @Test
//    fun realmStringSet_string() {
//        memScoped {
//            val s = alloc<realm_string_t>()
//            s.set(memScope, "Realm")
//            val actualSize = s.size.toInt()
//            assertEquals(5, actualSize)
//            val data = s.data!!.readBytes(actualSize)
//            assertTrue("Realm".encodeToByteArray(0, actualSize).contentEquals(data))
//        }
//    }
//
//    @Test
//    fun toKString_empty() {
//        var r: String? = null
//        memScoped {
//            val s = alloc<realm_string_t>()
//            s.set(memScope, "")
//            r = s.toKString()
//        }
//        assertEquals("", r)
//    }
//
//    @Test
//    fun toRString_string() {
//        val value = "Realm"
//        var r: String? = null
//        memScoped {
//            val s = alloc<realm_string_t>()
//            s.set(memScope, value)
//            r = s.toKString()
//        }
//        assertEquals(value, r)
//    }
}

fun realm_string_t.setRealmString(memScope: MemScope, str: String) {
    data = str.cstr.getPointer(memScope)
    size = str.length.toULong()
}

fun realmStringStruct(memScope: MemScope, str: String) = cValue<realm_string_t> {
    setRealmString(memScope, str)
}

fun assertNoError() {
    val error = cValue<realm_error_t>()
    val realmGetLastError = realm_get_last_error(error)
    assertFalse(realmGetLastError)

    error.useContents {
        assertEquals(0, kind.code)
        assertNull(message)
        assertEquals(0.toUInt(), this.error)
    }
}
