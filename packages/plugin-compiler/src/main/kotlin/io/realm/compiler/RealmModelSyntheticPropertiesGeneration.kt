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

import io.realm.compiler.FqNames.CLASS_FLAG
import io.realm.compiler.FqNames.COLLECTION_TYPE
import io.realm.compiler.FqNames.PROPERTY
import io.realm.compiler.FqNames.PROPERTY_FLAG
import io.realm.compiler.FqNames.PROPERTY_TYPE
import io.realm.compiler.FqNames.REALM_MODEL_COMPANION
import io.realm.compiler.FqNames.REALM_MODEL_INTERNAL_INTERFACE
import io.realm.compiler.FqNames.REALM_NATIVE_POINTER
import io.realm.compiler.FqNames.TABLE
import io.realm.compiler.Names.CLASS_FLAG_NORMAL
import io.realm.compiler.Names.OBJECT_IS_MANAGED
import io.realm.compiler.Names.OBJECT_POINTER
import io.realm.compiler.Names.OBJECT_TABLE_NAME
import io.realm.compiler.Names.PROPERTY_COLLECTION_TYPE_NONE
import io.realm.compiler.Names.PROPERTY_FLAG_NORMAL
import io.realm.compiler.Names.PROPERTY_FLAG_NULLABLE
import io.realm.compiler.Names.PROPERTY_TYPE_OBJECT
import io.realm.compiler.Names.REALM_OBJECT_COMPANION_NEW_INSTANCE_METHOD
import io.realm.compiler.Names.REALM_OBJECT_COMPANION_SCHEMA_METHOD
import io.realm.compiler.Names.REALM_OBJECT_SCHEMA
import io.realm.compiler.Names.REALM_POINTER
import io.realm.compiler.Names.SET
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.at
import org.jetbrains.kotlin.ir.builders.declarations.addGetter
import org.jetbrains.kotlin.ir.builders.declarations.addProperty
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrExpressionBodyImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrFail
import org.jetbrains.kotlin.ir.types.createType
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.getPropertySetter
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly

class RealmModelSyntheticPropertiesGeneration(private val pluginContext: IrPluginContext) {
    private val realmModelInternal = pluginContext.lookupClassOrThrow(REALM_MODEL_INTERNAL_INTERFACE)
    private val nullableNativePointerInterface = pluginContext.lookupClassOrThrow(REALM_NATIVE_POINTER)
        .symbol.createType(true, emptyList())
    private val realmObjectCompanionInterface = pluginContext.lookupClassOrThrow(REALM_MODEL_COMPANION)
    private val table = pluginContext.lookupClassOrThrow(TABLE)

    private val tableConstructor = table.primaryConstructor ?: error("Couldn't find constructor for $TABLE")
    private val classFlag: IrClass = pluginContext.lookupClassOrThrow(CLASS_FLAG)
    private val classFlags = classFlag.declarations.filter { it is IrEnumEntry } as List<IrEnumEntry>
    private val propertyClass = pluginContext.lookupClassOrThrow(PROPERTY)
    private val propertyConstructor = propertyClass.primaryConstructor ?: error("Couldn't find constructor for $TABLE")
    private val propertyType: IrClass = pluginContext.lookupClassOrThrow(PROPERTY_TYPE)
    private val propertyTypes = propertyType.declarations.filter { it is IrEnumEntry } as List<IrEnumEntry>
    private val collectionType: IrClass = pluginContext.lookupClassOrThrow(COLLECTION_TYPE)
    private val collectionTypes = collectionType.declarations.filter { it is IrEnumEntry } as List<IrEnumEntry>
    private val propertyFlag: IrClass = pluginContext.lookupClassOrThrow(PROPERTY_FLAG)
    private val propertyFlags = propertyFlag.declarations.filter { it is IrEnumEntry } as List<IrEnumEntry>

    fun addProperties(irClass: IrClass): IrClass =
        irClass.apply {
            addProperty(REALM_POINTER, nullableNativePointerInterface, ::irNull)
            addProperty(OBJECT_POINTER, nullableNativePointerInterface, ::irNull)
            addProperty(OBJECT_TABLE_NAME, pluginContext.irBuiltIns.stringType.makeNullable(), ::irNull)
            addProperty(OBJECT_IS_MANAGED, pluginContext.irBuiltIns.booleanType, ::irFalse)
            // Should be of type Mediator, but requires RealmModelInternal and Mediator to be in
            // same module
            addProperty(REALM_OBJECT_SCHEMA, pluginContext.irBuiltIns.anyType, ::irNull)
        }

    // Generate body for the synthetic schema method defined inside the Companion instance previously declared via `RealmModelSyntheticCompanionExtension`
    // TODO OPTIMIZE should be a one time only constructed object
    @OptIn(ObsoleteDescriptorBasedAPI::class)
    @Suppress("LongMethod")
    fun addSchemaMethodBody(irClass: IrClass) {
        val companionObject = irClass.companionObject() as? IrClass
            ?: error("Companion object not available")

        val fields: MutableMap<String, Pair<String, IrProperty>> =
            SchemaCollector.properties.getOrDefault(irClass, mutableMapOf())

        val function = companionObject.functions.first { it.name == REALM_OBJECT_COMPANION_SCHEMA_METHOD }
        function.dispatchReceiverParameter = companionObject.thisReceiver?.copyTo(function)
        function.body = pluginContext.blockBody(function.symbol) {
            +irReturn(
                IrConstructorCallImpl(
                    startOffset,
                    endOffset,
                    type = table.defaultType,
                    symbol = tableConstructor.symbol,
                    constructorTypeArgumentsCount = 0,
                    typeArgumentsCount = 0,
                    valueArgumentsCount = 4
                ).apply {
                    var arg = 0
                    // Name
                    putValueArgument(arg++, irString(irClass.name.identifier))
                    // Primary key
                    putValueArgument(arg++, irString(""))
                    // Flags
                    putValueArgument(
                        arg++,
                        buildSetOf(
                            pluginContext, this@blockBody, classFlag.defaultType,
                            listOf(
                                IrGetEnumValueImpl(
                                    UNDEFINED_OFFSET,
                                    UNDEFINED_OFFSET,
                                    classFlag.defaultType,
                                    classFlags.first { it.name == CLASS_FLAG_NORMAL }.symbol
                                )
                            )
                        )
                    )
                    // Properties
                    putValueArgument(
                        arg++,
                        buildListOf(
                            pluginContext, this@blockBody, propertyClass.defaultType,
                            fields.map { entry ->
                                val value = entry.value
                                val type = propertyTypes.firstOrNull {
                                    it.name.identifier.toLowerCaseAsciiOnly()
                                        .contains(value.first)
                                } ?: error("Unknown type ${value.first}")
                                val objectType = propertyTypes.firstOrNull { it.name == PROPERTY_TYPE_OBJECT } ?: error("Unknown type ${value.first}")
                                val property = value.second
                                val backingField = property.backingField ?: error("Property without backing field or type")
                                val nullable = backingField.type.isNullable()
                                IrConstructorCallImpl(
                                    startOffset,
                                    endOffset,
                                    type = propertyClass.defaultType,
                                    symbol = propertyConstructor.symbol,
                                    constructorTypeArgumentsCount = 0,
                                    typeArgumentsCount = 0,
                                    valueArgumentsCount = 7
                                ).apply {
                                    var arg = 0
                                    // Name
                                    putValueArgument(arg++, irString(entry.key))
                                    // Public name
                                    putValueArgument(arg++, irString(""))
                                    // Type
                                    putValueArgument(
                                        arg++,
                                        IrGetEnumValueImpl(
                                            UNDEFINED_OFFSET,
                                            UNDEFINED_OFFSET,
                                            propertyType.defaultType,
                                            type.symbol
                                        )
                                    )
                                    // Collection type
                                    putValueArgument(
                                        arg++,
                                        IrGetEnumValueImpl(
                                            UNDEFINED_OFFSET,
                                            UNDEFINED_OFFSET,
                                            collectionType.defaultType,
                                            collectionTypes.first { it.name == PROPERTY_COLLECTION_TYPE_NONE }.symbol
                                        )
                                    )
                                    // Link target
                                    putValueArgument(
                                        arg++,
                                        if (type == objectType) {
                                            irString(backingField.type.classifierOrFail.descriptor.name.identifier)
                                        } else
                                            irString("")
                                    )
                                    // Link property name
                                    putValueArgument(arg++, irString(""))
                                    // Property flags
                                    putValueArgument(
                                        arg++,
                                        buildSetOf(
                                            pluginContext, this@blockBody, propertyFlag.defaultType,
                                            if (nullable) {
                                                propertyFlags(listOf(PROPERTY_FLAG_NULLABLE))
                                            } else {
                                                propertyFlags(listOf(PROPERTY_FLAG_NORMAL))
                                            }
                                        )
                                    )
                                }
                            }
                        )
                    )
                }
            )
        }
        function.overriddenSymbols =
            listOf(realmObjectCompanionInterface.functions.first { it.name == REALM_OBJECT_COMPANION_SCHEMA_METHOD }.symbol)
    }

    private fun propertyFlags(flags: List<Name>) =
        flags.map { flag ->
            IrGetEnumValueImpl(
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                propertyFlag.defaultType,
                propertyFlags.first { flag == it.name }.symbol
            )
        }

    // Generate body for the synthetic new instance method defined inside the Companion instance previously declared via `RealmModelSyntheticCompanionExtension`
    fun addNewInstanceMethodBody(irClass: IrClass) {
        val companionObject = irClass.companionObject() as? IrClass
            ?: error("Companion object not available")

        val function = companionObject.functions.first { it.name == REALM_OBJECT_COMPANION_NEW_INSTANCE_METHOD }
        function.dispatchReceiverParameter = companionObject.thisReceiver?.copyTo(function)
        function.body = pluginContext.blockBody(function.symbol) {
            val defaultCtor = irClass.primaryConstructor
                ?: error("Can not find primary constructor")
            +irReturn(
                IrConstructorCallImpl( // CONSTRUCTOR_CALL 'public constructor <init> () [primary] declared in dev.nhachicha.A' type=dev.nhachicha.A origin=null
                    startOffset,
                    endOffset,
                    defaultCtor.returnType,
                    defaultCtor.symbol,
                    0,
                    0,
                    0,
                    origin = null
                )
            )
        }
        function.overriddenSymbols = listOf(realmObjectCompanionInterface.functions.first { it.name == REALM_OBJECT_COMPANION_NEW_INSTANCE_METHOD }.symbol)
    }

    private fun IrClass.addProperty(propertyName: Name, propertyType: IrType, initExpression: (startOffset: Int, endOffset: Int) -> IrExpressionBody) {
        // PROPERTY name:realmPointer visibility:public modality:OPEN [var]
        val property = addProperty {
            at(this@addProperty.startOffset, this@addProperty.endOffset)
            name = propertyName
            visibility = DescriptorVisibilities.PUBLIC
            modality = Modality.OPEN
            isVar = true
        }
        // FIELD PROPERTY_BACKING_FIELD name:objectPointer type:kotlin.Long? visibility:private
        property.backingField = pluginContext.irFactory.buildField {
            at(this@addProperty.startOffset, this@addProperty.endOffset)
            origin = IrDeclarationOrigin.PROPERTY_BACKING_FIELD
            name = property.name
            visibility = DescriptorVisibilities.PRIVATE
            modality = property.modality
            type = propertyType
        }.apply {
            // EXPRESSION_BODY
            //  CONST Boolean type=kotlin.Boolean value=false
            initializer = initExpression(startOffset, endOffset)
        }
        property.backingField?.parent = this
        property.backingField?.correspondingPropertySymbol = property.symbol

        // FUN DEFAULT _PROPERTY_ACCESSOR name:<get-objectPointer> visibility:public modality:OPEN <> ($this:dev.nhachicha.Foo.$RealmHandler) returnType:kotlin.Long?
        // correspondingProperty: PROPERTY name:objectPointer visibility:public modality:OPEN [var]
        val getter = property.addGetter {
            at(this@addProperty.startOffset, this@addProperty.endOffset)
            visibility = DescriptorVisibilities.PUBLIC
            modality = Modality.OPEN
            returnType = propertyType
            origin = IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR
        }
        // $this: VALUE_PARAMETER name:<this> type:dev.nhachicha.Foo.$RealmHandler
        getter.dispatchReceiverParameter = thisReceiver!!.copyTo(getter)
        // overridden:
        //   public abstract fun <get-realmPointer> (): kotlin.Long? declared in dev.nhachicha.RealmModelInternal
        val propertyAccessorGetter = realmModelInternal.getPropertyGetter(propertyName.asString())
            ?: error("${propertyName.asString()} function getter symbol is not available")
        getter.overriddenSymbols = listOf(propertyAccessorGetter)

        // BLOCK_BODY
        // RETURN type=kotlin.Nothing from='public final fun <get-objectPointer> (): kotlin.Long? declared in dev.nhachicha.Foo.$RealmHandler'
        // GET_FIELD 'FIELD PROPERTY_BACKING_FIELD name:objectPointer type:kotlin.Long? visibility:private' type=kotlin.Long? origin=null
        // receiver: GET_VAR '<this>: dev.nhachicha.Foo.$RealmHandler declared in dev.nhachicha.Foo.$RealmHandler.<get-objectPointer>' type=dev.nhachicha.Foo.$RealmHandler origin=null
        getter.body = pluginContext.blockBody(getter.symbol) {
            at(startOffset, endOffset)
            +irReturn(
                irGetField(irGet(getter.dispatchReceiverParameter!!), property.backingField!!)
            )
        }

        // FUN DEFAULT_PROPERTY_ACCESSOR name:<set-realmPointer> visibility:public modality:OPEN <> ($this:dev.nhachicha.Child, <set-?>:kotlin.Long?) returnType:kotlin.Unit
        //  correspondingProperty: PROPERTY name:realmPointer visibility:public modality:OPEN [var]
        val setter = property.addSetter {
            at(this@addProperty.startOffset, this@addProperty.endOffset)
            visibility = DescriptorVisibilities.PUBLIC
            modality = Modality.OPEN
            returnType = pluginContext.irBuiltIns.unitType
            origin = IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR
        }
        // $this: VALUE_PARAMETER name:<this> type:dev.nhachicha.Child
        setter.dispatchReceiverParameter = thisReceiver!!.copyTo(setter)
        setter.correspondingPropertySymbol = property.symbol

        // overridden:
        //  public abstract fun <set-realmPointer> (<set-?>: kotlin.Long?): kotlin.Unit declared in dev.nhachicha.RealmModelInternal
        val realmPointerSetter = realmModelInternal.getPropertySetter(propertyName.asString())
            ?: error("${propertyName.asString()} function getter symbol is not available")
        setter.overriddenSymbols = listOf(realmPointerSetter)

        // VALUE_PARAMETER name:<set-?> index:0 type:kotlin.Long?
        // BLOCK_BODY
        //  SET_FIELD 'FIELD PROPERTY_BACKING_FIELD name:realmPointer type:kotlin.Long? visibility:private' type=kotlin.Unit origin=null
        //  receiver: GET_VAR '<this>: io.realm.example.Sample declared in io.realm.example.Sample.<set-realmPointer>' type=io.realm.example.Sample origin=null
        //  value: GET_VAR '<set-?>: kotlin.Long? declared in io.realm.example.Sample.<set-realmPointer>' type=kotlin.Long? origin=null
        val valueParameter = setter.addValueParameter {
            this.name = SET
            this.type = propertyType
        }
        setter.body = DeclarationIrBuilder(pluginContext, setter.symbol).irBlockBody {
            at(startOffset, endOffset)
            +irSetField(irGet(setter.dispatchReceiverParameter!!), property.backingField!!, irGet(valueParameter))
        }
    }

    private fun irNull(startOffset: Int, endOffset: Int): IrExpressionBody =
        IrExpressionBodyImpl(startOffset, endOffset, IrConstImpl.constNull(startOffset, endOffset, pluginContext.irBuiltIns.nothingNType))

    private fun irFalse(startOffset: Int, endOffset: Int): IrExpressionBody =
        IrExpressionBodyImpl(startOffset, endOffset, IrConstImpl.constFalse(startOffset, endOffset, pluginContext.irBuiltIns.booleanType))
}
