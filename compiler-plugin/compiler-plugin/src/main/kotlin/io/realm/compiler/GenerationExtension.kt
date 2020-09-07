package io.realm.compiler

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.isPropertyAccessor
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.types.isNullableString
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.util.isGetter
import org.jetbrains.kotlin.ir.visitors.*

class GenerationExtension: IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        for (file in moduleFragment.files) {
            GetTransformer(pluginContext).runOnFileInOrder(file)
        }
    }

    class GetTransformer(
            val context: IrPluginContext
    ) : IrElementTransformerVoidWithContext(), FileLoweringPass {
        override fun lower(irFile: IrFile) {
            irFile.transformChildrenVoid()
        }

        override fun visitFunctionNew(declaration: IrFunction): IrStatement {
            return if (declaration.isPropertyAccessor
                    && declaration.isGetter
                    && (declaration.returnType.isString() || declaration.returnType.isNullableString())
            ) {
                declaration.body?.transformChildrenVoid(object : IrElementTransformerVoid() {
                    override fun visitReturn(expression: IrReturn): IrExpression {
                        return IrBlockBuilder(context, currentScope?.scope!!, expression.startOffset, expression.endOffset).irBlock {
                            val irConcat = irConcat()
                            irConcat.addArgument(irString("Hello "))
                            irConcat.addArgument(expression.value)
                            +irReturn(irConcat)
                        }
                    }
                })
                super.visitFunctionNew(declaration)
            } else {
                super.visitFunctionNew(declaration)
            }
        }

    }
}

fun FileLoweringPass.runOnFileInOrder(irFile: IrFile) {
    irFile.acceptVoid(object : IrElementVisitorVoid {
        override fun visitElement(element: IrElement) {
            element.acceptChildrenVoid(this)
        }

        override fun visitFile(declaration: IrFile) {
            lower(declaration)
            declaration.acceptChildrenVoid(this)
        }
    })
}
