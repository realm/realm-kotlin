package io.realm.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.declarations.IrFieldBuilder
import org.jetbrains.kotlin.ir.builders.declarations.IrFunctionBuilder
import org.jetbrains.kotlin.ir.builders.declarations.IrPropertyBuilder
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.name.FqName
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

val ClassDescriptor.isRealmModuleAnnotated
    get() = annotations.hasAnnotation(FqNames.REALM_MODULE_ANNOTATION)

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

internal fun findFunctionByName(functions: Sequence<IrSimpleFunction>, name: String): IrSimpleFunction {
    return functions.firstOrNull { it.name.asString() == name }
        ?: throw AssertionError("Function $name is not declared")
}

internal fun findFunctionInClass(pluginContext: IrPluginContext, fqName: FqName, function: String): IrSimpleFunction {
    return pluginContext.referenceClass(fqName)?.owner?.functions?.first {
        it.name == Name.identifier(function)
    } ?: error("Can not find '$function' method in '${fqName.asString()}'")
}

object SchemaCollector {
    val properties = mutableMapOf<String, MutableMap<String, Pair<String, Boolean>>>()
    val realmObjectClassesIrClasses = mutableListOf<IrClass>()

    fun reset() {
        properties.clear()
        realmObjectClassesIrClasses.clear()
    }
}
