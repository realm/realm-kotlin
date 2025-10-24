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

@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package io.realm.kotlin.compiler

import io.realm.kotlin.compiler.ClassIds.APP_CONFIGURATION_BUILDER
import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.CompilationException
import org.jetbrains.kotlin.backend.common.DeclarationContainerLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.runOnFilePostfix
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

/**
 * Lowering extension that injects the 'io.realm.kotlin.bundleId' compiler plugin option into
 * 'AppConfiguration's by rewiring:
 * - App.create(appId) -> AppImpl.create(appId, bundleId)
 * - AppConfiguration.create(appId) -> AppConfigurationImpl.create(appID, bundleId)
 * - AppConfiguration.Builder().build() -> AppConfigurationImpl.Builder.build(bundleId)
 */
class SyncLoweringExtension(private val bundleId: String) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        // Safe guard that we don't process anything unless we can actually look up a library-sync
        // symbol
        val syncSymbol = pluginContext.referenceClass(ClassIds.APP)?.owner
        if (syncSymbol != null) {
            SyncLowering(pluginContext, bundleId).lowerFromModuleFragment(moduleFragment)
        }
    }
}

private class SyncLowering(private val pluginContext: IrPluginContext, private val bundleId: String) : ClassLoweringPass, DeclarationContainerLoweringPass {
    private val appImplCompanionSymbol =
        pluginContext.lookupClassOrThrow(ClassIds.APP_IMPL).companionObject()!!.symbol
    private val appConfigurationImplCompanionSymbol =
        pluginContext.lookupClassOrThrow(ClassIds.APP_CONFIGURATION_IMPL).companionObject()!!.symbol
    // App.create(appId)
    private val appCreateAppId: IrSimpleFunction =
        pluginContext.lookupClassOrThrow(ClassIds.APP).companionObject()!!
            .lookupFunction(Names.APP_CREATE) {
                it.valueParameters.size == 1 && it.valueParameters[0].type == pluginContext.irBuiltIns.stringType
            }
    // AppImpl.create(appId, bundleId)
    private val appCreateAppIdBundleId: IrSimpleFunction =
        pluginContext.lookupClassOrThrow(ClassIds.APP_IMPL).companionObject()!!.lookupFunction(Names.APP_CREATE) {
            it.valueParameters.size == 2
        }
    // AppConfiguration.create(appId)
    private val appConfigurationCreateAppId: IrSimpleFunction =
        pluginContext.lookupClassOrThrow(ClassIds.APP_CONFIGURATION).companionObject()!!
            .lookupFunction(Names.APP_CONFIGURATION_CREATE) {
                it.valueParameters.size == 1 && it.valueParameters[0].type == pluginContext.irBuiltIns.stringType
            }
    // AppConfigurationImpl.create(appId, bundleId)
    private val appConfigurationImplCreateAppIdBungleId: IrSimpleFunction =
        pluginContext.lookupClassOrThrow(ClassIds.APP_CONFIGURATION_IMPL).companionObject()!!.lookupFunction(Names.APP_CONFIGURATION_CREATE) {
            it.valueParameters.size == 2
        }
    private val appConfigurationBuilder: IrClass =
        pluginContext.lookupClassOrThrow(APP_CONFIGURATION_BUILDER)
    // AppConfiguration.Builder.build()
    private val appBuilderBuildNoArg: IrSimpleFunction =
        appConfigurationBuilder.lookupFunction(Names.APP_CONFIGURATION_BUILDER_BUILD) {
            it.valueParameters.isEmpty()
        }
    // AppConfiguration.Builder.build(bundleId)
    private val appBuilderBuildBundleId: IrSimpleFunction =
        appConfigurationBuilder.lookupFunction(Names.APP_CONFIGURATION_BUILDER_BUILD) {
            it.valueParameters.size == 1
        }

    // Maps from a given call into a new call along with the accompanying dispatch receiver
    val replacements: Map<IrSimpleFunctionSymbol, Pair<IrSimpleFunction, (IrCall) -> IrExpression?>> = mapOf(
        appCreateAppId.symbol to (
            appCreateAppIdBundleId to { expression: IrCall ->
                IrGetObjectValueImpl(
                    startOffset = expression.startOffset,
                    endOffset = expression.endOffset,
                    type = IrSimpleTypeImpl(appImplCompanionSymbol, false, emptyList(), emptyList()),
                    symbol = appImplCompanionSymbol
                )
            }
            ),
        appConfigurationCreateAppId.symbol to (
            appConfigurationImplCreateAppIdBungleId to { expression: IrCall ->
                IrGetObjectValueImpl(
                    expression.startOffset,
                    expression.endOffset,
                    IrSimpleTypeImpl(
                        appConfigurationImplCompanionSymbol,
                        false,
                        emptyList(),
                        emptyList()
                    ),
                    appConfigurationImplCompanionSymbol
                )
            }
            ),
        appBuilderBuildNoArg.symbol to (appBuilderBuildBundleId to { expression: IrCall -> expression.dispatchReceiver })
    )

    val transformer = object : IrElementTransformerVoid() {
        override fun visitCall(expression: IrCall): IrExpression {
            replacements[expression.symbol]?.let { (target, dispatchReceiverFunction) ->
                return IrCallImpl(
                    startOffset = expression.startOffset,
                    endOffset = expression.endOffset,
                    type = expression.type,
                    symbol = target.symbol,
                    typeArgumentsCount = 0,
                    origin = null,
                    superQualifierSymbol = null
                ).apply {
                    dispatchReceiver = dispatchReceiverFunction(expression)
                    val valueArguments = List(expression.valueArgumentsCount) { expression.getValueArgument(it) }
                    valueArguments.forEachIndexed { index, irExpression ->
                        putValueArgument(index, irExpression,)
                    }
                    putValueArgument(
                        expression.valueArgumentsCount,
                        IrConstImpl.string(
                            startOffset,
                            endOffset,
                            pluginContext.irBuiltIns.stringType,
                            bundleId
                        )
                    )
                }
            }
            return super.visitCall(expression)
        }
    }

    // TODO 1.9-DEPRECATION Remove and rely on ClassLoweringPass.lower(IrModuleFragment) when leaving i
    //  1.9 support
    // Workaround that FileLoweringPass.lower(IrModuleFragment) is implemented as extension method
    // in 1.9 but as proper interface method in 2.0. Implementation in both versions are more or
    // less the same but this common implementation can loose some information as the IrElement is
    // also not uniformly available on the CompilationException across versions.
    fun lowerFromModuleFragment(
        moduleFragment: IrModuleFragment
    ) = moduleFragment.files.forEach {
        try {
            lower(it)
        } catch (e: CompilationException) {
            // Unfortunately we cannot access the IR element of e uniformly across 1.9 and 2.0 so
            // leaving it as null. Hopefully the embedded cause will give the appropriate pointers
            // to fix this.
            throw CompilationException(
                "Internal error in realm lowering : ${this::class.qualifiedName}: ${e.message}",
                it,
                null,
                cause = e
            ).apply {
                stackTrace = e.stackTrace
            }
        } catch (e: KotlinExceptionWithAttachments) {
            throw e
        } catch (e: Throwable) {
            throw CompilationException(
                "Internal error in file lowering : ${this::class.qualifiedName}: ${e.message}",
                it,
                null,
                cause = e
            ).apply {
                stackTrace = e.stackTrace
            }
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
