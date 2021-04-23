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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.realm.internal.RealmInitializer
import io.realm.interop.ClassFlag
import io.realm.interop.CollectionType
import io.realm.interop.Property
import io.realm.interop.PropertyFlag
import io.realm.interop.PropertyType
import io.realm.interop.RealmInterop
import io.realm.interop.SchemaMode
import io.realm.interop.Table
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class InstrumentedTests {

    val context = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun contextIsNotNull() {
        assertNotNull(RealmInitializer.filesDir)
    }

    @Test
    fun cinterop_swig() {
        System.loadLibrary("realmc")
        println(io.realm.interop.RealmInterop.realm_get_library_version())
    }

    @Test
    @Suppress("LongMethod")
    fun realm() {
        System.loadLibrary("realmc")
        RealmInterop.realm_get_library_version()

        val config: NativePointer = RealmInterop.realm_config_new()
        RealmInterop.realm_config_set_path(config, context.filesDir.absolutePath + "/library-test.realm")

        RealmInterop.realm_config_set_schema_mode(config, SchemaMode.RLM_SCHEMA_MODE_AUTOMATIC)
        RealmInterop.realm_config_set_schema_version(config, 1)

        val classes = listOf(
            Table(
                name = "foo",
                primaryKey = "",
                flags = setOf(ClassFlag.RLM_CLASS_NORMAL),
                properties = listOf(
                    Property(
                        name = "int",
                        type = PropertyType.RLM_PROPERTY_TYPE_INT,
                    ),
                    Property(
                        name = "str",
                        type = PropertyType.RLM_PROPERTY_TYPE_STRING,
                    ),
                    Property(
                        name = "bars",
                        type = PropertyType.RLM_PROPERTY_TYPE_OBJECT,
                        collectionType = CollectionType.RLM_COLLECTION_TYPE_LIST,
                        linkTarget = "bar",
                    ),
                )
            ),
            Table(
                name = "bar",
                primaryKey = "int",
                flags = setOf(ClassFlag.RLM_CLASS_NORMAL),
                properties = listOf(
                    Property(
                        name = "int",
                        type = PropertyType.RLM_PROPERTY_TYPE_INT,
                        flags = setOf(PropertyFlag.RLM_PROPERTY_INDEXED, PropertyFlag.RLM_PROPERTY_PRIMARY_KEY)
                    ),
                    Property(
                        name = "strings",
                        type = PropertyType.RLM_PROPERTY_TYPE_STRING,
                        collectionType = CollectionType.RLM_COLLECTION_TYPE_LIST,
                        flags = setOf(PropertyFlag.RLM_PROPERTY_NORMAL, PropertyFlag.RLM_PROPERTY_NULLABLE)
                    ),
                )
            ),
        )

        val schema = RealmInterop.realm_schema_new(classes)
        RealmInterop.realm_config_set_schema(config, schema)
        assertTrue(RealmInterop.realm_schema_validate(schema))

        RealmInterop.realm_config_set_schema(config, schema)

        val realm: NativePointer = RealmInterop.realm_open(config)

        RealmInterop.realm_release(config)
        RealmInterop.realm_release(schema)

        assertEquals(2, RealmInterop.realm_get_num_classes(realm))

        val key_foo = RealmInterop.realm_find_class(realm, "foo")

        RealmInterop.realm_begin_write(realm)

        val foo = RealmInterop.realm_object_create(realm, key_foo)

        assertEquals("", RealmInterop.realm_get_value(realm, foo, "foo", "str", PropertyType.RLM_PROPERTY_TYPE_STRING))

        RealmInterop.realm_set_value(realm, foo, "foo", "str", "Hello, World!", false)
        assertEquals("Hello, World!", RealmInterop.realm_get_value(realm, foo, "foo", "str", PropertyType.RLM_PROPERTY_TYPE_STRING))

        RealmInterop.realm_commit(realm)

        RealmInterop.realm_close(realm)
        RealmInterop.realm_release(realm)
    }

//    @Test
//    fun createObject() {
//        val configuration = RealmConfiguration.Builder()
//            .name("createObject")
//            .factory(TestUtils.factory())
//            .build()
//
//        val realm = Realm.open(configuration)
//
// //        println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>> START SLEEPING")
// //        SystemClock.sleep(10000 * 120)
// //        println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>> END SLEEPING")
//        realm.beginTransaction()
//        val managedPerson = realm.create(Person::class)
//        managedPerson.age = 2
//        managedPerson.name = "Sophia"
//        realm.commitTransaction()
//
//        assertEquals(2, managedPerson.age)
//        assertEquals("Sophia", managedPerson.name)
//    }
//
//    @Test
//    fun queryObjects() {
//        val configuration = RealmConfiguration.Builder()
//            .path(TestUtils.realmDefaultDirectory())
//            .name("queryObjects8")
//            .factory(TestUtils.factory())
//            .build()
//
//        val realm = Realm.open(configuration)
//
//        realm.beginTransaction()
//        val managedPerson1 = realm.create(Person::class)
//        val managedPerson2 = realm.create(Person::class)
//
//        managedPerson1.name = "Foo"
//        managedPerson1.age = 42
//
//        managedPerson2.name = "FooBar"
//        managedPerson2.age = 17
//        realm.commitTransaction()
//
//        val objects: RealmResults<Person> = realm.objects<Person>(Person::class, "name beginswith \"Foo\" SORT(name DESCENDING)")
//
//        assertEquals(2, objects.size)
//        val obj1 = objects[0]
//        val obj2 = objects[1]
//
//        assertEquals("Foo", obj1.name)
//        assertEquals(42, obj1.age)
//        assertEquals("FooBar", obj2.name)
//        assertEquals(17, obj2.age)
//    }
}
