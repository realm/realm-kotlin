package io.realm.compiler

import io.realm.compiler.FqNames.NATIVE_POINTER
import io.realm.compiler.FqNames.REALM_MODEL_COMPANION
import io.realm.compiler.FqNames.REALM_MODEL_INTERFACE
import io.realm.compiler.Names.NEW_INSTANCE_METHOD
import io.realm.compiler.Names.OBJECT_IS_MANAGED
import io.realm.compiler.Names.OBJECT_POINTER
import io.realm.compiler.Names.OBJECT_TABLE_NAME
import io.realm.compiler.Names.REALM_POINTER
import io.realm.compiler.Names.SCHEMA_METHOD
import io.realm.compiler.Names.SET
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
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
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrExpressionBodyImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.createType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.getPropertySetter
import org.jetbrains.kotlin.name.Name

class RealmModelSyntheticPropertiesGeneration(private val pluginContext: IrPluginContext) {
    private val realmModelInternal = pluginContext.referenceClass(REALM_MODEL_INTERFACE)
        ?: error("${REALM_MODEL_INTERFACE.asString()} is not available")
    private val nullableNativePointerInterface = pluginContext.referenceClass(NATIVE_POINTER)?.createType(true, emptyList())
        ?: error("${NATIVE_POINTER.asString()} interface not found")
    private val realmCompanionInterface = pluginContext.referenceClass(REALM_MODEL_COMPANION)
        ?: error("${REALM_MODEL_COMPANION.asString()} interface not found")

    fun addProperties(irClass: IrClass): IrClass =
        irClass.apply {
            addProperty(REALM_POINTER, nullableNativePointerInterface, ::irNull)
            addProperty(OBJECT_POINTER, nullableNativePointerInterface, ::irNull)
            addProperty(OBJECT_TABLE_NAME, pluginContext.irBuiltIns.stringType.makeNullable(), ::irNull)
            addProperty(OBJECT_IS_MANAGED, pluginContext.irBuiltIns.booleanType, ::irFalse)
        }

    // Generate body for the synthetic schema method defined inside the Companion instance previously declared via `RealmModelSyntheticCompanionExtension`
    fun addSchemaMethodBody(irClass: IrClass) {
        val companionObject = irClass.companionObject() as? IrClass
            ?: error("Companion object not available")

        val name = irClass.name.identifier
        val fields: MutableMap<String, Pair<String, Boolean>> = SchemaCollector.properties.getOrDefault(name, mutableMapOf())

        val function = companionObject.functions.first { it.name == SCHEMA_METHOD }
        function.dispatchReceiverParameter = companionObject.thisReceiver?.copyTo(function)
        function.body = pluginContext.blockBody(function.symbol) {
            +irReturn(irString(schemaString(name, fields)))
        }

        function.overriddenSymbols = listOf(realmCompanionInterface.owner.functions.first { it.name == SCHEMA_METHOD }.symbol)
    }

    // Generate body for the synthetic new instance method defined inside the Companion instance previously declared via `RealmModelSyntheticCompanionExtension`
    fun addNewInstanceMethodBody(irClass: IrClass) {
        val companionObject = irClass.companionObject() as? IrClass
            ?: error("Companion object not available")

        val function = companionObject.functions.first { it.name == NEW_INSTANCE_METHOD }
        function.dispatchReceiverParameter = companionObject.thisReceiver?.copyTo(function)
        function.body = pluginContext.blockBody(function.symbol) {
            val defaultCtor = irClass.constructors.find { it.isPrimary }
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
        function.overriddenSymbols = listOf(realmCompanionInterface.owner.functions.first { it.name == NEW_INSTANCE_METHOD }.symbol)
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
        val propertyAccessorGetter = realmModelInternal.owner.getPropertyGetter(propertyName.asString())
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
        val realmPointerSetter = realmModelInternal.owner.getPropertySetter(propertyName.asString())
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
            val fields = itField.next()
            builder.append("{\"${fields.key}\": {\"type\": \"${fields.value.first}\", \"nullable\": \"${fields.value.second}\"}}")
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
