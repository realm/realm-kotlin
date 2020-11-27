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
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation
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

val IrClass.isRealmModelAnnotated
    get() = annotations.hasAnnotation(FqNames.REALM_OBJECT_ANNOTATION)

val IrClass.isRealmModuleAnnotated
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

internal fun IrClass.lookupFunction(name: String): IrSimpleFunction {
    return functions.firstOrNull { it.name.asString() == name }
        ?: throw AssertionError("Function $name was not found")
}

internal fun IrPluginContext.lookupFunctionInClass(fqName: FqName, function: String): IrSimpleFunction {
    return lookupClassOrThrow(fqName).functions.first {
        it.name == Name.identifier(function)
    }
}

internal fun IrPluginContext.lookupClassOrThrow(name: FqName): IrClass {
    return referenceClass(name)?.owner
        ?: error("Cannot find ${name.asString()} on platform $platform.")
}

internal fun IrPluginContext.lookupConstructorInClass(fqName: FqName, filter: (ctor: IrConstructorSymbol) -> Boolean): IrConstructorSymbol {
    return referenceConstructors(fqName).first {
        filter(it)
    }
}

object SchemaCollector {
    val properties = mutableMapOf<IrClass, MutableMap<String, Pair<String, Boolean>>>()
}
