package sample

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmModel
import io.realm.RealmResults
import sample.model.Person
import sample.model.PersonProxy
import kotlin.reflect.KClass

expect fun path(): String

object PersonRepository {
    val realm: Realm by lazy {
        println("Realm location: ${path()}")

        val realmConfiguration = RealmConfiguration.Builder()
            .path(path())
            .name("people")
            .factory(modelFactory())
            .build()

        Realm.open(realmConfiguration)
    }

    fun adultsCount(): Int {
        val objects: RealmResults<Person> = realm.objects(Person::class, "age >= 18")
        return objects.size
    }

    fun minorsCount(): Int {
        val objects: RealmResults<Person> = realm.objects(Person::class, "age < 18")
        return objects.size
    }

    fun addPerson(name: String, age: Int) {
        realm.beginTransaction()
        val person = realm.create(Person::class)
        person.age = age
        person.name = name
        realm.commitTransaction()
    }

    // work around lack of codegen/reflection (could be solved by using a compiler plugin in the future)
    fun modelFactory(): (KClass<out RealmModel>) -> RealmModel {
        return {
            if (Person::class == it) PersonProxy()
            else error("Unsupported type: ${it.simpleName}")
        }
    }
}
