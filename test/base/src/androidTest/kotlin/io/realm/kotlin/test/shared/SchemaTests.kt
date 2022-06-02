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
package io.realm.kotlin.test.shared

import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.CyclicReference
import io.realm.kotlin.entities.FqNameImportEmbeddedChild
import io.realm.kotlin.entities.FqNameImportParent
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.entities.embedded.CyclicReferenceEmbedded
import io.realm.kotlin.entities.link.Child
import io.realm.kotlin.entities.link.Parent
import io.realm.kotlin.internal.InternalConfiguration
import io.realm.kotlin.schema.RealmClass
import io.realm.kotlin.types.BaseRealmObject
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchemaTests {

    @Test
    fun with() {
        val config = RealmConfiguration.create(schema = setOf(Sample::class))
        assertEquals(setOf(Sample::class), config.schema)
        assertEquals<Map<KClass<out BaseRealmObject>, io.realm.kotlin.internal.RealmObjectCompanion>>(
            mapOf(
                Sample::class to (Sample as io.realm.kotlin.internal.RealmObjectCompanion)
            ),
            config.companionMap
        )
    }

    @Test
    fun usingNamedArgument() {
        val conf =
            RealmConfiguration.create(schema = setOf(Sample::class, Parent::class, Child::class))
        assertValidCompanionMap(conf, Sample::class, Parent::class, Child::class)
    }

    @Test
    fun usingPositionalArgument() {
        val conf = RealmConfiguration.create(setOf(Sample::class, Parent::class, Child::class))
        assertValidCompanionMap(conf, Sample::class, Parent::class, Child::class)
    }

    @Test
    fun usingBuilder() {
        var conf = RealmConfiguration.create(schema = setOf(Sample::class, Parent::class, Child::class))
        assertValidCompanionMap(conf, Sample::class, Parent::class, Child::class)

        conf = RealmConfiguration.Builder(setOf(Parent::class, Child::class)).build()
        assertValidCompanionMap(conf, Parent::class, Child::class)
    }

    @Test
    fun usingSingleClassAsNamed() {
        // Using a single class causes a different input IR to transform (argument not passed as vararg)
        val conf = RealmConfiguration.create(schema = setOf(Sample::class))
        assertValidCompanionMap(conf, Sample::class)
    }

    @Test
    fun usingSingleClassAsPositional() {
        // Using a single class causes a different input IR to transform (argument not passed as vararg)
        val conf = RealmConfiguration.create(setOf(Sample::class))
        assertValidCompanionMap(conf, Sample::class)
    }

    @Test
    fun usingCyclicReferenceInSchema() {
        var conf = RealmConfiguration.create(schema = setOf(CyclicReference::class, CyclicReferenceEmbedded::class))
        assertValidCompanionMap(conf, CyclicReference::class, CyclicReferenceEmbedded::class)
    }

    @Test
    fun usingFqNameImports() {
        var conf = RealmConfiguration.create(schema = setOf(FqNameImportParent::class, FqNameImportEmbeddedChild::class))
        assertValidCompanionMap(conf, FqNameImportParent::class, FqNameImportEmbeddedChild::class)
    }

    private fun assertValidCompanionMap(
        conf: RealmConfiguration,
        vararg schema: KClass<out BaseRealmObject>
    ) {
        assertEquals(schema.size, conf.companionMap.size)
        for (clazz in schema) {
            assertTrue(conf.companionMap.containsKey(clazz))
            // make sure we can instantiate
            val classInfo: RealmClass = conf.companionMap[clazz]!!.`io_realm_kotlin_schema`()
            val newInstance: Any = conf.companionMap[clazz]!!.`io_realm_kotlin_newInstance`()
            assertEquals(clazz.simpleName, classInfo.name)
            assertTrue(newInstance::class == clazz)
        }
    }

    private val RealmConfiguration.companionMap: Map<KClass<out BaseRealmObject>, io.realm.kotlin.internal.RealmObjectCompanion>
        get() {
            return (this as InternalConfiguration).mapOfKClassWithCompanion
        }
}
