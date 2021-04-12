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

import io.realm.compiler.FqNames.REALM_MODEL_COMPANION
import io.realm.compiler.FqNames.REALM_OBJECT_INTERNAL_INTERFACE
import io.realm.compiler.FqNames.REALM_OBJECT_INTEROP_INTERFACE
import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.checkDeclarationParents
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.parentAsClass

class RealmModelLoweringExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        RealmModelLowering(pluginContext).lower(moduleFragment)
        moduleFragment.checkDeclarationParents()
    }
}

private class RealmModelLowering(private val pluginContext: IrPluginContext) : ClassLoweringPass {
    override fun lower(irClass: IrClass) {
        if (irClass.hasRealmModelInterface) {
            // add super type RealmModelInternal
            val realmObjectInteropInterface: IrClassSymbol = pluginContext.lookupClassOrThrow(REALM_OBJECT_INTEROP_INTERFACE).symbol
            val realmObjectInternalInterface: IrClassSymbol = pluginContext.lookupClassOrThrow(REALM_OBJECT_INTERNAL_INTERFACE).symbol
            irClass.superTypes += realmObjectInteropInterface.defaultType
            irClass.superTypes += realmObjectInternalInterface.defaultType

            // Generate RealmModelInternal properties overrides
            val generator = RealmModelSyntheticPropertiesGeneration(pluginContext)
            generator.addProperties(irClass)

            // Modify properties accessor to generate custom getter/setter
            AccessorModifierIrGeneration(pluginContext).modifyPropertiesAndCollectSchema(irClass)

            // Add body for synthetic companion methods
            val companion = irClass.companionObject() ?: error("RealmObject without companion")
            generator.addCompanionFieldsProperty(companion, SchemaCollector.properties[irClass])
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
