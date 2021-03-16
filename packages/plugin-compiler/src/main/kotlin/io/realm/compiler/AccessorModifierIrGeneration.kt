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

import io.realm.compiler.FqNames.REALM_OBJECT_HELPER
import io.realm.compiler.Names.OBJECT_IS_MANAGED
import io.realm.compiler.Names.OBJECT_POINTER
import io.realm.compiler.Names.REALM_OBJECT_HELPER_GET_OBJECT
import io.realm.compiler.Names.REALM_OBJECT_HELPER_GET_VALUE
import io.realm.compiler.Names.REALM_OBJECT_HELPER_SET_OBJECT
import io.realm.compiler.Names.REALM_OBJECT_HELPER_SET_VALUE
import io.realm.compiler.Names.REALM_POINTER
import io.realm.compiler.Names.REALM_SYNTHETIC_PROPERTY_PREFIX
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBlockBuilder
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrSetField
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.types.isBoolean
import org.jetbrains.kotlin.ir.types.isByte
import org.jetbrains.kotlin.ir.types.isChar
import org.jetbrains.kotlin.ir.types.isDouble
import org.jetbrains.kotlin.ir.types.isFloat
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.isLong
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.types.isPrimitiveType
import org.jetbrains.kotlin.ir.types.isShort
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.FqName

/**
 * Modifies the IR tree to transform getter/setter to call the C-Interop layer to retrieve read the managed values from the Realm
 * It also collect the schema information while processing the class properties.
 */
class AccessorModifierIrGeneration(private val pluginContext: IrPluginContext) {
    private var realmObjectHelper: IrClass = pluginContext.lookupClassOrThrow(REALM_OBJECT_HELPER)
    private var getValue: IrSimpleFunction = realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_GET_VALUE)
    private var setValue: IrSimpleFunction = realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_SET_VALUE)
    private var getObject: IrSimpleFunction = realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_GET_OBJECT)
    private val setObject = realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_SET_OBJECT)

    private var functionLongToChar: IrSimpleFunction =
        pluginContext.lookupFunctionInClass(FqName("kotlin.Long"), "toChar")
    private var functionCharToLong: IrSimpleFunction =
        pluginContext.lookupFunctionInClass(FqName("kotlin.Char"), "toLong")

    private var functionLongToByte: IrSimpleFunction =
        pluginContext.lookupFunctionInClass(FqName("kotlin.Long"), "toByte")
    private var functionByteToLong: IrSimpleFunction =
        pluginContext.lookupFunctionInClass(FqName("kotlin.Byte"), "toLong")

    private var functionLongToShort: IrSimpleFunction =
        pluginContext.lookupFunctionInClass(FqName("kotlin.Long"), "toShort")
    private var functionShortToLong: IrSimpleFunction =
        pluginContext.lookupFunctionInClass(FqName("kotlin.Short"), "toLong")

    private var functionLongToInt: IrSimpleFunction =
        pluginContext.lookupFunctionInClass(FqName("kotlin.Long"), "toInt")
    private var functionIntToLong: IrSimpleFunction =
        pluginContext.lookupFunctionInClass(FqName("kotlin.Int"), "toLong")

    private lateinit var objectPointerProperty: IrProperty
    private lateinit var dbPointerProperty: IrProperty
    private lateinit var isManagedProperty: IrProperty

    fun modifyPropertiesAndCollectSchema(irClass: IrClass) {
        logInfo("Processing class ${irClass.name}")
        val fields = SchemaCollector.properties.getOrPut(irClass, { mutableMapOf() })

        dbPointerProperty = irClass.lookupProperty(REALM_POINTER)
        objectPointerProperty = irClass.lookupProperty(OBJECT_POINTER)
        isManagedProperty = irClass.lookupProperty(OBJECT_IS_MANAGED)

        irClass.transformChildrenVoid(object : IrElementTransformerVoid() {
            @Suppress("LongMethod")
            override fun visitProperty(declaration: IrProperty): IrStatement {
                val name = declaration.name.asString()

                // Don't redefine accessors for internal synthetic properties or process declarations of subclasses
                if (declaration.backingField == null || name.startsWith(REALM_SYNTHETIC_PROPERTY_PREFIX) || declaration.parentAsClass != irClass) {
                    return declaration
                }

                val propertyTypeRaw = declaration.backingField!!.type
                val propertyType = propertyTypeRaw.makeNotNull()
                val nullable = propertyTypeRaw.isNullable()
                when {
                    propertyType.isString() -> {
                        logInfo("String property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("string", declaration) // collect schema information once
                        modifyAccessor(declaration, getValue, setValue)
                    }
                    propertyType.isByte() -> {
                        logInfo("Byte property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("int", declaration)
                        modifyAccessor(
                            declaration,
                            getValue,
                            setValue,
                            functionLongToByte,
                            functionByteToLong
                        )
                    }
                    propertyType.isChar() -> {
                        logInfo("Char property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("int", declaration)
                        modifyAccessor(
                            declaration,
                            getValue,
                            setValue,
                            functionLongToChar,
                            functionCharToLong
                        )
                    }
                    propertyType.isShort() -> {
                        logInfo("Short property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("int", declaration)
                        modifyAccessor(
                            declaration,
                            getValue,
                            setValue,
                            functionLongToShort,
                            functionShortToLong
                        )
                    }
                    propertyType.isInt() -> {
                        logInfo("Int property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("int", declaration)
                        modifyAccessor(
                            declaration,
                            getValue,
                            setValue,
                            functionLongToInt,
                            functionIntToLong
                        )
                    }
                    propertyType.isLong() -> {
                        logInfo("Long property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("int", declaration)
                        modifyAccessor(declaration, getValue, setValue)
                    }
                    propertyType.isBoolean() -> {
                        logInfo("Boolean property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("bool", declaration)
                        modifyAccessor(declaration, getValue, setValue)
                    }
                    propertyType.isFloat() -> {
                        logInfo("Float property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("float", declaration)
                        modifyAccessor(declaration, getValue, setValue)
                    }
                    propertyType.isDouble() -> {
                        logInfo("Double property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("double", declaration)
                        modifyAccessor(declaration, getValue, setValue)
                    }
                    !propertyType.isPrimitiveType() -> {
                        logInfo("Object property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("object", declaration)
                        modifyAccessor(declaration, getObject, setObject)
                    }
                    else -> {
                        logInfo("Type not processed: ${declaration.dump()}")
                    }
                }

                return super.visitProperty(declaration)
            }
        })
    }

    private fun modifyAccessor(
        property: IrProperty,
        getFunction: IrSimpleFunction,
        setFunction: IrSimpleFunction,
        fromLongToType: IrFunction? = null,
        functionTypeToLong: IrFunction? = null
    ) {
        val backingField = property.backingField!!
        val type = backingField.type
        val getter = property.getter
        val setter = property.setter
        getter?.body?.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitReturn(expression: IrReturn): IrExpression {
                return IrBlockBuilder(
                    pluginContext,
                    Scope(getter.symbol),
                    expression.startOffset,
                    expression.endOffset
                ).irBlock {
                    val receiver = getter.dispatchReceiverParameter!!
                    val cinteropCall =
                        irCall(getFunction, origin = IrStatementOrigin.GET_PROPERTY).also {
                            it.dispatchReceiver = irGetObject(realmObjectHelper.symbol)
                        }.apply {
                            putTypeArgument(0, type)
                            putValueArgument(0, irGet(receiver))
                            putValueArgument(1, irString(property.name.identifier))
                        }

                    val cinteropExpression = if (fromLongToType != null) {
                        irCall(fromLongToType).also {
                            it.dispatchReceiver = cinteropCall
                        }
                    } else {
                        cinteropCall
                    }
                    +irReturn(
                        irIfThenElse(
                            getter.returnType,
                            isManagedCall(receiver),
                            // For managed property call C-Interop function
                            cinteropExpression,
                            // For unmanaged property call backing field value
                            irGetField(irGet(receiver), backingField),
                            origin = IrStatementOrigin.IF,
                        )
                    )
                }
            }
        })
        setter?.body?.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitSetField(expression: IrSetField): IrExpression {
                return IrBlockBuilder(
                    pluginContext,
                    Scope(setter.symbol),
                    expression.startOffset,
                    expression.endOffset
                ).irBlock {
                    val receiver = property.setter!!.dispatchReceiverParameter!!
                    val cinteropCall =
                        irCall(setFunction, origin = IrStatementOrigin.GET_PROPERTY).also {
                            it.dispatchReceiver = irGetObject(realmObjectHelper.symbol)
                        }.apply {
                            putTypeArgument(0, type)
                            putValueArgument(0, irGet(receiver))
                            putValueArgument(1, irString(property.name.identifier))
                            val expression = if (functionTypeToLong != null) {
                                irCall(functionTypeToLong).also { it.dispatchReceiver = irGet(setter.valueParameters.first()) }
                            } else {
                                irGet(setter.valueParameters.first())
                            }
                            putValueArgument(2, expression)
                        }

                    +irReturn(
                        irIfThenElse(
                            pluginContext.irBuiltIns.unitType,
                            isManagedCall(receiver),
                            // For managed property call C-Interop function
                            cinteropCall,
                            // For unmanaged property set backing field
                            irSetField(
                                irGet(receiver),
                                backingField,
                                irGet(setter.valueParameters.first())
                            ),
                            origin = IrStatementOrigin.IF
                        )
                    )
                }
            }
        })
    }

    private fun IrBlockBuilder.isManagedCall(receiver: IrValueParameter?): IrCall {
        // CALL 'public open fun <get-isManaged> (): kotlin.Boolean declared in io.realm.example.Sample' type=kotlin.Boolean origin=GET_PROPERTY
        return irCall(
            isManagedProperty.getter!!,
            origin = IrStatementOrigin.GET_PROPERTY
        ).also {
            it.dispatchReceiver = irGet(receiver!!)
        }
    }
}
