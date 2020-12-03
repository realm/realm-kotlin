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
import io.realm.compiler.FqNames.REALM_MODEL_INTERFACE
import io.realm.compiler.FqNames.REALM_OBJECT_ANNOTATION
import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.checkDeclarationParents
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.parentAsClass

class RealmModelLoweringExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        RealmModelLowering(pluginContext).lower(moduleFragment)
        moduleFragment.checkDeclarationParents()

        logInfo("Collected schema is: ${SchemaCollector.properties}")
    }
}

private class RealmModelLowering(private val pluginContext: IrPluginContext) : ClassLoweringPass {
    override fun lower(irClass: IrClass) {
        if (irClass.isRealmModelAnnotated) {
            // add super type RealmModelInternal
            val realmModelClass: IrClassSymbol = pluginContext.lookupClassOrThrow(REALM_MODEL_INTERFACE).symbol
            irClass.superTypes += realmModelClass.defaultType

            // Generate RealmModelInternal properties overrides
            val generator = RealmModelSyntheticPropertiesGeneration(pluginContext)
            generator.addProperties(irClass)

            // Modify properties accessor to generate custom getter/setter
            AccessorModifierIrGeneration(pluginContext).modifyPropertiesAndCollectSchema(irClass)

            // Add body for synthetic methods
            generator.addSchemaMethodBody(irClass)
            generator.addNewInstanceMethodBody(irClass)
        } else {
            if (irClass.isCompanion && irClass.parentAsClass.annotations.hasAnnotation(REALM_OBJECT_ANNOTATION)) {
                val realmModelCompanion: IrClassSymbol = pluginContext.lookupClassOrThrow(REALM_MODEL_COMPANION).symbol
                irClass.superTypes += realmModelCompanion.defaultType
            }
        }
    }
}
