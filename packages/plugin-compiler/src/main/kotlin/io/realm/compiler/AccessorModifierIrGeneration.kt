package io.realm.compiler

import io.realm.compiler.FqNames.NATIVE_WRAPPER
import io.realm.compiler.Names.C_INTEROP_OBJECT_GET_INT64
import io.realm.compiler.Names.C_INTEROP_OBJECT_GET_STRING
import io.realm.compiler.Names.OBJECT_IS_MANAGED
import io.realm.compiler.Names.OBJECT_POINTER
import io.realm.compiler.Names.REALM_SYNTHETIC_PROPERTY_PREFIX
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.ScopeWithIr
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.jvm.ir.propertyIfAccessor
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBlockBuilder
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.isPropertyAccessor
import org.jetbrains.kotlin.ir.descriptors.toIrBasedDescriptor
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name

class AccessorModifierIrGeneration(private val pluginContext: IrPluginContext) {
    private lateinit var objectPointerProperty: IrProperty
    private lateinit var isManagedProperty: IrProperty
    private lateinit var nativeWrapperClass: IrClass
    private lateinit var nativeWrapperCompanion: IrClass
    private lateinit var objectGetStringFun: IrSimpleFunction
    private lateinit var objectGetInt64Fun: IrSimpleFunction
    private lateinit var getInstanceProperty: IrProperty

    fun modifyPropertiesAndCollectSchema(irClass: IrClass) {
        logInfo("Processing class ${irClass.name}")
        val className = irClass.name.asString()
        val fields = SchemaCollector.properties.getOrPut(className, { mutableMapOf() })

        objectPointerProperty = irClass.properties.find {
            it.name == OBJECT_POINTER
        } ?: error("Could not find synthetic property ${OBJECT_POINTER.asString()}")

        isManagedProperty = irClass.properties.find {
            it.name == OBJECT_IS_MANAGED
        } ?: error("Could not find synthetic property ${OBJECT_IS_MANAGED.asString()}")

        nativeWrapperClass = pluginContext.referenceClass(NATIVE_WRAPPER)?.owner
            ?: error("${NATIVE_WRAPPER.asString()} not available")

        nativeWrapperCompanion = nativeWrapperClass.companionObject() as IrClass

        objectGetStringFun = nativeWrapperClass.functions.find {
            it.name == C_INTEROP_OBJECT_GET_STRING
        } ?: error(" Could not find function ${C_INTEROP_OBJECT_GET_STRING.asString()}")

        objectGetInt64Fun = nativeWrapperClass.functions.find {
            it.name == C_INTEROP_OBJECT_GET_INT64
        } ?: error(" Could not find function ${C_INTEROP_OBJECT_GET_INT64.asString()}")

        getInstanceProperty = nativeWrapperCompanion.properties.find {
            it.name == Name.identifier("instance")
        } ?: error("Could not find property <get-instance>")

        irClass.transformChildrenVoid(object : IrElementTransformerVoidWithContext() {
            override fun visitFunctionNew(declaration: IrFunction): IrStatement {
                if (!declaration.isPropertyAccessor)
                    return declaration

                val name = declaration.propertyIfAccessor.toIrBasedDescriptor().name.identifier

                // Don't redefine accessors for internal synthetic properties
                if (name.startsWith(REALM_SYNTHETIC_PROPERTY_PREFIX)) {
                    return declaration
                }

                val nullable = declaration.returnType.isNullable()
                when {
                    declaration.isRealmString() -> {
                        logInfo("String property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("string", nullable)
                        modifyAccessor(irClass, currentScope, name, objectGetStringFun, declaration)
                    }
                    declaration.isRealmLong() -> {
                        logInfo("Long property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("int", nullable)
                        modifyAccessor(irClass, currentScope, name, objectGetInt64Fun, declaration)
                    }
                    declaration.isRealmInt() -> {
                        logInfo("Int property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("int", nullable)
                        modifyAccessor(irClass, currentScope, name, objectGetInt64Fun, declaration)
                    }
                    declaration.isRealmBoolean() -> {
                        logInfo("Boolean property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("boolean", nullable)
                    }
                    else -> {
                        logInfo("Type not processed: ${declaration.dump()}")
                    }
                }

                return super.visitFunctionNew(declaration)
            }
        })
    }

    private fun modifyAccessor(irClass: IrClass, currentScope: ScopeWithIr?, name: String, cInteropFunction: IrSimpleFunction, declaration: IrFunction) {
        declaration.body?.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitReturn(expression: IrReturn): IrExpression {
                return IrBlockBuilder(pluginContext, currentScope?.scope!!, expression.startOffset, expression.endOffset).irBlock {
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
                    val cinteropCall = irCall(cInteropFunction, origin = IrStatementOrigin.GET_PROPERTY).also {
                        it.dispatchReceiver = irCall(getInstanceProperty.getter!!).apply {
                            dispatchReceiver = irGetObject(nativeWrapperCompanion.symbol)
                        }
                    }.apply {
                        putValueArgument(
                            0,
                            irCall(objectPointerProperty.getter!!, origin = IrStatementOrigin.GET_PROPERTY).apply {
                                dispatchReceiver = irGet(property.getter!!.dispatchReceiverParameter!!)
                            }
                        )
                        putValueArgument(1, irString(name))
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
                            origin = IrStatementOrigin.IF
                        )
                    )
                }
            }
        })
    }
}
