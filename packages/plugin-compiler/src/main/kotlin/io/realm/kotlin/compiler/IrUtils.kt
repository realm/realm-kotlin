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

package io.realm.kotlin.compiler

import io.realm.kotlin.compiler.FqNames.BASE_REALM_OBJECT_INTERFACE
import io.realm.kotlin.compiler.FqNames.EMBEDDED_OBJECT_INTERFACE
import io.realm.kotlin.compiler.FqNames.KOTLIN_COLLECTIONS_LISTOF
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocationWithRange
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrBlockBuilder
import org.jetbrains.kotlin.ir.builders.at
import org.jetbrains.kotlin.ir.builders.declarations.IrFieldBuilder
import org.jetbrains.kotlin.ir.builders.declarations.IrFunctionBuilder
import org.jetbrains.kotlin.ir.builders.declarations.IrPropertyBuilder
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addGetter
import org.jetbrains.kotlin.ir.builders.declarations.addProperty
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrMutableAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrBranchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrElseBranchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrExpressionBodyImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrWhenImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.impl.IrTypeBase
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.copyTo
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.ir.util.nameForIrSerialization
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes.SUPER_TYPE_LIST
import java.lang.reflect.Field
import java.util.function.Predicate

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
    get() = isCompanionObject && (containingDeclaration as ClassDescriptor).isBaseRealmObject

val realmObjectInterfaceFqNames = setOf(FqNames.REALM_OBJECT_INTERFACE)
val realmEmbeddedObjectInterfaceFqNames = setOf(FqNames.EMBEDDED_OBJECT_INTERFACE)
val anyRealmObjectInterfacesFqNames = realmObjectInterfaceFqNames + realmEmbeddedObjectInterfaceFqNames

inline fun ClassDescriptor.hasInterfacePsi(interfaces: Set<String>): Boolean {
    // Using PSI to find super types to avoid cyclic reference (see https://github.com/realm/realm-kotlin/issues/339)
    var hasRealmObjectAsSuperType = false
    this.findPsi()?.acceptChildren(object : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {
            if (element.node.elementType == SUPER_TYPE_LIST) {
                hasRealmObjectAsSuperType = element.node.text.findAnyOf(interfaces) != null
            }
        }
    })

    return hasRealmObjectAsSuperType
}

val realmObjectPsiNames = setOf("RealmObject", "io.realm.kotlin.types.RealmObject")
val embeddedRealmObjectPsiNames = setOf("EmbeddedRealmObject", "io.realm.kotlin.types.EmbeddedRealmObject")
val ClassDescriptor.isRealmObject: Boolean
    get() = this.hasInterfacePsi(realmObjectPsiNames)
val ClassDescriptor.isEmbeddedRealmObject: Boolean
    get() = this.hasInterfacePsi(embeddedRealmObjectPsiNames)
val ClassDescriptor.isBaseRealmObject: Boolean
    get() = this.hasInterfacePsi(realmObjectPsiNames + embeddedRealmObjectPsiNames)

fun IrMutableAnnotationContainer.hasAnnotation(annotation: FqName): Boolean {
    return annotations.hasAnnotation(annotation)
}

val IrClass.isBaseRealmObject
    get() = superTypes.any { it.classFqName in anyRealmObjectInterfacesFqNames }

val IrClass.isRealmObject
    get() = superTypes.any { it.classFqName == BASE_REALM_OBJECT_INTERFACE }

val IrClass.isEmbeddedRealmObject: Boolean
    get() = superTypes.any { it.classFqName == EMBEDDED_OBJECT_INTERFACE }

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

internal fun IrClass.lookupFunction(name: Name, predicate: Predicate<IrSimpleFunction>? = null): IrSimpleFunction {
    return functions.firstOrNull { it.name == name && predicate?.test(it) ?: true }
        ?: throw AssertionError("Function '$name' not found in class '${this.name}'")
}

internal fun IrClass.lookupProperty(name: Name): IrProperty {
    return properties.firstOrNull { it.name == name }
        ?: throw AssertionError("Property '$name' not found in class '${this.name}'")
}

internal fun IrPluginContext.lookupFunctionInClass(
    fqName: FqName,
    function: String
): IrSimpleFunction {
    return lookupClassOrThrow(fqName).functions.first {
        it.name == Name.identifier(function)
    }
}

internal fun IrPluginContext.lookupClassOrThrow(name: FqName): IrClass {
    return referenceClass(name)?.owner
        ?: fatalError("Cannot find ${name.asString()} on platform $platform.")
}

internal fun IrPluginContext.lookupConstructorInClass(
    fqName: FqName,
    filter: (ctor: IrConstructorSymbol) -> Boolean = { true }
): IrConstructorSymbol {
    return referenceConstructors(fqName).first {
        filter(it)
    }
}

internal fun <T> IrClass.lookupCompanionDeclaration(
    name: Name
): T {
    return this.companionObject()?.declarations?.first { it.nameForIrSerialization == name } as T
        ?: fatalError("Cannot find companion method ${name.asString()} on ${this.name}")
}

object SchemaCollector {
    val properties = mutableMapOf<IrClass, MutableMap<String, SchemaProperty>>()
}

// ------------------------------------------------------------------------------

/**
 * This matches RealmEnums.CollectionType.
 */
enum class CollectionType(val description: String) {
    NONE("None"),
    LIST("RealmList"),
    SET("RealmSet"),
    DICTIONARY("RealmDictionary");
}

/**
 * This matches RealmEnums.PropertyType.
 */
enum class PropertyType {
    RLM_PROPERTY_TYPE_INT,
    RLM_PROPERTY_TYPE_BOOL,
    RLM_PROPERTY_TYPE_STRING,
    RLM_PROPERTY_TYPE_BINARY,
    RLM_PROPERTY_TYPE_OBJECT,
    RLM_PROPERTY_TYPE_FLOAT,
    RLM_PROPERTY_TYPE_DOUBLE,
    RLM_PROPERTY_TYPE_TIMESTAMP,
    RLM_PROPERTY_TYPE_OBJECT_ID,
    RLM_PROPERTY_TYPE_UUID,
}

data class CoreType(
    val propertyType: PropertyType,
    val nullable: Boolean
)

// FIXME use PropertyType instead of "type: String", consider using a common/shared type when implementing public schema
//  see (https://github.com/realm/realm-kotlin/issues/238)
data class SchemaProperty(
    val propertyType: PropertyType,
    val declaration: IrProperty,
    val collectionType: CollectionType = CollectionType.NONE,
    val coreGenericTypes: List<CoreType>? = null
)

// ------------------------------------------------------------------------------

@Suppress("LongParameterList")
internal fun <T : IrExpression> buildOf(
    context: IrPluginContext,
    startOffset: Int,
    endOffset: Int,
    function: IrSimpleFunctionSymbol,
    containerType: IrClass,
    elementType: IrType,
    args: List<T>
): IrExpression {
    return IrCallImpl(
        startOffset = startOffset, endOffset = endOffset,
        type = containerType.typeWith(elementType),
        symbol = function,
        typeArgumentsCount = 1,
        valueArgumentsCount = 1,
        origin = null,
        superQualifierSymbol = null
    ).apply {
        putTypeArgument(index = 0, type = elementType)
        putValueArgument(
            index = 0,
            valueArgument = IrVarargImpl(
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                context.irBuiltIns.arrayClass.typeWith(elementType),
                type,
                args.toList()
            )
        )
    }
}

internal fun <T : IrExpression> buildSetOf(
    context: IrPluginContext,
    startOffset: Int,
    endOffset: Int,
    elementType: IrType,
    args: List<T>
): IrExpression {
    val setOf = context.referenceFunctions(FqName("kotlin.collections.setOf"))
        .first {
            val parameters = it.owner.valueParameters
            parameters.size == 1 && parameters.first().isVararg
        }
    val setIrClass: IrClass = context.lookupClassOrThrow(FqNames.KOTLIN_COLLECTIONS_SET)
    return buildOf(context, startOffset, endOffset, setOf, setIrClass, elementType, args)
}

internal fun <T : IrExpression> buildListOf(
    context: IrPluginContext,
    startOffset: Int,
    endOffset: Int,
    elementType: IrType,
    args: List<T>
): IrExpression {
    val listOf = context.referenceFunctions(KOTLIN_COLLECTIONS_LISTOF)
        .first {
            val parameters = it.owner.valueParameters
            parameters.size == 1 && parameters.first().isVararg
        }
    val listIrClass: IrClass = context.lookupClassOrThrow(FqNames.KOTLIN_COLLECTIONS_LIST)
    return buildOf(context, startOffset, endOffset, listOf, listIrClass, elementType, args)
}

fun IrClass.addValueProperty(
    pluginContext: IrPluginContext,
    superClass: IrClass,
    propertyName: Name,
    propertyType: IrType,
    initExpression: (startOffset: Int, endOffset: Int) -> IrExpression
): IrProperty {
    // PROPERTY name:realmPointer visibility:public modality:OPEN [var]
    val property = addProperty {
        at(this@addProperty.startOffset, this@addProperty.endOffset)
        name = propertyName
        visibility = DescriptorVisibilities.PUBLIC
        modality = Modality.FINAL
        isVar = true
    }
    // FIELD PROPERTY_BACKING_FIELD name:objectPointer type:kotlin.Long? visibility:private
    property.backingField = pluginContext.irFactory.buildField {
        at(this@addValueProperty.startOffset, this@addValueProperty.endOffset)
        origin = IrDeclarationOrigin.PROPERTY_BACKING_FIELD
        name = property.name
        visibility = DescriptorVisibilities.PRIVATE
        modality = property.modality
        type = propertyType
    }.apply {
        initializer = IrExpressionBodyImpl(initExpression(startOffset, endOffset))
    }
    property.backingField?.parent = this
    property.backingField?.correspondingPropertySymbol = property.symbol

    val getter = property.addGetter {
        at(this@addValueProperty.startOffset, this@addValueProperty.endOffset)
        visibility = DescriptorVisibilities.PUBLIC
        modality = Modality.FINAL
        returnType = propertyType
        origin = IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR
    }
    // $this: VALUE_PARAMETER name:<this> type:dev.nhachicha.Foo.$RealmHandler
    getter.dispatchReceiverParameter = thisReceiver!!.copyTo(getter)
    // overridden:
    //   public abstract fun <get-realmPointer> (): kotlin.Long? declared in dev.nhachicha.RealmObjectInternal
    val propertyAccessorGetter = superClass.getPropertyGetter(propertyName.asString())
        ?: fatalError("${propertyName.asString()} function getter symbol is not available")
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
    return property
}

internal fun IrClass.addFakeOverrides(
    receiver: IrClassSymbol,
    functions: Set<Name>,
) {
    val overrides = receiver.owner.declarations.filterIsInstance<IrSimpleFunction>()
        .filter { it.name in functions }
    for (override in overrides) {
        addFunction {
            updateFrom(override)
            name = override.name
            returnType = override.returnType
            origin = IrDeclarationOrigin.FAKE_OVERRIDE
            isFakeOverride = true
        }.apply {
            override.valueParameters.forEach { x ->
                addValueParameter(x.name, x.type)
            }
            this.overriddenSymbols = listOf(override.symbol)
            dispatchReceiverParameter =
                receiver.owner.thisReceiver!!.copyTo(this)
        }
    }
}

// Copy of Kotlin's Fir2IrComponents.createSafeCallConstruction
fun IrBlockBuilder.createSafeCallConstruction(
    receiverVariable: IrVariable,
    receiverVariableSymbol: IrValueSymbol,
    expressionOnNotNull: IrExpression,
): IrExpression {
    val startOffset = expressionOnNotNull.startOffset
    val endOffset = expressionOnNotNull.endOffset

    val resultType = expressionOnNotNull.type.makeNullable()
    return IrBlockImpl(startOffset, endOffset, resultType, IrStatementOrigin.SAFE_CALL).apply {
        statements += receiverVariable
        statements += IrWhenImpl(startOffset, endOffset, resultType).apply {
            val condition = IrCallImpl(
                startOffset, endOffset, context.irBuiltIns.booleanType,
                context.irBuiltIns.eqeqSymbol,
                valueArgumentsCount = 2,
                typeArgumentsCount = 0,
                origin = IrStatementOrigin.EQEQ
            ).apply {
                putValueArgument(0, IrGetValueImpl(startOffset, endOffset, receiverVariableSymbol))
                putValueArgument(
                    1,
                    IrConstImpl.constNull(startOffset, endOffset, context.irBuiltIns.nothingNType)
                )
            }
            branches += IrBranchImpl(
                condition,
                IrConstImpl.constNull(startOffset, endOffset, context.irBuiltIns.nothingNType)
            )
            branches += IrElseBranchImpl(
                IrConstImpl.boolean(startOffset, endOffset, context.irBuiltIns.booleanType, true),
                expressionOnNotNull
            )
        }
    }
}

/**
 * Using reflection to invoke the `arguments` attribute of the `IrSimpleType` to determine the enclosing type
 * of the `RealmList`.
 * This work around is needed since `IrSimpleType` became an abstract class in Kotlin 1.7 (see https://github.com/JetBrains/kotlin/commit/53210770a6877c5c08735070f8eff3e33573f0f5)
 * which causes the compiler to throw: "java.lang.IncompatibleClassChangeError: Found class org.jetbrains.kotlin.ir.types.IrSimpleType, but interface was expected"
 * when the compiler plugin is compiled with Kotlin 1.6.10.
 */
// FIXME remove when upgrading to Kotlin 1.7 (revert to usage of `(backingField.type as IrSimpleType).arguments[0] as IrSimpleType` instead)
fun getCollectionElementType(backingFieldType: IrType): IrType? {
    if (backingFieldType is IrSimpleType) {
        val args: Field = backingFieldType::class.java.getDeclaredField("arguments")
        args.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val values: List<IrTypeArgument> = args.get(backingFieldType) as List<IrTypeArgument>
        if (values.isNotEmpty()) {
            return (values[0] as IrTypeBase).type
        }
    }
    return null
}

/** Finds the line and column of [IrDeclaration] */
fun IrDeclaration.locationOf(): CompilerMessageSourceLocation {
    val sourceRangeInfo = file.fileEntry.getSourceRangeInfo(
        beginOffset = startOffset,
        endOffset = endOffset
    )
    return CompilerMessageLocationWithRange.create(
        path = sourceRangeInfo.filePath,
        lineStart = sourceRangeInfo.startLineNumber + 1,
        columnStart = sourceRangeInfo.startColumnNumber + 1,
        lineEnd = sourceRangeInfo.endLineNumber + 1,
        columnEnd = sourceRangeInfo.endColumnNumber + 1,
        lineContent = null
    )!!
}

/**
 * Method to indicate fatal issues that should not have happeneded; as opposed to user modeling
 * errors that are reported as compiler errors.
 */
fun fatalError(message: String): Nothing {
    error(message)
}
