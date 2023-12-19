package io.realm.kotlin.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isBoolean
import org.jetbrains.kotlin.ir.types.isByteArray
import org.jetbrains.kotlin.ir.types.isDouble
import org.jetbrains.kotlin.ir.types.isFloat
import org.jetbrains.kotlin.ir.types.isLong
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.types.isSubtypeOfClass
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name


interface RealmPluginContext {
    val pluginContext: IrPluginContext
    val realmObjectHelper: IrClass
    val realmListClass: IrClass
    val realmSetClass: IrClass
    val realmDictionaryClass: IrClass
    val realmInstantClass: IrClass
    val realmBacklinksClass: IrClass
    val realmEmbeddedBacklinksClass: IrClass
    val realmObjectInterface: IrClassSymbol
    val embeddedRealmObjectInterface: IrClassSymbol

    // Attempt to find the interface for asymmetric objects.
    // The class will normally only be on the classpath for library-sync builds, not
    // library-base builds.
    val asymmetricRealmObjectInterface: IrClass?

    val objectIdClass: IrClass
    val decimal128Class: IrClass
    val realmObjectIdClass: IrClass
    val realmUUIDClass: IrClass
    val mutableRealmIntegerClass: IrClass
    val realmAnyClass: IrClass

    // Primitive (Core) type getters
    val getString: IrSimpleFunction
    val getLong: IrSimpleFunction
    val getBoolean: IrSimpleFunction
    val getFloat: IrSimpleFunction
    val getDouble: IrSimpleFunction
    val getDecimal128: IrSimpleFunction
    val getInstant: IrSimpleFunction
    val getObjectId: IrSimpleFunction
    val getUUID: IrSimpleFunction
    val getByteArray: IrSimpleFunction
    val getMutableInt: IrSimpleFunction
    val getRealmAny: IrSimpleFunction
    val getObject: IrSimpleFunction

    // Primitive (Core) type setters
    val setValue: IrSimpleFunction
    val setObject: IrSimpleFunction
    val setEmbeddedRealmObject: IrSimpleFunction

    // Getters and setters for collections
    val getList: IrSimpleFunction
    val setList: IrSimpleFunction
    val getSet: IrSimpleFunction
    val setSet: IrSimpleFunction
    val getDictionary: IrSimpleFunction
    val setDictionary: IrSimpleFunction

    // Top level SDK->Core converters
    val byteToLong: IrSimpleFunction
    val charToLong: IrSimpleFunction
    val shortToLong: IrSimpleFunction
    val intToLong: IrSimpleFunction

    // Top level Core->SDK converters
    val longToByte: IrSimpleFunction
    val longToChar: IrSimpleFunction
    val longToShort: IrSimpleFunction
    val longToInt: IrSimpleFunction
    val objectIdToRealmObjectId: IrSimpleFunction

    val providedAdapterFromRealm: IrSimpleFunction
    val providedAdapterToRealm: IrSimpleFunction

    val getTypeAdapter: IrSimpleFunction


    fun IrType.isRealmList(): Boolean {
        val propertyClassId: ClassId = this.classIdOrFail()
        val realmListClassId: ClassId? = realmListClass.classId
        return propertyClassId == realmListClassId
    }

    fun IrType.isRealmSet(): Boolean {
        val propertyClassId: ClassId = this.classIdOrFail()
        val realmSetClassId: ClassId? = realmSetClass.classId
        return propertyClassId == realmSetClassId
    }

    fun IrType.isRealmDictionary(): Boolean {
        val propertyClassId: ClassId = this.classIdOrFail()
        val realmDictionaryClassId: ClassId? = realmDictionaryClass.classId
        return propertyClassId == realmDictionaryClassId
    }

    fun IrType.isRealmInstant(): Boolean {
        val propertyClassId: ClassId = this.classIdOrFail()
        val realmInstantClassId: ClassId? = realmInstantClass.classId
        return propertyClassId == realmInstantClassId
    }

    fun IrType.isLinkingObject(): Boolean {
        val propertyClassId: ClassId = this.classIdOrFail()
        val realmBacklinksClassId: ClassId? = realmBacklinksClass.classId
        return propertyClassId == realmBacklinksClassId
    }

    fun IrType.isEmbeddedLinkingObject(): Boolean {
        val propertyClassId: ClassId = this.classIdOrFail()
        val realmEmbeddedBacklinksClassId: ClassId? = realmEmbeddedBacklinksClass.classId
        return propertyClassId == realmEmbeddedBacklinksClassId
    }

    fun IrType.isDecimal128(): Boolean {
        val propertyClassId: ClassId = this.classIdOrFail()
        val objectIdClassId: ClassId? = decimal128Class.classId
        return propertyClassId == objectIdClassId
    }

    fun IrType.isObjectId(): Boolean {
        val propertyClassId: ClassId = this.classIdOrFail()
        val objectIdClassId: ClassId? = objectIdClass.classId
        return propertyClassId == objectIdClassId
    }

    fun IrType.isRealmObjectId(): Boolean {
        val propertyClassId: ClassId = this.classIdOrFail()
        val objectIdClassId: ClassId? = realmObjectIdClass.classId
        return propertyClassId == objectIdClassId
    }

    fun IrType.hasSameClassId(other: IrType): Boolean {
        val propertyClassId: ClassId = this.classIdOrFail()
        val otherClassId = other.classIdOrFail()
        return propertyClassId == otherClassId
    }

    fun IrType.isRealmUUID(): Boolean {
        val propertyClassId: ClassId = this.classIdOrFail()
        val realmUUIDClassId: ClassId? = realmUUIDClass.classId
        return propertyClassId == realmUUIDClassId
    }

    fun IrType.isMutableRealmInteger(): Boolean {
        val propertyClassId: ClassId = this.classIdOrFail()
        val mutableRealmIntegerClassId: ClassId? = mutableRealmIntegerClass.classId
        return propertyClassId == mutableRealmIntegerClassId
    }

    fun IrType.isRealmAny(): Boolean {
        val propertyClassId: ClassId = this.classIdOrFail()
        val mutableRealmIntegerClassId: ClassId? = realmAnyClass.classId
        return propertyClassId == mutableRealmIntegerClassId
    }

    // TODO Clean up
    fun IrType.isValidPersistedType(): Boolean = isRealmAny() ||
            isByteArray() ||
            isString() ||
//    isLinkingObject() ||
//    isEmbeddedLinkingObject() ||
//    isPersistedPrimitiveType() ||
//    isMutableRealmInteger() ||
//    isByte() ||
//    isChar() ||
//    isShort() ||
//    isInt() ||
            isLong() ||
            isBoolean() ||
            isFloat() ||
            isDouble() ||
            isDecimal128() ||
            //    isEmbeddedLinkingObject() ||
            //    isLinkingObject() ||
            isRealmList() ||
            isRealmSet() ||
            isRealmDictionary() ||
            isRealmInstant() ||
            isObjectId() ||
            isRealmObjectId() ||
            isRealmUUID() ||
            isRealmList() ||
            isRealmSet() ||
            isRealmDictionary() ||
            isSubtypeOfClass(embeddedRealmObjectInterface) ||
            asymmetricRealmObjectInterface?.let { isSubtypeOfClass(it.symbol) } ?: false ||
            isSubtypeOfClass(realmObjectInterface)
}

class RealmPluginContextImpl(override val pluginContext: IrPluginContext): RealmPluginContext {
    override val realmObjectHelper: IrClass = pluginContext.lookupClassOrThrow(ClassIds.REALM_OBJECT_HELPER)
    override val realmListClass: IrClass = pluginContext.lookupClassOrThrow(ClassIds.REALM_LIST)
    override val realmSetClass: IrClass = pluginContext.lookupClassOrThrow(ClassIds.REALM_SET)
    override val realmDictionaryClass: IrClass = pluginContext.lookupClassOrThrow(ClassIds.REALM_DICTIONARY)
    override val realmInstantClass: IrClass = pluginContext.lookupClassOrThrow(ClassIds.REALM_INSTANT)
    override val realmBacklinksClass: IrClass = pluginContext.lookupClassOrThrow(ClassIds.REALM_BACKLINKS)
    override val realmEmbeddedBacklinksClass: IrClass =
        pluginContext.lookupClassOrThrow(ClassIds.REALM_EMBEDDED_BACKLINKS)
    override val realmObjectInterface =
        pluginContext.lookupClassOrThrow(ClassIds.REALM_OBJECT_INTERFACE).symbol
    override val embeddedRealmObjectInterface =
        pluginContext.lookupClassOrThrow(ClassIds.EMBEDDED_OBJECT_INTERFACE).symbol

    // Attempt to find the interface for asymmetric objects.
    // The class will normally only be on the classpath for library-sync builds, not
    // library-base builds.
    override val asymmetricRealmObjectInterface: IrClass? =
        pluginContext.referenceClass(ClassIds.ASYMMETRIC_OBJECT_INTERFACE)?.owner

    override val objectIdClass: IrClass = pluginContext.lookupClassOrThrow(ClassIds.KBSON_OBJECT_ID)
    override val decimal128Class: IrClass = pluginContext.lookupClassOrThrow(ClassIds.KBSON_DECIMAL128)
    override val realmObjectIdClass: IrClass = pluginContext.lookupClassOrThrow(ClassIds.REALM_OBJECT_ID)
    override val realmUUIDClass: IrClass = pluginContext.lookupClassOrThrow(ClassIds.REALM_UUID)
    override val mutableRealmIntegerClass: IrClass =
        pluginContext.lookupClassOrThrow(ClassIds.REALM_MUTABLE_INTEGER)
    override val realmAnyClass: IrClass = pluginContext.lookupClassOrThrow(ClassIds.REALM_ANY)

    // Primitive (Core) type getters
    override val getString: IrSimpleFunction =
        realmObjectHelper.lookupFunction(Names.REALM_ACCESSOR_HELPER_GET_STRING)
    override val getLong: IrSimpleFunction =
        realmObjectHelper.lookupFunction(Names.REALM_ACCESSOR_HELPER_GET_LONG)
    override val getBoolean: IrSimpleFunction =
        realmObjectHelper.lookupFunction(Names.REALM_ACCESSOR_HELPER_GET_BOOLEAN)
    override val getFloat: IrSimpleFunction =
        realmObjectHelper.lookupFunction(Names.REALM_ACCESSOR_HELPER_GET_FLOAT)
    override val getDouble: IrSimpleFunction =
        realmObjectHelper.lookupFunction(Names.REALM_ACCESSOR_HELPER_GET_DOUBLE)
    override val getDecimal128: IrSimpleFunction =
        realmObjectHelper.lookupFunction(Names.REALM_ACCESSOR_HELPER_GET_DECIMAL128)
    override val getInstant: IrSimpleFunction =
        realmObjectHelper.lookupFunction(Names.REALM_ACCESSOR_HELPER_GET_INSTANT)
    override val getObjectId: IrSimpleFunction =
        realmObjectHelper.lookupFunction(Names.REALM_ACCESSOR_HELPER_GET_OBJECT_ID)
    override val getUUID: IrSimpleFunction =
        realmObjectHelper.lookupFunction(Names.REALM_ACCESSOR_HELPER_GET_UUID)
    override val getByteArray: IrSimpleFunction =
        realmObjectHelper.lookupFunction(Names.REALM_ACCESSOR_HELPER_GET_BYTE_ARRAY)
    override val getMutableInt: IrSimpleFunction =
        realmObjectHelper.lookupFunction(Names.REALM_OBJECT_HELPER_GET_MUTABLE_INT)
    override val getRealmAny: IrSimpleFunction =
        realmObjectHelper.lookupFunction(Names.REALM_ACCESSOR_HELPER_GET_REALM_ANY)
    override val getObject: IrSimpleFunction =
        realmObjectHelper.lookupFunction(Names.REALM_OBJECT_HELPER_GET_OBJECT)

    // Primitive (Core) type setters
    override val setValue: IrSimpleFunction =
        realmObjectHelper.lookupFunction(Names.REALM_ACCESSOR_HELPER_SET_VALUE)
    override val setObject: IrSimpleFunction =
        realmObjectHelper.lookupFunction(Names.REALM_OBJECT_HELPER_SET_OBJECT)
    override val setEmbeddedRealmObject: IrSimpleFunction =
        realmObjectHelper.lookupFunction(Names.REALM_OBJECT_HELPER_SET_EMBEDDED_REALM_OBJECT)

    // Getters and setters for collections
    override val getList: IrSimpleFunction =
        realmObjectHelper.lookupFunction(Names.REALM_OBJECT_HELPER_GET_LIST)
    override val setList: IrSimpleFunction =
        realmObjectHelper.lookupFunction(Names.REALM_OBJECT_HELPER_SET_LIST)
    override val getSet: IrSimpleFunction =
        realmObjectHelper.lookupFunction(Names.REALM_OBJECT_HELPER_GET_SET)
    override val setSet: IrSimpleFunction =
        realmObjectHelper.lookupFunction(Names.REALM_OBJECT_HELPER_SET_SET)
    override val getDictionary: IrSimpleFunction =
        realmObjectHelper.lookupFunction(Names.REALM_OBJECT_HELPER_GET_DICTIONARY)
    override val setDictionary: IrSimpleFunction =
        realmObjectHelper.lookupFunction(Names.REALM_OBJECT_HELPER_SET_DICTIONARY)

    // Top level SDK->Core converters
    override val byteToLong: IrSimpleFunction =
        pluginContext.referenceFunctions(
            CallableId(
                FqName("io.realm.kotlin.internal"),
                Name.identifier("byteToLong")
            )
        ).first().owner
    override val charToLong: IrSimpleFunction =
        pluginContext.referenceFunctions(
            CallableId(
                FqName("io.realm.kotlin.internal"),
                Name.identifier("charToLong")
            )
        ).first().owner
    override val shortToLong: IrSimpleFunction =
        pluginContext.referenceFunctions(
            CallableId(
                FqName("io.realm.kotlin.internal"),
                Name.identifier("shortToLong")
            )
        ).first().owner
    override val intToLong: IrSimpleFunction =
        pluginContext.referenceFunctions(
            CallableId(
                FqName("io.realm.kotlin.internal"),
                Name.identifier("intToLong")
            )
        ).first().owner

    // Top level Core->SDK converters
    override val longToByte: IrSimpleFunction =
        pluginContext.referenceFunctions(CallableId(FqName("io.realm.kotlin.internal"), Name.identifier("longToByte"))).first().owner
    override val longToChar: IrSimpleFunction =
        pluginContext.referenceFunctions(CallableId(FqName("io.realm.kotlin.internal"), Name.identifier("longToChar"))).first().owner
    override val longToShort: IrSimpleFunction =
        pluginContext.referenceFunctions(CallableId(FqName("io.realm.kotlin.internal"), Name.identifier("longToShort"))).first().owner
    override val longToInt: IrSimpleFunction =
        pluginContext.referenceFunctions(CallableId(FqName("io.realm.kotlin.internal"), Name.identifier("longToInt"))).first().owner
    override val objectIdToRealmObjectId: IrSimpleFunction =
        pluginContext.referenceFunctions(CallableId(FqName("io.realm.kotlin.internal"), Name.identifier("objectIdToRealmObjectId"))).first().owner

    override val providedAdapterFromRealm: IrSimpleFunction =
        pluginContext.referenceFunctions(CallableId(FqName("io.realm.kotlin.internal"), Name.identifier("fromRealm"))).first().owner

    override val providedAdapterToRealm: IrSimpleFunction =
        pluginContext.referenceFunctions(CallableId(FqName("io.realm.kotlin.internal"), Name.identifier("toRealm"))).first().owner

    override val getTypeAdapter: IrSimpleFunction =
        pluginContext.referenceFunctions(CallableId(FqName("io.realm.kotlin.internal"), Name.identifier("getTypeAdapter"))).first().owner
}