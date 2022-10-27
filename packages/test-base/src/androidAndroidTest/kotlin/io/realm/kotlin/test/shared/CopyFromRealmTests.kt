@file:Suppress("invisible_reference", "invisible_member")
package io.realm.kotlin.test.shared

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.entities.embedded.embeddedSchema
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.internal.RealmObjectInternal
import io.realm.kotlin.internal.realmObjectCompanionOrThrow
import io.realm.kotlin.internal.schema.ClassMetadata
import io.realm.kotlin.schema.ListPropertyType
import io.realm.kotlin.schema.RealmProperty
import io.realm.kotlin.schema.RealmStorageType
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmUUID
import kotlin.reflect.KMutableProperty1
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.fail

class CopyFromRealmTests {

    lateinit var tmpDir: String
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class) + embeddedSchema)
            .directory(tmpDir)
            .build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (!realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun simpleValues() {
        // TODO We should also verify that updated values are actually copied. Currently it might work by accident due to default values
        val insertedObj = realm.writeBlocking {
            copyToRealm(Sample())
        }
        val unmanagedObj = insertedObj.copyFromRealm()
        val managedObj = realm.query(Sample::class).first().find()!!

        assertFalse(unmanagedObj.isManaged())

        val metadata: ClassMetadata = (managedObj as RealmObjectInternal).io_realm_kotlin_objectReference!!.metadata
        metadata.properties.forEach {
            when (val value: Any? = it.acccessor!!.get(unmanagedObj)) {
                is List<*> -> { /* Ignore */ }
                is Set<*> -> { /* Ignore */ }
                is ByteArray -> assertContentEquals(value, value)
                else -> {
                    // All object references will be `null` here
                    assertEquals(it.acccessor!!.get(managedObj), value, "${it.name} failed")
                }
            }
        }
    }

    @Test
    fun objectReferences() {
        val innerSample = Sample().apply { stringField = "inner" }

        val insertedObj = realm.writeBlocking {
            copyToRealm(Sample().apply { nullableObject = innerSample })
        }
        val unmanagedObj: Sample = insertedObj.copyFromRealm()
        assertNotSame(insertedObj, unmanagedObj)
        assertNotNull(unmanagedObj.nullableObject)
        val innerCopy = unmanagedObj.nullableObject!!
        assertFalse(innerCopy.isManaged())
        assertEquals("inner", innerCopy.stringField)
    }

    @Test
    fun primitiveLists() {
        val type = Sample::class
        val schemaProperties = type.realmObjectCompanionOrThrow().io_realm_kotlin_schema().properties
        val fields: Map<String, KMutableProperty1<*, *>> = type.realmObjectCompanionOrThrow().io_realm_kotlin_fields

        // Dynamically set data on the Sample object
        val originalObject = Sample()
        schemaProperties.forEach { prop: RealmProperty ->
            if (prop.type is ListPropertyType) {
                val accessor: KMutableProperty1<BaseRealmObject, Any?> = fields[prop.name] as KMutableProperty1<BaseRealmObject, Any?>
                val list: List<Any?> = createPrimitiveListData(prop, accessor)
                accessor.set(originalObject, list)
            }
        }

        // Round-trip object through `copyToRealm` and `copyFromRealm`.
        val unmanagedCopy = realm.writeBlocking {
            copyToRealm(originalObject).copyFromRealm()
        }

        // Validate that all primitive list fields were round-tripped correctly.
        schemaProperties.forEach { prop: RealmProperty ->
            if (prop.type is ListPropertyType) {
                val accessor: KMutableProperty1<BaseRealmObject, Any?> = fields[prop.name] as KMutableProperty1<BaseRealmObject, Any?>
                val list: List<Any?> = createPrimitiveListData(prop, accessor)

                if (prop.type.storageType == RealmStorageType.BINARY) {
                    val copy = accessor.get(unmanagedCopy) as List<ByteArray?>
                    assertEquals(list.size, copy.size)
                    copy.forEachIndexed { i, el: ByteArray? ->
                        assertContentEquals(list[i] as ByteArray?, el, "$i failed")
                    }
                } else {
                    assertContentEquals(list, accessor.get(unmanagedCopy) as List<Any?>, "${prop.name} failed")
                }
            }
        }
    }

    // Create Sample data, for lists that can contain `null`, there is a `null` element in the middle.
    private fun createPrimitiveListData(
        prop: RealmProperty,
        accessor: KMutableProperty1<BaseRealmObject, Any?>
    ): List<Any?> {
        val type: String = accessor.returnType.toString()
        val typeDescription: String = type
            .removePrefix("io.realm.kotlin.types.RealmList<")
            .removeSuffix(">")
            .removeSuffix("?")
        val list: MutableList<Any?> = when (typeDescription) {
            "kotlin.String" -> realmListOf("foo", "bar")
            "kotlin.Byte" -> realmListOf(1.toByte(), 2.toByte())
            "kotlin.Char" -> realmListOf('a', 'b')
            "kotlin.Short" -> realmListOf(3.toShort(), 4.toShort())
            "kotlin.Int" -> realmListOf(5, 6)
            "kotlin.Long" -> realmListOf(7.toLong(), 8.toLong())
            "kotlin.Boolean" -> realmListOf(true, false)
            "kotlin.Float" -> realmListOf(1.23.toFloat(), 1.34.toFloat())
            "kotlin.Double" -> realmListOf(1.234, 1.345)
            "kotlin.ByteArray" -> realmListOf(byteArrayOf(42), byteArrayOf(43))
            "io.realm.kotlin.types.RealmInstant" -> realmListOf(RealmInstant.from(1, 0), RealmInstant.from(1, 1))
            "io.realm.kotlin.types.ObjectId" -> realmListOf(ObjectId.from("635a1a95184a200db8a07bfc"), ObjectId.from("735a1a95184a200db8a07bfc"))
            "io.realm.kotlin.types.RealmUUID" -> realmListOf(RealmUUID.from("defda04c-80ac-4ed9-86f5-334fef3dcf8a"), RealmUUID.from("eefda04c-80ac-4ed9-86f5-334fef3dcf8a"))
            "io.realm.kotlin.entities.Sample" -> realmListOf() // Object references are not part of this test
            else -> fail("Missing support for $typeDescription")
        }
        if (prop.isNullable) {
            list.add(1, null)
        }
        return list
    }
}
