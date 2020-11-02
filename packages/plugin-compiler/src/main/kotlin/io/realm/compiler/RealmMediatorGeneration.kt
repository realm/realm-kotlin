package io.realm.compiler

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.createImplicitParameterDeclarationWithWrappedDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrExternalPackageFragmentImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockBodyImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class MediatorIrExtension : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val createEmptyExternalPackageFragment = IrExternalPackageFragmentImpl.createEmptyExternalPackageFragment(
                moduleFragment.descriptor,
                FqName("io.realm.fake")
        )
        moduleFragment.transform(RealmMediatorGeneration(createEmptyExternalPackageFragment, pluginContext), null)
    }
}

class RealmMediatorGeneration(private val extension: IrExternalPackageFragment, private val pluginContext: IrPluginContext) :
        IrElementTransformerVoidWithContext() {

    override fun visitClassNew(declaration: IrClass): IrStatement {
        logger("Mediator class: ${declaration.name}")
        val createClass = createClass(extension, "ASDF", ClassKind.CLASS, Modality.FINAL)
        val comp = irFactory.buildClass {
            name = Name.identifier("COMPANION")
            isCompanion = true
        }
        comp.addFunction {
            name = Name.identifier("COMP")
            returnType = pluginContext.symbols.unit.defaultType

        }.apply {
//            body = IrBlockBodyImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET)
//            body = irBlock {
//                +irReturn(irString("TEST"))
//            }
        }
        declaration.addField(Name.identifier("CLAUS"), createClass.defaultType)
        declaration.addFunction {
            name = Name.identifier("testCLAUS")
            returnType = pluginContext.symbols.unit.defaultType
        }

        return super.visitClassNew(declaration)
    }

    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        logger("Mediator class function: ${declaration.name}")
        return super.visitFunctionNew(declaration)
    }

    fun generate(irClass: IrClass): IrClass {
        return pluginContext.irFactory.buildClass {

        }.apply {

        }
    }
}

val irFactory = IrFactoryImpl
fun createClass(
        irPackage: IrPackageFragment,
        shortName: String,
        classKind: ClassKind,
        classModality: Modality
): IrClass = irFactory.buildClass {
    name = Name.identifier(shortName)
    kind = classKind
    modality = classModality
}.apply {
    parent = irPackage
    createImplicitParameterDeclarationWithWrappedDescriptor()
}
