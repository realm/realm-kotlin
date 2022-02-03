/*
 * Copyright 2022 Realm Inc.
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

package io.realm.test.shared

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.realm.BaseRealm
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.entities.AllJavaTypes
import io.realm.entities.AllTypes
import io.realm.entities.CyclicType
import io.realm.entities.Dog
import io.realm.entities.NullTypes
import io.realm.entities.NullablePrimitiveFields
import io.realm.entities.Owner
import io.realm.entities.PrimaryKeyAsBoxedByte
import io.realm.entities.PrimaryKeyAsBoxedInteger
import io.realm.entities.PrimaryKeyAsBoxedLong
import io.realm.entities.PrimaryKeyAsBoxedShort
import io.realm.entities.PrimaryKeyAsByte
import io.realm.entities.PrimaryKeyAsInteger
import io.realm.entities.PrimaryKeyAsLong
import io.realm.entities.PrimaryKeyAsShort
import io.realm.entities.PrimaryKeyAsString
import io.realm.exceptions.RealmException
import org.bson.types.Decimal128
import org.bson.types.ObjectId
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.text.ParseException
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class DynamicRealmObjectTests {
//    @Rule
//    val configFactory: TestRealmConfigurationFactory = TestRealmConfigurationFactory()
//
//    @Rule
//    val thrown = ExpectedException.none()
//    private var realm: Realm? = null
//    private var dynamicRealm: DynamicRealm? = null
//    private var typedObj: AllJavaTypes? = null
//
//    // DynamicRealmObject constructed from a typed RealmObject
//    private var dObjTyped: DynamicRealmObject? = null
//
//    // DynamicRealmObject queried from DynamicRealm
//    private var dObjDynamic: DynamicRealmObject? = null
//    @Before
//    fun setUp() {
//        val realmConfig: RealmConfiguration = configFactory.createConfiguration()
//        realm = Realm.getInstance(realmConfig)
//        realm.beginTransaction()
//        typedObj = realm.createObject(AllJavaTypes::class.java, 1)
//        typedObj.setFieldString("str")
//        typedObj.setFieldShort(1.toShort())
//        typedObj.setFieldInt(1)
//        typedObj.setFieldLong(1)
//        typedObj.setFieldByte(4.toByte())
//        typedObj.setFieldFloat(1.23f)
//        typedObj.setFieldDouble(1.234)
//        typedObj.setFieldBinary(byteArrayOf(1, 2, 3))
//        typedObj.setFieldBoolean(true)
//        typedObj.setFieldDate(Date(1000))
//        typedObj.setFieldDecimal128(Decimal128(BigDecimal.TEN))
//        typedObj.setFieldObjectId(ObjectId(TestHelper.generateObjectIdHexString(7)))
//        typedObj.setFieldUUID(UUID.randomUUID())
//        typedObj.setFieldObject(typedObj)
//        typedObj.getFieldList().add(typedObj)
//        typedObj.getFieldIntegerList().add(1)
//        typedObj.getFieldStringList().add("str")
//        typedObj.getFieldBooleanList().add(true)
//        typedObj.getFieldFloatList().add(1.23f)
//        typedObj.getFieldDoubleList().add(1.234)
//        typedObj.getFieldBinaryList().add(byteArrayOf(1, 2, 3))
//        typedObj.getFieldDateList().add(Date(1000))
//        dObjTyped = DynamicRealmObject(typedObj)
//        realm.commitTransaction()
//        dynamicRealm = DynamicRealm.getInstance(realm!!.configuration)
//        dObjDynamic = dynamicRealm.where(AllJavaTypes.CLASS_NAME).findFirst()
//    }
//
//    @After
//    fun tearDown() {
//        if (realm != null) {
//            realm!!.close()
//        }
//        if (dynamicRealm != null) {
//            dynamicRealm.close()
//        }
//    }
//
//    // Types supported by the DynamicRealmObject.
//    private enum class SupportedType {
//        BOOLEAN, SHORT, INT, LONG, BYTE, FLOAT, DOUBLE, STRING, BINARY, DATE, OBJECT, DECIMAL128, OBJECT_ID, UUID, LIST, LIST_INTEGER, LIST_STRING, LIST_BOOLEAN, LIST_FLOAT, LIST_DOUBLE, LIST_BINARY, LIST_DATE, LIST_DECIMAL128, LIST_OBJECT_ID, LIST_UUID
//    }
//
//    private enum class ThreadConfinedMethods {
//        GET_BOOLEAN, GET_BYTE, GET_SHORT, GET_INT, GET_LONG, GET_FLOAT, GET_DOUBLE, GET_UUID, GET_BLOB, GET_STRING, GET_DATE, GET_DECIMAL128, GET_OBJECT_ID, GET_OBJECT, GET_LIST, GET_PRIMITIVE_LIST, GET, SET_BOOLEAN, SET_BYTE, SET_SHORT, SET_INT, SET_LONG, SET_FLOAT, SET_DOUBLE, SET_UUID, SET_BLOB, SET_STRING, SET_DATE, SET_DECIMAL128, SET_OBJECT_ID, SET_OBJECT, SET_LIST, SET_PRIMITIVE_LIST, SET, IS_NULL, SET_NULL, HAS_FIELD, GET_FIELD_NAMES, GET_TYPE, GET_FIELD_TYPE, HASH_CODE, EQUALS, TO_STRING
//    }
//
//    @Test
//    @Throws(Throwable::class)
//    fun callThreadConfinedMethodsFromWrongThread() {
//        dynamicRealm.beginTransaction()
//        dynamicRealm.deleteAll()
//        val obj: DynamicRealmObject = dynamicRealm.createObject(AllJavaTypes.CLASS_NAME, 100L)
//        dynamicRealm.commitTransaction()
//        val throwableFromThread = AtomicReference<Throwable>()
//        val testFinished = CountDownLatch(1)
//        val expectedMessage: String
//        try {
//            val expectedMessageField = BaseRealm::class.java.getDeclaredField("INCORRECT_THREAD_MESSAGE")
//            expectedMessageField.isAccessible = true
//            expectedMessage = expectedMessageField[null] as String
//        } catch (e: NoSuchFieldException) {
//            throw AssertionError(e)
//        } catch (e: IllegalAccessException) {
//            throw AssertionError(e)
//        }
//        val thread: Thread = object : Thread("callThreadConfinedMethodsFromWrongThread") {
//            override fun run() {
//                try {
//                    for (method in io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.values()) {
//                        try {
//                            callThreadConfinedMethod(obj, method)
//                            Assert.fail("IllegalStateException must be thrown.")
//                        } catch (e: IllegalStateException) {
//                            if (expectedMessage == e.message) {
//                                // expected exception
//                                continue
//                            }
//                            throwableFromThread.set(e)
//                            return
//                        }
//                    }
//                } finally {
//                    testFinished.countDown()
//                }
//            }
//        }
//        thread.start()
//        TestHelper.awaitOrFail(testFinished)
//        val throwable = throwableFromThread.get()
//        if (throwable != null) {
//            throw throwable
//        }
//    }
//
//    @Test(expected = IllegalArgumentException::class)
//    fun constructor_nullThrows() {
//        DynamicRealmObject(null as RealmObject?)
//    }
//
//    @Test(expected = IllegalArgumentException::class)
//    fun constructor_dynamicObjectThrows() {
//        DynamicRealmObject(dObjTyped)
//    }
//
//    @Test
//    fun constructor_deletedObjectThrows() {
//        realm.beginTransaction()
//        typedObj.deleteFromRealm()
//        realm.commitTransaction()
//        thrown.expect(IllegalArgumentException::class.java)
//        DynamicRealmObject(typedObj)
//    }
//
//    @Test(expected = IllegalArgumentException::class)
//    fun constructor_unmanagedObjectThrows() {
//        DynamicRealmObject(AllTypes())
//    }
//
//    // Tests that all getters fail if given invalid field name.
//    @Test
//    fun typedGetter_illegalFieldNameThrows() {
//        // Sets arguments.
//        val linkedField: String = AllJavaTypes.FIELD_OBJECT.toString() + "." + AllJavaTypes.FIELD_STRING
//        val arguments = Arrays.asList(null, "foo", AllJavaTypes.FIELD_STRING, linkedField)
//        val stringArguments = Arrays.asList(null, "foo", AllJavaTypes.FIELD_BOOLEAN, linkedField)
//
//        // Tests all getters.
//        for (type in io.realm.DynamicRealmObjectTests.SupportedType.values()) {
//
//            // We cannot modularize everything, so STRING is a special case with its own set
//            // of failing values. Only difference is the wrong type column has to be different.
//            val args = if (type == io.realm.DynamicRealmObjectTests.SupportedType.STRING) stringArguments else arguments
//            try {
//                callGetter(dObjTyped, type, args)
//                Assert.fail()
//            } catch (ignored: IllegalArgumentException) {
//            }
//            try {
//                callGetter(dObjDynamic, type, args)
//                Assert.fail()
//            } catch (ignored: IllegalArgumentException) {
//            }
//        }
//    }
//
//    @Test
//    fun typedGetter_wrongUnderlyingTypeThrows() {
//        for (type in io.realm.DynamicRealmObjectTests.SupportedType.values()) {
//            try {
//                // Makes sure we hit the wrong underlying type for all types.
//                if (type == io.realm.DynamicRealmObjectTests.SupportedType.DOUBLE) {
//                    callGetter(dObjTyped, type, Arrays.asList(AllJavaTypes.FIELD_STRING))
//                } else {
//                    callGetter(dObjTyped, type, Arrays.asList(AllJavaTypes.FIELD_DOUBLE))
//                }
//                Assert.fail(type.toString() + " failed to throw.")
//            } catch (ignored: IllegalArgumentException) {
//            }
//            try {
//                // Makes sure we hit the wrong underlying type for all types.
//                if (type == io.realm.DynamicRealmObjectTests.SupportedType.DOUBLE) {
//                    callGetter(dObjDynamic, type, Arrays.asList(AllJavaTypes.FIELD_STRING))
//                } else {
//                    callGetter(dObjDynamic, type, Arrays.asList(AllJavaTypes.FIELD_DOUBLE))
//                }
//                Assert.fail(type.toString() + " failed to throw.")
//            } catch (ignored: IllegalArgumentException) {
//            }
//        }
//    }
//
//    // Tests that all getters fail if given an invalid field name.
//    @Test
//    fun typedSetter_illegalFieldNameThrows() {
//
//        // Sets arguments.
//        val linkedField: String = AllJavaTypes.FIELD_OBJECT.toString() + "." + AllJavaTypes.FIELD_STRING
//        val arguments = Arrays.asList(null, "foo", AllJavaTypes.FIELD_STRING, linkedField)
//        val stringArguments = Arrays.asList(null, "foo", AllJavaTypes.FIELD_BOOLEAN, linkedField)
//
//        // Tests all getters.
//        for (type in io.realm.DynamicRealmObjectTests.SupportedType.values()) {
//            val args = if (type == io.realm.DynamicRealmObjectTests.SupportedType.STRING) stringArguments else arguments
//            try {
//                callSetter(dObjTyped, type, args)
//                Assert.fail()
//            } catch (ignored: IllegalArgumentException) {
//            }
//            try {
//                callSetter(dObjDynamic, type, args)
//                Assert.fail()
//            } catch (ignored: IllegalArgumentException) {
//            }
//        }
//    }
//
//    @Test
//    fun typedSetter_wrongUnderlyingTypeThrows() {
//        for (type in io.realm.DynamicRealmObjectTests.SupportedType.values()) {
//            realm.beginTransaction()
//            try {
//                // Makes sure we hit the wrong underlying type for all types.
//                if (type == io.realm.DynamicRealmObjectTests.SupportedType.STRING) {
//                    callSetter(dObjTyped, type, Arrays.asList(AllJavaTypes.FIELD_BOOLEAN))
//                } else {
//                    callSetter(dObjTyped, type, Arrays.asList(AllJavaTypes.FIELD_STRING))
//                }
//                Assert.fail()
//            } catch (ignored: IllegalArgumentException) {
//            } finally {
//                realm.cancelTransaction()
//            }
//            dynamicRealm.beginTransaction()
//            try {
//                // Makes sure we hit the wrong underlying type for all types.
//                if (type == io.realm.DynamicRealmObjectTests.SupportedType.STRING) {
//                    callSetter(dObjDynamic, type, Arrays.asList(AllJavaTypes.FIELD_BOOLEAN))
//                } else {
//                    callSetter(dObjDynamic, type, Arrays.asList(AllJavaTypes.FIELD_STRING))
//                }
//                Assert.fail()
//            } catch (ignored: IllegalArgumentException) {
//            } finally {
//                dynamicRealm.cancelTransaction()
//            }
//        }
//    }
//
//    private fun callSetterOnPrimaryKey(className: String, `object`: DynamicRealmObject) {
//        when (className) {
//            PrimaryKeyAsByte.CLASS_NAME -> `object`.setByte(PrimaryKeyAsByte.FIELD_ID, 42.toByte())
//            PrimaryKeyAsShort.CLASS_NAME -> `object`.setShort(PrimaryKeyAsShort.FIELD_ID, 42.toShort())
//            PrimaryKeyAsInteger.CLASS_NAME -> `object`.setInt(PrimaryKeyAsInteger.FIELD_ID, 42)
//            PrimaryKeyAsLong.CLASS_NAME -> `object`.setLong(PrimaryKeyAsLong.FIELD_ID, 42)
//            PrimaryKeyAsString.CLASS_NAME -> `object`.setString(PrimaryKeyAsString.FIELD_PRIMARY_KEY, "42")
//            else -> Assert.fail()
//        }
//    }
//
//    @Test
//    fun typedSetter_changePrimaryKeyThrows() {
//        val primaryKeyClasses = arrayOf<String>(PrimaryKeyAsByte.CLASS_NAME, PrimaryKeyAsShort.CLASS_NAME,
//            PrimaryKeyAsInteger.CLASS_NAME, PrimaryKeyAsLong.CLASS_NAME, PrimaryKeyAsString.CLASS_NAME)
//        for (pkClass in primaryKeyClasses) {
//            dynamicRealm.beginTransaction()
//            var `object`: DynamicRealmObject
//            `object` = if (pkClass == PrimaryKeyAsString.CLASS_NAME) {
//                dynamicRealm.createObject(pkClass, "")
//            } else {
//                dynamicRealm.createObject(pkClass, 0)
//            }
//            try {
//                callSetterOnPrimaryKey(pkClass, `object`)
//                Assert.fail()
//            } catch (ignored: IllegalArgumentException) {
//            }
//            dynamicRealm.cancelTransaction()
//        }
//    }
//
//    // Tests all typed setters/setters.
//    @Test
//    fun typedGettersAndSetters() {
//        realm.beginTransaction()
//        val obj: AllJavaTypes = realm.createObject(AllJavaTypes::class.java, 0)
//        val dObj = DynamicRealmObject(obj)
//        try {
//            for (type in io.realm.DynamicRealmObjectTests.SupportedType.values()) {
//                when (type) {
//                    io.realm.DynamicRealmObjectTests.SupportedType.BOOLEAN -> {
//                        dObj.setBoolean(AllJavaTypes.FIELD_BOOLEAN, true)
//                        Assert.assertTrue(dObj.getBoolean(AllJavaTypes.FIELD_BOOLEAN))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.SHORT -> {
//                        dObj.setShort(AllJavaTypes.FIELD_SHORT, 42.toShort())
//                        assertEquals(42, dObj.getShort(AllJavaTypes.FIELD_SHORT))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.INT -> {
//                        dObj.setInt(AllJavaTypes.FIELD_INT, 42)
//                        assertEquals(42, dObj.getInt(AllJavaTypes.FIELD_INT))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LONG -> {
//                        dObj.setLong(AllJavaTypes.FIELD_LONG, 42L)
//                        assertEquals(42, dObj.getLong(AllJavaTypes.FIELD_LONG))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.BYTE -> {
//                        dObj.setByte(AllJavaTypes.FIELD_BYTE, 4.toByte())
//                        assertEquals(4, dObj.getByte(AllJavaTypes.FIELD_BYTE))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.FLOAT -> {
//                        dObj.setFloat(AllJavaTypes.FIELD_FLOAT, 1.23f)
//                        assertEquals(1.23f, dObj.getFloat(AllJavaTypes.FIELD_FLOAT), 0f)
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.DOUBLE -> {
//                        dObj.setDouble(AllJavaTypes.FIELD_DOUBLE, 1.234)
//                        assertEquals(1.234, dObj.getDouble(AllJavaTypes.FIELD_DOUBLE), 0.0)
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.STRING -> {
//                        dObj.setString(AllJavaTypes.FIELD_STRING, "str")
//                        assertEquals("str", dObj.getString(AllJavaTypes.FIELD_STRING))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.BINARY -> {
//                        dObj.setBlob(AllJavaTypes.FIELD_BINARY, byteArrayOf(1, 2, 3))
//                        assertArrayEquals(byteArrayOf(1, 2, 3), dObj.getBlob(AllJavaTypes.FIELD_BINARY))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.DATE -> {
//                        dObj.setDate(AllJavaTypes.FIELD_DATE, Date(1000))
//                        assertEquals(Date(1000), dObj.getDate(AllJavaTypes.FIELD_DATE))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.DECIMAL128 -> {
//                        dObj.setDecimal128(AllJavaTypes.FIELD_DECIMAL128, Decimal128(BigDecimal.ONE))
//                        assertEquals(Decimal128(BigDecimal.ONE), dObj.getDecimal128(AllJavaTypes.FIELD_DECIMAL128))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.OBJECT_ID -> {
//                        dObj.setObjectId(AllJavaTypes.FIELD_OBJECT_ID, ObjectId(TestHelper.generateObjectIdHexString(0)))
//                        assertEquals(ObjectId(TestHelper.generateObjectIdHexString(0)), dObj.getObjectId(AllJavaTypes.FIELD_OBJECT_ID))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.UUID -> {
//                        val uuid = UUID.randomUUID().toString()
//                        dObj.setUUID(AllJavaTypes.FIELD_UUID, UUID.fromString(uuid))
//                        assertEquals(UUID.fromString(uuid), dObj.getUUID(AllJavaTypes.FIELD_UUID))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.OBJECT -> {
//                        dObj.setObject(AllJavaTypes.FIELD_OBJECT, dObj)
//                        assertEquals(dObj, dObj.getObject(AllJavaTypes.FIELD_OBJECT))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_INTEGER -> checkSetGetValueList(dObj, AllJavaTypes.FIELD_INTEGER_LIST, Int::class.java, RealmList<Int>(null, 1))
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_STRING -> checkSetGetValueList(dObj, AllJavaTypes.FIELD_STRING_LIST, String::class.java, RealmList<String>(null, "foo"))
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_BOOLEAN -> checkSetGetValueList(dObj, AllJavaTypes.FIELD_BOOLEAN_LIST, Boolean::class.java, RealmList<Boolean>(null, true))
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_FLOAT -> checkSetGetValueList(dObj, AllJavaTypes.FIELD_FLOAT_LIST, Float::class.java, RealmList<Float>(null, 1.23f))
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_DOUBLE -> checkSetGetValueList(dObj, AllJavaTypes.FIELD_DOUBLE_LIST, Double::class.java, RealmList<Double>(null, 1.234))
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_BINARY -> checkSetGetValueList(dObj, AllJavaTypes.FIELD_BINARY_LIST, ByteArray::class.java, RealmList<ByteArray>(null, byteArrayOf(1, 2, 3)))
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_DATE -> checkSetGetValueList(dObj, AllJavaTypes.FIELD_DATE_LIST, Date::class.java, RealmList<Date>(null, Date(1000)))
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_DECIMAL128 -> checkSetGetValueList<E>(dObj, AllJavaTypes.FIELD_DECIMAL128_LIST, Decimal128::class.java, RealmList<Any>(null, Decimal128(BigDecimal.ONE)))
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_OBJECT_ID -> checkSetGetValueList<E>(dObj, AllJavaTypes.FIELD_OBJECT_ID_LIST, ObjectId::class.java, RealmList<Any>(null, ObjectId(TestHelper.generateObjectIdHexString(0))))
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_UUID -> checkSetGetValueList(dObj, AllJavaTypes.FIELD_UUID_LIST, UUID::class.java, RealmList<UUID>(null, UUID.randomUUID()))
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST -> {}
//                    else -> Assert.fail()
//                }
//            }
//        } finally {
//            realm.cancelTransaction()
//        }
//    }
//
//    private fun <E> checkSetGetValueList(obj: DynamicRealmObject, fieldName: String, primitiveType: Class<E>, list: RealmList<E>) {
//        obj.set(fieldName, list)
//        assertArrayEquals(list.toTypedArray(), obj.getList(fieldName, primitiveType).toArray())
//    }
//
//    @Test
//    fun setter_null() {
//        realm.beginTransaction()
//        val obj: NullTypes = realm.createObject(NullTypes::class.java, 0)
//        val dObj = DynamicRealmObject(obj)
//        try {
//            for (type in io.realm.DynamicRealmObjectTests.SupportedType.values()) {
//                when (type) {
//                    io.realm.DynamicRealmObjectTests.SupportedType.OBJECT -> {
//                        val childObj = NullTypes()
//                        childObj.setId(1)
//                        val dynamicChildObject = DynamicRealmObject(realm.copyToRealm(childObj))
//                        dObj.setObject(NullTypes.FIELD_OBJECT_NULL, dynamicChildObject)
//                        Assert.assertNotNull(dObj.getObject(NullTypes.FIELD_OBJECT_NULL))
//                        dObj.setNull(NullTypes.FIELD_OBJECT_NULL)
//                        Assert.assertNull(dObj.getObject(NullTypes.FIELD_OBJECT_NULL))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST -> try {
//                        dObj.setNull(NullTypes.FIELD_LIST_NULL)
//                        Assert.fail()
//                    } catch (ignored: IllegalArgumentException) {
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_INTEGER -> try {
//                        dObj.setNull(NullTypes.FIELD_INTEGER_LIST_NULL)
//                        Assert.fail()
//                    } catch (ignored: IllegalArgumentException) {
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_STRING -> try {
//                        dObj.setNull(NullTypes.FIELD_STRING_LIST_NULL)
//                        Assert.fail()
//                    } catch (ignored: IllegalArgumentException) {
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_BOOLEAN -> try {
//                        dObj.setNull(NullTypes.FIELD_BOOLEAN_LIST_NULL)
//                        Assert.fail()
//                    } catch (ignored: IllegalArgumentException) {
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_FLOAT -> try {
//                        dObj.setNull(NullTypes.FIELD_FLOAT_LIST_NULL)
//                        Assert.fail()
//                    } catch (ignored: IllegalArgumentException) {
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_DOUBLE -> try {
//                        dObj.setNull(NullTypes.FIELD_DOUBLE_LIST_NULL)
//                        Assert.fail()
//                    } catch (ignored: IllegalArgumentException) {
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_BINARY -> try {
//                        dObj.setNull(NullTypes.FIELD_BINARY_LIST_NULL)
//                        Assert.fail()
//                    } catch (ignored: IllegalArgumentException) {
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_DATE -> try {
//                        dObj.setNull(NullTypes.FIELD_DATE_LIST_NULL)
//                        Assert.fail()
//                    } catch (ignored: IllegalArgumentException) {
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_DECIMAL128 -> try {
//                        dObj.setNull(NullTypes.FIELD_DECIMAL128_LIST_NULL)
//                        Assert.fail()
//                    } catch (ignored: IllegalArgumentException) {
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_OBJECT_ID -> try {
//                        dObj.setNull(NullTypes.FIELD_OBJECT_ID_LIST_NULL)
//                        Assert.fail()
//                    } catch (ignored: IllegalArgumentException) {
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_UUID -> try {
//                        dObj.setNull(NullTypes.FIELD_UUID_LIST_NULL)
//                        Assert.fail()
//                    } catch (ignored: IllegalArgumentException) {
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.BOOLEAN -> {
//                        dObj.setNull(NullTypes.FIELD_BOOLEAN_NULL)
//                        Assert.assertTrue(dObj.isNull(NullTypes.FIELD_BOOLEAN_NULL))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.BYTE -> {
//                        dObj.setNull(NullTypes.FIELD_BYTE_NULL)
//                        Assert.assertTrue(dObj.isNull(NullTypes.FIELD_BYTE_NULL))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.SHORT -> {
//                        dObj.setNull(NullTypes.FIELD_SHORT_NULL)
//                        Assert.assertTrue(dObj.isNull(NullTypes.FIELD_SHORT_NULL))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.INT -> {
//                        dObj.setNull(NullTypes.FIELD_INTEGER_NULL)
//                        Assert.assertTrue(dObj.isNull(NullTypes.FIELD_INTEGER_NULL))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LONG -> {
//                        dObj.setNull(NullTypes.FIELD_LONG_NULL)
//                        Assert.assertTrue(dObj.isNull(NullTypes.FIELD_LONG_NULL))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.FLOAT -> {
//                        dObj.setNull(NullTypes.FIELD_FLOAT_NULL)
//                        Assert.assertTrue(dObj.isNull(NullTypes.FIELD_FLOAT_NULL))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.DOUBLE -> {
//                        dObj.setNull(NullTypes.FIELD_DOUBLE_NULL)
//                        Assert.assertTrue(dObj.isNull(NullTypes.FIELD_DOUBLE_NULL))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.STRING -> {
//                        dObj.setNull(NullTypes.FIELD_STRING_NULL)
//                        Assert.assertTrue(dObj.isNull(NullTypes.FIELD_STRING_NULL))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.BINARY -> {
//                        dObj.setNull(NullTypes.FIELD_BYTES_NULL)
//                        Assert.assertTrue(dObj.isNull(NullTypes.FIELD_BYTES_NULL))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.DATE -> {
//                        dObj.setNull(NullTypes.FIELD_DATE_NULL)
//                        Assert.assertTrue(dObj.isNull(NullTypes.FIELD_DATE_NULL))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.DECIMAL128 -> {
//                        dObj.setNull(NullTypes.FIELD_DECIMAL128_NULL)
//                        Assert.assertTrue(dObj.isNull(NullTypes.FIELD_DECIMAL128_NULL))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.OBJECT_ID -> {
//                        dObj.setNull(NullTypes.FIELD_OBJECT_ID_NULL)
//                        Assert.assertTrue(dObj.isNull(NullTypes.FIELD_OBJECT_ID_NULL))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.UUID -> {
//                        dObj.setNull(NullTypes.FIELD_UUID_NULL)
//                        Assert.assertTrue(dObj.isNull(NullTypes.FIELD_UUID_NULL))
//                    }
//                    else -> Assert.fail("Unknown type: $type")
//                }
//            }
//        } finally {
//            realm.cancelTransaction()
//        }
//    }
//
//    @Test
//    fun setter_nullOnRequiredFieldsThrows() {
//        realm.beginTransaction()
//        val obj: NullTypes = realm.createObject(NullTypes::class.java, 0)
//        val dObj = DynamicRealmObject(obj)
//        try {
//            for (type in io.realm.DynamicRealmObjectTests.SupportedType.values()) {
//                var fieldName: String? = null
//                try {
//                    when (type) {
//                        io.realm.DynamicRealmObjectTests.SupportedType.OBJECT -> continue  // Ignore
//                        io.realm.DynamicRealmObjectTests.SupportedType.LIST -> fieldName = NullTypes.FIELD_LIST_NULL
//                        io.realm.DynamicRealmObjectTests.SupportedType.LIST_INTEGER -> fieldName = NullTypes.FIELD_INTEGER_LIST_NULL
//                        io.realm.DynamicRealmObjectTests.SupportedType.LIST_STRING -> fieldName = NullTypes.FIELD_STRING_LIST_NULL
//                        io.realm.DynamicRealmObjectTests.SupportedType.LIST_BOOLEAN -> fieldName = NullTypes.FIELD_BOOLEAN_LIST_NULL
//                        io.realm.DynamicRealmObjectTests.SupportedType.LIST_FLOAT -> fieldName = NullTypes.FIELD_FLOAT_LIST_NULL
//                        io.realm.DynamicRealmObjectTests.SupportedType.LIST_DOUBLE -> fieldName = NullTypes.FIELD_DOUBLE_LIST_NULL
//                        io.realm.DynamicRealmObjectTests.SupportedType.LIST_BINARY -> fieldName = NullTypes.FIELD_BINARY_LIST_NULL
//                        io.realm.DynamicRealmObjectTests.SupportedType.LIST_DATE -> fieldName = NullTypes.FIELD_DATE_LIST_NULL
//                        io.realm.DynamicRealmObjectTests.SupportedType.LIST_DECIMAL128 -> fieldName = NullTypes.FIELD_DECIMAL128_LIST_NULL
//                        io.realm.DynamicRealmObjectTests.SupportedType.LIST_OBJECT_ID -> fieldName = NullTypes.FIELD_OBJECT_ID_LIST_NULL
//                        io.realm.DynamicRealmObjectTests.SupportedType.LIST_UUID -> fieldName = NullTypes.FIELD_UUID_LIST_NULL
//                        io.realm.DynamicRealmObjectTests.SupportedType.BOOLEAN -> fieldName = NullTypes.FIELD_BOOLEAN_NOT_NULL
//                        io.realm.DynamicRealmObjectTests.SupportedType.BYTE -> fieldName = NullTypes.FIELD_BYTE_NOT_NULL
//                        io.realm.DynamicRealmObjectTests.SupportedType.SHORT -> fieldName = NullTypes.FIELD_SHORT_NOT_NULL
//                        io.realm.DynamicRealmObjectTests.SupportedType.INT -> fieldName = NullTypes.FIELD_INTEGER_NOT_NULL
//                        io.realm.DynamicRealmObjectTests.SupportedType.LONG -> fieldName = NullTypes.FIELD_LONG_NOT_NULL
//                        io.realm.DynamicRealmObjectTests.SupportedType.FLOAT -> fieldName = NullTypes.FIELD_FLOAT_NOT_NULL
//                        io.realm.DynamicRealmObjectTests.SupportedType.DOUBLE -> fieldName = NullTypes.FIELD_DOUBLE_NOT_NULL
//                        io.realm.DynamicRealmObjectTests.SupportedType.STRING -> fieldName = NullTypes.FIELD_STRING_NOT_NULL
//                        io.realm.DynamicRealmObjectTests.SupportedType.BINARY -> fieldName = NullTypes.FIELD_BYTES_NOT_NULL
//                        io.realm.DynamicRealmObjectTests.SupportedType.DATE -> fieldName = NullTypes.FIELD_DATE_NOT_NULL
//                        io.realm.DynamicRealmObjectTests.SupportedType.DECIMAL128 -> fieldName = NullTypes.FIELD_DECIMAL128_NOT_NULL
//                        io.realm.DynamicRealmObjectTests.SupportedType.OBJECT_ID -> fieldName = NullTypes.FIELD_OBJECT_ID_NOT_NULL
//                        io.realm.DynamicRealmObjectTests.SupportedType.UUID -> fieldName = NullTypes.FIELD_UUID_NOT_NULL
//                        else -> Assert.fail("Unknown type: $type")
//                    }
//                    dObj.setNull(fieldName)
//                    Assert.fail("Setting value to null should throw: $type")
//                } catch (ignored: IllegalArgumentException) {
//                    Assert.assertTrue(ignored.message!!.contains(fieldName!!))
//                }
//            }
//        } finally {
//            realm.cancelTransaction()
//        }
//    }
//
//    // Tests types where you can set null using the typed setter instead of using setNull().
//    @Test
//    fun typedSetter_null() {
//        realm.beginTransaction()
//        val obj: NullTypes = realm.createObject(NullTypes::class.java, 0)
//        val dObj = DynamicRealmObject(obj)
//        try {
//            for (type in io.realm.DynamicRealmObjectTests.SupportedType.values()) {
//                when (type) {
//                    io.realm.DynamicRealmObjectTests.SupportedType.OBJECT -> {
//                        dObj.setObject(NullTypes.FIELD_OBJECT_NULL, null)
//                        Assert.assertNull(dObj.getObject(NullTypes.FIELD_OBJECT_NULL))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST -> try {
//                        dObj.setList(NullTypes.FIELD_LIST_NULL, null)
//                        Assert.fail()
//                    } catch (ignored: IllegalArgumentException) {
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_INTEGER -> try {
//                        dObj.setList(NullTypes.FIELD_INTEGER_LIST_NULL, null)
//                        Assert.fail()
//                    } catch (ignored: IllegalArgumentException) {
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_STRING -> try {
//                        dObj.setList(NullTypes.FIELD_STRING_LIST_NULL, null)
//                        Assert.fail()
//                    } catch (ignored: IllegalArgumentException) {
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_BOOLEAN -> try {
//                        dObj.setList(NullTypes.FIELD_BOOLEAN_LIST_NULL, null)
//                        Assert.fail()
//                    } catch (ignored: IllegalArgumentException) {
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_FLOAT -> try {
//                        dObj.setList(NullTypes.FIELD_FLOAT_LIST_NULL, null)
//                        Assert.fail()
//                    } catch (ignored: IllegalArgumentException) {
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_DOUBLE -> try {
//                        dObj.setList(NullTypes.FIELD_DOUBLE_LIST_NULL, null)
//                        Assert.fail()
//                    } catch (ignored: IllegalArgumentException) {
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_BINARY -> try {
//                        dObj.setList(NullTypes.FIELD_BINARY_LIST_NULL, null)
//                        Assert.fail()
//                    } catch (ignored: IllegalArgumentException) {
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_DATE -> try {
//                        dObj.setList(NullTypes.FIELD_DATE_LIST_NULL, null)
//                        Assert.fail()
//                    } catch (ignored: IllegalArgumentException) {
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.DATE -> {
//                        dObj.setDate(NullTypes.FIELD_DATE_NULL, null)
//                        Assert.assertNull(dObj.getDate(NullTypes.FIELD_DATE_NULL))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.STRING -> {
//                        dObj.setString(NullTypes.FIELD_STRING_NULL, null)
//                        Assert.assertNull(dObj.getString(NullTypes.FIELD_STRING_NULL))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.BINARY -> {
//                        dObj.setBlob(NullTypes.FIELD_BYTES_NULL, null)
//                        Assert.assertNull(dObj.getBlob(NullTypes.FIELD_BYTES_NULL))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.BOOLEAN, io.realm.DynamicRealmObjectTests.SupportedType.SHORT, io.realm.DynamicRealmObjectTests.SupportedType.INT, io.realm.DynamicRealmObjectTests.SupportedType.LONG, io.realm.DynamicRealmObjectTests.SupportedType.FLOAT, io.realm.DynamicRealmObjectTests.SupportedType.DOUBLE -> {}
//                    else -> {}
//                }
//            }
//        } finally {
//            realm.cancelTransaction()
//        }
//    }
//
//    @Test
//    fun setNull_changePrimaryKeyThrows() {
//        val primaryKeyClasses = arrayOf<String>(PrimaryKeyAsBoxedByte.CLASS_NAME, PrimaryKeyAsBoxedShort.CLASS_NAME,
//            PrimaryKeyAsBoxedInteger.CLASS_NAME, PrimaryKeyAsBoxedLong.CLASS_NAME, PrimaryKeyAsString.CLASS_NAME)
//        for (pkClass in primaryKeyClasses) {
//            dynamicRealm.beginTransaction()
//            var `object`: DynamicRealmObject
//            val isStringPK = pkClass == PrimaryKeyAsString.CLASS_NAME
//            `object` = if (isStringPK) {
//                dynamicRealm.createObject(pkClass, "")
//            } else {
//                dynamicRealm.createObject(pkClass, 0)
//            }
//            try {
//                `object`.setNull(if (isStringPK) PrimaryKeyAsString.FIELD_PRIMARY_KEY else "id")
//                Assert.fail()
//            } catch (ignored: IllegalArgumentException) {
//            }
//            dynamicRealm.cancelTransaction()
//        }
//    }
//
//    @Test
//    fun setObject_differentType() {
//        realm.beginTransaction()
//        val dog = DynamicRealmObject(realm.createObject(Dog::class.java))
//        var owner = DynamicRealmObject(realm.createObject(Owner::class.java))
//        owner.setString("name", "John")
//        dog.setObject("owner", owner)
//        realm.commitTransaction()
//        owner = dog.getObject("owner")
//        Assert.assertNotNull(owner)
//        assertEquals("John", owner.getString("name"))
//    }
//
//    @Test
//    fun setObject_wrongTypeThrows() {
//        realm.beginTransaction()
//        val obj: AllJavaTypes = realm.createObject(AllJavaTypes::class.java, 0)
//        val otherObj: Dog = realm.createObject(Dog::class.java)
//        val dynamicObj = DynamicRealmObject(obj)
//        val dynamicWrongType = DynamicRealmObject(otherObj)
//        thrown.expect(IllegalArgumentException::class.java)
//        dynamicObj.setObject(AllJavaTypes.FIELD_OBJECT, dynamicWrongType)
//    }
//
//    @Test
//    fun setObject_objectBelongToTypedRealmThrows() {
//        dynamicRealm.beginTransaction()
//        thrown.expect(IllegalArgumentException::class.java)
//        thrown.expectMessage("Cannot add an object from another Realm instance.")
//        dynamicRealm.where(AllJavaTypes.CLASS_NAME).findFirst().setObject(AllJavaTypes.FIELD_OBJECT, dObjTyped)
//        dynamicRealm.cancelTransaction()
//    }
//
//    @Test
//    fun setObject_objectBelongToDiffThreadRealmThrows() {
//        val finishedLatch = CountDownLatch(1)
//        Thread {
//            val dynamicRealm: DynamicRealm = DynamicRealm.getInstance(realm!!.configuration)
//            dynamicRealm.beginTransaction()
//            try {
//                // ExpectedException doesn't work in another thread.
//                dynamicRealm.where(AllJavaTypes.CLASS_NAME).findFirst()
//                    .setObject(AllJavaTypes.FIELD_OBJECT, dObjDynamic)
//                Assert.fail()
//            } catch (expected: IllegalArgumentException) {
//                Assert.assertEquals("Cannot add an object from another Realm instance.", expected.message)
//            }
//            dynamicRealm.cancelTransaction()
//            dynamicRealm.close()
//            finishedLatch.countDown()
//        }.start()
//        TestHelper.awaitOrFail(finishedLatch)
//    }
//
//    @Test
//    fun setList_listWithDynamicRealmObject() {
//        val dynamicRealm: DynamicRealm = DynamicRealm.getInstance(realm!!.configuration)
//        dynamicRealm.beginTransaction()
//        var allTypes: DynamicRealmObject = dynamicRealm.createObject(AllTypes.CLASS_NAME)
//        allTypes.setString(AllTypes.FIELD_STRING, "bender")
//        val dog: DynamicRealmObject = dynamicRealm.createObject(Dog.CLASS_NAME)
//        dog.setString(Dog.FIELD_NAME, "nibbler")
//        val list: RealmList<DynamicRealmObject> = RealmList<DynamicRealmObject>()
//        list.add(dog)
//        allTypes.setList(AllTypes.FIELD_REALMLIST, list)
//        dynamicRealm.commitTransaction()
//        allTypes = dynamicRealm.where(AllTypes.CLASS_NAME)
//            .equalTo(AllTypes.FIELD_STRING, "bender")
//            .findFirst()
//        assertEquals("nibbler", allTypes.getList(AllTypes.FIELD_REALMLIST).first().get(Dog.FIELD_NAME))
//        dynamicRealm.close()
//    }
//
//    @Test
//    fun setList_managedRealmList() {
//        dynamicRealm.executeTransaction(object : Transaction() {
//            fun execute(realm: DynamicRealm) {
//                realm.deleteAll()
//                val allTypes: DynamicRealmObject = realm.createObject(AllTypes.CLASS_NAME)
//                allTypes.setString(AllTypes.FIELD_STRING, "bender")
//                var anotherAllTypes: DynamicRealmObject
//                run {
//                    anotherAllTypes = realm.createObject(AllTypes.CLASS_NAME)
//                    anotherAllTypes.setString(AllTypes.FIELD_STRING, "bender2")
//                    val dog: DynamicRealmObject = realm.createObject(Dog.CLASS_NAME)
//                    dog.setString(Dog.FIELD_NAME, "nibbler")
//                    anotherAllTypes.getList(AllTypes.FIELD_REALMLIST).add(dog)
//                }
//
//                // set managed RealmList
//                allTypes.setList(AllTypes.FIELD_REALMLIST, anotherAllTypes.getList(AllTypes.FIELD_REALMLIST))
//            }
//        })
//        val allTypes: DynamicRealmObject = dynamicRealm.where(AllTypes.CLASS_NAME)
//            .equalTo(AllTypes.FIELD_STRING, "bender")
//            .findFirst()
//        assertEquals(1, allTypes.getList(AllTypes.FIELD_REALMLIST).size())
//        assertEquals("nibbler", allTypes.getList(AllTypes.FIELD_REALMLIST).first().get(Dog.FIELD_NAME))
//
//        // Check if allTypes and anotherAllTypes share the same Dog object.
//        dynamicRealm.executeTransaction(object : Transaction() {
//            fun execute(realm: DynamicRealm?) {
//                val anotherAllTypes: DynamicRealmObject = dynamicRealm.where(AllTypes.CLASS_NAME)
//                    .equalTo(AllTypes.FIELD_STRING, "bender2")
//                    .findFirst()
//                anotherAllTypes.getList(AllTypes.FIELD_REALMLIST).first()
//                    .setString(Dog.FIELD_NAME, "nibbler_modified")
//            }
//        })
//        assertEquals("nibbler_modified", allTypes.getList(AllTypes.FIELD_REALMLIST).first().get(Dog.FIELD_NAME))
//    }
//
//    @Test
//    fun setList_elementBelongToTypedRealmThrows() {
//        val list: RealmList<DynamicRealmObject?> = RealmList<DynamicRealmObject>()
//        list.add(dObjTyped)
//        dynamicRealm.beginTransaction()
//        thrown.expect(IllegalArgumentException::class.java)
//        thrown.expectMessage("Each element in 'list' must belong to the same Realm instance.")
//        dynamicRealm.where(AllJavaTypes.CLASS_NAME).findFirst().setList(AllJavaTypes.FIELD_LIST, list)
//        dynamicRealm.cancelTransaction()
//    }
//
//    @Test
//    fun setList_elementBelongToDiffThreadRealmThrows() {
//        val finishedLatch = CountDownLatch(1)
//        Thread {
//            val dynamicRealm: DynamicRealm = DynamicRealm.getInstance(realm!!.configuration)
//            val list: RealmList<DynamicRealmObject?> = RealmList<DynamicRealmObject>()
//            list.add(dObjDynamic)
//            dynamicRealm.beginTransaction()
//            try {
//                // ExpectedException doesn't work in another thread.
//                dynamicRealm.where(AllJavaTypes.CLASS_NAME).findFirst().setList(AllJavaTypes.FIELD_LIST, list)
//                Assert.fail()
//            } catch (expected: IllegalArgumentException) {
//                Assert.assertEquals("Each element in 'list' must belong to the same Realm instance.", expected.message)
//            }
//            dynamicRealm.cancelTransaction()
//            dynamicRealm.close()
//            finishedLatch.countDown()
//        }.start()
//        TestHelper.awaitOrFail(finishedLatch)
//    }
//
//    @Test
//    fun setList_wrongTypeThrows() {
//        realm.beginTransaction()
//        val wrongObj: AllTypes = realm.createObject(AllTypes::class.java)
//        val wrongDynamicObject = DynamicRealmObject(wrongObj)
//        val wrongDynamicList: RealmList<DynamicRealmObject> = wrongDynamicObject.getList(AllTypes.FIELD_REALMLIST)
//        thrown.expect(IllegalArgumentException::class.java)
//        dObjTyped.setList(AllJavaTypes.FIELD_LIST, wrongDynamicList)
//    }
//
//    @Test
//    fun setList_javaModelClassesThrowProperErrorMessage() {
//        dynamicRealm.beginTransaction()
//        try {
//            dObjDynamic.setList(AllJavaTypes.FIELD_LIST, RealmList<E>(typedObj))
//            Assert.fail()
//        } catch (e: IllegalArgumentException) {
//            Assert.assertTrue(e.message!!.contains("RealmList must contain `DynamicRealmObject's, not Java model classes."))
//        }
//    }
//
//    @Test
//    fun setList_objectsOwnList() {
//        dynamicRealm.beginTransaction()
//
//        // Test model classes
//        var originalSize: Int = dObjDynamic.getList(AllJavaTypes.FIELD_LIST).size()
//        dObjDynamic.setList(AllJavaTypes.FIELD_LIST, dObjDynamic.getList(AllJavaTypes.FIELD_LIST))
//        assertEquals(originalSize, dObjDynamic.getList(AllJavaTypes.FIELD_LIST).size())
//
//        // Smoke test value lists
//        originalSize = dObjDynamic.getList(AllJavaTypes.FIELD_STRING_LIST, String::class.java).size()
//        dObjDynamic.setList(AllJavaTypes.FIELD_STRING_LIST, dObjDynamic.getList(AllJavaTypes.FIELD_STRING_LIST, String::class.java))
//        assertEquals(originalSize, dObjDynamic.getList(AllJavaTypes.FIELD_STRING_LIST, String::class.java).size())
//    }
//
//    @Test
//    fun untypedSetter_listWrongTypeThrows() {
//        realm.beginTransaction()
//        val wrongObj: AllTypes = realm.createObject(AllTypes::class.java)
//        thrown.expect(IllegalArgumentException::class.java)
//        dObjTyped.set(AllJavaTypes.FIELD_LIST, wrongObj.getColumnRealmList())
//    }
//
//    @Test
//    fun untypedSetter_listRealmAnyTypesThrows() {
//        realm.beginTransaction()
//        val obj1: AllJavaTypes = realm.createObject(AllJavaTypes::class.java, 2)
//        val obj2: CyclicType = realm.createObject(CyclicType::class.java)
//        val list: RealmList<DynamicRealmObject> = RealmList<DynamicRealmObject>()
//        list.add(DynamicRealmObject(obj1))
//        list.add(DynamicRealmObject(obj2))
//        thrown.expect(IllegalArgumentException::class.java)
//        dObjTyped.set(AllJavaTypes.FIELD_LIST, list)
//    }
//
//    // List is not a simple getter, tests separately.
//    @get:Test
//    val list: Unit
//        get() {
//            realm.beginTransaction()
//            val obj: AllTypes = realm.createObject(AllTypes::class.java)
//            val dog: Dog = realm.createObject(Dog::class.java)
//            dog.setName("fido")
//            obj.getColumnRealmList().add(dog)
//            realm.commitTransaction()
//            val dynamicAllTypes = DynamicRealmObject(obj)
//            val list: RealmList<DynamicRealmObject> = dynamicAllTypes.getList(AllTypes.FIELD_REALMLIST)
//            val listObject: DynamicRealmObject = list[0]
//            Assert.assertEquals(1, list.size.toLong())
//            assertEquals(Dog.CLASS_NAME, listObject.getType())
//            assertEquals("fido", listObject.getString(Dog.FIELD_NAME))
//        }
//
//    @Test
//    fun untypedGetterSetter() {
//        realm.beginTransaction()
//        val obj: AllJavaTypes = realm.createObject(AllJavaTypes::class.java, 0)
//        val dObj = DynamicRealmObject(obj)
//        try {
//            for (type in io.realm.DynamicRealmObjectTests.SupportedType.values()) {
//                when (type) {
//                    io.realm.DynamicRealmObjectTests.SupportedType.BOOLEAN -> {
//                        dObj.set(AllJavaTypes.FIELD_BOOLEAN, true)
//                        Assert.assertTrue((dObj.get(AllJavaTypes.FIELD_BOOLEAN) as Boolean))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.SHORT -> {
//                        dObj.set(AllJavaTypes.FIELD_SHORT, 42.toShort())
//                        assertEquals("42".toLong(), dObj.< Long > get < kotlin . Long ? > AllJavaTypes.FIELD_SHORT.longValue())
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.INT -> {
//                        dObj.set(AllJavaTypes.FIELD_INT, 42)
//                        assertEquals("42".toLong(), dObj.< Long > get < kotlin . Long ? > AllJavaTypes.FIELD_INT.longValue())
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LONG -> {
//                        dObj.set(AllJavaTypes.FIELD_LONG, 42L)
//                        assertEquals("42".toLong(), dObj.< Long > get < kotlin . Long ? > AllJavaTypes.FIELD_LONG.longValue())
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.BYTE -> {
//                        dObj.set(AllJavaTypes.FIELD_BYTE, 4.toByte())
//                        assertEquals("4".toLong(), dObj.< Long > get < kotlin . Long ? > AllJavaTypes.FIELD_BYTE.longValue())
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.FLOAT -> {
//                        dObj.set(AllJavaTypes.FIELD_FLOAT, 1.23f)
//                        assertEquals("1.23".toFloat(), dObj.< Float > get < kotlin . Float ? > AllJavaTypes.FIELD_FLOAT, java.lang.Float.MIN_NORMAL)
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.DOUBLE -> {
//                        dObj.set(AllJavaTypes.FIELD_DOUBLE, 1.234)
//                        assertEquals("1.234".toDouble(), dObj.< Double > get < kotlin . Double ? > AllJavaTypes.FIELD_DOUBLE, java.lang.Double.MIN_NORMAL)
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.STRING -> {
//                        dObj.set(AllJavaTypes.FIELD_STRING, "str")
//                        assertEquals("str", dObj.get(AllJavaTypes.FIELD_STRING))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.BINARY -> {
//                        dObj.set(AllJavaTypes.FIELD_BINARY, byteArrayOf(1, 2, 3))
//                        Assert.assertArrayEquals(byteArrayOf(1, 2, 3), dObj.get(AllJavaTypes.FIELD_BINARY) as ByteArray)
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.DATE -> {
//                        dObj.set(AllJavaTypes.FIELD_DATE, Date(1000))
//                        assertEquals(Date(1000), dObj.get(AllJavaTypes.FIELD_DATE))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.DECIMAL128 -> {
//                        dObj.set(AllJavaTypes.FIELD_DECIMAL128, Decimal128(BigDecimal.ONE))
//                        assertEquals(Decimal128(BigDecimal.ONE), dObj.get(AllJavaTypes.FIELD_DECIMAL128))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.OBJECT_ID -> {
//                        dObj.set(AllJavaTypes.FIELD_OBJECT_ID, ObjectId(TestHelper.generateObjectIdHexString(7)))
//                        assertEquals(ObjectId(TestHelper.generateObjectIdHexString(7)), dObj.get(AllJavaTypes.FIELD_OBJECT_ID))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.UUID -> {
//                        val uuid = UUID.randomUUID().toString()
//                        dObj.set(AllJavaTypes.FIELD_UUID, UUID.fromString(uuid))
//                        assertEquals(UUID.fromString(uuid), dObj.get(AllJavaTypes.FIELD_UUID))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.OBJECT -> {
//                        dObj.set(AllJavaTypes.FIELD_OBJECT, dObj)
//                        assertEquals(dObj, dObj.get(AllJavaTypes.FIELD_OBJECT))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST -> {
//                        val newList: RealmList<DynamicRealmObject> = RealmList<DynamicRealmObject>()
//                        newList.add(dObj)
//                        dObj.set(AllJavaTypes.FIELD_LIST, newList)
//                        val list: RealmList<DynamicRealmObject> = dObj.getList(AllJavaTypes.FIELD_LIST)
//                        Assert.assertEquals(1, list.size.toLong())
//                        assertEquals(dObj, list[0])
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_INTEGER -> {
//                        val newList: RealmList<Int> = RealmList<Int>(null, 1)
//                        dObj.set(AllJavaTypes.FIELD_INTEGER_LIST, newList)
//                        val list: RealmList<Int> = dObj.getList(AllJavaTypes.FIELD_INTEGER_LIST, Int::class.java)
//                        Assert.assertEquals(2, list.size.toLong())
//                        Assert.assertArrayEquals(newList.toTypedArray(), list.toTypedArray())
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_STRING -> {
//                        val newList: RealmList<String> = RealmList<String>(null, "Foo")
//                        dObj.set(AllJavaTypes.FIELD_STRING_LIST, newList)
//                        val list: RealmList<String> = dObj.getList(AllJavaTypes.FIELD_STRING_LIST, String::class.java)
//                        Assert.assertEquals(2, list.size.toLong())
//                        Assert.assertArrayEquals(newList.toTypedArray(), list.toTypedArray())
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_BOOLEAN -> {
//                        val newList: RealmList<Boolean> = RealmList<Boolean>(null, true)
//                        dObj.set(AllJavaTypes.FIELD_BOOLEAN_LIST, newList)
//                        val list: RealmList<Boolean> = dObj.getList(AllJavaTypes.FIELD_BOOLEAN_LIST, Boolean::class.java)
//                        Assert.assertEquals(2, list.size.toLong())
//                        Assert.assertArrayEquals(newList.toTypedArray(), list.toTypedArray())
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_FLOAT -> {
//                        val newList: RealmList<Float> = RealmList<Float>(null, 1.23f)
//                        dObj.set(AllJavaTypes.FIELD_FLOAT_LIST, newList)
//                        val list: RealmList<Float> = dObj.getList(AllJavaTypes.FIELD_FLOAT_LIST, Float::class.java)
//                        Assert.assertEquals(2, list.size.toLong())
//                        Assert.assertArrayEquals(newList.toTypedArray(), list.toTypedArray())
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_DOUBLE -> {
//                        val newList: RealmList<Double> = RealmList<Double>(null, 1.24)
//                        dObj.set(AllJavaTypes.FIELD_DOUBLE_LIST, newList)
//                        val list: RealmList<Double> = dObj.getList(AllJavaTypes.FIELD_DOUBLE_LIST, Double::class.java)
//                        Assert.assertEquals(2, list.size.toLong())
//                        Assert.assertArrayEquals(newList.toTypedArray(), list.toTypedArray())
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_BINARY -> {
//                        val newList: RealmList<ByteArray> = RealmList<ByteArray>(null, byteArrayOf(1, 2, 3))
//                        dObj.set(AllJavaTypes.FIELD_BINARY_LIST, newList)
//                        val list: RealmList<ByteArray> = dObj.getList(AllJavaTypes.FIELD_BINARY_LIST, ByteArray::class.java)
//                        Assert.assertEquals(2, list.size.toLong())
//                        Assert.assertArrayEquals(newList.toTypedArray(), list.toTypedArray())
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_DATE -> {
//                        val newList: RealmList<Date> = RealmList<Date>(null, Date(1000))
//                        dObj.set(AllJavaTypes.FIELD_DATE_LIST, newList)
//                        val list: RealmList<Date> = dObj.getList(AllJavaTypes.FIELD_DATE_LIST, Date::class.java)
//                        Assert.assertEquals(2, list.size.toLong())
//                        Assert.assertArrayEquals(newList.toTypedArray(), list.toTypedArray())
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_DECIMAL128 -> {
//                        val newList: RealmList<Decimal128> = RealmList<Decimal128>(null, Decimal128(BigDecimal.ONE))
//                        dObj.set(AllJavaTypes.FIELD_DECIMAL128_LIST, newList)
//                        val list: RealmList<Decimal128> = dObj.getList(AllJavaTypes.FIELD_DECIMAL128_LIST, Decimal128::class.java)
//                        Assert.assertEquals(2, list.size.toLong())
//                        Assert.assertArrayEquals(newList.toTypedArray(), list.toTypedArray())
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_OBJECT_ID -> {
//                        val newList: RealmList<ObjectId> = RealmList<ObjectId>(null, ObjectId(TestHelper.generateObjectIdHexString(0)))
//                        dObj.set(AllJavaTypes.FIELD_OBJECT_ID_LIST, newList)
//                        val list: RealmList<ObjectId> = dObj.getList(AllJavaTypes.FIELD_OBJECT_ID_LIST, ObjectId::class.java)
//                        Assert.assertEquals(2, list.size.toLong())
//                        Assert.assertArrayEquals(newList.toTypedArray(), list.toTypedArray())
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_UUID -> {
//                        val newList: RealmList<UUID> = RealmList<UUID>(null, UUID.randomUUID())
//                        dObj.set(AllJavaTypes.FIELD_UUID_LIST, newList)
//                        val list: RealmList<UUID> = dObj.getList(AllJavaTypes.FIELD_UUID_LIST, UUID::class.java)
//                        Assert.assertEquals(2, list.size.toLong())
//                        Assert.assertArrayEquals(newList.toTypedArray(), list.toTypedArray())
//                    }
//                    else -> Assert.fail()
//                }
//            }
//        } finally {
//            realm.cancelTransaction()
//        }
//    }
//
//    @Test
//    fun untypedSetter_usingStringConversion() {
//        realm.beginTransaction()
//        val obj: AllJavaTypes = realm.createObject(AllJavaTypes::class.java, 0)
//        val dObj = DynamicRealmObject(obj)
//        try {
//            for (type in io.realm.DynamicRealmObjectTests.SupportedType.values()) {
//                when (type) {
//                    io.realm.DynamicRealmObjectTests.SupportedType.BOOLEAN -> {
//                        dObj.set(AllJavaTypes.FIELD_BOOLEAN, "true")
//                        Assert.assertTrue(dObj.getBoolean(AllJavaTypes.FIELD_BOOLEAN))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.SHORT -> {
//                        dObj.set(AllJavaTypes.FIELD_SHORT, "42")
//                        assertEquals(42.toShort(), dObj.getShort(AllJavaTypes.FIELD_SHORT))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.INT -> {
//                        dObj.set(AllJavaTypes.FIELD_INT, "42")
//                        assertEquals(42, dObj.getInt(AllJavaTypes.FIELD_INT))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LONG -> {
//                        dObj.set(AllJavaTypes.FIELD_LONG, "42")
//                        assertEquals(42.toLong(), dObj.getLong(AllJavaTypes.FIELD_LONG))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.FLOAT -> {
//                        dObj.set(AllJavaTypes.FIELD_FLOAT, "1.23")
//                        assertEquals(1.23f, dObj.getFloat(AllJavaTypes.FIELD_FLOAT), 0f)
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.DOUBLE -> {
//                        dObj.set(AllJavaTypes.FIELD_DOUBLE, "1.234")
//                        assertEquals(1.234, dObj.getDouble(AllJavaTypes.FIELD_DOUBLE), 0f)
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.DATE -> {
//                        dObj.set(AllJavaTypes.FIELD_DATE, "1000")
//                        assertEquals(Date(1000), dObj.getDate(AllJavaTypes.FIELD_DATE))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.DECIMAL128 -> {
//                        dObj.set(AllJavaTypes.FIELD_DECIMAL128, "1")
//                        assertEquals(Decimal128(BigDecimal.ONE), dObj.get(AllJavaTypes.FIELD_DECIMAL128))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.OBJECT_ID -> {
//                        dObj.set(AllJavaTypes.FIELD_OBJECT_ID, TestHelper.generateObjectIdHexString(7))
//                        assertEquals(ObjectId(TestHelper.generateObjectIdHexString(7)), dObj.get(AllJavaTypes.FIELD_OBJECT_ID))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.UUID -> {
//                        val uuid = UUID.randomUUID().toString()
//                        dObj.set(AllJavaTypes.FIELD_UUID, UUID.fromString(uuid))
//                        assertEquals(UUID.fromString(uuid), dObj.get(AllJavaTypes.FIELD_UUID))
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.OBJECT, io.realm.DynamicRealmObjectTests.SupportedType.LIST, io.realm.DynamicRealmObjectTests.SupportedType.LIST_INTEGER, io.realm.DynamicRealmObjectTests.SupportedType.LIST_STRING, io.realm.DynamicRealmObjectTests.SupportedType.LIST_BOOLEAN, io.realm.DynamicRealmObjectTests.SupportedType.LIST_FLOAT, io.realm.DynamicRealmObjectTests.SupportedType.LIST_DOUBLE, io.realm.DynamicRealmObjectTests.SupportedType.LIST_BINARY, io.realm.DynamicRealmObjectTests.SupportedType.LIST_DATE, io.realm.DynamicRealmObjectTests.SupportedType.LIST_DECIMAL128, io.realm.DynamicRealmObjectTests.SupportedType.LIST_OBJECT_ID, io.realm.DynamicRealmObjectTests.SupportedType.LIST_UUID, io.realm.DynamicRealmObjectTests.SupportedType.STRING, io.realm.DynamicRealmObjectTests.SupportedType.BINARY, io.realm.DynamicRealmObjectTests.SupportedType.BYTE -> {}
//                    else -> Assert.fail("Unknown type: $type")
//                }
//            }
//        } finally {
//            realm.cancelTransaction()
//        }
//    }
//
//    @Test
//    fun untypedSetter_illegalImplicitConversionThrows() {
//        realm.beginTransaction()
//        val obj: AllJavaTypes = realm.createObject(AllJavaTypes::class.java, 0)
//        val dObj = DynamicRealmObject(obj)
//        try {
//            for (type in io.realm.DynamicRealmObjectTests.SupportedType.values()) {
//                try {
//                    when (type) {
//                        io.realm.DynamicRealmObjectTests.SupportedType.SHORT -> dObj.set(AllJavaTypes.FIELD_SHORT, "foo")
//                        io.realm.DynamicRealmObjectTests.SupportedType.INT -> dObj.set(AllJavaTypes.FIELD_INT, "foo")
//                        io.realm.DynamicRealmObjectTests.SupportedType.LONG -> dObj.set(AllJavaTypes.FIELD_ID, "foo")
//                        io.realm.DynamicRealmObjectTests.SupportedType.FLOAT -> dObj.set(AllJavaTypes.FIELD_FLOAT, "foo")
//                        io.realm.DynamicRealmObjectTests.SupportedType.DOUBLE -> dObj.set(AllJavaTypes.FIELD_DOUBLE, "foo")
//                        io.realm.DynamicRealmObjectTests.SupportedType.DATE -> dObj.set(AllJavaTypes.FIELD_DATE, "foo")
//                        io.realm.DynamicRealmObjectTests.SupportedType.DECIMAL128 -> dObj.set(AllJavaTypes.FIELD_DECIMAL128, "foo")
//                        io.realm.DynamicRealmObjectTests.SupportedType.OBJECT_ID -> dObj.set(AllJavaTypes.FIELD_OBJECT_ID, "foo")
//                        io.realm.DynamicRealmObjectTests.SupportedType.UUID -> dObj.set(AllJavaTypes.FIELD_UUID, "foo")
//                        io.realm.DynamicRealmObjectTests.SupportedType.BOOLEAN, io.realm.DynamicRealmObjectTests.SupportedType.BYTE, io.realm.DynamicRealmObjectTests.SupportedType.OBJECT, io.realm.DynamicRealmObjectTests.SupportedType.LIST, io.realm.DynamicRealmObjectTests.SupportedType.LIST_INTEGER, io.realm.DynamicRealmObjectTests.SupportedType.LIST_STRING, io.realm.DynamicRealmObjectTests.SupportedType.LIST_BOOLEAN, io.realm.DynamicRealmObjectTests.SupportedType.LIST_FLOAT, io.realm.DynamicRealmObjectTests.SupportedType.LIST_DOUBLE, io.realm.DynamicRealmObjectTests.SupportedType.LIST_BINARY, io.realm.DynamicRealmObjectTests.SupportedType.LIST_DATE, io.realm.DynamicRealmObjectTests.SupportedType.LIST_DECIMAL128, io.realm.DynamicRealmObjectTests.SupportedType.LIST_OBJECT_ID, io.realm.DynamicRealmObjectTests.SupportedType.LIST_UUID, io.realm.DynamicRealmObjectTests.SupportedType.STRING, io.realm.DynamicRealmObjectTests.SupportedType.BINARY -> continue
//                        else -> Assert.fail("Unknown type: $type")
//                    }
//                    Assert.fail(type.toString() + " failed")
//                } catch (ignored: IllegalArgumentException) {
//                } catch (e: RealmException) {
//                    if (e.getCause() !is ParseException) {
//                        // Providing "foo" to the date parser will blow up with a RealmException
//                        // and the cause will be a ParseException.
//                        Assert.fail(type.toString() + " failed")
//                    }
//                }
//            }
//        } finally {
//            realm.cancelTransaction()
//        }
//    }
//
//    private fun testChangePrimaryKeyThroughUntypedSetter(value: String?) {
//        val primaryKeyClasses = arrayOf<String>(PrimaryKeyAsBoxedByte.CLASS_NAME, PrimaryKeyAsBoxedShort.CLASS_NAME,
//            PrimaryKeyAsBoxedInteger.CLASS_NAME, PrimaryKeyAsBoxedLong.CLASS_NAME, PrimaryKeyAsString.CLASS_NAME)
//        for (pkClass in primaryKeyClasses) {
//            dynamicRealm.beginTransaction()
//            var `object`: DynamicRealmObject
//            val isStringPK = pkClass == PrimaryKeyAsString.CLASS_NAME
//            `object` = if (isStringPK) {
//                dynamicRealm.createObject(pkClass, "")
//            } else {
//                dynamicRealm.createObject(pkClass, 0)
//            }
//            try {
//                `object`.set(if (isStringPK) PrimaryKeyAsString.FIELD_PRIMARY_KEY else "id", value)
//                Assert.fail()
//            } catch (ignored: IllegalArgumentException) {
//            }
//            dynamicRealm.cancelTransaction()
//        }
//    }
//
//    @Test
//    fun untypedSetter_setValue_changePrimaryKeyThrows() {
//        testChangePrimaryKeyThroughUntypedSetter("42")
//    }
//
//    @Test
//    fun untypedSetter_setNull_changePrimaryKeyThrows() {
//        testChangePrimaryKeyThroughUntypedSetter(null)
//    }
//
//    @get:Test
//    val isNull_nullNotSupportedField: Unit
//        get() {
//            Assert.assertFalse(dObjTyped.isNull(AllJavaTypes.FIELD_INT))
//        }
//
//    @get:Test
//    val isNull_true: Unit
//        get() {
//            realm.beginTransaction()
//            val obj: AllJavaTypes = realm.createObject(AllJavaTypes::class.java, 0)
//            realm.commitTransaction()
//            Assert.assertTrue(DynamicRealmObject(obj).isNull(AllJavaTypes.FIELD_OBJECT))
//        }
//
//    @get:Test
//    val isNull_false: Unit
//        get() {
//            Assert.assertFalse(dObjTyped.isNull(AllJavaTypes.FIELD_OBJECT))
//        }
//
//    // After the stable ID support, primary key field will be inserted first before others. So even FIELD_STRING is
//    // the first defined field in the class, it will be inserted after FIELD_ID.
//    // See ObjectStore::add_initial_columns #if REALM_HAVE_SYNC_STABLE_IDS branch.
//    @get:Test
//    val fieldNames: Unit
//        get() {
//            val expectedKeys = arrayOf<String>(AllJavaTypes.FIELD_STRING, AllJavaTypes.FIELD_ID, AllJavaTypes.FIELD_LONG,
//                AllJavaTypes.FIELD_SHORT, AllJavaTypes.FIELD_INT, AllJavaTypes.FIELD_BYTE, AllJavaTypes.FIELD_FLOAT,
//                AllJavaTypes.FIELD_DOUBLE, AllJavaTypes.FIELD_BOOLEAN, AllJavaTypes.FIELD_DATE,
//                AllJavaTypes.FIELD_BINARY, AllJavaTypes.FIELD_DECIMAL128, AllJavaTypes.FIELD_OBJECT_ID, AllJavaTypes.FIELD_UUID,
//                AllJavaTypes.FIELD_REALM_ANY, AllJavaTypes.FIELD_OBJECT, AllJavaTypes.FIELD_LIST,
//                AllJavaTypes.FIELD_STRING_LIST, AllJavaTypes.FIELD_BINARY_LIST, AllJavaTypes.FIELD_BOOLEAN_LIST,
//                AllJavaTypes.FIELD_LONG_LIST, AllJavaTypes.FIELD_INTEGER_LIST, AllJavaTypes.FIELD_SHORT_LIST,
//                AllJavaTypes.FIELD_BYTE_LIST, AllJavaTypes.FIELD_DOUBLE_LIST, AllJavaTypes.FIELD_FLOAT_LIST,
//                AllJavaTypes.FIELD_DATE_LIST, AllJavaTypes.FIELD_DECIMAL128_LIST, AllJavaTypes.FIELD_OBJECT_ID_LIST,
//                AllJavaTypes.FIELD_UUID_LIST, AllJavaTypes.FIELD_REALM_ANY_LIST)
//            val keys: Array<String> = dObjTyped.getFieldNames()
//            // After the stable ID support, primary key field will be inserted first before others. So even FIELD_STRING is
//            // the first defined field in the class, it will be inserted after FIELD_ID.
//            // See ObjectStore::add_initial_columns #if REALM_HAVE_SYNC_STABLE_IDS branch.
//            Assert.assertEquals(expectedKeys.size.toLong(), keys.size.toLong())
//            Assert.assertThat(Arrays.asList(*expectedKeys), Matchers.hasItems(keys))
//        }
//
//    @Test
//    fun hasField_false() {
//        Assert.assertFalse(dObjTyped.hasField(null))
//        Assert.assertFalse(dObjTyped.hasField(""))
//        Assert.assertFalse(dObjTyped.hasField("foo"))
//        Assert.assertFalse(dObjTyped.hasField("foo.bar"))
//        Assert.assertFalse(dObjTyped.hasField(TestHelper.getRandomString(65)))
//    }
//
//    @Test
//    fun hasField_true() {
//        Assert.assertTrue(dObjTyped.hasField(AllJavaTypes.FIELD_STRING))
//    }
//
//    @get:Test
//    val fieldType: Unit
//        get() {
//            assertEquals(RealmFieldType.STRING, dObjTyped.getFieldType(AllJavaTypes.FIELD_STRING))
//            assertEquals(RealmFieldType.BINARY, dObjTyped.getFieldType(AllJavaTypes.FIELD_BINARY))
//            assertEquals(RealmFieldType.BOOLEAN, dObjTyped.getFieldType(AllJavaTypes.FIELD_BOOLEAN))
//            assertEquals(RealmFieldType.DATE, dObjTyped.getFieldType(AllJavaTypes.FIELD_DATE))
//            assertEquals(RealmFieldType.OBJECT_ID, dObjTyped.getFieldType(AllJavaTypes.FIELD_OBJECT_ID))
//            assertEquals(RealmFieldType.DECIMAL128, dObjTyped.getFieldType(AllJavaTypes.FIELD_DECIMAL128))
//            assertEquals(RealmFieldType.UUID, dObjTyped.getFieldType(AllJavaTypes.FIELD_UUID))
//            assertEquals(RealmFieldType.DOUBLE, dObjTyped.getFieldType(AllJavaTypes.FIELD_DOUBLE))
//            assertEquals(RealmFieldType.FLOAT, dObjTyped.getFieldType(AllJavaTypes.FIELD_FLOAT))
//            assertEquals(RealmFieldType.OBJECT, dObjTyped.getFieldType(AllJavaTypes.FIELD_OBJECT))
//            assertEquals(RealmFieldType.LIST, dObjTyped.getFieldType(AllJavaTypes.FIELD_LIST))
//            assertEquals(RealmFieldType.INTEGER, dObjTyped.getFieldType(AllJavaTypes.FIELD_BYTE))
//            assertEquals(RealmFieldType.INTEGER, dObjTyped.getFieldType(AllJavaTypes.FIELD_SHORT))
//            assertEquals(RealmFieldType.INTEGER, dObjTyped.getFieldType(AllJavaTypes.FIELD_INT))
//            assertEquals(RealmFieldType.INTEGER, dObjTyped.getFieldType(AllJavaTypes.FIELD_LONG))
//            assertEquals(RealmFieldType.INTEGER_LIST, dObjTyped.getFieldType(AllJavaTypes.FIELD_INTEGER_LIST))
//            assertEquals(RealmFieldType.STRING_LIST, dObjTyped.getFieldType(AllJavaTypes.FIELD_STRING_LIST))
//            assertEquals(RealmFieldType.BOOLEAN_LIST, dObjTyped.getFieldType(AllJavaTypes.FIELD_BOOLEAN_LIST))
//            assertEquals(RealmFieldType.FLOAT_LIST, dObjTyped.getFieldType(AllJavaTypes.FIELD_FLOAT_LIST))
//            assertEquals(RealmFieldType.DOUBLE_LIST, dObjTyped.getFieldType(AllJavaTypes.FIELD_DOUBLE_LIST))
//            assertEquals(RealmFieldType.BINARY_LIST, dObjTyped.getFieldType(AllJavaTypes.FIELD_BINARY_LIST))
//            assertEquals(RealmFieldType.DATE_LIST, dObjTyped.getFieldType(AllJavaTypes.FIELD_DATE_LIST))
//            assertEquals(RealmFieldType.OBJECT_ID_LIST, dObjTyped.getFieldType(AllJavaTypes.FIELD_OBJECT_ID_LIST))
//            assertEquals(RealmFieldType.DECIMAL128_LIST, dObjTyped.getFieldType(AllJavaTypes.FIELD_DECIMAL128_LIST))
//            assertEquals(RealmFieldType.UUID_LIST, dObjTyped.getFieldType(AllJavaTypes.FIELD_UUID_LIST))
//        }
//
//    @Test
//    fun equals() {
//        val obj1: AllJavaTypes = realm.where(AllJavaTypes::class.java).findFirst()
//        val obj2: AllJavaTypes = realm.where(AllJavaTypes::class.java).findFirst()
//        val dObj1 = DynamicRealmObject(obj1)
//        val dObj2 = DynamicRealmObject(obj2)
//        Assert.assertTrue(dObj1.equals(dObj2))
//    }
//
//    @Test
//    fun equals_standardAndDynamicObjectsNotEqual() {
//        val standardObj: AllJavaTypes = realm.where(AllJavaTypes::class.java).findFirst()
//        Assert.assertFalse(dObjTyped.equals(standardObj))
//    }
//
//    @Test
//    fun hashcode() {
//        val standardObj: AllJavaTypes = realm.where(AllJavaTypes::class.java).findFirst()
//        val dObj1 = DynamicRealmObject(standardObj)
//        assertEquals(standardObj.hashCode(), dObj1.hashCode())
//    }
//
//    @Test
//    fun toString_test() {
//        // Checks that toString() doesn't crash, and does simple formatting checks. We cannot compare to a set String as
//        // eg. the byte array will be allocated each time it is accessed.
//        val str: String = dObjTyped.toString()
//        Assert.assertTrue(str.startsWith("AllJavaTypes = dynamic["))
//        Assert.assertTrue(str.endsWith("}]"))
//    }
//
//    @Test
//    fun toString_nullValues() {
//        dynamicRealm.beginTransaction()
//        val obj: DynamicRealmObject = dynamicRealm.createObject(NullTypes.CLASS_NAME, 0)
//        dynamicRealm.commitTransaction()
//        val str: String = obj.toString()
//        Assert.assertTrue(str.contains(NullTypes.FIELD_STRING_NULL.toString() + ":null"))
//        Assert.assertTrue(str.contains(NullTypes.FIELD_BYTES_NULL.toString() + ":null"))
//        Assert.assertTrue(str.contains(NullTypes.FIELD_BOOLEAN_NULL.toString() + ":null"))
//        Assert.assertTrue(str.contains(NullTypes.FIELD_BYTE_NULL.toString() + ":null"))
//        Assert.assertTrue(str.contains(NullTypes.FIELD_SHORT_NULL.toString() + ":null"))
//        Assert.assertTrue(str.contains(NullTypes.FIELD_INTEGER_NULL.toString() + ":null"))
//        Assert.assertTrue(str.contains(NullTypes.FIELD_LONG_NULL.toString() + ":null"))
//        Assert.assertTrue(str.contains(NullTypes.FIELD_FLOAT_NULL.toString() + ":null"))
//        Assert.assertTrue(str.contains(NullTypes.FIELD_DOUBLE_NULL.toString() + ":null"))
//        Assert.assertTrue(str.contains(NullTypes.FIELD_DATE_NULL.toString() + ":null"))
//        Assert.assertTrue(str.contains(NullTypes.FIELD_OBJECT_ID_NULL.toString() + ":null"))
//        Assert.assertTrue(str.contains(NullTypes.FIELD_DECIMAL128_NULL.toString() + ":null"))
//        Assert.assertTrue(str.contains(NullTypes.FIELD_UUID_NULL.toString() + ":null"))
//        Assert.assertTrue(str.contains(NullTypes.FIELD_OBJECT_NULL.toString() + ":null"))
//        Assert.assertTrue(str.contains(NullTypes.FIELD_LIST_NULL.toString() + ":RealmList<NullTypes>[0]"))
//        Assert.assertTrue(str.contains(NullTypes.FIELD_INTEGER_LIST_NULL.toString() + ":RealmList<Long>[0]"))
//        Assert.assertTrue(str.contains(NullTypes.FIELD_STRING_LIST_NULL.toString() + ":RealmList<String>[0]"))
//        Assert.assertTrue(str.contains(NullTypes.FIELD_BOOLEAN_LIST_NULL.toString() + ":RealmList<Boolean>[0]"))
//        Assert.assertTrue(str.contains(NullTypes.FIELD_FLOAT_LIST_NULL.toString() + ":RealmList<Float>[0]"))
//        Assert.assertTrue(str.contains(NullTypes.FIELD_DOUBLE_LIST_NULL.toString() + ":RealmList<Double>[0]"))
//        Assert.assertTrue(str.contains(NullTypes.FIELD_BINARY_LIST_NULL.toString() + ":RealmList<byte[]>[0]"))
//        Assert.assertTrue(str.contains(NullTypes.FIELD_DATE_LIST_NULL.toString() + ":RealmList<Date>[0]"))
//        Assert.assertTrue(str.contains(NullTypes.FIELD_OBJECT_ID_LIST_NULL.toString() + ":RealmList<ObjectId>[0]"))
//        Assert.assertTrue(str.contains(NullTypes.FIELD_DECIMAL128_LIST_NULL.toString() + ":RealmList<Decimal128>[0]"))
//        Assert.assertTrue(str.contains(NullTypes.FIELD_UUID_LIST_NULL.toString() + ":RealmList<UUID>[0]"))
//    }
//
//    @Test
//    fun testExceptionMessage() {
//        // Tests for https://github.com/realm/realm-java/issues/2141
//        realm.beginTransaction()
//        val obj: AllTypes = realm.createObject(AllTypes::class.java)
//        realm.commitTransaction()
//        val o = DynamicRealmObject(obj)
//        try {
//            o.getFloat("nonExisting") // Notes that "o" does not have "nonExisting" field.
//            Assert.fail()
//        } catch (e: IllegalArgumentException) {
//            Assert.assertEquals("Illegal Argument: Field not found: nonExisting", e.message)
//        }
//    }
//
//    @Test
//    fun getDynamicRealm() {
//        realm.beginTransaction()
//        realm.createObject(AllTypes::class.java)
//        realm.commitTransaction()
//        dynamicRealm.refresh()
//        val `object`: DynamicRealmObject = dynamicRealm.where(AllTypes.CLASS_NAME).findFirst()
//        Assert.assertSame(dynamicRealm, `object`.getDynamicRealm())
//    }
//
//    @Test
//    fun getRealm() {
//        realm.beginTransaction()
//        realm.createObject(AllTypes::class.java)
//        realm.commitTransaction()
//        dynamicRealm.refresh()
//        val `object`: DynamicRealmObject = dynamicRealm.where(AllTypes.CLASS_NAME).findFirst()
//        thrown.expect(IllegalStateException::class.java)
//        `object`.getRealm()
//    }
//
//    @get:Test
//    val realm_closedObjectThrows: Unit
//        get() {
//            realm.beginTransaction()
//            realm.createObject(AllTypes::class.java)
//            realm.commitTransaction()
//            dynamicRealm.refresh()
//            val `object`: DynamicRealmObject = dynamicRealm.where(AllTypes.CLASS_NAME).findFirst()
//            dynamicRealm.close()
//            dynamicRealm = null
//            try {
//                `object`.getDynamicRealm()
//                Assert.fail()
//            } catch (e: IllegalStateException) {
//                assertEquals(BaseRealm.CLOSED_REALM_MESSAGE, e.message)
//            }
//        }
//
//    @get:Test
//    val realmConfiguration_deletedObjectThrows: Unit
//        get() {
//            realm.beginTransaction()
//            realm.createObject(AllTypes::class.java)
//            realm.commitTransaction()
//            dynamicRealm.refresh()
//            val `object`: DynamicRealmObject = dynamicRealm.where(AllTypes.CLASS_NAME).findFirst()
//            dynamicRealm.beginTransaction()
//            `object`.deleteFromRealm()
//            dynamicRealm.commitTransaction()
//            try {
//                `object`.getDynamicRealm()
//                Assert.fail()
//            } catch (e: IllegalStateException) {
//                assertEquals(RealmObject.MSG_DELETED_OBJECT, e.message)
//            }
//        }
//
//    @get:Throws(Throwable::class)
//    @get:Test
//    val realm_illegalThreadThrows: Unit
//        get() {
//            realm.beginTransaction()
//            realm.createObject(AllTypes::class.java)
//            realm.commitTransaction()
//            dynamicRealm.refresh()
//            val `object`: DynamicRealmObject = dynamicRealm.where(AllTypes.CLASS_NAME).findFirst()
//            val threadFinished = CountDownLatch(1)
//            val thread = Thread {
//                try {
//                    `object`.getDynamicRealm()
//                    Assert.fail()
//                } catch (e: IllegalStateException) {
//                    assertEquals(BaseRealm.INCORRECT_THREAD_MESSAGE, e.message)
//                } finally {
//                    threadFinished.countDown()
//                }
//            }
//            thread.start()
//            TestHelper.awaitOrFail(threadFinished)
//        }
//
//    @get:Test
//    val nullableFields: Unit
//        get() {
//            realm.executeTransaction { realm ->
//                val primitiveNullables: NullablePrimitiveFields = realm.createObject(NullablePrimitiveFields::class.java)
//                primitiveNullables.setFieldBoolean(null)
//                Assert.assertNull(primitiveNullables.getFieldBoolean())
//                Assert.assertNull(primitiveNullables.getFieldInt())
//                Assert.assertNull(primitiveNullables.getFieldFloat())
//                Assert.assertNull(primitiveNullables.getFieldDouble())
//                Assert.assertNull(primitiveNullables.getFieldString())
//                Assert.assertNull(primitiveNullables.getFieldBinary())
//                Assert.assertNull(primitiveNullables.getFieldDate())
//                Assert.assertNull(primitiveNullables.getFieldObjectId())
//                Assert.assertNull(primitiveNullables.getFieldDecimal128())
//                Assert.assertNull(primitiveNullables.getFieldUUID())
//                Assert.assertTrue(primitiveNullables.getFieldRealmAny().isNull())
//                realm.delete(AllJavaTypes::class.java)
//                val allJavaTypes: AllJavaTypes = realm.createObject(AllJavaTypes::class.java, UUID.randomUUID().leastSignificantBits)
//                Assert.assertNull(allJavaTypes.getFieldObject())
//                allJavaTypes.getFieldBooleanList().add(null)
//                allJavaTypes.getFieldIntegerList().add(null)
//                allJavaTypes.getFieldFloatList().add(null)
//                allJavaTypes.getFieldDoubleList().add(null)
//                allJavaTypes.getFieldStringList().add(null)
//                allJavaTypes.getFieldBinaryList().add(null)
//                allJavaTypes.getFieldDateList().add(null)
//                allJavaTypes.getFieldObjectIdList().add(null)
//                allJavaTypes.getFieldDecimal128List().add(null)
//                allJavaTypes.getFieldUUIDList().add(null)
//                allJavaTypes.getFieldRealmAnyList().add(null)
//            }
//            realm!!.close()
//            dynamicRealm.refresh()
//            val primitiveNullables: DynamicRealmObject = dynamicRealm.where(NullablePrimitiveFields.CLASS_NAME).findFirst()
//            val allJavaTypes: DynamicRealmObject = dynamicRealm.where(AllJavaTypes.CLASS_NAME).findFirst()
//            for (value in RealmFieldType.values()) {
//                when (value) {
//                    INTEGER -> Assert.assertNull(primitiveNullables.get(NullablePrimitiveFields.FIELD_INT))
//                    BOOLEAN -> Assert.assertNull(primitiveNullables.get(NullablePrimitiveFields.FIELD_BOOLEAN))
//                    STRING -> Assert.assertNull(primitiveNullables.get(NullablePrimitiveFields.FIELD_STRING))
//                    BINARY -> Assert.assertNull(primitiveNullables.get(NullablePrimitiveFields.FIELD_BINARY))
//                    DATE -> Assert.assertNull(primitiveNullables.get(NullablePrimitiveFields.FIELD_DATE))
//                    FLOAT -> Assert.assertNull(primitiveNullables.get(NullablePrimitiveFields.FIELD_FLOAT))
//                    DOUBLE -> Assert.assertNull(primitiveNullables.get(NullablePrimitiveFields.FIELD_DOUBLE))
//                    OBJECT -> Assert.assertNull(allJavaTypes.get(AllJavaTypes.FIELD_OBJECT))
//                    DECIMAL128 -> Assert.assertNull(primitiveNullables.get(NullablePrimitiveFields.FIELD_DECIMAL128))
//                    OBJECT_ID -> Assert.assertNull(primitiveNullables.get(NullablePrimitiveFields.FIELD_OBJECT_ID))
//                    UUID -> Assert.assertNull(primitiveNullables.get(NullablePrimitiveFields.FIELD_UUID))
//                    MIXED -> Assert.assertNull(primitiveNullables.get(NullablePrimitiveFields.FIELD_REALM_ANY))
//                    INTEGER_LIST -> Assert.assertNull(allJavaTypes.getList(AllJavaTypes.FIELD_INTEGER_LIST, Int::class.java).get(0))
//                    BOOLEAN_LIST -> Assert.assertNull(allJavaTypes.getList(AllJavaTypes.FIELD_BOOLEAN_LIST, Boolean::class.java).get(0))
//                    STRING_LIST -> Assert.assertNull(allJavaTypes.getList(AllJavaTypes.FIELD_STRING_LIST, String::class.java).get(0))
//                    BINARY_LIST -> Assert.assertNull(allJavaTypes.getList(AllJavaTypes.FIELD_BINARY_LIST, ByteArray::class.java).get(0))
//                    DATE_LIST -> Assert.assertNull(allJavaTypes.getList(AllJavaTypes.FIELD_DATE_LIST, Date::class.java).get(0))
//                    FLOAT_LIST -> Assert.assertNull(allJavaTypes.getList(AllJavaTypes.FIELD_FLOAT_LIST, Float::class.java).get(0))
//                    DOUBLE_LIST -> Assert.assertNull(allJavaTypes.getList(AllJavaTypes.FIELD_DOUBLE_LIST, Double::class.java).get(0))
//                    DECIMAL128_LIST -> Assert.assertNull(allJavaTypes.getList(AllJavaTypes.FIELD_DECIMAL128_LIST, Decimal128::class.java).get(0))
//                    OBJECT_ID_LIST -> Assert.assertNull(allJavaTypes.getList(AllJavaTypes.FIELD_OBJECT_ID_LIST, ObjectId::class.java).get(0))
//                    UUID_LIST -> Assert.assertNull(allJavaTypes.getList(AllJavaTypes.FIELD_UUID_LIST, UUID::class.java).get(0))
//                    MIXED_LIST -> Assert.assertTrue(allJavaTypes.getList(AllJavaTypes.FIELD_REALM_ANY_LIST, RealmAny::class.java).get(0).isNull())
//                    TYPED_LINK, LIST, LINKING_OBJECTS -> {}
//                    STRING_TO_MIXED_MAP, STRING_TO_BOOLEAN_MAP, STRING_TO_STRING_MAP, STRING_TO_INTEGER_MAP, STRING_TO_FLOAT_MAP, STRING_TO_DOUBLE_MAP, STRING_TO_BINARY_MAP, STRING_TO_DATE_MAP, STRING_TO_OBJECT_ID_MAP, STRING_TO_UUID_MAP, STRING_TO_DECIMAL128_MAP, STRING_TO_LINK_MAP, BOOLEAN_SET, STRING_SET, INTEGER_SET, FLOAT_SET, DOUBLE_SET, BINARY_SET, DATE_SET, DECIMAL128_SET, OBJECT_ID_SET, UUID_SET, LINK_SET, MIXED_SET -> {}
//                    else -> Assert.fail("Not testing all types")
//                }
//            }
//            dynamicRealm.close()
//        }
//
//    companion object {
//        private fun callThreadConfinedMethod(obj: DynamicRealmObject, method: io.realm.DynamicRealmObjectTests.ThreadConfinedMethods) {
//            when (method) {
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.GET_BOOLEAN -> obj.getBoolean(AllJavaTypes.FIELD_BOOLEAN)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.GET_BYTE -> obj.getByte(AllJavaTypes.FIELD_BYTE)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.GET_SHORT -> obj.getShort(AllJavaTypes.FIELD_SHORT)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.GET_INT -> obj.getInt(AllJavaTypes.FIELD_INT)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.GET_LONG -> obj.getLong(AllJavaTypes.FIELD_LONG)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.GET_FLOAT -> obj.getFloat(AllJavaTypes.FIELD_FLOAT)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.GET_DOUBLE -> obj.getDouble(AllJavaTypes.FIELD_DOUBLE)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.GET_BLOB -> obj.getBlob(AllJavaTypes.FIELD_BINARY)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.GET_STRING -> obj.getString(AllJavaTypes.FIELD_STRING)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.GET_DATE -> obj.getDate(AllJavaTypes.FIELD_DATE)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.GET_DECIMAL128 -> obj.getDate(AllJavaTypes.FIELD_DECIMAL128)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.GET_OBJECT_ID -> obj.getDate(AllJavaTypes.FIELD_OBJECT_ID)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.GET_UUID -> obj.getDate(AllJavaTypes.FIELD_UUID)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.GET_OBJECT -> obj.getObject(AllJavaTypes.FIELD_OBJECT)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.GET_LIST -> obj.getList(AllJavaTypes.FIELD_LIST)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.GET_PRIMITIVE_LIST -> obj.getList(AllJavaTypes.FIELD_STRING_LIST, String::class.java)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.GET -> obj.get(AllJavaTypes.FIELD_LONG)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.SET_BOOLEAN -> obj.setBoolean(AllJavaTypes.FIELD_BOOLEAN, true)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.SET_BYTE -> obj.setByte(AllJavaTypes.FIELD_BYTE, 1.toByte())
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.SET_SHORT -> obj.setShort(AllJavaTypes.FIELD_SHORT, 1.toShort())
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.SET_INT -> obj.setInt(AllJavaTypes.FIELD_INT, 1)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.SET_LONG -> obj.setLong(AllJavaTypes.FIELD_LONG, 1L)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.SET_FLOAT -> obj.setFloat(AllJavaTypes.FIELD_FLOAT, 1f)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.SET_DOUBLE -> obj.setDouble(AllJavaTypes.FIELD_DOUBLE, 1.0)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.SET_BLOB -> obj.setBlob(AllJavaTypes.FIELD_BINARY, byteArrayOf(1, 2, 3))
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.SET_STRING -> obj.setString(AllJavaTypes.FIELD_STRING, "12345")
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.SET_DATE -> obj.setDate(AllJavaTypes.FIELD_DATE, Date(1L))
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.SET_DECIMAL128 -> obj.setDecimal128(AllJavaTypes.FIELD_DECIMAL128, Decimal128(BigDecimal.ONE))
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.SET_OBJECT_ID -> obj.setObjectId(AllJavaTypes.FIELD_OBJECT_ID, ObjectId(TestHelper.generateObjectIdHexString(5)))
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.SET_UUID -> obj.setUUID(AllJavaTypes.FIELD_UUID, UUID.randomUUID())
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.SET_OBJECT -> obj.setObject(AllJavaTypes.FIELD_OBJECT, obj)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.SET_LIST -> obj.setList(AllJavaTypes.FIELD_LIST, RealmList<E>(obj))
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.SET_PRIMITIVE_LIST -> obj.setList(AllJavaTypes.FIELD_STRING_LIST, RealmList<String>("foo"))
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.SET -> obj.set(AllJavaTypes.FIELD_LONG, 1L)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.IS_NULL -> obj.isNull(AllJavaTypes.FIELD_OBJECT)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.SET_NULL -> obj.setNull(AllJavaTypes.FIELD_OBJECT)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.HAS_FIELD -> obj.hasField(AllJavaTypes.FIELD_OBJECT)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.GET_FIELD_NAMES -> obj.getFieldNames()
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.GET_TYPE -> obj.getType()
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.GET_FIELD_TYPE -> obj.getFieldType(AllJavaTypes.FIELD_OBJECT)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.HASH_CODE -> obj.hashCode()
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.EQUALS -> obj.equals(obj)
//                io.realm.DynamicRealmObjectTests.ThreadConfinedMethods.TO_STRING -> obj.toString()
//                else -> throw AssertionError("missing case for $method")
//            }
//        }
//
//        // Helper method for calling getters with different field names.
//        private fun callGetter(target: DynamicRealmObject?, type: io.realm.DynamicRealmObjectTests.SupportedType, fieldNames: List<String?>) {
//            for (fieldName in fieldNames) {
//                when (type) {
//                    io.realm.DynamicRealmObjectTests.SupportedType.BOOLEAN -> target.getBoolean(fieldName)
//                    io.realm.DynamicRealmObjectTests.SupportedType.SHORT -> target.getShort(fieldName)
//                    io.realm.DynamicRealmObjectTests.SupportedType.INT -> target.getInt(fieldName)
//                    io.realm.DynamicRealmObjectTests.SupportedType.LONG -> target.getLong(fieldName)
//                    io.realm.DynamicRealmObjectTests.SupportedType.BYTE -> target.getByte(fieldName)
//                    io.realm.DynamicRealmObjectTests.SupportedType.FLOAT -> target.getFloat(fieldName)
//                    io.realm.DynamicRealmObjectTests.SupportedType.DOUBLE -> target.getDouble(fieldName)
//                    io.realm.DynamicRealmObjectTests.SupportedType.STRING -> target.getString(fieldName)
//                    io.realm.DynamicRealmObjectTests.SupportedType.BINARY -> target.getBlob(fieldName)
//                    io.realm.DynamicRealmObjectTests.SupportedType.DATE -> target.getDate(fieldName)
//                    io.realm.DynamicRealmObjectTests.SupportedType.DECIMAL128 -> target.getDecimal128(fieldName)
//                    io.realm.DynamicRealmObjectTests.SupportedType.OBJECT_ID -> target.getObjectId(fieldName)
//                    io.realm.DynamicRealmObjectTests.SupportedType.UUID -> target.getUUID(fieldName)
//                    io.realm.DynamicRealmObjectTests.SupportedType.OBJECT -> target.getObject(fieldName)
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST, io.realm.DynamicRealmObjectTests.SupportedType.LIST_INTEGER, io.realm.DynamicRealmObjectTests.SupportedType.LIST_STRING, io.realm.DynamicRealmObjectTests.SupportedType.LIST_BOOLEAN, io.realm.DynamicRealmObjectTests.SupportedType.LIST_FLOAT, io.realm.DynamicRealmObjectTests.SupportedType.LIST_DOUBLE, io.realm.DynamicRealmObjectTests.SupportedType.LIST_BINARY, io.realm.DynamicRealmObjectTests.SupportedType.LIST_DATE, io.realm.DynamicRealmObjectTests.SupportedType.LIST_DECIMAL128, io.realm.DynamicRealmObjectTests.SupportedType.LIST_OBJECT_ID, io.realm.DynamicRealmObjectTests.SupportedType.LIST_UUID -> target.getList(fieldName)
//                    else -> Assert.fail()
//                }
//            }
//        }
//
//        // Helper method for calling setters with different field names.
//        private fun callSetter(target: DynamicRealmObject?, type: io.realm.DynamicRealmObjectTests.SupportedType, fieldNames: List<String?>) {
//            for (fieldName in fieldNames) {
//                when (type) {
//                    io.realm.DynamicRealmObjectTests.SupportedType.BOOLEAN -> target.setBoolean(fieldName, false)
//                    io.realm.DynamicRealmObjectTests.SupportedType.SHORT -> target.setShort(fieldName, 1.toShort())
//                    io.realm.DynamicRealmObjectTests.SupportedType.INT -> target.setInt(fieldName, 1)
//                    io.realm.DynamicRealmObjectTests.SupportedType.LONG -> target.setLong(fieldName, 1L)
//                    io.realm.DynamicRealmObjectTests.SupportedType.BYTE -> target.setByte(fieldName, 4.toByte())
//                    io.realm.DynamicRealmObjectTests.SupportedType.FLOAT -> target.setFloat(fieldName, 1.23f)
//                    io.realm.DynamicRealmObjectTests.SupportedType.DOUBLE -> target.setDouble(fieldName, 1.23)
//                    io.realm.DynamicRealmObjectTests.SupportedType.STRING -> target.setString(fieldName, "foo")
//                    io.realm.DynamicRealmObjectTests.SupportedType.BINARY -> target.setBlob(fieldName, byteArrayOf())
//                    io.realm.DynamicRealmObjectTests.SupportedType.DATE -> target.getDate(fieldName)
//                    io.realm.DynamicRealmObjectTests.SupportedType.DECIMAL128 -> target.getDecimal128(fieldName)
//                    io.realm.DynamicRealmObjectTests.SupportedType.OBJECT_ID -> target.getObjectId(fieldName)
//                    io.realm.DynamicRealmObjectTests.SupportedType.UUID -> target.getUUID(fieldName)
//                    io.realm.DynamicRealmObjectTests.SupportedType.OBJECT -> {
//                        target.setObject(fieldName, null)
//                        target.setObject(fieldName, target)
//                    }
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST -> target.setList(fieldName, RealmList<DynamicRealmObject>())
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_INTEGER -> target.setList(fieldName, RealmList<Int>(1))
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_STRING -> target.setList(fieldName, RealmList<String>("foo"))
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_BOOLEAN -> target.setList(fieldName, RealmList<Boolean>(true))
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_FLOAT -> target.setList(fieldName, RealmList<Float>(1.23f))
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_DOUBLE -> target.setList(fieldName, RealmList<Double>(1.234))
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_BINARY -> target.setList(fieldName, RealmList<ByteArray>(byteArrayOf()))
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_DATE -> target.setList(fieldName, RealmList<Date>(Date()))
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_DECIMAL128 -> target.setList(fieldName, RealmList<E>(Decimal128(BigDecimal.ONE)))
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_OBJECT_ID -> target.setList(fieldName, RealmList<E>(ObjectId(TestHelper.generateObjectIdHexString(7))))
//                    io.realm.DynamicRealmObjectTests.SupportedType.LIST_UUID -> target.setList(fieldName, RealmList<E>(UUID.randomUUID()))
//                    else -> Assert.fail()
//                }
//            }
//        }
//    }
}
