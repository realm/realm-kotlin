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

import io.realm.compiler.FqNames.REALM_MODEL_COMPANION
import io.realm.compiler.FqNames.REALM_MODEL_INTERFACE
import io.realm.compiler.FqNames.REALM_NATIVE_POINTER
import io.realm.compiler.FqNames.TABLE
import io.realm.compiler.Names.REALM_OBJECT_COMPANION_NEW_INSTANCE_METHOD
import io.realm.compiler.Names.REALM_OBJECT_COMPANION_SCHEMA_METHOD
import io.realm.compiler.Names.OBJECT_IS_MANAGED
import io.realm.compiler.Names.OBJECT_POINTER
import io.realm.compiler.Names.OBJECT_TABLE_NAME
import io.realm.compiler.Names.REALM_OBJECT_SCHEMA
import io.realm.compiler.Names.REALM_POINTER
import io.realm.compiler.Names.SET
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.ValueDescriptor
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
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
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
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.toKotlinType
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.getPropertySetter
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.asSimpleType
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly

class RealmModelSyntheticPropertiesGeneration(private val pluginContext: IrPluginContext) {
    private val realmModelInternal = pluginContext.lookupClassOrThrow(REALM_MODEL_INTERFACE)
    private val nullableNativePointerInterface = pluginContext.lookupClassOrThrow(REALM_NATIVE_POINTER)
        .symbol.createType(true, emptyList())
    private val realmObjectCompanionInterface = pluginContext.lookupClassOrThrow(REALM_MODEL_COMPANION)
    private val table = pluginContext.lookupClassOrThrow(TABLE)

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
    // FIXME OPTIMIZE Move all lookups to class level
    // TODO OPTIMIZE should be a one time only constructed object
    @OptIn(ObsoleteDescriptorBasedAPI::class)
    fun addSchemaMethodBody(irClass: IrClass) {
        val companionObject = irClass.companionObject() as? IrClass
            ?: error("Companion object not available")

        val fields: MutableMap<String, Pair<String, Any>> =
            SchemaCollector.properties.getOrDefault(irClass, mutableMapOf())

        val function = companionObject.functions.first { it.name == REALM_OBJECT_COMPANION_SCHEMA_METHOD }
        function.dispatchReceiverParameter = companionObject.thisReceiver?.copyTo(function)

        val tableConstructor =
            table.primaryConstructor ?: error("Couldn't find constructor for $TABLE")

        val classFlag: IrClass = pluginContext.lookupClassOrThrow(FqName("io.realm.interop.ClassFlag"))
        val classFlags = classFlag.declarations.filter { it is IrEnumEntry } as List<IrEnumEntry>

        val property = pluginContext.lookupClassOrThrow(FqName("io.realm.interop.Property"))
        val propertyConstructor = property.primaryConstructor ?: error("Couldn't find constructor for $TABLE")
        val propertyFlag: IrClass = pluginContext.lookupClassOrThrow(FqName("io.realm.interop.PropertyFlag"))
        val propertyFlags = propertyFlag.declarations.filter { it is IrEnumEntry } as List<IrEnumEntry>
        val propertyType: IrClass = pluginContext.lookupClassOrThrow(FqName("io.realm.interop.PropertyType"))
        val propertyTypes = propertyType.declarations.filter { it is IrEnumEntry } as List<IrEnumEntry>
        val collectionType: IrClass = pluginContext.lookupClassOrThrow(FqName("io.realm.interop.CollectionType"))
        val collectionTypes = collectionType.declarations.filter { it is IrEnumEntry } as List<IrEnumEntry>

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
                    // Name
                    putValueArgument(0, irString(irClass.name.identifier))
                    // Primary key
                    putValueArgument(1, irString(""))
                    // Flags
                    putValueArgument(
                        2,
                        buildSetOf(pluginContext, this@blockBody, classFlag.defaultType, listOf(
                            IrGetEnumValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, classFlag.defaultType, classFlags.first { it.name == Name.identifier("RLM_CLASS_NORMAL") }.symbol)
                        ))
                    )
                    // Properties
                    putValueArgument(
                        3,
                        buildListOf(pluginContext, this@blockBody, property.defaultType,
                            fields.map { entry ->
                                val type = propertyTypes.firstOrNull {
                                    it.name.identifier.toLowerCaseAsciiOnly()
                                        .contains(entry.value.first)
                                } ?: error("Unknown type ${entry.value.first}")
                                val objectType = propertyTypes.firstOrNull {
                                    it.name.identifier == "RLM_PROPERTY_TYPE_OBJECT"
                                } ?: error("Unknown type ${entry.value.first}")
                                IrConstructorCallImpl(
                                    startOffset,
                                    endOffset,
                                    type = property.defaultType,
                                    symbol = propertyConstructor.symbol,
                                    constructorTypeArgumentsCount = 0,
                                    typeArgumentsCount = 0,
                                    valueArgumentsCount = 7
                                ).apply {
                                    // Name
                                    putValueArgument(0, irString(entry.key))
                                    // Public name
                                    putValueArgument(1, irString(""))
                                    // Type
                                    putValueArgument(2,
                                            IrGetEnumValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, propertyType.defaultType, type.symbol)
                                    )
                                    // Collection type
                                    putValueArgument(3,
                                        IrGetEnumValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, collectionType.defaultType, collectionTypes.first { it.name.identifier == "RLM_COLLECTION_TYPE_NONE" }.symbol)
                                    )
                                    // Link target
                                    putValueArgument(4,
                                        if (type == objectType) {
                                            val x= entry.value.second as IrProperty
                                            irString(x.backingField!!.type.classifierOrFail.descriptor.name.identifier)
//                                            irString((second.descriptor.original as PropertyDescriptor).type.name.identifier)
                                        } else
                                        irString("")
                                    )
                                    // Link property name
                                    putValueArgument(5, irString(""))
                                    // Property flags
                                    putValueArgument(6,
                                        buildSetOf(pluginContext, this@blockBody, propertyFlag.defaultType,
                                            listOf(IrGetEnumValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, propertyFlag.defaultType, propertyFlags.first { it.name.identifier == "RLM_PROPERTY_NULLABLE" }.symbol))
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

    private fun schemaString(name: String, fields: MutableMap<String, Pair<String, Boolean>>): String {
        val builder = StringBuilder("{\"name\": \"${name}\", \"properties\": [")

        val itField = fields.iterator()
        while (itField.hasNext()) {
            val field = itField.next()
            builder.append("{\"${field.key}\": {\"type\": \"${field.value.first}\", \"nullable\": \"${field.value.second}\"}}")
            if (itField.hasNext()) {
                builder.append(",")
            }
        }
        builder.append("]}")
        return builder.toString()
    }

    private fun irNull(startOffset: Int, endOffset: Int): IrExpressionBody =
        IrExpressionBodyImpl(startOffset, endOffset, IrConstImpl.constNull(startOffset, endOffset, pluginContext.irBuiltIns.nothingNType))

    private fun irFalse(startOffset: Int, endOffset: Int): IrExpressionBody =
        IrExpressionBodyImpl(startOffset, endOffset, IrConstImpl.constFalse(startOffset, endOffset, pluginContext.irBuiltIns.booleanType))
}
