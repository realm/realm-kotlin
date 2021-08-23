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
import io.realm.compiler.Names.REALM_CONFIGURATION_BUILDER_BUILD
import io.realm.compiler.Names.REALM_CONFIGURATION_DEFAULT_CONFIG
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.defaultType
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
        val configurationBuilder =
            pluginContext.lookupClassOrThrow(REALM_CONFIGURATION_BUILDER)
        val internalBuilderConstructor: IrConstructorSymbol =
            pluginContext.lookupConstructorInClass(REALM_CONFIGURATION_BUILDER) {
                it.owner.isPrimary //&& (it.owner.valueParameters[2].type.classFqName == KOTLIN_COLLECTIONS_MAP)
            }
        val internalBuildFunction = configurationBuilder.functions.first {
            it.name == REALM_CONFIGURATION_BUILDER_BUILD && it.valueParameters.size == 1
        }
        for (irFile in moduleFragment.files) {
            irFile.transformChildrenVoid(object : IrElementTransformerVoid() {
                override fun visitCall(expression: IrCall): IrExpression {
                    if (expression.symbol.owner.name == REALM_CONFIGURATION_BUILDER_BUILD &&
                        expression.type.classFqName == REALM_CONFIGURATION
                    ) {
                        // Replaces `RealmConfiguration.Builder.build(classSet)` with RealmConfiguration.Builder(...).build(companionMap)
                        // collect class references
                        val specifiedModels =
                            mutableListOf<Triple<IrClassifierSymbol, IrType, IrClassSymbol>>()
                        findSchemaClassLiterals(expression, pluginContext, specifiedModels)
                        if (specifiedModels.isEmpty()) {
                            logError("Schema argument must be a non-empty vararg of class literal (T::class)")
                        } else {
                            // substitute build function with internal one taking the companion map as argument
                            return IrCallImpl(
                                startOffset = expression.startOffset,
                                endOffset = expression.endOffset,
                                type = expression.type,
                                symbol = internalBuildFunction.symbol,
                                typeArgumentsCount = 0,
                                valueArgumentsCount = 1,
                                origin = null,
                                superQualifierSymbol = null
                            ).apply {
                                val populatedCompanionMap =
                                    buildCompanionMap(specifiedModels, pluginContext)
                                putValueArgument(0, populatedCompanionMap)
                                dispatchReceiver = expression.dispatchReceiver
                            }
                        }
                    } else {
                        if (expression.symbol.owner.name == REALM_CONFIGURATION_DEFAULT_CONFIG &&
                            expression.type.classFqName == REALM_CONFIGURATION
                        ) {
                            // Replaces `RealmConfiguration.defaultConfig(classSet)` with RealmConfiguration.Builder(...).build(companionMap)
                            val apply = IrConstructorCallImpl(
                                startOffset = expression.startOffset,
                                endOffset = expression.endOffset,
                                type = configurationBuilder.defaultType,
                                symbol = internalBuilderConstructor,
                                typeArgumentsCount = 0,
                                constructorTypeArgumentsCount = 0,
                                valueArgumentsCount = expression.valueArgumentsCount,
                                origin = null,
                            ).apply {
                                // Copying adds an outer class receiver that causes troubles, so just add them one by one
                                // copyTypeAndValueArgumentsFrom(expression)
                                IntRange(0, expression.valueArgumentsCount - 1).forEach { i ->
                                    putValueArgument(i, expression.getValueArgument(i))
                                }
                            }
                            return IrCallImpl(
                                startOffset = expression.startOffset,
                                endOffset = expression.endOffset,
                                type = expression.type,
                                symbol = internalBuildFunction.symbol,
                                typeArgumentsCount = 0,
                                valueArgumentsCount = 1,
                                origin = null,
                            ).apply {
                                val schemaArgument: IrExpression? = expression.getValueArgument(2)!!
                                val specifiedModels =
                                    mutableListOf<Triple<IrClassifierSymbol, IrType, IrClassSymbol>>()
                                findSchemaClassLiterals(
                                    schemaArgument,
                                    pluginContext,
                                    specifiedModels
                                )
                                val populatedCompanionMap =
                                    buildCompanionMap(specifiedModels, pluginContext)
                                putValueArgument(0, populatedCompanionMap)
                                dispatchReceiver = apply
                            }
                        }
                    }
                    return super.visitCall(expression)
                }
            })
        }
    }
}

/**
 * Locate the list of schema class literals. This method currently support them being defined in-place as var args, or
 * in place using some of the factory collection methods like setOf and arrayOf, but we don't follow the code if
 * defined further away than that.
 *
 * E.g. all of these are valid
 * ```
 * RealmConfiguration.Builder(schema = setOf(MyType::class)).build()
 * RealmConfiguration.Builder(schema = setOf(MyType::class)).build()
 * RealmConfiguration.Builder().schema(setOf(MyType::class)).build()
 * RealmConfiguration.Builder().schema(MyType::class, MyOtherType::class).build()
 * ```
 * While these are not
 * ```
 * val classes = setOf(MyType::class)
 * RealmConfiguration.Builder(schema = classes).build()
 *
 * TODO We should lift this restriction
 * ```
 */
fun findSchemaClassLiterals(schemaArgument: IrExpression?, pluginContext: IrPluginContext, specifiedModels: MutableList<Triple<IrClassifierSymbol, IrType, IrClassSymbol>>) {
    when (schemaArgument) {
        is IrCallImpl -> {
            // no ARGUMENTS_REORDERING_FOR_CALL block was added by IR, CLASS_REFERENCE should
            // be available as children
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
            // the list of CLASS_REFERENCE were probably created in a tmp variable because of
            // ARGUMENTS_REORDERING_FOR_CALL we need to navigate the parent block to extract
            // the CLASS_REFERENCE via the content of the tmp variable
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
            logError("Schema argument must be a set of class literals, i.e. `setOf(MyClass::type)`. Supplied argument format not supported: ${schemaArgument?.dump()}")
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
        logError(
            "No schema was provided. It must be defined as a set of class literals (MyType::class) either through " +
                "RealmConfiguration.Builder(schema = setOf(...)), RealmConfiguration.Builder(schema = setOf(...).build().build(), " +
                "or RealmConfiguration.Builder().schema(...).build()."
        )
    }

    val mapOf = pluginContext.referenceFunctions(FqNames.KOTLIN_COLLECTIONS_MAPOF)
        .first { it.owner.valueParameters.size == 1 && it.owner.valueParameters.first().isVararg }
    val realmObjectCompanionIrClass: IrClass = pluginContext.lookupClassOrThrow(FqNames.REALM_MODEL_COMPANION)
    val mapType = pluginContext.lookupClassOrThrow(FqNames.KOTLIN_COLLECTIONS_MAP)
    val companionMapKeyType = pluginContext.irBuiltIns.kClassClass.starProjectedType
    val companionMapValueType = realmObjectCompanionIrClass.defaultType
    val companionMapType: IrSimpleType = mapType.typeWith(companionMapKeyType, companionMapValueType)
    val companionMapEntryType = pluginContext.lookupClassOrThrow(FqNames.KOTLIN_PAIR)
        .typeWith(companionMapKeyType, companionMapValueType)
    val pairCtor = pluginContext.lookupConstructorInClass(FqNames.KOTLIN_PAIR) {
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
                                symbol.defaultType,
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
    val modelType: IrType = (expression.type as IrSimpleTypeImpl).arguments[0] as IrType
    val fqname: FqName = modelType.classFqName!!
    val irClass: IrClass = pluginContext.lookupClassOrThrow(fqname)
    val companion = irClass.companionObject()!!
    specifiedModels.add(Triple(irClass.symbol, modelType, companion.symbol))
}
