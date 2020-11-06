package io.realm

import io.realm.interop.*
import kotlinx.cinterop.*
import realm_wrapper.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CinteropTest {

    @Test
    fun cinterop_cinterop() {

        println(realm_wrapper.realm_get_library_version()!!.toKString())

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

            val classProperties: CPointer<CPointerVarOf<CPointer<realm_property_info_t>>> = cValuesOf(prop_1_1.ptr).ptr
            val realmSchemaNew = realm_schema_new(classes, 1.toULong(), classProperties)

            assertNoError()
            assertTrue(realm_schema_validate(realmSchemaNew))

            val config = realm_config_new()
            realm_config_set_path(config, realmStringStruct(memScope, "c_api_test.realm"))
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
            val realmFindClass = realm_find_class(realm, realmStringStruct(memScope, "foo"), found.ptr, classInfo.ptr)
            assertTrue(realmFindClass)
            assertTrue(found.value)
            assertEquals("foo", classInfo.name.toKString())
            assertEquals(1UL, classInfo.num_properties)

            val propertyInfo = alloc<realm_property_info_t>()
            val realmFindProperty = realm_wrapper.realm_find_property(realm, classInfo.key.readValue(), realmStringStruct(memScope, "int"), found.ptr, propertyInfo.ptr)
            assertTrue(realmFindProperty)
            assertTrue(found.value)
            assertEquals("int", propertyInfo.name.toKString())
        }
    }

    @Test
    fun cinterop_realmInterop() {
        val tables = listOf(
                Table(
                        name = "foo",
                        primaryKey = "",
                        flags = setOf(ClassFlag.RLM_CLASS_NORMAL),
                        properties = listOf(
                                Property(name = "int",
                                        type = PropertyType.RLM_PROPERTY_TYPE_INT,
                                        collectionType = CollectionType.RLM_COLLECTION_TYPE_NONE,
                                        flags = setOf(PropertyFlag.RLM_PROPERTY_NORMAL))
                        )
                )
        )

        val schema = RealmInterop.realm_schema_new(tables)

        memScoped {
            val nativeConfig = RealmInterop.realm_config_new()

            RealmInterop.realm_config_set_path(nativeConfig, "default.realm")
            RealmInterop.realm_config_set_schema(nativeConfig, schema)
            RealmInterop.realm_config_set_schema_mode(nativeConfig, SchemaMode.RLM_SCHEMA_MODE_AUTOMATIC)
            RealmInterop.realm_config_set_schema_version(nativeConfig, 1)

            val realm = RealmInterop.realm_open(nativeConfig)
            assertEquals(1L, RealmInterop.realm_get_num_classes(realm))
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
