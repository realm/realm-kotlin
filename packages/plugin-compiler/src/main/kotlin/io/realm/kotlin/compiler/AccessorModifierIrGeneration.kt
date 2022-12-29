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

import io.realm.kotlin.compiler.FqNames.EMBEDDED_OBJECT_INTERFACE
import io.realm.kotlin.compiler.FqNames.IGNORE_ANNOTATION
import io.realm.kotlin.compiler.FqNames.KBSON_OBJECT_ID
import io.realm.kotlin.compiler.FqNames.REALM_BACKLINKS
import io.realm.kotlin.compiler.FqNames.REALM_EMBEDDED_BACKLINKS
import io.realm.kotlin.compiler.FqNames.REALM_INSTANT
import io.realm.kotlin.compiler.FqNames.REALM_LIST
import io.realm.kotlin.compiler.FqNames.REALM_MUTABLE_INTEGER
import io.realm.kotlin.compiler.FqNames.REALM_OBJECT_HELPER
import io.realm.kotlin.compiler.FqNames.REALM_OBJECT_ID
import io.realm.kotlin.compiler.FqNames.REALM_OBJECT_INTERFACE
import io.realm.kotlin.compiler.FqNames.REALM_SET
import io.realm.kotlin.compiler.FqNames.REALM_UUID
import io.realm.kotlin.compiler.FqNames.TRANSIENT_ANNOTATION
import io.realm.kotlin.compiler.Names.OBJECT_REFERENCE
import io.realm.kotlin.compiler.Names.REALM_ACCESSOR_HELPER_GET_BOOLEAN
import io.realm.kotlin.compiler.Names.REALM_ACCESSOR_HELPER_GET_BYTE_ARRAY
import io.realm.kotlin.compiler.Names.REALM_ACCESSOR_HELPER_GET_DOUBLE
import io.realm.kotlin.compiler.Names.REALM_ACCESSOR_HELPER_GET_FLOAT
import io.realm.kotlin.compiler.Names.REALM_ACCESSOR_HELPER_GET_INSTANT
import io.realm.kotlin.compiler.Names.REALM_ACCESSOR_HELPER_GET_LONG
import io.realm.kotlin.compiler.Names.REALM_ACCESSOR_HELPER_GET_OBJECT_ID
import io.realm.kotlin.compiler.Names.REALM_ACCESSOR_HELPER_GET_STRING
import io.realm.kotlin.compiler.Names.REALM_ACCESSOR_HELPER_GET_UUID
import io.realm.kotlin.compiler.Names.REALM_ACCESSOR_HELPER_SET_VALUE
import io.realm.kotlin.compiler.Names.REALM_OBJECT_HELPER_GET_LIST
import io.realm.kotlin.compiler.Names.REALM_OBJECT_HELPER_GET_MUTABLE_INT
import io.realm.kotlin.compiler.Names.REALM_OBJECT_HELPER_GET_OBJECT
import io.realm.kotlin.compiler.Names.REALM_OBJECT_HELPER_GET_SET
import io.realm.kotlin.compiler.Names.REALM_OBJECT_HELPER_SET_EMBEDDED_REALM_OBJECT
import io.realm.kotlin.compiler.Names.REALM_OBJECT_HELPER_SET_LIST
import io.realm.kotlin.compiler.Names.REALM_OBJECT_HELPER_SET_OBJECT
import io.realm.kotlin.compiler.Names.REALM_OBJECT_HELPER_SET_SET
import io.realm.kotlin.compiler.Names.REALM_SYNTHETIC_PROPERTY_PREFIX
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.builders.IrBlockBuilder
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
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
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrDeclarationReference
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrSetFieldImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.classifierOrFail
import org.jetbrains.kotlin.ir.types.impl.IrAbstractSimpleType
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
import org.jetbrains.kotlin.ir.types.toKotlinType
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.classId
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.StarProjectionImpl
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlin.types.typeUtil.supertypes
import kotlin.collections.set

/**
 * Modifies the IR tree to transform getter/setter to call the C-Interop layer to retrieve read the managed values from the Realm
 * It also collect the schema information while processing the class properties.
 */
@OptIn(ObsoleteDescriptorBasedAPI::class)
class AccessorModifierIrGeneration(private val pluginContext: IrPluginContext) {

    private val realmObjectHelper: IrClass = pluginContext.lookupClassOrThrow(REALM_OBJECT_HELPER)
    private val realmListClass: IrClass = pluginContext.lookupClassOrThrow(REALM_LIST)
    private val realmSetClass: IrClass = pluginContext.lookupClassOrThrow(REALM_SET)
    private val realmInstantClass: IrClass = pluginContext.lookupClassOrThrow(REALM_INSTANT)
    private val realmBacklinksClass: IrClass = pluginContext.lookupClassOrThrow(REALM_BACKLINKS)
    private val realmEmbeddedBacklinksClass: IrClass = pluginContext.lookupClassOrThrow(REALM_EMBEDDED_BACKLINKS)
    private val realmObjectInterface = pluginContext.lookupClassOrThrow(REALM_OBJECT_INTERFACE).symbol
    private val embeddedRealmObjectInterface = pluginContext.lookupClassOrThrow(EMBEDDED_OBJECT_INTERFACE).symbol
    private val objectIdClass: IrClass = pluginContext.lookupClassOrThrow(KBSON_OBJECT_ID)
    private val realmObjectIdClass: IrClass = pluginContext.lookupClassOrThrow(REALM_OBJECT_ID)
    private val realmUUIDClass: IrClass = pluginContext.lookupClassOrThrow(REALM_UUID)
    private val mutableRealmIntegerClass: IrClass = pluginContext.lookupClassOrThrow(REALM_MUTABLE_INTEGER)

    // Primitive (Core) type getters
    private val getString: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_ACCESSOR_HELPER_GET_STRING)
    private val getLong: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_ACCESSOR_HELPER_GET_LONG)
    private val getBoolean: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_ACCESSOR_HELPER_GET_BOOLEAN)
    private val getFloat: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_ACCESSOR_HELPER_GET_FLOAT)
    private val getDouble: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_ACCESSOR_HELPER_GET_DOUBLE)
    private val getInstant: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_ACCESSOR_HELPER_GET_INSTANT)
    private val getObjectId: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_ACCESSOR_HELPER_GET_OBJECT_ID)
    private val getUUID: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_ACCESSOR_HELPER_GET_UUID)
    private val getByteArray: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_ACCESSOR_HELPER_GET_BYTE_ARRAY)
    private val getMutableInt: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_GET_MUTABLE_INT)
    private val getObject: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_GET_OBJECT)

    // Primitive (Core) type setters
    private val setValue: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_ACCESSOR_HELPER_SET_VALUE)
    private val setObject: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_SET_OBJECT)
    private val setEmbeddedRealmObject: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_SET_EMBEDDED_REALM_OBJECT)

    // Getters and setters for collections
    private val getList: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_GET_LIST)
    private val setList: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_SET_LIST)
    private val getSet: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_GET_SET)
    private val setSet: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_SET_SET)

    // Top level SDK->Core converters
    private val byteToLong: IrSimpleFunction =
        pluginContext.referenceFunctions(FqName("io.realm.kotlin.internal.byteToLong")).first().owner
    private val charToLong: IrSimpleFunction =
        pluginContext.referenceFunctions(FqName("io.realm.kotlin.internal.charToLong")).first().owner
    private val shortToLong: IrSimpleFunction =
        pluginContext.referenceFunctions(FqName("io.realm.kotlin.internal.shortToLong")).first().owner
    private val intToLong: IrSimpleFunction =
        pluginContext.referenceFunctions(FqName("io.realm.kotlin.internal.intToLong")).first().owner

    // Top level Core->SDK converters
    private val longToByte: IrSimpleFunction =
        pluginContext.referenceFunctions(FqName("io.realm.kotlin.internal.longToByte")).first().owner
    private val longToChar: IrSimpleFunction =
        pluginContext.referenceFunctions(FqName("io.realm.kotlin.internal.longToChar")).first().owner
    private val longToShort: IrSimpleFunction =
        pluginContext.referenceFunctions(FqName("io.realm.kotlin.internal.longToShort")).first().owner
    private val longToInt: IrSimpleFunction =
        pluginContext.referenceFunctions(FqName("io.realm.kotlin.internal.longToInt")).first().owner
    private val objectIdToRealmObjectId: IrSimpleFunction =
        pluginContext.referenceFunctions(FqName("io.realm.kotlin.internal.objectIdToRealmObjectId")).first().owner

    private lateinit var objectReferenceProperty: IrProperty
    private lateinit var objectReferenceType: IrType

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
                if (declaration.backingField == null ||
                    name.startsWith(REALM_SYNTHETIC_PROPERTY_PREFIX) ||
                    declaration.parentAsClass != irClass
                ) {
                    return declaration
                }

                val propertyTypeRaw = declaration.backingField!!.type
                val propertyType = propertyTypeRaw.makeNotNull()
                val nullable = propertyTypeRaw.isNullable()
                val excludeProperty =
                    declaration.backingField!!.hasAnnotation(IGNORE_ANNOTATION) ||
                        declaration.backingField!!.hasAnnotation(TRANSIENT_ANNOTATION)

                when {
                    excludeProperty -> {
                        logDebug("Property named ${declaration.name} ignored")
                    }
                    propertyType.isMutableRealmInteger() -> {
                        logDebug("MutableRealmInt property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_INT,
                            declaration = declaration,
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
                            getFunction = getMutableInt,
                            fromRealmValue = null,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = null,
                            toRealmValue = null
                        )
                    }
                    propertyType.isByteArray() -> {
                        logDebug("ByteArray property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_BINARY,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getByteArray,
                            fromRealmValue = null,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = null,
                            toRealmValue = null
                        )
                    }
                    propertyType.isString() -> {
                        logDebug("String property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_STRING,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getString,
                            fromRealmValue = null,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = null,
                            toRealmValue = null
                        )
                    }
                    propertyType.isByte() -> {
                        logDebug("Byte property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_INT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getLong,
                            fromRealmValue = longToByte,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = byteToLong,
                            toRealmValue = null
                        )
                    }
                    propertyType.isChar() -> {
                        logDebug("Char property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_INT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getLong,
                            fromRealmValue = longToChar,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = charToLong,
                            toRealmValue = null
                        )
                    }
                    propertyType.isShort() -> {
                        logDebug("Short property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_INT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getLong,
                            fromRealmValue = longToShort,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = shortToLong,
                            toRealmValue = null
                        )
                    }
                    propertyType.isInt() -> {
                        logDebug("Int property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_INT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getLong,
                            fromRealmValue = longToInt,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = intToLong,
                            toRealmValue = null
                        )
                    }
                    propertyType.isLong() -> {
                        logDebug("Long property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_INT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getLong,
                            fromRealmValue = null,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = null,
                            toRealmValue = null
                        )
                    }
                    propertyType.isBoolean() -> {
                        logDebug("Boolean property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_BOOL,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getBoolean,
                            fromRealmValue = null,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = null,
                            toRealmValue = null
                        )
                    }
                    propertyType.isFloat() -> {
                        logDebug("Float property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_FLOAT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getFloat,
                            fromRealmValue = null,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = null,
                            toRealmValue = null
                        )
                    }
                    propertyType.isDouble() -> {
                        logDebug("Double property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_DOUBLE,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getDouble,
                            fromRealmValue = null,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = null,
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
                            val isValidGenericType = (
                                targetPropertyType.isRealmList() || targetPropertyType.isRealmSet()
                                ) && generic!!.type.hasSameClassId(sourceType)

                            if (!(isValidTargetType || isValidGenericType)) {
                                val targetPropertyName = getLinkingObjectPropertyName(declaration.backingField!!)
                                logError(
                                    "Error in backlinks field '${declaration.name}' - target property '$targetPropertyName' does not reference '${sourceType.toKotlinType()}'.",
                                    declaration.locationOf()
                                )
                            }

                            fields[name] = SchemaProperty(
                                propertyType = PropertyType.RLM_PROPERTY_TYPE_LINKING_OBJECTS,
                                declaration = declaration,
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
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getInstant,
                            fromRealmValue = null,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = null,
                            toRealmValue = null
                        )
                    }
                    propertyType.isObjectId() -> {
                        logDebug("ObjectId property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_OBJECT_ID,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getObjectId,
                            fromRealmValue = null,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = null,
                            toRealmValue = null
                        )
                    }
                    propertyType.isRealmObjectId() -> {
                        logDebug("io.realm.kotlin.types.ObjectId property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        var schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_OBJECT_ID,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getObjectId,
                            fromRealmValue = objectIdToRealmObjectId,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = null,
                            toRealmValue = null
                        )
                    }
                    propertyType.isRealmUUID() -> {
                        logDebug("RealmUUID property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_UUID,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getUUID,
                            fromRealmValue = null,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = null,
                            toRealmValue = null,
                        )
                    }
                    propertyType.isRealmList() -> {
                        logDebug("RealmList property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        processCollectionField(CollectionType.LIST, fields, name, declaration)
                    }
                    propertyType.isRealmSet() -> {
                        logDebug("RealmSet property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        processCollectionField(CollectionType.SET, fields, name, declaration)
                    }
                    propertyType.isSubtypeOfClass(embeddedRealmObjectInterface) -> {
                        logDebug("Object property named ${declaration.name} is embedded and ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_OBJECT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            schemaProperty,
                            getFunction = getObject,
                            fromRealmValue = null,
                            toPublic = null,
                            setFunction = setEmbeddedRealmObject,
                            fromPublic = null,
                            toRealmValue = null
                        )
                    }
                    propertyType.isSubtypeOfClass(realmObjectInterface) -> {
                        logDebug("Object property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_OBJECT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        // Current getObject/setObject has it's own public->storagetype->realmvalue
                        // conversion so bypass any converters in accessors
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getObject,
                            fromRealmValue = null,
                            toPublic = null,
                            setFunction = setObject,
                            fromPublic = null,
                            toRealmValue = null
                        )
                    }
                    else -> {
                        logDebug("Type not processed: ${declaration.dump()}")
                    }
                }

                return super.visitProperty(declaration)
            }
        })
    }

    private fun processCollectionField(
        collectionType: CollectionType,
        fields: MutableMap<String, SchemaProperty>,
        name: String,
        declaration: IrProperty
    ) {
        val type = declaration.symbol.descriptor.type
        if (type.arguments[0] is StarProjectionImpl) {
            logError(
                "Error in field ${declaration.name} - ${collectionType.description}s cannot use a '*' projection.",
                declaration.locationOf()
            )
            return
        }
        val collectionGenericType = type.arguments[0].type
        val coreGenericTypes = getCollectionGenericCoreType(collectionType, declaration)

        // Only process field if we got valid generics
        if (coreGenericTypes != null) {
            val genericPropertyType = getPropertyTypeFromKotlinType(collectionGenericType)

            // Only process
            if (genericPropertyType != null) {
                val schemaProperty = SchemaProperty(
                    propertyType = genericPropertyType,
                    declaration = declaration,
                    collectionType = collectionType,
                    coreGenericTypes = listOf(coreGenericTypes)
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
                    getFunction = when (collectionType) {
                        CollectionType.LIST -> getList
                        CollectionType.SET -> getSet
                        CollectionType.DICTIONARY -> TODO("Dictionaries are not supported yet")
                        else -> throw UnsupportedOperationException("Only collections or dictionaries are supposed to modify the getter for '$name'")
                    },
                    fromRealmValue = null,
                    toPublic = null,
                    setFunction = when (collectionType) {
                        CollectionType.LIST -> setList
                        CollectionType.SET -> setSet
                        CollectionType.DICTIONARY -> TODO("Dictionaries are not supported yet")
                        else -> throw UnsupportedOperationException("Only collections or dictionaries are supposed to modify the setter for '$name'")
                    },
                    fromPublic = null,
                    toRealmValue = null,
                    collectionType = collectionType
                )
            }
        }
    }

    @Suppress("LongParameterList", "LongMethod", "ComplexMethod")
    private fun modifyAccessor(
        property: SchemaProperty,
        getFunction: IrSimpleFunction,
        fromRealmValue: IrSimpleFunction? = null,
        toPublic: IrSimpleFunction? = null,
        setFunction: IrSimpleFunction? = null,
        fromPublic: IrSimpleFunction? = null,
        toRealmValue: IrSimpleFunction? = null,
        collectionType: CollectionType = CollectionType.NONE
    ) {
        val backingField = property.declaration.backingField!!
        val type: IrType? = when (collectionType) {
            CollectionType.NONE -> backingField.type
            CollectionType.LIST,
            CollectionType.SET -> getCollectionElementType(backingField.type)
            else -> error("Collection type '$collectionType' not supported.")
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
                            putValueArgument(0, irGet(objectReferenceType, valueSymbol))
                            putValueArgument(1, irString(property.persistedName))
                        }
                        val storageValue = fromRealmValue?.let {
                            irCall(callee = it).apply {
                                if (typeArgumentsCount > 0) {
                                    putTypeArgument(0, type)
                                }
                                putValueArgument(0, managedObjectGetValueCall)
                            }
                        } ?: managedObjectGetValueCall
                        val publicValue = toPublic?.let {
                            irCall(callee = toPublic).apply {
                                putValueArgument(0, storageValue)
                            }
                        } ?: storageValue
                        irIfNull(
                            type = getter.returnType,
                            subject = irGet(objectReferenceType, valueSymbol),
                            // Unmanaged object, return backing field
                            thenPart = irGetField(irGet(receiver), backingField),
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
                        val storageValue: IrDeclarationReference = fromPublic?.let {
                            irCall(callee = it).apply {
                                putValueArgument(0, irGet(setter.valueParameters.first()))
                            }
                        } ?: irGet(setter.valueParameters.first())
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
                        ).also {
                            it.dispatchReceiver = irGetObject(realmObjectHelper.symbol)
                        }.apply {
                            if (typeArgumentsCount > 0) {
                                putTypeArgument(0, type)
                            }
                            putValueArgument(0, irGet(objectReferenceType, valueSymbol))
                            putValueArgument(1, irString(property.persistedName))
                            putValueArgument(2, realmValue)
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

    private fun IrType.isRealmList(): Boolean {
        val propertyClassId = this.classifierOrFail.descriptor.classId
        val realmListClassId = realmListClass.descriptor.classId
        return propertyClassId == realmListClassId
    }

    private fun IrType.isRealmSet(): Boolean {
        val propertyClassId = this.classifierOrFail.descriptor.classId
        val realmSetClassId = realmSetClass.descriptor.classId
        return propertyClassId == realmSetClassId
    }

    private fun IrType.isRealmInstant(): Boolean {
        val propertyClassId = this.classifierOrFail.descriptor.classId
        val realmInstantClassId = realmInstantClass.descriptor.classId
        return propertyClassId == realmInstantClassId
    }

    private fun IrType.isLinkingObject(): Boolean {
        val propertyClassId = this.classifierOrFail.descriptor.classId
        val realmBacklinksClassId: ClassId? = realmBacklinksClass.descriptor.classId
        return propertyClassId == realmBacklinksClassId
    }

    private fun IrType.isEmbeddedLinkingObject(): Boolean {
        val propertyClassId = this.classifierOrFail.descriptor.classId
        val realmEmbeddedBacklinksClassId: ClassId? = realmEmbeddedBacklinksClass.descriptor.classId
        return propertyClassId == realmEmbeddedBacklinksClassId
    }

    private fun IrType.isObjectId(): Boolean {
        val propertyClassId = this.classifierOrFail.descriptor.classId
        val objectIdClassId = objectIdClass.descriptor.classId
        return propertyClassId == objectIdClassId
    }

    private fun IrType.isRealmObjectId(): Boolean {
        val propertyClassId = this.classifierOrFail.descriptor.classId
        val objectIdClassId = realmObjectIdClass.descriptor.classId
        return propertyClassId == objectIdClassId
    }

    private fun IrType.hasSameClassId(other: IrType): Boolean {
        val classId = this.classifierOrFail.descriptor.classId
        val otherClassId = other.classifierOrFail.descriptor.classId
        return classId == otherClassId
    }

    private fun IrType.isRealmUUID(): Boolean {
        val propertyClassId = this.classifierOrFail.descriptor.classId
        val realmUUIDClassId = realmUUIDClass.descriptor.classId
        return propertyClassId == realmUUIDClassId
    }

    fun IrType.isMutableRealmInteger(): Boolean {
        val propertyClassId = this.classifierOrFail.descriptor.classId
        val mutableRealmIntegerClassId = mutableRealmIntegerClass.descriptor.classId
        return propertyClassId == mutableRealmIntegerClassId
    }

    @Suppress("ReturnCount")
    private fun getCollectionGenericCoreType(
        collectionType: CollectionType,
        declaration: IrProperty
    ): CoreType? {
        // Check first if the generic is a subclass of RealmObject
        val descriptorType = declaration.symbol.descriptor.type
        val collectionGenericType = descriptorType.arguments[0].type

        // No embedded objects for sets
        val supertypes = collectionGenericType.constructor.supertypes
        val isEmbedded = inheritsFromRealmObject(supertypes, RealmObjectType.EMBEDDED)
        if (collectionType == CollectionType.SET && isEmbedded) {
            logError(
                "Error in field ${declaration.name} - ${collectionType.description}s do not support embedded realm objects element types.",
                declaration.locationOf()
            )
            return null
        }

        if (inheritsFromRealmObject(supertypes)) {
            // Nullable objects are not supported
            if (collectionGenericType.isNullable()) {
                logError(
                    "Error in field ${declaration.name} - ${collectionType.description}s do not support nullable realm objects element types.",
                    declaration.locationOf()
                )
                return null
            }
            return CoreType(
                propertyType = PropertyType.RLM_PROPERTY_TYPE_OBJECT,
                nullable = false
            )
        }

        // If not a RealmObject, check whether the collection itself is nullable - if so, throw error
        if (descriptorType.isNullable()) {
            logError(
                "Error in field ${declaration.name} - a ${collectionType.description} field cannot be marked as nullable.",
                declaration.locationOf()
            )
            return null
        }

        // Otherwise just return the matching core type present in the declaration
        val genericPropertyType = getPropertyTypeFromKotlinType(collectionGenericType)
        return if (genericPropertyType != null) {
            CoreType(
                propertyType = genericPropertyType,
                nullable = collectionGenericType.isNullable()
            )
        } else {
            logError(
                "Unsupported type for ${collectionType.description}s: '$collectionGenericType'",
                declaration.locationOf()
            )
            null
        }
    }

    // TODO do the lookup only once
    @Suppress("ComplexMethod")
    private fun getPropertyTypeFromKotlinType(type: KotlinType): PropertyType? {
        return type.constructor.declarationDescriptor
            ?.name
            ?.let { identifier ->
                when (identifier.toString()) {
                    "Byte" -> PropertyType.RLM_PROPERTY_TYPE_INT
                    "Char" -> PropertyType.RLM_PROPERTY_TYPE_INT
                    "Short" -> PropertyType.RLM_PROPERTY_TYPE_INT
                    "Int" -> PropertyType.RLM_PROPERTY_TYPE_INT
                    "Long" -> PropertyType.RLM_PROPERTY_TYPE_INT
                    "Boolean" -> PropertyType.RLM_PROPERTY_TYPE_BOOL
                    "Float" -> PropertyType.RLM_PROPERTY_TYPE_FLOAT
                    "Double" -> PropertyType.RLM_PROPERTY_TYPE_DOUBLE
                    "String" -> PropertyType.RLM_PROPERTY_TYPE_STRING
                    "RealmInstant" -> PropertyType.RLM_PROPERTY_TYPE_TIMESTAMP
                    "ObjectId" -> PropertyType.RLM_PROPERTY_TYPE_OBJECT_ID
                    "BsonObjectId" -> PropertyType.RLM_PROPERTY_TYPE_OBJECT_ID
                    "RealmUUID" -> PropertyType.RLM_PROPERTY_TYPE_UUID
                    "ByteArray" -> PropertyType.RLM_PROPERTY_TYPE_BINARY
                    else ->
                        if (inheritsFromRealmObject(type.supertypes())) {
                            PropertyType.RLM_PROPERTY_TYPE_OBJECT
                        } else {
                            null
                        }
                }
            }
    }

    // Check if the class in question inherits from RealmObject, EmbeddedRealmObject or either
    private fun inheritsFromRealmObject(
        supertypes: Collection<KotlinType>,
        objectType: RealmObjectType = RealmObjectType.EITHER
    ): Boolean = supertypes.any {
        val objectFqNames = when (objectType) {
            RealmObjectType.OBJECT -> realmObjectInterfaceFqNames
            RealmObjectType.EMBEDDED -> realmEmbeddedObjectInterfaceFqNames
            RealmObjectType.EITHER -> realmObjectInterfaceFqNames + realmEmbeddedObjectInterfaceFqNames
        }
        it.constructor.declarationDescriptor?.fqNameSafe in objectFqNames
    }
}

private enum class RealmObjectType {
    OBJECT, EMBEDDED, EITHER
}
