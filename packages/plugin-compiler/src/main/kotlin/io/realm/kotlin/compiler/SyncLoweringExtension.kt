/*
 * Copyright 2023 Realm Inc.
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

import io.realm.kotlin.compiler.ClassIds.APP_CONFIGURATION_BUILDER
import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.DeclarationContainerLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower
import org.jetbrains.kotlin.backend.common.runOnFilePostfix
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.interpreter.toIrConstOrNull
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

class SyncLoweringExtension(private val bundleId: String) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        // Safe guard that we don't process anything unless we can actually look up a library-sync
        // symbol
        val syncSymbol = pluginContext.referenceClass(ClassIds.APP)?.owner
        if (syncSymbol != null) {
            SyncLowering(pluginContext, bundleId).lower(moduleFragment)
        }
    }
}

private class SyncLowering(private val pluginContext: IrPluginContext, private val bundleId: String) : ClassLoweringPass, DeclarationContainerLoweringPass {
    private val appCreateAppId: IrSimpleFunction =
        pluginContext.lookupClassOrThrow(ClassIds.APP).companionObject()!!
            .lookupFunction(Names.APP_CREATE) {
                it.valueParameters.size == 1 && it.valueParameters[0].type == pluginContext.irBuiltIns.stringType
            }
    private val appCreateAppIdBundleId: IrSimpleFunction =
        pluginContext.lookupClassOrThrow(ClassIds.APP_IMPL).companionObject()!!.lookupFunction(Names.APP_CREATE) {
            it.valueParameters.size == 2
        }
    private val appConfigurationBuilder: IrClass =
        pluginContext.lookupClassOrThrow(APP_CONFIGURATION_BUILDER)
    private val appBuilderBuildNoArg: IrSimpleFunction =
        appConfigurationBuilder.lookupFunction(Names.APP_CONFIGURATION_BUILDER_BUILD) {
            it.valueParameters.isEmpty()
        }
    private val appBuilderBuildBundleId: IrSimpleFunction =
        appConfigurationBuilder.lookupFunction(Names.APP_CONFIGURATION_BUILDER_BUILD) {
            it.valueParameters.size == 1
        }

    val replacements = mapOf(
        appCreateAppId.symbol to appCreateAppIdBundleId,
        appBuilderBuildNoArg.symbol to appBuilderBuildBundleId
    )

    val transformer = object : IrElementTransformerVoid() {
        override fun visitCall(expression: IrCall): IrExpression {
            replacements.get(expression.symbol)?.let { target ->
                return IrCallImpl(
                    startOffset = expression.startOffset,
                    endOffset = expression.endOffset,
                    type = expression.type,
                    symbol = target.symbol,
                    typeArgumentsCount = 0,
                    valueArgumentsCount = target.valueParameters.size,
                    origin = null,
                    superQualifierSymbol = null
                ).apply {
                    dispatchReceiver = expression.dispatchReceiver
                    expression.valueArguments.forEachIndexed { index, irExpression ->
                        putValueArgument(index, irExpression,)
                    }
                    putValueArgument(
                        expression.valueArgumentsCount,
                        bundleId.toIrConstOrNull(
                            pluginContext.irBuiltIns.stringType,
                            expression.startOffset,
                            expression.endOffset,
                        )
                    )
                }
            }
            return super.visitCall(expression)
        }
    }

    override fun lower(irFile: IrFile) {
        (this as DeclarationContainerLoweringPass).runOnFilePostfix(irFile)
        (this as ClassLoweringPass).runOnFilePostfix(irFile)
    }

    override fun lower(irDeclarationContainer: IrDeclarationContainer) {
        irDeclarationContainer.transformChildrenVoid(
            transformer
        )
    }

    override fun lower(irClass: IrClass) {
        irClass.transformChildrenVoid(
            transformer
        )
    }
}
