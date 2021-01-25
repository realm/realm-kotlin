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

import io.realm.interop.Property
import io.realm.interop.PropertyType
import io.realm.interop.RealmInterop
import io.realm.runtimeapi.Mediator
import io.realm.runtimeapi.RealmModel
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

class LinkTest {
    class PropertyDelegate(val property: Property) {
        operator fun getValue(child: Child, property: KProperty<*>): String {
            TODO("Not yet implemented")
//            RealmInterop.realm_set_value()
        }

        operator fun setValue(child: Child, property: KProperty<*>, s: String) {
            TODO("Not yet implemented")
        }
    }

    class ListPropertyDelegate {
        operator fun getValue(
            parent: Parent,
            property: KProperty<*>
        ): List<Child> {
            TODO("Not yet implemented")
        }

        operator fun setValue(
            parent: Parent,
            property: KProperty<*>,
            list: List<Child>
        ) {
            TODO("Not yet implemented")
        }

    }

    class GenericPropertyDelegate<T> {
        operator fun getValue(
            parent: Parent,
            property: KProperty<*>
        ): T {
            TODO("Not yet implemented")
        }

        operator fun setValue(
            parent: Parent,
            property: KProperty<*>,
            list: T
        ) {
            TODO("Not yet implemented")
        }

    }

    class TypedProperty<T>(name: String, type: PropertyType) : Property(name, type=type) {

    }

    class Child: RealmModel {
        var name : String by PropertyDelegate(Property(name  ="name", type =PropertyType.RLM_PROPERTY_TYPE_STRING))

    }
    class Parent: RealmModel {
        var children: List<Child> by ListPropertyDelegate()
    }

    companion object {
        fun properties(c: KClass<*>) {

            println(c.members)
            println(
                c.members.filter { it is KMutableProperty }.map { (it as KMutableProperty1<*, *>) }
                    .map { it.getter }
            )
        }
    }

    class DebugMediator(vararg classes: KClass<out RealmModel>): Mediator{
        val classes: List<KClass<out RealmModel>> = classes.asList()
        override fun newInstance(clazz: KClass<*>): Any {
            TODO("Not yet implemented")
        }

        override fun schema(): List<Any> {
            classes.map { properties(it) }
            return emptyList()
        }

    }

    class M(): Mediator {
        override fun newInstance(clazz: KClass<*>): Any {
            TODO("Not yet implemented")
        }

        override fun schema(): List<Any> {
            TODO("Not yet implemented")
        }

    }



}
