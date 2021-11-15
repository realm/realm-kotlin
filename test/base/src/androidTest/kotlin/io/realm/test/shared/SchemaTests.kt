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
package io.realm.test.shared

import io.realm.RealmConfiguration
import io.realm.RealmObject
import io.realm.entities.Sample
import io.realm.entities.link.Child
import io.realm.entities.link.Parent
import io.realm.internal.interop.Property
import io.realm.internal.interop.Table
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchemaTests {

    @Test
    fun with() {
        val config = RealmConfiguration.with(schema = setOf(Sample::class))
        assertEquals(setOf(Sample::class), config.schema)
        assertEquals<Map<KClass<out RealmObject>, io.realm.internal.RealmObjectCompanion>>(
            mapOf(
                Sample::class to (Sample as io.realm.internal.RealmObjectCompanion)
            ),
            config.companionMap
        )
    }

    @Test
    fun usingNamedArgument() {
        val conf =
            RealmConfiguration.with(schema = setOf(Sample::class, Parent::class, Child::class))
        assertValidCompanionMap(conf, Sample::class, Parent::class, Child::class)
    }

    @Test
    fun usingPositionalArgument() {
        val conf = RealmConfiguration.with(setOf(Sample::class, Parent::class, Child::class))
        assertValidCompanionMap(conf, Sample::class, Parent::class, Child::class)
    }

    @Test
    fun usingBuilder() {
        var conf = RealmConfiguration.Builder()
            .schema(Sample::class, Parent::class, Child::class)
            .path("/some/path").build()
        assertValidCompanionMap(conf, Sample::class, Parent::class, Child::class)

        conf = RealmConfiguration.Builder()
            .schema(Parent::class, Child::class).build()
        assertValidCompanionMap(conf, Parent::class, Child::class)
    }

    @Test
    fun usingSingleClassAsNamed() {
        // Using a single class causes a different input IR to transform (argument not passed as vararg)
        val conf = RealmConfiguration.with(schema = setOf(Sample::class))
        assertValidCompanionMap(conf, Sample::class)
    }

    @Test
    fun usingSingleClassAsPositional() {
        // Using a single class causes a different input IR to transform (argument not passed as vararg)
        val conf = RealmConfiguration.with(setOf(Sample::class))
        assertValidCompanionMap(conf, Sample::class)
    }

    private fun assertValidCompanionMap(
        conf: RealmConfiguration,
        vararg schema: KClass<out RealmObject>
    ) {
        assertEquals(schema.size, conf.companionMap.size)
        for (clazz in schema) {
            assertTrue(conf.companionMap.containsKey(clazz))
            // make sure we can instantiate
            val table: Pair<Table, List<Property>> = conf.companionMap[clazz]!!.`$realm$schema`()
            val newInstance: Any = conf.companionMap[clazz]!!.`$realm$newInstance`()
            assertEquals(clazz.simpleName, table.first.name)
            assertTrue(newInstance::class == clazz)
        }
    }

    private val RealmConfiguration.companionMap: Map<KClass<out RealmObject>, io.realm.internal.RealmObjectCompanion>
        get() {
            @Suppress("invisible_member")
            return (this as io.realm.internal.RealmConfigurationImpl).mapOfKClassWithCompanion
        }
}
