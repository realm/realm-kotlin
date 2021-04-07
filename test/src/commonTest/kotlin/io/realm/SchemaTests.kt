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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm

import io.realm.interop.Table
import test.Sample
import test.link.Child
import test.link.Parent
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchemaTests {

    @Test
    fun usingNamedArgument() {
        val conf = RealmConfiguration(schema = listOf(Sample::class, Parent::class, Child::class))
        assertValidCompanionMap(conf, Sample::class, Parent::class, Child::class)
    }

    @Test
    fun usingPositionalArgument() {
        val conf = RealmConfiguration("default", "path", listOf(Sample::class, Parent::class, Child::class))
        assertValidCompanionMap(conf, Sample::class, Parent::class, Child::class)
    }

    @Test
    fun usingSingleClassAsNamed() {
        // Using a single class causes a different input IR to transform (argument not passed as vararg)
        val conf = RealmConfiguration(schema = listOf(Sample::class))
        assertValidCompanionMap(conf, Sample::class)
    }

    @Test
    fun usingSingleClassAsPositional() {
        // Using a single class causes a different input IR to transform (argument not passed as vararg)
        val conf = RealmConfiguration("name", "path", listOf(Sample::class))
        assertValidCompanionMap(conf, Sample::class)
    }

    @Suppress("invisible_member")
    private fun assertValidCompanionMap(conf: RealmConfiguration, vararg schema: KClass<out RealmObject>) {
        assertEquals(schema.size, conf.mapOfKClassWithCompanion.size)
        for (clazz in schema) {
            assertTrue(conf.mapOfKClassWithCompanion.containsKey(clazz))
            // make sure we can instantiate
            val table: Table = conf.mapOfKClassWithCompanion[clazz]!!.`$realm$schema`()
            val newInstance: Any = conf.mapOfKClassWithCompanion[clazz]!!.`$realm$newInstance`()
            assertEquals(clazz.simpleName, table.name)
            assertTrue(newInstance::class == clazz)
        }
    }
}
