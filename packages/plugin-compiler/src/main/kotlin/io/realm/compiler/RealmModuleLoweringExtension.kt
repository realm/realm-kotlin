package io.realm.compiler

import io.realm.compiler.FqNames.REALM_MEDIATOR_INTERFACE
import io.realm.compiler.FqNames.REALM_MODULE_ANNOTATION
import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.checkDeclarationParents
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.impl.IrClassImpl
import org.jetbrains.kotlin.ir.expressions.IrVarargElement
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.hasAnnotation

class RealmModuleLoweringExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        RealmModuleLowering(pluginContext).lower(moduleFragment)
        moduleFragment.checkDeclarationParents()
    }
}

private class RealmModuleLowering(private val pluginContext: IrPluginContext) : ClassLoweringPass {
    @ObsoleteDescriptorBasedAPI
    override fun lower(irClass: IrClass) {
        if (irClass.annotations.hasAnnotation(REALM_MODULE_ANNOTATION)) {
            // add super type RealmModelInternal
            val realmMediatorClass: IrClassSymbol = pluginContext.referenceClass(REALM_MEDIATOR_INTERFACE)
                ?: error("${REALM_MEDIATOR_INTERFACE.asString()} not found")
            irClass.superTypes += realmMediatorClass.defaultType

            val models = listOfParticipatingModelClasses(irClass)

            // Generate Mediator interface overrides
            val generator = RealmModuleSyntheticMediatorInterfaceGeneration(pluginContext)
            generator.addInterfaceMethodImplementation(irClass, models)
        }
    }

    private fun listOfParticipatingModelClasses(irClass: IrClass): List<Triple<IrClassifierSymbol, IrType, IrClassImpl>> {
        // if the annotation has specified a set of models use them otherwise use all collected
        // @RealmObject classes
        val models = mutableListOf<Triple<IrClassifierSymbol, IrType, IrClassImpl>>()

        val annotationArgument = irClass.getAnnotation(REALM_MODULE_ANNOTATION)
        val arrayOfClasses: IrVarargImpl = annotationArgument?.getValueArgument(0) as IrVarargImpl
        for (clazz: IrVarargElement in arrayOfClasses.elements) {
            (clazz as IrClassReferenceImpl).apply {
                models.add(Triple(clazz.symbol, clazz.classType, (clazz.symbol.owner as IrClass).companionObject() as IrClassImpl))
            }
        }

        // if models if empty, fallback to the default behaviour (i.e collect all models)
        if (models.isEmpty()) {
            for (model in SchemaCollector.realmObjectClassesIrClasses) {
                models.add(Triple(model.symbol, model.defaultType, model.companionObject() as IrClassImpl))
            }
        }
        return models
    }
}
