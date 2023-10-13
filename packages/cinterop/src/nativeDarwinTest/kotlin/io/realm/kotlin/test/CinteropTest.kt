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

package io.realm.kotlin.test

import io.realm.kotlin.internal.interop.CPointerWrapper
import io.realm.kotlin.internal.interop.ClassFlags
import io.realm.kotlin.internal.interop.ClassInfo
import io.realm.kotlin.internal.interop.CollectionType
import io.realm.kotlin.internal.interop.ErrorCode
import io.realm.kotlin.internal.interop.PropertyFlags
import io.realm.kotlin.internal.interop.PropertyInfo
import io.realm.kotlin.internal.interop.PropertyType
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmSchemaT
import io.realm.kotlin.internal.interop.SchemaMode
import io.realm.kotlin.internal.interop.SchemaValidationMode
import io.realm.kotlin.internal.interop.set
import io.realm.kotlin.internal.interop.toKotlinString
import io.realm.kotlin.internal.interop.use
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.MemScope
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
import realm_wrapper.realm_config_new
import realm_wrapper.realm_config_set_path
import realm_wrapper.realm_config_set_schema
import realm_wrapper.realm_config_set_schema_mode
import realm_wrapper.realm_config_set_schema_version
import realm_wrapper.realm_error_t
import realm_wrapper.realm_find_class
import realm_wrapper.realm_get_last_error
import realm_wrapper.realm_get_num_classes
import realm_wrapper.realm_get_schema
import realm_wrapper.realm_open
import realm_wrapper.realm_property_info_t
import realm_wrapper.realm_schema_mode_e
import realm_wrapper.realm_schema_new
import realm_wrapper.realm_schema_t
import realm_wrapper.realm_schema_validate
import realm_wrapper.realm_string_t
import realm_wrapper.realm_t
import kotlin.native.internal.GC
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Direct tests of the 'cinterop' low level C-API wrapper for Darwin platforms.
// These test are not thought as being exhaustive, but is more to provide a playground for
// experiments and maybe more relevant for reproduction of C-API issues.
class CinteropTest {
    /**
     * Tests whether our autorelease pointer wrapper releases native memory.
     *
     * Allocates a Realm pointer wrapped with our GC autorelease wrapper, then returns the reference
     * to the releasable pointer that would tell if the underlying pointer has been released.
     */
    @Test
    fun cpointerWrapper_releasesWhenGCed() {
        val releasablePointer = {
            memScoped {
                val realmSchemaNew = realm_schema_new(
                    classes = allocArray(0),
                    num_classes = 0u,
                    class_properties = allocArray(0)
                )

                CPointerWrapper<RealmSchemaT>(realmSchemaNew)._ptr
            }
        }()

        // The pointer has not been reclaimed
        assertFalse(releasablePointer.released.value)

        // Trigger GC and wait for some time to allow it to collect the object
        for (i in 0..5) {
            GC.collect()
            platform.posix.sleep(5u)

            // if reclaimed stop looping
            if (releasablePointer.released.value) break
        }

        // The pointer has been reclaimed
        assertTrue(releasablePointer.released.value, "Pointer was not reclaimed")
    }

    @Test
    fun cinterop_cinterop() {
        memScoped {
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

            val classes: CPointer<realm_class_info_t> = allocArray(1)
            classes[0].apply {
                name = "foo".cstr.ptr
                primary_key = "".cstr.ptr
                num_properties = 1.toULong()
                num_computed_properties = 0.toULong()
                flags = RLM_CLASS_NORMAL.toInt()
            }

            val classProperties: CPointer<CPointerVarOf<CPointer<realm_property_info_t>>> =
                cValuesOf(prop_1_1.ptr).ptr
            val realmSchemaNew = realm_schema_new(classes, 1.toULong(), classProperties)

            assertNoError()
            assertTrue(
                realm_schema_validate(
                    realmSchemaNew,
                    SchemaValidationMode.RLM_SCHEMA_VALIDATION_BASIC.nativeValue.toULong()
                )
            )

            val config = realm_config_new()
            realm_config_set_path(config, "c_api_test.realm")
            realm_config_set_schema(config, realmSchemaNew)
            realm_config_set_schema_mode(config, realm_schema_mode_e.RLM_SCHEMA_MODE_AUTOMATIC)
            realm_config_set_schema_version(config, 1)

            val realm: CPointer<realm_t>? = realm_open(config)
            assertEquals(1U, realm_get_num_classes(realm))
            assertNotNull(realm)
            val schema: CPointer<realm_schema_t>? = realm_get_schema(realm)
            assertNotNull(schema)

            val found = alloc<BooleanVar>()
            val classInfo = alloc<realm_class_info_t>()
            val realmFindClass = realm_find_class(realm, "foo", found.ptr, classInfo.ptr)
            assertTrue(realmFindClass)
            assertTrue(found.value)
            assertEquals("foo", classInfo.name?.toKString())
            assertEquals(1UL, classInfo.num_properties)

            val propertyInfo = alloc<realm_property_info_t>()
            val realmFindProperty = realm_wrapper.realm_find_property(
                realm,
                classInfo.key,
                "int",
                found.ptr,
                propertyInfo.ptr
            )
            assertTrue(realmFindProperty)
            assertTrue(found.value)
            assertEquals("int", propertyInfo.name?.toKString())
        }
    }

    @Test
    fun cinterop_realmInterop() {
        val tables = listOf(
            ClassInfo(
                name = "foo",
                primaryKey = "",
                flags = ClassFlags.RLM_CLASS_NORMAL,
                numProperties = 1,
            ) to listOf(
                PropertyInfo(
                    name = "int",
                    type = PropertyType.RLM_PROPERTY_TYPE_INT,
                    collectionType = CollectionType.RLM_COLLECTION_TYPE_NONE,
                    flags = PropertyFlags.RLM_PROPERTY_NORMAL,
                )
            )
        )

        val schema = RealmInterop.realm_schema_new(tables)

        memScoped {
            val nativeConfig = RealmInterop.realm_config_new()

            RealmInterop.realm_config_set_path(nativeConfig, "default.realm")
            RealmInterop.realm_config_set_schema(nativeConfig, schema)
            RealmInterop.realm_config_set_schema_mode(
                nativeConfig,
                SchemaMode.RLM_SCHEMA_MODE_AUTOMATIC
            )
            RealmInterop.realm_config_set_schema_version(nativeConfig, 1)
            RealmInterop.realm_create_scheduler()
                .use { scheduler ->
                    val (realm, fileCreated) = RealmInterop.realm_open(nativeConfig, scheduler)
                    assertEquals(1L, RealmInterop.realm_get_num_classes(realm))
                    RealmInterop.realm_close(realm)
                }
        }
    }

    @Test
    fun realmStringSet_empty() {
        memScoped {
            val s = alloc<realm_string_t>()
            s.set(memScope, "")
            assertEquals(0UL, s.size)
            assertNotNull(s.data)
        }
    }

    @Test
    fun realmStringSet_string() {
        memScoped {
            val s = alloc<realm_string_t>()
            s.set(memScope, "Realm")
            val actualSize = s.size.toInt()
            assertEquals(5, actualSize)
            val data = s.data!!.readBytes(actualSize)
            assertTrue("Realm".encodeToByteArray(0, actualSize).contentEquals(data))
        }
    }

    @Test
    fun toKString_empty() {
        var r: String? = null
        memScoped {
            val s = alloc<realm_string_t>()
            s.set(memScope, "")
            r = s.toKotlinString()
        }
        assertEquals("", r)
    }

    @Test
    fun toRString_string() {
        val value = "Realm"
        var r: String? = null
        memScoped {
            val s = alloc<realm_string_t>()
            s.set(memScope, value)
            r = s.toKotlinString()
        }
        assertEquals(value, r)
    }

    /**
     * Monitors for changes in Core defined types.
     *
     * It checks that all the error code values defined in realm_errno are mapped by ErrorCode
     */
    @Test
    fun errorCodes_enumTest() {
        val coreErrorNativeValues = realm_wrapper.realm_errno.values()
            .map {
                it.value.toInt()
            }
            .toIntArray()

        val errorCodeValues = coreErrorNativeValues
            .map {
                ErrorCode.of(it)
            }
            .filterNotNull()
            .toSet()

        // validate that all error codes are mapped
        assertEquals(coreErrorNativeValues.size, errorCodeValues.size)
    }
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
        assertEquals(0U, categories)
        assertNull(message)
        assertEquals(realm_wrapper.realm_errno.RLM_ERR_NONE, this.error)
    }
}
