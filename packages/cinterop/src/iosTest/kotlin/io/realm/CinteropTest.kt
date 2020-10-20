package io.realm

import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cValue
import kotlinx.cinterop.cValuesOf
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
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
import realm_wrapper.realm_get_library_version
import realm_wrapper.realm_property_info_t
import realm_wrapper.realm_schema_mode_e
import realm_wrapper.realm_schema_new
import realm_wrapper.realm_schema_validate
import realm_wrapper.realm_string_t
import kotlin.test.Test
import kotlin.test.assertTrue

@ExperimentalUnsignedTypes
class CinteropTest {

    // FIXME Testing basic C API wrapper interaction (like in AndroidTest's CinteropTest)
    @Test
    fun cinterop_cinterop() {

        println(realm_get_library_version()!!.toKString())

        memScoped {
            val prop_1_1 = alloc<realm_property_info_t>().apply {
                name.data = "int".cstr.ptr
                name.size = 3.toULong()

                type = RLM_PROPERTY_TYPE_INT

                collection_type = RLM_COLLECTION_TYPE_NONE

                link_target.data = "".cstr.ptr
                link_target.size = 0.toULong()

                link_origin_property_name.data = "".cstr.ptr
                link_origin_property_name.size = 0.toULong()

//                key.col_key = 42  Unused when defining the schema.

                flags = RLM_PROPERTY_NORMAL.toInt()
            }

//            val classes: kotlinx.cinterop.CValuesRef<realm_class_info_t>? = allocArray<realm_class_info_t>(1)
            val classes = allocArray<realm_class_info_t>(1)
            classes[0].apply {
                name.data = "foo".cstr.ptr
                name.size = 3.toULong()

                primary_key.data = "".cstr.ptr
                primary_key.size = 0.toULong()

                num_properties = 3.toULong()
                num_computed_properties = 0.toULong()

//                key.table_key = 123.toUInt() Unused when defining the schema.

                flags = RLM_CLASS_NORMAL.toInt()
            }
            val classProperties = cValuesOf(prop_1_1.ptr).ptr
            val realmSchemaNew = realm_schema_new(classes, 1.toULong(), classProperties)
            val config = realm_config_new()
            val path = cValue<realm_string_t> {
//                data = "alloc()".cstr.getPointer(MemScope()) alternatively
                val p = "c_api_test.realm"
                data = mycstring(p)
                size = p.length.toULong()
            }

            assertTrue(realm_schema_validate(realmSchemaNew))
            realm_config_set_path(config, path)
            realm_config_set_schema(config, realmSchemaNew)
            realm_config_set_schema_mode(config, realm_schema_mode_e.RLM_SCHEMA_MODE_AUTOMATIC)
            realm_config_set_schema_version(config, 1)

            //TODO call realm_get_schema() to obtain and check value for table_key & col_key
        }
    }

    fun mycstring(s: String): CPointer<ByteVarOf<Byte>> = s.cstr.place(nativeHeap.allocArray(s.length * 4))
}
