package io.realm.compiler

import io.realm.compiler.FqNames.REALM_MODEL_INTERFACE
import io.realm.compiler.Names.OBJECT_IS_MANAGED
import io.realm.compiler.Names.OBJECT_POINTER
import io.realm.compiler.Names.OBJECT_TABLE_NAME
import io.realm.compiler.Names.REALM_POINTER
import io.realm.compiler.Names.SET
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addGetter
import org.jetbrains.kotlin.ir.builders.declarations.addProperty
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrExpressionBodyImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.createType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.getPropertySetter
import org.jetbrains.kotlin.name.Name

class RealmModelSyntheticPropertiesGeneration(private val pluginContext: IrPluginContext) {
    private val realmModelInterface = pluginContext.referenceClass(REALM_MODEL_INTERFACE)
            ?: error("${REALM_MODEL_INTERFACE.asString()} is not available")

    private val realmPointerGetter = realmModelInterface.owner.getPropertyGetter(REALM_POINTER.asString()) ?: error("${REALM_POINTER.asString()} function getter symbol is not available")
    private val realmPointerSetter = realmModelInterface.owner.getPropertySetter(REALM_POINTER.asString()) ?: error("${REALM_POINTER.asString()} function getter symbol is not available")
    private val realmObjectPointerGetter = realmModelInterface.owner.getPropertyGetter(OBJECT_POINTER.asString()) ?: error("${OBJECT_POINTER.asString()} function getter symbol is not available")
    private val realmObjectPointerSetter = realmModelInterface.owner.getPropertySetter(OBJECT_POINTER.asString()) ?: error("${OBJECT_POINTER.asString()} function getter symbol is not available")
    private val realmObjectIsManagedGetter = realmModelInterface.owner.getPropertyGetter(OBJECT_IS_MANAGED.asString()) ?: error("${OBJECT_IS_MANAGED.asString()} function getter symbol is not available")
    private val realmObjectIsManagedSetter = realmModelInterface.owner.getPropertySetter(OBJECT_IS_MANAGED.asString()) ?: error("${OBJECT_IS_MANAGED.asString()} function getter symbol is not available")
    private val realmObjectTableNameGetter = realmModelInterface.owner.getPropertyGetter(OBJECT_TABLE_NAME.asString()) ?: error("${OBJECT_TABLE_NAME.asString()} function getter symbol is not available")
    private val realmObjectTableNameSetter = realmModelInterface.owner.getPropertySetter(OBJECT_TABLE_NAME.asString()) ?: error("${OBJECT_TABLE_NAME.asString()} function getter symbol is not available")

    fun addProperties(irClass: IrClass): IrClass =
            irClass.apply {
                addNullableProperty(REALM_POINTER, pluginContext.irBuiltIns.longType.makeNullable(), realmPointerGetter, realmPointerSetter)
                addNullableProperty(OBJECT_POINTER, pluginContext.irBuiltIns.longType.makeNullable(), realmObjectPointerGetter, realmObjectPointerSetter)
                addNullableProperty(OBJECT_TABLE_NAME, pluginContext.irBuiltIns.stringType.makeNullable(), realmObjectTableNameGetter, realmObjectTableNameSetter)
                addNullableProperty(OBJECT_IS_MANAGED, pluginContext.irBuiltIns.booleanType.makeNullable(), realmObjectIsManagedGetter, realmObjectIsManagedSetter)
            }


    private fun irNull(startOffset: Int, endOffset: Int): IrConstImpl<Nothing?> {
        return IrConstImpl.constNull(startOffset, endOffset, pluginContext.irBuiltIns.nothingNType)
    }

    private fun IrClass.addNullableProperty(propertyName: Name, propertyType: IrType, propertyAccessorGetter: IrSimpleFunctionSymbol, propertyAccessorSetter: IrSimpleFunctionSymbol) {
        // PROPERTY name:realmPointer visibility:public modality:OPEN [var]
        val property = addProperty {
            name = propertyName
            visibility = DescriptorVisibilities.PUBLIC
            modality = Modality.OPEN
            isVar = true
        }
        // FIELD PROPERTY_BACKING_FIELD name:objectPointer type:kotlin.Long? visibility:private
        property.backingField = pluginContext.irFactory.buildField {
            origin = IrDeclarationOrigin.PROPERTY_BACKING_FIELD
            name = property.name
            visibility = DescriptorVisibilities.PRIVATE
            modality = property.modality
            type = propertyType

        }.apply {
        // EXPRESSION_BODY
        //  CONST Null type=kotlin.Nothing? value=null
            initializer = IrExpressionBodyImpl(startOffset, endOffset, irNull(startOffset, endOffset))
        }
        property.backingField?.parent = this
        property.backingField?.correspondingPropertySymbol = property.symbol


        // FUN DEFAULT _PROPERTY_ACCESSOR name:<get-objectPointer> visibility:public modality:OPEN <> ($this:dev.nhachicha.Foo.$RealmHandler) returnType:kotlin.Long?
        // correspondingProperty: PROPERTY name:objectPointer visibility:public modality:OPEN [var]
        val getter = property.addGetter {
            visibility = DescriptorVisibilities.PUBLIC
            modality = Modality.OPEN
            returnType = propertyType
            origin = IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR
        }
        // $this: VALUE_PARAMETER name:<this> type:dev.nhachicha.Foo.$RealmHandler
        getter.dispatchReceiverParameter = thisReceiver!!.copyTo(getter)
        // overridden:
        //   public abstract fun <get-realmPointer> (): kotlin.Long? declared in dev.nhachicha.RealmModelInterface
        getter.overriddenSymbols = listOf(propertyAccessorGetter)

        // BLOCK_BODY
        // RETURN type=kotlin.Nothing from='public final fun <get-objectPointer> (): kotlin.Long? declared in dev.nhachicha.Foo.$RealmHandler'
        // GET_FIELD 'FIELD PROPERTY_BACKING_FIELD name:objectPointer type:kotlin.Long? visibility:private' type=kotlin.Long? origin=null
        // receiver: GET_VAR '<this>: dev.nhachicha.Foo.$RealmHandler declared in dev.nhachicha.Foo.$RealmHandler.<get-objectPointer>' type=dev.nhachicha.Foo.$RealmHandler origin=null
        getter.body = pluginContext.blockBody(getter.symbol) {
            +irReturn(
                    irGetField(irGet(getter.dispatchReceiverParameter!!), property.backingField!!)
            )
        }

        // FUN DEFAULT_PROPERTY_ACCESSOR name:<set-realmPointer> visibility:public modality:OPEN <> ($this:dev.nhachicha.Child, <set-?>:kotlin.Long?) returnType:kotlin.Unit
        //  correspondingProperty: PROPERTY name:realmPointer visibility:public modality:OPEN [var]
        val setter = property.addSetter() {
            visibility = DescriptorVisibilities.PUBLIC
            modality = Modality.OPEN
            returnType = pluginContext.irBuiltIns.unitType
            origin = IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR
        }
        // $this: VALUE_PARAMETER name:<this> type:dev.nhachicha.Child
        setter.dispatchReceiverParameter = thisReceiver!!.copyTo(setter)
        setter.correspondingPropertySymbol = property.symbol

        // overridden:
        //  public abstract fun <set-realmPointer> (<set-?>: kotlin.Long?): kotlin.Unit declared in dev.nhachicha.RealmModelInterface
        setter.overriddenSymbols =  listOf(propertyAccessorSetter)

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
            +irSetField(irGet(setter.dispatchReceiverParameter!!), property.backingField!!, irGet(valueParameter))
        }
    }
}

