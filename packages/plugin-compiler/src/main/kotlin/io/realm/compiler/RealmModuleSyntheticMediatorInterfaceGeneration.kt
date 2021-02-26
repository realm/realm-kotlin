package io.realm.compiler

import io.realm.compiler.FqNames.JAVA_UTIL_ABSTRACT_COLLECTION
import io.realm.compiler.FqNames.JAVA_UTIL_ARRAY_LIST
import io.realm.compiler.FqNames.JAVA_UTIL_COLLECTION
import io.realm.compiler.FqNames.JAVA_UTIL_HASHMAP
import io.realm.compiler.FqNames.JAVA_UTIL_ITERATOR
import io.realm.compiler.FqNames.KOTLIN_COLLECTIONS_ABSTRACT_COLLECTION
import io.realm.compiler.FqNames.KOTLIN_COLLECTIONS_ARRAY_LIST
import io.realm.compiler.FqNames.KOTLIN_COLLECTIONS_HASHMAP
import io.realm.compiler.FqNames.KOTLIN_COLLECTIONS_ITERATOR
import io.realm.compiler.FqNames.KOTLIN_COLLECTIONS_MUTABLE_COLLECTION
import io.realm.compiler.FqNames.KOTLIN_COLLECTION_LIST
import io.realm.compiler.FqNames.REALM_MEDIATOR_INTERFACE
import io.realm.compiler.FqNames.REALM_MODEL_COMPANION
import io.realm.compiler.Names.REALM_MEDIATOR_MAPPING_PROPERTY
import io.realm.compiler.Names.REALM_MEDIATOR_NEW_INSTANCE_METHOD
import io.realm.compiler.Names.REALM_MEDIATOR_SCHEMA_METHOD
import io.realm.compiler.Names.REALM_OBJECT_COMPANION_NEW_INSTANCE_METHOD
import io.realm.compiler.Names.REALM_OBJECT_COMPANION_SCHEMA_METHOD
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.builders.IrBlockBuilder
import org.jetbrains.kotlin.ir.builders.at
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addGetter
import org.jetbrains.kotlin.ir.builders.declarations.addProperty
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSet
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOriginImpl
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator.IMPLICIT_COERCION_TO_UNIT
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrExpressionBodyImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTypeOperatorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrWhileLoopImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrAnonymousInitializerSymbolImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.impl.IrTypeBase
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.util.withReferenceScope
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.isNative
import org.jetbrains.kotlin.types.typeUtil.isInt

class RealmModuleSyntheticMediatorInterfaceGeneration(private val pluginContext: IrPluginContext) {

    // platform dependent lookups
    private lateinit var hashMapIrClass: IrClass
    private lateinit var hashMapCtor: IrConstructorSymbol
    private lateinit var mapIteratorFunction: IrSimpleFunction

    private lateinit var collectionType: IrSimpleType
    private lateinit var arrayListIrClassSymbol: IrClassSymbol
    private lateinit var arrayListCtor: IrConstructorSymbol
    private lateinit var iteratorIrClass: IrClass

    private val realmObjectCompanionIrClass: IrClass = pluginContext.lookupClassOrThrow(REALM_MODEL_COMPANION)
    private val listIrClass: IrClass = pluginContext.lookupClassOrThrow(KOTLIN_COLLECTION_LIST)
    private val mediatorIrClass = pluginContext.lookupClassOrThrow(REALM_MEDIATOR_INTERFACE)

    private var mapValuesProperty: IrPropertySymbol
    private var mapPutFunction: IrSimpleFunction
    private val mapGetFunction: IrSimpleFunction

    private var hasNextFunction: IrSimpleFunction
    private var nextFunction: IrSimpleFunction
    private var listAddFunction: IrSimpleFunction
    private var companionMapType: IrSimpleType
    private val collectionIteratorType: IrSimpleType

    init {
        when {
            // FIXME Optimize - We should rework building the companion map by calling `mapOf` which
            //  generated the platform abstracted map for us.
            pluginContext.platform.isNative() -> {
                hashMapIrClass = pluginContext.lookupClassOrThrow(KOTLIN_COLLECTIONS_HASHMAP)
                mapIteratorFunction = pluginContext.lookupFunctionInClass(KOTLIN_COLLECTIONS_ABSTRACT_COLLECTION, "iterator")
                arrayListIrClassSymbol = pluginContext.lookupClassOrThrow(KOTLIN_COLLECTIONS_ARRAY_LIST).symbol

                arrayListCtor = pluginContext.lookupConstructorInClass(KOTLIN_COLLECTIONS_ARRAY_LIST) {
                    it.owner.valueParameters.size == 1 &&
                        it.owner.valueParameters[0].type.isInt()
                }
                iteratorIrClass = pluginContext.lookupClassOrThrow(KOTLIN_COLLECTIONS_ITERATOR)

                hashMapCtor = pluginContext.lookupConstructorInClass(KOTLIN_COLLECTIONS_HASHMAP) {
                    it.owner.valueParameters.size == 1 &&
                        it.owner.valueParameters[0].type.isInt()
                }
                collectionType = pluginContext.lookupClassOrThrow(KOTLIN_COLLECTIONS_MUTABLE_COLLECTION).typeWith(arguments = listOf(realmObjectCompanionIrClass.symbol.defaultType))
            }
            pluginContext.platform.isJvm() -> {
                hashMapIrClass = pluginContext.lookupClassOrThrow(JAVA_UTIL_HASHMAP)
                mapIteratorFunction = pluginContext.lookupFunctionInClass(JAVA_UTIL_ABSTRACT_COLLECTION, "iterator")
                arrayListIrClassSymbol = pluginContext.lookupClassOrThrow(JAVA_UTIL_ARRAY_LIST).symbol
                arrayListCtor = pluginContext.lookupConstructorInClass(JAVA_UTIL_ARRAY_LIST) {
                    it.owner.valueParameters.size == 1 &&
                        (it.owner.valueParameters[0].type as IrTypeBase).kotlinType!!.isInt()
                }
                iteratorIrClass = pluginContext.lookupClassOrThrow(JAVA_UTIL_ITERATOR)
                hashMapCtor = pluginContext.lookupConstructorInClass(JAVA_UTIL_HASHMAP) {
                    it.owner.valueParameters.size == 1 &&
                        (it.owner.valueParameters[0].type as IrTypeBase).kotlinType!!.isInt()
                }
                collectionType = pluginContext.lookupClassOrThrow(JAVA_UTIL_COLLECTION).typeWith(arguments = listOf(realmObjectCompanionIrClass.symbol.defaultType))
            }
            else -> {
                logError("Unsupported platform ${pluginContext.platform}")
            }
        }
        mapPutFunction = hashMapIrClass.lookupFunction(Name.identifier("put"))
        mapGetFunction = hashMapIrClass.lookupFunction(Name.identifier("get"))
        mapValuesProperty = hashMapIrClass.properties.first {
            it.name == Name.identifier("values")
        }.symbol

        hasNextFunction = iteratorIrClass.lookupFunction(Name.identifier("hasNext"))
        nextFunction = iteratorIrClass.lookupFunction(Name.identifier("next"))
        listAddFunction = arrayListIrClassSymbol.owner.lookupFunction(Name.identifier("add"))

        // FIXME OPTIMIZE - Should be kotlin.collections.Map, no need to include implementation
        //  details in type
        companionMapType = hashMapIrClass.symbol.typeWith(
            pluginContext.irBuiltIns.kClassClass.starProjectedType,
            realmObjectCompanionIrClass.symbol.defaultType
        )

        collectionIteratorType = iteratorIrClass.typeWith(arguments = listOf(realmObjectCompanionIrClass.symbol.defaultType))
    }

    private val mediatorNewInstanceMethod: IrSimpleFunction = mediatorIrClass.lookupFunction(REALM_MEDIATOR_NEW_INSTANCE_METHOD)
    private val mediatorSchemaMethod: IrSimpleFunction = mediatorIrClass.lookupFunction(REALM_MEDIATOR_SCHEMA_METHOD)
    private val realmObjectCompanionNewInstanceFunction: IrSimpleFunction = realmObjectCompanionIrClass.lookupFunction(REALM_OBJECT_COMPANION_NEW_INSTANCE_METHOD)
    private val realmObjectCompanionRealmSchemaFunction: IrSimpleFunction = realmObjectCompanionIrClass.lookupFunction(REALM_OBJECT_COMPANION_SCHEMA_METHOD)

    @Suppress("ClassNaming")
    private object REALM_MEDIATOR_ORIGIN : IrDeclarationOriginImpl("MEDIATOR")

    @ObsoleteDescriptorBasedAPI
    fun addInterfaceMethodImplementation(irClass: IrClass, models: List<Triple<IrClassifierSymbol, IrType, IrClassSymbol>>): IrClass =
        // TODO move the implementation into the default Mediator interface, only the HashMap initializer block is specific per RealmModule
        irClass.apply {
            val mediatorMappingProperty = addInternalMapProperty(REALM_MEDIATOR_MAPPING_PROPERTY, models)
            addRealmMediatorSchemaMethod(mediatorMappingProperty)
            addRealmMediatorNewInstanceMethod(mediatorMappingProperty)
        }

    @ObsoleteDescriptorBasedAPI
    @Suppress("LongMethod")
    private fun IrClass.addInternalMapProperty(propertyName: Name, models: List<Triple<IrClassifierSymbol, IrType, IrClassSymbol /*Companion Class*/>>): IrProperty {
        val property = addProperty {
            at(this@addInternalMapProperty.startOffset, this@addInternalMapProperty.endOffset)
            name = propertyName
            visibility = DescriptorVisibilities.PRIVATE
            modality = Modality.FINAL
            isVar = false
        }

        property.backingField = pluginContext.irFactory.buildField {
            at(this@addInternalMapProperty.startOffset, this@addInternalMapProperty.endOffset)
            origin = IrDeclarationOrigin.PROPERTY_BACKING_FIELD
            name = property.name
            visibility = DescriptorVisibilities.PRIVATE
            modality = property.modality
            type = companionMapType
        }.apply {
            val numberOfModelInSchema = models.size
            val expression = IrExpressionBodyImpl(
                startOffset, endOffset,
                IrConstructorCallImpl(
                    startOffset,
                    endOffset,
                    type = companionMapType,
                    symbol = hashMapCtor,
                    typeArgumentsCount = 2,
                    constructorTypeArgumentsCount = 0,
                    valueArgumentsCount = 1
                )
                    .apply {
                        // <class: K>: kotlin.reflect.KClass<*>
                        // <class: V>: io.realm.internal.RealmObjectCompanion
                        // p0: CONST Int type=kotlin.Int value=2
                        putTypeArgument(0, pluginContext.irBuiltIns.kClassClass.starProjectedType)
                        putTypeArgument(1, realmObjectCompanionIrClass.defaultType)
                        putValueArgument(0, IrConstImpl.int(startOffset, endOffset, pluginContext.irBuiltIns.intType, numberOfModelInSchema))
                    }
            )
            initializer = expression
        }
        property.backingField?.parent = this
        property.backingField?.correspondingPropertySymbol = property.symbol

        val getter: IrSimpleFunction = property.addGetter {
            at(this@addInternalMapProperty.startOffset, this@addInternalMapProperty.endOffset)
            visibility = DescriptorVisibilities.PRIVATE
            modality = Modality.FINAL
            returnType = companionMapType
            origin = IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR
        }
        getter.dispatchReceiverParameter = thisReceiver!!.copyTo(getter)
        getter.body = pluginContext.blockBody(getter.symbol) {
            at(startOffset, endOffset)
            +irReturn(
                irGetField(irGet(getter.dispatchReceiverParameter!!), property.backingField!!)
            )
        }

        factory.createAnonymousInitializer(
            startOffset, endOffset,
            origin = REALM_MEDIATOR_ORIGIN,
            symbol = IrAnonymousInitializerSymbolImpl(descriptor)
        ).also {
            it.parent = this
            declarations.add(it)
        }.apply {
            pluginContext.symbolTable.withReferenceScope(descriptor) {
                annotations = this@addInternalMapProperty.annotations
                body =
                    DeclarationIrBuilder(pluginContext, symbol, startOffset, endOffset).irBlockBody {
                        for (model in models) {
                            +IrTypeOperatorCallImpl(
                                startOffset, endOffset, pluginContext.irBuiltIns.unitType,
                                IMPLICIT_COERCION_TO_UNIT, pluginContext.irBuiltIns.unitType,
                                IrCallImpl(
                                    mapPutFunction.startOffset, mapPutFunction.endOffset,
                                    type = companionMapType,
                                    symbol = mapPutFunction.symbol,
                                    typeArgumentsCount = 0,
                                    valueArgumentsCount = 2
                                ).apply {
                                    dispatchReceiver = irCall(property.getter!!).apply {
                                        dispatchReceiver = irGet(this@addInternalMapProperty.thisReceiver!!)
                                    }

                                    putValueArgument(
                                        0,
                                        IrClassReferenceImpl(
                                            startOffset, endOffset,
                                            type = pluginContext.irBuiltIns.kClassClass.typeWith(arguments = listOf(model.second)),
                                            symbol = model.first,
                                            classType = model.second
                                        )
                                    )
                                    putValueArgument(
                                        1,
                                        irGetObject(model.third)
                                    )
                                }
                            )
                        }
                    }
            }
        }
        return property
    }

    private fun IrClass.addRealmMediatorNewInstanceMethod(mediatorMappingProperty: IrProperty) {
        addFunction(
            name = "newInstance",
            returnType = pluginContext.irBuiltIns.anyType,
            modality = Modality.OPEN,
            visibility = DescriptorVisibilities.PUBLIC,
            isStatic = false,
            isSuspend = false,
            isFakeOverride = false
        ).apply {
            val functionReceiver = this

            overriddenSymbols = listOf(mediatorNewInstanceMethod.symbol)
            val clazzParameter = addValueParameter(name = "clazz", type = pluginContext.irBuiltIns.kClassClass.starProjectedType)
            body = pluginContext.blockBody(symbol) {
                val realmObjectCompanionType = realmObjectCompanionIrClass.defaultType
                val elementVar = irTemporary(
                    nameHint = "companion",
                    value = IrCallImpl(
                        mapGetFunction.startOffset, mapGetFunction.endOffset,
                        type = realmObjectCompanionType,
                        symbol = mapGetFunction.symbol,
                        typeArgumentsCount = 0,
                        valueArgumentsCount = 1
                    )
                        .apply {
                            putValueArgument(0, irGet(clazzParameter))
                            dispatchReceiver = irCall(mediatorMappingProperty.getter!!).apply {
                                dispatchReceiver = irGet(functionReceiver.dispatchReceiverParameter!!)
                            }
                        }
                )
                +irReturn(
                    irCall(realmObjectCompanionNewInstanceFunction).apply {
                        dispatchReceiver = irGet(elementVar)
                    }
                )
            }
        }
    }

    @Suppress("LongMethod")
    private fun IrClass.addRealmMediatorSchemaMethod(mediatorMappingProperty: IrProperty) {
        addFunction(
            name = "schema",
            returnType = listIrClass.typeWith(pluginContext.irBuiltIns.anyType),
            modality = Modality.OPEN,
            visibility = DescriptorVisibilities.PUBLIC,
            isStatic = false,
            isSuspend = false,
            isFakeOverride = false
        ).apply {
            val functionReceiver = this
            overriddenSymbols = listOf(mediatorSchemaMethod.symbol)
            body = pluginContext.blockBody(symbol) {
                val listType = arrayListIrClassSymbol.owner.typeWith(pluginContext.irBuiltIns.stringType)
                val initializeListWithSize = IrExpressionBodyImpl(
                    startOffset, endOffset,
                    IrConstructorCallImpl(
                        startOffset,
                        endOffset,
                        type = listType,
                        symbol = arrayListCtor,
                        typeArgumentsCount = 1,
                        constructorTypeArgumentsCount = 0,
                        valueArgumentsCount = 1
                    ).apply {
                        // p0: CONST Int type=kotlin.Int value=2
                        putTypeArgument(0, pluginContext.irBuiltIns.stringType)
                        putValueArgument(
                            0,
                            IrConstImpl.int(
                                startOffset, endOffset,
                                type = pluginContext.irBuiltIns.intType,
                                value = SchemaCollector.properties.size
                            )
                        )
                    }
                )
                val listVar = irTemporary(nameHint = "list", value = initializeListWithSize.expression)
                val callIterator = IrExpressionBodyImpl(
                    startOffset, endOffset,
                    IrCallImpl(
                        startOffset, endOffset,
                        type = collectionIteratorType,
                        symbol = mapIteratorFunction.symbol,
                        typeArgumentsCount = 0,
                        valueArgumentsCount = 0
                    ).also {
                        it.dispatchReceiver =
                            IrCallImpl(
                                mapValuesProperty.owner.getter!!.startOffset,
                                mapValuesProperty.owner.getter!!.endOffset,
                                type = collectionType,
                                symbol = mapValuesProperty.owner.getter!!.symbol,
                                typeArgumentsCount = 0,
                                valueArgumentsCount = 0
                            ).apply {
                                dispatchReceiver = irCall(mediatorMappingProperty.getter!!).apply {
                                    dispatchReceiver = irGet(functionReceiver.dispatchReceiverParameter!!)
                                }
                            }
                    }
                )
                val mutableIteratorVar = irTemporary(nameHint = "iterator", value = callIterator.expression)

                val elementVar = irTemporary(irType = realmObjectCompanionIrClass.defaultType.makeNullable(), nameHint = "element", isMutable = true)

                +IrWhileLoopImpl(
                    startOffset, endOffset,
                    context.irBuiltIns.unitType, IrStatementOrigin.WHILE_LOOP
                ).apply {
                    condition = irCall(hasNextFunction).apply {
                        dispatchReceiver = irGet(mutableIteratorVar)
                    }
                    body = IrBlockBuilder(
                        context, scope, startOffset, endOffset,
                        resultType = pluginContext.irBuiltIns.unitType
                    ).irBlock {
                        +irSet(
                            elementVar.symbol,
                            IrCallImpl(
                                startOffset, endOffset,
                                type = realmObjectCompanionIrClass.defaultType,
                                symbol = nextFunction.symbol,
                                typeArgumentsCount = 0,
                                valueArgumentsCount = 0
                            ).apply {
                                dispatchReceiver = irGet(mutableIteratorVar)
                            }
                        )

                        +IrTypeOperatorCallImpl(
                            startOffset, endOffset,
                            type = pluginContext.irBuiltIns.unitType,
                            operator = IMPLICIT_COERCION_TO_UNIT,
                            typeOperand = pluginContext.irBuiltIns.unitType,
                            irCall(listAddFunction).apply {
                                dispatchReceiver = irGet(listVar)
                                putValueArgument(
                                    0,
                                    irCall(realmObjectCompanionRealmSchemaFunction).apply {
                                        dispatchReceiver = irGet(elementVar)
                                    }
                                )
                            }
                        )
                    }
                }
                +irReturn(irGet(listVar))
            }
        }
    }
}
