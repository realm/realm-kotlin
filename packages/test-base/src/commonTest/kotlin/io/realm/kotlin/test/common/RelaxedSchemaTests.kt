import io.realm.kotlin.types.RealmObject

///*
// * Copyright 2024 Realm Inc.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package io.realm.kotlin.test.common
//
//import io.realm.kotlin.Realm
//import io.realm.kotlin.RealmConfiguration
//import io.realm.kotlin.dynamic.DynamicRealmObject
//import io.realm.kotlin.entities.Sample
//import io.realm.kotlin.ext.query
//import io.realm.kotlin.internal.asDynamicRealm
//import io.realm.kotlin.internal.platform.runBlocking
//import io.realm.kotlin.query.RealmQuery
//import io.realm.kotlin.query.RealmResults
//import io.realm.kotlin.schema.RealmClass
//import io.realm.kotlin.schema.RealmProperty
//import io.realm.kotlin.schema.RealmSchema
//import io.realm.kotlin.schema.ValuePropertyType
//import io.realm.kotlin.test.common.utils.assertFailsWithMessage
//import io.realm.kotlin.test.platform.PlatformUtils
//import io.realm.kotlin.types.RealmAny
//import io.realm.kotlin.types.RealmList
//import io.realm.kotlin.types.RealmObject
////import io.realm.kotlin.types.extras
//import io.realm.kotlin.types.get
//import io.realm.kotlin.types.removeProperty
//import io.realm.kotlin.types.set
//import io.realm.kotlin.types.setIfNotPresent
//import io.realm.kotlin.types.setIfPresent
//import kotlin.test.AfterTest
//import kotlin.test.BeforeTest
//import kotlin.test.Test
//
//class RelaxedSchemaTests {
//
//    lateinit var realm: Realm
//    private lateinit var tmpDir: String
//
//    @BeforeTest
//    fun setup() {
//        val configuration = RealmConfiguration.Builder(setOf(Sample::class, A::class))
//            .relaxedSchema(true)
//            .directory(tmpDir)
//            .build()
//        realm = Realm.open(configuration)
//    }
//
//    @AfterTest
//    fun tearDown() {
//        if (this::realm.isInitialized && !realm.isClosed()) {
//            realm.close()
//        }
//        PlatformUtils.deleteTempDir(tmpDir)
//    }
//
//
//    @Test
//    fun basic() = runBlocking<Unit> {
//
////        val configuration = RealmConfiguration.Builder(setOf(Sample::class, A::class))
////            .relaxedSchema(true)
////            .build()
////        Realm.deleteRealm(configuration)
////        val realm = Realm.open(configuration)
//        val schema: RealmSchema = realm.schema()
//        val table: RealmClass = schema["Sample"]!!
//        val schemaProperties: Collection<RealmProperty> = table.properties
//        val property: RealmProperty = schemaProperties.first()
////        property.run {
////            this.name
////            this.isNullable
////            this.type.run {
////                this.isNullable
////                this.storageType
////                this.collectionType
////                when (this) {
////                    is ListPropertyType -> TODO()
////                    is MapPropertyType -> TODO()
////                    is SetPropertyType -> TODO()
////                    is ValuePropertyType -> TODO()
////                }
////            }
////        }
//        var instance = realm.write {
//            copyToRealm(Sample())
//        }
//
//        // Iterate all strict properties dynamically (as RealmAny)
////        val x1: RealmList<String> = instance["strinaListField"]
//        val x2: RealmAny = instance.get("stringListField")
//        val x3: RealmList<String> = instance["stringListField"]
//        val x4: RealmAny = instance["stringListField"]
//        val x = schemaProperties.forEach {
////            if (it.type is ValuePropertyType) {
//                val y: RealmAny = instance[it.name]
//                println("instance[${it.name}] = $y")
////            }
//        }
//
//        instance = realm.write {
//            val instance = findLatest(instance)!!
//            instance["prop3"] = RealmAny.create("Realm")
//            instance["prop4"] = RealmAny.create(5)
//            instance
//        }
//        // Iterate all strict+relaxed properties dynamically (as RealmAny)
//        // Must be from instance as non-strict properties are differing across instances
//        instance.extraProperties.forEach { it ->
//            val y = instance.get<RealmAny>(it)
//            println("instance.extras[${it}] = $y")
//        }
//
//        // Iterate relaxed properties dynamically (as RealmAny)
//        // Must be from instance as non-strict properties are differing across instances
//        instance.extraProperties.forEach { it ->
//            if (table["it"] == null) {
//                instance.get<RealmAny>(it)
//            }
//        }
//
//        instance.extraProperties.forEach { it ->
//            instance.get<RealmAny>(it)
//        }
//        // Can only remove extra properties, hence must not contain realm-schema-props
//        realm.write {
//            val instance1 = findLatest(instance)!!
//            instance1.extraProperties.forEach {
//                println("Removed: $it ${instance1.removeProperty(it)}")
//            }
//        }
//
//        // What about these shortcuts for common patterns
////        if (instance.hasProperty("age")) {
////            instance.set("age", 6)
////        }
////        val present1 = instance.setIfPresent("age", 6)
////        if (!instance.hasProperty("age")) {
////            instance.set("age", 6)
////        }
////        val present1 = instance.setIfNotPresent("age", 6)
//
//        instance = realm.write {
//            val obj = instance
//            val present2 = findLatest(obj)!!.setIfNotPresent("age", RealmAny.create("asdfasdf"))
//            obj
//        }
//
//        //
//        // Strict properties
//        realm.schema()["Sample"]!!.properties.forEach { realmProperty: RealmProperty ->
//            if (realmProperty.type is ValuePropertyType) {
//                instance.get<RealmAny>(realmProperty.name)
//            }
//        }
//        // If this is too verbose, we could make explicit accessors for the individual selections
//        // all-, dataModel-, realmSchema-, extra-Properties
//        instance.extraProperties.forEach { it ->
//            val y = instance.get<RealmAny>(it)
//            println("instance.extras[${it}] = $y")
//        }
//
//        // EXTRAS
//        // Pretty neat, but how to keep things in Extras-namespace
////        realm.write {
////            findLatest(instance)!!.extras["asdf"] = RealmAny.create("ASDf")
////        }
//
//        // Non-strict properties
////        instance.extraProperties().forEach { it -> instance.get(it) }
//
////        val x: DynamicRealm
////        val y = x.query("Sample")
////        val dynamicSample: DynamicRealmObject = y.find().get(0)
////        dynamicSample.getNullableValue<String>("prop")
//
//        // ?? Do we need some RealmProperty? from RealmObject instances
//        // - We would newer have different type than RealmAny
//        //   Are there any RealmProperty information that is interesting
//        //   - Can we set/get/erase based on the type of property (model, schema, extra)
//        // - Think it would be extremely heavy to pull RealmProperty details
//        //   - And if there is no concept of a server schema then I can't see why we need the info
//    }
//
//    @Test
//    fun createRealm() = runBlocking<Unit> {
//        val configuration = RealmConfiguration.Builder(setOf(A::class))
//            .relaxedSchema(true)
//            .build()
//        val realm = Realm.open(configuration)
//        println("schema: ${realm.schema()}")
//        println("As: ${realm.query<A>().find().size}")
//        val x = realm.write {
//            val x = copyToRealm(A())
//            println("PROPS: ${x.extraProperties}")
//            x["PROP"] = RealmAny.create(34)
//            x
//        }
//        println("PROPS: ${x.extraProperties}")
//        for (property in x.extraProperties) {
//            println("PROP[$property] = ${x.get<RealmAny>(property)}")
//        }
//    }
//
//    @Test
//    fun iterateProperties() = runBlocking {
//        val configuration = RealmConfiguration.Builder(setOf(A::class))
//            .relaxedSchema(true)
//            .build()
//        val realm = Realm.open(configuration)
//        realm.write {
//            delete(query<A>())
//        }
//        println("schema: ${realm.schema()}")
//        println("As: ${realm.query<A>().find().size}")
//        val x = realm.write {
//            val x = copyToRealm(A())
//            println("PROPS: ${x.extraProperties}")
//            x["PROP"] = RealmAny.create(34)
//            x
//        }
//        println("PROPS: ${x.extraProperties}")
//        //
//        val properties = realm.schema()["A"]!!.properties
//        properties.forEach {
////            println("PROP[${it.name}] = ${(it.accessor as KMutableProperty1<A, String?>?)!!.get(x)}")
////            println("PROP[${it.name}] = ${(it.accessor as KMutableProperty1<A, String?>?)!!.set(x, null)}")
//        }
//        for (property in x.extraProperties) {
//            println("PROP[$property] = ${x.get<RealmAny>(property)}")
//        }
//
//        // TODO Maybe have a speedier way to get to the dynamic object
//        val result: RealmQuery<out DynamicRealmObject> = realm.asDynamicRealm().query("A")
//        val find: RealmResults<out DynamicRealmObject> = result.find()
//
//        find[0].run {
//
//            // Can be iterated across instances
//            val allProps = properties.map { it.name } + x.extraProperties
//            println("allProps: $allProps")
//            for (allProp in allProps) {
//                // Needs to be generalised to DynamicObject.get<T>(name: String) :thinking: RealmAny vs. T vs. Any
////                this.get<>()
//                println("property: $allProp")
//                val y: String = x[allProp]
////                x.asdf<String>(allProp)
//                println("x[$allProp]: String = ${x.get<String>(allProp)}")
//                println("this[$allProp]: RealmAny = ${this.get<RealmAny>(allProp)}")
//            }
//        }
//
//    }
//
//    // Tests
//    // - Unmanaged objects
//    // - Non-existing keys!?
//    // - Open non-relaxed file with relaxed flag
//    // - open relaxed file without relaxed flag
//    // - Sync?
//
//    // - Embedded objects import and assignment - Maybe more of an dynamic API test
//
//    // TEST
//    // - Add property outside write
//    // - Remove property outside write
//    @Test
//    fun removeModelPropertyThrows() {
//        // Can we remove a property from the schema - Needs to be in a write transaction
//    }
//
//    @Test
//    fun removeSchemaPropertyThrows() {
//        // Can we remove a property from the schema - Needs to be in a write transaction
//    }
//
//    @Test
//    fun unmanagedObject_throws() {
//        val sample = Sample()
//        assertFailsWithMessage<IllegalStateException>("Cannot access or add additional properties to unmanaged object") {
//            sample["unknown"]
//        }
//        assertFailsWithMessage<IllegalStateException>("Cannot access or add additional properties to unmanaged object") {
//            sample["unknown"] = "TEST"
//        }
//    }
//}
//
class A : RealmObject {
    var id: String = "DEFAULT"
}

class B : RealmObject {
    var id: String = "DEFAULT"
}
