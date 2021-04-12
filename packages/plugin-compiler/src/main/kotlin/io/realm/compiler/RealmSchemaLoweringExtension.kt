/*
 * Copyright 2021 Realm Inc.
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

import io.realm.compiler.FqNames.REALM_CONFIGURATION
import io.realm.compiler.FqNames.REALM_CONFIGURATION_BUILDER
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * This lowering helps create a [RealmConfiguration] containing a map of companion object associated with their
 * corresponding class literal.
 *
 * This is achieved by intercepting the public constructor call to create a [RealmConfiguration] then replace it with an
 * a call to an internal constructor, which accepts a map of companion object as an argument instead of just a list of class literal.
 * Similarly, if the user uses [RealmConfiguration.Builder] we replace the call from the public [RealmConfiguration.Builder.builder] by the
 * internal one that accepts the companion map.
 */
class RealmSchemaLoweringExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        for (irFile in moduleFragment.files) {
            irFile.transformChildrenVoid(object : IrElementTransformerVoid() {
                @Suppress("LongMethod")
                override fun visitConstructorCall(expression: IrConstructorCall): IrExpression {
                    if (REALM_CONFIGURATION == expression.symbol.owner.returnType.classFqName &&
                        expression.symbol.owner.isPrimary
                    ) {
                        // substitute the public constructor call with the internal one, which will contain
                        // also the populated companion map
                        val internalConstructor =
                            pluginContext.lookupConstructorInClass(REALM_CONFIGURATION) {
                                !it.owner.isPrimary
                            }
                        return IrConstructorCallImpl(
                            expression.startOffset, expression.endOffset,
                            internalConstructor.owner.returnType,
                            internalConstructor,
                            typeArgumentsCount = 0,
                            constructorTypeArgumentsCount = 0,
                            valueArgumentsCount = 3,
                        ).apply {
                            // copy positional argument from the original constructor call
                            putValueArgument(0, expression.getValueArgument(0))
                            putValueArgument(1, expression.getValueArgument(1))

                            // Transform the third argument (listOf<T::Class>) into a companion map
                            val schemaArgument = expression.getValueArgument(2)!!
                            val specifiedModels =
                                mutableListOf<Triple<IrClassifierSymbol, IrType, IrClassSymbol>>()
                            when (schemaArgument) {
                                is IrCallImpl -> {
                                    // no ARGUMENTS_REORDERING_FOR_CALL block was added by IR, CLASS_REFERENCE should be available as children
                                    schemaArgument.acceptChildrenVoid(object :
                                            IrElementVisitorVoid {
                                            override fun visitElement(element: IrElement) {
                                                element.acceptChildrenVoid(this)
                                            }

                                            override fun visitClassReference(expression: IrClassReference) {
                                                addEntryToCompanionMap(
                                                    expression,
                                                    pluginContext,
                                                    specifiedModels
                                                )
                                            }
                                        })
                                }
                                is IrGetValueImpl -> {
                                    // the list of CLASS_REFERENCE were probably created in a tmp variable because of ARGUMENTS_REORDERING_FOR_CALL
                                    // we need to navigate the parent block to extract the CLASS_REFERENCE via the content of the tmp variable
                                    if (schemaArgument.symbol.owner.origin == IrDeclarationOrigin.IR_TEMPORARY_VARIABLE) {
                                        schemaArgument.symbol.owner.parent.acceptChildren(
                                            object :
                                                IrElementVisitor<Unit, Name> {
                                                override fun visitElement(
                                                    element: IrElement,
                                                    data: Name
                                                ) {
                                                    element.acceptChildren(this, data)
                                                }

                                                override fun visitVariable(
                                                    declaration: IrVariable,
                                                    data: Name
                                                ) {
                                                    if (declaration.name == data) {
                                                        // get class references
                                                        declaration.acceptChildrenVoid(object :
                                                                IrElementVisitorVoid {

                                                                override fun visitElement(element: IrElement) {
                                                                    element.acceptChildrenVoid(this)
                                                                }

                                                                override fun visitClassReference(expression: IrClassReference) {
                                                                    addEntryToCompanionMap(
                                                                        expression,
                                                                        pluginContext,
                                                                        specifiedModels
                                                                    )
                                                                }
                                                            })
                                                    } else {
                                                        super.visitVariable(declaration, data)
                                                    }
                                                }
                                            },
                                            data = schemaArgument.symbol.owner.name
                                        )
                                    }
                                }
                                else -> {
                                    logError("Schema argument must be a list of class literal (T::class), supplied argument format not supported: ${schemaArgument.dump()}")
                                }
                            }

                            val populatedCompanionMap =
                                buildCompanionMap(specifiedModels, pluginContext)
                            putValueArgument(2, populatedCompanionMap)
                        }
                    }
                    return super.visitConstructorCall(expression)
                }

                override fun visitCall(expression: IrCall): IrExpression {
                    if (expression.symbol.owner.name == Name.identifier("build") &&
                        expression.type.classFqName == REALM_CONFIGURATION
                    ) {
                        // collect class references
                        val specifiedModels =
                            mutableListOf<Triple<IrClassifierSymbol, IrType, IrClassSymbol>>()

                        expression.acceptChildrenVoid(object : IrElementVisitorVoid {
                            override fun visitElement(element: IrElement) {
                                element.acceptChildrenVoid(this)
                            }

                            override fun visitVararg(classes: IrVararg) {
                                for (clazz in classes.elements) {
                                    addEntryToCompanionMap(
                                        clazz as IrClassReferenceImpl,
                                        pluginContext,
                                        specifiedModels
                                    )
                                }
                            }
                        })
                        if (specifiedModels.isEmpty()) {
                            logError("Schema argument must be a non-empty vararg of class literal (T::class)")
                        } else {
                            // substitute build function with internal one taking the companion map as argument
                            val internalBuildFunction = pluginContext.lookupClassOrThrow(REALM_CONFIGURATION_BUILDER).functions.first {
                                it.name == Name.identifier("build") && it.valueParameters.size == 1
                            }
                            return IrCallImpl(
                                expression.startOffset, expression.endOffset,
                                expression.type,
                                internalBuildFunction.symbol,
                                typeArgumentsCount = 0,
                                valueArgumentsCount = 1,
                                origin = null,
                                superQualifierSymbol = null
                            ).apply {
                                val populatedCompanionMap = buildCompanionMap(specifiedModels, pluginContext)
                                putValueArgument(0, populatedCompanionMap)
                                dispatchReceiver = expression.dispatchReceiver
                            }
                        }
                    }
                    return super.visitCall(expression)
                }
            })
        }
    }
}

private fun IrConstructorCallImpl.buildCompanionMap(
    specifiedModels: MutableList<Triple<IrClassifierSymbol, IrType, IrClassSymbol>>,
    pluginContext: IrPluginContext
): IrCallImpl {
    return populateCompanion(startOffset, endOffset, specifiedModels, pluginContext)
}

private fun IrCallImpl.buildCompanionMap(
    specifiedModels: MutableList<Triple<IrClassifierSymbol, IrType, IrClassSymbol>>,
    pluginContext: IrPluginContext
): IrExpression? {
    return populateCompanion(startOffset, endOffset, specifiedModels, pluginContext)
}

@Suppress("LongMethod")
private fun populateCompanion(
    startOffset: Int,
    endOffset: Int,
    specifiedModels: MutableList<Triple<IrClassifierSymbol, IrType, IrClassSymbol>>,
    pluginContext: IrPluginContext
): IrCallImpl {
    if (specifiedModels.isEmpty()) {
        logError("Schema argument must be a list of class literal (T::class)")
    }

    val mapOf =
        pluginContext.referenceFunctions(FqNames.KOTLIN_COLLECTIONS_MAPOF)
            .first { it.owner.valueParameters.size == 1 && it.owner.valueParameters.first().isVararg }

    val realmObjectCompanionIrClass: IrClass =
        pluginContext.lookupClassOrThrow(
            FqNames.REALM_MODEL_COMPANION
        )
    val mapType =
        pluginContext.lookupClassOrThrow(FqNames.KOTLIN_COLLECTIONS_MAP)
    val companionMapKeyType =
        pluginContext.irBuiltIns.kClassClass.starProjectedType
    val companionMapValueType = realmObjectCompanionIrClass.defaultType
    val companionMapType: IrSimpleType =
        mapType.typeWith(companionMapKeyType, companionMapValueType)
    val companionMapEntryType = pluginContext.lookupClassOrThrow(
        FqNames.KOTLIN_PAIR
    ).typeWith(companionMapKeyType, companionMapValueType)
    val pairCtor =
        pluginContext.lookupConstructorInClass(FqNames.KOTLIN_PAIR) {
            it.owner.valueParameters.size == 2
        }
    val populatedCompanionMap = IrCallImpl(
        startOffset, endOffset,
        companionMapType,
        mapOf,
        typeArgumentsCount = 2,
        valueArgumentsCount = 1,
        origin = null,
        superQualifierSymbol = null
    ).apply {
        putTypeArgument(0, companionMapKeyType)
        putTypeArgument(1, companionMapValueType)
        putValueArgument(
            0,
            IrVarargImpl(
                startOffset,
                endOffset,
                pluginContext.irBuiltIns.arrayClass.typeWith(
                    companionMapEntryType
                ),
                companionMapEntryType,
                specifiedModels.map { (irC: IrClassifierSymbol, type: IrType, symbol: IrClassSymbol) ->
                    IrConstructorCallImpl(
                        startOffset, endOffset,
                        companionMapEntryType,
                        pairCtor,
                        typeArgumentsCount = 2,
                        constructorTypeArgumentsCount = 0,
                        valueArgumentsCount = 2,
                    ).apply {
                        putTypeArgument(0, companionMapKeyType)
                        putTypeArgument(1, companionMapValueType)
                        putValueArgument(
                            0,
                            IrClassReferenceImpl(
                                startOffset, endOffset,
                                pluginContext.irBuiltIns.kClassClass.starProjectedType,
                                irC,
                                type
                            )
                        )
                        putValueArgument(
                            1,
                            IrGetObjectValueImpl(
                                startOffset,
                                endOffset,
                                realmObjectCompanionIrClass.defaultType,
                                symbol
                            )
                        )
                    }
                }
            )
        )
    }
    return populatedCompanionMap
}

private fun addEntryToCompanionMap(
    expression: IrClassReference,
    pluginContext: IrPluginContext,
    specifiedModels: MutableList<Triple<IrClassifierSymbol, IrType, IrClassSymbol>>
) {
    val modelType: IrType =
        (expression.type as IrSimpleTypeImpl).arguments[0] as IrType
    val fqname: FqName =
        modelType.classFqName!!
    val irClass: IrClass =
        pluginContext.lookupClassOrThrow(
            fqname
        )
    val companion =
        irClass.companionObject()!!
    specifiedModels.add(
        Triple(
            irClass.symbol,
            modelType,
            companion.symbol
        )
    )
}
