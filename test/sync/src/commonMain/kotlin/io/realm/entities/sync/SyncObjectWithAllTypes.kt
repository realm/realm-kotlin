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

package io.realm.entities.sync

import io.realm.ObjectId
import io.realm.RealmInstant
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.realmListOf
import io.realm.test.util.TypeDescriptor
import kotlin.random.Random

private typealias FieldDataFactory = (SyncObjectWithAllTypes) -> Unit
private typealias FieldValidator = (SyncObjectWithAllTypes) -> Unit

class SyncObjectWithAllTypes : RealmObject {
    @PrimaryKey
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
    var objectRealmList: RealmList<SyncObjectWithAllTypes> = realmListOf()

    // Nullable RealmLists of primitive values, not currently supported by Sync
    // Nullable Object lists, not currently supported by Core

    companion object {

        // Mapping between each Core Field type and functions that can insert data for that type
        // and also verify the value. This can be used to test objects that has been roundtripped
        // through Sync.
        val mapper: Map<TypeDescriptor.CoreFieldType, Pair<FieldDataFactory, FieldValidator>> = createMapper()

        /**
         * Create mapping for both creating and validating sample data
         */
        private fun createMapper(): Map<TypeDescriptor.CoreFieldType, Pair<FieldDataFactory, FieldValidator>> {
            val map: MutableMap<TypeDescriptor.CoreFieldType, Pair<FieldDataFactory, FieldValidator>> = mutableMapOf()
            // TODO Problems with accessing TypeDescriptor enums before class is fully initialized
            //  work-around for now by accessing a property
            TypeDescriptor.classifiers
            TypeDescriptor.CoreFieldType.values().forEach { type: TypeDescriptor.CoreFieldType ->
                map[type] = when (type) {
                    TypeDescriptor.CoreFieldType.INT -> {
                        Pair(
                            { obj: SyncObjectWithAllTypes ->
                                obj.intField = 42
                                obj.intNullableField = 42
                                obj.intRealmList = realmListOf(42)
                            },
                            { obj: SyncObjectWithAllTypes ->
                                assertEquals(42, obj.intField)
                                assertEquals(42, obj.intNullableField)
                                assertEquals(42, obj.intRealmList.first())
                            }
                        )
                    }
                    TypeDescriptor.CoreFieldType.BOOL -> {
                        Pair(
                            { obj: SyncObjectWithAllTypes ->
                                obj.booleanField = true
                                obj.booleanNullableField = true
                                obj.booleanRealmList = realmListOf(true, false)
                            },
                            { obj: SyncObjectWithAllTypes ->
                                assertEquals(true, obj.booleanField)
                                assertEquals(true, obj.booleanNullableField)
                                assertEquals(true, obj.booleanRealmList[0])
                                assertEquals(false, obj.booleanRealmList[1])
                            }
                        )
                    }
                    TypeDescriptor.CoreFieldType.STRING -> {
                        Pair(
                            { obj: SyncObjectWithAllTypes ->
                                obj.stringField = "Foo"
                                obj.stringNullableField = "Bar"
                                obj.stringRealmList = realmListOf("Foo", "")
                            },
                            { obj: SyncObjectWithAllTypes ->
                                assertEquals("Foo", obj.stringField)
                                assertEquals("Bar", obj.stringNullableField)
                                assertEquals("Foo", obj.stringRealmList[0])
                                assertEquals("", obj.stringRealmList[1])
                            }
                        )
                    }
                    TypeDescriptor.CoreFieldType.OBJECT -> {
                        Pair(
                            { obj: SyncObjectWithAllTypes ->
                                obj.objectField = SyncObjectWithAllTypes().apply { stringField = "child1" }
                                obj.objectNullableField = null
                                obj.objectRealmList = realmListOf(SyncObjectWithAllTypes().apply { stringField = "child2" })
                            },
                            { obj: SyncObjectWithAllTypes ->
                                assertEquals("child1", obj.objectField!!.stringField)
                                assertEquals(null, obj.objectNullableField)
                                assertEquals("child2", obj.objectRealmList.first().stringField)
                            }
                        )
                    }
                    TypeDescriptor.CoreFieldType.FLOAT -> {
                        Pair(
                            { obj: SyncObjectWithAllTypes ->
                                obj.floatField = 1.23F
                                obj.floatNullableField = 1.23F
                                obj.floatRealmList = realmListOf(1.23F, Float.MIN_VALUE, Float.MAX_VALUE)
                            },
                            { obj: SyncObjectWithAllTypes ->
                                assertEquals(1.23F, obj.floatField)
                                assertEquals(1.23F, obj.floatNullableField)
                                assertEquals(1.23F, obj.floatRealmList[0])
                                assertEquals(Float.MIN_VALUE, obj.floatRealmList[1])
                                assertEquals(Float.MAX_VALUE, obj.floatRealmList[2])
                            }
                        )
                    }
                    TypeDescriptor.CoreFieldType.DOUBLE -> {
                        Pair(
                            { obj: SyncObjectWithAllTypes ->
                                obj.doubleField = 1.234
                                obj.doubleNullableField = 1.234
                                obj.doubleRealmList = realmListOf(1.234, Double.MIN_VALUE, Double.MAX_VALUE)
                            },
                            { obj: SyncObjectWithAllTypes ->
                                assertEquals(1.234, obj.doubleField)
                                assertEquals(1.234, obj.doubleNullableField)
                                assertEquals(1.234, obj.doubleRealmList[0])
                                assertEquals(Double.MIN_VALUE, obj.doubleRealmList[1])
                                assertEquals(Double.MAX_VALUE, obj.doubleRealmList[2])
                            },
                        )
                    }
                    TypeDescriptor.CoreFieldType.TIMESTAMP -> {
                        Pair(
                            { obj: SyncObjectWithAllTypes ->
                                obj.realmInstantField = RealmInstant.fromEpochSeconds(1, 1)
                                obj.realmInstantNullableField = RealmInstant.fromEpochSeconds(-1, -1)
                                obj.realmInstantRealmList = realmListOf(RealmInstant.MIN, RealmInstant.MAX)
                            },
                            { obj: SyncObjectWithAllTypes ->
                                assertEquals(RealmInstant.fromEpochSeconds(1, 1), obj.realmInstantField)
                                assertEquals(RealmInstant.fromEpochSeconds(-1, -1), obj.realmInstantNullableField)
                                assertEquals(RealmInstant.MIN, obj.realmInstantRealmList[0])
                                assertEquals(RealmInstant.MAX, obj.realmInstantRealmList[1])
                            },
                        )
                    }
                    TypeDescriptor.CoreFieldType.OBJECT_ID -> {
                        val minObjId = ObjectId.from("000000000000000000000000")
                        val maxObjId = ObjectId.from("ffffffffffffffffffffffff")
                        val randomObjId = ObjectId.from("503f1f77bcf86cd793439011")
                        Pair(
                            { obj: SyncObjectWithAllTypes ->
                                obj.objectIdField = randomObjId
                                obj.objectIdNullableField = randomObjId
                                obj.objectIdRealmList = realmListOf(minObjId, maxObjId)
                            },
                            { obj: SyncObjectWithAllTypes ->
                                assertEquals(randomObjId, obj.objectIdField)
                                assertEquals(randomObjId, obj.objectIdNullableField)
                                assertEquals(minObjId, obj.objectIdRealmList[0])
                                assertEquals(maxObjId, obj.objectIdRealmList[1])
                            },
                        )
                    }
                    else -> TODO("Missing support for type: $type")
                }
            }
            return map
        }

        private fun assertEquals(value: Any?, other: Any?) {
            if (value != other) {
                throw IllegalStateException("Values do not match: '$value' vs. '$other'")
            }
        }

        /**
         * Create an object with sample data for all supported Core field types.
         */
        fun createWithSampleData(primaryKey: String): SyncObjectWithAllTypes {
            return SyncObjectWithAllTypes().also { obj ->
                obj._id = primaryKey
                TypeDescriptor.CoreFieldType.values().forEach { type: TypeDescriptor.CoreFieldType ->
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
            TypeDescriptor.CoreFieldType.values().forEach { type: TypeDescriptor.CoreFieldType ->
                val dataValidator: FieldValidator = mapper[type]!!.second
                dataValidator(obj)
            }
            return true
        }
    }
}
