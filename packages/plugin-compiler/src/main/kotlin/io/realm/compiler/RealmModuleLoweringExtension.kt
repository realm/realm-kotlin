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
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.impl.IrClassImpl
import org.jetbrains.kotlin.ir.declarations.lazy.IrLazyClass
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

class RealmModuleLoweringExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        RealmModuleLowering(pluginContext).lower(moduleFragment)
        moduleFragment.checkDeclarationParents()
    }
}

private class RealmModuleLowering(pluginContext: IrPluginContext) : ClassLoweringPass {
    val realmMediatorClass: IrClassSymbol = pluginContext.lookupClassOrThrow(REALM_MEDIATOR_INTERFACE).symbol
    val generator = RealmModuleSyntheticMediatorInterfaceGeneration(pluginContext)

    @ObsoleteDescriptorBasedAPI
    override fun lower(irClass: IrClass) {
        if (irClass.isRealmModuleAnnotated) {
            // add super type RealmModelInternal
            irClass.superTypes += realmMediatorClass.defaultType

            val models = listOfParticipatingModelClasses(irClass)

            // Generate Mediator interface overrides
            generator.addInterfaceMethodImplementation(irClass, models)
        }
    }

    private fun listOfParticipatingModelClasses(irClass: IrClass): List<Triple<IrClassifierSymbol, IrType, IrClassSymbol>> {
        // if the annotation has specified a set of models use them otherwise use all collected
        // @RealmObject classes
        val models = mutableListOf<Triple<IrClassifierSymbol, IrType, IrClassSymbol>>()

        val annotationArgument = irClass.getAnnotation(REALM_MODULE_ANNOTATION)
        val arrayOfClasses: IrVarargImpl = annotationArgument?.getValueArgument(0) as IrVarargImpl
        for (clazz: IrVarargElement in arrayOfClasses.elements) {
            (clazz as IrClassReferenceImpl).apply {
                val companionSymbol: IrClassSymbol = getSymbolOrThrow((clazz.symbol.owner as IrClass).companionObject())
                models.add(Triple(clazz.symbol, clazz.classType, companionSymbol))
            }
        }

        // if models if empty, fallback to the default behaviour (i.e collect all models)
        if (models.isEmpty()) {
            for (model in SchemaCollector.properties.keys) {
                val companionSymbol: IrClassSymbol = getSymbolOrThrow(model.companionObject())
                models.add(Triple(model.symbol, model.defaultType, companionSymbol))
            }
        }
        return models
    }

    private fun getSymbolOrThrow(companionObject: IrDeclaration?): IrClassSymbol {
        return when (companionObject) {
            is IrLazyClass -> { // TODO maybe use platform.isJVM ?
                companionObject.symbol
            }
            is IrClassImpl -> {
                companionObject.symbol
            }
            else -> {
                error("Cannot cast Companion Object as IrLazyClass or IrClassImpl")
            }
        }
    }
}
