package io.realm

import android.support.test.runner.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class InstrumentedTests {

    @Test
    fun contextIsNotNull() {
        assertNotNull(RealmInitProvider.applicationContext)
    }

    @Test
    fun cinterop_swig() {
        System.loadLibrary("realmc")
        println(io.realm.interop.RealmInterop.realm_get_library_version())
    }

//    @Test
//    fun createObject() {
//        val configuration = RealmConfiguration.Builder()
//            .name("createObject")
//            .factory(TestUtils.factory())
//            .build()
//
//        val realm = Realm.open(configuration)
//
////        println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>> START SLEEPING")
////        SystemClock.sleep(10000 * 120)
////        println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>> END SLEEPING")
//        realm.beginTransaction()
//        val managedPerson = realm.create(Person::class)
//        managedPerson.age = 2
//        managedPerson.name = "Sophia"
//        realm.commitTransaction()
//
//        assertEquals(2, managedPerson.age)
//        assertEquals("Sophia", managedPerson.name)
//    }


//    @Test
//    fun queryObjects() {
//        val configuration = RealmConfiguration.Builder()
//            .path(TestUtils.realmDefaultDirectory())
//            .name("queryObjects8")
//            .factory(TestUtils.factory())
//            .build()
//
//        val realm = Realm.open(configuration)
//
//        realm.beginTransaction()
//        val managedPerson1 = realm.create(Person::class)
//        val managedPerson2 = realm.create(Person::class)
//
//        managedPerson1.name = "Foo"
//        managedPerson1.age = 42
//
//        managedPerson2.name = "FooBar"
//        managedPerson2.age = 17
//        realm.commitTransaction()
//
//        val objects: RealmResults<Person> = realm.objects<Person>(Person::class, "name beginswith \"Foo\" SORT(name DESCENDING)")
//
//        assertEquals(2, objects.size)
//        val obj1 = objects[0]
//        val obj2 = objects[1]
//
//        assertEquals("Foo", obj1.name)
//        assertEquals(42, obj1.age)
//        assertEquals("FooBar", obj2.name)
//        assertEquals(17, obj2.age)
//    }

}
