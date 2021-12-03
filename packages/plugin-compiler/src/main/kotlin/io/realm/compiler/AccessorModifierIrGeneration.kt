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

import io.realm.compiler.FqNames.REALM_INSTANT
import io.realm.compiler.FqNames.REALM_LIST
import io.realm.compiler.FqNames.REALM_MODEL_INTERFACE
import io.realm.compiler.FqNames.REALM_OBJECT_HELPER
import io.realm.compiler.Names.OBJECT_IS_MANAGED
import io.realm.compiler.Names.OBJECT_POINTER
import io.realm.compiler.Names.REALM_OBJECT_HELPER_GET_LIST
import io.realm.compiler.Names.REALM_OBJECT_HELPER_GET_OBJECT
import io.realm.compiler.Names.REALM_OBJECT_HELPER_GET_TIMESTAMP
import io.realm.compiler.Names.REALM_OBJECT_HELPER_GET_VALUE
import io.realm.compiler.Names.REALM_OBJECT_HELPER_SET_LIST
import io.realm.compiler.Names.REALM_OBJECT_HELPER_SET_OBJECT
import io.realm.compiler.Names.REALM_OBJECT_HELPER_SET_TIMESTAMP
import io.realm.compiler.Names.REALM_OBJECT_HELPER_SET_VALUE
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
import org.jetbrains.kotlin.ir.builders.irIfNull
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrSetField
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrFail
import org.jetbrains.kotlin.ir.types.impl.IrTypeBase
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
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.classId
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.StarProjectionImpl
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlin.types.typeUtil.supertypes
import kotlin.collections.set

/**
 * Modifies the IR tree to transform getter/setter to call the C-Interop layer to retrieve read the managed values from the Realm
 * It also collect the schema information while processing the class properties.
 */
@OptIn(ObsoleteDescriptorBasedAPI::class)
class AccessorModifierIrGeneration(private val pluginContext: IrPluginContext) {

    private val realmObjectHelper: IrClass = pluginContext.lookupClassOrThrow(REALM_OBJECT_HELPER)
    private val realmListClass: IrClass = pluginContext.lookupClassOrThrow(REALM_LIST)
    private val realmInstantClass: IrClass = pluginContext.lookupClassOrThrow(REALM_INSTANT)

    private val getValue: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_GET_VALUE)
    private val setValue: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_SET_VALUE)
    private val getTimestamp: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_GET_TIMESTAMP)
    private val setTimestamp: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_SET_TIMESTAMP)
    private val getObject: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_GET_OBJECT)
    private val setObject: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_SET_OBJECT)
    private val getList: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_GET_LIST)
    private val setList: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_SET_LIST)

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
    private lateinit var isManagedProperty: IrProperty

    fun modifyPropertiesAndCollectSchema(irClass: IrClass) {
        logInfo("Processing class ${irClass.name}")
        val fields = SchemaCollector.properties.getOrPut(irClass, { mutableMapOf() })

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
                val excludeProperty = declaration.backingField!!.hasAnnotation(FqNames.IGNORE_ANNOTATION) ||
                    declaration.backingField!!.hasAnnotation(FqNames.TRANSIENT_ANNOTATION)

                when {
                    excludeProperty -> {
                        logInfo("Property named ${declaration.name} ignored")
                    }
                    propertyType.isString() -> {
                        logInfo("String property named ${declaration.name} is nullable $nullable")
                        fields[name] = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_STRING,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        modifyAccessor(
                            declaration,
                            getValue,
                            setValue
                        )
                    }
                    propertyType.isByte() -> {
                        logInfo("Byte property named ${declaration.name} is nullable $nullable")
                        fields[name] = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_INT,
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
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_INT,
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
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_INT,
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
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_INT,
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
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_INT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        modifyAccessor(declaration, getValue, setValue)
                    }
                    propertyType.isBoolean() -> {
                        logInfo("Boolean property named ${declaration.name} is nullable $nullable")
                        fields[name] = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_BOOL,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        modifyAccessor(declaration, getValue, setValue)
                    }
                    propertyType.isFloat() -> {
                        logInfo("Float property named ${declaration.name} is nullable $nullable")
                        fields[name] = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_FLOAT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        modifyAccessor(declaration, getValue, setValue)
                    }
                    propertyType.isDouble() -> {
                        logInfo("Double property named ${declaration.name} is nullable $nullable")
                        fields[name] = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_DOUBLE,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        modifyAccessor(declaration, getValue, setValue)
                    }
                    propertyType.isRealmInstant() -> {
                        logInfo("RealmInstant property named ${declaration.name} is nullable $nullable")
                        fields[name] = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_TIMESTAMP,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        modifyAccessor(declaration, getTimestamp, setTimestamp)
                    }
                    propertyType.isRealmList() -> {
                        logInfo("RealmList property named ${declaration.name} is nullable $nullable")
                        processListField(fields, name, declaration)
                    }
                    !propertyType.isPrimitiveType() -> {
                        logInfo("Object property named ${declaration.name} is nullable $nullable")
                        fields[name] = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_OBJECT,
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

    private fun processListField(
        fields: MutableMap<String, SchemaProperty>,
        name: String,
        declaration: IrProperty
    ) {
        val type = declaration.symbol.descriptor.type
        if (type.arguments[0] is StarProjectionImpl) {
            logError("Error in field ${declaration.name} - RealmLists cannot use a '*' projection.")
            return
        }
        val listGenericType = type.arguments[0].type
        val coreGenericTypes = getListGenericCoreType(declaration)

        // Only process field if we got valid generics
        if (coreGenericTypes != null) {
            val genericPropertyType = getPropertyTypeFromKotlinType(listGenericType)

            // Only process
            if (genericPropertyType != null) {
                fields[name] = SchemaProperty(
                    propertyType = genericPropertyType,
                    declaration = declaration,
                    collectionType = CollectionType.LIST,
                    coreGenericTypes = listOf(coreGenericTypes)
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

                modifyAccessor(
                    property = declaration,
                    getFunction = getList,
                    collectionType = CollectionType.LIST
                )
            }
        }
    }

    @Suppress("LongParameterList")
    private fun modifyAccessor(
        property: IrProperty,
        getFunction: IrSimpleFunction,
        setFunction: IrSimpleFunction? = null,
        fromLongToType: IrFunction? = null,
        functionTypeToLong: IrFunction? = null,
        collectionType: CollectionType = CollectionType.NONE
    ) {
        val backingField = property.backingField!!
        val type = when (collectionType) {
            CollectionType.NONE -> backingField.type
            CollectionType.LIST -> ((backingField.type as IrSimpleType).arguments[0] as IrTypeBase).type
            else -> error("Collection type '$collectionType' not supported.")
        }
        val getter = property.getter
        val setter = property.setter
        getter?.apply {
            origin = IrDeclarationOrigin.DEFINED
            body?.transformChildrenVoid(object : IrElementTransformerVoid() {
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
                                // TODO consider abstracting parameter addition
                                putTypeArgument(0, type)
                                putValueArgument(0, irGet(receiver))
                                putValueArgument(1, irString(property.name.identifier))
                            }

                        val cinteropExpression = if (fromLongToType != null) {
                            irBlock {
                                val temporary = scope.createTemporaryVariableDeclaration(
                                    cinteropCall.type,
                                    "coreValue",
                                    false
                                ).apply { initializer = cinteropCall }
                                +createSafeCallConstruction(
                                    temporary,
                                    temporary.symbol,
                                    irCall(fromLongToType).apply {
                                        this.dispatchReceiver = irGet(temporary)
                                    }
                                )
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
        }

        // Setter function is null when working with immutable properties
        if (setFunction != null) {
            setter?.apply {
                origin = IrDeclarationOrigin.DEFINED
                body?.transformChildrenVoid(object : IrElementTransformerVoid() {
                    override fun visitSetField(expression: IrSetField): IrExpression {
                        return IrBlockBuilder(
                            pluginContext,
                            Scope(setter.symbol),
                            expression.startOffset,
                            expression.endOffset
                        ).irBlock {
                            val receiver = property.setter!!.dispatchReceiverParameter!!
                            val cinteropCall =
                                irCall(
                                    callee = setFunction,
                                    origin = IrStatementOrigin.GET_PROPERTY
                                ).also {
                                    it.dispatchReceiver = irGetObject(realmObjectHelper.symbol)
                                }.apply {
                                    if (type != null) {
                                        putTypeArgument(0, type)
                                    }
                                    putValueArgument(0, irGet(receiver))
                                    putValueArgument(1, irString(property.name.identifier))
                                    val argumentExpression = if (functionTypeToLong != null) {
                                        irIfNull(
                                            pluginContext.irBuiltIns.longType.makeNullable(),
                                            irGet(setter.valueParameters.first()),
                                            irNull(),
                                            irCall(functionTypeToLong).also {
                                                it.dispatchReceiver =
                                                    irGet(setter.valueParameters.first())
                                            },
                                        )
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

    private fun IrType.isRealmInstant(): Boolean {
        val propertyClassId = this.classifierOrFail.descriptor.classId
        val realmInstantClassId = realmInstantClass.descriptor.classId
        return propertyClassId == realmInstantClassId
    }

    @Suppress("ReturnCount")
    private fun getListGenericCoreType(declaration: IrProperty): CoreType? {
        // Check first if the generic is a subclass of RealmObject
        val descriptorType = declaration.symbol.descriptor.type
        val listGenericType = descriptorType.arguments[0].type
        if (inheritsFromRealmObject(listGenericType.constructor.supertypes)) {
            // Nullable objects are not supported
            if (listGenericType.isNullable()) {
                logError("Error in field ${declaration.name} - RealmLists can only contain non-nullable RealmObjects.")
            }
            return CoreType(
                propertyType = PropertyType.RLM_PROPERTY_TYPE_OBJECT,
                nullable = false
            )
        }

        // If not a RealmObject, check whether the list itself is nullable - if so, throw error
        if (descriptorType.isNullable()) {
            logError("Error in field ${declaration.name} - a RealmList field cannot be marked as nullable.")
            return null
        }

        // Otherwise just return the matching core type present in the declaration
        val genericPropertyType = getPropertyTypeFromKotlinType(listGenericType)
        return if (genericPropertyType != null) {
            CoreType(
                propertyType = genericPropertyType,
                nullable = listGenericType.isNullable()
            )
        } else {
            logError("Unsupported type for lists: '$listGenericType'")
            null
        }
    }

    // TODO do the lookup only once
    private fun getPropertyTypeFromKotlinType(type: KotlinType): PropertyType? {
        return type.constructor.declarationDescriptor
            ?.name
            ?.let { identifier ->
                when (identifier.toString()) {
                    "Byte" -> PropertyType.RLM_PROPERTY_TYPE_INT
                    "Char" -> PropertyType.RLM_PROPERTY_TYPE_INT
                    "Short" -> PropertyType.RLM_PROPERTY_TYPE_INT
                    "Int" -> PropertyType.RLM_PROPERTY_TYPE_INT
                    "Long" -> PropertyType.RLM_PROPERTY_TYPE_INT
                    "Boolean" -> PropertyType.RLM_PROPERTY_TYPE_BOOL
                    "Float" -> PropertyType.RLM_PROPERTY_TYPE_FLOAT
                    "Double" -> PropertyType.RLM_PROPERTY_TYPE_DOUBLE
                    "String" -> PropertyType.RLM_PROPERTY_TYPE_STRING
                    "RealmInstant" -> PropertyType.RLM_PROPERTY_TYPE_TIMESTAMP
                    else ->
                        if (inheritsFromRealmObject(type.supertypes())) {
                            PropertyType.RLM_PROPERTY_TYPE_OBJECT
                        } else {
                            logError("Unsupported type for list: '$type'")
                            null
                        }
                }
            }
    }

    private fun inheritsFromRealmObject(supertypes: Collection<KotlinType>): Boolean =
        supertypes.any {
            it.constructor.declarationDescriptor?.fqNameSafe == REALM_MODEL_INTERFACE
        }
}
