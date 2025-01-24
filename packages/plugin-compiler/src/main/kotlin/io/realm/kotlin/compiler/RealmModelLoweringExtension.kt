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

@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package io.realm.kotlin.compiler

import io.realm.kotlin.compiler.ClassIds.MODEL_OBJECT_ANNOTATION
import io.realm.kotlin.compiler.ClassIds.REALM_MODEL_COMPANION
import io.realm.kotlin.compiler.ClassIds.REALM_OBJECT_INTERNAL_INTERFACE
import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.CompilationException
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.runOnFilePostfix
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.isAnonymousObject
import org.jetbrains.kotlin.ir.util.isEnumClass
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.platform.konan.isNative
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

class RealmModelLoweringExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        RealmModelLowering(pluginContext).lowerFromModuleFragment(moduleFragment)
    }
}

private class RealmModelLowering(private val pluginContext: IrPluginContext) : ClassLoweringPass {

    // NOTE This is only available on Native platforms
    val modelObjectAnnotationClass by lazy {
        pluginContext.lookupClassOrThrow(MODEL_OBJECT_ANNOTATION)
    }

    // TODO 1.9-DEPRECATION Remove and rely on ClassLoweringPass.lower(IrModuleFragment) when
    //  leaving 1.9 support
    // Workaround that FileLoweringPass.lower(IrModuleFragment) is implemented as extension method
    // in 1.9 but as proper interface method in 2.0. Implementation in both versions are more or
    // less the same but this common implementation can loose some information as the IrElement is
    // also not uniformly available on the CompilationException across versions.
    fun lowerFromModuleFragment(
        moduleFragment: IrModuleFragment
    ) = moduleFragment.files.forEach {
        try {
            lower(it)
        } catch (e: CompilationException) {
            // Unfortunately we cannot access the IR element of e uniformly across 1.9 and 2.0 so
            // leaving it as null. Hopefully the embedded cause will give the appropriate pointers
            // to fix this.
            throw CompilationException(
                "Internal error in realm lowering : ${this::class.qualifiedName}: ${e.message}",
                it,
                null,
                cause = e
            ).apply {
                stackTrace = e.stackTrace
            }
        } catch (e: KotlinExceptionWithAttachments) {
            throw e
        } catch (e: Throwable) {
            throw CompilationException(
                "Internal error in file lowering : ${this::class.qualifiedName}: ${e.message}",
                it,
                null,
                cause = e
            ).apply {
                stackTrace = e.stackTrace
            }
        }
    }

    override fun lower(irFile: IrFile) = runOnFilePostfix(irFile)

    override fun lower(irClass: IrClass) {
        if (irClass.isBaseRealmObject) {
            // Throw error with classes that we do not support
            if (irClass.isData) {
                error("Data class '${irClass.kotlinFqName}' is not currently supported. Only normal classes can inherit from 'RealmObject' or 'EmbeddedRealmObject'.")
            }
            if (irClass.isEnumClass) {
                error("Enum class '${irClass.kotlinFqName}' is not supported. Only normal classes can inherit from 'RealmObject' or 'EmbeddedRealmObject'.")
            }
            if (irClass.isObject) {
                error("Object declarations are not supported. Only normal classes can inherit from 'RealmObject' or 'EmbeddedRealmObject'.")
            }
            if (irClass.isAnonymousObject) {
                error("Anonymous objects are not supported. Only normal classes can inherit from 'RealmObject' or 'EmbeddedRealmObject'.")
            }
            // For native we add @ModelObject(irClass.Companion::class) as associated object to be
            // able to resolve the companion object during runtime due to absence of
            // kotlin.reflect.full.companionObjectInstance
            if (pluginContext.platform.isNative()) {
                val type = modelObjectAnnotationClass.defaultType as? IrType ?: throw IllegalStateException("defaultType is not an IrType")
                val primaryConstructor = modelObjectAnnotationClass.primaryConstructor ?: throw IllegalStateException("primaryConstructor is null")
                val constructorSymbol = primaryConstructor.symbol as? IrConstructorSymbol ?: throw IllegalStateException("symbol is not an IrConstructorSymbol")
                val modelObjectAnnotation = IrConstructorCallImpl.fromSymbolOwner(
                    startOffset = UNDEFINED_OFFSET,
                    endOffset = UNDEFINED_OFFSET,
                    type = type,
                    constructorSymbol = constructorSymbol
                ).apply {
                    putValueArgument(
                        0,
                        IrClassReferenceImpl(
                            startOffset, endOffset,
                            pluginContext.irBuiltIns.kClassClass.starProjectedType,
                            irClass.companionObject()!!.symbol,
                            type
                        )
                    )
                }
                irClass.annotations += modelObjectAnnotation
            }
            // add super type RealmObjectInternal and RealmObjectInterop
            val realmObjectInternalInterface: IrClassSymbol =
                pluginContext.lookupClassOrThrow(REALM_OBJECT_INTERNAL_INTERFACE).symbol
            irClass.superTypes += realmObjectInternalInterface.defaultType

            // Generate RealmObjectInternal properties overrides
            val generator = RealmModelSyntheticPropertiesGeneration(pluginContext)
            generator.addRealmObjectInternalProperties(irClass)

            // Modify properties accessor to generate custom getter/setter
            AccessorModifierIrGeneration(pluginContext).modifyPropertiesAndCollectSchema(irClass)

            // Add custom toString, equals and hashCode methods
            val methodGenerator = RealmModelDefaultMethodGeneration(pluginContext)
            methodGenerator.addDefaultMethods(irClass)

            // Add body for synthetic companion methods
            val companion = irClass.companionObject() ?: fatalError("RealmObject without companion: ${irClass.kotlinFqName}")
            generator.addCompanionFields(irClass, companion, SchemaCollector.properties[irClass])
            generator.addSchemaMethodBody(irClass)
            generator.addNewInstanceMethodBody(irClass)
        } else {
            if (irClass.isCompanion && irClass.parentAsClass.isBaseRealmObject) {
                val realmModelCompanion: IrClassSymbol =
                    pluginContext.lookupClassOrThrow(REALM_MODEL_COMPANION).symbol
                irClass.superTypes += realmModelCompanion.defaultType
            }
        }
    }
}
