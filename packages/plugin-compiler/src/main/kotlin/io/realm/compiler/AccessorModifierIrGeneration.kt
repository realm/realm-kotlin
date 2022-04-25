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

import io.realm.compiler.FqNames.REALM_INSTANT
import io.realm.compiler.FqNames.REALM_LIST
import io.realm.compiler.FqNames.REALM_MODEL_INTERFACE
import io.realm.compiler.FqNames.REALM_OBJECT_HELPER
import io.realm.compiler.Names.OBJECT_REFERENCE
import io.realm.compiler.Names.REALM_OBJECT_HELPER_GET_LIST
import io.realm.compiler.Names.REALM_OBJECT_HELPER_GET_OBJECT
import io.realm.compiler.Names.REALM_OBJECT_HELPER_GET_VALUE
import io.realm.compiler.Names.REALM_OBJECT_HELPER_SET_LIST
import io.realm.compiler.Names.REALM_OBJECT_HELPER_SET_OBJECT
import io.realm.compiler.Names.REALM_OBJECT_HELPER_SET_VALUE
import io.realm.compiler.Names.REALM_SYNTHETIC_PROPERTY_PREFIX
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
import org.jetbrains.kotlin.ir.types.classifierOrFail
import org.jetbrains.kotlin.ir.types.impl.IrTypeBase
import org.jetbrains.kotlin.ir.types.isBoolean
import org.jetbrains.kotlin.ir.types.isByte
import org.jetbrains.kotlin.ir.types.isChar
import org.jetbrains.kotlin.ir.types.isDouble
import org.jetbrains.kotlin.ir.types.isFloat
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.isLong
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.types.isPrimitiveType
import org.jetbrains.kotlin.ir.types.isShort
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
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
    private val realmInstantClass: IrClass = pluginContext.lookupClassOrThrow(REALM_INSTANT)

    private val getValue: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_GET_VALUE)
    private val setValue: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_SET_VALUE)
    private val getObject: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_GET_OBJECT)
    private val setObject: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_SET_OBJECT)
    private val getList: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_GET_LIST)
    private val setList: IrSimpleFunction =
        realmObjectHelper.lookupFunction(REALM_OBJECT_HELPER_SET_LIST)

    // Default conversion functions when there is not an explicit Converter in Converters.kt
    private val anyToRealmValue: IrSimpleFunction =
        pluginContext.referenceFunctions(FqName("io.realm.internal.anyToRealmValue")).first().owner
    private val realmValueToAny: IrSimpleFunction =
        pluginContext.referenceFunctions(FqName("io.realm.internal.realmValueToAny")).first().owner

    // Explicit type converters
    private val byteToLong: IrSimpleFunction =
        pluginContext.referenceFunctions(FqName("io.realm.internal.byteToLong")).first().owner
    private val longToByte: IrSimpleFunction =
        pluginContext.referenceFunctions(FqName("io.realm.internal.longToByte")).first().owner
    private val charToLong: IrSimpleFunction =
        pluginContext.referenceFunctions(FqName("io.realm.internal.charToLong")).first().owner
    private val longToChar: IrSimpleFunction =
        pluginContext.referenceFunctions(FqName("io.realm.internal.longToChar")).first().owner
    private val shortToLong: IrSimpleFunction =
        pluginContext.referenceFunctions(FqName("io.realm.internal.shortToLong")).first().owner
    private val longToShort: IrSimpleFunction =
        pluginContext.referenceFunctions(FqName("io.realm.internal.longToShort")).first().owner
    private val intToLong: IrSimpleFunction =
        pluginContext.referenceFunctions(FqName("io.realm.internal.intToLong")).first().owner
    private val longToInt: IrSimpleFunction =
        pluginContext.referenceFunctions(FqName("io.realm.internal.longToInt")).first().owner
    private val realmValueToRealmInstant: IrSimpleFunction =
        pluginContext.referenceFunctions(FqName("io.realm.internal.realmValueToRealmInstant")).first().owner

    private lateinit var objectReferenceProperty: IrProperty
    private lateinit var objectReferenceType: IrType

    fun modifyPropertiesAndCollectSchema(irClass: IrClass) {
        logInfo("Processing class ${irClass.name}")
        val fields = SchemaCollector.properties.getOrPut(irClass, { mutableMapOf() })

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
                    declaration.backingField!!.hasAnnotation(FqNames.IGNORE_ANNOTATION) ||
                        declaration.backingField!!.hasAnnotation(FqNames.TRANSIENT_ANNOTATION)

                when {
                    excludeProperty -> {
                        logInfo("Property named ${declaration.name} ignored")
                    }
                    propertyType.isString() -> {
                        logInfo("String property named ${declaration.name} is nullable $nullable")
                        fields[name] = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_STRING,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        modifyAccessor(
                            declaration,
                            getFunction = getValue,
                            setFunction = setValue
                        )
                    }
                    propertyType.isByte() -> {
                        logInfo("Byte property named ${declaration.name} is nullable $nullable")
                        fields[name] = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_INT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        modifyAccessor(
                            declaration,
                            getFunction = getValue,
                            toPublic = longToByte,
                            setFunction = setValue,
                            fromPublic = byteToLong
                        )
                    }
                    propertyType.isChar() -> {
                        logInfo("Char property named ${declaration.name} is nullable $nullable")
                        fields[name] = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_INT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        modifyAccessor(
                            declaration,
                            getFunction = getValue,
                            toPublic = longToChar,
                            setFunction = setValue,
                            fromPublic = charToLong
                        )
                    }
                    propertyType.isShort() -> {
                        logInfo("Short property named ${declaration.name} is nullable $nullable")
                        fields[name] = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_INT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        modifyAccessor(
                            declaration,
                            getFunction = getValue,
                            toPublic = longToShort,
                            setFunction = setValue,
                            fromPublic = shortToLong
                        )
                    }
                    propertyType.isInt() -> {
                        logInfo("Int property named ${declaration.name} is nullable $nullable")
                        fields[name] = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_INT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        modifyAccessor(
                            declaration,
                            getFunction = getValue,
                            toPublic = longToInt,
                            setFunction = setValue,
                            fromPublic = intToLong
                        )
                    }
                    propertyType.isLong() -> {
                        logInfo("Long property named ${declaration.name} is nullable $nullable")
                        fields[name] = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_INT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        modifyAccessor(
                            declaration,
                            getFunction = getValue,
                            setFunction = setValue
                        )
                    }
                    propertyType.isBoolean() -> {
                        logInfo("Boolean property named ${declaration.name} is nullable $nullable")
                        fields[name] = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_BOOL,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        modifyAccessor(
                            declaration,
                            getFunction = getValue,
                            setFunction = setValue
                        )
                    }
                    propertyType.isFloat() -> {
                        logInfo("Float property named ${declaration.name} is nullable $nullable")
                        fields[name] = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_FLOAT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        modifyAccessor(
                            declaration,
                            getFunction = getValue,
                            setFunction = setValue
                        )
                    }
                    propertyType.isDouble() -> {
                        logInfo("Double property named ${declaration.name} is nullable $nullable")
                        fields[name] = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_DOUBLE,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        modifyAccessor(
                            declaration,
                            getFunction = getValue,
                            setFunction = setValue
                        )
                    }
                    propertyType.isRealmInstant() -> {
                        logInfo("RealmInstant property named ${declaration.name} is nullable $nullable")
                        fields[name] = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_TIMESTAMP,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        modifyAccessor(
                            declaration,
                            getFunction = getValue,
                            fromRealmValue = realmValueToRealmInstant,
                            setFunction = setValue
                        )
                    }
                    propertyType.isRealmList() -> {
                        logInfo("RealmList property named ${declaration.name} is nullable $nullable")
                        processListField(fields, name, declaration)
                    }
                    !propertyType.isPrimitiveType() -> {
                        logInfo("Object property named ${declaration.name} is nullable $nullable")
                        fields[name] = SchemaProperty(
                            propertyType = PropertyType.RLM_PROPERTY_TYPE_OBJECT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        // Current getObject/setObject has it's own public->storagetype->realmvalue
                        // conversion so bypass any converters in accessors
                        modifyAccessor(
                            declaration,
                            getFunction = getObject,
                            fromRealmValue = null,
                            toPublic = null,
                            setFunction = setObject,
                            fromPublic = null,
                            toRealmValue = null
                        )
                    }
                    else -> {
                        logInfo("Type not processed: ${declaration.dump()}")
                    }
                }

                return super.visitProperty(declaration)
            }
        })
    }

    private fun processListField(
        fields: MutableMap<String, SchemaProperty>,
        name: String,
        declaration: IrProperty
    ) {
        val type = declaration.symbol.descriptor.type
        if (type.arguments[0] is StarProjectionImpl) {
            logError(
                "Error in field ${declaration.name} - RealmLists cannot use a '*' projection.",
                declaration.locationOf()
            )
            return
        }
        val listGenericType = type.arguments[0].type
        val coreGenericTypes = getListGenericCoreType(declaration)

        // Only process field if we got valid generics
        if (coreGenericTypes != null) {
            val genericPropertyType = getPropertyTypeFromKotlinType(listGenericType)

            // Only process
            if (genericPropertyType != null) {
                fields[name] = SchemaProperty(
                    propertyType = genericPropertyType,
                    declaration = declaration,
                    collectionType = CollectionType.LIST,
                    coreGenericTypes = listOf(coreGenericTypes)
                )
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

                // getList/setList gets/sets raw lists soi bypass any converters in accessors
                modifyAccessor(
                    property = declaration,
                    getFunction = getList,
                    fromRealmValue = null,
                    toPublic = null,
                    setFunction = setList,
                    fromPublic = null,
                    toRealmValue = null,
                    collectionType = CollectionType.LIST
                )
            }
        }
    }

    @Suppress("LongParameterList", "LongMethod", "ComplexMethod")
    private fun modifyAccessor(
        property: IrProperty,
        getFunction: IrSimpleFunction,
        fromRealmValue: IrSimpleFunction? = realmValueToAny,
        toPublic: IrSimpleFunction? = null,
        setFunction: IrSimpleFunction? = null,
        fromPublic: IrSimpleFunction? = null,
        toRealmValue: IrSimpleFunction? = anyToRealmValue,
        collectionType: CollectionType = CollectionType.NONE
    ) {
        val backingField = property.backingField!!
        val type = when (collectionType) {
            CollectionType.NONE -> backingField.type
            CollectionType.LIST -> ((backingField.type as IrSimpleType).arguments[0] as IrTypeBase).type
            else -> error("Collection type '$collectionType' not supported.")
        }
        val getter = property.getter
        val setter = property.setter
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
                        val managedObjectGetValueCall: IrCall =
                            irCall(
                                callee = getFunction,
                                origin = IrStatementOrigin.GET_PROPERTY
                            ).also {
                                it.dispatchReceiver = irGetObject(realmObjectHelper.symbol)
                            }.apply {
                                if (typeArgumentsCount > 0) {
                                    putTypeArgument(0, type)
                                }
                                putValueArgument(0, irGet(objectReferenceType, valueSymbol))
                                putValueArgument(1, irString(property.name.identifier))
                            }
                        val storageValue = fromRealmValue?.let {
                            irCall(callee = it).apply {
                                putValueArgument(0, managedObjectGetValueCall)
                            }
                        } ?: managedObjectGetValueCall
                        val publicValue = toPublic ?.let {
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
                        val realmValue: IrDeclarationReference = toRealmValue ?.let {
                            irCall(callee = it).apply {
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
                            putValueArgument(1, irString(property.name.identifier))
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

    private fun IrType.isRealmInstant(): Boolean {
        val propertyClassId = this.classifierOrFail.descriptor.classId
        val realmInstantClassId = realmInstantClass.descriptor.classId
        return propertyClassId == realmInstantClassId
    }

    @Suppress("ReturnCount")
    private fun getListGenericCoreType(declaration: IrProperty): CoreType? {
        // Check first if the generic is a subclass of RealmObject
        val descriptorType = declaration.symbol.descriptor.type
        val listGenericType = descriptorType.arguments[0].type
        if (inheritsFromRealmObject(listGenericType.constructor.supertypes)) {
            // Nullable objects are not supported
            if (listGenericType.isNullable()) {
                logError(
                    "Error in field ${declaration.name} - RealmLists can only contain non-nullable RealmObjects.",
                    declaration.locationOf()
                )
            }
            return CoreType(
                propertyType = PropertyType.RLM_PROPERTY_TYPE_OBJECT,
                nullable = false
            )
        }

        // If not a RealmObject, check whether the list itself is nullable - if so, throw error
        if (descriptorType.isNullable()) {
            logError(
                "Error in field ${declaration.name} - a RealmList field cannot be marked as nullable.",
                declaration.locationOf()
            )
            return null
        }

        // Otherwise just return the matching core type present in the declaration
        val genericPropertyType = getPropertyTypeFromKotlinType(listGenericType)
        return if (genericPropertyType != null) {
            CoreType(
                propertyType = genericPropertyType,
                nullable = listGenericType.isNullable()
            )
        } else {
            logError("Unsupported type for lists: '$listGenericType'", declaration.locationOf())
            null
        }
    }

    // TODO do the lookup only once
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
                    else ->
                        if (inheritsFromRealmObject(type.supertypes())) {
                            PropertyType.RLM_PROPERTY_TYPE_OBJECT
                        } else {
                            null
                        }
                }
            }
    }

    private fun inheritsFromRealmObject(supertypes: Collection<KotlinType>): Boolean =
        supertypes.any {
            it.constructor.declarationDescriptor?.fqNameSafe == REALM_MODEL_INTERFACE
        }
}
