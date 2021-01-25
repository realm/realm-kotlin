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

package io.realm

import io.realm.internal.Mediator
import io.realm.interop.PropertyType
import io.realm.interop.RealmInterop
import io.realm.runtimeapi.RealmModelInternal
import io.realm.runtimeapi.RealmModule
import test.Sample
import test.link.Child
import test.link.Parent
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

// TODO
// - Add
// - Insert
// - Copy to realm?
// - Delete
// - Schema
// - Property
class LinkTests {

    @RealmModule(Sample::class, Parent::class, Child::class)
    class MySchema

    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        val mediator = MySchema() as Mediator

        mediator.schema()

        println("schema ${mediator.schema()}")
        val configuration = RealmConfiguration.Builder(schema = mediator).build()
        realm = Realm.open(configuration)
        // FIXME Cleaning up realm to overcome lack of support for deleting actual files
        //  https://github.com/realm/realm-kotlin/issues/95
        realm.beginTransaction()
        realm.objects(Sample::class).delete()
        realm.commitTransaction()
        assertEquals(0, realm.objects(Sample::class).size, "Realm is not empty")
    }

    @Test
    fun basic() {
        realm.beginTransaction()
        val parent = realm.create(Parent::class)
        val child = realm.create(Child::class)
        parent.child = child
        realm.commitTransaction()
    }

//    class Schema : Mediator {
//        override fun newInstance(clazz: KClass<*>): Any {
//            TODO("Not yet implemented")
//        }
//
//        override fun schema(): List<Any> {
//            return listOf ("{\"name\": \"Parent\", \"properties\": [{\"child\": {\"type\": \"object\", \"nullable\": \"false\"}}]}")
//        }
//    }

//    class PropertyDelegate(val property: Pro) {

    //    }
    var Parent.child: Child?
        get() {
            with(this as RealmModelInternal) {
                val link = RealmInterop.realm_get_value<io.realm.runtimeapi.Link>(
                    this.`$realm$Pointer`,
                    this.`$realm$ObjectPointer`,
                    "Parent",
                    "Child",
                    PropertyType.RLM_PROPERTY_TYPE_OBJECT
                )
//                Child.Companion() as io.realm.internal.RealmObjectCompanion
//                val model = schema.newInstance(Child::class) as RealmModelInternal
            }
        }
        set(value: Child?) {
            with(this as RealmModelInternal) {
                RealmInterop.realm_set_value(
                    this.`$realm$Pointer`,
                    this.`$realm$ObjectPointer`,
                    Parent::child,
                    value,
                    false
                )

            }
            this as RealmModelInternal
        }


}
