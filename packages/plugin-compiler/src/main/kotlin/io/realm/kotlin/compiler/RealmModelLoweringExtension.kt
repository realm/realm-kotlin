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

package io.realm.kotlin.compiler

import io.realm.kotlin.compiler.FqNames.MODEL_OBJECT_ANNOTATION
import io.realm.kotlin.compiler.FqNames.REALM_MODEL_COMPANION
import io.realm.kotlin.compiler.FqNames.REALM_OBJECT_INTERNAL_INTERFACE
import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower
import org.jetbrains.kotlin.backend.common.runOnFilePostfix
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.platform.konan.isNative

class RealmModelLoweringExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        RealmModelLowering(pluginContext).lower(moduleFragment)
    }
}

private class RealmModelLowering(private val pluginContext: IrPluginContext) : ClassLoweringPass {

    // NOTE This is only available on Native platforms
    val modelObjectAnnotationClass by lazy {
        pluginContext.lookupClassOrThrow(MODEL_OBJECT_ANNOTATION)
    }

    override fun lower(irFile: IrFile) = runOnFilePostfix(irFile)

    override fun lower(irClass: IrClass) {
        if (irClass.isBaseRealmObject) {
            // We don't support data class
            if (irClass.isData) {
                error("Data class '${irClass.kotlinFqName}' is not currently supported.")
            }
            // For native we add @ModelObject(irClass.Companion::class) as associated object to be
            // able to resolve the companion object during runtime due to absence of
            // kotlin.reflect.full.companionObjectInstance
            if (pluginContext.platform.isNative()) {
                val modelObjectAnnotation = IrConstructorCallImpl.fromSymbolOwner(
                    startOffset = UNDEFINED_OFFSET,
                    endOffset = UNDEFINED_OFFSET,
                    type = modelObjectAnnotationClass.defaultType,
                    constructorSymbol = modelObjectAnnotationClass.primaryConstructor!!.symbol
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

            // Generate RealmObjectInternal properties overrides
            val generator = RealmModelSyntheticPropertiesGeneration(pluginContext)
            generator.addRealmObjectInternalProperties(irClass)

            // Modify properties accessor to generate custom getter/setter
            AccessorModifierIrGeneration(pluginContext).modifyPropertiesAndCollectSchema(irClass)

            // Add body for synthetic companion methods
            val companion = irClass.companionObject() ?: fatalError("RealmObject without companion")
            generator.addCompanionFields(irClass.name.identifier, companion, SchemaCollector.properties[irClass])
            generator.addSchemaMethodBody(irClass)
            generator.addNewInstanceMethodBody(irClass)
        } else {
            if (irClass.isCompanion && irClass.parentAsClass.isBaseRealmObject) {
                val realmModelCompanion: IrClassSymbol =
                    pluginContext.lookupClassOrThrow(REALM_MODEL_COMPANION).symbol
                irClass.superTypes += realmModelCompanion.defaultType
            }
        }
    }
}
