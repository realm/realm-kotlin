package io.realm

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
import kotlinx.cinterop.toKString
import kotlinx.cinterop.useContents
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
import realm_wrapper.realm_get_last_error
import realm_wrapper.realm_get_library_version
import realm_wrapper.realm_get_schema
import realm_wrapper.realm_open
import realm_wrapper.realm_property_info_t
import realm_wrapper.realm_schema_mode_e
import realm_wrapper.realm_schema_new
import realm_wrapper.realm_schema_t
import realm_wrapper.realm_schema_validate
import realm_wrapper.realm_string_t
import realm_wrapper.realm_t
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CinteropTest {

    // FIXME Testing basic C API wrapper interaction (like in AndroidTest's CinteropTest)
    @Test
    fun cinterop_cinterop() {

        println(realm_get_library_version()!!.toKString())

        memScoped {
            val prop_1_1 = alloc<realm_property_info_t>().apply {
                name.setRealmString(this@memScoped, "int")
                type = RLM_PROPERTY_TYPE_INT
                collection_type = RLM_COLLECTION_TYPE_NONE
                flags = RLM_PROPERTY_NORMAL.toInt()
            }

            val classes: CPointer<realm_class_info_t> = allocArray(1)
            classes[0].apply {
                name.setRealmString(this@memScoped, "foo")
                primary_key.setRealmString(this@memScoped, "")
                num_properties = 1.toULong()
                num_computed_properties = 0.toULong()
                flags = RLM_CLASS_NORMAL.toInt()
            }

            val classProperties: CPointer  <CPointerVarOf<CPointer<realm_property_info_t>>> = cValuesOf(prop_1_1.ptr).ptr
            val realmSchemaNew = realm_schema_new(classes, 1.toULong(), classProperties)

            assertNoError()
            assertTrue(realm_schema_validate(realmSchemaNew))

            val config = realm_config_new()
            realm_config_set_path(config, realmStringStruct(memScope, "c_api_test.realm"))
            realm_config_set_schema(config, realmSchemaNew)
            realm_config_set_schema_mode(config, realm_schema_mode_e.RLM_SCHEMA_MODE_AUTOMATIC)
            realm_config_set_schema_version(config, 1)

            val realm: CPointer<realm_t>? = realm_open(config)
            assertNotNull(realm)
            val schema: CPointer<realm_schema_t>? = realm_get_schema(realm)
            assertNotNull(schema)
        }
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
        assertEquals(0, kind.code)
        assertNull(message.data)
        assertEquals(0.toUInt(), this.error)
    }
}