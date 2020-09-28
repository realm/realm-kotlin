package io.realm.compiler

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
import org.jetbrains.kotlin.name.FqName

class RealmModelLoweringExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        RealmModelLowering(pluginContext).lower(moduleFragment)
        moduleFragment.checkDeclarationParents()
    }
}

private class RealmModelLowering(private val pluginContext: IrPluginContext) : ClassLoweringPass {
    override fun lower(irClass: IrClass) {
        if (irClass.annotations.hasAnnotation(REALM_OBJECT_ANNOTATION)) {
            // add super type RealmModelInterface
            val realmModelClass: IrClassSymbol = pluginContext.referenceClass(FqName("io.realm.runtimeapi.RealmModelInterface"))
                    ?: error("RealmModelInterface interface not found")
            irClass.superTypes = irClass.superTypes + realmModelClass.defaultType

            // Generate properties
            RealmModelSyntheticPropertiesGeneration(pluginContext).addProperties(irClass)
        }
    }
}