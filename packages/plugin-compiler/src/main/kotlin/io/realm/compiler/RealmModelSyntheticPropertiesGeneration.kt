package io.realm.compiler

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
import org.jetbrains.kotlin.ir.builders.declarations.*
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrExpressionBodyImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.createType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.name.Name

class RealmModelSyntheticPropertiesGeneration(private val pluginContext: IrPluginContext) {
    fun addProperties(irClass: IrClass): IrClass =
            irClass.apply {
                addNullableProperty(REALM_POINTER, pluginContext.irBuiltIns.longType.makeNullable()) //TODO expose via Type class for reusability with synthetic
                addNullableProperty(OBJECT_POINTER, pluginContext.irBuiltIns.longType.makeNullable())
                addNullableProperty(OBJECT_TABLE_NAME, pluginContext.irBuiltIns.stringType.makeNullable())
            }


    private fun irNull(startOffset: Int, endOffset: Int): IrConstImpl<Nothing?> {
        return IrConstImpl.constNull(startOffset, endOffset, pluginContext.irBuiltIns.nothingNType)
    }

    private fun IrClass.addNullableProperty(propertyName: Name, propertyType: IrType) {
        // PROPERTY name:realmPointer visibility:public modality:FINAL [var]
        val property = addProperty {
            name = propertyName
            visibility = DescriptorVisibilities.PUBLIC
            modality = Modality.FINAL
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
            initializer = IrExpressionBodyImpl(startOffset, endOffset, irNull(startOffset, endOffset))
        }
        property.backingField?.parent = this
        property.backingField?.correspondingPropertySymbol = property.symbol

        // FUN DEFAULT _PROPERTY_ACCESSOR name:<get-objectPointer> visibility:public modality:FINAL <> ($this:dev.nhachicha.Foo.$RealmHandler) returnType:kotlin.Long?
        // correspondingProperty: PROPERTY name:objectPointer visibility:public modality:FINAL [var]
        val getter = property.addGetter {
            visibility = DescriptorVisibilities.PUBLIC
            modality = Modality.FINAL
            returnType = propertyType
            origin = IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR
        }
        // $this: VALUE_PARAMETER name:<this> type:dev.nhachicha.Foo.$RealmHandler
        getter.dispatchReceiverParameter = thisReceiver!!.copyTo(getter)

        // BLOCK_BODY
        // RETURN type=kotlin.Nothing from='public final fun <get-objectPointer> (): kotlin.Long? declared in dev.nhachicha.Foo.$RealmHandler'
        // GET_FIELD 'FIELD PROPERTY_BACKING_FIELD name:objectPointer type:kotlin.Long? visibility:private' type=kotlin.Long? origin=null
        // receiver: GET_VAR '<this>: dev.nhachicha.Foo.$RealmHandler declared in dev.nhachicha.Foo.$RealmHandler.<get-objectPointer>' type=dev.nhachicha.Foo.$RealmHandler origin=null
        getter.body = pluginContext.blockBody(getter.symbol) {
            +irReturn(
                    irGetField(irGet(getter.dispatchReceiverParameter!!), property.backingField!!)
            )
        }

        // FUN DEFAULT_PROPERTY_ACCESSOR name:<set-objectPointer> visibility:public modality:FINAL <> ($this:dev.nhachicha.Foo.$RealmHandler, <set-?>:kotlin.Long?) returnType:kotlin.Unit
        // correspondingProperty: PROPERTY name:objectPointer visibility:public modality:FINAL [var]
        val setter = property.addSetter() {
            visibility = DescriptorVisibilities.PUBLIC
            modality = Modality.FINAL
            returnType = pluginContext.irBuiltIns.unitType
            origin = IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR
        }
        // $this: VALUE_PARAMETER name:<this> type:dev.nhachicha.Foo.$RealmHandler
        setter.dispatchReceiverParameter = thisReceiver!!.copyTo(setter)
        setter.correspondingPropertySymbol = property.symbol

        // BLOCK_BODY
        //  SET_FIELD 'FIELD PROPERTY_BACKING_FIELD name:objectPointer type:kotlin.Long? visibility:private' type=kotlin.Unit origin=null
        //  receiver: GET_VAR '<this>: dev.nhachicha.Foo.$RealmHandler declared in dev.nhachicha.Foo.$RealmHandler.<set-objectPointer>' type=dev.nhachicha.Foo.$RealmHandler origin=null
        //  value: GET_VAR '<set-?>: kotlin.Long? declared in dev.nhachicha.Foo.$RealmHandler.<set-objectPointer>' type=kotlin.Long? origin=null
        val valueParameter = setter.addValueParameter {
            this.name = SET
            this.type = pluginContext.symbols.long.createType(hasQuestionMark = true, arguments = emptyList()) // TODO move to util
        }
        setter.body = DeclarationIrBuilder(pluginContext, setter.symbol).irBlockBody {
            +irSetField(irGet(setter.dispatchReceiverParameter!!), property.backingField!!, irGet(valueParameter))
        }
    }
}

