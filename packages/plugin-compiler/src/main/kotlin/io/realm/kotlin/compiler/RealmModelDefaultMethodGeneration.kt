@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package io.realm.kotlin.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.name.Name

/**
 * Class responsible for adding Realm specific logic to the default object methods like:
 * - toString()
 * - hashCode()
 * - equals()
 *
 * WARNING: The current logic in here does not work well with incremental compilation. The reason
 * is that we check if these methods are "empty" before filling them out, and during incremental
 * compilation they already have content, and since all of these methods are using inlined
 * methods they will not pick up changes in the RealmObjectHelper.
 *
 * This should only impact us as SDK developers though, but it does mean that changes to
 * RealmObjectHelper methods will require a clean build to take effect.
 */
class RealmModelDefaultMethodGeneration(private val pluginContext: IrPluginContext) {

    private val realmObjectHelper: IrClass = pluginContext.lookupClassOrThrow(ClassIds.REALM_OBJECT_HELPER)
    private val realmToString: IrSimpleFunction = realmObjectHelper.lookupFunction(Name.identifier("realmToString"))
    private val realmEquals: IrSimpleFunction = realmObjectHelper.lookupFunction(Name.identifier("realmEquals"))
    private val realmHashCode: IrSimpleFunction = realmObjectHelper.lookupFunction(Name.identifier("realmHashCode"))
    private lateinit var objectReferenceProperty: IrProperty
    private lateinit var objectReferenceType: IrType

    fun addDefaultMethods(irClass: IrClass) {
        objectReferenceProperty = irClass.lookupProperty(Names.OBJECT_REFERENCE)
        objectReferenceType = objectReferenceProperty.backingField!!.type

        if (syntheticMethodExists(irClass, "toString")) {
            addToStringMethodBody(irClass)
        }
        if (syntheticMethodExists(irClass, "hashCode")) {
            addHashCodeMethod(irClass)
        }
        if (syntheticMethodExists(irClass, "equals")) {
            addEqualsMethod(irClass)
        }
    }

    /**
     * Checks if a synthetic method exists in the given class. Methods in super classes
     * are ignored, only methods actually declared in the class will return `true`.
     *
     * These methods are created by an earlier step by the Realm compiler plugin and are
     * recognized by not being fake and having an empty body.ß
     */
    private fun syntheticMethodExists(irClass: IrClass, methodName: String): Boolean {
        return irClass.functions.firstOrNull {
            !it.isFakeOverride && it.body == null && it.name == Name.identifier(methodName)
        } != null
    }

    private fun addEqualsMethod(irClass: IrClass) {
        val function: IrSimpleFunction = irClass.symbol.owner.functions.single { it.name.toString() == "equals" }
        function.body = pluginContext.blockBody(function.symbol) {
            +irReturn(
                IrCallImpl(
                    startOffset = startOffset,
                    endOffset = endOffset,
                    type = pluginContext.irBuiltIns.booleanType,
                    symbol = realmEquals.symbol,
                    typeArgumentsCount = 0,
                ).apply {
                    dispatchReceiver = irGetObject(realmObjectHelper.symbol)
                    putValueArgument(0, irGet(function.dispatchReceiverParameter!!.type, function.dispatchReceiverParameter!!.symbol))
                    putValueArgument(1, irGet(function.valueParameters[0].type, function.valueParameters[0].symbol))
                }
            )
        }
    }

    private fun addHashCodeMethod(irClass: IrClass) {
        val function: IrSimpleFunction = irClass.symbol.owner.functions.single { it.name.toString() == "hashCode" }
        function.body = pluginContext.blockBody(function.symbol) {
            +irReturn(
                IrCallImpl(
                    startOffset = startOffset,
                    endOffset = endOffset,
                    type = pluginContext.irBuiltIns.intType,
                    symbol = realmHashCode.symbol,
                    typeArgumentsCount = 0,
                ).apply {
                    dispatchReceiver = irGetObject(realmObjectHelper.symbol)
                    putValueArgument(0, irGet(function.dispatchReceiverParameter!!.type, function.dispatchReceiverParameter!!.symbol))
                }
            )
        }
    }

    private fun addToStringMethodBody(irClass: IrClass) {
        val function: IrSimpleFunction = irClass.symbol.owner.functions.single { it.name.toString() == "toString" }
        function.body = pluginContext.blockBody(function.symbol) {
            +irReturn(
                IrCallImpl(
                    startOffset = startOffset,
                    endOffset = endOffset,
                    type = pluginContext.irBuiltIns.stringType,
                    symbol = realmToString.symbol,
                    typeArgumentsCount = 0,
                ).apply {
                    dispatchReceiver = irGetObject(realmObjectHelper.symbol)
                    putValueArgument(0, irGet(function.dispatchReceiverParameter!!.type, function.dispatchReceiverParameter!!.symbol))
                }
            )
        }
    }
}
