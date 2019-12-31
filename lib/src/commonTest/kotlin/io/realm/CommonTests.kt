package io.realm

import realm.Realm
import realm.RealmConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals

class CommonTests {

    @Test
    fun createObject() {
        val configuration = RealmConfiguration.Builder()
            .path(TestUtils.realmDefaultDirectory())
            .name("hello_zepp2").build()

        val realm = Realm.open(configuration)

        val person = TestUtils.getPerson()

        realm.beginTransaction()
        val managedPerson = realm.save(person)
        managedPerson.age = 42
        managedPerson.name = "Sophia"
        realm.commitTransaction()

        person.age = 7
        person.name = "__"

        assertEquals(42, managedPerson.age)
        assertEquals("Sophia", managedPerson.name)
    }
}