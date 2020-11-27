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

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBlockBuilder
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irConcat
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.isPropertyAccessor
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.types.isNullableString
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.util.isGetter
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

class GenerationExtension : IrGenerationExtension {
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

        private fun isStringGetter(declaration: IrFunction): Boolean {
            return declaration.isPropertyAccessor &&
                declaration.isGetter &&
                (declaration.returnType.isString() || declaration.returnType.isNullableString())
        }

        override fun visitFunctionNew(declaration: IrFunction): IrStatement {
            return if (isStringGetter(declaration)) {
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
