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

import io.realm.kotlin.compiler.FqNames.CLASS_INFO
import io.realm.kotlin.compiler.FqNames.COLLECTION_TYPE
import io.realm.kotlin.compiler.FqNames.EMBEDDED_OBJECT_INTERFACE
import io.realm.kotlin.compiler.FqNames.INDEX_ANNOTATION
import io.realm.kotlin.compiler.FqNames.KOTLIN_COLLECTIONS_MAP
import io.realm.kotlin.compiler.FqNames.KOTLIN_COLLECTIONS_MAPOF
import io.realm.kotlin.compiler.FqNames.KOTLIN_PAIR
import io.realm.kotlin.compiler.FqNames.OBJECT_REFERENCE_CLASS
import io.realm.kotlin.compiler.FqNames.PRIMARY_KEY_ANNOTATION
import io.realm.kotlin.compiler.FqNames.PROPERTY_INFO
import io.realm.kotlin.compiler.FqNames.PROPERTY_TYPE
import io.realm.kotlin.compiler.FqNames.REALM_INSTANT
import io.realm.kotlin.compiler.FqNames.REALM_MODEL_COMPANION
import io.realm.kotlin.compiler.FqNames.REALM_NATIVE_POINTER
import io.realm.kotlin.compiler.FqNames.REALM_OBJECT_ID
import io.realm.kotlin.compiler.FqNames.REALM_OBJECT_INTERFACE
import io.realm.kotlin.compiler.FqNames.REALM_OBJECT_INTERNAL_INTERFACE
import io.realm.kotlin.compiler.Names.CLASS_INFO_CREATE
import io.realm.kotlin.compiler.Names.OBJECT_REFERENCE
import io.realm.kotlin.compiler.Names.PROPERTY_COLLECTION_TYPE_LIST
import io.realm.kotlin.compiler.Names.PROPERTY_COLLECTION_TYPE_NONE
import io.realm.kotlin.compiler.Names.PROPERTY_COLLECTION_TYPE_SET
import io.realm.kotlin.compiler.Names.PROPERTY_INFO_CREATE
import io.realm.kotlin.compiler.Names.PROPERTY_TYPE_OBJECT
import io.realm.kotlin.compiler.Names.REALM_OBJECT_COMPANION_CLASS_NAME_MEMBER
import io.realm.kotlin.compiler.Names.REALM_OBJECT_COMPANION_FIELDS_MEMBER
import io.realm.kotlin.compiler.Names.REALM_OBJECT_COMPANION_IS_EMBEDDED
import io.realm.kotlin.compiler.Names.REALM_OBJECT_COMPANION_NEW_INSTANCE_METHOD
import io.realm.kotlin.compiler.Names.REALM_OBJECT_COMPANION_PRIMARY_KEY_MEMBER
import io.realm.kotlin.compiler.Names.REALM_OBJECT_COMPANION_SCHEMA_METHOD
import io.realm.kotlin.compiler.Names.SET
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.at
import org.jetbrains.kotlin.ir.builders.declarations.addGetter
import org.jetbrains.kotlin.ir.builders.declarations.addProperty
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irLong
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrExpressionBodyImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrPropertyReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrSetFieldImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrFail
import org.jetbrains.kotlin.ir.types.createType
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.types.isSubtypeOfClass
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.getPropertySetter
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly

/**
 * Helper to assisting in modifying classes marked with the [RealmObject] interface according to our
 * needs:
 * - Adding the internal properties of [io.realm.kotlin.internal.interop.RealmObjectInterop]
 * - Adding the internal properties and methods of [RealmObjectCompanion] to the associated companion.
 */
class RealmModelSyntheticPropertiesGeneration(private val pluginContext: IrPluginContext) {
    private val realmObjectInterface: IrClass =
        pluginContext.lookupClassOrThrow(REALM_OBJECT_INTERFACE)
    private val embeddedRealmObjectInterface: IrClass =
        pluginContext.lookupClassOrThrow(EMBEDDED_OBJECT_INTERFACE)
    private val realmModelInternalInterface: IrClass =
        pluginContext.lookupClassOrThrow(REALM_OBJECT_INTERNAL_INTERFACE)
    private val nullableNativePointerInterface =
        pluginContext.lookupClassOrThrow(REALM_NATIVE_POINTER)
            .symbol.createType(true, emptyList())
    private val realmObjectCompanionInterface =
        pluginContext.lookupClassOrThrow(REALM_MODEL_COMPANION)
    private val classInfoClass = pluginContext.lookupClassOrThrow(CLASS_INFO)
    val classInfoCreateMethod = classInfoClass.lookupCompanionDeclaration<IrSimpleFunction>(CLASS_INFO_CREATE)

    private val propertyClass = pluginContext.lookupClassOrThrow(PROPERTY_INFO)
    val propertyCreateMethod = propertyClass.lookupCompanionDeclaration<IrSimpleFunction>(PROPERTY_INFO_CREATE)

    private val propertyType: IrClass = pluginContext.lookupClassOrThrow(PROPERTY_TYPE)
    private val propertyTypes =
        propertyType.declarations.filterIsInstance<IrEnumEntry>()
    private val collectionType: IrClass = pluginContext.lookupClassOrThrow(COLLECTION_TYPE)
    private val collectionTypes =
        collectionType.declarations.filterIsInstance<IrEnumEntry>()

    private val objectReferenceClass = pluginContext.lookupClassOrThrow(OBJECT_REFERENCE_CLASS)
    private val realmInstantType: IrType = pluginContext.lookupClassOrThrow(REALM_INSTANT).defaultType
    private val objectIdType: IrType = pluginContext.lookupClassOrThrow(REALM_OBJECT_ID).defaultType

    private val kProperty1Class: IrClass =
        pluginContext.lookupClassOrThrow(FqNames.KOTLIN_REFLECT_KPROPERTY1)

    private val mapClass: IrClass = pluginContext.lookupClassOrThrow(KOTLIN_COLLECTIONS_MAP)
    private val pairClass: IrClass = pluginContext.lookupClassOrThrow(KOTLIN_PAIR)
    private val pairCtor = pluginContext.lookupConstructorInClass(KOTLIN_PAIR)
    private val realmObjectPropertyType = kProperty1Class.typeWith(
        realmObjectInterface.defaultType,
        pluginContext.irBuiltIns.anyNType.makeNullable()
    )
    private val mapOf = pluginContext.referenceFunctions(KOTLIN_COLLECTIONS_MAPOF)
        .first {
            val parameters = it.owner.valueParameters
            parameters.size == 1 && parameters.first().isVararg
        }
    private val companionFieldsType = mapClass.typeWith(
        pluginContext.irBuiltIns.stringType,
        realmObjectPropertyType
    )
    private val companionFieldsElementType = pairClass.typeWith(
        pluginContext.irBuiltIns.stringType,
        realmObjectPropertyType
    )

    private val listIrClass: IrClass =
        pluginContext.lookupClassOrThrow(FqNames.KOTLIN_COLLECTIONS_LIST)
    val realmClassImpl = pluginContext.lookupClassOrThrow(FqNames.REALM_CLASS_IMPL)
    private val realmClassCtor = pluginContext.lookupConstructorInClass(FqNames.REALM_CLASS_IMPL) {
        it.owner.valueParameters.size == 2
    }

    fun addProperties(irClass: IrClass): IrClass =
        irClass.apply {
            addVariableProperty(
                realmModelInternalInterface,
                OBJECT_REFERENCE,
                objectReferenceClass.defaultType.makeNullable(),
                ::irNull
            )
        }

    @Suppress("LongMethod")
    fun addCompanionFields(
        className: String,
        companion: IrClass,
        properties: MutableMap<String, SchemaProperty>?,
    ) {
        val kPropertyType = kProperty1Class.typeWith(
            companion.parentAsClass.defaultType,
            pluginContext.irBuiltIns.anyNType.makeNullable()
        )
        companion.addValueProperty(
            pluginContext,
            realmObjectCompanionInterface,
            REALM_OBJECT_COMPANION_CLASS_NAME_MEMBER,
            pluginContext.irBuiltIns.stringType
        ) { startOffset, endOffset ->
            IrConstImpl.string(startOffset, endOffset, pluginContext.irBuiltIns.stringType, className)
        }
        // Add RealmObjectCompanion.io_realm_kotlin_fields: Map<String, KMutableProperty1<*, *>>
        companion.addValueProperty(
            pluginContext,
            realmObjectCompanionInterface,
            REALM_OBJECT_COMPANION_FIELDS_MEMBER,
            companionFieldsType
        ) { startOffset, endOffset ->
            IrCallImpl(
                startOffset = startOffset, endOffset = endOffset,
                type = companionFieldsType,
                symbol = mapOf,
                typeArgumentsCount = 2,
                valueArgumentsCount = 1,
                origin = null,
                superQualifierSymbol = null
            ).apply {
                putTypeArgument(index = 0, type = pluginContext.irBuiltIns.stringType)
                putTypeArgument(index = 1, type = realmObjectPropertyType)
                putValueArgument(
                    index = 0,
                    valueArgument = IrVarargImpl(
                        UNDEFINED_OFFSET,
                        UNDEFINED_OFFSET,
                        pluginContext.irBuiltIns.arrayClass.typeWith(companionFieldsElementType),
                        type,
                        properties!!.entries.map {
                            val property = it.value.declaration
                            IrConstructorCallImpl.fromSymbolOwner(
                                startOffset = startOffset,
                                endOffset = endOffset,
                                type = companionFieldsElementType,
                                constructorSymbol = pairCtor
                            ).apply {
                                putTypeArgument(0, pluginContext.irBuiltIns.stringType)
                                putTypeArgument(1, realmObjectPropertyType)
                                putValueArgument(
                                    0,
                                    IrConstImpl.string(
                                        startOffset,
                                        endOffset,
                                        pluginContext.irBuiltIns.stringType,
                                        property.name.identifier
                                    )
                                )
                                putValueArgument(
                                    1,
                                    IrPropertyReferenceImpl(
                                        startOffset = startOffset,
                                        endOffset = endOffset,
                                        type = kPropertyType,
                                        symbol = property.symbol,
                                        typeArgumentsCount = 0,
                                        field = null,
                                        getter = property.getter?.symbol,
                                        setter = property.setter?.symbol
                                    )
                                )
                            }
                        }
                    )
                )
            }
        }

        val primaryKeyFields = properties!!.filter {
            it.value.declaration.backingField!!.hasAnnotation(PRIMARY_KEY_ANNOTATION)
        }

        val primaryKey: IrProperty? = when (primaryKeyFields.size) {
            0 -> null
            1 -> primaryKeyFields.entries.first().value.declaration
            else -> {
                logError("RealmObject can only have one primary key", companion.parentAsClass.locationOf())
                null
            }
        }

        companion.addValueProperty(
            pluginContext,
            realmObjectCompanionInterface,
            REALM_OBJECT_COMPANION_PRIMARY_KEY_MEMBER,
            kPropertyType
        ) { startOffset, endOffset ->
            primaryKey?.let {
                IrPropertyReferenceImpl(
                    startOffset = startOffset,
                    endOffset = endOffset,
                    type = kPropertyType,
                    symbol = primaryKey.symbol,
                    typeArgumentsCount = 0,
                    field = null,
                    getter = primaryKey.getter?.symbol,
                    setter = primaryKey.setter?.symbol
                )
            } ?: IrConstImpl.constNull(
                startOffset,
                endOffset,
                pluginContext.irBuiltIns.nothingNType
            )
        }
        companion.addValueProperty(
            pluginContext,
            realmObjectCompanionInterface,
            REALM_OBJECT_COMPANION_IS_EMBEDDED,
            pluginContext.irBuiltIns.booleanType
        ) { startOffset, endOffset ->
            IrConstImpl.boolean(
                startOffset,
                endOffset,
                pluginContext.irBuiltIns.booleanType,
                companion.parentAsClass.defaultType.isSubtypeOfClass(embeddedRealmObjectInterface.symbol)
            )
        }
    }

    // Generate body for the synthetic schema method defined inside the Companion instance previously declared via `RealmModelSyntheticCompanionExtension`
    // TODO OPTIMIZE should be a one time only constructed object
    @OptIn(ObsoleteDescriptorBasedAPI::class)
    @Suppress("LongMethod", "ComplexMethod")
    fun addSchemaMethodBody(irClass: IrClass) {
        val companionObject = irClass.companionObject() as? IrClass
            ?: fatalError("Companion object not available")

        val fields: MutableMap<String, SchemaProperty> =
            SchemaCollector.properties.getOrDefault(irClass, mutableMapOf())

        val primaryKeyFields =
            fields.filter { it.value.declaration.backingField!!.hasAnnotation(PRIMARY_KEY_ANNOTATION) }

        val embedded = irClass.isEmbeddedRealmObject
        if (embedded && primaryKeyFields.isNotEmpty()) {
            logError("Embedded object is not allowed to have a primary key", irClass.locationOf())
        }
        val primaryKey: String? = when (primaryKeyFields.size) {
            0 -> null
            1 -> primaryKeyFields.entries.first().key
            else -> {
                logError("RealmObject can only have one primary key", irClass.locationOf())
                null
            }
        }

        val function =
            companionObject.functions.first { it.name == REALM_OBJECT_COMPANION_SCHEMA_METHOD }
        function.dispatchReceiverParameter = companionObject.thisReceiver?.copyTo(function)
        function.body = pluginContext.blockBody(function.symbol) {
            +irReturn(
                IrConstructorCallImpl.fromSymbolOwner(
                    startOffset = startOffset,
                    endOffset = endOffset,
                    type = realmClassImpl.defaultType,
                    constructorSymbol = realmClassCtor
                ).apply {
                    putValueArgument(
                        0,
                        IrCallImpl(
                            startOffset,
                            endOffset,
                            type = classInfoClass.defaultType,
                            symbol = classInfoCreateMethod.symbol,
                            typeArgumentsCount = 0,
                            valueArgumentsCount = 4
                        ).apply {
                            dispatchReceiver = irGetObject(classInfoClass.companionObject()!!.symbol)
                            var arg = 0
                            // Name
                            putValueArgument(arg++, irString(irClass.name.identifier))
                            // Primary key
                            putValueArgument(
                                arg++,
                                if (primaryKey != null) irString(primaryKey) else {
                                    IrConstImpl.constNull(
                                        startOffset,
                                        endOffset,
                                        pluginContext.irBuiltIns.nothingNType
                                    )
                                }
                            )
                            // num properties
                            putValueArgument(arg++, irLong(fields.size.toLong()))
                            putValueArgument(arg++, irBoolean(embedded))
                        }
                    )
                    putValueArgument(
                        1,
                        buildListOf(
                            pluginContext, startOffset, endOffset, propertyClass.defaultType,
                            fields.map { entry ->
                                val value = entry.value

                                // Extract type based on whether the field is a:
                                // 1 - primitive type, in which case it is extracted as is
                                // 2 - collection type, in which case the collection type(s)
                                //     specified in value.genericTypes should be used as type
                                val type = when (val primitiveType = getType(value.propertyType)) {
                                    null -> // Primitive type is null for collections
                                        when (value.collectionType) {
                                            CollectionType.LIST,
                                            CollectionType.SET ->
                                                // Extract generic type as mentioned
                                                getType(getCollectionType(value.coreGenericTypes))
                                                    ?: error("Unknown type ${value.propertyType} - should be a valid type for collections.")
                                            CollectionType.DICTIONARY ->
                                                error("Dictionaries not available yet.")
                                            else ->
                                                error("Unknown type ${value.propertyType}.")
                                        }
                                    else -> // Primitive type is non-null
                                        primitiveType
                                }

                                val objectType = propertyTypes.firstOrNull {
                                    it.name == PROPERTY_TYPE_OBJECT
                                } ?: error("Unknown type ${value.propertyType}")

                                val property = value.declaration
                                val backingField = property.backingField
                                    ?: fatalError("Property without backing field or type.")
                                // Nullability applies to the generic type in collections
                                val nullable = if (value.collectionType == CollectionType.NONE) {
                                    backingField.type.isNullable()
                                } else {
                                    value.coreGenericTypes?.get(0)?.nullable
                                        ?: fatalError("Missing generic type while processing a collection field.")
                                }
                                val primaryKey = backingField.hasAnnotation(PRIMARY_KEY_ANNOTATION)
                                val isIndexed = backingField.hasAnnotation(INDEX_ANNOTATION)

                                val validPrimaryKeyTypes = with(pluginContext.irBuiltIns) {
                                    setOf(
                                        byteType,
                                        charType,
                                        shortType,
                                        intType,
                                        longType,
                                        stringType,
                                        objectIdType
                                    ).map { it.classifierOrFail }
                                }
                                if (primaryKey && backingField.type.classifierOrFail !in validPrimaryKeyTypes) {
                                    logError(
                                        "Primary key ${property.name} is of type ${backingField.type.classifierOrFail.owner.symbol.descriptor.name} but must be of type ${validPrimaryKeyTypes.map { it.owner.symbol.descriptor.name }}",
                                        property.locationOf()
                                    )
                                }
                                val indexableTypes = with(pluginContext.irBuiltIns) {
                                    setOf(byteType, charType, shortType, intType, longType, stringType, realmInstantType, objectIdType).map { it.classifierOrFail }
                                }
                                if (isIndexed && backingField.type.classifierOrFail !in indexableTypes) {
                                    logError(
                                        "Indexed key ${property.name} is of type ${backingField.type.classifierOrFail.owner.symbol.descriptor.name} but must be of type ${indexableTypes.map { it.owner.symbol.descriptor.name }}",
                                        property.locationOf()
                                    )
                                }

                                IrCallImpl(
                                    startOffset,
                                    endOffset,
                                    type = propertyClass.defaultType,
                                    symbol = propertyCreateMethod.symbol,
                                    typeArgumentsCount = 0,
                                    valueArgumentsCount = 9
                                ).apply {
                                    dispatchReceiver = irGetObject(propertyClass.companionObject()!!.symbol)
                                    var arg = 0
                                    // Name
                                    putValueArgument(arg++, irString(entry.key))
                                    // Public name
                                    putValueArgument(arg++, irString(""))
                                    // Type
                                    putValueArgument(
                                        arg++,
                                        IrGetEnumValueImpl(
                                            startOffset = UNDEFINED_OFFSET,
                                            endOffset = UNDEFINED_OFFSET,
                                            type = propertyType.defaultType,
                                            symbol = type.symbol
                                        )
                                    )
                                    // Collection type: remember to specify it correctly here - the
                                    // type of the contents itself is specified as "type" above!
                                    val collectionTypeSymbol = when (value.collectionType) {
                                        CollectionType.NONE -> PROPERTY_COLLECTION_TYPE_NONE
                                        CollectionType.LIST -> PROPERTY_COLLECTION_TYPE_LIST
                                        CollectionType.SET -> PROPERTY_COLLECTION_TYPE_SET
                                        else ->
                                            error("Unsupported collection type '${value.collectionType}' for field ${entry.key}")
                                    }
                                    putValueArgument(
                                        arg++,
                                        IrGetEnumValueImpl(
                                            startOffset = UNDEFINED_OFFSET,
                                            endOffset = UNDEFINED_OFFSET,
                                            type = collectionType.defaultType,
                                            symbol = collectionTypes.first {
                                                it.name == collectionTypeSymbol
                                            }.symbol
                                        )
                                    )
                                    // Link target
                                    putValueArgument(
                                        arg++,
                                        if (type == objectType) {
                                            // Collections of type RealmObject require the type parameter be retrieved from the generic argument
                                            val linkTargetType = when (collectionTypeSymbol) {
                                                PROPERTY_COLLECTION_TYPE_NONE ->
                                                    backingField.type
                                                PROPERTY_COLLECTION_TYPE_LIST,
                                                PROPERTY_COLLECTION_TYPE_SET ->
                                                    getCollectionElementType(backingField.type)
                                                        ?: error("Could not get collection type from ${backingField.type}")
                                                else ->
                                                    error("Unsupported collection type '$collectionTypeSymbol' for field ${entry.key}")
                                            }
                                            irString(linkTargetType.classifierOrFail.descriptor.name.identifier)
                                        } else {
                                            irString("")
                                        }
                                    )
                                    // Link property name
                                    putValueArgument(arg++, irString(""))
                                    // isNullable
                                    putValueArgument(arg++, irBoolean(nullable))
                                    // isPrimaryKey
                                    putValueArgument(arg++, irBoolean(primaryKey))
                                    // isIndexed
                                    putValueArgument(arg++, irBoolean(isIndexed))
                                }
                            }
                        )
                    )
                }
            )
        }
        function.overriddenSymbols =
            listOf(realmObjectCompanionInterface.functions.first { it.name == REALM_OBJECT_COMPANION_SCHEMA_METHOD }.symbol)
    }

    private fun getType(type: PropertyType): IrEnumEntry? {
        return propertyTypes.firstOrNull {
            it.name.identifier.toLowerCaseAsciiOnly().contains(type.name.toLowerCaseAsciiOnly())
        }
    }

    private fun getCollectionType(generics: List<CoreType>?): PropertyType =
        checkNotNull(generics) { "Missing type for collection." }[0].propertyType

    // Generate body for the synthetic new instance method defined inside the Companion instance previously declared via `RealmModelSyntheticCompanionExtension`
    fun addNewInstanceMethodBody(irClass: IrClass) {
        val companionObject = irClass.companionObject() as? IrClass
            ?: fatalError("Companion object not available")

        val function =
            companionObject.functions.first { it.name == REALM_OBJECT_COMPANION_NEW_INSTANCE_METHOD }
        function.dispatchReceiverParameter = companionObject.thisReceiver?.copyTo(function)
        function.body = pluginContext.blockBody(function.symbol) {
            val firstZeroArgCtor: Any = irClass.constructors.filter { it.valueParameters.isEmpty() }.firstOrNull()
                ?: logError("Cannot find primary zero arg constructor", irClass.locationOf())
            if (firstZeroArgCtor is IrConstructor) {
                +irReturn(
                    IrConstructorCallImpl.fromSymbolOwner(
                        startOffset = startOffset,
                        endOffset = endOffset,
                        type = firstZeroArgCtor.returnType,
                        constructorSymbol = firstZeroArgCtor.symbol
                    )
                )
            }
        }
        function.overriddenSymbols =
            listOf(realmObjectCompanionInterface.functions.first { it.name == REALM_OBJECT_COMPANION_NEW_INSTANCE_METHOD }.symbol)
    }

    @Suppress("LongMethod")
    private fun IrClass.addVariableProperty(
        owner: IrClass,
        propertyName: Name,
        propertyType: IrType,
        initExpression: (startOffset: Int, endOffset: Int) -> IrExpressionBody
    ) {
        // PROPERTY name:realmPointer visibility:public modality:OPEN [var]
        val property = addProperty {
            at(this@addVariableProperty.startOffset, this@addVariableProperty.endOffset)
            name = propertyName
            visibility = DescriptorVisibilities.PUBLIC
            modality = Modality.OPEN
            isVar = true
        }
        // FIELD PROPERTY_BACKING_FIELD name:objectPointer type:kotlin.Long? visibility:private
        property.backingField = pluginContext.irFactory.buildField {
            at(this@addVariableProperty.startOffset, this@addVariableProperty.endOffset)
            origin = IrDeclarationOrigin.PROPERTY_BACKING_FIELD
            name = property.name
            visibility = DescriptorVisibilities.PRIVATE
            modality = property.modality
            type = propertyType
        }.apply {
            // EXPRESSION_BODY
            //  CONST Boolean type=kotlin.Boolean value=false
            initializer = initExpression(startOffset, endOffset)
        }
        property.backingField?.parent = this
        property.backingField?.correspondingPropertySymbol = property.symbol

        // FUN DEFAULT _PROPERTY_ACCESSOR name:<get-objectPointer> visibility:public modality:OPEN <> ($this:dev.nhachicha.Foo.$RealmHandler) returnType:kotlin.Long?
        // correspondingProperty: PROPERTY name:objectPointer visibility:public modality:OPEN [var]
        val getter = property.addGetter {
            at(this@addVariableProperty.startOffset, this@addVariableProperty.endOffset)
            visibility = DescriptorVisibilities.PUBLIC
            modality = Modality.OPEN
            returnType = propertyType
            origin = IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR
        }
        // $this: VALUE_PARAMETER name:<this> type:dev.nhachicha.Foo.$RealmHandler
        getter.dispatchReceiverParameter = thisReceiver!!.copyTo(getter)
        // overridden:
        //   public abstract fun <get-realmPointer> (): kotlin.Long? declared in dev.nhachicha.RealmObjectInternal
        val propertyAccessorGetter = owner.getPropertyGetter(propertyName.asString())
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

        // FUN DEFAULT_PROPERTY_ACCESSOR name:<set-realmPointer> visibility:public modality:OPEN <> ($this:dev.nhachicha.Child, <set-?>:kotlin.Long?) returnType:kotlin.Unit
        //  correspondingProperty: PROPERTY name:realmPointer visibility:public modality:OPEN [var]
        val setter = property.addSetter {
            at(this@addVariableProperty.startOffset, this@addVariableProperty.endOffset)
            visibility = DescriptorVisibilities.PUBLIC
            modality = Modality.OPEN
            returnType = pluginContext.irBuiltIns.unitType
            origin = IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR
        }
        // $this: VALUE_PARAMETER name:<this> type:dev.nhachicha.Child
        setter.dispatchReceiverParameter = thisReceiver!!.copyTo(setter)
        setter.correspondingPropertySymbol = property.symbol

        // overridden:
        //  public abstract fun <set-realmPointer> (<set-?>: kotlin.Long?): kotlin.Unit declared in dev.nhachicha.RealmObjectInternal
        val realmPointerSetter = owner.getPropertySetter(propertyName.asString())
            ?: fatalError("${propertyName.asString()} function getter symbol is not available")
        setter.overriddenSymbols = listOf(realmPointerSetter)

        // VALUE_PARAMETER name:<set-?> index:0 type:kotlin.Long?
        // BLOCK_BODY
        //  SET_FIELD 'FIELD PROPERTY_BACKING_FIELD name:realmPointer type:kotlin.Long? visibility:private' type=kotlin.Unit origin=null
        //  receiver: GET_VAR '<this>: io.realm.example.Sample declared in io.realm.example.Sample.<set-realmPointer>' type=io.realm.example.Sample origin=null
        //  value: GET_VAR '<set-?>: kotlin.Long? declared in io.realm.example.Sample.<set-realmPointer>' type=kotlin.Long? origin=null
        val valueParameter = setter.addValueParameter {
            this.name = SET
            this.type = propertyType
        }
        setter.body = DeclarationIrBuilder(pluginContext, setter.symbol).irBlockBody {
            at(startOffset, endOffset)

            +IrSetFieldImpl(
                startOffset = startOffset,
                endOffset = endOffset,
                symbol = property.backingField!!.symbol,
                receiver = irGet(setter.dispatchReceiverParameter!!),
                value = irGet(valueParameter),
                type = context.irBuiltIns.unitType
            )
        }
    }

    private fun irNull(startOffset: Int, endOffset: Int): IrExpressionBody =
        IrExpressionBodyImpl(
            startOffset,
            endOffset,
            IrConstImpl.constNull(startOffset, endOffset, pluginContext.irBuiltIns.nothingNType)
        )

    private fun irFalse(startOffset: Int, endOffset: Int): IrExpressionBody =
        IrExpressionBodyImpl(
            startOffset,
            endOffset,
            IrConstImpl.constFalse(startOffset, endOffset, pluginContext.irBuiltIns.booleanType)
        )
}
