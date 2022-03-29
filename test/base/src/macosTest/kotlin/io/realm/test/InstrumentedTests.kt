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

@file:OptIn(ExperimentalStdlibApi::class)
package io.realm.test

// FIXME API-CLEANUP Do we actually want to expose this. Test should probably just be reeavluated
//  or moved.
import io.realm.RealmConfiguration
import io.realm.RealmObject
import io.realm.entities.Sample
import io.realm.internal.BaseRealmImpl
import io.realm.internal.Mediator
import io.realm.internal.RealmObjectCompanion
import io.realm.internal.RealmObjectInternal
import io.realm.internal.RealmObjectReference
import io.realm.internal.RealmReference
import io.realm.internal.interop.ClassKey
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.PropertyInfo
import io.realm.internal.interop.PropertyKey
import io.realm.internal.schema.ClassMetadata
import io.realm.internal.schema.SchemaMetadata
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toLong
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class InstrumentedTests {

    // FIXME API-CLEANUP Do we actually want to expose this. Test should probably just be reeavluated
    //  or moved. Local implementation of pointer wrapper to support test. Using the internal one would
    //  require the native wrapper to be api dependency from cinterop/library. Don't know if the
    //  test is needed at all at this level
    class CPointerWrapper(val ptr: CPointer<out CPointed>?, managed: Boolean = true) : NativePointer

    @Test
    @Suppress("invisible_reference", "invisible_member")
    fun testRealmObjectInternalPropertiesGenerated() {
        val p = Sample()

        @Suppress("CAST_NEVER_SUCCEEDS")
        val realmModel: io.realm.internal.RealmObjectInternal = p as? io.realm.internal.RealmObjectInternal
            ?: error("Supertype RealmObjectInternal was not added to Sample class")

        memScoped {
            val ptr1: COpaquePointerVar = alloc()
            val ptr2: COpaquePointerVar = alloc()

            // Accessing getters/setters
            realmModel.`$realm$objectReference` = RealmObjectReference(
                type = RealmObject::class,
                objectPointer = CPointerWrapper(ptr1.ptr),
                className = "Sample",
                owner = MockRealmReference(),
                mediator = MockMediator()
            )

            val realmPointer: NativePointer = CPointerWrapper(ptr2.ptr)
            val configuration = RealmConfiguration.with(schema = setOf(Sample::class))

            realmModel.`$realm$objectReference`?.run {
                assertNotNull(this)
                assertEquals(ptr1.rawPtr.toLong(), (objectPointer as CPointerWrapper).ptr.toLong())
                assertEquals("Sample", className)
            }
        }
    }

    class MockRealmReference : RealmReference {
        override val dbPointer: NativePointer
            get() = TODO("Not yet implemented")
        override val owner: BaseRealmImpl
            get() = TODO("Not yet implemented")
        override val schemaMetadata: SchemaMetadata
            get() = object : SchemaMetadata {
                override fun get(className: String): ClassMetadata = object : ClassMetadata {
                    override val classKey: ClassKey
                        get() = TODO("Not yet implemented")
                    override val className: String
                        get() = TODO("Not yet implemented")
                    override val primaryKeyPropertyKey: PropertyKey?
                        get() = TODO("Not yet implemented")
                    override fun get(propertyKey: PropertyKey): PropertyInfo? {
                        TODO("Not yet implemented")
                    }
                    override fun get(propertyName: String): PropertyInfo? {
                        TODO("Not yet implemented")
                    }
                }
            }
    }
    class MockMediator : Mediator {
        override fun companionOf(clazz: KClass<out RealmObject>): RealmObjectCompanion {
            TODO("Not yet implemented")
        }
        override fun createInstanceOf(clazz: KClass<out RealmObject>): RealmObjectInternal {
            TODO("Not yet implemented")
        }
    }
}
