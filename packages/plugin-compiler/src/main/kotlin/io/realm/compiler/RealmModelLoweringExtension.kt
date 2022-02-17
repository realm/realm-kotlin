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

package io.realm.compiler

import io.realm.compiler.FqNames.MODEL_OBJECT_ANNOTATION
import io.realm.compiler.FqNames.REALM_MODEL_COMPANION
import io.realm.compiler.FqNames.REALM_OBJECT_INTERNAL_INTERFACE
import io.realm.compiler.Names.REALM_OBJECT_INTERNAL_EMIT_FROZEN_UPDATE
import io.realm.compiler.Names.REALM_OBJECT_INTERNAL_FREEZE
import io.realm.compiler.Names.REALM_OBJECT_INTERNAL_IS_FROZEN
import io.realm.compiler.Names.REALM_OBJECT_INTERNAL_PROPERTY_KEY
import io.realm.compiler.Names.REALM_OBJECT_INTERNAL_REALM_STATE
import io.realm.compiler.Names.REALM_OBJECT_INTERNAL_REGISTER_FOR_NOTIFICATION
import io.realm.compiler.Names.REALM_OBJECT_INTERNAL_THAW
import io.realm.compiler.Names.REALM_OBJECT_INTERNAL_VERSION
import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.checkDeclarationParents
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.konan.isNative

private val realmObjectInternalOverrides = setOf(
    REALM_OBJECT_INTERNAL_FREEZE,
    REALM_OBJECT_INTERNAL_THAW,
    REALM_OBJECT_INTERNAL_REGISTER_FOR_NOTIFICATION,
    REALM_OBJECT_INTERNAL_EMIT_FROZEN_UPDATE,
    REALM_OBJECT_INTERNAL_IS_FROZEN,
    REALM_OBJECT_INTERNAL_REALM_STATE,
    REALM_OBJECT_INTERNAL_VERSION,
    REALM_OBJECT_INTERNAL_PROPERTY_KEY
)

class RealmModelLoweringExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        RealmModelLowering(pluginContext).lower(moduleFragment)
        moduleFragment.checkDeclarationParents()
    }
}

private class RealmModelLowering(private val pluginContext: IrPluginContext) : ClassLoweringPass {

    // NOTE This is only available on Native platforms
    val modelObjectAnnotationClass by lazy {
        pluginContext.lookupClassOrThrow(MODEL_OBJECT_ANNOTATION)
    }

    override fun lower(irClass: IrClass) {
        if (irClass.hasRealmModelInterface) {
            // For native we add @ModelObject(irClass.Companion::class) as associated object to be
            // able to resolve the companion object during runtime due to absence of
            // kotlin.reflect.full.companionObjectInstance
            if (pluginContext.platform.isNative()) {
                val modelObjectAnnotation = IrConstructorCallImpl(
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    type = modelObjectAnnotationClass.defaultType,
                    symbol = modelObjectAnnotationClass.primaryConstructor!!.symbol,
                    constructorTypeArgumentsCount = 0,
                    typeArgumentsCount = 0,
                    valueArgumentsCount = 1
                ).apply {
                    putValueArgument(
                        0,
                        IrClassReferenceImpl(
                            startOffset, endOffset,
                            pluginContext.irBuiltIns.kClassClass.starProjectedType,
                            irClass.companionObject()!!.symbol,
                            type
                        )
                    )
                }
                irClass.annotations += modelObjectAnnotation
            }
            // add super type RealmObjectInternal and RealmObjectInterop
            val realmObjectInternalInterface: IrClassSymbol =
                pluginContext.lookupClassOrThrow(REALM_OBJECT_INTERNAL_INTERFACE).symbol
            irClass.superTypes += realmObjectInternalInterface.defaultType

            // Generate RealmObjectInterop properties overrides
            val generator = RealmModelSyntheticPropertiesGeneration(pluginContext)
            generator.addProperties(irClass)

            // Modify properties accessor to generate custom getter/setter
            AccessorModifierIrGeneration(pluginContext).modifyPropertiesAndCollectSchema(irClass)

            // RealmObjectInternal overrides
            irClass.addFakeOverrides(realmObjectInternalInterface, realmObjectInternalOverrides)

            // Add body for synthetic companion methods
            val companion = irClass.companionObject() ?: fatalError("RealmObject without companion")
            generator.addCompanionFields(irClass.name.identifier, companion, SchemaCollector.properties[irClass])
            generator.addSchemaMethodBody(irClass)
            generator.addNewInstanceMethodBody(irClass)
        } else {
            if (irClass.isCompanion && irClass.parentAsClass.hasRealmModelInterface) {
                val realmModelCompanion: IrClassSymbol = pluginContext.lookupClassOrThrow(REALM_MODEL_COMPANION).symbol
                irClass.superTypes += realmModelCompanion.defaultType
            }
        }
    }
}
