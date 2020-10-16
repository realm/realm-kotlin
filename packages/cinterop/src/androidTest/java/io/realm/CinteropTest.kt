package io.realm

import android.support.test.runner.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import realm_class_flags_e
import realm_class_info_t
import realm_col_key_t
import realm_collection_type_e
import realm_property_flags_e
import realm_property_info_t
import realm_property_type_e
import realm_schema_mode_e
import realm_table_key_t
import realmc
import java.math.BigInteger
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CinteropTest {

    @Test
    fun cinterop_swig() {
        System.loadLibrary("realmc")
        println(realmc.realm_get_library_version())

        val class_1 = realm_class_info_t().apply {
            name = "foo"
            primary_key = ""
            num_properties = 3
            num_computed_properties = 0
            key = realm_table_key_t()
            flags = realm_class_flags_e.RLM_CLASS_NORMAL.swigValue()
        }

        val prop_1_1 = realm_property_info_t().apply {
            name = "int"
            public_name = ""
            type = realm_property_type_e.RLM_PROPERTY_TYPE_INT
            collection_type = realm_collection_type_e.RLM_COLLECTION_TYPE_NONE
            link_target = ""
            link_origin_property_name = ""
            key = realm_col_key_t()
            flags = realm_property_flags_e.RLM_PROPERTY_NORMAL.swigValue()
        }
        val prop_1_2 = realm_property_info_t().apply {
            name = "str"
            public_name = ""
            type = realm_property_type_e.RLM_PROPERTY_TYPE_STRING
            collection_type = realm_collection_type_e.RLM_COLLECTION_TYPE_NONE
            link_target = ""
            link_origin_property_name = ""
            key = realm_col_key_t()
            flags = realm_property_flags_e.RLM_PROPERTY_NORMAL.swigValue()
        }
        val prop_1_3 = realm_property_info_t().apply {
            name = "bars"
            public_name = ""
            type = realm_property_type_e.RLM_PROPERTY_TYPE_OBJECT
            collection_type = realm_collection_type_e.RLM_COLLECTION_TYPE_LIST
            link_target = "bar"
            link_origin_property_name = ""
            key = realm_col_key_t()
            flags = realm_property_flags_e.RLM_PROPERTY_NORMAL.swigValue()
        }

        val class_2 = realm_class_info_t().apply {
            name = "bar"
            primary_key = "int"
            num_properties = 2
            num_computed_properties = 0
            key = realm_table_key_t()
            flags = realm_class_flags_e.RLM_CLASS_NORMAL.swigValue()
        }

        val prop_2_1 = realm_property_info_t().apply {
            name = "int"
            public_name = ""
            type = realm_property_type_e.RLM_PROPERTY_TYPE_INT
            collection_type = realm_collection_type_e.RLM_COLLECTION_TYPE_NONE
            link_target = ""
            link_origin_property_name = ""
            key = realm_col_key_t()
            flags = realm_property_flags_e.RLM_PROPERTY_NORMAL.swigValue()
        }
        val prop_2_2 = realm_property_info_t().apply {
            name = "strings"
            public_name = ""
            type = realm_property_type_e.RLM_PROPERTY_TYPE_STRING
            collection_type = realm_collection_type_e.RLM_COLLECTION_TYPE_LIST
            link_target = ""
            link_origin_property_name = ""
            key = realm_col_key_t()
            flags = realm_property_flags_e.RLM_PROPERTY_NORMAL.swigValue() and realm_property_flags_e.RLM_PROPERTY_NULLABLE.swigValue()
        }

        val classes = realmc.new_classArray(2);
        val props = realmc.new_propertyArrayArray(2)

        realmc.classArray_setitem(classes, 0, class_1);
        realmc.classArray_setitem(classes, 1, class_2);

        val properties_1 = realmc.new_propertyArray(3).also {
            realmc.propertyArray_setitem(it, 0, prop_1_1);
            realmc.propertyArray_setitem(it, 1, prop_1_2);
            realmc.propertyArray_setitem(it, 2, prop_1_3);
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
                        flags = realm_property_flags_e.RLM_PROPERTY_NORMAL.swigValue()
                    },
                    realm_property_info_t().apply {
                        name = "strings"
                        public_name = ""
                        type = realm_property_type_e.RLM_PROPERTY_TYPE_STRING
                        collection_type = realm_collection_type_e.RLM_COLLECTION_TYPE_LIST
                        link_target = ""
                        link_origin_property_name = ""
                        key = realm_col_key_t()
                        flags = realm_property_flags_e.RLM_PROPERTY_NORMAL.swigValue() and realm_property_flags_e.RLM_PROPERTY_NULLABLE.swigValue()
                    }
            ).forEachIndexed { i, prop ->
                realmc.propertyArray_setitem(properties, i, prop);
            }
        }
        realmc.propertyArrayArray_setitem(props, 1, properties_2)


        val realmSchemaNew = realmc.realm_schema_new(classes, 2, props)
        assertTrue(realmc.realm_schema_validate(realmSchemaNew))

        val config: Long = realmc.realm_config_new()

        realmc.realm_config_set_path(config, "c_api_test.realm")
        realmc.realm_config_set_schema(config, realmSchemaNew)
        realmc.realm_config_set_schema_mode(config, realm_schema_mode_e.RLM_SCHEMA_MODE_AUTOMATIC)
        realmc.realm_config_set_schema_version(config, BigInteger("1"))

        val realm = realmc.realm_open(config);
    }

}
