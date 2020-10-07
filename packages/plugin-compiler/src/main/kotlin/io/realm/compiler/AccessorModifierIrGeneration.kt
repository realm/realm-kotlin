package io.realm.compiler

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.jvm.ir.propertyIfAccessor
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.isPropertyAccessor
import org.jetbrains.kotlin.ir.descriptors.toIrBasedDescriptor
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

class AccessorModifierIrGeneration(val pluginContext: IrPluginContext) {
    fun modifyPropertiesAndReturnSchema(irClass: IrClass) {
        logInfo("Processing class ${irClass.name}")
        val className = irClass.name.asString()
        val fields = SchemaCollector.properties.getOrPut(className, {mutableMapOf()})

        irClass.transformChildrenVoid(object : IrElementTransformerVoidWithContext() {
            override fun visitFunctionNew(declaration: IrFunction): IrStatement {
                if (!declaration.isPropertyAccessor)
                    return declaration

                val name = declaration.propertyIfAccessor.toIrBasedDescriptor().name.identifier
                val nullable = declaration.returnType.isNullable()

                when {
                    declaration.isRealmString() -> {
                        logInfo("String property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("string", nullable)

                        declaration.body?.transformChildrenVoid(object : IrElementTransformerVoid() {
                            override fun visitReturn(expression: IrReturn): IrExpression {
                                return IrBlockBuilder(pluginContext, currentScope?.scope!!, expression.startOffset, expression.endOffset).irBlock {
                                    val irConcat = irConcat()
                                    irConcat.addArgument(irString("Hello "))
                                    irConcat.addArgument(expression.value)
                                    +irReturn(irConcat)
                                }
                            }
                        })
                    }
                    declaration.isRealmLong() -> {
                        logInfo("Long property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("int", nullable)
                    }
                    declaration.isRealmInt() -> {
                        logInfo("Int property named ${declaration.name} is nullable $nullable")
                        fields[name] = Pair("int", nullable)
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
}

