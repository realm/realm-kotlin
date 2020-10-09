package io.realm.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.jvm.ir.propertyIfAccessor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.declarations.IrFieldBuilder
import org.jetbrains.kotlin.ir.builders.declarations.IrFunctionBuilder
import org.jetbrains.kotlin.ir.builders.declarations.IrPropertyBuilder
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.impl.IrPropertyCommonImpl
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.isBoolean
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.isLong
import org.jetbrains.kotlin.ir.types.isNullableString
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.name.Name

// Somehow addSetter was removed from the IrProperty in https://github.com/JetBrains/kotlin/commit/d1dc938a5d7331ba43fcbb8ce53c3e17ef76a22a#diff-2726c3747ace0a1c93ad82365cf3ff18L114
// Remove this extension when this will be re-introduced? see https://kotlinlang.slack.com/archives/C7L3JB43G/p1600888883006300
inline fun IrProperty.addSetter(builder: IrFunctionBuilder.() -> Unit = {}): IrSimpleFunction =
    IrFunctionBuilder().run {
        factory.buildFun {
            this.name = Name.special("<set-${this@addSetter.name}>")
            builder()
        }.also { setter ->
            this@addSetter.setter = setter
            setter.correspondingPropertySymbol = this@addSetter.symbol
            setter.parent = this@addSetter.parent
        }
    }

fun IrPluginContext.blockBody(
    symbol: IrSymbol,
    block: IrBlockBodyBuilder.() -> Unit
): IrBlockBody =
    DeclarationIrBuilder(this, symbol).irBlockBody { block() }

val ClassDescriptor.isRealmObjectCompanion
    get() = isCompanionObject && (containingDeclaration as ClassDescriptor).isRealmObject

val ClassDescriptor.isRealmObject
    get() = annotations.hasAnnotation(FqNames.REALM_OBJECT_ANNOTATION)

fun IrFunction.isRealmString(): Boolean {
    // check should cover getter and setter, we use the backing field property for this
    val type = (propertyIfAccessor as IrPropertyCommonImpl).backingField?.type
    return if (type != null) {
        type.isString() || type.isNullableString()
    } else {
        false
    }
}

fun IrFunction.isRealmLong(): Boolean {
    val type = (propertyIfAccessor as IrPropertyCommonImpl).backingField?.type
    return if (type != null) {
        return type.makeNotNull().isLong()
    } else {
        false
    }
}

fun IrFunction.isRealmInt(): Boolean {
    val type = (propertyIfAccessor as IrPropertyCommonImpl).backingField?.type
    return if (type != null) {
        // there's no isNullableInt (see https://kotlinlang.slack.com/archives/C7L3JB43G/p1599501968080600) so use this hack instead to check for both Int and Int?
        return type.makeNotNull().isInt()
    } else {
        false
    }
}

fun IrFunction.isRealmBoolean(): Boolean {
    val type = (propertyIfAccessor as IrPropertyCommonImpl).backingField?.type
    return if (type != null) {
        return type.makeNotNull().isBoolean()
    } else {
        false
    }
}

internal fun IrFunctionBuilder.at(startOffset: Int, endOffset: Int) = also {
    this.startOffset = startOffset
    this.endOffset = endOffset
}

internal fun IrFieldBuilder.at(startOffset: Int, endOffset: Int) = also {
    this.startOffset = startOffset
    this.endOffset = endOffset
}

internal fun IrPropertyBuilder.at(startOffset: Int, endOffset: Int) = also {
    this.startOffset = startOffset
    this.endOffset = endOffset
}

object SchemaCollector {
    val properties = mutableMapOf<String, MutableMap<String, Pair<String, Boolean>>>()
}
