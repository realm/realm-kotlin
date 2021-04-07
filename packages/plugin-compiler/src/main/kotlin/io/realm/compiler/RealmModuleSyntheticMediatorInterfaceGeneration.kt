package io.realm.compiler

import io.realm.compiler.FqNames.JAVA_UTIL_ARRAY_LIST
import io.realm.compiler.FqNames.KOTLIN_COLLECTIONS_ABSTRACT_COLLECTION
import io.realm.compiler.FqNames.KOTLIN_COLLECTIONS_ARRAY_LIST
import io.realm.compiler.FqNames.KOTLIN_COLLECTIONS_ITERATOR
import io.realm.compiler.FqNames.KOTLIN_COLLECTIONS_MAP
import io.realm.compiler.FqNames.KOTLIN_COLLECTIONS_MUTABLE_COLLECTION
import io.realm.compiler.FqNames.KOTLIN_COLLECTION_LIST
import io.realm.compiler.FqNames.KOTLIN_PAIR
import io.realm.compiler.FqNames.REALM_MEDIATOR_INTERFACE
import io.realm.compiler.FqNames.REALM_MODEL_COMPANION
import io.realm.compiler.Names.REALM_MEDIATOR_MAPPING_PROPERTY
import io.realm.compiler.Names.REALM_MEDIATOR_NEW_INSTANCE_METHOD
import io.realm.compiler.Names.REALM_MEDIATOR_SCHEMA_METHOD
import io.realm.compiler.Names.REALM_OBJECT_COMPANION_NEW_INSTANCE_METHOD
import io.realm.compiler.Names.REALM_OBJECT_COMPANION_SCHEMA_METHOD
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.builders.IrBlockBuilder
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSet
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrClass
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
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTypeOperatorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrWhileLoopImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.impl.IrTypeBase
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.isNative
import org.jetbrains.kotlin.types.typeUtil.isInt

class RealmModuleSyntheticMediatorInterfaceGeneration(private val pluginContext: IrPluginContext) {

    private val mapType = pluginContext.lookupClassOrThrow(KOTLIN_COLLECTIONS_MAP)
    private val mapValuesProperty = mapType.properties.first { it.name == Name.identifier("values") }.symbol
    private var mapIteratorFunction: IrSimpleFunction = pluginContext.lookupFunctionInClass(KOTLIN_COLLECTIONS_ABSTRACT_COLLECTION, "iterator")
    private val mapGetFunction: IrSimpleFunction =
        pluginContext.lookupFunctionInClass(KOTLIN_COLLECTIONS_MAP, "get")

    private val listIrClass: IrClass = pluginContext.lookupClassOrThrow(KOTLIN_COLLECTION_LIST)
    private var iteratorIrClass: IrClass = pluginContext.lookupClassOrThrow(KOTLIN_COLLECTIONS_ITERATOR)
    private val hasNextFunction = iteratorIrClass.lookupFunction(Name.identifier("hasNext"))
    private val nextFunction = iteratorIrClass.lookupFunction(Name.identifier("next"))

    private val pairCtor = pluginContext.lookupConstructorInClass(KOTLIN_PAIR) {
        it.owner.valueParameters.size == 2
    }

    private val mediatorIrClass = pluginContext.lookupClassOrThrow(REALM_MEDIATOR_INTERFACE)
    private val realmObjectCompanionIrClass: IrClass = pluginContext.lookupClassOrThrow(REALM_MODEL_COMPANION)
    private val companionMapKeyType = pluginContext.irBuiltIns.kCallableClass.starProjectedType
    private val companionMapValueType = realmObjectCompanionIrClass.defaultType
    private var companionMapType: IrSimpleType = mapType.typeWith(companionMapKeyType, companionMapValueType)
    private val companionMapEntryType = pluginContext.lookupClassOrThrow(KOTLIN_PAIR).typeWith(companionMapKeyType, companionMapValueType)

    private var collectionType: IrSimpleType = pluginContext.lookupClassOrThrow(KOTLIN_COLLECTIONS_MUTABLE_COLLECTION)
        .typeWith(arguments = listOf(realmObjectCompanionIrClass.symbol.defaultType))
    private val collectionIteratorType = iteratorIrClass.typeWith(arguments = listOf(realmObjectCompanionIrClass.symbol.defaultType))

    // platform dependent lookups
    // FIXME OPTIMIZE These could be eliminated by either calling listOf directly on the members or
    //  calling map if generating a list from iterating another container.
    private lateinit var arrayListIrClassSymbol: IrClassSymbol
    private lateinit var arrayListCtor: IrConstructorSymbol
    private var listAddFunction: IrSimpleFunction

    init {
        when {
            pluginContext.platform.isNative() -> {
                arrayListIrClassSymbol = pluginContext.lookupClassOrThrow(KOTLIN_COLLECTIONS_ARRAY_LIST).symbol
                arrayListCtor =
                    pluginContext.lookupConstructorInClass(KOTLIN_COLLECTIONS_ARRAY_LIST) {
                        it.owner.valueParameters.size == 1 && it.owner.valueParameters[0].type.isInt()
                    }
            }
            pluginContext.platform.isJvm() -> {
                arrayListIrClassSymbol =
                    pluginContext.lookupClassOrThrow(JAVA_UTIL_ARRAY_LIST).symbol
                arrayListCtor = pluginContext.lookupConstructorInClass(JAVA_UTIL_ARRAY_LIST) {
                    it.owner.valueParameters.size == 1 && (it.owner.valueParameters[0].type as IrTypeBase).kotlinType!!.isInt()
                }
            }
            else -> {
                logError("Unsupported platform ${pluginContext.platform}")
            }
        }
        listAddFunction = arrayListIrClassSymbol.owner.lookupFunction(Name.identifier("add"))
    }

    private val mediatorNewInstanceMethod: IrSimpleFunction = mediatorIrClass.lookupFunction(REALM_MEDIATOR_NEW_INSTANCE_METHOD)
    private val mediatorSchemaMethod: IrSimpleFunction = mediatorIrClass.lookupFunction(REALM_MEDIATOR_SCHEMA_METHOD)
    private val realmObjectCompanionNewInstanceFunction: IrSimpleFunction = realmObjectCompanionIrClass.lookupFunction(REALM_OBJECT_COMPANION_NEW_INSTANCE_METHOD)
    private val realmObjectCompanionRealmSchemaFunction: IrSimpleFunction = realmObjectCompanionIrClass.lookupFunction(REALM_OBJECT_COMPANION_SCHEMA_METHOD)

    @Suppress("ClassNaming")
    private object REALM_MEDIATOR_ORIGIN : IrDeclarationOriginImpl("MEDIATOR")

    @Suppress("LongMethod")
    @ObsoleteDescriptorBasedAPI
    fun addInterfaceMethodImplementation(irClass: IrClass, models: List<Triple<IrClassifierSymbol, IrType, IrClassSymbol>>): IrClass =
        // TODO move the implementation into the default Mediator interface, only the HashMap initializer block is specific per RealmModule
        irClass.apply {
            val mediatorMappingProperty = addValueProperty(
                pluginContext, mediatorIrClass, REALM_MEDIATOR_MAPPING_PROPERTY, companionMapType
            ) { startOffset, endOffset ->
                val mapOf = pluginContext.referenceFunctions(FqNames.KOTLIN_COLLECTIONS_MAPOF)
                    .first { it.owner.valueParameters.size == 1 && it.owner.valueParameters.first().isVararg }
                    IrCallImpl(
                        startOffset, endOffset,
                        companionMapType,
                        mapOf,
                        2,
                        1,
                        null,
                        null
                    ).apply {
                        putTypeArgument(0, companionMapKeyType)
                        putTypeArgument(1, companionMapValueType)
                        putValueArgument(
                            0,
                            IrVarargImpl(
                                startOffset,
                                endOffset,
                                pluginContext.irBuiltIns.arrayClass.typeWith(companionMapEntryType),
                                companionMapEntryType,
                                models.map { (irC: IrClassifierSymbol, type: IrType, symbol: IrClassSymbol) ->
                                    IrConstructorCallImpl(
                                        startOffset, endOffset,
                                        companionMapEntryType,
                                        pairCtor,
                                        2,
                                        0,
                                        2,
                                    ).apply {
                                        putTypeArgument(0, companionMapKeyType)
                                        putTypeArgument(1, companionMapValueType)
                                        putValueArgument(
                                            0,
                                            IrClassReferenceImpl(
                                                startOffset, endOffset,
                                                pluginContext.irBuiltIns.kClassClass.starProjectedType,
                                                irC,
                                                type
                                            )
                                        )
                                        putValueArgument(
                                            1,
                                            IrGetObjectValueImpl(
                                                startOffset,
                                                endOffset,
                                                realmObjectCompanionIrClass.defaultType,
                                                symbol
                                            )
                                        )
                                    }
                                }
                            )
                        )
                    }
            }
            addRealmMediatorSchemaMethod(mediatorMappingProperty)
            addRealmMediatorNewInstanceMethod(mediatorMappingProperty)
        }

    // TODO OPTIMIZE Could be done as a default method in Mediator instead to avoid having to
    //  maintain IR generation.
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
                val realmObjectCompanionType = companionMapValueType
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

    // TODO OPTIMIZE This schema is constant so we should add it as a backing field and return it.
    //  Could be done as a lazy evaluated default property in Mediator instead to avoid having to
    //  maintain IR generation.
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

                val elementVar = irTemporary(irType = companionMapValueType.makeNullable(), nameHint = "element", isMutable = true)

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
                                type = companionMapValueType,
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
