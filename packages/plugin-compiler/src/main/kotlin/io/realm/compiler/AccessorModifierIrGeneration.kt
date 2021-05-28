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

import io.realm.compiler.FqNames.REALM_LIST
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
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
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
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrFail
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
import org.jetbrains.kotlin.resolve.descriptorUtil.classId
import org.jetbrains.kotlin.types.KotlinType

/**
 * Modifies the IR tree to transform getter/setter to call the C-Interop layer to retrieve read the managed values from the Realm
 * It also collect the schema information while processing the class properties.
 */
@OptIn(ObsoleteDescriptorBasedAPI::class)
class AccessorModifierIrGeneration(private val pluginContext: IrPluginContext) {

    private val realmObjectHelper: IrClass = pluginContext.lookupClassOrThrow(REALM_OBJECT_HELPER)
    private val realmListClass: IrClass = pluginContext.lookupClassOrThrow(REALM_LIST)

    private val getValue: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_GET_VALUE)
    private val setValue: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_SET_VALUE)
    private val getObject: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_GET_OBJECT)
    private val setObject: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_SET_OBJECT)
    // TODO getter
//    private val getList: IrSimpleFunction =
//        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_GET_LIST)

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
                if (declaration.backingField == null ||
                    name.startsWith(REALM_SYNTHETIC_PROPERTY_PREFIX) ||
                    declaration.parentAsClass != irClass
                ) {
                    return declaration
                }

                val propertyTypeRaw = declaration.backingField!!.type
                val propertyType = propertyTypeRaw.makeNotNull()
                val nullable = propertyTypeRaw.isNullable()
                when {
                    propertyType.isString() -> {
                        logInfo("String property named ${declaration.name} is nullable $nullable")
                        fields[name] = SchemaProperty(
                            type = "string",
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        modifyAccessor(declaration, getValue, setValue)
                    }
                    propertyType.isByte() -> {
                        logInfo("Byte property named ${declaration.name} is nullable $nullable")
                        fields[name] = SchemaProperty(
                            type = "int",
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
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
                        fields[name] = SchemaProperty(
                            type = "int",
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
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
                        fields[name] = SchemaProperty(
                            type = "int",
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
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
                        fields[name] = SchemaProperty(
                            type = "int",
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
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
                        fields[name] = SchemaProperty(
                            type = "int",
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        modifyAccessor(declaration, getValue, setValue)
                    }
                    propertyType.isBoolean() -> {
                        logInfo("Boolean property named ${declaration.name} is nullable $nullable")
                        fields[name] = SchemaProperty(
                            type = "bool",
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        modifyAccessor(declaration, getValue, setValue)
                    }
                    propertyType.isFloat() -> {
                        logInfo("Float property named ${declaration.name} is nullable $nullable")
                        fields[name] = SchemaProperty(
                            type = "float",
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        modifyAccessor(declaration, getValue, setValue)
                    }
                    propertyType.isDouble() -> {
                        logInfo("Double property named ${declaration.name} is nullable $nullable")
                        fields[name] = SchemaProperty(
                            type = "double",
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        modifyAccessor(declaration, getValue, setValue)
                    }
                    propertyType.isRealmList() -> {
                        logInfo("RealmList property named ${declaration.name} is nullable $nullable")
                        fields[name] = SchemaProperty(
                            type = "list",
                            declaration = declaration,
                            collectionType = CollectionType.LIST,
                            genericTypes = listOf(
                                getCoreTypeFromKotlinType(getListGenericType(declaration)[0])
                            )
                        )
                        // TODO OPTIMIZE consider synthetic property generation for lists to cache
                        //  reference instead of emitting a new list every time - also for links
                        //  see e.g.
                        //  if (isManaged()) {
                        //      if ($realm$synthetic$myList == null) {
                        //          $realm$synthetic$myList = RealmObjectHelper.getList(this, "myList")
                        //      }
                        //      return $realm$synthetic$myList
                        //  } else {
                        //      return backing_field
                        //  }

                        // TODO: use correct getter
                        modifyAccessor(declaration, getValue, setValue)
                    }
                    !propertyType.isPrimitiveType() -> {
                        logInfo("Object property named ${declaration.name} is nullable $nullable")
                        fields[name] = SchemaProperty(
                            type = "object",
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
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
        setFunction: IrSimpleFunction? = null,
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
                        irCall(
                            callee = getFunction,
                            origin = IrStatementOrigin.GET_PROPERTY
                        ).also {
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

        // Setter function is null when working with immutable properties
        if (setFunction != null) {
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
                                val argumentExpression = if (functionTypeToLong != null) {
                                    irCall(functionTypeToLong).also {
                                        it.dispatchReceiver = irGet(setter.valueParameters.first())
                                    }
                                } else {
                                    irGet(setter.valueParameters.first())
                                }
                                putValueArgument(2, argumentExpression)
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

    private fun IrType.isRealmList(): Boolean {
        val propertyClassId = this.classifierOrFail.descriptor.classId
        val realmListClassId = realmListClass.descriptor.classId
        return propertyClassId == realmListClassId
    }

    private fun getListGenericType(declaration: IrProperty): List<String> {
        // Check first if the generic is a subclass of RealmObject
        val superTypes = declaration.symbol.descriptor.type.arguments[0].type.constructor.supertypes
        for (superType: KotlinType in superTypes) {
            if (superType.toString() == "RealmObject") {
                return listOf("RealmObject")
            }
        }

        // Otherwise just return the generic(s) present in the declaration
        return declaration.symbol.descriptor.type.arguments.map {
            it.toString()
        }
    }

    private fun getCoreTypeFromKotlinType(type: String): String {
        // TODO nullability?
        return when (type) {
            "Byte" -> PropertyType.RLM_PROPERTY_TYPE_INT.name
            "Char" -> PropertyType.RLM_PROPERTY_TYPE_INT.name
            "Short" -> PropertyType.RLM_PROPERTY_TYPE_INT.name
            "Int" -> PropertyType.RLM_PROPERTY_TYPE_INT.name
            "Long" -> PropertyType.RLM_PROPERTY_TYPE_INT.name
            "Boolean" -> PropertyType.RLM_PROPERTY_TYPE_BOOL.name
            "Float" -> PropertyType.RLM_PROPERTY_TYPE_FLOAT.name
            "Double" -> PropertyType.RLM_PROPERTY_TYPE_DOUBLE.name
            "String" -> PropertyType.RLM_PROPERTY_TYPE_STRING.name
            "RealmObject" -> PropertyType.RLM_PROPERTY_TYPE_OBJECT.name
            else -> error("Wrong Kotlin type: '$type'")
        }
    }
}
