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

import io.realm.kotlin.compiler.ClassIds.IGNORE_ANNOTATION
import io.realm.kotlin.compiler.ClassIds.TRANSIENT_ANNOTATION
import io.realm.kotlin.compiler.ClassIds.TYPE_ADAPTER_ANNOTATION
import io.realm.kotlin.compiler.Names.OBJECT_REFERENCE
import io.realm.kotlin.compiler.Names.REALM_SYNTHETIC_PROPERTY_PREFIX
import io.realm.kotlin.compiler.Names.REALM_TYPE_ADAPTER_FROM_REALM
import io.realm.kotlin.compiler.Names.REALM_TYPE_ADAPTER_TO_REALM
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBlockBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irIfNull
import org.jetbrains.kotlin.ir.builders.irLetS
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.descriptors.toIrBasedKotlinType
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrDeclarationReference
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrSetFieldImpl
import org.jetbrains.kotlin.ir.interpreter.getAnnotation
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrStarProjection
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.impl.IrAbstractSimpleType
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.isBoolean
import org.jetbrains.kotlin.ir.types.isByte
import org.jetbrains.kotlin.ir.types.isByteArray
import org.jetbrains.kotlin.ir.types.isChar
import org.jetbrains.kotlin.ir.types.isDouble
import org.jetbrains.kotlin.ir.types.isFloat
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.isLong
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.types.isShort
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.types.isSubtypeOfClass
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.superTypes
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.findAnnotation
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import kotlin.IllegalStateException
import kotlin.collections.set

/**
 * Modifies the IR tree to transform getter/setter to call the C-Interop layer to retrieve read the managed values from the Realm
 * It also collect the schema information while processing the class properties.
 */
class AccessorModifierIrGeneration(realmPluginContext: RealmPluginContext) : RealmPluginContext by realmPluginContext {

    private lateinit var objectReferenceProperty: IrProperty
    private lateinit var objectReferenceType: IrType

    data class TypeAdapterMethodReferences(
        val propertyType: IrType,
        val toPublic: (IrBuilderWithScope.(IrGetValue, IrFunctionAccessExpression) -> IrDeclarationReference),
        val fromPublic: (IrBuilderWithScope.(IrGetValue, IrGetValue) -> IrDeclarationReference),
    )

    fun IrConstructorCall.getTypeAdapterInfo(): Triple<IrClassReference, IrType, IrType> =
        (getValueArgument(0)!! as IrClassReference).let { adapterClassReference ->
            adapterClassReference.symbol
                .superTypes()
                .first {
                    it.classId == ClassIds.REALM_TYPE_ADAPTER_INTERFACE
                }
                .let {
                    it as IrSimpleType
                }
                .arguments
                .let { arguments ->
                    Triple(
                        adapterClassReference,
                        arguments[0].typeOrNull!!,
                        arguments[1].typeOrNull!!
                    )
                }
        }

    fun modifyPropertiesAndCollectSchema(irClass: IrClass) {
        logDebug("Processing class ${irClass.name}")
        val fields = SchemaCollector.properties
            .getOrPut(irClass) {
                mutableMapOf()
            }

        objectReferenceProperty = irClass.lookupProperty(OBJECT_REFERENCE)
        objectReferenceType = objectReferenceProperty.backingField!!.type

        irClass.transformChildrenVoid(object : IrElementTransformerVoid() {
            @Suppress("LongMethod")
            override fun visitProperty(declaration: IrProperty): IrStatement {
                val name = declaration.name.asString()

                // Don't redefine accessors for internal synthetic properties or process declarations of subclasses
                @Suppress("ComplexCondition")
                if (declaration.backingField == null ||
                    // If the getter's dispatch receiver is null we cannot generate our accessors
                    // so skip processing those (See https://github.com/realm/realm-kotlin/issues/1296)
                    declaration.getter?.dispatchReceiverParameter == null ||
                    name.startsWith(REALM_SYNTHETIC_PROPERTY_PREFIX) ||
                    declaration.parentAsClass != irClass
                ) {
                    return declaration
                }

                var propertyTypeRaw = declaration.backingField!!.type
                var propertyType = propertyTypeRaw.makeNotNull()
                var nullable = propertyTypeRaw.isNullable()
                val excludeProperty =
                    declaration.hasAnnotation(IGNORE_ANNOTATION) ||
                        declaration.hasAnnotation(TRANSIENT_ANNOTATION) ||
                        declaration.backingField!!.hasAnnotation(IGNORE_ANNOTATION) ||
                        declaration.backingField!!.hasAnnotation(TRANSIENT_ANNOTATION)

                // Check for property modifiers:
                // - Persisted properties must be marked `var`.
                // - `lateinit` is not allowed.
                // - Backlinks must be marked `val`. The compiler will enforce wrong use of `var`.
                // - `const` is not allowed. The compiler will enforce wrong use of `const` inside classes.
                if (!excludeProperty &&
                    !propertyType.isLinkingObject() &&
                    !propertyType.isEmbeddedLinkingObject()
                ) {
                    if (declaration.isLateinit) {
                        logError("Persisted properties must not be marked with `lateinit`.", declaration.locationOf())
                    }
                    if (!declaration.isVar) {
                        logError("Persisted properties must be marked with `var`. `val` is not supported.", declaration.locationOf())
                    }
                }

                val typeAdapterMethodReferences = if (declaration.hasAnnotation(TYPE_ADAPTER_ANNOTATION)) {
                    logDebug("Object property named ${declaration.name} is an adapted type.")

                    if (declaration.isDelegated) {
                        logError("Type adapters do not support delegated properties")
                    }

                    val (adapterClassReference, realmType, userType) = declaration
                        .getAnnotation(TYPE_ADAPTER_ANNOTATION.asSingleFqName())
                        .getTypeAdapterInfo()

                    val adapterClass: IrClass = adapterClassReference.classType.getClass()!!

                    if (propertyType.classId != userType.classId) {
                        // TODO improve messaging
                        logError("Not matching types 1")
                    }

                    // Replace the property type with the one from the type adapter
                    propertyTypeRaw = realmType
                    propertyType = realmType.makeNotNull()
                    nullable = realmType.isNullable()

                    // Generate the conversion calls based on whether the adapter is singleton or not.
                    when (adapterClass.kind) {
                        ClassKind.CLASS -> {
                            TypeAdapterMethodReferences(
                                propertyType = propertyType,
                                toPublic = { objReference, realmValue ->
                                    irCall(callee = providedAdapterFromRealm).apply {
                                        // pass the class from the annotation
                                        putValueArgument(0, objReference)
                                        putValueArgument(1, adapterClassReference)
                                        putValueArgument(2, realmValue)
                                    }
                                },
                                fromPublic = { objReference, publicValue ->
                                    irCall(callee = providedAdapterToRealm).apply {
                                        // pass the class from the annotation
                                        putValueArgument(0, objReference)
                                        putValueArgument(1, adapterClassReference)
                                        putValueArgument(2, publicValue)
                                    }
                                }
                            )
                        }

                        ClassKind.OBJECT -> {
                            val fromRealm =
                                adapterClass.lookupFunction(REALM_TYPE_ADAPTER_FROM_REALM)
                            val toRealm =
                                adapterClass.lookupFunction(REALM_TYPE_ADAPTER_TO_REALM)

                            TypeAdapterMethodReferences(
                                propertyType = propertyType,
                                toPublic = { _, realmValue ->
                                    irCall(callee = fromRealm).apply {
                                        putValueArgument(0, realmValue)
                                        dispatchReceiver = irGetObject(adapterClass.symbol)
                                    }
                                },
                                fromPublic = { _, publicValue ->
                                    irCall(callee = toRealm).apply {
                                        putValueArgument(0, publicValue)
                                        dispatchReceiver = irGetObject(adapterClass.symbol)
                                    }
                                }
                            )
                        }

                        else -> throw IllegalStateException("Unsupported type")
                    }
                } else {
                    null
                }

                when {
                    excludeProperty -> {
                        logDebug("Property named ${declaration.name} ignored")
                    }
                    propertyType.isMutableRealmInteger() -> {
                        logDebug("MutableRealmInt property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_INT,
                            declaration = declaration,
                            computedType = propertyTypeRaw,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        // Not using the default fromPublic/toPublic solution here. We agreed
                        // changing the frontend in the future might incur in several changes to the
                        // implementation so we believe it to be a good decision to postpone making
                        // changes to the current framework until we embark in the next big update.
                        // TL;DR: use custom paths for this datatype as it requires references to
                        // the managed object to which the fields belongs.
                        modifyAccessor(
                            property = schemaProperty,
                            type = propertyType,
                            getFunction = getMutableInt,
                            fromRealmValue = null,
                            toPublic = typeAdapterMethodReferences?.toPublic,
                            setFunction = setValue,
                            fromPublic = typeAdapterMethodReferences?.fromPublic,
                            toRealmValue = null
                        )
                    }
                    propertyType.isRealmAny() -> {
                        logDebug("RealmAny property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        if (!nullable) {
                            logError(
                                "Error in field ${declaration.name} - RealmAny fields must be nullable.",
                                declaration.locationOf()
                            )
                        }
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_MIXED,
                            computedType = propertyTypeRaw,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            type = propertyType,
                            getFunction = getRealmAny,
                            fromRealmValue = null,
                            toPublic = typeAdapterMethodReferences?.toPublic,
                            setFunction = setValue,
                            fromPublic = typeAdapterMethodReferences?.fromPublic,
                            toRealmValue = null
                        )
                    }

                    propertyType.isByteArray() -> {
                        logDebug("ByteArray property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_BINARY,
                            declaration = declaration,
                            computedType = propertyTypeRaw,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            type = propertyType,
                            getFunction = getByteArray,
                            fromRealmValue = null,
                            toPublic = typeAdapterMethodReferences?.toPublic,
                            setFunction = setValue,
                            fromPublic = typeAdapterMethodReferences?.fromPublic,
                            toRealmValue = null
                        )
                    }
                    propertyType.isString() -> {
                        logDebug("String property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_STRING,
                            declaration = declaration,
                            computedType = propertyTypeRaw,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            type = propertyType,
                            getFunction = getString,
                            fromRealmValue = null,
                            toPublic = typeAdapterMethodReferences?.toPublic,
                            setFunction = setValue,
                            fromPublic = typeAdapterMethodReferences?.fromPublic,
                            toRealmValue = null
                        )
                    }
                    propertyType.isByte() -> {
                        logDebug("Byte property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_INT,
                            declaration = declaration,
                            computedType = propertyTypeRaw,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            type = propertyType,
                            getFunction = getLong,
                            fromRealmValue = longToByte,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = { _, value ->
                                irCall(callee = byteToLong).apply {
                                    putValueArgument(0, value)
                                }
                            },
                            toRealmValue = null
                        )
                    }
                    propertyType.isChar() -> {
                        logDebug("Char property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_INT,
                            declaration = declaration,
                            computedType = propertyTypeRaw,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            type = propertyType,
                            getFunction = getLong,
                            fromRealmValue = longToChar,
                            toPublic = typeAdapterMethodReferences?.toPublic,
                            setFunction = setValue,
                            fromPublic = { _, value ->
                                irCall(callee = charToLong).apply {
                                    putValueArgument(0, value)
                                }
                            },
                            toRealmValue = null
                        )
                    }
                    propertyType.isShort() -> {
                        logDebug("Short property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_INT,
                            declaration = declaration,
                            computedType = propertyTypeRaw,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            type = propertyType,
                            getFunction = getLong,
                            fromRealmValue = longToShort,
                            toPublic = typeAdapterMethodReferences?.toPublic,
                            setFunction = setValue,
                            fromPublic = { _, value ->
                                irCall(callee = shortToLong).apply {
                                    putValueArgument(0, value)
                                }
                            },
                            toRealmValue = null
                        )
                    }
                    propertyType.isInt() -> {
                        logDebug("Int property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_INT,
                            declaration = declaration,
                            computedType = propertyTypeRaw,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            type = propertyType,
                            getFunction = getLong,
                            fromRealmValue = longToInt,
                            toPublic = typeAdapterMethodReferences?.toPublic,
                            setFunction = setValue,
                            fromPublic = { _, value ->
                                irCall(callee = intToLong).apply {
                                    putValueArgument(0, value)
                                }
                            },
                            toRealmValue = null
                        )
                    }
                    propertyType.isLong() -> {
                        logDebug("Long property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_INT,
                            declaration = declaration,
                            computedType = propertyTypeRaw,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            type = propertyType,
                            getFunction = getLong,
                            fromRealmValue = null,
                            toPublic = typeAdapterMethodReferences?.toPublic,
                            setFunction = setValue,
                            fromPublic = typeAdapterMethodReferences?.fromPublic,
                            toRealmValue = null
                        )
                    }
                    propertyType.isBoolean() -> {
                        logDebug("Boolean property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_BOOL,
                            declaration = declaration,
                            computedType = propertyTypeRaw,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            type = propertyType,
                            getFunction = getBoolean,
                            fromRealmValue = null,
                            toPublic = typeAdapterMethodReferences?.toPublic,
                            setFunction = setValue,
                            fromPublic = typeAdapterMethodReferences?.fromPublic,
                            toRealmValue = null
                        )
                    }
                    propertyType.isFloat() -> {
                        logDebug("Float property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_FLOAT,
                            declaration = declaration,
                            computedType = propertyTypeRaw,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            type = propertyType,
                            getFunction = getFloat,
                            fromRealmValue = null,
                            toPublic = typeAdapterMethodReferences?.toPublic,
                            setFunction = setValue,
                            fromPublic = typeAdapterMethodReferences?.fromPublic,
                            toRealmValue = null
                        )
                    }
                    propertyType.isDouble() -> {
                        logDebug("Double property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_DOUBLE,
                            declaration = declaration,
                            computedType = propertyTypeRaw,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            type = propertyType,
                            getFunction = getDouble,
                            fromRealmValue = null,
                            toPublic = typeAdapterMethodReferences?.toPublic,
                            setFunction = setValue,
                            fromPublic = typeAdapterMethodReferences?.fromPublic,
                            toRealmValue = null
                        )
                    }
                    propertyType.isDecimal128() -> {
                        logDebug("Decimal128 property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_DECIMAL128,
                            declaration = declaration,
                            computedType = propertyTypeRaw,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            type = propertyType,
                            getFunction = getDecimal128,
                            fromRealmValue = null,
                            toPublic = typeAdapterMethodReferences?.toPublic,
                            setFunction = setValue,
                            fromPublic = typeAdapterMethodReferences?.fromPublic,
                            toRealmValue = null
                        )
                    }
                    propertyType.isEmbeddedLinkingObject() || propertyType.isLinkingObject() -> {
                        getBacklinksTargetPropertyType(declaration)?.let { targetPropertyType ->
                            val sourceType: IrSimpleType = irClass.defaultType

                            targetPropertyType as IrAbstractSimpleType

                            // Validates that backlinks points to a valid type
                            val generic: IrAbstractSimpleType? = targetPropertyType.arguments
                                .getOrNull(0)?.let { argument: IrTypeArgument ->
                                    argument as IrAbstractSimpleType
                                }

                            val isValidTargetType = targetPropertyType.hasSameClassId(sourceType)
                            val isValidCollectionType = targetPropertyType.isRealmList() ||
                                targetPropertyType.isRealmSet() ||
                                targetPropertyType.isRealmDictionary()
                            val isValidGenericType = isValidCollectionType &&
                                generic!!.type.hasSameClassId(sourceType)

                            if (!(isValidTargetType || isValidGenericType)) {
                                val targetPropertyName = getLinkingObjectPropertyName(declaration.backingField!!)
                                logError(
                                    "Error in backlinks field '${declaration.name}' - target property '$targetPropertyName' does not reference '${sourceType.toIrBasedKotlinType().getKotlinTypeFqNameCompat(true)}'.",
                                    declaration.locationOf()
                                )
                            }

                            fields[name] = SchemaProperty(
                                propertyType = PropertyType.RLM_PROPERTY_TYPE_LINKING_OBJECTS,
                                declaration = declaration,
                                computedType = propertyTypeRaw,
                                collectionType = CollectionType.LIST,
                                coreGenericTypes = listOf(
                                    CoreType(
                                        propertyType = PropertyType.RLM_PROPERTY_TYPE_OBJECT,
                                        nullable = false
                                    )
                                )
                            )
                        }
                    }
                    propertyType.isRealmInstant() -> {
                        logDebug("RealmInstant property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_TIMESTAMP,
                            declaration = declaration,
                            computedType = propertyTypeRaw,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            type = propertyType,
                            getFunction = getInstant,
                            fromRealmValue = null,
                            toPublic = typeAdapterMethodReferences?.toPublic,
                            setFunction = setValue,
                            fromPublic = typeAdapterMethodReferences?.fromPublic,
                            toRealmValue = null
                        )
                    }
                    propertyType.isObjectId() -> {
                        logDebug("ObjectId property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_OBJECT_ID,
                            declaration = declaration,
                            computedType = propertyTypeRaw,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            type = propertyType,
                            getFunction = getObjectId,
                            fromRealmValue = null,
                            toPublic = typeAdapterMethodReferences?.toPublic,
                            setFunction = setValue,
                            fromPublic = typeAdapterMethodReferences?.fromPublic,
                            toRealmValue = null
                        )
                    }
                    propertyType.isRealmObjectId() -> {
                        logDebug("io.realm.kotlin.types.ObjectId property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        var schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_OBJECT_ID,
                            declaration = declaration,
                            computedType = propertyTypeRaw,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            type = propertyType,
                            getFunction = getObjectId,
                            fromRealmValue = objectIdToRealmObjectId,
                            toPublic = typeAdapterMethodReferences?.toPublic,
                            setFunction = setValue,
                            fromPublic = typeAdapterMethodReferences?.fromPublic,
                            toRealmValue = null
                        )
                    }
                    propertyType.isRealmUUID() -> {
                        logDebug("RealmUUID property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_UUID,
                            declaration = declaration,
                            computedType = propertyTypeRaw,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            type = propertyType,
                            getFunction = getUUID,
                            fromRealmValue = null,
                            toPublic = typeAdapterMethodReferences?.toPublic,
                            setFunction = setValue,
                            fromPublic = typeAdapterMethodReferences?.fromPublic,
                            toRealmValue = null,
                        )
                    }
                    propertyType.isRealmList() -> {
                        logDebug("RealmList property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        processCollectionField(CollectionType.LIST, fields, name, declaration, propertyTypeRaw, typeAdapterMethodReferences)
                    }
                    propertyType.isRealmSet() -> {
                        logDebug("RealmSet property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        processCollectionField(CollectionType.SET, fields, name, declaration, propertyTypeRaw, typeAdapterMethodReferences)
                    }
                    propertyType.isRealmDictionary() -> {
                        logDebug("RealmDictionary property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        processCollectionField(CollectionType.DICTIONARY, fields, name, declaration, propertyTypeRaw, typeAdapterMethodReferences)
                    }
                    propertyType.isSubtypeOfClass(embeddedRealmObjectInterface) -> {
                        logDebug("Object property named ${declaration.name} is embedded and ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_OBJECT,
                            declaration = declaration,
                            computedType = propertyTypeRaw,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            type = propertyType,
                            getFunction = getObject,
                            fromRealmValue = null,
                            toPublic = typeAdapterMethodReferences?.toPublic,
                            setFunction = setEmbeddedRealmObject,
                            fromPublic = typeAdapterMethodReferences?.fromPublic,
                            toRealmValue = null
                        )
                    }
                    asymmetricRealmObjectInterface != null && propertyType.isSubtypeOfClass(asymmetricRealmObjectInterface.symbol) -> {
                        // Asymmetric objects must be top-level objects, so any link to one
                        // should be illegal. This will be detected later when creating the
                        // schema methods. So for now, just add the field to the list of schema
                        // properties, but do not modify the accessor.
                        logDebug("Object property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_OBJECT,
                            declaration = declaration,
                            computedType = propertyTypeRaw,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                    }
                    propertyType.isSubtypeOfClass(realmObjectInterface) -> {
                        logDebug("Object property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_OBJECT,
                            declaration = declaration,
                            computedType = propertyTypeRaw,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        // Current getObject/setObject has it's own public->storagetype->realmvalue
                        // conversion so bypass any converters in accessors
                        modifyAccessor(
                            property = schemaProperty,
                            type = propertyType,
                            getFunction = getObject,
                            fromRealmValue = null,
                            toPublic = typeAdapterMethodReferences?.toPublic,
                            setFunction = setObject,
                            fromPublic = typeAdapterMethodReferences?.fromPublic,
                            toRealmValue = null
                        )
                    }
                    else -> {
                        logError("Realm does not support persisting properties of this type. Mark the field with `@Ignore` to suppress this error.", declaration.locationOf())
                    }
                }

                return super.visitProperty(declaration)
            }
        })
    }

    @Suppress("LongParameterList", "LongMethod", "ComplexMethod")
    private fun processCollectionField(
        collectionType: CollectionType,
        fields: MutableMap<String, SchemaProperty>,
        name: String,
        declaration: IrProperty,
        propertyTypeRaw: IrType,
        typeAdapterMethodReferences: TypeAdapterMethodReferences?,
    ) {
        val collectionTypeArgument = (propertyTypeRaw as IrSimpleTypeImpl).arguments[0]

        if (collectionTypeArgument is IrStarProjection) {
            logError(
                "Error in field ${declaration.name} - ${collectionType.description} cannot use a '*' projection.",
                declaration.locationOf()
            )
            return
        }

        var collectionIrType = collectionTypeArgument.typeOrNull!!

        // if not null the type would need to be adapted
        val typeAdapterAnnotation = collectionIrType
            .annotations
            .findAnnotation(TYPE_ADAPTER_ANNOTATION.asSingleFqName())

        var adapterClassReference: IrClassReference? = null
        var collectionAdapterExpression: (IrBuilderWithScope.(IrGetValue) -> IrExpression)? = null
        var collectionStoreType: IrType? = null

        typeAdapterAnnotation?.let {
            val typeAdapterInfo = it.getTypeAdapterInfo()
            val (classReference, realmType, userType) = typeAdapterInfo

            if (collectionIrType.classId != userType.classId) {
                // TODO improve messaging
                logError("Not matching types ${collectionIrType.classFqName} ${userType.classFqName}")
            }

            // Replace the property type with the one from the type adapter
            collectionIrType = realmType
            adapterClassReference = classReference
            val adapterClass: IrClass = classReference.classType.getClass()!!

            collectionAdapterExpression = { objectReference ->
                // retrieve the actual type adapter
                when (adapterClass.kind) {
                    ClassKind.CLASS -> {
                        irCall(
                            callee = getTypeAdapter,
                            origin = IrStatementOrigin.INVOKE
                        ).apply {
                            // pass obj reference
                            putValueArgument(0, objectReference)
                            // pass class reference
                            putValueArgument(1, adapterClassReference)
                        }
                    }
                    ClassKind.OBJECT -> {
                        irGetObject(adapterClass.symbol)
                    }
                    else -> throw IllegalStateException("Unsupported type")
                }
            }
        }

        val coreGenericTypes = getCollectionGenericCoreType(
            collectionType = collectionType,
            declaration = declaration,
            descriptorType = propertyTypeRaw,
            collectionGenericType = collectionIrType,
        )

        // Only process field if we got valid generics
        if (coreGenericTypes != null) {
            val genericPropertyType = getPropertyTypeFromKotlinType(collectionIrType.makeNotNull())

            // Only process
            if (genericPropertyType != null) {
                val schemaProperty = SchemaProperty(
                    propertyType = genericPropertyType,
                    declaration = declaration,
                    computedType = propertyTypeRaw,
                    collectionType = collectionType,
                    coreGenericTypes = listOf(coreGenericTypes),
                )
                fields[name] = schemaProperty
                // TODO OPTIMIZE consider synthetic property generation for lists to cache
                //  reference instead of emitting a new list every time - also for links
                //  see e.g.
                //  if (isManaged()) {
                //      if (io_realm_kotlin_synthetic$myList == null) {
                //          io_realm_kotlin_synthetic$myList = RealmObjectHelper.getList(this, "myList")
                //      }
                //      return io_realm_kotlin_synthetic$myList
                //  } else {
                //      return backing_field
                //  }

                // getCollection/setCollection gets/sets raw collections so it bypasses any converters in accessors
                modifyAccessor(
                    property = schemaProperty,
                    type = propertyTypeRaw,
                    getFunction = when (collectionType) {
                        CollectionType.LIST -> getList
                        CollectionType.SET -> getSet
                        CollectionType.DICTIONARY -> getDictionary
                        else -> throw UnsupportedOperationException("Only collections or dictionaries are supposed to modify the getter for '$name'")
                    },
                    fromRealmValue = null,
                    toPublic = typeAdapterMethodReferences?.toPublic,
                    setFunction = when (collectionType) {
                        CollectionType.LIST -> setList
                        CollectionType.SET -> setSet
                        CollectionType.DICTIONARY -> setDictionary
                        else -> throw UnsupportedOperationException("Only collections or dictionaries are supposed to modify the setter for '$name'")
                    },
                    fromPublic = typeAdapterMethodReferences?.fromPublic,
                    toRealmValue = null,
                    collectionType = collectionType,
                    collectionStoreType = collectionIrType,
                    collectionAdapterValue = collectionAdapterExpression,
                )
            }
        }
    }

    @Suppress("LongParameterList", "LongMethod", "ComplexMethod", "MagicNumber")
    private fun modifyAccessor(
        property: SchemaProperty,
        type: IrType,
        getFunction: IrSimpleFunction,
        fromRealmValue: IrSimpleFunction? = null,
        toPublic: (IrBuilderWithScope.(IrGetValue, IrFunctionAccessExpression) -> IrDeclarationReference)? = null,
        setFunction: IrSimpleFunction? = null,
        fromPublic: (IrBuilderWithScope.(IrGetValue, IrGetValue) -> IrDeclarationReference)? = null,
        toRealmValue: IrSimpleFunction? = null,
        collectionType: CollectionType = CollectionType.NONE,
        collectionStoreType: IrType? = null,
        collectionAdapterValue: (IrBuilderWithScope.(IrGetValue) -> IrExpression)? = null,
    ) {
        // TODO check this backing field if required
        val backingField = property.declaration.backingField!!
        val type: IrType? = when (collectionType) {
            CollectionType.NONE -> type
            CollectionType.LIST,
            CollectionType.SET,
            CollectionType.DICTIONARY -> getCollectionElementType(type)
        }
        val getter = property.declaration.getter
        val setter = property.declaration.setter
        getter?.apply {
            /**
             * Transform the getter to whether access the managed object or the backing field
             * ```
             * get() {
             *      return this.`io_realm_kotlin_objectReference`?.let { it ->
             *         toPublic(fromRealmValue(it.getValue("propertyName"))
             *      } ?: backingField
             * }
             * ```
             */

            // TODO optimize: we can simplify the code paths in RealmObjectHelper for all
            //  getters if we wrap the calls using the 'getterScope {...}'  function and calling
            //  the converter helper functions for each supported data type. We should
            //  investigate how to use 'IrFunctionExpressionImpl' since it appears to be the
            //  way to go. Until then we can achieve high performance by having one accessor
            //  call per supported storage type.

            origin = IrDeclarationOrigin.DEFINED

            body = IrBlockBuilder(
                pluginContext,
                Scope(getter.symbol),
                startOffset = body!!.startOffset,
                endOffset = body!!.endOffset,
            ).irBlockBody {
                val receiver: IrValueParameter = getter.dispatchReceiverParameter!!

                +irReturn(
                    irLetS(
                        value = irCall(
                            objectReferenceProperty.getter!!,
                            origin = IrStatementOrigin.GET_PROPERTY
                        ).also {
                            it.dispatchReceiver = irGet(receiver)
                        },
                        nameHint = "objectReference",
                        irType = objectReferenceType,
                    ) { valueSymbol ->
                        val managedObjectGetValueCall: IrCall = irCall(
                            callee = getFunction,
                            origin = IrStatementOrigin.GET_PROPERTY
                        ).also {
                            it.dispatchReceiver = irGetObject(realmObjectHelper.symbol)
                        }.apply {
                            if (typeArgumentsCount > 0) {
                                putTypeArgument(0, type)
                            }
                            if (typeArgumentsCount > 1) {
                                putTypeArgument(1, collectionStoreType)
                            }
                            val objectReference = irGet(objectReferenceType, valueSymbol)
                            putValueArgument(0, objectReference)
                            putValueArgument(1, irString(property.persistedName))
                            collectionAdapterValue?.let {
                                putValueArgument(2, it(objectReference))
                            }
                        }
                        val storageValue = fromRealmValue?.let {
                            irCall(callee = it).apply {
                                if (typeArgumentsCount > 0) {
                                    putTypeArgument(0, type)
                                }
                                putValueArgument(0, managedObjectGetValueCall)
                            }
                        } ?: managedObjectGetValueCall
                        val publicValue = toPublic?.invoke(
                            this,
                            irGet(objectReferenceType, valueSymbol),
                            storageValue
                        ) ?: storageValue
                        irIfNull(
                            type = getter.returnType,
                            subject = irGet(objectReferenceType, valueSymbol),
                            // Unmanaged object, return backing field
                            thenPart = irGetFieldWrapper(irGet(receiver), backingField),
                            // Managed object, return realm value
                            elsePart = publicValue
                        )
                    }
                )
            }
        }

        // Setter function is null when working with immutable properties
        if (setFunction != null) {
            setter?.apply {
                /**
                 * Transform the setter to whether access the managed object or the backing field
                 * ```
                 * set(value) {
                 *      this.`io_realm_kotlin_objectReference`?.let {
                 *          it.setValue("propertyName", toRealmValue(fromPublic(value)))
                 *      } ?: run { backingField = value }
                 * }
                 * ```
                 */

                // TODO optimize: similarly to what is written above about the getters, we could do
                //  something similar for the setters and 'inputScope/inputScopeTracked {...}'.

                origin = IrDeclarationOrigin.DEFINED

                body = IrBlockBuilder(
                    pluginContext,
                    Scope(setter.symbol),
                    startOffset = body!!.startOffset,
                    endOffset = body!!.endOffset,
                ).irBlockBody {
                    val receiver: IrValueParameter = setter.dispatchReceiverParameter!!

                    +irLetS(
                        value = irCall(
                            objectReferenceProperty.getter!!,
                            origin = IrStatementOrigin.GET_PROPERTY
                        ).also {
                            it.dispatchReceiver = irGet(receiver)
                        },
                        nameHint = "objectReference",
                        irType = objectReferenceType,
                    ) { valueSymbol ->
                        val storageValue =
                            fromPublic?.invoke(
                                this,
                                irGet(objectReferenceType, valueSymbol),
                                irGet(setter.valueParameters.first())
                            ) ?: irGet(setter.valueParameters.first())
                        val realmValue: IrDeclarationReference = toRealmValue?.let {
                            irCall(callee = it).apply {
                                if (typeArgumentsCount > 0) {
                                    putTypeArgument(0, type)
                                }
                                putValueArgument(0, storageValue)
                            }
                        } ?: storageValue
                        val cinteropCall = irCall(
                            callee = setFunction,
                            origin = IrStatementOrigin.GET_PROPERTY
                        ).apply {
                            dispatchReceiver = irGetObject(realmObjectHelper.symbol)
                            if (typeArgumentsCount > 0) {
                                putTypeArgument(0, type)
                            }
                            if (typeArgumentsCount > 1) {
                                putTypeArgument(1, collectionStoreType)
                            }
                            val objectReference = irGet(objectReferenceType, valueSymbol)
                            putValueArgument(0, objectReference)
                            putValueArgument(1, irString(property.persistedName))
                            putValueArgument(2, realmValue)
                            collectionAdapterValue?.let {
                                putValueArgument(3, it(objectReference))
                            }
                        }

                        irIfNull(
                            type = pluginContext.irBuiltIns.unitType,
                            subject = irGet(objectReferenceType, valueSymbol),
                            // Unmanaged object, set the backing field
                            thenPart = IrSetFieldImpl(
                                startOffset = startOffset,
                                endOffset = endOffset,
                                symbol = backingField.symbol,
                                receiver = irGet(receiver),
                                value = irGet(setter.valueParameters.first()),
                                type = context.irBuiltIns.unitType
                            ),
                            // Managed object, return realm value
                            elsePart = cinteropCall
                        )
                    }
                }
            }
        }
    }

    @Suppress("ReturnCount", "LongMethod")
    private fun getCollectionGenericCoreType(
        collectionType: CollectionType,
        declaration: IrProperty,
        descriptorType: IrType,
        collectionGenericType: IrType,
    ): CoreType? {
        // Check first if the generic is a subclass of RealmObject
        val isRealmObject = collectionGenericType.isSubtypeOfClass(realmObjectInterface)
        val isEmbedded = collectionGenericType.isSubtypeOfClass(embeddedRealmObjectInterface)

        if (isRealmObject || isEmbedded) {
            // No embedded objects for sets
            if (collectionType == CollectionType.SET && isEmbedded) {
                logError(
                    "Error in field ${declaration.name} - ${collectionType.description} does not support embedded realm objects element types.",
                    declaration.locationOf()
                )
                return null
            }

            val isNullable = collectionGenericType.isNullable()

            // Lists of objects/embedded objects and sets of object may NOT contain null values, but dictionaries may
            when (collectionType) {
                CollectionType.SET,
                CollectionType.LIST -> {
                    if (isNullable) {
                        logError(
                            "Error in field ${declaration.name} - ${collectionType.description} does not support nullable realm objects element types.",
                            declaration.locationOf()
                        )
                        return null
                    }
                }
                CollectionType.DICTIONARY -> {
                    if (!isNullable) {
                        logError(
                            "Error in field ${declaration.name} - RealmDictionary does not support non-nullable realm objects element types.",
                            declaration.locationOf()
                        )
                        return null
                    }
                }
                else -> throw IllegalArgumentException("Only collections can be processed here.")
            }

            return CoreType(
                propertyType = PropertyType.RLM_PROPERTY_TYPE_OBJECT,
                nullable = isNullable
            )
        }

        // If not a RealmObject, check whether the collection itself is nullable - if so, throw error
        if (descriptorType.isNullable()) {
            logError(
                "Error in field ${declaration.name} - a ${collectionType.description} field cannot be marked as nullable. ${descriptorType.classFqName}",
                declaration.locationOf()
            )
            return null
        }

        // Otherwise just return the matching core type present in the declaration
        val genericPropertyType: PropertyType? = getPropertyTypeFromKotlinType(collectionGenericType.makeNotNull())
        return if (genericPropertyType == null) {
            logError(
                "Unsupported type for ${collectionType.description}: '${collectionGenericType.classFqName}'",
                declaration.locationOf()
            )
            null
        } else if (genericPropertyType == PropertyType.RLM_PROPERTY_TYPE_MIXED && !collectionGenericType.isNullable()) {
            logError(
                "Unsupported type for ${collectionType.description}: Only '${collectionType.description}<RealmAny?>' is supported.",
                declaration.locationOf()
            )
            return null
        } else {
            CoreType(
                propertyType = genericPropertyType,
                nullable = collectionGenericType.isNullable()
            )
        }
    }

    // TODO do the lookup only once
    @Suppress("ComplexMethod")
    private fun getPropertyTypeFromKotlinType(type: IrType): PropertyType? =
        when {
            type.isByte() -> PropertyType.RLM_PROPERTY_TYPE_INT
            type.isChar() -> PropertyType.RLM_PROPERTY_TYPE_INT
            type.isShort() -> PropertyType.RLM_PROPERTY_TYPE_INT
            type.isInt() -> PropertyType.RLM_PROPERTY_TYPE_INT
            type.isLong() -> PropertyType.RLM_PROPERTY_TYPE_INT
            type.isBoolean() -> PropertyType.RLM_PROPERTY_TYPE_BOOL
            type.isFloat() -> PropertyType.RLM_PROPERTY_TYPE_FLOAT
            type.isDouble() -> PropertyType.RLM_PROPERTY_TYPE_DOUBLE
            type.isString() -> PropertyType.RLM_PROPERTY_TYPE_STRING
            type.isRealmInstant() -> PropertyType.RLM_PROPERTY_TYPE_TIMESTAMP
            type.isObjectId() -> PropertyType.RLM_PROPERTY_TYPE_OBJECT_ID
            type.isRealmObjectId() -> PropertyType.RLM_PROPERTY_TYPE_OBJECT_ID
            type.isDecimal128() -> PropertyType.RLM_PROPERTY_TYPE_DECIMAL128
            type.isRealmUUID() -> PropertyType.RLM_PROPERTY_TYPE_UUID
            type.isByteArray() -> PropertyType.RLM_PROPERTY_TYPE_BINARY
            type.isRealmAny() -> PropertyType.RLM_PROPERTY_TYPE_MIXED
            else ->
                if (
                    type.isSubtypeOfClass(realmObjectInterface) ||
                    type.isSubtypeOfClass(embeddedRealmObjectInterface)
                ) {
                    PropertyType.RLM_PROPERTY_TYPE_OBJECT
                } else {
                    null
                }
        }
}
