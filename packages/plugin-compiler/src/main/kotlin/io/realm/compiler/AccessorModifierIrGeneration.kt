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

import io.realm.compiler.FqNames.NATIVE_WRAPPER
import io.realm.compiler.FqNames.REALM_OBJECT_HELPER
import io.realm.compiler.Names.C_INTEROP_OBJECT_GET_BOOLEAN
import io.realm.compiler.Names.C_INTEROP_OBJECT_GET_DOUBLE
import io.realm.compiler.Names.C_INTEROP_OBJECT_GET_FLOAT
import io.realm.compiler.Names.C_INTEROP_OBJECT_GET_INTEGER
import io.realm.compiler.Names.C_INTEROP_OBJECT_GET_STRING
import io.realm.compiler.Names.C_INTEROP_OBJECT_SET_BOOLEAN
import io.realm.compiler.Names.C_INTEROP_OBJECT_SET_DOUBLE
import io.realm.compiler.Names.C_INTEROP_OBJECT_SET_FLOAT
import io.realm.compiler.Names.C_INTEROP_OBJECT_SET_INTEGER
import io.realm.compiler.Names.C_INTEROP_OBJECT_SET_STRING
import io.realm.compiler.Names.OBJECT_IS_MANAGED
import io.realm.compiler.Names.OBJECT_POINTER
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
import org.jetbrains.kotlin.ir.expressions.impl.IrPropertyReferenceImpl
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
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Modifies the IR tree to transform getter/setter to call the C-Interop layer to retrieve read the managed values from the Realm
 * It also collect the schema information while processing the class properties.
 */
class AccessorModifierIrGeneration(private val pluginContext: IrPluginContext) {
    private lateinit var objectPointerProperty: IrProperty
    private lateinit var dbPointerProperty: IrProperty
    private lateinit var isManagedProperty: IrProperty
    private lateinit var nativeWrapperClass: IrClass
    private lateinit var realmObjectHelper: IrClass

    private lateinit var objectGetStringFun: IrSimpleFunction
    private lateinit var objectSetStringFun: IrSimpleFunction
    private lateinit var objectGetIntegerFun: IrSimpleFunction
    private lateinit var objectSetIntegerFun: IrSimpleFunction
    private lateinit var objectGetBooleanFun: IrSimpleFunction
    private lateinit var objectSetBooleanFun: IrSimpleFunction
    private lateinit var objectGetFloatFun: IrSimpleFunction
    private lateinit var objectSetFloatFun: IrSimpleFunction
    private lateinit var objectGetDoubleFun: IrSimpleFunction
    private lateinit var objectSetDoubleFun: IrSimpleFunction

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

    fun modifyPropertiesAndCollectSchema(irClass: IrClass) {
        logInfo("Processing class ${irClass.name}")
        val fields = SchemaCollector.properties.getOrPut(irClass, { mutableMapOf() })

        dbPointerProperty = irClass.properties.find {
            it.name == REALM_POINTER
        } ?: error("Could not find synthetic property ${REALM_POINTER.asString()}")

        objectPointerProperty = irClass.properties.find {
            it.name == OBJECT_POINTER
        } ?: error("Could not find synthetic property ${OBJECT_POINTER.asString()}")

        isManagedProperty = irClass.properties.find {
            it.name == OBJECT_IS_MANAGED
        } ?: error("Could not find synthetic property ${OBJECT_IS_MANAGED.asString()}")

        nativeWrapperClass = pluginContext.lookupClassOrThrow(NATIVE_WRAPPER)

        objectGetStringFun = nativeWrapperClass.functions.find {
            it.name == C_INTEROP_OBJECT_GET_STRING
        } ?: error(" Could not find function ${C_INTEROP_OBJECT_GET_STRING.asString()}")

        objectSetStringFun = nativeWrapperClass.functions.find {
            it.name == C_INTEROP_OBJECT_SET_STRING
        } ?: error(" Could not find function ${C_INTEROP_OBJECT_SET_STRING.asString()}")

        objectGetIntegerFun = nativeWrapperClass.functions.find {
            it.name == C_INTEROP_OBJECT_GET_INTEGER
        } ?: error(" Could not find function ${C_INTEROP_OBJECT_GET_INTEGER.asString()}")

        objectSetIntegerFun = nativeWrapperClass.functions.find {
            it.name == C_INTEROP_OBJECT_SET_INTEGER
        } ?: error(" Could not find function ${C_INTEROP_OBJECT_SET_INTEGER.asString()}")

        objectGetBooleanFun = nativeWrapperClass.functions.find {
            it.name == C_INTEROP_OBJECT_GET_BOOLEAN
        } ?: error(" Could not find function ${C_INTEROP_OBJECT_GET_BOOLEAN.asString()}")

        objectSetBooleanFun = nativeWrapperClass.functions.find {
            it.name == C_INTEROP_OBJECT_SET_BOOLEAN
        } ?: error(" Could not find function ${C_INTEROP_OBJECT_SET_BOOLEAN.asString()}")

        objectGetFloatFun = nativeWrapperClass.functions.find {
            it.name == C_INTEROP_OBJECT_GET_FLOAT
        } ?: error(" Could not find function ${C_INTEROP_OBJECT_GET_FLOAT.asString()}")

        objectSetFloatFun = nativeWrapperClass.functions.find {
            it.name == C_INTEROP_OBJECT_SET_FLOAT
        } ?: error(" Could not find function ${C_INTEROP_OBJECT_SET_FLOAT.asString()}")

        objectGetDoubleFun = nativeWrapperClass.functions.find {
            it.name == C_INTEROP_OBJECT_GET_DOUBLE
        } ?: error(" Could not find function ${C_INTEROP_OBJECT_GET_DOUBLE.asString()}")

        objectSetDoubleFun = nativeWrapperClass.functions.find {
            it.name == C_INTEROP_OBJECT_SET_DOUBLE
        } ?: error(" Could not find function ${C_INTEROP_OBJECT_SET_DOUBLE.asString()}")

        realmObjectHelper = pluginContext.lookupClassOrThrow(REALM_OBJECT_HELPER)
        val getObject = realmObjectHelper.lookupFunction("realm_get_object")
        val setValue = realmObjectHelper.lookupFunction("realm_set_value")

        irClass.transformChildrenVoid(object : IrElementTransformerVoid() {
            @Suppress("LongMethod")
            override fun visitProperty(declaration: IrProperty): IrStatement {
                val name = declaration.name.asString()

                // Don't redefine accessors for internal synthetic properties
                if (declaration.backingField == null || name.startsWith(REALM_SYNTHETIC_PROPERTY_PREFIX)) {
                    return declaration
                }

                val propertyTypeRaw = declaration.backingField!!.type
                val propertyType = propertyTypeRaw.makeNotNull()
                val nullable = propertyTypeRaw.isNullable()
                when {
                    propertyType.isString() -> {
                        logInfo("String property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("string", declaration) // collect schema information once
                        modifyGetterAccessor(irClass, name, objectGetStringFun, declaration.getter!!)
                        modifySetterAccessor(irClass, name, objectSetStringFun, declaration.setter!!)
                    }
                    propertyType.isByte() -> {
                        logInfo("Byte property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("int", nullable)
                        modifyGetterAccessor(irClass, name, objectGetIntegerFun, declaration.getter!!, functionLongToByte)
                        modifySetterAccessor(irClass, name, objectSetIntegerFun, declaration.setter!!, functionByteToLong)
                    }
                    propertyType.isChar() -> {
                        logInfo("Char property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("int", declaration)
                        modifyGetterAccessor(irClass, name, objectGetIntegerFun, declaration.getter!!, functionLongToChar)
                        modifySetterAccessor(irClass, name, objectSetIntegerFun, declaration.setter!!, functionCharToLong)
                    }
                    propertyType.isShort() -> {
                        logInfo("Short property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("int", declaration)
                        modifyGetterAccessor(irClass, name, objectGetIntegerFun, declaration.getter!!, functionLongToShort)
                        modifySetterAccessor(irClass, name, objectSetIntegerFun, declaration.setter!!, functionShortToLong)
                    }
                    propertyType.isInt() -> {
                        logInfo("Int property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("int", declaration)
                        modifyGetterAccessor(irClass, name, objectGetIntegerFun, declaration.getter!!, functionLongToInt)
                        modifySetterAccessor(irClass, name, objectSetIntegerFun, declaration.setter!!, functionIntToLong)
                    }
                    propertyType.isLong() -> {
                        logInfo("Long property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("int", declaration)
                        modifyGetterAccessor(irClass, name, objectGetIntegerFun, declaration.getter!!)
                        modifySetterAccessor(irClass, name, objectSetIntegerFun, declaration.setter!!)
                    }
                    propertyType.isBoolean() -> {
                        logInfo("Boolean property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("bool", declaration)
                        modifyGetterAccessor(irClass, name, objectGetBooleanFun, declaration.getter!!)
                        modifySetterAccessor(irClass, name, objectSetBooleanFun, declaration.setter!!)
                    }
                    propertyType.isFloat() -> {
                        logInfo("Float property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("float", declaration)
                        modifyGetterAccessor(irClass, name, objectGetFloatFun, declaration.getter!!)
                        modifySetterAccessor(irClass, name, objectSetFloatFun, declaration.setter!!)
                    }
                    propertyType.isDouble() -> {
                        logInfo("Double property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("double", declaration)
                        modifyGetterAccessor(irClass, name, objectGetDoubleFun, declaration.getter!!)
                        modifySetterAccessor(irClass, name, objectSetDoubleFun, declaration.setter!!)
                    }
                    !propertyType.isPrimitiveType() -> {
                        logInfo("Object property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("object", declaration)
                        modifyAccessor(irClass, declaration, getObject, setValue)
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
        irClass: IrClass,
        property: IrProperty,
        getFunction: IrSimpleFunction,
        setFunction: IrSimpleFunction,
    ) {
        val backingField = property.backingField!!
        val type = backingField.type
        val getter = property.getter
        val setter = property.setter
        getter?.body?.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitReturn(expression: IrReturn): IrExpression {
                return IrBlockBuilder(
                    pluginContext,
                    Scope(getter!!.symbol),
                    expression.startOffset,
                    expression.endOffset
                ).irBlock {
                    val receiver = getter!!.dispatchReceiverParameter
                    val cinteropCall =
                        irCall(getFunction, origin = IrStatementOrigin.GET_PROPERTY).also {
                            it.dispatchReceiver = irGetObject(realmObjectHelper.symbol)
                        }.apply {
                            putTypeArgument(0, irClass.defaultType)
                            putTypeArgument(1, type)
                            putValueArgument(
                                0,
                                irGet(receiver!!)
                            )
                            putValueArgument(
                                1,
                                IrPropertyReferenceImpl(
                                    startOffset,
                                    endOffset,
                                    type,
                                    property.symbol,
                                    0,
                                    backingField.symbol,
                                    getter?.symbol,
                                    property.setter?.symbol,
                                    null
                                )
                            )
                        }

                    +irReturn(
                        irIfThenElse(
                            getter!!.returnType,
                            isManagedCall(receiver),
                            cinteropCall, // property is managed call C-Interop function
                            irGetField(
                                irGet(receiver!!),
                                backingField!!
                            ), // unmanaged property call backing field value
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
                            putTypeArgument(0, irClass.defaultType)
                            putTypeArgument(1, type)
                            putValueArgument(0, irGet(receiver))
                            putValueArgument(
                                1,
                                IrPropertyReferenceImpl(
                                    startOffset,
                                    endOffset,
                                    backingField.type,
                                    property.symbol,
                                    0,
                                    backingField.symbol,
                                    getter?.symbol,
                                    property.setter?.symbol,
                                    null
                                )
                            )
                            putValueArgument(
                                2,
                                irGet(setter.valueParameters.first())
                            )
                        }

                    +irReturn(
                        irIfThenElse(
                            pluginContext.irBuiltIns.unitType,
                            isManagedCall(receiver),
                            cinteropCall, // property is managed call C-Interop function
                            irSetField(
                                irGet(receiver),
                                backingField,
                                irGet(setter.valueParameters.first())
                            ), // un-managed property set backing field value
                            origin = IrStatementOrigin.IF
                        )
                    )
                }
            }
        })
    }

    private fun modifyGetterAccessor(irClass: IrClass, name: String, cInteropGetFunction: IrSimpleFunction, declaration: IrFunction, fromLongToType: IrFunction? = null) {
        declaration.body?.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitReturn(expression: IrReturn): IrExpression {
                return IrBlockBuilder(pluginContext, Scope(declaration.symbol), expression.startOffset, expression.endOffset).irBlock {
                    val property = irClass.properties.find {
                        it.name == Name.identifier(name)
                    } ?: error("Could not find property $name")

//           then: BLOCK type=kotlin.String origin=null
//            CALL 'public abstract fun objectGetString (pointer: dev.nhachicha.NativePointer, propertyName: kotlin.String): kotlin.String declared in dev.nhachicha.NativeWrapper' type=kotlin.String origin=null
//              $this: CALL 'public final fun <get-instance> (): dev.nhachicha.NativeWrapper declared in dev.nhachicha.NativeWrapper.Companion' type=dev.nhachicha.NativeWrapper origin=GET_PROPERTY
//                $this: GET_OBJECT 'CLASS OBJECT name:Companion modality:FINAL visibility:public [companion] superTypes:[kotlin.Any]' type=dev.nhachicha.NativeWrapper.Companion
//              pointer: CALL 'public open fun <get-realmObjectPointer> (): dev.nhachicha.NativePointer declared in dev.nhachicha.Child' type=dev.nhachicha.NativePointer origin=GET_PROPERTY
//                $this: GET_VAR '<this>: dev.nhachicha.Child declared in dev.nhachicha.Child.<get-address>' type=dev.nhachicha.Child origin=null
//              propertyName: CONST String type=kotlin.String value="address"
                    val cinteropCall = irCall(cInteropGetFunction, origin = IrStatementOrigin.GET_PROPERTY).also {
                        it.dispatchReceiver = irGetObject(nativeWrapperClass.symbol)
                    }.apply {
                        var i = 0
                        putValueArgument(
                            i++,
                            irCall(dbPointerProperty.getter!!, origin = IrStatementOrigin.GET_PROPERTY).apply {
                                dispatchReceiver = irGet(property.getter!!.dispatchReceiverParameter!!)
                            }
                        )
                        putValueArgument(
                            i++,
                            irCall(objectPointerProperty.getter!!, origin = IrStatementOrigin.GET_PROPERTY).apply {
                                dispatchReceiver = irGet(property.getter!!.dispatchReceiverParameter!!)
                            }
                        )
                        putValueArgument(i++, irString(irClass.name.identifier))
                        putValueArgument(i, irString(name))
                    }

                    val cinteropExpression = if (fromLongToType != null) {
                        irCall(fromLongToType).also {
                            it.dispatchReceiver = cinteropCall
                        }
                    } else {
                        cinteropCall
                    }
                    // RETURN type=kotlin.Nothing from='public final fun <get-name> (): kotlin.String declared in io.realm.example.Sample'
                    //                WHEN type=kotlin.String origin=IF
                    //                  BRANCH
                    +irReturn(
                        irIfThenElse(
                            property.getter!!.returnType,
                            isManagedCall(property.getter!!.dispatchReceiverParameter!!),
                            cinteropExpression, // property is managed call C-Interop function
                            irGetField(irGet(property.getter!!.dispatchReceiverParameter!!), property.backingField!!), // unmanaged property call backing field value
                            origin = IrStatementOrigin.IF,
                        )
                    )
                }
            }
        })
    }

    private fun modifySetterAccessor(irClass: IrClass, name: String, cInteropSetFunction: IrSimpleFunction, declaration: IrFunction, functionTypeToLong: IrFunction? = null) {
        declaration.body?.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitSetField(expression: IrSetField): IrExpression {
                return IrBlockBuilder(pluginContext, Scope(declaration.symbol), expression.startOffset, expression.endOffset).irBlock {
                    val property = irClass.properties.find {
                        it.name == Name.identifier(name)
                    } ?: error("Could not find property $name")

                    val cinteropCall = irCall(cInteropSetFunction).also {
                        it.dispatchReceiver = irGetObject(nativeWrapperClass.symbol)
                    }.apply {
                        var i = 0
                        putValueArgument(
                            i++,
                            irCall(dbPointerProperty.getter!!, origin = IrStatementOrigin.GET_PROPERTY).apply {
                                dispatchReceiver = irGet(property.setter!!.dispatchReceiverParameter!!)
                            }
                        )
                        putValueArgument(
                            i++,
                            irCall(objectPointerProperty.getter!!, origin = IrStatementOrigin.GET_PROPERTY).apply {
                                dispatchReceiver = irGet(property.setter!!.dispatchReceiverParameter!!)
                            }
                        )
                        putValueArgument(i++, irString(irClass.name.identifier))
                        putValueArgument(i++, irString(name))
                        val expression = if (functionTypeToLong != null) {
                            irCall(functionTypeToLong).also { it.dispatchReceiver = irGet(declaration.valueParameters.first()) }
                        } else {
                            irGet(declaration.valueParameters.first())
                        }
                        putValueArgument(i, expression)
                    }

                    +irReturn(
                        irIfThenElse(
                            pluginContext.irBuiltIns.unitType,
                            isManagedCall(property.setter!!.dispatchReceiverParameter!!),
                            cinteropCall, // property is managed call C-Interop function
                            irSetField(irGet(property.setter!!.dispatchReceiverParameter!!), property.backingField!!, irGet(declaration.valueParameters.first())), // un-managed property set backing field value
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
