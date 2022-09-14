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

package io.realm.kotlin.entities.sync

import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.schema.RealmStorageType
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlin.random.Random

private typealias FieldDataFactory = (SyncObjectWithAllTypes) -> Unit
private typealias FieldValidator = (SyncObjectWithAllTypes) -> Unit

@Suppress("MagicNumber")
class SyncObjectWithAllTypes : RealmObject {
    @PrimaryKey
    @Suppress("VariableNaming")
    var _id: String = "id-${Random.nextLong()}"

    // Non-nullable types
    var stringField: String = "hello world"
    var byteField: Byte = 0
    var charField: Char = 0.toChar()
    var shortField: Short = 0
    var intField: Int = 0
    var longField: Long = 0
    var booleanField: Boolean = true
    var doubleField: Double = 0.0
    var floatField: Float = 0.0.toFloat()
    var realmInstantField: RealmInstant = RealmInstant.MIN
    var objectIdField: ObjectId = ObjectId.create()
    var realmUUIDField: RealmUUID = RealmUUID.random()
    var binaryField: ByteArray = byteArrayOf(42)
    var objectField: SyncObjectWithAllTypes? = null

    // Nullable types
    var stringNullableField: String? = null
    var byteNullableField: Byte? = null
    var charNullableField: Char? = null
    var shortNullableField: Short? = null
    var intNullableField: Int? = null
    var longNullableField: Long? = null
    var booleanNullableField: Boolean? = null
    var doubleNullableField: Double? = null
    var floatNullableField: Float? = null
    var realmInstantNullableField: RealmInstant? = null
    var objectIdNullableField: ObjectId? = null
    var realmUUIDNullableField: RealmUUID? = null
    var binaryNullableField: ByteArray? = null
    var objectNullableField: SyncObjectWithAllTypes? = null

    // RealmLists
    var stringRealmList: RealmList<String> = realmListOf("hello world")
    var byteRealmList: RealmList<Byte> = realmListOf(0)
    var charRealmList: RealmList<Char> = realmListOf(0.toChar())
    var shortRealmList: RealmList<Short> = realmListOf(0)
    var intRealmList: RealmList<Int> = realmListOf(0)
    var longRealmList: RealmList<Long> = realmListOf(0)
    var booleanRealmList: RealmList<Boolean> = realmListOf(true)
    var doubleRealmList: RealmList<Double> = realmListOf(0.0)
    var floatRealmList: RealmList<Float> = realmListOf(0.0.toFloat())
    var realmInstantRealmList: RealmList<RealmInstant> = realmListOf(RealmInstant.MIN)
    var objectIdRealmList: RealmList<ObjectId> = realmListOf(ObjectId.create())
    var realmUUIDRealmList: RealmList<RealmUUID> = realmListOf(RealmUUID.random())
    var binaryRealmList: RealmList<ByteArray> = realmListOf(byteArrayOf(42))
    var objectRealmList: RealmList<SyncObjectWithAllTypes> = realmListOf()

    // Nullable RealmLists of primitive values, not currently supported by Sync
    // Nullable Object lists, not currently supported by Core

    // RealmSets
    var stringRealmSet: RealmSet<String> = realmSetOf("hello world")
    var byteRealmSet: RealmSet<Byte> = realmSetOf(0)
    var charRealmSet: RealmSet<Char> = realmSetOf(0.toChar())
    var shortRealmSet: RealmSet<Short> = realmSetOf(0)
    var intRealmSet: RealmSet<Int> = realmSetOf(0)
    var longRealmSet: RealmSet<Long> = realmSetOf(0)
    var booleanRealmSet: RealmSet<Boolean> = realmSetOf(true)
    var doubleRealmSet: RealmSet<Double> = realmSetOf(0.0)
    var floatRealmSet: RealmSet<Float> = realmSetOf(0.0.toFloat())
    var realmInstantRealmSet: RealmSet<RealmInstant> = realmSetOf(RealmInstant.MIN)
    var objectIdRealmSet: RealmSet<ObjectId> = realmSetOf(ObjectId.create())
    var realmUUIDRealmSet: RealmSet<RealmUUID> = realmSetOf(RealmUUID.random())
    var binaryRealmSet: RealmSet<ByteArray> = realmSetOf(byteArrayOf(42))
    var objectRealmSet: RealmSet<SyncObjectWithAllTypes> = realmSetOf()

    // RealmSets of nullable primitive values, not currently supported by Sync
    // RealmSets of nullable objects, not currently supported by Core

    companion object {

        // Mapping between each Core Field type and functions that can insert data for that type
        // and also verify the value. This can be used to test objects that has been roundtripped
        // through Sync.
        private val mapper: Map<RealmStorageType, Pair<FieldDataFactory, FieldValidator>> =
            mutableMapOf<RealmStorageType, Pair<FieldDataFactory, FieldValidator>>()
                .also { map ->
                    RealmStorageType.values().forEach { type: RealmStorageType ->
                        map[type] = when (type) {
                            RealmStorageType.INT -> {
                                Pair(
                                    { obj: SyncObjectWithAllTypes ->
                                        obj.intField = 42
                                        obj.intNullableField = 42
                                        obj.intRealmList = realmListOf(42)
                                        obj.intRealmSet = realmSetOf(42)
                                    },
                                    { obj: SyncObjectWithAllTypes ->
                                        assertEquals(42, obj.intField)
                                        assertEquals(42, obj.intNullableField)
                                        assertEquals(42, obj.intRealmList.first())
                                        assertSetContains(42, obj.intRealmSet)
                                    }
                                )
                            }
                            RealmStorageType.BOOL -> {
                                Pair(
                                    { obj: SyncObjectWithAllTypes ->
                                        obj.booleanField = true
                                        obj.booleanNullableField = true
                                        obj.booleanRealmList = realmListOf(true, false)
                                        obj.booleanRealmSet = realmSetOf(true, false)
                                    },
                                    { obj: SyncObjectWithAllTypes ->
                                        assertEquals(true, obj.booleanField)
                                        assertEquals(true, obj.booleanNullableField)
                                        assertEquals(true, obj.booleanRealmList[0])
                                        assertEquals(false, obj.booleanRealmList[1])
                                        assertSetContains(true, obj.booleanRealmSet)
                                        assertSetContains(false, obj.booleanRealmSet)
                                    }
                                )
                            }
                            RealmStorageType.STRING -> {
                                Pair(
                                    { obj: SyncObjectWithAllTypes ->
                                        obj.stringField = "Foo"
                                        obj.stringNullableField = "Bar"
                                        obj.stringRealmList = realmListOf("Foo", "")
                                        obj.stringRealmSet = realmSetOf("Foo", "")
                                    },
                                    { obj: SyncObjectWithAllTypes ->
                                        assertEquals("Foo", obj.stringField)
                                        assertEquals("Bar", obj.stringNullableField)
                                        assertEquals("Foo", obj.stringRealmList[0])
                                        assertEquals("", obj.stringRealmList[1])
                                        assertSetContains("Foo", obj.stringRealmSet)
                                        assertSetContains("", obj.stringRealmSet)
                                    }
                                )
                            }
                            RealmStorageType.OBJECT -> {
                                Pair(
                                    { obj: SyncObjectWithAllTypes ->
                                        obj.objectField = SyncObjectWithAllTypes().apply {
                                            stringField = "child1"
                                        }
                                        obj.objectNullableField = null
                                        obj.objectRealmList =
                                            realmListOf(
                                                SyncObjectWithAllTypes().apply {
                                                    stringField = "child2"
                                                }
                                            )
                                        obj.objectRealmSet =
                                            realmSetOf(
                                                SyncObjectWithAllTypes().apply {
                                                    stringField = "child2"
                                                }
                                            )
                                    },
                                    { obj: SyncObjectWithAllTypes ->
                                        assertEquals("child1", obj.objectField!!.stringField)
                                        assertEquals(null, obj.objectNullableField)
                                        assertEquals(
                                            "child2",
                                            obj.objectRealmList.first().stringField
                                        )
                                        assertSetContainsObject("child2", obj.objectRealmSet)
                                    }
                                )
                            }
                            RealmStorageType.FLOAT -> {
                                Pair(
                                    { obj: SyncObjectWithAllTypes ->
                                        obj.floatField = 1.23F
                                        obj.floatNullableField = 1.23F
                                        obj.floatRealmList =
                                            realmListOf(1.23F, Float.MIN_VALUE, Float.MAX_VALUE)
                                        obj.floatRealmSet =
                                            realmSetOf(1.23F, Float.MIN_VALUE, Float.MAX_VALUE)
                                    },
                                    { obj: SyncObjectWithAllTypes ->
                                        assertEquals(1.23F, obj.floatField)
                                        assertEquals(1.23F, obj.floatNullableField)
                                        assertEquals(1.23F, obj.floatRealmList[0])
                                        assertEquals(Float.MIN_VALUE, obj.floatRealmList[1])
                                        assertEquals(Float.MAX_VALUE, obj.floatRealmList[2])
                                        assertSetContains(Float.MIN_VALUE, obj.floatRealmSet)
                                        assertSetContains(Float.MAX_VALUE, obj.floatRealmSet)
                                    }
                                )
                            }
                            RealmStorageType.DOUBLE -> {
                                Pair(
                                    { obj: SyncObjectWithAllTypes ->
                                        obj.doubleField = 1.234
                                        obj.doubleNullableField = 1.234
                                        obj.doubleRealmList =
                                            realmListOf(1.234, Double.MIN_VALUE, Double.MAX_VALUE)
                                        obj.doubleRealmSet =
                                            realmSetOf(1.234, Double.MIN_VALUE, Double.MAX_VALUE)
                                    },
                                    { obj: SyncObjectWithAllTypes ->
                                        assertEquals(1.234, obj.doubleField)
                                        assertEquals(1.234, obj.doubleNullableField)
                                        assertEquals(1.234, obj.doubleRealmList[0])
                                        assertEquals(Double.MIN_VALUE, obj.doubleRealmList[1])
                                        assertEquals(Double.MAX_VALUE, obj.doubleRealmList[2])
                                        assertSetContains(Double.MIN_VALUE, obj.doubleRealmSet)
                                        assertSetContains(Double.MAX_VALUE, obj.doubleRealmSet)
                                    },
                                )
                            }
                            RealmStorageType.TIMESTAMP -> {
                                Pair(
                                    { obj: SyncObjectWithAllTypes ->
                                        obj.realmInstantField = RealmInstant.from(1, 1)
                                        obj.realmInstantNullableField =
                                            RealmInstant.from(-1, -1)
                                        obj.realmInstantRealmList =
                                            realmListOf(RealmInstant.MIN, RealmInstant.MAX)
                                        obj.realmInstantRealmSet =
                                            realmSetOf(RealmInstant.MIN, RealmInstant.MAX)
                                    },
                                    { obj: SyncObjectWithAllTypes ->
                                        assertEquals(
                                            RealmInstant.from(1, 1),
                                            obj.realmInstantField
                                        )
                                        assertEquals(
                                            RealmInstant.from(-1, -1),
                                            obj.realmInstantNullableField
                                        )
                                        assertEquals(RealmInstant.MIN, obj.realmInstantRealmList[0])
                                        assertEquals(RealmInstant.MAX, obj.realmInstantRealmList[1])
                                        assertSetContains(
                                            RealmInstant.MIN,
                                            obj.realmInstantRealmSet
                                        )
                                        assertSetContains(
                                            RealmInstant.MAX,
                                            obj.realmInstantRealmSet
                                        )
                                    },
                                )
                            }
                            RealmStorageType.OBJECT_ID -> {
                                val minObjId = ObjectId.from("000000000000000000000000")
                                val maxObjId = ObjectId.from("ffffffffffffffffffffffff")
                                val randomObjId = ObjectId.from("503f1f77bcf86cd793439011")
                                Pair(
                                    { obj: SyncObjectWithAllTypes ->
                                        obj.objectIdField = randomObjId
                                        obj.objectIdNullableField = randomObjId
                                        obj.objectIdRealmList = realmListOf(minObjId, maxObjId)
                                        obj.objectIdRealmSet = realmSetOf(minObjId, maxObjId)
                                    },
                                    { obj: SyncObjectWithAllTypes ->
                                        assertEquals(randomObjId, obj.objectIdField)
                                        assertEquals(randomObjId, obj.objectIdNullableField)
                                        assertEquals(minObjId, obj.objectIdRealmList[0])
                                        assertEquals(maxObjId, obj.objectIdRealmList[1])
                                        assertSetContains(minObjId, obj.objectIdRealmSet)
                                        assertSetContains(maxObjId, obj.objectIdRealmSet)
                                    },
                                )
                            }
                            RealmStorageType.UUID -> {
                                val uuid1 = RealmUUID.random()
                                val uuid2 = RealmUUID.random()
                                val uuid3 = RealmUUID.random()
                                Pair(
                                    { obj: SyncObjectWithAllTypes ->
                                        obj.realmUUIDField = uuid1
                                        obj.realmUUIDNullableField = uuid1
                                        obj.realmUUIDRealmList = realmListOf(uuid2, uuid3)
                                        obj.realmUUIDRealmSet = realmSetOf(uuid2, uuid3)
                                    },
                                    { obj: SyncObjectWithAllTypes ->
                                        assertEquals(uuid1, obj.realmUUIDField)
                                        assertEquals(uuid1, obj.realmUUIDNullableField)
                                        assertEquals(uuid2, obj.realmUUIDRealmList[0])
                                        assertEquals(uuid3, obj.realmUUIDRealmList[1])
                                        assertSetContains(uuid2, obj.realmUUIDRealmSet)
                                        assertSetContains(uuid3, obj.realmUUIDRealmSet)
                                    },
                                )
                            }
                            RealmStorageType.BINARY -> {
                                Pair(
                                    { obj: SyncObjectWithAllTypes ->
                                        obj.binaryField = byteArrayOf(22)
                                        obj.binaryNullableField = byteArrayOf(22)
                                        obj.binaryRealmList = realmListOf(
                                            byteArrayOf(22),
                                            byteArrayOf(44, 66),
                                            byteArrayOf(11, 33)
                                        )
                                        obj.binaryRealmSet = realmSetOf(
                                            byteArrayOf(22),
                                            byteArrayOf(44, 66),
                                            byteArrayOf(11, 33)
                                        )
                                    },
                                    { obj: SyncObjectWithAllTypes ->
                                        assertContentEquals(
                                            byteArrayOf(22),
                                            obj.binaryField
                                        )
                                        assertContentEquals(
                                            byteArrayOf(22),
                                            obj.binaryNullableField
                                        )
                                        assertContentEquals(
                                            byteArrayOf(22),
                                            obj.binaryRealmList[0]
                                        )
                                        assertContentEquals(
                                            byteArrayOf(44, 66),
                                            obj.binaryRealmList[1]
                                        )
                                        assertContentEquals(
                                            byteArrayOf(11, 33),
                                            obj.binaryRealmList[2]
                                        )
                                        assertSetContainsBinary(
                                            byteArrayOf(22),
                                            obj.binaryRealmSet
                                        )
                                        assertSetContainsBinary(
                                            byteArrayOf(44, 66),
                                            obj.binaryRealmSet
                                        )
                                        assertSetContainsBinary(
                                            byteArrayOf(11, 33),
                                            obj.binaryRealmSet
                                        )
                                    },
                                )
                            }
                            else -> TODO("Missing support for type: $type")
                        }
                    }
                }

        private fun assertEquals(value: Any?, other: Any?) {
            if (value != other) {
                throw IllegalStateException("Values do not match: '$value' vs. '$other'")
            }
        }

        private fun assertSetContains(value: Any?, set: RealmSet<*>) {
            if (!set.contains(value)) {
                throw IllegalStateException("Set doesn't contain value $value")
            }
        }

        // Sets don't expose indices so we need to iterate them
        private fun assertSetContainsObject(value: String, set: RealmSet<SyncObjectWithAllTypes>) {
            var found = false
            val iterator = set.iterator()
            while (iterator.hasNext()) {
                val obj = iterator.next()
                if (obj.stringField == value) {
                    found = true
                }
            }
            if (!found) {
                throw IllegalStateException("Set doesn't contain object with 'stringField' value '$value'")
            }
        }

        // Similarly we need to iterate over the set and see if the binary contests are the same
        private fun assertSetContainsBinary(value: ByteArray, set: RealmSet<ByteArray>) {
            val iterator = set.iterator()
            var found = false
            while (iterator.hasNext()) {
                val byteArray = iterator.next()
                if (value.contentEquals(byteArray)) {
                    found = true
                }
            }
            if (!found) {
                throw IllegalStateException("Set does not contain ByteArray $value")
            }
        }

        private fun assertContentEquals(value: ByteArray?, other: ByteArray?) {
            value?.forEachIndexed { index, byte ->
                val actual = other?.get(index)
                if (byte != actual) {
                    throw IllegalStateException("Values do not match: '$byte' vs. '$actual'")
                }
            }
        }

        /**
         * Create an object with sample data for all supported Core field types.
         */
        fun createWithSampleData(primaryKey: String): SyncObjectWithAllTypes {
            return SyncObjectWithAllTypes().also { obj ->
                obj._id = primaryKey
                RealmStorageType.values().forEach { type: RealmStorageType ->
                    val dataFactory: FieldDataFactory = mapper[type]!!.first
                    dataFactory(obj)
                }
            }
        }

        /**
         * Validate that the incoming object has all the expected sample data
         *
         * @return `true` if the object matches.
         * @throws IllegalStateException if the comparison failed.
         */
        fun compareAgainstSampleData(obj: SyncObjectWithAllTypes): Boolean {
            RealmStorageType.values().forEach { type: RealmStorageType ->
                val dataValidator: FieldValidator = mapper[type]!!.second
                dataValidator(obj)
            }
            return true
        }
    }
}
