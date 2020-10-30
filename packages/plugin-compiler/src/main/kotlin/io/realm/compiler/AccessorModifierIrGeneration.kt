package io.realm.compiler

import io.realm.compiler.FqNames.NATIVE_WRAPPER
import io.realm.compiler.Names.C_INTEROP_OBJECT_GET_INT64
import io.realm.compiler.Names.C_INTEROP_OBJECT_GET_STRING
import io.realm.compiler.Names.C_INTEROP_OBJECT_SET_INT64
import io.realm.compiler.Names.C_INTEROP_OBJECT_SET_STRING
import io.realm.compiler.Names.OBJECT_IS_MANAGED
import io.realm.compiler.Names.OBJECT_POINTER
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
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrSetField
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.types.isBoolean
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.isLong
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
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
    private lateinit var objectGetStringFun: IrSimpleFunction
    private lateinit var objectSetStringFun: IrSimpleFunction
    private lateinit var objectGetInt64Fun: IrSimpleFunction
    private lateinit var objectSetInt64Fun: IrSimpleFunction

    //    private lateinit var objectGetBooleanFun: IrSimpleFunction
//    private lateinit var objectSetBooleanFun: IrSimpleFunction

    fun modifyPropertiesAndCollectSchema(irClass: IrClass) {
        logInfo("Processing class ${irClass.name}")
        val className = irClass.name.asString()
        val fields = SchemaCollector.properties.getOrPut(className, { mutableMapOf() })

        dbPointerProperty = irClass.properties.find {
            it.name == REALM_POINTER
        } ?: error("Could not find synthetic property ${REALM_POINTER.asString()}")

        objectPointerProperty = irClass.properties.find {
            it.name == OBJECT_POINTER
        } ?: error("Could not find synthetic property ${OBJECT_POINTER.asString()}")

        isManagedProperty = irClass.properties.find {
            it.name == OBJECT_IS_MANAGED
        } ?: error("Could not find synthetic property ${OBJECT_IS_MANAGED.asString()}")

        nativeWrapperClass = pluginContext.referenceClass(NATIVE_WRAPPER)?.owner
            ?: error("${NATIVE_WRAPPER.asString()} not available")

        objectGetStringFun = nativeWrapperClass.functions.find {
            it.name == C_INTEROP_OBJECT_GET_STRING
        } ?: error(" Could not find function ${C_INTEROP_OBJECT_GET_STRING.asString()}")

        objectSetStringFun = nativeWrapperClass.functions.find {
            it.name == C_INTEROP_OBJECT_SET_STRING
        } ?: error(" Could not find function ${C_INTEROP_OBJECT_SET_STRING.asString()}")

//        objectGetInt64Fun = nativeWrapperClass.functions.find {
//            it.name == C_INTEROP_OBJECT_GET_INT64
//        } ?: error(" Could not find function ${C_INTEROP_OBJECT_GET_INT64.asString()}")
//
//        objectSetInt64Fun = nativeWrapperClass.functions.find {
//            it.name == C_INTEROP_OBJECT_SET_INT64
//        } ?: error(" Could not find function ${C_INTEROP_OBJECT_SET_INT64.asString()}")

//        objectGetBooleanFun = nativeWrapperClass.functions.find {
//            it.name == C_INTEROP_OBJECT_GET_BOOLEAN
//        } ?: error(" Could not find function ${C_INTEROP_OBJECT_GET_BOOLEAN.asString()}")
//
//        objectSetBooleanFun = nativeWrapperClass.functions.find {
//            it.name == C_INTEROP_OBJECT_GET_BOOLEAN
//        } ?: error(" Could not find function ${C_INTEROP_OBJECT_SET_BOOLEAN.asString()}")

        irClass.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitProperty(declaration: IrProperty): IrStatement {
                val name = declaration.name.asString()

                // Don't redefine accessors for internal synthetic properties
                if (declaration.backingField == null || name.startsWith(REALM_SYNTHETIC_PROPERTY_PREFIX)) {
                    return declaration
                }

                val propertyType = declaration.backingField!!.type
                val nullable = propertyType.isNullable()
                when {
                    propertyType.makeNotNull().isString() -> {
                        logInfo("String property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("string", nullable) // collect schema information once
                        modifyGetterAccessor(irClass, name, objectGetStringFun, declaration.getter!!)
                        modifySetterAccessor(irClass, name, objectSetStringFun, declaration.setter!!)
                    }
                    propertyType.makeNotNull().isLong() -> {
                        logInfo("Long property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("int", nullable)
                        modifyGetterAccessor(irClass, name, objectGetInt64Fun, declaration.getter!!)
                        modifySetterAccessor(irClass, name, objectSetInt64Fun, declaration.setter!!)
                    }
                    propertyType.makeNotNull().isInt() -> {
                        logInfo("Int property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("int", nullable)
                        modifyGetterAccessor(irClass, name, objectGetInt64Fun, declaration.getter!!)
                        modifySetterAccessor(irClass, name, objectSetInt64Fun, declaration.setter!!)
                    }
                    propertyType.makeNotNull().isBoolean() -> {
                        logInfo("Boolean property named ${declaration.name} is nullable $nullable")
//                        if (declaration.isGetter) {
//                            fields[name] = Pair("boolean", nullable)
//                            modifyGetterAccessor(irClass, currentScope, name, objectGetBooleanFun, declaration)
//                        } else {
//                            modifySetterAccessor(irClass, currentScope, name, objectSetBooleanFun, declaration)
//                        }
                    }
                    else -> {
                        logInfo("Type not processed: ${declaration.dump()}")
                    }
                }

                return super.visitProperty(declaration)
            }
        })
    }

    private fun modifyGetterAccessor(irClass: IrClass, name: String, cInteropGetFunction: IrSimpleFunction, declaration: IrFunction) {
        declaration.body?.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitReturn(expression: IrReturn): IrExpression {
                return IrBlockBuilder(pluginContext, Scope(declaration.symbol), expression.startOffset, expression.endOffset).irBlock {
                    val property = irClass.properties.find {
                        it.name == Name.identifier(name)
                    } ?: error("Could not find property $name")

                    // if: CALL 'public open fun <get-isManaged> (): kotlin.Boolean declared in io.realm.example.Sample' type=kotlin.Boolean origin=GET_PROPERTY
                    val isManagedCall = irCall(isManagedProperty.getter!!, origin = IrStatementOrigin.GET_PROPERTY).also {
                        it.dispatchReceiver = irGet(property.getter!!.dispatchReceiverParameter!!)
                    }

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
                        putValueArgument(
                                0,
                                irCall(dbPointerProperty.getter!!, origin = IrStatementOrigin.GET_PROPERTY).apply {
                                    dispatchReceiver = irGet(property.getter!!.dispatchReceiverParameter!!)
                                }
                        )
                        putValueArgument(
                            1,
                            irCall(objectPointerProperty.getter!!, origin = IrStatementOrigin.GET_PROPERTY).apply {
                                dispatchReceiver = irGet(property.getter!!.dispatchReceiverParameter!!)
                            }
                        )
                        putValueArgument( 2, irString(irClass.name.identifier) )
                        putValueArgument(3, irString(name))
                    }

                    // RETURN type=kotlin.Nothing from='public final fun <get-name> (): kotlin.String declared in io.realm.example.Sample'
                    //                WHEN type=kotlin.String origin=IF
                    //                  BRANCH
                    +irReturn(
                        irIfThenElse(
                            property.getter!!.returnType,
                            isManagedCall,
                            cinteropCall, // property is managed call C-Interop function
                            irGetField(irGet(property.getter!!.dispatchReceiverParameter!!), property.backingField!!), // unmanaged property call backing field value
                            origin = IrStatementOrigin.IF,
                        )
                    )
                }
            }
        })
    }

    private fun modifySetterAccessor(irClass: IrClass, name: String, cInteropSetFunction: IrSimpleFunction, declaration: IrFunction) {
        declaration.body?.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitSetField(expression: IrSetField): IrExpression {
                return IrBlockBuilder(pluginContext, Scope(declaration.symbol), expression.startOffset, expression.endOffset).irBlock {
                    val property = irClass.properties.find {
                        it.name == Name.identifier(name)
                    } ?: error("Could not find property $name")

                    val isManagedCall = irCall(isManagedProperty.getter!!, origin = IrStatementOrigin.GET_PROPERTY).also {
                        it.dispatchReceiver = irGet(property.setter!!.dispatchReceiverParameter!!)
                    }

                    val cinteropCall = irCall(cInteropSetFunction).also {
                        it.dispatchReceiver = irGetObject(nativeWrapperClass.symbol)
                    }.apply {
                        putValueArgument(
                                0,
                                irCall(dbPointerProperty.getter!!, origin = IrStatementOrigin.GET_PROPERTY).apply {
                                    dispatchReceiver = irGet(property.setter!!.dispatchReceiverParameter!!)
                                }
                        )
                        putValueArgument(
                            1,
                            irCall(objectPointerProperty.getter!!, origin = IrStatementOrigin.GET_PROPERTY).apply {
                                dispatchReceiver = irGet(property.setter!!.dispatchReceiverParameter!!)
                            }
                        )
                        putValueArgument(2, irString(irClass.name.identifier))
                        putValueArgument(3, irString(name))
                        putValueArgument(4, irGet(declaration.valueParameters.first()))
                    }

                    +irReturn(
                        irIfThenElse(
                            pluginContext.irBuiltIns.unitType,
                            isManagedCall,
                            cinteropCall, // property is managed call C-Interop function
                            irSetField(irGet(property.setter!!.dispatchReceiverParameter!!), property.backingField!!, irGet(declaration.valueParameters.first())), // un-managed property set backing field value
                            origin = IrStatementOrigin.IF
                        )
                    )
                }
            }
        })
    }
}
