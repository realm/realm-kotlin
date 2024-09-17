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

@file:OptIn(UnsafeDuringIrConstructionAPI::class, UnsafeDuringIrConstructionAPI::class)

package io.realm.kotlin.compiler

import io.realm.kotlin.compiler.ClassIds.BASE_REALM_OBJECT_INTERFACE
import io.realm.kotlin.compiler.ClassIds.EMBEDDED_OBJECT_INTERFACE
import io.realm.kotlin.compiler.ClassIds.KOTLIN_COLLECTIONS_LISTOF
import io.realm.kotlin.compiler.ClassIds.PERSISTED_NAME_ANNOTATION
import io.realm.kotlin.compiler.ClassIds.REALM_OBJECT_INTERFACE
import io.realm.kotlin.compiler.FqNames.PACKAGE_TYPES
import io.realm.kotlin.compiler.Names.EMBEDDED_REALM_OBJECT
import io.realm.kotlin.compiler.Names.REALM_OBJECT
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocationWithRange
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.classId
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
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrMutableAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrPropertyReference
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrBranchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrElseBranchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrWhenImpl
import org.jetbrains.kotlin.ir.interpreter.getAnnotation
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.impl.IrAbstractSimpleType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.copyTo
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.util.superTypes
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes.SUPER_TYPE_LIST
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.types.KotlinType
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

val realmObjectInterfaceFqNames = setOf(REALM_OBJECT_INTERFACE)
val realmEmbeddedObjectInterfaceFqNames = setOf(EMBEDDED_OBJECT_INTERFACE)
val anyRealmObjectInterfacesFqNames = realmObjectInterfaceFqNames + realmEmbeddedObjectInterfaceFqNames

fun IrType.classIdOrFail(): ClassId = getClass()?.classId ?: error("Can't get classId of ${render()}")

@Suppress("NOTHING_TO_INLINE")
inline fun PsiElement.hasInterface(interfaces: Set<String>): Boolean {
    var hasRealmObjectAsSuperType = false
    this.acceptChildren(object : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {
            if (element.node.elementType == SUPER_TYPE_LIST) {
                // Check supertypes for classes with Embbeded/RealmObject as generics and remove
                // them from the string so as to avoid erroneously processing said classes which
                // implement these types as implementing Embedded/RealmObject. Doing so would
                // add our companion interface causing compilation errors.
                val elementNodeText = element.node.text
                    .replace(" ", "") // Sanitize removing spaces
                    .split(",") // Split by commas
                    .filter {
                        !(
                            it.contains("<RealmObject>") ||
                                it.contains("<io.realm.kotlin.types.RealmObject>") ||
                                it.contains("<EmbeddedRealmObject>") ||
                                it.contains("<io.realm.kotlin.types.EmbeddedRealmObject>")
                            )
                    }.joinToString(",") // Re-sanitize again
                hasRealmObjectAsSuperType = elementNodeText.findAnyOf(interfaces) != null
            }
        }
    })

    return hasRealmObjectAsSuperType
}

@Suppress("NOTHING_TO_INLINE")
inline fun ClassDescriptor.hasInterfacePsi(interfaces: Set<String>): Boolean {
    // Using PSI to find super types to avoid cyclic reference (see https://github.com/realm/realm-kotlin/issues/339)
    return this.findPsi()?.hasInterface(interfaces) ?: false
}

// Do to the way PSI works, it can be a bit tricky to uniquely identify when the Realm Kotlin
// RealmObject interface is used. For that reason, once we have determined a match for RealmObject,
// We also need to ensure we didn't accidentally matched on the Realm Java RealmObject abstract
// type. Fortunately that is visible in the PSI as `RealmObject()` (Java, abstract class) vs.
// `RealmObject` (Kotlin, interface).
val realmObjectPsiNames = setOf("RealmObject", "io.realm.kotlin.types.RealmObject")
val embeddedRealmObjectPsiNames = setOf("EmbeddedRealmObject", "io.realm.kotlin.types.EmbeddedRealmObject")
val realmJavaObjectPsiNames = setOf("io.realm.RealmObject()", "RealmObject()")
val ClassDescriptor.isRealmObject: Boolean
    get() = this.hasInterfacePsi(realmObjectPsiNames) && !this.hasInterfacePsi(realmJavaObjectPsiNames)
val ClassDescriptor.isEmbeddedRealmObject: Boolean
    get() = this.hasInterfacePsi(embeddedRealmObjectPsiNames)
val ClassDescriptor.isBaseRealmObject: Boolean
    get() = this.hasInterfacePsi(realmObjectPsiNames + embeddedRealmObjectPsiNames) && !this.hasInterfacePsi(realmJavaObjectPsiNames)

val realmObjectTypes: Set<Name> = setOf(REALM_OBJECT, EMBEDDED_REALM_OBJECT)
val realmObjectClassIds = realmObjectTypes.map { name -> ClassId(PACKAGE_TYPES, name) }

// This is the K2 equivalent of our PSI hack to determine if a symbol has a RealmObject base class.
// There is currently no way to determine this within the resolved type system and there is
// probably no such option around the corner.
// https://kotlinlang.slack.com/archives/C03PK0PE257/p1694599154558669
@OptIn(SymbolInternals::class)
val FirClassSymbol<*>.isBaseRealmObject: Boolean
    get() = this.classKind == ClassKind.CLASS &&
        this.fir.superTypeRefs.any { typeRef ->
            when (typeRef) {
                // In SUPERTYPES stage
                is FirUserTypeRef -> {
                    typeRef.qualifier.last().name in realmObjectTypes &&
                        // Disregard constructor invocations as that means that it is a Realm Java class
                        !(
                            typeRef.source?.run { treeStructure.getParent(lighterASTNode) }
                                ?.tokenType?.let { it == KtStubElementTypes.CONSTRUCTOR_CALLEE }
                                ?: false
                            )
                }
                // After SUPERTYPES stage
                is FirResolvedTypeRef -> typeRef.type.classId in realmObjectClassIds
                else -> false
            }
        }

// JetBrains already have a method `fun IrAnnotationContainer.hasAnnotation(symbol: IrClassSymbol)`
// It is unclear exactly what the difference is and how to get a ClassSymbol from a ClassId,
// so for now just work around it.
fun IrAnnotationContainer?.hasAnnotation(annotation: ClassId): Boolean {
    return this?.hasAnnotation(annotation.asSingleFqName()) ?: false
}

fun IrMutableAnnotationContainer.hasAnnotation(annotation: FqName): Boolean {
    return annotations.hasAnnotation(annotation)
}

val IrClass.isBaseRealmObject
    get() = superTypes.any { it.classId in anyRealmObjectInterfacesFqNames }

val IrClass.isRealmObject
    get() = superTypes.any { it.classId == BASE_REALM_OBJECT_INTERFACE }

val IrClass.isEmbeddedRealmObject: Boolean
    get() = superTypes.any { it.classId == EMBEDDED_OBJECT_INTERFACE }

val IrType.classId: ClassId?
    get() = this.getClass()?.classId

val IrType.isEmbeddedRealmObject: Boolean
    get() = superTypes().any { it.classId == EMBEDDED_OBJECT_INTERFACE }

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
    clazz: ClassId,
    function: String
): IrSimpleFunction {
    return lookupClassOrThrow(clazz).functions.first {
        it.name == Name.identifier(function)
    }
}

internal fun IrPluginContext.lookupClassOrThrow(name: ClassId): IrClass {
    return referenceClass(name)?.owner
        ?: fatalError("Cannot find ${name.asString()} on platform $platform.")
}

internal fun IrPluginContext.lookupConstructorInClass(
    clazz: ClassId,
    filter: (ctor: IrConstructorSymbol) -> Boolean = { true }
): IrConstructorSymbol {
    return referenceConstructors(clazz).first {
        filter(it)
    }
}

internal fun <T> IrClass.lookupCompanionDeclaration(
    name: Name
): T {
    @Suppress("UNCHECKED_CAST")
    return this.companionObject()?.declarations?.first {
        it is IrDeclarationWithName && it.name == name
    } as T
        ?: fatalError("Cannot find companion method ${name.asString()} on ${this.name}")
}

// Copy of `KotlinType.getKotlinTypeFqName` from Kotlin 1.8.21. This method needs to be backported
// as it is not available in Kotlin 1.8.0.
internal fun KotlinType.getKotlinTypeFqNameCompat(printTypeArguments: Boolean): String {
    val declaration = requireNotNull(constructor.declarationDescriptor) {
        "declarationDescriptor is null for constructor = $constructor with ${constructor.javaClass}"
    }
    if (declaration is TypeParameterDescriptor) {
        return StringUtil.join(declaration.upperBounds, { type -> type.getKotlinTypeFqNameCompat(printTypeArguments) }, "&")
    }

    val typeArguments = arguments
    val typeArgumentsAsString = if (printTypeArguments && !typeArguments.isEmpty()) {
        val joinedTypeArguments = StringUtil.join(typeArguments, { projection -> projection.type.getKotlinTypeFqNameCompat(false) }, ", ")

        "<$joinedTypeArguments>"
    } else {
        ""
    }

    return DescriptorUtils.getFqName(declaration).asString() + typeArgumentsAsString
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
    RLM_PROPERTY_TYPE_MIXED,
    RLM_PROPERTY_TYPE_TIMESTAMP,
    RLM_PROPERTY_TYPE_FLOAT,
    RLM_PROPERTY_TYPE_DOUBLE,
    RLM_PROPERTY_TYPE_OBJECT,
    RLM_PROPERTY_TYPE_LINKING_OBJECTS,
    RLM_PROPERTY_TYPE_DECIMAL128,
    RLM_PROPERTY_TYPE_OBJECT_ID,
    RLM_PROPERTY_TYPE_UUID,
}

data class CoreType(
    val propertyType: PropertyType,
    val nullable: Boolean
)

private const val NO_ALIAS = ""
// FIXME use PropertyType instead of "type: String", consider using a common/shared type when implementing public schema
//  see (https://github.com/realm/realm-kotlin/issues/238)
data class SchemaProperty(
    val propertyType: PropertyType,
    val declaration: IrProperty,
    val collectionType: CollectionType = CollectionType.NONE,
    val coreGenericTypes: List<CoreType>? = null
) {
    val isComputed = propertyType == PropertyType.RLM_PROPERTY_TYPE_LINKING_OBJECTS
    val hasPersistedNameAnnotation = declaration.backingField != null && declaration.hasAnnotation(PERSISTED_NAME_ANNOTATION)
    val persistedName: String
    val publicName: String

    init {
        val declarationName = declaration.name.identifier
        val persistedAnnotationName: String? = if (hasPersistedNameAnnotation) getPersistedName(declaration) else null

        // We only set the public name if the persisted and public names are different
        // because core would otherwise detect it as a duplicated name and fail.
        if (hasPersistedNameAnnotation && persistedAnnotationName!! != declarationName) {
            persistedAnnotationName.ifEmpty {
                logError(
                    "Names must contain at least 1 character.",
                    declaration.locationOf()
                )
            }

            // Set the persisted name to the name passed to `@PersistedName`
            persistedName = persistedAnnotationName
            // Set the public name to the original Kotlin name
            publicName = declarationName
        } else {
            persistedName = declarationName
            publicName = NO_ALIAS
        }
    }

    companion object {
        fun getPersistedName(declaration: IrProperty): String {
            @Suppress("UNCHECKED_CAST")
            return (declaration.getAnnotation(PERSISTED_NAME_ANNOTATION.asSingleFqName()).getValueArgument(0)!! as IrConstImpl<String>).value
        }
    }
}

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
    val setOf = context.referenceFunctions(CallableId(FqName("kotlin.collections"), Name.identifier("setOf")))
        .first {
            val parameters = it.owner.valueParameters
            parameters.size == 1 && parameters.first().isVararg
        }
    val setIrClass: IrClass = context.lookupClassOrThrow(ClassIds.KOTLIN_COLLECTIONS_SET)
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
    val listIrClass: IrClass = context.lookupClassOrThrow(ClassIds.KOTLIN_COLLECTIONS_LIST)
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
        name = property.name
        visibility = DescriptorVisibilities.PRIVATE
        modality = property.modality
        type = propertyType
    }.apply {
        initializer = factory.createExpressionBody(startOffset, endOffset, initExpression(startOffset, endOffset))
    }
    property.backingField?.parent = this
    property.backingField?.correspondingPropertySymbol = property.symbol

    val getter = property.addGetter {
        at(this@addValueProperty.startOffset, this@addValueProperty.endOffset)
        visibility = DescriptorVisibilities.PUBLIC
        modality = Modality.FINAL
        returnType = propertyType
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
            irGetField(
                irGet(getter.dispatchReceiverParameter!!),
                property.backingField!!,
                property.backingField!!.type
            )
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
            return (values[0] as IrType).type
        }
    }
    return null
}

fun getBacklinksTargetType(backingField: IrField): IrType {
    (backingField.initializer!!.expression as IrCall).let { irCall ->
        val propertyReference = irCall.getValueArgument(0) as IrPropertyReference
        val propertyType = (propertyReference.type as IrAbstractSimpleType)
        return propertyType.arguments[0] as IrType
    }
}

fun getBacklinksTargetPropertyType(declaration: IrProperty): IrType? {
    val backingField: IrField = declaration.backingField!!

    (backingField.initializer!!.expression as IrCall).let { irCall ->
        val targetPropertyParameter = irCall.getValueArgument(0)

        // Limit linkingObjects to accept only initialization parameters
        if (targetPropertyParameter is IrPropertyReference) {
            val propertyType = (targetPropertyParameter.type as IrAbstractSimpleType)
            return propertyType.arguments[1] as IrType
        } else {
            logError(
                "Error in backlinks field ${declaration.name} - only direct property references are valid parameters.",
                backingField.locationOf()
            )
            return null
        }
    }
}

fun getLinkingObjectPropertyName(backingField: IrField): String {
    (backingField.initializer!!.expression as IrCall).let { irCall ->
        val propertyReference = irCall.getValueArgument(0) as IrPropertyReference
        val targetProperty: IrProperty = propertyReference.symbol.owner
        return if (targetProperty.hasAnnotation(PERSISTED_NAME_ANNOTATION)) {
            SchemaProperty.getPersistedName(targetProperty)
        } else {
            targetProperty.name.identifier
        }
    }
}

/**
 * Returns the underlying schema name for a given class type
 */
fun getSchemaClassName(clazz: IrClass): String {
    return if (clazz.hasAnnotation(PERSISTED_NAME_ANNOTATION)) {
        @Suppress("UNCHECKED_CAST")
        return (clazz.getAnnotation(PERSISTED_NAME_ANNOTATION.asSingleFqName()).getValueArgument(0)!! as IrConstImpl<String>).value
    } else {
        clazz.name.identifier
    }
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
